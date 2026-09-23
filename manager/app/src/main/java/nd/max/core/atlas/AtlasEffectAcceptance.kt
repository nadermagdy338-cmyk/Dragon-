package nd.max.core.atlas

/**
 * سياسة القبول — «أنُبقي التدخّل أم نُرجعه؟»
 *
 * وهذا الملف هو الحدّ الفاصل بين أطلس كمراقب وأطلس كقرار. ولذلك صُمّم بحيث **لا يستطيع** أن يقول
 * «نجح» بلا قياس: القرارات ثلاثة، وواحد منها يستقبل الحالة غير المقيسة صراحةً بدل أن يجبر الحكم.
 *
 * القاعدة الأم: **`KEEP` تتطلّب قياسًا مُثبتًا**. و«لا قياس» تعني `INCONCLUSIVE` — أي «يبقى
 * التطبيق كما هو ولا ندّعي شيئًا»، لا «يبقى لأننا متفائلون» ولا «يُرجع لأننا متشكّكون».
 *
 * ولماذا لا يكفي «الطبقة قالت إن الهدف تحسّن»؟ لأن التدخّل له **كلفة** أيضًا (واط، حرارة،
 * تدهور في مقياس آخر)، فالتوقيع الكامل للقرار هدفٌ **وكلفة**: تدخّلٌ حسّن الهدف وأفسد الكلفة
 * ليس نجاحًا ولا فشلًا، بل تجارة تحتاج صاحب قرار — فتُعلَن `INCONCLUSIVE` بسبب يسمّيها.
 */
data class AtlasAcceptancePolicy(
    /** أقلّ زمن استقرار قبل الحكم: تبديل الحاكم وامتلاء/تفريغ الحرارة لا يقعان في مئة مللي ثانية. */
    val minSpanMs: Long = 3_000L,

    /** أقصى نافذة: أطول منها يخلط أثر التدخّل بتغيّر الحمل، فنقيس اللعبة لا الكتابة. */
    val maxSpanMs: Long = 180_000L,

    /** ما دون هذه النسبة يُعدّ ضجيجًا — والتسامح يُمرَّر صريحًا ولا يُخمَّن. */
    val tolerancePermille: Long = AtlasEffectMath.DEFAULT_TOLERANCE_PERMILLE,
) {
    init {
        require(minSpanMs >= 0L) { "min span is non-negative" }
        require(maxSpanMs >= minSpanMs) { "max span cannot be smaller than min span" }
        require(tolerancePermille >= 0L) { "tolerance is non-negative" }
    }
}

enum class AtlasEffectDecision { KEEP, REVERT, INCONCLUSIVE }

/** أسباب القرار — رموز ثابتة تُقرأ في السجل، وكل سبب يسمّي الحقيقة التي أنتجته. */
enum class AtlasAcceptanceReason {
    /** لا قياس أثر مُثبت: لا نجاح يُدَّعى ولا فشل يُنسب. */
    GOAL_UNMEASURED,

    /** النافذة خارج الحدود (قصيرة جدًا أو طويلة جدًا) فالحكم فيها ليس عن التدخّل. */
    WINDOW_UNUSABLE,

    /** الهدف تدهور قياسًا ⇒ يُرجع. */
    GOAL_WORSENED,

    /** الهدف تحسّن والكلفة لم تسوء ⇒ يُبقى. */
    GOAL_IMPROVED_COST_OK,

    /** الهدف تحسّن **والكلفة سوءت** ⇒ تجارة لا يقرّرها مُنفّذ آلي. */
    GOAL_IMPROVED_COST_WORSENED,

    /** لا تحسّن مُثبت والكلفة سوءت ⇒ دفعنا ثمنًا بلا مقابل. */
    COST_PAID_WITHOUT_GAIN,

    /** لا تحسّن مُثبت ولا كلفة سوءت ⇒ لا مكسب يُدَّعى، ولا ضرر يُرجع لأجله. */
    NO_CHANGE_NO_COST,
}

data class AtlasAcceptanceOutcome(
    val decision: AtlasEffectDecision,
    val reason: AtlasAcceptanceReason,
    val goal: AtlasEffectDelta,
    val cost: AtlasEffectDelta? = null,
)

object AtlasEffectAcceptance {

    /**
     * يحكم على تدخّل واحد من دلتا هدفه (ودلتا كلفته إن وُجدت) داخل نافذة معلومة.
     *
     * والترتيب مقصود: **النافذة قبل القياس، والقياس قبل الاتجاه**. نافذة غير صالحة تُبطل أي دلتا
     * مهما بدت قوية، لأن الدلتا نفسها قد تكون قِيست على تدخّل آخر داخل نافذة مخلوطة.
     */
    fun decide(
        goal: AtlasEffectDelta,
        window: AtlasEffectWindow,
        policy: AtlasAcceptancePolicy = AtlasAcceptancePolicy(),
        cost: AtlasEffectDelta? = null,
    ): AtlasAcceptanceOutcome {
        if (!window.usable(policy.minSpanMs, policy.maxSpanMs)) {
            return AtlasAcceptanceOutcome(AtlasEffectDecision.INCONCLUSIVE, AtlasAcceptanceReason.WINDOW_UNUSABLE, goal, cost)
        }
        if (!goal.measured) {
            return AtlasAcceptanceOutcome(AtlasEffectDecision.INCONCLUSIVE, AtlasAcceptanceReason.GOAL_UNMEASURED, goal, cost)
        }
        val costWorsened = cost?.measured == true && cost.verdict == AtlasEffectVerdict.WORSENED
        return when (goal.verdict) {
            AtlasEffectVerdict.WORSENED ->
                AtlasAcceptanceOutcome(AtlasEffectDecision.REVERT, AtlasAcceptanceReason.GOAL_WORSENED, goal, cost)

            AtlasEffectVerdict.IMPROVED -> AtlasAcceptanceOutcome(
                decision = AtlasEffectDecision.KEEP,
                reason = when {
                    costWorsened -> AtlasAcceptanceReason.GOAL_IMPROVED_COST_WORSENED
                    else -> AtlasAcceptanceReason.GOAL_IMPROVED_COST_OK
                },
                goal = goal,
                cost = cost,
            )

            // لا تحسّن مُثبت: القرار يتعلّق بالكلفة وحدها — وهذا هو موضع «لا ندّعي» بدقّة.
            AtlasEffectVerdict.UNCHANGED, AtlasEffectVerdict.UNMEASURABLE -> AtlasAcceptanceOutcome(
                decision = when {
                    costWorsened -> AtlasEffectDecision.REVERT
                    else -> AtlasEffectDecision.INCONCLUSIVE
                },
                reason = when {
                    costWorsened -> AtlasAcceptanceReason.COST_PAID_WITHOUT_GAIN
                    else -> AtlasAcceptanceReason.NO_CHANGE_NO_COST
                },
                goal = goal,
                cost = cost,
            )
        }
    }

    /**
     * هل يستحق هذا القرار أن يُكتب على العتاد؟
     *
     * `REVERT` وحدها تفعل ذلك — لأن `KEEP` تعني «اترك ما جرى»، و«اترك» لا تحتاج كتابة (والكتابة
     * بلا سبب تُنتج ضجيج `avc` ووميض تردد). وهذه القاعدة هي ما يجعل السياسة آمنة الاستعمال من
     * مسار تلقائي: أسوأ ما تفعله هو إعادة الحالة السابقة، لا فرض قيمة جديدة.
     */
    fun requiresWrite(decision: AtlasEffectDecision): Boolean = decision == AtlasEffectDecision.REVERT

    /** وصف نصّي ثابت للتقرير: يُقرأ في السجل ويُترجم في الواجهة، ولا يحتوي رقمًا مُخترعًا. */
    fun describe(outcome: AtlasAcceptanceOutcome): String = buildString {
        append("decision=").append(outcome.decision.name.lowercase())
        append(" reason=").append(outcome.reason.name.lowercase().replace('_', '-'))
        append(" goal=").append(outcome.goal.metric.name.lowercase())
        append(" verdict=").append(outcome.goal.verdict.name.lowercase())
        append(" confidence=").append(outcome.goal.confidence.name.lowercase())
        outcome.goal.relativePermille?.let { append(" goal_permille=").append(it) }
        outcome.goal.before?.let { append(" before=").append(trimmed(it.value)) }
        outcome.goal.after?.let { append(" after=").append(trimmed(it.value)) }
        outcome.goal.before?.let { append(" source=").append(it.source) }
        outcome.cost?.let { append(" cost_verdict=").append(it.verdict.name.lowercase()) }
    }

    private fun trimmed(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
