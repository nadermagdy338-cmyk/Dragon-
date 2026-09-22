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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
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
 *  0. storyboard— what your choices are actually doing right now: the last per-app
 *               session's verified hardware results, MAX AI's owned knobs, and your
 *               own manual locks. It states outcomes, never instruments: heat, load and
 *               cores keep living in the screens that own them.
 *  1. pulse   — device identity, heat, uptime, battery and power draw at a glance
 *  2. focus   — appears only when something is actually wrong
 *  4. verdict — the limiter, in one sentence, with recent events
 *  5. deck    — four destinations people actually reach for
 *
 * The measurement panels that duplicated owner screens (CPU/GPU load tiles, the
 * core matrix, RAM/swap/storage budgets) are gone from here per the storyboard plan's
 * phase 4: load lives in CPU and GPU, memory in ZRAM and Storage, cores and display in
 * their own screens. Nothing was lost — the same numbers are one tap away — and nothing
 * is drawn twice on this screen: heat, uptime, battery and power appear exactly once,
 * in the pulse block.
 *
 * No metric is drawn twice. The rule is enforced by subtraction, not by hope: heat is a
 * number in the pulse block and nowhere else, uptime/battery/power appear once, the engine
 * state is stated once (the pill, not a pill plus a subtitle), and the load sparklines that
 * used to sit here were the third drawing of the same series — their screens own them now.
 * And "nothing is lost" was verified, not assumed: uptime and power draw have no owner
 * screen yet, which is exactly why the pulse block kept them instead of dropping them.
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
        // بطاقة نشاط واحدة: تحكي الأثر المؤكد فقط، وتترك القياسات لشاشاتها المالكة.
        UnifiedActivityCard(maxAi = maxAi)
        PulsePanel(
            deviceName = deviceName,
            dashboard = dashboard,
            onOverview = { onNavigate(MaxDestination.Diagnostics.route) }
        )
        FocusCard(dashboard, onNavigate)
        VerdictPanel(
            dashboard = dashboard,
            maxAi = maxAi,
            request = profileRequest,
            onLive = { onNavigate(MaxDestination.MaxLive.route) },
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onRetry = onAiRetry
        )
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
