package nd.max.core.atlas

/**
 * Max Atlas evidence vocabulary (plan `P1`, slice `A`).
 *
 * An observation is *evidence about a device interface*, never permission to use it. Nothing in
 * this file can authorize a control action and no type here carries control authority: the
 * existing control plane keeps ownership of canonical keys, locks, baselines, readback and
 * rollback. Atlas only records what it read, from where, with which unit and how sure it is.
 *
 * Two rules shape every type below:
 * 1. Unknown stays unknown. A missing, malformed, truncated or unreadable value never becomes a
 *    plausible number, and a heuristic never upgrades itself to a reviewed match.
 * 2. Failure causes are distinct. "Absent", "denied", "read-only", "backend unavailable" and
 *    "timed out" are different facts; an unrecognized error is `UNKNOWN_CAUSE`, not "absent".
 */

/** Domains Atlas may describe. Domains without a backend stay visible as deferred with a reason. */
enum class AtlasDomain { CPU, GPU, THERMAL, MEMORY, POWER, DISPLAY, SENSOR, STORAGE, NETWORK, PRIVILEGE }

/** Where a candidate came from. Generic entries must still apply to an unknown device. */
enum class AtlasProviderKind {
    GENERIC,
    VENDOR,
    PLATFORM,
}

/**
 * Atlas has exactly one safety class. Anything else is out of scope until it is designed and
 * reviewed on its own, so this type must not grow a "can change" member by accident.
 */
enum class AtlasSafetyClass { READ_ONLY }

/** How strongly the *meaning* of a value is established. Only reviewed matches claim ABI semantics. */
enum class AtlasSemanticStatus {
    /** The interface matches a reviewed source (ABI documentation or inspected driver contract). */
    REVIEWED_MATCH,

    /** Meaning inferred from a heuristic or a partial match. Kept as evidence, never promoted. */
    INFERRED,

    /** Nothing established the meaning; the raw text may still be worth reporting. */
    UNKNOWN,
}

/** Unit identity. Values of different units are not interchangeable and are not scaled by magnitude. */
enum class AtlasUnit {
    HERTZ,
    KILO_HERTZ,
    MEGA_HERTZ,
    CELSIUS,
    MILLI_CELSIUS,
    DECI_CELSIUS,
    MICRO_AMP,
    MICRO_VOLT,
    MICRO_AMP_HOUR,
    MICRO_WATT_HOUR,
    BYTES,
    COUNT,
    PERCENT,
    UNKNOWN,
}

/** Access-mode *observation* for a specific attempt. It is not a capability statement. */
enum class AtlasAccess {
    /** A positive proof of absence for this attempt (the parent was readable and listed). */
    ABSENT,

    /** The path exists but this attempt could not read it. */
    EXISTS_NOT_READABLE,

    /** The value was read. Whether its meaning is established is a separate axis. */
    READABLE,

    /** The attempt could not establish even existence; do not report this as absence. */
    UNKNOWN,
}

/** Distinguishable failure causes. Never collapse two of these into one another. */
enum class AtlasFailure {
    NONE,
    ABSENT,
    PERMISSION_DENIED,
    READ_ONLY,
    MALFORMED,
    AMBIGUOUS,
    BACKEND_UNAVAILABLE,
    STALE,
    TIMED_OUT,
    BUDGET_EXCEEDED,
    CANCELLED,
    UNKNOWN_CAUSE,
}

/** Provenance confidence, mirroring the legend used by the phase source matrix (F/R/S/N/D), plus
 *  one member the matrix did not have:
 *
 *  - **[REPORTED]**: what a person observed on a specific device (a bug report, a user measurement).
 *    It is *not* a lower rank of `FETCHED` — a fetch asserts nothing while a report asserts a real
 *    behavior — and it is *not* `SOURCE_VERIFIED`: nobody who could inspect the implementation has
 *    confirmed it, and it speaks about one device, not about the interface in general.
 *
 *  That distinction exists because the model forbids one specific mistake: a human report silently
 *  becoming a reviewed fact. [AtlasConfidenceRules] keeps the order in one place, and
 *  [AtlasQuirkBase] may only ever **lower** along it. */
enum class AtlasSourceConfidence {
    /** Fetched, but fetching alone validates nothing about the claims. */
    FETCHED,

    /** Documentation claims it; it was not executed or reproduced. */
    CLAIMED,

    /** The named source was inspected and implements the described behavior. */
    SOURCE_VERIFIED,

    /** Observed by a person on one device. Weaker than an inspected source, stronger than a fetch. */
    REPORTED,

    /** No implementation-level assertion is supported. */
    NOT_INSPECTED,

    /** Independently authored design choice, not a measured property of anything. */
    DESIGN,
}

/**
 * ترتيب الثقة في مكان **واحد** (I-17).
 *
 * ولماذا دالّة ترتيب لا مقارنة ذهنية في كل موضع: التعديل الوحيد المسموح على ثقة معرفة قائمة هو
 * **الخفض**. فوجود الترتيب هنا يجعل القاعدة قابلة للاختبار (و[AtlasQuirkBase] يختبرها)، بدلًا من
 * أن تُصبح عُرفًا يُنسى في موضع واحد فيرتفع ادّعاء بلاغ إلى «مصدر مُتحقَّق».
 *
 * و`NOT_INSPECTED` و`DESIGN` في الرتبة نفسها: كلتاهما **لا تقول شيئًا عن جهاز** (إحداهما غياب فحص،
 * والأخرى اختيار تصميم)، فلا تصلح أيّ منهما سندًا لادّعاء عن العتاد.
 */
object AtlasConfidenceRules {

    fun rank(confidence: AtlasSourceConfidence): Int = when (confidence) {
        AtlasSourceConfidence.SOURCE_VERIFIED -> 4
        AtlasSourceConfidence.CLAIMED -> 3
        AtlasSourceConfidence.REPORTED -> 2
        AtlasSourceConfidence.FETCHED -> 1
        AtlasSourceConfidence.NOT_INSPECTED -> 0
        AtlasSourceConfidence.DESIGN -> 0
    }

    /** الأدنى من الاثنتين — ولا يرفع أبدًا، فاستعماله لا يمكن أن يُنتج ادّعاءً أقوى. */
    fun lower(
        current: AtlasSourceConfidence,
        ceiling: AtlasSourceConfidence,
    ): AtlasSourceConfidence = if (rank(current) <= rank(ceiling)) current else ceiling
}

/**
 * Where a catalog entry came from. An entry without provenance is not admissible knowledge, so the
 * catalog validator rejects blank fields instead of accepting an anonymous claim.
 */
data class AtlasProvenance(
    val sourceId: String,
    val reference: String,
    val revision: String,
    val licenseNote: String,
    val confidence: AtlasSourceConfidence,
) {
    init {
        require(sourceId.isNotBlank()) { "provenance needs a source id" }
        require(reference.isNotBlank()) { "provenance needs a reference" }
        require(licenseNote.isNotBlank()) { "provenance needs a license note" }
        if (confidence != AtlasSourceConfidence.DESIGN) {
            require(revision.isNotBlank()) { "a non-design claim needs an observed revision" }
        }
    }
}

/**
 * One attempt's evidence.
 *
 * The axes are deliberately orthogonal: [access], [semanticStatus], [failure] and freshness answer
 * different questions. Readability is not capability, a reviewed match is not a control, and an
 * observation vintage is not live telemetry.
 */
data class AtlasObservation(
    val id: String,
    val domain: AtlasDomain,
    val providerId: String,
    val catalogVersion: String,
    val sourceId: String,
    val path: String,
    val access: AtlasAccess,
    val semanticStatus: AtlasSemanticStatus,
    val unit: AtlasUnit,
    val value: Double?,
    val textValue: String?,
    val rawRepresentation: String?,
    val failure: AtlasFailure,
    val reason: String,
    val observedAtElapsedMs: Long?,
    val bootGeneration: Long,
    val privilegeGeneration: Long,
    val truncated: Boolean,
) {
    init {
        require(AtlasIds.isValidObservationId(id)) { "observation id is not canonical: $id" }
        require(providerId.isNotBlank()) { "observation needs a provider id" }
        require(catalogVersion.isNotBlank()) { "observation needs a catalog version" }
        require(path.startsWith("/")) { "observation path must be absolute: $path" }
        require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }
        require(observedAtElapsedMs == null || observedAtElapsedMs >= 0L) { "elapsed time is non-negative" }
        if (failure == AtlasFailure.NONE) {
            require(access == AtlasAccess.READABLE) { "a successful observation must be readable" }
            require(!truncated) { "a truncated read is never a successful observation" }
            require((value != null) xor (textValue != null)) { "success carries exactly one of value/textValue" }
            if (value != null) {
                require(unit != AtlasUnit.UNKNOWN) { "a numeric value needs a known unit" }
                require(value.isFinite()) { "NaN and infinity are not observations" }
            }
            if (textValue != null) {
                require(unit == AtlasUnit.UNKNOWN) { "free text has no numeric unit" }
            }
        } else {
            require(value == null && textValue == null) { "a failed attempt carries no parsed value" }
        }
        if (truncated) {
            require(failure == AtlasFailure.BUDGET_EXCEEDED || failure == AtlasFailure.MALFORMED) {
                "truncation is a budget or malformed outcome, not a silent partial value"
            }
        }
    }
}

/**
 * What one attempt is allowed to ask for.
 *
 * `requiresPrivilege` is a *request property*, not a permission: when no authorized privileged
 * transport exists the attempt returns `BACKEND_UNAVAILABLE` and nothing is prompted.
 * `reviewedProcSummary` selects the larger byte allowance that `P1`'s catalog grants only to
 * explicitly reviewed `/proc` summaries.
 */
data class AtlasProbeRequest(
    val id: String,
    val domain: AtlasDomain,
    val providerId: String,
    val catalogVersion: String,
    val sourceId: String,
    val path: String,
    val unit: AtlasUnit,
    val semanticStatus: AtlasSemanticStatus = AtlasSemanticStatus.UNKNOWN,
    val requiresPrivilege: Boolean = false,
    val reviewedProcSummary: Boolean = false,
) {
    init {
        require(AtlasIds.isValidObservationId(id)) { "probe id is not canonical: $id" }
        require(providerId.isNotBlank()) { "probe needs a provider id" }
        require(catalogVersion.isNotBlank()) { "probe needs a catalog version" }
        require(sourceId.isNotBlank()) { "probe needs a source id" }
    }
}

/** Typed result of one read attempt. The cause survives all the way to the reader. */
sealed interface AtlasReadResult {
    val path: String

    data class Observed(val observation: AtlasObservation) : AtlasReadResult {
        init {
            require(observation.failure == AtlasFailure.NONE) { "Observed requires a successful observation" }
        }

        override val path: String get() = observation.path
    }

    /** A failed attempt with its cause preserved. `NONE` is not a failure and is rejected here. */
    data class Rejected(
        override val path: String,
        val failure: AtlasFailure,
        val reason: String,
    ) : AtlasReadResult {
        init {
            require(failure != AtlasFailure.NONE) { "Rejected must carry a real cause" }
            require(reason.isNotBlank()) { "Rejected must say why" }
        }
    }
}

/**
 * One directory attempt. Names are returned only when they were actually read; `truncated` marks
 * partial coverage so unvisited entries are never presented as absent.
 */
data class AtlasDirectoryListing(
    val path: String,
    val names: List<String>,
    val truncated: Boolean,
    val failure: AtlasFailure,
    val reason: String,
) {
    init {
        require(path.startsWith("/")) { "listing path must be absolute: $path" }
        require(names == names.distinct().sorted()) { "listing names must be distinct and sorted" }
        names.forEach { name ->
            require(AtlasIds.isSafeBasename(name)) { "listing returned an unsafe name: $name" }
        }
        if (failure != AtlasFailure.NONE) {
            require(names.isEmpty()) { "a failed listing returns no names" }
            require(!truncated) { "a failed listing is not described as truncated" }
        }
        if (truncated) {
            require(reason.isNotBlank()) { "truncated coverage must state the limit it reached" }
        }
    }
}

/** Canonical identifier and basename rules shared by the catalog, results and listings. */
object AtlasIds {
    private val OBSERVATION_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")
    private val BASENAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")

    fun isValidObservationId(id: String): Boolean = OBSERVATION_ID.matches(id)

    /** A safe basename has no separator, no traversal and no pattern syntax of its own. */
    fun isSafeBasename(name: String): Boolean = BASENAME.matches(name)

    /** A safe absolute template: absolute, no traversal, no whitespace and no pattern or shell syntax. */
    fun isSafeAbsolutePath(path: String): Boolean {
        if (!path.startsWith("/")) return false
        if (path.length > 256) return false
        if (path.contains("..")) return false
        if (path.endsWith("/")) return false
        return path.none { it.isWhitespace() || it in FORBIDDEN_PATH_CHARS }
    }

    private val FORBIDDEN_PATH_CHARS: Set<Char> =
        setOf('*', '?', '[', ']', '!', ';', '|', '&', '$', '`', '<', '>', '"', '\'', '\\', '(', ')', '{', '}', '~')
}

/**
 * Pure conversion rules. Each function returns `null` when the unit is not the dimension it
 * handles: an energy value is never converted into a charge, and a magnitude is never used to
 * guess an unspecified unit.
 */
object AtlasUnitRules {

    fun frequencyHertz(value: Double, unit: AtlasUnit): Double? = when (unit) {
        AtlasUnit.HERTZ -> value
        AtlasUnit.KILO_HERTZ -> value * 1_000.0
        AtlasUnit.MEGA_HERTZ -> value * 1_000_000.0
        else -> null
    }

    fun celsius(value: Double, unit: AtlasUnit): Double? = when (unit) {
        AtlasUnit.CELSIUS -> value
        AtlasUnit.MILLI_CELSIUS -> value / 1_000.0
        AtlasUnit.DECI_CELSIUS -> value / 10.0
        else -> null
    }

    /** Charge is measured in microamp-hours. */
    fun chargeMicroAmpHours(value: Double, unit: AtlasUnit): Double? =
        if (unit == AtlasUnit.MICRO_AMP_HOUR) value else null

    /** Energy is a different dimension and has no charge conversion here, by design. */
    fun energyMicroWattHours(value: Double, unit: AtlasUnit): Double? =
        if (unit == AtlasUnit.MICRO_WATT_HOUR) value else null

    fun isFrequency(unit: AtlasUnit): Boolean =
        unit == AtlasUnit.HERTZ || unit == AtlasUnit.KILO_HERTZ || unit == AtlasUnit.MEGA_HERTZ

    fun isTemperature(unit: AtlasUnit): Boolean =
        unit == AtlasUnit.CELSIUS || unit == AtlasUnit.MILLI_CELSIUS || unit == AtlasUnit.DECI_CELSIUS

    /**
     * Maps an explicit unit token to a unit. Unknown tokens stay `UNKNOWN`; this function never
     * infers a unit from the size of the number.
     */
    fun fromToken(token: String?): AtlasUnit = when (token?.trim()?.lowercase()) {
        "hz" -> AtlasUnit.HERTZ
        "khz" -> AtlasUnit.KILO_HERTZ
        "mhz" -> AtlasUnit.MEGA_HERTZ
        "c", "celsius", "degc" -> AtlasUnit.CELSIUS
        "mc", "millic", "millicelsius" -> AtlasUnit.MILLI_CELSIUS
        "dc", "decic", "decicelsius" -> AtlasUnit.DECI_CELSIUS
        "ua", "uamp", "microamp" -> AtlasUnit.MICRO_AMP
        "uv", "uvolt", "microvolt" -> AtlasUnit.MICRO_VOLT
        "uah", "microamphour" -> AtlasUnit.MICRO_AMP_HOUR
        "uwh", "microwatthour" -> AtlasUnit.MICRO_WATT_HOUR
        "b", "bytes" -> AtlasUnit.BYTES
        "count" -> AtlasUnit.COUNT
        "%", "percent" -> AtlasUnit.PERCENT
        else -> AtlasUnit.UNKNOWN
    }
}

/**
 * CPU list normalization. Kernels expose lists either as ranges (`0-3`), separated values
 * (`0 1 2`), or a mixture. Aliases are resolved into one canonical ascending set so a policy is not
 * counted twice, and a malformed list returns `null` rather than a silently smaller set.
 */
object AtlasCpuList {
    private val PART = Regex("^\\d+(\\s*-\\s*\\d+)?$")

    fun parse(raw: String?): List<Int>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val out = sortedSetOf<Int>()
        text.split(',', ' ', '\t', '\n').map(String::trim).filter { it.isNotEmpty() }.forEach { part ->
            if (!PART.matches(part)) return null
            val dash = part.indexOf('-')
            if (dash < 0) {
                out.add(part.toInt())
            } else {
                val from = part.substring(0, dash).trim().toInt()
                val to = part.substring(dash + 1).trim().toInt()
                if (from > to) return null
                if (to - from > MAX_RANGE_SPAN) return null
                for (cpu in from..to) out.add(cpu)
            }
        }
        return if (out.isEmpty()) null else out.toList()
    }

    /**
     * Policy identity helper: `related_cpus` includes offline CPUs and `affected_cpus` the online
     * ones, so the union is the policy's core set while each list keeps its own meaning.
     */
    fun policyCoreSet(relatedCpus: String?, affectedCpus: String?): List<Int>? {
        val related = parse(relatedCpus) ?: emptyList()
        val affected = parse(affectedCpus) ?: emptyList()
        if (related.isEmpty() && affected.isEmpty()) return null
        return (related + affected).distinct().sorted()
    }

    private const val MAX_RANGE_SPAN = 4_096
}
