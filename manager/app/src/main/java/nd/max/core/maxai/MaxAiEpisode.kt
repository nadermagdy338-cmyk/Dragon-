/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

/**
 * دفتر القرارات — الذاكرة السردية لـMax AI.
 *
 * هنا تُحفظ كل دورة قرار كحلقة كاملة موثّقة بالقياس:
 *   لاحظ → لماذا يهم → قرر → ماذا غيّر → ماذا حدث → هل نجح →
 *   ما الأثر → ماذا تعلّم → ماذا فعل المستخدم بعده.
 *
 * القاعدة الملزِمة: لا حقل في هذا الملف يمكن توليده من العدم. كل رقم
 * إما قراءة من العتاد، أو ناتج نموذج تعلّم حقيقي، أو خرج مُحكِّم.
 * الحقول التي لا يوجد لها قياس تبقى null وتُعرض كذلك.
 *
 * الترميز نفسه في [MaxAiJournalCodec] — دالّات نقية يحرسها اختبار ذهاب/عودة.
 */

/** لقطة قابلة للعرض من قياسات دورة واحدة. */
data class MaxAiReading(
    val cpuLoadPercent: Int,
    val thermalC: Float,
    val batteryPercent: Int,
    val memoryPercent: Int,
    val networkPercent: Int,
    val screenOn: Boolean,
    /** درجة الحالة تحت الهدف النشط وقت القياس (0..1). */
    val objectiveScore: Float,
)

/** عيّنة في شريط التطور الزمني — من دورات المحرك الحقيقية فقط. */
data class MaxAiSample(
    val timestampMs: Long,
    val cpuLoadPercent: Int,
    val thermalC: Float,
    val batteryPercent: Int,
    val memoryPercent: Int,
    val objectiveScore: Float,
)

/** سبب استبعاد مرشّح — قيم ثابتة كي تترجمها الواجهة بلا تخمين. */
object MaxAiRejection {
    const val UNREADABLE = "unreadable"
    const val NO_STEP = "no_step"
    const val MEASURED_HARM = "measured_harm"
    const val PREDICTED_HARM = "predicted_harm"
}

/** مرشّح واحد كما رآه المخطِّط في هذه الدورة بالضبط. */
data class MaxAiCandidate(
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
    /** null = مؤهل للترشيح؛ غير ذلك أحد ثوابت [MaxAiRejection]. */
    val rejection: String?,
    val chosen: Boolean,
)

/** الحكم النهائي على الحلقة — مشتق من قياس، لا من نيّة. */
enum class MaxAiVerdict {
    /** تحسّن مقيس بعد التنفيذ. */
    IMPROVED,

    /** تراجع مقيس فاسترجع المحرك خط الأساس وأثبت الاسترجاع. */
    REGRESSED_ROLLED_BACK,

    /** تراجع مقيس وتعذّر إثبات الاسترجاع. */
    REGRESSED_STUCK,

    /** حجبته أسبقية السلامة قبل الكتابة أو بعدها. */
    BLOCKED_SAFETY,

    /** لم تثبت الكتابة على العتاد. */
    WRITE_FAILED,

    /** ثبتت الكتابة وتعذّر قياس الأثر. */
    UNMEASURED,

    /** فجوة مقيسة لكن كل المرشحين مستبعدون — مراقبة واعية لا خمول. */
    NO_ACTION,
}

/**
 * نوع الحلقة. الخط الزمني واحد لأن سؤال المستخدم واحد: «لماذا تغيّر
 * جهازي؟» — والجواب قد يكون قرار تحسين، تجربة معرفية، تدخل سلامة، أو
 * انحراف مقبض عاد لقيمة النظام. الفرز بالنوع، لا بخطّ زمني منفصل.
 */
enum class MaxAiEpisodeKind {
    /** قرار تحسين: فجوة مقيسة → خطوة → قياس → حكم. */
    DECISION,

    /** تجربة معرفية: الهدف مُشبَع والجهل هو الدافع، والاسترجاع إلزامي. */
    PROBE,

    /** تدخل [SafetyEngine]: سقف آمن فوق كل مالك. */
    SAFETY,

    /** انحراف: مقبض مملوك عاد لقيمة غير المطلوبة بعد كتابة مؤكَّدة. */
    DRIFT,
}

/** نوع تجاوز المستخدم — قيم ثابتة كي تترجمها الواجهة بلا تخمين. */
object MaxAiOverride {
    /** قفل يدوي على مقبض (ManualControlLocks). */
    const val LOCK = "lock"

    /** تطبيق بروفايل أساس يدويًا بعد تدخل المحرك. */
    const val PROFILE = "profile"
}

/** حلقة قرار واحدة كاملة. */
data class MaxAiEpisode(
    val id: Long,
    val appContext: String,
    val objectiveLabel: String,
    /** "user" تفضيل صريح / "learned" استنتاج سلوكي / "screen_off". */
    val objectiveSource: String,
    val weightPerformance: Float,
    val weightBattery: Float,
    val weightThermal: Float,
    val satisfactionTarget: Float,
    val gap: Float,
    val before: MaxAiReading,
    val after: MaxAiReading?,
    val knobKey: String?,
    val knobLabel: String?,
    val direction: String?,
    val fromValue: String?,
    val toValue: String?,
    val appliedValue: String?,
    val stepFraction: Float,
    val predictedGain: Float?,
    val predictedThermalC: Float?,
    val predictionConfidence: Float?,
    val candidates: List<MaxAiCandidate>,
    val verdict: MaxAiVerdict,
    /** حقيقة الآلة: خرج المُحكِّم/السلامة كما هو. */
    val detail: String,
    val objectiveDelta: Float?,
    val thermalDeltaC: Float?,
    val cpuDeltaPercent: Int?,
    val batteryDeltaPercent: Int?,
    val samplesBefore: Int,
    val samplesAfter: Int,
    val confidenceBefore: Float,
    val confidenceAfter: Float,
    /** |تنبؤ − مقيس| حين وُجد تنبؤ — صدق النموذج معروضًا لا مدّعى. */
    val predictionErrorGain: Float?,
    val safetyLevel: String,
    /**
     * true حين كانت هذه الحلقة **تجربة معرفية** لا قرار تحسين: الجهاز
     * كان محققًا لهدفه، وجرى القياس لأن أثر المقبض مجهول وتكلفة الخطأ
     * كانت منخفضة. تُعرض بعنوان مختلف كي لا تُقرأ كأنها تحسين مطلوب.
     */
    val exploration: Boolean = false,

    /** true حين أُعيد المقبض إلى خط أساسه بعد القياس (سلوك التجربة). */
    val reverted: Boolean = false,

    /** حالة المعرفة قبل التجربة/القرار — أحد أسماء [TrustModel.Knowledge]. */
    val knowledgeBefore: String? = null,

    /** حالة المعرفة بعد تسجيل القياس — الفرق هو التعلّم الفعلي. */
    val knowledgeAfter: String? = null,

    /** عدم اليقين المعرفي (الخطأ القياسي للمتوسط) قبل القياس. */
    val epistemicBefore: Float? = null,

    /** عدم اليقين المعرفي بعد القياس — يجب أن ينقص إن كان التعلّم حقيقيًا. */
    val epistemicAfter: Float? = null,

    /** قيمة المعلومة المتوقعة التي بُرِّرت بها التجربة. */
    val informationGain: Float? = null,

    /** تكلفة أسوأ حالة المقدّرة وقت السماح بالتجربة (0..1). */
    val probeCost: Float? = null,

    /** سبب منع التجربة — أحد ثوابت [TrustModel.Block] حين سُجِّل المنع. */
    val probeBlockReason: String? = null,

    /** نوع الحلقة — قرار، تجربة معرفية، تدخل سلامة، أو انحراف. */
    val kind: MaxAiEpisodeKind = MaxAiEpisodeKind.DECISION,

    /**
     * لحظة تجاوز المستخدم بعد هذه الحلقة: قفل يدوي أو بروفايل خلال
     * نافذة قصيرة من التدخل. أصدق إشارة رضا متاحة — سلوك فعلي لا
     * استبيان — ولذلك تُغذّى عقوبةً في [CredibilityStore].
     */
    val userOverrideAtMs: Long? = null,

    /** نوع التجاوز — أحد ثوابت [MaxAiOverride]. */
    val userOverrideKind: String? = null,

    /** لحظة إعادة فتح الحلقة للحكم المؤجل (قياس ثانٍ بعد ربع ساعة). */
    val deferredAtMs: Long? = null,

    /** انحدار البطارية المقيس في النافذة المؤجلة — ما لا تقيسه 10 ثوانٍ. */
    val deferredBatteryDeltaPercent: Int? = null,

    /** فرق الحرارة في النافذة المؤجلة. */
    val deferredThermalDeltaC: Float? = null,

    /** فرق درجة الرضا في النافذة المؤجلة. */
    val deferredObjectiveDelta: Float? = null,
) {
    val acted: Boolean get() = knobKey != null && verdict != MaxAiVerdict.NO_ACTION

    /** true حين ألغى المستخدم أثر هذه الحلقة يدويًا — رفض مقيس. */
    val userRejected: Boolean get() = userOverrideAtMs != null

    /** true حين أُعيد فتح الحلقة وقُيست النافذة المؤجلة فعلًا. */
    val hasDeferredVerdict: Boolean get() = deferredAtMs != null
}
