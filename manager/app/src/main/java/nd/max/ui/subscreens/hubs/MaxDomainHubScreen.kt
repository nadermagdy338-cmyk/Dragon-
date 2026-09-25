/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens.hubs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThermostatAuto
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVerticalCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.component.RendererDialog
import nd.max.ui.component.RootAppDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxNavigationRow
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.navigation.maxDestinationRole
import nd.max.ui.navigation.maxHubDescription
import nd.max.ui.navigation.maxHubRows
import nd.max.ui.viewmodel.TweakViewModel

/**
 * Shared body of the nine Control domain hubs (ADR-04).
 *
 * Navigation rows and their one-line roles come from [maxHubRows] and
 * [maxDestinationRole], so the hub and the Control page's expanded layout always
 * list the same screens in the same words — the cross-listed screens (the
 * preference editor carries both CPU and VM/swappiness rows) are declared once,
 * next to the destination tree.
 *
 * The hub also owns the switches that used to live in the removed flat tweaks
 * workspace: each one now sits in the domain it actually belongs to, so there
 * is exactly one place to look for a given knob.
 *
 * The "what is this page for" copy is NOT a card. It lives behind the top-bar
 * help action, which keeps every hub the same height and removes the large
 * question/answer block that dominated the old layout.
 */
@Composable
fun MaxDomainHubScreen(navController: NavHostController, destination: MaxDestination) {
    val actions = MaxNavActions(navController)
    val rows = maxHubRows(destination)

    MaxScreen(
        title = stringResource(destination.titleRes),
        subtitle = stringResource(maxHubDescription(destination)),
        onBack = actions::back,
        accentIcon = destination.icon,
        actions = {
            MaxHelpAction(
                title = stringResource(maxHubQuestionTitle(destination)),
                body = stringResource(maxHubQuestionDescription(destination)),
            )
        },
    ) {
        if (rows.isNotEmpty()) {
            MaxSection(title = stringResource(R.string.max_hub_tools_title)) {
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

        if (hubOwnsDirectControls(destination)) {
            HubDirectControls(destination)
        }
    }
}

/** Hubs that absorbed switches from the retired flat tweaks workspace. */
private fun hubOwnsDirectControls(hub: MaxDestination): Boolean = when (hub) {
    MaxDestination.ResponsivenessHub,
    MaxDestination.MemoryHub,
    MaxDestination.StorageHub,
    MaxDestination.ThermalHub,
    MaxDestination.DisplayHub -> true
    else -> false
}

/**
 * Direct switches for one hub.
 *
 * Isolated in its own composable so [TweakViewModel] (and its root reads) is
 * only created for the five hubs that actually need it.
 */
@Composable
private fun HubDirectControls(
    hub: MaxDestination,
    viewModel: TweakViewModel = viewModel(),
) {
    val context = LocalContext.current
    var showRendererDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hub) {
        viewModel.loadAllConfiguration(context)
    }

    val toggles = hubToggles(hub, viewModel).filter { it.checked != null }
    val showRenderer = hub == MaxDestination.DisplayHub

    if (toggles.isEmpty() && !showRenderer) return

    MaxSection(title = stringResource(R.string.max_hub_switches_title)) {
        MaxGroup {
            toggles.forEachIndexed { index, toggle ->
                if (index > 0) MaxGroupDivider()
                MaxSwitchRow(
                    title = stringResource(toggle.titleRes),
                    subtitle = stringResource(toggle.descRes),
                    icon = toggle.icon,
                    checked = toggle.checked == true,
                    onCheckedChange = toggle.onCheckedChange,
                )
            }

            if (showRenderer) {
                if (toggles.isNotEmpty()) MaxGroupDivider()
                MaxNavigationRow(
                    title = stringResource(R.string.renderengine),
                    subtitle = stringResource(R.string.renderengine_desc),
                    valueText = viewModel.currentRenderer?.uppercase(),
                    icon = Icons.Rounded.SettingsSuggest,
                    enabled = !viewModel.isRendererLoading,
                    onClick = { showRendererDialog = true },
                )
            }
        }
    }

    if (showRenderer) {
        RootAppDialog {
            RendererDialog(
                show = showRendererDialog,
                onDismiss = { showRendererDialog = false },
                onRenderer = { reason -> viewModel.executeSetRenderer(reason, context) },
            )
        }
    }
}

private class HubToggle(
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val descRes: Int,
    val icon: ImageVector,
    val checked: Boolean?,
    val onCheckedChange: (Boolean) -> Unit,
)

private fun hubToggles(hub: MaxDestination, vm: TweakViewModel): List<HubToggle> = when (hub) {
    MaxDestination.ResponsivenessHub -> listOf(
        HubToggle(
            R.string.perf_lite_mode,
            R.string.perf_lite_mode_desc,
            Icons.Rounded.Speed,
            vm.liteState,
            vm::updateLiteMode,
        ),
        HubToggle(
            R.string.app_priority_control,
            R.string.app_priority_control_desc,
            Icons.Rounded.SwapVerticalCircle,
            vm.appPriorState,
            vm::updateAppPriority,
        ),
        HubToggle(
            R.string.dnd_mode_gaming,
            R.string.dnd_mode_gaming_desc,
            Icons.Rounded.DoNotDisturbOn,
            vm.dndState,
            vm::updateDndMode,
        ),
    )

    MaxDestination.MemoryHub -> listOf(
        HubToggle(
            R.string.memory_killer,
            R.string.memory_killer_desc,
            Icons.Rounded.CleaningServices,
            vm.memKillerState,
            vm::updateMemoryKiller,
        ),
        HubToggle(
            R.string.game_preload,
            R.string.game_preload_desc,
            Icons.Rounded.RocketLaunch,
            vm.preloadState,
            vm::updatePreloadMode,
        ),
    )

    MaxDestination.StorageHub -> listOf(
        HubToggle(
            R.string.trim_filesystem,
            R.string.trim_filesystem_desc,
            Icons.Outlined.ContentCut,
            vm.fstrimState,
            vm::updateFstrim,
        ),
    )

    MaxDestination.ThermalHub -> listOf(
        HubToggle(
            R.string.thermalcore_service,
            R.string.thermalcore_service_desc,
            Icons.Filled.ThermostatAuto,
            vm.thermalState,
            vm::updateThermalCore,
        ),
    )

    else -> emptyList()
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

