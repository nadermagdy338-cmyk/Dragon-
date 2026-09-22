/*
 * Copyright (C) 2026 Rem01Gaming
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max


import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import org.lsposed.hiddenapibypass.HiddenApiBypass
import android.hardware.display.DisplayManager
import android.view.Display
import android.provider.Settings
import android.media.AudioManager
import android.net.wifi.WifiManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.diagnostics.DeviceFacts
import nd.max.core.diagnostics.LogHeader
import nd.max.core.diagnostics.LogSettingsDigest
import nd.max.ui.util.PerAppKernelUtil
import nd.max.ui.util.ProfilePresetStore
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.AtlasAdaptiveExecutor
import nd.max.core.hardware.AtlasRouteMemoryFactory
import nd.max.core.hardware.GpuCeilingPolicy
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.core.hardware.PlatformCeilingAuthority
import nd.max.core.hardware.HardwareRepairExecutor
import nd.max.core.hardware.GpuTweakPersistence
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.HardwareVerification
import nd.max.core.hardware.PerAppHardwareStatus
import nd.max.core.hardware.PerAppHardwareStatus.Outcome
import nd.max.core.hardware.RootFileAccess
import nd.max.core.hardware.ThermalCeilingRouter
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.hardware.PerAppControlRegistry
import nd.max.core.hardware.PerAppRecoveryStore
import nd.max.core.hardware.ThermalGuard
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.decodePerAppCpuPolicyControls
import nd.max.ui.viewmodel.TouchBoostViewModel


@SuppressLint("StaticFieldLeak", "DiscouragedPrivateApi", "PrivateApi")
object AppMonitor {
    private const val POLL_INTERVAL_MS = 500L
    // How often reassertDriftedKnobs() re-verifies GPU/CPU knobs against
    // what the per-app config actually asked for. Deliberately much coarser
    // than POLL_INTERVAL_MS: this is a safety net against vendor thermal
    // daemons undoing our writes, not a tight control loop.
    private const val DRIFT_CHECK_INTERVAL_MS = 10_000L
    private const val PID_RETRY_INTERVAL_MS = 50L
    private const val UNKNOWN_APP = "unknown 0 0"
    private const val NONE_APP = "none 0 0"
    // مهلة السماح قبل التراجع عن تعديلات تطبيق مغادر نحو غير مُدار:
    // التنقل السريع (إشعار ثم عودة) لا يخفق التعديلات، وموت العملية
    // يُعجّل التراجع فورًا دون انتظار. 10 ثوانٍ توافق مهلة الوحدة
    // الأصلية عند إطفاء الشاشة.
    private const val PERAPP_GRACE_MS = 10_000L
    // كم دورة متتابعة (٥٠٠ م.ث لكل دورة) يجب أن يثبت فيها غياب معرّف العملية قبل تسليم الحالة
    // إلى مهلة السماح. ثلاث دورات = ١٫٥ ثانية: أطول من أي تأخير عابر في تحديث قائمة العمليات،
    // وأقصر من أن يبقى الجهاز مُقيَّدًا بعد تطبيق أُغلق.
    private const val FOREGROUND_UNCONFIRMED_LIMIT = 3

    private val FOREGROUND_METHOD_CANDIDATES = listOf(
        "getFocusedRootTaskInfo",
        "getFocusedRootTask",
        "getFocusedTaskInfo",
        "getFocusedStackInfo",
        "getTopActivity",
        "getTasks",
        "getRunningTasks"
    )

    private val COMPONENT_NAME_FIELDS = listOf(
        "topActivity",
        "topActivityComponent",
        "realActivity",
        "baseActivity",
        "origActivity",
        "activity"
    )

    private var systemContext: Context? = null

    private var activityTaskManager: Any? = null
    private var foregroundMethod: Method? = null
    private var powerManager: PowerManager? = null
    private var activityManager: ActivityManager? = null
    private var notificationManager: Any? = null
    private var batteryManager: BatteryManager? = null
    private var getZenModeMethod: Method? = null

    private var bruteForceCandidates: List<Method>? = null

    @Volatile
    private var lastStatus = ""
    
    @Volatile
    private var lastBackgroundApps = ""

    private var outputPath = ""
    private var cachedGameListModified = -1L

    // زمن آخر تعديل لملف إعدادات التطبيقات **عند آخر تطبيق فعليّ** — لا عند آخر قراءة.
    //
    // والمقارنة به لا بـ[cachedGameListModified]: ذاك يُحدَّث في كل قراءة (ودورة الانحراف تقرأ
    // الملف كل عشر ثوانٍ)، فأي قراءة تقع بين تغيير المستخدم وفحص التغيير تُسقط الفحص ويبقى
    // الإعداد القديم ساريًا حتى تبديل تطبيق تالٍ — وهو بالحرف: «أختار gaming فيظهر أثره بعد
    // كم دقيقة». هذا الحقل لا يُحدَّث إلا عند تنفيذ تطبيق/تراجع حقيقي، فيبقى الفرق مرئيًّا.
    private var appliedConfigModified = -1L
    private var backgroundOutputPath = ""
    private var lockFilePath: String? = null

    // ── Per-App Config state ──────────────────────────────────────────────
    @Volatile private var lastAppliedPkg = ""

    // هل تعديلات per-app حيّة على العتاد الآن؟ يُنشر في app_status
    // (perapp_active) كي يتحول محرك MAX AI إلى وضع المراقبة أثناء
    // ملكية ملف التطبيق — قيمة فعلية لا مؤقت.
    @Volatile private var perAppOverridesActive = false

    // مهلة السماح عند مغادرة تطبيق مُدار نحو غير مُدار (المشغّل أو
    // الشاشة الرئيسية): التراجع مؤجل — الرجوع السريع خلال المهلة لا
    // يخفق التعديلات، وموت التطبيق فعليًا (عملية غير موجودة) يُعجّل
    // التراجع دون انتظار المؤقت. الحالة الحقيقية للتطبيق هي المرجع.
    @Volatile private var gracePkg: String? = null
    @Volatile private var graceDeadlineMs: Long = 0L

    // عدد الدورات المتتابعة التي قُرئ فيها التطبيق نفسه في المقدّمة **بلا معرّف عملية**.
    //
    // ولماذا عدّاد لا قراءة واحدة: قراءة واحدة بـ`0 0` عابرة (عملية تُولَد، أو خدمة إدارة
    // المهام لم تُحدَّث بعد)؛ والثبات عليها هو الدليل. وبعد [FOREGROUND_UNCONFIRMED_LIMIT]
    // دورة تُسلَّم الحالة إلى مهلة السماح، وهي التي تتحقّق من موت العملية فعليًّا قبل التراجع.
    private var missingFocusPkg: String? = null
    private var missingFocusCount = 0

    // جلسة تطبيق **انتهت** لأن عمليتها لم تعد موجودة. وتبقى معلومةً حتى يعود للتطبيق معرّف
    // عملية (أي: فُتح من جديد) أو يتغيّر التطبيق في المقدّمة.
    //
    // وبلا هذا الحقل كان كلُّ دورة تعيد تسليح مهلة السماح ثم تُتراجع من جديد: قراءة المقدّمة
    // تبقى `pkg 0 0` بعد موت التطبيق (المهمة تُعاد لفترة قبل أن تزول)، فتُقرأ على أنها «نفس
    // التطبيق ما زال في المقدّمة» — فلا تراجع، ولا عودة للنبضات/التردد إلى ما كانا عليه،
    // والبطاقة تكتب تطبيقًا مُغلقًا. وهو المقيس حرفيًّا: «أغلق كل شيء ويبقى التردد ٦٥٠».
    private var endedForegroundPkg: String? = null
    // Correlation id for the app switch currently being applied/reverted, e.g.
    // "sw-1798...". Regenerated every time the focused app changes (see
    // buildStatus()) and written into app_status so the native daemon's
    // read_app_status() picks it up and can tag its own log_zenith() calls
    // for the same switch with the same id -- see AppMonitorLogger.kt's
    // header comment and Batch 3 of the logging plan for the full picture.
    @Volatile private var currentSwitchId = ""
    private var savedGpuGovernor = ""
    private var savedCpuGovernors = mutableMapOf<String, String>()
    private var savedCpuMinFreqs = mutableMapOf<String, String>()
    private var savedCpuMaxFreqs = mutableMapOf<String, String>()
    // Baseline is captured once before the first per-app override. It must not
    // be overwritten on every app switch, otherwise switching from an explicit
    // governor app to `default` would snapshot the previous app's governor and
    // carry it into the next app.
    private var baselineGpuGovernor = ""
    private var baselineCpuGovernors = mutableMapOf<String, String>()
    private var baselineCaptured = false
    private var savedGpuNode = ""
    private var savedGpuMinFreq = ""
    private var savedGpuMaxFreq = ""
    private var savedGpuBaseline: GpuHardwareBackend.Baseline? = null
    private var savedThermalProfile = ""
    private var savedZenMode: Int? = null
    private var savedPeakRefreshRate = ""
    private var savedMinRefreshRate = ""
    private var savedVendorRefreshSnapshot: PerAppRefreshRateController.Snapshot? = null
    private var wasZenSet = false
    /**
     * This process's single ownership gate. One arbiter per process is what makes
     * in-process priority arbitration real; coherence with the app process comes
     * from the shared journal, never from a second gate instance.
     */
    private val mutationGate = HardwareControlArbiter()
    private val hardwareControlRegistry = PerAppControlRegistry(mutationGate)
    private var activePerAppCpuPackage = ""


    // ── App Settings fields (wired to backend here) ─────────────────────────
    // touch_boost is read every poll (buildStatus runs every 500ms, not just
    // on focus change) because applyTouchBoost() needs to react to
    // screen-on/off too. "default" means "fall back to the existing
    // isKnownGameApp() auto-detect heuristic", matching pre-existing
    // behavior for apps that never set this field. Set from the per-app
    // config in applyPerAppConfig() below, reset in revertPerAppConfig().
    @Volatile private var touchBoostOverride = "default"
    private var forcedHwUi = false
    private var savedHwUiProp: String? = null
    private var savedDisableHwProp: String? = null
    /**
     * نيّة المستخدم لكل مقبض عتاد قبل أي تدخل آليّ من الحارس الحراري.
     *
     * ولماذا لزمت: الحارس يخفض المقبض المملوك نفسه، فيصير «المطلوب» في السجل هو قيمة
     * الحارس لا ما طلبه المستخدم. بلا حفظ النيّة الأصلية لا يمكن أن يُعاد السقف إليها
     * عند البرودة، فيبقى التطبيق مُقيَّدًا بعد أن يزول سبب التقييد.
     * وتُلتقط عند التطبيق وتُطرح عند التراجع — فلا تبقى نيّة تطبيق على تطبيق آخر.
     */
    private val hardwareUserIntent = mutableMapOf<String, String>()

    /**
     * مُخطِّط سقف الحرارة — Atlas هو من يقرّر أيّ مسار يُنفَّذ، لا حلقة خاصة في هذا الملف.
     *
     * ويُبنى في [main] بعد معرفة سياق التطبيق (مخزن ذاكرة المسارات يعيش في تخزين التطبيق الخاص)،
     * فبقي `null` حتى ذلك الحين و`serviceThermalGuard` تتصرّف مع الغياب صراحةً لا بصمت.
     */
    @Volatile private var thermalRouter: ThermalCeilingRouter? = null

    /** هل أُعلنت حالة الحارس الحراري لهذه الجلسة؟ سطر واحد لكل جلسة لا واحد كل عشر ثوانٍ. */
    private var thermalGuardNoted = false

    /** مفتاح آخر كتلة جلسة كُتبت (`sw|pkg`) — كتلة واحدة لكل جلسة تطبيق، لا واحدة كل دورة. */
    private var lastSessionHeaderKey: String? = null
    private var logHeaderWritten = false
    private var cpuBoostThread: Thread? = null
    private var cpuBoostOriginalMins = mutableMapOf<String, String>()
    @Volatile private var cpuBoostGeneration = 0L
    private var savedHapticFeedbackEnabled: String? = null
    private var wasNotifStreamMuted = false
    private var wifiNoSleepLock: WifiManager.WifiLock? = null

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            AppMonitorLogger.fatal("Usage: <status_output_path> <background_output_path> [lock_file_path] -- missing required output paths")
            return
        }
        
        outputPath = args[0]
        backgroundOutputPath = args[1]

        if (args.size >= 3) {
            lockFilePath = args[2]
        }

        bypassHiddenApiRestrictions()
        setupSystemContext()

        if (systemContext == null) {
            AppMonitorLogger.fatal("System context is null after setupSystemContext() -- cannot continue")
            return
        }

        val controlContext = runCatching {
            systemContext!!.createPackageContext("nd.max", Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse {
            AppMonitorLogger.fatal("Cannot resolve nd.max package context for shared control plane: ${it.message}")
            return
        }
        SharedHardwareOwnershipStore.configure(
            controlContext.filesDir,
            controlContext.applicationInfo.uid,
            android.os.Process.myPid(),
        )
        // Same directory as the journal, so this process honours the exact locks
        // the UI wrote: a per-app rule must never move a knob the user pinned.
        ManualControlLocks.configure(controlContext.filesDir)
        configureThermalRouter(controlContext)

        if (!initializeServices()) {
            AppMonitorLogger.fatal("Failed to initialize services (ActivityTaskManager/PowerManager/etc.), exiting")
            return
        }

        // ترويسة السجل بعد تهيئة الخدمات: تحتاج `packageManager` لقراءة إصدار التطبيق، وتُكتب
        // قبل الحلقة فتصير في أعلى الجلسة لا في وسطها.
        writeLogStartupHeader()

        runCatching { GpuTweakPersistence.applySaved() }
            .onFailure { AppMonitorLogger.e("startup: saved GPU Studio state failed", it) }
        // A stale Core Grid manual-frequency session flag (set before a crash)
        // would keep the module's profile binary from ever resetting CPU
        // limits. The property is non-persistent, so this only matters when
        // the companion restarts without a reboot.
        //
        // Durable manual locks must survive that restart: if the user still holds
        // a locked cpufreq knob, the stand-down flag is re-asserted from the lock
        // store instead of being cleared, otherwise the module's coarse shell
        // channel would reclaim knobs the user pinned (a lock the AI respects but
        // the service ignores is not a lock). Only cpufreq locks set this flag —
        // it is the CPU-limit channel's stand-down, not a global mode.
        val lockedCpuKnobs = ManualControlLocks.lockedKeys().filter(HardwareControlKey::isCpuLimits)
        if (lockedCpuKnobs.isNotEmpty()) {
            runCatching { shellExec("setprop sys.maxmanager.manual_freq_session 1") }
            AppMonitorLogger.i("startup: re-asserted manual session for ${lockedCpuKnobs.size} locked cpufreq knob(s)")
        } else {
            runCatching { shellExec("setprop sys.maxmanager.manual_freq_session 0") }
        }
        recoverStalePerAppState()
        // سجل نتائج الجلسة السابقة يخصّ عملية ماتت (والإقلاع يُصفّر العتاد أصلًا)، فإبقاؤه
        // يجعل الواجهة تعرض «نتيجة الآن» وهي نتيجة أمس. والتاريخ يبقى كاملًا في
        // `MaxManager.log` بأسطر `EVENT=PERAPP_KNOB` — فالمحو هنا لا يُفقد دليلًا.
        runCatching { PerAppHardwareStatus.clear() }
        AppMonitorLogger.i("AppMonitor companion started (pid=${android.os.Process.myPid()})")

        val lockChannel = acquireLock()

        val monitorThread = Thread.currentThread()
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { revertPerAppConfig() }
            runCatching { shellExec("setprop sys.maxmanager.perapp.governor_isolation 0") }
            lockChannel?.close()
            monitorThread.interrupt()
        })

        runMonitorLoop()
    }

    /**
     * بناء مخطِّط مسارات سقف الحرارة مرّة واحدة في بداية العملية.
     *
     * `HardwareRepairExecutor` ثانٍ فوق **نفس** `mutationGate`: هذا ليس العطب المحذور في
     * `PerAppControlRegistry` (مُحكِّمان في عملية واحدة = جداول طلبات متنافرة)، بل غلاف رقيق حول
     * نفس البوّابة، والملكية تبقى في مكان واحد.
     *
     * المصنع المشترك مع Hilt يربط الذاكرة بنفس المجلد ونفس عدّاد إقلاع الجهاز.
     * إعادة تشغيل الرفيق ليست إقلاعًا جديدًا، وتعذّر قراءة العدّاد لا يرفع الحجر.
     */
    private fun configureThermalRouter(controlContext: Context) {
        runCatching {
            val memory = AtlasRouteMemoryFactory.create(controlContext) { android.os.SystemClock.elapsedRealtime() }
            thermalRouter = ThermalCeilingRouter(
                registry = hardwareControlRegistry,
                adaptive = AtlasAdaptiveExecutor(HardwareRepairExecutor(mutationGate), memory),
            )
        }.onFailure { AppMonitorLogger.e("EVENT=THERMAL_ROUTER_INIT_FAILED", it) }
    }

    // ── ترويسة السجل: تجعل ملف السجل يشرح نفسه ─────────────────────────────────

    /**
     * حقائق الجهاز والبناء كما تُقرأ هنا — لا تُخمَّن ولا تُترك فارغة.
     *
     * و`SOC_MODEL`/`SOC_MANUFACTURER` محميّان بـ API 31 (كما في `DataModule` و`LogsViewerViewModel`):
     * قراءتهما على 29/30 ترمي `NoSuchFieldError`، والمجهول يبقى `null` لا نصًّا يشبه اسم جهاز.
     */
    private fun logDeviceFacts(): DeviceFacts {
        val appVersion = runCatching {
            systemContext?.packageManager?.getPackageInfo("nd.max", 0)?.versionName
        }.getOrNull()
        val moduleVersion = runCatching {
            shellRead("grep '^version=' '${MaxManagerPaths.MODULE_DIR}/module.prop' 2>/dev/null | head -n1")
        }.getOrNull()?.substringAfter('=', "")?.trim()?.takeIf(String::isNotEmpty)
        return DeviceFacts(
            appVersion = appVersion,
            moduleVersion = moduleVersion,
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.ifBlank { null } else null,
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.ifBlank { null } else null,
            hardware = Build.HARDWARE.ifBlank { null },
            apiLevel = Build.VERSION.SDK_INT,
            kernel = System.getProperty("os.version"),
            // قياس لا ادّعاء: هذه العملية يبدأها `service.sh` بجذر، و"جذر" هنا هو هويّة العملية.
            rooted = runCatching { android.os.Process.myUid() == 0 }.getOrDefault(false),
            board = Build.BOARD.ifBlank { null },
            abi = Build.SUPPORTED_ABIS.firstOrNull(),
        )
    }

    /**
     * الإعداد الذي كان قائمًا وقت التشغيل — هو ما يجيب سؤال «عطل جهاز أم عطل إعداد؟».
     *
     * والقائمة تأتي من [LogSettingsDigest] لا من هنا: نفس القائمة تُكتب من عملية التطبيق أيضًا،
     * وقائمتان تتباعدان تُنتجان ملفين يبدوان صورة واحدة وهما ليستا كذلك.
     *
     * والقراءة عبر [PropertyUtils] (انعكاس على `SystemProperties`، بلا صندوق أوامر): أربعة عشر
     * `getprop` عند بدء العملية تعني أربعة عشر إنشاء عمل — كلفة بلا مقابل.
     */
    private fun logSettingsDigest(): List<Pair<String, String>> =
        LogSettingsDigest.of { key -> PropertyUtils.get(key) }

    /**
     * ترويسة التشغيل: الجهاز، والإعداد، ودليل القراءة، وقاموس الرموز — مرّة لكل عملية.
     *
     * ولماذا قبل الحلقة لا في تقرير منفصل: الملف المُرسَل هو الذي يجب أن يشرح نفسه، وترويسة
     * تُبنى عند المشاركة وحدها تترك أيّ نسخة مربوطة (`logcat` مثلًا) بلا سياق.
     */
    private fun writeLogStartupHeader() {
        if (logHeaderWritten) return
        logHeaderWritten = true
        runCatching { LogHeader.startupLines(logDeviceFacts(), logSettingsDigest()).forEach(AppMonitorLogger::i) }
            .onFailure { AppMonitorLogger.e("EVENT=LOG_HEADER_WRITE_FAILED", it) }
    }

    /**
     * كتلة جلسة التطبيق — تُكتب **بعد** محاولة تطبيق إعداداته، فتحمل ما طُلب فعلًا لا ما كان مأمورًا به.
     *
     * وهذا هو الفرق العملي: بلا `desired` في الملف يُعرف أن الكتابة فشلت ولا يُعرف أن المطلوب
     * كان `300000:2000000` — فيصير السؤال «هل فشل التطبيق أم فشل الطلب؟» بلا جواب.
     */
    private fun writeLogSessionHeader(pkgName: String) {
        val key = "$currentSwitchId|$pkgName"
        if (key == lastSessionHeaderKey) return
        lastSessionHeaderKey = key
        runCatching {
            val knobs = hardwareControlRegistry.ownedDesired().entries.map { (name, desired) -> name to desired }
            val startedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            LogHeader.sessionLines(currentSwitchId, pkgName, startedAt, knobs).forEach(AppMonitorLogger::i)
        }.onFailure { AppMonitorLogger.e("EVENT=LOG_HEADER_WRITE_FAILED pkg=$pkgName", it) }
    }

    private fun acquireLock(): FileChannel? {
        val path = lockFilePath ?: return null
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            val channel = FileChannel.open(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )

            val lock: FileLock? = channel.tryLock()
            if (lock == null) {
                AppMonitorLogger.fatal("Another AppMonitor instance already holds the lock at '$path'")
                channel.close()
                System.exit(1)
                null
            } else {
                channel
            }
        } catch (e: Exception) {
            AppMonitorLogger.fatal("Failed to acquire lock at '$path'", e)
            System.exit(1)
            null
        }
    }

    private fun runMonitorLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                serviceDeferredRevert()
                writeStatus()
                writeBackgroundApps()
                reassertDriftedKnobs()
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (t: Throwable) {
                AppMonitorLogger.e("Uncaught error in monitor loop iteration", t)
            }
        }
    }

    /**
     * خدمة مهلة السماح: تُنفَّذ كل دورة (500 مللي). يُتراجع فورًا إذا
     * ماتت عملية التطبيق المغادر (حالة حقيقية من ActivityManager)، أو
     * عند انقضاء المهلة (10 ثوانٍ — نفس مهلة الوحدة الأصلية عند
     * إطفاء الشاشة) — أيهما أسبق.
     */
    private fun serviceDeferredRevert() {
        val pkg = gracePkg ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val died = !isAppProcessAlive(pkg)
        if (died || now >= graceDeadlineMs) {
            AppMonitorLogger.i(
                "EVENT=PERAPP_DEFERRED_REVERT pkg=$pkg reason=${if (died) "app_died" else "grace_expired"} sw=$currentSwitchId"
            )
            runCatching { revertPerAppConfig() }
                .onFailure { AppMonitorLogger.e("EVENT=REVERT_FAILED pkg=$pkg sw=$currentSwitchId (deferred)", it) }
            // الجلسة انتهت: تُعلَم الحزمة بذلك فلا تُقرأ قراءةُ المقدّمة بلا معرّف عملية — التي
            // تبقى بعد الموت دهرًا — على أنها «التطبيق نفسه ما زال سارٍ» فتُعاد الدورة كل ثانيتين.
            endedForegroundPkg = pkg
            missingFocusPkg = null
            missingFocusCount = 0
            gracePkg = null
        }
    }

    /**
     * تُسلِّم الحزمة إلى مهلة السماح: التراجع يقع عند موت العملية فعليًّا أو عند انتهاء المهلة.
     *
     * ولماذا مهلة لا تراجع فوري: قراءة المقدّمة بلا معرّف عملية قد تكون **عابرة** (تطبيق يُولَد،
     * أو خدمة إدارة المهام لم تُحدَّث بعد). فالحسم من حالة العملية الحقيقية
     * ([isAppProcessAlive]) لا من القراءة وحدها، والمهلة هي هامش الأمان بينهما.
     */
    private fun armGraceRevert(pkg: String) {
        if (gracePkg == pkg) return
        gracePkg = pkg
        graceDeadlineMs = android.os.SystemClock.elapsedRealtime() + PERAPP_GRACE_MS
        AppMonitorLogger.i(
            "EVENT=PERAPP_GRACE_ARMED pkg=$pkg reason=foreground-process-missing grace_ms=$PERAPP_GRACE_MS sw=$currentSwitchId"
        )
    }

    /** زمن تعديل ملف إعدادات التطبيقات — القيمة الوحيدة التي يُبنى عليها قرار «تغيّر الإعداد». */
    private fun appListModified(): Long = runCatching {
        val file = File(MaxManagerPaths.APPLIST_JSON)
        if (file.exists()) file.lastModified() else -1L
    }.getOrDefault(-1L)

    /** هل ما زالت عملية التطبيق حية؟ فشل الاستعلام يُعامل كحي (ننتظر المؤقت). */
    private fun isAppProcessAlive(pkg: String): Boolean = runCatching {
        activityManager?.runningAppProcesses?.any { p ->
            p.processName == pkg || p.pkgList?.contains(pkg) == true
        } == true
    }.getOrDefault(true)

    private var lastDriftCheckAt = 0L

    /**
     * applyPerAppConfig() only runs once, at the moment the foreground app
     * changes -- it has no way to notice if something *else* undoes its
     * work afterward. On Xiaomi devices in particular, mi_thermald
     * periodically republishes its own CPU/GPU frequency ceilings on a
     * timer that has nothing to do with MaxManager, silently pulling a
     * chosen GPU/thermal profile back within seconds to minutes. From the
     * person's side this looks exactly like "I picked a profile and it did
     * nothing" even though the initial write succeeded.
     *
     * This re-checks the live value against what the per-app config
     * actually asked for, roughly every DRIFT_CHECK_INTERVAL_MS while an
     * app with a non-default override is in the foreground, and only
     * re-applies (and logs EVENT=APPLY_DRIFT) when they've actually
     * diverged -- not on every single poll, which would just be fighting
     * the vendor daemon in a tight loop for no benefit.
     */
    private fun reassertDriftedKnobs() {
        val pkg = lastAppliedPkg
        if (pkg.isBlank()) return
        val checkAt = System.currentTimeMillis()
        if (checkAt - lastDriftCheckAt < DRIFT_CHECK_INTERVAL_MS) return
        lastDriftCheckAt = checkAt

        // The initial apply registers each physical policy/device under its
        // canonical key. Reassert those exact entries; do not create generic
        // `cpu_governor`/`gpu_governor` keys here. The old generic keys caused
        // the drift path to become a second owner, bypassing per-policy
        // arbitration and sometimes failing on heterogeneous CPU policies.
        runCatching {
            hardwareControlRegistry.verifyAndRepair().forEach { result ->
                if (!result.successful) {
                    AppMonitorLogger.w("EVENT=APPLY_DRIFT_REASSERT_FAILED knob=${result.key} pkg=$pkg expected=${result.requested} live=${result.actual ?: "none"} error=${result.error ?: "unknown"} sw=$currentSwitchId")
                } else if (result.driftedBefore == true) {
                    // Only a knob that had actually diverged is a repair. The previous condition here
                    // (`attempts > 1`) was unreachable: `attempts` is 1 or 0 by construction, so a
                    // successful repair of a vendor reclaim was logged as nothing at all — the one
                    // event worth seeing in a drift log.
                    AppMonitorLogger.i("EVENT=APPLY_DRIFT_REPAIRED knob=${result.key} pkg=$pkg expected=${result.requested} live=${result.actual ?: "none"} sw=$currentSwitchId")
                }
            }
        }.onFailure { AppMonitorLogger.e("EVENT=DRIFT_CHECK_FAILED knob=hardware_registry pkg=$pkg sw=$currentSwitchId", it) }

        // الحارس الحراري يلي التحقّق مباشرةً: يقرأ ضغط المنصة ثمّ يعدّل **المقبض المملوك نفسه**
        // في حدود ما طلبه المستخدم. تفصيل التصميم في [ThermalGuard].
        runCatching { serviceThermalGuard() }
            .onFailure { AppMonitorLogger.e("EVENT=THERMAL_GUARD_FAILED pkg=$pkg sw=$currentSwitchId", it) }

        // Refresh-rate is another vendor-owned setting that can be reset after
        // an app switch (display/HAL policy changes are enough to do it). Verify
        // only explicit per-app requests and reapply them at the same coarse
        // cadence as the CPU/GPU drift guard. This intentionally does not fight
        // the system when the app profile says "default".
        runCatching {
            val requested = readAppConfigField(pkg, "refresh_rate")
                .takeIf { it.isNotBlank() && it != "default" }
                ?.toFloatOrNull()
                ?.toInt()
                ?: return@runCatching
            val context = systemContext ?: return@runCatching
            val normalized = PerAppRefreshRateController.normalizeRequestedRate(context, requested)
                ?: return@runCatching
            val live = PerAppRefreshRateController.currentEnforcedRate(context)
            if (live != null && live != normalized) {
                AppMonitorLogger.w("EVENT=APPLY_DRIFT knob=refresh_rate pkg=$pkg expected=$normalized live=$live sw=$currentSwitchId reassert=true")
                if (!PerAppRefreshRateController.apply(context, normalized)) {
                    AppMonitorLogger.w("EVENT=APPLY_DRIFT_REASSERT_FAILED knob=refresh_rate pkg=$pkg expected=$normalized sw=$currentSwitchId")
                }
            }
        }.onFailure { AppMonitorLogger.e("EVENT=DRIFT_CHECK_FAILED knob=refresh_rate pkg=$pkg sw=$currentSwitchId", it) }
    }

    /**
     * الحارس الحراري لكل تطبيق: يخفض السقف المملوك حين تُعلن المنصة خنقًا، ويعيده حين يزول.
     *
     * ولماذا هنا بالذات: هذه الدورة تعمل كل عشر ثوانٍ ما دام تطبيق مُدار في المقدّمة — نفس
     * الإيقاع الذي يكشف إعادة كتابة مُلطِّف الـvendor، وهو نفس الإيقاع المناسب لإشارة حرارية
     * (لا حلقة تحكّم ضيقة).
     *
     * والأهم: **هذه الدالة لا تقرّر أيّ مسار**. تُمرّر الضغط والسلّم والنيّة إلى
     * [ThermalCeilingRouter]، ويختار Atlas المسار (إشارة المنصة، أو سقف المستخدم الثابت عند غيابها،
     * أو البديل بعد فشل مُسترجع).
     *
     * حدود صريحة:
     * - لا يعمل إلّا بوجود مقبض مملوك فعلًا، فلا يُحرّك شيئًا على جهاز لم يُطبَّق عليه شيء.
     * - ولا يعمل إلّا بنيّة محفوظة للمستخدم؛ ولولا حفظها لكان خفضُ السقف يمحو ما اختاره
     *   المستخدم بدل أن يتحرّك داخله.
     * - وضغط مجهول لا يُخمَّن عليه: المسار الأوّل يصير غير مؤهّل، و**سقف المستخدم نفسه يبقى
     *   يُنفَّذ** بدل أن يُسكت كل شيء (وهو ما كان يحدث قبل الربط).
     */
    private fun serviceThermalGuard() {
        val pkg = lastAppliedPkg
        if (pkg.isBlank() || !perAppOverridesActive) return
        val owned = hardwareControlRegistry.ownedDesired()
        if (owned.isEmpty()) return

        val router = thermalRouter
        if (router == null) {
            if (!thermalGuardNoted) {
                thermalGuardNoted = true
                noteHardware("thermal", Outcome.UNSUPPORTED, "thermal-router-unavailable")
                PerAppHardwareStatus.flush()
            }
            return
        }
        val pressure = ThermalGuard.readPressure(powerManager)
        if (pressure == ThermalGuard.Pressure.UNKNOWN && !thermalGuardNoted) {
            // إشارة المنصة غائبة: لا يخفض الحارس على تخمين، **لكنّ السقف الذي اختاره المستخدم
            // يبقى مسارًا يُنفَّذ** — وهذا فرق حقيقي عن السابق حيث كان غياب الإشارة يُسكت كل شيء.
            thermalGuardNoted = true
            noteHardware("thermal", Outcome.APPLIED, "guard-static-only:platform-thermal-status-unavailable")
            PerAppHardwareStatus.flush()
        }

        // سقف GPU
        val gpuKey = owned.keys.firstOrNull(HardwareControlKey::isGpuFrequency)
        if (gpuKey != null) {
            val device = GpuHardwareBackend.selection().device?.takeIf { HardwareControlKey.gpuFrequency(it.name) == gpuKey }
            val userCeiling = hardwareUserIntent[gpuKey]
            if (device != null && userCeiling != null) {
                routeThermal(
                    statusKnob = "gpu_profile",
                    outcome = router.apply(
                        key = gpuKey,
                        target = AtlasControlTarget.GPU_FREQUENCY,
                        packageName = pkg,
                        userCeiling = userCeiling,
                        ladder = device.frequencies,
                        pressure = pressure,
                    ),
                    pressure = pressure,
                )
            }
        }

        // سقوف CPU — لكل سياسة سلّمها المُعلن.
        owned.keys.filter(HardwareControlKey::isCpuLimits).forEach { key ->
            val userCeiling = hardwareUserIntent[key] ?: return@forEach
            val policyName = HardwareControlKey.cpuLimitsPolicy(key) ?: return@forEach
            val policy = CpuHardwareBackend.policies().firstOrNull { it.name == policyName } ?: return@forEach
            routeThermal(
                statusKnob = key,
                outcome = router.apply(
                    key = key,
                    target = AtlasControlTarget.CPU_FREQUENCY,
                    packageName = pkg,
                    userCeiling = userCeiling,
                    ladder = policy.availableFrequenciesKHz,
                    pressure = pressure,
                ),
                pressure = pressure,
            )
        }

        if (!pressure.isThrottling && !thermalGuardNoted) {
            thermalGuardNoted = true
            noteHardware("thermal", Outcome.APPLIED, "guard-idle:${pressure.name}")
        }
        PerAppHardwareStatus.flush()
    }

    /**
     * تسجيل ناتج التخطيط: **مسار مُتحقَّق** أو فشل بسببه — ولا سطر لقيمة لم تحتج تغييرًا.
     *
     * و`acted = false` تعني «لا شيء يحتاج فعلًا» (السقف المطلوب هو القائم، أو المقبض غير مملوك)،
     * وهي ليست عطلًا: كتابتها كفشل تجعل السجل يصرخ كل عشر ثوانٍ على جهاز سليم.
     */
    private fun routeThermal(
        statusKnob: String,
        outcome: ThermalCeilingRouter.Outcome,
        pressure: ThermalGuard.Pressure,
    ) {
        if (!outcome.acted) return
        val reason = if (outcome.verified) {
            "thermal-guard:${outcome.routeId ?: "unknown-route"}"
        } else {
            // سبب الفشل **وقرار المسار معًا**: «فشل» وحدها لا تُصلح شيئًا، والفرق بين «كل
            // المسارات محجورة بعد استرجاع غير مؤكَّد» و«الهدف غير قابل للقياس» هو الفرق بين
            // عطل في جهاز وعطل في منطق.
            "thermal-guard-failed:${outcome.reason}@${outcome.decision.ifBlank { "undecided" }}"
        }
        noteHardware(
            statusKnob,
            if (outcome.verified) Outcome.APPLIED else Outcome.NOT_VERIFIED,
            reason,
            outcome.desired.orEmpty(),
            outcome.previous.orEmpty(),
        )
        // الاسم نفسه الذي وُجد في الجولة السابقة (`PERAPP_THERMAL_GUARD`) لأن اسم الحدث عقد لمن
        // يبحث عنه؛ ولكن الحقول الجديدة تُضاف إليه: **قرار المسار** والمسارات التي **لم تُجرَّب**.
        // وقبلهما كان السطر يقول «فشل» ولا يقول أيّ مسار رُفض ولا لماذا.
        val routeLine = "EVENT=PERAPP_THERMAL_GUARD knob=$statusKnob pressure=${pressure.name}" +
            " decision=${outcome.decision.ifBlank { "undecided" }}" +
            " chosen=${outcome.routeId ?: "none"}" +
            " verified=${outcome.verified}" +
            " reason=${outcome.reason}" +
            " from=${outcome.previous ?: "none"} to=${outcome.desired ?: "none"}" +
            " skipped=${outcome.skipped.joinToString(",") { (route, why) -> "$route:$why" }.ifEmpty { "none" }}" +
            " pkg=$lastAppliedPkg sw=$currentSwitchId"
        // المستوى من النتيجة: مسار لم يتحقّق يجب أن يظهر في مُرشِّح الفشل بلا استثناء.
        if (outcome.verified) {
            AppMonitorLogger.i(routeLine)
        } else {
            AppMonitorLogger.w(routeLine)
        }
    }

    private fun writeStatus() {
        val focusedApp = waitForValidFocusedApp() ?: return
        // ومعرّف العملية **جزء من الدليل** لا زينة: `pkg 0 0` تعني «لم أجد عملية هذا التطبيق»،
        // وهي إشارة نهاية جلسة لا «نفس التطبيق ما زال في المقدّمة». وكانت تُسقَط هنا (الدالة
        // التي تعرفها موجودة وتُنادى في مكان آخر فقط)، فيبقى التطبيق «ساريًا» بعد موته، فلا
        // تتراجع تعديلات per-app، وتبقى البطاقة تكتبه، والإشعار يعلن «Per-App active».
        val foregroundConfirmed = !hasMissingPid(focusedApp)
        val currentStatus = runCatching { buildStatus(focusedApp, foregroundConfirmed) }
            .onFailure { AppMonitorLogger.e("buildStatus() failed for focused app '$focusedApp'", it) }
            .getOrNull() ?: return
        if (currentStatus == lastStatus) return

        try {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            
            val tmpFile = File("$outputPath.tmp")

            FileOutputStream(tmpFile).use { fos ->
                fos.write(currentStatus.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }

            tmpFile.renameTo(file)
            
            lastStatus = currentStatus
        } catch (e: Exception) {
            AppMonitorLogger.e("Failed to write status file '$outputPath'", e)
        }
    }

    /**
     * MENGAMBIL DAFTAR PACKAGE YANG ADA DI RECENT APPS (TASK MANAGER)
     */
    @Suppress("DEPRECATION")
    private fun getRecentAppPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val recentTasks = activityManager?.getRecentTasks(30, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            recentTasks?.forEach { task ->
                val pkg = task.baseIntent.component?.packageName ?: task.topActivity?.packageName
                if (pkg != null) {
                    packages.add(pkg)
                }
            }
            
            val currentFocused = lastStatus.substringAfter("focused_app ").substringBefore(" ")
            if (currentFocused.isNotBlank() && currentFocused != "unknown" && currentFocused != "none") {
                packages.add(currentFocused)
            }
        } catch (e: Exception) {
            AppMonitorLogger.e("getRecentAppPackages() failed", e)
        }
        return packages
    }


    private fun writeBackgroundApps() {
        val processes = activityManager?.runningAppProcesses ?: return
        
        val recentPackages = getRecentAppPackages()

        val currentApps = buildString {
            for (process in processes) {
                val pkgName = process.pkgList?.firstOrNull() ?: process.processName
                
                if (recentPackages.contains(pkgName)) {
                    appendLine("$pkgName ${process.pid} ${process.uid}")
                }
            }
        }

        if (currentApps == lastBackgroundApps) return

        try {
            val file = File(backgroundOutputPath)
            file.parentFile?.mkdirs()
            
            val tmpFile = File("$backgroundOutputPath.tmp")

            FileOutputStream(tmpFile).use { fos ->
                fos.write(currentApps.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }

            tmpFile.renameTo(file)

            lastBackgroundApps = currentApps
        } catch (e: Exception) {
            AppMonitorLogger.e("Failed to write background apps file '$backgroundOutputPath'", e)
        }
    }


    private fun waitForValidFocusedApp(): String? {
        var focusedApp = getFocusedAppInfo()
        if (!hasMissingPid(focusedApp)) return focusedApp

        val deadline = System.currentTimeMillis() + POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(PID_RETRY_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            focusedApp = getFocusedAppInfo()
            if (!hasMissingPid(focusedApp)) return focusedApp
        }

        return focusedApp
    }

    private fun hasMissingPid(appInfo: String): Boolean =
        appInfo != NONE_APP && appInfo.endsWith(" 0 0")

    private fun recoverStalePerAppState() {
        runCatching {
            val stale = PerAppRecoveryStore.read() ?: return@runCatching
            val currentBoot = PerAppRecoveryStore.bootId()
            if (currentBoot.isBlank() || stale.bootId != currentBoot) {
                PerAppRecoveryStore.clear()
                return@runCatching
            }
            if (PerAppRecoveryStore.restore(stale)) {
                AppMonitorLogger.w("EVENT=PERAPP_RECOVERY_RESTORED pkg=${stale.packageName} cpu=${stale.cpuGovernors.size} gpu=${stale.gpuNode.isNotBlank()}")
            } else {
                AppMonitorLogger.e("EVENT=PERAPP_RECOVERY_PARTIAL pkg=${stale.packageName}")
            }
            // MTK fixed-index locks are intentionally released through the existing
            // vendor-aware adapter; the journal stores generic devfreq state only.
            runCatching { PerAppKernelUtil.releaseGpuFixedFrequency() }
            PerAppRecoveryStore.clear()
        }.onFailure { AppMonitorLogger.e("EVENT=PERAPP_RECOVERY_FAILED", it) }
    }

    private fun buildStatus(focusedApp: String, foregroundConfirmed: Boolean): String {
        val screenAwake = if (powerManager?.isInteractive == true) 1 else 0
        val batterySaver = if (powerManager?.isPowerSaveMode == true) 1 else 0
        val zenMode = getZenMode()
        val batteryLevel = getBatteryLevel()
        val isCharging = getChargingStatus()
        val pkgName = focusedApp.substringBefore(" ")
        val appName = getAppName(pkgName)
        val currentRefreshRate = getCurrentRefreshRate()
        val maxRefreshRate = getMaxRefreshRate()

        // ── Apply per-app config when focused app changes ─────────────────
        // Wrapped in runCatching: apply/revert touch privileged APIs
        // (WRITE_SECURE_SETTINGS-gated Settings.Global/System calls) that
        // throw SecurityException on ROMs that don't grant it (MIUI/HyperOS
        // and friends). Without this guard, one failure here used to abort
        // buildStatus() entirely -> app_status never got written again ->
        // lastAppliedPkg never advanced -> the same failing call retried
        // forever on every poll. lastAppliedPkg is now updated unconditionally
        // so the daemon always moves on, even when apply/revert partially fail.
        if (pkgName != lastAppliedPkg) {
            // تغيّر التطبيق: الجلسة المُنتهية السابقة لم تعد هي الحالة — وهذه العلامة تُصفَّر
            // هنا لا في مكان آخر، فلا تبقى حزمة «منتهية» تمنع مسارًا شرعيًّا لاحقًا.
            if (pkgName != endedForegroundPkg) endedForegroundPkg = null
            missingFocusPkg = null
            missingFocusCount = 0
            // زمن الملف **قبل** التنفيذ: تغيير يقع أثناء التطبيق يبقى مرئيًّا في الدورة التالية.
            val modifiedAtSwitch = appListModified()
            // عاد التطبيق المُدار خلال مهلة السماح؟ التعديلات ما زالت
            // حية على العتاد — إلغاء التراجع المؤجل بلا خفقان ولا
            // إعادة تطبيق.
            if (gracePkg != null && pkgName == gracePkg) {
                AppMonitorLogger.i("EVENT=PERAPP_GRACE_ABORTED pkg=$pkgName sw=$currentSwitchId reason=refocused")
                gracePkg = null
            } else {
                currentSwitchId = "sw-${System.currentTimeMillis()}"
                AppMonitorLogger.i("EVENT=APP_SWITCH pkg=$pkgName prev=$lastAppliedPkg sw=$currentSwitchId")
                // قرار "مُدار/غير مُدار" يحتاج الكاش طازجًا: بلا هذا
                // الإنعاش كانت أول إحالة بعد إقلاع الرفيق تمر بالمسار
                // السريع فلا تنشط مهلة السماح إلا من التبديل الثاني.
                refreshConfigCacheIfChanged()
                val prevPkg = lastAppliedPkg
                val prevManaged = prevPkg.isNotBlank() &&
                    cachedGameListText?.contains("\"$prevPkg\":") == true
                // و«مُدار» تشترط عملية في المقدّمة: تطبيق بلا عملية لا جلسة له، فتطبيق إعداده في
                // قائمة الإعدادات لا يُشغّل عليه شيء لأنه ما زال مكتوبًا في المهمة العليا بعد
                // موته. وهذا هو مسار التسريب المقيس: التردد يبقى والبطاقة تكتب تطبيقًا مُغلقًا.
                val newManaged = foregroundConfirmed && pkgName.isNotBlank() &&
                    pkgName != "unknown" && pkgName != "none" &&
                    cachedGameListText?.contains("\"$pkgName\":") == true

                if (newManaged || !prevManaged) {
                    // مسار سريع: تطبيق مُدار جديد (يأخذ الملكية فورًا)،
                    // أو مغادرة تطبيق غير مُدار (لا شيء مؤجل أصلًا).
                    gracePkg = null
                    // التراجع ليس مجّانيًّا، ولا يُنادى إلا لملكية قائمة.
                    // سبب هذا الشرط مقيس من حزمة سجلّات جهاز حقيقي (MT6899،
                    // 2026-09-20): الانتقال بين تطبيقين **غير مُدارين** كان يستدعي
                    // revertPerAppConfig() فتسير السلسلة: قراءة الملف الحالي ←
                    // تشغيل `sys.maxmanager-service --profile N` ← إعادة تطبيق
                    // الملف **كاملًا** ← إشعار. والمقيس في السجل: ٣٠ حدث APP_SWITCH
                    // تحمل ٢٢ EVENT=CLI_PROFILE_APPLY في ٦٧ ثانية، و٢٣ سطرًا
                    // «Balanced Profile applied successfully!» في ٥٥٫٦ ثانية —
                    // ولكل إعادة ≥٣٢ كتابة sysfs (محسوبة بين علامتي نجاح متتاليتين:
                    // ٨ سقوف + ٨ أرضيات لثماني سياسات، ومُجدوِل I/O، وحاكم dvfsrc،
                    // وvfs_cache_pressure، ومفاتيح fpsgo/GED، ومؤشر OPP للـGPU) أي
                    // أكثر من ٢٠٠ كتابة في الدقيقة + ولادة عملية + بثّ إشعار لكل
                    // تبديل تطبيق. والأسوأ من الكلفة: كل تبديل يمحو أي حدّ وضعه
                    // المستخدم أو وضعه MAX AI (الملف العام يعيد كتابة حدود الأنوية).
                    // فالملكية وحدها تُرخَّص: إمّا أن التطبيق المغادر كان مُدارًا،
                    // وإمّا أن تعديلات per-app حيّة على العتاد الآن.
                    if (prevPkg.isNotBlank() && (prevManaged || perAppOverridesActive)) {
                        runCatching { revertPerAppConfig() }
                            .onFailure { AppMonitorLogger.e("EVENT=REVERT_FAILED pkg=$prevPkg sw=$currentSwitchId", it) }
                    }
                    if (foregroundConfirmed && pkgName.isNotBlank() && pkgName != "unknown" && pkgName != "none") {
                        runCatching { applyPerAppConfig(pkgName) }
                            .onFailure { AppMonitorLogger.e("EVENT=APPLY_FAILED pkg=$pkgName sw=$currentSwitchId reason=no_per_app_overrides_applied", it) }
                    }
                } else {
                    // مغادرة تطبيق مُدار إلى المشغّل/تطبيق غير مُدار تعني انتهاء الملكية.
                    // لا نؤجل الاستعادة: إبقاء GPU/CPU على قيمة اللعبة بعد إغلاقها هو
                    // تسريب جلسة، وقد يرفع الحرارة والبطارية بلا سبب. التبديل السريع
                    // سيعيد تطبيق إعداد التطبيق عند عودته من جديد، بينما الأسبقية اليدوية
                    // وMAX AI محفوظة داخل الـarbiter ولا تُستعاد فوقها.
                    gracePkg = null
                    graceDeadlineMs = 0L
                    if (prevPkg.isNotBlank() && (prevManaged || perAppOverridesActive)) {
                        AppMonitorLogger.i(
                            "EVENT=PERAPP_REVERT_ON_FOREGROUND_LOSS pkg=$prevPkg reason=foreground-lost sw=$currentSwitchId"
                        )
                        runCatching { revertPerAppConfig() }
                            .onFailure { AppMonitorLogger.e("EVENT=REVERT_FAILED pkg=$prevPkg sw=$currentSwitchId", it) }
                    }
                }
            }
            lastAppliedPkg = pkgName
            appliedConfigModified = modifiedAtSwitch
            val parts = focusedApp.split(" ")
            val focusedPid = parts.getOrNull(1) ?: "0"
            val focusedUid = parts.getOrNull(2) ?: "0"
            // و«مُدار» تعني: مُدرَج في قائمة الإعدادات **وأن له عملية في المقدّمة**. تطبيق مُغلق
            // لا جلسة له: كتابة اسمه في بطاقة النشاط وإشعار «Per-App active» حينها تصف حالة
            // غير موجودة، وهي الشكوى الحرفية: «التطبيق ما زال مكتوبًا في بطاقة النشاط وأنا
            // متأكد أنه مغلق».
            val managed = foregroundConfirmed && cachedGameListText?.contains("\"$pkgName\":") == true
            if (managed) {
                writeAppGameInfo(pkgName, focusedPid, focusedUid)
                updateActiveAppNotification(pkgName, focusedApp)
            } else {
                writeAppGameInfo("", "0", "0")
                clearActiveAppNotification()
            }
            baselineCaptured = if (managed) baselineCaptured else false
        }

        // انتهت جلسة التطبيق الحالي (ماتت عمليته) ثم عاد إلى المقدّمة: تُفتح له جلسة جديدة.
        //
        // وبلا هذا يبقى `lastAppliedPkg` هو الحزمة نفسها، فلا يرى المسار تبديلًا ولا يُعاد
        // التطبيق أبدًا: تطبيق فُتح من جديد يظل بلا تعديلات حتى يغادر ويُفتح ثانية.
        if (pkgName == lastAppliedPkg && foregroundConfirmed && pkgName == endedForegroundPkg) {
            endedForegroundPkg = null
            AppMonitorLogger.i("EVENT=PERAPP_SESSION_REOPENED pkg=$pkgName sw=$currentSwitchId")
            // تصفير «المُطبَّق» يجعل الدورة التالية تمرّ بمسار التبديل: تراجع ثم تطبيق كاملين.
            lastAppliedPkg = ""
        }

        // التطبيق نفسه في المقدّمة لكن **بلا معرّف عملية**: ليست حالةً سارية، وتُعامل كإشارة
        // نهاية جلسة بعد ثباتها (العابرة تُحتمل، والثابتة لا). ومهلة السماح هي التي تتحقّق من
        // موت العملية فعليًّا قبل أي تراجع — فلا خفقان على تطبيق حيّ.
        if (pkgName == lastAppliedPkg) {
            val unconfirmed = !foregroundConfirmed &&
                pkgName.isNotBlank() && pkgName != "unknown" && pkgName != "none"
            if (unconfirmed) {
                if (missingFocusPkg == pkgName) missingFocusCount++ else {
                    missingFocusPkg = pkgName
                    missingFocusCount = 1
                }
                if (pkgName != endedForegroundPkg && missingFocusCount >= FOREGROUND_UNCONFIRMED_LIMIT) {
                    armGraceRevert(pkgName)
                }
            } else {
                missingFocusPkg = null
                missingFocusCount = 0
                if (gracePkg != null) {
                    AppMonitorLogger.i("EVENT=PERAPP_GRACE_ABORTED pkg=$pkgName sw=$currentSwitchId reason=foreground-process-confirmed")
                    gracePkg = null
                }
            }
        }

        // تغيّر إعداد التطبيق الحالي وهو في المقدّمة: يُنفَّذ في هذه الدورة لا عند تبديل تالٍ.
        //
        // والمقارنة بزمن **آخر تطبيق** لا بزمن آخر قراءة: الكاش يُحدَّث في كل قراءة (ودورة
        // الانحراف تقرأ `refresh_rate` كل عشر ثوانٍ)، فقراءة تقع بين تغيير المستخدم وفحصه كانت
        // تُسقط الفحص ويبقى الإعداد القديم ساريًا حتى تبديل تطبيق تالٍ — وهو المقيس: «أختار
        // gaming فيظهر أثره بعد كم دقيقة».
        if (pkgName == lastAppliedPkg && foregroundConfirmed) {
            val modified = appListModified()
            if (modified != appliedConfigModified) {
                appliedConfigModified = modified
                runCatching {
                    revertPerAppConfig()
                    applyPerAppConfig(pkgName)
                    lastAppliedPkg = pkgName
                    if (foregroundConfirmed) updateActiveAppNotification(pkgName, focusedApp)
                }.onFailure { AppMonitorLogger.e("EVENT=LIVE_CONFIG_REAPPLY_FAILED pkg=$pkgName sw=$currentSwitchId", it) }
            }
        }

        // Per-app touch policy explicitly overrides the ROM default only while
        // it is set. Otherwise the ROM-wide Touch Boost control remains the
        // source of truth (with the legacy game heuristic as a compatibility
        // fallback when that global property has not been configured yet).
        val globalTouchBoost = shellRead("getprop persist.sys.maxmanager.custom_touch_boost") == "1"
        val touchBoostDecision = when (touchBoostOverride) {
            "true" -> true
            "false" -> false
            else -> globalTouchBoost || isKnownGameApp(pkgName)
        }
        // والنداء **مُسوّي لا كاتب**: هذه الدالة تُنادى كل ٥٠٠ م.ث، وكانت الكتابة فيها بلا شرط —
        // فسُجّلت على جهاز حقيقي ٣٠١٦ كتابة إلى عقدة اللمس في ٣٦ دقيقة (٩٤% من ملف السجل) بنفس
        // القيمة. الآن لا كتابة إلا عند تغيّر القرار أو انحراف العقدة، والقراءة تكشف الانحراف في
        // نفس الدورة. التفصيل في [TouchBoostViewModel.reconcileBestEffortBoost].
        runCatching {
            TouchBoostViewModel.reconcileBestEffortBoost(screenAwake == 1 && touchBoostDecision)
            XiaomiVendorFeatures.applyAodColorOverride(
                context = systemContext,
                screenAwake = screenAwake == 1,
                aodEnabled = isAodEnabled(),
            )
        }.onFailure { AppMonitorLogger.e("Xiaomi vendor extras (touch boost / AOD colour) failed for '$pkgName' sw=$currentSwitchId", it) }

        return buildString {
            appendLine("focused_app $focusedApp")
            appendLine("screen_awake $screenAwake")
            appendLine("battery_saver $batterySaver")
            appendLine("zen_mode $zenMode")
            appendLine("battery_level $batteryLevel")
            appendLine("is_charging $isCharging")
            appendLine("app_name $appName")
            appendLine("refresh_rate $currentRefreshRate")
            appendLine("max_refresh_rate $maxRefreshRate")
            appendLine("switch_id $currentSwitchId")
            // تعديلات per-app حيّة الآن (أثناء التطبيق أو مهلة السماح):
            // يقرؤها محرك MAX AI فيتحول إلى وضع المراقبة بلا تدخل.
            appendLine("perapp_active ${if (perAppOverridesActive) 1 else 0}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Per-App Config Helpers (reads APPLIST_JSON, applies/reverts settings)
    // ─────────────────────────────────────────────────────────────────────

    private fun shellExec(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).apply {
                waitFor()
                destroy()
            }
        } catch (_: Exception) {}
    }

    private fun shellRead(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val result = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            p.destroy()
            result
        } catch (_: Exception) { "" }
    }

    /**
     * كتابة عقدة sysfs عبر رقصة chmod نفسها التي تستخدمها ثنائيات الوحدة
     * (binutils setsgov / binprofiles write_unlock_core): على HyperOS 3
     * (المُختبر الجاهز rodin/Dimensity 8400 Ultra) تُرفض الكتابة الجذرية
     * المباشرة على عقد 0444 بـEACCES بينما تنجح الكتابة بعد chmod. بدون
     * هذه الرقصة فشلت كل كتابات scaling_governor/min/max هنا بصمت — سجل
     * الجهاز الحقيقي أظهر live-value-mismatch في كل PERAPP_COMMIT. يُستعاد
     * الوضع الأصلي للعقدة بعد الكتابة حفاظًا على حالة العقدة كما كانت.
     */
    private fun sysfsWrite(path: String, value: String) {
        shellExec(
            "m=\$(stat -c %a '$path' 2>/dev/null); " +
                "chmod 644 '$path' 2>/dev/null; " +
                "echo '$value' > '$path' 2>/dev/null; " +
                "chmod \$m '$path' 2>/dev/null"
        )
    }

    private fun refreshConfigCacheIfChanged() {
        runCatching {
            val file = File(MaxManagerPaths.APPLIST_JSON)
            val modified = if (file.exists()) file.lastModified() else -1L
            if (modified != cachedGameListModified) {
                cachedGameListText = if (file.exists()) file.readText() else null
                cachedGameListModified = modified
            }
        }.onFailure { AppMonitorLogger.e("EVENT=APPLIST_CACHE_REFRESH_FAILED", it) }
    }

    private fun readAppConfigField(pkgName: String, field: String): String {
        refreshConfigCacheIfChanged()
        val json = cachedGameListText ?: return ""
        return try {
            // Fast regex-based extraction: find the package block and the field inside it.
            // واسم الحزمة يُهرَّب قبل أن يصير نمطًا: أسماء الحزم تحمل نقاطًا (أي محرفًا يقبل
            // أي حرف) — فلا يخطئ المطابقة اليوم ولا يهرب حرف خاص لو تغيّر نطاق الأسماء لاحقًا.
            val pkgPattern = Regex(""""${Regex.escape(pkgName)}"\s*:\s*\{([^}]+)\}""")
            val pkgBlock = pkgPattern.find(json)?.groupValues?.getOrNull(1) ?: return ""
            val fieldPattern = Regex(""""$field"\s*:\s*"([^"]*)"|\b$field\b\s*:\s*([^,}\n]+)""")
            fieldPattern.find(pkgBlock)?.groupValues?.let { it[1].ifEmpty { it[2].trim() } } ?: ""
        } catch (_: Exception) { "" }
    }

    @Synchronized
    private fun applyPerAppConfig(pkgName: String) {
        hardwareControlRegistry.beginApp(pkgName)
        // سجل نتائج جلسة جديدة: حالة تطبيق سابق تُقرأ على أنها حالة الآن = تشخيص كاذب.
        PerAppHardwareStatus.beginSession(pkgName)
        // Refresh cache and verify this package has an enabled Per-App entry.
        refreshConfigCacheIfChanged()
        if (cachedGameListText?.contains("\"$pkgName\":") != true) return

        // Save the live kernel state before any per-app override.
        val gpu = GpuHardwareBackend.selection().device
        savedGpuNode = gpu?.path.orEmpty()
        savedGpuGovernor = gpu?.governor.orEmpty()
        savedGpuMinFreq = gpu?.minFreq?.toString().orEmpty()
        savedGpuMaxFreq = gpu?.maxFreq?.toString().orEmpty()
        savedGpuBaseline = gpu?.let { GpuHardwareBackend.captureBaseline(it) }
        savedCpuGovernors.clear()
        shellRead("for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -f \"\$p/scaling_governor\" ] && echo \"\$p=\$(cat \$p/scaling_governor)\"; done").split("\n").forEach { line ->
            val eq = line.indexOf('=')
            if (eq > 0) savedCpuGovernors[line.substring(0, eq)] = line.substring(eq + 1).trim()
        }
        savedCpuMinFreqs.clear()
        savedCpuMaxFreqs.clear()
        CpuHardwareBackend.policies().forEach { policy ->
            policy.minKHz?.let { savedCpuMinFreqs[policy.path] = it.toString() }
            policy.maxKHz?.let { savedCpuMaxFreqs[policy.path] = it.toString() }
        }

        if (!baselineCaptured) {
            baselineGpuGovernor = savedGpuGovernor
            baselineCpuGovernors.clear()
            baselineCpuGovernors.putAll(savedCpuGovernors)
            baselineCaptured = true
            AppMonitorLogger.i("EVENT=PERAPP_GOV_BASELINE_CAPTURED cpu=${baselineCpuGovernors.size} gpu=$baselineGpuGovernor sw=$currentSwitchId")
            val baselineJournal = linkedMapOf<String, String>()
            baselineCpuGovernors.forEach { (path, value) -> baselineJournal["cpu_governor:$path"] = value }
            if (savedGpuNode.isNotBlank()) baselineJournal["gpu_node"] = savedGpuNode
            if (baselineGpuGovernor.isNotBlank()) baselineJournal["gpu_governor"] = baselineGpuGovernor
            if (savedGpuMinFreq.isNotBlank()) baselineJournal["gpu_min_freq"] = savedGpuMinFreq
            if (savedGpuMaxFreq.isNotBlank()) baselineJournal["gpu_max_freq"] = savedGpuMaxFreq
            PerAppRecoveryStore.markActive(pkgName, baselineJournal)
        }
        savedThermalProfile = shellRead("getprop sys.thermal.profile")
        // These 4 reads used to call the Settings ContentProvider API directly with
        // no runCatching around them at all. On this device/ROM the process's faked
        // system Context isn't a real app registered with ActivityManagerService, so
        // EVERY Settings.* ContentProvider call throws:
        //   SecurityException: Unable to find app for caller
        //   android.app.IApplicationThread$Stub$Proxy@... when getting content provider settings
        // Because these 4 lines were unguarded, that exception aborted
        // applyPerAppConfig() right here on every single app switch -- before any of
        // the per-app knobs below (GPU profile, CPU governor, touch boost, etc.) ever
        // ran. The daemon still correctly saw the app switch (app_status was written
        // fine by writeStatus()/buildStatus()), but literally none of the per-app
        // overrides ever got applied on this ROM, which is what looked like "the
        // daemon doesn't know which app is open."
        // Fixed the same way PerAppRefreshRateController.writeSetting() already does
        // elsewhere in this project: try the API, fall back to the `settings` shell
        // command (a legitimate caller, so it works even when the API path doesn't),
        // and never let one failing read take the rest of the function down with it.
        savedZenMode = runCatching {
            systemContext?.contentResolver?.let { Settings.Global.getInt(it, "zen_mode", 0) }
        }.getOrNull() ?: shellRead("settings get global zen_mode").toIntOrNull()
        savedPeakRefreshRate = runCatching {
            systemContext?.contentResolver?.let { Settings.System.getString(it, "peak_refresh_rate") }
        }.getOrNull()
            ?: shellRead("settings get system peak_refresh_rate").takeIf { it.isNotEmpty() && it != "null" }
            ?: ""
        savedMinRefreshRate = runCatching {
            systemContext?.contentResolver?.let { Settings.System.getString(it, "min_refresh_rate") }
        }.getOrNull()
            ?: shellRead("settings get system min_refresh_rate").takeIf { it.isNotEmpty() && it != "null" }
            ?: ""
        savedVendorRefreshSnapshot = runCatching { systemContext?.let { PerAppRefreshRateController.snapshot(it) } }.getOrNull()

        runCatching {
            PerAppRecoveryStore.capture(
                PerAppRecoveryStore.Snapshot(
                    bootId = PerAppRecoveryStore.bootId(),
                    packageName = pkgName,
                    gpuNode = savedGpuNode,
                    gpuGovernor = savedGpuGovernor,
                    gpuMin = savedGpuMinFreq,
                    gpuMax = savedGpuMaxFreq,
                    cpuGovernors = savedCpuGovernors.toMap(),
                    cpuMinFreqs = savedCpuMinFreqs.toMap(),
                    cpuMaxFreqs = savedCpuMaxFreqs.toMap(),
                    zenMode = savedZenMode?.toString().orEmpty(),
                    peakRefresh = savedPeakRefreshRate,
                    minRefresh = savedMinRefreshRate,
                    vendorRefreshNamespace = savedVendorRefreshSnapshot?.vendorNamespace.orEmpty(),
                    vendorRefreshKey = savedVendorRefreshSnapshot?.vendorKey.orEmpty(),
                    vendorRefreshValue = savedVendorRefreshSnapshot?.vendorValue.orEmpty(),
                    vendorRefreshAltKey = savedVendorRefreshSnapshot?.vendorAltKey.orEmpty(),
                    vendorRefreshAltValue = savedVendorRefreshSnapshot?.vendorAltValue.orEmpty(),
                    thermalProfile = savedThermalProfile,
                    hwUiProp = shellRead("getprop persist.sys.ui.hw"),
                    disableHwProp = shellRead("getprop debug.viewroot.disableHW"),
                    hapticEnabled = shellRead("settings get system haptic_feedback_enabled").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                )
            )
        }.onFailure { AppMonitorLogger.w("EVENT=PERAPP_RECOVERY_CAPTURE_FAILED pkg=$pkgName sw=$currentSwitchId", it) }


        // Each knob below is independent: if one throws (e.g. a
        // WRITE_SECURE_SETTINGS SecurityException on ROMs that don't grant
        // it), the rest still get a chance to apply instead of the whole
        // per-app config silently aborting partway through.
        // Register the concrete hardware knobs with the ownership layer before applying them.
        // This makes drift repair use the same desired value and prevents a later subsystem
        // from silently becoming the owner of a per-app override.
        runCatching {
            val cpuGovernor = readAppConfigField(pkgName, "cpu_governor")
            if (cpuGovernor.isNotBlank() && cpuGovernor != "default") {
                CpuHardwareBackend.policies().forEach { policy ->
                    val baseline = policy.governor
                    val key = "cpu_governor:${policy.name}"
                    // ناتج التسجيل كان يُهمَل هنا تمامًا: لا حالة في الواجهة ولا سطر في السجل.
                    // فحاكمٌ رُفض بقفل يدوي كان يبدو كأنه «لم يعمل» بلا سبب مكتوب، والسبب
                    // موجود في المُحكِّم أصلًا. صار يُقرأ ويُسجَّل.
                    val owned = hardwareControlRegistry.ownGovernor(
                        key = key,
                        desired = cpuGovernor,
                        apply = { value -> CpuHardwareBackend.setPolicyGovernor(policy.path, value).successful },
                        read = { CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.governor },
                        baseline = baseline,
                        restore = { value -> CpuHardwareBackend.setPolicyGovernor(policy.path, value).successful },
                    )
                    val refusal = hardwareControlRegistry.refusalReasons()[key]
                    if (!owned && refusal == null && policy.governors.isNotEmpty() && cpuGovernor !in policy.governors) {
                        // سبب قبل المُحكِّم: النواة لا تُعلن هذا الحاكم لهذه السياسة أصلًا. وتمييزه
                        // مهم: «غير مدعوم» إصلاحه تغيير الاختيار، و«مرفوض» إصلاحه تحرير القفل.
                        noteHardware(key, Outcome.UNSUPPORTED, "governor-not-advertised", cpuGovernor, policy.governor.orEmpty())
                    } else {
                        noteOwnedOutcome(key, owned, refusal, cpuGovernor, policy.governor.orEmpty())
                    }
                }
            } else {
                noteHardware("cpu_governor", Outcome.SKIPPED, "governor-is-default")
            }

            val gpuGovernor = readAppConfigField(pkgName, "gpu_governor")
            if (gpuGovernor.isNotBlank() && gpuGovernor != "default") {
                val gpuNode = savedGpuNode.takeIf { it.isNotBlank() }
                    ?: PerAppKernelUtil.findGpuNode()
                val generic = gpuNode?.let(GpuHardwareBackend::refresh)
                    ?: GpuHardwareBackend.selection().device
                if (generic == null) {
                    noteHardware("gpu_governor", Outcome.UNSUPPORTED, "no-gpu-provider", gpuGovernor)
                } else if (gpuGovernor !in generic.governors) {
                    noteHardware("gpu_governor", Outcome.UNSUPPORTED, "governor-not-advertised", gpuGovernor, generic.governor.orEmpty())
                } else {
                    val key = "gpu_governor:${generic.name}"
                    val owned = hardwareControlRegistry.ownGovernor(
                        key = key,
                        desired = gpuGovernor,
                        apply = { value -> GpuHardwareBackend.setGovernor(generic, value).successful },
                        read = { GpuHardwareBackend.refresh(generic.path)?.governor },
                        baseline = generic.governor,
                        restore = { value -> GpuHardwareBackend.setGovernor(generic, value).successful },
                    )
                    noteOwnedOutcome(key, owned, hardwareControlRegistry.refusalReasons()[key], gpuGovernor, generic.governor.orEmpty())
                }
            } else {
                noteHardware("gpu_governor", Outcome.SKIPPED, "governor-is-default")
            }
        }.onFailure { AppMonitorLogger.e("ownership: governor registration failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val encodedPolicyControls = readAppConfigField(pkgName, "cpu_policy_controls")
            val policyControls = decodePerAppCpuPolicyControls(encodedPolicyControls)
            if (encodedPolicyControls.isNotBlank() && policyControls.isEmpty()) {
                activePerAppCpuPackage = pkgName
                writePerAppCpuStatus(pkgName, "failed", "CPU controls are invalid")
                AppMonitorLogger.w("EVENT=PERAPP_CPU_FAILED pkg=$pkgName reason=invalid-controls sw=$currentSwitchId")
            } else if (policyControls.isNotEmpty()) {
                activePerAppCpuPackage = pkgName
                writePerAppCpuStatus(pkgName, "applying", "Applying CPU controls")
                val policies = CpuHardwareBackend.policies().associateBy { it.name }
                // كل سياسة تُحاكَم وحدها. كان `firstFailure` واحدًا يُسقط **كل** السياسات
                // (`acquiredKeys.asReversed().forEach(release)`): سياسةٌ واحدة يقيّدها الـvendor
                // كانت تُلغي تحكّمًا ناجحًا على بقية العناقيد ثم تُسترجع مقابضها — فالمستخدم يقرأ
                // «فشل» بينما نصف العمل كان قد نجح. والأصحّ عزل الفشل: الناجح يبقى مفعّلًا، والفاشل
                // يُعلن بحدّه ومع سببه.
                val failures = mutableListOf<String>()
                var appliedPolicies = 0
                policyControls.forEach { control ->
                    val policy = policies[control.policyName]
                    val key = HardwareControlKey.cpuLimits(control.policyName)
                    if (policy == null) {
                        failures += "${control.policyName} is unavailable"
                        noteHardware(key, Outcome.UNSUPPORTED, "policy-unavailable", "${control.minKHz}:${control.maxKHz}")
                        return@forEach
                    }
                    val supported = policy.availableFrequenciesKHz
                    val provenMin = policy.hwMinKHz ?: supported.firstOrNull()
                    val provenMax = policy.hwMaxKHz ?: supported.lastOrNull()
                    // `provenMax` is the hardware bound; the live `scaling_max_freq` may be lower
                    // because a vendor/thermal/power policy currently owns the ceiling. A saved
                    // Per-App target above that live ceiling can therefore never verify. Bound only
                    // the runtime request here, keep the saved intent intact, and log the adaptation.
                    val liveMax = policy.maxKHz?.takeIf { it > 0L }
                    val runtimeMax = listOfNotNull(provenMax, liveMax).minOrNull()
                    val normalizedMin = control.minKHz.let { requested ->
                        val bounded = runtimeMax?.let { requested.coerceAtMost(it) } ?: requested
                        supported.lastOrNull { it <= bounded } ?: supported.firstOrNull() ?: bounded
                    }
                    val normalizedMax = control.maxKHz.let { requested ->
                        val bounded = runtimeMax?.let { requested.coerceAtMost(it) } ?: requested
                        supported.lastOrNull { it <= bounded } ?: supported.firstOrNull() ?: bounded
                    }.coerceAtLeast(normalizedMin)
                    if (normalizedMin != control.minKHz || normalizedMax != control.maxKHz) {
                        AppMonitorLogger.w(
                            "EVENT=PERAPP_CPU_TARGET_CAPPED pkg=$pkgName policy=${policy.name} requested=${control.minKHz}:${control.maxKHz} live_cap=${runtimeMax ?: "?"} applied=$normalizedMin:$normalizedMax sw=$currentSwitchId"
                        )
                    }
                    if (supported.isEmpty()) {
                        failures += "${control.policyName} has no discoverable frequency table"
                        noteHardware(key, Outcome.UNSUPPORTED, "no-advertised-frequency-range", "${control.minKHz}:${control.maxKHz}")
                        return@forEach
                    }
                    if (provenMin == null || provenMax == null || normalizedMin < provenMin || normalizedMax > provenMax) {
                        failures += "${control.policyName} range is outside proven hardware bounds"
                        noteHardware(
                            key,
                            Outcome.UNSUPPORTED,
                            "outside-proven-hardware-bounds",
                            "$normalizedMin:$normalizedMax",
                            "$provenMin:$provenMax",
                        )
                        return@forEach
                    }
                    val requested = "$normalizedMin:$normalizedMax"
                    val liveRange = "${policy.minKHz ?: ""}:${policy.maxKHz ?: ""}"
                    fun liveRangeNow(): String? = CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.let {
                        "${it.minKHz ?: ""}:${it.maxKHz ?: ""}"
                    }
                    val owned = hardwareControlRegistry.ownValue(
                        key = key,
                        desired = requested,
                        apply = { value ->
                            val parts = value.split(":", limit = 2)
                            CpuHardwareBackend.setPolicyLimits(policy.path, parts[0].toLongOrNull(), parts[1].toLongOrNull()).successful
                        },
                        read = { liveRangeNow() },
                        baseline = liveRange,
                        restore = { value ->
                            val parts = value.split(":", limit = 2)
                            CpuHardwareBackend.setPolicyLimits(policy.path, parts[0].toLongOrNull(), parts[1].toLongOrNull()).successful
                        },
                        // المدى الحيّ **داخل** الطلب = مُلبّى: السائق يرفع الأرضية أو يهبط بالسقف
                        // إلى OPP مُعلن، وذلك تلبية لا فشل. والتساوي كان يسترجع خط الأساس فيرى
                        // المستخدم المقبض يرتدّ بلا سبب.
                        verify = HardwareVerification::rangeContained,
                    )
                    val live = liveRangeNow()
                    val refusal = hardwareControlRegistry.refusalReasons()[key]
                    if (owned) {
                        appliedPolicies++
                        hardwareUserIntent[key] = requested
                        noteOwnedOutcome(key, true, null, requested, live.orEmpty())
                    } else {
                        failures += "${policy.name} requested=$requested live=${live ?: "unreadable"} (${refusal ?: "not-verified"})"
                        noteOwnedOutcome(key, false, refusal, requested, live.orEmpty())
                    }
                }
                when {
                    appliedPolicies == policyControls.size -> {
                        writePerAppCpuStatus(pkgName, "applied", "CPU controls verified")
                        AppMonitorLogger.i("EVENT=PERAPP_CPU_APPLIED pkg=$pkgName policies=${policyControls.size} sw=$currentSwitchId")
                    }
                    appliedPolicies > 0 -> {
                        // جزئي: ما نجح يبقى على العتاد، والرسالة تقول الصدق بعددِ ما بقي.
                        writePerAppCpuStatus(pkgName, "failed", "${failures.first()} — kept $appliedPolicies/${policyControls.size}")
                        AppMonitorLogger.w("EVENT=PERAPP_CPU_PARTIAL pkg=$pkgName kept=$appliedPolicies total=${policyControls.size} reason=${failures.first()} sw=$currentSwitchId")
                    }
                    else -> {
                        writePerAppCpuStatus(pkgName, "failed", failures.first())
                        AppMonitorLogger.w("EVENT=PERAPP_CPU_FAILED pkg=$pkgName reason=${failures.first()} sw=$currentSwitchId")
                    }
                }
            }
        }.onFailure {
            activePerAppCpuPackage = pkgName
            writePerAppCpuStatus(pkgName, "failed", "CPU control failed")
            AppMonitorLogger.e("ownership: per-app CPU control failed for '$pkgName' sw=$currentSwitchId", it)
        }

        runCatching {
            val cpuMin = readAppConfigField(pkgName, "cpu_min_freq").toLongOrNull()
            val cpuMax = readAppConfigField(pkgName, "cpu_max_freq").toLongOrNull()
            val hasPolicyControls = readAppConfigField(pkgName, "cpu_policy_controls").isNotBlank()
            if (!hasPolicyControls && (cpuMin != null || cpuMax != null)) {
                CpuHardwareBackend.policies().forEach { policy ->
                    val baseline = "${policy.minKHz ?: ""}:${policy.maxKHz ?: ""}"
                    // الترددات المُعلنة هي مرجع الطلب، لا ما بين الحدّين: قيمةٌ غير
                    // مُعلنة لا يُرفض كتابتها بل تُبدَّل، فيُحكم على النجاح بالفشل
                    // (`CpuHardwareBackend.snapToAvailableAtOrBelow` يحمل القياس).
                    val minSnapped = cpuMin?.let { CpuHardwareBackend.snapToAvailableAtOrBelow(policy, it) }
                    val maxSnapped = cpuMax?.let { CpuHardwareBackend.snapToAvailableAtOrBelow(policy, it) }
                    val desired = "${minSnapped ?: ""}:${maxSnapped ?: ""}"
                    val key = HardwareControlKey.cpuLimits(policy.name)
                    val owned = hardwareControlRegistry.ownValue(
                        key = key,
                        desired = desired,
                        apply = { value ->
                            val parts = value.split(":", limit = 2)
                            val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            CpuHardwareBackend.setPolicyLimits(policy.path, min, max).successful
                        },
                        read = {
                            CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.let {
                                "${it.minKHz ?: ""}:${it.maxKHz ?: ""}"
                            }
                        },
                        baseline = baseline,
                        restore = { value ->
                            val parts = value.split(":", limit = 2)
                            val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            CpuHardwareBackend.setPolicyLimits(policy.path, min, max).successful
                        },
                        verify = HardwareVerification::rangeContained,
                    )
                    if (owned) hardwareUserIntent[key] = desired
                    noteOwnedOutcome(
                        knob = key,
                        owned = owned,
                        refusal = hardwareControlRegistry.refusalReasons()[key],
                        expected = desired,
                        live = CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }
                            ?.let { "${it.minKHz ?: ""}:${it.maxKHz ?: ""}" }.orEmpty(),
                    )
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: CPU frequency registration failed for '$pkgName' sw=$currentSwitchId", it) }

        // One GPU frequency owner handles both named profiles and explicit caps.
        runCatching {
            val profile = readAppConfigField(pkgName, "gpu_profile").ifEmpty {
                val legacy = readAppConfigField(pkgName, "thermal_profile")
                when (legacy) { "powersave" -> "power"; else -> legacy }
            }
            // "default" تعني **لا شيء يُنفَّذ**، وهي تختلف عن "لم نستطع": الأولى نتيجة
            // مقصودة تُسجَّل `skipped`، والثانية فشل يحمل سببه. وخلطهما هو ما يجعل الواجهة
            // تقول «فشل» لقيمة لم تُطلب أصلًا.
            val explicit = readAppConfigField(pkgName, "gpu_max_freq").toLongOrNull()
            if (explicit == null && (profile.isBlank() || profile == "default")) {
                noteHardware("gpu_profile", Outcome.SKIPPED, "profile-is-default")
                return@runCatching
            }
            // كل بوابة تخرج أدناه كانت تخرج بـ`return@runCatching` **صامتًا**: لا سطر في
            // السجل ولا حالة في الواجهة، فيرى المستخدم «لم يحدث شيء» ولا يعرف أيّ بوابة
            // أُغلقت. صار لكل خروج رمز سببه، والسبب يأتي من الجهاز نفسه لا من تخميننا.
            val gpuSelection = GpuHardwareBackend.selection()
            val device = gpuSelection.device
            if (device == null) {
                noteHardware("gpu_profile", Outcome.UNSUPPORTED, "no-gpu-provider:${gpuSelection.reason}", profile)
                return@runCatching
            }
            if (!(device.rangeWritable || device.exactLockWritable)) {
                noteHardware("gpu_profile", Outcome.NOT_WRITABLE, "gpu-provider-not-writable:${device.name}", profile)
                return@runCatching
            }
            // The OPP list is a capability catalogue, while the live max_freq
            // is a runtime ceiling that vendor thermal/power policy may lower.
            // Read it immediately before planning the per-app target so a stale
            // catalogue cannot make us request an impossible frequency.
            val liveAtPlan = GpuHardwareBackend.refresh(device.path)
            if (liveAtPlan == null) {
                noteHardware("gpu_profile", Outcome.UNSUPPORTED, "provider-disappeared", profile)
                return@runCatching
            }
            val liveCap = GpuHardwareBackend.configurableMaxFrequency(liveAtPlan)
            AppMonitorLogger.i(
                "EVENT=PERAPP_GPU_CAPABILITY_SCAN pkg=$pkgName profile=$profile" +
                    " provider=${liveAtPlan.name} path=${liveAtPlan.path}" +
                    " advertised_max=${liveAtPlan.frequencies.maxOrNull() ?: "none"}" +
                    " live_max=${liveAtPlan.maxFreq ?: "none"}" +
                    " current=${liveAtPlan.currentFreq ?: "none"}" +
                    " opp_count=${liveAtPlan.frequencies.size}" +
                    " fixed_index=${liveAtPlan.mtkFixedIndexPath ?: "none"}" +
                    " evidence=${liveAtPlan.evidence.joinToString(",")}" +
                    " sw=$currentSwitchId"
            )
            if (liveCap == null) {
                noteHardware("gpu_profile", Outcome.UNSUPPORTED, "no-advertised-frequency-range", profile)
                return@runCatching
            }
            val presetPercent = ProfilePresetStore.percentFor(systemContext, profile)
            // نسبة البروفايل من **قدرة الجهاز** لا من سقفه الحيّ: ٨٥٪ من ١٣٠٠ = ١١٠٥، بينما
            // ٨٥٪ من سقف حيّ عند ٧٥٤ تطلب ٦٢٤ — أي **أدنى من الجهاز كما هو**، فيصير «gaming»
            // أبردَ من عدم المسّ. فيبقى معنى النسبة ثابتًا وإن غيّرت المنصّة سقفها قبل وصول
            // التطبيق إلى المقدّمة. والسقف الحيّ يُستعمل في موضعه الصحيح بعد قليل: ليقرّر هل
            // الطلب يزيد عليه (فيلزمه تحرير) أم هو طلب تبريد دونه.
            val advertisedMaxHz = liveAtPlan.frequencies.filter { it > 0L }.maxOrNull()
            // ── وإعداد يحمل الاختيارين معًا لا يُحكَم عليه صامتًا ────────────────────────────
            //
            // صار في الشاشة مالك واحد للمقبض (اختيار البروفايل يُفرغ التردد الصريح والعكس)، فاجتماعهما
            // لا يأتي من الواجهة — يأتي من إعداد قديم كُتب قبل الإصلاح، أو من ملف مُستورد. وحكمُه
            // الصريح أولى من حكم صامت: التردد الصريح هو ما يُنفَّذ (قيمة يراها المستخدم في الشاشة
            // أيضًا)، ويُقال ذلك في السجل بدل أن يبدو «الأداء» بلا أثر.
            if (explicit != null && profile.isNotBlank() && profile != "default") {
                AppMonitorLogger.w(
                    "EVENT=PERAPP_GPU_EXPLICIT_OVERRIDES_PROFILE pkg=$pkgName profile=$profile" +
                        " explicit=$explicit reason=explicit-frequency-wins-over-profile sw=$currentSwitchId"
                )
            }
            val requested = explicit ?: PerAppKernelUtil.pickProfileFrequency(
                liveAtPlan.frequencies,
                profile,
                presetPercent,
                // القدرة أساس النسبة لكل البروفايلات: ما دونها يُقيَّد عند التنفيذ بالسقف الحيّ
                // لأّنه طلب تبريد — والتقييد يُعلَن (`PERAPP_GPU_TARGET_CAPPED`) ولا يُسكَت عنه.
                maximumHz = null,
            )
            if (requested == null) {
                noteHardware("gpu_profile", Outcome.UNSUPPORTED, "unsupported-profile:$profile", profile, liveCap.toString())
                return@runCatching
            }
            // ملاحظة عقد (مقصودة، لا عطب): هذا المقبض يتحقّق من **السقف** (`max`) لا من المدى
            // كاملًا، ولهذا لا يستعمل `encodeLive` المشتركة: لو قارنّا المدى أيضًا، لأدى أدنى
            // تثبيت من السائق للحدّ الأدنى (`min`) — وهو ما يفعله كثير من السائقين — إلى تصنيف
            // سقفٍ ناجح فاشلًا ثم استرجاعه. التحقق من السقف هو ما طلبه المستخدم وما يفعله
            // التطبيق فعليًا. أما `encodeLive` فهي الصيغة القياسية في مواضعها (GPU Studio و
            // `PerAppFrequencyController`) حيث الطلب مدى كامل وليس سقفًا فقط.
            //
            // القيمة المطلوبة تُلتقط من ترددات الجهاز المُعلنة **قبل** أن تصير عقدًا.
            // المُحكِّم يُثبت المعاملة بتساوي نصّين (المطلوب = المقروء)، فأي قيمة لا
            // يستطيع الجهاز حملها — إعداد محفوظ من نواة أو جهاز آخر، أو ملف مستورد،
            // أو قائمة OPP تغيّرت بعد تحديث نواة — يُبدّلها السائق بقيمة أخرى، فلا
            // يتساوى النصّان أبدًا ويُعاد الطلب في كل دورة انحراف بلا نهاية.
            // والقياس من سجل حقيقي (2026-09-20): `APPLY_VERIFY_FAILED knob=gpu_profile
            // expected=1300000000 live=754000000` ثم `APPLY_DRIFT_REASSERT_FAILED`
            // بعد ثانيتين، مرّتين لكل تطبيق — والجهاز لا يبلغ السقف المطلوب أصلًا.
            // ── وهل يلزم **تحرير سقف المصنّع** لهذا الطلب؟ ─────────────────────────────────
            //
            // السؤال ليس «هل النسبة ١٠٠٪؟» بل «هل الطلب يزيد على ما يسمح به الجهاز الآن؟».
            // وبالفارق بينهم وقع العطب المقيس («Performance لا يعمل» و«gaming بلا أثر»):
            //
            //  · الحكم القديم كان `explicit == null || explicit >= capability` — أي أن **كل**
            //    طلب بروفايل (بلا تردد صريح) يُعدّ طلب قدرة، فلا يُقيَّد بالسقف الحيّ.
            //  · والحكم النهائي (`ceilingSatisfied` لطلب القدرة) كان يسأل عن **بلوغ القدرة**؛
            //    وعلى جهاز تحتفظ منصّته بسقف ٧٥٤ دون ١٣٠٠ لا تُلبّى أبدًا ⇒ فشل ⇒ استرجاع خط
            //    الأساس — و**استرجاع خط الأساس يمحو التحرير نفسه**، فأصبح «Performance» بلا أثر
            //    بالبناء لا بالعتاد.
            //
            // فالفصل الآن صريح: [GpuCeilingPolicy.releaseRequired] تقول هل نحتاج التحرير،
            // و[GpuCeilingPolicy.releaseVerdict] تحكم **على التحرير** (وهو ما نملكه) لا على
            // سياسة المنصّة (وهي ما نقيسه ونعلنه).
            val releaseRequired = GpuCeilingPolicy.releaseRequired(requested, liveCap)
            val target = GpuHardwareBackend.snapToAvailableAtOrBelow(
                liveAtPlan,
                requested,
                respectLiveCeiling = !releaseRequired,
            )
            if (target == null) {
                noteHardware("gpu_profile", Outcome.UNSUPPORTED, "unsupported-frequency", requested.toString(), liveCap.toString())
                return@runCatching
            }
            if (explicit != null && target != explicit) {
                AppMonitorLogger.w(
                    "EVENT=PERAPP_GPU_TARGET_CAPPED pkg=$pkgName requested=$explicit live_cap=$liveCap applied=$target sw=$currentSwitchId"
                )
            }
            // سؤال «طلبت ١٠٠٪ فلماذا الكروت يقول ٧٥٤؟» يُجاب هنا بلا تفسير منّا: الطلب هو قدرة
            // الجهاز، والقيمة التي تُقرأ بعد الكتابة هي ما تسمح به سياسة الجهاز الآن. والاثنان
            // مكتوبان في السطر نفسه، فلا يُقرأ الفرق عطلًا في التطبيق.
            if (releaseRequired && target > liveCap) {
                AppMonitorLogger.i(
                    "EVENT=PERAPP_GPU_CAPABILITY_REQUESTED pkg=$pkgName profile=$profile" +
                        " requested=$target live_before=$liveCap note=device-policy-may-hold-lower sw=$currentSwitchId"
                )
            }
            // ── كيف يُنفَّذ هذا السقف على **هذا** الجهاز؟ قرار واحد صريح ────────────────
            //
            // وُجد لأن السجل المقيس (rodin · MTK6899 · 2026-09-22) أظهر أن `max_freq` يقرأ أعلى
            // درجة عند الجهاز **أصلًا** (1300000000) بينما التردد الجاري 260MHz: فالحاكم يقرأ
            // قيمة تساوي الطلب فيحكم «مُلبّى» ويتخطّى `apply` — و**تحرير سقف المنصّة كان داخل
            // `apply`**. والنتيجة في الحزمة: ٧٥ جلسة `profile=performance` كلها «نجحت» بصفر كتابة
            // على أي عقدة GPU (مقابل ١٢ كتابة على نظيرها في CPU). فصار المُقَاس الذي يُحكم به
            // هو **السقف** لا `max_freq` وحده، وصار للطلب شكل مُعلَن بدل تفرّع ضمني:
            //
            //  · `RELEASE_ONLY` — الطلب عند قدرة الجهاز: تُحرَّر سلطة المصنّع، ويُقاس الحكم من
            //    **قراءة السقف** (سقف العقدة + سقف GED المخصّص + حالة تبريد GPU) لا من `max_freq`
            //    وحده، لأن `max_freq` على هذا الجهاز ليس ما يقصّ. وهذا وحده يكفي لإصلاح تخطّي
            //    `apply`: السقف المقروء مقيّد ⇒ الحاكم لا يقول «مُلبّى» ⇒ يُنفَّذ التحرير داخل
            //    معاملته المملوكة نفسها (بخط أساسها واستعادتها). ولا تحرير خارج المعاملة: تحريرٌ
            //    بلا ملكية كان يرفع حماية المصنّع حتى حين يرفض الحاكمُ (قفل يدوي) كتابةَ التردد.
            //  · `RANGE`/`PIN` — سقف أدنى من القدرة: يُكتب كما كان، ولا يُرفع سقف المصنّع (طلب
            //    تبريد لا يجوز أن يرفع حماية وَضعها المصنّع ثم يكتب سقفه فوقها).
            val realization = GpuCeilingPolicy.realize(
                requestedHz = target,
                advertisedMaxHz = advertisedMaxHz,
                // والسؤال هو «هل تقبل عقدتا المدى كتابة سقف؟» لا «هل للجهاز مسار تثبيت OPP؟»:
                // اشتراط غياب مسار التثبيت هو ما حوّل كل سقف على MTK إلى تثبيت درجة واحدة.
                rangeWritable = liveAtPlan.devfreqCeilingWritable,
                pinAvailable = liveAtPlan.exactLockWritable,
            )
            if (realization == GpuCeilingPolicy.Realization.UNSUPPORTED) {
                noteHardware("gpu_profile", Outcome.UNSUPPORTED, "unsupported-frequency", target.toString(), liveCap.toString())
                return@runCatching
            }
            // وطلبٌ فوق ما يسمح به الجهاز **تحرير** لا كتابة فقط: يُحرَّر سقف المصنّع ويُرفع قفل
            // OPP إن كان قائمًا، ثم يُكتب السقف المطلوب — ولا يُثبَّت تردد إلا حين لا يحمل المدى
            // الطلبَ فعلًا (يُقاس، لا يُفترض)، وطلبُ التبريد (دون السقف الحيّ) لا يلمس حماية المصنّع.
            val releaseCeiling = releaseRequired
            val ceilingShaped = releaseRequired
            val ceilingCapture = if (releaseRequired) PlatformCeilingAuthority.captureGpuCeiling() else null
            val clockAtPlan = GpuHardwareBackend.currentFrequencyHz(liveAtPlan)

            val baseline = GpuHardwareBackend.captureBaseline(liveAtPlan)
            val desired = target.toString()
            val gpuKey = HardwareControlKey.gpuFrequency(device.name)
            // هل نُفِّذ الطلب بتثبيت درجة (لأن المدى لم يحمله)؟ يُعلَم من داخل المعاملة — وهي
            // المعلومة التي تحوّل «فشل المدى» إلى «نجاح بتثبيت» أو إلى فشل صريح بالتردد المقيس.
            var pinnedViaIndex = false
            val owned = hardwareControlRegistry.ownValue(
                key = gpuKey,
                desired = desired,
                apply = { value -> value.toLongOrNull()?.let { wantedHz ->
                        val live = GpuHardwareBackend.refresh(device.path) ?: return@let false
                        if (releaseCeiling) {
                            // التحرير **داخل** المعاملة المملوكة: بخط أساسها، وباستعادتها، وبإعادة
                            // المحاولة في حلقة الانحراف. وتحريرٌ قبلها كان يرفع حماية المصنّع حتى
                            // على مقبض يرفض الحاكم كتابته (قفل يدوي) — أي بلا ملكية.
                            GpuHardwareBackend.releaseVendorCeiling()
                            // ويُحرَّر معه **قفل OPP ثابت** إن كان قائمًا: قفلٌ من جلسة سابقة أو من
                            // أداة أخرى يقصّ التردد من **خارج** `devfreq`، فتبقى قراءة السقف عند
                            // القدرة بينما الجهاز عالق على درجة واحدة (وهو العطب المقيس: «أداء»
                            // يعطي ٦٥٠). وفهرس القفل محفوظ في خط الأساس فيُعاد عند الخروج.
                            GpuHardwareBackend.releaseExactLock()
                            // ولا تُثبّت درجة عند قدرة الجهاز حين لا مسار كتابة مدى: التثبيت كان
                            // سيجعل «أداء» يجمّد التردد بدل أن يطلقه.
                            //
                            // وإن قبل الجهاز كتابة مدى فنكتب السقف **عند القدرة** بعده: على هذا
                            // الجهاز يقصّ `max_freq` نفسه (وهو ٧٥٤ في الوضع العادي)، فتحرير قنوات
                            // السلطة وحده لا يرفع سقفًا كتبته خدمة الحرارة/الألعاب على العقدة.
                            // والكتابة لا تُخترع قيمة (لا شيء فوق قدرة معلنة)، والنتيجة تُقاس بعدها:
                            // فإن قُمعت تُقال مقموعة (`gpu-ceiling-held`) ويُستعاد خط الأساس.
                            if (!live.devfreqCeilingWritable) return@let true
                        }
                        val capped = GpuHardwareBackend.snapToAvailableAtOrBelow(
                            live,
                            wantedHz,
                            respectLiveCeiling = !releaseRequired,
                        ) ?: return@let false
                        val low = live.frequencies.firstOrNull { it <= capped } ?: return@let false
                        val rangeResult = GpuHardwareBackend.applyValidated(
                            live,
                            if (live.devfreqCeilingWritable) {
                                GpuHardwareBackend.Request(
                                    minFreq = low,
                                    maxFreq = capped,
                                    releaseVendorCeiling = releaseCeiling,
                                )
                            } else {
                                // تثبيت فهرس OPP — المسار الوحيد المتاح على هذا الجهاز.
                                GpuHardwareBackend.Request(
                                    minFreq = capped,
                                    maxFreq = capped,
                                    releaseVendorCeiling = releaseCeiling,
                                )
                            },
                        )
                        if (rangeResult.verified) return@let true
                        // ── والمدى لم يحمل الطلب: هل للتثبيت مسار؟ ────────────────────────────────
                        //
                        // وهذا هو ما يفرّق «تحرير لم يُنفَّذ» من «تحرير لا يكفي وحده». على MTK
                        // تُعلن عقد `devfreq` حتى ٧٥٤ بينما جدول OPP الموقّع يحمل ١٣٠٠، فكتابة
                        // السقف تُقصّ عند ٧٥٤ مهما فُتحت قنوات السلطة — والمسار الوحيد للدرجة
                        // الأعلى هو **فهرس OPP** (`fix_target_opp_index`)، وهو نفسه ما يفعله
                        // بروفايل الأداء في الوحدة نفسها.
                        //
                        // وحدوده مقصودة: لطلب التحرير وحده (لا لطلب تبريد)، ومع وجود فهرس حقيقي
                        // للدرجة المطلوبة (بلا اختراع فهرس)، وبعد أن يُقاس فشل المدى لا أن يُفترض،
                        // والقيمة تُقاس بعدها بالتردد الجاري (`pinVerdict`) فلا يُصدَّق صدى الفهرس
                        // وحده. والفهرس السابق محفوظ في خط الأساس فيُعاد عند خروج التطبيق.
                        if (!releaseCeiling || !live.exactLockWritable || live.mtkFixedIndexPath == null) return@let false
                        if (capped !in live.mtkOppIndexByFrequency) return@let false
                        pinnedViaIndex = true
                        GpuHardwareBackend.applyValidated(
                            live,
                            GpuHardwareBackend.Request(
                                minFreq = capped,
                                maxFreq = capped,
                                releaseVendorCeiling = true,
                            ),
                        ).verified
                    } ?: false },
                read = {
                    if (ceilingShaped) {
                        GpuHardwareBackend.refresh(device.path)?.let { live ->
                            GpuHardwareBackend.ceilingReading(live).token
                        }
                    } else {
                        GpuHardwareBackend.refresh(device.path)?.let { live ->
                            GpuHardwareBackend.effectiveFrequency(live)?.toString()
                        }
                    }
                },
                // The arbiter owns the encoded live value for this request; the backend baseline
                // object is captured separately so rollback never tries to decode a scalar as a
                // five-field baseline record.
                baseline = if (ceilingShaped) {
                    GpuHardwareBackend.ceilingReading(liveAtPlan).token
                } else {
                    GpuHardwareBackend.effectiveFrequency(liveAtPlan)?.toString()
                },
                restore = {
                    val frequencyRestored = GpuHardwareBackend.restoreBaseline(baseline)
                    // وسقف المصنّع يُعاد معه: التحرير تغيير حقيقي على العتاد، وإبقاؤه بعد خروج
                    // التطبيق تسريب — وكل مقبض مملوك في هذا المشروع يُستعاد عند الخروج.
                    val ceilingRestored = ceilingCapture?.let { PlatformCeilingAuthority.restoreGpuCeiling(it) } ?: true
                    frequencyRestored && ceilingRestored
                },
                // السقف يُحكم عليه بمعناه: لا يتجاوز المطلوب = مُلبّى. والتساوي كان يقرأ
                // `expected=1300000000 live=754000000` فشلًا فيسترجع خط الأساس ويعيد الكتابة
                // كل دورة انحراف بلا نتيجة (القياس في HANDOFF.md).
                // وفي الشكل المقيس يُقاس **السقف نفسه**: سقف العقدة + سقف المنصّة المخصّص +
                // حالة تبريد GPU. فحكم «مُلبّى» لا يُطلق على طلب ما زالت المنصّة تقصّه.
                verify = if (ceilingShaped) {
                    { wanted, actual ->
                        // والحكم على **التحرير** لا على سياسة المنصّة: طلبٌ عند السقف أو فوقه يتحقّق
                        // بزوال كل قنواتنا (تبريد GPU · سقف GED · قفل OPP)، وسقفٌ أدنى يُقاس ويُعلَن
                        // برقمه (`gpu-ceiling-open-below-request:754000000`) ولا يُحكم به فشلًا —
                        // لأن الفشل هنا يُعيد خط الأساس، فيمحو التحرير ويضمن ألّا يقع تغيير أبدًا.
                        // وما بقى **مقصوصًا من عندنا** (تبريد رافع أو سقف GED أو قفل أدنى من الطلب)
                        // فشلٌ صريح كما كان.
                        GpuCeilingPolicy.releaseVerdict(
                            GpuCeilingPolicy.CeilingReading.parse(actual),
                            wanted.toLongOrNull() ?: 0L,
                        ).satisfied
                    }
                } else {
                    HardwareVerification::ceilingAtMost
                },
            )
            // نيّة المستخدم تُحفظ قبل أي تدخّل من الحارس الحراري — الحارس يعدّل «المطلوب»
            // لاحقًا، ولا سبيل لإعادة السقف إلى ما اختاره المستخدم بلا حفظه هنا.
            if (owned) hardwareUserIntent[gpuKey] = desired
            // ── سطر واحد يجيب: ماذا نُفِّذ، وعلى أي تردد يجري الجهاز فعلًا، وهل السقف مُحرَّر؟ ──
            val liveAfter = GpuHardwareBackend.refresh(device.path)
            val measuredHz = liveAfter?.let { GpuHardwareBackend.currentFrequencyHz(it) }
            val pinnedHz = if (realization == GpuCeilingPolicy.Realization.PIN || pinnedViaIndex) {
                liveAfter?.let { GpuHardwareBackend.currentExactLockFrequency(it) }
            } else null
            val pinJudgement = if (realization == GpuCeilingPolicy.Realization.PIN || pinnedViaIndex) {
                GpuCeilingPolicy.pinVerdict(pinnedHz, measuredHz)
            } else null
            val ceilingAfter = GpuHardwareBackend.ceilingReading(liveAfter ?: liveAtPlan)
            // حكم السقف يُحسب دائمًا ويُطبع دائمًا، حتى في شكل الكتابة: جواب «هل ما زالت المنصّة
            // تقصّ؟» لا يجوز أن يغيب لأن مسار التنفيذ كان مسار كتابة.
            val ceilingJudgement = GpuCeilingPolicy.ceilingReason(
                target,
                ceilingAfter,
                capabilityHz = advertisedMaxHz,
            )
            // وحكم **التحرير** — وهو نفسه الذي حكم به المُحكِّم على المعاملة، فلا رقمان لسلوك واحد:
            // الرمز نفسه يذهب إلى السجل وإلى بطاقة الحالة، ومعه الرقم المقيس حين كان الرقم هو الفرق.
            val releaseJudgement = if (ceilingShaped) {
                GpuCeilingPolicy.releaseVerdict(ceilingAfter, target)
            } else null
            val judgement = when {
                releaseJudgement != null -> releaseJudgement.reason
                pinJudgement != null -> pinJudgement.token
                else -> GpuCeilingPolicy.Realization.RANGE.token
            }
            AppMonitorLogger.i(
                "EVENT=PERAPP_GPU_REALIZED pkg=$pkgName profile=$profile realization=${realization.token}" +
                    " released=$releaseRequired pinned_by_index=$pinnedViaIndex" +
                    " requested=$requested target=$target advertised_max=${advertisedMaxHz ?: "none"}" +
                    " clock_before=${clockAtPlan ?: "unreadable"} clock_now=${measuredHz ?: "unreadable"}" +
                    " pinned=${pinnedHz ?: "none"} ceiling=$ceilingJudgement" +
                    " judgement=$judgement owned=$owned sw=$currentSwitchId"
            )
            // والفشل المَقيس يُقال في بطاقة الحالة بنفسه (لا يُطمس بسطر «applied» المجاور):
            // «أداء» لا يعني أن المنصّة لم تعد تقصّ — ولا تثبيتٌ يُصدَّق بالفهرس وحده.
            val measuredFailure = when {
                releaseJudgement != null && !releaseJudgement.satisfied -> releaseJudgement.reason
                pinJudgement == GpuCeilingPolicy.PinVerdict.CLOCK_MISMATCH -> pinJudgement.token
                else -> null
            }
            when {
                measuredFailure != null -> noteHardware(
                    "gpu_profile",
                    Outcome.NOT_VERIFIED,
                    measuredFailure,
                    desired,
                    measuredHz?.toString().orEmpty(),
                )
                !owned -> noteOwnedOutcome(
                    knob = "gpu_profile",
                    owned = false,
                    refusal = hardwareControlRegistry.refusalReasons()[gpuKey],
                    expected = desired,
                    live = liveAfter?.let(GpuHardwareBackend::effectiveFrequency)?.toString().orEmpty(),
                )
                releaseJudgement != null -> noteHardware(
                    "gpu_profile",
                    Outcome.APPLIED,
                    // ولماذا قد يقول الرمز «تحرير کامل» أو «تحرير وسقفُ المنصّة أدنى»: الأول يعني
                    // بلوغ الطلب، والثاني يعني أن كل ما نملكه مفتوح وما تحتفظ به المنصّة مُقَاس
                    // ومكتوب برقمه — بدل أن يُكتم أو يُدَّعى أنه فشل.
                    releaseJudgement.reason,
                    desired,
                    measuredHz?.toString().orEmpty(),
                )
                else -> noteOwnedOutcome(
                    knob = "gpu_profile",
                    owned = true,
                    refusal = hardwareControlRegistry.refusalReasons()[gpuKey],
                    expected = desired,
                    live = liveAfter?.let(GpuHardwareBackend::effectiveFrequency)?.toString().orEmpty(),
                )
            }
        }.onFailure { AppMonitorLogger.e("ownership: GPU frequency registration failed for '$pkgName' sw=$currentSwitchId", it) }

        // Governors are now applied by the ownership registry below. This keeps the
        // initial apply path and the drift-repair path on exactly the same verified state.

        runCatching {
            val boostNode = CpuHardwareBackend.boostNode()
            val requestedBoost = readAppConfigField(pkgName, "cpu_boost") == "true"
            if (boostNode != null && readAppConfigField(pkgName, "cpu_boost").isNotBlank()) {
                val baseline = RootFileAccess.read(boostNode)?.trim()
                hardwareControlRegistry.ownValue(
                    key = HardwareControlKey.CPU_BOOST,
                    desired = if (requestedBoost) "1" else "0",
                    apply = { value -> CpuHardwareBackend.setBoost(value == "1").successful },
                    read = { RootFileAccess.read(boostNode)?.trim() },
                    baseline = baseline,
                    restore = { value -> CpuHardwareBackend.setBoost(value == "1").successful },
                )
            }
            stopCpuBoostAndRestore()
            if (requestedBoost) {
                cpuBoostOriginalMins.clear()
                val snapshot = readCpuMinFrequencies()
                cpuBoostOriginalMins.putAll(snapshot)
                cpuBoostGeneration++
                val generation = cpuBoostGeneration
                cpuBoostThread = Thread {
                    try {
                        snapshot.keys.forEach { path ->
                            val max = shellRead("cat '$path/cpuinfo_max_freq' 2>/dev/null")
                            if (max.isNotBlank()) sysfsWrite("$path/scaling_min_freq", max)
                        }
                        Thread.sleep(3000L)
                        if (!Thread.currentThread().isInterrupted && generation == cpuBoostGeneration) {
                            snapshot.forEach { (path, min) -> sysfsWrite("$path/scaling_min_freq", min) }
                        }
                    } catch (_: InterruptedException) {
                        // Revert path restores the saved values synchronously.
                    }
                }.apply { isDaemon = true; start() }
            }
        }.onFailure { AppMonitorLogger.e("cpu_boost knob failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val dnd = readAppConfigField(pkgName, "dnd_on_gaming")
            if (dnd == "true") {
                wasZenSet = true
                val changed = runCatching {
                    systemContext?.contentResolver?.let { Settings.Global.putInt(it, "zen_mode", 1) } == true
                }.getOrDefault(false)
                if (!changed) shellExec("settings put global zen_mode 1")
            }
        }.onFailure { AppMonitorLogger.e("dnd_on_gaming knob failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val requestedRefresh = readAppConfigField(pkgName, "refresh_rate").toIntOrNull()
            val context = systemContext
            if (requestedRefresh != null && context != null) {
                val normalized = PerAppRefreshRateController.normalizeRequestedRate(context, requestedRefresh)
                val baseline = PerAppRefreshRateController.currentEnforcedRate(context)?.toString()
                if (normalized != null) {
                    val owned = hardwareControlRegistry.ownValue(
                        key = "refresh_rate",
                        desired = normalized.toString(),
                        apply = { value -> value.toIntOrNull()?.let { PerAppRefreshRateController.apply(context, it) } ?: false },
                        read = { PerAppRefreshRateController.currentEnforcedRate(context)?.toString() },
                        baseline = baseline,
                        restore = { value ->
                            val hz = value.toIntOrNull()
                            hz != null && PerAppRefreshRateController.apply(context, hz)
                        },
                    )
                    noteOwnedOutcome(
                        knob = "refresh_rate",
                        owned = owned,
                        refusal = hardwareControlRegistry.refusalReasons()["refresh_rate"],
                        expected = normalized.toString(),
                        live = PerAppRefreshRateController.currentEnforcedRate(context)?.toString().orEmpty(),
                    )
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: refresh registration failed for '$pkgName' sw=$currentSwitchId", it) }

        // refresh_rate is applied through the ownership registry together with its baseline.

        // One verified commit point for all generic owned controls registered above.
        // This avoids separate writers racing each other and makes the initial apply path
        // identical to the periodic drift-repair path. Vendor-specific OPP/thermal controls
        // remain outside this generic registry because their adapters own their own protocol.
        runCatching {
            val commitResults = hardwareControlRegistry.verifyAndRepair()
            commitResults.forEach { result ->
                AppMonitorLogger.i(
                    "EVENT=PERAPP_COMMIT pkg=$pkgName knob=${result.key} requested=${result.requested} " +
                        "applied=${result.applied} verified=${result.verified} attempts=${result.attempts} " +
                        "live=${result.actual ?: "none"} error=${result.error ?: "none"} sw=$currentSwitchId"
                )
            }
            // كتلة الجلسة هنا لا في `beginApp`: هنا تُعرف النوايا المُنشورة فعلًا (وما رُفض قبلها
            // لا يصير نيّة)، فتسجّل الكتلة ما طُلب لا ما كان مرغوبًا.
            writeLogSessionHeader(pkgName)
            if (activePerAppCpuPackage == pkgName) {
                val failure = commitResults.firstOrNull { HardwareControlKey.isCpuLimits(it.key) && !it.successful }
                // A knob refused at the gate never becomes an owned entry, so it
                // is absent from commitResults. Reporting that list alone would
                // turn "your rule was refused" into silence, and silence reads
                // as success.
                val refusal = hardwareControlRegistry.refusalReasons()
                    .entries.firstOrNull { HardwareControlKey.isCpuLimits(it.key) }
                when {
                    failure != null -> {
                        val policyName = HardwareControlKey.cpuLimitsPolicy(failure.key) ?: failure.key
                        writePerAppCpuStatus(pkgName, "failed", "$policyName was not verified")
                        AppMonitorLogger.w("EVENT=PERAPP_CPU_FAILED pkg=$pkgName reason=${failure.error ?: "live-value-mismatch"} sw=$currentSwitchId")
                    }
                    refusal != null -> {
                        val policyName = HardwareControlKey.cpuLimitsPolicy(refusal.key) ?: refusal.key
                        writePerAppCpuStatus(pkgName, "failed", "$policyName refused by the ownership gate (${refusal.value})")
                        AppMonitorLogger.w("EVENT=PERAPP_CPU_BLOCKED pkg=$pkgName knob=${refusal.key} reason=${refusal.value} sw=$currentSwitchId")
                    }
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: verified commit failed for '$pkgName' sw=$currentSwitchId", it) }

        // touch_boost: per-app override. `default` deliberately follows the
        // ROM's global touch-boost setting; explicit true/false wins only for
        // this foreground app. This prevents the per-app screen from silently
        // re-enabling a global feature the user intentionally turned off.
        runCatching {
            val touchBoost = readAppConfigField(pkgName, "touch_boost")
            touchBoostOverride = if (touchBoost in setOf("true", "false")) touchBoost else "default"
        }.onFailure { AppMonitorLogger.e("touch_boost knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // force_hw_ui: mirrors the "Force GPU rendering" developer option
        // (persist.sys.ui.hw + debug.viewroot.disableHW). Like the renderer
        // prop archdaemon manages, HWUI reads this at window/process init, so
        // an already-running process may not visibly change until its next
        // cold start — this sets the prop so it takes effect from then on,
        // without force-restarting the app the user just switched into.
        runCatching {
            when (readAppConfigField(pkgName, "force_hw_ui")) {
                "true", "false" -> {
                    if (!forcedHwUi) {
                        savedHwUiProp = shellRead("getprop persist.sys.ui.hw").takeIf { it.isNotBlank() }
                        savedDisableHwProp = shellRead("getprop debug.viewroot.disableHW").takeIf { it.isNotBlank() }
                    }
                    if (readAppConfigField(pkgName, "force_hw_ui") == "true") {
                        shellExec("setprop persist.sys.ui.hw true; setprop debug.viewroot.disableHW false")
                    } else {
                        shellExec("setprop persist.sys.ui.hw false; setprop debug.viewroot.disableHW true")
                    }
                    forcedHwUi = true
                }
            }
        }.onFailure { AppMonitorLogger.e("force_hw_ui knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // haptic_feedback: "true" in the per-app config means "Reduce Haptic
        // Feedback" is ON, i.e. vibration gets disabled. Save the user's real
        // setting first so revert restores their actual preference.
        runCatching {
            if (readAppConfigField(pkgName, "haptic_feedback") == "true") {
                val context = systemContext
                if (context != null) {
                    savedHapticFeedbackEnabled = context.contentResolver?.let {
                        Settings.System.getString(it, "haptic_feedback_enabled")
                    }
                    val changed = runCatching {
                        context.contentResolver?.let { Settings.System.putInt(it, "haptic_feedback_enabled", 0) } == true
                    }.getOrDefault(false)
                    if (!changed) shellExec("settings put system haptic_feedback_enabled 0")
                } else {
                    shellExec("settings put system haptic_feedback_enabled 0")
                }
            }
        }.onFailure { AppMonitorLogger.e("haptic_feedback knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // disable_notifs: mutes the notification audio stream (sounds) while
        // this app is focused. This is deliberately independent from
        // dnd_on_gaming above (which flips zen_mode globally) — it only
        // silences this one stream, so it can be layered on top of DND or
        // used alone. Notification badges/heads-up visuals aren't touched
        // here, only sound.
        runCatching {
            if (readAppConfigField(pkgName, "disable_notifs") == "true") {
                val am = systemContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null && !am.isStreamMute(AudioManager.STREAM_NOTIFICATION)) {
                    am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    wasNotifStreamMuted = true
                }
            }
        }.onFailure { AppMonitorLogger.e("disable_notifs knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // wifi_no_sleep: standard high-perf WifiLock, held only while this
        // app stays focused and released the moment focus moves away
        // (revertPerAppConfig / next applyPerAppConfig call).
        runCatching {
            if (readAppConfigField(pkgName, "wifi_no_sleep") == "true") {
                val wm = systemContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiNoSleepLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MaxManager:wifiNoSleep")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
        }.onFailure { AppMonitorLogger.e("wifi_no_sleep knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // الحارس التفاعليّ يُنادى فورًا بعد التطبيق لا في دورة الانحراف فقط: الطلب الجديد لحظته هي
        // لحظة فتح التطبيق، ومن يفتح تطبيقًا ويتوقّع سقفًا لا ينتظر عشر ثوانٍ لرؤيته.
        runCatching { serviceThermalGuard() }
            .onFailure { AppMonitorLogger.e("EVENT=THERMAL_GUARD_FAILED pkg=$pkgName sw=$currentSwitchId", it) }

        // تعديلات هذا التطبيق حيّة على العتاد الآن: يُعلن في app_status
        // (perapp_active 1) فيدخل محرك MAX AI وضع المراقبة.
        perAppOverridesActive = true
        // كتابة واحدة في النهاية: الحالة تُقرأ كاملة أو لا تُقرأ، ولا تُعبّئ الواجهة بنصفِ
        // سجل يبدو سليمًا أثناء تطبيق جارٍ.
        PerAppHardwareStatus.flush()
    }

    private fun readCpuMinFrequencies(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        shellRead("for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -f \"\$p/scaling_min_freq\" ] && echo \"\$p=\$(cat \"\$p/scaling_min_freq\")\"; done")
            .split("\n").forEach { line ->
                val eq = line.indexOf('=')
                if (eq > 0) result[line.substring(0, eq)] = line.substring(eq + 1).trim()
            }
        return result
    }

    private fun stopCpuBoostAndRestore() {
        cpuBoostGeneration++
        cpuBoostThread?.interrupt()
        cpuBoostThread = null
        cpuBoostOriginalMins.forEach { (path, min) ->
            sysfsWrite("$path/scaling_min_freq", min)
        }
        cpuBoostOriginalMins.clear()
    }

    private fun restoreProp(name: String, value: String?) {
        if (value.isNullOrBlank()) shellExec("resetprop -n '$name'")
        else shellExec("setprop '$name' '${value.replace("'", "")}'")
    }

    /**
     * استرجاع مفتاح Settings.System مع بديل shell عند رفض المزوّد.
     * القيمة الفارغة تعني حذف المفتاح (نفس دلالة putString(key, null)).
     */
    private fun restoreSystemSettingWithShellFallback(key: String, value: String) {
        val apiOk = runCatching {
            systemContext?.contentResolver?.let {
                Settings.System.putString(it, key, value.ifEmpty { null })
            } == true
        }.getOrDefault(false)
        if (apiOk) return
        if (value.isEmpty()) shellExec("settings delete system '$key'")
        else shellExec("settings put system '$key' '${value.replace("'", "")}'")
    }

    private fun writeAppGameInfo(pkg: String, pid: String, uid: String) {
        runCatching {
            val file = File("/data/data/nd.max/API/gameinfo")
            file.parentFile?.mkdirs()
            if (pkg.isBlank() || pkg == "unknown" || pkg == "none") {
                file.writeText("NULL 0 0\nTime: --:--:--\n")
                return
            }
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            file.writeText("$pkg ${pid.toIntOrNull() ?: 0} ${uid.toIntOrNull() ?: 0}\nTime: $time\n")
        }.onFailure { AppMonitorLogger.e("EVENT=GAME_INFO_WRITE_FAILED pkg=$pkg sw=$currentSwitchId", it) }
    }

    private fun updateActiveAppNotification(pkg: String, focusedApp: String) {
        runCatching {
            val context = systemContext ?: return@runCatching
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? AndroidNotificationManager ?: return@runCatching
            val channelId = "maxmanager_per_app"
            nm.createNotificationChannel(NotificationChannel(channelId, "Per-App Manager", AndroidNotificationManager.IMPORTANCE_LOW))
            val pid = focusedApp.substringAfter(" ", "0").substringBefore(" ")
            val profile = readAppConfigField(pkg, "gpu_profile").ifBlank { "default" }
            val thermal = readAppConfigField(pkg, "thermal_profile").ifBlank { "default" }
            val cpuGov = readAppConfigField(pkg, "cpu_governor").ifBlank { "default" }
            val gpuGov = readAppConfigField(pkg, "gpu_governor").ifBlank { "default" }
            val gpuFreq = readAppConfigField(pkg, "gpu_max_freq").ifBlank { "default" }
            val gpuProfile = if (profile == "default") thermal else profile
            val appLabel = getAppName(pkg).ifBlank { pkg }
            val liveCpuGov = shellRead("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null").ifBlank { "N/A" }
            val liveGpuGov = savedGpuNode.takeIf { it.isNotBlank() }
                ?.let(GpuHardwareBackend::refresh)?.governor.orEmpty().ifBlank { "N/A" }
            val body = "PID: $pid\nThermal/GPU: $gpuProfile\nCPU Governor: $cpuGov (live: $liveCpuGov)\nGPU Governor: $gpuGov (live: $liveGpuGov)\nGPU Frequency: $gpuFreq"
            val intent = Intent().setClassName("nd.max", "nd.max.MainActivity")
            val pi = PendingIntent.getActivity(context, 2409, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("MaxManager • $appLabel")
                .setContentText("Per-App active • PID $pid • GPU $gpuProfile")
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
            nm.notify(2409, builder.build())
        }.onFailure { AppMonitorLogger.e("EVENT=PERAPP_NOTIFICATION_FAILED pkg=$pkg sw=$currentSwitchId", it) }
    }

    private fun clearActiveAppNotification() {
        runCatching {
            val context = systemContext ?: return@runCatching
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? AndroidNotificationManager)?.cancel(2409)
        }
    }

    private fun writePerAppCpuStatus(pkgName: String, state: String, message: String) {
        val safeMessage = message.replace('\n', ' ').replace('\r', ' ').take(140)
        RootFileAccess.atomicWriteText(
            MaxManagerPaths.PER_APP_CPU_STATUS,
            "package=$pkgName\nstate=$state\nmessage=$safeMessage\n"
        )
    }

    /**
     * يسجّل نتيجة مقبض واحد في **مكانين**: سجل الحالة الذي تقرؤه الواجهة، والسجل الموحّد.
     *
     * ولماذا الاثنان معًا: الواجهة تحتاج الحالة الآن (بلا `tail`)، ومن يُصلح عطلًا بعد أسبوع
     * يحتاج سطرًا مؤرَّخًا في `MaxManager.log` يحمل نفس رمز السبب. وسطرٌ في مكان دون آخر هو
     * بالضبط ما جعل سبب فشل GPU غير معروف في السابق: لا حالة في الواجهة ولا سطر في السجل.
     */
    private fun noteHardware(
        knob: String,
        outcome: PerAppHardwareStatus.Outcome,
        reason: String,
        expected: String = "",
        live: String = "",
    ) {
        PerAppHardwareStatus.note(knob, outcome, reason, expected, live)
        val line = "EVENT=PERAPP_KNOB knob=$knob outcome=${outcome.token} reason=$reason" +
            " expected=${expected.ifBlank { "none" }} live=${live.ifBlank { "none" }}" +
            " pkg=$lastAppliedPkg sw=$currentSwitchId"
        // المستوى من النتيجة لا من العادة: فشلٌ يُكتب I(معلوماتي) يختفي من مُرشِّح «المشاكل» في
        // شاشة السجل، وهو المكان الوحيد الذي يبحث فيه مَن يُصلح عطلًا. والسطر نفسه في الحالتين،
        // فالمستوى إضافة لا تغيير صيغة.
        if (outcome == Outcome.APPLIED || outcome == Outcome.SKIPPED) AppMonitorLogger.i(line)
        else AppMonitorLogger.w(line)
    }

    /**
     * ترجمة نتيجة التسجيل المُلكيّ إلى نتيجة **مُعلَنة**: النجاح كما هو، والرفض برمز سببه
     * الحقيقي (`manual-lock` / `preempted-by-*`) لا بـ«فشل» عامّ. والفرق ليس تجميليًّا: قفل
     * المستخدم يحتاج أن يقرأ «أنت قفلت هذا المقبض» لا «العتاد فشل»، وهما إجراءان مختلفان تمامًا.
     */
    private fun noteOwnedOutcome(
        knob: String,
        owned: Boolean,
        refusal: String?,
        expected: String,
        live: String,
    ) {
        when {
            owned -> noteHardware(knob, PerAppHardwareStatus.Outcome.APPLIED, "verified", expected, live)
            refusal == null -> noteHardware(knob, PerAppHardwareStatus.Outcome.NOT_VERIFIED, "not-verified", expected, live)
            refusal.startsWith("preempted") || refusal == "manual-lock" ||
                refusal == "handoff-awaiting-owner-process" ->
                noteHardware(knob, PerAppHardwareStatus.Outcome.BLOCKED, refusal, expected, live)
            else -> noteHardware(knob, PerAppHardwareStatus.Outcome.NOT_VERIFIED, refusal, expected, live)
        }
        PerAppHardwareStatus.flush()
    }

    private fun restoreGlobalMaxManagerProfile(): Boolean {
        val profile = shellRead("cat /data/adb/.config/MaxManager/API/current_profile 2>/dev/null")
        if (profile !in setOf("1", "2", "3")) return false
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service --profile '$profile' >/dev/null 2>&1"))
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    @Synchronized
    private fun revertPerAppConfig() {
        // Always release any MediaTek gpufreqv2/legacy OPP-index lock FIRST, unconditionally.
        // This runs before releaseAll() so the registry's exact baseline restore
        // (which may re-establish the user's own pre-app GPU lock) has the final
        // word. Releasing after it would silently wipe that restored lock.
        // restoreGlobalMaxManagerProfile() below only reapplies the Global Tweaks
        // page state (devfreq nodes and props) - it doesn't know about the MTK proc
        // interface, so skipping this would leave the GPU pinned at the app's fixed
        // frequency indefinitely after the app closes.
        runCatching { PerAppKernelUtil.releaseGpuFixedFrequency() }
            .onFailure { AppMonitorLogger.e("revert: releaseGpuFixedFrequency() failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }
        hardwareControlRegistry.releaseAll()
        // نيّة المستخدم تخصّ هذه الجلسة وحدها: إبقاؤها بعد التراجع يجعل الحارس الحراري
        // يحرس تطبيقًا لم يبق له مقبض مملوك، ويسجّل نتائج على تطبيق آخر.
        hardwareUserIntent.clear()
        PerAppHardwareStatus.flush()
        stopCpuBoostAndRestore()
        // Reapply the current Global MaxManager profile first when possible.
        // This restores the state defined in the main Tweaks page instead of
        // blindly resetting nodes to hard-coded defaults.
        // A verified GPU Studio snapshot is the canonical GPU override. Apply it
        // after legacy profile replay so the older service cannot overwrite it.
        var globalRestored = false
        runCatching {
            val profileRestored = restoreGlobalMaxManagerProfile()
            val studioResult = GpuTweakPersistence.applySaved()
            globalRestored = when {
                studioResult != null -> studioResult.verified
                else -> profileRestored
            }
        }
            .onFailure { AppMonitorLogger.e("revert: restoreGlobalMaxManagerProfile() failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        // If the global profile cannot be re-applied, restore the exact live state snapshot.
        if (!globalRestored) {
            runCatching {
                savedGpuBaseline?.let { baseline ->
                    GpuHardwareBackend.restoreBaseline(baseline)
                } ?: GpuHardwareBackend.refresh(savedGpuNode)?.let { live ->
                    GpuHardwareBackend.restoreBaseline(
                        GpuHardwareBackend.Baseline(
                            devicePath = live.path,
                            minFreq = savedGpuMinFreq.toLongOrNull(),
                            maxFreq = savedGpuMaxFreq.toLongOrNull(),
                            governor = savedGpuGovernor.takeIf(String::isNotBlank),
                        )
                    )
                }
            }.onFailure { AppMonitorLogger.e("revert: GPU node snapshot restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }
        }

        // When the daemon successfully reapplies the user's current Global MaxManager
        // profile, that profile is the authoritative owner of CPU/GPU governors. Do not
        // write the old pre-app baseline over it: doing so made the Global profile look
        // broken immediately after leaving a per-app override. The baseline is only a
        // fallback for the case where the daemon profile could not be restored.
        if (!globalRestored) {
            runCatching {
                if (baselineGpuGovernor.isNotBlank() && savedGpuNode.isNotBlank()) {
                    GpuHardwareBackend.refresh(savedGpuNode)?.let { device ->
                        GpuHardwareBackend.setGovernor(device, baselineGpuGovernor)
                    }
                }
                baselineCpuGovernors.forEach { (path, gov) ->
                    sysfsWrite("$path/scaling_governor", gov)
                }
                savedCpuMaxFreqs.forEach { (path, max) ->
                    sysfsWrite("$path/scaling_max_freq", max)
                }
                savedCpuMinFreqs.forEach { (path, min) ->
                    sysfsWrite("$path/scaling_min_freq", min)
                }
            }.onFailure { AppMonitorLogger.e("revert: baseline governor restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }
        }
        if (activePerAppCpuPackage.isNotBlank()) {
            writePerAppCpuStatus(activePerAppCpuPackage, "restored", "CPU controls released")
            activePerAppCpuPackage = ""
        }

        runCatching {
            if (savedThermalProfile.isNotEmpty()) shellExec("setprop sys.thermal.profile '$savedThermalProfile'")
        }.onFailure { AppMonitorLogger.e("revert: thermal_profile restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            savedZenMode?.let { value ->
                val changed = runCatching {
                    systemContext?.contentResolver?.let { Settings.Global.putInt(it, "zen_mode", value) } == true
                }.getOrDefault(false)
                if (!changed) shellExec("settings put global zen_mode $value")
            }
            savedZenMode = null
            wasZenSet = false
        }.onFailure { AppMonitorLogger.e("revert: zen_mode restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            // Restore the vendor-specific Xiaomi path and the generic nodes.
            // The old generic nodes are intentionally retained as fallbacks.
            val context = systemContext
            val snapshot = savedVendorRefreshSnapshot
            if (context != null && snapshot != null) {
                PerAppRefreshRateController.restore(context, snapshot)
            } else if (context != null) {
                // نفس عقد zen_mode/haptic أعلاه: محاولة API أولًا ثم بديل shell.
                // الاسترجاع يحدث لحظة مغادرة التطبيق للمقدمة، وهناك يرفض مزوّد
                // الإعدادات (settings provider) المتصلات من الخلفية بـ
                // SecurityException "Unable to find app for caller" — بينما
                // مسار shell ينجح دائمًا. بدون هذا البديل كان الفشل يتكرر
                // عند كل تبديل تطبيق (سجل الجهاز: 30+ تكرارًا في دقيقة واحدة).
                restoreSystemSettingWithShellFallback("peak_refresh_rate", savedPeakRefreshRate)
                restoreSystemSettingWithShellFallback("min_refresh_rate", savedMinRefreshRate)
            }
            savedVendorRefreshSnapshot = null
            savedPeakRefreshRate = ""
            savedMinRefreshRate = ""
        }.onFailure { AppMonitorLogger.e("revert: refresh rate restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            // touch_boost: back to auto-detect for whatever app comes next
            touchBoostOverride = "default"
        }.onFailure { AppMonitorLogger.e("revert: touch_boost reset failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            if (forcedHwUi) {
                restoreProp("persist.sys.ui.hw", savedHwUiProp)
                restoreProp("debug.viewroot.disableHW", savedDisableHwProp)
                savedHwUiProp = null
                savedDisableHwProp = null
                forcedHwUi = false
            }
        }.onFailure { AppMonitorLogger.e("revert: force_hw_ui restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            savedHapticFeedbackEnabled?.let { saved ->
                val ok = runCatching {
                    systemContext?.contentResolver?.let { Settings.System.putString(it, "haptic_feedback_enabled", saved) } == true
                }.getOrDefault(false)
                if (!ok) shellExec("settings put system haptic_feedback_enabled '${saved.replace("'", "")}'")
            }
            savedHapticFeedbackEnabled = null
        }.onFailure { AppMonitorLogger.e("revert: haptic_feedback restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            if (wasNotifStreamMuted) {
                val am = systemContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                wasNotifStreamMuted = false
            }
        }.onFailure { AppMonitorLogger.e("revert: disable_notifs restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            wifiNoSleepLock?.let { if (it.isHeld) it.release() }
            wifiNoSleepLock = null
        }.onFailure { AppMonitorLogger.e("revert: wifi_no_sleep lock release failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        savedGpuGovernor = ""
        savedGpuNode = ""
        savedGpuMinFreq = ""
        savedGpuMaxFreq = ""
        savedGpuBaseline = null
        savedThermalProfile = ""
        savedCpuGovernors.clear()
        savedCpuMinFreqs.clear()
        savedCpuMaxFreqs.clear()
        baselineGpuGovernor = ""
        baselineCpuGovernors.clear()
        baselineCaptured = false
        lastDriftCheckAt = 0L
        // ملكية per-app انتهت: يُعاد الإعلان (perapp_active 0) في أول
        // writeStatus تالية، فيستأنف محرك MAX AI إدارته.
        perAppOverridesActive = false
        writeAppGameInfo("", "0", "0")
        PerAppRecoveryStore.clear()
        clearActiveAppNotification()
    }



    
    private fun getZenMode(): Int {
        return try {
            getZenModeMethod?.invoke(notificationManager) as? Int ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun getFocusedAppInfo(): String {
        return try {
            val result = invokeForegroundMethod() ?: return UNKNOWN_APP
            if (result is List<*>) {
                getFocusedAppFromList(result)
            } else {
                resolveAppInfoFromObject(result)
            }
        } catch (e: Exception) {
            AppMonitorLogger.e("getFocusedAppInfo() failed", e)
            UNKNOWN_APP
        }
    }

    private fun getFocusedAppFromList(list: List<*>): String {
        if (list.isEmpty()) return NONE_APP
        list.forEach { element ->
            extractComponentName(element)?.let { return buildAppInfo(it.packageName) }
        }
        return resolveAppInfoFromObject(list[0]!!)
    }

    private fun resolveAppInfoFromObject(obj: Any): String {
        extractComponentName(obj)?.let { return buildAppInfo(it.packageName) }
        return findPackageLikeString(obj)?.let { buildAppInfo(it) } ?: UNKNOWN_APP
    }

    private fun invokeForegroundMethod(): Any? {
        val method = foregroundMethod ?: return null
        return tryInvokeForegroundMethod(method) ?: bruteForceForegroundMethod()
    }

    private fun tryInvokeForegroundMethod(method: Method): Any? {
        val name = method.name
        return try {
            when {
                name == "getTasks" || name == "getRunningTasks" -> {
                    tryInvokeWithArgs(
                        method,
                        activityTaskManager!!,
                        arrayOf(1),
                        arrayOf(1, 0),
                        arrayOf(1, false, false)
                    )
                }

                method.parameterTypes.isEmpty() -> method.invoke(activityTaskManager)
                else -> tryInvokeWithArgs(method, activityTaskManager!!, arrayOf(0))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryInvokeWithArgs(method: Method, target: Any, vararg argSets: Array<Any>): Any? {
        for (args in argSets) {
            try {
                return method.invoke(target, *args)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun bruteForceForegroundMethod(): Any? {
        return try {
            val candidates =
                bruteForceCandidates ?: getDeclaredMethods(activityTaskManager!!.javaClass)
                    .filter {
                        val name = it.name.lowercase()
                        name.contains("focus") || name.contains("top") || name.contains("task")
                    }
                    .onEach { it.isAccessible = true }
                    .also { bruteForceCandidates = it }

            candidates.firstNotNullOfOrNull { method ->
                when {
                    method.parameterTypes.isEmpty() ->
                        tryInvokeQuietly { method.invoke(activityTaskManager) }

                    method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java ->
                        tryInvokeQuietly { method.invoke(activityTaskManager, 1) }

                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private inline fun tryInvokeQuietly(block: () -> Any?): Any? {
        return try {
            block()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractComponentName(obj: Any?): ComponentName? {
        if (obj == null) return null
        if (obj is ComponentName) return obj

        COMPONENT_NAME_FIELDS.forEach { fieldName ->
            getComponentNameFromField(obj, obj.javaClass, fieldName)?.let { return it }
        }

        return scanHierarchyForComponentName(obj)
    }

    private fun getComponentNameFromField(
        obj: Any,
        cls: Class<*>,
        fieldName: String
    ): ComponentName? {
        return try {
            val field = cls.getDeclaredField(fieldName).apply { isAccessible = true }
            field.get(obj) as? ComponentName
        } catch (_: Exception) {
            null
        }
    }

    private fun scanHierarchyForComponentName(obj: Any): ComponentName? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            getInstanceFields(cls).forEach { field ->
                try {
                    field.isAccessible = true
                    val value = field.get(obj)
                    if (value is ComponentName) return value
                } catch (_: Exception) {
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun findPackageLikeString(obj: Any?): String? {
        if (obj == null) return null
        extractPackageName(obj.toString())?.let { return it }

        getInstanceFields(obj.javaClass).forEach { field ->
            if (field.type == String::class.java) {
                try {
                    field.isAccessible = true
                    (field.get(obj) as? String)?.let { str ->
                        extractPackageName(str)?.let { return it }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun extractPackageName(input: String?): String? {
        if (input == null || input.indexOf('.') <= 0) return null
        val normalized = input.lowercase().replace(Regex("[^a-z0-9._-]"), " ")
        return normalized.split(Regex("\\s+")).find {
            it.contains(".") && it.matches(Regex("[a-z0-9]+(\\.[a-z0-9]+)+"))
        }
    }

    private fun buildAppInfo(pkg: String): String {
        val pidUid = getPidUid(pkg)
        return "$pkg $pidUid"
    }

    private fun getPidUid(pkg: String): String {
        return try {
            activityManager?.runningAppProcesses
                ?.find { it.processName == pkg || it.pkgList?.contains(pkg) == true }
                ?.let { "${it.pid} ${it.uid}" }
                ?: run {
                    "0 0"
                }
        } catch (e: Exception) {
            "0 0"
        }
    }

    private fun setupSystemContext() {
        try {
            val looperClass = Class.forName("android.os.Looper")
            if (looperClass.getMethod("getMainLooper").invoke(null) == null) {
                looperClass.getMethod("prepareMainLooper").invoke(null)
            }
    
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            var thread: Any? = null
    
            try {
                thread = activityThreadClass.getMethod("systemMain").invoke(null)
            } catch (t: Throwable) {
                val cause = generateSequence(t) { it.cause }.lastOrNull()
                AppMonitorLogger.w("systemMain() failed (${t.javaClass.simpleName}): ${cause?.message}")
            }
    
            if (thread == null) {
                try {
                    thread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                } catch (t: Throwable) {
                    AppMonitorLogger.w("currentActivityThread() also failed: ${t.message}")
                }
            }

            thread ?: error("Both systemMain() and currentActivityThread() returned null")
    
            systemContext = activityThreadClass.getMethod("getSystemContext").invoke(thread) as? Context
                ?: error("getSystemContext() returned null")
    
        } catch (e: Exception) {
            AppMonitorLogger.e("setupSystemContext() failed", e)
        }
    }

    private fun bypassHiddenApiRestrictions() {
        HiddenApiBypass.addHiddenApiExemptions("")
    }

    private fun initializeServices(): Boolean {
        return try {
            val ctx = systemContext ?: return false
            powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            initActivityTaskManager()
            initNotificationManager()
            true
        } catch (e: Exception) {
            AppMonitorLogger.e("initializeServices() failed", e)
            false
        }
    }

    private fun initActivityTaskManager() {
        val binder = getSystemService(resolveAtmServiceName())
            ?: error("ServiceManager returned null binder for '${resolveAtmServiceName()}'")
        val atm = bindInterface("${resolveAtmInterfaceName()}\$Stub", binder)
        activityTaskManager = atm
        foregroundMethod = findForegroundMethod(atm)
    }

    private fun findForegroundMethod(atm: Any): Method? {
        val methods = getDeclaredMethods(atm.javaClass).associateBy { it.name }

        return FOREGROUND_METHOD_CANDIDATES
            .mapNotNull { candidate -> methods[candidate] }
            .find { method ->
                method.parameterTypes.isEmpty() ||
                        (method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java) ||
                        method.name == "getTasks" || method.name == "getRunningTasks"
            }
            ?.apply { isAccessible = true }
    }

    private fun initNotificationManager() {
        val binder = getSystemService(Context.NOTIFICATION_SERVICE)
            ?: error("ServiceManager returned null binder for notification service")
        notificationManager = bindInterface("android.app.INotificationManager\$Stub", binder)
        notificationManager?.let { manager ->
            getDeclaredMethods(manager.javaClass).forEach { member ->
                if (member.name == "getZenMode" && member.parameterTypes.isEmpty()) {
                    getZenModeMethod = member
                }
            }
        }
    }

    private fun resolveAtmServiceName() =
        if (Build.VERSION.SDK_INT >= 29) "activity_task" else Context.ACTIVITY_SERVICE

    private fun resolveAtmInterfaceName() =
        if (Build.VERSION.SDK_INT >= 29) "android.app.IActivityTaskManager" else "android.app.IActivityManager"

    private fun getSystemService(name: String): IBinder? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        return serviceManager.getMethod("getService", String::class.java)
            .invoke(null, name) as? IBinder
    }

    private fun bindInterface(stubClassName: String, binder: IBinder): Any {
        return Class.forName(stubClassName)
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: error("asInterface returned null for $stubClassName")
    }

    private fun getDeclaredMethods(cls: Class<*>): List<Method> {
        return HiddenApiBypass.getDeclaredMethods(cls).filterIsInstance<Method>()
    }

    private fun getInstanceFields(cls: Class<*>): List<Field> {
        return HiddenApiBypass.getInstanceFields(cls).filterIsInstance<Field>()
    }
    
    // Cached raw text of MaxManager's own curated game list, reused here so
    // XiaomiVendorFeatures doesn't need a second copy of this list. The
    // file is small and rarely changes, so a plain substring check
    // (matching the same package-key style used by the daemon) is
    // enough — no need to pull in a JSON parsing dependency for this.
    private var cachedGameListText: String? = null
    private var cachedGameListPath: String? = null

    private fun isKnownGameApp(pkgName: String): Boolean {
        if (pkgName.isBlank() || pkgName == "unknown" || pkgName == "none") return false
        val path = MaxManagerPaths.APPLIST_JSON
        if (cachedGameListText == null || cachedGameListPath != path) {
            cachedGameListText = runCatching { File(path).readText() }.getOrNull()
            cachedGameListPath = path
        }
        return cachedGameListText?.contains("\"$pkgName\":") == true
    }

    /** Mirrors AmbientDisplayConfiguration.alwaysOnEnabled() used by ColorService.kt. */
    private fun isAodEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(
                systemContext?.contentResolver, "doze_always_on", 0,
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun getAppName(pkgName: String): String {
        if (pkgName == "unknown" || pkgName == "none" || pkgName.isBlank()) {
            return "Unknown"
        }
        return try {
            val pm = systemContext?.packageManager ?: return "Unknown"
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {


            pkgName 
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getChargingStatus(): Int {
        return try {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0
        } catch (e: Exception) {
            0
        }
    }
    
    private fun getCurrentRefreshRate(): Int {
        return try {
            val dm = systemContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            
            val refreshRate = display?.refreshRate ?: 60.0f
            Math.round(refreshRate)
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getMaxRefreshRate(): Int {
        return try {
            val dm = systemContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            
            val modes = display?.supportedModes
            if (modes != null && modes.isNotEmpty()) {
                val maxRate = modes.maxOf { it.refreshRate }
                Math.round(maxRate)
            } else {
                60
            }
        } catch (e: Exception) {
            -1
        }
    }
}
