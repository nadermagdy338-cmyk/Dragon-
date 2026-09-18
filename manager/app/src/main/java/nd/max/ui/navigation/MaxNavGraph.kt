package nd.max.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nd.max.ui.activitylauncher.ActivityLauncherScreen
import nd.max.ui.flasher.KernelFlasherScreen
import nd.max.ui.terminal.TerminalScreen
import nd.max.ui.mainscreens.*
import nd.max.ui.subscreens.*
import nd.max.ui.subscreens.hubs.*

/**
 * Registers every destination declared in [MaxDestination.All] (ADR-02).
 * No route literal may appear here; every entry references the registry.
 */
fun NavGraphBuilder.maxNavGraph(navController: NavHostController) {
    // Onboarding + primary destinations
    composable(MaxDestination.GetStarted.route) { GetStartedScreen(navController) }
    composable(MaxDestination.Now.route) { HomeScreen(navController) }
    composable(MaxDestination.Control.route) { ControlScreen(navController) }
    composable(MaxDestination.Apps.route) { ApplistScreen(navController) }
    composable(MaxDestination.MaxAi.route) { MaxAiScreen(navController) }
    composable(MaxDestination.MaxLive.route) { MaxLiveScreen(navController) }
    composable(MaxDestination.Settings.route) { SettingsScreen(navController) }

    // Control domain hubs: one parameterized entry per domain (F-07).
    composable(MaxDestination.CpuHub.route) { MaxDomainHubScreen(navController, MaxDestination.CpuHub) }
    composable(MaxDestination.GpuHub.route) { MaxDomainHubScreen(navController, MaxDestination.GpuHub) }
    composable(MaxDestination.MemoryHub.route) { MaxDomainHubScreen(navController, MaxDestination.MemoryHub) }
    composable(MaxDestination.DisplayHub.route) { MaxDomainHubScreen(navController, MaxDestination.DisplayHub) }
    composable(MaxDestination.ResponsivenessHub.route) { MaxDomainHubScreen(navController, MaxDestination.ResponsivenessHub) }
    composable(MaxDestination.ThermalHub.route) { MaxDomainHubScreen(navController, MaxDestination.ThermalHub) }
    composable(MaxDestination.PowerHub.route) { MaxDomainHubScreen(navController, MaxDestination.PowerHub) }
    composable(MaxDestination.StorageHub.route) { MaxDomainHubScreen(navController, MaxDestination.StorageHub) }
    composable(MaxDestination.NetworkHub.route) { MaxDomainHubScreen(navController, MaxDestination.NetworkHub) }

    // Feature screens
    composable(MaxDestination.CpuCoreControl.route) { CpuCoreControlScreen(navController) }
    composable(MaxDestination.GovernorSettings.route) { GovSettings(navController) }
    composable(MaxDestination.PreferenceTweaks.route) { PreferenceTweakScreen(navController) }
    composable(MaxDestination.GpuStudio.route) { GpuStudioScreen(navController) }
    composable(MaxDestination.ZramManager.route) { ZramManagerScreen(navController) }
    composable(MaxDestination.DisplayStudio.route) { DisplayStudioScreen(navController) }
    composable(MaxDestination.Resolution.route) { ResolutionScreen(navController) }
    composable(MaxDestination.TouchBoost.route) { TouchBoostScreen(navController) }
    composable(MaxDestination.FpsGo.route) { FpsGoSettings(navController) }
    composable(MaxDestination.Fas.route) { FasScreen(navController) }
    composable(MaxDestination.FpsOverlay.route) { FpsOverlayScreen(navController) }
    composable(MaxDestination.ThermalDetail.route) { ThermalDetailScreen(navController) }
    composable(MaxDestination.Charging.route) { ChargingScreen(navController) }
    composable(MaxDestination.BypassCharging.route) { BypassChargeScreen(navController) }
    composable(MaxDestination.BypassChargingCheck.route) { BypassChargeCheckScreen(navController) }
    composable(MaxDestination.DozeMode.route) { DozeModeScreen(navController) }
    composable(MaxDestination.BatteryDetail.route) { BatteryDetailScreen(navController) }
    composable(MaxDestination.Dex2oat.route) { Dex2oatScreen(navController) }
    composable(MaxDestination.StorageDetail.route) { StorageDetailScreen(navController) }
    composable(MaxDestination.NetworkScheduler.route) { NetworkSchedulerScreen(navController) }
    composable(MaxDestination.NetworkDetail.route) { NetworkDetailScreen(navController) }
    composable(MaxDestination.ProcessManager.route) { ProcessManagerScreen(navController) }
    composable(MaxDestination.DebloatFreeze.route) { DebloatFreezeScreen(navController) }
    composable(
        route = MaxDestination.AppSettings.route,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType })
    ) { entry ->
        AppSettingsScreen(navController, entry.arguments?.getString("pkg"))
    }

    // Settings children
    composable(MaxDestination.ColorPalette.route) { ColorPaletteScreen(navController) }
    composable(MaxDestination.ColorScheme.route) { ColorSchemeSettings(navController) }
    composable(MaxDestination.Diagnostics.route) { DiagnosticsScreen(navController) }
    composable(MaxDestination.Logs.route) { LogsViewerScreen(navController) }
    composable(MaxDestination.ConfigBackup.route) { ConfigBackupScreen(navController) }
    composable(
        route = MaxDestination.MaxBackup.route,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType; defaultValue = "" })
    ) { entry ->
        MaxBackupScreen(
            navController = navController,
            packageName = entry.arguments?.getString("pkg")?.takeIf { it.isNotBlank() },
        )
    }
    composable(
        route = MaxDestination.Permissions.route,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType })
    ) { entry ->
        PermissionsScreen(
            navController = navController,
            pkg = entry.arguments?.getString("pkg").orEmpty(),
        )
    }
    composable(MaxDestination.About.route) { AboutScreen(navController) }
    composable(MaxDestination.Privilege.route) { PrivilegeScreen(navController) }
    composable(MaxDestination.ModuleHealth.route) { ModuleHealthScreen(navController) }

    // Settings - Advanced tools (risk-gated in SettingsScreen, ADR-16)
    composable(MaxDestination.Terminal.route) { TerminalScreen() }
    composable(MaxDestination.SetEdit.route) { SetEditScreen(navController) }
    composable(MaxDestination.ActivityLauncher.route) { ActivityLauncherScreen(navController) }
    composable(MaxDestination.KernelFlasher.route) { KernelFlasherScreen(navController) }
}
