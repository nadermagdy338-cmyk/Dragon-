/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import nd.max.ui.util.ModuleInventory

/**
 * Pre-flight outcome. **`READY` means the gates passed and the write may be *attempted*** — it never
 * means anything was written or that any app reads a different device: that is [SpoofCopgBackend]'s
 * readback verdict, and even a verified file cannot prove a per-app effect (§0.1).
 */
enum class SpoofApplyReason {
    INVALID_TARGET, BARRIER_NOT_ACKNOWLEDGED, CONSENT_REQUIRED, INVENTORY_UNKNOWN, ENGINE_ABSENT,
    ENGINE_UNAVAILABLE, ENGINE_AMBIGUOUS, READY,
}

data class SpoofApplyResult(
    val packageName: String,
    val profileId: String,
    val reason: SpoofApplyReason,
    val outcome: String = "blocked",
) {
    val applied: Boolean get() = false
    val verified: Boolean get() = false
    val writeAttempted: Boolean get() = false
}

/**
 * SP-05/SP-07 pre-flight gate. COPG's own WebUI documents the file shape and writes that file
 * (`webroot/js/copg-data.js`: "shared with zygisk/binaries") — so the shape is documented rather than
 * guessed, and this gate no longer refuses on unknown contract. What it still refuses: an unknown
 * target, an unacknowledged per-app barrier, missing consent, an unreadable inventory, a missing /
 * unavailable / ambiguous engine. Only then is a write *attempted* by [SpoofCopgBackend].
 *
 * A version number never enables anything; consent is per attempt, not stored.
 *
 * **Decision order is part of the contract**: an unknown target is refused first, then the per-app
 * honesty barrier, then this session's consent, and only then the engine inventory. So a reviewer
 * can read the first refusal and know the request never reached engine discovery.
 */
object SpoofApplyBackend {
    fun evaluate(
        packageName: String,
        profile: SpoofProfile,
        consent: Boolean,
        inventory: ModuleInventory,
        barrierAcknowledged: Boolean,
    ): SpoofApplyResult {
        val engines = SpoofEngineRegistry.derive(inventory).modules.filter { it.role == SpoofModuleRole.ENGINE }
        val reason = when {
            !SpoofWorkspace.validPackage(packageName) -> SpoofApplyReason.INVALID_TARGET
            !barrierAcknowledged -> SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED
            !consent -> SpoofApplyReason.CONSENT_REQUIRED
            !inventory.complete || inventory.modules.any { it.id == null } -> SpoofApplyReason.INVENTORY_UNKNOWN
            engines.isEmpty() -> SpoofApplyReason.ENGINE_ABSENT
            engines.size != 1 -> SpoofApplyReason.ENGINE_AMBIGUOUS
            engines.single().status == SpoofModuleStatus.UNKNOWN -> SpoofApplyReason.INVENTORY_UNKNOWN
            engines.single().status != SpoofModuleStatus.DETECTED -> SpoofApplyReason.ENGINE_UNAVAILABLE
            else -> SpoofApplyReason.READY
        }
        return SpoofApplyResult(packageName, profile.id, reason)
    }
}
