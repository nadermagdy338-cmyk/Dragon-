package nd.max.core.hardware

import nd.max.core.jni.ProbeBridge

/** Idempotent ZRAM backend with safety checks and live verification. */
object ZramHardwareBackend {
    private const val DEFAULT_ROOT = "/sys/block/zram0"

    data class State(
        val exists: Boolean,
        val sizeBytes: Long?,
        val algorithm: String?,
        val algorithms: List<String>,
        val activeSwap: Boolean = false,
        val devicePath: String = DEFAULT_ROOT,
    )

    private fun deviceRoot(): String = RootFileAccess.globDirectories("/sys/block/zram*")
        .sortedWith(compareBy<String> { it.removePrefix("/sys/block/zram").toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
        .firstOrNull()
        ?: DEFAULT_ROOT

    fun readState(): State {
        val root = deviceRoot()
        val exists = RootFileAccess.exists(root)
        if (!exists) return State(false, null, null, emptyList(), false, root)
        val raw = RootFileAccess.read("$root/comp_algorithm").orEmpty()
        val algorithms = raw.replace("[", " ").replace("]", " ")
            .split(Regex("\\s+")).filter(String::isNotBlank)
        val algorithm = Regex("\\[(.*?)]").find(raw)?.groupValues?.getOrNull(1)
            ?: algorithms.firstOrNull()
        return State(
            exists = true,
            sizeBytes = RootFileAccess.read("$root/disksize")?.toLongOrNull(),
            algorithm = algorithm,
            algorithms = algorithms.distinct(),
            activeSwap = isActiveSwap(root),
            devicePath = root,
        )
    }

    fun setAlgorithm(algorithm: String): VerificationResult<String> {
        val state = readState()
        val root = state.devicePath
        if (!state.exists || algorithm !in state.algorithms) return VerificationResult(algorithm, null, false, false, "unsupported")
        if (state.activeSwap) return VerificationResult(algorithm, state.algorithm, false, false, "active-swap-requires-reset")
        return VerifiedControl.apply(
            requested = algorithm,
            write = { RootFileAccess.write("$root/comp_algorithm", it) },
            read = { Regex("\\[(.*?)]").find(RootFileAccess.read("$root/comp_algorithm").orEmpty())?.groupValues?.getOrNull(1) },
            equals = { expected, actual -> expected == actual },
        )
    }

    fun setSizeBytes(sizeBytes: Long, compressionAlgorithm: String? = null): VerificationResult<Long> {
        val root = readState().devicePath
        if (sizeBytes < 0L || !RootFileAccess.exists("$root/disksize")) {
            return VerificationResult(sizeBytes, null, false, false, "unsupported")
        }
        val before = readState()
        if (sizeBytes == (before.sizeBytes ?: -1L)) {
            return VerificationResult(sizeBytes, before.sizeBytes, true, true, null)
        }
        val algo = compressionAlgorithm?.takeIf { it in before.algorithms } ?: before.algorithm
        val commands = mutableListOf("swapoff $root 2>/dev/null", "echo 1 > $root/reset 2>/dev/null")
        if (!algo.isNullOrBlank()) commands += "echo '$algo' > $root/comp_algorithm 2>/dev/null"
        commands += "echo $sizeBytes > $root/disksize 2>/dev/null"
        commands += "mkswap $root 2>/dev/null"
        if (sizeBytes > 0L) commands += "swapon $root 2>/dev/null"
        val result = com.topjohnwu.superuser.Shell.cmd(*commands.toTypedArray()).exec()
        val live = readState().sizeBytes
        return VerificationResult(sizeBytes, live, result.isSuccess, result.isSuccess && live == sizeBytes,
            if (result.isSuccess && live == sizeBytes) null else "zram-size-readback-mismatch")
    }

    /**
     * هل هذا الجهاز مشارَك كمبادلة؟
     *
     * `/proc/swaps` **عالمية القراءة**، فالسؤال «هل مسار هذا الجهاز في العمود الأول؟» لا
     * يحتاج ولادة عملية (`awk`): تُقرأ داخل العملية في نداء واحد، ويُشطر السطر الأول
     * (العنوان). والاحتياطي هو `awk` نفسه إن غابت المكتبة الأصلية أو عاد الملف فارغًا.
     */
    private fun isActiveSwap(root: String): Boolean {
        ProbeBridge.readMany(listOf("/proc/swaps"))
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { swaps ->
                return swaps.lineSequence()
                    .drop(1)
                    .mapNotNull { line -> line.trim().split(Regex("\\s+")).firstOrNull() }
                    .any { it == root }
            }
        return runCatching {
            com.topjohnwu.superuser.Shell.cmd("awk '\$1==\"$root\" {found=1} END {exit found?0:1}' /proc/swaps 2>/dev/null").exec().isSuccess
        }.getOrDefault(false)
    }
}
