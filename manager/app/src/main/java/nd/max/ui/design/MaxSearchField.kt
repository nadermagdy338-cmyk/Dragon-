/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — search field.
 *
 * Why this is a primitive and not per-screen `OutlinedTextField` calls:
 * Max AI's journal, the app list, the activity launcher and the language
 * picker all answer the same interaction — "narrow this list by typing" — and
 * each of them had grown a slightly different box: different shape, different
 * border, no clear affordance, no label for screen readers on the clear
 * button. A search box is the single most-used control on a long list, so it
 * belongs to the language like MaxRow and MaxSegmented do.
 *
 * Contract enforced here so no screen can regress it:
 *  - the field is single-line; search text never wraps
 *  - a clear affordance appears only when there is something to clear, and it
 *    carries a content description (an icon-only button with no label is
 *    invisible to a screen reader)
 *  - shape and container colour come from tokens, so it reads as part of the
 *    group it filters rather than as a floating Material default
 *  - filtering itself stays in the caller: this primitive never owns the query
 */
package nd.max.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single-line filter box for a list on the page.
 *
 * @param value current query — owned and persisted by the caller.
 * @param placeholder localized hint; must say what is being searched, not
 *        "Search…" on its own.
 * @param clearContentDescription localized label for the clear button. Pass
 *        null only when the caller renders its own clear affordance.
 */
@Composable
fun MaxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Search,
    clearContentDescription: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(MaxRadius.row),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = clearContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = MaxAlpha.border
            ),
        ),
    )
}
