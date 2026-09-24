package nd.max.core.atlas

import nd.max.core.hardware.AtlasAdaptiveExecutor
import nd.max.core.hardware.AtlasAdaptiveResult
import nd.max.core.hardware.AtlasAdapterChoice
import nd.max.core.hardware.AtlasAdapterContext
import nd.max.core.hardware.AtlasAdapterPlan
import nd.max.core.hardware.AtlasAdapterRegistry
import nd.max.core.hardware.AtlasControlRequest
import nd.max.core.hardware.AtlasRouteMemory
import nd.max.core.hardware.HardwareRepairState

/**
 * **Max Atlas** — نظام الذكاء والتكيف المركزي: كيف أجعل ميزات MaxManager تعمل على **هذا** الجهاز؟
 *
 * الدورة التي صُمِّم حولها، وكل مرحلة لها مالكها في الشيفرة:
 *
 * ```text
 * Discover ─ Understand ─ Map ── Adapt ── Execute ── Verify ── Learn
 *    │            │         │       │         │          │        │
 *    │            │         │       │         │          │        └ AtlasRouteMemory + AtlasEvidenceStore
 *    │            │         │       │         │          └ HardwareVerification + نافذة التأكيد (read-back)
 *    │            │         │       │         └ AtlasAdaptiveExecutor ← HardwareRepairExecutor ← المُحكِّم
 *    │            │         │       └ AtlasAdapterRegistry + AtlasControlAdapter (هذا الملف + AtlasAdapters)
 *    │            │         └ AtlasCapabilityMap + AtlasSafetyPolicy (ما هو مدعوم/للقراءة فقط/يحتاج
 *    │            │            مُلاءِمًا/غير متاح/يجب عدم لمسه/مجهول)
 *    │            └ AtlasDeviceIdentity + AtlasModels (الدلالة والوحدات — لا استنتاج من مجرد وجود عقدة)
 *    └ AtlasDiscovery + AtlasBackendProvider (مسح محدود الميزانية، غياب يُثبت لا يُفترض)
 * ```
 *
 * **الحدّ الفاصل — أطلس ≠ Max AI — وهو عقد لا إرشاد:**
 *
 * | النظام | سؤاله | ما يملكه | ما لا يملكه |
 * | --- | --- | --- | --- |
 * | **Max Atlas** | كيف أعمل على هذا الجهاز؟ | اكتشاف الواجهات، خريطة القدرة، اختيار المسار/الملاءِم، التنفيذ بالمعاملة المُتحقَّقة، التحقق من الحالة الفعلية، التعلّم لكل جهاز | لا هدف أداء، لا أولوية، لا «متى» — هذا الملف لا يقرّر شيئًا عن المستخدم |
 * | **Max AI** | ماذا أفعل الآن؟ | الهدف (أداء/توازن/بطارية)، القيم المطلوبة لكل مقبض، متى يُطلب ومتى يُترك | لا يختار مسارًا ولا يخترع عقدة ولا يكتب كتابة مباشرة |
 *
 * أي: MAX AI يسأل «أريد سقف ٦٠٪» (قرار سياسة)، فأطلس يجيب «على هذا الجهاز تُكتب عبر
 * `cpu_limits:policyN` بمدى مُقصٍّ على السلّم المُعلن، وتُتحقَّق قراءةً مرتجعة، وإن لم يثبت
 * النجاح فلا نجاح يُسجَّل». ولا ينتقل أي مسؤولية من عمود إلى عمود: أطلس لا يحسّن الأداء، وMAX AI
 * لا يكتشف العتاد.
 *
 * **صدق النتيجة (Verify):** النجاح حالة واحدة: `VERIFIED` — أي كتابة أُثبتت **بالقراءة من الجهاز**
 * على نافذة التأكيد. إرسال الأمر لا يكفي، و«كُتبت» ليست «استقرّت»؛ وما عدا ذلك يُسجَّل بحالته
 * الحقيقية (`FAILED_ROLLED_BACK` · `DRIFTED_ROLLED_BACK` · `STATE_UNKNOWN` · `BLOCKED` …) ولا يُجمَع
 * في «فشل» مبهم ولا يُلمَّع. وما لا تجربة أثبتته يبقى `UNKNOWN` في الخريطة، لا «مدعوم».
 *
 * هذا الملف تركيب لا ميكانيكا: لا يقرأ عقدة ولا يمسك كاتبًا. القراءة لمسار المسح المحدود، والكتابة
 * تمرّ حصريًّا عبر `AtlasRepairPort` ← `HardwareRepairExecutor` (حدّ المعاملة المُتحقَّق الوحيد)،
 * والتعلّم عبر ذاكرة المسارات — وهي الثلاثة مُحقونة من خارجه.
 */
class MaxAtlas(
    private val registry: AtlasAdapterRegistry,
    private val executor: AtlasAdaptiveExecutor,
    private val memory: AtlasRouteMemory? = null,
    private val identity: AtlasDeviceIdentity,
    private val catalogVersion: String,
    private val clockMs: () -> Long = { 0L },
    private val bootGeneration: () -> Long = { 0L },
    private val privilegeGeneration: () -> Long = { 0L },
) {

    /**
     * أهداف لها **فعل كتابة قائم في هذا البناء** خارج سجلّ الملاءِمين — لا افتراضًا عن جهاز، بل
     * اسمُ فعلٍ موجود اليوم في الشيفرة ويُقاس بـ`grep` لاسمه لا بذاكرة:
     * `CpuHardwareBackend.setGovernor` و`GpuHardwareBackend.setGovernor` (يستعملهما `PerAppKernelUtil`
     * و`AppMonitor` في معاملات المُحكِّم نفسها).
     *
     * والغرض صِدق الخريطة وحده: هدفٌ **يكتب** ولا مُلاءِم له بعد = «يحتاج مُلاءِمًا»
     * (`NEEDS_ADAPTER`) لا «للقراءة فقط» (`READ_ONLY`) — فالأول يدعوه لإضافة طريقة، والثاني يَعِد
     * بعدم وجود ما يُصلَّح. وتسجيل مُلاءِم لكلٍّ منها **يُفرغ هذه القائمة** — وهي تُبقي الصفّ صادقًا
     * حتى ذلك الحين.
     */
    private val legacyWriteTargets: Set<AtlasControlTarget> = setOf(
        AtlasControlTarget.CPU_GOVERNOR,
        AtlasControlTarget.GPU_GOVERNOR,
    )

    /**
     * Discover → Understand → Map → Learn: خريطة قدرة الجهاز، كما هي الآن.
     *
     * المسار التمثيلي لكل هدف يُبنى ([nd.max.core.hardware.AtlasControlAdapter.probe] — بلا كتابة)
     * ويُحكم عليه بمخطِّط أطلس، وتُدمج ذاكرة ما نجح فعلًا. وكل إدخال ينتهي بصلاحيته: جيل إقلاع
     * آخر يبطل ما تعلّمناه، فلا تتحوّل المعرفة إلى افتراض ثابت.
     */
    fun profile(
        context: AtlasAdapterContext,
        targets: List<AtlasControlTarget> = AtlasControlTarget.values().toList(),
    ): AtlasDeviceProfile {
        val boot = bootGeneration()
        val privilege = privilegeGeneration()
        val entries = targets.map { target -> capabilityFor(target, context, boot, privilege) }
        return AtlasDeviceProfile.build(
            identity = identity,
            catalogVersion = catalogVersion,
            capabilities = AtlasCapabilityMap(entries),
            learned = memory?.entries().orEmpty(),
            clockMs = clockMs,
            bootGeneration = boot,
            privilegeGeneration = privilege,
        )
    }

    /**
     * Adapt → Execute → Verify → Learn: تنفيذ نيّة واحدة بالطريقة التي تناسب هذا الجهاز.
     *
     * الترتيب مُلزم: قواعد عدم اللمس ← اختيار الملاءِم (الفجوة تُعلن `NO_ADAPTER`) ← بناء المسارات
     * ← بوابة المخطِّط ← المعاملة الكاملة (خط أساس · كتابة · قراءة مرتجعة · نافذة تأكيد · استرجاع)
     * ← حكم واحد للنتيجة ← ما تعلّمته الذاكرة. ولا يُخرج تقريرٌ نجاحًا إلا من قراءة أثبتته.
     */
    fun execute(context: AtlasAdapterContext, request: AtlasControlRequest): AtlasExecutionReport {
        val target = request.intent.target
        return when (val choice = registry.choose(target, context, request)) {
            is AtlasAdapterChoice.Denied -> refusal(
                verdict = AtlasExecutionVerdict.REFUSED_SAFETY,
                target = target,
                context = context,
                safety = AtlasSafetyVerdict.Denied(choice.ruleId, "adapter surface denied by safety policy"),
                refusalCode = "never-touch:${choice.ruleId}",
            )

            is AtlasAdapterChoice.Gap -> refusal(
                verdict = AtlasExecutionVerdict.NO_ADAPTER,
                target = target,
                context = context,
                refusalCode = "no-adapter:" + (choice.rejections.joinToString(",") { "${it.first}:${it.second}" }
                    .ifEmpty { "none-registered" }),
            )

            is AtlasAdapterChoice.Chosen -> executeChosen(choice, context, request)
        }
    }

    private fun executeChosen(
        choice: AtlasAdapterChoice.Chosen,
        context: AtlasAdapterContext,
        request: AtlasControlRequest,
    ): AtlasExecutionReport {
        val target = request.intent.target
        val planned = choice.adapter.plan(context, request)

        // حارس عدم اللمس الثاني: بالمسار في الملاءِم، وبالمفتاح هنا — مسارٌ سمّى عقدة ممنوعة
        // يُسقَط مهما بناءه، وهذا هو معنى «فوق الجميع».
        var deniedRule: String? = null
        val kept = planned.bindings.filter { binding ->
            val verdict = AtlasSafetyPolicy.verdictForKey(binding.request.key)
            if (verdict is AtlasSafetyVerdict.Denied) {
                deniedRule = verdict.ruleId
                false
            } else {
                true
            }
        }
        val deniedSkips = planned.bindings
            .filterNot { binding -> kept.any { it.candidate.id == binding.candidate.id } }
            .map { binding ->
                binding.request.key to "never-touch:${deniedRule ?: "unknown"}"
            }
        val plan = AtlasAdapterPlan(planned.adapterId, kept, planned.skipped + deniedSkips)

        if (kept.isEmpty()) {
            // ورفض السلامة يُقرأ من مصدرين: ما أسقطه الحارس هنا (deniedSkips)، وما أسقطه الملاءِم
            // في خطته برمز `never-touch:*` — فschütage «فوق الجميع» لا تتغيّر بتغيير مَن رشّح.
            val skipRule = plan.skipped
                .firstOrNull { it.second.startsWith("never-touch:") }
                ?.second
                ?.substringAfter("never-touch:")
            val safetyRefused = deniedSkips.isNotEmpty() || skipRule != null
            return AtlasExecutionReport(
                verdict = if (safetyRefused) AtlasExecutionVerdict.REFUSED_SAFETY else AtlasExecutionVerdict.NOT_PLANNED,
                adapterId = plan.adapterId,
                plan = plan,
                adaptive = null,
                capability = AtlasCapabilityRules.derive(
                    AtlasCapabilityInputs(
                        target = target,
                safety = if (safetyRefused) {
                    AtlasSafetyVerdict.Denied(
                        deniedRule ?: skipRule ?: "unknown",
                        "planned routes touched a denied interface",
                    )
                } else {
                    AtlasSafetyVerdict.Allowed
                },
                        measured = true,
                        readable = readableHint(context, target),
                        absenceProved = false,
                        routeKnown = false,
                        writeExpected = true,
                        adapterId = plan.adapterId,
                    ),
                ),
                refusalCode = plan.skipped.firstOrNull()?.second,
            )
        }

        val adaptive = executor.execute(
            intent = request.intent.copy(desired = kept.first().request.desired),
            bindings = kept,
            measuredGoalAvailable = request.measuredGoalAvailable,
        )
        return AtlasExecutionReport(
            verdict = verdictOf(adaptive),
            adapterId = plan.adapterId,
            plan = plan,
            adaptive = adaptive,
            capability = AtlasCapabilityRules.derive(
                AtlasCapabilityInputs(
                    target = target,
                    measured = true,
                    readable = kept.any { it.candidate.evidence.readable },
                    absenceProved = false,
                    routeKnown = true,
                    routeStatus = adaptive.decision.status,
                    routeReason = adaptive.decision.reason,
                    verifiedThisGeneration = adaptive.successful,
                    adapterId = plan.adapterId,
                    writeExpected = true,
                ),
            ),
        )
    }

    private fun refusal(
        verdict: AtlasExecutionVerdict,
        target: AtlasControlTarget,
        context: AtlasAdapterContext,
        safety: AtlasSafetyVerdict = AtlasSafetyVerdict.Allowed,
        refusalCode: String?,
    ): AtlasExecutionReport = AtlasExecutionReport(
        verdict = verdict,
        adapterId = null,
        plan = null,
        adaptive = null,
        capability = AtlasCapabilityRules.derive(
            AtlasCapabilityInputs(
                target = target,
                safety = safety,
                measured = true,
                readable = readableHint(context, target),
                absenceProved = false,
                routeKnown = false,
                // طلبُ تحكم وصل فعلًا: وجوده هو ادّعاء أن كتابةً مطلوبة، فمنعها غيابُ الملاءِم
                // فجوةٌ تُصلَّح لا مراقبة تُقرأ — وصِدق الخريطة بحالتهما يُفصل في `capabilityFor`.
                writeExpected = true,
            ),
        ),
        refusalCode = refusalCode,
    )

    private fun capabilityFor(
        target: AtlasControlTarget,
        context: AtlasAdapterContext,
        boot: Long,
        privilege: Long,
    ): AtlasCapability {
        val choice = registry.choose(target, context, request = null)
        if (choice is AtlasAdapterChoice.Denied) {
            return AtlasCapabilityRules.derive(
                AtlasCapabilityInputs(
                    target = target,
                    safety = AtlasSafetyVerdict.Denied(choice.ruleId, "adapter surface denied by safety policy"),
                    measured = true,
                    readable = readableHint(context, target),
                    absenceProved = false,
                    routeKnown = false,
                    writeExpected = registry.adaptersFor(target).isNotEmpty() || target in legacyWriteTargets,
                ),
            )
        }

        var adapterId: String? = null
        var plan: AtlasAdapterPlan? = null
        if (choice is AtlasAdapterChoice.Chosen) {
            adapterId = choice.adapter.id
            plan = choice.adapter.probe(context)
        }

        // المسار التمثيلي يُبنى ولا يُنفَّذ: الحكم بالمخطِّط على أدلّته هو كل ما تراه الخريطة.
        val bindings = plan?.bindings.orEmpty().filter {
            AtlasSafetyPolicy.verdictForKey(it.request.key) == AtlasSafetyVerdict.Allowed
        }
        val deniedRule = plan?.skipped
            ?.firstOrNull { it.second.startsWith("never-touch:") }
            ?.second
            ?.substringAfter("never-touch:")
        val allDenied = deniedRule != null || (plan != null && plan.bindings.isNotEmpty() && bindings.isEmpty())
        val decision = if (bindings.isNotEmpty()) {
            AtlasRoutePlanner.choose(
                intent = AtlasControlIntent(target = target, goal = AtlasControlGoal.PERFORMANCE),
                candidates = bindings.map { it.candidate },
            )
        } else {
            null
        }
        val verified = memory?.preferredRoute(target.name.lowercase()) != null
        return AtlasCapabilityRules.derive(
            AtlasCapabilityInputs(
                target = target,
                safety = if (allDenied) {
                    AtlasSafetyVerdict.Denied(deniedRule ?: "unknown", "every planned route touches a denied interface")
                } else {
                    AtlasSafetyVerdict.Allowed
                },
                measured = true,
                readable = bindings.any { it.candidate.evidence.readable } || readableHint(context, target),
                absenceProved = false,
                routeKnown = bindings.isNotEmpty(),
                writeExpected = registry.adaptersFor(target).isNotEmpty() || target in legacyWriteTargets,
                routeStatus = decision?.status,
                routeReason = decision?.reason,
                verifiedThisGeneration = verified,
                adapterId = adapterId,
            ),
        )
    }

    /**
     * الحكم على محاولة واحدة — وحده [AtlasExecutionVerdict.VERIFIED] يعني نجاحًا، وهو ما لا يُنتجه
     * إلا آخر محاولة أثبتت قراءةُ الجهاز استقرارَ ما كُتب.
     */
    private fun verdictOf(adaptive: AtlasAdaptiveResult): AtlasExecutionVerdict {
        if (adaptive.successful) return AtlasExecutionVerdict.VERIFIED
        val last = adaptive.attempts.lastOrNull() ?: return AtlasExecutionVerdict.BLOCKED
        return when (last.state) {
            HardwareRepairState.CONFIRMED_WINDOW -> AtlasExecutionVerdict.VERIFIED
            HardwareRepairState.APPLY_FAILED_ROLLED_BACK -> AtlasExecutionVerdict.FAILED_ROLLED_BACK
            HardwareRepairState.DRIFT_ROLLED_BACK -> AtlasExecutionVerdict.DRIFTED_ROLLED_BACK
            HardwareRepairState.ROLLBACK_UNVERIFIED -> AtlasExecutionVerdict.STATE_UNKNOWN
            HardwareRepairState.BLOCKED -> AtlasExecutionVerdict.BLOCKED
        }
    }

    /** أثر القراءة في سياق محدود: ما يُقاس فعلاً للهدف، لا ما يُفترض له. */
    private fun readableHint(context: AtlasAdapterContext, target: AtlasControlTarget): Boolean = when (target) {
        AtlasControlTarget.CPU_FREQUENCY, AtlasControlTarget.CPU_GOVERNOR ->
            context.cpuFacts.any { it.currentKHz != null } || context.cpuPolicies.isNotEmpty()

        AtlasControlTarget.GPU_FREQUENCY -> {
            val gpu = context.gpuDevice
            gpu != null && (gpu.currentFreq != null || gpu.maxFreq != null)
        }

        // الحرارة مراقبة تُقاس لا تُفترض: إشارة/قراءة أجابَت ينقلها المستدعي من قياسه هو.
        AtlasControlTarget.THERMAL_PROFILE -> context.thermalStatusReadable

        else -> false
    }
}

/**
 * حكم التنفيذ — ثماني حالات لا ثلاث، لأن «فشل» مبهم يخفي الفرق بين «لم يُكتب» و«الجهاز في حالة
 * مجهولة»، وهو الفرق الذي يقرّر ماذا يُجرَّب بعده.
 */
enum class AtlasExecutionVerdict {
    /** الكتابة أُثبتت بالقراءة المرتجعة على نافذة التأكيد. النجاح الوحيد المُعلن. */
    VERIFIED,

    /** لا مسار يمكن بناؤه لهذا الطلب (أسباب مُسمّاة في خطة الملاءِم). */
    NOT_PLANNED,

    /** لا ملاءِم لهذا الجهاز/البناء — يحتاج طريقة بديلة، وهذا ليس فشل جهاز. */
    NO_ADAPTER,

    /** قاعدة عدم اللمس منعت الطلب أو كل مساراته. */
    REFUSED_SAFETY,

    /** المخطِّط أو حراسة الملكية رفضت قبل أي كتابة. */
    BLOCKED,

    /** الكتابة لم تستقرّ؛ خط الأساس أُعيد وأُثبت استرجاعه. */
    FAILED_ROLLED_BACK,

    /** استقرّت لحظة ثم انحرفت؛ أُعيد خط الأساس وأُثبت. */
    DRIFTED_ROLLED_BACK,

    /** الانحراف وقع **ولم يُثبت استرجاعه** — الحالة الفيزيائية مجهولة، ولا شيء يُجرَّب بعده. */
    STATE_UNKNOWN,
}

/**
 * تقرير تنفيذ واحد: الحكم + ما بُني وما لم يُبنَ + مكان الهدف على خريطة القدرة بعده.
 *
 * يحمل كل ما يحتاجه مستدٍّ لتسجيل صدق: `plan`/`adaptive` للتفصيل، و`capability.code` لشارة
 * الخريطة، و`refusalCode` بالرمز الثابت — لا جملة مكتوبة هنا تظهر في واجهة بلغة أخرى.
 */
data class AtlasExecutionReport(
    val verdict: AtlasExecutionVerdict,
    val adapterId: String?,
    val plan: AtlasAdapterPlan?,
    val adaptive: AtlasAdaptiveResult?,
    val capability: AtlasCapability,
    val refusalCode: String? = null,
) {
    val verified: Boolean get() = verdict == AtlasExecutionVerdict.VERIFIED
    val selectedRouteId: String? get() = adaptive?.selectedRouteId
    val adaptiveDetail: String? get() = adaptive?.detail
}
