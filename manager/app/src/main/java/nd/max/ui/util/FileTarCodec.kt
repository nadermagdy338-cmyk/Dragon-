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

/**
 * ترميز `tar` (POSIX ustar) — كتابةً وقراءةً، بلا مكتبة خارجية.
 *
 * **ولماذا وُجد — وحدّه مقيس:** `tar.gz` كان يُنتَج ويُفكّ **بالصدفة المُمتازة**
 * (`PrivilegedShell.run("tar -czf …")`)، أي أنه كان يعمل بشرطين: `tar` على الجهاز +
 * **جذر**. أمّا مسار التطبيق بلا صدفة فكان `java.util.zip` وحده: يُضغط ويُفكّ `zip` فقط،
 * وما تقرؤه العملية نفسها (ملفّ في تخزين مشترك بلا جذر) لم يكن له مسار tar البتّة.
 *
 * فهذا الملف يُنزّل الصيغتين إلى داخل التطبيق (وليس بديلًا عن الصدفة: الصدفة تبقى احتياطًا
 * لما لا تقرؤه العملية، انظر [FileSystemEngine])، ويُقاس في JVM باختبار حقيقيّ — لا جهاز.
 *
 * **وحدوده معلنة لا مخفيّة:**
 *
 * | الحدّ | القيمة | الأثر |
 * | --- | --- | --- |
 * | الاسم الطويل | `prefix` (١٥٥) + `/` + `name` (١٠٠)، وإلا مدخل GNU `L` | الأسماء الطويلة والعربية تمرّ كاملةً |
 * | ترويسات PAX (`x`/`g`) | تُتخطّى | حقائق PAX الموسّعة لا تُقرأ (أرشيفاتنا لا تكتبها) |
 * | الروابط | `linkname` يُتخطّى | رابط رمزي يُفكّ ملفًّا عاديًّا (لا روابط تُنشأ) |
 * | الحجم | ثمانيّ في ١١ خانة | ملفّ > ٨ جيجا يُرفض [TarFailure.SizeTooLarge] |
 *
 * ولا `shell` ولا `Runtime`: بايتات فقط.
 */
package nd.max.ui.util

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** ما يُرفض في tar — بسبب معلَن لا «فشل». */
internal enum class TarFailure {
    /** ترويسة غير صالحة: سحر `ustar` غائب أو مجموع تحقّق لا يوافق. */
    BadHeader,

    /** ملفّ أكبر من سقف الصيغة. */
    SizeTooLarge,
}

internal class TarException(val failure: TarFailure, val subject: String? = null) :
    IOException("tar: $failure${subject?.let { " ($it)" } ?: ""}")

/** مدخل tar — باسم **بلا شرطة أولى** (المسار المطلق يُرفض في العقد الآمن قبل هنا). */
internal data class TarEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val mode: Int = DEFAULT_FILE_MODE,
    val modifiedEpochSec: Long = 0L,
) {
    companion object {
        /** `0644` و`0755` بصيغة الصيغة نفسها (الأرقام الثمانيّة تُكتب كما هي). */
        const val DEFAULT_FILE_MODE = 420 // 0o644
        const val DEFAULT_DIR_MODE = 493 // 0o755
    }
}

internal object FileTarCodec {

    /** حجم الكتلة في الصيغة — كل ترويسة وكل بيانات تُحاذى عليه. */
    const val BLOCK = 512

    private const val NAME_LEN = 100
    private const val PREFIX_LEN = 155
    private const val CHUNK = 64 * 1024
    private const val TYPE_FILE = '0'
    private const val TYPE_DIR = '5'
    private const val TYPE_GNU_LONG_NAME = 'L'

    // ────────────────────────────────────────────────────────────────────────
    // الكتابة
    // ────────────────────────────────────────────────────────────────────────

    /**
     * كتابة أرشيف tar: ترويسة لكل مدخل ثم محتواه مُحاذًى إلى ٥١٢، ثم كتلتا نهاية صفريتان.
     *
     * و`entries` أزواج (ملف، اسم مدخل) واسم المجلد ينتهي بـ`/` — وهو الاصطلاح نفسه في مسار
     * zip في [FileArchiveEngine]، فلا تُترجم الأسماء بين الصيغتين مرّتين.
     */
    fun write(
        entries: List<Pair<File, String>>,
        output: OutputStream,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
        totalBytes: Long = 0L,
    ): Long {
        val buffer = ByteArray(CHUNK)
        var done = 0L
        for ((file, entryName) in entries) {
            val isDirectory = entryName.endsWith("/")
            val entry = TarEntry(
                name = if (isDirectory) entryName.trimEnd('/') else entryName,
                isDirectory = isDirectory,
                size = if (isDirectory) 0L else file.length(),
                mode = if (isDirectory) TarEntry.DEFAULT_DIR_MODE else TarEntry.DEFAULT_FILE_MODE,
                modifiedEpochSec = file.lastModified() / 1000L,
            )
            writeEntry(entry, file, output, buffer) { read ->
                done += read
                progress(done, totalBytes)
            }
        }
        // كتلتان صفريتان = نهاية الأرشيف؛ وغيابهما يُقرأ في كثير من الفاكّات ذيلًا مبتورًا.
        output.write(ByteArray(BLOCK * 2))
        output.flush()
        return done
    }

    private fun writeEntry(
        entry: TarEntry,
        file: File,
        output: OutputStream,
        buffer: ByteArray,
        onChunk: (Int) -> Unit,
    ) {
        val split = splitName(entry.name)
        if (split == null) writeGnuLongName(entry.name, output)
        val namePart = split?.second ?: entry.name.take(NAME_LEN)
        val prefixPart = split?.first ?: ""

        val header = ByteArray(BLOCK)
        putString(header, 0, NAME_LEN, namePart)
        putOctal(header, 100, 8, entry.mode.toLong())
        putOctal(header, 108, 8, 0L) // uid
        putOctal(header, 116, 8, 0L) // gid
        putOctal(header, 124, 12, entry.size)
        putOctal(header, 136, 12, entry.modifiedEpochSec)
        putString(header, 148, 8, "        ") // chksum يُحسب على فراغات
        header[156] = (if (entry.isDirectory) TYPE_DIR else TYPE_FILE).code.toByte()
        putString(header, 257, 6, "ustar")
        header[263] = '0'.code.toByte()
        header[264] = '0'.code.toByte()
        putString(header, 345, PREFIX_LEN, prefixPart)
        putChecksum(header)
        output.write(header)

        if (entry.isDirectory) return
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                onChunk(read)
            }
        }
        val remainder = (entry.size % BLOCK).toInt()
        if (remainder != 0) output.write(ByteArray(BLOCK - remainder))
    }

    private fun writeGnuLongName(name: String, output: OutputStream) {
        val bytes = name.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val header = ByteArray(BLOCK)
        putString(header, 0, NAME_LEN, "././@LongLink")
        putOctal(header, 100, 8, TarEntry.DEFAULT_FILE_MODE.toLong())
        putOctal(header, 124, 12, bytes.size.toLong())
        putString(header, 148, 8, "        ")
        header[156] = TYPE_GNU_LONG_NAME.code.toByte()
        putString(header, 257, 6, "ustar")
        header[263] = '0'.code.toByte()
        header[264] = '0'.code.toByte()
        putChecksum(header)
        output.write(header)
        output.write(bytes)
        val remainder = bytes.size % BLOCK
        if (remainder != 0) output.write(ByteArray(BLOCK - remainder))
    }

    /**
     * قسمة الاسم إلى (prefix، name) كما تقتضي ustar، أو `null` إن لم يسع أيّ قسمةٍ —
     * فيُكتب باسم GNU الطويل. والانقسام **على شرطة**: قصّ الاسم في نصف حرف ينتج ملفًّا
     * باسم مشوّه بدل ملفّ برفض معلن.
     */
    internal fun splitName(name: String): Pair<String, String>? {
        if (name.toByteArray(Charsets.UTF_8).size <= NAME_LEN) return "" to name
        var slash = name.lastIndexOf('/')
        while (slash > 0) {
            val prefix = name.substring(0, slash)
            val rest = name.substring(slash + 1)
            if (prefix.toByteArray(Charsets.UTF_8).size <= PREFIX_LEN &&
                rest.toByteArray(Charsets.UTF_8).size <= NAME_LEN
            ) {
                return prefix to rest
            }
            slash = name.lastIndexOf('/', slash - 1)
        }
        return null
    }

    private fun putString(block: ByteArray, offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        bytes.copyInto(block, offset, 0, minOf(bytes.size, length))
    }

    private fun putOctal(block: ByteArray, offset: Int, length: Int, value: Long) {
        val text = java.lang.Long.toOctalString(value).padStart(length - 1, '0')
        if (text.length > length - 1) throw TarException(TarFailure.SizeTooLarge, value.toString())
        putString(block, offset, length, text)
        block[offset + length - 1] = 0
    }

    private fun putChecksum(header: ByteArray) {
        var sum = 0
        for (byte in header) sum += byte.toInt() and 0xFF
        putString(header, 148, 8, java.lang.Long.toOctalString(sum.toLong()).padStart(6, '0'))
        header[154] = 0
        header[155] = ' '.code.toByte()
    }

    // ────────────────────────────────────────────────────────────────────────
    // القراءة
    // ────────────────────────────────────────────────────────────────────────

    /**
     * قارئ متتابع — لا يحفظ الأرشيف في الذاكرة: كل مدخل يُنسخ ([copyTo]) أو يُتخطّى
     * ([skipEntry])، فأرشيف ٢ جيجا لا يطلب ٢ جيجا من RAM.
     */
    class Reader(private val input: InputStream) {

        /** اسم GNU طويل مقروء ينتظر مدخله — يُستهلك مرّة واحدة. */
        private var pendingLongName: String? = null

        /**
         * بايتات المدخل الحالي (محتواه وحشوه) التي لم يتقدّم القارئ عنها بعد.
         *
         * **ولماذا يُتتبّع:** من قرأ مدخلًا ثم نادى [next] بلا [copyTo] ولا [skipEntry] كان
         * يقرأ محتوى المدخل السابق على أنه ترويسة، فيُرفض الأرشيف بـ[TarFailure.BadHeader]
         * وهو سليم. وراصدها هنا يجعل [next] آمنةً بنفسها (القارئ يتخطّى ما بقي)، فلا يعتمد
         * العقد على انتباه المستدعي.
         */
        private var unconsumed = 0L

        /** المدخل التالي، أو `null` عند كتلة النهاية أو نهاية الدفق. */
        fun next(): TarEntry? {
            // ما بقي من المدخل السابق يُتخطّى أوّلًا، فلا يُقرأ محتواه ترويسةً.
            skip(unconsumed)
            unconsumed = 0L
            while (true) {
                val header = readBlock() ?: return null
                if (header.all { it.toInt() == 0 }) return null
                validate(header)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val local = readString(header, 0, NAME_LEN)
                val prefix = readString(header, 345, PREFIX_LEN)
                val full = if (prefix.isEmpty()) local else "$prefix/$local"
                if (type == TYPE_GNU_LONG_NAME) {
                    pendingLongName = readLongName(size)
                    continue
                }
                if (type == 'x' || type == 'g') {
                    skip(size + padding(size))
                    continue
                }
                val name = pendingLongName?.also { pendingLongName = null } ?: full
                val entry = TarEntry(
                    name = name,
                    isDirectory = type == TYPE_DIR || full.endsWith("/"),
                    size = if (type == TYPE_DIR) 0L else size,
                    mode = readOctal(header, 100, 8).toInt(),
                    modifiedEpochSec = readOctal(header, 136, 12),
                )
                // كل ما يلي هذه الترويسة يجب أن يتقدّم عنه قبل الترويسة التالية — ويُقاس
                // من الحجم المُعلَن في الترويسة لا من الحجم المُبلَّغ (مجلد أعلن حجمًا).
                unconsumed = size + padding(size)
                return entry
            }
        }

        /**
         * نسخ محتوى المدخل — والحشو يتركه [next] يتخطّاه، فلا يُحسب مرّتين.
         */
        fun copyTo(entry: TarEntry, output: OutputStream): Long {
            var copied = 0L
            if (!entry.isDirectory && entry.size > 0L) {
                val buffer = ByteArray(CHUNK)
                var remaining = entry.size
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) throw EOFException("tar: entry truncated at $copied/${entry.size}")
                    output.write(buffer, 0, read)
                    remaining -= read
                    copied += read
                }
            }
            // ما نُسخ من المحتوى صار مُستهلكًا؛ ويبقى الحشو لـ[next].
            unconsumed = (unconsumed - copied).coerceAtLeast(0L)
            return copied
        }

        /** يتخطّى محتوى مدخل مع حشوه (للتخطّي بلا كتابة). */
        fun skipEntry(entry: TarEntry) {
            skip(unconsumed)
            unconsumed = 0L
        }

        fun skip(size: Long) {
            var remaining = size
            if (remaining <= 0L) return
            val buffer = ByteArray(CHUNK)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) throw EOFException("tar: truncated while skipping $remaining bytes")
                remaining -= read
            }
        }

        private fun readBlock(): ByteArray? {
            val block = ByteArray(BLOCK)
            var read = 0
            while (read < BLOCK) {
                val step = input.read(block, read, BLOCK - read)
                if (step <= 0) {
                    if (read == 0) return null
                    throw EOFException("tar: torn header")
                }
                read += step
            }
            return block
        }

        private fun readLongName(size: Long): String {
            val bytes = ByteArray(size.toInt())
            var read = 0
            while (read < bytes.size) {
                val step = input.read(bytes, read, bytes.size - read)
                if (step <= 0) throw EOFException("tar: truncated long name")
                read += step
            }
            skip(padding(size))
            return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
        }

        private fun validate(header: ByteArray) {
            val magic = readString(header, 257, 6)
            if (magic.isNotEmpty() && !magic.startsWith("ustar")) {
                throw TarException(TarFailure.BadHeader, readString(header, 0, 12))
            }
            val stored = readOctalSafely(header, 148, 8) ?: return
            var sum = 0
            for (index in header.indices) {
                sum += if (index in 148 until 156) ' '.code else header[index].toInt() and 0xFF
            }
            if (sum.toLong() != stored) {
                throw TarException(TarFailure.BadHeader, "checksum $stored != $sum")
            }
        }

        private fun readOctal(block: ByteArray, offset: Int, length: Int): Long =
            readOctalSafely(block, offset, length) ?: 0L

        private fun readOctalSafely(block: ByteArray, offset: Int, length: Int): Long? {
            val text = readString(block, offset, length)
            if (text.isEmpty()) return 0L
            return try {
                java.lang.Long.parseLong(text, 8)
            } catch (_: NumberFormatException) {
                null
            }
        }

        private fun readString(block: ByteArray, offset: Int, length: Int): String {
            var end = offset
            val limit = minOf(offset + length, block.size)
            while (end < limit && block[end].toInt() != 0) end++
            return String(block, offset, end - offset, Charsets.UTF_8).trim()
        }
    }

    /** حشو الكتلة الأخيرة: الصيغة تُحاذي كل مدخل إلى ٥١٢. */
    internal fun padding(size: Long): Long = if (size % BLOCK == 0L) 0L else BLOCK - (size % BLOCK)
}
