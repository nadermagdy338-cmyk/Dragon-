/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * عقد `AR-08` و`AR-11`: تحليل نقي، و«غير المعروف» لا يصير رقمًا (ADR-07 / XR-19).
 */
class HealthParsersTest {

    // ── AR-08: البطارية ───────────────────────────────────────────────────────

    @Test
    fun microAh_acceptsPositiveIntegers() {
        assertEquals(4_500_000L, BatteryHealthUtil.parseMicroAh("4500000"))
        assertEquals(3_000_000L, BatteryHealthUtil.parseMicroAh(" 3000000 \n"))
    }

    @Test
    fun microAh_rejectsZeroNegativeAndGarbage() {
        assertNull(BatteryHealthUtil.parseMicroAh("0"))
        assertNull(BatteryHealthUtil.parseMicroAh("-5"))
        assertNull(BatteryHealthUtil.parseMicroAh("abc"))
        assertNull(BatteryHealthUtil.parseMicroAh(null))
    }

    @Test
    fun cycleCount_treatsNegativeSentinelAsUnknown() {
        assertEquals(120, BatteryHealthUtil.parseCycleCount("120"))
        assertEquals(0, BatteryHealthUtil.parseCycleCount("0"))
        assertNull("كثير من الأجهزة تُعلن -1 معنى «غير معروف»", BatteryHealthUtil.parseCycleCount("-1"))
        assertNull(BatteryHealthUtil.parseCycleCount(null))
    }

    @Test
    fun stateOfHealth_isDerivedAndSanityChecked() {
        assertEquals(90, BatteryHealthUtil.stateOfHealthPercent(5_000_000L, 4_500_000L))
        assertNull(BatteryHealthUtil.stateOfHealthPercent(null, 4_500_000L))
        assertNull(BatteryHealthUtil.stateOfHealthPercent(5_000_000L, null))
        assertNull(BatteryHealthUtil.stateOfHealthPercent(0L, 4_500_000L))
        assertNull("قراءة فاسدة (خمس أضعاف التصميم) لا تُنشَر", BatteryHealthUtil.stateOfHealthPercent(1_000_000L, 5_000_000L))
    }

    @Test
    fun source_distinguishesMeasuredEstimatedUnsupported() {
        assertEquals(
            BatteryHealthSource.MEASURED,
            BatteryHealth(5_000_000L, 4_500_000L, null, 90).source,
        )
        assertEquals(
            BatteryHealthSource.ESTIMATED,
            BatteryHealth(null, null, 120, null).source,
        )
        assertEquals(
            BatteryHealthSource.UNSUPPORTED,
            BatteryHealth(null, null, null, null).source,
        )
    }

    // ── AR-11: التخزين ────────────────────────────────────────────────────────

    @Test
    fun hexToken_readsFirstJedecValue() {
        assertEquals(1, StorageHealthUtil.parseHexToken("0x01 0x02"))
        assertEquals(11, StorageHealthUtil.parseHexToken("0x0B"))
        assertEquals(3, StorageHealthUtil.parseHexToken("3"))
        assertNull(StorageHealthUtil.parseHexToken(null))
        assertNull(StorageHealthUtil.parseHexToken("   "))
    }

    @Test
    fun lifeTimePair_splitsBothEstimates() {
        assertEquals(1 to 2, StorageHealthUtil.parseLifeTimePair("0x01 0x02"))
        assertEquals(11 to null, StorageHealthUtil.parseLifeTimePair("0x0B"))
        assertNull(StorageHealthUtil.parseLifeTimePair(null))
    }

    @Test
    fun lifeTimePercent_followsJedecBuckets() {
        assertEquals(10, StorageHealthUtil.lifeTimePercent(1))
        assertEquals(100, StorageHealthUtil.lifeTimePercent(10))
        assertEquals("0x0B = تجاوز الحد", 100, StorageHealthUtil.lifeTimePercent(11))
        assertNull("0 = غير معرّف", StorageHealthUtil.lifeTimePercent(0))
        assertNull(StorageHealthUtil.lifeTimePercent(null))
    }

    @Test
    fun storage_supportedRequiresEvidence() {
        assertEquals(false, StorageMediaHealth(null, null, null, null).supported)
        assertEquals(true, StorageMediaHealth("mmcblk0", 1, 1, 1).supported)
        assertEquals(true, StorageMediaHealth("mmcblk0", null, null, 2).supported)
    }
}
