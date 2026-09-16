/*
 * MaxManager visual foundation — Aurora / performance studio.
 * Quiet tonal surfaces, editorial hierarchy and precise, actionable readouts.
 */
package nd.max.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.container
import nd.max.ui.design.content

@Composable
fun maxSemanticColors(): MaxSemanticColors {
    val scheme = MaterialTheme.colorScheme
    return MaxSemanticColors(
        accent = scheme.primary,
        info = scheme.secondary,
        positive = scheme.primary,
        warning = scheme.tertiary,
        critical = scheme.error,
        neutral = scheme.outline
    )
}

data class MaxSemanticColors(
    val accent: Color,
    val info: Color,
    val positive: Color,
    val warning: Color,
    val critical: Color,
    val neutral: Color
)

object MaxUiAlpha {
    const val surfaceBorder = 0.32f
    const val subtleBorder = 0.24f
    const val accentSurface = 0.08f
    const val mutedText = 0.78f
    const val edgeLight = 0.22f
    const val haloGlow = 0.07f
}

object MaxUiMetrics {
    val pagePadding = MaxSpace.gutter
    val sectionGap = MaxSpace.section
    val itemGap = MaxSpace.md
    val cardPadding = MaxSpace.lg
    val cardRadius = MaxRadius.group
    val smallRadius = MaxRadius.row
    val compactRadius = MaxRadius.control
    val screenHorizontalPadding = MaxSpace.gutter
    val screenTopPadding = MaxSpace.lg
    val screenBottomPadding = MaxSpace.pageBottom
    val screenItemGap = MaxSpace.md
}

private val studioCardShape = RoundedCornerShape(MaxRadius.group)
private val studioActionShape = RoundedCornerShape(MaxRadius.row)

private fun studioSurfaceColor(scheme: ColorScheme, accent: Color?): Color =
    accent?.copy(alpha = 0.045f)?.compositeOver(scheme.surfaceContainerLow)
        ?: scheme.surfaceContainerLow

@Composable
fun MaxSurface(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    var m = modifier
        .animateContentSize(animationSpec = MaxMotion.contentSpring)
        .maxPressMotion(interactionSource)
        .clip(studioCardShape)
        .background(studioSurfaceColor(scheme, accent))
        .border(
            BorderStroke(1.dp, (accent ?: scheme.outlineVariant).copy(alpha = if (accent != null) MaxAlpha.borderStrong else MaxAlpha.border)),
            studioCardShape
        )
    if (onClick != null) {
        m = m.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick
        )
    }
    Column(modifier = m.padding(MaxUiMetrics.cardPadding), content = content)
}

@Composable
fun MaxSurfaceBox(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    containerColor: Color? = null,
    shape: RoundedCornerShape = RoundedCornerShape(MaxUiMetrics.cardRadius),
    borderEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    var m = modifier
        .animateContentSize(animationSpec = MaxMotion.contentSpring)
        .maxPressMotion(interactionSource)
        .clip(shape)
        .background(containerColor ?: studioSurfaceColor(scheme, accent))
    if (borderEnabled) {
        m = m.border(
            BorderStroke(1.dp, (accent ?: scheme.outlineVariant).copy(alpha = if (accent != null) MaxAlpha.borderStrong else MaxAlpha.border)),
            shape
        )
    }
    if (onClick != null) {
        m = m.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick
        )
    }
    Box(modifier = m, content = content)
}

@Composable
fun MaxSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(modifier = modifier.fillMaxWidth().maxHeadingSemantics()) {
        Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MaxStatusPill(text: String, active: Boolean, accent: Color = MaterialTheme.colorScheme.primary) {
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        if (active) accent else scheme.onSurfaceVariant,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "statusAccent"
    )
    Surface(
        modifier = Modifier.semantics { stateDescription = text },
        shape = RoundedCornerShape(MaxRadius.control),
        color = color.copy(alpha = 0.08f).compositeOver(scheme.surfaceContainerLow),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier.padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(MaxSpace.sm).clip(RoundedCornerShape(if (active) 50 else 20)).background(color))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MaxMetric(
    label: String,
    value: String,
    unit: String = "",
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    MaxSurface(modifier = modifier, accent = accent, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(MaxRadius.control),
                color = accent.copy(alpha = MaxAlpha.toneContainer)
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(MaxSpace.sm).size(MaxSize.iconGlyph))
            }
            Spacer(Modifier.width(MaxSpace.md))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(MaxSpace.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            fadeIn(tween(MaxMotion.standard, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(MaxMotion.fast))
                        },
                        label = "metricValue"
                    ) { animatedValue ->
                        Text(
                            animatedValue,
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                    }
                    if (unit.isNotBlank()) {
                        Text(unit, style = MonoValueStyleMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp, bottom = 1.dp))
                    }
                }
                if (!supporting.isNullOrBlank()) {
                    Spacer(Modifier.height(MaxSpace.xs))
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(MaxSize.iconGlyph))
            }
        }
    }
}

@Composable
fun MaxActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val layoutDirection = LocalLayoutDirection.current
    val chevronShift by animateFloatAsState(
        targetValue = if (pressed) 0f else 1f,
        animationSpec = tween(MaxMotion.fast, easing = FastOutSlowInEasing),
        label = "chevronShift"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaxMotion.contentSpring)
            .maxPressMotion(interactionSource)
            .clip(studioActionShape)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .maxButtonSemantics(title),
        shape = studioActionShape,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(MaxSize.hairlineBorder, scheme.outlineVariant.copy(alpha = MaxAlpha.border))
    ) {
        Row(Modifier.heightIn(min = MaxSize.minTouchTarget).padding(horizontal = MaxSpace.lg, vertical = MaxSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(MaxRadius.control), color = accent.copy(alpha = MaxAlpha.toneContainer)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(MaxSpace.sm).size(MaxSize.iconGlyph))
            }
            Spacer(Modifier.width(MaxSpace.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (!value.isNullOrBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Surface(shape = RoundedCornerShape(MaxRadius.control), color = accent.copy(alpha = MaxAlpha.toneContainer)) {
                        AnimatedContent(
                            targetState = value,
                            transitionSpec = { fadeIn(tween(MaxMotion.fast)) togetherWith fadeOut(tween(MaxMotion.fast)) },
                            label = "actionValue"
                        ) { animatedValue ->
                            Text(animatedValue, modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.hairline), style = MaterialTheme.typography.labelMedium, color = accent)
                        }
                    }
                }
            }
            Spacer(Modifier.width(MaxSpace.md))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                    translationX = (1f - chevronShift) * 4f * direction
                }
            )
        }
    }
}

@Composable
fun MaxSparkline(values: List<Float>, accent: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxWidth().height(MaxSize.sparklineHeight)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(1f)
        val inset = 4.dp.toPx().coerceAtMost(size.minDimension / 2f)
        val plotHeight = (size.height - inset * 2f).coerceAtLeast(0f)
        val step = (size.width - inset * 2f) / values.lastIndex.coerceAtLeast(1)
        val points = values.mapIndexed { i, v ->
            Offset(inset + i * step, size.height - inset - ((v - min) / range) * plotHeight)
        }
        for (i in 1..3) {
            val y = inset + plotHeight * i / 4f
            drawLine(scheme.outlineVariant.copy(alpha = MaxAlpha.border), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
        }
        val areaPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, size.height - inset)
            lineTo(points.first().x, size.height - inset)
            close()
        }
        drawPath(areaPath, accent.copy(alpha = 0.055f))
        drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(scheme.surfaceContainerLow, radius = 4.dp.toPx(), center = points.last())
        drawCircle(accent, radius = 2.5.dp.toPx(), center = points.last())
    }
}
