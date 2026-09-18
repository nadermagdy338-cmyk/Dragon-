package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد ميزانية الحرارة — الرقم الوحيد في Max AI الذي يقيس **زمنًا**.
 *
 * كل خطأ هنا يظهر للمستخدم كرقم معقول وكاذب: «جهازك يصمد ٢٠ دقيقة» وقد تكون
 * الجلسة لم تبدأ بعد، أو الرقم محسوبًا من آخر خنق لا أول خنق، أو صمودًا حين
 * أُطفئت الشاشة فتوقف الطلب لا العتاد. لذلك تُختبَر كل قاعدة قبول بقيمة صريحة.
 */
class MaxAiThermalBudgetTest {

    private val minute = 60_000L

    @Test
    fun `nothing is claimed before any session exists`() {
        val status = MaxAiThermalBudget.statusOf(MaxAiThermalBudget.State(), nowMs = minute)

        assertFalse(status.sessionOpen)
        assertEquals(0L, status.sessionElapsedMs)
        assertNull(status.lastBudgetMs)
        assertNull(status.lastCleanSessionMs)
        assertEquals(0, status.sessionsMeasured)
    }

    @Test
    fun `a demanding session opens and its elapsed time is reported while it runs`() {
        val opened = MaxAiThermalBudget.observe(
            previous = MaxAiThermalBudget.State(),
            nowMs = minute,
            demanding = true,
            screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        val status = MaxAiThermalBudget.statusOf(opened, nowMs = minute + 5 * minute)

        assertTrue(status.sessionOpen)
        assertEquals(5 * minute, status.sessionElapsedMs)
        assertNull("الجلسة ما زالت جارية بلا خنق: لا رقم ميزانية بعد", status.lastBudgetMs)
    }

    @Test
    fun `the budget is the time to the FIRST cap, not to the last one`() {
        val opened = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        val capped = MaxAiThermalBudget.observe(
            opened, nowMs = 12 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.ENGAGED,
        )
        // تدخل ثانٍ بعد وقت طويل: يجب ألا يُمدّد الرقم.
        val cappedAgain = MaxAiThermalBudget.observe(
            capped, nowMs = 40 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.CRITICAL,
        )

        assertEquals(12 * minute, cappedAgain.lastBudgetMs)
        assertEquals(1, cappedAgain.sessionsMeasured)
    }

    @Test
    fun `a session that ends by closing the game without any cap is recorded as clean`() {
        val opened = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        val closed = MaxAiThermalBudget.observe(
            opened, nowMs = 25 * minute, demanding = false, screenOn = true,
            level = SafetyLevel.NORMAL,
        )

        assertEquals(25 * minute, closed.lastCleanSessionMs)
        assertNull(closed.lastBudgetMs)
        assertFalse(closed.sessionOpen)
    }

    @Test
    fun `turning the screen off mid load never counts as endurance`() {
        val opened = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        val closed = MaxAiThermalBudget.observe(
            opened, nowMs = 25 * minute, demanding = true, screenOn = false,
            level = SafetyLevel.NORMAL,
        )

        assertNull("لا نعرف: توقف الطلب أم صمد العتاد — فلا يُدّعى صمود", closed.lastCleanSessionMs)
        assertNull(closed.lastBudgetMs)
        assertFalse(closed.sessionOpen)
    }

    @Test
    fun `a cap already measured stays valid when the screen goes off later`() {
        val opened = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        val capped = MaxAiThermalBudget.observe(
            opened, nowMs = 8 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.ENGAGED,
        )
        val closed = MaxAiThermalBudget.observe(
            capped, nowMs = 30 * minute, demanding = true, screenOn = false,
            level = SafetyLevel.NORMAL,
        )

        assertEquals("الخنق وقع فعلًا والقياس صحيح", 8 * minute, closed.lastBudgetMs)
        assertNull(closed.lastCleanSessionMs)
    }

    @Test
    fun `a blink of a session is not a thermal budget`() {
        val opened = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        val closed = MaxAiThermalBudget.observe(
            opened, nowMs = MaxAiThermalBudget.MIN_SESSION_MS - 1L, demanding = false,
            screenOn = true, level = SafetyLevel.NORMAL,
        )

        assertNull(closed.lastCleanSessionMs)
    }

    @Test
    fun `sessions are counted only when a cap was measured`() {
        var state = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        state = MaxAiThermalBudget.observe(
            state, nowMs = 20 * minute, demanding = false, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        assertEquals("جلسة نظيفة ليست تدخلًا مقيسًا", 0, state.sessionsMeasured)

        state = MaxAiThermalBudget.observe(
            state, nowMs = 21 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        state = MaxAiThermalBudget.observe(
            state, nowMs = 26 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.CRITICAL,
        )
        assertEquals(1, state.sessionsMeasured)
        assertEquals(5 * minute, state.lastBudgetMs)
    }

    @Test
    fun `a second session does not inherit the first session clock`() {
        var state = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        state = MaxAiThermalBudget.observe(
            state, nowMs = 3 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.ENGAGED,
        )
        // الجلسة تُغلق ثم تُفتح مرة أخرى بعد ساعة.
        state = MaxAiThermalBudget.observe(
            state, nowMs = 4 * minute, demanding = false, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        state = MaxAiThermalBudget.observe(
            state, nowMs = 64 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        state = MaxAiThermalBudget.observe(
            state, nowMs = 70 * minute, demanding = true, screenOn = true,
            level = SafetyLevel.ENGAGED,
        )

        assertEquals("الميزانية تُقاس من بدء جلسة جديدة", 6 * minute, state.lastBudgetMs)
        assertEquals(2, state.sessionsMeasured)
    }

    @Test
    fun `nothing is recorded while the safety level is normal`() {
        var state = MaxAiThermalBudget.observe(
            MaxAiThermalBudget.State(), nowMs = 0L, demanding = true, screenOn = true,
            level = SafetyLevel.NORMAL,
        )
        repeat(50) { index ->
            state = MaxAiThermalBudget.observe(
                state, nowMs = index * minute, demanding = true, screenOn = true,
                level = SafetyLevel.NORMAL,
            )
        }

        assertNull(state.lastBudgetMs)
        assertEquals(0, state.sessionsMeasured)
        assertTrue(state.sessionOpen)
    }
}
