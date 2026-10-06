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
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import nd.max.core.hardware.RootFileAccess
import nd.max.core.jni.ProbeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.ui.util.CpuTopologyUtil
import nd.max.core.platform.FpsMonitorUtil
import nd.max.ui.util.LoadHistory
import nd.max.ui.util.LoadHistoryStore
import nd.max.ui.util.LoadSample
import nd.max.ui.util.MtkUtils
import nd.max.core.platform.ThermalUtil
import nd.max.core.platform.getChipsetNameWithPartCode
import java.io.File

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

data class DashboardState(
    val intelligence: PerformanceIntelligence = PerformanceIntelligence(),
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
    val swapTotalMb: Int? = null
)

data class PerformanceIntelligenceSample(
    val timestampMs: Long,
    val cpuPercent: Int,
    val ramPercent: Int,
    val gpuPercent: Int?,
    val temperatureC: Float,
    val powerWatt: Float,
    val displayPixels: Long,
    val networkKbps: Long,
    val workload: String
)

data class PerformanceIntelligenceEvent(
    val timestampMs: Long,
    val title: String,
    val reason: String,
    val impact: String
)

data class PerformanceIntelligence(
    val sessionStartedAtMs: Long = System.currentTimeMillis(),
    val samples: List<PerformanceIntelligenceSample> = emptyList(),
    val events: List<PerformanceIntelligenceEvent> = emptyList(),
    val primaryLimiter: String = "Baseline",
    val explanation: String = "Collecting live samples",
    val realImprovement: Boolean? = null,
    val confidencePercent: Int = 0
)


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

class HomeDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var pollingJob: Job? = null

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
        val cpuFreqMhz: Int,
        val battery: FloatArray,
        val storage: FloatArray,
        val network: LongArray,
    )

    /** قراءات المجموعة البطيئة: ما يمرّ بعقد الجذر أو شرائح البائع. */
    private data class SlowReadings(
        val thermal: IntArray,
        val cores: List<CpuCoreState>,
        val gpu: Pair<Int?, Int?>,
        val swap: Pair<Int, Int>?,
        val batteryTemp: Float,
    )

    fun setPollingActive(active: Boolean) {
        if (!active) {
            pollingJob?.cancel()
            pollingJob = null
            // آخر ما قيس يُحفظ عند مغادرة الشاشة، فلا يعتمد الاستمرار على أن يمرّ وقتٌ كافٍ.
            persistLoadHistory(_dashboardState.value.loadSamples)
            return
        }
        if (pollingJob?.isActive == true) return
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                // **نبضةٌ متوازية تنشر ما جاهز فورًا (تكملة ٢٠٥ — عطب أداء مُبلّغ عنه).**
                //
                // كانت هذه القراءات **سلسلةً متتابعة**: كل قارئ ينتظر الذي قبله، وحصيلة
                // الانتظار هي مجموعها لا أقصاها — ثم لا يُنشر شيء حتى يكتمل آخرها. فالنبضة
                // الواحدة قد تستغرق عشرات الثواني على جهاز بطيء، والمالك يرى «لا قراءة» دقيقةً
                // ثم تُعرض الأرقام دفعةً واحدة.
                //
                // وصارت قراءتين **مصدرين مستقلّين متوازيين**:
                //
                // 1. **السريع:** ما تقرؤه إطار العمل وحدها أو عقدةٌ واحدة (`ActivityManager` ·
                //    `TrafficStats` · `StatFs` · بثّ البطارية · `scaling_cur_freq`) — يُنشر
                //    **فورًا**، فتظهر أرقام الرئيسية في أوّل إطار بدل أن تنتظر الصدفة.
                // 2. **البطيء:** ما يمرّ بعقد الجذر أو شرائح البائع (المناطق الحرارية · الأنوية ·
                //    الرسوم · المبادلة · حرارة البطارية) — يتوازى معه، ثم يُنشر ناتجه فوقه.
                //
                // **ولماذا هذان تجميعان لا أحد عشر مهمّة حرّة:** كل مجموعة تحمل قارئها المتتابع
                // بالأثر الحسّاس، فلا يتنافس قارئان على الكاش الداخلي نفسه (`coreTopology` ·
                // كاش عقدة الرسوم · كاش مناطق الحرارة) — توازٍ بلا سباق.
                val fast = coroutineScope {
                    val ram = async { FpsMonitorUtil.getRamInfo(context) }
                    val cpuLoad = async { FpsMonitorUtil.getCpuLoad() }
                    // طبقة القراءة الأسرع (قارئ أصلي ← IPC ← ملف ← صدفة): وصدفة `cat` لكل
                    // نبضة كانت رحلة كاملة.
                    val cpuFreq = async {
                        RootFileAccess.read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                            ?.toLongOrNull()?.div(1000)?.toInt() ?: 0
                    }
                    val battery = async { readBattery() }
                    val storage = async { readStorage() }
                    val network = async { readNetwork() }
                    FastReadings(
                        ram = ram.await(),
                        cpuLoad = cpuLoad.await(),
                        cpuFreqMhz = cpuFreq.await(),
                        battery = battery.await(),
                        storage = storage.await(),
                        network = network.await(),
                    )
                }
                publishFastReadings(fast)
                // والأسماء نفسها التي يستعملها ما بعدها: النشر الكامل يقرأ من المجموعة السريعة
                // كما كان يقرأ من قراءاتها المتتابعة — **قيمة واحدة من قارئ واحد**، لا نسخة ثانية.
                val ram = fast.ram
                val cpuLoad = fast.cpuLoad
                val cpuFreq = fast.cpuFreqMhz
                val battery = fast.battery
                val storage = fast.storage
                val network = fast.network

                val slow = coroutineScope {
                    val thermal = async { readThermal() }
                    val cores = async { readCores() }
                    val gpu = async { readGpu() }
                    val swap = async { readSwap() }
                    val batteryTemp = async { ThermalUtil.readBatteryTemperatureC(context) }
                    SlowReadings(
                        thermal = thermal.await(),
                        cores = cores.await(),
                        gpu = gpu.await(),
                        swap = swap.await(),
                        batteryTemp = batteryTemp.await(),
                    )
                }
                val thermal = slow.thermal
                val cores = slow.cores
                val gpu = slow.gpu
                val swap = slow.swap
                val batteryTemp = slow.batteryTemp
                val onlineCores = cores.filter { it.online }
                val cpuTopCoreMhz = onlineCores.maxOfOrNull { it.freqMhz } ?: 0
                val cpuCeilingMhz = cores.maxOfOrNull { it.maxFreqMhz } ?: 0
                val cpuMinMhz = onlineCores.mapNotNull { it.minFreqMhz.takeIf { mhz -> mhz > 0 } }
                    .minOrNull()
                // المدى الحقيقي للرسوم أولًا، ثم عقدة devfreq كاحتياط: السقف الذي تقرأه
                // البطاقة هو نفسه الذي تعرفه شاشة GPU، فلا رقمان لفكرة واحدة.
                val gpuRange = gpuRange()
                val gpuCeilingMhz = gpuRange.second ?: gpuCeiling()

                val previous = _dashboardState.value
                val ramPercent = if (ram.totalMb > 0) {
                    (ram.usedMb.toFloat() / ram.totalMb * 100f).coerceIn(0f, 100f)
                } else 0f
                val powerWatt = FpsMonitorUtil.getPowerWatt()
                val intelligence = updatePerformanceIntelligence(
                    previous = previous.intelligence,
                    cpuPercent = cpuLoad,
                    ramPercent = ramPercent.toInt(),
                    gpuPercent = gpu.first,
                    temperatureC = batteryTemp.takeIf { it.isFinite() && it > 0f } ?: thermal[0].toFloat(),
                    powerWatt = powerWatt,
                    displayPixels = dispInfoPixelCount(),
                    networkKbps = network[0] + network[1]
                )

                // عيّنة واحدة تحمل CPU وGPU معًا، ومعها طابعها: فتبقى الرسوم بعد إعادة فتح
                // التطبيق زوجًا مرتّبًا لا قائمتين قد تنفصلان إحداهما عن الأخرى. ومع النسبتين
                // **تردّدهما** لأن موجة الساعة في بطاقتَي الرئيسية ترسم هذه اللحظة نفسها.
                val sampledAtMs = System.currentTimeMillis()
                val samples = (
                    previous.loadSamples + LoadSample(
                        atMs = sampledAtMs,
                        cpu = cpuLoad.toFloat(),
                        gpu = gpu.first?.toFloat(),
                        cpuMhz = cpuTopCoreMhz.takeIf { it > 0 },
                        gpuMhz = gpu.second?.takeIf { it > 0 },
                    )
                    ).takeLast(LoadHistory.LIMIT)
                if (sampledAtMs - lastSavedAtMs >= HISTORY_SAVE_INTERVAL_MS) {
                    lastSavedAtMs = sampledAtMs
                    persistLoadHistory(samples)
                }

                _dashboardState.value = previous.copy(
                    ramUsedMb = ram.usedMb, ramTotalMb = ram.totalMb,
                    cpuLoadPercent = cpuLoad, cpuFreqMhz = cpuFreq,
                    cpuTopCoreMhz = cpuTopCoreMhz, cpuCeilingMhz = cpuCeilingMhz,
                    gpuCeilingMhz = gpuCeilingMhz,
                    gpuMinMhz = gpuRange.first,
                    gpuMaxSupportedMhz = gpuRange.third,
                    cpuMinMhz = cpuMinMhz,
                    loadSamples = samples,
                    // تاريخ الذاكرة يغذّي الرسوم المفصّلة وحدها؛ الشاشة الرئيسية تعرض قيمًا
                    // حالية معنونة بالتسمية، بلا خطوط متحرّكة غامضة.
                    ramLoadHistory = (previous.ramLoadHistory + ramPercent).takeLast(36),
                    gpuLoadPercent = gpu.first, gpuFreqMhz = gpu.second,
                    cores = cores,
                    batteryPercent = battery[0].toInt(), batteryVoltageV = battery[1] / 1000f,
                    batteryTempC = batteryTemp,
                    isCharging = battery[3].toInt() == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 battery[3].toInt() == BatteryManager.BATTERY_STATUS_FULL,
                    batteryStatus = batteryStatusOf(battery[3].toInt()),
                    powerWatt = powerWatt,
                    swapUsedMb = swap?.first, swapTotalMb = swap?.second,
                    cpuTempC = thermal[0], gpuTempC = thermal[1], skinTempC = thermal[2],
                    storageUsedGb = storage[0], storageTotalGb = storage[1],
                    downloadSpeedKbps = network[0], uploadSpeedKbps = network[1],
                    uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L,
                    intelligence = intelligence,
                    // **وطابع القراءة يُكتب هنا لا في النشر الأوّل:** `readingsAtMs > 0` تعني
                    // «اكتملت دورة قراءة واحدة على الأقل» — وهذا ما يفرّق في الواجهة بين
                    // «يُقرأ الآن» و«غير مقروء» (تكملة ٢٠٥).
                    readingsAtMs = System.currentTimeMillis()
                )
                delay(POLL_INTERVAL_MS)
            }
        }.also { job ->
            job.invokeOnCompletion { if (pollingJob === job) pollingJob = null }
        }
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
        val previous = _dashboardState.value
        val status = fast.battery[3].toInt()
        _dashboardState.value = previous.copy(
            ramUsedMb = fast.ram.usedMb,
            ramTotalMb = fast.ram.totalMb,
            cpuLoadPercent = fast.cpuLoad,
            cpuFreqMhz = fast.cpuFreqMhz,
            batteryPercent = fast.battery[0].toInt(),
            batteryVoltageV = fast.battery[1] / 1000f,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            batteryStatus = batteryStatusOf(status),
            storageUsedGb = fast.storage[0],
            storageTotalGb = fast.storage[1],
            downloadSpeedKbps = fast.network[0],
            uploadSpeedKbps = fast.network[1],
        )
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

    private fun dispInfoPixelCount(): Long {
        val display = getDisplayInfo()
        return display.getOrElse(0) { 0 }.toLong() * display.getOrElse(1) { 0 }.toLong()
    }

    private fun updatePerformanceIntelligence(
        previous: PerformanceIntelligence,
        cpuPercent: Int,
        ramPercent: Int,
        gpuPercent: Int?,
        temperatureC: Float,
        powerWatt: Float,
        displayPixels: Long,
        networkKbps: Long
    ): PerformanceIntelligence {
        val now = System.currentTimeMillis()
        val workload = when {
            cpuPercent >= 75 && (gpuPercent ?: 0) >= 55 -> "CPU+GPU workload"
            cpuPercent >= 75 -> "CPU-bound workload"
            (gpuPercent ?: 0) >= 60 -> "GPU-bound workload"
            ramPercent >= 82 -> "Memory pressure"
            networkKbps >= 1024 -> "Network-active workload"
            else -> "Light/system workload"
        }
        val sample = PerformanceIntelligenceSample(now, cpuPercent, ramPercent, gpuPercent, temperatureC, powerWatt, displayPixels, networkKbps, workload)
        val samples = (previous.samples + sample).takeLast(180)
        val baseline = samples.take(30).takeIf { it.size >= 3 } ?: samples
        val baseCpu = baseline.map { it.cpuPercent }.average().takeUnless { it.isNaN() } ?: cpuPercent.toDouble()
        val baseTemp = baseline.map { it.temperatureC }.average().takeUnless { it.isNaN() } ?: temperatureC.toDouble()
        val basePower = baseline.map { it.powerWatt }.filter { it > 0f }.average().takeUnless { it.isNaN() } ?: powerWatt.toDouble()
        val cpuDelta = cpuPercent - baseCpu
        val tempDelta = temperatureC - baseTemp
        val powerDelta = if (powerWatt > 0f && basePower > 0.0) powerWatt - basePower else 0.0
        val primaryLimiter = when {
            temperatureC >= 45f && cpuPercent >= 55 -> "Thermal"
            cpuPercent >= 82 -> "CPU"
            (gpuPercent ?: 0) >= 82 -> "GPU"
            ramPercent >= 86 -> "Memory"
            powerWatt >= 7f && temperatureC >= 40f -> "Power/Thermal"
            displayPixels > 0L && (gpuPercent ?: 0) >= 60 -> "Display/GPU"
            else -> "Baseline"
        }
        val explanation = when (primaryLimiter) {
            "Thermal" -> "Temperature rose ${signed(cpuDelta)} CPU points and ${signed(tempDelta)}°C from baseline; sustained boost may throttle."
            "CPU" -> "CPU load is the dominant pressure; GPU and memory are not the first limiter."
            "GPU" -> "GPU load is dominating the current frame path; display resolution/refresh may affect this."
            "Memory" -> "RAM pressure is high; ZRAM and app behavior are likely affecting responsiveness."
            "Power/Thermal" -> "Power draw and heat are rising together, so improvement must be judged by sustainability, not peak speed."
            "Display/GPU" -> "The render target is interacting with GPU load; resolution and refresh are part of the performance story."
            else -> "No single limiter dominates yet; this window is a real baseline for before/after comparison."
        }
        val realImprovement = when {
            samples.size < 6 -> null
            cpuDelta < -8 && tempDelta <= 1.5 && powerDelta <= 1.0 -> true
            cpuDelta > 10 && tempDelta > 2.5 -> false
            else -> null
        }
        val event = when {
            previous.primaryLimiter != primaryLimiter && samples.size > 3 -> PerformanceIntelligenceEvent(
                now,
                "Limiter changed to $primaryLimiter",
                explanation,
                if (primaryLimiter == "Baseline") "System returned to baseline" else "User should inspect the $primaryLimiter path"
            )
            realImprovement == true && previous.realImprovement != true -> PerformanceIntelligenceEvent(now, "Improvement looks real", explanation, "Lower pressure without extra heat")
            realImprovement == false && previous.realImprovement != false -> PerformanceIntelligenceEvent(now, "Gain is not sustainable", explanation, "Load and heat rose together")
            else -> null
        }
        return previous.copy(
            samples = samples,
            events = (previous.events + listOfNotNull(event)).takeLast(24),
            primaryLimiter = primaryLimiter,
            explanation = explanation,
            realImprovement = realImprovement,
            confidencePercent = ((samples.size.coerceAtMost(30) / 30f) * 100).toInt()
        )
    }

    private fun signed(value: Double): String = if (value >= 0) "+${value.toInt()}" else value.toInt().toString()

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
            val device = GpuHardwareBackend.refresh(path) ?: return null
            GpuHardwareBackend.frequencyMHz(device, device.currentFreq)?.toInt()?.takeIf { it > 0 }
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
            intArrayOf(cpu, gpu, skin)
        } catch (_: Exception) {
            intArrayOf(0, 0, 0)
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
