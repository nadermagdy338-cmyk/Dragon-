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
 * نموذج مدير الملفات — **خالص وقابل للاختبار بلا جهاز ولا shell**.
 *
 * كل قرار في هذه الشاشة يُتّخذ هنا: كيف يُقرأ سطر `stat`، وكيف يُرتَّب، وكيف يُنقّى،
 * وكيف يُحرس أمر خطير. وطبقة التنفيذ ([FileSystemEngine]) لا تقرّر شيئًا — تُنفّذ
 * ما قرّره هذا الملف. والفصل مقصود: الحرس الذي يمنع «نسخ مجلد داخل نفسه» يجب أن
 * يُختبر بلا جهاز، لأن خطأه يُفقد بيانات لا تُستعاد.
 *
 * وهذا الملف **لا يعتمد على Compose ولا على أندرويد**، فيُشغَّل في اختبار JVM عادي.
 */
package nd.max.ui.util

import java.util.Locale

/** نوع المدخل كما **يعلنه `stat`** — لا كما نخمّنه من امتداد الاسم أو لونه. */
enum class FileKind {
    Directory,
    RegularFile,
    Symlink,
    BlockDevice,
    CharDevice,
    Fifo,
    Socket,

    /** نوع أعلنه الجهاز ولم نعرفه. يُعرض كما هو ولا يُسقَط — إسقاطه يجعل ملفًّا «مختفيًا». */
    Unknown,
    ;

    companion object {
        /**
         * ترجمة كلمة `%F`. المصادر: coreutils وtoybox — والصيغتان مذكورتان لأن
         * الأسماء تختلف بينهما (مثال: `regular empty file` عند coreutils).
         */
        fun fromStatWord(word: String): FileKind = when (word.trim().lowercase(Locale.ROOT)) {
            "directory" -> Directory
            "regular file", "regular empty file" -> RegularFile
            "symbolic link", "link" -> Symlink
            "block special file", "block device" -> BlockDevice
            "character special file", "character device" -> CharDevice
            "fifo", "named pipe" -> Fifo
            "socket" -> Socket
            else -> Unknown
        }
    }
}

/**
 * الصلاحيات كما أعلنها الجهاز: `octal` للقراءة الآلية و`symbolic` للعرض.
 *
 * والبتّات الخاصة تُشتقّ من الرقم لا من الرموز، لأن `s`/`t` في الصيغة الرمزية تُكتب
 * بأحرف كبيرة أو صغيرة حسب حالة بت التنفيذ — أي أن الحرف وحده ليس دليلًا.
 */
data class FilePermissions(val octal: String, val symbolic: String) {
    private val special: Int
        get() = if (octal.length == 4) octal.first().digitToIntOrNull() ?: 0 else 0

    val setUid: Boolean get() = special and 4 != 0
    val setGid: Boolean get() = special and 2 != 0
    val sticky: Boolean get() = special and 1 != 0

    /** هل الصلاحيات "شاذّة" تستحقّ أن تُبرز في الواجهة؟ */
    val isUnusual: Boolean get() = setUid || setGid || sticky
}

/**
 * مدخل واحد في نظام الملفات.
 *
 * والحقول التي قد لا نعرفها `nullable` **عن قصد**: `null` تعني «لم يُقرأ»، والصفر
 * يعني «قُرئ وكان صفرًا». الخلط بينهما هو ما يجعل مدراء الملفات يعرضون «0 B» لمجلد
 * لا يعرفون حجمه، ويجعل المستخدم يظنّ أن الأداة كذبت عليه لا أنها عجزت.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val kind: FileKind,
    val sizeBytes: Long? = null,
    val modifiedEpochSec: Long? = null,
    val permissions: FilePermissions? = null,
    val owner: String? = null,
    val group: String? = null,
    val symlinkTarget: String? = null,
) {
    val isDirectory: Boolean get() = kind == FileKind.Directory
    val isSymlink: Boolean get() = kind == FileKind.Symlink
    val hasFullAttributes: Boolean get() = sizeBytes != null && modifiedEpochSec != null
}

/** نتيجة قراءة مجلد: إما مدخلات، أو **سبب معلن** لعدم القدرة على القراءة. */
sealed interface DirectoryListing {
    val path: String

    /**
     * @param attributesAvailable `false` يعني أن الأسماء وحدها وصلت (سقط `stat`) —
     *        فالأحجام والصلاحيات **لم تُقرأ** لا أنها فارغة.
     */
    data class Entries(
        override val path: String,
        val entries: List<FileEntry>,
        val skippedLines: Int = 0,
        val attributesAvailable: Boolean = true,
    ) : DirectoryListing

    data class Unreadable(override val path: String, val reason: ListingFailure) : DirectoryListing
}

/** لماذا فشلت القراءة. الخمسة مختلفة تمامًا، فلا تُدمج في «فشل». */
enum class ListingFailure { NotADirectory, PermissionDenied, NotFound, ShellUnavailable }

/** قطعة في مسار التنقّل. `label` ما يُعرض، و`path` أين تؤدّي. */
data class Crumb(val label: String, val path: String)

/** مفتاح الترتيب. */
enum class FileSortKey { Name, Size, Modified, Kind }

data class FileSort(
    val key: FileSortKey = FileSortKey.Name,
    val ascending: Boolean = true,
    val directoriesFirst: Boolean = true,
)

/**
 * عدد ما في المجلد: مجلدات · ملفات · مخفيّ.
 *
 * والثالث ليس زينة: مدير الملفات الذي يُخفي ما يبدأ بنقطة **يجب أن يقول كم أخفى**،
 * وإلا قرأ المستخدم «٤ ملفات» على مجلد فيه ٧ — وهذا هو النوع نفسه من الكذب الذي
 * يمنعه `status_unknown` في التلمترى (ADR-07)، مطبَّقًا على قائمة ملفات.
 */
data class EntryCounts(val folders: Int, val files: Int, val hidden: Int) {
    val total: Int get() = folders + files
}

/**
 * مساحة نظام ملفات قُرئت فعلًا: المجموع والمتاح.
 *
 * ولا وجود لـ«صفر» هنا: من لم يقرأ يُعيد `null` من القارئ، لأن «المساحة صفر» تعني
 * قرصًا ممتلئًا وهي أسوأ رسالة ممكنة لم تكن صحيحة (ADR-23).
 */
data class DiskSpace(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

/**
 * كل قواعد التنقّل والترتيب والتصفية — خالصة.
 *
 * الترتيب الطبيعي (natural) مقصود: `file2` قبل `file10`. الترتيب الأبجدي الصرف
 * يضع `file10` قبل `file2`، وهو من أكثر ما يُشتكى منه في مدراء الملفات.
 */
object FileBrowser {

    /** المسار بصيغة واحدة: بلا شرطة أخيرة، و`/` وحده يبقى `/`. */
    fun normalize(path: String): String {
        val collapsed = path.trim().replace(Regex("/+"), "/")
        if (collapsed.isEmpty()) return "/"
        return if (collapsed.length > 1) collapsed.trimEnd('/') else collapsed
    }

    fun childPath(directory: String, name: String): String {
        val base = normalize(directory)
        return if (base == "/") "/$name" else "$base/$name"
    }

    fun nameOf(path: String): String {
        val normalized = normalize(path)
        if (normalized == "/") return "/"
        return normalized.substringAfterLast('/')
    }

    /** المجلد الأب، أو `null` إن كنا في الجذر (فلا صعود). */
    fun parentOf(path: String): String? {
        val normalized = normalize(path)
        if (normalized == "/") return null
        val parent = normalized.substringBeforeLast('/', "")
        return if (parent.isEmpty()) "/" else parent
    }

    /** عناصر المسار من الجذر إليه، لتُبنى فتات الخبز. */
    fun breadcrumbs(path: String): List<Crumb> {
        val normalized = normalize(path)
        val crumbs = mutableListOf(Crumb("/", "/"))
        if (normalized == "/") return crumbs
        val segments = normalized.removePrefix("/").split('/')
        val builder = StringBuilder()
        for (segment in segments) {
            if (segment.isEmpty()) continue
            builder.append('/').append(segment)
            crumbs += Crumb(segment, builder.toString())
        }
        return crumbs
    }

    /**
     * هل `child` داخل `ancestor` (أو يساويه)؟
     *
     * تُستعمل لمنع «نسخ مجلد داخل نفسه»، وهو أمر يملأ القرص بلا نهاية في بعض
     * الأدوات. المقارنة على الحدود لا على البادئة النصّية، وإلا عُدّ `/data2`
     * داخلًا في `/data`.
     */
    fun isInside(child: String, ancestor: String): Boolean {
        val c = normalize(child)
        val a = normalize(ancestor)
        if (a == "/") return true
        return c == a || c.startsWith("$a/")
    }

    /** ترتيب مدخلات مجلد وفق [sort]. */
    fun sort(entries: List<FileEntry>, sort: FileSort): List<FileEntry> {
        val comparator = Comparator<FileEntry> { left, right ->
            if (sort.directoriesFirst) {
                val byKind = if (left.isDirectory == right.isDirectory) 0 else if (left.isDirectory) -1 else 1
                if (byKind != 0) return@Comparator byKind
            }
            when (sort.key) {
                FileSortKey.Name -> naturalCompare(left.name, right.name)
                FileSortKey.Size -> compareValues(left.sizeBytes ?: -1L, right.sizeBytes ?: -1L)
                FileSortKey.Modified -> compareValues(left.modifiedEpochSec ?: -1L, right.modifiedEpochSec ?: -1L)
                FileSortKey.Kind -> compareValues(left.kind.ordinal, right.kind.ordinal)
            }.let { primary ->
                // التعادل يُحسم بالاسم دائمًا، وإلا تغيّر ترتيب الملفات المتساوية بين رسمين
                // فتتحرّك الصفوف تحت إصبع المستخدم بلا سبب.
                if (primary != 0) primary else naturalCompare(left.name, right.name)
            }
        }
        val ordered = entries.sortedWith(comparator)
        return if (sort.ascending) ordered else ordered.reversed()
    }

    /** تصفية بالاسم، بلا حساسية لحالة الأحرف. البحث في الاسم لا في المسار الكامل. */
    fun filter(entries: List<FileEntry>, query: String): List<FileEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        return entries.filter { it.name.contains(needle, ignoreCase = true) }
    }

    /**
     * هل الاسم مخفيّ؟
     *
     * عرف يونكس وحده: نقطة في أول الاسم. **ولا يُستنتج من الصلاحيات** — الحكم على
     * الصلاحيات كان سيُخفي ملفًا مقروءًا من المستخدم العادي في لوح يعمل بالجذر، وهو
     * قرار لا يملكه هذا النموذج.
     */
    fun isHidden(entry: FileEntry): Boolean = entry.name.startsWith(".")

    /** ما يُعرض حين تكون الملفات المخفية مخفيّة. */
    fun withoutHidden(entries: List<FileEntry>): List<FileEntry> = entries.filterNot(::isHidden)

    /** عدّ المجلدات والملفات — عدّ **ما قُرئ** لا ما يُتوقَّع. */
    fun counts(entries: List<FileEntry>): EntryCounts = EntryCounts(
        folders = entries.count { it.isDirectory },
        files = entries.count { !it.isDirectory },
        hidden = entries.count(::isHidden),
    )

    /**
     * مقارنة طبيعية: تقارن الأرقام كأرقام والحروف كحروف.
     *
     * `a2` قبل `a10`، و`a` قبل `a1`. وهي ليست `String.compareTo`، لأن تلك تضع
     * `a10` قبل `a2` — عطب يراه المستخدم فورًا في أي مجلد فيه ترقيم.
     */
    fun naturalCompare(left: String, right: String): Int {
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            val li = left[i]
            val rj = right[j]
            if (li.isDigit() && rj.isDigit()) {
                val startI = i
                val startJ = j
                while (i < left.length && left[i].isDigit()) i++
                while (j < right.length && right[j].isDigit()) j++
                val numI = left.substring(startI, i).trimStart('0')
                val numJ = right.substring(startJ, j).trimStart('0')
                if (numI.length != numJ.length) return numI.length - numJ.length
                val cmp = numI.compareTo(numJ)
                if (cmp != 0) return cmp
            } else {
                val cmp = li.lowercaseChar().compareTo(rj.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (left.length - i) - (right.length - j)
    }
}

/** تحليل مخرج `stat` — الجزء الوحيد الذي يفهم تنسيقًا خارجيًّا. */
object FileStatParser {

    /**
     * التنسيق المُعلن، **بترتيب يحمي الاسم**: اسم الملف يُوضع **أخيرًا** ويُقرأ بحدّ
     * عدد الحقول، فينجو اسم فيه فاصلة أو `\t`، لأن كل ما بعد الحقل الثامن يبقى اسمًا.
     */
    const val FORMAT: String = "%F\t%a\t%A\t%U\t%G\t%s\t%Y\t%l\t%n"

    private const val FIELDS = 9

    /** المدخلات المقروءة، وعدد الأسطر التي لم نفهمها (يُعرض ولا يُسكَت عنه). */
    data class Parsed(val entries: List<FileEntry>, val skipped: Int)

    /**
     * @param parent المجلد الذي قُرئ، لبناء المسار الكامل. مخرج `stat` يعطي المسار
     *        الذي طلبناه، لكن بناءه من (الأب + الاسم) أبسط وأقل عرضة لاختلاف صيغ.
     */
    fun parse(lines: List<String>, parent: String): Parsed {
        val entries = ArrayList<FileEntry>(lines.size)
        var skipped = 0
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split('\t', limit = FIELDS)
            if (parts.size < FIELDS) {
                skipped++
                continue
            }
            val name = parts[8].trim().substringAfterLast('/')
            if (name.isEmpty()) {
                skipped++
                continue
            }
            entries += FileEntry(
                name = name,
                path = FileBrowser.childPath(parent, name),
                kind = FileKind.fromStatWord(parts[0]),
                sizeBytes = parts[5].trim().toLongOrNull(),
                modifiedEpochSec = parts[6].trim().toLongOrNull(),
                permissions = FilePermissions(
                    octal = parts[1].trim(),
                    symbolic = parts[2].trim(),
                ),
                owner = parts[3].trim().ifEmpty { null },
                group = parts[4].trim().ifEmpty { null },
                symlinkTarget = parts[7].trim().ifEmpty { null },
            )
        }
        return Parsed(entries, skipped)
    }

    /** بناء مدخلات من **الأسماء وحدها** حين يسقط `stat` — بلا صفات مُختلَقة. */
    fun degraded(names: List<String>, parent: String): List<FileEntry> = names
        .map(String::trim)
        .filter { it.isNotEmpty() && it != "." && it != ".." }
        .map { name ->
            FileEntry(
                name = name,
                path = FileBrowser.childPath(parent, name),
                // النوع مجهول فعلًا: لا نخمّنه من الاسم، فنُعلنه `Unknown`.
                kind = FileKind.Unknown,
                sizeBytes = null,
                modifiedEpochSec = null,
            )
        }
}

/**
 * ذاكرة مجلدات بسياسة الأقدم-استعمالًا-يُخرج.
 *
 * وهي **مصدر السلاسة الوحيد** في هذه الشاشة: قراءة مجلد تمرّ بـshell جذر، وزمنها ليس تحت
 * سيطرتنا. فالمجلد الذي زرناه يُعرض من الذاكرة في الإطار نفسه، ثم تُقرأ النسخة الطازجة في
 * الخلفية. والبديل — الانتظار عند كل رجوع — هو ما يجعل الأدوات المماثلة تبدو ثقيلة.
 *
 * والسياسة خالصة فمكانها هنا: تُختبر بلا جهاز ولا shell.
 */
class DirectoryCache(private val capacity: Int = 16) {

    private val entries = object : LinkedHashMap<String, DirectoryListing>(capacity, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, DirectoryListing>?,
        ): Boolean = size > capacity
    }

    val size: Int get() = entries.size

    fun get(path: String): DirectoryListing? = entries[FileBrowser.normalize(path)]

    fun put(listing: DirectoryListing) {
        entries[FileBrowser.normalize(listing.path)] = listing
    }

    /**
     * إبطال ما بعد عملية ناجحة.
     *
     * ويُبطَل **الكل** لا المتأثّر وحده عن قصد: العملية قد تمسّ مسارات في أكثر من مجلد
     * (نقل عبر شجرة)، وإبقاء مجلد لم يتحقّق منه أحد يُنتج قائمة تُكذّب ما حدث للتوّ.
     */
    fun invalidateAll() = entries.clear()
}

/** الاختيار المتعدّد — كائن ثابت، فالتحوّل الإجرائي لا يُخفي من اختار ماذا. */
data class FileSelection(val paths: Set<String> = emptySet()) {
    val count: Int get() = paths.size
    val isEmpty: Boolean get() = paths.isEmpty()
    val isNotEmpty: Boolean get() = paths.isNotEmpty()

    fun toggle(path: String): FileSelection =
        FileSelection(if (path in paths) paths - path else paths + path)

    fun clear(): FileSelection = FileSelection(emptySet())

    fun selectAll(entries: List<FileEntry>): FileSelection = FileSelection(entries.map { it.path }.toSet())

    /** عكس الاختيار — الإجراء الذي يجعل «حدّد الكل ثم استثنِ واحدًا» ممكنًا. */
    fun invert(entries: List<FileEntry>): FileSelection =
        FileSelection(entries.map { it.path }.filterNot { it in paths }.toSet())
}

/**
 * العمليات المعلنة. والعضوان الجديدان (`CreateFile` و`Change*`) أُضيفا مع واجهة MT:
 * إنشاء ملف فارغ، وتغيير صلاحيات/مالك من نافذة الخصائص — وكلاهما يمرّ بالحرس نفسه.
 */
enum class FileOperation {
    Copy,
    Move,
    Delete,
    Rename,
    CreateDirectory,
    CreateFile,
    Compress,
    Extract,
    ChangePermissions,
    ChangeOwner,

    /**
     * كتابة نصّ في ملف — «حفظ» في المحرّر الداخلي.
     *
     * والعملية معلنة هنا لا مُخفاة في واجهة: الحفظ يمرّ بالحرس نفسه الذي تمرّ به كل
     * عملية، ولذلك يُرفض على الجذر ويُرفض بلا محتوى، ولا يُكتب بطرف ثالث لا يُقاس.
     */
    WriteText,
}

data class FileOpRequest(
    val operation: FileOperation,
    val sources: List<String> = emptyList(),
    val destination: String? = null,
    val newName: String? = null,
    /** نصّ الحفظ — يُقرأ في [FileOperation.WriteText] وحده. */
    val content: String? = null,
    /**
     * حلّ تعارض الأسماء: لكل مصدر (بمساره المطبَّع) الاسم الذي يُكتب في الوجهة.
     *
     * وموضعه الطلب لا ملفَّ حافظات في الشاشة: القرار قرار العملية ذاتها، والمنفّذ يقرأه
     * فيكتب العنصر باسمه الجديد — ويُثبت الوجود على الاسم الجديد لا على القديم.
     */
    val renamed: Map<String, String> = emptyMap(),
)

sealed interface FileOpVerdict {
    data object Allowed : FileOpVerdict
    data class Refused(val reason: FileOpRefusal) : FileOpVerdict
}

enum class FileOpRefusal {
    EmptySelection,
    ProtectedPath,
    SelfTarget,
    TargetInsideSource,
    InvalidName,
    NameTaken,
}

/**
 * حرس العمليات — **قبل** أن يصل أي أمر إلى shell.
 *
 * وهذا هو الفرق بين مدير ملفات وورطة: أغلى الأخطاء هنا **صامتة ومدمّرة** — نسخ مجلد
 * داخل نفسه يملأ القرص، ونقل مجلد فوق نفسه يمحو الشجرة، وحذف `/` يمحو الجهاز. ولا
 * يكشفها اختبار على جهاز إلا بعد وقوعها، فمكانها نموذج خالص يُختبر دائمًا.
 */
object FileOpGuard {

    /**
     * المسارات التي **لا تُحذف ولا تُنقل ولا تُستهدف**. `/` وحده: حذف الجذر ليس
     * عملية، والمسارات تحته مسؤولية المستخدم الصريحة (هذه أداة جذر، وليست حاضنة).
     */
    private val PROTECTED = setOf("/")

    fun check(
        request: FileOpRequest,
        existingNames: Set<String> = emptySet(),
        directories: Set<String> = emptySet(),
    ): FileOpVerdict {
        val sources = request.sources.map(FileBrowser::normalize).filter { it.isNotEmpty() }

        if (request.operation == FileOperation.CreateDirectory || request.operation == FileOperation.CreateFile) {
            val name = request.newName?.trim().orEmpty()
            return when {
                !isValidName(name) -> FileOpVerdict.Refused(FileOpRefusal.InvalidName)
                name in existingNames -> FileOpVerdict.Refused(FileOpRefusal.NameTaken)
                else -> FileOpVerdict.Allowed
            }
        }

        if (sources.isEmpty()) return FileOpVerdict.Refused(FileOpRefusal.EmptySelection)

        when (request.operation) {
            FileOperation.Delete, FileOperation.Move -> {
                if (sources.any { it in PROTECTED }) return FileOpVerdict.Refused(FileOpRefusal.ProtectedPath)
            }
            FileOperation.Rename -> {
                val name = request.newName?.trim().orEmpty()
                if (!isValidName(name)) return FileOpVerdict.Refused(FileOpRefusal.InvalidName)
                if (name in existingNames) return FileOpVerdict.Refused(FileOpRefusal.NameTaken)
                if (sources.any { it in PROTECTED }) return FileOpVerdict.Refused(FileOpRefusal.ProtectedPath)
            }
            FileOperation.ChangePermissions -> {
                // الرقم يُتحقّق **قبل** أن يصل إلى shell: رقم فيه `8` أو حروف كان يُفسَّر
                // شيء آخر عند التنفيذ، ولا يمكن التراجع عنه بعد أن وقع.
                val octal = request.newName?.trim().orEmpty()
                if (!FilePermissionRules.isValidOctal(octal)) {
                    return FileOpVerdict.Refused(FileOpRefusal.InvalidName)
                }
                if (sources.any { it in PROTECTED }) return FileOpVerdict.Refused(FileOpRefusal.ProtectedPath)
            }
            FileOperation.ChangeOwner -> {
                val spec = request.newName?.trim().orEmpty()
                val owner = spec.substringBefore(':')
                val group = spec.substringAfter(':', "")
                if (FilePermissionRules.ownerSpec(owner, group) == null) {
                    return FileOpVerdict.Refused(FileOpRefusal.InvalidName)
                }
                if (sources.any { it in PROTECTED }) return FileOpVerdict.Refused(FileOpRefusal.ProtectedPath)
            }
            FileOperation.WriteText -> {
                val target = sources.singleOrNull()
                    ?: return FileOpVerdict.Refused(FileOpRefusal.EmptySelection)
                if (target in PROTECTED) return FileOpVerdict.Refused(FileOpRefusal.ProtectedPath)
                if (request.content == null) return FileOpVerdict.Refused(FileOpRefusal.EmptySelection)
            }
            FileOperation.Copy, FileOperation.Compress, FileOperation.Extract -> Unit
        }

        val destination = request.destination?.let(FileBrowser::normalize)
        if (request.operation == FileOperation.Copy || request.operation == FileOperation.Move) {
            if (destination.isNullOrEmpty()) return FileOpVerdict.Refused(FileOpRefusal.EmptySelection)
            if (destination in PROTECTED) return FileOpVerdict.Refused(FileOpRefusal.ProtectedPath)
            for (source in sources) {
                // النقل إلى نفس المكان ليس نقلًا، والنسخ إلى نفس المكان يرمي خطأ عند الجهاز.
                if (source == destination) return FileOpVerdict.Refused(FileOpRefusal.SelfTarget)
                // النسخ/النقل **داخل** مجلد هو نفسه يخلق شجرة لا تنتهي.
                if (source in directories && FileBrowser.isInside(destination, source)) {
                    return FileOpVerdict.Refused(FileOpRefusal.TargetInsideSource)
                }
            }
        }
        return FileOpVerdict.Allowed
    }

    /**
     * اسم صالح: لا فاصل مسار، ولا نقطة/نقطتان، ولا فارغ.
     *
     * ورفض `/` في الاسم ليس تجميلًا: اسم فيه فاصل يحوّل `mkdir a/b` إلى إنشاء مجلدين،
     * ويحوّل `mv x a/b` إلى كتابة في مجلد آخر — أي أن الاسم يصبح مسارًا لم يقصده أحد.
     */
    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\u0000')

    /** اسم جديد غير متعارض، بلاحقة ` (n)` — لإعادة التسمية التلقائية عند التعارض. */
    fun uniqueName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var index = 1
        while ("$base ($index)" in taken) index++
        return "$base ($index)"
    }
}

/**
 * إلى أين يذهب العنصر في الوجهة، وبأيّ اسم.
 *
 * ولماذا دالّة منفصلة: حلّ تعارض الأسماء قرار يُتّخذ في نموذج ([FileConflictRules])
 * ويُنفَّذ في المحرّك، وكان يمكن أن يُنسى في أحدهما فينسخ المحرّك باسم قديم بعد أن
 * وعدت الشاشة باسم جديد. فالحساب هنا مرة واحدة، ويُقاس في JVM.
 */
object FileTargets {

    /** اسم العنصر في الوجهة: الاسم الجديد إن حُلّ تعارضه، وإلا اسمه هو. */
    fun nameFor(source: String, renamed: Map<String, String>): String {
        val normalized = FileBrowser.normalize(source)
        val fresh = renamed[normalized]
        if (fresh.isNullOrBlank()) return FileBrowser.nameOf(normalized)
        return fresh
    }

    fun destinationFor(
        source: String,
        destinationDir: String,
        renamed: Map<String, String> = emptyMap(),
    ): String = FileBrowser.childPath(FileBrowser.normalize(destinationDir), nameFor(source, renamed))
}

/** تنسيق الحجم والزمن — في مكان واحد كي لا يختلف بين صفّ ولوحة تفاصيل. */
object FileFormat {

    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

    /**
     * حجم مقروء. و`null` تُعيد `null` **لا صفرًا**: «لم أقرأ الحجم» ليست «الحجم صفر».
     */
    fun size(bytes: Long?): String? {
        if (bytes == null) return null
        if (bytes < 0) return null
        if (bytes < 1024) return "$bytes ${UNITS[0]}"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < UNITS.lastIndex) {
            value /= 1024
            unit++
        }
        val rendered = if (value >= 100) String.format(Locale.US, "%.0f", value)
        else String.format(Locale.US, "%.1f", value)
        return "$rendered ${UNITS[unit]}"
    }

    /**
     * تاريخ التعديل بصيغة مضغوطة ثابتة (`MM/dd/yy HH:mm`).
     *
     * والنمط **ثابت** لا مشتقّ من لغة الجهاز عن قصد: شاشة مدير الملفات كلها LTR بقرار
     * المالك، وأعمدة التاريخ فيها ثابتة العرض، فتاريخ يُعاد تشكيله حسب اللغة يُنتج عرضًا
     * يتغيّر طوله فينهار العمود. و`null` تبقى `null` (لم يُقرأ) ولا تصير تاريخ اليوم.
     */
    fun date(epochSec: Long?): String? {
        if (epochSec == null || epochSec <= 0L) return null
        val formatter = java.text.SimpleDateFormat("MM/dd/yy HH:mm", Locale.US)
        return formatter.format(java.util.Date(epochSec * 1000L))
    }

    /** الصلاحيات الرمزية إن وُجدت، وإلا الرقم الثماني. */
    fun permissions(permissions: FilePermissions?): String? = when {
        permissions == null -> null
        permissions.symbolic.isNotBlank() -> permissions.symbolic
        permissions.octal.isNotBlank() -> permissions.octal
        else -> null
    }
}
