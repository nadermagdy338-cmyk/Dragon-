/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * عقد المفردات: السلّم يشتق من العتاد، والخطوة واحدة لا قفزة،
 * والنسبة (stepFraction) هي وحدة التعلّم القابلة للنقل بين الأجهزة.
 *
 * هذا الملف يحرس القرار #1: العقل يعمل على مقابض حقيقية بأصغر خطوة،
 * لا على أسماء أفعال تقفز إلى السقف الكامل — وهو بالضبط ما فشل في سجل
 * الجهاز الحقيقي ("رفع التردد" طلب السقف دفعة واحدة وسقط).
 */
class ControlRegistryTest {

    private fun control(
        ladder: List<String>,
        current: String?,
    ): Pair<ControlRegistry.Control, () -> String?> {
        var live = current
        val c = ControlRegistry.Control(
            key = "test",
            feature = nd.max.core.hardware.HardwareFeature.CPU_FREQUENCY,
            label = "اختبار",
            ladder = ladder,
            cost = 0.2f,
            read = { live },
            apply = { v -> live = v; v },
        )
        return c to { live }
    }

    @Test
    fun `step raises by exactly one rung`() {
        val (c, _) = control(listOf("100", "200", "400", "800"), "200")
        assertEquals("400", c.step("200", ControlRegistry.Direction.RAISE_PERFORMANCE))
    }

    @Test
    fun `step lowers by exactly one rung`() {
        val (c, _) = control(listOf("100", "200", "400", "800"), "400")
        assertEquals("200", c.step("400", ControlRegistry.Direction.SAVE_ENERGY))
    }

    @Test
    fun `step at the top of the ladder has nowhere to climb`() {
        val (c, _) = control(listOf("100", "200", "400"), "400")
        assertNull(c.step("400", ControlRegistry.Direction.RAISE_PERFORMANCE))
    }

    @Test
    fun `step at the bottom of the ladder has nowhere to fall`() {
        val (c, _) = control(listOf("100", "200", "400"), "100")
        assertNull(c.step("100", ControlRegistry.Direction.SAVE_ENERGY))
    }

    @Test
    fun `indexOf rejects values outside the ladder`() {
        val (c, _) = control(listOf("100", "200"), "100")
        assertNull(c.indexOf("999"))
        assertNull(c.indexOf(null))
        assertEquals(1, c.indexOf("200"))
    }

    @Test
    fun `stepFraction is a transferable unit independent of clock values`() {
        // هذا ما يجعل المعرفة تنتقل بين الأجهزة: النسبة لا المطلقات.
        val (weak, _) = control(listOf("300", "1000", "1800", "2100"), "1000")
        val (strong, _) = control(listOf("500", "1500", "2800", "3250"), "1500")
        val a = weak.stepFraction("1000", "1800")
        val b = strong.stepFraction("1500", "2800")
        assertEquals("نفس النسبة على جهازين مختلفين", a, b, 0.0001f)
        assertEquals(1f / 3f, a, 0.0001f)
    }

    @Test
    fun `stepFraction is signed for direction`() {
        val (c, _) = control(listOf("100", "200", "400"), "400")
        assertEquals(-0.5f, c.stepFraction("400", "200"), 0.0001f)
        assertEquals(0.5f, c.stepFraction("200", "400"), 0.0001f)
    }

    @Test
    fun `stepFraction is zero when a value is unplaceable`() {
        val (c, _) = control(listOf("100", "200"), "100")
        assertEquals(0f, c.stepFraction("999", "200"), 0.0001f)
    }
}
