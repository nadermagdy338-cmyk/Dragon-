/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.spoof.SampleDevice
import nd.max.core.spoof.SampleDeviceCatalog
import nd.max.core.spoof.SampleDeviceSearch
import nd.max.core.spoof.SpoofDeviceCatalog
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

private val ListMaxHeight = 400.dp

private sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data object Failed : CatalogLoad
    data class Ready(val catalog: SampleDeviceCatalog) : CatalogLoad
}

/**
 * «اختر جهازًا نموذجيًا» — قائمة أجهزة جاهزة بأزرار كبسولة، **نقرة واحدة تملأ كل الحقول** (تلقائي)،
 * وزرّ «يدوي» يفتح محرّر الملفات لمن يريد أن يكتبها بنفسه. المنتقي لا يحفظ شيئًا بنفسه: يُرجع الجهاز المختار
 * إلى مَن استدعاه (`SpoofStudioViewModel.applySample`) فيمرّ الحفظ كله من المسار الواحد المعتاد.
 *
 * القراءة من `assets/spoof/device_catalog.json` على خيط IO؛ فشلها يُعلَن نصًّا ولا يُفرغ القائمة بصمت.
 */
@Composable
internal fun SampleDevicePickerDialog(
    canAdd: Boolean,
    onPick: (SampleDevice) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
    /** كتالوج محمّل سابقًا (من صفّ الأجهزة في الشاشة) فلا يُقرأ الأصل مرّتين. */
    preset: SampleDeviceCatalog? = null,
) {
    val context = LocalContext.current
    val load by produceState<CatalogLoad>(preset?.let(CatalogLoad::Ready) ?: CatalogLoad.Loading, context, preset) {
        if (preset != null) {
            value = CatalogLoad.Ready(preset)
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("spoof/device_catalog.json").bufferedReader().use { it.readText() } }
                .getOrNull()?.let(SpoofDeviceCatalog::parse)?.let { CatalogLoad.Ready(it) } ?: CatalogLoad.Failed
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sample_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                MaxSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.sample_search))
                when (val state = load) {
                    CatalogLoad.Loading -> Text(stringResource(R.string.spoof_reading))
                    CatalogLoad.Failed -> Text(stringResource(R.string.sample_unavailable), color = MaterialTheme.colorScheme.error)
                    is CatalogLoad.Ready -> {
                        val shown = remember(state, query) { SampleDeviceSearch.search(state.catalog.devices, query) }
                        if (!canAdd) Text(stringResource(R.string.sample_limit), color = MaterialTheme.colorScheme.error)
                        if (shown.isEmpty()) Text(stringResource(R.string.sample_empty))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = ListMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                        ) {
                            items(shown, key = { it.key }) { device -> SampleDeviceRow(device, enabled = canAdd) { onPick(device) } }
                        }
                        Text(
                            stringResource(R.string.sample_credit, state.catalog.sourceRef.substringBefore('@') +
                                "@" + state.catalog.sourceRef.substringAfter('@', "").take(7)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(stringResource(R.string.sample_auto_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onManual) { Text(stringResource(R.string.sample_manual)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sample_close)) } },
    )
}

@Composable
private fun SampleDeviceRow(device: SampleDevice, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(MaxRadius.pill)
    val release = device.androidRelease
    val subtitle = if (release != null) stringResource(R.string.sample_row_sub, release, device.model)
    else stringResource(R.string.sample_row_sub_plain, device.model)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaxSize.minTouchTarget)
            .clip(shape)
            .border(MaxSize.hairlineBorder, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MaxSpace.lg, vertical = MaxSpace.sm),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            if (device.fingerprintShared) {
                Text(stringResource(R.string.sample_fp_shared), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = MaxSpace.hairline))
            }
        }
    }
}
