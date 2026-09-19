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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `OCR-10` بلا Android. وأهم ما يُقاس هنا **قاعدة المجهول**: حجم أو تاريخ غير مقيس لا يُعَدّ
 * مطابقًا لمرشّح يقيسه — وإلا صار العدد المعروض في الشاشة كاذبًا.
 */
class FileSearchFiltersTest {

    private val now = 1_700_000_000L

    private fun file(
        name: String,
        size: Long? = 1024L,
        modified: Long? = 1_700_000_000L,
    ) = FileEntry(
        name = name,
        path = "/storage/emulated/0/$name",
        kind = FileKind.RegularFile,
        sizeBytes = size,
        modifiedEpochSec = modified,
    )

    private fun folder(name: String) = FileEntry(
        name = name,
        path = "/storage/emulated/0/$name",
        kind = FileKind.Directory,
        sizeBytes = null,
        modifiedEpochSec = now,
    )

    @Test
    fun `the extension decides the content type, not the filesystem shape`() {
        assertEquals(FileSearchFilters.Kind.IMAGE, FileSearchFilters.classify("IMG_2.JPG", false))
        assertEquals(FileSearchFilters.Kind.IMAGE, FileSearchFilters.classify("shot.jpeg", false))
        assertEquals(FileSearchFilters.Kind.VIDEO, FileSearchFilters.classify("clip.mp4", false))
        assertEquals(FileSearchFilters.Kind.AUDIO, FileSearchFilters.classify("track.flac", false))
        assertEquals(FileSearchFilters.Kind.DOCUMENT, FileSearchFilters.classify("notes.pdf", false))
        assertEquals(FileSearchFilters.Kind.ARCHIVE, FileSearchFilters.classify("backup.zip", false))
        assertEquals(FileSearchFilters.Kind.APK, FileSearchFilters.classify("app.apk", false))
        assertEquals(FileSearchFilters.Kind.FOLDER, FileSearchFilters.classify("DCIM", true))
        // بلا امتداد، أو بنقطة في الآخر فقط: لا نوع — ويُطابق حين لا يوجد ترشيح نوع.
        assertEquals(FileSearchFilters.Kind.ANY, FileSearchFilters.classify("README", false))
        assertEquals(FileSearchFilters.Kind.ANY, FileSearchFilters.classify("odd.", false))
        assertEquals(FileSearchFilters.Kind.ANY, FileSearchFilters.classify(".hidden", false))
    }

    @Test
    fun `an inactive filter keeps the folder exactly as it is`() {
        val entries = listOf(file("a.jpg"), file("b.zip"), folder("DCIM"))
        val filter = FileSearchFilters.Filter()
        assertFalse(filter.isActive)
        assertEquals(entries, FileSearchFilters.apply(entries, filter, now))
    }

    @Test
    fun `a size filter never counts what we could not measure`() {
        val entries = listOf(
            file("big.bin", size = 20L * 1024 * 1024),
            file("small.bin", size = 1024L),
            // حجم غير مقيس: مجلد، أو ملف لم نقرأ وصفه. لا يُعَدّ مطابقًا لـ«أكبر من ١٠ م.ب».
            file("unknown.bin", size = null),
        )
        val over10 = FileSearchFilters.Filter(size = FileSearchFilters.Size.OVER_10MB)
        val kept = FileSearchFilters.apply(entries, over10, now)
        assertEquals(listOf("big.bin"), kept.map { it.name })
    }

    @Test
    fun `an age filter never counts what we could not date`() {
        val old = file("old.txt", modified = now - 40L * 24 * 60 * 60)
        val yesterday = file("recent.txt", modified = now - 20L * 60 * 60)
        val undated = file("undated.txt", modified = null)

        val today = FileSearchFilters.Filter(age = FileSearchFilters.Age.TODAY)
        assertEquals(listOf("recent.txt"), FileSearchFilters.apply(listOf(old, yesterday, undated), today, now).map { it.name })

        val week = FileSearchFilters.Filter(age = FileSearchFilters.Age.WEEK)
        assertEquals(listOf("recent.txt"), FileSearchFilters.apply(listOf(old, yesterday, undated), week, now).map { it.name })

        val month = FileSearchFilters.Filter(age = FileSearchFilters.Age.MONTH)
        assertEquals(listOf("recent.txt"), FileSearchFilters.apply(listOf(old, yesterday, undated), month, now).map { it.name })
    }

    @Test
    fun `a file dated in the future is not thrown out as impossible`() {
        // ساعة الجهاز قد تتأخّر عن زمن ملف كُتب على تخزين آخر (بطاقة، جهاز آخر). استبعاده
        // يعني اختفاء ملف موجود من نتائج «آخر ٢٤ ساعة».
        val future = file("future.txt", modified = now + 3600L)
        val today = FileSearchFilters.Filter(age = FileSearchFilters.Age.TODAY)
        assertTrue(FileSearchFilters.matches(future, today, now))
    }

    @Test
    fun `folders are matched by the folder kind and not by their missing size`() {
        val dir = folder("DCIM")
        val onlyImages = FileSearchFilters.Filter(kind = FileSearchFilters.Kind.IMAGE)
        assertFalse(FileSearchFilters.matches(dir, onlyImages, now))

        val onlyFolders = FileSearchFilters.Filter(kind = FileSearchFilters.Kind.FOLDER)
        assertTrue(FileSearchFilters.matches(dir, onlyFolders, now))

        // ومرشّح الحجم على مجلد: لا حجم ⇒ لا مطابقة، ولا خطأ.
        val over1mb = FileSearchFilters.Filter(kind = FileSearchFilters.Kind.FOLDER, size = FileSearchFilters.Size.OVER_1MB)
        assertFalse(FileSearchFilters.matches(dir, over1mb, now))
    }

    @Test
    fun `filters combine, which is the whole point of three separate dimensions`() {
        val entries = listOf(
            file("shot.jpg", size = 12L * 1024 * 1024, modified = now - 3600L),
            file("small.jpg", size = 100L * 1024, modified = now - 3600L),
            file("old.jpg", size = 12L * 1024 * 1024, modified = now - 60L * 24 * 60 * 60),
            file("clip.mp4", size = 50L * 1024 * 1024, modified = now - 3600L),
        )
        val filter = FileSearchFilters.Filter(
            kind = FileSearchFilters.Kind.IMAGE,
            size = FileSearchFilters.Size.OVER_10MB,
            age = FileSearchFilters.Age.WEEK,
        )
        assertEquals(listOf("shot.jpg"), FileSearchFilters.apply(entries, filter, now).map { it.name })
    }

    @Test
    fun `stopping the filter brings the whole folder back`() {
        val entries = listOf(file("a.jpg"), file("b.zip"))
        val filtered = FileSearchFilters.apply(entries, FileSearchFilters.Filter(kind = FileSearchFilters.Kind.IMAGE), now)
        assertEquals(1, filtered.size)
        assertEquals(entries, FileSearchFilters.apply(entries, FileSearchFilters.Filter(), now))
    }
}
