/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس على **شكل** نافذة الاستجابة في `MaxAiEngine`، لا اختبار سلوكي.
 *
 * العطب المحروس: بين الكتابة المُتحقَّقة على العتاد وحكمها نافذةٌ يمرّ فيها
 * `delay(RESPONSE_WINDOW_MS)` — عشر ثوانٍ. و`delay` نقطة إلغاء، فإلغاء نطاق
 * (شاشة تُغلق، أو نطاق محرك يُلغى) أو أي استثناء داخل النافذة كان يتخطى
 * الحكم **والاسترجاع معًا**: يبقى المقبض مكتوبًا على العتاد بلا حكم وبلا
 * حلقة تشرحه.
 *
 * وهذه الحرّاس تقيس الشكل الذي يمنع ذلك، وتمنع تراجعه. ولا تُغني عن قياس على
 * جهاز: لا شيء هنا يُثبت أن المُحكِّم يكتب فعلًا ولا أن الاسترجاع يُقبل في
 * العتاد — بل يُثبت أن الكود لا يستطيع مغادرة النافذة دون المرور بالاسترجاع.
 */
class MaxAiInterruptSafetyTest {
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

    /**
     * الكود دون تعليقاته: تُقاس البنية، ولا تُقاس صياغة التعليق. ولهذا تفشل
     * هذه الحرّاس إن كان الحارس موجودًا في تعليق فقط.
     */
    private fun engine(): String = File(sourceRoot, "core/maxai/MaxAiEngine.kt").readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    /**
     * جسم تعريف، من توقيعه إلى التعريف التالي.
     *
     * ويُشترط وجود **التوقيعين** قبل القص: `substringAfter` تُعيد النص كله إذا
     * غاب الفاصل، فقصٌّ فاشل يجعل الحارس يقيس منطقة خطأ ثم يُبلّغ برسالة
     * مضلِّلة. الشرط يجعل الفشل صريحًا في مكانه الحقيقي.
     */
    private fun function(signature: String, nextSignature: String): String {
        val text = engine()
        assertTrue(
            "Declaration vanished, so every guard over it is unanchored: $signature",
            text.contains(signature),
        )
        assertTrue(
            "Following declaration vanished, so the slice has no end: $nextSignature",
            text.contains(nextSignature),
        )
        return text.substringAfter(signature).substringBefore(nextSignature)
    }

    private fun helper(): String = function(
        "private fun restoreInterruptedWrite(",
        "private suspend fun exploreIfWorthwhile(",
    )

    @Test
    fun `the restore helper cannot suspend`() {
        val helper = helper()
        assertTrue("restore helper body not found", helper.contains("arbiter.release"))
        // هذا هو الشرط الذي يجعل الحارس حقيقيًا: نداء معلَّق داخل finally بعد
        // الإلغاء يرمي فورًا، فلا يقع الاسترجاع أصلًا — أي بابٌ شكله حماية.
        val suspending = listOf("suspend", "delay(", "withContext", "yield()", ".await()", "runBlocking")
            .filter { helper.contains(it) }
        assertTrue(
            "A suspend call in a finally after cancellation throws immediately, so the restore " +
                "would never run. Offending: $suspending",
            suspending.isEmpty(),
        )
    }

    @Test
    fun `the restore helper cannot replace the interruption it recovers from`() {
        assertTrue(
            "The release must stay wrapped so a throw inside finally cannot mask the original " +
                "cancellation or exception it is recovering from.",
            Regex("runCatching\\s*\\{[^}]*arbiter\\.release", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(helper()),
        )
    }

    @Test
    fun `the probe window restores when it is cut short`() {
        val probe = function("private suspend fun runProbe(", "private fun recordForecast(")
        val open = probe.indexOf("try {")
        val wait = probe.indexOf("delay(RESPONSE_WINDOW_MS)")
        val post = probe.indexOf("safetyGovernor.enforcePost")
        val close = probe.indexOf("} finally {")
        assertTrue("no guarded window in the probe", open >= 0)
        assertTrue("the wait must sit inside the guarded window", wait > open)
        assertTrue("the post-write safety check must sit inside the guarded window", post > open)
        assertTrue("the guarded window must be closed by a finally", close > post)
        assertTrue(
            "the finally must run the shared restore",
            probe.substring(close).contains("restoreInterruptedWrite(step)"),
        )
    }

    @Test
    fun `the decision window restores unless the write was deliberately kept`() {
        val cycle = function("private suspend fun decisionCycle(", "private fun rollbackOnRegression(")
        val open = cycle.indexOf("try {")
        val close = cycle.indexOf("} finally {")
        assertTrue("no guarded window in the decision cycle", open >= 0)
        assertTrue("the wait must sit inside the guarded window", cycle.indexOf("delay(RESPONSE_WINDOW_MS)") > open)
        assertTrue("the guarded window must be closed by a finally", close > open)
        assertTrue(
            "the finally must be guarded by the deliberate-keep flag, so an intended " +
                "improvement is not rolled back",
            cycle.substring(close).contains("if (!keptWrite) restoreInterruptedWrite(step)"),
        )
        assertTrue("the deliberate-keep flag must start false", cycle.contains("var keptWrite = false"))
        assertEquals(
            "There are exactly two deliberate-keep exits: unmeasured, and improved. A third " +
                "means a path that used to hand the baseline back now keeps the write — decide " +
                "that on purpose, not by accident.",
            2,
            Regex("keptWrite = true").findAll(cycle).count(),
        )
    }

    @Test
    fun `both windows restore through the same non-suspending helper`() {
        assertEquals(
            "Each guarded window must restore through the shared helper; a second inline " +
                "restore would be a path this guard does not cover.",
            2,
            Regex("restoreInterruptedWrite\\(step\\)").findAll(engine()).count(),
        )
    }
}
