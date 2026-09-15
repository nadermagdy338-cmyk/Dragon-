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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package nd.max.ui.subscreens
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.ChargingViewModel
import java.util.Locale

private const val LTR_MARK = "\u200E"

private fun measurement(value: Float, unit: String, decimals: Int): String =
    "$LTR_MARK${String.format(Locale.US, "%.${decimals}f", value)} $unit$LTR_MARK"

private fun currentMeasurement(currentMa: Int): String = when {
    kotlin.math.abs(currentMa) >= 1000 -> measurement(currentMa / 1000f, "A", 2)
    else -> "$LTR_MARK$currentMa mA$LTR_MARK"
}

@Composable
fun ChargingScreen(
    navController: NavController,
    viewModel: ChargingViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.loadState() }

    
    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { ChargingTopAppBar(scrollBehavior, onBack = { navController.popBackStack() }) },
        containerColor = colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = MaxUiMetrics.screenHorizontalPadding,
                end = MaxUiMetrics.screenHorizontalPadding,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            item {
                RadialGaugeCard(
                    title = stringResource(R.string.charging_gauge_title),
                    valueText = "$LTR_MARK${viewModel.capacityPercent}$LTR_MARK",
                    unitText = "%",
                    fraction = viewModel.capacityPercent / 100f,
                    isLive = true,
                    accentColor = if (viewModel.isCharging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    tipMarkerMinFraction = 0.15f,
                    subtitle = if (viewModel.isCharging)
                        stringResource(R.string.charging_status_charging)
                    else
                        stringResource(R.string.charging_status_not_charging)
                )
                Spacer(Modifier.height(12.dp))
                val voltage = viewModel.voltageMv / 1000f
                val current = viewModel.currentMa / 1000f
                val power = voltage * current
                StatTickRow(
                    stats = listOf(
                        stringResource(R.string.charging_voltage) to measurement(voltage, "V", 2),
                        stringResource(R.string.charging_current) to currentMeasurement(viewModel.currentMa),
                        stringResource(R.string.charging_temp) to measurement(viewModel.temperatureC, "°C", 1),
                        stringResource(R.string.charging_power) to measurement(power, "W", 1)
                    ),
                    valueTextDirection = TextDirection.Ltr
                )
            }

            item {
                ChargingReadinessCard(viewModel)
                Spacer(Modifier.height(12.dp))
            }

            item { TweaksSectionTitle(stringResource(R.string.charging_live_details_title)) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.charging_usb_type), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(viewModel.chargerType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.charging_connection), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            if (viewModel.usbConnected) stringResource(R.string.charging_connected)
                            else stringResource(R.string.charging_disconnected),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.charging_health), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            viewModel.batteryHealthPercent?.let { "$it%" } ?: "-",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.charging_cycle_count), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            viewModel.cycleCount?.toString() ?: "-",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.charging_technology), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(viewModel.batteryTechnology, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            item { TweaksSectionTitle(stringResource(R.string.charging_controls_title)) }

            item {
                ExpressiveList(
                    content = buildList {
                        if (viewModel.sicModeNodePath != null) {
                            add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Filled.Bolt,
                                    title = stringResource(R.string.charging_sic_boost_title),
                                    summary = if (viewModel.sicBoostEnabled)
                                        stringResource(R.string.charging_sic_boost_active, viewModel.sicModeCurrentValue ?: "-")
                                    else
                                        stringResource(R.string.charging_sic_boost_desc),
                                    checked = viewModel.sicBoostEnabled,
                                    onCheckedChange = { viewModel.setSicBoost(it) }
                                )
                            }
                        }
                        if (viewModel.fastChargeAvailable) {
                            var sliderMa by remember(viewModel.fastChargeCurrentMa) {
                                mutableStateOf(viewModel.fastChargeCurrentMa.toFloat())
                            }
                            val maxMa = viewModel.fastChargeMaxMa.coerceAtLeast(500).toFloat()
                            add {
                                ExpressiveSliderItem(
                                    icon = Icons.Outlined.Bolt,
                                    title = stringResource(R.string.charging_fast_current),
                                    badgeText = "${sliderMa.toInt()} mA",
                                    sliderPosition = sliderMa,
                                    valueRange = 300f..maxMa,
                                    steps = 0,
                                    onValueChange = { sliderMa = it },
                                    onValueChangeFinished = { viewModel.applyFastChargeCurrentMa(sliderMa.toInt()) }
                                )
                            }
                        }
                        add {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Outlined.BatteryStd) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.BypassCharging) },
                                headlineContent = { Text(stringResource(R.string.charging_bypass_title)) },
                                supportingContent = { Text(stringResource(R.string.charging_bypass_desc)) }
                            )
                        }
                        if (viewModel.chargeLimitSupported) {
                            val limitEnabled = viewModel.chargeLimitPercent < 100
                            add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Outlined.BatteryChargingFull,
                                    title = stringResource(R.string.charging_limit_title),
                                    summary = if (limitEnabled)
                                        stringResource(R.string.charging_limit_active, viewModel.chargeLimitPercent)
                                    else
                                        stringResource(R.string.charging_limit_desc),
                                    checked = limitEnabled,
                                    onCheckedChange = { viewModel.setChargeLimitEnabled(it) }
                                )
                            }
                            if (limitEnabled) {
                                var sliderPercent by remember(viewModel.chargeLimitPercent) {
                                    mutableStateOf(viewModel.chargeLimitPercent.toFloat())
                                }
                                add {
                                    ExpressiveSliderItem(
                                        icon = Icons.Outlined.BatteryStd,
                                        title = stringResource(R.string.charging_limit_slider_title),
                                        badgeText = "${sliderPercent.toInt()}%",
                                        sliderPosition = sliderPercent,
                                        valueRange = 50f..95f,
                                        steps = 8,
                                        onValueChange = { sliderPercent = it },
                                        onValueChangeFinished = { viewModel.applyChargeLimit(sliderPercent.toInt()) }
                                    )
                                }
                            }
                        }
                        add {
                            ExpressiveSwitchItem(
                                icon = Icons.Outlined.BatterySaver,
                                title = stringResource(R.string.charging_battery_saver_title),
                                summary = stringResource(R.string.charging_battery_saver_desc),
                                checked = viewModel.batterySaverEnabled,
                                onCheckedChange = { viewModel.setBatterySaver(it) }
                            )
                        }
                    }
                )
            }

            if (!viewModel.fastChargeAvailable && viewModel.sicModeNodePath == null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.charging_fast_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
    }
    }


@Composable
private fun ChargingReadinessCard(viewModel: ChargingViewModel) {
    val colors = MaterialTheme.colorScheme
    val accent = if (viewModel.isCharging) colors.tertiary else colors.primary
    val chargeLimitEnabled = viewModel.chargeLimitSupported && viewModel.chargeLimitPercent < 100
    val current = currentMeasurement(viewModel.currentMa)
    val power = measurement((viewModel.voltageMv / 1000f) * (viewModel.currentMa / 1000f), "W", 1)

    MaxSurface(accent = accent) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = if (viewModel.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(12.dp).size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(if (viewModel.isCharging) R.string.charging_session_active else R.string.charging_battery_ready),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (viewModel.isCharging) {
                        stringResource(R.string.charging_session_telemetry, power, current)
                    } else {
                        stringResource(R.string.charging_battery_ready_desc, viewModel.capacityPercent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f)) {
                Text(
                    text = if (viewModel.isCharging) stringResource(R.string.home_active) else stringResource(R.string.home_idle),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChargingReadinessMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.BatterySaver,
                label = stringResource(R.string.charging_battery_saver_title),
                value = if (viewModel.batterySaverEnabled) stringResource(R.string.label_enabled) else stringResource(R.string.disabled),
                accent = if (viewModel.batterySaverEnabled) colors.tertiary else colors.onSurfaceVariant
            )
            ChargingReadinessMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.BatteryChargingFull,
                label = stringResource(R.string.charging_limit_title),
                value = when {
                    !viewModel.chargeLimitSupported -> stringResource(R.string.home_sensor_unavailable)
                    chargeLimitEnabled -> "$LTR_MARK${viewModel.chargeLimitPercent}%$LTR_MARK"
                    else -> stringResource(R.string.charging_limit_full)
                },
                accent = if (chargeLimitEnabled) colors.tertiary else colors.onSurfaceVariant
            )
            ChargingReadinessMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.HealthAndSafety,
                label = stringResource(R.string.charging_health),
                value = viewModel.batteryHealthPercent?.let { "$LTR_MARK$it%$LTR_MARK" } ?: "—",
                accent = colors.primary
            )
        }
    }
}

@Composable
private fun ChargingReadinessMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ChargingTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.charging_title),
        onBack = onBack,
        accentIcon = Icons.Filled.BatteryChargingFull,
        accent = MaterialTheme.colorScheme.tertiary
    )
}
