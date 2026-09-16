package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import nd.max.R
import nd.max.ui.component.RendererDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.util.DebugUtils
import nd.max.ui.viewmodel.TweakViewModel
import nd.max.ui.viewmodel.SettingsViewModel as RuntimeSettingsViewModel

/**
 * Single manual-control index for MaxManager.
 *
 * The retired legacy tweaks page has been absorbed here. Controls are presented
 * as compact settings-style rows so users can scan and act without navigating
 * through a second "all tweaks" workspace or large dashboard cards.
 */
@Composable
fun ControlScreen(
    navController: NavHostController,
    viewModel: TweakViewModel = viewModel(),
    settingsViewModel: RuntimeSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val actions = MaxNavActions(navController)
    var isFullModeEnabled by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        isFullModeEnabled = DebugUtils.isFullModeEnabled()
        viewModel.loadAllConfiguration(context)
    }

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        subtitle = stringResource(R.string.control_workspace_subtitle),
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
    ) {
        ControlSection(stringResource(R.string.section_performance)) {
            if (viewModel.liteState != null) {
                MaxToggleRow(
                    title = stringResource(R.string.perf_lite_mode),
                    description = stringResource(R.string.perf_lite_mode_desc),
                    icon = MaxDestination.Control.icon,
                    checked = viewModel.liteState == true,
                    onCheckedChange = viewModel::updateLiteMode,
                )
            }
        }

        ControlSection(stringResource(R.string.section_features)) {
            if (viewModel.preloadState != null) {
                MaxToggleRow(
                    stringResource(R.string.game_preload),
                    stringResource(R.string.game_preload_desc),
                    MaxDestination.FpsGo.icon,
                    viewModel.preloadState == true,
                    viewModel::updatePreloadMode,
                )
            }
            if (isFullModeEnabled && viewModel.memKillerState != null) {
                MaxToggleRow(
                    stringResource(R.string.memory_killer),
                    stringResource(R.string.memory_killer_desc),
                    MaxDestination.MemoryHub.icon,
                    viewModel.memKillerState == true,
                    viewModel::updateMemoryKiller,
                )
            }
            if (isFullModeEnabled && viewModel.appPriorState != null) {
                MaxToggleRow(
                    stringResource(R.string.app_priority_control),
                    stringResource(R.string.app_priority_control_desc),
                    MaxDestination.ProcessManager.icon,
                    viewModel.appPriorState == true,
                    viewModel::updateAppPriority,
                )
            }
            if (viewModel.dndState != null) {
                MaxToggleRow(
                    stringResource(R.string.dnd_mode_gaming),
                    stringResource(R.string.dnd_mode_gaming_desc),
                    MaxDestination.DozeMode.icon,
                    viewModel.dndState == true,
                    viewModel::updateDndMode,
                )
            }
            if (isFullModeEnabled && viewModel.fstrimState != null) {
                MaxToggleRow(
                    stringResource(R.string.trim_filesystem),
                    stringResource(R.string.trim_filesystem_desc),
                    MaxDestination.StorageDetail.icon,
                    viewModel.fstrimState == true,
                    viewModel::updateFstrim,
                )
            }
            MaxLinkRow(
                stringResource(R.string.touch_boost_title),
                stringResource(R.string.touch_boost_desc),
                MaxDestination.TouchBoost.icon,
            ) { actions.navigateTo(MaxDestination.TouchBoost) }
        }

        ControlSection(stringResource(R.string.section_CPUSettings)) {
            MaxLinkRow(stringResource(R.string.gov_settings), stringResource(R.string.gov_settingsdesc), MaxDestination.GovernorSettings.icon) { actions.navigateTo(MaxDestination.GovernorSettings) }
            MaxLinkRow(stringResource(R.string.cpu_core_control_title), stringResource(R.string.cpu_core_control_desc), MaxDestination.CpuCoreControl.icon) { actions.navigateTo(MaxDestination.CpuCoreControl) }
            MaxLinkRow(stringResource(R.string.max_title_vendor_boost), stringResource(R.string.max_role_vendor_boost), MaxDestination.MtkVendor.icon) { actions.navigateTo(MaxDestination.MtkVendor) }
        }

        ControlSection(stringResource(R.string.max_hub_gpu)) {
            MaxLinkRow(stringResource(R.string.max_title_gpu_studio), stringResource(R.string.max_role_gpu_studio), MaxDestination.GpuStudio.icon) { actions.navigateTo(MaxDestination.GpuStudio) }
        }

        ControlSection(stringResource(R.string.section_power_thermal)) {
            MaxLinkRow(stringResource(R.string.charging_title), stringResource(R.string.charging_desc), MaxDestination.Charging.icon) { actions.navigateTo(MaxDestination.Charging) }
            MaxLinkRow(stringResource(R.string.dozemode_title), stringResource(R.string.dozemode_menu_desc), MaxDestination.DozeMode.icon) { actions.navigateTo(MaxDestination.DozeMode) }
            MaxLinkRow(stringResource(R.string.zram_title), stringResource(R.string.zram_desc), MaxDestination.ZramManager.icon) { actions.navigateTo(MaxDestination.ZramManager) }
            MaxLinkRow(stringResource(R.string.bcharging), stringResource(R.string.bcharging_desc), MaxDestination.BypassCharging.icon) { actions.navigateTo(MaxDestination.BypassCharging) }
            MaxLinkRow(stringResource(R.string.max_title_bypass_check), stringResource(R.string.max_role_bypass_check), MaxDestination.BypassChargingCheck.icon) { actions.navigateTo(MaxDestination.BypassChargingCheck) }
            if (viewModel.thermalState != null) {
                MaxToggleRow(
                    stringResource(R.string.thermalcore_service),
                    stringResource(R.string.thermalcore_service_desc),
                    MaxDestination.ThermalHub.icon,
                    viewModel.thermalState == true,
                    viewModel::updateThermalCore,
                )
            }
            MaxLinkRow(stringResource(R.string.thermal_title), stringResource(R.string.max_role_thermal), MaxDestination.ThermalDetail.icon) { actions.navigateTo(MaxDestination.ThermalDetail) }
            MaxLinkRow(stringResource(R.string.detail_battery), stringResource(R.string.max_role_battery_detail), MaxDestination.BatteryDetail.icon) { actions.navigateTo(MaxDestination.BatteryDetail) }
        }

        ControlSection(stringResource(R.string.section_display_render_settings)) {
            MaxLinkRow(stringResource(R.string.display_studio_title), stringResource(R.string.display_studio_desc), MaxDestination.DisplayStudio.icon) { actions.navigateTo(MaxDestination.DisplayStudio) }
            MaxLinkRow(
                stringResource(R.string.refreshrates),
                viewModel.currentRefreshRate?.let { stringResource(R.string.refresh_rate_format, it) } ?: stringResource(R.string.refreshrates_desc),
                MaxDestination.DisplayStudio.icon,
            ) { actions.navigateTo(MaxDestination.DisplayStudio) }
            MaxLinkRow(
                stringResource(R.string.renderengine),
                viewModel.currentRenderer?.uppercase() ?: stringResource(R.string.renderengine_desc),
                MaxDestination.DisplayStudio.icon,
            ) { showRendererDialog = true }
            MaxLinkRow(stringResource(R.string.resolution_title), stringResource(R.string.resolution_desc), MaxDestination.Resolution.icon) { actions.navigateTo(MaxDestination.Resolution) }
            MaxLinkRow(stringResource(R.string.fps_overlay_title), stringResource(R.string.fps_overlay_menu_desc), MaxDestination.FpsOverlay.icon) { actions.navigateTo(MaxDestination.FpsOverlay) }
        }

        ControlSection(stringResource(R.string.max_hub_responsiveness)) {
            MaxLinkRow(stringResource(R.string.str_frame_aware_scheduling), stringResource(R.string.max_role_fas), MaxDestination.Fas.icon) { actions.navigateTo(MaxDestination.Fas) }
            MaxLinkRow(stringResource(R.string.str_fpsgo_settings), stringResource(R.string.str_fpsgo_desc), MaxDestination.FpsGo.icon) { actions.navigateTo(MaxDestination.FpsGo) }
        }

        ControlSection(stringResource(R.string.section_additionalsettings)) {
            MaxLinkRow(stringResource(R.string.net_sched_title), stringResource(R.string.net_sched_menu_desc), MaxDestination.NetworkScheduler.icon) { actions.navigateTo(MaxDestination.NetworkScheduler) }
            MaxLinkRow(stringResource(R.string.detail_network), stringResource(R.string.max_role_network_detail), MaxDestination.NetworkDetail.icon) { actions.navigateTo(MaxDestination.NetworkDetail) }
            MaxLinkRow(stringResource(R.string.debloat_freeze_title), stringResource(R.string.debloat_freeze_desc), MaxDestination.DebloatFreeze.icon) { actions.navigateTo(MaxDestination.DebloatFreeze) }
            MaxLinkRow(stringResource(R.string.dex2oat_title), stringResource(R.string.dex2oat_desc), MaxDestination.Dex2oat.icon) { actions.navigateTo(MaxDestination.Dex2oat) }
            MaxLinkRow(stringResource(R.string.detail_storage), stringResource(R.string.max_role_storage_detail), MaxDestination.StorageDetail.icon) { actions.navigateTo(MaxDestination.StorageDetail) }
        }

        ControlSection(stringResource(R.string.section_addons)) {
            MaxLinkRow(stringResource(R.string.color_scheme), stringResource(R.string.schemecolordesc), MaxDestination.ColorScheme.icon) { actions.navigateTo(MaxDestination.ColorScheme) }
        }

        ControlSection(stringResource(R.string.max_nav_tools)) {
            MaxLinkRow(stringResource(R.string.processmgr_title), stringResource(R.string.processmgr_menu_desc), MaxDestination.ProcessManager.icon) { actions.navigateTo(MaxDestination.ProcessManager) }
            MaxLinkRow(stringResource(R.string.section_diagnostics), stringResource(R.string.tools_diagnostics_desc), MaxDestination.Diagnostics.icon) { actions.navigateTo(MaxDestination.Diagnostics) }
            MaxLinkRow(stringResource(R.string.logsviewer_title), stringResource(R.string.tools_logs_desc), MaxDestination.Logs.icon) { actions.navigateTo(MaxDestination.Logs) }
            MaxLinkRow(stringResource(R.string.max_title_terminal), stringResource(R.string.max_role_terminal), MaxDestination.Terminal.icon, MaxTone.Critical) { actions.navigateTo(MaxDestination.Terminal) }
            MaxLinkRow(stringResource(R.string.max_title_setedit), stringResource(R.string.max_role_setedit), MaxDestination.SetEdit.icon, MaxTone.Caution) { actions.navigateTo(MaxDestination.SetEdit) }
            MaxLinkRow(stringResource(R.string.max_title_activity_launcher), stringResource(R.string.max_role_activity_launcher), MaxDestination.ActivityLauncher.icon, MaxTone.Caution) { actions.navigateTo(MaxDestination.ActivityLauncher) }
            MaxLinkRow(stringResource(R.string.max_title_kernel_flasher), stringResource(R.string.max_role_kernel_flasher), MaxDestination.KernelFlasher.icon, MaxTone.Critical) { actions.navigateTo(MaxDestination.KernelFlasher) }
            MaxToggleRow(
                stringResource(R.string.allow_verbose_log),
                stringResource(R.string.allow_verbose_log_desc),
                MaxDestination.Logs.icon,
                settingsState.debugMode,
                settingsViewModel::setDebugMode,
            )
            MaxToggleRow(
                stringResource(R.string.detailed_activity_log),
                stringResource(R.string.detailed_activity_log_desc),
                MaxDestination.Logs.icon,
                settingsState.detailedLog,
                settingsViewModel::setDetailedLog,
            )
        }
    }

    RendererDialog(
        show = showRendererDialog,
        onDismiss = { showRendererDialog = false },
        onRenderer = { reason -> viewModel.executeSetRenderer(reason, context) },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.ControlSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = "$title:section") {
        MaxSection(title = title) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun MaxToggleRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MaxRow(
        title = title,
        subtitle = description,
        icon = icon,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}
