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

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.MaxSwitch
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.util.DozeAppInfo
import nd.max.ui.util.DozeState
import nd.max.ui.util.GmsDozeMode
import nd.max.ui.viewmodel.DozeAppTab
import nd.max.ui.viewmodel.DozeModeViewModel

/**
 * Doze / idle.
 *
 * Rebuilt on the MaxManager Design Language. What changed and why:
 *
 *  - The old overview was a tertiaryContainer card with an [AssistChip] whose
 *    onClick was empty: a control-looking element that did nothing. State is
 *    now presented as readings, and controls are the only tappable things.
 *  - Idle counters and the controller state come from one on-demand read, so
 *    they are labelled Snapshot with their source (deviceidle) instead of
 *    looking like live telemetry. A refresh action re-reads them.
 *  - Ownership is now explicit: Android owns the state, MaxManager owns the
 *    policy switches. The old copy mixed both in one card.
 *  - The four manual commands were unlabelled buttons in a 2x2 grid; each is
 *    now a row that says what it actually does to the idle state machine.
 *  - App rows: the whole row is one 48dp toggle target with a spoken state,
 *    instead of a clickable row plus an independently clickable switch.
 *  - Hardcoded English UI copy moved to string resources.
 */
@Composable
fun DozeModeScreen(
    navController: NavController,
    viewModel: DozeModeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.loadState(context) }

    val stateLabel = when (viewModel.dozeState) {
        DozeState.IDLE -> stringResource(R.string.dozemode_state_idle)
        DozeState.IDLE_PENDING -> stringResource(R.string.dozemode_state_idle_pending)
        DozeState.SENSING -> stringResource(R.string.dozemode_state_sensing)
        DozeState.LOCATING -> stringResource(R.string.dozemode_state_locating)
        DozeState.MAINTENANCE -> stringResource(R.string.dozemode_state_maintenance)
        DozeState.ACTIVE -> stringResource(R.string.dozemode_state_active)
        DozeState.UNKNOWN -> stringResource(R.string.dozemode_state_unknown)
    }
    val unknownState = viewModel.dozeState == DozeState.UNKNOWN
    val source = stringResource(R.string.max_doze_source_deviceidle)

    val gmsLabels = listOf(
        stringResource(R.string.dozemode_gms_default),
        stringResource(R.string.dozemode_gms_standard),
        stringResource(R.string.dozemode_gms_aggressive)
    )
    val tabLabels = listOf(
        stringResource(R.string.dozemode_tab_all),
        stringResource(R.string.dozemode_tab_whitelist)
    )

    val condition = when (viewModel.isAvailable) {
        null -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_doze_probe_title),
            detail = stringResource(R.string.max_doze_probe_detail)
        )

        false -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = stringResource(R.string.max_doze_unsupported_title),
            detail = stringResource(R.string.dozemode_unavailable),
            primaryActionLabel = stringResource(R.string.max_action_recheck),
            onPrimaryAction = { viewModel.loadState(context) }
        )

        else -> null
    }

    ScreenAccentProvider(scheme.tertiary) {
        MaxListScreen(
            title = stringResource(R.string.dozemode_title),
            onBack = { navController.popBackStack() },
            subtitle = if (viewModel.isAvailable == true) stateLabel else null,
            accentIcon = Icons.Rounded.Bedtime,
            accent = scheme.tertiary,
            condition = condition,
            actions = {
                IconButton(onClick = { viewModel.loadState(context) }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.max_action_refresh)
                    )
                }
            },
            header = {
                MaxSection(
                    title = stringResource(R.string.max_doze_section_state),
                    description = stringResource(R.string.max_doze_section_state_desc)
                ) {
                    MaxGroup {
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.max_doze_metric_state),
                                value = if (unknownState) null else stateLabel,
                                // Unknown is a real read failure, not a value.
                                trust = if (unknownState) MaxDataTrust.Unreadable else MaxDataTrust.Snapshot,
                                source = source
                            )
                        )
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.max_doze_metric_idle_cycles),
                                value = viewModel.stats.idleCount.toString(),
                                trust = MaxDataTrust.Snapshot,
                                source = source
                            )
                        )
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.max_doze_metric_light_idle),
                                value = viewModel.stats.lightIdleCount.toString(),
                                trust = MaxDataTrust.Snapshot,
                                source = source
                            )
                        )
                    }
                }

                MaxSection(
                    title = stringResource(R.string.max_doze_section_policy),
                    description = stringResource(R.string.max_doze_section_policy_desc)
                ) {
                    MaxGroup {
                        MaxSwitchRow(
                            title = stringResource(R.string.dozemode_enable),
                            subtitle = stringResource(R.string.dozemode_enable_desc),
                            checked = viewModel.settings.isEnabled,
                            onCheckedChange = viewModel::setDozeEnabled,
                            icon = Icons.Rounded.PowerSettingsNew,
                            iconTone = MaxTone.Accent,
                            onStateDescription = stringResource(R.string.max_doze_policy_on),
                            offStateDescription = stringResource(R.string.max_doze_policy_off)
                        )
                        MaxGroupDivider()
                        MaxSwitchRow(
                            title = stringResource(R.string.dozemode_aggressive),
                            subtitle = stringResource(R.string.dozemode_aggressive_desc),
                            checked = viewModel.settings.isAggressive,
                            onCheckedChange = viewModel::setAggressiveDoze,
                            icon = Icons.Rounded.Speed,
                            enabled = viewModel.settings.isEnabled,
                            lockedReason = stringResource(R.string.max_doze_aggressive_locked),
                            onStateDescription = stringResource(R.string.max_doze_policy_on),
                            offStateDescription = stringResource(R.string.max_doze_policy_off)
                        )
                    }
                }

                MaxSection(
                    title = stringResource(R.string.dozemode_gms_title),
                    description = stringResource(R.string.dozemode_gms_desc)
                ) {
                    MaxSegmented(
                        options = gmsLabels,
                        selectedIndex = viewModel.gmsMode.ordinal,
                        onSelect = { index -> viewModel.setGmsDozeMode(GmsDozeMode.entries[index]) }
                    )
                }

                MaxSection(
                    title = stringResource(R.string.max_doze_section_actions),
                    description = stringResource(R.string.max_doze_section_actions_desc)
                ) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.dozemode_action_force_idle),
                            subtitle = stringResource(R.string.max_doze_action_force_idle_desc),
                            icon = Icons.Rounded.Bedtime,
                            iconTone = MaxTone.Accent,
                            onClick = viewModel::forceIdle
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.dozemode_action_step),
                            subtitle = stringResource(R.string.max_doze_action_step_desc),
                            icon = Icons.Rounded.SkipNext,
                            onClick = viewModel::stepIdleState
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.dozemode_action_unforce),
                            subtitle = stringResource(R.string.max_doze_action_unforce_desc),
                            icon = Icons.Rounded.PlayArrow,
                            onClick = viewModel::unforceIdle
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.dozemode_action_reset_stats),
                            subtitle = stringResource(R.string.max_doze_action_reset_desc),
                            icon = Icons.Rounded.RestartAlt,
                            iconTone = MaxTone.Caution,
                            onClick = viewModel::resetStats
                        )
                    }
                }

                MaxSection(
                    title = stringResource(R.string.dozemode_section_whitelist),
                    description = stringResource(R.string.max_doze_whitelist_desc)
                ) {
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        shape = RoundedCornerShape(MaxRadius.row)
                    )
                    MaxSegmented(
                        options = tabLabels,
                        selectedIndex = if (viewModel.selectedTab == DozeAppTab.ALL) 0 else 1,
                        onSelect = { index ->
                            viewModel.selectedTab =
                                if (index == 0) DozeAppTab.ALL else DozeAppTab.WHITELISTED
                        }
                    )
                }
            }
        ) {
            if (viewModel.isAvailable == true) {
                if (viewModel.isAppsLoading) {
                    item(key = "doze_apps_loading") {
                        MaxConditionNotice(
                            condition = MaxCondition(
                                kind = MaxConditionKind.Loading,
                                title = stringResource(R.string.max_doze_apps_loading_title),
                                detail = stringResource(R.string.max_doze_apps_loading_detail)
                            )
                        )
                    }
                } else if (viewModel.filteredApps.isEmpty()) {
                    item(key = "doze_apps_empty") {
                        MaxConditionNotice(
                            condition = MaxCondition(
                                kind = MaxConditionKind.Empty,
                                title = stringResource(R.string.max_doze_apps_empty_title),
                                detail = stringResource(R.string.dozemode_no_apps_found)
                            )
                        )
                    }
                } else {
                    items(viewModel.filteredApps, key = { it.packageName }) { app ->
                        DozeAppRow(app) { checked ->
                            viewModel.setWhitelisted(context, app, checked)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DozeAppRow(
    app: DozeAppInfo,
    onCheckedChange: (Boolean) -> Unit
) {
    val bitmap = remember(app.packageName, app.icon) {
        val drawable = app.icon ?: return@remember null
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    val exemptLabel = stringResource(R.string.max_doze_app_exempt_on)
    val optimisedLabel = stringResource(R.string.max_doze_app_exempt_off)
    val spokenState = if (app.isWhitelisted) exemptLabel else optimisedLabel

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.row),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaxSize.minTouchTarget)
                .toggleable(
                    value = app.isWhitelisted,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .semantics { stateDescription = spokenState }
                .padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.rowPaddingVertical
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(MaxSize.iconContainer)
                        .clip(RoundedCornerShape(MaxRadius.control))
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(MaxSize.iconContainer)
                )
            }

            Spacer(Modifier.width(MaxSpace.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(MaxSpace.md))

            MaxSwitch(
                checked = app.isWhitelisted,
                onCheckedChange = null
            )
        }
    }
}
