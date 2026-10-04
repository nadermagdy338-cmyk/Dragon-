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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `UX-01` — تُقاس حسبة «الرئيسية» بمدخلات وصفيّة في JVM.
 *
 * وأهمّ ما يُحرَس هنا: **الصفر الكاذب**. قسم لم يُقرأ يجب أن يبقى «غير مقروء» حتى الشاشة،
 * ولا يتحوّل إلى `0` في الطريق — لأن رقمًا صفرًا يُقرأ «لا صور في هاتفك» وهو كذب.
 */
class FileHomeModelTest {

    // ────────────────────────────────────────────────────────────────────────
    // لا صفر كاذب
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a section we never read stays unreadable and never zero`() {
        val row = FileHomeModel.rowOf(CategoryReading(FileCategory.Images))
        assertEquals(FileRowState.Unreadable, row.state)
        assertNull("لا يُكمل العدد بصفر", row.count)
        assertNull(row.sizeBytes)
        assertFalse(row.countKnown)
        assertFalse(row.hasData)
    }

    @Test
    fun `a section we were not allowed to read says so`() {
        val row = FileHomeModel.rowOf(
            CategoryReading(FileCategory.Packages, count = 12, sizeBytes = 5_000, permitted = false),
        )
        assertEquals(FileRowState.NoPermission, row.state)
        assertNull("المنع لا يُعرض كعدد", row.count)
        assertNull(row.sizeBytes)
    }

    @Test
    fun `a read section that is really empty is the only honest zero`() {
        val row = FileHomeModel.rowOf(CategoryReading(FileCategory.Audio, count = 0))
        assertEquals(FileRowState.Empty, row.state)
        assertEquals(0L, row.count)
        assertEquals(0L, row.sizeBytes)
    }

    @Test
    fun `a known count with an unknown size keeps the count and no size`() {
        val row = FileHomeModel.rowOf(CategoryReading(FileCategory.Videos, count = 3))
        assertEquals(FileRowState.HasData, row.state)
        assertEquals(3L, row.count)
        assertNull("الحجم المجهول لا يصير صفرًا", row.sizeBytes)
        assertFalse(row.sizeKnown)
    }

    /** عشرة ملفّات فارغة حجمها `0 B` حقيقة — فلا يُردّ الصفر الصادق إلى المجهول. */
    @Test
    fun `a genuine zero size is kept as a known size`() {
        val row = FileHomeModel.rowOf(CategoryReading(FileCategory.Documents, count = 10, sizeBytes = 0))
        assertEquals(FileRowState.HasData, row.state)
        assertEquals(0L, row.sizeBytes)
        assertTrue(row.sizeKnown)
    }

    /** قراءة سالبة أثرُ عطب لا قيمة: تُردّ إلى المجهول ولا تُعرض سلبية. */
    @Test
    fun `a negative reading is treated as unknown`() {
        val row = FileHomeModel.rowOf(CategoryReading(FileCategory.Archives, count = -1, sizeBytes = -5))
        assertEquals(FileRowState.Unreadable, row.state)
        assertNull(row.count)
        assertNull(row.sizeBytes)
    }

    // ────────────────────────────────────────────────────────────────────────
    // كل قسم معلن يظهر، وبالترتيب المعلن
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `every declared category is shown even when nothing was read`() {
        val rows = FileHomeModel.rows(emptyList())
        assertEquals(FileHomeModel.ORDER.size, rows.size)
        assertEquals(FileHomeModel.ORDER, rows.map { it.category })
        assertTrue("القسم الغائب يظهر (غير مقروء) ولا يختفي", rows.all { it.state == FileRowState.Unreadable })
    }

    @Test
    fun `the declared order holds whatever order the readings arrive in`() {
        val rows = FileHomeModel.rows(
            listOf(
                CategoryReading(FileCategory.Packages, count = 1),
                CategoryReading(FileCategory.Images, count = 2),
                CategoryReading(FileCategory.Documents, count = 3),
            ),
        )
        assertEquals(
            listOf(FileCategory.Images, FileCategory.Videos, FileCategory.Audio, FileCategory.Documents, FileCategory.Archives, FileCategory.Packages),
            rows.map { it.category },
        )
    }

    @Test
    fun `the first reading of a category wins so the verdict is stable`() {
        val rows = FileHomeModel.rows(
            listOf(
                CategoryReading(FileCategory.Images, count = 2, sizeBytes = 100),
                CategoryReading(FileCategory.Images, count = 9, sizeBytes = 900),
            ),
        )
        val images = rows.first { it.category == FileCategory.Images }
        assertEquals(2L, images.count)
        assertEquals(100L, images.sizeBytes)
    }

    // ────────────────────────────────────────────────────────────────────────
    // المجاميع: ناقصة تُعلن ولا تُكمَّل
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `totals add the known and declare themselves incomplete`() {
        val totals = FileHomeModel.totals(
            FileHomeModel.rows(
                listOf(
                    CategoryReading(FileCategory.Images, count = 4, sizeBytes = 400),
                    // الفيديو لم يُقرأ: لا يُحسب صفرًا ولا يُسقط الحكم على الباقي.
                ),
            ),
        )
        assertEquals(4L, totals.count)
        assertFalse(totals.complete)
        assertNull("مجموع ناقص لا يُعرض كأنه الحجم كلّه", totals.sizeBytes)
    }

    @Test
    fun `totals expose the size only when every section is known`() {
        val totals = FileHomeModel.totals(
            FileHomeModel.rows(
                FileCategory.entries.map { CategoryReading(it, count = 1, sizeBytes = 10) },
            ),
        )
        assertEquals(FileCategory.entries.size.toLong(), totals.count)
        assertEquals(FileCategory.entries.size.toLong() * 10L, totals.sizeBytes)
        assertTrue(totals.complete)
    }

    @Test
    fun `a count we know with a size we do not keeps the total of counts honest`() {
        val totals = FileHomeModel.totals(
            FileHomeModel.rows(FileCategory.entries.map { CategoryReading(it, count = 2) }),
        )
        assertEquals(FileCategory.entries.size.toLong() * 2L, totals.count)
        assertTrue(totals.complete)
        assertNull(totals.sizeBytes)
    }

    // ────────────────────────────────────────────────────────────────────────
    // مؤخرًا والمفضّلة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `recent keeps the newest visit of every folder once`() {
        val recent = FileHomeModel.recent(
            listOf(
                HistoryEntry("/sdcard/Download", 100),
                HistoryEntry("/sdcard/DCIM", 300),
                HistoryEntry("/sdcard/Download", 200),
            ),
        )
        assertEquals(listOf("/sdcard/DCIM", "/sdcard/Download"), recent.map { it.path })
        assertEquals(200L, recent.first { it.path == "/sdcard/Download" }.atMs)
    }

    @Test
    fun `recent is capped by the declared limit`() {
        val history = (1..40).map { HistoryEntry("/sdcard/f$it", it.toLong()) }
        val recent = FileHomeModel.recent(history)
        assertEquals(FileHomeModel.RECENT_LIMIT, recent.size)
        assertEquals("/sdcard/f40", recent.first().path)
    }

    @Test
    fun `a non positive limit yields nothing`() {
        val history = listOf(HistoryEntry("/sdcard/a", 1))
        assertTrue(FileHomeModel.recent(history, limit = 0).isEmpty())
        assertTrue(FileHomeModel.bookmarks(listOf(FileBookmark("/sdcard/a")), limit = -1).isEmpty())
    }

    @Test
    fun `bookmarks keep the order the user put them in`() {
        val bookmarks = listOf(
            FileBookmark("/sdcard/z", "أخير"),
            FileBookmark("/sdcard/a", "أوّل"),
        )
        assertEquals(listOf("/sdcard/z", "/sdcard/a"), FileHomeModel.bookmarks(bookmarks).map { it.path })
    }

    @Test
    fun `bookmarks are capped by the declared limit`() {
        val bookmarks = (1..20).map { FileBookmark("/sdcard/b$it") }
        assertEquals(FileHomeModel.BOOKMARK_LIMIT, FileHomeModel.bookmarks(bookmarks).size)
    }
}
