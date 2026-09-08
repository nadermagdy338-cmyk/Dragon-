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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import nd.max.ui.theme.MaxTextRole
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A live-updating, smoothly-interpolated area/line chart for real-time hardware
 * telemetry (GPU frequency, load, ZRAM throughput, etc). Meant to sit below a
 * [RadialGaugeCard] as a "trend" companion so a live readout also shows *where
 * it's been*, not just where it is right now.
 *
 * [samples] are expected pre-normalized to 0f..1f (caller decides what 1f means,
 * e.g. hardware max frequency). The curve is smoothed with quadratic bezier
 * midpoints so new samples arrive as a fluid ribbon instead of a jagged line.
 */
@Composable
fun LiveHistoryGraph(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueLabel: String? = null,
    lineColor: Color = LocalScreenAccent.current ?: MaterialTheme.colorScheme.primary,
    emptyStateText: String = "",
    height: androidx.compose.ui.unit.Dp = 96.dp,
    gridLines: Int = 3
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, MaterialTheme.shapes.extraLarge)
            .padding(20.dp)
    ) {
        if (label != null || valueLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LivePulseDot(color = lineColor)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (valueLabel != null) {
                    Text(
                        text = valueLabel,
                        style = MaxTextRole.liveValue.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (samples.size < 2) {
            Box(
                modifier = Modifier.fillMaxWidth().height(height),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyStateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SmoothTrendCurve(
                samples = samples,
                lineColor = lineColor,
                gridColor = gridColor,
                gridLines = gridLines,
                modifier = Modifier.fillMaxWidth().height(height)
            )
        }
    }
}

@Composable
private fun SmoothTrendCurve(
    samples: List<Float>,
    lineColor: Color,
    gridColor: Color,
    gridLines: Int,
    modifier: Modifier = Modifier
) {
    // A gentle traveling shimmer on the fill, so the graph reads as "alive"
    // even when values are momentarily flat.
    val infiniteTransition = rememberInfiniteTransition(label = "graphShimmer")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable<Float>(
            animation = tween<Float>(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "graphShimmerValue"
    )

    val leadAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "graphLeadAlpha"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val topInset = 10f
        val bottomInset = 10f
        val usableH = (h - topInset - bottomInset).coerceAtLeast(1f)

        // Grid lines
        if (gridLines > 0) {
            for (i in 0..gridLines) {
                val y = topInset + usableH * (i / gridLines.toFloat())
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                )
            }
        }

        val n = samples.size
        val stepX = w / (n - 1).toFloat()
        fun pointFor(i: Int): Offset {
            val clamped = samples[i].coerceIn(0f, 1f)
            val x = stepX * i
            val y = topInset + usableH * (1f - clamped)
            return Offset(x, y)
        }

        val points = List(n) { pointFor(it) }

        val linePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                val midY = (p0.y + p1.y) / 2f
                quadraticTo(p0.x, p0.y, midX, midY)
            }
            lineTo(points.last().x, points.last().y)
        }

        val fillPath = androidx.compose.ui.graphics.Path().apply {
            addPath(linePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        val shimmerAlpha: Float = 0.32f + 0.06f * kotlin.math.sin(shimmer * 6.283f)

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = shimmerAlpha.coerceIn(0f, 1f)),
                    lineColor.copy(alpha = 0.02f)
                ),
                startY = 0f,
                endY = h
            )
        )

        // Soft glow behind the line: layered strokes, widest+faintest first.
        val glowWidths = listOf(10.dp.toPx() to 0.08f, 6.dp.toPx() to 0.14f)
        glowWidths.forEach { (strokeW, alpha) ->
            drawPath(
                path = linePath,
                color = lineColor.copy(alpha = alpha),
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Leading point marker (the "now" edge of the trend).
        val lead = points.last()
        drawCircle(
            color = lineColor.copy(alpha = 0.25f * leadAlpha),
            radius = 9.dp.toPx(),
            center = lead
        )
        drawCircle(
            color = lineColor,
            radius = 3.6.dp.toPx(),
            center = lead
        )
    }
}

/** Compact inline trend, for use inside stat rows or small cards without the full card chrome. */
@Composable
fun MiniSparkline(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = LocalScreenAccent.current ?: MaterialTheme.colorScheme.primary,
    width: androidx.compose.ui.unit.Dp = 64.dp,
    height: androidx.compose.ui.unit.Dp = 24.dp
) {
    if (samples.size < 2) {
        Box(modifier = modifier.size(width = width, height = height))
        return
    }
    Canvas(modifier = modifier.size(width = width, height = height)) {
        val w = size.width
        val h = size.height
        val n = samples.size
        val stepX = w / (n - 1).toFloat()
        val points = List(n) { i ->
            val v = samples[i].coerceIn(0f, 1f)
            Offset(stepX * i, h * (1f - v))
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
            }
            lineTo(points.last().x, points.last().y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
