package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نموذج اللوحين.
 *
 * وأهمّ ما هنا ليس حفظ الحالة — بل **الطلب الذي يبنيه اللوحان**: مع لوح واحد لا توجد
 * إلا وجهة واحدة، ومع لوحين تصير الوجهة مجلدًا اختاره المستخدم في الجهة الأخرى. وهنا
 * بالضبط يظهر «نسخ مجلد داخل نفسه»: لوحان على نفس الشجرة. فيُختبر أن الطلب المبنيّ
 * من اللوحين **يمرّ على نفس الحرس**، لا على حرس ثانٍ مكتوب في الواجهة.
 */
class FilePaneModelTest {

    private fun entry(name: String, directory: Boolean = false, size: Long? = null) = FileEntry(
        name = name,
        path = "/root/$name",
        kind = if (directory) FileKind.Directory else FileKind.RegularFile,
        sizeBytes = size,
    )

    private fun pane(
        path: String = "/root",
        entries: List<FileEntry> = emptyList(),
        selected: Set<String> = emptySet(),
        query: String = "",
        sort: FileSort = FileSort(),
    ) = FilePaneState(
        path = path,
        listing = DirectoryListing.Entries(path, entries),
        loading = false,
        query = query,
        sort = sort,
        selection = FileSelection(selected),
        selecting = selected.isNotEmpty(),
    )

    // ── الجانب ────────────────────────────────────────────────────────────────

    @Test
    fun `each side points at the other and never at itself`() {
        assertEquals(PaneSide.Right, PaneSide.Left.other)
        assertEquals(PaneSide.Left, PaneSide.Right.other)
        assertFalse(PaneSide.Left.other == PaneSide.Left)
    }

    // ── حالة اللوح ────────────────────────────────────────────────────────────

    @Test
    fun `visible entries are filtered then sorted`() {
        val state = pane(entries = listOf(entry("b2"), entry("b10"), entry("a")))
        val filtered = state.copy(query = "b").visible()
        assertEquals(listOf("b2", "b10"), filtered.map { it.name })
    }

    @Test
    fun `moving to a folder clears the selection and the search`() {
        val state = pane(selected = setOf("/root/x"), query = "q")
        val moved = state.at("/data/")
        assertEquals("/data", moved.path)
        assertTrue(moved.selection.isEmpty)
        assertEquals("", moved.query)
        assertFalse(moved.selecting)
    }

    @Test
    fun `a long press starts selecting and selects that entry`() {
        val state = pane(entries = listOf(entry("a"))).toggleSelection("/root/a")
        assertTrue(state.selecting)
        assertTrue(state.isSelected("/root/a"))
        assertFalse(state.isSelected("/root/b"))
    }

    @Test
    fun `clearing the selection also leaves selecting mode`() {
        val state = pane(selected = setOf("/root/a")).clearSelection()
        assertFalse(state.selecting)
        assertTrue(state.selection.isEmpty)
    }

    @Test
    fun `a fresh listing replaces the old one and ends loading`() {
        val loading = FilePaneState(path = "/root", loading = true)
        val loaded = loading.withListing(DirectoryListing.Entries("/root", listOf(entry("a"))))
        assertFalse(loaded.loading)
        assertEquals(listOf("a"), loaded.entries.map { it.name })
    }

    // ── النقل بين اللوحين ────────────────────────────────────────────────────

    @Test
    fun `a transfer request takes the sources from one pane and the other pane as destination`() {
        val from = pane(path = "/sdcard/DCIM", selected = setOf("/sdcard/DCIM/b.jpg", "/sdcard/DCIM/a.jpg"))
        val to = pane(path = "/sdcard/Backup")
        val request = DualPane.transferRequest(FileOperation.Copy, from, to)
        assertEquals("/sdcard/Backup", request.destination)
        // مرتّبة: الطلب يجب أن يكون قابلًا للمقارنة والمقايسة، لا أن يتغيّر ترتيبه.
        assertEquals(listOf("/sdcard/DCIM/a.jpg", "/sdcard/DCIM/b.jpg"), request.sources)
        assertEquals(FileOperation.Copy, request.operation)
    }

    @Test
    fun `transfer needs a selection and two different folders`() {
        val filled = pane(path = "/a", selected = setOf("/a/x"))
        val empty = pane(path = "/b")
        assertTrue(DualPane.canTransfer(filled, empty))
        assertFalse(DualPane.canTransfer(empty, filled))
        // اللوحان على المجلد نفسه: نقل «إلى هنا» ليس نقلًا.
        assertFalse(DualPane.canTransfer(filled, pane(path = "/a")))
        assertFalse(DualPane.canTransfer(filled, pane(path = "/a/")))
    }

    /**
     * الاختبار الحاسم: الطلب المبنيّ من اللوحين يُمرَّر إلى الحرس نفسه، فيرفض نسخ مجلد
     * داخل مجلد آخر يقع في شجرته. ولو بُنيت قاعدة ثانية في الواجهة لاختلفت يومًا ما.
     */
    @Test
    fun `a cross-pane copy into the source's own subtree is refused by the shared guard`() {
        val from = pane(path = "/sdcard/DCIM", selected = setOf("/sdcard/DCIM"))
        val to = pane(path = "/sdcard/DCIM/inner")
        val request = DualPane.transferRequest(FileOperation.Copy, from, to)
        val verdict = FileOpGuard.check(request, directories = setOf("/sdcard/DCIM"))
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.TargetInsideSource), verdict)
    }

    @Test
    fun `a cross-pane move into the pane's own folder is refused as a self target`() {
        val from = pane(path = "/data/local", selected = setOf("/data/local/tmp"))
        val to = pane(path = "/data/local/tmp")
        val request = DualPane.transferRequest(FileOperation.Move, from, to)
        val verdict = FileOpGuard.check(request, directories = setOf("/data/local/tmp"))
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.SelfTarget), verdict)
    }

    @Test
    fun `a cross-pane copy between sibling folders is allowed`() {
        val from = pane(path = "/sdcard/DCIM", selected = setOf("/sdcard/DCIM/a.jpg"))
        val to = pane(path = "/sdcard/Download")
        val request = DualPane.transferRequest(FileOperation.Copy, from, to)
        assertEquals(FileOpVerdict.Allowed, FileOpGuard.check(request, directories = emptySet()))
    }

    // ── المزامنة والتبديل ────────────────────────────────────────────────────

    @Test
    fun `sync moves only the other pane onto the active folder`() {
        val from = pane(path = "/data/adb/modules")
        val to = pane(path = "/sdcard", query = "keep")
        val synced = DualPane.syncOther(from, to)
        assertEquals("/data/adb/modules", synced.path)
        // ولا يمسّ بحث اللوح الآخر: المزامنة مسار لا حالة كاملة.
        assertEquals("keep", synced.query)
    }

    @Test
    fun `swap exchanges the paths and drops state tied to the old folder`() {
        val left = pane(path = "/left", selected = setOf("/left/x"), query = "q")
        val right = pane(path = "/right")
        val (newLeft, newRight) = DualPane.swap(left, right)
        assertEquals("/right", newLeft.path)
        assertEquals("/left", newRight.path)
        assertTrue(newLeft.selection.isEmpty)
        assertEquals("", newLeft.query)
    }

    @Test
    fun `activate reports the side it was given`() {
        assertEquals(PaneSide.Left, DualPane.activate(PaneSide.Left))
        assertEquals(PaneSide.Right, DualPane.activate(PaneSide.Right))
    }

    @Test
    fun `a pane with no listing shows no entries and does not invent them`() {
        val blank = FilePaneState(path = "/root", listing = null)
        assertTrue(blank.entries.isEmpty())
        assertTrue(blank.visible().isEmpty())
        assertNull((blank.listing as? DirectoryListing.Entries))
    }
}
