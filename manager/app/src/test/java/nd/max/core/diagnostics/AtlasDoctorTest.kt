package nd.max.core.diagnostics

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doctor tests (`P13`).
 *
 * The doctor is the gate that decides whether a support change is justified by evidence, so the tests
 * are written around the ways it could be *wrong* rather than the happy path it could be shown to pass:
 * a partial run that would look like a device change, a catalog revision that makes a comparison
 * meaningless, and a candidate interface whose absence is expected and must not open a support loop.
 */
class AtlasDoctorTest {

    private val version = "atlas-test-1"

    @Test
    fun `a replay of the same device reproduces the report`() {
        val verdict = AtlasDoctor.compare(
            report = report(reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ")),
            state = state(observed("cpu.cpufreq.cur")),
            catalogVersion = version,
        )

        assertEquals(AtlasDoctor.Verdict.Reproduced(features = 1, advisories = emptyList()), verdict)
    }

    @Test
    fun `a descriptive reason is not a behaviour change`() {
        // Reason strings say how a result was reached ("cause-absent" versus "suppressed-by-recent-attempt").
        // Comparing them would report a bookkeeping edit as a device change, so the signature does not.
        val verdict = AtlasDoctor.compare(
            report = report(
                reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ", reason = "from-held-evidence:after-absent"),
            ),
            state = state(observed("cpu.cpufreq.cur")),
            catalogVersion = version,
        )

        assertTrue("$verdict", verdict is AtlasDoctor.Verdict.Reproduced)
    }

    @Test
    fun `a different cause is reported with both sides`() {
        val verdict = AtlasDoctor.compare(
            report = report(reported("thermal.zone0.temp", "unresolved", failure = "ABSENT")),
            state = state(unresolved("thermal.zone0.temp", AtlasFailure.PERMISSION_DENIED)),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Diverged

        assertEquals(1, verdict.differences.size)
        val difference = verdict.differences.single()
        assertEquals(AtlasDoctor.Kind.CAUSE_CHANGED, difference.kind)
        assertEquals("thermal.zone0.temp", difference.id)
        assertTrue("the report side must be named: ${difference.reported}", difference.reported.contains("ABSENT"))
        assertTrue("the replay side must be named: ${difference.replayed}", difference.replayed.contains("PERMISSION_DENIED"))
    }

    @Test
    fun `a feature the replay did not produce is a missing feature, not a silent match`() {
        val verdict = AtlasDoctor.compare(
            report = report(reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ")),
            state = state(),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Diverged

        assertEquals(AtlasDoctor.Kind.MISSING_IN_REPLAY, verdict.differences.single().kind)
        assertEquals("nothing", verdict.differences.single().replayed)
    }

    @Test
    fun `a feature the report never mentioned is reported as extra`() {
        val verdict = AtlasDoctor.compare(
            report = report(),
            state = state(observed("cpu.cpufreq.cur")),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Diverged

        assertEquals(AtlasDoctor.Kind.EXTRA_IN_REPLAY, verdict.differences.single().kind)
        assertEquals("nothing", verdict.differences.single().reported)
    }

    @Test
    fun `a changed unit is its own kind of difference`() {
        val verdict = AtlasDoctor.compare(
            report = report(reported("cpu.cpufreq.cur", "observed", unit = "HERTZ")),
            state = state(observed("cpu.cpufreq.cur", unit = AtlasUnit.KILO_HERTZ)),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Diverged

        assertEquals(AtlasDoctor.Kind.UNIT_CHANGED, verdict.differences.single().kind)
    }

    @Test
    fun `an outcome that flipped is an outcome change, not a cause change`() {
        val verdict = AtlasDoctor.compare(
            report = report(reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ")),
            state = state(unresolved("cpu.cpufreq.cur", AtlasFailure.ABSENT)),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Diverged

        assertEquals(AtlasDoctor.Kind.OUTCOME_CHANGED, verdict.differences.single().kind)
    }

    @Test
    fun `a candidate interface that is absent is advice, never divergence`() {
        val verdict = AtlasDoctor.compare(
            report = report(
                reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ"),
                reported("vendor.mtk.gpu.busy", "unresolved", failure = "ABSENT", stage = "CANDIDATE_INTERFACE"),
            ),
            state = state(observed("cpu.cpufreq.cur")),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Reproduced

        assertEquals("both reported features were accounted for", 2, verdict.features)
        assertEquals(
            "a name the community knows being absent on this device is the expected case",
            listOf("vendor.mtk.gpu.busy"),
            verdict.advisories.map { it.id },
        )
    }

    @Test
    fun `an unfinished, cancelled or absent replay is refused instead of compared`() {
        val report = report(reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ"))

        listOf(
            AtlasScanStatus.IDLE to "has not finished",
            AtlasScanStatus.RUNNING to "has not finished",
            AtlasScanStatus.CANCELLED to "cancelled",
        ).forEach { (status, expected) ->
            val verdict = AtlasDoctor.compare(report, state(status = status), version) as AtlasDoctor.Verdict.Refused
            assertTrue("$status must be refused: ${verdict.reason}", verdict.reason.contains(expected))
        }
    }

    @Test
    fun `a comparison across catalog revisions is refused`() {
        val verdict = AtlasDoctor.compare(
            report = report(reported("cpu.cpufreq.cur", "observed", unit = "KILO_HERTZ"), catalog = "atlas-0"),
            state = state(observed("cpu.cpufreq.cur")),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Refused

        assertTrue(
            "the two revisions must both be named: ${verdict.reason}",
            verdict.reason.contains("atlas-0") && verdict.reason.contains(version),
        )
    }

    @Test
    fun `differences are ordered, so two runs of the same comparison read the same`() {
        val verdict = AtlasDoctor.compare(
            report = report(
                reported("thermal.zone0.temp", "unresolved", failure = "ABSENT"),
                reported("cpu.cpufreq.cur", "unresolved", failure = "ABSENT"),
            ),
            state = state(
                unresolved("thermal.zone0.temp", AtlasFailure.PERMISSION_DENIED),
                unresolved("cpu.cpufreq.cur", AtlasFailure.PERMISSION_DENIED),
            ),
            catalogVersion = version,
        ) as AtlasDoctor.Verdict.Diverged

        assertEquals(listOf("cpu.cpufreq.cur", "thermal.zone0.temp"), verdict.differences.map { it.id })
    }

    // ---- builders --------------------------------------------------------------------------------

    private fun reported(
        id: String,
        outcome: String,
        failure: String? = null,
        unit: String? = null,
        stage: String = AtlasStage.REVIEWED_KNOWLEDGE.name,
        reason: String = "cause-absent",
    ) = AtlasSupportReport.ReportedFeature(
        id = id,
        stage = stage,
        outcome = outcome,
        failure = failure,
        unit = unit,
        ageMs = null,
        reason = reason,
    )

    private fun report(
        vararg features: AtlasSupportReport.ReportedFeature,
        catalog: String = version,
    ) = AtlasSupportReport(
        appVersion = "1.0",
        catalogVersion = catalog,
        device = AtlasSupportReport.ReportedDevice(
            socModel = null,
            socManufacturer = null,
            apiLevel = 34,
            abi = "arm64-v8a",
            kernelMajorMinor = null,
            lowRamDevice = null,
        ),
        summary = AtlasSupportReport.ReportedSummary(
            status = AtlasScanStatus.COMPLETED.name,
            percent = 100,
            totalFeatures = features.size,
            observed = features.size,
            unresolved = 0,
            cacheHits = 0,
            suppressed = 0,
            limitsReached = false,
        ),
        features = features.toList(),
    )

    private fun state(
        vararg outcomes: AtlasFeatureOutcome,
        status: AtlasScanStatus = AtlasScanStatus.COMPLETED,
    ) = AtlasScanState(
        status = status,
        generation = 1L,
        totalFeatures = outcomes.size,
        outcomes = outcomes.toList(),
    )

    private fun observed(
        id: String,
        unit: AtlasUnit = AtlasUnit.KILO_HERTZ,
        stage: AtlasStage = AtlasStage.REVIEWED_KNOWLEDGE,
    ) = AtlasFeatureOutcome.Observed(
        id = id,
        observation = AtlasObservation(
            id = id,
            domain = AtlasDomain.CPU,
            providerId = "local",
            catalogVersion = version,
            sourceId = "reviewed-1",
            path = "/sys/class/devfreq/gpu/cur_freq",
            access = AtlasAccess.READABLE,
            semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
            unit = unit,
            value = 1_800_000_000.0,
            textValue = null,
            rawRepresentation = "1800000000",
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

    private fun unresolved(
        id: String,
        failure: AtlasFailure,
        stage: AtlasStage = AtlasStage.REVIEWED_KNOWLEDGE,
    ) = AtlasFeatureOutcome.Unresolved(
        id = id,
        failure = failure,
        reason = "cause-${failure.name.lowercase()}",
        stage = stage,
    )
}
