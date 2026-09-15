package nd.max.core.maxai

import nd.max.core.hardware.ControlOwnership

/**
 * نماذج حالة MAX AI — كل قيمة قابلة للعرض مشتقة من قياس أو قرار
 * حقيقي؛ لا حقل واحد هنا يمكن توليده من العدم.
 */

/** Publication state of the winning intent for one physical control key. */
enum class OwnershipCommitState { PENDING, VERIFIED }

/** UI-facing projection of the shared ownership journal, one winner per knob. */
data class KnobOwnershipSnapshot(
    val key: String,
    val owner: ControlOwnership.Owner,
    val desired: String,
    val state: OwnershipCommitState,
    /** True when the winner (or a safety override) holds a manual lock from the user. */
    val locked: Boolean = false,
)

/**
 * UI-facing projection of one durable user lock.
 *
 * Locks are reported independently of [MaxAiState.ownership] because they outlive
 * the journal: after a reboot no intent exists for the key, yet the knob is still
 * closed to every automated owner. Deriving the lock view from journal winners
 * would hide exactly the state the user must be able to see and undo.
 */
data class LockedKnobSnapshot(
    val key: String,
    val desired: String,
    val lockedAtMs: Long,
)

/** حالة محرك الأمان — درجات فعلية من قياس الحرارة والتنبؤ الأمامي. */
enum class SafetyLevel { NORMAL, ENGAGED, CRITICAL }

/** نتيجة التحقق الحي من تطبيق سقف الأمان، لا مجرد نية التطبيق. */
enum class SafetyEnforcement { NOT_REQUIRED, APPLIED, PARTIAL, FAILED, UNAVAILABLE }

data class SafetyStatus(
    val level: SafetyLevel = SafetyLevel.NORMAL,
    val thermalC: Float = 0f,
    val engaged: Boolean = false,
    /** عدد نوبات تدخل الأمان منذ التثبيت — لا عدد العينات الساخنة. */
    val interventions: Long = 0L,
    /** آخر سبب تدخل بلغة واضحة (قياسات، لا عموميات). */
    val lastReason: String = "",
    /** نتيجة آخر محاولة موثقة لفرض السقف. */
    val enforcement: SafetyEnforcement = SafetyEnforcement.NOT_REQUIRED,
    val enforcementDetail: String = "",
)

/** سجل قرار واحد — يُعرض في "آخر إجراء" وفي عدادات النشاط. */
data class DecisionRecord(
    val label: String,
    val timestampMs: Long,
    val result: DecisionResult,
    /** سبب القرار من القياسات التي أطلقته. */
    val reason: String,
)

enum class DecisionResult { EXECUTED, VERIFIED, ADJUSTED, BLOCKED_FOR_SAFETY, SKIPPED, FAILED }

/** حالة طلب ملف يدوي واحد لتمكين الواجهة من منع النقر المتكرر. */
data class ProfileRequestState(
    val profileId: String? = null,
    val inFlight: Boolean = false,
    val result: DecisionResult? = null,
)

/**
 * نقطة واحدة في منحنى التوقع الحراري مقابل الواقع.
 *
 * لماذا توجد: المحرك يحسب تنبّؤًا حراريًا أماميًا كل دورة لحسابات
 * السلامة، ثم يرميه فورًا. ومقارنة ذلك التنبّؤ بما حدث فعلًا لاحقًا هي
 * أصدق دليل على أن النظام يفهم الجهاز — وتكلفته صفر لأن الرقمين
 * محسوبان أصلًا.
 *
 * @param actualC الحرارة المقيسة في هذه اللحطة، أو null للنقاط المستقبلية.
 * @param forecastC ما تنبّأ به المتنبئ لهذه اللحطة قبل [leadMs]، أو null.
 * @param leadMs كم مسبقًا قيل هذا التنبّؤ.
 * @param future true للنقاط التي لم يحن وقتها بعد.
 */
data class MaxAiForecastPoint(
    val timestampMs: Long,
    val actualC: Float? = null,
    val forecastC: Float? = null,
    val leadMs: Long = 0L,
    val future: Boolean = false,
)

/**
 * ما تعرفه طبقة الثقة الآن عن الاستكشاف: هل يُسمح، ولماذا، وعلى ماذا.
 *
 * هذا الحقل هو ما يمنع الاستكشاف من أن يكون صندوقًا أسود: المستخدم
 * يرى متى كان النطام سيجرّب ولماذا امتنع بالضبط.
 */
data class ExplorationState(
    /** null حين يُسمح بالتجربة؛ غير ذلك أحد ثوابت [TrustModel.Block]. */
    val blockReason: String? = null,
    /** المقبض المرشح للتجربة القادمة إن وجد. */
    val targetLabel: String? = null,
    /** تكلفة أسوأ حالة المقدّرة للمرشح (0..1). */
    val worstCaseCost: Float? = null,
    /** قيمة المعلومة المتوقعة من التجربة. */
    val informationGain: Float? = null,
    /** عدد التجارب المنفّذة في عمر العملية وسقفها. */
    val probesThisSession: Int = 0,
    val budget: Int = 0,
    /** زمن آخر تجربة، أو 0 إن لم تجر واحدة بعد. */
    val lastProbeAtMs: Long = 0L,
)

/** الحالة الكاملة التي تستهلكها الواجهة — أعداد حقيقية فقط. */
data class MaxAiState(
    val aiEnabled: Boolean = false,
    /** موجز مشتق من الملكية الفعلية/الأمان/حالة AI، وليس وضع تحكم عالميًا. */
    val strategyLabel: String = "يدوي",
    val lastDecision: DecisionRecord? = null,
    val safety: SafetyStatus = SafetyStatus(),
    /** عدادات النشاط الحقيقية منذ التثبيت. */
    val totalDecisions: Long = 0L,
    val successfulDecisions: Long = 0L,
    val adjustedDecisions: Long = 0L,
    val blockedForSafety: Long = 0L,
    /**
     * تقدّم التعلّم الحقيقي — نواة Kotlin لا وكيل أصلي.
     *
     * كان الحقل السابق `rlSteps` يعدّ خطوات وكيل Rust الذي لم يعد
     * يُستشار في القرار (قرار #17)، فكان يبقى صفرًا أبدًا بينما التعلّم
     * الفعلي يجري على مستوى المقابض هنا. عرض رقم لا يتحرك كان سيُوهم
     * المستخدم أن النظام لا يتعلّم.
     */
    val learnedKnobs: Int = 0,
    /** إجمالي الأحكام المقيسة المتراكمة لكل المقابض. */
    val learningSamples: Long = 0L,
    /** قياسات المراقبة الحية الأخيرة. */
    val cpuLoadPercent: Int = 0,
    val thermalC: Float = 0f,
    val batteryPercent: Int = 0,
    val screenOn: Boolean = false,
    /** الفائز الفعلي لكل مقبض من دفتر الملكية المشترك. */
    val ownership: List<KnobOwnershipSnapshot> = emptyList(),
    /**
     * الأقفال اليدوية الدائمة — تُعرض مستقلة عن الملكية لأنها تبقى بعد إعادة
     * التشغيل بينما تنتهي نوايا الدفتر مع عملياتها. إخفاؤها يجعل المستخدم
     * يرى مقبضًا "حرًا" وهو في الحقيقة مغلق أمام كل مالك آلي.
     */
    val lockedKnobs: List<LockedKnobSnapshot> = emptyList(),
    /** ملف الأساس الحالي ("1" أداء / "2" متوازن / "3" توفير). */
    val currentProfile: String? = null,
    /**
     * شريط التطور الزمني: عيّنات دورات المحرك الحقيقية منذ إقلاع التطبيق.
     *
     * في الذاكرة فقط وبقصد: هذه قياسات جلسة حالية لا أرشيف، وتخزينها على
     * القرص كان سيجعل الواجهة تعرض رسمًا يبدو "حيًا" وهو من جلسة سابقة.
     * فارغ = لم تكتمل دورة بعد، وتعرضه الواجهة كذلك بلا رسم وهمي.
     */
    val trend: List<MaxAiSample> = emptyList(),
    /** أوزان الهدف النشط الآن — ما يوازن به المخطِّط فعلًا هذه الدورة. */
    val objectiveWeights: Objective? = null,
    /** "user" تفضيل صريح / "learned" استنتاج سلوكي / "screen_off". */
    val objectiveSource: String = "learned",
    /** التطبيق في المقدمة كما قرأه المحرك من ملف الرفيق، أو "system". */
    val appContext: String = "system",
    /** درجة رضا الحالة تحت الهدف النشط (0..1) من آخر لقطة مقيسة. */
    val objectiveScore: Float? = null,
    /** عتبة الرضا التي يقارن بها المخطِّط — تُعرض كي يكون الرقم مفهومًا. */
    val satisfactionTarget: Float = 0f,
    /** الذاكرة المستخدمة من آخر لقطة. */
    val memoryPercent: Int = 0,
    /** زمن آخر لقطة مقيسة — أساس شارة الثقة (حي/قديم) في الواجهة. */
    val lastSampleAtMs: Long = 0L,
    /**
     * منحنى التوقع مقابل الواقع الحراري: نقاط ماضية مطابَقة ثم امتداد
     * مستقبلي من أحدث تنبّؤ. فارغ = لا متنبئ على هذا الجهاز أو لم تكتمل
     * دورة بعد، وتعرضه الواجهة كذلك بلا رسم وهمي.
     */
    val thermalForecast: List<MaxAiForecastPoint> = emptyList(),
    /** متوسط |تنبّؤ − مقيس| للنقاط المطابَقة، أو null قبل أول مطابقة. */
    val forecastErrorC: Float? = null,
    /** حالة المعرفة لكل مقبض متاح الآن — ما يعرفه النطام وما يجهله. */
    val trust: List<TrustModel.KnobTrust> = emptyList(),
    /** بوابة الاستكشاف كما قُيّمت في الدورة الأخيرة. */
    val exploration: ExplorationState = ExplorationState(),
)
