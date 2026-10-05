/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelHandleMathTest {
    @Test
    fun rubberBandTracksTheFingerNearZeroAndNeverPassesTheLimit() {
        assertEquals(0f, rubberBand(0f, 150f), 0f)
        assertEquals(0f, rubberBand(-12f, 150f), 0f)
        assertEquals(10f, rubberBand(10f, 150f), 0.4f)
        assertTrue(rubberBand(100_000f, 150f) <= 150f)
        assertTrue(rubberBand(80f, 150f) > rubberBand(40f, 150f))
    }

    @Test
    fun pullOpensOnThresholdOrFastShortFling() {
        assertTrue(pullShouldOpen(80f, 0f, 76f, 24f, 1100f))
        assertTrue(pullShouldOpen(30f, 1500f, 76f, 24f, 1100f))
        assertFalse(pullShouldOpen(10f, 3000f, 76f, 24f, 1100f))
        assertFalse(pullShouldOpen(40f, 200f, 76f, 24f, 1100f))
    }

    @Test
    fun handleFractionRoundTripsAndSurvivesNoRange() {
        val f = handleFraction(250f, 10f, 910f)
        assertEquals(250f, handleY(f, 10f, 910f), 0.01f)
        assertEquals(0.5f, handleFraction(5f, 20f, 20f), 0f)
        assertEquals(20f, handleY(0.7f, 20f, 20f), 0f)
        assertEquals(1f, handleFraction(5000f, 10f, 910f), 0f)
    }
}
