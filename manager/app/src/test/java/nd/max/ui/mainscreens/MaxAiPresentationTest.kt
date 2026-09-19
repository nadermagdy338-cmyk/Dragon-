package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaxAiPresentationTest {
    @Test
    fun `no sample is never a live zero`() {
        assertEquals(SampleFreshness.Missing, sampleFreshness(0L, 20_000L))
        assertEquals(SampleFreshness.Missing, sampleFreshness(-1L, 20_000L))
    }

    @Test
    fun `freshness expires without any new engine sample`() {
        assertEquals(SampleFreshness.Live, sampleFreshness(1_000L, 46_000L))
        assertEquals(SampleFreshness.Stale, sampleFreshness(1_000L, 46_001L))
    }

    @Test
    fun `future timestamps are not live after a wall clock change`() {
        assertEquals(SampleFreshness.Stale, sampleFreshness(30_000L, 20_000L))
    }

    @Test
    fun `reordering contexts does not switch the chosen app`() {
        assertEquals("app.b", selectedLearningContext("app.b", listOf("*", "app.a", "app.b")))
        assertEquals("app.b", selectedLearningContext("app.b", listOf("*", "app.b", "app.a")))
    }

    @Test
    fun `missing selection falls back without inventing a context`() {
        assertEquals("*", selectedLearningContext("uninstalled", listOf("*", "app.a")))
        assertNull(selectedLearningContext("app.a", emptyList()))
    }
}
