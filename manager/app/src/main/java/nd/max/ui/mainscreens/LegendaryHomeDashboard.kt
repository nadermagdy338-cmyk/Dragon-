@file:OptIn(ExperimentalLayoutApi::class)

package nd.max.ui.mainscreens
import nd.max.ui.navigation.MaxDestination

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Compress
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
import androidx.compose.ui.graphics.Color
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
import nd.max.ui.component.PowerCoreCard
import nd.max.ui.component.PowerCoreInfo
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.CpuCoreState
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import java.util.Locale
import kotlin.math.roundToInt

private val DashboardShape = RoundedCornerShape(24.dp)
private val InnerShape = RoundedCornerShape(16.dp)

private data class HomePalette(
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val secondary: Color,
    val positive: Color,
    val warning: Color,
    val danger: Color
)

@Composable
private fun homePalette(): HomePalette {
    val colors = MaterialTheme.colorScheme
    return HomePalette(
        surface = colors.surfaceContainerLow,
        surfaceRaised = colors.surfaceContainerHigh,
        text = colors.onSurface,
        muted = colors.onSurfaceVariant,
        border = colors.outlineVariant,
        primary = colors.primary,
        secondary = colors.tertiary,
        positive = colors.secondary,
        warning = colors.tertiary,
        danger = colors.error
    )
}

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
    gpuRoute: String?,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit
) {
    val palette = homePalette()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeBrandHeader(ui.rootStatus && ui.moduleInstalled, palette, onSettings, onReboot)
        DeviceCommandHero(
            deviceName = deviceName,
            dashboard = dashboard,
            profile = stringResource(ui.currentProfileRes),
            engineOnline = ui.rootStatus && ui.moduleInstalled,
            palette = palette,
            onProfile = onProfile,
            onDetails = { onNavigate("diagnostics") }
        )
        LivePerformanceCard(
            dashboard = dashboard,
            palette = palette,
            onCpu = { onNavigate("cpucorecontrol") },
            onGpu = gpuRoute?.let { route -> { onNavigate(route) } },
            onMemory = { onNavigate("zrammanager") },
            onThermal = { onNavigate("thermal_detail") }
        )
        CpuCoreMatrix(dashboard.cores, palette) { onNavigate("cpucorecontrol") }
        MemoryStorageCard(
            dashboard = dashboard,
            palette = palette,
            onMemory = { onNavigate("zrammanager") },
            onStorage = { onNavigate("storage_detail") }
        )
        QuickActionsGrid(
            palette = palette,
            onBoost = onProfile,
            onThermal = { onNavigate("thermal_detail") },
            onBattery = { onNavigate("chargingscreen") },
            onAdvanced = { onNavigate("tweaks") }
        )
        DeviceResourcesCard(dashboard, palette, onNavigate)
        AiCommandCard(maxAi, profileRequest, palette, { onNavigate(MaxDestination.MaxAi.route) }, onAiRetry)
        ConnectivityStrip(dashboard, ui.rootStatus && ui.moduleInstalled, palette)
    }
}

@Composable
private fun HomeBrandHeader(online: Boolean, palette: HomePalette, onSettings: () -> Unit, onReboot: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("MAX", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = palette.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (online) palette.positive else palette.danger)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(if (online) R.string.home_engine_ready else R.string.home_engine_offline), style = MaterialTheme.typography.labelMedium, color = palette.muted)
            }
        }
        HeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), palette, onReboot)
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), palette, onSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, palette: HomePalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceRaised,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = palette.text, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
private fun DeviceCommandHero(
    deviceName: String,
    dashboard: DashboardState,
    profile: String,
    engineOnline: Boolean,
    palette: HomePalette,
    onProfile: () -> Unit,
    onDetails: () -> Unit
) {
    DashboardCard(palette.primary, palette, onClick = onDetails) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    LivePill(engineOnline, palette)
                    Spacer(Modifier.height(12.dp))
                    Text(deviceName, color = palette.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        dashboard.chipsetName.takeUnless { it.isBlank() || it == "..." } ?: stringResource(R.string.max_home_unavailable),
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = CircleShape, color = palette.primary.copy(alpha = .12f), border = BorderStroke(1.dp, palette.primary.copy(alpha = .2f))) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = palette.primary, modifier = Modifier.padding(14.dp).size(28.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.primary.copy(alpha = .09f),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = .16f)),
                onClick = onProfile
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = palette.primary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.max_home_active_profile), color = palette.muted, style = MaterialTheme.typography.labelSmall)
                        Text(profile, color = palette.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                }
            }
            HeroMetricStrip(
                listOf(
                    HeroMetric(Icons.Rounded.BatteryChargingFull, "${dashboard.batteryPercent}%", stringResource(R.string.max_home_battery)),
                    HeroMetric(Icons.Rounded.Thermostat, primaryBatteryTemperatureC(dashboard)?.let { "${it.oneDecimal()}°C" } ?: "—", stringResource(R.string.max_home_temp)),
                    HeroMetric(Icons.Rounded.Timer, compactUptime(dashboard.uptimeMinutes), stringResource(R.string.max_home_uptime))
                ),
                palette
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_device_overview), color = palette.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = palette.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class HeroMetric(val icon: ImageVector, val value: String, val label: String)

@Composable
private fun HeroMetricStrip(metrics: List<HeroMetric>, palette: HomePalette) {
    Surface(shape = InnerShape, color = palette.surfaceRaised.copy(alpha = .72f), border = BorderStroke(1.dp, palette.border.copy(alpha = .7f))) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            metrics.forEachIndexed { index, metric ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(metric.icon, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(metric.value, color = palette.text, style = MonoValueStyleSmall, maxLines = 1)
                    Text(metric.label, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index < metrics.lastIndex) Box(Modifier.width(1.dp).height(38.dp).background(palette.border))
            }
        }
    }
}

@Composable
private fun LivePerformanceCard(
    dashboard: DashboardState,
    palette: HomePalette,
    onCpu: () -> Unit,
    onGpu: (() -> Unit)?,
    onMemory: () -> Unit,
    onThermal: () -> Unit
) {
    val ramPercent = dashboard.ramTotalMb.takeIf { it > 0 }?.let { dashboard.ramUsedMb * 100 / it }
    val temperature = primaryBatteryTemperatureC(dashboard)?.roundToInt()
    DashboardCard(palette.primary, palette, padded = true) {
        SectionTitle(
            title = stringResource(R.string.home_live_performance),
            subtitle = stringResource(R.string.home_live_performance_desc),
            icon = Icons.Rounded.Speed,
            accent = palette.primary,
            palette = palette
        )
        Spacer(Modifier.height(14.dp))
        PerformanceMetric(
            label = "CPU",
            value = "${dashboard.cpuLoadPercent}%",
            detail = compactFrequency(dashboard.cpuFreqMhz),
            fraction = dashboard.cpuLoadPercent / 100f,
            icon = Icons.Rounded.DeveloperBoard,
            accent = palette.primary,
            palette = palette,
            onClick = onCpu
        )
        Spacer(Modifier.height(9.dp))
        PerformanceMetric(
            label = "GPU",
            value = dashboard.gpuLoadPercent?.let { "$it%" } ?: "—",
            detail = dashboard.gpuFreqMhz?.let(::compactFrequency) ?: stringResource(R.string.home_sensor_unavailable),
            fraction = (dashboard.gpuLoadPercent ?: 0) / 100f,
            icon = Icons.Rounded.Memory,
            accent = palette.secondary,
            palette = palette,
            enabled = onGpu != null,
            onClick = onGpu ?: {}
        )
        Spacer(Modifier.height(9.dp))
        PerformanceMetric(
            label = "RAM",
            value = ramPercent?.let { "$it%" } ?: "—",
            detail = "${memoryValue(dashboard.ramUsedMb)} / ${memoryValue(dashboard.ramTotalMb)}",
            fraction = (ramPercent ?: 0) / 100f,
            icon = Icons.Rounded.Memory,
            accent = palette.positive,
            palette = palette,
            onClick = onMemory
        )
        Spacer(Modifier.height(9.dp))
        PerformanceMetric(
            label = stringResource(R.string.home_temperature_short),
            value = temperature?.let { "$it°C" } ?: "—",
            detail = stringResource(R.string.home_open_details),
            fraction = (temperature ?: 0) / 80f,
            icon = Icons.Rounded.Thermostat,
            accent = temperatureColor(temperature, palette),
            palette = palette,
            onClick = onThermal
        )
    }
}

@Composable
private fun PerformanceMetric(
    label: String,
    value: String,
    detail: String,
    fraction: Float,
    icon: ImageVector,
    accent: Color,
    palette: HomePalette,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    var metricModifier = Modifier.fillMaxWidth().clip(InnerShape)
        .background(if (enabled) accent.copy(alpha = .055f) else palette.surfaceRaised.copy(alpha = .62f))
        .border(1.dp, if (enabled) accent.copy(alpha = .16f) else palette.border.copy(alpha = .55f), InnerShape)
    if (enabled) metricModifier = metricModifier.clickable(onClick = onClick)
    Row(metricModifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = .12f)) {
            Icon(icon, null, tint = accent, modifier = Modifier.padding(8.dp).size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = palette.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = if (enabled) accent else palette.muted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            ThinProgress(fraction, accent, palette, Modifier.width(62.dp), 4.dp)
        }
        if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun CpuCoreMatrix(cores: List<CpuCoreState>, palette: HomePalette, onOpen: () -> Unit) {
    DashboardCard(palette.primary, palette, padded = true, onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                stringResource(R.string.home_cpu_cores),
                if (cores.isEmpty()) stringResource(R.string.home_waiting_core_data)
                else stringResource(R.string.home_cpu_cores_online, cores.count { it.online }, cores.size),
                Icons.Rounded.DeveloperBoard,
                palette.primary,
                palette,
                Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = palette.primary)
        }
        Spacer(Modifier.height(16.dp))
        if (cores.isEmpty()) {
            Text(stringResource(R.string.max_home_waiting_samples), color = palette.muted, modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cores.forEach { CpuCoreTile(it, palette, Modifier.weight(1f)) }
                repeat((4 - cores.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CpuCoreTile(core: CpuCoreState, palette: HomePalette, modifier: Modifier) {
    val accent = when (core.clusterTag) {
        "PRIME" -> palette.warning
        "GOLD" -> palette.secondary
        else -> palette.primary
    }
    Surface(
        modifier = modifier.widthIn(min = 64.dp).aspectRatio(.82f),
        shape = RoundedCornerShape(14.dp),
        color = if (core.online) accent.copy(alpha = .06f) else palette.surfaceRaised.copy(alpha = .6f),
        border = BorderStroke(1.dp, if (core.online) accent.copy(alpha = .2f) else palette.border)
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (core.online) accent else palette.muted, 5.dp)
                Spacer(Modifier.weight(1f))
                Text("C${core.cpu}", color = if (core.online) accent else palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.DeveloperBoard, null, tint = if (core.online) accent else palette.muted, modifier = Modifier.size(18.dp))
            AnimatedContent(core.freqMhz, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "coreFreq") { freq ->
                Text(if (core.online) freq.toString() else "OFF", color = if (core.online) palette.text else palette.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(if (core.online) "MHz" else core.clusterTag, color = palette.muted, fontSize = 8.sp, maxLines = 1)
            ThinProgress(core.loadFraction, accent, palette, Modifier.fillMaxWidth(), 3.dp)
        }
    }
}

@Composable
private fun MemoryStorageCard(dashboard: DashboardState, palette: HomePalette, onMemory: () -> Unit, onStorage: () -> Unit) {
    val ramFraction = dashboard.ramTotalMb.takeIf { it > 0 }?.let { dashboard.ramUsedMb.toFloat() / it } ?: 0f
    val storageFraction = dashboard.storageTotalGb.takeIf { it > 0f }?.let { dashboard.storageUsedGb / it } ?: 0f
    DashboardCard(palette.secondary, palette, padded = true) {
        SectionTitle(
            stringResource(R.string.home_memory_storage),
            stringResource(R.string.home_memory_storage_desc),
            Icons.Rounded.Memory,
            palette.secondary,
            palette
        )
        Spacer(Modifier.height(14.dp))
        MemoryResourceRow(
            icon = Icons.Rounded.Memory,
            title = "RAM",
            value = "${memoryValue(dashboard.ramUsedMb)} / ${memoryValue(dashboard.ramTotalMb)}",
            detail = dashboard.ramTotalMb.takeIf { it > 0 }?.let { stringResource(R.string.home_available_memory, memoryValue((it - dashboard.ramUsedMb).coerceAtLeast(0))) } ?: "—",
            fraction = ramFraction,
            accent = palette.positive,
            palette = palette,
            onClick = onMemory
        )
        Spacer(Modifier.height(9.dp))
        val swapTotal = dashboard.swapTotalMb
        val swapUsed = dashboard.swapUsedMb
        if (swapTotal != null && swapUsed != null) {
            MemoryResourceRow(
                icon = Icons.Outlined.Compress,
                title = "ZRAM",
                value = "${memoryValue(swapUsed)} / ${memoryValue(swapTotal)}",
                detail = stringResource(R.string.home_available_swap, memoryValue((swapTotal - swapUsed).coerceAtLeast(0))),
                fraction = if (swapTotal > 0) swapUsed.toFloat() / swapTotal else 0f,
                accent = palette.primary,
                palette = palette,
                onClick = onMemory
            )
        } else {
            MemoryResourceRow(
                icon = Icons.Outlined.Compress,
                title = "ZRAM",
                value = "—",
                detail = stringResource(R.string.home_zram_unavailable),
                fraction = 0f,
                accent = palette.muted,
                palette = palette,
                onClick = onMemory
            )
        }
        Spacer(Modifier.height(9.dp))
        MemoryResourceRow(
            icon = Icons.Rounded.Storage,
            title = stringResource(R.string.max_home_storage),
            value = dashboard.storageTotalGb.takeIf { it > 0f }?.let { "${dashboard.storageUsedGb.oneDecimal()} / ${it.oneDecimal()} GB" } ?: "—",
            detail = dashboard.storageTotalGb.takeIf { it > 0f }?.let { stringResource(R.string.home_available_storage, (it - dashboard.storageUsedGb).coerceAtLeast(0f).oneDecimal()) } ?: "—",
            fraction = storageFraction,
            accent = palette.secondary,
            palette = palette,
            onClick = onStorage
        )
    }
}

@Composable
private fun MemoryResourceRow(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    fraction: Float,
    accent: Color,
    palette: HomePalette,
    onClick: () -> Unit
) {
    Surface(shape = InnerShape, color = palette.surfaceRaised, border = BorderStroke(1.dp, accent.copy(alpha = .14f)), onClick = onClick) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = palette.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(detail, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(value, color = palette.text, style = MonoValueStyleSmall, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(10.dp))
            ThinProgress(fraction, accent, palette, Modifier.fillMaxWidth(), 5.dp)
        }
    }
}

@Composable
private fun QuickActionsGrid(
    palette: HomePalette,
    onBoost: () -> Unit,
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onAdvanced: () -> Unit
) {
    Column {
        SectionTitle(stringResource(R.string.home_quick_actions), stringResource(R.string.home_quick_actions_desc), Icons.Rounded.RocketLaunch, palette.secondary, palette)
        Spacer(Modifier.height(12.dp))
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.RocketLaunch, stringResource(R.string.home_action_boost), stringResource(R.string.home_action_boost_desc), palette.primary, palette, onBoost, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Thermostat, stringResource(R.string.home_action_thermal), stringResource(R.string.home_action_thermal_desc), palette.warning, palette, onThermal, Modifier.weight(1f))
            QuickAction(Icons.Rounded.BatteryChargingFull, stringResource(R.string.home_action_battery), stringResource(R.string.home_action_battery_desc), palette.positive, palette, onBattery, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Tune, stringResource(R.string.home_action_advanced), stringResource(R.string.home_action_advanced_desc), palette.secondary, palette, onAdvanced, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, subtitle: String, accent: Color, palette: HomePalette, onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier.heightIn(min = 118.dp), shape = RoundedCornerShape(20.dp), color = palette.surfaceRaised, border = BorderStroke(1.dp, accent.copy(alpha = .20f)), onClick = onClick) {
        Box(Modifier.padding(14.dp)) {
            Column {
                Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .14f)) { Icon(icon, null, tint = accent, modifier = Modifier.padding(9.dp).size(20.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(title, color = palette.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
        }
    }
}

@Composable
private fun DeviceResourcesCard(dashboard: DashboardState, palette: HomePalette, onNavigate: (String) -> Unit) {
    DashboardCard(palette.warning, palette, padded = true) {
        SectionTitle(stringResource(R.string.home_device_resources), stringResource(R.string.home_device_resources_desc), Icons.Rounded.DisplaySettings, palette.warning, palette)
        Spacer(Modifier.height(14.dp))
        ResourceLink(Icons.Rounded.DisplaySettings, stringResource(R.string.studio_display), if (dashboard.displayWidth > 0) "${dashboard.displayWidth}×${dashboard.displayHeight} · ${dashboard.displayRefreshHz} Hz · ${dashboard.displayDensityDpi} dpi" else "—", palette.primary, palette) { onNavigate("displaystudio") }
        Spacer(Modifier.height(9.dp))
        ResourceLink(Icons.Rounded.NetworkCheck, stringResource(R.string.max_home_network), "↓ ${formatNetSpeed(dashboard.downloadSpeedKbps)}  ↑ ${formatNetSpeed(dashboard.uploadSpeedKbps)}", palette.positive, palette) { onNavigate("networkscheduler") }
        Spacer(Modifier.height(9.dp))
        ResourceLink(Icons.Rounded.Bolt, stringResource(R.string.home_power_draw), if (dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W · ${dashboard.batteryVoltageV.oneDecimal()} V" else "—", palette.warning, palette) { onNavigate("chargingscreen") }
    }
}

@Composable
private fun ResourceLink(icon: ImageVector, title: String, value: String, accent: Color, palette: HomePalette, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(15.dp), color = palette.surfaceRaised, border = BorderStroke(1.dp, palette.border.copy(alpha = .65f)), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = .1f)) { Icon(icon, null, tint = accent, modifier = Modifier.padding(8.dp).size(18.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(value, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AiCommandCard(state: MaxAiState, request: ProfileRequestState, palette: HomePalette, onOpen: () -> Unit, onRetry: () -> Unit) {
    DashboardCard(palette.secondary, palette, onClick = onOpen) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = palette.secondary.copy(alpha = .13f), border = BorderStroke(1.dp, palette.secondary.copy(alpha = .3f))) {
                Icon(Icons.Rounded.Psychology, null, tint = palette.secondary, modifier = Modifier.padding(13.dp).size(26.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_ai_center), color = palette.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    StatusDot(if (state.aiEnabled) palette.positive else palette.muted, 6.dp)
                }
                Text(when { request.inFlight -> stringResource(R.string.max_home_ai_working); state.lastDecision != null -> state.lastDecision.label; else -> state.strategyLabel }, color = palette.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (request.result == DecisionResult.FAILED) {
                Surface(shape = RoundedCornerShape(10.dp), color = palette.danger.copy(alpha = .1f), onClick = onRetry) {
                    Text(stringResource(R.string.max_home_retry), color = palette.danger, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                }
            } else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = palette.secondary)
        }
    }
}

@Composable
private fun ConnectivityStrip(dashboard: DashboardState, engineOnline: Boolean, palette: HomePalette) {
    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(Icons.Rounded.Wifi, stringResource(R.string.home_network_status), if (dashboard.downloadSpeedKbps > 0 || dashboard.uploadSpeedKbps > 0) stringResource(R.string.home_active) else stringResource(R.string.home_idle), palette.primary, palette, Modifier.weight(1f))
        StatusChip(Icons.Rounded.Security, stringResource(R.string.home_engine_status), if (engineOnline) stringResource(R.string.home_protected) else stringResource(R.string.max_home_offline), if (engineOnline) palette.positive else palette.danger, palette, Modifier.weight(1f))
        StatusChip(Icons.Rounded.DeveloperBoard, stringResource(R.string.home_cores_status), dashboard.cores.takeIf { it.isNotEmpty() }?.let { "${it.count(CpuCoreState::online)}/${it.size}" } ?: "—", palette.secondary, palette, Modifier.weight(1f))
    }
}

@Composable
private fun StatusChip(icon: ImageVector, title: String, value: String, accent: Color, palette: HomePalette, modifier: Modifier) {
    Surface(modifier = modifier.heightIn(min = 68.dp), shape = RoundedCornerShape(16.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, icon: ImageVector, accent: Color, palette: HomePalette, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = .1f)) { Icon(icon, null, tint = accent, modifier = Modifier.padding(8.dp).size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DashboardCard(
    accent: Color,
    palette: HomePalette,
    modifier: Modifier = Modifier,
    padded: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var cardModifier = modifier.fillMaxWidth().clip(DashboardShape).background(palette.surface)
        .border(BorderStroke(1.dp, accent.copy(alpha = .18f)), DashboardShape)
    if (onClick != null) cardModifier = cardModifier.clickable(onClick = onClick)
    Column(cardModifier.then(if (padded) Modifier.padding(18.dp) else Modifier), content = content)
}

@Composable
private fun ThinProgress(fraction: Float, accent: Color, palette: HomePalette, modifier: Modifier, height: Dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600, easing = FastOutSlowInEasing), label = "dashboardBar")
    Box(modifier.height(height).clip(CircleShape).background(palette.border.copy(alpha = .65f)).semantics { progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f) }) {
        Box(Modifier.fillMaxWidth(animated).height(height).background(accent))
    }
}

@Composable
private fun LivePill(online: Boolean, palette: HomePalette) {
    val color = if (online) palette.positive else palette.danger
    Surface(shape = CircleShape, color = color.copy(alpha = .08f), border = BorderStroke(1.dp, color.copy(alpha = .3f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (online) R.string.home_system_stable else R.string.home_system_attention), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusDot(color: Color, size: Dp = 7.dp) { Box(Modifier.size(size).clip(CircleShape).background(color)) }

private fun temperatureColor(value: Int?, palette: HomePalette): Color = when {
    value == null -> palette.muted
    value >= 48 -> palette.danger
    value >= 42 -> palette.warning
    else -> palette.positive
}
