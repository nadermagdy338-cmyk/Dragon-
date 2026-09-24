package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد قياس ضغط الذاكرة (XR-06): التوقف لا الامتلاء.
 *
 * النص المُختبَر هو الشكل الحقيقي لملف `/proc/pressure/memory`. وخطورة هذه
 * الوحدة أنها تُنتج **حكمًا يمنع قرارًا**: إن قرأت `some` مكان `full`، أو
 * اعتبرت الغياب صفرًا، أو مرّرت رقمًا مشوّهًا، لتحوّل المنع إلى قياس كاذب —
 * أو توقّف المحرك على نواة قديمة بلا سبب.
 */
class MemoryStallTest {

    private val psi = """
        some avg10=1.25 avg60=0.40 avg300=0.10 total=123456
        full avg10=12.50 avg60=4.00 avg300=1.00 total=98765
    """.trimIndent()

    // ── التحليل: الأنواع والنوافذ ────────────────────────────────

    @Test
    fun `a real psi file yields both series for the requested window`() {
        val sample = MemoryStall.parse(psi)

        assertTrue(sample.measured)
        assertEquals(MemoryStall.Availability.MEASURED, sample.availability)
        assertEquals(1.25f, sample.somePercent!!, 0.0001f)
        assertEquals(12.50f, sample.fullPercent!!, 0.0001f)
        assertEquals(MemoryStall.DEFAULT_WINDOW_SECONDS, sample.windowSeconds)
    }

    @Test
    fun `fractions are derived from the percentages instead of being guessed`() {
        val sample = MemoryStall.parse(psi)

        assertEquals(0.0125f, sample.someFraction!!, 0.00001f)
        assertEquals(0.125f, sample.fullFraction!!, 0.00001f)
    }

    @Test
    fun `the requested window is honoured not assumed`() {
        val sample = MemoryStall.parse(psi, windowSeconds = 300)

        assertEquals(300, sample.windowSeconds)
        assertEquals(0.10f, sample.somePercent!!, 0.0001f)
        assertEquals(1.00f, sample.fullPercent!!, 0.0001f)
    }

    @Test
    fun `a window the kernel does not export is unsupported rather than zero`() {
        val sample = MemoryStall.parse(psi, windowSeconds = 5)

        assertFalse(sample.measured)
        assertNull(sample.fullPercent)
        assertNull(sample.fullFraction)
    }

    @Test
    fun `order, padding and extra fields do not break the parse`() {
        val shuffled = "full total=5   avg10=7.5  avg60=1.0\n\nsome\tavg10=2.0 avg60=0.5 total=9"

        val sample = MemoryStall.parse(shuffled)

        assertEquals(7.5f, sample.fullPercent!!, 0.0001f)
        assertEquals(2.0f, sample.somePercent!!, 0.0001f)
    }

    // ── الرفض: لا رقم جزئي يُعرض كقياس ────────────────────────────

    @Test
    fun `a kernel that exports only some is unsupported, because some is not full`() {
        val someOnly = "some avg10=99.99 avg60=50.00 avg300=20.00 total=1"

        val sample = MemoryStall.parse(someOnly)

        assertFalse("لا يُعرض انتظار عادي كخنق كامل", sample.measured)
        assertNull(sample.fullPercent)
    }

    @Test
    fun `a null or blank file is unsupported not a zero measurement`() {
        assertFalse(MemoryStall.parse(null).measured)
        assertFalse(MemoryStall.parse("").measured)
        assertFalse(MemoryStall.parse("   \n  ").measured)
    }

    @Test
    fun `garbage numbers reject the sample instead of reaching a decision`() {
        assertFalse(MemoryStall.parse("some avg10=abc\nfull avg10=1.0").measured)
        assertFalse(MemoryStall.parse("some avg10=1.0\nfull avg10=--").measured)
        assertFalse(MemoryStall.parse("some avg10=1.0\nfull avg10=NaN").measured)
        assertFalse(MemoryStall.parse("some avg10=1.0\nfull avg10=Infinity").measured)
    }

    @Test
    fun `an out of range number is clamped to the physical range, never kept as impossible`() {
        val sample = MemoryStall.parse("some avg10=1.0\nfull avg10=250.0")

        assertEquals(100f, sample.fullPercent!!, 0.0001f)
        assertEquals(1f, sample.fullFraction!!, 0.0001f)
    }

    @Test
    fun `unrelated lines never contribute a series`() {
        val sample = MemoryStall.parse("cpu avg10=5.0\ntotal=3\nsome avg10=1.0\nfull avg10=2.0")

        assertEquals(1.0f, sample.somePercent!!, 0.0001f)
        assertEquals(2.0f, sample.fullPercent!!, 0.0001f)
    }

    // ── الحكم: الجهل ليس خنقًا ───────────────────────────────────

    @Test
    fun `an unsupported sample never blocks anything`() {
        assertFalse(MemoryStall.isThrashing(MemoryStall.UNSUPPORTED.fullFraction))
        assertFalse(MemoryStall.isThrashing(null))
        assertFalse(MemoryStall.isThrashing(MemoryStall.parse("garbage").fullFraction))
    }

    @Test
    fun `the stall threshold is inclusive at the declared boundary`() {
        assertFalse(MemoryStall.isThrashing(MemoryStall.STALL_FRACTION - 0.0001f))
        assertTrue(MemoryStall.isThrashing(MemoryStall.STALL_FRACTION))
        assertTrue(MemoryStall.isThrashing(1f))
    }

    @Test
    fun `ordinary reclaim below the threshold is not a stall`() {
        assertFalse(MemoryStall.isThrashing(0f))
        assertFalse(MemoryStall.isThrashing(0.05f))
    }

    @Test
    fun `the unsupported singleton is not a measured zero`() {
        assertFalse(MemoryStall.UNSUPPORTED.measured)
        assertNull(MemoryStall.UNSUPPORTED.somePercent)
        assertNull(MemoryStall.UNSUPPORTED.fullPercent)
        assertNull(MemoryStall.UNSUPPORTED.fullFraction)
        assertNull(MemoryStall.UNSUPPORTED.someFraction)
    }
}
