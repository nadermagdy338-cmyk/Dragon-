package nd.max.core.atlas

/**
 * Reviewed capability knowledge (plan `P1`, slice `A`).
 *
 * The catalog describes *interfaces*: an anchor directory, one attribute name inside it, the unit
 * that interface is documented to use, and where that knowledge came from. It deliberately cannot
 * express a phone profile, a model-name allowlist, a control action or any authority: an entry says
 * "this interface is reviewed and readable", never "this device may be changed".
 *
 * Two structural choices matter:
 * 1. **Anchors, not globs.** An entry names a fixed parent root plus a single safe basename. Listing
 *    a parent and validating the returned basenames is the only way to enumerate, so no pattern or
 *    caller string can ever reach a shell.
 * 2. **Provenance is mandatory.** An entry without a source id, reference and license note is not
 *    admissible knowledge; the validator rejects it instead of trusting an anonymous claim.
 */

/**
 * How an entry's attribute is reached (`scope` correction, 2026-09-20).
 *
 * This enum exists because of a **measured defect**: twelve of the fifteen reviewed entries named a
 * class directory (`/sys/class/devfreq`, `/sys/class/thermal`, `/sys/class/power_supply`,
 * `/sys/class/kgsl`, `/sys/devices/system/cpu/cpufreq`, `/sys/block`) as if an attribute lived
 * directly inside it. On a real device those directories contain *devices*, and the attribute lives
 * inside a device directory, so every one of those paths addresses a file that cannot exist while the
 * interface it describes is present and readable one level down.
 *
 * The distinction is not cosmetic: absence is only ever claimed for a path whose parent was
 * enumerated, so the wrong shape can turn "read from the device" into a statement about a device that
 * was never asked. The corrected grammar has exactly two shapes, and both of them are concrete paths:
 *
 * - [ROOT_FILE]: the attribute is a file inside [AtlasCatalogEntry.parentRoot].
 * - [CHILD_FILE]: [AtlasCatalogEntry.parentRoot] is enumerated, and the attribute is read inside each
 *   child whose name matches [AtlasCatalogEntry.childPrefix].
 *
 * A child *prefix* is deliberately not a pattern: it is a plain string compared against names the
 * kernel itself returned, so it can select `zram0`/`zram1` without ever becoming a glob that a caller
 * could widen.
 */
enum class AtlasCatalogScope {
    /** `parentRoot` is a directory of devices; the attribute lives one level down, inside a child. */
    CHILD_FILE,

    /** `parentRoot` holds the file itself: `parentRoot/attribute`. */
    ROOT_FILE,
}

/** One reviewed interface. `vendorTags` empty means "applies to any device", including an unknown one. */
data class AtlasCatalogEntry(
    val id: String,
    val domain: AtlasDomain,
    val provider: AtlasProviderKind,
    val vendorTags: Set<String>,
    val parentRoot: String,
    val attribute: String,
    val unit: AtlasUnit,
    val safety: AtlasSafetyClass,
    val provenance: AtlasProvenance,
    val featureTag: String?,
    val note: String,
    val scope: AtlasCatalogScope = AtlasCatalogScope.ROOT_FILE,
    /** Which enumerated children are interfaces of this entry's kind. `null` means every child. */
    val childPrefix: String? = null,
) {
    init {
        require(AtlasIds.isValidObservationId(id)) { "catalog id is not canonical: $id" }
        require(AtlasIds.isSafeAbsolutePath(parentRoot)) { "catalog root is not a safe absolute path: $parentRoot" }
        require(AtlasIds.isSafeBasename(attribute)) { "catalog attribute is not a safe basename: $attribute" }
        require(vendorTags.all { it.isNotBlank() && it == it.lowercase() }) { "vendor tags are lowercase tokens" }
        if (provider == AtlasProviderKind.VENDOR) {
            require(vendorTags.isNotEmpty()) { "a vendor entry must name its vendor tag" }
        }
        when (scope) {
            AtlasCatalogScope.ROOT_FILE ->
                require(childPrefix == null) { "a root-level entry addresses one file and cannot filter children" }

            AtlasCatalogScope.CHILD_FILE -> {
                require(AtlasAnchors.isEnumerable(parentRoot)) {
                    "an enumerated entry needs an enumerable approved root: $parentRoot"
                }
                require(childPrefix == null || CHILD_PREFIX.matches(childPrefix)) {
                    "a child prefix is a plain name fragment, not a pattern: $childPrefix"
                }
            }
        }
    }
}

/**
 * A child prefix is a name fragment, never a pattern: letters, digits, dot, dash and underscore.
 * `*`, `?`, `[`, `]` and every shell metacharacter are refused above, and the fragment is only ever
 * compared with `startsWith` against names a listing returned.
 */
private val CHILD_PREFIX = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$")

/** Result of validating a catalog. Invalid problems are stable, sorted and human-readable. */
sealed interface AtlasCatalogValidation {
    data class Valid(val catalog: AtlasCatalog) : AtlasCatalogValidation

    data class Invalid(val problems: List<String>) : AtlasCatalogValidation {
        init {
            require(problems.isNotEmpty()) { "Invalid must list at least one problem" }
        }
    }
}

/**
 * A versioned set of reviewed entries. `candidates` is the only selection API: it never returns an
 * empty list because of an unknown vendor, and it never depends on iteration order.
 */
data class AtlasCatalog(
    val version: String,
    val entries: List<AtlasCatalogEntry>,
) {
    fun byId(id: String): AtlasCatalogEntry? = entries.firstOrNull { it.id == id }

    fun byDomain(domain: AtlasDomain): List<AtlasCatalogEntry> = candidates(domain, emptySet())

    /**
     * Deterministic per-feature candidates: generic and platform entries always participate, vendor
     * entries only for a matching device hint. An unknown vendor therefore loses vendor knowledge
     * and keeps generic knowledge, instead of losing everything.
     */
    fun candidates(domain: AtlasDomain, vendorTags: Set<String> = emptySet()): List<AtlasCatalogEntry> {
        val tags = vendorTags.map(String::lowercase).toSet()
        return entries
            .filter { it.domain == domain }
            .filter { entry ->
                when (entry.provider) {
                    AtlasProviderKind.GENERIC -> true
                    AtlasProviderKind.PLATFORM -> true
                    AtlasProviderKind.VENDOR -> entry.vendorTags.any(tags::contains)
                }
            }
            .sortedWith(compareBy({ providerRank(it.provider) }, { it.id }))
    }

    companion object {
        const val SCHEMA_VERSION = "atlas-catalog-1"

        fun providerRank(kind: AtlasProviderKind): Int = when (kind) {
            AtlasProviderKind.GENERIC -> 0
            AtlasProviderKind.PLATFORM -> 1
            AtlasProviderKind.VENDOR -> 2
        }

        /**
         * Validates a whole catalog and collects every problem instead of stopping at the first one,
         * so an author sees the complete list. Nothing is coerced: an entry list written against
         * another grammar is rejected rather than read as if it were this one.
         *
         * Two axes are deliberately separate. [schema] is the **grammar** an entry list is written
         * against — one grammar, one validator, however many banks. [version] is the **content
         * revision**, which each bank owns: it is what a cache key and a report cite, so a bank that
         * changes a unit without changing its revision would be serving evidence under a stale name.
         */
        fun validate(
            version: String,
            entries: List<AtlasCatalogEntry>,
            schema: String = SCHEMA_VERSION,
        ): AtlasCatalogValidation {
            val problems = mutableListOf<String>()
            if (schema != SCHEMA_VERSION) problems.add("unsupported-schema:$schema")
            if (version.isBlank()) problems.add("blank-version")
            if (entries.isEmpty()) problems.add("empty-catalog")

            val ids = mutableListOf<String>()
            entries.forEach { entry ->
                if (ids.contains(entry.id)) problems.add("duplicate-id:${entry.id}")
                ids.add(entry.id)
                // A root-level entry either sits inside an approved directory, or addresses one
                // specific reviewed file outside any approved root. An enumerated entry needs a root
                // that may actually be walked.
                when (entry.scope) {
                    AtlasCatalogScope.ROOT_FILE ->
                        if (!AtlasAnchors.isAddressable(entry.parentRoot, entry.attribute)) {
                            problems.add("unapproved-root:${entry.parentRoot}")
                        }

                    AtlasCatalogScope.CHILD_FILE ->
                        if (!AtlasAnchors.isEnumerable(entry.parentRoot)) {
                            problems.add("unapproved-child-root:${entry.parentRoot}")
                        }
                }
                if (!AtlasIds.isSafeBasename(entry.attribute)) problems.add("unsafe-attribute:${entry.attribute}")
                if (!entry.provenance.sourceId.startsWith("local:") && entry.provenance.revision.isBlank()) {
                    problems.add("missing-revision:${entry.id}")
                }
            }

            conflicts(entries).forEach(problems::add)
            return if (problems.isEmpty()) {
                AtlasCatalogValidation.Valid(AtlasCatalog(version, entries.sortedBy(AtlasCatalogEntry::id)))
            } else {
                AtlasCatalogValidation.Invalid(problems.sorted())
            }
        }

        /**
         * Two entries describing one interface must agree on the unit, or the catalog is ambiguous.
         * The key includes the scope and the child prefix because two entries may legitimately read
         * one attribute under different children (`cur_state` under cooling devices and nothing else).
         */
        private fun conflicts(entries: List<AtlasCatalogEntry>): List<String> {
            val units = mutableMapOf<String, AtlasUnit>()
            val found = mutableListOf<String>()
            entries.forEach { entry ->
                val key = "${entry.parentRoot}|${entry.scope}|${entry.childPrefix}/${entry.attribute}"
                val previous = units.put(key, entry.unit)
                if (previous != null && previous != entry.unit) {
                    found.add("unit-conflict:$key:$previous!=$entry.unit")
                }
            }
            return found
        }
    }
}

/**
 * Anchor roots Atlas is allowed to look at. A root is approved only when it is a fixed kernel
 * interface directory; a path outside this list is rejected by validation, which keeps the catalog
 * from growing into "scan everything under /sys".
 *
 * Two shapes are approved, and the difference matters (`P10`):
 * - **A directory root** ([APPROVED]) may be enumerated *and* read, which is the only way absence can
 *   ever be claimed.
 * - **A single file** ([REVIEWED_FILES]) may be read but **never enumerated**, which exists because
 *   the platform grants a few individual `/proc` files to app domains while the `/proc` directory
 *   itself is not ours to walk. Approving `/proc` as a root would be the cheap version of this and it
 *   would open `stat`, `uptime`, `version`, `vmstat`, `loadavg`, `mounts` and `swaps` — all denied to
 *   apps by platform policy — to a future careless catalog entry.
 */
object AtlasAnchors {

    /**
     * The virtual root of public-API surfaces (`P4`).
     *
     * It is approved so an observation about the battery or display API can be addressed, and it is
     * deliberately **not a filesystem path**: the bounded file boundary refuses to open anything
     * under it, so an API surface can never be smuggled into the file transport later.
     */
    const val PUBLIC_API: String = "/android/api"

    val APPROVED: Set<String> = setOf(
        "/sys/devices/system/cpu",
        "/sys/devices/system/cpu/cpufreq",
        "/sys/class/devfreq",
        "/sys/class/thermal",
        "/sys/class/power_supply",
        "/sys/class/kgsl",
        "/sys/block",
        "/proc/pressure",
        "/proc/gpufreqv2",
        "/proc/gpufreq",
        PUBLIC_API,
    )

    /**
     * The subset of [APPROVED] that may be **enumerated**. A directory listing is what proves absence,
     * so the ability to walk a root is a stronger grant than the ability to read one known file in it,
     * and the public-API virtual root is excluded because it has no children and no file transport to
     * open.
     */
    val ENUMERABLE: Set<String> = APPROVED - PUBLIC_API

    /** True when `root` is an approved root Atlas may list. */
    fun isEnumerable(root: String): Boolean = root in ENUMERABLE

    /**
     * The only individual files Atlas may read outside an approved directory root.
     *
     * Each one is here because a reviewed platform grant makes it readable to an ordinary app; a file
     * whose only argument is "some apps seem to read it" does not belong here. See
     * `01-GAPS-AND-IDEAS.md` §3 and §4 for the granted-versus-denied table this list is derived from.
     */
    val REVIEWED_FILES: Set<String> = setOf(
        // `allow domain proc_cpuinfo:file r_file_perms;` in AOSP private/domain.te.
        "/proc/cpuinfo",
    )

    fun isReviewedFile(path: String): Boolean = REVIEWED_FILES.contains(path)

    /**
     * True when this (parent root, attribute) pair addresses something the boundary would allow.
     *
     * It exists so the rule is stated once: validation, the boundary and the tests all ask this
     * question instead of each re-deriving "approved root, or one reviewed file outside a root".
     */
    fun isAddressable(parentRoot: String, attribute: String): Boolean =
        isApproved(parentRoot) || isReviewedFile("$parentRoot/$attribute")

    fun isApproved(path: String): Boolean =
        isReviewedFile(path) || APPROVED.any { approved -> path == approved || path.startsWith("$approved/") }

    /** True for the public-API virtual root, which no file transport may open. */
    fun isPublicApiSurface(path: String): Boolean =
        path == PUBLIC_API || path.startsWith("$PUBLIC_API/")
}

/**
 * The reviewed seed set for phase 1.
 *
 * Every entry cites either the phase source matrix (`01-SOURCES.md`) or the local reviewed backend
 * it mirrors. The set is intentionally small: it is the vocabulary proof for `P1`, not the domain
 * matrix, which lands in `P4`. Nested kernel attributes with a slash in the name (for example
 * `stats/time_in_state`) are **not** represented here because the template grammar allows exactly
 * one safe basename; extending it is a `P4` decision, not something to smuggle in as a path string.
 */
object AtlasReviewedSeeds {

    private const val LOCAL_CPU = "local:manager/app/src/main/java/nd/max/core/hardware/CpuHardwareBackend.kt"
    private const val LOCAL_GPU = "local:manager/app/src/main/java/nd/max/core/hardware/GpuHardwareBackend.kt"
    private const val LOCAL_TREE = "working tree, observed 2026-09-20"

    fun validation(): AtlasCatalogValidation =
        AtlasCatalog.validate(AtlasCatalog.SCHEMA_VERSION, entries())

    fun catalog(): AtlasCatalog = when (val result = validation()) {
        is AtlasCatalogValidation.Valid -> result.catalog
        is AtlasCatalogValidation.Invalid ->
            error("reviewed seed catalog is invalid: ${result.problems.joinToString()}")
    }

    fun entries(): List<AtlasCatalogEntry> = listOf(
        entry(
            id = "cpu.policy.scaling_cur_freq",
            domain = AtlasDomain.CPU,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_cur_freq",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "policy",
            unit = AtlasUnit.KILO_HERTZ,
            source = "L02",
            reference = "https://docs.kernel.org/admin-guide/pm/cpufreq.html",
            revision = "live docs snapshot, fetched 2026-09-20",
            note = "kHz. Often the last requested P-state rather than the exact hardware clock.",
            local = LOCAL_CPU,
        ),
        entry(
            id = "cpu.policy.scaling_available_frequencies",
            domain = AtlasDomain.CPU,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_available_frequencies",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "policy",
            unit = AtlasUnit.KILO_HERTZ,
            source = "L02",
            reference = "https://docs.kernel.org/admin-guide/pm/cpufreq.html",
            revision = "live docs snapshot, fetched 2026-09-20",
            note = "A snapshot, not a constraint. Absence does not mean a continuous range.",
            local = LOCAL_CPU,
        ),
        entry(
            id = "cpu.policy.scaling_available_governors",
            domain = AtlasDomain.CPU,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_available_governors",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "policy",
            unit = AtlasUnit.UNKNOWN,
            source = "L02",
            reference = "https://docs.kernel.org/admin-guide/pm/cpufreq.html",
            revision = "live docs snapshot, fetched 2026-09-20",
            note = "Free text list. A governor name is not policy authority.",
            local = LOCAL_CPU,
        ),
        entry(
            id = "cpu.policy.related_cpus",
            domain = AtlasDomain.CPU,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/devices/system/cpu/cpufreq",
            attribute = "related_cpus",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "policy",
            unit = AtlasUnit.UNKNOWN,
            source = "L02",
            reference = "https://docs.kernel.org/admin-guide/pm/cpufreq.html",
            revision = "live docs snapshot, fetched 2026-09-20",
            note = "Normalize as a CPU list; includes offline CPUs, unlike affected_cpus.",
            local = LOCAL_CPU,
        ),
        entry(
            id = "gpu.devfreq.cur_freq",
            domain = AtlasDomain.GPU,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/devfreq",
            attribute = "cur_freq",
            scope = AtlasCatalogScope.CHILD_FILE,
            unit = AtlasUnit.HERTZ,
            source = "L04",
            reference = "https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/v6.12/drivers/devfreq/devfreq.c",
            revision = "v6.12",
            note = "Mainline Hz. A vendor deviation must stay INFERRED, never silently scaled.",
            local = LOCAL_GPU,
        ),
        entry(
            id = "gpu.devfreq.available_frequencies",
            domain = AtlasDomain.GPU,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/devfreq",
            attribute = "available_frequencies",
            scope = AtlasCatalogScope.CHILD_FILE,
            unit = AtlasUnit.HERTZ,
            source = "L03",
            reference = "https://docs.kernel.org/admin-guide/abi-testing.html",
            revision = "live docs snapshot, fetched 2026-09-20",
            note = "A snapshot of what the driver offers, not a min/max guarantee.",
            local = LOCAL_GPU,
        ),
        entry(
            id = "thermal.zone.temp",
            domain = AtlasDomain.THERMAL,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/thermal",
            attribute = "temp",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "thermal_zone",
            unit = AtlasUnit.MILLI_CELSIUS,
            source = "L05",
            reference = "https://docs.kernel.org/driver-api/thermal/sysfs-api.html",
            revision = "v6.12 ABI",
            note = "Millidegrees by mainline ABI. A zone index proves no sensor location.",
            local = LOCAL_TREE,
        ),
        entry(
            id = "thermal.zone.type",
            domain = AtlasDomain.THERMAL,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/thermal",
            attribute = "type",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "thermal_zone",
            unit = AtlasUnit.UNKNOWN,
            source = "L05",
            reference = "https://docs.kernel.org/driver-api/thermal/sysfs-api.html",
            revision = "v6.12 ABI",
            note = "Driver-supplied free text; a broad name match is not a proven junction sensor.",
            local = LOCAL_TREE,
        ),
        entry(
            id = "power.charge_full",
            domain = AtlasDomain.POWER,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/power_supply",
            attribute = "charge_full",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "bat",
            unit = AtlasUnit.MICRO_AMP_HOUR,
            source = "L06",
            reference = "https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/v6.12/Documentation/power/power_supply_class.rst",
            revision = "v6.12",
            note = "Microamp-hours: charge. Energy nodes are a different dimension and are separate entries.",
            local = LOCAL_TREE,
        ),
        entry(
            id = "power.energy_full",
            domain = AtlasDomain.POWER,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/power_supply",
            attribute = "energy_full",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "bat",
            unit = AtlasUnit.MICRO_WATT_HOUR,
            source = "L06",
            reference = "https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/v6.12/Documentation/power/power_supply_class.rst",
            revision = "v6.12",
            note = "Microwatt-hours: energy. Never converted into a charge value without explicit voltage semantics.",
            local = LOCAL_TREE,
        ),
        entry(
            id = "power.voltage_now",
            domain = AtlasDomain.POWER,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/class/power_supply",
            attribute = "voltage_now",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "bat",
            unit = AtlasUnit.MICRO_VOLT,
            source = "L06",
            reference = "https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/v6.12/Documentation/power/power_supply_class.rst",
            revision = "v6.12",
            note = "Microvolts. Raw polarity of current is preserved elsewhere and is not absolute-ized.",
            local = LOCAL_TREE,
        ),
        entry(
            id = "memory.psi.cpu",
            domain = AtlasDomain.MEMORY,
            provider = AtlasProviderKind.PLATFORM,
            root = "/proc/pressure",
            attribute = "cpu",
            unit = AtlasUnit.UNKNOWN,
            source = "LOCAL-PSI",
            reference = "local:manager/app/src/main/java/nd/max/core/maxai/MemoryStall.kt",
            revision = LOCAL_TREE,
            note = "A reviewed proc summary, so it may use the larger read allowance. PSI stays a distinct signal from meminfo heuristics.",
        ),
        entry(
            id = "storage.zram.disksize",
            domain = AtlasDomain.STORAGE,
            provider = AtlasProviderKind.GENERIC,
            root = "/sys/block",
            attribute = "disksize",
            scope = AtlasCatalogScope.CHILD_FILE,
            childPrefix = "zram",
            unit = AtlasUnit.BYTES,
            source = "LOCAL-ZRAM",
            reference = "local:manager/app/src/main/java/nd/max/core/hardware/ZramHardwareBackend.kt",
            revision = LOCAL_TREE,
            note = "Enumerate every zram device; a hardcoded zram0 is not the device set.",
        ),
        entry(
            id = "cpu.info.cpuinfo",
            domain = AtlasDomain.CPU,
            provider = AtlasProviderKind.PLATFORM,
            root = "/proc",
            attribute = "cpuinfo",
            unit = AtlasUnit.UNKNOWN,
            source = "S18c",
            reference = "https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/domain.te",
            revision = "blob 6999586eaf09978949b1ab3fce5bba738870c47a",
            note = "The one /proc text surface with a reviewed platform grant to app domains " +
                "(allow domain proc_cpuinfo:file r_file_perms). Free text: it is reported raw, and its " +
                "fields are never promoted to typed values from a name match. Read-only, and never enumerated.",
        ),
        entry(
            id = "gpu.mediatek.stack_signed_opp_table",
            domain = AtlasDomain.GPU,
            provider = AtlasProviderKind.VENDOR,
            vendorTags = setOf("mediatek"),
            root = "/proc/gpufreqv2",
            attribute = "stack_signed_opp_table",
            unit = AtlasUnit.UNKNOWN,
            source = "LOCAL-MTK",
            reference = LOCAL_GPU,
            revision = LOCAL_TREE,
            note = "Signed table: index and raw units stay distinct until a reviewed ABI establishes meaning.",
        ),
    )

    private fun entry(
        id: String,
        domain: AtlasDomain,
        provider: AtlasProviderKind,
        root: String,
        attribute: String,
        unit: AtlasUnit,
        source: String,
        reference: String,
        revision: String,
        note: String,
        vendorTags: Set<String> = emptySet(),
        local: String? = null,
        scope: AtlasCatalogScope = AtlasCatalogScope.ROOT_FILE,
        childPrefix: String? = null,
    ): AtlasCatalogEntry = AtlasCatalogEntry(
        id = id,
        domain = domain,
        provider = provider,
        vendorTags = vendorTags,
        parentRoot = root,
        attribute = attribute,
        unit = unit,
        scope = scope,
        childPrefix = childPrefix,
        safety = AtlasSafetyClass.READ_ONLY,
        provenance = AtlasProvenance(
            sourceId = source,
            reference = reference,
            revision = revision,
            licenseNote = if (local != null) "Project source, independently authored; no upstream code copied" else "Cited documentation/source; no code copied",
            confidence = if (source.startsWith("LOCAL")) AtlasSourceConfidence.DESIGN else AtlasSourceConfidence.SOURCE_VERIFIED,
        ),
        featureTag = null,
        note = note,
    )
}
