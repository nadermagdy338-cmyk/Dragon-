/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens

import java.util.Locale
import nd.max.core.audio.AudioDeviceDescriptor
import nd.max.core.audio.AudioEffectInfo
import nd.max.core.audio.AudioInventorySnapshot
import nd.max.core.audio.AudioOutputCapabilities
import nd.max.core.platform.SensorInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نموذج شاشة معلومات الجهاز — مُقاس **بلا جهاز ولا محاكي**.
 *
 * **الحدّ المعلن أولًا:** ما لا يُقاس هنا هو **القراءة**: `Build` و`/proc/meminfo`
 * و`SensorManager` تُقرأ على هاتف حقيقي، فلا يُدّعى أن جهازًا بعينه يُعلن قيمة بعينه.
 * والمقاس هو **ما يُفعل بما قُرِئ**: أي حقل يُعرض، وبأي وحدة، ومتى يُقال «غير مقروء»
 * بدل أن يُكتب صفر — وهي القاعدة التي نصّها المالك: «عدم إظهار قيم غير متاحة باعتبارها
 * صفرًا» و«عرض Unsupported أو Unavailable عندما يتعذر الحصول على المعلومة».
 */
class DeviceInfoModelTest {

    // ── الأقسام ──────────────────────────────────────────────────────

    @Test
    fun `every declared section is produced in declaration order`() {
        val sections = deviceInfoSections(DeviceInfoSnapshot())
        assertEquals(DeviceInfoSection.entries.toList(), sections.map { it.section })
        // و«قسم بلا حقول» عطب عرض لا شاشة فارغة: القسم يُعلن شيئًا أو لا يُعلن أصلًا.
        val empty = sections.filter { it.facts.isEmpty() }.map { it.section.name }
        assertTrue("أقسام بلا حقول: $empty", empty.isEmpty())
    }

    // ── القاعدة الأولى: لا صفر مكان قيمة لم تُقرأ ─────────────────────

    /**
     * لقطة فارغة تمامًا: **لا قيمة واحدة** تُنتجها الشاشة، ولا واحدة منها "0".
     * وهذا هو العطب الذي تمنعه هذه الشاشة: لوحة أصفار تبدو قياسًا على جهاز لم تُقرأ عتاده.
     */
    @Test
    fun `an empty snapshot fabricates nothing`() {
        val facts = deviceInfoSections(DeviceInfoSnapshot()).flatMap { it.facts }
        assertTrue("لا حقول أصلًا؟ فالقياس لا يقيس شيئًا", facts.size >= 30)
        val fabricated = facts.filter { it.value != null }
        assertTrue("حقول اخترعت قيمة من لقطة فارغة: ${fabricated.map { it.value }}", fabricated.isEmpty())
    }

    /**
     * والصفر **قراءة** في موضعين فقط: مستوى البطارية ونسبة الحمل. فتمرّ كما هي، بينما
     * الصفر في الإجمالي (ذاكرة، تخزين، أبعاد شاشة، تردّد) غيابُ قراءة لا قيمة.
     */
    @Test
    fun `a real zero is shown where zero is a reading, and hidden where it is a gap`() {
        val snapshot = DeviceInfoSnapshot(
            batteryPercent = 0,
            cpuLoadPercent = 0,
            ramTotalMb = 0,
            storageTotalGb = 0f,
            displayWidth = 0,
            displayHeight = 0,
            cpuCeilingMhz = 0,
            gpuFreqMhz = 0,
        )
        val facts = deviceInfoSections(snapshot).flatMap { it.facts }
        fun value(labelRes: Int) = facts.first { it.label == labelRes }

        assertEquals("0", value(nd.max.R.string.devinfo_battery_level).value)
        assertEquals("0", value(nd.max.R.string.devinfo_cpu_load).value)
        assertNull(value(nd.max.R.string.devinfo_ram_total).value)
        assertNull(value(nd.max.R.string.devinfo_storage_total).value)
        assertNull(value(nd.max.R.string.devinfo_resolution).value)
        assertNull(value(nd.max.R.string.devinfo_cpu_ceiling).value)
        assertNull(value(nd.max.R.string.devinfo_gpu_freq).value)
    }

    @Test
    fun `an unreadable value is declared, not silently dropped`() {
        val facts = deviceInfoSections(DeviceInfoSnapshot()).flatMap { it.facts }
        // وحين لا قيمة، فالحالة تُقال: «غير مقروء» أو «غير مدعوم» — لا فراغ بلا سبب.
        val silent = facts.filter { it.value == null && it.trust == DeviceInfoTrust.Live }
        assertTrue("حقول بلا قيمة وبلا حالة تُعلنها: ${silent.size}", silent.isEmpty())
    }

    /**
     * بوابة «المصدر مذكور»: كل حقل يعرف من أين جاء. وهذا نصّ ADR-07 — «لا تليمتري
     * مُصنَّع»، والرقم بلا مصدر رقم لا يُصدَّق.
     */
    @Test
    fun `every fact names its source`() {
        val facts = deviceInfoSections(populated()).flatMap { it.facts }
        val sourceless = facts.filter { it.source.isNullOrBlank() }
        assertTrue("حقول بلا مصدر: ${sourceless.size}", sourceless.isEmpty())
    }

    // ── الصياغة: وحدة واحدة لكل فكرة ──────────────────────────────────

    @Test
    fun `memory switches unit at one threshold, so no reading carries two units`() {
        val big = deviceInfoSections(DeviceInfoSnapshot(ramTotalMb = 8192)).flatMap { it.facts }
            .first { it.label == nd.max.R.string.devinfo_ram_total }
        assertEquals("8.0", big.value)
        assertEquals("GB", big.unit)

        val small = deviceInfoSections(DeviceInfoSnapshot(ramTotalMb = 512)).flatMap { it.facts }
            .first { it.label == nd.max.R.string.devinfo_ram_total }
        assertEquals("512", small.value)
        assertEquals("MB", small.unit)
    }

    @Test
    fun `frequency is shown in GHz with two decimals`() {
        val facts = deviceInfoSections(DeviceInfoSnapshot(cpuCeilingMhz = 2400, gpuFreqMhz = 260))
            .flatMap { it.facts }
        val ceiling = facts.first { it.label == nd.max.R.string.devinfo_cpu_ceiling }
        assertEquals("2.40", ceiling.value)
        assertEquals("GHz", ceiling.unit)
        // والتردّد المنخفض يبقى منزلتين أيضًا: `0.26` لا `0.3` — القرار واحد لكل الحقول.
        assertEquals("0.26", facts.first { it.label == nd.max.R.string.devinfo_gpu_freq }.value)
    }

    @Test
    fun `resolution is one figure, and only when both sides were read`() {
        val both = deviceInfoSections(DeviceInfoSnapshot(displayWidth = 2400, displayHeight = 1080))
            .flatMap { it.facts }.first { it.label == nd.max.R.string.devinfo_resolution }
        assertEquals("2400 × 1080", both.value)

        val half = deviceInfoSections(DeviceInfoSnapshot(displayWidth = 2400))
            .flatMap { it.facts }.first { it.label == nd.max.R.string.devinfo_resolution }
        assertNull("نصف دقّة ليست دقّة", half.value)
    }

    /**
     * والفاصلة العشرية **بمقام ثابت**: على جهاز عربيّ لو استُعمل `String.format` بمقام
     * الجهاز لكتب `2.40` بحروف أخرى واختلف المخرَج بين جهازين. والقياس هنا يثبت الثبات.
     */
    @Test
    fun `decimal formatting does not follow the device locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "EG"))
            assertEquals("2.40", formatDouble(2.4, 2))
            assertEquals("8.0", formatDouble(8.0))
        } finally {
            Locale.setDefault(original)
        }
    }

    // ── الحساب: لا مشتقّ من مجهول ──────────────────────────────────────

    @Test
    fun `a derived value is not computed from a missing side`() {
        assertEquals(5000, availableMb(usedMb = 3000, totalMb = 8000))
        assertNull(availableMb(usedMb = 3000, totalMb = null))
        assertNull(availableMb(usedMb = null, totalMb = 8000))
        // ولا متاح سالب: طرحٌ يخرج عن المدى يعني أن أحد الرقمين لا يصف نفس الجهاز.
        assertEquals(0, availableMb(usedMb = 9000, totalMb = 8000))

        assertEquals(20.0f, freeGb(usedGb = 44.0f, totalGb = 64.0f)!!, 0.001f)
        assertNull(freeGb(usedGb = null, totalGb = 64.0f))
    }

    // ── «غير مدعوم» ليست عطبًا ────────────────────────────────────────

    /**
     * الجهاز **بلا swap** ليس جهازًا تعذّرت قراءته: الحالة `Unsupported` ومعها السبب.
     * ولو مرّ الصفر لقرأ المستخدم «0 MB / 0 MB» وكأنّ لديه swap فارغًا.
     */
    @Test
    fun `a device with no swap is reported as unsupported, with its reason`() {
        val fact = deviceInfoSections(DeviceInfoSnapshot(swapTotalMb = 0)).flatMap { it.facts }
            .first { it.label == nd.max.R.string.devinfo_zram }
        assertEquals(DeviceInfoTrust.Unsupported, fact.trust)
        assertNull(fact.value)
        assertNotNull("السبب غير مُعلن", fact.noteRes)
    }

    @Test
    fun `a device with swap shows used over total`() {
        val fact = deviceInfoSections(DeviceInfoSnapshot(swapUsedMb = 2048, swapTotalMb = 4096))
            .flatMap { it.facts }.first { it.label == nd.max.R.string.devinfo_zram }
        assertEquals(DeviceInfoTrust.Live, fact.trust)
        assertEquals("2.0 GB / 4.0 GB", fact.value)
    }

    /**
     * **ونصف المقروء لا يُكمل بصفر.** كان `swapUsedMb ?: 0` يُطبع «0 MB / 4.0 GB» على جهاز
     * قُرئ حجمه ولم تُقرأ كميته المستخدمة — صفرٌ مُختلق بمعنى «لم تُقرأ»، وهو ما تمنعه القاعدة
     * الأولى في الملفّ نفسها. فحص `PHONE-INFO-02` هو الذي كشفه (كان يمرّ من قبل)، وهذا
     * الاختبار يمنع عودته: لا قيمة، وحالة «غير مقروء»، وسبب مكتوب.
     */
    @Test
    fun `a swap total without a used figure is a gap, not a zero`() {
        val fact = deviceInfoSections(DeviceInfoSnapshot(swapTotalMb = 4096))
            .flatMap { it.facts }.first { it.label == nd.max.R.string.devinfo_zram }
        assertEquals(DeviceInfoTrust.Unreadable, fact.trust)
        assertNull("كمية مستخدمة مُختلقة", fact.value)
        assertNotNull("غياب الكمية المستخدمة بلا سبب مكتوب", fact.noteRes)
    }

    /**
     * وأسرة الرسوم: البائع يُعرف من اسم الشريحة، وما لا يُعرف يُقال «غير مدعوم».
     * و`Exynos` ليست `Mali` تلقائيًّا — الأسرة تُقرأ من العتاد لا من التخمين.
     */
    @Test
    fun `the graphics family is inferred only where the vendor makes it certain`() {
        assertEquals("Adreno", gpuFamilyOf("Qualcomm Snapdragon 8 Gen 2"))
        assertEquals("Mali", gpuFamilyOf("MediaTek Dimensity 8300"))
        assertNull(gpuFamilyOf("Exynos 2100"))
        assertNull(gpuFamilyOf(null))

        val unsupported = deviceInfoSections(DeviceInfoSnapshot(chipset = "Exynos 2100"))
            .first { it.section == DeviceInfoSection.Gpu }.facts
            .first { it.label == nd.max.R.string.devinfo_gpu_family }
        assertEquals(DeviceInfoTrust.Unsupported, unsupported.trust)
        assertNull(unsupported.value)
    }

    // ── اللقطة الكاملة: لا مسار يُفقد ─────────────────────────────────

    /**
     * لقطة كاملة: **كل حقل عرض قيمة**. وهي البوّابة التي تكشف مسارًا يعود فارغًا بخطأ
     * (مثلاً مقارنة خاطئة تُسقط حقلًا ممتلئًا) — بينما الاختبارات أعلاه تقيس الغياب.
     */
    @Test
    fun `a fully populated device leaves no field empty`() {
        val facts = deviceInfoSections(populated()).flatMap { it.facts }
        // **والفراغ عَدَمُ الطرفين معًا:** بعد الجولة الجديدة صار للحقول قيمةٌ مترجَمة تُقرأ
        // من الموارد (‏`valueRes`: «مدعوم» · «USB» · «جيدة»)، فحقلٌ يحملها **ليس فارغًا**
        // — والفراغ هو الذي لا قيمة له ولا موردَ قيمة.
        val empty = facts.filter { it.value == null && it.valueRes == null }
        assertTrue("حقول بقيت فارغة على جهاز ممتلئ: ${empty.map { it.label }}", empty.isEmpty())
        assertTrue("قيمة صفرية تسللت: ${facts.filter { it.value == "0" }.map { it.label }}",
            facts.none { it.value == "0" })
        // ولا قيمة مكرّرة الصيغة: كل رقم يجب أن ينتهي بمنزلة عشرية واحدة على الأقل أو رقماً صحيحاً.
        assertTrue(facts.filter { it.unit == "%" }.all { it.value!!.toIntOrNull() != null })
    }

    @Test
    fun `network speed switches unit at one megabit per second`() {
        assertEquals("512 Kbps", formatKbps(512))
        assertEquals("2.0 Mbps", formatKbps(2048))
    }

    // ── `/proc/cpuinfo`: نصّ النواة يُحلّل بدالّة خالصة ────────────────

    /**
     * أوّل عطب حقيقي في هذا الملفّ: **صيغة الملفّ ليست واحدة**. على ARM تُسمّى الخصائص
     * `Features` وعلى x86 `flags`، والمخبأ `cache size` يظهر على x86 ويغيب على ARM.
     * فمحلّل يعرف اسمًا واحدًا يعمل على نصف الأجهزة ويصمت على نصفها.
     */
    @Test
    fun `the cpuinfo parser reads both the ARM and the x86 spelling`() {
        // والفواصل **مسافات حقيقية لا `\t` مكتوبة**: في النصّ الخامّ (`"""`) يبقى `\t`
        // حرفين لا محرف جدولة، فيسقط السطر بلا أن يسقط المحلّل — وهذا ما قاسه التشغيل.
        val arm = """
            processor      : 0
            BogoMIPS       : 38.40
            Features       : fp asimd evtstrm aes pmull sha1 sha2 crc32
            CPU implementer: 0x41
            Hardware       : MT6897
        """.trimIndent()
        val parsedArm = parseCpuInfo(arm)
        assertEquals("fp asimd evtstrm aes pmull sha1 sha2 crc32", parsedArm.features)
        assertNull("ARM لا يُعلن cache size في هذا الملفّ", parsedArm.cacheSize)

        val x86 = """
            processor  : 0
            flags      : fpu vme de pse tsc msr
            cache size : 8192 KB
        """.trimIndent()
        val parsedX86 = parseCpuInfo(x86)
        assertEquals("fpu vme de pse tsc msr", parsedX86.features)
        assertEquals("8192 KB", parsedX86.cacheSize)

        // وملفّ فارغ أو مبتور **لا يخترع نصًّا**: `null` صريحة.
        assertEquals(null, parseCpuInfo("").features)
        assertEquals(null, parseCpuInfo("processor : 0\nBogoMIPS : 38.40").features)
        assertEquals(null, parseCpuInfo("Features :\n").features)
    }

    /** المخبأ: مستويات النواة أولًا، ثم احتياطيّ `/proc/cpuinfo`، وإلّا `null` بلا اختراع. */
    @Test
    fun `cache is built from real levels, falls back once, and never invents a level`() {
        val levels = listOf(
            CacheLevel("1", "Data", "64K"),
            CacheLevel("1", "Instruction", "64K"),
            CacheLevel("2", "Unified", "512K"),
            CacheLevel("3", "Unified", "4M"),
        )
        assertEquals("L1d 64K · L1i 64K · L2 512K · L3 4M", formatCache(levels))
        // والترتيب يُحترم كما وصل — النواة تُعلن المستويات مرتّبة، ولا يُعاد ترتيبها تخمينًا.
        assertEquals("L2 512K", formatCache(listOf(CacheLevel("2", "Unified", "512K"))))
        // ومستوى بلا حجم أو بلا رقم لا يُكتب صفًّا ناقصًا.
        assertEquals(null, formatCache(listOf(CacheLevel(null, "Data", "32K"))))
        assertEquals(null, formatCache(listOf(CacheLevel("1", "Data", null))))
        // والاحتياط يعمل **فقط** حين لا مستوى واحد مقروءًا.
        assertEquals("8192 KB", formatCache(emptyList(), fallbackSize = "8192 KB"))
        assertEquals("L1d 32K", formatCache(listOf(CacheLevel("1", "Data", "32K")), "8192 KB"))
        assertNull(formatCache(emptyList()))
        assertNull(formatCache(emptyList(), fallbackSize = "  "))
    }

    /**
     * الأنماط المدعومة: مرتّبة بلا تكرار — **ولا قائمة افتراضية**. جهاز لا يُعلن نمطًا
     * يُقال له غير مقروء، ولا تُضاف ٦٠/٩٠/١٢٠ لم تُقرأ (وهو الفرق عن
     * `getSupportedRefreshRates` التي تُضيفها لمنتقي الإعداد).
     */
    @Test
    fun `declared refresh rates are sorted, deduplicated, and never invented`() {
        assertEquals("60 · 90 · 120", formatHz(listOf(120, 60, 90, 60)))
        assertNull(formatHz(emptyList()))
        assertNull(formatHz(listOf(0, -1)))

        val none = deviceInfoSections(DeviceInfoSnapshot())
            .first { it.section == DeviceInfoSection.Display }.facts
            .first { it.label == nd.max.R.string.devinfo_refresh_supported }
        assertEquals(DeviceInfoTrust.Unreadable, none.trust)
        assertNull(none.value)
    }

    // ── الصفر: قراءة في موضع، وغياب في آخر ────────────────────────────

    /**
     * دورات الشحن **وصفرها قراءة** («بطارية جديدة»)، وصحة السعة المعلنة صفرًا ليست صحة.
     * وهذان الصفّان يكشفان الانزلاق الأكثر شيوعًا: قاعدة «الصفر غياب» تُعمَّم فتمحو قراءةً.
     */
    @Test
    fun `a zero cycle count is a reading, a zero state of health is not`() {
        val facts = deviceInfoSections(DeviceInfoSnapshot(batteryCycleCount = 0, batteryHealthPercent = 0))
            .flatMap { it.facts }
        val cycles = facts.first { it.label == nd.max.R.string.devinfo_battery_cycles }
        assertEquals("0", cycles.value)
        assertEquals(DeviceInfoTrust.Live, cycles.trust)
        val health = facts.first { it.label == nd.max.R.string.devinfo_battery_health }
        assertNull(health.value)
        assertEquals(DeviceInfoTrust.Unreadable, health.trust)
    }

    /** وقراءة حرارة صفرية ليست "٠°C" — سنسور لا يعمل يبدو كغرفة مجمّدة. */
    @Test
    fun `a zero temperature is reported as unread, not as freezing`() {
        val thermal = deviceInfoSections(DeviceInfoSnapshot(cpuTempC = 0, batteryTempC = 0f))
            .first { it.section == DeviceInfoSection.Thermal }.facts
        assertNull(thermal.first { it.label == nd.max.R.string.devinfo_temp_cpu }.value)
        assertEquals(DeviceInfoTrust.Unreadable, thermal.first { it.label == nd.max.R.string.devinfo_temp_cpu }.trust)
    }

    /**
     * وكل قسم **له باب** — اثنا عشر قسمًا واثنا عشر بابًا، ولا استثناء للنظرة العامة
     * (`PHONE-INFO-02`): الدالة صارت غير قابلة للعدم، وهذا الاختبار هو بوّابتها.
     *
     * **وأُنشئ القسم الثاني عشر (الكاميرا) في جولة «معلومات أكثر من الصور»:** بلا شاشة تحكّم
     * مالكة، فصارت `Diagnostics` بيتَ أربعة أقسام لا ثلاثة — والحدّ صار ٤ وهو مُعلَن لا مُخفَّف
     * بصمت (سابقه كان ٣ على أحد عشر قسمًا).
     *
     * **والقسم الثالث عشر (الصوت، `AS-01`) يحتاج شاشةَ تحكّم مالكة، ولا بديل:** نقلُ جرد الصوت
     * إلى هنا بلا شاشةٍ تتحكّم فيه كان يُنتج قسمًا يصف ولا يفعل، فبُني سطح `AudioStudio`
     * (‏`AS-02`+`AS-03`) وصار **ابنةَ `AudioHub` في `MaxDestination.All`** — وعائد الباب هنا
     * يُقاس في اختبارات التسعة/التقارب لا مكتوبًا بالسليقة.
     */
    @Test
    fun `all thirteen sections open a real screen, the overview included`() {
        val doors = DeviceInfoSection.entries.associateWith { maxDeviceInfoSource(it) }
        assertEquals("قسم بلا باب", DeviceInfoSection.entries.size, doors.size)
        assertEquals(13, DeviceInfoSection.entries.size)
        // ولا بابان مختلفان يعودان لنفس الشاشة **هنا** — إلا الأربعة التي بيتها واحد فعلًا
        // (النظرة العامة والنظام والمستشعرات والكاميرا ⇒ التشخيص)، وهي مُعلنة لا صدفة.
        val grouped = doors.values.groupingBy { it.route }.eachCount()
        assertTrue(
            "شاشة واحدة صارت بابًا لأكثر من أربعة أقسام: $grouped",
            grouped.values.all { it <= 4 },
        )
    }

    // ── CPU Info: سطر كل نواة مقيس ─────────────────────────────

    /**
     * **النواة المطفأة لا تُعرض بتردّدها.** تُقرأ `0` على كثير من الأنوية، و`0.00 GHz`
     * يقول «توقّفت عند الصفر» بدل «لا تعمل الآن» — وطلب المالك نفسه: «لا تعرض 0 أو قيمة
     * وهمية عند فشل القراءة».
     */
    @Test
    fun `a parked core says it is parked and prints no frequency`() {
        val line = formatCoreLine(
            CpuCoreReading(id = 7, online = false, currentMhz = 0, maxMhz = 2210),
            offlineLabel = "Off",
        )
        assertEquals("#7 · Off", line)
        assertFalse("تردّد مُختلق لنواة مطفأة", line.contains("GHz"))
        assertFalse(line.contains("0.00"))
    }

    /** ونواة متصلة لا تُعلن تردّدًا تُعرض بما يُقرأ منها فقط — لا بأصفار تُكمل السطر. */
    @Test
    fun `a running core prints only what was actually read`() {
        assertEquals(
            "#0 · 2.40/3.35 GHz · policy0 · schedutil",
            formatCoreLine(
                CpuCoreReading(0, online = true, currentMhz = 2400, maxMhz = 3350, cluster = "policy0", governor = "schedutil"),
                "Off",
            ),
        )
        // والحالي وحده يُقال؛ والأقصى وحده **يُحذف** لأنه لا يقول شيئًا عن الحال.
        assertEquals(
            "#1 · 1.80 GHz",
            formatCoreLine(CpuCoreReading(1, online = true, currentMhz = 1800, maxMhz = 0), "Off"),
        )
        assertEquals(
            "#2 · policy4",
            formatCoreLine(CpuCoreReading(2, online = true, currentMhz = 0, maxMhz = 2210, cluster = "policy4"), "Off"),
        )
        assertEquals("#3", formatCoreLine(CpuCoreReading(3, online = true), "Off"))
    }

    /**
     * وفي الجهاز الممتلئ: **لا تردّد صفري في أي قسم** — الصيغة الواحدة (`formatDouble` على
     * `GHz`) تعني أن العطب لو عاد لظهر هنا، لا في الحقل الذي عاد فيه وحده.
     */
    @Test
    fun `no frequency anywhere carries a fabricated zero`() {
        val frequencies = deviceInfoSections(populated()).flatMap { it.facts }
            .filter { it.unit == "GHz" }
            .mapNotNull { it.value }
        assertTrue("حقول تردّد بلا قراءة؟ فالفحص لا يقيس شيئًا", frequencies.isNotEmpty())
        assertTrue("تردّد صفري معروض: $frequencies", frequencies.none { it.startsWith("0.00") || it == "0" })

        // وسطر النواة نفسه: يُعرض، ومعه سببه، وحالته حيّة لا "غير مقروء".
        val cores = deviceInfoSections(DeviceInfoSnapshot(cpuCoreLines = listOf("#7 · Off")))
            .flatMap { it.facts }
            .first { it.label == nd.max.R.string.devinfo_cpu_core_lines }
        assertEquals("#7 · Off", cores.value)
        assertEquals(DeviceInfoTrust.Live, cores.trust)
        assertNotNull("سطر النواة بلا شرح لصيغته", cores.noteRes)
    }

    // ── `DI-01`: التقليب — الصفحة من المسار، والترقيم هو موضع القسم ──

    /**
     * العلاقة التي يقوم عليها التقليب: **رقم الصفحة هو موضع القسم في القائمة المُنتَجة**.
     * ولو انفصلا لفتح مسار `?section=thermal` صفحةً غير بطاقة الحرارة — وهو عطب لا يرمي
     * ولا يظهر في رسم: يُعرض قسمٌ خاطئ بصمت.
     */
    @Test
    fun `a section's page is its index in the produced section list`() {
        val sections = deviceInfoSections(DeviceInfoSnapshot())
        DeviceInfoSection.entries.forEach { section ->
            assertEquals(
                "صفحة ${section.wireKey} ليست موضعها في القائمة",
                section,
                sections[deviceInfoPageOf(section.wireKey)].section,
            )
        }
    }

    @Test
    fun `an unknown section key opens the overview rather than a page that does not exist`() {
        assertEquals(DeviceInfoSection.Overview.ordinal, deviceInfoPageOf(null))
        assertEquals(DeviceInfoSection.Overview.ordinal, deviceInfoPageOf(""))
        // ومفتاحٌ لا قسمَ له لا يُفتح على فضاء — يسقط إلى «نظرة عامة» كما تسقط
        // `deviceInfoSectionOf` نفسها. **و`camera` لم يعد مثالَ المجهول** منذ صار قسمًا
        // حقيقيًّا (تكملة ٢٢٥)، فصار المثال `camera_lens` — والمُقاس هو القاعدة لا الكلمة.
        assertEquals(DeviceInfoSection.Overview.ordinal, deviceInfoPageOf("camera_lens"))
        // والمفتاح الجديد يفتح صفحته لا النظرة العامة — فالقاعدة سليمة والقسم مُضاف لا مُستبدل.
        assertEquals(DeviceInfoSection.Camera.ordinal, deviceInfoPageOf("camera"))
    }

    // ── `DI-03`: ما هو مقروء أصلًا يُعرض (ولا قراءة جديدة) ────────────

    private fun sectionOf(snapshot: DeviceInfoSnapshot, section: DeviceInfoSection) =
        deviceInfoSections(snapshot).first { it.section == section }

    private fun thermalCardOf(snapshot: DeviceInfoSnapshot, titleRes: Int) =
        sectionOf(snapshot, DeviceInfoSection.Thermal).cards.first { it.titleRes == titleRes }

    @Test
    fun `every reported sensor becomes one row, and its title is the hardware name`() {
        val rows = sectionOf(inventoried(), DeviceInfoSection.Sensors).rows
        assertEquals(2, rows.size)
        assertEquals("MPU6500 Acceleration Sensor", rows[0].title)
        // ومستشعر بلا اسم يأخذ اسم صنفه — فلا يظهر صفّ بلا عنوان.
        assertEquals("Environment", rows[1].title)
    }

    @Test
    fun `a sensor row carries every announced field and omits only the absent ones`() {
        val rows = sectionOf(inventoried(), DeviceInfoSection.Sensors).rows
        // الحقول التسعة: الصنف · النوع الرقميّ · البائع · المدى · الدقّة · الاستهلاك ·
        // التأخير · الإيقاظ — والاسم عنوان الصفّ لا يكرّر في سطره.
        assertEquals(
            "Motion · 1 · InvenSense · ±39.2 · 0.00240 · 0.15 mA · 5000 us · wake-up",
            rows[0].detail,
        )
        // و«صفر المنصّة = غير معلَن»: لا `±0` ولا `0.0 mA` ولا `0 us` — السطر يقول ما قيل فقط.
        assertEquals("Environment · 6", rows[1].detail)
    }

    @Test
    fun `sensor rows stay in the sensor section and nowhere else`() {
        val others = deviceInfoSections(inventoried())
            .filter { it.section != DeviceInfoSection.Sensors }
            .flatMap { it.rows }
        assertTrue("صفوف استُنسخت في أقسام أخرى: ${others.map { it.title }}", others.isEmpty())
    }

    @Test
    fun `the kinds line names what is present, and a good light reading is not noted as a fault`() {
        val sensors = sectionOf(inventoried(), DeviceInfoSection.Sensors)
        val kinds = sensors.facts.first { it.label == nd.max.R.string.max_sensor_kinds }
        assertEquals("Motion · Environment", kinds.value)
        assertEquals(DeviceInfoTrust.Live, kinds.trust)

        val light = sensors.facts.first { it.label == nd.max.R.string.max_sensor_light }
        assertEquals("12.5", light.value)
        assertEquals("lux", light.unit)
        assertEquals(DeviceInfoTrust.Live, light.trust)
        assertNull("قراءة ناجحة تُشرح كأنها عطب", light.noteRes)
    }

    @Test
    fun `the absent sensor categories are named, and never invented`() {
        fun absentFact(snapshot: DeviceInfoSnapshot) = sectionOf(snapshot, DeviceInfoSection.Sensors)
            .facts.find { it.label == nd.max.R.string.max_sensor_kinds_absent }

        // الحاضران MOTION وENVIRONMENT ⇒ الأربعة الباقية بأسمائها ومرتّبة بترتيب التعداد.
        val absent = absentFact(inventoried())
        assertEquals("Position · Body · Unpositioned · Other", absent?.value)
        assertEquals(DeviceInfoTrust.Live, absent?.trust)
        assertEquals("SensorManager", absent?.source)

        // وكل الأصناف حاضرة ⇒ **لا صفّ** (لا رقم صفر معروض كأنه غياب).
        val allKinds = DeviceInfoSnapshot(sensorKinds = SensorInventory.Kind.entries.toList())
        assertNull(absentFact(allKinds))

        // ولم تُقرأ المستشعرات أصلًا ⇒ لا تُقال «كلها غائبة»: هذا حكم على الجهاز لم يُقس.
        assertNull(absentFact(DeviceInfoSnapshot()))
    }

    @Test
    fun `an absent sensor is unsupported and a timeout unreadable, and neither is zero lux`() {
        fun lightFactOf(state: SensorInventory.ReadingState) = sectionOf(
            DeviceInfoSnapshot(sensorLight = SensorInventory.LightReading(lux = null, state = state)),
            DeviceInfoSection.Sensors,
        ).facts.first { it.label == nd.max.R.string.max_sensor_light }

        val absent = lightFactOf(SensorInventory.ReadingState.ABSENT)
        assertEquals(DeviceInfoTrust.Unsupported, absent.trust)
        assertNull(absent.value)

        val timedOut = lightFactOf(SensorInventory.ReadingState.UNREADABLE)
        assertEquals(DeviceInfoTrust.Unreadable, timedOut.trust)
        assertNull(timedOut.value)
    }

    @Test
    fun `the thermal map is one row per zone, and its states are three not two`() {
        val rows = thermalCardOf(inventoried(), nd.max.R.string.detail_thermal_map).rows
        assertEquals(3, rows.size)
        // قُرئت: صنفها وقياسها.
        assertEquals("cpu · 42 °C", rows[0].detail)
        // مطفأة: النواة تقول إنها لا تعمل — حالة معلنة لا عجز قراءة.
        assertEquals("off", rows[1].detail)
        // وتقرأ ولا تُعطي: غياب يُقال (وهو موضع `—` في الرسم)، ولا يُكمَّل بصفر.
        assertNull(rows[2].detail)
    }

    @Test
    fun `an off zone falls back to its category when the UI has no word for off`() {
        fun detailOf(zone: ThermalZoneRow, offLabel: String?) = sectionOf(
            DeviceInfoSnapshot(thermalZones = listOf(zone), thermalOffLabel = offLabel),
            DeviceInfoSection.Thermal,
        ).cards.first { it.titleRes == nd.max.R.string.detail_thermal_map }.rows.single().detail

        val off = ThermalZoneRow("gpu-0-0-us", "gpu", 0, enabled = false)
        assertEquals("off", detailOf(off, "off"))
        // وبلا كلمة في الواجهة يُقال صنفها — لا فراغ ولا صفر.
        assertEquals("gpu", detailOf(off, null))
        // ومنطقة لا صنف لها ولا كلمة: غياب معلن.
        assertNull(detailOf(ThermalZoneRow("z", "", 0, enabled = false), null))
    }

    @Test
    fun `trip points are attributed to their own zone, not to the device in general`() {
        val trips = thermalCardOf(inventoried(), nd.max.R.string.detail_thermal_trips)
        assertEquals(
            listOf("cpu-0-0-us · passive", "cpu-0-0-us · critical"),
            trips.rows.map { it.title },
        )
        assertEquals(listOf("95 °C", "115 °C"), trips.rows.map { it.detail })
    }

    @Test
    fun `a cooling device shows its state against its ceiling, and hides both when unannounced`() {
        val cooling = thermalCardOf(inventoried(), nd.max.R.string.detail_thermal_cooling)
        assertEquals("thermal-fan", cooling.rows[0].title)
        assertEquals("3/10", cooling.rows[0].detail)
        // الحالة غير معلنة (`-1`) والسقف لم يُعلَن (`0`): لا «-1/0» ولا «0/0» — حتى لو بُني
        // الصفّ بيدٍ على النوع المفتوح، فالقاعدة تُطبّق مرّة ثانية عند العرض.
        assertNull(cooling.rows[1].detail)
        assertNull(coolingStateOf(-1, 0))
        assertNull(coolingStateOf(null, 10))
        assertNull(coolingStateOf(3, null))

        assertEquals(10, coolingRow("x", 3, 10).max)
        assertEquals(3, coolingRow("x", 3, 10).current)
        // وصفر الحالة **قراءة معروضة** (كصفر البطارية): لا يمرّ على تصفيةٍ تُسقطه.
        assertEquals(0, coolingRow("x", 0, 10).current)
        // أمّا `-1` فليست حالة: لا «-1/10».
        assertNull(coolingRow("x", -1, 10).current)
        assertNull(coolingRow("x", 0, 0).max)
    }

    @Test
    fun `a card with no rows is dropped, not shown as a title with nothing under it`() {
        assertTrue(sectionOf(DeviceInfoSnapshot(), DeviceInfoSection.Thermal).cards.isEmpty())
    }

    @Test
    fun `an empty snapshot exposes no invented rows either`() {
        val rows = deviceInfoSections(DeviceInfoSnapshot()).flatMap { it.rows }
        assertTrue("صفوف مخترعة من لقطة فارغة: ${rows.map { it.title }}", rows.isEmpty())
    }

    /**
     * جهازٌ قرأنا جرده فعلًا: مستشعرَيه ومناطقه الحرارية وأجهزة تبريده — وتقوله **على شكل
     * `Report` و`ThermalZoneInfo`** بلا قراءة جهاز (وهو نصّ قبول `DI-03`).
     */
    private fun inventoried() = populated().copy(
        sensorCount = 2,
        sensorWakeUpCount = 1,
        sensorKindCount = 2,
        sensorItems = listOf(
            SensorInventory.Item(
                name = "MPU6500 Acceleration Sensor",
                vendor = "InvenSense",
                typeId = 1,
                kind = SensorInventory.Kind.MOTION,
                powerMilliAmp = 0.15f,
                maxRange = 39.2f,
                resolution = 0.0024f,
                minDelayUs = 5000,
                isWakeUp = true,
            ),
            SensorInventory.Item(
                name = "",
                vendor = "",
                typeId = 6,
                kind = SensorInventory.Kind.ENVIRONMENT,
                powerMilliAmp = 0f,
                maxRange = 0f,
                resolution = 0f,
                minDelayUs = 0,
                isWakeUp = false,
            ),
        ),
        sensorKinds = listOf(SensorInventory.Kind.MOTION, SensorInventory.Kind.ENVIRONMENT),
        // والأسماء **لكل الأصناف** كما تفعل الشاشة (`sensorKindText` على `Kind.entries`) — فلا
        // يسقط صنف غائب إلى معرّفه الإنجليزيّ داخل واجهة عربية.
        sensorKindLabels = mapOf(
            SensorInventory.Kind.MOTION to "Motion",
            SensorInventory.Kind.ENVIRONMENT to "Environment",
            SensorInventory.Kind.POSITION to "Position",
            SensorInventory.Kind.BODY to "Body",
            SensorInventory.Kind.UNPOSITIONED to "Unpositioned",
            SensorInventory.Kind.OTHER to "Other",
        ),
        sensorWakeUpLabel = "wake-up",
        sensorLight = SensorInventory.LightReading(
            lux = 12.5f,
            state = SensorInventory.ReadingState.REPORTED,
        ),
        thermalZones = listOf(
            ThermalZoneRow(
                label = "cpu-0-0-us",
                category = "cpu",
                celsius = 42,
                enabled = true,
                trips = listOf(ThermalTripRow(95, "passive"), ThermalTripRow(115, "critical")),
            ),
            // مطفأة: النواة تُعلنها، وقراءتها صفر — ولا تُخلط الحالتان.
            ThermalZoneRow(label = "gpu-0-0-us", category = "gpu", celsius = 0, enabled = false),
            // حيّة ولا تُعطي قراءة: `0` (وهو الغياب نفسه في `countable`).
            ThermalZoneRow(label = "battery", category = "battery", celsius = 0, enabled = true),
        ),
        thermalOffLabel = "off",
        coolingDevices = listOf(CoolingRow("thermal-fan", 3, 10), CoolingRow("thermal-pid", -1, 0)),
    )

    // ── القيمي المُغذّي: جهاز كامل معلَن ──────────────────────────────

    private fun populated() = DeviceInfoSnapshot(
        deviceName = "POCO X6 Pro",
        manufacturer = "Xiaomi",
        chipset = "MediaTek Dimensity 8300",
        chipsetVendor = "mediatek",
        android = "14",
        sdk = 34,
        kernel = "5.15.78",
        abis = "arm64-v8a",
        fingerprint = "Xiaomi/duchamp/duchamp:14/UKQ1/OS2.0:user/release-keys",
        securityPatch = "2026-08-01",
        selinux = "Enforcing",
        appVersion = "v1.0",
        cpuArch = "aarch64",
        cpuCache = "L1d 64K · L1i 64K · L2 512K · L3 4M",
        cpuFeatures = "fp asimd evtstrm aes pmull sha1 sha2 crc32",
        gpuGles = "3.2",
        batteryHealthPercent = 96,
        batteryCycleCount = 120,
        batteryTechnology = "Li-ion",
        displaySupportedHz = listOf(60, 90, 120),
        cpuCores = 8,
        cpuCoresOnline = 7,
        cpuCoreLines = listOf(
            "#0 · 3.35/3.35 GHz · policy0 · schedutil",
            "#7 · Off",
        ),
        cpuClusters = listOf("policy0: 0-3 · schedutil · 2200 MHz"),
        cpuMinMhz = 400,
        cpuFreqMhz = 2400,
        cpuCeilingMhz = 3350,
        cpuLoadPercent = 37,
        gpuLoadPercent = 22,
        gpuFreqMhz = 260,
        gpuCeilingMhz = 754,
        gpuMaxSupportedMhz = 1400,
        ramUsedMb = 5300,
        ramTotalMb = 8192,
        swapUsedMb = 2048,
        swapTotalMb = 4096,
        storageUsedGb = 44.0f,
        storageTotalGb = 256.0f,
        batteryPercent = 63,
        batteryStatus = "Charging",
        batteryTempC = 31.5f,
        batteryVoltageV = 4.35f,
        batteryCurrentMa = 1800,
        powerWatt = 7.8f,
        displayWidth = 2400,
        displayHeight = 1080,
        displayDensityDpi = 420,
        displayRefreshHz = 120,
        cpuTempC = 42,
        gpuTempC = 40,
        skinTempC = 33,
        thermalZoneCount = 42,
        sensorCount = 31,
        sensorWakeUpCount = 6,
        sensorKindCount = 18,
        downloadKbps = 2048,
        uploadKbps = 512,
        // ══ حقول الجولة الجديدة — حتى يبقى «لا حقل فارغ» حكمًا على المسار لا على النسيان ══
        brand = "POCO",
        model = "2311DRK48G",
        deviceCodename = "duchamp",
        board = "duchamp",
        hardware = "mt6897",
        bootloader = "unknown",
        baseband = "MOLY.NR15.W23.R1",
        buildId = "UKQ1.230804.001",
        buildIncremental = "OS2.0.1.0.UMLMIXM",
        buildType = "user",
        buildMillis = 1_755_000_000_000L,
        androidCodename = "UpsideDownCake",
        uptimeSeconds = 86_400L + 5 * 3_600L + 12 * 60L,
        bootMillis = 1_755_000_000_000L,
        timeZoneId = "Africa/Cairo",
        localeName = "en_US",
        vmLabel = "2.1.0 · Dalvik",
        webViewVersion = "131.0.6778.200",
        playServicesVersion = "24.30.15",
        treble = true,
        seamlessUpdates = true,
        dynamicPartitions = true,
        drmSecurityLevel = "L1",
        drmVendor = "Google",
        drmVersion = "19.0.1",
        drmAlgorithms = "AES/CBC/NoPadding",
        drmHdcp = "HDCP 2.3",
        cpuImplementer = "0x41",
        cpuPart = "0xd05",
        cpuRevision = "1",
        gpuRenderer = "Mali-G610 MC6",
        gpuRendererVendor = "ARM",
        gpuDriver = "v1.r29p0",
        vulkanVersion = "1.3.231",
        vulkanLevel = "1",
        ramCachedMb = 2100,
        ramBuffersMb = 350,
        javaHeapMaxMb = 512,
        storagePartitions = listOf(DeviceInfoPartitionRow("/data", 44.0f, 220.0f)),
        batteryHealthCode = 2,
        batteryPluggedCode = 2,
        batteryDesignCapacityMah = 5000,
        batteryChargeCounterMah = 3150,
        displayCutoutTopPx = 130,
        displayHdrTypes = listOf(2, 3),
        displayWideGamut = true,
        displayBrightnessPercent = 42,
        displayAutoBrightness = true,
        displayTimeoutMinutes = 1.0f,
        fontScale = 1.15f,
        orientationCode = 1,
        adbEnabled = true,
        developerOptions = true,
        capabilities = listOf(
            DeviceInfoChipRow(nd.max.R.string.devinfo_cap_nfc, CapabilityState.Supported),
        ),
        cameras = listOf(
            DeviceCameraInfo(
                facing = CameraFacing.Back,
                hardwareLevel = "LEVEL_3",
                apertures = listOf(1.7f),
                focalLengthsMm = listOf(4.94f),
                sensorSizeMm = 6.55f to 4.92f,
                pixelArray = 4096 to 3072,
                sensorOrientation = 90,
                maxDigitalZoom = 8.0f,
                flashAvailable = true,
                jpegMax = 4096 to 3072,
                jpegCount = 12,
                capabilities = listOf("BURST_CAPTURE", "YUV_REPROCESSING"),
                colorFilterArrangement = 3,
            ),
        ),
        // ══ والصوت (`AS-01`): الجهاز الممتلئ يُعلن مخرجًا وأجهزةً ومؤثّرًا ══
        // وبلا هذه الأسطر تبقى حقول القسم الثالث عشر بلا قيمة، فيسقط «لا حقل فارغ» — وهو ما
        // أسقطه CI فعلًا: الاختبار كان يقيس نسيان اللقطة لا عطب المسار.
        audio = AudioInventorySnapshot(
            output = AudioOutputCapabilities(
                sampleRateHz = 48000,
                framesPerBuffer = 192,
                devices = audioDevices(),
            ),
            devices = audioDevices(),
            effects = listOf(
                AudioEffectInfo(
                    // ومعرّف النوع من `AudioEffect.java` في AOSP (نفس ما يُقاس في `AudioCapabilitiesTest`).
                    name = "Equalizer",
                    typeUuid = "0bed4300-ddd6-11db-8f34-0002a5d5c51b",
                    implementor = "AOSP",
                    connectMode = "Insert",
                ),
            ),
        ),
    )

    /** جهاز مخرج كما تُعلنه المنصّة — يُقرأ منه الاسم والقنوات والمعدّل. */
    private fun audioDevices() = listOf(
        AudioDeviceDescriptor(
            id = 2,
            productName = "Speaker",
            typeCode = 2,
            isSink = true,
            sampleRatesHz = listOf(48000),
            channelCounts = listOf(2),
            encodings = listOf(1),
        ),
    )

    @Test
    fun `the exposed panel matches the declared section list one to one`() {
        // حارس التكرار: قائمة الأقسام تُقرأ من الـenum وحده، فلا يظهر قسم مرّتين ولا يُنسى قسم.
        val sections = deviceInfoSections(populated())
        assertEquals(sections.size, sections.map { it.section }.distinct().size)
        assertFalse("Overview ليست أولًا", sections.first().section != DeviceInfoSection.Overview)
    }
}
