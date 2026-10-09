/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0
 */

package nd.max.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import nd.max.core.hardware.MemoryPressureReader
import nd.max.core.hardware.RootFileAccess
import nd.max.core.maxai.MemoryStall
import nd.max.ui.util.HomeLiveReadings
import nd.max.ui.util.NO_THROTTLE_HEADROOM
import nd.max.core.jni.ProbeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.ui.util.CpuTopologyUtil
import nd.max.core.platform.FpsMonitorUtil
import nd.max.ui.util.LoadHistory
import nd.max.ui.util.LoadHistoryStore
import nd.max.ui.util.LoadSample
import nd.max.ui.util.MtkUtils
import nd.max.core.platform.ThermalUtil
import nd.max.core.platform.getChipsetNameWithPartCode
import nd.max.core.privilege.PrivilegeManager
import nd.max.ui.util.BoostOutcome
import nd.max.ui.util.MemoryBoostEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.update

/**
 * One live core as the dashboard's core matrix renders it.
 *
 * [freqMhz] is that core's own `scaling_cur_freq`, not its cluster's — on
 * big.LITTLE the per-core node is what actually differs, which is the whole
 * point of showing eight tiles instead of one number. [maxFreqMhz] comes from
 * the cluster policy's `cpuinfo_max_freq` and is the fixed ceiling used to
 * normalize the tile's fill; a core reporting 0 is offline (hotplugged out)
 * and is rendered as such rather than as "0 MHz busy".
 */
data class CpuCoreState(
    val cpu: Int,
    val freqMhz: Int = 0,
    val maxFreqMhz: Int = 0,
    /**
     * أرضية عنقود هذه النواة (`cpuinfo_min_freq`) — تُقرأ مرة واحدة مع السقف.
     *
     * و`0` تعني «النواة لا تُعلن أرضية»، فتُعرض البطاقة بلا حدّ أدنى بدل اختراع رقم:
     * أرضيةٌ مُخترعة تجعل كل قراءة تبدو قريبة من القاع أو من القمة بلا سبب حقيقي.
     */
    val minFreqMhz: Int = 0,
    val online: Boolean = true,
    val clusterTag: String = "",
    val coreName: String? = null
) {
    /** Fill fraction against this core's own ceiling; 0 when offline or unknown. */
    val loadFraction: Float
        get() = if (!online || maxFreqMhz <= 0 || freqMhz <= 0) 0f
        else (freqMhz.toFloat() / maxFreqMhz).coerceIn(0f, 1f)
}

/**
 * حالة «تعزيز الذاكرة» كما تُعرض.
 *
 * وثلاث حالات لا حالة واحدة بـ`Boolean`: «يُنفَّذ الآن» ≠ «انتهى بحصيلة» ≠ «لم يُنفَّذ لنقص
 * صلاحية» — وواحدة منها كانت ستُكتب في الواجهة كذبًا لو جُمعت في علمين متقاطعين.
 */
data class MemoryBoostState(
    val running: Boolean = false,
    val outcome: BoostOutcome? = null,
    val blocked: Boolean = false,
)

data class DashboardState(
    /**
     * طابع آخر دورة قياس **اكتملت** (٠ = لم تُقرأ بعد).
     *
     * **ولماذا وُلد (تكملة ٢٠٥):** كانت الواجهة تقرأ «قيمة غائبة» فتُسمّيها **«غير مقروء»** —
     * وهذا حكمٌ كاذب في اللحظة التي تسبق أول قراءة: المصدر لم يفشل، بل السؤال لم يُطرح بعد.
     * ومعه صار في الواجهة تمييز بين «يُقرأ الآن» و«غير مقروء» و«لم تُقرأ بعد» — وهو نصّ روح
     * ADR-07: لا يُدَّعي عطب لم يُقس كما لا يُدَّعى رقم.
     */
    val readingsAtMs: Long = 0L,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val cpuLoadPercent: Int = 0,
    val cpuFreqMhz: Int = 0,
    /**
     * أعلى تردّد **حيّ** بين الأنوية المتصلة — وهو رقم مقياس المعالج.
     *
     * ولا يُستعمل [cpuFreqMhz] لهذا: هو `cpu0` وحده، وعلى big.LITTLE يكون نواة صغيرة
     * تبقى في أدنى درجاته بينما العمل الحقيقي على العنقود الرئيسي — فيقرأ الشريط «هادئًا»
     * وجهاز يعمل بكامل قوّته. والصفر يعني «لا نواة متصلة تُقرأ»، لا «تردّد صفري».
     */
    val cpuTopCoreMhz: Int = 0,
    /**
     * سقف المعالج المعلَن: أعلى سقف مقروء من سياسات العناقيد (`cpuinfo_max_freq`).
     * والصفر يعني «النواة لا تُعلن سقفًا» — فيُعرض الرقم بلا مدرّج بدل اختراع مدى.
     */
    val cpuCeilingMhz: Int = 0,
    val chipsetName: String = "...",
    val batteryPercent: Int = 0,
    val batteryVoltageV: Float = 0f,
    val batteryTempC: Float = 0f,
    val isCharging: Boolean = false,
    /** Battery current in mA; positive means charging and null means unavailable. */
    val batteryCurrentMa: Int? = null,
    val batteryStatus: String = "",
    val cpuTempC: Int = 0,
    val gpuTempC: Int = 0,
    val skinTempC: Int = 0,
    val storageUsedGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val downloadSpeedKbps: Long = 0L,
    val uploadSpeedKbps: Long = 0L,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val displayRefreshHz: Int = 0,
    val displayDensityDpi: Int = 0,
    /**
     * تاريخ الحمل: مصدر واحد لـCPU وGPU معًا، لأن الطيف يرسمهما زوجًا على مقياس واحد.
     * وطابع كل عيّنة محفوظ معها، فيبقى الطيف صادقًا بعد إعادة فتح التطبيق: ما قيس قبل
     * أكثر من نافذة الصلاحية لا يُعرض على أنه «آخر فترة» (انظر `LoadHistory`).
     */
    val loadSamples: List<LoadSample> = emptyList(),
    val uptimeMinutes: Long = 0L,
    /** Per-core live frequencies; empty until the first poll resolves topology. */
    val cores: List<CpuCoreState> = emptyList(),
    /** GPU busy percentage, or null when this kernel exposes no usable node. */
    val gpuLoadPercent: Int? = null,
    /**
     * تردّد الرسوم الجاري بالـMHz، أو null حين لا يُقرأ.
     *
     * ومصدره **عقدة العتاد** عبر `GpuHardwareBackend` — عينها التي تعرضها شاشة GPU؛
     * ومسار البائع (`MtkUtils.getCurrentGpuFreq`) احتياطٌ وحده. والسبب مقيَّد في `readGpu`.
     */
    val gpuFreqMhz: Int? = null,
    /**
     * سقف تردّد الرسوم بالـMHz، أو null حين لا تُعلنه هذه النواة.
     *
     * ويُقرأ أولًا من `GpuHardwareBackend` — نفس مصدر شاشة الرسوم — ثم من عقدة `devfreq`
     * (`max_freq` ثم `available_frequencies`) أو جدول OPP كاحتياط، ويُحفظ بعد أول قراءة:
     * هو خاصية مدى الإقلاع، لا قياس يتغيّر كل دورتين.
     */
    val gpuCeilingMhz: Int? = null,

    /**
     * أرضية تردّد الرسوم بالـMHz، أو null حين لا تُعلنها هذه النواة — تُقرأ من الجهاز نفسه
     * الذي حلّ السقف، فلا مصدران لفكرة واحدة.
     */
    val gpuMinMhz: Int? = null,

    /**
     * أقصى تردد مدعوم للرسوم بالـMHz = أعلى درجة يُعلنها كتالوج الدرجات (`provenMaxFreq`)،
     * أو null حين لا يُعلنه — **وهو ما يستطيعه الرسّام لا ما يُسمح به الآن**. وثلاثة أرقام
     * لا يخلط بينها القارئ: هذا (مثل `1300`) ≠ `gpuCeilingMhz` السقف الحيّ (مثل `754`) ≠
     * `gpuFreqMhz` الجاري (مثل `260`) — وبطاقة الرئيسية تفرّق الثلاثة بالمواضع.
     */
    val gpuMaxSupportedMhz: Int? = null,

    /** أدنى أرضية معلنة بين الأنوية المتصلة بالميغاهرتز، أو null حين لا تُعلن أي نواة أرضية. */
    val cpuMinMhz: Int? = null,
    /** RAM history for the live chart, same cadence as [loadSamples]. */
    val ramLoadHistory: List<Float> = emptyList(),
    /** Battery drain/charge power in watts; 0 when current_now is unreadable. */
    val powerWatt: Float = 0f,
    /** ZRAM/swap usage in MB, or null when the device has no swap configured. */
    val swapUsedMb: Int? = null,
    val swapTotalMb: Int? = null,
    /** أقرب مسافة (°م) إلى نقطة تخفيف الحرارة بين مناطق المعالج والرسوم؛ سالبة حين تجاوزتها. `null` إن لم تُقرأ أي نقطة. */
    val throttleHeadroomC: Int? = null,
    /** ضغط الذاكرة من PSI؛ `UNSUPPORTED` حين لا تُصدّره النواة، فلا يُعرض صفر مزيّف. */
    val memoryStall: MemoryStall.Sample = MemoryStall.UNSUPPORTED,
)

/*
 * **ورحل «ذكاء الأداء» (`PerformanceIntelligence`) بأمر المالك (`HOME-STORY-TRIM-01`).**
 *
 * كان ثلاثة أنواعٍ وعدّادَ ثقة وحُكمَ محدِّدٍ يُبنى هنا كل دورتين، ويُعرض في **بطاقة واحدة**
 * (`VerdictPanel` في الرئيسية) — وقد حُذفت البطاقة، فبقي المُنتِج بلا مستهلك: ثلاثة أنواع
 * ونحو سبعين سطرًا تُحسب كل ثانيتين ولا يقرؤها أحد. وهذا ليس تنظيفًا شكليًّا: عملٌ دوريّ في
 * مسار القياس يُقرأ منه أن **شيئًا يُقاس**، ويُصعِّب لاحقًا معرفة ما إذا كان الرقم مسؤوليةً
 * حيّة أم بقية.
 *
 * **ولم يُنقل إلى `MaxLive` عن قصد:** ما يقيسه (عتبات ثابتة على CPU/حرارة/ذاكرة + «ثقة»
 * = نسبة امتلاء النافذة) ليس قياسًا بل عتباتٍ مُعلنة في الواجهة، و`MaxLive` تعرض الحلقة
 * الحيّة بمقاييسها الحقيقية. فنقلُه كان سيُنشئ نسخةً ثانية من الحكم في مكانٍ ثانٍ.
 */

internal fun primaryBatteryTemperatureC(state: DashboardState): Float? =
    state.batteryTempC.takeIf { it.isFinite() && it > 0f }

/** First unsigned integer in a preformatted vendor string ("47%", "1200 MHz"). */
private val LEADING_INTEGER = Regex("\\d+")

/**
 * كل ٣٠ ثانية تُكتب العيّنات المتراكمة مرّة واحدة (١٥ دورة قياس)، لا في كل دورة: الملف
 * أربع مئة بايت، لكن الكتابة كل ثانيتين عملٌ لا يلزم.
 */
private const val HISTORY_SAVE_INTERVAL_MS = 30_000L

/**
 * إيقاع نبضة القياس. **ولا يُقصَّر لمجرد الظهور:** كل تقصير يزيد عدد القراءات على العتاد،
 * والسرعة جاءت من التوازي وكاش الاحتياط الثقيل (تكملة ٢٠٥) لا من سؤال العتاد أكثر.
 */
private const val POLL_INTERVAL_MS = 2_000L
/** كل كم نبضة يُكتب سطر كلفة الحلقة في السجلّ (`adb logcat -s MaxPoll`). */
private const val POLL_LOG_EVERY = 15L
private const val POLL_TAG = "MaxPoll"
/** التخزين يتغيّر بالدقائق لا بالثواني: يُقرأ بهذا الإيقاع والقيمة السابقة تبقى بينهما. */
private const val STORAGE_REFRESH_MS = 30_000L

/** الجهاز المُكتشف لعقدة الرسوم خاصية إقلاع (المسار والوحدة): يُعاد اكتشافه كل دقيقة لا كل نبضة. */
private const val GPU_DEVICE_REFRESH_MS = 60_000L

/**
 * الحالة المشتركة للوحة القياس في العملية كلها (تكملة ٢٦١): آخر قراءة معروفة لا تُمسح بخروج الشاشة،
 * ولا تبدأ أي نسخة جديدة من الـViewModel من أصفار — فمعلومات الجهاز تفتح نسختها الخاصة عبر `viewModel()`.
 */
private object SharedDashboard {
    val state: MutableStateFlow<DashboardState> = MutableStateFlow(DashboardState())
}

class HomeDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    /** حالة مشتركة في العملية كلها ([SharedDashboard])، لا لكل نسخة من هذا الـViewModel. */
    private val _dashboardState = SharedDashboard.state
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    /*
     * «تعزيز الذاكرة» — حالة قصيرة تُقرأ في بطاقة الذاكرة، ولا تُخلط بـ[DashboardState]:
     * ذاك ليست قراءة عتاد دوريّة بل **نتيجة فعل طلبه المستخدم**، وعمرها ينتهي عند الفعل الذي
     * يليها. وخلطُها بالقراءات كان سيجعل كل دورة قياس (كل ثانيتين) تحمل معها نتيجة فعل قديم.
     */
    private val _memoryBoost = MutableStateFlow(MemoryBoostState())
    val memoryBoost: StateFlow<MemoryBoostState> = _memoryBoost.asStateFlow()

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var pollingJob: Job? = null

    /** مُقدِّر الحمل لهذه الشاشة بنافذته الخاصة، لا تُسرق من حلقات الخلفية ([FpsMonitorUtil.CpuLoadMeter]). */
    private val cpuMeter = FpsMonitorUtil.CpuLoadMeter()

    /** هل تحتاج الشاشة المرئية قراءات الآن؟ الحلقة تنام حين لا، ولا تُلغى. */
    private val pollingEnabled = MutableStateFlow(false)

    /** الشريحة البطيئة الجارية: واحدة في كل مرة، فلا تتراكم رحلات الصدفة فوق بعضها. */
    private var slowJob: Job? = null

    /** الجهاز المُكتشف لعقدة الرسوم (المسار والوحدة)، يُجدَّد كل `GPU_DEVICE_REFRESH_MS` لا كل نبضة. */
    private var gpuDevice: GpuHardwareBackend.Device? = null
    private var gpuDeviceAtMs = 0L

    /** زمن كل قارئ في آخر دورة بالميلي ثانية من بدايتها — يُكتب في سجلّ `MaxPoll` لتُعرف القراءة البطيئة. */
    private val readerDoneMs = ConcurrentHashMap<String, Long>()

    /**
     * CPU topology is fixed for the life of the boot, so cluster ranges, core
     * ceilings and ARM core names are resolved once and reused. Only
     * `scaling_cur_freq` and the online flag are re-read per tick — resolving
     * topology every 2s would mean dozens of extra root shell round-trips for
     * data that cannot change.
     */
    private var coreTopology: List<CpuCoreState>? = null

    /**
     * سقف تردّد GPU: خاصية مدى إقلاع لا قياس لحظي، فتُقرأ مرة واحدة.
     *
     * وقراءتها كل دورتين تعني ثلاث نداءات إلى العقدة في الدقيقة بلا نتيجة جديدة، ونداءً
     * لجدول OPP في كل مرة على جهاز حساس للحرارة. والقيمة `null` محفوظة كما هي: «لا سقف
     * معلَن» نتيجة نهائية، لا «لم نجرب بعد».
     */
    private var gpuCeilingCacheMhz: Int? = null
    private var gpuCeilingResolved: Boolean = false

    /** مدى تردّد الرسوم الحقيقي ومسار عقدته: يُحلّان مرة واحدة لكل إقلاع. */
    private var gpuRangeResolved: Boolean = false
    private var gpuRangeCache: Triple<Int?, Int?, Int?> = Triple(null, null, null)
    private var gpuPathCache: String? = null

    /**
     * تاريخ الحمل على القرص: كل جلسة تكمل من حيث انتهت التي قبلها، فلا يبدأ الطيف من
     * الصفر في كل فتح للتطبيق. والكتابة **مجزّأة** (انظر [HISTORY_SAVE_INTERVAL_MS]) فلا
     * تتحوّل الشاشة إلى كاتب ملفات كل ثانيتين.
     */
    private val historyStore = LoadHistoryStore(File(context.filesDir, "max_load_history.txt"))
    private var lastSavedAtMs = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restoreLoadHistory()
            // السطر يحمل رمز القطعة بين قوسين بجانب اسم المعالج (طلب المالك، 2026-10-06).
            val chip = getChipsetNameWithPartCode(context)
            val dispInfo = getDisplayInfo()
            _dashboardState.value = _dashboardState.value.copy(
                chipsetName = chip,
                displayWidth = dispInfo[0], displayHeight = dispInfo[1],
                displayRefreshHz = dispInfo[2], displayDensityDpi = dispInfo[3]
            )
        }
    }

    /**
     * ما قيس في الجلسة السابقة ويقع داخل نافذة الصلاحية يُعاد إلى الطيف كما هو — بلا
     * إعادة ترتيب ولا تصنيع: `LoadHistoryCodec` هو من يقرّر ما يُقبل (انظر `LoadHistoryTest`).
     */
    // ---- قياس الحلقة وإيقاع التخزين: الكلفة تُكتب بالمللي ثانية، والتخزين يُقرأ كل ثلاثين ثانية.
    private var pollCycles = 0L
    private var storageReadAtMs = 0L
    private var cachedStorage: FloatArray = floatArrayOf(0f, 0f)

    /** التخزين: تُجدَّد القراءة كل `STORAGE_REFRESH_MS` والقيمة السابقة تبقى بين القراءتين. */
    private fun storageForCycle(nowMs: Long): FloatArray {
        if (storageReadAtMs == 0L || nowMs - storageReadAtMs >= STORAGE_REFRESH_MS) {
            cachedStorage = readStorage()
            storageReadAtMs = nowMs
        }
        return cachedStorage
    }

    /** كلفة النبضة: السريعة والبطيئة بالمللي ثانية، تُكتب كل `POLL_LOG_EVERY` نبضة. */
    private fun recordPollCost(fastMs: Long, slowMs: Long) {
        pollCycles++
        if (pollCycles % POLL_LOG_EVERY == 0L) {
            Log.d(POLL_TAG, "cycle=$pollCycles fast=${fastMs}ms slow=${slowMs}ms readers=$readerDoneMs")
        }
    }

    private fun restoreLoadHistory() {
        // الشرط الثاني ليس زينة: لو سبقتنا دورة قياس حيّة فلا نستبدل عيّنةً قيست الآن
        // بعيّنة من الجلسة السابقة — الاسترجاع يملأ فراغًا، لا يُزاحم قياسًا فعلًا.
        if (_dashboardState.value.loadSamples.isNotEmpty()) return
        val restored = historyStore.load(System.currentTimeMillis())
        if (restored.isNotEmpty()) {
            _dashboardState.value = _dashboardState.value.copy(loadSamples = restored)
        }
    }

    /** قراءات المجموعة السريعة: إطار العمل لا الجذر. */
    private data class FastReadings(
        val ram: FpsMonitorUtil.RamInfo,
        val cpuLoad: Int,
        val battery: FloatArray,
        val storage: FloatArray,
        val network: LongArray,
    )


    /**
     * تعزيز الذاكرة: يقيس، يوقف المخبأ، يقيس — والناتج رقم مقيس أو لا رقم.
     *
     * **والصلاحية تُقرأ سلبيًّا (`cachedRootGranted`) لا بطلب:** من لا جذر له يجب ألاّ يرى
     * نافذة صلاحية لأنه مرّ على الرئيسية. فمن لا جذر له تُكتب له الحالة `blocked` ويُقال له
     * ما ينقصه — وهو نصّ الخطة («كل زرّ مُقفل يقول ماذا ينقصه لا يختفي»).
     *
     * ولا يُنادى مرّتين في وقت واحد: الفحص على `running` يمنع تراكب أمرين على الصدفة نفسها.
     */
    fun boostMemory() {
        if (_memoryBoost.value.running) return
        val root = PrivilegeManager.cachedRootGranted()
        if (!root) {
            _memoryBoost.value = MemoryBoostState(blocked = true)
            return
        }
        _memoryBoost.value = MemoryBoostState(running = true)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                // الدالّة لا القيمة: المحرّك يقرأ «قبل» ثم ينفّذ ثم يقرأ «بعد» بنفسه.
                MemoryBoostEngine.boost { MemoryBoostEngine.availableMb(context) }
            }
            _memoryBoost.value = MemoryBoostState(outcome = outcome)
        }
    }

    /**
     * تشغيل القياس وإيقافه بحسب ظهور الشاشة.
     *
     * **الإيقاف لا يُلغي شيئًا (تكملة ٢٦١):** كان يُلغي الحلقة كلها، فتضيع الشرائح البطيئة الجارية
     * (الحرارة والرسوم والأنوية) في كل مرّة تغادر فيها الشاشة قبل أن تكتمل — وهي بطيئة لأنها تمرّ بالصدفة.
     * فصارت الحلقة تنام حين تكون الشاشة مخفية، والقراءة الجارية تُكمل وتُنشر فتبقى في الحالة المشتركة.
     */
    fun setPollingActive(active: Boolean) {
        if (!active) {
            pollingEnabled.value = false
            // آخر ما قيس يُحفظ عند مغادرة الشاشة، فلا يعتمد الاستمرار على أن يمرّ وقتٌ كافٍ.
            persistLoadHistory(_dashboardState.value.loadSamples)
            return
        }
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        pollingEnabled.value = true
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch(Dispatchers.IO) { pollLoop() }.also { job ->
            job.invokeOnCompletion { if (pollingJob === job) pollingJob = null }
        }
    }

    /**
     * حلقة النبض: كل دورة تنشر السريع فورًا، ثم تُطلق الشرائح البطيئة **دون أن تنتظرها**.
     *
     * **لماذا لا تنتظرها (تكملة ٢٦١):** كانت الحلقة تنتظر أبطأ قارئ قبل الدورة التالية، فقارئ بطيء واحد
     * يوقف الدورة كلها — السريع أيضًا — ويُبقي الشاشة على الشرطات. فصارت الشرائح البطيئة مهمةً مستقلةً
     * واحدةً في كل مرة، لا تتراكم ولا تؤخّر السريع.
     */
    private suspend fun pollLoop() {
        while (true) {
            // لا قراءة لشاشة مخفية: الحلقة تنام هنا بلا كلفة حتى تُفعَّل من جديد.
            pollingEnabled.first { it }
            val cycleStartMs = SystemClock.elapsedRealtime()
            val fast = coroutineScope {
                val ram = async { FpsMonitorUtil.getRamInfo(context) }
                val cpuLoad = async { cpuMeter.sample() }
                // السريع لا يمرّ بالصدفة أبدًا: تردّد cpu0 (وقد يحتاجها الصدفة) صار في الشرائح البطيئة.
                val battery = async { readBattery() }
                val storage = async { storageForCycle(SystemClock.elapsedRealtime()) }
                val network = async { readNetwork() }
                FastReadings(
                    ram = ram.await(),
                    cpuLoad = cpuLoad.await(),
                    battery = battery.await(),
                    storage = storage.await(),
                    network = network.await(),
                )
            }
            publishFastReadings(fast)
            val fastMs = SystemClock.elapsedRealtime() - cycleStartMs
            // شريحة بطيئة واحدة في كل مرة: ما دامت الجارية لم تكتمل، تكتفي هذه الدورة بالسريع.
            if (slowJob?.isActive != true) {
                slowJob = viewModelScope.launch(Dispatchers.IO) {
                    runSlowReadings(cycleStartMs, fast, fastMs)
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * الشرائح البطيئة: ما يمرّ بعقد الجذر أو شرائح البائع. كل قراءة **تُنشر فور جهوزها** (تكملة ٢٦٠)،
     * ثم يُكتب النشر الكامل — التاريخ وطابع الدورة — بعد اكتمال الكل.
     */
    private suspend fun runSlowReadings(cycleStartMs: Long, fast: FastReadings, fastMs: Long) {
        coroutineScope {
            val thermal = async { readThermal() }
            val cores = async { readCores() }
            val gpu = async { readGpu() }
            val swap = async { readSwap() }
            val batteryTemp = async { ThermalUtil.readBatteryTemperatureC(context) }
            val memoryStall = async { MemoryPressureReader.read() }
            val power = async { FpsMonitorUtil.getPowerWatt() }
            val cpuFreq = async {
                RootFileAccess.read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                    ?.toLongOrNull()?.div(1000)?.toInt() ?: 0
            }
            publishWhenReady("thermal", cycleStartMs, thermal) { publishThermal(it) }
            publishWhenReady("cores", cycleStartMs, cores) { publishCores(it) }
            publishWhenReady("gpu", cycleStartMs, gpu) { publishGpu(it) }
            publishWhenReady("swap", cycleStartMs, swap) { s ->
                _dashboardState.update { it.copy(swapUsedMb = s?.first, swapTotalMb = s?.second) }
            }
            publishWhenReady("battery", cycleStartMs, batteryTemp) { t ->
                _dashboardState.update { it.copy(batteryTempC = t) }
            }
            publishWhenReady("memory", cycleStartMs, memoryStall) { m ->
                _dashboardState.update { it.copy(memoryStall = m) }
            }
            publishWhenReady("power", cycleStartMs, power) { w ->
                _dashboardState.update { it.copy(powerWatt = w) }
            }
            publishWhenReady("cpufreq", cycleStartMs, cpuFreq) { mhz ->
                _dashboardState.update { it.copy(cpuFreqMhz = mhz) }
            }
        }
        val slowMs = SystemClock.elapsedRealtime() - cycleStartMs - fastMs
        // النشر الكامل: ما يخصّ الدورة كلها وحدها. والقيم نفسها نُشرت فور جهوزها، فتُقرأ من الحالة المنشورة.
        val published = _dashboardState.value
        val sampledAtMs = System.currentTimeMillis()
        val samples = (published.loadSamples + LoadSample(
            atMs = sampledAtMs,
            cpu = fast.cpuLoad.toFloat(),
            gpu = published.gpuLoadPercent?.toFloat(),
            cpuMhz = published.cpuTopCoreMhz.takeIf { it > 0 },
            gpuMhz = published.gpuFreqMhz?.takeIf { it > 0 },
        )).takeLast(LoadHistory.LIMIT)
        if (sampledAtMs - lastSavedAtMs >= HISTORY_SAVE_INTERVAL_MS) {
            lastSavedAtMs = sampledAtMs
            persistLoadHistory(samples)
        }
        val ramPercent = if (fast.ram.totalMb > 0) {
            (fast.ram.usedMb.toFloat() / fast.ram.totalMb * 100f).coerceIn(0f, 100f)
        } else 0f
        _dashboardState.update {
            it.copy(
                loadSamples = samples,
                // تاريخ الذاكرة يغذّي الرسوم المفصّلة وحدها؛ الشاشة الرئيسية تعرض قيمًا حالية.
                ramLoadHistory = (it.ramLoadHistory + ramPercent).takeLast(36),
                uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L,
                // طابع الدورة **المكتملة** وحدها (تكملة ٢٠٥): النشر الجزئي لا يُعدّ قراءة دورة كاملة.
                readingsAtMs = System.currentTimeMillis(),
            )
        }
        recordPollCost(fastMs, slowMs)
    }

    /**
     * النشر الأوّل في كل نبضة: ما تُجيبه إطار العمل وحدها — **قبل** أن تمرّ أي قراءة بالجذر.
     *
     * وهو ما يجعل «سرعة ظهور القراءات» تُقاس بالإطار لا بالثانية: أرقام الذاكرة والمعالج
     * والبطارية والتخزين والشبكة تُكتب في `StateFlow` فور جهوزها، ثم يتلوها النشر الكامل فوقها.
     * **ولا يُخترع حقل:** كل قيمة هنا جاءت من قارئها، والحقول التي لم تُقرأ بعد تبقى كما كانت
     * في الحالة السابقة (صفرًا كانت أو قراءةً قديمة توسم «قديمة» في الواجهة).
     */
    private fun publishFastReadings(fast: FastReadings) {
        val status = fast.battery[3].toInt()
        _dashboardState.update {
            it.copy(
                ramUsedMb = fast.ram.usedMb,
                ramTotalMb = fast.ram.totalMb,
                cpuLoadPercent = fast.cpuLoad,
                batteryPercent = fast.battery[0].toInt(),
                batteryVoltageV = fast.battery[1] / 1000f,
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL,
                batteryStatus = batteryStatusOf(status),
                storageUsedGb = fast.storage[0],
                storageTotalGb = fast.storage[1],
                downloadSpeedKbps = fast.network[0],
                uploadSpeedKbps = fast.network[1],
                // مدة التشغيل من ساعة النظام: تظهر في أوّل إطار، لا بعد دورة كاملة.
                uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L,
            )
        }
    }

    /** نشر قراءة واحدة فور جهوزها — لا تنتظر النبضة بقية قرّائها، ويُسجَّل زمنها في الدورة. */
    private fun <T> CoroutineScope.publishWhenReady(
        name: String,
        cycleStartMs: Long,
        reading: Deferred<T>,
        publish: (T) -> Unit,
    ) = launch {
        publish(reading.await())
        readerDoneMs[name] = SystemClock.elapsedRealtime() - cycleStartMs
    }

    private fun publishThermal(t: IntArray) = _dashboardState.update {
        it.copy(
            cpuTempC = t[0],
            gpuTempC = t[1],
            skinTempC = t[2],
            throttleHeadroomC = t[3].takeIf { h -> h != NO_THROTTLE_HEADROOM },
        )
    }

    private fun publishCores(cores: List<CpuCoreState>) = _dashboardState.update {
        val online = cores.filter { c -> c.online }
        it.copy(
            cores = cores,
            cpuTopCoreMhz = online.maxOfOrNull { c -> c.freqMhz } ?: 0,
            cpuCeilingMhz = cores.maxOfOrNull { c -> c.maxFreqMhz } ?: 0,
            cpuMinMhz = online.mapNotNull { c -> c.minFreqMhz.takeIf { mhz -> mhz > 0 } }.minOrNull(),
        )
    }

    private fun publishGpu(gpu: Pair<Int?, Int?>) {
        val range = gpuRange()
        val ceiling = range.second ?: gpuCeiling()
        _dashboardState.update {
            it.copy(
                gpuLoadPercent = gpu.first,
                gpuFreqMhz = gpu.second,
                gpuCeilingMhz = ceiling,
                gpuMinMhz = range.first,
                gpuMaxSupportedMhz = range.third,
            )
        }
    }

    /** خريطة حالة البطارية — واحدة، يستعملها النشران فلا يختلف نصّان لحالة واحدة. */
    private fun batteryStatusOf(raw: Int): String = when (raw) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
        else -> "Unknown"
    }

    /** كتابة تجزئة تاريخ الحمل — على مسار IO أصلًا (حلقة القياس)، فلا تجمّد الواجهة. */
    private fun persistLoadHistory(samples: List<LoadSample>) {
        if (samples.isEmpty()) return
        historyStore.save(samples)
    }

    /**
     * Live per-core frequencies — read in **one in-process native batch** (`ProbeBridge`),
     * with the historical single batched shell call kept as the declared fallback.
     *
     * والمقيس لماذا: الصدفة كانت تفرّخ `cat` **لكل عقدة** (١٦ عملية فرعية لدورة من ٨
     * أنوية): **٣٠٧٠٢ ميكرو** على المضيف مقابل **٨٢ ميكرو** للقراءة الأصلية ⇒ **×٣٧٤**،
     * والدورة كل ثانيتين. وهذا هو نفس العطب الذي جاءت الموجة الأولى لتمحوه: الحمل ليس
     * «موضع القراءة» بل **إفراخ عملية لكل عقدة**. والقراءة الأصلية لا تشتري قدرةً: العقدة
     * التي لا يقرأها uid التطبيق تعود `null` فيسألها الاحتياطي نفسه.
     *
     * A core whose frequency node is unreadable while offline reports
     * `online = false`; that is a real hotplug state, not missing data.
     */
    private fun readCores(): List<CpuCoreState> {
        return try {
            val topology = coreTopology ?: buildCoreTopology().also { coreTopology = it }
            if (topology.isEmpty()) return emptyList()

            // «<cpu> <khz|-> <online|->» لكل عنقود — من الأصل أو من الصدفة، بنفس الشكل.
            val readings = readCoreNodes(topology) ?: shellCoreNodes(topology)

            topology.map { core ->
                val parts = readings[core.cpu]
                val khz = parts?.getOrNull(1)?.toLongOrNull()
                // cpu0 usually exposes no 'online' node; absence means online.
                val onlineFlag = parts?.getOrNull(2)
                val online = when {
                    onlineFlag == "0" -> false
                    onlineFlag == "1" -> true
                    else -> khz != null && khz > 0L
                }
                core.copy(
                    freqMhz = if (online) ((khz ?: 0L) / 1000L).toInt() else 0,
                    online = online
                )
            }
        } catch (_: Exception) {
            coreTopology.orEmpty()
        }
    }

    /**
     * مسارا كل عنقود: التردد اللحظي ثم حالة التوصيل — نفس ترتيب الاحتياطي حرفيًّا.
     */
    private fun coreNodePaths(topology: List<CpuCoreState>): List<String> =
        topology.flatMap { core ->
            val base = "/sys/devices/system/cpu/cpu${core.cpu}"
            listOf("$base/cpufreq/scaling_cur_freq", "$base/online")
        }

    /**
     * القراءة الأصلية: **نداء واحد** يقرأ كل عقد الأطراف داخل العملية.
     *
     * `null` تعني «اسأل غيري»: المكتبة غائبة، أو الحزمة عادت بعدد مخالف (يستحيل تفسيره
     * بمحاذاة مخمَّنة) — فيسأل المتصل الصدفةَ كما كانت.
     */
    private fun readCoreNodes(topology: List<CpuCoreState>): Map<Int, List<String>>? {
        val values = ProbeBridge.readMany(coreNodePaths(topology)) ?: return null
        if (values.size != topology.size * 2) return null
        return topology.mapIndexed { index, core ->
            // `-` هي دلالة `|| echo -` نفسها: عقدة غائبة أو لا تُقرأ.
            val freq = values[index * 2]?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
            val online = values[index * 2 + 1]?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
            core.cpu to listOf(core.cpu.toString(), freq, online)
        }.toMap()
    }

    /** الاحتياطي المصرَّح: صدفة واحدة تفرّخ `cat` لكل عقدة (سلوك ما قبل الجولة). */
    private fun shellCoreNodes(topology: List<CpuCoreState>): Map<Int, List<String>> {
        val script = topology.joinToString("; ") { core ->
            val base = "/sys/devices/system/cpu/cpu${core.cpu}"
            "echo \"${core.cpu} \$(cat $base/cpufreq/scaling_cur_freq 2>/dev/null || echo -) " +
                "\$(cat $base/online 2>/dev/null || echo -)\""
        }
        return Shell.cmd(script).exec().out
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val cpu = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                cpu to parts
            }.toMap()
    }

    /** Resolves the immutable part of the core matrix: ceilings, cluster tags, ARM names. */
    private fun buildCoreTopology(): List<CpuCoreState> {
        val clusters = CpuTopologyUtil.detectClusters()
        if (clusters.isEmpty()) return emptyList()
        // الأسماء تُقرأ **دفعةً واحدة لكل الأنوية** لا سؤالًا لكل نواة: `decodeCoreName` كانت تفتح
        // صدفة جذر لكل نواة (`Shell.cmd("cat …").exec()`) وهذا المسار يُعاد في كل دورة قياس على
        // الرئيسية. والنتيجة نفسها — الاسم لكل نواة لم يتغيّر، وإنّما سقطت الرحلات.
        val names = CpuTopologyUtil.coreNames(clusters.flatMap { it.cores })
        return clusters.flatMap { cluster ->
            val ceiling = CpuTopologyUtil.clusterMaxFreqMhz(cluster.policyPath)
            // الأرضية تُقرأ مع السقف ومعها: المدى كله خاصية مدى إقلاع، ولذلك يُحلّان
            // في الدورة نفسها التي تُحلّ فيها الهوية — لا نداء إضافي في كل دورة قياس.
            val floor = CpuTopologyUtil.clusterMinFreqMhz(cluster.policyPath)
            cluster.cores.map { cpu ->
                CpuCoreState(
                    cpu = cpu,
                    maxFreqMhz = ceiling,
                    minFreqMhz = floor,
                    clusterTag = cluster.shortTag,
                    coreName = names[cpu]
                )
            }
        }.sortedBy { it.cpu }
    }

    /**
     * GPU busy percentage and clock, read from the same hardware node that backs
     * the GPU screens (the vendor string is a fallback only, so the two screens
     * cannot disagree about one number). Returns nulls (never zeros) when the
     * kernel exposes nothing usable, so the UI can hide the widget instead of
     * claiming the GPU is idle.
     */
    private fun readGpu(): Pair<Int?, Int?> {
        return try {
            // Both helpers hand back preformatted strings ("47%", "1200 MHz")
            // or "N/A"; take the leading integer rather than trusting a suffix.
            val load = LEADING_INTEGER.find(MtkUtils.getGpuLoad())
                ?.value?.toIntOrNull()?.coerceIn(0, 100)
            // **والترتيب مُقلوب بطلب المالك، ودليله رقمه:** البطاقة كانت تعرض `260 MHz`
            // في صدرها والتردّد الفعليّ `754 MHz` — لأن مسار البائع (نصّ GED الجاهز)
            // كان **مُقدَّمًا** على عقدة العتاد. والعقدة هي ما تعرضه شاشة GPU (`device.currentFreq`
            // في `GpuStudioScreen.kt:245`) فهي **مصدر واحد** للرقمين لا مصدران يفترقان على أوّل
            // جهاز يخالف مفترضات أحدهما (وهو ما جرى: ٢٦٠ في الرئيسية و٧٥٤ في شاشة الرسوم
            // في اللحظة نفسها).
            //
            // ومسار البائع يبقى **احتياطًا** لا يُحذف: على أجهزة لا تُحلّ فيها عقدةٌ موثوقة
            // (وحدة بالميغاهرتز غير محسومة ⇒ `frequencyMHz` تُرجع null) هو القارئ الوحيد
            // المتاح، وغيابه كان يعني رسمًا فارغًا في بطاقة GPU — وهو العطب الأقدم المسجّل هنا.
            val freq = gpuCurrentFromHardware()
                ?: LEADING_INTEGER.find(MtkUtils.getCurrentGpuFreq())
                    ?.value?.toIntOrNull()?.takeIf { it > 0 }
            load to freq
        } catch (_: Exception) {
            null to null
        }
    }

    /**
     * تردّد الرسوم الحالي من عقدة العتاد، عبر `GpuHardwareBackend` نفسه الذي تستعمله شاشة GPU.
     *
     * والمسار مُستقصى سلبيًّا: مسار العقدة يُحلّ مرة واحدة، فإن لم تُوجد عقدة لا يُعاد السؤال
     * كل دورتين (كل قراءة فاشلة قد تكلّف نداء قشرة عبر `RootFileAccess`).
     */
    private fun gpuCurrentFromHardware(): Int? {
        val path = gpuNodePath() ?: return null
        return try {
            val now = SystemClock.elapsedRealtime()
            // الجهاز (وحدته ومساره) خاصية إقلاع: يُكتشف من جديد كل دقيقة، وبينهما تُقرأ عقدة التردد وحدها.
            val device = gpuDevice?.takeIf { it.path == path && now - gpuDeviceAtMs < GPU_DEVICE_REFRESH_MS }
                ?: GpuHardwareBackend.refresh(path)?.also { gpuDevice = it; gpuDeviceAtMs = now }
                ?: return null
            val raw = RootFileAccess.read("$path/cur_freq")?.trim()?.toLongOrNull()
            GpuHardwareBackend.frequencyMHz(device, raw)?.toInt()?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * مسار عقدة الرسوم ومداها الحقيقي (أدنى/أعلى بالميغاهرتز)، يُحلّان مرة واحدة لكل إقلاع.
     *
     * والمصدر هو `GpuHardwareBackend` — لا قارئ ثانٍ. وكانت الرئيسية تقرأ السقف بصدفة
     * `devfreq` مباشرة بينما شاشة الرسوم تعرف المدى كاملًا من العتاد، فافترق المصدران على أول
     * جهاز يخالف مفترضات أحدهما. والواحد `AMBIGUOUS` يُرفض ولا يُخمَّن (`frequencyMHz` تُرجع
     * null حين لا تثق الوحدة)، فتبقى البطاقة بلا مدى بدل مدى مصنوع.
     */
    private fun gpuRange(): Triple<Int?, Int?, Int?> {
        if (!gpuRangeResolved) {
            gpuRangeResolved = true
            gpuRangeCache = try {
                val device = GpuHardwareBackend.selection().device
                gpuPathCache = device?.path
                if (device == null || !device.unitTrusted) {
                    Triple(null, null, null)
                } else {
                    // **والحدّان من الحيّ لا من كُتالوج الدرجات:** الكتالوج يسرد ما "يستطيعه"
                    // المعالج، و`min_freq`/`max_freq` يقولان ما **يُسمح** به الآن — فمن قرأ
                    // السقف من الكتالوج عرض `1.3 GHz` على جهاز مُقيَّد عند `754 MHz`
                    // (وهو نفس العطب الذي كُتبت من أجله `configurableMaxFrequency` لمسار
                    // Per-App). وبالأرضية كذلك: `min_freq` الحيّة هي حدّ المقياس الفعلي.
                    val min = GpuHardwareBackend.frequencyMHz(device, device.minFreq ?: device.provenMinFreq)
                    val max = GpuHardwareBackend.frequencyMHz(
                        device,
                        GpuHardwareBackend.configurableMaxFrequency(device),
                    )
                    // **وأمّا الرقم الثالث فالكتالوج مصدره لا الحيّ:** «أقصى مدعوم» = أعلى
                    // درجة تُعلنها الدرجات (`provenMaxFreq`) — ما يستطيعه الرسّام لا ما يُسمح
                    // به الآن. وهذا بالضبط الرقم الذي حذّر التعليق أعلاه من عرضه سقفًا (1300
                    // مقابل 754) — يُعرض اليوم في بطاقة الرئيسية في موضعه المُسمّى فلا يعود
                    // الخلط بينهما ممكنًا.
                    val supportedMax = GpuHardwareBackend.frequencyMHz(device, device.provenMaxFreq)
                    Triple(
                        min?.toInt()?.takeIf { it > 0 },
                        max?.toInt()?.takeIf { it > 0 },
                        supportedMax?.toInt()?.takeIf { it > 0 },
                    )
                }
            } catch (_: Exception) {
                Triple(null, null, null)
            }
        }
        return gpuRangeCache
    }

    /** مسار عقدة الرسوم المُحلّ، أو null إن لم يُوجد (ولا يُسأل مرة أخرى). */
    private fun gpuNodePath(): String? {
        gpuRange()
        return gpuPathCache
    }

    /** سقف GPU مرة واحدة لكل إقلاع؛ ما بعده يُقرأ من الذاكرة. */
    private fun gpuCeiling(): Int? {
        if (!gpuCeilingResolved) {
            gpuCeilingResolved = true
            gpuCeilingCacheMhz = readGpuCeilingMhz()
        }
        return gpuCeilingCacheMhz
    }

    /**
     * سقف تردّد الرسوم، من المصادر الثلاثة التي يملكها هذا المسار بهذا الترتيب:
     *
     * 1. **`max_freq` في عقدة `devfreq`** — تصريح النواة الصريح (بالكيلوهرتز).
     * 2. **`available_frequencies`** — سلّم العقدة؛ أعلاه هو ما تقدر عليه فعلاً. يُقرأ حين
     *    لا يوجد `max_freq`، لأن قفل التردّد على بعض أنوية MTK يخفي الحدّ الأعلى ويُبقي السلّم.
     * 3. **جدول OPP** — للمسار GED/MTK وحده، ومفاتيحه بالهرتز لا بالـMHz (لهذا تُقسم على
     *    1_000_000؛ أخذ الرقم كما هو يعطي «2400000 MHz»).
     *
     * وما لم يُقرأ شيء منها فهي `null` صريحة: الشاشة تعرض التردّد **بلا مدرّج** بدل أن
     * تخترع سقفًا من أعلى قيمة رآها التطبيق — وهو ليس مدى الشريحة.
     */
    private fun readGpuCeilingMhz(): Int? {
        return try {
            val node = MtkUtils.getGpuDevfreqNode()
            val declaredKhz = node?.let { path ->
                RootFileAccess.read("$path/max_freq")?.toLongOrNull()?.takeIf { it > 0L }
            }
            declaredKhz?.div(1_000L)?.toInt()?.takeIf { it > 0 }?.let { return it }

            val ladderKhz = node?.let { path ->
                RootFileAccess.read("$path/available_frequencies")
                    ?.split(Regex("\\s+"))
                    ?.mapNotNull { it.toLongOrNull() }
                    ?.filter { it > 0L }
                    ?.maxOrNull()
            }
            ladderKhz?.div(1_000L)?.toInt()?.takeIf { it > 0 }?.let { return it }

            MtkUtils.oppFrequenciesHz()
                .maxOrNull()
                ?.div(1_000_000L)
                ?.toInt()
                ?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /** ZRAM/swap usage in MB, or null when no swap device is configured. */
    private fun readSwap(): Pair<Int, Int>? {
        return try {
            // `/proc/meminfo` تُقرأ داخل العملية في نداء واحد، والاحتياطي هو صدفة `grep`
            // نفسها (والتصفية أدناه تعمل على الشكلين: ملف كامل أو سطرين).
            val info = ProbeBridge.readMany(listOf("/proc/meminfo"))
                ?.firstOrNull()
                ?.lines()
                ?.takeIf { it.isNotEmpty() }
                ?: Shell.cmd("grep -E '^Swap(Total|Free):' /proc/meminfo 2>/dev/null").exec().out
            val total = info.firstOrNull { it.startsWith("SwapTotal") }
                ?.let { Regex("\\d+").find(it)?.value?.toLongOrNull() } ?: return null
            if (total <= 0L) return null
            val free = info.firstOrNull { it.startsWith("SwapFree") }
                ?.let { Regex("\\d+").find(it)?.value?.toLongOrNull() } ?: 0L
            (((total - free) / 1024L).toInt()) to ((total / 1024L).toInt())
        } catch (_: Exception) {
            null
        }
    }

    private fun readBattery(): FloatArray {
        return try {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (scale > 0) (level * 100f / scale) else 0f
            val volt = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0).toFloat()
            val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0).toFloat()
            val status = (i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0).toFloat()
            floatArrayOf(pct, volt, temp, status)
        } catch (e: Exception) { floatArrayOf(0f, 0f, 0f, 0f) }
    }

    private fun readThermal(): IntArray {
        return try {
            val zones = ThermalUtil.readThermalZones()
            var cpu = zones.filter { it.category == "CPU" && it.temperatureC > 0 }
                .maxOfOrNull { it.temperatureC } ?: 0
            var gpu = zones.filter { it.category == "GPU" && it.temperatureC > 0 }
                .maxOfOrNull { it.temperatureC } ?: 0
            var skin = zones.filter { it.category == "Skin" && it.temperatureC > 0 }
                .maxOfOrNull { it.temperatureC } ?: 0

            // Fallback for MTK/vendor kernels where thermal sysfs nodes are
            // hidden or their names do not contain a recognizable category.
            //
            // **ولا يُسأل إلا حين تنقص فئة فعلًا (تكملة ٢٠٥ — عطب أداء مقيس):** كان النداء
            // **مطلقًا في كل نبضة قياس (كل ثانيتين)**، وهو نداء `dumpsys thermalservice`
            // عبر صدفة الجذر، **ثم يُطرح ناتجه في الحال** في الحالة الشائعة (المناطق مقروءة
            // من `sysfs`) — أي رحلة ثقيلة كل ثانيتين بلا خبر. وقد أُضيف معه كاش بنافذة
            // صلاحية في `ThermalUtil`، وهذا الشرط هو الطرف الثاني من الإصلاح: **لا
            // يُسأل سؤال ثقيل لا يجيبه إلا حالةٌ نادرة، ولا يجيبه في الحالة الشائعة.**
            if (cpu == 0 || gpu == 0 || skin == 0) {
                val service = ThermalUtil.readThermalServiceTemperatures()
                if (cpu == 0) cpu = service[0]
                if (gpu == 0) gpu = service[1]
                if (skin == 0) skin = service[2]
            }

            // Last-resort SoC fallback for vendor kernels that expose an
            // unnamed/"system" sensor instead of CPU-specific zones.
            if (cpu == 0) {
                cpu = zones.asSequence()
                    .filter { it.temperatureC > 0 && it.category !in setOf("Battery", "Skin", "Charger", "GPU") }
                    .maxOfOrNull { it.temperatureC } ?: 0
            }
            intArrayOf(cpu, gpu, skin, HomeLiveReadings.throttleHeadroomOf(zones))
        } catch (_: Exception) {
            intArrayOf(0, 0, 0, NO_THROTTLE_HEADROOM)
        }
    }

    private fun readStorage(): FloatArray {
        return try {
            val sf = StatFs("/data")
            val total = sf.blockSizeLong * sf.blockCountLong
            val free = sf.blockSizeLong * sf.availableBlocksLong
            floatArrayOf((total - free) / 1_073_741_824f, total / 1_073_741_824f)
        } catch (e: Exception) { floatArrayOf(0f, 0f) }
    }

    private fun readNetwork(): LongArray {
        val newRx = TrafficStats.getTotalRxBytes()
        val newTx = TrafficStats.getTotalTxBytes()
        val dl = if (newRx > lastRxBytes) (newRx - lastRxBytes) / 2048L else 0L
        val ul = if (newTx > lastTxBytes) (newTx - lastTxBytes) / 2048L else 0L
        lastRxBytes = newRx; lastTxBytes = newTx
        return longArrayOf(dl, ul)
    }

    @Suppress("DEPRECATION")
    private fun getDisplayInfo(): IntArray {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            intArrayOf(metrics.widthPixels, metrics.heightPixels, display.refreshRate.toInt(), metrics.densityDpi)
        } catch (e: Exception) { intArrayOf(0, 0, 0, 0) }
    }
}
