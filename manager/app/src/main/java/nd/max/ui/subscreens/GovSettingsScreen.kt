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
import nd.max.core.platform.PropertyUtils
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
            // اختصار Max AI — **مُصلَّح عن اللافتة التي كانت هنا.** اللافتة القديمة كانت تقول
            // «قد يتجاوز المحرك تغييراتك اليدوية هنا»، والمقيس في `ControlRegistry` أن المحرّك
            // لا يملك مقبضًا للحاكم أصلًا: مقابضه تردّدات المعالج وسقف الرسوم والـboost. فالحاكم
            // لا يُلمس، ووصفُ تجاوزٍ لم يقع ادّعاءٌ بلا دليل (ADR-07) — ولهذا `manual = false`.
            item {
                nd.max.ui.component.MaxAiShortcut(
                    navController = navController,
                    manual = false,
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
                if (viewModel.cpuGovIndex != null &&
                    viewModel.freqOffsetIndex != null &&
                    viewModel.availableGovernors?.isNotEmpty() == true) {
                    ExpressiveList(
                        content = listOf(
                            {
                                // خيار واحد يعرض **الساري في النواة**: كانت ثلاثة صفوف
                                // (متوازن/أداء/توفير) تُقرأ ثلاثة أرقام لحقيقة واحدة،
                                // ولا يقول أيّها مطبَّق الآن. والكتابة تذهب للملامح الثلاثة
                                // معًا ثم تُطبَّق فورًا (`TweakViewModel.updateCpuGovernor`).
                                ExpressiveDropdownItem(
                                    icon = Icons.Outlined.Memory,
                                    title = stringResource(R.string.cpu_governor),
                                    summary = stringResource(R.string.cpu_governor_desc),
                                    items = viewModel.availableGovernors ?: emptyList(),
                                    selectedIndex = viewModel.cpuGovIndex!!,
                                    onItemSelected = { viewModel.updateCpuGovernor(it) }
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
                // خيار واحد يعرض الجدولة السارية (ما بين قوسَي عقدة النواة)، ويكتب للملامح
                // الثلاثة معًا ثم يطبّق على أجهزة الكتل فورًا. ولا صفّ لملف بعينه بعد اليوم.
                if (viewModel.availableIOSchedulers == null) {
                    SectionLoadingIndicator()
                } else if (viewModel.availableIOSchedulers!!.isNotEmpty()) {
                    if (viewModel.ioSchedulerIndex != null) {
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.Water,
                                        title = stringResource(R.string.io_scheduler),
                                        summary = stringResource(R.string.io_scheduler_desc),
                                        items = viewModel.availableIOSchedulers ?: emptyList(),
                                        selectedIndex = viewModel.ioSchedulerIndex!!,
                                        onItemSelected = { viewModel.updateIoScheduler(it) }
                                    )
                                }
                            )
                        )
                    } else {
                        SectionLoadingIndicator()
                    }
                } else {
                    // لا جدولة معلنة على هذا الجهاز: لا صفّ فارغ ولا قائمة وهمية.
                }
            }

            if (viewModel.isMaliGpuAvailable == true) {
                item { TweaksSectionTitle(text = stringResource(R.string.section_mali_gpu)) }
                item {
                    if (viewModel.availableMaliGovernors == null) {
                        SectionLoadingIndicator()
                    } else if (viewModel.availableMaliGovernors!!.isNotEmpty()) {
                        if (viewModel.maliGovIndex != null) {
                            ExpressiveList(
                                content = listOf(
                                    {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Outlined.Tune,
                                            title = stringResource(R.string.mali_gpu_governor),
                                            summary = stringResource(R.string.mali_gpu_governor_desc),
                                            items = viewModel.availableMaliGovernors ?: emptyList(),
                                            selectedIndex = viewModel.maliGovIndex!!,
                                            onItemSelected = { viewModel.updateMaliGovernor(it) }
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
            