/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هندسة لوحة «المكعّب» — تُقاس بلا جهاز لأن [CubeLayoutMath] و[CubeGeometry] رياضيات خالصة.
 *
 * ووُجدت أولًا لتثبيت **عطب مقيس**: `meterH = room.coerceIn(64f, u * 24f)` كان يرمي
 * `IllegalArgumentException: Cannot coerce value to an empty range` عند `u = 2.4`
 * (أي عرض أقل من ٥٠٧dp تقريبًا ⇒ `u * 24 = 57.6 < 64`) — فينهار **أول رسم** على أكثر الهواتف.
 * فأول ما يُقاس هنا ليس شكلًا ولا ترتيبًا بل **عدم الرمي** على شبكة أحجام كاملة.
 *
 * والقاعدة الباقية هي التي وُعد بها الملف نفسه: «الأعمدة تُوزَّع بعرض مضمون لا يتقاطع» —
 * فتُقاس المسافات بالأرقام (أضيق ما قِيس عبر الشبكة: ٦٫٤dp بين المقياسين، و٦dp بين العمود
 * الأيسر ومقياس CPU حين لا تكون الشاشة ضيّقة، و١٢dp بين الشبكة والحافة).
 */
class CubeLayoutTest {

    private val widths: List<Float> = (320..1400 step 60).map { it.toFloat() }
    private val heights: List<Float> = (240..2400 step 80).map { it.toFloat() }

    private inline fun eachLayout(
        body: (w: Float, h: Float, left: Boolean, wide: Boolean, layout: CubeLayout) -> Unit,
    ): Int {
        var cases = 0
        for (w in widths) {
            for (h in heights) {
                for (left in listOf(true, false)) {
                    for (wide in listOf(true, false)) {
                        cases++
                        body(w, h, left, wide, CubeLayoutMath.of(w, h, left, wide))
                    }
                }
            }
        }
        return cases
    }

    @Test
    fun `layout never throws and stays ordered across the full size sweep`() {
        val cases = eachLayout { w, h, left, wide, l ->
            val where = "${w.toInt()}x${h.toInt()} left=$left wide=$wide"
            val clampedW = max(w, 320f)

            // العطب المدفون الذي وُجد بهذا الاختبار: `u = 2.4` يجعل سقف المقياس ٥٧٫٦ تحت أرضيته ٦٤.
            assertTrue("$where: meterH=${l.meterH} u=${l.u}", l.meterH >= 64f && l.meterH <= max(64f, l.u * 24f))
            assertTrue("$where: u=${l.u}", l.u in 2.4f..8f)
            assertTrue("$where: topPad=${l.topPad} divider=${l.dividerY} top=${l.bodyTop} bottom=${l.bodyBottom}",
                l.topPad < l.dividerY && l.dividerY < l.bodyTop && l.bodyTop < l.bodyBottom)
            assertTrue("$where: bodyHeight=${l.bodyHeight}", l.bodyHeight > 0f)
            assertTrue("$where: gridX=${l.gridX} gridW=${l.gridW}", l.gridX >= 0f && l.gridX + l.gridW <= clampedW)
            assertTrue("$where: compact=${l.compact}", l.compact == CockpitModel.isCompact(clampedW))
        }
        assertTrue("the sweep must actually run: $cases", cases >= 1000)
    }

    @Test
    fun `cpu and gpu gauges never intersect`() {
        var narrowest = Float.MAX_VALUE
        eachLayout { w, h, left, wide, l ->
            val gap = l.gpuRange.start - l.cpuRange.endInclusive
            narrowest = minOf(narrowest, gap)
            assertTrue("gauges intersect at ${w.toInt()}x${h.toInt()} left=$left wide=$wide: gap=$gap",
                gap > 0f)
        }
        // مُقاس عبر الشبكة: أضيق فراغ ٦٫٤dp (عند ٣٢٠×٢٤٠) — فالحدّ هنا سقفُ انحدار لا وصفُ اليوم.
        assertTrue("narrowest=$narrowest", narrowest >= 4f)
    }

    @Test
    fun `the open left column never covers the cpu gauge unless the screen is compact`() {
        eachLayout { w, h, left, wide, l ->
            if (left && !l.compact) {
                assertTrue(
                    "left column covers cpu gauge at ${w.toInt()}x${h.toInt()} wide=$wide: leftRight=${l.leftRight} cpuStart=${l.cpuRange.start}",
                    l.cpuRange.start >= l.leftRight,
                )
            }
        }
    }

    @Test
    fun `bar geometry is one path for drawing and for testing`() {
        val w = 800f
        val h = 600f
        val x0 = CubeGeometry.TOP_X * w
        val x1 = x0 + CubeGeometry.LEAN_OUT * h
        val x2 = x1 - CubeGeometry.LEAN_BACK * h
        val tipY = CubeGeometry.TIP_Y * h

        assertEquals(x0, CubeGeometry.centerX(w, h, 0f), 0.01f)
        assertEquals(x1, CubeGeometry.centerX(w, h, tipY), 0.01f)
        assertEquals(x2, CubeGeometry.centerX(w, h, h), 0.01f)
        // خارج المدى يُقصّ لا يُسقط — نفس قاعدة `coerceIn` في الرسم.
        assertEquals(x2, CubeGeometry.centerX(w, h, h + 500f), 0.01f)
        assertEquals(x0, CubeGeometry.centerX(w, h, -80f), 0.01f)

        val y = 300f
        assertEquals(
            CubeGeometry.centerX(w, h, y) - CubeGeometry.HALF * h,
            CubeGeometry.leftEdge(w, h, y),
            0.01f,
        )

        // الميل نحو الخارج حتى القمّة: المركز لا يعود للداخل قبلها.
        var previous = CubeGeometry.centerX(w, h, 0f)
        var step = 0f
        while (step <= tipY) {
            val x = CubeGeometry.centerX(w, h, step)
            assertTrue("center went backwards at y=$step: $x < $previous", x >= previous - 0.01f)
            previous = x
            step += 5f
        }
    }
}
