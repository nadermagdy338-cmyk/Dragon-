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

/**
 * شاشة الحرارة.
 *
 * كانت تُبنى بلغة لوحة البداية لا بلغة التطبيق، وفيها عطب حقيقي: قراءة البطارية كانت
 * تبحث عن منطقة تصنيفها **النصّ العربي** «البطارية»، بينما تصنيف النواة إنجليزي
 * (`battery`) — فكانت النتيجة صفرًا دائمًا في الواجهة العربية، تُعرض كصفر لا كـ«مجهول».
 * والآن تأتي من نفس مصدر الشاشة الرئيسية (`ThermalUtil.readBatteryTemperatureC`).
 *
 * وأُضيف ما تعرضه مراقبات الحرارة الجدّية (على نمط Android-Thermal-Monitor) وكان
 * `ThermalUtil` يقرؤه أصلًا ولا تعرضه أي شاشة:
 *   ١. **نقاط التخفيف**: عند أي درجة يبدأ النظام يخفّض، وعند أي درجة يُغلق. تُرسم على
 *      شريط الحرارة، فيرى المستخدم **كم بقي** لا درجة مجرّدة. وتُقرأ مرة واحدة لأنها
 *      لا تتغيّر، لا مع كل عيّنة.
 *   ٢. **أجهزة التبريد**: ما يخفّض فعلًا (معالج/رسوميات/مروحة/إضاءة) وحالته الآن من
 *      سعته. وهذا الجواب على «لماذا انخفض الأداء» لا درجة الحرارة وحدها.
 *
 * وكل ما يُقرأ هنا قراءة: لا مسار كتابة واحد في هذه الشاشة، فتصنيف الحرارة سلطة
 * النظام لا سلطة الواجهة (ADR-11).
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.MaxSurface
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxMetricReadout
import nd.max.ui.design.MaxMetricSize
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxUsageBar
import nd.max.ui.design.content
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.util.CoolingDeviceInfo
import nd.max.ui.util.ThermalLevel
import nd.max.ui.util.ThermalModel
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.ThermalZoneInfo
import nd.max.ui.util.ThrottleState
import nd.max.ui.util.TripReading
import kotlin.math.roundToInt

private const val LTR = "\u200E"
private const val ZONE_REFRESH_MS = 3_000L

/** سلّم شريط الحرارة: ١٠٠° هو سقف العرض، كما في الشاشة السابقة. */
private const val BAR_SCALE_C = 100f

@Composable
fun ThermalDetailScreen(navController: NavHostController) {
    val context = LocalContext.current

    var zones by remember { mutableStateOf<List<ThermalZoneInfo>>(emptyList()) }
    var cooling by remember { mutableStateOf<List<CoolingDeviceInfo>>(emptyList()) }
    var batteryHeat by remember { mutableStateOf(0f) }
    var trips by remember { mutableStateOf<Map<String, List<TripReading>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // نقاط التخفيف تُقرأ **مرة واحدة**: ثابتة في النواة، وقراءتها لكل منطقة مع كل عيّنة
    // (كل ٣ ثوان) كانت مئات قراءات sysfs لعرض رقم لا يتغيّر.
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            ThermalUtil.readThermalZones().associate { zone ->
                zone.sysfsPath to ThermalUtil.readTripPoints(zone.sysfsPath).map { trip ->
                    TripReading(temperatureC = trip.temperatureC, kind = trip.kind)
                }
            }
        }
        trips = loaded
        while (true) {
            val sample = withContext(Dispatchers.IO) {
                Triple(
                    ThermalUtil.readThermalZones(),
                    ThermalUtil.readCoolingDevices(),
                    ThermalUtil.readBatteryTemperatureC(context)
                )
            }
            zones = sample.first
            cooling = sample.second
            batteryHeat = sample.third
            isLoading = false
            delay(ZONE_REFRESH_MS)
        }
    }

    val enabledZones = zones.filter { it.isEnabled && it.temperatureC > 0 }
    val hottest = enabledZones.maxByOrNull { it.temperatureC }
    val cpuAverage = enabledZones
        .filter { it.category == "CPU" }
        .map { it.temperatureC }
        .average()
        .takeUnless { it.isNaN() }
        ?.roundToInt()
    val gpuPeak = enabledZones.filter { it.category == "GPU" }.maxOfOrNull { it.temperatureC }
    val hottestLevel = hottest?.let { ThermalModel.levelOf(it.temperatureC) }
    val hottestTrips = hottest?.let { trips[it.sysfsPath].orEmpty() }.orEmpty()
    val throttleState = hottest?.let { ThermalModel.throttleState(it.temperatureC, hottestTrips) }
    val accent = MaterialTheme.colorScheme.tertiary

    val condition = when {
        isLoading -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.detail_thermal),
            detail = stringResource(R.string.detail_thermal_reading)
        )

        enabledZones.isEmpty() -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = stringResource(R.string.detail_no_thermal_zones),
            detail = stringResource(R.string.detail_no_thermal_zones_desc)
        )

        else -> null
    }

    MaxListScreen(
        title = stringResource(R.string.detail_thermal),
        subtitle = stringResource(R.string.detail_thermal_label),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Thermostat,
        accent = accent,
        condition = condition,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.detail_thermal),
                body = stringResource(R.string.detail_thermal_subtitle)
            )
        },
        header = {
            if (hottest != null) {
                ThermalHeadline(
                    zone = hottest,
                    level = hottestLevel ?: ThermalLevel.Nominal,
                    state = throttleState,
                    trips = hottestTrips
                )
            }
        }
    ) {
        item(key = "thermal_summary") {
            MaxSection(
                title = stringResource(R.string.detail_thermal_summary),
                description = stringResource(R.string.detail_thermal_summary_desc)
            ) {
                MaxGroup {
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.detail_cpu_average),
                            value = cpuAverage?.toString() ?: MAX_VALUE_UNAVAILABLE,
                            unit = if (cpuAverage != null) "°C" else null,
                            trust = if (cpuAverage != null) MaxDataTrust.Live else MaxDataTrust.Unreadable
                        )
                    )
                    MaxGroupDivider()
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.detail_gpu_peak),
                            value = gpuPeak?.toString() ?: MAX_VALUE_UNAVAILABLE,
                            unit = if (gpuPeak != null) "°C" else null,
                            trust = if (gpuPeak != null) MaxDataTrust.Live else MaxDataTrust.Unreadable
                        )
                    )
                    MaxGroupDivider()
                    // حرارة البطارية من مصدر الشاشة الرئيسية نفسه، لا من تصنيف منطقة
                    // مُعرَّب: الاسم واحد والرقم واحد في الشاشتين.
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.detail_thermal_heat),
                            value = batteryHeat.takeIf { it > 0f }?.roundToInt()?.toString()
                                ?: MAX_VALUE_UNAVAILABLE,
                            unit = if (batteryHeat > 0f) "°C" else null,
                            trust = if (batteryHeat > 0f) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                            source = "BatteryManager",
                            note = stringResource(R.string.detail_thermal_heat_note)
                        )
                    )
                    hottest?.let {
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.detail_hottest_zone),
                                value = "${it.temperatureC}",
                                unit = "°C",
                                trust = MaxDataTrust.Live,
                                source = it.sysfsPath
                            )
                        )
                    }
                }
            }
        }

        item(key = "thermal_trips") {
            val readable = enabledZones.mapNotNull { zone ->
                val zoneTrips = trips[zone.sysfsPath].orEmpty()
                val start = ThermalModel.throttleStart(zoneTrips)
                val shutdown = ThermalModel.shutdownPoint(zoneTrips)
                if (start == null && shutdown == null) null else Triple(zone, start, shutdown)
            }
            MaxSection(
                title = stringResource(R.string.detail_thermal_trips),
                description = stringResource(R.string.detail_thermal_trips_desc)
            ) {
                MaxGroup {
                    if (readable.isEmpty()) {
                        MaxRow(
                            title = stringResource(R.string.detail_thermal_trips_unknown),
                            icon = Icons.Rounded.Info,
                            iconTone = MaxTone.Neutral
                        )
                    } else {
                        readable.forEachIndexed { index, (zone, start, shutdown) ->
                            if (index > 0) MaxGroupDivider()
                            MaxRow(
                                title = zone.label,
                                subtitle = when {
                                    start != null && shutdown != null ->
                                        stringResource(R.string.detail_thermal_trip_row, start, shutdown)
                                    start != null -> stringResource(R.string.detail_thermal_trip_start, start)
                                    else -> stringResource(R.string.detail_thermal_trip_shutdown, shutdown ?: 0)
                                },
                                trailing = {
                                    Text(
                                        text = "${zone.temperatureC}°C",
                                        style = MonoValueStyleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (cooling.isNotEmpty()) {
            item(key = "thermal_cooling") {
                MaxSection(
                    title = stringResource(R.string.detail_thermal_cooling),
                    description = stringResource(R.string.detail_thermal_cooling_desc)
                ) {
                    MaxGroup {
                        cooling.forEachIndexed { index, device ->
                            if (index > 0) MaxGroupDivider()
                            CoolingBlock(device)
                        }
                    }
                }
            }
        }

        val grouped = enabledZones.groupBy { it.category }
        grouped.toList().sortedBy { it.first }.forEach { (category, zoneList) ->
            item(key = "thermal_zone_group_$category") {
                MaxSection(
                    title = zoneCategoryLabel(category),
                    description = stringResource(R.string.detail_thermal_zones_desc, zoneList.size)
                ) {
                    MaxGroup {
                        zoneList.forEachIndexed { index, zone ->
                            if (index > 0) MaxGroupDivider()
                            ZoneBlock(zone = zone, trips = trips[zone.sysfsPath].orEmpty())
                        }
                    }
                }
            }
        }

        if (cooling.isEmpty() && enabledZones.isNotEmpty()) {
            item(key = "thermal_cooling_absent") {
                MaxRow(
                    title = stringResource(R.string.detail_thermal_cooling_absent),
                    icon = Icons.Rounded.Info
                )
            }
        }
    }
}

/** القراءة الكبيرة: أعلى درجة، شريطها، ونقطة التخفيف مرسومة عليها. */
@Composable
private fun ThermalHeadline(
    zone: ThermalZoneInfo,
    level: ThermalLevel,
    state: ThrottleState?,
    trips: List<TripReading>
) {
    val tone = level.tone()
    val start = ThermalModel.throttleStart(trips)
    MaxSurface(accent = tone.content()) {
        MaxMetricReadout(
            metric = MaxMetric(
                label = stringResource(R.string.detail_current_peak),
                value = zone.temperatureC.toString(),
                unit = "°C",
                trust = MaxDataTrust.Live,
                source = zone.label
            ),
            size = MaxMetricSize.Large
        )
        Spacer(Modifier.height(MaxSpace.md))
        MaxUsageBar(
            fraction = zone.temperatureC / BAR_SCALE_C,
            tone = tone,
            marker = start?.let { it / BAR_SCALE_C }
        )
        Spacer(Modifier.height(MaxSpace.md))
        when (state) {
            ThrottleState.Reached -> Text(
                text = stringResource(R.string.detail_thermal_throttling),
                style = MaterialTheme.typography.bodySmall,
                color = MaxTone.Critical.content()
            )

            ThrottleState.Headroom -> {
                val remaining = ThermalModel.headroomC(zone.temperatureC, trips) ?: 0
                Text(
                    text = stringResource(R.string.detail_thermal_headroom, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // مجهول ≠ آمن: حين لا تُعلن النواة نقطة تخفيف، تُقال الجملة التي تقول ذلك
            // في قسم نقاط التخفيف، ولا يُختلق هنا طمأنة.
            ThrottleState.Unknown, null -> Unit
        }
    }
}

@Composable
private fun ZoneBlock(zone: ThermalZoneInfo, trips: List<TripReading>) {
    val tone = ThermalModel.levelOf(zone.temperatureC).tone()
    val start = ThermalModel.throttleStart(trips)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(zone.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "$LTR${zone.sysfsPath}$LTR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = "${zone.temperatureC}°C", style = MonoValueStyleSmall, color = tone.content())
        }
        MaxUsageBar(
            fraction = zone.temperatureC / BAR_SCALE_C,
            tone = tone,
            marker = start?.let { it / BAR_SCALE_C }
        )
        start?.let {
            Text(
                text = stringResource(R.string.detail_thermal_trip_start, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoolingBlock(device: CoolingDeviceInfo) {
    val fraction = if (device.maxState > 0) {
        (device.currentState.toFloat() / device.maxState.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val tone = if (device.currentState > 0) MaxTone.Caution else MaxTone.Neutral
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(device.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${coolingCategoryLabel(device.category)} · $LTR${device.sysfsPath}$LTR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (device.maxState > 0) {
                    stringResource(R.string.detail_thermal_cooling_state, device.currentState, device.maxState)
                } else {
                    MAX_VALUE_UNAVAILABLE
                },
                style = MonoValueStyleSmall,
                color = if (device.currentState > 0) {
                    MaxTone.Caution.content()
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (device.maxState > 0) {
            MaxUsageBar(fraction = fraction, tone = tone)
        }
        if (device.currentState <= 0) {
            Text(
                text = stringResource(R.string.detail_thermal_cooling_idle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ThermalLevel.tone(): MaxTone = when (this) {
    ThermalLevel.Nominal -> MaxTone.Accent
    ThermalLevel.Warm -> MaxTone.Caution
    ThermalLevel.Hot -> MaxTone.Critical
}

/** أسماء تصنيفات النواة بلغة المستخدم؛ وما لا اسم له يظهر بمعرّفه كما هو. */
@Composable
private fun zoneCategoryLabel(category: String): String = when (category) {
    "CPU" -> stringResource(R.string.detail_zone_cpu)
    "GPU" -> stringResource(R.string.detail_zone_gpu)
    "Battery" -> stringResource(R.string.detail_zone_battery)
    "Charger" -> stringResource(R.string.detail_zone_charger)
    "Skin" -> stringResource(R.string.detail_zone_skin)
    "Modem" -> stringResource(R.string.detail_zone_modem)
    "WiFi" -> stringResource(R.string.detail_zone_wifi)
    "Camera" -> stringResource(R.string.detail_zone_camera)
    "Flash" -> stringResource(R.string.detail_zone_flash)
    "PA" -> stringResource(R.string.detail_zone_pa)
    "System" -> stringResource(R.string.detail_zone_system)
    else -> category
}

@Composable
private fun coolingCategoryLabel(category: String): String = when (category) {
    "CPU" -> stringResource(R.string.detail_zone_cpu)
    "GPU" -> stringResource(R.string.detail_zone_gpu)
    "Fan" -> stringResource(R.string.detail_cooling_fan)
    "Display" -> stringResource(R.string.detail_cooling_display)
    "Thermal" -> stringResource(R.string.detail_cooling_thermal)
    else -> stringResource(R.string.detail_bucket_other)
}
