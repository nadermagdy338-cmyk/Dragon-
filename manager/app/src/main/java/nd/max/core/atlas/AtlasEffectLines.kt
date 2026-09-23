package nd.max.core.atlas

/**
 * أسطر الأثر — الجسر بين ما **قِسناه** وما **يُقرأ**.
 *
 * تقرير المستخدم كان يجيب سؤالًا واحدًا: «ماذا طلبنا وماذا قرأت العقدة؟». وكان هذا كافيًا ليبدو
 * العطب نجاحًا: في حزمة ٢٠٢٦-٠٩-٢٢ ظهر سطرٌ حرفيًّا يقول
 * `PERAPP_COMMIT … applied=true verified=true live=520000000` — أي «نجح» بينما التردد لم يزد عن
 * ٥٢٠ التي خفضناها نحن. فالسؤال الذي كان غائبًا هو: **ما الذي تغيّر، وإلى أين طُلب، وهل اتّجه الاتجاه؟**
 *
 * وهذا الملف يُجيب الثلاثة برموز ثابتة (لا نصوص مُترجَمة، فلا تُترجم في رحلة)، ويحمل الهدف معه:
 * `requested=` هو ما طلبه التطبيق، فمنه يُقاس **اتجاه الهدف** لا من اسم البروفايل (اسم البروفايل
 * وسمٌ في واجهة، لا قياس).
 *
 * والقاعدة الأخيرة المهمة: سطر بلا قياس **ليس** «لم يتغير» — بل `effect=unmeasured`، لأن طمس
 * «لم نقس» في «لم يتغير» هو بالضبط الخطأ الذي وُلد هذا الجزء من أطلس لإزالته.
 */
object AtlasEffectLines {

    /**
     * بادئة الحقل في السطر وفي قناة الحالة.
     *
     * ⚠️ والحمولة نفسها **بفواصل لا بمسافات**، وهذا ليس تذوّقًا: قناة حالة per-app تفكّ السطر
     * بتقسيمه على المسافات، فقيمة أثر فيها فراغ تنقسم إلى حقلين ويقرأ القارئ نصف جملة. والفواصل
     * تبقى داخل الحقل الواحد في كلتا القناتين (السجل والملف)، وتُقرأ بالعين كما هي.
     */
    const val PREFIX: String = "effect="

    /** حمولة «لم يُقس» — بلا سبب مفقود ولا رقم مُخترع. */
    const val UNMEASURED: String = "unmeasured"

    /** سطر كامل جاهز للّصق: `effect=<حمولة>`. */
    fun line(payload: String): String = PREFIX + payload

    /**
     * سطر أثر لطلب واحد: القيمة قبل، والقيمة بعد، والطلب، فالاتجاه.
     *
     * @param before التردد المقروء **قبل** التنفيذ (هرتز في مسار GPU).
     * @param after التردد المقروء **بعد** التنفيذ والتحقّق.
     * @param requested ما طلبه التطبيق؛ منه يُشتق اتجاه الهدف. وطلب يساوي «قبل» لا يعلن اتجاهًا،
     *   فالسطر يعلن الحركة بلا حكم — وهذا ليس تعتيمًا بل رفض تسمية اتجاه لم يُطلَب.
     */
    fun requestEffect(
        metric: AtlasEffectMetric,
        before: Long?,
        after: Long?,
        requested: Long?,
        source: String,
        atMs: Long,
        bootGeneration: Long = 0L,
        privilegeGeneration: Long = 0L,
        tolerancePermille: Long = AtlasEffectMath.DEFAULT_TOLERANCE_PERMILLE,
    ): String {
        val direction = goalDirection(before, requested)
        if (before == null || after == null) {
            return buildString {
                append(UNMEASURED)
                append(",reason=").append(AtlasEffectReasons.MISSING_SAMPLE)
                requested?.let { append(",requested=").append(it) }
            }
        }
        val beforeSample = sample(metric, before.toDouble(), atMs, source, bootGeneration, privilegeGeneration)
        val afterSample = sample(metric, after.toDouble(), atMs, source, bootGeneration, privilegeGeneration)
        val delta = AtlasEffectMath.compare(
            metric = metric,
            before = beforeSample,
            after = afterSample,
            tolerancePermille = tolerancePermille,
            direction = direction ?: metric.direction,
        )
        return buildString {
            append(delta.verdict.name.lowercase())
            append(",before=").append(before)
            append(",after=").append(after)
            requested?.let { append(",requested=").append(it) }
            // والهدف المجهول يُعلَن `none` ولا يُخمَّن: سطرٌ يقول اتجاهًا لم يُطلَب أسوأ من سطر لا يقول.
            append(",goal=").append(direction?.name?.lowercase()?.replace('_', '-') ?: "none")
            append(",reason=").append(delta.reason)
            append(",permille=").append(delta.relativePermille ?: 0L)
            append(",confidence=").append(delta.confidence.name.lowercase())
        }
    }

    /**
     * اتجاه الهدف من الطلب نفسه: أعلى من الحالي ⇒ رفع، أدنى ⇒ خفض، مساوٍ ⇒ لا اتجاه معلن.
     *
     * ولماذا لا يُشتق من اسم البروفايل (`performance`/`power`): الأسماء تُغيَّر وتُترجم، والطلب
     * رقم. وإشتقاق حكم عتادي من نصّ واجهة هو عين ما يمنعه نموذج أطلس (`AtlasControlIntent`: هدف
     * مُقاس لا أمر، و[AtlasRouteEvidence] أدلّة مُرقَّمة لا أوصاف).
     */
    fun goalDirection(before: Long?, requested: Long?): AtlasEffectDirection? = when {
        before == null || requested == null -> null
        requested > before -> AtlasEffectDirection.HIGHER_IS_BETTER
        requested < before -> AtlasEffectDirection.LOWER_IS_BETTER
        else -> null
    }

    private fun sample(
        metric: AtlasEffectMetric,
        value: Double,
        atMs: Long,
        source: String,
        bootGeneration: Long,
        privilegeGeneration: Long,
    ) = AtlasEffectSample(
        metric = metric,
        value = value,
        observedAtElapsedMs = atMs,
        source = source,
        bootGeneration = bootGeneration,
        privilegeGeneration = privilegeGeneration,
        semanticStatus = AtlasSemanticStatus.INFERRED,
    )
}
