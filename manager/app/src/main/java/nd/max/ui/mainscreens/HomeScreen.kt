/*
 * MaxManager Home — the command center. Information is intentionally
 * hierarchical: status first, live performance second, actions third.
 *
 * v2 — the hero card is now a true instrument: a live radial CPU-load gauge
 * sits beside the frequency readout, the sparkline reads as a scoped signal
 * with an area fill, and every stat lives in its own machined chip.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import android.content.Context
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.R
import nd.max.ui.component.StudioPerformanceHero
import nd.max.ui.component.StudioShortcut
import nd.max.ui.component.StudioAdaptivePair
import nd.max.ui.component.MaxSnackbarHost
import nd.max.ui.component.MaxMetric
import nd.max.ui.component.MaxDecisionCard
import nd.max.ui.component.MaxMotion
import nd.max.ui.component.MaxReveal
import nd.max.ui.component.MaxSectionHeader
import nd.max.ui.component.MaxSparkline
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.component.MaxSurface
import nd.max.ui.component.ProfileDialog
import nd.max.ui.component.RebootBottomSheet
import nd.max.ui.component.RootAppDialog
import nd.max.ui.util.getRealDeviceName
import nd.max.ui.viewmodel.HomeDashboardViewModel
import nd.max.ui.viewmodel.HomeViewModel
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    dashVM: HomeDashboardViewModel = viewModel(),
    maxAiVM: nd.max.ui.viewmodel.MaxAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui by viewModel.uiState.collectAsState()
    val dash by dashVM.dashboardState.collectAsState()
    val maxAi by maxAiVM.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showReboot by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val online = ui.rootStatus && ui.moduleInstalled
    val deviceName = remember(context) { getRealDeviceName(context) }
    val ramPercent = if (dash.ramTotalMb > 0) (dash.ramUsedMb * 100 / dash.ramTotalMb).coerceIn(0, 100) else 0
    val batteryTempC = primaryBatteryTemperatureC(dash)
    val batteryTempWarning = batteryTempC?.let { it >= 45f } == true
    val batteryTempHigh = batteryTempC?.let { it >= 50f } == true
    val tempText = batteryTempC?.let { "${it.formatOne()}°C" } ?: "—"
    val gpuText = when {
        dash.chipsetName.isBlank() || dash.chipsetName == "..." -> "—"
        dash.chipsetName.contains("Mali", true) -> "Mali GPU"
        else -> if (dash.chipsetName.contains("MediaTek", true) || dash.chipsetName.contains("Dimensity", true)) "Mali GPU" else if (dash.chipsetName.contains("Snapdragon", true)) "Adreno GPU" else "GPU"
    }
    val profileText = context.getString(ui.currentProfileRes)
    val activeApp = ui.runningGamePkg?.takeIf { it.isNotBlank() }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { MaxSnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.maxAdaptiveContentWidth(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 124.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MaxReveal(delayMillis = 0) {
                    HomeHeader(
                    online = online,
                    onSettings = { navController.navigate("settings") },
                    onPower = { showReboot = true }
                )
                }
            }

            item {
                MaxReveal(delayMillis = 45) {
                    StudioPerformanceHero(
                    online = online,
                    deviceName = deviceName,
                    profile = profileText,
                    profileEnabled = ui.autoMode == "0",
                    cpuLoad = dash.cpuLoadPercent,
                    cpuFreq = dash.cpuFreqMhz,
                    temperature = tempText,
                    activeApp = activeApp,
                    history = dash.cpuLoadHistory,
                    onProfile = { if (ui.autoMode == "0") showProfile = true },
                    onApps = { navController.navigate("applist") }
                )
                }
            }

            item {
                MaxReveal(delayMillis = 72) {
                    val state = when {
                        !online -> stringResource(R.string.max_home_state_engine_offline)
                        batteryTempHigh -> stringResource(R.string.max_home_state_thermal)
                        dash.cpuLoadPercent >= 90 -> stringResource(R.string.max_home_state_high_load)
                        dash.isCharging -> stringResource(R.string.max_home_state_charging)
                        else -> stringResource(R.string.max_home_state_balanced)
                    }
                    val guidance = when {
                        !online -> stringResource(R.string.max_home_state_engine_offline_desc)
                        batteryTempHigh -> stringResource(R.string.max_home_state_thermal_desc)
                        dash.cpuLoadPercent >= 90 -> stringResource(R.string.max_home_state_high_load_desc)
                        dash.isCharging -> stringResource(R.string.max_home_state_charging_desc)
                        else -> stringResource(R.string.max_home_state_balanced_desc)
                    }
                    MaxDecisionCard(
                        state = state,
                        guidance = guidance,
                        title = stringResource(R.string.max_home_current_state),
                        icon = if (!online || batteryTempHigh) Icons.Rounded.Thermostat else Icons.Rounded.Speed,
                        accent = if (!online || batteryTempHigh) colors.error else colors.tertiary
                    )
                }
            }

            item {
                MaxReveal(delayMillis = 80) {
                    MaxSectionHeader(
                    title = stringResource(R.string.max_home_live_hardware),
                    subtitle = stringResource(R.string.max_home_hardware_desc),
                    accent = colors.primary
                )
                }
            }

            item {
                MaxReveal(delayMillis = 115) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioAdaptivePair {
                        MaxMetric(
                            label = "CPU",
                            value = if (dash.cpuFreqMhz > 0) dash.cpuFreqMhz.toString() else "—",
                            unit = if (dash.cpuFreqMhz > 0) "MHz" else "",
                            icon = Icons.Rounded.Memory,
                            accent = colors.primary,
                            modifier = Modifier.weight(1f),
                            supporting = "${dash.cpuLoadPercent}% load",
                            onClick = { navController.navigate("cpucorecontrol") }
                        )
                        MaxMetric(
                            label = "GPU",
                            value = gpuText,
                            icon = Icons.Rounded.DeveloperBoard,
                            accent = colors.tertiary,
                            modifier = Modifier.weight(1f),
                            supporting = stringResource(R.string.max_home_graphics),
                            onClick = { navController.navigate("maligpufreq") }
                        )
                    }
                    StudioAdaptivePair {
                        MaxMetric(
                            label = stringResource(R.string.max_home_temp),
                            value = tempText,
                            icon = Icons.Rounded.Thermostat,
                            accent = if (batteryTempWarning) colors.error else colors.secondary,
                            modifier = Modifier.weight(1f),
                            supporting = stringResource(R.string.max_home_battery_sensor),
                            onClick = { navController.navigate("battery_detail") }
                        )
                        MaxMetric(
                            label = stringResource(R.string.studio_display),
                            value = if (dash.displayRefreshHz > 0) dash.displayRefreshHz.toString() else "—",
                            unit = if (dash.displayRefreshHz > 0) "Hz" else "",
                            icon = Icons.Rounded.DisplaySettings,
                            accent = colors.primary,
                            modifier = Modifier.weight(1f),
                            supporting = if (dash.displayWidth > 0) "${dash.displayWidth} × ${dash.displayHeight}" else "display",
                            onClick = { navController.navigate("displaystudio") }
                        )
                    }
                }
                }
            }

            item {
                MaxReveal(delayMillis = 150) {
                    MaxSectionHeader(
                    title = stringResource(R.string.max_home_control_center),
                    subtitle = stringResource(R.string.max_home_control_desc),
                    accent = colors.tertiary
                )
                }
            }

            item {
                MaxReveal(delayMillis = 185) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioAdaptivePair {
                            StudioShortcut(
                                title = stringResource(R.string.max_home_cpu_control),
                                subtitle = stringResource(R.string.max_home_cpu_control_desc),
                                icon = Icons.Rounded.Tune,
                                accent = colors.primary,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("governorsettings") }
                            )
                            StudioShortcut(
                                title = stringResource(R.string.max_home_gpu_control),
                                subtitle = stringResource(R.string.max_home_gpu_control_desc),
                                icon = Icons.Rounded.DeveloperBoard,
                                accent = colors.tertiary,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("maligpufreq") }
                            )
                        }
                        StudioAdaptivePair {
                            StudioShortcut(
                                title = stringResource(R.string.max_home_display_control),
                                subtitle = stringResource(R.string.max_home_display_control_desc),
                                icon = Icons.Rounded.DisplaySettings,
                                accent = colors.secondary,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("displaystudio") }
                            )
                            StudioShortcut(
                                title = stringResource(R.string.max_home_network_control),
                                subtitle = stringResource(R.string.max_home_network_control_desc),
                                icon = Icons.Rounded.NetworkCheck,
                                accent = colors.primary,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("networkscheduler") }
                            )
                        }
                    }
                }
            }

            // ── محرك MAX AI الموحد ─────────────────────────────────────
            // حلّ محل شاشات التنبؤ/التوصيات/التعلم/لوحة القيادة المتفرقة:
            // مدخل واحد يعرض حالة المحرك الحقيقية وأعداده الفعلية.
            item {
                MaxReveal(delayMillis = 220) {
                    MaxSectionHeader(
                    title = stringResource(R.string.max_home_smart_section),
                    subtitle = stringResource(R.string.max_home_smart_section_desc),
                    accent = colors.tertiary
                )
                }
            }

            item {
                MaxReveal(delayMillis = 255) {
                    MaxAiHomeCard(
                        aiEnabled = maxAi.aiEnabled,
                        controllerLabel = maxAiControllerLabel(maxAi.controller),
                        lastActionLabel = maxAi.lastDecision?.label,
                        onOpen = { navController.navigate("maxai") }
                    )
                }
            }

            item {
                MaxReveal(delayMillis = 290) {
                    MaxSectionHeader(
                    title = stringResource(R.string.max_home_resources),
                    subtitle = stringResource(R.string.max_home_resources_desc),
                    accent = colors.secondary
                )
                }
            }

            item {
                MaxReveal(delayMillis = 325) {
                    ResourcePanel(
                    ramPercent = ramPercent,
                    ramText = if (dash.ramTotalMb > 0) "${(dash.ramUsedMb / 1024f).formatOne()} / ${(dash.ramTotalMb / 1024f).formatOne()} GB" else "—",
                    storagePercent = if (dash.storageTotalGb > 0) (dash.storageUsedGb / dash.storageTotalGb * 100).toInt() else 0,
                    storageText = if (dash.storageTotalGb > 0) "${dash.storageUsedGb.formatOne()} / ${dash.storageTotalGb.formatOne()} GB" else "—",
                    battery = dash.batteryPercent,
                    charging = dash.isCharging,
                    onStorage = { navController.navigate("storage_detail") },
                    onBattery = { navController.navigate("battery_detail") }
                )
                }
            }

            item {
                MaxReveal(delayMillis = 360) {
                    MaxSurface(accent = if (online) colors.tertiary else colors.error) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(13.dp), color = (if (online) colors.tertiary else colors.error).copy(alpha = 0.12f)) {
                            Icon(Icons.Rounded.Security, null, tint = if (online) colors.tertiary else colors.error, modifier = Modifier.padding(9.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.max_home_engine), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (online) stringResource(R.string.max_home_engine_ready) else stringResource(R.string.max_home_engine_attention_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        MaxStatusPill(if (online) stringResource(R.string.max_home_live) else stringResource(R.string.max_home_offline), online, if (online) colors.tertiary else colors.error)
                    }
                }
                }
            }
        }
    }

    RootAppDialog {
        RebootBottomSheet(show = showReboot, onDismiss = { showReboot = false }, onReboot = { viewModel.rebootDevice(it) })
    }
    RootAppDialog {
        ProfileDialog(
            show = showProfile,
            onDismiss = { showProfile = false },
            onProfile = { reason ->
                viewModel.applyProfile(reason) { appliedNow ->
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(
                                if (appliedNow) R.string.toast_applying_profile
                                else R.string.toast_profile_pending
                            )
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeHeader(online: Boolean, onSettings: () -> Unit, onPower: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Brand mark: the wordmark gains a small accent node, echoing
                // the circuit-trace motif used across section headers.
                Text("MAX", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = colors.primary, letterSpacing = 2.sp)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.primary, colors.tertiary)
                            )
                        )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (online) colors.tertiary else colors.error))
                Spacer(Modifier.width(7.dp))
                Text(if (online) stringResource(R.string.max_home_engine_online) else stringResource(R.string.max_home_engine_attention), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(15.dp), color = colors.surfaceContainerHigh, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .15f))) {
            IconButton(onClick = onPower) { Icon(Icons.Rounded.PowerSettingsNew, stringResource(R.string.studio_power)) }
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(15.dp), color = colors.surfaceContainerHigh, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .15f))) {
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.studio_settings)) }
        }
    }
}

@Composable
private fun ResourcePanel(
    ramPercent: Int,
    ramText: String,
    storagePercent: Int,
    storageText: String,
    battery: Int,
    charging: Boolean,
    onStorage: () -> Unit,
    onBattery: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    MaxSurface {
        ResourceLine(stringResource(R.string.max_home_memory), ramText, ramPercent, Icons.Rounded.Memory, colors.primary)
        Spacer(Modifier.height(15.dp))
        ResourceLine(stringResource(R.string.max_home_storage), storageText, storagePercent, Icons.Rounded.Storage, colors.secondary, onStorage)
        Spacer(Modifier.height(15.dp))
        ResourceLine(stringResource(R.string.max_home_battery), "$battery%${if (charging) " · ${stringResource(R.string.max_home_charging)}" else ""}", battery.coerceIn(0, 100), Icons.Rounded.BatteryChargingFull, colors.tertiary, onBattery)
    }
}

@Composable
private fun ResourceLine(label: String, value: String, percent: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: (() -> Unit)? = null) {
    val animatedPercent by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100)) / 100f,
        animationSpec = MaxMotion.needleSpring,
        label = "resourcePercent"
    )
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.heightIn(min = 56.dp).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(9.dp), color = accent.copy(alpha = 0.12f)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(5.dp).size(13.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.labelMedium, color = accent)
            }
            if (onClick != null) {
                Icon(Icons.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedPercent },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = accent,
            trackColor = accent.copy(alpha = .10f)
        )
    }
}

private fun Float.formatOne(): String = String.format(java.util.Locale.US, "%.1f", this)

/**
 * بطاقة Max AI الحيّة في الرئيسية — تعرض حالة المحرك الحقيقية بدل
 * اختصار أعمى: مفعّل/مطفأ، المتحكم الحالي، وآخر إجراء فعلي. تربط
 * الرئيسية بالمحرك في نظرة واحدة، والضغط يفتح شاشة الإدارة الكاملة.
 */
@Composable
private fun MaxAiHomeCard(
    aiEnabled: Boolean,
    controllerLabel: String,
    lastActionLabel: String?,
    onOpen: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (aiEnabled) colors.tertiary else colors.onSurfaceVariant
    MaxSurface(modifier = Modifier.fillMaxWidth(), accent = if (aiEnabled) colors.tertiary else null, onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = accent.copy(alpha = 0.12f)) {
                Icon(Icons.Rounded.Psychology, null, tint = accent, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.maxai_master_switch),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            MaxStatusPill(
                if (aiEnabled) stringResource(R.string.max_home_ai_on) else stringResource(R.string.max_home_ai_off),
                aiEnabled,
                accent
            )
        }
        Spacer(Modifier.height(12.dp))
        // سطر المتحكم: من يتحكم بالأداء الآن فعلًا (يدوي/Max AI/ملف تطبيق/أمان).
        Text(controllerLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.height(3.dp))
        Text(
            if (aiEnabled && lastActionLabel != null)
                "${stringResource(R.string.max_home_ai_last)}: $lastActionLabel"
            else if (aiEnabled)
                stringResource(R.string.max_home_smart_engine_desc)
            else
                stringResource(R.string.max_home_ai_off_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

/** يحوّل المتحكم الحالي إلى نص معروض — نفس مفردات شاشة Max AI (اتساق). */
@Composable
private fun maxAiControllerLabel(controller: nd.max.core.maxai.MaxAiController): String = stringResource(
    when (controller) {
        nd.max.core.maxai.MaxAiController.MANUAL -> R.string.maxai_controller_manual
        nd.max.core.maxai.MaxAiController.MAX_AI -> R.string.maxai_controller_ai
        nd.max.core.maxai.MaxAiController.APP_PROFILE -> R.string.maxai_controller_app
        nd.max.core.maxai.MaxAiController.SAFETY_OVERRIDE -> R.string.maxai_controller_safety
    }
)
