/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package nd.max.ui.activitylauncher

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import nd.max.ui.components.VideoWallpaperPlayer
import nd.max.ui.components.WeatherEffectOverlay
import nd.max.ui.settings.BgType
import nd.max.ui.settings.SettingsViewModel
import nd.max.ui.settings.WeatherEffect

/**
 * Every resolved color/flag both Activity Launcher screens need to render the
 * "glass" theme. Both screens used to re-derive this from SettingsViewModel by
 * hand (~90 near-identical lines each) — that logic now lives in one place so
 * the two screens can't drift out of sync.
 */
data class GlassPalette(
    val primary: Color,
    val isDark: Boolean,
    val isCustomBg: Boolean,
    val isGlassActive: Boolean,
    val backgroundColor: Color,
    val cardColor: Color,
    val textColor: Color,
    val subTextColor: Color,
    val bgUriString: String?,
    val isVideo: Boolean,
    val isBgBlur: Boolean,
    val blurStrength: Float,
    val bgSaturation: Float,
    val bgContrast: Float,
    val weatherEffect: WeatherEffect,
    val weatherIntensity: Float
)

@Composable
fun rememberGlassPalette(): GlassPalette {
    val settingsViewModel: SettingsViewModel = viewModel()
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()

    val effectivePrimary = when {
        isDynamic -> MaterialTheme.colorScheme.primary
        isCustomColor -> Color(customPrimary)
        else -> themeColorName.primary
    }

    val isDark = when (themeMode) {
        nd.max.ui.theme.ThemeMode.LIGHT -> false
        nd.max.ui.theme.ThemeMode.DARK -> true
        nd.max.ui.theme.ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }

    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg

    val mainBackgroundColor = if (isCustomBg) Color.Transparent else MaterialTheme.colorScheme.surface

    val finalCardColor = when {
        isGlassActive -> Color.Transparent
        isCustomBg -> Color.Black.copy(alpha = cardDarkness)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // Text on a custom wallpaper (with or without glass) always reads better in
    // white; on the generated system tint it follows the Material color scheme.
    val textColor = if (isCustomBg) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isCustomBg) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    return GlassPalette(
        primary = effectivePrimary,
        isDark = isDark,
        isCustomBg = isCustomBg,
        isGlassActive = isGlassActive,
        backgroundColor = mainBackgroundColor,
        cardColor = finalCardColor,
        textColor = textColor,
        subTextColor = subTextColor,
        bgUriString = bgUriString,
        isVideo = isVideo,
        isBgBlur = isBgBlur,
        blurStrength = blurStrength,
        bgSaturation = bgSaturation,
        bgContrast = bgContrast,
        weatherEffect = weatherEffect,
        weatherIntensity = weatherIntensity
    )
}

/**
 * Draws the wallpaper/blur/weather backdrop shared by both Activity Launcher
 * screens, then hosts [content] on top of it inside the same full-size [Box].
 */
@Composable
fun GlassScreenScaffold(
    hazeState: HazeState,
    palette: GlassPalette,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(palette.backgroundColor)) {
        if (palette.isCustomBg && palette.bgUriString != null) {
            val blurModifier = when {
                palette.isBgBlur && palette.blurStrength > 0f -> Modifier.blur(palette.blurStrength.dp)
                palette.isBgBlur -> Modifier.blur(20.dp)
                else -> Modifier
            }
            val colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(palette.bgSaturation) })
            val uri = Uri.parse(palette.bgUriString)

            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                if (palette.isVideo) {
                    VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(uri).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().then(blurModifier),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter
                    )
                }
                if (palette.bgContrast > 0f) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = palette.bgContrast)))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f))
        }

        WeatherEffectOverlay(
            effect = palette.weatherEffect,
            intensity = palette.weatherIntensity,
            modifier = Modifier.fillMaxSize()
        )

        content()
    }
}
