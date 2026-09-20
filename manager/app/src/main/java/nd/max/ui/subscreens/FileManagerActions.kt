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
 * عقود شاشة مدير الملفات: **ماذا يعني كل إجراء، وكيف يُبنى طلبه** — خارج ملف الشاشة.
 *
 * وهذا الفصل ليس تنظيمًا شكليًّا: الشاشة صارت أكبر (نوافذ · حافظة · مهام · درج · محرّر)،
 * وحدّ الحجم المعلن في المستودع (١٠٠٠ سطر) يجعل أي إضافة إليها قرضًا على القارئ. فيبقى
 * هنا كل ما **يُقرأ بمعزل**: موقع سريع، وترجمة إجراء إلى طلب، ونصّ سبب رفض، وتحويل فشل
 * قراءة إلى حالة معلنة. أما الشاشة فتبقى تركيب الحالة والواجهة.
 *
 * والقاعدتان الثابتتان:
 *
 * 1. **الإجراء غير المناسب لا يُعرض** (‏[FileActionSet] تحكم أيّها ينطبق) — لا زرّ معطّل
 *    يَعِد بما لا يمكن.
 * 2. **طلب واحد يُبنى هنا ثم يُسأل [FileOpGuard] في الشاشة** — لا تُكتب قواعد الحماية مرتين.
 */
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileArchive
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileOpGuard
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOpRequest
import nd.max.ui.util.FileOperation
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.FileTask
import nd.max.ui.util.FileTaskKind
import nd.max.ui.util.ListingFailure

/** موقع سريع: اسمه في الموارد ومساره — ولا يُخمَّن مسار في الواجهة. */
internal data class QuickLocation(val labelRes: Int, val path: String)

/**
 * المواقع السريعة: الخمسة التي تُزار فعلًا في هذه الشاشة.
 *
 * ولا «مواقع ذكية» تُخترع من القرص: قائمة المواقع قرار تصميمي معلن، ومن احتاج غيرها كتب
 * مسارًا أو ثبّت مفضّلًا. وكل موقع قد لا يكون موجودًا على جهاز بعينه — والدرج يقوله بنصّه
 * (`Path not found`) بدل أن يُخفي الموقع أو يخمّن بديلًا عنه.
 */
internal fun quickLocations(): List<QuickLocation> = listOf(
    QuickLocation(R.string.max_files_location_root, "/"),
    QuickLocation(R.string.max_files_location_storage, "/sdcard"),
    QuickLocation(R.string.max_files_location_download, "/sdcard/Download"),
    QuickLocation(R.string.max_files_location_appdata, "/sdcard/Android/data"),
    QuickLocation(R.string.max_files_location_system, "/system"),
)

/** اسم الإجراء كما يُقرأ. */
internal fun actionLabel(action: FileAction): Int = when (action) {
    FileAction.Copy -> R.string.max_files_action_copy
    FileAction.Move -> R.string.max_files_action_cut
    FileAction.Compress -> R.string.max_files_action_compress
    FileAction.Extract -> R.string.max_files_action_extract
    FileAction.Rename -> R.string.max_files_action_rename
    FileAction.Details -> R.string.max_files_action_details
    FileAction.Delete -> R.string.max_files_action_delete
    FileAction.Clear -> R.string.max_files_action_clear_selection
}

/** رمز الإجراء — للمسح البصري فقط، والاسم دائمًا معه. */
internal fun actionIcon(action: FileAction): ImageVector = when (action) {
    FileAction.Copy -> Icons.Rounded.ContentCopy
    FileAction.Move -> Icons.Rounded.ContentCut
    FileAction.Compress -> Icons.Rounded.FolderZip
    FileAction.Extract -> Icons.Rounded.Unarchive
    FileAction.Rename -> Icons.Rounded.DriveFileRenameOutline
    FileAction.Details -> Icons.Rounded.Info
    FileAction.Delete -> Icons.Rounded.Delete
    FileAction.Clear -> Icons.Rounded.Delete
}

/**
 * طلب العملية لإجراء فوري — أو `null` حين لا يُنفَّذ من هنا.
 *
 * والنسخ والقصّ **لا يُنفَّذان هنا**: يملآن الحافظة فينتظران لصقًا. ومن احتاج نقلًا فوريًّا
 * يقطع مسافة أقلّ: قصّ ثم لصق في النافذة الأخرى — وهي رحلة يدعمها النموذج صراحةً.
 */
internal fun immediateRequest(action: FileAction, selected: List<FileEntry>, windowPath: String): FileOpRequest? {
    val paths = selected.map { it.path }.sorted()
    return when (action) {
        FileAction.Delete -> FileOpRequest(FileOperation.Delete, sources = paths)
        FileAction.Compress -> {
            val first = selected.firstOrNull() ?: return null
            // الاسم من محرّك الأرشيف نفسه، ثم يُزاح إن كان مشغولًا: فما وعد به الصفّ
            // («اضغط إلى x.zip») هو ما يُكتب على القرص.
            val name = FileOpGuard.uniqueName(
                FileArchive.archiveNameFor(first.name),
                selected.map { it.name }.toSet(),
            )
            FileOpRequest(
                operation = FileOperation.Compress,
                sources = paths,
                destination = FileBrowser.childPath(windowPath, name),
            )
        }
        FileAction.Extract -> {
            val archive = selected.singleOrNull() ?: return null
            FileOpRequest(
                operation = FileOperation.Extract,
                sources = listOf(archive.path),
                destination = windowPath,
            )
        }
        FileAction.Copy, FileAction.Move, FileAction.Rename, FileAction.Details, FileAction.Clear -> null
    }
}

/** سبب الرفض بنصّه لا برقمه. */
internal fun refusalText(reason: FileOpRefusal): Int = when (reason) {
    FileOpRefusal.EmptySelection -> R.string.max_files_refuse_empty
    FileOpRefusal.ProtectedPath -> R.string.max_files_refuse_protected
    FileOpRefusal.SelfTarget -> R.string.max_files_refuse_self
    FileOpRefusal.TargetInsideSource -> R.string.max_files_refuse_inside
    FileOpRefusal.InvalidName -> R.string.max_files_refuse_name
    FileOpRefusal.NameTaken -> R.string.max_files_refuse_taken
}

/** عنوان ترتيب القائمة. */
internal fun sortLabel(key: FileSortKey): Int = when (key) {
    FileSortKey.Name -> R.string.max_files_sort_name
    FileSortKey.Size -> R.string.max_files_sort_size
    FileSortKey.Modified -> R.string.max_files_sort_date
    FileSortKey.Kind -> R.string.max_files_sort_kind
}

internal fun sortIcon(key: FileSortKey): ImageVector = when (key) {
    FileSortKey.Name -> Icons.AutoMirrored.Rounded.Sort
    FileSortKey.Size -> Icons.Rounded.Straighten
    FileSortKey.Modified -> Icons.Rounded.Schedule
    FileSortKey.Kind -> Icons.Rounded.Category
}

/** اسم المهمة كما يُقرأ في شريط المهام. */
internal fun taskLabel(kind: FileTaskKind): Int = when (kind) {
    FileTaskKind.Copy -> R.string.max_files_action_copy
    FileTaskKind.Move -> R.string.max_files_action_cut
    FileTaskKind.Compress -> R.string.max_files_action_compress
    FileTaskKind.Extract -> R.string.max_files_action_extract
    FileTaskKind.Delete -> R.string.max_files_action_delete
}

/**
 * تقدّم مقيس أو `null`.
 *
 * و`null` تعني «لم تُقس الوجهة»، وتُعرض شريطًا غير محدَّد — لا صفرًا ولا نسبة مُخترعة
 * (ADR-07: المجهول يبقى مجهولًا).
 */
internal fun taskDetail(task: FileTask): String? {
    val done = FileFormat.size(task.doneBytes) ?: return null
    val total = FileFormat.size(task.totalBytes) ?: return null
    return "$done / $total"
}

/**
 * حالة القراءة كما تُعرض. وهي **قرار خالص** يُختبر وحده، والنصّ يُترجم عند العرض.
 *
 * ولماذا الفصل: الشاشة تبني الحالة في دالّة عاديّة، و`stringResource` لا تُستدعى هناك؛
 * ودفن النصوص في نموذج يخالف قاعدة المستودع (النماذج لا تعرف `R`). فالنموذج يقول **أيّ
 * حالة**، والواجهة تقول **ماذا نقول عنها**.
 */
internal enum class ListingState {
    Ready,
    Loading,
    ShellDown,
    NotFound,
    NotADirectory,
    Denied,
    Empty,
    EmptyFiltered,
    EmptyHidden,
}

internal fun listingState(
    listing: DirectoryListing?,
    loading: Boolean,
    visibleCount: Int,
    hiddenCount: Int,
    filtering: Boolean,
): ListingState = when {
    listing == null && loading -> ListingState.Loading
    listing == null -> ListingState.ShellDown
    listing is DirectoryListing.Unreadable -> when (listing.reason) {
        ListingFailure.NotFound -> ListingState.NotFound
        ListingFailure.NotADirectory -> ListingState.NotADirectory
        ListingFailure.PermissionDenied -> ListingState.Denied
        ListingFailure.ShellUnavailable -> ListingState.ShellDown
    }
    visibleCount == 0 && filtering -> ListingState.EmptyFiltered
    visibleCount == 0 && hiddenCount > 0 -> ListingState.EmptyHidden
    visibleCount == 0 -> ListingState.Empty
    else -> ListingState.Ready
}

/**
 * حالة القراءة بنصّها وإجرائها — `null` تعني «اعرض القائمة».
 *
 * وكل فشل يميّز نفسه: «لا جذر» ≠ «المسار غير موجود» ≠ «ليس مجلدًا» ≠ «فارغ فعلًا». ودمجها
 * في «تعذّرت القراءة» هو ما يجعل مدير ملفات بلا فائدة.
 */
@Composable
internal fun listingCondition(
    state: ListingState,
    retryLabel: String,
    onRetry: () -> Unit,
): MaxCondition? {
    @Composable
    fun condition(kind: MaxConditionKind, title: Int, detail: Int, retry: Boolean = false) = MaxCondition(
        kind = kind,
        title = stringResource(title),
        detail = stringResource(detail),
        primaryActionLabel = retryLabel.takeIf { retry },
        onPrimaryAction = onRetry.takeIf { retry },
    )

    return when (state) {
        ListingState.Ready -> null
        ListingState.Loading -> condition(
            MaxConditionKind.Loading,
            R.string.max_files_cond_loading_title,
            R.string.max_files_cond_loading_detail,
        )
        ListingState.ShellDown -> condition(
            MaxConditionKind.RootRequired,
            R.string.max_files_cond_shell_title,
            R.string.max_files_cond_shell_detail,
            retry = true,
        )
        ListingState.NotFound -> condition(
            MaxConditionKind.Error,
            R.string.max_files_cond_notfound_title,
            R.string.max_files_cond_notfound_detail,
            retry = true,
        )
        ListingState.NotADirectory -> condition(
            MaxConditionKind.Error,
            R.string.max_files_cond_notdir_title,
            R.string.max_files_cond_notdir_detail,
        )
        ListingState.Denied -> condition(
            MaxConditionKind.RootRequired,
            R.string.max_files_cond_denied_title,
            R.string.max_files_cond_denied_detail,
            retry = true,
        )
        ListingState.Empty, ListingState.EmptyFiltered, ListingState.EmptyHidden -> condition(
            MaxConditionKind.Empty,
            R.string.max_files_cond_empty_title,
            R.string.max_files_cond_empty_detail,
        )
    }
}

/**
 * شريط صدق فوق القائمة: قراءة ناقصة تُعلن نقصها ولا تُخفيه.
 *
 * - `attributesAvailable = false`: الأسماء وحدها وصلت، فالأحجام والصلاحيات **مجهولة**
 *   لا صفر.
 * - `skippedLines > 0`: أسطر لم تُفهم فهناك مداخل قد لا تظهر.
 */
@Composable
internal fun listingBanner(entries: DirectoryListing.Entries?): MaxCondition? {
    if (entries == null) return null
    if (!entries.attributesAvailable) {
        return MaxCondition(
            kind = MaxConditionKind.Unavailable,
            title = stringResource(R.string.max_files_degraded_title),
            detail = stringResource(R.string.max_files_degraded_detail),
        )
    }
    if (entries.skippedLines > 0) {
        return MaxCondition(
            kind = MaxConditionKind.Unavailable,
            title = stringResource(R.string.max_files_skipped_title),
            detail = stringResource(R.string.max_files_skipped_detail, entries.skippedLines),
        )
    }
    return null
}
