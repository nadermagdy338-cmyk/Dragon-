package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.HelpOutline
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxUiMetrics.cardRadius),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
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
        icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

