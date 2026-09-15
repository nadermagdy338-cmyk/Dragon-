package nd.max.core.maxai

import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot
import nd.max.core.hardware.HardwareControlArbiter
import javax.inject.Inject
import javax.inject.Singleton

/** Double safety veto around the planner's canonical arbiter transaction. */
@Singleton
class SafetyGovernor @Inject constructor(
    private val planner: MinimalPlanner,
    private val arbiter: HardwareControlArbiter,
    private val safetyEngine: SafetyEngine,
) {
    data class Transaction(
        val outcome: MinimalPlanner.Outcome,
        val postState: DeviceSnapshot? = null,
        val rolledBack: Boolean = false,
        val safetyReason: String? = null,
    )

    fun execute(
        step: MinimalPlanner.Step,
        appContext: String,
        token: String,
        readState: () -> DeviceSnapshot?,
    ): Transaction {
        preVeto(step)?.let { return it }
        val outcome = planner.execute(step, appContext, token)
        if (!outcome.verified) return Transaction(outcome)
        return enforcePost(step, outcome, readState(), token, appContext)
    }

    /**
     * [appContext] is required, not optional: the safety veto is recorded per
     * app context, and defaulting it to a placeholder would silently attribute
     * the veto to the wrong context and corrupt the credibility signal.
     */
    fun enforcePost(
        step: MinimalPlanner.Step,
        outcome: MinimalPlanner.Outcome,
        state: DeviceSnapshot?,
        token: String,
        appContext: String,
    ): Transaction {
        if (!outcome.verified || state == null) return Transaction(outcome, postState = state)
        val thermalC = state.thermal * 100f
        val safety = safetyEngine.evaluate(thermalC, predictedThermalC = null)
        val unsafe = step.direction == ControlRegistry.Direction.RAISE_PERFORMANCE &&
            (safety.engaged || thermalC >= SafetyEngine.ENGAGE_TEMP_C)
        if (!unsafe) return Transaction(outcome, postState = state)

        val rollback = arbiter.release(step.control.key, token, restore = true)
        // A safety-forced rollback is not a knob failure: the write verified
        // against hardware but a higher authority (thermal supremacy) cancelled
        // it. Recording it as a credibility failure would poison the learning
        // signal for a knob that did exactly what it was told to do.
        planner.recordSafetyVeto(step, appContext)
        return Transaction(
            outcome = outcome.copy(
                verified = false,
                blocked = true,
                actual = runCatching { step.control.read() }.getOrNull(),
                detail = "${outcome.detail} (post-veto thermal=${thermalC.toInt()}C)",
            ),
            postState = state,
            rolledBack = rollback?.verified == true,
            safetyReason = "post-veto",
        )
    }

    private fun preVeto(step: MinimalPlanner.Step): Transaction? {
        val safety = safetyEngine.status.value
        if (!safety.engaged || step.direction != ControlRegistry.Direction.RAISE_PERFORMANCE) return null
        return Transaction(
            outcome = MinimalPlanner.Outcome(
                step = step,
                verified = false,
                blocked = true,
                actual = runCatching { step.control.read() }.getOrNull(),
                detail = "${step.control.key}: pre-veto safety=${safety.level}",
            ),
            safetyReason = "pre-veto",
        )
    }
}
