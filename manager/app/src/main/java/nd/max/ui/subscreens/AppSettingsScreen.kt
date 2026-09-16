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

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.util.AppConfig
import nd.max.ui.util.PerAppCpuControlMode
import nd.max.ui.util.PerAppCpuPolicyControl
import nd.max.ui.util.decodePerAppCpuPolicyControls
import nd.max.ui.util.encodePerAppCpuPolicyControls
import nd.max.ui.util.getSupportedDownscaleFactors
import nd.max.ui.util.getSupportedRefreshRates
import nd.max.ui.util.PerAppKernelUtil
import nd.max.ui.util.ProfilePresetStore
import nd.max.ui.util.customizedFieldCount
import nd.max.ui.viewmodel.AppSettingsViewModel
import nd.max.ui.viewmodel.ApplistViewmodel
import nd.max.ui.mainscreens.LabelText
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.navigation.MaxDestination

// ────────────────────────────────────────────────────────────────────────────
// Main Screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun AppSettingsScreen(
    navController: NavController,
    packageName: String?,
    viewModel: AppSettingsViewModel = viewModel(),
    appListViewModel: ApplistViewmodel = viewModel()
) {
    val context = LocalContext.current
    val appDetails = remember(packageName) { getAppDetails(context, packageName) }
    val isGameApp = remember(packageName) { isGameCategory(context, packageName) }
    val colorScheme = MaterialTheme.colorScheme
    var showProfileEditor by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(packageName) { viewModel.loadConfig(); viewModel.loadKernelCapabilities(packageName) }

    val config = viewModel.fullConfig[packageName]
    var localMasterOn by remember(config != null) { mutableStateOf(config != null) }
    var userToggled by remember { mutableStateOf(false) }
    LaunchedEffect(packageName, localMasterOn) {
        if (localMasterOn) viewModel.refreshCpuRuntimeStatus(packageName)
    }

    val rawRefreshModes = remember { getSupportedRefreshRates(context) }
    val rawDownscaleSteps = remember { getSupportedDownscaleFactors() }
    val defaultLabel = stringResource(R.string.default_label)

    DisposableEffect(Unit) {
        onDispose { appListViewModel.loadApps(context, forceRefresh = true) }
    }

    var showGuide by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    FeatureGuideDialog(
        visible = showGuide,
        onDismiss = { showGuide = false }
    )

    // ── Per-App Tabs: Performance | Display | Gaming | Power & Connectivity ──
    // Grouping settings by intent instead of one long scroll keeps this screen
    // scannable as more per-app controls get added over time. Each tab shows
    // how many values are already customized (non-Default) for that group, so
    // the user doesn't have to open every tab to know what's active.
    val cfgForTabs = config ?: AppConfig()
    var selectedTab by remember(packageName) { mutableStateOf(0) }

    // CPU/GPU governors are now regular per-app controls; their lists are detected from the live kernel.
    val appTabs = listOf(
        AppSettingsTabInfo(
            label = stringResource(R.string.app_tab_performance),
            icon = Icons.Filled.Speed,
            badgeCount = cfgForTabs.performanceCustomizedCount()
        ),
        AppSettingsTabInfo(
            label = stringResource(R.string.app_tab_display),
            icon = Icons.Filled.Monitor,
            badgeCount = cfgForTabs.displayCustomizedCount(isGameApp)
        ),
        AppSettingsTabInfo(
            label = stringResource(R.string.app_tab_gaming),
            icon = Icons.Filled.Gamepad,
            badgeCount = cfgForTabs.gamingCustomizedCount()
        ),
        AppSettingsTabInfo(
            label = stringResource(R.string.app_tab_power),
            icon = Icons.Filled.BatteryChargingFull,
            badgeCount = cfgForTabs.powerCustomizedCount()
        )
    )

    ScreenAccentProvider(colorScheme.secondary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AppSettingsTopAppBar(
                    scrollBehavior = scrollBehavior,
                    onLaunchApp = {
                        packageName?.let { pkg ->
                            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_app_launch_fail), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onOpenAppInfo = {
                        packageName?.let { pkg ->
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$pkg")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onShowGuide = { showGuide = true },
                    onBack = {
                        appListViewModel.loadApps(context, forceRefresh = true)
                        navController.popBackStack()
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            ) {

                // ── App Hero Header ──────────────────────────────────────────
                item { AppHeroHeader(appDetails = appDetails, packageName = packageName) }

                // ── Master Switch (Hero Card) ────────────────────────────────
                item {
                    MasterSwitchCard(
                        isEnabled = localMasterOn,
                        onToggle = { checked ->
                            userToggled = true
                            localMasterOn = checked
                            packageName?.let { viewModel.toggleMasterSwitch(it, checked) }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    // ربط ملف التطبيق بـ Max AI: يشرح علاقة الأولوية —
                    // ملف التطبيق يتولّى والمحرك يراقب أثناء فتح التطبيق.
                    AnimatedVisibility(visible = localMasterOn) {
                        Column {
                            MaxManagerInsight(
                                text = stringResource(R.string.app_profile_maxai_note),
                                accent = colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // ── All Settings ─────────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = localMasterOn,
                        enter = if (userToggled) expandVertically(tween(380)) + fadeIn(tween(380)) else EnterTransition.None,
                        exit = shrinkVertically(tween(380)) + fadeOut(tween(200))
                    ) {
                        val cfg = config ?: AppConfig()
                        Column {

                            PerAppSystemBridge(
                                customizedCount = cfg.customizedFieldCount(),
                                isGameApp = isGameApp,
                                onOpenLive = { navController.navigate(MaxDestination.MaxAi.route) },
                                onOpenControl = { navController.navigate(MaxDestination.Control.route) }
                            )
                            Spacer(Modifier.height(10.dp))

                            AppSettingsTabRow(
                                tabs = appTabs,
                                selectedIndex = selectedTab,
                                onSelect = { selectedTab = it }
                            )
                            Spacer(Modifier.height(4.dp))

                            when (selectedTab) {
                            0 -> {
                            // ══ PERFORMANCE ══════════════════════════════════
                            ExpressiveList(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                content = buildList {
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Bolt,
                                            title = stringResource(R.string.perf_lite_mode),
                                            summary = stringResource(R.string.perf_lite_mode_desc_short),
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.perf_lite_mode),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "perf_lite_mode", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.FlashOn,
                                            title = "CPU Boost on Launch",
                                            summary = "Temporarily boost CPU clocks when this app opens",
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.cpu_boost),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "cpu_boost", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.RocketLaunch,
                                            title = stringResource(R.string.game_preload),
                                            summary = stringResource(R.string.game_preload_desc),
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.game_preload),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "game_preload", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.SwapVerticalCircle,
                                            title = stringResource(R.string.app_priority),
                                            summary = stringResource(R.string.app_priority_desc),
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.app_priority),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "app_priority", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Block,
                                            title = "Kill Background Apps",
                                            summary = "Clear background processes while this app is in foreground",
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.kill_bg_apps),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "kill_bg_apps", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                }
                            )

                            // ══ GPU / GOVERNOR CONTROL ══════════════════════════
                            SettingsSectionTitle(Icons.Filled.Thermostat, "Thermal & GPU Governor", colorScheme.error)
                            Text(
                                text = "Default leaves the device untouched and lets HyperOS / Game Turbo manage the app.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            val gpuProfileValues = listOf("default", "power", "balanced", "gaming", "performance", "custom")
                            val gpuProfileLabels = listOf("Default", "Power", "Balanced", "Gaming", "Performance", "Custom")
                            ThermalProfilePicker(
                                selected = cfg.gpu_profile,
                                labels = gpuProfileLabels,
                                values = gpuProfileValues,
                                onSelectProfile = { profile -> packageName?.let { viewModel.updateSetting(it, "gpu_profile", profile) } }
                            )
                            ExpressiveList(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                content = buildList {
                                    add {
                                        val cpuGovernorValues = listOf("default") + viewModel.availableCpuGovernors
                                        val cpuGovernorLabels = listOf(defaultLabel) + viewModel.availableCpuGovernors
                                        val selected = cpuGovernorValues.indexOfFirst { it.equals(cfg.cpu_governor, true) }.coerceAtLeast(0)
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Memory,
                                            title = "CPU Governor",
                                            summary = if (viewModel.availableCpuGovernors.isEmpty()) "No common CPU governors detected" else "Only governors supported by all CPU policies are shown",
                                            items = cpuGovernorLabels,
                                            selectedIndex = selected,
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "cpu_governor", cpuGovernorValues[i]) } }
                                        )
                                    }
                                    add {
                                        val gpuGovernorValues = listOf("default") + viewModel.availableGpuGovernors
                                        val gpuGovernorLabels = listOf(defaultLabel) + viewModel.availableGpuGovernors
                                        val selected = gpuGovernorValues.indexOfFirst { it.equals(cfg.gpu_governor, true) }.coerceAtLeast(0)
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.DeveloperBoard,
                                            title = "GPU Governor",
                                            summary = if (viewModel.availableGpuGovernors.isEmpty()) "No GPU governor node detected" else "Only governors reported by the GPU driver are shown",
                                            items = gpuGovernorLabels,
                                            selectedIndex = selected,
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "gpu_governor", gpuGovernorValues[i]) } }
                                        )
                                    }
                                    add {
                                        val freqItems = listOf("default") + viewModel.availableGpuFrequencies.map { it.toString() }
                                        val freqLabels = listOf(defaultLabel) + viewModel.availableGpuFrequencies.map { PerAppKernelUtil.formatFrequency(it) }
                                        val selected = freqItems.indexOf(cfg.gpu_max_freq).coerceAtLeast(0)
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Tune,
                                            title = "GPU Fixed Frequency",
                                            summary = if (viewModel.availableGpuFrequencies.isEmpty()) "Frequency control is unavailable on this kernel" else "Locks the GPU to exactly this frequency while the app is open, overriding GPU Governor above. Default leaves it dynamic.",
                                            items = freqLabels,
                                            selectedIndex = selected,
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "gpu_max_freq", freqItems[i]) } }
                                        )
                                    }
                                }
                            )
                            PerAppCpuControlSection(
                                encodedControls = cfg.cpu_policy_controls,
                                policies = viewModel.cpuPolicies,
                                runtimeStatus = viewModel.cpuRuntimeStatus,
                                onSave = { encoded -> packageName?.let { viewModel.updateSetting(it, "cpu_policy_controls", encoded) } },
                                onRefreshStatus = { viewModel.refreshCpuRuntimeStatus(packageName) }
                            )
                            Spacer(Modifier.height(8.dp))
                            nd.max.ui.component.StudioOutlinedButton(
                                onClick = { showProfileEditor = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Customize Power / Balanced / Gaming / Performance")
                            }
                            nd.max.ui.component.StudioTextButton(
                                onClick = { showResetConfirmation = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Reset all app settings to Default")
                            }
                            } // end tab 0: Performance
                            1 -> {
                            // ══ DISPLAY & RENDER ══════════════════════════════
                            ExpressiveList(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                content = buildList {
                                    // Refresh Rate – always visible (no longer locked behind full mode)
                                    add {
                                        val refreshLabels = rawRefreshModes.map { if (it.equals("default", true)) defaultLabel else "${it}Hz" }
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Refresh,
                                            title = stringResource(R.string.refreshrates),
                                            summary = stringResource(R.string.refreshrates_desc),
                                            items = refreshLabels,
                                            selectedIndex = rawRefreshModes.indexOfFirst { it.equals(cfg.refresh_rate, true) }.coerceAtLeast(0),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "refresh_rate", rawRefreshModes[i]) } }
                                        )
                                    }
                                    // Render Engine
                                    add {
                                        val rendererValues = listOf("default","skiavk","skiavkthreaded","skiagl","skiaglthreaded","opengl","openglthreaded","vulkan")
                                        val rendererLabels = listOf(defaultLabel,"SkiaVK","SkiaVK (Threaded)","SkiaGL","SkiaGL (Threaded)","OpenGL ES","OpenGL ES (Threaded)","Vulkan")
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Layers,
                                            title = stringResource(R.string.renderengine),
                                            summary = stringResource(R.string.renderengine_desc),
                                            items = rendererLabels,
                                            selectedIndex = rendererValues.indexOfFirst { it.equals(cfg.renderer, true) }.coerceAtLeast(0),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "renderer", rendererValues[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Brush,
                                            title = "Force Hardware UI Rendering",
                                            summary = "Force GPU-accelerated rendering for all UI layers",
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.force_hw_ui),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "force_hw_ui", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                }
                            )

                            // ══ RESOLUTION (game apps only) ════════════════════
                            if (isGameApp) {
                                SettingsSectionTitle(Icons.Filled.AspectRatio, stringResource(R.string.section_resolution_settings), colorScheme.secondary)
                                ExpressiveList(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    content = buildList {
                                        add {
                                            val downscaleLabels = rawDownscaleSteps.map { if (it == "default") defaultLabel else it }
                                            var downPos by remember(cfg.resolution_downscale) {
                                                mutableStateOf(rawDownscaleSteps.indexOf(cfg.resolution_downscale).coerceAtLeast(0).toFloat())
                                            }
                                            ExpressiveSliderItem(
                                                icon = Icons.Rounded.AspectRatio,
                                                title = stringResource(R.string.resolution_downscale_title),
                                                subtitle = stringResource(R.string.resolution_downscale_desc),
                                                badgeText = downscaleLabels.getOrElse(downPos.toInt()) { defaultLabel },
                                                sliderPosition = downPos,
                                                valueRange = 0f..(rawDownscaleSteps.size - 1).toFloat(),
                                                steps = (rawDownscaleSteps.size - 2).coerceAtLeast(0),
                                                onValueChange = { downPos = it },
                                                onValueChangeFinished = {
                                                    packageName?.let { viewModel.updateSetting(it, "resolution_downscale", rawDownscaleSteps[downPos.toInt()]) }
                                                }
                                            )
                                        }
                                    }
                                )
                            }

                            } // end tab 1: Display
                            2 -> {
                            // ══ GAMING EXPERIENCE ══════════════════════════════
                            ExpressiveList(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                content = buildList {
                                    add {
                                        run {
                                            val touchProfiles = listOf(
                                                "default" to "Follow ROM",
                                                "true" to "Responsive",
                                                "false" to "Battery aware"
                                            )
                                            ExpressiveDropdownItem(
                                                icon = Icons.Rounded.TouchApp,
                                                title = "Touch Response Profile",
                                                summary = "Responsive enables the available touch boost while this app is focused; Battery aware keeps it off. Follow ROM respects the global setting and compatible game detection.",
                                                items = touchProfiles.map { it.second },
                                                selectedIndex = touchProfiles.indexOfFirst { it.first == cfg.touch_boost }.coerceAtLeast(0),
                                                onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "touch_boost", touchProfiles[i].first) } }
                                            )
                                        }
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Vibration,
                                            title = "Reduce Haptic Feedback",
                                            summary = "Disable or reduce vibration to free up CPU cycles",
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.haptic_feedback),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "haptic_feedback", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.DoNotDisturbOn,
                                            title = stringResource(R.string.dnd_mode),
                                            summary = stringResource(R.string.dnd_mode_desc),
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.dnd_on_gaming),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "dnd_on_gaming", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Filled.NotificationsOff,
                                            title = "Silence Notifications",
                                            summary = "Block notification sounds and badges while this app is active",
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.disable_notifs),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "disable_notifs", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                }
                            )

                            } // end tab 2: Gaming
                            3 -> {
                            // ══ CONNECTIVITY & POWER ═══════════════════════════
                            ExpressiveList(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                content = buildList {
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Cable,
                                            title = stringResource(R.string.bcharging),
                                            summary = stringResource(R.string.enable_bypass_charge_desc),
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.bypass_charging),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "bypass_charging", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                    add {
                                        ExpressiveDropdownItem(
                                            icon = Icons.Rounded.Wifi,
                                            title = "WiFi No-Sleep",
                                            summary = "Prevent WiFi from sleeping while this app is active",
                                            items = listOf(defaultLabel, stringResource(R.string.on_label), stringResource(R.string.off_label)),
                                            selectedIndex = getBoolIndex(cfg.wifi_no_sleep),
                                            onItemSelected = { i -> packageName?.let { viewModel.updateSetting(it, "wifi_no_sleep", listOf("default","true","false")[i]) } }
                                        )
                                    }
                                }
                            )
                            } // end tab 3: Power & Connectivity
                            } // end when(selectedTab)

                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showProfileEditor) {
        ProfilePresetEditor(onDismiss = { showProfileEditor = false })
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.app_reset_confirm_title)) },
            text = { Text(stringResource(R.string.app_reset_confirm_message)) },
            confirmButton = {
                nd.max.ui.component.StudioTextButton(onClick = {
                    packageName?.let { viewModel.resetAppSettings(it) }
                    showResetConfirmation = false
                }) { Text(stringResource(R.string.reset_label)) }
            },
            dismissButton = {
                nd.max.ui.component.StudioTextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.cancel_label))
                }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────────

private fun getBoolIndex(v: String?): Int = when (v) { "true" -> 1; "false" -> 2; else -> 0 }

@Composable
private fun PerAppSystemBridge(
    customizedCount: Int,
    isGameApp: Boolean,
    onOpenLive: () -> Unit,
    onOpenControl: () -> Unit,
) {
    MaxManagerInsight(
        text = stringResource(
            R.string.app_settings_system_bridge,
            customizedCount,
            if (isGameApp) stringResource(R.string.applist_filter_games) else stringResource(R.string.applist_filter_all)
        ),
        accent = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nd.max.ui.component.StudioOutlinedButton(onClick = onOpenLive, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Timeline, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.app_settings_open_live))
        }
        nd.max.ui.component.StudioOutlinedButton(onClick = onOpenControl, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Tune, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.app_settings_open_control))
        }
    }
}

@Composable
private fun PerAppCpuControlSection(
    encodedControls: String,
    policies: List<nd.max.core.hardware.CpuHardwareBackend.Policy>,
    runtimeStatus: nd.max.ui.util.PerAppCpuRuntimeStatus,
    onSave: (String) -> Unit,
    onRefreshStatus: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val saved = remember(encodedControls) { decodePerAppCpuPolicyControls(encodedControls).associateBy { it.policyName } }
    val drafts = remember(encodedControls) { mutableStateMapOf<String, PerAppCpuPolicyControl>().apply { putAll(saved) } }
    val configured = drafts.values.toList()
    val summary = when {
        configured.isEmpty() -> "CPU: الافتراضي"
        configured.any { it.mode == PerAppCpuControlMode.EXACT_LOCK } -> "CPU: Exact Lock · ${configured.size}"
        else -> "CPU: Dynamic Range · ${configured.size}"
    }
    val controllable = policies.filter { it.cpuFrequencyChoices().isNotEmpty() }

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f))
        ) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("خيارات متقدمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefreshStatus) { Icon(Icons.Rounded.Refresh, "Refresh status", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (controllable.isEmpty()) {
                    Text("CPU frequency control is unavailable on this kernel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                controllable.forEach { policy ->
                    val choices = policy.cpuFrequencyChoices()
                    val draft = drafts[policy.name] ?: PerAppCpuPolicyControl(policy.name, PerAppCpuControlMode.DEFAULT, policy.minKHz ?: choices.first(), policy.maxKHz ?: choices.last())
                    val modes = listOf(PerAppCpuControlMode.DEFAULT, PerAppCpuControlMode.DYNAMIC_RANGE, PerAppCpuControlMode.EXACT_LOCK)
                    val labels = listOf("Default", "Dynamic Range", "Exact Lock")
                    val minIndex = choices.cpuIndexFor(draft.minKHz)
                    val maxIndex = choices.cpuIndexFor(draft.maxKHz).coerceAtLeast(minIndex)
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(policy.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${policy.governor ?: "Default"} · ${formatCpuKHz(policy.minKHz ?: choices.first())} – ${formatCpuKHz(policy.maxKHz ?: choices.last())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ExpressiveDropdownItem(
                                icon = Icons.Rounded.Tune,
                                title = "Mode",
                                summary = labels[modes.indexOf(draft.mode)],
                                items = labels,
                                selectedIndex = modes.indexOf(draft.mode),
                                onItemSelected = { index ->
                                    when (val mode = modes[index]) {
                                        PerAppCpuControlMode.DEFAULT -> drafts.remove(policy.name)
                                        PerAppCpuControlMode.DYNAMIC_RANGE -> drafts[policy.name] = draft.copy(mode = mode, minKHz = choices[minIndex], maxKHz = choices[maxIndex])
                                        PerAppCpuControlMode.EXACT_LOCK -> drafts[policy.name] = draft.copy(mode = mode, minKHz = choices[maxIndex], maxKHz = choices[maxIndex])
                                    }
                                }
                            )
                            if (draft.mode == PerAppCpuControlMode.DYNAMIC_RANGE) {
                                CpuFrequencySelector("Minimum", choices, minIndex) { index -> drafts[policy.name] = draft.copy(minKHz = choices[index], maxKHz = choices[maxIndex.coerceAtLeast(index)]) }
                                CpuFrequencySelector("Maximum", choices, maxIndex) { index -> drafts[policy.name] = draft.copy(minKHz = choices[minIndex.coerceAtMost(index)], maxKHz = choices[index]) }
                            } else if (draft.mode == PerAppCpuControlMode.EXACT_LOCK) {
                                CpuFrequencySelector("Locked frequency", choices, maxIndex) { index -> drafts[policy.name] = draft.copy(minKHz = choices[index], maxKHz = choices[index]) }
                            }
                        }
                    }
                }
                if (runtimeStatus.isFailure || runtimeStatus.isApplied) {
                    val color = if (runtimeStatus.isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .10f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .24f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (runtimeStatus.isFailure) Icons.Outlined.ErrorOutline else Icons.Rounded.CheckCircle, null, tint = color)
                            Spacer(Modifier.width(9.dp))
                            Text(runtimeStatus.message.ifBlank { if (runtimeStatus.isFailure) "CPU control could not be applied." else "CPU controls verified for the active app." }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = onRefreshStatus) { Icon(Icons.Rounded.Refresh, "Refresh status") }
                        }
                    }
                }
                nd.max.ui.component.StudioOutlinedButton(onClick = { onSave(encodePerAppCpuPolicyControls(drafts.values)) }, modifier = Modifier.fillMaxWidth(), enabled = controllable.isNotEmpty()) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save CPU controls")
                }
            }
        }
    }
}

@Composable
private fun CpuFrequencySelector(label: String, choices: List<Long>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onSelect((selectedIndex - 1).coerceAtLeast(0)) }, enabled = selectedIndex > 0) { Icon(Icons.Rounded.Remove, "Lower") }
        Text(formatCpuKHz(choices[selectedIndex]), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onSelect((selectedIndex + 1).coerceAtMost(choices.lastIndex)) }, enabled = selectedIndex < choices.lastIndex) { Icon(Icons.Rounded.Add, "Higher") }
    }
}

private fun nd.max.core.hardware.CpuHardwareBackend.Policy.cpuFrequencyChoices(): List<Long> {
    if (availableFrequenciesKHz.isNotEmpty()) return availableFrequenciesKHz
    val minKHz = provenMinKHz
    val maxKHz = provenMaxKHz
    return if (minKHz != null && maxKHz != null && minKHz <= maxKHz) {
        listOfNotNull(minKHz, maxKHz).distinct()
    } else {
        emptyList()
    }
}

private fun List<Long>.cpuIndexFor(value: Long): Int = indexOf(value).takeIf { it >= 0 } ?: indices.minByOrNull { kotlin.math.abs(this[it] - value) } ?: 0

private fun formatCpuKHz(value: Long): String = when {
    value <= 0L -> "—"
    value >= 1_000_000L -> String.format(java.util.Locale.US, "%.2f GHz", value / 1_000_000.0)
    else -> "${value / 1000} MHz"
}

// ────────────────────────────────────────────────────────────────────────────
// Thermal Profile Chip Picker
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfilePresetEditor(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val names = listOf("power", "balanced", "gaming", "performance", "custom")
    val labels = listOf("Power", "Balanced", "Gaming", "Performance", "Custom")
    var values by remember {
        mutableStateOf(names.associateWith { ProfilePresetStore.percentFor(context, it) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Thermal / GPU Presets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose how much of the device's detected stock GPU maximum each preset may use. The nearest real OPP is selected automatically, so this works across different GPUs.",
                    style = MaterialTheme.typography.bodySmall
                )
                names.forEachIndexed { index, name ->
                    val value = values[name] ?: ProfilePresetStore.defaultPercent(name)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(labels[index], fontWeight = FontWeight.SemiBold)
                            Text("$value%")
                        }
                        MaxSlider(
                            value = value.toFloat(),
                            onValueChange = { v -> values = values + (name to v.toInt().coerceIn(20, 100)) },
                            valueRange = 20f..100f,
                            steps = 79
                        )
                    }
                }
                nd.max.ui.component.StudioTextButton(
                    onClick = {
                        ProfilePresetStore.reset(context)
                        values = names.associateWith { ProfilePresetStore.percentFor(context, it) }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore preset defaults")
                }
            }
        },
        confirmButton = {
            nd.max.ui.component.StudioTextButton(onClick = {
                values.forEach { (name, value) -> ProfilePresetStore.setPercent(context, name, value) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { nd.max.ui.component.StudioTextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ThermalProfilePicker(
    selected: String,
    labels: List<String>,
    values: List<String>,
    onSelectProfile: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val icons = listOf(
        Icons.Filled.DeviceUnknown,
        Icons.Filled.BatteryFull,
        Icons.Filled.Balance,
        Icons.Filled.SportsEsports,
        Icons.Filled.Speed,
        Icons.Filled.Tune
    )
    // Intensity ramp: neutral (auto) -> green (power save) -> blue (balanced)
    // -> amber (gaming) -> red (performance/max heat). Matches the same
    // severity language the Home dashboard's temperature card already uses,
    // so "performance profile" and "hot" read as the same kind of red
    // everywhere in the app, not two unrelated color choices.
    val accents = listOf(
        colorScheme.onSurfaceVariant,
        Color(0xFF36C77B),
        Color(0xFF3D8EFF),
        Color(0xFFFF9800),
        Color(0xFFFF4444),
        colorScheme.primary
    )
    val profiles = values.indices.map { i ->
        Triple(values[i], icons.getOrElse(i) { Icons.Filled.Memory }, labels[i]) to accents.getOrElse(i) { colorScheme.primary }
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        profiles.chunked(3).forEach { rowProfiles ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowProfiles.forEach { (profile, accent) ->
                    val (value, icon, label) = profile
                    ThermalChip(
                        modifier = Modifier.weight(1f),
                        icon = icon, label = label,
                        accent = accent,
                        isSelected = selected == value,
                        onClick = { onSelectProfile(value) }
                    )
                }
                repeat(3 - rowProfiles.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ThermalChip(modifier: Modifier = Modifier, icon: ImageVector, label: String, accent: Color, isSelected: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = if (isSelected) accent.copy(alpha = 0.16f) else colorScheme.surfaceContainer
    val fg = if (isSelected) accent else colorScheme.onSurfaceVariant
    val border = if (isSelected) accent.copy(alpha = 0.5f) else Color.Transparent
    Surface(
        modifier = modifier.height(76.dp).border(1.5.dp, border, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = bg
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (isSelected) {
                IconBadge(icon = icon, tint = accent, size = 22)
            } else {
                Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Section Title
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsSectionTitle(icon: ImageVector, title: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Backwards-compat wrapper for existing call sites. */
@Composable
fun SectionHeader(title: String) {
    MaxManagerSectionTitle(text = title, accent = MaterialTheme.colorScheme.secondary)
}

// ────────────────────────────────────────────────────────────────────────────
// Master Switch Hero Card
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MasterSwitchCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val bgBrush = if (isEnabled)
        Brush.horizontalGradient(listOf(colorScheme.primaryContainer, colorScheme.secondaryContainer))
    else
        Brush.horizontalGradient(listOf(colorScheme.surfaceContainerHigh, colorScheme.surfaceContainerHigh))
    val textColor = if (isEnabled) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(bgBrush).padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isEnabled) {
                IconBadge(icon = Icons.Filled.PlayCircleFilled, tint = colorScheme.primary, size = 36)
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = colorScheme.outline,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnabled) "AZenith Active" else stringResource(R.string.master_switch),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor
                    )
                    if (isEnabled) {
                        Spacer(Modifier.width(8.dp))
                        LivePulseDot(color = colorScheme.primary)
                    }
                }
                Text(
                    text = if (isEnabled) "Per-app optimizations are being applied" else stringResource(R.string.master_switch_desc),
                    style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.75f)
                )
            }
            MaxSwitch(
                checked = isEnabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.primary,
                    checkedTrackColor = colorScheme.primaryContainer,
                    uncheckedThumbColor = colorScheme.outline,
                    uncheckedTrackColor = colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// App Hero Header
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun AppHeroHeader(appDetails: Triple<String, android.content.pm.ApplicationInfo?, String>, packageName: String?) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = LocalDensity.current
    val iconSizePx = remember(density) { with(density) { 52.dp.roundToPx() } }

    var appBitmap by remember(packageName) { mutableStateOf(packageName?.let { AppIconCache.get(it) }) }
    LaunchedEffect(packageName, iconSizePx) {
        if (appBitmap == null && packageName != null) {
            appDetails.second?.let { info ->
                try { appBitmap = AppIconCache.loadIcon(pm, info, iconSizePx) } catch (_: Exception) {}
            }
        }
    }

    val isSystem = remember(appDetails.second) { ((appDetails.second?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0 }
    val isGame = remember(packageName) { isGameCategory(context, packageName) }
    val colors = MaterialTheme.colorScheme

    // The app identity is context, not a hero card: keep it compact so the
    // actual per-app controls arrive immediately below the top bar.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = colors.secondary,
            modifier = Modifier.size(18.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerHighest,
            modifier = Modifier.size(52.dp)
        ) {
            Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = appBitmap, animationSpec = tween(180), label = "AppIcon") { icon ->
                    if (icon != null) {
                        androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
                    } else {
                        Icon(Icons.Filled.Apps, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = appDetails.first,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName ?: stringResource(R.string.unknown_package),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AppBadge("v${appDetails.third}", colors.secondaryContainer, colors.onSecondaryContainer)
                if (isSystem) AppBadge("System", colors.tertiaryContainer, colors.onTertiaryContainer)
                if (isGame) AppBadge("Game", colors.primaryContainer, colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AppBadge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = CircleShape) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = fg)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Feature Guide Dialog
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun FeatureGuideDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    val colorScheme = MaterialTheme.colorScheme
    CustomContentDialog(
        visible = visible,
        title = "Per-App Features Guide",
        onDismiss = onDismiss,
        onConfirm = onDismiss,
        confirmText = "Got it",
        dismissText = "" // Hide cancel button
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GuideItem(Icons.Rounded.FlashOn, "CPU Boost on Launch", "Temporarily forces the CPU to its maximum frequency for 3 seconds when the app is opened, dramatically reducing load times.", colorScheme.primary)
            }
            item {
                GuideItem(Icons.Filled.Thermostat, "Thermal Profile", "Bypasses the system's default thermal throttling limits. 'Gaming' allows higher temperatures before slowing down the game, while 'Power Save' keeps the phone cool.", colorScheme.error)
            }
            item {
                GuideItem(Icons.Rounded.Memory, "GPU Governor", "Controls how aggressively the GPU ramps up. 'msm-adreno-tz' is dynamic, while 'Performance' forces maximum graphics power constantly.", colorScheme.tertiary)
            }
            item {
            }
            item {
                GuideItem(Icons.Rounded.Brush, "Force Hardware UI Rendering", "Forces GPU-accelerated rendering for this app's UI layers instead of software rendering. Takes effect from the app's next cold start, not instantly on an already-running process.", colorScheme.primaryContainer)
            }
            item {
                GuideItem(Icons.Rounded.Block, "Kill Background Apps", "Automatically executes an aggressive RAM sweep whenever this app is brought to the foreground, ensuring maximum memory is available.", colorScheme.secondary)
            }
            item {
                GuideItem(Icons.Rounded.Cable, "Bypass Charging", "If supported by the kernel, powers the motherboard directly from the charger without routing through the battery, reducing heat during heavy gaming.", colorScheme.primary)
            }
        }
    }
}

@Composable
private fun GuideItem(icon: ImageVector, title: String, desc: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun AppSettingsTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior, 
    onLaunchApp: () -> Unit, 
    onOpenAppInfo: () -> Unit, 
    onShowGuide: () -> Unit,
    onBack: () -> Unit
) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.app_settings_title),
        onBack = onBack,
        accentIcon = Icons.Filled.Apps,
        accent = MaterialTheme.colorScheme.secondary,
        actions = {
            IconButton(onClick = onShowGuide) { Icon(Icons.Rounded.HelpOutline, contentDescription = "Feature Guide") }
            IconButton(onClick = onLaunchApp) { Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = stringResource(R.string.str_launch_app)) }
            IconButton(onClick = onOpenAppInfo) { Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.str_app_info)) }
        }
    )
}

private data class AppSettingsTabInfo(
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int
)

@Composable
private fun AppSettingsTabRow(
    tabs: List<AppSettingsTabInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tab.badgeCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            LabelText(text = "${tab.badgeCount}", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    }
}

private fun AppConfig.performanceCustomizedCount(): Int = listOf(
    perf_lite_mode, cpu_boost, game_preload, app_priority, kill_bg_apps,
    gpu_profile, cpu_governor, gpu_governor, gpu_max_freq
).count { it != "default" } + if (cpu_policy_controls.isNotBlank()) 1 else 0

private fun AppConfig.displayCustomizedCount(isGameApp: Boolean): Int {
    var count = listOf(refresh_rate, renderer, force_hw_ui).count { it != "default" }
    if (isGameApp && resolution_downscale != "default") count++
    return count
}

private fun AppConfig.gamingCustomizedCount(): Int = listOf(
    touch_boost, haptic_feedback, dnd_on_gaming, disable_notifs
).count { it != "default" }

private fun AppConfig.powerCustomizedCount(): Int = listOf(
    bypass_charging, wifi_no_sleep
).count { it != "default" }

// ────────────────────────────────────────────────────────────────────────────
// App Metadata Helpers
// ────────────────────────────────────────────────────────────────────────────

@Suppress("DEPRECATION")
fun isGameCategory(context: android.content.Context, packageName: String?): Boolean {
    if (packageName.isNullOrEmpty()) return false
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    } catch (_: Exception) { false }
}

fun getAppDetails(context: android.content.Context, packageName: String?): Triple<String, android.content.pm.ApplicationInfo?, String> {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName ?: "", 0)
        val pkgInfo = pm.getPackageInfo(packageName ?: "", 0)
        Triple(pm.getApplicationLabel(info).toString(), info, pkgInfo.versionName ?: context.getString(R.string.status_unknown))
    } catch (_: Exception) {
        Triple(context.getString(R.string.unknown_app), null, "0.0.0")
    }
}
