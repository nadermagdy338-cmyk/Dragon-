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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A single point of light orbiting the core. */
data class Mote(
    var angle: Float,
    var radiusPercent: Float,
    var orbitSpeed: Float,
    var size: Float,
    var twinklePhase: Float
)

/** A brief accent arc drawn between an inbound mote and the core when charging. */
data class SurgeArc(var angle: Float, var life: Float)

class PulseFieldEngine {

    var ringPhase by mutableFloatStateOf(0f)
    var corePulse by mutableFloatStateOf(0f)
    var coreBreath by mutableFloatStateOf(0f)

    val motes = mutableListOf<Mote>()
    private val surges = mutableListOf<SurgeArc>()
    val activeSurges: List<SurgeArc> get() = surges

    private val ringSpeedIdle = 0.0018f
    private val ringSpeedActive = 0.006f

    init {
        repeat(MOTE_COUNT) { motes.add(randomMote()) }
    }

    fun update(isCharging: Boolean, chargeIntensity: Float) {
        val speed = if (isCharging) ringSpeedActive * (0.6f + chargeIntensity) else ringSpeedIdle
        ringPhase = (ringPhase + speed) % 1f
        coreBreath += 0.01f

        corePulse = (sin(coreBreath * 2 * PI).toFloat() * 0.5f + 0.5f)

        motes.forEach { mote ->
            mote.angle += mote.orbitSpeed * (if (isCharging) 1.8f else 1f)
            mote.twinklePhase += 0.04f

            if (isCharging) {
                // Feed the core: motes slowly spiral inward, then respawn at the rim.
                mote.radiusPercent -= 0.004f * (0.5f + chargeIntensity)
                if (mote.radiusPercent <= 0.12f) {
                    if (Random.nextFloat() < 0.6f) surges.add(SurgeArc(mote.angle, 1f))
                    resetMote(mote)
                }
            } else {
                // Idle drift: gentle outward creep, wrapped back before it ever
                // looks like it "escaped" the field.
                mote.radiusPercent += 0.0006f
                if (mote.radiusPercent > 1f) mote.radiusPercent = 0.35f + Random.nextFloat() * 0.2f
            }
        }

        val iterator = surges.iterator()
        while (iterator.hasNext()) {
            val surge = iterator.next()
            surge.life -= 0.08f
            if (surge.life <= 0f) iterator.remove()
        }
    }

    private fun resetMote(mote: Mote) {
        mote.radiusPercent = 0.85f + Random.nextFloat() * 0.15f
        mote.angle = Random.nextFloat() * 360f
        mote.orbitSpeed = 0.3f + Random.nextFloat() * 0.6f
        mote.size = 2f + Random.nextFloat() * 3f
    }

    private fun randomMote() = Mote(
        angle = Random.nextFloat() * 360f,
        radiusPercent = 0.3f + Random.nextFloat() * 0.65f,
        orbitSpeed = 0.3f + Random.nextFloat() * 0.6f,
        size = 2f + Random.nextFloat() * 3f,
        twinklePhase = Random.nextFloat() * 10f
    )

    companion object {
        private const val MOTE_COUNT = 22

        fun moteOffset(angleDeg: Float, radiusPx: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angleDeg.toDouble())
            return (cos(rad).toFloat() * radiusPx) to (sin(rad).toFloat() * radiusPx)
        }
    }
}
