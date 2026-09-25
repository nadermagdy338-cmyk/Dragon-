/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import nd.max.core.maxai.MaxAiEpisode
import nd.max.core.maxai.MaxAiEpisodeKind
import nd.max.core.maxai.MaxAiReading
import nd.max.core.maxai.MaxAiVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxAiTimelineFilterTest {
    private val kindsByFilter = mapOf(
        TimelineFilter.All to MaxAiEpisodeKind.values().toSet(),
        TimelineFilter.Decisions to setOf(MaxAiEpisodeKind.DECISION),
        TimelineFilter.Probes to setOf(MaxAiEpisodeKind.PROBE),
        TimelineFilter.Alerts to setOf(MaxAiEpisodeKind.SAFETY, MaxAiEpisodeKind.DRIFT),
    )

    @Test
    fun `no verdict selection preserves every kind filter`() {
        kindsByFilter.forEach { (filter, kinds) ->
            MaxAiEpisodeKind.values().forEach { kind ->
                MaxAiVerdict.values().forEach { verdict ->
                    assertEquals(
                        "$filter / $kind / $verdict",
                        kind in kinds,
                        filter.matches(kind, verdict),
                    )
                }
            }
        }
    }

    @Test
    fun `kind and verdict filters intersect for every recorded verdict`() {
        kindsByFilter.forEach { (filter, kinds) ->
            MaxAiEpisodeKind.values().forEach { kind ->
                MaxAiVerdict.values().forEach { verdict ->
                    MaxAiVerdict.values().forEach { selected ->
                        assertEquals(
                            "$filter / $kind / $verdict selected $selected",
                            kind in kinds && verdict == selected,
                            filter.matches(kind, verdict, selected),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `filtering preserves newest first order and does not change source`() {
        val recorded = listOf(
            4L to MaxAiVerdict.WRITE_FAILED,
            3L to MaxAiVerdict.IMPROVED,
            2L to MaxAiVerdict.NO_ACTION,
            1L to MaxAiVerdict.IMPROVED,
        )
        val original = recorded.toList()
        val matched = recorded.filter { (_, verdict) ->
            TimelineFilter.Decisions.matches(
                MaxAiEpisodeKind.DECISION, verdict, MaxAiVerdict.IMPROVED,
            )
        }

        assertEquals(listOf(3L, 1L), matched.map { it.first })
        assertEquals(original, recorded)
    }
}

/**
 * بحث الدفتر: على حقول الحلقة الخام لا على نص الواجهة المترجم.
 *
 * لو بحث في النص المعروض لاختلفت نتيجة نفس الدفتر بين لغة وأخرى، وهي عيب
 * صامت لا يظهر إلّا عند مستخدم يبدّل لغة ثم يبحث فلا يجد ما وجده قبل قليل.
 */
class MaxAiTimelineSearchTest {

    @Test
    fun `an empty or whitespace query filters nothing`() {
        val episode = episode(knobKey = "cpu.max")
        assertTrue(TimelineSearch.matches(episode, ""))
        assertTrue(TimelineSearch.matches(episode, "   "))
    }

    @Test
    fun `a query matches the knob key, its label, the app context and the details`() {
        val episode = episode(
            knobKey = "gpu.max_freq",
            knobLabel = "GPU max frequency",
            appContext = "com.game.one",
            detail = "arbiter verify ok",
        )

        assertTrue(TimelineSearch.matches(episode, "gpu.max"))
        assertTrue(TimelineSearch.matches(episode, "MAX FREQUENCY"))
        assertTrue(TimelineSearch.matches(episode, "com.game"))
        assertTrue(TimelineSearch.matches(episode, "arbiter"))
        assertFalse(TimelineSearch.matches(episode, "thermal"))
    }

    @Test
    fun `a query matching nothing is reported as no match, never as a match-all`() {
        val episode = episode(knobKey = "cpu.max", knobLabel = "CPU max")
        assertFalse(TimelineSearch.matches(episode, "zzz"))
    }

    @Test
    fun `the query is trimmed so a pasted value still finds its row`() {
        val episode = episode(appContext = "com.tencent.ig")
        assertTrue(TimelineSearch.matches(episode, "  com.tencent.ig  "))
    }

    @Test
    fun `multiple terms can match different recorded fields`() {
        val episode = episode(knobKey = "gpu.max", appContext = "com.game", detail = "verified")
        assertTrue(TimelineSearch.matches(episode, "GPU com.game verified"))
        assertFalse(TimelineSearch.matches(episode, "GPU com.game missing"))
    }

    @Test
    fun `context kind verdict and text intersect without changing journal order`() {
        val first = episode(knobKey = "gpu.max", appContext = "app.a").copy(id = 3L)
        val second = first.copy(id = 2L, appContext = "app.b")
        val third = first.copy(id = 1L)
        val journal = listOf(first, second, third)
        val matched = filterTimeline(journal, TimelineFilter.Decisions, MaxAiVerdict.NO_ACTION, "gpu", "app.a")
        assertEquals(listOf(3L, 1L), matched.map { it.id })
        assertEquals(3, journal.size)
        assertTrue(filterTimeline(journal, TimelineFilter.Probes, null, "", "app.a").isEmpty())
    }

    @Test
    fun `recorded verdict and event kind are searchable`() {
        assertTrue(TimelineSearch.matches(episode(), "NO_ACTION DECISION"))
        assertFalse(TimelineSearch.matches(episode(), "WRITE_FAILED"))
    }

    private fun episode(
        knobKey: String? = null,
        knobLabel: String? = null,
        appContext: String = "system",
        detail: String = "detail",
    ): MaxAiEpisode {
        val reading = MaxAiReading(
            cpuLoadPercent = 1,
            thermalC = 35f,
            batteryPercent = 50,
            memoryPercent = 40,
            networkPercent = 0,
            screenOn = true,
            objectiveScore = 0.5f,
        )
        return MaxAiEpisode(
            id = 1L,
            appContext = appContext,
            objectiveLabel = "balanced",
            objectiveSource = "learned",
            weightPerformance = 0.4f,
            weightBattery = 0.3f,
            weightThermal = 0.3f,
            satisfactionTarget = 0.75f,
            gap = 0.1f,
            before = reading,
            after = null,
            knobKey = knobKey,
            knobLabel = knobLabel,
            direction = null,
            fromValue = null,
            toValue = null,
            appliedValue = null,
            stepFraction = 0f,
            predictedGain = null,
            predictedThermalC = null,
            predictionConfidence = null,
            candidates = emptyList(),
            verdict = MaxAiVerdict.NO_ACTION,
            detail = detail,
            objectiveDelta = null,
            thermalDeltaC = null,
            cpuDeltaPercent = null,
            batteryDeltaPercent = null,
            samplesBefore = 0,
            samplesAfter = 0,
            confidenceBefore = 0f,
            confidenceAfter = 0f,
            predictionErrorGain = null,
            safetyLevel = "NORMAL",
            kind = MaxAiEpisodeKind.DECISION,
        )
    }
}
