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
 * قوائم أوامر مدير الملفات: **الشاشة · النافذة · المدخل**.
 *
 * وفُصلت هنا لأن كل واحدة تُبنى من نصّ ونموذج فقط، ولا تحتاج حالة الشاشة — فتُقرأ بمعزل،
 * ويبقى ملف الشاشة تركيبًا لا قائمة أسماء.
 *
 * والقاعدتان الملزمتان:
 *
 * 1. **الإجراء غير المناسب لا يُعرض** (‏[FileActionSet]): لا زرّ معطّل يَعِد بما لا يمكن.
 * 2. **كل أمر يحمل اسمه**: لا رمز بلا كلمة بجانبه في أي قائمة.
 */
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.ui.design.MaxCommand
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileActionSet
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileSort
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.FileWindowState

/** أوامر الشاشة: الترتيب · الإخفاء · المفضّلة · التحديد · النتائج · الشرح. */
@Composable
internal fun fileManagerScreenCommands(
    window: FileWindowState,
    resultsVisible: Boolean,
    onSort: (FileSort) -> Unit,
    onToggleHidden: () -> Unit,
    onAddBookmark: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearResults: () -> Unit,
    onHelp: () -> Unit,
): List<MaxCommand> = buildList {
    FileSortKey.entries.forEach { key ->
        add(
            MaxCommand(
                label = stringResource(sortLabel(key)),
                icon = sortIcon(key),
                active = window.sort.key == key,
                onSelect = { onSort(window.sort.copy(key = key)) },
            )
        )
    }
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_sort_ascending),
            icon = Icons.Rounded.SwapVert,
            active = window.sort.ascending,
            onSelect = { onSort(window.sort.copy(ascending = !window.sort.ascending)) },
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_sort_folders_first),
            icon = Icons.Rounded.FolderOpen,
            active = window.sort.directoriesFirst,
            onSelect = { onSort(window.sort.copy(directoriesFirst = !window.sort.directoriesFirst)) },
        )
    )
    add(
        MaxCommand(
            label = stringResource(
                if (window.showHidden) R.string.max_files_hide_hidden else R.string.max_files_show_hidden,
            ),
            icon = if (window.showHidden) Icons.Rounded.HideSource else Icons.Rounded.Visibility,
            active = window.showHidden,
            onSelect = onToggleHidden,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_bookmark_add),
            icon = Icons.Rounded.BookmarkAdd,
            onSelect = onAddBookmark,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_action_select_all),
            icon = Icons.Rounded.SelectAll,
            onSelect = onSelectAll,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_action_invert),
            icon = Icons.Rounded.Checklist,
            onSelect = onInvertSelection,
        )
    )
    if (resultsVisible) {
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_search_clear_results),
                icon = Icons.Rounded.HideSource,
                onSelect = onClearResults,
            )
        )
    }
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_help_title),
            icon = Icons.Rounded.HelpOutline,
            onSelect = onHelp,
        )
    )
}

/** أوامر النافذة: تسميتها · رئيسيها · الانتقال إليه · فتحها في الأخرى · تبديلهما. */
@Composable
internal fun fileManagerWindowCommands(
    window: FileWindowState,
    onRenameWindow: () -> Unit,
    onSetHome: () -> Unit,
    onGoHome: () -> Unit,
    onOpenInOther: () -> Unit,
    onSwap: () -> Unit,
): List<MaxCommand> = buildList {
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_window_rename),
            icon = Icons.Rounded.Edit,
            onSelect = onRenameWindow,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_window_set_home),
            icon = Icons.Rounded.FolderOpen,
            onSelect = onSetHome,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_window_go_home),
            icon = Icons.Rounded.FolderOpen,
            onSelect = onGoHome,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_open_other_window),
            icon = Icons.Rounded.OpenInNew,
            onSelect = onOpenInOther,
        )
    )
    add(
        MaxCommand(
            label = stringResource(R.string.max_files_window_swap),
            icon = Icons.Rounded.SwapHoriz,
            onSelect = onSwap,
        )
    )
}

/**
 * قائمة الضغط الطويل: **أوامر المدخل الملموس** لا قائمة عامّة.
 *
 * وتبدأ بـ«فتح» لأن أوّل ما يريده من ضغط مطوّلًا على ملف هو فتحه، ثم تأتي أوامر
 * [FileActionSet] بحسب ما ينطبق على هذا التحديد بالضبط.
 */
@Composable
internal fun fileManagerContextCommands(
    entries: List<FileEntry>,
    selection: FileSelection,
    onOpen: (FileEntry) -> Unit,
    onAction: (FileAction) -> Unit,
): List<MaxCommand> = buildList {
    val first = entries.filter { it.path in selection.paths }.firstOrNull()
    if (first != null) {
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_open_cd),
                icon = Icons.Rounded.OpenInNew,
                onSelect = { onOpen(first) },
            )
        )
    }
    FileActionSet.forSelection(entries, selection).forEach { action ->
        add(
            MaxCommand(
                label = stringResource(actionLabel(action)),
                icon = actionIcon(action),
                destructive = action.destructive,
                onSelect = { onAction(action) },
            )
        )
    }
}
