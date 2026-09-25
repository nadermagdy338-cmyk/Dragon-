/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasRouteMemoryEntry
import nd.max.core.hardware.AtlasRouteOutcome

/**
 * The `Learn` stage's face: one device's discovered knowledge, assembled — never stored as a second
 * truth.
 *
 * The owner's rule for learning is exact: *"احتفظ بالمعرفة المكتشفة عن الجهاز بطريقة آمنة وقابلة
 * للتحديث، بحيث يتحسن التعامل مع نفس الجهاز لاحقًا دون تحويل المعرفة إلى افتراضات ثابتة غير
 * صحيحة."* Three design decisions carry that rule:
 *
 * 1. **Derived, not persisted.** The knowledge itself lives where it was established — the evidence
 *    store (what was read) and the route memory (what worked). A profile is *assembled* from those on
 *    demand. A second store would eventually disagree with the first two, and a device profile that
 *    disagrees with the device is exactly the "fixed false assumption" this stage must not create.
 * 2. **Every claim expires.** The profile carries the boot and privilege generations it was built
 *    under; a different boot voids what was learned (the vendor rebuilt its own state), and a
 *    privilege change voids what was readable. [stalenessAt] is the same rule the evidence cache uses
 *    ([AtlasFreshness], `STATIC` volatility), not a new one.
 * 3. **Identity is private.** The identity rides along so a caller can tell *which* device this
 *    knowledge is about, and the private cache key never leaves [AtlasDeviceIdentity]: a profile is
 *    in-memory working state, not an export format.
 */
data class AtlasLearnedRoute(
    val target: String,
    val routeId: String,
    val lastOutcome: AtlasRouteOutcome,
    val lastVerifiedElapsedMs: Long?,
    val bootGeneration: Long,
    val privilegeGeneration: Long,
    val successes: Int,
    val failures: Int,
    val rollbackFailures: Int,
) {
    val verified: Boolean get() = lastOutcome == AtlasRouteOutcome.VERIFIED && successes > 0 && rollbackFailures == 0

    companion object {
        fun from(entry: AtlasRouteMemoryEntry): AtlasLearnedRoute = AtlasLearnedRoute(
            target = entry.target,
            routeId = entry.routeId,
            lastOutcome = entry.lastOutcome,
            lastVerifiedElapsedMs = entry.lastVerifiedElapsedMs,
            bootGeneration = entry.bootGeneration,
            privilegeGeneration = entry.privilegeGeneration,
            successes = entry.successes,
            failures = entry.failures,
            rollbackFailures = entry.rollbackFailures,
        )
    }
}

/**
 * What this build knows about one device right now: who it claims to be, what the capability map
 * says, and what previous attempts taught.
 *
 * @param catalogVersion the knowledge version the map was derived against; a catalog change makes
 *   the map a statement about older knowledge, which is why it is part of the freshness key.
 */
data class AtlasDeviceProfile(
    val identity: AtlasDeviceIdentity,
    val catalogVersion: String,
    val bootGeneration: Long,
    val privilegeGeneration: Long,
    val builtAtElapsedMs: Long,
    val capabilities: AtlasCapabilityMap,
    val learnedRoutes: List<AtlasLearnedRoute>,
) {

    /**
     * Whether this profile may still be used as a statement about *this* boot and *these*
     * permissions. Same ordering as every other freshness verdict: boot, then privilege, then clock.
     */
    fun stalenessAt(nowMs: Long, bootGeneration: Long, privilegeGeneration: Long): AtlasStaleness =
        AtlasFreshness(
            volatility = AtlasVolatility.STATIC,
            observedAtElapsedMs = builtAtElapsedMs,
            bootGeneration = this.bootGeneration,
            privilegeGeneration = this.privilegeGeneration,
        ).stalenessAt(nowMs, bootGeneration, privilegeGeneration)

    fun isUsableAt(nowMs: Long, bootGeneration: Long, privilegeGeneration: Long): Boolean =
        stalenessAt(nowMs, bootGeneration, privilegeGeneration) == AtlasStaleness.FRESH

    /** Routes remembered as verified under the profile's own generations — no others count. */
    fun verifiedRoutes(): List<AtlasLearnedRoute> = learnedRoutes.filter {
        it.verified && it.bootGeneration == bootGeneration && it.privilegeGeneration == privilegeGeneration
    }

    companion object {

        /**
         * Assembles the profile. Everything is passed in: this function performs no I/O, so the map
         * builder and the memory reader stay independently testable and a caller decides when the
         * device is read (never during composition on a main thread).
         */
        fun build(
            identity: AtlasDeviceIdentity,
            catalogVersion: String,
            capabilities: AtlasCapabilityMap,
            learned: List<AtlasRouteMemoryEntry> = emptyList(),
            clockMs: () -> Long,
            bootGeneration: Long,
            privilegeGeneration: Long,
        ): AtlasDeviceProfile = AtlasDeviceProfile(
            identity = identity,
            catalogVersion = catalogVersion,
            bootGeneration = bootGeneration,
            privilegeGeneration = privilegeGeneration,
            builtAtElapsedMs = clockMs(),
            capabilities = capabilities,
            learnedRoutes = learned.map(AtlasLearnedRoute::from),
        )
    }
}
