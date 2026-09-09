/*
 * Adapted from ZKM (Zuan Kernel Manager) ui/fpsmanager/FpsOverlayTab.kt.
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

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.service.FpsOverlayService
import nd.max.ui.component.*
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.util.FpsOverlayPrefs
import nd.max.ui.util.FpsReadMode

private val PRESET_COLORS = listOf(
    "#00E676" to "Green", "#00E5FF" to "Cyan", "#FFEA00" to "Yellow",
    "#FF6D00" to "Orange", "#FF1744" to "Red", "#FFFFFF" to "White"
)

@Composable
fun FpsOverlayScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var state by remember { mutableStateOf(FpsOverlayPrefs.load(context)) }
    var hasOverlayPermission by remember { mutableStateOf(canDrawOverlays(context)) }
    var fpsReadModeIndex by remember {
        nd.max.ui.util.FpsMonitorUtil.init(context)
        mutableStateOf(nd.max.ui.util.FpsMonitorUtil.currentMode.ordinal)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = canDrawOverlays(context)
        if (hasOverlayPermission && state.enabled) startOverlayService(context)
    }

    fun persist(newState: FpsOverlayPrefs.State) {
        state = newState
        FpsOverlayPrefs.save(context, newState)
        if (newState.enabled && hasOverlayPermission) startOverlayService(context)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !hasOverlayPermission) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        persist(state.copy(enabled = enabled))
        if (!enabled) stopOverlayService(context)
    }

    
    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = stringResource(R.string.fps_overlay_title),
                    onBack = { navController.popBackStack() },
                    accentIcon = Icons.Filled.Speed,
                    accent = MaterialTheme.colorScheme.tertiary
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = 16.dp, end = 16.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    MaxManagerInsight(
                        text = stringResource(R.string.fps_overlay_intro),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                item {
                    ExpressiveList(content = listOf {
                        ExpressiveSwitchItem(
                            icon = Icons.Outlined.PictureInPicture,
                            title = stringResource(R.string.fps_overlay_enable),
                            summary = if (hasOverlayPermission) null else stringResource(R.string.fps_overlay_needs_permission),
                            checked = state.enabled,
                            onCheckedChange = { setEnabled(it) }
                        )
                    })
                }

                item { TweaksSectionTitle(stringResource(R.string.fps_overlay_section_style)) }
                item {
                    ExpressiveList(content = listOf(
                        {
                            ExpressiveDropdownItem(
                                icon = Icons.Outlined.Style,
                                title = stringResource(R.string.fps_overlay_style),
                                items = listOf(
                                    stringResource(R.string.fps_overlay_style_android),
                                    stringResource(R.string.fps_overlay_style_pc),
                                    stringResource(R.string.fps_overlay_style_mini)
                                ),
                                selectedIndex = state.styleMode,
                                onItemSelected = { persist(state.copy(styleMode = it)) }
                            )
                        },
                        {
                            ExpressiveDropdownItem(
                                icon = Icons.Outlined.ScreenRotation,
                                title = stringResource(R.string.fps_overlay_orientation),
                                items = listOf(
                                    stringResource(R.string.fps_overlay_orientation_vertical),
                                    stringResource(R.string.fps_overlay_orientation_horizontal)
                                ),
                                enabled = state.styleMode == 0,
                                selectedIndex = state.orientation,
                                onItemSelected = { persist(state.copy(orientation = it)) }
                            )
                        },
                        {
                            ExpressiveDropdownItem(
                                icon = Icons.Outlined.Speed,
                                title = stringResource(R.string.fps_overlay_read_mode),
                                summary = stringResource(R.string.fps_overlay_read_mode_desc),
                                items = listOf(
                                    stringResource(R.string.fps_overlay_mode_surfaceflinger),
                                    stringResource(R.string.fps_overlay_mode_kernel),
                                    stringResource(R.string.fps_overlay_mode_dumpsys)
                                ),
                                selectedIndex = fpsReadModeIndex,
                                onItemSelected = { idx ->
                                    fpsReadModeIndex = idx
                                    nd.max.ui.util.FpsMonitorUtil.setMode(context, FpsReadMode.entries[idx])
                                }
                            )
                        }
                    ))
                }

                item {
                    ColorSwatchRow(
                        selectedHex = state.colorHex,
                        onSelect = { persist(state.copy(colorHex = it)) }
                    )
                }

                item {
                    ExpressiveList(content = listOf(
                        {
                            ExpressiveSliderItem(
                                icon = Icons.Outlined.FormatSize,
                                title = stringResource(R.string.fps_overlay_text_size),
                                badgeText = "${state.textSizeSp.toInt()}sp",
                                sliderPosition = state.textSizeSp,
                                valueRange = 10f..24f,
                                steps = 6,
                                onValueChange = { state = state.copy(textSizeSp = it) },
                                onValueChangeFinished = { persist(state) }
                            )
                        },
                        {
                            ExpressiveSliderItem(
                                icon = Icons.Outlined.Opacity,
                                title = stringResource(R.string.fps_overlay_bg_alpha),
                                badgeText = "${(state.bgAlpha * 100).toInt()}%",
                                sliderPosition = state.bgAlpha,
                                valueRange = 0f..1f,
                                steps = 9,
                                onValueChange = { state = state.copy(bgAlpha = it) },
                                onValueChangeFinished = { persist(state) }
                            )
                        },
                        {
                            ExpressiveSliderItem(
                                icon = Icons.Outlined.SwapHoriz,
                                title = stringResource(R.string.fps_overlay_width_scale),
                                badgeText = "x${"%.1f".format(state.widthScale)}",
                                sliderPosition = state.widthScale,
                                valueRange = 0.6f..2f,
                                steps = 13,
                                onValueChange = { state = state.copy(widthScale = it) },
                                onValueChangeFinished = { persist(state) }
                            )
                        }
                    ))
                }

                item { TweaksSectionTitle(stringResource(R.string.fps_overlay_section_metrics)) }
                item {
                    ExpressiveList(content = listOf(
                        { ExpressiveSwitchItem(title = stringResource(R.string.fps_overlay_metric_fps), checked = state.showFps, onCheckedChange = { persist(state.copy(showFps = it)) }) },
                        { ExpressiveSwitchItem(title = stringResource(R.string.fps_overlay_metric_cpu), checked = state.showCpu, onCheckedChange = { persist(state.copy(showCpu = it)) }) },
                        { ExpressiveSwitchItem(title = stringResource(R.string.fps_overlay_metric_ram), checked = state.showRam, onCheckedChange = { persist(state.copy(showRam = it)) }) },
                        { ExpressiveSwitchItem(title = stringResource(R.string.fps_overlay_metric_watt), checked = state.showWatt, onCheckedChange = { persist(state.copy(showWatt = it)) }) },
                        { ExpressiveSwitchItem(title = stringResource(R.string.fps_overlay_metric_temp), checked = state.showTemp, onCheckedChange = { persist(state.copy(showTemp = it)) }) },
                        {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.fps_overlay_metric_render),
                                summary = stringResource(R.string.fps_overlay_metric_render_desc),
                                checked = state.showRender,
                                onCheckedChange = { persist(state.copy(showRender = it)) }
                            )
                        }
                    ))
                }
            }
        }
    }
    }


@Composable
private fun ColorSwatchRow(selectedHex: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.fps_overlay_accent_color),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PRESET_COLORS.forEach { (hex, _) ->
                val color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
                val selected = hex.equals(selectedHex, ignoreCase = true)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(if (selected) 36.dp else 30.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(color)
                        .then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                            else Modifier
                        )
                        .clickable { onSelect(hex) }
                )
            }
        }
    }
}

private fun canDrawOverlays(context: android.content.Context): Boolean =
    Settings.canDrawOverlays(context)

private fun startOverlayService(context: android.content.Context) {
    val intent = Intent(context, FpsOverlayService::class.java)
    androidx.core.content.ContextCompat.startForegroundService(context, intent)
}

private fun stopOverlayService(context: android.content.Context) {
    context.startService(Intent(context, FpsOverlayService::class.java).setAction(FpsOverlayService.ACTION_STOP))
}
