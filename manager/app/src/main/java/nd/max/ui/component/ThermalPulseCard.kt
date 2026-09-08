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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Named bands so the caller (and QA) can reason about state without magic numbers. */
enum class ThermalBand(val label: String) {
    COLD("Cold"), NORMAL("Normal"), ELEVATED("Elevated"), HIGH("High"), CRITICAL("Critical")
}

data class ThermalPulseInfo(val celsius: Float)

private fun bandFor(celsius: Float): ThermalBand = when {
    celsius < 30f -> ThermalBand.COLD
    celsius < 36f -> ThermalBand.NORMAL
    celsius < 41f -> ThermalBand.ELEVATED
    celsius < 45f -> ThermalBand.HIGH
    else -> ThermalBand.CRITICAL
}

@Composable
fun ThermalPulseCard(
    info: ThermalPulseInfo,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val band = bandFor(info.celsius)
    val scheme = MaterialTheme.colorScheme
    val semantic = maxSemanticColors()

    // Cool bands lean on the app's own dynamic color scheme; only the hot
    // bands borrow the conventional amber/red safety cue, which is a
    // universal UX convention rather than anything specific to one app.
    val targetColor = when (band) {
        ThermalBand.COLD -> scheme.tertiary
        ThermalBand.NORMAL -> scheme.primary
        ThermalBand.ELEVATED -> semantic.warning
        ThermalBand.HIGH -> semantic.warning
        ThermalBand.CRITICAL -> semantic.critical
    }
    val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(800), label = "thermal_color")

    val blinkDuration = when (band) {
        ThermalBand.CRITICAL -> 500
        ThermalBand.HIGH -> 900
        else -> 1600
    }
    val transition = rememberInfiniteTransition(label = "thermal_segments")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(blinkDuration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thermal_sweep"
    )

    val segmentCount = 10
    val litSegments = ((info.celsius.coerceIn(15f, 50f) - 15f) / 35f * segmentCount).toInt().coerceIn(1, segmentCount)

    MaxSurfaceBox(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Row(
            modifier = Modifier.padding(MaxUiMetrics.cardPadding).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${info.celsius}°C",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Surface(
                    color = animatedColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = band.label,
                        color = animatedColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // A ring of discrete arc segments lights up progressively with
            // temperature, and the leading segment pulses — a segmented
            // gauge silhouette rather than a continuous rotating sweep.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 6.dp.toPx()
                    val radius = (size.minDimension / 2f) - strokeWidth
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val gapDeg = 6f
                    val segmentSweep = (360f / segmentCount) - gapDeg

                    for (i in 0 until segmentCount) {
                        val startAngle = -90f + i * (360f / segmentCount) + gapDeg / 2f
                        val isLit = i < litSegments
                        val isLeading = i == litSegments - 1
                        val alpha = when {
                            !isLit -> 0.12f
                            isLeading -> 0.5f + sweep * 0.5f
                            else -> 0.85f
                        }
                        drawArc(
                            color = if (isLit) animatedColor.copy(alpha = alpha) else textColor.copy(alpha = alpha),
                            startAngle = startAngle,
                            sweepAngle = segmentSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    drawCircle(color = animatedColor, radius = 5.dp.toPx(), center = center)
                }
            }
        }
    }
}
