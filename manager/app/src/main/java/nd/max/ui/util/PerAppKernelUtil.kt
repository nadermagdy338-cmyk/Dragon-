package nd.max.ui.util

import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.GpuHardwareBackend

/**
 * Runtime capability detection for Per-App CPU/GPU controls.
 * Nothing is hard-coded into the UI: options come from the nodes that exist
 * on the running kernel and only values actually reported by those nodes are shown.
 */
object PerAppKernelUtil {
    data class GpuCapabilities(
        val node: String?,
        val governors: List<String>,
        val frequencies: List<Long>,
    )

    fun findGpuNode(): String? = GpuHardwareBackend.selection().device?.path

    fun readGpuCapabilities(): GpuCapabilities {
        val device = GpuHardwareBackend.selection().device
            ?: return GpuCapabilities(null, emptyList(), emptyList())
        return GpuCapabilities(
            node = device.path,
            governors = device.governors,
            frequencies = device.frequencies,
        )
    }

    /** Intersection: a governor is offered only if every CPU policy supports it. */
    fun readCpuGovernors(): List<String> {
        val all = CpuHardwareBackend.policies()
        if (all.isEmpty() || all.any { it.governors.isEmpty() }) return emptyList()
        return all.map { it.governors.toSet() }
            .reduce { acc, set -> acc.intersect(set) }
            .sorted()
    }

    fun formatFrequency(raw: Long): String = when {
        raw >= 1_000_000 -> String.format("%d MHz", raw / 1_000_000)
        raw >= 1_000 -> String.format("%d MHz", raw / 1_000)
        else -> "$raw"
    }

    /**
     * Picks an actual supported OPP for a profile.
     *
     * Power is device-adaptive: it uses the highest frequency reported by the
     * GPU's own OPP/available-frequency table as the stock/reference maximum,
     * then reduces that value by 35% (65% of stock max remains). The result is
     * rounded down to the nearest real OPP so we never request a frequency that
     * the device does not expose.
     *
     * This is deliberately based on frequency, not OPP-list position. OPP tables
     * are not guaranteed to be evenly spaced, and index-based "50%" selection
     * previously produced an unexpectedly high value on Rodin (~780 MHz while
     * its normal maximum is ~752 MHz).
     */
    fun pickProfileFrequency(frequencies: List<Long>, profile: String, customPercent: Int? = null): Long? {
        if (frequencies.isEmpty()) return null
        val normalized = frequencies.filter { it > 0L }.distinct().sorted()
        if (normalized.isEmpty()) return null

        if (profile.equals("power", true)) {
            val stockMaxHz = normalized.last()
            val targetHz = (stockMaxHz * (customPercent ?: 65).coerceIn(20, 100).toLong()) / 100L

            // Prefer an OPP at or below the target. If the device's lowest OPP
            // is already above the calculated target, use that lowest real OPP.
            return normalized.lastOrNull { it <= targetHz } ?: normalized.first()
        }

        val percent = when (profile.lowercase()) {
            "balanced" -> customPercent ?: 70
            "gaming" -> customPercent ?: 85
            "performance" -> customPercent ?: 100
            "power" -> customPercent ?: 65
            "custom" -> customPercent ?: 55
            else -> return null
        }.coerceIn(20, 100)
        val targetHz = (normalized.last() * percent.toLong()) / 100L
        return normalized.lastOrNull { it <= targetHz } ?: normalized.first()
    }

    /** Backward-compatible percentage picker for other callers. */
    fun pickFrequency(frequencies: List<Long>, percent: Int): Long? {
        if (frequencies.isEmpty()) return null
        val normalized = frequencies.sorted()
        val clamped = percent.coerceIn(0, 100) / 100.0
        val idx = ((normalized.lastIndex) * clamped).toInt().coerceIn(0, normalized.lastIndex)
        return normalized[idx]
    }

    fun applyGpuGovernor(node: String?, governor: String): Boolean {
        if (governor == "default") return true
        val device = node?.let(GpuHardwareBackend::refresh) ?: GpuHardwareBackend.selection().device ?: return false
        return GpuHardwareBackend.setGovernor(device, governor).successful
    }

    fun applyCpuGovernor(governor: String): Boolean {
        if (governor == "default") return true
        return CpuHardwareBackend.setGovernor(governor).successful
    }

    fun releaseGpuFixedFrequency() {
        GpuHardwareBackend.releaseExactLock()
    }

}
