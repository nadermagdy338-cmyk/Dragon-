/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nd.max.R
import nd.max.core.spoof.EffectiveSpoofProfileResolver
import nd.max.core.spoof.SpoofField
import nd.max.core.spoof.SpoofProfile
import nd.max.core.spoof.SpoofWorkspace
import nd.max.ui.design.MaxCard
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxTone
import nd.max.ui.viewmodel.SpoofStudioViewModel
import java.util.UUID

@Composable
fun SpoofStudioScreen(navController: NavController, viewModel: SpoofStudioViewModel = hiltViewModel()) {
    val state by viewModel.configuration.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val config by viewModel.engineConfig.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val write by viewModel.lastWrite.collectAsStateWithLifecycle()
    val globalWrite by viewModel.lastGlobalWrite.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val recoveryFailed by viewModel.recoveryFailed.collectAsStateWithLifecycle()
    var restoreEngine by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<SpoofProfile?>(null) }
    var deleting by remember { mutableStateOf<SpoofProfile?>(null) }
    var pickingGlobalSample by remember { mutableStateOf(false) }
    // Confirm an exact configuration revision. Any later edit cancels confirmation instead of applying unseen changes.
    var confirmation by remember { mutableStateOf<IdentityConfirmation?>(null) }
    val workspace = state.workspace
    val global = workspace?.profiles?.firstOrNull { it.id == workspace.globalProfileId }
    val resolved = workspace?.let { EffectiveSpoofProfileResolver.resolve(it, null, viewModel.observed) }
    MaxScreen(title = stringResource(R.string.spoof_title), subtitle = stringResource(R.string.identity_subtitle),
        accentIcon = Icons.Rounded.Fingerprint, onBack = { navController.navigateUp() },
        actions = { TextButton(onClick = { viewModel.refresh() }, enabled = !busy) { Text(stringResource(R.string.spoof_rescan)) } }) {
        MaxCard(title = global?.name ?: stringResource(R.string.identity_host), icon = Icons.Rounded.PhoneAndroid,
            description = stringResource(R.string.identity_hero, global?.model ?: viewModel.observed[SpoofField.MODEL].orEmpty()),
            tone = MaxTone.Accent)
        Text(stringResource(R.string.identity_not_verified), style = MaterialTheme.typography.bodySmall)
        if (state.failed) Text(stringResource(R.string.spoof_save_failed))
        if (state.loading) Text(stringResource(R.string.spoof_reading))
        saved?.let { Text(stringResource(if (it) R.string.spoof_draft_saved else R.string.spoof_save_failed)) }
        MaxSegmented(options = listOf(stringResource(R.string.identity_overview), stringResource(R.string.spoof_tab_profiles),
            stringResource(R.string.spoof_tab_app), stringResource(R.string.spoof_tab_engine)), selectedIndex = tab, onSelect = { tab = it })
        if (workspace != null) when (tab) {
            0 -> {
                MaxSection(title = stringResource(R.string.identity_global), description = stringResource(R.string.identity_global_notice)) {
                    MaxGroup {
                        MaxRow(title = global?.name ?: stringResource(R.string.identity_host),
                            subtitle = stringResource(R.string.identity_saved_target), icon = Icons.Rounded.Fingerprint,
                            onClick = { tab = 1 })
                    }
                    TextButton(enabled = !busy, onClick = { pickingGlobalSample = true }) { Text(stringResource(R.string.sample_pick_button)) }
                    if (global != null) TextButton(enabled = !busy, onClick = { editing = global }) { Text(stringResource(R.string.spoof_edit)) }
                    TextButton(enabled = !busy && global != null, onClick = { viewModel.setGlobal(null) }) {
                        Text(stringResource(R.string.identity_reset_global))
                    }
                }
                MaxSection(title = stringResource(R.string.identity_preview), description = stringResource(R.string.identity_observation_notice)) {
                    MaxGroup {
                        resolved?.fields?.filter { it.field.category == nd.max.core.spoof.SpoofCategory.IDENTITY ||
                            it.field.category == nd.max.core.spoof.SpoofCategory.BUILD }?.forEachIndexed { index, field ->
                            if (index > 0) MaxGroupDivider()
                            MaxRow(title = field.field.name, subtitle = stringResource(R.string.identity_value_comparison,
                                field.observed ?: stringResource(R.string.status_unknown), field.target ?: stringResource(R.string.status_unknown),
                                field.verifiedEffective ?: stringResource(R.string.status_unknown)))
                        }
                    }
                }
                MaxSection(title = stringResource(R.string.identity_assignments)) {
                    Text(stringResource(R.string.identity_app_count, (workspace.bindings.keys + workspace.appPolicies.keys).size))
                    (workspace.bindings.keys + workspace.appPolicies.keys).sorted().forEach { app ->
                        MaxRow(title = app, subtitle = stringResource(modeLabel(workspace.appPolicy(app).mode)),
                            onClick = { pkg = app; tab = 2 })
                    }
                }
            }
            1 -> {
                MaxSection(title = stringResource(R.string.spoof_profiles_title)) {
                    TextButton(enabled = !busy && workspace.profiles.size < 100, onClick = {
                        val values = viewModel.observed
                        editing = runCatching { SpoofProfile(UUID.randomUUID().toString(),
                            values[SpoofField.MODEL].orEmpty(), values[SpoofField.BRAND].orEmpty(), values[SpoofField.MODEL].orEmpty(),
                            values[SpoofField.DEVICE].orEmpty(), values[SpoofField.PRODUCT].orEmpty()) }.getOrNull()
                    }) { Text(stringResource(R.string.spoof_add_profile)) }
                    MaxSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.spoof_search_profiles))
                    val matches = workspace.profiles.filter { it.name.contains(query, true) || it.model.contains(query, true) }
                    if (matches.isEmpty()) Text(stringResource(R.string.spoof_no_profiles))
                    matches.forEach { profile ->
                        MaxGroup {
                            MaxRow(title = profile.name, subtitle = profile.model, icon = Icons.Rounded.PhoneAndroid,
                                iconTone = if (profile.id == workspace.globalProfileId) MaxTone.Accent else MaxTone.Neutral,
                                onClick = { editing = profile })
                            TextButton(enabled = !busy, onClick = { viewModel.setGlobal(profile.id) }) { Text(stringResource(R.string.identity_use_global)) }
                            TextButton(enabled = !busy && workspace.profiles.size < 100, onClick = {
                                editing = profile.copy(id = UUID.randomUUID().toString())
                            }) { Text(stringResource(R.string.spoof_duplicate_profile)) }
                            TextButton(enabled = !busy, onClick = { deleting = profile }) { Text(stringResource(R.string.spoof_delete_profile)) }
                        }
                    }
                }
                SpoofTransferSection(workspace, !busy, viewModel::importWorkspace)
            }
            2 -> {
                IdentityAppPicker(pkg, { pkg = it })
                if (SpoofWorkspace.validPackage(pkg)) AppSpoofSection(pkg, viewModel)
                else Text(stringResource(R.string.identity_package_hint))
            }
            3 -> {
                IdentityEngineSection(config, globalConfig, write, globalWrite, busy,
                    onApply = { isGlobal, clear -> confirmation = IdentityConfirmation(isGlobal, clear, state.revision) },
                    recovery = recovery, recoveryFailed = recoveryFailed, onRestore = { restoreEngine = it })
            }
        }
    }
    if (pickingGlobalSample && workspace != null) {
        SampleDevicePickerDialog(
            canAdd = workspace.profiles.size < 100,
            onPick = { viewModel.applySample(it, null); pickingGlobalSample = false },
            onManual = {
                pickingGlobalSample = false
                val values = viewModel.observed
                editing = runCatching { SpoofProfile(UUID.randomUUID().toString(),
                    values[SpoofField.MODEL].orEmpty(), values[SpoofField.BRAND].orEmpty(), values[SpoofField.MODEL].orEmpty(),
                    values[SpoofField.DEVICE].orEmpty(), values[SpoofField.PRODUCT].orEmpty()) }.getOrNull()
            },
            onDismiss = { pickingGlobalSample = false },
        )
    }
    editing?.let { profile -> SpoofProfileEditor(profile, { editing = null }, { viewModel.saveProfile(it); editing = null }) }
    deleting?.let { profile -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.spoof_delete_profile)) }, text = { Text(stringResource(R.string.identity_delete_notice, profile.name)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.change { it.remove(profile.id) }; deleting = null }) {
            Text(stringResource(R.string.spoof_delete_profile))
        } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.spoof_cancel)) } }) }
    restoreEngine?.let { engine -> AlertDialog(onDismissRequest = { restoreEngine = null },
        title = { Text(stringResource(R.string.identity_recovery_restore)) },
        text = { Text(stringResource(R.string.identity_recovery_notice)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.restore(engine); restoreEngine = null }) {
            Text(stringResource(R.string.spoof_apply_confirm))
        } }, dismissButton = { TextButton(onClick = { restoreEngine = null }) { Text(stringResource(R.string.spoof_cancel)) } }) }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text(stringResource(R.string.identity_confirm)) },
        text = { Text(stringResource(R.string.identity_confirm_notice)) },
        confirmButton = { TextButton(enabled = !busy && state.revision == action.revision, onClick = {
            viewModel.apply(action.global, action.clear, action.revision); confirmation = null
        }) { Text(stringResource(R.string.spoof_apply_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.spoof_cancel)) } }) }
}

private data class IdentityConfirmation(val global: Boolean, val clear: Boolean, val revision: Long)
internal fun modeLabel(mode: nd.max.core.spoof.SpoofInheritanceMode): Int = when (mode) {
    nd.max.core.spoof.SpoofInheritanceMode.GLOBAL -> R.string.identity_mode_global
    nd.max.core.spoof.SpoofInheritanceMode.CUSTOM -> R.string.identity_mode_custom
    nd.max.core.spoof.SpoofInheritanceMode.DISABLED -> R.string.identity_mode_disabled
}
