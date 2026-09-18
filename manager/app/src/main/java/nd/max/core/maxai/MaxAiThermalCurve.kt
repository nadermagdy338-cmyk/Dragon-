package nd.max.core.maxai

import kotlin.math.floor

/**
 * منحنى السقف الحراري — سقف يتناسب مع مقدار التجاوز لا سقفان ثابتان.
 *
 * المشكلة التي يحلها: محرك الأمان كان يستخدم **سقفين منفصلين فقط** (0.55 عند
 * عتبة التدخل، 0.35 عند الحارة الحرجة). أي أن ٤ درجات من التجاوز تُعالَج بنفس
 * القسوة، وأن الدرجة الخامسة لا تُعالَج بشيء. وهذا ينتج نمطين معروفين في
 * التحكم: **تأخر في الزيادة** (الحرارة تعلو داخل النطاق بلا استجابة متدرجة)،
 * و**تأرجح عند التنازل** (ما إن تهبط درجة واحدة تحت العتبة حتى يُرفع السقف
 * كاملًا فيرتفع الحمل ثانية).
 *
 * ما يأتي من البحث الخارجي (مفاهيم لا كود — لا شيء منسوخ):
 *  - حاكم `power_allocator` في نواة لينكس (IPA) يستخدم متحكمًا تناسبيًا-تكامليًا
 *    بثابتين مختلفين: `k_po` عند التجاوز و`k_pu` عند ما دونه، وحدًّا للبند
 *    التكاملي (`integral_cutoff`) لأن وسائل التبريد **مُكمَّمة**: لا تستطيع
 *    ضبط القدرة المطلوبة بالضبط، فيبقى خطأ ثابت لا يصلحه البند التناسبي وحده.
 *  - المتحكمات التجارية (AutoTDP وما شابهها) تستخدم **لا تماثل مقصود**: ترفع
 *    القدرة فورًا عند الهبوط تحت الهدف، ولا تخفضها إلا بعد قياسات متتالية
 *    مستقرة، وتنتبه إلى أن زمن استجابة المُشغِّل (نصف ثانية أو أكثر) يجعل
 *    التعديل الأسرع منه بلا معنى.
 *
 * القاعدة المُثبَتة هنا (وهي التي تجعله آمنًا للتسليم): **المنحنى لا يكون أبدًا
 * أخفّ من القاعدة القديمة.** عند عتبة التدخل يعطي بالضبط السقف القديم، وعند
 * الحارة الحرجة بالضبط السقف القديم، وما بينهما أشدّ أو مساوٍ، وما فوقها أشدّ.
 * والبند التكاملي **يطرح فقط** (يشدّد ولا يرخي). أي أن كل فرق عن السلوك السابق
 * هو زيادة في الحماية، لا نقصان. هذا ما يمكن إثباته باختبار JVM بلا حاجة إلى
 * جهاز — وهو شرط قبول هذه الطبقة.
 *
 * كل الدوال نقية: لا Android، لا كتابة، لا وقت داخلي — الوقت يُمرَّر إليها،
 * فتُختبر دورة بدورة بقيم صريحة.
 */
object MaxAiThermalCurve {

    /**
     * الحالة التي تعيش بين الدورات.
     *
     * @param integral شدّة التشديد المتراكمة (٠..[Config.integralCap]) — تُقاس
     *        بوحدة **نسبة السقف** لا بالدرجات، كي تُضاف مباشرةً إلى السقف.
     * @param coolStreak عدد القياسات المتتالية تحت عتبة التراجع.
     */
    data class State(
        val integral: Float = 0f,
        val coolStreak: Int = 0,
    )

    /** سبب الحكم — ثوابت تترجمها الواجهة بلا تخمين. */
    object Reason {
        /** لا تدخل: السقف مفتوح. */
        const val NORMAL = "thermal_normal"

        /** تدخل متدرّج داخل نطاق عتبة التدخل (يشمل التشديد التكاملي). */
        const val ENGAGED = "thermal_engaged"

        /** حرارة حرجة: السقف عند حده الأدنى المُعلن. */
        const val CRITICAL = "thermal_critical"

        /** تجاوز فوق الحارة الحرجة: هبوط متدرّج نحو أرضية السقف. */
        const val OVERSHOOT = "thermal_overshoot"
    }

    /**
     * @param level مستوى الحماية المطلوب **من آلة الحالة في [SafetyEngine]**
     *        (بعد الهستيريسيس والتنبؤ الأمامي) — هذا الملف لا يقرر المستوى،
     *        بل يترجمه إلى مقدار. يفصل ذلك مقدار التدخل عن قرار التدخل، فلا
     *        تُلمس قواعد السلامة المكتسبة عند تعديل المنحنى.
     * @param releaseReady true حين استُوفي شرط التبريد المتتالي، أي أن الاسترجاع
     *        مسموح الآن لا واجب.
     */
    data class Assessment(
        val capFraction: Float,
        val reason: String,
        val integral: Float,
        val coolStreak: Int,
        val releaseReady: Boolean,
    ) {
        val capping: Boolean get() = capFraction < 1f
    }

    /**
     * ثوابت المنحنى. القيم الحدّية (`engageCap`/`criticalCap`) تأتي من
     * [SafetyEngine] نفسها كي لا يوجد رقمان للحقيقة الواحدة.
     *
     * @param overshootFloorCap أقصى تشديد ممكن. لا ينزل السقف تحته أبدًا:
     *        سقف يقارب الصفر يعطّل الجهاز بلا أن يبرّده أسرع.
     * @param overshootSpanC مدى التجاوز فوق الحارة الحرجة الذي يُستوفى فيه
     *        النزول إلى الأرضية.
     * @param integralCutoffC الخطأ الذي دونَه لا يتراكم (نفس فكرة
     *        `integral_cutoff`): ضجيج قياس بحجم نصف درجة ليس انحرافًا نظاميًا.
     * @param integralGain مقدار التشديد المضاف لكل (درجة × ثانية) متراكمة.
     * @param integralCap سقف التشديد التكاملي — يمنع أن يصبح التصحيح التكاملي
     *        هو المتحكم كله.
     * @param integralDecay معامل تحلّل التشديد أثناء التبريد، فالتصحيح مؤقت
     *        ولا يبقى عقوبة دائمة على هبوط عابر.
     * @param releaseConfirmSamples عدد القياسات المتتالية تحت عتبة التراجع
     *        المطلوبة قبل الاسترجاع — يمنع التأرجح حول العتبة (كل تأرجح كان
     *        كتابتين على العتاد: تصعيد ثم استرجاع).
     */
    data class Config(
        val engageC: Float,
        val criticalC: Float,
        val releaseC: Float,
        val engageCap: Float,
        val criticalCap: Float,
        val overshootFloorCap: Float = 0.25f,
        val overshootSpanC: Float = 4f,
        val integralCutoffC: Float = 0.5f,
        val integralGain: Float = 0.0015f,
        val integralCap: Float = 0.12f,
        val integralDecay: Float = 0.9f,
        val releaseConfirmSamples: Int = 3,
    )

    /**
     * يقيّم دورة واحدة ويعيد السقف المطلوب + الحالة التالية.
     *
     * @param thermalC أعلى حرارة مقيسة الآن (°م).
     * @param level مستوى الحماية النشط (أو المرشّح) من آلة حالة الأمان.
     * @param previous حالته السابقة، أو [State] الافتراضية عند أول دورة.
     * @param intervalMs زمن الدورة الفعلي. يُستخدم كعامل تكامل حقيقي: دورة
     *        أطول تعني خطأً استمر أطول، فيجب أن تُحتسب كذلك لا أن تُحتسب دورة.
     */
    fun assess(
        thermalC: Float,
        level: SafetyLevel,
        previous: State = State(),
        config: Config,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
    ): Assessment {
        // قياس غائب أو غير معقول: لا قرار ولا تغيير حالة — الصمت أصدق من تخمين.
        if (thermalC <= 0f) {
            return Assessment(
                capFraction = 1f,
                reason = Reason.NORMAL,
                integral = previous.integral,
                coolStreak = previous.coolStreak,
                releaseReady = false,
            )
        }

        val dt = (intervalMs.toFloat() / DEFAULT_INTERVAL_MS)
            .coerceIn(MIN_DT, MAX_DT)

        val error = thermalC - config.engageC
        val cooling = thermalC < config.releaseC
        val coolStreak = if (cooling) previous.coolStreak + 1 else 0
        val integral = when {
            error > config.integralCutoffC ->
                (previous.integral + error * config.integralGain * dt)
                    .coerceAtMost(config.integralCap)
            // التبريد يمحو التشديد تدريجيًا: عقوبة هبوط عابر لا يجوز أن تبقى.
            cooling -> (previous.integral * config.integralDecay)
                .coerceIn(0f, config.integralCap)
            else -> previous.integral.coerceIn(0f, config.integralCap)
        }

        val span = config.criticalC - config.engageC
        val base = when {
            level == SafetyLevel.NORMAL -> 1f
            level == SafetyLevel.CRITICAL || thermalC >= config.criticalC -> {
                val overshoot = if (config.overshootSpanC <= 0f) {
                    1f
                } else {
                    ((thermalC - config.criticalC) / config.overshootSpanC).coerceIn(0f, 1f)
                }
                lerp(config.criticalCap, config.overshootFloorCap, overshoot)
            }
            else -> {
                val t = if (span <= 0f) 1f else (error / span).coerceIn(0f, 1f)
                lerp(config.engageCap, config.criticalCap, t)
            }
        }

        val reason = when {
            level == SafetyLevel.NORMAL -> Reason.NORMAL
            base <= config.criticalCap + FLOATING_SLACK && thermalC > config.criticalC ->
                Reason.OVERSHOOT
            base <= config.criticalCap + FLOATING_SLACK -> Reason.CRITICAL
            else -> Reason.ENGAGED
        }

        // التشديد التكاملي يُطرح ولا يُضاف: كل فرق عن القاعدة القديمة زيادةُ حماية.
        //
        // ويُطرح **من رصيد الدورة السابقة** لا من رصيد هذه الدورة: الرصيد يُمثل
        // انحرافًا استمر زمنًا، فإضافته واحتسابه في اللحظة نفسها كانت ستشدّد
        // السقف قبل أن يمرّ الوقت الذي برّر التشديد، وتُفسد أيضًا التساوي المعلن
        // عند الحدّين (0.55 عند عتبة التدخل و0.35 عند الحرجة).
        val capFraction = (base - previous.integral)
            .coerceAtLeast(config.overshootFloorCap)
            .coerceAtMost(1f)

        return Assessment(
            capFraction = if (level == SafetyLevel.NORMAL) 1f else capFraction,
            reason = reason,
            integral = integral,
            coolStreak = coolStreak,
            releaseReady = coolStreak >= config.releaseConfirmSamples,
        )
    }

    /** تقريب السقف إلى خطوة معلنة — يمنع كتابة عتاد لأجل فرق غير محسوس. */
    fun quantize(capFraction: Float, step: Float = CAP_STEP): Float {
        if (step <= 0f) return capFraction
        val steps = (capFraction / step).let { if (it < 0f) 0f else it }
        return (floor(steps + FLOATING_SLACK) * step).coerceIn(0f, 1f)
    }

    /**
     * هل يستحق الفرق الجديد كتابة على العتاد؟
     *
     * الشرط يُقاس على القيم الحقيقية لا على المخروجة بعد التقريب: لو قيست على
     * المخرجة لكفى فرق واحد من عشرة آلاف (0.5500 ← 0.5499) لتوليد كتابة، لأن
     * التقريب للأرض يجعل هذا الفرق ينزل خطوة كاملة. و`current` هو دائمًا سقف
     * مكتوب فعلًا أي على شبكة الخطوات، فالمقارنة بالمقدار الحقيقي محسومة.
     */
    fun isTighterByStep(current: Float, candidate: Float, step: Float = CAP_STEP): Boolean =
        current > 0f && candidate <= current - step + FLOATING_SLACK

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /**
     * سماحة مقارنات الفاصلة العائمة. المطلوبة هنا لأن `base` قد يأتي من
     * `lerp` بقيمة `0.35 - ε` فتُقرأ "أشدّ من الحرجة" بلا سبب حقيقي.
     */
    private const val FLOATING_SLACK = 1e-4f

    /** خطوة تقريب السقف: ٥٪ — أصغر من إدراك المستخدم، وأكبر من ضجيج الكتابة. */
    const val CAP_STEP = 0.05f

    /** زمن الدورة المرجعي الذي بُنيت عليه ثوابت التكامل (حلقة الأمان). */
    const val DEFAULT_INTERVAL_MS = 1_000L

    /** حدود عامل الزمن: دورة طويلة جدًا لا يجوز أن تُفرط في التشديد. */
    private const val MIN_DT = 0.25f
    private const val MAX_DT = 10f
}
