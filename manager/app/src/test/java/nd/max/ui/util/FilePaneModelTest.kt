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
 * نموذج اللوحين وحرس العمليات — يُقاسان في JVM عادي بلا جهاز ولا Android.
 *
 * والسببان مختلفان: قرار **ترتيب اللوحين** يحدّد أيّ شاشتين تُركَّبان (وتخطئته تنتج لوحين
 * متراكبين في صندوق لا يتّسعهما)، و[FileOpGuard] هو آخر ما يقف بين نقرة وحذف شجرة. ولا
 * يُقاس أيٌّ منهما بقراءة الشيفرة — يُقاس بطلب يُرفض أو يُسمح.
 */
class FilePaneModelTest {

    private fun dir(parent: String, name: String) = FileEntry(
        name = name,
        path = FileBrowser.childPath(parent, name),
        kind = FileKind.Directory,
    )

    private fun file(parent: String, name: String) = FileEntry(
        name = name,
        path = FileBrowser.childPath(parent, name),
        kind = FileKind.RegularFile,
    )

    private fun pane(path: String, vararg entries: FileEntry) = FilePaneState(
        path = path,
        listing = DirectoryListing.Entries(path = path, entries = entries.toList()),
        loading = false,
    )

    // ────────────────────────────────────────────────────────────────────────
    // ترتيب اللوحين
    // ────────────────────────────────────────────────────────────────────────

    /** الآلي يتبع العرض المقيس: الضيّق يرصّ، والواسع يجانب. */
    @Test
    fun automaticArrangementFollowsTheMeasuredWidth() {
        assertTrue(PaneLayoutRule.sideBySide(PaneLayout.Auto, availableWidth = 900f, threshold = 600f))
        assertFalse(PaneLayoutRule.sideBySide(PaneLayout.Auto, availableWidth = 359f, threshold = 600f))
        // حدّ صريح: العرض المساوي للحدّ يُعدّ كافيًا، وإلا صار سلوك الحدّ متروكًا للحظّ.
        assertTrue(PaneLayoutRule.sideBySide(PaneLayout.Auto, availableWidth = 600f, threshold = 600f))
    }

    /**
     * الوضع الصريح يتقدّم على القياس في الاتجاهين — وهذا هو الطلب نفسه: من اختار
     * «جنبًا إلى جنب» على هاتف ضيّق يريده كذلك، ومن اختار «فوق وتحت» لا يُجبَر على عمودين.
     */
    @Test
    fun anExplicitArrangementWinsOverTheWidth() {
        assertTrue(PaneLayoutRule.sideBySide(PaneLayout.SideBySide, 359f, 600f))
        assertFalse(PaneLayoutRule.sideBySide(PaneLayout.Stacked, 1200f, 600f))
    }

    // ────────────────────────────────────────────────────────────────────────
    // التنقّل المرتبط
    // ────────────────────────────────────────────────────────────────────────

    /**
     * الربط يتبع **الاسم** لا المسار: اللوح الآخر يُدخل مجلدًا بالاسم نفسه من مساره هو.
     * وهذا ما يجعله مفيدًا: المزامنة المطلقة تضع اللوحين على مجلد واحد فتصير الوجهة =
     * المصدر، أي تمنع النقل الذي وُجد اللوحان من أجله.
     */
    @Test
    fun linkedNavigationFollowsTheFolderNameOnTheOtherSide() {
        val right = pane("/storage/emulated/0", dir("/storage/emulated/0", "Download"))
        val target = DualPane.mirrorFolder(right, dir("/sdcard", "Download"))
        assertEquals("/storage/emulated/0/Download", target)
    }

    /** الاسم غير الموجود هناك لا يُخترع له مسار: لا `childPath` بالنصّ على مجلد لم نقسه. */
    @Test
    fun linkedNavigationLeavesTheOtherPaneAloneWhenTheFolderIsMissingThere() {
        val right = pane("/storage/emulated/0", dir("/storage/emulated/0", "Pictures"))
        assertNull(DualPane.mirrorFolder(right, dir("/sdcard", "Download")))
    }

    /** قفزة فتات الخبز تصعد العدد نفسه من الخطوات في اللوح المقابل. */
    @Test
    fun linkedBreadcrumbNavigationMirrorsAncestorDepth() {
        val right = pane("/storage/emulated/0/Android/data/cache")
        assertEquals(
            "/storage/emulated/0/Android",
            DualPane.mirrorAncestor(right, "/sdcard/a/b", "/sdcard"),
        )
        assertNull(DualPane.mirrorAncestor(right, "/sdcard/a", "/other"))
    }

    /** ملف بالاسم نفسه ليس مجلدًا: الربط لا يوقف لوحًا على ملف. */
    @Test
    fun linkedNavigationNeverTreatsAFileAsAFolder() {
        val right = pane("/storage/emulated/0", file("/storage/emulated/0", "Download"))
        assertNull(DualPane.mirrorFolder(right, dir("/sdcard", "Download")))
    }

    /** لوح لم تُقرأ مديرته لا يُنقل على اسم منه — لا مدخلات مقيسة يعني لا مقابل. */
    @Test
    fun anUnreadablePaneIsNeverMoved() {
        val unreadable = FilePaneState(
            path = "/data",
            listing = DirectoryListing.Unreadable("/data", ListingFailure.PermissionDenied),
            loading = false,
        )
        assertNull(DualPane.mirrorFolder(unreadable, dir("/", "data")))
    }

    /** الصعود المرتبط خطوة واحدة، والجذر لا أبَ له فلا يُدفَع اللوح إلى مسار غير موجود. */
    @Test
    fun goingUpOnlyMirrorsWhileThereIsAParent() {
        assertEquals("/sdcard", DualPane.mirrorParent(pane("/sdcard/Download")))
        assertNull(DualPane.mirrorParent(pane("/")))
    }

    /** الوجهة في النقل هي **مجلد اللوح الآخر**: لا يكتب المستخدم مسارًا ولا يُخمَّن له مسار. */
    @Test
    fun transferRequestTargetsTheOtherPaneFolder() {
        val from = pane("/sdcard", file("/sdcard", "a.txt"), file("/sdcard", "b.txt")).let {
            it.copy(selection = FileSelection(setOf("/sdcard/a.txt", "/sdcard/b.txt")))
        }
        val to = pane("/sdcard/Backup")
        val request = DualPane.transferRequest(FileOperation.Copy, from, to)
        assertEquals(listOf("/sdcard/a.txt", "/sdcard/b.txt"), request.sources)
        assertEquals("/sdcard/Backup", request.destination)
        assertTrue(DualPane.canTransfer(from, to))
    }

    /**
     * واللوحان على المجلد نفسه بعد «مزامنة المسار»: لا نقلَ ممكنًا — وهذا هو الفرق العملي
     * بين المزامنة المطلقة والتنقّل المرتبط، فيُثبَّت هنا حتى لا يُخلط بينهما.
     */
    @Test
    fun absoluteSynchronisationLeavesNothingToTransfer() {
        val left = pane("/sdcard").let { it.copy(selection = FileSelection(setOf("/sdcard/a.txt"))) }
        val right = DualPane.syncOther(left, pane("/sdcard/Backup"))
        assertEquals("/sdcard", right.path)
        assertFalse(DualPane.canTransfer(left, right))
    }

    /** التبديل يتبادل المسارين و**يُصفّر** البحث والتحديد معهما. */
    @Test
    fun swappingKeepsEachPaneCleanOfTheOtherState() {
        val left = pane("/a").let { it.copy(query = "log", selection = FileSelection(setOf("/a/x"))) }
        val right = pane("/b")
        val (newLeft, newRight) = DualPane.swap(left, right)
        assertEquals("/b", newLeft.path)
        assertEquals("/a", newRight.path)
        assertEquals("", newLeft.query)
        assertTrue(newLeft.selection.isEmpty)
        assertFalse(newLeft.selecting)
    }

    // ────────────────────────────────────────────────────────────────────────
    // حرس العمليات
    // ────────────────────────────────────────────────────────────────────────

    /** نسخ مجلد داخل مجلد فرعيّ منه: شجرة لا تنتهي وقرص يمتلئ. */
    @Test
    fun copyingAFolderIntoItselfIsRefused() {
        val verdict = FileOpGuard.check(
            FileOpRequest(
                operation = FileOperation.Copy,
                sources = listOf("/sdcard/Pictures"),
                destination = "/sdcard/Pictures/copy",
            ),
            directories = setOf("/sdcard/Pictures"),
        )
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.TargetInsideSource), verdict)
    }

    /**
     * ونفس الوجهة من اللوحين: النقل إلى المجلد الذي جاء منه الملف ليس نقلًا.
     * وهذا هو الشكل الذي يظهر بمجرّد أن يقف اللوحان على مجلد واحد.
     */
    @Test
    fun movingSomethingOntoItsOwnSourceIsRefused() {
        val verdict = FileOpGuard.check(
            FileOpRequest(
                operation = FileOperation.Move,
                sources = listOf("/sdcard/Backup"),
                destination = "/sdcard/Backup",
            ),
        )
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.SelfTarget), verdict)
    }

    /** جذر نظام الملفات لا يُحذف ولا يُنقل ولا يكون وجهة. */
    @Test
    fun theFilesystemRootIsAlwaysProtected() {
        assertEquals(
            FileOpVerdict.Refused(FileOpRefusal.ProtectedPath),
            FileOpGuard.check(FileOpRequest(FileOperation.Delete, sources = listOf("/"))),
        )
        assertEquals(
            FileOpVerdict.Refused(FileOpRefusal.ProtectedPath),
            FileOpGuard.check(
                FileOpRequest(FileOperation.Move, sources = listOf("/sdcard/x"), destination = "/"),
            ),
        )
    }

    /** الاسم لا يكون مسارًا: فاصل في الاسم يحوّل `mv x a/b` إلى كتابة في مجلد آخر. */
    @Test
    fun aNameCarryingASeparatorIsRefused() {
        assertFalse(FileOpGuard.isValidName("a/b"))
        assertFalse(FileOpGuard.isValidName(".."))
        assertFalse(FileOpGuard.isValidName("   "))
        assertTrue(FileOpGuard.isValidName("backup 2026"))
        assertEquals(
            FileOpVerdict.Refused(FileOpRefusal.InvalidName),
            FileOpGuard.check(
                FileOpRequest(FileOperation.Rename, sources = listOf("/sdcard/a"), newName = "b/c"),
            ),
        )
    }

    /** اسم مشغول يُرفض بدل أن يُستبدل ملف، والبديل الفريد يُبنى بلا مسّ ما هو موجود. */
    @Test
    fun aTakenNameIsRefusedAndTheUniqueAlternativeIsOffered() {
        assertEquals(
            FileOpVerdict.Refused(FileOpRefusal.NameTaken),
            FileOpGuard.check(
                FileOpRequest(FileOperation.CreateDirectory, destination = "/sdcard", newName = "Backup"),
                existingNames = setOf("Backup"),
            ),
        )
        assertEquals("Backup (2)", FileOpGuard.uniqueName("Backup", setOf("Backup", "Backup (1)")))
    }

    /** والنسخ العادي بين مجلدين مختلفين يُسمح — الحرس ليس مانعًا عامًّا. */
    @Test
    fun aPlainCopyBetweenTwoDifferentFoldersIsAllowed() {
        assertEquals(
            FileOpVerdict.Allowed,
            FileOpGuard.check(
                FileOpRequest(
                    operation = FileOperation.Copy,
                    sources = listOf("/sdcard/Download/a.zip"),
                    destination = "/sdcard/Backup",
                ),
            ),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // OCR-10: المرشّح داخل اللوح
    // ────────────────────────────────────────────────────────────────────────

    private fun sized(parent: String, name: String, size: Long?) = FileEntry(
        name = name,
        path = FileBrowser.childPath(parent, name),
        kind = FileKind.RegularFile,
        sizeBytes = size,
        modifiedEpochSec = 1_700_000_000L,
    )

    /** ما يراه المستخدم هو ناتج البحث **ثم** المرشّح — لا أحدهما. */
    @Test
    fun theVisibleListIsTheSearchThenTheFilter() {
        val state = FilePaneState(
            path = "/sdcard/DCIM",
            listing = DirectoryListing.Entries(
                path = "/sdcard/DCIM",
                entries = listOf(
                    sized("/sdcard/DCIM", "holiday.jpg", 20L * 1024 * 1024),
                    sized("/sdcard/DCIM", "tiny.jpg", 1024L),
                    sized("/sdcard/DCIM", "clip.mp4", 30L * 1024 * 1024),
                ),
            ),
            loading = false,
        )

        assertEquals(3, state.visible().size)

        val images = state.copy(search = FileSearchFilters.Filter(kind = FileSearchFilters.Kind.IMAGE))
        assertEquals(listOf("holiday.jpg", "tiny.jpg"), images.visible().map { it.name })

        val bigImages = images.copy(search = images.search.copy(size = FileSearchFilters.Size.OVER_10MB))
        assertEquals(listOf("holiday.jpg"), bigImages.visible().map { it.name })

        // والبحث بالاسم يعمل مع المرشّح لا بدلًا منه.
        val named = images.copy(query = "tiny")
        assertEquals(listOf("tiny.jpg"), named.visible().map { it.name })
    }

    /** وغياب ملف بسبب مرشّح **يُعلَن**، فلا يُقرأ كأنه حُذف. */
    @Test
    fun aFilteredListSaysThatItIsFiltered() {
        val plain = FilePaneState(path = "/sdcard", loading = false)
        assertFalse(plain.filtering)
        assertTrue(plain.copy(query = "a").filtering)
        assertTrue(
            plain.copy(search = FileSearchFilters.Filter(age = FileSearchFilters.Age.TODAY)).filtering
        )
    }

    /** وعكس الاختيار على النتائج: ما لم يُحدَّد يُحدَّد، وما حُدِّد يُرفع — لا عملية تُلغي الأخرى. */
    @Test
    fun selectAllResultsAndInvertWorkOnWhatIsVisible() {
        val entries = listOf(
            sized("/sdcard", "a.jpg", null),
            sized("/sdcard", "b.jpg", null),
            sized("/sdcard", "c.jpg", null),
        )
        // «حدّد الكل» على **النتائج** لا على المجلد: من مرّر قائمة مرشّحة يحدّد ما يراها.
        val visible = entries.take(2)
        assertEquals(setOf("/sdcard/a.jpg", "/sdcard/b.jpg"), FileSelection().selectAll(visible).paths)

        // والعكس يعني «ما لم يُحدَّد يُحدَّد»: من لا شيء يُنتج الكل، ومن الكل يُنتج لا شيء.
        assertEquals(entries.map { it.path }.toSet(), FileSelection().invert(entries).paths)
        assertEquals(emptySet<String>(), FileSelection().selectAll(entries).invert(entries).paths)
        assertEquals(
            setOf("/sdcard/b.jpg", "/sdcard/c.jpg"),
            FileSelection(setOf("/sdcard/a.jpg")).invert(entries).paths,
        )
    }
}
