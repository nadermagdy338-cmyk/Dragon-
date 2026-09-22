package nd.max.ui.mainscreens

/**
 * سياسة عرض **البطاقة الواحدة** في الشاشة الرئيسية: أيّ الأسطر تُعرض، وبأيّ شكل، وبلا تكرار.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * البطاقة كانت تُرشّح أسطرها **داخل دالة Compose**، فصارت السياسة غير قابلة للقياس: اختبار الوحدة
 * كان يقيس نسخة ثانية من السياسة (نسخة لا تُنفَّذ)، والفلتر الحقيقي كان يمرّ بلا اختبار. والنتيجة
 * العملية ظهرت مرّتين:
 *
 * 1. **التكرار داخل البطاقة نفسها**: سطر المشهد الأول كان يُعرض كاملًا ثم تُضاف أسطر المشاهد
 *    التالية بترشيح `distinctBy` **على القائمة الثانية وحدها**، فمقبض واحد (`cpu_limits:policy0`
 *    مثلًا) يمكن أن يُقرأ مرّتين في بطاقة واحدة — وهو عين ما رُفض.
 * 2. **غياب الاختيارات**: الفلتر كان يشترط وجود «قبل» (`from != null`)، فكل سطر اختيار بلا قياس
 *    يُطرح: `Kill Background Apps` · البروفايل · الحاكمان · معدّل التحديث · المُصيّر. وهذا نقضٌ
 *    لطلب صاحب المشروع: «إذا اختار gaming يكتب أن كذا يعمل، وأن إيقاف تطبيقات الخلفية صار on».
 *
 * فالقرار هنا **خالص** (بلا Compose وبلا Android) ويُقاس في JVM، والواجهة ترسم ما يعود منه فقط.
 *     * والتخصيص نفسه **لا يوسّع الصدق**: لا خيار هنا يجعل سطرًا غير متحقّق يُعرض، فكل خيار يُضيّق أو
     * يُوسّع ما هو قائم ومتحقّق فقط.
     *
     * والقواعد — وهي حدود صدق لا تنسيق:
 *
 * 1. **النجاح فقط**: ما لم يُتحقّق يُسكَت هنا ويُقرأ في شاشة السجل والإعدادات. البطاقة ليست مكان
 *    تقرير العطل.
 * 2. **لا سطر فارغ المعنى**: سطر بلا قيمة (`max_ai_active` مثلًا) يُعرض لأنه **يقول حالة**، وما
 *    لا قيمة له ولا حالة يُطرح.
 * 3. **لا مقبض مرّتين**: الترشيح على البطاقة كلها، بمشاهدها كلها.
 * 4. **الحدود تُطبَّق هنا**: لا تُبنى قائمة ثم تُقصّ في الواجهة.
 *
 * والتخصيص (المرحلة ٥ من خطة الرئيسية)
 * -----------------------------------
 * التخصيص **لا يوسّع الصدق**: لا خيال خيار يجعل سطرًا غير متحقّق يُعرض. كل خيار يُضيّق أو يُوسّع
 * ما هو **قائم ومتحقّق** فقط، و«التلقائي» هو الافتراضي ويختار الشكل من البيانات نفسها.
 *
 * ولا خيار بلا أثر: كل قيمة هنا تغيّر ما يُرسم فعلًا (عدد المشاهد/الأسطر، أو الشارات، أو سطر
 * السبب). وما لا مصدر له اليوم (مثل «نجاح ميزات النظامية» كمشهد مستقل) **لم يُضَف** — خيار لا
 * يفعل شيئًا أسوأ من غيابه.
 */
object UnifiedActivityModel {

    /** بادئة مفاتيح الذكاء: أسطرها قد تقول «يعمل» بلا قيمة، وهي معنى لا فراغ. */
    const val MAX_AI_PREFIX = "max_ai_"

    /**
     * نافذة «جلسة حديثة»: آخر جلسة تطبيق أقدم من ربع ساعة تُعدّ سجلًا لا نشاطًا.
     *
     * والقيمة مقصودة ومُعلنة لأنها القرار الوحيد في هذا الملف الذي يعتمد على الزمن؛ و`atMs` يأتي
     * من ملف الحالة نفسه لا من ساعة الواجهة.
     */
    const val SESSION_RECENT_MS = 15L * 60L * 1000L

    /** حدّ «كثرة الميزات» الذي يقلب الشكل التلقائي إلى شارات. */
    private const val ADAPTIVE_CHIP_THRESHOLD = 4

    /** نمط عرض البطاقة — وكل نمط يغيّر ما يُرسم فعلًا، لا عنوانه. */
    enum class CardStyle(
        val token: String,
        val maxScenes: Int,
        val linesPerScene: Int,
        val maxLines: Int,
    ) {
        /**
         * تلقائي: يختار من البيانات نفسها بين [CINEMATIC] و[SUMMARY] و[CHIPS] ([adaptiveStyle]).
         *
         * و[TIMELINE] **ليس** من اختياره: التفصيل لكل حدث قرار ذوقي على القصة، ولا شيء في البيانات
         * يقول إنه المطلوب — فيُترك لمن يطلبه.
         *
         * وهو الافتراضي، فلا يُعرض على أحد إعداد لم يطلبه ويبقى التخصيص لمن يريده.
         */
        AUTO("auto", 2, 3, 4),

        /** مشهد واحد بملء العرض: أقلّ عدد، أكثر حضورًا — الحالة التي وُجدت البطاقة من أجلها. */
        CINEMATIC("cinematic", 1, 2, 3),

        /** قائمة عملية مكثّفة: مشهدان وأسطر أكثر، لمن يريد أن يقرأ كل ما وقع لا أن يشاهد. */
        SUMMARY("summary", 2, 4, 6),

        /** شارات مختصرة: حين تكثر الميزات، فيصير السرد الطويل غير مقروء. */
        CHIPS("chips", 3, 4, 8),

        /**
         * خط زمني: **كل حدث برأسه ووقته**، لا قائمة أسطر مسطّحة.
         *
         * والفارق حقيقي ومقيس: في غيره تُدمج أسطر كل المشاهد في قائمة واحدة، فيضيع وقت المشهد
         * الثاني (والوقت يُعرض اليوم على المشهد الأول وحده). وهنا يُقرأ الحدث بزمنه:
         * `بروفايل التطبيق — قيس قبل ٣ د` ثم أسطره، ثم `تحكمك اليدوي` ثم أسطره.
         *
         * ورصيده أضيق (٥ أسطر لا ٦) لأن رأس كل حدث يشغل موضعه أيضًا، والغرض أن يُقرأ لا أن يُعدّ.
         * ولا وقت يُخترع لمشهد لا وقت له: رأسه يُكتب بلا سطر زمن، والترتيب يبقى ترتيب الأولوية
         * الذي كتبه `StoryboardModel` (فلا يقفز الحدث بين تحديثين).
         */
        TIMELINE("timeline", 2, 3, 5),
    }

    /** كمية التفاصيل: المبسّط يُخفي سطر السبب، والتقني المختصر يُظهره. */
    enum class CardVerbosity(val token: String) { SIMPLE("simple"), TECHNICAL("technical") }

    /**
     * الحركة: كاملة · مخفّفة · موقوفة.
     *
     * والمدّة هنا لا في الواجهة، لأن «مخفّفة» و«موقوفة» قراران لهما رقم واحد يجب أن يكون موضعًا
     * واحدًا؛ والمدّة صفر تعني **عدم استخدام حركة** لا حركةً بطول صفر.
     */
    enum class CardMotion(val token: String, val enterMs: Int) {
        FULL("full", 220),
        REDUCED("reduced", 110),
        OFF("off", 0),
    }

    /**
     * سياسة آخر جلسة تطبيق: البطاقة تقرأ سجل `PerAppHardwareStatus` الذي **يبقى بعد الخروج**،
     * فمن فتح الرئيسية بعد ساعات كان يرى جلسة قديمة كأنها الآن.
     */
    enum class SessionPolicy(val token: String) {
        ALWAYS("always"),
        RECENT("recent"),
        HIDE("hide"),
    }

    /**
     * خيارات المستخدم. الافتراضي هو **التلقائي**: الأنماط الافتراضية هي التي رُتّبت لتُقرأ بلا
     * إعداد، والتخصيص لمن يريده (قرار صاحب المشروع في المواصفة).
     */
    data class CardOptions(
        val auto: Boolean = true,
        val style: CardStyle = CardStyle.AUTO,
        val verbosity: CardVerbosity = CardVerbosity.SIMPLE,
        val motion: CardMotion = CardMotion.FULL,
        val showPerApp: Boolean = true,
        val showMaxAi: Boolean = true,
        val showManual: Boolean = true,
        /** «الذكاء يعمل» بلا تغيير مثبت: حالة تُعرض أو تُسكَت. */
        val showMonitoring: Boolean = true,
        val session: SessionPolicy = SessionPolicy.ALWAYS,
    ) {
        /**
         * هل هذه القيم هي الافتراضية بلا لمس؟
         *
         * يُستعمل لعرض الحالة في سطر الإعدادات فقط، فلا يُقرأ «افتراضي» على قيم غيّرها صاحبها بيده.
         * و`style` **خارج المقارنة** لا سهوًا: الشرط يشترط `auto` أصلًا، و«التلقائي» مشتغلًا يعني
         * أن قيمة النمط **لا تُنفَّذ** (القرار يعود للبيانات) — ومن أطفأ التلقائي ثم أعاده صار
         * افتراضيًّا فعلًا وإن بقيت قيمته القديمة محفوظة؛ الاحتفاظ بها لا يعني أنها تُنفَّذ.
         *
         * وعكسُ ذلك — أن تُحسب قيمة النمط المحفوظة خروجًا عن الافتراضي — كان يُخبر صاحب البطاقة
         * أنه «مخصّص» وهو لم يُخصّص شيئًا يُنفَّذ، لأن `ActivityCardSettings` تُسمّي النمط حينها
         * على البطاقة والحال أن التلقائي هو الذي يرسم.
         */
        val isDefault: Boolean
            get() = auto && verbosity == CardVerbosity.SIMPLE &&
                motion == CardMotion.FULL && showPerApp && showMaxAi && showManual &&
                showMonitoring && session == SessionPolicy.ALWAYS

        companion object {
            val DEFAULT = CardOptions()
        }
    }

    /**
     * نتيجة القرار: النمط الفعّال (بعد التلقائي) والمشاهد المرشَّحة، وهل يُرسم سطر السبب.
     *
     * والنمط الفعّال يعود للواجهة لأن **الرسم يختلف به** (شارات · وقت · مشهد واحد)، فلا يُترك
     * للواجهة أن تُخمّنه ثانيةً — تخمين ثانٍ هو الطريق إلى سياستين تفترقان.
     */
    data class CardModel(
        val style: CardStyle,
        val scenes: List<StoryboardScene>,
        val showReason: Boolean,
    ) {
        val isEmpty: Boolean get() = scenes.isEmpty()
    }

    /**
     * البطاقة كما ستُرسم.
     *
     * @param nowMs ساعة القراءة — تُستعمل لسياسة «جلسة حديثة» وحدها.
     */
    fun build(
        scenes: List<StoryboardScene>,
        options: CardOptions = CardOptions.DEFAULT,
        nowMs: Long = 0L,
    ): CardModel {
        val content = scenes
            .filter { scene -> options.allows(scene.kind) }
            .filter { scene -> options.keeps(scene, nowMs) }

        // «التلقائي» يختار **الشكل** من البيانات؛ أمّا خيارات المحتوى فقواعد المستخدم تسري في
        // الحالتين — وإلا صار تشغيل التلقائي يُلغي ما ضبطه بيده بلا أن يقول له أحد.
        //
        // والحالتان تُنفّذان الاختيار من البيانات: «تلقائي» مفعّل، أو النمط المحفوظ هو `AUTO`
        // نفسه (وهو ما يقع لمن أطفأ التلقائي ولم يختر نمطًا بعد). والبديل — أن يُقرأ `AUTO`
        // كأرقامه — كان يجعل البطاقة تتبع حدًّا لا يعني أحدًا بلا أن يظهر ذلك في أي مكان.
        val style = if (options.auto || options.style == CardStyle.AUTO) {
            adaptiveStyle(content)
        } else {
            options.style
        }

        val shown = linkedSetOf<String>()
        var remaining = style.maxLines
        val result = mutableListOf<StoryboardScene>()
        for (scene in content) {
            if (remaining <= 0 || result.size >= style.maxScenes) break
            val lines = scene.lines
                .asSequence()
                .filter { line -> isShowable(line, options.showMonitoring) }
                .filter { shown.add(it.knob) }
                .take(minOf(style.linesPerScene, remaining))
                .toList()
            if (lines.isEmpty()) continue
            remaining -= lines.size
            result += scene.copy(lines = lines)
        }
        return CardModel(
            style = style,
            scenes = result,
            showReason = options.verbosity == CardVerbosity.TECHNICAL,
        )
    }

    /**
     * النمط التلقائي — قاعدة واحدة معلنة، بلا تعلّم ولا تخمين:
     *
     * - ميزات كثيرة (أكثر من [ADAPTIVE_CHIP_THRESHOLD] سطرًا) ⇒ **شارات**، لأن السرد يطول.
     * - أكثر من مشهد ⇒ **قائمة عملية**، لأن القصة تحتاج ترتيبًا لا إطارًا سينمائيًّا.
     * - وإلا ⇒ **مشهد سينمائي**، وهي الحالة التي وُجدت البطاقة من أجلها.
     */
    fun adaptiveStyle(scenes: List<StoryboardScene>): CardStyle {
        val total = scenes.sumOf { it.lines.size }
        return when {
            total > ADAPTIVE_CHIP_THRESHOLD -> CardStyle.CHIPS
            scenes.size > 1 -> CardStyle.SUMMARY
            else -> CardStyle.CINEMATIC
        }
    }

    /**
     * هل يعرض هذا السطر؟
     *
     * `DONE` فقط (لا فشل، ولا مقفولًا بيد): الفشل مكانه السجل. والقيمة التالية مطلوبة — إلا
     * أسطر الذكاء التي تقول «يعمل» بلا قيمة، وتلك **حالة** يعرضها المستخدم أو يُسكتها بخيار صريح.
     */
    fun isShowable(line: StoryLine, showMonitoring: Boolean = true): Boolean =
        line.tone == LineTone.DONE && (
            line.to != null ||
                (showMonitoring && line.knob.startsWith(MAX_AI_PREFIX))
            )

    /** هل يسمح المستخدم بمشهد هذا النوع؟ */
    private fun CardOptions.allows(kind: SceneKind): Boolean = when (kind) {
        SceneKind.PER_APP -> showPerApp
        SceneKind.MAX_AI -> showMaxAi
        SceneKind.MANUAL -> showManual
    }

    /**
     * وهل تُحتفظ به حسب سياسة الجلسة؟ وغياب الوقت (`atMs = null`) يعني «ليس جلسة محفوظة» —
     * مشهد الذكاء وتحكّمك اليدوي مصدرهما الحالة القائمة، فلا تُسقَط بسياسة تخصّ الجلسات.
     */
    private fun CardOptions.keeps(scene: StoryboardScene, nowMs: Long): Boolean {
        if (scene.kind != SceneKind.PER_APP) return true
        return when (session) {
            SessionPolicy.ALWAYS -> true
            SessionPolicy.HIDE -> false
            SessionPolicy.RECENT -> {
                val at = scene.atMs ?: return true
                nowMs <= 0L || nowMs - at <= SESSION_RECENT_MS
            }
        }
    }

    /** هل هناك ما يُعرض أصلًا؟ (البطاقة تختفي بلا نشاط مؤكَّد، وهذا مقصود.) */
    fun shouldShow(
        scenes: List<StoryboardScene>,
        options: CardOptions = CardOptions.DEFAULT,
        nowMs: Long = 0L,
    ): Boolean = !build(scenes, options, nowMs).isEmpty
}
