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
 * Battery and charging — one screen.
 *
 * It used to be two destinations: `Charging` (the gauge plus every control) and
 * `BatteryDetail` (a second reading of the same pack, with its own polling loop,
 * its own data class and its own English status literals, and no controls at
 * all). Both answered the same question, and neither answered it whole: one knew
 * what you could change, the other knew what the battery was. Opening one and
 * then the other to learn a single fact is the cost of that split.
 *
 * They are merged here in the order the question is actually asked: **what is it
 * doing** (live), **what is it** (identity), **what can I change** (controls) —
 * with the limit's own effect stated against the current level, so the control
 * and the reading are not two unrelated halves of one page.
 *
 * Nothing was dropped in the move. The readings `BatteryDetail` owned that this
 * screen lacked — the driver's health verdict, the nameplate capacity, the pack's
 * current full capacity and the raw status — now come from the same
 * `ChargingViewModel` that owns everything else, so there is one poll loop and
 * one source per fact instead of two that could disagree.
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import nd.max.ui.viewmodel.BatteryHealthVerdict
import nd.max.ui.viewmodel.BatteryStatus
import nd.max.ui.util.ThermalUtil
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
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadState() }

    /*
     * حرارة البطارية من نفس مصدر الشاشة الرئيسية: بثّ `ACTION_BATTERY_CHANGED` أوّلًا،
     * ثم عقدة البطارية، ثم منطقة حرارة البطارية، ثم `thermalservice`.
     *
     * كان هذا الرقم في الشاشة يُقرأ من `$batteryDir/temp` وحدها، فعلى جهاز لا تُقرأ فيه
     * تلك العقدة كان يُعرض `0.0 °C` بينما الشاشة الرئيسية تعرض قراءة صحيحة — رقمان لفكرة
     * واحدة يفترقان فيُفقد الثقة فيهما معًا. وقراءة الـViewModel تبقى احتياطًا.
     */
    var homeBatteryHeat by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            homeBatteryHeat = withContext(Dispatchers.IO) { ThermalUtil.readBatteryTemperatureC(context) }
            delay(3_000L)
        }
    }
    val batteryHeat = homeBatteryHeat.takeIf { it > 0f } ?: viewModel.temperatureC

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
                        stringResource(R.string.charging_temp) to measurement(batteryHeat, "\u00b0C", 1),
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

            // ── هوية البطارية: ما نقلته شاشة البطارية المنفصلة ──────────────
            item(key = "battery_identity") {
                MaxSection(title = stringResource(R.string.detail_battery_identity)) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.detail_status),
                            subtitle = stringResource(R.string.detail_battery_identity_subtitle),
                            trailing = {
                                Text(
                                    text = stringResource(viewModel.batteryStatus.labelRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.charging_health),
                            // الرأي نفسه، لا رقمنا المشتق: يُذكر بطول مسافة من النسبة
                            // أعلاه حتى لا يُنسب أحدهما للآخر.
                            value = stringResource(viewModel.healthVerdict.labelRes)
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.detail_design_capacity),
                            value = viewModel.designCapacityMah?.let { "$LTR_MARK$it mAh$LTR_MARK" }
                                ?: stringResource(R.string.home_sensor_unavailable)
                        )
                        MaxGroupDivider()
                        ChargingFactRow(
                            label = stringResource(R.string.charging_full_capacity),
                            value = viewModel.fullCapacityMah?.let { "$LTR_MARK$it mAh$LTR_MARK" }
                                ?: stringResource(R.string.home_sensor_unavailable)
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
                                // الربط الذي كانت الشاشتان تفقده: الحدّ يُقرأ مقابل المستوى
                                // الحالي، فيُعرف أين يقف الآن من الحدّ الذي ضبطه المستخدم.
                                subtitle = if (chargeLimitActive) {
                                    stringResource(
                                        R.string.charging_limit_active_progress,
                                        viewModel.chargeLimitPercent,
                                        viewModel.capacityPercent
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

/** Wording for the pack's state and the driver's verdict is owned here, not in the ViewModel. */
private val BatteryStatus.labelRes: Int
    get() = when (this) {
        BatteryStatus.Charging -> R.string.detail_power_flowing
        BatteryStatus.Discharging -> R.string.detail_on_battery
        BatteryStatus.Full -> R.string.charging_status_full
        BatteryStatus.NotCharging -> R.string.charging_status_not_charging
        BatteryStatus.Unknown -> R.string.home_sensor_unavailable
    }

private val BatteryHealthVerdict.labelRes: Int
    get() = when (this) {
        BatteryHealthVerdict.Good -> R.string.detail_good
        BatteryHealthVerdict.Overheat -> R.string.detail_overheat
        BatteryHealthVerdict.Dead -> R.string.charging_health_dead
        BatteryHealthVerdict.OverVoltage -> R.string.charging_health_over_voltage
        BatteryHealthVerdict.Cold -> R.string.charging_health_cold
        BatteryHealthVerdict.Unknown -> R.string.home_sensor_unavailable
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
