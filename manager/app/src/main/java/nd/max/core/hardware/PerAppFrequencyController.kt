package nd.max.core.hardware

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frequency controls for per-app profiles. Requests are capability-driven and
 * are never silently substituted with an unsupported value.
 */
@Singleton
class PerAppFrequencyController @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {
    data class Result(
        val requested: String,
        val applied: Boolean,
        val verified: Boolean,
        val actual: String? = null,
        val error: String? = null,
    )

    fun applyCpuLimits(policyPath: String, minKHz: Long?, maxKHz: Long?): Result {
        if (minKHz == null && maxKHz == null) {
            return Result("default", true, true, "default")
        }
        val key = HardwareControlKey.cpuLimits(policyPath)
        val token = "per-app-cpu-${System.currentTimeMillis()}"
        val baseline = CpuHardwareBackend.liveLimits(policyPath)
        val desired = "${minKHz ?: ""}:${maxKHz ?: ""}"
        val applyFunc = { value: String ->
            val split = value.split(":")
            val newMin = split[0].toLongOrNull()
            val newMax = split[1].toLongOrNull()
            val r = CpuHardwareBackend.setLimits(newMin, newMax)
            r.successful
        }
        val readFunc = { CpuHardwareBackend.liveLimits(policyPath) }
        val r = arbiter.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = token,
            desired = desired,
            apply = applyFunc,
            read = readFunc,
            baseline = baseline,
            restore = { applyFunc(baseline) },
        )
        return Result(desired, r.applied, r.verified, r.actual, r.error)
    }

    fun applyGpuCeiling(maxHz: Long): Result {
        val selection = GpuHardwareBackend.selection()
        val device = selection.device
            ?: return Result(maxHz.toString(), false, false, error = selection.reason)
        if (device.frequencies.isEmpty()) {
            return Result(maxHz.toString(), false, false, error = "no-advertised-frequency-range")
        }
        val low = device.frequencies.first()
        val target = device.frequencies.lastOrNull { it <= maxHz }
            ?: return Result(maxHz.toString(), false, false, error = "unsupported-frequency")

        val key = HardwareControlKey.gpuFrequency(device.name)
        val token = "per-app-gpu-${System.currentTimeMillis()}"
        val baseline = GpuHardwareBackend.captureBaseline(device)
        val request = if (device.rangeWritable) {
            GpuHardwareBackend.Request(minFreq = low, maxFreq = target)
        } else {
            GpuHardwareBackend.Request(minFreq = target, maxFreq = target)
        }
        val applyFunc = { value: String ->
            val split = value.split(":")
            val newMin = split[0].toLongOrNull()
            val newMax = split[1].toLongOrNull()
            val r = GpuHardwareBackend.apply(
                device,
                GpuHardwareBackend.Request(newMin, newMax, request.governor)
            )
            r.successful
        }
        val readFunc = {
            val current = GpuHardwareBackend.readCurrent(device)
            "${current.minFreq}:${current.maxFreq}"
        }
        val desired = "${request.minFreq}:${request.maxFreq}"
        val r = arbiter.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = token,
            desired = desired,
            apply = applyFunc,
            read = readFunc,
            baseline = "${baseline.minFreq}:${baseline.maxFreq}",
            restore = {
                GpuHardwareBackend.restoreBaseline(baseline)
            },
        )
        return Result(desired, r.applied, r.verified, r.actual, r.error)
    }
}
