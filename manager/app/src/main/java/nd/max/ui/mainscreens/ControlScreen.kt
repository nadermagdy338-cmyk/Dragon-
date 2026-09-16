/*
 * The Control primary destination.
 *
 * Product shape: this is the old tuning workspace, kept in the new design
 * system. Grouped sections, one row per destination, a short line under each
 * title — the "outer" feel of the all-tweaks list, with none of its flat
 * dead-end toggles.
 *
 * Three things below are deliberate:
 *
 * 1. The screen opens straight into its sections. The explanatory header card
 *    ("global control" + scope paragraph) is gone: it pushed every real control
 *    below the fold, and the same copy already lives behind the top-bar help
 *    action.
 * 2. A two-name layout switch next to the help action flips between the grouped
 *    list and cards. It changes presentation ONLY — both layouts render the
 *    same destinations in the same order, so switching never changes what the
 *    screen contains.
 * 3. The advanced tools carry no risk chips. They are still gated in Settings
 *    (ADR-16); on this screen the label was noise repeated on every row.
 */
package nd.max.ui.mainscreens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.LeadingIcon
import nd.max.ui.component.rememberConfigBackupFlow
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxViewToggle
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.subscreens.hubs.maxHubDescription
import nd.max.ui.viewmodel.TweakViewModel

/** Layout preference store — the same "settings" file the rest of the UI uses. */
private const val CONTROL_PREFS = "settings"
private const val CONTROL_LAYOUT_KEY = "control_layout_cards"

@Composable
fun ControlScreen(navController: NavHostController) {
    val actions = MaxNavActions(navController)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel: TweakViewModel = viewModel()

    val prefs = remember(context) { context.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE) }
    var cardsLayout by rememberSaveable {
        mutableStateOf(prefs.getBoolean(CONTROL_LAYOUT_KEY, false))
    }

    val backupFlow = rememberConfigBackupFlow(viewModel) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val hubs = MaxDestination.All.filter { it.parent == MaxDestination.Control }
    val sections = groupedControlDomains(hubs)
    val listLabel = stringResource(R.string.control_view_list)
    val cardsLabel = stringResource(R.string.control_view_cards)

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        subtitle = stringResource(R.string.control_workspace_subtitle),
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxViewToggle(
                labels = listOf(listLabel, cardsLabel),
                selectedIndex = if (cardsLayout) 1 else 0,
                onSelect = { index ->
                    cardsLayout = index == 1
                    prefs.edit().putBoolean(CONTROL_LAYOUT_KEY, cardsLayout).apply()
                }
            )
            MaxHelpAction(
                title = stringResource(R.string.control_map_title),
                body = stringResource(R.string.control_map_desc),
            )
            IconButton(onClick = backupFlow::showSheet) {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = stringResource(R.string.cd_backup_restore),
                )
            }
        },
    ) {
        sections.forEach { group ->
            item(key = group.key) {
                ControlSection(
                    titleRes = group.titleRes,
                    entries = group.entries,
                    asCards = cardsLayout,
                    onOpen = actions::navigateTo,
                )
            }
        }

        item(key = "control_tools") {
            ControlSection(
                titleRes = R.string.max_nav_advanced_tools,
                entries = controlTools(),
                asCards = cardsLayout,
                onOpen = actions::navigateTo,
            )
        }
    }
}

/**
 * One titled band of destinations. Both layouts draw the same [entries] in the
 * same order; only the presentation changes.
 */
@Composable
private fun ControlSection(
    @StringRes titleRes: Int,
    entries: List<ControlEntry>,
    asCards: Boolean,
    onOpen: (MaxDestination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TweaksSectionTitle(text = stringResource(titleRes))
        if (asCards) {
            ControlCardGrid(entries = entries, onOpen = onOpen)
        } else {
            ExpressiveList(content = controlRows(entries = entries, onOpen = onOpen))
        }
    }
}

/** Grouped-list rows: the layout the old tuning workspace used. */
private fun controlRows(
    entries: List<ControlEntry>,
    onOpen: (MaxDestination) -> Unit,
): List<@Composable () -> Unit> = entries.map { entry ->
    { ControlRow(entry = entry, onOpen = onOpen) }
}

@Composable
private fun ControlRow(entry: ControlEntry, onOpen: (MaxDestination) -> Unit) {
    ExpressiveListItem(
        leadingContent = { LeadingIcon(icon = entry.destination.icon) },
        onClick = { onOpen(entry.destination) },
        headlineContent = { Text(stringResource(entry.destination.titleRes)) },
        supportingContent = { Text(stringResource(entry.subtitleRes)) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
    )
}

/** Card layout: two per row, same titles and subtitles as the list. */
@Composable
private fun ControlCardGrid(
    entries: List<ControlEntry>,
    onOpen: (MaxDestination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
        entries.chunked(2).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                rowEntries.forEach { entry ->
                    ControlCard(
                        entry = entry,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowEntries.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlCard(
    entry: ControlEntry,
    onOpen: (MaxDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 108.dp)
            .clickable { onOpen(entry.destination) },
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border),
        ),
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        ) {
            LeadingIcon(icon = entry.destination.icon)
            Text(
                text = stringResource(entry.destination.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(entry.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A destination plus the one line of copy that explains it on this screen. */
private data class ControlEntry(
    val destination: MaxDestination,
    @StringRes val subtitleRes: Int,
)

private data class ControlGroupSpec(
    val key: String,
    @StringRes val titleRes: Int,
    val entries: List<ControlEntry>,
)

private fun groupedControlDomains(hubs: List<MaxDestination>): List<ControlGroupSpec> = listOf(
    ControlGroupSpec(
        key = "control_group_performance",
        titleRes = R.string.control_group_performance,
        entries = hubs.filter {
            it in listOf(
                MaxDestination.CpuHub,
                MaxDestination.GpuHub,
                MaxDestination.MemoryHub,
                MaxDestination.ResponsivenessHub,
            )
        }.map(::hubEntry),
    ),
    ControlGroupSpec(
        key = "control_group_environment",
        titleRes = R.string.control_group_environment,
        entries = hubs.filter {
            it in listOf(
                MaxDestination.ThermalHub,
                MaxDestination.PowerHub,
                MaxDestination.DisplayHub,
            )
        }.map(::hubEntry),
    ),
    ControlGroupSpec(
        key = "control_group_system",
        titleRes = R.string.control_group_system,
        entries = hubs.filter {
            it in listOf(
                MaxDestination.StorageHub,
                MaxDestination.NetworkHub,
            )
        }.map(::hubEntry),
    ),
).filter { it.entries.isNotEmpty() }

private fun hubEntry(destination: MaxDestination) =
    ControlEntry(destination, maxHubDescription(destination))

/** Every low-level tool, in one place (no more "advanced" vs "normal" split). */
private fun controlTools(): List<ControlEntry> = listOf(
    MaxDestination.Terminal,
    MaxDestination.SetEdit,
    MaxDestination.ActivityLauncher,
    MaxDestination.KernelFlasher,
).map { ControlEntry(it, controlToolRole(it)) }

@StringRes
private fun controlToolRole(destination: MaxDestination): Int = when (destination) {
    MaxDestination.Terminal -> R.string.max_role_terminal
    MaxDestination.SetEdit -> R.string.max_role_setedit
    MaxDestination.ActivityLauncher -> R.string.max_role_activity_launcher
    MaxDestination.KernelFlasher -> R.string.max_role_kernel_flasher
    else -> R.string.max_role_open_screen
}
