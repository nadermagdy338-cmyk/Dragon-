/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * The command menu a long press opens, anchored at the finger.
 *
 * Why not [MaxCommandMenu] (the `⋮` menu): that one belongs to a control — it appears
 * under its trigger and says "the same as the button". This one belongs to an **entry**:
 * it opens where the user pressed, on the row they pressed, and only then do its
 * commands have a subject. MT Manager works exactly this way, and it is the difference
 * between "copy something" and "copy this file".
 *
 * Placement is decided here, not by the caller: the menu is clamped inside
 * [container] so a press near the right edge does not open a menu half off-screen, and a
 * press in the lower part of the list opens it **above** the finger instead of under it.
 * The height estimate uses the same row constant the rows are drawn with, so the clamp
 * cannot drift from the layout.
 */
@Composable
fun MaxContextMenu(
    visible: Boolean,
    anchor: Offset,
    commands: List<MaxCommand>,
    onDismiss: () -> Unit,
    container: IntSize,
    modifier: Modifier = Modifier,
) {
    if (!visible || commands.isEmpty()) return

    val density = LocalDensity.current
    val menuWidthPx = with(density) { MENU_WIDTH.roundToPx() }
    val rowHeightPx = with(density) { MENU_ROW_HEIGHT.roundToPx() }
    val estimatedHeight = rowHeightPx * commands.size.coerceAtMost(MENU_ESTIMATED_ROWS)

    val x = anchor.x.toInt().coerceIn(0, (container.width - menuWidthPx).coerceAtLeast(0))
    val below = anchor.y.toInt()
    val y = if (container.height > 0 && below + estimatedHeight > container.height) {
        (below - estimatedHeight).coerceAtLeast(0)
    } else {
        below
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier
                .width(MENU_WIDTH)
                .heightIn(max = MENU_MAX_HEIGHT),
            shape = RoundedCornerShape(MaxRadius.group),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                commands.forEach { command ->
                    CommandRow(command = command, onDismiss = onDismiss)
                }
            }
        }
    }
}

/**
 * One command row. The whole row is the tap target — a menu of named commands is not a
 * place for pixel-hunting. Dismiss first, then act, so a command that opens a dialog is
 * not fighting the popup's own dismissal for the same frame.
 */
@Composable
private fun CommandRow(command: MaxCommand, onDismiss: () -> Unit) {
    val contentColor = if (command.destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MENU_ROW_HEIGHT)
            .clickable {
                onDismiss()
                command.onSelect()
            }
            .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        command.icon?.let { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = if (command.destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(MaxSize.iconGlyph),
            )
        }
        Text(
            text = command.label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (command.active) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
            )
        }
    }
}

private val MENU_WIDTH = 260.dp
private val MENU_MAX_HEIGHT = 420.dp
private val MENU_ROW_HEIGHT = 48.dp
private const val MENU_ESTIMATED_ROWS = 8
