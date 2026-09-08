package nd.max.core.hardware

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.topjohnwu.superuser.Shell
import nd.max.ui.util.FpsMonitorUtil
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.getChipsetName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class HardwareDataSource(private val context: Context) {

    data class CpuData(
        val loadPercent: Int,
        val freqMhz: Int,
        val history: List<Float>
    )

    data class MemoryData(
        val usedMb: Int,
        val totalMb: Int
    )

    data class BatteryData(
        val percent: Int,
        val voltage: Float,
        val tempC: Float,
        val isCharging: Boolean,
        val status: String
    )

    data class ThermalData(
        val cpuTempC: Int,
        val gpuTempC: Int,
        val skinTempC: Int
    )

    data class StorageData(
        val usedGb: Float,
        val totalGb: Float
    )

    data class NetworkData(
        val downloadKbps: Long,
        val uploadKbps: Long
    )

    data class DisplayData(
        val width: Int,
        val height: Int,
        val refreshHz: Int,
        val densityDpi: Int
    )

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()

    suspend fun getChipsetName(): String = withContext(Dispatchers.IO) {
        getChipsetName(context)
    }

    suspend fun getCpuData(): CpuData = withContext(Dispatchers.IO) {
        val load = FpsMonitorUtil.getCpuLoad()
        val freq = Shell.cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
            .exec().out.firstOrNull()?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0
        CpuData(load, freq, emptyList())
    }

    suspend fun getCpuDataWithHistory(history: List<Float>): CpuData = withContext(Dispatchers.IO) {
        val load = FpsMonitorUtil.getCpuLoad()
        val freq = Shell.cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
            .exec().out.firstOrNull()?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0
        val updatedHistory = (history + load.toFloat()).takeLast(36)
        CpuData(load, freq, updatedHistory)
    }

    suspend fun getMemoryData(): MemoryData = withContext(Dispatchers.IO) {
        val ram = FpsMonitorUtil.getRamInfo(context)
        MemoryData(ram.usedMb, ram.totalMb)
    }

    suspend fun getBatteryData(): BatteryData = withContext(Dispatchers.IO) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (scale > 0) (level * 100 / scale) else 0
        val voltage = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0).toFloat() / 1000f
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0).toFloat() / 10f
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        BatteryData(percent, voltage, tempC, isCharging, statusText)
    }

    suspend fun getThermalData(): ThermalData = withContext(Dispatchers.IO) {
        val zones = ThermalUtil.readThermalZones()
        var cpu = zones.filter { it.category == "CPU" && it.temperatureC > 0 }
            .maxOfOrNull { it.temperatureC } ?: 0
        var gpu = zones.filter { it.category == "GPU" && it.temperatureC > 0 }
            .maxOfOrNull { it.temperatureC } ?: 0
        var skin = zones.filter { it.category == "Skin" && it.temperatureC > 0 }
            .maxOfOrNull { it.temperatureC } ?: 0

        val service = ThermalUtil.readThermalServiceTemperatures()
        if (cpu == 0) cpu = service[0]
        if (gpu == 0) gpu = service[1]
        if (skin == 0) skin = service[2]

        if (cpu == 0) {
            cpu = zones.asSequence()
                .filter { it.temperatureC > 0 && it.category !in setOf("Battery", "Skin", "Charger", "GPU") }
                .maxOfOrNull { it.temperatureC } ?: 0
        }
        ThermalData(cpu, gpu, skin)
    }

    suspend fun getStorageData(): StorageData = withContext(Dispatchers.IO) {
        val sf = StatFs("/data")
        val total = sf.blockSizeLong * sf.blockCountLong
        val free = sf.blockSizeLong * sf.availableBlocksLong
        StorageData(
            usedGb = (total - free) / 1_073_741_824f,
            totalGb = total / 1_073_741_824f
        )
    }

    suspend fun getNetworkData(): NetworkData = withContext(Dispatchers.IO) {
        val newRx = TrafficStats.getTotalRxBytes()
        val newTx = TrafficStats.getTotalTxBytes()
        val dl = if (newRx > lastRxBytes) (newRx - lastRxBytes) / 2048L else 0L
        val ul = if (newTx > lastTxBytes) (newTx - lastTxBytes) / 2048L else 0L
        lastRxBytes = newRx
        lastTxBytes = newTx
        NetworkData(dl, ul)
    }

    @Suppress("DEPRECATION")
    suspend fun getDisplayData(): DisplayData = withContext(Dispatchers.IO) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        DisplayData(
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            refreshHz = display.refreshRate.toInt(),
            densityDpi = metrics.densityDpi
        )
    }

    suspend fun getFullSnapshot(): Map<String, Any> = withContext(Dispatchers.IO) {
        val chipset = getChipsetName()
        val cpu = getCpuData()
        val memory = getMemoryData()
        val battery = getBatteryData()
        val thermal = getThermalData()
        val storage = getStorageData()
        val network = getNetworkData()
        val display = getDisplayData()
        mapOf(
            "chipset" to chipset,
            "cpuLoad" to cpu.loadPercent,
            "cpuFreq" to cpu.freqMhz,
            "ramUsed" to memory.usedMb,
            "ramTotal" to memory.totalMb,
            "batteryPercent" to battery.percent,
            "batteryVoltage" to battery.voltage,
            "batteryTemp" to battery.tempC,
            "isCharging" to battery.isCharging,
            "batteryStatus" to battery.status,
            "cpuTemp" to thermal.cpuTempC,
            "gpuTemp" to thermal.gpuTempC,
            "skinTemp" to thermal.skinTempC,
            "storageUsed" to storage.usedGb,
            "storageTotal" to storage.totalGb,
            "downloadSpeed" to network.downloadKbps,
            "uploadSpeed" to network.uploadKbps,
            "displayWidth" to display.width,
            "displayHeight" to display.height,
            "displayRefresh" to display.refreshHz,
            "displayDensity" to display.densityDpi
        )
    }
}