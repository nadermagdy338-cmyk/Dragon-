/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P12` tests for evidence lifetime and for the probe plan built on it.
 *
 * The clock is injected, so every boundary here is exact rather than timed.
 */
class AtlasFreshnessTest {

    // ---- freshness ----------------------------------------------------------------------------------

    @Test
    fun `an instant value is fresh inside its lifetime and stale after it`() {
        val freshness = AtlasFreshness(AtlasVolatility.INSTANT, observedAtElapsedMs = 0L, bootGeneration = 0L, privilegeGeneration = 0L)

        assertFalse(freshness.isStaleAt(500L, 0L, 0L))
        assertFalse("the boundary belongs to the value, not to the age", freshness.isStaleAt(1_000L, 0L, 0L))
        assertTrue(freshness.isStaleAt(1_001L, 0L, 0L))
        assertNull(freshness.ageMsAt(-1L))
        assertEquals(400L, freshness.ageMsAt(400L))
    }

    @Test
    fun `a static capability survives time and expires on a generation change`() {
        val capability = AtlasFreshness(
            AtlasVolatility.STATIC,
            observedAtElapsedMs = 0L,
            bootGeneration = 3L,
            privilegeGeneration = 1L,
        )

        assertFalse(capability.isStaleAt(Long.MAX_VALUE / 2, 3L, 1L))
        assertTrue("another boot invalidates a capability claim", capability.isStaleAt(1L, 4L, 1L))
        assertTrue("a privilege change invalidates it too", capability.isStaleAt(1L, 3L, 2L))
    }

    @Test
    fun `a clock that moved backwards is not treated as fresh`() {
        val freshness = AtlasFreshness(AtlasVolatility.FAST, observedAtElapsedMs = 10_000L, bootGeneration = 0L, privilegeGeneration = 0L)

        assertTrue("an unmeasurable age is not a young age", freshness.isStaleAt(9_000L, 0L, 0L))
    }

    @Test
    fun `each way of being unusable reports its own reason`() {
        val instant = AtlasFreshness(AtlasVolatility.INSTANT, 0L, bootGeneration = 1L, privilegeGeneration = 1L)

        assertEquals(AtlasStaleness.FRESH, instant.stalenessAt(1_000L, 1L, 1L))
        assertEquals(AtlasStaleness.EXPIRED_BY_TIME, instant.stalenessAt(1_001L, 1L, 1L))
        assertEquals(AtlasStaleness.UNMEASURABLE_CLOCK, instant.stalenessAt(-1L, 1L, 1L))
        assertEquals(AtlasStaleness.SUPERSEDED_BY_BOOT, instant.stalenessAt(1L, 2L, 1L))
        assertEquals(AtlasStaleness.SUPERSEDED_BY_PRIVILEGE, instant.stalenessAt(1L, 1L, 2L))
        assertFalse(AtlasStaleness.FRESH.isStale)
        assertTrue(AtlasStaleness.EXPIRED_BY_TIME.isStale)
        assertTrue(AtlasStaleness.SUPERSEDED_BY_BOOT.isStale)
        assertTrue(AtlasStaleness.SUPERSEDED_BY_PRIVILEGE.isStale)
        assertTrue(AtlasStaleness.UNMEASURABLE_CLOCK.isStale)
    }

    @Test
    fun `the boolean stays the summary of the reason, so no call site changed meaning`() {
        val volatilities = AtlasVolatility.values().toList()
        val observations = listOf(0L, 500L)
        val queries = listOf(-1L, 0L, 500L, 1_000L, 10_000L, 60_000L, Long.MAX_VALUE / 2)
        val generations = listOf(0L, 1L)

        var checked = 0
        for (volatility in volatilities) {
            for (observed in observations) {
                for (now in queries) {
                    for (boot in generations) {
                        for (privilege in generations) {
                            val evidence = AtlasFreshness(volatility, observed, boot, privilege)
                            val reason = evidence.stalenessAt(now, 0L, 0L)
                            assertEquals(
                                "$volatility/$observed/$now/$boot/$privilege: the boolean must equal the reason",
                                reason.isStale,
                                evidence.isStaleAt(now, 0L, 0L),
                            )
                            checked++
                        }
                    }
                }
            }
        }
        assertEquals(4 * 2 * 7 * 2 * 2, checked)
    }

    @Test
    fun `the precedences are fixed so a report names one cause, not the first one tested`() {
        val evidence = AtlasFreshness(AtlasVolatility.STATIC, 0L, bootGeneration = 1L, privilegeGeneration = 1L)

        assertEquals(
            "another boot outranks a superseded privilege and an unmeasurable clock",
            AtlasStaleness.SUPERSEDED_BY_BOOT,
            evidence.stalenessAt(-5L, 2L, 2L),
        )
        assertEquals(
            "a privilege change outranks the clock",
            AtlasStaleness.SUPERSEDED_BY_PRIVILEGE,
            evidence.stalenessAt(-5L, 1L, 2L),
        )
        assertEquals("and the clock is reported only when it is the reason", AtlasStaleness.UNMEASURABLE_CLOCK, evidence.stalenessAt(-5L, 1L, 1L))
        assertEquals(
            "a static capability is not expired by time — its bound is the generation",
            AtlasStaleness.FRESH,
            evidence.stalenessAt(Long.MAX_VALUE / 2, 1L, 1L),
        )
    }

    @Test
    fun `the lifetime table is ordered from the shortest to the unbounded`() {
        assertTrue(AtlasFreshnessPolicy.INSTANT_TTL_MS < AtlasFreshnessPolicy.FAST_TTL_MS)
        assertTrue(AtlasFreshnessPolicy.FAST_TTL_MS < AtlasFreshnessPolicy.SLOW_TTL_MS)
        assertTrue(AtlasFreshnessPolicy.SLOW_TTL_MS < AtlasFreshnessPolicy.STATIC_TTL_MS)
        assertTrue(AtlasFreshnessPolicy.SLOW_TTL_MS <= AtlasFreshnessPolicy.MAX_POSITIVE_CACHE_TTL_MS)
        assertEquals(AtlasFreshnessPolicy.STATIC_TTL_MS, AtlasFreshnessPolicy.ttlMs(AtlasVolatility.STATIC))
    }

    @Test
    fun `a negative observation time or generation is rejected`() {
        assertTrue(
            runCatching { AtlasFreshness(AtlasVolatility.FAST, -1L, 0L, 0L) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { AtlasFreshness(AtlasVolatility.FAST, 0L, -1L, 0L) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    // ---- the probe plan ------------------------------------------------------------------------------

    @Test
    fun `everything never observed is due and the plan reports what it will spend`() {
        val plan = scheduler().plan(listOf(probe("probe.one"), probe("probe.two")), 0L, 0L)

        assertEquals(listOf("probe.one", "probe.two"), plan.due.map { it.id })
        assertEquals(4, plan.plannedOperations)
        assertEquals(32, plan.plannedBytes)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun `held evidence that is still fresh is skipped as not due`() {
        val fresh = probe(
            "probe.one",
            freshness = AtlasFreshness(AtlasVolatility.SLOW, observedAtElapsedMs = 0L, bootGeneration = 0L, privilegeGeneration = 0L),
        )
        val plan = scheduler().plan(listOf(fresh, probe("probe.two")), 0L, 0L)

        assertEquals(listOf("probe.two"), plan.due.map { it.id })
        assertEquals(AtlasSkipReasons.NOT_DUE, plan.reasonFor("probe.one"))
    }

    @Test
    fun `held evidence past its lifetime is due again`() {
        val old = probe(
            "probe.one",
            freshness = AtlasFreshness(AtlasVolatility.INSTANT, observedAtElapsedMs = 0L, bootGeneration = 0L, privilegeGeneration = 0L),
        )
        val plan = scheduler(now = { 60_000L }).plan(listOf(old), 0L, 0L)

        assertEquals(listOf("probe.one"), plan.due.map { it.id })
        assertNull(plan.reasonFor("probe.one"))
    }

    @Test
    fun `a static capability is not re-read for the same boot and is re-read after a privilege change`() {
        val capability = probe(
            "probe.one",
            freshness = AtlasFreshness(AtlasVolatility.STATIC, observedAtElapsedMs = 0L, bootGeneration = 0L, privilegeGeneration = 0L),
        )

        assertTrue(scheduler(now = { 10_000_000L }).plan(listOf(capability), 0L, 0L).due.isEmpty())
        assertEquals(
            listOf("probe.one"),
            scheduler(now = { 10_000_000L }).plan(listOf(capability), 0L, 1L).due.map { it.id },
        )
    }

    @Test
    fun `a suppressed path is skipped and the reason names the cause`() {
        val suppression = AtlasSuppression(AtlasFailure.PERMISSION_DENIED, "denied by policy", 5_000L)
        val plan = scheduler().plan(listOf(probe("probe.one")), 0L, 0L) { path ->
            if (path.endsWith("/temp")) suppression else null
        }

        assertTrue(plan.due.isEmpty())
        assertEquals("${AtlasSkipReasons.SUPPRESSED}:${AtlasFailure.PERMISSION_DENIED}", plan.reasonFor("probe.one"))
    }

    @Test
    fun `the plan runs the cheapest probes first so breadth comes before depth`() {
        val expensive = probe("probe.expensive", opens = 9, bytes = 512)
        val cheap = probe("probe.cheap", opens = 1, bytes = 8)
        val middle = probe("probe.middle", opens = 2, bytes = 16)

        val plan = scheduler().plan(listOf(expensive, cheap, middle), 0L, 0L)

        assertEquals(listOf("probe.cheap", "probe.middle", "probe.expensive"), plan.due.map { it.id })
    }

    @Test
    fun `the plan does not depend on the order of its input`() {
        val probes = listOf(probe("probe.aa", opens = 3), probe("probe.bb", opens = 1), probe("probe.cc", opens = 2))

        assertEquals(scheduler().plan(probes, 0L, 0L), scheduler().plan(probes.reversed(), 0L, 0L))
    }

    @Test
    fun `a probe that does not fit the budget is skipped and the plan stays inside the budget`() {
        val probes = (1..5).map { probe("probe.p$it.a", opens = 2, bytes = 16) }
        val plan = scheduler(operations = 4, bytes = 4_096).plan(probes, 0L, 0L)

        assertEquals(listOf("probe.p1.a", "probe.p2.a"), plan.due.map { it.id })
        assertEquals(4, plan.plannedOperations)
        assertEquals(AtlasSkipReasons.OVER_BUDGET, plan.reasonFor("probe.p3.a"))
        assertTrue(plan.plannedBytes <= 4_096)
    }

    @Test
    fun `a byte budget smaller than a candidate stops the expensive probe`() {
        val plan = scheduler(operations = 8, bytes = 1_024).plan(
            listOf(probe("probe.wide", opens = 1, bytes = 2_048), probe("probe.narrow", opens = 1, bytes = 16)),
            0L,
            0L,
        )

        assertEquals(listOf("probe.narrow"), plan.due.map { it.id })
        assertEquals(AtlasSkipReasons.OVER_BUDGET, plan.reasonFor("probe.wide"))
    }

    @Test
    fun `a plan runs a probe once and its totals are the sum of its due probes`() {
        val once = probe("probe.one")
        assertTrue(
            runCatching { AtlasProbePlan(listOf(once, once), emptyList(), 4, 32) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "a plan cannot claim spending it did not plan",
            runCatching { AtlasProbePlan(listOf(once), emptyList(), 99, 32) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { AtlasProbePlan(listOf(once), listOf(AtlasSkippedProbe("probe.two", "x")), 2, 16) }.isSuccess,
        )
    }

    @Test
    fun `a cost or a probe that cannot be a real attempt is rejected`() {
        assertTrue(runCatching { AtlasProbeCost(0, 16) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { AtlasProbeCost(1, 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            runCatching { probe("BadId") }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { probe("a") }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { probe("probe.one", path = "sys/class/thermal") }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `a negative generation is rejected`() {
        assertTrue(
            runCatching { scheduler().plan(listOf(probe("probe.one")), -1L, 0L) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private fun probe(
        id: String,
        path: String = "/sys/class/thermal/thermal_zone0/temp",
        opens: Int = 2,
        bytes: Int = 16,
        freshness: AtlasFreshness? = null,
    ): AtlasScheduledProbe = AtlasScheduledProbe(id, path, AtlasProbeCost(opens, bytes), freshness)

    private fun scheduler(
        operations: Int = 512,
        bytes: Int = 256 * 1_024,
        now: () -> Long = { 1_000L },
    ): AtlasProbeScheduler = AtlasProbeScheduler(budget(operations, bytes), now)

    private fun budget(operations: Int, bytes: Int): AtlasReadBudget = AtlasReadBudget(
        jobDeadlineMs = 8_000L,
        operationDeadlineMs = 1_000L,
        maxEntriesPerRoot = 4,
        maxEntriesTotal = 16,
        maxScalarBytes = 1_024,
        maxReviewedProcBytes = 2_048,
        maxAggregateBytes = maxOf(bytes, 1_024),
        maxSymlinkHops = 8,
        maxOperations = operations,
        maxConcurrentReads = 2,
        maxConcurrentPrivileged = 1,
    )
}
