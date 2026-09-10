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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import nd.max.ui.theme.MonoValueStyleLarge
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.theme.MonoValueStyleSmall
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP_ANGLE = 240f

/**
 * The signature visual for every live-hardware screen in this app: an
 * instrument-cluster style radial gauge (start 150°, sweep 240°, gap at the
 * bottom) instead of a plain text summary card. Every hub with a live reading
 * (GPU frequency, ZRAM usage, charging rate, resolution scale) uses the same
 * gauge shape so the whole tuning section reads as one connected instrument
 * panel rather than a stack of unrelated cards.
 */
@Composable
fun RadialGaugeCard(
    title: String,
    valueText: String,
    unitText: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isLive: Boolean = false,
    accentColor: Color = LocalScreenAccent.current ?: MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    glowEnabled: Boolean = true,
    gradientStroke: Boolean = true,
    tickCount: Int = 9,
    tipMarkerMinFraction: Float = 0f
) {
    // Slight spring overshoot gives the needle a bit of real-instrument "kick"
    // instead of a flat digital tween, matching the app's expressive motion scheme.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gaugeFraction"
    )
    val secondaryTone = Color(
        red = (accentColor.red + MaterialTheme.colorScheme.tertiary.red) / 2f,
        green = (accentColor.green + MaterialTheme.colorScheme.tertiary.green) / 2f,
        blue = (accentColor.blue + MaterialTheme.colorScheme.tertiary.blue) / 2f,
        alpha = 1f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, MaterialTheme.shapes.extraLarge)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLive) {
                LivePulseDot(color = accentColor)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.size(168.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val diameter = min(size.width, size.height) - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = diameter / 2f

                // Base track
                drawArc(
                    color = trackColor,
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = GAUGE_SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Minor tick marks around the full sweep, instrument-cluster style
                if (tickCount > 1) {
                    val tickInner = radius - strokeWidth / 2f - 2.dp.toPx()
                    val tickOuter = radius + strokeWidth / 2f + 4.dp.toPx()
                    for (i in 0 until tickCount) {
                        val t = i / (tickCount - 1).toFloat()
                        val angleDeg = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * t
                        val angleRad = angleDeg * (PI / 180f).toFloat()
                        val dx = cos(angleRad)
                        val dy = sin(angleRad)
                        val lit = t <= animatedFraction
                        drawLine(
                            color = if (lit) accentColor.copy(alpha = 0.55f) else trackColor.copy(alpha = 0.9f),
                            start = Offset(center.x + dx * tickInner, center.y + dy * tickInner),
                            end = Offset(center.x + dx * tickOuter, center.y + dy * tickOuter),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                val sweepNow = GAUGE_SWEEP_ANGLE * animatedFraction

                // Soft layered glow behind the value arc (cheap, API-safe substitute
                // for a real blur: wider+fainter strokes stacked under the crisp one).
                if (glowEnabled && sweepNow > 0.5f) {
                    val glowLayers = listOf(strokeWidth + 16.dp.toPx() to 0.10f, strokeWidth + 8.dp.toPx() to 0.16f)
                    glowLayers.forEach { (w, a) ->
                        drawArc(
                            color = accentColor.copy(alpha = a),
                            startAngle = GAUGE_START_ANGLE,
                            sweepAngle = sweepNow,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = w, cap = StrokeCap.Round)
                        )
                    }
                }

                val valueBrush = if (gradientStroke) {
                    Brush.linearGradient(
                        colors = listOf(accentColor, secondaryTone),
                        start = Offset(topLeft.x, topLeft.y),
                        end = Offset(topLeft.x + arcSize.width, topLeft.y + arcSize.height)
                    )
                } else {
                    Brush.linearGradient(colors = listOf(accentColor, accentColor))
                }

                drawArc(
                    brush = valueBrush,
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = sweepNow,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Bright pulsing tip marker at the current value's position, echoing
                // a needle-tip on a real instrument.
                if (sweepNow > 0.5f && animatedFraction >= tipMarkerMinFraction.coerceIn(0f, 1f)) {
                    val tipAngleRad = (GAUGE_START_ANGLE + sweepNow) * (PI / 180f).toFloat()
                    val tipCenter = Offset(
                        center.x + cos(tipAngleRad) * radius,
                        center.y + sin(tipAngleRad) * radius
                    )
                    val pulseScale = 1f
                    drawCircle(
                        color = accentColor.copy(alpha = 0.25f),
                        radius = (strokeWidth / 2f + 6.dp.toPx()) * pulseScale,
                        center = tipCenter
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = strokeWidth / 2.6f,
                        center = tipCenter
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    style = MonoValueStyleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unitText,
                    style = MonoValueStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (subtitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Static live-data marker; polling values already provide visible motion. */
@Composable
fun LivePulseDot(color: Color = LocalScreenAccent.current ?: MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color)
    )
}

/**
 * A row of compact instrument-style readouts (label on top, monospace value
 * below) used instead of a paragraph of comma-joined stats, so every screen's
 * secondary numbers read the same way as the primary gauge.
 */
@Composable
fun StatTickRow(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    valueTextDirection: TextDirection? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        stats.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MonoValueStyleMedium.copy(
                        textDirection = valueTextDirection ?: MonoValueStyleMedium.textDirection
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
