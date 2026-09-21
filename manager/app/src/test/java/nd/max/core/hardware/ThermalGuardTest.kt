package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * الحارس الحراري: القرار الخالص الذي يقرّر سقف المقبض من ضغط المنصة.
 *
 * وهذه الحماية مقصودة بالمعنى الحرفي: الحارس **لا يرفع فوق ما طلبه المستخدم أبدًا**، ولا
 * يخفض تحت أدنى تردد يُعلنه الجهاز. أي خلل في هذين الحدّين يعني إمّا أداءً أعلى من طلب
 * المستخدم (خطر حراري) أو قيمة لا يستطيع العتاد حملها (مقبض ميّت).
 */
class ThermalGuardTest {

    private val ladder = listOf(180_000_000L, 300_000_000L, 490_000_000L, 680_000_000L, 754_000_000L)

    @Test
    fun `no throttling restores exactly what the user asked for`() {
        assertEquals(
            754_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 490_000_000L, ladder, ThermalGuard.Pressure.NONE),
        )
        assertEquals(
            754_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 490_000_000L, ladder, ThermalGuard.Pressure.LIGHT),
        )
    }

    @Test
    fun `throttling steps down exactly one advertised rung`() {
        assertEquals(
            680_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, null, ladder, ThermalGuard.Pressure.MODERATE),
        )
        assertEquals(
            "كل دورة درجة واحدة: الهبوط المفاجئ يقرأ كعطل",
            490_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 680_000_000L, ladder, ThermalGuard.Pressure.SEVERE),
        )
    }

    @Test
    fun `the lowest advertised rung is the floor and is never crossed`() {
        assertEquals(
            180_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 180_000_000L, ladder, ThermalGuard.Pressure.SHUTDOWN),
        )
    }

    @Test
    fun `an unknown platform status never moves the ceiling`() {
        assertEquals(
            "منصة لا تُجيب ليست منصة باردة: لا خفض على تخمين",
            754_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 300_000_000L, ladder, ThermalGuard.Pressure.UNKNOWN),
        )
    }

    @Test
    fun `rungs above the user request are never candidates`() {
        val high = listOf(300_000_000L, 600_000_000L, 900_000_000L)
        val next = ThermalGuard.nextCeiling(600_000_000L, null, high, ThermalGuard.Pressure.CRITICAL)
        assertEquals(300_000_000L, next)
    }

    @Test
    fun `a device that advertises nothing keeps the request untouched`() {
        assertEquals(
            "بلا سلّم مُعلن لا تخمين: القيمة تُعاد كما هي",
            754_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 490_000_000L, emptyList(), ThermalGuard.Pressure.SEVERE),
        )
    }

    @Test
    fun `a current ceiling outside the ladder is treated as the request`() {
        assertEquals(
            680_000_000L,
            ThermalGuard.nextCeiling(754_000_000L, 999_000_000L, ladder, ThermalGuard.Pressure.MODERATE),
        )
    }
}
