/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Vendor ceiling authority — the layer that actually holds CPU/GPU ceilings.
 *
 * Device-proven on MediaTek (rodin / MT6899 / HyperOS 3): writing
 * `scaling_max_freq` or `mali/max_freq` alone is **clamped synchronously** by
 * authorities we never touched. A real capture showed 1800000 -> 1200000 and
 * 2500000 -> 2200000 on cpufreq, and 1300000000 -> 754000000 on the GPU, while
 * values below the clamp were accepted — i.e. "same value works, any change
 * fails". These nodes are those authorities' own interfaces.
 */
package nd.max.core.hardware

/**
 * سلطة السقف على مستوى المنصّة.
 *
 * لماذا وُجد
 * ----------
 * لأن العقدة التي نكتب فيها ليست صاحبة القرار على أجهزة MediaTek. سجلّ جهاز حقيقي أثبت القمع
 * **المتزامن**: `policy0/scaling_max_freq` كُتب 1800000 فقُرئ 1200000، و`mali/max_freq` كُتب
 * 1300000000 فقُرئ 754000000، بينما القيمة التي **تحت** السقف تُطبَّق. فالمستخدم يرى «التغيير
 * يفشل والقيمة نفسها تنجح» بلا سبب ظاهر في السجل.
 *
 * والصاحبان الفعليّان:
 *
 * 1. **حرارة MI** — `/sys/devices/virtual/thermal/thermal_message/cpu_limits` (بصيغة `cpu{P} {kHz}`)
 *    و`sconfig` الذي يحمل وضع الحدّ الحالي (`6` = بلا حدود).
 * 2. **حاكم طاقة MTK** — `/proc/powerhal_cpu_ctrl/perfserv_freq` (بصيغة `{P} {minKHz} {maxKHz}`).
 * 3. **سقف GPU** — جهاز تبريد الحرارة الخاص بالGPU (`cooling_deviceN/cur_state` تحت `/sys/class/thermal`)
 *    وسقف GED (`custom_upbound_gpu_freq` · `gpu_cust_upbound_freq`).
 *
 * قواعد هذا الملف
 * ---------------
 * - **لا يخمّن**: كل عقدة تُختبَر بالوجود قبل الكتابة، وكل كتابة تُقرأ بعدها (نفس عقيدة
 *   `WriteVerification`). فعقدة غائبة لا تُنتج «نجاحًا» كاذبًا.
 * - **لا يتجاوز العتاد**: المدى يُقيَّد في المتصل (`CpuHardwareBackend` · `GpuHardwareBackend`)
 *   بحدود `cpuinfo_*` وقائمة OPP المعلنة — فالسلطة هنا تُحرّر، ولا تُخترع قيمة.
 * - **الترتيب مقصود**: التحرير **قبل** الكتابة على العقدة العادية. ولو كتبنا ثم حرّرنا لَبقي القمع
 *   الأول مسجَّلًا `differs`، وهو ما يجعل السجل يشرح عطلًا انتهى.
 */
object PlatformCeilingAuthority {

    private const val MI_THERMAL_CPU_LIMITS = "/sys/devices/virtual/thermal/thermal_message/cpu_limits"
    private const val MI_THERMAL_SCONFIG = "/sys/devices/virtual/thermal/thermal_message/sconfig"
    private const val MTK_POWERHAL_CPU_FREQ = "/proc/powerhal_cpu_ctrl/perfserv_freq"
    private const val THERMAL_ROOT = "/sys/class/thermal"

    private const val GED_UPBOUND = "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
    private const val GED_CUST_UPBOUND = "/sys/module/ged/parameters/gpu_cust_upbound_freq"
    private const val GED_CUST_BOOST = "/sys/module/ged/parameters/gpu_cust_boost_freq"
    private const val GED_DVFS_ENABLE = "/sys/module/ged/parameters/gpu_dvfs_enable"

    /** `6` = «بلا حدود» في واجهة حرارة MI. وتُكتب عند كل طلب فلا تبقى حدود جلسة سابقة. */
    const val MI_THERMAL_NO_LIMITS_MODE = 6

    /**
     * حالة سقف المنصّة كما تُقرأ الآن — النصف الذي تملكه هذه الطبقة في قراءة السقف.
     *
     * و[upboundRaw] خام عن قصد: `0` = «بلا سقف مخصّص»، وغير الصفر = سقف قائم. ولا يُفسَّر
     * مقداره هنا لأن وحدة `gpu_*` في GED غير مؤكَّدة على كل إصدار، وتفسيرٌ غير مؤكَّد يُنتج حكمًا
     * غير مؤكَّد. و[coolingHeld] من `cur_state` لجهاز تبريد GPU: `null` = لا يُقرأ أو لا وجود له.
     */
    data class PlatformCeiling(val upboundRaw: Long?, val coolingHeld: Boolean?)

    /**
     * ما تحرّره هذه الطبقة، محفوظًا **قبل** التحرير ليُعاد كما كان.
     *
     * ووُجد لأن التحرير تغيير حقيقي على العتاد: بلا هذا الحفظ يبقى سقف المصنّع مرفوعًا بعد أن
     * ينتهي سبب رفعه (خرج التطبيق مثلًا)، وهو تسريب يتعارض مع قاعدة «كل مقبض يُستعاد عند الخروج».
     */
    data class CeilingCapture(
        val gedUpbound: String?,
        val gedCustUpbound: String?,
        val gedCustBoost: String?,
        val dvfsEnable: String?,
        val coolingStatePath: String?,
        val coolingState: String?,
    )

    /** ما تمّ فعلًا — يُعاد للمتصل ليُسجَّل، ولا يُخزَّن هنا (لا حالة في هذا الملف). */
    data class Report(
        val releasedMiThermalMode: Boolean,
        val cpuLimitAccepted: Boolean,
        val powerhalRangeAccepted: Boolean,
        val gpuCoolingReleased: Boolean,
        val gedCeilingReleased: Boolean,
    ) {
        /** هل تحرّرت سلطة واحدة على الأقل؟ */
        val anyChannel: Boolean
            get() = cpuLimitAccepted || powerhalRangeAccepted || gpuCoolingReleased || gedCeilingReleased
    }

    // ── بناء الطلبات: خالص وقابل للاختبار بلا جهاز ────────────────────────────

    /** `cpu{P} {kHz}` — كما تكتبه حرارة MI. الوحدة kHz وإن كان الاسم «limit». */
    fun cpuLimitRequest(policyIndex: Int, maxKHz: Long): String = "cpu$policyIndex $maxKHz"

    /** `{P} {minKHz} {maxKHz}` — كما يقبله حاكم طاقة MTK. */
    fun powerhalRangeRequest(policyIndex: Int, minKHz: Long, maxKHz: Long): String =
        "$policyIndex $minKHz $maxKHz"

    /**
     * رقم السياسة من مسارها (`…/policy4` ⇒ `4`).
     *
     * ووُضع هنا لا في [CpuHardwareBackend] لأن واجهات السلطة تُعنون **بالرقم** لا بالمسار، فتحويل
     * المسار إلى رقم جزء من عقد هذه الطبقة. و`null` حين لا يكون المسار سياسة — ولا يُسقَط إلى `0`،
     * فالسقوط إلى صفر يكتب سلطة على `policy0` بدل ألّا يكتب شيئًا.
     */
    fun policyIndex(policyPath: String): Int? =
        policyPath.substringAfterLast("policy", "").toIntOrNull()

    /**
     * هل هذا النوع من `type` يخصّ GPU؟ — التعرّف بالاسم لا برقم الجهاز.
     *
     * ولماذا لا `cooling_device3` كما في المراجع: الرقم مرتبط بهذا الجهاز وحده، والاسم هو
     * العقد الوحيد الذي يبقى صحيحًا على جهاز آخر. والبحث يقع على ملف `type` في كل جهاز تبريد.
     */
    fun isGpuCoolingType(type: String): Boolean {
        val t = type.trim().lowercase()
        if (t.isEmpty()) return false
        return t.contains("gpu") || t.contains("mali") || t.contains("gpufreq") || t.contains("graphics")
    }

    // ── CPU ────────────────────────────────────────────────────────────────────

    /**
     * يجعل المنصّة تقبل سقفًا على سياسة واحدة، ثم يترك الكتابة للمتصل.
     *
     * ولماذا لا يكتب هو نفسه `scaling_max_freq`: لأن مالك تلك الكتابة هو `CpuHardwareBackend`
     * (ومعه التحقّق والتراجع وحلقة `min <= max`). هذا الملف **يفتح البوابة**، لا يُنفّذ التغيير.
     *
     * @return تقرير بما استُقبل فعلًا؛ وكل قناة غائبة تُترك `false` فلا يُدّعى تحرير لم يقع.
     */
    fun permitCpu(policyIndex: Int, minKHz: Long?, maxKHz: Long?): Report {
        val released = releaseMiThermalMode()
        val limitAccepted = maxKHz != null &&
            writeVerified(MI_THERMAL_CPU_LIMITS, cpuLimitRequest(policyIndex, maxKHz))
        val rangeAccepted = minKHz != null && maxKHz != null &&
            writeVerified(MTK_POWERHAL_CPU_FREQ, powerhalRangeRequest(policyIndex, minKHz, maxKHz))
        return Report(released, limitAccepted, rangeAccepted, false, false)
    }

    /** `/proc/powerhal_cpu_ctrl/perfserv_freq` وحده — لمسار لا يعرف `min`. */
    fun permitCpuCeiling(policyIndex: Int, maxKHz: Long): Boolean =
        writeVerified(MI_THERMAL_CPU_LIMITS, cpuLimitRequest(policyIndex, maxKHz))

    /**
     * يحرّر وضع الحدّ في واجهة حرارة MI: بلا هذا تبقى حدود الوضع السابق (لعبة/توازن/توفير) سارية
     * فوق أي كتابة. والقيمة تُقرأ بعدها، فرجوع الجهاز إلى وضع مقصود (مثلًا أثناء شحن) يُبلَّغ عنه
     * بدل أن يُدّعى تحرير لم يستقرّ.
     */
    fun releaseMiThermalMode(): Boolean =
        writeVerified(MI_THERMAL_SCONFIG, MI_THERMAL_NO_LIMITS_MODE.toString())

    // ── GPU ────────────────────────────────────────────────────────────────────

    /**
     * يحرّر سقف GPU ثم يترك الكتابة للمتصل.
     *
     * وثلاث خطوات بترتيبها: **سقف التبريد** (وهو ما يفسّر 754 MHz في تقرير الجهاز: `mali` عند
     * `cur_state` غير صفر يُقصّ عبر `devfreq`)، ثم **سقف GED** (الذي يملك DVFS الGPU على MTK)،
     * ثم تُترك `max_freq` لـ`GpuHardwareBackend`.
     *
     * وحين يُطلب تثبيت تردد واحد (`lock`) يوقف DVFS في GED، لأن GED سيعيد التردد بعد لحظات
     * وإلا — وهذا فرق بين «كُتب» و«ثبت».
     *
     * ## ومعاملان لا معامل واحد (`AR-34`)
     *
     * كان `lock` يحمل معنًى ثالثًا لم يكن اسمه: **تحرير السقف**. وكان المتصل يمرّره
     * `request.releaseVendorCeiling || min == max` — أي أن طلب «سقف عند قدرة الجهاز» (وهو عكس
     * التثبيت تمامًا) كان **يُطفئ DVFS** فيُثبَّت التردد الذي صادف وجوده لحظة الطلب. وهذا ليس
     * تفصيلًا: هو نفسه العطب الذي حذّرت منه هذه الطبقة سابقًا («تركه مطفأً يثبّت OPP منخفضًا»).
     *
     * فصار الصريح صريحًا: `release` = هل نرفع سقف المصنّع؟ و`lock` = هل نثبّت قيمة واحدة؟
     * وهما لا يُشتقّ أحدهما من الآخر. و`release = false` تعني **لا نلمس عقد السقف**: طلبُ سقفٍ
     * أدنى من قدرة الجهاز لا يجوز أن يرفع حماية حراريّة وَضعها المصنّع، وإلا صار طلب تبريد
     * تسخينًا. وDVFS يُضبَط في **كل** نداء (يُشغَّل إن لم يكن طلب تثبيت) فلا يبقى مطفأً أبدًا.
     */
    fun permitGpu(lock: Boolean = false, release: Boolean = true): Report {
        val coolingReleased = if (release) releaseGpuCoolingCap() else false
        val gedReleased = if (release) releaseGedCeiling() else false
        // والDVFS يُطفأ **عند طلب التثبيت فقط**، ويُشغَّل في كل طلب غيره. وتركه مطفأً بعد أن طُلب
        // تثبيت سابق يثبّت OPP منخفضًا كان سائدًا لحظة الإطفاء — وهو عطب **أسوأ من عدم التثبيت**:
        // الجهاز يبقى على تردد ضعيف بلا سبب ظاهر في أي شاشة. (وهذا خطأ أدركناه في المراجعة،
        // ومسجّل هنا لأنه لا يظهر في أي اختبار وحدة: يظهر بعد تحرير المستخدم للتثبيت.)
        setGedDvfs(enabled = !lock)
        return Report(false, false, false, coolingReleased, gedReleased)
    }

    /**
     * يحرّر جهاز تبريد GPU: يبحث في `/sys/class/thermal` عن جهاز `type` يخصّ GPU ثم يكتب حالته `0`.
     *
     * والبحث بالاسم مقصود (انظر [isGpuCoolingType]). وإن تعذّر التعرّف على أي جهاز فالعودة
     * `false` — لا نكتب `0` على تبريد لا نعرف ما يبرّده.
     */
    fun releaseGpuCoolingCap(): Boolean {
        val base = gpuCoolingDeviceBase() ?: return false
        // `0` = بلا تقييد لهذا الجهاز. والقراءة بعدها تُثبت أنها استقرّت.
        return writeVerifiedIfDifferent("$base/cur_state", "0")
    }

    /**
     * جهاز تبريد GPU — يُبحث بالاسم لا بالرقم (انظر [isGpuCoolingType])، ويُعاد مساره الأساس.
     *
     * واستُخرج هنا لأن ثلاثة مواضع تحتاجه (التحرير، والقراءة، والحفظ/الاستعادة)، وثلاث نسخ من
     * بحث واحد تعني أن تصحيحًا في إحداها لا يصل إلى الأخريين.
     */
    private fun gpuCoolingDeviceBase(): String? {
        val devices = RootFileAccess.listDirectories(THERMAL_ROOT)
            .filter { it.startsWith("cooling_device") }
            .sortedBy { it.removePrefix("cooling_device").toIntOrNull() ?: Int.MAX_VALUE }
        for (name in devices) {
            val base = "$THERMAL_ROOT/$name"
            val type = RootFileAccess.read("$base/type") ?: continue
            if (isGpuCoolingType(type)) return base
        }
        return null
    }

    /** سقف GED: `0` تعني «بلا سقف مخصّص» فيترك GED يدير OPP كاملًا. */
    fun releaseGedCeiling(): Boolean {
        var done = false
        for (path in listOf(GED_UPBOUND, GED_CUST_UPBOUND)) {
            if (writeVerifiedIfDifferent(path, "0")) done = true
        }
        // وسقف «boost» يُصفَّر بالمعنى نفسه: قيمته المخصّصة تسند التردد فلا ينزل عند الحاجة.
        writeVerifiedIfDifferent(GED_CUST_BOOST, "0")
        return done
    }

    /** يوقف/يشغّل DVFS في GED. الإيقاف يثبّت التردد الحالي — ولا يُستعمل إلا عند طلب تثبيت. */
    fun setGedDvfs(enabled: Boolean): Boolean =
        writeVerifiedIfDifferent(GED_DVFS_ENABLE, if (enabled) "1" else "0")

    /**
     * حالة السقف كما تُقرأ الآن — للعرض والحكم، ولا تكتب شيئًا.
     *
     * و`upboundRaw` يُقرأ من عقدتَي السقف: الأولى المعلنة، والثانية بديل بعض الإصدارات. وأول
     * قيمة مقروءة تكفي؛ والغياب الكامل يعني `null` — **لا صفر**، فالغياب ليس «بلا سقف» بل
     * «لا عقدة أقيس عليها».
     */
    fun gpuPlatformCeiling(): PlatformCeiling {
        val upbound = listOf(GED_UPBOUND, GED_CUST_UPBOUND)
            .firstNotNullOfOrNull { path -> RootFileAccess.read(path)?.trim()?.toLongOrNull() }
        val cooling = gpuCoolingDeviceBase()?.let { base ->
            RootFileAccess.read("$base/cur_state")?.trim()?.toIntOrNull()?.let { it > 0 }
        }
        return PlatformCeiling(upboundRaw = upbound, coolingHeld = cooling)
    }

    /**
     * يحفظ سقف المنصّة **قبل** تحريره، ليُعاد كما كان.
     *
     * وما لم يُقرأ يبقى `null` ولا يُخترع له قيمة: عقدة غائبة لا تُكتَب عند الاستعادة، فالحفظ
     * نصفه معلوم ونصفه معلَن بدل أن يصير خط الأساس قيمةً مُفترضة.
     */
    fun captureGpuCeiling(): CeilingCapture {
        val coolingBase = gpuCoolingDeviceBase()
        return CeilingCapture(
            gedUpbound = RootFileAccess.read(GED_UPBOUND),
            gedCustUpbound = RootFileAccess.read(GED_CUST_UPBOUND),
            gedCustBoost = RootFileAccess.read(GED_CUST_BOOST),
            dvfsEnable = RootFileAccess.read(GED_DVFS_ENABLE),
            coolingStatePath = coolingBase?.let { "$it/cur_state" },
            coolingState = coolingBase?.let { RootFileAccess.read("$it/cur_state") },
        )
    }

    /**
     * يعيد ما حرّرناه — وكل قناة كتبها [captureGpuCeiling] بقيمتها المحفوظة.
     *
     * وتُعاد `true` حين لا شيء محفوظ: «لا شيء لأستعيده» ليست فشلًا، وإرجاع `false` كان سيُنتج
     * سطر «فشل استعادة» على جهاز لا يملك هذه العقد أصلًا — وهو ضجيج يُخفي فشلًا حقيقيًّا.
     */
    fun restoreGpuCeiling(capture: CeilingCapture): Boolean {
        var restored = true
        capture.gedUpbound?.let { restored = writeVerified(GED_UPBOUND, it) && restored }
        capture.gedCustUpbound?.let { restored = writeVerified(GED_CUST_UPBOUND, it) && restored }
        capture.gedCustBoost?.let { restored = writeVerified(GED_CUST_BOOST, it) && restored }
        capture.dvfsEnable?.let { restored = writeVerified(GED_DVFS_ENABLE, it) && restored }
        val statePath = capture.coolingStatePath
        val state = capture.coolingState
        if (statePath != null && state != null) {
            restored = writeVerified(statePath, state) && restored
        }
        return restored
    }

    // ── الأداة الواحدة ────────────────────────────────────────────────────────

    /**
     * كتابة **متحقَّقة** على عقدة سلطة: تُكتب ثم تُقرأ.
     *
     * ولا تُكرَّر `WriteVerification` هنا: التسجيل يقع في [RootFileAccess.writeVerified] نفسه، فسطر
     * `WRITE_CHECK` واحد لكل عقدة سلطة وبتوقيع واحد مع بقية العتاد.
     */
    private fun writeVerified(path: String, value: String): Boolean {
        if (!RootFileAccess.exists(path)) return false
        return RootFileAccess.writeVerified(path, value) == WriteVerification.Outcome.MATCHED
    }

    /**
     * كتابة **عند الحاجة فقط**: عقدة تحمل القيمة أصلًا ⇒ لا كتابة ولا سطر سجل.
     *
     * ولماذا: مسار التحرير أصبح يُنادى في **كل** جلسة تطبيق (لأنه هو الفعل نفسه على أجهزة
     * `RELEASE_ONLY`)، وكتابة عمياء على عقدة سليمة كل جلسة تُنتج سطرًا لكل عقدة بلا تغيير — وهو
     * المرض الذي عُولج في تكملة ٧٥ (٩٤٪ من حزمة سجل كانت حلقة كتابة بلا تغيّر قيمة).
     * والحكم على «تحمل القيمة» يستعمل `WriteVerification.compare` نفسها، فتُقبل التكافؤات
     * العدديّة (`0` و`00`) ولا تُكتب عقدة تحمل المطلوب.
     */
    private fun writeVerifiedIfDifferent(path: String, value: String): Boolean {
        if (!RootFileAccess.exists(path)) return false
        val current = RootFileAccess.read(path)
        if (WriteVerification.compare(value, current) == WriteVerification.Outcome.MATCHED) return true
        return writeVerified(path, value)
    }
}
