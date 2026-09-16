package nd.max.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import nd.max.R

/**
 * Risk classification for destinations that can wedge or brick a device.
 * Consumed by Control and any advanced-tool gate that explains device risk.
 */
enum class MaxRisk { Normal, Advanced, Dangerous }

/**
 * The single source of truth for every route in the app (ADR-02).
 *
 * Route strings exist ONLY here. Screens navigate through [MaxNavActions]
 * and reference these objects; navigate("literal") anywhere outside this
 * package is a static-test violation.
 *
 * @param parent the destination this one is reached from. Drives the
 *        Control hub membership and back-stack expectations.
 */
sealed class MaxDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val parent: MaxDestination? = null,
    val risk: MaxRisk = MaxRisk.Normal,
    val isPrimary: Boolean = false,
) {
    // Primary destinations (bottom bar / navigation rail)
    data object Now : MaxDestination("now", R.string.max_nav_now, Icons.Rounded.Home, isPrimary = true)
    data object Control : MaxDestination("control", R.string.max_nav_control, Icons.Rounded.Tune, isPrimary = true)
    data object Apps : MaxDestination("apps", R.string.max_nav_apps, Icons.Rounded.Apps, isPrimary = true)
    data object MaxAi : MaxDestination("max_ai", R.string.max_nav_max_ai, Icons.Rounded.AutoAwesome, isPrimary = true)


    // Settings root (opened from the Now top bar)
    data object Settings : MaxDestination("settings", R.string.max_nav_settings, Icons.Rounded.Settings)

    // Onboarding
    data object GetStarted : MaxDestination("get_started", R.string.max_title_get_started, Icons.Rounded.Home)

    // Control domain hubs (ADR-04)
    data object CpuHub : MaxDestination("hub_cpu", R.string.max_hub_cpu, Icons.Rounded.Memory, Control)
    data object GpuHub : MaxDestination("hub_gpu", R.string.max_hub_gpu, Icons.Rounded.Speed, Control)
    data object MemoryHub : MaxDestination("hub_memory", R.string.max_hub_memory, Icons.Rounded.Storage, Control)
    data object DisplayHub : MaxDestination("hub_display", R.string.max_hub_display, Icons.Rounded.DisplaySettings, Control)
    data object ResponsivenessHub : MaxDestination("hub_responsiveness", R.string.max_hub_responsiveness, Icons.Rounded.TouchApp, Control)
    data object ThermalHub : MaxDestination("hub_thermal", R.string.max_hub_thermal, Icons.Rounded.Thermostat, Control)
    data object PowerHub : MaxDestination("hub_power", R.string.max_hub_power, Icons.Rounded.BatteryChargingFull, Control)
    data object StorageHub : MaxDestination("hub_storage", R.string.max_hub_storage, Icons.Rounded.Storage, Control)
    data object NetworkHub : MaxDestination("hub_network", R.string.max_hub_network, Icons.Rounded.NetworkCheck, Control)


    // Feature screens: CPU domain
    data object CpuCoreControl : MaxDestination("cpucorecontrol", R.string.cpu_core_control_title, Icons.Rounded.DeveloperBoard, CpuHub)
    data object GovernorSettings : MaxDestination("governorsettings", R.string.gov_settings, Icons.Rounded.Tune, CpuHub)
    data object PreferenceTweaks : MaxDestination("preferenced", R.string.prefs, Icons.Rounded.Tune, CpuHub)
    data object MtkVendor : MaxDestination("mtkscreen", R.string.max_title_vendor_boost, Icons.Rounded.Memory, CpuHub)

    // Feature screens: GPU domain
    data object GpuStudio : MaxDestination("gpustudio", R.string.max_title_gpu_studio, Icons.Rounded.Speed, GpuHub)

    // Feature screens: Memory domain
    data object ZramManager : MaxDestination("zrammanager", R.string.zram_title, Icons.Rounded.Storage, MemoryHub)

    // Feature screens: Display domain
    data object DisplayStudio : MaxDestination("displaystudio", R.string.display_studio_title, Icons.Rounded.DisplaySettings, DisplayHub)
    data object Resolution : MaxDestination("resolutionscreen", R.string.resolution_title, Icons.Rounded.AspectRatio, DisplayHub)

    // Feature screens: Responsiveness domain
    data object TouchBoost : MaxDestination("touchboost", R.string.touch_boost_title, Icons.Rounded.TouchApp, ResponsivenessHub)
    data object FpsGo : MaxDestination("fpsgoscreen", R.string.str_fpsgo_settings, Icons.Rounded.Speed, ResponsivenessHub)
    data object Fas : MaxDestination("FasScreen", R.string.str_frame_aware_scheduling, Icons.Rounded.Schedule, ResponsivenessHub)
    data object FpsOverlay : MaxDestination("fpsoverlay", R.string.fps_overlay_title, Icons.Rounded.PictureInPicture, ResponsivenessHub)

    // Feature screens: Thermal domain
    data object ThermalDetail : MaxDestination("thermal_detail", R.string.thermal_title, Icons.Rounded.Thermostat, ThermalHub)

    // Feature screens: Power domain
    data object Charging : MaxDestination("chargingscreen", R.string.charging_title, Icons.Rounded.BatteryChargingFull, PowerHub)
    data object BypassCharging : MaxDestination("bypasschg", R.string.bcharging, Icons.Rounded.Cable, PowerHub)
    data object BypassChargingCheck : MaxDestination("bypasschg_check", R.string.max_title_bypass_check, Icons.Rounded.Power, PowerHub)
    data object DozeMode : MaxDestination("dozemode", R.string.dozemode_title, Icons.Rounded.Bedtime, PowerHub)
    data object BatteryDetail : MaxDestination("battery_detail", R.string.detail_battery, Icons.Rounded.BatteryFull, PowerHub)

    // Feature screens: Storage & compiler domain
    data object Dex2oat : MaxDestination("dex2oat", R.string.dex2oat_title, Icons.Rounded.Science, StorageHub)
    data object StorageDetail : MaxDestination("storage_detail", R.string.detail_storage, Icons.Rounded.DataUsage, StorageHub)

    // Feature screens: Network domain
    data object NetworkScheduler : MaxDestination("networkscheduler", R.string.net_sched_title, Icons.Rounded.NetworkCheck, NetworkHub)
    data object NetworkDetail : MaxDestination("network_detail", R.string.detail_network, Icons.Rounded.Wifi, NetworkHub)

    // Feature screens: Apps destination
    data object ProcessManager : MaxDestination("processmanager", R.string.processmgr_title, Icons.Rounded.Timeline, Apps)
    data object DebloatFreeze : MaxDestination("debloatfreeze", R.string.debloat_freeze_title, Icons.Rounded.CleaningServices, Apps)
    data object AppSettings : MaxDestination("app_settings/{pkg}", R.string.max_title_app_settings, Icons.Rounded.AppSettingsAlt, Apps)

    // Settings children + system tooling routes
    data object ColorPalette : MaxDestination("color_palette", R.string.theme, Icons.Rounded.Palette, Settings)
    data object ColorScheme : MaxDestination("colorscheme", R.string.color_scheme, Icons.Rounded.ColorLens, Settings)
    data object Diagnostics : MaxDestination("diagnostics", R.string.section_diagnostics, Icons.Rounded.BugReport, Control)
    data object Logs : MaxDestination("logsviewer", R.string.logsviewer_title, Icons.Rounded.ListAlt, Control)
    data object About : MaxDestination("aboutscreen", R.string.section_about, Icons.Rounded.Info, Settings)

    // Control - Tools (gated, not preferences)
    data object Terminal : MaxDestination("terminal", R.string.max_title_terminal, Icons.Rounded.Terminal, Control, MaxRisk.Dangerous)
    data object SetEdit : MaxDestination("setedit", R.string.max_title_setedit, Icons.Rounded.Edit, Control, MaxRisk.Advanced)
    data object ActivityLauncher : MaxDestination("activitylauncher", R.string.max_title_activity_launcher, Icons.Rounded.Launch, Control, MaxRisk.Advanced)
    data object KernelFlasher : MaxDestination("kernelflasher", R.string.max_title_kernel_flasher, Icons.Rounded.Build, Control, MaxRisk.Dangerous)

    companion object {
        /** The four bottom-bar / nav-rail destinations (ADR-03). */
        val PrimaryDestinations = listOf(Now, Control, Apps, MaxAi)

        /** Every destination registered in [MaxNavGraph]. */
        val All = listOf(
            GetStarted, Now, Control, Apps, MaxAi, Settings,
            CpuHub, GpuHub, MemoryHub, DisplayHub, ResponsivenessHub, ThermalHub,
            PowerHub, StorageHub, NetworkHub,
            CpuCoreControl, GovernorSettings, PreferenceTweaks, MtkVendor, GpuStudio,
            ZramManager, DisplayStudio, Resolution, TouchBoost, FpsGo, Fas, FpsOverlay,
            ThermalDetail, Charging, BypassCharging, BypassChargingCheck, DozeMode,
            BatteryDetail, Dex2oat, StorageDetail, NetworkScheduler, NetworkDetail,
            ProcessManager, DebloatFreeze, AppSettings,
            ColorPalette, ColorScheme, Diagnostics, Logs, About,
            Terminal, SetEdit, ActivityLauncher, KernelFlasher,
        )

        /** Route ids of the primary destinations, for bar visibility checks. */
        val PrimaryRoutes = PrimaryDestinations.map { it.route }.toSet()
    }
}
