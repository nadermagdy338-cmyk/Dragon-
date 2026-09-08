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

@Preview(name = "Aurora • large text", fontScale = 1.5f, widthDp = 360, heightDp = 1600, showBackground = true)
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
