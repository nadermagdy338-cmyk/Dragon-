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
    )

    fun policies(): List<Policy> = RootFileAccess.listDirectories(ROOT)
        .filter { it.startsWith("policy") }
        .mapNotNull { name ->
            val path = "$ROOT/$name"
            val gov = RootFileAccess.read("$path/scaling_governor")
            val governors = RootFileAccess.read("$path/scaling_available_governors")
                .orEmpty().split(Regex("\\s+")).filter(String::isNotBlank).distinct()
            val min = RootFileAccess.read("$path/scaling_min_freq")?.toLongOrNull()
            val max = RootFileAccess.read("$path/scaling_max_freq")?.toLongOrNull()
            val hwMin = RootFileAccess.read("$path/cpuinfo_min_freq")?.toLongOrNull()
            val hwMax = RootFileAccess.read("$path/cpuinfo_max_freq")?.toLongOrNull()
            if (gov == null && governors.isEmpty() && min == null && max == null) null
            else Policy(path, name, gov, governors, min, max, hwMin, hwMax)
        }
        .sortedBy { it.name }

    fun commonGovernors(): List<String> {
        val sets = policies().map { it.governors.toSet() }.filter(Set<String>::isNotEmpty)
        if (sets.isEmpty()) return emptyList()
        return sets.reduce { a, b -> a.intersect(b) }.sorted()
    }

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

    fun setGovernor(governor: String): VerificationResult<String> {
        val current = policies().filter { governor in it.governors }
        if (current.isEmpty()) return VerificationResult(governor, null, false, false, "unsupported")
        var attempted = 0
        var writes = 0
        current.forEach { policy ->
            attempted++
            if (RootFileAccess.write("${policy.path}/scaling_governor", governor)) writes++
        }
        val live = policies().mapNotNull { it.governor }.distinct()
        val verified = attempted > 0 && writes == attempted && live.size == 1 && live.first() == governor
        return VerificationResult(governor, live.joinToString(","), writes == attempted, verified, if (verified) null else "write-failed")
    }

    /** Apply limits to one cpufreq policy only, preserving heterogeneous policies. */
    fun setPolicyLimits(policyPath: String, minKHz: Long? = null, maxKHz: Long? = null): VerificationResult<String> {
        if (minKHz == null && maxKHz == null) return VerificationResult("", null, false, false, "no-request")
        val policy = policies().firstOrNull { it.path == policyPath }
            ?: return VerificationResult("$minKHz:$maxKHz", null, false, false, "unsupported-policy")
        val hwMin = policy.hwMinKHz ?: policy.minKHz ?: minKHz ?: 0L
        val hwMax = policy.hwMaxKHz ?: policy.maxKHz ?: maxKHz ?: Long.MAX_VALUE
        var targetMin = minKHz?.coerceIn(hwMin, hwMax)
        var targetMax = maxKHz?.coerceIn(hwMin, hwMax)
        if (targetMin != null && targetMax != null && targetMin > targetMax) targetMin = targetMax

        var ok = true
        val currentMin = policy.minKHz
        if (targetMax != null && currentMin != null && targetMax < currentMin) {
            ok = RootFileAccess.write("$policyPath/scaling_max_freq", targetMax.toString()) && ok
        }
        if (targetMin != null) ok = RootFileAccess.write("$policyPath/scaling_min_freq", targetMin.toString()) && ok
        if (targetMax != null && !(currentMin != null && targetMax < currentMin)) {
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

    fun setLimits(minKHz: Long? = null, maxKHz: Long? = null): VerificationResult<String> {
        val all = policies()
        if (all.isEmpty()) return VerificationResult("", null, false, false, "unsupported")
        if (minKHz == null && maxKHz == null) return VerificationResult("", null, false, false, "no-request")

        var writesOk = true
        all.forEach { p ->
            val hwMin = p.hwMinKHz ?: p.minKHz ?: minKHz ?: 0L
            val hwMax = p.hwMaxKHz ?: p.maxKHz ?: maxKHz ?: Long.MAX_VALUE
            var targetMin = minKHz?.coerceIn(hwMin, hwMax)
            var targetMax = maxKHz?.coerceIn(hwMin, hwMax)
            if (targetMin != null && targetMax != null && targetMin > targetMax) {
                // Keep the request valid for kernels that require min <= max.
                targetMin = targetMax
            }
            // Kernels commonly require min <= max at every write. When lowering
            // the ceiling below the current floor, lower max first; when raising
            // the floor above the current ceiling, raise max first.
            val currentMin = p.minKHz
            if (targetMax != null && currentMin != null && targetMax < currentMin) {
                writesOk = RootFileAccess.write("${p.path}/scaling_max_freq", targetMax.toString()) && writesOk
            }
            if (targetMin != null) writesOk = RootFileAccess.write("${p.path}/scaling_min_freq", targetMin.toString()) && writesOk
            if (targetMax != null && !(currentMin != null && targetMax < currentMin)) {
                writesOk = RootFileAccess.write("${p.path}/scaling_max_freq", targetMax.toString()) && writesOk
            }
        }

        val livePolicies = policies()
        val verified = writesOk && livePolicies.isNotEmpty() && livePolicies.all { p ->
            val hwMin = p.hwMinKHz ?: p.minKHz ?: minKHz ?: 0L
            val hwMax = p.hwMaxKHz ?: p.maxKHz ?: maxKHz ?: Long.MAX_VALUE
            var expectedMin = minKHz?.coerceIn(hwMin, hwMax)
            val expectedMax = maxKHz?.coerceIn(hwMin, hwMax)
            if (expectedMin != null && expectedMax != null && expectedMin > expectedMax) expectedMin = expectedMax
            (expectedMin == null || p.minKHz == expectedMin) && (expectedMax == null || p.maxKHz == expectedMax)
        }
        val requested = "${minKHz ?: ""}:${maxKHz ?: ""}"
        val actual = livePolicies.joinToString(",") { "${it.name}=${it.minKHz ?: "?"}-${it.maxKHz ?: "?"}" }
        return VerificationResult(requested, actual, writesOk, verified, if (verified) null else "live limits differ from requested limits")
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
