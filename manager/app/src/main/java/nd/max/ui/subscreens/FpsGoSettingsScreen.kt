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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import nd.max.MaxManagerProps


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
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.util.PropertyUtils


@Composable
fun FpsGoSettings(navController: NavController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    
        
    ScreenAccentProvider(MaterialTheme.colorScheme.secondary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { FpsGoTopAppBar(
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
                MaxManagerInsight(
                    text = stringResource(R.string.str_fpsgo_frame_per_second_go_is_a),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            item {
                var fpsgostate by remember { mutableStateOf<Boolean?>(null) }
                
                LaunchedEffect(Unit) {
                    fpsgostate = PropertyUtils.get(MaxManagerProps.Conf.USE_FPSGO) == "1"
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (fpsgostate != null) {
                    ExpressiveList(
                        content = listOf(
                            {
                                 ExpressiveSwitchItem(
                                    icon = Icons.Rounded.Speed,
                                     title = stringResource(R.string.str_use_fpsgo_title),
                                     summary = stringResource(R.string.str_use_fpsgo_summary),
                                    checked = fpsgostate!!,
                                    onCheckedChange = { isChecked ->
                                        fpsgostate = isChecked
                                        PropertyUtils.set(MaxManagerProps.Conf.USE_FPSGO, if (isChecked) "1" else "0")
                                    }
                                )
                            }
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
            
            item { PrefSectionTitle(stringResource(R.string.section_prefstweaks)) }
            
            item {
                var fpsgogedstate by remember { mutableStateOf<Boolean?>(null) }
                
                LaunchedEffect(Unit) {
                    fpsgogedstate = PropertyUtils.get(MaxManagerProps.Conf.FPS_GED) == "1"
                }
                
                if (fpsgogedstate != null) {
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveSwitchItem(
                                    icon = Icons.Rounded.Speed,
                                    title = stringResource(R.string.fpsgo_ged),
                                    summary = stringResource(R.string.fpsgo_ged_desc),
                                    checked = fpsgogedstate!!,
                                    onCheckedChange = { isChecked ->
                                        fpsgogedstate = isChecked
                                        PropertyUtils.set(MaxManagerProps.Conf.FPS_GED, if (isChecked) "1" else "0")
                                    }
                                )
                            }
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
    }
    }


@Composable
fun FpsGoTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.str_fpsgo_settings),
        onBack = onBack,
        accentIcon = Icons.Filled.SportsEsports,
        accent = MaterialTheme.colorScheme.secondary
    )
}
            