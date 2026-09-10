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
import nd.max.ui.util.MtkUtils
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.getChipsetName

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
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val cpuLoadPercent: Int = 0,
    val cpuFreqMhz: Int = 0,
    val chipsetName: String = "...",
    val batteryPercent: Int = 0,
    val batteryVoltageV: Float = 0f,
    val batteryTempC: Float = 0f,
    val isCharging: Boolean = false,
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
    /** CPU load history reserved for the detailed telemetry view. */
    val cpuLoadHistory: List<Float> = emptyList(),
    val uptimeMinutes: Long = 0L,
    /** Per-core live frequencies; empty until the first poll resolves topology. */
    val cores: List<CpuCoreState> = emptyList(),
    /** GPU busy percentage, or null when this kernel exposes no usable node. */
    val gpuLoadPercent: Int? = null,
    /** GPU clock in MHz, or null when unreadable. */
    val gpuFreqMhz: Int? = null,
    /** RAM history for the live chart, same cadence as [cpuLoadHistory]. */
    val ramLoadHistory: List<Float> = emptyList(),
    /** GPU history for the live chart; only appended when a real GPU reading exists. */
    val gpuLoadHistory: List<Float> = emptyList(),
    /** Battery drain/charge power in watts; 0 when current_now is unreadable. */
    val powerWatt: Float = 0f,
    /** ZRAM/swap usage in MB, or null when the device has no swap configured. */
    val swapUsedMb: Int? = null,
    val swapTotalMb: Int? = null
)

internal fun primaryBatteryTemperatureC(state: DashboardState): Float? =
    state.batteryTempC.takeIf { it.isFinite() && it > 0f }

/** First unsigned integer in a preformatted vendor string ("47%", "1200 MHz"). */
private val LEADING_INTEGER = Regex("\\d+")

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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val chip = getChipsetName(context)
            val dispInfo = getDisplayInfo()
            _dashboardState.value = _dashboardState.value.copy(
                chipsetName = chip,
                displayWidth = dispInfo[0], displayHeight = dispInfo[1],
                displayRefreshHz = dispInfo[2], displayDensityDpi = dispInfo[3]
            )
        }
    }

    fun setPollingActive(active: Boolean) {
        if (!active) {
            pollingJob?.cancel()
            pollingJob = null
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

                _dashboardState.value = previous.copy(
                    ramUsedMb = ram.usedMb, ramTotalMb = ram.totalMb,
                    cpuLoadPercent = cpuLoad, cpuFreqMhz = cpuFreq,
                    cpuLoadHistory = (previous.cpuLoadHistory + cpuLoad.toFloat()).takeLast(36),
                    // Histories feed detailed telemetry only. The home dashboard
                    // renders current, labeled values; no ambiguous animated lines.
                    ramLoadHistory = (previous.ramLoadHistory + ramPercent).takeLast(36),
                    gpuLoadHistory = gpu.first
                        ?.let { (previous.gpuLoadHistory + it.toFloat()).takeLast(36) }
                        ?: previous.gpuLoadHistory,
                    gpuLoadPercent = gpu.first, gpuFreqMhz = gpu.second,
                    cores = cores,
                    batteryPercent = battery[0].toInt(), batteryVoltageV = battery[1] / 1000f,
                    batteryTempC = ThermalUtil.readBatteryTemperatureC(context),
                    isCharging = battery[3].toInt() == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 battery[3].toInt() == BatteryManager.BATTERY_STATUS_FULL,
                    batteryStatus = when (battery[3].toInt()) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        else -> "Unknown"
                    },
                    powerWatt = FpsMonitorUtil.getPowerWatt(),
                    swapUsedMb = swap?.first, swapTotalMb = swap?.second,
                    cpuTempC = thermal[0], gpuTempC = thermal[1], skinTempC = thermal[2],
                    storageUsedGb = storage[0], storageTotalGb = storage[1],
                    downloadSpeedKbps = network[0], uploadSpeedKbps = network[1],
                    uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L
                )
                delay(2000)
            }
        }.also { job ->
            job.invokeOnCompletion { if (pollingJob === job) pollingJob = null }
        }
    }

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
