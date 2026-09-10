@file:OptIn(ExperimentalLayoutApi::class)

package nd.max.ui.mainscreens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.CpuCoreState
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import java.util.Locale
import kotlin.math.roundToInt

private val HomeSurface = Color(0xFF0A111D)
private val HomeSurfaceHigh = Color(0xFF0D1726)
private val HomeBorder = Color(0xFF1B2B42)
private val HomeBlue = Color(0xFF3B82F6)
private val HomeCyan = Color(0xFF38D6F5)
private val HomeViolet = Color(0xFF8B5CF6)
private val HomeGreen = Color(0xFF27D695)
private val HomeOrange = Color(0xFFFF9F43)
private val HomeRed = Color(0xFFFF5D73)
private val HomeText = Color(0xFFF5F9FF)
private val HomeMuted = Color(0xFF8FA3BF)
private val HomeFaint = Color(0xFF53657E)
private val DashboardShape = RoundedCornerShape(24.dp)
private val InnerShape = RoundedCornerShape(16.dp)

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
private fun compactFrequency(mhz: Int?): String = when {
    mhz == null || mhz <= 0 -> "—"
    mhz >= 1000 -> "${(mhz / 1000f).oneDecimal()} GHz"
    else -> "$mhz MHz"
}
private fun compactUptime(minutes: Long): String = when {
    minutes <= 0 -> "—"
    minutes >= 1440 -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
private fun memoryValue(mb: Int): String = when {
    mb <= 0 -> "—"
    mb >= 1024 -> "${(mb / 1024f).oneDecimal()} GB"
    else -> "$mb MB"
}

@Composable
internal fun LegendaryHomeDashboard(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeBrandHeader(ui.rootStatus && ui.moduleInstalled, onSettings, onReboot)
        DeviceCommandHero(deviceName, dashboard, stringResource(ui.currentProfileRes), ui.rootStatus && ui.moduleInstalled, onProfile)
        SystemVitalsCard(dashboard)
        CpuCoreMatrix(dashboard.cores) { onNavigate("cpucorecontrol") }
        PerformanceMonitorCard(dashboard)
        QuickActionsGrid(onProfile, { onNavigate("thermal_detail") }, { onNavigate("battery_detail") }, { onNavigate("tweaks") })
        MemoryStorageCard(dashboard) { onNavigate("storage_detail") }
        DeviceResourcesCard(dashboard, onNavigate)
        AiCommandCard(maxAi, profileRequest, { onNavigate("maxai") }, onAiRetry)
        ConnectivityStrip(dashboard, ui.rootStatus && ui.moduleInstalled)
    }
}

@Composable
private fun HomeBrandHeader(online: Boolean, onSettings: () -> Unit, onReboot: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("MAX", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = HomeText)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (online) HomeGreen else HomeRed)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(if (online) R.string.home_engine_ready else R.string.home_engine_offline), style = MaterialTheme.typography.labelMedium, color = HomeMuted)
            }
        }
        HeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), onReboot)
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), onSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = HomeSurfaceHigh,
        border = BorderStroke(1.dp, HomeBorder)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = HomeText, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
private fun DeviceCommandHero(deviceName: String, dashboard: DashboardState, profile: String, online: Boolean, onProfile: () -> Unit) {
    DashboardCard(HomeBlue) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(HomeBlue.copy(alpha = .13f), size.width * .55f, Offset(size.width * .9f, size.height * .05f))
            drawCircle(HomeViolet.copy(alpha = .08f), size.width * .38f, Offset(size.width * .1f, size.height * 1.05f))
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    LivePill(online)
                    Spacer(Modifier.height(12.dp))
                    Text(deviceName, color = HomeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(dashboard.chipsetName.takeUnless { it.isBlank() || it == "..." } ?: stringResource(R.string.max_home_unavailable), color = HomeMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = HomeBlue.copy(alpha = .10f), border = BorderStroke(1.dp, HomeBlue.copy(alpha = .24f)), onClick = onProfile) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null, tint = HomeCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp)); Text(profile, color = HomeText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = HomeCyan, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                NeonGauge(dashboard.cpuLoadPercent, dashboard.cpuLoadPercent.coerceIn(0, 100) / 100f, "CPU", Icons.Rounded.DeveloperBoard, HomeCyan, Modifier.size(118.dp), 9.dp)
            }
            HeroMetricStrip(listOf(
                HeroMetric(Icons.Rounded.Thermostat, primaryBatteryTemperatureC(dashboard)?.let { "${it.oneDecimal()}°" } ?: "—", stringResource(R.string.max_home_temp)),
                HeroMetric(Icons.Rounded.BatteryChargingFull, "${dashboard.batteryPercent}%", stringResource(R.string.max_home_battery)),
                HeroMetric(Icons.Rounded.Bolt, compactFrequency(dashboard.cpuFreqMhz), stringResource(R.string.max_home_frequency)),
                HeroMetric(Icons.Rounded.Timer, compactUptime(dashboard.uptimeMinutes), stringResource(R.string.max_home_uptime))
            ))
        }
    }
}

private data class HeroMetric(val icon: ImageVector, val value: String, val label: String)

@Composable
private fun HeroMetricStrip(metrics: List<HeroMetric>) {
    Surface(shape = InnerShape, color = Color.White.copy(alpha = .035f), border = BorderStroke(1.dp, Color.White.copy(alpha = .06f))) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            metrics.forEachIndexed { index, metric ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(metric.icon, null, tint = HomeCyan, modifier = Modifier.size(15.dp)); Spacer(Modifier.height(5.dp))
                    Text(metric.value, color = HomeText, style = MonoValueStyleSmall, maxLines = 1)
                    Text(metric.label, color = HomeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index < metrics.lastIndex) Box(Modifier.width(1.dp).height(38.dp).background(HomeBorder))
            }
        }
    }
}

@Composable
private fun SystemVitalsCard(dashboard: DashboardState) {
    val ram = if (dashboard.ramTotalMb > 0) (dashboard.ramUsedMb * 100f / dashboard.ramTotalMb).roundToInt().coerceIn(0, 100) else null
    val temp = primaryBatteryTemperatureC(dashboard)?.roundToInt()
    DashboardCard(HomeCyan, padded = true) {
        SectionTitle(stringResource(R.string.home_system_vitals), stringResource(R.string.home_system_vitals_desc), Icons.Rounded.Speed, HomeCyan)
        Spacer(Modifier.height(16.dp))
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VitalsTile("RAM", ram, memoryValue(dashboard.ramUsedMb), Icons.Rounded.Memory, HomeViolet, Modifier.weight(1f))
            VitalsTile("CPU", dashboard.cpuLoadPercent, compactFrequency(dashboard.cpuFreqMhz), Icons.Rounded.DeveloperBoard, HomeCyan, Modifier.weight(1f))
            VitalsTile("GPU", dashboard.gpuLoadPercent, dashboard.gpuFreqMhz?.let(::compactFrequency) ?: stringResource(R.string.home_sensor_unavailable), Icons.Rounded.DeveloperBoard, HomeViolet, Modifier.weight(1f))
            VitalsTile(stringResource(R.string.home_temperature_short), temp, temp?.let { "$it°C" } ?: "—", Icons.Rounded.Thermostat, temperatureColor(temp), Modifier.weight(1f), true)
        }
    }
}

@Composable
private fun VitalsTile(label: String, value: Int?, supporting: String, icon: ImageVector, accent: Color, modifier: Modifier, temperature: Boolean = false) {
    Surface(
        modifier = modifier.heightIn(min = 105.dp),
        shape = InnerShape,
        color = HomeSurfaceHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = .16f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonGauge(value, if (temperature) (value ?: 0) / 80f else (value ?: 0) / 100f, if (temperature) "°C" else "%", icon, accent, Modifier.size(72.dp), 6.dp)
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
                Text(label, color = HomeText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp)); Text(supporting, color = HomeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CpuCoreMatrix(cores: List<CpuCoreState>, onOpen: () -> Unit) {
    DashboardCard(HomeBlue, padded = true, onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                stringResource(R.string.home_cpu_cores),
                if (cores.isEmpty()) stringResource(R.string.home_waiting_core_data)
                else stringResource(R.string.home_cpu_cores_online, cores.count { it.online }, cores.size),
                Icons.Rounded.DeveloperBoard, HomeBlue, Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = HomeBlue)
        }
        Spacer(Modifier.height(16.dp))
        if (cores.isEmpty()) {
            Text(stringResource(R.string.max_home_waiting_samples), color = HomeMuted, modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cores.forEach { CpuCoreTile(it, Modifier.weight(1f)) }
                repeat((4 - cores.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CpuCoreTile(core: CpuCoreState, modifier: Modifier) {
    val accent = when (core.clusterTag) { "PRIME" -> HomeOrange; "GOLD" -> HomeViolet; else -> HomeCyan }
    Surface(
        modifier = modifier.widthIn(min = 64.dp).aspectRatio(.82f),
        shape = RoundedCornerShape(14.dp),
        color = if (core.online) accent.copy(alpha = .07f) else HomeSurfaceHigh.copy(alpha = .6f),
        border = BorderStroke(1.dp, if (core.online) accent.copy(alpha = .22f) else HomeBorder)
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (core.online) accent else HomeFaint, 5.dp); Spacer(Modifier.weight(1f))
                Text("C${core.cpu}", color = if (core.online) accent else HomeFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.DeveloperBoard, null, tint = if (core.online) accent else HomeFaint, modifier = Modifier.size(18.dp))
            AnimatedContent(core.freqMhz, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "coreFreq") { freq ->
                Text(if (core.online) freq.toString() else "OFF", color = if (core.online) HomeText else HomeFaint, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(if (core.online) "MHz" else core.clusterTag, color = HomeMuted, fontSize = 8.sp, maxLines = 1)
            ThinProgress(core.loadFraction, accent, Modifier.fillMaxWidth(), 3.dp)
        }
    }
}

@Composable
private fun PerformanceMonitorCard(dashboard: DashboardState) {
    DashboardCard(HomeCyan, padded = true) {
        SectionTitle(stringResource(R.string.home_performance_monitor), stringResource(R.string.home_performance_monitor_desc), Icons.Rounded.Speed, HomeCyan)
        Spacer(Modifier.height(14.dp))
        MultiTelemetryChart(dashboard.cpuLoadHistory, dashboard.gpuLoadHistory.takeIf { dashboard.gpuLoadPercent != null }.orEmpty(), dashboard.ramLoadHistory, Modifier.fillMaxWidth().height(128.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendValue("CPU", "${dashboard.cpuLoadPercent}%", HomeCyan, Modifier.weight(1f))
            LegendValue("GPU", dashboard.gpuLoadPercent?.let { "$it%" } ?: "—", HomeViolet, Modifier.weight(1f))
            val ram = if (dashboard.ramTotalMb > 0) dashboard.ramUsedMb * 100 / dashboard.ramTotalMb else 0
            LegendValue("RAM", "$ram%", HomeGreen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MultiTelemetryChart(cpu: List<Float>, gpu: List<Float>, ram: List<Float>, modifier: Modifier) {
    Canvas(modifier.clip(InnerShape).background(Color(0xFF07101B))) {
        val inset = 10.dp.toPx(); val w = size.width - inset * 2; val h = size.height - inset * 2
        repeat(4) { i -> val y = inset + h * i / 3f; drawLine(HomeBorder.copy(alpha = .65f), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx()) }
        repeat(6) { i -> val x = inset + w * i / 5f; drawLine(HomeBorder.copy(alpha = .28f), Offset(x, inset), Offset(x, size.height - inset), 1.dp.toPx()) }
        drawTelemetrySeries(ram, HomeGreen, false, inset, w, h)
        drawTelemetrySeries(gpu, HomeViolet, false, inset, w, h)
        drawTelemetrySeries(cpu, HomeCyan, true, inset, w, h)
    }
}

private fun DrawScope.drawTelemetrySeries(values: List<Float>, color: Color, area: Boolean, inset: Float, width: Float, height: Float) {
    val data = values.takeLast(36)
    if (data.size < 2) return
    val points = data.mapIndexed { index, value -> Offset(inset + width * index / data.lastIndex, size.height - inset - height * value.coerceIn(0f, 100f) / 100f) }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    if (area) {
        val fill = Path().apply {
            addPath(path); lineTo(points.last().x, size.height - inset); lineTo(points.first().x, size.height - inset); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .22f), Color.Transparent), startY = inset, endY = size.height - inset))
    }
    drawPath(path, color.copy(alpha = .15f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(color.copy(alpha = .22f), 7.dp.toPx(), points.last())
    drawCircle(HomeText, 2.dp.toPx(), points.last())
}

@Composable
private fun LegendValue(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = HomeSurfaceHigh) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, 6.dp); Spacer(Modifier.width(7.dp)); Text(label, color = HomeMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f)); Text(value, color = HomeText, style = MonoValueStyleSmall)
        }
    }
}

@Composable
private fun QuickActionsGrid(onBoost: () -> Unit, onThermal: () -> Unit, onBattery: () -> Unit, onAdvanced: () -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.home_quick_actions), stringResource(R.string.home_quick_actions_desc), Icons.Rounded.RocketLaunch, HomeViolet)
        Spacer(Modifier.height(12.dp))
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.RocketLaunch, stringResource(R.string.home_action_boost), stringResource(R.string.home_action_boost_desc), HomeBlue, onBoost, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Thermostat, stringResource(R.string.home_action_thermal), stringResource(R.string.home_action_thermal_desc), HomeCyan, onThermal, Modifier.weight(1f))
            QuickAction(Icons.Rounded.BatteryChargingFull, stringResource(R.string.home_action_battery), stringResource(R.string.home_action_battery_desc), HomeGreen, onBattery, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Tune, stringResource(R.string.home_action_advanced), stringResource(R.string.home_action_advanced_desc), HomeViolet, onAdvanced, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, subtitle: String, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier.heightIn(min = 118.dp), shape = RoundedCornerShape(20.dp), color = accent.copy(alpha = .065f), border = BorderStroke(1.dp, accent.copy(alpha = .20f)), onClick = onClick) {
        Box(Modifier.padding(14.dp)) {
            Column {
                Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .14f)) { Icon(icon, null, tint = accent, modifier = Modifier.padding(9.dp).size(20.dp)) }
                Spacer(Modifier.height(12.dp)); Text(title, color = HomeText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = HomeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
        }
    }
}

@Composable
private fun MemoryStorageCard(dashboard: DashboardState, onOpen: () -> Unit) {
    val ram = if (dashboard.ramTotalMb > 0) dashboard.ramUsedMb.toFloat() / dashboard.ramTotalMb else 0f
    val storage = if (dashboard.storageTotalGb > 0f) dashboard.storageUsedGb / dashboard.storageTotalGb else 0f
    DashboardCard(HomeViolet, padded = true, onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.home_memory_storage), stringResource(R.string.home_memory_storage_desc), Icons.Rounded.Memory, HomeViolet, Modifier.weight(1f))
            Text(stringResource(R.string.home_details), color = HomeCyan, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
        ResourceRow(Icons.Rounded.Memory, "RAM", "${memoryValue(dashboard.ramUsedMb)} / ${memoryValue(dashboard.ramTotalMb)}", ram, HomeViolet)
        Spacer(Modifier.height(10.dp))
        ResourceRow(Icons.Rounded.Storage, stringResource(R.string.max_home_storage), if (dashboard.storageTotalGb > 0f) "${dashboard.storageUsedGb.oneDecimal()} / ${dashboard.storageTotalGb.oneDecimal()} GB" else "—", storage, HomeBlue)
        if (dashboard.swapTotalMb != null && dashboard.swapUsedMb != null) {
            Spacer(Modifier.height(10.dp))
            ResourceRow(Icons.Rounded.Memory, "ZRAM", "${memoryValue(dashboard.swapUsedMb)} / ${memoryValue(dashboard.swapTotalMb)}", if (dashboard.swapTotalMb > 0) dashboard.swapUsedMb.toFloat() / dashboard.swapTotalMb else 0f, HomeCyan)
        }
    }
}

@Composable
private fun ResourceRow(icon: ImageVector, title: String, value: String, fraction: Float, accent: Color) {
    Surface(shape = InnerShape, color = HomeSurfaceHigh, border = BorderStroke(1.dp, accent.copy(alpha = .12f))) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp))
                Text(title, color = HomeText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f)); Text(value, color = HomeMuted, style = MonoValueStyleSmall)
            }
            Spacer(Modifier.height(10.dp)); ThinProgress(fraction, accent, Modifier.fillMaxWidth(), 5.dp)
        }
    }
}

@Composable
private fun DeviceResourcesCard(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    DashboardCard(HomeOrange, padded = true) {
        SectionTitle(stringResource(R.string.home_device_resources), stringResource(R.string.home_device_resources_desc), Icons.Rounded.DisplaySettings, HomeOrange)
        Spacer(Modifier.height(14.dp))
        ResourceLink(Icons.Rounded.DisplaySettings, stringResource(R.string.studio_display), if (dashboard.displayWidth > 0) "${dashboard.displayWidth}×${dashboard.displayHeight} · ${dashboard.displayRefreshHz} Hz · ${dashboard.displayDensityDpi} dpi" else "—", HomeCyan) { onNavigate("displaystudio") }
        Spacer(Modifier.height(9.dp))
        ResourceLink(Icons.Rounded.NetworkCheck, stringResource(R.string.max_home_network), "↓ ${formatNetSpeed(dashboard.downloadSpeedKbps)}  ↑ ${formatNetSpeed(dashboard.uploadSpeedKbps)}", HomeGreen) { onNavigate("network_detail") }
        Spacer(Modifier.height(9.dp))
        ResourceLink(Icons.Rounded.Bolt, stringResource(R.string.home_power_draw), if (dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W · ${dashboard.batteryVoltageV.oneDecimal()} V" else "—", HomeOrange) { onNavigate("battery_detail") }
    }
}

@Composable
private fun ResourceLink(icon: ImageVector, title: String, value: String, accent: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(15.dp), color = HomeSurfaceHigh, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = .1f)) { Icon(icon, null, tint = accent, modifier = Modifier.padding(8.dp).size(18.dp)) }
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
                Text(title, color = HomeText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(value, color = HomeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AiCommandCard(state: MaxAiState, request: ProfileRequestState, onOpen: () -> Unit, onRetry: () -> Unit) {
    DashboardCard(HomeViolet, onClick = onOpen) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(HomeViolet.copy(alpha = .12f), size.minDimension * .55f, Offset(size.width * .08f, size.height * .6f))
            drawCircle(HomeBlue.copy(alpha = .08f), size.minDimension * .35f, Offset(size.width * .18f, size.height * .25f))
        }
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = HomeViolet.copy(alpha = .13f), border = BorderStroke(1.dp, HomeViolet.copy(alpha = .3f))) { Icon(Icons.Rounded.Psychology, null, tint = HomeViolet, modifier = Modifier.padding(13.dp).size(26.dp)) }
            Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_ai_center), color = HomeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp)); StatusDot(if (state.aiEnabled) HomeGreen else HomeFaint, 6.dp)
                }
                Text(when { request.inFlight -> stringResource(R.string.max_home_ai_working); state.lastDecision != null -> state.lastDecision.label; else -> state.strategyLabel }, color = HomeMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (request.result == DecisionResult.FAILED) {
                Surface(shape = RoundedCornerShape(10.dp), color = HomeRed.copy(alpha = .1f), onClick = onRetry) { Text(stringResource(R.string.max_home_retry), color = HomeRed, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium) }
            } else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = HomeViolet)
        }
    }
}

@Composable
private fun ConnectivityStrip(dashboard: DashboardState, engineOnline: Boolean) {
    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(Icons.Rounded.Wifi, stringResource(R.string.home_network_status), if (dashboard.downloadSpeedKbps > 0 || dashboard.uploadSpeedKbps > 0) stringResource(R.string.home_active) else stringResource(R.string.home_idle), HomeCyan, Modifier.weight(1f))
        StatusChip(Icons.Rounded.Security, stringResource(R.string.home_engine_status), if (engineOnline) stringResource(R.string.home_protected) else stringResource(R.string.max_home_offline), if (engineOnline) HomeGreen else HomeRed, Modifier.weight(1f))
        StatusChip(Icons.Rounded.DeveloperBoard, stringResource(R.string.home_cores_status), dashboard.cores.takeIf { it.isNotEmpty() }?.let { "${it.count(CpuCoreState::online)}/${it.size}" } ?: "—", HomeViolet, Modifier.weight(1f))
    }
}

@Composable
private fun StatusChip(icon: ImageVector, title: String, value: String, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(16.dp),
        color = HomeSurface,
        border = BorderStroke(1.dp, HomeBorder)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(title, color = HomeMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(6.dp)); Text(value, color = HomeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = .1f)) { Icon(icon, null, tint = accent, modifier = Modifier.padding(8.dp).size(18.dp)) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
            Text(title, color = HomeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HomeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DashboardCard(accent: Color, modifier: Modifier = Modifier, padded: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    var card = modifier.fillMaxWidth().clip(DashboardShape).background(Brush.linearGradient(listOf(accent.copy(alpha = .055f), HomeSurface, HomeSurfaceHigh.copy(alpha = .9f))))
    if (onClick != null) card = card.clickable(onClick = onClick)
    Box(card) {
        Canvas(Modifier.matchParentSize()) { drawRoundRect(Brush.linearGradient(listOf(accent.copy(alpha = .36f), HomeBorder, Color.Transparent)), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(24.dp.toPx())) }
        Box(if (padded) Modifier.padding(18.dp) else Modifier, content = content)
    }
}

@Composable
private fun NeonGauge(value: Int?, fraction: Float, label: String, icon: ImageVector, accent: Color, modifier: Modifier, strokeWidth: Dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "neonGauge")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx(); val d = size.minDimension - stroke * 2; val o = Offset((size.width - d) / 2f, (size.height - d) / 2f); val s = Size(d, d); val start = 140f; val total = 260f
            drawArc(HomeBorder, start, total, false, o, s, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(accent.copy(alpha = .12f), start, total * animated, false, o, s, style = Stroke(stroke + 10.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Brush.linearGradient(listOf(HomeBlue, accent, Color.White), o, Offset(o.x + d, o.y + d)), start, total * animated, false, o, s, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(if (strokeWidth >= 8.dp) 18.dp else 13.dp))
            Text(value?.toString() ?: "—", color = HomeText, fontSize = if (strokeWidth >= 8.dp) 22.sp else 15.sp, fontWeight = FontWeight.Black)
            Text(label, color = HomeMuted, fontSize = if (strokeWidth >= 8.dp) 9.sp else 8.sp)
        }
    }
}

@Composable
private fun ThinProgress(fraction: Float, accent: Color, modifier: Modifier, height: Dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "dashboardBar")
    Box(modifier.height(height).clip(CircleShape).background(HomeBorder).semantics { progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f) }) {
        Box(Modifier.fillMaxWidth(animated).height(height).background(Brush.horizontalGradient(listOf(accent.copy(alpha = .7f), accent))))
    }
}

@Composable
private fun LivePill(online: Boolean) {
    val color = if (online) HomeGreen else HomeRed
    Surface(shape = CircleShape, color = color.copy(alpha = .08f), border = BorderStroke(1.dp, color.copy(alpha = .3f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, 6.dp); Spacer(Modifier.width(6.dp)); Text(stringResource(if (online) R.string.home_system_stable else R.string.home_system_attention), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusDot(color: Color, size: Dp = 7.dp) { Box(Modifier.size(size).clip(CircleShape).background(color)) }

private fun temperatureColor(value: Int?): Color = when { value == null -> HomeFaint; value >= 48 -> HomeRed; value >= 42 -> HomeOrange; else -> HomeGreen }
