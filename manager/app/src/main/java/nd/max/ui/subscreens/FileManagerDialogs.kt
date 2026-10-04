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
 * نوافذ مدير الملفات: تُرَكَّب هنا بدل أن تُبعثر في جسم الشاشة.
 *
 * ولماذا ملف مستقلّ: الشاشة صارت تركيب حالة وواجهة، وعدد النوافذ (تسمية · مجلد/ملف جديد ·
 * حذف · مسار · خصائص · محرّر · بحث · تعارض · رفض · شرح) كان سيجعلها ملفًا لا يُقرأ.
 *
 * والقاعدتان الملزمتان هنا:
 *
 * 1. **لا زرّ تأكيد على قيمة غير صالحة**: تسمية/إنشاء يُتحقَّق منها بـ[FileOpGuard.isValidName]
 *    قبل أن يكون التأكيد فعّالًا — فالرفض يأتي قبل الحوار لا بعده.
 * 2. **الحذف نهائي ومؤكَّد** (قرار المالك): النصّ يقول العدد وأن لا سلّة محذوفات، والزرّ
 *    بلون الخطأ، **والنصّ نفسه** يقول النتيجة — فلا يعتمد المعنى على اللون وحده.
 */
package nd.max.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.ui.component.FileCompressDialog
import nd.max.ui.component.FileConflictDialog
import nd.max.ui.component.FileEditorDialog
import nd.max.ui.component.FilePropertiesDialog
import nd.max.ui.component.FileSearchDialog
import nd.max.ui.component.MaxScreenHelpDialog
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxInputDialog
import nd.max.ui.util.AccessBit
import nd.max.ui.util.AccessScope
import nd.max.ui.util.ArchiveFormat
import nd.max.ui.util.CompressionLevel
import nd.max.ui.util.ConflictChoice
import nd.max.ui.util.FileDeleteTarget
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileOpGuard
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileWindowsState
import nd.max.ui.util.StorageScanModel

/** كل نافذة تُفتح فوق الشاشة — ونوع مغلق فلا تُخترع نافذة بلا إغلاق. */
internal enum class FileDialog {
    Rename,
    RenameWindow,
    NewFolder,
    NewFile,
    Compress,
    Delete,
    Path,
    Refused,
    Properties,
    Editor,
    Search,
    Conflict,
    Help,
}

/** ما تناديه النوافذ في الشاشة. مجمّعة في كائن واحد فلا يصير لكل نافذة عقد طويل. */
internal data class FileManagerCallbacks(
    val onInput: (String) -> Unit,
    val onRename: (String) -> Unit,
    val onRenameWindow: (String) -> Unit,
    val onNewFolder: (String) -> Unit,
    val onNewFile: (String) -> Unit,
    val onCompressName: (String) -> Unit,
    val onCompressFormat: (ArchiveFormat) -> Unit,
    val onCompressLevel: (CompressionLevel) -> Unit,
    val onCompressConfirm: () -> Unit,
    val onDelete: () -> Unit,
    val onPath: (String) -> Unit,
    val onProperties: (PropertiesState) -> Unit,
    val onLoadSelinux: () -> Unit,
    val onLoadApk: () -> Unit,
    val onToggleBit: (AccessScope, AccessBit) -> Unit,
    val onApplyPermissions: () -> Unit,
    val onApplyOwner: () -> Unit,
    val onCopyPath: (String) -> Unit,
    val onEditor: (EditorState) -> Unit,
    val onSaveEditor: () -> Unit,
    val onReloadEditor: () -> Unit,
    val onSearch: (SearchState) -> Unit,
    val onStartSearch: () -> Unit,
    val onCancelSearch: () -> Unit,
    val onConflict: (ConflictChoice, Boolean) -> Unit,
    val onDismiss: (FileDialog) -> Unit,
)

@Composable
internal fun FileManagerDialogs(
    windows: FileWindowsState,
    rename: FileEntry?,
    renameWindowOpen: Boolean,
    newFolderOpen: Boolean,
    newFileOpen: Boolean,
    compress: CompressState?,
    deleteTargets: FileDeleteTarget?,
    pathEditOpen: Boolean,
    inputDraft: String,
    refused: FileOpRefusal?,
    properties: PropertiesState?,
    editor: EditorState?,
    search: SearchState,
    conflict: ConflictRequest?,
    helpOpen: Boolean,
    callbacks: FileManagerCallbacks,
) {
    val cancelLabel = stringResource(R.string.max_files_cancel)
    val applyLabel = stringResource(R.string.max_files_confirm)

    // ── التسمية ───────────────────────────────────────────────────────────────
    rename?.let { entry ->
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_files_rename_title),
            fieldLabel = stringResource(R.string.max_files_rename_field),
            value = inputDraft,
            onValueChange = callbacks.onInput,
            confirmLabel = applyLabel,
            // التأكيد لا يُفعّل إلا على اسم صالح ومختلف — والرفض قبل الحوار لا بعده.
            confirmEnabled = FileOpGuard.isValidName(inputDraft) && inputDraft != entry.name,
            onConfirm = { callbacks.onRename(inputDraft) },
            onDismiss = { callbacks.onDismiss(FileDialog.Rename) },
        )
    }

    // ── تسمية النافذة ────────────────────────────────────────────────────────
    if (renameWindowOpen) {
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_files_window_rename),
            fieldLabel = stringResource(R.string.max_files_window_title_field),
            value = inputDraft,
            onValueChange = callbacks.onInput,
            confirmLabel = applyLabel,
            onConfirm = { callbacks.onRenameWindow(inputDraft) },
            onDismiss = { callbacks.onDismiss(FileDialog.RenameWindow) },
            supportingText = stringResource(R.string.max_files_window_title_note),
        )
    }

    if (newFolderOpen) {
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_files_new_folder_title),
            fieldLabel = stringResource(R.string.max_files_new_folder_field),
            value = inputDraft,
            onValueChange = callbacks.onInput,
            confirmLabel = applyLabel,
            confirmEnabled = FileOpGuard.isValidName(inputDraft),
            onConfirm = { callbacks.onNewFolder(inputDraft) },
            onDismiss = { callbacks.onDismiss(FileDialog.NewFolder) },
        )
    }

    if (newFileOpen) {
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_files_new_file_title),
            fieldLabel = stringResource(R.string.max_files_new_file_field),
            value = inputDraft,
            onValueChange = callbacks.onInput,
            confirmLabel = applyLabel,
            confirmEnabled = FileOpGuard.isValidName(inputDraft),
            onConfirm = { callbacks.onNewFile(inputDraft) },
            onDismiss = { callbacks.onDismiss(FileDialog.NewFile) },
            supportingText = stringResource(R.string.max_files_new_file_note),
        )
    }

    // ── الضغط: الخيارات قبل الكتابة ────────────────────────────────────────
    compress?.let { state ->
        FileCompressDialog(
            visible = true,
            name = state.name,
            format = state.format,
            level = state.level,
            sourceCount = state.sources.size,
            // السبب رمزٌ في النموذج، والجملة تُبنى هنا (النماذج لا تعرف `R`).
            nameProblem = state.nameProblem?.let { stringResource(refusalText(it)) },
            onName = callbacks.onCompressName,
            onFormat = callbacks.onCompressFormat,
            onLevel = callbacks.onCompressLevel,
            onConfirm = callbacks.onCompressConfirm,
            onDismiss = { callbacks.onDismiss(FileDialog.Compress) },
        )
    }

    // ── الحذف: نهائي ومؤكَّد ─────────────────────────────────────────────────
    deleteTargets?.let { target ->
        // الحجم يُعرض **فقط** إن كان كل عنصر حجمه معروفًا؛ وإلا يُقال إن الحجم غير معروف.
        // وحوار حذف جذريّ يجب أن يقول الثمن قبل التأكيد لا بعده (فجوة §10.5 في المواصفة).
        val size = target.sizeBytes
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_files_delete_title),
            message = if (size == null) {
                stringResource(R.string.max_files_delete_body, target.count)
            } else {
                stringResource(
                    R.string.max_files_delete_body_sized,
                    target.count,
                    StorageScanModel.formatBytes(size),
                )
            },
            confirmLabel = stringResource(R.string.max_files_action_delete),
            destructive = true,
            onConfirm = callbacks.onDelete,
            onDismiss = { callbacks.onDismiss(FileDialog.Delete) },
            dismissLabel = cancelLabel,
        )
    }

    if (pathEditOpen) {
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_files_edit_path_title),
            fieldLabel = stringResource(R.string.max_files_edit_path_field),
            value = inputDraft,
            onValueChange = callbacks.onInput,
            confirmLabel = stringResource(R.string.max_files_open_cd),
            placeholder = stringResource(R.string.max_files_destination_hint),
            onConfirm = { callbacks.onPath(inputDraft) },
            onDismiss = { callbacks.onDismiss(FileDialog.Path) },
        )
    }

    refused?.let { reason ->
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_files_refused_title),
            message = stringResource(refusalText(reason)),
            confirmLabel = stringResource(R.string.max_files_action_done),
            onConfirm = { callbacks.onDismiss(FileDialog.Refused) },
            onDismiss = { callbacks.onDismiss(FileDialog.Refused) },
            dismissLabel = cancelLabel,
        )
    }

    properties?.let { state ->
        FilePropertiesDialog(
            visible = true,
            entry = state.entry,
            tab = state.tab,
            onTab = { index -> callbacks.onProperties(state.copy(tab = index)) },
            onDismiss = { callbacks.onDismiss(FileDialog.Properties) },
            dismissLabel = cancelLabel,
            selinux = state.selinux,
            onLoadSelinux = callbacks.onLoadSelinux,
            apkFacts = state.apk,
            onLoadApk = callbacks.onLoadApk,
            permissions = state.permissions,
            octalDraft = state.octal,
            onOctalDraft = { value -> callbacks.onProperties(state.copy(octal = value)) },
            onToggleBit = callbacks.onToggleBit,
            ownerDraft = state.owner,
            groupDraft = state.group,
            onOwnerDraft = { value -> callbacks.onProperties(state.copy(owner = value)) },
            onGroupDraft = { value -> callbacks.onProperties(state.copy(group = value)) },
            onApplyPermissions = callbacks.onApplyPermissions,
            onApplyOwner = callbacks.onApplyOwner,
            onCopyPath = { callbacks.onCopyPath(state.entry.path) },
        )
    }

    editor?.let { state ->
        FileEditorDialog(
            visible = true,
            path = state.path,
            preview = state.preview,
            text = state.text,
            onTextChange = { next -> callbacks.onEditor(state.edited(next)) },
            wideLines = state.wide,
            onToggleWideLines = { callbacks.onEditor(state.copy(wide = !state.wide)) },
            dirty = state.dirty,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = { callbacks.onEditor(state.undo()) },
            onRedo = { callbacks.onEditor(state.redo()) },
            onReload = callbacks.onReloadEditor,
            onSave = callbacks.onSaveEditor,
            onClose = { callbacks.onDismiss(FileDialog.Editor) },
            saving = state.saving,
            verdict = state.verdict,
        )
    }

    FileSearchDialog(
        visible = search.open,
        root = search.root.ifBlank { windows.activeWindow.path },
        query = search.query,
        onQuery = { value -> callbacks.onSearch(search.copy(query = value)) },
        limits = search.limits,
        onLimits = { value -> callbacks.onSearch(search.copy(limits = value)) },
        running = search.running,
        outcome = search.outcome,
        onStart = callbacks.onStartSearch,
        onCancel = callbacks.onCancelSearch,
        onDismiss = { callbacks.onDismiss(FileDialog.Search) },
    )

    conflict?.let { pending ->
        FileConflictDialog(
            visible = true,
            names = pending.names,
            onChoose = callbacks.onConflict,
            onDismiss = { callbacks.onDismiss(FileDialog.Conflict) },
        )
    }

    MaxScreenHelpDialog(
        visible = helpOpen,
        title = stringResource(R.string.max_files_help_title),
        description = stringResource(R.string.max_files_help_body),
        onDismiss = { callbacks.onDismiss(FileDialog.Help) },
    )
}
