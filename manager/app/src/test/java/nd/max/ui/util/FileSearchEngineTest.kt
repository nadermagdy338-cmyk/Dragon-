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
 * محرّك البحث العميق — يُقاس على **شجرة مصنوعة** يُحقن قارئها، فلا جهاز ولا `/data` حقيقية.
 *
 * وما يُقاس هنا هو الصدق لا السرعة: أن حدّ العمق وحدّ النتائج والمهلة والإلغاء **تُعلَن**
 * في النتيجة، فلا تُعرض نتيجة مقطوعة كأنها مسح كامل.
 */
class FileSearchEngineTest {

    private fun file(parent: String, name: String) = FileEntry(
        name = name,
        path = FileBrowser.childPath(parent, name),
        kind = FileKind.RegularFile,
        sizeBytes = 10,
    )

    private fun folder(parent: String, name: String) = FileEntry(
        name = name,
        path = FileBrowser.childPath(parent, name),
        kind = FileKind.Directory,
    )

    /** شجرة: root › a › c › d، وroot › b غير مقروء، وroot/notes.txt ملف. */
    private val tree: Map<String, DirectoryListing> = mapOf(
        "/root" to DirectoryListing.Entries(
            "/root",
            listOf(folder("/root", "a"), folder("/root", "b"), file("/root", "notes.txt")),
        ),
        "/root/a" to DirectoryListing.Entries(
            "/root/a",
            listOf(folder("/root/a", "c"), file("/root/a", "target.txt")),
        ),
        "/root/a/c" to DirectoryListing.Entries(
            "/root/a/c",
            listOf(folder("/root/a/c", "d"), file("/root/a/c", "target.txt")),
        ),
        "/root/a/c/d" to DirectoryListing.Entries("/root/a/c/d", listOf(file("/root/a/c/d", "target.txt"))),
        "/root/b" to DirectoryListing.Unreadable("/root/b", ListingFailure.PermissionDenied),
    )

    private val lister: (String) -> DirectoryListing = { path ->
        tree[path] ?: DirectoryListing.Unreadable(path, ListingFailure.NotFound)
    }

    @Test
    fun theSearchFindsMatchingNamesThroughTheWholeTree() {
        val request = FileSearchPlan.of("/root", "target")!!
        val outcome = FileSearchEngine.search(request, lister)

        assertEquals(3, outcome.hits.size)
        assertEquals(4, outcome.scannedFolders)
        // الترتيب بالأقرب أولًا: يُقرأ منه قبل الأعمق.
        assertEquals("/root/a/target.txt", outcome.hits.first().path)
        assertFalse(outcome.complete) // مجلد واحد لم يُقرأ ⇒ النتيجة ليست كاملة
        assertEquals(1, outcome.unreadableFolders)
    }

    @Test
    fun theDepthLimitIsDeclaredAndDeeperFoldersAreNotScanned() {
        val request = FileSearchPlan.of("/root", "target", SearchLimits(maxDepth = 2))!!
        val outcome = FileSearchEngine.search(request, lister)

        assertTrue(outcome.depthLimitReached)
        assertFalse(outcome.complete)
        // العمق ٣ (`d`) لم يُقرأ، فنتيجته غير موجودة — وهذا هو معنى الحدّ.
        assertTrue(outcome.hits.any { it.path == "/root/a/c/target.txt" })
        assertFalse(outcome.hits.any { it.path == "/root/a/c/d/target.txt" })
    }

    @Test
    fun theResultLimitIsDeclaredAndStopsTheWalkAtExactlyTheLimit() {
        // الحدّ يُبنى مباشرةً لا عبر `FileSearchPlan.of` الذي يضيّق الحدود إلى مداها المعقول.
        val request = DeepSearchRequest("/root", "target", SearchLimits(maxResults = 2))
        val outcome = FileSearchEngine.search(request, lister)

        assertTrue(outcome.hitLimitReached)
        assertEquals(2, outcome.hits.size)
        assertFalse(outcome.complete)
    }

    @Test
    fun aWalkThatMeetsNoLimitIsDeclaredComplete() {
        val noUnreadable = mapOf(
            "/root" to DirectoryListing.Entries("/root", listOf(file("/root", "target.txt"))),
        )
        val outcome = FileSearchEngine.search(
            DeepSearchRequest("/root", "target", SearchLimits()),
            { path -> noUnreadable[path] ?: DirectoryListing.Unreadable(path, ListingFailure.NotFound) },
        )

        assertTrue(outcome.complete)
        assertEquals(1, outcome.hits.size)
    }

    /** مهلة تُقاس بساعة محقونة: لا انتظار حقيقي في الاختبار. */
    @Test
    fun theTimeoutIsDeclaredWhenTheClockRunsOut() {
        val request = FileSearchPlan.of("/root", "target", SearchLimits(timeoutMs = 1_000L))!!
        var now = 0L
        val outcome = FileSearchEngine.search(request, lister, clock = { now.also { now += 400L } })

        assertTrue(outcome.timedOut)
        assertFalse(outcome.complete)
    }

    @Test
    fun cancellationIsHonouredImmediatelyAndDeclared() {
        val request = FileSearchPlan.of("/root", "target")!!
        val outcome = FileSearchEngine.search(request, lister, isCancelled = { true })

        assertTrue(outcome.cancelled)
        assertFalse(outcome.complete)
        assertTrue(outcome.hits.isEmpty())
    }

    @Test
    fun aFolderThatCannotBeReadIsCountedAndDoesNotStopTheSearch() {
        val request = FileSearchPlan.of("/root", "notes")!!
        val outcome = FileSearchEngine.search(request, lister)

        assertEquals(1, outcome.unreadableFolders)
        assertEquals("/root/notes.txt", outcome.hits.single().path)
    }

    @Test
    fun nothingMatchingProducesAnEmptyButHonestOutcome() {
        val request = FileSearchPlan.of("/root", "nothing-like-this")!!
        val outcome = FileSearchEngine.search(request, lister)

        assertTrue(outcome.hits.isEmpty())
        assertEquals(4, outcome.scannedFolders)
        assertEquals(1, outcome.unreadableFolders)
    }
}
