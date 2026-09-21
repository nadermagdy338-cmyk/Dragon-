package nd.max.core.hardware

import android.os.PowerManager

/**
 * حارس حراري للتطبيق الواحد: يخفض السقف المطلوب حين يخنق الجهاز نفسه، ويعيده حين يبرد.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * «ثيرمل» في شاشة التطبيقات لم يكن تحكّمًا حراريًّا على أكثر الأجهزة: ما يصل إلى العتاد هو
 * سقف تردد GPU (نسبة مئوية من أعلى OPP)، والمسار الحراري الوحيد الموجود في الوحدة الأصلية
 * خاصّ بXiaomi (`/sys/devices/virtual/thermal/thermal_message/sconfig`) ويسجّل
 * `PERAPP_THERMAL_UNSUPPORTED reason=sconfig_missing` على غيرها. فيبقى الملف الحقيقي الوحيد
 * للتحكم الحراري في التطبيق **ساكنًا** بينما الجهاز يخنق نفسه من تلقائه.
 *
 * وهذا الحارس يجعله حيًّا بأمان على كل جهاز:
 *
 * 1. **لا يرفع فوق ما طلبه المستخدم أبدًا.** السقف يتحرّك في الاتجاه الواحد عند السخونة،
 *    فأسوأ حالة ممكنة هي أداء أقل — لا ضرر ولا حرارة زائدة. وهذا هو شرط قبول أي متحكّم
 *    حراري تلقائي في هذا المشروع.
 * 2. **لا يخترع عتبات حرارة.** المصدر هو `PowerManager.getCurrentThermalStatus()` — تصنيف
 *    المنصة نفسها (0..6، متاح بلا جذر منذ API 29)، لا حدّ درجة نُخمّنه لكل جهاز.
 * 3. **يستهلك مقابض مُثبتة لا عقدًا جديدة.** الخفض يتم بإعادة استهداف المقبض المملوك نفسه
 *    عبر المُحكِّم ونفس خط الأساس، فيبقى استرجاع نهاية الجلسة صحيحًا. و**التنفيذ ليس هنا**:
 *    هذا الصنف يقرّر *كم* يصير السقف ([nextCeiling] / [nextRangeCeiling])، ويُنفّذه
 *    `ThermalCeilingRouter` كهدف Atlas فيُختار المسار ويُتحقَّق ويُتذكَّر. فصلٌ مقصود: لو
 *    نفّذ الحارس بنفسه لصار لدينا حلقتا قرار لسلوك واحد.
 * 4. **ويعيد النيّة بمجرّد زوال الخنق**، فلا يبقى الجهاز مُقيَّدًا بعد أن برد.
 *
 * وما لا يفعلته: لا يعطّل `thermal_zone*`، ولا يوقف خدمة حرارية، ولا يكتب `sconfig`. هذا
 * الحارس **يتفاعل** مع سياسة المنصة ولا يقاومها — وهو ما نصّت عليه وثائق AOSP نفسها.
 *
 * والدالة الحاسمة [nextCeiling] خالصة (بلا عتاد) ليُقاس المنطق كاملًا باختبار وحدة.
 */
object ThermalGuard {

    /**
     * ضغط حراري كما تُعلنه المنصة (`ThermalService` / `PowerManager.getCurrentThermalStatus`).
     *
     * و`UNKNOWN` مقصود: منصة لا تُجيب ليست منصة باردة. ولا يُتصرَّف على المجهول إطلاقًا —
     * الحارس يصمت ويُعلن عدم الدعم بدل أن يخمّن.
     */
    enum class Pressure(val level: Int) {
        UNKNOWN(-1),
        NONE(0),
        LIGHT(1),
        MODERATE(2),
        SEVERE(3),
        CRITICAL(4),
        EMERGENCY(5),
        SHUTDOWN(6);

        /** هل تُعلن المنصة خنقًا فعليًّا الآن؟ (MODERATE وما فوقها) */
        val isThrottling: Boolean get() = level >= MODERATE.level

        companion object {
            fun fromPlatform(status: Int): Pressure = entries.firstOrNull { it.level == status } ?: UNKNOWN
        }
    }

    /**
     * السقف الذي يجب أن يكون عليه المقبض الآن.
     *
     * @param requestedHz ما طلبه المستخدم (أو ما حسِبته خطة البروفايل) — **الحدّ الأعلى المطلق**.
     * @param currentHz السقف الذي وضعه الحارس في الدورة السابقة، أو `null` إن لم يخفض بعد.
     * @param ladder سلّم الترددات المُعلنة من الجهاز، تصاعديًّا، وكلها ≤ [requestedHz].
     *
     * والقاعدتان:
     *  - بلا خنق (أو ضغط مجهول) ⇒ [requestedHz]: النيّة تُعاد كما هي.
     *  - مع خنق ⇒ **درجة واحدة** أسفل السقف الحالي، ولا تحت أدنى درجة مُعلنة. ودرجة واحدة
     *    كل دورة (كل عشر ثوانٍ) لأن الهبوط المفاجئ يقرأ كعطل، ولأنّ المنصة أصدرت إشارة
     *    مبكرة يُقصد منها أن يتفاعل المتلقّي تدريجيًّا.
     *
     * وإن كان السقف أدنى درجة مُعلنة أصلًا فيُعاد كما هو (لا 0 ولا قيمة غير قابلة للحمل).
     */
    fun nextCeiling(
        requestedHz: Long,
        currentHz: Long?,
        ladder: List<Long>,
        pressure: Pressure,
    ): Long {
        val usable = ladder.filter { it > 0L && it <= requestedHz }.distinct().sorted()
        if (usable.isEmpty()) return requestedHz
        if (!pressure.isThrottling) return requestedHz

        val reference = currentHz?.takeIf { it in usable } ?: requestedHz
        val lower = usable.lastOrNull { it < reference }
        return lower ?: usable.first()
    }

    /**
     * نفس القرار لكن لمدى `min:max` (سقوف CPU لكل سياسة).
     *
     * وقاعدتان مقصودتان:
     * - **الشكل يُحفظ**: حدّ أدنى فارغ يبقى فارغًا. لو ملأناه لصار الطلب "ثبّت الأرضية" بدل
     *   "اتركها"، وهو تغيير معنى لا تغيير قيمة.
     * - **الأرضية تتبع السقف هبوطًا**: مدى بـ`min > max` ترفضه النواة، فيُقيَّد الأدنى بالسقف
     *   الجديد. وهذا تعديل في الحدّ الأعلى وحده: الحارس يقيّد ولا يرفع.
     *
     * ويُعاد `null` لمدى غير رقمي أو بلا سقف مقروء — "لا يمكن التخطيط" لا "لا خنق".
     */
    fun nextRangeCeiling(
        userRange: String,
        currentRange: String?,
        ladder: List<Long>,
        pressure: Pressure,
    ): String? {
        val parts = userRange.split(":", limit = 2)
        if (parts.size != 2) return null
        val userMax = parts[1].trim().toLongOrNull() ?: return null
        val userMin = parts[0].trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        val currentMax = currentRange?.split(":", limit = 2)?.getOrNull(1)?.trim()?.toLongOrNull()
        val nextMax = nextCeiling(userMax, currentMax, ladder, pressure)
        val minText = userMin?.coerceAtMost(nextMax)?.toString().orEmpty()
        return "$minText:$nextMax"
    }

    /**
     * الضغط الحالي من المنصة. `null` تعني «سؤال غير ممكن الآن» — ويُترجم عند المستدعي
     * إلى [Pressure.UNKNOWN] مع **تسجيل عدم الدعم**، لا إلى «لا خنق».
     */
    fun readPressure(powerManager: PowerManager?): Pressure {
        val manager = powerManager ?: return Pressure.UNKNOWN
        return runCatching { Pressure.fromPlatform(manager.currentThermalStatus) }.getOrDefault(Pressure.UNKNOWN)
    }
}
