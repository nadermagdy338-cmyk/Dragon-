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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `OCR-06` بلا Android: الحصيلة، وصفّ التجميع، وعدد الظاهر.
 *
 * **ولماذا هذه الأرقام تستحق اختبارًا:** العدّاد في العنوان وصفّ «+N أخرى» هما كل ما يراه
 * المستخدم من قائمة قد تكون ١١٩ نسخة. وخطأ صغير فيهما (مجموع ناقص، أو «+0 أخرى») يُقرأ كعطب
 * في القائمة نفسها لا في الحساب — فيُبحث عنه في المكان الخطأ.
 */
class MaxBackupCountsTest {

    private fun fact(
        pkg: String = "nd.max",
        at: Long = 1_000L,
        bytes: Long = 100L,
        entries: Int = 3,
        complete: Boolean = true,
    ) = MaxBackupCounts.CopyFact(pkg, at, bytes, entries, complete)

    @Test
    fun `a summary adds up what the screen claims`() {
        val summary = MaxBackupCounts.summarize(
            listOf(
                fact(at = 1_000L, bytes = 100L, entries = 3),
                fact(at = 2_000L, bytes = 250L, entries = 7),
                fact(at = 500L, bytes = 50L, entries = 0),
            )
        )
        assertEquals(3, summary.copies)
        assertEquals(10, summary.entries)
        assertEquals(400L, summary.bytes)
        assertEquals(2_000L, summary.newestAtMs)
        assertEquals(0, summary.incomplete)
    }

    @Test
    fun `incomplete copies are counted, because they are the ones that need a person`() {
        val summary = MaxBackupCounts.summarize(
            listOf(fact(complete = true), fact(complete = false), fact(complete = false))
        )
        assertEquals(2, summary.incomplete)
        assertEquals(3, summary.copies)
    }

    @Test
    fun `an empty list has no newest copy rather than a false one`() {
        val summary = MaxBackupCounts.summarize(emptyList())
        assertTrue(summary.isEmpty)
        assertEquals(0, summary.copies)
        assertEquals(0L, summary.bytes)
        // `null` لا `0L`: «لا نسخ» شيء و«نسخة بطابع زمني صفري» شيء آخر.
        assertNull(summary.newestAtMs)
    }

    @Test
    fun `the entry total does not overflow into a negative number`() {
        val many = listOf(fact(entries = Int.MAX_VALUE), fact(entries = Int.MAX_VALUE))
        assertTrue(MaxBackupCounts.summarize(many).entries > 0)
    }

    @Test
    fun `overflow is what is hidden, and it is never a negative promise`() {
        assertEquals(114, MaxBackupCounts.overflow(5, 119))
        assertEquals(0, MaxBackupCounts.overflow(119, 119))
        // لو قُدّم إلينا عدد أكبر من الموجود: لا «+سالب»، والقائمة لا تُطوى على شيء.
        assertEquals(0, MaxBackupCounts.overflow(200, 119))
    }

    @Test
    fun `visible count is the limit until the list is opened, then everything`() {
        assertEquals(5, MaxBackupCounts.visibleCount(total = 119, limit = 5, expanded = false))
        assertEquals(119, MaxBackupCounts.visibleCount(total = 119, limit = 5, expanded = true))
        assertEquals(3, MaxBackupCounts.visibleCount(total = 3, limit = 5, expanded = false))
    }

    @Test
    fun `copies are counted per package, which is what a row claims`() {
        val perPackage = MaxBackupCounts.perPackage(
            listOf(fact("a.app"), fact("a.app"), fact("b.app"))
        )
        assertEquals(2, perPackage["a.app"])
        assertEquals(1, perPackage["b.app"])
        assertNull(perPackage["c.app"])
    }
}
