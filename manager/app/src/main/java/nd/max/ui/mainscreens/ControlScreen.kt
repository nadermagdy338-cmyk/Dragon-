package nd.max.ui.mainscreens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.design.MaxDomainCard
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.navigation.MaxRisk
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
    ) {
        item(key = "control_live_lane") {
            MaxSection(
                title = stringResource(R.string.control_live_title),
                description = stringResource(R.string.control_live_desc),
            ) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_live_title),
                        subtitle = stringResource(R.string.control_live_plan_desc),
                        icon = MaxDestination.MaxLive.icon,
                        iconTone = MaxTone.Accent,
                        onClick = { actions.navigateTo(MaxDestination.MaxLive) },
                        trailing = { MaxCapsule(text = stringResource(R.string.control_live_badge), tone = MaxTone.Positive) },
                    )
                }
            }
        }

        groupedControlDomains(hubs).forEach { group ->
            item(key = group.key) {
                MaxSection(
                    title = stringResource(group.titleRes),
                    description = stringResource(group.descRes),
                ) {
                    MaxGroup {
                        group.items.forEachIndexed { index, hub ->
                            if (index > 0) MaxGroupDivider()
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
                }
            }
        }

        item(key = "control_advanced_tools") {
            MaxSection(
                title = stringResource(R.string.max_nav_advanced_tools),
                description = stringResource(R.string.control_advanced_tools_desc),
            ) {
                MaxGroup {
                    controlAdvancedTools().forEachIndexed { index, tool ->
                        if (index > 0) MaxGroupDivider()
                        MaxRow(
                            title = stringResource(tool.titleRes),
                            subtitle = stringResource(controlToolRole(tool)),
                            icon = tool.icon,
                            iconTone = controlRiskTone(tool),
                            onClick = { actions.navigateTo(tool) },
                            trailing = { MaxCapsule(text = stringResource(controlRiskLabel(tool.risk)), tone = controlRiskTone(tool)) },
                        )
                    }
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

private data class ControlDomainGroup(
    val key: String,
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val descRes: Int,
    val items: List<MaxDestination>,
)

private fun groupedControlDomains(hubs: List<MaxDestination>): List<ControlDomainGroup> = listOf(
    ControlDomainGroup("control_group_performance", R.string.control_group_performance, R.string.control_group_performance_desc, hubs.filter { it in listOf(MaxDestination.CpuHub, MaxDestination.GpuHub, MaxDestination.MemoryHub, MaxDestination.ResponsivenessHub) }),
    ControlDomainGroup("control_group_environment", R.string.control_group_environment, R.string.control_group_environment_desc, hubs.filter { it in listOf(MaxDestination.ThermalHub, MaxDestination.PowerHub, MaxDestination.DisplayHub) }),
    ControlDomainGroup("control_group_system", R.string.control_group_system, R.string.control_group_system_desc, hubs.filter { it in listOf(MaxDestination.StorageHub, MaxDestination.NetworkHub) }),
)

private fun controlAdvancedTools(): List<MaxDestination> = listOf(
    MaxDestination.AllTweaks,
    MaxDestination.Terminal,
    MaxDestination.SetEdit,
    MaxDestination.ActivityLauncher,
    MaxDestination.KernelFlasher,
)

private fun controlRiskTone(destination: MaxDestination): MaxTone = when (destination.risk) {
    MaxRisk.Dangerous -> MaxTone.Critical
    MaxRisk.Advanced -> MaxTone.Caution
    MaxRisk.Normal -> MaxTone.Neutral
}

@androidx.annotation.StringRes
private fun controlToolRole(destination: MaxDestination): Int = when (destination) {
    MaxDestination.AllTweaks -> R.string.max_nav_all_tweaks_desc
    MaxDestination.Terminal -> R.string.max_role_terminal
    MaxDestination.SetEdit -> R.string.max_role_setedit
    MaxDestination.ActivityLauncher -> R.string.max_role_activity_launcher
    MaxDestination.KernelFlasher -> R.string.max_role_kernel_flasher
    else -> R.string.max_role_open_screen
}

@androidx.annotation.StringRes
private fun controlRiskLabel(risk: MaxRisk): Int = when (risk) {
    MaxRisk.Normal -> R.string.max_risk_normal
    MaxRisk.Advanced -> R.string.max_risk_advanced
    MaxRisk.Dangerous -> R.string.max_risk_dangerous
}
