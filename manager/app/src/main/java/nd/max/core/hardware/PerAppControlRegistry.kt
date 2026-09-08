package nd.max.core.hardware

/**
 * Per-app ownership registry. An entry contains the desired value and an
 * explicit baseline restore callback so ownership has a deterministic exit.
 */
class PerAppControlRegistry(
    private val token: String = "per-app",
    private val mutationGate: HardwareControlArbiter = HardwareControlArbiter(),
) {
    data class Entry(
        val key: String,
        var desired: String,
        val apply: (String) -> Boolean,
        val read: () -> String?,
        val baseline: String? = null,
        val restore: ((String) -> Boolean)? = null,
    )

    data class RepairResult(
        val key: String,
        val requested: String,
        val actual: String?,
        val applied: Boolean,
        val verified: Boolean,
        val attempts: Int,
        val error: String? = null,
    ) { val successful: Boolean get() = applied && verified }

    private val entries = linkedMapOf<String, Entry>()
    @Volatile private var currentToken = token

    @Synchronized fun beginApp(packageName: String) {
        // Never discard an owned entry without restoring its baseline. AppMonitor
        // normally calls releaseAll() during a foreground switch, but this guard
        // also makes beginApp() safe when it is called directly after an interrupted
        // apply/revert sequence.
        entries.values.toList().asReversed().forEach { entry ->
            if (entry.baseline != null && entry.restore != null) {
                runCatching { entry.restore.invoke(entry.baseline) }
            }
            mutationGate.release(entry.key, currentToken, restore = false)
        }
        mutationGate.releaseToken(currentToken, restore = false)
        entries.clear()
        currentToken = "per-app:$packageName"
    }

    @Synchronized fun ownGovernor(key: String, desired: String, apply: (String) -> Boolean, read: () -> String?, baseline: String? = null, restore: ((String) -> Boolean)? = null): Boolean =
        own(key, desired, apply, read, baseline, restore)

    @Synchronized fun ownValue(key: String, desired: String, apply: (String) -> Boolean, read: () -> String?, baseline: String? = null, restore: ((String) -> Boolean)? = null): Boolean =
        own(key, desired, apply, read, baseline, restore)

    @Synchronized private fun own(key: String, desired: String, apply: (String) -> Boolean, read: () -> String?, baseline: String?, restore: ((String) -> Boolean)?): Boolean {
        val result = mutationGate.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = currentToken,
            desired = desired,
            apply = apply,
            read = read,
            baseline = baseline,
            restore = restore,
        )
        if (result.blocked) return false
        entries[key] = Entry(key, desired, apply, read, baseline, restore)
        return result.verified || result.applied
    }

    @Synchronized fun release(key: String) {
        val entry = entries.remove(key)
        if (entry != null && entry.baseline != null && entry.restore != null) {
            runCatching { entry.restore.invoke(entry.baseline) }
        }
        mutationGate.release(key, currentToken, restore = false)
    }

    @Synchronized fun releaseAll() {
        entries.values.toList().asReversed().forEach { entry ->
            if (entry.baseline != null && entry.restore != null) runCatching { entry.restore.invoke(entry.baseline) }
            mutationGate.release(entry.key, currentToken, restore = false)
        }
        entries.clear()
    }

    @Synchronized fun verifyAndRepair(): List<RepairResult> = entries.values.map { entry ->
        var actual = runCatching { entry.read() }.getOrNull()
        if (actual == entry.desired) return@map RepairResult(entry.key, entry.desired, actual, true, true, 0)
        var applied = false
        var verified = false
        var attempts = 0
        var error: String? = null
        while (attempts < 3 && !verified) {
            attempts++
            applied = runCatching { entry.apply(entry.desired) }.getOrElse {
                error = it.message ?: "apply-exception"
                false
            }
            actual = runCatching { entry.read() }.getOrNull()
            verified = applied && actual == entry.desired
            if (!verified && error == null) error = "live-value-mismatch"
        }
        RepairResult(entry.key, entry.desired, actual, applied, verified, attempts, error)
    }
}
