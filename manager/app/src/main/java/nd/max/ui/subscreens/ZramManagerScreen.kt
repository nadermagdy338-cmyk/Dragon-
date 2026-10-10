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
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxMetricReadout
import nd.max.ui.design.MaxMetricSize
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxTone
import nd.max.ui.component.MaxDeviceInfoShortcut
import nd.max.ui.component.rememberMemoryLedgerFields
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.settings.rememberAdvancedMode
import nd.max.ui.viewmodel.ZramOpKind
import nd.max.ui.viewmodel.ZramOperation
import nd.max.ui.viewmodel.ZramSizePreset
import nd.max.ui.viewmodel.ZramViewModel

/** Size slider granularity. 256 MB is the smallest step that is meaningful for swap. */
private const val SIZE_STEP_MB = 256

private val SIZE_SLIDER_STEPS = (ZramViewModel.MAX_ZRAM_MB / SIZE_STEP_MB) - 1

/**
 * Compressed swap (ZRAM).
 *
 * Rebuilt on the MaxManager Design Language. What changed and why:
 *
 *  - The old screen showed a radial gauge and a LIVE pill above numbers that are
 *    actually a mix of a 3 second poll and one-shot probes. Size, swap usage and
 *    the compression counters really are polled, so they are marked Live. Codec,
 *    swappiness, stream count and node presence are read once by loadState(), so
 *    they are marked Snapshot. The gauge was dropped: a dial that maps swap size
 *    against an arbitrary ceiling adds nothing the number does not already say.
 *  - Every control used to be fire and forget, and the UI moved to the requested
 *    value immediately. Vendor kernels routinely refuse disksize writes, so the
 *    app claimed a state the device never had. The ViewModel now reads the value
 *    back, and this screen renders Applying, Applied or Failed with the value
 *    that was requested next to the value the kernel reports.
 *  - Turning swap off and restoring defaults are destructive, so they go through
 *    the shared confirm dialog with the exact shell effect shown in mono.
 *  - Preset labels resolve by id through a when(), not Resources.getIdentifier().
 *    Name based reflection returns 0 once resource shrinking runs, which is how
 *    these labels would silently go blank in a release build.
 *  - Sliders keep local state while dragging and commit only on release, so one
 *    drag can no longer fire dozens of root writes at the kernel.
 *  - The poll stops when the screen is not resumed instead of running forever.
 */
@Composable
fun ZramManagerScreen(
    navController: NavController,
    viewModel: ZramViewModel = viewModel()
) {
    LifecycleResumeEffect(Unit) {
        viewModel.loadState()
        onPauseOrDispose { viewModel.pausePolling() }
    }

    val operation = viewModel.operation
    val busy = operation is ZramOperation.Applying
    val available = viewModel.isAvailable == true
    val canWrite = available && !busy

    val diskMb = viewModel.currentDiskSizeMb
    val swapOff = diskMb <= 0

    var sizeSlider by remember { mutableStateOf(diskMb.toFloat()) }
    LaunchedEffect(diskMb) { sizeSlider = diskMb.toFloat() }

    var swappinessSlider by remember { mutableStateOf(viewModel.swappiness.toFloat()) }
    LaunchedEffect(viewModel.swappiness) { swappinessSlider = viewModel.swappiness.toFloat() }

    var confirmDisable by remember { mutableStateOf(false) }
    var confirmCompact by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // Kernel facts are secondary diagnosis, so they stay folded until asked for.
    var showKernelFacts by remember { mutableStateOf(false) }

    // **ودفتر الذاكرة (تكملة ٢٦٣)** يشارك الكشف مع «Kernel Facts»، لكنه لا يظهر إلا بالوضع
    // المتقدّم — أمر المالك: «وتظهر memory من شاشة Kernel Facts و devic info عند الضغط على
    // Advanced Mode». وهو **عرض لا يقيس ويُسجّل**: القياس يُسجَّل في شاشة التشخيص وحدها.
    val advanced = rememberAdvancedMode()

    val sysfsSource = stringResource(R.string.max_zram_source_sysfs)
    val swapsSource = stringResource(R.string.max_zram_source_swaps)
    val unitMb = stringResource(R.string.max_zram_unit_mb)
    val offLabel = stringResource(R.string.max_zram_swap_off)
    val busyReason = stringResource(R.string.max_zram_applying_detail)
    val rootReason = stringResource(R.string.max_zram_locked_root)
    val supportedLabel = stringResource(R.string.zram_hw_supported)
    val unsupportedLabel = stringResource(R.string.zram_hw_unsupported)

    // Screen level explanation lives in the top bar help dialog instead of
    // occupying permanent vertical space under every section title.
    val helpBody = stringResource(R.string.max_zram_section_state_desc) +
        "\n\n" + stringResource(R.string.max_zram_section_pressure_desc)

    // Compression ratio is derived, never invented: without both counters the
    // metric is unreadable rather than silently shown as zero.
    val origMb = viewModel.origDataMb
    val compMb = viewModel.compDataMb
    val ratio = if (origMb > 0 && compMb > 0) origMb.toFloat() / compMb.toFloat() else null
    val ratioText = ratio?.let { stringResource(R.string.max_zram_ratio_value, "%.2f".format(it)) }

    val condition = when (viewModel.isAvailable) {
        null -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_zram_probe_title),
            detail = stringResource(R.string.max_zram_probe_detail)
        )

        false -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = stringResource(R.string.zram_unavailable),
            detail = stringResource(R.string.max_zram_unsupported_detail),
            technicalDetail = "/sys/block/zram0/disksize",
            primaryActionLabel = stringResource(R.string.max_action_recheck),
            onPrimaryAction = { viewModel.loadState() }
        )

        else -> null
    }

    val banner = when (operation) {
        null -> null

        is ZramOperation.Applying -> MaxCondition(
            kind = MaxConditionKind.Applying,
            title = applyingTitle(operation.kind),
            detail = stringResource(R.string.max_zram_applying_detail)
        )

        is ZramOperation.Applied -> MaxCondition(
            kind = MaxConditionKind.Applied,
            title = stringResource(R.string.max_zram_applied_title),
            detail = appliedDetail(operation),
            primaryActionLabel = stringResource(R.string.max_action_dismiss),
            onPrimaryAction = { viewModel.clearOperation() }
        )

        is ZramOperation.Failed -> MaxCondition(
            kind = MaxConditionKind.Failed,
            title = stringResource(R.string.max_zram_failed_title),
            detail = failedDetail(operation),
            technicalDetail = stringResource(R.string.max_zram_failed_detail),
            primaryActionLabel = stringResource(R.string.max_action_dismiss),
            onPrimaryAction = { viewModel.clearOperation() }
        )
    }

    MaxScreen(
        title = stringResource(R.string.zram_title),
        subtitle = stringResource(R.string.zram_desc),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Memory,
        condition = condition,
        banner = banner,
        actions = {
            IconButton(onClick = { viewModel.loadState() }, enabled = !busy) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.max_action_refresh)
                )
            }
            MaxHelpAction(
                title = stringResource(R.string.zram_title),
                body = helpBody
            )
        }
    ) {
        // ---- What the kernel reports right now -------------------------------
        // وبطاقة **swap/zram** الأولى تحمل في آخرها باب **More info** إلى قسم الذاكرة في
        // «معلومات الجهاز» — سطر رابط بأيقونة الشاشة وكلمة (`MaxDeviceInfoShortcut`) لا كبسولة
        // ولا صفًّا كاملًا يُزاح به العمل (أمر المالك: «زرًّا وليس أيقونة، باسم More info»).
        MaxSection(title = stringResource(R.string.zram_gauge_title)) {
            MaxGroup {
                MaxMetricReadout(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_zram_metric_size),
                        value = if (swapOff) offLabel else diskMb.toString(),
                        unit = if (swapOff) null else unitMb,
                        trust = MaxDataTrust.Live,
                        source = sysfsSource
                    ),
                    size = MaxMetricSize.Large
                )
                MaxGroupDivider()
                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_zram_metric_used),
                        value = if (swapOff) offLabel else viewModel.usedSwapMb.toString(),
                        unit = if (swapOff) null else unitMb,
                        trust = MaxDataTrust.Live,
                        source = swapsSource
                    )
                )
                MaxGroupDivider()
                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_zram_metric_ratio),
                        value = ratioText,
                        trust = if (ratio == null) MaxDataTrust.Unreadable else MaxDataTrust.Live,
                        source = sysfsSource
                    )
                )
                MaxGroupDivider()
                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_zram_metric_saved),
                        value = if (ratio == null) null else viewModel.ramSavedMb.toString(),
                        unit = if (ratio == null) null else unitMb,
                        trust = if (ratio == null) MaxDataTrust.Unreadable else MaxDataTrust.Live,
                        source = sysfsSource
                    )
                )
                // والباب آخر البطاقة، مفصولًا بخطّ داخلي: إجراء عليها لا صفّ بيانات بينها.
                MaxGroupDivider()
                MaxDeviceInfoShortcut(navController, MaxDestination.ZramManager)
            }
        }

        // ---- Size -------------------------------------------------------------
        MaxSection(title = stringResource(R.string.zram_presets_title)) {
            MaxGroup {
                ZramViewModel.SIZE_PRESETS.forEachIndexed { index, preset ->
                    if (index > 0) MaxGroupDivider()
                    MaxChoiceRow(
                        title = presetLabel(preset),
                        subtitle = if (preset.mb <= 0) {
                            stringResource(R.string.zram_preset_disabled_desc)
                        } else {
                            stringResource(R.string.max_zram_custom_value, preset.mb.toString())
                        },
                        selected = viewModel.selectedPresetId == preset.id,
                        enabled = canWrite,
                        lockedReason = if (busy) busyReason else null,
                        onSelect = {
                            if (preset.mb <= 0) confirmDisable = true else viewModel.applyPreset(preset)
                        }
                    )
                }
            }
            MaxGroup {
                MaxSliderRow(
                    title = stringResource(R.string.zram_custom_size),
                    subtitle = stringResource(R.string.zram_custom_size_desc),
                    value = sizeSlider,
                    onValueChange = { sizeSlider = it },
                    valueRange = 0f..ZramViewModel.MAX_ZRAM_MB.toFloat(),
                    steps = SIZE_SLIDER_STEPS,
                    valueText = snapSizeMb(sizeSlider).let { target ->
                        if (target <= 0) {
                            offLabel
                        } else {
                            stringResource(R.string.max_zram_custom_value, target.toString())
                        }
                    },
                    enabled = canWrite,
                    lockedReason = if (busy) busyReason else null,
                    onValueChangeFinished = {
                        val target = snapSizeMb(sizeSlider)
                        if (target <= 0) confirmDisable = true else viewModel.applyCustomSizeMb(target)
                    }
                )
            }
            MaxBullets(
                lines = listOf(stringResource(R.string.max_zram_section_size_desc)),
                tone = MaxTone.Caution
            )
        }

        // ---- Codec ------------------------------------------------------------
        MaxSection(title = stringResource(R.string.zram_algo_title)) {
            val algorithms = viewModel.availableCompAlgorithms
            MaxGroup {
                if (algorithms.isEmpty()) {
                    // The kernel did not publish a codec list. Show what is in use
                    // instead of an empty section that looks broken.
                    ZramFactRow(
                        label = stringResource(R.string.max_zram_metric_algo),
                        value = viewModel.compAlgorithm.ifBlank { MAX_VALUE_UNAVAILABLE },
                        subtitle = stringResource(R.string.zram_algo_desc)
                    )
                } else {
                    algorithms.forEachIndexed { index, algorithm ->
                        if (index > 0) MaxGroupDivider()
                        val description = algorithmDescription(algorithm)
                        MaxChoiceRow(
                            // The kernel token itself is the honest label here.
                            title = algorithm,
                            subtitle = description?.let { stringResource(it) },
                            selected = algorithm.equals(viewModel.compAlgorithm, ignoreCase = true),
                            enabled = canWrite,
                            lockedReason = if (busy) busyReason else null,
                            onSelect = { viewModel.setCompAlgorithm(algorithm) }
                        )
                    }
                }
            }
            MaxBullets(
                lines = listOf(stringResource(R.string.max_zram_section_codec_desc)),
                tone = MaxTone.Caution
            )
        }

        // ---- Memory pressure ---------------------------------------------------
        MaxSection(title = stringResource(R.string.zram_tuning_title)) {
            MaxGroup {
                MaxSliderRow(
                    title = stringResource(R.string.zram_swappiness),
                    subtitle = stringResource(R.string.zram_swappiness_desc),
                    value = swappinessSlider,
                    onValueChange = { swappinessSlider = it },
                    valueRange = 0f..200f,
                    valueText = if (viewModel.swappinessKnown) {
                        swappinessSlider.roundToInt().toString()
                    } else {
                        MAX_VALUE_UNAVAILABLE
                    },
                    enabled = canWrite && viewModel.swappinessKnown,
                    lockedReason = when {
                        busy -> busyReason
                        !viewModel.swappinessKnown -> rootReason
                        else -> null
                    },
                    onValueChangeFinished = {
                        viewModel.applySwappiness(swappinessSlider.roundToInt())
                    }
                )
            }
        }

        // ---- Maintenance --------------------------------------------------------
        MaxSection(title = stringResource(R.string.zram_instant_tools_title)) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.zram_compact_button),
                    subtitle = if (viewModel.kernelCompactionSupported) {
                        stringResource(R.string.max_zram_compact_confirm_body)
                    } else {
                        stringResource(R.string.max_zram_compaction_unsupported)
                    },
                    icon = Icons.Rounded.CleaningServices,
                    iconTone = MaxTone.Accent,
                    enabled = canWrite && viewModel.kernelCompactionSupported,
                    onClick = { confirmCompact = true }
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.zram_reset_button),
                    subtitle = stringResource(R.string.max_zram_reset_confirm_body),
                    icon = Icons.Rounded.RestartAlt,
                    iconTone = MaxTone.Caution,
                    enabled = canWrite,
                    onClick = { confirmReset = true }
                )
            }
            MaxBullets(
                lines = listOf(stringResource(R.string.max_zram_section_maintenance_desc)),
                tone = MaxTone.Caution
            )
        }

        // ---- Kernel facts: secondary, folded until asked for --------------------
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.zram_hw_arch_title),
                onClick = { showKernelFacts = !showKernelFacts },
                trailing = {
                    Icon(
                        imageVector = if (showKernelFacts) {
                            Icons.Rounded.ExpandLess
                        } else {
                            Icons.Rounded.ExpandMore
                        },
                        contentDescription = null
                    )
                }
            )
            if (showKernelFacts) {
                MaxGroupDivider()
                ZramFactRow(
                    label = stringResource(R.string.zram_hw_block_device),
                    value = if (viewModel.blockDeviceNodeExists) supportedLabel else unsupportedLabel
                )
                MaxGroupDivider()
                ZramFactRow(
                    label = stringResource(R.string.zram_hw_swap_status),
                    value = if (viewModel.totalSwapMb > 0) {
                        stringResource(R.string.zram_hw_swap_active)
                    } else {
                        stringResource(R.string.zram_hw_swap_inactive)
                    }
                )
                MaxGroupDivider()
                ZramFactRow(
                    label = stringResource(R.string.zram_hw_compaction),
                    value = if (viewModel.kernelCompactionSupported) supportedLabel else unsupportedLabel
                )
                MaxGroupDivider()
                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_zram_metric_priority),
                        value = viewModel.swapPriority?.toString(),
                        trust = if (viewModel.swapPriority == null) {
                            MaxDataTrust.Unreadable
                        } else {
                            MaxDataTrust.Live
                        },
                        source = swapsSource
                    )
                )
                MaxGroupDivider()
                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.zram_hw_streams),
                        value = viewModel.multiStreamCount?.toString(),
                        trust = if (viewModel.multiStreamCount == null) {
                            MaxDataTrust.Unreadable
                        } else {
                            MaxDataTrust.Snapshot
                        },
                        source = sysfsSource
                    )
                )
                // ---- ودفتر الذاكرة (`AR-24`): صفوفٌ في المجموعة نفسها، بالوضع المتقدّم وحده.
                // وبعرضٍ لا كتابة: القيم من مصدر واحد (`rememberMemoryLedgerFields`) الذي
                // يقيس PSS الحالي بلا امتياز ويقارنه بآخر ما سُجّل، ولا يُضيف لقطة.
                if (advanced) {
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.max_memory_ledger_title),
                        subtitle = stringResource(R.string.max_memory_ledger_view_desc),
                    )
                    rememberMemoryLedgerFields().forEach { field ->
                        MaxGroupDivider()
                        MaxRow(title = field.label, subtitle = field.value)
                    }
                }
            }
        }
    }

    MaxConfirmDialog(
        visible = confirmDisable,
        title = stringResource(R.string.zram_disable_confirm_title),
        message = stringResource(R.string.zram_disable_confirm_body),
        technicalDetail = "swapoff /sys/block/zram0",
        confirmLabel = stringResource(R.string.max_action_turn_off),
        icon = Icons.Rounded.PowerSettingsNew,
        destructive = true,
        onConfirm = {
            val disabled = ZramViewModel.SIZE_PRESETS.first { it.mb <= 0 }
            viewModel.applyPreset(disabled)
        },
        onDismiss = {
            confirmDisable = false
            // Put the slider back where the kernel actually is, so a cancelled
            // confirmation never leaves the UI showing a value that was refused.
            sizeSlider = diskMb.toFloat()
        }
    )

    MaxConfirmDialog(
        visible = confirmCompact,
        title = stringResource(R.string.max_zram_compact_confirm_title),
        message = stringResource(R.string.max_zram_compact_confirm_body),
        technicalDetail = "echo 1 > /proc/sys/vm/compact_memory",
        confirmLabel = stringResource(R.string.zram_compact_button),
        icon = Icons.Rounded.CleaningServices,
        onConfirm = { viewModel.compactZram() },
        onDismiss = { confirmCompact = false }
    )

    MaxConfirmDialog(
        visible = confirmReset,
        title = stringResource(R.string.max_zram_reset_confirm_title),
        message = stringResource(R.string.max_zram_reset_confirm_body),
        technicalDetail = "mkswap + swapon /sys/block/zram0",
        confirmLabel = stringResource(R.string.zram_reset_button),
        icon = Icons.Rounded.RestartAlt,
        destructive = true,
        onConfirm = { viewModel.resetToDefault() },
        onDismiss = { confirmReset = false }
    )
}

/** Snaps a raw slider position to the 256 MB grid the kernel is asked for. */
private fun snapSizeMb(value: Float): Int =
    ((value / SIZE_STEP_MB).roundToInt() * SIZE_STEP_MB)
        .coerceIn(0, ZramViewModel.MAX_ZRAM_MB)

/**
 * A kernel fact that is text rather than a measurement: a node that exists, a
 * codec token, a supported flag. These deliberately carry no trust chip, because
 * a trust marker next to a word that is not a number reads as noise.
 */
@Composable
private fun ZramFactRow(label: String, value: String, subtitle: String? = null) {
    MaxRow(
        title = label,
        subtitle = subtitle,
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    )
}

/**
 * Preset labels resolve by id, not by the label name stored on the preset.
 * Resources.getIdentifier() returns 0 once resource shrinking runs, and a label
 * that silently disappears in release builds is worse than a compile-time when.
 */
@Composable
private fun presetLabel(preset: ZramSizePreset): String = stringResource(
    when (preset.id) {
        "disabled" -> R.string.zram_preset_disabled
        "light" -> R.string.zram_preset_light
        "stock" -> R.string.zram_preset_stock
        "power" -> R.string.zram_preset_power
        else -> R.string.zram_custom_size
    }
)

/** Known kernel codecs get an explanation; unknown ones are shown without one. */
private fun algorithmDescription(algorithm: String): Int? = when (algorithm.lowercase()) {
    "lz4" -> R.string.zram_algo_lz4_desc
    "zstd" -> R.string.zram_algo_zstd_desc
    "lzo-rle" -> R.string.zram_algo_lzorle_desc
    "lzo" -> R.string.zram_algo_lzo_desc
    else -> null
}

@Composable
private fun applyingTitle(kind: ZramOpKind): String = stringResource(
    when (kind) {
        ZramOpKind.Size -> R.string.max_zram_applying_size
        ZramOpKind.Swappiness -> R.string.max_zram_applying_swappiness
        ZramOpKind.Algorithm -> R.string.max_zram_applying_algorithm
        ZramOpKind.Compact -> R.string.max_zram_applying_compact
        ZramOpKind.Reset -> R.string.max_zram_applying_reset
    }
)

@Composable
private fun appliedDetail(operation: ZramOperation.Applied): String {
    val value = operation.value ?: MAX_VALUE_UNAVAILABLE
    return when (operation.kind) {
        ZramOpKind.Size -> stringResource(R.string.max_zram_applied_size, value)
        ZramOpKind.Swappiness -> stringResource(R.string.max_zram_applied_swappiness, value)
        ZramOpKind.Algorithm -> stringResource(R.string.max_zram_applied_algorithm, value)
        ZramOpKind.Compact -> stringResource(R.string.max_zram_applied_compact)
        ZramOpKind.Reset -> stringResource(R.string.max_zram_applied_reset)
    }
}

/**
 * Failure copy states the requested value next to the value the kernel reports.
 * That difference is the whole diagnosis, so it is never reduced to a generic
 * error message.
 */
@Composable
private fun failedDetail(operation: ZramOperation.Failed): String {
    val requested = operation.requested ?: MAX_VALUE_UNAVAILABLE
    val actual = operation.actual
    return when (operation.kind) {
        ZramOpKind.Compact -> stringResource(R.string.max_zram_failed_compact)
        ZramOpKind.Size, ZramOpKind.Reset -> if (actual == null) {
            stringResource(R.string.max_zram_failed_unreadable, requested)
        } else {
            stringResource(R.string.max_zram_failed_size, requested, actual)
        }

        ZramOpKind.Swappiness -> if (actual == null) {
            stringResource(R.string.max_zram_failed_unreadable, requested)
        } else {
            stringResource(R.string.max_zram_failed_swappiness, requested, actual)
        }

        ZramOpKind.Algorithm -> if (actual == null) {
            stringResource(R.string.max_zram_failed_unreadable, requested)
        } else {
            stringResource(R.string.max_zram_failed_algorithm, requested, actual)
        }
    }
}
