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
 * `OCR-02` — **مجموعات المجلدات**: مجلدات يختارها المستخدم بنفسه فيُنسخ ما فيها.
 *
 * **ولماذا فئة جديدة:** ما عندنا اليوم هو نسخ **تطبيقات** (`MaxBackupEngine`) ونسخ **أصناف
 * نظام ثابتة** (`MaxBackupSystem`: واي فاي · جهات · مكالمات · رسائل…). وكلاهما «ما نعرفه
 * نحن». أما «الصور في `DCIM` التي أنا جمعتها» و«مشروعي في `Documents`» فليسا صنفًا نعرفه،
 * بل مجموعة يسمّيها المستخدم. والفرق ليس تجميليًّا: مجموعة المجلدات **قد تُنسخ في الخلفية**
 * (`OCR-01`) ويمكن أن تنمو إلى غيغابايتات — فقواعدها (ما يُرفض، وما يتداخل، وما يُسمّى) لا
 * بدّ أن تكون في مكان يُقاس، لا في شيفرة واجهة تُقرأ.
 *
 * **ويُخزَّن نصًّا لا JSON.** سبب اختيار النصّ لهذا الملف بعينه: هذه المجموعات **تُقرأ في
 * تشغيل مجدول** أي في مسار لا يشاهده أحد، وملفٌّ لا يُعرَب لا يُنتج استثناءً في مهمة تعمل
 * وحدها. والقراءة متسامحة: سطر لا يُفهم **يُتخطّى** والبقية تُقرأ، ويُبلَّغ عن العدد.
 *
 * وحدّه المعلَن: اسم أو مسار يحمل **تبويبًا أو سطرًا جديدًا** يُرفض، لأن الفاصل في الملف
 * نفسه هوهما — ولا يُشفَّر الفاصل بأي حيلة، لأن الملف يجب أن يبقى مقروءًا للإنسان.
 */
package nd.max.ui.util

object MaxBackupFolders {

    /** اسم ملف التخزين، في مجلد التطبيق الخاص: سياسة يملكها المستخدم لا تسافر مع نسخة. */
    const val FILE_NAME = "folder-sets.txt"

    const val MAX_NAME = 40
    const val MAX_PATHS = 32

    /** مجموعة: اسم يعرضه المستخدم، ومسارات مفصولة. */
    data class FolderSet(
        val name: String,
        val paths: List<String>,
        val addedAtMs: Long = 0L,
    )

    /**
     * المسارات التي **لا تُنسخ** أبدًا: الجذر، وأنظمة ملفات وهمية لا محتوى حقيقي فيها.
     *
     * ورفض `/` ليس تشدّدًا: ضغط الجذر في مهمة مجدولة يملأ التخزين ثم يملأ نفسه، وهو أسوأ
     * عطب يمكن أن تصنعه ميزة «نسخ احتياطي». و`/proc` و`/sys` و`/dev` تُقرأ منها بلا نهاية
     * وتُنتج `tar` لا معنى له.
     */
    private val PSEUDO = setOf("/", "/proc", "/sys", "/dev", "/acct", "/config", "/d", "/system_dlkm")

    private const val SEPARATOR = '\t'

    /**
     * اسم صالح لمجموعة: غير فارغ، بلا فاصل الملف، بلا فاصل مسار، وبحدّ طول معقول.
     * فاصل المسار مرفوض لأن الاسم يُعرض في مسار النسخة، واسم يحمل `/` يجعل منه مسارًا لم يقصده أحد.
     */
    fun sanitizeName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME) return null
        if (trimmed == "." || trimmed == "..") return null
        if (trimmed.any { it == SEPARATOR || it == '\n' || it == '\r' || it == '/' || it == '\u0000' }) return null
        return trimmed
    }

    /** اسم غير متعارض، بلاحقة ` (n)` — بنفس منطق `FileOpGuard.uniqueName` فلا يتعلّم المستخدم شكلين. */
    fun uniqueName(base: String, taken: Collection<String>): String {
        if (base !in taken) return base
        var index = 1
        while ("$base ($index)" in taken) index++
        return "$base ($index)"
    }

    /**
     * مسار يصلح للنسخ: غير فارغ، بلا فاصل الملف، وليس نظامًا وهميًّا **ولا شيئًا تحته**.
     *
     * وحدّ الرفض على أول قطعة من المسار لا على مطابقة المسار كاملًا — وهذا فرق مقيس:
     * مطابقة `/sys` وحدها تقبل `/sys/kernel`، وهي شجرة لا تنتهي قراءتها، وضغطها في مهمة
     * مجدولة يقرأ إلى ما لا نهاية أو يمتلئ بالتخزين. ونفس الأمر في كل ما تحت `/proc` و`/dev`.
     *
     * والوجود **لا** يُتحقَّق هنا: هذا قرار خالص، والحكم على الوجود يُعلنه المحرّك عند التنفيذ.
     */
    fun isBackupable(rawPath: String): Boolean {
        if (rawPath.isBlank()) return false
        if (rawPath.any { it == SEPARATOR || it == '\n' || it == '\r' || it == '\u0000' }) return false
        val path = FileBrowser.normalize(rawPath)
        if (path == "/") return false
        return PSEUDO.none { root -> root != "/" && (path == root || path.startsWith("$root/")) }
    }

    /** يُنقّي المسارات: يُبقي الصالح، يحذف المكرّر، ويرتّب — فلا يختلف الملف باختلاف ترتيب الإدخال. */
    fun sanitizePaths(raw: List<String>): List<String> = raw
        .map { FileBrowser.normalize(it) }
        .filter { isBackupable(it) }
        .distinct()
        .sorted()

    /**
     * إنشاء مجموعة. `null` = الاسم غير صالح أو لا مسار صالح فيه — ولا تُنشأ مجموعة فارغة
     * تُوعد بنسخة لا تحتوي شيئًا.
     */
    fun create(
        name: String,
        paths: List<String>,
        addedAtMs: Long,
        taken: Collection<String> = emptyList(),
    ): FolderSet? {
        val clean = sanitizeName(name) ?: return null
        val usable = sanitizePaths(paths)
        if (usable.isEmpty()) return null
        return FolderSet(name = uniqueName(clean, taken), paths = usable, addedAtMs = addedAtMs)
    }

    /** إضافة مسار إلى مجموعة موجودة. `null` = المسار مرفوض أو موجود أصلًا أو بلغت الحدّ. */
    fun addPath(set: FolderSet, rawPath: String): FolderSet? {
        if (set.paths.size >= MAX_PATHS) return null
        val path = FileBrowser.normalize(rawPath)
        if (!isBackupable(path) || path in set.paths) return null
        return set.copy(paths = (set.paths + path).sorted())
    }

    fun removePath(set: FolderSet, rawPath: String): FolderSet =
        set.copy(paths = set.paths.filterNot { it == FileBrowser.normalize(rawPath) })

    /** إعادة تسمية بمحلّ الاسم نفسه: تُستخدم في الواجهة، وتحفظ التفرّد. */
    fun rename(set: FolderSet, rawName: String, taken: Collection<String>): FolderSet? {
        val clean = sanitizeName(rawName) ?: return null
        val others = taken.filterNot { it == set.name }
        return set.copy(name = uniqueName(clean, others))
    }

    /**
     * هل تتقاطع مجموعتان؟ أي مجموعة تحتوي مجلدًا **داخل** مجلد في الأخرى.
     *
     * ولا يُرفض التقاطع تلقائيًّا: نسخ المجلد نفسه في مجموعتين **مقصود** أحيانًا (نسخة
     * «صور» ونسخة «كل ما يهمّ»). لكنه يُعلَن، لأن أثره مضاعفة الحجم في مهمة مجدولة.
     */
    fun overlaps(left: FolderSet, right: FolderSet): Boolean =
        left.paths.any { a ->
            right.paths.any { b -> a == b || FileBrowser.isInside(a, b) || FileBrowser.isInside(b, a) }
        }

    /** الحجم التقديري للمجموعة بعد التقاطع مع أخرى — يُعرض تحذيرًا لا يُمنع. */
    fun sharedPaths(left: FolderSet, right: FolderSet): List<String> =
        left.paths.filter { a ->
            right.paths.any { b -> a == b || FileBrowser.isInside(a, b) || FileBrowser.isInside(b, a) }
        }

    /** اسم يُعرض للمستخدم من مسار: آخر قطعة، أو المسار نفسه إن كان جذرًا. */
    fun labelOf(path: String): String {
        val normalized = FileBrowser.normalize(path)
        return FileBrowser.nameOf(normalized).ifBlank { normalized }
    }

    // ────────────────────────────────────────────────────────────────────────
    // التخزين النصّي
    // ────────────────────────────────────────────────────────────────────────

    /** `الاسم<TAB>زمن الإضافة<TAB>مسار<TAB>مسار` لكل مجموعة، سطر لكل واحدة. */
    fun encode(sets: List<FolderSet>): String = buildString {
        sets.forEach { set ->
            if (sanitizeName(set.name) == null) return@forEach
            append(set.name)
            append(SEPARATOR)
            append(set.addedAtMs)
            set.paths.filter { isBackupable(it) }.forEach { path ->
                append(SEPARATOR)
                append(FileBrowser.normalize(path))
            }
            append('\n')
        }
    }

    /**
     * قراءة متسامحة: سطر مشوّه يُتخطّى، ومجموعة بلا مسارات صالحة تُسقَط.
     * والمكرّر بالاسم يُعاد تسميته بدل أن يُحذف — فقدان إعداد المستخدم أسوأ من اسم بلا لاحقة.
     */
    fun decode(text: String): List<FolderSet> {
        val result = mutableListOf<FolderSet>()
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val cells = line.split(SEPARATOR)
            if (cells.size < 2) return@forEach
            val name = sanitizeName(cells[0]) ?: return@forEach
            val addedAt = cells[1].toLongOrNull() ?: 0L
            val paths = sanitizePaths(cells.drop(2))
            if (paths.isEmpty()) return@forEach
            result += FolderSet(
                name = uniqueName(name, result.map { it.name }),
                paths = paths,
                addedAtMs = addedAt,
            )
        }
        return result
    }
}
