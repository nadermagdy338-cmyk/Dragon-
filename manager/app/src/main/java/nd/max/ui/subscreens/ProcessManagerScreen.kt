/*
 * Live process monitor - top processes by CPU/RAM, force-stop / kill actions,
 * and a floating overlay toggle. Adapted from ZKM's ui/proces/ProcessManagerScreen.kt
 * (which leaned on Haze glass cards, donut/bar Canvas charts, and its own
 * SettingsViewModel for theme), but rebuilt on MaxManager's own ExpressiveList /
 * ExpressiveListItem / ConfirmDialog / CustomBottomSheet components instead,
 * and driven by ProcessManagerViewModel + ProcessMonitorUtil (libsu Shell).
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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.mainscreens.DashCardWrapper
import nd.max.ui.mainscreens.GlowLinearBar
import nd.max.ui.util.ProcessInfo
import nd.max.ui.util.ProcessSortType
import nd.max.ui.viewmodel.ProcessManagerViewModel

@Composable
fun ProcessManagerScreen(
    navController: NavController,
    viewModel: ProcessManagerViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedProcess by remember { mutableStateOf<ProcessInfo?>(null) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val forceStopDialog = rememberConfirmDialog(
        onConfirm = { selectedProcess?.let { viewModel.forceStopApp(context, it) }; selectedProcess = null },
        onDismiss = {}
    )
    val killDialog = rememberConfirmDialog(
        onConfirm = { selectedProcess?.let { viewModel.killProcess(context, it) }; selectedProcess = null },
        onDismiss = {}
    )

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (hasOverlayPermission) startProcessOverlayService(context)
    }

    fun toggleFloatingMonitor() {
        if (hasOverlayPermission) {
            startProcessOverlayService(context)
        } else {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.processmgr_overlay_permission_needed),
                    actionLabel = context.getString(R.string.open_settings)
                )
                if (result == SnackbarResult.ActionPerformed) {
                    overlayPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.startMonitoring(context) }

    LaunchedEffect(viewModel.actionResult) {
        viewModel.actionResult?.let { result ->
            val (kind, name) = result.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val message = when (kind) {
                "killed" -> context.getString(R.string.processmgr_result_killed, name)
                "kill_failed" -> context.getString(R.string.processmgr_result_kill_failed, name)
                "stopped" -> context.getString(R.string.processmgr_result_stopped, name)
                else -> context.getString(R.string.processmgr_result_stop_failed, name)
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionResult()
        }
    }

    ScreenAccentProvider(colorScheme.primary) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    MaxManagerSubScreenTopBar(
                        scrollBehavior = scrollBehavior,
                        title = stringResource(R.string.processmgr_title),
                        onBack = { navController.popBackStack() },
                        accentIcon = Icons.Filled.Memory,
                        accent = colorScheme.primary,
                        actions = {
                            IconButton(onClick = { toggleFloatingMonitor() }) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.processmgr_overlay_title)
                                )
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = colorScheme.surface
            ) { innerPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.maxAdaptiveContentWidth(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                    item {
                        ProcessOverviewHeader(
                            processCount = viewModel.processList.size,
                            userCount = viewModel.userProcessCount,
                            systemCount = viewModel.systemProcessCount,
                            sortType = viewModel.sortType,
                            onOverlay = { toggleFloatingMonitor() }
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    item {
                        ResourceUsageChart(
                            processList = viewModel.processList,
                            sortType = viewModel.sortType
                        )
                        Spacer(Modifier.height(12.dp))
                        ProcessDistributionChart(
                            userCount = viewModel.userProcessCount,
                            systemCount = viewModel.systemProcessCount
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.surfaceContainerLow
                        ) {
                            ProcessSortAndLimitRow(
                            currentSort = viewModel.sortType,
                            currentLimit = viewModel.limitOption,
                            onSortChange = { viewModel.setSort(it) },
                            onLimitChange = { viewModel.setLimit(it) }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Running processes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap a process for actions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = viewModel.processList.size.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.primary
                            )
                        }
                    }

                    if (viewModel.isLoading && viewModel.processList.isEmpty()) {
                        item { SectionLoadingIndicator() }
                    } else if (viewModel.processList.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.processmgr_no_processes), color = colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(viewModel.processList, key = { it.pid }) { process ->
                            ProcessRow(process, viewModel.sortType, onClick = { selectedProcess = process })
                        }
                    }
                }
            }
        }

    ConfirmDialogHost(handle = forceStopDialog)
    ConfirmDialogHost(handle = killDialog)

    CustomBottomSheet(
        visible = selectedProcess != null,
        onDismiss = { selectedProcess = null }
    ) {
        selectedProcess?.let { process ->
            ProcessDetailSheetContent(
                process = process,
                onForceStop = {
                    forceStopDialog.showConfirm(
                        title = context.getString(R.string.processmgr_force_stop_confirm_title, process.appName),
                        content = context.getString(R.string.processmgr_force_stop_confirm_desc, process.appName),
                        confirm = context.getString(R.string.processmgr_action_force_stop),
                        dismiss = context.getString(R.string.no)
                    )
                },
                onKill = {
                    killDialog.showConfirm(
                        title = context.getString(R.string.processmgr_kill_confirm_title),
                        content = context.getString(R.string.processmgr_kill_confirm_desc, process.pid),
                        confirm = context.getString(R.string.processmgr_action_kill),
                        dismiss = context.getString(R.string.no)
                    )
                },
                onAppInfo = {
                    nd.max.ui.util.DebloatFreezeUtil.openAppSystemSettings(context, process.packageName)
                },
                onClose = { selectedProcess = null }
            )
        }
    }
}

private fun startProcessOverlayService(context: android.content.Context) {
    val intent = Intent(context, nd.max.service.ProcessOverlayService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@Composable
private fun ProcessOverviewHeader(
    processCount: Int,
    userCount: Int,
    systemCount: Int,
    sortType: ProcessSortType,
    onOverlay: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Process Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Live process activity and resource usage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onOverlay) {
                    Icon(
                        Icons.Outlined.PictureInPictureAlt,
                        contentDescription = stringResource(R.string.processmgr_overlay_title)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProcessMetric("Running", processCount.toString(), Modifier.weight(1f))
                ProcessMetric("Apps", userCount.toString(), Modifier.weight(1f))
                ProcessMetric("System", systemCount.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = cs.primaryContainer
                ) {
                    Text(
                        if (sortType == ProcessSortType.CPU) "CPU" else "RAM",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    "Refreshes every 3 seconds",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProcessMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResourceUsageChart(
    processList: List<ProcessInfo>,
    sortType: ProcessSortType
) {
    val accent = MaterialTheme.colorScheme.primary
    DashCardWrapper(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (sortType == ProcessSortType.CPU) Icons.Rounded.Memory else Icons.Rounded.Storage,
                tint = accent,
                size = 22
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (sortType == ProcessSortType.CPU) stringResource(R.string.processmgr_sort_cpu) else stringResource(R.string.processmgr_sort_ram),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val topProcesses = processList.take(4)
        val maxValue = topProcesses.maxOfOrNull {
            if (sortType == ProcessSortType.CPU) it.cpu.replace("%", "").toFloatOrNull() ?: 0f
            else it.res.replace("M", "").replace("K", "").toFloatOrNull() ?: 0f
        } ?: 1f

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topProcesses.forEach { process ->
                val rawVal = if (sortType == ProcessSortType.CPU) {
                    process.cpu.replace("%", "").toFloatOrNull() ?: 0f
                } else {
                    process.res.replace("M", "").replace("K", "").toFloatOrNull() ?: 0f
                }
                val progress = (rawVal / maxValue).coerceIn(0f, 1f)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = process.appName,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(50.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlowLinearBar(fraction = progress, accent = accent, height = 5.dp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ProcessDistributionChart(
    userCount: Int,
    systemCount: Int
) {
    val total = userCount + systemCount
    val userRatio = if (total > 0) userCount.toFloat() / total else 0f
    val userColor = MaterialTheme.colorScheme.primary

    val animatedUserRatio by androidx.compose.animation.core.animateFloatAsState(
        targetValue = userRatio,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f),
        label = "donut"
    )

    DashCardWrapper(modifier = Modifier.fillMaxWidth().height(160.dp), accent = userColor) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                val systemColor = MaterialTheme.colorScheme.surfaceVariant

                // Soft glow behind the ring, same layered-alpha technique as IconBadge/RadialGaugeCard.
                Box(Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape).background(userColor.copy(alpha = 0.10f)))

                androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2

                    drawCircle(
                        color = systemColor,
                        radius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                    )

                    drawArc(
                        color = userColor,
                        startAngle = -90f,
                        sweepAngle = animatedUserRatio * 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }

                Text(
                    text = total.toString(),
                    style = nd.max.ui.theme.MonoValueStyleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot("User", userCount, userColor)
                LegendDot("Sys", systemCount, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LegendDot(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = count.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProcessSortAndLimitRow(
    currentSort: ProcessSortType,
    currentLimit: Int,
    onSortChange: (ProcessSortType) -> Unit,
    onLimitChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentSort == ProcessSortType.CPU,
            onClick = { onSortChange(ProcessSortType.CPU) },
            label = { Text(stringResource(R.string.processmgr_sort_cpu)) },
            leadingIcon = { Icon(Icons.Filled.Speed, null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50)
        )
        FilterChip(
            selected = currentSort == ProcessSortType.RAM,
            onClick = { onSortChange(ProcessSortType.RAM) },
            label = { Text(stringResource(R.string.processmgr_sort_ram)) },
            leadingIcon = { Icon(Icons.Filled.Memory, null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50)
        )
        FilterChip(
            selected = false,
            onClick = {
                val next = when (currentLimit) { 10 -> 20; 20 -> 50; else -> 10 }
                onLimitChange(next)
            },
            label = { Text(stringResource(R.string.processmgr_limit_format, currentLimit)) },
            leadingIcon = { Icon(Icons.Outlined.FilterList, null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50)
        )
    }
}

@Composable
private fun ProcessRow(process: ProcessInfo, sortType: ProcessSortType, onClick: () -> Unit) {
    ExpressiveListItem(
        onClick = onClick,
        leadingContent = { ProcessIcon(process) },
        headlineContent = {
            Text(process.appName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(process.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val value = if (sortType == ProcessSortType.CPU) process.cpu else process.res
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.processmgr_pid_format, process.pid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    )
}

@Composable
private fun ProcessIcon(process: ProcessInfo) {
    val bitmap = remember(process.pid, process.icon) {
        val drawable = process.icon ?: return@remember null
        val size = 96
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = process.appName, modifier = Modifier.size(30.dp))
            } else {
                Icon(Icons.Outlined.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProcessDetailSheetContent(
    process: ProcessInfo,
    onForceStop: () -> Unit,
    onKill: () -> Unit,
    onAppInfo: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProcessIcon(process)
        Spacer(Modifier.height(12.dp))
        Text(process.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(process.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DetailBadge(stringResource(R.string.cpu), process.cpu)
            DetailBadge(stringResource(R.string.ram), process.res)
            DetailBadge(stringResource(R.string.pid), process.pid)
        }
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            nd.max.ui.component.StudioTonalButton(onClick = onAppInfo, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.processmgr_action_app_info))
            }
            nd.max.ui.component.StudioButton(
                onClick = onKill,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.processmgr_action_kill))
            }
        }
        Spacer(Modifier.height(8.dp))
        nd.max.ui.component.StudioTextButton(onClick = onForceStop, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.processmgr_action_force_stop), color = MaterialTheme.colorScheme.outline)
        }
        nd.max.ui.component.StudioTextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.processmgr_dialog_close))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
