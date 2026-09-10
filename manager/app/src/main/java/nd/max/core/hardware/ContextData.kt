package nd.max.core.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import nd.max.ui.util.ThermalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * بيانات السياق التي يتم جمعها من الجهاز لتوجيه سياسات الأداء.
 */
data class ContextData(
    val foregroundPackage: String = "",
    val isScreenOn: Boolean = true,
    val ambientLightLux: Float = 0f,
    val audioVolumePercent: Int = 0,
    val isCharging: Boolean = false,
    val batteryLevel: Int = 0,
    val cpuLoadAvg: Float = 0f,
    val thermalZoneMax: Float = 0f
)

/**
 * واجهة لجمع بيانات السياق من مصادر متعددة.
 */
interface ContextDataSource {
    suspend fun getCurrentContext(): ContextData
}

/**
 * تنفيذ فعلي يجمع البيانات من نظام Android وملفات النواة.
 */
class AndroidContextDataSource(
    private val context: Context
) : ContextDataSource {

    override suspend fun getCurrentContext(): ContextData = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val foregroundPkg = runCatching {
            activityManager.getRunningAppProcesses()?.firstOrNull { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }?.processName ?: ""
        }.getOrDefault("")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        var ambientLight = 0f
        if (lightSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                        ambientLight = event.values[0]
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.unregisterListener(listener)
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val volume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        val loadAvg = runCatching {
            java.io.File("/proc/loadavg").readText().split(" ").firstOrNull()?.toFloatOrNull() ?: 0f
        }.getOrDefault(0f)

        val maxTemp = ThermalUtil.readBatteryTemperatureC(context)

        ContextData(
            foregroundPackage = foregroundPkg,
            isScreenOn = isScreenOn,
            ambientLightLux = ambientLight,
            audioVolumePercent = volume,
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            cpuLoadAvg = loadAvg,
            thermalZoneMax = maxTemp
        )
    }
}