/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.diagnostics.HardwareRouteHealth
import nd.max.core.hardware.AccessLevel
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.theme.MaxTextRole

/**
 * The capability matrix, with each control's **activation verdict** under it.
 *
 * The access level answers "is this interface readable or writable"; the verdict answers the different
 * question the user is actually asking when a control does nothing — "is there a route this build can
 * prove for applying it". Both are shown because they fail differently: a control can be perfectly
 * writable and still have no verified route that survives a vendor daemon, and it can be readable with
 * no route at all.
 *
 * Moved out of `DiagnosticsScreen` on its own merits (a screen should not own a 55-line card) and
 * because keeping it inline had pushed that file past the project's file-size ceiling.
 */
@Composable
fun CapabilityMatrixCard(
    snapshot: HardwareCapabilitySnapshot?,
    routes: List<HardwareRouteHealth.Verdict>,
    onRefresh: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.diagnostics_capability_matrix),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    snapshot?.let { "${it.vendor} • ${it.platform}" }
                        ?: stringResource(R.string.diagnostics_detecting_interfaces),
                    style = MaxTextRole.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StudioTextButton(onClick = onRefresh) { Text(stringResource(R.string.diagnostics_refresh)) }
        }
        Spacer(Modifier.height(10.dp))
        snapshot?.features?.values?.forEach { capability ->
            val label = capability.feature.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            val status = when (capability.access) {
                AccessLevel.READ_WRITE -> stringResource(R.string.diagnostics_access_read_write)
                AccessLevel.READ_ONLY -> stringResource(R.string.diagnostics_access_read_only)
                AccessLevel.NONE -> stringResource(R.string.diagnostics_access_none)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${capability.backend} • $status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RouteVerdictLabel(routes, capability.feature)
                    if (capability.evidence.isNotEmpty()) {
                        Text(
                            capability.evidence.take(3).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.diagnostics_capability_matrix_hint),
            style = MaxTextRole.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        // **والباب في آخر البطاقة لا في صفّ عنوانها:** صفُّ العنوان يحمل عنوانًا ووصفًا وزرّ
        // تحديث، فضغطةٌ ثالثة فيه تزاحم القراءة؛ وهنا يُقرأ **إجراءً على البطاقة** بعد أن
        // تُقرأ بياناتها. وهو سطر رابط لا كبسولة (`MaxDeviceInfoShortcut`)، ويُمرَّر له صفر
        // حاشية لأن هذه البطاقة تحشو نفسها أصلًا.
        trailing?.invoke()
    }
}
