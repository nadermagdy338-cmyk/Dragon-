package nd.max.core.hardware

/**
 * Canonical identities for physical hardware controls.
 *
 * Policy layers must never invent their own key for the same kernel/provider
 * transaction: owner priority is meaningful only when every contender names
 * the physical knob identically.
 */
object HardwareControlKey {
    /**
     * بادئة مفتاح مقبض سياسة cpufreq.
     *
     * وليست خاصة لأن **التوثيق نفسه يحتاجها**: دليل السجل يشرح وحدة `cpu_limits:<policy>`، ولو
     * نسخ البادئة لصار للمقبض الواحد كتابتان — وهو بالضبط ما تمنعه بوابة
     * `ControlPlaneArchitectureTest.canonicalKeysAreNotReinvented`. فالتوثيق يُبنى من هنا أيضًا.
     */
    const val CPU_LIMITS_PREFIX = "cpu_limits:"

    /** بادئة مفتاح مقبض تردد جهاز GPU — عامّة للسبب نفسه. */
    const val GPU_FREQUENCY_PREFIX = "gpu_frequency:"

    fun cpuLimits(policyName: String): String = CPU_LIMITS_PREFIX + policyName
    fun gpuFrequency(deviceName: String): String = GPU_FREQUENCY_PREFIX + deviceName
    const val CPU_BOOST = "cpu_boost"

    /** True when [key] identifies one cpufreq policy's limits knob. */
    fun isCpuLimits(key: String): Boolean = key.startsWith(CPU_LIMITS_PREFIX)

    /** True when [key] identifies one GPU device's frequency knob. */
    fun isGpuFrequency(key: String): Boolean = key.startsWith(GPU_FREQUENCY_PREFIX)

    /** The device name inside a gpu_frequency key; null for any other knob. */
    fun gpuFrequencyDevice(key: String): String? =
        if (isGpuFrequency(key)) key.substring(GPU_FREQUENCY_PREFIX.length).takeIf(String::isNotEmpty) else null

    /** The policy name inside a cpu_limits key; null for any other knob. */
    fun cpuLimitsPolicy(key: String): String? =
        if (isCpuLimits(key)) key.substring(CPU_LIMITS_PREFIX.length).takeIf(String::isNotEmpty) else null
}
