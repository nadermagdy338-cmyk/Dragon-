package nd.max.core.hardware

/** Generic cpufreq backend. It discovers policy nodes instead of assuming a CPU layout. */
object CpuHardwareBackend {
    private const val ROOT = "/sys/devices/system/cpu/cpufreq"

    data class Policy(
        val path: String,
        val name: String,
        val governor: String?,
        val governors: List<String>,
        val minKHz: Long?,
        val maxKHz: Long?,
        val hwMinKHz: Long?,
        val hwMaxKHz: Long?,
        val availableFrequenciesKHz: List<Long>,
    ) {
        val provenMinKHz: Long? get() = hwMinKHz ?: availableFrequenciesKHz.firstOrNull()
        val provenMaxKHz: Long? get() = hwMaxKHz ?: availableFrequenciesKHz.lastOrNull()
    }

    private fun policyFrequencies(path: String): List<Long> {
        val advertised = RootFileAccess.read("$path/scaling_available_frequencies")
            .orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull(String::toLongOrNull)
        if (advertised.isNotEmpty()) return advertised.distinct().sorted()

        // A number of modern kernels omit scaling_available_frequencies while
        // retaining the cpufreq statistics table. It is still a kernel-reported
        // OPP list, unlike a guessed range, so it is safe to expose in the UI.
        return RootFileAccess.read("$path/stats/time_in_state")
            .orEmpty()
            .lineSequence()
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() }
            .filter { it > 0L }
            .distinct()
            .sorted()
    }

    private fun readPolicy(name: String, path: String): Policy? {
        val gov = RootFileAccess.read("$path/scaling_governor")
        val governors = RootFileAccess.read("$path/scaling_available_governors")
            .orEmpty().split(Regex("\\s+")).filter(String::isNotBlank).distinct()
        val min = RootFileAccess.read("$path/scaling_min_freq")?.toLongOrNull()
        val max = RootFileAccess.read("$path/scaling_max_freq")?.toLongOrNull()
        val hwMin = RootFileAccess.read("$path/cpuinfo_min_freq")?.toLongOrNull()
        val hwMax = RootFileAccess.read("$path/cpuinfo_max_freq")?.toLongOrNull()
        val availableFrequencies = policyFrequencies(path)
        return if (gov == null && governors.isEmpty() && min == null && max == null) null
        else Policy(path, name, gov, governors, min, max, hwMin, hwMax, availableFrequencies)
    }

    /**
     * policy* is the canonical cpufreq API. Legacy rooted kernels may expose
     * only cpuN/cpufreq, which we use only when policy directories are absent
     * to avoid aliasing the same hardware policy twice.
     */
    fun policies(): List<Policy> {
        val policyNodes = RootFileAccess.listDirectories(ROOT)
            .filter { it.matches(Regex("policy\\d+")) }
            .mapNotNull { name -> readPolicy(name, "$ROOT/$name") }
            .sortedBy { it.name }
        if (policyNodes.isNotEmpty()) return policyNodes

        val legacyPolicies = RootFileAccess.listDirectories("/sys/devices/system/cpu")
            .filter { it.matches(Regex("cpu\\d+")) }
            .mapNotNull { cpu -> readPolicy(cpu, "/sys/devices/system/cpu/$cpu/cpufreq") }
        // Older kernels often expose one cpuN/cpufreq symlink per core. Group
        // aliases by related_cpus/affected_cpus so one hardware policy is never
        // presented or written multiple times.
        return legacyPolicies
            .groupBy { policy ->
                RootFileAccess.read("${policy.path}/related_cpus")
                    ?: RootFileAccess.read("${policy.path}/affected_cpus")
                    ?: policy.path
            }
            .values
            .map { aliases -> aliases.minBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE } }
            .sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
    }

    fun commonGovernors(): List<String> {
        val sets = policies().map { it.governors.toSet() }.filter(Set<String>::isNotEmpty)
        if (sets.isEmpty()) return emptyList()
        return sets.reduce { a, b -> a.intersect(b) }.sorted()
    }

    /** Reads a policy's live clock, which may differ from its configured limits. */
    fun readCurrentFrequencyKHz(policyPath: String): Long? =
        RootFileAccess.read("$policyPath/scaling_cur_freq")?.toLongOrNull()
            ?: RootFileAccess.read("$policyPath/cpuinfo_cur_freq")?.toLongOrNull()

    /** Apply a governor to exactly one policy, preserving heterogeneous policy setups. */
    fun setPolicyGovernor(policyPath: String, governor: String): VerificationResult<String> {
        val policy = policies().firstOrNull { it.path == policyPath }
            ?: return VerificationResult(governor, null, false, false, "unsupported-policy")
        if (policy.governors.isNotEmpty() && governor !in policy.governors) {
            return VerificationResult(governor, policy.governor, false, false, "unsupported-governor")
        }
        val written = RootFileAccess.write("$policyPath/scaling_governor", governor)
        val actual = policies().firstOrNull { it.path == policyPath }?.governor
        return VerificationResult(
            governor, actual, written, written && actual == governor,
            if (written && actual == governor) null else "live governor differs"
        )
    }

    /**
     * Applies a global governor only when every discovered policy supports it.
     * A partial cluster change is worse than a rejected request, so rollback to
     * each live baseline if a write or final verification fails.
     */
    fun setGovernor(governor: String): VerificationResult<String> {
        val all = policies()
        if (all.isEmpty() || all.any { governor !in it.governors }) {
            return VerificationResult(governor, null, false, false, "unsupported-by-all-policies")
        }
        val baseline = all.associate { it.path to it.governor }
        var writesOk = true
        for (policy in all) {
            if (!RootFileAccess.write("${policy.path}/scaling_governor", governor)) {
                writesOk = false
                break
            }
        }
        val live = policies()
        val verified = writesOk && live.size == all.size && live.all { it.governor == governor }
        var rollbackVerified = true
        val actualPolicies = if (!verified) {
            baseline.forEach { (path, previous) ->
                if (!previous.isNullOrBlank()) {
                    val restored = RootFileAccess.write("$path/scaling_governor", previous) &&
                        policies().firstOrNull { it.path == path }?.governor == previous
                    rollbackVerified = rollbackVerified && restored
                }
            }
            policies()
        } else live
        val actual = actualPolicies.joinToString(",") { "${it.name}=${it.governor ?: "?"}" }
        return VerificationResult(
            governor,
            actual,
            writesOk,
            verified,
            if (verified) null else if (rollbackVerified) "live governor differed; restored baseline" else "live governor differed; rollback failed"
        )
    }

    /** Apply limits to one cpufreq policy only, preserving heterogeneous policies. */
    fun setPolicyLimits(policyPath: String, minKHz: Long? = null, maxKHz: Long? = null): VerificationResult<String> {
        if (minKHz == null && maxKHz == null) return VerificationResult("", null, false, false, "no-request")
        val policy = policies().firstOrNull { it.path == policyPath }
            ?: return VerificationResult("$minKHz:$maxKHz", null, false, false, "unsupported-policy")
        val hwMin = policy.provenMinKHz
            ?: return VerificationResult("$minKHz:$maxKHz", null, false, false, "unknown-hardware-bounds")
        val hwMax = policy.provenMaxKHz
            ?: return VerificationResult("$minKHz:$maxKHz", null, false, false, "unknown-hardware-bounds")
        var targetMin = minKHz?.coerceIn(hwMin, hwMax)
        var targetMax = maxKHz?.coerceIn(hwMin, hwMax)
        if (targetMin != null && targetMax != null && targetMin > targetMax) targetMin = targetMax

        var ok = true
        val currentMax = policy.maxKHz
        // Keep min <= max after every write. When raising the floor above the
        // current ceiling, raise max first; otherwise establish min first.
        val mustRaiseMaxFirst = targetMin != null && currentMax != null && targetMin > currentMax && targetMax != null
        if (mustRaiseMaxFirst) {
            ok = RootFileAccess.write("$policyPath/scaling_max_freq", targetMax.toString()) && ok
        }
        if (targetMin != null) ok = RootFileAccess.write("$policyPath/scaling_min_freq", targetMin.toString()) && ok
        if (targetMax != null && !mustRaiseMaxFirst) {
            ok = RootFileAccess.write("$policyPath/scaling_max_freq", targetMax.toString()) && ok
        }
        val live = policies().firstOrNull { it.path == policyPath }
        val verified = ok && live != null &&
            (targetMin == null || live.minKHz == targetMin) &&
            (targetMax == null || live.maxKHz == targetMax)
        return VerificationResult(
            requested = "${minKHz ?: ""}:${maxKHz ?: ""}",
            actual = "${live?.minKHz ?: "?"}:${live?.maxKHz ?: "?"}",
            writeSucceeded = ok,
            verified = verified,
            error = if (verified) null else "live policy limits differ",
        )
    }

    /**
     * Applies identical global limits only when every policy has proven hardware
     * bounds. A partial application is rolled back to the captured live range.
     * Per-app policy control should prefer [setPolicyLimits] instead.
     */
    fun setLimits(minKHz: Long? = null, maxKHz: Long? = null): VerificationResult<String> {
        val all = policies()
        if (all.isEmpty()) return VerificationResult("", null, false, false, "unsupported")
        if (minKHz == null && maxKHz == null) return VerificationResult("", null, false, false, "no-request")
        if (all.any { it.provenMinKHz == null || it.provenMaxKHz == null }) {
            return VerificationResult("${minKHz ?: ""}:${maxKHz ?: ""}", null, false, false, "unknown-hardware-bounds")
        }
        val baseline = all.associate { it.path to (it.minKHz to it.maxKHz) }
        val results = mutableListOf<VerificationResult<String>>()
        for (policy in all) {
            val result = setPolicyLimits(policy.path, minKHz, maxKHz)
            results += result
            if (!result.verified) break
        }
        val verified = results.size == all.size && results.all { it.verified }
        var rollbackVerified = true
        if (!verified) {
            baseline.forEach { (path, range) ->
                if (range.first != null || range.second != null) {
                    rollbackVerified = setPolicyLimits(path, range.first, range.second).verified && rollbackVerified
                }
            }
        }
        val live = policies()
        val requested = "${minKHz ?: ""}:${maxKHz ?: ""}"
        val actual = live.joinToString(",") { "${it.name}=${it.minKHz ?: "?"}-${it.maxKHz ?: "?"}" }
        return VerificationResult(
            requested = requested,
            actual = actual,
            writeSucceeded = results.size == all.size && results.all { it.writeSucceeded },
            verified = verified,
            error = if (verified) null else if (rollbackVerified) "one or more policy limits were not verified; restored baseline" else "one or more policy limits were not verified; rollback failed",
        )
    }

    fun boostNode(): String? = listOf(
        "$ROOT/boost",
        "/sys/devices/system/cpu/cpufreq/boost",
    ).firstOrNull(RootFileAccess::exists)

    fun setBoost(enabled: Boolean): VerificationResult<String> {
        val node = boostNode() ?: return VerificationResult(if (enabled) "1" else "0", null, false, false, "unsupported")
        val requested = if (enabled) "1" else "0"
        val written = RootFileAccess.write(node, requested)
        val actual = RootFileAccess.read(node)?.trim()
        val verified = written && actual == requested
        return VerificationResult(requested, actual, written, verified, if (verified) null else "live boost state differs")
    }

    fun setCoreOnline(cpu: Int, online: Boolean): Boolean {
        if (cpu < 0) return false
        val path = "/sys/devices/system/cpu/cpu$cpu/online"
        if (!RootFileAccess.exists(path)) return false
        if (!RootFileAccess.write(path, if (online) "1" else "0")) return false
        return RootFileAccess.read(path)?.trim() == if (online) "1" else "0"
    }
}
