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

package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مقياس التردد — الوعد الذي يحرسه هذا الاختبار: **لا سقف معلَن ⇒ لا شريط**، والقراءة
 * الصغيرة تُرى، والقراءة التي تتجاوز السقف تُقصّ ولا تُبالغ.
 */
class ClockMeterModelTest {

    @Test
    fun aReadingWithADeclaredCeilingIsMeasured() {
        assertEquals(ClockTrust.MEASURED, ClockMeter.trust(currentMhz = 2_400, ceilingMhz = 3_200))
        assertEquals(0.75f, ClockMeter.fraction(2_400, 3_200)!!, 0.0001f)
    }

    /** النواة تُعلن السقف صفرًا أو لا تُعلنه: لا تعبئة، ولو كانت الساعة تُقرأ. */
    @Test
    fun aReadingWithoutACeilingHasNoFillAtAll() {
        assertEquals(ClockTrust.CEILING_UNREADABLE, ClockMeter.trust(currentMhz = 1_800, ceilingMhz = null))
        assertEquals(ClockTrust.CEILING_UNREADABLE, ClockMeter.trust(currentMhz = 1_800, ceilingMhz = 0))
        assertNull(ClockMeter.fraction(1_800, null))
        assertNull(ClockMeter.fraction(1_800, 0))
        assertEquals(0, ClockMeter.litSteps(ClockMeter.fraction(1_800, null)))
    }

    /** نواة مُطفأة ليست تردّدًا صفريًا: لا رقم ولا شريط. */
    @Test
    fun anOfflineCoreIsNotAZeroReading() {
        assertEquals(ClockTrust.NO_READING, ClockMeter.trust(currentMhz = 0, ceilingMhz = 3_200))
        assertEquals(ClockTrust.NO_READING, ClockMeter.trust(currentMhz = -5, ceilingMhz = 3_200))
        assertNull(ClockMeter.fraction(0, 3_200))
        assertEquals(0, ClockMeter.litSteps(null))
    }

    @Test
    fun aReadingAboveTheDeclaredCeilingIsClampedNotExaggerated() {
        assertEquals(1f, ClockMeter.fraction(4_100, 3_200)!!, 0.0001f)
        assertEquals(ClockMeter.STEPS, ClockMeter.litSteps(ClockMeter.fraction(4_100, 3_200)))
    }

    @Test
    fun theSmallestVisibleReadingStillLightsAStep() {
        val fraction = ClockMeter.fraction(currentMhz = 20, ceilingMhz = 3_200)!!

        assertTrue("القراءة الموجبة تُرى: ١٪ ليس ٠٪", ClockMeter.litSteps(fraction) >= 1)
        assertEquals(1, ClockMeter.litSteps(fraction))
    }

    @Test
    fun aFullReadingLightsEveryStepAndNothingBeyondIt() {
        assertEquals(ClockMeter.STEPS, ClockMeter.litSteps(1f))
        assertTrue(ClockMeter.isLit(ClockMeter.STEPS - 1, 1f))
        assertFalse("لا مقطع خارج المدرّج", ClockMeter.isLit(ClockMeter.STEPS, 1f))
        assertFalse(ClockMeter.isLit(-1, 1f))
    }

    @Test
    fun theStepCountIsHonouredAndNeverNegative() {
        assertEquals(5, ClockMeter.litSteps(1f, steps = 5))
        assertEquals(0, ClockMeter.litSteps(1f, steps = 0))
        assertEquals(0, ClockMeter.litSteps(1f, steps = -3))
        assertFalse(ClockMeter.isLit(0, 0.5f, steps = 0))
    }

    @Test
    fun halfTheRangeLightsHalfTheSteps() {
        assertEquals(6, ClockMeter.litSteps(0.5f, steps = 12))
        assertEquals(3, ClockMeter.litSteps(0.25f, steps = 12))
    }
}
