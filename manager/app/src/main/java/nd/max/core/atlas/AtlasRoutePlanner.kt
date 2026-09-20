package nd.max.core.atlas

private val ATLAS_ROUTE_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")

/** Execution transports are ordered by safety and platform ownership. */
enum class AtlasControlTransport {
    PLATFORM_HINT,
    VENDOR_BRIDGE,
    ROOT_DAEMON,
    ARBITER_SYSFS,
    READ_ONLY,
}

enum class AtlasRouteStatus {
    ELIGIBLE,
    BLOCKED,
    UNSUPPORTED,
    REVIEW_REQUIRED,
}

enum class AtlasRouteReason {
    PLATFORM_ROUTE_AVAILABLE,
    VERIFIED_VENDOR_ROUTE,
    VERIFIED_DAEMON_ROUTE,
    VERIFIED_ARBITER_ROUTE,
    BASELINE_UNREADABLE,
    ROLLBACK_UNPROVEN,
    UNIT_AMBIGUOUS,
    PROVIDER_AMBIGUOUS,
    PRIVILEGE_UNAVAILABLE,
    GOAL_UNMEASURABLE,
    ROUTE_NOT_REVIEWED,
}

/** Read-only facts that a route planner is allowed to consume. */
data class AtlasRouteEvidence(
    val providerId: String,
    val transport: AtlasControlTransport,
    val target: AtlasControlTarget,
    val readable: Boolean,
    val privilegeAvailable: Boolean,
    val unitProven: Boolean,
    val baselineReadable: Boolean,
    val rollbackProven: Boolean,
    val reviewed: Boolean,
    val ambiguous: Boolean = false,
    val reason: String = "",
) {
    init { require(providerId.isNotBlank()) { "providerId is required" } }
}

data class AtlasRouteCandidate(
    val id: String,
    val evidence: AtlasRouteEvidence,
    val priority: Int,
) {
    init {
        require(id.matches(ATLAS_ROUTE_ID)) { "route id is not canonical: $id" }
        require(priority >= 0) { "route priority must be non-negative" }
    }
}

data class AtlasRouteDecision(
    val status: AtlasRouteStatus,
    val selected: AtlasRouteCandidate? = null,
    val skipped: List<Pair<String, AtlasRouteReason>> = emptyList(),
    val reason: AtlasRouteReason? = null,
) {
    init {
        if (status == AtlasRouteStatus.ELIGIBLE) requireNotNull(selected) { "eligible needs a selected route" }
        if (status != AtlasRouteStatus.ELIGIBLE) require(selected == null) { "blocked decisions select no route" }
    }
}

/**
 * Pure route selection. It does not read a node, request root, or mutate ownership. The first eligible
 * route wins after deterministic safety ordering; candidate routes are never promoted by a heuristic.
 */
object AtlasRoutePlanner {

    fun choose(
        intent: AtlasControlIntent,
        candidates: List<AtlasRouteCandidate>,
        measuredGoalAvailable: Boolean = true,
    ): AtlasRouteDecision {
        val applicable = candidates
            .filter { it.evidence.target == intent.target }
            .sortedWith(compareBy<AtlasRouteCandidate> { transportRank(it.evidence.transport) }
                .thenByDescending { it.evidence.reviewed }
                .thenBy { it.priority }
                .thenBy { it.id })

        val skipped = mutableListOf<Pair<String, AtlasRouteReason>>()
        applicable.forEach { candidate ->
            val evidence = candidate.evidence
            val reason = rejectionReason(evidence, measuredGoalAvailable)
            if (reason == null) {
                return AtlasRouteDecision(
                    status = AtlasRouteStatus.ELIGIBLE,
                    selected = candidate,
                    skipped = skipped,
                )
            }
            skipped += candidate.id to reason
        }

        val finalReason = skipped.lastOrNull()?.second ?: AtlasRouteReason.ROUTE_NOT_REVIEWED
        val status = when (finalReason) {
            AtlasRouteReason.ROUTE_NOT_REVIEWED -> AtlasRouteStatus.REVIEW_REQUIRED
            AtlasRouteReason.PRIVILEGE_UNAVAILABLE,
            AtlasRouteReason.BASELINE_UNREADABLE,
            AtlasRouteReason.ROLLBACK_UNPROVEN,
            AtlasRouteReason.UNIT_AMBIGUOUS,
            AtlasRouteReason.PROVIDER_AMBIGUOUS,
            AtlasRouteReason.GOAL_UNMEASURABLE,
            -> AtlasRouteStatus.BLOCKED

            else -> AtlasRouteStatus.UNSUPPORTED
        }
        return AtlasRouteDecision(status = status, skipped = skipped, reason = finalReason)
    }

    private fun rejectionReason(evidence: AtlasRouteEvidence, goalAvailable: Boolean): AtlasRouteReason? {
        if (evidence.transport == AtlasControlTransport.READ_ONLY) return AtlasRouteReason.PRIVILEGE_UNAVAILABLE
        if (evidence.ambiguous) return AtlasRouteReason.PROVIDER_AMBIGUOUS
        if (!evidence.readable) return AtlasRouteReason.PRIVILEGE_UNAVAILABLE
        if (!evidence.privilegeAvailable && evidence.transport != AtlasControlTransport.PLATFORM_HINT) {
            return AtlasRouteReason.PRIVILEGE_UNAVAILABLE
        }
        if (!evidence.unitProven) return AtlasRouteReason.UNIT_AMBIGUOUS
        if (!evidence.baselineReadable) return AtlasRouteReason.BASELINE_UNREADABLE
        if (!evidence.rollbackProven) return AtlasRouteReason.ROLLBACK_UNPROVEN
        if (!goalAvailable && evidence.transport != AtlasControlTransport.PLATFORM_HINT) {
            return AtlasRouteReason.GOAL_UNMEASURABLE
        }
        if (!evidence.reviewed) return AtlasRouteReason.ROUTE_NOT_REVIEWED
        return null
    }

    private fun transportRank(transport: AtlasControlTransport): Int = when (transport) {
        AtlasControlTransport.PLATFORM_HINT -> 0
        AtlasControlTransport.VENDOR_BRIDGE -> 1
        AtlasControlTransport.ROOT_DAEMON -> 2
        AtlasControlTransport.ARBITER_SYSFS -> 3
        AtlasControlTransport.READ_ONLY -> 4
    }
}
