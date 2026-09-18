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
 * عقد `AR-14`: الحاويات تُترجَم كما أعلنتها المنصّة، و«غير المقروء» يبقى مجهولًا (ADR-07).
 */
class BackgroundGovernanceUtilTest {

    @Test
    fun bucketOf_mapsOfficialNumbers() {
        assertEquals(StandbyBucket.EXEMPTED, BackgroundGovernanceUtil.bucketOf(5))
        assertEquals(StandbyBucket.ACTIVE, BackgroundGovernanceUtil.bucketOf(10))
        assertEquals(StandbyBucket.WORKING_SET, BackgroundGovernanceUtil.bucketOf(20))
        assertEquals(StandbyBucket.FREQUENT, BackgroundGovernanceUtil.bucketOf(30))
        assertEquals(StandbyBucket.RARE, BackgroundGovernanceUtil.bucketOf(40))
        assertEquals(StandbyBucket.RESTRICTED, BackgroundGovernanceUtil.bucketOf(45))
        assertEquals(StandbyBucket.NEVER, BackgroundGovernanceUtil.bucketOf(50))
    }

    @Test
    fun bucketOf_unknownNumberIsNotSnappedToANeighbour() {
        assertEquals(StandbyBucket.UNKNOWN, BackgroundGovernanceUtil.bucketOf(99))
        assertEquals(StandbyBucket.UNKNOWN, BackgroundGovernanceUtil.bucketOf(null))
    }

    @Test
    fun parseBucket_acceptsNonNegativeAndRejectsNoise() {
        assertEquals(40, BackgroundGovernanceUtil.parseBucket("40"))
        assertEquals(40, BackgroundGovernanceUtil.parseBucket(" 40 \n"))
        assertEquals(0, BackgroundGovernanceUtil.parseBucket("0"))
        assertNull(BackgroundGovernanceUtil.parseBucket("-3"))
        assertNull(BackgroundGovernanceUtil.parseBucket("Error: unknown package"))
        assertNull(BackgroundGovernanceUtil.parseBucket(null))
    }

    @Test
    fun whitelistContains_separatesNotReadFromNotFound() {
        val dump = "system,com.example.keep\nuser,com.example.other\ncom.example.plain\n"
        assertEquals(true, BackgroundGovernanceUtil.whitelistContains(dump, "com.example.keep"))
        assertEquals(true, BackgroundGovernanceUtil.whitelistContains(dump, "com.example.plain"))
        assertEquals(false, BackgroundGovernanceUtil.whitelistContains(dump, "com.example.absent"))
        assertNull("لم نقرأ المخرج ⇒ مجهول لا «غير موجود»", BackgroundGovernanceUtil.whitelistContains(null, "com.example.x"))
    }

    @Test
    fun restricted_isDerivedFromBucketOnly() {
        assertTrue(BackgroundGovernance(rawBucket = 45, dozeWhitelisted = null).restricted == true)
        assertTrue(BackgroundGovernance(rawBucket = 50, dozeWhitelisted = null).restricted == true)
        assertFalse(BackgroundGovernance(rawBucket = 30, dozeWhitelisted = null).restricted == true)
        assertNull(BackgroundGovernance(rawBucket = null, dozeWhitelisted = null).restricted)
    }

    @Test
    fun readable_requiresReadingAtLeastOneSignal() {
        assertTrue(BackgroundGovernance(rawBucket = 10, dozeWhitelisted = null).readable)
        assertTrue(BackgroundGovernance(rawBucket = null, dozeWhitelisted = false).readable)
        assertFalse(BackgroundGovernance(rawBucket = null, dozeWhitelisted = null).readable)
    }
}
