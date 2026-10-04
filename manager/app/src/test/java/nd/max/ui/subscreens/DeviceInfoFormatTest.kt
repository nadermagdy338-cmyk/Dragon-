/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الصياغات والتحليلات الصافية — كل دالّة قيمةً في قيمةً، والاختبار هنا **على نصوص حقيقية**
 * لا على أرقام مُقرَّبة: سطر `GLES` بصيغة SurfaceFlinger، و`meminfo` بصيغة النواة.
 *
 * **والمقاس هو القاعدة لا المثال:** كل اختبار يثبّت حدًّا (صفر ليس قراءة، رقمٌ مجهول لا
 * يُفسَّر، نصٌّ ناقص لا يُنتج قيمة) — فلو تغيّرت الصياغة لجماليّة لأحبطها الحارس.
 */
class DeviceInfoFormatTest {

    // ── عمر التشغيل ────────────────────────────────────────────────────────

    @Test
    fun `uptime is never zero and never negative`() {
        assertNull(formatUptime(0))
        assertNull(formatUptime(-5))
        // وأقلّ من دقيقة تُقال بالثواني — لا «0m» تُقرأ صفرًا.
        assertEquals("45s", formatUptime(45))
        assertEquals("1m", formatUptime(90))
        assertEquals("1h 2m", formatUptime(3_720))
        assertEquals("1d 1h", formatUptime(90_000))
    }

    // ── اللحظة الزمنية ─────────────────────────────────────────────────────

    @Test
    fun `an epoch moment is formatted in a fixed zone and format`() {
        assertNull(formatEpochMillis(0, "UTC"))
        assertNull(formatEpochMillis(-1, null))
        // والمنطقة **المعطاة** هي المحسوبة — فلا تتبدّل الصورة بمنطقة الآلة التي تُشغّل الاختبار.
        assertEquals("2025-08-12 12:00", formatEpochMillis(1_755_000_000_000L, "UTC"))
        assertEquals("2025-08-12 15:00", formatEpochMillis(1_755_000_000_000L, "Africa/Cairo"))
    }

    // ── الشاشة ────────────────────────────────────────────────────────────

    @Test
    fun `aspect ratio is the long side over the short one`() {
        assertNull(aspectRatioLabel(null, 1220))
        assertNull(aspectRatioLabel(0, 0))
        assertEquals("2.22 : 1", aspectRatioLabel(2712, 1220))
        // وترتيب الطرفين لا يُغيّر النتيجة: الأعلى على الأقصر في الحالتين.
        assertEquals(aspectRatioLabel(2712, 1220), aspectRatioLabel(1220, 2712))
    }

    @Test
    fun `the diagonal is computed from pixels and dpi, never asserted`() {
        assertNull(diagonalInchesLabel(2400, 1080, null))
        assertNull(diagonalInchesLabel(2400, 0, 420))
        // sqrt(2400² + 1080²) / 420 = 6.27 — حسابٌ مُعلن لا قياس.
        assertEquals("6.27", diagonalInchesLabel(2400, 1080, 420))
    }

    @Test
    fun `the density class is the nearest standard bucket`() {
        assertNull(densityBucketLabel(null))
        assertEquals("MDPI", densityBucketLabel(160))
        assertEquals("XXHDPI", densityBucketLabel(480))
        // وجهاز 520dpi يكون XXHDPI — أقرب قيمة قياسية (480) لا التخمين.
        assertEquals("XXHDPI", densityBucketLabel(520))
        assertEquals("XXXHDPI", densityBucketLabel(640))
    }

    // ── Vulkan ────────────────────────────────────────────────────────────

    @Test
    fun `the encoded vulkan version is decoded by its declared bit layout`() {
        assertNull(decodeVulkanVersion(null))
        assertNull(decodeVulkanVersion(0))
        // 1.3.231 = (1<<22) | (3<<12) | 231
        assertEquals("1.3.231", decodeVulkanVersion((1 shl 22) or (3 shl 12) or 231))
        assertNull(vulkanLevelLabel(0))
        assertEquals("2", vulkanLevelLabel(2))
    }

    // ── سطر GLES ──────────────────────────────────────────────────────────

    @Test
    fun `the surfaceflinger gles line yields vendor renderer api and driver`() {
        val parsed = parseGlesLine("GLES: ARM, Mali-G720 MC7, OpenGL ES 3.2 v1.r44p1-01eac0")
        assertEquals("ARM", parsed?.vendor)
        assertEquals("Mali-G720 MC7", parsed?.renderer)
        assertEquals("OpenGL ES 3.2", parsed?.api)
        assertEquals("v1.r44p1-01eac0", parsed?.driver)
    }

    @Test
    fun `the gles line is found inside a dumpsys block and nowhere else`() {
        val dump = "Display 0 HWC layers:\n  GLES: Qualcomm, Adreno (TM) 730, OpenGL ES 3.2 v1 @512.0\nEGL: foo"
        val parsed = parseGlesLine(dump)
        assertEquals("Qualcomm", parsed?.vendor)
        assertEquals("Adreno (TM) 730", parsed?.renderer)
        // وسطر بلا `GLES:` ليس سطرَ الرسوم — فلا يُنتج قيمةً من أي نصّ.
        assertNull(parseGlesLine("EGL: foo, bar, OpenGL ES 3.0"))
        assertNull(parseGlesLine(null))
    }

    // ── `/proc/meminfo` ────────────────────────────────────────────────────

    @Test
    fun `meminfo is parsed by the kernel's own keys and units`() {
        val text = """
            MemTotal:        8123456 kB
            MemFree:          512000 kB
            MemAvailable:    3145728 kB
            Cached:          2097152 kB
            Buffers:          262144 kB
            SwapTotal:             0 kB
        """.trimIndent()
        val info = parseMemInfo(text)
        assertEquals(8_123_456L, info?.totalKb)
        assertEquals(3_145_728L, info?.availableKb)
        assertEquals(2_097_152L, info?.cachedKb)
        assertEquals(262_144L, info?.buffersKb)
        // و`MemTotal` وحدها شرط اللقطة — ونصٌّ لا يحملها ليس لقطة.
        assertNull(parseMemInfo("MemFree: 1 kB"))
        assertNull(parseMemInfo(null))
    }

    // ── ترميزات البطارية والشاشة ──────────────────────────────────────────

    @Test
    fun `battery health and power source codes map to resources and refuse the unknown`() {
        assertEquals(nd.max.R.string.devinfo_battery_health_good, batteryHealthLabelRes(2))
        assertEquals(nd.max.R.string.devinfo_battery_health_overheat, batteryHealthLabelRes(5))
        // ورقمٌ لا يعرفه الجدول يُرجع `null` — لا تفسيرًا مُختلقًا.
        assertNull(batteryHealthLabelRes(99))
        assertNull(batteryHealthLabelRes(null))

        assertEquals(nd.max.R.string.devinfo_power_battery, powerSourceLabelRes(0))
        assertEquals(nd.max.R.string.devinfo_power_usb, powerSourceLabelRes(2))
        assertEquals(nd.max.R.string.devinfo_power_wireless, powerSourceLabelRes(4))
        assertNull(powerSourceLabelRes(3))
    }

    @Test
    fun `orientation codes map to resources and refuse the unknown`() {
        assertEquals(nd.max.R.string.devinfo_orientation_portrait, orientationLabelRes(1))
        assertEquals(nd.max.R.string.devinfo_orientation_landscape, orientationLabelRes(2))
        assertNull(orientationLabelRes(0))
    }

    @Test
    fun `a zero charge counter is a reading and a negative one is not`() {
        assertEquals(3150, milliAmpHoursFromMicro(3_150_000))
        assertEquals(0, milliAmpHoursFromMicro(0))
        assertNull(milliAmpHoursFromMicro(-5))
        assertNull(milliAmpHoursFromMicro(null))
    }

    @Test
    fun `hdr type numbers name themselves and unknown numbers are dropped`() {
        assertEquals("Dolby Vision · HDR10 · HLG · HDR10+", hdrTypeNames(listOf(1, 2, 3, 4)))
        // والتكرار يُزال، والمجهول يُحذف لا يُخترع له اسم.
        assertEquals("HDR10", hdrTypeNames(listOf(2, 2, 99)))
        assertNull(hdrTypeNames(emptyList()))
        assertNull(hdrTypeNames(null))
    }

    @Test
    fun `hdcp level names the version and treats no output as its own case`() {
        assertEquals("HDCP 2.3", formatHdcpLevel(5))
        assertNull(formatHdcpLevel(0))
        assertNull(formatHdcpLevel(-1))
        assertTrue(isHdcpNoDigitalOutput(-1))
        assertFalse(isHdcpNoDigitalOutput(5))
    }

    // ── الكاميرا ──────────────────────────────────────────────────────────

    @Test
    fun `camera optics are formatted with their units and zeros are not readings`() {
        assertEquals("f/1.7", formatAperture(1.7f))
        assertNull(formatAperture(0f))
        assertEquals("4.94 mm", formatFocalLength(4.94f))
        assertEquals("6.55 × 4.92 mm", formatSensorSize(6.55f, 4.92f))
        assertNull(formatSensorSize(6.55f, null))
        // والميغابكسل **حسابٌ من المصفوفة** لا رقمٌ إعلانيّ: 4096 × 3072 = 12.6 MP.
        assertEquals("12.6 MP", formatMegaPixels(4096, 3072))
        assertNull(formatMegaPixels(4096, null))
    }

    @Test
    fun `the color filter arrangement names only the declared orders`() {
        assertEquals("BGGR", colorFilterName(3))
        assertEquals("MONO", colorFilterName(5))
        assertNull(colorFilterName(9))
    }

    @Test
    fun `a partition line shows what was read and a question mark where it was not`() {
        assertEquals("/data 44.0/220.0 GB", formatPartitionLine("/data", 44.0f, 220.0f))
        // ومستخدمٌ لم يُقرأ يُكتب `?` — لا صفرًا يبدو قياسًا.
        assertEquals("/data ?/220.0 GB", formatPartitionLine("/data", null, 220.0f))
        assertNull(formatPartitionLine("/data", 44.0f, null))
    }

    @Test
    fun `the cpu identity codes are kept as the kernel declared them`() {
        // `CPU implementer` و`CPU part` و`CPU revision` تُعلَن على ARM وحدها — وهي رموزٌ
        // تبقى كما هي (`0x41`) لا تُحوَّل إلى اسمٍ مُستنتج. (تكملة ٢٢٥: كانت تُقرأ في طبقة
        // الجمع ثم اكتُشف أن المحلّل لا يقرأها — فانتقلت إلى الصياغة الصافية وصارت مقيسة.)
        val arm = """
            processor      : 0
            CPU implementer: 0x41
            CPU part       : 0xd05
            CPU revision   : 1
            Features       : fp asimd
            processor      : 1
            CPU implementer: 0x41
        """.trimIndent()
        val parsed = parseCpuInfo(arm)
        assertEquals("0x41", parsed.implementer)
        assertEquals("0xd05", parsed.part)
        assertEquals("1", parsed.revision)

        // وx86 لا يُعلنها — فتغيب `null` بلا اختراع.
        val x86 = parseCpuInfo("processor : 0\nflags : fpu vme\n")
        assertNull(x86.implementer)
        assertNull(x86.part)
        assertNull(x86.revision)
    }
}
