package nd.max.core.maxai

import kotlin.math.abs

/**
 * استخلاص المعرفة — تحويل ما تعلّمه المحرك فعلًا إلى أحكام قابلة للعرض.
 *
 * كل ما هنا دالة نقية فوق بيانات مقيسة: خرائط أثر [ControlOutcomeModel]
 * وحلقات [MaxAiJournal]. لا Android، لا Context، لا حالة — كي تُحرس
 * أخطر نقطة في السرد (ما نقوله للمستخدم إنه "ثابت") باختبار JVM.
 *
 * القاعدة: الحكم يُعلن عينَاته. مقبض بعينة واحدة يبقى "قيد التعلّم"
 * ولا يُسمى ناجحًا ولا فاشلًا.
 */
object MaxAiInsights {

    enum class Verdict {
        /** مكسب متوسط موجب معتبر بعينات كافية. */
        PROVEN_HELPFUL,

        /** يسخّن بلا مكسب يوازيه — مستبعد من الترشيح. */
        PROVEN_COSTLY,

        /** عينات كافية بلا أثر يُذكر في أي اتجاه. */
        INCONCLUSIVE,

        /** لم تتراكم عينات كافية بعد. */
        LEARNING,
    }

    data class KnobInsight(
        val key: String,
        val label: String,
        val direction: ControlRegistry.Direction,
        val samples: Int,
        val meanGain: Float,
        val meanThermalC: Float,
        val confidence: Float,
        val verdict: Verdict,
        /**
         * سياق القياس: [ControlOutcomeModel.GLOBAL_CONTEXT] لسجل الجهاز
         * العام، أو حزمة تطبيق حُكم على المقبض داخلها.
         *
         * هذا الحقل هو ما يجعل المعروض صادقًا: نفس المقبض نافع في لعبة
         * ومكلف في سياق النظام، وخلط الاثنين في رقم واحد يخفي الحقيقة
         * التي جمعها المحرك أصلًا.
         */
        val context: String = ControlOutcomeModel.GLOBAL_CONTEXT,
    )

    /**
     * حقيقة تنبؤات المحرك عن نفسه — صدق ما يقول قبل أن يُصدّق.
     *
     * لماذا تُقاس: المحرك يعلن ثقة مع كل تنبؤ، والمعنى الوحيد المفيد لكلمة
     * «ثقة» أن تُقابل بما حدث. [meanAbsError] وحدها لا تكفي لأنها تخلط خطأ
     * صغيرًا في تنبؤ ضعيف بخطأ كبير في تنبؤ واثق؛ لهذا تُقاس **نسبة الإصابات
     * داخل سماحة معلنة** ([PREDICTION_TOLERANCE_GAIN]) — وهو رقم يستطيع
     * المستخدم أن يقرأه ويرفضه إن كان سيئًا.
     */
    data class PredictionAudit(
        val samples: Int = 0,
        val meanAbsError: Float? = null,
        /** نسبة التنبؤات التي وقع خطؤها داخل السماحة المعلنة (0..1). */
        val hitRate: Float? = null,
        /** متوسط الثقة التي أعلنها النموذج — تُقارن بالإصابات لا تُزينها. */
        val meanConfidence: Float? = null,
        val calibration: Calibration = Calibration.LEARNING,
    )

    /**
     * حكم المعايرة. `LEARNING` ليس حكمًا سلبيًا بل غياب عينات كافية — نفس
     * قاعدة [verdictOf]: لا يُعلن حكم بلا دليل. و`DRIFTING` تعني أن
     * التنبؤات تبتعد عن الواقع، وهي عبارة صادقة عن حالة المحرك لا عيب فيه.
     */
    enum class Calibration { LEARNING, CALIBRATED, DRIFTING }

    data class Snapshot(
        /**
         * أحكام السياق العام فقط — حكم على **الجهاز** لا على تطبيق بعينه.
         * التفصيل لكل سياق في [knobsByContext]، وهذا الحقل يبقى كما كان كي
         * لا تتغيّر دلالة أي شاشة تعتمد عليه.
         */
        val knobs: List<KnobInsight> = emptyList(),
        /** كل سياق له عينة مقيسة واحدة على الأقل، مرتّبًا بـ[contextOrder]. */
        val contexts: List<String> = emptyList(),
        /** أحكام كل سياق على حدة، بمفاتيح [contexts] نفسها. */
        val knobsByContext: Map<String, List<KnobInsight>> = emptyMap(),
        val episodes: Int = 0,
        val improved: Int = 0,
        val rolledBack: Int = 0,
        val stuck: Int = 0,
        val blocked: Int = 0,
        val failed: Int = 0,
        val unmeasured: Int = 0,
        val noAction: Int = 0,
        /** متوسط مكسب/حرارة/حكم السياق العام — يبقى للتوافق مع العرض القديم. */
        val meanAbsPredictionError: Float? = null,
        val predictionSamples: Int = 0,
        /** صدق التنبؤ الكامل (خطأ + إصابات + ثقة معلنة + حكم معايرة). */
        val prediction: PredictionAudit = PredictionAudit(),
        val totalSamples: Long = 0L,
        /** أكثر سياق تطبيق تكرّر في الحلقات، أو null قبل أي حلقة. */
        val busiestContext: String? = null,
        /**
         * التجارب المعرفية المسجلة — معدودة منفصلة ومستبعدة من عدادات
         * القرارات: التجربة تُسترجع بعد القياس دائمًا، فعدّها نجاحًا كان
         * سيضخّم نسبة النجاح بما لا يزال أثره قائمًا على الجهاز.
         */
        val explorations: Int = 0,
        /** التجارب التي انتهت بقياس فعلي (أي أنتجت معرفة). */
        val explorationsMeasured: Int = 0,
        /**
         * حلقات النطام — تدخلات سلامة وانحرافات مقباض. معدودة منفصلة
         * ومستبعدة من نسبة النجاح: لا قرار اختاره المخطِّط فيها، فعدّها
         * نجاحًا أو فشلًا كان سينسب للمحرك ما لم يقرره.
         */
        val safetyEvents: Int = 0,
        val driftEvents: Int = 0,
        /** الحلقات التي ألغاها المستخدم يدويًا خلال نافذة الربط (رفض مقيس). */
        val userOverrides: Int = 0,
        /** الحلقات التي وصلها حكم مؤجل فعلي (قياس بطارية بعد ربع ساعة). */
        val deferredMeasured: Int = 0,
    ) {
        /** نسبة الحلقات التي انتهت بتحسّن مقيس من الحلقات المُنفَّذة. */
        val actedEpisodes: Int get() = improved + rolledBack + stuck + blocked + failed + unmeasured
        val successRate: Float?
            get() = if (actedEpisodes <= 0) null else improved.toFloat() / actedEpisodes

        /** أحكام سياق واحد، أو قائمة فارغة إن لم تُقسَّ فيه عينة بعد. */
        fun knobsIn(context: String): List<KnobInsight> = knobsByContext[context].orEmpty()

        /**
         * حاصل التجارب المعرفية: كم تجربة أنتجت قياسًا من كل ما جرى.
         * null حين لا تجربة أصلًا — لا صفر يبدو فشلًا.
         */
        val probeYield: Float?
            get() = if (explorations <= 0) null
            else explorationsMeasured.toFloat() / explorations

        /**
         * نسبة تدخلاتك التي جاءت بعد قرار المحرك. null بلا قرارات مُنفَّذة:
         * النسبة التي مقامها صفر ليست صفرًا بل سؤالًا بلا جواب بعد.
         */
        val overrideRate: Float?
            get() = if (actedEpisodes <= 0) null else userOverrides.toFloat() / actedEpisodes

        /** نسبة التدخلات التي وصلها قياس بعد التنفيذ (فوريّ أو مؤجل). */
        val measurementRate: Float?
            get() = if (actedEpisodes <= 0) null
            else (actedEpisodes - unmeasured).toFloat() / actedEpisodes
    }

    /** ترتيب المقابض داخل السياق: الأغزر عينات ثم الأعلى مكسبًا. */
    private val knobOrder = compareByDescending<KnobInsight> { it.samples }
        .thenByDescending { it.meanGain }

    /** مكسب متوسط فوقه يُعد المقبض نافعًا بالقياس. */
    const val HELPFUL_GAIN = 0.004f

    /** ارتفاع حراري متوسط فوقه تُطلب مقابلة بمكسب حقيقي. */
    const val COSTLY_THERMAL_C = 1.5f

    /** نسبة المكسب المطلوبة لكل درجة حرارة (نفس منطق نواة التعلّم). */
    const val THERMAL_GAIN_RATIO = 0.03f

    /** أقل عينات لإصدار حكم — قبلها "قيد التعلّم" فقط. */
    const val MIN_SAMPLES_FOR_VERDICT = 3

    /**
     * سماحة خطأ التنبؤ المقبولة، مشتقة لا مُختارة: خمسة أمثال [HELPFUL_GAIN].
     *
     * المنطق: الخطأ الذي لا يكفي لتغيير حكم "نافع/غير نافع" لا يمكن أن يضر
     * قرارًا، فمحاسبته على أنه فشل تنبؤ كانت ستظلم محركًا صادقًا. وما فوقها
     * خطأ كبير بما يكفي ليقلب الترتيب بين مرشحين — فيُحاسب عليه.
     */
    const val PREDICTION_TOLERANCE_GAIN = HELPFUL_GAIN * 5f

    /** فوق هذه النسبة من الإصابات يُعد التنبؤ معايرًا على هذا الجهاز. */
    const val CALIBRATED_HIT_RATE = 0.6f

    fun verdictOf(samples: Int, meanGain: Float, meanThermalC: Float): Verdict = when {
        samples < MIN_SAMPLES_FOR_VERDICT -> Verdict.LEARNING
        meanThermalC > COSTLY_THERMAL_C &&
            meanGain <= abs(meanThermalC) * THERMAL_GAIN_RATIO -> Verdict.PROVEN_COSTLY
        meanGain >= HELPFUL_GAIN -> Verdict.PROVEN_HELPFUL
        meanGain <= -HELPFUL_GAIN -> Verdict.PROVEN_COSTLY
        else -> Verdict.INCONCLUSIVE
    }

    /** حكم المعايرة من عدد الأزواج (تنبؤ ومقياس) ونسبة الإصابات. */
    fun calibrationOf(samples: Int, hitRate: Float): Calibration = when {
        samples < MIN_SAMPLES_FOR_VERDICT -> Calibration.LEARNING
        hitRate >= CALIBRATED_HIT_RATE -> Calibration.CALIBRATED
        else -> Calibration.DRIFTING
    }

    /**
     * ترتيب السياقات للعرض: العام أولًا، ثم الأغزر عينات، ثم أبجديًا.
     *
     * العام أساس لأن حكم التطبيق يُقرأ مقارنةً به، والأغزر أولًا لأن ما
     * قُيس أكثر يُقرأ أولًا، والأبجدي كسرًا للتعادل كي لا يهتز ترتيب القائمة
     * بين إعادة رسم وأخرى (قائمة تتحرك بلا سبب تُفقد الثقة في محتواها).
     */
    fun contextOrder(insights: List<KnobInsight>): List<String> {
        val totals = insights.groupBy { it.context }
            .mapValues { (_, list) -> list.sumOf { it.samples } }
        return totals.keys.sortedWith(
            compareByDescending<String> { it == ControlOutcomeModel.GLOBAL_CONTEXT }
                .thenByDescending { totals[it] ?: 0 }
                .thenBy { it }
        )
    }

    /**
     * صدق التنبؤ من الحلقات وحدها.
     *
     * المصدر هو [MaxAiEpisode.predictionErrorGain] المحسوب وقت التسجيل
     * (تنبؤ ناقص مقياس)، فلا يُعاد اشتقاقه هنا بمصدر ثانٍ يمكن أن يخالف
     * الدفتر. الحلقات بلا طرفَي القياس تُستبعد بدل أن تُحتسب صفرًا.
     */
    fun predictAudit(episodes: List<MaxAiEpisode>): PredictionAudit {
        val observed = episodes.mapNotNull { episode ->
            val error = episode.predictionErrorGain ?: return@mapNotNull null
            error to episode.predictionConfidence
        }
        if (observed.isEmpty()) return PredictionAudit()
        val errors = observed.map { it.first }
        val hitRate = errors.count { it <= PREDICTION_TOLERANCE_GAIN }.toFloat() / errors.size
        val confidences = observed.mapNotNull { it.second }
        return PredictionAudit(
            samples = errors.size,
            meanAbsError = errors.average().toFloat(),
            hitRate = hitRate,
            meanConfidence = confidences.takeIf { it.isNotEmpty() }
                ?.average()?.toFloat(),
            calibration = calibrationOf(errors.size, hitRate),
        )
    }

    /**
     * يبني لقطة المعرفة.
     *
     * @param effects خرائط الأثر بمفتاح "مقبض|اتجاه|سياق" كما يخزنها
     *        [ControlOutcomeModel] — كل سياق على حدة، فالخاص لا يُخلط بالعام.
     * @param episodes حلقات الدفتر (الأحدث أولًا)، تُستخدم للتسميات
     *        البشرية وللعدادات وصدق التنبؤ.
     */
    fun derive(
        effects: Map<String, ControlOutcomeModel.Effect>,
        episodes: List<MaxAiEpisode>,
    ): Snapshot {
        val labels = HashMap<String, String>()
        episodes.forEach { episode ->
            val key = episode.knobKey ?: return@forEach
            val label = episode.knobLabel ?: return@forEach
            labels.putIfAbsent(key, label)
            episode.candidates.forEach { labels.putIfAbsent(it.key, it.label) }
        }
        episodes.forEach { episode ->
            episode.candidates.forEach { labels.putIfAbsent(it.key, it.label) }
        }

        // كل صفوف الأثر تُبنى مرة واحدة بسياقها، ثم تُقسَّم: العام يبقى في
        // [Snapshot.knobs] كما كان، وكل سياق يأخذ حكمه المستقل في
        // [Snapshot.knobsByContext]. لا تصفية تُسقط صفوفًا: ما قيس يُعرض.
        val all = effects.mapNotNull { (composed, effect) ->
            val parts = composed.split("|")
            if (parts.size != 3) return@mapNotNull null
            if (effect.samples <= 0) return@mapNotNull null
            val direction = ControlRegistry.Direction.values()
                .firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            val key = parts[0]
            KnobInsight(
                key = key,
                label = labels[key] ?: key,
                direction = direction,
                samples = effect.samples,
                meanGain = effect.meanGain,
                meanThermalC = effect.meanThermal,
                confidence = effect.confidence,
                verdict = verdictOf(effect.samples, effect.meanGain, effect.meanThermal),
                context = parts[2],
            )
        }

        val byContext = all
            .groupBy { it.context }
            .mapValues { (_, rows) -> rows.sortedWith(knobOrder) }
        val knobs = byContext[ControlOutcomeModel.GLOBAL_CONTEXT].orEmpty()

        // التجارب المعرفية تُعزل عن عدادات القرار: أهدافها مختلفة
        // (تقليل جهل) ومصيرها واحد (استرجاع)، فخلطها بالقرارات يفسد
        // معنى نسبة النجاح نفسها.
        // الفرز بنوع الحلقة لا براية exploration وحدها: حلقات السلامة
        // والانحراف ليست تجارب ولا قرارات، وخلطها بالقرارات كان سيجعل
        // كل ارتفاع حرارة يبدو كأنه خطأ من المخطِّط.
        val decisions = episodes.filter { it.kind == MaxAiEpisodeKind.DECISION }
        val probes = episodes.filter { it.kind == MaxAiEpisodeKind.PROBE }

        val audit = predictAudit(episodes)

        val busiest = episodes
            .groupingBy { it.appContext }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return Snapshot(
            knobs = knobs,
            contexts = contextOrder(all),
            knobsByContext = byContext,
            episodes = episodes.size,
            improved = decisions.count { it.verdict == MaxAiVerdict.IMPROVED },
            rolledBack = decisions.count { it.verdict == MaxAiVerdict.REGRESSED_ROLLED_BACK },
            stuck = decisions.count { it.verdict == MaxAiVerdict.REGRESSED_STUCK },
            blocked = decisions.count { it.verdict == MaxAiVerdict.BLOCKED_SAFETY },
            failed = decisions.count { it.verdict == MaxAiVerdict.WRITE_FAILED },
            unmeasured = decisions.count { it.verdict == MaxAiVerdict.UNMEASURED },
            noAction = decisions.count { it.verdict == MaxAiVerdict.NO_ACTION },
            // نفس أرقام المعايرة تُعرض في حقلين: القديم كي لا تتغير دلالة
            // أي شاشة تعتمد عليه، والجديد كحكم كامل. مصدر واحد فلا تناقض.
            meanAbsPredictionError = audit.meanAbsError,
            predictionSamples = audit.samples,
            prediction = audit,
            totalSamples = all.sumOf { it.samples.toLong() },
            busiestContext = busiest,
            explorations = probes.size,
            explorationsMeasured = probes.count { it.after != null },
            safetyEvents = episodes.count { it.kind == MaxAiEpisodeKind.SAFETY },
            driftEvents = episodes.count { it.kind == MaxAiEpisodeKind.DRIFT },
            userOverrides = episodes.count { it.userRejected },
            deferredMeasured = episodes.count { it.hasDeferredVerdict },
        )
    }
}
