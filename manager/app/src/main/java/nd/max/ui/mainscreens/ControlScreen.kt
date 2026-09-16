/*
 * The Control primary destination.
 *
 * Product shape: this is the old tuning workspace, kept in the new design
 * system. Grouped bands of destinations, one row per screen, a short line under
 * each title.
 *
 * Four things below are deliberate:
 *
 * 1. The screen opens straight into its bands. The explanatory header card
 *    ("global control" + scope paragraph) is gone: it pushed every real control
 *    below the fold, and the same copy already lives behind the top-bar help
 *    action.
 * 2. Two presentations, one model. `compact` lists the domains; `expanded` opens
 *    every domain out and shows each screen it owns, painted straight on the page
 *    — no group container inside a group container, which is the whole point of
 *    that mode. Both read [controlLayoutModel], so adding or removing a
 *    destination shows up in both at once.
 * 3. The layout switch is one top-bar icon that opens the two names as a menu
 *    (see MaxViewMenu); the two-chip control it replaced crowded the help and
 *    side actions on phones.
 * 4. The advanced tools carry no risk chips. They are still gated in Settings
 *    (ADR-16); on this screen the label was noise repeated on every row.
 */
package nd.max.ui.mainscreens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.LeadingIcon
import nd.max.ui.component.rememberConfigBackupFlow
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxViewMenu
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.viewmodel.TweakViewModel

/** Layout preference store — the same "settings" file the rest of the UI uses. */
private const val CONTROL_PREFS = "settings"

/** `false` (the default) is the compact list; `true` is the expanded page. */
private const val CONTROL_LAYOUT_KEY = "control_layout_expanded"

/** Index of the expanded option in the layout menu. */
private const val EXPANDED_INDEX = 1

@Composable
fun ControlScreen(navController: NavHostController) {
    val actions = MaxNavActions(navController)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel: TweakViewModel = viewModel()

    val prefs = remember(context) { context.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE) }
    var expandedLayout by rememberSaveable {
        mutableStateOf(prefs.getBoolean(CONTROL_LAYOUT_KEY, false))
    }

    val backupFlow = rememberConfigBackupFlow(viewModel) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // One model, two presentations. Nothing below decides what the page contains:
    // it only decides how much of it is opened out.
    val bands = controlLayoutModel()
    val tools = controlToolEntries()

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        subtitle = stringResource(R.string.control_workspace_subtitle),
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxViewMenu(
                labels = listOf(
                    stringResource(R.string.control_view_compact),
                    stringResource(R.string.control_view_expanded),
                ),
                selectedIndex = if (expandedLayout) EXPANDED_INDEX else 0,
                contentDescription = stringResource(R.string.control_view_switch_cd),
                onSelect = { index ->
                    expandedLayout = index == EXPANDED_INDEX
                    prefs.edit().putBoolean(CONTROL_LAYOUT_KEY, expandedLayout).apply()
                },
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
        bands.forEach { band ->
            item(key = band.key) {
                ControlBand(
                    titleRes = band.titleRes,
                    hubs = band.hubs,
                    expanded = expandedLayout,
                    onOpen = actions::navigateTo,
                )
            }
        }

        item(key = "control_tools") {
            ControlBand(
                titleRes = R.string.max_nav_advanced_tools,
                entries = tools,
                expanded = expandedLayout,
                onOpen = actions::navigateTo,
            )
        }
    }
}

/**
 * One titled band of the page, in either presentation.
 *
 * `hubs` are the domains (their own screens); `entries` are loose destinations
 * that belong to no domain, which is how the advanced-tools band renders.
 * Compact puts both into grouped rows; expanded opens the hubs out and paints
 * every row straight on the page.
 */
@Composable
private fun ControlBand(
    @StringRes titleRes: Int,
    expanded: Boolean,
    onOpen: (MaxDestination) -> Unit,
    hubs: List<ControlHubSpec> = emptyList(),
    entries: List<ControlEntry> = emptyList(),
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TweaksSectionTitle(text = stringResource(titleRes))

        if (expanded) {
            hubs.forEach { spec ->
                HubHeader(spec = spec, onOpen = onOpen)
                spec.features.forEachIndexed { index, feature ->
                    if (index > 0) MaxGroupDivider()
                    ExpandedRow(
                        entry = feature,
                        onOpen = onOpen,
                        // Indented under the hub's icon, so "which domain owns this
                        // row" is carried by alignment instead of another container.
                        modifier = Modifier.padding(start = MaxSize.iconGlyph + MaxSpace.md),
                    )
                }
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) MaxGroupDivider()
                ExpandedRow(entry = entry, onOpen = onOpen)
            }
        } else {
            val rows = buildList<@Composable () -> Unit> {
                hubs.forEach { spec ->
                    add {
                        ControlRow(
                            entry = ControlEntry(spec.hub, spec.hubSubtitleRes),
                            onOpen = onOpen,
                        )
                    }
                }
                entries.forEach { entry ->
                    add {
                        ControlRow(entry = entry, onOpen = onOpen)
                    }
                }
            }
            ExpressiveList(content = rows)
        }
    }
}

/** Grouped-list row: the compact presentation of any destination. */
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

/** Flat row on the expanded page. Nothing wraps it; the page is the container. */
@Composable
private fun ExpandedRow(
    entry: ControlEntry,
    onOpen: (MaxDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaxRow(
        title = stringResource(entry.destination.titleRes),
        subtitle = stringResource(entry.subtitleRes),
        icon = entry.destination.icon,
        iconTone = MaxTone.Accent,
        onClick = { onOpen(entry.destination) },
        modifier = modifier,
    )
}

/**
 * Hub header of the expanded page: names the domain, states its scope, and is
 * itself the link to the hub screen — where that domain's own switches live, so
 * the page never has to inline controls to stay complete.
 */
@Composable
private fun HubHeader(spec: ControlHubSpec, onOpen: (MaxDestination) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaxRadius.row))
            .clickable(role = Role.Button) { onOpen(spec.hub) }
            .padding(horizontal = MaxSpace.xs, vertical = MaxSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        Icon(
            imageVector = spec.hub.icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(MaxSize.iconGlyph),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(spec.hub.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                text = stringResource(spec.hubSubtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MaxSize.iconGlyphSmall),
        )
    }
}
