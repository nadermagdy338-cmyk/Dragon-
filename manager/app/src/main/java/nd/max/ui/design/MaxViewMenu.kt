/*
 * MaxManager Design Language — single-choice menu.
 *
 * Used by page top bars when one page-level choice must be made between a few
 * names: the Control page's compact/expanded layout, the file manager's sort
 * order, and the file manager's pane arrangement. It is one top-bar icon that
 * opens the names instead of one always-visible chip per option: the top bar
 * already carries the page's help action and its side actions, and a chip per
 * option big enough to touch (48dp each) crowded them on phones while the name
 * it showed was almost always the one you were not in.
 *
 * The menu is a radio group — the active name carries the check — and every name
 * stays readable when opened, so the control never depends on an icon being
 * understood. An option may carry an icon for faster scanning, but **icons are
 * optional per option and are always looked up by index with a fallback**: this
 * primitive is handed as many labels as the caller has choices, and a caller
 * with four choices once crashed the screen the moment the menu opened, because
 * the trigger and the item icons indexed a two-entry list directly. Nothing here
 * indexes an icon list without a bound.
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
 * Icons used when a caller names two layout options and passes no icons of its
 * own: `[0]` compact (everything behind its hub) and `[1]` expanded (every
 * screen opened out).
 */
private val MaxViewIcons: List<ImageVector> = listOf(
    Icons.AutoMirrored.Rounded.ViewList,
    Icons.Rounded.UnfoldMore,
)

/**
 * The icon drawn on the trigger when neither the caller's trigger icon nor a
 * per-option icon exists for the active index. It exists so a menu can never
 * render without a face — the alternative is an invisible control.
 */
private val MaxViewFallbackIcon: ImageVector = Icons.AutoMirrored.Rounded.ViewList

/**
 * Single-choice menu for a page-level switch.
 *
 * @param labels the names the user chooses between, in order.
 * @param selectedIndex index of the active option; coerced into range, so a
 *        caller whose state came from a saved string cannot crash the menu.
 * @param contentDescription spoken name of the control; the menu items carry
 *        the names themselves.
 * @param icons optional icon per option, **looked up by index**: shorter lists
 *        are allowed and simply leave the later options without an icon.
 * @param triggerIcon icon for the closed control; `null` means "the active
 *        option's icon", falling back to [MaxViewFallbackIcon].
 */
@Composable
fun MaxViewMenu(
    labels: List<String>,
    selectedIndex: Int,
    contentDescription: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector> = MaxViewIcons,
    triggerIcon: ImageVector? = null,
) {
    if (labels.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val selected = selectedIndex.coerceIn(0, labels.lastIndex)
    val face = triggerIcon ?: icons.getOrNull(selected) ?: MaxViewFallbackIcon

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = face,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val iconTint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                // `null` حين لا أيقونة لهذا الخيار: اسم بلا رمز خير من رمز لا يعني شيئًا.
                // والشكل نفسه المستعمل في `trailingIcon` أدناه، فلا يُخترع نمط ثانٍ لنفس الغرض.
                val optionIcon = icons.getOrNull(index)
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = if (optionIcon == null) {
                        null
                    } else {
                        {
                            Icon(
                                imageVector = optionIcon,
                                contentDescription = null,
                                tint = iconTint,
                            )
                        }
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
