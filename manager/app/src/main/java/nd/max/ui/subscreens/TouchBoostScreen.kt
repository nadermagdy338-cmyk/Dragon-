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

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.viewmodel.TouchBoostViewModel

/**
 * Touch response.
 *
 * Rebuilt on the MaxManager Design Language. What changed and why:
 *
 *  - The old screen showed a primaryContainer hero with "ON"/"OFF" text plus
 *    three "Game / Rate / DT2W" tiles that repeated what the switches below
 *    already said. Removed: a hero that restates the controls is decoration.
 *  - The old screen used the SAME row component to mean two different things:
 *    in "Response tuning" the check icon meant "boost is on", in "Driver
 *    information" the identical icon meant "this node exists". That is a real
 *    confusion bug, not a style issue. Capability and state are now separated:
 *    switches express state, the Providers section expresses what the kernel
 *    actually exposes, with data-trust marking.
 *  - Node paths are read once by loadState(), so they are presented as
 *    Snapshot, never as live telemetry, and a missing node renders as
 *    unavailable instead of an empty line.
 *  - Disabled controls now state the reason (no writable node / no HAL)
 *    instead of being silently greyed out.
 *  - Hardcoded English UI copy was moved to string resources.
 *  - The whole row is the touch target (48dp min) via MaxSwitchRow.
 */
@Composable
fun TouchBoostScreen(
    navController: NavController,
    viewModel: TouchBoostViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadState() }

    val gameNode = viewModel.gameModeNode
    val sampleNode = viewModel.sampleRateNode
    val dt2wNode = viewModel.doubleTapNode
    val halOnly = viewModel.vendorHalDetected

    // A write provider is a verified sysfs node, or the vendor HAL when no node
    // exists. Without either, the switch must not pretend to work.
    val canWriteBoost = gameNode != null || sampleNode != null || halOnly

    val sysfsSource = stringResource(R.string.max_touch_source_sysfs)
    val halSource = stringResource(R.string.max_touch_source_hal)
    val missing = stringResource(R.string.max_touch_node_missing)

    val condition = when (viewModel.isAvailable) {
        null -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_touch_probe_title),
            detail = stringResource(R.string.max_touch_probe_detail)
        )

        false -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = stringResource(R.string.max_touch_unsupported_title),
            detail = stringResource(
                if (halOnly) R.string.touch_boost_unavailable_vendor_hal
                else R.string.touch_boost_unavailable
            ),
            // The real reason, not "something went wrong": these are the exact
            // paths that were probed and did not answer.
            technicalDetail = stringResource(
                R.string.touch_boost_node_note,
                gameNode?.path ?: missing,
                sampleNode?.path ?: missing
            ),
            primaryActionLabel = stringResource(R.string.max_action_recheck),
            onPrimaryAction = { viewModel.loadState() }
        )

        else -> null
    }

    MaxScreen(
        title = stringResource(R.string.touch_boost_title),
        onBack = { navController.popBackStack() },
        subtitle = gameNode?.path
            ?: sampleNode?.path
            ?: if (halOnly) stringResource(R.string.max_touch_provider_hal) else null,
        accentIcon = Icons.Rounded.TouchApp,
        accent = MaterialTheme.colorScheme.secondary,
        condition = condition,
        actions = {
            IconButton(onClick = { viewModel.loadState() }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.max_action_recheck)
                )
            }
        }
    ) {
        MaxSection(
            title = stringResource(R.string.max_touch_section_controls),
            description = stringResource(R.string.max_touch_section_controls_desc)
        ) {
            MaxGroup {
                MaxSwitchRow(
                    title = stringResource(R.string.touch_boost_title),
                    subtitle = stringResource(R.string.touch_boost_desc),
                    checked = viewModel.boostEnabled,
                    onCheckedChange = viewModel::setBoost,
                    icon = Icons.Rounded.Bolt,
                    iconTone = MaxTone.Accent,
                    enabled = canWriteBoost,
                    lockedReason = stringResource(R.string.max_touch_boost_locked),
                    onStateDescription = stringResource(R.string.max_touch_state_on),
                    offStateDescription = stringResource(R.string.max_touch_state_off)
                )

                MaxGroupDivider()

                // Shown even when unsupported, so the user learns the device
                // lacks the node instead of wondering where the setting went.
                MaxSwitchRow(
                    title = stringResource(R.string.touch_dt2w_title),
                    subtitle = stringResource(R.string.touch_dt2w_desc),
                    checked = viewModel.doubleTapEnabled,
                    onCheckedChange = viewModel::setDoubleTapToWake,
                    icon = Icons.Rounded.Nightlight,
                    iconTone = MaxTone.Neutral,
                    enabled = dt2wNode != null,
                    lockedReason = stringResource(R.string.max_touch_dt2w_locked),
                    onStateDescription = stringResource(R.string.max_touch_state_on),
                    offStateDescription = stringResource(R.string.max_touch_state_off)
                )
            }

            // Honest explanation of the revert behaviour the ViewModel already
            // implements: a rejected write flips the switch back.
            Text(
                text = stringResource(R.string.max_touch_revert_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaxSpace.groupPadding)
            )
        }

        MaxSection(
            title = stringResource(R.string.max_touch_section_providers),
            description = stringResource(R.string.max_touch_section_providers_desc)
        ) {
            MaxGroup {
                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_touch_node_game),
                        value = gameNode?.path,
                        trust = if (gameNode != null) MaxDataTrust.Snapshot else MaxDataTrust.Unsupported,
                        source = sysfsSource,
                        note = if (gameNode == null) missing else null
                    )
                )

                MaxGroupDivider()

                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_touch_node_sample),
                        value = sampleNode?.path,
                        trust = if (sampleNode != null) MaxDataTrust.Snapshot else MaxDataTrust.Unsupported,
                        source = sysfsSource,
                        note = if (sampleNode == null) missing else null
                    )
                )

                MaxGroupDivider()

                MaxMetricLine(
                    metric = MaxMetric(
                        label = stringResource(R.string.max_touch_node_dt2w),
                        value = dt2wNode?.path,
                        trust = if (dt2wNode != null) MaxDataTrust.Snapshot else MaxDataTrust.Unsupported,
                        source = sysfsSource,
                        note = if (dt2wNode == null) missing else null
                    )
                )

                if (halOnly) {
                    MaxGroupDivider()
                    MaxMetricLine(
                        metric = MaxMetric(
                            label = stringResource(R.string.max_touch_provider_hal),
                            value = halSource,
                            trust = MaxDataTrust.Snapshot,
                            source = halSource,
                            note = stringResource(R.string.max_touch_provider_hal_detail)
                        )
                    )
                }
            }
        }

        MaxSection(title = stringResource(R.string.max_touch_section_about)) {
            MaxGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaxSpace.rowPaddingHorizontal,
                            vertical = MaxSpace.rowPaddingVertical
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
                ) {
                    Text(
                        text = stringResource(R.string.touch_boost_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.touch_boost_node_note,
                            gameNode?.path ?: missing,
                            sampleNode?.path ?: missing
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.touch_engine_safety_title),
                    subtitle = stringResource(R.string.touch_engine_safety_desc),
                    icon = Icons.Rounded.Shield,
                    iconTone = MaxTone.Caution
                )
            }
        }
    }
}
