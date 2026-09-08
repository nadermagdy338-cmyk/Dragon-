package nd.max.core.hardware

/** Generic devfreq GPU backend. Vendor-specific paths remain in their existing backends. */
object GpuHardwareBackend {
    private const val ROOT = "/sys/class/devfreq"

    data class Device(
        val path: String,
        val name: String,
        val governor: String?,
        val governors: List<String>,
        val minFreq: Long?,
        val maxFreq: Long?,
        val currentFreq: Long?,
        val frequencies: List<Long>,
    )

    fun devices(): List<Device> = RootFileAccess.listDirectories(ROOT)
        .filter { name ->
            name.contains("gpu", true) || name.contains("mali", true) || name.contains("kgsl", true) ||
                RootFileAccess.exists("$ROOT/$name/governor")
        }
        .mapNotNull { name ->
            val path = "$ROOT/$name"
            val gov = RootFileAccess.read("$path/governor")
            val governors = RootFileAccess.read("$path/available_governors").orEmpty()
                .split(Regex("\\s+")).filter(String::isNotBlank).distinct()
            val freqs = RootFileAccess.read("$path/available_frequencies").orEmpty()
                .split(Regex("\\s+")).mapNotNull(String::toLongOrNull).distinct().sorted()
            if (gov == null && governors.isEmpty() && freqs.isEmpty()) null
            else Device(
                path, name, gov, governors,
                RootFileAccess.read("$path/min_freq")?.toLongOrNull(),
                RootFileAccess.read("$path/max_freq")?.toLongOrNull(),
                RootFileAccess.read("$path/cur_freq")?.toLongOrNull(), freqs
            )
        }

    fun setGovernor(device: Device, governor: String): VerificationResult<String> {
        if (governor !in device.governors) return VerificationResult(governor, null, false, false, "unsupported")
        val written = RootFileAccess.write("${device.path}/governor", governor)
        val live = RootFileAccess.read("${device.path}/governor")
        return VerificationResult(governor, live, written, written && live.equals(governor, true), if (written) null else "write-failed")
    }

    fun clamp(device: Device, frequency: Long): VerificationResult<Long> {
        if (device.frequencies.isNotEmpty() && frequency !in device.frequencies) {
            return VerificationResult(frequency, null, false, false, "unsupported-frequency")
        }
        val min = "${device.path}/min_freq"
        val max = "${device.path}/max_freq"
        val oldMin = RootFileAccess.read(min)?.toLongOrNull()
        val oldMax = RootFileAccess.read(max)?.toLongOrNull()
        val low = device.frequencies.firstOrNull() ?: oldMin ?: frequency
        val high = device.frequencies.lastOrNull() ?: oldMax ?: frequency
        RootFileAccess.write(min, low.toString())
        RootFileAccess.write(max, high.toString())
        val a = RootFileAccess.write(min, frequency.toString())
        val b = RootFileAccess.write(max, frequency.toString())
        val liveMin = RootFileAccess.read(min)?.toLongOrNull()
        val liveMax = RootFileAccess.read(max)?.toLongOrNull()
        val verified = a && b && liveMin == frequency && liveMax == frequency
        return VerificationResult(frequency, liveMax, a && b, verified, if (verified) null else "rejected-or-readback-mismatch")
    }
}
