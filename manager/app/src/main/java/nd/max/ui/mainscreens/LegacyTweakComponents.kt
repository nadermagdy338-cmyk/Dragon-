/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.component.LeadingIcon
import nd.max.ui.component.MaxLoadingState
import nd.max.ui.component.MaxManagerSectionTitle
import nd.max.ui.component.MaxSlider

/*
 * Shared tweak primitives that used to live in the retired all-tweaks screen.
 * That screen is gone, but these three rows are still called from
 * DebloatFreezeScreen, DisplayStudioScreen, GovSettingsScreen, LogsViewerScreen,
 * ProcessManagerScreen and SetEditScreen, which import them from this package
 * by name. They are kept here byte-for-byte so those screens compile and look
 * exactly as before; they will be retired one at a time as each of those
 * sub-screens is migrated to the nd.max.ui.design system.
 */

@Composable
fun SectionLoadingIndicator() {
    MaxLoadingState(
        title = stringResource(R.string.loading),
        message = "",
        compact = true
    )
}

/**
 * Thin wrapper over [MaxManagerSectionTitle] so the legacy call sites keep the
 * primary-tinted trace motif they had on the old tweaks screen.
 */
@Composable
fun TweaksSectionTitle(text: String) {
    MaxManagerSectionTitle(text = text, accent = MaterialTheme.colorScheme.primary)
}

@Composable
fun FreqLimitSliderItem(
    icon: ImageVector? = null,
    initialValue: Float,
    labels: List<String>,
    onSaved: (Float) -> Unit
) {
    var sliderValue by remember { mutableStateOf(initialValue) }
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    LeadingIcon(icon = icon, contentDescription = stringResource(R.string.freq_offset))
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = stringResource(R.string.freq_offset),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            }

            Surface(
                color = if (sliderValue.roundToInt() == 0) colorScheme.surfaceVariant else colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (sliderValue.roundToInt() == 0) stringResource(R.string.disabled) else labels[sliderValue.roundToInt()],
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (sliderValue.roundToInt() == 0) colorScheme.onSurfaceVariant else colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        MaxSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSaved(sliderValue) },
            valueRange = 0f..6f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.disabled), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
            Text(stringResource(R.string.str_40), style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
        }
    }
}
