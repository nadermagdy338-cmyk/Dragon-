package nd.max.core.maxai

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * طبقة الثقة — "أعرف ما الذي لا أعرفه".
 *
 * لماذا تُسبق هذه الطبقة الاستكشاف: نواة التعلّم ([ControlOutcomeModel])
 * تحمل بالفعل حدًّا أعلى متفائلًا (UCB) يجعل المقبض المجهول جاذبًا،
 * لكن ذلك الجذب يعمل **فقط** داخل ترشيح دورة فيها فجوة مقيسة. أي أن
 * مقبضًا لم يُجرَّب قط في جهاز يعمل جيدًا يبقى مجهولًا للأبد، ثم
 * يُقرَّر مصيره أول مرة في أسوأ لحظة ممكنة: تحت ضغط حراري أو حمل عالٍ.
 *
 * وحل ذلك بـ"تجربة عشوائية كي نتعلّم" مرفوض: تسخين جهاز المستخدم
 * لفائدة النموذج ليس ذكاءً. الحل هنا هو الفصل الصريح بين نوعي عدم
 * اليقين ثم تسعير الخطأ:
 *
 *  - **عدم يقين معرفي (epistemic)**: قلة العينات. يتقلّص بالقياس،
 *    وهو وحده ما يُبرّر تجربة.
 *  - **تشتت طبيعي (aleatoric)**: تقلّب الأثر نفسه بين مرة وأخرى.
 *    لا تجربة تُصلحه، فالتجربة لأجله ضجيج لا تعلّم.
 *
 * وكل ما هنا دوال نقية فوق قيم مقيسة: لا Android، لا Context، لا حالة
 * محفوظة — كي تُحرس أخطر بوابة في النظام (متى يُسمح بلمس العتاد بلا
 * فجوة مقيسة) باختبار JVM بسيط.
 */
object TrustModel {

    /** حالة معرفة المحرك بمقبض في اتجاه معيّن. */
    enum class Knowledge {
        /** لا عيّنة واحدة — أثره مجهول تمامًا. */
        UNKNOWN,

        /** عيّنات قليلة أو متضاربة: الاتجاه غير محسوم بعد. */
        UNCERTAIN,

        /** مقيس ونافع: مكسب متوسط معتبر بخطأ قياسي صغير. */
        KNOWN_USEFUL,

        /** مقيس وبلا أثر يُذكر — معرفة صالحة أيضًا: لا تُجرِّبه مرة أخرى. */
        KNOWN_NEUTRAL,

        /** مقيس ومكلف: يسخّن أو يضر بلا مقابل. */
        KNOWN_COSTLY,
    }

    /**
     * حكم الثقة على مقبض واحد في اتجاه واحد.
     *
     * @param epistemic الخطأ القياسي للمتوسط: عدم اليقين القابل للتقليل.
     * @param aleatoric الانحراف المعياري: تقلّب الأثر نفسه.
     * @param informationGain قيمة المعلومة المتوقعة من تجربة واحدة.
     */
    data class KnobTrust(
        val key: String,
        val label: String,
        val direction: ControlRegistry.Direction,
        val samples: Int,
        val meanGain: Float,
        val meanThermalC: Float,
        val epistemic: Float,
        val aleatoric: Float,
        val confidence: Float,
        val knowledge: Knowledge,
        val informationGain: Float,
    ) {
        /** هل صار الأثر معروفًا بدرجة تكفي لإخراجه من قائمة التجارب؟ */
        val settled: Boolean
            get() = knowledge == Knowledge.KNOWN_USEFUL ||
                knowledge == Knowledge.KNOWN_NEUTRAL ||
                knowledge == Knowledge.KNOWN_COSTLY
    }

    /** حالة الجهاز وقت تقييم التجربة — قياسات فقط. */
    data class RiskContext(
        val thermalC: Float,
        val batteryPercent: Int,
        val cpuLoadPercent: Int,
        val screenOn: Boolean,
        val safetyNormal: Boolean,
        val charging: Boolean = false,
        val thermalCeilingC: Float = DEFAULT_THERMAL_CEILING_C,
    ) {
        /** المسافة إلى السقف الحراري — مقام تسعير المخاطرة الحرارية. */
        val thermalHeadroomC: Float get() = thermalCeilingC - thermalC
    }

    /** تكلفة الخطأ في أسوأ الحالات لتجربة واحدة، مُفكَّكة لبنودها. */
    data class ProbeCost(
        val thermalTerm: Float,
        val energyTerm: Float,
        val disturbanceTerm: Float,
        val reversible: Boolean,
    ) {
        val worstCase: Float get() = thermalTerm + energyTerm + disturbanceTerm
    }

    /** مقبض مرشّح لتجربة معرفية مع ما يلزم لتسعيره. */
    data class ProbeCandidate(
        val trust: KnobTrust,
        val stepFraction: Float,
        val controlCost: Float,
        /** هل القيمة الحالية مقروءة فعلًا كي يكون الاسترجاع مضمونًا؟ */
        val hasBaseline: Boolean,
    )

    /** قرار البوابة: يُسمح أم لا، ولماذا بالضبط. */
    data class Gate(
        val allowed: Boolean,
        /** أحد ثوابت [Block] حين يُمنع، وnull حين يُسمح. */
        val blockReason: String?,
        val target: ProbeCandidate?,
        val cost: ProbeCost?,
    )

    /** أسباب منع التجربة — قيم ثابتة تترجمها الواجهة بلا تخمين. */
    object Block {
        const val SAFETY = "safety"
        const val THERMAL_HEADROOM = "thermal_headroom"
        const val BATTERY = "battery"
        const val CONTEXT_BUSY = "context_busy"
        const val COOLDOWN = "cooldown"
        const val SESSION_BUDGET = "session_budget"
        const val NOTHING_UNKNOWN = "nothing_unknown"
        const val IRREVERSIBLE = "irreversible"
        const val COST_TOO_HIGH = "cost_too_high"
    }

    // ── عدم اليقين ──────────────────────────────────────────

    /**
     * عدم اليقين المعرفي = الخطأ القياسي للمتوسط.
     *
     * بلا عيّنات يُعاد سابق معلن ([PRIOR_EPISTEMIC]) لا صفر: الصفر هنا
     * كذب يجعل المجهول يبدو معروفًا.
     */
    fun epistemicUncertainty(samples: Int, gainStdDev: Float): Float =
        if (samples <= 0) PRIOR_EPISTEMIC else abs(gainStdDev) / sqrt(samples.toFloat())

    /**
     * قيمة المعلومة المتوقعة من تجربة واحدة: مقدار ما سينقص من عدم
     * اليقين المعرفي. الانخفاض يتبع 1 − √(n/(n+1))، فأول عيّنة تساوي
     * معلوماتيًا أضعاف العيّنة العاشرة — ولهذا لا يتحول الاستكشاف إلى
     * تجريب مستمر بلا نهاية.
     */
    fun informationGain(samples: Int, epistemic: Float): Float {
        val n = samples.coerceAtLeast(0).toFloat()
        val reduction = 1f - sqrt(n / (n + 1f))
        return epistemic * reduction
    }

    /** حالة المعرفة من القياس وحده. */
    fun knowledgeOf(
        samples: Int,
        meanGain: Float,
        meanThermalC: Float,
        epistemic: Float,
    ): Knowledge = when {
        samples <= 0 -> Knowledge.UNKNOWN
        samples < MIN_SAMPLES_KNOWN || epistemic > EPISTEMIC_KNOWN_MAX -> Knowledge.UNCERTAIN
        meanThermalC > COSTLY_THERMAL_C &&
            meanGain <= abs(meanThermalC) * THERMAL_GAIN_RATIO -> Knowledge.KNOWN_COSTLY
        meanGain <= -USEFUL_GAIN -> Knowledge.KNOWN_COSTLY
        meanGain >= USEFUL_GAIN -> Knowledge.KNOWN_USEFUL
        else -> Knowledge.KNOWN_NEUTRAL
    }

    /** يبني حكم الثقة من أثر مقيس واحد. */
    fun assess(
        key: String,
        label: String,
        direction: ControlRegistry.Direction,
        effect: ControlOutcomeModel.Effect,
    ): KnobTrust {
        val epistemic = epistemicUncertainty(effect.samples, effect.gainStdDev)
        return KnobTrust(
            key = key,
            label = label,
            direction = direction,
            samples = effect.samples,
            meanGain = effect.meanGain,
            meanThermalC = effect.meanThermal,
            epistemic = epistemic,
            aleatoric = if (effect.samples < 2) 0f else effect.gainStdDev,
            confidence = effect.confidence,
            knowledge = knowledgeOf(
                samples = effect.samples,
                meanGain = effect.meanGain,
                meanThermalC = effect.meanThermal,
                epistemic = epistemic,
            ),
            informationGain = informationGain(effect.samples, epistemic),
        )
    }

    // ── تسعير الخطأ ─────────────────────────────────────────

    /**
     * تكلفة أسوأ حالة لتجربة واحدة (0 = بلا تكلفة، 1 = غير مقبول).
     *
     * ثلاثة بنود مستقلة كي يظهر في الواجهة **لماذا** رُفضت التجربة:
     *  - حراري: أسوأ ارتفاع معقول ÷ الهامش المتاح فعلًا.
     *  - طاقة: حجم الخطوة مرجّحًا بندرة البطارية (يُعفى عند الشحن).
     *  - إزعاج: خشونة المقبض × الحمل × كون الشاشة قيد الاستخدام.
     */
    fun probeCost(candidate: ProbeCandidate, risk: RiskContext): ProbeCost {
        val plausibleThermalC = if (candidate.trust.samples <= 0) {
            PRIOR_THERMAL_RISE_C
        } else {
            candidate.trust.meanThermalC.coerceAtLeast(0f) + UNMEASURED_THERMAL_MARGIN_C
        }
        val headroom = risk.thermalHeadroomC.coerceAtLeast(MIN_HEADROOM_DENOMINATOR_C)
        val thermalTerm = (plausibleThermalC / headroom).coerceIn(0f, 1f)

        val scarcity = if (risk.charging) 0f else {
            ((100 - risk.batteryPercent).coerceIn(0, 100)) / 100f
        }
        val energyTerm = (abs(candidate.stepFraction) * scarcity).coerceIn(0f, 1f)

        val attention = if (risk.screenOn) 1f else IDLE_ATTENTION_WEIGHT
        val load = (risk.cpuLoadPercent.coerceIn(0, 100)) / 100f
        val disturbanceTerm =
            (candidate.controlCost.coerceIn(0f, 1f) * load * attention).coerceIn(0f, 1f)

        return ProbeCost(
            thermalTerm = thermalTerm,
            energyTerm = energyTerm,
            disturbanceTerm = disturbanceTerm,
            reversible = candidate.hasBaseline,
        )
    }

    // ── البوابة ─────────────────────────────────────────────

    /**
     * هل يُسمح بتجربة معرفية الآن، وعلى أي مقبض؟
     *
     * الترتيب مقصود: شروط الجهاز أولًا (سلامة/حرارة/بطارية/انتباه)،
     * ثم ضوابط الوتيرة (تهدئة/سقف الجلسة)، وأخيرًا وجود جهل يستحق
     * القياس وتكلفة مقبولة. أول شرط يفشل هو السبب المعروض — كي يكون
     * "لماذا لم يجرّب" مفهومًا كما "لماذا جرّب".
     */
    fun gate(
        candidates: List<ProbeCandidate>,
        risk: RiskContext,
        nowMs: Long,
        lastProbeAtMs: Long,
        probesThisSession: Int,
        budget: Int = MAX_PROBES_PER_SESSION,
        cooldownMs: Long = PROBE_COOLDOWN_MS,
        maxCost: Float = MAX_PROBE_COST,
    ): Gate {
        if (!risk.safetyNormal) return blocked(Block.SAFETY)
        if (risk.thermalHeadroomC < MIN_THERMAL_HEADROOM_C) return blocked(Block.THERMAL_HEADROOM)
        if (!risk.charging && risk.batteryPercent < MIN_BATTERY_PERCENT) {
            return blocked(Block.BATTERY)
        }
        if (risk.screenOn && risk.cpuLoadPercent > MAX_BUSY_CPU_PERCENT) {
            return blocked(Block.CONTEXT_BUSY)
        }
        if (lastProbeAtMs > 0L && nowMs - lastProbeAtMs < cooldownMs) return blocked(Block.COOLDOWN)
        if (probesThisSession >= budget) return blocked(Block.SESSION_BUDGET)

        val unsettled = candidates.filter { !it.trust.settled }
        if (unsettled.isEmpty()) return blocked(Block.NOTHING_UNKNOWN)

        val reversible = unsettled.filter { it.hasBaseline }
        if (reversible.isEmpty()) return blocked(Block.IRREVERSIBLE)

        val priced = reversible.map { it to probeCost(it, risk) }
        val affordable = priced.filter { (_, cost) -> cost.worstCase <= maxCost }
        if (affordable.isEmpty()) {
            val cheapest = priced.minByOrNull { (_, cost) -> cost.worstCase }
            return Gate(
                allowed = false,
                blockReason = Block.COST_TOO_HIGH,
                target = cheapest?.first,
                cost = cheapest?.second,
            )
        }

        // بين المقبولات تكلفةً: الأعلى قيمةً معلوماتية. أي أن الاختيار
        // هو "أكثر ما أجهله وأقدر على قياسه بأمان" لا "أي شيء متاح".
        val best = affordable.maxWithOrNull(
            compareBy<Pair<ProbeCandidate, ProbeCost>> { it.first.trust.informationGain }
                .thenByDescending { it.second.worstCase }
        ) ?: return blocked(Block.NOTHING_UNKNOWN)

        return Gate(allowed = true, blockReason = null, target = best.first, cost = best.second)
    }

    private fun blocked(reason: String): Gate =
        Gate(allowed = false, blockReason = reason, target = null, cost = null)

    // ── ثوابت معلنة ─────────────────────────────────────────

    /** عدم يقين مفترض قبل أي عيّنة — يماثل السابق في نواة التعلّم. */
    const val PRIOR_EPISTEMIC = 0.15f

    /** أقل عيّنات لاعتماد حكم "معروف". */
    const val MIN_SAMPLES_KNOWN = 4

    /** فوق هذا الخطأ القياسي يبقى المقبض "غير محسوم" مهما تعددت العينات. */
    const val EPISTEMIC_KNOWN_MAX = 0.02f

    /** مكسب متوسط فوقه يُعد المقبض نافعًا (نفس عتبة استخلاص المعرفة). */
    const val USEFUL_GAIN = MaxAiInsights.HELPFUL_GAIN

    /** ارتفاع حراري متوسط فوقه يلزم مكسب يوازيه. */
    const val COSTLY_THERMAL_C = MaxAiInsights.COSTLY_THERMAL_C

    /** نسبة المكسب المطلوبة لكل درجة حرارة. */
    const val THERMAL_GAIN_RATIO = MaxAiInsights.THERMAL_GAIN_RATIO

    /** سقف حراري مرجعي لحساب الهامش حين لا يعطي العتاد سقفًا معلنًا. */
    const val DEFAULT_THERMAL_CEILING_C = 45f

    /** أقل هامش حراري تُسمح عنده أي تجربة. */
    const val MIN_THERMAL_HEADROOM_C = 8f

    /** حدٌّ أدنى للمقام كي لا ينفجر البند الحراري قرب السقف. */
    private const val MIN_HEADROOM_DENOMINATOR_C = 1f

    /** أسوأ ارتفاع معقول لمقبض لم يُقس أثره الحراري بعد. */
    const val PRIOR_THERMAL_RISE_C = 2.5f

    /** هامش فوق المتوسط المقيس — لأن المتوسط ليس أسوأ حالة. */
    private const val UNMEASURED_THERMAL_MARGIN_C = 0.5f

    /** وزن الإزعاج والشاشة مطفأة: التجربة حينها شبه غير محسوسة. */
    private const val IDLE_ATTENTION_WEIGHT = 0.2f

    /** أقل بطارية تُسمح عندها تجربة بلا شاحن. */
    const val MIN_BATTERY_PERCENT = 35

    /** فوق هذا الحمل والشاشة مضاءة يُعد السياق مشغولًا. */
    const val MAX_BUSY_CPU_PERCENT = 45

    /** أقل مسافة زمنية بين تجربتين معرفيتين. */
    const val PROBE_COOLDOWN_MS = 3_600_000L

    /** سقف التجارب في عمر العملية الواحد. */
    const val MAX_PROBES_PER_SESSION = 3

    /** سقف تكلفة أسوأ حالة المقبولة لتجربة. */
    const val MAX_PROBE_COST = 0.35f
}
