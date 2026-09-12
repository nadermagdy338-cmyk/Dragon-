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

package nd.max

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.*
import nd.max.ui.subscreens.*
import nd.max.ui.theme.MaxManagerTheme
import nd.max.ui.util.*


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ====== MTK ROOT IPC DAEMON START ======
        // Connects RootIpcManager.ipc to the bound root service so MTK Game Mode & Boost
        // reads/writes go through fast, reliable root IPC instead of always falling through
        // to the (previously the only path) su-shell fallback.
        RootIpcManager.bind(this)
        // ========================================

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        val fromTileType = if (intent.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            val component = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
            }
            
            when (component?.className) {
                "nd.max.TileService.BypassChgTileService" -> "bypass"
                "nd.max.TileService.ProfileTileService" -> "profile"
                else -> null
            }
        } else null

        setContent {
            MaxManagerTheme {
                MainScreen(fromTileType)
            }
        }
    }

    // ====== MTK ROOT IPC DAEMON STOP ======
    override fun onDestroy() {
        super.onDestroy()
        RootIpcManager.unbind()
    }
    // =======================================
}

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val gradientColors: List<Color> = listOf(Color.Transparent, Color.Transparent)
)

/**
 * Extension function for smooth scrolling pager
 */
suspend fun PagerState.smoothScrollToPage(
    targetPage: Int,
    perPageDurationMs: Int = 220,
    maxDurationMs: Int = 650
) {
    val distance = targetPage - currentPage
    if (distance == 0 && currentPageOffsetFraction == 0f) return

    val pageSizePx = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()
    if (pageSizePx <= 0f) {
        animateScrollToPage(targetPage)
        return
    }

    val totalOffsetPx = (distance - currentPageOffsetFraction) * pageSizePx
    val duration = (perPageDurationMs * abs(distance)).coerceIn(perPageDurationMs, maxDurationMs)

    var previous = 0f
    scroll(scrollPriority = MutatePriority.Default) {
        Animatable(0f).animateTo(
            targetValue = totalOffsetPx,
            animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
        ) {
            scrollBy(value - previous)
            previous = value
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(fromTileType: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    // STATE: Apakah scroll animation nyala atau nggak? (Default false)
    var useScrollAnimation by remember { mutableStateOf(settingsPrefs.getBoolean("use_scroll_animation", false)) }

    val pagerRoutes = remember { listOf("home", "applist", "tweaks", "settings") }
    val pagerState = rememberPagerState(initialPage = 0) { pagerRoutes.size }
    
    // Bottom bar routes dinamis tergantung setting
    val bottomBarRoutes = remember(useScrollAnimation) {
        if (useScrollAnimation) setOf("main") 
        else setOf("home", "applist", "tweaks", "settings")
    }

    LaunchedEffect(fromTileType, useScrollAnimation) {
        val rootNav = if (useScrollAnimation) "main" else "home"
        when (fromTileType) {
            "bypass" -> {
                navController.navigate("bypasschg") {
                    popUpTo(rootNav) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            "profile" -> {
                navController.navigate(rootNav) {
                    popUpTo(rootNav) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
     
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val rawRoute = navBackStackEntry?.destination?.route
    val isOnMainPager = rawRoute == "main"
    
    // Evaluasi current route untuk highlight di BottomNavBar
    val currentRoute = if (useScrollAnimation && isOnMainPager) {
        pagerRoutes[pagerState.currentPage]
    } else {
        rawRoute
    }

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useNavigationRail = configuration.screenWidthDp >= 840
    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val hasCompletedGetStarted = remember {
        appPrefs.getBoolean("has_completed_get_started", false)
    }
    
    LaunchedEffect(Unit) {
        WallpaperCache.init(context)
    }
    
    var isBlurEnabled by remember { mutableStateOf(settingsPrefs.getBoolean("expressive_blur_ui", false)) }
    val hazeState = remember { HazeState() }
    var rootStatus by remember { mutableStateOf(false) }
    var moduleInstalled by remember { mutableStateOf(false) }

    val navItems = remember {
        listOf(
            NavItem("home", R.string.nav_home, Icons.Rounded.Home),
            NavItem("applist", R.string.nav_applist, Icons.Rounded.Widgets),
            NavItem("tweaks", R.string.nav_tweaks, Icons.Rounded.SettingsInputComponent),
            NavItem("settings", R.string.nav_settings, Icons.Rounded.Settings)
        )
    }
    
    val pendingReboot by RebootManager.pendingReboot.collectAsState()

    val refreshStatus: suspend () -> Unit = {
        val status = withContext(Dispatchers.IO) {
            RootUtils.requestRootAccess() to RootUtils.isModuleInstalled()
        }
        rootStatus = status.first
        moduleInstalled = status.second
        isBlurEnabled = settingsPrefs.getBoolean("expressive_blur_ui", false)
        useScrollAnimation = settingsPrefs.getBoolean("use_scroll_animation", false)
        RebootManager.refreshModuleFlag()
    }
    
    LaunchedEffect(rawRoute, pagerState.currentPage) {
        refreshStatus()
    }
    
    val installingDialog = rememberInstallingDialog()
    val updateDialog = rememberConfirmDialog(
        onConfirm = {
            coroutineScope.launch {
                installingDialog.withInstalling {
                    val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        Shell.cmd(
                            "cp ${MaxManagerPaths.MODULE_APK} /data/local/tmp/MaxManager_tmp.apk",
                            "sleep 5 && pm install -r /data/local/tmp/MaxManager_tmp.apk",
                            "rm -f /data/local/tmp/MaxManager_tmp.apk"
                        ).exec()
                    }
                    if (result.isSuccess) {
                        Toast.makeText(context, context.getString(R.string.toast_update_success), Toast.LENGTH_SHORT).show()
                    } else {
                        val errorLog = result.out.joinToString("\n").ifEmpty { context.getString(R.string.status_unknown) }
                        Toast.makeText(context, context.getString(R.string.toast_install_fail, errorLog), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val rebootDialog = rememberConfirmDialog(
        onConfirm = {
            Shell.cmd("svc power reboot || reboot").submit()
        }
    )
    
    LaunchedEffect(rootStatus) {
        if (rootStatus) {
            val (moduleVC, apkAvailable, rebootPending) = withContext(Dispatchers.IO) {
                Triple(
                    RootUtils.getModuleVersionCode(),
                    RootUtils.isUpdateApkAvailable(),
                    RootUtils.isModuleUpdatePendingReboot(),
                )
            }
            val appVC = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }

            if (appVC < moduleVC && apkAvailable) {
                updateDialog.showConfirm(
                    title = context.getString(R.string.dialog_update_available_title),
                    content = context.getString(R.string.dialog_update_available_content, appVC, moduleVC),
                    confirm = context.getString(R.string.dialog_update_available_confirm),
                    dismiss = context.getString(R.string.dialog_update_available_dismiss)
                )
            }

            if (rebootPending) {
                rebootDialog.showConfirm(
                    title = context.getString(R.string.dialog_module_update_title),
                    content = context.getString(R.string.dialog_module_update_content),
                    confirm = context.getString(R.string.dialog_module_update_confirm),
                    dismiss = context.getString(R.string.dialog_module_update_dismiss)
                )
            }
        }
    }
    
    val isFabVisible = remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -10f) isFabVisible.value = false
                    else if (available.y > 10f) isFabVisible.value = true
                }
                return Offset.Zero
            }
        }
    }

   
    CompositionLocalProvider(LocalAppHazeState provides hazeState) {
        RootDialogsProvider {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                NavHost(
                    navController = navController,
                    // Penentuan start destination dinamis
                    startDestination = if (hasCompletedGetStarted) {
                        if (useScrollAnimation) "main" else "home"
                    } else "get_started",
                    
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (useNavigationRail) 96.dp else 0.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .nestedScroll(nestedScrollConnection)
                        .then(
                            if (isBlurEnabled) Modifier.hazeSource(state = hazeState) else Modifier
                        ),
                    enterTransition = {
                        if (initialState.destination.route == "get_started" && targetState.destination.route in bottomBarRoutes) {
                            fadeIn(animationSpec = tween(700)) 
                        } else if (targetState.destination.route !in bottomBarRoutes) {
                            // Navigation must not imply a left-to-right reading direction.
                            // A small scale/fade works equally well in RTL and LTR locales.
                            fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                        } else {
                            fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            )
                        }
                    },
                    exitTransition = {
                        if (initialState.destination.route == "get_started" && targetState.destination.route in bottomBarRoutes) {
                            fadeOut(animationSpec = tween(700))
                        } else if (initialState.destination.route in bottomBarRoutes && targetState.destination.route !in bottomBarRoutes) {
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                        } else {
                            fadeOut(animationSpec = tween(150))
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.route !in bottomBarRoutes && targetState.destination.route in bottomBarRoutes) {
                            fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                        } else {
                            fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            )
                        }
                    },
                    popExitTransition = {
                        if (initialState.destination.route !in bottomBarRoutes) {
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                        } else {
                            fadeOut(animationSpec = tween(150))
                        }
                    }
                ) {
                    composable("get_started") { GetStartedScreen(navController) }
                    
                    // Route Pager (Kode 2)
                    composable("main") {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (pagerRoutes[page]) {
                                "home" -> HomeScreen(navController, isVisible = pagerState.currentPage == page)
                                "applist" -> ApplistScreen(navController)
                                "tweaks" -> TweakScreen(navController)
                                "settings" -> SettingsScreen(navController)
                            }
                        }
                    }
                    
                    // Route Normal (Kode 1)
                    composable("home") { HomeScreen(navController) }
                    composable("applist") { ApplistScreen(navController) }
                    composable("tweaks") { TweakScreen(navController) }
                    composable("settings") { SettingsScreen(navController) }

                    // Subscreens
                    composable("color_palette") { ColorPaletteScreen(navController) }
                    composable("colorscheme") { ColorSchemeSettings(navController) }
                    composable("FasScreen") { FasScreen(navController) }
                    composable("bypasschg") { BypassChargeScreen(navController) }
                    composable("bypasschg_check") { BypassChargeCheckScreen(navController) }
                    composable("preferenced") { PreferenceTweakScreen(navController) }
                    composable("aboutscreen") { AboutScreen(navController) }
                    composable("diagnostics") { DiagnosticsScreen(navController) }
                    composable("fpsgoscreen") { FpsGoSettings(navController) }
                    composable("governorsettings") { GovSettings(navController) }
                    composable("gpustudio") { nd.max.ui.subscreens.GpuStudioScreen(navController) }
                    // Bounded migration aliases: old callers open the single canonical studio.
                    composable("maligpufreq") { nd.max.ui.subscreens.GpuStudioScreen(navController) }
                    composable("adrenogpufreq") { nd.max.ui.subscreens.GpuStudioScreen(navController) }
                    composable("networkscheduler") { nd.max.ui.subscreens.NetworkSchedulerScreen(navController) }
                    composable("cpucorecontrol") { nd.max.ui.subscreens.CpuCoreControlScreen(navController) }
                    composable("resolutionscreen") { nd.max.ui.subscreens.ResolutionScreen(navController) }
                    composable("zrammanager") { nd.max.ui.subscreens.ZramManagerScreen(navController) }
                    composable("touchboost") { nd.max.ui.subscreens.TouchBoostScreen(navController) }
                    composable("displaystudio") { nd.max.ui.subscreens.DisplayStudioScreen(navController) }
                    composable("chargingscreen") { nd.max.ui.subscreens.ChargingScreen(navController) }
                    composable("debloatfreeze") { nd.max.ui.subscreens.DebloatFreezeScreen(navController) }
                    composable("dex2oat") { nd.max.ui.subscreens.Dex2oatScreen(navController) }
                    composable("fpsoverlay") { nd.max.ui.subscreens.FpsOverlayScreen(navController) }
                    composable("dozemode") { nd.max.ui.subscreens.DozeModeScreen(navController) }
                    composable("processmanager") { nd.max.ui.subscreens.ProcessManagerScreen(navController) }
                    composable("setedit") { nd.max.ui.subscreens.SetEditScreen(navController) }
                    composable("logsviewer") { nd.max.ui.subscreens.LogsViewerScreen(navController) }
                    composable("terminal") { nd.max.ui.terminal.TerminalScreen() }
                    composable("activitylauncher") { nd.max.ui.activitylauncher.ActivityLauncherScreen(navController) }
                    composable("kernelflasher") { nd.max.ui.flasher.KernelFlasherScreen(navController) }
                    composable("mtkscreen") { nd.max.ui.gpu.mtk.MtkScreen(navController) }
                    // Dashboard detail screens
                    composable("thermal_detail") { nd.max.ui.mainscreens.ThermalDetailScreen(navController) }
                    composable("storage_detail") { nd.max.ui.mainscreens.StorageDetailScreen(navController) }
                    composable("network_detail") { nd.max.ui.mainscreens.NetworkDetailScreen(navController) }
                    composable("battery_detail") { nd.max.ui.mainscreens.BatteryDetailScreen(navController) }
                    composable(
                        route = "app_settings/{pkg}",
                        arguments = listOf(navArgument("pkg") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pkg = backStackEntry.arguments?.getString("pkg")
                        AppSettingsScreen(navController, pkg)
                    }

                    // محرك MAX AI الموحد: حلّ محل شاشات التنبؤ/التوصيات/
                    // التعلم/لوحة القيادة المتفرقة السابقة.
                    composable("maxai") { nd.max.ui.mainscreens.MaxAiScreen(navController) }
                }
                
                AnimatedVisibility(
                    visible = rootStatus && moduleInstalled && rawRoute in bottomBarRoutes,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(
                        if (useNavigationRail) Alignment.CenterStart else Alignment.BottomCenter
                    )
                ) {
                    if (useNavigationRail) {
                        NavigationRailBar(
                            items = navItems,
                            selectedRoute = currentRoute ?: "home",
                            isBlurEnabled = isBlurEnabled,
                            hazeState = hazeState,
                            modifier = Modifier.align(Alignment.CenterStart),
                            onItemSelected = { route ->
                            if (useScrollAnimation) {
                                // Logic klik untuk Pager (Kode 2)
                                val targetIndex = pagerRoutes.indexOf(route)
                                if (isOnMainPager) {
                                    if (pagerState.currentPage != targetIndex) {
                                        coroutineScope.launch {
                                            pagerState.smoothScrollToPage(targetIndex)
                                        }
                                    }
                                } else {
                                    navController.navigate("main") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(targetIndex)
                                    }
                                }
                            } else {
                                // Logic klik untuk Normal NavHost (Kode 1)
                                if (rawRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            }
                        }
                        )
                    } else {
                        BottomNavBar(
                            items = navItems,
                            selectedRoute = currentRoute ?: "home",
                            isBlurEnabled = isBlurEnabled,
                            hazeState = hazeState,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onItemSelected = { route ->
                                if (useScrollAnimation) {
                                    val targetIndex = pagerRoutes.indexOf(route)
                                    if (isOnMainPager) {
                                        if (pagerState.currentPage != targetIndex) {
                                            coroutineScope.launch { pagerState.smoothScrollToPage(targetIndex) }
                                        }
                                    } else {
                                        navController.navigate("main") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = false }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                        coroutineScope.launch { pagerState.scrollToPage(targetIndex) }
                                    }
                                } else {
                                    if (rawRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = false }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                
                val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                if (!useNavigationRail && navBarHeight > 32.dp) {
                    val colorScheme = MaterialTheme.colorScheme
                    val bottomScrimGradient = remember(colorScheme) {
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.1f to colorScheme.surface.copy(alpha = 0.3f),
                            0.2f to colorScheme.surface.copy(alpha = 0.4f),
                            0.3f to colorScheme.surface.copy(alpha = 0.5f),
                            0.4f to colorScheme.surface.copy(alpha = 0.7f),
                            0.5f to colorScheme.surface.copy(alpha = 0.8f),
                            0.6f to colorScheme.surface.copy(alpha = 0.9f),
                            1.0f to colorScheme.surface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(navBarHeight + 12.dp)
                            .align(Alignment.BottomCenter)
                            .background(bottomScrimGradient)
                    )
                }

                AnimatedVisibility(
                    visible = rootStatus && moduleInstalled && pendingReboot && rawRoute in bottomBarRoutes && isFabVisible.value,
                    enter = scaleIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 24.dp,
                            bottom = (if (useNavigationRail) 24.dp else 116.dp) +
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            rebootDialog.showConfirm(
                                title = context.getString(R.string.dialog_reboot_required_title),
                                content = context.getString(R.string.dialog_reboot_required_content),
                                confirm = context.getString(R.string.reboot),
                                dismiss = context.getString(R.string.dialog_update_available_dismiss)
                            )
                        },
                        icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.reboot)) },
                        text = { Text(stringResource(R.string.reboot), fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    )
                }
            }
            ConfirmDialogHost(handle = updateDialog)
            ConfirmDialogHost(handle = rebootDialog)
            InstallingDialogHost(handle = installingDialog)
        }
    }
}

@Composable
private fun NavigationRailBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = false,
    hazeState: HazeState? = null
) {
    val navigationShape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(104.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
            .clip(navigationShape)
            .then(
                if (isBlurEnabled && hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect { blurRadius = 24.dp }
                    }
                } else Modifier
            ),
        shape = navigationShape,
        color = if (isBlurEnabled) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shadowElevation = if (isBlurEnabled) 0.dp else 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            items.forEach { item ->
                val isSelected = selectedRoute == item.route
                NavRailPill(
                    item = item,
                    isSelected = isSelected,
                    isBlurEnabled = isBlurEnabled,
                    onClick = { onItemSelected(item.route) }
                )
            }
        }
    }
}

@Composable
private fun NavRailPill(
    item: NavItem,
    isSelected: Boolean,
    isBlurEnabled: Boolean,
    onClick: () -> Unit
) {
    NavPill(
        item = item,
        isSelected = isSelected,
        isBlurEnabled = isBlurEnabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
    )
}
@Composable
fun BottomNavBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = false,
    hazeState: HazeState? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)) 
                .then(
                    if (isBlurEnabled && hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = 24.dp
                            }
                        }
                    } else Modifier
                ),
            shape = RoundedCornerShape(28.dp),
            color = if (isBlurEnabled) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = if (isBlurEnabled) 0.dp else 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = selectedRoute == item.route
                    NavPill(
                        item = item,
                        isSelected = isSelected,
                        isBlurEnabled = isBlurEnabled, 
                        onClick = { onItemSelected(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    item: NavItem,
    isSelected: Boolean,
    isBlurEnabled: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (isSelected) colors.primaryContainer else Color.Transparent,
        tween(MaxMotion.standard), label = "studioNavBackground"
    )
    val foreground by animateColorAsState(
        if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        tween(MaxMotion.fast), label = "studioNavForeground"
    )
    val lift by animateFloatAsState(if (isSelected) 1.08f else 1f, MaxMotion.controlSpring, label = "studioNavIcon")
    Column(
        modifier = modifier
            .maxPressMotion(interactionSource)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (!isSelected) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = 3.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Icon(item.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(23.dp).scale(lift))
        Text(
            stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = foreground,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}