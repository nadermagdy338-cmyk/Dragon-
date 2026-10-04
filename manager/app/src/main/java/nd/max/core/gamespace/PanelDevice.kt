package nd.max.core.gamespace

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import android.content.res.Resources
import android.media.AudioManager
import android.provider.Settings
import com.topjohnwu.superuser.Shell

/**
 * سطوع النظام وصوت الوسائط الحقيقيّان للوحة. السطوع على مدى الجهاز نفسه (يُقرأ من موارد النظام
 * لا 0..255 مفترضًا)، ويُكتب بإذن الإعدادات إن وُجد وإلا بالجذر. الصوت بلا إذن.
 * كل الدوال سريعة ولا تحجب الخيط الرئيسي (الكتابة بالجذر `submit` غير متزامنة).
 */
class PanelDevice(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val minB = resInt("config_screenBrightnessSettingMinimum", 1).coerceAtLeast(0)
    private val maxB = resInt("config_screenBrightnessSettingMaximum", 255).coerceAtLeast(minB + 1)

    fun brightness(): Float = runCatching {
        val v = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        ((v - minB).toFloat() / (maxB - minB)).coerceIn(0f, 1f)
    }.getOrDefault(0.5f)

    fun setBrightness(fraction: Float) {
        val value = (minB + fraction.coerceIn(0f, 1f) * (maxB - minB)).toInt().coerceIn(minB, maxB)
        val written = runCatching {
            Settings.System.canWrite(app) &&
                Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) &&
                Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.getOrDefault(false)
        if (!written) {
            Shell.cmd("settings put system screen_brightness_mode 0", "settings put system screen_brightness $value").submit()
        }
    }

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max).coerceIn(0f, 1f)
    }

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).toInt(), 0) }
    }

    /** قفل التدوير: `ACCELEROMETER_ROTATION == 0` يعني مقفولًا. */
    fun rotationLocked(): Boolean = runCatching {
        Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0
    }.getOrDefault(false)

    fun setRotationLock(locked: Boolean) {
        val value = if (locked) 0 else 1
        val written = runCatching {
            Settings.System.canWrite(app) &&
                Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, value)
        }.getOrDefault(false)
        if (!written) Shell.cmd("settings put system accelerometer_rotation $value").submit()
    }

    /** «18:23 · 33%» بصيغة ساعة المستخدم (12/24)، و«⚡» أثناء الشحن. بلا إذن. */
    fun statusLine(): String {
        val time = android.text.format.DateFormat.getTimeFormat(app).format(java.util.Date())
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "--"
        return "$time · $pct" + (if (charging) " ⚡" else "") + " · " + CockpitModel.speedText(netBytesPerSecond())
    }

    /** الذاكرة المتاحة (MB) من `MemAvailable` — قراءة بلا جذر. */
    fun availMb(): Int = runCatching {
        java.io.File("/proc/meminfo").useLines { lines ->
            lines.first { it.startsWith("MemAvailable:") }.filter { it.isDigit() }.toInt() / 1024
        }
    }.getOrDefault(0)

    /**
     * يقتل العمليات المخبّأة/الفارغة فقط (`am kill-all` لا يمسّ المقدّمة) ثم يدمج الذاكرة (نفس
     * أمر شاشة ZRAM)، ويعيد **المتاح الذي زاد فعلًا** بالقياس لا بالتقدير. من خيط IO.
     */
    fun cleanMemory(): Int {
        val before = availMb()
        Shell.cmd("am kill-all", "echo 1 > /proc/sys/vm/compact_memory 2>/dev/null").exec()
        Thread.sleep(500)
        return (availMb() - before).coerceAtLeast(0)
    }

    private var lastNetBytes = -1L
    private var lastNetAt = 0L

    /** سرعة الشبكة الإجمالية (تنزيل+رفع) بين استدعاءين؛ -1 حين لا يدعم الجهاز العدّاد، و0 في أوّل قراءة. */
    fun netBytesPerSecond(): Long {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return -1L
        val now = SystemClock.elapsedRealtime()
        val total = rx + tx
        val rate = if (lastNetBytes < 0 || now <= lastNetAt) 0L else ((total - lastNetBytes) * 1000L / (now - lastNetAt)).coerceAtLeast(0L)
        lastNetBytes = total
        lastNetAt = now
        return rate
    }

    /** السطوع قابل للكتابة إن مُنح إذن الإعدادات أو توفّر الجذر؛ وإلا يُفتح طلب الإذن بدل صمتٍ بلا أثر. */
    fun canSetBrightness(): Boolean = runCatching {
        Settings.System.canWrite(app) || Shell.isAppGrantedRoot() == true
    }.getOrDefault(false)

    fun openWriteSettings() {
        runCatching {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + app.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun resInt(name: String, fallback: Int): Int = runCatching {
        val id = Resources.getSystem().getIdentifier(name, "integer", "android")
        if (id != 0) Resources.getSystem().getInteger(id) else fallback
    }.getOrDefault(fallback)
}
