@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.DisplayStudioViewModel
import nd.max.ui.viewmodel.ResolutionViewModel
import nd.max.ui.viewmodel.TweakViewModel

private fun formatAnimationScale(scale: Float): String =
    if (scale == 0f) "Off" else "${if (scale == scale.toInt().toFloat()) scale.toInt() else scale}x"

private fun formatFontScale(scale: Float): String = "${(scale * 100).toInt()}%"

private fun formatTimeout(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    else -> "${seconds / 3600}h"
}

@Composable
fun DisplayStudioScreen(
    navController: NavController,
    viewModel: DisplayStudioViewModel = viewModel(),
    resolutionViewModel: ResolutionViewModel = viewModel(),
    tweakViewModel: TweakViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    var showRefreshDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadState()
        resolutionViewModel.loadState()
        tweakViewModel.loadAllConfiguration(context)
    }

    val activeW = resolutionViewModel.activeWidthPx
    val activeH = resolutionViewModel.activeHeightPx
    val nativeW = resolutionViewModel.nativeWidthPx.coerceAtLeast(1)
    val scale = (activeW.toFloat() / nativeW).coerceIn(0f, 1f)
    val currentHz = tweakViewModel.currentRefreshRate ?: resolutionViewModel.refreshRateHz
    val currentRateReason = tweakViewModel.currentRefreshRateReason

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MaxManagerSubScreenTopBar(
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.display_studio_title),
                onBack = { navController.popBackStack() },
                accentIcon = Icons.Filled.DisplaySettings,
                accent = colorScheme.secondary
            )
        },
        containerColor = colorScheme.surface
    ) { innerPadding ->
        if (!viewModel.isLoaded || !resolutionViewModel.isLoaded) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                SectionLoadingIndicator()
            }
        } else {
            ScreenAccentProvider(colorScheme.secondary) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ControlScreenIntro(
                            icon = Icons.Filled.DisplaySettings,
                            title = "Display Studio",
                            description = "إدارة الدقة ومعدل التحديث وإعدادات العرض من نقطة تحكم واحدة.",
                            accent = colorScheme.secondary,
                            status = "ACTIVE"
                        )
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                    item {
                        DisplayHeroCard(
                            width = activeW,
                            height = activeH,
                            nativeWidth = resolutionViewModel.nativeWidthPx,
                            nativeHeight = resolutionViewModel.nativeHeightPx,
                            dpi = resolutionViewModel.activeDpi,
                            refresh = currentHz,
                            scale = scale,
                            onResolution = { MaxNavActions(navController).navigateTo(MaxDestination.Resolution) },
                            onRefresh = { showRefreshDialog = true }
                        )
                    }

                    item { TweaksSectionTitle(stringResource(R.string.display_features_title)) }
                    item {
                        val featureItems = buildList<@Composable () -> Unit> {
                            if (viewModel.sunlightNode != null) add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Outlined.WbSunny,
                                    title = stringResource(R.string.sunlight_mode),
                                    summary = stringResource(R.string.sunlight_mode_desc),
                                    checked = viewModel.sunlightEnabled,
                                    onCheckedChange = viewModel::setSunlight
                                )
                            }
                            if (viewModel.silkyNode != null) add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Outlined.Brightness6,
                                    title = stringResource(R.string.silky_brightness),
                                    summary = stringResource(R.string.silky_brightness_desc),
                                    checked = viewModel.silkyEnabled,
                                    onCheckedChange = viewModel::setSilky
                                )
                            }
                            if (viewModel.videoEnhanceNode != null) add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Outlined.Movie,
                                    title = stringResource(R.string.video_enhancement),
                                    summary = stringResource(R.string.video_enhancement_desc),
                                    checked = viewModel.videoEnhanceEnabled,
                                    onCheckedChange = viewModel::setVideoEnhance
                                )
                            }
                            if (viewModel.hdrNode != null) add {
                                ExpressiveSwitchItem(
                                    icon = Icons.Outlined.HdrOn,
                                    title = stringResource(R.string.dolby_vision_hdr),
                                    summary = stringResource(R.string.dolby_vision_hdr_desc),
                                    checked = viewModel.hdrEnabled,
                                    onCheckedChange = viewModel::setHdr
                                )
                            }
                        }
                        if (featureItems.isNotEmpty()) ExpressiveList(content = featureItems)
                        else {
                            DisplayUnavailableCard(
                                vendorHal = viewModel.vendorHalDetected
                            )
                        }
                    }

                    item { TweaksSectionTitle(stringResource(R.string.display_system_settings_title)) }
                    item {
                        val animIndex = DisplayStudioViewModel.ANIMATION_SCALE_PRESETS.indexOfFirst { it == viewModel.animationScale }.coerceAtLeast(0)
                        val fontIndex = DisplayStudioViewModel.FONT_SCALE_PRESETS.indexOfFirst { it == viewModel.fontScale }.coerceAtLeast(0)
                        val timeoutIndex = DisplayStudioViewModel.SCREEN_TIMEOUT_PRESETS.indexOfFirst { it == viewModel.screenTimeoutSeconds }.coerceAtLeast(0)
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.Animation,
                                        title = stringResource(R.string.display_animation_scale_title),
                                        summary = formatAnimationScale(viewModel.animationScale),
                                        items = DisplayStudioViewModel.ANIMATION_SCALE_PRESETS.map(::formatAnimationScale),
                                        selectedIndex = animIndex,
                                        onItemSelected = { viewModel.updateAnimationScale(DisplayStudioViewModel.ANIMATION_SCALE_PRESETS[it]) }
                                    )
                                },
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.FormatSize,
                                        title = stringResource(R.string.display_font_scale_title),
                                        summary = formatFontScale(viewModel.fontScale),
                                        items = DisplayStudioViewModel.FONT_SCALE_PRESETS.map(::formatFontScale),
                                        selectedIndex = fontIndex,
                                        onItemSelected = { viewModel.updateFontScale(DisplayStudioViewModel.FONT_SCALE_PRESETS[it]) }
                                    )
                                },
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Outlined.Timer,
                                        title = stringResource(R.string.display_screen_timeout_title),
                                        summary = formatTimeout(viewModel.screenTimeoutSeconds),
                                        items = DisplayStudioViewModel.SCREEN_TIMEOUT_PRESETS.map(::formatTimeout),
                                        selectedIndex = timeoutIndex,
                                        onItemSelected = { viewModel.setScreenTimeout(DisplayStudioViewModel.SCREEN_TIMEOUT_PRESETS[it]) }
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Outlined.NightlightRound,
                                        title = stringResource(R.string.display_night_light_title),
                                        summary = stringResource(R.string.display_night_light_desc),
                                        checked = viewModel.nightLightEnabled,
                                        onCheckedChange = viewModel::setNightLight
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Outlined.InvertColors,
                                        title = stringResource(R.string.display_color_inversion_title),
                                        summary = stringResource(R.string.display_color_inversion_desc),
                                        checked = viewModel.colorInversionEnabled,
                                        onCheckedChange = viewModel::setColorInversion
                                    )
                                }
                            )
                        )
                    }

                    item { TweaksSectionTitle(stringResource(R.string.display_controls_title)) }
                    item {
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.AspectRatio) },
                                        headlineContent = { Text(stringResource(R.string.display_resolution_control_title)) },
                                        supportingContent = { Text("${activeW}×${activeH} · ${resolutionViewModel.activeDpi} DPI") },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                                        onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Resolution) }
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Refresh) },
                                        headlineContent = { Text(stringResource(R.string.display_refresh_control_title)) },
                                        supportingContent = { Text(if (currentRateReason.equals("default", true)) "${currentHz} Hz · Auto" else "${currentHz} Hz · Fixed") },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                                        onClick = { showRefreshDialog = true }
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    RefreshRatePickerDialog(
        show = showRefreshDialog,
        currentReason = currentRateReason,
        onDismiss = { showRefreshDialog = false },
        onRefreshRatePicker = { reason -> tweakViewModel.executeSetRefreshRates(reason, context) }
    )
}

@Composable
private fun DisplayHeroCard(
    width: Int,
    height: Int,
    nativeWidth: Int,
    nativeHeight: Int,
    dpi: Int,
    refresh: Int?,
    scale: Float,
    onResolution: () -> Unit,
    onRefresh: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerLow, MaterialTheme.shapes.extraLarge)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenAccentGlyph(Icons.Filled.DisplaySettings, cs.secondary, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("DISPLAY", style = MaterialTheme.typography.labelLarge, color = cs.secondary, fontWeight = FontWeight.Bold)
                Text("Live panel configuration", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.large, color = cs.secondaryContainer) {
                Text("LIVE", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("${width} × ${height}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Current canvas", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DisplayMetric("Refresh", "${refresh ?: 0} Hz", Modifier.weight(1f), onRefresh)
            DisplayMetric("Density", "$dpi DPI", Modifier.weight(1f), null)
            DisplayMetric("Scale", "${(scale * 100).toInt()}%", Modifier.weight(1f), onResolution)
        }
        Spacer(Modifier.height(12.dp))
        if (nativeWidth > 0 && nativeHeight > 0) {
            Text("Native ${nativeWidth} × ${nativeHeight}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun DisplayMetric(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.padding(12.dp) else Modifier.padding(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DisplayUnavailableCard(vendorHal: Boolean) {
    val cs = MaterialTheme.colorScheme
    ExpressiveInfoCard(
        leadingContent = { LeadingIcon(Icons.Outlined.Info) },
        supportingContent = {
            Text(
                if (vendorHal) stringResource(R.string.display_features_unavailable_vendor_hal)
                else stringResource(R.string.display_features_unavailable)
            )
        },
        containerColor = cs.surfaceContainerLow,
        onClick = {}
    )
}
