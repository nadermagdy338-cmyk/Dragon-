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
        // MaxSection requires a title; the app bar already names the hub, so the
        // section carries the scope description instead of repeating the name (F-07).
        MaxSection(
            title = stringResource(maxHubDescription(destination)),
        ) {
            MaxGroup {
                rows.forEachIndexed { index, row ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = stringResource(row.titleRes),
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
    MaxDestination.MemoryHub -> listOf(MaxDestination.PreferenceTweaks)
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

