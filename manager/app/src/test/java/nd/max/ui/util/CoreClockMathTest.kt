/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ساعة الأنوية — **الحكم الذي طلبه المالك في `MAX-MANAGER-LEVEL-UP.md` §12 (المرحلة ٣)**:
 * «نواة offline ≠ 0 MHz» و«نواة بلا max = MHz بلا %».
 *
 * وهذا الملفّ يقيس ما كان تعليقًا في ملفّ Compose: أن الحالات ثلاث لا رقمٌ واحد، وأن النسبة لا
 * تُخترع من مقام مجهول، وأن المتوسط يقف على ما قيس وحده.
 */
class CoreClockMathTest {

    @Test
    fun `an offline core has no reading and no percent`() {
        val reading = CoreClockMath.reading(online = false, freqMhz = null, ceilingMhz = 2400)
        assertEquals("مطفأة تُكتب «متوقفة» لا «0 MHz»", CoreClockState.OFFLINE, reading.state)
        assertNull(reading.percent)
    }

    @Test
    fun `an offline core stays offline even if a stale frequency is read`() {
        // التردّد قد يبقى مقروءًا لحظة إطفاء النواة؛ فالحكم للنواة لا للرقم العالق.
        val reading = CoreClockMath.reading(online = false, freqMhz = 1800, ceilingMhz = 2400)
        assertEquals(CoreClockState.OFFLINE, reading.state)
        assertNull(reading.percent)
    }

    @Test
    fun `an unreadable frequency is hidden, never zero`() {
        val reading = CoreClockMath.reading(online = true, freqMhz = null, ceilingMhz = 2400)
        assertEquals("النواة تخفي cpufreq", CoreClockState.HIDDEN, reading.state)
        assertNull(reading.percent)
    }

    @Test
    fun `a parked core is a state of its own, not zero megahertz`() {
        val reading = CoreClockMath.reading(online = true, freqMhz = 0, ceilingMhz = 2400)
        assertEquals("صفر القراءة = مسكّنة، وهي حالة ثالثة", CoreClockState.PARKED, reading.state)
        assertNull(reading.percent)
    }

    @Test
    fun `a live core carries its frequency share of the declared ceiling`() {
        val reading = CoreClockMath.reading(online = true, freqMhz = 1800, ceilingMhz = 2400)
        assertEquals(CoreClockState.LIVE, reading.state)
        assertEquals(75, reading.percent)
    }

    @Test
    fun `without a declared ceiling the frequency is shown with no percent`() {
        val reading = CoreClockMath.reading(online = true, freqMhz = 1800, ceilingMhz = 0)
        assertEquals("التردد معروض", CoreClockState.LIVE, reading.state)
        assertNull("ولا نسبة من مقام مجهول", reading.percent)
        assertNull(CoreClockMath.sharePercent(1800, 0))
    }

    @Test
    fun `an unknown cluster policy yields a zero ceiling, not a value from another cluster`() {
        val ceilings = mapOf("/sys/devices/system/cpu/cpufreq/policy0" to 2400)
        assertEquals(2400, CoreClockMath.ceilingMhz("/sys/devices/system/cpu/cpufreq/policy0", ceilings))
        assertEquals(0, CoreClockMath.ceilingMhz("/sys/devices/system/cpu/cpufreq/policy4", ceilings))
    }

    @Test
    fun `a frequency above the declared ceiling is clamped, not printed over one hundred`() {
        assertEquals(100, CoreClockMath.sharePercent(2800, 2400))
    }

    @Test
    fun `the hero average stands on what was measured alone`() {
        assertNull("بلا قياس واحد: لا رقم", CoreClockMath.averagePercent(emptyList()))
        assertEquals(50, CoreClockMath.averagePercent(listOf(25, 75)))
        assertEquals("والمطفأة لا تدخل بصفر", 80, CoreClockMath.averagePercent(listOf(80)))
    }
}
