package nd.max.core.hardware

/**
 * Canonical GPU owner. A provider is controllable only when its identity,
 * frequency unit, advertised values, writable controls and restoration baseline
 * can all be proved from the running driver.
 */
object GpuHardwareBackend {
    private const val ROOT = "/sys/class/devfreq"
    private const val THERMAL_ROOT = "/sys/class/thermal"
    private val MTK_OPP_TABLES = listOf(
        "/proc/gpufreqv2/stack_signed_opp_table",
        "/proc/gpufreqv2/gpu_working_opp_table",
        "/proc/gpufreq/gpufreq_opp_dump",
    )
    private val MTK_LOCK_PATHS = listOf(
        "/proc/gpufreqv2/fix_target_opp_index",
        "/proc/gpufreq/gpufreq_opp_freq",
    )

    // شكل صيغة قيمة مفتاح gpu_frequency — انظر explanation في encodeRequest.
    private const val SCHEMA_SEPARATOR = "|"
    private const val UNTOUCHED = "-"
    private const val UNREADABLE = "unreadable"
    private const val RELEASED = "released"
    private const val HELD = "held"

    enum class Family { QUALCOMM, MALI, UNKNOWN }
    enum class IntentMode { EFFICIENCY, ADAPTIVE, SUSTAINED }
    enum class SelectionState { READY, READ_ONLY, AMBIGUOUS, UNAVAILABLE }
    enum class FrequencyUnit(val hzMultiplier: Long) {
        HZ(1L), KHZ(1_000L), MHZ(1_000_000L), AMBIGUOUS(0L)
    }

    /**
     * The read half of [Io] (plan `P3`/`T3.2`).
     *
     * A discovery caller — `Max Atlas` — gets this and nothing more. It has no `write` and no
     * `writable` member, so it cannot *ask* whether something could be written, which is stronger
     * than asking and being told `false`. The legacy [Io] extends it, so every existing implementation
     * and call site is unchanged.
     *
     * **Contract:** [read] returns the value as the interface reported it, `null` when it did not
     * answer. `SystemIo` is backed by [RootFileAccess], which trims; the scanner below does not rely on
     * that, because a sysfs value arrives with a trailing newline and a reader that handed back the
     * bytes unchanged used to make every frequency parse to `null` — a device that *has* a clock would
     * have been reported as having none. Trimming at the parse site is what makes both readers behave
     * the same.
     */
    interface ReadIo {
        fun exists(path: String): Boolean
        fun read(path: String): String?
        fun listDirectories(path: String): List<String>
    }

    interface Io : ReadIo {
        fun writable(path: String): Boolean
        fun write(path: String, value: String): Boolean

        /**
         * يحرّر سقف المنصّة قبل الكتابة على مدى `devfreq`. والمُهلة الافتراضية **بلا فعل** عن قصد:
         * كاتب مُوجَّه باختبار لا يجب أن يلمس عقد سلطة حقيقية، و[MockIo] وأخواته يُرثان هذه
         * القيمة فلا يتغيّر سلوك أي اختبار قائم. أما [SystemIo] فينفّذها فعلًا.
         *
         * ولماذا هي في هذا الموصل لا نداء مباشر داخل [applyDevfreq]: لأن نقطة الكتابة نفسها
         * تُستبدل في الاختبارات، فالوصول إلى العتاد من داخل دالة تُختبر بمُوجّه وهمي يُبطل الوهم.
         */
        fun permitVendorCeiling(lock: Boolean): Boolean = false
    }

    object SystemIo : Io {
        override fun exists(path: String) = RootFileAccess.exists(path)
        override fun writable(path: String) = RootFileAccess.writable(path)
        override fun read(path: String) = RootFileAccess.read(path)
        override fun write(path: String, value: String) = RootFileAccess.write(path, value)
        override fun listDirectories(path: String) = RootFileAccess.listDirectories(path)
        override fun permitVendorCeiling(lock: Boolean): Boolean =
            PlatformCeilingAuthority.permitGpu(lock = lock).anyChannel
    }

    data class Device(
        val path: String,
        val name: String,
        val governor: String?,
        val governors: List<String>,
        val minFreq: Long?,
        val maxFreq: Long?,
        val currentFreq: Long?,
        val frequencies: List<Long>,
        val family: Family = Family.UNKNOWN,
        val loadPercent: Int? = null,
        val thermalC: Int? = null,
        val frequencyUnit: FrequencyUnit = FrequencyUnit.AMBIGUOUS,
        val minWritable: Boolean = false,
        val maxWritable: Boolean = false,
        val governorWritable: Boolean = false,
        val mtkFixedIndexPath: String? = null,
        val mtkLockWritable: Boolean = false,
        val mtkOppIndexByFrequency: Map<Long, String> = emptyMap(),
        val evidence: List<String> = emptyList(),
    ) {
        val unitTrusted: Boolean get() = frequencyUnit != FrequencyUnit.AMBIGUOUS
        val rangeWritable: Boolean get() =
            minWritable && maxWritable && frequencies.isNotEmpty() && minFreq != null && maxFreq != null && unitTrusted && mtkFixedIndexPath == null
        val exactLockWritable: Boolean get() = rangeWritable ||
            (mtkFixedIndexPath != null && mtkLockWritable && mtkOppIndexByFrequency.isNotEmpty() && unitTrusted)
        val provenMinFreq: Long? get() = frequencies.firstOrNull()
        val provenMaxFreq: Long? get() = frequencies.lastOrNull()
    }

    data class Selection(
        val state: SelectionState,
        val device: Device? = null,
        val candidates: List<Device> = emptyList(),
        val reason: String,
        val observedAtMs: Long = System.currentTimeMillis(),
    )

    data class Request(
        val minFreq: Long? = null,
        val maxFreq: Long? = null,
        val governor: String? = null,
        /** Release a fixed-index lock so the governor scales the GPU dynamically again. */
        val releaseLock: Boolean = false,
        /** Allow a full-capability request to remove a lower vendor ceiling before writing. */
        val releaseVendorCeiling: Boolean = false,
    )

    data class Baseline(
        val devicePath: String,
        val minFreq: Long?,
        val maxFreq: Long?,
        val governor: String?,
        val fixedIndex: String? = null,
    )

    data class TransactionResult(
        val requested: Request,
        val actual: Device?,
        val writeSucceeded: Boolean,
        val verified: Boolean,
        val rollbackAttempted: Boolean = false,
        val rollbackVerified: Boolean? = null,
        val error: String? = null,
    )

    private data class Candidate(val device: Device, val score: Int)

    /** Capability frequencies and safe fixed-index mappings are different facts. */
    private data class MtkOppDiscovery(
        val frequencies: Set<Long>,
        val indexed: Map<Long, String>,
    )

    /**
     * A GPU **fact** as Atlas may hold it.
     *
     * Every writability flag of [Device] is absent, not `false`: a projection that cannot express
     * "this could be written" cannot leak a control decision, and `P3`/`T3.4` requires exactly that.
     * The frequency unit stays attached because it is the one thing that decides whether a number
     * means anything at all.
     */
    data class GpuFact(
        val path: String,
        val name: String,
        val family: Family,
        val governor: String?,
        val governors: List<String>,
        val minFreq: Long?,
        val maxFreq: Long?,
        val currentFreq: Long?,
        val frequencies: List<Long>,
        val frequencyUnit: FrequencyUnit,
        val loadPercent: Int?,
        val thermalC: Int?,
        val evidence: List<String>,
    ) {
        val unitTrusted: Boolean get() = frequencyUnit != FrequencyUnit.AMBIGUOUS
        val provenMinFreq: Long? get() = frequencies.firstOrNull()
        val provenMaxFreq: Long? get() = frequencies.lastOrNull()
    }

    enum class GpuObservationState { AVAILABLE, AMBIGUOUS, UNAVAILABLE }

    data class GpuObservation(
        val state: GpuObservationState,
        /** Every candidate, so an ambiguous device is visible rather than silently narrowed. */
        val devices: List<GpuFact>,
        /** The provider the scanner considers best; `null` when nothing qualified or several tied. */
        val bestPath: String?,
        val reason: String,
        val observedAtMs: Long,
    )

    /**
     * Adapts a reader to the legacy [Io] by answering the writer half **locally**.
     *
     * It never delegates `writable` and never performs `write`, so running discovery through it is
     * not "a read that happened to be refused" — the attempt does not exist. This is what lets Atlas
     * reuse the shipped candidate/selection code without a second copy of it.
     */
    class ReadOnlyIo(private val reads: ReadIo) : Io {
        override fun exists(path: String): Boolean = reads.exists(path)
        override fun read(path: String): String? = reads.read(path)
        override fun listDirectories(path: String): List<String> = reads.listDirectories(path)
        override fun writable(path: String): Boolean = false
        override fun write(path: String, value: String): Boolean = false
    }

    fun selection(io: Io = SystemIo): Selection = select(discoverCandidates(io))

    /**
     * Read-only discovery for Atlas: one pass over the same candidate code, reported as facts.
     *
     * Ambiguity is preserved exactly as [selection] computes it (no "first GPU wins"), and the unit
     * ambiguity survives too, so a caller cannot read a value whose meaning was never established.
     */
    fun observe(reads: ReadIo): GpuObservation {
        val candidates = discoverCandidates(ReadOnlyIo(reads))
        val selection = select(candidates)
        return GpuObservation(
            state = when (selection.state) {
                SelectionState.UNAVAILABLE -> GpuObservationState.UNAVAILABLE
                SelectionState.AMBIGUOUS -> GpuObservationState.AMBIGUOUS
                else -> GpuObservationState.AVAILABLE
            },
            devices = candidates.map { fact(it.device) },
            bestPath = selection.device?.path,
            reason = selection.reason,
            observedAtMs = selection.observedAtMs,
        )
    }

    private fun fact(device: Device): GpuFact = GpuFact(
        path = device.path,
        name = device.name,
        family = device.family,
        governor = device.governor,
        governors = device.governors,
        minFreq = device.minFreq,
        maxFreq = device.maxFreq,
        currentFreq = device.currentFreq,
        frequencies = device.frequencies,
        frequencyUnit = device.frequencyUnit,
        loadPercent = device.loadPercent,
        thermalC = device.thermalC,
        evidence = device.evidence,
    )

    private fun select(candidates: List<Candidate>): Selection {
        if (candidates.isEmpty()) return Selection(SelectionState.UNAVAILABLE, reason = "no-gpu-provider")
        val bestScore = candidates.maxOf(Candidate::score)
        val best = candidates.filter { it.score == bestScore }
        if (best.size != 1) {
            return Selection(
                SelectionState.AMBIGUOUS,
                candidates = best.map(Candidate::device),
                reason = "multiple-equally-proven-gpu-providers",
            )
        }
        val device = best.single().device
        val writable = device.unitTrusted && (device.rangeWritable || device.exactLockWritable || device.governorWritable)
        return Selection(
            state = if (writable) SelectionState.READY else SelectionState.READ_ONLY,
            device = device,
            candidates = candidates.map(Candidate::device),
            reason = when {
                !device.unitTrusted -> "ambiguous-frequency-unit"
                writable -> "verified-gpu-provider"
                else -> "telemetry-only-provider"
            },
        )
    }

    fun refresh(path: String, io: Io = SystemIo): Device? =
        discoverCandidates(io).firstOrNull { it.device.path == path }?.device

    fun effectiveFrequency(device: Device, io: Io = SystemIo): Long? =
        currentExactLockFrequency(device, io) ?: device.maxFreq

    /**
     * Returns the highest frequency that is both advertised by the provider and
     * currently allowed by its live `max_freq` ceiling.  The advertised OPP
     * table is a capability catalogue; `max_freq` is a runtime constraint and
     * can be lowered by a vendor thermal/power daemon. Treating the catalogue
     * maximum as the live maximum made per-app profiles repeatedly request an
     * impossible value (for example 1.3 GHz while the driver was capped at
     * 754 MHz).
     */
    fun configurableMaxFrequency(device: Device): Long? {
        val advertisedMax = device.frequencies.filter { it > 0L }.maxOrNull()
        val liveMax = device.maxFreq?.takeIf { it > 0L }
        return when {
            advertisedMax == null -> liveMax
            liveMax == null -> advertisedMax
            else -> minOf(advertisedMax, liveMax)
        }
    }

    /**
     * Maps a requested frequency to a real OPP that the provider can currently
     * carry.  It never returns an OPP above the live configurable ceiling.
     */
    fun snapToAvailableAtOrBelow(
        device: Device,
        requestedHz: Long,
        respectLiveCeiling: Boolean = true,
    ): Long? {
        val frequencies = device.frequencies.filter { it > 0L }.distinct().sorted()
        if (frequencies.isEmpty()) return null
        // Cooling profiles respect the live vendor ceiling. A full-capability request
        // must not: its contract is to ask for the highest advertised OPP and let the
        // platform authority report any lower ceiling it enforces after the write.
        val cap = if (respectLiveCeiling) configurableMaxFrequency(device) else null
        val usable = if (cap != null) frequencies.filter { it <= cap } else frequencies
        if (usable.isEmpty()) return null
        return usable.lastOrNull { it <= requestedHz } ?: usable.first()
    }

    /**
     * الصيغة القياسية لقيمة مفتاح `gpu_frequency:<device>`.
     *
     * المُحكِّم يُثبت المعاملة بـ**تساوي نصّين**: القيمة المطلوبة والقيمة
     * المقروءة من السائق بعد الكتابة. فصيغة تحمل المدى الرقمي وحده لا تستطيع
     * إثبات طلبٍ مضمونه في حقل آخر: اختيار مُحكِّم (governor)، أو تحرير قفل
     * OPP الثابت في MediaTek. في الحالتين تنجح الكتابة، ولا يساوي المقروء
     * المطلوبَ أبدًا، فيُصنَّف تغييرٌ ناجح فاشلًا غير مؤكَّد وتُعاد الحالة
     * السابقة — والمستخدم يرى المقبض يرتدّ بلا سبب مكتوب.
     *
     * فالصيغة هنا تحمل كل حقل يمكن للطلب أن يمسّه، و[encodeLive] تُسقِط الحالة
     * الحية على **نفس** الحقول التي يمسّها ذلك الطلب وحده؛ فيصير التساوي معناه
     * «هذا الطلب مُلبّى» لا شيئًا آخر.
     */
    fun encodeRequest(request: Request): String = listOf(
        if (request.minFreq != null && request.maxFreq != null) {
            "${request.minFreq}:${request.maxFreq}"
        } else UNTOUCHED,
        request.governor ?: UNTOUCHED,
        if (request.releaseLock) RELEASED else UNTOUCHED,
    ).joinToString(SCHEMA_SEPARATOR)

    /** الحالة الحية مُسقَطةً على حقول [request] وحدها — لا على كل الحالة. */
    fun encodeLive(device: Device, request: Request, io: Io = SystemIo): String = listOf(
        if (request.minFreq != null && request.maxFreq != null) {
            "${device.minFreq?.toString().orEmpty()}:${device.maxFreq?.toString().orEmpty()}"
        } else UNTOUCHED,
        if (request.governor != null) device.governor ?: UNREADABLE else UNTOUCHED,
        if (request.releaseLock) {
            if (fixedLockReleased(device, io)) RELEASED else HELD
        } else UNTOUCHED,
    ).joinToString(SCHEMA_SEPARATOR)

    /**
     * هل لا يوجد قفل OPP ثابت يقيّد التردد؟ صحيح أيضًا لجهاز لا يملك مسار قفل
     * أصلًا (لا شيئ لنحرّره)، وخاطئ إن كان المسار موجودًا وغير مقروء — لا يُدّعى
     * تحرير قفل لم يُقرأ.
     */
    /**
     * هل الحالة الحية تُلبّي الطلب المُرمَّز؟ — حكم [requestSatisfied] وحده يُمرَّر للمُحكِّم.
     *
     * ولماذا لا يكفي التساوي الحرفي: حقول المدى (`min:max`) تُدمج/تُقيَّد من السائق
     * وسياسة الـvendor. فطلبٌ `500000000:754000000` مقابل قراءة `500000000:600000000`
     * هو **تلبية** (السقف لم يُخرَق) لا فشل، والتساوي كان يسترجع خط الأساس ثم يُعاد
     * الطلب في كل دورة انحراف بلا نتيجة. وبقية الحقول تبقى تساويًا حرفيًّا لأن معناها
     * الوحيد كذلك: مُحكِّم اخترناه، أو تحرير قفل OPP ثابت. والصيغة تعيش هنا وحدها
     * لأن هذا الملف هو الذي يُنشئها ([encodeRequest]/[encodeLive])، فلا تُنسخ في مكان آخر.
     */
    fun requestSatisfied(desired: String, actual: String?): Boolean {
        if (actual == null) return false
        if (!desired.contains(SCHEMA_SEPARATOR) && !actual.contains(SCHEMA_SEPARATOR)) {
            return HardwareVerification.rangeContained(desired, actual)
        }
        val want = desired.split(SCHEMA_SEPARATOR)
        val live = actual.split(SCHEMA_SEPARATOR)
        if (want.size != live.size) return false
        return want.indices.all { index ->
            if (index != 0) return@all want[index] == live[index]
            if (want[index] == UNTOUCHED) live[index] == UNTOUCHED
            else HardwareVerification.rangeContained(want[index], live[index])
        }
    }

    fun fixedLockReleased(device: Device, io: Io = SystemIo): Boolean {
        val path = device.mtkFixedIndexPath ?: return true
        val raw = io.read(path) ?: return false
        return parseMtkIndex(raw) == "-1"
    }

    fun encodeBaseline(baseline: Baseline): String = listOf(
        baseline.devicePath,
        baseline.minFreq?.toString().orEmpty(),
        baseline.maxFreq?.toString().orEmpty(),
        baseline.governor.orEmpty(),
        baseline.fixedIndex.orEmpty(),
    ).joinToString("\u001f")

    fun decodeBaseline(value: String): Baseline? {
        val parts = value.split('\u001f')
        if (parts.size != 5 || parts[0].isBlank()) return null
        return Baseline(
            devicePath = parts[0],
            minFreq = parts[1].toLongOrNull(),
            maxFreq = parts[2].toLongOrNull(),
            governor = parts[3].takeIf(String::isNotBlank),
            fixedIndex = parts[4].takeIf(String::isNotBlank),
        )
    }

    fun captureBaseline(device: Device, io: Io = SystemIo): Baseline = Baseline(
        devicePath = device.path,
        minFreq = device.minFreq,
        maxFreq = device.maxFreq,
        governor = device.governor,
        fixedIndex = device.mtkFixedIndexPath?.let(io::read)?.let(::parseMtkIndex),
    )

    fun requestForMode(device: Device, mode: IntentMode): Request? {
        val advertised = device.frequencies.filter { it > 0L }.distinct().sorted()
        if (advertised.isEmpty()) return null
        val liveCap = configurableMaxFrequency(device)
        val frequencies = if (liveCap != null) advertised.filter { it <= liveCap } else advertised
        if (frequencies.isEmpty()) return null
        val last = frequencies.lastIndex
        val min = frequencies.first()
        val max = frequencies.last()
        if (device.rangeWritable) return when (mode) {
            IntentMode.EFFICIENCY -> Request(min, frequencies[(last * 0.40f).toInt().coerceIn(0, last)])
            IntentMode.ADAPTIVE -> Request(min, max)
            IntentMode.SUSTAINED -> Request(frequencies[(last * 0.65f).toInt().coerceIn(0, last)], max)
        }
        if (device.exactLockWritable && device.mtkFixedIndexPath != null) return when (mode) {
            IntentMode.EFFICIENCY -> Request(min, min)
            IntentMode.ADAPTIVE -> Request(releaseLock = true)
            IntentMode.SUSTAINED -> {
                val sustained = frequencies[(last * 0.65f).toInt().coerceIn(0, last)]
                Request(sustained, sustained)
            }
        }
        return null
    }

    fun validate(device: Device, request: Request): String? {
        if (request.minFreq == null && request.maxFreq == null && request.governor == null && !request.releaseLock) return "empty-request"
        if (!device.unitTrusted) return "ambiguous-frequency-unit"
        if (request.releaseLock) {
            if (request.minFreq != null || request.maxFreq != null || request.governor != null) return "invalid-request"
            if (device.mtkFixedIndexPath == null || !device.mtkLockWritable) return "release-lock-unavailable"
            return null
        }
        if (request.governor != null && request.governor !in device.governors) return "unsupported-governor"
        if (request.governor != null && !device.governorWritable) return "governor-read-only"
        if (request.minFreq != null || request.maxFreq != null) {
            if (request.minFreq == null || request.maxFreq == null) return "incomplete-range"
            if (request.minFreq > request.maxFreq) return "invalid-range"
            if (request.minFreq !in device.frequencies || request.maxFreq !in device.frequencies) return "unsupported-frequency"
            val exact = request.minFreq == request.maxFreq
            if (exact && !device.exactLockWritable) return "exact-lock-read-only-or-unproven"
            if (!exact && !device.rangeWritable) return "range-read-only-or-unproven"
            if (device.mtkFixedIndexPath == null && (device.minFreq == null || device.maxFreq == null)) return "baseline-unreadable"
        }
        return null
    }

    fun apply(device: Device, request: Request, io: Io = SystemIo): TransactionResult {
        val selected = selection(io)
        val live = selected.device?.takeIf { it.path == device.path }
            ?: return TransactionResult(
                request,
                null,
                false,
                false,
                error = if (selected.state == SelectionState.AMBIGUOUS) "provider-ambiguous" else "provider-disappeared",
            )
        val error = validate(live, request)
        if (error != null) return TransactionResult(request, live, false, false, error = error)
        return applyValidated(live, request, io)
    }

    /**
     * Applies a request to an already-selected and already-validated provider.
     *
     * The caller must own the control transaction (normally through
     * [HardwareControlArbiter]). This seam prevents a control arbiter callback
     * from selecting and transacting the same hardware a second time.
     */
    fun applyValidated(live: Device, request: Request, io: Io = SystemIo): TransactionResult {
        val error = validate(live, request)
        if (error != null) return TransactionResult(request, live, false, false, error = error)
        return when {
            request.releaseLock -> applyMtkRelease(live, request, io)
            request.minFreq != null && request.minFreq == request.maxFreq && live.mtkFixedIndexPath != null -> applyMtkExact(live, request, io)
            else -> applyDevfreq(live, request, io)
        }
    }

    private fun applyMtkRelease(live: Device, request: Request, io: Io): TransactionResult {
        val path = live.mtkFixedIndexPath ?: return TransactionResult(request, live, false, false, error = "fixed-lock-unavailable")
        val baselineIndex = io.read(path)?.let(::parseMtkIndex)
            ?: return TransactionResult(request, live, false, false, error = "baseline-unreadable")
        if (baselineIndex == "-1") return TransactionResult(request, refresh(live.path, io), true, true)
        // مقابل `lock = true` في applyMtkExact: تحرير القفل يعني عودة GED لإدارة OPP
        // بنفسه، فيجب أن يعمل DVFS من جديد وإلا بقي التردد مثبَّتًا على آخر قيمة
        // صادف وجودها لحظة الإيقاف — وهو ما وثّقه permitGpu نفسه.
        io.permitVendorCeiling(lock = false)
        val wrote = io.write(path, "-1")
        val verified = wrote && io.read(path)?.let(::parseMtkIndex) == "-1"
        if (verified) return TransactionResult(request, refresh(live.path, io), true, true)
        val rollbackWrote = io.write(path, baselineIndex)
        val rollbackVerified = rollbackWrote && io.read(path)?.let(::parseMtkIndex) == baselineIndex
        return TransactionResult(
            request, refresh(live.path, io), wrote, false, true, rollbackVerified,
            if (rollbackVerified) "apply-not-verified-baseline-restored" else "apply-and-rollback-failed",
        )
    }

    private fun applyDevfreq(live: Device, request: Request, io: Io): TransactionResult {
        val touchesRange = request.minFreq != null && request.maxFreq != null
        val touchesGovernor = request.governor != null
        if (touchesRange && (live.minFreq == null || live.maxFreq == null)) {
            return TransactionResult(request, live, false, false, error = "baseline-unreadable")
        }
        if (touchesGovernor && live.governor == null) {
            return TransactionResult(request, live, false, false, error = "baseline-unreadable")
        }
        val baseline = captureBaseline(live, io)
        // سلطة المنصّة **قبل** الكتابة، والترتيب مقصود: على MediaTek يقمع جهاز تبريد الحرارة وسقف
        // GED أي رفع فوقهما، فيُقرأ `differs` بلا سبب ظاهر في السجل (قيس على جهاز حقيقي:
        // 1300000000 ⇒ 754000000). ولو كتبنا أولًا لبقي أثر القمع مسجّلًا عطلًا انتهى.
        if (touchesRange) {
            io.permitVendorCeiling(
                lock = request.releaseVendorCeiling || request.minFreq == request.maxFreq,
            )
        }
        var wrote = true
        if (touchesRange) wrote = writeRange(live, request.minFreq, request.maxFreq, io)
        if (wrote && touchesGovernor) wrote = io.write("${live.path}/governor", request.governor)
        val actual = refresh(live.path, io)
        if (wrote && matches(actual, request)) return TransactionResult(request, actual, true, true)

        val rollbackVerified = restoreTouchedBaseline(baseline, request, io)
        // وبعد التراجع يُعاد الوضع الطبيعي للسلطة: طلب تثبيت فشل كان قد أوقف DVFS في GED، وإبقاؤه
        // موقوفًا يُثبّت التردد الذي صادف وجوده — أي يُنتج العطب الذي جاء التراجع لإلغائه.
        io.permitVendorCeiling(lock = false)
        return TransactionResult(
            requested = request,
            actual = refresh(live.path, io),
            writeSucceeded = wrote,
            verified = false,
            rollbackAttempted = true,
            rollbackVerified = rollbackVerified,
            error = if (rollbackVerified) "apply-not-verified-baseline-restored" else "apply-and-rollback-failed",
        )
    }

    private fun applyMtkExact(live: Device, request: Request, io: Io): TransactionResult {
        val path = live.mtkFixedIndexPath ?: return TransactionResult(request, live, false, false, error = "fixed-lock-unavailable")
        val frequency = request.minFreq ?: return TransactionResult(request, live, false, false, error = "incomplete-range")
        val targetIndex = live.mtkOppIndexByFrequency[frequency]
            ?: return TransactionResult(request, live, false, false, error = "unsupported-frequency")
        val baselineIndex = io.read(path)?.let(::parseMtkIndex)
            ?: return TransactionResult(request, live, false, false, error = "baseline-unreadable")
        val touchesGovernor = request.governor != null
        val baselineGovernor = if (touchesGovernor) {
            live.governor ?: return TransactionResult(request, live, false, false, error = "baseline-unreadable")
        } else null
        // نفس سلطة المنصّة التي أُطلقت في applyDevfreq، وللسبب نفسه: القراءة الفورية بعد
        // الكتابة هنا تنجح دائمًا (السائق يقبل رقم الـindex فورًا)، لكن GED يواصل DVFS
        // الخاص به فوق هذا القفل ويُعيد التردد خلال ثوانٍ — فيرى المستخدم «تحقّق ناجح»
        // في السجل وتذبذبًا فعليًّا على الجهاز. `lock = true` هنا توقف DVFS في GED تحديدًا
        // لأجل هذا (انظر توثيق `PlatformCeilingAuthority.permitGpu`)، وهو ما كان ناقصًا في
        // هذا المسار وحده من بين مسارات الكتابة الثلاثة.
        io.permitVendorCeiling(lock = true)
        val wroteLock = io.write(path, targetIndex)
        val wroteGovernor = !touchesGovernor || (wroteLock && io.write("${live.path}/governor", request.governor))
        val actual = refresh(live.path, io)
        val verified = wroteLock && wroteGovernor &&
            io.read(path)?.let(::parseMtkIndex) == parseMtkIndex(targetIndex) &&
            (request.governor == null || actual?.governor.equals(request.governor, true))
        if (verified) return TransactionResult(request, actual, true, true)
        val rollbackLock = io.write(path, baselineIndex) && io.read(path)?.let(::parseMtkIndex) == baselineIndex
        val rollbackGovernor = baselineGovernor == null ||
            (io.write("${live.path}/governor", baselineGovernor) && refresh(live.path, io)?.governor == baselineGovernor)
        val rollbackVerified = rollbackLock && rollbackGovernor
        io.permitVendorCeiling(lock = false)
        return TransactionResult(
            request, refresh(live.path, io), wroteLock && wroteGovernor, false, true, rollbackVerified,
            if (rollbackVerified) "apply-not-verified-baseline-restored" else "apply-and-rollback-failed",
        )
    }

    private fun restoreTouchedBaseline(baseline: Baseline, request: Request, io: Io): Boolean {
        val live = refresh(baseline.devicePath, io) ?: return false
        var restoredAll = true
        if (request.minFreq != null || request.maxFreq != null) {
            val min = baseline.minFreq
            val max = baseline.maxFreq
            restoredAll = if (min == null || max == null) false else writeRangeRaw(live, min, max, io) && restoredAll
        }
        if (request.governor != null) {
            val governor = baseline.governor
            restoredAll = if (governor == null) false else io.write("${live.path}/governor", governor) && restoredAll
        }
        val restored = refresh(baseline.devicePath, io) ?: return false
        return restoredAll &&
            (request.minFreq == null || (restored.minFreq == baseline.minFreq && restored.maxFreq == baseline.maxFreq)) &&
            (request.governor == null || restored.governor == baseline.governor)
    }

    fun restoreBaseline(baseline: Baseline, io: Io = SystemIo): Boolean {
        val live = refresh(baseline.devicePath, io) ?: return false
        var touched = false
        var restoredAll = true
        if (baseline.fixedIndex != null && live.mtkFixedIndexPath != null) {
            touched = true
            val wrote = io.write(live.mtkFixedIndexPath, baseline.fixedIndex)
            restoredAll = wrote && io.read(live.mtkFixedIndexPath)?.let(::parseMtkIndex) == baseline.fixedIndex && restoredAll
        }
        if (baseline.minFreq != null && baseline.maxFreq != null && live.mtkFixedIndexPath == null) {
            touched = true
            restoredAll = writeRangeRaw(live, baseline.minFreq, baseline.maxFreq, io) && restoredAll
        }
        if (baseline.governor != null && live.governorWritable) {
            touched = true
            restoredAll = io.write("${live.path}/governor", baseline.governor) && restoredAll
        }
        if (!touched) return false
        val restored = refresh(baseline.devicePath, io) ?: return false
        return restoredAll &&
            (baseline.minFreq == null || live.mtkFixedIndexPath != null || (restored.minFreq == baseline.minFreq && restored.maxFreq == baseline.maxFreq)) &&
            (baseline.governor == null || !live.governorWritable || restored.governor == baseline.governor)
    }

    fun setGovernor(device: Device, governor: String, io: Io = SystemIo): VerificationResult<String> {
        val result = apply(device, Request(governor = governor), io)
        return VerificationResult(governor, result.actual?.governor, result.writeSucceeded, result.verified, result.error)
    }

    fun clamp(device: Device, frequency: Long, io: Io = SystemIo): VerificationResult<Long> {
        val result = apply(device, Request(frequency, frequency), io)
        return VerificationResult(frequency, result.actual?.maxFreq, result.writeSucceeded, result.verified, result.error)
    }

    fun currentExactLockFrequency(device: Device, io: Io = SystemIo): Long? {
        val live = selection(io).device?.takeIf { it.path == device.path } ?: return null
        val path = live.mtkFixedIndexPath ?: return null
        val index = io.read(path)?.let(::parseMtkIndex)?.takeIf { it != "-1" } ?: return null
        return live.mtkOppIndexByFrequency.entries.firstOrNull { parseMtkIndex(it.value) == index }?.key
    }

    fun releaseExactLock(io: Io = SystemIo): Boolean {
        val device = selection(io).device ?: return true
        val path = device.mtkFixedIndexPath ?: return true
        if (!device.mtkLockWritable) return false
        val baselineIndex = io.read(path)?.let(::parseMtkIndex) ?: return false
        if (baselineIndex == "-1") return true
        val wrote = io.write(path, "-1")
        val verified = wrote && io.read(path)?.let(::parseMtkIndex) == "-1"
        if (verified) return true
        io.write(path, baselineIndex)
        return false
    }

    fun frequencyMHz(device: Device, raw: Long?): Long? {
        if (raw == null || raw <= 0L || !device.unitTrusted) return null
        val hz = raw * device.frequencyUnit.hzMultiplier
        return (hz / 1_000_000L).takeIf { it in 1L..10_000L }
    }

    fun inferFrequencyUnit(values: List<Long>): FrequencyUnit {
        val positive = values.filter { it > 0L }
        if (positive.isEmpty()) return FrequencyUnit.AMBIGUOUS
        val units = positive.map { raw ->
            when {
                raw >= 10_000_000L -> FrequencyUnit.HZ
                raw >= 10_000L -> FrequencyUnit.KHZ
                else -> FrequencyUnit.MHZ
            }
        }.distinct()
        if (units.size != 1) return FrequencyUnit.AMBIGUOUS
        val unit = units.single()
        val mhz = positive.map { (it * unit.hzMultiplier) / 1_000_000L }
        return unit.takeIf { mhz.all { value -> value in 1L..10_000L } } ?: FrequencyUnit.AMBIGUOUS
    }

    fun rangeWriteOrder(currentMax: Long?, targetMin: Long, targetMax: Long): List<String> =
        if (currentMax != null && targetMin > currentMax) listOf("max", "min") else listOf("min", "max")

    private fun discoverCandidates(io: Io): List<Candidate> = io.listDirectories(ROOT)
        .mapNotNull { readCandidate(it, io) }
        .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.device.name })

    private fun readCandidate(name: String, io: Io): Candidate? {
        val path = "$ROOT/$name"
        val deviceName = io.read("$path/device_name").orEmpty()
        val descriptor = "$name $deviceName $path".lowercase()
        val family = when {
            descriptor.contains("kgsl") || descriptor.contains("adreno") -> Family.QUALCOMM
            descriptor.contains("mali") -> Family.MALI
            else -> Family.UNKNOWN
        }
        val identityScore = when {
            descriptor.contains("kgsl") || descriptor.contains("adreno") -> 100
            descriptor.contains("mali") -> 100
            name.contains("gpu", true) -> 80
            name.contains("3d", true) -> 70
            else -> 0
        }
        if (identityScore == 0) return null

        val rawFrequencies = parseLongList(io.read("$path/available_frequencies"))
        // Trimmed here, not assumed trimmed by the reader: see the [ReadIo] contract.
        val rawMin = io.read("$path/min_freq")?.trim()?.toLongOrNull()
        val rawMax = io.read("$path/max_freq")?.trim()?.toLongOrNull()
        val rawCurrent = io.read("$path/cur_freq")?.trim()?.toLongOrNull()
        val governor = io.read("$path/governor")?.trim()
        val governors = io.read("$path/available_governors").orEmpty()
            .split(Regex("\\s+")).filter(String::isNotBlank).distinct()
        if (governor == null && rawFrequencies.isEmpty() && rawCurrent == null) return null

        val mtkDiscovery = if (family == Family.MALI) readMtkOppMap(io) else MtkOppDiscovery(emptySet(), emptyMap())
        val mtkMap = mtkDiscovery.indexed
        // An unindexed OPP proves capability but cannot safely be written through a
        // fixed-index node. Never invent an index for it.
        val mtkLockPath = if (
            mtkMap.isNotEmpty() &&
            // A fixed-index writer is safe only when every discovered capability
            // has a real kernel index. If another OPP surface exposes an unindexed
            // higher capability, prefer a generic devfreq range (when writable)
            // instead of silently pinning the GPU to the smaller indexed table.
            mtkDiscovery.frequencies.all { it in mtkMap }
        ) MTK_LOCK_PATHS.firstOrNull(io::exists) else null
        val genericUnit = inferFrequencyUnit(rawFrequencies + listOfNotNull(rawMin, rawMax, rawCurrent))
        val frequencies: List<Long>
        val min: Long?
        val max: Long?
        val current: Long?
        val unit: FrequencyUnit
        val effectiveMtkFrequency = if (mtkLockPath != null) {
            val activeIndex = io.read(mtkLockPath)?.let(::parseMtkIndex)?.takeIf { it != "-1" }
            activeIndex?.let { index -> mtkMap.entries.firstOrNull { parseMtkIndex(it.value) == index }?.key }
        } else null
        if (mtkLockPath != null) {
            frequencies = (mtkDiscovery.frequencies + mtkMap.keys).toList().sorted()
            min = effectiveMtkFrequency ?: frequencies.firstOrNull()
            max = effectiveMtkFrequency ?: frequencies.lastOrNull()
            current = effectiveMtkFrequency ?: normalizeToAdvertisedHz(rawCurrent, frequencies)
            unit = FrequencyUnit.HZ
        } else {
            // لغة أرقام هذا الكائن هي **لغة العقدة نفسها**، لا Hz.
            //
            // `min_freq`/`max_freq`/`cur_freq` و`available_frequencies` تُحفظ كما قرأها السائق،
            // و`frequencyUnit` يسمّي تلك اللغة: `frequencyMHz()` هي التي تحوّل للعرض،
            // و`writeRange()` تكتب القيمة كما هي. وتحويلها هنا إلى Hz كان: (١) يضاعف التحويل في
            // كل عرض (MHZ × MHZ)، (٢) ويسجّل قيمة غير متطابقة لما يقرأه `encodeLive` فيُصنَّف
            // تطبيقٌ ناجح فاشلًا، (٣) ويكتب Hz في عقدة تتكلّم kHz — أي يفسد `min_freq` على كل جهاز
            // لا يستعمل Hz. وجدول MediaTek وحده معلوم الوحدة (Hz) فيُدمج **فقط** حين يكون الجدول
            // العام بنفس اللغة أو غير موجود أصلًا؛ وإلا لَخُلطت وحدتان في سلّم واحد.
            val mtkCapability = if (genericUnit == FrequencyUnit.HZ || rawFrequencies.isEmpty()) {
                mtkDiscovery.frequencies
            } else {
                emptySet()
            }
            frequencies = (rawFrequencies + mtkCapability).filter { it > 0L }.distinct().sorted()
            // حدّ أدنى/أعلى غير مقروء **لا يُخترع** من السلّم. الاختراع كان يجعل خط الأساس يبدو
            // مقروءًا، فيمرّ المدى من [validate] وتنجح كتابة لا يمكن التراجع عنها إلى ما كان عليه
            // فعلًا (وقد كُشف ذلك في `GpuControlModelTest.unreadableBaselineBlocksRangeMutation`).
            // والغياب هنا هو بالضبط ما يجعل `rangeWritable` كاذبًا و[validate] تُجيب
            // `range-read-only-or-unproven`: الفشل الصريح خير من نجاح لا خط أساس له.
            min = rawMin
            max = rawMax
            current = rawCurrent
            unit = if (mtkCapability.isNotEmpty()) FrequencyUnit.HZ else genericUnit
        }
        val evidence = buildList {
            add(if (family == Family.QUALCOMM) "qualcomm-gpu-identity" else "gpu-device-identity")
            if (frequencies.isNotEmpty()) add(if (mtkLockPath != null) "mtk-signed-opp-table" else "devfreq-opp-table")
            if (current != null) add("current-frequency")
            if (unit != FrequencyUnit.AMBIGUOUS) add("consistent-${unit.name.lowercase()}-unit")
        }
        val device = Device(
            path = path,
            name = deviceName.ifBlank { name },
            governor = governor,
            governors = governors,
            minFreq = min,
            maxFreq = max,
            currentFreq = current,
            frequencies = frequencies,
            family = family,
            loadPercent = readLoad(path, family, io),
            thermalC = readGpuThermal(io),
            frequencyUnit = unit,
            minWritable = io.writable("$path/min_freq"),
            maxWritable = io.writable("$path/max_freq"),
            governorWritable = io.writable("$path/governor"),
            mtkFixedIndexPath = mtkLockPath,
            mtkLockWritable = mtkLockPath?.let(io::writable) == true,
            mtkOppIndexByFrequency = mtkMap,
            evidence = evidence,
        )
        val score = identityScore + (if (frequencies.isNotEmpty()) 20 else 0) + (if (current != null) 5 else 0) +
            (if (mtkLockPath != null) 10 else 0)
        return Candidate(device, score)
    }

    private fun parseLongList(raw: String?): List<Long> = raw.orEmpty().split(Regex("\\s+"))
        .mapNotNull(String::toLongOrNull).filter { it > 0L }.distinct().sorted()

    private fun readMtkOppMap(io: Io): MtkOppDiscovery {
        // MTK exposes more than one OPP surface. The signed table can be a filtered
        // runtime table (for example ending at 546 MHz), while gpufreq_opp_dump
        // still exposes the hardware capability (1300 MHz on the reported device).
        // Reading only the first non-empty file silently turns a runtime ceiling into
        // a hardware maximum, which made Performance 100% write 546 successfully.
        val contents = MTK_OPP_TABLES.mapNotNull { path ->
            io.read(path)?.takeIf(String::isNotBlank)
        }
        if (contents.isEmpty()) return MtkOppDiscovery(emptySet(), emptyMap())
        val frequencies = linkedSetOf<Long>()
        val indexedResult = linkedMapOf<Long, String>()
        contents.asSequence().flatMap { it.lineSequence() }.forEach { line ->
            // Kernels expose both indexed tables (`[3] freq=...`) and the legacy
            // dump format (`freq = 1300000`). The latter is still authoritative
            // capability evidence, but it has no explicit index in some builds.
            // Give only a labelled, unit-bearing frequency a bounded ordinal; bare
            // voltage/frequency-looking numbers remain rejected below.
            val indexed = Regex("""\[\s*(\d+)\s*]""").find(line)?.groupValues?.getOrNull(1)
            val tail = line.substringAfterLast(']').trim()
            val labelledFrequency = Regex("""(?i)\bfreq(?:uency)?\s*[=:]\s*""").containsMatchIn(tail)
            if (indexed == null && !labelledFrequency) return@forEach
            // A synthetic ordinal is not a kernel OPP index. It may be used for
            // capability discovery only, never for a write.
            val index = indexed
            val matches = Regex("""(?i)(\d+(?:\.\d+)?)\s*(GHz|MHz|kHz)?""").findAll(tail).toList()
            fun isFrequencyLabeled(candidate: MatchResult): Boolean {
                val prefix = tail.substring(0, candidate.range.first)
                return listOf("freq", "frequency").any { prefix.contains(it, true) }
            }
            // Selection is proof-driven, never a guess: prefer a labeled frequency,
            // then any unit-bearing number, then a single bare number. Multiple bare
            // numbers without labels are ambiguous (frequency vs voltage) and the
            // whole line is rejected rather than misread.
            val match = matches.firstOrNull { candidate ->
                candidate.groupValues.getOrNull(2)?.isNotBlank() == true && isFrequencyLabeled(candidate)
            } ?: matches.firstOrNull { candidate -> isFrequencyLabeled(candidate) }
                ?: matches.firstOrNull { candidate -> candidate.groupValues.getOrNull(2)?.isNotBlank() == true }
                ?: matches.singleOrNull()
                ?: return@forEach
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val hz = when (match.groupValues.getOrNull(2)?.lowercase().orEmpty()) {
                "ghz" -> (value * 1_000_000_000.0).toLong()
                "mhz" -> (value * 1_000_000.0).toLong()
                "khz" -> (value * 1_000.0).toLong()
                else -> when {
                    value >= 10_000_000.0 -> value.toLong()
                    value >= 10_000.0 -> (value * 1_000.0).toLong()
                    else -> (value * 1_000_000.0).toLong()
                }
            }
            if (hz in 1_000_000L..10_000_000_000L) {
                frequencies += hz
                if (index != null) indexedResult[hz] = index
            }
        }
        return MtkOppDiscovery(frequencies, indexedResult)
    }

    private fun normalizeToAdvertisedHz(raw: Long?, advertisedHz: List<Long>): Long? {
        if (raw == null || raw <= 0L) return null
        return listOf(raw, raw * 1_000L, raw * 1_000_000L).distinct().singleOrNull { it in advertisedHz }
    }

    private fun parseMtkIndex(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty() || clean == "-1" || clean.contains("disabled", true) || clean.contains("dynamic", true)) return "-1"
        return Regex("-?\\d+").findAll(clean).map { it.value }.toList().lastOrNull() ?: "-1"
    }

    private fun readLoad(path: String, family: Family, io: Io): Int? {
        val paths = buildList {
            add("$path/load")
            add("$path/device/load")
            if (family == Family.QUALCOMM) add("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            if (family == Family.MALI) add("/sys/module/ged/parameters/gpu_idle")
        }
        return paths.asSequence().mapNotNull { candidate ->
            val raw = io.read(candidate) ?: return@mapNotNull null
            if (candidate.endsWith("gpu_idle")) parseLoad(raw)?.let { 100 - it } else parseLoad(raw)
        }.firstOrNull()
    }

    private fun parseLoad(raw: String): Int? {
        val numbers = Regex("\\d+").findAll(raw).mapNotNull { it.value.toLongOrNull() }.toList()
        if (numbers.isEmpty()) return null
        val percent = if (numbers.size >= 2 && numbers[1] > 0L && numbers[0] > 100L) {
            ((numbers[0] * 100L) / numbers[1]).toInt()
        } else numbers.first().toInt()
        return percent.takeIf { it in 0..100 }
    }

    private fun readGpuThermal(io: Io): Int? = io.listDirectories(THERMAL_ROOT).asSequence()
        .filter { it.startsWith("thermal_zone") }
        .mapNotNull { name ->
            val base = "$THERMAL_ROOT/$name"
            val type = io.read("$base/type").orEmpty().lowercase()
            if (listOf("gpu", "gpuss", "mali", "tsgpu").none(type::contains)) return@mapNotNull null
            val raw = io.read("$base/temp")?.toLongOrNull() ?: return@mapNotNull null
            val c = when {
                kotlin.math.abs(raw) >= 10_000L -> raw / 1_000L
                kotlin.math.abs(raw) > 200L -> raw / 10L
                else -> raw
            }.toInt()
            c.takeIf { it in -40..200 }
        }.maxOrNull()

    private fun writeRange(device: Device, min: Long, max: Long, io: Io): Boolean {
        if (min !in device.frequencies || max !in device.frequencies || min > max) return false
        return writeRangeRaw(device, min, max, io)
    }

    private fun writeRangeRaw(device: Device, min: Long, max: Long, io: Io): Boolean {
        for (field in rangeWriteOrder(device.maxFreq, min, max)) {
            val value = if (field == "min") min else max
            if (!io.write("${device.path}/${field}_freq", value.toString())) return false
        }
        return true
    }

    private fun matches(actual: Device?, request: Request): Boolean = actual != null &&
        (request.minFreq == null || actual.minFreq == request.minFreq) &&
        (request.maxFreq == null || actual.maxFreq == request.maxFreq) &&
        (request.governor == null || actual.governor.equals(request.governor, true))
}
