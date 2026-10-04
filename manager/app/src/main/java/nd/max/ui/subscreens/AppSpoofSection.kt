/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nd.max.R
import nd.max.core.spoof.EffectiveSpoofProfileResolver
import nd.max.core.spoof.SpoofCategory
import nd.max.core.spoof.SpoofCategoryMode
import nd.max.core.spoof.SpoofField
import nd.max.core.spoof.SpoofInheritanceMode
import nd.max.core.spoof.SpoofProfile
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxTone
import nd.max.ui.viewmodel.SpoofStudioViewModel

@Composable
internal fun AppSpoofSection(packageName: String, viewModel: SpoofStudioViewModel = hiltViewModel()) {
    val configuration by viewModel.configuration.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val acknowledged by viewModel.acknowledgments.collectAsStateWithLifecycle()
    val engineConfig by viewModel.engineConfig.collectAsStateWithLifecycle()
    val lastWrite by viewModel.lastWrite.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val recoveryFailed by viewModel.recoveryFailed.collectAsStateWithLifecycle()
    val workspace = configuration.workspace
    val policy = workspace?.appPolicy(packageName)
    val selected = workspace?.profiles?.firstOrNull { it.id == workspace.bindings[packageName] }
    var editing by remember(packageName) { mutableStateOf<SpoofProfile?>(null) }
    var newProfileForApp by remember(packageName) { mutableStateOf(false) }
    var pickingSample by remember(packageName) { mutableStateOf(false) }
    val newProfileName = stringResource(R.string.spoof_new_profile)
    MaxSection(title = stringResource(R.string.spoof_title), description = stringResource(R.string.identity_shared)) {
        when {
            configuration.loading -> Text(stringResource(R.string.spoof_reading))
            workspace == null -> Text(stringResource(R.string.spoof_load_failed))
            else -> {
                val labels = listOf(stringResource(R.string.identity_mode_global),
                    stringResource(R.string.identity_mode_custom), stringResource(R.string.identity_mode_disabled))
                MaxSegmented(options = labels, selectedIndex = policy!!.mode.ordinal, onSelect = { index ->
                    if (!busy && (index != SpoofInheritanceMode.CUSTOM.ordinal || selected != null)) {
                        viewModel.setMode(packageName, SpoofInheritanceMode.entries[index])
                    }
                })
                MaxGroup {
                    MaxRow(title = labels[policy.mode.ordinal],
                        subtitle = when (policy.mode) {
                            SpoofInheritanceMode.CUSTOM -> selected?.name ?: stringResource(R.string.spoof_no_binding)
                            SpoofInheritanceMode.GLOBAL -> workspace.profiles.firstOrNull { it.id == workspace.globalProfileId }?.name
                                ?: stringResource(R.string.identity_host)
                            SpoofInheritanceMode.DISABLED -> stringResource(R.string.identity_disabled_notice)
                        }, icon = Icons.Rounded.Fingerprint, iconTone = MaxTone.Accent)
                }
                if (policy.mode != SpoofInheritanceMode.DISABLED) {
                    TextButton(enabled = !busy, onClick = { pickingSample = true }) { Text(stringResource(R.string.sample_pick_button)) }
                }
                Text(stringResource(R.string.identity_not_verified))
                if (lastWrite != null) Text(stringResource(if (lastWrite?.applied == true)
                    R.string.identity_config_verified else R.string.identity_apply_failed))
                if (recoveryFailed) Text(stringResource(R.string.identity_recovery_corrupt))
                recovery.firstOrNull { it.engineId == nd.max.core.spoof.SpoofCopgContract.MODULE_ID }?.let { entry ->
                    Text(stringResource(R.string.identity_recovery_entry,
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAtMs)),
                        stringResource(if (entry.phase == nd.max.core.spoof.SpoofRecoveryPhase.CONFIG_VERIFIED)
                            R.string.identity_config_verified else if (entry.phase == nd.max.core.spoof.SpoofRecoveryPhase.RESTORED)
                            R.string.identity_rollback_ok else R.string.identity_recovery_pending)))
                }
                if (engineConfig?.present == false) Text(stringResource(R.string.identity_engine_missing))
                if (workspace.globalProfileId != null && policy.mode == SpoofInheritanceMode.DISABLED) {
                    Text(stringResource(R.string.identity_isolation_gap))
                }
                if (policy.mode == SpoofInheritanceMode.CUSTOM || selected == null) {
                    Text(stringResource(R.string.identity_choose_custom))
                    workspace.profiles.forEach { profile ->
                        MaxRow(title = profile.name, subtitle = profile.model, enabled = !busy,
                            onClick = { viewModel.assign(packageName, profile.id) },
                            trailing = { TextButton(enabled = !busy, onClick = { editing = profile }) {
                                Text(stringResource(R.string.spoof_edit))
                            } })
                    }
                    if (workspace.profiles.isEmpty()) Text(stringResource(R.string.spoof_no_profiles))
                    TextButton(enabled = !busy && workspace.profiles.size < 100, onClick = {
                        val values = viewModel.observed
                        editing = runCatching { SpoofProfile(java.util.UUID.randomUUID().toString(), newProfileName,
                            values[SpoofField.BRAND].orEmpty(), values[SpoofField.MODEL].orEmpty(),
                            values[SpoofField.DEVICE].orEmpty(), values[SpoofField.PRODUCT].orEmpty()) }.getOrNull()
                        newProfileForApp = editing != null
                    }) { Text(stringResource(R.string.spoof_add_profile)) }
                }
                if (policy.mode == SpoofInheritanceMode.CUSTOM) {
                    Text(stringResource(R.string.identity_category_policy))
                    listOf(SpoofCategory.IDENTITY, SpoofCategory.BUILD).forEach { category ->
                        Text(stringResource(if (category == SpoofCategory.IDENTITY) R.string.identity_category_identity
                            else R.string.identity_category_build))
                        MaxSegmented(options = listOf(stringResource(R.string.identity_category_custom),
                            stringResource(R.string.identity_mode_global), stringResource(R.string.identity_category_real)),
                            selectedIndex = (policy.categories[category] ?: SpoofCategoryMode.INHERIT).ordinal,
                            onSelect = { index -> if (!busy) viewModel.change { latest ->
                                val fresh = latest.appPolicy(packageName)
                                latest.setAppPolicy(packageName, fresh.copy(categories = fresh.categories +
                                    (category to SpoofCategoryMode.entries[index])))
                            } })
                    }
                }
                MaxGroup {
                    EffectiveSpoofProfileResolver.resolve(workspace, packageName, viewModel.observed).fields
                        .filter { it.field == SpoofField.MODEL || it.field == SpoofField.FINGERPRINT }.forEach { field ->
                            MaxRow(title = field.field.name, subtitle = stringResource(R.string.identity_value_comparison,
                                field.observed ?: stringResource(R.string.status_unknown),
                                field.target ?: stringResource(R.string.status_unknown), stringResource(R.string.status_unknown)))
                        }
                }
            }
        }
    }
    if (workspace != null && policy != null && policy.mode != SpoofInheritanceMode.DISABLED) {
        // Same room from both doors: Studio's apps tab and AppSettings both render this composable.
        val hasDevice = if (policy.mode == SpoofInheritanceMode.CUSTOM) selected != null else workspace.globalProfileId != null
        AppSpoofTagsSection(packageName, policy, hasDevice, packageName in acknowledged, busy, viewModel)
    }
    if (workspace != null) SpoofBarrierSection(packageName, packageName in acknowledged, !busy,
        onAcknowledge = { viewModel.acknowledge(packageName, true) },
        onRevoke = { viewModel.acknowledge(packageName, false) })
    if (pickingSample && workspace != null) {
        SampleDevicePickerDialog(
            canAdd = workspace.profiles.size < 100,
            onPick = { viewModel.applySample(it, packageName); pickingSample = false },
            onManual = {
                pickingSample = false
                val values = viewModel.observed
                editing = runCatching { SpoofProfile(java.util.UUID.randomUUID().toString(), newProfileName,
                    values[SpoofField.BRAND].orEmpty(), values[SpoofField.MODEL].orEmpty(),
                    values[SpoofField.DEVICE].orEmpty(), values[SpoofField.PRODUCT].orEmpty()) }.getOrNull()
                newProfileForApp = editing != null
            },
            onDismiss = { pickingSample = false },
        )
    }
    editing?.let { profile -> SpoofProfileEditor(profile, onDismiss = { editing = null; newProfileForApp = false }, onSave = { updated ->
        if (newProfileForApp) viewModel.change { it.upsert(updated).bind(packageName, updated.id) }
        else viewModel.saveProfile(updated)
        editing = null; newProfileForApp = false
    }) }
}
