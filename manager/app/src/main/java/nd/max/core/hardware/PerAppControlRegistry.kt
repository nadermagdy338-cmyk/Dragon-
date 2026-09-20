package nd.max.core.hardware

/**
 * Per-app policy adapter. The arbiter is the only ownership publisher and the
 * only path allowed to repair drift or restore a baseline.
 *
 * The gate is injected, never constructed here: two arbiter instances inside one
 * process would each keep their own request table, so an intent created by one
 * would look ownerless to the other and be reported as a foreign preemption.
 *
 * Every write goes through [HardwareRepairExecutor], which is the single implementation of
 * "apply, confirm for a bounded window, restore on failure" in the app. This class used to carry a
 * second copy of that logic inline, so a fix to one did not reach the other; now there is one.
 */
class PerAppControlRegistry(
    private val mutationGate: HardwareControlArbiter,
    private val token: String = "per-app",
    private val confirmationSamples: Int = 3,
    private val confirmationIntervalMs: Long = 40L,
    sleep: (Long) -> Unit = { delay -> if (delay > 0L) Thread.sleep(delay) },
) {
    private val executor = HardwareRepairExecutor(mutationGate, sleep)

    init {
        require(confirmationSamples in 1..8) { "confirmationSamples must stay bounded" }
        require(confirmationIntervalMs in 0L..500L) { "confirmationIntervalMs must stay bounded" }
    }

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
        /**
         * The live value did not match the intent *before* this pass attempted anything.
         *
         * This is the only honest signal for "a repair happened": the arbiter reports `applied = true`
         * both when it wrote the value and when it found the desired value already in place, so a caller
         * logging "repaired" off `applied` would log it every ten seconds for every healthy knob — and a
         * log that cries wolf is a log nobody reads. `null` means the value could not be read at all.
         */
        val driftedBefore: Boolean? = null,
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
        val entry = Entry(key, desired, apply, read, baseline, restore)
        val outcome = executor.execute(requestFor(entry))
        record(entry, outcome)
        return outcome.successful
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

    /**
     * The bounded drift pass: re-verifies every registered knob and repairs the ones an external
     * writer took back.
     *
     * The iteration runs over a **snapshot** on purpose. [record] may drop a failed knob from
     * [entries], and removing an element of a `LinkedHashMap` while iterating its live values view
     * throws `ConcurrentModificationException` on the next element — so the earlier version aborted
     * the whole pass on the first knob a vendor daemon had reclaimed, and none of the knobs after it
     * were ever repaired. That is precisely the case this loop exists for.
     */
    @Synchronized fun verifyAndRepair(): List<RepairResult> = entries.entries.toList().map { (key, entry) ->
        val before = runCatching { entry.read() }.getOrNull()
        val outcome = executor.execute(requestFor(entry))
        record(entry, outcome)
        RepairResult(
            key = key,
            requested = entry.desired,
            actual = outcome.actual,
            applied = outcome.applied,
            verified = outcome.verified,
            attempts = if (outcome.applied) 1 else 0,
            error = outcome.error,
            driftedBefore = before?.let { it != entry.desired },
        )
    }

    private fun requestFor(entry: Entry): HardwareRepairRequest = HardwareRepairRequest(
        routeId = HardwareRepairExecutor.labelFor(entry.key),
        key = entry.key,
        owner = ControlOwnership.Owner.PER_APP,
        token = currentToken,
        desired = entry.desired,
        apply = entry.apply,
        read = entry.read,
        restore = entry.restore ?: entry.apply,
        baseline = entry.baseline,
        stabilitySamples = confirmationSamples,
        stabilityIntervalMs = confirmationIntervalMs,
    )

    /**
     * Records the intent according to what actually happened.
     *
     * A drift failure — the value was written, an external writer took it back inside the confirmation
     * window, and the baseline was restored — **keeps the entry registered**. This pass is the only
     * place that can notice and repair that class of failure, and dropping the entry on the way in
     * meant a per-app knob was silently lost for the rest of the app session the first time a vendor
     * daemon won a race: the user set a control, nothing changed, and no later pass tried again.
     *
     * A refusal (manual lock, foreign owner) or a hardware failure that could not even be rolled back
     * is *not* retried: those repeat identically forever, and quarantining them is what keeps a bounded
     * loop bounded.
     */
    private fun record(entry: Entry, outcome: HardwareRepairResult) {
        if (outcome.successful) {
            entries[entry.key] = entry
            refusals.remove(entry.key)
            return
        }
        outcome.error?.let { refusals[entry.key] = it }
        when (outcome.state) {
            HardwareRepairState.DRIFT_ROLLED_BACK -> entries[entry.key] = entry
            else -> entries.remove(entry.key)
        }
    }
}
