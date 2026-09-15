package nd.max
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.component.*
import nd.max.ui.navigation.*
import nd.max.ui.theme.MaxManagerTheme
import nd.max.ui.util.*
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RootIpcManager.bind(this)
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
    override fun onDestroy() {
        super.onDestroy()
        RootIpcManager.unbind()
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(fromTileType: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val primaryRoutes = remember { MaxDestination.PrimaryRoutes }
    val navActions = remember(navController) { MaxNavActions(navController) }
    LaunchedEffect(fromTileType) {
        when (fromTileType) {
            "bypass" -> navController.navigate(MaxDestination.BypassCharging.route) {
                popUpTo(MaxDestination.Now.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            "profile" -> navController.navigate(MaxDestination.Now.route) {
                popUpTo(MaxDestination.Now.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // Primary destination currently selected, or null on hubs/feature screens.
    // Used for cheap local state refreshes; never for device probing.
    val currentPrimaryRoute = currentRoute?.takeIf { it in primaryRoutes }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
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
    // ADR-17 session state: probed ONCE at start and refreshed explicitly.
    // The route space is ~50 destinations, so keying this on every route meant
    // a root + module probe on every hub and feature entry (F-05).
    var rootStatus by remember { mutableStateOf(false) }
    var moduleInstalled by remember { mutableStateOf(false) }
    val navItems = remember {
        MaxDestination.PrimaryDestinations.map { NavItem(it.route, it.titleRes, it.icon) }
    }
    val pendingReboot by RebootManager.pendingReboot.collectAsState()
    val refreshStatus: suspend () -> Unit = {
        val status = withContext(Dispatchers.IO) {
            RootUtils.requestRootAccess() to RootUtils.isModuleInstalled()
        }
        rootStatus = status.first
        moduleInstalled = status.second
        isBlurEnabled = settingsPrefs.getBoolean("expressive_blur_ui", false)
        RebootManager.refreshModuleFlag()
    }
    LaunchedEffect(Unit) {
        refreshStatus()
    }
    // Cheap local preference re-read when a primary destination is entered.
    // No device probe here: root/module state is owned by the session above.
    LaunchedEffect(currentPrimaryRoute) {
        isBlurEnabled = settingsPrefs.getBoolean("expressive_blur_ui", false)
    }
    val installingDialog = rememberInstallingDialog()
    val updateDialog = rememberConfirmDialog(
        onConfirm = {
            coroutineScope.launch {
                installingDialog.withInstalling {
                    val result = withContext(Dispatchers.IO) {
                        Shell.cmd(
                            "cp ${MaxManagerPaths.MODULE_APK} /data/local/tmp/MaxManager_tmp.apk",
                            "sleep 5 && pm install -r /data/local/tmp/MaxManager_tmp.apk",
                            "rm -f /data/local/tmp/MaxManager_tmp.apk",
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
                    dismiss = context.getString(R.string.dialog_update_available_dismiss),
                )
            }
            if (rebootPending) {
                rebootDialog.showConfirm(
                    title = context.getString(R.string.dialog_module_update_title),
                    content = context.getString(R.string.dialog_module_update_content),
                    confirm = context.getString(R.string.dialog_module_update_confirm),
                    dismiss = context.getString(R.string.dialog_module_update_dismiss),
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
                modifier = Modifier.fillMaxSize(),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (hasCompletedGetStarted) {
                        MaxDestination.Now.route
                    } else MaxDestination.GetStarted.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (useNavigationRail) 96.dp else 0.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .nestedScroll(nestedScrollConnection)
                        .then(
                            if (isBlurEnabled) Modifier.hazeSource(state = hazeState) else Modifier
                        ),
                    enterTransition = {
                        if (initialState.destination.route == MaxDestination.GetStarted.route && targetState.destination.route in primaryRoutes) {
                            fadeIn(animationSpec = tween(700))
                        } else if (targetState.destination.route !in primaryRoutes) {
                            fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                        } else {
                            fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                )
                        }
                    },
                    exitTransition = {
                        if (initialState.destination.route == MaxDestination.GetStarted.route && targetState.destination.route in primaryRoutes) {
                            fadeOut(animationSpec = tween(700))
                        } else if (initialState.destination.route in primaryRoutes && targetState.destination.route !in primaryRoutes) {
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                        } else {
                            fadeOut(animationSpec = tween(150))
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.route !in primaryRoutes && targetState.destination.route in primaryRoutes) {
                            fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                        } else {
                            fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                )
                        }
                    },
                    popExitTransition = {
                        if (initialState.destination.route !in primaryRoutes) {
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                        } else {
                            fadeOut(animationSpec = tween(150))
                        }
                    },
                ) {
                    maxNavGraph(navController)
                }
                AnimatedVisibility(
                    visible = rootStatus && moduleInstalled && currentRoute in primaryRoutes,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(
                        if (useNavigationRail) Alignment.CenterStart else Alignment.BottomCenter,
                    ),
                ) {
                    val onItemSelected: (String) -> Unit = { route ->
                        MaxDestination.PrimaryDestinations
                            .firstOrNull { it.route == route }
                            ?.let(navActions::navigateToPrimary)
                    }
                    if (useNavigationRail) {
                        NavigationRailBar(
                            items = navItems,
                            selectedRoute = currentRoute ?: MaxDestination.Now.route,
                            isBlurEnabled = isBlurEnabled,
                            hazeState = hazeState,
                            modifier = Modifier.align(Alignment.CenterStart),
                            onItemSelected = onItemSelected,
                        )
                    } else {
                        BottomNavBar(
                            items = navItems,
                            selectedRoute = currentRoute ?: MaxDestination.Now.route,
                            isBlurEnabled = isBlurEnabled,
                            hazeState = hazeState,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onItemSelected = onItemSelected,
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
                            1.0f to colorScheme.surface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(navBarHeight + 12.dp)
                            .align(Alignment.BottomCenter)
                            .background(bottomScrimGradient),
                    )
                }
                AnimatedVisibility(
                    visible = rootStatus && moduleInstalled && pendingReboot && currentRoute in primaryRoutes && isFabVisible.value,
                    enter = scaleIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 24.dp,
                            bottom = (if (useNavigationRail) 24.dp else 116.dp) +
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            rebootDialog.showConfirm(
                                title = context.getString(R.string.dialog_reboot_required_title),
                                content = context.getString(R.string.dialog_reboot_required_content),
                                confirm = context.getString(R.string.reboot),
                                dismiss = context.getString(R.string.dialog_update_available_dismiss),
                            )
                        },
                        icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.reboot)) },
                        text = { Text(stringResource(R.string.reboot), fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    )
                }
            }
            ConfirmDialogHost(handle = updateDialog)
            ConfirmDialogHost(handle = rebootDialog)
        }
    }
}
