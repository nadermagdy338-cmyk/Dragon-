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

package nd.max.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.ui.design.MaxCardSpec
import nd.max.ui.design.MaxRadius
import nd.max.R
import java.util.Locale
import kotlin.math.abs

/**
 * Dashboard-only battery snapshot. Unknown values stay null so the card never
 * presents missing telemetry as a real zero reading.
 */
data class PowerCoreInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val currentMilliAmps: Int?,
    val voltageV: Float,
    val temperatureC: Float?,
    val powerWatt: Float
)

/**
 * Home power surface. It is deliberately read-only: tapping opens Charge
 * Control, where device-changing controls remain isolated from the dashboard.
 */
@Composable
fun PowerCoreCard(
    info: PowerCoreInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (info.isCharging) colors.tertiary else colors.primary
    val level = info.levelPercent.coerceIn(0, 100)
    val animatedLevel by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "powerCoreLevel"
    )
    val status = stringResource(if (info.isCharging) R.string.charging_status_charging else R.string.charging_status_not_charging)
    val current = info.currentMilliAmps?.let(::formatCurrent) ?: "—"
    val voltage = info.voltageV.takeIf { it > 0f }?.let { formatValue(it, "V") } ?: "—"
    val temperature = info.temperatureC?.let { formatValue(it, "°C") } ?: "—"
    val power = info.powerWatt.takeIf { it > 0f }?.let { formatValue(it, "W") } ?: "—"

    MaxSurfaceBox(
        modifier = modifier.fillMaxWidth(),
        accent = accent,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(MaxCardSpec.radius),
        onClick = onClick
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(MaxRadius.row), color = accent.copy(alpha = .12f)) {
                    Icon(
                        imageVector = if (info.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.charging_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = accent.copy(alpha = .1f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (info.isCharging) R.string.home_active else R.string.home_idle),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text("$level%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = colors.onSurface)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (info.isCharging) stringResource(R.string.charging_session_active) else stringResource(R.string.charging_battery_ready),
                    modifier = Modifier.padding(bottom = 7.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(colors.outlineVariant.copy(alpha = .55f))
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(animatedLevel, 0f..1f) }
            ) {
                Box(Modifier.fillMaxWidth(animatedLevel).height(10.dp).background(accent))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerMetric(Modifier.weight(1f), Icons.Rounded.Bolt, stringResource(R.string.charging_power), power, accent)
                PowerMetric(Modifier.weight(1f), Icons.Rounded.BatteryStd, stringResource(R.string.charging_current), current, accent)
                PowerMetric(Modifier.weight(1f), Icons.Rounded.Thermostat, stringResource(R.string.charging_temp), temperature, accent)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${stringResource(R.string.charging_voltage)} · $voltage",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.home_open_details), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun PowerMetric(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(MaxRadius.row), color = accent.copy(alpha = .07f)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatCurrent(valueMilliAmps: Int): String = when {
    abs(valueMilliAmps) >= 1000 -> String.format(Locale.US, "%.2f A", abs(valueMilliAmps) / 1000f)
    else -> "${abs(valueMilliAmps)} mA"
}

private fun formatValue(value: Float, unit: String): String = String.format(Locale.US, "%.1f %s", value, unit)
