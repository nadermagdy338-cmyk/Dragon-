package nd.max.ui.mainscreens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxDomainCard
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.navigation.MaxRisk
import nd.max.ui.subscreens.hubs.maxHubDescription

/**
 * The Control primary destination.
 *
 * Structure is deliberately flat and legible, which is what the old "all
 * tweaks" list got right: grouped headings, one line per destination, a short
 * description under each title, and nothing else competing for attention.
 *
 *   performance / environment / system domains -> tools
 *
 * Removed on purpose:
 * • the "live command center" lane, which duplicated Max Live;
 * • the freshness chip on every domain row, which shouted "Snapshot" nine
 *   times for values the row does not even display;
 * • the flat tweaks workspace — its switches now live in the domain hub they
 *   belong to, and backup/restore moved under Settings.
 */
@Composable
fun ControlScreen(navController: NavHostController) {
    val actions = MaxNavActions(navController)
    val hubs = MaxDestination.All.filter { it.parent == MaxDestination.Control }

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        subtitle = stringResource(R.string.control_workspace_subtitle),
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.control_map_title),
                body = stringResource(R.string.control_map_desc),
            )
        },
    ) {
        groupedControlDomains(hubs).forEach { group ->
            item(key = group.key) {
                MaxSection(title = stringResource(group.titleRes)) {
                    MaxGroup {
                        group.items.forEachIndexed { index, hub ->
                            if (index > 0) MaxGroupDivider()
                            MaxDomainCard(
                                title = stringResource(hub.titleRes),
                                subtitle = stringResource(maxHubDescription(hub)),
                                icon = hub.icon,
                                onClick = { actions.navigateTo(hub) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "control_tools") {
            MaxSection(title = stringResource(R.string.max_nav_advanced_tools)) {
                MaxGroup {
                    controlTools().forEachIndexed { index, tool ->
                        if (index > 0) MaxGroupDivider()
                        MaxRow(
                            title = stringResource(tool.titleRes),
                            subtitle = stringResource(controlToolRole(tool)),
                            icon = tool.icon,
                            iconTone = controlRiskTone(tool),
                            onClick = { actions.navigateTo(tool) },
                            trailing = {
                                MaxCapsule(
                                    text = stringResource(controlRiskLabel(tool.risk)),
                                    tone = controlRiskTone(tool),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class ControlDomainGroup(
    val key: String,
    @androidx.annotation.StringRes val titleRes: Int,
    val items: List<MaxDestination>,
)

private fun groupedControlDomains(hubs: List<MaxDestination>): List<ControlDomainGroup> = listOf(
    ControlDomainGroup(
        "control_group_performance",
        R.string.control_group_performance,
        hubs.filter {
            it in listOf(
                MaxDestination.CpuHub,
                MaxDestination.GpuHub,
                MaxDestination.MemoryHub,
                MaxDestination.ResponsivenessHub,
            )
        },
    ),
    ControlDomainGroup(
        "control_group_environment",
        R.string.control_group_environment,
        hubs.filter {
            it in listOf(
                MaxDestination.ThermalHub,
                MaxDestination.PowerHub,
                MaxDestination.DisplayHub,
            )
        },
    ),
    ControlDomainGroup(
        "control_group_system",
        R.string.control_group_system,
        hubs.filter {
            it in listOf(
                MaxDestination.StorageHub,
                MaxDestination.NetworkHub,
            )
        },
    ),
).filter { it.items.isNotEmpty() }

/** Every low-level tool, in one place (no more "advanced" vs "normal" split). */
private fun controlTools(): List<MaxDestination> = listOf(
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
