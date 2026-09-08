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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Which ambient motion pattern to layer over a background. */
enum class AmbientMotif { NONE, DRIFT, STREAK, SPARKLE, GLOW }

@Composable
fun AmbientMotifOverlay(
    motif: AmbientMotif,
    intensity: Float,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    when (motif) {
        AmbientMotif.NONE -> {}
        AmbientMotif.DRIFT -> DriftLayer(intensity, tint, modifier)
        AmbientMotif.STREAK -> StreakLayer(intensity, tint, modifier)
        AmbientMotif.SPARKLE -> SparkleLayer(intensity, tint, modifier)
        AmbientMotif.GLOW -> GlowLayer(intensity, tint, modifier)
    }
}

// ==================== DRIFT — soft orbs wandering in loose figure-eight paths ====================
private data class DriftOrb(
    val baseX: Float,
    val baseY: Float,
    val swingX: Float,
    val swingY: Float,
    val radius: Float,
    val speed: Float,
    val phaseOffset: Float
) {
    companion object {
        fun random() = DriftOrb(
            baseX = Random.nextFloat(),
            baseY = Random.nextFloat(),
            swingX = 0.08f + Random.nextFloat() * 0.12f,
            swingY = 0.05f + Random.nextFloat() * 0.08f,
            radius = 0.12f + Random.nextFloat() * 0.18f,
            speed = 0.4f + Random.nextFloat() * 0.6f,
            phaseOffset = Random.nextFloat() * 2f * PI.toFloat()
        )
    }
}

@Composable
private fun DriftLayer(intensity: Float, tint: Color, modifier: Modifier) {
    val orbs = remember { List(6) { DriftOrb.random() } }
    val transition = rememberInfiniteTransition(label = "drift")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "drift_t"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        orbs.forEach { orb ->
            val phase = t * orb.speed + orb.phaseOffset
            // Figure-eight (Lissajous-style) drift instead of a single linear pass.
            val cx = (orb.baseX + sin(phase) * orb.swingX) * size.width
            val cy = (orb.baseY + sin(phase * 2f) * orb.swingY) * size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.18f * intensity), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.width * orb.radius
                ),
                radius = size.width * orb.radius,
                center = Offset(cx, cy),
                blendMode = BlendMode.Screen
            )
        }
    }
}

// ==================== STREAK — diagonal light traces sweeping the frame ====================
private data class LightStreak(
    val lane: Float,
    val length: Float,
    val thickness: Float,
    val delay: Float,
    val speed: Float
) {
    companion object {
        fun random() = LightStreak(
            lane = Random.nextFloat(),
            length = 0.15f + Random.nextFloat() * 0.25f,
            thickness = 1.5f + Random.nextFloat() * 2.5f,
            delay = Random.nextFloat(),
            speed = 0.7f + Random.nextFloat() * 0.6f
        )
    }
}

@Composable
private fun StreakLayer(intensity: Float, tint: Color, modifier: Modifier) {
    val streaks = remember { List((8 + (intensity * 18).toInt())) { LightStreak.random() } }
    val transition = rememberInfiniteTransition(label = "streak")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "streak_t"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        streaks.forEach { streak ->
            val progress = ((t * streak.speed + streak.delay) % 1f)
            // Diagonal travel (top-right to bottom-left) rather than straight-down rain.
            val startX = size.width * (streak.lane + 0.25f - progress * 1.3f)
            val startY = size.height * (progress - 0.1f)
            val endX = startX + size.width * streak.length * 0.6f
            val endY = startY - size.height * streak.length

            val edgeFade = if (progress < 0.08f) progress / 0.08f
            else if (progress > 0.9f) (1f - progress) / 0.1f
            else 1f

            drawLine(
                color = tint.copy(alpha = edgeFade * intensity * 0.5f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = streak.thickness,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Screen
            )
        }
    }
}

// ==================== SPARKLE — stationary points that twinkle in place, no fall ====================
private data class TwinklePoint(
    val x: Float,
    val y: Float,
    val size: Float,
    val blinkSpeed: Float,
    var phase: Float
) {
    companion object {
        fun random() = TwinklePoint(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            size = 1.5f + Random.nextFloat() * 2.5f,
            blinkSpeed = 0.6f + Random.nextFloat() * 1.4f,
            phase = Random.nextFloat() * 2f * PI.toFloat()
        )
    }
}

@Composable
private fun SparkleLayer(intensity: Float, tint: Color, modifier: Modifier) {
    val points = remember { List((20 + (intensity * 40).toInt())) { TwinklePoint.random() } }
    val transition = rememberInfiniteTransition(label = "sparkle")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "sparkle_t"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        points.forEach { point ->
            val blink = (sin(t * point.blinkSpeed + point.phase) * 0.5f + 0.5f)
            drawCircle(
                color = tint.copy(alpha = blink * intensity * 0.8f),
                radius = point.size,
                center = Offset(point.x * size.width, point.y * size.height),
                blendMode = BlendMode.Screen
            )
        }
    }
}

// ==================== GLOW — a single breathing bloom, no radiating rays ====================
@Composable
private fun GlowLayer(intensity: Float, tint: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "glow")
    val breathe by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow_breathe"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.78f, size.height * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = intensity * 0.32f), Color.Transparent),
                    center = center,
                    radius = size.width * 0.35f * breathe
                ),
                radius = size.width * 0.35f * breathe,
                center = center,
                blendMode = BlendMode.Screen
            )
        }
    }
}
