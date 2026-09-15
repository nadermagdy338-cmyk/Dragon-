package nd.max.core.maxai

import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot
import nd.max.core.hardware.HardwareControlArbiter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * أصغر تدخل كافٍ (قرار #13 — مبدأ لا خيار).
 *
 * يستبدل executeDecision القديم الذي كان يوزّع على ثماني سلاسل نصية
 * تنتهي ستٌّ منها بتبديل ملف عام. هنا:
 *
 *   1. لا فجوة مقيسة ⇒ لا فعل إطلاقًا (INV-4).
 *   2. المرشحون = مقابض مكتشفة من العتاد فقط (INV-5).
 *   3. الترتيب = الأثر ÷ التكلفة × المصداقية — فالمقبض المتنازع يهبط
 *      تلقائيًا بدل أن يُعاد تجريبه (INV-6).
 *   4. خطوة واحدة على السلّم، لا قفزة إلى الحد الأقصى — هذا بالضبط ما
 *      فشل في سجل الجهاز: "رفع التردد" كان يطلب السقف الكامل دفعة.
 *   5. كل كتابة تمر بالمُحكِّم مع خط أساس واسترجاع (INV-1).
 */
@Singleton
class MinimalPlanner @Inject constructor(
    private val arbiter: HardwareControlArbiter,
    private val credibility: CredibilityStore,
    private val outcomeModel: ControlOutcomeModel,
    private val responseModel: ResponseModel,
) {
    /** خطوة واحدة مخططة: مقبض واحد وقيمة واحدة وسبب مقيس. */
    data class Step(
        val control: ControlRegistry.Control,
        val from: String?,
        val to: String,
        val direction: ControlRegistry.Direction,
        val reason: String,
        /** نسبة الخطوة إلى مدى المقبض — وحدة تعلّم قابلة للنقل بين الأجهزة. */
        val stepFraction: Float = 0f,
        /** تنبؤ ما قبل التنفيذ، أو null إن لم يتعلّم النموذج بعد. */
        val predicted: ResponseModel.Prediction? = null,
    )

    /** نتيجة تنفيذ خطوة — موثّقة بقراءة حية، لا بنيّة. */
    data class Outcome(
        val step: Step?,
        val verified: Boolean,
        val blocked: Boolean,
        val actual: String?,
        val detail: String,
    ) {
        val acted: Boolean get() = step != null
    }

    /**
     * يخطط خطوة واحدة أو لا شيء.
     *
     * @param satisfaction عتبة الرضا: فوقها الجهاز "بخير" فلا تدخل.
     */
    fun plan(
        controls: List<ControlRegistry.Control>,
        state: DeviceSnapshot,
        objective: Objective,
        appContext: String,
        satisfaction: Float = SATISFIED_SCORE,
    ): Step? {
        if (controls.isEmpty()) return null

        val score = objective.score(state)
        if (score >= satisfaction) return null // الجهاز بخير: لا تدخل.

        val direction = objective.preferredDirection(state)
        val gap = satisfaction - score

        val ranked = controls.mapNotNull { control ->
            val current = runCatching { control.read() }.getOrNull() ?: return@mapNotNull null
            val next = control.step(current, direction) ?: return@mapNotNull null

            // استُبعد بالتجربة: يسخّن بلا مكسب يُذكر (تعلّم سابق مقيس).
            if (outcomeModel.isThermallyHarmful(control.key, direction, appContext)) {
                return@mapNotNull null
            }

            val stepFraction = control.stepFraction(current, next)
            val prediction = responseModel.predict(
                key = control.key,
                direction = direction,
                appContext = appContext,
                state = state,
                stepFraction = stepFraction,
            )

            // استُبعد بالتنبؤ: النموذج يتوقع ضررًا حراريًا يفوق المكسب
            // قبل أن نلمس العتاد أصلًا — هذا ما يوفّر تجارب فاشلة على
            // جهاز المستخدم بدل اكتشاف الضرر بعد وقوعه.
            if (prediction != null &&
                prediction.confidence >= TRUST_CONFIDENCE &&
                prediction.thermalDeltaC > MAX_ACCEPTABLE_THERMAL_C &&
                prediction.objectiveGain <= prediction.thermalDeltaC * THERMAL_TRADE_RATIO
            ) {
                return@mapNotNull null
            }

            val cred = credibility.credibility(control.key, direction, appContext)

            // مصدر تقدير الأثر: التنبؤ حين يكون موثوقًا، وإلا التجربة
            // المتفائلة (UCB). مزيج مرجّح بالثقة كي ينتقل العقل تدريجيًا
            // من "جرّب لتعرف" إلى "توقّع ثم اختر".
            val empirical = outcomeModel.optimisticGain(control.key, direction, appContext)
            val expectedImpact = if (prediction != null) {
                val w = prediction.confidence
                (prediction.objectiveGain * w + empirical * (1f - w))
            } else {
                empirical
            }.coerceAtLeast(MIN_EXPECTED_IMPACT)

            // العائد على التكلفة: الأثر المتوقع مقابل خشونة التدخل
            // والمخاطرة الحرارية المتوقعة، مرجّحًا بمصداقية المقبض.
            val utility = utilityOf(
                expectedImpact = expectedImpact,
                cost = control.cost,
                credibility = cred,
                predictedThermalDeltaC = prediction?.thermalDeltaC ?: 0f,
            )

            Candidate(control, current, next, stepFraction, prediction, utility)
        }.sortedByDescending { it.utility }

        val best = ranked.firstOrNull() ?: return null
        val predictedNote = best.prediction?.let {
            " تنبؤ=%.3f حرارة=%+.1f° ثقة=%.2f".format(it.objectiveGain, it.thermalDeltaC, it.confidence)
        } ?: " (بلا تنبؤ: تعلّم أولي)"
        return Step(
            control = best.control,
            from = best.from,
            to = best.to,
            direction = direction,
            reason = "فجوة=%.2f اتجاه=%s جدوى=%.2f".format(gap, direction.name, best.utility) + predictedNote,
            stepFraction = best.stepFraction,
            predicted = best.prediction,
        )
    }

    /** مرشح مرتَّب داخليًا — يحمل تنبؤه كي لا يُعاد حسابه. */
    private data class Candidate(
        val control: ControlRegistry.Control,
        val from: String?,
        val to: String,
        val stepFraction: Float,
        val prediction: ResponseModel.Prediction?,
        val utility: Float,
    )

    /**
     * ينفّذ خطوة عبر المُحكِّم مع خط أساس للاسترجاع، ثم يسجّل المصداقية
     * بالنتيجة الحقيقية المقروءة.
     */
    fun execute(step: Step, appContext: String, token: String): Outcome {
        val baseline = step.from
        val result = arbiter.submit(
            key = step.control.key,
            owner = ControlOwnership.Owner.MAX_AI,
            token = token,
            desired = step.to,
            apply = { value -> runCatching { step.control.apply(value) }.getOrNull() == value },
            read = { runCatching { step.control.read() }.getOrNull() },
            baseline = baseline,
            restore = { value -> runCatching { step.control.apply(value) }.getOrNull() == value },
        )

        // المصداقية تُسجَّل فقط عندما لم يحجب مالك أعلى المحاولة: الحجب
        // ليس فشل المقبض، بل قرار ملكية.
        if (!result.blocked) {
            credibility.record(step.control.key, step.direction, appContext, result.verified)
        }

        return Outcome(
            step = step,
            verified = result.verified,
            blocked = result.blocked,
            actual = result.actual,
            detail = "${step.control.key}: ${step.from ?: "?"} → ${step.to}" +
                if (result.blocked) " (محجوب: ${result.winner})" else " (حي: ${result.actual ?: "?"})",
        )
    }

    /**
     * تقدّم التعلّم الحقيقي للعرض: كم مقبضًا تعلّم النظام أثرَه فعلًا،
     * وكم حكمًا مقيسًا تراكم. أعداد مقيسة لا مُدّعاة — نواة Kotlin.
     */
    fun learningProgress(): LearningProgress {
        val effects = outcomeModel.snapshot()
        return LearningProgress(
            learnedKnobs = responseModel.trainedCount(),
            samples = effects.values.sumOf { it.samples.toLong() },
        )
    }

    /** أعداد تقدّم التعلّم — تُعرض في الواجهة بدل عدّاد وكيل لم يعد يُستشار. */
    data class LearningProgress(val learnedKnobs: Int, val samples: Long)

    /** ينسب الأثر المقيس إلى المقبض المنفذ وسياقه فقط. */
    fun recordMeasuredOutcome(
        step: Step,
        appContext: String,
        before: DeviceSnapshot,
        after: DeviceSnapshot,
        objective: Objective,
    ) {
        val gain = objective.score(after) - objective.score(before)
        val thermalDelta = (after.thermal - before.thermal) * 100f

        // التعلّم التجريبي: ماذا حدث فعلًا لهذا المقبض في هذا السياق.
        outcomeModel.observe(
            key = step.control.key,
            direction = step.direction,
            appContext = appContext,
            objectiveGain = gain,
            thermalDeltaC = thermalDelta,
        )

        // التعلّم التنبؤي: كيف تعتمد الاستجابة على حالة الجهاز وحجم
        // الخطوة — هذا ما يسمح لاحقًا بتقدير أثر خطوة لم تُجرَّب بعد.
        responseModel.observe(
            key = step.control.key,
            direction = step.direction,
            appContext = appContext,
            stateBefore = before,
            stepFraction = step.stepFraction,
            measuredGain = gain,
            measuredThermalDeltaC = thermalDelta,
        )
    }

    /**
     * فيتو سلامة قبل/بعد الكتابة ليس فشل المقبض: الكتابة أثبتت على العتاد
     * لكن سلطة أعلى (أسبقية السلامة) ألغتها. تسجيله كفشل في المصداقية
     * يسمّم إشارة التعلّم لمقبض فعل بالضبط ما أُمر به. هذا الفصل يمنع
     * العقل من تعلّم "المقبض X لا يثبت" بينما الحقيقة "الحرارة منعته".
     */
    fun recordSafetyVeto(step: Step, appContext: String) {
        credibility.record(step.control.key, step.direction, appContext, verified = true)
    }

    companion object {
        /**
         * دالة الجدوى النقية — قرار الترتيب بلا أي تبعية Android.
         *
         * فصلت عن [plan] لسببين: (1) تُختبر في JVM فتُحرس أخطر نقطة في
         * العقل (كيف يرجّح بين مقبضين)، و(2) تجعل المعادلة مقروءة في
         * مكان واحد بدل تشتّتها بين فروع.
         *
         * المعادلة: (الأثر المتوقع ÷ تكلفة التدخل) × المصداقية ÷ المخاطرة
         * الحرارية. المقبض الخشن المكلف يسقط، والمتنازع عليه يسقط،
         * والذي يُتوقع أن يسخّن يسقط — حتى لو بدا مغريًا على ورق الأداء.
         */
        internal fun utilityOf(
            expectedImpact: Float,
            cost: Float,
            credibility: Float,
            predictedThermalDeltaC: Float,
        ): Float {
            val thermalRisk = 1f + predictedThermalDeltaC.coerceAtLeast(0f) * THERMAL_RISK_WEIGHT
            return (expectedImpact / cost.coerceAtLeast(0.05f)) * credibility / thermalRisk
        }

        /** فوق هذه الدرجة يُعد الجهاز محققًا للهدف فلا يتدخل العقل. */
        const val SATISFIED_SCORE = 0.75f
        const val MIN_EXPECTED_IMPACT = 0.01f

        /** فوق هذه الثقة يُعتمد التنبؤ في الاستبعاد المسبق. */
        const val TRUST_CONFIDENCE = 0.45f

        /** ارتفاع حراري متوقع فوقه يلزم تبرير بمكسب حقيقي. */
        const val MAX_ACCEPTABLE_THERMAL_C = 1.2f

        /** المكسب المطلوب لكل درجة حرارة متوقعة. */
        const val THERMAL_TRADE_RATIO = 0.04f

        /** وزن المخاطرة الحرارية في مقام دالة الجدوى. */
        const val THERMAL_RISK_WEIGHT = 0.6f
    }
}
