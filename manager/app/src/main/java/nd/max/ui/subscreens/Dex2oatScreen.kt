/*
 * App Compiler screen - forces ART/dex2oat recompilation with a chosen
 * speed/size filter, per-app or in bulk (all / system-only / user-only),
 * plus a reset path back to installer-time compilation. Adapted from ZKM's
 * Dex2oatScreen but rebuilt on MaxManager's own ScreenChrome / ExpressiveList
 * components instead of ZKM's layout, and driven by Dex2oatViewModel +
 * Dex2oatUtil. The installed-app list itself is NOT reloaded a second way -
 * this screen reuses DebloatFreezeUtil.getInstalledApps() / DebloatAppInfo,
 * the same data the Debloat & Freeze screen already builds.
 *
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

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.util.DebloatAppInfo
import nd.max.ui.viewmodel.Dex2oatProgress
import nd.max.ui.viewmodel.Dex2oatTab
import nd.max.ui.viewmodel.Dex2oatViewModel

@Composable
private fun compileModeLabel(filter: String): String = when (filter) {
    "speed-profile" -> stringResource(R.string.dex2oat_mode_speed_profile)
    "speed" -> stringResource(R.string.dex2oat_mode_speed)
    "everything" -> stringResource(R.string.dex2oat_mode_everything)
    "quicker" -> stringResource(R.string.dex2oat_mode_quicker)
    "verify" -> stringResource(R.string.dex2oat_mode_verify)
    else -> filter
}

@Composable
private fun progressText(progress: Dex2oatProgress): String = when (progress) {
    is Dex2oatProgress.Single -> if (progress.resetting) {
        stringResource(R.string.dex2oat_progress_resetting, progress.label)
    } else {
        stringResource(R.string.dex2oat_progress_compiling, progress.label)
    }
    is Dex2oatProgress.Batch -> stringResource(R.string.dex2oat_progress_batch, progress.current, progress.total, progress.label)
    Dex2oatProgress.CompilingAll -> stringResource(R.string.dex2oat_progress_all)
    Dex2oatProgress.ResettingAll -> stringResource(R.string.dex2oat_progress_reset_all)
}

@Composable
fun Dex2oatScreen(
    navController: NavController,
    viewModel: Dex2oatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    var isSearchMode by remember { mutableStateOf(false) }
    var pendingBulkAction by remember { mutableStateOf<BulkAction?>(null) }

    LaunchedEffect(Unit) {
        if (viewModel.allApps.isEmpty()) viewModel.loadApps(context)
    }

    
    ScreenAccentProvider(colorScheme.tertiary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Dex2oatTopAppBar(
                    scrollBehavior = scrollBehavior,
                    onBack = { navController.popBackStack() },
                    isSearchMode = isSearchMode,
                    onSearchModeChange = { isSearchMode = it },
                    searchQuery = viewModel.searchQuery,
                    onSearchChange = { viewModel.updateSearch(it) },
                    onRefresh = { viewModel.loadApps(context) }
                )
            },
            containerColor = colorScheme.surface
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(innerPadding.calculateTopPadding() + 12.dp))

                val modeLabels = Dex2oatUtilCompileModeLabels()
                Column(Modifier.padding(horizontal = 16.dp)) {
                    MaxManagerInsight(
                        text = stringResource(R.string.dex2oat_subtitle),
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                ExpressiveList(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    content = listOf(
                        {
                            ExpressiveDropdownItem(
                                icon = Icons.Outlined.Build,
                                title = stringResource(R.string.dex2oat_mode_title),
                                summary = stringResource(R.string.dex2oat_mode_desc),
                                items = modeLabels.map { it.second },
                                selectedIndex = modeLabels.indexOfFirst { it.first == viewModel.selectedMode }.coerceAtLeast(0),
                                onItemSelected = { index -> viewModel.onModeSelected(modeLabels[index].first) }
                            )
                        }
                    )
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { pendingBulkAction = BulkAction.COMPILE_ALL },
                        label = { Text(stringResource(R.string.dex2oat_action_compile_all)) },
                        leadingIcon = { Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { pendingBulkAction = BulkAction.COMPILE_SYSTEM },
                        label = { Text(stringResource(R.string.dex2oat_action_compile_system)) },
                        leadingIcon = { Icon(Icons.Outlined.Android, null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { pendingBulkAction = BulkAction.COMPILE_USER },
                        label = { Text(stringResource(R.string.dex2oat_action_compile_user)) },
                        leadingIcon = { Icon(Icons.Outlined.Person, null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { pendingBulkAction = BulkAction.RESET_ALL },
                        label = { Text(stringResource(R.string.dex2oat_action_reset_all)) },
                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp)) }
                    )
                }

                val activeProgress = viewModel.progress
                if (activeProgress != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = progressText(activeProgress),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Dex2oatTabRow(
                    selectedTab = viewModel.selectedTab,
                    totalCount = viewModel.totalCount,
                    userCount = viewModel.userCount,
                    systemCount = viewModel.systemCount,
                    onTabSelected = { viewModel.selectedTab = it }
                )

                if (viewModel.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SectionLoadingIndicator()
                    }
                } else {
                    ExpressiveLazyList(
                        state = listState,
                        items = viewModel.filteredApps,
                        key = { it.packageName },
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ) { app ->
                        Dex2oatAppRow(
                            app = app,
                            onCompile = { viewModel.compileApp(app) },
                            onReset = { viewModel.resetApp(app) }
                        )
                    }
                }
            }
        }
    }

    val action = pendingBulkAction
    if (action != null) {
    CustomContentDialog(
        visible = true,
        title = stringResource(action.confirmTitleRes),
        onDismiss = { pendingBulkAction = null },
        onConfirm = {
            when (action) {
                BulkAction.COMPILE_ALL -> viewModel.compileAll()
                BulkAction.COMPILE_SYSTEM -> viewModel.compileSystemApps()
                BulkAction.COMPILE_USER -> viewModel.compileUserApps()
                BulkAction.RESET_ALL -> viewModel.resetAllApps()
            }
            pendingBulkAction = null
        },
        confirmText = stringResource(action.confirmActionRes)
    ) {
        Text(stringResource(action.confirmDescRes), style = MaterialTheme.typography.bodyMedium)
    }
    }
    }

private enum class BulkAction(
    val confirmTitleRes: Int,
    val confirmDescRes: Int,
    val confirmActionRes: Int
) {
    COMPILE_ALL(R.string.dex2oat_confirm_compile_all_title, R.string.dex2oat_confirm_compile_all_desc, R.string.dex2oat_action_compile_all),
    COMPILE_SYSTEM(R.string.dex2oat_confirm_compile_system_title, R.string.dex2oat_confirm_compile_system_desc, R.string.dex2oat_action_compile_system),
    COMPILE_USER(R.string.dex2oat_confirm_compile_user_title, R.string.dex2oat_confirm_compile_user_desc, R.string.dex2oat_action_compile_user),
    RESET_ALL(R.string.dex2oat_confirm_reset_all_title, R.string.dex2oat_confirm_reset_all_desc, R.string.dex2oat_action_reset_all)
}

@Composable
private fun Dex2oatUtilCompileModeLabels(): List<Pair<String, String>> {
    return nd.max.ui.util.Dex2oatUtil.COMPILE_MODES.map { it to compileModeLabel(it) }
}

@Composable
private fun Dex2oatTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    isSearchMode: Boolean,
    onSearchModeChange: (Boolean) -> Unit,
    searchQuery: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    onRefresh: () -> Unit
) {
    if (isSearchMode) {
        MaxManagerTopBarScrim {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onSearchModeChange(false); onSearchChange(TextFieldValue("")) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    } else {
        MaxManagerSubScreenTopBar(
            scrollBehavior = scrollBehavior,
            title = stringResource(R.string.dex2oat_title),
            onBack = onBack,
            accentIcon = Icons.Outlined.Build,
            accent = MaterialTheme.colorScheme.tertiary,
            actions = {
                IconButton(onClick = { onSearchModeChange(true) }) {
                    Icon(Icons.Default.Search, stringResource(R.string.cd_search))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.menu_refresh))
                }
            }
        )
    }
}

@Composable
private fun Dex2oatTabRow(
    selectedTab: Dex2oatTab,
    totalCount: Int,
    userCount: Int,
    systemCount: Int,
    onTabSelected: (Dex2oatTab) -> Unit
) {
    val tabs = listOf(
        Dex2oatTab.ALL to "${stringResource(R.string.dex2oat_tab_all)} ($totalCount)",
        Dex2oatTab.USER to "${stringResource(R.string.dex2oat_tab_user)} ($userCount)",
        Dex2oatTab.SYSTEM to "${stringResource(R.string.dex2oat_tab_system)} ($systemCount)"
    )
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
        divider = {}
    ) {
        tabs.forEach { (tab, label) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(label) }
            )
        }
    }
}

@Composable
private fun Dex2oatAppRow(
    app: DebloatAppInfo,
    onCompile: () -> Unit,
    onReset: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    ExpressiveListItem(
        onClick = { menuExpanded = true },
        leadingContent = { Dex2oatAppIcon(app) },
        headlineContent = {
            Text(text = app.label, fontWeight = FontWeight.Bold, maxLines = 1)
        },
        supportingContent = { Text(text = app.packageName, maxLines = 1) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dex2oat_action_compile)) },
                        leadingIcon = { Icon(Icons.Outlined.Bolt, null) },
                        onClick = { onCompile(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dex2oat_action_reset)) },
                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, null, tint = colorScheme.error) },
                        onClick = { onReset(); menuExpanded = false }
                    )
                }
            }
        }
    )
}

@Composable
private fun Dex2oatAppIcon(app: DebloatAppInfo) {
    val bitmap = remember(app.packageName) {
        val drawable = app.icon ?: return@remember null
        val size = 108
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    Box(modifier = Modifier.size(44.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            )
        }
    }
}
