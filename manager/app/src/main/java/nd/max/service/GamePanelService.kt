/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.gamespace.GameLibraryAccess
import nd.max.core.gamespace.GamePanelMode
import nd.max.core.gamespace.BypassState
import nd.max.core.gamespace.PanelClockReader
import nd.max.core.gamespace.PanelControlState
import nd.max.core.gamespace.PanelControls
import nd.max.core.gamespace.PanelClocks
import nd.max.core.gamespace.GamePanelState
import nd.max.core.gamespace.PanelSide
import nd.max.core.gamespace.PanelSubject
import nd.max.core.gamespace.PanelLifetime
import nd.max.core.gamespace.gameLibrary
import nd.max.core.gamespace.nextRefreshRate
import nd.max.core.gamespace.panelPlacement
import nd.max.core.gamespace.panelServiceLifetime
import nd.max.core.gamespace.panelServiceNeeded
import nd.max.core.gamespace.panelSubject
import nd.max.core.gamespace.reconcileGamePanel
import nd.max.core.platform.FpsMonitorUtil
import nd.max.core.platform.HUD_OWNER_OVERLAY
import nd.max.core.platform.HudField
import nd.max.core.platform.HudLive
import nd.max.core.platform.HudRecorder
import nd.max.core.platform.HudSampler
import nd.max.ui.component.GamePanelSurface
import nd.max.ui.overlay.OverlayWindow
import nd.max.ui.util.FpsOverlayPrefs
import nd.max.ui.util.GamePanelPrefs

/**
 * لوحة مساحة الألعاب الجانبية — **قيادة فقط، والقراءة والكتابة ليست هنا**.
 *
 * ### ما تفعله وما لا تفعله
 *
 * تفعل شيئًا واحدًا: تُبقي **مقبضًا على الحافة** حين تكون اللعبة الأمامية في المكتبة ومُفعَّلة
 * لها اللوحة، وتفتحها بلمسة، وتُخفيها حين تُغادر اللعبة. ولا تقرأ عتادًا ولا تكتب عليه: كل
 * رقم يُعرض يأتي من `HudSampler` (القارئ الواحد) عبر `HudLive`، وكل كتابة تبقى في `AppMonitor`
 * عبر المركّب (ADR-11). ولهذا لا يوجد هنا سطر `RootFileAccess` واحد.
 *
 * ### ولماذا خدمة رابعة لا توسيع `FpsOverlayService`
 *
 * الخدمة القائمة تعرض **لوحة أرقام مصقولة** في مكان يختاره المستخدم، وهذه تعرض **حالة لعبة**
 * (أيّ لعبة، مقبض/فتح، جلسة) في موضع تلتصق فيه بالحافة. جمعهما في خدمة واحدة يعني مفتاحًا
 * واحدًا يُشغّل سطحين مختلفَي السلوك، وإطفاء أحدهما يُطفئ الآخر. والحدّ المعلن: الخدمتان
 * **لا تعملان معًا** لأنّ `HudSampler` قارئ واحد يملكه من بدأ أخيرًا (`HUD_OWNER_OVERLAY`
 * مشترك) — وهو قيد قائم مُعلن في `HudSession`، لا عطبًا جديدًا.
 *
 * ### والحدّ الثاني
 *
 * تعرّف اللعبة الأمامية يأتي من `FpsMonitorUtil.getForegroundPackage()` وهي **قراءة واحدة في
 * الثانية عبر صدفة**، وليست إشارة دقيقة لحظية؛ ولذلك الفاصل هنا ثانية ونصف، وقراءة غير مقروءة
 * تُطوى اللوحة ([PanelSubject.Unknown]) بدل أن تُقرأ «ليست لعبة».
 */
class GamePanelService : LifecycleService() {

    companion object {
        /** هل الخدمة معروضة الآن؟ تقرأه الشاشة قبل أن تَعِد المستخدم بشيء. */
        var isRunning = false
            private set

        const val CHANNEL_ID = "maxmanager_game_panel"
        const val NOTIF_ID = 9023
        const val ACTION_STOP = "nd.max.service.GamePanelService.STOP"

        /** فاصل استطلاع اللعبة الأمامية — ثانية ونصف: قراءة `dumpsys` لكل ثانية عبء بلا داعٍ. */
        private const val POLL_MS = 1500L

        /**
         * يُشغّل اللوحة عند إقلاع التطبيق **إن كانت هناك لعبة مُفعَّلة واحدة على الأقل**.
         *
         * **وهذا هو شرط الطلب حرفيًّا:** «عند دخول أي شيء من التطبيقات التي في اللوبي، أو لو
         * فُتحت اللعبة من خارج التطبيق، يظهر الـoverlay». فالخدمة تُشغَّل هنا وتنتظر اللعبة
         * (`IDLE_LIMIT`)، ثم تبقى ما دامت لعبة مُفعَّلة تعمل. وإن لم تكن هناك لعبة مُفعَّلة
         * **لا تُشغّل شيئًا** — فلا خدمة أمامية بلا سبب، ولا إشعار دائم لم يُطلب.
         */
        fun ensureRunning(context: Context) {
            val enabled = GamePanelPrefs.load(context).enabledPackages
            if (enabled.isEmpty() || isRunning) return
            val intent = Intent(context, GamePanelService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * كم استطلاعًا تنتظر الخدمة لعبةً قبل أن تُوقف نفسها (~دقيقة).
         *
         * الفرق الذي يمنع حالتين سيئتين: تفعيل الخيار من داخل التطبيق (المقدّمة لحظتها تطبيقنا،
         * فالانتظار يُبقيها حتى تُفتح اللعبة)، ونيّة قديمة بلا لعبة (فلا تبقى خدمة أمامية أبدًا).
         */
        private const val IDLE_LIMIT = 40
    }

    private lateinit var window: OverlayWindow
    private var state by mutableStateOf(GamePanelState())
    private var library: Set<String> = emptySet()

    /** هل رأينا لعبة مُفعَّلة في هذه الجلسة؟ — يمنع انتظارًا أبديًّا لنيّة قديمة. */
    private var seenGame = false
    private var idlePolls = 0

    /** آخر معدّل تحديث فرضناه من اللوحة — يُعرض كما هو، و`null` يعني «بلا فرض». */
    private var refreshRateHz: Int? = null
    private var clocks by mutableStateOf(PanelClocks())
    private var controls by mutableStateOf(PanelControlState())

    override fun onCreate() {
        super.onCreate()
        window = OverlayWindow(this)
        startOverlayForeground(
            OverlayNotice(
                channelId = CHANNEL_ID,
                notificationId = NOTIF_ID,
                channelNameRes = R.string.game_panel_notif_channel,
                titleRes = R.string.game_panel_notif_title,
                stopLabelRes = R.string.game_panel_notif_stop,
                stopAction = ACTION_STOP,
                serviceClass = GamePanelService::class.java
            )
        )
        // قارئ واحد: تُشغّل الخدمة اللوحة فيبقى الحائز عليها حتى تُغادر، فلا حلقتان على جهاز.
        HudSampler.start(
            context = applicationContext,
            owner = HUD_OWNER_OVERLAY,
            scope = lifecycleScope,
            fields = { HudField.entries.toSet() }
        )
        lifecycleScope.launch { poll() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!window.isShown) {
            // **بلا سحب مقصود:** اللوحة ملتصقة بالحافة بموضع محسوب ([panelPlacement])، والسحب
            // كان سيجعلها تُسحب ثم يُعيدها الاستطلاع كل ثانية ونصف إلى موضعها — أي صراعًا يقرأه
            // المستخدم «اللوحة ترتجف». وموضعها يُحسب، فلا حاجة لسحب.
            val mounted = window.mount(dragEnabled = false) {
                Panel()
            }
            if (!mounted) {
                // لا ادّعاء تشغيل بلا نافذة: نفس عقد `FpsOverlayService` بالحرف.
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
            isRunning = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        HudSampler.stop(HUD_OWNER_OVERLAY)
        window.unmount()
        isRunning = false
        super.onDestroy()
    }

    /**
     * استطلاع اللعبة الأمامية: يقرأ المكتبة مرّة عند البدء، ثم يسأل عن المقدّمة ويُوفِّق الحالة.
     *
     * **وكل انتقال يمرّ من `reconcileGamePanel`** — فلا كتابة مباشرة على الحالة من موضعين، ولا
     * فتح ينجو من مغادرة اللعبة.
     */
    private suspend fun poll() {
        library = withContext(Dispatchers.IO) {
            runCatching {
                val apps = GameLibraryAccess.apps(applicationContext)
                gameLibrary(apps, GameLibraryAccess.manual(applicationContext), GameLibraryAccess.excluded(applicationContext))
                    .map { it.packageName }.toSet()
            }.getOrDefault(emptySet())
        }
        while (lifecycleScope.isActive) {
            val prefs = GamePanelPrefs.load(applicationContext)
            val foreground = withContext(Dispatchers.IO) {
                runCatching { FpsMonitorUtil.getForegroundPackage() }.getOrNull()
            }
            val subject = panelSubject(foreground, library)
            // لعبة جديدة ⟹ الفتح لا يُنقل إليها: الفتح حالة جلسة لا حالة مخزّنة.
            val sameGame = subject is PanelSubject.Tracked && state.subject == subject
            val open = if (sameGame) state.openByUser else false
            state = reconcileGamePanel(
                state = state,
                subject = subject,
                enabled = subject is PanelSubject.Tracked && subject.packageName in prefs.enabledPackages,
                openByUser = open
            ).copy(side = prefs.side)
            if (state.mode == GamePanelMode.Open) {
                clocks = withContext(Dispatchers.IO) { runCatching { PanelClockReader.read() }.getOrDefault(PanelClocks()) }
                controls = withContext(Dispatchers.IO) { runCatching { PanelControls.read() }.getOrDefault(controls) }
            }
            placeWindow()

            // اللوحة تُفتح **تلقائيًّا** عند دخول لعبة مُفعَّلة من أيّ طريق (حتى من مشغّل خارجي)،
            // وتُوقف الخدمة حين تنتهي الحاجة — بقاعدة نقيّة مُختبَرة لا بشرط في المكان.
            val needed = panelServiceNeeded(prefs.enabledPackages, foreground)
            if (needed) {
                seenGame = true
                idlePolls = 0
            } else if (seenGame || ++idlePolls >= IDLE_LIMIT) {
                stopSelf()
                return
            }
            if (panelServiceLifetime(seenGame, needed, idlePolls, IDLE_LIMIT) == PanelLifetime.Stop) {
                stopSelf()
                return
            }
            delay(POLL_MS)
        }
    }

    /**
     * يدوّر معدّل التحديث: 60 ⟶ 90 ⟶ 120 ⟶ **بلا فرض** — عبر `RefreshRateReceiver` وحده.
     *
     * **ولا كتابة من هنا:** المالك القائم يطبّق ذرّيًّا بقراءة نهائية وبـ`resetprop` مقيّد،
     * وتكرار الكتابة في موضع ثانٍ هو ما يمنعه ADR-11.
     */
    private fun cycleRefreshRate() {
        val next = nextRefreshRate(refreshRateHz)
        val intent = Intent(applicationContext, nd.max.RefreshRateReceiver::class.java)
            .setAction("nd.max.SET_FPS")
        if (next == null) intent.putExtra("reset", true) else intent.putExtra("fps", next)
        applicationContext.sendBroadcast(intent)
        refreshRateHz = next
    }

    /** تسجيل الجلسة: الحائز القائم `HudRecorder` — لا سجلّ ثانٍ ولا ملفّ جديد. */
    private fun selectProfile(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            PanelControls.applyProfile(id)
            controls = PanelControls.read()
        }
    }

    private fun toggleBypass() {
        lifecycleScope.launch(Dispatchers.IO) {
            PanelControls.setBypass(controls.bypass != BypassState.On)
            controls = PanelControls.read()
        }
    }

    private fun toggleRecording() {
        if (HudRecorder.isLive) HudRecorder.stop() else HudRecorder.start()
    }

    /**
     * يضع النافذة على الحافة **بعد أن يُقاس مقاس المحتوى** (المقبض غير اللوحة).
     *
     * والحدّ المعلن: القياس يأتي بعد أوّل إطار، فتكون أوّل وضعية عند ٠ ثم تُصحَّح — وهو سطر
     * واحد مرئيّ عند الإقلاع لا انزياح دائم.
     */
    private fun placeWindow() {
        // الوضع المفتوح ملء الشاشة: لا موضع جانبيّ يُحسب له.
        if (state.mode == GamePanelMode.Open) {
            window.setFullScreen(true)
            return
        }
        window.setFullScreen(false)
        val (screenWidth, screenHeight) = window.screenBounds()
        val content = window.contentSize() ?: return
        val side = state.side
        val placement = panelPlacement(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            panelWidth = content.first,
            panelHeight = content.second,
            insetTop = 0,
            insetBottom = 0,
            side = side
        )
        window.place(placement.x, placement.y)
    }

    /** يفتح شاشة إعدادات التطبيق التي يعيش فيها مقبض عدم الإزعاج (أثره عالميّ ومالكه هناك). */
    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(applicationContext, nd.max.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** الرسم: من الحالة وحدها، ومن القاسم المشترك للأرقام — بلا قراءة ثانية. */
    @Composable
    private fun Panel() {
        val snapshot by HudLive.snapshot.collectAsState()            // تُقرأ كل إطار لأنّها رخيصة (ملفّ تفضيلات في الذاكرة) — فمن غيّر اللون أو الحقول
            // من الشاشة يراها في اللوحة بلا إعادة تشغيل الخدمة، وهو نفس عقد التراكبين القائمين.
            val prefs = FpsOverlayPrefs.load(applicationContext)
            val label = when (val subject = state.subject) {
            is PanelSubject.Tracked -> subject.packageName
            is PanelSubject.Other -> subject.packageName
            PanelSubject.Unknown -> ""
        }
        GamePanelSurface(
            mode = state.mode,
            gameLabel = label,
            reading = snapshot.reading,
            tally = HudRecorder.tally(),
            fields = prefs.orderedFields,
            accent = parseHudColor(prefs.colorHex),
            side = state.side,
            recording = HudRecorder.isLive,
            refreshRateHz = refreshRateHz,
            clocks = clocks,
            controls = controls,
            frames = snapshot.framesHistory,
            onOpen = {
                state = state.copy(mode = GamePanelMode.Open, openByUser = true)
                window.setFullScreen(true)
            },
            onCollapse = {
                state = state.copy(mode = GamePanelMode.Handle, openByUser = false)
                window.setFullScreen(false)
                // بعد قياس المقبض الجديد لا القديم، فيُوضع على الحافة فورًا لا بعد الاستطلاع التالي.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ placeWindow() }, 80L)
            },
            onClose = { stopSelf() },
            onCycleRefresh = { cycleRefreshRate() },
            onToggleRecording = { toggleRecording() },
            onOpenControls = { openAppSettings() },
            onSelectProfile = { id -> selectProfile(id) },
            onToggleBypass = { toggleBypass() }
        )
    }
}

/**
 * لون التمييز المخزَّن نصًّا (`#RRGGBB`) ⟶ `Color`، والقراءة المرفوضة تُسقط إلى الأصل.
 *
 * **ولماذا لا يُترك التحليل لمكتبة:** التفضيل يكتبه المستخدم من منتقي في الشاشة، وقيمة مُفسَدة
 * (نصّ فارغ، رمز ناقص) يجب أن تُعطي اللون الافتراضي لا أن تُسقط الرسم — وهو نفس مبدأ «أيّ اسم
 * نوع لا يُعرف يُسقط إلى الافتراضيّ» في `FpsOverlayPrefs`.
 */
internal fun parseHudColor(hex: String, fallback: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF00E676)): androidx.compose.ui.graphics.Color =
    runCatching {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) fallback else androidx.compose.ui.graphics.Color(clean.toLong(16) or 0xFF000000L)
    }.getOrDefault(fallback)
