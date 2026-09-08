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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Minimal snapshot the card needs to render — kept separate from any one
 * screen's battery model so this component doesn't depend on a specific
 * ViewModel shape.
 */
data class PowerCoreInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val currentNowMicroAmps: Int
)

@Composable
fun PowerCoreCard(
    info: PowerCoreInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val engine = remember { PulseFieldEngine() }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val currentInfo by rememberUpdatedState(info)

    val dayProgress = remember { AmbientGlowCycle.dayProgress() }
    val ambientIntensity = remember(dayProgress) { AmbientGlowCycle.ambientIntensity(dayProgress) }

    val scheme = MaterialTheme.colorScheme
    val coolColor = scheme.primary
    val warmColor = scheme.tertiary
    val ambientColor = remember(dayProgress, coolColor, warmColor) {
        AmbientGlowCycle.blendAmbientColor(coolColor, warmColor, dayProgress)
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanosCompat {
                val latest = currentInfo
                val intensity = (kotlin.math.abs(latest.currentNowMicroAmps) / 2_500_000f).coerceIn(0.25f, 1f)
                engine.update(isCharging = latest.isCharging, chargeIntensity = intensity)
            }
        }
    }

    MaxSurfaceBox(
        modifier = modifier,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
        borderEnabled = false,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width != canvasSize.width || size.height != canvasSize.height) {
                    canvasSize = size
                }
                val w = size.width
                val h = size.height
                if (w <= 1f || h <= 1f) return@Canvas

                val center = Offset(w / 2f, h / 2f)
                val fieldRadius = min(w, h) / 2f * 0.92f
                val coreRadius = fieldRadius * (0.16f + (info.levelPercent / 100f) * 0.14f)

                // Ambient backdrop — derived from the app's own dynamic color
                // scheme and scaled by time of day, not a fixed sky palette.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientColor.copy(alpha = 0.22f * ambientIntensity),
                            scheme.surface.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = fieldRadius * 1.3f
                    )
                )

                // Breathing pulse rings.
                val ringCount = 3
                for (i in 0 until ringCount) {
                    val t = ((engine.ringPhase + i / ringCount.toFloat()) % 1f)
                    val ringRadius = coreRadius + (fieldRadius - coreRadius) * t
                    val fade = (1f - t).coerceIn(0f, 1f)
                    PulseArt.run {
                        drawPulseRing(
                            center = center,
                            radius = ringRadius,
                            color = ambientColor,
                            alpha = fade * 0.35f,
                            strokeWidth = 2.5f + fade * 3f
                        )
                    }
                }

                // Orbiting motes.
                engine.motes.forEach { mote ->
                    val radiusPx = coreRadius + (fieldRadius - coreRadius) * mote.radiusPercent
                    val pos = PulseArt.angleToOffset(center, mote.angle, radiusPx)
                    val twinkle = (kotlin.math.sin(mote.twinklePhase) * 0.5f + 0.5f)
                    PulseArt.run {
                        drawMote(
                            center = pos,
                            size = mote.size,
                            color = if (info.isCharging) warmColor else coolColor,
                            alpha = 0.4f + twinkle * 0.5f
                        )
                    }
                }

                // Surge arcs, only meaningful while charging.
                if (info.isCharging) {
                    engine.activeSurges.forEach { surge ->
                        val from = PulseArt.angleToOffset(center, surge.angle, fieldRadius * 0.3f)
                        val to = PulseArt.angleToOffset(center, surge.angle, coreRadius)
                        PulseArt.run { drawSurgeArc(from, to, warmColor, surge.life) }
                    }
                }

                // Core.
                val pulseScale = 1f + engine.corePulse * 0.04f
                PulseArt.run {
                    drawCore(
                        center = center,
                        radius = coreRadius * pulseScale,
                        fillColor = (if (info.isCharging) warmColor else coolColor).copy(alpha = 0.28f),
                        rimColor = if (info.isCharging) warmColor else coolColor,
                        rimAlpha = 0.85f
                    )
                }
            }

            Column(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Battery",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${info.levelPercent}%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (info.isCharging) "Charging" else "On battery",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private suspend inline fun withFrameNanosCompat(crossinline block: () -> Unit) {
    androidx.compose.runtime.withFrameNanos { block() }
}
