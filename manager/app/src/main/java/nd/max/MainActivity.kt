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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
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
import nd.max.core.ipc.RootNodeChannel
import nd.max.ui.component.*
import nd.max.ui.design.LocalFloatingBottomBarHeight
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.*
import nd.max.ui.settings.AppLanguage
import nd.max.ui.theme.MaxManagerTheme
import nd.max.ui.util.*
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * لغة التطبيق تُربط هنا قبل أي مورد وقبل إنشاء الواجهة. النشاط `ComponentActivity` لا يمرّ على
     * `AppCompatDelegate` (وهو ما يعمل به تبديل اللغة على API 33+)، فاللفّ هنا هو ما يجعل الاختيار
     * المحفوظ ظاهرًا على أندرويد ١٠–١٢ بدل أن يبقى محفوظًا بلا أثر. التفصيل في `AppLanguage.wrap`.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RootNodeChannel.connect(this)
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
    /**
     * `AR-32` — مراقبة انسداد الخيط الرئيسي داخل عمليتنا (قياس لا استنتاج). تُشغَّل مع
     * ظهور الواجهة وتُوقف مع اختفائها، فما لا نستطيع رؤيته لا ندّعي قياسه.
     */
    private val stallDetector = MainThreadStallDetector { report ->
        EventLog.symptom(
            screen = "UiHealth",
            symptom = "main_thread_stall",
            valueMs = report.durationMs,
            worstMs = report.worstMs,
            count = report.count,
        )
    }

    override fun onStart() {
        super.onStart()
        stallDetector.start()
    }

    override fun onStop() {
        // أسوأ انسداد يُسجَّل مرّة عند الإخفاء: هذا هو الرقم الذي يُقارَن به لاحقًا لنفي
        // التحسّن أو إثباته، ولا معنى لسطر يقيس انسدادًا واحدًا بلا سياقه.
        if (stallDetector.worstStallMs > 0L) {
            EventLog.symptom(
                screen = "UiHealth",
                symptom = "main_thread_stall_session",
                valueMs = stallDetector.worstStallMs,
                count = stallDetector.stallCount,
            )
        }
        stallDetector.stop()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        RootNodeChannel.disconnect()
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(fromTileType: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // موارد من `LocalResources.current`: كل نصوص هذه الشاشة ودوالها تُقرأ داخل `launch`/`onClick`،
    // وهي سياقات لا تُبطل فيها قراءة `LocalContext.current.resources` عند تغيّر التكوين.
    val resources = LocalResources.current
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
                        Toast.makeText(context, resources.getString(R.string.toast_update_success), Toast.LENGTH_SHORT).show()
                    } else {
                        val errorLog = result.out.joinToString("\n").ifEmpty { resources.getString(R.string.status_unknown) }
                        Toast.makeText(context, resources.getString(R.string.toast_install_fail, errorLog), Toast.LENGTH_LONG).show()
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
            // `AR-04`: إصدار التطبيق يُقرأ من **مصدر واحد** (`VersionIdentity`)، لا بتكرار
            // `getPackageInfo` هنا مع تفريع حسب إصدار النظام كما كان.
            val appVC = VersionIdentity.readApp(context).versionCode
            if (appVC >= 0L && appVC < moduleVC.toLong() && apkAvailable) {
                updateDialog.showConfirm(
                    title = resources.getString(R.string.dialog_update_available_title),
                    content = resources.getString(R.string.dialog_update_available_content, appVC, moduleVC),
                    confirm = resources.getString(R.string.dialog_update_available_confirm),
                    dismiss = resources.getString(R.string.dialog_update_available_dismiss),
                )
            }
            if (rebootPending) {
                rebootDialog.showConfirm(
                    title = resources.getString(R.string.dialog_module_update_title),
                    content = resources.getString(R.string.dialog_module_update_content),
                    confirm = resources.getString(R.string.dialog_module_update_confirm),
                    dismiss = resources.getString(R.string.dialog_module_update_dismiss),
                )
            }
        }
    }
    val isFabVisible = remember { mutableStateOf(true) }
    // BottomNavBar floats *over* the NavHost (see the Box below) instead of living in a
    // Scaffold's bottomBar slot, and the NavHost is deliberately left full-bleed so page
    // content keeps scrolling underneath the pill. That pass-through is the whole point of
    // the design: a bar whose backdrop you can see moving through its blur reads as
    // floating, whereas insetting the NavHost by the bar's height (what this used to do)
    // cropped the scroll viewport short and left a dead strip of background behind the pill
    // — content visibly disappeared before it ever reached the bar.
    //
    // Since nothing reserves layout space for the bar anymore, it is published to the
    // screens instead: `LocalFloatingBottomBarHeight` carries the bar's real rendered height
    // (it is not a fixed constant — labels can wrap to two lines, e.g. longer Arabic
    // strings) so each scrollable page body can reserve exactly that much as bottom content
    // padding. Content scrolls behind the bar; the last row still stops above it.
    //
    // Seeding this at 0px left a window — most visible right after switching a primary
    // screen's own layout (e.g. Control's compact/expanded toggle) — where the bar was
    // already on screen but no space had been reserved for it yet, so the last row could
    // render behind it. Seed it with the safe MaxSpace.bottomBarReserve floor and only ever
    // grow it from the real measurement, so content always has at least that much clearance.
    val density = LocalDensity.current
    // The seed must already include the system navigation-bar inset: BottomNavBar applies
    // `.windowInsetsPadding(WindowInsets.navigationBars)` itself, so its *real* measured
    // height (captured below via onSizeChanged) always contains that inset. A seed of just
    // MaxSpace.bottomBarReserve was missing it, so on the very first frame(s) — before
    // onSizeChanged ever fires — page bodies were under-padded by the inset's worth of space
    // (~24–48dp on most devices), letting the last visible card peek out from behind the bar.
    val navInsetPx = WindowInsets.navigationBars.getBottom(density)
    var bottomBarHeightPx by remember(density, navInsetPx) {
        mutableIntStateOf(with(density) { MaxSpace.bottomBarReserve.roundToPx() } + navInsetPx)
    }
    val isPrimaryBarVisible = rootStatus && moduleInstalled && currentRoute in primaryRoutes
    // Reserved bottom space for the pages that show the bar. Zero in navigation-rail
    // layouts (the rail lives on the start edge, so the bottom stays free) and on every
    // route without the bar. The value already includes the system navigation-bar inset,
    // because the bar applies `.windowInsetsPadding(navigationBars)` itself.
    val bottomBarInset = if (!useNavigationRail && isPrimaryBarVisible) {
        with(density) { bottomBarHeightPx.toDp() }
    } else 0.dp
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
    CompositionLocalProvider(
        LocalAppHazeState provides hazeState,
        LocalFloatingBottomBarHeight provides bottomBarInset,
    ) {
        RootDialogsProvider {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The app's real background, painted behind everything. It shows through
                    // wherever a screen's own body does not cover the window (the pill's
                    // rounded corners, the gesture-bar strip) and keeps those slivers from
                    // falling through to the raw window background (solid black) — which is
                    // what used to make the pill look bolted onto a black slab.
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (hasCompletedGetStarted) {
                        MaxDestination.Now.route
                    } else MaxDestination.GetStarted.route,
                    modifier = Modifier
                        .fillMaxSize()
                        // Full-bleed to the bottom edge: content scrolls *behind* the
                        // floating bar (each page body reserves the bar's height as bottom
                        // content padding — see LocalFloatingBottomBarHeight). `start` still
                        // reserves space for the navigation rail on wide layouts, because
                        // the rail covers the content instead of the other way round.
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
                // No bottom scrim is painted here on purpose. There used to be a
                // transparent→surface gradient sitting behind the pill (to blend the
                // gesture-bar strip), but now that page content runs underneath the bar it
                // turned into a haze band that faded content out just before it reached the
                // bottom edge — the exact "the bar is not floating" impression this change
                // removes. The bar's own translucent surface is the only thing between the
                // scrolling content and the bottom edge now.
                AnimatedVisibility(
                    visible = isPrimaryBarVisible,
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
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .onSizeChanged { size ->
                                    // Only ever grow past the safe reserve (e.g. a real
                                    // two-line label) — never shrink below it, so a
                                    // transient small measurement can't under-reserve space
                                    // for the content behind it.
                                    val minPx = with(density) { MaxSpace.bottomBarReserve.roundToPx() }
                                    bottomBarHeightPx = maxOf(size.height, minPx)
                                },
                            onItemSelected = onItemSelected,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = isPrimaryBarVisible && pendingReboot && isFabVisible.value,
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
                                title = resources.getString(R.string.dialog_reboot_required_title),
                                content = resources.getString(R.string.dialog_reboot_required_content),
                                confirm = resources.getString(R.string.reboot),
                                dismiss = resources.getString(R.string.dialog_update_available_dismiss),
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
