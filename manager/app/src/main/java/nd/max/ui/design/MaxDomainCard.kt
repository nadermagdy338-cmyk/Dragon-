package nd.max.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Domain summary row for the Control destination.
 *
 * App-agnostic by contract: it takes primitives, never a navigation type, so
 * [nd.max.ui.design] stays reusable and independently testable (ADR-06).
 * Callers map their own destination model onto these parameters.
 *
 * @param state optional live summary. When absent nothing is rendered - never
 *        a placeholder number (ADR-07).
 * @param trust freshness/source of [state]; rendered as [MaxTrustChip] when
 *        [state] is present.
 */
@Composable
fun MaxDomainCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: String? = null,
    trust: MaxDataTrust? = null,
) {
    MaxRow(
        title = title,
        subtitle = if (state != null) state else subtitle,
        icon = icon,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                if (state != null && trust != null) {
                    MaxTrustChip(trust = trust)
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
