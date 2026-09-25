/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Traffic & Scheduler.
 *
 * The old layout opened with a decorative card that counted how many knobs
 * the kernel exposed, then repeated an explanatory paragraph above every
 * group. Counting knobs is not a decision, so the card is gone and the screen
 * is now the controls themselves: current values live on the rows that change
 * them, the long-form explanation moved to the top bar help, and the raw
 * kernel parameters stay folded until asked for.
 */

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.CustomContentDialog
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.viewmodel.NetworkSchedulerViewModel

/** Kernel nodes this screen reads; shown as machine truth on condition panels. */
private const val KERNEL_SOURCES = "/proc/sys/net/ipv4 \u00b7 /proc/sys/kernel"

@Composable
fun NetworkSchedulerScreen(
    navController: NavController,
    viewModel: NetworkSchedulerViewModel = viewModel()
) {
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) { viewModel.loadState() }

    // Secondary choices and raw parameters are folded: one section, one idea.
    var congestionExpanded by remember { mutableStateOf(false) }
    var scalingExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    val screenTitle = stringResource(R.string.net_sched_title)

    val condition = when (viewModel.isAvailable) {
        null -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = screenTitle,
            detail = stringResource(R.string.net_sched_probe_detail),
            technicalDetail = KERNEL_SOURCES
        )

        false -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = screenTitle,
            detail = stringResource(R.string.net_sched_unavailable),
            technicalDetail = KERNEL_SOURCES
        )

        else -> null
    }

    ScreenAccentProvider(accent) {
        MaxScreen(
            title = screenTitle,
            subtitle = stringResource(R.string.net_sched_menu_desc),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Outlined.SettingsEthernet,
            accent = accent,
            condition = condition,
            actions = {
                MaxHelpAction(
                    title = screenTitle,
                    body = stringResource(R.string.sched_advanced_desc)
                )
            }
        ) {
            NetworkSection(
                vm = viewModel,
                congestionExpanded = congestionExpanded,
                onToggleCongestion = { congestionExpanded = !congestionExpanded }
            )
            SchedulerSection(
                vm = viewModel,
                scalingExpanded = scalingExpanded,
                onToggleScaling = { scalingExpanded = !scalingExpanded }
            )
            UclampSection(vm = viewModel)
            AdvancedSection(
                vm = viewModel,
                expanded = advancedExpanded,
                onToggle = { advancedExpanded = !advancedExpanded }
            )
        }
    }
}

@Composable
private fun NetworkSection(
    vm: NetworkSchedulerViewModel,
    congestionExpanded: Boolean,
    onToggleCongestion: () -> Unit
) {
    val rows = buildList<@Composable () -> Unit> {
        if (vm.hasTcpCongestion) add {
            MaxRow(
                title = stringResource(R.string.net_congestion_title),
                subtitle = stringResource(R.string.net_congestion_desc),
                icon = Icons.Outlined.NetworkCheck,
                onClick = onToggleCongestion,
                trailing = {
                    ValueChevron(
                        value = vm.tcpCongestion.ifBlank { MAX_VALUE_UNAVAILABLE },
                        expanded = congestionExpanded
                    )
                }
            )
            if (congestionExpanded) {
                // Algorithm tokens come from the kernel, so they are not translated.
                vm.availableCongestion.forEach { algorithm ->
                    MaxGroupDivider()
                    MaxChoiceRow(
                        title = algorithm,
                        selected = algorithm == vm.tcpCongestion,
                        onSelect = { vm.setTcpCongestion(algorithm) }
                    )
                }
            }
        }
        if (vm.hasSyncookies) add {
            MaxSwitchRow(
                title = stringResource(R.string.net_syncookies_title),
                subtitle = stringResource(R.string.net_syncookies_desc),
                icon = Icons.Outlined.Security,
                checked = vm.syncookiesEnabled,
                onCheckedChange = vm::setSyncookies
            )
        }
        if (vm.hasTcpReuse) add {
            MaxSwitchRow(
                title = stringResource(R.string.net_tcp_reuse_title),
                subtitle = stringResource(R.string.net_tcp_reuse_desc),
                icon = Icons.Outlined.Sync,
                checked = vm.tcpReuseEnabled,
                onCheckedChange = vm::setTcpReuse
            )
        }
        if (vm.hasTcpFastopen) add {
            MaxSwitchRow(
                title = stringResource(R.string.net_tcp_fastopen_title),
                subtitle = stringResource(R.string.net_tcp_fastopen_desc),
                icon = Icons.Outlined.Bolt,
                checked = vm.tcpFastopenEnabled,
                onCheckedChange = vm::setTcpFastopen
            )
        }
        if (vm.hasTcpSack) add {
            MaxSwitchRow(
                title = stringResource(R.string.net_tcp_sack_title),
                subtitle = stringResource(R.string.net_tcp_sack_desc),
                icon = Icons.Outlined.CheckCircle,
                checked = vm.tcpSackEnabled,
                onCheckedChange = vm::setTcpSack
            )
        }
        if (vm.hasTcpEcn) add {
            MaxSwitchRow(
                title = stringResource(R.string.net_tcp_ecn_title),
                subtitle = stringResource(R.string.net_tcp_ecn_desc),
                icon = Icons.Outlined.Warning,
                checked = vm.tcpEcnEnabled,
                onCheckedChange = vm::setTcpEcn
            )
        }
    }
    if (rows.isEmpty()) return

    MaxSection(title = stringResource(R.string.net_section_title)) {
        MaxGroup {
            rows.forEachIndexed { index, row ->
                if (index > 0) MaxGroupDivider()
                row()
            }
        }
    }
}

@Composable
private fun SchedulerSection(
    vm: NetworkSchedulerViewModel,
    scalingExpanded: Boolean,
    onToggleScaling: () -> Unit
) {
    val rows = buildList<@Composable () -> Unit> {
        if (vm.hasBore) add {
            MaxSwitchRow(
                title = stringResource(R.string.sched_bore_title),
                subtitle = stringResource(R.string.sched_bore_desc),
                icon = Icons.Outlined.Speed,
                checked = vm.boreEnabled,
                onCheckedChange = vm::setBore
            )
        }
        if (vm.hasAutogroup) add {
            MaxSwitchRow(
                title = stringResource(R.string.sched_autogroup_title),
                subtitle = stringResource(R.string.sched_autogroup_desc),
                icon = Icons.Outlined.Sync,
                checked = vm.autogroupEnabled,
                onCheckedChange = vm::setAutogroup
            )
        }
        if (vm.hasChildRunsFirst) add {
            MaxSwitchRow(
                title = stringResource(R.string.sched_child_runs_first_title),
                subtitle = stringResource(R.string.sched_child_runs_first_desc),
                icon = Icons.Outlined.Bolt,
                checked = vm.childRunsFirstEnabled,
                onCheckedChange = vm::setChildRunsFirst
            )
        }
        if (vm.hasSchedstats) add {
            MaxSwitchRow(
                title = stringResource(R.string.sched_stats_title),
                subtitle = stringResource(R.string.sched_stats_desc),
                icon = Icons.Outlined.QueryStats,
                checked = vm.schedstatsEnabled,
                onCheckedChange = vm::setSchedstats
            )
        }
        if (vm.hasCstateAware) add {
            MaxSwitchRow(
                title = stringResource(R.string.sched_cstate_aware_title),
                subtitle = stringResource(R.string.sched_cstate_aware_desc),
                icon = Icons.Outlined.BatteryChargingFull,
                checked = vm.cstateAwareEnabled,
                onCheckedChange = vm::setCstateAware
            )
        }
        if (vm.hasTunableScaling) add {
            val labels = listOf(
                stringResource(R.string.sched_tunable_scaling_none),
                stringResource(R.string.sched_tunable_scaling_log),
                stringResource(R.string.sched_tunable_scaling_linear)
            )
            MaxRow(
                title = stringResource(R.string.sched_tunable_scaling_title),
                subtitle = stringResource(R.string.sched_tunable_scaling_desc),
                icon = Icons.Outlined.Tune,
                onClick = onToggleScaling,
                trailing = {
                    ValueChevron(
                        value = labels.getOrNull(vm.tunableScalingIndex)
                            ?: MAX_VALUE_UNAVAILABLE,
                        expanded = scalingExpanded
                    )
                }
            )
            if (scalingExpanded) {
                labels.forEachIndexed { index, label ->
                    MaxGroupDivider()
                    MaxChoiceRow(
                        title = label,
                        selected = index == vm.tunableScalingIndex,
                        onSelect = { vm.setTunableScaling(index) }
                    )
                }
            }
        }
    }
    if (rows.isEmpty()) return

    MaxSection(title = stringResource(R.string.sched_section_title)) {
        MaxGroup {
            rows.forEachIndexed { index, row ->
                if (index > 0) MaxGroupDivider()
                row()
            }
        }
    }
}

@Composable
private fun UclampSection(vm: NetworkSchedulerViewModel) {
    if (!vm.hasUclampMax && !vm.hasUclampMin) return

    MaxSection(title = stringResource(R.string.sched_uclamp_section_title)) {
        MaxGroup {
            if (vm.hasUclampMax) {
                RawValueRow(
                    title = stringResource(R.string.sched_uclamp_max_title),
                    value = vm.uclampMaxValue,
                    onConfirm = vm::setUclampMax
                )
            }
            if (vm.hasUclampMax && vm.hasUclampMin) MaxGroupDivider()
            if (vm.hasUclampMin) {
                RawValueRow(
                    title = stringResource(R.string.sched_uclamp_min_title),
                    value = vm.uclampMinValue,
                    onConfirm = vm::setUclampMin
                )
            }
        }
        // The 0-1024 capacity range is needed to type a valid value, so it stays
        // next to the fields instead of hiding in help.
        MaxBullets(lines = listOf(stringResource(R.string.sched_uclamp_desc)))
    }
}

@Composable
private fun AdvancedSection(
    vm: NetworkSchedulerViewModel,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val tunables = vm.genericTunables
    val count = tunables.size + if (vm.hasPrintk) 1 else 0
    if (count == 0) return

    MaxGroup {
        MaxRow(
            title = stringResource(R.string.sched_advanced_section_title),
            icon = Icons.Outlined.Tune,
            onClick = onToggle,
            trailing = {
                ValueChevron(value = count.toString(), expanded = expanded)
            }
        )
        if (expanded) {
            tunables.forEach { tunable ->
                MaxGroupDivider()
                RawValueRow(
                    title = stringResource(tunable.labelRes),
                    value = tunable.value,
                    onConfirm = { value -> vm.setGenericTunable(tunable.path, value) }
                )
            }
            if (vm.hasPrintk) {
                MaxGroupDivider()
                RawValueRow(
                    title = stringResource(R.string.sched_printk_title),
                    value = vm.printkValue,
                    onConfirm = vm::setPrintk,
                    subtitle = stringResource(R.string.sched_printk_desc)
                )
            }
        }
    }
}

/**
 * Trailing slot for a row that both reports the live value and expands to the
 * choices behind it. No trust chip: these are settings, not measurements.
 */
@Composable
private fun ValueChevron(value: String, expanded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Rounded.ExpandLess
            } else {
                Icons.Rounded.ExpandMore
            },
            contentDescription = null
        )
    }
}

/**
 * A kernel value that has no safe range to offer as a slider: the row shows
 * what the node currently holds and editing happens in a dialog, so a typo
 * cannot be written by dragging.
 */
@Composable
private fun RawValueRow(
    title: String,
    value: String,
    onConfirm: (String) -> Unit,
    subtitle: String? = null
) {
    var dialogVisible by remember { mutableStateOf(false) }
    var pendingValue by remember(value, dialogVisible) { mutableStateOf(value) }

    MaxRow(
        title = title,
        subtitle = subtitle,
        onClick = { dialogVisible = true },
        trailing = {
            Text(
                text = value.ifBlank { MAX_VALUE_UNAVAILABLE },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    )

    CustomContentDialog(
        visible = dialogVisible,
        title = title,
        onDismiss = { dialogVisible = false },
        onConfirm = {
            onConfirm(pendingValue.trim())
            dialogVisible = false
        }
    ) {
        OutlinedTextField(
            value = pendingValue,
            onValueChange = { pendingValue = it },
            label = { Text(stringResource(R.string.net_sched_edit_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
