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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.ExpressiveTile
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.viewmodel.CpuCoreControlViewModel
import nd.max.ui.viewmodel.CpuCoreRow

@Composable
fun CpuCoreControlScreen(
    navController: NavController,
    viewModel: CpuCoreControlViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadState(context) }

    ScreenAccentProvider(colorScheme.primary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { CpuCoreControlTopAppBar(scrollBehavior, onBack = { navController.popBackStack() }) },
            containerColor = colorScheme.surface
        ) { innerPadding ->
            when (viewModel.isAvailable) {
                null -> Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { SectionLoadingIndicator() }

                false -> Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cpu_core_unavailable),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                true -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 10.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        nd.max.ui.component.MaxAiActiveBanner(navController = navController)
                    }
                    item {
                        MaxManagerInsight(
                            text = "تحكم مباشر في سياسات الأنوية وتوزيعها بدون خلطها مع إعدادات الأداء العامة.",
                            accent = colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                    item {
                        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                text = "CORE GRID",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Manual Core Control",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.primary
                            )
                        }
                    }

                    item {
                        CpuHeroCard(
                            chipsetName = viewModel.chipsetName,
                            onlineCores = viewModel.onlineCores,
                            totalCores = viewModel.totalCores
                        )
                    }

                    item {
                        MaxDecisionCard(
                            state = "${viewModel.onlineCores}/${viewModel.totalCores} cores online",
                            guidance = if (viewModel.manualControlEnabled) "Manual control is active. Changes below directly affect core online state." else "Manual control is off. The kernel remains responsible for normal core policy.",
                            icon = Icons.Outlined.Memory
                        )
                    }

                    item {
                        CpuCoresSection(
                            clusters = viewModel.clusters,
                            coreRows = viewModel.coreRows,
                            clusterMaxFreqMhz = viewModel.clusterMaxFreqMhz
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CpuSectionTitle("QUICK PRESETS")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CpuCoreControlViewModel.QUICK_CONFIGS.forEach { config ->
                                    CoreQuickConfigTile(
                                        modifier = Modifier.weight(1f),
                                        icon = quickConfigIcon(config.id),
                                        label = quickConfigLabel(config.id),
                                        description = quickConfigDesc(config.id),
                                        accent = quickConfigAccent(config.id),
                                        onClick = { viewModel.applyQuickConfig(config.id) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        CpuManualControlCard(
                            enabled = viewModel.manualControlEnabled,
                            onEnabledChange = viewModel::setManualControlEnabled
                        )
                    }

                    viewModel.clusters.forEach { cluster ->
                        val rows = viewModel.coreRows.filter { it.cluster.policyPath == cluster.policyPath }
                        val onlineInCluster = rows.count { it.online }
                        val coreName = rows.firstOrNull()?.coreName
                        val rangeText = if (cluster.cores.size > 1) {
                            "${cluster.cores.first()}–${cluster.cores.last()}"
                        } else {
                            "${cluster.cores.first()}"
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CpuSectionTitle(clusterDisplayName(cluster))
                                Text(
                                    text = buildString {
                                        if (coreName != null) append("$coreName · ")
                                        append("Cores $rangeText · $onlineInCluster/${cluster.cores.size} Online")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                ScreenAccentProvider(clusterAccent(cluster)) {
                                    ExpressiveList(
                                        content = rows.map { row ->
                                            {
                                                CoreRowItem(
                                                    row = row,
                                                    enabled = viewModel.manualControlEnabled && !row.isMaster,
                                                    onToggle = { viewModel.setCoreOnline(row.cpu, it) }
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (viewModel.cpusetGroups.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CpuSectionTitle(stringResource(R.string.cpu_affinity_title))
                                Text(
                                    text = stringResource(R.string.cpu_affinity_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                ExpressiveList(
                                    content = viewModel.cpusetGroups.map { group ->
                                        {
                                            CpusetGroupRow(
                                                group = group,
                                                totalCores = viewModel.totalCores,
                                                onApply = { cores -> viewModel.setCpusetGroupCores(group, cores) }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.cpu_core_safety_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun CpuHeroCard(
    chipsetName: String,
    onlineCores: Int,
    totalCores: Int
) {
    val scheme = MaterialTheme.colorScheme
    val vendor = remember(chipsetName) {
        when {
            chipsetName.contains("snapdragon", true) || chipsetName.contains("qualcomm", true) -> "Snapdragon"
            chipsetName.contains("mediatek", true) || chipsetName.contains("dimensity", true) -> "MediaTek"
            chipsetName.contains("exynos", true) -> "Exynos"
            chipsetName.contains("tensor", true) -> "Tensor"
            chipsetName.contains("unisoc", true) -> "Unisoc"
            else -> "SoC"
        }
    }
    val displayName = chipsetName.ifBlank { "Unknown SoC" }
    val imageRes = when (vendor) {
        "MediaTek" -> R.drawable.cpu_soc_mediatek
        "Snapdragon" -> R.drawable.cpu_soc_snapdragon
        else -> R.drawable.cpu_soc_generic
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
        ) {
            // Full-bleed photographic SoC artwork, matching the reference hero style.
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Keep the left information readable without turning the artwork into a separate floating chip.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .background(
                        Brush.horizontalGradient(
                            0f to scheme.surface.copy(alpha = 0.98f),
                            0.52f to scheme.surface.copy(alpha = 0.86f),
                            1f to Color.Transparent
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp, end = 12.dp)
                    .widthIn(max = 235.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = vendor,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2
                )
                Surface(
                    color = scheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LivePulseDot(color = scheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Online · $onlineCores / $totalCores",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CpuCoresSection(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    coreRows: List<CpuCoreRow>,
    clusterMaxFreqMhz: Map<String, Int>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CpuSectionTitle("CPU CORES")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            clusters.forEach { cluster ->
                CpuClusterSummaryCard(
                    modifier = Modifier.weight(1f),
                    cluster = cluster,
                    rows = coreRows.filter { it.cluster.policyPath == cluster.policyPath },
                    maxFreqMhz = clusterMaxFreqMhz[cluster.policyPath] ?: 0
                )
            }
        }
    }
}

@Composable
private fun CpuClusterSummaryCard(
    modifier: Modifier,
    cluster: CpuTopologyUtil.CpuCluster,
    rows: List<CpuCoreRow>,
    maxFreqMhz: Int
) {
    val scheme = MaterialTheme.colorScheme
    val accent = clusterAccent(cluster)
    val title = clusterDisplayName(cluster)
    val frequency = if (maxFreqMhz > 0) String.format("%.2f GHz", maxFreqMhz / 1000f) else "—"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = clusterIcon(cluster),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 2
            )
            Text(
                text = frequency,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Text(
                text = "${rows.count { it.online }} / ${rows.size}",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface
            )
            Text(
                text = "Online",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CpuManualControlCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CpuSectionTitle("MANUAL CORE CONTROL")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaxSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Manual Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Enable to manually power on/off individual processor cores. Session only — resets on reboot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconBadge(icon = Icons.Outlined.Tune, tint = scheme.primary, size = 44)
            }
        }
    }
}

private fun clusterDisplayName(cluster: CpuTopologyUtil.CpuCluster): String = when (cluster.shortTag) {
    "PRIME" -> "Prime Core"
    "GOLD" -> "Performance Cores"
    "SILVER" -> "Efficiency Cores"
    else -> cluster.label
}

@Composable
private fun quickConfigIcon(id: String) = when (id) {
    "all_on" -> Icons.Filled.Bolt
    "balanced" -> Icons.Outlined.Balance
    "power_saver" -> Icons.Outlined.EnergySavingsLeaf
    else -> Icons.Outlined.Tune
}

@Composable
private fun quickConfigLabel(id: String): String = when (id) {
    "all_on" -> "Performance"
    "balanced" -> "Balanced"
    "power_saver" -> "Power Saver"
    else -> id
}

@Composable
private fun quickConfigDesc(id: String): String = when (id) {
    "all_on" -> "All cores"
    "balanced" -> "Top cluster"
    "power_saver" -> "Efficiency"
    else -> ""
}

/**
 * Fixed identity color per cluster tier -- amber/gold for the Prime
 * supercore, blue for the mid Performance cluster(s), green for Efficiency
 * -- keyed off the shortTag CpuTopologyUtil.detectClusters() already
 * assigns, so this reads correctly whether the chip has 2, 3, or 4
 * clusters, the same way that function's own label/tag logic does. Falls
 * back to the screen's theme accent for the single-cluster case.
 */
@Composable
private fun clusterAccent(cluster: CpuTopologyUtil.CpuCluster): Color = when (cluster.shortTag) {
    "PRIME" -> MaterialTheme.colorScheme.tertiary
    "GOLD" -> MaterialTheme.colorScheme.primary
    "SILVER" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.tertiary
}

private fun clusterIcon(cluster: CpuTopologyUtil.CpuCluster): ImageVector = when (cluster.shortTag) {
    "PRIME" -> Icons.Filled.Bolt
    "GOLD" -> Icons.Outlined.Balance
    "SILVER" -> Icons.Outlined.EnergySavingsLeaf
    else -> Icons.Outlined.Memory
}

@Composable
private fun quickConfigAccent(id: String): Color = when (id) {
    "all_on" -> MaterialTheme.colorScheme.tertiary
    "balanced" -> MaterialTheme.colorScheme.primary
    "power_saver" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

/**
 * A quick-config preset as its own colored tile -- icon in a glow badge,
 * bold label, one-line description -- instead of the screen falling back
 * to ExpressiveTile's generic neutral treatment. Kept local to this screen
 * (not a change to ExpressiveTile itself) since that component is shared
 * with TweakScreen.kt and this per-preset coloring is specific to core
 * presets, not something every ExpressiveTile caller should inherit.
 */
@Composable
private fun CoreQuickConfigTile(
    icon: ImageVector,
    label: String,
    description: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), MaterialTheme.colorScheme.surfaceContainerLow)))
            .border(1.dp, accent.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(icon = icon, tint = accent, size = 36)
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CoreRowItem(
    row: CpuCoreRow,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val title = if (row.isMaster) {
        "CPU${row.cpu} \u00b7 " + stringResource(R.string.cpu_core_master_tag)
    } else {
        "CPU${row.cpu} \u00b7 ${row.cluster.shortTag}"
    }
    val summary = when {
        row.isMaster -> stringResource(R.string.cpu_core_master_note)
        row.online -> stringResource(R.string.cpu_core_row_online)
        else -> stringResource(R.string.cpu_core_row_offline)
    }
    ExpressiveSwitchItem(
        icon = clusterIcon(row.cluster),
        title = title,
        summary = summary,
        checked = row.online,
        enabled = enabled,
        onCheckedChange = onToggle
    )
}

@Composable
fun CpuCoreControlTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.cpu_core_control_title),
        onBack = onBack,
        accentIcon = Icons.Filled.Memory,
        accent = MaterialTheme.colorScheme.tertiary
    )
}

/**
 * One cpuset scheduling group ("top-app", "foreground", ...) with its current
 * core assignment as a summary, and a dialog to pick which cores that group
 * is allowed to schedule threads on. Adapted from ZKM's cpuset affinity tool;
 * ported onto MaxManager's own ExpressiveList / CustomContentDialog components
 * instead of copying ZKM's UI code directly.
 */
@Composable
private fun CpusetGroupRow(
    group: CpuTopologyUtil.CpusetGroup,
    totalCores: Int,
    onApply: (List<Int>) -> Unit
) {
    var dialogVisible by remember { mutableStateOf(false) }
    var pendingSelection by remember(group.cores) { mutableStateOf(group.cores.toSet()) }

    val rangeText = summarizeCoreList(group.cores)

    ExpressiveListItem(
        onClick = {
            pendingSelection = group.cores.toSet()
            dialogVisible = true
        },
        leadingContent = { LeadingIcon(icon = Icons.Outlined.Hub) },
        headlineContent = { Text(group.label) },
        supportingContent = { Text(stringResource(R.string.cpu_affinity_cores_summary, rangeText)) }
    )

    CustomContentDialog(
        visible = dialogVisible,
        title = group.label,
        onDismiss = { dialogVisible = false },
        onConfirm = {
            onApply(pendingSelection.toList())
            dialogVisible = false
        },
        confirmEnabled = pendingSelection.isNotEmpty()
    ) {
        Column {
            Text(
                text = stringResource(R.string.cpu_affinity_dialog_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cpu in 0 until totalCores) {
                    val selected = pendingSelection.contains(cpu)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            pendingSelection = if (selected) {
                                pendingSelection - cpu
                            } else {
                                pendingSelection + cpu
                            }
                        },
                        label = { Text("CPU$cpu") }
                    )
                }
            }
        }
    }
}

/** Collapses a sorted core list like [0,1,2,3,6,7] into "0\u20133,6\u20137" for the summary line. */
private fun summarizeCoreList(cores: List<Int>): String {
    if (cores.isEmpty()) return "\u2014"
    val sorted = cores.sorted()
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var prev = sorted.first()
    for (c in sorted.drop(1)) {
        if (c == prev + 1) {
            prev = c
        } else {
            ranges.add(start..prev)
            start = c
            prev = c
        }
    }
    ranges.add(start..prev)
    return ranges.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}\u2013${it.last}" }
}
