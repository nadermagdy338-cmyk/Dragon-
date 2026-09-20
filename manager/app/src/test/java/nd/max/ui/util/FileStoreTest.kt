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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * مخزن المفضّلة والسجل — الوعد الذي يحرسه هذا الاختبار: **ما كُتب يُقرأ، وما لم يُفهم
 * لا يُسقط الشاشة**. والقياس على ملف حقيقي في مجلد مؤقّت، لا على ترميز نظري.
 */
class FileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    // ── المفضّلة ──────────────────────────────────────────────────────────────

    @Test
    fun bookmarksRoundTripThroughARealFile() {
        val store = FileStore(File(folder.root, "bookmarks.txt"))
        val saved = listOf(FileBookmark("/data/local/tmp", "tmp"), FileBookmark("/sdcard/Download"))
        store.saveBookmarks(saved)

        val loaded = FileStore(File(folder.root, "bookmarks.txt")).loadBookmarks()
        assertEquals(listOf("/data/local/tmp", "/sdcard/Download"), loaded.map { it.path })
        assertEquals("tmp", loaded[0].label)
        assertEquals("", loaded[1].label)
    }

    /** نسخة أقدم كتبت مسارًا وحده (بلا فاصل): يُقرأ مسارًا، ولا يسقط ما بعده. */
    @Test
    fun aPathOnlyLineFromAnOlderVersionIsStillRead() {
        val file = File(folder.root, "bookmarks.txt")
        file.writeText("/sdcard/Download\n/data/local/tmp\n", StandardCharsets.UTF_8)
        val loaded = FileStore(file).loadBookmarks()
        assertEquals(listOf("/sdcard/Download", "/data/local/tmp"), loaded.map { it.path })
        assertEquals("", loaded[0].label)
    }

    @Test
    fun nonsenseLinesAreDroppedAndTheRestSurvives() {
        val file = File(folder.root, "bookmarks.txt")
        // سطر فارغ · فاصل بلا مسار · مسافة وحدها · وسطر صالح في الذيل.
        file.writeText("\n\u001Flabel only\n   \n/sdcard\n", StandardCharsets.UTF_8)
        assertEquals(listOf("/sdcard"), FileStore(file).loadBookmarks().map { it.path })
    }

    @Test
    fun duplicatesCollapseByNormalizedPath() {
        val file = File(folder.root, "bookmarks.txt")
        file.writeText("/sdcard/\n/sdcard\n", StandardCharsets.UTF_8)
        assertEquals(1, FileStore(file).loadBookmarks().size)
    }

    @Test
    fun bookmarkCountIsCapped() {
        val many = (1..FileBookmarkCodec.MAX + 20).map { FileBookmark("/folder$it") }
        val encoded = FileBookmarkCodec.encode(many)
        assertEquals(FileBookmarkCodec.MAX, encoded.lineSequence().count())
        assertEquals(FileBookmarkCodec.MAX, FileBookmarkCodec.decode(encoded).size)
    }

    /** فصل محجوز داخل قيمة (مسار مُلصق غريب) لا يخلق عنصرًا ثانيًا ولا يلغي السطر. */
    @Test
    fun aSeparatorInsideAValueCannotForkTheLine() {
        val tricky = FileBookmark("/sdcard/\u001Ffake", "lab\u001Fel")
        val decoded = FileBookmarkCodec.decode(FileBookmarkCodec.encode(listOf(tricky)))
        assertEquals(1, decoded.size)
        assertEquals("/sdcard/fake", decoded[0].path)
        assertEquals("label", decoded[0].label)
    }

    // ── السجل ────────────────────────────────────────────────────────────────

    @Test
    fun historyRoundTripsAndKeepsOrder() {
        val store = FileStore(File(folder.root, "history.txt"))
        val saved = listOf(HistoryEntry("/b", 1_700_000_000_000L), HistoryEntry("/a", 1_600_000_000_000L))
        store.saveHistory(saved)
        assertEquals(saved, FileStore(File(folder.root, "history.txt")).loadHistory())
    }

    @Test
    fun historyLinesWithoutAValidTimestampAreDropped() {
        val file = File(folder.root, "history.txt")
        file.writeText(
            "/no-timestamp\n" +
                "/zero\u001F0\n" +
                "/negative\u001F-5\n" +
                "/garbage\u001Fsoon\n" +
                "/good\u001F1700000000000\n",
            StandardCharsets.UTF_8,
        )
        assertEquals(listOf("/good"), FileStore(file).loadHistory().map { it.path })
    }

    @Test
    fun historyIsCappedAtTheModelLimit() {
        val many = (1..FileHistory.LIMIT + 50).map { HistoryEntry("/f$it", 1_700_000_000_000L + it) }
        assertEquals(FileHistory.LIMIT, FileHistoryCodec.decode(FileHistoryCodec.encode(many)).size)
    }

    // ── المخزن نفسه ──────────────────────────────────────────────────────────

    @Test
    fun aMissingFileYieldsEmptyListsNotACrash() {
        val store = FileStore(File(folder.root, "never-written.txt"))
        assertTrue(store.loadBookmarks().isEmpty())
        assertTrue(store.loadHistory().isEmpty())
    }

    @Test
    fun aCorruptFileYieldsWhatWasUnderstoodNotACrash() {
        val file = File(folder.root, "corrupt.txt")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x7F))
        val store = FileStore(file)
        // بايتات لا نصّ فيها: النتيجة قائمة فارغة، لا استثناء يصل إلى المستخدم.
        assertTrue(store.loadBookmarks().isEmpty())
        assertTrue(store.loadHistory().isEmpty())
    }

    @Test
    fun savingCreatesTheParentFolderWhenMissing() {
        val nested = File(File(folder.root, "sub/dir"), "bookmarks.txt")
        FileStore(nested).saveBookmarks(listOf(FileBookmark("/sdcard")))
        assertTrue(nested.isFile)
        assertEquals(listOf("/sdcard"), FileStore(nested).loadBookmarks().map { it.path })
    }

    @Test
    fun anEmptyListClearsTheFileContentInsteadOfKeepingStaleEntries() {
        val file = File(folder.root, "bookmarks.txt")
        val store = FileStore(file)
        store.saveBookmarks(listOf(FileBookmark("/sdcard")))
        store.saveBookmarks(emptyList())
        assertTrue(FileStore(file).loadBookmarks().isEmpty())
    }
}
