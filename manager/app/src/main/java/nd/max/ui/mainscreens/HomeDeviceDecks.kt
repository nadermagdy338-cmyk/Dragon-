/**
 * Home dashboard lower sections: thermal truth, device facts, history and navigation.
 *
 * These sections deliberately avoid recreating controls that already exist in the hero,
 * profile strip or resource tiles. Home is a summary surface; deep controls remain one tap away.
 */
package nd.max.ui.mainscreens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.component.NeuralAreaPlot
import nd.max.ui.component.NeuralCategoryRow
import nd.max.ui.component.NeuralDataRow
import nd.max.ui.component.NeuralDivider
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralReadoutTile
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralSensorOption
import nd.max.ui.component.NeuralSensorPicker
import nd.max.ui.component.NeuralStatCard
import nd.max.ui.component.neuralClickable
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.ThermalGridModel
import nd.max.ui.util.ThermalGridPreferences
import nd.max.ui.viewmodel.DashboardState

@Composable
internal fun ThermalPanel(dashboard: DashboardState, measured: Boolean, onThermal: () -> Unit) {
    val context = LocalContext.current
    val p = neuralPalette()
    val headline = if (measured) {
        dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt() ?: dashboard.cpuTempC.takeIf { it > 0 }
    } else null
    val headlineAccent = temperatureAccent(headline)
    val calm = measured && (headline == null || headline < 43)
    var pinned by remember { mutableStateOf(ThermalGridPreferences.read(context)) }
    var picking by remember { mutableStateOf(false) }
    val available = remember(dashboard.thermalByCategory) {
        ThermalGridModel.options(dashboard.thermalByCategory.keys)
    }

    NeuralPanel(
        accent = headlineAccent,
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 9.dp,
        onClick = onThermal,
    ) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_system_vitals),
            caption = stringResource(R.string.max_home_thermal_desc),
            accent = headlineAccent,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeuralPill(
                        text = stringResource(
                            when {
                                !measured -> R.string.max_home_unavailable
                                calm -> R.string.home_system_stable
                                else -> R.string.home_system_attention
                            }
                        ),
                        accent = if (!measured) p.muted else if (calm) p.ok else headlineAccent,
                        dot = true,
                    )
                    ThermalSensorButton(onClick = { picking = true })
                }
            },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            pinned.take(4).forEach { category ->
                ThermalTile(
                    caption = categoryLabel(category),
                    value = dashboard.sensorReading(category),
                    modifier = Modifier.weight(1f),
                )
            }
            repeat((4 - pinned.take(4).size).coerceAtLeast(0)) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }

    if (picking) {
        NeuralSensorPicker(
            title = stringResource(R.string.home_sensors_title),
            caption = stringResource(R.string.home_sensors_desc),
            limit = ThermalGridModel.LIMIT,
            options = available.map { key ->
                val value = dashboard.sensorReading(key)
                NeuralSensorOption(
                    key = key,
                    label = categoryLabel(key),
                    reading = thermalReadingText(value),
                    selected = key in pinned,
                    accent = temperatureAccent(value.takeIf { it > 0 }),
                )
            },
            onToggle = { key ->
                pinned = ThermalGridModel.toggle(pinned, key)
                ThermalGridPreferences.write(context, pinned)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "CPU" -> stringResource(R.string.home_cpu_tag)
    "GPU" -> stringResource(R.string.home_gpu_tag)
    "Skin" -> stringResource(R.string.home_skin_tag)
    "Battery" -> stringResource(R.string.max_home_battery)
    "Charger" -> stringResource(R.string.home_cat_charger)
    "Modem" -> stringResource(R.string.home_cat_modem)
    "WiFi" -> stringResource(R.string.home_cat_wifi)
    "Camera" -> stringResource(R.string.home_cat_camera)
    "Flash" -> stringResource(R.string.home_cat_flash)
    "PA" -> stringResource(R.string.home_cat_pa)
    "System" -> stringResource(R.string.home_cat_system)
    else -> category
}

private fun DashboardState.sensorReading(category: String): Int = when (category) {
    "CPU" -> cpuTempC
    "GPU" -> gpuTempC
    "Skin" -> skinTempC
    "Battery" -> batteryTempC.roundToInt()
    else -> thermalByCategory[category] ?: 0
}

private fun thermalReadingText(value: Int): String = if (value > 0) "$value°" else "—"

@Composable
private fun ThermalSensorButton(onClick: () -> Unit) {
    val p = neuralPalette()
    Row(
        Modifier
            .size(28.dp)
            .neuralClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.home_sensors_choose),
            modifier = Modifier.size(15.dp),
            tint = p.muted,
        )
    }
}

@Composable
private fun ThermalTile(caption: String, value: Int, modifier: Modifier) {
    val p = neuralPalette()
    val known = value > 0
    NeuralReadoutTile(
        caption = caption,
        value = if (known) "$value°" else "—",
        accent = if (known) temperatureAccent(value) else p.muted,
        modifier = modifier,
        sub = if (known) null else stringResource(R.string.home_sensor_unavailable),
    )
}

@Composable
internal fun DeviceDetailsPanel(
    dashboard: DashboardState,
    measured: Boolean,
    onDisplay: () -> Unit,
    onNetwork: () -> Unit,
    onPower: () -> Unit,
    onDeviceInfo: () -> Unit,
) {
    val p = neuralPalette()
    val dash = "—"
    val resolution = if (dashboard.displayWidth > 0 && dashboard.displayHeight > 0) {
        "${dashboard.displayWidth}×${dashboard.displayHeight}"
    } else dash

    NeuralPanel(accent = p.accent, contentPadding = PaddingValues(14.dp), verticalSpacing = 9.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_device_details),
            caption = stringResource(R.string.home_device_details_desc),
            accent = p.accent,
            trailing = {
                NeuralPill(
                    text = stringResource(R.string.home_details),
                    accent = p.muted,
                    icon = Icons.Rounded.Info,
                    onClick = onDeviceInfo,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.home_android_version),
                value = "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDeviceInfo,
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_resolution),
                value = resolution,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDisplay,
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_current_refresh),
                value = dashboard.displayRefreshHz.takeIf { it > 0 }?.let { "$it Hz" } ?: dash,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDisplay,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.home_download),
                value = if (measured) netSpeed(dashboard.downloadSpeedKbps) else dash,
                accent = p.ok,
                modifier = Modifier.weight(1f),
                onClick = onNetwork,
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_upload),
                value = if (measured) netSpeed(dashboard.uploadSpeedKbps) else dash,
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
                onClick = onNetwork,
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_density),
                value = dashboard.displayDensityDpi.takeIf { it > 0 }?.let { "$it dpi" } ?: dash,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDisplay,
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_battery_voltage),
                value = if (dashboard.batteryVoltageV > 0f) "${dashboard.batteryVoltageV.oneDecimal()} V" else dash,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onPower,
            )
        }
        NeuralDataRow(
            label = stringResource(R.string.device_model),
            value = Build.MODEL.ifBlank { dash },
            onClick = onDeviceInfo,
        )
    }
}

internal fun windowedAverage(series: List<Float?>): Float? {
    val measured = series.filterNotNull()
    return if (measured.size >= 3) measured.average().toFloat() else null
}

@Composable
internal fun PerformanceHistoryCard(dashboard: DashboardState) {
    val p = neuralPalette()
    val samples = dashboard.loadSamples
    val windowed = samples.size >= 3
    val cpuAvg = windowedAverage(samples.map { it.cpu })
    val gpuSeries = samples.map { it.gpu }
    val gpuAvg = windowedAverage(gpuSeries)
    val ramAvg = windowedAverage(dashboard.ramLoadHistory)

    NeuralStatCard(
        title = stringResource(R.string.home_history_title),
        caption = stringResource(R.string.home_history_desc),
        accent = p.accent,
        badge = if (windowed) null else stringResource(R.string.max_home_unavailable),
        chart = {
            NeuralAreaPlot(
                values = samples.map { it.cpu },
                accent = p.accent,
                secondary = gpuSeries,
                secondaryAccent = p.accentAlt,
                maxValue = 100f,
                adaptive = false,
                modifier = Modifier.fillMaxWidth().height(78.dp),
            )
        },
    ) {
        if (cpuAvg != null) {
            NeuralCategoryRow(
                label = stringResource(R.string.home_cpu_tag),
                value = "${cpuAvg.roundToInt()}%",
                fraction = cpuAvg / 100f,
                accent = p.accent,
            )
        }
        if (gpuAvg != null) {
            NeuralCategoryRow(
                label = stringResource(R.string.home_gpu_tag),
                value = "${gpuAvg.roundToInt()}%",
                fraction = gpuAvg / 100f,
                accent = p.accentAlt,
            )
        }
        if (ramAvg != null) {
            NeuralCategoryRow(
                label = stringResource(R.string.home_ram_tag),
                value = "${ramAvg.roundToInt()}%",
                fraction = ramAvg / 100f,
                accent = p.warn,
            )
        }
    }
}
