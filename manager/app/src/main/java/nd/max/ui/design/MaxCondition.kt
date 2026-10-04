/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — condition (state) system.
 *
 * Problem this solves:
 * screens previously showed either nothing, a spinner, or a generic failure
 * string when the kernel/root/sysfs layer could not serve them. A performance
 * tool that says "Something went wrong" is useless — the user cannot tell an
 * unsupported SoC from a denied root request from a stale daemon.
 *
 * So the state vocabulary is a type, not a convention: every non-ready state
 * must name a [MaxConditionKind] AND carry a concrete `detail` describing the
 * real cause. There is no constructor path that produces a vague message.
 */
package nd.max.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nd.max.ui.component.StudioTextButton
import nd.max.ui.component.StudioTonalButton
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * The complete set of non-ready situations a MaxManager surface can be in.
 * `Ready` is deliberately absent: ready means "render your content", which is
 * represented by a null condition.
 */
enum class MaxConditionKind {
    /** First read in flight, nothing to show yet. */
    Loading,

    /** The read succeeded and legitimately returned nothing. */
    Empty,

    /** The read failed for a reason we can describe. */
    Error,

    /** This device / kernel / SoC does not expose the feature at all. */
    Unsupported,

    /** Root is required and has not been granted. */
    RootRequired,

    /** An Android runtime or special permission is missing. */
    PermissionRequired,

    /** The backing service/daemon/module is installed but not reachable. */
    Disconnected,

    /** The source exists but has no usable value right now. */
    Unavailable,

    /** A write is in flight. */
    Applying,

    /** A write completed and was verified. */
    Applied,

    /** A write was rejected by the kernel or reverted. */
    Failed
}

/**
 * A described condition.
 *
 * @param title short, human, screen-specific ("GPU frequency control").
 * @param detail the real cause, in the user's language. Required.
 * @param technicalDetail optional machine truth (sysfs path, exit code, errno)
 *        rendered in mono — this is what makes the app credible to power users
 *        without polluting the main message for everyone else.
 */
@Immutable
data class MaxCondition(
    val kind: MaxConditionKind,
    val title: String,
    val detail: String,
    val technicalDetail: String? = null,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null
)

val MaxConditionKind.tone: MaxTone
    get() = when (this) {
        MaxConditionKind.Loading -> MaxTone.Neutral
        MaxConditionKind.Empty -> MaxTone.Neutral
        MaxConditionKind.Error -> MaxTone.Critical
        MaxConditionKind.Unsupported -> MaxTone.Inactive
        MaxConditionKind.RootRequired -> MaxTone.Caution
        MaxConditionKind.PermissionRequired -> MaxTone.Caution
        MaxConditionKind.Disconnected -> MaxTone.Caution
        MaxConditionKind.Unavailable -> MaxTone.Inactive
        MaxConditionKind.Applying -> MaxTone.Accent
        MaxConditionKind.Applied -> MaxTone.Positive
        MaxConditionKind.Failed -> MaxTone.Critical
    }

val MaxConditionKind.icon: ImageVector
    get() = when (this) {
        MaxConditionKind.Loading -> Icons.Rounded.HourglassTop
        MaxConditionKind.Empty -> Icons.Rounded.SearchOff
        MaxConditionKind.Error -> Icons.Rounded.ErrorOutline
        MaxConditionKind.Unsupported -> Icons.Rounded.Block
        MaxConditionKind.RootRequired -> Icons.Rounded.Lock
        MaxConditionKind.PermissionRequired -> Icons.Rounded.Security
        MaxConditionKind.Disconnected -> Icons.Rounded.LinkOff
        MaxConditionKind.Unavailable -> Icons.Rounded.RemoveCircleOutline
        MaxConditionKind.Applying -> Icons.Rounded.HourglassTop
        MaxConditionKind.Applied -> Icons.Rounded.CheckCircle
        MaxConditionKind.Failed -> Icons.Rounded.Cancel
    }

/** True while work is in flight — these render a progress indicator, not a glyph. */
val MaxConditionKind.isBusy: Boolean
    get() = this == MaxConditionKind.Loading || this == MaxConditionKind.Applying

/** Transient confirmations should not occupy a whole screen. */
val MaxConditionKind.isTransient: Boolean
    get() = this == MaxConditionKind.Applying || this == MaxConditionKind.Applied

/**
 * Full-block presentation: used when the condition replaces the content of a
 * screen or a whole section.
 */
@Composable
fun MaxConditionPanel(
    condition: MaxCondition,
    modifier: Modifier = Modifier
) {
    val tone = condition.kind.tone
    val toneContent = tone.content()

    // **ترحيل إلى القشرة:** الشكل والخلفية والحدّ من العقد. والحدّ يُمرّر لأنّ لوح الحالة
    // يلوّنه بنبرته (`tone.border()`) — وهو تمييز مقصود لا انزياح، فلا يُسوّى مع البطاقة العاديّة.
    // والحشو هنا **متباين** (أفقيّ `xl` ورأسيّ `xxl`) ولا تعبّر عنه معلمة واحدة، فيبقى داخليًّا.
    MaxCardShell(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = MaxSize.readingMaxWidth)
            .semantics { liveRegion = LiveRegionMode.Polite },
        borderColor = tone.border(),
        contentPadding = 0.dp,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaxSpace.xl, vertical = MaxSpace.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Surface(
                shape = RoundedCornerShape(MaxRadius.row),
                color = tone.container()
            ) {
                if (condition.kind.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(MaxSpace.md)
                            .size(MaxSize.iconGlyph),
                        strokeWidth = 2.dp,
                        color = toneContent
                    )
                } else {
                    Icon(
                        imageVector = condition.kind.icon,
                        contentDescription = null,
                        tint = toneContent,
                        modifier = Modifier
                            .padding(MaxSpace.md)
                            .size(MaxSize.iconGlyph)
                    )
                }
            }

            Text(
                text = condition.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (condition.detail.isNotBlank()) {
                Text(
                    text = condition.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            condition.technicalDetail?.takeIf { it.isNotBlank() }?.let { technical ->
                Surface(
                    shape = RoundedCornerShape(MaxRadius.control),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = technical,
                        style = MonoValueStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaxSpace.md,
                            vertical = MaxSpace.sm
                        )
                    )
                }
            }

            MaxConditionActions(condition)
        }
    }
}

/**
 * Inline presentation: a single row that sits above or inside a section when
 * the rest of the screen is still usable.
 */
@Composable
fun MaxConditionNotice(
    condition: MaxCondition,
    modifier: Modifier = Modifier
) {
    val tone = condition.kind.tone
    val toneContent = tone.content()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(MaxRadius.row),
        color = tone.container(),
        border = BorderStroke(MaxSize.hairlineBorder, tone.border())
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MaxSpace.md,
                vertical = MaxSpace.md
            ),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
            ) {
                if (condition.kind.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MaxSize.iconGlyphSmall),
                        strokeWidth = 2.dp,
                        color = toneContent
                    )
                } else {
                    Icon(
                        imageVector = condition.kind.icon,
                        contentDescription = null,
                        tint = toneContent,
                        modifier = Modifier.size(MaxSize.iconGlyph)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                    Text(
                        text = condition.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = toneContent
                    )
                    if (condition.detail.isNotBlank()) {
                        Text(
                            text = condition.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    condition.technicalDetail?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MonoValueStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            MaxConditionActions(condition)
        }
    }
}

@Composable
private fun MaxConditionActions(condition: MaxCondition) {
    val primary = condition.primaryActionLabel
    val onPrimary = condition.onPrimaryAction
    val secondary = condition.secondaryActionLabel
    val onSecondary = condition.onSecondaryAction
    if ((primary == null || onPrimary == null) && (secondary == null || onSecondary == null)) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (primary != null && onPrimary != null) {
            StudioTonalButton(onClick = onPrimary) { Text(primary) }
        }
        if (secondary != null && onSecondary != null) {
            StudioTextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

/**
 * Renders [content] when [condition] is null, otherwise renders the condition.
 * Transient conditions (Applying/Applied) never hide content — they are shown
 * by the caller as a notice instead, because hiding controls mid-write is what
 * made the old screens feel unstable.
 */
@Composable
fun MaxConditionGate(
    condition: MaxCondition?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = condition?.kind,
        transitionSpec = {
            fadeIn(tween(MaxDuration.standard)) togetherWith fadeOut(tween(MaxDuration.quick))
        },
        modifier = modifier,
        label = "max_condition_gate"
    ) { kind ->
        if (kind == null || condition == null) {
            content()
        } else {
            MaxConditionPanel(condition)
        }
    }
}
