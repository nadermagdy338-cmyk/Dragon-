/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.core.atlas.AtlasAccess
import nd.max.core.atlas.AtlasAnchors
import nd.max.core.atlas.AtlasDirectoryListing
import nd.max.core.atlas.AtlasFailure
import nd.max.core.atlas.AtlasIds
import nd.max.core.atlas.AtlasObservation
import nd.max.core.atlas.AtlasProbeRequest
import nd.max.core.atlas.AtlasReadResult
import nd.max.core.atlas.AtlasUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The lowest-level read surface Atlas is allowed to touch (`P2`).
 *
 * The interface is the guarantee: every function returns bytes that were already on the device, and
 * none of them can change anything. There is deliberately no `write`, no `exec`, no `open` and no
 * shell escape — a transport that cannot be asked to mutate cannot mutate, and `AtlasSourceGuard`
 * fails closed if this file ever names an authority or transport token.
 *
 * A real implementation over the existing authorized read path lands with `P5`; until an adapter has
 * been reviewed, [UnavailableAtlasReadTransport] is the honest default and every attempt against it
 * is reported as `BACKEND_UNAVAILABLE` rather than guessed at.
 */
interface AtlasReadTransport {

    /** Reads at most [maxBytes] of [path]. The transport must never return a partial value silently. */
    fun readText(path: String, maxBytes: Int): AtlasTransportRead

    /** Names directly under [path], capped at [limit]. */
    fun listNames(path: String, limit: Int): AtlasTransportList

    /**
     * Resolves [path] through at most [maxHops] symbolic links and returns the canonical absolute
     * path, or `null` when it cannot be resolved within that bound.
     */
    fun canonicalPath(path: String, maxHops: Int): String?
}

/** Result of one low-level read. Truncation is reported, never hidden in the text. */
sealed interface AtlasTransportRead {
    data class Text(val text: String, val truncated: Boolean) : AtlasTransportRead

    data class Failed(val cause: AtlasFailure, val reason: String) : AtlasTransportRead {
        init {
            require(cause != AtlasFailure.NONE) { "a failed transport read carries a real cause" }
            require(reason.isNotBlank()) { "a failed transport read says why" }
        }
    }
}

/**
 * Result of one low-level enumeration. A failed enumeration returns no names.
 *
 * `truncated` exists because only the transport knows whether it stopped early: without it a
 * listing that was cut at the transport would look complete to the caller, and unvisited entries
 * would silently become "absent".
 */
data class AtlasTransportList(
    val names: List<String>,
    val cause: AtlasFailure = AtlasFailure.NONE,
    val reason: String = "ok",
    val truncated: Boolean = false,
) {
    init {
        if (cause != AtlasFailure.NONE) {
            require(names.isEmpty()) { "a failed enumeration returns no names" }
            require(!truncated) { "a failed enumeration is not described as truncated" }
        }
    }
}

/**
 * Bounds for one discovery job (`P2`).
 *
 * These are **design values, not measurements**: no device timing evidence exists yet, and
 * `DECISION-1` in `.planning/phases/01-…/01-PLAN.md` §15 records that the two source artifacts
 * disagreed. This object is the single source of truth — the test harness delegates here instead of
 * keeping a second copy, because a second copy is how two budgets drift apart.
 */
data class AtlasReadBudget(
    val jobDeadlineMs: Long,
    val operationDeadlineMs: Long,
    val maxEntriesPerRoot: Int,
    val maxEntriesTotal: Int,
    val maxScalarBytes: Int,
    val maxReviewedProcBytes: Int,
    val maxAggregateBytes: Int,
    val maxSymlinkHops: Int,
    val maxOperations: Int,
    val maxConcurrentReads: Int,
    val maxConcurrentPrivileged: Int,
) {
    init {
        require(jobDeadlineMs > 0L) { "job deadline must be positive" }
        require(operationDeadlineMs > 0L) { "operation deadline must be positive" }
        require(operationDeadlineMs <= jobDeadlineMs) { "one operation cannot outlive its job" }
        require(maxEntriesPerRoot > 0) { "per-root entry budget must be positive" }
        require(maxEntriesPerRoot <= maxEntriesTotal) { "per-root budget cannot exceed the job budget" }
        require(maxReviewedProcBytes >= maxScalarBytes) { "the reviewed /proc allowance is the larger one" }
        require(maxAggregateBytes >= maxScalarBytes) { "aggregate budget cannot be smaller than one read" }
        require(maxSymlinkHops > 0) { "symlink resolution needs a positive hop bound" }
        require(maxOperations > 0) { "operation budget must be positive" }
        require(maxConcurrentReads > 0) { "at least one read must be allowed to make progress" }
        require(maxConcurrentPrivileged in 1..maxConcurrentReads) { "privileged concurrency cannot exceed total concurrency" }
    }

    /** The byte allowance for one attempt. */
    fun bytesFor(request: AtlasProbeRequest): Int =
        if (request.reviewedProcSummary) maxReviewedProcBytes else maxScalarBytes

    companion object {

        /**
         * The recommended reconciliation of the conflicting sources: 8 s for the job, 1 s per
         * operation, 128 entries per root and 512 for the job, 4 KiB per value (16 KiB for a
         * reviewed `/proc` summary), 256 KiB aggregate, 8 symlink hops.
         */
        val DEFAULT: AtlasReadBudget = AtlasReadBudget(
            jobDeadlineMs = 8_000L,
            operationDeadlineMs = 1_000L,
            maxEntriesPerRoot = 128,
            maxEntriesTotal = 512,
            maxScalarBytes = 4 * 1024,
            maxReviewedProcBytes = 16 * 1024,
            maxAggregateBytes = 256 * 1024,
            maxSymlinkHops = 8,
            maxOperations = 512,
            maxConcurrentReads = 2,
            maxConcurrentPrivileged = 1,
        )

        /** Provenance, so nobody mistakes these bounds for measured limits. */
        const val PROVENANCE: String = "design values; unmeasured; DECISION-1 reconciliation pending review"
    }
}

/** What one job actually did, so a report can say a limit was reached instead of hiding it. */
data class AtlasAccessStats(
    val operations: Int,
    val entriesVisited: Int,
    val bytesRead: Int,
    val limitsReached: Boolean,
    val generation: Long,
    /** Attempts still in flight. Zero after a job finishes; non-zero would mean a leaked gate. */
    val inFlight: Int,
)

/**
 * A transport that is honest about having nothing behind it.
 *
 * Wiring this in is how the app behaves when no reviewed bounded transport exists yet: results are
 * `BACKEND_UNAVAILABLE` (an unknown), never `ABSENT` (a claim about the device) and never a guess.
 */
object UnavailableAtlasReadTransport : AtlasReadTransport {

    private const val REASON = "no reviewed bounded transport is wired in yet"

    override fun readText(path: String, maxBytes: Int): AtlasTransportRead =
        AtlasTransportRead.Failed(AtlasFailure.BACKEND_UNAVAILABLE, REASON)

    override fun listNames(path: String, limit: Int): AtlasTransportList =
        AtlasTransportList(emptyList(), AtlasFailure.BACKEND_UNAVAILABLE, REASON)

    override fun canonicalPath(path: String, maxHops: Int): String? = null
}

/**
 * The bounded, read-only probe boundary (`P2`).
 *
 * Everything that can turn a failed look into a false statement about the device is decided here,
 * not in an adapter:
 *
 * - **A cause is never invented.** A transport failure keeps the cause it reported.
 * - **Absence is proven or it is not claimed.** `ABSENT` is only ever returned for a path whose
 *   parent *this instance successfully enumerated* and which was not in that listing. Anything else
 *   is `UNKNOWN_CAUSE` — "I did not establish that it is missing" is not "it is missing".
 * - **Partial values never survive.** A truncated read is `BUDGET_EXCEEDED`; the text is dropped
 *   rather than parsed.
 * - **Budgets are enforced before work, not after.** Operation, entry, byte and deadline bounds are
 *   checked before each attempt, so a runaway path stops instead of running to the end.
 * - **No prompt is ever raised.** A privileged request without an authorized transport is
 *   `BACKEND_UNAVAILABLE`.
 *
 * One instance serves one job; its counters start at construction and are reported by [stats].
 *
 * @param clockMs monotonic milliseconds. Injected so deadline boundaries are exact in tests.
 * @param privilegeAvailable consulted only when a request asks for privilege; never blocks, never asks.
 * @param approvedAnchor the anchor check, injectable so a test can prove the refusal path.
 * @param currentGeneration the generation a result must still match to be publishable.
 */
class ReadOnlyProbeAccess(
    private val transport: AtlasReadTransport = UnavailableAtlasReadTransport,
    private val budget: AtlasReadBudget = AtlasReadBudget.DEFAULT,
    private val clockMs: () -> Long,
    private val privilegeAvailable: () -> Boolean = { true },
    private val approvedAnchor: (String) -> Boolean = AtlasAnchors::isApproved,
    private val currentGeneration: () -> Long = { 0L },
) {

    private val jobStartedAtMs: Long = clockMs()

    /** Parents this instance enumerated successfully: the only place absence can be proven from. */
    private val enumeratedParents: MutableSet<String> = mutableSetOf()

    /** In-flight attempts. Atomic because one job may be driven from more than one thread. */
    private val inFlight = AtomicInteger(0)
    private val privilegedInFlight = AtomicInteger(0)

    private var operations: Int = 0
    private var entriesVisited: Int = 0
    private var bytesRead: Int = 0
    private var limitsReached: Boolean = false

    /** One bounded scalar attempt. */
    fun read(request: AtlasProbeRequest): AtlasReadResult {
        val path = request.path

        if (!AtlasIds.isSafeAbsolutePath(path) || !AtlasIds.isSafeBasename(path.substringAfterLast('/'))) {
            return reject(path, AtlasFailure.MALFORMED, "refused before any transport call: $path is not a safe template")
        }
        if (AtlasAnchors.isPublicApiSurface(path)) {
            return reject(path, AtlasFailure.UNKNOWN_CAUSE, "a public-API surface is not a file path; not attempted")
        }
        if (!approvedAnchor(path)) {
            return reject(path, AtlasFailure.UNKNOWN_CAUSE, "outside the approved anchor roots; not attempted")
        }
        if (clockMs() - jobStartedAtMs > budget.jobDeadlineMs) {
            limitsReached = true
            return reject(path, AtlasFailure.BUDGET_EXCEEDED, "job deadline ${budget.jobDeadlineMs} ms exceeded")
        }
        if (operations >= budget.maxOperations) {
            limitsReached = true
            return reject(path, AtlasFailure.BUDGET_EXCEEDED, "operation budget ${budget.maxOperations} reached")
        }
        if (request.requiresPrivilege && !privilegeAvailable()) {
            return reject(path, AtlasFailure.BACKEND_UNAVAILABLE, "privileged read requested but no authorized transport exists")
        }

        tryEnter(request.requiresPrivilege)?.let { (cause, reason) ->
            limitsReached = true
            return reject(path, cause, reason)
        }

        val remaining = budget.maxAggregateBytes - bytesRead
        val limit = minOf(budget.bytesFor(request), remaining)
        if (limit <= 0) {
            limitsReached = true
            return reject(path, AtlasFailure.BUDGET_EXCEEDED, "aggregate byte budget ${budget.maxAggregateBytes} reached")
        }

        return try {
            performRead(request, limit)
        } finally {
            exit(request.requiresPrivilege)
        }
    }

    private fun performRead(request: AtlasProbeRequest, limit: Int): AtlasReadResult {
        val path = request.path
        operations += 1
        val deadline = clockMs() + budget.operationDeadlineMs
        val canonical = transport.canonicalPath(path, budget.maxSymlinkHops)
            ?: return reject(path, AtlasFailure.BACKEND_UNAVAILABLE, "path could not be resolved within ${budget.maxSymlinkHops} symlink hops")
        if (canonical != path) {
            if (!AtlasIds.isSafeAbsolutePath(canonical) || !approvedAnchor(canonical)) {
                return reject(path, AtlasFailure.UNKNOWN_CAUSE, "resolved path escapes the approved anchors: $canonical")
            }
        }
        if (clockMs() > deadline) {
            limitsReached = true
            return reject(path, AtlasFailure.TIMED_OUT, "operation deadline ${budget.operationDeadlineMs} ms exceeded before the read")
        }

        return when (val attempt = transport.readText(canonical, limit)) {
            is AtlasTransportRead.Failed -> when (attempt.cause) {
                AtlasFailure.ABSENT ->
                    if (absenceIsProven(path)) {
                        reject(path, AtlasFailure.ABSENT, attempt.reason)
                    } else {
                        reject(path, AtlasFailure.UNKNOWN_CAUSE, "absence not proven: ${parentOf(path)} was not enumerated")
                    }

                else -> reject(path, attempt.cause, attempt.reason)
            }

            is AtlasTransportRead.Text -> {
                if (clockMs() > deadline) {
                    limitsReached = true
                    return reject(path, AtlasFailure.TIMED_OUT, "operation deadline ${budget.operationDeadlineMs} ms exceeded")
                }
                if (attempt.truncated) {
                    limitsReached = true
                    return reject(path, AtlasFailure.BUDGET_EXCEEDED, "value exceeded $limit bytes; no partial parse")
                }
                bytesRead += attempt.text.toByteArray(Charsets.UTF_8).size
                parse(request, attempt.text)
            }
        }
    }

    /**
     * One bounded enumeration.
     *
     * Names that are not safe basenames are dropped and the listing is marked truncated, so a caller
     * never sees an unsafe name and never mistakes dropped coverage for a complete one.
     */
    fun list(root: String): AtlasDirectoryListing {
        if (!AtlasIds.isSafeAbsolutePath(root)) {
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.MALFORMED, "refused: $root is not a safe template")
        }
        if (AtlasAnchors.isPublicApiSurface(root)) {
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.UNKNOWN_CAUSE, "a public-API surface is not a file path; not attempted")
        }
        if (!approvedAnchor(root)) {
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.UNKNOWN_CAUSE, "outside the approved anchor roots; not attempted")
        }
        if (clockMs() - jobStartedAtMs > budget.jobDeadlineMs) {
            limitsReached = true
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "job deadline ${budget.jobDeadlineMs} ms exceeded")
        }
        if (operations >= budget.maxOperations) {
            limitsReached = true
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "operation budget ${budget.maxOperations} reached")
        }

        tryEnter(requiresPrivilege = false)?.let { (cause, reason) ->
            limitsReached = true
            return AtlasDirectoryListing(root, emptyList(), false, cause, reason)
        }

        val limit = minOf(budget.maxEntriesPerRoot, budget.maxEntriesTotal - entriesVisited)
        if (limit <= 0) {
            limitsReached = true
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "aggregate entry budget ${budget.maxEntriesTotal} reached")
        }

        return try {
            performList(root, limit)
        } finally {
            exit(requiresPrivilege = false)
        }
    }

    private fun performList(root: String, limit: Int): AtlasDirectoryListing {
        operations += 1
        val deadline = clockMs() + budget.operationDeadlineMs
        val listed = transport.listNames(root, limit)
        if (clockMs() > deadline) {
            limitsReached = true
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.TIMED_OUT, "operation deadline ${budget.operationDeadlineMs} ms exceeded")
        }
        if (listed.cause != AtlasFailure.NONE) {
            return AtlasDirectoryListing(root, emptyList(), false, listed.cause, listed.reason)
        }

        val distinct = listed.names.distinct()
        val safe = distinct.filter(AtlasIds::isSafeBasename).sorted()
        val dropped = distinct.size - safe.size
        entriesVisited += safe.size
        if (entriesVisited > budget.maxEntriesTotal) {
            limitsReached = true
            return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "aggregate entry budget ${budget.maxEntriesTotal} exceeded")
        }

        // Only a listing that was actually read may license a later absence claim.
        enumeratedParents.add(root)

        val cutByTransport = listed.truncated || safe.size > limit
        val truncated = cutByTransport || dropped > 0
        if (truncated) limitsReached = true
        return when {
            dropped > 0 -> AtlasDirectoryListing(root, safe.take(limit), true, AtlasFailure.NONE, "dropped $dropped unsafe basenames")
            cutByTransport -> AtlasDirectoryListing(root, safe.take(limit), true, AtlasFailure.NONE, "truncated at $limit entries")
            else -> AtlasDirectoryListing(root, safe, false, AtlasFailure.NONE, "complete listing")
        }
    }

    /**
     * Admits an attempt, or refuses it because a cap is already held.
     *
     * The claim is made by **incrementing first and rolling back on refusal**. A check-then-enter
     * version is not atomic: several threads can all read `inFlight = 0`, all pass the check, and all
     * enter — the cap would hold for one thread at a time and fail exactly under the flood it exists
     * for. (That defect was real and was found by the threaded test, not by reading this code.)
     *
     * A stuck worker is answered with a refusal, never with another worker: queueing unbounded
     * replacements is how one slow kernel interface turns into a burst of parallel reads.
     */
    private fun tryEnter(requiresPrivilege: Boolean): Pair<AtlasFailure, String>? {
        val now = inFlight.incrementAndGet()
        if (now > budget.maxConcurrentReads) {
            inFlight.decrementAndGet()
            return AtlasFailure.BUDGET_EXCEEDED to
                "concurrency cap ${budget.maxConcurrentReads} reached; refused instead of queued"
        }
        if (requiresPrivilege) {
            val privileged = privilegedInFlight.incrementAndGet()
            if (privileged > budget.maxConcurrentPrivileged) {
                privilegedInFlight.decrementAndGet()
                inFlight.decrementAndGet()
                return AtlasFailure.BUDGET_EXCEEDED to
                    "privileged concurrency cap ${budget.maxConcurrentPrivileged} reached; refused instead of queued"
            }
        }
        return null
    }

    private fun exit(requiresPrivilege: Boolean) {
        if (requiresPrivilege) privilegedInFlight.decrementAndGet()
        inFlight.decrementAndGet()
    }

    /** A result may be published only from the generation that is still current. */
    fun isCurrent(generation: Long): Boolean = generation == currentGeneration()

    fun stats(): AtlasAccessStats = AtlasAccessStats(
        operations = operations,
        entriesVisited = entriesVisited,
        bytesRead = bytesRead,
        limitsReached = limitsReached,
        generation = currentGeneration(),
        inFlight = inFlight.get(),
    )

    private fun absenceIsProven(path: String): Boolean = parentOf(path) in enumeratedParents

    private fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

    private fun reject(path: String, cause: AtlasFailure, reason: String): AtlasReadResult.Rejected =
        AtlasReadResult.Rejected(path, cause, reason)

    private fun parse(request: AtlasProbeRequest, raw: String): AtlasReadResult {
        if (request.unit == AtlasUnit.UNKNOWN) {
            return AtlasReadResult.Observed(observation(request, AtlasUnit.UNKNOWN, null, raw, raw))
        }
        val parsed = raw.trim().toDoubleOrNull()
            ?: return reject(request.path, AtlasFailure.MALFORMED, "value is not numeric for unit ${request.unit}")
        if (!parsed.isFinite()) {
            return reject(request.path, AtlasFailure.MALFORMED, "value is not a finite number")
        }
        return AtlasReadResult.Observed(observation(request, request.unit, parsed, null, raw))
    }

    private fun observation(
        request: AtlasProbeRequest,
        unit: AtlasUnit,
        value: Double?,
        textValue: String?,
        raw: String,
    ): AtlasObservation = AtlasObservation(
        id = request.id,
        domain = request.domain,
        providerId = request.providerId,
        catalogVersion = request.catalogVersion,
        sourceId = request.sourceId,
        path = request.path,
        access = AtlasAccess.READABLE,
        semanticStatus = request.semanticStatus,
        unit = unit,
        value = value,
        textValue = textValue,
        rawRepresentation = raw.take(512),
        failure = AtlasFailure.NONE,
        reason = "read",
        observedAtElapsedMs = clockMs(),
        bootGeneration = 0L,
        privilegeGeneration = currentGeneration(),
        truncated = false,
    )
}
