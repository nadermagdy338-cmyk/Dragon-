/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

/**
 * ميزانية الحرارة — كم صمد الجهاز بكامل أدائه قبل أن يُضطر الحاكم إلى خفضه.
 *
 * لماذا هذا الرقم تحديدًا: كل ما يعرضه Max AI اليوم يصف **حالة** (حرارة، حمل،
 * عدد تدخلات). والحالة لا تُجيب على السؤال الذي يخرج به اللاعب من جلسة لعب:
 * «هل يمكنني الاعتماد على هذا الجهاز ساعة كاملة، أم سيهبط بعد عشر دقائق؟».
 * والجواب ليس حرارة ولا ترددًا، بل **زمن**: من لحظة بدء الحمل الثقيل إلى أول
 * تدخل سلامة. منصة أندرويد نفسها تقترح هذا المقياس في أدبيات التخفيف الحراري
 * (تسجيل `t0` عند `THERMAL_STATUS_NONE` وقياس الزمن المنقضي إلى أول خفض)، وهو
 * المخرج الأول من `docs/ai/EXTERNAL-RESEARCH.md` (XR-05).
 *
 * ولماذا وحدة نقيّة منفصلة: القياس نفسه هو الميزة، والخطأ فيه لا يظهر كعطل بل
 * كرقم **يبدو معقولًا وهو كاذب** — وهذا أسوأ ما في مشروع قياس. لذلك كل قواعد
 * القبول هنا صريحة ومُختبَرة:
 *
 *  ① لا ميزانية بلا تدخل مقيس: جلسة لم تُخنق تعطي «جلسة نظيفة» لا رقمًا مُقدرًا.
 *  ② لا يُعاد عدّ التدخل الثاني في الجلسة نفسها: الميزانية عمر الجلسة حتى أول
 *     خنق، وليس آخر خنق — وإعادتها كانت ستُطيل الرقم بعد كل تدخل.
 *  ③ إنهاء الجلسة بإطفاء الشاشة لا يُحتسب صمودًا حين يكون الحمل قائمًا: لا
 *     نعرف هل صمد العتاد أم توقف الطلب، والجهل يُقال لا يُملأ.
 *  ④ الجلسات الأقصر من [MIN_SESSION_MS] لا تُقيَّد: وميض لعبة ثانيتين ليس ميزانية.
 *
 * الوحدة نقية تمامًا: لا Android ولا وقت داخلي ولا كتابة — الزمن يُمرَّر إليها،
 * فتُختبر جلسةً جلسة بقيم صريحة.
 */
object MaxAiThermalBudget {

    /**
     * الحالة التي تعيش بين الدورات (تُقيَّد كل ثانية من حلقة الأمان).
     *
     * @param sessionStartedAtMs بداية الجلسة الحالية، أو null إن لا جلسة مفتوحة.
     *        ولماذا null لا صفرًا: الصفر لحظة زمنية صحيحة (بدء التشغيل)، فاستعماله
     *        كعلامة «لا جلسة» كان يجعل قراءة ساعة عند الصفر تُلغي الجلسة صامتةً —
     *        وهي علاقة خفيّة بين قيمة العلامة وقيمة الزمن كشفها اختبار على أساس
     *        زمني صفري. الغياب يُكتب غيابًا لا رقمًا.
     * @param firstInterventionAtMs لحظة أول خنق في الجلسة الحالية، أو null.
     * @param lastBudgetMs آخر ميزانية مقيسة (زمن حتى أول خنق)، أو null.
     * @param lastCleanSessionMs آخر جلسة كاملة انتهت **بلا خنق**، أو null.
     *        وهو أفضل دليل ممكن: الجهاز صمد فعلًا.
     */
    data class State(
        val sessionStartedAtMs: Long? = null,
        val firstInterventionAtMs: Long? = null,
        val lastBudgetMs: Long? = null,
        val lastBudgetEndedAtMs: Long? = null,
        val lastCleanSessionMs: Long? = null,
        val lastCleanSessionEndedAtMs: Long? = null,
        val sessionsMeasured: Int = 0,
    ) {
        val sessionOpen: Boolean get() = sessionStartedAtMs != null
    }

    /** الصورة المنشورة للواجهة — كل حقل مقيس أو صريح الغياب. */
    data class Status(
        val sessionOpen: Boolean = false,
        val sessionElapsedMs: Long = 0L,
        val lastBudgetMs: Long? = null,
        val lastBudgetEndedAtMs: Long? = null,
        val lastCleanSessionMs: Long? = null,
        val lastCleanSessionEndedAtMs: Long? = null,
        val sessionsMeasured: Int = 0,
    )

    /**
     * يقيس دورة واحدة.
     *
     * @param demanding حمل ثقيل مقيس (لعبة في المقدمة على هذا الجهاز).
     * @param screenOn الشاشة قيد الاستخدام — إطفاؤها ينهي الجلسة لا يُطيلها.
     * @param level مستوى حاكم الأمان الحالي: أي شيء غير [SafetyLevel.NORMAL]
     *        يعني أن العتاد مُقيَّد الآن فعلًا.
     */
    fun observe(
        previous: State,
        nowMs: Long,
        demanding: Boolean,
        screenOn: Boolean,
        level: SafetyLevel,
    ): State {
        val active = demanding && screenOn
        var next = previous

        // ① فتح جلسة عند بدء حمل ثقيل والشاشة قائمة.
        if (active && !previous.sessionOpen) {
            next = next.copy(sessionStartedAtMs = nowMs, firstInterventionAtMs = null)
        }

        // ② إغلاق الجلسة: انتهى الحمل، أو أُطفئت الشاشة.
        val startedAt = previous.sessionStartedAtMs
        if (!active && startedAt != null) {
            val endedByHeavyLoadStopping = !demanding && screenOn
            val duration = nowMs - startedAt
            val cleanAndMeasurable = previous.firstInterventionAtMs == null &&
                endedByHeavyLoadStopping &&
                duration >= MIN_SESSION_MS
            if (cleanAndMeasurable) {
                next = next.copy(
                    lastCleanSessionMs = duration,
                    lastCleanSessionEndedAtMs = nowMs,
                )
            }
            next = next.copy(sessionStartedAtMs = null, firstInterventionAtMs = null)
        }

        // ③ أول خنق داخل جلسة مفتوحة ⇒ الميزانية تُقفل لحظتها. ولا يُعاد فتحها
        //    بتدخل ثانٍ في الجلسة نفسها.
        val openSince = next.sessionStartedAtMs
        if (openSince != null && next.firstInterventionAtMs == null &&
            level != SafetyLevel.NORMAL
        ) {
            next = next.copy(
                firstInterventionAtMs = nowMs,
                lastBudgetMs = nowMs - openSince,
                lastBudgetEndedAtMs = nowMs,
                sessionsMeasured = next.sessionsMeasured + 1,
            )
        }

        return next
    }

    /** تُشتق في كل نشر — لا تُخزَّن، فلا يمكن أن تتناقض مع الحالة. */
    fun statusOf(state: State, nowMs: Long): Status = Status(
        sessionOpen = state.sessionOpen,
        sessionElapsedMs = state.sessionStartedAtMs
            ?.let { (nowMs - it).coerceAtLeast(0L) }
            ?: 0L,
        lastBudgetMs = state.lastBudgetMs,
        lastBudgetEndedAtMs = state.lastBudgetEndedAtMs,
        lastCleanSessionMs = state.lastCleanSessionMs,
        lastCleanSessionEndedAtMs = state.lastCleanSessionEndedAtMs,
        sessionsMeasured = state.sessionsMeasured,
    )

    /** أقل مدة جلسة تُقيَّد — وميض ثانيتين ليس ميزانية حرارية. */
    const val MIN_SESSION_MS = 30_000L

    /** قيمة `appIntent` التي تعني «حمل ثقيل مقيس» (1 لعبة، 0.5 عادي). */
    const val DEMANDING_INTENT = 0.75f
}
