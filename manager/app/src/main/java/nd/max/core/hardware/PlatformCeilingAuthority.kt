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
     */
    fun permitGpu(lock: Boolean = false): Report {
        val coolingReleased = releaseGpuCoolingCap()
        val gedReleased = releaseGedCeiling()
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
        val devices = RootFileAccess.listDirectories(THERMAL_ROOT)
            .filter { it.startsWith("cooling_device") }
            .sortedBy { it.removePrefix("cooling_device").toIntOrNull() ?: Int.MAX_VALUE }
        for (name in devices) {
            val base = "$THERMAL_ROOT/$name"
            val type = RootFileAccess.read("$base/type") ?: continue
            if (!isGpuCoolingType(type)) continue
            val state = "$base/cur_state"
            // `0` = بلا تقييد لهذا الجهاز. والقراءة بعدها تُثبت أنها استقرّت.
            return writeVerified(state, "0")
        }
        return false
    }

    /** سقف GED: `0` تعني «بلا سقف مخصّص» فيترك GED يدير OPP كاملًا. */
    fun releaseGedCeiling(): Boolean {
        var done = false
        for (path in listOf(GED_UPBOUND, GED_CUST_UPBOUND)) {
            if (writeVerified(path, "0")) done = true
        }
        // وسقف «boost» يُصفَّر بالمعنى نفسه: قيمته المخصّصة تسند التردد فلا ينزل عند الحاجة.
        writeVerified(GED_CUST_BOOST, "0")
        return done
    }

    /** يوقف/يشغّل DVFS في GED. الإيقاف يثبّت التردد الحالي — ولا يُستعمل إلا عند طلب تثبيت. */
    fun setGedDvfs(enabled: Boolean): Boolean =
        writeVerified(GED_DVFS_ENABLE, if (enabled) "1" else "0")

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
}
