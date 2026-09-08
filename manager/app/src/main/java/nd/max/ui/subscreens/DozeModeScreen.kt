@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import nd.max.ui.component.MaxSwitch
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.MaxUiMetrics
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.ExpressiveSwitchItem
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.util.DozeAppInfo
import nd.max.ui.util.DozeState
import nd.max.ui.util.GmsDozeMode
import nd.max.ui.viewmodel.DozeAppTab
import nd.max.ui.viewmodel.DozeModeViewModel

@Composable
fun DozeModeScreen(
    navController: NavController,
    viewModel: DozeModeViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.loadState(context) }

    ScreenAccentProvider(scheme.tertiary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = stringResource(R.string.dozemode_title),
                    onBack = { navController.popBackStack() },
                    accentIcon = Icons.Filled.Bedtime,
                    accent = scheme.tertiary
                )
            }
        ) { padding ->
            when (viewModel.isAvailable) {
                null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    SectionLoadingIndicator()
                }
                false -> DozeUnavailable(Modifier.fillMaxSize().padding(padding))
                true -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 12.dp,
                        start = MaxUiMetrics.screenHorizontalPadding,
                        end = MaxUiMetrics.screenHorizontalPadding,
                        bottom = MaxUiMetrics.screenBottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaxUiMetrics.screenItemGap)
                ) {
                    item { DozeOverview(viewModel) }
                    item { DozeEngineCard(viewModel) }
                    item { GmsPolicyCard(viewModel) }
                    item { DozeActionsCard(viewModel) }
                    item { WhitelistHeader(viewModel) }
                    if (viewModel.isAppsLoading) {
                        item { SectionLoadingIndicator() }
                    } else if (viewModel.filteredApps.isEmpty()) {
                        item { EmptyAppsCard() }
                    } else {
                        items(viewModel.filteredApps, key = { it.packageName }) { app ->
                            DozeAppRow(app) { checked -> viewModel.setWhitelisted(context, app, checked) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DozeOverview(viewModel: DozeModeViewModel) {
    val scheme = MaterialTheme.colorScheme
    val state = when (viewModel.dozeState) {
        DozeState.IDLE -> stringResource(R.string.dozemode_state_idle)
        DozeState.IDLE_PENDING -> stringResource(R.string.dozemode_state_idle_pending)
        DozeState.SENSING -> stringResource(R.string.dozemode_state_sensing)
        DozeState.LOCATING -> stringResource(R.string.dozemode_state_locating)
        DozeState.MAINTENANCE -> stringResource(R.string.dozemode_state_maintenance)
        DozeState.ACTIVE -> stringResource(R.string.dozemode_state_active)
        DozeState.UNKNOWN -> stringResource(R.string.dozemode_state_unknown)
    }
    val idle = viewModel.dozeState == DozeState.IDLE
    Card(shape = RoundedCornerShape(MaxUiMetrics.cardRadius), colors = CardDefaults.cardColors(containerColor = scheme.tertiaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bedtime, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Doze engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onTertiaryContainer)
                    Text(if (idle) "Device is currently in deep idle" else "Monitoring the system idle state", style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer.copy(alpha = .72f))
                }
                AssistChip(onClick = {}, label = { Text(state) })
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Idle cycles", viewModel.stats.idleCount.toString(), Modifier.weight(1f))
                Metric("Light idle", viewModel.stats.lightIdleCount.toString(), Modifier.weight(1f))
                Metric("GMS", viewModel.gmsMode.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .55f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DozeEngineCard(viewModel: DozeModeViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(MaxUiMetrics.cardRadius)) {
        Column(Modifier.padding(16.dp)) {
            Text("System policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("These switches change Max Manager's Doze policy. Android's DeviceIdleController remains the source of the live state above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ExpressiveSwitchItem(
                icon = Icons.Outlined.PowerSettingsNew,
                title = stringResource(R.string.dozemode_enable),
                summary = stringResource(R.string.dozemode_enable_desc),
                checked = viewModel.settings.isEnabled,
                onCheckedChange = viewModel::setDozeEnabled
            )
            HorizontalDivider()
            ExpressiveSwitchItem(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.dozemode_aggressive),
                summary = stringResource(R.string.dozemode_aggressive_desc),
                enabled = viewModel.settings.isEnabled,
                checked = viewModel.settings.isAggressive,
                onCheckedChange = viewModel::setAggressiveDoze
            )
        }
    }
}

@Composable
private fun GmsPolicyCard(viewModel: DozeModeViewModel) {
    val labels = listOf(
        stringResource(R.string.dozemode_gms_default),
        stringResource(R.string.dozemode_gms_standard),
        stringResource(R.string.dozemode_gms_aggressive)
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(MaxUiMetrics.cardRadius)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dozemode_gms_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.dozemode_gms_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = viewModel.gmsMode.ordinal == index,
                        onClick = { viewModel.setGmsDozeMode(GmsDozeMode.entries[index]) },
                        shape = SegmentedButtonDefaults.itemShape(index, labels.size)
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun DozeActionsCard(viewModel: DozeModeViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(MaxUiMetrics.cardRadius)) {
        Column(Modifier.padding(16.dp)) {
            Text("Idle controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Manual actions are useful for testing the device's idle state machine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = viewModel::forceIdle, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dozemode_action_force_idle)) }
                OutlinedButton(onClick = viewModel::stepIdleState, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dozemode_action_step)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::unforceIdle, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dozemode_action_unforce)) }
                TextButton(onClick = viewModel::resetStats, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dozemode_action_reset_stats)) }
            }
        }
    }
}

@Composable
private fun WhitelistHeader(viewModel: DozeModeViewModel) {
    Column {
        Text(stringResource(R.string.dozemode_section_whitelist), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Allow selected apps to stay exempt from battery optimization.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val tabs = listOf(DozeAppTab.ALL to stringResource(R.string.dozemode_tab_all), DozeAppTab.WHITELISTED to stringResource(R.string.dozemode_tab_whitelist))
            tabs.forEachIndexed { index, (tab, label) ->
                SegmentedButton(
                    selected = viewModel.selectedTab == tab,
                    onClick = { viewModel.selectedTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun DozeAppRow(app: DozeAppInfo, onCheckedChange: (Boolean) -> Unit) {
    val bitmap = remember(app.packageName, app.icon) {
        val drawable = app.icon ?: return@remember null
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onCheckedChange(!app.isWhitelisted) },
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) Image(bitmap, app.label, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
            else Icon(Icons.Outlined.Android, null, Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, maxLines = 1, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(app.packageName, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MaxSwitch(checked = app.isWhitelisted, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun EmptyAppsCard() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(stringResource(R.string.dozemode_no_apps_found), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DozeUnavailable(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(MaxUiMetrics.cardRadius), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Bedtime, null, Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.dozemode_unavailable), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
