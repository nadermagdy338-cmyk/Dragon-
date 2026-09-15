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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import nd.max.R
import nd.max.service.FpsOverlayService
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.util.FpsMonitorUtil
import nd.max.ui.util.FpsOverlayPrefs
import nd.max.ui.util.FpsReadMode

/**
 * Colour presets for the overlay text.
 *
 * Each entry now carries a translatable name so the swatches are not a
 * colour-only control: screen readers announce the name, and the selected
 * swatch also shows a check mark.
 */
private val OVERLAY_COLORS: List<Pair<String, Int>> = listOf(
    "#00E676" to R.string.max_fps_color_green,
    "#00E5FF" to R.string.max_fps_color_cyan,
    "#FFEA00" to R.string.max_fps_color_yellow,
    "#FF6D00" to R.string.max_fps_color_orange,
    "#FF1744" to R.string.max_fps_color_red,
    "#FFFFFF" to R.string.max_fps_color_white
)

/**
 * On-screen performance overlay.
 *
 * Rebuilt on the MaxManager Design Language. What changed and why:
 *
 *  - Missing overlay permission used to be a grey summary line under a
 *    switch. It is a real blocking condition, so it is now a
 *    PermissionRequired banner with a direct action into the Android
 *    permission screen.
 *  - The permission is re-read on every resume, so revoking it from system
 *    settings no longer leaves this screen claiming the overlay is available.
 *  - Style, orientation and frame source were dropdowns for 2-3 options
 *    each. Dropdowns hide the choices; segmented controls show them.
 *  - Orientation is genuinely inert outside the Android list style, so the
 *    control is now disabled *and* says why, instead of silently doing
 *    nothing when tapped.
 *  - The colour row was colour-only with sub-minimum touch targets. Each
 *    swatch is now a 48dp radio target with a name, a spoken selected state
 *    and a contrast-aware check mark.
 *  - The frame source section states that sampling falls back to another
 *    source when the selected one returns nothing, which is what the sampler
 *    already does. Hiding that made the picker look stronger than it is.
 */
@Composable
fun FpsOverlayScreen(navController: NavController) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var state by remember { mutableStateOf(FpsOverlayPrefs.load(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var readModeIndex by remember {
        FpsMonitorUtil.init(context)
        mutableStateOf(FpsMonitorUtil.currentMode.ordinal)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (hasOverlayPermission && state.enabled) startOverlayService(context)
    }

    // The permission can be revoked from system settings while this screen is
    // in the background, so re-read it on resume instead of trusting a value
    // captured once at first composition.
    LifecycleResumeEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    fun requestOverlayPermission() {
        permissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun persist(newState: FpsOverlayPrefs.State) {
        state = newState
        FpsOverlayPrefs.save(context, newState)
        if (newState.enabled && hasOverlayPermission) startOverlayService(context)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !hasOverlayPermission) {
            requestOverlayPermission()
            return
        }
        persist(state.copy(enabled = enabled))
        if (!enabled) stopOverlayService(context)
    }

    val styleOptions = listOf(
        stringResource(R.string.fps_overlay_style_android),
        stringResource(R.string.fps_overlay_style_pc),
        stringResource(R.string.fps_overlay_style_mini)
    )
    val orientationOptions = listOf(
        stringResource(R.string.fps_overlay_orientation_vertical),
        stringResource(R.string.fps_overlay_orientation_horizontal)
    )
    val sourceOptions = listOf(
        stringResource(R.string.fps_overlay_mode_surfaceflinger),
        stringResource(R.string.fps_overlay_mode_kernel),
        stringResource(R.string.fps_overlay_mode_dumpsys)
    )

    val orientationEnabled = state.styleMode == 0

    ScreenAccentProvider(scheme.tertiary) {
        MaxScreen(
            title = stringResource(R.string.fps_overlay_title),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Rounded.Speed,
            accent = scheme.tertiary,
            banner = if (hasOverlayPermission) {
                null
            } else {
                MaxCondition(
                    kind = MaxConditionKind.PermissionRequired,
                    title = stringResource(R.string.max_fps_permission_title),
                    detail = stringResource(R.string.max_fps_permission_detail),
                    primaryActionLabel = stringResource(R.string.max_fps_permission_action),
                    onPrimaryAction = { requestOverlayPermission() }
                )
            }
        ) {
            MaxSection(
                title = stringResource(R.string.max_fps_section_overlay),
                description = stringResource(R.string.max_fps_section_overlay_desc)
            ) {
                MaxGroup {
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_enable),
                        subtitle = if (hasOverlayPermission) {
                            stringResource(R.string.fps_overlay_intro)
                        } else {
                            stringResource(R.string.fps_overlay_needs_permission)
                        },
                        checked = state.enabled && hasOverlayPermission,
                        onCheckedChange = { setEnabled(it) },
                        icon = Icons.Rounded.PictureInPicture,
                        iconTone = MaxTone.Accent
                    )
                }
            }

            MaxSection(
                title = stringResource(R.string.max_fps_section_source),
                description = stringResource(R.string.fps_overlay_read_mode_desc)
            ) {
                MaxSegmented(
                    options = sourceOptions,
                    selectedIndex = readModeIndex,
                    onSelect = { index ->
                        readModeIndex = index
                        FpsMonitorUtil.setMode(context, FpsReadMode.entries[index])
                    }
                )
                Text(
                    text = stringResource(R.string.max_fps_source_fallback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaxSpace.groupPadding)
                )
            }

            MaxSection(title = stringResource(R.string.max_fps_section_presentation)) {
                MaxSegmented(
                    options = styleOptions,
                    selectedIndex = state.styleMode,
                    onSelect = { index -> persist(state.copy(styleMode = index)) }
                )
                MaxSegmented(
                    options = orientationOptions,
                    selectedIndex = state.orientation,
                    onSelect = { index -> persist(state.copy(orientation = index)) },
                    enabled = orientationEnabled
                )
                if (!orientationEnabled) {
                    Text(
                        text = stringResource(R.string.max_fps_orientation_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaxSpace.groupPadding)
                    )
                }

                OverlayColorRow(
                    selectedHex = state.colorHex,
                    onSelect = { hex -> persist(state.copy(colorHex = hex)) }
                )

                MaxGroup {
                    MaxSliderRow(
                        title = stringResource(R.string.fps_overlay_text_size),
                        value = state.textSizeSp,
                        onValueChange = { state = state.copy(textSizeSp = it) },
                        valueText = "${state.textSizeSp.toInt()} sp",
                        valueRange = 10f..24f,
                        steps = 6,
                        onValueChangeFinished = { persist(state) }
                    )
                    MaxGroupDivider()
                    MaxSliderRow(
                        title = stringResource(R.string.fps_overlay_bg_alpha),
                        value = state.bgAlpha,
                        onValueChange = { state = state.copy(bgAlpha = it) },
                        valueText = "${(state.bgAlpha * 100).toInt()} %",
                        valueRange = 0f..1f,
                        steps = 9,
                        onValueChangeFinished = { persist(state) }
                    )
                    MaxGroupDivider()
                    MaxSliderRow(
                        title = stringResource(R.string.fps_overlay_width_scale),
                        value = state.widthScale,
                        onValueChange = { state = state.copy(widthScale = it) },
                        valueText = "×${"%.1f".format(state.widthScale)}",
                        valueRange = 0.6f..2f,
                        steps = 13,
                        onValueChangeFinished = { persist(state) }
                    )
                }
            }

            MaxSection(title = stringResource(R.string.fps_overlay_section_metrics)) {
                MaxGroup {
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_metric_fps),
                        checked = state.showFps,
                        onCheckedChange = { persist(state.copy(showFps = it)) }
                    )
                    MaxGroupDivider()
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_metric_cpu),
                        checked = state.showCpu,
                        onCheckedChange = { persist(state.copy(showCpu = it)) }
                    )
                    MaxGroupDivider()
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_metric_ram),
                        checked = state.showRam,
                        onCheckedChange = { persist(state.copy(showRam = it)) }
                    )
                    MaxGroupDivider()
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_metric_watt),
                        checked = state.showWatt,
                        onCheckedChange = { persist(state.copy(showWatt = it)) }
                    )
                    MaxGroupDivider()
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_metric_temp),
                        checked = state.showTemp,
                        onCheckedChange = { persist(state.copy(showTemp = it)) }
                    )
                    MaxGroupDivider()
                    MaxSwitchRow(
                        title = stringResource(R.string.fps_overlay_metric_render),
                        subtitle = stringResource(R.string.fps_overlay_metric_render_desc),
                        checked = state.showRender,
                        onCheckedChange = { persist(state.copy(showRender = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayColorRow(
    selectedHex: String,
    onSelect: (String) -> Unit
) {
    val selectedLabel = stringResource(R.string.max_fps_color_selected)
    val unselectedLabel = stringResource(R.string.max_fps_color_not_selected)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        Text(
            text = stringResource(R.string.fps_overlay_accent_color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaxSpace.groupPadding)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
            OVERLAY_COLORS.forEach { (hex, nameRes) ->
                val name = stringResource(nameRes)
                val selected = hex.equals(selectedHex, ignoreCase = true)
                val swatch = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                // Contrast-aware mark: a white check on a white swatch is invisible.
                val markColor = if (swatch.luminance() > 0.5f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(MaxSize.minTouchTarget)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(hex) }
                        )
                        .semantics {
                            contentDescription = name
                            stateDescription = if (selected) selectedLabel else unselectedLabel
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(MaxSize.rowIconContainer)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) MaxSpace.hairline else MaxSize.hairlineBorder,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = markColor,
                                modifier = Modifier.size(MaxSize.iconGlyphSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun startOverlayService(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, FpsOverlayService::class.java)
    )
}

private fun stopOverlayService(context: Context) {
    context.startService(
        Intent(context, FpsOverlayService::class.java)
            .setAction(FpsOverlayService.ACTION_STOP)
    )
}
