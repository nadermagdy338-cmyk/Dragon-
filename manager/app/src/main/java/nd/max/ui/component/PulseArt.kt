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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

object PulseArt {

    /** One breathing ring of light, thickness fading toward its outer edge. */
    fun DrawScope.drawPulseRing(
        center: Offset,
        radius: Float,
        color: Color,
        alpha: Float,
        strokeWidth: Float
    ) {
        if (alpha <= 0.01f || radius <= 0f) return
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            alpha = alpha,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }

    /** The central charge-level core: a soft filled disc plus a bright rim. */
    fun DrawScope.drawCore(
        center: Offset,
        radius: Float,
        fillColor: Color,
        rimColor: Color,
        rimAlpha: Float
    ) {
        drawCircle(color = fillColor, radius = radius, center = center)
        drawCircle(
            color = rimColor,
            radius = radius,
            center = center,
            alpha = rimAlpha,
            style = Stroke(width = radius * 0.08f)
        )
    }

    fun DrawScope.drawMote(center: Offset, size: Float, color: Color, alpha: Float) {
        drawCircle(color = color, radius = size, center = center, alpha = alpha)
    }

    /** A short jagged spark connecting an inbound mote to the core rim, used only while charging. */
    fun DrawScope.drawSurgeArc(
        from: Offset,
        to: Offset,
        color: Color,
        alpha: Float
    ) {
        if (alpha <= 0.02f) return
        val midX = (from.x + to.x) / 2f + (to.y - from.y) * 0.08f
        val midY = (from.y + to.y) / 2f - (to.x - from.x) * 0.08f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(from.x, from.y)
            lineTo(midX, midY)
            lineTo(to.x, to.y)
        }
        drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }

    fun angleToOffset(center: Offset, angleDeg: Float, radius: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        return Offset(center.x + cos(rad).toFloat() * radius, center.y + sin(rad).toFloat() * radius)
    }
}
