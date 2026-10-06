/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyModelTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `target page falls back to the first game for unknown or missing selection`() {
        val games = listOf("a", "b", "c")
        assertEquals(2, LobbyModel.targetPage(games, "c"))
        assertEquals(0, LobbyModel.targetPage(games, "zzz"))
        assertEquals(0, LobbyModel.targetPage(games, null))
        assertEquals(0, LobbyModel.targetPage(emptyList(), "a"))
    }

    @Test
    fun `temperature is unknown rather than zero when the reading is missing or absurd`() {
        assertNull(LobbyModel.temperatureText(null))
        assertNull(LobbyModel.temperatureText(-301))
        assertNull(LobbyModel.temperatureText(1501))
        assertEquals("34.5°C", LobbyModel.temperatureText(345))
        assertEquals("0.0°C", LobbyModel.temperatureText(0))
    }

    @Test
    fun `free memory uses latin digits and rejects non positive readings`() {
        assertNull(LobbyModel.freeMemoryText(null))
        assertNull(LobbyModel.freeMemoryText(0L))
        assertNull(LobbyModel.freeMemoryText(-5L))
        assertEquals("3.1 GB", LobbyModel.freeMemoryText(3_100_000_000L))
    }

    @Test
    fun `dominant colour ignores gray black and transparent pixels`() {
        val pixels = IntArray(300) { i ->
            when {
                i % 3 == 0 -> argb(255, 128, 128, 128)
                i % 3 == 1 -> argb(255, 0, 0, 0)
                else -> argb(0, 255, 0, 0)
            }
        }
        assertNull(LobbyModel.dominantRgb(pixels, step = 1))
    }

    @Test
    fun `dominant colour picks the saturated hue over a larger white background`() {
        val pixels = IntArray(400) { i -> if (i < 300) argb(255, 255, 255, 255) else argb(255, 220, 30, 40) }
        val rgb = LobbyModel.dominantRgb(pixels, step = 1)
        assertNotNull(rgb)
        val r = (rgb!! shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        assertTrue("red channel should dominate: $r", r > 200)
        assertTrue("green channel should stay low: $g", g < 60)
    }

    @Test
    fun `empty input has no dominant colour`() {
        assertNull(LobbyModel.dominantRgb(IntArray(0)))
    }

    @Test
    fun `lift brightens dark hues without changing the ratio and leaves bright ones alone`() {
        val bright = (230 shl 16) or (40 shl 8) or 50
        assertEquals(bright, LobbyModel.lift(bright))
        val dark = (95 shl 16) or (0 shl 8) or 0
        val lifted = LobbyModel.lift(dark)
        assertEquals(190, (lifted shr 16) and 0xFF)
        assertEquals(0, (lifted shr 8) and 0xFF)
    }
}
