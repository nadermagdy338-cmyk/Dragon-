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
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.NeuralActionTile
import nd.max.ui.component.NeuralBarSpectrum
import nd.max.ui.component.NeuralBudgetBar
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralExpandable
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
 * Question the screen answers: how is the device right now, and what is the one
 * thing worth touching? Everything that only explained MAX to itself (system
 * passport, AI console, the old "live performance" gauge stack, the base
 * profile row) is gone; those live in Max AI, Control and Diagnostics.
 *
 * Reading order, each block earning its place exactly once:
 *  1. pulse   — device identity, heat with a stable/attention read, and
 *               uptime, battery and power draw at a glance
 *  2. focus   — appears only when something is actually wrong
 *  3. trends  — CPU and GPU with smoothed, auto-scaled sparklines
 *  4. spectrum— per-sample load bars, tinted calm to hot
 *  5. memory  — RAM, compressed swap and storage budgets
 *  6. verdict — the limiter, in one sentence, with recent events
 *  7. details — collapsed: core matrix, display, network, storage
 *  8. deck    — four destinations people actually reach for
 *
 * No metric is drawn twice, and every color comes from MaterialTheme through
 * neuralPalette(), so the Settings theme drives the entire screen.
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
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeHeader(online, onSettings, onReboot)
        PulsePanel(
            deviceName = deviceName,
            dashboard = dashboard,
            onOverview = { onNavigate(MaxDestination.Diagnostics.route) }
        )
        FocusCard(dashboard, onNavigate)
        TrendDuo(
            dashboard = dashboard,
            onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
            onGpu = { onNavigate(gpuRoute ?: MaxDestination.GpuStudio.route) }
        )
        SpectrumPanel(dashboard) { onNavigate(MaxDestination.MaxLive.route) }
        MemoryBudgetPanel(
            dashboard = dashboard,
            onMemory = { onNavigate(MaxDestination.ZramManager.route) },
            onStorage = { onNavigate(MaxDestination.StorageDetail.route) }
        )
        VerdictPanel(
            dashboard = dashboard,
            maxAi = maxAi,
            request = profileRequest,
            onLive = { onNavigate(MaxDestination.MaxLive.route) },
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onRetry = onAiRetry
        )
        DetailsSection(
            dashboard = dashboard,
            expanded = detailsOpen,
            onToggle = { detailsOpen = !detailsOpen },
            onNavigate = onNavigate
        )
        CommandDeck(
            onBoost = onProfile,
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onBattery = { onNavigate(MaxDestination.Charging.route) },
            onAdvanced = { onNavigate(MaxDestination.Control.route) }
        )
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

/**
 * The signature block. One headline reading (heat plus a plain-language verdict)
 * over three pressure meters, because the first question is never "what is CPU
 * load" but "is anything under pressure, and is the device hot". Straight bars
 * beat a dial here: they share one baseline, so three values are comparable at a
 * glance and every label has room to breathe in either writing direction.
 */
@Composable
private fun PulsePanel(
    deviceName: String,
    dashboard: DashboardState,
    onOverview: () -> Unit
) {
    val p = neuralPalette()
    val heat = primaryBatteryTemperatureC(dashboard)?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val heatAccent = temperatureAccent(heat)
    val calm = heat == null || heat < 43
    NeuralPanel(accent = p.accent, contentPadding = PaddingValues(18.dp), verticalSpacing = 16.dp) {
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
                Text(
                    dashboard.chipsetName,
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NeuralCaption(stringResource(R.string.home_temperature_short), color = heatAccent)
                Row(verticalAlignment = Alignment.Bottom) {
                    NeuralValue(
                        heat?.toString() ?: "\u2014",
                        style = MonoValueStyleSmall.copy(
                            fontSize = 44.sp,
                            lineHeight = 48.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = p.text
                    )
                    Spacer(Modifier.width(4.dp))
                    NeuralCaption("\u00b0C", color = p.muted)
                }
            }
            NeuralPill(
                text = stringResource(if (calm) R.string.home_system_stable else R.string.home_system_attention),
                accent = if (calm) p.ok else heatAccent,
                dot = true
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.max_home_uptime),
                value = compactUptime(dashboard.uptimeMinutes),
                accent = p.accent,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.max_home_battery),
                value = "${dashboard.batteryPercent}%",
                accent = if (dashboard.isCharging) p.ok else p.accentAlt,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_power_draw),
                value = "${dashboard.powerWatt.oneDecimal()} W",
                accent = p.warn,
                modifier = Modifier.weight(1f)
            )
        }
        NeuralPill(
            text = stringResource(R.string.home_device_overview),
            accent = p.muted,
            onClick = onOverview
        )
    }
}

/** Shown only when a real problem exists, so its presence itself means something. */
@Composable
private fun FocusCard(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    val heat = primaryBatteryTemperatureC(dashboard)?.roundToInt() ?: dashboard.cpuTempC
    val ram = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)
    val storageFree = if (dashboard.storageTotalGb <= 0f) 1f else
        ((dashboard.storageTotalGb - dashboard.storageUsedGb) / dashboard.storageTotalGb).coerceIn(0f, 1f)

    val title: String
    val body: String
    val accent: Color
    val icon: ImageVector
    val route: String
    when {
        heat >= 45 -> {
            title = stringResource(R.string.home_focus_heat)
            body = stringResource(R.string.home_focus_heat_desc)
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            route = MaxDestination.ThermalDetail.route
        }
        storageFree < 0.10f -> {
            title = stringResource(R.string.home_focus_storage)
            body = stringResource(R.string.home_focus_storage_desc)
            accent = p.warn
            icon = Icons.Rounded.Storage
            route = MaxDestination.StorageDetail.route
        }
        ram >= 0.90f -> {
            title = stringResource(R.string.home_focus_memory)
            body = stringResource(R.string.home_focus_memory_desc)
            accent = p.accentAlt
            icon = Icons.Rounded.Speed
            route = MaxDestination.ZramManager.route
        }
        else -> return
    }
    NeuralPanel(accent = accent, onClick = { onNavigate(route) }, verticalSpacing = 8.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(icon, accent, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    color = p.text,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** CPU and GPU trends. RAM lives in the budget panel, heat lives in the ring. */
@Composable
private fun TrendDuo(dashboard: DashboardState, onCpu: () -> Unit, onGpu: () -> Unit) {
    val p = neuralPalette()
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
}

/**
 * Replaces the old "live performance" card. A line chart answered "what is the
 * trend", which the sparklines already do; bars answer the more useful question
 * on a phone — how often and how hard does the system spike — and the calm-to-hot
 * tint makes that visible without reading a single number.
 */
@Composable
private fun SpectrumPanel(dashboard: DashboardState, onLive: () -> Unit) {
    val p = neuralPalette()
    val history = dashboard.cpuLoadHistory
    val peak = history.maxOrNull()?.roundToInt() ?: 0
    val avg = if (history.isEmpty()) 0 else (history.sum() / history.size).roundToInt()
    val now = history.lastOrNull()?.roundToInt() ?: dashboard.cpuLoadPercent
    NeuralPanel(onClick = onLive) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_spectrum_title),
            caption = stringResource(R.string.home_spectrum_caption),
            accent = p.accent,
            trailing = {
                NeuralPill(stringResource(R.string.home_session_open_loop), p.accent, filled = true, dot = true)
            }
        )
        if (history.size > 1) {
            NeuralBarSpectrum(
                values = history,
                accent = p.accent,
                hot = p.danger,
                modifier = Modifier.fillMaxWidth().height(96.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuralFactTile(stringResource(R.string.home_stat_now), "$now%", p.accent, Modifier.weight(1f))
                NeuralFactTile(stringResource(R.string.home_stat_avg), "$avg%", p.ok, Modifier.weight(1f))
                NeuralFactTile(stringResource(R.string.home_stat_peak), "$peak%", p.danger, Modifier.weight(1f))
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

/** One verdict: what is limiting the device, how sure we are, what changed. */
@Composable
private fun VerdictPanel(
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
        intel.events.lastOrNull()?.let { event ->
            NeuralFeedRow(
                icon = Icons.Rounded.Bolt,
                title = event.title,
                meta = event.impact.takeIf { it.isNotBlank() },
                accent = p.accentAlt
            )
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
                NeuralCaption(maxAi.strategyLabel)
            }
        }
    }
}

/** Secondary detail, collapsed by default so the first screen stays calm. */
@Composable
private fun DetailsSection(
    dashboard: DashboardState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = neuralPalette()
    val cores = dashboard.cores
    NeuralExpandable(
        title = stringResource(R.string.home_details_title),
        caption = stringResource(R.string.home_details_caption),
        expanded = expanded,
        accent = p.accentAlt,
        onToggle = onToggle
    ) {
        if (cores.isEmpty()) {
            NeuralCaption(stringResource(R.string.home_waiting_core_data))
        } else {
            NeuralCaption(stringResource(R.string.home_cpu_cores_online, cores.count { it.online }, cores.size))
            // Core identity is positional: C0 must read first regardless of locale.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FabricChip(
                icon = Icons.Rounded.DisplaySettings,
                label = stringResource(R.string.max_hub_display),
                value = if (dashboard.displayWidth > 0) "${dashboard.displayWidth}x${dashboard.displayHeight}" else "\u2014",
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
                icon = Icons.Rounded.BatteryChargingFull,
                label = stringResource(R.string.max_home_battery),
                value = "${dashboard.batteryVoltageV.oneDecimal()} V",
                support = dashboard.batteryStatus.takeIf { it.isNotBlank() },
                accent = p.warn,
                modifier = Modifier.weight(1f),
                // الشاشتان دُمجتا: "BatteryDetail" لم يعد موجودًا، وهذا المدخل يذهب
                // إلى الشاشة المدمجة نفسها التي يذهب إليها مدخل البطارية في الأعلى.
                onClick = { onNavigate(MaxDestination.Charging.route) }
            )
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
        NeuralIconChip(icon, accent, size = 26.dp)
        NeuralCaption(label, color = accent)
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