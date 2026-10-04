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

package nd.max.ui.subscreens

import nd.max.ui.util.ArchiveFormat
import nd.max.ui.util.CompressionLevel
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileArchive
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileKind
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOperation
import nd.max.ui.util.WindowSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * نموذج حوار الضغط — الاسم والصيغة والمستوى **قبل** أن يُبنى الطلب.
 *
 * وأهمّ ما يُقاس: أن الاسم الذي يعِد به الحوار هو **نفسه** الامتداد الذي يكتبه المحرّك
 * (اختيار `tar.gz` وذكر `zip` يعطي ملفًّا يُفكّ خطأً)، وأن تغيير الصيغة **يتبع** الامتداد لا
 * يقفله فيخدع الاسمُ المحتوى.
 */
class FileManagerCompressModelTest {

    private fun entry(name: String, kind: FileKind = FileKind.RegularFile) = FileEntry(
        name = name,
        path = "/root/$name",
        kind = kind,
    )

    @Test
    fun `the default archive name is the first source with the chosen extension`() {
        val state = compressStateFor(listOf(entry("DCIM")), WindowSide.First, ArchiveFormat.Zip)!!
        assertEquals("DCIM.zip", state.name)
        assertEquals(ArchiveFormat.Zip, state.format)
        assertEquals(CompressionLevel.Normal, state.level)
        assertNull(state.nameProblem)
    }

    @Test
    fun `a taken name is made unique before the user sees it`() {
        val state = compressStateFor(
            listOf(entry("DCIM"), entry("DCIM.zip")),
            WindowSide.First,
            ArchiveFormat.Zip,
        )!!
        assertEquals("DCIM.zip (1)", state.name)
    }

    @Test
    fun `the tar format names the archive tar gz`() {
        val state = compressStateFor(listOf(entry("DCIM")), WindowSide.First, ArchiveFormat.TarGz)!!
        assertEquals("DCIM.tar.gz", state.name)
    }

    @Test
    fun `changing the format moves the extension instead of leaving it lying`() {
        assertEquals("x.tar.gz", FileArchive.renamedForFormat("x.zip", ArchiveFormat.TarGz))
        assertEquals("x.zip", FileArchive.renamedForFormat("x.tar.gz", ArchiveFormat.Zip))
        assertEquals("x.zip", FileArchive.renamedForFormat("x.tgz", ArchiveFormat.Zip))
        // اسم بلا امتداد معروف يُعطى امتداد الصيغة، فلا يبقى ملفًّا لا يُفتح بنقرة.
        assertEquals("my backup.zip", FileArchive.renamedForFormat("my backup", ArchiveFormat.Zip))
    }

    // ────────────────────────────────────────────────────────────────────────
    // رفض الاسم
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a blank or path-bearing name is refused as invalid`() {
        assertEquals(FileOpRefusal.InvalidName, compressNameRefusal("", emptySet()))
        assertEquals(FileOpRefusal.InvalidName, compressNameRefusal("   ", emptySet()))
        assertEquals(FileOpRefusal.InvalidName, compressNameRefusal("a/b.zip", emptySet()))
        assertEquals(FileOpRefusal.InvalidName, compressNameRefusal("..", emptySet()))
    }

    @Test
    fun `a name already present in the destination is refused as taken`() {
        assertEquals(FileOpRefusal.NameTaken, compressNameRefusal("a.zip", setOf("a.zip")))
    }

    @Test
    fun `a fresh valid name passes`() {
        assertNull(compressNameRefusal("a.zip", setOf("b.zip")))
    }

    // ────────────────────────────────────────────────────────────────────────
    // تعديل الحالة: الاسم والصيغة والمستوى
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `naming validates against the destination window`() {
        val state = compressStateFor(listOf(entry("a")), WindowSide.First, ArchiveFormat.Zip)!!

        val fresh = state.named("pack.zip") { emptyList() }
        assertEquals("pack.zip", fresh.name)
        assertNull(fresh.nameProblem)

        assertEquals(FileOpRefusal.NameTaken, state.named("pack.zip") { listOf(entry("pack.zip")) }.nameProblem)
        assertEquals(FileOpRefusal.InvalidName, state.named("a/b.zip") { emptyList() }.nameProblem)
    }

    @Test
    fun `switching the format moves the extension and keeps the level`() {
        val state = compressStateFor(listOf(entry("a")), WindowSide.First, ArchiveFormat.Zip)!!
            .atLevel(CompressionLevel.Store)

        val tarred = state.switchedTo(ArchiveFormat.TarGz)
        assertEquals("a.tar.gz", tarred.name)
        assertEquals(ArchiveFormat.TarGz, tarred.format)
        assertEquals(CompressionLevel.Store, tarred.level)

        assertEquals("a.zip", tarred.switchedTo(ArchiveFormat.Zip).name)
    }

    @Test
    fun `the level chosen is carried into the request`() {
        val state = compressStateFor(listOf(entry("a")), WindowSide.First)!!.atLevel(CompressionLevel.Maximum)
        assertEquals(CompressionLevel.Maximum, state.level)
        assertEquals(CompressionLevel.Maximum, compressRequest(state, "/sdcard").compressionLevel)
    }

    // ────────────────────────────────────────────────────────────────────────
    // الطلب
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the request carries the format and level the user chose`() {
        val state = compressStateFor(listOf(entry("a"), entry("b")), WindowSide.Second, ArchiveFormat.TarGz)!!
            .copy(level = CompressionLevel.Maximum, name = "pack.tar.gz")

        val request = compressRequest(state, "/sdcard")

        assertEquals(FileOperation.Compress, request.operation)
        assertEquals("pack.tar.gz", request.destination.orEmpty().substringAfterLast('/'))
        assertEquals("/sdcard/pack.tar.gz", request.destination)
        assertEquals(ArchiveFormat.TarGz, request.archiveFormat)
        assertEquals(CompressionLevel.Maximum, request.compressionLevel)
        assertEquals(listOf("/root/a", "/root/b"), request.sources)
    }

    /**
     * الضغط **ليس** إجراءً فوريًّا: من ناداه من [immediateRequest] كان سيُنفّذ بلا أن يختار
     * المستخدم اسمًا أو صيغة — وهو ما يمنعه العائد `null` هنا ثم يفتح الحوار.
     */
    @Test
    fun `compress is not an immediate action`() {
        assertNull(immediateRequest(FileAction.Compress, listOf(entry("a")), "/sdcard"))
    }
}
