/*
 * MaxManager Design Language — layout switch.
 *
 * Used by page top bars when the same content can be presented two ways (the
 * Control page's compact list and its expanded page). It is one top-bar icon that
 * opens the two names as a menu instead of two always-visible chips: the top bar
 * already carries the page's help action and its side actions, and a two-chip
 * control big enough to touch (48dp per option) crowded them on phones while the
 * name it showed was almost always the one you are not in.
 *
 * The menu is a radio group — the active name carries the check — and both names
 * stay readable when opened, so the control never depends on an icon being
 * understood. The trigger shows the mode you are in, which is the one fact the
 * collapsed chip could not tell you anyway when it showed two names at once.
 */
package nd.max.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The two presentations this control switches between, in the order callers pass
 * their labels: `[0]` compact (everything behind its hub) and `[1]` expanded
 * (every screen opened out).
 */
private val MaxViewIcons: List<ImageVector> = listOf(
    Icons.AutoMirrored.Rounded.ViewList,
    Icons.Rounded.UnfoldMore,
)

/**
 * Top-bar layout switch.
 *
 * @param labels exactly the two names the user chooses between.
 * @param selectedIndex index of the active option.
 * @param contentDescription spoken name of the control; the menu items carry the
 *        names themselves.
 */
@Composable
fun MaxViewMenu(
    labels: List<String>,
    selectedIndex: Int,
    contentDescription: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.size < 2) return

    var expanded by remember { mutableStateOf(false) }
    val selected = selectedIndex.coerceIn(0, labels.lastIndex)

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MaxViewIcons[selected],
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        Icon(
                            imageVector = MaxViewIcons[index],
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
