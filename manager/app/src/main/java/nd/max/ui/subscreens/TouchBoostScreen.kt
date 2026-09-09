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

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.viewmodel.TouchBoostViewModel

@Composable
fun TouchBoostScreen(
    navController: NavController,
    viewModel: TouchBoostViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.loadState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TouchBoostTopAppBar(scrollBehavior, onBack = { navController.popBackStack() }) },
        containerColor = colorScheme.surface
    ) { innerPadding ->
        when (viewModel.isAvailable) {
            null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { SectionLoadingIndicator() }

            false -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.TouchApp, null, tint = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(
                                if (viewModel.vendorHalDetected) R.string.touch_boost_unavailable_vendor_hal
                                else R.string.touch_boost_unavailable
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            true -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = MaxUiMetrics.screenHorizontalPadding,
                    end = MaxUiMetrics.screenHorizontalPadding,
                    bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(MaxUiMetrics.screenItemGap)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                item {
                    TouchOverviewCard(
                        boostEnabled = viewModel.boostEnabled,
                        doubleTapEnabled = viewModel.doubleTapEnabled,
                        gameNode = viewModel.gameModeNode,
                        sampleNode = viewModel.sampleRateNode,
                        dt2wNode = viewModel.doubleTapNode
                    )
                }

                item {
                    TouchSectionTitle(
                        title = stringResource(R.string.touch_boost_title),
                        subtitle = "Response tuning"
                    )
                }

                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Column {
                            ListItem(
                                leadingContent = { Icon(Icons.Filled.Bolt, null) },
                                headlineContent = { Text(stringResource(R.string.touch_boost_title)) },
                                supportingContent = { Text(stringResource(R.string.touch_boost_desc)) },
                                trailingContent = {
                                    MaxSwitch(
                                        checked = viewModel.boostEnabled,
                                        onCheckedChange = viewModel::setBoost
                                    )
                                }
                            )
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))
                            TouchCapabilityRow(
                                label = "Game mode",
                                path = viewModel.gameModeNode?.path,
                                enabled = viewModel.boostEnabled
                            )
                            TouchCapabilityRow(
                                label = "Sample rate",
                                path = viewModel.sampleRateNode?.path,
                                enabled = viewModel.boostEnabled
                            )
                        }
                    }
                }

                if (viewModel.doubleTapNode != null) {
                    item {
                        TouchSectionTitle(
                            title = stringResource(R.string.touch_dt2w_title),
                            subtitle = "Screen-off gesture"
                        )
                    }
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp
                        ) {
                            Column {
                                ListItem(
                                    leadingContent = { Icon(Icons.Outlined.Nightlight, null) },
                                    headlineContent = { Text(stringResource(R.string.touch_dt2w_title)) },
                                    supportingContent = { Text(stringResource(R.string.touch_dt2w_desc)) },
                                    trailingContent = {
                                        MaxSwitch(
                                            checked = viewModel.doubleTapEnabled,
                                            onCheckedChange = viewModel::setDoubleTapToWake
                                        )
                                    }
                                )
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))
                                TouchCapabilityRow(
                                    label = "Gesture node",
                                    path = viewModel.doubleTapNode?.path ?: "",
                                    enabled = viewModel.doubleTapEnabled
                                )
                            }
                        }
                    }
                }

                item {
                    TouchSectionTitle(
                        title = "Driver information",
                        subtitle = "Detected capabilities on this device"
                    )
                }

                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            TouchCapabilityRow("Game control", viewModel.gameModeNode?.path, viewModel.gameModeNode != null)
                            TouchCapabilityRow("Report rate", viewModel.sampleRateNode?.path, viewModel.sampleRateNode != null)
                            TouchCapabilityRow("Double tap", viewModel.doubleTapNode?.path, viewModel.doubleTapNode != null)
                        }
                    }
                }

                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Outlined.Info, null, tint = colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("How this works", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.touch_boost_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(
                                        R.string.touch_boost_node_note,
                                        viewModel.gameModeNode?.path ?: "-",
                                        viewModel.sampleRateNode?.path ?: "-"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Shield, null, tint = colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    stringResource(R.string.touch_engine_safety_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    stringResource(R.string.touch_engine_safety_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSecondaryContainer
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
private fun TouchOverviewCard(
    boostEnabled: Boolean,
    doubleTapEnabled: Boolean,
    gameNode: TouchBoostViewModel.TouchNode?,
    sampleNode: TouchBoostViewModel.TouchNode?,
    dt2wNode: TouchBoostViewModel.TouchNode?
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.10f)
                ) {
                    Icon(
                        Icons.Filled.TouchApp,
                        null,
                        modifier = Modifier.padding(10.dp),
                        tint = scheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Touch response", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
                    Text("Vendor-level input controls", style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer.copy(alpha = 0.72f))
                }
                Text(
                    if (boostEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TouchMetric("Game", gameNode != null, Modifier.weight(1f))
                TouchMetric("Rate", sampleNode != null, Modifier.weight(1f))
                TouchMetric("DT2W", dt2wNode != null && doubleTapEnabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TouchMetric(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = scheme.onPrimaryContainer.copy(alpha = 0.08f)
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer.copy(alpha = 0.70f))
            Spacer(Modifier.height(3.dp))
            Text(if (active) "Available" else "Not set", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun TouchCapabilityRow(label: String, path: String?, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        leadingContent = {
            Icon(
                if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.RemoveCircleOutline,
                null,
                tint = if (enabled) scheme.primary else scheme.onSurfaceVariant
            )
        },
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = { Text(path ?: "Not exposed by this kernel", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
    )
}

@Composable
private fun TouchSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TouchBoostTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.touch_boost_title),
        onBack = onBack,
        accentIcon = Icons.Filled.TouchApp,
        accent = MaterialTheme.colorScheme.secondary
    )
}
