/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.spoof.EffectiveSpoofProfileResolver
import nd.max.core.spoof.PerAppDeviceModel
import nd.max.core.spoof.SpoofCategory
import nd.max.core.spoof.SpoofCategoryMode
import nd.max.core.spoof.SpoofCopgContract
import nd.max.core.spoof.SpoofDeviceCatalog
import nd.max.core.spoof.SpoofField
import nd.max.core.spoof.SpoofInheritanceMode
import nd.max.core.spoof.SpoofProfile
import nd.max.core.spoof.SampleDeviceCatalog
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
 * هوية التطبيق (تزييف لكل تطبيق) — **الموضع الوحيد**: تبويب «تزييف» في إعدادات التطبيق بجانب «العرض».
 *
 * ### الترتيب: **الجهاز أوّلًا** (تصحيح بناءً على رفض المالك للنسخة السابقة)
 * كانت الشاشة تبدأ بسياسة الوراثة (عام · مخصّص · متوقّف) ثم بالخيارات، فقرأها المالك — بحقّ — شاشة
 * إعدادات لمحرّك COPG لا «مزيّف أجهزة لكل تطبيق» على نمط `device_faker`. فصارت الآن:
 *
 * 1. **جهاز هذا التطبيق** — ما يراه التطبيق الآن (اسم الجهاز وبصمته وكم حقلًا يغيّره عن جهازك، أو
 *    «الجهاز الحقيقي»)، وشارة دليل الحالة. لا ادّعاء «تم التزييف» بلا كتابة مُتحقَّقة.
 * 2. **صفّ الأجهزة** — بطاقات أفقية من كتالوج العيّنات، **نقرة واحدة تمنح هذا التطبيق ذلك الجهاز**،
 *    وبطاقة «المزيد» تفتح المنتقي القابل للبحث. تحديد الجهاز هو الفعل الأوّل هنا لا سياسة الوراثة.
 * 3. **التطبيق على هذا التطبيق** — تجهيز هذا التطبيق وحده عبر المحكّم مع قراءة بعد الكتابة، ثم
 *    إعادة تشغيل التطبيق المستهدف لا النظام.
 * 4. **متقدّم** — سياسة الوراثة (عام/مخصّص/متوقّف)، الملفات المحفوظة (تعديل/ربط/نسخة مستقلّة)، سياسة
 *    الفئات، خيارات COPG، وملاحظات الحالة والتحقّق والمقارنة. تبقى كاملة لكن لا تزحم الفعل اليومي.
 * 5. **إقرار المخاطر** — كما كان، ولا يُجهَّز شيء قبله.
 *
 * مستلهم مفاهيميًا من فكرة «القالب ↔ التطبيق» في `device_faker` (‏GPL: **تحليل فقط، صفر نسخ**) وعمّا
 * فُهم من واجهته في `docs/ai/DEVICE_FAKER_ANALYSIS.md`؛ والتنفيذ بمكوّناتنا ومسارنا (المستودع الواحد
 * الذي يقرأ ويكتب، والـarbiter)، والفرق المُعلَن: نحن نحفظ **أصل كل قيمة** ونعلن ما لا ندعمه بدلًا من
 * استبدال السجلّ كاملًا بصمت.
 */
@Composable
internal fun AppSpoofSection(
    packageName: String,
    viewModel: SpoofStudioViewModel = hiltViewModel(),
) {
    val configuration by viewModel.configuration.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val acknowledged by viewModel.acknowledgments.collectAsStateWithLifecycle()
    val engineConfig by viewModel.engineConfig.collectAsStateWithLifecycle()
    val lastWrite by viewModel.lastWrite.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val recoveryFailed by viewModel.recoveryFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // يُقرأ الأصل مرّة واحدة هنا ويُمرَّر إلى المنتقي: قراءتان لنفس الملفّ لا تفيدان أحدًا.
    val catalog by produceState<SampleDeviceCatalog?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("spoof/device_catalog.json").bufferedReader().use { it.readText() } }
                .getOrNull()?.let(SpoofDeviceCatalog::parse)
        }
    }
    val workspace = configuration.workspace
    val policy = workspace?.appPolicy(packageName)
    var editing by remember(packageName) { mutableStateOf<SpoofProfile?>(null) }
    var newProfileForApp by remember(packageName) { mutableStateOf(false) }
    var pickingSample by remember(packageName) { mutableStateOf(false) }
    // تأكيد واحد لتجهيز هذا التطبيق: أي تعديل لاحق يلغي التأكيد بدل تطبيق ما لم يُرَ.
    var applyConfirm by remember(packageName) { mutableStateOf<IdentityPerAppConfirmation?>(null) }
    val newProfileName = stringResource(R.string.spoof_new_profile)
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        when {
            configuration.loading -> Text(stringResource(R.string.spoof_reading))
            workspace == null || policy == null -> Text(stringResource(R.string.spoof_load_failed))
            else -> {
                val device = PerAppDeviceModel.effective(workspace, packageName)
                val verified = device != null && lastWrite?.applied == true
                val canAddProfile = workspace.profiles.size < 100
                val realDevice = stringResource(R.string.spoof_ui_real_device)
                val ordered = PerAppDeviceModel.rowOrder(catalog?.devices.orEmpty(), device?.id)
                val currentKey = PerAppDeviceModel.sampleKey(device?.id)

                // 1 — جهاز هذا التطبيق: أوّل ما يُقرأ، وأوّل ما يُغيَّر.
                MaxGroup {
                    MaxRow(
                        title = device?.name ?: realDevice,
                        subtitle = listOfNotNull(device?.brand, device?.model).joinToString(" · ")
                            .ifEmpty { stringResource(R.string.spoof_device_real_hint) },
                        icon = Icons.Rounded.PhoneAndroid,
                        iconTone = if (device != null) MaxTone.Accent else MaxTone.Neutral,
                        trailing = {
                            MaxStatusPill(
                                text = stringResource(when {
                                    device == null -> R.string.spoof_ui_pill_off
                                    verified -> R.string.spoof_ui_pill_verified
                                    else -> R.string.spoof_ui_pill_unverified
                                }),
                                active = verified,
                            )
                        },
                    )
                    if (device != null) {
                        device.fingerprint?.let { fingerprint ->
                            MaxGroupDivider()
                            Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
                                Text(stringResource(R.string.spoof_device_fingerprint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fingerprint, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        MaxGroupDivider()
                        val changed = PerAppDeviceModel.changedFieldCount(device, viewModel.observed)
                        Text(
                            if (changed > 0) stringResource(R.string.spoof_device_changes_n, changed)
                            else stringResource(R.string.spoof_device_changes_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                        )
                    }
                }

                // 2 — صفّ الأجهزة: نقرة واحدة تمنح هذا التطبيق ذلك الجهاز.
                MaxSection(
                    title = stringResource(R.string.spoof_device_for_app),
                    description = stringResource(R.string.spoof_device_pick),
                ) {
                    if (ordered.isEmpty()) {
                        Text(stringResource(R.string.sample_unavailable), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm))
                    } else {
                        SpoofDeviceRow(
                            devices = ordered,
                            currentKey = currentKey,
                            enabled = !busy && canAddProfile,
                            onPick = { viewModel.applySample(it, packageName) },
                            onMore = { pickingSample = true },
                            modifier = Modifier.padding(horizontal = MaxSpace.lg),
                        )
                    }
                    if (!canAddProfile) {
                        Text(stringResource(R.string.sample_limit), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm))
                    }
                    Text(stringResource(R.string.spoof_device_restart),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaxSpace.lg))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                    ) {
                        TextButton(enabled = !busy && canAddProfile, onClick = {
                            val values = viewModel.observed
                            editing = runCatching {
                                SpoofProfile(java.util.UUID.randomUUID().toString(), newProfileName,
                                    values[SpoofField.BRAND].orEmpty(), values[SpoofField.MODEL].orEmpty(),
                                    values[SpoofField.DEVICE].orEmpty(), values[SpoofField.PRODUCT].orEmpty())
                            }.getOrNull()
                            newProfileForApp = editing != null
                        }) { Text(stringResource(R.string.spoof_ui_new_from_device)) }
                        if (device != null) {
                            TextButton(enabled = !busy, onClick = { editing = device }) {
                                Text(stringResource(R.string.spoof_ui_edit_current))
                            }
                            val copyName = stringResource(R.string.spoof_ui_copy_name, device.name)
                            TextButton(enabled = !busy && canAddProfile,
                                onClick = { viewModel.copyProfileForApp(packageName, copyName) }) {
                                Text(stringResource(R.string.spoof_ui_copy_for_app))
                            }
                        }
                    }
                }

                // 3 — التطبيق على المحرّك: هذا التطبيق وحده، ويكتب عبر المحكّم ثم يقرأ بعد الكتابة.
                if (device != null) {
                    val canApply = !busy && packageName in acknowledged
                    MaxSection(title = stringResource(R.string.spoof_ui_apply_title),
                        description = stringResource(R.string.spoof_ui_apply_notice)) {
                        if (packageName !in acknowledged) {
                            MaxInfoStrip(text = stringResource(R.string.spoof_ui_apply_need_ack))
                        }
                        FilledTonalButton(enabled = canApply, onClick = {
                            applyConfirm = IdentityPerAppConfirmation(stateRevision = configuration.revision)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.spoof_ui_apply_action))
                        }
                    }
                }

                // 4 — متقدّم: كل السياسة والخيارات والملفّات والتحقّق — لا تزحم الفعل اليومي.
                MaxCollapsibleGroup(
                    title = stringResource(R.string.spoof_ui_advanced),
                    summary = stringResource(R.string.spoof_ui_advanced_summary),
                ) {
                    Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                        Text(stringResource(R.string.identity_mode_global) + " · " +
                            stringResource(R.string.identity_mode_custom) + " · " +
                            stringResource(R.string.identity_mode_disabled),
                            style = MaterialTheme.typography.labelLarge)
                        val labels = listOf(stringResource(R.string.identity_mode_global),
                            stringResource(R.string.identity_mode_custom),
                            stringResource(R.string.identity_mode_disabled))
                        MaxSegmented(options = labels, selectedIndex = policy.mode.ordinal, enabled = !busy,
                            onSelect = { index ->
                                if (index != SpoofInheritanceMode.CUSTOM.ordinal || device != null ||
                                    workspace.profiles.isNotEmpty()) {
                                    viewModel.setMode(packageName, SpoofInheritanceMode.entries[index])
                                }
                            })
                        val globalProfile = workspace.profiles.firstOrNull { it.id == workspace.globalProfileId }
                        if (policy.mode == SpoofInheritanceMode.GLOBAL) {
                            Text(stringResource(R.string.spoof_ui_global_hint_perapp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            globalProfile?.let {
                                Text(listOfNotNull(it.name, it.model).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // الملفّات المحفوظة: ربط ملفّ بهذا التطبيق، أو تعديل ملفّ قائم.
                    MaxGroupDivider()
                    Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm)) {
                        Text(stringResource(R.string.spoof_ui_saved_profiles_n, workspace.profiles.size),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    if (workspace.profiles.isEmpty()) {
                        Text(stringResource(R.string.spoof_no_profiles), modifier = Modifier.padding(MaxSpace.lg))
                    }
                    workspace.profiles.forEach { profile ->
                        MaxRow(
                            title = profile.name,
                            subtitle = profile.model,
                            enabled = !busy,
                            icon = Icons.Rounded.Fingerprint,
                            iconTone = if (profile.id == device?.id) MaxTone.Accent else MaxTone.Neutral,
                            onClick = { viewModel.assign(packageName, profile.id) },
                            trailing = {
                                TextButton(enabled = !busy, onClick = { editing = profile }) {
                                    Text(stringResource(R.string.spoof_edit))
                                }
                            },
                        )
                    }

                    // سياسة الفئات (تُقرأ فقط حين يكون الوضع مخصّصًا).
                    if (policy.mode == SpoofInheritanceMode.CUSTOM) {
                        MaxGroupDivider()
                        listOf(SpoofCategory.IDENTITY, SpoofCategory.BUILD).forEach { category ->
                            Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                                verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                                Text(stringResource(if (category == SpoofCategory.IDENTITY) R.string.identity_category_identity
                                    else R.string.identity_category_build), style = MaterialTheme.typography.labelLarge)
                                MaxSegmented(options = listOf(stringResource(R.string.identity_category_custom),
                                    stringResource(R.string.identity_mode_global), stringResource(R.string.identity_category_real)),
                                    selectedIndex = (policy.categories[category] ?: SpoofCategoryMode.INHERIT).ordinal,
                                    enabled = !busy,
                                    onSelect = { index -> viewModel.change { latest ->
                                        val fresh = latest.appPolicy(packageName)
                                        latest.setAppPolicy(packageName, fresh.copy(categories = fresh.categories +
                                            (category to SpoofCategoryMode.entries[index])))
                                    } })
                            }
                        }
                    }

                    // خيارات COPG (تُكتب مع جهاز فعّال فقط).
                    if (device != null) {
                        MaxGroupDivider()
                        AppSpoofTagsSection(packageName, policy, true, packageName in acknowledged, busy, viewModel)
                    }

                    // الحالة والتحقّق: كل الملاحظات الطويلة في مكان واحد.
                    val notes = buildList {
                        add(stringResource(R.string.identity_not_verified))
                        if (lastWrite != null) {
                            add(stringResource(if (verified) R.string.identity_config_verified else R.string.identity_apply_failed))
                        }
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
                        if (workspace.globalProfileId != null && policy.mode == SpoofInheritanceMode.DISABLED) {
                            add(stringResource(R.string.identity_isolation_gap))
                        }
                        if (policy.mode == SpoofInheritanceMode.CUSTOM && device == null) {
                            add(stringResource(R.string.identity_choose_custom))
                        }
                    }
                    MaxGroupDivider()
                    MaxCollapsibleGroup(
                        title = stringResource(R.string.spoof_ui_status),
                        summary = stringResource(R.string.spoof_ui_status_n, notes.size),
                    ) {
                        Column(Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                            notes.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        MaxGroupDivider()
                        Text(stringResource(R.string.spoof_ui_compare), style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm))
                        EffectiveSpoofProfileResolver.resolve(workspace, packageName, viewModel.observed).fields
                            .filter { it.field == SpoofField.MODEL || it.field == SpoofField.FINGERPRINT }
                            .forEach { field ->
                                MaxRow(title = field.field.name, subtitle = stringResource(
                                    R.string.identity_value_comparison, field.observed ?: stringResource(R.string.status_unknown),
                                    field.target ?: stringResource(R.string.status_unknown),
                                    stringResource(R.string.status_unknown)))
                            }
                    }
                }

                // 5 — إقرار المخاطر
                SpoofBarrierSection(packageName, packageName in acknowledged, !busy,
                    onAcknowledge = { viewModel.acknowledge(packageName, true) },
                    onRevoke = { viewModel.acknowledge(packageName, false) })
            }
        }
    }
    if (pickingSample && workspace != null) {
        SampleDevicePickerDialog(
            canAdd = workspace.profiles.size < 100,
            preset = catalog,
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
    applyConfirm?.let { confirmation ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { applyConfirm = null },
            title = { Text(stringResource(R.string.spoof_ui_apply_confirm_title)) },
            text = { Text(stringResource(R.string.spoof_ui_apply_confirm_text)) },
            confirmButton = {
                TextButton(enabled = !busy && configuration.revision == confirmation.stateRevision, onClick = {
                    viewModel.apply(global = false, clear = false, expectedRevision = confirmation.stateRevision)
                    applyConfirm = null
                }) { Text(stringResource(R.string.spoof_ui_apply_confirm)) }
            },
            dismissButton = { TextButton(onClick = { applyConfirm = null }) { Text(stringResource(R.string.spoof_cancel)) } },
        )
    }
}

private data class IdentityPerAppConfirmation(val stateRevision: Long)
