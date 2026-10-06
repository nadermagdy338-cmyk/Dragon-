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
 * طلبان بنسبة مئويّة — **يُقاسان بقراءة الرقم لا بلقطة**: «اجعل أوّل بطاقة في الشاشة الرئيسية
 * أصغر بنسبة ٥٪» و«وزر معلومات الجهاز أكبر بنسبة ٤٪».
 *
 * **ولماذا اختبار نصّيّ لا اختبار رسم:** النسبة نفسها رقمٌ في الكود (`PULSE_SCALE` ·
 * `COMPACT_SCALE`)، وهذا ما يُرسّخه الحارس: ألّا يُمحى الرقم بضغطة عابرة، وأن يبقى **مطبَّقًا على
 * كل** ما وُعد به لا على زاوية واحدة منه.
 *
 * **وحدّه المُعلَن:** هل تُقرأ البطاقة أصغر فعلًا، وهل يبدو الزرّ أكبر — **يحتاج جهازًا**. المقيس
 * هنا: الرقم، ومواضع تطبيقه، وأنّ **حدّ اللمس ٤٨dp لم يُمسّ** (وهو ما كانت النسبة تستطيع سحقه).
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

    @Test
    fun `the first home card is lowered by exactly five percent, on its own numbers`() {
        val home = read("ui/mainscreens/LegendaryHomeDashboard.kt")
        assertTrue("‏−٥٪ رقم واحد مسمّى", home.contains("const val PULSE_SCALE = 0.95f"))
        listOf(
            "contentPadding = PaddingValues(18.dp * PULSE_SCALE)",
            "verticalSpacing = 16.dp * PULSE_SCALE",
            "size = 40.dp * PULSE_SCALE",
            "fontSize = 16.sp * PULSE_SCALE",
            "fontSize = 11.sp * PULSE_SCALE",
            "fontSize = 44.sp * PULSE_SCALE",
            "lineHeight = 48.sp * PULSE_SCALE",
            "spacedBy(8.dp * PULSE_SCALE)",
        ).forEach { applied ->
            assertTrue("لم يُطبَّق: $applied", home.contains(applied))
        }
    }

    @Test
    fun `the five percent stops at the card's own numbers and never shaves a shared component`() {
        // المكوّنات المشتركة (زرّ Max AI · حبّة معلومات الجهاز) لها صندوق لمس ٤٨dp ومستعملون
        // آخرون: لو دخلها المعامل لتغيّر مكوّن بحكم بطاقةٍ لا يملكه.
        val pulse = read("ui/mainscreens/LegendaryHomeDashboard.kt")
            .substringAfter("private fun PulsePanel(")
            .substringBefore("Shown only when a real problem exists")

        val maxAiCall = pulse.substringAfter("MaxAiEntryButton(").substringBefore("onMaxAi")
        assertFalse("المعامل تسرّب إلى زرّ Max AI", maxAiCall.contains("PULSE_SCALE"))

        /*
         * **والمَعلَم تغيّر بعد جولة المستوى:** كانت هذه أوّل `NeuralPill(` في البطل، ودخلت
         * قبله حبّة **وضع الوصول** (`accessLabelRes`) في صفّ الهوية. وأخذُ أوّل حبّة صار يقيس
         * حبّة الدولة لا حبّة الجهاز — أي يقيس شيئًا آخر ويقول «تسرّب» أو «لم يتسرّب» عنه.
         * فالمَعلَم صار ما يميّز الحبّة المقصودة فعلًا: **نصّها من سجلّ الوجهة** (`DeviceInfo.titleRes`)
         * — وهي نفسها قاعدة ADR-02 (الاسم من السجلّ لا نصًّا مكتوبًا).
         */
        val pillCall = pulse.substringAfter("MaxDestination.DeviceInfo.titleRes")
            .substringBefore("onOverview")
        assertFalse("المعامل تسرّب إلى حبّة معلومات الجهاز", pillCall.contains("PULSE_SCALE"))
        assertTrue(
            "والحبّة المقيسة هي حبّة الجهاز لا حبّة وضع الوصول",
            pillCall.contains("compact = true"),
        )

        assertTrue(
            "وحدّ اللمس ٤٨dp لزرّ Max AI قائم في موضعه",
            read("ui/component/MaxAiEntryButton.kt").contains("minimumInteractiveComponentSize()"),
        )

    }
}
