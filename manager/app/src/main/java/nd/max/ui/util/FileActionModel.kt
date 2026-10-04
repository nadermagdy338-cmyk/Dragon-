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
 * أيّ إجراء يصلح لأيّ تحديد — **قرار خالص** لا رسم.
 *
 * وُجد هذا الملف لأن عطبًا حقيقيًّا وقع فعلًا: بعد إعادة كتابة الشاشة للوحين بقي إجراء
 * «إعادة التسمية» بلا مسار واجهة، وبقي `FileOperation.Extract` في المحرّك والحرس مع
 * **لا زرّ يناديه**. والعطب لم يظهر في تصريف ولا في اختبار، لأن كليهما يفحص ما هو موجود،
 * لا ما هو **موصول**. فصارت «أيّ الإجراءات تنطبق» مسؤولية نموذج تُكتب له اختبارات.
 *
 * والقاعدة الحاكمة: **الإجراء غير المناسب لا يُعرض معطّلًا، بل لا يُعرض أصلًا**.
 * زرّ «فكّ» على ملف ليس أرشيفًا ليس خيارًا محايدًا — إنه يَعِد بشيء لا يمكن.
 *
 * `labelRes` **ليست هنا عن قصد**: هذا الملف لا يعرف `R` ولا Compose، فيُختبر وحده.
 */
package nd.max.ui.util

/** إجراء واحد على التحديد. القائمة مغلقة، فلا يوجد «إجراء» يُخترع في الواجهة. */
enum class FileAction(val destructive: Boolean = false) {
    Copy,
    Move,
    Compress,
    Extract,
    Rename,
    Details,
    Delete(destructive = true),
    Clear,
}

object FileActionSet {

    /**
     * الإجراءات التي تنطبق فعلًا على هذا التحديد.
     *
     * - **إعادة التسمية** وتفاصيل مدخل واحد: تحتاج مدخلًا **واحدًا بالضبط**. تسمية عشرة
     *   ملفات باسم واحد ليست إعادة تسمية.
     * - **الفكّ**: يحتاج **أرشيفًا واحدًا** محدَّدًا. وفكّ أرشيفين معًا إلى المجلد نفسه
     *   خطر تعارض أسماء لا يريد أحد أن يشرحه للمستخدم.
     * - والترتيب مقصود: الإجراءات المدمِّرة في النهاية، والمسح في النهاية تمامًا.
     */
    fun forSelection(entries: List<FileEntry>, selection: FileSelection): List<FileAction> {
        if (selection.isEmpty) return emptyList()

        val chosen = entries.filter { it.path in selection.paths }
        val single = chosen.singleOrNull()

        return buildList {
            add(FileAction.Copy)
            add(FileAction.Move)
            add(FileAction.Compress)
            // ومجلد اسمه `backup.tar.gz` ليس أرشيفًا: النوع يُقرأ من الجهاز لا من الاسم
            // وحده، وإلا عرضنا «فكّ» على مجلد.
            if (single != null && !single.isDirectory && FileArchive.isSupportedArchive(single.name)) {
                add(FileAction.Extract)
            }
            if (single != null) add(FileAction.Rename)
            if (single != null) add(FileAction.Details)
            add(FileAction.Delete)
            add(FileAction.Clear)
        }
    }

    /** هل يمكن تنفيذ [action] على هذا التحديد؟ الحرس الثاني الذي يمنع نداءً مباشرًا خاطئًا. */
    fun isAvailable(action: FileAction, entries: List<FileEntry>, selection: FileSelection): Boolean =
        action in forSelection(entries, selection)
}

/**
 * تمييز الأرشيف — **بالاسم لا بالمحتوى**، وهذا حدّ يُعلَن.
 *
 * قراءة أول بايتين من كل ملف لمعرفة نوعه أدقّ، لكنها تمرّ بـshell لكل ملف في القائمة،
 * وثمنها إحساس بالثقَل في كل مرة تُحدَّد. وما يُدعم فعلًا اليوم: `zip` (يُنتجه محرّكنا
 * الداخلي ويُفكّ داخليًّا) و`tar.gz`/`tgz`/`tar` (تنتجها أداة النظام). فأي اسم آخر
 * يُقال عنه «ليس أرشيفًا مدعومًا» ولا يُخمَّن.
 */
object FileArchive {

    /**
     * الصيغ القابلة للفكّ. و**zip أولًا** لأنه ما ينتجه محرّكنا اليوم وما يتناقله الناس،
     * وتليه صيغ `tar` التي ينتجها النظام ويقرؤها بلا أداة إضافية.
     */
    private val SUPPORTED = listOf(".zip", ".tar.gz", ".tgz", ".tar")

    fun isSupportedArchive(name: String): Boolean {
        val lower = name.lowercase()
        return SUPPORTED.any { lower.endsWith(it) }
    }

    /** هل هذا الاسم أرشيف zip؟ الحكم من الامتداد لأنه هو ما يُبنى عليه اسم الأرشيف. */
    fun isZip(name: String): Boolean = name.lowercase().endsWith(".zip")

    /**
     * اسم الأرشيف الناتج عن ضغط عنصر: **zip** كما في MT، ويُبنى في مكان واحد فيتفق
     * الصفّ والمحرّك فلا يفترق ما وُعد به المستخدم عمّا يُكتب على القرص.
     */
    fun archiveNameFor(entryName: String): String = archiveNameFor(entryName, ArchiveFormat.Zip)

    /** الاسم لصيغة مختارة — فيتفق ما وعد به الحوار مع ما يُكتب على القرص. */
    fun archiveNameFor(entryName: String, format: ArchiveFormat): String =
        "$entryName${format.extension}"

    /** الخيار الثاني (صيغة tar) لمن يريدها صراحةً — باسم معلن لا بخيال في الواجهة. */
    fun tarNameFor(entryName: String): String = archiveNameFor(entryName, ArchiveFormat.TarGz)

    /**
     * إعادة تسمية عند تغيير الصيغة: **الامتداد يتبع الصيغة** لا العكس.
     *
     * ولماذا: بلا هذا كان من الممكن أن يُختار `tar.gz` ويبقى الاسم `x.zip`، فيُكتب داخل
     * ملفّ اسمه zip أرشيفُ tar — أي أن الاسم يكذب. والمقايضة معلنة: اسم اختاره المستخدم
     * بيده (بلا امتداد معروف) يُعطى امتداد الصيغة، وهو ما يجعل الملفّ يُفتح بالنقرة في
     * هذا التطبيق بدل أن يُفكّ يدويًّا في كل مرّة.
     */
    fun renamedForFormat(name: String, format: ArchiveFormat): String {
        val lower = name.lowercase()
        val stripped = SUPPORTED.firstOrNull { lower.endsWith(it) }?.let { name.dropLast(it.length) } ?: name
        return "$stripped${format.extension}"
    }
}
