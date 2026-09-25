/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

/**
 * Evidence lifetime (`P12`).
 *
 * Atlas has two different questions hiding behind one word, "old", and conflating them is how a
 * stale number reaches a decision:
 *
 * - **How long is a *value* still a statement about now?** A frequency, a temperature or a load
 *   average is a statement about a moment. Seconds later it is history, and a UI that shows it as
 *   live is lying.
 * - **How long is an *identity* or *capability* still a statement about the device?** "This phone
 *   exposes a readable GPU clock" stays true across a whole boot and unchanged permissions, and stops
 *   being true after a reboot or a privilege change.
 *
 * [AtlasVolatility] separates those, and the generation check is what makes the second case real.
 *
 * **[AtlasFailure.STALE] still has no emitter, and this file deliberately does not become one.**
 * [AtlasFailureLedger] documents why: staleness says "we hold an old value", not "reading failed", so a
 * read attempt must not report it. The place that serves held evidence is `P5`'s resolver, and that is
 * where the cause belongs. What this file supplies is the **rule** and a **reason-preserving verdict**
 * ([AtlasStaleness]), so that resolver emits the cause *with* a reason rather than inventing one.
 */
enum class AtlasVolatility {
    /** Read again in seconds; a displayed value that is older than this must not be presented as live. */
    INSTANT,

    /** Normal telemetry cadence. */
    FAST,

    /** Slow-moving quantities where a minute of age changes nothing. */
    SLOW,

    /** Identity or capability: valid until the boot or the privilege generation changes. */
    STATIC,
}

/**
 * One observation's vintage, and the rules that expire it.
 *
 * The generations recorded here are the ones **in force when the observation was made**, not the
 * current ones: an observation cannot be trusted just because the caller's clock happens to be
 * ahead of it.
 */
data class AtlasFreshness(
    val volatility: AtlasVolatility,
    val observedAtElapsedMs: Long,
    val bootGeneration: Long,
    val privilegeGeneration: Long,
) {
    init {
        require(observedAtElapsedMs >= 0L) { "elapsed time is non-negative" }
        require(bootGeneration >= 0L) { "generations are non-negative" }
        require(privilegeGeneration >= 0L) { "generations are non-negative" }
    }

    fun ttlMs(): Long = AtlasFreshnessPolicy.ttlMs(volatility)

    /** Age in milliseconds, or `null` when the clock is not ahead of the observation. */
    fun ageMsAt(nowMs: Long): Long? = (nowMs - observedAtElapsedMs).takeIf { it >= 0L }

    /**
     * Why this evidence may no longer be used, or [AtlasStaleness.FRESH] when it may.
     *
     * Checks are applied in a fixed order — boot, then privilege, then the clock — so that a given
     * piece of evidence yields **one** reason, not whichever one a caller happened to test first. A
     * report that said "expired" when the real cause was "another boot installed" would be sending the
     * maintainer to the wrong source.
     */
    fun stalenessAt(nowMs: Long, bootGeneration: Long, privilegeGeneration: Long): AtlasStaleness {
        if (bootGeneration != this.bootGeneration) return AtlasStaleness.SUPERSEDED_BY_BOOT
        if (privilegeGeneration != this.privilegeGeneration) return AtlasStaleness.SUPERSEDED_BY_PRIVILEGE
        val age = ageMsAt(nowMs) ?: return AtlasStaleness.UNMEASURABLE_CLOCK
        return if (age > ttlMs()) AtlasStaleness.EXPIRED_BY_TIME else AtlasStaleness.FRESH
    }

    /**
     * True when this evidence may no longer be used as a statement about the present.
     *
     * A clock that moved backwards is **not** treated as fresh: an unmeasurable age is not a young
     * age, and the safe direction is to re-read rather than to serve a value nobody can age.
     */
    fun isStaleAt(nowMs: Long, bootGeneration: Long, privilegeGeneration: Long): Boolean =
        stalenessAt(nowMs, bootGeneration, privilegeGeneration).isStale
}

/**
 * Why held evidence is no longer usable (`P12`).
 *
 * Four different situations used to collapse into one boolean, and only one of them is a clock. That is
 * the opposite of what the rest of Atlas does with causes: `P2` preserves all eleven rather than the
 * first one it notices, and the support report has to say *why* a value fell back. "Old" is not a
 * reason a maintainer can act on; "another boot installed" and "one second past the lifetime" are.
 */
enum class AtlasStaleness {

    /** Usable as a statement about the present. */
    FRESH,

    /** The value is older than its [AtlasVolatility] allows. */
    EXPIRED_BY_TIME,

    /** A different boot installed, so capability claims from the previous boot are void. */
    SUPERSEDED_BY_BOOT,

    /** The privilege generation changed, so what *was* readable is not necessarily readable now. */
    SUPERSEDED_BY_PRIVILEGE,

    /**
     * The clock is not ahead of the observation, so the age cannot be measured. Unusable by default:
     * an unmeasurable age is not a young age.
     */
    UNMEASURABLE_CLOCK,
    ;

    /** The summary a boolean caller (and every existing call site) needs. */
    val isStale: Boolean get() = this != FRESH
}

/**
 * The lifetime table.
 *
 * These are **design values, not measurements**: no device timing evidence exists yet, and they are
 * the `P5.4` lifetimes of `01-PLAN.md` §15 (`DECISION-1`) expressed once, in the product, so a test
 * cannot enforce a lifetime the shipped code does not use.
 */
object AtlasFreshnessPolicy {

    /** One second: a live-looking value must be re-read before it is shown as live. */
    const val INSTANT_TTL_MS: Long = 1_000L

    const val FAST_TTL_MS: Long = 10_000L

    const val SLOW_TTL_MS: Long = 60_000L

    /**
     * `STATIC` never expires by time alone. Its expiry is a **generation change** (reboot or privilege
     * change), which is the only thing that can make "this device exposes this interface" false.
     */
    const val STATIC_TTL_MS: Long = Long.MAX_VALUE

    /**
     * The hard cap on how long *value* evidence may be served from cache. It deliberately does not
     * apply to `STATIC` identity/capability evidence, whose bound is the generation, not a clock.
     */
    const val MAX_POSITIVE_CACHE_TTL_MS: Long = 10 * 60_000L

    fun ttlMs(volatility: AtlasVolatility): Long = when (volatility) {
        AtlasVolatility.INSTANT -> INSTANT_TTL_MS
        AtlasVolatility.FAST -> FAST_TTL_MS
        AtlasVolatility.SLOW -> SLOW_TTL_MS
        AtlasVolatility.STATIC -> STATIC_TTL_MS
    }

    /** Provenance, so nobody mistakes these lifetimes for measured ones. */
    const val PROVENANCE: String = "design values; unmeasured; P5.4 lifetimes of 01-PLAN §15 (DECISION-1)"
}
