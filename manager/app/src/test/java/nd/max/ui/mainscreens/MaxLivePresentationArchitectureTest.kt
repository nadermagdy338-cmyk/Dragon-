/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Focused source guards, not a substitute for Compose lifecycle or rendering tests. */
class MaxLivePresentationArchitectureTest {
    private lateinit var source: String

    @Before
    fun readScreen() {
        val file = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).map { File(it, "ui/mainscreens/MaxLiveScreen.kt") }.firstOrNull { it.isFile }
        checkNotNull(file) { "Cannot locate MaxLiveScreen; source guard was not evaluated" }
        source = file.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\\n]*"), "")
    }

    private fun function(name: String): String {
        val declaration = Regex("(?m)^(?:private )?fun\\s+${Regex.escape(name)}\\s*\\(")
            .find(source) ?: error("Missing function $name")
        val next = Regex("(?m)^(?:private )?fun\\s+").find(source, declaration.range.last + 1)
        return source.substring(declaration.range.first, next?.range?.first ?: source.length)
    }

    @Test
    fun `freshness is driven by the lifecycle clock and shared timestamp policy`() {
        val screen = function("MaxLiveScreen")
        val clock = Regex("val\\s+(\\w+)\\s+by\\s+viewModel\\.nowMs\\.collectAsStateWithLifecycle\\(\\)")
            .find(screen) ?: error("Missing lifecycle-bound presentation clock")
        assertTrue(
            Regex("sampleFreshness\\(state\\.lastSampleAtMs,\\s*${clock.groupValues[1]}\\)")
                .containsMatchIn(screen),
        )
        assertFalse("The screen must not freeze its own wall clock", source.contains("System.currentTimeMillis"))
        assertTrue(
            "Future timestamps must not be described as just measured",
            Regex("elapsedMs\\s*<\\s*0L").containsMatchIn(function("liveRelativeTime")),
        )
    }

    @Test
    fun `all feature sections are lazy with unique stable keys`() {
        val screen = function("MaxLiveScreen")
        assertTrue(Regex("\\bMaxListScreen\\s*\\(").containsMatchIn(screen))
        assertFalse(Regex("\\bMaxScreen\\s*\\(").containsMatchIn(screen))
        val items = Regex("item\\s*\\(\\s*key\\s*=\\s*\"([^\"]+)\"\\s*\\)\\s*\\{\\s*(\\w+Section)\\s*\\(")
            .findAll(screen).toList()
        val expected = setOf(
            "VitalsSection", "LiveForecastSection", "PredictionErrorSection", "KnowledgeSection",
            "ExplorationSection", "AutomationPlanSection", "LoopCountersSection", "OwnershipSection",
            "ActionsSection",
        )
        assertEquals(expected, items.map { it.groupValues[2] }.toSet())
        assertEquals(expected.size, items.size)
        assertEquals("Lazy item keys must be unique", items.size, items.map { it.groupValues[1] }.toSet().size)
    }

    @Test
    fun `indicators and counters never simulate ongoing work or intermediate counts`() {
        assertFalse(Regex("rememberInfiniteTransition|infiniteRepeatable|animateIntAsState").containsMatchIn(source))
        assertTrue(
            Regex("text\\s*=\\s*count\\.toString\\(\\)").containsMatchIn(function("LiveCountRow")),
        )
        val vitals = function("VitalsSection")
        assertTrue(vitals.contains("R.string.max_trust_live"))
        assertTrue(vitals.contains("R.string.max_live_pulse_stale"))
        assertTrue(vitals.contains("R.string.max_ai_decisions_enabled"))
        assertTrue(vitals.contains("R.string.max_ai_decisions_disabled"))
        assertFalse(vitals.contains("R.string.max_live_pulse_on"))
        assertTrue(vitals.indexOf("return@MaxSection") < vitals.indexOf("LiveBar("))
    }

    @Test
    fun `exploration and plan defaults are hidden until a measured sample exists`() {
        listOf(
            "ExplorationSection" to "R.string.max_live_explore_allowed",
            "AutomationPlanSection" to "plan.confidencePercent",
        ).forEach { (section, value) ->
            val text = function(section)
            val guard = Regex("if\\s*\\(freshness\\s*==\\s*SampleFreshness\\.Missing\\)").find(text)
                ?: error("$section must guard the first sample")
            val end = text.indexOf("return@MaxSection", guard.range.last)
            assertTrue("$section must stop before exposing default evidence", end > guard.range.last)
            assertTrue(text.substring(guard.range.first, end).contains("R.string.max_live_empty"))
            assertTrue("$section exposes evidence before its pending guard", text.indexOf(value) > end)
        }
    }

    @Test
    fun `stale or disabled automation remains labelled as historical evidence`() {
        val exploration = function("ExplorationSection")
        assertTrue(
            Regex("state\\.aiEnabled\\s*&&\\s*freshness\\s*==\\s*SampleFreshness\\.Live\\s*&&\\s*blocked\\s*==\\s*null")
                .containsMatchIn(exploration),
        )
        val allowed = exploration.indexOf("R.string.max_live_explore_allowed")
        assertTrue(exploration.indexOf("!state.aiEnabled") in 0 until allowed)
        assertTrue(exploration.indexOf("freshness == SampleFreshness.Stale") in 0 until allowed)
        val plan = function("AutomationPlanSection")
        assertTrue(plan.contains("R.string.max_live_plan_snapshot"))
        assertTrue(plan.contains("R.string.max_ai_learning_coverage"))
        assertFalse(plan.contains("R.string.max_live_confidence"))
        assertTrue(Regex("if\\s*\\(plan\\.mode\\s*!=\\s*\"Safety guard\"\\)").containsMatchIn(plan))
        val forecast = function("LiveForecastSection")
        assertTrue(forecast.contains("R.string.max_trust_stale"))
        assertFalse(forecast.contains("R.string.max_trust_snapshot"))
    }

    @Test
    fun `opening evidence does not request a cycle and explicit refresh uses runtime status`() {
        assertFalse(Regex("LaunchedEffect|SideEffect|DisposableEffect").containsMatchIn(source))
        val screen = function("MaxLiveScreen")
        assertTrue(Regex("viewModel\\.cycleStatus\\.collectAsStateWithLifecycle\\(\\)").containsMatchIn(screen))
        assertTrue(screen.contains("viewModel::refresh"))
        assertTrue(Regex("MaxAiRuntimeStatus\\(status,\\s*onRefresh\\)").containsMatchIn(function("ActionsSection")))
    }
}
