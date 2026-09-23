package nd.max.core.atlas

/**
 * «ماذا **أحدث** التحكم؟» — الجزء الذي كان غائبًا من أطلس.
 *
 * كل ما كان يقيسه أطلس قبله كان **حالة**: ما كتبناه، وما تقرأه العقدة، وهل الصدى يطابق الطلب.
 * والحالة لا تُجيب السؤال الذي يخرج به المستخدم من جلسة: «هل تحسّن شيء؟». فبين «الكتابة نجحت»
 * و«النتيجة تحسّنت» فرق كامل — والثاني هو مسؤوليتنا أيضًا.
 *
 * وهذا الملف **مفردات وحساب خالصان**: لا Android ولا قرص ولا وقت. الزمن يُمرَّر، والقراءات تأتي
 * من مصدر مُسمّى، والحكم يُحسب بقواعد معلنة قابلة للتكذيب.
 *
 * ثلاث قواعد تحكم كل نوع هنا:
 *
 * 1. **لا رقم بلا مصدر.** `source` إلزامي في كل عيّنة؛ رقم بلا منشأ ليس قياسًا.
 * 2. **لا مقارنة عبر إقلاع.** عيّنتان من جيلَي إقلاع مختلفين تصفان جهازين مختلفين سلوكيًا (حالة
 *    حرارية، جداول OPP بعد إعادة كتابة، حاكم مختلف). فالمقارنة تُرفض بـ`UNMEASURABLE` وتقول
 *    السبب — ولا تُخفَض إلى «لا تغيير» الذي هو ادّعاء قياس.
 * 3. **التقدير لا يحكم.** عيّنة من نوع `ESTIMATED` تُعدّ دليلًا للعرض ولا تُبنى عليها قرار
 *    «أُبقي/أُرجع» — لأن القرار يكتب على العتاد.
 *
 * ⚠️ واتجاه التحسّن **ليس واحدًا للجميع**، وأشهر خطأ في هذا الباب هو عكس اتجاه الحرارة:
 * `THERMAL_HEADROOM` في منصة أندرويد **أكبر = أقرب إلى الخنق** (‏٠ بارد و١٫٠ عتبة الخنق الشديد)،
 * فهو `LOWER_IS_BETTER`؛ بينما `THERMAL_ONSET_MS` (الزمن حتى أول خنق) أكبر = أفضل.
 */

/** الوحدة التي يُقاس بها الأثر — منفصلة عن [AtlasUnit] التي تصف وحدات واجهات النواة. */
enum class AtlasEffectUnit {
    MILLI_SECOND,
    WATT,
    KILO_HERTZ,

    /** هرتز خام — للقراءة اللحظية من العقدة (`currentFrequencyHz`)؛ وما عداها كيلوهرتز. */
    HERTZ,
    PERCENT,

    /** نسبة بلا وحدة (مثل `thermal headroom`: ٠٫٢ · ١٫٠). */
    RATIO,
}

/** أي اتجاه هو الأحسن لهذا المقياس. غياب الاتجاه يعني «لا حكم» لا «تعادل». */
enum class AtlasEffectDirection { LOWER_IS_BETTER, HIGHER_IS_BETTER }

/**
 * مقاييس الأثر المعتمدة.
 *
 * وليست قائمة أمنيات: كل مقياس هنا له مصدر موجود في المشروع اليوم (انظر `source` في العيّنة)،
 * وما لا مصدر له لا يُدرَج. وإضافة مقياس بلا مصدر حقيقي تُنتج عيّنات `ESTIMATED` لا تُحكم.
 */
enum class AtlasEffectMetric(val unit: AtlasEffectUnit, val direction: AtlasEffectDirection) {
    /** زمن الإطار عند الشريحة ٩٥ — مقياس السلاسة الذي يراه المستخدم. */
    FRAME_TIME_P95(AtlasEffectUnit.MILLI_SECOND, AtlasEffectDirection.LOWER_IS_BETTER),

    /** معدل الإطارات المتوسط. */
    FPS_MEAN(AtlasEffectUnit.PERCENT, AtlasEffectDirection.HIGHER_IS_BETTER),

    /** نسبة الإطارات المتأخرة (jank) من إجمالي الإطارات. */
    JANK_PERCENT(AtlasEffectUnit.PERCENT, AtlasEffectDirection.LOWER_IS_BETTER),

    /** متوسط التردد المرجَّح بالتِكّات من `stats/time_in_state` — أدقّ من قراءة لحظية. */
    CPU_FREQUENCY_TICK_WEIGHTED(AtlasEffectUnit.KILO_HERTZ, AtlasEffectDirection.HIGHER_IS_BETTER),

    /** مثيله في GPU. */
    GPU_FREQUENCY_TICK_WEIGHTED(AtlasEffectUnit.KILO_HERTZ, AtlasEffectDirection.HIGHER_IS_BETTER),

    /** قدرة الاستهلاك اللحظية (فولت × تيار من `power_supply`). */
    POWER_WATTS(AtlasEffectUnit.WATT, AtlasEffectDirection.LOWER_IS_BETTER),

    /**
     * هامش الحرارة المتنبَّأ من المنصة (`getThermalHeadroom`).
     * ⚠️ واتجاهه `LOWER_IS_BETTER`: ‏١٫٠ = عتبة الخنق الشديد، و٠ = بارد.
     */
    THERMAL_HEADROOM(AtlasEffectUnit.RATIO, AtlasEffectDirection.LOWER_IS_BETTER),

    /** الزمن المنقضي من بدء الحمل حتى أول خنق — «كم صمد الجهاز؟». */
    THERMAL_ONSET_MS(AtlasEffectUnit.MILLI_SECOND, AtlasEffectDirection.HIGHER_IS_BETTER),

    /**
     * تردد GPU **لحظي** كما تقرأه العقدة بعد التنفيذ — أضعف من المرجَّح بالتِكّات.
     *
     * وهذا الضعف يُقال ولا يُخفى: قراءة لحظية تقول «إلى أين استقرّ الحاكم الآن»، لا «أين أقام
     * المعالج في الدقيقة الماضية». وهي المقياس المتاح في مسار per-app (المُحكِّم يقرأ العقدة قبل
     * الكتابة وبعدها)، فالاكتفاء بها هناك صحيح؛ واستعمالها كأنها إقامة ليس.
     *
     * ووحدتها هرتز لأن مسار GPU في هذا المشروع يقرأ العقدة بالهرتز وجوبًا
     * (`GpuHardwareBackend.currentFrequencyHz`) — ولا تُنسب إلى هذه الوحدة قيمة من مسار لا يعرف وحدته.
     */
    GPU_FREQUENCY_INSTANT(AtlasEffectUnit.HERTZ, AtlasEffectDirection.HIGHER_IS_BETTER),

    /** نظيره في CPU — نفس الحدود ونفس الصدق. */
    CPU_FREQUENCY_INSTANT(AtlasEffectUnit.HERTZ, AtlasEffectDirection.HIGHER_IS_BETTER),
}

/**
 * ثقة قياس الأثر — مرتّبة من الأقوى إلى الأضعف، والمقارنة تحتاج اثنتين من الأعلى.
 *
 * وهي ليست زينة: قاعدة القبول أدناه تتطلّب `MEASURED` أو `CORROBORATED` قبل أي قرار يُكتب على
 * العتاد. و`UNMEASURABLE` ليست «صفر تغيير» بل «لا قياس» — والفرق بينهما هو موضوع هذا الملف كله.
 */
enum class AtlasEffectConfidence {
    /** مصدران مستقلان (اسمَي مصدر مختلفين) وافقا في الاتجاه. */
    CORROBORATED,

    /** مصدر واحد قاس القيمة مباشرة. */
    MEASURED,

    /** مشتقّ من غيره (مثل متوسط من عيّنتين متقاربتين) — يُعرض ولا يُحكم به. */
    ESTIMATED,

    /** لا قياس. */
    UNMEASURABLE,
}

/** عيّنة أثر واحدة: قيمة، ومصدرها، وجيل الجهاز الذي قُيست عليه. */
data class AtlasEffectSample(
    val metric: AtlasEffectMetric,
    val value: Double,
    val observedAtElapsedMs: Long,
    val source: String,
    val bootGeneration: Long,
    val privilegeGeneration: Long = 0L,
    val semanticStatus: AtlasSemanticStatus = AtlasSemanticStatus.INFERRED,
) {
    init {
        require(value.isFinite()) { "NaN and infinity are not effect samples" }
        require(observedAtElapsedMs >= 0L) { "elapsed time is non-negative" }
        require(source.isNotBlank()) { "an effect sample without a source is not a measurement" }
        require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }
    }

    val unit: AtlasEffectUnit get() = metric.unit
}

/** نافذة القياس حول تدخّل: متى قِيست «قبل»، ومتى قِيست «بعد». */
data class AtlasEffectWindow(val beforeAtElapsedMs: Long, val afterAtElapsedMs: Long) {
    init {
        require(beforeAtElapsedMs >= 0L && afterAtElapsedMs >= 0L) { "elapsed time is non-negative" }
        require(afterAtElapsedMs >= beforeAtElapsedMs) { "a window cannot end before it starts" }
    }

    val spanMs: Long get() = afterAtElapsedMs - beforeAtElapsedMs

    /**
     * هل النافذة صالحة للحكم؟
     *
     * الحدّ الأدنى ليس تحكّمًا: أثر خفض سقف تردد يحتاج زمن استقرار (تبديل الحاكم، امتلاء/تفريغ
     * حرارة) فلا يُقاس في مئة مللي ثانية. والحدّ الأعلى مقصود كذلك: نافذة طويلة جدًا تخلط
     * التدخّل بتغيّر الحمل، فنصير نقيس اللعبة لا الكتابة.
     */
    fun usable(minSpanMs: Long, maxSpanMs: Long): Boolean = spanMs in minSpanMs..maxSpanMs
}

/** نتيجة المقارنة، وكلها بأسباب نصّية ثابتة. */
enum class AtlasEffectVerdict { IMPROVED, UNCHANGED, WORSENED, UNMEASURABLE }

/**
 * فرق مقياس واحد بين عيّنتين.
 *
 * @param delta الفرق الخام (`after - before`) في وحدة المقياس، أو `null` حين لا مقارنة.
 * @param relativePermille حجم الفرق نسبةً إلى القيمة الأولى بالألف (‏١٢٠ = ‏١٢٪)، أو `null`.
 * @param reason رمز ثابت يشرح الحكم — ولا يُترك فارغًا في أي حالة.
 */
data class AtlasEffectDelta(
    val metric: AtlasEffectMetric,
    val before: AtlasEffectSample?,
    val after: AtlasEffectSample?,
    val verdict: AtlasEffectVerdict,
    val delta: Double?,
    val relativePermille: Long?,
    val confidence: AtlasEffectConfidence,
    val reason: String,
) {
    val measured: Boolean
        get() = confidence == AtlasEffectConfidence.MEASURED || confidence == AtlasEffectConfidence.CORROBORATED
}

/** مفردات أسباب الأثر — رموز ثابتة تُقرأ في السجل وفي الواجهة. */
object AtlasEffectReasons {
    const val MISSING_SAMPLE = "effect-sample-missing"
    const val BOOT_CHANGED = "effect-boot-changed"
    const val PRIVILEGE_CHANGED = "effect-privilege-changed"
    const val NOT_FINITE = "effect-value-not-finite"
    const val WITHIN_TOLERANCE = "effect-within-tolerance"
    const val IMPROVED = "effect-improved"
    const val WORSENED = "effect-worsened"
    const val ESTIMATED_ONLY = "effect-estimated-only"
    const val CONTRADICTED = "effect-sources-contradict"
}

/**
 * حساب مقارنة واحدة — دالّة خالصة بلا حالة.
 *
 * والتسامح (`tolerancePermille`) ليس ترفًا: بدون «فرق أقل من X يُعدّ ضجيجًا» سيُقرأ أي اهتزاز
 * العيّنة تحسّنًا (وهو أسوأ من عدم القياس لأنه يبني قرارًا على ضجيج). والتسامح يُمرَّر صريحًا
 * ويُسجَّل مع الحكم.
 */
object AtlasEffectMath {

    const val DEFAULT_TOLERANCE_PERMILLE: Long = 30L

    /**
     * يقارن عيّنتين.
     *
     * و[direction] تُمرَّر حين يكون الاتجاه **هدف الطلب** لا صفة المقياس: طلبُ بروفايل `power`
     * يريد ترددًا **أدنى**، وطلبُ `performance` يريد **أعلى** — والمقياس واحد. فتمرير الهدف
     * يُميّز «خُفِّض كما أردنا» من «ارتفع كما أردنا»، وكلاهما صواب في موضعه. والافتراضي هو صفة
     * المقياس، فلا يتغير سلوك أي قارئ قائم.
     */
    fun compare(
        metric: AtlasEffectMetric,
        before: AtlasEffectSample?,
        after: AtlasEffectSample?,
        tolerancePermille: Long = DEFAULT_TOLERANCE_PERMILLE,
        direction: AtlasEffectDirection = metric.direction,
    ): AtlasEffectDelta {
        require(tolerancePermille >= 0L) { "tolerance is non-negative" }
        val unmeasurable = { reason: String ->
            AtlasEffectDelta(
                metric = metric,
                before = before,
                after = after,
                verdict = AtlasEffectVerdict.UNMEASURABLE,
                delta = null,
                relativePermille = null,
                confidence = AtlasEffectConfidence.UNMEASURABLE,
                reason = reason,
            )
        }
        if (before == null || after == null) return unmeasurable(AtlasEffectReasons.MISSING_SAMPLE)
        if (before.metric != metric || after.metric != metric) return unmeasurable(AtlasEffectReasons.MISSING_SAMPLE)
        // لا مقارنة عبر إقلاع أو تغيّر صلاحية: الجهاز بعد الإقلاع ليس الجهاز نفسه في القياس.
        if (before.bootGeneration != after.bootGeneration) return unmeasurable(AtlasEffectReasons.BOOT_CHANGED)
        if (before.privilegeGeneration != after.privilegeGeneration) {
            return unmeasurable(AtlasEffectReasons.PRIVILEGE_CHANGED)
        }
        if (!before.value.isFinite() || !after.value.isFinite()) return unmeasurable(AtlasEffectReasons.NOT_FINITE)
        if (before.observedAtElapsedMs > after.observedAtElapsedMs) return unmeasurable(AtlasEffectReasons.MISSING_SAMPLE)

        val rawDelta = after.value - before.value
        val sign = when (direction) {
            AtlasEffectDirection.HIGHER_IS_BETTER -> 1.0
            AtlasEffectDirection.LOWER_IS_BETTER -> -1.0
        }
        val improvement = rawDelta * sign
        val scale = kotlin.math.abs(before.value).takeIf { it > EPSILON } ?: kotlin.math.abs(after.value)
        val relativePermille = if (scale > EPSILON) ((rawDelta / scale) * 1000.0).toLong() else null
        val withinTolerance = relativePermille == null || kotlin.math.abs(relativePermille) <= tolerancePermille
        val verdict = when {
            withinTolerance -> AtlasEffectVerdict.UNCHANGED
            improvement > 0.0 -> AtlasEffectVerdict.IMPROVED
            else -> AtlasEffectVerdict.WORSENED
        }
        val confidence = when {
            before.source == after.source -> AtlasEffectConfidence.MEASURED
            else -> AtlasEffectConfidence.CORROBORATED
        }
        val reason = when (verdict) {
            AtlasEffectVerdict.IMPROVED -> AtlasEffectReasons.IMPROVED
            AtlasEffectVerdict.WORSENED -> AtlasEffectReasons.WORSENED
            else -> AtlasEffectReasons.WITHIN_TOLERANCE
        }
        return AtlasEffectDelta(
            metric = metric,
            before = before,
            after = after,
            verdict = verdict,
            delta = rawDelta,
            relativePermille = relativePermille,
            confidence = confidence,
            reason = reason,
        )
    }

    /**
     * تقاطع مصدرين على المقياس نفسه: هل وافقا؟
     *
     * والمقارنة هنا على **الاتجاه** لا على القيمة (مصدران بوحدتين مختلفتين لا تُقارن قيمهما)،
     * فالاتفاق يعني «تحسّن عند الاثنين»، والاختلاف يُعلَن `CONTRADICTED` **بالمصدرين وقيمتيهما**
     * ولا يُختار أحدهما — وهو نفس مبدأ `I-21` في وثيقة الفجوات.
     */
    fun corroborate(primary: AtlasEffectDelta, secondary: AtlasEffectDelta): AtlasEffectDelta {
        // سبب الأساس يُحفَظ كما هو حين يكون هو نفسه غير مقيس: «إقلاع تغيّر» أدقّ من «تقدير فقط»،
        // وتغييره هنا يُفقد السبب الحقيقي الذي جاء به القياس.
        if (!primary.measured) return primary
        if (!secondary.measured) return primary.copy(reason = AtlasEffectReasons.ESTIMATED_ONLY)
        val primaryDirection = directionSign(primary)
        val secondaryDirection = directionSign(secondary)
        return when {
            primaryDirection == 0 && secondaryDirection == 0 ->
                primary.copy(confidence = AtlasEffectConfidence.CORROBORATED, reason = AtlasEffectReasons.WITHIN_TOLERANCE)

            primaryDirection == secondaryDirection ->
                primary.copy(confidence = AtlasEffectConfidence.CORROBORATED, reason = primary.reason)

            else -> primary.copy(
                verdict = AtlasEffectVerdict.UNMEASURABLE,
                confidence = AtlasEffectConfidence.UNMEASURABLE,
                delta = null,
                relativePermille = null,
                reason = AtlasEffectReasons.CONTRADICTED,
            )
        }
    }

    private fun directionSign(delta: AtlasEffectDelta): Int = when (delta.verdict) {
        AtlasEffectVerdict.IMPROVED -> 1
        AtlasEffectVerdict.WORSENED -> -1
        else -> 0
    }

    private const val EPSILON = 1e-9
}

/**
 * توزيع الإقامة على الترددات من `stats/time_in_state`.
 *
 * ولماذا هذا أهمّ من قراءة التردد اللحظي: العقدة اللحظية تقول «آخر ما طُلب»، أما فرق عيّنتين من
 * عدّادات التِكّات فيقول **أين أقام المعالج فعلًا**. لذلك متوسطه — وإن كان مرجَّحًا بالتِكّات لا
 * بالزمن — أصدق من أي قراءة لحظية.
 *
 * ⚠️ وحدّها الصادق: وحدة التِكّات (‏HZ) لا تُقرأ من الشجرة، فيُمنع تحويلها إلى ثوانٍ **بلا دليل**.
 * فما يُنشر هنا: **الأنصبة** (بلا وحدة)، والمتوسط المرجَّح بالكيلوهرتز مع وسم `INFERRED` الصريح —
 * ولا يُقدَّم أي منهما كزمن.
 */
data class AtlasResidency(
    val shares: Map<Long, Double>,
    val tickTotal: Long,
    val weightedMeanKHz: Double?,
    val metric: AtlasEffectMetric,
    val frequencyUnit: AtlasUnit,
) {
    init {
        require(tickTotal > 0L) { "a residency sample needs ticks" }
        require(shares.values.all { it.isFinite() && it >= 0.0 }) { "shares are non-negative" }
        require(frequencyUnit == AtlasUnit.KILO_HERTZ || frequencyUnit == AtlasUnit.HERTZ) {
            "residency frequencies are read in kHz or Hz, never guessed"
        }
    }

    /** أعلى درجة أقام عليها المعالج — منفصلة عن المتوسط لأن «الأعلى» ليس «الأكثر». */
    val topFrequency: Long? get() = shares.maxByOrNull { it.value }?.key
}

object AtlasResidencyMath {

    /**
     * يقرأ فرق عيّنتين من `time_in_state` ويُعيد الأنصبة.
     *
     * والقواعد المعلنة:
     * - سطر غير قابل للتحليل يُسقَط ولا يُفشل العيّنة كلها (عقدة واحدة معطوبة لا تُلغي القياس).
     * - تردد يظهر **بعد** غيابه: يُحتسب من صفر ويُعلَن في الأنصبة، لأن ظهوره حدث حقيقي بين العيّنتين.
     * - تردد يختفي أو يتراجع عدّاده (إعادة ضبط) يُسقَط، لأن الفرق السالب ليس إقامة.
     */
    fun fromTimeInState(
        before: String?,
        after: String?,
        metric: AtlasEffectMetric = AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED,
        frequencyUnit: AtlasUnit = AtlasUnit.KILO_HERTZ,
    ): AtlasResidency? {
        require(metric.unit == AtlasEffectUnit.KILO_HERTZ) { "a residency sample is a frequency metric" }
        val beforeCounts = parse(before)
        val afterCounts = parse(after)
        if (afterCounts.isEmpty()) return null
        val deltas = linkedMapOf<Long, Long>()
        afterCounts.forEach { (frequency, ticks) ->
            val previous = beforeCounts[frequency] ?: 0L
            val difference = ticks - previous
            if (difference > 0L) deltas[frequency] = difference
        }
        val total = deltas.values.sum()
        if (total <= 0L) return null
        val shares = deltas.entries.associate { (frequency, ticks) -> frequency to ticks.toDouble() / total.toDouble() }
        val weighted = deltas.entries.sumOf { (frequency, ticks) -> frequency.toDouble() * ticks.toDouble() } / total.toDouble()
        return AtlasResidency(
            shares = shares,
            tickTotal = total,
            weightedMeanKHz = weighted.takeIf { it.isFinite() && it > 0.0 },
            metric = metric,
            frequencyUnit = frequencyUnit,
        )
    }

    /** يحوّل الإقامة إلى عيّنة أثر صريحة، بمصدر إلزامي وجيل إقلاع معلَن. */
    fun toSample(
        residency: AtlasResidency,
        observedAtElapsedMs: Long,
        source: String,
        bootGeneration: Long,
        privilegeGeneration: Long = 0L,
    ): AtlasEffectSample? {
        val mean = residency.weightedMeanKHz ?: return null
        // المقياس بالكيلوهرتز، وتحويل الهرتز قسمة صريحة — لا معامل يُضبط فيُخطئ أحدهم في اتجاهه.
        val value = when (residency.frequencyUnit) {
            AtlasUnit.HERTZ -> mean / 1_000.0
            AtlasUnit.KILO_HERTZ -> mean
            else -> return null
        }
        return AtlasEffectSample(
            metric = residency.metric,
            value = value,
            observedAtElapsedMs = observedAtElapsedMs,
            source = source,
            bootGeneration = bootGeneration,
            privilegeGeneration = privilegeGeneration,
            semanticStatus = AtlasSemanticStatus.INFERRED,
        )
    }

    private fun parse(text: String?): Map<Long, Long> {
        if (text.isNullOrBlank()) return emptyMap()
        val counts = linkedMapOf<Long, Long>()
        text.lineSequence().forEach { raw ->
            val parts = raw.trim().split(' ', '\t').filter { it.isNotBlank() }
            if (parts.size < 2) return@forEach
            val frequency = parts[0].toLongOrNull() ?: return@forEach
            val ticks = parts[1].toLongOrNull() ?: return@forEach
            if (frequency > 0L && ticks >= 0L) counts[frequency] = ticks
        }
        return counts
    }
}
