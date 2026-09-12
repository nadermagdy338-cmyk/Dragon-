package nd.max.core.hardware

/**
 * Frequency controls for per-app profiles. Requests are capability-driven and
 * are never silently substituted with an unsupported value.
 */
object PerAppFrequencyController {
    data class Result(
        val requested: String,
        val applied: Boolean,
        val verified: Boolean,
        val actual: String? = null,
        val error: String? = null,
    )

    fun applyCpuLimits(minKHz: Long?, maxKHz: Long?): Result {
        if (minKHz == null && maxKHz == null) return Result("default", true, true, "default")
        val r = CpuHardwareBackend.setLimits(minKHz, maxKHz)
        return Result(r.requested.toString(), r.writeSucceeded, r.successful, r.actual, r.error)
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

        val request = if (device.rangeWritable) {
            GpuHardwareBackend.Request(minFreq = low, maxFreq = target)
        } else {
            GpuHardwareBackend.Request(minFreq = target, maxFreq = target)
        }
        val result = GpuHardwareBackend.apply(device, request)
        return Result(
            requested = maxHz.toString(),
            applied = result.writeSucceeded,
            verified = result.verified,
            actual = result.actual?.maxFreq?.toString(),
            error = result.error,
        )
    }
}
