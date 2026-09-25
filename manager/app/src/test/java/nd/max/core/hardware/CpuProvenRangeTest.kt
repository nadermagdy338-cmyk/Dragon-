/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «القيمة لم تُطلب من العقدة» ليست «العقدة رفضت القيمة».
 *
 * `setPolicyLimits` يُقيّد الطلب إلى المدى المُثبَت قبل الكتابة، فطلبٌ خارج المدى
 * لا يصل إلى العقدة أصلًا. والواجهة كانت تُعرض الحالتين بجملة واحدة، فيسأل
 * المستخدم: هل العقدة محمية أم لا تقبل القيمة؟ — ولا جواب في التطبيق.
 */
class CpuProvenRangeTest {
    // المدى المُثبَت هنا هو نفسه ما قرأه المستخدم على جهازه: ٣٠٠ ميجا – ١٫٢ جيجا،
    // مع طلب ١٫٦ جيجا — وهي الحالة التي أنتجت السؤال «أمحمية العقدة أم لا تقبل القيمة؟».
    private fun policy(
        hwMin: Long? = 300_000L,
        hwMax: Long? = 1_200_000L,
        advertised: List<Long> = listOf(300_000L, 800_000L, 1_200_000L),
    ) = CpuHardwareBackend.Policy(
        path = "/sys/devices/system/cpu/cpufreq/policy0",
        name = "policy0",
        governor = "schedutil",
        governors = listOf("schedutil"),
        minKHz = 300_000L,
        maxKHz = 1_200_000L,
        hwMinKHz = hwMin,
        hwMaxKHz = hwMax,
        availableFrequenciesKHz = advertised,
    )

    @Test fun requestAboveProvenMaxIsReportedAsOutsideNotAsRefused() {
        assertTrue(CpuHardwareBackend.isOutsideProvenRange(policy(), 1_600_000L, 1_600_000L))
    }

    @Test fun requestBelowProvenMinIsAlsoOutside() {
        assertTrue(CpuHardwareBackend.isOutsideProvenRange(policy(), 100_000L, 1_200_000L))
    }

    @Test fun requestInsideTheProvenRangeIsNotOutside() {
        assertFalse(CpuHardwareBackend.isOutsideProvenRange(policy(), 1_200_000L, 1_200_000L))
        assertFalse(CpuHardwareBackend.isOutsideProvenRange(policy(), 300_000L, 1_200_000L))
    }

    @Test fun aRangeBoundedOnlyByOneSideIsStillChecked() {
        assertTrue(CpuHardwareBackend.isOutsideProvenRange(policy(), null, 2_000_000L))
        assertTrue(CpuHardwareBackend.isOutsideProvenRange(policy(), 2_000_000L, null))
        assertFalse(CpuHardwareBackend.isOutsideProvenRange(policy(), null, 1_200_000L))
    }

    @Test fun unprovenBoundsNeverClaimTheRequestWasOutside() {
        // بلا مدى مُثبَت — ولا جدول ترددات يُسقط عليه — لا يُدّعى أن الطلب خارجه:
        // المجهول ليس صفرًا ولا حدًّا.
        val unknown = policy(hwMin = null, hwMax = null, advertised = emptyList())
        assertFalse(CpuHardwareBackend.isOutsideProvenRange(unknown, 9_000_000L, 9_000_000L))
    }

    @Test fun advertisedTableIsTheFallbackWhenExplicitBoundsAreMissing() {
        // provenMin/provenMax يسقطان على جدول الترددات المُعلَن — نفس ما يفعله
        // القيد في setPolicyLimits، فلا تنفصل الدلالتان.
        val noBounds = policy(hwMin = null, hwMax = null, advertised = listOf(300_000L, 800_000L))
        assertTrue(CpuHardwareBackend.isOutsideProvenRange(noBounds, 900_000L, null))
        assertFalse(CpuHardwareBackend.isOutsideProvenRange(noBounds, 800_000L, 800_000L))
    }

    @Test fun anEmptyTailRequestIsNeverOutside() {
        assertFalse(CpuHardwareBackend.isOutsideProvenRange(policy(), null, null))
    }
}
