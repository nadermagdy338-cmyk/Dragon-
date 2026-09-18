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
 * عقد `AR-05`: التحليل نقي، و«غير المعروف» لا يُفسَّر كـ«سليم» (ADR-07).
 */
class ModuleHealthUtilTest {

    @Test
    fun bootCount_readsTheValuePostFsDataWrites() {
        assertEquals(1, ModuleHealthUtil.parseBootCount("BOOTCOUNT=1"))
        assertEquals(12, ModuleHealthUtil.parseBootCount("BOOTCOUNT=12\n"))
        assertEquals(3, ModuleHealthUtil.parseBootCount("BOOTCOUNT = 3"))
    }

    @Test
    fun bootCount_absentOrMalformedIsNullNotZero() {
        assertNull(ModuleHealthUtil.parseBootCount(null))
        assertNull(ModuleHealthUtil.parseBootCount(""))
        assertNull(ModuleHealthUtil.parseBootCount("something else"))
    }

    @Test
    fun versionCode_readsModulePropLine() {
        val prop = "id=MaxManager\nname=MaxManager\nversion=V1\nversionCode=1823\n"
        assertEquals(1823, ModuleHealthUtil.parseVersionCode(prop))
    }

    @Test
    fun versionCode_absentIsMinusOne() {
        assertEquals(-1, ModuleHealthUtil.parseVersionCode(null))
        assertEquals(-1, ModuleHealthUtil.parseVersionCode("version=V1"))
    }

    @Test
    fun recoveryEvents_countsNonBlankLines() {
        assertEquals(3, ModuleHealthUtil.countRecoveryEvents("a\n\nb\n c \n"))
        assertEquals(0, ModuleHealthUtil.countRecoveryEvents(""))
        assertNull(ModuleHealthUtil.countRecoveryEvents(null))
    }

    @Test
    fun rescueTriggered_needsEvidence() {
        // دليل قاطع: الوحدة معطّلة
        assertTrue(health(disabled = true, bootCount = null).rescueTriggered == true)
        // دليل قاطع: عدّاد إقلاع أكبر من واحد
        assertTrue(health(disabled = false, bootCount = 2).rescueTriggered == true)
        // لا إنقاذ: مقروء وعدّاده واحد
        assertFalse(health(disabled = false, bootCount = 1).rescueTriggered == true)
        // غير معروف: لا ندّعي شيئًا
        assertNull(health(disabled = null, bootCount = null).rescueTriggered)
    }

    @Test
    fun recoveryHistory_isKnownWhenReadable() {
        assertTrue(health(recoveryCount = 2).hasRecoveryHistory)
        assertFalse(health(recoveryCount = 0).hasRecoveryHistory)
        assertFalse("غير المقروء ليس «لا شيء»", health(recoveryCount = null).hasRecoveryHistory)
    }

    private fun health(
        disabled: Boolean? = null,
        bootCount: Int? = null,
        recoveryCount: Int? = null,
    ) = ModuleHealth(
        installed = true,
        disabled = disabled,
        bootCount = bootCount,
        recoveryEventCount = recoveryCount,
        lastRecoveryEvent = null,
        updatePending = null,
        versionCode = -1,
        hasOriginalProp = null,
    )
}
