package nd.max.core.hardware

import android.content.Context
import kotlinx.coroutines.delay
import nd.max.core.jni.PredictorBridge
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safety predictor — predicts thermal spikes 800ms in advance and
 * implements preventive throttling before it hits the limit.
 *
 * This is the "safety supremacy" layer on top of the arbiter.
 */
@Singleton
class PredictiveSafety @Inject constructor(
    private val context: Context,
    private val safetyGovernor: SafetyGovernor,
) {
    private val lastThermalSpike = AtomicLong(0L)
    private val thermalHistory = mutableListOf<Float>()
    private val lastSensorUpdate = AtomicLong(0L)

    /**
     * Predict a thermal spike 800ms in advance.
     * Fails SAFELY: returns false if any invalid data/assumptions are detected.
     */
    fun predictThermalSpike(currentThermalC: Float, deviceState: DeviceStateCollector.DeviceSnapshot): Boolean {
        val now = System.currentTimeMillis()

        // Check for stale sensor data (sensor didn't update for 2.5s)
        if (lastSensorUpdate.get() > 0L && (now - lastSensorUpdate.get() > 2500L)) {
            return false
        }
        lastSensorUpdate.set(now)

        // Reject obviously invalid thermal values (sensor fault)
        if (currentThermalC < -20f || currentThermalC > 150f) {
            return false
        }

        thermalHistory.add(currentThermalC)
        if (thermalHistory.size > 50) {
            thermalHistory.removeFirst()
        }

        // Not enough data to predict
        if (thermalHistory.size < 10) return false

        // Compute thermal gradient
        val last10 = thermalHistory.takeLast(10)
        val gradient = (last10.last() - last10.first()) / 10f

        // Reject invalid/non-physical gradient
        if (gradient < -2f || gradient > 5f) return false

        // Predict if we'll hit the limit in 800ms
        val predictedThermal = currentThermalC + (gradient * 0.8f)
        val thermalLimit = 80f
        if (predictedThermal >= thermalLimit && gradient > 0.2f) {
            return true
        }

        return false
    }

    /**
     * Apply preventive throttling. Fails SAFELY if anything goes wrong.
     */
    fun applyPreventiveThrottling(deviceState: DeviceStateCollector.DeviceSnapshot) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastThermalSpike.get() < 3000L) return

        try {
            lastThermalSpike.set(currentTime)

            // Apply preventive throttling through SafetyGovernor
            val key = HardwareControlKey.cpuLimits("policy0")
            val control = ControlRegistry.buildSingle(
                key,
                desired = "1000000:1400000"
            )
            val step = MinimalPlanner.Step(
                control = control,
                reason = "predictive safety: thermal spike ahead",
                from = null,
                to = null,
            )

            safetyGovernor.execute(
                step = step,
                appContext = "system",
                token = "predictive-safety",
                readState = { null },
            )
        } catch (t: Throwable) {
            // Fail SAFELY: log and do nothing else
        }
    }
}
