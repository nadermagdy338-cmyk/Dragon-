package nd.max.core.atlas

import kotlinx.coroutines.CancellationException

/**
 * Which stage produced an outcome (`P5`, slice `E`).
 *
 * The order is the product rule: reviewed knowledge and already-held evidence first, bounded
 * discovery second, and an explicit unresolved result before anything is suggested to the user.
 */
enum class AtlasStage {
    /** Answered from the catalog and the evidence store, without touching the device. */
    REVIEWED_KNOWLEDGE,

    /** Answered by one bounded read attempt on a reviewed catalog interface. */
    BOUNDED_DISCOVERY,

    /**
     * Answered by the community bank, for a domain the reviewed catalog could not resolve.
     *
     * It is its own stage rather than a flavour of discovery because it is weaker evidence: a reviewed
     * interface says "this is what has been reviewed to mean", a candidate interface says "this name is
     * known to exist". A reader that cannot tell the two apart would eventually treat a candidate name
     * as a reviewed fact, which is exactly the confusion this stage exists to prevent.
     */
    CANDIDATE_INTERFACE,
}

/**
 * One feature's resolution. Every state is distinguishable on purpose: a cancelled job and an
 * exhausted device look identical in a progress bar and mean completely different things to the
 * person reading it, and only one of them is worth a support report.
 */
sealed interface AtlasFeatureOutcome {
    val id: String

    data class Observed(
        override val id: String,
        val observation: AtlasObservation,
        val stage: AtlasStage,
        /** True when held evidence answered and nothing was read from the device this time. */
        val fromCache: Boolean,
        val cacheMiss: AtlasCacheMiss? = null,
    ) : AtlasFeatureOutcome

    data class Unresolved(
        override val id: String,
        val failure: AtlasFailure,
        val reason: String,
        val stage: AtlasStage,
        val cacheMiss: AtlasCacheMiss? = null,
    ) : AtlasFeatureOutcome

    /** Skipped before any attempt because a recent attempt failed and a retry could not change it. */
    data class Suppressed(
        override val id: String,
        val cause: AtlasFailure,
        val reason: String,
    ) : AtlasFeatureOutcome

    /**
     * The job was cancelled. Deliberately not a failure: no conclusion about the device follows from
     * the user leaving the screen, so this must never be reported as exhaustion.
     */
    data class Cancelled(
        override val id: String,
        val reason: String,
    ) : AtlasFeatureOutcome
}

/**
 * Ordered per-feature resolution (`T5.1`, `T5.4`).
 *
 * Two rules decide the whole design:
 *
 * 1. **A feature that is already answered is not asked again.** A fresh cached observation returns
 *    immediately, so a second scan of the same list performs zero reads — the property that makes a
 *    bounded budget meaningful.
 * 2. **Cancellation is not a cause.** `CancellationException` is rethrown, never converted into
 *    `Unresolved`: a cancelled job is a fact about the job, not about the device. Everything else a
 *    discovery stage can throw becomes `UNKNOWN_CAUSE` with a reason, because that is what it is —
 *    an error we did not recognize — rather than `ABSENT`, which would be a claim we cannot support.
 */
class AtlasResolver(
    private val store: AtlasEvidenceStore,
    private val clockMs: () -> Long,
    private val bootGeneration: () -> Long = { 0L },
    private val privilegeGeneration: () -> Long = { 0L },
) {

    /** The freshness verdict of held evidence, exposed so a caller can explain a re-read. */
    fun heldStaleness(observationId: String, fingerprint: String): AtlasStaleness? {
        val lookup = store.load(observationId, fingerprint)
        if (lookup !is AtlasCacheLookup.Hit) return null
        return lookup.evidence.freshnessAt().stalenessAt(clockMs(), bootGeneration(), privilegeGeneration())
    }

    suspend fun resolve(
        request: AtlasProbeRequest,
        volatility: AtlasVolatility,
        fingerprint: String,
        /** Recorded so a caller can say "we already knew this" without re-deriving the decision. */
        suppressedBecause: AtlasSuppression? = null,
        /**
         * Which pass produced this attempt. Defaulted so every existing caller keeps its meaning, and
         * carried into the outcome because "a candidate name was not there" and "a reviewed interface
         * failed" must never be counted as the same event.
         */
        stage: AtlasStage = AtlasStage.BOUNDED_DISCOVERY,
        discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
    ): AtlasFeatureOutcome {
        if (suppressedBecause != null) {
            return AtlasFeatureOutcome.Suppressed(
                id = request.id,
                cause = suppressedBecause.cause,
                reason = suppressedBecause.reason,
            )
        }

        // ---- stage 1: reviewed knowledge and held evidence ------------------------------------------
        val lookup = store.load(request.id, fingerprint)
        val miss = (lookup as? AtlasCacheLookup.Miss)?.reason
        if (lookup is AtlasCacheLookup.Hit) {
            val verdict = lookup.evidence.freshnessAt()
                .stalenessAt(clockMs(), bootGeneration(), privilegeGeneration())
            if (verdict == AtlasStaleness.FRESH) {
                return AtlasFeatureOutcome.Observed(
                    id = request.id,
                    observation = lookup.evidence.observation,
                    // Held evidence answers in the reviewed-knowledge stage whatever bank asked for it:
                    // what is being reported is "this is what we already held", and that is the same
                    // fact for a candidate interface as for a reviewed one.
                    stage = AtlasStage.REVIEWED_KNOWLEDGE,
                    fromCache = true,
                )
            }
        }

        // ---- stage 2: bounded discovery ------------------------------------------------------------
        // Stage 3 (candidate interfaces) is not decided here: it is a per-scan decision made after the
        // reviewed pass, because "which domains are still unresolved" is only known when the pass ends.
        val result = try {
            discover(request)
        } catch (cancelled: CancellationException) {
            // Rethrown on purpose: see the class documentation. A cancellation the caller cannot
            // distinguish from exhaustion is a lie by omission.
            throw cancelled
        } catch (error: Exception) {
            AtlasReadResult.Rejected(
                path = request.path,
                failure = AtlasFailure.UNKNOWN_CAUSE,
                reason = "the discovery stage threw ${error.javaClass.simpleName} before it could answer",
            )
        }

        return when (result) {
            is AtlasReadResult.Observed -> {
                // A write failure here loses speed, never truth: the observation is returned either way.
                store.save(result.observation, volatility, fingerprint)
                AtlasFeatureOutcome.Observed(
                    id = request.id,
                    observation = result.observation,
                    stage = stage,
                    fromCache = false,
                    cacheMiss = miss,
                )
            }

            is AtlasReadResult.Rejected -> AtlasFeatureOutcome.Unresolved(
                id = request.id,
                failure = result.failure,
                reason = result.reason,
                stage = stage,
                cacheMiss = miss,
            )
        }
    }

    companion object {
        /** How one catalogue entry's volatility is chosen, in one place. */
        fun volatilityFor(entry: AtlasCatalogEntry): AtlasVolatility = volatilityForDomain(entry.domain)

        /**
         * The same rule, for an interface that is not a catalog entry (a candidate interface).
         *
         * It exists so a second copy of the table cannot appear: a candidate reading's lifetime must be
         * the same as the reviewed reading of the same domain, or the cache would hold one and evict the
         * other for a reason nobody could explain.
         */
        fun volatilityForDomain(domain: AtlasDomain): AtlasVolatility = when (domain) {
            AtlasDomain.CPU, AtlasDomain.GPU, AtlasDomain.THERMAL, AtlasDomain.MEMORY -> AtlasVolatility.INSTANT
            AtlasDomain.POWER -> AtlasVolatility.FAST
            AtlasDomain.STORAGE -> AtlasVolatility.SLOW
            // Identity and capability: bound by the boot and the privilege generation, not by a clock.
            AtlasDomain.DISPLAY, AtlasDomain.SENSOR, AtlasDomain.NETWORK, AtlasDomain.PRIVILEGE ->
                AtlasVolatility.STATIC
        }
    }
}
