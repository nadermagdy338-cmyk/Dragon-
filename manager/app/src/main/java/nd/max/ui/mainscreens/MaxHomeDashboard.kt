package nd.max.ui.mainscreens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.MaxReveal
import nd.max.ui.component.NeuralCoreGrid
import nd.max.ui.component.NeuralGaugeCard
import nd.max.ui.component.NeuralIconButton
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralMetricTrendCard
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralReadoutTile
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxSegmented
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState

/** ملف يدوي قابل للتطبيق — الرقم هو العقد الذي يمر إلى طبقة التحكم. */
private data class ProfileChoice(val reason: String, val labelRes: Int)

private const val DEVICE_INFO_ACTION = "android.settings.DEVICE_INFO_SETTINGS"

private val PROFILE_CHOICES = listOf(
    ProfileChoice("1", R.string.Profile_Performance),
    ProfileChoice("2", R.string.Profile_Balanced),
    ProfileChoice("3", R.string.Profile_ECO_mode),
)

internal fun profileReasonFor(profileRes: Int): String? = when (profileRes) {
    R.string.Profile_Performance, R.string.profile_perflite -> "1"
    R.string.Profile_Balanced -> "2"
    R.string.Profile_ECO_mode -> "3"
    else -> null
}

@Composable
internal fun MaxHomeDashboard(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onProfile: (String) -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit,
) {
    val context = LocalContext.current
    val measured = dashboard.ready
    val online = ui.rootStatus && ui.moduleInstalled

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MaxReveal(true, 0, Modifier.fillMaxWidth()) {
            HomeHeader(online = online, onSettings = onSettings, onReboot = onReboot)
        }

        MaxReveal(true, 25, Modifier.fillMaxWidth()) {
            DeviceHeroCard(
                dashboard = dashboard,
                deviceName = deviceName,
                measured = measured,
                online = online,
                maxAi = maxAi,
                profileRes = ui.currentProfileRes,
                onOverview = { onNavigate(MaxDestination.Diagnostics.route) },
            )
        }

        AttentionStrip(
            dashboard = dashboard,
            maxAi = maxAi,
            measured = measured,
            onNavigate = onNavigate,
        )

        MaxReveal(true, 55, Modifier.fillMaxWidth()) {
            ModeControlStrip(
                profileRes = ui.currentProfileRes,
                manualProfileAllowed = ui.autoMode == "0",
                maxAi = maxAi,
                request = profileRequest,
                onProfile = onProfile,
                onOpenAi = { onNavigate(MaxDestination.MaxAi.route) },
                onLive = { onNavigate(MaxDestination.MaxLive.route) },
                onRetry = onAiRetry,
            )
        }

        MaxReveal(true, 80, Modifier.fillMaxWidth()) {
            PerformanceDeck(
                dashboard = dashboard,
                measured = measured,
                onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
                onGpu = { onNavigate(MaxDestination.GpuStudio.route) },
            )
        }

        MaxReveal(true, 105, Modifier.fillMaxWidth()) {
            CoreMatrixPanel(
                dashboard = dashboard,
                onCores = { onNavigate(MaxDestination.CpuCoreControl.route) },
            )
        }

        MaxReveal(true, 130, Modifier.fillMaxWidth()) {
            ResourceDeck(
                dashboard = dashboard,
                onRam = { onNavigate(MaxDestination.MemoryHub.route) },
                onZram = { onNavigate(MaxDestination.ZramManager.route) },
                onStorage = { onNavigate(MaxDestination.StorageDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
            )
        }

        MaxReveal(true, 155, Modifier.fillMaxWidth()) {
            ThermalPanel(
                dashboard = dashboard,
                measured = measured,
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
        }

        MaxReveal(true, 180, Modifier.fillMaxWidth()) {
            PerformanceHistoryCard(dashboard)
        }

        MaxReveal(true, 205, Modifier.fillMaxWidth()) {
            DeviceDetailsPanel(
                dashboard = dashboard,
                measured = measured,
                onDisplay = { onNavigate(MaxDestination.DisplayStudio.route) },
                onNetwork = { onNavigate(MaxDestination.NetworkDetail.route) },
                onPower = { onNavigate(MaxDestination.Charging.route) },
                onDeviceInfo = {
                    runCatching { context.startActivity(Intent(DEVICE_INFO_ACTION)) }.onFailure {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                    }
                },
            )
        }

        MaxReveal(true, 230, Modifier.fillMaxWidth()) {
            UnifiedActivityCard(maxAi = maxAi)
        }

        MaxReveal(true, 255, Modifier.fillMaxWidth()) {
            CommandDeck(
                onApps = { onNavigate(MaxDestination.Apps.route) },
                onControl = { onNavigate(MaxDestination.Control.route) },
                onAi = { onNavigate(MaxDestination.MaxAi.route) },
                onDiagnostics = { onNavigate(MaxDestination.Diagnostics.route) },
            )
        }
    }
}

@Composable
private fun HomeHeader(
    online: Boolean,
    onSettings: () -> Unit,
    onReboot: () -> Unit,
) {
    val p = neuralPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(R.string.max_brand_mark),
                color = p.text,
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = stringResource(R.string.max_home_dashboard_eyebrow),
                color = p.muted,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(10.dp))
        NeuralPill(
            text = stringResource(if (online) R.string.home_active else R.string.home_idle),
            accent = if (online) p.ok else p.danger,
            filled = true,
            dot = true,
        )
        Spacer(Modifier.weight(1f))
        HomeHeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), onReboot)
        Spacer(Modifier.width(6.dp))
        HomeHeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), onSettings)
    }
}

@Composable
private fun HomeHeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    NeuralIconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    )
}

@Composable
private fun DeviceHeroCard(
    dashboard: DashboardState,
    deviceName: String,
    measured: Boolean,
    online: Boolean,
    maxAi: MaxAiState,
    profileRes: Int,
    onOverview: () -> Unit,
) {
    val p = neuralPalette()
    val heat = if (measured) {
        dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
            ?: dashboard.cpuTempC.takeIf { it > 0 }
    } else null
    val accent = if (heat == null) p.accent else temperatureAccent(heat)
    val temp = heat?.let { "$it°C" } ?: "—"
    val statusAccent = when {
        !online -> p.danger
        !measured -> p.muted
        maxAi.safety.engaged -> p.danger
        heat != null && heat >= 45 -> p.danger
        else -> p.ok
    }
    val statusLabel = when {
        !online -> stringResource(R.string.max_home_engine_attention)
        !measured -> stringResource(R.string.max_home_unavailable)
        maxAi.safety.engaged -> stringResource(R.string.maxai_safety_engaged)
        heat != null && heat >= 45 -> stringResource(R.string.home_system_attention)
        else -> stringResource(R.string.home_system_stable)
    }
    val profileLabel = stringResource(PROFILE_CHOICES.firstOrNull { it.reason == profileReasonFor(profileRes) }?.labelRes ?: R.string.Profile_Balanced)

    NeuralPanel(
        accent = accent,
        contentPadding = PaddingValues(16.dp),
        verticalSpacing = 11.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            deviceName,
                            color = p.text,
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            dashboard.chipsetName,
                            color = p.muted,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeuralPill(statusLabel, statusAccent, filled = true, dot = true)
                    NeuralPill(profileLabel, p.accent, icon = Icons.Rounded.Tune)
                    if (maxAi.aiEnabled) {
                        NeuralPill(stringResource(R.string.max_home_ai_on), p.accentAlt, filled = true, dot = true)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.home_temperature_short),
                    color = p.muted,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                NeuralValue(
                    temp,
                    style = MonoValueStyleSmall.copy(fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black),
                    color = if (heat == null) p.muted else accent,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HeroMiniStat(
                label = stringResource(R.string.max_home_power),
                value = if (measured && dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W" else "—",
                accent = accent,
                modifier = Modifier.weight(1f),
            )
            HeroMiniStat(
                label = stringResource(R.string.max_home_uptime),
                value = dashboard.uptimeMinutes.takeIf { it > 0 }?.let(::compactUptime) ?: "—",
                accent = p.muted,
                modifier = Modifier.weight(1f),
            )
            HeroMiniStat(
                label = stringResource(R.string.home_current_refresh),
                value = dashboard.displayRefreshHz.takeIf { it > 0 }?.let { "$it Hz" } ?: "—",
                accent = p.muted,
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_engine_status),
                color = p.muted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            NeuralPill(
                text = stringResource(R.string.home_device_overview),
                accent = p.accent,
                icon = Icons.Rounded.PhoneAndroid,
                onClick = onOverview,
            )
        }
    }
}

@Composable
private fun HeroMiniStat(label: String, value: String, accent: Color, modifier: Modifier) {
    NeuralReadoutTile(
        caption = label,
        value = value,
        accent = accent,
        modifier = modifier,
    )
}

@Composable
private fun AttentionStrip(
    dashboard: DashboardState,
    maxAi: MaxAiState,
    measured: Boolean,
    onNavigate: (String) -> Unit,
) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt() ?: dashboard.cpuTempC
    val storageFree = if (dashboard.storageTotalGb > 0f) {
        ((dashboard.storageTotalGb - dashboard.storageUsedGb) / dashboard.storageTotalGb).coerceIn(0f, 1f)
    } else 1f
    val ram = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)

    val route: String
    val accent: Color
    val icon: ImageVector
    val message: String
    when {
        maxAi.safety.engaged -> {
            route = MaxDestination.ThermalDetail.route
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            message = maxAi.safety.lastReason.ifBlank { stringResource(R.string.max_home_state_thermal_desc) }
        }
        measured && heat >= 45 -> {
            route = MaxDestination.ThermalDetail.route
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            message = stringResource(R.string.home_focus_heat_desc)
        }
        measured && storageFree < .10f -> {
            route = MaxDestination.StorageDetail.route
            accent = p.warn
            icon = Icons.Rounded.Memory
            message = stringResource(R.string.home_focus_storage_desc)
        }
        measured && ram >= .90f -> {
            route = MaxDestination.MemoryHub.route
            accent = p.accentAlt
            icon = Icons.Rounded.Memory
            message = stringResource(R.string.home_focus_memory_desc)
        }
        else -> return
    }

    NeuralPanel(
        accent = accent,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
        verticalSpacing = 5.dp,
        onClick = { onNavigate(route) },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(icon, accent, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                color = p.text,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.home_open_details),
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ModeControlStrip(
    profileRes: Int,
    manualProfileAllowed: Boolean,
    maxAi: MaxAiState,
    request: ProfileRequestState,
    onProfile: (String) -> Unit,
    onOpenAi: () -> Unit,
    onLive: () -> Unit,
    onRetry: () -> Unit,
) {
    val p = neuralPalette()
    val selectedReason = profileReasonFor(profileRes)
    val selectedIndex = PROFILE_CHOICES.indexOfFirst { it.reason == selectedReason }.coerceAtLeast(0)
    val status = when {
        request.inFlight -> stringResource(R.string.max_home_ai_working)
        request.result == DecisionResult.FAILED -> stringResource(R.string.max_home_ai_failed)
        maxAi.aiEnabled -> stringResource(R.string.maxai_banner_active_title)
        else -> stringResource(R.string.max_home_ai_off_hint)
    }
    val statusAccent = when {
        request.result == DecisionResult.FAILED -> p.danger
        request.inFlight -> p.warn
        maxAi.aiEnabled -> p.accentAlt
        else -> p.muted
    }

    NeuralPanel(
        accent = p.accent,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalSpacing = 9.dp,
    ) {
        NeuralSectionHeader(
            title = stringResource(R.string.max_home_control_center),
            caption = stringResource(R.string.max_home_control_desc),
            accent = p.accent,
            trailing = {
                NeuralPill(
                    text = stringResource(if (manualProfileAllowed) R.string.home_active else R.string.home_protected),
                    accent = if (manualProfileAllowed) p.ok else p.accentAlt,
                    dot = true,
                )
            },
        )

        MaxSegmented(
            options = PROFILE_CHOICES.map { stringResource(it.labelRes) },
            selectedIndex = selectedIndex,
            enabled = manualProfileAllowed && !request.inFlight,
            onSelect = { onProfile(PROFILE_CHOICES[it].reason) },
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                status,
                color = statusAccent,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (request.result == DecisionResult.FAILED) {
                NeuralPill(stringResource(R.string.max_home_retry), p.muted, onClick = onRetry)
            } else {
                NeuralPill(stringResource(R.string.maxai_banner_open), statusAccent, icon = Icons.Rounded.Bolt, onClick = onOpenAi)
                if (maxAi.aiEnabled) {
                    Spacer(Modifier.width(6.dp))
                    NeuralPill(stringResource(R.string.home_session_open_loop), p.accent, onClick = onLive)
                }
            }
        }
    }
}

@Composable
private fun PerformanceDeck(
    dashboard: DashboardState,
    measured: Boolean,
    onCpu: () -> Unit,
    onGpu: () -> Unit,
) {
    val p = neuralPalette()
    val cpuValue = if (measured) "${dashboard.cpuLoadPercent.coerceIn(0, 100)}%" else "—"
    val gpuValue = if (measured) dashboard.gpuLoadPercent?.let { "${it.coerceIn(0, 100)}%" } ?: "—" else "—"
    val cpuFreq = dashboard.cpuTopCoreMhz.takeIf { measured && it > 0 }?.let(::compactFrequency) ?: "—"
    val gpuFreq = dashboard.gpuFreqMhz?.takeIf { measured && it > 0 }?.let(::compactFrequency) ?: "—"
    val cpuSeries = dashboard.loadSamples.map { it.cpu }
    val gpuSeries = dashboard.loadSamples.map { it.gpu }
    val gpuAvailable = dashboard.gpuLoadPercent != null || dashboard.gpuFreqMhz != null || !measured

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_live_performance),
            caption = stringResource(R.string.home_live_performance_desc),
            accent = p.accent,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralMetricTrendCard(
                title = stringResource(R.string.home_cpu_tag),
                value = cpuValue,
                secondaryValue = cpuFreq,
                accent = p.accent,
                series = cpuSeries,
                modifier = Modifier.weight(1f),
                onClick = onCpu,
            )
            if (gpuAvailable) {
                NeuralMetricTrendCard(
                    title = stringResource(R.string.home_gpu_tag),
                    value = gpuValue,
                    secondaryValue = gpuFreq,
                    accent = p.accentAlt,
                    series = gpuSeries,
                    modifier = Modifier.weight(1f),
                    onClick = onGpu,
                )
            }
        }
    }
}

@Composable
private fun CoreMatrixPanel(dashboard: DashboardState, onCores: () -> Unit) {
    val p = neuralPalette()
    val unavailable = stringResource(R.string.max_home_unavailable)
    val readings = dashboard.cores.map { core ->
        nd.max.ui.component.NeuralCoreReading(
            id = "C${core.cpu}",
            frequency = if (core.online && core.freqMhz > 0) compactFrequency(core.freqMhz) else unavailable,
            fraction = core.loadFraction,
            online = core.online,
        )
    }
    val online = dashboard.cores.count { it.online }

    NeuralPanel(accent = p.accentAlt, onClick = onCores, contentPadding = PaddingValues(14.dp), verticalSpacing = 9.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_cpu_cores),
            caption = if (readings.isEmpty()) null else stringResource(R.string.home_cpu_cores_online, online, readings.size),
            accent = p.accentAlt,
            trailing = { NeuralPill(stringResource(R.string.home_details), p.muted) },
        )
        if (readings.isEmpty()) {
            Text(
                stringResource(R.string.home_waiting_core_data),
                color = p.muted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
            )
        } else {
            NeuralCoreGrid(cores = readings, accent = p.accentAlt, perRow = 4, barHeight = 38.dp)
        }
    }
}

@Composable
private fun ResourceDeck(
    dashboard: DashboardState,
    onRam: () -> Unit,
    onZram: () -> Unit,
    onStorage: () -> Unit,
    onBattery: () -> Unit,
) {
    val p = neuralPalette()
    val ramKnown = dashboard.ramTotalMb > 0
    val ramFraction = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)
    val ramAccent = when {
        !ramKnown -> p.muted
        ramFraction >= .90f -> p.danger
        ramFraction >= .75f -> p.warn
        else -> p.accent
    }
    val swapTotal = dashboard.swapTotalMb
    val swapUsed = dashboard.swapUsedMb
    val swapKnown = swapTotal != null && swapTotal > 0
    val swapFraction = if (swapKnown && swapUsed != null) (swapUsed.toFloat() / swapTotal).coerceIn(0f, 1f) else 0f
    val storageKnown = dashboard.storageTotalGb > 0f
    val storageFreeGb = (dashboard.storageTotalGb - dashboard.storageUsedGb).coerceAtLeast(0f)
    val storageFraction = if (storageKnown) (dashboard.storageUsedGb / dashboard.storageTotalGb).coerceIn(0f, 1f) else 0f
    val storageAccent = when {
        !storageKnown -> p.muted
        storageFreeGb / dashboard.storageTotalGb < .10f -> p.danger
        storageFreeGb / dashboard.storageTotalGb < .20f -> p.warn
        else -> p.accent
    }
    val batteryKnown = dashboard.batteryPercent > 0
    val batteryAccent = when {
        !batteryKnown -> p.muted
        dashboard.isCharging -> p.ok
        dashboard.batteryPercent <= 20 -> p.danger
        dashboard.batteryPercent <= 40 -> p.warn
        else -> p.ok
    }

    NeuralSectionHeader(
        title = stringResource(R.string.max_home_resources),
        caption = stringResource(R.string.max_home_resources_desc),
        accent = p.accentAlt,
        modifier = Modifier.padding(horizontal = 3.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralGaugeCard(
                caption = stringResource(R.string.home_ram_tag),
                value = if (ramKnown) "${(ramFraction * 100).roundToInt()}%" else "—",
                accent = ramAccent,
                modifier = Modifier.weight(1f),
                ringFraction = if (ramKnown) ramFraction else null,
                support = if (ramKnown) "${gigabytes(dashboard.ramUsedMb)} / ${gigabytes(dashboard.ramTotalMb)}" else stringResource(R.string.max_home_unavailable),
                onClick = onRam,
                ringSize = 88.dp,
            )
            if (swapKnown) {
                NeuralGaugeCard(
                    caption = stringResource(R.string.home_zram_tag),
                    value = "${(swapFraction * 100).roundToInt()}%",
                    accent = p.accentAlt,
                    modifier = Modifier.weight(1f),
                    ringFraction = swapFraction,
                    support = if (swapUsed != null) "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}" else stringResource(R.string.max_home_unavailable),
                    onClick = onZram,
                    ringSize = 88.dp,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralGaugeCard(
                caption = stringResource(R.string.max_home_storage),
                value = if (storageKnown) "${(storageFraction * 100).roundToInt()}%" else "—",
                accent = storageAccent,
                modifier = Modifier.weight(1f),
                ringFraction = if (storageKnown) storageFraction else null,
                support = if (storageKnown) stringResource(R.string.home_available_memory, "${storageFreeGb.oneDecimal()} GB") else stringResource(R.string.max_home_unavailable),
                onClick = onStorage,
                ringSize = 88.dp,
            )
            NeuralGaugeCard(
                caption = stringResource(R.string.max_home_battery),
                value = if (batteryKnown) "${dashboard.batteryPercent}%" else "—",
                accent = batteryAccent,
                modifier = Modifier.weight(1f),
                ringFraction = if (batteryKnown) dashboard.batteryPercent / 100f else null,
                badge = if (dashboard.isCharging) stringResource(R.string.max_home_charging) else null,
                support = if (dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W" else stringResource(R.string.max_home_unavailable),
                onClick = onBattery,
                ringSize = 88.dp,
            )
        }
    }
}
