package nd.max.ui.util

import android.os.SystemClock
import com.topjohnwu.superuser.Shell
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
        // Populated only when MediaTek's gpufreqv2 (or legacy gpufreq) signed OPP table is
        // available. Maps an exact frequency in Hz to the OPP index that
        // fix_target_opp_index / the legacy fixed-index node expects. See
        // applyGpuFixedFrequency() below for why this takes priority over the devfreq node
        // whenever it's present.
        val mtkOppIndexByFreqHz: Map<Long, String> = emptyMap()
    )

    private fun shell(command: String): List<String> = runCatching {
        Shell.cmd(command).exec().out
    }.getOrDefault(emptyList())

    fun findGpuNode(): String? {
        val candidates = shell(
            "for d in /sys/class/kgsl/kgsl-3d0/devfreq /sys/class/devfreq/*.mali /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali*; do " +
            "[ -d \"\$d\" ] || continue; " +
            "[ -f \"\$d/governor\" ] || continue; echo \"\$d\"; break; done"
        )
        return candidates.firstOrNull { it.isNotBlank() }?.trim()
    }

    private const val GPU_CAPABILITY_CACHE_MS = 10_000L

    @Volatile
    private var gpuCapabilitiesCache: GpuCapabilities? = null

    @Volatile
    private var gpuCapabilitiesCachedAt = 0L

    @Synchronized
    fun invalidateGpuCapabilitiesCache() {
        gpuCapabilitiesCache = null
        gpuCapabilitiesCachedAt = 0L
    }

    fun readGpuCapabilities(forceRefresh: Boolean = false): GpuCapabilities {
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh) {
            gpuCapabilitiesCache?.let { cached ->
                if (now - gpuCapabilitiesCachedAt < GPU_CAPABILITY_CACHE_MS) return cached
            }
        }

        val node = findGpuNode()
        val governors = if (node != null) {
            shell("cat '$node/available_governors' 2>/dev/null")
                .flatMap { it.trim().split(Regex("\\s+")) }
                .filter { it.isNotBlank() }
                .distinct()
        } else emptyList()

        // MediaTek gpufreqv2 (or the older /proc/gpufreq) exposes the signed OPP table that
        // MediaTek's own GED driver actually enforces. On these chips the devfreq node above
        // is very often just a reporting/thermal-cooling shim: its "governor" reads back as
        // "dummy" and writing governor/min_freq/max_freq there has no real effect, because
        // GED drives the hardware out-of-band and never consults those files. When this
        // table is present it's the authoritative frequency list, and
        // applyGpuFixedFrequency() locks against it directly instead of the (frequently
        // inert) devfreq node.
        val mtkMap = runCatching { MtkUtils.getMtkFreqMap() }.getOrDefault(emptyMap())
        if (mtkMap.isNotEmpty()) {
            val byFreqHz = mtkMap.entries
                .mapNotNull { (khzStr, idx) -> khzStr.trim().toLongOrNull()?.let { (it * 1000) to idx } }
                .toMap()
            if (byFreqHz.isNotEmpty()) {
                return GpuCapabilities(node, governors, byFreqHz.keys.sorted(), byFreqHz).also {
                    gpuCapabilitiesCache = it
                    gpuCapabilitiesCachedAt = now
                }
            }
        }

        if (node == null) return GpuCapabilities(null, emptyList(), emptyList()).also {
            gpuCapabilitiesCache = it
            gpuCapabilitiesCachedAt = now
        }
        val frequencies = shell("cat '$node/available_frequencies' 2>/dev/null")
            .flatMap { it.trim().split(Regex("\\s+")) }
            .mapNotNull { it.toLongOrNull() }
            .distinct()
            .sorted()
        return GpuCapabilities(node, governors, frequencies).also {
            gpuCapabilitiesCache = it
            gpuCapabilitiesCachedAt = now
        }
    }

    /** Intersection: a governor is offered only if every CPU policy supports it. */
    fun readCpuGovernors(): List<String> {
        val policies = shell("ls -d /sys/devices/system/cpu/cpufreq/policy* 2>/dev/null")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (policies.isEmpty()) return emptyList()

        val perPolicy = policies.map { policy ->
            shell("cat '$policy/scaling_available_governors' 2>/dev/null")
                .flatMap { it.trim().split(Regex("\\s+")) }
                .filter { it.isNotBlank() }
                .toSet()
        }
        if (perPolicy.any { it.isEmpty() }) return emptyList()
        return perPolicy.reduce { acc, set -> acc.intersect(set) }.sorted()
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

    /** Returns the live MediaTek fixed-OPP lock, normalized to an OPP index. */
    fun currentMtkGpuLockIndex(): String? {
        if (!MtkUtils.isMtkV2() && !MtkUtils.isMtkLegacy()) return null
        val raw = runCatching { MtkUtils.readData(MtkUtils.getFixedIndexPath()) }.getOrDefault("")
        return MtkUtils.parseMtkIndex(raw).takeIf { it != "-1" }
    }

    /** Returns the OPP index expected for an exact frequency, when MTK exposes its table. */
    fun mtkOppIndexForFrequency(caps: GpuCapabilities, frequencyHz: Long): String? =
        caps.mtkOppIndexByFreqHz[frequencyHz]

    fun applyGpuGovernor(node: String?, governor: String): Boolean {
        if (node.isNullOrBlank() || governor == "default") return true
        val device = GpuHardwareBackend.devices().firstOrNull { it.path == node }
            ?: return false
        return GpuHardwareBackend.setGovernor(device, governor).successful
    }

    fun applyCpuGovernor(governor: String): Boolean {
        if (governor == "default") return true
        return CpuHardwareBackend.setGovernor(governor).successful
    }

    /**
     * Locks the GPU to one exact, device-supported frequency (not just a soft ceiling).
     *
     * Two mechanisms are tried, in order:
     *
     * 1. MediaTek gpufreqv2 / legacy gpufreq OPP-index lock (authoritative on MTK chips).
     *    [caps] already carries the frequency -> OPP-index map when this path exists (see
     *    readGpuCapabilities()); the matching index is written straight to
     *    fix_target_opp_index (or the legacy equivalent) via MtkUtils.lockGpuFreq(). This is
     *    the mechanism MediaTek's own driver actually enforces: writing
     *    governor/min_freq/max_freq to the devfreq node on these chips is frequently a
     *    no-op, because GED bypasses it entirely.
     *
     * 2. Standard Linux devfreq "userspace" governor (Adreno kgsl-3d0, generic GKI GPUs).
     *    An exact frequency can only be pinned this way through the "userspace" governor,
     *    which exposes a writable "userspace/set_freq" attribute once it's active - the GPU
     *    equivalent of scaling_governor=userspace + scaling_setspeed on the CPU side.
     *    min_freq/max_freq are also pinned to the same value, both as a safety clamp and as
     *    the fallback lock for devices that don't expose a "userspace" governor at all.
     */
    fun applyGpuFixedFrequency(node: String?, caps: GpuCapabilities, frequencyHz: Long): Boolean {
        val mtkIndex = caps.mtkOppIndexByFreqHz[frequencyHz]
        if (mtkIndex != null) {
            val locked = MtkUtils.lockGpuFreq(mtkIndex)
            // Best-effort mirror onto the devfreq node too, in case anything else on this
            // ROM (thermal cooling, etc.) reads its min/max instead of the MTK proc nodes.
            if (!node.isNullOrBlank()) {
                shell("echo '$frequencyHz' > '$node/max_freq' 2>/dev/null")
            }
            return locked
        }

        if (node.isNullOrBlank() || frequencyHz !in caps.frequencies) return false

        val minPath = "$node/min_freq"
        val maxPath = "$node/max_freq"
        // Widen the clamp to the device's full range first so the exact value below can't
        // be rejected for sitting outside a stale min/max window left by a previous app.
        shell("echo '${caps.frequencies.first()}' > '$minPath' 2>/dev/null")
        shell("echo '${caps.frequencies.last()}' > '$maxPath' 2>/dev/null")
        shell("echo '$frequencyHz' > '$minPath' 2>/dev/null")
        shell("echo '$frequencyHz' > '$maxPath' 2>/dev/null")

        if ("userspace" in caps.governors) {
            Shell.cmd("echo 'userspace' > '$node/governor' 2>/dev/null").exec()
            // This attribute only shows up once the userspace governor is actually active.
            val setFreqPath = "$node/userspace/set_freq"
            val hasSetFreq = shell("[ -f '$setFreqPath' ] && echo 1").firstOrNull() == "1"
            if (hasSetFreq) {
                return Shell.cmd("echo '$frequencyHz' > '$setFreqPath' 2>/dev/null").exec().isSuccess
            }
        }
        // No writable userspace/set_freq (or "userspace" unsupported): the min==max clamp
        // above is the best available lock for whatever governor stays active.
        return true
    }

    /**
     * Releases a lock previously applied by [applyGpuFixedFrequency]. Devfreq
     * governor/min_freq/max_freq are restored separately by the caller from its own
     * pre-override snapshot; this only needs to undo the MTK OPP-index lock, since leaving
     * that in place would pin the GPU at that frequency indefinitely after the app closes.
     */
    fun releaseGpuFixedFrequency() {
        if (MtkUtils.isMtkV2() || MtkUtils.isMtkLegacy()) {
            runCatching { MtkUtils.resetGpuLock() }
        }
    }
}
