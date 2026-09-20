package nd.max.core.atlas.support

/**
 * Deterministic monotonic clock (`P0`).
 *
 * Tests must never assert timing with sleeps. This clock is advanced by the test (or by a fake
 * transport that simulates a slow read), so a deadline boundary is an exact assertion rather than a
 * race. It also models a clock that moves backwards, which is a real failure mode for cache logic.
 */
class AtlasClock(startMs: Long = 0L, startGeneration: Long = 0L) {

    private var elapsedMs: Long = startMs
    private var generationValue: Long = startGeneration

    init {
        require(startMs >= 0L) { "clock cannot start before zero" }
        require(startGeneration >= 0L) { "generation is non-negative" }
    }

    fun nowMs(): Long = elapsedMs

    fun generation(): Long = generationValue

    fun advance(byMs: Long) {
        require(byMs >= 0L) { "advance must be non-negative" }
        elapsedMs += byMs
    }

    /** Models a clock that moved backwards (suspend/resume, NITZ correction, test rig). */
    fun rewind(byMs: Long) {
        require(byMs >= 0L) { "rewind must be non-negative" }
        elapsedMs = (elapsedMs - byMs).coerceAtLeast(0L)
    }

    fun bumpGeneration(): Long {
        generationValue += 1L
        return generationValue
    }

    fun deadlineFrom(startedAtMs: Long, budgetMs: Long): Long {
        require(startedAtMs >= 0L && budgetMs >= 0L) { "deadline inputs are non-negative" }
        return startedAtMs + budgetMs
    }

    fun isPastDeadline(deadlineMs: Long): Boolean = elapsedMs > deadlineMs

    /** A future timestamp is not fresh, and null (never attempted) is handled by the caller. */
    fun isStale(observedAtMs: Long, ttlMs: Long): Boolean {
        require(observedAtMs >= 0L && ttlMs >= 0L) { "freshness inputs are non-negative" }
        if (observedAtMs > elapsedMs) return true
        return (elapsedMs - observedAtMs) > ttlMs
    }
}

/**
 * A result may be published only if the generation it was produced in is still current. This is the
 * fence that keeps a late completion from a cancelled job out of the cache.
 */
object AtlasGenerationFence {
    fun mayPublish(resultGeneration: Long, currentGeneration: Long): Boolean =
        resultGeneration == currentGeneration
}
