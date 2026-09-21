package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * منحنى الحرارة يُقاس هنا لا على الجهاز: القرار خالص، والعتاد طبقة تنفيذ.
 *
 * والاختبارات تحرس **حدود الأمان** لا الصيغة: لا رفع، ولا قيمة لا يحملها الجهاز، ولا سقف أعلى
 * من النسبة المطلوبة عند تقريب على سلّم خشن — وهي المواضع التي يتحوّل فيها Cleaner ميزة إلى عطل.
 */
class ThermalCurveTest {

    // سلّم واقعي من جهاز (١٢ درجة، أعلاها ٧٥٤ ميجاهرتز) — نفس عائلة الأرقام التي في السجلات.
    private val gpuLadder = listOf(180_000_000L, 267_000_000L, 355_000_000L, 430_000_000L,
        500_000_000L, 565_000_000L, 650_000_000L, 754_000_000L)

    private val cpuLadder = listOf(300_000L, 500_000L, 700_000L, 900_000L, 1_100_000L,
        1_300_000L, 1_500_000L, 1_800_000L)

    @Test
    fun `a percentage below one hundred caps the live ceiling`() {
        // ٧٠٪ من ٧٥٤ = ٥٢٧٫٨ ⇒ أقرب درجة مُعلنة **تحت** النسبة هي ٥٠٠.
        assertEquals(500_000_000L, ThermalCurve.capMaxHz(754_000_000L, 70, gpuLadder))
    }

    @Test
    fun `rounding is always downward so the requested percentage is never exceeded`() {
        // ٩٠٪ من ٧٥٤ = ٦٧٨٫٦ ⇒ ٦٥٠ لا ٧٥٤. والتقريب إلى أعلى كان يكتب ٧٥٤ أي «بلا سقف» بينما
        // المستخدم طلب ٩٠٪.
        val cap = ThermalCurve.capMaxHz(754_000_000L, 90, gpuLadder)
        assertEquals(650_000_000L, cap)
        assertTrue(cap <= (754_000_000L * 90) / 100L)
    }

    @Test
    fun `one hundred percent is no ceiling at all rather than a full ceiling`() {
        assertNull("performance تعني «بلا سقف» لا «سقف كامل»", ThermalCurve.cappingPercent(100))
        assertEquals(754_000_000L, ThermalCurve.capMaxHz(754_000_000L, 100, gpuLadder))
    }

    @Test
    fun `a ladder with nothing below the target writes no cap`() {
        // أدنى درجة مُعلنة ٥٠٠ وأعلى الحيّ ٥٢٠: كل درجات السلّم أعلى من السقف المطلوب، فالكتابة
        // كانت ستُعطي ٥٠٠ وهي ٩٦٪ من الحيّ بينما الطلب ٦٠٪. عدم الكتابة أصدق.
        assertEquals(520_000L, ThermalCurve.capMaxHz(520_000L, 60, listOf(500_000L, 520_000L)))
    }

    @Test
    fun `an unreadable ceiling is returned untouched instead of capped from a guess`() {
        assertEquals(0L, ThermalCurve.capMaxHz(0L, 50, cpuLadder))
        assertEquals(-1L, ThermalCurve.capMaxHz(-1L, 50, cpuLadder))
    }

    @Test
    fun `a policy range keeps its floor and moves only the ceiling`() {
        assertEquals("300000:900000", ThermalCurve.rangeFor(300_000L, 900_000L))
        assertEquals(":900000", ThermalCurve.rangeFor(null, 900_000L))
    }

    @Test
    fun `the realized percentage is reported so intent and result cannot be confused`() {
        // طُلب ٧٠ ووقع ٦٦: بدون هذا الرقم يُقرأ السجل كأن الطلب لم يُنفَّذ.
        assertEquals(66, ThermalCurve.realizedPercent(754_000_000L, 500_000_000L))
        assertNull("السقف الأعلى من المرجع ليس تنفيذًا لنسبة", ThermalCurve.realizedPercent(754_000_000L, 800_000_000L))
    }

    @Test
    fun `full capability means the device maximum, not the ceiling it happens to allow now`() {
        // القياس الذي رفضه صاحب الجهاز: أعلى درجة مُعلنة ١٣٠٠، وسياسة الجهاز الحالية تسمح بـ٧٥٤،
        // وكانت نسبة ٨٥٪ تُحسب من ٧٥٤ فيصير الطلب ٦٢٤ — أي أدنى من جهاز غير ممسوس.
        assertTrue(ThermalCurve.atFullCapability(100))
        assertTrue(ThermalCurve.atFullCapability(101))

        // وما دون ١٠٠٪ يسقّف فعلاً، فتبقى نسبته من السقف الحيّ (معنى التبريد).
        assertFalse(ThermalCurve.atFullCapability(99))
        assertEquals(99, ThermalCurve.cappingPercent(99))
        // ٦٥٪ من السقف الحيّ ٧٥٤ ⇒ ٤٣٠ (أقرب درجة مُعلنة تحت النسبة على هذا السلّم).
        assertEquals(430_000_000L, ThermalCurve.capMaxHz(754_000_000L, 65, gpuLadder))
    }

    @Test
    fun `the curve never touches a policy the user configured by hand`() {
        // قرار «من ضبطها بنفسه» ليس في هذه الدالة بل عند المستدعي — وهذا الاختبار يثبّت الشرط
        // المقروء: كل ما يعطيه المنحنى هو نسبة، ولا شيء غيرها.
        val percent = ThermalCurve.cappingPercent(65)
        assertEquals(65, percent)
        assertNull(ThermalCurve.cappingPercent(0))
        assertNull(ThermalCurve.cappingPercent(101))
    }
}
