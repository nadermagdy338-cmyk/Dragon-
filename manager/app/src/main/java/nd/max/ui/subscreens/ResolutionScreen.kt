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

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.component.ConfirmDialogHost
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.component.rememberConfirmDialog
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxMetricReadout
import nd.max.ui.design.MaxMetricSize
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.viewmodel.ResolutionViewModel

/**
 * Display size (resolution and density).
 *
 * Rebuilt on the MaxManager Design Language. What changed and why:
 *
 *  - The old screen showed a [RadialGaugeCard] with `isLive = true` while the
 *    numbers came from a single `wm size` read. That is a false real-time
 *    claim, so the gauge is gone: the same numbers are now readouts marked as
 *    a snapshot, with their source (`wm size` / `wm density`) and a refresh
 *    action. The gauge also encoded "width percentage" as if it were an
 *    overall resolution scale, which is not the same thing; it is now labelled
 *    as width scale and marked as derived.
 *  - The old "Height" control was a disabled slider bound to the width value:
 *    a control that cannot be moved and does not show its own number. Height
 *    is now a readout while the aspect lock is on, and a real slider when the
 *    lock is off.
 *  - That also gives the aspect-ratio switch an actual effect. Previously the
 *    UI derived height from width unconditionally, so the switch changed
 *    nothing on this screen.
 *  - Refresh rate reports 0 when the display service does not expose `fps=`.
 *    Zero is not a refresh rate, so it renders as unreadable instead.
 *  - Reset keeps its confirmation dialog and is the only critical-tone row.
 */
@Composable
fun ResolutionScreen(
    navController: NavController,
    viewModel: ResolutionViewModel = viewModel()
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val resetDialog = rememberConfirmDialog(
        onConfirm = viewModel::resetToNative,
        onDismiss = {}
    )

    LaunchedEffect(Unit) { viewModel.loadState() }

    val nativeW = viewModel.nativeWidthPx
    val nativeH = viewModel.nativeHeightPx
    val nativeD = viewModel.nativeDpi
    val panelReadable = nativeW > 0 && nativeH > 0 && nativeD > 0
    val source = stringResource(R.string.max_res_source_wm)

    var widthSlider by remember(viewModel.activeWidthPx) {
        mutableStateOf(viewModel.activeWidthPx.toFloat())
    }
    var heightSlider by remember(viewModel.activeHeightPx) {
        mutableStateOf(viewModel.activeHeightPx.toFloat())
    }
    var dpiSlider by remember(viewModel.activeDpi) {
        mutableStateOf(viewModel.activeDpi.toFloat())
    }

    // Ranges are clamped so the screen cannot build an invalid slider range
    // even if the panel read failed.
    val minWidth = (nativeW * 0.4f).roundToInt().coerceAtLeast(1)
    val maxWidth = nativeW.coerceAtLeast(minWidth + 1)
    val minHeight = (nativeH * 0.4f).roundToInt().coerceAtLeast(1)
    val maxHeight = nativeH.coerceAtLeast(minHeight + 1)
    val minDpi = (nativeD * 0.4f).roundToInt().coerceAtLeast(1)
    val maxDpi = nativeD.coerceAtLeast(minDpi + 1)

    val aspectLocked = viewModel.lockAspectRatio
    val derivedHeight = (widthSlider / nativeW.coerceAtLeast(1) * nativeH)
        .roundToInt()
        .coerceAtLeast(1)
    val targetHeight = if (aspectLocked) derivedHeight else heightSlider.roundToInt()

    val condition = when {
        !viewModel.isLoaded -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_res_probe_title),
            detail = stringResource(R.string.max_res_probe_detail)
        )

        !panelReadable -> MaxCondition(
            kind = MaxConditionKind.Error,
            title = stringResource(R.string.max_res_unreadable_title),
            detail = stringResource(R.string.max_res_unreadable_detail),
            technicalDetail = source,
            primaryActionLabel = stringResource(R.string.max_action_retry),
            onPrimaryAction = { viewModel.loadState() }
        )

        else -> null
    }

    val scalePercent = if (nativeW > 0) {
        (viewModel.activeWidthPx.toFloat() / nativeW * 100f).roundToInt()
    } else {
        null
    }

    ScreenAccentProvider(scheme.secondary) {
        MaxScreen(
            title = stringResource(R.string.resolution_title),
            onBack = { navController.popBackStack() },
            subtitle = if (panelReadable) {
                "${viewModel.activeWidthPx}×${viewModel.activeHeightPx}"
            } else {
                null
            },
            accentIcon = Icons.Rounded.AspectRatio,
            accent = scheme.secondary,
            condition = condition,
            actions = {
                IconButton(onClick = { viewModel.loadState() }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.max_action_refresh)
                    )
                }
            }
        ) {
            MaxSection(
                title = stringResource(R.string.max_res_section_current),
                description = stringResource(R.string.max_res_section_current_desc)
            ) {
                MaxGroup {
                    MaxMetricReadout(
                        metric = MaxMetric(
                            label = stringResource(R.string.max_res_metric_active),
                            value = "${viewModel.activeWidthPx}×${viewModel.activeHeightPx}",
                            unit = "px",
                            trust = MaxDataTrust.Snapshot,
                            source = source
                        ),
                        size = MaxMetricSize.Large
                    )
                    MaxGroupDivider()
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.max_res_metric_native),
                            value = "$nativeW×$nativeH",
                            trust = MaxDataTrust.Snapshot,
                            source = source
                        )
                    )
                    MaxGroupDivider()
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.max_res_metric_dpi),
                            value = viewModel.activeDpi.toString(),
                            unit = "dpi",
                            trust = MaxDataTrust.Snapshot,
                            source = source,
                            note = stringResource(R.string.max_res_dpi_native_note, nativeD)
                        )
                    )
                    MaxGroupDivider()
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.max_res_metric_scale),
                            value = scalePercent?.toString(),
                            unit = "%",
                            trust = MaxDataTrust.Snapshot,
                            source = stringResource(R.string.max_res_source_derived),
                            note = stringResource(R.string.max_res_scale_note)
                        )
                    )
                    MaxGroupDivider()
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.max_res_metric_refresh),
                            // 0 Hz is not a refresh rate: report it as unreadable.
                            value = viewModel.refreshRateHz.takeIf { it > 0 }?.toString(),
                            unit = "Hz",
                            trust = if (viewModel.refreshRateHz > 0) {
                                MaxDataTrust.Snapshot
                            } else {
                                MaxDataTrust.Unreadable
                            },
                            source = source,
                            note = if (viewModel.refreshRateHz > 0) {
                                null
                            } else {
                                stringResource(R.string.max_res_refresh_missing)
                            }
                        )
                    )
                }
            }

            MaxSection(
                title = stringResource(R.string.resolution_presets_title),
                description = stringResource(R.string.max_res_presets_desc)
            ) {
                MaxGroup {
                    viewModel.presets.forEachIndexed { index, preset ->
                        if (index > 0) MaxGroupDivider()
                        val active = preset.widthPx == viewModel.activeWidthPx &&
                            preset.heightPx == viewModel.activeHeightPx &&
                            preset.dpi == viewModel.activeDpi
                        MaxChoiceRow(
                            title = presetTitle(preset.id),
                            subtitle = "${preset.widthPx}×${preset.heightPx} · ${preset.dpi} dpi",
                            selected = active,
                            onSelect = { viewModel.applyPreset(preset) }
                        )
                    }
                }
            }

            MaxSection(
                title = stringResource(R.string.resolution_manual_title),
                description = stringResource(R.string.max_res_section_manual_desc)
            ) {
                MaxGroup {
                    MaxSwitchRow(
                        title = stringResource(R.string.resolution_lock_aspect),
                        subtitle = stringResource(R.string.resolution_lock_aspect_desc),
                        checked = aspectLocked,
                        onCheckedChange = { viewModel.lockAspectRatio = it },
                        icon = Icons.Rounded.Lock
                    )
                    MaxGroupDivider()
                    MaxSliderRow(
                        title = stringResource(R.string.resolution_width),
                        value = widthSlider,
                        onValueChange = { widthSlider = it },
                        valueText = "${widthSlider.roundToInt()} px",
                        valueRange = minWidth.toFloat()..maxWidth.toFloat(),
                        onValueChangeFinished = {
                            viewModel.applyResolution(
                                widthSlider.roundToInt(),
                                targetHeight,
                                dpiSlider.roundToInt()
                            )
                        }
                    )
                    MaxGroupDivider()
                    if (aspectLocked) {
                        // A derived value is a readout, not a dead control.
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.max_res_height_derived),
                                value = derivedHeight.toString(),
                                unit = "px",
                                trust = MaxDataTrust.Snapshot,
                                source = stringResource(R.string.max_res_source_derived),
                                note = stringResource(R.string.max_res_height_locked_note)
                            )
                        )
                    } else {
                        MaxSliderRow(
                            title = stringResource(R.string.resolution_height),
                            value = heightSlider,
                            onValueChange = { heightSlider = it },
                            valueText = "${heightSlider.roundToInt()} px",
                            valueRange = minHeight.toFloat()..maxHeight.toFloat(),
                            onValueChangeFinished = {
                                viewModel.applyResolution(
                                    widthSlider.roundToInt(),
                                    heightSlider.roundToInt(),
                                    dpiSlider.roundToInt()
                                )
                            }
                        )
                    }
                    MaxGroupDivider()
                    MaxSliderRow(
                        title = stringResource(R.string.resolution_dpi),
                        value = dpiSlider,
                        onValueChange = { dpiSlider = it },
                        valueText = "${dpiSlider.roundToInt()} dpi",
                        valueRange = minDpi.toFloat()..maxDpi.toFloat(),
                        onValueChangeFinished = {
                            viewModel.applyResolution(
                                widthSlider.roundToInt(),
                                targetHeight,
                                dpiSlider.roundToInt()
                            )
                        }
                    )
                }
            }

            MaxSection(title = stringResource(R.string.max_res_section_reset)) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.resolution_reset_native),
                        subtitle = stringResource(
                            R.string.resolution_reset_native_desc,
                            nativeW,
                            nativeH,
                            nativeD
                        ),
                        icon = Icons.Rounded.RestartAlt,
                        iconTone = MaxTone.Critical,
                        enabled = panelReadable,
                        onClick = {
                            resetDialog.showConfirm(
                                title = context.getString(R.string.resolution_reset_title),
                                content = context.getString(R.string.resolution_reset_body),
                                confirm = context.getString(R.string.yes),
                                dismiss = context.getString(R.string.no)
                            )
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.resolution_safety_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaxSpace.groupPadding)
                )
            }

            ConfirmDialogHost(handle = resetDialog)
        }
    }
}

@Composable
private fun presetTitle(id: String): String = when (id) {
    "native" -> stringResource(R.string.resolution_preset_native)
    "balanced" -> stringResource(R.string.resolution_preset_balanced)
    "performance" -> stringResource(R.string.resolution_preset_performance)
    "battery_saver" -> stringResource(R.string.resolution_preset_battery)
    else -> id
}
