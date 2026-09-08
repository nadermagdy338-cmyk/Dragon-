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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.MaliFreqViewModel

@Composable
fun MaliGpuFreqScreen(
    navController: NavController,
    viewModel: MaliFreqViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val bypassConfirmDialog = rememberConfirmDialog(
        onConfirm = { viewModel.enableThrottleBypass() },
        onDismiss = {}
    )

    LaunchedEffect(Unit) { viewModel.loadState() }

    
    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MaliGpuTopAppBar(scrollBehavior, onBack = { navController.popBackStack() })
        },
        containerColor = colorScheme.surface
    ) { innerPadding ->
        when (viewModel.isAvailable) {
            null -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                SectionLoadingIndicator()
            }
            false -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.mali_freq_unavailable))
            }
            true -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            ) {
                item {
                    ControlScreenIntro(
                        icon = Icons.Outlined.Memory,
                        title = "Mali GPU",
                        description = "ترددات وقيود GPU الفعلية على الدرايفر المكتشف في الجهاز.",
                        accent = colorScheme.tertiary,
                        status = "LIVE"
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }

                item {
                    WarningBanner(
                        text = stringResource(R.string.mali_freq_screen_warning),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                item {
                    val freqs = viewModel.availableFrequenciesKhz
                    val maxHw = (freqs.lastOrNull() ?: 1L).coerceAtLeast(1L)
                    val fraction = viewModel.curFreqKhz.toFloat() / maxHw.toFloat()

                    // The gauge and trend graph both lean toward the theme's
                    // error tone as load climbs, so the whole panel doubles
                    // as a soft thermal-awareness cue without a separate
                    // temperature readout.
                    val loadTone = when {
                        viewModel.loadPercent >= 85 -> colorScheme.error
                        viewModel.loadPercent >= 55 -> colorScheme.tertiary
                        else -> colorScheme.primary
                    }
                    val animatedTone by animateColorAsState(
                        targetValue = loadTone,
                        animationSpec = tween(500),
                        label = "maliLoadTone"
                    )

                    RadialGaugeCard(
                        title = stringResource(R.string.mali_gauge_title),
                        valueText = (viewModel.curFreqKhz / 1000).toString(),
                        unitText = "MHz",
                        fraction = fraction,
                        isLive = true,
                        accentColor = animatedTone,
                        subtitle = stringResource(R.string.mali_gauge_subtitle, maxHw / 1000)
                    )
                    Spacer(Modifier.height(12.dp))
                    MaxDecisionCard(
                        state = "${viewModel.loadPercent}% GPU load",
                        guidance = when {
                            viewModel.loadPercent >= 85 -> "High load detected. Avoid adding unnecessary limits while the workload is active."
                            viewModel.loadPercent >= 55 -> "GPU is actively working. Changes below should be made with the live frequency in mind."
                            else -> "GPU load is currently light. Frequency changes will affect how aggressively the driver responds."
                        },
                        icon = Icons.Outlined.Memory,
                        accent = animatedTone
                    )
                    Spacer(Modifier.height(12.dp))
                    StatTickRow(
                        stats = listOf(
                            stringResource(R.string.mali_stat_load) to "${viewModel.loadPercent}%",
                            stringResource(R.string.mali_stat_preset) to presetLabel(viewModel.selectedPresetId),
                            stringResource(R.string.mali_stat_hwmax) to "${maxHw / 1000} MHz"
                        )
                    )
                    if (viewModel.throttleCoolingDevicePath != null) {
                        Spacer(Modifier.height(8.dp))
                        StatTickRow(
                            stats = listOf(
                                stringResource(R.string.mali_stat_thermal_clamp) to thermalClampLabel(
                                    bypassed = viewModel.throttleBypassEnabled,
                                    state = viewModel.thermalClampState
                                ),
                                stringResource(R.string.mali_stat_active_range) to
                                    "${viewModel.minFreqKhz / 1000}\u2013${viewModel.maxFreqKhz / 1000} MHz"
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LiveHistoryGraph(
                        samples = viewModel.freqHistory,
                        label = stringResource(R.string.mali_activity_title),
                        valueLabel = "${viewModel.curFreqKhz / 1000} MHz",
                        lineColor = animatedTone,
                        emptyStateText = stringResource(R.string.mali_activity_collecting)
                    )
                }

                item { TweaksSectionTitle(stringResource(R.string.mali_freq_presets_title)) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MaliFreqViewModel.PRESETS.forEach { preset ->
                            MaliPresetCard(
                                icon = presetIcon(preset.id),
                                title = presetLabel(preset.id),
                                description = presetDesc(preset.id),
                                accentColor = presetTone(preset.id),
                                selected = viewModel.selectedPresetId == preset.id,
                                onClick = { viewModel.applyPreset(preset) }
                            )
                        }
                    }
                }

                if (viewModel.throttleCoolingDevicePath != null) {
                    item { TweaksSectionTitle(stringResource(R.string.mali_throttle_bypass_section)) }
                    item {
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.Whatshot,
                                        title = stringResource(R.string.mali_throttle_bypass_title),
                                        summary = stringResource(R.string.mali_throttle_bypass_desc),
                                        checked = viewModel.throttleBypassEnabled,
                                        onCheckedChange = { turnOn ->
                                            if (turnOn) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                bypassConfirmDialog.showConfirm(
                                                    title = context.getString(R.string.mali_throttle_bypass_confirm_title),
                                                    content = context.getString(R.string.mali_throttle_bypass_confirm_body),
                                                    confirm = context.getString(R.string.yes),
                                                    dismiss = context.getString(R.string.no)
                                                )
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.disableThrottleBypass()
                                            }
                                        }
                                    )
                                }
                            )
                        )
                    }
                    item {
                        ExpressiveInfoCard(
                            supportingContent = { Text(stringResource(R.string.mali_throttle_bypass_warning)) },
                            leadingContent = { LeadingIcon(icon = Icons.Filled.Warning, containerColor = colorScheme.errorContainer, contentColor = colorScheme.error) },
                            containerColor = colorScheme.errorContainer.copy(alpha = 0.25f),
                            onClick = {}
                        )
                    }
                }

                item { TweaksSectionTitle(stringResource(R.string.mali_freq_manual_title)) }

                item {
                    val freqs = viewModel.availableFrequenciesKhz
                    if (freqs.size > 1) {
                        val minHw = freqs.first().toFloat()
                        val maxHw = freqs.last().toFloat()
                        var minSliderMhz by remember(viewModel.minFreqKhz) {
                            mutableStateOf(viewModel.minFreqKhz.toFloat())
                        }
                        var maxSliderMhz by remember(viewModel.maxFreqKhz) {
                            mutableStateOf(viewModel.maxFreqKhz.toFloat())
                        }
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveSliderItem(
                                        icon = Icons.Outlined.ArrowDownward,
                                        title = stringResource(R.string.mali_freq_min_floor),
                                        badgeText = "${(minSliderMhz / 1000).toInt()} MHz",
                                        sliderPosition = minSliderMhz,
                                        valueRange = minHw..maxHw,
                                        steps = (freqs.size - 2).coerceAtLeast(0),
                                        onValueChange = { minSliderMhz = it },
                                        onValueChangeFinished = {
                                            val nearest = freqs.minByOrNull { kotlin.math.abs(it - minSliderMhz.toLong()) }
                                                ?: minSliderMhz.toLong()
                                            viewModel.applyCustomMin(nearest)
                                        }
                                    )
                                },
                                {
                                    ExpressiveSliderItem(
                                        icon = Icons.Outlined.ArrowUpward,
                                        title = stringResource(R.string.mali_freq_max_ceiling),
                                        badgeText = "${(maxSliderMhz / 1000).toInt()} MHz",
                                        sliderPosition = maxSliderMhz,
                                        valueRange = minHw..maxHw,
                                        steps = (freqs.size - 2).coerceAtLeast(0),
                                        onValueChange = { maxSliderMhz = it },
                                        onValueChangeFinished = {
                                            val nearest = freqs.minByOrNull { kotlin.math.abs(it - maxSliderMhz.toLong()) }
                                                ?: maxSliderMhz.toLong()
                                            viewModel.applyCustomMax(nearest)
                                        }
                                    )
                                }
                            )
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.mali_freq_safety_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                item { TweaksSectionTitle(stringResource(R.string.mali_ged_boost_section)) }
                item {
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveSwitchItem(
                                    icon = Icons.Filled.FlashOn,
                                    title = stringResource(R.string.mali_ged_boost_title),
                                    summary = if (viewModel.gedBoostNode != null)
                                        stringResource(R.string.mali_ged_boost_desc)
                                    else
                                        stringResource(R.string.mali_ged_boost_unavailable),
                                    checked = viewModel.gedBoostEnabled,
                                    enabled = viewModel.gedBoostNode != null,
                                    onCheckedChange = { viewModel.setGedBoost(it) }
                                )
                            }
                        )
                    )
                }

                if (viewModel.powerPolicyDir != null && viewModel.availablePowerPolicies.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        ExpressiveInfoCard(
                            leadingContent = { LeadingIcon(icon = Icons.Outlined.Bolt, containerColor = colorScheme.tertiaryContainer, contentColor = colorScheme.onTertiaryContainer) },
                            supportingContent = {
                                Column {
                                    Text(stringResource(R.string.mali_power_policy_title), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.mali_power_policy_desc), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailingContent = {
                                Text(
                                    text = powerPolicyLabel(viewModel.currentPowerPolicy),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.tertiary
                                )
                            },
                            onClick = {}
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            viewModel.availablePowerPolicies.forEach { policy ->
                                val selected = viewModel.currentPowerPolicy == policy
                                androidx.compose.material3.FilterChip(
                                    modifier = Modifier.weight(1f),
                                    selected = selected,
                                    onClick = { viewModel.setPowerPolicy(policy) },
                                    label = {
                                        Column {
                                            Text(powerPolicyLabel(policy), style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                powerPolicyDesc(policy),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant
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
    }
    }

    ConfirmDialogHost(handle = bypassConfirmDialog)
    }


@Composable
private fun thermalClampLabel(bypassed: Boolean, state: Int?): String = when {
    bypassed -> stringResource(R.string.mali_thermal_bypassed)
    state == null -> "\u2013"
    state == 0 -> stringResource(R.string.mali_thermal_active)
    else -> stringResource(R.string.mali_thermal_throttled, state)
}

/**
 * Labels/descriptions for whatever policies the running kbase driver actually
 * reports (never a hardcoded pair) — unrecognized policy names still get a
 * readable fallback instead of being hidden.
 */
@Composable
private fun powerPolicyLabel(id: String?): String = when (id) {
    "coarse_demand" -> stringResource(R.string.mali_power_policy_coarse)
    "always_on" -> stringResource(R.string.mali_power_policy_always_on)
    "demand" -> stringResource(R.string.mali_power_policy_demand)
    null -> "\u2013"
    else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun powerPolicyDesc(id: String): String = when (id) {
    "coarse_demand" -> stringResource(R.string.mali_power_policy_coarse_desc)
    "always_on" -> stringResource(R.string.mali_power_policy_always_on_desc)
    "demand" -> stringResource(R.string.mali_power_policy_demand_desc)
    else -> ""
}

@Composable
private fun presetLabel(id: String): String = when (id) {
    "battery_saver" -> stringResource(R.string.mali_preset_battery)
    "balanced" -> stringResource(R.string.mali_preset_balanced)
    "gaming_dynamic" -> stringResource(R.string.mali_preset_gaming)
    "max_performance" -> stringResource(R.string.mali_preset_max)
    else -> id
}

@Composable
private fun presetDesc(id: String): String = when (id) {
    "battery_saver" -> stringResource(R.string.mali_preset_battery_desc)
    "balanced" -> stringResource(R.string.mali_preset_balanced_desc)
    "gaming_dynamic" -> stringResource(R.string.mali_preset_gaming_desc)
    "max_performance" -> stringResource(R.string.mali_preset_max_desc)
    else -> ""
}

/** Each preset gets its own icon so the list scans visually instead of reading as four identical rows. */
private fun presetIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    "battery_saver" -> Icons.Outlined.BatterySaver
    "balanced" -> Icons.Outlined.Balance
    "gaming_dynamic" -> Icons.Outlined.SportsEsports
    "max_performance" -> Icons.Outlined.Bolt
    else -> Icons.Outlined.Tune
}

/**
 * Semantic, theme-derived tone per preset (never a hardcoded literal hue) so the
 * preset colors stay in harmony with the user's Material You / custom seed color
 * instead of clashing with it.
 */
@Composable
private fun presetTone(id: String): Color {
    val colorScheme = MaterialTheme.colorScheme
    return when (id) {
        "battery_saver" -> colorScheme.tertiary
        "balanced" -> colorScheme.primary
        "gaming_dynamic" -> colorScheme.secondary
        "max_performance" -> colorScheme.error
        else -> colorScheme.primary
    }
}

/**
 * Rich, tappable preset row: color-coded icon chip, title + description, and an
 * animated selected state (tinted container, soft border, check glyph) instead of
 * a bare radio button — the four presets read as distinct "modes" rather than a
 * plain settings list.
 */
@Composable
private fun MaliPresetCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val pressScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "presetCardScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(250),
        label = "presetCardContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(250),
        label = "presetCardBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .border(1.5.dp, borderColor, MaterialTheme.shapes.large)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (selected) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor)
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        AnimatedVisibility(
            visible = selected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = accentColor
            )
        }
    }
}

@Composable
fun MaliGpuTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.mali_freq_title),
        onBack = onBack,
        accentIcon = Icons.Filled.DeveloperBoard,
        accent = MaterialTheme.colorScheme.tertiary
    )
}
