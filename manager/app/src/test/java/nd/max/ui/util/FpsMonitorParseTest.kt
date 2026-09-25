package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تحليل قراءات `/proc` — الجزء النقيّ من `FpsMonitorUtil`، مُختبَر بعد استخراجه.
 *
 * ولماذا استُخرج: كان التحليل **داخل** دالة تقرأ صدفة جذر، فلم يكن قابلًا للاختبار أصلًا،
 * وخطأ فيه (حقل خاطئ، أو خمول منسوب) لا يظهر إلا كرقم حمل خاطئ على الشاشة. الآن الصيغة
 * مُثبَّتة على سطور حقيقية من `/proc/stat`، والقراءة وحدها هي ما يحتاج جهازًا.
 */
class FpsMonitorParseTest {

    @Test
    fun `a real stat line yields the established total and idle`() {
        val sample = FpsMonitorUtil.parseStatSample("cpu  1234 56 789 45678 90 12 34 5 0 0")
        assertEquals(45_768L, sample!!.idle)          // idle + iowait
        assertEquals(47_898L, sample.total)           // + user nice system irq softirq steal
    }

    @Test
    fun `iowait counts as idle — waiting on IO is not CPU work`() {
        val withIowait = FpsMonitorUtil.parseStatSample("cpu 10 0 10 100 500")!!
        assertEquals(600L, withIowait.idle)   // 100 idle + 500 iowait
        assertEquals(620L, withIowait.total)  // + user 10 · system 10
    }

    @Test
    fun `malformed lines are unknown, never a fabricated zero load`() {
        assertNull(FpsMonitorUtil.parseStatSample("cpu"))
        assertNull(FpsMonitorUtil.parseStatSample("cpu x 1 2 3"))
        assertNull(FpsMonitorUtil.parseStatSample("cpu 1 2 3"))
    }

    @Test
    fun `load percent is the delta ratio, clamped`() {
        val previous = FpsMonitorUtil.CpuSample(total = 100, idle = 40)
        assertEquals(60, FpsMonitorUtil.loadPercent(previous, FpsMonitorUtil.CpuSample(200, 80)))
        assertEquals(0, FpsMonitorUtil.loadPercent(previous, FpsMonitorUtil.CpuSample(200, 140)))
        assertEquals(0, FpsMonitorUtil.loadPercent(previous, FpsMonitorUtil.CpuSample(100, 40)))
        assertEquals(0, FpsMonitorUtil.loadPercent(previous, FpsMonitorUtil.CpuSample(50, 10)))
        assertEquals(0, FpsMonitorUtil.loadPercent(previous, FpsMonitorUtil.CpuSample(200, 300)))
    }

    @Test
    fun `second field replaces awk without spawning a shell`() {
        assertEquals("120.5", FpsMonitorUtil.secondField("fps 120.5"))
        assertEquals("fps", FpsMonitorUtil.secondField("120.5 fps"))
        assertEquals("b", FpsMonitorUtil.secondField("  a   b  "))
        assertNull(FpsMonitorUtil.secondField("single"))
        assertNull(FpsMonitorUtil.secondField(""))
    }
}
