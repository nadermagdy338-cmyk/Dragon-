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
}
