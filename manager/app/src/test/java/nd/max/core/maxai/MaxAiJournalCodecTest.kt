package nd.max.core.maxai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حراسة التسلسل — ذهاب/عودة على كل حقل.
 *
 * سبب وجود هذا الاختبار محدد: الدفتر يُكتب نصًا ويُقرأ نصًا، فأي حقل
 * جديد يُضاف إلى [MaxAiEpisode] ولا يُضاف إلى [MaxAiJournalCodec] يُفقد
 * صامتًا عند أول إعادة تشغيل — بلا استثناء وبلا سطر في السجل. المقارنة
 * هنا على الكائن كاملًا (data class equals) كي يفشل الاختبار تلقائيًا
 * عند إضافة حقل غير مُرمَّز.
 */
class MaxAiJournalCodecTest {

    private fun reading(seed: Float) = MaxAiReading(
        cpuLoadPercent = 41,
        thermalC = 38.5f + seed,
        batteryPercent = 77,
        memoryPercent = 62,
        networkPercent = 12,
        screenOn = true,
        objectiveScore = 0.61f + seed,
    )

    private fun candidate(chosen: Boolean) = MaxAiCandidate(
        key = "cpu_max_freq",
        label = "سقف ترددات المعالج",
        from = "1800000",
        to = "1600000",
        utility = 0.42f,
        credibility = 0.55f,
        predictedGain = 0.013f,
        predictedThermalC = -0.8f,
        predictionConfidence = 0.34f,
        samples = 5,
        rejection = if (chosen) null else MaxAiRejection.PREDICTED_HARM,
        chosen = chosen,
    )

    /** حلقة قرار كاملة: كل حقل مملوء بقيمة مميزة، بلا افتراضات. */
    private fun fullEpisode() = MaxAiEpisode(
        id = 1_726_000_000_000L,
        appContext = "com.example.game",
        objectiveLabel = "أداء",
        objectiveSource = "learned",
        weightPerformance = 0.6f,
        weightBattery = 0.25f,
        weightThermal = 0.15f,
        satisfactionTarget = 0.8f,
        gap = 0.19f,
        before = reading(0f),
        after = reading(0.05f),
        knobKey = "cpu_max_freq",
        knobLabel = "سقف ترددات المعالج",
        direction = "DOWN",
        fromValue = "1800000",
        toValue = "1600000",
        appliedValue = "1600000",
        stepFraction = 0.25f,
        predictedGain = 0.013f,
        predictedThermalC = -0.8f,
        predictionConfidence = 0.34f,
        candidates = listOf(candidate(chosen = true), candidate(chosen = false)),
        verdict = MaxAiVerdict.IMPROVED,
        detail = "verified write :: 1600000",
        objectiveDelta = 0.05f,
        thermalDeltaC = -0.4f,
        cpuDeltaPercent = -6,
        batteryDeltaPercent = 0,
        samplesBefore = 5,
        samplesAfter = 6,
        confidenceBefore = 0.31f,
        confidenceAfter = 0.38f,
        predictionErrorGain = 0.037f,
        safetyLevel = "NORMAL",
        exploration = false,
        reverted = false,
        knowledgeBefore = TrustModel.Knowledge.UNCERTAIN.name,
        knowledgeAfter = TrustModel.Knowledge.KNOWN_USEFUL.name,
        epistemicBefore = 0.09f,
        epistemicAfter = 0.06f,
        informationGain = 0.21f,
        probeCost = 0.12f,
        probeBlockReason = TrustModel.Block.COOLDOWN,
        kind = MaxAiEpisodeKind.DECISION,
        userOverrideAtMs = 1_726_000_240_000L,
        userOverrideKind = MaxAiOverride.LOCK,
        deferredAtMs = 1_726_000_900_000L,
        deferredBatteryDeltaPercent = -3,
        deferredThermalDeltaC = 0.7f,
        deferredObjectiveDelta = 0.02f,
    )

    /** حلقة نظام (سلامة): كل ما لا يُقاس يبقى null ويجب أن يعود null. */
    private fun systemEpisode() = MaxAiEpisode(
        id = 1_726_000_500_000L,
        appContext = "system",
        objectiveLabel = "متوازن",
        objectiveSource = "screen_off",
        weightPerformance = 0.34f,
        weightBattery = 0.33f,
        weightThermal = 0.33f,
        satisfactionTarget = 0.8f,
        gap = 0.04f,
        before = reading(0.2f),
        after = null,
        knobKey = null,
        knobLabel = null,
        direction = null,
        fromValue = null,
        toValue = null,
        appliedValue = null,
        stepFraction = 0f,
        predictedGain = null,
        predictedThermalC = null,
        predictionConfidence = null,
        candidates = emptyList(),
        verdict = MaxAiVerdict.BLOCKED_SAFETY,
        detail = "thermal 49.2C :: الفرض APPLIED",
        objectiveDelta = null,
        thermalDeltaC = null,
        cpuDeltaPercent = null,
        batteryDeltaPercent = null,
        samplesBefore = 0,
        samplesAfter = 0,
        confidenceBefore = 0f,
        confidenceAfter = 0f,
        predictionErrorGain = null,
        safetyLevel = "ENGAGED",
        kind = MaxAiEpisodeKind.SAFETY,
    )

    @Test
    fun `full episode survives encode then decode`() {
        val original = fullEpisode()
        val decoded = MaxAiJournalCodec.decodeEpisode(
            MaxAiJournalCodec.encodeEpisode(original)
        )
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `system episode keeps unmeasured fields null`() {
        val original = systemEpisode()
        val decoded = MaxAiJournalCodec.decodeEpisode(
            MaxAiJournalCodec.encodeEpisode(original)
        )
        assertEquals(original, decoded)
        assertEquals(null, decoded?.after)
        assertEquals(null, decoded?.objectiveDelta)
        assertEquals(null, decoded?.predictionErrorGain)
    }

    @Test
    fun `journal file round trip preserves order and every episode`() {
        val episodes = listOf(fullEpisode(), systemEpisode())
        val text = MaxAiJournalCodec.encodeAll(episodes)
        assertEquals(episodes, MaxAiJournalCodec.decodeAll(text))
    }

    @Test
    fun `probe episode keeps exploration fields`() {
        val probe = fullEpisode().copy(
            kind = MaxAiEpisodeKind.PROBE,
            exploration = true,
            reverted = true,
            verdict = MaxAiVerdict.UNMEASURED,
            userOverrideAtMs = null,
            userOverrideKind = null,
        )
        val decoded = MaxAiJournalCodec.decodeAll(MaxAiJournalCodec.encodeAll(listOf(probe)))
        assertEquals(listOf(probe), decoded)
        assertTrue(decoded.first().exploration)
        assertTrue(decoded.first().reverted)
        assertEquals(MaxAiEpisodeKind.PROBE, decoded.first().kind)
    }

    /**
     * دفتر قديم مكتوب قبل إضافة [MaxAiEpisodeKind]: لا يجوز أن يُفقد،
     * والنوع يُستنتج من راية exploration وحدها لأنها كل ما كان مسجلًا.
     */
    @Test
    fun `legacy episode without kind is decoded and inferred`() {
        val legacy = MaxAiJournalCodec.encodeEpisode(fullEpisode())
        legacy.remove("kind")
        legacy.remove("ovAt")
        legacy.remove("ovKind")
        legacy.remove("dfAt")
        legacy.remove("dfBat")
        legacy.remove("dfTemp")
        legacy.remove("dfScore")
        val decoded = MaxAiJournalCodec.decodeEpisode(legacy)
        assertNotNull(decoded)
        assertEquals(MaxAiEpisodeKind.DECISION, decoded?.kind)
        assertEquals(null, decoded?.userOverrideAtMs)
        assertEquals(null, decoded?.deferredAtMs)

        val legacyProbe = MaxAiJournalCodec.encodeEpisode(
            fullEpisode().copy(exploration = true)
        )
        legacyProbe.remove("kind")
        assertEquals(
            MaxAiEpisodeKind.PROBE,
            MaxAiJournalCodec.decodeEpisode(legacyProbe)?.kind,
        )
    }

    /** نص تالف لا يُسقط التطبيق ولا يُنتج حلقات وهمية. */
    @Test
    fun `corrupt text decodes to empty list`() {
        assertEquals(emptyList<MaxAiEpisode>(), MaxAiJournalCodec.decodeAll("{not json"))
        assertEquals(emptyList<MaxAiEpisode>(), MaxAiJournalCodec.decodeAll(""))
    }

    /** حلقة بمفاتيح ناقصة تُهمل وحدها بلا إسقاط بقية الدفتر. */
    @Test
    fun `broken episode is skipped without losing the rest`() {
        val good = MaxAiJournalCodec.encodeEpisode(fullEpisode())
        val array = JSONArray().put(JSONObject().put("id", 5)).put(good)
        val decoded = MaxAiJournalCodec.decodeAll(array.toString())
        assertEquals(1, decoded.size)
        assertEquals(fullEpisode(), decoded.first())
    }
}
