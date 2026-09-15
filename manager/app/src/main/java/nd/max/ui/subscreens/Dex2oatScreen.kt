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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.util.DebloatAppInfo
import nd.max.ui.util.Dex2oatUtil
import nd.max.ui.viewmodel.Dex2oatProgress
import nd.max.ui.viewmodel.Dex2oatResult
import nd.max.ui.viewmodel.Dex2oatTab
import nd.max.ui.viewmodel.Dex2oatViewModel

/** The four scope-wide operations, kept as one state so only one can be pending. */
private enum class Dex2oatBulk { COMPILE_ALL, COMPILE_SYSTEM, COMPILE_USER, RESET_ALL }

/**
 * ART compilation (dex2oat).
 *
 * Rebuilt on the MaxManager Design Language. What changed and why:
 *
 *  - This screen is a long list with a control panel on top, so it is built on
 *    MaxListScreen: the filter, scope and list controls are a header inside the
 *    same scroll, and only the app rows are lazy. The old version nested a
 *    scrolling column of cards around its own list, which made the app list
 *    scroll inside a scroll and rebuilt every row on each recomposition.
 *  - Compile filters were a row of chips labelled with bare ART tokens. Nobody
 *    outside the platform team knows what quicker means, so every filter now
 *    carries one line explaining the tradeoff, with the kernel token itself kept
 *    as the title because that is the value actually passed to cmd package.
 *  - The bulk actions used to run straight from a tap. They now go through the
 *    shared confirm dialog and state the exact command and the number of
 *    packages involved, because compiling everything can run for tens of
 *    minutes and heat the device.
 *  - The old screen discarded the util result entirely: the progress spinner
 *    vanished and the app implied success. The ViewModel now returns a real
 *    outcome, and this screen renders Applied or Failed, including partial batch
 *    failures with the count that failed.
 *  - Per-app actions are text buttons, not icon-only glyphs. Compile and reset
 *    are not guessable from a symbol, and the consequence is not reversible
 *    cheaply.
 *  - Search and tab filters no longer blank the screen when they match nothing.
 *    An inline notice explains why the list is empty and offers to clear it,
 *    which is different from having no apps at all.
 */
@Composable
fun Dex2oatScreen(
    navController: NavController,
    viewModel: Dex2oatViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (viewModel.allApps.isEmpty()) viewModel.loadApps(context)
    }

    val progress = viewModel.progress
    val result = viewModel.lastResult
    val busy = progress != null
    val apps = viewModel.filteredApps
    val filter = viewModel.selectedMode

    var pendingBulk by remember { mutableStateOf<Dex2oatBulk?>(null) }

    // Hoisted because the lazy content block is not a composable scope.
    val busyReason = stringResource(R.string.max_dex_applying_detail)
    val compileLabel = stringResource(R.string.dex2oat_action_compile)
    val resetLabel = stringResource(R.string.dex2oat_action_reset)
    val systemTag = stringResource(R.string.max_dex_tag_system)

    val condition = when {
        viewModel.isLoading && viewModel.allApps.isEmpty() -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_dex_loading_title),
            detail = stringResource(R.string.max_dex_loading_detail)
        )

        viewModel.allApps.isEmpty() -> MaxCondition(
            kind = MaxConditionKind.Empty,
            title = stringResource(R.string.max_dex_empty_title),
            detail = stringResource(R.string.max_dex_empty_detail),
            primaryActionLabel = stringResource(R.string.max_action_retry),
            onPrimaryAction = { viewModel.loadApps(context) }
        )

        else -> null
    }

    val banner = when {
        progress != null -> applyingCondition(progress)
        result != null -> resultCondition(result) { viewModel.clearResult() }
        else -> null
    }

    // Filtered-to-nothing is a different state from having no apps, so it gets
    // its own inline notice with a way out instead of an empty screen.
    val noMatch = MaxCondition(
        kind = MaxConditionKind.Empty,
        title = stringResource(R.string.max_dex_no_match_title),
        detail = stringResource(R.string.max_dex_no_match_detail),
        primaryActionLabel = stringResource(R.string.max_dex_search_clear),
        onPrimaryAction = { viewModel.updateSearch(TextFieldValue("")) }
    )

    MaxListScreen(
        title = stringResource(R.string.dex2oat_title),
        subtitle = stringResource(R.string.dex2oat_subtitle),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Layers,
        condition = condition,
        banner = banner,
        actions = {
            IconButton(onClick = { viewModel.loadApps(context) }, enabled = !busy) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.max_action_refresh)
                )
            }
        },
        header = {
            // ---- Compile filter ------------------------------------------------
            MaxSection(
                title = stringResource(R.string.dex2oat_mode_title),
                description = stringResource(R.string.max_dex_filter_desc)
            ) {
                MaxGroup {
                    Dex2oatUtil.COMPILE_MODES.forEachIndexed { index, mode ->
                        if (index > 0) MaxGroupDivider()
                        val explanation = filterDescription(mode)
                        MaxChoiceRow(
                            // The ART token is the title: it is what gets passed
                            // to cmd package compile -m.
                            title = mode,
                            subtitle = explanation?.let { stringResource(it) },
                            selected = filter == mode,
                            enabled = !busy,
                            lockedReason = if (busy) busyReason else null,
                            onSelect = { viewModel.onModeSelected(mode) }
                        )
                    }
                }
            }

            // ---- Scope-wide actions --------------------------------------------
            MaxSection(
                title = stringResource(R.string.max_dex_scope_title),
                description = stringResource(R.string.max_dex_scope_desc)
            ) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.dex2oat_action_compile_all),
                        subtitle = stringResource(R.string.max_dex_bulk_warning),
                        icon = Icons.Rounded.Bolt,
                        iconTone = MaxTone.Caution,
                        enabled = !busy,
                        onClick = { pendingBulk = Dex2oatBulk.COMPILE_ALL }
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.dex2oat_action_compile_system),
                        subtitle = stringResource(
                            R.string.max_dex_apps_count,
                            viewModel.systemCount.toString(),
                            viewModel.totalCount.toString()
                        ),
                        icon = Icons.Rounded.Android,
                        iconTone = MaxTone.Neutral,
                        enabled = !busy,
                        onClick = { pendingBulk = Dex2oatBulk.COMPILE_SYSTEM }
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.dex2oat_action_compile_user),
                        subtitle = stringResource(
                            R.string.max_dex_apps_count,
                            viewModel.userCount.toString(),
                            viewModel.totalCount.toString()
                        ),
                        icon = Icons.Rounded.Apps,
                        iconTone = MaxTone.Neutral,
                        enabled = !busy,
                        onClick = { pendingBulk = Dex2oatBulk.COMPILE_USER }
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.dex2oat_action_reset_all),
                        icon = Icons.Rounded.RestartAlt,
                        iconTone = MaxTone.Critical,
                        enabled = !busy,
                        onClick = { pendingBulk = Dex2oatBulk.RESET_ALL }
                    )
                }
            }

            // ---- List controls --------------------------------------------------
            MaxSection(
                title = stringResource(R.string.max_dex_list_title),
                description = stringResource(
                    R.string.max_dex_apps_count,
                    apps.size.toString(),
                    viewModel.totalCount.toString()
                )
            ) {
                MaxSegmented(
                    options = listOf(
                        stringResource(R.string.dex2oat_tab_all),
                        stringResource(R.string.dex2oat_tab_user),
                        stringResource(R.string.dex2oat_tab_system)
                    ),
                    selectedIndex = viewModel.selectedTab.ordinal,
                    onSelect = { viewModel.selectedTab = Dex2oatTab.values()[it] },
                    enabled = !busy
                )

                val hasQuery = viewModel.searchQuery.text.isNotEmpty()
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.max_dex_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (hasQuery) {
                            IconButton(onClick = { viewModel.updateSearch(TextFieldValue("")) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.max_dex_search_clear)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(MaxRadius.control)
                )
            }
        }
    ) {
        if (apps.isEmpty()) {
            item { MaxConditionNotice(noMatch) }
        } else {
            itemsIndexed(items = apps, key = { _, app -> app.packageName }) { index, app ->
                Dex2oatAppRow(
                    app = app,
                    compileLabel = compileLabel,
                    resetLabel = resetLabel,
                    systemTag = systemTag,
                    enabled = !busy,
                    onCompile = { viewModel.compileApp(app) },
                    onReset = { viewModel.resetApp(app) }
                )
                if (index < apps.lastIndex) {
                    MaxGroupDivider()
                }
            }
        }
    }

    val bulk = pendingBulk
    if (bulk != null) {
        MaxConfirmDialog(
            visible = true,
            title = stringResource(bulkTitleRes(bulk)),
            message = stringResource(bulkMessageRes(bulk)),
            technicalDetail = bulkCommand(bulk, filter),
            confirmLabel = stringResource(bulkActionRes(bulk)),
            icon = bulkIcon(bulk),
            destructive = bulk == Dex2oatBulk.RESET_ALL || bulk == Dex2oatBulk.COMPILE_ALL,
            onConfirm = {
                when (bulk) {
                    Dex2oatBulk.COMPILE_ALL -> viewModel.compileAll()
                    Dex2oatBulk.COMPILE_SYSTEM -> viewModel.compileSystemApps()
                    Dex2oatBulk.COMPILE_USER -> viewModel.compileUserApps()
                    Dex2oatBulk.RESET_ALL -> viewModel.resetAllApps()
                }
            },
            onDismiss = { pendingBulk = null }
        )
    }
}

/**
 * One installed app. The package name is shown in mono under the label because
 * two apps can carry the same display name, and the package is what the compile
 * command actually targets.
 */
@Composable
private fun Dex2oatAppRow(
    app: DebloatAppInfo,
    compileLabel: String,
    resetLabel: String,
    systemTag: String,
    enabled: Boolean,
    onCompile: () -> Unit,
    onReset: () -> Unit
) {
    // Keyed on the package so scrolling does not re-rasterize icons.
    val iconBitmap = remember(app.packageName) { app.icon?.toBitmap()?.asImageBitmap() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaxSize.minTouchTarget)
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(MaxSize.rowIconContainer)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                modifier = Modifier.size(MaxSize.rowIconContainer),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MonoValueStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.isSystem) {
                Text(
                    text = systemTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextButton(onClick = onCompile, enabled = enabled) {
            Text(text = compileLabel)
        }
        TextButton(onClick = onReset, enabled = enabled) {
            Text(text = resetLabel)
        }
    }
}

/** Each ART filter gets one line on what it trades away. */
private fun filterDescription(filter: String): Int? = when (filter) {
    "speed-profile" -> R.string.max_dex_filter_speed_profile_desc
    "speed" -> R.string.max_dex_filter_speed_desc
    "everything" -> R.string.max_dex_filter_everything_desc
    "quicker" -> R.string.max_dex_filter_quicker_desc
    "verify" -> R.string.max_dex_filter_verify_desc
    else -> null
}

private fun bulkTitleRes(bulk: Dex2oatBulk): Int = when (bulk) {
    Dex2oatBulk.COMPILE_ALL -> R.string.dex2oat_confirm_compile_all_title
    Dex2oatBulk.COMPILE_SYSTEM -> R.string.dex2oat_confirm_compile_system_title
    Dex2oatBulk.COMPILE_USER -> R.string.dex2oat_confirm_compile_user_title
    Dex2oatBulk.RESET_ALL -> R.string.dex2oat_confirm_reset_all_title
}

private fun bulkMessageRes(bulk: Dex2oatBulk): Int = when (bulk) {
    Dex2oatBulk.COMPILE_ALL -> R.string.dex2oat_confirm_compile_all_desc
    Dex2oatBulk.COMPILE_SYSTEM -> R.string.dex2oat_confirm_compile_system_desc
    Dex2oatBulk.COMPILE_USER -> R.string.dex2oat_confirm_compile_user_desc
    Dex2oatBulk.RESET_ALL -> R.string.dex2oat_confirm_reset_all_desc
}

private fun bulkActionRes(bulk: Dex2oatBulk): Int = when (bulk) {
    Dex2oatBulk.COMPILE_ALL -> R.string.dex2oat_action_compile_all
    Dex2oatBulk.COMPILE_SYSTEM -> R.string.dex2oat_action_compile_system
    Dex2oatBulk.COMPILE_USER -> R.string.dex2oat_action_compile_user
    Dex2oatBulk.RESET_ALL -> R.string.dex2oat_action_reset_all
}

private fun bulkIcon(bulk: Dex2oatBulk): ImageVector = when (bulk) {
    Dex2oatBulk.COMPILE_ALL -> Icons.Rounded.Bolt
    Dex2oatBulk.COMPILE_SYSTEM -> Icons.Rounded.Android
    Dex2oatBulk.COMPILE_USER -> Icons.Rounded.Apps
    Dex2oatBulk.RESET_ALL -> Icons.Rounded.RestartAlt
}

/** The literal command the confirmation is authorising. */
private fun bulkCommand(bulk: Dex2oatBulk, filter: String): String = when (bulk) {
    Dex2oatBulk.COMPILE_ALL -> "cmd package compile -m $filter -f -a"
    Dex2oatBulk.COMPILE_SYSTEM -> "cmd package compile -m $filter -f <system package>"
    Dex2oatBulk.COMPILE_USER -> "cmd package compile -m $filter -f <user package>"
    Dex2oatBulk.RESET_ALL -> "cmd package compile --reset -a"
}

@Composable
private fun applyingCondition(progress: Dex2oatProgress): MaxCondition = when (progress) {
    is Dex2oatProgress.Single -> MaxCondition(
        kind = MaxConditionKind.Applying,
        title = if (progress.resetting) {
            stringResource(R.string.max_dex_applying_reset_single, progress.label)
        } else {
            stringResource(R.string.max_dex_applying_single, progress.label)
        },
        detail = stringResource(R.string.max_dex_applying_detail)
    )

    is Dex2oatProgress.Batch -> MaxCondition(
        kind = MaxConditionKind.Applying,
        title = stringResource(R.string.max_dex_applying_single, progress.label),
        detail = stringResource(
            R.string.max_dex_applying_batch_detail,
            progress.current.toString(),
            progress.total.toString()
        )
    )

    Dex2oatProgress.CompilingAll -> MaxCondition(
        kind = MaxConditionKind.Applying,
        title = stringResource(R.string.max_dex_applying_all),
        detail = stringResource(R.string.max_dex_applying_detail)
    )

    Dex2oatProgress.ResettingAll -> MaxCondition(
        kind = MaxConditionKind.Applying,
        title = stringResource(R.string.max_dex_applying_reset_all),
        detail = stringResource(R.string.max_dex_applying_detail)
    )
}

/**
 * Turns the ViewModel outcome into an Applied or Failed notice. A batch with any
 * failures is reported as Failed with the count, because silently succeeding on
 * 300 apps and failing on 12 is not a success.
 */
@Composable
private fun resultCondition(
    result: Dex2oatResult,
    onDismiss: () -> Unit
): MaxCondition {
    val dismiss = stringResource(R.string.max_action_dismiss)
    val appliedTitle = stringResource(R.string.max_dex_applied_title)
    val failedTitle = stringResource(R.string.max_dex_failed_title)
    val failedHint = stringResource(R.string.max_dex_failed_detail)

    return when (result) {
        is Dex2oatResult.Single -> if (result.success) {
            MaxCondition(
                kind = MaxConditionKind.Applied,
                title = appliedTitle,
                detail = if (result.resetting) {
                    stringResource(R.string.max_dex_applied_reset_single, result.label)
                } else {
                    stringResource(R.string.max_dex_applied_single, result.label, result.filter)
                },
                primaryActionLabel = dismiss,
                onPrimaryAction = onDismiss
            )
        } else {
            MaxCondition(
                kind = MaxConditionKind.Failed,
                title = failedTitle,
                detail = if (result.resetting) {
                    stringResource(R.string.max_dex_failed_reset_single, result.label)
                } else {
                    stringResource(R.string.max_dex_failed_single, result.label)
                },
                technicalDetail = failedHint,
                primaryActionLabel = dismiss,
                onPrimaryAction = onDismiss
            )
        }

        is Dex2oatResult.Batch -> if (result.failed == 0) {
            MaxCondition(
                kind = MaxConditionKind.Applied,
                title = appliedTitle,
                detail = stringResource(
                    R.string.max_dex_applied_batch,
                    result.total.toString(),
                    result.filter
                ),
                primaryActionLabel = dismiss,
                onPrimaryAction = onDismiss
            )
        } else {
            MaxCondition(
                kind = MaxConditionKind.Failed,
                title = failedTitle,
                detail = stringResource(
                    R.string.max_dex_failed_batch,
                    result.failed.toString(),
                    result.total.toString()
                ),
                technicalDetail = failedHint,
                primaryActionLabel = dismiss,
                onPrimaryAction = onDismiss
            )
        }

        is Dex2oatResult.Bulk -> if (result.success) {
            MaxCondition(
                kind = MaxConditionKind.Applied,
                title = appliedTitle,
                detail = if (result.resetting) {
                    stringResource(R.string.max_dex_applied_reset_bulk)
                } else {
                    stringResource(R.string.max_dex_applied_bulk, result.filter)
                },
                primaryActionLabel = dismiss,
                onPrimaryAction = onDismiss
            )
        } else {
            MaxCondition(
                kind = MaxConditionKind.Failed,
                title = failedTitle,
                detail = stringResource(R.string.max_dex_failed_bulk),
                technicalDetail = failedHint,
                primaryActionLabel = dismiss,
                onPrimaryAction = onDismiss
            )
        }
    }
}
