/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteDecision
import nd.max.core.atlas.AtlasRoutePlanner
import nd.max.core.atlas.AtlasRouteReason
import nd.max.core.atlas.AtlasRouteStatus

/**
 * One adaptive execution attempt owned by an Atlas route.
 *
 * The route only supplies a typed hardware transaction; it cannot supply a shell command or a raw
 * path to Atlas. [HardwareRepairExecutor] remains the single mutation boundary, so every attempt has
 * a baseline, read-back verification, a bounded confirmation window and rollback on drift.
 */
data class AtlasRouteBinding(
    val candidate: AtlasRouteCandidate,
    val request: HardwareRepairRequest,
)

/** The complete decision, including routes that were tried and why fallback stopped. */
data class AtlasAdaptiveResult(
    val decision: AtlasRouteDecision,
    val attempts: List<HardwareRepairResult>,
    val selectedRouteId: String? = null,
    val fallbackStopped: Boolean = false,
    val detail: String? = null,
    /** Routes that were not attempted, each with a stable reason code (never a sentence). */
    val skipped: List<Pair<String, String>> = emptyList(),
) {
    val successful: Boolean get() = attempts.lastOrNull()?.successful == true
}

/**
 * Chooses and executes the best available route, then falls through to another route when the first
 * route proves unusable.
 *
 * This is deliberately not a "try every route blindly" loop:
 * - only routes that pass [AtlasRoutePlanner] are attempted;
 * - a verified rollback permits fallback;
 * - an unverified rollback stops immediately because the physical state is unknown;
 * - a successful route ends the search and becomes the route the caller can persist for this device.
 */
class AtlasAdaptiveExecutor(
    private val repairExecutor: AtlasRepairPort,
    /**
     * Optional per-device memory. When present it can only *narrow* what is attempted (a quarantined
     * route) or *break ties inside one safety tier* (a previously verified route). It can never
     * promote a route past the planner's transport ordering.
     */
    private val memory: AtlasRouteMemory? = null,
) {
    fun execute(
        intent: AtlasControlIntent,
        bindings: List<AtlasRouteBinding>,
        measuredGoalAvailable: Boolean = true,
    ): AtlasAdaptiveResult {
        require(bindings.map { it.candidate.id }.distinct().size == bindings.size) {
            "Atlas route ids must be unique within one execution"
        }

        val targetKey = intent.target.name.lowercase()
        val quarantined = memory?.quarantinedRoutes(targetKey).orEmpty()
        val skipped = bindings
            .filter { it.candidate.id in quarantined }
            .map { it.candidate.id to SKIP_ROUTE_QUARANTINED }
        val preferred = memory?.preferredRoute(targetKey)

        val remaining = bindings
            .filter { it.candidate.id !in quarantined }
            .map { binding ->
                // The boost is a priority shift, not a transport promotion: `AtlasRoutePlanner` orders
                // by transport first, so a remembered sysfs route still loses to a platform route.
                if (preferred != null && binding.candidate.id == preferred) {
                    binding.copy(candidate = binding.candidate.copy(priority = 0))
                } else {
                    binding.copy(candidate = binding.candidate.copy(priority = binding.candidate.priority + 1))
                }
            }
            .toMutableList()

        val attempts = mutableListOf<HardwareRepairResult>()
        var lastDecision = AtlasRouteDecision(
            status = AtlasRouteStatus.REVIEW_REQUIRED,
            reason = AtlasRouteReason.ROUTE_NOT_REVIEWED,
        )

        while (remaining.isNotEmpty()) {
            val decision = AtlasRoutePlanner.choose(
                intent = intent,
                candidates = remaining.map { it.candidate },
                measuredGoalAvailable = measuredGoalAvailable,
            )
            lastDecision = decision
            val selected = decision.selected ?: break
            val binding = remaining.first { it.candidate.id == selected.id }
            remaining.removeAll { it.candidate.id == selected.id }

            val result = repairExecutor.execute(binding.request)
            attempts += result
            memory?.let { memory ->
                when (result.state) {
                    HardwareRepairState.CONFIRMED_WINDOW -> memory.noteVerified(targetKey, selected.id)

                    HardwareRepairState.APPLY_FAILED_ROLLED_BACK,
                    HardwareRepairState.DRIFT_ROLLED_BACK,
                    -> memory.noteFailed(targetKey, selected.id, rollbackVerified = true)

                    HardwareRepairState.ROLLBACK_UNVERIFIED -> memory.noteFailed(targetKey, selected.id, rollbackVerified = false)

                    // A blocked attempt is an ownership decision, not a verdict about the route: the
                    // knob was never touched, so nothing about this route was learned.
                    HardwareRepairState.BLOCKED -> Unit
                }
            }
            if (result.successful) {
                return AtlasAdaptiveResult(
                    decision = decision,
                    attempts = attempts.toList(),
                    selectedRouteId = selected.id,
                    detail = "route verified",
                    skipped = skipped,
                )
            }

            // A route that changed the device but could not restore the baseline is not safe to follow
            // with another route. The caller receives the failure and can surface recovery guidance.
            if (result.rollbackAttempted && result.rollbackVerified != true) {
                return AtlasAdaptiveResult(
                    decision = decision,
                    attempts = attempts.toList(),
                    fallbackStopped = true,
                    detail = "fallback stopped: rollback was not verified",
                    skipped = skipped,
                )
            }
        }

        return AtlasAdaptiveResult(
            decision = lastDecision,
            attempts = attempts.toList(),
            fallbackStopped = attempts.any { it.rollbackAttempted && it.rollbackVerified != true },
            detail = when {
                attempts.isEmpty() && skipped.isNotEmpty() -> "every route for this target is quarantined"
                attempts.isEmpty() -> "no eligible route"
                else -> "all eligible routes failed"
            },
            skipped = skipped,
        )
    }

    private companion object {
        /** A stable machine reason, not a sentence: the caller owns the wording. */
        const val SKIP_ROUTE_QUARANTINED = "route-quarantined-after-unverified-rollback"
    }
}
