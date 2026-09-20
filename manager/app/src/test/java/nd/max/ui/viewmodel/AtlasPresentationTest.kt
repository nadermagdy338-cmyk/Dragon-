package nd.max.ui.viewmodel

import nd.max.core.atlas.AtlasAccess
import nd.max.core.atlas.AtlasDomain
import nd.max.core.atlas.AtlasFailure
import nd.max.core.atlas.AtlasFeatureOutcome
import nd.max.core.atlas.AtlasObservation
import nd.max.core.atlas.AtlasScanState
import nd.max.core.atlas.AtlasScanStatus
import nd.max.core.atlas.AtlasSemanticStatus
import nd.max.core.atlas.AtlasStage
import nd.max.core.atlas.AtlasUnit
import nd.max.core.diagnostics.AtlasSupportReport
import nd.max.ui.design.MaxDataTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P7` acceptance tests for the Atlas screen's rules.
 *
 * They are pure on purpose. A rule that can only be checked by rendering a screen is a rule that gets
 * broken silently, and every one of these has a specific falsehood on the other side of it: a made-up
 * `0%` for an unknown total, an "Applied" banner over a read-only pass, a cancelled pass rendered as an
 * absent device, and a report offered from an incomplete run.
 */
class AtlasPresentationTest {

    // ---- progress ------------------------------------------------------------------------------------

    @Test
    fun `an unknown total is not zero percent`() {
        val unknown = AtlasScanState(
            status = AtlasScanStatus.RUNNING,
            totalFeatures = 0,
            outcomes = listOf(observed("cpu.policy.scaling_cur_freq")),
        )
        val known = unknown.copy(totalFeatures = 4)

        assertNull("a bar without a length has no percentage", AtlasPresentation.percent(unknown))
        assertEquals(25, AtlasPresentation.percent(known))
    }

    @Test
    fun `progress is counted in interfaces and never in time`() {
        val state = AtlasScanState(status = AtlasScanStatus.RUNNING, totalFeatures = 8)
        assertEquals(0, AtlasPresentation.percent(state))

        // Holding the same state for longer changes nothing: there is no clock in the computation.
        assertEquals(0, AtlasPresentation.percent(state.copy(outcomes = emptyList())))
    }

    // ---- conditions ----------------------------------------------------------------------------------

    @Test
    fun `a completed read-only pass shows no applied banner`() {
        val complete = AtlasScanState(
            status = AtlasScanStatus.COMPLETED,
            totalFeatures = 1,
            outcomes = listOf(observed("cpu.policy.scaling_cur_freq")),
        )

        assertNull("nothing was applied, so nothing is announced", AtlasPresentation.condition(complete, limitsReached = false))
        assertTrue(
            "a read-only pass has no applying/applied state to render at all",
            AtlasConditionCode.entries.none { it.name.startsWith("Appl") },
        )
    }

    @Test
    fun `cancellation is not exhaustion`() {
        val cancelled = AtlasScanState(status = AtlasScanStatus.CANCELLED, totalFeatures = 3)
        val unavailable = AtlasScanState(
            status = AtlasScanStatus.UNAVAILABLE,
            totalFeatures = 3,
            outcomes = listOf(unresolved("cpu.policy.scaling_cur_freq", AtlasFailure.BACKEND_UNAVAILABLE)),
        )

        assertEquals(AtlasUiPhase.Cancelled, AtlasPresentation.phase(cancelled))
        assertNotEquals(
            "leaving the screen and a device with no answer are different facts",
            AtlasPresentation.condition(unavailable, limitsReached = false),
            AtlasPresentation.condition(cancelled, limitsReached = false),
        )
    }

    @Test
    fun `a bound that stopped the pass is reported as the bound`() {
        val stopped = AtlasScanState(
            status = AtlasScanStatus.PARTIAL,
            totalFeatures = 4,
            outcomes = listOf(unresolved("thermal.zone.temp", AtlasFailure.BUDGET_EXCEEDED)),
        )

        assertEquals(AtlasConditionCode.LimitReached, AtlasPresentation.condition(stopped, limitsReached = true))
        // Without the bound, the same state is an honest partial — the difference is a fact about us,
        // not about the device, so it must not be inferred from the outcomes.
        assertEquals(AtlasConditionCode.Partial, AtlasPresentation.condition(stopped, limitsReached = false))
    }

    // ---- report gating -------------------------------------------------------------------------------

    @Test
    fun `a running pass offers no report`() {
        val running = AtlasScanState(status = AtlasScanStatus.RUNNING, totalFeatures = 2)
        assertEquals(AtlasReportRefusal.NotFinished, AtlasPresentation.refusalFor(running, limitsReached = false))
        assertFalse(AtlasPresentation.offersReport(running, limitsReached = false))
    }

    @Test
    fun `a cancelled pass offers no report even when it has gaps`() {
        val cancelled = AtlasScanState(
            status = AtlasScanStatus.CANCELLED,
            totalFeatures = 2,
            outcomes = listOf(unresolved("cpu.policy.scaling_cur_freq", AtlasFailure.PERMISSION_DENIED)),
        )
        assertEquals(AtlasReportRefusal.WasCancelled, AtlasPresentation.refusalFor(cancelled, limitsReached = false))
    }

    @Test
    fun `a bound-stopped pass offers a retry and not a report`() {
        val stopped = AtlasScanState(
            status = AtlasScanStatus.PARTIAL,
            totalFeatures = 2,
            outcomes = listOf(unresolved("cpu.policy.scaling_cur_freq", AtlasFailure.TIMED_OUT)),
        )
        assertEquals(AtlasReportRefusal.BoundReached, AtlasPresentation.refusalFor(stopped, limitsReached = true))
        assertFalse(AtlasPresentation.offersReport(stopped, limitsReached = true))
    }

    @Test
    fun `a device whose reviewed interfaces all answered needs no report`() {
        val complete = AtlasScanState(
            status = AtlasScanStatus.COMPLETED,
            totalFeatures = 1,
            outcomes = listOf(observed("cpu.policy.scaling_cur_freq")),
        )
        assertEquals(AtlasReportRefusal.Refused, AtlasPresentation.refusalFor(complete, limitsReached = false))
        assertFalse(AtlasPresentation.offersReport(complete, limitsReached = false))
    }

    @Test
    fun `a reviewed gap opens the report path`() {
        val partial = AtlasScanState(
            status = AtlasScanStatus.PARTIAL,
            totalFeatures = 2,
            outcomes = listOf(
                observed("cpu.policy.scaling_cur_freq"),
                unresolved("thermal.zone.temp", AtlasFailure.ABSENT),
            ),
        )
        assertNull(AtlasPresentation.refusalFor(partial, limitsReached = false))
        assertTrue(AtlasPresentation.offersReport(partial, limitsReached = false))
    }

    @Test
    fun `an oversized outcome list is refused before the artifact exists`() {
        val huge = AtlasScanState(
            status = AtlasScanStatus.PARTIAL,
            totalFeatures = AtlasSupportReport.MAX_FEATURES + 1,
            outcomes = (0..AtlasSupportReport.MAX_FEATURES).map {
                unresolved("cpu.policy.scaling_cur_freq", AtlasFailure.ABSENT)
            },
        )
        assertEquals(
            AtlasReportRefusal.TooManyInterfaces,
            AtlasPresentation.refusalFor(huge, limitsReached = false),
        )
    }

    // ---- tier counting and trust ---------------------------------------------------------------------

    @Test
    fun `the two evidence tiers are counted apart`() {
        val state = AtlasScanState(
            status = AtlasScanStatus.PARTIAL,
            totalFeatures = 4,
            outcomes = listOf(
                observed("cpu.policy.scaling_cur_freq", AtlasStage.REVIEWED_KNOWLEDGE),
                observed("gpu.devfreq.cur_freq", AtlasStage.BOUNDED_DISCOVERY),
                observed("gpu.kgsl.gpuclk", AtlasStage.CANDIDATE_INTERFACE),
                unresolved("thermal.zone.temp", AtlasFailure.ABSENT),
            ),
        )

        assertEquals("a candidate reading is never counted as reviewed", 2, AtlasPresentation.reviewedReadings(state))
        assertEquals(1, AtlasPresentation.candidateReadings(state))
        assertEquals(1, AtlasPresentation.reviewedGaps(state))
    }

    @Test
    fun `a one-shot reading is never shown as live`() {
        val reading = observed("cpu.policy.scaling_cur_freq")

        assertEquals(MaxDataTrust.Snapshot, AtlasPresentation.trust(reading))
        assertNotEquals(
            "Atlas has no sampling interval, so nothing it reads is live",
            MaxDataTrust.Live,
            AtlasPresentation.trust(reading),
        )
    }

    @Test
    fun `a missing interface and a refused one are different trust states`() {
        assertEquals(
            MaxDataTrust.Unsupported,
            AtlasPresentation.trust(unresolved("thermal.zone.temp", AtlasFailure.ABSENT)),
        )
        assertEquals(
            MaxDataTrust.Unreadable,
            AtlasPresentation.trust(unresolved("thermal.zone.temp", AtlasFailure.PERMISSION_DENIED)),
        )
        assertEquals(
            MaxDataTrust.Unreadable,
            AtlasPresentation.trust(AtlasFeatureOutcome.Suppressed("cpu.policy.scaling_cur_freq", AtlasFailure.PERMISSION_DENIED, "recent refusal")),
        )
    }

    // ---- fixtures --------------------------------------------------------------------------------------

    private fun observed(
        id: String,
        stage: AtlasStage = AtlasStage.BOUNDED_DISCOVERY,
    ): AtlasFeatureOutcome.Observed = AtlasFeatureOutcome.Observed(
        id = id,
        observation = AtlasObservation(
            id = id,
            domain = AtlasDomain.CPU,
            providerId = "test",
            catalogVersion = "atlas-catalog-1",
            sourceId = "L02",
            path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq",
            access = AtlasAccess.READABLE,
            semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
            unit = AtlasUnit.KILO_HERTZ,
            value = 1_800_000.0,
            textValue = null,
            rawRepresentation = null,
            failure = AtlasFailure.NONE,
            reason = "read",
            observedAtElapsedMs = 0L,
            bootGeneration = 0L,
            privilegeGeneration = 0L,
            truncated = false,
        ),
        stage = stage,
        fromCache = false,
    )

    private fun unresolved(id: String, failure: AtlasFailure): AtlasFeatureOutcome.Unresolved =
        AtlasFeatureOutcome.Unresolved(
            id = id,
            failure = failure,
            reason = "cause-${failure.name.lowercase()}",
            stage = AtlasStage.BOUNDED_DISCOVERY,
        )
}
