/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات `SetEdit` — والغرض الأهمّ فيها **منع كراش التمرير** لا التحليل وحده:
 * `LazyColumn` يرمي `Key … was already used` إن تكرّر مفتاح صفّ، ولا يظهر ذلك عند فتح الشاشة
 * بل **عند التمرير**، لأن الصفّ المكرّر لا يُبنى إلا حين يدخل نطاق العرض.
 */
class SetEditUtilTest {

    private val settings = SetEditCategory.GLOBAL
    private val prop = SetEditCategory.ANDROID_PROP

    // ────────────────────────────────────────────────────────────────────────
    // التحليل
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `settings line is split on the first separator only`() {
        // القيمة قد تحمل `=` بداخلها (JSON، URL، base64): القطع بـ`limit = 2` ضرورة لا ترتيب.
        val items = SetEditUtil.parseOutput(settings, listOf("""some_key={"a":"b=c"}"""))
        assertEquals(1, items.size)
        assertEquals("some_key", items[0].key)
        assertEquals("""{"a":"b=c"}""", items[0].value)
    }

    @Test
    fun `settings lines without a separator are dropped, not guessed`() {
        val items = SetEditUtil.parseOutput(settings, listOf("no_separator_here", "", "   "))
        assertTrue(items.isEmpty())
    }

    @Test
    fun `prop lines are parsed from the bracket form`() {
        val items = SetEditUtil.parseOutput(prop, listOf("[ro.build.version.sdk]: [34]"))
        assertEquals(1, items.size)
        assertEquals("ro.build.version.sdk", items[0].key)
        assertEquals("34", items[0].value)
        assertEquals(prop, items[0].category)
    }

    @Test
    fun `a prop line carrying the separator inside its value is dropped`() {
        // قيمة تحمل `]: [` تُنتج أكثر من جزأين ⇒ تُسقَط، ولا تُفسَّر خطأً على أنها خاصية أخرى.
        val items = SetEditUtil.parseOutput(prop, listOf("[weird]: [a]: [b]"))
        assertTrue(items.isEmpty())
    }

    @Test
    fun `junk output never throws and never yields a blank key`() {
        val junk = listOf("", " ", "=", "]():[", "::", "\u0000", "a".repeat(5_000), "] ]: [", " = value")
        val items = SetEditUtil.parseOutput(settings, junk) + SetEditUtil.parseOutput(prop, junk)
        // لا استثناء — وهذا أول المطلوب.
        // وثانيه: **لا مفتاح فارغ**. صفّ بلا مفتاح لا يُقرأ ولا يُبحث عنه، وتكتبه الشاشة لاحقًا
        // إلى الجهاز باسم `settings put <ns> ""` — أي أمر يكتب في لا شيء.
        assertTrue("مفتاح فارغ تسرّب من التحليل", items.none { it.key.isBlank() })
    }

    @Test
    fun `an empty key is rejected in both parsers`() {
        assertTrue(SetEditUtil.parseOutput(settings, listOf("=value")).isEmpty())
        assertTrue(SetEditUtil.parseOutput(prop, listOf("[]: [value]")).isEmpty())
    }

    // ────────────────────────────────────────────────────────────────────────
    // التفرد — ضمانة ضد كراش `LazyColumn`
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `duplicate category-key pairs are collapsed and the first one wins`() {
        val items = listOf(
            SetEditItem("adb_enabled", "1", settings),
            SetEditItem("adb_enabled", "tx", settings),
            SetEditItem("keep", "v", settings),
        )
        val unique = SetEditUtil.dedupe(items, settings)
        assertEquals(listOf("adb_enabled", "keep"), unique.map { it.key })
        assertEquals("1", unique[0].value)
    }

    @Test
    fun `the same key in different categories is not a duplicate`() {
        // وهذا واقع مخرج الجهاز: `global:adb_enabled` و`ANDROID_PROP:adb_enabled` صفّان مختلفان.
        val items = listOf(
            SetEditItem("adb_enabled", "1", SetEditCategory.GLOBAL),
            SetEditItem("adb_enabled", "1", SetEditCategory.SECURE),
            SetEditItem("adb_enabled", "1", SetEditCategory.SYSTEM),
            SetEditItem("adb_enabled", "1", SetEditCategory.ANDROID_PROP),
        )
        val unique = SetEditUtil.dedupe(items, null)
        assertEquals(4, unique.size)
        assertEquals(4, unique.map { it.lazyKey }.distinct().size)
    }

    /**
     * **الضمانة التي تمنع الكراش:** كل عناصر القائمة بمفاتيح فريدة — بمعيار المفتاح الذي
     * تستعمله الشاشة فعلًا ([SetEditItem.lazyKey]).
     */
    @Test
    fun `every item yields a unique lazy key even from repeating output`() {
        val repeating = buildList {
            repeat(50) {
                add("adb_enabled=1")
                add("device_provisioned=1")
                add("device_provisioned=1") // تكرار صريح
            }
        }
        val parsed = SetEditUtil.parseOutput(settings, repeating)
        val unique = SetEditUtil.dedupe(parsed, settings)

        val keys = unique.map { it.lazyKey }
        assertEquals("مفتاح صفّ مكرَّر يعني كراش LazyColumn عند التمرير", keys.size, keys.distinct().size)
        assertEquals(2, unique.size)
    }

    @Test
    fun `the all-categories list is sorted by key and stays unique`() {
        val items = listOf(
            SetEditItem("zeta", "1", SetEditCategory.GLOBAL),
            SetEditItem("alpha", "2", SetEditCategory.ANDROID_PROP),
            SetEditItem("zeta", "3", SetEditCategory.GLOBAL),
        )
        val unique = SetEditUtil.dedupe(items, null)
        assertEquals(listOf("alpha", "zeta"), unique.map { it.key })
    }

    @Test
    fun `a single-category list keeps its source order`() {
        // الترتيب داخل صنف واحد هو ترتيب الأمر نفسه؛ إعادة ترتيبه تُفقد المستخدم مكانه.
        val items = listOf(
            SetEditItem("z", "1", settings),
            SetEditItem("a", "2", settings),
        )
        assertEquals(listOf("z", "a"), SetEditUtil.dedupe(items, settings).map { it.key })
    }

    @Test
    fun `an empty list stays empty`() {
        assertTrue(SetEditUtil.dedupe(emptyList(), null).isEmpty())
    }
}
