/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas.support

import nd.max.core.atlas.AtlasFailureRetryPolicy
import nd.max.core.atlas.AtlasFreshnessPolicy
import nd.max.core.hardware.AtlasReadBudget

/**
 * Atlas budgets, seen from the tests.
 *
 * The transport bounds are **not** copied here: they are read from [AtlasReadBudget.DEFAULT], the
 * single source of truth in the product. `DECISION-1` in `01-PLAN.md` §15 records that the two source
 * artifacts disagreed (8 s / 250 ms / 256 KiB versus 12 s / 1 s / 512 KiB); the reconciliation lives
 * in the product so a test cannot enforce a limit the shipped code does not.
 *
 * The evidence lifetimes and retry delays are **not** copied either (`P12`): they are read from the
 * product's own policy objects, for the same reason — a test that enforces a lifetime the shipped code
 * does not use is a test that agrees with nobody. The report bound is still a design value for `P6`.
 */
object AtlasBudgets {

    private val BUDGET: AtlasReadBudget = AtlasReadBudget.DEFAULT

    /** One discovery job, covering the reviewed stage and the bounded discovery stage. */
    val JOB_DEADLINE_MS: Long = BUDGET.jobDeadlineMs

    /** Per file/attribute attempt. Slow kernels exist; a 250 ms cap would manufacture false timeouts. */
    val OPERATION_DEADLINE_MS: Long = BUDGET.operationDeadlineMs

    /** Enumeration bounds: per parent root, and for the whole job. */
    val MAX_ENTRIES_PER_ROOT: Int = BUDGET.maxEntriesPerRoot
    val MAX_ENTRIES_TOTAL: Int = BUDGET.maxEntriesTotal

    /** Read bounds. `+1` byte detection of truncation is the transport's job. */
    val MAX_SCALAR_BYTES: Int = BUDGET.maxScalarBytes
    val MAX_REVIEWED_PROC_BYTES: Int = BUDGET.maxReviewedProcBytes
    val MAX_AGGREGATE_BYTES: Int = BUDGET.maxAggregateBytes

    /** Path discipline. */
    val MAX_SYMLINK_HOPS: Int = BUDGET.maxSymlinkHops

    /** Concurrency: one coalesced job, at most two reads, at most one privileged operation. */
    val MAX_CONCURRENT_READS: Int = BUDGET.maxConcurrentReads
    val MAX_CONCURRENT_PRIVILEGED: Int = BUDGET.maxConcurrentPrivileged

    /** Evidence lifetimes. Positive evidence is capped; negative evidence retries briefly. */
    const val POSITIVE_TTL_MS = AtlasFreshnessPolicy.MAX_POSITIVE_CACHE_TTL_MS
    const val NEGATIVE_TTL_MS = AtlasFailureRetryPolicy.NEGATIVE_TTL_MS
    const val DENIAL_RETRY_MS = AtlasFailureRetryPolicy.DENIAL_RETRY_MS

    /** One instant value must be re-read before it is shown as live (`P12`). */
    const val INSTANT_TTL_MS = AtlasFreshnessPolicy.INSTANT_TTL_MS

    /** Support report bounds (P6). */
    const val REPORT_MAX_BYTES = 256 * 1024

    /** Provenance of these numbers, so nobody mistakes them for measured limits. */
    const val PROVENANCE: String = AtlasReadBudget.PROVENANCE
}
