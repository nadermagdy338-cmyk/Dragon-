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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.FpsMonitorUtil
import nd.max.ui.util.LoadHistory
import nd.max.ui.util.LoadHistoryStore
import nd.max.ui.util.LoadSample
import nd.max.ui.util.MtkUtils
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.getChipsetName
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
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val cpuLoadPercent: Int = 0,
    val cpuFreqMhz: Int = 0,
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
    /** GPU clock in MHz, or null when unreadable. */
    val gpuFreqMhz: Int? = null,
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
     * تاريخ الحمل على القرص: كل جلسة تكمل من حيث انتهت التي قبلها، فلا يبدأ الطيف من
     * الصفر في كل فتح للتطبيق. والكتابة **مجزّأة** (انظر [HISTORY_SAVE_INTERVAL_MS]) فلا
     * تتحوّل الشاشة إلى كاتب ملفات كل ثانيتين.
     */
    private val historyStore = LoadHistoryStore(File(context.filesDir, "max_load_history.txt"))
    private var lastSavedAtMs = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restoreLoadHistory()
            val chip = getChipsetName(context)
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
                val ram = FpsMonitorUtil.getRamInfo(context)
                val cpuLoad = FpsMonitorUtil.getCpuLoad()
                val cpuFreq = Shell.cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0
                val battery = readBattery()
                val thermal = readThermal()
                val storage = readStorage()
                val network = readNetwork()
                val cores = readCores()
                val gpu = readGpu()
                val swap = readSwap()

                val previous = _dashboardState.value
                val ramPercent = if (ram.totalMb > 0) {
                    (ram.usedMb.toFloat() / ram.totalMb * 100f).coerceIn(0f, 100f)
                } else 0f
                val batteryTemp = ThermalUtil.readBatteryTemperatureC(context)
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

                // عيّنة واحدة تحمل CPU وGPU معًا، ومعها طابعها: فيبقى الطيف بعد إعادة فتح
                // التطبيق زوجًا مرتّبًا لا قائمتين قد تنفصلان إحداهما عن الأخرى.
                val sampledAtMs = System.currentTimeMillis()
                val samples = (
                    previous.loadSamples + LoadSample(sampledAtMs, cpuLoad.toFloat(), gpu.first?.toFloat())
                    ).takeLast(LoadHistory.LIMIT)
                if (sampledAtMs - lastSavedAtMs >= HISTORY_SAVE_INTERVAL_MS) {
                    lastSavedAtMs = sampledAtMs
                    persistLoadHistory(samples)
                }

                _dashboardState.value = previous.copy(
                    ramUsedMb = ram.usedMb, ramTotalMb = ram.totalMb,
                    cpuLoadPercent = cpuLoad, cpuFreqMhz = cpuFreq,
                    loadSamples = samples,
                    // التاريخ التالي يغذّي الرسوم المفصّلة وحدها؛ الشاشة الرئيسية تعرض قيمًا
                    // حالية معنونة بالتسمية، بلا خطوط متحرّكة غامضة.
                    ramLoadHistory = (previous.ramLoadHistory + ramPercent).takeLast(36),
                    gpuLoadPercent = gpu.first, gpuFreqMhz = gpu.second,
                    cores = cores,
                    batteryPercent = battery[0].toInt(), batteryVoltageV = battery[1] / 1000f,
                    batteryTempC = batteryTemp,
                    isCharging = battery[3].toInt() == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 battery[3].toInt() == BatteryManager.BATTERY_STATUS_FULL,
                    batteryStatus = when (battery[3].toInt()) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        else -> "Unknown"
                    },
                    powerWatt = powerWatt,
                    swapUsedMb = swap?.first, swapTotalMb = swap?.second,
                    cpuTempC = thermal[0], gpuTempC = thermal[1], skinTempC = thermal[2],
                    storageUsedGb = storage[0], storageTotalGb = storage[1],
                    downloadSpeedKbps = network[0], uploadSpeedKbps = network[1],
                    uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L,
                    intelligence = intelligence
                )
                delay(2000)
            }
        }.also { job ->
            job.invokeOnCompletion { if (pollingJob === job) pollingJob = null }
        }
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
     * Live per-core frequencies. Every core's `scaling_cur_freq` and `online`
     * node is fetched in a single batched shell invocation — one root
     * round-trip per tick regardless of core count, instead of 2N.
     *
     * A core whose frequency node is unreadable while offline reports
     * `online = false`; that is a real hotplug state, not missing data.
     */
    private fun readCores(): List<CpuCoreState> {
        return try {
            val topology = coreTopology ?: buildCoreTopology().also { coreTopology = it }
            if (topology.isEmpty()) return emptyList()

            // One shell call emits "<cpu> <khz|-> <online|->" per core.
            val script = topology.joinToString("; ") { core ->
                val base = "/sys/devices/system/cpu/cpu${core.cpu}"
                "echo \"${core.cpu} \$(cat $base/cpufreq/scaling_cur_freq 2>/dev/null || echo -) " +
                    "\$(cat $base/online 2>/dev/null || echo -)\""
            }
            val readings = Shell.cmd(script).exec().out
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    val cpu = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    cpu to parts
                }.toMap()

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

    /** Resolves the immutable part of the core matrix: ceilings, cluster tags, ARM names. */
    private fun buildCoreTopology(): List<CpuCoreState> {
        val clusters = CpuTopologyUtil.detectClusters()
        if (clusters.isEmpty()) return emptyList()
        return clusters.flatMap { cluster ->
            val ceiling = CpuTopologyUtil.clusterMaxFreqMhz(cluster.policyPath)
            cluster.cores.map { cpu ->
                CpuCoreState(
                    cpu = cpu,
                    maxFreqMhz = ceiling,
                    clusterTag = cluster.shortTag,
                    coreName = CpuTopologyUtil.decodeCoreName(cpu)
                )
            }
        }.sortedBy { it.cpu }
    }

    /**
     * GPU busy percentage and clock, reusing the vendor-node logic that already
     * backs the GPU screens. Returns nulls (never zeros) when the kernel
     * exposes nothing usable, so the UI can hide the widget instead of
     * claiming the GPU is idle.
     */
    private fun readGpu(): Pair<Int?, Int?> {
        return try {
            // Both helpers hand back preformatted strings ("47%", "1200 MHz")
            // or "N/A"; take the leading integer rather than trusting a suffix.
            val load = LEADING_INTEGER.find(MtkUtils.getGpuLoad())
                ?.value?.toIntOrNull()?.coerceIn(0, 100)
            val freq = LEADING_INTEGER.find(MtkUtils.getCurrentGpuFreq())
                ?.value?.toIntOrNull()?.takeIf { it > 0 }
            load to freq
        } catch (_: Exception) {
            null to null
        }
    }

    /** ZRAM/swap usage in MB, or null when no swap device is configured. */
    private fun readSwap(): Pair<Int, Int>? {
        return try {
            val info = Shell.cmd("grep -E '^Swap(Total|Free):' /proc/meminfo 2>/dev/null").exec().out
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
            val service = ThermalUtil.readThermalServiceTemperatures()
            if (cpu == 0) cpu = service[0]
            if (gpu == 0) gpu = service[1]
            if (skin == 0) skin = service[2]

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
