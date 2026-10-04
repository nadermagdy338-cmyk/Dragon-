/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.spoof.SpoofCapabilityMatrix
import nd.max.core.spoof.SpoofCapabilityEvidence
import nd.max.core.spoof.SpoofScope
import nd.max.core.spoof.SpoofEngineConfig
import nd.max.core.spoof.SpoofEngineReason
import nd.max.core.spoof.SpoofEngineWrite
import nd.max.core.spoof.SpoofField
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone

@Composable
internal fun IdentityEngineSection(
    config: SpoofEngineConfig?, globalConfig: SpoofEngineConfig?, write: SpoofEngineWrite?,
    globalWrite: SpoofEngineWrite?, busy: Boolean, onApply: (Boolean, Boolean) -> Unit,
    recovery: List<nd.max.core.spoof.SpoofRecoverySummary>, recoveryFailed: Boolean,
    onRestore: (String) -> Unit,
) {
    MaxSection(title = stringResource(R.string.identity_capabilities), description = stringResource(R.string.identity_capability_notice)) {
        SpoofField.entries.forEach { field ->
            val capability = SpoofCapabilityMatrix.resolve(field, SpoofScope.PER_APP,
                SpoofCapabilityEvidence(config?.parseable, config?.engineAvailable, globalConfig?.engineAvailable == true))
            MaxRow(title = field.name, subtitle = stringResource(when (capability.state) {
                nd.max.core.spoof.SpoofCapabilityState.WRITABLE -> R.string.identity_config_writable
                nd.max.core.spoof.SpoofCapabilityState.READ_ONLY -> R.string.identity_read_only
                nd.max.core.spoof.SpoofCapabilityState.NEVER_TOUCH -> R.string.identity_never_touch
                nd.max.core.spoof.SpoofCapabilityState.UNKNOWN -> R.string.status_unknown
                nd.max.core.spoof.SpoofCapabilityState.UNAVAILABLE -> R.string.identity_unavailable
                else -> R.string.identity_needs_adapter
            }))
        }
    }
    MaxSection(title = stringResource(R.string.identity_perapp_engine), description = config?.configPath) {
        EngineEvidence(config, write)
        TextButton(enabled = !busy, onClick = { onApply(false, false) }) { Text(stringResource(R.string.identity_prepare_apps)) }
        TextButton(enabled = !busy && config?.ownedKeys?.isNotEmpty() == true, onClick = { onApply(false, true) }) {
            Text(stringResource(R.string.spoof_apply_clear))
        }
    }
    MaxSection(title = stringResource(R.string.identity_recovery_title), description = stringResource(R.string.identity_recovery_notice)) {
        if (recoveryFailed) Text(stringResource(R.string.identity_recovery_corrupt))
        if (recovery.isEmpty() && !recoveryFailed) Text(stringResource(R.string.identity_recovery_empty))
        recovery.forEach { entry ->
            MaxRow(title = entry.engineId, subtitle = stringResource(R.string.identity_recovery_entry,
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAtMs)),
                stringResource(when (entry.phase) {
                    nd.max.core.spoof.SpoofRecoveryPhase.PREPARED -> R.string.identity_recovery_pending
                    nd.max.core.spoof.SpoofRecoveryPhase.CONFIG_VERIFIED -> R.string.identity_config_verified
                    nd.max.core.spoof.SpoofRecoveryPhase.RESTORED -> R.string.identity_rollback_ok
                    nd.max.core.spoof.SpoofRecoveryPhase.CONFLICT -> R.string.identity_foreign_conflict
                    nd.max.core.spoof.SpoofRecoveryPhase.FAILED -> R.string.identity_apply_failed
                })))
            TextButton(enabled = !busy && entry.phase != nd.max.core.spoof.SpoofRecoveryPhase.RESTORED,
                onClick = { onRestore(entry.engineId) }) { Text(stringResource(R.string.identity_recovery_restore)) }
        }
    }
    MaxSection(title = stringResource(R.string.identity_global_engine), description = globalConfig?.configPath) {
        EngineEvidence(globalConfig, globalWrite)
        Text(stringResource(R.string.identity_global_gap))
        TextButton(enabled = !busy, onClick = { onApply(true, false) }) { Text(stringResource(R.string.identity_prepare_global)) }
        TextButton(enabled = !busy && globalWrite?.applied == true, onClick = { onApply(true, true) }) { Text(stringResource(R.string.identity_undo_global)) }
    }
}

@Composable
private fun EngineEvidence(config: SpoofEngineConfig?, write: SpoofEngineWrite?) {
    MaxGroup {
        MaxRow(title = stringResource(R.string.identity_configuration), subtitle = stringResource(when {
            config?.present == null -> R.string.status_unknown
            config.present == false -> R.string.identity_unavailable
            config.parseable != true -> R.string.spoof_status_entries_unreadable
            else -> R.string.identity_config_readable
        }))
        write?.let {
            MaxRow(title = stringResource(if (it.applied) R.string.identity_config_verified else R.string.identity_apply_failed),
                subtitle = stringResource(when (it.reason) {
                    SpoofEngineReason.FOREIGN_PACKAGE_CONFLICT -> R.string.identity_foreign_conflict
                    SpoofEngineReason.UNSUPPORTED_POLICY -> R.string.identity_policy_blocked
                    SpoofEngineReason.UNSUPPORTED_TAG -> R.string.copg_tag_refused
                    SpoofEngineReason.ROOT_REQUIRED -> R.string.identity_root_required
                    SpoofEngineReason.ENGINE_UNAVAILABLE -> R.string.identity_engine_missing
                    SpoofEngineReason.GLOBAL_LAYER_CONFLICT -> R.string.identity_global_conflict
                    SpoofEngineReason.RECOVERY_STORE_FAILED -> R.string.identity_recovery_corrupt
                    SpoofEngineReason.RECOVERY_REQUIRED -> R.string.identity_recovery_pending
                    SpoofEngineReason.RECOVERY_MISSING -> R.string.identity_recovery_empty
                    SpoofEngineReason.CONFIG_CHANGED -> R.string.identity_config_changed
                    SpoofEngineReason.ACKNOWLEDGMENT_REQUIRED -> R.string.spoof_barrier_not_recorded
                    SpoofEngineReason.ARBITER_BLOCKED -> R.string.spoof_apply_outcome_blocked
                    else -> R.string.identity_not_verified
                }), icon = Icons.Rounded.Warning, iconTone = if (it.applied) MaxTone.Neutral else MaxTone.Caution)
            if (it.rollbackAttempted) Text(stringResource(if (it.rollbackVerified == true)
                R.string.identity_rollback_ok else R.string.identity_rollback_failed))
        }
    }
}
