package nd.max.ui.mainscreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.NeuralActionTile
import nd.max.ui.component.NeuralAreaPlot
import nd.max.ui.component.NeuralBudgetBar
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralKpiTile
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTile
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.CpuCoreState
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The MAX "Now" dashboard.
 *
 * What this screen is for: answer "how is my device right now, and what is the
 * one thing worth touching?" in a single scroll. Everything that only explains
 * MAX itself (system passport, engine verdict copy, AI console, duplicated
 * gauges) moved out; those belong to Max AI and Diagnostics, and on the home
 * screen they pushed the actual measurements below the fold.
 *
 * Eight blocks, in decreasing order of "what do I look at first":
 * identity -> four KPIs -> live plot -> memory budget -> one insight ->
 * core matrix -> command deck -> fabric strip.
 *
 * Every color comes from MaterialTheme.colorScheme through [neuralPalette], so
 * the screen follows the palette, contrast and light/dark mode chosen in
 * Settings instead of hardcoding a dark look. Every live number renders through
 * NeuralValue, which pins direction to LTR: "2712x1220 - 120 Hz" is Latin
 * technical notation and must not be reordered by an RTL locale.
 */

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun compactFrequency(mhz: Int?): String = when {
    mhz == null || mhz <= 0 -> "\u2014"
    mhz >= 1000 -> "${(mhz / 1000f).oneDecimal()} GHz"
    else -> "$mhz MHz"
}

private fun compactUptime(minutes: Long): String = when {
    minutes <= 0 -> "\u2014"
    minutes >= 1440 -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

private fun gigabytes(mb: Int): String = when {
    mb <= 0 -> "\u2014"
    mb >= 1024 -> "${(mb / 1024f).oneDecimal()} GB"
    else -> "$mb MB"
}

private fun netSpeed(kbps: Long): String = when {
    kbps <= 0 -> "0 KB/s"
    kbps >= 1024 -> "${(kbps / 1024f).oneDecimal()} MB/s"
    else -> "$kbps KB/s"
}

private fun fractionOf(used: Int, total: Int): Float =
    if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)

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
    val online = ui.rootStatus && ui.moduleInstalled
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeHeader(online, onSettings, onReboot)
        DeviceIdentityPanel(
            deviceName = deviceName,
            dashboard = dashboard,
            profile = stringResource(ui.currentProfileRes),
            onProfile = onProfile,
            onOverview = { onNavigate(MaxDestination.Diagnostics.route) }
        )
        KpiGrid(
            dashboard = dashboard,
            onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
            onGpu = { onNavigate(gpuRoute ?: MaxDestination.GpuStudio.route) },
            onMemory = { onNavigate(MaxDestination.ZramManager.route) },
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) }
        )
        LiveLoadPanel(dashboard) { onNavigate(MaxDestination.MaxLive.route) }
        MemoryBudgetPanel(
            dashboard = dashboard,
            onMemory = { onNavigate(MaxDestination.ZramManager.route) },
            onStorage = { onNavigate(MaxDestination.StorageDetail.route) }
        )
        InsightPanel(
            dashboard = dashboard,
            maxAi = maxAi,
            request = profileRequest,
            onLive = { onNavigate(MaxDestination.MaxLive.route) },
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onRetry = onAiRetry
        )
        CoreMatrixPanel(dashboard.cores) { onNavigate(MaxDestination.CpuCoreControl.route) }
        CommandDeck(
            onBoost = onProfile,
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onBattery = { onNavigate(MaxDestination.Charging.route) },
            onAdvanced = { onNavigate(MaxDestination.Control.route) }
        )
        FabricStrip(dashboard, onNavigate)
    }
}

@Composable
private fun HomeHeader(online: Boolean, onSettings: () -> Unit, onReboot: () -> Unit) {
    val p = neuralPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "MAX",
                color = p.text,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
            )
            Text(
                stringResource(if (online) R.string.home_engine_ready else R.string.home_engine_offline),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NeuralPill(
            text = stringResource(if (online) R.string.home_active else R.string.home_idle),
            accent = if (online) p.ok else p.danger,
            filled = true,
            dot = true
        )
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), onReboot)
        Spacer(Modifier.width(6.dp))
        HeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), onSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .size(38.dp)
            .clip(shape)
            .background(p.tile)
            .border(BorderStroke(1.dp, p.border), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(18.dp), tint = p.muted)
    }
}

/** Who am I, what profile am I running, and the three numbers people check first. */
@Composable
private fun DeviceIdentityPanel(
    deviceName: String,
    dashboard: DashboardState,
    profile: String,
    onProfile: () -> Unit,
    onOverview: () -> Unit
) {
    val p = neuralPalette()
    val battTemp = primaryBatteryTemperatureC(dashboard)
    NeuralPanel(accent = p.accent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    deviceName,
                    color = p.text,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NeuralValue(
                    dashboard.chipsetName,
                    style = MonoValueStyleSmall.copy(fontSize = 11.sp),
                    color = p.muted
                )
            }
            NeuralPill(
                text = stringResource(R.string.home_device_overview),
                accent = p.muted,
                onClick = onOverview
            )
        }
        NeuralTile(Modifier.fillMaxWidth(), accent = p.accent, onClick = onProfile, verticalSpacing = 4.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    NeuralCaption(stringResource(R.string.max_home_active_profile), color = p.accent)
                    Text(
                        profile,
                        color = p.text,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp), tint = p.accent)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.max_home_uptime),
                value = compactUptime(dashboard.uptimeMinutes),
                accent = p.accent,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.max_home_temp),
                value = battTemp?.let { "${it.oneDecimal()}\u00b0C" } ?: "\u2014",
                accent = temperatureAccent(battTemp?.roundToInt()),
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.max_home_battery),
                value = "${dashboard.batteryPercent}%",
                accent = if (dashboard.isCharging) p.ok else p.accentAlt,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Four measurements, each with its own trend. This is the core of the screen. */
@Composable
private fun KpiGrid(
    dashboard: DashboardState,
    onCpu: () -> Unit,
    onGpu: () -> Unit,
    onMemory: () -> Unit,
    onThermal: () -> Unit
) {
    val p = neuralPalette()
    val ramPercent = (fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb) * 100).roundToInt()
    val heat = primaryBatteryTemperatureC(dashboard)?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralKpiTile(
                caption = "CPU",
                value = "${dashboard.cpuLoadPercent}%",
                accent = p.accent,
                support = compactFrequency(dashboard.cpuFreqMhz),
                history = dashboard.cpuLoadHistory,
                onClick = onCpu,
                modifier = Modifier.weight(1f)
            )
            NeuralKpiTile(
                caption = "GPU",
                value = dashboard.gpuLoadPercent?.let { "$it%" } ?: "\u2014",
                accent = p.accentAlt,
                support = compactFrequency(dashboard.gpuFreqMhz),
                history = dashboard.gpuLoadHistory,
                onClick = onGpu,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralKpiTile(
                caption = "RAM",
                value = "$ramPercent%",
                accent = p.ok,
                support = "${gigabytes(dashboard.ramUsedMb)} / ${gigabytes(dashboard.ramTotalMb)}",
                history = dashboard.ramLoadHistory,
                onClick = onMemory,
                modifier = Modifier.weight(1f)
            )
            NeuralKpiTile(
                caption = stringResource(R.string.home_temperature_short),
                value = heat?.let { "$it\u00b0C" } ?: "\u2014",
                accent = temperatureAccent(heat),
                support = "${dashboard.powerWatt.oneDecimal()} W",
                onClick = onThermal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One chart instead of three gauge cards: CPU filled, GPU as a reference line. */
@Composable
private fun LiveLoadPanel(dashboard: DashboardState, onLive: () -> Unit) {
    val p = neuralPalette()
    val cpu = dashboard.cpuLoadHistory
    val gpu = dashboard.gpuLoadHistory
    val peak = cpu.maxOrNull()?.roundToInt() ?: 0
    NeuralPanel(onClick = onLive) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_live_performance),
            caption = stringResource(R.string.home_live_performance_desc),
            trailing = {
                NeuralPill(stringResource(R.string.home_session_open_loop), p.accent, filled = true, dot = true)
            }
        )
        if (cpu.size > 1) {
            NeuralAreaPlot(
                values = cpu,
                accent = p.accent,
                secondary = gpu,
                secondaryAccent = p.accentAlt,
                modifier = Modifier.fillMaxWidth().height(112.dp)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LegendDot("CPU", p.accent)
                if (gpu.size > 1) {
                    Spacer(Modifier.width(14.dp))
                    LegendDot("GPU", p.accentAlt)
                }
                Spacer(Modifier.weight(1f))
                NeuralValue(
                    "PEAK $peak%",
                    style = MonoValueStyleSmall.copy(fontSize = 11.sp),
                    color = p.muted
                )
            }
        } else {
            NeuralValue(
                stringResource(R.string.max_home_waiting_samples),
                style = MonoValueStyleSmall.copy(fontSize = 11.sp),
                color = p.muted
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        NeuralCaption(label)
    }
}

/** RAM, compressed swap and storage as three budgets, not three separate cards. */
@Composable
private fun MemoryBudgetPanel(
    dashboard: DashboardState,
    onMemory: () -> Unit,
    onStorage: () -> Unit
) {
    val p = neuralPalette()
    val swapUsed = dashboard.swapUsedMb
    val swapTotal = dashboard.swapTotalMb
    val storageUsed = dashboard.storageUsedGb
    val storageTotal = dashboard.storageTotalGb
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_memory_storage),
            caption = stringResource(R.string.home_memory_storage_desc),
            accent = p.ok
        )
        NeuralBudgetBar(
            label = "RAM",
            value = "${gigabytes(dashboard.ramUsedMb)} / ${gigabytes(dashboard.ramTotalMb)}",
            fraction = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb),
            accent = p.accent,
            support = stringResource(
                R.string.home_available_memory,
                gigabytes(dashboard.ramTotalMb - dashboard.ramUsedMb)
            ),
            onClick = onMemory
        )
        if (swapUsed != null && swapTotal != null && swapTotal > 0) {
            NeuralBudgetBar(
                label = "ZRAM",
                value = "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}",
                fraction = fractionOf(swapUsed, swapTotal),
                accent = p.accentAlt,
                support = stringResource(R.string.home_available_swap, gigabytes(swapTotal - swapUsed)),
                onClick = onMemory
            )
        }
        NeuralBudgetBar(
            label = stringResource(R.string.max_home_storage),
            value = "${storageUsed.oneDecimal()} / ${storageTotal.oneDecimal()} GB",
            fraction = if (storageTotal <= 0f) 0f else (storageUsed / storageTotal).coerceIn(0f, 1f),
            accent = p.ok,
            support = stringResource(R.string.home_available_storage, (storageTotal - storageUsed).oneDecimal()),
            onClick = onStorage
        )
    }
}

/**
 * The single verdict block. It replaced the old trio of "system passport",
 * "performance story" and "session report" cards, which each restated the same
 * limiter from a slightly different angle.
 */
@Composable
private fun InsightPanel(
    dashboard: DashboardState,
    maxAi: MaxAiState,
    request: ProfileRequestState,
    onLive: () -> Unit,
    onThermal: () -> Unit,
    onRetry: () -> Unit
) {
    val p = neuralPalette()
    val intel = dashboard.intelligence
    val accent = when (intel.realImprovement) {
        true -> p.ok
        false -> p.danger
        null -> p.accent
    }
    NeuralPanel(accent = accent) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_story_title),
            caption = stringResource(R.string.home_session_confidence, intel.confidencePercent),
            accent = accent,
            trailing = {
                NeuralPill(
                    text = when (intel.realImprovement) {
                        true -> stringResource(R.string.home_session_real_yes)
                        false -> stringResource(R.string.home_session_real_no)
                        null -> stringResource(R.string.home_session_collecting)
                    },
                    accent = accent,
                    filled = true
                )
            }
        )
        Text(
            intel.explanation,
            color = p.text,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        NeuralTrack(intel.confidencePercent / 100f, accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.home_story_bottleneck),
                value = intel.primaryLimiter,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_session_workload),
                value = intel.samples.lastOrNull()?.workload ?: "\u2014",
                accent = p.accentAlt,
                modifier = Modifier.weight(1f)
            )
        }
        if (intel.events.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intel.events.takeLast(2).reversed().forEach { event ->
                    NeuralFeedRow(
                        icon = Icons.Rounded.Bolt,
                        title = event.title,
                        meta = event.reason,
                        accent = p.accentAlt
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = stringResource(R.string.home_session_open_loop),
                accent = p.accent,
                icon = Icons.Rounded.Timeline,
                onClick = onLive
            )
            Spacer(Modifier.width(8.dp))
            NeuralPill(
                text = stringResource(R.string.home_session_open_heat),
                accent = p.warn,
                icon = Icons.Rounded.Thermostat,
                onClick = onThermal
            )
            Spacer(Modifier.weight(1f))
            if (request.inFlight) {
                NeuralValue(
                    stringResource(R.string.max_home_ai_working),
                    style = MonoValueStyleSmall.copy(fontSize = 10.sp),
                    color = p.muted
                )
            } else if (request.result != null) {
                NeuralPill(
                    text = stringResource(R.string.max_home_retry),
                    accent = p.muted,
                    onClick = onRetry
                )
            } else if (maxAi.strategyLabel.isNotBlank()) {
                NeuralValue(
                    maxAi.strategyLabel,
                    style = MonoValueStyleSmall.copy(fontSize = 10.sp),
                    color = p.muted
                )
            }
        }
    }
}

/** Per-core clocks as a compact chip matrix; the app's signature block. */
@Composable
private fun CoreMatrixPanel(cores: List<CpuCoreState>, onOpen: () -> Unit) {
    val p = neuralPalette()
    val onlineCores = cores.count { it.online }
    NeuralPanel(onClick = onOpen) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_cpu_cores),
            caption = if (cores.isEmpty()) {
                stringResource(R.string.home_waiting_core_data)
            } else {
                stringResource(R.string.home_cpu_cores_online, onlineCores, cores.size)
            },
            accent = p.accent
        )
        if (cores.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cores.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { core -> CoreChip(core, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreChip(core: CpuCoreState, modifier: Modifier) {
    val p = neuralPalette()
    val accent = if (core.online) p.accent else p.muted
    NeuralTile(
        modifier,
        accent = if (core.online) accent else null,
        verticalSpacing = 6.dp,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralCaption("C${core.cpu}", Modifier.weight(1f), color = accent)
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (core.online) 1f else .35f))
            )
        }
        NeuralValue(
            if (core.online) compactFrequency(core.freqMhz) else "OFF",
            style = MonoValueStyleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = if (core.online) p.text else p.muted
        )
        NeuralTrack(core.loadFraction, accent, height = 4.dp)
    }
}

/** Four destinations people actually reach for from the home screen. */
@Composable
private fun CommandDeck(
    onBoost: () -> Unit,
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onAdvanced: () -> Unit
) {
    val p = neuralPalette()
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_quick_actions),
            caption = stringResource(R.string.home_quick_actions_desc),
            accent = p.accentAlt
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.home_action_boost),
                support = stringResource(R.string.home_action_boost_desc),
                accent = p.accent,
                onClick = onBoost,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Thermostat,
                title = stringResource(R.string.home_action_thermal),
                support = stringResource(R.string.home_action_thermal_desc),
                accent = p.warn,
                onClick = onThermal,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.home_action_battery),
                support = stringResource(R.string.home_action_battery_desc),
                accent = p.ok,
                onClick = onBattery,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_action_advanced),
                support = stringResource(R.string.home_action_advanced_desc),
                accent = p.accentAlt,
                onClick = onAdvanced,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Display, network and power draw kept as one thin strip, not four fat rows. */
@Composable
private fun FabricStrip(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FabricChip(
            icon = Icons.Rounded.DisplaySettings,
            label = stringResource(R.string.max_hub_display),
            value = if (dashboard.displayWidth > 0) {
                "${dashboard.displayWidth}x${dashboard.displayHeight}"
            } else "\u2014",
            support = if (dashboard.displayRefreshHz > 0) "${dashboard.displayRefreshHz} Hz" else null,
            accent = p.accent,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(MaxDestination.DisplayStudio.route) }
        )
        FabricChip(
            icon = Icons.Rounded.NetworkCheck,
            label = stringResource(R.string.home_network_status),
            value = netSpeed(dashboard.downloadSpeedKbps),
            support = "\u2191 ${netSpeed(dashboard.uploadSpeedKbps)}",
            accent = p.ok,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(MaxDestination.NetworkDetail.route) }
        )
        FabricChip(
            icon = Icons.Rounded.Bolt,
            label = stringResource(R.string.home_power_draw),
            value = "${dashboard.powerWatt.oneDecimal()} W",
            support = "${dashboard.batteryVoltageV.oneDecimal()} V",
            accent = p.warn,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(MaxDestination.BatteryDetail.route) }
        )
    }
}

@Composable
private fun FabricChip(
    icon: ImageVector,
    label: String,
    value: String,
    support: String?,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = neuralPalette()
    NeuralTile(modifier, onClick = onClick, verticalSpacing = 6.dp, contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(icon, accent, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            NeuralCaption(label, Modifier.weight(1f), color = accent)
        }
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = p.text
        )
        if (support != null) {
            NeuralValue(
                support,
                style = MonoValueStyleSmall.copy(fontSize = 10.sp),
                color = p.muted
            )
        }
    }
}

@Composable
private fun temperatureAccent(value: Int?): Color {
    val p = neuralPalette()
    return when {
        value == null -> p.muted
        value >= 45 -> p.danger
        value >= 40 -> p.warn
        else -> p.ok
    }
}
