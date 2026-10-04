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
 * `MT-FM/ب` — محرّك **zip** داخل التطبيق: ضغط وفكّ بلا أداة خارجية.
 *
 * ولماذا zip بعد أن كان `tar.gz` يعمل: `tar.gz` صيغة أنظمة، أما ما يتناقله الناس على
 * هواتفهم فهو zip — وهو ما يفعله MT. وإضافة أداة `zip` خارجية كانت ستربط الميزة بوجود
 * busybox على الجهاز، فنُفِّذت بالمكتبة المعيارية (`java.util.zip`) التي لا تفشل لغياب أداة.
 *
 * وثلاث قواعد تحكم هذا الملف:
 *
 * 1. **حماية من `zip-slip` قبل أي كتابة.**: أرشيف فيه `../../data/x` هو هجوم كلاسيكي على
 *    فاكّات الأرشيف، ولذلك يُفحص الأرشيف **كاملًا قبل** أن تُكتب أول بايت. وإن وُجد مدخل
 *    غير آمن يُرفض الأرشيف كلّه بسببه المعلَن — لا يُفكّ نصفه.
 * 2. **التقدّم مقيس أو مُعلَن.**: المجموع يُجمع من أحجام الملفات قبل البدء، وما لا يُقاس
 *    يُبلَّغ عنه بمجموع غير معروف بدل نسبة مخترعة (ADR-07).
 * 3. **لا نجاح بلا إثبات.**: بعد الضغط يُتحقّق من ملف الأرشيف، وبعد الفكّ يُتحقّق من وجود
 *    كل مدخل على القرص؛ وما لم يُتحقّق يُعلن «نُفِّذ ولم يُتحقّق» لا «نجح».
 *
 * ولا shell هنا إطلاقًا: هذا المحرّك يعمل على ما تراه عملية التطبيق. ومسار لا يقرأه
 * التطبيق يُرفض بـ[ArchiveFailure.UnreadableSource] **معلنًا**، وله في الشاشة إعلان.
 */
package nd.max.ui.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import nd.max.core.jni.ArchiveBridge
import nd.max.core.jni.ArchivePacket

/** لماذا فشل الضغط/الفكّ. الأسباب مختلفة، فلا تُدمج في «فشل». */
enum class ArchiveFailure {
    NoSources,

    /** ملف لا تراه عملية التطبيق (مسار جذري مثلًا) — يُعلن لا يُتجاوز بصمت. */
    UnreadableSource,

    /** مدخل غير آمن في الأرشيف (مسار مطلق أو `..`) — رفض قبل الكتابة. */
    UnsafeEntry,

    /** الأرشيف تالف أو ليس zip. */
    CorruptArchive,

    /** فشل كتابة على القرص (مساحة أو صلاحية). */
    WriteFailed,
}

/**
 * مستوى الضغط كما يُختار من الواجهة — ولكلٍّ رقم `Deflater` يقابله.
 *
 * **والأرقام مُعلَنة لا مُتخيَّلة:** `NO_COMPRESSION` = ٠ (يُخزّن بلا ضغط)، و`BEST_SPEED` = ١،
 * و`DEFAULT_COMPRESSION` = ‎-١ (ما يراه `zlib` توازنًا)، و`BEST_COMPRESSION` = ٩. وهي نفسها
 * تُمرَّر إلى مستوى `Deflater` في مسار `tar.gz`، فلا مستويان مختلفان للصيغتين.
 *
 * وهذا النوع **لا يعرف `R`** (قاعدة المستودع: النماذج لا تعرف الموارد) — التسمية في الواجهة.
 */
enum class CompressionLevel(val deflater: Int) {
    /** بلا ضغط: أسرع ما يمكن، وأكبر حجمًا — مفيد لمحتوى مضغوط أصلًا (‏apk · jpg). */
    Store(Deflater.NO_COMPRESSION),
    Fast(Deflater.BEST_SPEED),
    Normal(Deflater.DEFAULT_COMPRESSION),
    Maximum(Deflater.BEST_COMPRESSION),
}

/** الصيغة التي يُنتجها الضغط — والاختيار قرار مستخدم لا ثابت في الكود. */
enum class ArchiveFormat {
    /** `zip` — الصيغة التي يتناقلها الناس، وبها مسار أصلي (Rust) بلا تقدّم. */
    Zip,

    /** `tar.gz` — صيغة الأنظمة، تُكتب هنا بـ[FileTarCodec] و`GZIP`. */
    TarGz,
}

/** امتداد الأرشيف لكل صيغة — يُكتب في مكان واحد فيتفق الاسم والمحرّك. */
val ArchiveFormat.extension: String
    get() = when (this) {
        ArchiveFormat.Zip -> ".zip"
        ArchiveFormat.TarGz -> ".tar.gz"
    }

/**
 * نتيجة عملية أرشيف: **ثلاثة أحوال** لا اثنان — نُفِّذ وتُحقّق ≠ نُفِّذ ولم يُتحقّق ≠ فشل.
 */
data class ArchiveOutcome(
    val executed: Boolean,
    val verified: Boolean,
    val failure: ArchiveFailure? = null,
    val subject: String? = null,
) {
    val ok: Boolean get() = executed && verified

    fun toFileOpOutcome(): FileOpOutcome = FileOpOutcome(executed, verified)

    companion object {
        fun verified(): ArchiveOutcome = ArchiveOutcome(executed = true, verified = true)

        fun executedOnly(): ArchiveOutcome = ArchiveOutcome(executed = true, verified = false)

        fun failed(reason: ArchiveFailure, subject: String? = null): ArchiveOutcome =
            ArchiveOutcome(executed = false, verified = false, failure = reason, subject = subject)
    }
}

object FileArchiveEngine {

    /** حجم القطعة المقروءة/المكتوبة — وكل قطعة تحدّث التقدّم، فلا يتجمّد الشريط. */
    private const val CHUNK = 64 * 1024

    /**
     * «لا تقدّم مطلوب» — **كائن واحد** يُقارن بالمرجع لا بالمحتوى.
     *
     * ولماذا المقارنة بالمرجع: المسار الأصلي (Rust) يكتب الأرشيف في نداء واحد، فلا يُبلّغ
     * تقدّمًا لكل قطعة. ومن طلب تقدّمًا حقيقيًّا أخذ مسار Kotlin بنفس دلالته السابقة —
     * فلا صمت في الشريط، ولا تقدّم مُختلق (ADR-07).
     *
     * **ومقيس في الإنتاج:** المستدعي الوحيد `FileSystemEngine.compress` لا يمرّر تقدّمًا ⇒
     * المسار الأصلي هو ما يستعمله المستخدم اليوم. (وطلبات التقدّم الحقيقية في الاختبارات
     * وحدها حتى الآن.)
     */
    val NO_PROGRESS: (done: Long, total: Long) -> Unit = { _, _ -> }

    // ────────────────────────────────────────────────────────────────────────
    // الضغط
    // ────────────────────────────────────────────────────────────────────────

    /**
     * ضغط مصادر (ملفات ومجلدات) في أرشيف zip.
     *
     * والمداخل **نسبية إلى أب كل مصدر** وتبدأ باسمه، كما يفعل `tar -C`: فكّ الأرشيف على
     * جهاز آخر يُنتج مجلدًا باسمه لا شجرة مسارات مطلقة في جذر لا يملكه المستخدم.
     */
    fun createZip(
        sources: List<String>,
        archivePath: String,
        level: CompressionLevel = CompressionLevel.Normal,
        progress: (done: Long, total: Long) -> Unit = NO_PROGRESS,
    ): ArchiveOutcome {
        if (sources.isEmpty()) return ArchiveOutcome.failed(ArchiveFailure.NoSources)

        val archive = File(FileBrowser.normalize(archivePath))

        // الطبقة الأولى: القارئ/الكاتب الأصلي — ويشترط أنه لا تقدّم دقيق مطلوب (الشرح
        // في [NO_PROGRESS]) **وأن يكون المستوى الافتراضي**: مسار Rust لا يحمل مستوى ضغط،
        // فتمريره إليه كان سيُنفّذ بخلاف ما اختاره المستخدم بلا أن يقول أحد. و`null`
        // تعني «اسأل غيري» فتستمرّ الدالّة إلى التنفيذ المرجعي أدناه بنفس الدلالات.
        if (progress === NO_PROGRESS && level == CompressionLevel.Normal) {
            ArchiveBridge.createZip(sources, archive.path)?.let { return it.toOutcome(archive) }
        }
        // والتمشية في مكان واحد ([prepareEntries]) تستعمله الصيغتان: نسختان منها كانتا
        // ستفترقان في أوّل إصلاح يُمَسّ إحداهما.
        val prepared = prepareEntries(sources, archive) ?: return ArchiveOutcome.failed(ArchiveFailure.NoSources)
        if (prepared is Prepared.Failed) return ArchiveOutcome.failed(prepared.reason, prepared.subject)
        val entries = (prepared as Prepared.Ready).entries
        val total = entries.filterNot { it.second.endsWith("/") }.sumOf { it.first.length() }

        archive.parentFile?.let { if (!it.exists()) it.mkdirs() }
        var done = 0L
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { zip ->
                // المستوى المختار قبل أول مدخل: `setLevel` بعد `putNextEntry` بلا أثر.
                zip.setLevel(level.deflater)
                for ((file, entryName) in entries) {
                    zip.putNextEntry(ZipEntry(entryName))
                    // مجلد فارغ: يُكتب كمدخل بشرطة أخيرة، وإلا ضاع عند الفكّ.
                    if (entryName.endsWith("/")) {
                        zip.closeEntry()
                        continue
                    }
                    BufferedInputStream(FileInputStream(file)).use { input ->
                        val buffer = ByteArray(CHUNK)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            zip.write(buffer, 0, read)
                            done += read
                            progress(done, total)
                        }
                    }
                    zip.closeEntry()
                }
                zip.finish()
            }
            if (archive.exists() && archive.length() > 0L) {
                ArchiveOutcome.verified()
            } else {
                ArchiveOutcome.executedOnly()
            }
        } catch (io: IOException) {
            // أرشيف نصفه مكتوب أسوأ من لا أرشيف: يُزال فلا يظنّ المستخدم أنه يملك نسخة.
            archive.delete()
            ArchiveOutcome.failed(ArchiveFailure.WriteFailed, io.message)
        }
    }

    /**
     * ضغط مصادر في `tar.gz`: نفس تمشية [createZip] ونفس قواعدها، بترميز [FileTarCodec]
     * وضغط `GZIP` على المستوى المختار.
     *
     * **ولماذا وُجد:** اسم الصيغة كان قرارًا ضمنيًّا من امتداد الهدف، فيمرّ `tar.gz` بالصدفة
     * (ويحتاج جذرًا وثنائية `tar`) أو لا يمرّ أصلًا في مسار التطبيق. والآن الصيغتان خيارٌ
     * صريح للمستخدم، ومستوى الضغط يُمرَّر إلى `GZIP` نفسه ([CompressionLevel]).
     *
     * ولا مسار أصليّ هنا: `ArchiveBridge` يعرف `zip` وحده، فلا يُسأل عن `tar`.
     */
    fun createTarGz(
        sources: List<String>,
        archivePath: String,
        level: CompressionLevel = CompressionLevel.Normal,
        progress: (done: Long, total: Long) -> Unit = NO_PROGRESS,
    ): ArchiveOutcome {
        if (sources.isEmpty()) return ArchiveOutcome.failed(ArchiveFailure.NoSources)
        val archive = File(FileBrowser.normalize(archivePath))
        val prepared = prepareEntries(sources, archive) ?: return ArchiveOutcome.failed(
            ArchiveFailure.NoSources,
        )
        if (prepared is Prepared.Failed) return ArchiveOutcome.failed(prepared.reason, prepared.subject)
        val entries = (prepared as Prepared.Ready).entries
        val total = entries.filterNot { it.second.endsWith("/") }.sumOf { it.first.length() }

        archive.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return try {
            LeveledGzipStream(BufferedOutputStream(FileOutputStream(archive)), level.deflater).use { gzip ->
                FileTarCodec.write(entries, gzip, progress, total)
            }
            if (archive.exists() && archive.length() > 0L) ArchiveOutcome.verified() else ArchiveOutcome.executedOnly()
        } catch (io: IOException) {
            // أرشيف نصفه مكتوب أسوأ من لا أرشيف: يُزال فلا يظنّ المستخدم أنه يملك نسخة.
            archive.delete()
            ArchiveOutcome.failed(ArchiveFailure.WriteFailed, io.message)
        }
    }

    /**
     * ضغط بصيغة مختارة من الواجهة — نقطة واحدة تفصل القرار عن التنفيذ.
     */
    fun create(
        sources: List<String>,
        archivePath: String,
        format: ArchiveFormat,
        level: CompressionLevel = CompressionLevel.Normal,
        progress: (done: Long, total: Long) -> Unit = NO_PROGRESS,
    ): ArchiveOutcome = when (format) {
        ArchiveFormat.Zip -> createZip(sources, archivePath, level, progress)
        ArchiveFormat.TarGz -> createTarGz(sources, archivePath, level, progress)
    }

    /** مداخل جاهزة، أو سبب رفض معلَن — تُقرأ مرّة فلا تُعيد التمشية الدالّتان. */
    private sealed interface Prepared {
        data class Ready(val entries: List<Pair<File, String>>) : Prepared
        data class Failed(val reason: ArchiveFailure, val subject: String?) : Prepared
    }

    /**
     * تمشية المصادر إلى مداخل (ملف، اسم) — نفس القواعد في الصيغتين: المداخل نسبية
     * إلى أب كل مصدر وتبدأ باسمه، والأرشيف الناتج مستثنى، ومصدر لا يُقرأ **يُرفض قبل الكتابة**.
     */
    private fun prepareEntries(sources: List<String>, archive: File): Prepared? {
        val entries = ArrayList<Pair<File, String>>()
        try {
            for (raw in sources) {
                val source = File(FileBrowser.normalize(raw))
                if (!source.exists() || !(source.canRead() || source.isDirectory)) {
                    return Prepared.Failed(ArchiveFailure.UnreadableSource, source.path)
                }
                val rootName = FileBrowser.nameOf(source.path)
                if (source.isFile) {
                    entries += source to rootName
                    continue
                }
                entries += walkedEntries(source, source.parentFile, rootName, archive)
            }
        } catch (io: IOException) {
            return Prepared.Failed(ArchiveFailure.UnreadableSource, io.message)
        }
        if (entries.isEmpty()) return null
        return Prepared.Ready(entries)
    }

    /**
     * تمشية مجلد إلى مداخل zip، بترتيب أبجدي ثابت (فأرشيفان لنفس المجلد يتفقان).
     *
     * والمداخل تُبنى **نسبية إلى المجلد الأب** وتبدأ باسم المجلد نفسه، والمجلدات الفارغة
     * تُكتب بشرطة أخيرة حتى لا تضيع، والأرشيف الناتج نفسه يُستثنى فلا يُضغط داخل نفسه.
     *
     * والسقف [MAX_WALK_DEPTH] يحمي من دورة روابط رمزية؛ وما هو أعمق منه يُهمَل — وحدّ
     * معلن في التوثيق لا مخفيّ في الكود.
     */
    private fun walkedEntries(
        root: File,
        parent: File?,
        rootName: String,
        archive: File,
        depth: Int = 0,
    ): List<Pair<File, String>> {
        if (depth > MAX_WALK_DEPTH) return emptyList()
        val out = ArrayList<Pair<File, String>>()
        val children = root.listFiles() ?: return out
        for (child in children.sortedBy { it.name }) {
            if (sameFile(child, archive)) continue
            val relative = relativeToParent(child, parent)
            val entryName = if (relative.startsWith("$rootName/") || relative == rootName) {
                relative
            } else {
                "$rootName/$relative"
            }
            if (!child.isDirectory) {
                if (!child.canRead()) throw IOException("unreadable: ${child.path}")
                out += child to entryName
                continue
            }
            val nested = walkedEntries(child, parent, rootName, archive, depth + 1)
            if (nested.isEmpty()) {
                // مجلد فارغ: مدخل بشرطة أخيرة — وإلا اختفى من الأرشيف.
                out += child to "$entryName/"
            } else {
                out += nested
            }
        }
        return out
    }

    private fun relativeToParent(child: File, parent: File?): String {
        if (parent == null) return child.name
        val parentPath = parent.path.trimEnd('/')
        return if (child.path.startsWith("$parentPath/")) child.path.removePrefix("$parentPath/") else child.name
    }

    private fun sameFile(left: File, right: File): Boolean = runCatching {
        left.canonicalPath == right.canonicalPath
    }.getOrDefault(false)

    /** سقف عمق التمشية — يُعلن ولا يُخفى (الشجرة الأعمق تُهمَل بلا إنكار). */
    const val MAX_WALK_DEPTH: Int = 40

    // ────────────────────────────────────────────────────────────────────────
    // الفكّ
    // ────────────────────────────────────────────────────────────────────────

    /**
     * فكّ zip إلى مجلد.
     *
     * والفحص **قبل** الكتابة: أرشيف فيه مدخل غير آمن يُرفض كاملًا، فلا يبقى على القرص
     * نصف فكٍّ يظنّ المستخدم أنه كامل.
     */
    fun extractZip(
        archivePath: String,
        destination: String,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): ArchiveOutcome {
        val archive = File(FileBrowser.normalize(archivePath))
        if (!archive.exists() || !archive.canRead()) {
            return ArchiveOutcome.failed(ArchiveFailure.UnreadableSource, archive.path)
        }
        val dir = File(FileBrowser.normalize(destination))

        val entries: List<ZipEntry> = try {
            ZipFile(archive).use { zip -> Collections.list(zip.entries()) }
        } catch (bad: ZipException) {
            return ArchiveOutcome.failed(ArchiveFailure.CorruptArchive, bad.message)
        } catch (io: IOException) {
            return ArchiveOutcome.failed(ArchiveFailure.UnreadableSource, io.message)
        }

        // الفحص الأمني أولًا: كل مدخل قبل أول بايت تُكتب.
        entries.firstOrNull { isUnsafeEntry(it.name) }?.let { unsafe ->
            return ArchiveOutcome.failed(ArchiveFailure.UnsafeEntry, unsafe.name)
        }

        if (!dir.exists() && !dir.mkdirs()) {
            return ArchiveOutcome.failed(ArchiveFailure.WriteFailed, dir.path)
        }

        val total = entries.filterNot { it.isDirectory }.sumOf { if (it.size > 0) it.size else 0L }
        var done = 0L

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(dir, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(CHUNK)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            done += read
                            progress(done, total)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (io: IOException) {
            return ArchiveOutcome.failed(ArchiveFailure.WriteFailed, io.message)
        }

        // الإثبات: كل مدخل على القرص بعد الفكّ.
        val verified = entries.all { entry ->
            val target = File(dir, entry.name)
            if (entry.isDirectory) target.isDirectory else target.exists()
        }
        return if (verified) ArchiveOutcome.verified() else ArchiveOutcome.executedOnly()
    }

    /**
     * فكّ **بأي صيغة مدعومة** — zip أو tar أو tar.gz — من نقطة واحدة.
     *
     * **ولماذا وُجدت:** كان قرار الصيغة موزَّعًا على مستدعِينَ: `extractZip` لـ`zip`، و`tar`
     * عبر `PrivilegedShell` لما عداها (انظر [FileSystemEngine])؛ فمن نادى المحرّك مباشرةً على
     * `.tar.gz` أخذ `CorruptArchive`. فصار القرار **من الاسم، مرة واحدة، هنا** — والصيغ الأربع
     * المُعلنة في `FileArchive.SUPPORTED` صار لها مسار داخلي فعليّ.
     */
    fun extractArchive(
        archivePath: String,
        destination: String,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): ArchiveOutcome {
        val archive = File(FileBrowser.normalize(archivePath))
        val name = archive.name.lowercase()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTar(archive, destination, gzipped = true, progress)
            name.endsWith(".tar") -> extractTar(archive, destination, gzipped = false, progress)
            else -> extractZip(archive.path, destination, progress)
        }
    }

    /**
     * فكّ `tar`/`tar.gz` بدفق واحد لا بحفظ الأرشيف في الذاكرة.
     *
     * **والأمان قبل الكتابة لكل مدخل:** لا يمكن فحص أرشيف متتابع كاملًا قبل أول بايت (ذلك
     * يقرؤه مرّتين)، فالفحص هنا **قبل كتابة كل مدخل** — وإن وُجد مدخل غير آمن يُمحى ما كتبناه
     * في هذه الدعوة وحدها ويُعلن السبب، فلا يُترك على القرص نصف فكّ يُظنّ أنه كامل.
     *
     * **وحدّ معلن:** مجموع البايتات مجهول في تدقيق واحد (الترويسة تحمل حجم كل مدخل، وقراءة
     * الأرشيف مرّتين لمعرفة المجموع ثمنٌ لا مقابل له)، فهذا المسار يُبلّغ `total = 0` — وهو ما
     * تُترجمه الواجهة إلى شريط غير محدَّد لا إلى نسبة مُخترعة (ADR-07).
     */
    private fun extractTar(
        archive: File,
        destination: String,
        gzipped: Boolean,
        progress: (done: Long, total: Long) -> Unit,
    ): ArchiveOutcome {
        if (!archive.exists() || !archive.canRead()) {
            return ArchiveOutcome.failed(ArchiveFailure.UnreadableSource, archive.path)
        }
        val dir = File(FileBrowser.normalize(destination))
        val written = ArrayList<Pair<File, Long>>()
        var done = 0L

        try {
            val raw = BufferedInputStream(FileInputStream(archive))
            val stream = if (gzipped) GZIPInputStream(raw) else raw
            stream.use { input ->
                val reader = FileTarCodec.Reader(input)
                while (true) {
                    val entry = reader.next() ?: break
                    if (isUnsafeEntry(entry.name)) {
                        removeWritten(written)
                        return ArchiveOutcome.failed(ArchiveFailure.UnsafeEntry, entry.name)
                    }
                    val target = File(dir, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    val copied = BufferedOutputStream(FileOutputStream(target)).use { output ->
                        reader.copyTo(entry, output)
                    }
                    written += target to entry.size
                    done += copied
                    progress(done, 0L)
                }
            }
        } catch (tar: TarException) {
            removeWritten(written)
            return ArchiveOutcome.failed(ArchiveFailure.CorruptArchive, tar.message)
        } catch (io: IOException) {
            // دفق مبتور أو `.gz` تالف: الأرشيف نقص عن إعلانه — لا يُقال «نُفّذ» وهو ناقص.
            removeWritten(written)
            return ArchiveOutcome.failed(ArchiveFailure.CorruptArchive, io.message)
        }

        // الإثبات: كل ما كتبناه موجود وبالحجم المُعلَن في ترويسته.
        val verified = written.all { (file, size) -> file.isFile && file.length() == size }
        return if (verified && written.isNotEmpty()) ArchiveOutcome.verified() else ArchiveOutcome.executedOnly()
    }

    /** محو ما كتبناه هذه الدعوة — بالحذف لا بالخيال. */
    private fun removeWritten(written: List<Pair<File, Long>>) {
        written.forEach { (file, _) -> runCatching { file.delete() } }
    }

    /**
     * هل مدخل الأرشيف غير آمن؟
     *
     * الفحص على **المقاطع** لا على النصّ: اسم فيه `..` داخل مقطع (`a..b`) مشروع، أما
     * مقطع `..` كامل فهو صعود عن المجلد ويُرفض. ويُرفض كذلك المسار المطلق — أرشيف كتبه
     * ويندوز بشرطة مائلة عكسية أو بقرص `C:` يُعدّ مستهدفًا لمسار آخر.
     */
    fun isUnsafeEntry(name: String): Boolean {
        val cleaned = name.replace('\\', '/')
        if (cleaned.isBlank()) return true
        if (cleaned.startsWith("/")) return true
        // قرص ويندوز (`C:`) يوجّه الكتابة إلى مكان آخر. والنقطتان العاديتان في اسم
        // مثل `12:30.mp3` مشروعتان، فلا يُرفض أي اسم فيه نقطتان — بل القرص وحده.
        if (DRIVE_LETTER.containsMatchIn(cleaned)) return true
        return cleaned.split('/').any { it == ".." }
    }

    private val DRIVE_LETTER = Regex("^[A-Za-z]:")
}

/**
 * تحويل ردّ المسار الأصلي إلى حكم الأرشيف — **بنفس حكم Kotlin حرفيًّا**:
 * الأرشيف موجود وطوله > ٠ ⇒ «نُفِّذ وتُحقّق»، وإلا «نُفِّذ ولم يُتحقّق» (لا نجاح بلا إثبات).
 *
 * والأسباب الرمزية تُترجم إلى [ArchiveFailure] المنفصلة كما هي، **وما لا نعرفه يُعلن**
 * `WriteFailed` ولا يُبتلع: سبب غامض لا يصير «نجاحًا» ولا «لا مصادر».
 *
 * ودالّة مستقلّة لا عضوًا في [FileArchiveEngine]: دالّة الإرشاد في عضو تحتاج مستقبلًا
 * موزَّعًا في سياق النداء، فيصير قياسها في اختبار الوحدة أطول من الحكم نفسه.
 */
/**
 * مجرى `gzip` بمستوى ضغط مُختار.
 *
 * **ولماذا صنف لا نداء:** `GZIPOutputStream` لا يعرض مستوى الضغط — `setLevel` في
 * `DeflaterOutputStream` **محميّ** (‏protected)، بخلاف `ZipOutputStream.setLevel` العلني في
 * هذا المسار. فالطريق بلا انعكاس هو صنف صغير يضبط مستوى `Deflater` من داخل بنائه؛
 * وبلا هذا كان اختيار المستخدم «بلا ضغط» يُنتج أرشيفًا مضغوطًا بلا أن يقول أحد (ADR-07).
 */
private class LeveledGzipStream(output: OutputStream, level: Int) : GZIPOutputStream(output) {
    init {
        def.setLevel(level)
    }
}

internal fun ArchivePacket.Result.toOutcome(archive: File): ArchiveOutcome = when (this) {
    is ArchivePacket.Result.Ok ->
        if (archive.exists() && archive.length() > 0L) ArchiveOutcome.verified()
        else ArchiveOutcome.executedOnly()

    is ArchivePacket.Result.Failed -> ArchiveOutcome.failed(
        when (reason) {
            "no_sources" -> ArchiveFailure.NoSources
            "unreadable" -> ArchiveFailure.UnreadableSource
            else -> ArchiveFailure.WriteFailed
        },
        subject,
    )
}
