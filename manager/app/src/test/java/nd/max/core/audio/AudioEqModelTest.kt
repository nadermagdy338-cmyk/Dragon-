/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نموذج المعادل — **مقيسٌ على JVM بلا جهاز ولا `android.jar`**.
 *
 * و**ما يُقاس هنا بالضبط:** أنّ المدى يأتي من المنصّة ولا يُخترع، وأنّ الغياب يبقى غيابًا (`null`) لا
 * صفرًا، وأنّ السحب لا يخرج عن المُعلَن، وأنّ صياغة التردد والكسب **حرفيّة** (وهي التي يُقارَن بها ما
 * يقرؤه المحرّك بعد الكتابة)، وأنّ «مستويًا» يُقاس على القيم لا على الرسم.
 */
class AudioEqModelTest {

    private fun band(
        index: Int = 0,
        center: Int? = 100,
        level: Int? = 0,
        min: Int? = -1500,
        max: Int? = 1500,
    ) = AudioEqBand(
        index = index,
        centerHz = center,
        rangeLowHz = null,
        rangeHighHz = null,
        levelMb = level,
        levelMinMb = min,
        levelMaxMb = max,
    )

    @Test
    fun `range comes from the platform and a zero span yields no range`() {
        assertEquals(-1500f, eqSliderRange(band())!!.start, 0.001f)
        assertEquals(1500f, eqSliderRange(band())!!.endInclusive, 0.001f)
        // مدًى صفريّ يعني «النطاق لا يقبل تحريكًا» — ولا يُخترع له `0..1`.
        assertNull(eqSliderRange(band(min = 0, max = 0)))
        assertFalse(band(min = 0, max = 0).isWritable)
    }

    @Test
    fun `missing level is not zero and yields no fraction`() {
        assertNull(eqLevelFraction(band(level = null)))
        // ‏**ولا يمنع غيابُ الكسبِ تحويلَ نسبة إلى قيم**: التحويل يعتمد على المدى وحده، والكسب الحاليّ
        // ليس مُدخلًا له. وخلطُهما كان خطأً في هذا الاختبار وحده، فهو المُصلَح لا الدالّة.
        assertEquals(0, eqLevelFromFraction(band(level = null), 0.5f)!!)
        // والصفر نفسه **قراءة**: يقع في وسط مدًى متماثل.
        assertNotNullValue(eqLevelFraction(band(level = 0)))
    }

    private fun assertNotNullValue(value: Float?) {
        assertTrue("expected a value, got null", value != null)
    }

    @Test
    fun `fraction and level are inverse and clamp to the declared range`() {
        assertEquals(0.5f, eqLevelFraction(band(level = 0))!!, 0.001f)
        assertEquals(0, eqLevelFromFraction(band(), 0.5f)!!)
        // والسحب خارج المدى لا يُنتج قيمةً خارج المُعلَن.
        assertEquals(-1500, eqLevelFromFraction(band(), -3f)!!)
        assertEquals(1500, eqLevelFromFraction(band(), 3f)!!)
    }

    @Test
    fun `curve x is logarithmic and y is measured on the declared range`() {
        val bands = listOf(
            band(index = 0, center = 100, level = 0),
            band(index = 1, center = 1000, level = 600),
        )
        val curve = eqCurveNodesOf(bands)
        assertEquals(2, curve.size)
        assertEquals(0f, curve[0].x, 0.001f)
        assertEquals(1f, curve[1].x, 0.001f)
        // و**الارتفاع على المدى المُعلَن** (±1500) لا على أكبر كسبٍ مقروء: ٦٠٠ ÷ ١٥٠٠ = ٠٫٤.
        // وهذا هو الفرق الذي يجعل النقطة التي تُرى هي النقطة التي تُسحب.
        assertEquals(0.4f, curve[1].y, 0.001f)
        assertEquals(1, curve[1].index)
        assertEquals(600, curve[1].levelMb)
        assertEquals(1500, eqCurveMetricsOf(bands).yScaleMb)
        assertTrue(eqCurveMetricsOf(bands).isDraggable)
    }

    @Test
    fun `without a declared range the curve is relative and cannot be dragged`() {
        val bands = listOf(
            band(index = 0, center = 100, level = 0, min = null, max = null),
            band(index = 1, center = 1000, level = 600, min = null, max = null),
        )
        val metrics = eqCurveMetricsOf(bands)
        // يُرسم الشكل (مُعايرًا على أكبر كسبٍ مقروء) **ولا يُسحب**: لا سحب بلا مدًى مُعلَن.
        assertFalse(metrics.scaleDeclared)
        assertEquals(600, metrics.yScaleMb)
        assertEquals(1f, eqCurveNodesOf(bands, metrics)[1].y, 0.001f)
        assertFalse(metrics.isDraggable)
        assertNull(eqLevelFromCurveHeight(bands[1], metrics, 0.5f))
        assertNull(eqNearestDraggableBandIndex(bands, metrics, 0.5f))
    }

    @Test
    fun `curve drops bands with no centre or no level`() {
        assertTrue(eqCurveNodesOf(listOf(band(center = 0, level = 300))).isEmpty())
        assertTrue(eqCurveNodesOf(listOf(band(center = 200, level = null))).isEmpty())
        // وبلا مقياس (لا كسب معلَن ولا مقروء) لا منحنى أصلًا.
        assertTrue(eqCurveNodesOf(listOf(band(level = null, min = null, max = null))).isEmpty())
    }

    @Test
    fun `dragging maps height to a level inside the declared range`() {
        val target = band(index = 0, center = 1000, min = -1200, max = 1200)
        val metrics = eqCurveMetricsOf(listOf(target))
        assertEquals(1200, eqLevelFromCurveHeight(target, metrics, 1f)!!)
        assertEquals(-1200, eqLevelFromCurveHeight(target, metrics, -1f)!!)
        assertEquals(0, eqLevelFromCurveHeight(target, metrics, 0f)!!)
        // والسحب خارج الإطار لا يُنتج قيمةً خارج المُعلَن — يُقيَّد ولا يُرفض.
        assertEquals(1200, eqLevelFromCurveHeight(target, metrics, 4f)!!)
        assertEquals(-1200, eqLevelFromCurveHeight(target, metrics, -4f)!!)
        // وذهابٌ وعودةٌ لا يُغيّر الرقم: الارتفاع المشتقّ من الكسب يعيد الكسب نفسه.
        val height = eqCurveHeightOf(target.copy(levelMb = 720), metrics)!!
        assertEquals(720, eqLevelFromCurveHeight(target, metrics, height)!!)
    }

    @Test
    fun `the nearest draggable band is chosen by position and skips locked bands`() {
        val bands = listOf(
            band(index = 0, center = 100),
            band(index = 1, center = 1000),
            band(index = 2, center = 10000),
        )
        val metrics = eqCurveMetricsOf(bands)
        assertEquals(0, eqNearestDraggableBandIndex(bands, metrics, 0f)!!)
        assertEquals(1, eqNearestDraggableBandIndex(bands, metrics, 0.5f)!!)
        assertEquals(2, eqNearestDraggableBandIndex(bands, metrics, 0.99f)!!)

        // والنطاق الذي لا يقبل كتابةً لا يُختار ولو كان الأقرب إلى الإصبع.
        val locked = listOf(band(index = 0, center = 100, min = 0, max = 0), band(index = 1, center = 1000))
        val lockedMetrics = eqCurveMetricsOf(locked)
        assertFalse(locked[0].isWritable)
        assertEquals(1, eqNearestDraggableBandIndex(locked, lockedMetrics, 0f)!!)
    }

    @Test
    fun `flat response is measured on the declared values`() {
        assertTrue(eqIsFlat(listOf(band(level = 200), band(index = 1, level = 200))))
        assertFalse(eqIsFlat(listOf(band(level = 200), band(index = 1, level = 0))))
        // ولا قراءة ⇒ لا حكم بالاستواء.
        assertFalse(eqIsFlat(listOf(band(level = null))))
    }

    @Test
    fun `frequency and level formatting is literal`() {
        assertEquals("60 Hz", eqFormatFrequency(60))
        assertEquals("1 kHz", eqFormatFrequency(1000))
        assertEquals("1.2 kHz", eqFormatFrequency(1200))
        assertNull(eqFormatFrequency(null))
        assertNull(eqFormatFrequency(0))

        assertEquals("+3.5 dB", eqFormatLevel(350))
        assertEquals("-2 dB", eqFormatLevel(-200))
        assertEquals("0 dB", eqFormatLevel(0))
        assertNull(eqFormatLevel(null))
    }
}
