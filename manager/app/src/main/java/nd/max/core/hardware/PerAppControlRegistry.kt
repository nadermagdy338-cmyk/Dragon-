package nd.max.core.hardware

/**
 * Per-app policy adapter. The arbiter is the only ownership publisher and the
 * only path allowed to repair drift or restore a baseline.
 *
 * The gate is injected, never constructed here: two arbiter instances inside one
 * process would each keep their own request table, so an intent created by one
 * would look ownerless to the other and be reported as a foreign preemption.
 */
class PerAppControlRegistry(
    private val mutationGate: HardwareControlArbiter,
    private val token: String = "per-app",
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

    /**
     * Knobs the gate refused since [beginApp], with its own reason. A refused
     * knob never becomes an entry, so without this record a per-app rule blocked
     * by a manual lock or by safety would be reported as silence — and silence
     * reads as success.
     */
    private val refusals = linkedMapOf<String, String>()
    @Volatile private var currentToken = token

    @Synchronized fun beginApp(packageName: String) {
        releaseAll()
        refusals.clear()
        currentToken = "per-app:$packageName"
    }

    /** The gate's refusal reasons for the current app, keyed by control key. */
    @Synchronized fun refusalReasons(): Map<String, String> = refusals.toMap()

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
        if (result.verified) {
            entries[key] = Entry(key, desired, apply, read, baseline, restore)
            refusals.remove(key)
        } else if (result.error != null) {
            refusals[key] = result.error!!
        }
        return result.verified
    }

    @Synchronized fun release(key: String) {
        refusals.remove(key)
        if (entries.remove(key) != null) mutationGate.release(key, currentToken, restore = true)
    }

    @Synchronized fun releaseAll() {
        entries.keys.toList().asReversed().forEach { key ->
            mutationGate.release(key, currentToken, restore = true)
        }
        mutationGate.releaseToken(currentToken, restore = true)
        entries.clear()
    }

    @Synchronized fun verifyAndRepair(): List<RepairResult> = entries.values.map { entry ->
        val result = mutationGate.reconcile(entry.key) ?: mutationGate.submit(
            key = entry.key,
            owner = ControlOwnership.Owner.PER_APP,
            token = currentToken,
            desired = entry.desired,
            apply = entry.apply,
            read = entry.read,
            baseline = entry.baseline,
            restore = entry.restore,
        )
        RepairResult(
            key = entry.key,
            requested = entry.desired,
            actual = result.actual,
            applied = result.applied,
            verified = result.verified,
            attempts = if (result.applied) 1 else 0,
            error = result.error,
        )
    }
}
