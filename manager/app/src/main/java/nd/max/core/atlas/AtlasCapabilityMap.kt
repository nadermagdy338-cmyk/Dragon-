/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

/**
 * The `Map` stage: the **Capability Map** — what MaxManager can actually do on *this* device.
 *
 * The map is the answer to one question per control target: how can this feature work here? Seven
 * answers exist, and exactly one of them is allowed to be optimistic:
 *
 * | State | It means | The evidence behind it |
 * | --- | --- | --- |
 * | [SUPPORTED] | a reviewed route exists **and** a write has been verified on this device | planner eligible + remembered verified outcome |
 * | [WRITABLE] | a reviewed route is eligible now; success is not yet proven | planner eligible |
 * | [READ_ONLY] | the device answers reads here, but no write route is eligible | measured + readable |
 * | [NEEDS_ADAPTER] | the feature reads here, and this build has no proven way to drive it | readable + no route known |
 * | [UNAVAILABLE] | absence was **proved** (a listing said so) | absence proved |
 * | [NEVER_TOUCH] | a reviewed safety rule forbids writing this interface | [AtlasSafetyPolicy] |
 * | [UNKNOWN] | nothing was measured, or nothing answered and absence was never proved | the honest default |
 *
 * Two rules decide everything below:
 *
 * 1. **The map derives; it never measures.** Every input is something another component established
 *    (a scan, a backend parser, the route planner, the route memory). A pure derivation is what makes
 *    the truth table testable and what keeps two surfaces — the diagnostics matrix and the execution
 *    report — from growing two answers to one question ([AtlasCapabilityRules] is the single table).
 * 2. **Unknown stays unknown.** "Not measured" is not "absent", and "could not read" is not "not
 *    supported". A device Atlas has not looked at is `UNKNOWN`, never `UNAVAILABLE`, and success is
 *    only ever claimed behind [SUPPORTED] — which requires a *verified* outcome, not a sent command.
 */
// AtlasCapabilityState is declared separately so read-only registries can use the vocabulary
// without pulling in the complete Atlas route planner for pure JVM tests.

/**
 * What a producer measured, in the map's vocabulary. Producers fill what they actually established
 * and leave the rest false — a false here means "not established", never "established as absent".
 */
data class AtlasCapabilityInputs(
    val target: AtlasControlTarget,
    val safety: AtlasSafetyVerdict = AtlasSafetyVerdict.Allowed,
    /** Did any measurement run for this target at all? */
    val measured: Boolean,
    /** Did a live read answer for this target? */
    val readable: Boolean,
    /** Was absence proved the only way this project ever claims it: a non-empty listing without the name? */
    val absenceProved: Boolean,
    /** Does this build know a route for this target on this device? */
    val routeKnown: Boolean,
    /** The route planner's verdict over the planned candidates, when any were planned. */
    val routeStatus: AtlasRouteStatus? = null,
    val routeReason: AtlasRouteReason? = null,
    /** Does the route memory remember a verified write for this target in this boot/privilege generation? */
    val verifiedThisGeneration: Boolean = false,
    /** The adapter that owns the route, when one exists. */
    val adapterId: String? = null,
    /**
     * Does this build claim a **control method** for the target at all?
     *
     * `false` means a monitoring-only surface: thermal zones read, and there is nothing here to adapt.
     * «للقراءة فقط» هي الحقيقة حينها، أما «يحتاج مُلاءِمًا» فتعدُد طريقًا لا يحتاجه أحد — والفارق
     * بينهما في قائمة القدرة نفسه هو الفرق بين «اقرأني» و«أصلِحني».
     */
    val writeExpected: Boolean = true,
)

/** One target's place on the map, with the reason code that put it there. */
data class AtlasCapability(
    val target: AtlasControlTarget,
    val state: AtlasCapabilityState,
    /** A stable machine code (`route-eligible`, `adapter-gap`, `safety:thermal-trips`), never a sentence. */
    val reason: String,
    val adapterId: String? = null,
    val routeReason: AtlasRouteReason? = null,
    val verified: Boolean = false,
) {
    /** The code a log line or support report carries, e.g. `map:read-only:read-only:route_not_reviewed`. */
    val code: String
        get() = buildString {
            append("map:").append(state.name.lowercase()).append(':').append(reason)
            routeReason?.let { append(':').append(it.name.lowercase()) }
        }
}

/**
 * The single derivation table. Precedence is fixed and tested:
 *
 * ```text
 * 1. safety denied                              -> NEVER_TOUCH  (nothing outranks this)
 * 2. route eligible + verified this generation  -> SUPPORTED    (the only state that claims success)
 * 3. route eligible                             -> WRITABLE
 * 4. no route known + reads + a method is claimed -> NEEDS_ADAPTER
 * 5. absence proved + nothing read                -> UNAVAILABLE
 * 6. the interface reads here                     -> READ_ONLY  (مراقبة حين لا يدّعي البناء طريقة)
 * 7. anything else                                -> UNKNOWN    (not-measured / not-observed)
 * ```
 */
object AtlasCapabilityRules {

    fun derive(inputs: AtlasCapabilityInputs): AtlasCapability {
        val denied = inputs.safety as? AtlasSafetyVerdict.Denied
        if (denied != null) {
            return capability(inputs, AtlasCapabilityState.NEVER_TOUCH, "safety:${denied.ruleId}")
        }
        if (inputs.routeStatus == AtlasRouteStatus.ELIGIBLE) {
            return if (inputs.verifiedThisGeneration) {
                capability(inputs, AtlasCapabilityState.SUPPORTED, "route-eligible+verified", verified = true)
            } else {
                capability(inputs, AtlasCapabilityState.WRITABLE, "route-eligible")
            }
        }
        if (!inputs.routeKnown && inputs.readable && inputs.writeExpected) {
            return capability(inputs, AtlasCapabilityState.NEEDS_ADAPTER, "adapter-gap")
        }
        if (inputs.absenceProved && !inputs.readable) {
            return capability(inputs, AtlasCapabilityState.UNAVAILABLE, "absence-proved")
        }
        if (inputs.readable) {
            return capability(inputs, AtlasCapabilityState.READ_ONLY, "read-only")
        }
        return capability(
            inputs,
            AtlasCapabilityState.UNKNOWN,
            if (inputs.measured) "not-observed" else "not-measured",
        )
    }

    private fun capability(
        inputs: AtlasCapabilityInputs,
        state: AtlasCapabilityState,
        reason: String,
        verified: Boolean = false,
    ): AtlasCapability = AtlasCapability(
        target = inputs.target,
        state = state,
        reason = reason,
        adapterId = inputs.adapterId,
        routeReason = inputs.routeReason,
        verified = verified,
    )
}

/** The map itself: one entry per target, no duplicates, lookup and counts without re-derivation. */
data class AtlasCapabilityMap(val entries: List<AtlasCapability>) {
    init {
        require(entries.map { it.target }.distinct().size == entries.size) {
            "the capability map holds one entry per control target"
        }
    }

    fun forTarget(target: AtlasControlTarget): AtlasCapability? = entries.firstOrNull { it.target == target }

    fun counts(): Map<AtlasCapabilityState, Int> = entries.groupingBy { it.state }.eachCount()
}
