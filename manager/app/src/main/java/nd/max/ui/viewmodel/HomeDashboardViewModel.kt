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
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nd.max.ui.util.FpsMonitorUtil
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.getChipsetName

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
    val displayRefreshHz: Int = 60,
    val displayDensityDpi: Int = 0,
    val cpuLoadHistory: List<Float> = emptyList()
)

class HomeDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()

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
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val ram = FpsMonitorUtil.getRamInfo(context)
                val cpuLoad = FpsMonitorUtil.getCpuLoad()
                val cpuFreq = Shell.cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0
                val battery = readBattery()
                val thermal = readThermal()
                val storage = readStorage()
                val network = readNetwork()

                _dashboardState.value = _dashboardState.value.copy(
                    ramUsedMb = ram.usedMb, ramTotalMb = ram.totalMb,
                    cpuLoadPercent = cpuLoad, cpuFreqMhz = cpuFreq,
                    cpuLoadHistory = (_dashboardState.value.cpuLoadHistory + cpuLoad.toFloat()).takeLast(36),
                    batteryPercent = battery[0].toInt(), batteryVoltageV = battery[1] / 1000f,
                    batteryTempC = (if (battery[2] > 0f) battery[2] / 10f else ThermalUtil.readBatteryTemperatureC().toFloat()),
                    isCharging = battery[3].toInt() == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 battery[3].toInt() == BatteryManager.BATTERY_STATUS_FULL,
                    batteryStatus = when (battery[3].toInt()) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        else -> "Unknown"
                    },
                    cpuTempC = thermal[0], gpuTempC = thermal[1], skinTempC = thermal[2],
                    storageUsedGb = storage[0], storageTotalGb = storage[1],
                    downloadSpeedKbps = network[0], uploadSpeedKbps = network[1]
                )
                delay(2000)
            }
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
        } catch (e: Exception) { intArrayOf(0, 0, 60, 0) }
    }
}
