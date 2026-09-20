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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** One tab of a [MaxTabbedDialog]: its name, and an optional glyph for scanning only. */
data class MaxDialogTab(val label: String, val icon: ImageVector? = null)

/**
 * Dialog whose body is a set of named tabs.
 *
 * Why it exists: the file manager's properties window answers four different questions
 * about one entry (what is it, what is inside it if it is a package, who may touch it,
 * who owns it). Four separate dialogs would mean four opens and no way to compare them,
 * and a single long body would put the permissions the user came for below the fold.
 * The tab strip is chips rather than an underline row on purpose: a chip says "this is a
 * choice", is readable when the labels are long translations, and scrolls horizontally
 * instead of clipping.
 *
 * The body is height-capped and scrollable, so a package with two hundred requested
 * permissions cannot grow the dialog past the screen it is describing.
 */
@Composable
fun MaxTabbedDialog(
    visible: Boolean,
    title: String,
    tabs: List<MaxDialogTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    bodyMaxHeight: Dp = MaxSize.dialogListMax,
    body: @Composable (Int) -> Unit,
) {
    if (!visible) return

    val safeIndex = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tabs.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = MaxSpace.sm),
                        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            FilterChip(
                                selected = index == safeIndex,
                                onClick = { onSelect(index) },
                                label = { Text(text = tab.label) },
                                leadingIcon = tab.icon?.let { glyph ->
                                    {
                                        Icon(
                                            imageVector = glyph,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = MaxSpace.xs),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                        content = { body(safeIndex) },
                    )
                }
            }
        },
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                TextButton(onClick = onConfirm) {
                    Text(text = confirmLabel, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = dismissLabel) }
        },
    )
}
