/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget

/**
 * Which probes a job will actually run (`P12`).
 *
 * The budget in `P2` answers "may this attempt happen". This file answers the question that comes
 * first: **which attempts are worth their cost right now**. Three things decide, and each has its own
 * reason code so a report can say why a probe did not run instead of implying the device lacked it:
 *
 * 1. **A remembered failure** (see [AtlasFailureLedger]) — asking again cannot help yet.
 * 2. **Freshness** — the evidence is still a statement about now, so re-reading it buys nothing.
 * 3. **Budget** — reviewed, due, unsuppressed, and still more than the job can afford.
 *
 * Ordering is deliberately **cheapest-first, then by id**: breadth of evidence before depth, and two
 * runs over the same input produce the same plan in the same order, because a scan whose shape depends
 * on hash iteration order is a scan nobody can reproduce.
 */
data class AtlasProbeCost(
    /** Syscalls the attempt is expected to cost. Never zero: an attempt is at least one operation. */
    val opens: Int,
    /** Upper bound on bytes the attempt may read. */
    val maxBytes: Int,
) {
    init {
        require(opens >= 1) { "an attempt costs at least one operation" }
        require(maxBytes >= 1) { "an attempt reads at least one byte" }
    }
}

/**
 * One candidate probe.
 *
 * [freshness] is the vintage of the **last successful observation of this path**, recorded with the
 * generations that were in force then; `null` means "never observed in this session", which is always
 * due. A hint about what the catalog expects is not part of this type on purpose: a design intention
 * must not be able to make a probe look measured.
 */
data class AtlasScheduledProbe(
    val id: String,
    val path: String,
    val cost: AtlasProbeCost,
    val freshness: AtlasFreshness?,
) {
    init {
        require(AtlasIds.isValidObservationId(id)) { "scheduled probe id is not canonical: $id" }
        require(AtlasIds.isSafeAbsolutePath(path)) { "scheduled probe path is not a safe absolute path: $path" }
    }
}

/** A probe that was left out, with the stable code that says why. */
data class AtlasSkippedProbe(val id: String, val reason: String) {
    init {
        require(id.isNotBlank()) { "a skipped probe is identified" }
        require(reason.isNotBlank()) { "a skipped probe states a reason" }
    }
}

/** The reason codes. Stable strings, because a report quotes them. */
object AtlasSkipReasons {
    /** Prefix, followed by the cause: `suppressed:PERMISSION_DENIED`. */
    const val SUPPRESSED: String = "suppressed"

    /** Held evidence is still fresh enough to answer. */
    const val NOT_DUE: String = "not-due"

    /** Due and allowed, but the job's operation or byte budget was already committed. */
    const val OVER_BUDGET: String = "over-budget"
}

/** What one job will do, and what it left behind. `due` is in execution order. */
data class AtlasProbePlan(
    val due: List<AtlasScheduledProbe>,
    val skipped: List<AtlasSkippedProbe>,
    val plannedOperations: Int,
    val plannedBytes: Int,
) {
    init {
        require(due.map { it.id }.distinct().size == due.size) { "a plan runs a probe once" }
        require(plannedOperations == due.sumOf { it.cost.opens }) { "planned operations are the sum of the due probes" }
        require(plannedBytes == due.sumOf { it.cost.maxBytes }) { "planned bytes are the sum of the due probes" }
        require(skipped.map { it.id }.distinct().size == skipped.size) { "a skipped probe is reported once" }
    }

    fun reasonFor(id: String): String? = skipped.firstOrNull { it.id == id }?.reason
}

/**
 * Plans one job over a fixed budget.
 *
 * @param budget the same bounds `P2` enforces, so the plan can never propose more than the boundary
 *   would allow; defaults to the single product source of truth.
 * @param clockMs monotonic milliseconds, injected so cadence boundaries are exact in tests.
 */
class AtlasProbeScheduler(
    private val budget: AtlasReadBudget = AtlasReadBudget.DEFAULT,
    private val clockMs: () -> Long,
) {

    fun plan(
        probes: List<AtlasScheduledProbe>,
        bootGeneration: Long,
        privilegeGeneration: Long,
        suppressionOf: (String) -> AtlasSuppression? = { null },
    ): AtlasProbePlan {
        require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }
        val now = clockMs()
        val due = mutableListOf<AtlasScheduledProbe>()
        val skipped = mutableListOf<AtlasSkippedProbe>()
        val candidates = mutableListOf<AtlasScheduledProbe>()

        probes.forEach { probe ->
            val suppression = suppressionOf(probe.path)
            val held = probe.freshness
            when {
                suppression != null -> skipped += AtlasSkippedProbe(
                    probe.id,
                    "${AtlasSkipReasons.SUPPRESSED}:${suppression.cause}",
                )

                held != null && !held.isStaleAt(now, bootGeneration, privilegeGeneration) ->
                    skipped += AtlasSkippedProbe(probe.id, AtlasSkipReasons.NOT_DUE)

                else -> candidates += probe
            }
        }

        val ordered = candidates.sortedWith(compareBy({ it.cost.opens }, { it.cost.maxBytes }, { it.id }))
        var operations = 0
        var bytes = 0
        ordered.forEach { probe ->
            val fitsOperations = operations + probe.cost.opens <= budget.maxOperations
            val fitsBytes = bytes + probe.cost.maxBytes <= budget.maxAggregateBytes
            if (fitsOperations && fitsBytes) {
                due += probe
                operations += probe.cost.opens
                bytes += probe.cost.maxBytes
            } else {
                skipped += AtlasSkippedProbe(probe.id, AtlasSkipReasons.OVER_BUDGET)
            }
        }

        return AtlasProbePlan(
            due = due,
            skipped = skipped.sortedBy { it.id },
            plannedOperations = operations,
            plannedBytes = bytes,
        )
    }
}
