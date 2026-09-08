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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.util.Calendar

/**
 * Where we are in the day, expressed as a single continuous 0f..1f value
 * (0 = midnight, 0.5 = noon) rather than discrete named buckets. This lets
 * the glow intensity below drift smoothly instead of snapping between
 * fixed states.
 */
object AmbientGlowCycle {

    fun dayProgress(calendar: Calendar = Calendar.getInstance()): Float {
        val minutesOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return (minutesOfDay / 1440f).coerceIn(0f, 1f)
    }

    /**
     * Ambient brightness multiplier applied on top of the app's own dynamic
     * color scheme, peaking at midday and dipping past midnight. Unlike a
     * fixed day/dusk/night palette, this only ever scales the colors the
     * caller already has (primary/tertiary/surface from MaterialTheme), so
     * the widget always matches whatever Material You seed the user picked.
     */
    fun ambientIntensity(dayProgress: Float): Float {
        // Two lobes: one bright lobe centered on midday, a dim floor overnight.
        val distanceFromNoon = kotlin.math.abs(dayProgress - 0.5f)
        val lift = (1f - (distanceFromNoon * 2f)).coerceIn(0f, 1f)
        return 0.35f + lift * 0.65f
    }

    /** How much the core's glow should lean warm (tertiary) vs cool (primary). */
    fun warmthBlend(dayProgress: Float): Float {
        // Warmest around sunset (~0.75), coolest around dawn (~0.25).
        val phase = (dayProgress - 0.75f + 1f) % 1f
        return (kotlin.math.cos(phase * 2 * Math.PI).toFloat() * 0.5f + 0.5f)
    }

    fun blendAmbientColor(cool: Color, warm: Color, dayProgress: Float): Color =
        lerp(cool, warm, warmthBlend(dayProgress))
}
