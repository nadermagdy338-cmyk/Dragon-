/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.diagnostics

import nd.max.core.atlas.AtlasFeatureOutcome
import nd.max.core.atlas.AtlasScanState
import nd.max.core.atlas.AtlasScanStatus
import nd.max.core.atlas.AtlasStage
import nd.max.core.diagnostics.AtlasSupportReport.Companion.OUTCOME_CANCELLED
import nd.max.core.diagnostics.AtlasSupportReport.Companion.OUTCOME_OBSERVED
import nd.max.core.diagnostics.AtlasSupportReport.Companion.OUTCOME_SUPPRESSED
import nd.max.core.diagnostics.AtlasSupportReport.Companion.OUTCOME_UNRESOLVED

/**
 * The Atlas doctor (`P13`): does a replayed device still reproduce the report it came from?
 *
 * This is the maintainer's loop, and it exists because "we have a fixture" and "we have a regression
 * test" are not the same sentence. A fixture replays a device; the [AtlasSupportReport] a user sent is
 * the only record of what that device *was asked and answered*. Putting them side by side is what turns
 * a report into a test that fails when behaviour changes — and what stops a support change from being
 * justified by an impression.
 *
 * Four rules shape the verdict, and each one exists to stop a specific way of lying to ourselves:
 *
 * 1. **An unfinished or cancelled replay is refused, not compared.** A partial run differs from a
 *    complete report because it stopped, so a comparison would report a difference caused by the test
 *    harness and present it as a behaviour change.
 * 2. **A different catalog revision is refused.** Outcomes produced under different knowledge are not
 *    a reproduction of anything. The revision is compared, not merely mentioned.
 * 3. **A missing candidate interface is not a finding.** The community bank is a second line of
 *    defense: a name it knows and this device lacks is the *expected* case. Those differences are
 *    returned as `advisories` and never as divergence, so a support loop cannot be opened by a name
 *    that was never reviewed.
 * 4. **Every difference names both sides.** "It changed" is not actionable; `reported`/`replayed` are
 *    carried so a maintainer sees what the device used to answer and what it answers now.
 *
 * The comparison is pure: it reads no device, opens no file, and starts no scan. The caller replays a
 * fixture through the shipped boundary and hands the result in.
 */
object AtlasDoctor {

    /** What one side of a comparison says about a feature. */
    private data class Signature(
        val outcome: String,
        val failure: String?,
        val unit: String?,
        val stage: String?,
    )

    /** How two signatures differ. Each one is a different kind of behaviour change. */
    enum class Kind {
        /** The report has this feature and the replay produced nothing for it. */
        MISSING_IN_REPLAY,

        /** The replay produced a feature the report never mentioned. */
        EXTRA_IN_REPLAY,

        /** Observed versus unresolved, suppressed or cancelled. */
        OUTCOME_CHANGED,

        /** Both unresolved, with different causes. */
        CAUSE_CHANGED,

        /** Both observed, with different units. */
        UNIT_CHANGED,

        /** Both the same outcome, reached at a different resolution stage. */
        STAGE_CHANGED,
    }

    /** One difference, naming both sides so it can be acted on rather than guessed at. */
    data class Difference(
        val id: String,
        val kind: Kind,
        val reported: String,
        val replayed: String,
    )

    sealed interface Verdict {

        /** The replayed device reproduces the report. Advisories may still be present. */
        data class Reproduced(val features: Int, val advisories: List<Difference>) : Verdict

        /** A reviewed feature behaves differently now. */
        data class Diverged(val differences: List<Difference>, val advisories: List<Difference>) : Verdict

        /** The comparison was not a comparison. Says why, and never guesses. */
        data class Refused(val reason: String) : Verdict
    }

    /**
     * Compares a stored report with a replayed run.
     *
     * @param catalogVersion the revision the *replay* ran under. A mismatch with the report's revision
     *   is a refusal: the doctor cannot tell a device change from a knowledge change, and pretending
     *   otherwise is how a support file grows an entry that describes the wrong thing.
     */
    fun compare(report: AtlasSupportReport, state: AtlasScanState, catalogVersion: String): Verdict {
        if (state.status == AtlasScanStatus.IDLE || state.isRunning) {
            return Verdict.Refused("the replayed run has not finished; a partial run is not a comparison")
        }
        if (state.status == AtlasScanStatus.CANCELLED) {
            return Verdict.Refused("the replayed run was cancelled, so its differences describe the interruption")
        }
        if (report.catalogVersion != catalogVersion) {
            return Verdict.Refused(
                "the report was taken with catalog ${report.catalogVersion} and the replay ran with $catalogVersion",
            )
        }

        val replayed = state.outcomes.associateBy { it.id }
        val differences = mutableListOf<Difference>()
        val advisories = mutableListOf<Difference>()

        report.features.forEach { reported ->
            val actual = replayed[reported.id]
            if (actual == null) {
                differences += Difference(
                    id = reported.id,
                    kind = Kind.MISSING_IN_REPLAY,
                    reported = describeReported(reported),
                    replayed = "nothing",
                )
                return@forEach
            }
            val replaySignature = signatureOf(actual)
            val replayText = describe(replaySignature)
            when {
                replaySignature.outcome != reported.outcome -> differences += Difference(
                    id = reported.id,
                    kind = Kind.OUTCOME_CHANGED,
                    reported = describeReported(reported),
                    replayed = replayText,
                )

                replaySignature.failure != reported.failure -> differences += Difference(
                    id = reported.id,
                    kind = Kind.CAUSE_CHANGED,
                    reported = describeReported(reported),
                    replayed = replayText,
                )

                replaySignature.unit != reported.unit -> differences += Difference(
                    id = reported.id,
                    kind = Kind.UNIT_CHANGED,
                    reported = describeReported(reported),
                    replayed = replayText,
                )

                replaySignature.stage != reported.stage -> differences += Difference(
                    id = reported.id,
                    kind = Kind.STAGE_CHANGED,
                    reported = describeReported(reported),
                    replayed = replayText,
                )
            }
        }

        val reportedIds = report.features.map { it.id }.toSet()
        state.outcomes.forEach { outcome ->
            if (outcome.id !in reportedIds) {
                differences += Difference(
                    id = outcome.id,
                    kind = Kind.EXTRA_IN_REPLAY,
                    reported = "nothing",
                    replayed = describe(signatureOf(outcome)),
                )
            }
        }

        // A candidate-stage difference is expected, not a regression: the second bank is asked only for
        // a gap, and a name it knows being absent on this device is what "candidate" means.
        val (candidateStage, reviewedStage) = differences.partition { difference ->
            replayed[difference.id]?.let { stageOf(it) == AtlasStage.CANDIDATE_INTERFACE } == true ||
                difference.id in candidateIds(report)
        }
        val ordered = reviewedStage.sortedWith(ORDER)
        val advisory = (candidateStage + advisories).sortedWith(ORDER)

        return if (ordered.isEmpty()) {
            Verdict.Reproduced(features = report.features.size, advisories = advisory)
        } else {
            Verdict.Diverged(differences = ordered, advisories = advisory)
        }
    }

    /** Ids the report itself came from the candidate stage. Stage vocabulary, not a new name. */
    private fun candidateIds(report: AtlasSupportReport): Set<String> =
        report.features.filter { it.stage == AtlasStage.CANDIDATE_INTERFACE.name }.map { it.id }.toSet()

    private fun stageOf(outcome: AtlasFeatureOutcome): AtlasStage = when (outcome) {
        is AtlasFeatureOutcome.Observed -> outcome.stage
        is AtlasFeatureOutcome.Unresolved -> outcome.stage
        is AtlasFeatureOutcome.Suppressed -> AtlasStage.REVIEWED_KNOWLEDGE
        is AtlasFeatureOutcome.Cancelled -> AtlasStage.BOUNDED_DISCOVERY
    }

    private fun signatureOf(outcome: AtlasFeatureOutcome): Signature = when (outcome) {
        is AtlasFeatureOutcome.Observed -> Signature(
            outcome = OUTCOME_OBSERVED,
            failure = null,
            unit = outcome.observation.unit.name,
            stage = outcome.stage.name,
        )

        is AtlasFeatureOutcome.Unresolved -> Signature(
            outcome = OUTCOME_UNRESOLVED,
            failure = outcome.failure.name,
            unit = null,
            stage = outcome.stage.name,
        )

        is AtlasFeatureOutcome.Suppressed -> Signature(
            outcome = OUTCOME_SUPPRESSED,
            failure = outcome.cause.name,
            unit = null,
            stage = AtlasStage.REVIEWED_KNOWLEDGE.name,
        )

        is AtlasFeatureOutcome.Cancelled -> Signature(
            outcome = OUTCOME_CANCELLED,
            failure = null,
            unit = null,
            stage = AtlasStage.BOUNDED_DISCOVERY.name,
        )
    }

    private fun describe(signature: Signature): String {
        val parts = mutableListOf(signature.outcome)
        signature.failure?.let { parts += "cause=$it" }
        signature.unit?.let { parts += "unit=$it" }
        signature.stage?.let { parts += "stage=$it" }
        return parts.joinToString(" ")
    }

    private fun describeReported(reported: AtlasSupportReport.ReportedFeature): String {
        val parts = mutableListOf(reported.outcome)
        reported.failure?.let { parts += "cause=$it" }
        reported.unit?.let { parts += "unit=$it" }
        parts += "stage=${reported.stage}"
        return parts.joinToString(" ")
    }

    private val ORDER: Comparator<Difference> = compareBy({ it.id }, { it.kind.name })
}
