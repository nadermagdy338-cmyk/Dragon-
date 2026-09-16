package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.component.MaxSurface
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.component.RendererDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone
import nd.max.ui.design.content
import nd.max.ui.design.container
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.util.DebugUtils
import nd.max.ui.viewmodel.TweakViewModel
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * MaxManager Control — the visual control center.
 *
 * This keeps the old tuning surface alive, but presents it like an analytics
 * product: a compact state strip first, then domain groups with dense rows.
 * No giant hero card and no duplicate "All Tweaks" workspace.
 */
@Composable
fun ControlScreen(
    navController: NavHostController,
    viewModel: TweakViewModel = viewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val actions = MaxNavActions(navController)
    var isFullModeEnabled by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isFullModeEnabled = DebugUtils.isFullModeEnabled()
        viewModel.loadAllConfiguration(context)
    }

    // Section titles are resolved here, inside the @Composable ControlScreen
    // function, because MaxListScreen's `content` lambda is a plain
    // LazyListScope.() -> Unit (not @Composable) — stringResource() cannot
    // be called directly from inside it.
    val sectionPerformanceTitle = stringResource(R.string.section_performance)
    val sectionFeaturesTitle = stringResource(R.string.section_features)
    val sectionCpuSettingsTitle = stringResource(R.string.section_CPUSettings)
    val sectionGpuTitle = stringResource(R.string.max_hub_gpu)
    val sectionPowerThermalTitle = stringResource(R.string.section_power_thermal)
    val sectionDisplayRenderTitle = stringResource(R.string.section_display_render_settings)
    val sectionResponsivenessTitle = stringResource(R.string.max_hub_responsiveness)
    val sectionAdditionalTitle = stringResource(R.string.section_additionalsettings)
    val sectionAddonsTitle = stringResource(R.string.section_addons)

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        subtitle = "System controls · live configuration",
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
        header = {
            ControlOverview(
                fullMode = isFullModeEnabled,
                renderer = viewModel.currentRenderer,
                refreshRate = viewModel.currentRefreshRate,
            )
        },
    ) {
        ControlSection(sectionPerformanceTitle, "Primary performance controls") {
            if (viewModel.liteState != null) {
                MaxToggleRow(
                    stringResource(R.string.perf_lite_mode),
                    stringResource(R.string.perf_lite_mode_desc),
                    MaxDestination.Control.icon,
                    viewModel.liteState == true,
                    viewModel::updateLiteMode,
                )
            }
        }

        ControlSection(sectionFeaturesTitle, "Session and responsiveness behavior") {
            if (viewModel.preloadState != null) {
                MaxToggleRow(stringResource(R.string.game_preload), stringResource(R.string.game_preload_desc), MaxDestination.FpsGo.icon, viewModel.preloadState == true, viewModel::updatePreloadMode)
            }
            if (isFullModeEnabled && viewModel.memKillerState != null) {
                MaxToggleRow(stringResource(R.string.memory_killer), stringResource(R.string.memory_killer_desc), MaxDestination.MemoryHub.icon, viewModel.memKillerState == true, viewModel::updateMemoryKiller)
            }
            if (isFullModeEnabled && viewModel.appPriorState != null) {
                MaxToggleRow(stringResource(R.string.app_priority_control), stringResource(R.string.app_priority_control_desc), MaxDestination.ProcessManager.icon, viewModel.appPriorState == true, viewModel::updateAppPriority)
            }
            if (viewModel.dndState != null) {
                MaxToggleRow(stringResource(R.string.dnd_mode_gaming), stringResource(R.string.dnd_mode_gaming_desc), MaxDestination.DozeMode.icon, viewModel.dndState == true, viewModel::updateDndMode)
            }
            if (isFullModeEnabled && viewModel.fstrimState != null) {
                MaxToggleRow(stringResource(R.string.trim_filesystem), stringResource(R.string.trim_filesystem_desc), MaxDestination.StorageDetail.icon, viewModel.fstrimState == true, viewModel::updateFstrim)
            }
            MaxLinkRow(stringResource(R.string.touch_boost_title), stringResource(R.string.touch_boost_desc), MaxDestination.TouchBoost.icon) { actions.navigateTo(MaxDestination.TouchBoost) }
        }

        ControlSection(sectionCpuSettingsTitle, "Frequency, cores and vendor controls") {
            MaxLinkRow(stringResource(R.string.gov_settings), stringResource(R.string.gov_settingsdesc), MaxDestination.GovernorSettings.icon) { actions.navigateTo(MaxDestination.GovernorSettings) }
            MaxLinkRow(stringResource(R.string.cpu_core_control_title), stringResource(R.string.cpu_core_control_desc), MaxDestination.CpuCoreControl.icon) { actions.navigateTo(MaxDestination.CpuCoreControl) }
            MaxLinkRow(stringResource(R.string.max_title_vendor_boost), stringResource(R.string.max_role_vendor_boost), MaxDestination.MtkVendor.icon) { actions.navigateTo(MaxDestination.MtkVendor) }
        }

        ControlSection(sectionGpuTitle, "GPU frequency and rendering") {
            MaxLinkRow(stringResource(R.string.max_title_gpu_studio), stringResource(R.string.max_role_gpu_studio), MaxDestination.GpuStudio.icon) { actions.navigateTo(MaxDestination.GpuStudio) }
        }

        ControlSection(sectionPowerThermalTitle, "Power, charging, thermal and memory pressure") {
            MaxLinkRow(stringResource(R.string.charging_title), stringResource(R.string.charging_desc), MaxDestination.Charging.icon) { actions.navigateTo(MaxDestination.Charging) }
            MaxLinkRow(stringResource(R.string.dozemode_title), stringResource(R.string.dozemode_menu_desc), MaxDestination.DozeMode.icon) { actions.navigateTo(MaxDestination.DozeMode) }
            MaxLinkRow(stringResource(R.string.zram_title), stringResource(R.string.zram_desc), MaxDestination.ZramManager.icon) { actions.navigateTo(MaxDestination.ZramManager) }
            MaxLinkRow(stringResource(R.string.bcharging), stringResource(R.string.bcharging_desc), MaxDestination.BypassCharging.icon) { actions.navigateTo(MaxDestination.BypassCharging) }
            MaxLinkRow(stringResource(R.string.max_title_bypass_check), stringResource(R.string.max_role_bypass_check), MaxDestination.BypassChargingCheck.icon) { actions.navigateTo(MaxDestination.BypassChargingCheck) }
            if (viewModel.thermalState != null) {
                MaxToggleRow(stringResource(R.string.thermalcore_service), stringResource(R.string.thermalcore_service_desc), MaxDestination.ThermalHub.icon, viewModel.thermalState == true, viewModel::updateThermalCore)
            }
            MaxLinkRow(stringResource(R.string.thermal_title), stringResource(R.string.max_role_thermal), MaxDestination.ThermalDetail.icon) { actions.navigateTo(MaxDestination.ThermalDetail) }
            MaxLinkRow(stringResource(R.string.detail_battery), stringResource(R.string.max_role_battery_detail), MaxDestination.BatteryDetail.icon) { actions.navigateTo(MaxDestination.BatteryDetail) }
        }

        ControlSection(sectionDisplayRenderTitle, "Display, refresh rate and render path") {
            MaxLinkRow(stringResource(R.string.display_studio_title), stringResource(R.string.display_studio_desc), MaxDestination.DisplayStudio.icon) { actions.navigateTo(MaxDestination.DisplayStudio) }
            MaxLinkRow(stringResource(R.string.refreshrates), viewModel.currentRefreshRate?.let { stringResource(R.string.refresh_rate_format, it) } ?: stringResource(R.string.refreshrates_desc), MaxDestination.DisplayStudio.icon) { actions.navigateTo(MaxDestination.DisplayStudio) }
            MaxLinkRow(stringResource(R.string.renderengine), viewModel.currentRenderer?.uppercase() ?: stringResource(R.string.renderengine_desc), MaxDestination.DisplayStudio.icon) { showRendererDialog = true }
            MaxLinkRow(stringResource(R.string.resolution_title), stringResource(R.string.resolution_desc), MaxDestination.Resolution.icon) { actions.navigateTo(MaxDestination.Resolution) }
            MaxLinkRow(stringResource(R.string.fps_overlay_title), stringResource(R.string.fps_overlay_menu_desc), MaxDestination.FpsOverlay.icon) { actions.navigateTo(MaxDestination.FpsOverlay) }
        }

        ControlSection(sectionResponsivenessTitle, "Frame-aware scheduling and latency") {
            MaxLinkRow(stringResource(R.string.str_frame_aware_scheduling), stringResource(R.string.max_role_fas), MaxDestination.Fas.icon) { actions.navigateTo(MaxDestination.Fas) }
            MaxLinkRow(stringResource(R.string.str_fpsgo_settings), stringResource(R.string.str_fpsgo_desc), MaxDestination.FpsGo.icon) { actions.navigateTo(MaxDestination.FpsGo) }
        }

        ControlSection(sectionAdditionalTitle, "Network, storage and system behavior") {
            MaxLinkRow(stringResource(R.string.net_sched_title), stringResource(R.string.net_sched_menu_desc), MaxDestination.NetworkScheduler.icon) { actions.navigateTo(MaxDestination.NetworkScheduler) }
            MaxLinkRow(stringResource(R.string.detail_network), stringResource(R.string.max_role_network_detail), MaxDestination.NetworkDetail.icon) { actions.navigateTo(MaxDestination.NetworkDetail) }
            MaxLinkRow(stringResource(R.string.debloat_freeze_title), stringResource(R.string.debloat_freeze_desc), MaxDestination.DebloatFreeze.icon) { actions.navigateTo(MaxDestination.DebloatFreeze) }
            MaxLinkRow(stringResource(R.string.dex2oat_title), stringResource(R.string.dex2oat_desc), MaxDestination.Dex2oat.icon) { actions.navigateTo(MaxDestination.Dex2oat) }
            MaxLinkRow(stringResource(R.string.detail_storage), stringResource(R.string.max_role_storage_detail), MaxDestination.StorageDetail.icon) { actions.navigateTo(MaxDestination.StorageDetail) }
        }

        ControlSection(sectionAddonsTitle, "Appearance and visual system") {
            MaxLinkRow(stringResource(R.string.color_scheme), stringResource(R.string.schemecolordesc), MaxDestination.ColorScheme.icon) { actions.navigateTo(MaxDestination.ColorScheme) }
        }
    }

    RendererDialog(
        show = showRendererDialog,
        onDismiss = { showRendererDialog = false },
        onRenderer = { reason -> viewModel.executeSetRenderer(reason, context) },
    )
}

@Composable
private fun ControlOverview(
    fullMode: Boolean,
    renderer: String?,
    refreshRate: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Analytics-style overview: one compact status surface followed by
        // four dense metric tiles. This mirrors the reference language the
        // user supplied without turning Control into a generic settings page.
        MaxSurface(accent = MaterialTheme.colorScheme.primary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CONTROL CENTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        if (fullMode) "Full system control" else "Standard system control",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Live configuration · hardware aware",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MaxStatusPill(
                    if (fullMode) "ACTIVE" else "LIMITED",
                    active = fullMode
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ControlKpi("PERFORMANCE", "CPU / cores", Icons.Rounded.Speed, Modifier.weight(1f), MaxTone.Accent)
            ControlKpi("MEMORY", "RAM / ZRAM", Icons.Rounded.Memory, Modifier.weight(1f), MaxTone.Positive)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ControlKpi("THERMAL", "Power / cooling", Icons.Rounded.Thermostat, Modifier.weight(1f), MaxTone.Caution)
            ControlKpi("DISPLAY", refreshRate?.let { "$it Hz" } ?: "Auto", Icons.Rounded.DisplaySettings, Modifier.weight(1f), MaxTone.Accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MaxStatusPill(if (fullMode) "Full control" else "Standard", active = fullMode)
            MaxStatusPill(renderer?.uppercase() ?: "Renderer auto", active = renderer != null)
            MaxStatusPill("Live", active = true, accent = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ControlKpi(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Neutral,
) {
    MaxSurface(modifier = modifier, accent = tone.content()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tone.container()),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.content(),
                    modifier = Modifier.size(17.dp)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.7.sp
                )
                Text(
                    value,
                    style = MonoValueStyleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


private fun androidx.compose.foundation.lazy.LazyListScope.ControlSection(
    title: String,
    description: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    item(key = "$title:section") {
        MaxSection(title = title, description = description) {
            MaxGroup {
                content()
            }
        }
    }
}

@Composable
private fun MaxLinkRow(
    title: String,
    description: String,
    icon: ImageVector,
    tone: MaxTone = MaxTone.Neutral,
    onClick: () -> Unit,
) {
    MaxRow(
        title = title,
        subtitle = description,
        icon = icon,
        iconTone = tone,
        onClick = onClick,
        trailing = {
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

@Composable
private fun MaxToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MaxRow(
        title = title,
        subtitle = description,
        icon = icon,
        trailing = { androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}
