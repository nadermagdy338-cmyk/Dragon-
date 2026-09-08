@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package nd.max.ui.mainscreens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.AppIconImage
import nd.max.ui.viewmodel.ApplistViewmodel

@Composable
fun ApplistScreen(navController: NavController) {
    val viewModel: ApplistViewmodel = viewModel()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // The ViewModel doesn't track search-mode itself (it only tracks the
    // query text), so that bit of pure UI state lives locally in the screen.
    var isSearchMode by remember { mutableStateOf(false) }

    val filteredApps = viewModel.filteredApps
    val allApps = ApplistViewmodel.apps
    val totalApps = allApps.size
    val customizedApps = allApps.count { it.isEnabledInConfig }
    val recommendedApps = allApps.count { it.isRecommended }
    val systemApps = allApps.count { it.isSystem }

    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ApplistTopAppBar(
                scrollBehavior = scrollBehavior,
                isSearchMode = isSearchMode,
                onSearchModeChange = { active ->
                    isSearchMode = active
                    if (!active) viewModel.clearSearch()
                },
                searchQuery = viewModel.searchTextFieldValue,
                onSearchChange = { viewModel.updateSearch(it) },
                showSystemApps = viewModel.showSystemApps,
                onToggleSystem = { viewModel.showSystemApps = !viewModel.showSystemApps },
                onRefresh = { viewModel.loadApps(context, forceRefresh = true) },
                focusRequester = focusRequester
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.isRefreshing && filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.applist_loading_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.applist_loading_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                viewModel.loadError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.applist_error_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = viewModel.loadError ?: stringResource(R.string.applist_error_desc),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadApps(context, forceRefresh = true) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        val isFiltering = isSearchMode && viewModel.searchQuery.isNotEmpty()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(
                                    if (isFiltering) R.string.applist_no_results_title else R.string.applist_empty_title
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(
                                    if (isFiltering) R.string.applist_no_results_desc else R.string.applist_empty_desc
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        state = pullToRefreshState,
                        isRefreshing = viewModel.isRefreshing,
                        onRefresh = { viewModel.loadApps(context, forceRefresh = true) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isSearchMode) {
                                item {
                                    AppListOverview(
                                        total = totalApps,
                                        customized = customizedApps,
                                        recommended = recommendedApps,
                                        onClear = { viewModel.appFilter = ApplistViewmodel.AppFilter.ALL }
                                    )
                                }
                                item {
                                    AppFilterRow(
                                        selected = viewModel.appFilter,
                                        total = totalApps,
                                        customized = customizedApps,
                                        recommended = recommendedApps,
                                        system = systemApps,
                                        onSelected = { viewModel.appFilter = it }
                                    )
                                }
                            }
                            items(
                                items = filteredApps,
                                key = { it.packageName }
                            ) { app ->
                                ApplistItem(
                                    app = app,
                                    onClick = { navController.navigate("app_settings/${app.packageName}") }
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
private fun ApplistTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    isSearchMode: Boolean,
    onSearchModeChange: (Boolean) -> Unit,
    searchQuery: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    showSystemApps: Boolean,
    onToggleSystem: () -> Unit,
    onRefresh: () -> Unit,
    focusRequester: FocusRequester
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            if (isSearchMode) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (searchQuery.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_apps),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(stringResource(R.string.applist_title), fontWeight = FontWeight.SemiBold)
            }
        },
        navigationIcon = {
            if (isSearchMode) {
                IconButton(onClick = {
                    onSearchModeChange(false)
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            }
        },
        actions = {
            if (isSearchMode) {
                if (searchQuery.text.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange(TextFieldValue("")) }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear))
                    }
                }
            } else {
                IconButton(onClick = { onSearchModeChange(true) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                }
                IconButton(onClick = onToggleSystem) {
                    Icon(
                        imageVector = if (showSystemApps) Icons.Filled.Check else Icons.Filled.Apps,
                        contentDescription = stringResource(R.string.menu_show_system_apps),
                        tint = if (showSystemApps) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                }
            }
        }
    )
}

@Composable
private fun AppListOverview(
    total: Int,
    customized: Int,
    recommended: Int,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.applist_overview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.applist_view_all))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.applist_customized_summary, customized, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.applist_overview_summary, customized, recommended),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppFilterRow(
    selected: ApplistViewmodel.AppFilter,
    total: Int,
    customized: Int,
    recommended: Int,
    system: Int,
    onSelected: (ApplistViewmodel.AppFilter) -> Unit
) {
    val entries = remember(total, customized, recommended, system) {
        listOf(
            Triple(ApplistViewmodel.AppFilter.ALL, R.string.applist_filter_all, total),
            Triple(ApplistViewmodel.AppFilter.CUSTOMIZED, R.string.applist_filter_customized, customized),
            Triple(ApplistViewmodel.AppFilter.RECOMMENDED, R.string.applist_filter_recommended, recommended),
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
                    Text(stringResource(R.string.applist_filter_count, stringResource(labelRes), count))
                }
            )
        }
    }
}

@Composable
private fun ApplistItem(
    app: ApplistViewmodel.AppInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(app = app, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (app.isEnabledInConfig) {
                Spacer(Modifier.width(8.dp))
                LabelText(text = "${app.customizedCount}", color = MaterialTheme.colorScheme.primary)
            }
            if (app.isRecommended) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.applist_filter_recommended),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
