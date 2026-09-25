/*
 * Live logcat viewer screen, driven by LogsViewerViewModel. Adapted from
 * ZKM's LogsView screen but rebuilt on MaxManager's own ExpressiveList /
 * CustomBottomSheet / ConfirmDialog components instead of ZKM's Haze glass
 * cards, and paired with a viewmodel that reads through a dedicated rooted
 * Shell instead of ZKM's non-root ProcessBuilder tail.
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

import android.content.Intent
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.core.diagnostics.LogArea
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.viewmodel.LogsViewerViewModel

@Composable
fun LogsViewerScreen(
    navController: NavController,
    viewModel: LogsViewerViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // موارد من `LocalResources.current`: كل النصوص هنا تُقرأ داخل `onClick` أو `launch`.
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsVisible by remember { mutableStateOf(false) }
    var shareVisible by remember { mutableStateOf(false) }

    val clearConfirmDialog = rememberConfirmDialog(
        onConfirm = {
            if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) viewModel.clearLogs() else viewModel.clearUnifiedLogs(context)
        },
        onDismiss = {}
    )

    LaunchedEffect(Unit) { viewModel.start() }

    /**
     * مشاركة ملف واحد — مسار واحد مهما كان الملف: ملف السجل الخام أو التقرير التشخيصي.
     *
     * وهذا هو معنى «نفس زرّ المشاركة»: الزرّ والمسار والقناع واحد، والذي يختلف هو الملفّ الناتج.
     */
    // اسم المعامل `mimeType` لا `type`: داخل `apply` يكون `this` هو الـIntent، ولو سُمّي `type`
    // لقُرئ `this.type = type` كإسناد الخاصيّة إلى نفسها (أي `null`).
    val shareFileNow: (File, String) -> Unit = { file, mimeType ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            setType(mimeType)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }
    val announceExport: (File?) -> Unit = { file ->
        // العدد من التبويب المفتوح لا من تبويب logcat دائمًا: كانت الرسالة تقول «حفظت N سطرًا»
        // برقم لا يخصّ الملف الذي خُرج.
        val count = if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.UNIFIED) {
            viewModel.unifiedTotalLineCount
        } else {
            viewModel.totalLineCount
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                if (file != null) {
                    resources.getString(R.string.logsviewer_save_success, count, file.name)
                } else {
                    resources.getString(R.string.logsviewer_save_failed)
                }
            )
        }
    }

    ScreenAccentProvider(colorScheme.secondary) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    MaxManagerSubScreenTopBar(
                        scrollBehavior = scrollBehavior,
                        title = stringResource(R.string.logsviewer_title),
                        onBack = { navController.popBackStack() },
                        accentIcon = Icons.Filled.Terminal,
                        accent = colorScheme.secondary,
                        actions = {
                            IconButton(onClick = { viewModel.togglePause() }) {
                                Icon(
                                    imageVector = if (viewModel.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = stringResource(
                                        if (viewModel.isPaused) R.string.logsviewer_resume_cd else R.string.logsviewer_pause_cd
                                    )
                                )
                            }
                            IconButton(onClick = {
                                val isUnified = viewModel.viewerMode == LogsViewerViewModel.ViewerMode.UNIFIED
                                clearConfirmDialog.showConfirm(
                                    title = resources.getString(R.string.logsviewer_clear_confirm_title),
                                    content = resources.getString(
                                        if (isUnified) R.string.logsviewer_clear_confirm_desc_unified else R.string.logsviewer_clear_confirm_desc
                                    ),
                                    confirm = resources.getString(R.string.yes),
                                    dismiss = resources.getString(R.string.no)
                                )
                            }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.logsviewer_clear_cd))
                            }
                            // المشاركة واحدة، وما يُشارَك هو ما يختار: نفس الزرّ يخدّم ملف السجل
                            // الخام والتقرير التشخيصي. ولو صار لكل واحد زرّه لصار عندنا زرّان يفعلان
                            // شيئًا واحدًا بصيغتين — وهذا ما يُنسى أحدهما.
                            IconButton(onClick = {
                                when (viewModel.viewerMode) {
                                    LogsViewerViewModel.ViewerMode.UNIFIED -> shareVisible = true
                                    LogsViewerViewModel.ViewerMode.LOGCAT -> viewModel.saveLogs(context) { file ->
                                        announceExport(file)
                                        file?.let { shareFileNow(it, "text/plain") }
                                    }
                                }
                            }) {
                                Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.logsviewer_save_cd))
                            }
                            if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) {
                                IconButton(onClick = { settingsVisible = true }) {
                                    Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.logsviewer_settings_cd))
                                }
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = colorScheme.surface
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                    LogViewerStatusHeader(
                        mode = viewModel.viewerMode,
                        lineCount = if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) viewModel.totalLineCount else viewModel.unifiedTotalLineCount,
                        paused = viewModel.isPaused,
                        filtered = if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) viewModel.displayedLogs.size else viewModel.unifiedDisplayedLogs.size
                    )

                    SecondaryTabRow(
                        selectedTabIndex = if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) 0 else 1
                    ) {
                        Tab(
                            selected = viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT,
                            onClick = { viewModel.setViewerMode(LogsViewerViewModel.ViewerMode.LOGCAT) },
                            text = { Text(stringResource(R.string.logsviewer_tab_logcat)) }
                        )
                        Tab(
                            selected = viewModel.viewerMode == LogsViewerViewModel.ViewerMode.UNIFIED,
                            onClick = { viewModel.setViewerMode(LogsViewerViewModel.ViewerMode.UNIFIED) },
                            text = { Text(stringResource(R.string.logsviewer_tab_unified)) }
                        )
                    }

                    val currentAvailability = if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) {
                        viewModel.isAvailable
                    } else {
                        viewModel.unifiedAvailable
                    }

                    when (currentAvailability) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SectionLoadingIndicator()
                    }
                    false -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.logsviewer_unavailable))
                    }
                    true -> if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        OutlinedTextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text(stringResource(R.string.logsviewer_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (viewModel.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LogsViewerViewModel.LogLevel.entries.forEach { level ->
                                val selected = level in viewModel.selectedLevels
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleLevel(level) },
                                    // الحرف وحده لا يُقرأ لقارئ الشاشة، والاسم الكامل في المورد.
                                    label = { Text(level.letter) },
                                    modifier = Modifier.semantics {
                                        contentDescription = stringResource(level.labelRes)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = level.color.copy(alpha = 0.22f),
                                        selectedLabelColor = level.color
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        AnimatedVisibility(
                            visible = viewModel.isPaused,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.secondaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.PauseCircle,
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.logsviewer_paused_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (viewModel.displayedLogs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.logsviewer_empty_state),
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().weight(1f, fill = true),
                                contentPadding = PaddingValues(
                                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                )
                            ) {
                                items(viewModel.displayedLogs, key = { it.id }) { entry ->
                                    LogLineRow(
                                        entry = entry,
                                        showPid = viewModel.showPid,
                                        showTid = viewModel.showTid
                                    )
                                }
                            }
                        }
                    } else Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = viewModel.unifiedSearchQuery,
                            onValueChange = { viewModel.onUnifiedSearchQueryChange(it) },
                            placeholder = { Text(stringResource(R.string.logsviewer_unified_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (viewModel.unifiedSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onUnifiedSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LogsViewerViewModel.LogSource.entries.forEach { source ->
                                val selected = source in viewModel.selectedSources
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleSource(source) },
                                    label = { Text(stringResource(source.labelRes)) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LogsViewerViewModel.UnifiedLogLevel.entries.forEach { level ->
                                val selected = level in viewModel.selectedUnifiedLevels
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleUnifiedLevel(level) },
                                    label = { Text(level.letter) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = level.color.copy(alpha = 0.22f),
                                        selectedLabelColor = level.color
                                    )
                                )
                            }
                            // «الفشل» حكم **مفهوم** لا مستوى سطر: `PERAPP_KNOB outcome=not-verified`
                            // و`APPLY_DRIFT_REASSERT_FAILED` فشلان مهما كان الحرف الذي كُتبا به،
                            // ومن يبحث عن عطل لا يعرف مسبقًا أيّ حرف اختاره الكاتب.
                            FilterChip(
                                selected = viewModel.failuresOnly,
                                onClick = { viewModel.toggleFailuresOnly() },
                                label = { Text(stringResource(R.string.logsviewer_failures_only)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.error.copy(alpha = 0.20f),
                                    selectedLabelColor = colorScheme.error
                                )
                            )
                        }

                        // الميزة: «أرني الحرارة» يحتاج معرفة الميزة من الحدث أو من المقبض، لا قراءة
                        // مئات الأسطر. والرمز تقني (GPU/CPU/thermal) كما بقية معرفات هذا السجل.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LogArea.entries.forEach { area ->
                                FilterChip(
                                    selected = area in viewModel.selectedAreas,
                                    onClick = { viewModel.toggleArea(area) },
                                    label = { Text(area.token) }
                                )
                            }
                        }

                        // عرضان لسؤالين مختلفين: «ماذا حدث الآن» و«ما آخر ما عُرف عن هذا المقبض».
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LogsViewerViewModel.UnifiedView.entries.forEach { view ->
                                FilterChip(
                                    selected = viewModel.unifiedView == view,
                                    onClick = { viewModel.setUnifiedView(view) },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (view) {
                                                    LogsViewerViewModel.UnifiedView.TIMELINE -> R.string.logsviewer_view_events
                                                    LogsViewerViewModel.UnifiedView.TARGETS -> R.string.logsviewer_view_controls
                                                }
                                            )
                                        )
                                    }
                                )
                            }
                            viewModel.focusedTarget?.let { target ->
                                // التركيز معلَن وقابل للإلغاء في مكانه: مرشِّح خفيّ يجعل الشاشة
                                // تبدو ناقصة بلا سبب.
                                Text(
                                    text = stringResource(R.string.logsviewer_focused_target, target),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearFocus() }) {
                                    Text(stringResource(R.string.logsviewer_clear_focus))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        AnimatedVisibility(
                            visible = viewModel.isPaused,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.secondaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.PauseCircle,
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.logsviewer_paused_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        val bottomPadding = PaddingValues(
                            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                        when {
                            viewModel.unifiedView == LogsViewerViewModel.UnifiedView.TARGETS && viewModel.targetSummaries.isEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.logsviewer_no_controls),
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }

                            viewModel.unifiedView == LogsViewerViewModel.UnifiedView.TARGETS -> LazyColumn(
                                modifier = Modifier.fillMaxSize().weight(1f, fill = true),
                                contentPadding = bottomPadding
                            ) {
                                items(viewModel.targetSummaries, key = { it.target }) { summary ->
                                    TargetSummaryRow(
                                        summary = summary,
                                        focused = viewModel.focusedTarget == summary.target,
                                        onClick = { viewModel.focusTarget(summary.target) }
                                    )
                                }
                            }

                            viewModel.unifiedDisplayedLogs.isEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.logsviewer_empty_state),
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }

                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize().weight(1f, fill = true),
                                contentPadding = bottomPadding
                            ) {
                                items(viewModel.unifiedDisplayedLogs, key = { it.id }) { entry ->
                                    UnifiedLogLineRow(
                                        entry = entry,
                                        expanded = viewModel.expandedId == entry.id,
                                        onToggle = { viewModel.toggleExpanded(entry.id) },
                                        onFocusTarget = { viewModel.focusTarget(it) }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
    }

    ConfirmDialogHost(handle = clearConfirmDialog)

    LogsShareSheet(
        visible = shareVisible,
        onDismiss = { shareVisible = false },
        onRawLog = {
            shareVisible = false
            viewModel.exportUnifiedLogs(context) { file ->
                announceExport(file)
                file?.let { shareFileNow(it, "application/zip") }
            }
        },
        onReport = {
            shareVisible = false
            viewModel.shareDiagnosticBundle(context) { file ->
                announceExport(file)
                file?.let { shareFileNow(it, "text/plain") }
            }
        }
    )

    LogsViewerSettingsSheet(
        visible = settingsVisible,
        onDismiss = { settingsVisible = false },
        viewModel = viewModel
    )
}

