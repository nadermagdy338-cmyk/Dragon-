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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nd.max.R
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsVisible by remember { mutableStateOf(false) }

    val clearConfirmDialog = rememberConfirmDialog(
        onConfirm = {
            if (viewModel.viewerMode == LogsViewerViewModel.ViewerMode.LOGCAT) viewModel.clearLogs() else viewModel.clearUnifiedLogs()
        },
        onDismiss = {}
    )

    LaunchedEffect(Unit) { viewModel.start() }

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
                                    title = context.getString(R.string.logsviewer_clear_confirm_title),
                                    content = context.getString(
                                        if (isUnified) R.string.logsviewer_clear_confirm_desc_unified else R.string.logsviewer_clear_confirm_desc
                                    ),
                                    confirm = context.getString(R.string.yes),
                                    dismiss = context.getString(R.string.no)
                                )
                            }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.logsviewer_clear_cd))
                            }
                            IconButton(onClick = {
                                val isUnified = viewModel.viewerMode == LogsViewerViewModel.ViewerMode.UNIFIED
                                val exportShareType = if (isUnified) "application/zip" else "text/plain"
                                val onExportResult: (File?) -> Unit = { file ->
                                    if (file != null) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = exportShareType
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, file.name))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.logsviewer_save_success, viewModel.totalLineCount, file.name)
                                            )
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.logsviewer_save_failed))
                                        }
                                    }
                                }
                                if (isUnified) {
                                    viewModel.exportUnifiedLogs(context, onExportResult)
                                } else {
                                    viewModel.saveLogs(context, onExportResult)
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
                                    label = { Text(level.letter) },
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
                                    label = { Text(source.displayName) }
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

                        if (viewModel.unifiedDisplayedLogs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.logsviewer_empty_state),
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().weight(1f, fill = true),
                                contentPadding = PaddingValues(
                                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                )
                            ) {
                                items(viewModel.unifiedDisplayedLogs, key = { it.id }) { entry ->
                                    UnifiedLogLineRow(entry = entry)
                                }
                            }
                        }
                    }
                    }
                }
            }
    }

    ConfirmDialogHost(handle = clearConfirmDialog)

    LogsViewerSettingsSheet(
        visible = settingsVisible,
        onDismiss = { settingsVisible = false },
        viewModel = viewModel
    )
}

@Composable
private fun LogViewerStatusHeader(
    mode: LogsViewerViewModel.ViewerMode,
    lineCount: Int,
    paused: Boolean,
    filtered: Int
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (mode == LogsViewerViewModel.ViewerMode.LOGCAT) "System log" else "MaxManager log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (paused) "Stream paused" else "Live stream",
                style = MaterialTheme.typography.bodySmall,
                color = if (paused) colors.error else colors.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = lineCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filtered} shown",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogLineRow(
    entry: LogsViewerViewModel.LogEntry,
    showPid: Boolean,
    showTid: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val highlight = entry.level == LogsViewerViewModel.LogLevel.ERROR || entry.level == LogsViewerViewModel.LogLevel.ASSERT
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
            append(entry.time)
            append("  ")
        }
        if (showPid) {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                append(entry.pid.padStart(6))
                append(' ')
            }
        }
        if (showTid) {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                append(entry.tid.padStart(6))
                append(' ')
            }
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.Bold)) {
            append(entry.level.letter)
            append(' ')
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.SemiBold)) {
            append(entry.tag)
        }
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) { append(": ") }
        withStyle(SpanStyle(color = colorScheme.onSurface)) { append(entry.message) }
    }

    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) Modifier.background(entry.level.color.copy(alpha = 0.07f)) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 3.dp)
    )
}

@Composable
private fun UnifiedLogLineRow(entry: LogsViewerViewModel.UnifiedLogEntry) {
    val colorScheme = MaterialTheme.colorScheme
    val highlight = entry.level == LogsViewerViewModel.UnifiedLogLevel.ERROR ||
        entry.level == LogsViewerViewModel.UnifiedLogLevel.FATAL
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
            append(entry.timestamp.substringAfter(' ')) // time only, date rarely needed inline
            append("  ")
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.Bold)) {
            append(entry.level.letter)
            append(' ')
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.SemiBold)) {
            append(entry.source.displayName.ifEmpty { entry.rawTag })
        }
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) { append(": ") }
        if (entry.eventType != null) {
            withStyle(
                SpanStyle(
                    color = colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    background = colorScheme.secondaryContainer
                )
            ) {
                append(' ')
                append(entry.eventType)
                append(' ')
            }
            withStyle(SpanStyle(color = colorScheme.onSurface)) {
                append(' ')
                append(entry.message.substringAfter("EVENT=${entry.eventType}").trim())
            }
        } else {
            withStyle(SpanStyle(color = colorScheme.onSurface)) { append(entry.message) }
        }
    }

    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) Modifier.background(entry.level.color.copy(alpha = 0.07f)) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 3.dp)
    )
}

@Composable
private fun LogsViewerSettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: LogsViewerViewModel
) {
    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.logsviewer_settings_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
        )

        ExpressiveList(
            title = stringResource(R.string.logsviewer_buffers_section),
            content = LogsViewerViewModel.LogBuffer.entries.map { buffer ->
                {
                    ExpressiveCheckboxItem(
                        title = buffer.displayName,
                        checked = buffer in viewModel.selectedBuffers,
                        onCheckedChange = { checked ->
                            val updated = if (checked) {
                                viewModel.selectedBuffers + buffer
                            } else {
                                viewModel.selectedBuffers - buffer
                            }
                            viewModel.setBuffers(updated)
                        }
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.logsviewer_buffers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        ExpressiveList(
            title = stringResource(R.string.logsviewer_display_section),
            content = listOf(
                {
                    ExpressiveSwitchItem(
                        title = stringResource(R.string.logsviewer_show_pid),
                        checked = viewModel.showPid,
                        onCheckedChange = { viewModel.setShowPid(it) }
                    )
                },
                {
                    ExpressiveSwitchItem(
                        title = stringResource(R.string.logsviewer_show_tid),
                        checked = viewModel.showTid,
                        onCheckedChange = { viewModel.setShowTid(it) }
                    )
                }
            )
        )

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
