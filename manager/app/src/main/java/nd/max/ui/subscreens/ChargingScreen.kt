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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.ChargingViewModel

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
                    valueText = viewModel.capacityPercent.toString(),
                    unitText = "%",
                    fraction = viewModel.capacityPercent / 100f,
                    isLive = true,
                    accentColor = if (viewModel.isCharging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    subtitle = if (viewModel.isCharging)
                        stringResource(R.string.charging_status_charging)
                    else
                        stringResource(R.string.charging_status_not_charging)
                )
                Spacer(Modifier.height(12.dp))
                StatTickRow(
                    stats = listOf(
                        stringResource(R.string.charging_voltage) to "${"%.2f".format(viewModel.voltageMv / 1000f)} V",
                        stringResource(R.string.charging_current) to "${"%.2f".format(viewModel.currentMa / 1000f)} A",
                        stringResource(R.string.charging_temp) to "${"%.1f".format(viewModel.temperatureC)}\u00b0C",
                        stringResource(R.string.charging_power) to "${"%.1f".format((viewModel.voltageMv / 1000f) * (viewModel.currentMa / 1000f))} W"
                    )
                )
            }

            item {
                MaxDecisionCard(
                    state = if (viewModel.isCharging) "Charging at ${"%.1f".format((viewModel.voltageMv / 1000f) * (viewModel.currentMa / 1000f))} W" else "Not charging",
                    guidance = if (viewModel.isCharging) "Live charging telemetry is available below. Use charging controls only when you understand the device's supported limits." else "Connect a charger to observe live charging telemetry and available charging controls.",
                    icon = Icons.Outlined.BatteryChargingFull,
                    accent = if (viewModel.isCharging) colorScheme.tertiary else colorScheme.primary
                )
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
                                onClick = { navController.navigate("bypasschg") },
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
fun ChargingTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.charging_title),
        onBack = onBack,
        accentIcon = Icons.Filled.BatteryChargingFull,
        accent = MaterialTheme.colorScheme.tertiary
    )
}
