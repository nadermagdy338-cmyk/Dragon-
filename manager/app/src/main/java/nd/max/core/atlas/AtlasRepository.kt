package nd.max.core.atlas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The lifecycle of one scan. `CANCELLED` and `PARTIAL` are separate on purpose: only one of them
 * means "we looked and the device did not answer".
 */
enum class AtlasScanStatus { IDLE, RUNNING, COMPLETED, PARTIAL, CANCELLED, DENIED, UNAVAILABLE }

/**
 * The candidate pass: a caller that can turn still-unresolved domains into extra features.
 *
 * A function type rather than an interface so a scan can be given one inline, and suspending because
 * expanding a bank means listing approved roots through the same bounded access the scan already holds.
 * It returns features to *attempt*, never conclusions: what a candidate interface means is decided by
 * the same resolution stages as everything else.
 */
typealias AtlasCompletion = suspend (Set<AtlasDomain>) -> List<AtlasFeatureRequest>

/** One feature to resolve, with the volatility that decides how long its evidence stays usable. */
data class AtlasFeatureRequest(
    val request: AtlasProbeRequest,
    val volatility: AtlasVolatility,
)

/**
 * An immutable snapshot of what the scan knows (`T5.2`).
 *
 * Published from a repository rather than assembled in a composable, so a recomposition cannot start
 * work and a screen cannot invent progress. Nothing here is derived from a timer: [percent] comes from
 * feature counts alone, and is `null` when the total is unknown instead of a made-up zero.
 */
data class AtlasScanState(
    val status: AtlasScanStatus = AtlasScanStatus.IDLE,
    val generation: Long = 0L,
    /** How many features this scan intends to resolve. Zero means "unknown", not "none". */
    val totalFeatures: Int = 0,
    val outcomes: List<AtlasFeatureOutcome> = emptyList(),
    val attempts: Int = 0,
    val cacheHits: Int = 0,
    val suppressed: Int = 0,
    val message: String? = null,
) {


    val observed: List<AtlasFeatureOutcome.Observed> get() = outcomes.filterIsInstance<AtlasFeatureOutcome.Observed>()
    val unresolved: List<AtlasFeatureOutcome.Unresolved> get() = outcomes.filterIsInstance<AtlasFeatureOutcome.Unresolved>()

    /** True while a scan is in flight. */
    val isRunning: Boolean get() = status == AtlasScanStatus.RUNNING

    /**
     * Progress over *features*, or `null` when the total is unknown.
     *
     * There is no timer anywhere in this computation: a bar that creeps forward while nothing is being
     * read would be the fake progress this project refuses. Four of five features answered is 80%;
     * the same four with no known total is `null`, which the UI must render as unknown rather than 0.
     */
    val percent: Int? =
        if (totalFeatures <= 0) null
        else ((outcomes.size.toLong() * 100L) / totalFeatures.toLong()).toInt().coerceIn(0, 100)

    /** Features still to be resolved. */
    val remaining: Int get() = (totalFeatures - outcomes.size).coerceAtLeast(0)

    /**
     * Unresolved interfaces that a **reviewed** entry addressed.
     *
     * Kept apart from the candidate pass on purpose: a candidate interface is a name the community
     * knows, so it being missing on this device is the expected case and not a finding. A reviewed
     * interface that did not answer is the event a maintainer can act on.
     */
    val reviewedUnresolved: List<AtlasFeatureOutcome.Unresolved>
        get() = unresolved.filter { it.stage != AtlasStage.CANDIDATE_INTERFACE }

    /** Outcomes that came from the community bank, so a screen can count them without re-deriving it. */
    val candidateOutcomes: List<AtlasFeatureOutcome> get() = outcomes.filter { outcomeStage(it) == AtlasStage.CANDIDATE_INTERFACE }

    /**
     * Whether a report may be suggested yet (`T7.6`).
     *
     * A cancelled or still-running scan never qualifies: suggesting that the user send a diagnostic
     * report while the run is incomplete would misrepresent what the report contains. A finished scan
     * whose reviewed interfaces all answered qualifies for nothing either — there is nothing to report
     * about a device that answered everything it was asked. A candidate name that was not there does
     * not qualify on its own: that is what the second line of defense is expected to find.
     */
    val reportEligible: Boolean
        get() = (status == AtlasScanStatus.COMPLETED || status == AtlasScanStatus.PARTIAL ||
            status == AtlasScanStatus.DENIED || status == AtlasScanStatus.UNAVAILABLE) &&
            reviewedUnresolved.isNotEmpty()

    private fun outcomeStage(outcome: AtlasFeatureOutcome): AtlasStage = when (outcome) {
        is AtlasFeatureOutcome.Observed -> outcome.stage
        is AtlasFeatureOutcome.Unresolved -> outcome.stage
        is AtlasFeatureOutcome.Suppressed -> AtlasStage.REVIEWED_KNOWLEDGE
        is AtlasFeatureOutcome.Cancelled -> AtlasStage.BOUNDED_DISCOVERY
    }
}

/**
 * One coalesced scan, exposed as immutable state (`T5.2`, `T5.6`).
 *
 * Design points that are load-bearing:
 *
 * - **Constructors do not scan.** Creating this object reads nothing; a scan starts only when a caller
 *   asks for one, so a dependency-injection graph cannot perform I/O on the main thread.
 * - **One scan at a time.** A second `start` while one is running is refused and the caller keeps
 *   observing the same state, so two screens cannot double the read budget.
 * - **Cancellation is published immediately and outranks a late completion.** A result that arrives
 *   after the user left the screen must not overwrite `CANCELLED` with `COMPLETED`.
 * - **Failure memory is used, not re-derived.** A suppressed path never reaches the device, and the
 *   ledger stops remembering a path the moment it answers.
 */
class AtlasRepository(
    private val store: AtlasEvidenceStore,
    private val resolver: AtlasResolver,
    private val ledger: AtlasFailureLedger,
    private val identity: AtlasDeviceIdentity,
    private val catalog: AtlasCatalog,
    dispatcher: CoroutineDispatcher,
    private val bootGeneration: () -> Long = { 0L },
    private val privilegeGeneration: () -> Long = { 0L },
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(AtlasScanState())
    val state: StateFlow<AtlasScanState> = _state.asStateFlow()

    private var scan: Job? = null
    private var generationCounter: Long = 0L

    /** The context fingerprint the current scan is keyed to. Private, and never reported. */
    fun fingerprint(): String = store.fingerprint(
        catalogVersion = catalog.version,
        identity = identity,
        bootGeneration = bootGeneration(),
        privilegeGeneration = privilegeGeneration(),
    )

    /**
     * Starts a scan, or refuses when one is already running.
     *
     * Returns true when this call started the scan and false when it coalesced into the running one.
     */
    fun start(
        features: List<AtlasFeatureRequest>,
        /**
         * The second line of defense. `null` means this scan asks only the first bank.
         *
         * Declared **before** [discover] on purpose: the discovery function is the one callers write as a
         * trailing lambda, and a parameter added after it would silently become the lambda's target at
         * every existing call site instead of failing to compile.
         */
        complete: AtlasCompletion? = null,
        discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
    ): Boolean {
        if (scan?.isActive == true) return false
        val generation = generationCounter + 1
        generationCounter = generation
        val fingerprint = fingerprint()
        _state.value = AtlasScanState(
            status = AtlasScanStatus.RUNNING,
            generation = generation,
            totalFeatures = features.size,
        )
        scan = scope.launch {
            runScan(features, fingerprint, generation, discover, complete)
        }

        return true
    }

    /**
     * Cancels the running scan. The state says `CANCELLED` from this moment, and a result that arrives
     * later cannot change it — that is the difference between "the user left" and "the device has
     * nothing", and only one of those belongs in a report.
     */
    fun cancel(reason: String = "cancelled before the scan finished") {
        val running = scan
        if (running?.isActive == true) {
            running.cancel(CancellationException(reason))
            _state.value = _state.value.copy(status = AtlasScanStatus.CANCELLED, message = reason)
        }
    }

    /** A retry is a new scan: it reuses held evidence and re-attempts only what is stale or unknown. */
    fun retry(
        features: List<AtlasFeatureRequest>,
        complete: AtlasCompletion? = null,
        discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
    ): Boolean = start(features, complete, discover)

    /** Drops every cached observation. The next scan reads the device again. */
    fun invalidate(): Int {
        val removed = store.clear()
        _state.value = AtlasScanState()
        return removed
    }

    private suspend fun runScan(
        features: List<AtlasFeatureRequest>,
        fingerprint: String,
        generation: Long,
        discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
        complete: AtlasCompletion?,
    ) {
        val outcomes = mutableListOf<AtlasFeatureOutcome>()
        var attempts = 0
        var hits = 0
        var suppressed = 0
        var total = features.size
        try {
            features.forEach { feature ->
                // Checked before every feature: without this a cancelled job would keep reading the
                // device until the whole list finished.
                currentCoroutineContext().ensureActive()
                val suppression = ledger.suppressionFor(feature.request.path)
                if (suppression != null) suppressed += 1
                val outcome = resolver.resolve(
                    request = feature.request,
                    volatility = feature.volatility,
                    fingerprint = fingerprint,
                    suppressedBecause = suppression,
                    discover = discover,
                )
                outcomes += outcome
                when (outcome) {
                    is AtlasFeatureOutcome.Observed -> {
                        if (outcome.fromCache) hits += 1 else attempts += 1
                        ledger.noteSuccess(feature.request.path)
                    }

                    is AtlasFeatureOutcome.Unresolved -> {
                        attempts += 1
                        ledger.record(feature.request.path, outcome.failure)
                    }

                    else -> Unit
                }
                publish(outcomes, attempts, hits, suppressed, generation, total, AtlasScanStatus.RUNNING, null)
            }
            currentCoroutineContext().ensureActive()

            // ---- the second line of defense ------------------------------------------------------
            // Only the domains the first bank could not resolve are asked about, and only interfaces
            // no attempt has addressed yet. This is completion, not a second opinion: a reviewed answer
            // is never re-asked, and a candidate answer never overwrites one.
            if (complete != null) {
                val unresolvedDomains = unresolvedDomains(features, outcomes)
                if (unresolvedDomains.isNotEmpty()) {
                    val candidates = complete(unresolvedDomains)
                    total += candidates.size
                    publish(outcomes, attempts, hits, suppressed, generation, total, AtlasScanStatus.RUNNING, null)
                    candidates.forEach { feature ->
                        currentCoroutineContext().ensureActive()
                        val suppression = ledger.suppressionFor(feature.request.path)
                        if (suppression != null) suppressed += 1
                        val outcome = resolver.resolve(
                            request = feature.request,
                            volatility = feature.volatility,
                            fingerprint = fingerprint,
                            suppressedBecause = suppression,
                            stage = AtlasStage.CANDIDATE_INTERFACE,
                            discover = discover,
                        )
                        outcomes += outcome
                        when (outcome) {
                            is AtlasFeatureOutcome.Observed -> {
                                if (outcome.fromCache) hits += 1 else attempts += 1
                                ledger.noteSuccess(feature.request.path)
                            }

                            is AtlasFeatureOutcome.Unresolved -> {
                                attempts += 1
                                ledger.record(feature.request.path, outcome.failure)
                            }

                            else -> Unit
                        }
                        publish(outcomes, attempts, hits, suppressed, generation, total, AtlasScanStatus.RUNNING, null)
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            publish(
                outcomes = outcomes,
                attempts = attempts,
                hits = hits,
                suppressed = suppressed,
                generation = generation,
                totalFeatures = total,
                status = finalStatus(outcomes),
                message = null,
            )
        } catch (cancelled: CancellationException) {
            // The state was already published as CANCELLED by `cancel`. Nothing is recomputed here:
            // a late arrival must not turn a cancelled scan into a finished one.
            throw cancelled
        }
    }

    private fun publish(
        outcomes: List<AtlasFeatureOutcome>,
        attempts: Int,
        hits: Int,
        suppressed: Int,
        generation: Long,
        totalFeatures: Int,
        status: AtlasScanStatus,
        message: String?,
    ) {
        _state.value = AtlasScanState(
            status = status,
            generation = generation,
            totalFeatures = totalFeatures,
            outcomes = outcomes.toList(),
            attempts = attempts,
            cacheHits = hits,
            suppressed = suppressed,
            message = message,
        )
    }

    /**
     * The domains still unresolved after the reviewed pass, derived from the outcomes and the feature
     * list that produced them. A feature resolved from held evidence is not unresolved, and a candidate
     * feature does not appear here because this pass runs before any candidate was asked.
     */
    private fun unresolvedDomains(
        features: List<AtlasFeatureRequest>,
        outcomes: List<AtlasFeatureOutcome>,
    ): Set<AtlasDomain> {
        val byId = features.associateBy { it.request.id }
        return outcomes
            .filterIsInstance<AtlasFeatureOutcome.Unresolved>()
            .mapNotNull { byId[it.id]?.request?.domain }
            .toSet()
    }

    /** The summary status, derived from the outcomes and never from a timer or a hoped-for total. */
    private fun finalStatus(outcomes: List<AtlasFeatureOutcome>): AtlasScanStatus = when {
        outcomes.any { it is AtlasFeatureOutcome.Cancelled } -> AtlasScanStatus.CANCELLED
        outcomes.isEmpty() -> AtlasScanStatus.COMPLETED
        outcomes.all { it is AtlasFeatureOutcome.Observed } -> AtlasScanStatus.COMPLETED
        outcomes.none { it is AtlasFeatureOutcome.Observed } &&
            outcomes.all { it is AtlasFeatureOutcome.Suppressed } -> AtlasScanStatus.DENIED
        outcomes.none { it is AtlasFeatureOutcome.Observed } &&
            outcomes.filterIsInstance<AtlasFeatureOutcome.Unresolved>()
                .all { it.failure == AtlasFailure.BACKEND_UNAVAILABLE } -> AtlasScanStatus.UNAVAILABLE
        else -> AtlasScanStatus.PARTIAL
    }
}
