/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.spoof.PerAppDeviceModel
import nd.max.core.spoof.SampleDeviceCatalog
import nd.max.core.spoof.SpoofCpuCatalog
import nd.max.core.spoof.SpoofCpuCatalogParser
import nd.max.core.spoof.SpoofCustomDevice
import nd.max.core.spoof.SpoofDeviceCatalog
import nd.max.core.spoof.SpoofEngineReason
import nd.max.core.spoof.SpoofImpersonation
import nd.max.ui.component.MaxInfoStrip
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.content
import nd.max.ui.viewmodel.SpoofStudioViewModel

/**
 * «تزييف هذا التطبيق» — محتوى تبويب «تزييف» داخل إعدادات التطبيق. التطبيق المفتوح هو الهدف دائمًا،
 * فلا حقل لاسم حزمة. ثلاثة أقسام من الأعلى إلى الأسفل:
 *
 * 1. **نوع الجهاز الآن**: بطاقة واحدة. نقرها يفتح نافذة بكل أجهزة الكتالوج، وآخرها «معلومات جهاز مخصص».
 * 2. **الانتحال**: مطوٍ ومغلق افتراضيًا، ويضم **الخيارات المجانية فقط** من مجموعة COPG (انتحال المعالج
 *    وحظره). خيارات PRO (COW وAndroid ID) لا تُنقل. وتعديلات COPG الأخرى ليست هنا، بل في تبويبات
 *    الأداء والعرض حيث تعمل.
 * 3. **تجهيز / إرجاع**: كتابة ملفّ محرّك COPG مع قراءة بعدها، كما كانت.
 *
 * ### الصدق (ADR-07)
 * «مُتحقَّق» تعني أن ملفّ المحرّك كُتب وقُرئ بعد الكتابة، لا أن التطبيق يرى ذلك الجهاز. وأي رفض من
 * المحرّك يُعرض باسمه.
 */
@Composable
internal fun AppSpoofSection(
    packageName: String,
    viewModel: SpoofStudioViewModel = hiltViewModel(),
) {
    val configuration by viewModel.configuration.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val acknowledged by viewModel.acknowledgments.collectAsStateWithLifecycle()
    val lastWrite by viewModel.lastWrite.collectAsStateWithLifecycle()
    val engineConfig by viewModel.engineConfig.collectAsStateWithLifecycle()
    val verifiedRevision by viewModel.verifiedRevision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // الكتالوجان مشحونان مع التطبيق، ويُقرآن من الأصول مرّة واحدة لكل شاشة.
    val catalog by produceState<SampleDeviceCatalog?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("spoof/device_catalog.json").bufferedReader().use { it.readText() } }
                .getOrNull()?.let(SpoofDeviceCatalog::parse)
        }
    }
    val cpuCatalog by produceState<SpoofCpuCatalog?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("spoof/cpu_catalog.json").bufferedReader().use { it.readText() } }
                .getOrNull()?.let(SpoofCpuCatalogParser::parse)
        }
    }
    // قراءة حالة المحرّك عند ظهور هذا التبويب فقط (كانت تحدث عند إنشاء الـViewModel).
    LaunchedEffect(Unit) { viewModel.refresh() }
    var confirmApply by remember(packageName) { mutableStateOf(false) }
    var confirmRestore by remember(packageName) { mutableStateOf(false) }
    var devicePickerOpen by rememberSaveable(packageName) { mutableStateOf(false) }
    var customOpen by rememberSaveable(packageName) { mutableStateOf(false) }
    var cpuPickerOpen by rememberSaveable(packageName) { mutableStateOf(false) }

    val workspace = configuration.workspace
    if (workspace == null) {
        Text(stringResource(if (configuration.loading) R.string.spoof_reading else R.string.spoof_load_failed))
        return
    }
    val device = PerAppDeviceModel.effective(workspace, packageName)
    // «مُتحقَّق» = كتابة نجحت **ولم يُعدَّل شيء بعدها**؛ فأي نقرة جهاز أو تعديل يُبطل الوسم حتى تجهيز جديد.
    val stale = lastWrite?.applied == true && verifiedRevision != configuration.revision
    val verified = device != null && lastWrite?.applied == true && !stale
    val canAdd = workspace.profiles.size < 100
    val tags = workspace.appPolicy(packageName).tags
    val cpuKey = SpoofImpersonation.cpuKey(tags)
    val cpuName = cpuKey?.let { key -> cpuCatalog?.models?.firstOrNull { it.key == key }?.name ?: key }
    val blocksCpu = SpoofImpersonation.blocksCpu(tags)
    // لا يُعاد استعمال ملف مخصّص إلا إن كان مربوطًا بهذا التطبيق وحده، فلا يتغيّر تطبيق آخر بالخطأ.
    val reusableCustomId = device?.id?.takeIf { id ->
        id.startsWith(SpoofCustomDevice.PROFILE_PREFIX) && workspace.bindings[packageName] == id &&
            workspace.bindings.values.count { it == id } == 1
    }
    val impersonationSummary = when {
        device == null -> stringResource(R.string.spoof_impersonation_needs_device)
        cpuName != null -> stringResource(R.string.spoof_impersonation_summary_cpu, cpuName)
        blocksCpu -> stringResource(R.string.spoof_cpu_block)
        else -> stringResource(R.string.spoof_cpu_real)
    }

    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        // 1 — نوع الجهاز الآن. البطاقة نفسها تفتح نافذة الأجهزة الكاملة.
        MaxSection(
            title = stringResource(R.string.spoof_device_for_app),
            description = stringResource(R.string.spoof_device_pick),
        ) {
            MaxGroup {
                MaxRow(
                    title = device?.name ?: stringResource(R.string.spoof_ui_real_device),
                    subtitle = listOfNotNull(device?.brand, device?.model).joinToString(" · ")
                        .ifEmpty { stringResource(R.string.spoof_device_real_hint) },
                    icon = Icons.Rounded.PhoneAndroid,
                    iconTone = if (device != null) MaxTone.Accent else MaxTone.Neutral,
                    enabled = !busy,
                    onClick = { devicePickerOpen = true },
                    trailing = {
                        MaxStatusPill(
                            text = stringResource(
                                when {
                                    device == null -> R.string.spoof_ui_pill_off
                                    verified -> R.string.spoof_ui_pill_verified
                                    else -> R.string.spoof_ui_pill_unverified
                                }
                            ),
                            active = verified,
                        )
                    },
                )
            }
        }

        // حال المحرّك تُقال قبل الفعل لا بعده (انظر تعليق الإصدار السابق: الرفض يُعرض باسمه).
        engineConfig?.unavailableReason?.let { reason ->
            MaxInfoStrip(text = engineRefusalText(reason), accent = MaxTone.Caution.content())
        }

        // 2 — الانتحال: مطوٍ ومغلق افتراضيًا. الخيارات المجانية فقط.
        MaxSection(
            title = stringResource(R.string.spoof_impersonation_title),
            description = stringResource(R.string.spoof_impersonation_desc),
            collapsible = true,
            summary = impersonationSummary,
        ) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.spoof_cpu_spoof),
                    subtitle = cpuName ?: stringResource(R.string.spoof_cpu_real),
                    icon = Icons.Rounded.Memory,
                    iconTone = if (cpuKey != null) MaxTone.Accent else MaxTone.Neutral,
                    enabled = device != null && !busy,
                    onClick = { cpuPickerOpen = true },
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.spoof_cpu_block),
                    subtitle = stringResource(R.string.spoof_cpu_block_desc),
                    icon = Icons.Rounded.Block,
                    iconTone = if (blocksCpu) MaxTone.Accent else MaxTone.Neutral,
                    enabled = device != null && !busy,
                    trailing = {
                        Switch(
                            checked = blocksCpu,
                            enabled = device != null && !busy,
                            onCheckedChange = { viewModel.setBlockCpuForApp(packageName, it) },
                        )
                    },
                )
            }
        }

        // 3 — الفعل: تجهيز، أو إرجاع. لا شيء آخر. (التجهيز يحتاج جهازًا مختارًا؛ والإرجاع يحتاج ربطًا.)
        if (device != null) {
            MaxSection(title = stringResource(R.string.spoof_ui_apply_title)) {
                Text(
                    stringResource(R.string.spoof_device_restart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                )
                FilledTonalButton(
                    enabled = !busy,
                    onClick = { confirmApply = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                ) { Text(stringResource(R.string.spoof_ui_apply_action)) }
            }
        }
        if (workspace.bindings.containsKey(packageName)) {
            MaxSection(title = stringResource(R.string.spoof_ui_restore_title)) {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmRestore = true },
                    modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                ) { Text(stringResource(R.string.spoof_ui_restore_action)) }
            }
        }

        // 4 — الصدق: آخر نتيجة كتابة من المحرّك، بلا تجميل.
        lastWrite?.let { write ->
            when {
                stale -> MaxInfoStrip(
                    text = stringResource(R.string.spoof_ui_apply_pending),
                    accent = MaxTone.Accent.content(),
                )
                write.applied -> MaxInfoStrip(text = stringResource(R.string.identity_config_verified))
                else -> MaxInfoStrip(
                    text = engineRefusalText(write.reason),
                    accent = MaxTone.Caution.content(),
                )
            }
        }
        if (lastWrite?.rollbackAttempted == true && lastWrite?.rollbackVerified != true) {
            Text(
                stringResource(R.string.identity_recovery_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = MaxSpace.lg),
            )
        }
    }

    // نوافذ الأجهزة والمعالجات. تُغلق قبل تنفيذ الفعل حتى لا تبقى نافذة فوق حالة قديمة.
    if (devicePickerOpen) {
        SpoofDevicePickerDialog(
            devices = catalog?.devices.orEmpty(),
            catalogReady = catalog != null,
            currentKey = PerAppDeviceModel.sampleKey(device?.id),
            canAdd = canAdd,
            enabled = !busy,
            onPick = { sample ->
                devicePickerOpen = false
                viewModel.applySample(sample, packageName)
            },
            onCustom = {
                devicePickerOpen = false
                customOpen = true
            },
            onDismiss = { devicePickerOpen = false },
        )
    }
    if (customOpen) {
        SpoofCustomDeviceDialog(
            initial = device,
            reusableId = reusableCustomId,
            canAdd = canAdd,
            onSave = { profile ->
                customOpen = false
                viewModel.saveCustomDeviceForApp(packageName, profile)
            },
            onDismiss = { customOpen = false },
        )
    }
    if (cpuPickerOpen) {
        SpoofCpuPickerDialog(
            models = cpuCatalog?.models.orEmpty(),
            currentKey = cpuKey,
            enabled = device != null && !busy,
            onPick = { key ->
                cpuPickerOpen = false
                viewModel.setCpuForApp(packageName, key)
            },
            onDismiss = { cpuPickerOpen = false },
        )
    }

    // حوار التجهيز = الإقرار نفسه: نصّ المخاطر يُقرأ هنا، ثم صراحةً «أقرّ وجهّز».
    if (confirmApply) {
        val needsAck = packageName !in acknowledged
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text(stringResource(R.string.spoof_ui_apply_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                    if (needsAck) Text(stringResource(R.string.spoof_barrier_text))
                    Text(stringResource(R.string.spoof_ui_apply_confirm_text))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmApply = false
                    viewModel.applyForApp(packageName, acknowledgeNeeded = needsAck)
                }) {
                    Text(stringResource(if (needsAck) R.string.spoof_barrier_accept else R.string.spoof_ui_apply_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) { Text(stringResource(R.string.spoof_cancel)) }
            },
        )
    }

    // الإرجاع: حذف ربط هذا التطبيق ثم إعادة كتابة المحرّك بلا هذا التطبيق — في تسلسل واحد.
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.spoof_ui_restore_title)) },
            text = { Text(stringResource(R.string.spoof_ui_restore_notice)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmRestore = false
                    viewModel.restoreForApp(packageName)
                }) { Text(stringResource(R.string.spoof_ui_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.spoof_cancel)) }
            },
        )
    }
}

/**
 * نصّ رفض المحرّك. **الحالات الأربع المقيسة لها نصوص تقول ما يُفعل بها**، وما لا نصّ له يُعرض
 * باسم رمزه كما كان — فلا تُبتلع حالةٌ جديدة في جملة عامّة.
 *
 * ولماذا يحتاج هذا دالّة: العطب المُصلَح (تصدير ٢٠٢٦-١٠-٠٦) كان **رمزًا واحدًا** (`ENGINE_UNAVAILABLE`)
 * لأربع حالات علاجها مختلف، يُعرض للمستخدم بلا فكّ ولا إجراء. فالنصّ هنا هو نصف الإصلاح، والنصف
 * الآخر في `SpoofEngineGate` الذي يسمّي الحالة بدقّة.
 */
@Composable
private fun engineRefusalText(reason: SpoofEngineReason): String {
    val specific = when (reason) {
        SpoofEngineReason.ENGINE_MODULE_ABSENT -> R.string.spoof_engine_absent
        SpoofEngineReason.ENGINE_DISABLED -> R.string.spoof_engine_disabled
        SpoofEngineReason.ENGINE_REMOVAL_PENDING -> R.string.spoof_engine_removal_pending
        SpoofEngineReason.ENGINE_UPDATE_PENDING -> R.string.spoof_engine_update_pending
        else -> null
    }
    return if (specific != null) stringResource(specific)
    else stringResource(R.string.spoof_ui_apply_reason, reason.name)
}

