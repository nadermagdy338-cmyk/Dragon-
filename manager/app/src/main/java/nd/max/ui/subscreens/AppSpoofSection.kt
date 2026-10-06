/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import nd.max.core.spoof.SampleDevice
import nd.max.core.spoof.SampleDeviceCatalog
import nd.max.core.spoof.SampleDeviceSearch
import nd.max.core.spoof.SpoofDeviceCatalog
import nd.max.ui.component.MaxInfoStrip
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.viewmodel.SpoofStudioViewModel

/**
 * «تزييف هذا التطبيق» — **أُعيد تصميمه من الصفر** بعد أن رُفض مرتين: مرّة لأنه بدأ بسياسة محرّك COPG
 * (عام/مخصّص/متوقّف)، ومرّة لأن طبقاتها (الأنماط · الفئات · الوسوم · الملفّات المحفوظة · النسخة المستقلة ·
 * «متقدّم») جعلت الفعل الوحيد المطلوب — «هذا التطبيق يرى ذلك الجهاز» — مدفونًا بينها.
 *
 * ### الفكرة: فعل واحد، صريح، بلا طبقات
 * 1. **بطاقة واحدة** تقول ما يراه هذا التطبيق الآن (جهازك الحقيقي أو جهاز من الكتالوج) وحالته.
 * 2. **بحث + قائمة** بأجهزة الكتالوج (15 جهازًا): **نقرة واحدة تمنح هذا التطبيق ذلك الجهاز**.
 * 3. **زرّان فقط**: «تجهيز» (يكتب عبر المحكّم مع قراءة بعد الكتابة) و«إرجاع» (يحذف ربط هذا التطبيق).
 * 4. **حوار التجهيز يحمل الإقرار**: نصّ المخاطر يُقرأ فيه، وتأكيده هو الإقرار نفسه — فلا قسم منفصل،
 *    ولا يُكتب شيء بلا فعل صريح من المستخدم (ADR-16: الأدوات عالية الخطأ مُبوَّبة لا معروضة).
 *
 * ### ما لم يعد يُعرض (بقرار المالك: «أزل ما لا يفيد ولا يعمل»)
 * سياسة الوراثة والفئات والوسوم، والملفّات المحفوظة ومحرّر الحقول، و«النسخة المستقلة»، ومقارنة
 * «المرصود مقابل الهدف»، وأهداف معدّل الإطار، ومفاتيح per-app الأخرى، وقسم مسح التطبيق. الطبقة
 * الجوهرية المُختبَرة باقية كما هي (ADR-18)، لكنّ الواجهة لا تعرضها.
 *
 * ### الصدق (ADR-07)
 * «مُتحقَّق» تعني **ملفّ المحرّك كُتب وقُرئ بعد الكتابة** — لا «التطبيق يرى ذلك الجهاز»؛ ولا ادّعاء
 * أثر في عملية الهدف بلا رصد. وأي رفض من المحرّك يُعرض باسمه، ويُسجَّل في السجلّ التشخيصي.
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
    val verifiedRevision by viewModel.verifiedRevision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // يُقرأ الأصل مرّة واحدة هنا — قراءتان لنفس الملفّ لا تفيدان أحدًا.
    val catalog by produceState<SampleDeviceCatalog?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("spoof/device_catalog.json").bufferedReader().use { it.readText() } }
                .getOrNull()?.let(SpoofDeviceCatalog::parse)
        }
    }
    var query by rememberSaveable(packageName) { mutableStateOf("") }
    var confirmApply by remember(packageName) { mutableStateOf(false) }
    var confirmRestore by remember(packageName) { mutableStateOf(false) }

    val workspace = configuration.workspace
    if (workspace == null) {
        Text(stringResource(if (configuration.loading) R.string.spoof_reading else R.string.spoof_load_failed))
        return
    }
    val device = PerAppDeviceModel.effective(workspace, packageName)
    // «مُتحقَّق» = كتابة نجحت **ولم يُعدَّل شيء بعدها**؛ فأي نقرة جهاز أو تعديل يُبطل الوسم حتى تجهيز جديد.
    // ولذلك لا تقول الواجهة إنّ جهازًا اختير للتوّ «مُتحقَّق» — وهو العطب الذي كان قائمًا.
    val stale = lastWrite?.applied == true && verifiedRevision != configuration.revision
    val verified = device != null && lastWrite?.applied == true && !stale
    val canAdd = workspace.profiles.size < 100

    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        // 1 — ما يراه هذا التطبيق الآن. أوّل سطر، وأصدق سطر.
        MaxGroup {
            MaxRow(
                title = device?.name ?: stringResource(R.string.spoof_ui_real_device),
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
        }

        // 2 — الجهاز المطلوب: بحث + قائمة، نقرة واحدة = اختيار لهذا التطبيق. بلا تمرير داخلي
        // (الشاشة تُستضاف داخل LazyColumn، وقائمة كسولة/تمرير متداخل داخلها يُسقط القياس).
        MaxSection(
            title = stringResource(R.string.spoof_device_for_app),
            description = stringResource(R.string.spoof_device_pick),
        ) {
            MaxSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.sample_search),
                modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
            )
            val devices = catalog?.devices.orEmpty()
            if (catalog == null) {
                Text(
                    stringResource(R.string.sample_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                )
            } else {
                val shown = remember(devices, query) { SampleDeviceSearch.search(devices, query) }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.sample_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                    )
                }
                shown.forEach { sample ->
                    DeviceChoiceRow(
                        device = sample,
                        selected = sample.key == PerAppDeviceModel.sampleKey(device?.id),
                        enabled = !busy && canAdd,
                        onClick = { viewModel.applySample(sample, packageName) },
                    )
                }
                if (!canAdd) {
                    Text(
                        stringResource(R.string.sample_limit),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
                    )
                }
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
                    text = stringResource(R.string.spoof_ui_apply_reason, write.reason.name),
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

/** صفّ جهاز واحد: الاسم + إصدار أندرويد والطراز + علامة الاختيار. لا شيء آخر. */
@Composable
private fun DeviceChoiceRow(
    device: SampleDevice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val release = device.androidRelease
    MaxRow(
        title = device.name,
        subtitle = if (release != null) stringResource(R.string.sample_row_sub, release, device.model)
        else stringResource(R.string.sample_row_sub_plain, device.model),
        icon = Icons.Rounded.PhoneAndroid,
        iconTone = if (selected) MaxTone.Accent else MaxTone.Neutral,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.spoof_device_in_use),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
    MaxGroupDivider()
}
