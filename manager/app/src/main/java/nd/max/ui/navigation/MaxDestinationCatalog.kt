/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager — destination catalog.
 *
 * Everything the UI needs to *describe* a destination lives here, next to the
 * tree it describes. It used to be spread over the screen that happened to need
 * it first: the hub screens owned the role table, the Control page kept a second
 * copy for the advanced tools, and the hub rows were recomputed at each call
 * site. Three copies of "which screens does this hub own, and what is each one
 * for" is exactly how a page ends up listing a screen the hub does not, or
 * describing the same screen two different ways.
 *
 * Rules for anything that touches destinations:
 * - Add or remove a destination in [MaxDestination] only.
 * - Add its one-line role here (one entry), and every layout that shows it —
 *   hub screen, Control compact list, Control expanded page — updates at once.
 */
package nd.max.ui.navigation

import androidx.annotation.StringRes
import nd.max.R

/**
 * Every screen reachable from [hub], in registry order.
 *
 * A few screens serve more than one domain (the preference editor carries CPU
 * and VM/swappiness rows), so those are cross-listed explicitly. Hub screens and
 * the Control page's expanded layout both read this, which is what keeps
 * "what the hub contains" and "what the expanded page shows" the same list.
 */
fun maxHubRows(hub: MaxDestination): List<MaxDestination> =
    MaxDestination.All.filter { it.parent == hub } + extraRows(hub)

/** Screens that belong to more than one domain (see the KDoc above). */
private fun extraRows(hub: MaxDestination): List<MaxDestination> = when (hub) {
    MaxDestination.MemoryHub -> listOf(MaxDestination.PreferenceTweaks)
    else -> emptyList()
}

/** One-line scope description for a Control domain hub. */
@StringRes
fun maxHubDescription(hub: MaxDestination): Int = when (hub) {
    MaxDestination.CpuHub -> R.string.max_hub_cpu_desc
    MaxDestination.GpuHub -> R.string.max_hub_gpu_desc
    MaxDestination.MemoryHub -> R.string.max_hub_memory_desc
    MaxDestination.DisplayHub -> R.string.max_hub_display_desc
    MaxDestination.ResponsivenessHub -> R.string.max_hub_responsiveness_desc
    MaxDestination.ThermalHub -> R.string.max_hub_thermal_desc
    MaxDestination.PowerHub -> R.string.max_hub_power_desc
    MaxDestination.StorageHub -> R.string.max_hub_storage_desc
    MaxDestination.NetworkHub -> R.string.max_hub_network_desc
    else -> R.string.max_status_unknown
}

/**
 * The one line that explains a destination on a row.
 *
 * The advanced tools are listed here rather than in a screen-local `when`, so the
 * Control page and the hub screens can never describe the same screen differently.
 */
@StringRes
fun maxDestinationRole(destination: MaxDestination): Int = when (destination) {
    MaxDestination.CpuCoreControl -> R.string.max_role_cpu_core
    MaxDestination.GovernorSettings -> R.string.max_role_governor
    MaxDestination.PreferenceTweaks -> R.string.max_role_preference_tweaks
    MaxDestination.GpuStudio -> R.string.max_role_gpu_studio
    MaxDestination.ZramManager -> R.string.max_role_zram
    MaxDestination.DisplayStudio -> R.string.max_role_display_studio
    MaxDestination.Resolution -> R.string.max_role_resolution
    MaxDestination.TouchBoost -> R.string.max_role_touch
    MaxDestination.FpsGo -> R.string.max_role_fpsgo
    MaxDestination.Fas -> R.string.max_role_fas
    MaxDestination.FpsOverlay -> R.string.max_role_fps_overlay
    MaxDestination.ThermalDetail -> R.string.max_role_thermal
    // الدور يصف ما صارت عليه الشاشة بعد الدمج: بطارية **و** شحن، لا شحن وحده.
    MaxDestination.Charging -> R.string.max_role_battery_and_charging
    MaxDestination.BypassCharging -> R.string.max_role_bypass
    MaxDestination.BypassChargingCheck -> R.string.max_role_bypass_check
    MaxDestination.DozeMode -> R.string.max_role_doze
    MaxDestination.Dex2oat -> R.string.max_role_dex2oat
    MaxDestination.StorageDetail -> R.string.max_role_storage
    MaxDestination.NetworkScheduler -> R.string.max_role_network_scheduler
    MaxDestination.NetworkDetail -> R.string.max_role_network_detail
    // الأدوات الأربع التي انتقلت من الإعدادات/التشخيص إلى `Control → Tools`.
    MaxDestination.ProcessManager -> R.string.max_role_process_manager
    MaxDestination.Logs -> R.string.max_role_logs
    MaxDestination.ColorPalette -> R.string.max_role_color_palette
    MaxDestination.ColorScheme -> R.string.max_role_color_scheme
    MaxDestination.SetEdit -> R.string.max_role_setedit
    MaxDestination.ActivityLauncher -> R.string.max_role_activity_launcher
    MaxDestination.MaxBackup -> R.string.max_role_max_backup
    MaxDestination.Permissions -> R.string.max_role_permissions
    MaxDestination.FileManager -> R.string.max_role_file_manager
    MaxDestination.Plugins -> R.string.max_role_plugins
    else -> R.string.max_role_open_screen
}
