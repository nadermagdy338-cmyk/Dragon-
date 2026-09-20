package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget
import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.AtlasTransportRead

/**
 * Turns one real run into a fixture (`P9.1`, and the recording half of `P13`'s doctor).
 *
 * The recorder asks the **same** [AtlasReadTransport] interface the app uses, so what it captures is
 * what the bounded boundary received — not a paraphrase written by whoever was debugging. It writes
 * nothing to the device: every call on that interface is a read, and the recorder adds no new verb.
 *
 * Three limits are asserted here rather than trusted:
 *
 * - **Only what may be addressed is recorded.** A path that is unsafe, or outside the approved
 *   anchors, is *skipped and named* — never silently dropped, because a fixture that quietly omits a
 *   path reads later as "the device did not have it".
 * - **The entry ceiling is the job's.** [AtlasReadBudget.DEFAULT] allows 512 entries for a whole scan;
 *   a recording that needed more than a scan would means the caller is recording something other than
 *   a scan.
 * - **A value stays a scalar.** A read longer than the allowance is recorded with its cut flag, exactly
 *   as the transport would have handed it over — the fixture reproduces truncation instead of hiding it.
 */
class AtlasFixtureRecorder(
    private val transport: AtlasReadTransport,
    private val maxBytes: Int = AtlasReadBudget.DEFAULT.maxScalarBytes,
    private val listLimit: Int = DEFAULT_LIST_LIMIT,
    private val maxHops: Int = AtlasReadBudget.DEFAULT.maxSymlinkHops,
) {

    init {
        require(maxBytes > 0) { "a read allowance must be positive" }
        require(listLimit > 0) { "a listing allowance must be positive" }
        require(maxHops > 0) { "symlink resolution needs a positive hop bound" }
    }

    /** A path the recorder refused to touch, with the reason it refused. */
    data class Skipped(val path: String, val reason: String)

    /**
     * What one recording produced.
     *
     * `truncated` is the entry ceiling being reached, not a failed read: the fixture is still usable
     * and still honest, it simply covers less than the caller asked for.
     */
    data class Result(
        val fixture: AtlasFixture,
        val skipped: List<Skipped>,
        val truncated: Boolean,
    )

    /**
     * Records one value attempt.
     *
     * A directory is *not* quietly recorded as a failed value: whatever the transport answers is
     * recorded verbatim, including a first-cause failure. Guessing a path's kind is what produced the
     * twelve catalog defects this track already paid for.
     */
    fun recordValue(state: State, path: String): State = when (val decision = accept(state, path)) {
        is Decision.Refused -> decision.state
        is Decision.Allowed -> {
            val attempt = transport.readText(path, maxBytes)
            val answer = when (attempt) {
                is AtlasTransportRead.Text -> AtlasFixtureAnswer.Text(attempt.text, attempt.truncated)
                is AtlasTransportRead.Failed -> failure(attempt)
            }
            decision.state.copy(fixture = decision.state.fixture.record(path, answer))
        }
    }

    /** Records one enumeration. Names are recorded as the transport handed them over. */
    fun recordDirectory(state: State, path: String): State = when (val decision = accept(state, path)) {
        is Decision.Refused -> decision.state
        is Decision.Allowed -> {
            val listing = transport.listNames(path, listLimit)
            val answer = if (listing.cause == AtlasFailure.NONE) {
                AtlasFixtureAnswer.Listing(
                    listing.names.distinct().sorted(),
                    truncated = listing.truncated || listing.names.size > listLimit,
                )
            } else {
                AtlasFixtureAnswer.Missing(listing.cause, listing.reason)
            }
            decision.state.copy(fixture = decision.state.fixture.record(path, answer))
        }
    }

    /**
     * Records resolution for one path.
     *
     * Recorded separately because it is a different question with a different answer: a path can be
     * readable and still be a link, and a fixture that dropped the hop would replay a *different*
     * device than the one that was measured.
     */
    fun recordCanonical(state: State, path: String): State = when (val decision = accept(state, path)) {
        is Decision.Refused -> decision.state
        is Decision.Allowed -> {
            val resolved = transport.canonicalPath(path, maxHops)
            val answer = if (resolved == null) AtlasFixtureAnswer.Unresolvable else AtlasFixtureAnswer.Canonical(resolved)
            decision.state.copy(fixture = decision.state.fixture.record(path, answer))
        }
    }

    /**
     * Records resolution and a value for every path, in that order.
     *
     * Directories are **not** enumerated here: enumeration is a decision about which roots a scan may
     * walk, and that decision belongs to the caller ([recordDirectory]) rather than to a loop that
     * guesses it from a failure reason.
     */
    fun record(paths: List<String>, origin: AtlasFixtureOrigin, catalogVersion: String): Result {
        var state = start(origin, catalogVersion)
        paths.forEach { path ->
            // The gate is asked once per path here, not once per call: two operations on one refused
            // path would otherwise record the same refusal twice, and a duplicated skip line makes a
            // fixture read as if two different things had been skipped.
            when (val decision = accept(state, path)) {
                is Decision.Refused -> state = decision.state
                is Decision.Allowed -> {
                    state = recordCanonical(decision.state, path)
                    state = recordValue(state, path)
                }
            }
        }
        return Result(state.fixture, state.skipped, state.truncated)
    }

    /** An empty recording, for a caller that records in several passes. */
    fun start(origin: AtlasFixtureOrigin, catalogVersion: String): State =
        State(AtlasFixture.empty(origin, catalogVersion), emptyList(), truncated = false)

    /** The recording in progress. Kept explicit so a caller can record in several passes. */
    data class State(
        val fixture: AtlasFixture,
        val skipped: List<Skipped>,
        val truncated: Boolean,
    )

    /** The outcome of the one gate every recording call passes through. */
    private sealed interface Decision {
        /** The path may be asked about, and the state to keep recording into. */
        data class Allowed(val state: State) : Decision

        /** The path must not be touched. The state carries the named reason, already recorded. */
        data class Refused(val state: State) : Decision
    }

    /**
     * The gate: unsafe paths, unapproved anchors and the entry ceiling stop the call before any
     * transport call happens.
     *
     * A refused path is **named** in [State.skipped] rather than dropped: a fixture that quietly
     * omitted it would replay later as "the device did not answer here", which is a claim nobody
     * measured.
     */
    private fun accept(state: State, path: String): Decision {
        if (!AtlasIds.isSafeAbsolutePath(path)) {
            return Decision.Refused(state.copy(skipped = state.skipped + Skipped(path, SKIP_UNSAFE_PATH)))
        }
        if (!AtlasAnchors.isApproved(path)) {
            return Decision.Refused(state.copy(skipped = state.skipped + Skipped(path, SKIP_UNAPPROVED)))
        }
        if (state.fixture.entries.size >= AtlasFixture.MAX_ENTRIES) {
            return Decision.Refused(state.copy(truncated = true))
        }
        return Decision.Allowed(state)
    }

    private fun failure(attempt: AtlasTransportRead.Failed): AtlasFixtureAnswer.Missing =
        AtlasFixtureAnswer.Missing(
            attempt.cause,
            attempt.reason.take(AtlasFixtureAnswer.MAX_REASON_CHARS).replace('\n', ' ').replace('\r', ' '),
        )

    companion object {
        /** Enough names for any approved enumerable root, and bounded like every other allowance. */
        const val DEFAULT_LIST_LIMIT: Int = 64

        /** Recorded skip reasons. Short codes, so a fixture diff stays readable. */
        const val SKIP_UNSAFE_PATH: String = "unsafe-path"
        const val SKIP_UNAPPROVED: String = "unapproved-anchor"
    }
}
