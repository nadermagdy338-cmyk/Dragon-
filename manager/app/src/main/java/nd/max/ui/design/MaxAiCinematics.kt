/*
 * MaxManager Design Language — causal / cinematic primitives.
 *
 * Why this file exists:
 * the Max AI surface has to narrate a real control loop (state → observation →
 * decision → write → re-measurement → verdict → learning). Rendering that with
 * ad-hoc Columns inside the screen produced "pretty cards" that could just as
 * easily be filled with invented numbers.
 *
 * Everything here is a DUMB renderer: it takes already-measured text/values and
 * draws them. It knows nothing about the engine, the planner or navigation, so
 * it cannot invent a value, and it cannot drag the design layer into a
 * dependency on nd.max.core (the mistake logged as F-02 for MaxDomainCard).
 *
 * Rule for callers: if you do not have a measurement, do not call these with a
 * fabricated one — pass null / omit the element instead.
 */
package nd.max.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * Inline history strip for a measured series.
 *
 * Renders nothing at all when fewer than two samples exist: a single sample has
 * no shape, and stretching it into a flat line would imply "stable" where the
 * truth is "not enough data yet".
 *
 * @param values chronological samples, oldest first.
 * @param baseline optional reference value (a target / satisfaction threshold)
 *        drawn as a hairline, only if it falls inside the measured range.
 */
@Composable
fun MaxSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Accent,
    baseline: Float? = null,
    label: String? = null,
) {
    if (values.size < 2) return
    val strokeColor = tone.content()
    val baselineColor = MaxTone.Neutral.content().copy(alpha = MaxAlpha.borderStrong)
    val minValue = values.min()
    val maxValue = values.max()
    val span = (maxValue - minValue).let { if (it > 0.0001f) it else 1f }
    val spoken = label

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MaxSize.sparklineHeight)
            .then(
                if (spoken != null) {
                    Modifier.clearAndSetSemantics { contentDescription = spoken }
                } else {
                    Modifier
                }
            )
    ) {
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - minValue) / span) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (baseline != null && baseline in minValue..maxValue) {
            val y = size.height - ((baseline - minValue) / span) * size.height
            drawLine(
                color = baselineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        val lastY = size.height - ((values.last() - minValue) / span) * size.height
        drawCircle(
            color = strokeColor,
            radius = 2.5.dp.toPx(),
            center = Offset(size.width, lastY),
        )
    }
}

/**
 * Forecast vs actual on one shared scale.
 *
 * Why it exists: the engine already computes a forward thermal forecast for its
 * safety math and then throws it away. Drawing that forecast next to what later
 * actually happened is the cheapest honest proof that the system understands the
 * device — and the gap between the two lines is the error, visible instead of
 * claimed.
 *
 * Contract: both lists are indexed by the same time steps, oldest first, and a
 * `null` entry means "no value for this step" (no forecast was made, or the
 * moment has not happened yet). Nulls break the line instead of being
 * interpolated, because interpolating would draw data that was never measured.
 *
 * @param futureFrom index at which the steps stop being measured; the segment
 *        after it is faded and a hairline marks "now". Pass `points` size (or
 *        beyond) when nothing is in the future yet.
 */
@Composable
fun MaxForecastChart(
    actual: List<Float?>,
    forecast: List<Float?>,
    futureFrom: Int,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Accent,
    forecastTone: MaxTone = MaxTone.Caution,
    label: String? = null,
) {
    val steps = maxOf(actual.size, forecast.size)
    val values = actual.filterNotNull() + forecast.filterNotNull()
    if (steps < 2 || values.isEmpty()) return
    val minValue = values.min()
    val maxValue = values.max()
    val span = (maxValue - minValue).let { if (it > 0.0001f) it else 1f }
    val actualColor = tone.content()
    val forecastColor = forecastTone.content()
    val gridColor = MaxTone.Neutral.content().copy(alpha = MaxAlpha.borderStrong)
    val spoken = label

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MaxSize.sparklineHeight * 3)
            .then(
                if (spoken != null) {
                    Modifier.clearAndSetSemantics { contentDescription = spoken }
                } else {
                    Modifier
                }
            )
    ) {
        val stepX = size.width / (steps - 1)
        val strokeWidth = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(
            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
            0f,
        )

        fun yOf(value: Float): Float = size.height - ((value - minValue) / span) * size.height

        fun segmentsOf(series: List<Float?>): List<List<Offset>> {
            val out = ArrayList<List<Offset>>()
            var current = ArrayList<Offset>()
            for (index in 0 until steps) {
                val value = series.getOrNull(index)
                if (value == null) {
                    if (current.size > 1) out.add(current)
                    current = ArrayList()
                } else {
                    current.add(Offset(stepX * index, yOf(value)))
                }
            }
            if (current.size > 1) out.add(current)
            return out
        }

        fun pathOf(offsets: List<Offset>): Path {
            val path = Path()
            offsets.forEachIndexed { index, offset ->
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            return path
        }

        if (futureFrom in 1 until steps) {
            val x = stepX * futureFrom
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }

        segmentsOf(forecast).forEach { segment ->
            val isFuture = segment.first().x >= stepX * futureFrom
            drawPath(
                path = pathOf(segment),
                color = if (isFuture) {
                    forecastColor.copy(alpha = MaxAlpha.disabledContent)
                } else {
                    forecastColor
                },
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = dash),
            )
        }

        segmentsOf(actual).forEach { segment ->
            drawPath(
                path = pathOf(segment),
                color = actualColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        val lastActual = (0 until steps).lastOrNull { actual.getOrNull(it) != null }
        val lastValue = lastActual?.let { actual[it] }
        if (lastActual != null && lastValue != null) {
            drawCircle(
                color = actualColor,
                radius = 2.5.dp.toPx(),
                center = Offset(stepX * lastActual, yOf(lastValue)),
            )
        }
    }
}

/**
 * Before → after comparison for one measured quantity.
 *
 * @param afterText null when the post-change measurement never arrived; the row
 *        then shows the unavailable placeholder instead of repeating `before`,
 *        which would read as "nothing changed".
 */
@Composable
fun MaxDeltaRow(
    label: String,
    beforeText: String,
    afterText: String?,
    modifier: Modifier = Modifier,
    deltaText: String? = null,
    deltaTone: MaxTone = MaxTone.Neutral,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = beforeText,
            style = MonoValueStyleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = afterText ?: MAX_VALUE_UNAVAILABLE,
            style = MonoValueStyleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (deltaText != null) {
            MaxCapsule(text = deltaText, tone = deltaTone)
        }
    }
}

/**
 * One numbered stage of a causal chain, with the connector drawn between
 * stages so the sequence reads as a single flow rather than separate cards.
 *
 * @param technical optional raw evidence (kernel path, applied value, planner
 *        detail). Kept visually quiet but never hidden: it is what makes the
 *        narrative checkable.
 */
@Composable
fun MaxCausalStage(
    order: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Neutral,
    isLast: Boolean = false,
    technical: String? = null,
) {
    val content = tone.content()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(MaxSize.iconGlyph)
                    .background(tone.container(strong = true), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = order.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = MaxSpace.xs)
                        .width(MaxSize.hairlineBorder)
                        .weight(1f)
                        .background(MaxTone.Neutral.border(strong = true)),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (technical != null && technical.isNotBlank()) {
                Text(
                    text = technical,
                    style = MonoValueStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = MaxAlpha.supportingText),
                )
            }
        }
    }
}

/**
 * Collapsed summary of one real control-loop episode, expanding into its full
 * causal chain. Collapsed state carries the verdict so a long history stays
 * scannable; expansion is where the evidence lives.
 */
@Composable
fun MaxEpisodeCard(
    headline: String,
    timeLabel: String,
    verdictLabel: String,
    verdictTone: MaxTone,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MaxDuration.quick),
        label = "episodeChevron",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = MaxAlpha.toneContainer),
        border = BorderStroke(MaxSize.hairlineBorder, verdictTone.border(strong = expanded)),
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MaxCapsule(text = verdictLabel, tone = verdictTone)
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(MaxSize.iconGlyph)
                        .rotate(chevronRotation),
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(MaxDuration.quick)) +
                    expandVertically(tween(MaxDuration.standard)),
                exit = fadeOut(tween(MaxDuration.instant)) +
                    shrinkVertically(tween(MaxDuration.quick)),
            ) {
                Column(
                    modifier = Modifier.padding(top = MaxSpace.sm),
                    verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * One objective weight as a proportion bar. The bar is the weight itself, not a
 * decorative progress indicator, so the user can see what the system is
 * currently optimising for.
 */
@Composable
fun MaxWeightBar(
    label: String,
    fraction: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Accent,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MonoValueStyleSmall,
                color = tone.content(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MaxSpace.sm)
                .background(
                    MaxTone.Neutral.container(),
                    RoundedCornerShape(MaxRadius.pill),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeFraction)
                    .height(MaxSpace.sm)
                    .background(tone.content(), RoundedCornerShape(MaxRadius.pill)),
            )
        }
    }
}

/** Small status pill. Text always present, colour only reinforces it. */
@Composable
fun MaxCapsule(
    text: String,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Neutral,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.pill),
        color = tone.container(),
        border = BorderStroke(MaxSize.hairlineBorder, tone.border()),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.content(),
                    modifier = Modifier.size(MaxSize.iconGlyphSmall),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tone.content(),
            )
        }
    }
}
