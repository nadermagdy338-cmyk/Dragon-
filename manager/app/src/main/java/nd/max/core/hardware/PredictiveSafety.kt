package nd.max.core.hardware

import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safety predictor that fails safely and only uses existing HardwareControlArbiter.
 *
 * This is not mandatory: if SafetyGovernor isn't available (for testing), it just does nothing.
 */
@Singleton
class PredictiveSafety @Inject constructor() {
    private val lastThermalSpike = AtomicLong(0L)
    private val thermalHistory = mutableListOf<Float>()

    /**
     * Predict a thermal spike. Fails safely if any invalid data/assumptions are detected.
     *
     * @param currentThermalC Current measured thermal (°C)
     * @return true if a spike is predicted, false otherwise (always false on any error)
     */
    fun predictThermalSpike(currentThermalC: Float, deviceState: DeviceSnapshot): Boolean {
        return try {
            // Reject obviously invalid thermal values (sensor fault)
            if (currentThermalC < -20f || currentThermalC > 150f) {
                return false
            }

            thermalHistory.add(currentThermalC)
            if (thermalHistory.size > 50) {
                // `removeAt(0)` لا `removeFirst()`: الثانية على `MutableList` تُحلّ إلى
                // `java.util.List#removeFirst` (Java 21 → API 35)، فترمي `NoSuchMethodError` على
                // 29–34. والقائمة هنا محدودة بـ50 قياسًا فالتحريك الذي تكلّفه `removeAt` تافه.
                thermalHistory.removeAt(0)
            }

            // Not enough data to predict
            if (thermalHistory.size < 10) return false

            // Calculate thermal gradient
            val last10 = thermalHistory.takeLast(10)
            val gradient = (last10.last() - last10.first()) / 10f

            // Reject invalid/non-physical gradient
            if (gradient < -2f || gradient > 5f) return false

            // Predict if we'll hit the limit soon
            val thermalLimit = 52f
            val predictedThermal = currentThermalC + (gradient * 0.8f) // ~800ms horizon
            if (predictedThermal >= thermalLimit) {
                val now = System.currentTimeMillis()
                val last = lastThermalSpike.get()
                if (now - last > 2000L) { // At most one spike prediction every 2 seconds
                    lastThermalSpike.set(now)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Throwable) {
            false // Fail safely: always assume no spike if any error
        }
    }
}
