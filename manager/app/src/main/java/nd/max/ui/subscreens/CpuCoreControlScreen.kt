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

@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.viewmodel.CpuCoreControlViewModel
import nd.max.ui.viewmodel.CpuCoreRow
import nd.max.ui.viewmodel.CpuFrequencyControlState

/** Nodes this screen reads; shown as machine truth on condition panels. */
private const val CPU_SOURCES = "/sys/devices/system/cpu"

/** Keeps core counters left-to-right inside an RTL layout. */
private const val LTR_MARK = "\u200E"

/**
 * The view model reports one localized sentence for an apply attempt, so the
 * banner tone is derived from the wording it uses for a refusal.
 */
private val FREQUENCY_FAILURE_WORDS = listOf(
    "رفض", "تعذّر", "تعارض", "أعاد ضبط", "كِيان خارجي"
)

@Composable
fun CpuCoreControlScreen(
    navController: NavHostController,
    viewModel: CpuCoreControlViewModel = hiltViewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val accent = colorScheme.primary
    val screenTitle = stringResource(R.string.cpu_core_control_title)

    LaunchedEffect(Unit) { viewModel.loadState(context) }

    // The old screen opened with an AI banner, an insight paragraph and a
    // duplicate "CORE GRID / Manual Core Control" heading before any control,
    // then repeated the online count in a decision card that the hero already
    // showed. The chrome is now the title bar, and the safety note is in help.
    val condition = when (viewModel.isAvailable) {
        null -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = screenTitle,
            detail = stringResource(R.string.cpu_core_probe_detail),
            technicalDetail = CPU_SOURCES
        )

        false -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = screenTitle,
            detail = stringResource(R.string.cpu_core_unavailable),
            technicalDetail = CPU_SOURCES
        )

        else -> null
    }

    // Transient apply/verify feedback belongs in the scaffold banner slot,
    // not in a dismissible card wedged between two control sections.
    val actionMessage = viewModel.frequencyActionMessage
    val banner = actionMessage?.let { message ->
        val failed = FREQUENCY_FAILURE_WORDS.any { word -> message.contains(word) }
        MaxCondition(
            kind = if (failed) MaxConditionKind.Failed else MaxConditionKind.Applied,
            title = stringResource(
                if (failed) R.string.cpu_freq_action_failed
                else R.string.cpu_freq_action_applied
            ),
            detail = message,
            primaryActionLabel = stringResource(R.string.max_action_dismiss),
            onPrimaryAction = viewModel::consumeFrequencyActionMessage
        )
    }

    ScreenAccentProvider(accent) {
        MaxListScreen(
            title = screenTitle,
            subtitle = stringResource(R.string.cpu_core_control_subtitle),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Outlined.Memory,
            accent = accent,
            condition = condition,
            banner = banner,
            actions = {
                MaxHelpAction(
                    title = screenTitle,
                    body = stringResource(R.string.cpu_core_safety_note)
                )
            }
        ) {
            item {
                CpuHeroCard(
                    chipsetName = viewModel.chipsetName,
                    onlineCores = viewModel.onlineCores,
                    totalCores = viewModel.totalCores,
                    coreRows = viewModel.coreRows
                )
            }

            item {
                CpuFrequencyControlSection(
                    clusters = viewModel.clusters,
                    controls = viewModel.frequencyControls,
                    hasSessionChanges = viewModel.hasSessionFrequencyChanges,
                    onApply = viewModel::applyFrequencyLimits,
                    onRestore = viewModel::resetFrequencyLimits,
                    onRestoreSession = viewModel::restoreSessionFrequencyLimits
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
                MaxSection(title = stringResource(R.string.cpu_core_quick_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
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
                val rows = viewModel.coreRows
                    .filter { it.cluster.policyPath == cluster.policyPath }

                item {
                    MaxSection(title = clusterDisplayName(cluster)) {
                        ClusterCoreSummary(cluster = cluster, rows = rows)
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
                    MaxSection(title = stringResource(R.string.cpu_affinity_title)) {
                        Text(
                            text = stringResource(R.string.cpu_affinity_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
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
        }
    }
}

/**
 * Cluster line: which physical cores the cluster owns and how many are online,
 * assembled from the shared labels instead of a hand-written English sentence.
 */
@Composable
private fun ClusterCoreSummary(
    cluster: CpuTopologyUtil.CpuCluster,
    rows: List<CpuCoreRow>
) {
    val coresLabel = stringResource(R.string.cpu_core_cores_label)
    val onlineLabel = stringResource(R.string.cpu_core_online_label)
    val coreName = rows.firstOrNull()?.coreName
    val online = rows.count { it.online }
    val range = if (cluster.cores.size > 1) {
        "${cluster.cores.first()}\u2013${cluster.cores.last()}"
    } else {
        "${cluster.cores.first()}"
    }

    Text(
        text = buildString {
            if (coreName != null) append("$coreName \u00b7 ")
            append("$coresLabel $range \u00b7 $online/${cluster.cores.size} $onlineLabel")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CpuHeroCard(
    chipsetName: String,
    onlineCores: Int,
    totalCores: Int,
    coreRows: List<CpuCoreRow>
) {
    val scheme = MaterialTheme.colorScheme
    val availability = if (totalCores == 0) 0f else onlineCores.toFloat() / totalCores
    val allOnline = totalCores > 0 && onlineCores == totalCores
    val statusColor = if (allOnline) scheme.secondary else scheme.tertiary

    // The page opens with one line of facts and one picture: how much of this
    // CPU is up, and which cores are up. The circular gauge that used to sit
    // here restated the same ratio four times (value, unit, ring, caption) and
    // pushed the first real control below the fold on a phone, so it is now a
    // single summary line plus a 4dp bar.
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            IconBadge(icon = Icons.Outlined.Memory, tint = statusColor, size = 40)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(
                    text = chipsetName.ifBlank { stringResource(R.string.cpu_core_chipset_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$LTR_MARK$onlineCores/$totalCores$LTR_MARK " +
                        stringResource(R.string.cpu_core_online_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MaxSpace.hairline + 2.dp)
                .clip(RoundedCornerShape(MaxRadius.pill))
                .background(scheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(availability.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(statusColor)
            )
        }
        CoreGridMap(coreRows = coreRows)
    }
}

/**
 * The grid this screen is named after: one tile per CPU, tinted by its cluster
 * while online and dropped to the outline tone once the kernel parks it.
 */
@Composable
private fun CoreGridMap(coreRows: List<CpuCoreRow>) {
    val scheme = MaterialTheme.colorScheme
    val cpuLabel = stringResource(R.string.cpu_label)
    val offLabel = stringResource(R.string.cpu_core_row_offline)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        maxItemsInEachRow = 4
    ) {
        coreRows.forEach { row ->
            val accent = if (row.online) clusterAccent(row.cluster) else scheme.outline
            val tileShape = RoundedCornerShape(MaxRadius.row)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(
                        accent.copy(
                            alpha = if (row.online) MaxAlpha.toneContainerStrong else MaxAlpha.toneContainer
                        )
                    )
                    .border(
                        width = MaxSize.hairlineBorder,
                        color = accent.copy(
                            alpha = if (row.online) MaxAlpha.borderStrong else MaxAlpha.border
                        ),
                        shape = tileShape
                    )
                    .padding(vertical = MaxSpace.sm, horizontal = MaxSpace.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(
                    text = "$cpuLabel$LTR_MARK${row.cpu}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = if (row.online) row.cluster.shortTag else offLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Frequency limits: one group per controllable cluster.
 *
 * The old section stacked a description paragraph, a restore card, a gradient
 * card per cluster, a metric strip, a verification card and a conflict card:
 * six surfaces for one idea. The same facts now live in rows inside a single
 * hairline group, and the copy comes from resources instead of the source.
 */
@Composable
private fun CpuFrequencyControlSection(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    controls: Map<String, CpuFrequencyControlState>,
    hasSessionChanges: Boolean,
    onApply: (String, Long, Long) -> Unit,
    onRestore: (String) -> Unit,
    onRestoreSession: () -> Unit
) {
    val controllable = clusters.filter { controls[it.policyPath]?.canControl == true }
    if (controllable.isEmpty()) return

    MaxSection(
        title = stringResource(R.string.cpu_freq_section_title),
        description = stringResource(R.string.cpu_freq_section_desc)
    ) {
        if (hasSessionChanges) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.cpu_freq_session_title),
                    subtitle = stringResource(R.string.cpu_freq_session_desc),
                    icon = Icons.Outlined.History,
                    iconTone = MaxTone.Caution,
                    onClick = onRestoreSession
                )
            }
        }

        controllable.forEach { cluster ->
            CpuFrequencyControlGroup(
                cluster = cluster,
                control = controls.getValue(cluster.policyPath),
                onApply = { min, max -> onApply(cluster.policyPath, min, max) },
                onRestore = { onRestore(cluster.policyPath) }
            )
        }
    }
}

@Composable
private fun CpuFrequencyControlGroup(
    cluster: CpuTopologyUtil.CpuCluster,
    control: CpuFrequencyControlState,
    onApply: (Long, Long) -> Unit,
    onRestore: () -> Unit
) {
    val lower = control.hardwareMinKHz ?: control.minKHz ?: 0L
    val upper = control.hardwareMaxKHz ?: control.maxKHz ?: lower
    // Edit state is deliberately keyed on the policy only: the 3s live poll
    // updates control.minKHz/maxKHz, and keying remember() on those values
    // reset the sliders mid-drag (the old "hard to use" behavior).
    var editMin by remember(control.policyPath) { mutableStateOf(control.minKHz ?: lower) }
    var editMax by remember(control.policyPath) { mutableStateOf(control.maxKHz ?: upper) }
    val allowed = remember(control.availableFrequenciesKHz, lower, upper) {
        control.availableFrequenciesKHz.filter { it in lower..upper }.ifEmpty {
            listOf(lower, upper).distinct().sorted()
        }
    }
    val editable = allowed.size > 1 && lower < upper
    val activeMin = editMin.coerceIn(lower, upper)
    val activeMax = editMax.coerceIn(activeMin, upper)
    val isModified = activeMin != control.minKHz || activeMax != control.maxKHz
    var pinned by remember(control.policyPath) { mutableStateOf(false) }
    var pinSelection by remember(control.policyPath) { mutableStateOf<Long?>(null) }
    val effectivePin = pinSelection ?: activeMax
    val pinModified = pinned && (effectivePin != control.maxKHz || effectivePin != control.minKHz)
    val tableMissing = stringResource(R.string.cpu_freq_table_missing)
    val liveLimits = "${formatCpuFrequency(control.minKHz ?: 0L)} \u2013 " +
        formatCpuFrequency(control.maxKHz ?: 0L)

    MaxGroup {
        MaxRow(
            title = clusterDisplayName(cluster),
            subtitle = stringResource(
                R.string.cpu_freq_cluster_summary,
                cluster.cores.size,
                control.governor ?: stringResource(R.string.cpu_freq_governor_default)
            ),
            icon = clusterIcon(cluster),
            iconTone = MaxTone.Accent
        )

        MaxGroupDivider()

        Column(
            modifier = Modifier.padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.cpu_freq_current_label),
                    value = control.currentKHz?.let(::formatCpuFrequency),
                    source = control.policyPath
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.cpu_freq_range_label),
                    value = liveLimits
                )
            )
        }

        MaxGroupDivider()

        MaxSwitchRow(
            title = stringResource(R.string.cpu_freq_pin_title),
            checked = pinned,
            onCheckedChange = { pinned = it },
            subtitle = stringResource(
                if (pinned) R.string.cpu_freq_pin_on_desc else R.string.cpu_freq_pin_off_desc
            ),
            enabled = editable,
            lockedReason = tableMissing.takeIf { !editable }
        )

        if (editable) {
            if (pinned) {
                FrequencyStepRow(
                    title = stringResource(R.string.cpu_freq_pinned_label),
                    value = effectivePin,
                    options = allowed,
                    onValueChange = { selected -> pinSelection = selected }
                )
            } else {
                FrequencyStepRow(
                    title = stringResource(R.string.cpu_freq_min_label),
                    value = activeMin,
                    options = allowed.filter { it <= activeMax },
                    onValueChange = { selected -> editMin = selected.coerceAtMost(editMax) }
                )
                FrequencyStepRow(
                    title = stringResource(R.string.cpu_freq_max_label),
                    value = activeMax,
                    options = allowed.filter { it >= activeMin },
                    onValueChange = { selected -> editMax = selected.coerceAtLeast(editMin) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.xs
                ),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.weight(1f),
                enabled = lower < upper
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
                Spacer(Modifier.width(MaxSpace.xs))
                Text(stringResource(R.string.cpu_freq_hardware_range))
            }
            Button(
                onClick = {
                    if (pinned) onApply(effectivePin, effectivePin) else onApply(activeMin, activeMax)
                },
                modifier = Modifier.weight(1f),
                enabled = editable && (if (pinned) pinModified else isModified)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
                Spacer(Modifier.width(MaxSpace.xs))
                Text(
                    stringResource(
                        if (pinned) R.string.cpu_freq_pin_action else R.string.dialog_apply
                    )
                )
            }
        }

        val notes = buildList {
            control.verification?.let { addAll(frequencyVerificationLines(it)) }
            if (control.externalConflict) add(stringResource(R.string.cpu_freq_conflict_detail))
            if (control.sessionOwned) add(stringResource(R.string.cpu_freq_session_owned_note))
        }
        if (notes.isNotEmpty()) {
            MaxBullets(
                lines = notes,
                tone = when {
                    control.externalConflict -> MaxTone.Critical
                    control.verification?.verified == false -> MaxTone.Caution
                    else -> MaxTone.Accent
                },
                modifier = Modifier.padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.xs
                )
            )
        }
    }
}

/**
 * Verification result as reading lines: what was requested, what the hardware
 * reported back, and why it differs. The old card printed three frequency
 * ranges; the live-limits row above already carries the current one.
 */
@Composable
private fun frequencyVerificationLines(
    verification: nd.max.ui.viewmodel.CpuFrequencyVerification
): List<String> {
    val headline = when {
        verification.verified && verification.reassertions > 0 -> stringResource(
            R.string.cpu_freq_verified_reasserted,
            verification.reassertions
        )

        verification.verified -> stringResource(R.string.cpu_freq_verified)
        verification.writeAccepted -> stringResource(R.string.cpu_freq_overridden)
        else -> stringResource(R.string.cpu_freq_rejected)
    }
    val requested = "${formatCpuFrequency(verification.requestedMinKHz)} \u2013 " +
        formatCpuFrequency(verification.requestedMaxKHz)
    val measured = "${formatCpuFrequency(verification.actualMinKHz ?: 0L)} \u2013 " +
        formatCpuFrequency(verification.actualMaxKHz ?: 0L)

    return buildList {
        add(headline)
        add(stringResource(R.string.cpu_freq_verification_detail, requested, measured))
        if (!verification.verified) {
            add(
                stringResource(
                    if (verification.writeAccepted) R.string.cpu_freq_overridden_explain
                    else R.string.cpu_freq_rejected_explain
                )
            )
        }
    }
}

/** One frequency step picked from the kernel's own table, as a house slider row. */
@Composable
private fun FrequencyStepRow(
    title: String,
    value: Long,
    options: List<Long>,
    onValueChange: (Long) -> Unit
) {
    val safeOptions = options.ifEmpty { listOf(value) }
    val index = safeOptions.indexOf(value).takeIf { it >= 0 } ?: 0
    MaxSliderRow(
        title = title,
        value = index.toFloat(),
        onValueChange = { position ->
            onValueChange(safeOptions[position.toInt().coerceIn(0, safeOptions.lastIndex)])
        },
        valueText = formatCpuFrequency(safeOptions[index]),
        valueRange = 0f..safeOptions.lastIndex.toFloat().coerceAtLeast(1f),
        steps = (safeOptions.size - 2).coerceAtLeast(0)
    )
}

private fun formatCpuFrequency(kHz: Long): String = when {
    kHz <= 0L -> "—"
    kHz >= 1_000_000L -> String.format(java.util.Locale.US, "%.2f GHz", kHz / 1_000_000f)
    else -> "${kHz / 1000} MHz"
}

@Composable
private fun CpuCoresSection(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    coreRows: List<CpuCoreRow>,
    clusterMaxFreqMhz: Map<String, Int>
) {
    MaxSection(title = stringResource(R.string.cpu_core_cores_label)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            maxItemsInEachRow = 3
        ) {
            clusters.forEach { cluster ->
                CpuClusterSummaryCard(
                    modifier = Modifier.weight(1f, fill = true),
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
    val frequency = if (maxFreqMhz > 0) String.format(java.util.Locale.US, "%.2f GHz", maxFreqMhz / 1000f) else "—"

    val online = rows.count { it.online }

    // Three rows instead of five, and the title reserves two of them so the
    // three summary tiles keep one height whatever the cluster name is. The
    // previous card stacked icon / title / GHz / "3 / 3" / "Online" vertically,
    // which made a summary tile taller than the controls it summarises and left
    // the three tiles visibly ragged next to each other.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.group),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            MaxSize.hairlineBorder,
            accent.copy(alpha = MaxAlpha.borderStrong)
        )
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
            ) {
                Icon(
                    imageVector = clusterIcon(cluster),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(MaxSize.iconGlyph)
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = frequency,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Text(
                text = "$LTR_MARK$online/${rows.size}$LTR_MARK " +
                    stringResource(R.string.cpu_core_online_label),
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
    // A titled section holding one switch row, the shape every other redesigned
    // screen uses, instead of a card that carried its own section title inside
    // it and stretched its icon and its switch to opposite edges.
    MaxSection(title = stringResource(R.string.cpu_core_manual_control)) {
        MaxGroup {
            MaxSwitchRow(
                title = stringResource(R.string.cpu_manual_enable_title),
                subtitle = stringResource(R.string.cpu_core_manual_control_desc),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                icon = Icons.Outlined.Tune,
                iconTone = MaxTone.Accent
            )
        }
    }
}

/** Cluster name from resources, so section titles and tiles agree in every locale. */
@Composable
private fun clusterDisplayName(cluster: CpuTopologyUtil.CpuCluster): String = when (cluster.shortTag) {
    "PRIME" -> stringResource(R.string.cpu_cluster_prime)
    "GOLD" -> stringResource(R.string.cpu_cluster_gold)
    "SILVER" -> stringResource(R.string.cpu_cluster_silver)
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
 * with the tweak workspace and this per-preset coloring is specific to core
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
            .padding(vertical = 12.dp, horizontal = MaxSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(icon = icon, tint = accent, size = 32)
        Spacer(Modifier.height(MaxSpace.sm))
        // Two reserved lines: the longest preset name must wrap instead of being
        // cut mid-word, which is what "Performanc" was in the old one-line tile.
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
