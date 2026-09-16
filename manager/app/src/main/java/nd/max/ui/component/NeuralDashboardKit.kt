package nd.max.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * Neural dashboard kit — the single visual language for MAX's data surfaces.
 *
 * Why this file exists: the previous home screen stacked a dozen bespoke cards,
 * each with its own padding, icon badge, arrow and title rhythm. The result read
 * as a list of settings rows rather than an instrument panel. Everything here is
 * built from four primitives only — panel, tile, readout, plot — so a screen
 * assembled from the kit is automatically consistent.
 *
 * Numbers always render through [NeuralValue], which pins the text direction to
 * LTR. Live readouts such as "2712x1220 - 120 Hz" or "7.4 / 10.6 GB" are Latin
 * technical notation; letting an RTL locale reorder them (as the old screens did)
 * produced values that looked plainly wrong to the reader.
 */

val NeuralPanelShape = RoundedCornerShape(22.dp)
val NeuralTileShape = RoundedCornerShape(18.dp)
private val ChipShape = RoundedCornerShape(12.dp)

@Immutable
data class NeuralPalette(
    val panel: Color,
    val tile: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val grid: Color,
    val accent: Color,
    val accentAlt: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
)

@Composable
fun neuralPalette(): NeuralPalette {
    val c = MaterialTheme.colorScheme
    return NeuralPalette(
        panel = c.surfaceContainerLow,
        tile = c.surfaceContainerHighest.copy(alpha = .38f),
        text = c.onSurface,
        muted = c.onSurfaceVariant,
        border = c.outlineVariant.copy(alpha = .5f),
        grid = c.onSurfaceVariant.copy(alpha = .14f),
        accent = c.primary,
        accentAlt = c.tertiary,
        ok = c.secondary,
        warn = c.tertiary,
        danger = c.error,
    )
}

/** Outer container. One radius, one border weight, one padding across the app. */
@Composable
fun NeuralPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = neuralPalette()
    var box = modifier
        .fillMaxWidth()
        .clip(NeuralPanelShape)
        .background(Brush.verticalGradient(listOf(p.panel, p.panel.copy(alpha = .62f))))
        .border(BorderStroke(1.dp, accent?.copy(alpha = .30f) ?: p.border), NeuralPanelShape)
    if (onClick != null) box = box.clickable(onClick = onClick)
    Column(
        box.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

/** Inner container for grouped readouts inside a panel. */
@Composable
fun NeuralTile(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = neuralPalette()
    var box = modifier
        .clip(NeuralTileShape)
        .background(accent?.copy(alpha = .10f) ?: p.tile)
    if (accent != null) box = box.border(BorderStroke(1.dp, accent.copy(alpha = .22f)), NeuralTileShape)
    if (onClick != null) box = box.clickable(onClick = onClick)
    Column(
        box.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

/** Small all-caps caption used above every readout. */
@Composable
fun NeuralCaption(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val p = neuralPalette()
    Text(
        text,
        modifier,
        color = color ?: p.muted,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Live numeric readout. Always laid out LTR so units and separators stay in order. */
@Composable
fun NeuralValue(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MonoValueStyleSmall,
    color: Color? = null,
    maxLines: Int = 1,
) {
    val p = neuralPalette()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text,
            modifier,
            color = color ?: p.text,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Section heading: caption line, title line, optional trailing slot. No icon badges. */
@Composable
fun NeuralSectionHeader(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = neuralPalette()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (caption == null) 18.dp else 30.dp)
                .clip(CircleShape)
                .background(accent ?: p.accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = p.text,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    caption,
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Status pill. Filled for live/primary state, outlined otherwise. */
@Composable
fun NeuralPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    dot: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    var box = modifier
        .clip(CircleShape)
        .background(if (filled) accent.copy(alpha = .18f) else Color.Transparent)
        .border(BorderStroke(1.dp, accent.copy(alpha = if (filled) .45f else .30f)), CircleShape)
    if (onClick != null) box = box.clickable(onClick = onClick)
    Row(
        box.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = accent)
        Text(text, color = accent, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Square icon chip used by tiles and feed rows. */
@Composable
fun NeuralIconChip(icon: ImageVector, accent: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier
            .size(size)
            .clip(ChipShape)
            .background(accent.copy(alpha = .16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = accent)
    }
}

/**
 * KPI tile: caption, large readout, delta caption and an optional sparkline.
 * This is the unit the reference dashboards repeat, and it replaces the old
 * "icon + title + subtitle + arrow" rows that made every metric look like a
 * navigation entry instead of a measurement.
 */
@Composable
fun NeuralKpiTile(
    caption: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    support: String? = null,
    history: List<Float> = emptyList(),
    maxValue: Float = 100f,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    NeuralTile(modifier, accent = accent, onClick = onClick, verticalSpacing = 6.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralCaption(caption, Modifier.weight(1f), color = accent)
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent.copy(alpha = .8f)))
        }
        NeuralValue(value, style = MonoValueStyleSmall.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold), color = p.text)
        if (support != null) NeuralValue(support, style = MonoValueStyleSmall.copy(fontSize = 11.sp), color = p.muted)
        if (history.size > 1) {
            NeuralSparkline(history, accent, Modifier.fillMaxWidth().height(26.dp), maxValue = maxValue)
        }
    }
}

/** Compact line+area plot for tiles. */
@Composable
fun NeuralSparkline(
    values: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f,
    filled: Boolean = true,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val ceiling = if (maxValue <= 0f) 1f else maxValue
        val step = size.width / (values.size - 1)
        val line = Path()
        val area = Path()
        values.forEachIndexed { index, raw ->
            val x = step * index
            val y = size.height - (raw.coerceIn(0f, ceiling) / ceiling) * size.height
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, size.height)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        if (filled) {
            area.lineTo(size.width, size.height)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .30f), Color.Transparent)))
        }
        drawPath(
            line,
            accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Full-width plot with grid, gradient fill and a marker on the newest sample —
 * the "request volume" chart from the reference, applied to live device load.
 */
@Composable
fun NeuralAreaPlot(
    values: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    secondary: List<Float> = emptyList(),
    secondaryAccent: Color? = null,
    maxValue: Float = 100f,
) {
    val p = neuralPalette()
    Canvas(modifier) {
        val ceiling = if (maxValue <= 0f) 1f else maxValue
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(p.grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
        }
        fun plot(series: List<Float>, color: Color, fill: Boolean) {
            if (series.size < 2) return
            val step = size.width / (series.size - 1)
            val line = Path()
            val area = Path()
            var lastX = 0f
            var lastY = size.height
            series.forEachIndexed { index, raw ->
                val x = step * index
                val y = size.height - (raw.coerceIn(0f, ceiling) / ceiling) * size.height
                if (index == 0) {
                    line.moveTo(x, y)
                    area.moveTo(x, size.height)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
                lastX = x
                lastY = y
            }
            if (fill) {
                area.lineTo(size.width, size.height)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .34f), Color.Transparent)))
            }
            drawPath(line, color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color.copy(alpha = .28f), radius = 7.dp.toPx(), center = Offset(lastX, lastY))
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
        }
        if (secondary.size > 1 && secondaryAccent != null) plot(secondary, secondaryAccent, false)
        plot(values, accent, true)
    }
}

/** Donut readout. Center content is supplied so it can hold a value plus label. */
@Composable
fun NeuralRing(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 72.dp,
    stroke: Dp = 7.dp,
    center: @Composable () -> Unit,
) {
    val p = neuralPalette()
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(650),
        label = "neural-ring",
    )
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arc = Size(size.width - width, size.height - width)
            drawArc(
                color = p.grid,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(accent.copy(alpha = .45f), accent)),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

/** Ring plus captions, sized for a three-up row. */
@Composable
fun NeuralRingStat(
    caption: String,
    value: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    support: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    var box = modifier
    if (onClick != null) box = box.clip(NeuralTileShape).clickable(onClick = onClick)
    Column(box, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NeuralRing(fraction, accent, diameter = 74.dp) {
            NeuralValue(value, style = MonoValueStyleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = p.text)
        }
        NeuralCaption(caption, color = p.muted)
        if (support != null) NeuralValue(support, style = MonoValueStyleSmall.copy(fontSize = 10.sp), color = accent)
    }
}

/** Budget bar: label left, readout right, thin track underneath. */
@Composable
fun NeuralBudgetBar(
    label: String,
    value: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    support: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    var box = modifier.fillMaxWidth()
    if (onClick != null) box = box.clip(NeuralTileShape).clickable(onClick = onClick)
    Column(box, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralCaption(label, Modifier.weight(1f))
            NeuralValue(value, style = MonoValueStyleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = p.text)
        }
        NeuralTrack(fraction, accent)
        if (support != null) NeuralValue(support, style = MonoValueStyleSmall.copy(fontSize = 10.sp), color = p.muted)
    }
}

/** Thin rounded progress track. */
@Composable
fun NeuralTrack(fraction: Float, accent: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val p = neuralPalette()
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(520),
        label = "neural-track",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(p.grid)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .65f), accent)))
        )
    }
}

/** Feed row for the event timeline. */
@Composable
fun NeuralFeedRow(
    icon: ImageVector,
    title: String,
    meta: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    var box = modifier.fillMaxWidth()
    if (onClick != null) box = box.clip(NeuralTileShape).clickable(onClick = onClick)
    Row(box, verticalAlignment = Alignment.CenterVertically) {
        NeuralIconChip(icon, accent, size = 30.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = p.text,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                color = p.muted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Two-column readout grid used for verdict facts (limiter, workload, heat...). */
@Composable
fun NeuralFactTile(
    caption: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val p = neuralPalette()
    NeuralTile(modifier, verticalSpacing = 4.dp, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        NeuralCaption(caption, color = accent)
        NeuralValue(value, style = MonoValueStyleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = p.text)
    }
}

/** Compact action tile for the command deck. */
@Composable
fun NeuralActionTile(
    icon: ImageVector,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    support: String? = null,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    NeuralTile(modifier, accent = accent, onClick = onClick, verticalSpacing = 8.dp) {
        NeuralIconChip(icon, accent, size = 30.dp)
        Text(
            title,
            color = p.text,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (support != null) {
            Text(
                support,
                color = p.muted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
