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
import androidx.compose.runtime.remember
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
import nd.max.ui.component.NeuralBudgetBar
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.NeuralFrequencyMeter
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralKpiTile
import nd.max.ui.component.NeuralLoadRibbon
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.util.ClockMeter
import nd.max.ui.util.Spectrum
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
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
 *  3. load    — CPU and GPU now, side by side
 *  4. spectrum— the last window of that same load, per sample, calm to hot; the
 *               only place on this screen where a series is drawn
 *  5. memory  — RAM, compressed swap and storage budgets
 *  6. verdict — the limiter, in one sentence, with recent events
 *  7. details — always open: the core matrix by cluster, display, network, voltage
 *  8. deck    — four destinations people actually reach for
 *
 * No metric is drawn twice. The rule is enforced by subtraction, not by hope: the
 * spectrum owns the history, so the load tiles carry no second sparkline of the same
 * series, the engine state is stated once (the pill, not a pill plus a subtitle), the
 * spectrum repeats neither the current CPU number nor the verdict's link, and the
 * battery is a percentage up top and a voltage down here — never the same label twice.
 * Every color comes from MaterialTheme through neuralPalette(), so the Settings theme
 * drives the entire screen.
 */

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
        HomeDetailsPanel(dashboard = dashboard, onNavigate = onNavigate)
        CommandDeck(
            onBoost = onProfile,
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onBattery = { onNavigate(MaxDestination.Charging.route) },
            onAdvanced = { onNavigate(MaxDestination.Control.route) }
        )
    }
}

/**
 * Brand, one state, two actions. The engine state is said once: it used to be a pill
 * ("active/idle", dot and color) *and* a subtitle line saying the same thing in words,
 * which is the definition of a screen that repeats itself. The pill keeps more
 * information (color, dot, localized word), so the duplicate line went.
 */
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

/**
 * Current CPU and GPU load — each with the frequency meter it belongs to.
 *
 * The load number is the tile's own reading; the meter answers the second question a bare
 * number cannot: **how far into its range is this clock**? A phone at 1.8 GHz is loafing on
 * a 3.2 GHz chip and pinned on a 2.0 GHz one, and only the meter says which.
 *
 * The frequency is stated **once**, by the meter. It used to be a support line *and* would
 * have been a bar; a number that appears twice under two names is how a screen teaches
 * people not to trust it. The trend of these same series is still drawn once, below, in the
 * spectrum — the tiles own "now", the chart owns "recently".
 *
 * CPU reads the **highest live core clock**, not `cpu0`: on big.LITTLE the first core idles
 * while the prime cluster does the work, so `cpu0` would draw a calm meter on a busy device.
 */
@Composable
private fun TrendDuo(dashboard: DashboardState, onCpu: () -> Unit, onGpu: () -> Unit) {
    val p = neuralPalette()
    val cpuCeiling = dashboard.cpuCeilingMhz.takeIf { it > 0 }
    val gpuCeiling = dashboard.gpuCeilingMhz?.takeIf { it > 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralKpiTile(
            caption = "CPU",
            value = "${dashboard.cpuLoadPercent}%",
            accent = p.accent,
            onClick = onCpu,
            modifier = Modifier.weight(1f),
            meter = {
                NeuralFrequencyMeter(
                    reading = compactFrequency(dashboard.cpuTopCoreMhz),
                    ceiling = frequencyCeilingLabel(dashboard.cpuTopCoreMhz, cpuCeiling),
                    fraction = ClockMeter.fraction(dashboard.cpuTopCoreMhz, cpuCeiling),
                    accent = p.accent,
                )
            }
        )
        NeuralKpiTile(
            caption = "GPU",
            value = dashboard.gpuLoadPercent?.let { "$it%" } ?: "\u2014",
            accent = p.accentAlt,
            onClick = onGpu,
            modifier = Modifier.weight(1f),
            meter = {
                NeuralFrequencyMeter(
                    reading = compactFrequency(dashboard.gpuFreqMhz),
                    ceiling = frequencyCeilingLabel(dashboard.gpuFreqMhz, gpuCeiling),
                    fraction = ClockMeter.fraction(dashboard.gpuFreqMhz ?: 0, gpuCeiling),
                    accent = p.accentAlt,
                )
            }
        )
    }
}

/**
 * The load spectrum, in two lanes.
 *
 * A line chart answered "what is the trend" — which the tiles above already answer with a
 * number; lanes of bars answer the more useful question on a phone: how often and how hard
 * does the system spike. The calm-to-hot tint makes that visible without reading a number.
 *
 * The previous drawing put CPU and GPU **side by side inside one slot**, so each bar was
 * half a slot wide on a 96dp canvas: a barcode. Here each load owns a full-width lane — GPU
 * on top, CPU below — and the newest sample carries a hotter cap, so "now" is visible in
 * the chart itself. The geometry still comes from [Spectrum], so the frame is a full set of
 * slots from the first second and the session's history is restored from disk.
 *
 * The summary now leads with **now**, then average, then peak, because "now" is what a
 * person looks for when they open this card: `avg` and `peak` describe a window they cannot
 * see the edges of, and only the current reading is a fact about this moment. Numbers here
 * come from the same live state as the tiles above, so the two can never disagree.
 */
@Composable
private fun SpectrumPanel(dashboard: DashboardState, onLive: () -> Unit) {
    val p = neuralPalette()
    val samples = dashboard.loadSamples
    val summary = remember(samples) { Spectrum.summary(samples) }
    val paired = remember(samples) { samples.any { it.gpu?.isFinite() == true } }
    NeuralPanel(onClick = onLive) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_spectrum_title),
            caption = stringResource(R.string.home_spectrum_caption),
            accent = p.accent
        )
        if (paired) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpectrumLegend("CPU", p.accent)
                SpectrumLegend("GPU", p.accentAlt)
            }
        }
        NeuralLoadRibbon(
            samples = samples,
            accent = p.accent,
            secondaryAccent = p.accentAlt,
            hot = p.danger,
            modifier = Modifier.fillMaxWidth().height(if (paired) 104.dp else 86.dp)
        )
        if (samples.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuralFactTile(
                    stringResource(R.string.home_stat_now),
                    "${dashboard.cpuLoadPercent}%",
                    p.accent,
                    Modifier.weight(1f)
                )
                NeuralFactTile(
                    stringResource(R.string.home_stat_avg),
                    "${summary.average}%",
                    p.accent,
                    Modifier.weight(1f)
                )
                NeuralFactTile(
                    stringResource(R.string.home_stat_peak),
                    "${summary.peak}%",
                    p.danger,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Whether a frequency ceiling can be stated at all.
 *
 * Three answers, not two: a ceiling, an explicit "this kernel declares none", and `null`
 * when the reading itself is missing — there is no point labelling the range of a clock we
 * never read. A ceiling is never inferred from readings we happened to see: the highest
 * value this app observed is not the chip's range, and a meter drawn against it would
 * measure our own sample history.
 */
@Composable
private fun frequencyCeilingLabel(currentMhz: Int?, ceilingMhz: Int?): String? {
    if (currentMhz == null || currentMhz <= 0) return null
    if (ceilingMhz == null || ceilingMhz <= 0) return stringResource(R.string.home_freq_ceiling_unknown)
    return stringResource(R.string.home_freq_ceiling, compactFrequency(ceilingMhz))
}

/** One dot plus the load it stands for, so the two lanes are never guessed at. */
@Composable
private fun SpectrumLegend(label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        NeuralCaption(label, color = accent)
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
