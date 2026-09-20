package nd.max.core.atlas

import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.GpuHardwareBackend

/**
 * Reuse of the existing CPU/GPU backends as Atlas evidence (plan `P3`, slice `C`).
 *
 * The point of this file is that Atlas does **not** grow a second parser. `CpuHardwareBackend` and
 * `GpuHardwareBackend` already discover policy nodes, group legacy aliases, decode MediaTek OPP
 * tables and infer a frequency unit; duplicating that would create two answers to one question, and
 * the second one would drift. So the parsers are reused through read-only seams
 * (`CpuHardwareBackend.DiscoveryIo`, `GpuHardwareBackend.ReadIo`) and this class only **projects**
 * their output into Atlas vocabulary.
 *
 * Four rules are enforced here, and each one has a test:
 *
 * 1. **No writer participates.** The class holds a discovery reader and a read-only GPU reader. It
 *    cannot express a write: `GpuHardwareBackend.ReadIo` has no write member, and the CPU seam has no
 *    write member either. Running the GPU scanner through `ReadOnlyIo` is what makes the legacy
 *    selection code reusable without also reusing its writability probes — the probe is answered
 *    locally and never delegated, so discovery cannot even discover whether writing is possible.
 * 2. **Absence is proved, never assumed.** A missing attribute is reported `ABSENT` only when its
 *    parent directory was **listed and non-empty** and the name was not in it. Otherwise the cause is
 *    `UNKNOWN_CAUSE`, because "I could not look" is not "it is not there".
 * 3. **A heuristic never produces a reviewed unit.** The catalog's unit for `gpu.devfreq.cur_freq` is
 *    mainline Hz. If the driver's own evidence leaves the unit ambiguous, this class reports
 *    `AMBIGUOUS` and **no value**, rather than assuming Hz. When a vendor deviates from mainline, the
 *    scaled value is reported with `INFERRED` and the observed unit named in the reason — never
 *    silently.
 * 4. **Ambiguity survives.** When two GPU providers are equally proven, no provider is chosen and no
 *    value is claimed.
 *
 * **A gap this file records rather than hides:** the evidence vocabulary has no *list with a unit*
 * shape. `scaling_available_frequencies` and `available_frequencies` are lists of frequencies, not a
 * scalar and not free text, so they are carried in the projection below ([AtlasCpuPolicyFact],
 * [GpuHardwareBackend.GpuFact]) with their unit attached, and deliberately **not** flattened into a
 * fabricated single value.
 */
class AtlasBackendProvider(
    private val catalog: AtlasCatalog = AtlasReviewedSeeds.catalog(),
    private val elapsedMs: () -> Long,
    private val bootGeneration: Long = 0L,
    private val privilegeGeneration: Long = 0L,
    private val providerId: String = DEFAULT_PROVIDER_ID,
) {

    init {
        require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }
        require(providerId.isNotBlank()) { "the provider needs an id" }
        // Fail fast rather than silently reporting nothing: this class maps *these* entries, so a
        // catalog that lost one is a mismatch that must not turn into a quiet "not observed".
        MAPPED_ENTRY_IDS.forEach { id ->
            requireNotNull(catalog.byId(id)) { "catalog is missing the mapped entry: $id" }
        }
    }

    /** CPU evidence: one projection per discovered policy, plus a result per mapped attribute. */
    fun cpu(io: CpuHardwareBackend.DiscoveryIo = CpuHardwareBackend.SystemDiscoveryIo): AtlasBackendEvidence {
        val policies = CpuHardwareBackend.policies(io)
        if (policies.isEmpty()) {
            return AtlasBackendEvidence(
                domain = AtlasDomain.CPU,
                results = listOf(
                    rejection(
                        id = CPU_CUR_FREQ,
                        path = CPU_ROOT,
                        failure = AtlasFailure.BACKEND_UNAVAILABLE,
                        reason = "no cpufreq policy was discovered on this device",
                    ),
                ),
            )
        }
        val facts = policies.map { policy -> cpuFact(policy, io) }
        // A policy directory can be listed while none of its attributes answers (a partially
        // restricted policy). The parser drops such a policy because it has nothing to report, which
        // would make a whole cluster look like it does not exist. Comparing the root listing against
        // what was parsed keeps that cluster visible instead of absent.
        val discovered = policies.map { it.name }.toSet()
        val unreported = io.listDirectories(CPU_ROOT)
            .filter { it.matches(POLICY_DIRECTORY) && it !in discovered }
            .map { name ->
                rejection(
                    id = CPU_CUR_FREQ,
                    path = "$CPU_ROOT/$name/$ATTR_SCALING_CUR_FREQ",
                    failure = AtlasFailure.UNKNOWN_CAUSE,
                    reason = "the policy directory is listed but no attribute answered, so its state is " +
                        "unknown rather than absent",
                )
            }
        val results = policies.flatMap { policy -> cpuResults(policy, io) } + unreported
        return AtlasBackendEvidence(domain = AtlasDomain.CPU, results = results, cpuPolicies = facts)
    }

    /** GPU evidence: the read-only projection of the shipped selection code. */
    fun gpu(reads: GpuHardwareBackend.ReadIo = GpuHardwareBackend.SystemIo): AtlasBackendEvidence {
        val observation = GpuHardwareBackend.observe(reads)
        val facts = observation.devices
        if (facts.isEmpty()) {
            return AtlasBackendEvidence(
                domain = AtlasDomain.GPU,
                results = listOf(
                    rejection(
                        id = GPU_CUR_FREQ,
                        path = GPU_ROOT,
                        failure = AtlasFailure.BACKEND_UNAVAILABLE,
                        reason = observation.reason,
                    ),
                ),
                gpu = observation,
            )
        }
        val best = facts.firstOrNull { it.path == observation.bestPath }
        if (observation.state == GpuHardwareBackend.GpuObservationState.AMBIGUOUS || best == null) {
            return AtlasBackendEvidence(
                domain = AtlasDomain.GPU,
                results = facts.map { fact ->
                    rejection(
                        id = GPU_CUR_FREQ,
                        path = "${fact.path}/cur_freq",
                        failure = AtlasFailure.AMBIGUOUS,
                        reason = "${observation.reason}: ${facts.size} equally proven providers, so " +
                            "none is chosen",
                    )
                },
                gpu = observation,
            )
        }
        return AtlasBackendEvidence(
            domain = AtlasDomain.GPU,
            results = gpuResults(best),
            gpu = observation,
        )
    }

    // ---- CPU ---------------------------------------------------------------------------------------

    private fun cpuFact(policy: CpuHardwareBackend.Policy, io: CpuHardwareBackend.DiscoveryIo): AtlasCpuPolicyFact {
        val scalingCur = io.read("${policy.path}/$ATTR_SCALING_CUR_FREQ")?.trim()?.toLongOrNull()
        val cpuinfoCur = io.read("${policy.path}/$ATTR_CPUINFO_CUR_FREQ")?.trim()?.toLongOrNull()
        return AtlasCpuPolicyFact(
            name = policy.name,
            path = policy.path,
            governor = policy.governor,
            governors = policy.governors,
            minKHz = policy.minKHz,
            maxKHz = policy.maxKHz,
            // A hardware-declared bound is the driver's statement; an OPP-list end point is the best
            // available inference. The flag records which of the two this is, so a caller can present
            // them differently instead of printing one as if it were the other.
            provenMinKHz = policy.provenMinKHz,
            provenMaxKHz = policy.provenMaxKHz,
            boundsDeclaredByKernel = policy.hwMinKHz != null && policy.hwMaxKHz != null,
            frequencyLadderKHz = policy.availableFrequenciesKHz,
            currentKHz = scalingCur ?: cpuinfoCur,
            currentSource = when {
                scalingCur != null -> AtlasCpuFrequencySource.SCALING_CUR_FREQ
                cpuinfoCur != null -> AtlasCpuFrequencySource.CPUINFO_CUR_FREQ
                else -> AtlasCpuFrequencySource.NONE
            },
        )
    }

    private fun cpuResults(
        policy: CpuHardwareBackend.Policy,
        io: CpuHardwareBackend.DiscoveryIo,
    ): List<AtlasReadResult> = listOf(
        // The attribute is read by name, not through the parser's fallback, so the value is never
        // attributed to an interface that did not answer. The fallback still exists in the product
        // for display, and it is recorded in the projection instead.
        scalarResult(
            id = CPU_CUR_FREQ,
            path = "${policy.path}/$ATTR_SCALING_CUR_FREQ",
            io = io,
            note = "kHz, scaling policy's current request",
        ),
        // A list, so it is projected rather than flattened (`AtlasCpuPolicyFact.frequencyLadderKHz`).
        textResult(
            id = CPU_GOVERNORS,
            path = "${policy.path}/$ATTR_SCALING_AVAILABLE_GOVERNORS",
            io = io,
        ),
        textResult(
            id = CPU_RELATED_CPUS,
            path = "${policy.path}/$ATTR_RELATED_CPUS",
            io = io,
        ),
    )

    // ---- GPU ---------------------------------------------------------------------------------------

    private fun gpuResults(fact: GpuHardwareBackend.GpuFact): List<AtlasReadResult> {
        val entry = entryOf(GPU_CUR_FREQ)
        if (!fact.unitTrusted) {
            return listOf(
                rejection(
                    id = GPU_CUR_FREQ,
                    path = "${fact.path}/$ATTR_GPU_CUR_FREQ",
                    failure = AtlasFailure.AMBIGUOUS,
                    reason = "the driver exposed no consistent frequency unit, and mainline ${entry.unit} " +
                        "is not assumed from magnitude",
                ),
            )
        }
        val raw = fact.currentFreq
            ?: return listOf(
                rejection(
                    id = GPU_CUR_FREQ,
                    path = "${fact.path}/$ATTR_GPU_CUR_FREQ",
                    failure = if (fact.frequencies.isEmpty()) {
                        AtlasFailure.UNKNOWN_CAUSE
                    } else {
                        AtlasFailure.ABSENT
                    },
                    reason = if (fact.frequencies.isEmpty()) {
                        "no frequency reading answered and no OPP table was available to prove absence"
                    } else {
                        "the provider advertises ${fact.frequencies.size} steps but reports no current clock"
                    },
                ),
            )
        val multiplier = fact.frequencyUnit.hzMultiplier
        val deviation = fact.frequencyUnit != GpuHardwareBackend.FrequencyUnit.HZ
        return listOf(
            AtlasReadResult.Observed(
                observation(
                    id = GPU_CUR_FREQ,
                    path = "${fact.path}/$ATTR_GPU_CUR_FREQ",
                    unit = entry.unit,
                    value = raw.toDouble() * multiplier,
                    semanticStatus = if (deviation) AtlasSemanticStatus.INFERRED else AtlasSemanticStatus.REVIEWED_MATCH,
                    reason = if (deviation) {
                        "mainline ${entry.unit} but this driver reports ${fact.frequencyUnit.name.lowercase()}; " +
                            "scaled and kept inferred"
                    } else {
                        "driver reports ${fact.frequencyUnit.name.lowercase()}, matching the reviewed ABI unit"
                    },
                    sourceId = entry.provenance.sourceId,
                ),
            ),
        )
    }

    // ---- shared helpers ----------------------------------------------------------------------------

    private fun scalarResult(
        id: String,
        path: String,
        io: CpuHardwareBackend.DiscoveryIo,
        note: String,
    ): AtlasReadResult {
        val entry = entryOf(id)
        val raw = io.read(path)
            ?: return unreadFailure(id, path, io)
        val parsed = raw.trim().toDoubleOrNull()
            ?: return rejection(
                id = id,
                path = path,
                failure = AtlasFailure.MALFORMED,
                reason = "the interface answered with text that is not a number; the raw text is kept",
                raw = raw,
            )
        return AtlasReadResult.Observed(
            observation(
                id = id,
                path = path,
                unit = entry.unit,
                value = parsed,
                semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
                reason = note,
                sourceId = entry.provenance.sourceId,
                raw = raw,
            ),
        )
    }

    private fun textResult(id: String, path: String, io: CpuHardwareBackend.DiscoveryIo): AtlasReadResult {
        val entry = entryOf(id)
        val raw = io.read(path)
            ?: return unreadFailure(id, path, io)
        val text = raw.trim()
        if (text.isEmpty()) {
            return rejection(
                id = id,
                path = path,
                failure = AtlasFailure.MALFORMED,
                reason = "the interface answered with blank text",
                raw = raw,
            )
        }
        return AtlasReadResult.Observed(
            observation(
                id = id,
                path = path,
                unit = AtlasUnit.UNKNOWN,
                text = text,
                semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
                reason = "free text as the driver wrote it; no field is promoted to a typed value",
                sourceId = entry.provenance.sourceId,
                raw = raw,
            ),
        )
    }

    /**
     * A failure to obtain a value, with the strongest cause the two reads we are allowed can establish.
     *
     * The rule mirrors the boundary's: absence needs a **non-empty listing** of the parent that does
     * not contain the name. An empty listing is indistinguishable from a failed one here, so it can
     * never be used to conclude absence.
     */
    private fun unreadFailure(
        id: String,
        path: String,
        io: CpuHardwareBackend.DiscoveryIo,
    ): AtlasReadResult {
        val parent = path.substringBeforeLast('/')
        val name = path.substringAfterLast('/')
        val names = io.listDirectories(parent)
        return when {
            names.isEmpty() -> rejection(
                id = id,
                path = path,
                failure = AtlasFailure.UNKNOWN_CAUSE,
                reason = "the interface returned no value and its parent could not be listed, so absence " +
                    "is not claimed",
            )

            name !in names -> rejection(
                id = id,
                path = path,
                failure = AtlasFailure.ABSENT,
                reason = "the parent listing does not contain this attribute",
            )

            else -> rejection(
                id = id,
                path = path,
                failure = AtlasFailure.UNKNOWN_CAUSE,
                reason = "the attribute is present in the listing but returned no value",
            )
        }
    }

    private fun observation(
        id: String,
        path: String,
        unit: AtlasUnit,
        value: Double? = null,
        text: String? = null,
        semanticStatus: AtlasSemanticStatus,
        reason: String,
        sourceId: String,
        raw: String? = null,
    ): AtlasObservation = AtlasObservation(
        id = id,
        domain = if (id.startsWith("gpu.")) AtlasDomain.GPU else AtlasDomain.CPU,
        providerId = providerId,
        catalogVersion = catalog.version,
        sourceId = sourceId,
        path = path,
        access = AtlasAccess.READABLE,
        semanticStatus = semanticStatus,
        unit = unit,
        value = value,
        textValue = text,
        rawRepresentation = raw,
        failure = AtlasFailure.NONE,
        reason = reason,
        observedAtElapsedMs = elapsedMs(),
        bootGeneration = bootGeneration,
        privilegeGeneration = privilegeGeneration,
        truncated = false,
    )

    private fun rejection(
        id: String,
        path: String,
        failure: AtlasFailure,
        reason: String,
        raw: String? = null,
    ): AtlasReadResult = AtlasReadResult.Rejected(
        path = path,
        failure = failure,
        reason = if (raw == null) reason else "$reason (raw: ${raw.take(MAX_RAW_PREVIEW)})",
    )

    private fun entryOf(id: String): AtlasCatalogEntry =
        catalog.byId(id) ?: error("catalog is missing the mapped entry: $id")

    companion object {
        const val DEFAULT_PROVIDER_ID: String = "backend-reuse"

        /** The catalog entries this provider maps. Construction fails if any is missing. */
        val MAPPED_ENTRY_IDS: List<String> = listOf(
            "cpu.policy.scaling_cur_freq",
            "cpu.policy.scaling_available_governors",
            "cpu.policy.related_cpus",
            "gpu.devfreq.cur_freq",
        )

        const val CPU_ROOT: String = "/sys/devices/system/cpu/cpufreq"
        const val GPU_ROOT: String = "/sys/class/devfreq"

        const val ATTR_SCALING_CUR_FREQ: String = "scaling_cur_freq"
        const val ATTR_CPUINFO_CUR_FREQ: String = "cpuinfo_cur_freq"
        const val ATTR_SCALING_AVAILABLE_GOVERNORS: String = "scaling_available_governors"
        const val ATTR_RELATED_CPUS: String = "related_cpus"
        const val ATTR_GPU_CUR_FREQ: String = "cur_freq"

        private const val CPU_CUR_FREQ = "cpu.policy.scaling_cur_freq"
        private const val CPU_GOVERNORS = "cpu.policy.scaling_available_governors"
        private const val CPU_RELATED_CPUS = "cpu.policy.related_cpus"
        private const val GPU_CUR_FREQ = "gpu.devfreq.cur_freq"

        private const val MAX_RAW_PREVIEW = 48

        /** The canonical cpufreq policy directory shape, same rule the backend parser uses. */
        private val POLICY_DIRECTORY = Regex("policy\\d+")
    }
}

/** Where a CPU policy's current clock came from. Two different interfaces are two different facts. */
enum class AtlasCpuFrequencySource { SCALING_CUR_FREQ, CPUINFO_CUR_FREQ, NONE }

/**
 * A CPU policy as a **projection**: parsed values with their units, and no control field of any kind.
 *
 * The ladder is here, not flattened into an observation, because the evidence vocabulary has no
 * "list of numbers with a unit" shape yet. That is a recorded gap, not a reason to invent a scalar.
 */
data class AtlasCpuPolicyFact(
    val name: String,
    val path: String,
    val governor: String?,
    val governors: List<String>,
    val minKHz: Long?,
    val maxKHz: Long?,
    val provenMinKHz: Long?,
    val provenMaxKHz: Long?,
    /** True when the kernel declared the bounds; false when they came from the OPP list end points. */
    val boundsDeclaredByKernel: Boolean,
    val frequencyLadderKHz: List<Long>,
    val currentKHz: Long?,
    val currentSource: AtlasCpuFrequencySource,
) {
    /** A missing ladder permits observation and never invents an OPP. */
    val hasLadder: Boolean get() = frequencyLadderKHz.isNotEmpty()
}

/** What one domain's backend reuse produced: evidence, plus the projection it was derived from. */
data class AtlasBackendEvidence(
    val domain: AtlasDomain,
    val results: List<AtlasReadResult>,
    val cpuPolicies: List<AtlasCpuPolicyFact> = emptyList(),
    val gpu: GpuHardwareBackend.GpuObservation? = null,
) {
    val observed: List<AtlasObservation> get() = results.filterIsInstance<AtlasReadResult.Observed>().map { it.observation }
    val rejected: List<AtlasReadResult.Rejected> get() = results.filterIsInstance<AtlasReadResult.Rejected>()
}
