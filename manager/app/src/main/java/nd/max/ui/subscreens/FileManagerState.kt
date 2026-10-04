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
 * حالة الشاشة كما تُحمل بين إعادة التركيب: ما يُقرأ من كل نافذة، وما يفتح فوقها.
 *
 * وهي `data class` ثابتة عن قصد، على قاعدة النماذج في هذا المستودع: كل تغيير يُنتج قيمة
 * جديدة، فلا يبقى حقل تغيّر في مكان ونسيه قارئ في مكان آخر — وهو أصل أكثر أعطاب الشاشات
 * التي تحمل عشرات الحقول المتغيّرة.
 *
 * **والمسار ليس هنا**: المسار والسجل والترتيب والإخفاء في [FileWindowState] (نموذج النافذة)،
 * وهذا الملف يحمل ما هو **عرض قراءة**: المدخلات المقروءة، والبحث، والتحديد. فلا يملك
 * المسارُ مكانين يختلفان.
 */
package nd.max.ui.subscreens

import nd.max.ui.util.ApkFacts
import nd.max.ui.util.ArchiveFormat
import nd.max.ui.util.CompressionLevel
import nd.max.ui.util.DeepSearchOutcome
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.EntryCounts
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOpRequest
import nd.max.ui.util.FilePermissionRules
import nd.max.ui.util.FileSearchFilters
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileWindowState
import nd.max.ui.util.PermissionSet
import nd.max.ui.util.SearchLimits
import nd.max.ui.util.TextPreview
import nd.max.ui.util.WindowSide

/** ما قُرئ من نافذة، وكيف يُعرض الآن. */
internal data class WindowView(
    val listing: DirectoryListing? = null,
    val loading: Boolean = true,
    val query: String = "",
    val filters: FileSearchFilters.Filter = FileSearchFilters.Filter(),
    val selection: FileSelection = FileSelection(),
    val selecting: Boolean = false,
    /**
     * مرساة النطاق: آخر مدخل لمسه المستخدم لمسًا **واحدًا** (سحب بدأ تحديدًا، أو نقرة
     * عكست تحديدًا). فالسحب بعدها يمتدّ من هذه المرساة إليه بدل أن يضيف عنصرًا وحده.
     *
     * و`null` تعني «لا مرساة»: أوّل سحب يبدأ تحديدًا جديدًا ويصير هو المرساة. وتُصفَّر
     * عند كل خروج من نمط التحديد وعند كل فعل جماعيّ («حدّد الكل» · «اعكس») — فالفعل
     * الجماعيّ لا مدخلَ واحدًا يُنسب إليه.
     */
    val swipeAnchor: String? = null,
) {
    /** المدخلات كما قرأها الجهاز، بلا تصفية ولا ترتيب. */
    val entries: List<FileEntry> get() = (listing as? DirectoryListing.Entries)?.entries.orEmpty()

    /**
     * نتائج البحث العميق المعروضة في هذه النافذة، أو `null`.
     *
     * ولها موضع واحد: النافذة التي بحثت. والانتقال أو التحديث يمسحها — فلا تُقرأ نتائج
     * بحث قديم على مجلد آخر.
     */
    val results: DeepSearchOutcome? get() = null

    /** ما يُعرض فعلًا: إخفاء ثم بحث بالاسم ثم المرشّح ثم الترتيب. */
    fun visible(window: FileWindowState, nowEpochSec: Long): List<FileEntry> = FileBrowser.sort(
        FileSearchFilters.apply(
            FileBrowser.filter(
                if (window.showHidden) entries else FileBrowser.withoutHidden(entries),
                query,
            ),
            filters,
            nowEpochSec,
        ),
        window.sort,
    )

    val filtering: Boolean get() = query.isNotBlank() || filters.isActive

    val counts: EntryCounts? get() = (listing as? DirectoryListing.Entries)?.let { FileBrowser.counts(entries) }

    /**
     * قصّ التحديد على ما هو ظاهر.
     *
     * وهذا حرس **مقيس** لا تجميلي: اختيار اختفى بمرشّح ثم يُنسخ بلا أن يُرى هو أسوأ ما
     * يمكن أن يفعله مرشّح في مدير ملفات. فيُقصّ عند كل تغيير يعيد تشكيل القائمة.
     */
    fun pruned(window: FileWindowState, nowEpochSec: Long): WindowView {
        if (selection.isEmpty) return this
        val visiblePaths = visible(window, nowEpochSec).mapTo(HashSet()) { it.path }
        val kept = selection.paths.filterTo(HashSet()) { it in visiblePaths }
        if (kept.size == selection.paths.size) return this
        return copy(selection = FileSelection(kept))
    }
}

/** حالة المحرّر: النصّ وما حُفظ منه، ومعه سجلّ تراجع محدود. */
internal data class EditorState(
    val path: String,
    val preview: TextPreview? = null,
    val text: String = "",
    val savedText: String = "",
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val wide: Boolean = false,
    val saving: Boolean = false,
    val verdict: String? = null,
) {
    val dirty: Boolean get() = text != savedText
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** تعديل: الحالة السابقة تُدفع إلى سجلّ التراجع، وحفظ جديد يُلغي مسار الإعادة. */
    fun edited(next: String): EditorState = copy(
        text = next,
        undoStack = (undoStack + text).takeLast(UNDO_LIMIT),
        redoStack = emptyList(),
    )

    fun undo(): EditorState {
        val previous = undoStack.lastOrNull() ?: return this
        return copy(
            text = previous,
            undoStack = undoStack.dropLast(1),
            redoStack = (redoStack + text).takeLast(UNDO_LIMIT),
        )
    }

    fun redo(): EditorState {
        val next = redoStack.lastOrNull() ?: return this
        return copy(
            text = next,
            undoStack = (undoStack + text).takeLast(UNDO_LIMIT),
            redoStack = redoStack.dropLast(1),
        )
    }

    companion object {
        const val UNDO_LIMIT: Int = 50
    }
}

/** حالة البحث العميق: طلبه ونتيجته. */
internal data class SearchState(
    val open: Boolean = false,
    val root: String = "",
    val query: String = "",
    val limits: SearchLimits = SearchLimits(),
    val running: Boolean = false,
    val startedAtMs: Long = 0L,
    val outcome: DeepSearchOutcome? = null,
)

/** حالة نافذة الخصائص: القيم المقروءة وما يُكتب. */
internal data class PropertiesState(
    val entry: FileEntry,
    val tab: Int = 0,
    val selinux: String? = null,
    val loadingSelinux: Boolean = false,
    val apk: ApkFacts? = null,
    val loadingApk: Boolean = false,
    val permissions: PermissionSet? = null,
    val octal: String = "",
    val owner: String = "",
    val group: String = "",
) {
    /** يُحدَّث الرقم الثماني من البتّات بعد كل تبديل — فلا يفترق ما يراه عن ما سيُكتب. */
    fun toggled(scope: nd.max.ui.util.AccessScope, bit: nd.max.ui.util.AccessBit): PropertiesState {
        val current = permissions ?: return this
        val next = current.toggle(scope, bit)
        return copy(permissions = next, octal = next.octal)
    }

    companion object {
        /** من قراءة المدخل: ما لم يُقرأ يبقى فارغًا لا صفرًا. */
        fun of(entry: FileEntry): PropertiesState {
            val parsed = FilePermissionRules.parse(entry.permissions)
            return PropertiesState(
                entry = entry,
                permissions = parsed,
                octal = parsed?.octal.orEmpty(),
                owner = entry.owner.orEmpty(),
                group = entry.group.orEmpty(),
            )
        }
    }
}

/**
 * ضغط ينتظر قرارًا: الاسم والصيغة والمستوى — **والقرار يُبنى عليه الطلب**.
 *
 * ويُحمل معه المصادر لا التحديد الحاليّ: من فتح الحوار ثم لمس صفًّا آخر لا يُضاف إلى
 * أرشيفه بلا أن يقول — والعدد يُعرض من هنا فيُعلن ما سيُضغط.
 */
internal data class CompressState(
    val sources: List<FileEntry>,
    /** النافذة التي طُلب الضغط فيها — فالطلب يُبنى على وجهتها لا على النافذة النشطة لاحقًا. */
    val side: WindowSide,
    val name: String,
    val format: ArchiveFormat,
    val level: CompressionLevel = CompressionLevel.Normal,
    /**
     * سبب رفض الاسم **رمزًا** (من [nd.max.ui.util.FileOpGuard]) أو `null`.
     *
     * ورمزًا لا نصًّا، لأن النموذج لا يعرف `R`: الواجهة هي التي تُترجمه إلى جملة.
     */
    val nameProblem: FileOpRefusal? = null,
)

/**
 * تعارض أسماء ينتظر قرارًا.
 *
 * ويُحمل معه الطلب الأصلي: القرار يُطبَّق على **الدفعة كما طُلبت**، لا على ما تبقّى بعد
 * سؤال سابق — وهذا ما يمنع تنفيذ نصف دفعة برأيين.
 */
internal data class ConflictRequest(
    val names: List<String>,
    val destinationNames: Set<String>,
    val request: FileOpRequest,
)
