package nd.max.core.hardware

import android.content.Context
import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot
import nd.max.core.maxai.SafetyGovernor
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safety predictor that fails safely and only uses existing HardwareControlArbiter.
 */
@Singleton
class PredictiveSafety @Inject constructor(
    private val context: Context,
    private val arbiter: HardwareControlArbiter,
    private val safetyGovernor: SafetyGovernor? = null // Optional, to avoid hard dependency
) {
    private val lastThermalSpike = AtomicLong(0)
    private val thermalHistory = mutableListOf<Float>()

    /**
     * Predict a thermal spike, fails safely if any error occurs.
     */
    fun predictThermalSpike(currentThermalC: Float, deviceState: DeviceSnapshot): Boolean {
        return try {
            // Reject obviously invalid values
            if (currentThermalC < -20f || currentThermalC > 150f) {
                return false
            }

            thermalHistory.add(currentThermalC)
            if (thermalHistory.size > 50) {
                thermalHistory.removeFirst()
            }

            // Not enough data yet
            if (thermalHistory.size < 10) {
                return false
            }

            // Calculate thermal gradient
            val last10 = thermalHistory.takeLast(10)
            val gradient = (last10.last() - last10.first()) / 10f

            // Reject invalid gradients
            if (gradient < -2f || gradient > 5f) {
                return false
            }

            // Predict if we'll hit the limit soon
            val thermalLimit = 80f
            val predictedThermal = currentThermalC + (gradient * 0.8f)
            if (predictedThermal >= thermalLimit) {
                val now = System.currentTimeMillis()
                val lastSpike = lastThermalSpike.get()
                if (now - lastSpike > 2000L) { // At most one spike prediction every 2 seconds
                    lastThermalSpike.set(now)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Throwable) {
            false // Fail safely
        }
    }

    /**
     * Get the current thermal history for debugging.
     */
    fun getThermalHistory(): List<Float> = thermalHistory.toList()
}
