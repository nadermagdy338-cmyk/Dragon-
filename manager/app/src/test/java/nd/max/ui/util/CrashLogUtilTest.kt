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
 * عقد `AR-06`: التحليل نقي، و«غير المقروء» ليس «صفر الحوادث» (ADR-07 / XR-19).
 */
class CrashLogUtilTest {

    @Test
    fun newestFile_takesFirstNonBlankLine() {
        assertEquals("anr_0", CrashLogUtil.parseNewestFile("anr_0\nanr_1\n"))
        assertEquals("tombstone_00", CrashLogUtil.parseNewestFile("\n  \n tombstone_00 \n"))
        assertNull(CrashLogUtil.parseNewestFile(null))
        assertNull(CrashLogUtil.parseNewestFile("   \n\n"))
    }

    @Test
    fun epochSeconds_convertsToMillisAndRejectsSentinels() {
        assertEquals(1_700_000_000_000L, CrashLogUtil.parseEpochSeconds("1700000000"))
        assertNull("صفر ليس زمنًا", CrashLogUtil.parseEpochSeconds("0"))
        assertNull(CrashLogUtil.parseEpochSeconds("-1"))
        assertNull(CrashLogUtil.parseEpochSeconds(null))
        assertNull(CrashLogUtil.parseEpochSeconds("not a number"))
    }

    @Test
    fun readable_requiresReadingAtLeastOneFolder() {
        assertTrue(summary(anr = 0, tomb = 0).readable)
        assertFalse("لم نقرأ أيًّا منهما ⇒ غير معروف", summary(anr = null, tomb = null).readable)
    }

    @Test
    fun total_isNullWhenNothingWasRead() {
        assertNull(summary(anr = null, tomb = null).total)
        assertEquals(3, summary(anr = 1, tomb = 2).total)
        assertEquals(0, summary(anr = 0, tomb = 0).total)
    }

    @Test
    fun partialRead_doesNotInventTheMissingSide() {
        val s = summary(anr = 2, tomb = null)
        assertEquals(2, s.total)
        assertNull("الجانب غير المقروء يبقى null لا صفرًا", s.tombstoneCount)
    }

    private fun summary(anr: Int?, tomb: Int?) = CrashLogSummary(
        anrCount = anr,
        tombstoneCount = tomb,
        latestAnrName = null,
        latestTombstoneName = null,
        latestAtMs = null,
    )
}
