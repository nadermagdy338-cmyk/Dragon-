/*
 * MaxManager Design Language — contextual help and inline explanations.
 *
 * Product rule enforced by this file:
 *   a screen never spends permanent vertical space on a question/answer card.
 *
 * • If the copy answers "what is this page for?" it belongs behind the top-bar
 *   help action ([MaxHelpAction]) — mirrored to the start/end edge automatically
 *   because the top bar itself is RTL-aware.
 * • If the copy is a short description, it renders as calm bulleted lines
 *   ([MaxBullets]) instead of a large card.
 */
package nd.max.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import nd.max.ui.component.MaxScreenHelpDialog

/**
 * Top-bar help affordance.
 *
 * Pass it through `MaxScreen(actions = { MaxHelpAction(...) })` so the question
 * mark sits on the trailing edge of the title bar and the explanation opens on
 * demand.
 */
@Composable
fun MaxHelpAction(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    IconButton(onClick = { visible = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    MaxScreenHelpDialog(
        visible = visible,
        title = title,
        description = body,
        onDismiss = { visible = false },
    )
}

/** One descriptive line: a tone dot plus copy. Never a card. */
@Composable
fun MaxBullet(
    text: String,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Accent,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = tone.content(),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Stack of [MaxBullet] lines used where a description card used to sit. */
@Composable
fun MaxBullets(
    lines: List<String>,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Accent,
) {
    if (lines.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        lines.forEach { line -> MaxBullet(text = line, tone = tone) }
    }
}
