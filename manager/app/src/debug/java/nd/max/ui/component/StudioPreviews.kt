/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.mainscreens.HOME_DECK_DEFAULT
import nd.max.ui.mainscreens.HomeDashboardContent
import nd.max.ui.mainscreens.HomeDeckPool
import nd.max.ui.util.LoadSample
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import com.materialkolor.rememberDynamicColorScheme
import nd.max.R
import nd.max.ui.theme.MaxManagerBrandSeed
import nd.max.ui.theme.Shapes
import nd.max.ui.theme.Typography

@Preview(name = "Aurora • phone", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
private fun StudioLightPreview() = StudioPreviewContent(dark = false)

@Preview(name = "Aurora • dark", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
private fun StudioDarkPreview() = StudioPreviewContent(dark = true)

@Preview(name = "Aurora • Arabic", locale = "ar", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
private fun StudioArabicPreview() = StudioPreviewContent(dark = false)

@Preview(name = "Aurora • large text", fontScale = 2.0f, widthDp = 360, heightDp = 1600, showBackground = true)
@Composable
private fun StudioLargeTextPreview() = StudioPreviewContent(dark = true)

// Sample telemetry is restricted to debug previews and never enters the live dashboard.
@Composable
private fun StudioPreviewContent(dark: Boolean) {
    val colors = rememberDynamicColorScheme(
        seedColor = MaxManagerBrandSeed,
        isDark = dark,
        primary = MaxManagerBrandSeed,
        secondary = androidx.compose.ui.graphics.Color(0xFF596F6B),
        tertiary = androidx.compose.ui.graphics.Color(0xFFB36A42)
    )
    MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes) {
        Surface(color = colors.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                StudioPerformanceHero(
                    deviceName = "MaxManager · Preview",
                    online = true,
                    cpuLoad = 38,
                    cpuFreq = 2400,
                    temperature = "36°",
                    profile = stringResource(R.string.max_home_profile),
                    history = listOf(18f, 21f, 20f, 34f, 28f, 46f, 40f, 38f),
                    activeApp = null,
                    profileEnabled = true,
                    onProfile = {},
                    onApps = {}
                )
                MaxSectionHeader(stringResource(R.string.max_home_control_center))
                MaxSurface {
                    MaxSlider(value = .62f, onValueChange = {})
                    MaxSlider(value = .35f, onValueChange = {}, enabled = false)
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MaxSwitch(checked = true, onCheckedChange = {})
                        MaxSwitch(checked = false, onCheckedChange = {})
                        MaxSwitch(checked = true, onCheckedChange = {}, enabled = false)
                    }
                    StudioButton(onClick = {}) { androidx.compose.material3.Text(stringResource(R.string.max_home_change_profile)) }
                    StudioOutlinedButton(onClick = {}) { androidx.compose.material3.Text(stringResource(R.string.max_home_app_profiles)) }
                }
                StudioAdaptivePair {
                    StudioShortcut(
                        title = stringResource(R.string.max_home_cpu_control),
                        subtitle = stringResource(R.string.max_home_cpu_control_desc),
                        icon = Icons.Rounded.Tune,
                        accent = colors.primary,
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                    StudioShortcut(
                        title = stringResource(R.string.max_home_display_control),
                        subtitle = stringResource(R.string.max_home_display_control_desc),
                        icon = Icons.Rounded.DisplaySettings,
                        accent = colors.tertiary,
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}


@Preview(name = "Home • phone", widthDp = 393, heightDp = 1500, showBackground = true)
@Composable
private fun HomePhonePreview() = HomeCommandPreview()

@Preview(name = "Home • wide", widthDp = 1100, heightDp = 1200, showBackground = true)
@Composable
private fun HomeWidePreview() = HomeCommandPreview()

@Preview(name = "Home • RTL", locale = "ar", widthDp = 393, heightDp = 1600, showBackground = true)
@Composable
private fun HomeRtlPreview() = HomeCommandPreview()

@Preview(name = "Home • large text", fontScale = 2f, widthDp = 393, heightDp = 2200, showBackground = true)
@Composable
private fun HomeLargeTextPreview() = HomeCommandPreview()

@Preview(name = "Home • missing data", widthDp = 393, heightDp = 1500, showBackground = true)
@Composable
private fun HomeMissingPreview() = HomeCommandPreview(missing = true)

@Composable
private fun HomeCommandPreview(missing: Boolean = false) {
    // تاريخ مصنوع للعرض: الشكل الذي يُراجع به الطيف كامل الفتحات بأعمدة CPU+GPU.
    val previewSamples = List(20) { index ->
        LoadSample(
            atMs = index * 2_000L,
            cpu = (28 + (index * 9) % 58).toFloat(),
            gpu = (18 + (index * 6) % 44).toFloat(),
        )
    }
    val colors = rememberDynamicColorScheme(seedColor = MaxManagerBrandSeed, isDark = true, primary = MaxManagerBrandSeed, secondary = androidx.compose.ui.graphics.Color(0xFF00B7C7), tertiary = androidx.compose.ui.graphics.Color(0xFF9B7BFF))
    MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes) {
        HomeDashboardContent(
            ui = HomeUiState(rootStatus = true, moduleInstalled = true, autoMode = "0"),
            dashboard = if (missing) DashboardState(chipsetName = "Unknown SoC") else DashboardState(ramUsedMb = 4300, ramTotalMb = 8192, cpuLoadPercent = 48, cpuFreqMhz = 2400, chipsetName = "Snapdragon 8 Gen 3", batteryPercent = 74, batteryTempC = 37.4f, batteryStatus = "Discharging", storageUsedGb = 128f, storageTotalGb = 256f, downloadSpeedKbps = 850, uploadSpeedKbps = 120, displayWidth = 1440, displayHeight = 3200, displayRefreshHz = 120, loadSamples = previewSamples),
            maxAi = MaxAiState(aiEnabled = true, strategyLabel = "Balanced"),
            profileRequest = ProfileRequestState(),
            deviceName = "MAX Preview Device",
            deckEntries = HomeDeckPool.take(HOME_DECK_DEFAULT),
            onOpenDeck = {}, onConfigureDeck = {},
            onNavigate = {}, onReboot = {}, onSettings = {}, onAiRetry = {}
        )
    }
}
