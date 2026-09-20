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
 * المفضّلة والسجل — قرارات مستخدم تُحفظ، فخطؤها **يبقى** بين الجلسات (مفضّلة مكرّرة،
 * أو ترتيب يضيع). تُقاس هنا بلا جهاز.
 */
class FileBookmarkModelTest {

    @Test
    fun aNewBookmarkGoesToTheTop() {
        val bookmarks = FileBookmarks.add(
            FileBookmarks.add(emptyList(), "/sdcard/Download"),
            "/data/local/tmp",
        )
        assertEquals(listOf("/data/local/tmp", "/sdcard/Download"), bookmarks.map { it.path })
    }

    /** الإضافة مرّتين لا تُنتج سطرين: التكرار في قائمة محفوظة يبقى حتى يُمسح يدويًّا. */
    @Test
    fun addingTheSameFolderTwiceKeepsOneEntry() {
        val once = FileBookmarks.add(emptyList(), "/sdcard/")
        val twice = FileBookmarks.add(once, "/sdcard")
        assertEquals(1, twice.size)
    }

    @Test
    fun removingUsesTheNormalizedPath() {
        val bookmarks = FileBookmarks.add(FileBookmarks.add(emptyList(), "/a"), "/b")
        assertEquals(listOf("/b"), FileBookmarks.remove(bookmarks, "/a/").map { it.path })
    }

    @Test
    fun renamingTargetsOneEntryAndKeepsTheRest() {
        val bookmarks = listOf(FileBookmark("/a"), FileBookmark("/b"))
        val renamed = FileBookmarks.rename(bookmarks, "/b", "النظام")
        assertEquals("النظام", renamed[1].label)
        assertEquals("", renamed[0].label)
        // التسمية الفارغة تعني «اسم المجلد» — لا تبويب بلا عنوان.
        assertEquals("a", renamed[0].display)
    }

    @Test
    fun movingReordersAndSurvivesOutOfRangeIndexes() {
        val bookmarks = listOf(FileBookmark("/a"), FileBookmark("/b"), FileBookmark("/c"))
        assertEquals(listOf("/c", "/a", "/b"), FileBookmarks.move(bookmarks, 2, 0).map { it.path })
        // سحب خارج المدى أو إلى الموضع نفسه: لا تغيير ولا استثناء (هو إصبع لا مؤشّر).
        assertEquals(bookmarks, FileBookmarks.move(bookmarks, 5, 0))
        assertEquals(bookmarks, FileBookmarks.move(bookmarks, 1, 1))
        // السحب إلى ما بعد النهاية يضع العنصر في الآخر — لا يختفي ولا يبقى مكانه.
        assertEquals(listOf("/b", "/c", "/a"), FileBookmarks.move(bookmarks, 0, 99).map { it.path })
    }

    @Test
    fun containsIgnoresRedundantSlashes() {
        val bookmarks = FileBookmarks.add(emptyList(), "/sdcard/Download")
        assertTrue(FileBookmarks.contains(bookmarks, "/sdcard//Download/"))
        assertFalse(FileBookmarks.contains(bookmarks, "/sdcard"))
    }

    // ────────────────────────────────────────────────────────────────────────
    // السجل
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun theHistoryPutsTheLatestVisitFirst() {
        val history = FileHistory.push(FileHistory.push(emptyList(), "/a", 10), "/b", 20)
        assertEquals(listOf("/b", "/a"), history.map { it.path })
    }

    /** زيارة المجلد نفسه مرّتين لا تُكرَّر: «تحديث» عشر مرات ليس عشرة أسطر. */
    @Test
    fun revisitingTheSameFolderMovesItUpInsteadOfDuplicating() {
        val history = FileHistory.push(
            listOf(HistoryEntry("/a", 10), HistoryEntry("/b", 20)),
            "/b",
            30,
        )
        assertEquals(listOf("/b", "/a"), history.map { it.path })
        assertEquals(30L, history.first().atMs)
    }

    @Test
    fun theHistoryIsCappedAtTheDeclaredLimit() {
        val history = (1..(FileHistory.LIMIT + 25)).fold(emptyList<HistoryEntry>()) { acc, index ->
            FileHistory.push(acc, "/folder-$index", index.toLong())
        }
        assertEquals(FileHistory.LIMIT, history.size)
        assertEquals("/folder-${FileHistory.LIMIT + 25}", history.first().path)
    }

    @Test
    fun clearingTheHistoryLeavesNothing() {
        assertTrue(FileHistory.clear().isEmpty())
    }

    /** التجميع باليوم يُقاس بمفتاح يُمرَّر من الخارج: لا ساعة ولا منطقة زمنية في النموذج. */
    @Test
    fun groupingUsesTheKeyProvidedByTheCaller() {
        val history = listOf(
            HistoryEntry("/a", 1_000),
            HistoryEntry("/b", 2_000),
            HistoryEntry("/c", 90_000),
        )
        val groups = FileHistory.grouped(history) { millis -> if (millis < 50_000) "اليوم" else "أمس" }
        assertEquals(listOf("أمس", "اليوم"), groups.map { it.first })
        assertEquals(listOf("/c"), groups.first().second.map { it.path })
        assertEquals(listOf("/b", "/a"), groups.last().second.map { it.path })
    }
}
