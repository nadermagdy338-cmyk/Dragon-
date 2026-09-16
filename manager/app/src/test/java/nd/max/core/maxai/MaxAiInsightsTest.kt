package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxAiInsightsTest {
    @Test fun `verdict stays learning below minimum samples`() {
        assertEquals(
            MaxAiInsights.Verdict.LEARNING,
            MaxAiInsights.verdictOf(2, 0.2f, 0f),
        )
    }

    @Test fun `helpful verdict requires measured gain`() {
        assertEquals(
            MaxAiInsights.Verdict.PROVEN_HELPFUL,
            MaxAiInsights.verdictOf(3, MaxAiInsights.HELPFUL_GAIN, 0f),
        )
    }

    @Test fun `thermal cost can make an otherwise small gain costly`() {
        assertEquals(
            MaxAiInsights.Verdict.PROVEN_COSTLY,
            MaxAiInsights.verdictOf(4, 0.01f, 2f),
        )
    }

    @Test fun `derive separates decisions probes safety and drift`() {
        val reading = MaxAiReading(40, 38f, 80, 50, 10, true, 0.8f)
        val decision = MaxAiEpisode(
            id = 1L, appContext = "game", objectiveLabel = "performance", objectiveSource = "user",
            weightPerformance = .6f, weightBattery = .2f, weightThermal = .2f, satisfactionTarget = .8f, gap = .1f,
            before = reading, after = reading.copy(objectiveScore = .85f), knobKey = "cpu", knobLabel = "CPU",
            direction = ControlRegistry.Direction.RAISE_PERFORMANCE.name, fromValue = "1", toValue = "2", appliedValue = "2",
            stepFraction = .1f, predictedGain = .04f, predictedThermalC = .2f, predictionConfidence = .7f,
            candidates = emptyList(), verdict = MaxAiVerdict.IMPROVED, detail = "ok", objectiveDelta = .05f,
            thermalDeltaC = .2f, cpuDeltaPercent = 1, batteryDeltaPercent = 0, samplesBefore = 2, samplesAfter = 3,
            confidenceBefore = .2f, confidenceAfter = .3f, predictionErrorGain = .01f, safetyLevel = "NORMAL",
        )
        val probe = decision.copy(id = 2L, kind = MaxAiEpisodeKind.PROBE, exploration = true, reverted = true, verdict = MaxAiVerdict.UNMEASURED, after = null, objectiveDelta = null)
        val safety = decision.copy(id = 3L, kind = MaxAiEpisodeKind.SAFETY, knobKey = null, knobLabel = null, verdict = MaxAiVerdict.BLOCKED_SAFETY, after = null, objectiveDelta = null)
        val drift = decision.copy(id = 4L, kind = MaxAiEpisodeKind.DRIFT, verdict = MaxAiVerdict.NO_ACTION)
        val effect = ControlOutcomeModel.Effect(samples = 3, meanGain = .01f, meanThermal = .1f)
        val snapshot = MaxAiInsights.derive(
            effects = mapOf("cpu|${ControlRegistry.Direction.RAISE_PERFORMANCE.name}|${ControlOutcomeModel.GLOBAL_CONTEXT}" to effect),
            episodes = listOf(decision, probe, safety, drift),
        )
        assertEquals(1, snapshot.improved)
        assertEquals(1, snapshot.explorations)
        assertEquals(1, snapshot.explorationsMeasured)
        assertEquals(1, snapshot.safetyEvents)
        assertEquals(1, snapshot.driftEvents)
        assertEquals(1, snapshot.predictionSamples)
        assertEquals("game", snapshot.busiestContext)
        assertTrue(snapshot.knobs.single().label == "CPU")
        assertNull(MaxAiInsights.derive(emptyMap(), emptyList()).busiestContext)
    }
}
