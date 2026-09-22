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
        val liveCeilingHz: Long? = null,
    )

    fun findGpuNode(): String? = GpuHardwareBackend.selection().device?.path

    /**
     * قدرة GPU **المُعلنة** للواجهة — كتالوج لا سقف.
     *
     * والفرق ليس تفصيلًا: كانت القائمة تُقيَّد بالسقف الحيّ (`configurableMaxFrequency` = الأقل من
     * المُعلن وسقف `max_freq`)، وهيوس على هذا الجهاز تحمل ٧٥٤ في الوضع العادي — فكانت أعلى درجة
     * **معروضة** مساويةً لما عنده أصلًا، ولا سبيل لاختيار ١٣٠٠ من الشاشة أبدًا. وهو المقيس بالحرف:
     * «الخيارات لا تزيد عن الافتراضي». والسقف الحيّ يُعاد منفصلًا ([liveCeilingHz]) ليُقال للمستخدم
     * لا ليُخفى به ما تجلبه قدرة الجهاز: الطلب عند القدرة صار **تحريرًا** لسلطة المصنّع لا كتابة
     * تردد فوقها، وما دون القدرة يُقيَّد بالسقف الحيّ عند التنفيذ (فيُعلَن التقييد بسطر
     * `PERAPP_GPU_TARGET_CAPPED`).
     */
    fun readGpuCapabilities(): GpuCapabilities {
        val device = GpuHardwareBackend.selection().device
            ?: return GpuCapabilities(null, emptyList(), emptyList())
        val advertised = device.frequencies.filter { it > 0L }.distinct().sorted()
        return GpuCapabilities(
            node = device.path,
            governors = device.governors,
            frequencies = advertised,
            liveCeilingHz = GpuHardwareBackend.configurableMaxFrequency(device),
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
     * Power is device-adaptive: it uses the highest frequency currently usable
     * by the GPU's OPP/available-frequency table as the stock/reference maximum,
     * then reduces that value according to the selected profile (Power defaults
     * to 40% of the discovered capability). The result is
     * rounded down to the nearest real OPP so we never request a frequency that
     * the device does not expose.
     *
     * When [maximumHz] is supplied it is treated as a live runtime ceiling, not
     * as a hardware capability claim. The catalogue may still contain higher OPPs.
     * This is deliberately based on frequency, not OPP-list position. OPP tables
     * are not guaranteed to be evenly spaced, and index-based "50%" selection
     * previously produced an unexpectedly high value on Rodin (~780 MHz while
     * its normal maximum is ~752 MHz).
     */
    /**
     * Returns whether a per-app GPU request is asking for the device's full advertised capability.
     *
     * A named profile reaches full capability only at 100%. Lower profiles intentionally stay
     * bounded by the live vendor ceiling. A direct frequency selection reaches full capability
     * only when it explicitly selects the highest advertised OPP.
     */
    fun isFullCapabilityRequest(
        explicitHz: Long?,
        advertisedMaxHz: Long?,
        profilePercent: Int,
    ): Boolean =
        explicitHz?.let { explicit ->
            advertisedMaxHz != null && explicit >= advertisedMaxHz
        } ?: (profilePercent >= 100)

    /**
     * Returns the explicit GPU ceiling only when no named profile owns the same control.
     *
     * The UI contract is one owner: selecting a profile clears the explicit frequency. This
     * runtime guard also heals stale/imported configurations where both survived on disk.
     */
    fun effectiveExplicitGpuCeiling(profile: String, explicitHz: Long?): Long? =
        explicitHz?.takeIf { profile.isBlank() || profile == "default" }

    fun pickProfileFrequency(
        frequencies: List<Long>,
        profile: String,
        customPercent: Int? = null,
        maximumHz: Long? = null,
    ): Long? {
        if (frequencies.isEmpty()) return null
        val normalized = frequencies.filter { it > 0L }.distinct().sorted()
        if (normalized.isEmpty()) return null
        val capped = maximumHz?.takeIf { it > 0L }?.let { cap ->
            normalized.filter { it <= cap }
        }.orEmpty().ifEmpty { normalized }

        // Every profile takes one path: the percentage is applied to the top of [capped], and the
        // nearest real OPP at or below the target is chosen. `power` used to have a second, earlier
        // branch with its own default (40) while a later `"power" -> customPercent ?: 65` was
        // unreachable — two answers for one profile, and the reachable one was undocumented.
        val percent = when (profile.lowercase()) {
            "power" -> customPercent ?: 40
            "balanced" -> customPercent ?: 60
            "gaming" -> customPercent ?: 85
            "performance" -> customPercent ?: 100
            "custom" -> customPercent ?: 55
            else -> return null
        }.coerceIn(20, 100)
        val targetHz = (capped.last() * percent.toLong()) / 100L
        return capped.lastOrNull { it <= targetHz } ?: capped.first()
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
