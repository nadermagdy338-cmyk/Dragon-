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

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Collections
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import nd.max.core.jni.ArchivePacket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * محرّك zip — يُقاس على **شجرة حقيقية** في مجلد مؤقّت لا بأمثلة ملفّقة.
 *
 * وأهمّ ما يُقاس فيه: أن مدخلًا خطِرًا في الأرشيف (`../`) يُرفض **قبل** أن تُكتب بايت
 * واحدة على القرص — وهذا هجوم `zip-slip` الذي يكتب خارج المجلد المطلوب، ولا يكشفه
 * اختبار يفحص «هل نجح الفكّ؟» بل اختبار يفحص **أين كُتب**.
 */
class FileArchiveEngineTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("fm-archive").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(relative: String, content: String): File {
        val file = File(root, relative)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun entryNames(archive: File): List<String> =
        ZipFile(archive).use { zip -> Collections.list(zip.entries()).map { it.name } }

    // ────────────────────────────────────────────────────────────────────────
    // الدورة الكاملة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun aFolderIsCompressedAndExtractedBackWithTheSameContent() {
        val source = write("work/notes.txt", "hello")
        write("work/nested/deep.txt", "deep")
        val archive = File(root, "out.zip")

        val packed = FileArchiveEngine.createZip(listOf(source.parentFile!!.path), archive.path)
        assertTrue(packed.ok)
        assertTrue(archive.exists())

        // المداخل تحمل اسم المجلد الأب كما يفعل `tar -C`: فكُّها على جهاز آخر لا يكتب
        // في مسار مطلق لا يملكه المستخدم.
        val names = entryNames(archive)
        assertTrue(names.contains("work/notes.txt"))
        assertTrue(names.contains("work/nested/deep.txt"))

        val destination = File(root, "unpacked")
        val unpacked = FileArchiveEngine.extractZip(archive.path, destination.path)
        assertTrue(unpacked.ok)
        assertEquals("hello", File(destination, "work/notes.txt").readText())
        assertEquals("deep", File(destination, "work/nested/deep.txt").readText())
    }

    /** مجلد فارغ لا يضيع: يُكتب مدخلًا بشرطة أخيرة. */
    @Test
    fun anEmptyFolderSurvivesAsAnEntry() {
        val source = write("tree/keep.txt", "x")
        File(root, "tree/empty").mkdirs()
        val archive = File(root, "empty.zip")

        assertTrue(FileArchiveEngine.createZip(listOf(source.parentFile!!.path), archive.path).ok)
        assertTrue(entryNames(archive).contains("tree/empty/"))

        val destination = File(root, "back")
        assertTrue(FileArchiveEngine.extractZip(archive.path, destination.path).ok)
        assertTrue(File(destination, "tree/empty").isDirectory)
    }

    /** الأرشيف الناتج لا يُضغط داخل نفسه — وإلا صار ملفًا ينمو بلا نهاية. */
    @Test
    fun theArchiveIsNeverCompressedIntoItself() {
        val source = write("self/data.txt", "x")
        val archive = File(root, "self/out.zip")
        archive.writeText("stale") // ملف موجود فعلًا وقت التمشية

        assertTrue(FileArchiveEngine.createZip(listOf(source.parentFile!!.path), archive.path).ok)
        assertFalse(entryNames(archive).any { it.endsWith("out.zip") })
    }

    // ────────────────────────────────────────────────────────────────────────
    // السلامة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun aZipSlipEntryIsRefusedBeforeAnythingIsWritten() {
        val archive = File(root, "evil.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("boom".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("harmless.txt"))
            zip.write("ok".toByteArray())
            zip.closeEntry()
        }

        val destination = File(root, "dest")
        val result = FileArchiveEngine.extractZip(archive.path, destination.path)

        assertEquals(ArchiveFailure.UnsafeEntry, result.failure)
        assertFalse("لا ملف خارج الوجهة", File(root, "evil.txt").exists())
        // ولا نصف فكّ: مدخل سليم مع مدخل خطِر ⇒ لا شيء يُكتب.
        assertFalse(destination.exists())
    }

    @Test
    fun aFileThatIsNotAZipIsDeclaredCorrupt() {
        val notZip = write("plain.txt", "this is not a zip at all")
        val result = FileArchiveEngine.extractZip(notZip.path, File(root, "dest").path)
        assertEquals(ArchiveFailure.CorruptArchive, result.failure)
    }

    @Test
    fun aMissingSourceIsDeclaredUnreadable() {
        val result = FileArchiveEngine.createZip(listOf(File(root, "ghost").path), File(root, "o.zip").path)
        assertEquals(ArchiveFailure.UnreadableSource, result.failure)
        assertFalse(File(root, "o.zip").exists())
    }

    @Test
    fun noSourcesIsItsOwnFailure() {
        val result = FileArchiveEngine.createZip(emptyList(), File(root, "o.zip").path)
        assertEquals(ArchiveFailure.NoSources, result.failure)
    }

    @Test
    fun theEntrySafetyCheckFollowsPathSegmentsNotRawText() {
        assertFalse(FileArchiveEngine.isUnsafeEntry("a/b.txt"))
        assertFalse(FileArchiveEngine.isUnsafeEntry("12:30 recording.mp3"))
        assertFalse(FileArchiveEngine.isUnsafeEntry("a..b/c"))
        assertTrue(FileArchiveEngine.isUnsafeEntry("/etc/passwd"))
        assertTrue(FileArchiveEngine.isUnsafeEntry("../../x"))
        assertTrue(FileArchiveEngine.isUnsafeEntry("C:/x"))
        assertTrue(FileArchiveEngine.isUnsafeEntry("   "))
    }

    // ────────────────────────────────────────────────────────────────────────
    // التقدّم والأحكام
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun progressIsMeasuredAndEndsAtTheTotal() {
        write("data/a.bin", "a".repeat(1024))
        write("data/b.bin", "b".repeat(2048))
        val seen = mutableListOf<Pair<Long, Long>>()

        val result = FileArchiveEngine.createZip(
            listOf(File(root, "data").path),
            File(root, "p.zip").path,
        ) { done, total -> seen += done to total }

        assertTrue(result.ok)
        assertEquals(3072L, seen.last().second)
        assertEquals(3072L, seen.last().first)
        // رتابة: الشريط لا يعود إلى الوراء.
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
    }

    // ────────────────────────────────────────────────────────────────────────
    // السلّم الأصلي ← Kotlin (ADR-49)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun aNativeSuccessIsVerifiedOnlyWhenTheArchiveIsRealOnDisk() {
        val archive = write("out.zip", "not empty")
        assertEquals(
            ArchiveOutcome.verified(),
            ArchivePacket.Result.Ok(entries = 3, bytes = 1024L).toOutcome(archive),
        )

        val empty = File(root, "empty.zip").apply { createNewFile() }
        assertEquals(
            "أرشيف بطول صفر = نُفِّذ ولم يُتحقّق (لا نجاح بلا إثبات)",
            ArchiveOutcome.executedOnly(),
            ArchivePacket.Result.Ok(entries = 0, bytes = 0L).toOutcome(empty),
        )
    }

    @Test
    fun nativeFailureReasonsMapToTheSeparateArchiveFailures() {
        val archive = File(root, "never.zip")
        assertEquals(
            ArchiveFailure.NoSources,
            ArchivePacket.Result.Failed("no_sources", null).toOutcome(archive).failure,
        )
        val unreadable = ArchivePacket.Result.Failed("unreadable", "/data/x")
            .toOutcome(archive)
        assertEquals(ArchiveFailure.UnreadableSource, unreadable.failure)
        assertEquals("/data/x", unreadable.subject)
        assertEquals(
            ArchiveFailure.WriteFailed,
            ArchivePacket.Result.Failed("write_failed", "/out.zip: disk full").toOutcome(archive).failure,
        )
    }

    @Test
    fun anUnknownNativeReasonIsDeclaredWriteFailedNotSwallowed() {
        // سبب لم نعرفه لا يصير «نجاحًا» ولا يُترجم إلى «لا مصادر»: يُعلن فشلًا بعينه.
        val outcome = ArchivePacket.Result.Failed("something_new", null).toOutcome(File(root, "x.zip"))
        assertEquals(ArchiveFailure.WriteFailed, outcome.failure)
        assertFalse(outcome.ok)
    }

    @Test
    fun theKotlinPathIsKeptWhenARealProgressCallbackIsAsked() {
        // المسار الأصلي لا يُبلّغ تقدّمًا، فمن طلب تقدّمًا حقيقيًّا يأخذ مسار Kotlin بعينه —
        // والاختبار هنا يثبت أن الحكم لا يتغيّر بذلك.
        write("work/data.bin", "payload")
        val seen = mutableListOf<Pair<Long, Long>>()
        val withProgress = FileArchiveEngine.createZip(
            listOf(File(root, "work").path),
            File(root, "with.zip").path,
        ) { done, total -> seen += done to total }
        val withoutProgress = FileArchiveEngine.createZip(
            listOf(File(root, "work").path),
            File(root, "without.zip").path,
        )
        assertTrue(withProgress.ok)
        assertTrue(withoutProgress.ok)
        assertTrue("مسار التقدّم يُبلّغ فعلًا", seen.isNotEmpty())
        assertEquals(entryNames(File(root, "with.zip")), entryNames(File(root, "without.zip")))
    }

    @Test
    fun aMissingSourceIsStillDeclaredUnreadableThroughTheDefaultEntryPoint() {
        val result = FileArchiveEngine.createZip(
            listOf(File(root, "ghost").path),
            File(root, "default.zip").path,
        )
        assertEquals(ArchiveFailure.UnreadableSource, result.failure)
        assertFalse(File(root, "default.zip").exists())
    }

    @Test
    fun theOutcomeMapsOntoTheThreeProjectVerdicts() {
        assertEquals(FileOpOutcome(true, true), ArchiveOutcome.verified().toFileOpOutcome())
        assertEquals(FileOpOutcome(true, false), ArchiveOutcome.executedOnly().toFileOpOutcome())
        assertEquals(
            FileOpOutcome(false, false),
            ArchiveOutcome.failed(ArchiveFailure.WriteFailed).toFileOpOutcome(),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // tar.gz — الصيغة الداخلية الجديدة
    // ────────────────────────────────────────────────────────────────────────

    /** gzip لـtar كتبه [FileTarCodec] — يُصنع بأسماء مداخل مُختارة يدويًّا (لا من قرص). */
    private fun gzipTar(entries: List<Pair<File, String>>): File {
        val archive = File(root, "evil.tar.gz")
        GZIPOutputStream(FileOutputStream(archive)).use { gzip ->
            FileTarCodec.write(entries, gzip)
        }
        return archive
    }

    @Test
    fun aTarGzRoundTripsThroughTheDispatcher() {
        val source = write("work/notes.txt", "hello tar")
        write("work/nested/deep.txt", "deep tar")
        File(root, "work/empty").mkdirs()
        val archive = File(root, "out.tar.gz")

        val packed = FileArchiveEngine.createTarGz(listOf(source.parentFile!!.path), archive.path)
        assertTrue(packed.ok)
        assertTrue(archive.exists())
        assertTrue(archive.length() > 0L)

        val destination = File(root, "untar")
        val unpacked = FileArchiveEngine.extractArchive(archive.path, destination.path)
        assertTrue(unpacked.ok)
        assertEquals("hello tar", File(destination, "work/notes.txt").readText())
        assertEquals("deep tar", File(destination, "work/nested/deep.txt").readText())
        assertTrue("المجلد الفارغ وصل", File(destination, "work/empty").isDirectory)
    }

    /**
     * الفحص الأمني في مسار tar **قبل كتابة كل مدخل** — والمدخل غير الآمن يطلب محو ما كُتب
     * في هذه الدعوة وحدها، فلا يبقى على القرص نصف فكّ يُظنّ أنه كامل.
     */
    @Test
    fun aTarSlipEntryIsRefusedAndWhatWasWrittenIsRemoved() {
        val good = write("payload.bin", "ok")
        val archive = gzipTar(
            listOf(
                good to "harmless.txt",
                good to "../evil.txt",
            ),
        )

        val destination = File(root, "dest")
        val result = FileArchiveEngine.extractArchive(archive.path, destination.path)

        assertEquals(ArchiveFailure.UnsafeEntry, result.failure)
        assertFalse("لا ملف خارج الوجهة", File(root, "evil.txt").exists())
        assertFalse("المدخل السليم مُحي أيضًا", File(destination, "harmless.txt").exists())
    }

    @Test
    fun aFileThatIsNotATarIsDeclaredCorrupt() {
        val notTar = write("plain.tar.gz", "this is not a tar at all")
        val result = FileArchiveEngine.extractArchive(notTar.path, File(root, "dest").path)
        assertEquals(ArchiveFailure.CorruptArchive, result.failure)
    }

    @Test
    fun theDispatcherPicksTheFormatFromTheName() {
        val source = write("pick/data.txt", "x")
        val zipped = File(root, "pick.zip")
        val tarred = File(root, "pick.tar.gz")

        assertTrue(FileArchiveEngine.create(listOf(source.parentFile!!.path), zipped.path, ArchiveFormat.Zip).ok)
        assertTrue(FileArchiveEngine.create(listOf(source.parentFile!!.path), tarred.path, ArchiveFormat.TarGz).ok)

        // كل ملف يُفكّ بمسار صيغته: zip بمدخلاته، وtar.gz الذي يبدأ ببايت gzip السحري.
        assertTrue(FileArchiveEngine.extractArchive(zipped.path, File(root, "z").path).ok)
        assertTrue(FileArchiveEngine.extractArchive(tarred.path, File(root, "t").path).ok)
        assertEquals("x", File(root, "z/pick/data.txt").readText())
        assertEquals("x", File(root, "t/pick/data.txt").readText())
    }

    @Test
    fun tarGzProgressIsMonotonicAndItsTotalMatchesThePayload() {
        write("big/a.bin", "a".repeat(4096))
        write("big/b.bin", "b".repeat(2048))
        val seen = mutableListOf<Pair<Long, Long>>()

        val result = FileArchiveEngine.createTarGz(
            listOf(File(root, "big").path),
            File(root, "big.tar.gz").path,
        ) { done, total -> seen += done to total }

        assertTrue(result.ok)
        assertTrue("التقدّم يُبلَّغ", seen.isNotEmpty())
        assertEquals(6144L, seen.last().second)
        assertEquals(6144L, seen.last().first)
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
    }

    @Test
    fun noSourcesIsItsOwnFailureForTarToo() {
        assertEquals(
            ArchiveFailure.NoSources,
            FileArchiveEngine.createTarGz(emptyList(), File(root, "o.tar.gz").path).failure,
        )
    }
}
