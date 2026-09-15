package nd.max.ui.mainscreens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.design.MaxDomainCard
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.subscreens.hubs.maxHubDescription

/**
 * The Control primary destination: the nine device-domain hubs (ADR-04)
 * plus the legacy flat tweaks workspace while its toggle rows await a home.
 */
@Composable
fun ControlScreen(navController: NavHostController) {
    val actions = MaxNavActions(navController)
    val hubs = MaxDestination.All.filter { it.parent == MaxDestination.Control && it != MaxDestination.AllTweaks }

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        subtitle = stringResource(R.string.control_workspace_subtitle),
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
        header = {
            MaxSection(
                title = stringResource(R.string.control_map_title),
                description = stringResource(R.string.control_map_desc),
            ) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.control_map_how_title),
                        subtitle = stringResource(R.string.control_map_how_desc),
                        icon = MaxDestination.MaxLive.icon,
                    )
                }
            }
        }
    ) {
        hubs.forEach { hub ->
            item(key = hub.route) {
                val profile = controlDomainProfile(hub)
                MaxDomainCard(
                    title = stringResource(hub.titleRes),
                    subtitle = stringResource(maxHubDescription(hub)),
                    icon = hub.icon,
                    onClick = { actions.navigateTo(hub) },
                    state = stringResource(profile.stateRes, controlDomainScreenCount(hub)),
                    trust = MaxDataTrust.Snapshot,
                )
            }
        }
        item(key = MaxDestination.AllTweaks.route) {
            MaxSection(title = stringResource(R.string.max_nav_all_tweaks_section)) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_nav_all_tweaks),
                        subtitle = stringResource(R.string.max_nav_all_tweaks_desc),
                        icon = MaxDestination.AllTweaks.icon,
                        onClick = { actions.navigateTo(MaxDestination.AllTweaks) },
                    )
                }
            }
        }
    }
}


private data class ControlDomainProfile(
    @androidx.annotation.StringRes val stateRes: Int,
)

private fun controlDomainProfile(hub: MaxDestination): ControlDomainProfile = when (hub) {
    MaxDestination.CpuHub -> ControlDomainProfile(R.string.control_domain_cpu_state)
    MaxDestination.GpuHub -> ControlDomainProfile(R.string.control_domain_gpu_state)
    MaxDestination.MemoryHub -> ControlDomainProfile(R.string.control_domain_memory_state)
    MaxDestination.DisplayHub -> ControlDomainProfile(R.string.control_domain_display_state)
    MaxDestination.ResponsivenessHub -> ControlDomainProfile(R.string.control_domain_responsiveness_state)
    MaxDestination.ThermalHub -> ControlDomainProfile(R.string.control_domain_thermal_state)
    MaxDestination.PowerHub -> ControlDomainProfile(R.string.control_domain_power_state)
    MaxDestination.StorageHub -> ControlDomainProfile(R.string.control_domain_storage_state)
    MaxDestination.NetworkHub -> ControlDomainProfile(R.string.control_domain_network_state)
    else -> ControlDomainProfile(R.string.control_domain_generic_state)
}

private fun controlDomainScreenCount(hub: MaxDestination): Int =
    MaxDestination.All.count { it.parent == hub } + when (hub) {
        MaxDestination.GpuHub, MaxDestination.ThermalHub, MaxDestination.MemoryHub -> 1
        else -> 0
    }
