/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

enum class SpoofScope { GLOBAL, PER_APP }
enum class SpoofRisk { SAFE, CONDITIONAL, RISKY, NEVER_TOUCH }
enum class SpoofVerificationMethod { ENGINE_FILE_READBACK, TARGET_PROCESS_REQUIRED, NONE }
enum class SpoofReadMethod { MANAGER_BUILD_OBSERVATION, ADAPTER_REQUIRED }
enum class SpoofWriteMethod { COPG_CONFIG, COPG_VD_CONFIG, NONE }
enum class SpoofRollbackMethod { DURABLE_CONFIG_BASELINE, NONE }

data class SpoofCapabilityEvidence(
    val readableConfig: Boolean?,
    val moduleEligible: Boolean?,
    val competingGlobalLayer: Boolean = false,
)
data class SpoofCapability(
    val field: SpoofField,
    val scope: SpoofScope,
    val state: SpoofCapabilityState,
    val readMethod: SpoofReadMethod,
    val writeMethod: SpoofWriteMethod,
    val verificationMethod: SpoofVerificationMethod,
    val rollbackMethod: SpoofRollbackMethod,
    val risk: SpoofRisk,
    /** Android/ABI compatibility cannot be derived from a parseable JSON file. */
    val processCompatibilityVerified: Boolean = false,
)

object SpoofCapabilityMatrix {
    fun resolve(field: SpoofField, scope: SpoofScope, evidence: SpoofCapabilityEvidence): SpoofCapability {
        val denied = field in setOf(SpoofField.ANDROID_ID, SpoofField.SERIAL, SpoofField.CARRIER)
        val readOnly = field == SpoofField.SDK_INT
        val configField = field.category in setOf(SpoofCategory.IDENTITY, SpoofCategory.BUILD) && !readOnly
        val state = when {
            denied -> SpoofCapabilityState.NEVER_TOUCH
            readOnly -> SpoofCapabilityState.READ_ONLY
            !configField -> SpoofCapabilityState.NEEDS_ADAPTER
            evidence.competingGlobalLayer && scope == SpoofScope.PER_APP -> SpoofCapabilityState.NEEDS_ADAPTER
            evidence.moduleEligible == false || evidence.readableConfig == false -> SpoofCapabilityState.UNAVAILABLE
            evidence.moduleEligible == null || evidence.readableConfig == null -> SpoofCapabilityState.UNKNOWN
            else -> SpoofCapabilityState.WRITABLE
        }
        val writable = state == SpoofCapabilityState.WRITABLE
        return SpoofCapability(field, scope, state,
            if (configField || readOnly) SpoofReadMethod.MANAGER_BUILD_OBSERVATION else SpoofReadMethod.ADAPTER_REQUIRED,
            if (!writable) SpoofWriteMethod.NONE else if (scope == SpoofScope.GLOBAL) SpoofWriteMethod.COPG_VD_CONFIG else SpoofWriteMethod.COPG_CONFIG,
            if (writable) SpoofVerificationMethod.ENGINE_FILE_READBACK else SpoofVerificationMethod.NONE,
            if (writable) SpoofRollbackMethod.DURABLE_CONFIG_BASELINE else SpoofRollbackMethod.NONE,
            when { denied -> SpoofRisk.NEVER_TOUCH; readOnly -> SpoofRisk.SAFE; scope == SpoofScope.GLOBAL -> SpoofRisk.RISKY; else -> SpoofRisk.CONDITIONAL })
    }
}
