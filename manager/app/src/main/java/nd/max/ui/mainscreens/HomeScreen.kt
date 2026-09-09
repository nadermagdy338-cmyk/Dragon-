@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.MaxSnackbarHost
import nd.max.ui.component.ProfileDialog
import nd.max.ui.component.RebootBottomSheet
import nd.max.ui.component.RootAppDialog
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.ui.util.getRealDeviceName
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeDashboardViewModel
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    isVisible: Boolean = true,
    homeViewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    dashboardViewModel: HomeDashboardViewModel = viewModel(),
    maxAiViewModel: nd.max.ui.viewmodel.MaxAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val ui by homeViewModel.uiState.collectAsStateWithLifecycle()
    val dashboard by dashboardViewModel.dashboardState.collectAsStateWithLifecycle()
    val maxAi by maxAiViewModel.state.collectAsStateWithLifecycle()
    val profileRequest by maxAiViewModel.profileRequest.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showReboot by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val deviceName = remember(context) { getRealDeviceName(context) }

    LifecycleStartEffect(dashboardViewModel, isVisible) {
        if (isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            dashboardViewModel.setPollingActive(true)
        }
        onStopOrDispose { dashboardViewModel.setPollingActive(false) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { MaxSnackbarHost(snackbar) }
    ) { padding ->
        HomeDashboardContent(
            ui = ui,
            dashboard = dashboard,
            maxAi = maxAi,
            profileRequest = profileRequest,
            deviceName = deviceName,
            topPadding = padding.calculateTopPadding(),
            onNavigate = navController::navigate,
            onProfile = { if (ui.autoMode == "0") showProfile = true },
            onReboot = { showReboot = true },
            onAiRetry = maxAiViewModel::refresh
        )
    }

    RootAppDialog {
        RebootBottomSheet(
            show = showReboot,
            onDismiss = { showReboot = false },
            onReboot = homeViewModel::rebootDevice
        )
    }
    RootAppDialog {
        ProfileDialog(
            show = showProfile,
            onDismiss = { showProfile = false },
            onProfile = { reason ->
                homeViewModel.applyProfile(reason) { appliedNow ->
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(
                                if (appliedNow) R.string.toast_applying_profile
                                else R.string.toast_profile_pending
                            )
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun HomeDashboardContent(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReboot: () -> Unit,
    onAiRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val online = ui.rootStatus && ui.moduleInstalled
    val gpuRoute = gpuRouteForChipset(dashboard.chipsetName)
    val gpuName = gpuFamilyForChipset(dashboard.chipsetName)
    val profile = stringResource(ui.currentProfileRes)
    val aiFailed = profileRequest.result == DecisionResult.FAILED ||
        maxAi.lastDecision?.result == DecisionResult.FAILED
    val backdrop = remember(colors) {
        Brush.radialGradient(
            listOf(colors.primary.copy(alpha = .15f), colors.background.copy(alpha = 0f)),
            center = Offset(180f, 120f), radius = 820f
        )
    }

    Box(Modifier.fillMaxSize().background(colors.background).background(backdrop)) {
        TechnicalBackdrop()
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.maxAdaptiveContentWidth(),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp,
                top = topPadding + 8.dp,
                bottom = 124.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CommandHero(
                    deviceName = deviceName,
                    chipset = dashboard.chipsetName.takeUnless { it.isBlank() || it == "..." },
                    online = online,
                    profile = profile,
                    temperatureC = nd.max.ui.viewmodel.primaryBatteryTemperatureC(dashboard),
                    onProfile = onProfile,
                    onThermal = { onNavigate("thermal_detail") },
                    onSettings = { onNavigate("settings") },
                    onReboot = onReboot
                )
            }
            item {
                LivePerformance(
                    dashboard = dashboard,
                    gpuName = gpuName,
                    onGpu = gpuRoute?.let { route -> { onNavigate(route) } }
                )
            }
            item {
                ControlDeck(
                    dashboard = dashboard,
                    profile = profile,
                    profileEnabled = ui.autoMode == "0",
                    profilePending = ui.autoMode != "0",
                    gpuName = gpuName,
                    onRoute = onNavigate,
                    onGpu = { gpuRoute?.let(onNavigate) },
                    onProfile = onProfile,
                    onReboot = onReboot
                )
            }
            item {
                MaxAiConsole(
                    state = maxAi,
                    request = profileRequest,
                    failed = aiFailed,
                    onOpen = { onNavigate("maxai") },
                    onRetry = onAiRetry
                )
            }
        }
    }
}

@Composable
private fun TechnicalBackdrop() {
    val line = MaterialTheme.colorScheme.primary.copy(alpha = .035f)
    Canvas(Modifier.fillMaxSize()) {
        val step = 44.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        drawCircle(line.copy(alpha = .08f), radius = size.minDimension * .38f, center = Offset(size.width * .84f, size.height * .1f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

internal fun gpuRouteForChipset(chipset: String): String? {
    val value = chipset.lowercase()
    return when {
        listOf("adreno", "qualcomm", "snapdragon").any(value::contains) -> "adrenogpufreq"
        listOf("mali", "mediatek", "dimensity").any(value::contains) -> "maligpufreq"
        else -> null
    }
}

internal fun gpuFamilyForChipset(chipset: String): String? = when (gpuRouteForChipset(chipset)) {
    "adrenogpufreq" -> "Adreno"
    "maligpufreq" -> "Mali"
    else -> null
}
