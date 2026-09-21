package nd.max.ui.mainscreens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedActivityModelTest {
    @Test
    fun unverifiedItemsNeverReachTheHomeCard() {
        val items = listOf(
            UnifiedActivityItem("failed", 100, 10L, false, "failed", ""),
            UnifiedActivityItem("ok", 10, 11L, true, "ok", "verified"),
        )
        assertEquals(listOf("ok"), UnifiedActivityModel.select(items, 12L).map { it.key })
    }

    @Test
    fun sameControlIsShownOnlyOnce() {
        val items = listOf(
            UnifiedActivityItem("gpu", 10, 10L, true, "GPU", "profile"),
            UnifiedActivityItem("gpu", 90, 20L, true, "GPU", "duplicate"),
        )
        assertEquals(1, UnifiedActivityModel.select(items, 21L).size)
    }

    @Test
    fun priorityWinsThenNewestEvent() {
        val items = listOf(
            UnifiedActivityItem("old-important", 100, 1L, true, "important", ""),
            UnifiedActivityItem("new-normal", 10, 99L, true, "normal", ""),
        )
        assertEquals("old-important", UnifiedActivityModel.select(items, 100L).first().key)
    }

    @Test
    fun emptyOrOnlyUnverifiedHidesCard() {
        assertFalse(UnifiedActivityModel.shouldShow(emptyList(), 0L))
        assertFalse(
            UnifiedActivityModel.shouldShow(
                listOf(UnifiedActivityItem("x", 1, null, false, "x", "")),
                0L,
            ),
        )
        assertTrue(
            UnifiedActivityModel.shouldShow(
                listOf(UnifiedActivityItem("x", 1, null, true, "x", "")),
                0L,
            ),
        )
    }
}
