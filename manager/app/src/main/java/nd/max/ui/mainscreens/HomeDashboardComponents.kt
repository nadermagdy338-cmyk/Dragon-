@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.*
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import java.util.Locale

fun formatNetSpeed(kbps: Long): String = when {
    kbps >= 1024 -> String.format(Locale.US, "%.1f MB/s", kbps / 1024f)
    else -> "$kbps KB/s"
}

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun formatFreq(mhz: Int): String = when {
    mhz <= 0 -> "—"
    mhz >= 1000 -> "${(mhz / 1000f).oneDecimal()} GHz"
    else -> "$mhz MHz"
}

private fun formatUptime(minutes: Long): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}


@Composable
fun DashSectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 40) {
    Surface(modifier = Modifier.size(size.dp), shape = RoundedCornerShape((size * .34f).dp), color = tint.copy(alpha = .12f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * .52f).dp)) }
    }
}

@Composable
fun LabelText(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = .14f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun DashCardWrapper(modifier: Modifier = Modifier, accent: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    MaxSurface(modifier = modifier, accent = accent, onClick = onClick, content = content)
}

@Composable
fun GlowLinearBar(fraction: Float, accent: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 7.dp) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "dashboardProgress")
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
        color = accent,
        trackColor = accent.copy(alpha = .1f)
    )
}

/**
 * Home's identity/status readout — a pure display surface. It states what the
 * device is and how it's doing (name, chipset, live stat quad) with no inline
 * controls; the only two icon actions here (settings, reboot) are utility
 * shortcuts tucked in the header, not headline calls to action. Anything that
 * actually changes device behavior (profile, tuning) lives in Quick access
 * below or in the Tweaks tab, never here.
 */
@Composable
fun CommandHero(
    deviceName: String,
    chipset: String?,
    online: Boolean,
    batteryPercent: Int,
    cpuFreqMhz: Int,
    uptimeMinutes: Long,
    temperatureC: Float?,
    onSettings: () -> Unit,
    onReboot: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    MaxSurfaceBox(
        modifier = Modifier.fillMaxWidth(),
        accent = colors.primary,
        containerColor = colors.surfaceContainerLow.copy(alpha = .92f),
        shape = RoundedCornerShape(30.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(colors.primary.copy(alpha = .12f), size.minDimension * .46f, Offset(size.width * .88f, size.height * .18f))
            drawCircle(colors.tertiary.copy(alpha = .08f), size.minDimension * .34f, Offset(size.width * .62f, size.height * .88f))
            val chip = Path().apply {
                moveTo(size.width * .72f, size.height * .22f)
                lineTo(size.width * .92f, size.height * .34f)
                lineTo(size.width * .84f, size.height * .72f)
                lineTo(size.width * .64f, size.height * .60f)
                close()
            }
            drawPath(chip, Brush.linearGradient(listOf(colors.primary.copy(.24f), colors.tertiary.copy(.08f))), style = Stroke(2.dp.toPx()))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val wide = maxWidth >= 600.dp
            val content: @Composable RowScope.() -> Unit = {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MaxStatusPill(
                            if (online) stringResource(R.string.max_home_live) else stringResource(R.string.max_home_offline),
                            online,
                            if (online) colors.tertiary else colors.error
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onReboot) { Icon(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power)) }
                        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.max_home_settings)) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.max_home_dashboard_eyebrow).uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary, letterSpacing = 2.sp)
                        Text(deviceName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(chipset ?: stringResource(R.string.max_home_unavailable), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = .07f)) {
                        StatTickRow(
                            stats = listOf(
                                stringResource(R.string.max_home_temp) to (temperatureC?.let { "${it.oneDecimal()}°" } ?: stringResource(R.string.max_home_unavailable)),
                                stringResource(R.string.max_home_battery) to "$batteryPercent%",
                                stringResource(R.string.max_home_frequency) to formatFreq(cpuFreqMhz),
                                stringResource(R.string.max_home_uptime) to formatUptime(uptimeMinutes)
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                        )
                    }
                }
                if (wide) { Spacer(Modifier.width(24.dp)); ChipSchematic(Modifier.width(180.dp).height(220.dp), colors.primary) }
            }
            if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
            else Row(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun ChipSchematic(modifier: Modifier, accent: Color) {
    Canvas(modifier) {
        val inset = 24.dp.toPx()
        drawRoundRect(accent.copy(.08f), Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(28f, 28f))
        drawRoundRect(accent.copy(.6f), Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(28f, 28f), style = Stroke(2.dp.toPx()))
        repeat(6) { i ->
            val y = inset + (i + 1) * (size.height - inset * 2) / 7
            drawLine(accent.copy(.32f), Offset(0f, y), Offset(inset, y), 2.dp.toPx())
            drawLine(accent.copy(.32f), Offset(size.width - inset, y), Offset(size.width, y), 2.dp.toPx())
        }
    }
}

@Composable
fun LivePerformance(dashboard: DashboardState, gpuName: String?, onGpu: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val cpuReady = dashboard.cpuLoadHistory.isNotEmpty()
    val ram = dashboard.ramTotalMb.takeIf { it > 0 }
        ?.let { (dashboard.ramUsedMb.toFloat() / it * 100).toInt().coerceIn(0, 100) }
    DashboardSection(
        stringResource(R.string.max_home_live_overview),
        stringResource(R.string.max_home_hardware_desc),
        colors.primary
    ) {
        MaxSurface(accent = colors.primary) {
            RadialGaugeCard(
                title = stringResource(R.string.max_home_cpu_activity),
                valueText = if (cpuReady) dashboard.cpuLoadPercent.coerceIn(0, 100).toString() else "--",
                unitText = "%",
                fraction = if (cpuReady) dashboard.cpuLoadPercent.coerceIn(0, 100) / 100f else 0f,
                subtitle = dashboard.cpuFreqMhz.takeIf { cpuReady && it > 0 }?.let { formatFreq(it) } ?: stringResource(R.string.max_home_unavailable),
                isLive = cpuReady,
                accentColor = colors.primary
            )
            Spacer(Modifier.height(12.dp))
            if (dashboard.cpuLoadHistory.size >= 2) PerformanceGraph(dashboard.cpuLoadHistory.takeLast(36), colors.primary, Modifier.fillMaxWidth().height(120.dp))
            else Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.max_home_waiting_samples), color = colors.onSurfaceVariant) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 700.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            val cardWidth = if (stacked) maxWidth else (maxWidth - 12.dp) / 2
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TelemetryGauge(stringResource(R.string.max_home_memory), ram, if (ram != null) "${(dashboard.ramUsedMb / 1024f).oneDecimal()} / ${(dashboard.ramTotalMb / 1024f).oneDecimal()} GB" else null, Icons.Rounded.Storage, colors.secondary, Modifier.width(cardWidth))
                TelemetryMetric(stringResource(R.string.max_home_battery_temperature), primaryBatteryTemperatureC(dashboard)?.let { "${it.oneDecimal()}°C" }, stringResource(R.string.max_home_battery_sensor), Icons.Rounded.Thermostat, colors.tertiary, Modifier.width(cardWidth))
                TelemetryMetric(stringResource(R.string.max_home_gpu_identity), gpuName, if (onGpu == null) stringResource(R.string.max_home_gpu_unknown_desc) else stringResource(R.string.max_home_graphics), Icons.Rounded.DeveloperBoard, colors.primary, Modifier.width(cardWidth), onGpu)
            }
        }
    }
}

@Composable
private fun TelemetryGauge(label: String, percent: Int?, supporting: String?, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    MaxSurface(modifier = modifier, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent); Spacer(Modifier.width(10.dp)); Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        Text(percent?.let { "$it%" } ?: stringResource(R.string.max_home_unavailable), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val progress = percent?.coerceIn(0, 100)?.div(100f)
        LinearProgressIndicator(
            progress = { progress ?: 0f },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).then(if (progress != null) Modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) } else Modifier),
            color = accent,
            trackColor = accent.copy(alpha = .1f)
        )
    }
}

@Composable
private fun TelemetryMetric(label: String, value: String?, supporting: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    MaxSurface(modifier = modifier, accent = accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent); Spacer(Modifier.width(10.dp)); Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); if (onClick != null) { Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent) } }
        Spacer(Modifier.height(14.dp))
        Text(value ?: stringResource(R.string.max_home_unavailable), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun DashboardSection(title: String, subtitle: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MaxSectionHeader(title, subtitle, accent = accent)
        content()
    }
}

@Composable
private fun PerformanceGraph(values: List<Float>, accent: Color, modifier: Modifier) {
    val secondary = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
    Canvas(modifier) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val step = size.width / values.lastIndex
        val line = Path(); val area = Path()
        values.forEachIndexed { i, raw ->
            val x = i * step; val y = size.height - raw.coerceIn(0f, 100f) / 100f * size.height
            if (i == 0) { line.moveTo(x, y); area.moveTo(x, size.height); area.lineTo(x, y) } else { line.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(size.width, size.height); area.close()
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(.28f), accent.copy(0f))))
        drawPath(line, Brush.horizontalGradient(listOf(secondary, accent)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
/**
 * Quick access — pure shortcuts, not controls in themselves. Every tile here
 * either opens a detail/read-more screen or hands off to a dedicated screen
 * (Tweaks) where the actual change happens; nothing on Home executes a
 * device change directly except Reboot, which stays in the header, not here.
 */
@Composable
fun ControlDeck(
    dashboard: DashboardState,
    profile: String,
    profilePending: Boolean,
    gpuName: String?,
    onRoute: (String) -> Unit,
    onGpu: () -> Unit,
    onProfile: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DashboardSection(
        stringResource(R.string.max_home_quick_access),
        stringResource(R.string.max_home_quick_access_desc),
        colors.tertiary
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 700.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            val itemWidth = if (stacked) maxWidth else (maxWidth - 12.dp) / 2
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlAction(stringResource(R.string.max_home_change_profile), if (profilePending) stringResource(R.string.max_home_profile_managed) else stringResource(R.string.max_home_profile_ready), profile, Icons.Rounded.Speed, colors.primary, Modifier.width(itemWidth), onProfile)
                ControlAction(stringResource(R.string.max_home_battery), stringResource(R.string.max_home_battery_desc), dashboard.batteryPercent.takeIf { dashboard.batteryStatus.isNotBlank() }?.let { "$it%" }, Icons.Rounded.BatteryChargingFull, colors.tertiary, Modifier.width(itemWidth)) { onRoute("battery_detail") }
                ControlAction(stringResource(R.string.max_home_storage), stringResource(R.string.max_home_storage_desc), dashboard.storageTotalGb.takeIf { it > 0f }?.let { "${dashboard.storageUsedGb.oneDecimal()} / ${it.oneDecimal()} GB" }, Icons.Rounded.Storage, colors.secondary, Modifier.width(itemWidth)) { onRoute("storage_detail") }
                ControlAction(stringResource(R.string.max_home_thermal), stringResource(R.string.max_home_thermal_desc), primaryBatteryTemperatureC(dashboard)?.let { "${it.oneDecimal()}°C" }, Icons.Rounded.Thermostat, colors.error, Modifier.width(itemWidth)) { onRoute("thermal_detail") }
                ControlAction(stringResource(R.string.max_home_network), stringResource(R.string.max_home_network_desc), "↓ ${formatNetSpeed(dashboard.downloadSpeedKbps)}  ↑ ${formatNetSpeed(dashboard.uploadSpeedKbps)}", Icons.Rounded.NetworkCheck, colors.primary, Modifier.width(itemWidth)) { onRoute("network_detail") }
                ControlAction(stringResource(R.string.studio_display), stringResource(R.string.max_home_resources_desc), dashboard.displayWidth.takeIf { it > 0 }?.let { "${dashboard.displayWidth}×${dashboard.displayHeight} · ${dashboard.displayRefreshHz} Hz" }, Icons.Rounded.DisplaySettings, colors.secondary, Modifier.width(itemWidth)) { onRoute("displaystudio") }
                if (gpuName != null) ControlAction(stringResource(R.string.max_home_gpu_control), stringResource(R.string.max_home_gpu_control_desc), gpuName, Icons.Rounded.DeveloperBoard, colors.tertiary, Modifier.width(itemWidth), onGpu)
            }
        }
    }
}

@Composable
private fun ControlAction(title: String, subtitle: String, value: String?, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    MaxActionRow(title = title, subtitle = subtitle, icon = icon, accent = accent, value = value, modifier = modifier, onClick = onClick)
}

@Composable
fun MaxAiConsole(state: MaxAiState, request: ProfileRequestState, failed: Boolean, onOpen: () -> Unit, onRetry: () -> Unit) {
    val purple = Color(0xFF9B7BFF)
    DashboardSection(stringResource(R.string.max_home_ai_console), stringResource(R.string.max_home_smart_section_desc), purple) {
        MaxSurface(accent = purple, onClick = onOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, null, tint = purple, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("MAX AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(state.strategyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                MaxStatusPill(if (state.aiEnabled) stringResource(R.string.max_home_ai_on) else stringResource(R.string.max_home_ai_off), state.aiEnabled, purple)
            }
            Spacer(Modifier.height(16.dp))
            when {
                request.inFlight -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.max_home_ai_working)) }
                failed -> {
                    Text(stringResource(R.string.max_home_ai_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    state.lastDecision?.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.max_home_retry)) }
                }
                state.lastDecision != null -> { Text(state.lastDecision.label, fontWeight = FontWeight.Bold); Text(state.lastDecision.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> Text(stringResource(R.string.max_home_ai_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
