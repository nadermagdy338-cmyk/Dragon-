package nd.max.core.maxai

/**
 * نماذج حالة MAX AI — كل قيمة قابلة للعرض مشتقة من قياس أو قرار
 * حقيقي؛ لا حقل واحد هنا يمكن توليده من العدم.
 */

/** من يملك التحكم الآن — أولوية تنفيذية معلنة، لا عرض فقط. */
enum class MaxAiController {
    /** Max AI مطفأ: المستخدم يتحكم يدويًا (ملف + تعديلات مباشرة). */
    MANUAL,

    /** Max AI مفعّل ويدير الأداء دوريًا (قرار → تنفيذ → قياس نتيجة). */
    MAX_AI,

    /** تطبيق بملف خاص في المقدمة: Max AI في وضع المراقبة فقط. */
    APP_PROFILE,

    /** محرك الأمان متدخل (سقف حراري) — يتقدم على الكل. */
    SAFETY_OVERRIDE,
}

/** حالة محرك الأمان — درجات فعلية من قياس الحرارة والتنبؤ الأمامي. */
enum class SafetyLevel { NORMAL, ENGAGED, CRITICAL }

data class SafetyStatus(
    val level: SafetyLevel = SafetyLevel.NORMAL,
    val thermalC: Float = 0f,
    val engaged: Boolean = false,
    /** عدد تدخلات الأمان منذ التثبيت — عداد تراكمي حقيقي. */
    val interventions: Long = 0L,
    /** آخر سبب تدخل بلغة واضحة (قياسات، لا عموميات). */
    val lastReason: String = "",
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

/** تعديل يدوي طلب أثناء إدارة Max AI — يُطبق عند إيقافه (لا يضيع). */
data class PendingManualChange(
    val key: String,
    val value: String,
    val label: String,
    val timestampMs: Long,
)

/** الحالة الكاملة التي تستهلكها الواجهة — أعداد حقيقية فقط. */
data class MaxAiState(
    val aiEnabled: Boolean = false,
    val controller: MaxAiController = MaxAiController.MANUAL,
    /** الاستراتيجية الجارية: آخر قرار معتبر أو "يدوي". */
    val strategyLabel: String = "يدوي",
    val lastDecision: DecisionRecord? = null,
    val safety: SafetyStatus = SafetyStatus(),
    /** عدادات النشاط الحقيقية منذ التثبيت. */
    val totalDecisions: Long = 0L,
    val successfulDecisions: Long = 0L,
    val adjustedDecisions: Long = 0L,
    val blockedForSafety: Long = 0L,
    /** خطوات التعلم الفعلية للوكيل الأصلي (0 بلا مكتبة — بلا تزييف). */
    val rlSteps: Long = 0L,
    /** قياسات المراقبة الحية الأخيرة. */
    val cpuLoadPercent: Int = 0,
    val thermalC: Float = 0f,
    val batteryPercent: Int = 0,
    val screenOn: Boolean = false,
    /** التغييرات اليدوية المعلقة (تُطبق عند إيقاف AI). */
    val pendingChanges: List<PendingManualChange> = emptyList(),
    /** الملف العام الحالي ("1" أداء / "2" متوازن / "3" توفير). */
    val currentProfile: String? = null,
)
