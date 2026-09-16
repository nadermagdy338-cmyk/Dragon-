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
    )

    data class Snapshot(
        val knobs: List<KnobInsight> = emptyList(),
        val episodes: Int = 0,
        val improved: Int = 0,
        val rolledBack: Int = 0,
        val stuck: Int = 0,
        val blocked: Int = 0,
        val failed: Int = 0,
        val unmeasured: Int = 0,
        val noAction: Int = 0,
        /** متوسط |خطأ التنبؤ| على الحلقات التي وُجد فيها تنبؤ ومقياس. */
        val meanAbsPredictionError: Float? = null,
        val predictionSamples: Int = 0,
        val totalSamples: Long = 0L,
        /** أكثر سياق تطبيق تكرّر في الحلقات، أو null قبل أي حلقة. */
        val busiestContext: String? = null,
        /**
         * التجارب المعرفية المسجلة — معدودة منفصلة ومستبعدة من عدادات
         * القرارات: التجربة تُسترجع بعد القياس دائمًا، فعدّها نجاحًا كان
         * سيضخّم نسبة النجاح بما لا يزال أثره قائمًا على الجهاز.
         */
        val explorations: Int = 0,
        /**
         * التجارب التي أكملت دورتها كاملة (طُبِّقت ثم أُعيدت لخط
         * الأساس) لا التي حُجبت أو فشلت قبل أن تصل مرحلة القياس أصلًا.
         * العلامة [MaxAiEpisode.reverted] — لا وجود قراءة `after` — هي
         * الدليل الصحيح: الاسترجاع نفسه لا يحدث إلا بعد محاولة القياس
         * (انظر توثيق الحقل)، وقد تفشل التقاطة القراءة النهائية رغم
         * اكتمال الدورة فعليًا فيبقى `after` عندها null.
         */
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
    }

    /** مكسب متوسط فوقه يُعد المقبض نافعًا بالقياس. */
    const val HELPFUL_GAIN = 0.004f

    /** ارتفاع حراري متوسط فوقه تُطلب مقابلة بمكسب حقيقي. */
    const val COSTLY_THERMAL_C = 1.5f

    /** نسبة المكسب المطلوبة لكل درجة حرارة (نفس منطق نواة التعلّم). */
    const val THERMAL_GAIN_RATIO = 0.03f

    /** أقل عينات لإصدار حكم — قبلها "قيد التعلّم" فقط. */
    const val MIN_SAMPLES_FOR_VERDICT = 3

    fun verdictOf(samples: Int, meanGain: Float, meanThermalC: Float): Verdict = when {
        samples < MIN_SAMPLES_FOR_VERDICT -> Verdict.LEARNING
        meanThermalC > COSTLY_THERMAL_C &&
            meanGain <= abs(meanThermalC) * THERMAL_GAIN_RATIO -> Verdict.PROVEN_COSTLY
        meanGain >= HELPFUL_GAIN -> Verdict.PROVEN_HELPFUL
        meanGain <= -HELPFUL_GAIN -> Verdict.PROVEN_COSTLY
        else -> Verdict.INCONCLUSIVE
    }

    /**
     * يبني لقطة المعرفة.
     *
     * @param effects خرائط الأثر بمفتاح "مقبض|اتجاه|سياق" كما يخزنها
     *        [ControlOutcomeModel]. يُقرأ منها السياق العام فقط لأن
     *        القائمة المعروضة حكم على الجهاز لا على تطبيق بعينه.
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

        val knobs = effects.mapNotNull { (composed, effect) ->
            val parts = composed.split("|")
            if (parts.size != 3) return@mapNotNull null
            if (parts[2] != ControlOutcomeModel.GLOBAL_CONTEXT) return@mapNotNull null
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
            )
        }.sortedWith(
            compareByDescending<KnobInsight> { it.samples }
                .thenByDescending { it.meanGain }
        )

        // التجارب المعرفية تُعزل عن عدادات القرار: أهدافها مختلفة
        // (تقليل جهل) ومصيرها واحد (استرجاع)، فخلطها بالقرارات يفسد
        // معنى نسبة النجاح نفسها.
        // الفرز بنوع الحلقة لا براية exploration وحدها: حلقات السلامة
        // والانحراف ليست تجارب ولا قرارات، وخلطها بالقرارات كان سيجعل
        // كل ارتفاع حرارة يبدو كأنه خطأ من المخطِّط.
        val decisions = episodes.filter { it.kind == MaxAiEpisodeKind.DECISION }
        val probes = episodes.filter { it.kind == MaxAiEpisodeKind.PROBE }

        // صدق التنبؤ يُقاس على حلقات المخطِّط فقط — قرارات وتجارب، لا
        // سلامة ولا انحراف: هذان الأخيران لا قرار للمخطِّط فيهما أصلًا،
        // فأي predictedGain/objectiveDelta موروث عليهما ليس تنبؤًا حقيقيًا.
        val errors = (decisions + probes).mapNotNull { episode ->
            val predicted = episode.predictedGain ?: return@mapNotNull null
            val measured = episode.objectiveDelta ?: return@mapNotNull null
            abs(predicted - measured)
        }

        val busiest = episodes
            .groupingBy { it.appContext }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return Snapshot(
            knobs = knobs,
            episodes = episodes.size,
            improved = decisions.count { it.verdict == MaxAiVerdict.IMPROVED },
            rolledBack = decisions.count { it.verdict == MaxAiVerdict.REGRESSED_ROLLED_BACK },
            stuck = decisions.count { it.verdict == MaxAiVerdict.REGRESSED_STUCK },
            blocked = decisions.count { it.verdict == MaxAiVerdict.BLOCKED_SAFETY },
            failed = decisions.count { it.verdict == MaxAiVerdict.WRITE_FAILED },
            unmeasured = decisions.count { it.verdict == MaxAiVerdict.UNMEASURED },
            noAction = decisions.count { it.verdict == MaxAiVerdict.NO_ACTION },
            meanAbsPredictionError = errors.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            predictionSamples = errors.size,
            totalSamples = knobs.sumOf { it.samples.toLong() },
            busiestContext = busiest,
            explorations = probes.size,
            explorationsMeasured = probes.count { it.reverted },
            safetyEvents = episodes.count { it.kind == MaxAiEpisodeKind.SAFETY },
            driftEvents = episodes.count { it.kind == MaxAiEpisodeKind.DRIFT },
            userOverrides = episodes.count { it.userRejected },
            deferredMeasured = episodes.count { it.hasDeferredVerdict },
        )
    }
}
