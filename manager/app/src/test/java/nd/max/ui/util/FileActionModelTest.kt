/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * أيّ إجراء ينطبق على أيّ تحديد.
 *
 * وأهمّ اختبارٍ هنا هو **مانع التحكّم الميّت**: عطب وقع فعلًا في هذا المستودع — بعد إعادة
 * كتابة الشاشة للوحين بقي إجراء «إعادة التسمية» بلا مسار واجهة، وبقي فكّ الأرشيف معرَّفًا
 * في المحرّك والحرس مع **لا زرّ يناديه**. وكلاهما لا يظهر في تصريف ولا في اختبار يفحص ما
 * هو موجود — بل ما هو **موصول**. فصار لكل إجراء اختبار يثبت أن تحديدًا ما ينتجه.
 */
class FileActionModelTest {

    private fun file(name: String, path: String = "/root/$name") = FileEntry(
        name = name,
        path = path,
        kind = FileKind.RegularFile,
        sizeBytes = 100,
        modifiedEpochSec = 1,
    )

    private fun dir(name: String) = FileEntry(
        name = name,
        path = "/root/$name",
        kind = FileKind.Directory,
    )

    private fun selection(vararg names: String) = FileSelection(names.map { "/root/$it" }.toSet())

    @Test
    fun `nothing selected offers nothing`() {
        assertTrue(FileActionSet.forSelection(listOf(file("a")), FileSelection()).isEmpty())
    }

    @Test
    fun `a single file offers rename and details but not extract`() {
        val actions = FileActionSet.forSelection(listOf(file("notes.txt")), selection("notes.txt"))
        assertTrue(FileAction.Rename in actions)
        assertTrue(FileAction.Details in actions)
        assertFalse(FileAction.Extract in actions)
        assertTrue(FileAction.Delete in actions)
        assertTrue(FileAction.Clear in actions)
    }

    @Test
    fun `a single supported archive offers extract`() {
        listOf("backup.zip", "BACKUP.ZIP", "backup.tar.gz", "backup.tgz", "backup.tar", "BACKUP.TAR.GZ").forEach { name ->
            val actions = FileActionSet.forSelection(listOf(file(name)), selection(name))
            assertTrue("$name should offer extract", FileAction.Extract in actions)
        }
    }

    /**
     * النوع يُقرأ من الجهاز لا من الاسم: مجلد اسمه `backup.tar.gz` ليس أرشيفًا، وعرض
     * «فكّ» عليه وعدٌ بما لا يمكن.
     */
    @Test
    fun `a folder whose name looks like an archive is not treated as one`() {
        val actions = FileActionSet.forSelection(listOf(dir("backup.tar.gz")), selection("backup.tar.gz"))
        assertFalse(FileAction.Extract in actions)
    }

    @Test
    fun `two selected files offer no single-entry action`() {
        val entries = listOf(file("a.tar.gz"), file("b.txt"))
        val actions = FileActionSet.forSelection(entries, selection("a.tar.gz", "b.txt"))
        assertFalse(FileAction.Rename in actions)
        assertFalse(FileAction.Details in actions)
        assertFalse(FileAction.Extract in actions)
        assertTrue(FileAction.Compress in actions)
        assertTrue(FileAction.Copy in actions)
    }

    @Test
    fun `wrong archive names do not offer extract`() {
        listOf("notes.txt", "archive.zipp", "photo.png", "tar.gz", "a.targz").forEach { name ->
            val actions = FileActionSet.forSelection(listOf(file(name)), selection(name))
            assertFalse("$name must not offer extract", FileAction.Extract in actions)
        }
    }

    /**
     * **مانع التحكّم الميّت**: كل إجراء معرَّف يجب أن يُنتجه تحديدٌ ما. أي إجراء جديد
     * يُضاف بلا مسار يُسقط هذا الاختبار بدل أن يبقى زرًّا بلا فعل.
     */
    @Test
    fun `every declared action is reachable by some selection`() {
        val entries = listOf(file("notes.txt"), file("backup.tar.gz"), dir("folder"))
        val reachable = buildSet {
            addAll(FileActionSet.forSelection(entries, selection("notes.txt")))
            addAll(FileActionSet.forSelection(entries, selection("backup.tar.gz")))
            addAll(FileActionSet.forSelection(entries, selection("notes.txt", "folder")))
        }
        val missing = FileAction.entries.filterNot { it in reachable }
        assertEquals("no selection reaches: $missing", emptyList<FileAction>(), missing)
    }

    @Test
    fun `only delete is marked destructive and it is offered last but one`() {
        val destructive = FileAction.entries.filter { it.destructive }
        assertEquals(listOf(FileAction.Delete), destructive)

        val actions = FileActionSet.forSelection(listOf(file("a")), selection("a"))
        assertEquals(FileAction.Delete, actions[actions.lastIndex - 1])
        assertEquals(FileAction.Clear, actions.last())
    }

    @Test
    fun `availability agrees with the offered list`() {
        val entries = listOf(file("a.txt"))
        val selection = selection("a.txt")
        FileAction.entries.forEach { action ->
            assertEquals(
                "availability disagreed for $action",
                action in FileActionSet.forSelection(entries, selection),
                FileActionSet.isAvailable(action, entries, selection),
            )
        }
        assertFalse(FileActionSet.isAvailable(FileAction.Copy, entries, FileSelection()))
    }

    /**
     * اسم الأرشيف الذي يُوعد به المستخدم هو **zip** (كما في MT) وهو نفسه الذي يبنيه
     * المحرّك: اسمان مختلفان بين القائمة والقرص يعطيان ملفًا لا يجده من بحث عنه.
     */
    @Test
    fun `the produced archive name matches what the engine creates`() {
        assertEquals("DCIM.zip", FileArchive.archiveNameFor("DCIM"))
        assertTrue(FileArchive.isSupportedArchive(FileArchive.archiveNameFor("any")))
        assertTrue(FileArchive.isZip(FileArchive.archiveNameFor("any")))

        // والخيار الثاني معلن أيضًا: صيغة tar باسمها الكامل، ويُفكّ كذلك.
        assertEquals("DCIM.tar.gz", FileArchive.tarNameFor("DCIM"))
        assertTrue(FileArchive.isSupportedArchive(FileArchive.tarNameFor("any")))
        assertFalse(FileArchive.isZip(FileArchive.tarNameFor("any")))
    }
}
