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


import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.*
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.*
import nd.max.ui.viewmodel.TweakViewModel


@Composable
fun GovSettings(
    navController: NavHostController,
    viewModel: TweakViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.loadAllConfiguration(context)
    }
    
        
    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { GovSettingsTopAppBar(
            scrollBehavior,
            onBack = { navController.popBackStack() }
            ) 
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            
        ) {
            item {
                nd.max.ui.component.MaxAiActiveBanner(
                    navController = navController,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                MaxManagerInsight(
                    text = stringResource(R.string.gov_settingsdesc2),
                    accent = colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            item { TweaksSectionTitle(stringResource(R.string.section_CPUSettings)) }
            item {
                if (viewModel.defaultGovIndex != null && 
                    viewModel.powersaveGovIndex != null && 
                    viewModel.performanceGovIndex != null && 
                    viewModel.freqOffsetIndex != null) {
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveDropdownItem(
                                    icon = Icons.Outlined.Water,
                                    title = stringResource(R.string.default_cpu_gov),
                                    summary = stringResource(R.string.default_cpu_gov_desc),
                                    items = viewModel.availableGovernors ?: emptyList(),
                                    selectedIndex = viewModel.defaultGovIndex!!,
                                    onItemSelected = { viewModel.updateDefaultGovernor(it) }
                                )
                            },
                            {
                                ExpressiveDropdownItem(
                                    icon = Icons.Outlined.OfflineBolt,
                                    title = stringResource(R.string.performance_cpu_gov),
                                    summary = stringResource(R.string.performance_cpu_gov_desc),
                                    items = viewModel.availableGovernors ?: emptyList(),
                                    selectedIndex = viewModel.performanceGovIndex!!,
                                    onItemSelected = { viewModel.updatePerformanceGovernor(it) }
                                )
                            },
                            {
                                ExpressiveDropdownItem(
                                    icon = Icons.Outlined.EnergySavingsLeaf,
                                    title = stringResource(R.string.powersave_cpu_gov),
                                    summary = stringResource(R.string.powersave_cpu_gov_desc),
                                    items = viewModel.availableGovernors ?: emptyList(),
                                    selectedIndex = viewModel.powersaveGovIndex!!,
                                    onItemSelected = { viewModel.updatePowersaveGovernor(it) }
                                )
                            },
                            {
                                FreqLimitSliderItem(
                                    icon = Icons.Outlined.Tune,
                                    initialValue = viewModel.freqOffsetIndex!!,
                                    labels = viewModel.offsetLabels,
                                    onSaved = { viewModel.saveFreqOffset(it) }
                                )
                            }
                        )
                    )
                } else {
                    SectionLoadingIndicator()
                }
            }

            item { TweaksSectionTitle(stringResource(R.string.io_settings)) }
            item {
                if (viewModel.availableIOSchedulers == null) {
                    SectionLoadingIndicator()
                } else if (viewModel.availableIOSchedulers!!.isNotEmpty()) {
                    if (viewModel.balancedIOIndex != null && 
                        viewModel.performanceIOIndex != null && 
                        viewModel.powersaveIOIndex != null) {
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.Water,
                                        title = stringResource(R.string.balanced_io_scheduler),
                                        summary = stringResource(R.string.balanced_io_scheduler_desc),
                                        items = viewModel.availableIOSchedulers ?: emptyList(),
                                        selectedIndex = viewModel.balancedIOIndex!!,
                                        onItemSelected = { viewModel.updateBalancedIO(it) }
                                    )
                                },
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.OfflineBolt,
                                        title = stringResource(R.string.performance_io_scheduler),
                                        summary = stringResource(R.string.performance_io_scheduler_desc),
                                        items = viewModel.availableIOSchedulers ?: emptyList(),
                                        selectedIndex = viewModel.performanceIOIndex!!,
                                        onItemSelected = { viewModel.updatePerformanceIO(it) }
                                    )
                                },
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.EnergySavingsLeaf,
                                        title = stringResource(R.string.powersave_io_scheduler),
                                        summary = stringResource(R.string.powersave_io_scheduler_desc),
                                        items = viewModel.availableIOSchedulers ?: emptyList(),
                                        selectedIndex = viewModel.powersaveIOIndex!!,
                                        onItemSelected = { viewModel.updatePowersaveIO(it) }
                                    )
                                }
                            )
                        )
                    } else {
                        SectionLoadingIndicator()
                    }
                } else {

                }
            }
            

            if (viewModel.isMaliGpuAvailable == true) {
                item { TweaksSectionTitle(text = stringResource(R.string.section_mali_gpu)) }
                item {
                    if (viewModel.availableMaliGovernors == null) {
                        SectionLoadingIndicator()
                    } else if (viewModel.availableMaliGovernors!!.isNotEmpty()) {
                        if (viewModel.balancedMaliGovIndex != null && 
                            viewModel.performanceMaliGovIndex != null && 
                            viewModel.powersaveMaliGovIndex != null) {
                            ExpressiveList(
                                content = listOf(
                                    {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Outlined.Water,
                                            title = stringResource(R.string.balanced_mali_gov),
                                            summary = stringResource(R.string.balanced_mali_gov_desc),
                                            items = viewModel.availableMaliGovernors ?: emptyList(),
                                            selectedIndex = viewModel.balancedMaliGovIndex!!,
                                            onItemSelected = { viewModel.updateBalancedMaliGov(it) }
                                        )
                                    },
                                    {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Outlined.OfflineBolt,
                                            title = stringResource(R.string.performance_mali_gov),
                                            summary = stringResource(R.string.performance_mali_gov_desc),
                                            items = viewModel.availableMaliGovernors ?: emptyList(),
                                            selectedIndex = viewModel.performanceMaliGovIndex!!,
                                            onItemSelected = { viewModel.updatePerformanceMaliGov(it) }
                                        )
                                    },
                                    {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Outlined.EnergySavingsLeaf,
                                            title = stringResource(R.string.powersave_mali_gov),
                                            summary = stringResource(R.string.powersave_mali_gov_desc),
                                            items = viewModel.availableMaliGovernors ?: emptyList(),
                                            selectedIndex = viewModel.powersaveMaliGovIndex!!,
                                            onItemSelected = { viewModel.updatePowersaveMaliGov(it) }
                                        )
                                    }
                                )
                            )
                        } else {
                            SectionLoadingIndicator()
                        }
                    }
                }
            }              
        }
    }
    }
    }


@Composable
fun GovSettingsTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.gov_settings),
        onBack = onBack,
        accentIcon = Icons.Filled.Speed,
        accent = MaterialTheme.colorScheme.tertiary
    )
}
            