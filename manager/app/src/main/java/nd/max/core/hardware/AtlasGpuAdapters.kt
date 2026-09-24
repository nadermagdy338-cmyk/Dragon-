package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteEvidence
import nd.max.core.atlas.AtlasSafetyPolicy
import nd.max.core.atlas.AtlasSafetyVerdict

/**
 * ثقب معاملة سقف GPU — القراءة والكتابة وخط الأساس. قرين [AtlasCeilingAccess] للجهاز الرسوميّ.
 *
 * **وحدات الأرقام مقصودة:** كل رقم في هذا العقد (وكل نصّ معاملة يمرّ منه) بوحدة **العقدة نفسها**
 * كما تحفظها [GpuHardwareBackend.Device] وتُقرأ من سلّم المقبض — فلا تحويل عند مستدعي ولا قيمة
 * مُختلِطة الوحدات. غير ذلك توكن [GpuCeilingPolicy.CeilingReading] الذي حقوله **Hz** بصيغته
 * المُقاسة الوحيدة، يُفسَّر في موضع واحد ([GpuCeilingContracts]) لا في موضعين.
 *
 * وقراءتان لأن عقدي السقف والتثبيت يقيسان شيئين مختلفين: عقد السقف يقرأ **سقف العقدة** (توكن)،
 * وعقد التثبيت يقرأ **التردد الجاري** (رقم) — لأن تثبيت درجة يُقاس بالساعة لا بـ`max_freq`:
 * جهاز مُثبَّت يقرأ `max_freq` عند القدرة بينما هو عالق على درجة واحدة (العطب المقيس)، وخلط
 * القراءتين هو ما يُنتج «مُلبّى» كاذبًا.
 */
interface AtlasGpuCeilingAccess {

    /** هل الكاتب المُتحقَّق (`GpuHardwareBackend`) متاح على هذا الجهاز الآن؟ (إشارة تُقاس) */
    val privileged: Boolean

    /** سقف العقدة بصيغة توكن [GpuCeilingPolicy.CeilingReading]؛ `null` = لا قياس. */
    fun readCeiling(devicePath: String): String?

    /** التردد الجاري بوحدة العقدة؛ `null` = لا قياس (لا «صفر» ولا رقم مُخترع). */
    fun readClock(devicePath: String): String?

    /** سقف بمدى عبر الكاتب المُتحقَّق القائم؛ `false` = لم تُثبت الكتابة. */
    fun writeCeiling(devicePath: String, ceilingNode: String): Boolean

    /** تثبيت درجة واحدة (فهرس OPP حيث لا يقبل المدى)؛ `false` = لم يثبت التثبيت. */
    fun pinFrequency(devicePath: String, frequencyNode: String): Boolean

    /** استرجاع سقف العقدة كما قُرئ (صيغة توكن). */
    fun restoreCeiling(devicePath: String, baselineToken: String): Boolean

    /**
     * استرجاع حالة التردد كما قُرئت: **تحرير القفل** وإعادة التحجيم الديناميكي — لأن خط الأساس
     * قبل التثبيت هو «بلا قفل» لا درجةٌ مسمّاة؛ وما كان مثبَّتًا من جلسة أخرى يعيده الكاتب من
     * خط أساسه الداخلي ([GpuHardwareBackend.Baseline.fixedIndex]).
     */
    fun restoreClock(devicePath: String, baselineNode: String): Boolean
}

/**
 * منفِّذ الثقب الإنتاجي — **لا كاتب ثاني**: كل كتابة تفوّض إلى `GpuHardwareBackend` نفسه
 * ([GpuHardwareBackend.applyValidated]، الخيط المعدّ لهذا بالضبط: «caller must own the control
 * transaction»). فالاختلاف عن بقية المقابض في **اختيار الطريقة والحكم** فقط ([GpuCeilingContracts])،
 * لا في مسار الكتابة. والاستثناء الوحيد المعلن: **التحرير** ([GpuHardwareBackend.releaseVendorCeiling])
 * عند القدرة وفوق السقف المقرؤة — فعلٌ يملكه هذا الكاتب ككل كاتب قائم، ولا يُنفَّذ من داخل
 * `apply` إلا إذا صاحبته كتابة، فيتخطّاه الحكم «مُلبّى» فلا يقع قط.
 */
object SystemGpuCeilingAccess : AtlasGpuCeilingAccess {

    private val io: GpuHardwareBackend.Io get() = GpuHardwareBackend.SystemIo

    /**
     * «معاملة قابلة للمحاولة» بالمعنى نفسه المستعمل في نظير CPU ([SystemCeilingAccess]):
     * جهازٌ مكتشف يمرّ بالكاتب المُتحقَّق — **لا قراءة تُثبت صلاحية الكتابة**، والحكم النهائي عند
     * الكاتب نفسه، وقبولها الكاذب لا يُنتج ادّعاء نجاح لأن `false` تُسقط المعاملة فيُعلن الفشل.
     */
    override val privileged: Boolean get() = GpuHardwareBackend.selection().device != null

    override fun readCeiling(devicePath: String): String? =
        GpuHardwareBackend.refresh(devicePath, io)?.let { live ->
            GpuHardwareBackend.ceilingReading(live, io).token
        }

    override fun readClock(devicePath: String): String? =
        GpuHardwareBackend.refresh(devicePath, io)?.currentFreq?.toString()

    override fun writeCeiling(devicePath: String, ceilingNode: String): Boolean {
        val target = ceilingNode.toLongOrNull() ?: return false
        val live = GpuHardwareBackend.refresh(devicePath, io) ?: return false
        if (!live.unitTrusted) return false
        val unit = live.frequencyUnit.hzMultiplier
        val requestedHz = target * unit
        val capabilityHz = live.provenMaxFreq?.times(unit)
        // الطلب عند القدرة **هو** التحرير نفسه: يُنفَّذ بلا كتابة تردد ولا تثبيت
        // ([GpuHardwareBackend.releaseVendorCeiling]) — وإلا حُكم «مُلبّى» فتخطّى كل شيء ولم
        // يُحرَّر شيء قط، ثم أعاد استرجاعُ خط الأساس محوَ التحرير: «Performance» بلا أثر مقيس.
        if (GpuCeilingPolicy.atCapability(requestedHz, capabilityHz)) {
            return GpuHardwareBackend.releaseVendorCeiling(io)
        }
        val request = GpuCeilingContracts.requestFor(live, target) ?: return false
        // ورفعٌ فوق السقف المقرؤة يُحرَّر أولًا — القرار نفسه في كل كاتب قائم
        // (`releaseRequiredForRetarget`)؛ وإلا قُمعت الكتابة فقُرئ `differs` بلا سبب ظاهر
        // (المقيس على rodin: 1300000000 ← 754000000).
        if (GpuCeilingPolicy.releaseRequiredForRetarget(
                requestedHz = requestedHz,
                liveCeilingHz = GpuHardwareBackend.nodeCeilingHz(live),
                // لا نيّة تحرير مسبقة هنا: القرار **يُقاس** (الطلب فوق السقف الحيّ) لا يُخطَّط.
                plannedRelease = false,
            )
        ) {
            GpuHardwareBackend.releaseVendorCeiling(io)
        }
        return GpuHardwareBackend.applyValidated(live, request, io).verified
    }

    override fun pinFrequency(devicePath: String, frequencyNode: String): Boolean {
        val target = frequencyNode.toLongOrNull() ?: return false
        val live = GpuHardwareBackend.refresh(devicePath, io) ?: return false
        val request = GpuHardwareBackend.Request(minFreq = target, maxFreq = target)
        return GpuHardwareBackend.applyValidated(live, request, io).verified
    }

    override fun restoreCeiling(devicePath: String, baselineToken: String): Boolean {
        val reading = GpuCeilingPolicy.CeilingReading.parse(baselineToken) ?: return false
        val ceilingHz = reading.nodeCeilingHz ?: return false
        val live = GpuHardwareBackend.refresh(devicePath, io) ?: return false
        if (!live.unitTrusted) return false
        return writeCeiling(devicePath, (ceilingHz / live.frequencyUnit.hzMultiplier).toString())
    }

    override fun restoreClock(devicePath: String, baselineNode: String): Boolean {
        val live = GpuHardwareBackend.refresh(devicePath, io) ?: return false
        return GpuHardwareBackend
            .applyValidated(live, GpuHardwareBackend.Request(releaseLock = true), io)
            .verified
    }
}

/**
 * مُلاءِم سقف GPU حيث يقبل الجهاز **كتابة مدى** (`min_freq`/`max_freq` على devfreq) — الطريقة
 * الأولى في سلّم [GpuCeilingPolicy.realize] بعد التحرير، لأن السقف يُقاس بعدم التجاوز لا بالتجميد.
 *
 * والجهاز ذاته قد يملك مسار تثبيت OPP إلى جانبه — ووجودُه لا يغيّر اختيار السقف: العطب المقيس
 * (rodin · MT6899) كان خلطَ الاثنين فصار كل بروفايل **تثبيتًا** يجمّد التردد، و«أداء» يعطي ٦٥٠
 * عالقًا. والتثبيت يبقى لجهاز لا يقبل المدى أصلًا ([GpuOppPinCeilingAdapter]).
 */
class GpuDevfreqCeilingAdapter : AtlasControlAdapter {
    override val id: String = "gpu-devfreq-ceiling"
    override val target: AtlasControlTarget = AtlasControlTarget.GPU_FREQUENCY
    override val transport: AtlasControlTransport = AtlasControlTransport.ARBITER_SYSFS

    override fun assess(context: AtlasAdapterContext, request: AtlasControlRequest?): AtlasAdapterAssessment {
        val device = context.gpuDevice
            ?: return AtlasAdapterAssessment.NotApplicable(SKIP_NO_GPU)
        if (!device.unitTrusted) {
            return AtlasAdapterAssessment.NotApplicable(SKIP_UNIT_AMBIGUOUS)
        }
        // `devfreqCeilingWritable` يقيس الكتابة والسلّم معًا — فشل مغلق في خاصية واحدة.
        if (!device.devfreqCeilingWritable) {
            return AtlasAdapterAssessment.NotApplicable("devfreq-ceiling-not-writable")
        }
        return AtlasAdapterAssessment.Applicable(id, "devfreq=${device.path}; ladder=${device.frequencies.size}")
    }

    override fun plan(context: AtlasAdapterContext, request: AtlasControlRequest): AtlasAdapterPlan =
        gpuCeilingPlan(adapterId = id, pin = false, context = context, ceilings = request as? AtlasControlRequest.GpuCeilings)

    override fun probe(context: AtlasAdapterContext): AtlasAdapterPlan =
        gpuCeilingPlan(adapterId = id, pin = false, context = context, ceilings = null)
}

/**
 * مُلاءِم **تثبيت درجة OPP** — الطريقة البديلة حيث لا تقبل عقدتا المدى كتابة سقف أصلًا (فهرس
 * `fix_target_opp_index` على MediaTek): أداةٌ تعرف جهازًا واحدًا كانت تكتب نفس المسار للجميع،
 * وهذا هو «MediaTek ≠ MediaTek على كيرنل آخر»: التثبيت يبقى محسومًا بقياس (`mtkFixedIndexPath`
 * مكتوب ومُقاس + جدول فهارس) لا باسم الشركة المصنِّعة.
 *
 * وحكمه أضعف من عقد المدى بحكمٍ صريح: النجاح هو **الدرجة المثبَّتة على الساعة** ([GpuCeilingContracts.pinHolds])
 * — لا `max_freq` الذي يبقى عند القدرة على جهاز مُثبَّت.
 */
class GpuOppPinCeilingAdapter : AtlasControlAdapter {
    override val id: String = "gpu-opp-pin-ceiling"
    override val target: AtlasControlTarget = AtlasControlTarget.GPU_FREQUENCY
    override val transport: AtlasControlTransport = AtlasControlTransport.ARBITER_SYSFS

    override fun assess(context: AtlasAdapterContext, request: AtlasControlRequest?): AtlasAdapterAssessment {
        val device = context.gpuDevice
            ?: return AtlasAdapterAssessment.NotApplicable(SKIP_NO_GPU)
        if (!device.unitTrusted) {
            return AtlasAdapterAssessment.NotApplicable(SKIP_UNIT_AMBIGUOUS)
        }
        // التثبيت أسوأ طريقة لطلب سقف؛ يبقى لجهاز لا يقبل المدى — لا يُقدَّم على كتابة السقف أبدًا.
        if (device.devfreqCeilingWritable) {
            return AtlasAdapterAssessment.NotApplicable("devfreq-ceiling-preferred")
        }
        if (device.mtkFixedIndexPath == null || !device.exactLockWritable || device.mtkOppIndexByFrequency.isEmpty()) {
            return AtlasAdapterAssessment.NotApplicable("opp-pin-not-writable")
        }
        return AtlasAdapterAssessment.Applicable(id, "opp-pin=${device.mtkFixedIndexPath}; steps=${device.mtkOppIndexByFrequency.size}")
    }

    override fun plan(context: AtlasAdapterContext, request: AtlasControlRequest): AtlasAdapterPlan =
        gpuCeilingPlan(adapterId = id, pin = true, context = context, ceilings = request as? AtlasControlRequest.GpuCeilings)

    override fun probe(context: AtlasAdapterContext): AtlasAdapterPlan =
        gpuCeilingPlan(adapterId = id, pin = true, context = context, ceilings = null)
}

/**
 * بناء مسار GPU واحد — مشترك بين الملاءِمَين، والفرق صيغةُ العقد وحدها (سقفٌ بمدى أو تثبيت درجة).
 *
 * الترتيب مُلزم: قواعد عدم اللمس أولًا (عقدة ممنوعة ⇒ `never-touch:<rule>` بلا بناء)، ثم الوحدة
 * المُوثوقة، ثم القصّ إلى سلّم OPP المُعلن — **لا قيمة مُخترعة** فوقه ولا دونه: طلبٌ دون أدنى
 * درجة مُعلنة لا مسار له (الجهاز لا يقبل) بدل أن يُكتب رقمٌ لا وجود له.
 */
private fun gpuCeilingPlan(
    adapterId: String,
    pin: Boolean,
    context: AtlasAdapterContext,
    ceilings: AtlasControlRequest.GpuCeilings?,
    probeNode: Long? = null,
): AtlasAdapterPlan {
    val device = context.gpuDevice
        ?: return AtlasAdapterPlan(adapterId, emptyList(), listOf(SKIP_GPU_KEY to SKIP_NO_GPU))
    val key = HardwareControlKey.gpuFrequency(device.name)
    val access = context.gpuAccess
        ?: return AtlasAdapterPlan(adapterId, emptyList(), listOf(key to SKIP_NO_SEAM))

    // قواعد عدم اللمس أولًا — باسم الجهاز ومساره معًا (فشل مغلق: أيهما وقع منع).
    val safety = AtlasSafetyPolicy.verdictFor(name = device.name, path = device.path)
    if (safety is AtlasSafetyVerdict.Denied) {
        return AtlasAdapterPlan(adapterId, emptyList(), listOf(key to "never-touch:${safety.ruleId}"))
    }

    val requestedNode = ceilings?.ceilingByKnob?.get(device.name) ?: probeNode
    if (requestedNode == null || requestedNode <= 0L) {
        return AtlasAdapterPlan(adapterId, emptyList(), listOf(key to SKIP_NO_REQUEST))
    }
    if (!device.unitTrusted) {
        return AtlasAdapterPlan(adapterId, emptyList(), listOf(key to SKIP_UNIT_AMBIGUOUS))
    }
    val ladder = device.frequencies.filter { it > 0L }.sorted()
    val snapped = ladder.lastOrNull { it <= requestedNode }
        ?: return AtlasAdapterPlan(adapterId, emptyList(), listOf(key to SKIP_NO_PROVEN_RANGE))

    val unit = device.frequencyUnit.hzMultiplier
    val capabilityNode = device.provenMaxFreq
    val routeId = HardwareRepairExecutor.labelFor(key)
    val desired = snapped.toString()
    val baseline = runCatching {
        if (pin) access.readClock(device.path) else access.readCeiling(device.path)
    }.getOrNull()

    val request = HardwareRepairRequest(
        routeId = routeId,
        key = key,
        owner = ceilings?.owner ?: ControlOwnership.Owner.MAX_AI,
        token = ceilings?.token ?: PROBE_TOKEN,
        desired = desired,
        apply = { value ->
            if (pin) access.pinFrequency(device.path, value) else access.writeCeiling(device.path, value)
        },
        read = { if (pin) access.readClock(device.path) else access.readCeiling(device.path) },
        restore = { value ->
            if (pin) access.restoreClock(device.path, value) else access.restoreCeiling(device.path, value)
        },
        baseline = baseline,
        // حكما العقد كما في كل كاتب لهذا المقبض: «لا تتجاوز» حكمٌ ضعيف يكفي للنجاح، و«بلغت
        // القيمة أو تحرّر السقف» حكمٌ قوي يُثبته الواقع — لا يُخلط أحدهما بالآخر.
        verify = { value, actual ->
            if (pin) {
                GpuCeilingContracts.pinHolds(value, actual, unit)
            } else {
                GpuCeilingContracts.contained(value, actual, unit)
            }
        },
        realized = { value, actual ->
            if (pin) {
                GpuCeilingContracts.pinMeasured(value, actual, unit)
            } else {
                GpuCeilingContracts.reachedOrReleased(value, actual, unit, capabilityNode)
            }
        },
        stabilitySamples = 3,
        stabilityIntervalMs = 40L,
    )

    val evidence = AtlasRouteEvidence(
        providerId = "gpu-hardware-backend",
        transport = AtlasControlTransport.ARBITER_SYSFS,
        target = AtlasControlTarget.GPU_FREQUENCY,
        readable = device.currentFreq != null || device.maxFreq != null,
        privilegeAvailable = access.privileged,
        unitProven = ladder.size >= 2,
        baselineReadable = baseline != null,
        rollbackProven = baseline != null && access.privileged,
        reviewed = ceilings?.reviewed?.invoke(device.name)
            ?: AtlasDiscoveredControl.isReviewedControlRoute(key),
        reason = buildString {
            append("gpu; method=").append(if (pin) "opp-pin" else "devfreq-ceiling")
            append("; ladder=").append(ladder.size)
            append("; clock=").append(device.currentFreq ?: "unread")
            append("; baseline=").append(if (baseline != null) "read" else "unread")
        },
    )
    val candidate = AtlasRouteCandidate(id = routeId, evidence = evidence, priority = 0)
    return AtlasAdapterPlan(adapterId, listOf(AtlasRouteBinding(candidate, request)), emptyList())
}

private const val SKIP_GPU_KEY: String = "gpu-ceiling"
private const val SKIP_NO_GPU: String = "no-gpu-device-seen"
private const val SKIP_NO_SEAM: String = "no-write-seam-available"
private const val SKIP_NO_REQUEST: String = "no-requested-value"
private const val SKIP_NO_PROVEN_RANGE: String = "no-proven-range"
private const val SKIP_UNIT_AMBIGUOUS: String = "frequency-unit-ambiguous"

/** معرّف التمثيل — لا ملكية فيه ولا يُنفَّذ أبدًا. */
private const val PROBE_TOKEN: String = "atlas-map-probe"

/**
 * عقود حكم GPU — والمفصول هنا بالضبط ما يقيسه كل عقد. كل دالة **خالصة**: لا تقرأ عقدة ولا تكتب.
 *
 * الوحدة: الأرقام المطلوبة بوحدة العقدة ([GpuHardwareBackend.Device.frequencyUnit])، و`hzMultiplier`
 * هو المُحوِّل الوحيد إلى لغة [GpuCeilingPolicy] (Hz) — فلا تُقارَن أرقامٌ بوحدتين في موضعٍ واحد.
 */
object GpuCeilingContracts {

    /**
     * شكل الكتابة على هذا الجهاز — القرار واحد: [GpuCeilingPolicy.realize] (التحرير عند القدرة،
     * ثم المدى، ثم التثبيت، ثم عدم الدعم). `null` = لا طريقة مسموحة هنا (وحدة مجهولة، أو طلبٌ
     * دون أدنى درجة مُعلنة) — لا طلبٌ مُخترع.
     */
    fun requestFor(device: GpuHardwareBackend.Device, requestedNode: Long): GpuHardwareBackend.Request? {
        if (requestedNode <= 0L || !device.unitTrusted) return null
        val ladder = device.frequencies.filter { it > 0L }.sorted()
        if (ladder.isEmpty()) return null
        val snapped = ladder.lastOrNull { it <= requestedNode } ?: return null
        val realization = GpuCeilingPolicy.realize(
            requestedHz = snapped * device.frequencyUnit.hzMultiplier,
            advertisedMaxHz = device.provenMaxFreq?.times(device.frequencyUnit.hzMultiplier),
            rangeWritable = device.devfreqCeilingWritable,
            pinAvailable = device.mtkFixedIndexPath != null && device.exactLockWritable,
        )
        return when (realization) {
            // الطلب عند القدرة **هو** التحرير: يُكتب أعلى درجة مُعلنة مع تحرير سلطة المصنِّع
            // (الافتراضي في `Request`)، ولا يُثبَّت شيء.
            GpuCeilingPolicy.Realization.RELEASE_ONLY,
            GpuCeilingPolicy.Realization.RANGE -> GpuHardwareBackend.Request(
                minFreq = ladder.first(),
                maxFreq = snapped,
            )

            GpuCeilingPolicy.Realization.PIN -> GpuHardwareBackend.Request(
                minFreq = snapped,
                maxFreq = snapped,
            )

            GpuCeilingPolicy.Realization.UNSUPPORTED -> null
        }
    }

    /** عقد **السقف**: لا تتجاوز الطلب — وبلا قفلٍ مُثبِّت (التجميد ليس سقفًا). */
    fun contained(requestedNode: String?, actual: String?, hzMultiplier: Long): Boolean {
        val target = targetHz(requestedNode, hzMultiplier) ?: return false
        val reading = GpuCeilingPolicy.CeilingReading.parse(actual) ?: return false
        if (reading.lockActive == true) return false
        val node = reading.nodeCeilingHz ?: return false
        return node <= target
    }

    /**
     * عقد **البلغ**: السقف صار الطلب **فعلًا** — لا «يحتويه». والفارق ليس لفظيًّا: هذا هو الحكم
     * الذي يقرّر **تخطّي الكتابة** ([HardwareRepairRequest.realized])، و`ceilingSatisfied` وحدها
     * معناها `node ≤ desired` فقراءةٌ أدنى من الطلب (٢٢٠ لطلب ٤٤٢) تُقرأ «مُلبّاة» فتُتخطّى
     * الكتابة فلا تُكتب قيمة أعلى أبدًا — وهي حرفًا العطب الذي وُضع له `HardwareVerification.ceilingReached`
     * في مقبض CPU: «طلب رفع سقف لا يكفي فيه «دون السقف» دليلًا».
     *
     * والاستثناء الوحيد: عند قدرة الجهاز **التحرير** هو النجاح ([GpuCeilingPolicy.releaseVerdict]
     * — حكمٌ ثانٍ لأن التحرير فعلٌ نملكه وبلوغ القدرة حكمُ منصّة)؛ وما تحتفظ به المنصّة قياسٌ
     * يُعلن (`OPEN_BELOW_REQUEST:<رقم>`) لا فشلٌ يُعاد به الجهاز إلى ما كان عليه.
     */
    fun reachedOrReleased(
        requestedNode: String?,
        actual: String?,
        hzMultiplier: Long,
        capabilityNode: Long?,
    ): Boolean {
        val target = targetHz(requestedNode, hzMultiplier) ?: return false
        val reading = GpuCeilingPolicy.CeilingReading.parse(actual) ?: return false
        val capabilityHz = capabilityNode?.takeIf { it > 0L && hzMultiplier > 0L }?.times(hzMultiplier)
        if (capabilityHz != null && GpuCeilingPolicy.atCapability(target, capabilityHz)) {
            return GpuCeilingPolicy.releaseVerdict(reading, target).satisfied
        }
        return reading.nodeCeilingHz == target && GpuCeilingPolicy.ceilingSatisfied(target, reading)
    }

    /** عقد **التثبيت**: الدرجة المثبَّتة لا تخالف الطلب — بالساعة إن قُرئ، وبصدى الفهرس إن لم يُقرأ. */
    fun pinHolds(requestedNode: String?, clockNode: String?, hzMultiplier: Long): Boolean {
        val target = targetHz(requestedNode, hzMultiplier) ?: return false
        val measured = clockNode?.toLongOrNull()?.takeIf { hzMultiplier > 0L }?.times(hzMultiplier)
        return GpuCeilingPolicy.pinVerdict(target, measured) != GpuCeilingPolicy.PinVerdict.CLOCK_MISMATCH
    }

    /** حكمٌ قوي للتثبيت: الساعة نفسها ساوت المثبَّت — لا صدى فهرس وحده. */
    fun pinMeasured(requestedNode: String?, clockNode: String?, hzMultiplier: Long): Boolean {
        val target = targetHz(requestedNode, hzMultiplier) ?: return false
        val measured = clockNode?.toLongOrNull()?.takeIf { hzMultiplier > 0L }?.times(hzMultiplier)
        return GpuCeilingPolicy.pinVerdict(target, measured) == GpuCeilingPolicy.PinVerdict.VERIFIED
    }

    /** الطلب بوحدة العقدة ← Hz؛ وحدة غير موثوقة أو رقم غير صالح = `null` (فشل مغلق، لا صفر). */
    private fun targetHz(requestedNode: String?, hzMultiplier: Long): Long? =
        requestedNode?.toLongOrNull()
            ?.takeIf { it > 0L && hzMultiplier > 0L }
            ?.times(hzMultiplier)
}
