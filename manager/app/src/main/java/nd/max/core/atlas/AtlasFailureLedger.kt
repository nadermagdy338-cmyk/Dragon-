/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

/**
 * Negative evidence and retry policy (`P12`).
 *
 * A read that failed is a fact, and that fact has a lifetime too. Some failures are permanent for the
 * current session (a denied node will not become readable by asking again), some are transient (a
 * timeout on a busy kernel), and one is not evidence about the device at all (a cancelled job). Asking
 * again without remembering which is which costs budget twice over: the wasted attempt, and the
 * `avc: denied` noise the platform writes for every refused access.
 *
 * Two rules keep this honest:
 *
 * 1. **Only a real attempt may create a suppression.** Nothing here suppresses a path that was never
 *    tried, and an unknown cause suppresses only briefly — the model's rule that an unrecognized error
 *    is not an absence applies to retries too.
 * 2. **A failure never extends a success.** This object knows nothing about successful reads: it
 *    suppresses *attempts*, and a success erases the record entirely (see [AtlasFailureLedger.noteSuccess]).
 */
object AtlasFailureRetryPolicy {

    /** An absent node is retried after a minute: cheap to ask, and mount-time races do resolve. */
    const val NEGATIVE_TTL_MS: Long = 60_000L

    /**
     * The ceiling for privilege-shaped failures (`PERMISSION_DENIED`, `READ_ONLY`,
     * `BACKEND_UNAVAILABLE`). `DECISION-1` records the intent as "until the privilege generation
     * changes **or** five minutes": a generation change drops the record immediately, and this ceiling
     * stops an unchanged session from suppressing a path forever.
     */
    const val DENIAL_RETRY_MS: Long = 5 * 60_000L

    /** A malformed value is a property of the interface; it will not fix itself quickly. */
    const val MALFORMED_RETRY_MS: Long = 5 * 60_000L

    /** An unrecognized error is retried sooner than a reviewed denial, because we do not know it. */
    const val UNKNOWN_RETRY_MS: Long = 30_000L

    /** First retry delay for a transient failure, doubling per consecutive attempt. */
    const val TRANSIENT_BASE_MS: Long = 1_000L

    /** Cap on the doubling schedule, so a stuck interface is still re-checked once a minute. */
    const val TRANSIENT_CAP_MS: Long = 60_000L

    /**
     * Whether a cause justifies remembering a refusal at all.
     *
     * `CANCELLED` is excluded on purpose: a cancelled job is evidence about the *job*, not about the
     * device, and turning it into a suppression would let one cancellation hide a readable interface.
     * `STALE` is excluded for a different reason: staleness says "we hold an old value", not "reading
     * this failed", so there is nothing to suppress.
     */
    fun suppresses(cause: AtlasFailure): Boolean = when (cause) {
        AtlasFailure.NONE,
        AtlasFailure.CANCELLED,
        AtlasFailure.STALE,
        -> false

        AtlasFailure.ABSENT,
        AtlasFailure.PERMISSION_DENIED,
        AtlasFailure.READ_ONLY,
        AtlasFailure.MALFORMED,
        AtlasFailure.AMBIGUOUS,
        AtlasFailure.BACKEND_UNAVAILABLE,
        AtlasFailure.TIMED_OUT,
        AtlasFailure.BUDGET_EXCEEDED,
        AtlasFailure.UNKNOWN_CAUSE,
        -> true
    }

    /**
     * How long a recorded failure keeps a path quiet, given how many times in a row it failed.
     *
     * The doubling schedule applies only to transient causes. A denial is a policy answer, so it uses
     * the single ceiling instead of growing: sleeping longer cannot change a policy.
     */
    fun retryDelayMs(cause: AtlasFailure, consecutiveAttempts: Int): Long {
        require(consecutiveAttempts >= 1) { "the first recorded failure is attempt 1" }
        require(suppresses(cause)) { "a cause that does not suppress has no delay: $cause" }
        return when (cause) {
            AtlasFailure.ABSENT -> NEGATIVE_TTL_MS
            AtlasFailure.PERMISSION_DENIED,
            AtlasFailure.READ_ONLY,
            AtlasFailure.BACKEND_UNAVAILABLE,
            -> DENIAL_RETRY_MS

            AtlasFailure.MALFORMED, AtlasFailure.AMBIGUOUS -> MALFORMED_RETRY_MS
            AtlasFailure.UNKNOWN_CAUSE -> UNKNOWN_RETRY_MS
            AtlasFailure.TIMED_OUT, AtlasFailure.BUDGET_EXCEEDED ->
                doubling(consecutiveAttempts)

            AtlasFailure.NONE, AtlasFailure.CANCELLED, AtlasFailure.STALE ->
                error("unreachable: suppresses() already rejected $cause")
        }
    }

    private fun doubling(attempts: Int): Long {
        var delay = TRANSIENT_BASE_MS
        var remaining = attempts - 1
        while (remaining > 0 && delay < TRANSIENT_CAP_MS) {
            delay *= 2L
            remaining -= 1
        }
        return minOf(delay, TRANSIENT_CAP_MS)
    }

    /** Provenance, so nobody mistakes these delays for measured ones. */
    const val PROVENANCE: String = "design values; unmeasured; P5.4 of 01-PLAN §15 (DECISION-1)"
}

/** A failure that is still in force for a path: the cause, why it is kept, and when it lapses. */
data class AtlasSuppression(
    val cause: AtlasFailure,
    /** Stable, human-readable and non-empty: a suppression nobody can explain is not auditable. */
    val reason: String,
    /**
     * When this suppression lapses by time. A generation change (reboot, privilege change) lapses it
     * earlier, so this is an upper bound rather than the only way out.
     */
    val untilElapsedMs: Long,
) {
    init {
        require(cause != AtlasFailure.NONE) { "a suppression names a real cause" }
        require(reason.isNotBlank()) { "a suppression says why" }
        require(untilElapsedMs >= 0L) { "a lapse time is non-negative" }
    }
}

/**
 * Remembers failed attempts per path so a job does not re-ask the same closed door.
 *
 * One instance serves one session. Records are keyed by path only; a *different cause* for the same
 * path replaces the record and restarts the count, because "it timed out, then it was denied" is a new
 * fact, not a longer version of the old one.
 */
class AtlasFailureLedger(
    /**
     * The current boot generation, read at use rather than captured: a ledger is a session object that
     * can outlive a reboot (the process may be long-lived), and a captured value would make the
     * "another boot invalidates this" branch unreachable while looking correct.
     */
    private val bootGeneration: () -> Long = { 0L },
    private val privilegeGeneration: () -> Long = { 0L },
    private val clockMs: () -> Long,
) {

    private data class Record(
        val cause: AtlasFailure,
        val recordedAtMs: Long,
        val consecutiveAttempts: Int,
        val bootGeneration: Long,
        val privilegeGeneration: Long,
    )

    private val records: MutableMap<String, Record> = LinkedHashMap()
    private var avoided: Int = 0

    /** Records one real failed attempt. A cause that carries no information removes any record. */
    fun record(path: String, cause: AtlasFailure) {
        require(AtlasIds.isSafeAbsolutePath(path)) { "a ledger key is a canonical absolute path: $path" }
        if (!AtlasFailureRetryPolicy.suppresses(cause)) {
            records.remove(path)
            return
        }
        val currentBoot = bootGeneration()
        require(currentBoot >= 0L) { "generations are non-negative" }
        val previous = records[path]
        val attempts = if (previous?.cause == cause) previous.consecutiveAttempts + 1 else 1
        records[path] = Record(
            cause = cause,
            recordedAtMs = clockMs(),
            consecutiveAttempts = attempts,
            bootGeneration = currentBoot,
            privilegeGeneration = privilegeGeneration(),
        )
    }

    /**
     * A successful read erases the record.
     *
     * This is what keeps the ledger from becoming a belief: the moment the interface answers, every
     * earlier conclusion about it is void.
     */
    fun noteSuccess(path: String) {
        require(AtlasIds.isSafeAbsolutePath(path)) { "a ledger key is a canonical absolute path: $path" }
        records.remove(path)
    }

    /**
     * The suppression still in force for [path], or `null` when an attempt is allowed now.
     *
     * A record from another boot, from another privilege generation, or from a time the clock can no
     * longer place, is discarded rather than trusted. Returning `null` never deletes history that the
     * doubling schedule needs, so a transient failure keeps backing off across the attempts.
     */
    fun suppressionFor(path: String): AtlasSuppression? {
        val record = records[path] ?: return null
        if (record.bootGeneration != bootGeneration()) {
            records.remove(path)
            return null
        }
        if (record.privilegeGeneration != privilegeGeneration()) {
            records.remove(path)
            return null
        }
        val age = (clockMs() - record.recordedAtMs).takeIf { it >= 0L } ?: return null
        val delay = AtlasFailureRetryPolicy.retryDelayMs(record.cause, record.consecutiveAttempts)
        if (age >= delay) return null
        avoided += 1
        val until = record.recordedAtMs + delay
        return AtlasSuppression(
            cause = record.cause,
            reason = reasonFor(record.cause),
            untilElapsedMs = until,
        )
    }

    /** Paths currently remembered, for a report or a test. */
    fun trackedPaths(): Int = records.size

    /** Attempts a caller did not have to make because the ledger refused them. */
    fun avoidedAttempts(): Int = avoided

    private fun reasonFor(cause: AtlasFailure): String = when (cause) {
        AtlasFailure.PERMISSION_DENIED -> "denied by policy or permission; a retry cannot change it"
        AtlasFailure.READ_ONLY -> "the interface reports read-only semantics; not a retry problem"
        AtlasFailure.BACKEND_UNAVAILABLE -> "no authorized transport exists yet"
        AtlasFailure.ABSENT -> "proven absent by an enumeration this session performed"
        AtlasFailure.MALFORMED, AtlasFailure.AMBIGUOUS -> "the value could not be interpreted"
        AtlasFailure.TIMED_OUT -> "a transient timeout; backing off"
        AtlasFailure.BUDGET_EXCEEDED -> "the budget, not the device, stopped the attempt; backing off"
        AtlasFailure.UNKNOWN_CAUSE -> "an unrecognized failure; retried sooner because it is unknown"
        AtlasFailure.NONE, AtlasFailure.CANCELLED, AtlasFailure.STALE -> "not remembered"
    }
}
