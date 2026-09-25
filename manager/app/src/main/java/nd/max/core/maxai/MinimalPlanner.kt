/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteEvidence
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.hardware.AtlasAdaptiveExecutor
import nd.max.core.hardware.AtlasRouteBinding
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot
import nd.max.core.hardware.HardwareFeature
import nd.max.core.hardware.HardwareRepairExecutor
import nd.max.core.hardware.HardwareRepairState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * أصغر تدخل كافٍ (قرار #13 — مبدأ لا خيار).
 *
 * يستبدل executeDecision القديم الذي كان يوزّع على ثماني سلاسل نصية
 * تنتهي ستٌ منها بتبديل ملف عام. هنا:
 *
 *   1. لا فجوة مقيسة ⇒ لا فعل إطلاقًا (INV-4).
 *   2. المرشحون = مقابض مكتشفة من العتاد فقط (INV-5).
 *   3. الترتيب = الأثر ÷ التكلفة × المصداقية — فالمقبض المتنازع يهبط
 *      تلقائيًا بدل أن يُعاد تجريبه (INV-6).
 *   4. خطوة واحدة على السلّم، لا قفزة إلى الحد الأقصى.
 *   5. كل كتابة تمر بالمُحكِّم مع خط أساس واسترجاع (INV-1).
 *
 * إضافة مرحلة الشرّافية: [planWithTrace] يعيد **كل** ما رآه المخطِّط —
 * المرشحين المقبولين والمستبعدين مع سبب استبعاد كل واحد. قبل ذلك كان
 * المخطِّط يُسقط المرشحين بـmapNotNull فيفقد النطاق سبب القرار نفسه،
 * فيصل للمستخدم "لا فجوة مقيسة" بلا تفسير. لا يغير هذا القرار نفسه،
 * بل يجعله قابلًا للتفسير والمراجعة.
 */
@Singleton
class MinimalPlanner @Inject constructor(
    private val atlasExecutor: AtlasAdaptiveExecutor,
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
        /** تنبؤ ما قبل التنفيذ، أو null إن لم يتعلم النموذج بعد. */
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
     * مرشّح واحد كما قيّمه المخطِّط في هذه الدورة بالضبط.
     *
     * `rejection` يحمل أحد ثوابت [MaxAiRejection] حين يُستبعد، وnull حين
     * يكون مؤهلًا وداخلًا في الترتيب.
     */
    data class CandidateTrace(
        val key: String,
        val label: String,
        val from: String?,
        val to: String?,
        val utility: Float,
        val credibility: Float,
        val predictedGain: Float?,
        val predictedThermalC: Float?,
        val predictionConfidence: Float?,
        val samples: Int,
        val rejection: String?,
        val chosen: Boolean,
    )

    /** قرار دورة واحدة مع كل ما بُني عليه. */
    data class Plan(
        val step: Step?,
        val score: Float,
        val gap: Float,
        val satisfaction: Float,
        val direction: ControlRegistry.Direction,
        val candidates: List<CandidateTrace>,
        /** true حين كانت الدرجة فوق عتبة الرضا — لا تدخل ولا حاجة له. */
        val satisfied: Boolean,
    )

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
    ): Step? = planWithTrace(controls, state, objective, appContext, satisfaction).step

    /** نفس القرار، مع سرده الكامل للدفتر والواجهة. */
    fun planWithTrace(
        controls: List<ControlRegistry.Control>,
        state: DeviceSnapshot,
        objective: Objective,
        appContext: String,
        satisfaction: Float = SATISFIED_SCORE,
    ): Plan {
        val score = objective.score(state)
        val direction = objective.preferredDirection(state)
        val gap = satisfaction - score

        // الجهاز بخير أو لا مفردات على هذا العتاد: لا تدخل.
        if (score >= satisfaction || controls.isEmpty()) {
            return Plan(
                step = null,
                score = score,
                gap = gap,
                satisfaction = satisfaction,
                direction = direction,
                candidates = emptyList(),
                satisfied = score >= satisfaction,
            )
        }

        val traces = mutableListOf<CandidateTrace>()
        val ranked = mutableListOf<Candidate>()

        controls.forEach { control ->
            val cred = credibility.credibility(control.key, direction, appContext)
            val effect = outcomeModel.expectedEffect(control.key, direction, appContext)

            val current = runCatching { control.read() }.getOrNull()
            if (current == null) {
                traces += CandidateTrace(
                    key = control.key, label = control.label, from = null, to = null,
                    utility = 0f, credibility = cred,
                    predictedGain = null, predictedThermalC = null, predictionConfidence = null,
                    samples = effect.samples, rejection = MaxAiRejection.UNREADABLE, chosen = false,
                )
                return@forEach
            }

            val next = control.step(current, direction)
            if (next == null) {
                traces += CandidateTrace(
                    key = control.key, label = control.label, from = current, to = null,
                    utility = 0f, credibility = cred,
                    predictedGain = null, predictedThermalC = null, predictionConfidence = null,
                    samples = effect.samples, rejection = MaxAiRejection.NO_STEP, chosen = false,
                )
                return@forEach
            }

            // استُبعد بالتجربة: يسخّن بلا مكسب يُذكر (تعلّم سابق مقيس).
            if (outcomeModel.isThermallyHarmful(control.key, direction, appContext)) {
                traces += CandidateTrace(
                    key = control.key, label = control.label, from = current, to = next,
                    utility = 0f, credibility = cred,
                    predictedGain = null, predictedThermalC = effect.meanThermal,
                    predictionConfidence = effect.confidence,
                    samples = effect.samples, rejection = MaxAiRejection.MEASURED_HARM, chosen = false,
                )
                return@forEach
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
                traces += CandidateTrace(
                    key = control.key, label = control.label, from = current, to = next,
                    utility = 0f, credibility = cred,
                    predictedGain = prediction.objectiveGain,
                    predictedThermalC = prediction.thermalDeltaC,
                    predictionConfidence = prediction.confidence,
                    samples = effect.samples, rejection = MaxAiRejection.PREDICTED_HARM, chosen = false,
                )
                return@forEach
            }

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

            val utility = utilityOf(
                expectedImpact = expectedImpact,
                cost = control.cost,
                credibility = cred,
                predictedThermalDeltaC = prediction?.thermalDeltaC ?: 0f,
            )

            ranked += Candidate(control, current, next, stepFraction, prediction, utility)
            traces += CandidateTrace(
                key = control.key, label = control.label, from = current, to = next,
                utility = utility, credibility = cred,
                predictedGain = prediction?.objectiveGain,
                predictedThermalC = prediction?.thermalDeltaC,
                predictionConfidence = prediction?.confidence,
                samples = effect.samples, rejection = null, chosen = false,
            )
        }

        val best = ranked.maxByOrNull { it.utility }
        if (best == null) {
            return Plan(
                step = null,
                score = score,
                gap = gap,
                satisfaction = satisfaction,
                direction = direction,
                candidates = traces.sortedByDescending { it.utility },
                satisfied = false,
            )
        }

        val predictedNote = best.prediction?.let {
            " تنبؤ=%.3f حرارة=%+.1f° ثقة=%.2f".format(it.objectiveGain, it.thermalDeltaC, it.confidence)
        } ?: " (بلا تنبؤ: تعلّم أولي)"

        val finalTraces = traces
            .map { trace ->
                if (trace.rejection == null && trace.key == best.control.key) {
                    trace.copy(chosen = true)
                } else {
                    trace
                }
            }
            .sortedWith(
                compareByDescending<CandidateTrace> { it.chosen }
                    .thenByDescending { it.utility }
            )

        return Plan(
            step = Step(
                control = best.control,
                from = best.from,
                to = best.to,
                direction = direction,
                reason = "فجوة=%.2f اتجاه=%s جدوى=%.2f".format(gap, direction.name, best.utility) +
                    predictedNote,
                stepFraction = best.stepFraction,
                predicted = best.prediction,
            ),
            score = score,
            gap = gap,
            satisfaction = satisfaction,
            direction = direction,
            candidates = finalTraces,
            satisfied = false,
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
        val target = atlasTarget(step.control.feature)
        val routeId = HardwareRepairExecutor.labelFor(step.control.key)
        val adaptive = atlasExecutor.execute(
            intent = AtlasControlIntent(
                target = target,
                goal = when (step.direction) {
                    ControlRegistry.Direction.RAISE_PERFORMANCE -> AtlasControlGoal.PERFORMANCE
                    ControlRegistry.Direction.SAVE_ENERGY -> AtlasControlGoal.EFFICIENCY
                },
                desired = step.to,
                packageName = appContext.takeIf { it.contains('.') },
            ),
            bindings = listOf(
                AtlasRouteBinding(
                    candidate = AtlasRouteCandidate(
                        id = routeId,
                        priority = 0,
                        evidence = AtlasRouteEvidence(
                            providerId = "max-ai-control",
                            transport = AtlasControlTransport.ARBITER_SYSFS,
                            target = target,
                            readable = step.from != null,
                            privilegeAvailable = true,
                            unitProven = true,
                            baselineReadable = step.from != null,
                            rollbackProven = step.from != null,
                            reviewed = true,
                        ),
                    ),
                    request = nd.max.core.hardware.HardwareRepairRequest(
                        routeId = routeId,
                        key = step.control.key,
                        owner = ControlOwnership.Owner.MAX_AI,
                        token = token,
                        desired = step.to,
                        apply = { value -> runCatching { step.control.apply(value) == value }.getOrDefault(false) },
                        read = { runCatching { step.control.read() }.getOrNull() },
                        restore = { value -> runCatching { step.control.apply(value) == value }.getOrDefault(false) },
                        baseline = step.from,
                    ),
                ),
            ),
        )
        val result = adaptive.attempts.lastOrNull()
        val blocked = result?.state == HardwareRepairState.BLOCKED
        // المصداقية تُسجَّل فقط عندما لم يُحجب المقبض ولم تُسترجع قيمته: الحجب
        // قرار ملكية، وفشل الاسترجاع يعني أن الحالة غير معروفة — وكلاهما لا
        // يقول شيئًا عن المقبض، فتسجيله يسمّم إشارة التعلّم.
        if (!blocked && result?.rollbackAttempted != true) {
            credibility.record(step.control.key, step.direction, appContext, result?.verified == true)
        }

        return Outcome(
            step = step,
            verified = result?.verified == true,
            blocked = blocked,
            actual = result?.actual,
            detail = "${step.control.key}: ${step.from ?: "?"} → ${step.to}" +
                if (adaptive.fallbackStopped) " (Atlas stopped: ${adaptive.detail})" else " (Atlas: ${adaptive.detail})",
        )
    }

    private fun atlasTarget(feature: HardwareFeature): AtlasControlTarget = when (feature) {
        HardwareFeature.CPU_FREQUENCY -> AtlasControlTarget.CPU_FREQUENCY
        HardwareFeature.CPU_GOVERNOR -> AtlasControlTarget.CPU_GOVERNOR
        HardwareFeature.GPU_FREQUENCY -> AtlasControlTarget.GPU_FREQUENCY
        HardwareFeature.GPU_GOVERNOR -> AtlasControlTarget.GPU_GOVERNOR
        HardwareFeature.THERMAL_COOLING, HardwareFeature.THERMAL_ZONES -> AtlasControlTarget.THERMAL_PROFILE
        HardwareFeature.DISPLAY_REFRESH, HardwareFeature.DISPLAY_RESOLUTION -> AtlasControlTarget.DISPLAY_REFRESH
        HardwareFeature.ZRAM -> AtlasControlTarget.MEMORY
        else -> AtlasControlTarget.STORAGE
    }

    /**
     * تقدّم التعلّم الحقيقي للعرض: كم مقبضًا تعلّم النطام أثرَه فعلًا،
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

    /** لقطة خرائط الأثر المتعلّمة — مصدر قسم "ما تعلّمه Max AI". */
    fun effectsSnapshot(): Map<String, ControlOutcomeModel.Effect> = outcomeModel.snapshot()

    /** أثر مقبض واحد في سياقه — يُقرأ قبل التنفيذ وبعده لقياس التعلّم. */
    fun effectOf(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
    ): ControlOutcomeModel.Effect = outcomeModel.expectedEffect(key, direction, appContext)

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
     * يسمم إشارة التعلّم لمقبض فعل بالضبط ما أُمر به.
     */
    fun recordSafetyVeto(step: Step, appContext: String) {
        credibility.record(step.control.key, step.direction, appContext, verified = true)
    }

    companion object {
        /**
         * دالة الجدوى النقية — قرار الترتيب بلا أي تبعية Android.
         *
         * المعادلة: (الأثر المتوقع ÷ تكلفة التدخل) × المصداقية ÷ المخاطرة
         * الحرارية.
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
