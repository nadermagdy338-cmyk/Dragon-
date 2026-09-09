@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.ResolutionViewModel

@Composable
fun ResolutionScreen(
    navController: NavController,
    viewModel: ResolutionViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    val resetDialog = rememberConfirmDialog(
        onConfirm = viewModel::resetToNative,
        onDismiss = {}
    )
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MaxManagerSubScreenTopBar(
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.resolution_title),
                onBack = { navController.popBackStack() },
                accentIcon = Icons.Filled.AspectRatio,
                accent = cs.secondary
            )
        },
        containerColor = cs.surface
    ) { innerPadding ->
        if (!viewModel.isLoaded) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                SectionLoadingIndicator()
            }
        } else {
            ScreenAccentProvider(cs.secondary) {
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
                        val aspect = aspectRatioLabel(viewModel.activeWidthPx, viewModel.activeHeightPx)
                        val nativeW = viewModel.nativeWidthPx.coerceAtLeast(1)
                        val fraction = (viewModel.activeWidthPx.toFloat() / nativeW).coerceIn(0f, 1f)
                        RadialGaugeCard(
                            title = stringResource(R.string.resolution_gauge_title),
                            valueText = "${(fraction * 100).toInt()}",
                            unitText = "%",
                            fraction = fraction,
                            subtitle = "${viewModel.activeWidthPx}×${viewModel.activeHeightPx}",
                            isLive = true
                        )
                        Spacer(Modifier.height(10.dp))
                        StatTickRow(
                            stats = listOf(
                                stringResource(R.string.resolution_stat_aspect) to aspect,
                                stringResource(R.string.resolution_stat_dpi) to "${viewModel.activeDpi}",
                                stringResource(R.string.resolution_stat_refresh) to "${viewModel.refreshRateHz} Hz"
                            )
                        )
                    }

                    item { TweaksSectionTitle(stringResource(R.string.resolution_presets_title)) }
                    item {
                        ExpressiveList(
                            content = viewModel.presets.map { preset ->
                                {
                                    val active = preset.widthPx == viewModel.activeWidthPx &&
                                        preset.heightPx == viewModel.activeHeightPx &&
                                        preset.dpi == viewModel.activeDpi
                                    ExpressiveRadioItem(
                                        title = presetTitle(preset.id),
                                        summary = "${preset.widthPx}×${preset.heightPx} · ${preset.dpi} DPI",
                                        selected = active,
                                        onClick = { viewModel.applyPreset(preset) }
                                    )
                                }
                            }
                        )
                    }

                    item { TweaksSectionTitle(stringResource(R.string.resolution_manual_title)) }
                    item {
                        var widthSlider by remember(viewModel.activeWidthPx) { mutableStateOf(viewModel.activeWidthPx.toFloat()) }
                        var dpiSlider by remember(viewModel.activeDpi) { mutableStateOf(viewModel.activeDpi.toFloat()) }
                        val nativeW = viewModel.nativeWidthPx.toFloat().coerceAtLeast(1f)
                        val nativeH = viewModel.nativeHeightPx.toFloat().coerceAtLeast(1f)
                        val nativeD = viewModel.nativeDpi.toFloat().coerceAtLeast(1f)
                        val minW = (nativeW * 0.4f).roundToIntSafe()
                        val minD = (nativeD * 0.4f).roundToIntSafe()
                        fun heightFor(w: Float) = (w / nativeW * nativeH)

                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.Lock,
                                        title = stringResource(R.string.resolution_lock_aspect),
                                        summary = stringResource(R.string.resolution_lock_aspect_desc),
                                        checked = viewModel.lockAspectRatio,
                                        onCheckedChange = { viewModel.lockAspectRatio = it }
                                    )
                                },
                                {
                                    ExpressiveSliderItem(
                                        icon = Icons.Outlined.SwapHoriz,
                                        title = stringResource(R.string.resolution_width),
                                        badgeText = "${widthSlider.toInt()} px",
                                        sliderPosition = widthSlider,
                                        valueRange = minW.toFloat()..nativeW,
                                        steps = 0,
                                        onValueChange = { widthSlider = it },
                                        onValueChangeFinished = {
                                            viewModel.applyResolution(
                                                widthSlider.toInt(),
                                                heightFor(widthSlider).toInt(),
                                                dpiSlider.toInt()
                                            )
                                        }
                                    )
                                },
                                {
                                    ExpressiveSliderItem(
                                        icon = Icons.Outlined.Height,
                                        title = stringResource(R.string.resolution_height),
                                        badgeText = "${heightFor(widthSlider).toInt()} px",
                                        sliderPosition = widthSlider,
                                        valueRange = minW.toFloat()..nativeW,
                                        steps = 0,
                                        enabled = false,
                                        onValueChange = {},
                                        onValueChangeFinished = {}
                                    )
                                },
                                {
                                    ExpressiveSliderItem(
                                        icon = Icons.Outlined.Grain,
                                        title = stringResource(R.string.resolution_dpi),
                                        badgeText = "${dpiSlider.toInt()} DPI",
                                        sliderPosition = dpiSlider,
                                        valueRange = minD.toFloat()..nativeD,
                                        steps = 0,
                                        onValueChange = { dpiSlider = it },
                                        onValueChangeFinished = {
                                            viewModel.applyResolution(
                                                widthSlider.toInt(),
                                                heightFor(widthSlider).toInt(),
                                                dpiSlider.toInt()
                                            )
                                        }
                                    )
                                }
                            )
                        )
                    }

                    item {
                        ExpressiveList(
                            content = listOf {
                                ExpressiveListItem(
                                    leadingContent = {
                                        LeadingIcon(
                                            icon = Icons.Filled.RestartAlt,
                                            containerColor = cs.errorContainer,
                                            contentColor = cs.onErrorContainer
                                        )
                                    },
                                    headlineContent = { Text(stringResource(R.string.resolution_reset_native), color = cs.error) },
                                    supportingContent = {
                                        Text(
                                            stringResource(
                                                R.string.resolution_reset_native_desc,
                                                viewModel.nativeWidthPx,
                                                viewModel.nativeHeightPx,
                                                viewModel.nativeDpi
                                            )
                                        )
                                    },
                                    onClick = {
                                        resetDialog.showConfirm(
                                            title = context.getString(R.string.resolution_reset_title),
                                            content = context.getString(R.string.resolution_reset_body),
                                            confirm = context.getString(R.string.yes),
                                            dismiss = context.getString(R.string.no)
                                        )
                                    }
                                )
                            }
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.resolution_safety_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

    ConfirmDialogHost(handle = resetDialog)
}

private fun Float.roundToIntSafe(): Int = if (isFinite()) toInt() else 0

private fun aspectRatioLabel(w: Int, h: Int): String {
    if (w <= 0 || h <= 0) return "-"
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(w, h).coerceAtLeast(1)
    return "${w / g}:${h / g}"
}

@Composable
private fun presetTitle(id: String): String = when (id) {
    "native" -> stringResource(R.string.resolution_preset_native)
    "balanced" -> stringResource(R.string.resolution_preset_balanced)
    "performance" -> stringResource(R.string.resolution_preset_performance)
    "battery_saver" -> stringResource(R.string.resolution_preset_battery)
    else -> id
}
