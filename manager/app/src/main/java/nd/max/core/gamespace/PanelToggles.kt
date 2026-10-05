/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nd.max.MaxManagerProps
import nd.max.core.hardware.RootFileAccess
import nd.max.core.platform.PropertyUtils
import nd.max.ui.viewmodel.TouchBoostViewModel
import java.util.Locale

/**
 * مقابض لوحة «المكعّب» التي تُكتب بالروت فعلًا. كل دالة `set*` تُستدعى من خيط IO فقط،
 * وكل `*On` قراءة رخيصة؛ واللوحة تعيد القراءة بعد الكتابة فلا تعرض حالة لم تحدث.
 * أمّا التحويل الجانبي: لا أداة تُعرض هنا إن لم يكن لها أثر حقيقي على الجهاز.
 */
object PanelToggles {
    /** نطاق للكتابة يعيش أطول من اللوحة: طيّ اللوحة بعد الضغط لا يلغي الكتابة. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun async(block: () -> Unit) {
        io.launch { runCatching(block) }
    }

    private fun global(ctx: Context, key: String, fallback: Int): Int =
        runCatching { Settings.Global.getInt(ctx.contentResolver, key, fallback) }.getOrDefault(fallback)

    fun wifiOn(ctx: Context): Boolean = global(ctx, "wifi_on", 0) != 0
    fun setWifi(on: Boolean) {
        Shell.cmd("svc wifi ${if (on) "enable" else "disable"}").exec()
    }

    fun hasCellular(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun dataOn(ctx: Context): Boolean = global(ctx, "mobile_data", 1) != 0
    fun setData(on: Boolean) {
        Shell.cmd("svc data ${if (on) "enable" else "disable"}").exec()
    }

    /** نفس مفتاح `zen_mode` الذي يكتبه `AppMonitor` عند «عدم الإزعاج أثناء اللعب». */
    fun dndOn(ctx: Context): Boolean = global(ctx, "zen_mode", 0) != 0
    fun setDnd(on: Boolean) {
        Shell.cmd("settings put global zen_mode ${if (on) 1 else 0}").exec()
    }

    private val TOUCH_NODES = listOf(
        "/proc/touchpanel/game_switch_enable",
        "/sys/touchpanel/game_switch_enable",
        "/proc/touch_boost/enable"
    )

    /** لا تظهر الأداة إلا إن وُجدت عقدة لمس مقروءة فعلًا على هذا الجهاز. */
    fun touchAvailable(): Boolean = TOUCH_NODES.any { RootFileAccess.read(it) != null }
    fun touchOn(): Boolean = PropertyUtils.get(MaxManagerProps.Touch.BOOST) == "1"
    /** يطبّق عبر مالك اللمس القائم أولًا، ولا يحفظ التفضيل إلا إن لم تفشل الكتابة. */
    fun setTouch(on: Boolean) {
        val applied = TouchBoostViewModel.reconcileBestEffortBoost(on)
        if (applied != false) PropertyUtils.set(MaxManagerProps.Touch.BOOST, if (on) "1" else "0")
    }
}

/** خطوات المنبّه بالدقائق، و`0` = متوقف. نقيّة فتُختبر بلا جهاز. */
val REMINDER_STEPS: List<Int> = listOf(30, 60, 90, 120)

fun nextReminderMinutes(current: Int): Int {
    if (current <= 0) return REMINDER_STEPS.first()
    val index = REMINDER_STEPS.indexOf(current)
    return if (index < 0 || index == REMINDER_STEPS.lastIndex) 0 else REMINDER_STEPS[index + 1]
}

/** منبّه وقت اللعب: مؤقّت في الذاكرة ينبّه بـToast + نغمة إشعار، ولا يحتاج إذنًا إضافيًّا. */
object PanelReminder {
    private val main = Handler(Looper.getMainLooper())
    private var task: Runnable? = null

    @Volatile private var minutes = 0
    @Volatile private var endsAt = 0L

    fun remainingMin(): Int =
        if (endsAt == 0L) 0 else ((endsAt - SystemClock.elapsedRealtime() + 59_999L) / 60_000L).toInt().coerceAtLeast(0)

    /** يدوّر الخطوة التالية ويعيدها (`0` = أُوقف). */
    fun cycle(ctx: Context, doneMessage: String): Int {
        cancel()
        val next = nextReminderMinutes(minutes)
        minutes = next
        if (next == 0) return 0
        val app = ctx.applicationContext
        val delayMs = next * 60_000L
        endsAt = SystemClock.elapsedRealtime() + delayMs
        val r = Runnable {
            endsAt = 0L
            task = null
            runCatching { Toast.makeText(app, doneMessage, Toast.LENGTH_LONG).show() }
            runCatching {
                RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play()
            }
        }
        task = r
        main.postDelayed(r, delayMs)
        return next
    }

    private fun cancel() {
        task?.let { main.removeCallbacks(it) }
        task = null
        endsAt = 0L
    }
}

/**
 * تسجيل الشاشة بالروت عبر `screenrecord` (مقاطع ≤ 3 دقائق متتابعة حتى الإيقاف) إلى
 * `Movies/MaxManager`. العلم ملفّ في `/data/local/tmp` فيعيش خارج عمر اللوحة، ولو فشل
 * `screenrecord` توقّفت الحلقة بدل أن تدور بلا نهاية.
 */
object PanelScreenRecorder {
    private const val DIR = "/sdcard/Movies/MaxManager"
    private const val FLAG = "/data/local/tmp/.max_screenrec"

    /** الحالة من ملفّ العلم نفسه: لو مات `screenrecord` وحذف العلم، تتصحّح اللوحة وحدها. */
    fun isRecording(): Boolean = RootFileAccess.exists(FLAG)

    fun start() {
        Shell.cmd(
            "mkdir -p $DIR",
            "touch $FLAG",
            "(while [ -f $FLAG ]; do screenrecord --time-limit 180 $DIR/Max_\$(date +%Y%m%d_%H%M%S).mp4 " +
                "|| { rm -f $FLAG; break; }; done) >/dev/null 2>&1 &"
        ).exec()
    }

    fun stop() {
        Shell.cmd(
            "rm -f $FLAG",
            "pkill -2 screenrecord || killall -2 screenrecord",
            "sleep 1",
            "for f in $DIR/*.mp4; do am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://\$f >/dev/null 2>&1; done"
        ).exec()
    }
}

/** ساعة الجلسة: منذ متى اللعبة الحالية أمامية (تُصفَّر بتبدّل اللعبة). */
object PanelSessionClock {
    @Volatile private var pkg: String? = null
    @Volatile private var since = 0L

    fun touch(packageName: String) {
        if (packageName != pkg) {
            pkg = packageName
            since = SystemClock.elapsedRealtime()
        }
    }

    fun hoursText(): String {
        val hours = if (since == 0L) 0f else (SystemClock.elapsedRealtime() - since) / 3_600_000f
        return "%.1fh".format(Locale.US, hours)
    }
}

/** تطبيق مختصر يظهر أعلى اللوحة: يُعرض فقط إن كان مثبّتًا فعلًا وله شاشة تشغيل. */
class QuickApp(val pkg: String, val label: String, val icon: Bitmap)

object PanelQuickApps {
    private val CANDIDATES = listOf(
        "com.android.chrome", "com.google.android.gm", "com.whatsapp", "com.discord",
        "org.telegram.messenger", "com.google.android.youtube", "com.google.android.apps.messaging",
        "com.facebook.orca"
    )

    fun installed(ctx: Context): List<QuickApp> {
        val pm = ctx.packageManager
        return CANDIDATES.mapNotNull { pkg ->
            runCatching {
                if (pm.getLaunchIntentForPackage(pkg) == null) return@runCatching null
                QuickApp(
                    pkg = pkg,
                    label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                    icon = pm.getApplicationIcon(pkg).toBitmap(96)
                )
            }.getOrNull()
        }
    }

    fun launch(ctx: Context, pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        runCatching { ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun Drawable.toBitmap(px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, px, px)
        draw(Canvas(bmp))
        return bmp
    }
}
