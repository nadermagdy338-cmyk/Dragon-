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
/*
 * محرّك التنظيف الفائق: **قياس ثم حذف ثم قياس**.
 *
 * وثلاث قواعد كُتبت قبل أول سطر، وكلها من عقد `MAX-MANAGER-LEVEL-UP.md` §6.3 و§11:
 *
 * 1. **لا مسار حرّ من الواجهة.** كل مسار هنا **قالب ثابت** في هذا الملفّ (`CACHE_DIRS`,
 *    `THUMB_DIRS`, `LOG_DIRS`) — لا يأتي مسار من الشاشة ولا من المستخدم أصلًا، فلا يوجد ما
 *    يُقتبس خطأً. والحذف يمرّ بـ`FileSystemEngine.delete` (نفس ما يستخدمه مدير الملفات)،
 *    وهو يقتبس كل مسار ويقرأ بعده (`test -e`) — فالتحقّق ليس وعدًا في تعليق.
 * 2. **لا حذف لملفّ مستخدم بلا تأشير.** ولذلك `InstallerFiles` و`EmptyFolders` مطفأتان
 *    افتراضيًّا في النموذج، **وهنا** لا يوجد لهما استثناء: تُحذف فقط إن وصلتا في قائمة الفئات
 *    المختارة — والقرار في النموذج لا في المحرّك.
 * 3. **`null` ليست صفرًا.** فشل التنفيذ يعني `bytes = null`، وتُعرض «لم يُقس»؛ ونجاحٌ بمخرج
 *    فارغ يعني `0`، وتُعرض «٠» (فليس في المسار شيء أصلًا).
 *
 * **وحدوده المعلنة:**
 * - تكرار المجلدات عبر روابط رمزية لا يُتبع (`File.listFiles` لا يتبع symlink للمجلدات)، فلا
 *   يدخل المسح في حلقة.
 * - الملفات المخفية **تُحذف** من مجلدات الكاش كما تُحذف غيرها — بخلاف صدفة `*` التي تتجاهلها.
 *   فذلك هو المقصود: أندرويد يخزّن أكثر كاشه في مجلدات مخفية، وإغفالها كان سيترك الميزة تعمل
 *   نصف عمل.
 * - عمق المسح محدود ([WALK_DEPTH_CAP]) ومعه سقف عناصر ([WALK_ENTRY_CAP]): شجرة تخزين كاملة
 *   على جهاز ممتلئ تحتاج دقائق، وشاشة تجمّد لتُنتج بايتات أدقّ ليست صفقة رابحة — وعند بلوغ
 *   السقف يُعلن القياس **ناقصًا** لا كاملًا.
 */
package nd.max.ui.util

import android.content.Context
import android.os.Environment
import java.io.File

/** نتيجة حذف فئة: هل نُفِّذ، وهل تحقّق، وما المساحة التي أُزيلت فعلًا. */
data class CleanOutcome(val executed: Boolean, val verified: Boolean, val freedBytes: Long)

object UltraCleanEngine {

    /** سقف عمق المسح من الجذر — يكفي لشجرة التنزيلات والتخزين المشترك ولا يبلغ قرارات النظام. */
    private const val WALK_DEPTH_CAP = 6

    /** سقف عدد العناصر في القياس الواحد. بلوغه يُعلن نقصًا لا كمالًا. */
    private const val WALK_ENTRY_CAP = 40_000

    /**
     * أمر نظام التنظيف لأكياس التطبيقات — **لا `rm` على `/data/data`**.
     *
     * و`pm trim-caches` هو ما يطلبه النظام نفسه لخفض الكاش، فيمرّ على كل تطبيق عبر آليته
     * المعلنة بدل أن نمرّ عليه بمسار guessable. والحجم `999G` معناه «أفرغ ما تستطيع».
     */
    private const val TRIM_CACHES = "pm trim-caches 999G"

    /** مجلدات الكاش الداخلي لكل تطبيق (تحتاج جذرًا للقراءة). */
    private const val INTERNAL_CACHE_GLOB = "/data/data/*/cache"

    /** آثار الانهيار وصندوق النظام (تحتاج جذرًا). */
    private val LOG_DIRS = listOf("/data/anr", "/data/tombstones", "/data/system/dropbox")

    /** مجلدات الصور المصغّرة الثابتة على التخزين المشترك. */
    private fun thumbDirs(external: File): List<File> = listOf(
        File(external, "DCIM/.thumbnails"),
        File(external, "Pictures/.thumbnails"),
        File(external, "Movies/.thumbnails"),
        File(external, "Download/.thumbnails"),
        File(external, ".thumbnails"),
    )

    /**
     * يقيس فئة واحدة. لا يرمي: الفشل يعود `bytes = null`.
     *
     * @param rootAvailable هل الجذر متاح؟ يحدّد المسار: الأمر عبر الصدفة أو القراءة المباشرة.
     */
    fun measure(
        context: Context,
        category: UltraCleanCategory,
        rootAvailable: Boolean,
    ): CleanMeasurement = when (category) {
        UltraCleanCategory.AppCaches ->
            measureViaShell(category, INTERNAL_CACHE_GLOB)
                ?: CleanMeasurement(category, null, 0)

        UltraCleanCategory.SharedCaches -> {
            val external = Environment.getExternalStorageDirectory()
            measureViaShell(category, File(external, "Android/data/*/cache").path)
                ?: CleanMeasurement(category, null, 0)
        }

        UltraCleanCategory.Thumbnails -> {
            val dirs = thumbDirs(Environment.getExternalStorageDirectory())
            val present = dirs.filter { runCatching { it.isDirectory }.getOrDefault(false) }
            if (present.isEmpty()) {
                CleanMeasurement(category, 0L, 0)
            } else if (!rootAvailable && present.none { runCatching { it.canRead() }.getOrDefault(false) }) {
                // موجودة ولا تُقرأ: «لم يُقس» لا «صفر».
                CleanMeasurement(category, null, present.size)
            } else {
                walkBytes(category, present)
            }
        }

        UltraCleanCategory.InstallerFiles -> {
            val downloads = File(Environment.getExternalStorageDirectory(), "Download")
            val found = collectFiles(downloads, rootAvailable) { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return CleanMeasurement(category, null, 0)
            CleanMeasurement(category, found.sumOf { it.second }, found.size)
        }

        UltraCleanCategory.EmptyFolders -> {
            val roots = listOf(Environment.getExternalStorageDirectory())
            val empty = collectEmptyFolders(roots) ?: return CleanMeasurement(category, null, 0)
            // الحجم صفر بالتعريف؛ والعدّ هو الخبر، فلا يُكتب «0 B».
            CleanMeasurement(category, 0L, empty.size)
        }

        UltraCleanCategory.SystemLogs -> {
            val missing = LOG_DIRS.none { path -> runCatching { File(path).isDirectory }.getOrDefault(false) }
            if (missing) {
                CleanMeasurement(category, 0L, 0)
            } else {
                measureViaShell(category, *LOG_DIRS.toTypedArray())
                    ?: CleanMeasurement(category, null, 0)
            }
        }
    }

    /**
     * يحذف فئة واحدة ويعيد ما أُزيل فعلًا.
     *
     * والمساحة المُزالة **تُقاس قبل وبعد** لا تُقدَّر: المقارنة بين [before] وما يُقاس بعد
     * الحذف — والفرق السالب يُكلَّم إلى صفر (تطبيق عاد فحجز مكانه) فلا يُعلن تحرير لم يقع.
     */
    fun clean(
        context: Context,
        category: UltraCleanCategory,
        before: CleanMeasurement?,
        rootAvailable: Boolean,
    ): CleanOutcome {
        val executed: Boolean
        val verified: Boolean

        when (category) {
            UltraCleanCategory.AppCaches -> {
                // نظامي لا `rm`: النظام يخفض كاش كل تطبيق بآليته.
                executed = PrivilegedShell.run(TRIM_CACHES) != null
                verified = executed
            }

            UltraCleanCategory.SharedCaches -> {
                val outcome = deleteMatching(
                    glob = File(Environment.getExternalStorageDirectory(), "Android/data/*/cache").path + "/*"
                )
                executed = outcome.first
                verified = outcome.second
            }

            UltraCleanCategory.Thumbnails -> {
                val dirs = thumbDirs(Environment.getExternalStorageDirectory())
                    .filter { runCatching { it.isDirectory }.getOrDefault(false) }
                val outcome = FileSystemEngine.delete(dirs.map { it.path })
                executed = outcome.executed
                verified = outcome.verified
            }

            UltraCleanCategory.InstallerFiles -> {
                val downloads = File(Environment.getExternalStorageDirectory(), "Download")
                val apks = collectFiles(downloads, rootAvailable) { it.name.endsWith(".apk", ignoreCase = true) }
                    ?.map { it.first } ?: emptyList()
                if (apks.isEmpty()) {
                    return CleanOutcome(executed = true, verified = true, freedBytes = 0L)
                }
                val outcome = FileSystemEngine.delete(apks)
                executed = outcome.executed
                verified = outcome.verified
            }

            UltraCleanCategory.EmptyFolders -> {
                val empty = collectEmptyFolders(listOf(Environment.getExternalStorageDirectory()))
                    ?.map { it.path } ?: emptyList()
                if (empty.isEmpty()) {
                    return CleanOutcome(executed = true, verified = true, freedBytes = 0L)
                }
                val outcome = FileSystemEngine.delete(empty)
                executed = outcome.executed
                verified = outcome.verified
            }

            UltraCleanCategory.SystemLogs -> {
                val targets = LOG_DIRS.map { "$it/*" }
                var anyExecuted = true
                var allVerified = true
                for (glob in targets) {
                    val outcome = deleteMatching(glob)
                    anyExecuted = anyExecuted && outcome.first
                    allVerified = allVerified && outcome.second
                }
                executed = anyExecuted
                verified = allVerified
            }
        }

        val after = if (verified) measure(context, category, rootAvailable) else null
        val beforeBytes = before?.bytes
        val afterBytes = after?.bytes
        val freed = if (beforeBytes != null && afterBytes != null) {
            (beforeBytes - afterBytes).coerceAtLeast(0L)
        } else {
            0L
        }
        return CleanOutcome(executed = executed, verified = verified, freedBytes = freed)
    }

    /**
     * حذف كل ما يطابق قالبًا عامًّا (`*`).
     *
     * والتوسيع **في الصدفة** (`ls -d`) ثم الحذف **بمسارات صريحة** عبر `FileSystemEngine.delete`:
     * لو مرّرنا القالب إلى `rm` مباشرةً لكان الحذف بلا قراءة بعده، وهذا يعارض عقد التحقّق.
     * والسطر القادم من `ls` قد يحمل اسم ملفّ فيه مسافة — و`FileSystemEngine` يقتبس كل مسار
     * قبل `rm`، فلا يُقرأ الاسم جزءًا ثانيًا من الأمر.
     */
    private fun deleteMatching(glob: String): Pair<Boolean, Boolean> {
        val listed = PrivilegedShell.run("ls -d $glob") ?: return false to false
        val paths = listed.filter { it.isNotBlank() && it != glob }
        if (paths.isEmpty()) return true to true
        val outcome = FileSystemEngine.delete(paths)
        return outcome.executed to outcome.verified
    }

    /**
     * قياس بمخرج `du -sk` (بالكيلوبايت) عبر الصدفة — للمسارات التي لا يقرؤها التطبيق.
     *
     * والفشل `null`: إما أن الأمر لم يُنفَّذ (لا جذر)، وإما أن العدد في سطر لم يُقرأ. وكلاهما
     * «لم يُقس» لا «صفر».
     */
    private fun measureViaShell(category: UltraCleanCategory, vararg patterns: String): CleanMeasurement? {
        val output = PrivilegedShell.run("du -sk ${patterns.joinToString(" ")}") ?: return null
        if (output.isEmpty()) return CleanMeasurement(category, 0L, 0)
        var kilobytes = 0L
        var count = 0
        for (line in output) {
            val first = line.substringBefore('\t').substringBefore(' ').trim()
            val value = first.toLongOrNull() ?: return null
            kilobytes += value
            count++
        }
        return CleanMeasurement(category, kilobytes * 1024L, count)
    }

    /** قياس مباشر لشجرة من المجلدات المعروفة: مجموع أطوال الملفات وعددها. */
    private fun walkBytes(category: UltraCleanCategory, roots: List<File>): CleanMeasurement {
        var bytes = 0L
        var count = 0
        val queue = ArrayDeque<Pair<File, Int>>()
        roots.forEach { queue.addLast(it to 0) }
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (depth < WALK_DEPTH_CAP) queue.addLast(child to depth + 1)
                    continue
                }
                count++
                bytes += runCatching { child.length() }.getOrDefault(0L)
                if (count >= WALK_ENTRY_CAP) {
                    return CleanMeasurement(category, bytes, count)
                }
            }
        }
        return CleanMeasurement(category, bytes, count)
    }

    /** ملفات تطابق [match] تحت [root]، أو `null` إن لم يُقرأ الجذر ولا يوجد جذر يقرؤه. */
    private fun collectFiles(
        root: File,
        rootAvailable: Boolean,
        match: (File) -> Boolean,
    ): List<Pair<String, Long>>? {
        if (!runCatching { root.isDirectory }.getOrDefault(false)) return emptyList()
        if (!rootAvailable && !runCatching { root.canRead() }.getOrDefault(false)) return null
        val found = mutableListOf<Pair<String, Long>>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return null
            for (child in children) {
                if (child.isDirectory) {
                    if (depth < WALK_DEPTH_CAP) queue.addLast(child to depth + 1)
                    continue
                }
                if (match(child)) {
                    found += child.path to runCatching { child.length() }.getOrDefault(0L)
                }
                if (found.size >= WALK_ENTRY_CAP) return found
            }
        }
        return found
    }

    /**
     * مجلدات فارغة على التخزين المشترك، باستثناء `Android/`.
     *
     * و`Android/` مستثنى بنصّ العقد (§6.2): ما تحته ملك التطبيقات لا المستخدم، وحذفه ليس
     * تنظيفًا بل تعديل على بيانات تطبيقات أخرى.
     */
    private fun collectEmptyFolders(roots: List<File>): List<File>? {
        val empty = mutableListOf<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        roots.forEach { queue.addLast(it to 0) }
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (!child.isDirectory) continue
                if (child.name == "Android") continue
                val inner = runCatching { child.listFiles() }.getOrNull() ?: return null
                if (inner.isEmpty()) {
                    empty += child
                    continue
                }
                if (depth + 1 < WALK_DEPTH_CAP) queue.addLast(child to depth + 1)
                if (empty.size >= WALK_ENTRY_CAP) return empty
            }
        }
        return empty
    }
}
