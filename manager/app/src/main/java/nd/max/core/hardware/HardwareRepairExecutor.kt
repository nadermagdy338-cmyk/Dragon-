package nd.max.core.hardware

/** The externally meaningful state of one route transaction. */
enum class HardwareRepairState {
    /**
     * The value was written and read back unchanged for the bounded confirmation window.
     *
     * **This is a fast confirmation, not a claim of sustained stability.** The default window is three
     * samples forty milliseconds apart — roughly eighty milliseconds — and a vendor writer that
     * republishes on a seconds-long timer cannot be observed inside it. The state was called
     * `VERIFIED_STABLE` before, which promised more than the measurement supports: a caller reading
     * "stable" would stop looking exactly when the interesting failure happens later.
     *
     * Sustained verification is owned by the caller's own drift loop, which re-reads on a much coarser
     * cadence (`AppMonitor.DRIFT_CHECK_INTERVAL_MS`, ten seconds) and is the only thing that can see a
     * periodic rewrite. `DRIFT_ROLLED_BACK` below is what that class of failure looks like when it
     * happens *inside* the window.
     */
    CONFIRMED_WINDOW,
    BLOCKED,
    APPLY_FAILED_ROLLED_BACK,

    /** An external writer took the value back inside the window and the baseline was restored. */
    DRIFT_ROLLED_BACK,
    ROLLBACK_UNVERIFIED,
}

data class HardwareRepairRequest(
    val routeId: String,
    val key: String,
    val owner: ControlOwnership.Owner,
    val token: String,
    val desired: String,
    val apply: (String) -> Boolean,
    val read: () -> String?,
    val restore: (String) -> Boolean,
    val baseline: String? = null,
    val stabilitySamples: Int = 3,
    val stabilityIntervalMs: Long = 40L,
) {
    init {
        require(routeId.matches(ROUTE_ID)) { "routeId is not canonical" }
        require(key.isNotBlank() && token.isNotBlank()) { "hardware transaction identity is required" }
        require(stabilitySamples in 1..8) { "stability sample count is outside the bounded range" }
        require(stabilityIntervalMs in 0L..500L) { "stability interval is outside the bounded range" }
    }

    /** How long the read-back value must hold still before the transaction is called confirmed. */
    val confirmationWindowMs: Long get() = stabilityIntervalMs * (stabilitySamples - 1)

    companion object {
        val ROUTE_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")
    }
}

data class HardwareRepairResult(
    val routeId: String,
    val key: String,
    val state: HardwareRepairState,
    val requested: String,
    val actual: String?,
    val applied: Boolean,
    val verified: Boolean,
    val stabilitySamples: Int,
    val rollbackAttempted: Boolean,
    val rollbackVerified: Boolean?,
    val error: String? = null,
) {
    val successful: Boolean get() = state == HardwareRepairState.CONFIRMED_WINDOW
}

/**
 * The only generic repair seam Atlas may use. It never owns a writer itself: all mutation and ownership
 * arbitration are delegated to [HardwareControlArbiter]. A successful first read-back is not enough;
 * the value must remain unchanged for the bounded confirmation window or the transaction is restored.
 *
 * **The window is deliberately short** (see [HardwareRepairState.CONFIRMED_WINDOW]): its job is to catch
 * a value that did not take at all, and it runs inside a caller that must not stall. A caller that needs
 * a long observation owns it in its own loop over `read()`, not here.
 */
class HardwareRepairExecutor(
    private val arbiter: HardwareControlArbiter,
    private val sleep: (Long) -> Unit = { delay -> if (delay > 0L) Thread.sleep(delay) },
) {
    fun execute(request: HardwareRepairRequest): HardwareRepairResult {
        val result = arbiter.submit(
            key = request.key,
            owner = request.owner,
            token = request.token,
            desired = request.desired,
            apply = request.apply,
            read = request.read,
            baseline = request.baseline,
            restore = request.restore,
        )
        if (!result.verified) {
            return HardwareRepairResult(
                routeId = request.routeId,
                key = request.key,
                state = if (result.rollbackAttempted && result.rollbackVerified == true) {
                    HardwareRepairState.APPLY_FAILED_ROLLED_BACK
                } else if (result.rollbackAttempted) {
                    HardwareRepairState.ROLLBACK_UNVERIFIED
                } else {
                    HardwareRepairState.BLOCKED
                },
                requested = request.desired,
                actual = result.actual,
                applied = result.applied,
                verified = false,
                stabilitySamples = 0,
                rollbackAttempted = result.rollbackAttempted,
                rollbackVerified = result.rollbackVerified,
                error = result.error,
            )
        }

        var lastActual = result.actual
        var stable = true
        for (sample in 0 until request.stabilitySamples) {
            if (sample > 0) sleep(request.stabilityIntervalMs)
            lastActual = runCatching { request.read() }.getOrNull()
            if (lastActual != request.desired) {
                stable = false
                break
            }
        }
        if (stable) {
            return HardwareRepairResult(
                routeId = request.routeId,
                key = request.key,
                state = HardwareRepairState.CONFIRMED_WINDOW,
                requested = request.desired,
                actual = lastActual,
                applied = true,
                verified = true,
                stabilitySamples = request.stabilitySamples,
                rollbackAttempted = false,
                rollbackVerified = null,
            )
        }

        val released = runCatching { arbiter.release(request.key, request.token, restore = true) }.getOrNull()
        val rollbackVerified = released?.rollbackVerified == true
        return HardwareRepairResult(
            routeId = request.routeId,
            key = request.key,
            state = if (rollbackVerified) {
                HardwareRepairState.DRIFT_ROLLED_BACK
            } else {
                HardwareRepairState.ROLLBACK_UNVERIFIED
            },
            requested = request.desired,
            actual = lastActual,
            applied = true,
            verified = false,
            stabilitySamples = request.stabilitySamples,
            rollbackAttempted = true,
            rollbackVerified = rollbackVerified,
            error = if (rollbackVerified) "external-writer-drift-baseline-restored" else "external-writer-drift-rollback-unverified",
        )
    }

    companion object {

        /**
         * A canonical transaction label for any control key.
         *
         * Keys carry separators that are meaningless in an id (`cpu_limits:/sys/devices/...` →
         * `cpu-limits-sys-devices-...`), so a label has to be derived rather than reused. It is
         * **reporting vocabulary only**: it never addresses a node, it is never parsed back, and the
         * hardware key inside the request is passed through untouched. Two different keys can collide
         * after canonicalisation, which is why the label is never used for ownership or comparison —
         * [HardwareControlArbiter] keys on `Request.key` alone.
         */
        fun labelFor(key: String): String {
            val slug = key.lowercase()
                .map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '-' }
                .joinToString("")
                .replace(Regex("-{2,}"), "-")
                .trim('-', '.')
            val prefixed = if (slug.firstOrNull()?.isLetter() == true) slug else "knob-$slug"
            val bounded = prefixed.take(63).trimEnd('-', '.')
            val label = if (bounded.length >= 3) bounded else (bounded + "-knob").take(63)
            require(label.matches(HardwareRepairRequest.ROUTE_ID)) { "cannot derive a canonical label from '$key'" }
            return label
        }
    }
}
