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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ترميز `tar` — يُقاس **بالدورة الكاملة** على بايتات حقيقية، لا بمثال ملفّق.
 *
 * وأهمّ ما يُقاس هنا سببان وقع عطبهما فعلًا في هذا النوع من الكود: **الاسم الطويل** (اسم
 * مجلد عربي طويل يتجاوز ١٠٠ بايت، فإمّا يُبتر فينتج ملفّ باسم مشوّه، وإمّا يُنبَّه بالمقسم
 * المعلن)، و**الترويسة بأحجام غير مصفوفة على ٥١٢** (فيتولّد أرشيف يقرؤه `tar` ذيلًا مبتورًا).
 */
class FileTarCodecTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("fm-tar").toFile()
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

    /** أرشيف في الذاكرة — فلا سؤال عن مسار ولا عن صدفة. */
    private fun pack(vararg entries: Pair<File, String>): ByteArray {
        val out = ByteArrayOutputStream()
        FileTarCodec.write(entries.toList(), out)
        return out.toByteArray()
    }

    private fun reader(bytes: ByteArray) = FileTarCodec.Reader(ByteArrayInputStream(bytes))

    // ────────────────────────────────────────────────────────────────────────
    // الدورة الكاملة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `entries written are read back with the same names, kinds and sizes`() {
        val file = write("a.txt", "hello")
        File(root, "sub").mkdirs()
        File(root, "empty").mkdirs()
        val nested = write("sub/b.txt", "deep")

        val bytes = pack(file to "a.txt", File(root, "sub") to "sub/", nested to "sub/b.txt", File(root, "empty") to "empty/")

        val read = reader(bytes)
        val seen = mutableListOf<TarEntry>()
        while (true) {
            val entry = read.next() ?: break
            seen += entry
            read.copyTo(entry, ByteArrayOutputStream())
        }

        assertEquals(listOf("a.txt", "sub", "sub/b.txt", "empty"), seen.map { it.name })
        assertEquals(listOf(false, true, false, true), seen.map { it.isDirectory })
        assertEquals(listOf(5L, 0L, 4L, 0L), seen.map { it.size })
    }

    @Test
    fun `the file content survives the round trip byte for byte`() {
        val payload = "حروف عربية ورمز %1\$d".repeat(200)
        val file = write("payload.bin", payload)

        val read = reader(pack(file to "payload.bin"))
        val entry = read.next()!!
        val out = ByteArrayOutputStream()
        read.copyTo(entry, out)

        assertEquals(payload, out.toString(Charsets.UTF_8.name()))
        assertEquals(payload.toByteArray(Charsets.UTF_8).size.toLong(), entry.size)
    }

    @Test
    fun `write reports exactly the bytes it copied`() {
        val a = write("a.bin", "a".repeat(1000))
        val b = write("b.bin", "b".repeat(37))
        val out = ByteArrayOutputStream()
        val copied = FileTarCodec.write(listOf(a to "a.bin", b to "b.bin"), out)
        assertEquals(1037L, copied)
    }

    /**
     * عطب وقع فعلًا حين وُلد هذا القارئ: `next` بلا `copyTo` تقرأ محتوى المدخل السابق على
     * أنه ترويسة فتُعلن `BadHeader` لأرشيف سليم. والقارئ الآن يتخطّى ما بقي بنفسه.
     */
    @Test
    fun `the end of archive stops the reader`() {
        val file = write("only.txt", "x")
        val read = reader(pack(file to "only.txt"))
        assertEquals("only.txt", read.next()!!.name)
        assertNull("المحتوى غير المقروء يُتخطّى ولا يُقرأ ترويسةً", read.next())
        assertNull("نهاية الأرشيف ثابتة لا تتغيّر بالقراءة مرّة أخرى", read.next())
    }

    @Test
    fun `advancing past an unconsumed entry skips its payload and its padding`() {
        val a = write("a.bin", "a".repeat(700)) // محتوى + حشو
        val b = write("b.bin", "b".repeat(10))

        val read = reader(pack(a to "a.bin", b to "b.bin"))
        assertEquals("a.bin", read.next()!!.name) // بلا نسخ
        assertEquals("b.bin", read.next()!!.name) // يجب أن يصل سليمًا لا BadHeader
        assertNull(read.next())
    }

    // ────────────────────────────────────────────────────────────────────────
    // الأسماء الطويلة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `splitName keeps a short name whole`() {
        assertEquals("" to "a.txt", FileTarCodec.splitName("a.txt"))
    }

    /**
     * ustar يُقسَّم على **آخر** شرطة: `name` هو المكوّن الأخير، و`prefix` ما قبله — وهي
     * القسمة المتعارف عليها، ولا تُبتر أسماء المجلدات.
     */
    @Test
    fun `splitName divides on the last slash when the name is too long`() {
        val longPrefix = "x".repeat(120)
        assertEquals("$longPrefix/dir" to "file.txt", FileTarCodec.splitName("$longPrefix/dir/file.txt"))
    }

    /**
     * وإن لم يسمح المكوّن الأخير بالقسمة (بادئة طويلة)، يُتراجع إلى شرطة أبعد — فلا يُبتر
     * الاسم فينتج ملفّ باسم مشوّه.
     */
    @Test
    fun `splitName walks back when the prefix would be too long`() {
        val longPrefix = "p".repeat(150)
        assertEquals(
            "$longPrefix/aaa" to "sub/file.txt",
            FileTarCodec.splitName("$longPrefix/aaa/sub/file.txt"),
        )
    }

    @Test
    fun `splitName refuses to cut a single component`() {
        // اسم واحد أطول من المسموح بلا شرطة: لا قسمة ممكنة، فيُكتب باسم GNU الطويل.
        assertNull(FileTarCodec.splitName("x".repeat(150)))
    }

    @Test
    fun `a long arabic path survives through the ustar prefix`() {
        // الحروف العربية حرفان في UTF-8، فاسم ظاهره قصير قد يتجاوز ١٠٠ بايت.
        val arabic = "م".repeat(60) // ١٢٠ بايت في UTF-8
        val path = "$arabic/ملف.txt"
        assertTrue(path.toByteArray(Charsets.UTF_8).size > 100)

        val split = FileTarCodec.splitName(path)
        assertTrue("path must fit the prefix+name split", split != null)

        val file = write("arabic.bin", "ok")
        val read = reader(pack(file to path))
        assertEquals(path, read.next()!!.name)
    }

    @Test
    fun `a component longer than the format limit uses a GNU long name entry`() {
        val huge = "n".repeat(150) // > 100 بايت بلا شرطة
        val file = write("huge.bin", "payload")

        val read = reader(pack(file to huge))
        val entry = read.next()!!
        assertEquals(huge, entry.name)
        // والاسم الطويل لا يُربك ما بعده: المحتوى يقرأ كاملًا.
        val out = ByteArrayOutputStream()
        read.copyTo(entry, out)
        assertEquals("payload", out.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `the archive stays aligned to the block on every entry`() {
        // حشو كل مدخل إلى ٥١٢، ثم كتلتا نهاية صفريتان — فالطول النهائي من مضاعفات الكتلة.
        val a = write("a.bin", "a".repeat(100))
        val b = write("bb.bin", "b".repeat(512))
        val bytes = pack(a to "a.bin", b to "bb.bin")

        assertEquals(0, bytes.size % FileTarCodec.BLOCK)
        // ترويستا مدخلين (٥١٢ × ٢) + بيانات محاذاة (٥١٢ + ٥١٢) + كتلتا نهاية (١٠٢٤).
        assertEquals(512L * 6, bytes.size.toLong())
    }

    // ────────────────────────────────────────────────────────────────────────
    // الصيغة والتحقّق
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `padding rounds up to the block`() {
        assertEquals(0L, FileTarCodec.padding(0))
        assertEquals(0L, FileTarCodec.padding(512))
        assertEquals(511L, FileTarCodec.padding(1))
        assertEquals(511L, FileTarCodec.padding(513))
    }

    @Test
    fun `the magic says ustar`() {
        val file = write("a.txt", "x")
        val bytes = pack(file to "a.txt")
        assertEquals("ustar", String(bytes, 257, 5, Charsets.UTF_8))
    }

    /**
     * مجموع التحقّق هو ما يفصل «أرشيف تالف» عن «ملفّ لا علاقة له بـtar»، فترويسة مُعدَّلة
     * ببايت واحد يجب أن **تُعلن** لا أن تُقرأ بصمت.
     */
    @Test
    fun `a damaged header is declared a bad header`() {
        val file = write("a.txt", "hello")
        val bytes = pack(file to "a.txt")
        bytes[0] = 'Z'.code.toByte() // اسم مُعدَّل بلا تحديث المجموع

        val error = runCatching { reader(bytes).next() }.exceptionOrNull()
        assertTrue("expected a TarException, got $error", error is TarException)
        assertEquals(TarFailure.BadHeader, (error as TarException).failure)
    }

    @Test
    fun `a torn header is declared truncated not empty`() {
        val file = write("a.txt", "hello")
        val bytes = pack(file to "a.txt")

        val error = runCatching { reader(bytes.copyOf(300)).next() }.exceptionOrNull()
        assertTrue("expected EOF on a torn header, got $error", error is java.io.EOFException)
    }

    @Test
    fun `a truncated payload is declared not silently accepted`() {
        val file = write("a.txt", "x".repeat(2000))
        val bytes = pack(file to "a.txt")

        val read = reader(bytes.copyOf(512 + 700)) // ترويسة + جزء من المحتوى
        val entry = read.next()!!
        assertFalse("المدخل يُعلن حجمه الكامل", entry.size == 700L)
        val error = runCatching { read.copyTo(entry, ByteArrayOutputStream()) }.exceptionOrNull()
        assertTrue("expected EOF on a truncated payload, got $error", error is java.io.EOFException)
    }

    @Test
    fun `an empty archive reads as no entries`() {
        val out = ByteArrayOutputStream()
        FileTarCodec.write(emptyList(), out)
        assertNull(reader(out.toByteArray()).next())
    }
}
