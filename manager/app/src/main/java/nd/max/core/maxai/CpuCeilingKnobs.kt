package nd.max.core.maxai

import nd.max.core.atlas.AtlasBackendProvider
import nd.max.core.atlas.AtlasCpuPolicyFact
import nd.max.core.hardware.AtlasAdaptiveExecutor
import nd.max.core.hardware.AtlasCeilingAccess
import nd.max.core.hardware.AtlasDiscoveredControl
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * عقدة "سقف تردد CPU لكل سياسة cpufreq" عبر المُحكِّم الموحد — نفس
 * مسار التحكيم المجرَّب (مفتاح cpu_limits:<policy> بصيغة "min:max"،
 * كتابة موثّقة بقراءة حية بعد الكتابة، وخط أساس للاسترجاع).
 *
 * يستخدمه محرك MAX AI (Owner.MAX_AI) ومحرك الأمان (Owner.SAFETY):
 * عندما يتدخل الأمان يسبق ملكيته كل مالك آخر فيُحجب سقف AI تلقائيًا
 * في نفس المُحكِّم — هذا هو تنفيذ "الأمان فوق الجميع" على العتاد.
 */
@Singleton
class CpuCeilingKnobs @Inject constructor(
    private val arbiter: HardwareControlArbiter,
    /**
     * ما اكتشفته الواجهات الخلفية على هذا الجهاز — يُقاس عند الطلب، ولا يُخزَّن ادّعاء.
     */
    private val atlasDiscovery: AtlasBackendProvider,
    /**
     * مُنفِّذ أطلس المُتكيِّف. وجوده هنا هو ما يجعل هذا المقبض **مسار أطلس إنتاجيًا** لا مخطِّطًا
     * لا مُستدعي له: الطلب يُخطَّط من دليلٍ مقيس ثم يُنفَّذ بمعاملة المُحكِّم الكاملة.
     */
    private val atlasAdaptive: AtlasAdaptiveExecutor,
) {
    data class KnobOutcome(val applied: Int, val blocked: Int, val failed: Int, val detail: String)

    /**
     * يثبّت سقفًا لكل سياسة عند جزء محدد من مدى العتاد
     * ([fractionOfRange] من 0.0 إلى 1.0؛ 1.0 = تحرير كامل).
     *
     * والمسارات اثنان، بالترتيب:
     *
     * 1. **مسار أطلس** ([capDiscovered]): يُخطَّط من **دليلٍ مقيس** (سياسات اكتشفها أطلس فعلًا،
     *    وسلّم تردداتها المُعلن) ويُنتفَّذ بمعاملة المُحكِّم الكاملة. وهذا هو ما يجعل أطلس يكتب
     *    في الإنتاج لا في الاختبارات وحدها.
     * 2. **المسار المُتحقَّق القائم** ([submitPerPolicy]): يُستعمل حين لا يُخطَّط مسار أطلس أصلًا
     *    (جهاز لا يُعلن سياسات، أو وحدة غير مُثبتة، أو مسار لم يُراجَع) — **وسلوكه لم يتغيّر
     *    بالحرف**، ودليله يُلحَق بالتفصيل (`atlas=…`) فيُعرف من السجل أيّ المسارين كتب.
     *
     * ولا يُنفَّذ المساران معًا على السياسة نفسها: أطلس إن كتب سياسة فلا تُعاد كتابتها مباشرة.
     */
    fun cap(fractionOfRange: Float, owner: ControlOwnership.Owner, token: String): KnobOutcome {
        val fraction = fractionOfRange.coerceIn(0.1f, 1f)

        val discovered = measuredPolicies()
        val viaAtlas = discovered.takeIf { it.isNotEmpty() }
            ?.let { facts -> capDiscovered(facts, fraction, owner, token) }
        if (viaAtlas != null && viaAtlas.applied > 0) return viaAtlas

        val direct = submitPerPolicy(owner, token) { policy ->
            val hwMin = policy.hwMinKHz ?: policy.minKHz ?: 0L
            val hwMax = policy.hwMaxKHz ?: policy.maxKHz ?: return@submitPerPolicy null
            // الكسر يبقى على **مدى العتاد** (هذا معناه: نسبة من المدى)، وما يُكتب
            // فعليًّا يُلتقط من الجدول المُعلن. السبب مقيس لا نظري: قيمةٌ بين الحدّين
            // وليست في جدول OPP لا يرفضها السائق، بل يُبدّلها بقيمة أخرى — فيقرأ
            // المُحكِّم قيمةً ≠ المطلوب ويحكم على تغييرٍ ناجح بالفشل ثم يسترجع
            // (التفصيل والقياس في `CpuHardwareBackend.snapToAvailableAtOrBelow`).
            val cappedMax = hwMin + ((hwMax - hwMin) * fraction).toLong()
            val min = CpuHardwareBackend.snapToAvailableAtOrBelow(policy, hwMin)
            val max = CpuHardwareBackend.snapToAvailableAtOrBelow(policy, cappedMax)
            "$min:$max"
        }
        // ودليل محاولة أطلس يُلحق بالتفصيل: يُعرف من السجل أيّ المسارين كتب، ولماذا لم يُكتب
        // شيء من مسار أطلس (سياسة بلا سلّم مُعلن، أو مسار لم يُراجَع).
        return direct.copy(detail = direct.detail + viaAtlas?.detail.orEmpty())
    }

    /**
     * يحرر السقف إلى مدى العتاد الكامل (أداء مفتوح).
     *
     * ولا يمرّ بمسار أطلس عن قصد: هذا **استرجاع** إلى مدى العتاد المُعلن كما هو لا تخطيط سقف،
     * وطرحُه على مخطِّط كان سيُدخل قرارًا في عملية لا قرار فيها. والكاتب واحد في الحالتين.
     */
    fun release(owner: ControlOwnership.Owner, token: String): KnobOutcome =
        submitPerPolicy(owner, token) { policy ->
            val hwMin = policy.hwMinKHz ?: policy.minKHz ?: return@submitPerPolicy null
            val hwMax = policy.hwMaxKHz ?: policy.maxKHz ?: return@submitPerPolicy null
            // التحرير أيضًا يُكتب بترددات حقيقية: `cpuinfo_max_freq` قد يكون أكبر من
            // أكبر OPP مُعلَن، وكتابة قيمة غير مُعلَنة تُبدَّل في النواة فيبدو التحرير
            // فاشلًا ويُسترجع السقف — أي أن المقبض يبقى مقيّدًا بلا سبب مكتوب.
            val min = CpuHardwareBackend.snapToAvailableAtOrBelow(policy, hwMin)
            val max = CpuHardwareBackend.snapToAvailableAtOrBelow(policy, hwMax)
            "$min:$max"
        }

    /** يترك كل مفاتيح هذا المالك (مع استرجاع خط الأساس عند آخر مغادرة). */
    fun leaveAll(token: String) {
        arbiter.releaseToken(token, restore = true)
    }

    private fun submitPerPolicy(
        owner: ControlOwnership.Owner,
        token: String,
        desiredFor: (CpuHardwareBackend.Policy) -> String?,
    ): KnobOutcome {
        val policies = CpuHardwareBackend.policies()
        if (policies.isEmpty()) {
            return KnobOutcome(0, 0, 0, "no cpufreq policies")
        }

        var applied = 0
        var blocked = 0
        var failed = 0
        val details = StringBuilder()

        policies.forEach { policy ->
            val desired = desiredFor(policy) ?: return@forEach
            val key = HardwareControlKey.cpuLimits(policy.name)
            val baseline = "${policy.minKHz ?: ""}:${policy.maxKHz ?: ""}"

            val result = arbiter.submit(
                key = key,
                owner = owner,
                token = token,
                desired = desired,
                apply = { value -> setLimits(policy, value) },
                read = {
                    CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }
                        ?.let { "${it.minKHz ?: ""}:${it.maxKHz ?: ""}" }
                },
                baseline = baseline,
                restore = { value -> setLimits(policy, value) },
            )

            when {
                result.blocked -> {
                    blocked++
                    details.append("${policy.name}=محجوز(${result.winner});")
                }
                result.verified -> {
                    applied++
                    details.append("${policy.name}=${result.actual};")
                }
                else -> {
                    failed++
                    details.append("${policy.name}=فشل(${result.actual ?: "none"});")
                }
            }
        }
        return KnobOutcome(applied, blocked, failed, details.toString())
    }

    /**
     * كتابة السقف عبر أطلس — من دليلٍ مقيس إلى معاملة المُحكِّم.
     *
     * وهي **قابلة للاختبار** عن قصد (أدلّة مُمرَّرة + وصول مُمرَّر)، لأن المسار الإنتاجي يقرأ من
     * `/sys` فلا يُقاس في JVM. ومعاملاتها الثلاثة هي ما يُغيَّر في الاختبار فقط.
     *
     * @param access الوصول إلى العقد. الإنتاجي يفوّض إلى الكاتب المُتحقَّق القائم
     *   (`CpuHardwareBackend.setPolicyLimits`) — لا كاتب ثانٍ ينجرف عن الأول.
     * @param reviewed هل راجع التطبيق شكل هذا المسار؟ ([AtlasDiscoveredControl.isReviewedControlRoute]).
     * @return ملخّص المحاولة. `applied > 0` تعني أن أطلس كتب فعلًا؛ و`applied == 0` تعني «لم يُكتب
     *   شيء» — والمستدعي حينها يتراجع إلى المسار المُتحقَّق، وسبب التخطّي يبقى في `detail` للتسجيل.
     */
    fun capDiscovered(
        facts: List<AtlasCpuPolicyFact>,
        fraction: Float,
        owner: ControlOwnership.Owner,
        token: String,
        access: AtlasCeilingAccess = SystemCeilingAccess,
        reviewed: (AtlasCpuPolicyFact) -> Boolean = { fact ->
            AtlasDiscoveredControl.isReviewedControlRoute(HardwareControlKey.cpuLimits(fact.name))
        },
    ): KnobOutcome {
        val control = AtlasDiscoveredControl(access = access, token = token, owner = owner)
        val result = control.applyCpuCeiling(
            executor = atlasAdaptive,
            facts = facts,
            ceilingKHz = { fact -> fractionCeiling(fact, fraction) },
            reviewed = reviewed,
        )
        val planned = result.plan.bindings.size
        val detail = buildString {
            append("atlas=")
            append(result.selectedRouteId ?: "no-route:" + (result.adaptive?.detail ?: "not-planned"))
            append(";planned=").append(planned)
            append(";skipped=").append(result.plan.skipped.size)
            result.adaptive?.skipped?.takeIf { it.isNotEmpty() }?.let { skipped ->
                append(";routes-skipped=").append(skipped.joinToString(",") { it.first + ":" + it.second })
            }
            append(";")
        }
        return if (result.verified) {
            KnobOutcome(applied = planned, blocked = 0, failed = 0, detail = detail)
        } else {
            KnobOutcome(applied = 0, blocked = 0, failed = 0, detail = detail)
        }
    }

    /**
     * السياسات كما اكتشفها أطلس الآن. فشل الاكتشاف لا يُسقط المقبض: يُعيد قائمة فارغة فيمرّ
     * الطلب على المسار المُتحقَّق — فالاكتشاف إضافة، لا شرط لعمل التحكم.
     */
    private fun measuredPolicies(): List<AtlasCpuPolicyFact> =
        runCatching { atlasDiscovery.cpu().cpuPolicies }.getOrDefault(emptyList())

    /**
     * السقف المطلوب لسياسة عند جزء من **مدىها** المُثبت (أدنى وأقصى ما أثبتته النواة أو السلّم).
     *
     * والفرق عن المسار المُتحقَّق معلن: المسار المُتحقَّق يحسب الجزء على `hwMin/hwMax` وإلا على
     * الحدود الحيّة، وهذا يحسبه على `provenMin/provenMax` (وقد يكونان طرفي السلّم حين لا تُعلن
     * النواة حدّيها) — وهي أقرب إلى معنى «جزء من مدى العتاد»؛ والقيمة تُقصُّ بعدها إلى السلّم في
     * [AtlasDiscoveredControl.desiredRange] فلا تُكتب قيمة غير مُعلنة.
     */
    private fun fractionCeiling(fact: AtlasCpuPolicyFact, fraction: Float): Long? {
        val min = fact.provenMinKHz ?: return null
        val max = fact.provenMaxKHz ?: return null
        if (max <= min) return null
        return min + ((max - min) * fraction).toLong()
    }

    /**
     * الوصول الإنتاجي إلى مقابض سقف CPU: قراءة من السياسات المكتشفة، وكتابة عبر
     * `CpuHardwareBackend.setPolicyLimits` — نفس ما يستعمله كل كاتب آخر لهذا المفتاح.
     *
     * و`privileged` هنا تعني حرفيًّا «معاملة قابلة للمحاولة» (السياسات موجودة، والكتابة تمرّ
     * بالكاتب المُتحقَّق): **لا قراءة تُثبت صلاحية الكتابة**، والحكم النهائي عند `writeLimits`
     * نفسه — وقبولها الكاذب لا يُنتج ادّعاء نجاح، لأن `false` تُسقط المعاملة فيُعلن الفشل.
     */
    private object SystemCeilingAccess : AtlasCeilingAccess {
        override val privileged: Boolean get() = CpuHardwareBackend.policies().isNotEmpty()

        override fun readLimits(policyPath: String): String? =
            CpuHardwareBackend.policies()
                .firstOrNull { it.path == policyPath }
                ?.let { "${it.minKHz ?: ""}:${it.maxKHz ?: ""}" }

        override fun writeLimits(policyPath: String, range: String): Boolean {
            val parts = range.split(":", limit = 2)
            val min = parts.getOrNull(0)?.trim()?.takeIf(String::isNotEmpty)?.toLongOrNull()
            val max = parts.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)?.toLongOrNull()
            return CpuHardwareBackend.setPolicyLimits(policyPath, min, max).successful
        }
    }

    private fun setLimits(policy: CpuHardwareBackend.Policy, value: String): Boolean {
        val parts = value.split(":", limit = 2)
        val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        return CpuHardwareBackend.setPolicyLimits(policy.path, min, max).successful
    }
}
