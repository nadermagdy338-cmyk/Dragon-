/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Source guards only; these do not substitute for device or coroutine tests. */
class MaxAiPresentationArchitectureTest {
    private lateinit var sourceRoot: File

    @Before
    fun locateSourceRoot() {
        val found = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).firstOrNull { it.isDirectory }
        assumeTrue("Cannot locate nd.max sources; guard not evaluated", found != null)
        sourceRoot = found!!
    }

    private fun source(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    @Test
    fun `insight snapshot and derivation run upstream of the IO boundary`() {
        val text = source("ui/viewmodel/MaxAiViewModel.kt")
        assertTrue(text.contains("import kotlinx.coroutines.flow.flowOn"))
        val pipeline = text.substringAfter("val insights:").substringBefore("fun setAiEnabled")
        assertTrue(
            "The snapshot and derivation must run off Main before stateIn(viewModelScope)",
            Regex(
                "\\.map\\s*\\{\\s*MaxAiInsights\\.derive\\(engine\\.effectsSnapshot\\(\\),\\s*it\\)\\s*}" +
                    "\\s*\\.flowOn\\(Dispatchers\\.IO\\)\\s*\\.stateIn\\(",
            ).containsMatchIn(pipeline),
        )
    }

    @Test
    fun `missing outcome and prediction values are not rendered as zero`() {
        val text = source("ui/mainscreens/MaxAiScreen.kt")
        listOf("objectiveDelta", "predictionConfidence", "predictedThermalC").forEach { field ->
            assertFalse(
                "Missing $field must remain unknown",
                Regex("episode\\.$field\\s*\\?:\\s*0(?:f|\\.0f)?\\b").containsMatchIn(text),
            )
        }
        val summary = text.substringAfter("private fun episodeSummary(")
            .substringBefore("private fun probeSummary(")
        assertTrue(summary.contains("R.string.max_ai_value_unknown"))
        val prediction = text.substringAfter("private fun predictionText(")
            .substringBefore("private fun deltaTone(")
        assertTrue(prediction.contains("R.string.max_ai_value_unknown"))
    }

    @Test
    fun `opening evidence does not run a hardware decision cycle`() {
        listOf("MaxAiScreen.kt", "MaxLiveScreen.kt").forEach { file ->
            val text = source("ui/mainscreens/$file")
            assertFalse(Regex("LaunchedEffect\\s*\\([^)]*\\)\\s*\\{\\s*viewModel\\.refresh").containsMatchIn(text))
        }
    }

    @Test
    fun `objective selection observes state rather than remembered getter`() {
        val text = source("ui/mainscreens/MaxAiScreen.kt")
        assertTrue(text.contains("state.objectivePreference"))
        assertFalse(text.contains("viewModel.objectivePreference()"))
        // **تصحيح حرس قديم (CI-GUARD-STALE-01):** كان هنا
        // `assertTrue(text.contains("viewModel.profileRequest.collectAsStateWithLifecycle()"))`
        // — أي أنه يطالب بسطح *أُزيل بأمر المالك*: قسم «Base profiles» في Controls حُذف مع
        // `MaxAiViewModel.requestProfile`، ولم يبقَ لطلب المسبق مستهلك في هذه الشاشة. فالخيار
        // الصحيح ليس حذف السطر ولا إبقاءه مسقوفًا، بل **قلبُه إلى شرط يقيس الحالة الجارية**:
        // الشاشة لا تحمل السطح المزال أبدًا. وبهذا لا يعود الطلب المسبق إلى هذه الشاشة صامتًا
        // (والسطح نفسه باقٍ حيًّا حيث يخصّه: `HomeScreen` ← `profileRequest` ← لوحة الحكم).
        assertFalse(text.contains("profileRequest"))
    }

    @Test
    fun `presentation clock stops with its last subscriber`() {
        val text = source("ui/viewmodel/MaxAiViewModel.kt")
        val clock = text.substringAfter("val nowMs:").substringBefore("val episodes:")
        assertTrue(clock.contains("SharingStarted.WhileSubscribed(0L)"))
        assertFalse(clock.contains("engine.requestRefresh"))
    }

    @Test
    fun `saved preference is unknown until loaded and app labels reset with identity`() {
        val models = source("core/maxai/MaxAiModels.kt")
        assertTrue(models.contains("val objectivePreference: String? = null"))
        val screen = source("ui/mainscreens/MaxAiScreen.kt")
        assertTrue(screen.contains("return key(context, packageName)"))
        assertTrue(screen.contains("freshness != SampleFreshness.Live"))
    }

    @Test
    fun `refresh execution is owned by the engine not the waiting screen`() {
        val engine = source("core/maxai/MaxAiEngine.kt")
        val request = engine.substringAfter("suspend fun requestRefresh()").substringBefore("fun clearJournal")
        assertTrue(request.contains("scope.async { runCycleSingleFlight() }.await()"))
    }
}
