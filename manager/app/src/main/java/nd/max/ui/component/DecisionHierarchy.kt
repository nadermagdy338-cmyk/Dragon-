/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxCardShell

/**
 * A compact decision-oriented summary: state first, consequence second, action
 * guidance last. It is intentionally non-interactive so it cannot compete with
 * the actual controls below it.
 */
@Composable
fun MaxDecisionCard(
    state: String,
    guidance: String,
    modifier: Modifier = Modifier,
    title: String = "CURRENT STATE",
    icon: ImageVector = Icons.Outlined.Info,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val scheme = MaterialTheme.colorScheme
    // **ترحيل إلى القشرة:** الحدّ يُمرّر بلونه الحالي حرفيًّا — و`0.16f` هي `MaxAlpha.border`
    // بعينها، فالرقم الحرفي كان اسمًا مفقودًا لا قيمة مخترعة. والترحيل يُوحّد الشكل
    // والخلفية والقصّ ولا يغيّر شدة الحدّ.
    MaxCardShell(
        modifier = modifier.fillMaxWidth(),
        borderColor = accent.copy(alpha = MaxAlpha.border),
        contentPadding = 0.dp,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.padding(MaxUiMetrics.cardPadding),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(MaxUiMetrics.smallRadius),
                color = accent.copy(alpha = 0.11f)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp).size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(state, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(guidance, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
/**
 * Compact contextual help shown from a screen's top-bar help action.
 * Page explanations live here instead of occupying permanent vertical space.
 */
@Composable
fun MaxScreenHelpDialog(
    visible: Boolean,
    title: String,
    description: String,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

