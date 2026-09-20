package nd.max.core.atlas

/**
 * Non-CPU/GPU domain observations and the support matrix (plan `P4`, slice `D`).
 *
 * This file is **pure Kotlin over injected readings**. It does not import Android, does not open a
 * file, does not hold a view-model and does not know a control key, and that is not a promise in a
 * comment: `AtlasPlatformProviderTest` scans this source for `import android`, for every product
 * view-model name, for `Shell`/`chmod` and for `/data/adb`, and fails closed if one appears. The
 * adapter that fills [AtlasPlatformSource] from the platform (public battery API, `/proc`, `/sys`)
 * is wired by a later plan, exactly as the transport adapter of `P2` is.
 *
 * What this provider refuses to do, per the phase plan:
 * - **No invented value.** A missing or malformed reading is an observation with `null` and the raw
 *   text retained; it never becomes `0`, and the zero-filled fallbacks product screens use are not
 *   evidence.
 * - **No promoted meaning.** A broad `ap`/`tsens` zone token is `INFERRED`, never "CPU junction".
 * - **No merged dimensions.** Micro-amp, micro-volt, micro-amp-hour and micro-watt-hour stay
 *   separate; current keeps its raw polarity; energy is never turned into a charge.
 * - **No identity in the network rows.** Connected/metered/transport only: no address, no SSID, no
 *   MAC, no package name — the type cannot even carry one.
 * - **No root claim from existence.** Privilege is `DEFERRED`/`UNAVAILABLE` unless the existing
 *   control plane verified it; `File.exists` under `/data/adb` is not an observation.
 * - **No silent omission.** [AtlasSupportMatrix] cannot be built without a row for every domain, so
 *   a domain with no backend today stays visible as deferred with a reason code.
 */

/** How far a domain got. `PARTIAL` means both a real observation and a real gap exist. */
enum class AtlasSupportState { OBSERVED, PARTIAL, DEFERRED, UNAVAILABLE }

/** Stable reason codes. Every row states one; a deferred row's code is its whole explanation. */
object AtlasReasonCodes {
    const val NONE = "none"

    /** No adapter is wired for this domain yet (an honest default, not a device claim). */
    const val NO_SOURCE_WIRED = "no-source-wired"

    /** The domain is owned by another provider (P3 supplies CPU/GPU), so it is not omitted here. */
    const val OTHER_PROVIDER = "other-provider"

    /** A reading arrived and could not be interpreted; the raw text is retained with it. */
    const val MALFORMED = "malformed"

    /** Some fields were observed and some were not. */
    const val PARTIAL = "partial-support"

    /** Identity could not be verified by the existing control plane. */
    const val UNVERIFIED_IDENTITY = "unverified-identity"

    /** A source was wired and nothing could be observed through it. */
    const val NO_OBSERVATION = "no-observation"
}

// ---- raw readings: what a platform adapter may hand over, nothing more ------------------------------

/** The unit a source declares for a zone's `temp` attribute. Atlas never guesses it from magnitude. */
enum class AtlasThermalScale { CELSIUS, MILLI_CELSIUS }

data class AtlasThermalZone(
    val index: Int,
    val typeRaw: String?,
    val tempRaw: String?,
    val scale: AtlasThermalScale,
) {
    init {
        require(index >= 0) { "zone index is non-negative" }
    }
}

/**
 * Public-API battery values. Every field is a separate dimension: an engine that needs a charge may
 * not silently accept energy, and a discharging current stays negative.
 */
data class AtlasBatteryReading(
    val levelPercent: Int?,
    val statusRaw: String?,
    val currentMicroAmps: Long?,
    val voltageMicroVolts: Long?,
    val chargeCounterMicroAmpHours: Long?,
    val energyMicroWattHours: Long?,
    val present: Boolean?,
)

/** `/proc/meminfo` values arrive in KiB; PSI arrives as its own two-axis text form. */
data class AtlasMemoryReading(
    val memTotalKb: Long?,
    val memAvailableKb: Long?,
    val swapTotalKb: Long?,
    val swapFreeKb: Long?,
    val psiSomeRaw: String?,
    val psiFullRaw: String?,
)

data class AtlasZramReading(
    val name: String,
    val disksizeBytes: Long?,
    val memUsedBytes: Long?,
    val algorithm: String?,
)

data class AtlasDisplayReading(
    val widthPx: Int?,
    val heightPx: Int?,
    val refreshHzRaw: String?,
    val hdrTypes: List<String>?,
)

data class AtlasSensorReading(val kind: String, val count: Int?)

data class AtlasStorageReading(val dataFreeBytes: Long?, val dataTotalBytes: Long?)

/**
 * Network state without identity. There is deliberately no field for an address, SSID, MAC, BSSID or
 * package: a type that cannot carry them cannot leak them into a report later.
 */
data class AtlasNetworkReading(val connected: Boolean?, val metered: Boolean?, val transport: String?)

/** Identity verification comes from the existing control plane, not from a path check. */
enum class AtlasIdentityVerification { VERIFIED_BY_CONTROL_PLANE, UNVERIFIED }

data class AtlasPrivilegeReading(
    val rootManager: String?,
    val moduleVersion: String?,
    val verification: AtlasIdentityVerification,
)

/**
 * The platform surface Atlas may read from.
 *
 * A domain with no adapter returns empty/null here, and the matrix says so instead of guessing. The
 * interface is read-only by construction: every method returns readings, none changes anything.
 */
interface AtlasPlatformSource {
    fun thermalZones(): List<AtlasThermalZone> = emptyList()
    fun battery(): AtlasBatteryReading? = null
    fun memory(): AtlasMemoryReading? = null
    fun zramDevices(): List<AtlasZramReading> = emptyList()
    fun display(): AtlasDisplayReading? = null
    fun sensors(): List<AtlasSensorReading> = emptyList()
    fun storage(): AtlasStorageReading? = null
    fun network(): AtlasNetworkReading? = null
    fun privilege(): AtlasPrivilegeReading? = null

    /** Advisory device hints for catalog selection. Never a model-name allowlist. */
    fun vendorHints(): Set<String> = emptySet()
}

/** The honest default until the platform adapter is wired: nothing is known, nothing is claimed. */
object UnavailableAtlasPlatformSource : AtlasPlatformSource

// ---- the matrix --------------------------------------------------------------------------------------

/** One domain's row. An `OBSERVED`/`PARTIAL` row must carry evidence; a `DEFERRED` row must not. */
data class AtlasDomainSupport(
    val domain: AtlasDomain,
    val state: AtlasSupportState,
    val reason: String,
    val observations: List<AtlasObservation>,
    /** Reviewed entries the catalog holds for this domain, vendor-tagged ones included. */
    val catalogEntries: Int,
    /** Reviewed entries that actually apply to this device: generic + platform + matching vendor. */
    val matchedEntries: Int,
) {
    init {
        require(reason.isNotBlank()) { "every row states a reason (or ${AtlasReasonCodes.NONE})" }
        require(observations.all { it.domain == domain }) { "a row may only carry its own domain" }
        require(catalogEntries >= 0) { "catalog entry count is non-negative" }
        require(matchedEntries in 0..catalogEntries) {
            "$domain cannot match more entries ($matchedEntries) than the catalog holds ($catalogEntries)"
        }
        if (state == AtlasSupportState.OBSERVED || state == AtlasSupportState.PARTIAL) {
            require(observations.any { it.failure == AtlasFailure.NONE }) {
                "$domain claims support without one successful observation"
            }
        }
        if (state == AtlasSupportState.DEFERRED) {
            require(observations.isEmpty()) { "$domain is deferred and cannot carry observations" }
        }
        if (state == AtlasSupportState.UNAVAILABLE || state == AtlasSupportState.PARTIAL) {
            require(observations.any { it.failure != AtlasFailure.NONE }) {
                "$domain claims a gap without recording one"
            }
        }
    }
}

/**
 * The support matrix artifact. It **cannot** be built with a domain missing: that constraint is what
 * makes "the domain has no backend yet" visible as a deferred row instead of an omission.
 */
data class AtlasSupportMatrix(
    val catalogVersion: String,
    val supports: List<AtlasDomainSupport>,
) {
    private val byDomain: Map<AtlasDomain, AtlasDomainSupport> = supports.associateBy(AtlasDomainSupport::domain)

    init {
        require(catalogVersion.isNotBlank()) { "the matrix names the catalog version it was built against" }
        require(supports.map { it.domain }.distinct().size == supports.size) { "one row per domain" }
        require(supports.map { it.domain }.sortedBy(AtlasDomain::ordinal) == supports.map { it.domain }) {
            "rows are ordered by domain so two runs are comparable"
        }
        val missing = AtlasDomain.entries.toSet() - byDomain.keys
        require(missing.isEmpty()) { "the matrix must account for every domain; missing: $missing" }
    }

    fun byDomain(domain: AtlasDomain): AtlasDomainSupport = byDomain.getValue(domain)

    fun stateOf(domain: AtlasDomain): AtlasSupportState = byDomain(domain).state

    /** Domains that are not fully observed, with the code explaining why. */
    fun gaps(): Map<AtlasDomain, String> = supports
        .filter { it.state != AtlasSupportState.OBSERVED }
        .associate { it.domain to it.reason }

    /** Every observation in the matrix, in row order, for a report or a test to walk. */
    fun observations(): List<AtlasObservation> = supports.flatMap(AtlasDomainSupport::observations)
}

// ---- parsers -----------------------------------------------------------------------------------------

/**
 * Pure conversions. Each function returns `null` when the input cannot be interpreted; none of them
 * substitutes a default, because a default is exactly how a broken interface becomes a plausible
 * reading.
 */
object AtlasPlatformParsers {

    fun decimal(raw: String?): Double? =
        raw?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** `/proc/meminfo` reports KiB. The conversion is explicit and named so nobody re-guesses it. */
    fun kilobytesToBytes(kilobytes: Long?): Long? = kilobytes?.takeIf { it >= 0L }?.let { it * 1024L }

    /** One key of a PSI line: `some avg10=1.23 avg60=0.45 avg300=0.10 total=12345`. */
    fun psiField(raw: String?, field: String): Double? {
        if (raw == null) return null
        val axis = if (raw.trimStart().startsWith("some")) "some" else if (raw.trimStart().startsWith("full")) "full" else ""
        val line = if (axis.isEmpty()) raw else raw.substringAfter(axis, missingDelimiterValue = raw)
        return line.split(' ')
            .firstOrNull { it.startsWith("$field=") }
            ?.removePrefix("$field=")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }
    }

    /** `avg10` is a percentage in the kernel's text form; a malformed field yields `null`. */
    fun psiAverage(raw: String?, field: String = "avg10"): Double? = psiField(raw, field)

    /** `total` is cumulative stall time in the kernel's own units, reported here as a count of them. */
    fun psiTotal(raw: String?): Double? = psiField(raw, "total")

    fun psiIsWellFormed(raw: String?): Boolean =
        raw != null && psiAverage(raw) != null && psiTotal(raw) != null
}

/** Reviewed zone-type tokens. Only the kernel ABI's own names claim a junction sensor. */
object AtlasThermalTypes {

    /**
     * The Linux thermal sysfs ABI names these zone types. A token found here means the meaning is
     * established; anything else is a vendor hint, and a vendor hint is never a CPU junction.
     */
    private val reviewed: Map<String, String> = mapOf(
        "cpu" to "cpu junction",
        "gpu" to "gpu junction",
        "battery" to "battery cell",
    )

    /** Tokens seen in the wild that describe *something*, but not a CPU junction. */
    private val vendorHints: Set<String> = setOf(
        "ap",
        "tsens",
        "soc_thermal",
        "soc-thermal",
        "quiet_therm",
        "quiet-therm",
        "pa",
        "charger",
        "skin",
        "modem",
    )

    data class Classification(val semantic: AtlasSemanticStatus, val label: String)

    fun classify(typeRaw: String?): Classification {
        val token = typeRaw?.trim()?.lowercase().orEmpty()
        reviewed[token]?.let { return Classification(AtlasSemanticStatus.REVIEWED_MATCH, it) }
        if (token.isEmpty()) return Classification(AtlasSemanticStatus.UNKNOWN, "unnamed zone")
        if (token in vendorHints) return Classification(AtlasSemanticStatus.INFERRED, "vendor hint; identity not established")
        return Classification(AtlasSemanticStatus.INFERRED, "unreviewed token")
    }
}

// ---- the provider ------------------------------------------------------------------------------------

/**
 * Builds the support matrix from one injected source.
 *
 * @param clockMs monotonic milliseconds for observation vintage. Injected, so tests are exact.
 * @param catalog the reviewed catalog, used only to count the reviewed entries per domain.
 * @param identity the declared device identity (`P10`), used **only** to widen catalog selection with
 *   vendor tags. It cannot narrow selection, and it cannot make an entry readable; an inapplicable
 *   vendor entry simply reads and fails, and the failure is recorded as evidence.
 */
class AtlasPlatformProvider(
    private val source: AtlasPlatformSource = UnavailableAtlasPlatformSource,
    private val catalogVersion: String = AtlasCatalog.SCHEMA_VERSION,
    private val providerId: String = "atlas-platform-1",
    private val catalog: AtlasCatalog = AtlasReviewedSeeds.catalog(),
    private val clockMs: () -> Long = { 0L },
    private val identity: AtlasDeviceIdentity? = null,
) {

    /**
     * Device hints for this provider: what the source observed, plus what the platform declared.
     *
     * The two are unioned rather than merged, so a declaration can only ever add candidates, and an
     * unknown vendor loses the vendor entries and keeps every generic one.
     */
    private val deviceHints: Set<String> = source.vendorHints() + identity?.vendorHints().orEmpty()

    fun matrix(): AtlasSupportMatrix =
        AtlasSupportMatrix(catalogVersion = catalogVersion, supports = AtlasDomain.entries.map(::row))

    private fun row(domain: AtlasDomain): AtlasDomainSupport {
        val observations = observationsFor(domain)
        // `byDomain` excludes vendor-tagged entries (it selects with no device hint), so counting all
        // entries for the domain has to be done explicitly — otherwise a matched vendor entry would
        // look like more knowledge than the catalog holds.
        val entries = catalog.entries.count { it.domain == domain }
        val matched = catalog.candidates(domain, deviceHints).size
        if (observations.isEmpty()) {
            return AtlasDomainSupport(
                domain = domain,
                state = AtlasSupportState.DEFERRED,
                reason = deferredReason(domain),
                observations = emptyList(),
                catalogEntries = entries,
                matchedEntries = matched,
            )
        }
        val succeeded = observations.any { it.failure == AtlasFailure.NONE }
        val failed = observations.any { it.failure != AtlasFailure.NONE }
        val state = when {
            succeeded && failed -> AtlasSupportState.PARTIAL
            succeeded -> AtlasSupportState.OBSERVED
            else -> AtlasSupportState.UNAVAILABLE
        }
        val reason = when (state) {
            AtlasSupportState.OBSERVED -> AtlasReasonCodes.NONE
            AtlasSupportState.PARTIAL -> AtlasReasonCodes.PARTIAL
            else -> AtlasReasonCodes.NO_OBSERVATION
        }
        return AtlasDomainSupport(domain, state, reason, observations, entries, matched)
    }

    /** CPU/GPU are another provider's job; they are named here so the matrix stays complete. */
    private fun deferredReason(domain: AtlasDomain): String = when (domain) {
        AtlasDomain.CPU, AtlasDomain.GPU -> AtlasReasonCodes.OTHER_PROVIDER
        else -> AtlasReasonCodes.NO_SOURCE_WIRED
    }

    private fun observationsFor(domain: AtlasDomain): List<AtlasObservation> = when (domain) {
        AtlasDomain.THERMAL -> thermal()
        AtlasDomain.POWER -> power()
        AtlasDomain.MEMORY -> memory()
        AtlasDomain.STORAGE -> storage()
        AtlasDomain.DISPLAY -> display()
        AtlasDomain.SENSOR -> sensors()
        AtlasDomain.NETWORK -> network()
        AtlasDomain.PRIVILEGE -> privilege()
        AtlasDomain.CPU, AtlasDomain.GPU -> emptyList()
    }

    // ---- thermal (T4.1) -----------------------------------------------------------------------------

    private fun thermal(): List<AtlasObservation> = source.thermalZones().flatMap { zone ->
        val classification = AtlasThermalTypes.classify(zone.typeRaw)
        val unit = when (zone.scale) {
            AtlasThermalScale.CELSIUS -> AtlasUnit.CELSIUS
            AtlasThermalScale.MILLI_CELSIUS -> AtlasUnit.MILLI_CELSIUS
        }
        val base = "/sys/class/thermal/thermal_zone${zone.index}"
        val typeRow = when (val type = zone.typeRaw?.takeIf { it.isNotBlank() }) {
            null -> missing("thermal.zone${zone.index}.type", AtlasDomain.THERMAL, "$base/type", "the zone reports no type")
            else -> text(
                id = "thermal.zone${zone.index}.type",
                domain = AtlasDomain.THERMAL,
                path = "$base/type",
                semantic = classification.semantic,
                text = type,
                raw = type,
                note = "zone type: ${classification.label}",
            )
        }
        val tempRow = when (val temp = AtlasPlatformParsers.decimal(zone.tempRaw)) {
            null -> malformed(
                id = "thermal.zone${zone.index}.temp",
                domain = AtlasDomain.THERMAL,
                path = "$base/temp",
                raw = zone.tempRaw,
                why = "temperature was not a finite number; raw retained and never recorded as 0",
            )

            else -> number(
                id = "thermal.zone${zone.index}.temp",
                domain = AtlasDomain.THERMAL,
                path = "$base/temp",
                unit = unit,
                value = temp,
                note = "temperature in the unit the zone declared; the scale is never inferred from magnitude",
                semantic = classification.semantic,
            )
        }
        listOf(typeRow, tempRow)
    }

    // ---- power (T4.2) -------------------------------------------------------------------------------

    private fun power(): List<AtlasObservation> {
        val reading = source.battery() ?: return emptyList()
        val root = "/android/api/power/battery"
        val rows = mutableListOf<AtlasObservation>()

        rows += reading.levelPercent?.let { level ->
            number("power.level", AtlasDomain.POWER, "$root/level", AtlasUnit.PERCENT, level.toDouble(), "state of charge as the public API reports it")
        } ?: missing("power.level", AtlasDomain.POWER, "$root/level", "level was not reported")

        reading.statusRaw?.takeIf { it.isNotBlank() }?.let { status ->
            rows += text(
                id = "power.status",
                domain = AtlasDomain.POWER,
                path = "$root/status",
                semantic = AtlasSemanticStatus.UNKNOWN,
                text = status,
                raw = status,
                note = "charging status token; interpretation stays with the existing power screens",
            )
        }

        reading.currentMicroAmps?.let { current ->
            rows += number(
                id = "power.current_now",
                domain = AtlasDomain.POWER,
                path = "$root/current_now",
                unit = AtlasUnit.MICRO_AMP,
                value = current.toDouble(),
                note = "raw polarity preserved: a negative value means discharging, never zero",
            )
        }

        reading.voltageMicroVolts?.let { volts ->
            rows += number("power.voltage_now", AtlasDomain.POWER, "$root/voltage_now", AtlasUnit.MICRO_VOLT, volts.toDouble(), "terminal voltage")
        }

        reading.chargeCounterMicroAmpHours?.let { charge ->
            rows += number(
                id = "power.charge_counter",
                domain = AtlasDomain.POWER,
                path = "$root/charge_counter",
                unit = AtlasUnit.MICRO_AMP_HOUR,
                value = charge.toDouble(),
                note = "charge. A different dimension from energy below, deliberately",
            )
        }

        reading.energyMicroWattHours?.let { energy ->
            rows += number(
                id = "power.energy_now",
                domain = AtlasDomain.POWER,
                path = "$root/energy_now",
                unit = AtlasUnit.MICRO_WATT_HOUR,
                value = energy.toDouble(),
                note = "energy. Never converted into a charge without explicit voltage semantics",
            )
        }

        reading.present?.let { present ->
            rows += text(
                id = "power.present",
                domain = AtlasDomain.POWER,
                path = "$root/present",
                semantic = AtlasSemanticStatus.UNKNOWN,
                text = if (present) "present" else "absent",
                raw = present.toString(),
                note = "presence of a battery, not of a charger",
            )
        }

        if (rows.isEmpty()) {
            rows += missing("power.reading", AtlasDomain.POWER, root, "the battery reading carried no usable field")
        }
        return rows
    }

    // ---- memory, pressure and ZRAM (T4.3) ------------------------------------------------------------

    private fun memory(): List<AtlasObservation> {
        val reading = source.memory() ?: return emptyList()
        return buildList {
            addAll(
                listOfNotNull(
                    bytes("memory.meminfo.total", "/proc/meminfo/mem_total", reading.memTotalKb),
                    bytes("memory.meminfo.available", "/proc/meminfo/mem_available", reading.memAvailableKb),
                    bytes("memory.meminfo.swap_total", "/proc/meminfo/swap_total", reading.swapTotalKb),
                    bytes("memory.meminfo.swap_free", "/proc/meminfo/swap_free", reading.swapFreeKb),
                ),
            )
            addAll(psi("memory.psi.cpu", "/proc/pressure/cpu", reading.psiSomeRaw))
            addAll(psi("memory.psi.memory", "/proc/pressure/memory", reading.psiFullRaw))
        }
    }

    private fun bytes(id: String, path: String, kilobytes: Long?): AtlasObservation? {
        val converted = AtlasPlatformParsers.kilobytesToBytes(kilobytes) ?: return null
        return number(
            id = id,
            domain = AtlasDomain.MEMORY,
            path = path,
            unit = AtlasUnit.BYTES,
            value = converted.toDouble(),
            note = "converted from the KiB procfs reports; the conversion is explicit, not a guess",
        )
    }

    /**
     * PSI is a reviewed proc summary and stays a **separate signal** from meminfo heuristics. A
     * malformed line yields a failed observation that keeps the raw text, never a zero.
     */
    private fun psi(id: String, path: String, raw: String?): List<AtlasObservation> {
        if (raw == null) return listOf(missing(id, AtlasDomain.MEMORY, path, "no pressure reading was reported"))
        if (!AtlasPlatformParsers.psiIsWellFormed(raw)) {
            return listOf(
                malformed(
                    id = id,
                    domain = AtlasDomain.MEMORY,
                    path = path,
                    raw = raw,
                    why = "pressure text is not the reviewed some/full form; raw text retained",
                ),
            )
        }
        val average = AtlasPlatformParsers.psiAverage(raw)
        val total = AtlasPlatformParsers.psiTotal(raw)
        val averageRow = if (average == null) {
            missing("$id.avg10", AtlasDomain.MEMORY, path, "avg10 missing while the line looked well formed")
        } else {
            number("$id.avg10", AtlasDomain.MEMORY, path, AtlasUnit.PERCENT, average, "share of the last 10 s spent stalled")
        }
        val totalRow = if (total == null) {
            missing("$id.total", AtlasDomain.MEMORY, path, "total missing while the line looked well formed")
        } else {
            number("$id.total", AtlasDomain.MEMORY, path, AtlasUnit.COUNT, total, "cumulative stall time in the kernel's own units")
        }
        return listOf(averageRow, totalRow)
    }

    // ---- storage, display, sensors, network, privilege (T4.4) -----------------------------------------

    private fun storage(): List<AtlasObservation> {
        val zramRows = source.zramDevices().flatMap { device ->
            val token = device.name.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.lowercase()
            if (token.isEmpty()) return@flatMap emptyList()
            val base = "/sys/block/${device.name}"
            listOf(
                device.disksizeBytes?.let { size ->
                    number("storage.$token.disksize", AtlasDomain.STORAGE, "$base/disksize", AtlasUnit.BYTES, size.toDouble(), "device size")
                } ?: missing("storage.$token.disksize", AtlasDomain.STORAGE, "$base/disksize", "no disksize reported"),
                device.memUsedBytes?.let { used ->
                    number("storage.$token.mem_used", AtlasDomain.STORAGE, "$base/mem_used_total", AtlasUnit.BYTES, used.toDouble(), "bytes currently held by this device")
                } ?: missing("storage.$token.mem_used", AtlasDomain.STORAGE, "$base/mem_used_total", "no used size reported"),
                device.algorithm?.takeIf { it.isNotBlank() }?.let { algorithm ->
                    text(
                        id = "storage.$token.algorithm",
                        domain = AtlasDomain.STORAGE,
                        path = "$base/algorithm",
                        semantic = AtlasSemanticStatus.INFERRED,
                        text = algorithm,
                        raw = algorithm,
                        note = "compression algorithm name as the device reports it",
                    )
                },
            ).filterNotNull()
        }

        val dataRows = source.storage()?.let { data ->
            listOfNotNull(
                data.dataFreeBytes?.let { free ->
                    number("storage.data.free", AtlasDomain.STORAGE, "/android/api/storage/data/free", AtlasUnit.BYTES, free.toDouble(), "free space on the data volume")
                },
                data.dataTotalBytes?.let { total ->
                    number("storage.data.total", AtlasDomain.STORAGE, "/android/api/storage/data/total", AtlasUnit.BYTES, total.toDouble(), "size of the data volume")
                },
            )
        }.orEmpty()

        return zramRows + dataRows
    }

    private fun display(): List<AtlasObservation> {
        val reading = source.display() ?: return emptyList()
        val root = "/android/api/display/mode"
        return listOfNotNull(
            reading.widthPx?.let { width ->
                number("display.width", AtlasDomain.DISPLAY, "$root/width", AtlasUnit.COUNT, width.toDouble(), "width in pixels")
            },
            reading.heightPx?.let { height ->
                number("display.height", AtlasDomain.DISPLAY, "$root/height", AtlasUnit.COUNT, height.toDouble(), "height in pixels")
            },
            reading.refreshHzRaw?.let { refresh ->
                when (val parsed = AtlasPlatformParsers.decimal(refresh)?.takeIf { it > 0.0 }) {
                    null -> malformed("display.refresh", AtlasDomain.DISPLAY, "$root/refresh", refresh, "refresh rate was not a positive number")
                    else -> number("display.refresh", AtlasDomain.DISPLAY, "$root/refresh", AtlasUnit.HERTZ, parsed, "current mode refresh rate")
                }
            },
            reading.hdrTypes?.takeIf { it.isNotEmpty() }?.let { types ->
                text(
                    id = "display.hdr",
                    domain = AtlasDomain.DISPLAY,
                    path = "$root/hdr",
                    semantic = AtlasSemanticStatus.UNKNOWN,
                    text = types.sorted().joinToString(separator = "+"),
                    raw = types.joinToString(separator = "+"),
                    note = "HDR type tokens reported by the platform",
                )
            },
        )
    }

    private fun sensors(): List<AtlasObservation> = source.sensors().mapNotNull { sensor ->
        val kind = sensor.kind.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.lowercase()
        val count = sensor.count
        if (kind.isEmpty() || count == null || count < 0) return@mapNotNull null
        number(
            id = "sensor.$kind.count",
            domain = AtlasDomain.SENSOR,
            path = "/android/api/sensors/$kind",
            unit = AtlasUnit.COUNT,
            value = count.toDouble(),
            note = "how many of this sensor the platform lists, not a rating of any of them",
        )
    }

    /** Network rows carry state only. There is no field here that could hold an address or an SSID. */
    private fun network(): List<AtlasObservation> {
        val reading = source.network() ?: return emptyList()
        val root = "/android/api/network/state"
        val rows = mutableListOf<AtlasObservation>()
        reading.connected?.let { connected ->
            rows += text(
                id = "network.connected",
                domain = AtlasDomain.NETWORK,
                path = "$root/connected",
                semantic = AtlasSemanticStatus.UNKNOWN,
                text = if (connected) "connected" else "disconnected",
                raw = connected.toString(),
                note = "reachability state only; no address, SSID or MAC is read or stored",
            )
        }
        reading.metered?.let { metered ->
            rows += text(
                id = "network.metered",
                domain = AtlasDomain.NETWORK,
                path = "$root/metered",
                semantic = AtlasSemanticStatus.UNKNOWN,
                text = if (metered) "metered" else "unmetered",
                raw = metered.toString(),
                note = "billing state only",
            )
        }
        reading.transport?.let { transport ->
            val token = transport.trim().lowercase()
            if (token.isNotEmpty() && token.all { it.isLetter() || it == '-' }) {
                rows += text(
                    id = "network.transport",
                    domain = AtlasDomain.NETWORK,
                    path = "$root/transport",
                    semantic = AtlasSemanticStatus.INFERRED,
                    text = token,
                    raw = transport,
                    note = "transport kind as a bare word",
                )
            }
        }
        return rows
    }

    /**
     * Privilege is unavailable until the existing control plane says it verified the identity.
     * Existence of a file under `/data/adb` is not verification and is not read here at all.
     */
    private fun privilege(): List<AtlasObservation> {
        val reading = source.privilege() ?: return emptyList()
        if (reading.verification != AtlasIdentityVerification.VERIFIED_BY_CONTROL_PLANE) {
            return listOf(
                missing(
                    id = "privilege.identity",
                    domain = AtlasDomain.PRIVILEGE,
                    path = "/android/api/privilege/identity",
                    why = "${AtlasReasonCodes.UNVERIFIED_IDENTITY}: only the control plane may assert this",
                ),
            )
        }
        val root = "/android/api/privilege"
        val rows = listOfNotNull(
            reading.rootManager?.takeIf { it.isNotBlank() }?.let { manager ->
                text(
                    id = "privilege.root_manager",
                    domain = AtlasDomain.PRIVILEGE,
                    path = "$root/root_manager",
                    semantic = AtlasSemanticStatus.REVIEWED_MATCH,
                    text = manager,
                    raw = manager,
                    note = "identity as reported by the verified control plane",
                )
            },
            reading.moduleVersion?.takeIf { it.isNotBlank() }?.let { version ->
                text(
                    id = "privilege.module_version",
                    domain = AtlasDomain.PRIVILEGE,
                    path = "$root/module_version",
                    semantic = AtlasSemanticStatus.REVIEWED_MATCH,
                    text = version,
                    raw = version,
                    note = "module revision as reported by the verified control plane",
                )
            },
        )
        return if (rows.isEmpty()) {
            listOf(missing("privilege.identity", AtlasDomain.PRIVILEGE, "$root/identity", "verified, but the source reported no identity fields"))
        } else {
            rows
        }
    }

    // ---- observation builders -------------------------------------------------------------------------

    private fun text(
        id: String,
        domain: AtlasDomain,
        path: String,
        semantic: AtlasSemanticStatus,
        text: String,
        raw: String,
        note: String,
    ): AtlasObservation = observation(
        id = id,
        domain = domain,
        path = path,
        semantic = semantic,
        unit = AtlasUnit.UNKNOWN,
        value = null,
        textValue = text,
        raw = raw,
        failure = AtlasFailure.NONE,
        reason = note,
    )

    private fun number(
        id: String,
        domain: AtlasDomain,
        path: String,
        unit: AtlasUnit,
        value: Double,
        note: String,
        semantic: AtlasSemanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
    ): AtlasObservation = observation(
        id = id,
        domain = domain,
        path = path,
        semantic = semantic,
        unit = unit,
        value = value,
        textValue = null,
        raw = null,
        failure = AtlasFailure.NONE,
        reason = note,
    )

    /** A value that arrived and could not be interpreted: `null` plus the raw text, never a zero. */
    private fun malformed(id: String, domain: AtlasDomain, path: String, raw: String?, why: String): AtlasObservation =
        observation(
            id = id,
            domain = domain,
            path = path,
            semantic = AtlasSemanticStatus.UNKNOWN,
            unit = AtlasUnit.UNKNOWN,
            value = null,
            textValue = null,
            raw = raw,
            failure = AtlasFailure.MALFORMED,
            reason = why,
        )

    /** A field the source did not report: nothing was observed, so nothing is claimed. */
    private fun missing(id: String, domain: AtlasDomain, path: String, why: String): AtlasObservation =
        observation(
            id = id,
            domain = domain,
            path = path,
            semantic = AtlasSemanticStatus.UNKNOWN,
            unit = AtlasUnit.UNKNOWN,
            value = null,
            textValue = null,
            raw = null,
            failure = AtlasFailure.BACKEND_UNAVAILABLE,
            reason = why,
        )

    private fun observation(
        id: String,
        domain: AtlasDomain,
        path: String,
        semantic: AtlasSemanticStatus,
        unit: AtlasUnit,
        value: Double?,
        textValue: String?,
        raw: String?,
        failure: AtlasFailure,
        reason: String,
    ): AtlasObservation = AtlasObservation(
        id = sanitizeId(id),
        domain = domain,
        providerId = providerId,
        catalogVersion = catalogVersion,
        sourceId = SOURCE_ID,
        path = path,
        access = if (failure == AtlasFailure.NONE) AtlasAccess.READABLE else AtlasAccess.UNKNOWN,
        semanticStatus = semantic,
        unit = unit,
        value = value,
        textValue = textValue,
        rawRepresentation = raw?.take(512),
        failure = failure,
        reason = reason,
        observedAtElapsedMs = clockMs(),
        bootGeneration = 0L,
        privilegeGeneration = 0L,
        truncated = false,
    )

    /**
     * Ids are canonical or the observation cannot exist. An unusable one is made safe rather than
     * dropped, so a device with an odd name still produces evidence about itself.
     */
    private fun sanitizeId(id: String): String {
        val cleaned = id.lowercase().map { character ->
            if (character.isLetterOrDigit() || character == '.' || character == '_' || character == '-') character else '-'
        }.joinToString(separator = "")
        val padded = if (cleaned.length >= 3) cleaned else cleaned.padEnd(3, '0')
        return if (AtlasIds.isValidObservationId(padded)) padded else "atlas.unnamed"
    }

    private companion object {
        const val SOURCE_ID = "provider:platform"
    }
}
