package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTrendModelTest {
    @Test
    fun `one reading is not a trend`() {
        assertEquals(HomeTrend.UNKNOWN, HomeTrendModel.direction(listOf(40)))
    }

    @Test
    fun `a rise or a fall of a degree or more is up or down`() {
        assertEquals(HomeTrend.UP, HomeTrendModel.direction(listOf(40, 41)))
        assertEquals(HomeTrend.DOWN, HomeTrendModel.direction(listOf(41, 40)))
    }

    @Test
    fun `less than a degree of movement is steady, not a trend`() {
        assertEquals(HomeTrend.STEADY, HomeTrendModel.direction(listOf(40, 40, 41, 40)))
    }

    @Test
    fun `the window keeps only the last minute of readings`() {
        val trail = mutableListOf<Int>()
        repeat(40) { HomeTrendModel.append(trail, 40 + it / 10) }
        assertEquals(HomeTrendModel.WINDOW_SAMPLES, trail.size)
        assertEquals(HomeTrend.UP, HomeTrendModel.direction(trail))
    }

    @Test
    fun `a missing reading is skipped and never becomes a zero`() {
        val trail = mutableListOf(40)
        HomeTrendModel.append(trail, null)
        assertEquals(listOf(40), trail)
    }
}
