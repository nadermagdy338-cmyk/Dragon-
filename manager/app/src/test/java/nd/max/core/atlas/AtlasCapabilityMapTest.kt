/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * جدول حقيقة خريطة القدرة — سبعة حالات، وأسبقية واحدة لا تتغيّر.
 *
 * والقاعدة التي تُقاس بقوة هنا: **النجاح لا يُدَّعى إلا خلف `SUPPORTED`**، وهي لا تتحقق إلا
 * بمسار مؤهل **و**نتيجة مُتحقَّقة على هذا الجهاز. وما لم يُقاس يبقى `UNKNOWN` — لا «غير متاح»
 * ولا «مدعوم»؛ والفارق بين «لم نُقِس» و«قِسنا فلم يُجب» محفوظ في رمز السبب.
 */
class AtlasCapabilityMapTest {

    private fun inputs(
        safety: AtlasSafetyVerdict = AtlasSafetyVerdict.Allowed,
        measured: Boolean = true,
        readable: Boolean = false,
        absenceProved: Boolean = false,
        routeKnown: Boolean = false,
        routeStatus: AtlasRouteStatus? = null,
        routeReason: AtlasRouteReason? = null,
        verified: Boolean = false,
        writeExpected: Boolean = true,
    ) = AtlasCapabilityInputs(
        target = AtlasControlTarget.CPU_FREQUENCY,
        safety = safety,
        measured = measured,
        readable = readable,
        absenceProved = absenceProved,
        routeKnown = routeKnown,
        routeStatus = routeStatus,
        routeReason = routeReason,
        verifiedThisGeneration = verified,
        writeExpected = writeExpected,
    )

    @Test
    fun `a monitoring-only surface that reads is read-only, never a phantom adapter gap`() {
        // «يحتاج مُلاءِمًا» تعدُد طريقًا يحتاجه أحد — ومراقبة الحرارة تقرأ ولا تكتب أصلًا،
        // فصدق الخريطة يفرض الفصل بينهما.
        val monitoring = AtlasCapabilityRules.derive(inputs(readable = true, writeExpected = false))
        assertEquals(AtlasCapabilityState.READ_ONLY, monitoring.state)

        // أمّا البناء الذي يدّعي طريقة كتابة فتغيب عن هذا الجهاز: الفجوة صادقة.
        val gap = AtlasCapabilityRules.derive(inputs(readable = true, writeExpected = true))
        assertEquals(AtlasCapabilityState.NEEDS_ADAPTER, gap.state)
    }

    @Test
    fun `an eligible route verified on this device is the only supported state`() {
        val capability = AtlasCapabilityRules.derive(
            inputs(routeKnown = true, routeStatus = AtlasRouteStatus.ELIGIBLE, verified = true),
        )
        assertEquals(AtlasCapabilityState.SUPPORTED, capability.state)
        assertTrue(capability.verified)
    }

    @Test
    fun `an eligible route without a proven write is writable, not supported`() {
        val capability = AtlasCapabilityRules.derive(
            inputs(routeKnown = true, routeStatus = AtlasRouteStatus.ELIGIBLE),
        )
        assertEquals(AtlasCapabilityState.WRITABLE, capability.state)
    }

    @Test
    fun `a verified write is still never-touch when a safety rule names the interface`() {
        // الأسبقية العليا: نجاح قديم أو مسار مؤهل لا يُبطلان قاعدة سلامة.
        val capability = AtlasCapabilityRules.derive(
            inputs(
                safety = AtlasSafetyVerdict.Denied("thermal-trips", "hardware protection"),
                routeKnown = true,
                routeStatus = AtlasRouteStatus.ELIGIBLE,
                verified = true,
            ),
        )
        assertEquals(AtlasCapabilityState.NEVER_TOUCH, capability.state)
        assertEquals("safety:thermal-trips", capability.reason)
    }

    @Test
    fun `a visible interface with no route of ours needs an adapter or another way`() {
        val capability = AtlasCapabilityRules.derive(inputs(readable = true))
        assertEquals(AtlasCapabilityState.NEEDS_ADAPTER, capability.state)
        assertEquals("adapter-gap", capability.reason)
    }

    @Test
    fun `proved absence is unavailable and nothing else`() {
        val capability = AtlasCapabilityRules.derive(inputs(absenceProved = true))
        assertEquals(AtlasCapabilityState.UNAVAILABLE, capability.state)
    }

    @Test
    fun `reads without an eligible write route are read-only, keeping the route reason`() {
        val capability = AtlasCapabilityRules.derive(
            inputs(
                routeKnown = true,
                readable = true,
                routeStatus = AtlasRouteStatus.BLOCKED,
                routeReason = AtlasRouteReason.PRIVILEGE_UNAVAILABLE,
            ),
        )
        assertEquals(AtlasCapabilityState.READ_ONLY, capability.state)
        assertEquals("map:read_only:read-only:privilege_unavailable", capability.code)
    }

    @Test
    fun `nothing measured is unknown, never unavailable`() {
        val capability = AtlasCapabilityRules.derive(inputs(measured = false))
        assertEquals(AtlasCapabilityState.UNKNOWN, capability.state)
        assertEquals("not-measured", capability.reason)
    }

    @Test
    fun `measured but unanswered without proved absence is unknown and says so`() {
        val capability = AtlasCapabilityRules.derive(inputs(measured = true))
        assertEquals(AtlasCapabilityState.UNKNOWN, capability.state)
        assertEquals("not-observed", capability.reason)
    }

    @Test
    fun `proved absence wins over an adapter gap when nothing reads`() {
        // غياب مُثبت + لا قراءة ⇒ «غير متاح» لا «يحتاج مُلاءِمًا»: الفجوة تتطلّب أن الجهاز يُظهر
        // سطحًا أصلًا.
        val capability = AtlasCapabilityRules.derive(inputs(absenceProved = true, readable = false))
        assertEquals(AtlasCapabilityState.UNAVAILABLE, capability.state)
    }

    @Test
    fun `the map holds one entry per target and counts without re-derivation`() {
        val map = AtlasCapabilityMap(
            listOf(
                AtlasCapabilityRules.derive(inputs(routeKnown = true, routeStatus = AtlasRouteStatus.ELIGIBLE)),
                AtlasCapabilityRules.derive(inputs(measured = false)).copy(target = AtlasControlTarget.GPU_FREQUENCY),
            ),
        )
        assertEquals(2, map.entries.size)
        assertEquals(
            AtlasCapabilityState.WRITABLE,
            map.forTarget(AtlasControlTarget.CPU_FREQUENCY)?.state,
        )
        assertEquals(
            1,
            map.counts()[AtlasCapabilityState.WRITABLE],
        )
        assertEquals(null, map.forTarget(AtlasControlTarget.THERMAL_PROFILE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two entries for one target are a derivation bug, not a map`() {
        AtlasCapabilityMap(
            listOf(
                AtlasCapabilityRules.derive(inputs()),
                AtlasCapabilityRules.derive(inputs(measured = false)),
            ),
        )
    }

    @Test
    fun `the state code is machine vocabulary in both directions`() {
        val capability = AtlasCapabilityRules.derive(
            inputs(routeKnown = true, routeStatus = AtlasRouteStatus.ELIGIBLE, verified = true),
        )
        assertEquals("map:supported:route-eligible+verified", capability.code)
    }
}
