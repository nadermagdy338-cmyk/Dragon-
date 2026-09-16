package nd.max.ui.subscreens.hubs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

/**
 * Shared body of the nine Control domain hubs (ADR-04).
 *
 * Rows are derived from [MaxDestination] parentage so the registry stays the
 * single source of truth. A few screens serve several domains (the MTK vendor
 * screen carries CPU, GPU/DRAM and thermal tabs; the preference editor carries
 * both CPU and VM/swappiness rows); those are cross-listed explicitly below
 * until NT-03 dissolves the tabbed screens.
 */
@Composable
fun MaxDomainHubScreen(navController: NavHostController, destination: MaxDestination) {
    val actions = MaxNavActions(navController)
    val rows = MaxDestination.All.filter { it.parent == destination } + extraRows(destination)

    MaxScreen(
        title = stringResource(destination.titleRes),
        onBack = actions::back,
        accentIcon = destination.icon,
    ) {
        MaxSection(
            title = stringResource(R.string.max_hub_experience_title),
            description = stringResource(maxHubDescription(destination)),
        ) {
            MaxGroup {
                MaxRow(
                    title = stringResource(maxHubQuestionTitle(destination)),
                    subtitle = stringResource(maxHubQuestionDescription(destination)),
                    icon = destination.icon,
                )
            }
        }

        MaxSection(
            title = stringResource(R.string.max_hub_tools_title),
            description = stringResource(R.string.max_hub_tools_desc, rows.size),
        ) {
            MaxGroup {
                rows.forEachIndexed { index, row ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = stringResource(row.titleRes),
                        subtitle = stringResource(maxDestinationRole(row)),
                        icon = row.icon,
                        onClick = { actions.navigateTo(row) },
                    )
                }
            }
        }
    }
}

/** Screens that belong to more than one domain (see kdoc above). */
private fun extraRows(hub: MaxDestination): List<MaxDestination> = when (hub) {
    MaxDestination.GpuHub -> listOf(MaxDestination.MtkVendor)
    MaxDestination.ThermalHub -> listOf(MaxDestination.MtkVendor)
    else -> emptyList()
}

/** One-line scope description for a Control domain hub. */
@androidx.annotation.StringRes
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



@androidx.annotation.StringRes
private fun maxHubQuestionTitle(hub: MaxDestination): Int = when (hub) {
    MaxDestination.CpuHub -> R.string.max_hub_cpu_question_title
    MaxDestination.GpuHub -> R.string.max_hub_gpu_question_title
    MaxDestination.MemoryHub -> R.string.max_hub_memory_question_title
    MaxDestination.DisplayHub -> R.string.max_hub_display_question_title
    MaxDestination.ResponsivenessHub -> R.string.max_hub_responsiveness_question_title
    MaxDestination.ThermalHub -> R.string.max_hub_thermal_question_title
    MaxDestination.PowerHub -> R.string.max_hub_power_question_title
    MaxDestination.StorageHub -> R.string.max_hub_storage_question_title
    MaxDestination.NetworkHub -> R.string.max_hub_network_question_title
    else -> R.string.max_hub_generic_question_title
}

@androidx.annotation.StringRes
private fun maxHubQuestionDescription(hub: MaxDestination): Int = when (hub) {
    MaxDestination.CpuHub -> R.string.max_hub_cpu_question_desc
    MaxDestination.GpuHub -> R.string.max_hub_gpu_question_desc
    MaxDestination.MemoryHub -> R.string.max_hub_memory_question_desc
    MaxDestination.DisplayHub -> R.string.max_hub_display_question_desc
    MaxDestination.ResponsivenessHub -> R.string.max_hub_responsiveness_question_desc
    MaxDestination.ThermalHub -> R.string.max_hub_thermal_question_desc
    MaxDestination.PowerHub -> R.string.max_hub_power_question_desc
    MaxDestination.StorageHub -> R.string.max_hub_storage_question_desc
    MaxDestination.NetworkHub -> R.string.max_hub_network_question_desc
    else -> R.string.max_hub_generic_question_desc
}

@androidx.annotation.StringRes
private fun maxDestinationRole(destination: MaxDestination): Int = when (destination) {
    MaxDestination.CpuCoreControl -> R.string.max_role_cpu_core
    MaxDestination.GovernorSettings -> R.string.max_role_governor
    MaxDestination.MtkVendor -> R.string.max_role_vendor
    MaxDestination.GpuStudio -> R.string.max_role_gpu_studio
    MaxDestination.ZramManager -> R.string.max_role_zram
    MaxDestination.DisplayStudio -> R.string.max_role_display_studio
    MaxDestination.Resolution -> R.string.max_role_resolution
    MaxDestination.TouchBoost -> R.string.max_role_touch
    MaxDestination.FpsGo -> R.string.max_role_fpsgo
    MaxDestination.Fas -> R.string.max_role_fas
    MaxDestination.FpsOverlay -> R.string.max_role_fps_overlay
    MaxDestination.ThermalDetail -> R.string.max_role_thermal
    MaxDestination.Charging -> R.string.max_role_charging
    MaxDestination.BypassCharging -> R.string.max_role_bypass
    MaxDestination.BypassChargingCheck -> R.string.max_role_bypass_check
    MaxDestination.DozeMode -> R.string.max_role_doze
    MaxDestination.BatteryDetail -> R.string.max_role_battery
    MaxDestination.Dex2oat -> R.string.max_role_dex2oat
    MaxDestination.StorageDetail -> R.string.max_role_storage
    MaxDestination.NetworkScheduler -> R.string.max_role_network_scheduler
    MaxDestination.NetworkDetail -> R.string.max_role_network_detail
    else -> R.string.max_role_open_screen
}
