/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حارس مصدر لعقد المفتاح الرئيسي في Max AI — **لا يُغني عن قياس على جهاز**، لكنه يمنع
 * رجوع العطب الذي شكاه المالك حرفيًّا («زر التفعيل يأخذ ~دقيقة»).
 *
 * والعطب كان سببان متعاضدان، وكلٌّ له دعوى قابلة للتكذيب هنا:
 *
 * 1. **الحالة تُنشر بعد دورة كاملة** (وفيها `RESPONSE_WINDOW_MS` لكل مقبض) ⇒ الزر لا يستجيب
 *    عشرات الثواني. الدعوى: `_state.update` يجب أن **يسبق** `runCycleSingleFlight()`.
 * 2. **كتابة الخاصية لا تنتظر** (`Shell.cmd(…).submit()` في مسار `PropertyUtils.set` الاحتياطي)
 *    ⇒ الدورة التالية تقرأ قيمة قديمة فتُرجع الزر. الدعوى: مسار التبديل يستعمل
 *    `setAndConfirm` (المُتحقَّقة) ولا يحتوي `.submit()` أصلًا.
 */
class MaxAiMasterSwitchTest {

    private lateinit var sourceRoot: File

    @Before
    fun locateSourceRoot() {
        val found = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).firstOrNull { it.isDirectory }
        assumeTrue("Cannot locate nd.max sources; guard not evaluated", found != null)
        sourceRoot = found!!
    }

    /** النص **بلا تعليقات**: الحارس يحكم على الشيفرة، لا على ما نقوله عنها. */
    private fun source(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    private fun masterSwitchBody(): String {
        val engine = source("core/maxai/MaxAiEngine.kt")
        return engine.substringAfter("fun setAiEnabled(").substringBefore("fun setObjectivePreference(")
    }

    @Test
    fun `the switch state is updated before the cycle is launched`() {
        val body = masterSwitchBody()
        val update = body.indexOf("_state.update")
        val cycle = body.indexOf("runCycleSingleFlight()")
        assertTrue("the switch must touch state at all", update >= 0)
        assertTrue("the cycle must still run", cycle >= 0)
        assertTrue(
            "state must be reflected before the cycle runs, not only by publish() inside it",
            update in 0 until cycle,
        )
    }

    @Test
    fun `the property write is awaited and cannot be fire-and-forget`() {
        val body = masterSwitchBody()
        assertTrue(
            "the master switch must use the confirmed write, not the non-blocking set()",
            body.contains("PropertyUtils.setAndConfirm("),
        )
        assertFalse(
            "no fire-and-forget write on the switch path: it reads back what it just wrote",
            body.contains(".submit()"),
        )
    }

    @Test
    fun `the confirmed write verifies the value instead of promising it`() {
        val util = source("ui/util/PropertyUtil.kt")
        val confirmed = util.substringAfter("fun setAndConfirm(")
        assertTrue("it must read the property back", Regex("get\\s*\\(\\s*key").containsMatchIn(confirmed))
        assertTrue("it must be able to await the shell fallback", confirmed.contains(".exec()"))
        assertFalse("and it must not promise what it did not wait for", confirmed.contains(".submit()"))
    }

    @Test
    fun `the mode file the daemon reads is written synchronously`() {
        assertTrue(
            "current_modes must be written with exec(), not submitted",
            masterSwitchBody().contains("current_modes").and(
                masterSwitchBody().substringAfter("current_modes").contains(".exec()"),
            ),
        )
    }
}
