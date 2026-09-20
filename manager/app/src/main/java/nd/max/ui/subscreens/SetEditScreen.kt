/*
 * Raw settings (global/secure/system) and system property editor. Adapted from
 * ZKM's ui/setedit/SetEditScreen.kt (a single 1400-line composable using Haze
 * glass cards, a paged tab container, and its own SettingsViewModel for
 * theme), rebuilt here on MaxManager's own SetEditViewModel + ExpressiveList /
 * ConfirmDialog / CustomBottomSheet components and the search+segmented-tabs
 * pattern already used by DozeModeScreen.
 *
 * Original: Copyright (c) 2025 ZKM, licensed GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
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

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.util.SetEditCategory
import nd.max.ui.util.SetEditItem
import nd.max.ui.util.isSensitiveSetEditKey
import nd.max.ui.viewmodel.SetEditHistoryEntry
import nd.max.ui.viewmodel.SetEditViewModel

@Composable
fun SetEditScreen(
    navController: NavController,
    viewModel: SetEditViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // موارد من `LocalResources.current`: كل نصوص هذه الشاشة تُقرأ داخل لامبدات استجابة (onSave/onDelete/…)
    // التي لا تُبطل فيها قراءة `LocalContext.current.resources` عند تغيّر التكوين.
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedItem by remember { mutableStateOf<SetEditItem?>(null) }
    var pendingDelete by remember { mutableStateOf<SetEditItem?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }

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
        Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    MaxManagerSubScreenTopBar(
                        scrollBehavior = scrollBehavior,
                        title = stringResource(R.string.setedit_title),
                        onBack = { navController.popBackStack() },
                        accentIcon = Icons.Filled.Dns,
                        accent = colorScheme.primary,
                        actions = {
                            IconButton(onClick = { showHistorySheet = true }) {
                                Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.setedit_history_cd))
                            }
                            IconButton(onClick = { showAddSheet = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.setedit_add_cd))
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = colorScheme.surface
            ) { innerPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                    item {
                        SetEditHero(
                            itemCount = viewModel.items.size,
                            filteredCount = viewModel.filteredItems.size,
                            onHistory = { showHistorySheet = true },
                            onAdd = { showAddSheet = true }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        OutlinedTextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.setedit_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(MaxUiMetrics.smallRadius),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        SetEditCategoryTabs(
                            selected = viewModel.selectedCategory,
                            onSelect = { viewModel.setCategory(it) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (viewModel.isLoading && viewModel.items.isEmpty()) {
                        item { SectionLoadingIndicator() }
                    } else if (viewModel.filteredItems.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.setedit_no_items), color = colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(viewModel.filteredItems, key = { it.lazyKey }) { item ->
                            SetEditRow(item, onClick = { selectedItem = item })
                        }
                    }
                }
            }
        }

    ConfirmDialogHost(handle = deleteConfirmDialog)

    CustomBottomSheet(visible = selectedItem != null, onDismiss = { selectedItem = null }) {
        selectedItem?.let { item ->
            SetEditDetailSheetContent(
                item = item,
                onSave = { newValue ->
                    viewModel.saveItem(item, newValue) { ok, key ->
                        if (ok) resources.getString(R.string.setedit_msg_saved, key) else resources.getString(R.string.setedit_msg_save_failed, key)
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

    CustomBottomSheet(visible = showHistorySheet, onDismiss = { showHistorySheet = false }) {
        SetEditHistorySheetContent(
            history = viewModel.deletedHistory,
            onRestore = { entry ->
                viewModel.restoreFromHistory(entry) { ok, key ->
                    if (ok) resources.getString(R.string.setedit_msg_restored, key) else resources.getString(R.string.setedit_msg_restore_failed, key)
                }
            }
        )
    }

    CustomBottomSheet(visible = showAddSheet, onDismiss = { showAddSheet = false }) {
        SetEditAddSheetContent(
            onCreate = { category, key, value ->
                viewModel.createItem(category, key, value) { ok, k ->
                    if (ok) resources.getString(R.string.setedit_msg_created, k) else resources.getString(R.string.setedit_msg_create_failed, k)
                }
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun SetEditHero(
    itemCount: Int,
    filteredCount: Int,
    onHistory: () -> Unit,
    onAdd: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "setedit_hero")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    MaxSurface(
        modifier = Modifier.fillMaxWidth(),
        accent = colors.primary
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .background(colors.primaryContainer, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setedit_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${stringResource(R.string.setedit_tab_all)} · $filteredCount/$itemCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                nd.max.ui.component.StudioTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(MaxUiMetrics.smallRadius)
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.setedit_add_cd))
                }
                nd.max.ui.component.StudioOutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(MaxUiMetrics.smallRadius)
                ) {
                    Icon(Icons.Outlined.History, null)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.setedit_history_cd))
                }
            }
    }
}

@Composable
private fun SetEditCategoryTabs(selected: SetEditCategory?, onSelect: (SetEditCategory?) -> Unit) {
    val tabs: List<Pair<SetEditCategory?, String>> = listOf(
        null to stringResource(R.string.setedit_tab_all),
        SetEditCategory.GLOBAL to stringResource(R.string.setedit_tab_global),
        SetEditCategory.SECURE to stringResource(R.string.setedit_tab_secure),
        SetEditCategory.SYSTEM to stringResource(R.string.setedit_tab_system),
        SetEditCategory.ANDROID_PROP to stringResource(R.string.setedit_tab_android)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (category, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                selected = selected == category,
                onClick = { onSelect(category) }
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SetEditRow(item: SetEditItem, onClick: () -> Unit) {
    val isSensitive = remember(item.key) { isSensitiveSetEditKey(item.key) }
    ExpressiveListItem(
        onClick = onClick,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (isSensitive) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.WarningAmber, contentDescription = stringResource(R.string.setedit_sensitive_cd), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        },
        supportingContent = {
            Text(item.value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    )
}

@Composable
private fun SetEditDetailSheetContent(item: SetEditItem, onSave: (String) -> Unit, onDelete: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var editValue by remember(item.key) { mutableStateOf(item.value) }
    val isSensitive = remember(item.key) { isSensitiveSetEditKey(item.key) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaxUiMetrics.screenHorizontalPadding, vertical = 8.dp)) {
        Text(stringResource(R.string.setedit_edit_sheet_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (isSensitive) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(MaxUiMetrics.compactRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setedit_sensitive_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(stringResource(R.string.setedit_label_key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        nd.max.ui.component.StudioOutlinedButton(
            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.key)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaxUiMetrics.compactRadius)
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.setedit_action_copy_cd), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.key, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.setedit_label_current_value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        nd.max.ui.component.StudioOutlinedButton(
            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.value)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaxUiMetrics.compactRadius)
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.setedit_action_copy_cd), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = editValue,
            onValueChange = { editValue = it },
            label = { Text(stringResource(R.string.setedit_label_new_value)) },
            singleLine = true,
            shape = RoundedCornerShape(MaxUiMetrics.compactRadius),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            nd.max.ui.component.StudioTonalButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MaxUiMetrics.compactRadius),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setedit_action_delete))
            }
            nd.max.ui.component.StudioButton(onClick = { onSave(editValue) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(MaxUiMetrics.compactRadius)) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setedit_action_save))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SetEditHistorySheetContent(history: List<SetEditHistoryEntry>, onRestore: (SetEditHistoryEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaxUiMetrics.screenHorizontalPadding, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.setedit_history_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.setedit_history_count, history.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.setedit_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                history.forEach { entry ->
                    ExpressiveListItem(
                        headlineContent = { Text(entry.item.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(entry.item.value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            nd.max.ui.component.StudioTextButton(onClick = { onRestore(entry) }) { Text(stringResource(R.string.setedit_history_restore)) }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SetEditAddSheetContent(onCreate: (SetEditCategory, String, String) -> Unit) {
    var category by remember { mutableStateOf(SetEditCategory.GLOBAL) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaxUiMetrics.screenHorizontalPadding, vertical = 8.dp)) {
        Text(stringResource(R.string.setedit_add_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.setedit_add_category_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        val categories = listOf(
            SetEditCategory.GLOBAL to stringResource(R.string.setedit_tab_global),
            SetEditCategory.SECURE to stringResource(R.string.setedit_tab_secure),
            SetEditCategory.SYSTEM to stringResource(R.string.setedit_tab_system),
            SetEditCategory.ANDROID_PROP to stringResource(R.string.setedit_tab_android)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            categories.forEachIndexed { index, (cat, label) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = categories.size),
                    selected = category == cat,
                    onClick = { category = cat }
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.setedit_add_key_label)) },
            singleLine = true,
            shape = RoundedCornerShape(MaxUiMetrics.compactRadius),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(stringResource(R.string.setedit_add_value_label)) },
            singleLine = true,
            shape = RoundedCornerShape(MaxUiMetrics.compactRadius),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        nd.max.ui.component.StudioButton(
            onClick = { onCreate(category, key.trim(), value) },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaxUiMetrics.compactRadius)
        ) {
            Text(stringResource(R.string.setedit_add_confirm))
        }
        Spacer(Modifier.height(8.dp))
    }
}
