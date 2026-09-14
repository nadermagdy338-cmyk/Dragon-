package nd.max.core.hardware

/**
 * Canonical identities for physical hardware controls.
 *
 * Policy layers must never invent their own key for the same kernel/provider
 * transaction: owner priority is meaningful only when every contender names
 * the physical knob identically.
 */
object HardwareControlKey {
    private const val CPU_LIMITS_PREFIX = "cpu_limits:"
    private const val GPU_FREQUENCY_PREFIX = "gpu_frequency:"

    fun cpuLimits(policyName: String): String = CPU_LIMITS_PREFIX + policyName
    fun gpuFrequency(deviceName: String): String = GPU_FREQUENCY_PREFIX + deviceName
    const val CPU_BOOST = "cpu_boost"

    /** True when [key] identifies one cpufreq policy's limits knob. */
    fun isCpuLimits(key: String): Boolean = key.startsWith(CPU_LIMITS_PREFIX)

    /** The policy name inside a cpu_limits key; null for any other knob. */
    fun cpuLimitsPolicy(key: String): String? =
        if (isCpuLimits(key)) key.substring(CPU_LIMITS_PREFIX.length).takeIf(String::isNotEmpty) else null
}
