/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
شاشة «التجميد والحذف»: تبويبات (الكل · المستخدم · النظام · المُجمَّد)، وبحث، وإجراءات
 * العقد والتفعيل عبر `DebloatFreezeViewModel`/`DebloatFreezeUtil`. */

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.fillMaxWidth
import nd.max.ui.design.maxEdgeFade
import nd.max.ui.design.maxBleed
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSplitScreen
import nd.max.ui.mainscreens.LabelText
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.util.DebloatAppInfo
import nd.max.ui.viewmodel.DebloatFreezeViewModel
import nd.max.ui.viewmodel.DebloatTab

@Composable
fun DebloatFreezeScreen(
    navController: NavController,
    viewModel: DebloatFreezeViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    var isSearchMode by remember { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<DebloatAppInfo?>(null) }

    LaunchedEffect(Unit) {
        if (viewModel.allApps.isEmpty()) viewModel.loadApps(context)
    }

    
    ScreenAccentProvider(colorScheme.tertiary) {
        // The shell owns the Scaffold, the insets and the scroll connection; this screen owns only
        // its search-capable bar and its expressive list, which `MaxListScreen` cannot host without
        // dropping `ExpressiveLazyList`'s card styling (shape per row position + placement spring).
        MaxSplitScreen(
            title = stringResource(R.string.debloat_freeze_title),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Outlined.DeleteSweep,
            accent = colorScheme.tertiary,
            topBar = { scrollBehavior ->
                DebloatFreezeTopAppBar(
                    scrollBehavior = scrollBehavior,
                    onBack = { navController.popBackStack() },
                    isSearchMode = isSearchMode,
                    onSearchModeChange = { isSearchMode = it },
                    searchQuery = viewModel.searchQuery,
                    onSearchChange = { viewModel.updateSearch(it) },
                    onRefresh = { viewModel.loadApps(context) }
                )
            }
        ) {
            MaxManagerInsight(
                text = stringResource(R.string.debloat_subtitle),
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp)
            )

            DebloatTabRow(
                selectedTab = viewModel.selectedTab,
                totalCount = viewModel.totalCount,
                userCount = viewModel.userCount,
                systemCount = viewModel.systemCount,
                frozenCount = viewModel.frozenCount,
                onTabSelected = { viewModel.selectedTab = it }
            )

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    SectionLoadingIndicator()
                }
            } else {
                ExpressiveLazyList(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    items = viewModel.filteredApps,
                    key = { it.packageName },
                    // `MaxSplitScreen` يملك هامش الصفحة (`MaxSpace.gutter`) **و**الحجز الأسفل
                    // (`pageBottom` + شريط النظام). وكان `start/end = 16.dp` فوقه ⇒ 36dp للجهة،
                    // و`bottom = 16.dp + inset` حجزًا مضاعفًا — نفس عطب صورة المالك في شاشة رابعة.
                    contentPadding = PaddingValues(
                        top = MaxSpace.sm,
                        bottom = MaxSpace.sm
                    )
                ) { app ->
                    DebloatAppRow(
                        app = app,
                        onToggleFreeze = { viewModel.toggleFreeze(context, app) },
                        onUninstall = { pendingUninstall = app },
                        onOpenSettings = { viewModel.openAppSettings(context, app.packageName) }
                    )
                }
            }
        }
    }

    val target = pendingUninstall
    if (target != null) {
    CustomContentDialog(
        visible = true,
        title = stringResource(R.string.debloat_uninstall_confirm_title, target.label),
        onDismiss = { pendingUninstall = null },
        onConfirm = {
            viewModel.debloatApp(context, target)
            pendingUninstall = null
        },
        confirmText = stringResource(R.string.debloat_uninstall_action)
    ) {
        Text(
            text = stringResource(R.string.debloat_uninstall_confirm_desc, target.packageName),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    }
    }

@Composable
private fun DebloatTabRow(
    selectedTab: DebloatTab,
    totalCount: Int,
    userCount: Int,
    systemCount: Int,
    frozenCount: Int,
    onTabSelected: (DebloatTab) -> Unit
) {
    val tabs = listOf(
        Triple(DebloatTab.ALL, stringResource(R.string.debloat_tab_all), totalCount),
        Triple(DebloatTab.USER, stringResource(R.string.debloat_tab_user), userCount),
        Triple(DebloatTab.SYSTEM, stringResource(R.string.debloat_tab_system), systemCount),
        Triple(DebloatTab.FROZEN, stringResource(R.string.debloat_tab_frozen), frozenCount)
    )
    val colorScheme = MaterialTheme.colorScheme
    ScrollableTabRow(
        // يمتدّ إلى حافة الشاشة ويُظلّل طرفيه؛ أوّل مقطع يرتاح عند هامش الصفحة نفسه.
        modifier = Modifier.fillMaxWidth().maxBleed().maxEdgeFade(),
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
        edgePadding = MaxSpace.gutter,
        containerColor = Color.Transparent,
        divider = {}
    ) {
        tabs.forEach { (tab, label, count) ->
            val isSelected = selectedTab == tab
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label)
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = count.toString(),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DebloatAppRow(
    app: DebloatAppInfo,
    onToggleFreeze: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    ExpressiveListItem(
        onClick = onOpenSettings,
        onLongClick = { menuExpanded = true },
        leadingContent = { DebloatAppIcon(app) },
        headlineContent = {
            Text(
                text = app.label,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        },
        supportingContent = {
            Column {
                Text(text = app.packageName, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (app.isSystem) LabelText(stringResource(R.string.label_system), colorScheme.secondary)
                    if (!app.isEnabled) LabelText(stringResource(R.string.debloat_label_frozen), colorScheme.error)
                }
            }
        },
        trailingContent = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaxSwitch(checked = app.isEnabled, onCheckedChange = { onToggleFreeze() })
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (app.isEnabled) stringResource(R.string.debloat_action_freeze) else stringResource(R.string.debloat_action_unfreeze)) },
                        leadingIcon = { Icon(Icons.Outlined.AcUnit, null) },
                        onClick = { onToggleFreeze(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.debloat_action_uninstall)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteForever, null, tint = colorScheme.error) },
                        onClick = { onUninstall(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.debloat_action_app_info)) },
                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        onClick = { onOpenSettings(); menuExpanded = false }
                    )
                }
            }
        }
    )
}

@Composable
private fun DebloatAppIcon(app: DebloatAppInfo) {
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
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(MaxRadius.chip))
            )
        }
    }
}

@Composable
private fun DebloatFreezeTopAppBar(
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
            title = stringResource(R.string.debloat_freeze_title),
            onBack = onBack,
            accentIcon = Icons.Outlined.DeleteSweep,
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
