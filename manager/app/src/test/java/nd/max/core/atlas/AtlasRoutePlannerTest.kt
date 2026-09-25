/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AtlasRoutePlannerTest {
    @Test
    fun `platform route wins over vendor and sysfs`() {
        val intent = AtlasControlIntent(
            target = AtlasControlTarget.GPU_FREQUENCY,
            goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
            desired = "sustained",
        )
        val decision = AtlasRoutePlanner.choose(
            intent,
            listOf(
                candidate("sysfs", AtlasControlTransport.ARBITER_SYSFS, priority = 0),
                candidate("vendor", AtlasControlTransport.VENDOR_BRIDGE, priority = 0),
                candidate("platform", AtlasControlTransport.PLATFORM_HINT, priority = 10),
            ),
        )

        assertEquals(AtlasRouteStatus.ELIGIBLE, decision.status)
        assertEquals("platform", decision.selected?.id)
        assertEquals(emptyList<Pair<String, AtlasRouteReason>>(), decision.skipped)
    }

    @Test
    fun `read-only evidence can never become an eligible repair route`() {
        val decision = AtlasRoutePlanner.choose(
            AtlasControlIntent(AtlasControlTarget.CPU_FREQUENCY, AtlasControlGoal.PERFORMANCE),
            listOf(candidate("read-only", AtlasControlTransport.READ_ONLY)),
        )

        assertEquals(AtlasRouteStatus.BLOCKED, decision.status)
        assertNull(decision.selected)
        assertEquals(AtlasRouteReason.PRIVILEGE_UNAVAILABLE, decision.reason)
    }

    @Test
    fun `unreviewed route is never promoted by complete-looking evidence`() {
        val decision = AtlasRoutePlanner.choose(
            AtlasControlIntent(AtlasControlTarget.GPU_GOVERNOR, AtlasControlGoal.EFFICIENCY),
            listOf(candidate("candidate", AtlasControlTransport.VENDOR_BRIDGE, reviewed = false)),
        )

        assertEquals(AtlasRouteStatus.REVIEW_REQUIRED, decision.status)
        assertNull(decision.selected)
        assertEquals(AtlasRouteReason.ROUTE_NOT_REVIEWED, decision.reason)
    }

    @Test
    fun `ambiguous provider wins no route`() {
        val decision = AtlasRoutePlanner.choose(
            AtlasControlIntent(AtlasControlTarget.GPU_FREQUENCY, AtlasControlGoal.PERFORMANCE),
            listOf(candidate("ambiguous", AtlasControlTransport.ARBITER_SYSFS, target = AtlasControlTarget.GPU_FREQUENCY, ambiguous = true)),
        )

        assertEquals(AtlasRouteStatus.BLOCKED, decision.status)
        assertEquals(AtlasRouteReason.PROVIDER_AMBIGUOUS, decision.reason)
        assertNull(decision.selected)
    }

    @Test
    fun `route without measurable goal is blocked unless platform owns the hint`() {
        val intent = AtlasControlIntent(AtlasControlTarget.CPU_FREQUENCY, AtlasControlGoal.PERFORMANCE)
        val root = AtlasRoutePlanner.choose(intent, listOf(candidate("root", AtlasControlTransport.ROOT_DAEMON, target = AtlasControlTarget.CPU_FREQUENCY)), false)
        val platform = AtlasRoutePlanner.choose(intent, listOf(candidate("platform", AtlasControlTransport.PLATFORM_HINT, target = AtlasControlTarget.CPU_FREQUENCY)), false)

        assertEquals(AtlasRouteReason.GOAL_UNMEASURABLE, root.reason)
        assertEquals(AtlasRouteStatus.ELIGIBLE, platform.status)
        assertNotNull(platform.selected)
    }

    private fun candidate(
        id: String,
        transport: AtlasControlTransport,
        priority: Int = 0,
        reviewed: Boolean = true,
        ambiguous: Boolean = false,
        target: AtlasControlTarget? = null,
    ) = AtlasRouteCandidate(
        id = id,
        priority = priority,
        evidence = AtlasRouteEvidence(
            providerId = "test-provider",
            transport = transport,
            target = target ?: when {
                id.contains("gpu") || id.contains("vendor") || id.contains("platform") -> AtlasControlTarget.GPU_FREQUENCY
                else -> AtlasControlTarget.CPU_FREQUENCY
            },
            readable = true,
            privilegeAvailable = transport == AtlasControlTransport.PLATFORM_HINT || transport != AtlasControlTransport.READ_ONLY,
            unitProven = true,
            baselineReadable = true,
            rollbackProven = true,
            reviewed = reviewed,
            ambiguous = ambiguous,
        ),
    )
}
