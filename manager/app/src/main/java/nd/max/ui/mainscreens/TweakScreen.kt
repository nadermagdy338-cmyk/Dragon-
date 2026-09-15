/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.MaxManagerProps


import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.topjohnwu.superuser.Shell
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.component.MaxSnackbarHost
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.*
import nd.max.ui.viewmodel.TweakViewModel


@Composable
fun TweakScreen(
    navController: NavHostController,
    viewModel: TweakViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val colorScheme = MaterialTheme.colorScheme
    var showBackupRestoreSheet by remember { mutableStateOf(false) }
    var showScreenHelp by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var pendingRestoreData by remember { mutableStateOf<Map<String, String>?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var optBackupTweaks by remember { mutableStateOf(true) }
    var optBackupApplist by remember { mutableStateOf(true) }

    var pendingRestoreResult by remember { mutableStateOf<TweakViewModel.ValidationResult?>(null) }
    var optRestoreTweaks by remember { mutableStateOf(true) }
    var optRestoreApplist by remember { mutableStateOf(true) }

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog(onConfirm = {}, onDismiss = {})

    LoadingDialogHost(handle = loadingDialog)
    ConfirmDialogHost(handle = confirmDialog)

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let {
            scope.launch {
                val success = loadingDialog.withLoading {
                    viewModel.createConfigFileBackup(context, it, optBackupTweaks, optBackupApplist)
                }
                if (success) {
                    snackbarHostState.showSnackbar(context.getString(R.string.dialog_backup_success))
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.dialog_backup_fail))
                }
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            showBackupRestoreSheet = false
            scope.launch {
                loadingDialog.withLoading {
                    val result = viewModel.validateAndRestoreFile(context, it)
                    if (result.isValid && result.data != null) {
                        pendingRestoreResult = result
                        optRestoreTweaks = result.hasTweaks
                        optRestoreApplist = result.hasApplist
                        showRestoreDialog = true
                    } else {
                        confirmDialog.showConfirm(context.getString(R.string.dialog_restore_fail_title), result.message, context.getString(android.R.string.ok), null)
                    }
                }
            }
        }
    }

    var isFullModeEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isFullModeEnabled = DebugUtils.isFullModeEnabled()
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllConfiguration(context)
    }


    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TweakScreenTopAppBar(
                scrollBehavior = scrollBehavior,
                onMoreClick = { showBackupRestoreSheet = true },
                onHelpClick = { showScreenHelp = true }
            )
        },
        snackbarHost = {
            MaxSnackbarHost(snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->

        LazyColumn(
            state = listState,
            modifier = Modifier.maxAdaptiveContentWidth(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {

            item {
                ControlScreenIntro(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.tweaks_workspace_title),
                    description = stringResource(R.string.tweaks_workspace_guidance),
                    status = stringResource(
                        if (isFullModeEnabled) R.string.tweaks_workspace_state_full
                        else R.string.tweaks_workspace_state_lite
                    ),
                    accent = colorScheme.primary,
                    modifier = Modifier.padding(top = MaxUiMetrics.screenTopPadding)
                )
                Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
            }

            item {
                SystemPulseBoard(
                    fullMode = isFullModeEnabled,
                    touchState = viewModel.touchBoostState,
                    thermalState = viewModel.thermalState,
                    onTouchOpen = { MaxNavActions(navController).navigateTo(MaxDestination.TouchBoost) },
                    onCoreOpen = { MaxNavActions(navController).navigateTo(MaxDestination.CpuCoreControl) },
                    onThermalOpen = { MaxNavActions(navController).navigateTo(MaxDestination.ThermalDetail) }
                )
                Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
            }

            item { TweaksSectionTitle(text = stringResource(R.string.section_performance)) }
            item {
                var socType by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    socType = withContext(Dispatchers.IO) { getChipsetVendor(context) }
                }
                if (socType != null && viewModel.liteState != null) {
                    val isMediaTek   = socType == "mediatek"

                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveSwitchItem(
                                    icon = Icons.Rounded.Speed,
                                    title = stringResource(R.string.perf_lite_mode),
                                    summary = stringResource(R.string.perf_lite_mode_desc),
                                    checked = viewModel.liteState!!,
                                    onCheckedChange = { viewModel.updateLiteMode(it) }
                                )
                            },
                            {
                                Box(modifier = Modifier.alpha(if (isMediaTek) 1f else 0.4f)) {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Speed) },
                                        onClick = {
                                            if (isMediaTek) {
                                                MaxNavActions(navController).navigateTo(MaxDestination.FpsGo)
                                            }
                                        },
                                        headlineContent = { Text(text = stringResource(R.string.str_fpsgo_settings)) },
                                        supportingContent = {
                                            Text(
                                                text = if (isMediaTek)
                                                    stringResource(R.string.str_fpsgo_desc)
                                                else
                                                    stringResource(R.string.str_fpsgo_unavailable)
                                            )
                                        },
                                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                    )
                                }
                            }
                        )
                    )
                } else {
                    SectionLoadingIndicator()
                }
            }

                            item {
                if (viewModel.preloadState != null &&
                    viewModel.memKillerState != null &&
                    viewModel.appPriorState != null &&
                    viewModel.dndState != null &&
                    viewModel.fstrimState != null) {

                    TweaksSectionTitle(stringResource(R.string.section_features))
                    ExpressiveList(
                        content = buildList {
                            add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Rounded.RocketLaunch,
                                    title = stringResource(R.string.game_preload),
                                    summary = stringResource(R.string.game_preload_desc),
                                    checked = viewModel.preloadState!!,
                                    onCheckedChange = { viewModel.updatePreloadMode(it) }
                                )
                            }
                            if (isFullModeEnabled) {
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.CleaningServices,
                                        title = stringResource(R.string.memory_killer),
                                        summary = stringResource(R.string.memory_killer_desc),
                                        checked = viewModel.memKillerState!!,
                                        onCheckedChange = { viewModel.updateMemoryKiller(it) }
                                    )
                                }
                            }
                            if (isFullModeEnabled) {
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.SwapVerticalCircle,
                                        title = stringResource(R.string.app_priority_control),
                                        summary = stringResource(R.string.app_priority_control_desc),
                                        checked = viewModel.appPriorState!!,
                                        onCheckedChange = { viewModel.updateAppPriority(it) }
                                    )
                                }
                            }
                            add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Rounded.DoNotDisturbOn,
                                    title = stringResource(R.string.dnd_mode_gaming),
                                    summary = stringResource(R.string.dnd_mode_gaming_desc),
                                    checked = viewModel.dndState!!,
                                    onCheckedChange = { viewModel.updateDndMode(it) }
                                )
                            }
                            if (isFullModeEnabled) {
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Outlined.ContentCut,
                                        title = stringResource(R.string.trim_filesystem),
                                        summary = stringResource(R.string.trim_filesystem_desc),
                                        checked = viewModel.fstrimState!!,
                                        onCheckedChange = { viewModel.updateFstrim(it) }
                                    )
                                }
                            }
                            add {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.TouchApp) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.TouchBoost) },
                                    headlineContent = { Text(stringResource(R.string.touch_boost_title)) },
                                    supportingContent = { Text(stringResource(R.string.touch_boost_desc)) },
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
                    TweaksSectionTitle(stringResource(R.string.section_CPUSettings))
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.Ballot) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.GovernorSettings) },
                                    headlineContent = { Text(stringResource(R.string.gov_settings)) },
                                    supportingContent = { Text(stringResource(R.string.gov_settingsdesc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.DeveloperBoard) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.CpuCoreControl) },
                                    headlineContent = { Text(stringResource(R.string.cpu_core_control_title)) },
                                    supportingContent = { Text(stringResource(R.string.cpu_core_control_desc)) },
                                )
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(MaxUiMetrics.itemGap))
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.Memory) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.MtkVendor) },
                                    headlineContent = { Text("Vendor Boost") },
                                    supportingContent = { Text("Chipset-specific game mode and thermal parameters") },
                                )
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(MaxUiMetrics.itemGap))
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.DeveloperBoard) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.GpuStudio) },
                                    headlineContent = { Text("GPU Reality Studio") },
                                    supportingContent = { Text("قراءة حية موثقة وتحكم يتكيف مع GPU الفعلي في جهازك") },
                                )
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
                    TweaksSectionTitle(stringResource(R.string.section_power_thermal))
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.BatteryChargingFull) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Charging) },
                                    headlineContent = { Text(stringResource(R.string.charging_title)) },
                                    supportingContent = { Text(stringResource(R.string.charging_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.Bedtime) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.DozeMode) },
                                    headlineContent = { Text(stringResource(R.string.dozemode_title)) },
                                    supportingContent = { Text(stringResource(R.string.dozemode_menu_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.SdStorage) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ZramManager) },
                                    headlineContent = { Text(stringResource(R.string.zram_title)) },
                                    supportingContent = { Text(stringResource(R.string.zram_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.Cable) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.BypassCharging) },
                                    headlineContent = { Text(stringResource(R.string.bcharging)) },
                                    supportingContent = { Text(stringResource(R.string.bcharging_desc)) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            },
                            {
                                ExpressiveSwitchItem(
                                    icon = Icons.Filled.ThermostatAuto,
                                    title = stringResource(R.string.thermalcore_service),
                                    summary = stringResource(R.string.thermalcore_service_desc),
                                    checked = viewModel.thermalState!!,
                                    onCheckedChange = { viewModel.updateThermalCore(it) }
                                )
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
                    TweaksSectionTitle(stringResource(R.string.section_display_render_settings))
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.Palette) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.DisplayStudio) },
                                    headlineContent = { Text(stringResource(R.string.display_studio_title)) },
                                    supportingContent = { Text(stringResource(R.string.display_studio_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.AspectRatio) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Resolution) },
                                    headlineContent = { Text(stringResource(R.string.resolution_title)) },
                                    supportingContent = { Text(stringResource(R.string.resolution_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.Speed) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.FpsOverlay) },
                                    headlineContent = { Text(stringResource(R.string.fps_overlay_title)) },
                                    supportingContent = { Text(stringResource(R.string.fps_overlay_menu_desc)) },
                                )
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
                    TweaksSectionTitle(stringResource(R.string.section_additionalsettings))
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.Hub) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.NetworkScheduler) },
                                    headlineContent = { Text(stringResource(R.string.net_sched_title)) },
                                    supportingContent = { Text(stringResource(R.string.net_sched_menu_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.DeleteSweep) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.DebloatFreeze) },
                                    headlineContent = { Text(stringResource(R.string.debloat_freeze_title)) },
                                    supportingContent = { Text(stringResource(R.string.debloat_freeze_desc)) },
                                )
                            },
                            {
                                ExpressiveListItem(
                                    leadingContent = { LeadingIcon(icon = Icons.Outlined.Build) },
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Dex2oat) },
                                    headlineContent = { Text(stringResource(R.string.dex2oat_title)) },
                                    supportingContent = { Text(stringResource(R.string.dex2oat_desc)) },
                                )
                            }
                        )
                    )
                    Spacer(modifier = Modifier.height(MaxUiMetrics.sectionGap))
                    TweaksSectionTitle(stringResource(R.string.section_advanced_tools))
                    AdvancedToolsGrid(navController)

                } else {
                    SectionLoadingIndicator()
                }
            }
item {
                Spacer(modifier = Modifier.height(MaxUiMetrics.itemGap))
                if (viewModel.currentRefreshRate != null && viewModel.currentRenderer != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExpressiveTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.WebStories,
                            label = stringResource(R.string.refreshrates),
                            value = stringResource(R.string.refresh_rate_format, viewModel.currentRefreshRate.toString()),
                            showArrow = true,
                            highlight = true,
                            isLoading = viewModel.isRefreshRateLoading,
                            accent = colorScheme.primary
                        ) {
                            // Display Studio is the single source of truth for refresh-rate changes.
                            // Keep this tile as a live shortcut so the value stays visible here while
                            // the actual picker remains in one place and cannot drift out of sync.
                            MaxNavActions(navController).navigateTo(MaxDestination.DisplayStudio)
                        }

                        ExpressiveTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.SettingsSuggest,
                            label = stringResource(R.string.renderengine),
                            value = viewModel.currentRenderer!!.uppercase(),
                            showArrow = true,
                            highlight = true,
                            isLoading = viewModel.isRendererLoading,
                            accent = colorScheme.secondary
                        ) {
                            showRendererDialog = true
                        }
                    }
                } else {
                    SectionLoadingIndicator()
                }
            }

            item { TweaksSectionTitle(stringResource(R.string.section_addons)) }
            item {
                ExpressiveList(
                    content = listOf(
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.FilterBAndW) },
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ColorScheme) },
                                headlineContent = { Text(stringResource(R.string.color_scheme)) },
                                supportingContent = { Text(stringResource(R.string.schemecolordesc)) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { LeadingIcon(icon = Icons.Filled.AddToPhotos) },
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.PreferenceTweaks) },
                                headlineContent = { Text(stringResource(R.string.prefs)) },
                                supportingContent = { Text(stringResource(R.string.prefsdesc)) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                            )
                        }
                    )
                )
            }
        }
    }

    RootAppDialog {
        BackupRestoreBottomSheet(
            show = showBackupRestoreSheet,
            onDismiss = { showBackupRestoreSheet = false },
            onBackup = {
                showBackupRestoreSheet = false
                showBackupOptionsDialog = true
            },
            onRestore = {
                openDocLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
        )
    }

    RootAppDialog {
        CustomContentDialog(
            visible = showBackupOptionsDialog,
            title = context.getString(R.string.dialog_backup_options_title),
            confirmText = context.getString(R.string.dialog_backup_options_confirm),
            confirmEnabled = optBackupTweaks || optBackupApplist,
            onDismiss = { showBackupOptionsDialog = false },
            onConfirm = {
                showBackupOptionsDialog = false
                val sdf = java.text.SimpleDateFormat("ddMMyyyy_HHmmss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())
                val dynamicFileName = "MaxManagerConfig_Backup_$timestamp.zx"
                createDocLauncher.launch(dynamicFileName)
            }
        ) {
            Column {
                Text(
                    text = stringResource(R.string.str_select_the_configurations_you),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { optBackupTweaks = !optBackupTweaks }) {
                    Checkbox(checked = optBackupTweaks, onCheckedChange = { optBackupTweaks = it })
                    Text(stringResource(R.string.str_tweak_configuration_settings), color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { optBackupApplist = !optBackupApplist }) {
                    Checkbox(checked = optBackupApplist, onCheckedChange = { optBackupApplist = it })
                    Text(stringResource(R.string.str_per_app_applist_settings), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    RootAppDialog {
        CustomContentDialog(
            visible = showRestoreDialog,
            title = context.getString(R.string.str_restore_configuration),
            confirmText = context.getString(R.string.dialog_restore_confirm),
            confirmEnabled = pendingRestoreResult?.let { result ->
                val currentSocType = PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)
                val isSocMismatch = result.socType != currentSocType
                (optRestoreTweaks && !isSocMismatch) || optRestoreApplist
            } ?: false,
            onDismiss = { showRestoreDialog = false },
            onConfirm = {
                showRestoreDialog = false


                pendingRestoreResult?.let { result ->
                    val dataToRestore = result.data
                    val currentSocType = PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)
                    val isSocMismatch = result.socType != currentSocType

                    if (dataToRestore != null) {
                        scope.launch {
                            loadingDialog.withLoading {
                                viewModel.applyRestoreData(context, dataToRestore, optRestoreTweaks && !isSocMismatch, optRestoreApplist)
                                viewModel.loadAllConfiguration(context)
                            }
                        }
                    }
                }
            }
        ) {

            pendingRestoreResult?.let { result ->
                val socName = nd.max.ui.util.BackupManager.getSocName(result.socType)
                val currentSocType = PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)
                val isSocMismatch = result.socType != currentSocType

                Column {
                    Text(
                        text = stringResource(R.string.str_backup_content_detected_select),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    if (isSocMismatch && result.hasTweaks) {
                        Text(
                            stringResource(R.string.str_warning_backup_is_for_socname, socName),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (result.hasTweaks) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (!isSocMismatch) optRestoreTweaks = !optRestoreTweaks }) {
                            Checkbox(
                                checked = optRestoreTweaks && !isSocMismatch,
                                onCheckedChange = { if (!isSocMismatch) optRestoreTweaks = it },
                                enabled = !isSocMismatch
                            )
                            Text(stringResource(R.string.str_tweak_configuration_settings), color = if (isSocMismatch) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (result.hasApplist) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { optRestoreApplist = !optRestoreApplist }) {
                            Checkbox(checked = optRestoreApplist, onCheckedChange = { optRestoreApplist = it })
                            Text(stringResource(R.string.str_per_app_applist_settings), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    RootAppDialog {
        RendererDialog(
            show = showRendererDialog,
            onDismiss = { showRendererDialog = false },
            onRenderer = { reason -> viewModel.executeSetRenderer(reason, context) }
        )
    }


    MaxScreenHelpDialog(
        visible = showScreenHelp,
        title = stringResource(R.string.tweaks_help_title),
        description = stringResource(R.string.tweaks_workspace_guidance),
        onDismiss = { showScreenHelp = false }
    )
    }


@Composable
private fun SystemPulseBoard(
    fullMode: Boolean,
    touchState: Boolean?,
    thermalState: Boolean?,
    onTouchOpen: () -> Unit,
    onCoreOpen: () -> Unit,
    onThermalOpen: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val liveCount = listOf(touchState, thermalState).count { it == true }
    val accent = if (liveCount > 0) scheme.secondary else scheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = .28f))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = .14f), Color.Transparent)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = .13f)) {
                    Icon(Icons.Rounded.AutoGraph, null, tint = accent, modifier = Modifier.padding(12.dp).size(28.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("SYSTEM PULSE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
                    Text("ROM response studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = scheme.onSurface)
                    Text(
                        if (fullMode) "Advanced controls are available. Tap a lane to tune it safely." else "Balanced controls are active. Per-app choices always take priority.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Surface(shape = RoundedCornerShape(99.dp), color = accent.copy(alpha = .11f)) {
                    Text("$liveCount LIVE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseLane(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.TouchApp,
                    label = "Input lane",
                    value = when (touchState) { true -> "Precision on"; false -> "Adaptive"; null -> "Checking" },
                    active = touchState == true,
                    accent = scheme.tertiary,
                    onClick = onTouchOpen
                )
                PulseLane(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Memory,
                    label = "Core lane",
                    value = "Topology",
                    active = true,
                    accent = scheme.primary,
                    onClick = onCoreOpen
                )
                PulseLane(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ThermostatAuto,
                    label = "Thermal lane",
                    value = when (thermalState) { true -> "Guarding"; false -> "Manual"; null -> "Checking" },
                    active = thermalState == true,
                    accent = scheme.secondary,
                    onClick = onThermalOpen
                )
            }

            Text(
                "Pulse is a readiness map, not a one-tap performance mode. It shows which system lanes are managed and keeps deeper controls deliberate.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PulseLane(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = if (active) .13f else .06f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) .27f else .13f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
            Text(if (active) "MANAGED" else "STANDBY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent)
        }
    }
}

@Composable
private fun AdvancedToolsGrid(navController: NavController) {
    val colors = MaterialTheme.colorScheme
    val tools = listOf(
        Triple("Task Monitor", "Processes & RAM", Icons.Outlined.Memory) to colors.primary,
        Triple("Log Console", "Live diagnostics", Icons.Outlined.Terminal) to colors.secondary,
        Triple("Shell Console", "Root terminal", Icons.Outlined.Terminal) to colors.tertiary,
        Triple("Property Editor", "System properties", Icons.Outlined.Dns) to colors.primary,
        Triple("Boot Image", "Backup & flash", Icons.Outlined.Memory) to colors.secondary,
        Triple("Hidden Activities", "Activity launcher", Icons.Outlined.RocketLaunch) to colors.tertiary
    )
    val routes = listOf("processmanager", "logsviewer", "terminal", "setedit", "kernelflasher", "activitylauncher")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEachIndexed { columnIndex, entry ->
                    val index = rowIndex * 2 + columnIndex
                    AdvancedToolTile(
                        title = entry.first.first,
                        subtitle = entry.first.second,
                        icon = entry.first.third,
                        accent = entry.second,
                        onClick = { navController.navigate(routes[index]) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AdvancedToolTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(120),
        label = "advancedToolPress"
    )

    Surface(
        modifier = modifier
            .height(112.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = accent.copy(alpha = 0.11f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SectionLoadingIndicator() {
    MaxLoadingState(
        title = stringResource(R.string.loading),
        message = "",
        compact = true
    )
}

/**
 * Kept as a thin wrapper (rather than deleted) so every existing call site
 * across the app — this screen and eight sub-screens — keeps working
 * unchanged. The actual trace-motif rendering now lives once in
 * [MaxManagerSectionTitle]; this just forwards to it with the tweaks-screen
 * accent so its own section labels stay primary-tinted as before.
 */
@Composable
fun TweaksSectionTitle(text: String) {
    MaxManagerSectionTitle(text = text, accent = MaterialTheme.colorScheme.primary)
}

@Composable
fun FreqLimitSliderItem(
    icon: ImageVector? = null,
    initialValue: Float,
    labels: List<String>,
    onSaved: (Float) -> Unit
) {
    var sliderValue by remember { mutableStateOf(initialValue) }
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    LeadingIcon(icon = icon, contentDescription = stringResource(R.string.freq_offset))
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = stringResource(R.string.freq_offset),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            }

            Surface(
                color = if (sliderValue.roundToInt() == 0) colorScheme.surfaceVariant else colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (sliderValue.roundToInt() == 0) stringResource(R.string.disabled) else labels[sliderValue.roundToInt()],
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (sliderValue.roundToInt() == 0) colorScheme.onSurfaceVariant else colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        MaxSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSaved(sliderValue) },
            valueRange = 0f..6f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.disabled), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
            Text(stringResource(R.string.str_40), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
        }
    }
}

@Composable
fun ExpressiveTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    showArrow: Boolean = false,
    isLoading: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val effectiveAccent = accent ?: colorScheme.primary
    val shape = RoundedCornerShape(24.dp)
    val borderColor = if (highlight) effectiveAccent.copy(alpha = 0.18f) else colorScheme.outline.copy(alpha = 0.12f)
    val containerColor by animateColorAsState(
        targetValue = if (highlight) {
            effectiveAccent.copy(alpha = 0.07f).compositeOver(colorScheme.surfaceContainerLow)
        } else {
            colorScheme.surfaceContainerLow
        },
        animationSpec = tween(280),
        label = "expressiveTileContainer"
    )
    val iconContainerColor by animateColorAsState(
        targetValue = if (highlight) effectiveAccent.copy(alpha = 0.14f) else colorScheme.surfaceContainer,
        animationSpec = tween(280),
        label = "expressiveTileIconContainer"
    )
    val iconColor by animateColorAsState(
        targetValue = if (highlight) effectiveAccent else colorScheme.onSurfaceVariant,
        animationSpec = tween(280),
        label = "expressiveTileIcon"
    )

    Surface(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = iconColor
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highlight) effectiveAccent else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showArrow) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun TweakScreenTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onMoreClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val accentIconAlpha by animateFloatAsState(
        targetValue = 1f - scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "accentIconScrollAlpha"
    )
    val colorScheme = MaterialTheme.colorScheme

    MaxManagerTopBarScrim {
        LargeTopAppBar(
            navigationIcon = {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                        .graphicsLayer { alpha = accentIconAlpha }
                ) {
                    ScreenAccentGlyph(
                        icon = Icons.Rounded.Tune,
                        accent = colorScheme.primary,
                        size = 38.dp
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.nav_tweaks),
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            actions = {
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.Rounded.HelpOutline,
                        contentDescription = stringResource(R.string.cd_screen_help)
                    )
                }
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = stringResource(R.string.cd_menu)
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars
        )
    }
}



