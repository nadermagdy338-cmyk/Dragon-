/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

/**
 * وتيرة إعادة التقييم — متى يعيد المحرك النظر، ولماذا، وإن لم يفعل فمتى.
 *
 * لماذا وُجد هذا الملف: المحرك كان يفكر كل ثلاثين ثانية فقط، ودورة فورية
 * تحدث عند طلب المستخدم وحده. فمن يشغّل لعبة ينتظر حتى ٣٠ ثانية كي يلاحظ
 * Max AI أن السياق تغيّر — والقياس الذي بُني عليه القرار صار قديمًا قبل أن
 * يبدأ. الحل ليس إلغاء الهدوء: التحكم الذي يتأرجح أسوأ من التحكم البطيء.
 * الحل هو **إبقاء الهدوء مع تغيير مؤهِّل**: لا استيقاظ إلا عند تغيّر حقيقي
 * في حقيقة يستشعرها القرار (الشاشة، نوع الاستخدام، مستوى السلامة)، ولا
 * استيقاظ أكثر من مرة كل [MIN_ADAPTIVE_INTERVAL_MS].
 *
 * وكل هذا دوال نقية بلا Android ولا حالة محفوظة: القرار «هل نستيقظ الآن؟»
 * أخطر ما يمكن أن يتحوّل إلى حلقة ضجيج على العتاد، فيُحرَس باختبار JVM.
 *
 * مدخلات الإشارة مقصورة على ما يُقاس مجانًا في حلقة السلامة كل ثانية
 * ([DeviceStateCollector.DeviceSnapshot.appIntent] و`screenOn` ومستوى
 * السلامة). لا قراءة جذرية جديدة تُضاف لأجل التوقيت: إشارة رخيصة تكفي لأن
 * القرار نفسه يقرأ السياق الدقيق (اسم الحزمة) عند تنفيذه.
 */
object MaxAiCadence {

    /**
     * الحقائق التي يستحق تغيّرها إعادة تقييم — لا القياسات كلها.
     *
     * الحرارة والحمل مستبعدان عمدًا: لكل منهما مسار أسرع يعمل كل ثانية
     * (حاكم الأمان)، وإدخالهما هنا كان سيجعل المحرك يستيقظ كل دورة بلا
     * سبب جديد. أما هذه الثلاثة فتغيّرها يغيّر **نوع القرار المطلوب**.
     */
    data class Signal(
        val screenOn: Boolean,
        /** نوع الاستخدام كما يقيسه المجمّع: 1 لعبة، 0.5 عادي، 0 شاشة مطفأة. */
        val appIntent: Float,
        val safetyLevel: SafetyLevel,
    )

    /** أسباب إعادة التقييم — ثوابت تترجمها الواجهة بلا تخمين. */
    object Reason {
        /** تبدّل مستوى حاكم الأمان الحراري. */
        const val SAFETY = "safety"

        /** أُضيئت الشاشة أو أُطفئت — هدف مختلف تمامًا. */
        const val SCREEN = "screen"

        /** تغيّر نوع الاستخدام (دخل أو خرج تطبيق ثقيل). */
        const val USAGE = "usage"
    }

    /**
     * قرار التوقيت.
     *
     * @param wake true حين يجب تشغيل دورة القرار الآن.
     * @param reason سبب الاستيقاظ، أو سبب الانتظار حين مُنع بالحد الأدنى.
     * @param waitMs كم بقي حتى يُسمح بإعادة التقييم (0 حين لا انتظار).
     */
    data class Decision(
        val wake: Boolean,
        val reason: String?,
        val waitMs: Long,
    )

    /**
     * هل نستيقظ الآن؟
     *
     * @param previous الإشارة التي بُني عليها آخر قرار، أو null قبل أول
     *        قياس (فلا معنى لمقارنة شيء بلا سابق).
     * @param lastCycleAtMs زمن آخر دورة قرار نُفِّذت فعلًا، أو 0 إن لم تجر.
     */
    fun decide(
        previous: Signal?,
        current: Signal,
        nowMs: Long,
        lastCycleAtMs: Long,
        floorMs: Long = MIN_ADAPTIVE_INTERVAL_MS,
    ): Decision {
        val reason = reasonFor(previous, current) ?: return Decision(false, null, 0L)
        val since = if (lastCycleAtMs <= 0L) floorMs else nowMs - lastCycleAtMs
        val wait = floorMs - since
        return if (wait <= 0L) Decision(true, reason, 0L) else Decision(false, reason, wait)
    }

    /**
     * ما الحقيقة التي تغيّرت — أو null حين لم يتغيّر شيء مؤهِّل.
     *
     * الترتيب مقصود: السلامة أولًا لأنها تسحب سلطة القرار، ثم الشاشة لأنها
     * تبدّل الهدف، ثم نوع الاستخدام.
     */
    fun reasonFor(previous: Signal?, current: Signal): String? = when {
        previous == null -> null
        previous.safetyLevel != current.safetyLevel -> Reason.SAFETY
        previous.screenOn != current.screenOn -> Reason.SCREEN
        previous.appIntent != current.appIntent -> Reason.USAGE
        else -> null
    }

    /**
     * أقل مسافة بين دورتين ناتجتين عن تغيّر حقيقي.
     *
     * عشرة ثوانٍ: أطول من نافذة استجابة النظام التي يقيسها المحرك (10 ث)،
     * فلا يُعاد التخطيط قبل أن يظهر أثر الخطوة السابقة، وأقصر بكثير من دورة
     * الثلاثين ثانية فتبقى الفائدة حقيقية.
     */
    const val MIN_ADAPTIVE_INTERVAL_MS = 10_000L
}
