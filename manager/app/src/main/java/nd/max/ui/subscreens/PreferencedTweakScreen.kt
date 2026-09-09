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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.*
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
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.util.DebugUtils
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.RebootManager
import nd.max.ui.util.getChipsetVendor


@Composable
fun PreferenceTweakScreen(navController: NavController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    
    var isFullModeEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isFullModeEnabled = DebugUtils.isFullModeEnabled()
    }

    // --- Reboot confirm dialog plumbing ---
    var pendingToggle by remember { mutableStateOf<(() -> Unit)?>(null) }
    val rebootDialog = rememberConfirmDialog(
        onConfirm = {
            pendingToggle?.invoke()
            pendingToggle = null
        },
        onDismiss = { pendingToggle = null }
    )

    fun toggleWithRebootCheck(key: String, isChecked: Boolean, apply: () -> Unit) {
        if (RebootManager.wouldRequireReboot(key, isChecked)) {
            pendingToggle = apply
            rebootDialog.showConfirm(
                title = context.getString(R.string.dialog_reboot_required_title),
                content = context.getString(R.string.reboot_required_content),
                confirm = context.getString(R.string.yes),
                dismiss = context.getString(R.string.no)
            )
        } else {
            apply()
        }
    }
    // ---------------------------------------

        
    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { PreferenceTweakTopAppBar(
            scrollBehavior,
            onBack = { navController.popBackStack() }
            ) 
        },
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
            
            item {
                MaxManagerInsight(
                    text = stringResource(R.string.str_apply_add_on_configurations_ta),
                    accent = colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item { PrefSectionTitle(stringResource(R.string.section_prefstweaks)) }
            item {
                var socType by remember { mutableStateOf<String?>(null) }
                var schedTunes by remember { mutableStateOf<Boolean?>(null) }
                var sflstate by remember { mutableStateOf<Boolean?>(null) }
                var jitstate by remember { mutableStateOf<Boolean?>(null) }
                
                var malischedstate by remember { mutableStateOf<Boolean?>(null) }
                var waltTunes by remember { mutableStateOf<Boolean?>(null) }
                var DTraces by remember { mutableStateOf<Boolean?>(null) }
                var dlogcat by remember { mutableStateOf<Boolean?>(null) }
                var distherm by remember { mutableStateOf<Boolean?>(null) }
    
                LaunchedEffect(Unit) {
                    socType = withContext(Dispatchers.IO) { getChipsetVendor(context) }
                    schedTunes = PropertyUtils.get(MaxManagerProps.Conf.SCHED_TUNES) == "1"
                    sflstate = PropertyUtils.get(MaxManagerProps.Conf.SFL) == "1"
                    jitstate = PropertyUtils.get(MaxManagerProps.Conf.JUST_IN_TIME) == "1"                        
                    malischedstate = PropertyUtils.get(MaxManagerProps.Conf.MALI_SCHED) == "1"
                    waltTunes = PropertyUtils.get(MaxManagerProps.Conf.WALT_TUNES) == "1"
                    DTraces = PropertyUtils.get(MaxManagerProps.Conf.DISABLE_TRACE) == "1"
                    dlogcat = PropertyUtils.get(MaxManagerProps.Conf.LOGD) == "1"
                    distherm = PropertyUtils.get(MaxManagerProps.Conf.DYNAMIC_THERMAL) == "1"

                    RebootManager.captureBaselineOnce("pref_schedtunes", schedTunes!!)
                    RebootManager.captureBaselineOnce("pref_SFL", sflstate!!)
                    RebootManager.captureBaselineOnce("pref_justintime", jitstate!!)
                    RebootManager.captureBaselineOnce("pref_malisched", malischedstate!!)
                    RebootManager.captureBaselineOnce("pref_walttunes", waltTunes!!)
                    RebootManager.captureBaselineOnce("pref_disabletrace", DTraces!!)
                    RebootManager.captureBaselineOnce("pref_logd", dlogcat!!)
                    RebootManager.captureBaselineOnce("pref_DThermal", distherm!!)
                }
    
                if (socType != null && schedTunes != null && distherm != null && dlogcat != null && DTraces != null && waltTunes != null && sflstate != null && jitstate != null && malischedstate != null) { 
                    
                    val isMediaTek   = socType == "mediatek"
                    val isSnapdragon = socType == "qualcomm"

                    ExpressiveList(
                        content = buildList {
                            add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Rounded.Tune,
                                    title = stringResource(R.string.sched_tunes),
                                    summary = stringResource(R.string.sched_tunes_desc),
                                    checked = schedTunes!!,
                                    onCheckedChange = { isChecked ->
                                        toggleWithRebootCheck("pref_schedtunes", isChecked) {
                                            schedTunes = isChecked
                                            PropertyUtils.set(MaxManagerProps.Conf.SCHED_TUNES, if (isChecked) "1" else "0")
                                            RebootManager.checkAgainstBaseline("pref_schedtunes", isChecked)

                                            if (isChecked && waltTunes == true) {
                                                waltTunes = false
                                                PropertyUtils.set(MaxManagerProps.Conf.WALT_TUNES, "0")
                                                RebootManager.checkAgainstBaseline("pref_walttunes", false)
                                            }
                                        }
                                    }
                                )
                            }
                            add {
                                Box(modifier = Modifier.alpha(if (isSnapdragon) 1f else 0.4f)) {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.Timeline,
                                        title = stringResource(R.string.walt_tunes),
                                        summary = if (isSnapdragon) stringResource(R.string.walt_tunes_desc) else "This option is only available for Snapdragon devices.",
                                        checked = waltTunes!!,
                                        enabled = isSnapdragon,
                                        onCheckedChange = { isChecked ->
                                            toggleWithRebootCheck("pref_walttunes", isChecked) {
                                                waltTunes = isChecked
                                                PropertyUtils.set(MaxManagerProps.Conf.WALT_TUNES, if (isChecked) "1" else "0")
                                                RebootManager.checkAgainstBaseline("pref_walttunes", isChecked)

                                                if (isChecked && schedTunes == true) {
                                                    schedTunes = false
                                                    PropertyUtils.set(MaxManagerProps.Conf.SCHED_TUNES, "0")
                                                    RebootManager.checkAgainstBaseline("pref_schedtunes", false)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Rounded.Layers,
                                    title = stringResource(R.string.sfl_latency),
                                    summary = stringResource(R.string.sfl_latency_desc),
                                    checked = sflstate!!,
                                    onCheckedChange = { isChecked ->
                                        toggleWithRebootCheck("pref_SFL", isChecked) {
                                            sflstate = isChecked
                                            PropertyUtils.set(MaxManagerProps.Conf.SFL, if (isChecked) "1" else "0")
                                            RebootManager.checkAgainstBaseline("pref_SFL", isChecked)
                                        }
                                    }
                                )
                            }
                            if (isFullModeEnabled) {
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.Bolt,
                                        title = stringResource(R.string.jit_compilation),
                                        summary = stringResource(R.string.jit_compilation_desc),
                                        checked = jitstate!!,
                                        onCheckedChange = { isChecked ->
                                            toggleWithRebootCheck("pref_justintime", isChecked) {
                                                jitstate = isChecked
                                                PropertyUtils.set(MaxManagerProps.Conf.JUST_IN_TIME, if (isChecked) "1" else "0")
                                                RebootManager.checkAgainstBaseline("pref_justintime", isChecked)
                                            }
                                        }
                                    )
                                }
                            }
                            if (isFullModeEnabled) {
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.TrackChanges,
                                        title = stringResource(R.string.disable_trace),
                                        summary = stringResource(R.string.disable_trace_desc),
                                        checked = DTraces!!,
                                        onCheckedChange = { isChecked ->
                                            toggleWithRebootCheck("pref_disabletrace", isChecked) {
                                                DTraces = isChecked
                                                PropertyUtils.set(MaxManagerProps.Conf.DISABLE_TRACE, if (isChecked) "1" else "0")
                                                RebootManager.checkAgainstBaseline("pref_disabletrace", isChecked)
                                            }
                                        }
                                    )
                                }
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.AutoMirrored.Rounded.Notes,
                                        title = stringResource(R.string.disable_logging),
                                        summary = stringResource(R.string.disable_logging_desc),
                                        checked = dlogcat!!,
                                        onCheckedChange = { isChecked ->
                                            toggleWithRebootCheck("pref_logd", isChecked) {
                                                dlogcat = isChecked
                                                PropertyUtils.set(MaxManagerProps.Conf.LOGD, if (isChecked) "1" else "0")
                                                RebootManager.checkAgainstBaseline("pref_logd", isChecked)
                                            }
                                        }
                                    )
                                }
                            }
                            
                            add {
                                Box(modifier = Modifier.alpha(if (isMediaTek) 1f else 0.4f)) {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.DeveloperBoard,
                                        title = stringResource(R.string.gpu_mali),
                                        summary = if (isMediaTek) stringResource(R.string.gpu_mali_desc) else "This option is only available for MediaTek devices.",
                                        checked = malischedstate!!,
                                        enabled = isMediaTek,
                                        onCheckedChange = { isChecked ->
                                            toggleWithRebootCheck("pref_malisched", isChecked) {
                                                malischedstate = isChecked
                                                PropertyUtils.set(MaxManagerProps.Conf.MALI_SCHED, if (isChecked) "1" else "0")
                                                RebootManager.checkAgainstBaseline("pref_malisched", isChecked)
                                            }
                                        }
                                    )
                                }
                            }
                            if (isFullModeEnabled) {
                                add {
                                    Box(modifier = Modifier.alpha(if (isMediaTek) 1f else 0.4f)) {
                                        ExpressiveSwitchItem(
                                            icon = Icons.Rounded.Thermostat,
                                            title = stringResource(R.string.disable_thermals),
                                            summary = if (isMediaTek) stringResource(R.string.disable_thermals_desc) else "This option is only available for MediaTek devices.",
                                            checked = distherm!!,
                                            enabled = isMediaTek,
                                            onCheckedChange = { isChecked ->
                                                toggleWithRebootCheck("pref_DThermal", isChecked) {
                                                    distherm = isChecked
                                                    PropertyUtils.set(MaxManagerProps.Conf.DYNAMIC_THERMAL, if (isChecked) "1" else "0")
                                                    RebootManager.checkAgainstBaseline("pref_DThermal", isChecked)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
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

    ConfirmDialogHost(handle = rebootDialog)
    }


/** Thin wrapper over [MaxManagerSectionTitle] — kept so existing call sites don't change. */
@Composable
fun PrefSectionTitle(text: String) {
    MaxManagerSectionTitle(text = text, accent = MaterialTheme.colorScheme.tertiary)
}

@Composable
fun PreferenceTweakTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.prefs),
        onBack = onBack,
        accentIcon = Icons.Filled.Tune,
        accent = MaterialTheme.colorScheme.tertiary
    )
}