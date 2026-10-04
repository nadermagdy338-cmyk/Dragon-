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

class CockpitModelTest {
    @Test
    fun `segments clamp instead of throwing`() {
        assertEquals(0, CockpitModel.segmentOf(-1f, 16))
        assertEquals(8, CockpitModel.segmentOf(0.5f, 16))
        assertEquals(16, CockpitModel.segmentOf(1f, 16))
        assertEquals(16, CockpitModel.segmentOf(7f, 16))
    }

    @Test
    fun `speed text switches unit at one megabyte and hides unsupported counters`() {
        assertEquals("--", CockpitModel.speedText(-1))
        assertEquals("0.0 KB/s", CockpitModel.speedText(0))
        assertEquals("28.5 KB/s", CockpitModel.speedText(28_500))
        assertEquals("1.20 MB/s", CockpitModel.speedText(1_200_000))
    }

    @Test
    fun `compact threshold and idle collapse boundaries`() {
        assertTrue(CockpitModel.isCompact(639f))
        assertFalse(CockpitModel.isCompact(640f))
        assertFalse(CockpitModel.shouldCollapseIdle(30_000, 0))
        assertTrue(CockpitModel.shouldCollapseIdle(30_001, 0))
    }
}
