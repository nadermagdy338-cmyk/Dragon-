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

@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.AdvancedConfigViewModel

@Composable
fun AdvancedConfigurationScreen(
    navController: NavController,
    viewModel: AdvancedConfigViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.loadState() }

    
    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AdvancedConfigTopAppBar(scrollBehavior, onBack = { navController.popBackStack() }) },
        containerColor = colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            item {
                MaxManagerInsight(
                    text = stringResource(R.string.advanced_config_subtitle),
                    accent = colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (viewModel.isAvailable == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        SectionLoadingIndicator()
                    }
                }
            }

            // New layout: the screen is named "processor governors, graphics scaling,
            // and storage queue policies" -- so those three live here first, in that
            // order. The deep-link shortcuts to other tools (FAS, FPSGO, kernel
            // flasher...) are a secondary, unrelated grab-bag and move to the bottom
            // as a "more tools" section instead of leading the page.

            if (viewModel.clusterGovernors.isNotEmpty()) {
                item { TweaksSectionTitle(stringResource(R.string.advanced_config_cpu_gov_title)) }
                viewModel.clusterGovernors.forEachIndexed { index, state ->
                    item {
                        GovernorPickerCard(
                            title = state.cluster.label,
                            subtitle = stringResource(R.string.advanced_config_cluster_cores, state.cluster.cores.first(), state.cluster.cores.last()),
                            statusText = state.pinned?.let { stringResource(R.string.advanced_config_pinned, it) }
                                ?: stringResource(R.string.advanced_config_unpinned),
                            pinned = state.pinned != null,
                            options = state.available,
                            current = state.current,
                            onSelect = { viewModel.setClusterGovernor(index, it) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            if (viewModel.gpuGovernorDir != null) {
                item { TweaksSectionTitle(stringResource(R.string.advanced_config_gpu_gov_title)) }
                item {
                    GovernorPickerCard(
                        title = stringResource(R.string.advanced_config_gpu_gov_card_title),
                        subtitle = stringResource(R.string.advanced_config_gpu_gov_card_desc),
                        statusText = viewModel.pinnedGpuGovernor?.let { stringResource(R.string.advanced_config_pinned, it) }
                            ?: stringResource(R.string.advanced_config_unpinned),
                        pinned = viewModel.pinnedGpuGovernor != null,
                        options = viewModel.availableGpuGovernors,
                        current = viewModel.currentGpuGovernor,
                        onSelect = { viewModel.setGpuGovernor(it) }
                    )
                }
            }

            if (viewModel.storageDevice != null) {
                item { TweaksSectionTitle(stringResource(R.string.advanced_config_storage_title)) }
                item {
                    GovernorPickerCard(
                        title = stringResource(R.string.advanced_config_storage_card_title),
                        subtitle = stringResource(R.string.advanced_config_storage_card_desc),
                        statusText = viewModel.pinnedScheduler?.let { stringResource(R.string.advanced_config_pinned, it) }
                            ?: stringResource(R.string.advanced_config_unpinned),
                        pinned = viewModel.pinnedScheduler != null,
                        options = viewModel.availableSchedulers,
                        current = viewModel.currentScheduler,
                        onSelect = { viewModel.setStorageScheduler(it) }
                    )
                }
            }

            item { TweaksSectionTitle(stringResource(R.string.advanced_config_related_title)) }

            item {
                ExpressiveList(
                    content = listOf(
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.Ballot) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = { navController.navigate("governorsettings") },
                                headlineContent = { Text(stringResource(R.string.gov_settings)) },
                                supportingContent = { Text(stringResource(R.string.gov_settingsdesc)) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.Speed) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = { navController.navigate("FasScreen") },
                                headlineContent = { Text(stringResource(R.string.str_frame_aware_scheduling_fas)) },
                                supportingContent = { Text(stringResource(R.string.fas_hub_desc)) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.SportsEsports) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = { navController.navigate("fpsgoscreen") },
                                headlineContent = { Text(stringResource(R.string.str_fpsgo_settings)) },
                                supportingContent = { Text(stringResource(R.string.str_fpsgo_desc)) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.Tune) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = { navController.navigate("preferenced") },
                                headlineContent = { Text(stringResource(R.string.prefs)) },
                                supportingContent = { Text(stringResource(R.string.prefsdesc)) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.RocketLaunch) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = { navController.navigate("activitylauncher") },
                                headlineContent = { Text("Activity Launcher") },
                                supportingContent = { Text("Launch hidden app activities") }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.Memory) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = { navController.navigate("kernelflasher") },
                                headlineContent = { Text("Kernel Flasher") },
                                supportingContent = { Text("Backup and flash boot/kernel images") }
                            )
                        }
                    )
                )
            }
        }
    }
    }
    }


/**
 * One governor/scheduler picker: a status row (name + whether it's pinned)
 * followed by a wrapping row of selectable chips built from whatever options
 * this device's driver actually reports — reused for CPU clusters, the GPU
 * governor, and the storage scheduler instead of three near-identical composables.
 */
@Composable
private fun GovernorPickerCard(
    title: String,
    subtitle: String,
    statusText: String,
    pinned: Boolean,
    options: List<String>,
    current: String?,
    onSelect: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colorScheme.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
            Surface(
                color = if (pinned) colorScheme.tertiaryContainer else colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pinned) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = current == option,
                        onClick = { onSelect(option) },
                        label = { Text(option) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedConfigTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.advanced_config_title),
        onBack = onBack,
        accentIcon = Icons.Filled.Build,
        accent = MaterialTheme.colorScheme.tertiary
    )
}
