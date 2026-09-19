package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد مدير الملفات النقي.
 *
 * وأهمّ ما هنا ليس التنقّل بل **الحرس**: خطأ في `FileOpGuard` لا يظهر في تصريف ولا في
 * مراجعة، ويظهر بعد وقوعه على قرص فيه بيانات لا تُستعاد. فكل سبب رفض له اختبار، وكل
 * حالة مسموح لها اختبار يمنع الحرس من أن يصير مانعًا لكل شيء.
 */
class FileSystemModelTest {

    // ── المسارات ──────────────────────────────────────────────────────────────

    @Test
    fun `normalize collapses redundant separators and trailing slash`() {
        assertEquals("/data/adb", FileBrowser.normalize("/data/adb/"))
        assertEquals("/data/adb", FileBrowser.normalize("//data//adb"))
        assertEquals("/", FileBrowser.normalize("/"))
        assertEquals("/", FileBrowser.normalize(""))
    }

    @Test
    fun `childPath never doubles the slash at the root`() {
        assertEquals("/sdcard", FileBrowser.childPath("/", "sdcard"))
        assertEquals("/data/adb", FileBrowser.childPath("/data", "adb"))
        assertEquals("/data/adb", FileBrowser.childPath("/data/", "adb"))
    }

    @Test
    fun `parentOf stops at the root`() {
        assertEquals("/data", FileBrowser.parentOf("/data/adb"))
        assertEquals("/", FileBrowser.parentOf("/sdcard"))
        assertNull(FileBrowser.parentOf("/"))
    }

    @Test
    fun `nameOf reads the last segment and the root reads itself`() {
        assertEquals("adb", FileBrowser.nameOf("/data/adb"))
        assertEquals("adb", FileBrowser.nameOf("/data/adb/"))
        assertEquals("/", FileBrowser.nameOf("/"))
    }

    @Test
    fun `breadcrumbs rebuild every prefix of the path`() {
        val crumbs = FileBrowser.breadcrumbs("/data/adb/modules")
        assertEquals(listOf("/", "data", "adb", "modules"), crumbs.map { it.label })
        assertEquals(listOf("/", "/data", "/data/adb", "/data/adb/modules"), crumbs.map { it.path })
        assertEquals(listOf("/"), FileBrowser.breadcrumbs("/").map { it.path })
    }

    /**
     * الحدود النصّية ليست حدود المسار: `/data2` **ليست** داخل `/data`. اختبار البادئة
     * النصّية وحده كان سيجعل حرس «لا تنسخ داخل نفسك» يرفض مسارات شقيقة بريئة.
     */
    @Test
    fun `isInside compares on path boundaries not on text prefixes`() {
        assertTrue(FileBrowser.isInside("/data/adb/modules", "/data/adb"))
        assertTrue(FileBrowser.isInside("/data/adb", "/data/adb"))
        assertFalse(FileBrowser.isInside("/data2", "/data"))
        assertFalse(FileBrowser.isInside("/data", "/data/adb"))
        assertTrue(FileBrowser.isInside("/anything", "/"))
    }

    // ── الترتيب الطبيعي ───────────────────────────────────────────────────────

    @Test
    fun `natural compare orders numbers as numbers`() {
        assertTrue(FileBrowser.naturalCompare("file2", "file10") < 0)
        assertTrue(FileBrowser.naturalCompare("file10", "file2") > 0)
        assertTrue(FileBrowser.naturalCompare("a", "a1") < 0)
        assertEquals(0, FileBrowser.naturalCompare("File", "file"))
    }

    @Test
    fun `leading zeros do not change the numeric order`() {
        assertEquals(0, FileBrowser.naturalCompare("file007", "file7"))
        assertTrue(FileBrowser.naturalCompare("file007", "file70") < 0)
    }

    // ── القراءة ───────────────────────────────────────────────────────────────

    private fun line(
        type: String,
        octal: String,
        symbolic: String,
        owner: String,
        group: String,
        size: String,
        mtime: String,
        link: String,
        name: String,
    ) = listOf(type, octal, symbolic, owner, group, size, mtime, link, name).joinToString("\t")

    @Test
    fun `stat format declares exactly the fields the parser reads`() {
        // هذا الاختبار يربط الصيغة المُنفَّذة بالمحلّل: تغيير أحدهما بلا الآخر يُسقطه.
        assertEquals(9, FileStatParser.FORMAT.split('\t').size)
        val parsed = FileStatParser.parse(
            listOf(
                line("directory", "755", "drwxr-xr-x", "root", "root", "4096", "1700000000", "", "modules")
            ),
            "/data/adb",
        )
        assertEquals(0, parsed.skipped)
        assertEquals(1, parsed.entries.size)
    }

    @Test
    fun `a parsed entry carries every measured attribute`() {
        val parsed = FileStatParser.parse(
            listOf(line("regular file", "644", "-rw-r--r--", "system", "system", "128", "1700000001", "", "build.prop")),
            "/system",
        )
        val entry = parsed.entries.single()
        assertEquals("build.prop", entry.name)
        assertEquals("/system/build.prop", entry.path)
        assertEquals(FileKind.RegularFile, entry.kind)
        assertEquals(128L, entry.sizeBytes)
        assertEquals(1700000001L, entry.modifiedEpochSec)
        assertEquals("system", entry.owner)
        assertEquals("644", entry.permissions?.octal)
        // العرض يفضّل الصيغة الرمزية حين تصل — والرقم الثماني احتياط عند غيابها.
        assertEquals("-rw-r--r--", FileFormat.permissions(entry.permissions))
        assertEquals("755", FileFormat.permissions(FilePermissions("755", "")))
        assertTrue(entry.hasFullAttributes)
    }

    /**
     * الاسم آخر حقل، فاسم فيه تبويب لا يُفسد بقية الحقول. وهذا شرط التنسيق لا صدفة.
     */
    @Test
    fun `a name containing a tab does not corrupt the other fields`() {
        val parsed = FileStatParser.parse(
            listOf(line("regular file", "600", "-rw-------", "root", "root", "42", "1699999999", "", "odd\tname")),
            "/data",
        )
        assertEquals(0, parsed.skipped)
        val entry = parsed.entries.single()
        assertEquals("odd\tname", entry.name)
        assertEquals(600, entry.permissions?.octal?.toInt())
        assertEquals(42L, entry.sizeBytes)
    }

    @Test
    fun `lines that do not match the format are counted not silently dropped`() {
        val parsed = FileStatParser.parse(
            listOf(
                "garbage",
                "",
                line("regular file", "600", "-rw-------", "root", "root", "1", "1", "", ""),
                line("regular file", "600", "-rw-------", "root", "root", "1", "1", "", "ok"),
            ),
            "/data",
        )
        assertEquals(1, parsed.entries.size)
        assertEquals(2, parsed.skipped)
    }

    @Test
    fun `symlink target is kept and never invented`() {
        val parsed = FileStatParser.parse(
            listOf(line("symbolic link", "777", "lrwxrwxrwx", "root", "root", "0", "1", "/system/build.prop", "link")),
            "/",
        )
        val entry = parsed.entries.single()
        assertEquals(FileKind.Symlink, entry.kind)
        assertEquals("/system/build.prop", entry.symlinkTarget)
        assertTrue(entry.isSymlink)
    }

    @Test
    fun `degraded listing declares unknown attributes instead of zero`() {
        val entries = FileStatParser.degraded(listOf("a", ".", "..", "b"), "/data")
        assertEquals(listOf("a", "b"), entries.map { it.name })
        assertTrue(entries.all { it.sizeBytes == null })
        assertTrue(entries.all { it.modifiedEpochSec == null })
        assertTrue(entries.all { it.kind == FileKind.Unknown })
        assertFalse(entries.first().hasFullAttributes)
    }

    @Test
    fun `unknown stat words become Unknown rather than a guess`() {
        assertEquals(FileKind.Unknown, FileKind.fromStatWord("something new"))
        assertEquals(FileKind.Directory, FileKind.fromStatWord("directory"))
        assertEquals(FileKind.RegularFile, FileKind.fromStatWord("regular empty file"))
        assertEquals(FileKind.Symlink, FileKind.fromStatWord("link"))
    }

    // ── الصلاحيات والحجم ──────────────────────────────────────────────────────

    @Test
    fun `special permission bits come from the octal not from the letters`() {
        assertTrue(FilePermissions("4755", "-rwsr-xr-x").setUid)
        assertTrue(FilePermissions("2755", "-rwxr-sr-x").setGid)
        assertTrue(FilePermissions("1777", "drwxrwxrwt").sticky)
        assertFalse(FilePermissions("755", "drwxr-xr-x").isUnusual)
    }

    @Test
    fun `size formatting says unknown for unknown and never zero for it`() {
        assertNull(FileFormat.size(null))
        assertEquals("0 B", FileFormat.size(0L))
        assertEquals("1.0 KB", FileFormat.size(1024L))
        assertEquals("1.0 MB", FileFormat.size(1024L * 1024))
    }

    // ── الترتيب والتصفية ──────────────────────────────────────────────────────

    private fun entry(name: String, directory: Boolean = false, size: Long? = null, mtime: Long? = null) =
        FileEntry(
            name = name,
            path = "/root/$name",
            kind = if (directory) FileKind.Directory else FileKind.RegularFile,
            sizeBytes = size,
            modifiedEpochSec = mtime,
        )

    @Test
    fun `sorting keeps folders first by default`() {
        val sorted = FileBrowser.sort(
            listOf(entry("b.txt"), entry("Adir", directory = true), entry("a.txt")),
            FileSort(),
        )
        assertEquals(listOf("Adir", "a.txt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun `descending sort mirrors the whole order`() {
        val sorted = FileBrowser.sort(
            listOf(entry("a"), entry("c"), entry("b")),
            FileSort(ascending = false),
        )
        assertEquals(listOf("c", "b", "a"), sorted.map { it.name })
    }

    @Test
    fun `a size tie is broken by name so rows never reshuffle`() {
        val sorted = FileBrowser.sort(
            listOf(entry("z", size = 10), entry("a", size = 10)),
            FileSort(key = FileSortKey.Size),
        )
        assertEquals(listOf("a", "z"), sorted.map { it.name })
    }

    @Test
    fun `entries with unknown size sort as unknown not as zero`() {
        val sorted = FileBrowser.sort(
            listOf(entry("known", size = 0), entry("unknown")),
            FileSort(key = FileSortKey.Size),
        )
        assertEquals(listOf("unknown", "known"), sorted.map { it.name })
    }

    @Test
    fun `filter matches on the name case-insensitively`() {
        val filtered = FileBrowser.filter(
            listOf(entry("Build.prop"), entry("notes.txt")),
            "build",
        )
        assertEquals(listOf("Build.prop"), filtered.map { it.name })
        assertEquals(2, FileBrowser.filter(listOf(entry("a"), entry("b")), "  ").size)
    }

    // ── الاختيار ──────────────────────────────────────────────────────────────

    @Test
    fun `selection toggles, selects all and inverts`() {
        val all = listOf(entry("a"), entry("b"), entry("c"))
        var selection = FileSelection()
        selection = selection.toggle("/root/a")
        assertTrue("/root/a" in selection.paths)
        selection = selection.toggle("/root/a")
        assertTrue(selection.isEmpty)

        selection = selection.selectAll(all)
        assertEquals(3, selection.count)
        selection = selection.invert(all)
        assertTrue(selection.isEmpty)
    }

    // ── ذاكرة المجلدات ────────────────────────────────────────────────────

    @Test
    fun `the cache returns what was stored for the same path`() {
        val cache = DirectoryCache()
        cache.put(DirectoryListing.Entries("/data", listOf(entry("x"))))
        assertNotNull(cache.get("/data"))
        assertNotNull(cache.get("/data/"))
        assertNull(cache.get("/other"))
    }

    @Test
    fun `the cache evicts the least recently used folder`() {
        val cache = DirectoryCache(capacity = 2)
        cache.put(DirectoryListing.Entries("/a", emptyList()))
        cache.put(DirectoryListing.Entries("/b", emptyList()))
        assertNotNull(cache.get("/a")) // /a صار الأحدث استعمالًا
        cache.put(DirectoryListing.Entries("/c", emptyList()))
        assertEquals(2, cache.size)
        assertNotNull(cache.get("/a"))
        assertNull(cache.get("/b"))
    }

    @Test
    fun `a failed read is cached too and never re-branded as success`() {
        val cache = DirectoryCache()
        cache.put(DirectoryListing.Unreadable("/root", ListingFailure.PermissionDenied))
        val cached = cache.get("/root")
        assertTrue(cached is DirectoryListing.Unreadable)
        assertEquals(
            ListingFailure.PermissionDenied,
            (cached as DirectoryListing.Unreadable).reason,
        )
    }

    @Test
    fun `invalidating clears everything after an operation`() {
        val cache = DirectoryCache()
        cache.put(DirectoryListing.Entries("/a", emptyList()))
        cache.put(DirectoryListing.Entries("/b", emptyList()))
        cache.invalidateAll()
        assertEquals(0, cache.size)
    }

    // ── الحرس ─────────────────────────────────────────────────────────────────

    @Test
    fun `an empty selection is refused rather than executed`() {
        val verdict = FileOpGuard.check(FileOpRequest(FileOperation.Delete))
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.EmptySelection), verdict)
    }

    @Test
    fun `the filesystem root is a protected target`() {
        val verdict = FileOpGuard.check(FileOpRequest(FileOperation.Delete, sources = listOf("/")))
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.ProtectedPath), verdict)
    }

    @Test
    fun `copying onto the same path is refused`() {
        val verdict = FileOpGuard.check(
            FileOpRequest(FileOperation.Copy, sources = listOf("/data/x"), destination = "/data/x"),
        )
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.SelfTarget), verdict)
    }

    /**
     * أشهر خطأ مدمّر في مدراء الملفات: نسخ مجلد داخل نفسه يملأ القرص بشجرة لا تنتهي.
     */
    @Test
    fun `copying a folder inside itself is refused`() {
        val verdict = FileOpGuard.check(
            FileOpRequest(FileOperation.Copy, sources = listOf("/sdcard/DCIM"), destination = "/sdcard/DCIM/backup"),
            directories = setOf("/sdcard/DCIM"),
        )
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.TargetInsideSource), verdict)
    }

    @Test
    fun `moving a folder inside itself is refused as well`() {
        val verdict = FileOpGuard.check(
            FileOpRequest(FileOperation.Move, sources = listOf("/data/app"), destination = "/data/app/sub"),
            directories = setOf("/data/app"),
        )
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.TargetInsideSource), verdict)
    }

    @Test
    fun `a sibling folder with a shared prefix is not treated as a descendant`() {
        val verdict = FileOpGuard.check(
            FileOpRequest(FileOperation.Copy, sources = listOf("/data/log"), destination = "/data/log2"),
            directories = setOf("/data/log"),
        )
        assertEquals(FileOpVerdict.Allowed, verdict)
    }

    @Test
    fun `names with a separator or the dot entries are refused`() {
        listOf("a/b", ".", "..", "", "   ").forEach { bad ->
            assertEquals(
                "expected '$bad' to be refused",
                FileOpVerdict.Refused(FileOpRefusal.InvalidName),
                FileOpGuard.check(FileOpRequest(FileOperation.CreateDirectory, newName = bad)),
            )
        }
        assertFalse(FileOpGuard.isValidName("a\u0000b"))
    }

    @Test
    fun `creating a folder that exists is refused by name`() {
        val verdict = FileOpGuard.check(
            FileOpRequest(FileOperation.CreateDirectory, newName = "Download"),
            existingNames = setOf("Download"),
        )
        assertEquals(FileOpVerdict.Refused(FileOpRefusal.NameTaken), verdict)
    }

    @Test
    fun `a valid copy is allowed — the guard must not block everything`() {
        val verdict = FileOpGuard.check(
            FileOpRequest(FileOperation.Copy, sources = listOf("/sdcard/DCIM"), destination = "/sdcard/Backup"),
            directories = setOf("/sdcard/DCIM"),
        )
        assertEquals(FileOpVerdict.Allowed, verdict)
    }

    @Test
    fun `uniqueName never overwrites an existing entry`() {
        assertEquals("a", FileOpGuard.uniqueName("a", emptySet()))
        assertEquals("a (1)", FileOpGuard.uniqueName("a", setOf("a")))
        assertEquals("a (2)", FileOpGuard.uniqueName("a", setOf("a", "a (1)")))
    }
}
