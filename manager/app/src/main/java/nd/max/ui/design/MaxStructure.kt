/*
 * MaxManager Design Language — page structure.
 *
 * The old UI expressed every idea as a rounded Card, so nothing had priority:
 * a critical thermal reading and a link to the changelog looked identical.
 *
 * The new structure has exactly three levels:
 *   1. Section  — titled band of the page, separated by whitespace, not boxes.
 *   2. Group    — one hairline container holding related rows (settings, specs).
 *   3. Row      — a single fact or a single action, 48dp minimum.
 * Elevation is not used to group things; borders and space are. Elevation is
 * reserved for things that float above the page (sheets, dialogs, snackbars).
 */
package nd.max.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A titled band of a page.
 *
 * @param title short noun phrase. Rendered as an accessibility heading so
 *        screen-reader users can jump between sections.
 * @param description one line of "why this exists", shown only when it adds
 *        information the title cannot carry.
 * @param trailing optional section-level action (e.g. "Reset").
 */
@Composable
fun MaxSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
        content()
    }
}

/**
 * Hairline container for related rows. One border around many rows instead of
 * one card per row: fewer edges, faster scanning, less vertical waste.
 */
@Composable
fun MaxGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = MaxSpace.groupPadding), content = content)
    }
}

/** Separator between rows of the same group. Inset to align with row text. */
@Composable
fun MaxGroupDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    HorizontalDivider(
        modifier = modifier.padding(
            start = if (inset) MaxSpace.rowPaddingHorizontal else 0.dp,
            end = if (inset) MaxSpace.rowPaddingHorizontal else 0.dp
        ),
        thickness = MaxSize.hairlineBorder,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
    )
}

/**
 * One row: a fact, a setting, or a destination.
 *
 * Accessibility contract enforced here so no screen can regress it:
 * - minimum 48dp height
 * - clickable rows expose Role.Button
 * - the icon is decorative; the text carries the meaning
 */
@Composable
fun MaxRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: MaxTone = MaxTone.Neutral,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val clickModifier = if (onClick != null && enabled) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    val contentAlpha = if (enabled) 1f else MaxAlpha.disabledContent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = MaxSize.minTouchTarget)
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(MaxRadius.control),
                color = iconTone.container(),
                modifier = Modifier.size(MaxSize.rowIconContainer)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTone.content().copy(alpha = contentAlpha),
                        modifier = Modifier.size(MaxSize.iconGlyphSmall)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
        ) {
            // `LineBreak.Heading` keeps a long single word from being split into
            // individual letters when the row is narrow; the overflow guard then
            // truncates it instead. See MaxControlRows.kt for the full note.
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Segmented control for 2–4 mutually exclusive, instantly-applied choices
 * (profile, scope, time window).
 *
 * Hand-built rather than using the experimental Material segmented button so
 * it can carry MaxManager's shape/tone language and correct selection
 * semantics without pulling an unstable API into 40 screens.
 */
@Composable
fun MaxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.row),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
        )
    ) {
        Row(
            modifier = Modifier.padding(MaxSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val background = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.toneContainerStrong)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                val labelColor = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = MaxAlpha.disabledContent)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        )
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(MaxRadius.control),
                    color = background
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = labelColor,
                            modifier = Modifier.padding(
                                horizontal = MaxSpace.sm,
                                vertical = MaxSpace.sm
                            )
                        )
                    }
                }
            }
        }
    }
}
