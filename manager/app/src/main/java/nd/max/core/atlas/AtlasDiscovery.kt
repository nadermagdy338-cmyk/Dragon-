/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget
import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.ReadOnlyProbeAccess

/**
 * What one bounded discovery job is allowed to do (`P2` + `P5`).
 *
 * One instance serves one scan. It owns the [ReadOnlyProbeAccess] counters — operations, entries,
 * bytes, limits reached — so a report can say a limit stopped it rather than implying the device had
 * nothing to say. A new scan gets a new job, which is what makes the budgets per-scan rather than
 * cumulative over the process lifetime.
 */
class AtlasJob(
    internal val access: ReadOnlyProbeAccess,
    private val plan: AtlasProbePlan,
) {
    /** The probes this job intends to run, cheapest first. */
    val due: List<String> get() = plan.due.map { it.id }

    /** Why a feature will not be attempted, per probe id. */
    fun skipReason(id: String): String? = plan.reasonFor(id)

    fun discover(request: AtlasProbeRequest): AtlasReadResult = access.read(request)

    /**
     * Names directly under an approved root, bounded by the same budget as every other attempt.
     *
     * This is the only way an interface that lives one level down can be addressed at all, and the only
     * way absence can ever be claimed: a name that was in a successful listing is present, and a name
     * that was not is absent — anything else stays unknown. A failed listing is returned as a failure
     * and never as an empty device.
     */
    fun childrenOf(root: String): AtlasDirectoryListing = access.list(root)

    /** True when any bound (time, entries, bytes, operations) actually stopped this job. */
    fun limitsReached(): Boolean = access.stats().limitsReached

    fun stats() = access.stats()
}

/**
 * A catalog together with **how its entries must be read**.
 *
 * The same review rules apply to every bank, but the strength of the claim does not: an entry from the
 * reviewed set is `REVIEWED_MATCH`, an entry from the community bank is `INFERRED` — a name known to
 * exist, not a meaning established. Keeping that in one place is what stops a bank from silently
 * upgrading its own evidence.
 */
data class AtlasSourceCatalog(
    val catalog: AtlasCatalog,
    val providerId: String,
    val semantic: AtlasSemanticStatus,
)

/**
 * Turns the reviewed catalog into work, and work into evidence.
 *
 * This is the piece that makes the difference between a designed library and a feature: without it
 * nothing in the app ever calls the transport, which is exactly the state the gap review found. Five
 * decisions live here:
 *
 * 1. **Two shapes, both concrete.** A root-level entry addresses `parentRoot/attribute`; an enumerated
 *    entry addresses `parentRoot/<child>/attribute` for each child the device actually listed. Twelve
 *    reviewed entries were written in the wrong shape before this was a grammar, and every one of them
 *    named a path that cannot exist while the interface it describes is present.
 * 2. **Only addressable entries become features.** The catalog's own validation already refuses an
 *    entry pinned to an unapproved root, and this class asks the same question again before building a
 *    request (`AtlasAnchors.isAddressable`, `AtlasAnchors.isEnumerable`), so a mis-edited catalog cannot
 *    reach the transport.
 * 3. **The reviewed `/proc` allowance is attached to the entry that earned it**, not decided at read
 *    time by a path prefix. A caller cannot inflate its own byte budget by naming a path.
 * 4. **A bank is consulted for the domains the previous one could not resolve**, and never for an
 *    interface that was already attempted: the second line of defense is coverage, not a second opinion.
 * 5. **A backend's parsed evidence is not a probe.** CPU/GPU facts come from the existing parsers
 *    through [AtlasBackendProvider]; requesting a *catalog path* for the same interface would read the
 *    kernel a second time for the same answer.
 */
class AtlasDiscovery(
    private val sources: List<AtlasSourceCatalog> = listOf(REVIEWED),
    private val identity: AtlasDeviceIdentity,
    private val transport: AtlasReadTransport,
    private val budget: AtlasReadBudget = AtlasReadBudget.DEFAULT,
    private val clockMs: () -> Long,
    private val bootGeneration: () -> Long = { 0L },
    private val privilegeGeneration: () -> Long = { 0L },
) {

    /** The reviewed bank: the first line of defense. */
    val catalog: AtlasCatalog get() = sources.first().catalog

    /**
     * Vendor tags this device declares, resolved once.
     *
     * A vendor-tagged interface is only ever attempted on a device that claims that vendor: an entry
     * for MediaTek's `/proc/gpufreq` read on a Qualcomm phone answers nothing and costs a read, and a
     * catalog that ignores the tag turns "not your vendor" into noise indistinguishable from "missing".
     */
    private val deviceTags: Set<String> by lazy { identity.vendorHints() }

    /** Whether an attempt that needs privilege may proceed: never, without an authorized transport. */
    var privilegeAvailable: () -> Boolean = { false }

    /**
     * Every **root-level** feature of one bank this device context may address.
     *
     * Enumerated entries are not here on purpose: they need a job to be listed before they can be
     * addressed, so they are produced by [enumeratedFeatures] with that job. The first pass calls this
     * for [REVIEWED] only; the candidate pass calls it for [COMMUNITY] with [domains] narrowed to what
     * the first pass could not resolve.
     */
    fun features(
        source: AtlasSourceCatalog = sources.first(),
        domains: Set<AtlasDomain>? = null,
        volatilityOf: (AtlasCatalogEntry) -> AtlasVolatility = AtlasResolver::volatilityFor,
    ): List<AtlasFeatureRequest> = source.catalog.entries
        .filter { it.scope == AtlasCatalogScope.ROOT_FILE }
        .filter { it.safety == AtlasSafetyClass.READ_ONLY }
        .filter { domains == null || it.domain in domains }
        .filter { it.provider != AtlasProviderKind.VENDOR || it.vendorTags.any(deviceTags::contains) }
        .filter { AtlasAnchors.isAddressable(it.parentRoot, it.attribute) }
        .map { entry ->
            AtlasFeatureRequest(
                request = requestFor(entry, source = source),
                volatility = volatilityOf(entry),
            )
        }

    /** Paths a bank's root-level features address, so a later bank cannot read the same file twice. */
    fun rootPaths(source: AtlasSourceCatalog = sources.first()): Set<String> =
        features(source).map { it.request.path }.toSet()

    /**
     * Reviewed and candidate features whose attribute lives inside an enumerated child.
     *
     * Every returned request names a path the device itself produced (or a child that was listed and
     * whose attribute was not — in which case the read fails honestly). Nothing here guesses a device
     * name: `zram0` exists because a listing said so, not because a table said most devices have it.
     *
     * @param catalogs the banks to expand, in order. The first bank's entries are attempted first.
     * @param attemptedPaths paths already attempted in this scan, so no interface is read twice.
     * @param domains when non-null, only entries in these domains are expanded (the candidate pass).
     * @param limit the hard cap on returned features. A bank is a body of knowledge, not a budget.
     */
    fun enumeratedFeatures(
        job: AtlasJob,
        catalogs: List<AtlasSourceCatalog> = sources,
        attemptedPaths: Set<String> = emptySet(),
        domains: Set<AtlasDomain>? = null,
        limit: Int = DEFAULT_ENUMERATED_LIMIT,
    ): List<AtlasFeatureRequest> {
        val chosen = LinkedHashMap<String, AtlasFeatureRequest>()
        val listings = mutableMapOf<String, AtlasDirectoryListing>()

        catalogs.forEach { source ->
            source.catalog.entries
                .filter { it.scope == AtlasCatalogScope.CHILD_FILE }
                .filter { it.safety == AtlasSafetyClass.READ_ONLY }
                .filter { domains == null || it.domain in domains }
                .filter { it.provider != AtlasProviderKind.VENDOR || it.vendorTags.any(deviceTags::contains) }
                .sortedWith(compareBy({ AtlasCatalog.providerRank(it.provider) }, { it.id }))
                .forEach { entry ->
                    if (chosen.size >= limit) return@forEach
                    if (!AtlasAnchors.isEnumerable(entry.parentRoot)) return@forEach
                    val listing = listings.getOrPut(entry.parentRoot) { job.childrenOf(entry.parentRoot) }
                    // A root that could not be listed yields no features and no claim: the caller sees
                    // the failure in the job's own stats, and nothing is invented in its place.
                    if (listing.failure != AtlasFailure.NONE) return@forEach
                    listing.names
                        .filter { entry.childPrefix == null || it.startsWith(entry.childPrefix) }
                        .forEach { child ->
                            if (chosen.size >= limit) return@forEach
                            val path = "${entry.parentRoot}/$child/${entry.attribute}"
                            if (path in attemptedPaths || chosen.containsKey(path)) return@forEach
                            val id = perChildId(entry.id, child) ?: return@forEach
                            chosen[path] = AtlasFeatureRequest(
                                request = requestFor(entry, source = source, child = child, id = id),
                                volatility = AtlasResolver.volatilityForDomain(entry.domain),
                            )
                        }
                }
        }
        return chosen.values.toList()
    }

    /** One catalog entry as a probe request. The reviewed `/proc` allowance follows the entry. */
    fun requestFor(
        entry: AtlasCatalogEntry,
        source: AtlasSourceCatalog = sources.first(),
        child: String? = null,
        id: String = entry.id,
    ): AtlasProbeRequest = AtlasProbeRequest(
        id = id,
        domain = entry.domain,
        providerId = source.providerId,
        catalogVersion = source.catalog.version,
        sourceId = entry.provenance.sourceId,
        path = pathFor(entry, child),
        unit = entry.unit,
        semanticStatus = source.semantic,
        requiresPrivilege = false,
        reviewedProcSummary = entry.provenance.sourceId in REVIEWED_PROC_SOURCES,
    )

    /** The concrete path an entry addresses, given the child it was enumerated under. */
    fun pathFor(entry: AtlasCatalogEntry, child: String?): String = when (entry.scope) {
        AtlasCatalogScope.ROOT_FILE -> "${entry.parentRoot}/${entry.attribute}"
        AtlasCatalogScope.CHILD_FILE -> requireNotNull(child) {
            "an enumerated entry addresses one child at a time: ${entry.id}"
        }.let { "${entry.parentRoot}/$it/${entry.attribute}" }
    }

    /** Opens a fresh bounded job for the given features. Constructors here perform no I/O. */
    fun newJob(features: List<AtlasFeatureRequest>): AtlasJob {
        val access = ReadOnlyProbeAccess(
            transport = transport,
            budget = budget,
            clockMs = clockMs,
            privilegeAvailable = { privilegeAvailable() },
            currentGeneration = { bootGeneration() },
        )
        return AtlasJob(access, planFor(features))
    }

    /**
     * Rebuilds a job's probe plan after enumeration produced more features than it was created with.
     *
     * Without this the plan would describe only what was known before the device was listed, and the
     * job's own `due` list would contradict what the scan actually attempts.
     */
    fun replan(job: AtlasJob, features: List<AtlasFeatureRequest>): AtlasJob =
        AtlasJob(job.access, planFor(features))

    private fun planFor(features: List<AtlasFeatureRequest>): AtlasProbePlan =
        AtlasProbeScheduler(budget, clockMs).plan(
            probes = features.map { feature ->
                AtlasScheduledProbe(
                    id = feature.request.id,
                    path = feature.request.path,
                    cost = costOf(feature),
                    freshness = null,
                )
            },
            bootGeneration = bootGeneration(),
            privilegeGeneration = privilegeGeneration(),
        )

    private fun costOf(feature: AtlasFeatureRequest) = AtlasProbeCost(
        opens = 1,
        maxBytes = budget.bytesFor(feature.request),
    )

    companion object {

        /** The reviewed bank, as a source. Everything read from here is a reviewed match. */
        val REVIEWED: AtlasSourceCatalog = AtlasSourceCatalog(
            catalog = AtlasReviewedSeeds.catalog(),
            providerId = "reviewed-catalog",
            semantic = AtlasSemanticStatus.REVIEWED_MATCH,
        )

        /**
         * The community bank, as a source. Everything read from here is `INFERRED`: the interface name
         * is known, the meaning of its number is not established by review.
         */
        val COMMUNITY: AtlasSourceCatalog = AtlasSourceCatalog(
            catalog = AtlasCommunityBank.catalog(),
            providerId = "community-bank",
            semantic = AtlasSemanticStatus.INFERRED,
        )

        /** The default chain: reviewed knowledge first, accumulated vocabulary second. */
        fun defaultSources(): List<AtlasSourceCatalog> = listOf(REVIEWED, COMMUNITY)

        /**
         * How many enumerated features one scan may add.
         *
         * A design value, not a measurement: the transport budget already caps operations, bytes and
         * time, and this cap exists so a device with two hundred enumerated children cannot turn one
         * screen open into a thousand reads. Sixty-four is chosen so the reviewed banks fit whole on a
         * normal device (a handful of policies, one or two devfreq devices, a dozen thermal zones,
         * one battery) with room for the candidate pass.
         */
        const val DEFAULT_ENUMERATED_LIMIT: Int = 64

        /** Observation ids are bounded by their own rule; a longer id cannot be addressed. */
        private const val MAX_ID_LENGTH: Int = 64

        /**
         * The id of one enumerated interface: `entry.id` plus a canonical slug of the child name.
         *
         * A child whose name has no canonical form (or whose id would exceed the bound) yields `null`,
         * and that interface is skipped rather than given an invented id: an id that does not derive
         * from the device cannot be cached, compared or reported honestly.
         */
        fun perChildId(entryId: String, child: String): String? {
            val slug = child.lowercase()
                .map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '-' }
                .joinToString("")
                .trim('-', '.')
                .replace(Regex("-{2,}"), "-")
            if (slug.isEmpty()) return null
            val id = "$entryId.$slug"
            if (id.length > MAX_ID_LENGTH) return null
            return id.takeIf { AtlasIds.isValidObservationId(it) }
        }

        const val PROVIDER_ID: String = "reviewed-catalog"

        /**
         * Sources whose entries were granted a larger read allowance by review: the `/proc` summaries
         * the platform actually exposes to app domains.
         */
        val REVIEWED_PROC_SOURCES: Set<String> = setOf("LOCAL-PSI", "S18c")
    }
}
