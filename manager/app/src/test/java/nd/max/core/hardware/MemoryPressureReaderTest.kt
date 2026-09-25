/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.core.maxai.MemoryStall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد سياسة استقصاء PSI.
 *
 * هذه السياسة هي التي تمنع أن يصبح «قياس لا يُنتج رقمًا» حملًا دائمًا: على نواة
 * بلا PSI، كل استقصاء يجرّب قشرة في `RootFileAccess`، فاستقصاء كل ثانية يعني
 * عملية قشرة كل ثانية إلى الأبد. والقاعدة المضادة ليست «لا نقرأ أبدًا» بل
 * **لا نُعيد سؤال نواة أعلنت عدم الدعم إلا بعد مهلة**.
 *
 * الاختبار نقيّ: لا يلمس `/proc` ولا الجذر، ويُمرَّر فيه الزمن صراحةً.
 */
class MemoryPressureReaderTest {

    private val measured = MemoryStall.parse("some avg10=1.0\nfull avg10=2.0")
    private val unsupported = MemoryStall.UNSUPPORTED

    @Test
    fun `a measured sample is always re-read so the numbers stay fresh`() {
        assertTrue(MemoryPressureReader.shouldProbe(measured, lastProbeAtMs = 1_000L, nowMs = 1_001L))
        assertTrue(MemoryPressureReader.shouldProbe(measured, lastProbeAtMs = 1_000L, nowMs = 999_999L))
    }

    @Test
    fun `an unsupported kernel is not asked again inside the retry window`() {
        assertFalse(
            MemoryPressureReader.shouldProbe(
                unsupported,
                lastProbeAtMs = 0L,
                nowMs = MemoryPressureReader.RETRY_AFTER_MS - 1L,
            ),
        )
    }

    @Test
    fun `the retry window boundary re-opens the question exactly once`() {
        assertTrue(
            MemoryPressureReader.shouldProbe(
                unsupported,
                lastProbeAtMs = 0L,
                nowMs = MemoryPressureReader.RETRY_AFTER_MS,
            ),
        )
    }

    @Test
    fun `a reader that never probed asks immediately, even at a zero clock`() {
        // الصفر لحظة صحيحة (بدء التشغيل) ولا يجوز أن تُمنع أول قراءة (ADR-33).
        assertTrue(MemoryPressureReader.shouldProbe(unsupported, lastProbeAtMs = null, nowMs = 0L))
        assertTrue(MemoryPressureReader.shouldProbe(unsupported, lastProbeAtMs = null, nowMs = 9_999L))
    }

    @Test
    fun `a probe recorded at a zero clock still starts the retry window`() {
        assertFalse(MemoryPressureReader.shouldProbe(unsupported, lastProbeAtMs = 0L, nowMs = 1L))
    }

    @Test
    fun `a backwards clock keeps serving the cached answer instead of thrashing the shell`() {
        // انحدار الزمن (تعديل ساعة/إعادة تشغيل عدّاد): نخدم الجواب المحفوظ،
        // ثم تعودالمحاولة حين يعبر الزمن المهلة من جديد — لا جمود ولا استقصاء كل ثانية.
        assertFalse(
            MemoryPressureReader.shouldProbe(unsupported, lastProbeAtMs = 5_000L, nowMs = 1_000L),
        )
        assertTrue(
            MemoryPressureReader.shouldProbe(
                unsupported,
                lastProbeAtMs = 5_000L,
                nowMs = 5_000L + MemoryPressureReader.RETRY_AFTER_MS,
            ),
        )
    }
}
