/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد `AR-02`: القراءة لا تكتب، و«الجهولة» ليست «لا شيء» (ADR-07 / XR-19).
 */
class BootHistoryUtilTest {

    @Test
    fun parts_splitsAospTriple() {
        assertEquals(
            listOf("shutdown", "thermal", "battery"),
            BootHistoryUtil.parts("shutdown,thermal,battery"),
        )
    }

    @Test
    fun parts_ignoresBlankSegmentsAndNull() {
        assertEquals(listOf("cold"), BootHistoryUtil.parts(" cold , , "))
        assertEquals(emptyList<String>(), BootHistoryUtil.parts(null))
        assertEquals(emptyList<String>(), BootHistoryUtil.parts(""))
    }

    @Test
    fun thermalShutdown_isDetectedFromEitherSource() {
        assertTrue(BootHistory("shutdown,thermal", null, emptyList(), true).isThermalShutdown)
        assertTrue(BootHistory(null, "shutdown,thermal", emptyList(), true).isThermalShutdown)
        assertFalse(BootHistory("cold", "hard", emptyList(), true).isThermalShutdown)
    }

    @Test
    fun unreadablePstore_isUnknownNotAbsent() {
        val h = BootHistory("cold", "cold", emptyList(), pstoreReadable = false)
        assertFalse(h.pstoreReadable)
        assertFalse("الجهولة لا تُقرأ كـ«لا أثر»", h.hasCrashArtifact)
    }

    @Test
    fun read_withInjectedListing_reportsCrashArtifact() {
        val h = BootHistoryUtil.read(pstoreListing = { listOf("dmesg-ramoops-0") })
        assertTrue(h.pstoreReadable)
        assertTrue(h.hasCrashArtifact)
        assertEquals(listOf("dmesg-ramoops-0"), h.pstoreEntries)
    }

    @Test
    fun read_withNoListing_staysUnknown() {
        val h = BootHistoryUtil.read(pstoreListing = { null })
        assertFalse(h.pstoreReadable)
        assertFalse(h.hasCrashArtifact)
    }
}
