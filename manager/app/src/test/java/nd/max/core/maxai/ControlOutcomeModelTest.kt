package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد التعلّم التجريبي: إحصاء Welford التزايدي، والثقة الصادقة، وحدود
 * الربحية الحرارية. هذه الطبقة هي بديل المكافأة العامة التي لا تُعزى
 * إلى مقبض بعينه (قرار #17)، فأي انحدار فيها يعيد "التعلّم الأعمى".
 *
 * الرياضيات نقية ([ControlOutcomeModel.Effect]) فتُختبر في JVM بلا
 * Android — وهذا اختيار تصميمي: الحساب منفصل عن حالة التخزين.
 */
class ControlOutcomeModelTest {

    @Test
    fun `fresh effect declares zero confidence instead of false certainty`() {
        val e = ControlOutcomeModel.Effect()
        assertEquals(0, e.samples)
        assertEquals(0f, e.confidence, 0.0f)
        assertEquals(0f, e.meanGain, 0.0f)
    }

    @Test
    fun `welford mean converges to the true average`() {
        var e = ControlOutcomeModel.Effect()
        listOf(0.10f, 0.20f, 0.30f, 0.40f).forEach { e = e.observe(it, 0f) }
        assertEquals(4, e.samples)
        assertEquals(0.25f, e.meanGain, 0.0001f)
    }

    @Test
    fun `confidence grows with samples and never reaches certainty`() {
        var e = ControlOutcomeModel.Effect()
        val c1 = e.observe(0.1f, 0f).confidence
        e = e.observe(0.1f, 0f).let { it.observe(0.1f, 0f) }.let { it.observe(0.1f, 0f) }
        val c4 = e.confidence
        assertTrue("الثقة تنمو مع العينات", c4 > c1)
        assertTrue("لا يقين مطلق من عينات محدودة", c4 < 1f)
    }

    @Test
    fun `variance separates a steady knob from an erratic one`() {
        var steady = ControlOutcomeModel.Effect()
        var erratic = ControlOutcomeModel.Effect()
        val samples = listOf(0.10f, 0.10f, 0.10f, 0.10f)
        val noisy = listOf(0.50f, -0.30f, 0.40f, -0.20f)
        samples.forEach { steady = steady.observe(it, 0f) }
        noisy.forEach { erratic = erratic.observe(it, 0f) }
        assertTrue(
            "المقبض المتذبذب يجب أن يحمل انحرافًا أكبر",
            erratic.gainStdDev > steady.gainStdDev,
        )
    }

    @Test
    fun `thermal mean tracks repeated overheating`() {
        var e = ControlOutcomeModel.Effect()
        listOf(2.0f, 2.4f, 1.6f).forEach { e = e.observe(0.01f, it) }
        assertEquals(2.0f, e.meanThermal, 0.0001f)
    }

    @Test
    fun `prior std dev applies before two samples exist`() {
        // صفر انحراف قبل عينتين يعطي ثقة كاذبة — الافتراضي المعلن أصدق.
        val one = ControlOutcomeModel.Effect().observe(0.1f, 0f)
        assertEquals(1, one.samples)
        assertTrue("انحراف مسبق معلن لا صفر", one.gainStdDev > 0f)
    }
}
