/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — command menu.
 *
 * Why this is a primitive and not a per-screen `DropdownMenu`:
 * MaxViewMenu already covers "choose one of a few names" (a radio group). What
 * was missing is the other half of every dense toolbar — a list of one-shot
 * commands and toggles behind a single trigger. The file manager's screen menu,
 * its selection bar and its quick-locations list all need exactly that, and each
 * of them would otherwise have grown its own trigger, its own check mark and its
 * own idea of where a destructive command goes.
 *
 * Contract enforced here:
 *  - a command is always a **named** row: an icon-only menu is invisible to
 *    anyone who does not already know what the icon means
 *  - toggles and radio members show a check, so "which one is on" is answered
 *    inside the menu rather than by remembering what the trigger looked like
 *  - a destructive command is coloured and icon-marked from the theme's error
 *    role — tone is never the only carrier, so it also keeps its own name
 *  - an empty command list renders **nothing**: a trigger that opens an empty
 *    menu is decoration pretending to be a control
 */
package nd.max.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One command in a [MaxCommandMenu].
 *
 * @param label the command's own name; the row is always readable as text.
 * @param onSelect what happens when it is chosen.
 * @param icon optional leading glyph, for scanning only — never the sole carrier.
 * @param active `true` marks a toggle that is currently on, or the selected
 *        member of a small radio group (the screen's pane arrangement).
 * @param destructive names a consequence the user cannot take back.
 */
data class MaxCommand(
    val label: String,
    val onSelect: () -> Unit,
    val icon: ImageVector? = null,
    val active: Boolean = false,
    val destructive: Boolean = false,
)

/**
 * Single trigger that opens a list of named commands.
 *
 * @param commands the commands, in the order they are shown. An empty list
 *        renders no trigger at all.
 * @param contentDescription spoken name of the closed control.
 * @param triggerIcon glyph for the closed control.
 * @param triggerTint colour of that glyph — a caller may tint it when the menu
 *        itself carries a state (for example: hidden files are visible).
 */
@Composable
fun MaxCommandMenu(
    commands: List<MaxCommand>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    triggerIcon: ImageVector = Icons.Rounded.MoreVert,
    triggerTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (commands.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = triggerIcon,
                contentDescription = contentDescription,
                tint = triggerTint,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            commands.forEach { command ->
                val contentColor = if (command.destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = command.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                        )
                    },
                    leadingIcon = command.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (command.destructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    trailingIcon = if (command.active) {
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
                        expanded = false
                        command.onSelect()
                    },
                )
            }
        }
    }
}
