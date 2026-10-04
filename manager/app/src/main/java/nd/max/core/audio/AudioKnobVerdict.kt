/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * حكم **مقبض مؤثّر** — صافٍ وقابل للقياس على JVM (`AQ-02`…`AQ-06`).
 *
 * **ولماذا هو غير [`AudioWriteVerdict]`:** ذاك يحمل `liveLevel: Int?` لأنّ مقبضه رقمٌ واحد (مستوى دفق).
 * ومقبض المؤثّر قيمته **أيضًا** رقمٌ واحد لكن **بمعنيين**: `mB` للجهارة، و`dB` للالتفاف، وقيمة خام لمعامل
 * المنصّة. فالحكم يُخزّن `expected`/`actual` **نصًّا** كما قُرئا — فما يُقارن هو ما قُرئ، لا ما ظنّناه.
 *
 * **والقاعدة نفسها لا تتغيّر:** لا `applied=true` بلا قراءة مطابقة (شرط المالك الصريح: «لا تعرض أي خيار
 * على أنه يعمل إذا كان مجرد واجهة بدون Backend حقيقي»).
 */
package nd.max.core.audio

/**
 * حكم كتابة مقبض واحد.
 *
 * @param expected ما طُلب كتابته — نصًّا كما سيُكتب، ليكون التقابل حرفيًّا لا مُقرَّبًا.
 * @param actual ما قرأته المنصّة بعد الكتابة — و`null` غياب قراءة، وهو **ليس** عدم تطابق مُثبت.
 * @param reason رمز السبب من المحكِّم (`manual-lock` · `apply-not-verified-baseline-restored`) أو سبب
 *   مكتوب من الطبقة نفسها (`effect-not-attached` · `control-not-owned` · `band-out-of-range`).
 */
data class AudioKnobVerdict(
    val outcome: AudioWriteOutcome,
    val expected: String? = null,
    val actual: String? = null,
    val reason: String? = null,
) {
    val isApplied: Boolean get() = outcome == AudioWriteOutcome.APPLIED
}

/**
 * نتيجة المحكِّم ← حكم، بنفس ترتيب [`audioWriteVerdict`] وبنفس السبب: **«محجوب» تُفحص قبل «لم يُطبَّق»**،
 * لأنّ الكتابة التي منعها قفلٌ يدويّ تُقرأ فشلًا عامًّا فيظنّ المستخدم أنّ الشاشة معطوبة لا أنّ المنصّة
 * أو المستخدم منعها.
 */
fun audioKnobVerdict(
    attempted: Boolean,
    blocked: Boolean,
    applied: Boolean,
    verified: Boolean,
    expected: String?,
    actual: String?,
    error: String?,
): AudioKnobVerdict = when {
    !attempted -> AudioKnobVerdict(AudioWriteOutcome.NOT_ATTEMPTED, expected, actual, error)
    blocked -> AudioKnobVerdict(AudioWriteOutcome.BLOCKED, expected, actual, error)
    applied && verified -> AudioKnobVerdict(AudioWriteOutcome.APPLIED, expected, actual, null)
    else -> AudioKnobVerdict(AudioWriteOutcome.FAILED, expected, actual, error)
}

/**
 * حكم **محاولة لم تُجرَّب بسببه** — تُستعمل حين لا يصل النداء إلى المحكِّم أصلًا (مؤثّر غير مُرفَق ·
 * ملكيّة التحكّم لغيره · معرّف خارج المدى). ووُجدت لأنّ الاتحاد السابق كان يعيد `NOT_ATTEMPTED` بأسبابٍ
 * مختلفة دون نصّها، فيقرأ المستخدم «لم يُكتب» بلا سبب — وهو نصف الحقيقة.
 */
fun audioKnobNotAttempted(reason: String, expected: String? = null, actual: String? = null): AudioKnobVerdict =
    AudioKnobVerdict(AudioWriteOutcome.NOT_ATTEMPTED, expected, actual, reason)

/** رموز أسباب محرّك المؤثّرات — في موضع واحد فلا تتفرّق بين الطبقات. */
object AudioEffectReason {
    const val NOT_ATTACHED = "effect-not-attached"
    const val ATTACH_REFUSED = "effect-attach-refused"

    /**
     * فشل خطوة الإرفاق الأولى: **هندسة المنصّة الافتراضيّة** (‏`DynamicsProcessing(جلسة)` بلا
     * `Config`). وأُضيف هذا الرمز لأنّ [`ATTACH_REFUSED`] الواحد كان يطوي ثلاث خطوات مختلفة في
     * جملةٍ واحدة — فلا يُعرف أين سقطنا (تكملة ٢٣٩).
     */
    const val ATTACH_PLATFORM_DEFAULT_REFUSED = "attach-platform-default-refused"

    /** فشلت هندسة المنصّة، ثمّ هندسة **دقّة التردّد** (بأعداد النطاقات المُقاسة أو المطلوبة). */
    const val ATTACH_RESOLUTION_VARIANT_REFUSED = "attach-resolution-variant-refused"

    /** وفشلت الأخيرة كذلك: هندسة **زمن الاستقرار**. */
    const val ATTACH_TIME_VARIANT_REFUSED = "attach-time-variant-refused"

    /**
     * **فشل السلّم كله** — وهو الرمز الذي يُعرض ويُسجّل، لأنّه يعني «جرّبنا الثلاثة ولا واحد قُبل»؛
     * وما دونه من الرموز يُسجّل للتشخيص ولا يُعرض كحكم على الجهاز.
     */
    const val ATTACH_ALL_STEPS_REFUSED = "effect-attach-refused-after-all-steps"
    const val CONTROL_NOT_OWNED = "control-not-owned-by-us"

    /**
     * **الجهاز عطّل المؤثّر ونحن نملكه** (تكملة ٢٤٣ · ٣ح-أ): الكتابة عليه تُقبل وتُقرأ مطابقةً **ولا
     * تُسمع** — وهو الفرق بين سطرٍ يقول «مطبَّق» وسطرٍ يقول الحقيقة. ويُقال بـ`onEnableStatusChange`
     * قبل أن يلمس المستخدم أيّ مقبض — فلا تُعرض مقابضُ تُشير إلى صفر أثر (وهو جوهر شكوى المالك).
     */
    const val DISABLED_BY_ENGINE = "effect-disabled-by-engine"
    const val BAND_OUT_OF_RANGE = "band-out-of-range"
    const val PARAM_UNREADABLE = "param-unreadable"
    const val NOT_SUPPORTED_BY_DEVICE = "strength-not-supported-by-device"
    const val STORE_UNCONFIGURED = "control-store-unconfigured"
    const val ARBITER_UNAVAILABLE = "arbiter-unavailable"
    const val NO_MANAGER = "no-audio-manager"
    const val BELOW_API = "below-platform-version"
    const val UNKNOWN_ROUTE = "unknown-route"
}

/**
 * **ما أعلنته المنصّة عن ملكيّة التحكّم** — حالةٌ نقيّة تُقاس (`kverify-audio`) ولا تُخمَّن.
 *
 * **ولماذا نقيّة وهي تُغذّى من نداءات أندرويد:** الأخطاء التي وقعت فعلًا في هذا الملفّ ليست في النداء
 * بل في **الانتقال**: مؤثّرٌ كان لنا ثمّ أخذه تطبيق آخر (أو ضاع تمكينه) ونحن نعرض «مطبَّق». والانتقال
 * دالّةٌ نقيّة تُختبر، والخيط في أندرويد لا.
 *
 * @param controlled ما أعلنه `onControlStatusChange` — `null` ⇒ **لم تُبلَّغ بعد** (لا «ملكٌ لنا»).
 * @param enabledByEngine ما أعلنه `onEnableStatusChange` — `null` ⇒ لم تُبلَّغ بعد.
 */
data class AudioEffectControlState(
    val controlled: Boolean? = null,
    val enabledByEngine: Boolean? = null,
) {

    fun onControlStatus(value: Boolean): AudioEffectControlState = copy(controlled = value)

    fun onEnableStatus(value: Boolean): AudioEffectControlState = copy(enabledByEngine = value)

    /**
     * **هل نُجرّب الكتابة؟** — الجهل لا يمنع: مقبضٌ لم تُقَس ملكيّته لا يُقعد (والقياس الفعليّ
     * `hasControl()` يبقى هو المرجع عند الكتابة). والمنع هنا لِما **قِيس وأُعلن**.
     */
    val mayWrite: Boolean get() = controlled != false

    /** سبب المنع إن وُجد — **وهو نفس الرمز المعروض في الشاشة** (لا رمز ثانٍ للشيء نفسه). */
    val blockReason: String? get() = if (controlled == false) AudioEffectReason.CONTROL_NOT_OWNED else null

    /**
     * **والعطب المسموع — وهو الأدهى من فقد التحكّم:** مؤثّرٌ نملكه **والجهاز معطّله** (تطبيق آخر
     * عطّله، أو المهلة ضيّقته). فالكتابة عليه تُقبل وتُقرأ مطابقةً **ولا تُسمع**: «مطبَّق» و«صفر تغيير»
     * في السطر نفسه. ويُفرَّق عن «لا نملك» فلا يُخلط سببان بعقوبة واحدة.
     */
    val disabledByEngine: Boolean get() = controlled != false && enabledByEngine == false

    /**
     * سببُ المنع الثاني — `"effect-disabled-by-engine"`، ومعه يُقال للمستخدم لماذا لن يُسمع شيء.
     * **ومصدره [`disabledByEngine`] نفسه لا شرطٌ ثانٍ**: شرطان لنفس الحكم ينحرفان يومًا، وحكمٌ يُقال
     * للمستخدم لا يجوز أن يكون له مصدران.
     */
    val disabledReason: String?
        get() = if (disabledByEngine) AudioEffectReason.DISABLED_BY_ENGINE else null
}
