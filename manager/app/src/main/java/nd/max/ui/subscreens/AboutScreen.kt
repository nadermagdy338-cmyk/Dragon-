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
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nd.max.BuildConfig
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.util.PropertyUtils


@Composable
fun AboutScreen(navController: NavController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val listState = rememberLazyListState()
    

    val openLink = { url: String ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

        
    ScreenAccentProvider(MaterialTheme.colorScheme.primary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { 
            AboutTopAppBar(
                scrollBehavior = scrollBehavior,
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
            )
        ) {

            item {
                StudioSectionHeader(
                    title = stringResource(R.string.section_about),
                    subtitle = stringResource(R.string.about_subtitle),
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = stringResource(R.string.str_version_buildconfig_version_na, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        nd.max.ui.component.StudioTonalButton(
                            onClick = { openLink("https://t.me/ROBINHOOD_GROUP_RODIN") }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_telegram), 
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.telegram_group))
                        }
                    }
                }
            }


            item { 
                AboutSectionTitle(stringResource(R.string.str_maintainer)) 
            }
            item {
                ExpressiveList(
                    content = listOf(
                        {
                            ExpressiveListItem(
                                headlineContent = { Text(text = "MaxManager Project", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(stringResource(R.string.str_creator_maintainer)) },
                                leadingContent = {
                                    Image(
                                        painter = painterResource(R.drawable.avatar),
                                        contentDescription = "MaxManager Project",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { openLink("https://t.me/ROBINHOOD_GROUP_RODIN") }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_telegram),
                                            contentDescription = stringResource(R.string.cd_telegram),
                                            modifier = Modifier.size(26.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    )
                )
            }
        }
    }
    }
    }


/** Thin wrapper over [MaxManagerSectionTitle] — kept so existing call sites don't change. */
@Composable
fun AboutSectionTitle(text: String) {
    MaxManagerSectionTitle(text = text, accent = MaterialTheme.colorScheme.primary)
}

@Composable
fun AboutTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.section_about),
        onBack = onBack,
        accentIcon = Icons.Filled.Info,
        accent = MaterialTheme.colorScheme.primary
    )
}
 