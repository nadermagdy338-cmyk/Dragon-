/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

/**
 * أدلّة أهليّة المسار — **مقيسة من الجهاز الآن**، لا ادّعاءات حرفيّة.
 *
 * لماذا هذا النوع
 * ---------------
 * مخطِّط أطلس ([nd.max.core.atlas.AtlasRoutePlanner]) بوابة قوية: يرفض المسار إن لم تكن وحدته
 * مُثبتة، أو خط أساسه غير مقروء، أو استرجاعه غير مُثبت، أو لم تتمّ مراجعته. لكن بوّابته كانت
 * تُغذَّى في مسار الحارس الحراري بأربع قيم حرفيّة (`readable = true` · `unitProven = true` ·
 * `baselineReadable = true` · `rollbackProven = true`) — أي أن البوابة كانت تعمل، والادّعاء الذي
 * يُدخلها لم يكن مقيسًا. فهذا النوع هو صيغة الأدلّة **المقيسة**، ومكانه واحد يستعمله كل مَن يبني
 * مسارًا على المُحكِّم.
 *
 * ما هو مقيس فعلًا، وكيف
 * ---------------------
 * - `readable`: هل أجابت عقدة هذا المقبض بقراءة حيّة الآن؟ (قراءة فاشلة ⇒ غير مقروء).
 * - `baselineReadable`: خط الأساس في هذه المعاملة **هو تلك القراءة نفسها** (المُحكِّم يأخذ خط
 *   الأساس من قراءة حيّة عند التنفيذ، ويُخفق بـ`baseline-unreadable` إن لم تُقرأ) — فالقياس واحد.
 * - `unitProven`: هل نشرت النواة سلّم ترددات لهذا المقبض؟ وما نكتبه هو **عضو في ذلك السلّم**
 *   (القصّ إليه في كل كاتب)، فبغير سلّم لا نعرف وحدة ما نكتبه ولا قيمة صالحة ⇒ غير مُثبت.
 * - `privilegeAvailable`: هل توجد معاملة حيّة على هذه العقدة (كاتب + قارئ)؟
 * - `rollbackProven`: `baselineReadable && privilegeAvailable` — الاسترجاع يكتب **بالكاتب نفسه**
 *   ويُتحقَّق بقراءة مرتجعة في نفس المعاملة، فوجود الاثنين هو كل ما يمكن إثباته قبل الكتابة.
 *
 * وحدّ لا يُخفى: **لا قراءة تُثبت صلاحية الكتابة**. صلاحية الكتابة يحكم عليها المُحكِّم عند
 * التنفيذ (`apply` يفشل بـSELinux/صلاحية ⇒ المعاملة تُعلن فشلها بصدق ولا تُدّعى نجاحًا). لذلك
 * `privilegeAvailable` هنا تعني حرفيًّا «معاملة قابلة للمحاولة على هذه العقدة»، لا أكثر؛ ومن
 * قرأها «الكتابة ستنجح» فقد أساء قراءتها، ولذلك سُمّيت في الرمز `attemptAvailable` نصًّا.
 */
data class RouteEvidenceFacts(
    val readable: Boolean,
    val attemptAvailable: Boolean,
    val unitProven: Boolean,
    val baselineReadable: Boolean,
    val rollbackProven: Boolean,
) {
    /** هل قِيس شيء أصلًا؟ (كل `false` تعني «لم نُقس»، لا «قِسناه فوجدناه غير كافٍ»). */
    val measured: Boolean
        get() = readable || attemptAvailable || unitProven || baselineReadable || rollbackProven

    /**
     * رموز الأدلّة **الموجودة** مفصولة بـ`+`، و`none` حين لا شيء — صيغة ثابتة تُكتب في سطر سجل
     * واحد، فيُعرف من اللقطة أيّ دليلٍ نقص بلا قراءة شيفرة.
     */
    fun codes(): String = buildList {
        if (readable) add("read")
        if (attemptAvailable) add("attempt")
        if (unitProven) add("unit")
        if (baselineReadable) add("baseline")
        if (rollbackProven) add("rollback")
    }.joinToString("+").ifEmpty { "none" }

    companion object {

        /**
         * لا قياس ⇒ لا أهليّة. وهي حالة **فشل مغلق**: مسارٌ لا نعرف عنه شيئًا لا يُنفَّذ، والمخطِّط
         * يُسمّي السبب برمزه القياسي (لا صمت).
         */
        val UNMEASURED: RouteEvidenceFacts =
            RouteEvidenceFacts(
                readable = false,
                attemptAvailable = false,
                unitProven = false,
                baselineReadable = false,
                rollbackProven = false,
            )

        /**
         * أدلّة مقبض cpufreq من قياسه الحيّ.
         *
         * @param liveReadable هل أجابت عقدة السياسة بقراءة حيّة الآن؟ (مقيس)
         * @param transactionHeld هل يحمل الطالب معاملة كاملة على هذا المقبض (كاتبٌ وقارئ)؟ وهي
         *   **حقيقة بنيوية**: في [PerAppControlRegistry] كل إدخال يحمل كاتبًا وقارئًا ببنائه، فليست
         *   ادّعاءً عن الجهاز — وما يُقاس عن الجهاز هو `liveReadable` و`ladder`.
         * @param ladder سلّم الترددات **المُعلَن** لهذه السياسة (فارغ ⇒ الوحدة غير مُثبتة).
         */
        fun cpu(
            liveReadable: Boolean,
            transactionHeld: Boolean,
            ladder: List<Long>,
        ): RouteEvidenceFacts = of(
            liveReadable = liveReadable,
            transactionHeld = transactionHeld,
            unitProven = ladder.any { it > 0L },
        )

        /**
         * أدلّة مقبض تردد GPU من قياسه الحيّ.
         *
         * @param unitTrusted هل وحدتُه موثوقة من دليل السائق نفسه؟ (وحدة مُستنتَجة بالحجم ليست
         *   ثقة: `GpuHardwareBackend` يميّزها ولا يُخمّن).
         */
        fun gpu(
            liveReadable: Boolean,
            transactionHeld: Boolean,
            unitTrusted: Boolean,
            ladder: List<Long>,
        ): RouteEvidenceFacts = of(
            liveReadable = liveReadable,
            transactionHeld = transactionHeld,
            unitProven = unitTrusted && ladder.any { it > 0L },
        )

        /** الصيغة الوحيدة التي تُبنى منها كل الأدلّة: قراءة حيّة + معاملة + وحدة مُثبتة. */
        fun of(
            liveReadable: Boolean,
            transactionHeld: Boolean,
            unitProven: Boolean,
        ): RouteEvidenceFacts {
            val usable = liveReadable && transactionHeld
            return RouteEvidenceFacts(
                readable = liveReadable,
                attemptAvailable = usable,
                unitProven = unitProven,
                baselineReadable = liveReadable,
                rollbackProven = usable,
            )
        }
    }
}
