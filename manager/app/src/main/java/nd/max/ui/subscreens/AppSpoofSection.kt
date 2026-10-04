/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nd.max.R
import nd.max.core.spoof.EffectiveSpoofProfileResolver
import nd.max.core.spoof.SpoofCategory
import nd.max.core.spoof.SpoofCategoryMode
import nd.max.core.spoof.SpoofCopgContract
import nd.max.core.spoof.SpoofField
import nd.max.core.spoof.SpoofInheritanceMode
import nd.max.core.spoof.SpoofProfile
import nd.max.core.spoof.SpoofRecoveryPhase
import nd.max.ui.component.MaxInfoStrip
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.design.MaxCollapsibleGroup
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.viewmodel.SpoofStudioViewModel

/**
 * هوية التطبيق (تزييف لكل تطبيق) — **غرفة واحدة يراها المستخدم من بابين**: تبويب «تزييف» في إعدادات التطبيق،
 * وتبويب «التطبيق» في استوديو التزييف. كلاهما يقرأ ويكتب عبر `SpoofStudioViewModel` (مصدر واحد) فلا فرق بينهما.
 *
 * ### الترتيب (من الأهم إلى الأقل)
 * 1. **بطاقة الحالة:** أي جهاز يراه هذا التطبيق الآن + شارة الدليل (لا «تم التزييف» بلا دليل).
 * 2. **وضع التطبيق:** عام · مخصّص · متوقف.
 * 3. **بطاقة الجهاز:** زرّ واحد بارز «اختيار جهاز نموذجي» (تلقائي)، وتعديل الحقول يدويًّا لمن يريد.
 * 4. **الخيارات:** خمس مجموعات مطوية ([AppSpoofTagsSection]).
 * 5. **الحالة والتحقق:** كل الملاحظات الطويلة في مجموعة مطوية واحدة بدل أن تتكدّس فوق الشاشة.
 * 6. **إقرار المخاطر.**
 *
 * @param onOpenStudio يظهر زرّ «فتح استوديو التزييف» فقط إن أُعطي (من شاشة إعدادات التطبيق)؛ وفي الاستوديو نفسه يُترك `null`.
 */
@Composable
internal fun AppSpoofSection(
    packageName: String,
    viewModel: SpoofStudioViewModel = hiltViewModel(),
    onOpenStudio: (() -> Unit)? = null,
) {
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
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        when {
            configuration.loading -> Text(stringResource(R.string.spoof_reading))
            workspace == null || policy == null -> Text(stringResource(R.string.spoof_load_failed))
            else -> {
                val labels = listOf(stringResource(R.string.identity_mode_global),
                    stringResource(R.string.identity_mode_custom), stringResource(R.string.identity_mode_disabled))
                val globalProfile = workspace.profiles.firstOrNull { it.id == workspace.globalProfileId }
                val effective: SpoofProfile? = when (policy.mode) {
                    SpoofInheritanceMode.CUSTOM -> selected
                    SpoofInheritanceMode.GLOBAL -> globalProfile
                    SpoofInheritanceMode.DISABLED -> null
                }
                val verified = lastWrite?.applied == true
                val realDevice = stringResource(R.string.spoof_ui_real_device)

                // 1 — الحالة: أي جهاز يراه التطبيق + شارة الدليل
                MaxGroup {
                    MaxRow(
                        title = effective?.name ?: realDevice,
                        subtitle = listOfNotNull(labels[policy.mode.ordinal], effective?.model).joinToString(" · "),
                        icon = Icons.Rounded.PhoneAndroid,
                        iconTone = if (effective != null) MaxTone.Accent else MaxTone.Neutral,
                        trailing = {
                            MaxStatusPill(
                                text = stringResource(when {
                                    policy.mode == SpoofInheritanceMode.DISABLED -> R.string.spoof_ui_pill_off
                                    verified -> R.string.spoof_ui_pill_verified
                                    else -> R.string.spoof_ui_pill_unverified
                                }),
                                active = verified && policy.mode != SpoofInheritanceMode.DISABLED,
                            )
                        },
                    )
                }

                // 2 — الوضع
                MaxSegmented(options = labels, selectedIndex = policy.mode.ordinal, onSelect = { index ->
                    if (!busy && (index != SpoofInheritanceMode.CUSTOM.ordinal || selected != null)) {
                        viewModel.setMode(packageName, SpoofInheritanceMode.entries[index])
                    }
                })

                // 3 — الجهاز
                if (policy.mode != SpoofInheritanceMode.DISABLED) {
                    MaxSection(title = stringResource(R.string.spoof_ui_device_title)) {
                        if (policy.mode == SpoofInheritanceMode.GLOBAL) {
                            MaxInfoStrip(text = stringResource(R.string.spoof_ui_global_hint))
                            if (onOpenStudio != null) {
                                OutlinedButton(onClick = onOpenStudio, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.spoof_ui_open_studio))
                                }
                            }
                        }
                        FilledTonalButton(enabled = !busy, onClick = { pickingSample = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sample_pick_button))
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                        ) {
                            if (policy.mode == SpoofInheritanceMode.CUSTOM && selected != null) {
                                TextButton(enabled = !busy, onClick = { editing = selected }) {
                                    Text(stringResource(R.string.spoof_ui_edit_current))
                                }
                            }
                            TextButton(enabled = !busy && workspace.profiles.size < 100, onClick = {
                                val values = viewModel.observed
                                editing = runCatching { SpoofProfile(java.util.UUID.randomUUID().toString(), newProfileName,
                                    values[SpoofField.BRAND].orEmpty(), values[SpoofField.MODEL].orEmpty(),
                                    values[SpoofField.DEVICE].orEmpty(), values[SpoofField.PRODUCT].orEmpty()) }.getOrNull()
                                newProfileForApp = editing != null
                            }) { Text(stringResource(R.string.spoof_ui_new_from_device)) }
                        }
                    }
                }

                // الملفات المحفوظة (للوضع المخصّص أو عند غياب ملف)
                if (policy.mode == SpoofInheritanceMode.CUSTOM || (policy.mode != SpoofInheritanceMode.DISABLED && selected == null && workspace.profiles.isNotEmpty())) {
                    MaxCollapsibleGroup(
                        title = stringResource(R.string.spoof_ui_saved_profiles),
                        summary = stringResource(R.string.spoof_ui_saved_profiles_n, workspace.profiles.size),
                        initiallyExpanded = selected == null,
                    ) {
                        if (workspace.profiles.isEmpty()) Text(stringResource(R.string.spoof_no_profiles), modifier = Modifier.padding(MaxSpace.lg))
                        workspace.profiles.forEachIndexed { index, profile ->
                            if (index > 0) MaxGroupDivider()
                            MaxRow(title = profile.name, subtitle = profile.model, enabled = !busy,
                                iconTone = if (profile.id == selected?.id) MaxTone.Accent else MaxTone.Neutral,
                                icon = Icons.Rounded.Fingerprint,
                                onClick = { viewModel.assign(packageName, profile.id) },
                                trailing = { TextButton(enabled = !busy, onClick = { editing = profile }) {
                                    Text(stringResource(R.string.spoof_edit))
                                } })
                        }
                    }
                }

                // متقدّم: سياسة كل فئة (مخصّص فقط)
                if (policy.mode == SpoofInheritanceMode.CUSTOM) {
                    MaxCollapsibleGroup(
                        title = stringResource(R.string.spoof_ui_advanced),
                        summary = stringResource(R.string.spoof_ui_advanced_summary),
                    ) {
                        listOf(SpoofCategory.IDENTITY, SpoofCategory.BUILD).forEach { category ->
                            Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                                verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                                Text(stringResource(if (category == SpoofCategory.IDENTITY) R.string.identity_category_identity
                                    else R.string.identity_category_build), style = MaterialTheme.typography.labelLarge)
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
                    }
                }

                // 4 — الخيارات (COPG)
                if (policy.mode != SpoofInheritanceMode.DISABLED) {
                    val hasDevice = if (policy.mode == SpoofInheritanceMode.CUSTOM) selected != null else workspace.globalProfileId != null
                    AppSpoofTagsSection(packageName, policy, hasDevice, packageName in acknowledged, busy, viewModel)
                }

                // 5 — الحالة والتحقق: كل الملاحظات الطويلة في مجموعة مطوية واحدة
                val notes = buildList {
                    add(stringResource(R.string.identity_not_verified))
                    if (lastWrite != null) add(stringResource(if (verified) R.string.identity_config_verified else R.string.identity_apply_failed))
                    if (recoveryFailed) add(stringResource(R.string.identity_recovery_corrupt))
                    recovery.firstOrNull { it.engineId == SpoofCopgContract.MODULE_ID }?.let { entry ->
                        add(stringResource(R.string.identity_recovery_entry,
                            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAtMs)),
                            stringResource(when (entry.phase) {
                                SpoofRecoveryPhase.CONFIG_VERIFIED -> R.string.identity_config_verified
                                SpoofRecoveryPhase.RESTORED -> R.string.identity_rollback_ok
                                else -> R.string.identity_recovery_pending
                            })))
                    }
                    if (engineConfig?.present == false) add(stringResource(R.string.identity_engine_missing))
                    if (workspace.globalProfileId != null && policy.mode == SpoofInheritanceMode.DISABLED) add(stringResource(R.string.identity_isolation_gap))
                    if (policy.mode == SpoofInheritanceMode.CUSTOM && selected == null) add(stringResource(R.string.identity_choose_custom))
                }
                MaxCollapsibleGroup(
                    title = stringResource(R.string.spoof_ui_status),
                    summary = stringResource(R.string.spoof_ui_status_n, notes.size),
                ) {
                    Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                        notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    MaxGroupDivider()
                    Text(stringResource(R.string.spoof_ui_compare), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm))
                    EffectiveSpoofProfileResolver.resolve(workspace, packageName, viewModel.observed).fields
                        .filter { it.field == SpoofField.MODEL || it.field == SpoofField.FINGERPRINT }.forEach { field ->
                            MaxRow(title = field.field.name, subtitle = stringResource(R.string.identity_value_comparison,
                                field.observed ?: stringResource(R.string.status_unknown),
                                field.target ?: stringResource(R.string.status_unknown), stringResource(R.string.status_unknown)))
                        }
                }

                // 6 — إقرار المخاطر
                SpoofBarrierSection(packageName, packageName in acknowledged, !busy,
                    onAcknowledge = { viewModel.acknowledge(packageName, true) },
                    onRevoke = { viewModel.acknowledge(packageName, false) })
            }
        }
    }
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
