package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasCpuPolicyFact
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteEvidence

/**
 * جسر أطلس إلى الكتابة: **من دليلٍ مقيس إلى مسارٍ قابل للتنفيذ**.
 *
 * لماذا هذا الملف
 * ---------------
 * لأطلس نصفان لا يلتقيان:
 *
 * - **الاكتشاف** ([nd.max.core.atlas.AtlasBackendProvider]) يقيس فعلًا: سياسات cpufreq
 *   المُعلنة، وسلّم الترددات، والتردد الحالي، وجذر وحدة القياس، ومصداقية كل ذلك.
 * - **التحكم** ([AtlasAdaptiveExecutor] ومَن يبني `AtlasRouteBinding`) يكتب، لكن أدلّة مساراته
 *   **حرفيّة مكتوبة باليد**: `ThermalCeilingRoutes.candidates` تجعل `unitProven = true`
 *   و`baselineReadable = true` و`rollbackProven = true` و`privileged = true` و`reviewed = true`
 *   بلا قياس، وكذلك يفعل `MinimalPlanner.execute`.
 *
 * وبوابة الأمان في [nd.max.core.atlas.AtlasRoutePlanner] تُرفض بـ`UNIT_AMBIGUOUS` و
 * `BASELINE_UNREADABLE` و`ROLLBACK_UNPROVEN` و`ROUTE_NOT_REVIEWED`. أي أنها **بوابة قوية**،
 * لكنها كانت تُغذّى بادّعاءات كاتب المسار لا بقياسات الجهاز. فهذا الملف هو ما يجعل أدلّة
 * المسار **مشتقّة من قياس**، ويبقى القرار للمخطِّط كما هو مقصود.
 *
 * القواعد التي يقوم عليها
 * ----------------------
 * 1. **لا عقدة مُخترعة.** المسار يُبنى من سياسة اكتشفها أطلس فعلًا، وباسمها القياسي نفسه
 *    (`HardwareControlKey.cpuLimits`) وبمعرّف المسار القياسي نفسه ([HardwareRepairExecutor.labelFor])
 *    الذي يستعمله كل كاتب آخر للمقبض ذاته — فلا مالكَين ولا صيغتين لعقدة واحدة.
 * 2. **ما لم يُقس لا يُدَّعى.** `readable` و`baselineReadable` و`unitProven` و`rollbackProven`
 *    كلها تُشتق من قراءة الآن ومن إعلان النواة، لا تُكتب `true`.
 * 3. **الفشل مغلق.** `reviewed` تأتي من المستدعي (كتالوج أطلس المُراجَع)، وقيمتها الافتراضية
 *    `false` ⇒ مخطِّط أطلس يرفض بـ`ROUTE_NOT_REVIEWED` ولا تُكتب عقدة لم يراجعها أحد.
 * 4. **الكاتب ليس ثانيًا.** الكتابة تمرّ من `AtlasCeilingAccess`، ومُنفِّذها الإنتاجي يفوّض إلى
 *    الكاتب المُتحقَّق القائم (`CpuHardwareBackend.setPolicyLimits`) والحكم القائم
 *    (`HardwareVerification.rangeContained`) — نفس ما يفعله `PerAppFrequencyController`، فلا
 *    منطق كتابة ثانٍ ينجرف عن الأول.
 * 5. **لا يُرفع فوق المُعلن.** السقف المطلوب يُقصّ إلى سلّم الترددات المُعلن؛ وطلبٌ أعلى من
 *    أقصى ما أثبتته النواة لا يصير قيمة مُخترعة.
 *
 * والحدّ المعلن: هذا الملف **يمنح أطلس القدرة على الكتابة** بمعاملة كاملة (خط أساس · قراءة
 * مرتجعة · نافذة تأكيد · استرجاع عند الانحراف)؛ ولا يوصله بواجهة ولا يختار متى يُنادى — ذلك
 * قرار مُستدعٍ يُوثَّق في مكانه.
 */
interface AtlasCeilingAccess {

    /** هل الكاتب المُتحقَّق متاح على هذا الجهاز الآن؟ (إشارة تُقاس، لا تُفترض) */
    val privileged: Boolean

    /** الحدود الحيَّة للسياسة بصيغة `min:max` (حقل فارغ حين لا تُجيب العقدة). */
    fun readLimits(policyPath: String): String?

    /** كتابة مدى `min:max` عبر الكاتب المُتحقَّق القائم. `false` = لم تُثبَت الكتابة. */
    fun writeLimits(policyPath: String, range: String): Boolean
}

/** نتيجة محاولة كتابة مبنيّة على الاكتشاف: ما بُني، وما لم يُبنَ وسببُه. */
data class AtlasDiscoveredResult(
    val plan: AtlasDiscoveredPlan,
    val adaptive: AtlasAdaptiveResult?,
) {
    val verified: Boolean get() = adaptive?.successful == true
    val selectedRouteId: String? get() = adaptive?.selectedRouteId
}

/** مسارات بُنيت فعلًا، ومفاتيح لم يُبنَ لها مسار مع رمز سبب ثابت (لا جملة). */
data class AtlasDiscoveredPlan(
    val bindings: List<AtlasRouteBinding>,
    val skipped: List<Pair<String, String>>,
) {
    val routes: List<AtlasRouteCandidate> get() = bindings.map { it.candidate }
}

/**
 * يبني — وينفّذ — كتابة سقف على المقابض التي **اكتشفها** أطلس.
 *
 * @param access الوصول: القراءة والكتابة وخط الأساس. لا يخرج من هنا إلى مخطط قطّ.
 * @param owner مالك المعاملة. أطلس مالك آليّ، فمكانه **تحت `PER_APP` وفوق `GLOBAL_PROFILE`**:
 *   أسبقية الأولوية لا تحمي اختيار المستخدم اليدوي وحدها (فذلك عمل [ManualControlLocks])،
 *   لكنها تضمن أن ملف تطبيق لا يُقاطَع بكتابة آلية.
 * @param token بصمة المعاملة. ثابتة للمالك الواحد حتى يعود الاسترجاع إلى خط الأساس الأصلي.
 */
class AtlasDiscoveredControl(
    private val access: AtlasCeilingAccess,
    private val token: String,
    private val owner: ControlOwnership.Owner = ControlOwnership.Owner.MAX_AI,
    private val confirmationSamples: Int = 3,
    private val confirmationIntervalMs: Long = 40L,
) {

    init {
        require(token.isNotBlank()) { "a control transaction needs an identity" }
        require(confirmationSamples in 1..8) { "confirmation samples must stay bounded" }
        require(confirmationIntervalMs in 0L..500L) { "confirmation interval must stay bounded" }
        require(owner == ControlOwnership.Owner.MAX_AI || owner == ControlOwnership.Owner.PER_APP) {
            "an automated discovery route may not claim a safety or recovery owner"
        }
    }

    /**
     * يخطّط كتابة سقف CPU للمقابض المكتشفة.
     *
     * @param ceilingKHz السقف المطلوب لكل سياسة. `null` = لا طلب لهذه السياسة ⇒ لا مسار
     *   (فلا كتابة بلا نيّة).
     * @param reviewed هل راجع الكتالوج عقدة هذه السياسة؟ الافتراضي `false` **عن قصد**:
     *   `UNIT_AMBIGUOUS` يعني «لم نُثبت الوحدة»، أما هذه فتعني «لم يُراجع أحد هذه العقدة».
     */
    fun cpuCeilingPlan(
        facts: List<AtlasCpuPolicyFact>,
        ceilingKHz: (AtlasCpuPolicyFact) -> Long?,
        reviewed: (AtlasCpuPolicyFact) -> Boolean = { false },
    ): AtlasDiscoveredPlan {
        val bindings = mutableListOf<AtlasRouteBinding>()
        val skipped = mutableListOf<Pair<String, String>>()

        facts.forEach { fact ->
            val key = HardwareControlKey.cpuLimits(fact.name)
            val routeId = HardwareRepairExecutor.labelFor(key)
            val requested = ceilingKHz(fact)
            if (requested == null || requested <= 0L) {
                skipped += key to SKIP_NO_REQUEST
                return@forEach
            }

            // خط الأساس يُقرأ **مرّة واحدة** ويُستعمل للأدلّة وللمدى معًا: قراءتان تفصل بينهما
            // لحظة قد تُعطي رقمين مختلفين، فيوصف خط أساس لم يُكتب منه شيء.
            val baseline = measuredBaseline(fact)
            val desired = desiredRange(fact, baseline, requested)
            if (desired == null) {
                skipped += key to SKIP_NO_PROVEN_RANGE
                return@forEach
            }

            bindings += AtlasRouteBinding(
                candidate = AtlasRouteCandidate(
                    id = routeId,
                    priority = 0,
                    evidence = evidenceFor(fact, baseline, reviewed(fact)),
                ),
                request = HardwareRepairRequest(
                    routeId = routeId,
                    key = key,
                    owner = owner,
                    token = token,
                    desired = desired,
                    apply = { range -> access.writeLimits(fact.path, range) },
                    read = { access.readLimits(fact.path) },
                    restore = { range -> access.writeLimits(fact.path, range) },
                    baseline = baseline,
                    // نفس حكم السقف المستعمل في كل كاتب آخر: السقف يعني «لا تتجاوز» لا «ساوِ».
                    verify = HardwareVerification::rangeContained,
                    // وطلب **رفع** سقف إلى قيمة نكتبها نحن لا يكفي فيه «دون السقف» دليلًا: بلا هذا
                    // يُقرأ سقفُنا السابق مُلبًّى فلا تُكتب قيمت أعلى أبدًا (وتُقاس في كل مقبض سقف).
                    realized = HardwareVerification::ceilingReached,
                    stabilitySamples = confirmationSamples,
                    stabilityIntervalMs = confirmationIntervalMs,
                ),
            )
        }

        return AtlasDiscoveredPlan(bindings, skipped)
    }

    /**
     * يخطّط ثم ينفّذ عبر [AtlasAdaptiveExecutor] — أي أن أطلس يختار المسار ويُنفّذه بالمعاملة
     * الكاملة، لا يكتب كتابة خاصة.
     */
    fun applyCpuCeiling(
        executor: AtlasAdaptiveExecutor,
        facts: List<AtlasCpuPolicyFact>,
        ceilingKHz: (AtlasCpuPolicyFact) -> Long?,
        reviewed: (AtlasCpuPolicyFact) -> Boolean = { false },
        packageName: String? = null,
        goal: AtlasControlGoal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
        measuredGoalAvailable: Boolean = true,
    ): AtlasDiscoveredResult {
        val plan = cpuCeilingPlan(facts, ceilingKHz, reviewed)
        if (plan.bindings.isEmpty()) return AtlasDiscoveredResult(plan, null)
        // الهدف يحمل قيمة CPU_FREQUENCY نفسها التي تحملها أدلّة كل مسار: مخطِّط أطلس يُرشّح
        // المسارات بهدفها، فهدفٌ مخالف يترك المسارات بلا اختيار («no eligible route») بلا سبب ظاهر.
        val intent = AtlasControlIntent(
            target = AtlasControlTarget.CPU_FREQUENCY,
            goal = goal,
            desired = plan.bindings.firstOrNull()?.request?.desired,
            packageName = packageName,
        )
        return AtlasDiscoveredResult(
            plan = plan,
            adaptive = executor.execute(
                intent = intent,
                bindings = plan.bindings,
                measuredGoalAvailable = measuredGoalAvailable,
            ),
        )
    }

    /**
     * أدلّة المسار — كل حقل فيها **مشتقّ من قياس**، وسببُه مكتوب بصيغة ثابتة في [AtlasRouteEvidence.reason].
     *
     * ووحدة القياس تُعتبر مُثبتة فقط حين **أعلنت النواة حدّيها** و**نشرت سلّمًا**: أرقام على عقدة
     * بلا سلّم ولا حدود مُعلنة لا نعرف سلّمها، فإدّعاء الوحدة هناك هو بالضبط ما يمنعه المخطِّط.
     */
    private fun evidenceFor(
        fact: AtlasCpuPolicyFact,
        baseline: String?,
        reviewed: Boolean,
    ): AtlasRouteEvidence {
        val unitProven = fact.hasLadder && fact.boundsDeclaredByKernel
        val baselineReadable = maxFieldOf(baseline) != null
        return AtlasRouteEvidence(
            providerId = PROVIDER_ID,
            transport = AtlasControlTransport.ARBITER_SYSFS,
            target = AtlasControlTarget.CPU_FREQUENCY,
            // «مقروء» هنا = عقدة الترددات أجابت في الاكتشاف: `scaling_cur_freq`/`cpuinfo_cur_freq`.
            readable = fact.currentKHz != null,
            privilegeAvailable = access.privileged,
            unitProven = unitProven,
            baselineReadable = baselineReadable,
            // استرجاع مُثبت = خط أساس مقروء **و**كاتب متاح؛ بلا الكاتبين معًا لا يُدَّعى استرجاع.
            rollbackProven = baselineReadable && access.privileged,
            reviewed = reviewed,
            reason = buildString {
                append("discovered; ladder=").append(fact.frequencyLadderKHz.size)
                append("; kernelBounds=").append(fact.boundsDeclaredByKernel)
                append("; current=").append(fact.currentKHz ?: "unread")
                append("; baseline=").append(if (baselineReadable) "read" else "unread")
                append(if (reviewed) "; reviewed" else "; not-reviewed")
            },
        )
    }

    /** خط الأساس المقيس الآن من العقدة نفسها — لا من قيمة قديمة محفوظة في الاكتشاف. */
    private fun measuredBaseline(fact: AtlasCpuPolicyFact): String? =
        runCatching { access.readLimits(fact.path) }.getOrNull()

    // وملاحظة مقصودة: المسار يُبنى حتى حين يكون خط الأساس غير مقروء (بأرضية فارغة)، لأن رفض
    // الكتابة قرار **المخطِّط** برمزه القياسي (`BASELINE_UNREADABLE`). ولو أُسقط المسار هنا لضاع
    // السبب، وصار الرفض صمتًا يقرأه المستخدم «لا شيء يحدث» — وهو أسوأ من فشل مُعلن.

    /**
     * الصيغة المطلوبة `min:max` بالصيغة القياسية نفسها التي يستعملها كل كاتب لهذا المقبض.
     *
     * والأرضية تُؤخذ من القراءة الحيّة كما هي (قيمة قبلها السائق أصلًا)، والسقف يُقصّ إلى سلّم
     * الترددات المُعلن: طلبٌ أعلى من أقصى ما أثبتته النواة **لا يصير قيمة مُخترعة** بل يُقصّ،
     * وإن لم يوجد سلّم أصلًا فلا مسار.
     */
    private fun desiredRange(fact: AtlasCpuPolicyFact, baseline: String?, requestedKHz: Long): String? {
        val liveMin = pairOf(baseline)?.first
        val provenMax = fact.provenMaxKHz ?: fact.maxKHz
        if (provenMax == null || provenMax <= 0L) return null
        val ceiling = minOf(requestedKHz, provenMax)
        val snapped = snapDown(fact, ceiling) ?: return null
        return "${liveMin?.toString() ?: ""}:$snapped"
    }

    /** القصّ إلى السلّم المُعلن، وإلى القيمة نفسها إن لم يُعلَن سلّم (الحالة تمنعها أصلًا). */
    private fun snapDown(fact: AtlasCpuPolicyFact, ceilingKHz: Long): Long? {
        val ladder = fact.frequencyLadderKHz.filter { it > 0L }.sorted()
        if (ladder.isEmpty()) return null
        return ladder.lastOrNull { it <= ceilingKHz } ?: ladder.first()
    }

    /** السقف الرقميّ في نصّ `min:max` — و`null` حين لا حقل رقميّ (لا «صفر» مزيّف). */
    private fun maxFieldOf(range: String?): Long? = pairOf(range)?.second

    private fun pairOf(range: String?): Pair<Long?, Long?>? {
        val parts = range?.split(':', limit = 2) ?: return null
        if (parts.size != 2) return null
        val min = parts[0].trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        val max = parts[1].trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        if (min == null && max == null) return null
        return min to max
    }

    companion object {
        /** مَن يقدّم الأدلّة: إعادة استخدام مُصرِّفات الواجهات الخلفية القائمة، لا مُصرِّف جديد. */
        const val PROVIDER_ID: String = "atlas-discovery"

        /** لا نيّة مطلوبة لهذه السياسة ⇒ لا مسار ولا كتابة. */
        const val SKIP_NO_REQUEST: String = "no-ceiling-requested"

        /** لا سلّم مُعلن ولا خط أساس ⇒ لا مدى يمكن كتابته بصدق. */
        const val SKIP_NO_PROVEN_RANGE: String = "no-proven-range"
    }
}
