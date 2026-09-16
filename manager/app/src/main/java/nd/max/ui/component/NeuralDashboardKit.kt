package nd.max.ui.component


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * Neural dashboard kit — the single visual language for MAX's data surfaces.
 *
 * Design rules, all enforced here instead of per screen:
 *
 *  - Three depth levels only: panel (outlined, optional accent glow), tile
 *    (filled, no border) and accent tile (accent wash + accent hairline).
 *    Radii are fixed at 24 / 18 / 12 and padding at 16 / 14 / 12.
 *  - One measurement per tile, number first, caption above it, trend below.
 *  - Plots are smoothed (Catmull-Rom) and auto-scaled to the visible window:
 *    a metric that hovers at 75% must still show its shape, not a flat line.
 *  - Every color resolves from MaterialTheme.colorScheme, so the palette,
 *    contrast and light/dark choice made in Settings drives the whole screen.
 *  - Live numbers render through [NeuralValue], which pins layout direction to
 *    LTR. "7.9 GB / 10.6 GB" is Latin technical notation and must never be
 *    reordered or clipped by an RTL locale.
 */

val NeuralPanelShape = RoundedCornerShape(24.dp)
val NeuralTileShape = RoundedCornerShape(18.dp)
private val ChipShape = RoundedCornerShape(12.dp)

@Immutable
data class NeuralPalette(
    val panel: Color,
    val panelTop: Color,
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
        panelTop = c.surfaceContainerHigh,
        tile = c.surfaceContainerHighest.copy(alpha = .40f),
        text = c.onSurface,
        muted = c.onSurfaceVariant,
        border = c.outlineVariant.copy(alpha = .45f),
        grid = c.onSurfaceVariant.copy(alpha = .13f),
        accent = c.primary,
        accentAlt = c.tertiary,
        ok = c.secondary,
        warn = c.tertiary,
        danger = c.error,
    )
}

/** Press feedback shared by every tappable surface: 2.5% scale plus theme ripple. */
@Composable
private fun Modifier.neuralClickable(onClick: (() -> Unit)?): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .975f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "neural-press",
    )
    // Every call site runs the same composable calls above, whether or not the
    // surface is tappable, so composition structure stays stable.
    if (onClick == null) return this
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
}

/**
 * Outer container. When an accent is supplied the panel gets a soft corner glow
 * and a tinted hairline; that single touch is what separates a "card" from the
 * flat grey rectangles the screen used to be made of.
 */
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
    val base = modifier
        .fillMaxWidth()
        .neuralClickable(onClick)
        .clip(NeuralPanelShape)
        .background(Brush.verticalGradient(listOf(p.panelTop.copy(alpha = .92f), p.panel)))
    val glow = if (accent == null) {
        base
    } else {
        base.background(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .16f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = 620f,
            )
        )
    }
    Column(
        glow
            .border(BorderStroke(1.dp, accent?.copy(alpha = .28f) ?: p.border), NeuralPanelShape)
            .padding(contentPadding),
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
        .neuralClickable(onClick)
        .clip(NeuralTileShape)
        .background(accent?.copy(alpha = .10f) ?: p.tile)
    if (accent != null) box = box.border(BorderStroke(1.dp, accent.copy(alpha = .22f)), NeuralTileShape)
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
    align: TextAlign? = null,
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
            softWrap = false,
            textAlign = align,
        )
    }
}

/** Section heading: accent rule, title, caption, optional trailing slot. */
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
                .height(if (caption == null) 18.dp else 32.dp)
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
            )
            if (caption != null) {
                Text(
                    caption,
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Status pill. */
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
    Row(
        modifier
            .neuralClickable(onClick)
            .clip(CircleShape)
            .background(if (filled) accent.copy(alpha = .16f) else Color.Transparent)
            .border(BorderStroke(1.dp, accent.copy(alpha = if (filled) .42f else .28f)), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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

// ---------------------------------------------------------------- plots

/**
 * Map values to canvas points. [adaptive] rescales to the visible window with a
 * 25% margin, which is what makes a metric parked at 75% still read as a curve
 * instead of a straight line glued to the top edge.
 */
private fun plotPoints(
    values: List<Float>,
    width: Float,
    height: Float,
    ceiling: Float,
    adaptive: Boolean,
): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val lo: Float
    val hi: Float
    if (adaptive) {
        val low = values.minOrNull() ?: 0f
        val high = values.maxOrNull() ?: ceiling
        val pad = ((high - low) * .25f).coerceAtLeast(ceiling * .04f)
        lo = (low - pad).coerceAtLeast(0f)
        hi = (high + pad).coerceAtMost(ceiling).coerceAtLeast(lo + ceiling * .08f)
    } else {
        lo = 0f
        hi = ceiling
    }
    val span = (hi - lo).coerceAtLeast(0.001f)
    val step = if (values.size > 1) width / (values.size - 1) else width
    return values.mapIndexed { index, raw ->
        val t = ((raw - lo) / span).coerceIn(0f, 1f)
        Offset(step * index, height - t * height)
    }
}

/** Catmull-Rom through every point, emitted as cubic segments. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points[if (i > 0) i - 1 else i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[if (i + 2 < points.size) i + 2 else i + 1]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y,
        )
    }
    return path
}

private fun DrawScope.drawSeries(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    fill: Boolean,
    marker: Boolean,
) {
    if (points.size < 2) return
    val line = smoothPath(points)
    if (fill) {
        val area = Path()
        area.addPath(line)
        area.lineTo(points.last().x, size.height)
        area.lineTo(points.first().x, size.height)
        area.close()
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(color.copy(alpha = .34f), color.copy(alpha = .10f), Color.Transparent)
            ),
        )
    }
    drawPath(line, color.copy(alpha = .22f), style = Stroke(width = strokeWidth * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(line, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (marker) {
        val last = points.last()
        drawCircle(color.copy(alpha = .25f), radius = strokeWidth * 3.2f, center = last)
        drawCircle(color, radius = strokeWidth * 1.3f, center = last)
    }
}

/** Compact smoothed trend for KPI tiles. */
@Composable
fun NeuralSparkline(
    values: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f,
    adaptive: Boolean = true,
) {
    Canvas(modifier) {
        val points = plotPoints(values, size.width, size.height, if (maxValue <= 0f) 1f else maxValue, adaptive)
        drawSeries(points, accent, 2.dp.toPx(), fill = true, marker = false)
    }
}

/** Full-width smoothed plot with grid, used where two series must be compared. */
@Composable
fun NeuralAreaPlot(
    values: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    secondary: List<Float> = emptyList(),
    secondaryAccent: Color? = null,
    maxValue: Float = 100f,
    adaptive: Boolean = true,
) {
    val p = neuralPalette()
    Canvas(modifier) {
        val ceiling = if (maxValue <= 0f) 1f else maxValue
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 7.dp.toPx()))
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(p.grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
        }
        if (secondaryAccent != null) {
            drawSeries(plotPoints(secondary, size.width, size.height, ceiling, adaptive), secondaryAccent, 2.dp.toPx(), fill = false, marker = true)
        }
        drawSeries(plotPoints(values, size.width, size.height, ceiling, adaptive), accent, 2.6.dp.toPx(), fill = true, marker = true)
    }
}

/**
 * Load spectrum: one rounded bar per sample, tinted from calm to hot by value.
 * Bars beat a line here because the question is "how often does it spike",
 * which is a comparison of quantities rather than a trend.
 */
@Composable
fun NeuralBarSpectrum(
    values: List<Float>,
    accent: Color,
    hot: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f,
    maxBars: Int = 26,
) {
    val p = neuralPalette()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "neural-spectrum",
    )
    Canvas(modifier) {
        val series = values.takeLast(maxBars)
        if (series.isEmpty()) return@Canvas
        val ceiling = if (maxValue <= 0f) 1f else maxValue
        val slot = size.width / series.size
        val barWidth = (slot * .55f).coerceAtLeast(2f)
        val radius = barWidth / 2f
        series.forEachIndexed { index, raw ->
            val t = (raw / ceiling).coerceIn(0f, 1f)
            val barHeight = (size.height * t * appear).coerceAtLeast(barWidth)
            val x = slot * index + (slot - barWidth) / 2f
            val top = size.height - barHeight
            drawRoundRect(
                color = p.grid,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(lerp(accent, hot, t), accent.copy(alpha = .55f)),
                    startY = top,
                    endY = size.height,
                ),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
    }
}

// ---------------------------------------------------------------- rings


// ---------------------------------------------------------------- readouts

/** KPI tile: caption, large readout, support line, smoothed trend. */
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
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent.copy(alpha = .85f)))
        }
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            color = p.text,
        )
        if (support != null) NeuralValue(support, style = MonoValueStyleSmall.copy(fontSize = 11.sp), color = p.muted)
        if (history.size > 1) {
            NeuralSparkline(history, accent, Modifier.fillMaxWidth().height(28.dp), maxValue = maxValue)
        }
    }
}

/**
 * Budget bar. Label and value sit on separate lines: sharing one row was what
 * clipped "7.9 GB / 10.6 GB" down to ".8 GB" once an RTL label took the width.
 */
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
    Column(
        modifier
            .fillMaxWidth()
            .neuralClickable(onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        NeuralCaption(label, color = accent)
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = p.text,
        )
        NeuralTrack(fraction, accent)
        if (support != null) {
            Text(
                support,
                color = p.muted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Thin rounded progress track. */
@Composable
fun NeuralTrack(fraction: Float, accent: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val p = neuralPalette()
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
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
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .6f), accent)))
        )
    }
}

/** Feed row for event timelines. */
@Composable
fun NeuralFeedRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    Row(
        modifier
            .fillMaxWidth()
            .neuralClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuralIconChip(icon, accent, size = 30.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = p.text,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (meta != null) {
                Text(
                    meta,
                    color = p.muted,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

/** Caption plus value, the smallest readout unit. */
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

/** Compact action tile. */
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
        )
        if (support != null) {
            Text(
                support,
                color = p.muted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

/** Collapsible panel so secondary detail can exist without crowding the screen. */
@Composable
fun NeuralExpandable(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = neuralPalette()
    NeuralPanel(modifier, onClick = onToggle) {
        NeuralSectionHeader(
            title = title,
            caption = caption,
            accent = accent,
            trailing = {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    Modifier.size(20.dp),
                    tint = p.muted,
                )
            },
        )
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}
