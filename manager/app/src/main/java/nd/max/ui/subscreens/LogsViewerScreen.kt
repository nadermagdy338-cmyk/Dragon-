/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * شاشة عرض السجل: تبويبان (logcat الموحّد ومخازنه)، وترشيح بالمستوى والمصدر والمخزن،
 * وإيقاف/متابعة البثّ، وتصدير ما ظهر. والقراءة كلها من `LogsViewerViewModel`.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import nd.max.ui.design.MaxScrollRow
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
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.R
import nd.max.core.diagnostics.LogArea
import nd.max.ui.component.*
import nd.max.ui.design.MaxSplitScreen
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
        MaxSplitScreen(
            title = stringResource(R.string.logsviewer_title),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Filled.Terminal,
            accent = colorScheme.secondary,
            snackbarHostState = snackbarHostState,
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
        ) {
                Column(modifier = Modifier.fillMaxSize()) {
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
                            shape = RoundedCornerShape(MaxRadius.inset),
                            // الحشو الأفقي ملك الهيكل (`MaxSplitScreen` ← `MaxSpace.gutter`)، وكان
                            // 16dp هنا يُضاف فوقه ⇒ 36dp، فيبدو حقل البحث مُزاحًا عن ترويسة الحالة
                            // والتبويبات فوقه (20dp) — وهذا سبب «الفجوات الجانبية« الملحوظ.
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaxSpace.sm)
                        )

                        MaxScrollRow(spacing = MaxSpace.sm) {
                            LogsViewerViewModel.LogLevel.entries.forEach { level ->
                                val selected = level in viewModel.selectedLevels
                                // Resolved here, not inside `semantics {}`: a composable call is not
                                // allowed from that non-composable lambda.
                                val levelDescription = stringResource(level.labelRes)
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleLevel(level) },
                                    // الحرف وحده لا يُقرأ لقارئ الشاشة، والاسم الكامل في المورد.
                                    label = { Text(level.letter) },
                                    modifier = Modifier.semantics {
                                        contentDescription = levelDescription
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
                                    .padding(vertical = MaxSpace.sm)
                                    .clip(RoundedCornerShape(MaxRadius.control))
                                    .background(colorScheme.secondaryContainer)
                                    .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm)
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
                                // الهيكل يحجز أسفل الصفحة أصلًا (‏`floatingBottomBarPadding` + حشو
                                // شريط النظام)، فإضافة `16.dp + inset` هنا تحجز الحجز مرّتين.
                                contentPadding = PaddingValues(bottom = MaxSpace.sm)
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
                            shape = RoundedCornerShape(MaxRadius.inset),
                            // الحشو الأفقي ملك الهيكل (`MaxSplitScreen` ← `MaxSpace.gutter`)، وكان
                            // 16dp هنا يُضاف فوقه ⇒ 36dp، فيبدو حقل البحث مُزاحًا عن ترويسة الحالة
                            // والتبويبات فوقه (20dp) — وهذا سبب «الفجوات الجانبية« الملحوظ.
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaxSpace.sm)
                        )

                        MaxScrollRow(spacing = MaxSpace.sm) {
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

                        MaxScrollRow(spacing = MaxSpace.sm) {
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
                                .padding(vertical = MaxSpace.xs),
                            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
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
                                .padding(vertical = MaxSpace.xs),
                            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
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
                                    .padding(vertical = MaxSpace.sm)
                                    .clip(RoundedCornerShape(MaxRadius.control))
                                    .background(colorScheme.secondaryContainer)
                                    .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm)
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

                        // الحجز الأسفل يملكه الهيكل مرّة واحدة — لا مضاعفة هنا.
                        val bottomPadding = PaddingValues(bottom = MaxSpace.sm)
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

