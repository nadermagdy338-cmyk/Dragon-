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

package nd.max.ui.subscreens


import android.app.Activity
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.theme.ColorMode
import nd.max.ui.theme.ThemeController
import nd.max.ui.theme.animateColorSchemeAsState
import nd.max.ui.util.clearHeaderImage
import nd.max.ui.util.EventLog
import nd.max.ui.util.getBannerGradientAlpha
import nd.max.ui.util.getHeaderImage
import nd.max.ui.util.isBannerImageEnabled
import nd.max.ui.util.saveHeaderImage
import nd.max.ui.util.saveMediaDirectly
import nd.max.ui.util.setBannerGradientAlpha
import nd.max.ui.util.setBannerImageEnabled


// ─── Palette of selectable key colors ─────────────────────────────────────────
private val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(),
    Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(),
    Color(0xFF673AB7).toArgb(),
    Color(0xFF3F51B5).toArgb(),
    Color(0xFF2196F3).toArgb(),
    Color(0xFF00BCD4).toArgb(),
    Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(),
    Color(0xFFFFEB3B).toArgb(),
    Color(0xFFFFC107).toArgb(),
    Color(0xFFFF9800).toArgb(),
    Color(0xFF795548).toArgb(),
    Color(0xFF607D8F).toArgb(),
    Color(0xFFFF9CA8).toArgb(),
)

// ─── Root screen ──────────────────────────────────────────────────────────────
@Composable
fun ColorPaletteScreen(navController: NavController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isBannerEnabled by rememberSaveable { mutableStateOf(context.isBannerImageEnabled()) }
    var bannerGradientAlpha by rememberSaveable { mutableFloatStateOf(context.getBannerGradientAlpha()) }
    var customBannerUri by remember { mutableStateOf(context.getHeaderImage()) }
    var pendingCropUriPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isBlurEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("expressive_blur_ui", false)) }
    var useScrollAnimation by rememberSaveable { mutableStateOf(prefs.getBoolean("use_scroll_animation", false)) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val colorScheme = MaterialTheme.colorScheme

    // ── Crop launcher ─────────────────────────────────────────────────────────
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            UCrop.getOutput(result.data!!)?.let {
                context.saveHeaderImage(it.toString())
                customBannerUri = it.toString()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.str_banner_updated))
                }
            }
        } else {
            pendingCropUriPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
        pendingCropUriPath = null
    }

    // ── Image / video picker ──────────────────────────────────────────────────
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { sourceUri ->
            val mimeType = context.contentResolver.getType(sourceUri) ?: ""
            val isVideo = mimeType.startsWith("video/")
            val isGif = mimeType == "image/gif"
            val isVideoOrGif = isVideo || isGif

            var sizeBytes = 0L
            context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
            if (sizeBytes > 50 * 1024 * 1024L) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.str_max_video_size))
                }
                return@let
            }

            if (isVideo) {
                var durationMs = 0L
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, sourceUri)
                    durationMs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    retriever.release()
                }
                if (durationMs > 30_000L) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.str_video_too_long))
                    }
                    return@let
                }
            }

            if (isVideoOrGif) {
                coroutineScope.launch {
                    val ext = if (isVideo) "mp4" else "gif"
                    val saved = context.saveMediaDirectly(sourceUri, ext)
                    if (saved != null) {
                        context.saveHeaderImage(saved)
                        customBannerUri = saved
                        snackbarHostState.showSnackbar(context.getString(R.string.str_pick_media_success))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.str_pick_media_fail))
                    }
                }
            } else {
                val bannerDir = File(context.filesDir, "banners").also { if (!it.exists()) it.mkdirs() }
                val destFile = File(bannerDir, "banner_${System.currentTimeMillis()}.jpg")
                pendingCropUriPath = destFile.absolutePath
                val destUri = Uri.fromFile(destFile)

                val options = UCrop.Options().apply {
                    setHideBottomControls(false)
                    setFreeStyleCropEnabled(false)
                    setToolbarColor(colorScheme.surface.toArgb())
                    setToolbarWidgetColor(colorScheme.onSurface.toArgb())
                    setRootViewBackgroundColor(colorScheme.surfaceContainerLowest.toArgb())
                    setActiveControlsWidgetColor(colorScheme.primary.toArgb())
                    setCropFrameColor(colorScheme.primary.toArgb())
                    setCropGridColor(colorScheme.primary.copy(alpha = 0.5f).toArgb())
                    setDimmedLayerColor(colorScheme.scrim.copy(alpha = 0.6f).toArgb())
                }
                cropLauncher.launch(
                    UCrop.of(sourceUri, destUri).withAspectRatio(20f, 9f).withOptions(options)
                        .getIntent(context)
                )
            }
        }
    }

    // ── Theme state ───────────────────────────────────────────────────────────
    var currentColorMode by remember { mutableStateOf(ThemeController.getAppSettings(context).colorMode) }
    var currentKeyColor by remember { mutableIntStateOf(ThemeController.getAppSettings(context).keyColor) }
    var currentColorSpec by remember { mutableStateOf(ThemeController.getAppSettings(context).colorSpec) }

    val isDark = currentColorMode.getDarkThemeValue(isSystemInDarkTheme())
    val amoledMode = currentColorMode == ColorMode.DARKAMOLED

    ScreenAccentProvider(MaterialTheme.colorScheme.primary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                PaletteTopAppBar(scrollBehavior, onBack = { navController.popBackStack() })
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(top = innerPadding.calculateTopPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        ThemePreviewCard(
                            keyColor = currentKeyColor,
                            colorSpec = currentColorSpec,
                            isDark = isDark,
                            isAmoled = amoledMode,
                            isLandscape = true
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            StudioSectionHeader(
                                title = stringResource(R.string.theme),
                                subtitle = stringResource(R.string.theme_subtitle),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        themeSettingsItems(
                            currentColorMode = currentColorMode,
                            currentKeyColor = currentKeyColor,
                            currentColorSpec = currentColorSpec,
                            isDark = isDark,
                            isBannerEnabled = isBannerEnabled,
                            bannerGradientAlpha = bannerGradientAlpha,
                            customBannerUri = customBannerUri,
                            isBlurEnabled = isBlurEnabled,
                            useScrollAnimation = useScrollAnimation,
                            prefs = prefs,
                            onColorModeChange = { currentColorMode = it },
                            onKeyColorChange = { currentKeyColor = it },
                            onColorSpecChange = { currentColorSpec = it },
                            onBannerEnabledChange = {
                                isBannerEnabled = it; context.setBannerImageEnabled(it)
                            },
                            onBannerGradientAlphaChange = {
                                bannerGradientAlpha = it; context.setBannerGradientAlpha(it)
                            },
                            onBannerUpdated = { customBannerUri = it },
                            onBlurEnabledChange = {
                                EventLog.userAction(screen = "CustomTheme", field = "expressive_blur_ui", old = isBlurEnabled.toString(), new = it.toString())
                                isBlurEnabled = it; prefs.edit { putBoolean("expressive_blur_ui", it) }
                            },
                            onUseScrollAnimationChange = {
                                EventLog.userAction(screen = "CustomTheme", field = "use_scroll_animation", old = useScrollAnimation.toString(), new = it.toString())
                                useScrollAnimation = it; prefs.edit {
                                    putBoolean("use_scroll_animation", it)
                                }
                            },
                            imagePicker = imagePicker,
                            context = context,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        ThemePreviewCard(
                            keyColor = currentKeyColor,
                            colorSpec = currentColorSpec,
                            isDark = isDark,
                            isAmoled = amoledMode,
                            isLandscape = false
                        )
                    }

                    themeSettingsItems(
                        currentColorMode = currentColorMode,
                        currentKeyColor = currentKeyColor,
                        currentColorSpec = currentColorSpec,
                        isDark = isDark,
                        isBannerEnabled = isBannerEnabled,
                        bannerGradientAlpha = bannerGradientAlpha,
                        customBannerUri = customBannerUri,
                        isBlurEnabled = isBlurEnabled,
                        useScrollAnimation = useScrollAnimation,
                        prefs = prefs,
                        onColorModeChange = { currentColorMode = it },
                        onKeyColorChange = { currentKeyColor = it },
                        onColorSpecChange = { currentColorSpec = it },
                        onBannerEnabledChange = {
                            isBannerEnabled = it; context.setBannerImageEnabled(it)
                        },
                        onBannerGradientAlphaChange = {
                            bannerGradientAlpha = it; context.setBannerGradientAlpha(it)
                        },
                        onBannerUpdated = { customBannerUri = it },
                        onBlurEnabledChange = {
                            EventLog.userAction(screen = "CustomTheme", field = "expressive_blur_ui", old = isBlurEnabled.toString(), new = it.toString())
                            isBlurEnabled = it; prefs.edit { putBoolean("expressive_blur_ui", it) }
                        },
                        onUseScrollAnimationChange = {
                            EventLog.userAction(screen = "CustomTheme", field = "use_scroll_animation", old = useScrollAnimation.toString(), new = it.toString())
                            useScrollAnimation = it; prefs.edit {
                                putBoolean("use_scroll_animation", it)
                            }
                        },
                        imagePicker = imagePicker,
                        context = context,
                        snackbarHostState = snackbarHostState,
                        coroutineScope = coroutineScope
                    )
                }
            }
        }
    }
}

// ─── Lazy list items ──────────────────────────────────────────────────────────
private fun androidx.compose.foundation.lazy.LazyListScope.themeSettingsItems(
    currentColorMode: ColorMode,
    currentKeyColor: Int,
    currentColorSpec: ColorSpec.SpecVersion,
    isDark: Boolean,
    isBannerEnabled: Boolean,
    bannerGradientAlpha: Float,
    customBannerUri: String?,
    isBlurEnabled: Boolean,
    useScrollAnimation: Boolean,
    prefs: android.content.SharedPreferences,
    onColorModeChange: (ColorMode) -> Unit,
    onKeyColorChange: (Int) -> Unit,
    onColorSpecChange: (ColorSpec.SpecVersion) -> Unit,
    onBannerEnabledChange: (Boolean) -> Unit,
    onBannerGradientAlphaChange: (Float) -> Unit,
    onBannerUpdated: (String?) -> Unit,
    onBlurEnabledChange: (Boolean) -> Unit,
    onUseScrollAnimationChange: (Boolean) -> Unit,
    imagePicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    context: Context,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {

    // ── Section: Accent Color ──────────────────────────────────────────────
    item {
        ThemeSectionHeader(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.accent_color),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 10.dp)
        )
    }

    item {
        // Horizontally scrollable color swatches with edge fade
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.06f to Color.Black,
                            0.94f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            // "Dynamic / Wallpaper" swatch
            item {
                ColorSwatch(
                    color = Color.Unspecified,
                    isSelected = currentKeyColor == 0,
                    isDark = isDark,
                    colorSpec = currentColorSpec,
                    label = "Auto",
                    onClick = {
                        onKeyColorChange(0)
                        EventLog.userAction(screen = "CustomTheme", field = "key_color", old = currentKeyColor.toString(), new = "0")
                        prefs.edit { putInt("key_color", 0) }
                    }
                )
            }
            items(keyColorOptions) { colorInt ->
                ColorSwatch(
                    color = Color(colorInt),
                    isSelected = currentKeyColor == colorInt,
                    isDark = isDark,
                    colorSpec = currentColorSpec,
                    onClick = {
                        onKeyColorChange(colorInt)
                        EventLog.userAction(screen = "CustomTheme", field = "key_color", old = currentKeyColor.toString(), new = colorInt.toString())
                        prefs.edit { putInt("key_color", colorInt) }
                    }
                )
            }
        }
    }

    // ── Section: Appearance (dark / light) ────────────────────────────────
    item { Spacer(Modifier.height(24.dp)) }

    item {
        ThemeSectionHeader(
            icon = Icons.Outlined.DarkMode,
            title = stringResource(R.string.appearance),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 10.dp)
        )
    }

    item {
        val options = listOf(
            ColorMode.SYSTEM to (Icons.Filled.Brightness4 to "System"),
            ColorMode.LIGHT to (Icons.Filled.Brightness7 to "Light"),
            ColorMode.DARK to (Icons.Filled.Brightness3 to "Dark"),
            ColorMode.DARKAMOLED to (Icons.Filled.Brightness1 to "AMOLED"),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            options.forEachIndexed { index, (mode, iconAndLabel) ->
                val (icon, label) = iconAndLabel
                SegmentedButton(
                    selected = currentColorMode == mode,
                    onClick = {
                        onColorModeChange(mode)
                        EventLog.userAction(screen = "CustomTheme", field = "color_mode", old = currentColorMode.value.toString(), new = mode.value.toString())
                        prefs.edit { putInt("color_mode", mode.value) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    icon = { Icon(imageVector = icon, contentDescription = label) },
                    label = {}
                )
            }
        }
    }

    // ── Section: Color Specification ──────────────────────────────────────
    item { Spacer(Modifier.height(24.dp)) }

    item {
        ThemeSectionHeader(
            icon = Icons.Outlined.Colorize,
            title = stringResource(R.string.str_color_specification),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 10.dp)
        )
    }

    item {
        val specOptions = ColorSpec.SpecVersion.entries
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            specOptions.forEachIndexed { index, spec ->
                SegmentedButton(
                    selected = currentColorSpec == spec,
                    onClick = {
                        onColorSpecChange(spec)
                        EventLog.userAction(screen = "CustomTheme", field = "color_spec", old = currentColorSpec.name, new = spec.name)
                        prefs.edit { putString("color_spec", spec.name) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = specOptions.size),
                    colors = SegmentedButtonDefaults.colors(
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    label = {
                        Text(
                            spec.name.replace("_", " "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }

    // ── Section: Banner ───────────────────────────────────────────────────
    item { Spacer(Modifier.height(24.dp)) }

    item {
        ThemeSectionHeader(
            icon = Icons.Outlined.Image,
            title = stringResource(R.string.banner),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 10.dp)
        )
    }

    item {
        ExpressiveColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            content = buildList {
                add {
                    Column {
                        ExpressiveSwitchItem(
                            icon = Icons.Outlined.Image,
                            title = stringResource(R.string.str_enable_banner),
                            checked = isBannerEnabled,
                            onCheckedChange = onBannerEnabledChange
                        )

                        AnimatedVisibility(
                            visible = isBannerEnabled,
                            enter = expandVertically(tween(380)) + fadeIn(tween(380)),
                            exit = shrinkVertically(tween(380)) + fadeOut(tween(380))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    nd.max.ui.component.StudioOutlinedButton(
                                        onClick = {
                                            imagePicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(
                                            topStart = 50.dp, bottomStart = 50.dp
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            Icons.Filled.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.str_pick_media),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    nd.max.ui.component.StudioOutlinedButton(
                                        onClick = {
                                            context.clearHeaderImage()
                                            onBannerUpdated(null)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.str_default_banner_toast)
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(
                                            topEnd = 50.dp, bottomEnd = 50.dp
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            Icons.Filled.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.default_label),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // Gradient control card — shown only when the banner is on
    item {
        AnimatedVisibility(
            visible = isBannerEnabled,
            enter = expandVertically(tween(400)) + fadeIn(tween(400)),
            exit = shrinkVertically(tween(400)) + fadeOut(tween(400))
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                ExpressiveColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    content = buildList {
                        add {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Banner preview
                                BannerGradientPreview(
                                    gradientAlpha = bannerGradientAlpha,
                                    customBannerUri = customBannerUri
                                )

                                // Gradient opacity row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LeadingIcon(icon = Icons.Outlined.Gradient)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.str_adjust_gradient),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.str_gradient_opacity),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.str_bannergradientalpha_100_toint,
                                                    (bannerGradientAlpha * 100).toInt()
                                                ),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = { onBannerGradientAlphaChange(0.5f) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Restore,
                                                    contentDescription = stringResource(R.string.reset),
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    MaxSlider(
                                        value = bannerGradientAlpha,
                                        onValueChange = { v ->
                                            onBannerGradientAlphaChange(
                                                if (v in 0.47f..0.53f) 0.5f else v
                                            )
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // ── Section: Interface ────────────────────────────────────────────────
    item { Spacer(Modifier.height(24.dp)) }

    item {
        ThemeSectionHeader(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.str_interface),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 10.dp)
        )
    }

    item {
        ExpressiveColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            content = buildList {
                add {
                    ExpressiveSwitchItem(
                        icon = Icons.Filled.BlurOn,
                        title = stringResource(R.string.str_expressive_blur),
                        summary = stringResource(R.string.str_expressive_blur_summary),
                        checked = isBlurEnabled,
                        onCheckedChange = onBlurEnabledChange
                    )
                }
                add {
                    ExpressiveSwitchItem(
                        icon = Icons.Filled.SwipeRight,
                        title = stringResource(R.string.str_use_scroll_animation),
                        summary = stringResource(R.string.str_use_scroll_animation_summary),
                        checked = useScrollAnimation,
                        onCheckedChange = onUseScrollAnimationChange
                    )
                }
            }
        )
    }

    item { Spacer(Modifier.height(16.dp)) }
}

// ─── Top App Bar ──────────────────────────────────────────────────────────────
@Composable
fun PaletteTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.theme),
        onBack = onBack,
        accentIcon = Icons.Filled.Colorize,
        accent = MaterialTheme.colorScheme.primary
    )
}

// ─── Section header helper ────────────────────────────────────────────────────
@Composable
private fun ThemeSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Banner gradient preview ──────────────────────────────────────────────────
@Composable
private fun BannerGradientPreview(gradientAlpha: Float, customBannerUri: String?) {
    val colorScheme = MaterialTheme.colorScheme

    // Slow diagonal shine that keeps the preview "alive"
    val shineTransition = rememberInfiniteTransition(label = "banner_shine")
    val shineProgress by shineTransition.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "banner_shine_progress"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(20 / 9f)
            .clip(RoundedCornerShape(16.dp)),
        color = colorScheme.surfaceContainerHighest
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = customBannerUri,
                animationSpec = tween(500),
                label = "banner_crossfade"
            ) { uri ->
                MediaBannerRenderer(uriString = uri, modifier = Modifier.fillMaxSize())
            }
            // Bottom fade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                colorScheme.surfaceContainerLow.copy(alpha = gradientAlpha)
                            )
                        )
                    )
            )
            // Diagonal shine
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            start = Offset(shineProgress * 600f - 200f, 0f),
                            end = Offset(shineProgress * 600f + 200f, 400f)
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp),
                color = colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = stringResource(R.string.str_preview),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ─── Theme preview phone card ─────────────────────────────────────────────────
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    colorSpec: ColorSpec.SpecVersion,
    isDark: Boolean,
    isAmoled: Boolean,
    isLandscape: Boolean
) {
    val context = LocalContext.current

    val targetColorScheme = if (keyColor == 0) {
        val base = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else lightColorScheme()
        }
        rememberDynamicColorScheme(
            seedColor = base.primary,
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = colorSpec,
            primary = base.primary,
            secondary = base.secondary,
            tertiary = base.tertiary,
            neutral = base.surface,
            neutralVariant = base.surfaceVariant,
            error = base.error
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(keyColor),
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = colorSpec
        )
    }

    val cs = animateColorSchemeAsState(targetColorScheme)

    // Pulsing glow halo behind the phone
    val glowAnim = rememberInfiniteTransition(label = "phone_glow")
    val glowAlpha by glowAnim.animateFloat(
        initialValue = 0.12f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phone_glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft colored halo
        Box(
            modifier = Modifier
                .size(
                    width = if (isLandscape) 130.dp else 120.dp,
                    height = if (isLandscape) 270.dp else 250.dp
                )
                .clip(RoundedCornerShape(32.dp))
                .background(cs.primary.copy(alpha = glowAlpha * 0.5f))
        )

        // Phone surface
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.80f else 0.52f)
                .aspectRatio(0.48f),
            color = cs.surface,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera notch pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 28.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(cs.outlineVariant.copy(alpha = 0.6f))
                )

                // Status bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 30.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(cs.onSurface.copy(alpha = 0.25f))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(cs.onSurface.copy(alpha = 0.25f))
                            )
                        }
                    }
                }

                // Banner / hero strip
                Surface(
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cs.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            cs.primary.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp, bottom = 6.dp)
                                .size(width = 28.dp, height = 5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cs.onSecondaryContainer.copy(alpha = 0.55f))
                        )
                    }
                }

                // Two info tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(cs.primaryContainer, cs.secondaryContainer).forEach { tileColor ->
                        Surface(
                            modifier = Modifier.weight(1f).height(44.dp),
                            color = tileColor,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 20.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(cs.onPrimaryContainer.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }

                // Settings-like list block
                Surface(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    color = cs.surfaceColorAtElevation(2.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(3) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cs.primary.copy(alpha = 0.5f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cs.onSurface.copy(alpha = 0.18f))
                                )
                                if (row == 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 16.dp, height = 9.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(cs.primary.copy(alpha = 0.7f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom nav bar
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surfaceColorAtElevation(3.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == 0) 22.dp else 18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (i == 0) cs.primaryContainer
                                    else cs.onSurface.copy(alpha = 0.15f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─── Individual color swatch ──────────────────────────────────────────────────
@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    colorSpec: ColorSpec.SpecVersion,
    label: String = "",
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val targetScheme = if (color == Color.Unspecified) {
        val base = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else lightColorScheme()
        }
        rememberDynamicColorScheme(
            seedColor = base.primary, isDark = isDark, specVersion = colorSpec,
            primary = base.primary, secondary = base.secondary, tertiary = base.tertiary,
            neutral = base.surface, neutralVariant = base.surfaceVariant, error = base.error
        )
    } else {
        rememberDynamicColorScheme(seedColor = color, isDark = isDark, specVersion = colorSpec)
    }

    val cs = animateColorSchemeAsState(targetScheme)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "swatch_scale"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "swatch_border"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = cs.surfaceContainer,
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(
                    width = 2.dp,
                    color = cs.primary.copy(alpha = borderAlpha),
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Dual-tone arc
                Canvas(modifier = Modifier.size(42.dp)) {
                    drawArc(
                        color = cs.primaryContainer,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                    drawArc(
                        color = cs.tertiaryContainer,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                }

                // Check badge or dot
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(cs.primary)
                    )
                }
            }
        }

        // Label — shown only for the "Auto" swatch
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
