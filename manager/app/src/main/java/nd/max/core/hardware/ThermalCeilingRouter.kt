package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteEvidence

/**
 * مسارا سقف الحرارة كهدف Atlas — لا حلقة خاصة داخل المراقب.
 *
 * لماذا هذا الملف
 * ---------------
 * الحارس الحراري ([ThermalGuard]) يعرف **كيف** يُحسب سقف أبرد؛ ولا يعرف ماذا نفعل حين لا تُجيب
 * المنصة، ولا كيف يتذكّر ما نجح على هذا الجهاز. وAtlas يملك بالضبط هذين: ترتيب مسارات آمن،
 * وبديل عند فشل مسار، وذاكرة لكل جهاز ([AtlasRouteMemory]). فالفصل هنا:
 *
 * - السجل ([PerAppControlRegistry]) يملك **الوصول**: هو الذي يملك إغلاقات الكتابة والقراءة
 *   وخط الأساس، ولا تُسلَّم لغيره.
 * - Atlas يملك **الاختيار**: أيّ مسار يُقدَّم، ومتى يُجرَّب غيره، وما يُتذكَّر.
 * - المُحكِّم ([HardwareControlArbiter]) يملك **التنفيذ**: معاملة واحدة بخط أساس وقراءة مرتجعة
 *   ونافذة تأكيد واسترجاع.
 *
 * المساران
 * --------
 * 1. `thermal.platform-status` — القيمة تُقرَّر من إشارة المنصة (`getCurrentThermalStatus`)، ويُنقل
 *    بها النقل `PLATFORM_HINT` لأنها **إشارة منصة لا عقدة vendor خمّنّاها**. أهليّتها مشروطة بقراءة
 *    الإشارة: منصة لا تُجيب ⇒ هذا المسار غير مؤهّل، فلا يُختار ولا يُخمَّن.
 * 2. `thermal.static-ceiling` — السقف الذي اختاره المستخدم نفسه بلا تدخّل آليّ (نقل `ARBITER_SYSFS`).
 *    وهو البديل حين تغيب الإشارة، وحين يفشل المسار الأول بعد استرجاع مُتحقَّق.
 *
 * وحدود لا تُخترق
 * --------------
 * - **لا يرفع فوق نيّة المستخدم أبدًا**: هدية الحارس قيمة أقل أو مساوية، والمسار الثاني هو نيّة
 *   المستخدم حرفيًّا.
 * - **لا يكتب شيئًا لم يعرفه السجل**: المعاملة تُبنى من مقبض مملوك فقط (`retargetRequest` تُعيد
 *   `null` لغيره)، فلا يستطيع مخطط أن يخترع عقدة.
 * - **`reviewed` ليست ادّعاءً جديدًا عن الجهاز**: المساران يستعملان نفس المعاملة المُثبتة التي
 *   تمرّ بها مقابض per-app أصلًا (خط أساس + قراءة مرتجعة + استرجاع)، فلا مسار كتابة جديد هنا.
 *   ولو تُرك `reviewed = false` لسقط التخطيط إلى `REVIEW_REQUIRED` ولم يعمل الحارس أصلًا — وهذا
 *   هو المعنى المقصود للحقل: مسار **مُراجَع** يمكن أن يُنفَّذ، لا مسار مختبر.
 */
object ThermalCeilingRoutes {

    const val PLATFORM_ROUTE_ID: String = "thermal.platform-status"
    const val STATIC_ROUTE_ID: String = "thermal.static-ceiling"

    private const val PLATFORM_PROVIDER = "platform-thermal-status"
    private const val ARBITER_PROVIDER = "arbiter-ceiling"

    /**
     * مرشّحا المسار لهذا الهدف. ترتيب الأمان هو ما يقرّره المخطِّط بالنقل (`PLATFORM_HINT` قبل
     * `ARBITER_SYSFS`)، والأولوية هنا تفصل داخل النقل نفسه فقط.
     */
    fun candidates(target: AtlasControlTarget, pressure: ThermalGuard.Pressure): List<AtlasRouteCandidate> {
        val platformReadable = pressure != ThermalGuard.Pressure.UNKNOWN
        return listOf(
            AtlasRouteCandidate(
                id = PLATFORM_ROUTE_ID,
                priority = 0,
                evidence = AtlasRouteEvidence(
                    providerId = PLATFORM_PROVIDER,
                    transport = AtlasControlTransport.PLATFORM_HINT,
                    target = target,
                    readable = platformReadable,
                    privilegeAvailable = true,
                    unitProven = true,
                    baselineReadable = true,
                    rollbackProven = true,
                    reviewed = true,
                    reason = "thermal-status-${pressure.name.lowercase()}",
                ),
            ),
            AtlasRouteCandidate(
                id = STATIC_ROUTE_ID,
                priority = 1,
                evidence = AtlasRouteEvidence(
                    providerId = ARBITER_PROVIDER,
                    transport = AtlasControlTransport.ARBITER_SYSFS,
                    target = target,
                    readable = true,
                    privilegeAvailable = true,
                    unitProven = true,
                    baselineReadable = true,
                    rollbackProven = true,
                    reviewed = true,
                    reason = "user-ceiling",
                ),
            ),
        )
    }
}

/**
 * يخطّط ويُنفّذ تعديل سقف واحد داخل حدود نيّة المستخدم — عبر Atlas لا عبر كتابة خاصة.
 *
 * و`apply` هي المدخل الوحيد: تُنادى لكل مقبض مملوك (سقف GPU، وسقوف CPU لكل سياسة)، فتُبنى
 * المعاملة من السجل، ويختار Atlas المسار، ويُثبَّت الناتج في النيّة المنشورة.
 */
class ThermalCeilingRouter(
    private val registry: PerAppControlRegistry,
    private val adaptive: AtlasAdaptiveExecutor,
) {

    /**
     * @param acted هل جُرّب مسار فعلًا؟ `false` تعني «لا شيء يحتاج فعلًا» (لا تغيير مطلوب أو مقبض
     *   غير مملوك) — وليست فشلًا، ولا يجوز أن تُسجَّل كعطل.
     */
    data class Outcome(
        val acted: Boolean,
        val verified: Boolean,
        val routeId: String?,
        val desired: String?,
        val previous: String?,
        val reason: String,
        val fallbackStopped: Boolean = false,
        /**
         * حكم المخطِّط (`ELIGIBLE`/`BLOCKED`/`UNSUPPORTED`/`REVIEW_REQUIRED`) — وسبب رفضه إن رفض.
         *
         * ووجوده هنا لأن **قرار المسار كان صامتًا**: حين لا يُختار مسار كان يُسجّل «فشل» بلا
         * ذكر السبب، والفرق بين «كل المسارات محجورة» و«الهدف غير قابل للقياس» فرق يُبنى عليه
         * إصلاح، وكلاهما كان يُقرأ سطرًا واحدًا.
         */
        val decision: String = "",
        /** مسارات لم تُجرَّب وسبب كل واحد (رمز ثابت من Atlas، لا جملة). */
        val skipped: List<Pair<String, String>> = emptyList(),
    )

    fun apply(
        key: String,
        target: AtlasControlTarget,
        packageName: String?,
        userCeiling: String,
        ladder: List<Long>,
        pressure: ThermalGuard.Pressure,
    ): Outcome {
        val previous = registry.ownedDesired()[key] ?: return Outcome(false, false, null, null, null, "knob-not-owned")
        val platformEligible = pressure != ThermalGuard.Pressure.UNKNOWN

        val guarded = guardedCeiling(target, userCeiling, previous, ladder, pressure)
        // `if/else` بين قوسين قبل `?:`: بلا القوسين يُربط `?:` بالفرع الأخير وحده، فيصير
        // معنى الشرط مختلفًا عمّا قُصد.
        val planned = (if (platformEligible) guarded else userCeiling)
            ?: return Outcome(false, false, null, null, previous, "ceiling-not-planable")

        if (planned == previous) {
            // السقف المطلوب هو السقف القائم: لا معاملة تُبنى، ولا سطر عطل يُكتب. الهدوء هنا مقصود —
            // سطر «فشل» لقيمة لم تتغيّر هو الضجيج الذي يجعل السجل غير مقروء.
            val reason = if (platformEligible) "ceiling-already-held" else "user-ceiling-already-held"
            return Outcome(false, false, null, planned, previous, reason)
        }

        val valueByRoute = linkedMapOf(
            ThermalCeilingRoutes.PLATFORM_ROUTE_ID to guarded,
            ThermalCeilingRoutes.STATIC_ROUTE_ID to userCeiling,
        )
        val bindings = ThermalCeilingRoutes.candidates(target, pressure).mapNotNull { candidate ->
            val value = valueByRoute[candidate.id] ?: return@mapNotNull null
            registry.retargetRequest(key, value)?.let { request -> AtlasRouteBinding(candidate, request) }
        }
        if (bindings.isEmpty()) return Outcome(false, false, null, planned, previous, "no-route-transaction")

        val intent = AtlasControlIntent(
            target = target,
            goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
            desired = userCeiling,
            packageName = packageName?.takeIf(::isPlausiblePackage),
        )
        // `measuredGoalAvailable` يبقى `true` عن قصد، ولا يُربط بقراءة إشارة المنصة.
        //
        // معناها في المخطِّط: "هل الهدف **قابل للقياس**؟" — وهي ترفض كل مسار غير `PLATFORM_HINT`
        // حين تكون `false`. وربطها بغياب الإشارة كان يجعل **المسارين مرفوضَين معًا** عند ضغط مجهول:
        // المنصة غير مقروءة، وسقف المستخدم يُرفض بـ`GOAL_UNMEASURABLE` لأن نقله `ARBITER_SYSFS`.
        // فتصير النتيجة «لا مسار» وسقف المستخدم لا يُنفَّذ — وهو عكس ما وُجد الربط من أجله.
        //
        // والقياس هنا حقيقي لا مُدَّعى: هدف سقف المستخدم يُقاس بقراءة مرتجعة عبر نفس المُحكِّم
        // ونفس `verify`، وحكمه في [HardwareVerification]. أما أهليّة مسار المنصة فتحملها
        // `readable` وحدها: منصة لا تُجيب ⇒ مسارها غير مقروء ⇒ يُرفض، ويبقى سقف المستخدم.
        val result = adaptive.execute(
            intent = intent,
            bindings = bindings,
        )

        val chosen = result.selectedRouteId?.let(valueByRoute::get)
        val failure = result.attempts.lastOrNull()?.error ?: result.detail ?: "route-not-verified"
        if (chosen != null) {
            registry.commitRetarget(key, chosen, successful = result.successful, error = failure)
        } else {
            // لم يُختَر مسار: النيّة تبقى كما كانت (لا يُسقط الحارس إعدادًا بشريًّا) ويُسجَّل السبب.
            registry.commitRetarget(key, previous, successful = false, error = failure)
        }

        return Outcome(
            acted = true,
            verified = result.successful,
            routeId = result.selectedRouteId,
            desired = chosen ?: planned,
            previous = previous,
            reason = when {
                result.successful -> "route-verified"
                result.fallbackStopped -> "rollback-not-verified"
                else -> failure
            },
            fallbackStopped = result.fallbackStopped,
            decision = result.decision.status.name.lowercase() +
                "-" + (result.decision.reason?.name?.lowercase() ?: "selected"),
            skipped = result.skipped,
        )
    }

    /**
     * اسم حزمة صالح لسياق الملكية — يُرفض النصّ المشوَّه بدل أن يرمي باني الهدف استثناءً.
     *
     * وسبب الفحص هنا لا في المستدعي: `lastAppliedPkg` يأتي من قراءة التطبيق في المقدّمة، وقد
     * يكون نصًّا غير اسم حزمة في ROM شاذّ. والهدف يحمل الحزمة **سياقًا للملكية لا مفتاحًا للعتاد**،
     * فغيابها مقبول، ورمي استثناء من أجلها لا.
     */
    private fun isPlausiblePackage(value: String): Boolean =
        value.length in 3..255 &&
            value.first().isLetter() &&
            value.contains('.') &&
            !value.startsWith('.') &&
            !value.endsWith('.') &&
            !value.contains("..") &&
            value.all { it.isLetterOrDigit() || it == '.' || it == '_' }

    /**
     * القيمة التي يقترحها الحارس لهذا الهدف — الشكل فقط يختلف بين سقف مفرد ومدى `min:max`.
     *
     * و`null` تعني «لا يمكن التخطيط لطلب بهذا الشكل» (نصّ غير رقمي)، ولا تعني «لا خنق».
     */
    private fun guardedCeiling(
        target: AtlasControlTarget,
        userCeiling: String,
        previous: String,
        ladder: List<Long>,
        pressure: ThermalGuard.Pressure,
    ): String? = when (target) {
        AtlasControlTarget.GPU_FREQUENCY -> userCeiling.toLongOrNull()?.let { wanted ->
            ThermalGuard.nextCeiling(wanted, previous.toLongOrNull(), ladder, pressure).toString()
        }

        AtlasControlTarget.CPU_FREQUENCY ->
            ThermalGuard.nextRangeCeiling(userCeiling, previous, ladder, pressure)

        else -> null
    }
}
