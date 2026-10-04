/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * طيّ البحث ورتبته — **يقاس على JVM**، وكل مثال هنا من نصّ واجهة حقيقي.
 *
 * ولماذا لا يُترك للعين: «البحث لا يجد الشاشة» عطبٌ صامت (لا استثناء ولا سجلّ)، ويظهر
 * **بلوحة مفاتيح المستخدم** لا بلوحتنا — وهذا ما لا تراه مراجعةُ كود ولا لقطةُ شاشة، وتراه
 * هذه الأمثلة في أجزاء من الثانية.
 */
class MaxSearchTest {

    // ── الطيّ: ما يكتبه المستخدم مقابل ما كتبناه ──────────────────────────────

    @Test
    fun `the alef family the keyboard does not offer folds to one letter`() {
        assertEquals(maxSearchFold("الحرارة"), maxSearchFold("الحراره"))
        assertEquals(maxSearchFold("الإعدادات"), maxSearchFold("الاعدادات"))
        assertEquals(maxSearchFold("أدوات"), maxSearchFold("ادوات"))
        assertEquals(maxSearchFold("آخر قراءة"), maxSearchFold("اخر قراءة"))
        assertEquals(maxSearchFold("إذن"), maxSearchFold("اذن"))
    }

    @Test
    fun `tashkeel and tatweel are not letters`() {
        assertEquals(maxSearchFold("السجل"), maxSearchFold("السّجِل"))
        assertEquals(maxSearchFold("السجل"), maxSearchFold("السـجل"))
        assertEquals(maxSearchFold("مُحرّر القيم"), maxSearchFold("محرر القيم"))
    }

    @Test
    fun `hamza carriers and the ta marbuta fold to the letter under the finger`() {
        assertEquals(maxSearchFold("مسؤول"), maxSearchFold("مسوول"))
        assertEquals(maxSearchFold("قائمة"), maxSearchFold("قايمه"))
        assertEquals(maxSearchFold("مستوى"), maxSearchFold("مستوي"))
    }

    @Test
    fun `case and latin diacritics fold away, and no locale is consulted`() {
        assertEquals("cafe", maxSearchFold("Café"))
        assertEquals(maxSearchFold("Konfiguration"), maxSearchFold("konfiguration"))
        assertEquals("max", maxSearchFold("MÁX"))
        // التركية: `Locale.forLanguageTag("tr")` تُبدّل حالة `i`، فلو مرّ الطيّ بـ`Locale`
        // الافتراضيّ لتغيّر **بحث المستخدم** مع لغة الهاتف بدل أن يتغيّر مع النصّ.
        assertEquals(maxSearchFold("İstanbul"), maxSearchFold("istanbul"))
    }

    @Test
    fun `whitespace collapses, and never leads or trails`() {
        assertEquals("تحكم النواه", maxSearchFold("  تحكّم   النواة  "))
        assertEquals("", maxSearchFold(""))
        assertEquals("", maxSearchFold("   "))
    }

    @Test
    fun `two different words stay different`() {
        assertNotEquals(maxSearchFold("الذاكرة"), maxSearchFold("الذخيرة"))
    }

    // ── الرتبة: أين وقعت الكلمة ───────────────────────────────────────────────

    @Test
    fun `rank prefers the whole name, then its start, then a word inside it, then the middle`() {
        assertEquals(0, maxSearchRank("الذاكرة", "الذاكرة"))
        assertEquals(1, maxSearchRank("جدولة Network", "جدولة"))
        // «ال» التعريف ملتصقة، فمطابقةٌ بعدها **بدايةُ كلمة** لا وسطها — و«حرار» في «الحرارة»
        // من هذا الباب لا من باب «يبدأ به الاسم»: الاسم يبدأ بـ«ال».
        assertEquals(2, maxSearchRank("الحرارة الحيّة", "حرار"))
        assertEquals(2, maxSearchRank("تحكّم النواة", "نواة"))
        assertEquals(2, maxSearchRank("ضبط سقف الحرارة", "حرار"))
        assertEquals(2, maxSearchRank("جدولة Network", "network"))
        // داخل الكلمة نفسها لا في بدايتها.
        assertEquals(3, maxSearchRank("ضبط سقف الحرارة", "رار"))
        assertEquals(3, maxSearchRank("StorageDetail", "raged"))
    }

    @Test
    fun `a query that is not there has no rank, and an empty one never matches`() {
        assertEquals(-1, maxSearchRank("الحرارة", "الشبكة"))
        assertEquals(-1, maxSearchRank("الحرارة", ""))
        assertEquals(-1, maxSearchRank("", "الحرارة"))
        assertEquals(-1, maxSearchRank("الحرارة", "   "))
    }

    @Test
    fun `the rank of a folded needle equals the rank of the raw one`() {
        // الخطأ الذي يجعل البحث يعمل في اختبار ويفشل في شاشة: مقارنة صورة مطويّة بأخرى خام.
        assertEquals(
            maxSearchRank("مُحرّر القيم", "محرر"),
            maxSearchRankFolded("مُحرّر القيم", foldedNeedle = maxSearchFold("محرر")),
        )
    }
}
