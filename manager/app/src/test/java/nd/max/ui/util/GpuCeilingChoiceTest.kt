/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مقبض سقف GPU: **مالك واحد** — اختبار انحدار لعطب مقيس من سجل جهاز حقيقي.
 *
 * والقصة التي أوجبت هذا الملف، من الحزمة نفسها:
 *
 * ```text
 * gpu_profile=performance   gpu_max_freq=650000000      ← إعداد باقٍ من زمن كانت القائمة مقيَّدة
 * PERAPP_KNOB knob=gpu_profile outcome=applied expected=650000000 live=650000000
 * ```
 *
 * فقد كان اختيار «Performance» يكتب `gpu_profile` وحده ويُبقي التردد الصريح من جلسة سابقة، ثم يقدّمه
 * `AppMonitor` على البروفايل (`explicit` = المطلوب، والبروفايل لا يُقرأ عند وجوده). فما يختاره
 * المستخدم كان **يُلغى صامتًا** ويُنفَّذ الرقم القديم — وهو بالحرف: «أختار الأداء فيعطيني ٦٥٠».
 *
 * والقاعدة التي يثبّتها هذا الاختبار: من اختار أحد الاثنين فقد ملك المقبض، و`default` في أيّهما تعني
 * «لا شيء مفروض» فيتحرّر الآخر. وسبب وجودها دالّة خالصة أن العطب لا يُعاد إنتاجه بلا جهاز إلا هكذا.
 */
class GpuCeilingChoiceTest {

    @Test
    fun `choosing a profile clears a stale explicit frequency`() {
        // حالة الجهاز المقيسة: بروفايل جديد فوق تردد صريح قديم.
        val stale = AppConfig(gpu_profile = "default", gpu_max_freq = "650000000")
        val chosen = applyGpuCeilingChoice(stale, "gpu_profile", "performance")
        assertEquals("performance", chosen.gpu_profile)
        assertEquals(
            "بلا هذا السطر يُنفَّذ الرقم القديم ويُلغى اختيار المستخدم صامتًا",
            "default",
            chosen.gpu_max_freq,
        )
        // والعطب الآخر في الحالة نفسها: «متوازن يعطي ١٣٠٠ أحيانًا و٦٥٠ أحيانًا» — بحسب ما في إعداد
        // كل تطبيق من قيمة صريحة. فبعد الاختيار لم يبقَ للتطبيق إلا ما اختاره المستخدم.
        assertEquals(1, chosen.customizedFieldCount())
    }

    @Test
    fun `a named profile ignores a stale explicit GPU ceiling`() {
        assertEquals(
            null,
            PerAppKernelUtil.effectiveExplicitGpuCeiling("performance", 650_000_000L),
        )
        assertEquals(
            null,
            PerAppKernelUtil.effectiveExplicitGpuCeiling("balanced", 650_000_000L),
        )
        assertEquals(
            650_000_000L,
            PerAppKernelUtil.effectiveExplicitGpuCeiling("default", 650_000_000L),
        )
        assertEquals(
            null,
            PerAppKernelUtil.effectiveExplicitGpuCeiling("", null),
        )
    }

    @Test
    fun `only 100 percent or an explicit top OPP requests full capability`() {
        assertTrue(
            PerAppKernelUtil.isFullCapabilityRequest(
                explicitHz = null,
                advertisedMaxHz = 1_300_000_000L,
                profilePercent = 100,
            )
        )
        assertFalse(
            PerAppKernelUtil.isFullCapabilityRequest(
                explicitHz = null,
                advertisedMaxHz = 1_300_000_000L,
                profilePercent = 60,
            )
        )
        assertFalse(
            PerAppKernelUtil.isFullCapabilityRequest(
                explicitHz = null,
                advertisedMaxHz = 1_300_000_000L,
                profilePercent = 40,
            )
        )
        assertTrue(
            PerAppKernelUtil.isFullCapabilityRequest(
                explicitHz = 1_300_000_000L,
                advertisedMaxHz = 1_300_000_000L,
                profilePercent = 60,
            )
        )
        assertFalse(
            PerAppKernelUtil.isFullCapabilityRequest(
                explicitHz = 754_000_000L,
                advertisedMaxHz = 1_300_000_000L,
                profilePercent = 100,
            )
        )
    }

    @Test
    fun `choosing an explicit frequency clears the profile`() {
        val profiled = AppConfig(gpu_profile = "gaming")
        val chosen = applyGpuCeilingChoice(profiled, "gpu_max_freq", "1300000000")
        assertEquals("1300000000", chosen.gpu_max_freq)
        assertEquals("default", chosen.gpu_profile)
        assertEquals(1, chosen.customizedFieldCount())
    }

    @Test
    fun `default on either side means nothing is imposed and releases the other`() {
        val both = AppConfig(gpu_profile = "performance", gpu_max_freq = "754000000")
        val clearedByProfile = applyGpuCeilingChoice(both, "gpu_profile", "default")
        assertEquals("default", clearedByProfile.gpu_profile)
        assertEquals("default", clearedByProfile.gpu_max_freq)
        val clearedByFrequency = applyGpuCeilingChoice(both, "gpu_max_freq", "default")
        assertEquals("default", clearedByFrequency.gpu_profile)
        assertEquals("default", clearedByFrequency.gpu_max_freq)
        assertEquals(0, clearedByProfile.customizedFieldCount())
        assertEquals(0, clearedByFrequency.customizedFieldCount())
    }

    @Test
    fun `the legacy thermal key obeys the same rule and keeps its rename`() {
        // `thermal_profile` مفتاح قديم يدلّ على الاختيار نفسه، ويُرقّى إلى `gpu_profile` عند القراءة.
        val stale = AppConfig(gpu_max_freq = "650000000")
        val powersave = applyGpuCeilingChoice(stale, "thermal_profile", "powersave")
        assertEquals("power", powersave.gpu_profile)
        assertEquals("default", powersave.gpu_max_freq)
        assertEquals("default", powersave.thermal_profile)

        val gaming = applyGpuCeilingChoice(AppConfig(), "thermal_profile", "gaming")
        assertEquals("gaming", gaming.gpu_profile)
        assertEquals("default", gaming.gpu_max_freq)
    }

    @Test
    fun `a knob this rule does not own is left exactly as it was`() {
        val config = AppConfig(gpu_profile = "performance", gpu_max_freq = "1300000000")
        assertEquals(config, applyGpuCeilingChoice(config, "cpu_governor", "performance"))
        assertEquals(config, applyGpuCeilingChoice(config, "gpu_governor", "performance"))
    }

    @Test
    fun `other fields of the app override survive the choice`() {
        // المقبض واحد، والإعداد كائن: ما لا علاقة له بسقف GPU لا يُلمس.
        val config = AppConfig(
            perf_lite_mode = "on",
            gpu_max_freq = "650000000",
            refresh_rate = "120",
            kill_bg_apps = "on",
        )
        val chosen = applyGpuCeilingChoice(config, "gpu_profile", "gaming")
        assertEquals("on", chosen.perf_lite_mode)
        assertEquals("120", chosen.refresh_rate)
        assertEquals("on", chosen.kill_bg_apps)
        assertEquals("gaming", chosen.gpu_profile)
    }
}
