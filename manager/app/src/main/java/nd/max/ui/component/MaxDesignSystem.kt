/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
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
    val pagePadding = 20.dp
    val sectionGap = 28.dp
    val itemGap = 12.dp
    val cardPadding = 18.dp
    val cardRadius = 28.dp
    val smallRadius = 18.dp
    val compactRadius = 12.dp
    val screenHorizontalPadding = 20.dp
    val screenTopPadding = 16.dp
    val screenBottomPadding = 32.dp
    val screenItemGap = 12.dp
}

private val studioCardShape = RoundedCornerShape(28.dp)
private val studioActionShape = RoundedCornerShape(18.dp)

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
            BorderStroke(1.dp, (accent ?: scheme.outlineVariant).copy(alpha = if (accent != null) 0.22f else MaxUiAlpha.surfaceBorder)),
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
            BorderStroke(1.dp, (accent ?: scheme.outlineVariant).copy(alpha = if (accent != null) 0.22f else MaxUiAlpha.surfaceBorder)),
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
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f).compositeOver(scheme.surfaceContainerLow),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(if (active) 50 else 20)).background(color))
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
            Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.10f)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = value,
                modifier = Modifier.weight(1f, fill = false),
                transitionSpec = {
                    fadeIn(tween(MaxMotion.standard, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(MaxMotion.fast))
                },
                label = "metricValue"
            ) { animatedValue ->
                Text(
                    animatedValue,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            }
            if (unit.isNotBlank()) {
                Text(unit, style = MonoValueStyleMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp, bottom = 3.dp))
            }
        }
        if (!supporting.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.45f)))
            Spacer(Modifier.height(9.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = MaxUiAlpha.surfaceBorder))
    ) {
        Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = MaxUiAlpha.accentSurface)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(10.dp).size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (!value.isNullOrBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.08f)) {
                        AnimatedContent(
                            targetState = value,
                            transitionSpec = { fadeIn(tween(MaxMotion.fast)) togetherWith fadeOut(tween(MaxMotion.fast)) },
                            label = "actionValue"
                        ) { animatedValue ->
                            Text(animatedValue, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = accent)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
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
    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
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
            drawLine(scheme.outlineVariant.copy(alpha = 0.35f), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
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
        drawPath(areaPath, accent.copy(alpha = 0.06f))
        drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(scheme.surfaceContainerLow, radius = 4.dp.toPx(), center = points.last())
        drawCircle(accent, radius = 2.5.dp.toPx(), center = points.last())
    }
}
