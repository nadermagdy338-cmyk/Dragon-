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
    val sort: FileSort = FileSort(),
    val selection: FileSelection = FileSelection(),
    val selecting: Boolean = false,
) {
    /** المدخلات كما قرأها الجهاز، بلا تصفية ولا ترتيب. */
    val entries: List<FileEntry> get() = (listing as? DirectoryListing.Entries)?.entries.orEmpty()

    /** ما يُعرض فعلًا: تصفية ثم ترتيب. */
    fun visible(): List<FileEntry> = FileBrowser.sort(FileBrowser.filter(entries, query), sort)

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
