package nd.max.core.hardware

/**
 * Per-app policy adapter. The arbiter is the only ownership publisher and the
 * only path allowed to repair drift or restore a baseline.
 *
 * The gate is injected, never constructed here: two arbiter instances inside one
 * process would each keep their own request table, so an intent created by one
 * would look ownerless to the other and be reported as a foreign preemption.
 *
 * Every write goes through [HardwareRepairExecutor], which is the single implementation of
 * "apply, confirm for a bounded window, restore on failure" in the app. This class used to carry a
 * second copy of that logic inline, so a fix to one did not reach the other; now there is one.
 */
class PerAppControlRegistry(
    private val mutationGate: HardwareControlArbiter,
    private val token: String = "per-app",
    private val confirmationSamples: Int = 3,
    private val confirmationIntervalMs: Long = 40L,
    sleep: (Long) -> Unit = { delay -> if (delay > 0L) Thread.sleep(delay) },
) {
    private val executor = HardwareRepairExecutor(mutationGate, sleep)

    init {
        require(confirmationSamples in 1..8) { "confirmationSamples must stay bounded" }
        require(confirmationIntervalMs in 0L..500L) { "confirmationIntervalMs must stay bounded" }
    }

    data class Entry(
        val key: String,
        var desired: String,
        val apply: (String) -> Boolean,
        val read: () -> String?,
        val baseline: String? = null,
        val restore: ((String) -> Boolean)? = null,
        /**
         * حكم تلبية الطلب لهذا المقبض — `null` يعني التساوي الحرفي.
         *
         * يُمرَّر كما هو إلى المُحكِّم وإلى نافذة التأكيد. سقوف GPU وأمدية CPU تحتاجه:
         * مقارنتها بالتساوي الحرفي تُصنّف تلبيةً حقيقية فشلًا (سياسة vendor أضيق، أو
         * إدماج الحاكم للسقف بقيمة مُعلنة أخرى)، ثم تسترجع خط الأساس بلا سبب مفهوم.
         */
        val verify: ((String, String?) -> Boolean)? = null,
        /**
         * هل القراءة الحيّة **دليل** على أن الطلب نُفِّذ؟ — `null` = «نعم».
         *
         * وموضعه هنا لا في المُحكِّم لأن المقبض هو من يعرف دلالة قيمته: مقبض سقفٍ نكتبه نحن
         * لا تُقرأ قيمته الأدنى «مُلبّاة» لطلبٍ أعلى ([HardwareVerification.ceilingReached]).
         */
        val realized: ((String, String?) -> Boolean)? = null,
    )

    data class RepairResult(
        val key: String,
        val requested: String,
        val actual: String?,
        val applied: Boolean,
        val verified: Boolean,
        val attempts: Int,
        val error: String? = null,
        /**
         * The live value did not match the intent *before* this pass attempted anything.
         *
         * This is the only honest signal for "a repair happened": the arbiter reports `applied = true`
         * both when it wrote the value and when it found the desired value already in place, so a caller
         * logging "repaired" off `applied` would log it every ten seconds for every healthy knob — and a
         * log that cries wolf is a log nobody reads. `null` means the value could not be read at all.
         */
        val driftedBefore: Boolean? = null,
    ) { val successful: Boolean get() = applied && verified }

    private val entries = linkedMapOf<String, Entry>()

    /**
     * Knobs the gate refused since [beginApp], with its own reason. A refused
     * knob never becomes an entry, so without this record a per-app rule blocked
     * by a manual lock or by safety would be reported as silence — and silence
     * reads as success.
     */
    private val refusals = linkedMapOf<String, String>()
    @Volatile private var currentToken = token

    @Synchronized fun beginApp(packageName: String) {
        releaseAll()
        refusals.clear()
        currentToken = "per-app:$packageName"
    }

    /** The gate's refusal reasons for the current app, keyed by control key. */
    @Synchronized fun refusalReasons(): Map<String, String> = refusals.toMap()

    @Synchronized fun ownGovernor(key: String, desired: String, apply: (String) -> Boolean, read: () -> String?, baseline: String? = null, restore: ((String) -> Boolean)? = null, verify: ((String, String?) -> Boolean)? = null, realized: ((String, String?) -> Boolean)? = null): Boolean =
        own(key, desired, apply, read, baseline, restore, verify, realized)

    @Synchronized fun ownValue(key: String, desired: String, apply: (String) -> Boolean, read: () -> String?, baseline: String? = null, restore: ((String) -> Boolean)? = null, verify: ((String, String?) -> Boolean)? = null, realized: ((String, String?) -> Boolean)? = null): Boolean =
        own(key, desired, apply, read, baseline, restore, verify, realized)

    @Synchronized private fun own(key: String, desired: String, apply: (String) -> Boolean, read: () -> String?, baseline: String?, restore: ((String) -> Boolean)?, verify: ((String, String?) -> Boolean)?, realized: ((String, String?) -> Boolean)?): Boolean {
        val entry = Entry(key, desired, apply, read, baseline, restore, verify, realized)
        val outcome = executor.execute(requestFor(entry))
        record(entry, outcome)
        return outcome.successful
    }

    /**
     * المقابض المُملوكة الآن والقيمة المطلوبة لكل منها — للقراءة والتشخيص والحارس الحراري.
     *
     * نسخة لا مرجع: من يقرأ لا يستطيع أن يعدّل طلبًا وهو يمرّ، فتبقى الأسبقية في مكان واحد.
     */
    @Synchronized fun ownedDesired(): Map<String, String> = entries.mapValues { it.value.desired }

    /**
     * أدلّة أهليّة المسار لهذا المقبض — **مقيسة الآن من هذه العقدة**، لا قيمًا حرفيّة.
     *
     * ولماذا تُقاس هنا: هذا السجل هو من يملك القارئ والكاتب، فمن يسأل عن الأهليّة لا يحقّ له أن
     * يخمّنها (ولا أن يقرأ العقدة بنفسه فيصير قارئان لعقدة واحدة). القراءة تُجرَّب مرّة واحدة،
     * ونتيجتها هي `readable` و`baselineReadable` معًا — فخط الأساس في كل معاملة من هذا السجل هو
     * قراءة حيّة، لا قيمة محفوظة.
     *
     * والمسجَّل غير المملوك يُعيد [RouteEvidenceFacts.UNMEASURED]: لا معاملة ⇒ لا أدلّة ⇒ المخطِّط
     * يرفض برمزه القياسي بدل أن يُخمَّن مسار لعقدة لا يملكها أحد.
     *
     * @param unitProven هل أثبت الجهاز وحدة هذا المقبض؟ يحملها المستدعي لأنه يعرف سلّم المقبض
     *   المُعلن (سياسة cpufreq أو جهاز devfreq)، والسجل لا يعرف شيئًا عن الوحدات.
     */
    @Synchronized fun routeFacts(key: String, unitProven: Boolean): RouteEvidenceFacts {
        val entry = entries[key] ?: return RouteEvidenceFacts.UNMEASURED
        val live = runCatching { entry.read() }.getOrNull()?.trim()?.takeIf(String::isNotEmpty)
        return RouteEvidenceFacts.of(
            liveReadable = live != null,
            // وهذا السجل لا يحمل إدخالًا بلا كاتب أصلًا (النوع يمنعه)، فالمعاملة قائمة بنيويًّا.
            transactionHeld = true,
            unitProven = unitProven,
        )
    }

    /**
     * إعادة استهداف مقبض مملوك بقيمة أخرى **دون** فقدان خط الأساس ولا نيّة المستخدم.
     *
     * الطلب يُنقل داخل حدود ما طلبه المستخدم نفسه، والمعاملة تمرّ من نفس المُحكِّم ونفس خط
     * الأساس، فيبقى الاسترجاع عند نهاية جلسة التطبيق صحيحًا (بصمة التوكِن نفسها ⇒ خط الأساس
     * الأصلي محفوظ).
     *
     * وعلى فشل إعادة الاستهداف: تُحفظ النيّة السابقة ويبقى المقبض مُسجّلًا — إعادة الاستهداف
     * قرار آليّ، ولا يجوز أن يُسقط هو نفسه إعدادًا اختاره المستخدم. ودورة الانحراف التالية
     * تُعيد تأكيد النيّة المحفوظة بلا مؤقّت جديد.
     *
     * **ومن له مخطِّط فليستعمل [retargetRequest] + [commitRetarget]:** هذه الدالة تنفّذ معاملة
     * واحدة بلا اختيار مسار ولا بديل ولا تذكّر. ومسار الحارس الحراري الإنتاجي يمرّ بالزوج
     * المفصول (`ThermalCeilingRouter` ⇒ `AtlasAdaptiveExecutor`)، فلا قراران لسلوك واحد.
     */
    @Synchronized fun retarget(key: String, desired: String): Boolean {
        val request = retargetRequest(key, desired) ?: return false
        val outcome = executor.execute(request)
        return commitRetarget(key, desired, outcome.successful, outcome.error)
    }

    /**
     * معاملة إعادة استهداف **جاهزة** لمقبض مملوك، بلا تنفيذ.
     *
     * ولماذا تُسلَّم بدل تنفيذها هنا: من يخطط (Atlas) يحتاج أن يرى المعاملة كاملة — المفتاح، وخط
     * الأساس، وحكم التلبية — ليقرّر أيّ مسار يُقدّم. والإغلاقات (`apply`/`read`/`restore`) لا تخرج
     * من هذا السجل أبدًا، فلا يستطيع مخطط أن يكتب في عقدة لم يعرفها السجل. فالفصل: السجل يملك
     * **الوصول**، والمخطط يملك **الاختيار**، والمُحكِّم يملك **التنفيذ**.
     *
     * ويُعاد `null` حين لا يكون المقبض مملوكًا: لا معاملة لمن لا يملك شيئًا.
     */
    @Synchronized fun retargetRequest(key: String, desired: String): HardwareRepairRequest? =
        entries[key]?.let { entry -> requestFor(entry, desired) }

    /**
     * يُثبّت نتيجة إعادة الاستهداف في النيّة المنشورة — أو يبقيها السابقة عند الفشل.
     *
     * وهذا هو نصف العقد الذي يجعل تنفيذ المخطط آمنًا: بعد أن ينفّذ Atlas المعاملة، يجب أن تُحدَّث
     * النيّة حتى لا تُعيد دورة الانحراف الطلب القديم في نفس الدقيقة.
     */
    @Synchronized fun commitRetarget(
        key: String,
        desired: String,
        successful: Boolean,
        error: String? = null,
    ): Boolean {
        val entry = entries[key] ?: return false
        return if (successful) {
            entry.desired = desired
            entries[key] = entry
            refusals.remove(key)
            true
        } else {
            entries[key] = entry
            refusals[key] = error ?: "retarget-not-verified"
            false
        }
    }

    @Synchronized fun release(key: String) {
        refusals.remove(key)
        if (entries.remove(key) != null) mutationGate.release(key, currentToken, restore = true)
    }

    @Synchronized fun releaseAll() {
        entries.keys.toList().asReversed().forEach { key ->
            mutationGate.release(key, currentToken, restore = true)
        }
        mutationGate.releaseToken(currentToken, restore = true)
        entries.clear()
    }

    /**
     * The bounded drift pass: re-verifies every registered knob and repairs the ones an external
     * writer took back.
     *
     * The iteration runs over a **snapshot** on purpose. [record] may drop a failed knob from
     * [entries], and removing an element of a `LinkedHashMap` while iterating its live values view
     * throws `ConcurrentModificationException` on the next element — so the earlier version aborted
     * the whole pass on the first knob a vendor daemon had reclaimed, and none of the knobs after it
     * were ever repaired. That is precisely the case this loop exists for.
     */
    @Synchronized fun verifyAndRepair(): List<RepairResult> = entries.entries.toList().map { (key, entry) ->
        val before = runCatching { entry.read() }.getOrNull()
        val outcome = executor.execute(requestFor(entry))
        record(entry, outcome)
        RepairResult(
            key = key,
            requested = entry.desired,
            actual = outcome.actual,
            applied = outcome.applied,
            verified = outcome.verified,
            attempts = if (outcome.applied) 1 else 0,
            error = outcome.error,
            driftedBefore = before?.let { it != entry.desired },
        )
    }

    private fun requestFor(entry: Entry, desired: String = entry.desired): HardwareRepairRequest = HardwareRepairRequest(
        routeId = HardwareRepairExecutor.labelFor(entry.key),
        key = entry.key,
        owner = ControlOwnership.Owner.PER_APP,
        token = currentToken,
        desired = desired,
        apply = entry.apply,
        read = entry.read,
        restore = entry.restore ?: entry.apply,
        baseline = entry.baseline,
        verify = entry.verify,
        realized = entry.realized,
        stabilitySamples = confirmationSamples,
        stabilityIntervalMs = confirmationIntervalMs,
    )

    /**
     * Records the intent according to what actually happened.
     *
     * A drift failure — the value was written, an external writer took it back inside the confirmation
     * window, and the baseline was restored — **keeps the entry registered**. This pass is the only
     * place that can notice and repair that class of failure, and dropping the entry on the way in
     * meant a per-app knob was silently lost for the rest of the app session the first time a vendor
     * daemon won a race: the user set a control, nothing changed, and no later pass tried again.
     *
     * A refusal (manual lock, foreign owner) or a hardware failure that could not even be rolled back
     * is *not* retried: those repeat identically forever, and quarantining them is what keeps a bounded
     * loop bounded.
     */
    private fun record(entry: Entry, outcome: HardwareRepairResult) {
        if (outcome.successful) {
            entries[entry.key] = entry
            refusals.remove(entry.key)
            return
        }
        outcome.error?.let { refusals[entry.key] = it }
        when (outcome.state) {
            HardwareRepairState.DRIFT_ROLLED_BACK -> entries[entry.key] = entry
            else -> entries.remove(entry.key)
        }
    }
}
