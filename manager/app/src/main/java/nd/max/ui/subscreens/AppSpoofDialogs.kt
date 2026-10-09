/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.spoof.CpuModel
import nd.max.core.spoof.SampleDevice
import nd.max.core.spoof.SampleDeviceSearch
import nd.max.core.spoof.SpoofCustomDevice
import nd.max.core.spoof.SpoofProfile
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import java.util.UUID

/**
 * نافذة اختيار الجهاز: بحث، ثم كل أجهزة الكتالوج بنقرة واحدة لكل جهاز، وآخرها «معلومات جهاز مخصص».
 * تُقدَّم كاملة بلا تمرير داخل القائمة الأم، لأن الشاشة نفسها داخل قائمة كسولة.
 */
@Composable
internal fun SpoofDevicePickerDialog(
    devices: List<SampleDevice>,
    catalogReady: Boolean,
    currentKey: String?,
    canAdd: Boolean,
    enabled: Boolean,
    onPick: (SampleDevice) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(devices, query) { SampleDeviceSearch.search(devices, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                MaxSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.sample_search),
                )
                Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (!catalogReady) {
                        Text(stringResource(R.string.sample_unavailable), color = MaterialTheme.colorScheme.error)
                    }
                    shown.forEach { sample ->
                        DeviceChoiceRow(
                            device = sample,
                            selected = sample.key == currentKey,
                            enabled = enabled && canAdd,
                            onClick = { onPick(sample) },
                        )
                    }
                    if (catalogReady && shown.isEmpty()) {
                        Text(stringResource(R.string.sample_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!canAdd) {
                        Text(stringResource(R.string.sample_limit), color = MaterialTheme.colorScheme.error)
                    }
                    MaxRow(
                        title = stringResource(R.string.spoof_picker_custom),
                        subtitle = stringResource(R.string.spoof_picker_custom_hint),
                        icon = Icons.Rounded.Edit,
                        iconTone = MaxTone.Accent,
                        enabled = enabled,
                        onClick = onCustom,
                    )
                    MaxGroupDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.spoof_cancel)) } },
    )
}

/**
 * «معلومات جهاز مخصص»: حقول الجهاز كما يكتبها المحرّك. التحقّق بقاعدة [SpoofCustomDevice] نفسها، فالخطأ
 * يظهر هنا لا بعد الحفظ. الحقول الاختيارية (الشركة المصنّعة والبصمة وSDK) مذكورة في نص القواعد أعلى النافذة.
 */
@Composable
internal fun SpoofCustomDeviceDialog(
    initial: SpoofProfile?,
    reusableId: String?,
    canAdd: Boolean,
    onSave: (SpoofProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var manufacturer by remember { mutableStateOf(initial?.manufacturer.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var deviceCode by remember { mutableStateOf(initial?.device.orEmpty()) }
    var product by remember { mutableStateOf(initial?.product.orEmpty()) }
    var fingerprint by remember { mutableStateOf(initial?.fingerprint.orEmpty()) }
    var sdk by remember { mutableStateOf(initial?.sdkInt?.toString().orEmpty()) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_custom_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                Text(
                    stringResource(R.string.spoof_custom_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DialogField(stringResource(R.string.spoof_profile_name), name) { name = it }
                DialogField(stringResource(R.string.spoof_brand), brand) { brand = it }
                DialogField(stringResource(R.string.spoof_manufacturer), manufacturer) { manufacturer = it }
                DialogField(stringResource(R.string.spoof_model), model) { model = it }
                DialogField(stringResource(R.string.spoof_device), deviceCode) { deviceCode = it }
                DialogField(stringResource(R.string.spoof_product), product) { product = it }
                DialogField(stringResource(R.string.spoof_fingerprint), fingerprint) { fingerprint = it }
                DialogField(stringResource(R.string.spoof_sdk), sdk) { sdk = it }
                if (!canAdd) {
                    Text(stringResource(R.string.sample_limit), color = MaterialTheme.colorScheme.error)
                }
                if (invalid) {
                    Text(stringResource(R.string.spoof_custom_invalid), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    val draft = SpoofCustomDevice(
                        name = name,
                        brand = brand,
                        manufacturer = manufacturer,
                        model = model,
                        device = deviceCode,
                        product = product,
                        fingerprint = fingerprint,
                        sdk = sdk,
                    )
                    val id = reusableId ?: (SpoofCustomDevice.PROFILE_PREFIX + UUID.randomUUID())
                    val profile = draft.toProfile(id)
                    if (profile == null) {
                        invalid = true
                    } else {
                        onSave(profile)
                    }
                },
            ) { Text(stringResource(R.string.spoof_custom_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.spoof_cancel)) } },
    )
}

/** نافذة المعالج: «المعالج الحقيقي» أولًا لإلغاء الانتحال، ثم كتالوج COPG للمعالجات. */
@Composable
internal fun SpoofCpuPickerDialog(
    models: List<CpuModel>,
    currentKey: String?,
    enabled: Boolean,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_cpu_picker_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                MaxRow(
                    title = stringResource(R.string.spoof_cpu_real),
                    icon = Icons.Rounded.Memory,
                    iconTone = if (currentKey == null) MaxTone.Accent else MaxTone.Neutral,
                    enabled = enabled,
                    onClick = { onPick(null) },
                    trailing = { SelectedMark(selected = currentKey == null) },
                )
                MaxGroupDivider()
                models.forEach { cpu ->
                    MaxRow(
                        title = cpu.name,
                        subtitle = cpu.key,
                        icon = Icons.Rounded.Memory,
                        iconTone = if (cpu.key == currentKey) MaxTone.Accent else MaxTone.Neutral,
                        enabled = enabled,
                        onClick = { onPick(cpu.key) },
                        trailing = { SelectedMark(selected = cpu.key == currentKey) },
                    )
                    MaxGroupDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.spoof_cancel)) } },
    )
}

/** صفّ جهاز واحد: الاسم + إصدار أندرويد والطراز + علامة الاختيار. لا شيء آخر. */
@Composable
internal fun DeviceChoiceRow(
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
        trailing = { SelectedMark(selected = selected) },
    )
    MaxGroupDivider()
}

@Composable
private fun SelectedMark(selected: Boolean) {
    if (selected) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = stringResource(R.string.spoof_device_in_use),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DialogField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
