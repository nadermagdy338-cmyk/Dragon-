/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * منحنى الحرارة يُقاس هنا لا على الجهاز: القرار خالص، والعتاد طبقة تنفيذ.
 *
 * والاختبارات تحرس **حدود الأمان** لا الصيغة: التقريب إلى أسفل دائمًا، ولا قيمة لا يحملها الجهاز،
 * ولا خفض في بروفايلي القوّة، ولا رفع في بروفايلات التبريد — وهي المواضع التي يتحوّل فيها المتحكّم
 * من ميزة إلى عطل.
 *
 * والنسب المقيسة في هذه الملف هي **قياس جهاز حقيقي** (سلّم CPU بكيلوهرتز، وقدرة ٢٤٠٠ ميجاهرتز):
 * المنصّة كانت تخفض السقف الحيّ إلى ١٥٠٠، فكانت نسبة ٨٥٪ تُحسب من الحيّ فتُعطي ١٢٧٥ — أي **أدنى
 * من الجهاز كما هو**. وهذا بالضبط ما ترفضه الحالات أدناه.
 */
class ThermalCurveTest {

    // سلّم CPU واقعي (كيلوهرتز)، وأعلاه هو قدرة الجهاز المُعلنة.
    private val cpuLadder = listOf(300_000L, 500_000L, 700_000L, 900_000L, 1_100_000L,
        1_300_000L, 1_500_000L, 1_800_000L, 2_000_000L, 2_200_000L, 2_400_000L)

    private val capabilityHz = 2_400_000L

    private fun request(
        profile: String,
        percent: Int,
        liveMaxHz: Long = capabilityHz,
        ladder: List<Long> = cpuLadder,
        capability: Long = capabilityHz,
    ) = ThermalCurve.requestedCpuCeiling(profile, percent, capability, liveMaxHz, ladder)

    // ── بروفايلات التبريد: تنزل فقط، ومن القدرة لا من الحيّ ─────────────────

    @Test
    fun `a cooling profile caps below the live ceiling from the device capability`() {
        // ٦٠٪ من القدرة ٢٤٠٠ = ١٤٤٠ ⇒ أقرب درجة مُعلنة **تحت** النسبة هي ١٣٠٠.
        val request = request("balanced", 60)

        assertEquals(ThermalCurve.Direction.LOWER, request.direction)
        assertEquals(1_300_000L, request.hz)
    }

    @Test
    fun `rounding is always downward so the requested percentage is never exceeded`() {
        // ٩٠٪ من ٢٤٠٠ = ٢١٦٠ ⇒ ٢٠٠٠ لا ٢٢٠٠. التقريب إلى أعلى كان يكتب سقفًا فوق ما طُلب.
        val request = request("custom", 90)

        assertEquals(2_000_000L, request.hz)
        assertTrue((request.hz ?: 0L) <= (capabilityHz * 90) / 100L)
    }

    @Test
    fun `a cooling profile never raises a live ceiling that is already lower`() {
        // المنصّة خفّضت السقف إلى ١١٠٠، والطلب ٦٠٪ من القدرة (١٤٤٠): لا كتابة — الرفع في بروفايل
        // تبريد يناقض غرضه.
        val request = request("balanced", 60, liveMaxHz = 1_100_000L)

        assertNull(request.hz)
        assertFalse(request.writes)
        assertEquals(ThermalCurve.Direction.HOLD, request.direction)
        assertEquals("cooling-already-at-or-below-percent", request.reason)
    }

    @Test
    fun `a ladder with nothing below the target writes no cap`() {
        // لا درجة مُعلنة تحت ٢٠٪ من القدرة (٤٨٠)، وأدنى درجة ٥٠٠: الكتابة كانت ستُعطي سقفًا أعلى
        // من المطلوب. عدم الكتابة أصدق.
        val request = request("power", 20, ladder = listOf(500_000L, 1_000_000L, 2_400_000L))

        assertNull(request.hz)
        assertEquals("no-lower-advertised-step", request.reason)
    }

    // ── بروفايلا القوّة: رفع مشروع، ولا خفض أبدًا ──────────────────────────

    @Test
    fun `gaming raises the live ceiling the platform capped, never below it`() {
        // القياس الذي رفضه صاحب الجهاز: القدرة ٢٤٠٠، والمنصّة تقيّد الحيّ عند ١٥٠٠. نسبة ٨٥٪ من
        // القدرة = ٢٠٤٠ ⇒ درجة مُعلنة ٢٠٠٠، وهي **أعلى** من الحيّ ١٥٠٠ ⇒ رفع مُعلَن.
        val request = request("gaming", 85, liveMaxHz = 1_500_000L)

        assertEquals(ThermalCurve.Direction.RAISE, request.direction)
        assertEquals(2_000_000L, request.hz)
        assertTrue("الرفع لا ينزل بالجهاز عن واقعه", (request.hz ?: 0L) > 1_500_000L)
    }

    @Test
    fun `performance asks for the device capability when the platform holds a lower ceiling`() {
        val request = request("performance", 100, liveMaxHz = 1_500_000L)

        assertEquals(ThermalCurve.Direction.RAISE, request.direction)
        assertEquals(capabilityHz, request.hz)
    }

    @Test
    fun `a power profile does not lower a live ceiling that already equals capability`() {
        val request = request("performance", 100)

        assertNull("لا كتابة حين لا يوجد ما يُرفع", request.hz)
        assertEquals(ThermalCurve.Direction.HOLD, request.direction)
        assertEquals("power-profile-never-lowers", request.reason)
    }

    @Test
    fun `a power profile whose percentage is below the live ceiling still holds, not lowers`() {
        // الحيّ أعلى من ٨٥٪ من القدرة (٢٠٤٠): «Gaming» يطلب ٢٠٠٠، والكتابة كانت ستُنزل السقف —
        // والحيّ أعلى أصلًا، وهذا ما رُفض صراحةً.
        val request = request("gaming", 85, liveMaxHz = 2_200_000L)

        assertNull(request.hz)
        assertEquals(ThermalCurve.Direction.HOLD, request.direction)
        assertEquals("power-profile-never-lowers", request.reason)
    }

    @Test
    fun `a coarse ladder never gets a power profile written above its requested share`() {
        // سلّم خشن: لا درجة مُعلنة عند ٨٥٪ من القدرة، وأقرب درجة عند الحيّ (١٠٠٠)، والتالية هي القدرة
        // (٢٤٠٠). فقاعدة «لا سقف فوق النسبة المطلوبة» تمنع كتابة ٢٤٠٠، ورفعُ الحيّ عبثٌ فهو لا يزيد
        // شيئًا ⇒ لا كتابة. ولا ينزل البروفايل عنه بحال.
        val request = request("gaming", 85, liveMaxHz = 1_000_000L, ladder = listOf(1_000_000L, 2_400_000L))

        assertNull(request.hz)
        assertEquals("power-profile-never-lowers", request.reason)
    }

    @Test
    fun `a device that declares no step list still raises to the capability the driver states`() {
        // بلا سلّم مُعلن، القدرة تُقرأ من `cpuinfo_max_freq` وحدها وهي قيمة أعلنها السائق — فالرفع
        // إليها مشروع، والسكونُ عليها ليس خيارًا (المنصّة تخفض الحيّ تحتها).
        val request = request("gaming", 85, liveMaxHz = 1_000_000L, ladder = emptyList())

        assertEquals(ThermalCurve.Direction.RAISE, request.direction)
        assertEquals(capabilityHz, request.hz)
    }

    @Test
    fun `isPowerProfile names exactly the two power profiles`() {
        assertTrue(ThermalCurve.isPowerProfile("performance"))
        assertTrue(ThermalCurve.isPowerProfile("GAMING"))
        assertFalse(ThermalCurve.isPowerProfile("balanced"))
        assertFalse(ThermalCurve.isPowerProfile("power"))
        assertFalse(ThermalCurve.isPowerProfile("default"))
    }

    // ── حالات شاذّة: الصمت أصدق من تخمين ────────────────────────────────────

    @Test
    fun `an unreadable ceiling is reported instead of capped from a guess`() {
        assertEquals("unreadable", request("balanced", 60, liveMaxHz = 0L).reason)
        assertEquals("unreadable", request("balanced", 60, liveMaxHz = -1L).reason)
        assertEquals("unreadable", request("gaming", 85, capability = 0L).reason)
        assertNull(request("gaming", 85, capability = 0L).hz)
    }

    @Test
    fun `an out of range percentage is clamped instead of escaping the capability`() {
        // النسبة المحفوظة تُقرأ من تخزين المستخدم، فقد تكون قديمة أو فاسدة: الصفر يصير ١٪ (سقف
        // مقصود) و١٢٠ يصير ١٠٠٪ (القدرة) — ولا يُكتب فوق القدرة في الحالتين.
        assertNull("١٪ من القدرة أدنى من كل درجة مُعلنة، فلا تُكتب قيمة غير مُعلنة", request("balanced", 0).hz)
        assertEquals(
            ThermalCurve.Direction.LOWER,
            request("balanced", 1, ladder = listOf(10_000L) + cpuLadder).direction,
        )
        assertEquals(ThermalCurve.Direction.RAISE, request("performance", 120, liveMaxHz = 1_000_000L).direction)
        assertEquals(2_400_000L, request("performance", 120, liveMaxHz = 1_000_000L).hz)
    }

    // ── الصيغ المشتركة ─────────────────────────────────────────────────────

    @Test
    fun `a policy range keeps its floor and moves only the ceiling`() {
        assertEquals("300000:900000", ThermalCurve.rangeFor(300_000L, 900_000L))
        assertEquals(":900000", ThermalCurve.rangeFor(null, 900_000L))
    }

    @Test
    fun `the realized percentage is reported so intent and result cannot be confused`() {
        // طُلب ٦٠ من القدرة ووقع ٥٤: بدون هذا الرقم يُقرأ السجل كأن الطلب لم يُنفَّذ.
        assertEquals(54, ThermalCurve.realizedPercent(2_400_000L, 1_300_000L))
        assertNull(
            "السقف الأعلى من المرجع ليس تنفيذًا لنسبة",
            ThermalCurve.realizedPercent(2_400_000L, 2_600_000L),
        )
    }
}
