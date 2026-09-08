/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.MaxUiMetrics
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.viewmodel.ZramViewModel
import nd.max.ui.viewmodel.ZramSizePreset

@Composable
fun ZramManagerScreen(
    navController: NavController,
    viewModel: ZramViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current

    val disableConfirmDialog = rememberConfirmDialog(
        onConfirm = { viewModel.applyPreset(ZramViewModel.SIZE_PRESETS.first { it.id == "disabled" }) },
        onDismiss = {}
    )

    LaunchedEffect(Unit) { viewModel.loadState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MaxManagerSubScreenTopBar(
                scrollBehavior = scrollBehavior,
                title = androidx.compose.ui.res.stringResource(R.string.zram_title),
                onBack = { navController.popBackStack() },
                accentIcon = Icons.Filled.Memory,
                accent = colors.tertiary
            )
        },
        containerColor = colors.surface
    ) { innerPadding ->
        when (viewModel.isAvailable) {
            null -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                SectionLoadingIndicator()
            }
            false -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateCard(
                    icon = Icons.Outlined.Memory,
                    title = androidx.compose.ui.res.stringResource(R.string.zram_unavailable),
                    modifier = Modifier.padding(24.dp)
                )
            }
            true -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 14.dp,
                    start = MaxUiMetrics.screenHorizontalPadding,
                    end = MaxUiMetrics.screenHorizontalPadding,
                    bottom = MaxUiMetrics.screenBottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ZramHero(
                        sizeMb = viewModel.currentDiskSizeMb,
                        usedMb = viewModel.usedSwapMb,
                        totalSwapMb = viewModel.totalSwapMb,
                        algorithm = viewModel.compAlgorithm,
                        efficiency = viewModel.efficiencyRatio,
                        active = viewModel.swapPriority != null
                    )
                }

                item {
                    ZramMemoryBreakdown(
                        usedMb = viewModel.usedSwapMb,
                        totalMb = viewModel.totalSwapMb,
                        originalMb = viewModel.origDataMb,
                        compressedMb = viewModel.compDataMb,
                        savedMb = viewModel.ramSavedMb
                    )
                }

                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(R.string.zram_tuning_title),
                        subtitle = "Change the values that directly control compressed swap."
                    )
                }

                item {
                    var sizeSlider by remember(viewModel.currentDiskSizeMb) {
                        mutableFloatStateOf(viewModel.currentDiskSizeMb.toFloat())
                    }
                    ZramSizeCard(
                        valueMb = sizeSlider.toInt(),
                        maxMb = ZramViewModel.MAX_ZRAM_MB,
                        onValueChange = { sizeSlider = it },
                        onFinished = { viewModel.applyCustomSizeMb(sizeSlider.toInt()) }
                    )
                }

                if (viewModel.availableCompAlgorithms.size > 1) {
                    item {
                        ZramAlgorithmCard(
                            algorithms = viewModel.availableCompAlgorithms,
                            selected = viewModel.compAlgorithm,
                            onSelected = viewModel::setCompAlgorithm
                        )
                    }
                }

                item {
                    var slider by remember(viewModel.swappiness) {
                        mutableFloatStateOf(viewModel.swappiness.toFloat())
                    }
                    ZramSwappinessCard(
                        value = slider.toInt(),
                        onValueChange = { slider = it },
                        onFinished = { viewModel.applySwappiness(slider.toInt()) }
                    )
                }

                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(R.string.zram_presets_title),
                        subtitle = "Shortcuts for common memory profiles."
                    )
                }

                item {
                    ZramPresetStrip(
                        selected = viewModel.selectedPresetId,
                        onSelect = { preset ->
                            if (preset.id == "disabled") {
                                disableConfirmDialog.showConfirm(
                                    title = context.getString(R.string.zram_disable_confirm_title),
                                    content = context.getString(R.string.zram_disable_confirm_body),
                                    confirm = context.getString(R.string.yes),
                                    dismiss = context.getString(R.string.no)
                                )
                            } else viewModel.applyPreset(preset)
                        }
                    )
                }

                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(R.string.zram_hw_arch_title),
                        subtitle = "Read-only kernel facts detected from this device."
                    )
                }

                item {
                    ZramKernelFacts(
                        blockDevice = viewModel.blockDeviceNodeExists,
                        priority = viewModel.swapPriority,
                        compaction = viewModel.kernelCompactionSupported,
                        streams = viewModel.multiStreamCount
                    )
                }

                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(R.string.zram_instant_tools_title),
                        subtitle = "Maintenance actions use the live kernel nodes."
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::compactZram
                        ) {
                            Icon(Icons.Outlined.Compress, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.zram_compact_button))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::resetToDefault,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                            border = BorderStroke(1.dp, colors.error.copy(alpha = .45f))
                        ) {
                            Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.zram_reset_button))
                        }
                    }
                }

                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .65f))
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Info, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.zram_safety_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    ConfirmDialogHost(handle = disableConfirmDialog)
}

@Composable
private fun ZramHero(sizeMb: Int, usedMb: Int, totalSwapMb: Int, algorithm: String, efficiency: Float, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val fraction = if (totalSwapMb > 0) (usedMb.toFloat() / totalSwapMb).coerceIn(0f, 1f) else 0f
    val sizeText = if (sizeMb >= 1024) String.format("%.1f GB", sizeMb / 1024f) else "$sizeMb MB"
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .7f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = colors.tertiaryContainer) {
                    Icon(Icons.Filled.Memory, null, tint = colors.onTertiaryContainer, modifier = Modifier.padding(11.dp).size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Compressed memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("ZRAM-backed swap", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                StatusPill(if (active) "ACTIVE" else "IDLE", active)
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(sizeText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("configured", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = colors.tertiary,
                trackColor = colors.surfaceContainerHighest
            )
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${usedMb} MB used", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text("$totalSwapMb MB swap", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricChip("Codec", algorithm.uppercase(), Modifier.weight(1f))
                MetricChip("Ratio", String.format("%.2f×", efficiency), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ZramMemoryBreakdown(usedMb: Int, totalMb: Int, originalMb: Int, compressedMb: Int, savedMb: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(16.dp)) {
            Text("Memory accounting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            InfoRow("Swap in use", "$usedMb / $totalMb MB")
            InfoRow("Original data", "$originalMb MB")
            InfoRow("Compressed data", "$compressedMb MB")
            InfoRow("RAM saved", "$savedMb MB", emphasize = true)
        }
    }
}

@Composable
private fun ZramSizeCard(valueMb: Int, maxMb: Int, onValueChange: (Float) -> Unit, onFinished: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ZRAM size", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("0–${maxMb / 1024} GB kernel-backed compressed swap", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                ValueBadge(if (valueMb >= 1024) String.format("%.1f GB", valueMb / 1024f) else "$valueMb MB")
            }
            Spacer(Modifier.height(8.dp))
            MaxSlider(value = valueMb.toFloat(), onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = 0f..maxMb.toFloat(), steps = 31)
        }
    }
}

@Composable
private fun ZramAlgorithmCard(algorithms: List<String>, selected: String, onSelected: (String) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(16.dp)) {
            Text("Compression algorithm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Only algorithms exposed by this kernel are shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            algorithms.forEach { algo ->
                val checked = selected == algo
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelected(algo) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = checked, onClick = { onSelected(algo) })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(algoLabelLocal(algo), style = MaterialTheme.typography.bodyLarge, fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal)
                        val desc = algoDescLocal(algo)
                        if (desc.isNotEmpty()) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZramSwappinessCard(value: Int, onValueChange: (Float) -> Unit, onFinished: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Swappiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("How readily the kernel moves pages into swap.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                ValueBadge(value.toString())
            }
            MaxSlider(value = value.toFloat(), onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = 0f..200f, steps = 19)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Prefer RAM", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("Swap sooner", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ZramPresetStrip(selected: String, onSelect: (ZramSizePreset) -> Unit) {
    // Kept as a small horizontal choice surface rather than another tall list of cards.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ZramViewModel.SIZE_PRESETS.forEach { preset ->
            val selectedNow = selected == preset.id
            val label = when (preset.id) { "disabled" -> "Off"; "light" -> "4 GB"; "stock" -> "8 GB"; "power" -> "12 GB"; else -> preset.id }
            FilterChip(selected = selectedNow, onClick = { onSelect(preset) }, label = { Text(label) }, leadingIcon = if (selectedNow) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null)
        }
    }
}

@Composable
private fun ZramKernelFacts(blockDevice: Boolean, priority: Int?, compaction: Boolean, streams: Int?) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
        Column {
            FactRow("/dev/block/zram0", if (blockDevice) "Available" else "Not detected", blockDevice)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            FactRow("Swap", priority?.let { "Active · priority $it" } ?: "Inactive", priority != null)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            FactRow("Memory compaction", if (compaction) "Supported" else "Not detected", compaction)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            FactRow("Compression streams", streams?.toString() ?: "Not exposed", streams != null)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Outlined.HelpOutline, null, tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ValueBadge(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (active) colors.tertiaryContainer else colors.surfaceContainerHighest) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (active) colors.onTertiaryContainer else colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyStateCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun algoLabelLocal(id: String): String = when (id) {
    "lz4" -> "LZ4 · fast"
    "zstd" -> "ZSTD · high compression"
    "lzo-rle" -> "LZO-RLE · balanced"
    "lzo" -> "LZO · legacy"
    "842" -> "842"
    else -> id.uppercase()
}

private fun algoDescLocal(id: String): String = when (id) {
    "lz4" -> "Lower compression overhead and generally low CPU cost."
    "zstd" -> "Higher compression with more CPU work."
    "lzo-rle" -> "A balanced kernel compression option."
    "lzo" -> "Older compatibility-oriented compressor."
    else -> ""
}
