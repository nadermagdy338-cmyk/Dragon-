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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AR-32` — الحكم على فجوة الإطار.
 *
 * هذه الاختبارات تحرس أهمّ ما في البند: **ألا ندّعي انسدادًا لم نقسه**. ‏[StallMath] خالصة
 * بلا اعتماد على أندرويد، لذلك تُختبر هنا بلا جهاز ولا محاكي.
 */
class MainThreadStallDetectorTest {

    private val ms = 1_000_000L

    @Test
    fun `normal frame gap is not a stall`() {
        val verdict = StallMath.classify(previousFrameNs = 1_000 * ms, frameNs = 1_016 * ms)
        assertEquals(StallMath.Verdict.None, verdict)
    }

    @Test
    fun `first frame is never judged`() {
        // لا إطار سابق (0) ⇒ لا مقارنة ممكنة ⇒ لا ادّعاء.
        assertEquals(StallMath.Verdict.None, StallMath.classify(0L, 5_000 * ms))
        assertEquals(StallMath.Verdict.None, StallMath.classify(-1L, 5_000 * ms))
    }

    @Test
    fun `gap at the threshold is a stall`() {
        val verdict = StallMath.classify(
            previousFrameNs = 0L + 1,
            frameNs = 1 + StallMath.DEFAULT_THRESHOLD_MS * ms,
        )
        assertTrue("الحدّ نفسه يُحتسب انسدادًا", verdict is StallMath.Verdict.Stall)
        assertEquals(StallMath.DEFAULT_THRESHOLD_MS, (verdict as StallMath.Verdict.Stall).durationMs)
    }

    @Test
    fun `just under the threshold is not a stall`() {
        val verdict = StallMath.classify(
            previousFrameNs = 1L,
            frameNs = 1L + (StallMath.DEFAULT_THRESHOLD_MS - 1) * ms,
        )
        assertEquals(StallMath.Verdict.None, verdict)
    }

    @Test
    fun `measured stall reports the real duration`() {
        val verdict = StallMath.classify(previousFrameNs = 1L, frameNs = 1L + 2_500 * ms)
        assertEquals(StallMath.Verdict.Stall(2_500L), verdict)
    }

    @Test
    fun `gap too long to be credible is ignored not claimed`() {
        // ٣٠ ثانية = الحلقة كانت متوقّفة (شاشة مطفأة/خلفية) — و«تجمّد ٣٠ ثانية» ادّعاء لا نستطيع إثباته.
        val verdict = StallMath.classify(previousFrameNs = 1L, frameNs = 1L + 30_000 * ms)
        assertTrue(verdict is StallMath.Verdict.Ignored)
        assertEquals(30_000L, (verdict as StallMath.Verdict.Ignored).gapMs)
    }

    @Test
    fun `exactly at the credibility ceiling is still a stall`() {
        val verdict = StallMath.classify(
            previousFrameNs = 1L,
            frameNs = 1L + StallMath.MAX_CREDIBLE_MS * ms,
        )
        assertEquals(StallMath.Verdict.Stall(StallMath.MAX_CREDIBLE_MS), verdict)
    }

    @Test
    fun `a backwards clock is not a stall`() {
        assertEquals(StallMath.Verdict.None, StallMath.classify(9_000 * ms, 1_000 * ms))
        assertEquals(StallMath.Verdict.None, StallMath.classify(9_000 * ms, 9_000 * ms))
    }
}
