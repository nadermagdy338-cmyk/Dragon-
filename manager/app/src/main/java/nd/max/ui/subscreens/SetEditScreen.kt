/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.ConfirmDialogHost
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.MaxEmptyState
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.component.StudioButton
import nd.max.ui.component.StudioOutlinedButton
import nd.max.ui.component.StudioTextButton
import nd.max.ui.component.StudioTonalButton
import nd.max.ui.component.rememberConfirmDialog
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.util.SetEditCategory
import nd.max.ui.util.SetEditItem
import nd.max.ui.util.isSensitiveSetEditKey
import nd.max.ui.util.setEditValueKind
import nd.max.ui.util.SetEditValueKind
import nd.max.ui.viewmodel.SetEditAction
import nd.max.ui.viewmodel.SetEditHistoryEntry
import nd.max.ui.viewmodel.SetEditSort
import nd.max.ui.viewmodel.SetEditViewModel

/**
 * محرّر الإعدادات والخصائص — قراءة مباشرة من الجهاز، وتعديل بإثبات، ويوميّة تُرجع ما غُيّر.
 *
 * ### ما أُعيد تصميمه، ولماذا كل تغيير مقصود
 *
 * 1. **النبض المُزخرف أُزيل.** كانت الشاشة تفتح على كتلة تنبض بلا توقّف (`rememberInfiniteTransition`
 *    بين 0.96 و1.04، تكرار لا ينتهي) في شاشة تُعدّل قيم نظام. حركة لا تحمل معلومة تُدفع من بطارية
 *    الجهاز الذي تجلس عليه — والشاشة الآن تبدأ **بما يُقاس**: عدد المعروض من الكلّ.
 * 2. **العنوان لم يُكرَّر.** كان مكتوبًا في الشريط العلويّ وفي كتلة البطل معًا.
 * 3. **اليوميّة صارت يوميّة.** كان المعروض منها **المحذوف وحده**: تعديل تكتبه للتوّ لا أثر له في أي
 *    مكان. وهي الآن تشمل **التعديل والإنشاء والحذف**، بأحدثها أوّلًا، ومحفوظ فيها **القيمة السابقة**.
 * 4. **و«أعِد القيمة القديمة» أُضيف** في ورقة التعديل: أوّل ما يحتاجه من حرّر مفتاحًا وأراد الرجوع —
 *    وكان عليه أن يتذكّر الرقم القديم بنفسه.
 * 5. **المحرّر يتكيّف مع نوع القيمة.** نصف مفاتيح `Settings` إمّا `0/1` وإمّا `true/false`؛ وكانت
 *    كلّها حقل نصّ خامًّا فيُكتب الرقم من الذاكرة. فالآن يُقرأ النوع من القيمة نفسها
 *    ([setEditValueKind]) فيُعرض **مفتاح تبديل** إن كانت ثنائية، **وحقل نصّ يبقى معها** للقيم
 *    التي لا تُختصر (`2`، `unknown`، مسار).
 * 6. **«ما غيّرتُه فقط» والترتيب بالاسم** أُضيفا: بعد عشر دقائق من التعديل تصير القائمة ألفًا،
 *    والسؤال «ما الذي لمسته؟» لا جواب له بالتمرير.
 * 7. **وصف الفئة في التبويب صار عدًّا مقيسًا** في الرأس لا محرفًا في العنوان.
 *
 * **والحدّ المعلَن:** هذه الشاشة **تكتب على الجهاز** — كل نتيجة كتابة تُعلَن في لقطة (نجحت أو فشلت)
 * ولا تُفترض، والقراءة كلها عبر `SetEditUtil` (أوامر `settings`/`getprop`) لا عبر كتابة ملفّات.
 */
@Composable
fun SetEditScreen(
    navController: NavController,
    viewModel: SetEditViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // موارد من `LocalResources.current`: كل نصوص هذه الشاشة تُقرأ داخل لامبدات استجابة (onSave/onDelete/…).
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedItem by remember { mutableStateOf<SetEditItem?>(null) }
    var pendingDelete by remember { mutableStateOf<SetEditItem?>(null) }
    var showJournal by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    val deleteConfirmDialog = rememberConfirmDialog(
        onConfirm = {
            pendingDelete?.let { item ->
                viewModel.deleteItem(item) { ok, key ->
                    if (!ok && item.category == SetEditCategory.ANDROID_PROP) {
                        resources.getString(R.string.setedit_msg_delete_unsupported)
                    } else if (ok) {
                        resources.getString(R.string.setedit_msg_deleted, key)
                    } else {
                        resources.getString(R.string.setedit_msg_delete_failed, key)
                    }
                }
            }
            pendingDelete = null
            selectedItem = null
        },
        onDismiss = { pendingDelete = null }
    )

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(viewModel.actionResult) {
        viewModel.actionResult?.let { result ->
            snackbarHostState.showSnackbar(result.substringAfter(":"))
            viewModel.clearActionResult()
        }
    }

    ScreenAccentProvider(colorScheme.primary) {
        MaxListScreen(
            title = stringResource(R.string.setedit_title),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Filled.Dns,
            accent = colorScheme.primary,
            snackbarHostState = snackbarHostState,
            actions = {
                IconButton(onClick = { showJournal = true }) {
                    Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.setedit_history_cd))
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.setedit_add_cd))
                }
            }
        ) {
            item {
                StateHeader(
                    shown = viewModel.filteredItems.size,
                    total = viewModel.items.size,
                    journalCount = viewModel.journal.size,
                    onlyEdited = viewModel.onlyEdited,
                    onOnlyEdited = viewModel::filterOnlyEdited
                )
                Spacer(Modifier.height(MaxSpace.sm))
            }

            item {
                MaxSearchField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = stringResource(R.string.setedit_search_hint)
                )
                Spacer(Modifier.height(MaxSpace.sm))
            }

            item {
                CategoryStrip(
                    selected = viewModel.selectedCategory,
                    onSelect = { viewModel.setCategory(it) }
                )
                Spacer(Modifier.height(MaxSpace.sm))
                SortStrip(sort = viewModel.sort, onSort = viewModel::chooseSort)
                Spacer(Modifier.height(MaxSpace.sm))
            }

            when {
                viewModel.isLoading && viewModel.items.isEmpty() -> item { SectionLoadingIndicator() }

                viewModel.filteredItems.isEmpty() -> item {
                    MaxEmptyState(
                        title = stringResource(R.string.setedit_empty_title),
                        message = stringResource(R.string.setedit_no_items)
                    )
                }

                else -> items(viewModel.filteredItems, key = { it.lazyKey }) { item ->
                    SetEditRow(
                        item = item,
                        edited = viewModel.editedKeys.contains(item.key),
                        onClick = { selectedItem = item }
                    )
                }
            }
        }
    }

    ConfirmDialogHost(handle = deleteConfirmDialog)

    CustomBottomSheet(visible = selectedItem != null, onDismiss = { selectedItem = null }) {
        selectedItem?.let { item ->
            DetailSheet(
                item = item,
                previousValue = viewModel.previousValueOf(item.key),
                onSave = { newValue ->
                    viewModel.saveItem(item, newValue) { ok, key ->
                        if (ok) resources.getString(R.string.setedit_msg_saved, key)
                        else resources.getString(R.string.setedit_msg_save_failed, key)
                    }
                    selectedItem = null
                },
                onRevert = { previous ->
                    viewModel.saveItem(item, previous) { ok, key ->
                        if (ok) resources.getString(R.string.setedit_msg_saved, key)
                        else resources.getString(R.string.setedit_msg_save_failed, key)
                    }
                    selectedItem = null
                },
                onDelete = {
                    pendingDelete = item
                    deleteConfirmDialog.showConfirm(
                        title = resources.getString(R.string.setedit_delete_confirm_title, item.key),
                        content = resources.getString(R.string.setedit_delete_confirm_desc) +
                            if (isSensitiveSetEditKey(item.key)) "\n\n" + resources.getString(R.string.setedit_sensitive_warning) else "",
                        confirm = resources.getString(R.string.setedit_action_delete),
                        dismiss = resources.getString(R.string.no)
                    )
                }
            )
        }
    }

    CustomBottomSheet(visible = showJournal, onDismiss = { showJournal = false }) {
        JournalSheet(
            entries = viewModel.journal,
            onRestore = { entry ->
                viewModel.restoreFromHistory(entry) { ok, key ->
                    if (ok) resources.getString(R.string.setedit_msg_restored, key)
                    else resources.getString(R.string.setedit_msg_restore_failed, key)
                }
            }
        )
    }

    CustomBottomSheet(visible = showAdd, onDismiss = { showAdd = false }) {
        AddSheet(
            onCreate = { category, key, value ->
                viewModel.createItem(category, key, value) { ok, k ->
                    if (ok) resources.getString(R.string.setedit_msg_created, k)
                    else resources.getString(R.string.setedit_msg_create_failed, k)
                }
                showAdd = false
            }
        )
    }
}

/**
 * رأس الشاشة: ما يُعرض من الكلّ، وعدد ما غُيّر في هذه الجلسة، ومفتاح «ما غيّرتُه فقط».
 *
 * وهو **بديل كتلة البطل** التي كانت تنبض: لا حركة، ولا تكرار للعنوان، وكل رقم فيه مقيس.
 */
@Composable
private fun StateHeader(
    shown: Int,
    total: Int,
    journalCount: Int,
    onlyEdited: Boolean,
    onOnlyEdited: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = colors.surfaceContainerLow
    ) {
        Column(Modifier.padding(MaxSpace.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.setedit_counts, shown, total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.setedit_journal_count, journalCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary
                )
            }
            Spacer(Modifier.height(MaxSpace.sm))
            MaxSwitchRow(
                title = stringResource(R.string.setedit_only_edited),
                checked = onlyEdited,
                onCheckedChange = onOnlyEdited,
                icon = Icons.Outlined.History
            )
        }
    }
}

/**
 * تبويبات الفئة — من `MaxSegmented` فلا نمط جديد، والعدّاد في الرأس لا في التبويب.
 *
 * و«الكل» أوّلًا لأنّه ما يُقرأ أوّلًا: الشاشة تقرأ كل الأصناف ثم تُصفّي في العرض، فالتبويب
 * **عرض** لا أمر قراءة جديد — إلا حين يُختار صنف بعينه فيُقرأ وحده ([SetEditViewModel.setCategory]).
 */
@Composable
private fun CategoryStrip(selected: SetEditCategory?, onSelect: (SetEditCategory?) -> Unit) {
    val options = listOf(
        null to stringResource(R.string.setedit_tab_all),
        SetEditCategory.GLOBAL to stringResource(R.string.setedit_tab_global),
        SetEditCategory.SECURE to stringResource(R.string.setedit_tab_secure),
        SetEditCategory.SYSTEM to stringResource(R.string.setedit_tab_system),
        SetEditCategory.ANDROID_PROP to stringResource(R.string.setedit_tab_android)
    )
    MaxSegmented(
        options = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onSelect = { index -> options.getOrNull(index)?.let { onSelect(it.first) } }
    )
}

/** الترتيب: `MaxSegmented` بعنصرين — «كما ورد» و«بالاسم». */
@Composable
private fun SortStrip(sort: SetEditSort, onSort: (SetEditSort) -> Unit) {
    MaxSegmented(
        options = listOf(
            stringResource(R.string.setedit_sort_natural),
            stringResource(R.string.setedit_sort_name)
        ),
        selectedIndex = if (sort == SetEditSort.ByKey) 1 else 0,
        onSelect = { index -> onSort(if (index == 1) SetEditSort.ByKey else SetEditSort.Natural) }
    )
}

@Composable
private fun SetEditRow(item: SetEditItem, edited: Boolean, onClick: () -> Unit) {
    val isSensitive = remember(item.key) { isSensitiveSetEditKey(item.key) }
    ExpressiveListItem(
        onClick = onClick,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.key,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // علامة «غُيّر في هذه الجلسة»: تُغني عن تذكّر ما لمسته، وتُقرأ بجانب المفتاح
                // نفسه لا في شاشة أخرى.
                if (edited) {
                    Spacer(Modifier.width(MaxSpace.xs))
                    Text(
                        text = stringResource(R.string.setedit_marker_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isSensitive) {
                    Spacer(Modifier.width(MaxSpace.xs))
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = stringResource(R.string.setedit_sensitive_cd),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(MaxSpace.lg)
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = item.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

/**
 * ورقة التعديل: المفتاح وقيمته (للنسخ)، والقيمة السابقة إن وُجدت، ومحرّر **يتكيّف** مع النوع.
 *
 * والمحرّر المزدوج (مفتاح تبديل **وحقل نصّ**) مقصود لا تردّد: التبديل أسرع وأأمن للثنائيات، والحقل
 * يبقى لأنّ القيمة الحقيقية قد تكون `2` أو `unknown` — فيُكتب النصّ الذي تراه العين، لا ما يفترضه
 * التطبيق.
 */
@Composable
private fun DetailSheet(
    item: SetEditItem,
    previousValue: String?,
    onSave: (String) -> Unit,
    onRevert: (String) -> Unit,
    onDelete: () -> Unit
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var editValue by remember(item.key) { mutableStateOf(item.value) }
    val isSensitive = remember(item.key) { isSensitiveSetEditKey(item.key) }
    val kind = remember(item.value) { setEditValueKind(item.value) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm)
    ) {
        Text(
            text = stringResource(R.string.setedit_edit_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = item.key,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(MaxSpace.md))

        if (isSensitive) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(MaxRadius.control),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(MaxSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(MaxSpace.sm))
                    Text(
                        text = stringResource(R.string.setedit_sensitive_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(MaxSpace.md))
        }

        CopyRow(
            label = stringResource(R.string.setedit_label_current_value),
            value = item.value,
            onCopy = { clipboard.setText(AnnotatedString(item.value)) },
            copyDescription = stringResource(R.string.setedit_action_copy_cd)
        )

        if (previousValue != null && previousValue != item.value) {
            Spacer(Modifier.height(MaxSpace.sm))
            StudioOutlinedButton(
                onClick = { onRevert(previousValue) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MaxRadius.control)
            ) {
                Text(stringResource(R.string.setedit_revert, previousValue))
            }
        }

        Spacer(Modifier.height(MaxSpace.md))
        if (kind == SetEditValueKind.Boolean) {
            MaxSwitchRow(
                title = stringResource(R.string.setedit_label_new_value),
                subtitle = stringResource(R.string.setedit_boolean_hint),
                checked = editValue.equals(item.booleanTrue, ignoreCase = true),
                onCheckedChange = { on -> editValue = if (on) item.booleanTrue else item.booleanFalse }
            )
            Spacer(Modifier.height(MaxSpace.xs))
        }

        OutlinedTextField(
            value = editValue,
            onValueChange = { editValue = it },
            label = { Text(stringResource(R.string.setedit_label_new_value)) },
            singleLine = true,
            shape = RoundedCornerShape(MaxRadius.control),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MaxSpace.md))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            StudioTonalButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MaxRadius.control)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(MaxSpace.lg))
                Text(stringResource(R.string.setedit_action_delete))
            }
            StudioButton(
                onClick = { onSave(editValue) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MaxRadius.control)
            ) {
                Text(stringResource(R.string.setedit_action_save))
            }
        }
        Spacer(Modifier.height(MaxSpace.sm))
    }
}

@Composable
private fun CopyRow(label: String, value: String, onCopy: () -> Unit, copyDescription: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(MaxSpace.xs))
        StudioOutlinedButton(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaxRadius.control)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = copyDescription,
                modifier = Modifier.size(MaxSpace.lg)
            )
            Spacer(Modifier.width(MaxSpace.sm))
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * اليوميّة: تعديل وإنشاء وحذف — بأحدثها أوّلًا، ومع كلّ سطر ما يكفي ليُفهم ويُرجَع.
 *
 * والسطر يحمل **القيمة السابقة** للسطر المُعدَّل (`old → new`)، لأنّ «١٠٤٨» وحدها لا تقول إن كانت
 * قديمة أو جديدة.
 */
@Composable
private fun JournalSheet(entries: List<SetEditHistoryEntry>, onRestore: (SetEditHistoryEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.setedit_history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.setedit_history_count, entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(MaxSpace.md))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = MaxSpace.xxl), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.setedit_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
                modifier = Modifier.heightIn(max = MaxSize.dialogListMax)
            ) {
                entries.forEach { entry ->
                    ExpressiveListItem(
                        headlineContent = {
                            Text(
                                text = entry.item.key,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        supportingContent = { JournalLine(entry) },
                        trailingContent = {
                            StudioTextButton(onClick = { onRestore(entry) }) {
                                Text(stringResource(R.string.setedit_history_restore))
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(MaxSpace.sm))
    }
}

@Composable
private fun JournalLine(entry: SetEditHistoryEntry) {
    val action = when (entry.action) {
        SetEditAction.CREATED -> stringResource(R.string.setedit_action_created)
        SetEditAction.MODIFIED -> stringResource(R.string.setedit_action_modified)
        SetEditAction.DELETED -> stringResource(R.string.setedit_action_deleted)
    }
    val previous = entry.previousValue
    Text(
        text = if (previous != null) "$action · $previous → ${entry.item.value}" else "$action · ${entry.item.value}",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AddSheet(onCreate: (SetEditCategory, String, String) -> Unit) {
    var category by remember { mutableStateOf(SetEditCategory.GLOBAL) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val categories = listOf(
        SetEditCategory.GLOBAL to stringResource(R.string.setedit_tab_global),
        SetEditCategory.SECURE to stringResource(R.string.setedit_tab_secure),
        SetEditCategory.SYSTEM to stringResource(R.string.setedit_tab_system),
        SetEditCategory.ANDROID_PROP to stringResource(R.string.setedit_tab_android)
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm)) {
        Text(
            text = stringResource(R.string.setedit_add_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(MaxSpace.md))

        Text(
            text = stringResource(R.string.setedit_add_category_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(MaxSpace.xs))
        MaxSegmented(
            options = categories.map { it.second },
            selectedIndex = categories.indexOfFirst { it.first == category }.coerceAtLeast(0),
            onSelect = { index -> categories.getOrNull(index)?.let { category = it.first } }
        )
        Spacer(Modifier.height(MaxSpace.md))

        // والمفتاح المكرّر يُمنع من جهة الكتابة نفسها (`settings put` يستبدل)، فالمقارنة هنا
        // **بين ما يُكتب وما هو موجود** تُنفَّذ في الشاشة بصياغة «سيُكتب فوق الموجود».
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.setedit_add_key_label)) },
            singleLine = true,
            shape = RoundedCornerShape(MaxRadius.control),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MaxSpace.sm))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(stringResource(R.string.setedit_add_value_label)) },
            singleLine = true,
            shape = RoundedCornerShape(MaxRadius.control),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MaxSpace.md))

        StudioButton(
            onClick = { onCreate(category, key.trim(), value) },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaxRadius.control)
        ) {
            Text(stringResource(R.string.setedit_add_confirm))
        }
        Spacer(Modifier.height(MaxSpace.sm))
    }
}
