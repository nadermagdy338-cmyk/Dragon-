@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package nd.max.ui.mainscreens
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxSection
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.component.AppIconImage
import nd.max.ui.component.MaxEmptyState
import nd.max.ui.component.MaxErrorState
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.ui.viewmodel.ApplistViewmodel

@Composable
fun ApplistScreen(navController: NavHostController) {
    val viewModel: ApplistViewmodel = viewModel()
    val context = LocalContext.current
    val allApps = ApplistViewmodel.apps
    val filteredApps = viewModel.filteredApps
    val totalApps = allApps.size
    val customizedApps = allApps.count { it.isEnabledInConfig }
    val gameApps = allApps.count { it.isRecommended }
    val systemApps = allApps.count { it.isSystem }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ApplistViewmodel.apps.isNotEmpty()) viewModel.refreshAppConfigStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.applist_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (totalApps > 0) {
                            Text(
                                stringResource(R.string.applist_app_count, totalApps),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    MaxHelpAction(
                        title = stringResource(R.string.applist_hero_title),
                        body = stringResource(R.string.applist_hero_desc),
                    )
                    IconButton(
                        onClick = { viewModel.loadApps(context, forceRefresh = true) },
                        enabled = !viewModel.isRefreshing
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when {
            viewModel.isRefreshing && allApps.isEmpty() -> {
                AppListLoadingState(modifier = Modifier.padding(padding))
            }

            viewModel.loadError != null -> {
                MaxErrorState(
                    title = stringResource(R.string.applist_error_title),
                    message = viewModel.loadError ?: stringResource(R.string.applist_error_desc),
                    retryLabel = stringResource(R.string.retry),
                    onRetry = { viewModel.loadApps(context, forceRefresh = true) },
                    modifier = Modifier.padding(padding)
                )
            }

            else -> {
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.loadApps(context, forceRefresh = true) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().maxAdaptiveContentWidth(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 24.dp,
                            end = 16.dp,
                            bottom = 144.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // The hero card and the "app behaviour in the system" bridge were
                        // removed: the filter row below already carries every count they
                        // showed, and the page explanation now lives in the top-bar help
                        // action instead of a permanent card.
                        item {
                            AppSearchField(
                                query = viewModel.searchTextFieldValue,
                                onQueryChange = viewModel::updateSearch,
                                onClear = viewModel::clearSearch
                            )
                        }
                        item {
                            AppFilterRow(
                                selected = viewModel.appFilter,
                                total = totalApps,
                                customized = customizedApps,
                                games = gameApps,
                                system = systemApps,
                                onSelected = { viewModel.appFilter = it }
                            )
                        }
                        item {
                            AppResultsBar(
                                count = filteredApps.size,
                                sort = viewModel.appSort,
                                onSort = { viewModel.appSort = it }
                            )
                        }

                        item(key = "apps_workspace_links") {
                            WorkspaceLinks(
                                onOpen = { MaxNavActions(navController).navigateTo(it) }
                            )
                        }

                        if (filteredApps.isEmpty()) {
                            item {
                                MaxEmptyState(
                                    title = stringResource(
                                        if (viewModel.searchQuery.isNotBlank() || viewModel.appFilter != ApplistViewmodel.AppFilter.ALL) R.string.applist_no_results_title
                                        else R.string.applist_empty_title
                                    ),
                                    message = stringResource(
                                        if (viewModel.searchQuery.isNotBlank() || viewModel.appFilter != ApplistViewmodel.AppFilter.ALL) R.string.applist_no_results_desc
                                        else R.string.applist_empty_desc
                                    ),
                                    actionLabel = if (viewModel.searchQuery.isNotBlank() || viewModel.appFilter != ApplistViewmodel.AppFilter.ALL) stringResource(R.string.applist_clear_filters) else null,
                                    onAction = if (viewModel.searchQuery.isNotBlank() || viewModel.appFilter != ApplistViewmodel.AppFilter.ALL) {
                                        {
                                            viewModel.clearSearch()
                                            viewModel.appFilter = ApplistViewmodel.AppFilter.ALL
                                        }
                                    } else null
                                )
                            }
                        } else {
                            items(filteredApps, key = { it.packageName }) { app ->
                                ApplistItem(
                                    app = app,
                                    onClick = {
                                        MaxNavActions(navController).openApp(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(76.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 8.dp
            )
            Text(
                text = stringResource(R.string.applist_loading_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = stringResource(R.string.applist_loading_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppSearchField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_apps)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.text.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.cd_clear)
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

@Composable
private fun AppFilterRow(
    selected: ApplistViewmodel.AppFilter,
    total: Int,
    customized: Int,
    games: Int,
    system: Int,
    onSelected: (ApplistViewmodel.AppFilter) -> Unit
) {
    val entries = remember(total, customized, games, system) {
        listOf(
            Triple(ApplistViewmodel.AppFilter.ALL, R.string.applist_filter_all, total),
            Triple(ApplistViewmodel.AppFilter.RECOMMENDED, R.string.applist_filter_games, games),
            Triple(
                ApplistViewmodel.AppFilter.CUSTOMIZED,
                R.string.applist_filter_customized,
                customized
            ),
            Triple(ApplistViewmodel.AppFilter.SYSTEM, R.string.applist_filter_system, system)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.forEach { (filter, labelRes, count) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        stringResource(
                            R.string.applist_filter_count,
                            stringResource(labelRes),
                            count
                        )
                    )
                },
                leadingIcon = if (selected == filter) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun AppResultsBar(
    count: Int,
    sort: ApplistViewmodel.AppSort,
    onSort: (ApplistViewmodel.AppSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.applist_results_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.applist_results_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(sort.labelRes))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ApplistViewmodel.AppSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        leadingIcon = if (sort == option) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            onSort(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private val ApplistViewmodel.AppSort.labelRes: Int
    get() = when (this) {
        ApplistViewmodel.AppSort.SMART -> R.string.applist_sort_smart
        ApplistViewmodel.AppSort.NAME -> R.string.applist_sort_name
        ApplistViewmodel.AppSort.CUSTOMIZATION -> R.string.applist_sort_customized
    }


@Composable
private fun ApplistItem(app: ApplistViewmodel.AppInfo, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .42f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(app = app, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    when {
                        app.isRecommended -> {
                            Spacer(Modifier.width(7.dp))
                            AppTypeTag(stringResource(R.string.applist_tag_game), colors.tertiary)
                        }

                        app.isSystem -> {
                            Spacer(Modifier.width(7.dp))
                            AppTypeTag(stringResource(R.string.applist_tag_system), colors.secondary)
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (app.isEnabledInConfig) {
                        stringResource(R.string.applist_overrides_count, app.customizedCount)
                    } else {
                        stringResource(R.string.applist_default_settings)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (app.isEnabledInConfig) colors.primary else colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppTypeTag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = .12f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
/**
 * Workspace rows for the Apps destination. Keeps Process manager and
 * Debloat & freeze reachable from Apps itself instead of only through the
 * legacy tweaks screen (F-04).
 */
@Composable
private fun WorkspaceLinks(onOpen: (MaxDestination) -> Unit) {
    val links = MaxDestination.All.filter {
        it.parent == MaxDestination.Apps &&
            it != MaxDestination.AppSettings &&
            it != MaxDestination.ProcessManager &&
            it != MaxDestination.DebloatFreeze
    }
    if (links.isEmpty()) return
    MaxSection(title = stringResource(R.string.max_nav_apps)) {
        MaxGroup {
            links.forEachIndexed { index, destination ->
                if (index > 0) MaxGroupDivider()
                MaxRow(
                    title = stringResource(destination.titleRes),
                    icon = destination.icon,
                    onClick = { onOpen(destination) },
                )
            }
        }
    }
}
