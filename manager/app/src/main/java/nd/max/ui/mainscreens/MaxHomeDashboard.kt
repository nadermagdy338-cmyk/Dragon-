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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
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
import nd.max.ui.component.NeuralDivider
import nd.max.ui.component.NeuralCoreGrid
import nd.max.ui.component.NeuralCoreReading
import nd.max.ui.component.NeuralGaugeCard
import nd.max.ui.component.NeuralIconButton
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralMetricTrendCard
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralReadoutTile
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MaxReveal(true, 0, Modifier.fillMaxWidth()) {
            HomeHeader(online = online, onSettings = onSettings, onReboot = onReboot)
        }
        MaxReveal(true, 35, Modifier.fillMaxWidth()) {
            DeviceHeroCard(
                dashboard = dashboard,
                deviceName = deviceName,
                measured = measured,
                onOverview = { onNavigate(MaxDestination.Diagnostics.route) },
            )
        }
        MaxReveal(true, 70, Modifier.fillMaxWidth()) {
            FocusCard(dashboard = dashboard, maxAi = maxAi, onNavigate = onNavigate)
        }
        MaxReveal(true, 105, Modifier.fillMaxWidth()) {
            ControlBand(
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
        MaxReveal(true, 140, Modifier.fillMaxWidth()) {
            ComputeDeck(
                dashboard = dashboard,
                measured = measured,
                onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
                onGpu = { onNavigate(MaxDestination.GpuStudio.route) },
            )
        }
        MaxReveal(true, 175, Modifier.fillMaxWidth()) {
            CoreMatrixPanel(
                dashboard = dashboard,
                onCores = { onNavigate(MaxDestination.CpuCoreControl.route) },
            )
        }
        MaxReveal(true, 210, Modifier.fillMaxWidth()) {
            MemoryDeck(
                dashboard = dashboard,
                onRam = { onNavigate(MaxDestination.MemoryHub.route) },
                onZram = { onNavigate(MaxDestination.ZramManager.route) },
            )
        }
        MaxReveal(true, 245, Modifier.fillMaxWidth()) {
            CapacityDeck(
                dashboard = dashboard,
                onStorage = { onNavigate(MaxDestination.StorageDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
            )
        }
        MaxReveal(true, 280, Modifier.fillMaxWidth()) {
            ThermalPanel(
                dashboard = dashboard,
                measured = measured,
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
        }
        MaxReveal(true, 315, Modifier.fillMaxWidth()) {
            PerformanceHistoryCard(dashboard)
        }
        MaxReveal(true, 350, Modifier.fillMaxWidth()) {
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
        MaxReveal(true, 385, Modifier.fillMaxWidth()) {
            UnifiedActivityCard(maxAi = maxAi)
        }
        MaxReveal(true, 420, Modifier.fillMaxWidth()) {
            CommandDeck(
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
                onApps = { onNavigate(MaxDestination.Apps.route) },
                onAdvanced = { onNavigate(MaxDestination.Control.route) },
                onSystemSettings = {
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                },
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
        Text(
            text = stringResource(R.string.max_brand_mark),
            color = p.text,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.width(10.dp))
        NeuralPill(
            text = stringResource(if (online) R.string.home_active else R.string.home_idle),
            accent = if (online) p.ok else p.danger,
            filled = true,
            dot = true,
        )
        Spacer(Modifier.weight(1f))
        HomeHeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), onReboot)
        Spacer(Modifier.width(8.dp))
        HomeHeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), onSettings)
    }
}

@Composable
private fun HomeHeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    NeuralIconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        modifier = Modifier.size(38.dp),
    )
}

@Composable
private fun DeviceHeroCard(
    dashboard: DashboardState,
    deviceName: String,
    measured: Boolean,
    onOverview: () -> Unit,
) {
    val p = neuralPalette()
    val heat = if (measured) {
        dashboard.batteryTempC.takeIf { it > 0f }?.let { it.toInt() } ?: dashboard.cpuTempC.takeIf { it > 0 }
    } else {
        null
    }
    val heatAccent = temperatureAccent(heat)
    val temp = heat?.let { "$it\u00b0C" } ?: "\u2014"
    val power = dashboard.powerWatt.takeIf { measured && it > 0f }?.let { "${it.oneDecimal()} W" } ?: "\u2014"
    val battery = dashboard.batteryPercent.takeIf { measured && it > 0 }?.let { "$it%" } ?: "\u2014"
    val uptime = dashboard.uptimeMinutes.takeIf { it > 0 }?.let(::compactUptime) ?: "\u2014"

    NeuralPanel(accent = heatAccent, verticalSpacing = 14.dp, contentPadding = PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            deviceName,
                            color = p.text,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            dashboard.chipsetName,
                            color = p.muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                NeuralPill(
                    text = when {
                        !measured -> stringResource(R.string.max_home_unavailable)
                        heat == null -> stringResource(R.string.home_system_stable)
                        heat >= 45 -> stringResource(R.string.home_system_attention)
                        else -> stringResource(R.string.home_system_stable)
                    },
                    accent = when {
                        !measured -> p.muted
                        heat != null && heat >= 45 -> p.danger
                        else -> p.ok
                    },
                    filled = true,
                    dot = true,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.home_temperature_short), color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(1.dp))
                NeuralValue(
                    temp,
                    style = MonoValueStyleSmall.copy(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black),
                    color = if (heat == null) p.muted else heatAccent,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralHeroStat(stringResource(R.string.home_power_draw), power, heatAccent, Modifier.weight(1f))
            NeuralHeroStat(stringResource(R.string.max_home_battery), battery, p.ok, Modifier.weight(1f))
            NeuralHeroStat(stringResource(R.string.max_home_uptime), uptime, p.muted, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
private fun NeuralHeroStat(label: String, value: String, accent: Color, modifier: Modifier) {
    NeuralReadoutTile(
        caption = label,
        value = value,
        accent = accent,
        modifier = modifier,
    )
}

@Composable
private fun FocusCard(dashboard: DashboardState, maxAi: MaxAiState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.toInt() ?: dashboard.cpuTempC
    val ram = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)
    val storageFree = if (dashboard.storageTotalGb <= 0f) 1f else
        ((dashboard.storageTotalGb - dashboard.storageUsedGb) / dashboard.storageTotalGb).coerceIn(0f, 1f)

    val title: String
    val body: String
    val accent: Color
    val icon: ImageVector
    val route: String
    when {
        maxAi.safety.engaged -> {
            title = stringResource(R.string.home_system_attention)
            body = maxAi.safety.lastReason.ifBlank { stringResource(R.string.max_home_state_thermal_desc) }
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            route = MaxDestination.ThermalDetail.route
        }
        heat >= 45 -> {
            title = stringResource(R.string.home_focus_heat)
            body = stringResource(R.string.home_focus_heat_desc)
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            route = MaxDestination.ThermalDetail.route
        }
        storageFree < .10f -> {
            title = stringResource(R.string.home_focus_storage)
            body = stringResource(R.string.home_focus_storage_desc)
            accent = p.warn
            icon = Icons.Rounded.Storage
            route = MaxDestination.StorageDetail.route
        }
        ram >= .90f -> {
            title = stringResource(R.string.home_focus_memory)
            body = stringResource(R.string.home_focus_memory_desc)
            accent = p.accentAlt
            icon = Icons.Rounded.Memory
            route = MaxDestination.ZramManager.route
        }
        else -> return
    }

    NeuralPanel(accent = accent, onClick = { onNavigate(route) }, verticalSpacing = 8.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(icon, accent, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                Text(body, color = p.muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.home_open_details), color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ControlBand(
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
    val aiAccent = if (maxAi.aiEnabled) p.accentAlt else p.muted
    val selectedReason = profileReasonFor(profileRes)
    val selectedIndex = PROFILE_CHOICES.indexOfFirst { it.reason == selectedReason }.coerceAtLeast(0)

    NeuralPanel(accent = p.accent, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.max_home_active_profile),
            caption = stringResource(R.string.max_home_profile_ready),
            accent = p.accent,
        )
        nd.max.ui.design.MaxSegmented(
            options = PROFILE_CHOICES.map { stringResource(it.labelRes) },
            selectedIndex = selectedIndex,
            enabled = manualProfileAllowed && !request.inFlight,
            onSelect = { index -> onProfile(PROFILE_CHOICES[index].reason) },
        )

        val statusText = when {
            request.inFlight -> stringResource(R.string.max_home_ai_working)
            request.result == DecisionResult.FAILED -> stringResource(R.string.max_home_ai_failed)
            !manualProfileAllowed && maxAi.aiEnabled -> stringResource(R.string.maxai_banner_active_title)
            !manualProfileAllowed -> stringResource(R.string.home_service_unready)
            else -> null
        }
        if (statusText != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(statusText, color = if (request.result == DecisionResult.FAILED) p.danger else p.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (request.result == DecisionResult.FAILED) {
                    NeuralPill(stringResource(R.string.max_home_retry), p.muted, onClick = onRetry)
                }
            }
        }

        NeuralDivider()
        NeuralSectionHeader(
            title = stringResource(R.string.max_nav_max_ai),
            caption = stringResource(R.string.max_home_smart_section_desc),
            accent = aiAccent,
            trailing = {
                NeuralPill(
                    text = stringResource(if (maxAi.aiEnabled) R.string.home_active else R.string.home_idle),
                    accent = if (maxAi.aiEnabled) p.ok else p.muted,
                    filled = true,
                    dot = true,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralValue(
                maxAi.objectivePreference ?: maxAi.strategyLabel.takeIf { it.isNotBlank() && maxAi.aiEnabled } ?: "\u2014",
                style = MonoValueStyleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = p.text,
            )
            Spacer(Modifier.weight(1f))
            if (maxAi.aiEnabled) {
                Text(stringResource(R.string.home_ai_confidence, maxAi.automationPlan.confidencePercent), color = aiAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            if (maxAi.aiEnabled) maxAi.automationPlan.reason else stringResource(R.string.max_home_ai_off_hint),
            color = p.muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(stringResource(R.string.maxai_banner_open), aiAccent, icon = Icons.Rounded.Bolt, onClick = onOpenAi)
            if (maxAi.aiEnabled) {
                Spacer(Modifier.width(8.dp))
                NeuralPill(stringResource(R.string.home_session_open_loop), p.accent, icon = Icons.Rounded.Timeline, onClick = onLive)
            }
        }
    }
}

@Composable
private fun ComputeDeck(
    dashboard: DashboardState,
    measured: Boolean,
    onCpu: () -> Unit,
    onGpu: () -> Unit,
) {
    val p = neuralPalette()
    val cpuValue = if (measured) "${dashboard.cpuLoadPercent.coerceIn(0, 100)}%" else "\u2014"
    val gpuValue = if (measured) dashboard.gpuLoadPercent?.let { "${it.coerceIn(0, 100)}%" } ?: "\u2014" else "\u2014"
    val cpuFreq = dashboard.cpuTopCoreMhz.takeIf { measured && it > 0 }?.let(::compactFrequency) ?: "\u2014"
    val gpuFreq = dashboard.gpuFreqMhz?.takeIf { measured && it > 0 }?.let(::compactFrequency) ?: "\u2014"
    val cpuSeries = dashboard.loadSamples.map { it.cpu }
    val gpuSeries = dashboard.loadSamples.map { it.gpu }
    val gpuAvailable = dashboard.gpuLoadPercent != null || dashboard.gpuFreqMhz != null || !measured

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun CoreMatrixPanel(dashboard: DashboardState, onCores: () -> Unit) {
    val p = neuralPalette()
    val unavailable = stringResource(R.string.max_home_unavailable)
    val readings = dashboard.cores.map { core ->
        NeuralCoreReading(
            id = "C${core.cpu}",
            frequency = if (core.online && core.freqMhz > 0) {
                compactFrequency(core.freqMhz)
            } else {
                unavailable
            },
            fraction = core.loadFraction,
            online = core.online,
        )
    }
    val online = dashboard.cores.count { it.online }

    NeuralPanel(accent = p.accentAlt, onClick = onCores, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_cpu_cores),
            caption = if (readings.isEmpty()) {
                null
            } else {
                stringResource(R.string.home_cpu_cores_online, online, readings.size)
            },
            accent = p.accentAlt,
        )
        if (readings.isEmpty()) {
            Text(
                stringResource(R.string.home_waiting_core_data),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        } else {
            NeuralCoreGrid(cores = readings, accent = p.accentAlt)
        }
    }
}

/**
 * الذاكرة: RAM بجانب ZRAM — وهما نفس السؤال بمقامين مختلفين.
 *
 * وZRAM تُخفى كاملةً حين لا يكون في النظام swap مُهيّأ (`swapTotalMb` = null): صفٌّ يقول
 * «ZRAM: ٠ / ٠» لجهاز لا يملكها يوهم بعطب. وفي غيابها يأخذ RAM العرض كلّه.
 */
@Composable
private fun MemoryDeck(dashboard: DashboardState, onRam: () -> Unit, onZram: () -> Unit) {
    val p = neuralPalette()
    val ramKnown = dashboard.ramTotalMb > 0
    val ramFraction = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)
    val ramAccent = when {
        !ramKnown -> p.muted
        ramFraction >= .90f -> p.danger
        ramFraction >= .75f -> p.warn
        else -> p.accent
    }
    val swapUsed = dashboard.swapUsedMb
    val swapTotal = dashboard.swapTotalMb
    val swapKnown = swapTotal != null && swapTotal > 0
    val swapFraction = if (swapKnown && swapUsed != null) {
        (swapUsed.toFloat() / swapTotal).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralGaugeCard(
            caption = stringResource(R.string.home_ram_tag),
            value = if (ramKnown) "${(ramFraction * 100).roundToInt()}%" else "\u2014",
            accent = ramAccent,
            modifier = Modifier.weight(1f),
            ringFraction = if (ramKnown) ramFraction else null,
            icon = Icons.Rounded.Storage,
            ringSize = 106.dp,
            onClick = onRam,
            support = if (ramKnown) {
                "${gigabytes(dashboard.ramUsedMb)} / ${gigabytes(dashboard.ramTotalMb)}"
            } else {
                stringResource(R.string.max_home_unavailable)
            },
        )
        if (swapKnown) {
            NeuralGaugeCard(
                caption = stringResource(R.string.home_zram_tag),
                value = "${(swapFraction * 100).roundToInt()}%",
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
                ringFraction = swapFraction,
                icon = Icons.Rounded.Memory,
                ringSize = 106.dp,
                onClick = onZram,
                support = if (swapUsed != null) {
                    "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}"
                } else {
                    stringResource(R.string.max_home_unavailable)
                },
            )
        }
    }
}

/**
 * السعة: التخزين بجانب البطارية — أكثر رقمين يفتح الناس تطبيق معلومات جهاز من أجلهما.
 *
 * وقاعدة الصدق هنا صارمة لأن الرقم مُغري بالتدوير: تخزين بلا مقام (`storageTotalGb` = ٠)
 * لا يُرسم قوسه، والبطارية عند ٠٪ تُعامَل «لا قراءة» لا «فارغة» — والجهاز الذي يعرض
 * الصفحة لا يكون بطاريته صفرًا.
 */
@Composable
private fun CapacityDeck(dashboard: DashboardState, onStorage: () -> Unit, onBattery: () -> Unit) {
    val p = neuralPalette()
    val storageKnown = dashboard.storageTotalGb > 0f
    val storageFreeGb = (dashboard.storageTotalGb - dashboard.storageUsedGb).coerceAtLeast(0f)
    val storageFraction = if (storageKnown) {
        (dashboard.storageUsedGb / dashboard.storageTotalGb).coerceIn(0f, 1f)
    } else {
        0f
    }
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

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralGaugeCard(
            caption = stringResource(R.string.max_home_storage),
            value = if (storageKnown) "${(storageFraction * 100).roundToInt()}%" else "\u2014",
            accent = storageAccent,
            modifier = Modifier.weight(1f),
            ringFraction = if (storageKnown) storageFraction else null,
            icon = Icons.Rounded.Storage,
            ringSize = 106.dp,
            onClick = onStorage,
            support = if (storageKnown) {
                stringResource(R.string.home_available_memory, "${storageFreeGb.oneDecimal()} GB")
            } else {
                stringResource(R.string.max_home_unavailable)
            },
        )
        NeuralGaugeCard(
            caption = stringResource(R.string.max_home_battery),
            value = if (batteryKnown) "${dashboard.batteryPercent}%" else "\u2014",
            accent = batteryAccent,
            modifier = Modifier.weight(1f),
            ringFraction = if (batteryKnown) dashboard.batteryPercent / 100f else null,
            icon = Icons.Rounded.BatteryChargingFull,
            ringSize = 106.dp,
            badge = if (dashboard.isCharging) stringResource(R.string.max_home_charging) else null,
            onClick = onBattery,
            // الاستهلاك هو السطر الطبيعي تحت نسبة الشحن (يُشحن أم يُسحب؟ وبقوّة كم؟)،
            // ولذلك لا يتكرّر في شبكة التفاصيل: قراءة واحدة في السياق الذي تُسأل فيه.
            // والصفر «لا قراءة» لا «صفر واط» (`current_now` صامت).
            support = if (dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W" else "\u2014",
        )
    }
}
