package nd.max.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn

/** Shared visual language for transient, empty and recoverable screen states. */
@Composable
fun MaxContentState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    MaxSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .maxStateContentWidth(),
        accent = accent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(18.dp),
                color = accent.copy(alpha = 0.12f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(14.dp).size(28.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.maxHeadingSemantics(),
                textAlign = TextAlign.Center
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                nd.max.ui.component.StudioButton(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                nd.max.ui.component.StudioOutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
            }
        }
    }
}

@Composable
fun MaxLoadingState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        MaxContentState(title, message, Icons.Rounded.HourglassTop, modifier)
    }
}

@Composable
fun MaxEmptyState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    MaxContentState(title, message, Icons.Rounded.SearchOff, modifier, actionLabel, onAction)
}

@Composable
fun MaxErrorState(title: String, message: String, retryLabel: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MaxContentState(title, message, Icons.Rounded.ErrorOutline, modifier, retryLabel, onRetry)
}

@Composable
fun MaxStateTransition(targetState: Any, content: @Composable () -> Unit) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "maxContentState"
    ) { content() }
}


private fun Modifier.maxStateContentWidth(): Modifier = widthIn(max = 520.dp)
