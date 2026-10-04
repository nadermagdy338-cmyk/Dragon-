/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **بنّاؤو الحقول**: كل قرار «أي حقل، وبأي وحدة، ومتى يُقال غير مقروء».
 *
 * **ولماذا فُصل عن النموذج:** سقف `code_health` ألف سطر للملف، وكان `DeviceInfoModel.kt`
 * عند ٩٩١ — فأيّ حقل جديد كان سيُخرجه. والنقل هنا **حِرفيّ** (النصّ نفسه انتقل حرفيًّا
 * من الملفّين إلى ثلاث طبقات في الحزمة نفسها)، فلا قاعدة تغيّرت ولا اختبار تحرّك:
 * الأنواع واللقطة في `DeviceInfoModel`، والصياغات الصافية في `DeviceInfoFormat`،
 * وما بينهما (المجموعات والبواني) هنا.
 *
 * وقاعدة الملفّ الأصل سارية هنا حرفيًّا: `null` تعني «لا قيمة»، ولا يُكتب صفر مكان
 * قيمة لم تُقرأ، وكل حقل يُبنى من بوّابة واحدة.
 */
package nd.max.ui.subscreens

import androidx.annotation.StringRes
import nd.max.R
import nd.max.core.platform.SensorInventory

/**
 * الصفوف التي عنوانها من الجهاز. وفي هذه المرحلة **المستشعرات وحدها**: الجرد كاملًا كان
 * يُعرض عدًّا (٣٤ مستشعرًا) وكان أسماءها وأصنافها ومداها مُهمَلة وهي مقروءة أصلًا.
 */
internal fun rowsOf(section: DeviceInfoSection, s: DeviceInfoSnapshot): List<DeviceInfoRow> =
    when (section) {
        DeviceInfoSection.Sensors -> sensorRows(s)
        else -> emptyList()
    }

/**
 * البطاقات الإضافية. والحرارة وحدها أكثر من فكرة (مناطق · تخفيف · تبريد)، و`System` يحمل
 * بطاقة DRM، و`Camera` بطاقتها لكل عدسة — وكل بطاقة **تُحذف إن كانت فارغة**.
 */
internal fun cardsOf(section: DeviceInfoSection, s: DeviceInfoSnapshot): List<DeviceInfoCard> =
    when (section) {
        DeviceInfoSection.Thermal -> thermalCards(s)
        DeviceInfoSection.System -> listOfNotNull(drmCard(s))
        DeviceInfoSection.Camera -> cameraCards(s)
        else -> emptyList()
    }

/**
 * صفوف خصائص العتاد — **في الشبكة وحدها**: هي فهرس «ماذا يملك جهازك»، وما عداها
 * أقسامُ قياسٍ لا فهرسُ ملكية.
 */
internal fun chipsOf(section: DeviceInfoSection, s: DeviceInfoSnapshot): List<DeviceInfoChipRow> =
    when (section) {
        DeviceInfoSection.Network -> s.capabilities
        else -> emptyList()
    }

// ── رأس القسم: انتقلت كتلة البناء (المقياس والبلاطات وحالات الثقة) حرفيًّا إلى
// `DeviceInfoHeroModel.kt` (تكملة ٢٢٥، سقف الأسطر) — والقياس كما كان: `DeviceInfoFactsTest`. ──

internal fun factsOf(section: DeviceInfoSection, s: DeviceInfoSnapshot): List<DeviceInfoFact> =
    when (section) {
        // والكاميرا: عدُّ العدسات هنا، وتفاصيل كل عدسة في بطاقتها — لا كومةُ حقولٍ في قائمة.
        DeviceInfoSection.Camera -> listOf(
            count(R.string.devinfo_camera_count, s.cameras.size, source = CAMERA2),
        )
        DeviceInfoSection.Overview -> overviewFacts(s)
        DeviceInfoSection.Cpu -> cpuFacts(s)
        DeviceInfoSection.Gpu -> gpuFacts(s)
        DeviceInfoSection.Memory -> memoryFacts(s)
        DeviceInfoSection.Storage -> storageFacts(s)
        DeviceInfoSection.Battery -> batteryFacts(s)
        DeviceInfoSection.Display -> displayFacts(s)
        DeviceInfoSection.Thermal -> thermalFacts(s)
        DeviceInfoSection.Sensors -> sensorFacts(s)
        DeviceInfoSection.System -> systemFacts(s)
        DeviceInfoSection.Network -> networkFacts(s)
        DeviceInfoSection.Audio -> audioFacts(s)
    }

// ── (١) نظرة عامة ────────────────────────────────────────────────────────
private fun overviewFacts(s: DeviceInfoSnapshot) = listOf(
    text(R.string.device_name, s.deviceName),
    text(R.string.devinfo_manufacturer, s.manufacturer),
    // والهوية الموسّعة (الجولة الجديدة): ما تُعلنه `Build` عن الجهاز نفسه — العلامة والطراز
    // واسم الرمز واللوحة والمكوّنات، وهي أوّل ما تُقارن به الأجهزة بعضها.
    text(R.string.devinfo_brand, s.brand),
    text(R.string.devinfo_model, s.model),
    text(R.string.devinfo_device_codename, s.deviceCodename),
    text(R.string.devinfo_board, s.board),
    text(R.string.devinfo_hardware, s.hardware),
    text(R.string.str_chipset, s.chipset),
    text(R.string.android_version, s.android),
    text(R.string.kernel_version, s.kernel),
    // تاريخ البناء و`uptime` ولحظة الإقلاع: ثلاثةٌ من ساعةٍ واحدة، والصياغة في `DeviceInfoFormat`
    // بمنطقةٍ مُمرَّرة فلا تتبدّل صورتها بين جهازَين.
    text(R.string.devinfo_build_date, formatEpochMillis(s.buildMillis ?: 0L, s.timeZoneId), source = BUILD_FIELD),
    text(R.string.devinfo_uptime, formatUptime(s.uptimeSeconds ?: 0L), source = PROC_UPTIME),
    text(R.string.devinfo_boot_time, formatEpochMillis(s.bootMillis ?: 0L, s.timeZoneId), source = PROC_UPTIME),
    memory(R.string.ram, s.ramTotalMb),
    storage(R.string.devinfo_storage_total, s.storageTotalGb),
    resolution(R.string.devinfo_resolution, s.displayWidth, s.displayHeight),
    // ومعدّل التحديث **هنا وفي قسم الشاشة**: هو من أوّل ما يُسأل عنه عن الجهاز، وهو قراءة
    // واحدة تُعرض في موضعين — لا قراءتان من مصدرين.
    refreshRate(s.displayRefreshHz),
    batteryLevel(s.batteryPercent),
    temperature(R.string.devinfo_temp_cpu, s.cpuTempC),
)

// ── (٢) المعالج ──────────────────────────────────────────────────────────
private fun cpuFacts(s: DeviceInfoSnapshot) = listOf(
    text(R.string.str_chipset, s.chipset),
    // وبائع الشريحة هو **قيمة صفّ الكتالوج** لا نصّ العرض: رمز تعلنه شريحتان لا يقول بائعًا.
    text(R.string.devinfo_soc_vendor, s.chipsetVendor),
    text(R.string.devinfo_arch, s.cpuArch, source = UNAME),
    text(R.string.instruction_sets, s.abis),
    count(R.string.devinfo_cpu_cores, s.cpuCores),
    count(R.string.devinfo_cpu_cores_online, s.cpuCoresOnline, source = CPU_ONLINE),
    // ولكل نواة سطرها: المُعرّف والحالة والتردد والعنقود والحاكم — وسطر الحالة هذا هو
    // ما يمنع قراءة «متوقفة» كأنها «تردّد صفر»، وهو الفرق الذي يُقاس في `formatCoreLine`.
    DeviceInfoFact(
        label = R.string.devinfo_cpu_core_lines,
        value = s.cpuCoreLines.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        trust = if (s.cpuCoreLines.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = CPU_ONLINE,
        noteRes = R.string.devinfo_cpu_core_lines_note.takeIf { s.cpuCoreLines.isNotEmpty() },
    ),
    // واحدة لكل عنقود: اسم العنقود وحاكمه وأرضيته وسقفه. وتُبنى في الشاشة من
    // `CpuHardwareBackend.policies()` — نفس مصدر شاشة الأنوية، فلا مصدران لمدى واحد.
    DeviceInfoFact(
        R.string.devinfo_cpu_clusters,
        value = s.cpuClusters.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        trust = if (s.cpuClusters.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = CPU_POLICIES,
        noteRes = R.string.devinfo_cpu_clusters_note.takeIf { s.cpuClusters.isEmpty() },
    ),
    DeviceInfoFact(
        R.string.devinfo_cpu_load,
        value = s.cpuLoadPercent?.toString(),
        unit = "%",
        trust = if (s.cpuLoadPercent == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = PROC_STAT,
    ),
    frequency(R.string.devinfo_cpu_min, s.cpuMinMhz, CPUINFO_MIN_FREQ),
    frequency(R.string.devinfo_cpu_freq, s.cpuFreqMhz, CPU_TOP_CORE),
    frequency(R.string.devinfo_cpu_ceiling, s.cpuCeilingMhz, CPUINFO_MAX_FREQ),
    // والذاكرة المخبئية والخصائص: «غير مقروء» حين لا تُعلنها النواة — وأنوية ARM كثيرة
    // تُعلن المخبأ في `cpu0/cache` لا في `/proc/cpuinfo`، فيُجرَّب المصدران بالترتيب.
    text(R.string.devinfo_cpu_cache, s.cpuCache, source = CPU_CACHE),
    text(R.string.devinfo_cpu_features, s.cpuFeatures, source = CPU_INFO),
    // وتفاصيل `/proc/cpuinfo` التي لا تُقرأ من غيره: المُنفِّذ والجزء والمراجعة — رموزٌ
    // لاتينية كما تُعلنها النواة (‏`0x41` · `0xd05`)، لا أسماءَ تُترجم فيضيع الأصل.
    text(R.string.devinfo_cpu_implementer, s.cpuImplementer, source = CPU_INFO),
    text(R.string.devinfo_cpu_part, s.cpuPart, source = CPU_INFO),
    text(R.string.devinfo_cpu_revision, s.cpuRevision, source = CPU_INFO),
)

// ── (٣) الرسوم ───────────────────────────────────────────────────────────
private fun gpuFacts(s: DeviceInfoSnapshot) = listOf(
    // ولا «اسم GPU» مخترع: البطاقة لا تُعلن اسمها على كل جهاز، والمقروء هو **الأسرة**
    // المستقرأة من اسم الشريحة (`Mali` · `Adreno`) — وهي تُقال كأسرة لا كاسم، وهذا
    // حدّ مُعلن لا نقص مخفيّ (وفيه لا يُدَّعى «Adreno 740» على شريحة لم تُقرأ عقدتها).
    DeviceInfoFact(
        R.string.devinfo_gpu_family,
        value = s.chipset?.let(::gpuFamilyOf),
        trust = if (gpuFamilyOf(s.chipset) == null) DeviceInfoTrust.Unsupported else DeviceInfoTrust.Snapshot,
        source = BUILD_PROP,
        noteRes = R.string.devinfo_gpu_family_note.takeIf { gpuFamilyOf(s.chipset) == null },
    ),
    // وواجهة الرسم **المعلَنة** لا المُفترضة: المنصّة تقول ما تدعمه (`OpenGL ES 3.2`)،
    // ولا يُقال «Vulkan» من وجود مكتبة في الذاكرة — سؤالٌ لم يُقَس لا يُجاب بتخمين.
    text(R.string.devinfo_gpu_api, s.gpuGles, source = GLES),
    DeviceInfoFact(
        R.string.devinfo_gpu_load,
        value = s.gpuLoadPercent?.toString(),
        unit = "%",
        trust = if (s.gpuLoadPercent == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = GPU_SYSFS,
    ),
    frequency(R.string.devinfo_gpu_freq, s.gpuFreqMhz, GPU_SYSFS),
    frequency(R.string.devinfo_gpu_ceiling, s.gpuCeilingMhz, GPU_SYSFS),
    frequency(R.string.devinfo_gpu_max_supported, s.gpuMaxSupportedMhz, GPU_OPP_TABLE),
    // والاسم الحقيقيّ كما يُعلنه العتاد (`SurfaceFlinger`) لا كما يُستنتج من الشريحة — وهذه
    // هي الفجوة التي كانت تُقال عنها «أسرة» وتُقرأ «اسم». والمشغّل يُقال معه لا يُهمل.
    text(R.string.devinfo_gpu_renderer, s.gpuRenderer, source = SURFACE_FLINGER),
    text(R.string.devinfo_gpu_vendor, s.gpuRendererVendor, source = SURFACE_FLINGER),
    text(R.string.devinfo_gpu_driver, s.gpuDriver, source = SURFACE_FLINGER),
    // وVulkan **مُعلنَان من المنصّة** (‏`getSystemAvailableFeatures`) لا مُستنتجان من وجود
    // مكتبة في الذاكرة — وهو التحفظ نفسه المكتوب على `declaredGles`، بلا مسار ثانٍ للتخمين.
    text(R.string.devinfo_vulkan_version, s.vulkanVersion, source = PACKAGE_MANAGER),
    text(R.string.devinfo_vulkan_level, s.vulkanLevel, source = PACKAGE_MANAGER),
)

// ── (٤) الذاكرة ──────────────────────────────────────────────────────────
private fun memoryFacts(s: DeviceInfoSnapshot) = listOf(
    memory(R.string.devinfo_ram_total, s.ramTotalMb),
    memory(R.string.devinfo_ram_used, s.ramUsedMb),
    memory(R.string.devinfo_ram_available, availableMb(s.ramUsedMb, s.ramTotalMb)),
    // و`swapTotalMb == 0` هو **«لا swap على هذا الجهاز»** لا «swap صفريّ»: تُقال الحقيقة
    // (`Unsupported`) بدل صفّ يقرأ «0 MB» وكأنّ للجهاز swap فارغًا.
    if (s.swapTotalMb == null || s.swapTotalMb == 0) {
        DeviceInfoFact(
            R.string.devinfo_zram,
            value = null,
            trust = DeviceInfoTrust.Unsupported,
            source = PROC_SWAPS,
            noteRes = R.string.devinfo_no_swap,
        )
    } else if (s.swapUsedMb == null) {
        // **والمقروء نصفه لا يُكمل بصفر.** كان هنا `swapUsedMb ?: 0`، فيُطبع «0 MB / 4.0 GB»
        // على جهاز قُرئ حجمه ولم تُقرأ كميته المستخدمة — أي صفرًا **مُختلقًا** بمعنى «لم تُقرأ»،
        // وهو العطب نفسه الذي تمنعه قاعدة الملفّ («لا يُكتب صفر مكان قيمة لم تُقرأ»)، وقد
        // كشفه فحص `PHONE-INFO-02` بعد أن كان يمرّ: النموذج كان يقرأ `null` فيضع له صفرًا.
        // فيُقال الغياب ويُشرح سببه — والحجم مقروء، والكمية المستخدمة هي التي لم تُقرأ.
        DeviceInfoFact(
            R.string.devinfo_zram,
            value = null,
            trust = DeviceInfoTrust.Unreadable,
            source = PROC_SWAPS,
            noteRes = R.string.devinfo_swap_used_unread,
        )
    } else {
        DeviceInfoFact(
            R.string.devinfo_zram,
            value = "${formatMb(s.swapUsedMb)} / ${formatMb(s.swapTotalMb)}",
            trust = DeviceInfoTrust.Live,
            source = PROC_SWAPS,
        )
    },
    // وما تُعلنه `meminfo` غير المجاميع: المخبّأ والمخازن المؤقتة وأقصى ذاكرة افتراضية —
    // وهذه هي فجوة «تفاصيل الذاكرة» في المرجعين، وهي كلّها قراءات ملفٍّ واحد.
    memory(R.string.devinfo_mem_cached, s.ramCachedMb),
    memory(R.string.devinfo_mem_buffers, s.ramBuffersMb),
    memory(R.string.devinfo_java_heap, s.javaHeapMaxMb),
)

// ── (٥) التخزين ──────────────────────────────────────────────────────────
private fun storageFacts(s: DeviceInfoSnapshot) = listOf(
    storage(R.string.devinfo_storage_total, s.storageTotalGb),
    storage(R.string.devinfo_storage_used, s.storageUsedGb),
    storage(R.string.devinfo_storage_free, freeGb(s.storageUsedGb, s.storageTotalGb)),
    // وأقسام الملفّات: مسارٌ لكل قسم ومقاساه — والمستخدم **`?`** حين قُرأ الحجم دون الكمية،
    // لا صفراً ولا سطراً يُستنتج من غير مصدر.
    DeviceInfoFact(
        label = R.string.devinfo_storage_partitions,
        value = s.storagePartitions.mapNotNull { part ->
            formatPartitionLine(part.path, part.usedGb, part.totalGb)
        }.joinToString("\n").takeIf { it.isNotEmpty() },
        trust = if (s.storagePartitions.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = STAT_FS,
    ),
)

// ── (٦) البطارية ─────────────────────────────────────────────────────────
private fun batteryFacts(s: DeviceInfoSnapshot) = listOf(
    batteryLevel(s.batteryPercent),
    DeviceInfoFact(
        R.string.devinfo_battery_status,
        value = s.batteryStatus?.takeIf { it.isNotBlank() },
        trust = if (s.batteryStatus.isNullOrBlank()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = BATTERY_SYSFS,
    ),
    decimal(R.string.devinfo_battery_temp, s.batteryTempC, "°C", BATTERY_SYSFS),
    decimal(R.string.devinfo_battery_voltage, s.batteryVoltageV, "V", BATTERY_SYSFS),
    DeviceInfoFact(
        R.string.devinfo_battery_current,
        value = s.batteryCurrentMa?.toString(),
        unit = "mA",
        trust = if (s.batteryCurrentMa == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = BATTERY_SYSFS,
    ),
    decimal(R.string.devinfo_power, s.powerWatt, "W", BATTERY_SYSFS),
    // صحة السعة وعدد الدورات: تُعلنها عُقد الشحن على بعض الأجهزة وتغيب على كثير غيرها،
    // فالغياب هنا هو الحال الغالب لا الاستثناء — ويُقال «غير مقروء» ولا يُخترع رقم.
    batteryHealth(s.batteryHealthPercent),
    batteryCycles(s.batteryCycleCount),
    text(R.string.devinfo_battery_technology, s.batteryTechnology, source = BATTERY_SYSFS),
    // وصحة البطارية **بالكلمة** كما تُعلنها المنصّة («جيدة» · «حرارة زائدة») بجانب النسبة
    // المقيسة — فأحدهما حكمُ العتاد والآخر قياسُ العقدة، ولا يُلغى أحدهما الآخر.
    textRes(R.string.devinfo_battery_health_label, batteryHealthLabelRes(s.batteryHealthCode), BATTERY_MANAGER),
    textRes(R.string.devinfo_battery_power_source, powerSourceLabelRes(s.batteryPluggedCode), BATTERY_MANAGER),
    // والسعة التصميمية وعدّاد الشحن: أرقامٌ تُعلنها عُقد الشحن على بعض الأجهزة وتغيب عن
    // كثير — فالغياب هو الحال الغالب، ويُقال «غير مقروء» ولا يُخترع رقم.
    batteryMah(R.string.devinfo_battery_design_capacity, s.batteryDesignCapacityMah),
    batteryMah(R.string.devinfo_battery_charge_counter, s.batteryChargeCounterMah),
)

// ── (٧) الشاشة ───────────────────────────────────────────────────────────
private fun displayFacts(s: DeviceInfoSnapshot) = listOf(
    resolution(R.string.devinfo_resolution, s.displayWidth, s.displayHeight),
    DeviceInfoFact(
        R.string.devinfo_density,
        value = s.displayDensityDpi?.toString(),
        unit = "dpi",
        trust = if (s.displayDensityDpi == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = WINDOW_MANAGER,
    ),
    refreshRate(s.displayRefreshHz),
    // والأنماط المدعومة: تُعلنها المنصّة كأنماط عرض، ولا يُضاف إليها نمط مخترع. وجهاز لا
    // يُعلن أنماطًا يُقال له «غير مقروء» بدل قائمة افتراضية (٦٠/٩٠/١٢٠) تُضلّل.
    DeviceInfoFact(
        R.string.devinfo_refresh_supported,
        value = formatHz(s.displaySupportedHz),
        unit = "Hz",
        trust = if (s.displaySupportedHz.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = WINDOW_MANAGER,
    ),
    // وما يُحسب من القراءتين معاً مُعلَنٌ كحساب لا كقياس: النسبة والقطر (‏`DeviceInfoFormat`).
    text(R.string.devinfo_aspect, aspectRatioLabel(s.displayWidth, s.displayHeight), source = WINDOW_MANAGER),
    DeviceInfoFact(
        R.string.devinfo_screen_size,
        value = diagonalInchesLabel(s.displayWidth, s.displayHeight, s.displayDensityDpi),
        unit = "in",
        trust = if (diagonalInchesLabel(s.displayWidth, s.displayHeight, s.displayDensityDpi) == null) {
            DeviceInfoTrust.Unreadable
        } else {
            DeviceInfoTrust.Snapshot
        },
        source = WINDOW_MANAGER,
    ),
    text(R.string.devinfo_density_bucket, densityBucketLabel(s.displayDensityDpi), source = WINDOW_MANAGER),
    // والشقّ: **وصفره قراءة** (لا شقّ) — والغياب وحده عدم قراءة، فتُقال الحالتان بلفظين.
    DeviceInfoFact(
        R.string.devinfo_cutout_top,
        value = s.displayCutoutTopPx?.takeIf { it >= 0 }?.let { "$it px" },
        trust = if (s.displayCutoutTopPx == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = WINDOW_MANAGER,
    ),
    supportedFact(R.string.devinfo_wide_gamut, s.displayWideGamut, WINDOW_MANAGER),
    DeviceInfoFact(
        R.string.devinfo_hdr_caps,
        value = hdrTypeNames(s.displayHdrTypes),
        trust = when {
            s.displayHdrTypes.isNotEmpty() -> DeviceInfoTrust.Snapshot
            else -> DeviceInfoTrust.Unsupported
        },
        source = WINDOW_MANAGER,
        noteRes = R.string.devinfo_hdr_none.takeIf { s.displayHdrTypes.isEmpty() },
    ),
    // السطوع الحاليّ ونسبةُ الحدّ ومقياس الخط والاتجاه — كلها إعداداتٌ حيّة تُقرأ لا تُخمَّن.
    DeviceInfoFact(
        R.string.devinfo_brightness,
        value = s.displayBrightnessPercent?.takeIf { it in 0..100 }?.toString(),
        unit = "%",
        trust = if (s.displayBrightnessPercent == null || s.displayBrightnessPercent !in 0..100) {
            DeviceInfoTrust.Unreadable
        } else {
            DeviceInfoTrust.Live
        },
        source = SETTINGS_SYSTEM,
    ),
    stateFact(
        R.string.devinfo_brightness_mode,
        s.displayAutoBrightness,
        SETTINGS_SYSTEM,
        R.string.devinfo_brightness_auto,
        R.string.devinfo_brightness_manual,
    ),
    DeviceInfoFact(
        R.string.devinfo_screen_timeout,
        value = s.displayTimeoutMinutes?.takeIf { it > 0f }?.let { formatDouble(it.toDouble()) },
        unit = "min",
        trust = if (s.displayTimeoutMinutes == null || s.displayTimeoutMinutes <= 0f) {
            DeviceInfoTrust.Unreadable
        } else {
            DeviceInfoTrust.Snapshot
        },
        source = SETTINGS_SYSTEM,
    ),
    DeviceInfoFact(
        R.string.devinfo_font_scale,
        value = s.fontScale?.takeIf { it > 0f }?.let { formatDouble(it.toDouble(), 2) },
        unit = "×",
        trust = if (s.fontScale == null || s.fontScale <= 0f) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = CONFIGURATION,
    ),
    textRes(R.string.devinfo_orientation, orientationLabelRes(s.orientationCode), CONFIGURATION),
)

// ── (٨) الحرارة ──────────────────────────────────────────────────────────
private fun thermalFacts(s: DeviceInfoSnapshot) = listOf(
    temperature(R.string.devinfo_temp_cpu, s.cpuTempC),
    temperature(R.string.devinfo_temp_gpu, s.gpuTempC),
    temperature(R.string.devinfo_temp_skin, s.skinTempC),
    count(R.string.devinfo_thermal_zones, s.thermalZoneCount, source = THERMAL_ZONE),
)

// ── (٨ب) الحرارة: بطاقاتها الثلاث ──────────────────────────────────────────
/**
 * بطاقات الحرارة — كلها من قارئات قائمة (`readThermalZones` · `readCoolingDevices` · `readTripPoints`)،
 * وكل بطاقة **تُحذف إن كانت فارغة**: عنوانٌ لا تحته شيء يَعِد بما لا يوجد.
 */
private fun thermalCards(s: DeviceInfoSnapshot): List<DeviceInfoCard> = listOfNotNull(
    zonesCard(s),
    tripsCard(s),
    coolingCard(s),
)

/** الخريطة الحرارية: صفٌّ لكل منطقة — تسميتها وفئتها وقراءتها. */
private fun zonesCard(s: DeviceInfoSnapshot): DeviceInfoCard? {
    if (s.thermalZones.isEmpty()) return null
    return DeviceInfoCard(
        titleRes = R.string.detail_thermal_map,
        rows = s.thermalZones.map { zone -> DeviceInfoRow(zone.label, zoneDetail(zone, s.thermalOffLabel)) },
    )
}

/**
 * تفاصيل منطقة — **ثلاث حالات لا اثنتان**، وهي نفس الثلاثيّة التي يقوم عليها الجرد:
 *
 * * قُرئت: الفئة وقياسها («CPU · 42 °C»).
 * * **مطفأة**: النواة تقول إنها لا تعمل — حالة معلنة لا عجز قراءة، فتُسمّى.
 * * تقرأ ولا تُعطي: غياب (`null`) فيقول العرض «غير متاح» — ولا تُكمَّل بصفر.
 */
private fun zoneDetail(zone: ThermalZoneRow, offLabel: String?): String? {
    val reading = zone.celsius?.takeIf { it > 0 }
    val head = zone.category.takeIf { it.isNotBlank() }
    return when {
        reading != null -> listOfNotNull(head, "$reading °C").joinToString(" · ")
        !zone.enabled -> offLabel ?: head
        else -> null
    }
}

/** نقاط التخفيف: عنوان الصفّ **منطقتُه** — فنُسبت الحرارة إلى موضعها لا إلى عموم. */
private fun tripsCard(s: DeviceInfoSnapshot): DeviceInfoCard? {
    val rows = s.thermalZones.flatMap { zone ->
        zone.trips.map { trip ->
            DeviceInfoRow(title = "${zone.label} · ${trip.kind}", detail = "${trip.celsius} °C")
        }
    }
    if (rows.isEmpty()) return null
    return DeviceInfoCard(titleRes = R.string.detail_thermal_trips, rows = rows)
}

/** أجهزة التبريد: صفٌّ لكل جهاز وحالته من أقصاها (`3/10`). */
private fun coolingCard(s: DeviceInfoSnapshot): DeviceInfoCard? {
    if (s.coolingDevices.isEmpty()) return null
    return DeviceInfoCard(
        titleRes = R.string.detail_thermal_cooling,
        rows = s.coolingDevices.map { device ->
            DeviceInfoRow(title = device.label, detail = coolingStateOf(device.current, device.max))
        },
    )
}

// ── (٩) المستشعرات ───────────────────────────────────────────────────────
private fun sensorFacts(s: DeviceInfoSnapshot) = listOfNotNull(
    count(R.string.devinfo_sensor_count, s.sensorCount, source = SENSOR_SERVICE),
    count(R.string.devinfo_sensor_kinds, s.sensorKindCount, source = SENSOR_SERVICE),
    count(R.string.devinfo_sensor_wakeup, s.sensorWakeUpCount, source = SENSOR_SERVICE),
    kindsFact(s),
    missingKindsFact(s),
    lightFact(s),
)

/**
 * الأصناف **الغائبة** بأسمائها — فلا يُستنتج الغائب من غياب صفّ، ولا يُدَّعى صنف لم تُعلنه المنصّة.
 * ولا صفّ حين لا غائب، أو حين لم تُقرأ المستشعرات أصلًا (فلا «كلها غائبة» كذبًا).
 */
private fun missingKindsFact(s: DeviceInfoSnapshot): DeviceInfoFact? {
    if (s.sensorKinds.isEmpty()) return null
    val missing = SensorInventory.Kind.entries.filterNot { it in s.sensorKinds }
    if (missing.isEmpty()) return null
    return DeviceInfoFact(
        label = R.string.max_sensor_kinds_absent,
        value = missing.joinToString(" · ") { kind -> s.sensorKindLabels[kind] ?: kind.id },
        trust = DeviceInfoTrust.Live,
        source = SENSOR_SERVICE,
    )
}

/**
 * الجرد: **صفٌّ لكل مستشعر** بعنوانه من العتاد وبسطر حقوله التسعة.
 *
 * والاسم الفارغ يأخذ اسم صنفه — وهو ما تفعله بطاقة التشخيص كذلك، فلا يظهر صفّ بلا عنوان.
 * والسطر يُبنى في [SensorInventory.detailLine] (صافٍ ومُقاس) لا في الرسم.
 */
private fun sensorRows(s: DeviceInfoSnapshot): List<DeviceInfoRow> =
    s.sensorItems.map { item ->
        DeviceInfoRow(
            title = item.name.ifBlank { kindName(item, s) },
            detail = SensorInventory.detailLine(
                item = item,
                kindLabel = kindName(item, s),
                wakeUpLabel = s.sensorWakeUpLabel,
            ),
        )
    }

/** اسم الصنف بلغة الواجهة، والبديل **معرّفه** الذي تُسمّيه به المنصّة لا تخمين. */
private fun kindName(item: SensorInventory.Item, s: DeviceInfoSnapshot): String =
    s.sensorKindLabels[item.kind] ?: item.kind.id

/** الأصناف الحاضرة بأسمائها — وغائبها في الصفّ الذي يليه ([missingKindsFact]). */
private fun kindsFact(s: DeviceInfoSnapshot): DeviceInfoFact? {
    if (s.sensorKinds.isEmpty()) return null
    return DeviceInfoFact(
        label = R.string.max_sensor_kinds,
        value = s.sensorKinds.joinToString(" · ") { kind -> s.sensorKindLabels[kind] ?: kind.id },
        trust = DeviceInfoTrust.Live,
        source = SENSOR_SERVICE,
    )
}

/**
 * قراءة الضوء **بحالاتها الثلاث**: قراءة حقيقية، أو مستشعر غائب (`Unsupported`)، أو لم
 * تُقرأ في المهلة (`Unreadable`). وصفر لوكس يمرّ لأنّه **قراءة** (مظلم مقيس) لا غياب.
 */
private fun lightFact(s: DeviceInfoSnapshot): DeviceInfoFact? {
    val light = s.sensorLight ?: return null
    return DeviceInfoFact(
        label = R.string.max_sensor_light,
        value = light.lux?.let { formatDouble(it.toDouble()) },
        unit = LUX_UNIT,
        trust = when (light.state) {
            SensorInventory.ReadingState.REPORTED -> DeviceInfoTrust.Live
            SensorInventory.ReadingState.UNREADABLE -> DeviceInfoTrust.Unreadable
            SensorInventory.ReadingState.ABSENT -> DeviceInfoTrust.Unsupported
        },
        source = SENSOR_SERVICE,
        noteRes = when (light.state) {
            SensorInventory.ReadingState.REPORTED -> null
            SensorInventory.ReadingState.UNREADABLE -> R.string.max_sensor_light_unreadable
            SensorInventory.ReadingState.ABSENT -> R.string.max_sensor_light_absent
        },
    )
}

// ── (١٠) النظام ──────────────────────────────────────────────────────────
private fun systemFacts(s: DeviceInfoSnapshot) = listOf(
    text(R.string.android_version, s.android),
    DeviceInfoFact(
        R.string.devinfo_api_level,
        value = s.sdk?.toString(),
        trust = if (s.sdk == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = BUILD_FIELD,
    ),
    text(R.string.kernel_version, s.kernel),
    text(R.string.fingerprint, s.fingerprint),
    text(R.string.devinfo_security_patch, s.securityPatch),
    text(R.string.selinux_status, s.selinux),
    // ولا صفّ لوسيلة الجذر هنا: قارئها الواحد `DeviceBlueprint` ووسيلتها **شاشة الصلاحيات**
    // المالكة لها (`Privilege`) — ونقلُ حالةِ الجذر إلى صفّ ثانٍ كان سيصنع مصدرين لفكرة واحدة.
    // ومن أرادها فله "افتح الشاشة الكاملة" تحت هذا القسم نفسه.
    text(R.string.devinfo_app_version, s.appVersion),
    // ══ ما يُعلنه البناء والمنصّة (الجولة الجديدة) ═════════════════════════════
    text(R.string.devinfo_android_codename, s.androidCodename, source = BUILD_FIELD),
    text(R.string.devinfo_build_id, s.buildId, source = BUILD_FIELD),
    text(R.string.devinfo_build_incremental, s.buildIncremental, source = BUILD_FIELD),
    text(R.string.devinfo_build_type, s.buildType, source = BUILD_FIELD),
    text(R.string.devinfo_baseband, s.baseband, source = BUILD_FIELD),
    text(R.string.devinfo_bootloader, s.bootloader, source = BUILD_FIELD),
    text(R.string.devinfo_locale, s.localeName, source = CONFIGURATION),
    text(R.string.devinfo_timezone, s.timeZoneId, source = TIME_ZONE),
    text(R.string.devinfo_vm, s.vmLabel, source = RUNTIME_HEAP),
    // وWebView وخدمات Play: إصداران يُقرآن من مدير الحزم، وغيابهما (جهاز بلا خدمات Google)
    // حالٌ معروفة تُقال «غير مقروء» لا تُملأ بإصدارٍ عامّ.
    text(R.string.devinfo_webview, s.webViewVersion, source = PACKAGE_MANAGER),
    text(R.string.devinfo_play_services, s.playServicesVersion, source = PACKAGE_MANAGER),
    supportedFact(R.string.devinfo_treble, s.treble, BUILD_PROP),
    supportedFact(R.string.devinfo_seamless, s.seamlessUpdates, BUILD_PROP),
    supportedFact(R.string.devinfo_dynamic_partitions, s.dynamicPartitions, BUILD_PROP),
)

// ── (١١) الشبكة ──────────────────────────────────────────────────────────
private fun networkFacts(s: DeviceInfoSnapshot) = listOf(
    DeviceInfoFact(
        R.string.devinfo_download,
        value = s.downloadKbps?.let(::formatKbps),
        trust = if (s.downloadKbps == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = TRAFFIC_STATS,
    ),
    DeviceInfoFact(
        R.string.devinfo_upload,
        value = s.uploadKbps?.let(::formatKbps),
        trust = if (s.uploadKbps == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = TRAFFIC_STATS,
    ),
    // وحالة التطوير: قراءتان من `Settings.Global` لا ادّعاءان — والقيمة **مترجَمة** (مُفعّل/
    // مُعطّل) فلا يُطبع لفظٌ لاتينيّ في جملة عربية.
    stateFact(R.string.devinfo_adb, s.adbEnabled, SETTINGS_GLOBAL, R.string.devinfo_state_on, R.string.devinfo_state_off),
    stateFact(
        R.string.devinfo_dev_options,
        s.developerOptions,
        SETTINGS_GLOBAL,
        R.string.devinfo_state_on,
        R.string.devinfo_state_off,
    ),
    // ولا عنوان IP ولا اسم شبكة: الأول بيان جهاز لا يُعرض بلا حاجة، والثاني يحتاج إذن
    // موقع لم يُطلب — والطلب نصّ صريح: «مراعاة الخصوصية وعدم عرض بيانات حساسة دون حاجة».
    // **وهو قرارٌ محفوظ لا منسيّ:** أُعيدت قراءته في جولة «معلومات أكثر من الصور» فاختير
    // أن يبقى — فالمقارنة أوسع بالقدرات المُعلَنة (‏`chipsOf`) لا بمعطيات تعقّب.
)

// ── بواني الحقول: كل حقل يُبنى من دالّة واحدة فلا تختلف قاعدتان لحقلين ──

private fun text(@StringRes label: Int, value: String?, source: String? = BUILD_FIELD) =
    DeviceInfoFact(
        label = label,
        value = value?.takeIf { it.isNotBlank() },
        trust = if (value.isNullOrBlank()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = source,
    )

private fun count(@StringRes label: Int, value: Int?, source: String? = BUILD_FIELD) =
    DeviceInfoFact(
        label = label,
        value = value?.takeIf { it > 0 }?.toString(),
        trust = if (value == null || value <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = source,
    )

private fun decimal(@StringRes label: Int, value: Float?, unit: String, source: String?) =
    DeviceInfoFact(
        label = label,
        value = value?.takeIf { it.isFinite() }?.let { formatDouble(it.toDouble()) },
        unit = unit,
        trust = if (value == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = source,
    )

/** تردّد: يُعرض بالجيجاهرتز، والصفر ليس تردّدًا. */
private fun frequency(@StringRes label: Int, mhz: Int?, source: String?) =
    DeviceInfoFact(
        label = label,
        value = mhz?.takeIf { it > 0 }?.let { formatDouble(it / 1000.0, 2) },
        unit = "GHz",
        trust = if (mhz == null || mhz <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = source,
    )

/**
 * معدّل التحديث: **لقطة** لا قراءة حيّة — هو نمط الشاشة الملتزم به، لا رقم يُقاس كل دورة.
 * وصفر "٦٠" إنما صفر الغياب: منصّة لا تُعلن معدّلًا لا يُكتب لها ٦٠.
 */
private fun refreshRate(hz: Int?) =
    DeviceInfoFact(
        label = R.string.devinfo_refresh,
        value = hz?.takeIf { it > 0 }?.toString(),
        unit = "Hz",
        trust = if (hz == null || hz <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = WINDOW_MANAGER,
    )

/**
 * صحة السعة: نسبة معقولة (١..١٥٠٪) أو «غير مقروء». **والصفر هنا غياب لا قراءة** — قرأ
 * الاختبار المقيَّد هذا الفرق فعلًا: كان الصفر يُعرض بلا قيمة (`null`) و**بحالة `Live`**،
 * وهو الجمع الذي تنفيه بوابة النموذج نفسها «حقل بلا قيمة وبلا حالة تُعلنها».
 */
private fun batteryHealth(percent: Int?) = DeviceInfoFact(
    label = R.string.devinfo_battery_health,
    value = percent?.takeIf { it in HEALTH_PERCENT_RANGE }?.toString(),
    unit = "%",
    trust = if (percent?.takeIf { it in HEALTH_PERCENT_RANGE } == null) {
        DeviceInfoTrust.Unreadable
    } else {
        DeviceInfoTrust.Live
    },
    source = BATTERY_SYSFS,
)

/**
 * دورات الشحن: **وصفرها قراءة صحيحة** (بطارية جديدة)، فلا يمرّ على `countable` —
 * وهو الفرق نفسه المعلن في مستوى البطارية: الصفر يمرّ حيث يكون قراءة.
 */
private fun batteryCycles(count: Int?) = DeviceInfoFact(
    label = R.string.devinfo_battery_cycles,
    value = count?.takeIf { it >= 0 }?.toString(),
    trust = if (count == null || count < 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
    source = BATTERY_SYSFS,
)

/** حرارة بالدرجة المئوية: **وصفرها ليس قراءة** (صفر مئويّ يعني سنسورًا لا يعمل أو غيابه). */
private fun temperature(@StringRes label: Int, celsius: Int?) =
    DeviceInfoFact(
        label = label,
        value = celsius?.takeIf { it > 0 }?.toString(),
        unit = "°C",
        trust = if (celsius == null || celsius <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = THERMAL_ZONE,
    )

/** ذاكرة: ميغابايت حتى ١٠٢٤ ثم جيجابايت — والعتبة واحدة فلا يقرأ المستخدم وحدتين بلا سبب. */
private fun memory(@StringRes label: Int, mb: Int?): DeviceInfoFact {
    val value = mb?.takeIf { it > 0 } ?: return DeviceInfoFact(
        label, null, trust = DeviceInfoTrust.Unreadable, source = PROC_MEMINFO,
    )
    return if (value >= MB_PER_GB) {
        DeviceInfoFact(label, formatDouble(value / 1024.0), "GB", DeviceInfoTrust.Live, PROC_MEMINFO)
    } else {
        DeviceInfoFact(label, value.toString(), "MB", DeviceInfoTrust.Live, PROC_MEMINFO)
    }
}

internal fun formatMb(mb: Int): String =
    if (mb >= MB_PER_GB) "${formatDouble(mb / 1024.0)} GB" else "$mb MB"

private fun storage(@StringRes label: Int, gb: Float?): DeviceInfoFact =
    DeviceInfoFact(
        label = label,
        value = gb?.takeIf { it > 0f }?.let { formatDouble(it.toDouble()) },
        unit = "GB",
        trust = if (gb == null || gb <= 0f) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = STAT_FS,
    )

private fun resolution(@StringRes label: Int, width: Int?, height: Int?): DeviceInfoFact {
    val w = width?.takeIf { it > 0 }
    val h = height?.takeIf { it > 0 }
    return DeviceInfoFact(
        label = label,
        value = if (w == null || h == null) null else "$w × $h",
        trust = if (w == null || h == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = WINDOW_MANAGER,
    )
}

private fun batteryLevel(percent: Int?): DeviceInfoFact =
    DeviceInfoFact(
        label = R.string.devinfo_battery_level,
        // وصفر البطارية **قراءة صحيحة** لا غياب: لا يمرّ على `countable` (وهو فرق مقصود).
        value = percent?.takeIf { it in 0..100 }?.toString(),
        unit = "%",
        trust = if (percent == null || percent !in 0..100) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = BATTERY_SYSFS,
    )

// ── بواني الجولة الجديدة: قيم مترجَمة، وأحكام ثلاثية، وبطاقات ──────────────

/** حقل قيمته **موارد نصّية** («جيدة» · «USB» · «طولي») — والترجمة في الموارد لا هنا. */
private fun textRes(@StringRes label: Int, @StringRes valueRes: Int?, source: String?): DeviceInfoFact =
    DeviceInfoFact(
        label = label,
        value = null,
        valueRes = valueRes,
        trust = if (valueRes == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = source,
    )

/**
 * حقل حكمٍ ثلاثيّ: `true` ⇒ [trueRes]، `false` ⇒ [falseRes]، و`null` ⇒ «لم يُقرأ» —
 * فلا يُكتب «غير مدعوم» لجهازٍ لم تُطرح عليه المسألة أصلًا.
 */
private fun stateFact(
    @StringRes label: Int,
    value: Boolean?,
    source: String?,
    @StringRes trueRes: Int,
    @StringRes falseRes: Int,
): DeviceInfoFact = DeviceInfoFact(
    label = label,
    value = null,
    valueRes = when (value) {
        true -> trueRes
        false -> falseRes
        null -> null
    },
    trust = if (value == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
    source = source,
)

/** خاصية مدعومة/غير مدعومة — و`null` لم يُقرأ فلا يُدّعى غيابٌ لم يُقَس. */
private fun supportedFact(@StringRes label: Int, supported: Boolean?, source: String?): DeviceInfoFact =
    stateFact(label, supported, source, R.string.devinfo_cap_supported, R.string.devinfo_cap_not_supported)

/**
 * سعة بالـmAh — **وصفرها قراءة صحيحة** (عدّاد شحنٍ مُفرَّغ) فتمرّ، والغياب وحده عدم قراءة.
 */
private fun batteryMah(@StringRes label: Int, mah: Int?): DeviceInfoFact =
    DeviceInfoFact(
        label = label,
        value = mah?.takeIf { it >= 0 }?.toString(),
        unit = "mAh",
        trust = if (mah == null || mah < 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
        source = BATTERY_SYSFS,
    )

// ── بطاقة DRM ────────────────────────────────────────────────────────────

/**
 * بطاقة DRM — كلها من عقدة `MediaDrm` واحدة، وكل حقلٍ منها يُعلن أو يغيب. **ولا يُبنى
 * عنوانٌ تحته لا شيء**: بطاقة بلا حقلٍ مقروء تُحذف كلّها (القاعدة نفسها لبطاقات الحرارة).
 */
private fun drmCard(s: DeviceInfoSnapshot): DeviceInfoCard? {
    val hdcp = when {
        // «لا مخرج رقميّ» حالةٌ معلنة (`-1`) لا مستوى ولا غياب — فلها سطرُها وترجمتُها.
        s.drmNoDigitalOutput -> DeviceInfoFact(
            label = R.string.devinfo_drm_hdcp,
            value = null,
            trust = DeviceInfoTrust.Unsupported,
            source = MEDIA_DRM,
            noteRes = R.string.devinfo_no_digital_output,
        )
        else -> text(R.string.devinfo_drm_hdcp, s.drmHdcp, source = MEDIA_DRM)
    }
    val facts = listOf(
        text(R.string.devinfo_drm_security, s.drmSecurityLevel, source = MEDIA_DRM),
        text(R.string.devinfo_drm_vendor, s.drmVendor, source = MEDIA_DRM),
        text(R.string.devinfo_drm_version, s.drmVersion, source = MEDIA_DRM),
        text(R.string.devinfo_drm_algorithms, s.drmAlgorithms, source = MEDIA_DRM),
        hdcp,
    )
    val anyRead = facts.any { it.value != null || it.valueRes != null || it.noteRes != null }
    if (!anyRead) return null
    return DeviceInfoCard(titleRes = R.string.devinfo_drm_title, facts = facts)
}

// ── بطاقات الكاميرا ──────────────────────────────────────────────────────

/**
 * بطاقة لكل عدسة — وعنوانُها **جهتها** (خلفية/أمامية/خارجية) لا رقمُها: فالرقم يختلف
 * بين الأجهزة والجهة هي ما يفهمه القارئ.
 */
private fun cameraCards(s: DeviceInfoSnapshot): List<DeviceInfoCard> =
    s.cameras.map { camera ->
        DeviceInfoCard(
            titleRes = when (camera.facing) {
                CameraFacing.Front -> R.string.devinfo_camera_front
                CameraFacing.Back -> R.string.devinfo_camera_rear
                CameraFacing.External -> R.string.devinfo_camera_external
            },
            facts = cameraFacts(camera),
        )
    }

/**
 * حقول العدسة — **ولكل حقلٍ مقروء صفُّه**: الفتحة والطول البؤري قائمتان مُعلَنتان،
 * والمستشعر وأبعاده واتجاهه وتكبيره وفلاشه وقدراته كلها من `CameraCharacteristics`.
 * والغائب يُقال عنه «غير مقروء» ولا يُملأ بقيمة العدسة المجاورة.
 */
private fun cameraFacts(c: DeviceCameraInfo): List<DeviceInfoFact> = listOf(
    text(R.string.devinfo_camera_level, c.hardwareLevel, source = CAMERA2),
    DeviceInfoFact(
        label = R.string.devinfo_camera_aperture,
        value = c.apertures.mapNotNull(::formatAperture).joinToString(" · ").takeIf { it.isNotEmpty() },
        trust = if (c.apertures.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = CAMERA2,
    ),
    DeviceInfoFact(
        label = R.string.devinfo_camera_focal,
        value = c.focalLengthsMm.mapNotNull(::formatFocalLength).joinToString(" · ").takeIf { it.isNotEmpty() },
        trust = if (c.focalLengthsMm.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = CAMERA2,
    ),
    text(
        R.string.devinfo_camera_sensor_size,
        formatSensorSize(c.sensorSizeMm?.first, c.sensorSizeMm?.second),
        source = CAMERA2,
    ),
    text(
        R.string.devinfo_camera_pixel_array,
        c.pixelArray?.takeIf { it.first > 0 && it.second > 0 }?.let { "${it.first} × ${it.second}" },
        source = CAMERA2,
    ),
    text(R.string.devinfo_camera_megapixels, formatMegaPixels(c.pixelArray?.first, c.pixelArray?.second), source = CAMERA2),
    // واتجاه المستشعر **درجة لا كلمة** (‏`90°`) — كما تُعلنه المنصّة، فلا يُخلط بينه وبين
    // «الجهة» التي عنوانُها البطاقة.
    text(
        R.string.devinfo_camera_orientation,
        c.sensorOrientation?.takeIf { it >= 0 }?.let { "$it°" },
        source = CAMERA2,
    ),
    DeviceInfoFact(
        label = R.string.devinfo_camera_zoom,
        value = c.maxDigitalZoom?.takeIf { it > 0f }?.let { formatDouble(it.toDouble()) },
        unit = "×",
        trust = if (c.maxDigitalZoom == null || c.maxDigitalZoom <= 0f) {
            DeviceInfoTrust.Unreadable
        } else {
            DeviceInfoTrust.Snapshot
        },
        source = CAMERA2,
    ),
    stateFact(
        R.string.devinfo_camera_flash,
        c.flashAvailable,
        CAMERA2,
        R.string.devinfo_cap_supported,
        R.string.devinfo_cap_not_supported,
    ),
    // وأقصى دقة JPEG **بعددها**: القائمة كاملة سطرٌ لا يُقرأ، والرقمان يكفيان لسؤال
    // «ما أقصى دقة؟ وكم دقةً يتفاوض عليها العتاد؟».
    text(
        R.string.devinfo_camera_jpeg_max,
        c.jpegMax?.takeIf { it.first > 0 && it.second > 0 }?.let { "${it.first} × ${it.second}" },
        source = CAMERA2,
    ),
    DeviceInfoFact(
        label = R.string.devinfo_camera_jpeg,
        value = c.jpegCount?.takeIf { it > 0 }?.toString(),
        trust = if (c.jpegCount == null || c.jpegCount <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = CAMERA2,
    ),
    text(R.string.devinfo_camera_color_filter, colorFilterName(c.colorFilterArrangement), source = CAMERA2),
    DeviceInfoFact(
        label = R.string.devinfo_camera_caps,
        value = c.capabilities.joinToString(" · ").takeIf { it.isNotEmpty() },
        trust = if (c.capabilities.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
        source = CAMERA2,
    ),
)
