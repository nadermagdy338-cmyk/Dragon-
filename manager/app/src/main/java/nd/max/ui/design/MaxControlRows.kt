/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — control rows.
 *
 * Why this file exists: across the app the same control was drawn six
 * different ways (switch inside a card, switch inside a Row with a hand-made
 * 12dp padding, switch with the label as a clickable Text, slider with the
 * value rendered in body text, slider with no value at all). That is the main
 * reason the app reads as a pile of screens instead of one product.
 *
 * Rules encoded here, not left to each screen:
 *  - The WHOLE row is the touch target, never just the switch.
 *  - A disabled control must say WHY it is disabled. `lockedReason` is not
 *    decoration: if a control is off-limits (no root, unsupported kernel node,
 *    charging), the reason is rendered and spoken, instead of a grey widget.
 *  - Numeric state is rendered in the mono value style so values line up and
 *    can be compared vertically — this is a performance tool, columns matter.
 *  - Semantics use the correct role (Switch / RadioButton / Button) with a
 *    state description, so TalkBack does not just say "button".
 */
package nd.max.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import nd.max.ui.component.MaxSlider
import nd.max.ui.component.MaxSwitch
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * Shared row body. Private on purpose: screens should use the semantic rows
 * below, never assemble their own control row again.
 */
@Composable
private fun MaxControlRowLayout(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    lockedReason: String? = null,
    icon: ImageVector? = null,
    iconTone: MaxTone = MaxTone.Neutral,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = MaxAlpha.disabledContent)
    }
    val supportingColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaxAlpha.disabledContent)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaxSize.minTouchTarget)
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(MaxSize.rowIconContainer)
                        .clip(RoundedCornerShape(MaxRadius.control))
                        .background(iconTone.container()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) iconTone.content() else supportingColor,
                        modifier = Modifier.size(MaxSize.iconGlyphSmall)
                    )
                }
                Spacer(Modifier.width(MaxSpace.md))
            }

            Column(modifier = Modifier.weight(1f)) {
                // `LineBreak.Heading` and the overflow guard together are what stop
                // a long single word from being chopped into letters. `maxLines`
                // alone does NOT: the greedy breaker will happily split a word that
                // is wider than the line, and only then ellipsize the tail — which
                // is how a language name came out as one letter per line.
                Text(
                    text = title,
                    // `LineBreak.Heading` is set on the style, not on Text: it is what
                    // stops a long single word from being chopped into letters.
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                        color = supportingColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(MaxSpace.md))
                trailing()
            }
        }

        // The "why is this greyed out" line. Always icon + text, never colour
        // alone, and it survives font scaling because it wraps.
        if (!enabled && !lockedReason.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaxTone.Caution.content(),
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
                Spacer(Modifier.width(MaxSpace.sm))
                Text(
                    text = lockedReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxTone.Caution.content(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        below?.invoke()
    }
}

/** Boolean control. Whole row toggles; the switch itself is decorative. */
@Composable
fun MaxSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: MaxTone = MaxTone.Neutral,
    enabled: Boolean = true,
    lockedReason: String? = null,
    onStateDescription: String? = null,
    offStateDescription: String? = null
) {
    val stateText = if (checked) onStateDescription else offStateDescription
    MaxControlRowLayout(
        title = title,
        modifier = modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .then(
                if (stateText != null) {
                    Modifier.semantics { stateDescription = stateText }
                } else {
                    Modifier
                }
            ),
        subtitle = subtitle,
        lockedReason = lockedReason,
        icon = icon,
        iconTone = iconTone,
        enabled = enabled,
        trailing = {
            MaxSwitch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}

/**
 * Numeric control.
 *
 * The current value is always visible as text next to the label — a slider
 * alone is unreadable for frequencies, thresholds and timeouts, which is
 * exactly what this app tunes.
 */
@Composable
fun MaxSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    subtitle: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    lockedReason: String? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    MaxControlRowLayout(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        lockedReason = lockedReason,
        enabled = enabled,
        trailing = if (valueText != null) {
            {
                Text(
                    text = valueText,
                    style = MonoValueStyleSmall.copy(lineBreak = LineBreak.Heading),
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = MaxAlpha.disabledContent)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            null
        },
        below = {
            MaxSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished
            )
        }
    )
}

/**
 * Row that opens something else. Shows the CURRENT value, so the user does not
 * have to navigate in just to find out what is configured.
 */
@Composable
fun MaxNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueText: String? = null,
    icon: ImageVector? = null,
    iconTone: MaxTone = MaxTone.Neutral,
    enabled: Boolean = true,
    lockedReason: String? = null
) {
    MaxControlRowLayout(
        title = title,
        modifier = modifier.then(
            if (enabled) {
                Modifier.selectable(
                    selected = false,
                    enabled = true,
                    role = Role.Button,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        ),
        subtitle = subtitle,
        lockedReason = lockedReason,
        icon = icon,
        iconTone = iconTone,
        enabled = enabled,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!valueText.isNullOrBlank()) {
                    Text(
                        text = valueText,
                        style = MonoValueStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(MaxSpace.sm))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(MaxSize.iconGlyph)
                )
            }
        }
    )
}

/** One option inside a mutually exclusive group (governor, profile, mode). */
@Composable
fun MaxChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    lockedReason: String? = null
) {
    MaxControlRowLayout(
        title = title,
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onSelect
        ),
        subtitle = subtitle,
        lockedReason = lockedReason,
        enabled = enabled,
        trailing = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        }
    )
}
