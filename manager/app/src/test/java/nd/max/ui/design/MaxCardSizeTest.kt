/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * مقاسا البطاقة — **مقيسان بالكود لا بالقراءة**: [MaxCardSize.Medium] يجب أن يكون أصغر من
 * [MaxCardSize.Regular] في **كل رقم يملكه**، وألّا يكون أصغر في رقم لا يملكه.
 *
 * ولماذا اختبار على JVM أصلًا: أمر المالك («اجعل بطاقة منصة التحكم بحجم متوسّط ليست كبيرة وليست
 * صغيرة») حكمٌ بصريّ لا يُقاس هنا بحرفيّته — لكن **الأرقام التي تُنتجه** تُقاس هنا بالضبط، وهي
 * الشيء الذي ينزلق: لو عُدّل `MaxSpace.md` لاحقًا إلى ٢٠ صار «المتوسّط» أكبر من القياسيّ في
 * الحشو بلا أن يقول أحد كلمة. وهذا الاختبار يقرأ الأربعة كلها من مصدرها، فلا يعيد كتابتها.
 *
 * **وحدّ هذا القياس:** كونه أصغر لا يعني أنه **مريح**؛ الراحة تُرى على جهاز. المقيس هنا هو
 * العلاقة بين المقاسين لا حسن المظهر.
 */
class MaxCardSizeTest {

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
    fun `the regular size is the card that was already on every screen`() {
        val regular = MaxCardSize.Regular.metrics
        assertEquals(MaxCardSpec.padding, regular.padding)
        assertEquals(MaxCardSpec.iconContainer, regular.iconContainer)
        assertEquals(MaxCardSpec.iconGlyph, regular.iconGlyph)
        assertEquals(MaxCardSpec.minHeight, regular.minHeight)
    }

    @Test
    fun `the medium size is smaller than the regular one on every number it owns`() {
        val regular = MaxCardSize.Regular.metrics
        val medium = MaxCardSize.Medium.metrics

        assertTrue(
            "الحشو: ${medium.padding} ليس أصغر من ${regular.padding}",
            medium.padding < regular.padding,
        )
        assertTrue(
            "حاوية الأيقونة: ${medium.iconContainer} لا تصغر عن ${regular.iconContainer}",
            medium.iconContainer < regular.iconContainer,
        )
        assertTrue(
            "الأيقونة: ${medium.iconGlyph} لا تصغر عن ${regular.iconGlyph}",
            medium.iconGlyph < regular.iconGlyph,
        )
        assertTrue(
            "أرضية الارتفاع: ${medium.minHeight} لا تنزل عن ${regular.minHeight}",
            medium.minHeight < regular.minHeight,
        )
    }

    @Test
    fun `medium shrinks the card, not the words — the line contract stays the regular one`() {
        // سطرا العنوان وسطرا الوصف سقفٌ للنصّ لا زخرفة: تصغيرهما يعني قصّ كلمة، وهو العطب الذي
        // بُني هذا الملفّ لمنعه (`Powe…`). فلا مقاس يلمسهما — ولا يقدر أن يلمسهما من بنيته.
        assertEquals(2, MaxCardSpec.titleLines)
        assertEquals(2, MaxCardSpec.descriptionLines)

        // والقراءة على **كتلة المقاس وحدها** لا على الملفّ: السقفان معرّفان فوقها في `MaxCardSpec`
        // ولا يجوز أن يظهر لهما نظير داخل `MaxCardMetrics`.
        val block = read("ui/design/MaxTokens.kt")
            .substringAfter("data class MaxCardMetrics")
            .substringBefore("enum class MaxCardSize")
        assertFalse(
            "ولا خطوط داخل المقاس: البطاقة المتوسّطة أقصر، لا أقلّ نصًّا",
            block.contains("titleLines") || block.contains("descriptionLines"),
        )
    }

    @Test
    fun `the grid hands its size down to every card in the row`() {
        val card = read("ui/design/MaxCard.kt")
        assertTrue(
            "الرسم يمرّر المقاس إلى البطاقة",
            card.contains("MaxCard(card, Modifier.weight(1f).fillMaxHeight(), size)"),
        )
        assertTrue(
            "والمقاس افتراضيّه القياسيّ حتى لا تتغيّر شاشة لم تُطلب",
            card.contains("size: MaxCardSize = MaxCardSize.Regular"),
        )
    }
}
