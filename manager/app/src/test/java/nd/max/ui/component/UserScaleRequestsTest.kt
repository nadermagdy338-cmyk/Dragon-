/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * طلب بنسبة مئويّة — **يُقاس بقراءة الرقم لا بلقطة**: «وزر معلومات الجهاز أكبر بنسبة ٤٪».
 *
 * (وكان معه «أوّل بطاقة أصغر بنسبة ٥٪»؛ سقط حارسه لأن تلك البطاقة — `PulsePanel` — استُبدلت
 * بـ`HomeHeroCard` المضغوطة أصلًا، وبقاء الحارس كان سيحرس سطورًا لم تعد في الكود.
 * وحرّاس الشكل الجديد في `HomeShapeContractTest`.)
 *
 * **ولماذا اختبار نصّيّ لا اختبار رسم:** النسبة نفسها رقمٌ في الكود (`COMPACT_SCALE`)، وهذا ما
 * يُرسّخه الحارس: ألّا يُمحى الرقم بضغطة عابرة، وأن يبقى **مطبَّقًا على كل** ما وُعد به.
 *
 * **وحدّه المُعلَن:** هل يبدو الزرّ أكبر — **يحتاج جهازًا**. المقيس هنا: الرقم، ومواضع تطبيقه،
 * وأنّ **حدّ اللمس ٤٨dp لم يُمسّ** (وهو ما كانت النسبة تستطيع سحقه).
 */
class UserScaleRequestsTest {

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

    private fun read(path: String): String = File(sourceRoot, path).readText()

    @Test
    fun `the compact pill is raised by exactly four percent, on every number it owns`() {
        val pill = read("ui/component/NeuralPill.kt")
        assertTrue(
            "‏+٤٪ رقم واحد مسمّى لا حرفيّ في كل سطر",
            pill.contains("const val COMPACT_SCALE = 1.04f"),
        )
        listOf(
            "val hPad = (if (compact) MaxSpace.sm else 10.dp) * scale",
            "val vPad = (if (compact) MaxSpace.xs else 5.dp) * scale",
            "val gap = (if (compact) MaxSpace.xs else 6.dp) * scale",
            "val dotSize = (if (compact) 5.dp else 6.dp) * scale",
            "val iconSize = (if (compact) 12.dp else 13.dp) * scale",
            "val arrowSize = (if (compact) 12.dp else 14.dp) * scale",
        ).forEach { applied ->
            assertTrue("مقاس لم يحمل المعامل — كبرٌ في موضع ونقصٌ في آخر: $applied", pill.contains(applied))
        }
        assertTrue(
            "وحجم النصّ يتبعهم، وإلا كبرت الورقة وصغرت كلمتها",
            pill.contains("fontSize = 11.sp * scale") && pill.contains("lineHeight = 14.sp * scale"),
        )
        assertTrue(
            "والقياسيّ لا يتغيّر: المعامل 1f",
            pill.contains("val scale = if (compact) COMPACT_SCALE else 1f"),
        )
    }

    @Test
    fun `the pill keeps its forty-eight dp touch box, which is a policy and not a style`() {
        val pill = read("ui/component/NeuralPill.kt")
        assertTrue(
            "صندوق اللمس ٤٨dp قائم (والمضغوط هو الذي يحتاجه أكثر: شكلُه أصغر من الحدّ)",
            pill.contains("heightIn(min = MaxSize.minTouchTarget)"),
        )
        assertFalse(
            "ولا يُضرب المعامل في حدّ اللمس — الحدّ سياسة (§١٣) لا ذوق",
            pill.contains("minTouchTarget *"),
        )
    }
}
