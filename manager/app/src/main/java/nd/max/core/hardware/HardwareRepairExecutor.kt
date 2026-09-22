package nd.max.core.hardware

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
) : AtlasRepairPort {
    override fun execute(request: HardwareRepairRequest): HardwareRepairResult {
        val result = arbiter.submit(
            key = request.key,
            owner = request.owner,
            token = request.token,
            desired = request.desired,
            apply = request.apply,
            read = request.read,
            baseline = request.baseline,
            restore = request.restore,
            verify = request.verify,
            realized = request.realized,
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
            if (!satisfied(request, lastActual)) {
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

        /** نفس حكم المُحكِّم بالضبط: الطلب هو مصدر الحكم، والتساوي الحرفي هو الافتراض. */
        private fun satisfied(request: HardwareRepairRequest, actual: String?): Boolean =
            request.verify?.invoke(request.desired, actual) ?: (actual != null && actual == request.desired)

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
