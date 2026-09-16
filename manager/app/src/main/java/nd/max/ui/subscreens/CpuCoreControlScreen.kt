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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.viewmodel.CpuCoreControlViewModel
import nd.max.ui.viewmodel.CpuCoreRow
import nd.max.ui.viewmodel.CpuFrequencyControlState

@Composable
fun CpuCoreControlScreen(
    navController: NavHostController,
    viewModel: CpuCoreControlViewModel = hiltViewModel()
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
                            totalCores = viewModel.totalCores,
                            coreRows = viewModel.coreRows
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
                        CpuFrequencyControlSection(
                            clusters = viewModel.clusters,
                            controls = viewModel.frequencyControls,
                            hasSessionChanges = viewModel.hasSessionFrequencyChanges,
                            onApply = viewModel::applyFrequencyLimits,
                            onRestore = viewModel::resetFrequencyLimits,
                            onRestoreSession = viewModel::restoreSessionFrequencyLimits
                        )
                    }

                    viewModel.frequencyActionMessage?.let { message ->
                        item {
                            FrequencyActionBanner(
                                message = message,
                                onDismiss = viewModel::consumeFrequencyActionMessage
                            )
                        }
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
                    item { Spacer(Modifier.height(10.dp)) }
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
    totalCores: Int,
    coreRows: List<CpuCoreRow>
) {
    val scheme = MaterialTheme.colorScheme
    val availability = if (totalCores == 0) 0f else onlineCores.toFloat() / totalCores
    val allOnline = totalCores > 0 && onlineCores == totalCores
    val statusColor = if (allOnline) scheme.secondary else scheme.tertiary
    val processorName = chipsetName.ifBlank { "Processor topology" }
    val activeClusters = coreRows.map { it.cluster.policyPath }.distinct().size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = .32f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(statusColor.copy(alpha = .16f), scheme.surfaceContainerLow, Color.Transparent)
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    color = statusColor.copy(alpha = .13f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = .20f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.padding(13.dp).size(30.dp)
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "PROCESSOR ARRAY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = processorName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = scheme.onSurface,
                        maxLines = 2
                    )
                    Text(
                        text = "$activeClusters scheduling cluster${if (activeClusters == 1) "" else "s"} detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            CpuActivityMap(coreRows = coreRows, statusColor = statusColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CpuHeroMetric(
                    modifier = Modifier.weight(1f),
                    value = "$onlineCores/$totalCores",
                    label = "available cores",
                    accent = statusColor
                )
                CpuHeroMetric(
                    modifier = Modifier.weight(1f),
                    value = "${(availability * 100).toInt()}%",
                    label = "compute ready",
                    accent = scheme.primary
                )
            }

            Surface(
                color = scheme.surfaceContainerHighest.copy(alpha = .64f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LivePulseDot(color = statusColor)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = if (allOnline) "All processor lanes are online" else "Kernel policy is adapting the active lanes",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CpuActivityMap(coreRows: List<CpuCoreRow>, statusColor: Color) {
    val scheme = MaterialTheme.colorScheme
    val onlineFraction = if (coreRows.isEmpty()) 0f else coreRows.count { it.online }.toFloat() / coreRows.size

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LIVE CORE MAP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (onlineFraction == 1f) "FULL CAPACITY" else "DYNAMIC CAPACITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4
        ) {
            coreRows.forEach { row ->
                val accent = if (row.online) clusterAccent(row.cluster) else scheme.outline
                Surface(
                    modifier = Modifier.weight(1f, fill = true),
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = if (row.online) .13f else .06f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = if (row.online) .32f else .16f))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(accent)
                        )
                        Text(
                            text = "CPU${row.cpu}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Text(
                            text = if (row.online) row.cluster.shortTag else "PARKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuHeroMetric(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = .09f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .16f))
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = scheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FrequencyActionBanner(message: String, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Failure wording gets failure colors; verified/success stays neutral-green.
    val isError = listOf("رفض", "تعذّر", "تعارض", "أعاد ضبط", "كِيان خارجي").any(message::contains)
    val container = if (isError) scheme.errorContainer else scheme.secondaryContainer
    val content = if (isError) scheme.onErrorContainer else scheme.onSecondaryContainer
    val border = if (isError) scheme.error else scheme.secondary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, border.copy(alpha = .3f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Verified,
                null,
                tint = content,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = content)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = content)
            }
        }
    }
}

@Composable
private fun CpuFrequencyControlSection(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    controls: Map<String, CpuFrequencyControlState>,
    hasSessionChanges: Boolean,
    onApply: (String, Long, Long) -> Unit,
    onRestore: (String) -> Unit,
    onRestoreSession: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CpuSectionTitle("FREQUENCY CONTROL")
        Text(
            text = "اضبط حدود كل عنقود بنفسك أو ثبّته على تردد واحد. كل تطبيق يُتحقق منه فوراً من العتاد، وإن أعاد كِيان خارجي ضبط القيم نعيد تثبيتها تلقائياً.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (hasSessionChanges) {
            SessionRestoreCard(onRestoreSession = onRestoreSession)
        }
        clusters.forEach { cluster ->
            val control = controls[cluster.policyPath]
            if (control?.canControl == true) {
                CpuFrequencyControlCard(
                    cluster = cluster,
                    control = control,
                    onApply = { min, max -> onApply(cluster.policyPath, min, max) },
                    onRestore = { onRestore(cluster.policyPath) }
                )
            }
        }
    }
}

@Composable
private fun SessionRestoreCard(onRestoreSession: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.tertiaryContainer.copy(alpha = .48f),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.tertiary.copy(alpha = .32f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(Icons.Outlined.History, null, tint = scheme.tertiary, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f)) {
                Text("تغييرات جلسة قائمة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onTertiaryContainer)
                Text("استعادة كل الحدود إلى ما كانت عليه عند فتح هذه الشاشة، وإعادة التحكم للنظام.", style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer)
            }
            TextButton(onClick = onRestoreSession) {
                Text("استعادة", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FrequencyVerificationStrip(
    verification: nd.max.ui.viewmodel.CpuFrequencyVerification,
    accent: Color,
    controlMin: Long?,
    controlMax: Long?,
) {
    val scheme = MaterialTheme.colorScheme
    val statusColor = when {
        verification.verified -> scheme.secondary
        verification.writeAccepted -> scheme.tertiary
        else -> scheme.error
    }
    val headline = when {
        verification.verified && verification.reassertions > 0 ->
            "أعدنا التثبيت تلقائياً وتحققنا منه (محاولة ${verification.reassertions})"
        verification.verified -> "التطبيق موثّق — العتاد قبل القيم"
        verification.writeAccepted ->
            "كِيان خارجي أعاد الضبط بعد الكتابة — نحاول إعادة التثبيت"
        else -> "رفضت عقدة النظام الكتابة — القيم لم تتغير"
    }
    val explanation = when {
        verification.verified -> null
        verification.writeAccepted ->
            "ملف MaxManager العام أو نظام الحرارة في الجهاز يفرض حدوداً مختلفة."
        else -> "قد تكون العقدة محمية أو لا تقبل هذه القيمة."
    }
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = statusColor.copy(alpha = .09f),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = .24f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verification.verified) Icons.Outlined.Verified
                    else if (verification.writeAccepted) Icons.Outlined.Sync
                    else Icons.Outlined.ErrorOutline,
                    null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    headline,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Text(
                "المطلوب: ${formatCpuFrequency(verification.requestedMinKHz)} – ${formatCpuFrequency(verification.requestedMaxKHz)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Text(
                "القراءة لحظة التحقق: ${verification.actualMinKHz?.let(::formatCpuFrequency) ?: "—"} – ${verification.actualMaxKHz?.let(::formatCpuFrequency) ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (verification.verified) accent else statusColor
            )
            Text(
                "القيم الحية الآن: ${controlMin?.let(::formatCpuFrequency) ?: "—"} – ${controlMax?.let(::formatCpuFrequency) ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            explanation?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CpuFrequencyControlCard(
    cluster: CpuTopologyUtil.CpuCluster,
    control: CpuFrequencyControlState,
    onApply: (Long, Long) -> Unit,
    onRestore: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = clusterAccent(cluster)
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
    val current = control.currentKHz
    val activeMin = editMin.coerceIn(lower, upper)
    val activeMax = editMax.coerceIn(activeMin, upper)
    val isModified = activeMin != control.minKHz || activeMax != control.maxKHz
    var pinned by remember(control.policyPath) { mutableStateOf(false) }
    var pinSelection by remember(control.policyPath) { mutableStateOf<Long?>(null) }
    val effectivePin = pinSelection ?: activeMax
    val pinModified = pinned && (effectivePin != control.maxKHz || effectivePin != control.minKHz)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .24f))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = .10f), Color.Transparent)))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = .13f)
                ) {
                    Icon(
                        imageVector = clusterIcon(cluster),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = clusterDisplayName(cluster),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "${cluster.cores.size} core${if (cluster.cores.size == 1) "" else "s"} · ${control.governor ?: "kernel governor"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = RoundedCornerShape(99.dp), color = accent.copy(alpha = .11f)) {
                        Text(
                            text = current?.let(::formatCpuFrequency) ?: "LIVE —",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    if (control.externalConflict) {
                        Surface(shape = RoundedCornerShape(99.dp), color = scheme.errorContainer) {
                            Text(
                                text = "تعارض خارجي",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onErrorContainer
                            )
                        }
                    } else if (control.sessionOwned) {
                        Surface(shape = RoundedCornerShape(99.dp), color = scheme.secondaryContainer) {
                            Text(
                                text = "جلسة يدوية",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceContainerHighest.copy(alpha = .56f)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp)) {
                    CpuLimitMetric(
                        modifier = Modifier.weight(1f),
                        label = "LIVE FLOOR",
                        value = control.minKHz?.let(::formatCpuFrequency) ?: "—",
                        accent = accent
                    )
                    VerticalDivider(
                        modifier = Modifier.height(42.dp).padding(horizontal = 8.dp),
                        color = scheme.outlineVariant.copy(alpha = .65f)
                    )
                    CpuLimitMetric(
                        modifier = Modifier.weight(1f),
                        label = "LIVE CEILING",
                        value = control.maxKHz?.let(::formatCpuFrequency) ?: "—",
                        accent = accent
                    )
                }
            }

            control.verification?.let { FrequencyVerificationStrip(it, accent, control.minKHz, control.maxKHz) }

            if (control.externalConflict) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.errorContainer.copy(alpha = .5f)
                ) {
                    Text(
                        text = "تعارض مستمر: كِيان خارجي يفرض حدوداً مختلفة رغم إعادة التثبيت المتكررة. توقفت المحاولات التلقائية حفاظاً على الاستقرار.",
                        modifier = Modifier.padding(11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaxSwitch(
                    checked = pinned,
                    onCheckedChange = { pinned = it }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "تثبيت التردد",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = if (pinned) "قفل العنقود على تردد واحد ثابت (أدنى = أعلى)"
                        else "حد أدنى وحد أعلى — النظام يتحرك بينهما",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (!editable) {
                Text(
                    text = "هذا العنقود لا يعلن جدول ترددات قابل للاختيار؛ يبقى المدى الحي ظاهراً أعلاه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            } else if (pinned) {
                FrequencyPointPicker(
                    label = "التردد المثبّت",
                    value = effectivePin,
                    options = allowed,
                    accent = accent,
                    onValueChange = { selected -> pinSelection = selected }
                )
            } else {
                FrequencyPointPicker(
                    label = "الحد الأدنى",
                    value = activeMin,
                    options = allowed.filter { it <= activeMax },
                    accent = accent,
                    onValueChange = { selected -> editMin = selected.coerceAtMost(editMax) }
                )
                FrequencyPointPicker(
                    label = "الحد الأقصى",
                    value = activeMax,
                    options = allowed.filter { it >= activeMin },
                    accent = accent,
                    onValueChange = { selected -> editMax = selected.coerceAtLeast(editMin) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    enabled = lower < upper,
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .45f))
                ) {
                    Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("مدى العتاد")
                }
                Button(
                    onClick = { if (pinned) onApply(effectivePin, effectivePin) else onApply(activeMin, activeMax) },
                    modifier = Modifier.weight(1f),
                    enabled = editable && (if (pinned) pinModified else isModified),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = scheme.onPrimary)
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (pinned) "تثبيت" else "تطبيق")
                }
            }
            if (control.sessionOwned) {
                Text(
                    text = "حدودك محمية خلال هذه الجلسة: ملف MaxManager العام لن يعيدها للوضع الافتراضي حتى تستعيدها بنفسك.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CpuLimitMetric(modifier: Modifier, label: String, value: String, accent: Color) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FrequencyPointPicker(
    label: String,
    value: Long,
    options: List<Long>,
    accent: Color,
    onValueChange: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val safeOptions = options.ifEmpty { listOf(value) }
    val index = safeOptions.indexOf(value).takeIf { it >= 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatCpuFrequency(safeOptions[index]), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { position -> onValueChange(safeOptions[position.toInt().coerceIn(0, safeOptions.lastIndex)]) },
            valueRange = 0f..safeOptions.lastIndex.toFloat(),
            steps = (safeOptions.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = scheme.outlineVariant.copy(alpha = .58f)
            )
        )
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CpuSectionTitle("CPU CORES")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
