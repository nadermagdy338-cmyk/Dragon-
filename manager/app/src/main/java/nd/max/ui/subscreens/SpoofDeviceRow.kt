/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.spoof.SampleDevice
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

private val CardWidth = 148.dp
private val CardMinHeight = 104.dp

/**
 * صفّ الأجهزة — قلب تبويب التزييف لكل تطبيق: بطاقات أفقية من كتالوج العيّنات، و**نقرة واحدة تمنح هذا
 * التطبيق ذلك الجهاز** (تنشئ/تحدّث ملفّ `sample_<key>` وتربطه به) — وهو الفعل الذي طلبه المالك على نمط
 * `device_faker` بدل تقديم سياسة الوراثة (عام/مخصّص/متوقف) في صدر الشاشة.
 *
 * لا يكتب شيئًا بنفسه: يُرجع الجهاز المختار إلى مَن استدعاه، فيمرّ الحفظ كله من مسار المستودع الواحد.
 * وبطاقة «المزيد» تفتح المنتقي القابل للبحث — ولا تُخفى الأجهزة التي لا تسعها الشاشة.
 */
@Composable
internal fun SpoofDeviceRow(
    devices: List<SampleDevice>,
    currentKey: String?,
    enabled: Boolean,
    onPick: (SampleDevice) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        devices.forEach { device ->
            SpoofDeviceCard(device = device, inUse = device.key == currentKey, enabled = enabled) { onPick(device) }
        }
        MoreDevicesCard(enabled = enabled, onClick = onMore)
    }
}

@Composable
private fun SpoofDeviceCard(device: SampleDevice, inUse: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(MaxRadius.tile)
    val border = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(CardWidth)
            .heightIn(min = CardMinHeight)
            .clip(shape)
            .border(if (inUse) MaxSize.activeRing else MaxSize.hairlineBorder, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Text(device.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        val release = device.androidRelease
        Text(if (release != null) stringResource(R.string.sample_row_sub, release, device.model)
            else stringResource(R.string.sample_row_sub_plain, device.model),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (inUse) {
            Text(stringResource(R.string.spoof_device_in_use), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        if (device.fingerprintShared) {
            Text(stringResource(R.string.sample_fp_shared), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MoreDevicesCard(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(MaxRadius.tile)
    Column(
        modifier = Modifier
            .width(CardWidth)
            .heightIn(min = CardMinHeight)
            .clip(shape)
            .border(MaxSize.hairlineBorder, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.spoof_device_more), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = MaxSpace.xs))
    }
}
