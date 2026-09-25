/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد الوتيرة: متى يُسمح بدورة قرار إضافية.
 *
 * هذه السياسة تحكم شيئًا واحدًا لكنه ثمين: أن يلاحظ Max AI تغيّر السياق قبل
 * ثلاثين ثانية، دون أن يتحوّل ذلك إلى حلقة استطلاع ثانية تسخّن الجهاز. أي
 * خلل هنا يظهر عند المستخدم كإمّا تأخير محسوس، أو كتحكم يتأرجح؛ لذا تُحرَس
 * بقيم صريحة لا بتقدير.
 *
 * الدالة نقية (بلا Android وبلا حالة محفوظة) فالاختبار حاسم: نفس المدخلات
 * تعطي نفس القرار دائمًا.
 */
class MaxAiCadenceTest {

    private fun signal(
        screenOn: Boolean = true,
        appIntent: Float = 0.5f,
        level: SafetyLevel = SafetyLevel.NORMAL,
    ) = MaxAiCadence.Signal(screenOn = screenOn, appIntent = appIntent, safetyLevel = level)

    @Test
    fun `nothing wakes the engine before the first signal is known`() {
        val decision = MaxAiCadence.decide(
            previous = null,
            current = signal(),
            nowMs = 1_000_000L,
            lastCycleAtMs = 0L,
        )

        assertFalse(decision.wake)
        assertNull(decision.reason)
        assertEquals(0L, decision.waitMs)
    }

    @Test
    fun `an unchanged signal never triggers an extra cycle`() {
        val current = signal()
        val decision = MaxAiCadence.decide(
            previous = current,
            current = current,
            nowMs = 5_000_000L,
            lastCycleAtMs = 0L,
        )

        assertFalse(decision.wake)
        assertNull(decision.reason)
    }

    @Test
    fun `a screen flip between on and off re-plans immediately`() {
        // آخر دورة وقعت قبل ١٩ ثانية: تجاوزت المهلة، فالاستيقاظ مسموح.
        val off = MaxAiCadence.decide(
            previous = signal(screenOn = true),
            current = signal(screenOn = false),
            nowMs = 20_000L,
            lastCycleAtMs = 1_000L,
        )
        val on = MaxAiCadence.decide(
            previous = signal(screenOn = false),
            current = signal(screenOn = true),
            nowMs = 20_000L,
            lastCycleAtMs = 1_000L,
        )

        assertTrue(off.wake)
        assertTrue(on.wake)
        assertEquals(MaxAiCadence.Reason.SCREEN, off.reason)
        assertEquals(MaxAiCadence.Reason.SCREEN, on.reason)
    }

    @Test
    fun `entering and leaving a heavy usage context re-plans`() {
        val decision = MaxAiCadence.decide(
            previous = signal(appIntent = 0.5f),
            current = signal(appIntent = 1f),
            nowMs = 60_000L,
            lastCycleAtMs = 30_000L,
        )

        assertTrue(decision.wake)
        assertEquals(MaxAiCadence.Reason.USAGE, decision.reason)
    }

    @Test
    fun `safety outranks the other changes because it moves decision authority`() {
        val reason = MaxAiCadence.reasonFor(
            previous = signal(screenOn = true, appIntent = 0.5f, level = SafetyLevel.NORMAL),
            current = signal(screenOn = false, appIntent = 1f, level = SafetyLevel.ENGAGED),
        )

        assertEquals(MaxAiCadence.Reason.SAFETY, reason)
    }

    @Test
    fun `a qualifying change inside the floor is deferred, kept and counted down`() {
        val previous = signal(screenOn = true)
        val current = signal(screenOn = false)

        val early = MaxAiCadence.decide(
            previous = previous,
            current = current,
            nowMs = 100_000L,
            lastCycleAtMs = 95_000L,
        )
        val later = MaxAiCadence.decide(
            previous = previous,
            current = current,
            nowMs = 104_000L,
            lastCycleAtMs = 95_000L,
        )
        val due = MaxAiCadence.decide(
            previous = previous,
            current = current,
            nowMs = 105_000L,
            lastCycleAtMs = 95_000L,
        )

        assertFalse(early.wake)
        assertEquals("السبب لا يُنسى أثناء الانتظار", MaxAiCadence.Reason.SCREEN, early.reason)
        assertEquals(5_000L, early.waitMs)
        assertFalse(later.wake)
        assertEquals("الوقت المتبقي يُنقص لا يُصفَّر", 1_000L, later.waitMs)
        assertTrue("عند انتهاء المهلة يقع الاستيقاظ", due.wake)
        assertEquals(0L, due.waitMs)
    }

    @Test
    fun `the first evaluation after startup is never blocked by the floor`() {
        val decision = MaxAiCadence.decide(
            previous = signal(screenOn = true),
            current = signal(screenOn = false, appIntent = 1f),
            nowMs = 1_000L,
            lastCycleAtMs = 0L,
        )

        assertTrue(decision.wake)
        assertEquals("مهلة لاحقة لا تُحجز قبل أول دورة", 0L, decision.waitMs)
    }

    @Test
    fun `the declared floor matches the window the engine needs to measure a step`() {
        // نافذة استجابة النظام في المحرك عشر ثوان؛ وإعادة التخطيط لا تُسبقها
        // كي لا يُبنى القرار التالي على أثر لم يظهر بعد.
        assertEquals(10_000L, MaxAiCadence.MIN_ADAPTIVE_INTERVAL_MS)
    }
}
