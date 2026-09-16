package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.MaxActionRow
import nd.max.ui.component.MaxSectionHeader
import nd.max.ui.component.MaxSparkline
import nd.max.ui.component.MaxSurface
import nd.max.ui.component.MaxSurfaceBox
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MaxTextRole
import nd.max.ui.theme.MonoValueStyleLarge
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import java.util.Locale
import androidx.compose.ui.res.stringResource
import nd.max.R

/**
 * Analytics-first home: Material 3 remains the foundation, while MaxManager
 * owns the hierarchy. No hero/card stack; live data is the visual language.
 */
@Composable
internal fun MaxAnalyticsHome(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    gpuRoute: String?,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val online = ui.rootStatus && ui.moduleInstalled
    val thermal = primaryBatteryTemperatureC(dashboard)?.let { "${it.oneDecimal()}°" } ?: "—"
    val cpu = dashboard.cpuLoadPercent.coerceIn(0, 100)
    val ram = if (dashboard.ramTotalMb > 0) {
        (dashboard.ramUsedMb.toFloat() / dashboard.ramTotalMb * 100f).toInt().coerceIn(0, 100)
    } else 0
    val gpu = dashboard.gpuLoadPercent
    val profile = stringResource(ui.currentProfileRes)

    Column(
        modifier = modifier.fillMaxWidth().maxAdaptiveContentWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("MAXMANAGER", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.8.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(7.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (online) colors.primary else colors.error
                    ) {}
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (online) "System online" else "Engine offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            CompactIconButton(Icons.Rounded.PowerSettingsNew, "Power", onReboot)
            Spacer(Modifier.width(8.dp))
            CompactIconButton(Icons.Rounded.Settings, "Settings", onSettings)
        }

        Column {
            Text(deviceName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                dashboard.chipsetName.takeUnless { it.isBlank() || it == "..." } ?: "Hardware profile unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        MaxSurface(accent = colors.primary, onClick = if (ui.autoMode == "0") onProfile else null) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("SYSTEM STATE", style = MaxTextRole.status, color = colors.primary)
                    Spacer(Modifier.height(7.dp))
                    Text(profile, style = MonoValueStyleLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Active profile", style = MaxTextRole.metadata, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(thermal, style = MonoValueStyleMedium, color = if ((primaryBatteryTemperatureC(dashboard) ?: 0f) >= 50f) colors.error else colors.primary)
                    Text("thermal", style = MaxTextRole.metadata, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("CPU", "$cpu%", colors.primary, Modifier.weight(1f))
                MiniMetric("RAM", "$ram%", colors.secondary, Modifier.weight(1f))
                MiniMetric("GPU", gpu?.let { "$it%" } ?: "—", colors.tertiary, Modifier.weight(1f))
            }
        }

        Column {
            MaxSectionHeader("Performance", "Live telemetry", accent = colors.primary)
            Spacer(Modifier.height(10.dp))
            MaxSurface {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("CPU LOAD", style = MaxTextRole.status, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("$cpu%", style = MonoValueStyleLarge, color = colors.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatFrequency(dashboard.cpuFreqMhz), style = MonoValueStyleMedium, color = colors.primary)
                        Text("current", style = MaxTextRole.metadata, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                MaxSparkline(dashboard.cpuLoadHistory, colors.primary)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AnalyticsMetric("Memory", memoryText(dashboard), Icons.Rounded.Memory, colors.secondary, Modifier.weight(1f)) {
                    onNavigate(MaxDestination.ZramManager.route)
                }
                AnalyticsMetric("Thermal", thermal, Icons.Rounded.Thermostat, colors.tertiary, Modifier.weight(1f)) {
                    onNavigate(MaxDestination.ThermalDetail.route)
                }
            }
        }

        Column {
            MaxSectionHeader("Control", "Adjust the system, not the UI", accent = colors.primary)
            Spacer(Modifier.height(10.dp))
            MaxActionRow(
                title = "Control center",
                subtitle = "CPU · GPU · memory · thermal · display",
                icon = Icons.Rounded.Tune,
                value = profile,
                onClick = { onNavigate(MaxDestination.Control.route) }
            )
            Spacer(Modifier.height(10.dp))
            MaxActionRow(
                title = "Apps",
                subtitle = "Per-app profiles and hardware ownership",
                icon = Icons.Rounded.Speed,
                onClick = { onNavigate(MaxDestination.Apps.route) }
            )
        }

        MaxSurfaceBox(
            modifier = Modifier.fillMaxWidth(),
            accent = colors.primary,
            onClick = { onNavigate(MaxDestination.MaxAi.route) }
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MAX AI", style = MaxTextRole.status, color = colors.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(maxAi.strategyLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    maxAi.lastDecision?.reason ?: dashboard.intelligence.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (dashboard.intelligence.samples.size >= 2) {
                    Spacer(Modifier.height(10.dp))
                    MaxSparkline(dashboard.intelligence.samples.map { it.cpuPercent.toFloat() }, colors.primary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsMetric("Battery", "${dashboard.batteryPercent}%", Icons.Rounded.BatteryChargingFull, colors.primary, Modifier.weight(1f)) {
                onNavigate(MaxDestination.Charging.route)
            }
            AnalyticsMetric("Display", if (dashboard.displayRefreshHz > 0) "${dashboard.displayRefreshHz} Hz" else "—", Icons.Rounded.Wifi, colors.secondary, Modifier.weight(1f)) {
                onNavigate(MaxDestination.DisplayStudio.route)
            }
        }

        if (gpuRoute != null) {
            Text(
                "GPU telemetry available · ${dashboard.gpuFreqMhz?.let(::formatFrequency) ?: "clock unavailable"}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun CompactIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Icon(icon, description, modifier = Modifier.padding(10.dp).size(20.dp))
    }
}

@Composable
private fun MiniMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaxTextRole.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MonoValueStyleMedium, color = accent)
    }
}

@Composable
private fun AnalyticsMetric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    MaxSurface(modifier = modifier, accent = accent, onClick = onClick) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaxTextRole.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MonoValueStyleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatFrequency(mhz: Int): String = when {
    mhz <= 0 -> "—"
    mhz >= 1000 -> String.format(Locale.US, "%.2f GHz", mhz / 1000f)
    else -> "$mhz MHz"
}

private fun memoryText(dashboard: DashboardState): String = if (dashboard.ramTotalMb > 0) {
    String.format(Locale.US, "%.1f / %.1f GB", dashboard.ramUsedMb / 1024f, dashboard.ramTotalMb / 1024f)
} else "—"

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
