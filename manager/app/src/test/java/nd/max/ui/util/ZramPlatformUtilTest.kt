/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد `AR-10`: فهم الأعلام لا تخمينها، و«غير المعروف» لا يصير «لا» (ADR-07 / XR-19).
 */
class ZramPlatformUtilTest {

    @Test
    fun boolFlag_understandsCommonSpellings() {
        listOf("1", "true", "TRUE", "yes", "on", "enabled", " 1 ").forEach {
            assertEquals("قيمة: $it", true, ZramPlatformUtil.parseBoolFlag(it))
        }
        listOf("0", "false", "NO", "off", "disabled").forEach {
            assertEquals("قيمة: $it", false, ZramPlatformUtil.parseBoolFlag(it))
        }
    }

    @Test
    fun boolFlag_unknownStaysUnknown() {
        assertNull(ZramPlatformUtil.parseBoolFlag("2"))
        assertNull(ZramPlatformUtil.parseBoolFlag("maybe"))
        assertNull(ZramPlatformUtil.parseBoolFlag(""))
        assertNull(ZramPlatformUtil.parseBoolFlag(null))
    }

    @Test
    fun bytes_acceptsPositiveAndRejectsGarbage() {
        assertEquals(2_147_483_648L, ZramPlatformUtil.parseBytes("2147483648"))
        assertNull(ZramPlatformUtil.parseBytes("0"))
        assertNull(ZramPlatformUtil.parseBytes("-1"))
        assertNull(ZramPlatformUtil.parseBytes("2G"))
        assertNull(ZramPlatformUtil.parseBytes(null))
    }

    @Test
    fun supported_needsRealEvidence() {
        assertTrue(state(managed = true, disksize = null).supported)
        assertTrue(state(managed = null, disksize = 2_147_483_648L).supported)
        assertFalse(state(managed = null, disksize = null).supported)
        assertFalse("وجود zram0 وحده دون مقروء لا يكفي", state(managed = false, disksize = null).supported)
    }

    private fun state(managed: Boolean?, disksize: Long?) = ZramPlatformState(
        platformManaged = managed,
        algorithm = null,
        sizeRaw = null,
        writebackEnabled = null,
        recompressSupported = null,
        idleTrackingPresent = null,
        disksizeBytes = disksize,
    )
}
