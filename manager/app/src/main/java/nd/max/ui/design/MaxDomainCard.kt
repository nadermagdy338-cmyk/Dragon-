package nd.max.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nd.max.ui.navigation.MaxDestination

/**
 * Hub summary row for the Control destination.
 *
 * Carries the hub title, its one-line scope description and a trailing
 * chevron. Live summaries (frequency, temperature, ...) land here in NT-03
 * and must carry [MaxDataTrust]; until then the supporting line is scope
 * copy, never a placeholder number (ADR-07).
 */
@Composable
fun MaxDomainCard(
    destination: MaxDestination,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaxRow(
        title = stringResource(destination.titleRes),
        subtitle = subtitle,
        icon = destination.icon,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
