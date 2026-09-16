/*
 * MaxManager Design Language — compact layout switch.
 *
 * Used by page top bars when the same content can be presented two ways (the
 * Control screen's grouped list vs its cards). Both names stay visible, so the
 * control never depends on an icon being understood, and it is a radio group
 * rather than a single flip: "which layout am I in?" is answerable from the
 * control itself.
 *
 * Hand-built for the same reason as [MaxSegmented] — it must carry
 * MaxManager's shape/tone language inside a top-bar action row without pulling
 * the experimental Material segmented API into every screen.
 */
package nd.max.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

/**
 * Two-option layout switch sized for a top bar.
 *
 * @param labels exactly the two names the user chooses between.
 * @param selectedIndex index of the active option.
 */
@Composable
fun MaxViewToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.size < 2) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.control),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
        )
    ) {
        Row(
            modifier = Modifier.padding(MaxSpace.xs).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        )
                        .heightIn(min = MaxSize.minTouchTarget),
                    shape = RoundedCornerShape(MaxRadius.control),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.toneContainerStrong)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = MaxSpace.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
