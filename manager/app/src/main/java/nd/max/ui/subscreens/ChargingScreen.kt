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

/*
 * Charge control.
 *
 * The old layout stacked a gauge, a "readiness" card, two hand built card
 * grids and a control list, which repeated the same facts three times. The
 * screen now keeps a single live hero, one group of read only facts and one
 * group of actions, all built from the shared design system.
 */

package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.component.RadialGaugeCard
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.component.StatTickRow
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxNavigationRow
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
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
    navController: NavHostController,
    viewModel: ChargingViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val navActions = MaxNavActions(navController)

    LaunchedEffect(Unit) { viewModel.loadState() }

    val accent = colorScheme.tertiary
    val gaugeAccent = if (viewModel.isCharging) colorScheme.tertiary else colorScheme.primary
    val voltage = viewModel.voltageMv / 1000f
    val amps = viewModel.currentMa / 1000f
    val chargeLimitActive = viewModel.chargeLimitPercent < 100
    val fastChargeCeiling = viewModel.fastChargeMaxMa.coerceAtLeast(500).toFloat()

    var fastChargeMa by remember(viewModel.fastChargeCurrentMa) {
        mutableStateOf(viewModel.fastChargeCurrentMa.toFloat())
    }
    var chargeLimit by remember(viewModel.chargeLimitPercent) {
        mutableStateOf(viewModel.chargeLimitPercent.coerceIn(50, 95).toFloat())
    }

    ScreenAccentProvider(accent) {
        MaxListScreen(
            title = stringResource(R.string.charging_title),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Filled.BatteryChargingFull,
            accent = accent,
            actions = {
                MaxHelpAction(
                    title = stringResource(R.string.charging_title),
                    body = stringResource(R.string.charging_screen_help)
                )
            },
            header = {
                RadialGaugeCard(
                    title = stringResource(R.string.charging_gauge_title),
                    valueText = "$LTR_MARK${viewModel.capacityPercent}$LTR_MARK",
                    unitText = "%",
                    fraction = viewModel.capacityPercent / 100f,
                    isLive = true,
                    accentColor = gaugeAccent,
                    tipMarkerMinFraction = 0.15f,
                    subtitle = if (viewModel.isCharging) {
                        stringResource(R.string.charging_status_charging)
                    } else {
                        stringResource(R.string.charging_status_not_charging)
                    }
                )
                StatTickRow(
                    stats = listOf(
                        stringResource(R.string.charging_voltage) to measurement(voltage, "V", 2),
                        stringResource(R.string.charging_current) to currentMeasurement(viewModel.currentMa),
                        stringResource(R.string.charging_temp) to measurement(viewModel.temperatureC, "\u00b0C", 1),
                        stringResource(R.string.charging_power) to measurement(voltage * amps, "W", 1)
                    ),
                    valueTextDirection = TextDirection.Ltr
                )
            }
        ) {
            item(key = "charging_details") {
                MaxSection(title = stringResource(R.string.charging_live_details_title)) {
                    MaxGroup {
                        ChargingFactRow(
                            label = stringResource(R.string.charging_usb_type),
                            value = viewModel.chargerType
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.charging_connection),
                            value = if (viewModel.usbConnected) {
                                stringResource(R.string.charging_connected)
                            } else {
                                stringResource(R.string.charging_disconnected)
                            }
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.charging_health),
                            value = viewModel.batteryHealthPercent?.let { "$LTR_MARK$it%$LTR_MARK" }
                                ?: stringResource(R.string.home_sensor_unavailable)
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.charging_cycle_count),
                            value = viewModel.cycleCount?.let { "$LTR_MARK$it$LTR_MARK" }
                                ?: stringResource(R.string.home_sensor_unavailable)
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.charging_technology),
                            value = viewModel.batteryTechnology
                        )
                    }
                }
            }

            item(key = "charging_controls") {
                MaxSection(title = stringResource(R.string.charging_controls_title)) {
                    MaxGroup {
                        if (viewModel.sicModeNodePath != null) {
                            MaxSwitchRow(
                                title = stringResource(R.string.charging_sic_boost_title),
                                checked = viewModel.sicBoostEnabled,
                                onCheckedChange = { viewModel.setSicBoost(it) },
                                subtitle = if (viewModel.sicBoostEnabled) {
                                    stringResource(
                                        R.string.charging_sic_boost_active,
                                        viewModel.sicModeCurrentValue.orEmpty()
                                    )
                                } else {
                                    stringResource(R.string.charging_sic_boost_desc)
                                },
                                icon = Icons.Outlined.Bolt,
                                iconTone = MaxTone.Accent
                            )
                            MaxGroupDivider()
                        }
                        if (viewModel.fastChargeAvailable) {
                            MaxSliderRow(
                                title = stringResource(R.string.charging_fast_current),
                                value = fastChargeMa.coerceIn(300f, fastChargeCeiling),
                                onValueChange = { fastChargeMa = it },
                                valueText = "$LTR_MARK${fastChargeMa.toInt()} mA$LTR_MARK",
                                valueRange = 300f..fastChargeCeiling,
                                onValueChangeFinished = {
                                    viewModel.applyFastChargeCurrentMa(fastChargeMa.toInt())
                                }
                            )
                            MaxGroupDivider()
                        }
                        if (viewModel.chargeLimitSupported) {
                            MaxSwitchRow(
                                title = stringResource(R.string.charging_limit_title),
                                checked = chargeLimitActive,
                                onCheckedChange = { viewModel.setChargeLimitEnabled(it) },
                                subtitle = if (chargeLimitActive) {
                                    stringResource(
                                        R.string.charging_limit_active,
                                        viewModel.chargeLimitPercent
                                    )
                                } else {
                                    stringResource(R.string.charging_limit_desc)
                                },
                                icon = Icons.Outlined.BatteryStd,
                                iconTone = MaxTone.Accent
                            )
                            if (chargeLimitActive) {
                                MaxGroupDivider()
                                MaxSliderRow(
                                    title = stringResource(R.string.charging_limit_slider_title),
                                    value = chargeLimit,
                                    onValueChange = { chargeLimit = it },
                                    valueText = "$LTR_MARK${chargeLimit.toInt()}%$LTR_MARK",
                                    valueRange = 50f..95f,
                                    steps = 8,
                                    onValueChangeFinished = {
                                        viewModel.applyChargeLimit(chargeLimit.toInt())
                                    }
                                )
                            }
                            MaxGroupDivider()
                        }
                        MaxSwitchRow(
                            title = stringResource(R.string.charging_battery_saver_title),
                            checked = viewModel.batterySaverEnabled,
                            onCheckedChange = { viewModel.setBatterySaver(it) },
                            subtitle = stringResource(R.string.charging_battery_saver_desc),
                            icon = Icons.Outlined.BatterySaver,
                            iconTone = MaxTone.Accent
                        )
                        MaxGroupDivider()
                        MaxNavigationRow(
                            title = stringResource(R.string.charging_bypass_title),
                            onClick = { navActions.navigateTo(MaxDestination.BypassCharging) },
                            subtitle = stringResource(R.string.charging_bypass_desc),
                            icon = Icons.Outlined.Usb
                        )
                    }
                    if (!viewModel.fastChargeAvailable && viewModel.sicModeNodePath == null) {
                        MaxBullets(
                            lines = listOf(stringResource(R.string.charging_fast_unavailable)),
                            tone = MaxTone.Caution
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargingFactRow(label: String, value: String) {
    MaxRow(
        title = label,
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
