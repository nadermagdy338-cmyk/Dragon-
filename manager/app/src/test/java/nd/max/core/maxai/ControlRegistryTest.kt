/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * سياسة مثل ما تقرأه الأجهزة: الحدّ الحي (`scaling_min/max_freq`) وحدّ العتاد، وجدول OPP معلَن.
     *
     * والأسماء والقيم من جهاز حقيقي (POCO X7 Pro / MT6899، ۲۰۲۶-۱۰-۰۱) — لأنّ العطب الذي
     * يحرسه هذا الاختبار عطب ذلك الجهاز بعينه.
     */
    private fun policy(
        name: String = "policy7",
        min: Long? = 3000000L,
        max: Long? = 3000000L,
        hwMin: Long? = 1000000L,
        hwMax: Long? = 3250000L,
        declared: List<Long> = listOf(1000000L, 2000000L, 2900000L, 3000000L, 3250000L),
    ) = nd.max.core.hardware.CpuHardwareBackend.Policy(
        path = "/sys/devices/system/cpu/cpufreq/$name",
        name = name,
        governor = "sugov_ext",
        governors = emptyList(),
        minKHz = min,
        maxKHz = max,
        hwMinKHz = hwMin,
        hwMaxKHz = hwMax,
        availableFrequenciesKHz = declared,
    )

    /**
     * **العطب المقيس: سياسة مثبّتة من المنصّة كانت تُنتج تسع كتابات فاشلة.**
     *
     * `policy7` على `rodin` كان `min = max = 3000000` ومداه الحقيقي `1000000..3250000`، فالسلّم
     * القديم (المبنيّ من الحدّ الأدنى الحيّ) صار `3000000:…` كله، وأي خطوة خفض تعني `min = max`
     * أي **تثبيت العنقود على تردد واحد** — وهو خطر على البطارية والحرارة قبل أن يكون فشل تحقّق.
     */
    @Test
    fun `a pinned policy yields a raise-only ladder without a pinning step`() {
        val ladder = ControlRegistry.ceilingLadder(policy())!!

        assertEquals(listOf("3000000:3250000"), ladder)
        // ولا درجة تُثبّت: كل درجة سقفها **فوق** أرضيتها.
        ladder.forEach { rung ->
            val (floor, ceiling) = rung.split(":")
            assertTrue("لا تثبيت في السلّم: $rung", ceiling.toLong() > floor.toLong())
        }
    }

    /**
     * وأثر ذلك على المخطِّط: الخطوة التي كان يكرّرها على ذلك الجهاز **لا تُنتَج أصلًا** —
     * فالانقطاع يقع عند المنبع (لا يوجد مرشّح) لا بعد معاملة فاشلة تُكلّف كتابةً واسترجاعًا.
     */
    @Test
    fun `the measured failing step is never proposed again`() {
        val ladder = ControlRegistry.ceilingLadder(policy())!!
        val control = ControlRegistry.Control(
            key = "cpu_limits:policy7",
            feature = nd.max.core.hardware.HardwareFeature.CPU_FREQUENCY,
            label = "سقف policy7",
            ladder = ladder,
            cost = 0.2f,
            read = { "3000000:3000000" },
            apply = { it },
        )

        // القيمة الحيّة (`3000000:3000000`) ليست في السلّم: لا موضع لها ⇒ لا خطوة خفض.
        assertNull(control.step("3000000:3000000", ControlRegistry.Direction.SAVE_ENERGY))
    }

    @Test
    fun `a free policy yields one rung per declared ceiling above the live floor`() {
        val ladder = ControlRegistry.ceilingLadder(policy(min = 1000000L, max = 2000000L))!!

        assertEquals(
            listOf("1000000:2000000", "1000000:2900000", "1000000:3000000", "1000000:3250000"),
            ladder,
        )
    }

    /**
     * والحدّ الحي **غير المُعلَن** يُقرَّب إلى تردد معلَن: كتابة قيمة ليست في جدول OPP تُبدَّل في
     * النواة فيبدو النجاح فشلًا (عطب `policy4` المقيس ۲۰۲۶-۰۹-۲۰).
     */
    @Test
    fun `an unlisted live floor is snapped to a declared frequency`() {
        val ladder = ControlRegistry.ceilingLadder(policy(min = 1404000L))!!

        // ‏1404000 غير مُعلَنة ⇒ 1000000 هي أكبر معلَنة تحتها.
        assertTrue(ladder.all { it.startsWith("1000000:") })
    }

    /** وحدّ حي غير مقروء لا يُعطّل المقبض: تُعاد أرضيّة العتاد المعلَنة كما كان يفعل `provenMinKHz`. */
    @Test
    fun `an unreadable live floor falls back to the smallest declared frequency`() {
        val ladder = ControlRegistry.ceilingLadder(policy(min = null))!!

        assertEquals(4, ladder.size)
        assertTrue(ladder.first().startsWith("1000000:"))
    }

    /** وسياسة بلا جدول OPP معلَن لا سلّم لها — تُسقط بالغياب لا بكتابة تخمين. */
    @Test
    fun `a policy without declared frequencies has no ladder`() {
        assertNull(ControlRegistry.ceilingLadder(policy(declared = emptyList())))
    }

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
