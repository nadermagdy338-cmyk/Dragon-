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
        val device = GpuHardwareBackend.devices().firstOrNull()
            ?: return Result(maxHz.toString(), false, false, error = "unsupported")
        val high = device.frequencies.lastOrNull() ?: device.maxFreq
            ?: return Result(maxHz.toString(), false, false, error = "no-frequency-range")
        val low = device.frequencies.firstOrNull() ?: device.minFreq ?: 0L
        val target = maxHz.coerceIn(low, high)
        if (target <= 0L) return Result(maxHz.toString(), false, false, error = "invalid-frequency")

        val minPath = "${device.path}/min_freq"
        val maxPath = "${device.path}/max_freq"
        if (!RootFileAccess.exists(maxPath)) return Result(maxHz.toString(), false, false, error = "max_freq-unavailable")

        // A kernel may reject max_freq below the current floor. Lower the floor
        // first only when necessary; the per-app restore path owns the baseline.
        if ((RootFileAccess.read(minPath)?.toLongOrNull() ?: low) > target && RootFileAccess.exists(minPath)) {
            RootFileAccess.write(minPath, low.toString())
        }

        val result = VerifiedControl.apply(
            requested = target,
            write = { RootFileAccess.write(maxPath, it.toString()) },
            read = { RootFileAccess.read(maxPath)?.toLongOrNull() },
            equals = { expected, actual -> actual == expected },
        )
        return Result(maxHz.toString(), result.writeSucceeded, result.successful, result.actual?.toString(), result.error)
    }
}
