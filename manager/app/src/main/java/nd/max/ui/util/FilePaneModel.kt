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
 * `FM-01` — حالة اللوح الواحد ونموذج اللوحين، **خالصة** بلا Compose وبلا shell.
 *
 * ولماذا نموذج للّوحين أصلًا: مع لوح واحد، العملية الخطِرة الوحيدة هي كتابة في مسار
 * واحد. ومع لوحين تصير **الوجهة مجلدًا اختاره المستخدم في اللوح الآخر** — وهذا هو
 * بالضبط الشكل الذي يظهر فيه «نسخ مجلد داخل نفسه»: تفتح اللوحين على المجلد نفسه أو
 * على شجرة متداخلة، تضغط نسخ، فتنسخ الشجرة داخل نفسها بلا نهاية. فالقرار مكانه نموذج
 * يُختبر، لا انتباه كاتب الواجهة.
 *
 * و[FileOpGuard] هو من يحكم فعلًا؛ هذا الملف يبني الطلب **من اللوحين** ويسأله — فلا
 * تُكتب قواعد الحماية مرتين.
 */
package nd.max.ui.util

/** أيّ لوح. والنوع مغلق فلا يُخترع «لوح ثالث» في مكان آخر. */
enum class PaneSide {
    Left,
    Right,
    ;

    val other: PaneSide get() = if (this == Left) Right else Left
}

/**
 * كيف يُعرض اللوحان.
 *
 * و`Auto` ليس احتياطًا بل قرارًا: على هاتف ضيّق يقيس اللوحان بعرض 320dp فيصير كل صفّ
 * عمودًا من حرفين. وعلى شاشة أعرض ينقلبان جنبًا إلى جنب. لكن القرار الآلي لا يعرف أن
 * المستخدم **يريد** اللوحين معًا الآن، ولا أنه يريد رؤية مسار طويل كامل — فالأوضاع
 * الصريحة تُلغي الآلي، والآلي يبقى الافتراضي.
 */
enum class PaneLayout {
    Auto,
    SideBySide,
    Stacked,
}

/**
 * قاعدة العرض الخالصة: عرض متاح وحدّ ⇒ جنبًا إلى جنب أو فوق/تحت.
 *
 * ولماذا في نموذج لا في الشاشة: هذه القاعدة يُبنى عليها **أيّ الشاشتين تُركَّب**، فخطأ
 * فيها يُنتج لوحين متراكبين في صندوق لا يتّسعهما (قياس لا نهائي ← انهيار في `LazyColumn`).
 * وهي دالة صافية تُقاس في JVM بلا جهاز.
 */
object PaneLayoutRule {

    fun sideBySide(layout: PaneLayout, availableWidth: Float, threshold: Float): Boolean = when (layout) {
        PaneLayout.SideBySide -> true
        PaneLayout.Stacked -> false
        PaneLayout.Auto -> availableWidth >= threshold
    }
}

/**
 * حالة لوح واحد: مسار · قراءة · بحث · ترتيب · تحديد.
 *
 * وهي `data class` ثابتة عن قصد: كل انتقال يُنتج حالة جديدة، فلا يبقى في الشاشة حقل
 * تغيّر في مكان ونسيه قارئ في مكان آخر (وهو أصل أكثر أعطاب الشاشات المزدوجة).
 */
data class FilePaneState(
    val path: String = "/",
    val listing: DirectoryListing? = null,
    val loading: Boolean = true,
    val query: String = "",
    /** `OCR-10`: مرشّح البحث — نوع المحتوى، والحجم، والعمر. `Filter()` = بلا ترشيح. */
    val search: FileSearchFilters.Filter = FileSearchFilters.Filter(),
    val sort: FileSort = FileSort(),
    val selection: FileSelection = FileSelection(),
    val selecting: Boolean = false,
    /**
     * الملفات المخفّية ظاهرة أو لا.
     *
     * والافتراضي **مخفيّة** كما في كل مدير ملفات — وليس إخفاءً صامتًا: [EntryCounts.hidden]
     * يُعرض في سطر حالة اللوح دائمًا، فيعرف المستخدم أن هناك ما لا يراه وأن مفتاحه في
     * قائمة أوامر اللوح. والفرق بين «مخفيّ وأعلنته» و«مفقود» هو كل الفرق.
     */
    val showHidden: Boolean = false,
    /**
     * تبويبات اللوح: مجلدات مثبّتة في **هذا اللوح وحده** لتُنقَر فيُنتقل إليها.
     *
     * وتبقى مع التنقّل ([at] لا يصفّرها) لأنها ليست موضعًا حاليًا بل مفاتيح، ولا تُصفَّر
     * بعد عملية لأن العملية قد تكون هي سبب الحاجة إليها.
     */
    val tabs: List<String> = emptyList(),
) {
    /** المدخلات كما قرأها الجهاز، بلا تصفية ولا ترتيب. */
    val entries: List<FileEntry> get() = (listing as? DirectoryListing.Entries)?.entries.orEmpty()

    /** ما يُعرض فعلًا: تصفية ثم ترتيب. */
    /**
     * ما يُعرض فعلًا: البحث بالاسم ثم المرشّح ثم الترتيب.
     *
     * و[nowEpochSec] يُمرَّر لأن مرشّح العمر يحتاج «الآن»: أخذه من الساعة داخل النموذج كان
     * سيصيّر الناتج غير قابل للقياس بلا انتظار. والقيمة الافتراضية تجعل الشاشة تُمرّر لا شيئًا.
     */
    fun visible(nowEpochSec: Long = System.currentTimeMillis() / 1000L): List<FileEntry> = FileBrowser.sort(
        FileSearchFilters.apply(
            FileBrowser.filter(
                if (showHidden) entries else FileBrowser.withoutHidden(entries),
                query,
            ),
            search,
            nowEpochSec,
        ),
        sort,
    )

    /** بحث أو مرشّح فعّال — يُعلن في الشاشة كي لا يُقرأ غياب الملف كأنه حُذف. */
    val filtering: Boolean get() = query.isNotBlank() || search.isActive

    /** انتقال إلى مجلد: صفر التحديد والبحث — فلا تبقى حالة لوح على لوح آخر. */
    fun at(newPath: String): FilePaneState =
        copy(path = FileBrowser.normalize(newPath), selection = FileSelection(), query = "", selecting = false)

    fun withListing(fresh: DirectoryListing): FilePaneState = copy(listing = fresh, loading = false)

    /** ضغط طويل: يبدأ التحديد ويحدّد المدخل في خطوة واحدة. */
    fun toggleSelection(target: String): FilePaneState = copy(
        selecting = true,
        selection = selection.toggle(target),
    )

    fun clearSelection(): FilePaneState = copy(selecting = false, selection = FileSelection())

    fun isSelected(target: String): Boolean = target in selection.paths
}

/**
 * تبويبات اللوح — عمليّات خالصة على قائمة مسارات.
 *
 * ولماذا تبويبات أصلًا: التنقّل في مدير ملفات ذهاب وعودة بين مجلدين أو ثلاثة، وكل
 * عودة تعني صعودًا ثم دخولًا أو مسارًا يُكتب يدويًّا. والتبويب يحفظ المكان الذي **اختاره
 * المستخدم** بنقرة واحدة، وهو الفرق الذي يجعل اللوحين يعملان: لوح على `Download`
 * وآخر على `Android/data`، والتبويبات داخل كل لوح لحفظ المقارنات المتكرّرة.
 *
 * ولا سقف لعددها — الشريط يمرّ أفقيًّا. و«سقف يُسقط الأقدم» كان سيعني أن تثبيتًا وقع
 * بيد المستخدم يُحذف بلا أن يعلم، وهو أسوأ من تمرير.
 */
object PaneTabs {

    /** تثبيت مسار. التكرار لا يُنتج تبويبًا ثانيًا، فيُصبح النقر على «ثبّت» آمنًا. */
    fun pin(tabs: List<String>, path: String): List<String> {
        val target = FileBrowser.normalize(path)
        return if (target in tabs) tabs else tabs + target
    }

    /** إزالة التثبيت — وبلا خطأ إن لم يكن مثبّتًا. */
    fun unpin(tabs: List<String>, path: String): List<String> = tabs - FileBrowser.normalize(path)

    fun isPinned(tabs: List<String>, path: String): Boolean = FileBrowser.normalize(path) in tabs

    /**
     * عنوان التبويب: اسم آخر قطعة في المسار — والجذر يعرض نفسه (`/`).
     *
     * والاسم وحده لا المسار الكامل: الشريط يحمل خمسة تبويبات أو أكثر، ومسار كامل في كل
     * تبويب يجعل الشريط سطرًا من المسارات المتشابهة. والمسار الكامل يُقرأ في شريط المسار
     * فور النقر على التبويب — أي أنه لا يُخفى، بل يُؤجَّل إلى حيث يُقرأ.
     */
    fun label(path: String): String = FileBrowser.nameOf(path)
}

/** نموذج اللوحين: بناء طلبات النقل بينهما، والمزامنة، والتبديل. */
object DualPane {

    /**
     * طلب نقل من لوح إلى آخر.
     *
     * والوجهة هي **مجلد اللوح المقابل** — وهذا هو معنى اللوحين: لا يُكتب مسار بيد
     * المستخدم، ولا يُنسخ لمسار لم يرَه. والطلب يُمرَّر إلى [FileOpGuard] قبل التنفيذ،
     * فترفض الحالات: المصدر = الوجهة، أو الوجهة داخل المصدر.
     */
    fun transferRequest(
        operation: FileOperation,
        from: FilePaneState,
        to: FilePaneState,
    ): FileOpRequest = FileOpRequest(
        operation = operation,
        sources = from.selection.paths.toList().sorted(),
        destination = to.path,
    )

    /** هل يوجد ما يُنقل؟ الوجهة الصالحة شرط، والتحديد الفارغ ليس طلبًا. */
    fun canTransfer(from: FilePaneState, to: FilePaneState): Boolean =
        from.selection.isNotEmpty && FileBrowser.normalize(from.path) != FileBrowser.normalize(to.path)

    /** المزامنة: كلا اللوحين على مجلد واحد — بلا تغيير أي شيء آخر في اللوح المقابل. */
    fun syncOther(from: FilePaneState, to: FilePaneState): FilePaneState =
        to.copy(path = FileBrowser.normalize(from.path))

    /**
     * **التنقّل المرتبط** — أين يذهب اللوح الآخر عند دخول مجلد في اللوح الذي يتحرّك.
     *
     * وهذه هي المزامنة التي تنفع فعلًا مع الاثنين: المزامنة المطلقة ([syncOther]) تضع
     * اللوحين على المجلد **نفسه**، وعندها تصير الوجهة = المصدر فلا نقلَ ممكنًا — أي أن
     * المزامنة تمنع الشيء الذي وُجد اللوحان من أجله. أما هنا فيتبع اللوح الآخر **الاسم**
     * لا المسار: تدخل `Android/data` فيُدخل الآخر `Android/data` بدوره من مساره هو.
     *
     * والبحث يجري في **قراءة اللوح الآخر** لا في مسار مُخترع: لا `childPath` بالنصّ،
     * لأن مجلدًا لم نقسه قد لا يوجد أصلًا (`/sdcard/Android/data` محجوب على كثير من
     * الإصدارات) فنكون قد أنزلنا لوحًا على مجلد لا يُقرأ. ولم يُوجد المجلد ⇒ `null`،
     * واللوح الآخر يبقى مكانه — ولا يُخمَّن له مسار.
     */
    fun mirrorFolder(other: FilePaneState, entered: FileEntry): String? {
        if (!entered.isDirectory) return null
        val twin = other.entries.firstOrNull { it.isDirectory && it.name == entered.name }
        return twin?.path
    }

    /**
     * الصعود المرتبط: المجلد الأب للوح الآخر، أو `null` إن كان على الجذر.
     *
     * و`null` هنا ليست فشلًا: الجذر لا أبَ له، واللوح الآخر يبقى عليه بدل أن يُدفع
     * إلى مسار غير موجود.
     */
    fun mirrorParent(other: FilePaneState): String? = FileBrowser.parentOf(other.path)

    /**
     * مزامنة قفزة فتات الخبز إلى سلف: إذا قفز المستخدم من `a/b/c` إلى `a`،
     * يصعد اللوح الآخر خطوتين من مساره الحالي. لا يُبنى مسار ابن جديد ولا تُخفى
     * قفزة غير قابلة للإثبات؛ إن لم يكن الهدف سلفًا مباشرًا نعيد `null`.
     */
    fun mirrorAncestor(other: FilePaneState, currentPath: String, targetPath: String): String? {
        var cursor = FileBrowser.normalize(currentPath)
        val target = FileBrowser.normalize(targetPath)
        var steps = 0
        while (cursor != target) {
            cursor = FileBrowser.parentOf(cursor) ?: return null
            steps++
        }
        var mirrored = other.path
        repeat(steps) {
            mirrored = FileBrowser.parentOf(mirrored) ?: return null
        }
        return mirrored
    }

    /**
     * تبديل اللوحين: يتبادل المساران، وتُصفَّر حالة كل لوح (تحديد وبحث) مع الانتقال.
     * وتبديل المسارين **بلا** تصفير الحالة كان سيُبقي تحديدًا على مدخل لم يعد مرئيًّا.
     */
    fun swap(left: FilePaneState, right: FilePaneState): Pair<FilePaneState, FilePaneState> =
        left.at(right.path) to right.at(left.path)

    /**
     * أيّ لوح يكون نشطًا بعد فتح المجلد الآخر: الضغط داخل لوح ينقله إلى الواجهة.
     * دالّة صغيرة لكنها تُختبر، لأن «اللوح النشط» يحدّد أين تذهب كل عملية.
     */
    fun activate(side: PaneSide): PaneSide = side
}
