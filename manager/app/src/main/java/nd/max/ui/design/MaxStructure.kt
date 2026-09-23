/*
 * MaxManager Design Language — page structure.
 *
 * The old UI expressed every idea as a rounded Card, so nothing had priority:
 * a critical thermal reading and a link to the changelog looked identical.
 *
 * The structure has exactly three levels:
 *   1. Section  — titled band of the page, separated by whitespace, not boxes.
 *   2. Group    — one hairline container holding related rows (settings, specs).
 *   3. Row      — a single fact or a single action, 48dp minimum.
 * Elevation is not used to group things; borders and space are. Elevation is
 * reserved for things that float above the page (sheets, dialogs, snackbars).
 *
 * **And this file no longer draws its own surfaces.** The page structure used to
 * paint flat containers (`surfaceContainerLow` + a 16% hairline) while the home
 * screen painted tinted, lit, elevated ones — which is exactly why moving from
 * the home into a sub-screen felt like leaving the product. Every surface here
 * now comes from the shared depth layer (`neuralSurface` / `neuralPalette`), so
 * one edit to that language moves all ~27 screens that use these components:
 *
 *   - [MaxSection] renders the kit's section header (accent rail + title + caption),
 *     so section headings are identical on every screen of the app.
 *   - [MaxGroup] is a lit panel: tinted gradient, corner aura from the reading
 *     edge, top sheen, tinted elevation — the same material as `NeuralPanel`.
 *   - [MaxRow]'s icon sits in a gradient chip with a hairline, like `NeuralIconChip`.
 *   - [MaxSegmented] lights its selected option the way the home profile rail does.
 */
package nd.max.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.neuralPalette
import nd.max.ui.component.neuralSurface

/**
 * A titled band of a page.
 *
 * The header itself is the kit's [NeuralSectionHeader] — accent rail, 15sp bold
 * title, 11sp caption, optional trailing action — so a section title looks the
 * same on the home screen, in a settings screen and inside a subsystem. The
 * heading semantics stay here, because jumping between sections is an
 * accessibility feature of the *page*, not of the header's geometry.
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
        Box(Modifier.fillMaxWidth().semantics { heading() }) {
            NeuralSectionHeader(
                title = title,
                caption = description?.takeIf { it.isNotBlank() },
                accent = MaterialTheme.colorScheme.primary,
                trailing = trailing,
            )
        }
        content()
    }
}

/**
 * Hairline container for related rows. One surface around many rows instead of
 * one card per row: fewer edges, faster scanning, less vertical waste.
 *
 * The container is a **lit surface**, not a flat fill: the same tinted gradient,
 * reading-edge aura, top sheen and tinted elevation the home screen's panels
 * carry ([neuralSurface]). The aura is held at 35% here — a group frames rows,
 * it is not a destination, so it must not glow like the hero band.
 */
@Composable
fun MaxGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = neuralPalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neuralSurface(
                shape = RoundedCornerShape(MaxRadius.group),
                top = p.panelTop.copy(alpha = .94f),
                bottom = p.panel,
                border = p.border,
                glow = p.accent,
                elevation = 3.dp,
                glowStrength = .35f,
                rtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
            )
            .padding(vertical = MaxSpace.groupPadding),
        content = content
    )
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
 * The icon container, shared by [MaxRow] and the control rows in
 * `MaxControlRows.kt`.
 *
 * One implementation on purpose: this chip used to be a flat tone container in
 * rows and a differently-padded flat box in control rows, which is the drift
 * this token layer exists to stop. The gradient plus hairline is the same
 * treatment `NeuralIconChip` gives the home screen's chips, so an icon reads
 * identically wherever it appears.
 */
@Composable
internal fun MaxRowIcon(
    icon: ImageVector,
    tone: MaxTone,
    enabled: Boolean,
    size: Dp = MaxSize.rowIconContainer,
    glyph: Dp = MaxSize.iconGlyphSmall
) {
    val shape = RoundedCornerShape(MaxRadius.control)
    val tint = if (enabled) {
        tone.content()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaxAlpha.disabledContent)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = .26f), tint.copy(alpha = .10f))))
            .border(BorderStroke(MaxSize.hairlineBorder, tint.copy(alpha = .22f)), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(glyph)
        )
    }
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
            MaxRowIcon(icon = icon, tone = iconTone, enabled = enabled)
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
 * semantics without pulling an unstable API into 40 screens. And the selected
 * segment is a **lit key** — animated vertical gradient plus a stronger
 * hairline — exactly like the home profile rail (`NeuralSegmented`), because
 * both are the same control answering the same kind of question.
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
    val p = neuralPalette()
    val tone = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neuralSurface(
                shape = RoundedCornerShape(MaxRadius.row),
                top = p.tile,
                bottom = p.tile,
                border = p.border.copy(alpha = .60f),
                rtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
            )
            .padding(MaxSpace.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val fillTop by animateColorAsState(
                    targetValue = if (selected) tone.copy(alpha = .30f) else Color.Transparent,
                    animationSpec = tween(MaxDuration.quick, easing = FastOutSlowInEasing),
                    label = "max_segmented_fill_top",
                )
                val fillBottom by animateColorAsState(
                    targetValue = if (selected) tone.copy(alpha = .11f) else Color.Transparent,
                    animationSpec = tween(MaxDuration.quick, easing = FastOutSlowInEasing),
                    label = "max_segmented_fill_bottom",
                )
                val labelColor = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = MaxAlpha.disabledContent)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MaxRadius.control))
                        .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
                        .border(
                            BorderStroke(
                                MaxSize.hairlineBorder,
                                if (selected) tone.copy(alpha = .42f) else Color.Transparent
                            ),
                            RoundedCornerShape(MaxRadius.control)
                        )
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        )
                        .heightIn(min = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
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
