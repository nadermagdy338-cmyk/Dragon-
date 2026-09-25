/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد استخلاص المعرفة (NT-04 + سياق التعلّم + صدق التنبؤ).
 *
 * ما يُحرس هنا هو أخطر ما يقوله التطبيق للمستخدم: «هذا المقبض نافع»، «هذا
 * المقبض مكلف»، «تنبؤاتي صادقة». أي انحدار في هذه الأحكام يجعل الواجهة
 * تكذب بثقة، وهي أسوأ من ألا تقول شيئًا. كل الدوال نقية فوق بيانات مقيسة
 * (خرائط الأثر + حلقات الدفتر)، فلا تحتاج Android ولا تشغيلًا على جهاز.
 */
class MaxAiInsightsTest {

    // ── الأحكام: لا حكم بلا عينات ────────────────────────────────

    @Test
    fun `a single sample stays learning`() {
        assertEquals(
            MaxAiInsights.Verdict.LEARNING,
            MaxAiInsights.verdictOf(samples = 1, meanGain = 0.5f, meanThermalC = 0f),
        )
    }

    @Test
    fun `enough samples with a real gain is proven helpful`() {
        assertEquals(
            MaxAiInsights.Verdict.PROVEN_HELPFUL,
            MaxAiInsights.verdictOf(
                samples = MaxAiInsights.MIN_SAMPLES_FOR_VERDICT,
                meanGain = MaxAiInsights.HELPFUL_GAIN,
                meanThermalC = 0f,
            ),
        )
    }

    @Test
    fun `heat without a matching gain is proven costly`() {
        assertEquals(
            MaxAiInsights.Verdict.PROVEN_COSTLY,
            MaxAiInsights.verdictOf(
                samples = 5,
                meanGain = 0.01f,
                meanThermalC = MaxAiInsights.COSTLY_THERMAL_C + 0.1f,
            ),
        )
    }

    @Test
    fun `a proven loss is costly even when the device stays cool`() {
        assertEquals(
            MaxAiInsights.Verdict.PROVEN_COSTLY,
            MaxAiInsights.verdictOf(
                samples = 5,
                meanGain = -MaxAiInsights.HELPFUL_GAIN,
                meanThermalC = 0f,
            ),
        )
    }

    @Test
    fun `flat measurements are inconclusive rather than successful`() {
        assertEquals(
            MaxAiInsights.Verdict.INCONCLUSIVE,
            MaxAiInsights.verdictOf(samples = 6, meanGain = 0f, meanThermalC = 0f),
        )
    }

    // ── السياق: العام لا يُخلط بالخاص (I-46) ──────────────────────

    @Test
    fun `device wide verdicts exclude app contexts and are never averaged with them`() {
        val effects = mapOf(
            effectKey("cpu.max", ControlRegistry.Direction.RAISE_PERFORMANCE, ControlOutcomeModel.GLOBAL_CONTEXT) to
                effect(samples = 4, gain = 0.01f),
            effectKey("cpu.max", ControlRegistry.Direction.RAISE_PERFORMANCE, "com.game.one") to
                effect(samples = 4, gain = 2.0f),
        )

        val snapshot = MaxAiInsights.derive(effects, emptyList())

        assertEquals("العام يحمل سجله وحده", 1, snapshot.knobs.size)
        assertEquals(
            MaxAiInsights.Verdict.PROVEN_HELPFUL,
            snapshot.knobs.single().verdict,
        )
        assertEquals(0.01f, snapshot.knobs.single().meanGain, 0.0001f)
        assertEquals(2, snapshot.knobsByContext.size)
        assertEquals(
            "حكم التطبيق لا يستعير أرقام العام",
            2.0f,
            snapshot.knobsIn("com.game.one").single().meanGain,
            0.0001f,
        )
    }

    @Test
    fun `contexts list puts the device first then the busiest app`() {
        val effects = mapOf(
            effectKey("gpu.max", ControlRegistry.Direction.RAISE_PERFORMANCE, "com.small.app") to
                effect(samples = 1),
            effectKey("cpu.max", ControlRegistry.Direction.RAISE_PERFORMANCE, "com.big.app") to
                effect(samples = 9),
            effectKey("cpu.max", ControlRegistry.Direction.RAISE_PERFORMANCE, ControlOutcomeModel.GLOBAL_CONTEXT) to
                effect(samples = 2),
        )

        val snapshot = MaxAiInsights.derive(effects, emptyList())

        assertEquals(
            listOf(ControlOutcomeModel.GLOBAL_CONTEXT, "com.big.app", "com.small.app"),
            snapshot.contexts,
        )
    }

    @Test
    fun `context ordering is deterministic for equal sample counts`() {
        val insights = listOf(
            insight("b.knob", context = "b.app", samples = 3),
            insight("a.knob", context = "a.app", samples = 3),
            insight("g.knob", context = ControlOutcomeModel.GLOBAL_CONTEXT, samples = 1),
        )

        assertEquals(
            listOf(ControlOutcomeModel.GLOBAL_CONTEXT, "a.app", "b.app"),
            MaxAiInsights.contextOrder(insights),
        )
    }

    @Test
    fun `zero sample effects and unknown directions are dropped instead of shown as verdicts`() {
        val effects = mapOf(
            effectKey("cpu.max", ControlRegistry.Direction.RAISE_PERFORMANCE, ControlOutcomeModel.GLOBAL_CONTEXT) to
                effect(samples = 0),
            "cpu.max|NOT_A_DIRECTION|*" to effect(samples = 5),
            "missing|parts" to effect(samples = 5),
        )

        val snapshot = MaxAiInsights.derive(effects, emptyList())

        assertTrue(snapshot.knobs.isEmpty())
        assertTrue(snapshot.contexts.isEmpty())
        assertEquals(0L, snapshot.totalSamples)
    }

    // ── صدق التنبؤ ───────────────────────────────────────────────

    @Test
    fun `no prediction ever compared with a measurement reports learning not a zero score`() {
        val audit = MaxAiInsights.predictAudit(listOf(episode(id = 1L)))

        assertEquals(0, audit.samples)
        assertNull(audit.meanAbsError)
        assertNull(audit.hitRate)
        assertEquals(MaxAiInsights.Calibration.LEARNING, audit.calibration)
    }

    @Test
    fun `errors inside the declared tolerance count as hits and calibrate the model`() {
        val audits = listOf(
            episode(id = 1L, predictionError = 0f, confidence = 0.5f),
            episode(id = 2L, predictionError = MaxAiInsights.PREDICTION_TOLERANCE_GAIN, confidence = 0.7f),
            episode(id = 3L, predictionError = 0.001f, confidence = null),
        ).let { MaxAiInsights.predictAudit(it) }

        assertEquals(3, audits.samples)
        assertEquals(1f, audits.hitRate!!, 0.0001f)
        assertEquals(
            MaxAiInsights.Calibration.CALIBRATED,
            audits.calibration,
        )
        assertEquals("الثقة المتوسطة تُقرأ من العينات التي أعلنتها فقط", 0.6f, audits.meanConfidence!!, 0.0001f)
    }

    @Test
    fun `large errors with enough samples report drift`() {
        val episodes = (1L..4L).map { episode(id = it, predictionError = 0.4f) }
        val audit = MaxAiInsights.predictAudit(episodes)

        assertEquals(4, audit.samples)
        assertEquals(0f, audit.hitRate!!, 0.0001f)
        assertEquals(MaxAiInsights.Calibration.DRIFTING, audit.calibration)
    }

    @Test
    fun `tolerance is derived from the usefulness threshold, not hard coded twice`() {
        assertEquals(MaxAiInsights.HELPFUL_GAIN * 5f, MaxAiInsights.PREDICTION_TOLERANCE_GAIN, 0f)
    }

    // ── النسب: مقام صفري ليس صفرًا ────────────────────────────────

    @Test
    fun `ratios are unknown until the journal holds an acted loop`() {
        val snapshot = MaxAiInsights.derive(emptyMap(), listOf(episode(id = 1L)))

        assertEquals(0, snapshot.actedEpisodes)
        assertNull(snapshot.successRate)
        assertNull(snapshot.overrideRate)
        assertNull(snapshot.measurementRate)
        assertNull(snapshot.probeYield)
    }

    @Test
    fun `probe yield and override rate count measured facts only`() {
        val episodes = listOf(
            episode(id = 5L, verdict = MaxAiVerdict.IMPROVED, knobKey = "cpu.max", after = reading()),
            episode(id = 4L, verdict = MaxAiVerdict.UNMEASURED, knobKey = "gpu.max", overrideAt = 4_000L),
            probe(id = 3L, measured = true),
            probe(id = 2L, measured = false),
        )

        val snapshot = MaxAiInsights.derive(emptyMap(), episodes)

        assertEquals(2, snapshot.actedEpisodes)
        assertEquals(0.5f, snapshot.successRate!!, 0.0001f)
        assertEquals(0.5f, snapshot.probeYield!!, 0.0001f)
        assertEquals(0.5f, snapshot.overrideRate!!, 0.0001f)
        assertEquals(1, snapshot.userOverrides)
    }

    // ── بنّاءات الاختبار ────────────────────────────────────────────

    private fun effectKey(
        key: String,
        direction: ControlRegistry.Direction,
        context: String,
    ): String = "$key|${direction.name}|$context"

    private fun effect(samples: Int, gain: Float = 0.01f, thermal: Float = 0f) =
        ControlOutcomeModel.Effect(
            samples = samples,
            meanGain = gain,
            m2Gain = 0f,
            meanThermal = thermal,
        )

    private fun reading() = MaxAiReading(
        cpuLoadPercent = 10,
        thermalC = 38f,
        batteryPercent = 70,
        memoryPercent = 50,
        networkPercent = 0,
        screenOn = true,
        objectiveScore = 0.5f,
    )

    private fun insight(key: String, context: String, samples: Int) = MaxAiInsights.KnobInsight(
        key = key,
        label = key,
        direction = ControlRegistry.Direction.RAISE_PERFORMANCE,
        samples = samples,
        meanGain = 0.01f,
        meanThermalC = 0f,
        confidence = 0.5f,
        verdict = MaxAiInsights.Verdict.PROVEN_HELPFUL,
        context = context,
    )

    private fun episode(
        id: Long,
        verdict: MaxAiVerdict = MaxAiVerdict.NO_ACTION,
        knobKey: String? = null,
        after: MaxAiReading? = null,
        overrideAt: Long? = null,
        predictionError: Float? = null,
        confidence: Float? = null,
    ) = MaxAiEpisode(
        id = id,
        appContext = ControlOutcomeModel.GLOBAL_CONTEXT,
        objectiveLabel = "balanced",
        objectiveSource = "learned",
        weightPerformance = 0.4f,
        weightBattery = 0.3f,
        weightThermal = 0.3f,
        satisfactionTarget = MinimalPlanner.SATISFIED_SCORE,
        gap = 0.1f,
        before = reading(),
        after = after,
        knobKey = knobKey,
        knobLabel = knobKey,
        direction = ControlRegistry.Direction.RAISE_PERFORMANCE.name,
        fromValue = "1",
        toValue = "2",
        appliedValue = "2",
        stepFraction = 0.1f,
        predictedGain = predictionError?.let { 0.02f },
        predictedThermalC = null,
        predictionConfidence = confidence,
        candidates = emptyList(),
        verdict = verdict,
        detail = "test",
        objectiveDelta = if (after != null) 0.02f else null,
        thermalDeltaC = null,
        cpuDeltaPercent = null,
        batteryDeltaPercent = null,
        samplesBefore = 0,
        samplesAfter = 1,
        confidenceBefore = 0f,
        confidenceAfter = 0.1f,
        predictionErrorGain = predictionError,
        safetyLevel = "NORMAL",
        userOverrideAtMs = overrideAt,
    )

    private fun probe(id: Long, measured: Boolean) = episode(
        id = id,
        verdict = MaxAiVerdict.UNMEASURED,
        knobKey = "cpu.max",
        after = if (measured) reading() else null,
    ).copy(kind = MaxAiEpisodeKind.PROBE, exploration = true)
}
