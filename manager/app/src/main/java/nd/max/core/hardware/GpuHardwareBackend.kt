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

    enum class Family { QUALCOMM, MALI, UNKNOWN }
    enum class IntentMode { EFFICIENCY, ADAPTIVE, SUSTAINED }
    enum class SelectionState { READY, READ_ONLY, AMBIGUOUS, UNAVAILABLE }
    enum class FrequencyUnit(val hzMultiplier: Long) {
        HZ(1L), KHZ(1_000L), MHZ(1_000_000L), AMBIGUOUS(0L)
    }

    interface Io {
        fun exists(path: String): Boolean
        fun writable(path: String): Boolean
        fun read(path: String): String?
        fun write(path: String, value: String): Boolean
        fun listDirectories(path: String): List<String>
    }

    object SystemIo : Io {
        override fun exists(path: String) = RootFileAccess.exists(path)
        override fun writable(path: String) = RootFileAccess.writable(path)
        override fun read(path: String) = RootFileAccess.read(path)
        override fun write(path: String, value: String) = RootFileAccess.write(path, value)
        override fun listDirectories(path: String) = RootFileAccess.listDirectories(path)
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

    fun selection(io: Io = SystemIo): Selection {
        val candidates = discoverCandidates(io)
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

    fun captureBaseline(device: Device, io: Io = SystemIo): Baseline = Baseline(
        devicePath = device.path,
        minFreq = device.minFreq,
        maxFreq = device.maxFreq,
        governor = device.governor,
        fixedIndex = device.mtkFixedIndexPath?.let(io::read)?.let(::parseMtkIndex),
    )

    fun requestForMode(device: Device, mode: IntentMode): Request? {
        val frequencies = device.frequencies
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
        var wrote = true
        if (touchesRange) wrote = writeRange(live, request.minFreq!!, request.maxFreq!!, io)
        if (wrote && touchesGovernor) wrote = io.write("${live.path}/governor", request.governor!!)
        val actual = refresh(live.path, io)
        if (wrote && matches(actual, request)) return TransactionResult(request, actual, true, true)

        val rollbackVerified = restoreTouchedBaseline(baseline, request, io)
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
        val wroteLock = io.write(path, targetIndex)
        val wroteGovernor = !touchesGovernor || (wroteLock && io.write("${live.path}/governor", request.governor!!))
        val actual = refresh(live.path, io)
        val verified = wroteLock && wroteGovernor &&
            io.read(path)?.let(::parseMtkIndex) == parseMtkIndex(targetIndex) &&
            (request.governor == null || actual?.governor.equals(request.governor, true))
        if (verified) return TransactionResult(request, actual, true, true)
        val rollbackLock = io.write(path, baselineIndex) && io.read(path)?.let(::parseMtkIndex) == baselineIndex
        val rollbackGovernor = baselineGovernor == null ||
            (io.write("${live.path}/governor", baselineGovernor) && refresh(live.path, io)?.governor == baselineGovernor)
        val rollbackVerified = rollbackLock && rollbackGovernor
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
        val rawMin = io.read("$path/min_freq")?.toLongOrNull()
        val rawMax = io.read("$path/max_freq")?.toLongOrNull()
        val rawCurrent = io.read("$path/cur_freq")?.toLongOrNull()
        val governor = io.read("$path/governor")?.trim()
        val governors = io.read("$path/available_governors").orEmpty()
            .split(Regex("\\s+")).filter(String::isNotBlank).distinct()
        if (governor == null && rawFrequencies.isEmpty() && rawCurrent == null) return null

        val mtkMap = if (family == Family.MALI) readMtkOppMap(io) else emptyMap()
        val mtkLockPath = if (mtkMap.isNotEmpty()) MTK_LOCK_PATHS.firstOrNull(io::exists) else null
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
            frequencies = mtkMap.keys.sorted()
            min = effectiveMtkFrequency ?: frequencies.firstOrNull()
            max = effectiveMtkFrequency ?: frequencies.lastOrNull()
            current = effectiveMtkFrequency ?: normalizeToAdvertisedHz(rawCurrent, frequencies)
            unit = FrequencyUnit.HZ
        } else {
            frequencies = rawFrequencies
            min = rawMin
            max = rawMax
            current = rawCurrent
            unit = genericUnit
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

    private fun readMtkOppMap(io: Io): Map<Long, String> {
        val content = MTK_OPP_TABLES.firstNotNullOfOrNull { path -> io.read(path)?.takeIf(String::isNotBlank) }
            ?: return emptyMap()
        val result = linkedMapOf<Long, String>()
        content.lineSequence().forEach { line ->
            val index = Regex("""\[\s*(\d+)\s*]""").find(line)?.groupValues?.getOrNull(1) ?: return@forEach
            val tail = line.substringAfterLast(']').trim()
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
            if (hz in 1_000_000L..10_000_000_000L) result[hz] = index
        }
        return result
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
