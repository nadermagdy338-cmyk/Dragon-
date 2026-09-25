/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هندسة موجة الساعة — الحدود التي تمنع رسم معلومة لم تُقس.
 *
 * وأخطرها ليس الرياضيات، بل **الصفر الكاذب**: لو تحوّلت عيّنة غير مقروءة إلى `0f` لظهرت
 * في المنحنى هبوطًا إلى القاع، ويقرؤه المستخدم «الجهاز سكن» بينما الحقيقة أننا لم نقرأ.
 */
class ClockWaveTest {

    @Test fun withoutADeclaredCeilingThereIsNoWaveAtAll() {
        val clocks = listOf(800, 1200, 2400)
        assertFalse(ClockWave.canDraw(clocks, null))
        assertFalse(ClockWave.canDraw(clocks, 0))
        assertTrue(ClockWave.series(clocks, null).all { it == null })
        // ولا يُشتق سقف من أعلى قيمة رآها التطبيق: ذلك يقيس تاريخ قياساتنا لا العتاد.
    }

    @Test fun aReadingIsNormalisedAgainstTheDeclaredCeiling() {
        val s = ClockWave.series(listOf(1_000_000, 2_000_000), 4_000_000)
        assertEquals(0.25f, s[0]!!, 1e-6f)
        assertEquals(0.5f, s[1]!!, 1e-6f)
    }

    @Test fun aReadingAboveTheCeilingIsClampedNotScaled() {
        val s = ClockWave.series(listOf(5_000_000), 2_000_000)
        assertEquals(1f, s[0]!!, 1e-6f)
    }

    @Test fun anUnreadSampleStaysNullAndNeverBecomesZero() {
        val s = ClockWave.series(listOf(1_000_000, null, 0, -5), 4_000_000)
        assertEquals(0.25f, s[0]!!, 1e-6f)
        assertNull(s[1])
        assertNull(s[2])
        assertNull(s[3])
    }

    @Test fun twoMeasuredSamplesAreTheMinimumForADrawing() {
        assertFalse(ClockWave.canDraw(listOf(1_000_000, null, null), 4_000_000))
        assertTrue(ClockWave.canDraw(listOf(1_000_000, 900_000, null), 4_000_000))
    }

    @Test fun theHeadIsTheLastMeasuredSampleNotTheLastSlot() {
        assertNull(ClockWave.head(listOf(null, null)))
        assertEquals(0.4f, ClockWave.head(listOf(0.2f, null, 0.4f, null))!!, 1e-6f)
    }

    @Test fun aGapInTheMiddleIsNotReadAsAFall() {
        // 0.8 ثم فراغ ثم 0.9: الاتجاه صاعد. ولو قارنّا بالجار المباشر لصار «هبوطًا».
        assertEquals(ClockTrend.RISING, ClockWave.trend(listOf(0.8f, null, 0.9f)))
        assertEquals(ClockTrend.FALLING, ClockWave.trend(listOf(0.9f, null, 0.8f)))
    }

    @Test fun aSmallChangeIsSteadyNotADirection() {
        assertEquals(ClockTrend.STEADY, ClockWave.trend(listOf(0.50f, 0.52f)))
        assertEquals(ClockTrend.RISING, ClockWave.trend(listOf(0.50f, 0.56f)))
    }

    @Test fun directionNeedsTwoMeasuredSamples() {
        assertEquals(ClockTrend.UNKNOWN, ClockWave.trend(emptyList()))
        assertEquals(ClockTrend.UNKNOWN, ClockWave.trend(listOf(0.5f)))
        assertEquals(ClockTrend.UNKNOWN, ClockWave.trend(listOf(null, 0.5f, null)))
    }
}
