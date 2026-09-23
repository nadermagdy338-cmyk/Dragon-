package nd.max.ui.component


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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
import nd.max.ui.util.ClockMeter

/**
 * Neural dashboard kit — the single visual language for MAX's data surfaces.
 *
 * Design rules, all enforced here instead of per screen:
 *
 *  - Three depth levels only: panel (outlined, optional accent glow), tile
 *    (filled, no border) and accent tile (accent wash + accent hairline).
 *    Radii are fixed at 26 / 20 / 12 and padding at 16 / 14 / 12.
 *  - One measurement per tile, number first, caption above it, trend below.
 *  - Plots are smoothed (Catmull-Rom) and auto-scaled to the visible window:
 *    a metric that hovers at 75% must still show its shape, not a flat line.
 *  - Every color resolves from MaterialTheme.colorScheme, so the palette,
 *    contrast and light/dark choice made in Settings drives the whole screen.
 *  - Live numbers render through [NeuralValue], which pins layout direction to
 *    LTR. "7.9 GB / 10.6 GB" is Latin technical notation and must never be
 *    reordered or clipped by an RTL locale.
 */

val NeuralPanelShape = RoundedCornerShape(22.dp)
val NeuralTileShape = RoundedCornerShape(16.dp)
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

/**
 * نسبة لون المفتاح الممزوجة في أسطح المكتبة.
 *
 * والرقم **مقيس لا مُختار**: حُلّلت ثماني لقطات تصميم مرجعية بكسلًا بكسل، فظهر أن أسطحها مصبوغة
 * بلون مفتاحها — `#200050` (بنفسجي) فوق خلفية `#100020`، و`#203050` (كحلي) في لقطة أخرى،
 * و`#003040` (تركواز) في ثالثة — بينما تولّد تدرّجات Material محايدات شبه رمادية عن قصد
 * (انظر تعليق `Theme.kt`: «generated neutrals keep surfaces calm»).
 *
 * وسبعة بالمئة تفي بالغرض لأن الدمج **يُضيف صبغة ولا يُطيح بالإضاءة**: أسود + ٧٪ من لون ساطع
 * ما زال أسود (تباين النصّ الأبيض فوقه > ١٤:١)، وأبيض + ٧٪ من لون غامق ما زال فاتحًا. فالمكسب
 * بصريّ والثمن صفر في القراءة — وهو الشرط الذي لا أتنازل عنه في أي تغيير يمسّ كل الشاشات.
 */
private const val SurfaceHueFraction = .07f

@Composable
fun neuralPalette(): NeuralPalette {
    val c = MaterialTheme.colorScheme
    // أسطح المكتبة تحمل **هويّة اللون** لا الرمادي: هو الفرق الواحد الأظهر بين تطبيقنا ونماذج
    // التصميم المرجعية، وهو تغيير ينتقل إلى كل شاشة تستعمل المكتبة بلا لمس أي شاشة منها.
    return NeuralPalette(
        panel = lerp(c.surfaceContainerLow, c.primary, SurfaceHueFraction),
        panelTop = lerp(c.surfaceContainerHigh, c.primary, SurfaceHueFraction),
        tile = lerp(c.surfaceContainerHighest, c.primary, SurfaceHueFraction * .7f).copy(alpha = .40f),
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

/**
 * Press feedback shared by every tappable surface: 2.5% scale plus theme ripple.
 *
 * `internal` because the kit's satellite file (`NeuralControls.kt`) must reuse this
 * exact interaction — a second press implementation would drift on the first edit.
 */
@Composable
internal fun Modifier.neuralClickable(onClick: (() -> Unit)?): Modifier {
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
    contentPadding: PaddingValues = PaddingValues(12.dp),
    verticalSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = neuralPalette()
    // العمق كله في `neuralSurface`: ظلّ مُلوَّن بلون اللوحة أو بلون مخطّطها، وهالتان ركنيتان
    // (قوية حيث تقع العين أولًا، خافتة في الركن المقابل)، ولمعة حافة عليا مشتقّة من إضاءة
    // السطح. ولوح مخطَّط يُرفع درجةً فوق أخوته لأن وجوده نفسه معلومة (تركيز أو تحذير).
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val surface = modifier
        .fillMaxWidth()
        .neuralClickable(onClick)
        .neuralSurface(
            shape = NeuralPanelShape,
            top = p.panelTop.copy(alpha = .94f),
            bottom = p.panel,
            border = accent?.copy(alpha = .28f) ?: p.border.copy(alpha = .78f),
            glow = accent ?: p.accent,
            elevation = if (accent == null) 1.dp else 2.dp,
            glowStrength = if (accent == null) .12f else .20f,
            rtl = rtl,
        )
    Column(
        surface.padding(contentPadding),
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
    contentPadding: PaddingValues = PaddingValues(11.dp),
    verticalSpacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = neuralPalette()
    // البلاطة أصغر من اللوحة فتحتاج تدرّجًا أقصر: الصبغة تبدأ أعلى (17٪) وتنزل (7٪) فيُقرأ
    // السطح مقببًا لا مسطّحًا — والمبلاطة غير الملوّنة تبقى هادئة وتأخذ لمعة الحافة وحدها.
    val box = modifier
        .neuralClickable(onClick)
        .neuralSurface(
            shape = NeuralTileShape,
            top = accent?.copy(alpha = .12f) ?: p.tile,
            bottom = accent?.copy(alpha = .055f) ?: p.tile,
            border = accent?.copy(alpha = .20f) ?: p.border.copy(alpha = .42f),
            glow = accent,
            elevation = if (accent == null) 0.dp else 1.dp,
            sheen = if (accent == null) -1f else .025f,
            rtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
        )
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
        fontSize = 9.5.sp,
        lineHeight = 12.sp,
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
        // الشرطة كاملة اللون عند رأسها وخافتة عند ذيلها: تُقرأ كمسطرة تُشير إلى العنوان لا
        // كخطّ لاصق. وهي أرخص علامة هويّة في الشاشة، وتتكرّر في كل مقطع فيصير الشكل واحدًا.
        val tone = accent ?: p.accent
        Box(
            Modifier
                .width(3.dp)
                .height(if (caption == null) 16.dp else 30.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(tone, tone.copy(alpha = .38f))))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = p.text,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (caption != null) {
                Text(
                    caption,
                    color = p.muted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
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
    // الشارة الممتلئة مصبوغة بتدرّج (لا لون مسطّح): هي «حالة» تُقرأ من بعيد، والتدرّج يجعلها
    // قطعة واحدة مع هوية الشاشة بلا أن تصرخ.
    var chip = modifier
        .neuralClickable(onClick)
        .clip(CircleShape)
    if (filled) {
        chip = chip.background(
            Brush.horizontalGradient(listOf(accent.copy(alpha = .26f), accent.copy(alpha = .10f)))
        )
    }
    Row(
        chip
            .border(BorderStroke(1.dp, accent.copy(alpha = if (filled) .46f else .28f)), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = accent)
        Text(text, color = accent, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Square icon chip used by tiles and feed rows. */
@Composable
fun NeuralIconChip(icon: ImageVector, accent: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    // الأيقونة تُقرأ أسرع حين تجلس في رقعة مصبوغة متدرّجة لا في مربّع لون مسطّح، والحدّ
    // الرفيع يحفظ شكلها على السطح الأبيض في الوضع الفاتح.
    Box(
        modifier
            .size(size)
            .clip(ChipShape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = .28f), accent.copy(alpha = .12f))))
            .border(BorderStroke(1.dp, accent.copy(alpha = .22f)), ChipShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = accent)
    }
}

/** Compact icon button shared by headers and dense dashboard controls. */
@Composable
fun NeuralIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    val tone = accent ?: p.muted
    Box(
        modifier
            .clip(ChipShape)
            .background(Brush.verticalGradient(listOf(tone.copy(alpha = .12f), tone.copy(alpha = .05f))))
            .border(BorderStroke(1.dp, tone.copy(alpha = .18f)), ChipShape)
            .neuralClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(18.dp), tint = tone)
    }
}

// ---------------------------------------------------------------- plots

/**
 * Map values to canvas points. [adaptive] rescales to the visible window with a
 * 25% margin, which is what makes a metric parked at 75% still read as a curve
 * instead of a straight line glued to the top edge.
 */
private fun plotPoints(
    values: List<Float?>,
    width: Float,
    height: Float,
    ceiling: Float,
    adaptive: Boolean,
): List<Offset?> {
    if (values.isEmpty()) return emptyList()
    // المقياس يُحسب من القيم **المقيسة فقط**، والقيمة المجهولة تُصبح فجوة (null) لا صفرًا:
    // صفرٌ يُرسم كقاعٍ في المنحنى، والفجوة تقول الحقيقة — «لم تُقرأ».
    val measured = values.filterNotNull()
    val lo: Float
    val hi: Float
    if (adaptive) {
        val low = measured.minOrNull() ?: 0f
        val high = measured.maxOrNull() ?: ceiling
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
        raw?.let {
            val t = ((it - lo) / span).coerceIn(0f, 1f)
            Offset(step * index, height - t * height)
        }
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
    points: List<Offset?>,
    color: Color,
    strokeWidth: Float,
    fill: Boolean,
    marker: Boolean,
) {
    // كل قطعة متصلة تُرسم وحدها، والقيمة المجهولة تُبقي **فجوة** في الرسم: خطٌّ يملأ الفجوة
    // بين عيّنتين لم تُقرأا معًا يخترع قياسًا لا وجود له.
    var open = ArrayList<Offset>()
    val segments = ArrayList<List<Offset>>()
    for (point in points) {
        if (point == null) {
            if (open.size > 1) segments.add(open)
            open = ArrayList()
        } else {
            open.add(point)
        }
    }
    if (open.size > 1) segments.add(open)

    for (segment in segments) {
        val line = smoothPath(segment)
        if (fill) {
            val area = Path()
            area.addPath(line)
            area.lineTo(segment.last().x, size.height)
            area.lineTo(segment.first().x, size.height)
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
    }
    if (marker) {
        val last = points.lastOrNull { it != null }
        if (last != null) {
            drawCircle(color.copy(alpha = .25f), radius = strokeWidth * 3.2f, center = last)
            drawCircle(color, radius = strokeWidth * 1.3f, center = last)
        }
    }
}

/** Full-width smoothed plot with grid, used where two series must be compared. */
@Composable
fun NeuralAreaPlot(
    values: List<Float?>,
    accent: Color,
    modifier: Modifier = Modifier,
    secondary: List<Float?> = emptyList(),
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

// ---------------------------------------------------------------- rings


// ---------------------------------------------------------------- readouts

/**
 * مقياس تردد: القراءة الحالية، وسقفها، ومدرّج بينهما.
 *
 * وهذا هو «شريط التردد» الذي كان على الشاشة ثم سقط إلى سطر نصّي: الرقم وحده يقول ٢.٤ جيجاهرتز،
 * ولا يقول إن ذلك **قريب من السقف أم بعيد عنه** — وهي المعلومة التي تُتّخذ بها قرار (أهذا
 * الجهاز يخنق نفسه حرارياً أم يعمل بطبيعته؟). والمدرّج يجيبها بلا حساب في الرأس، ونقاط
 * الفصل فيه تجعله يُقرأ كمدارج تردد معروفة في أدوات النواة.
 *
 * وأربع حالات تُرسم مختلفات، لا ثلاث:
 *  - **مقروء**: تعبئة بنسبة القراءة/السقف؛
 *  - **سقف غير معلَن**: النسبة `null` ⇒ **لا تعبئة**، والرقم يبقى معروضًا (قراءة حقيقية
 *    بلا مقياس — ولا يُخترع سقف من أعلى قيمة رآها التطبيق)؛
 *  - **لا قراءة**: الرقم `—` والمدرّج فارغ؛
 *  - و**التعبئة تُقلَّص إلى عرض المدرّج** حتى لا يتجاوز غطاء التردد حدَّ الشريط.
 *
 * وفي RTL تبدأ التعبئة من اليمين: المقياس يقيس اتجاه القراءة لا اتجاه الكود.
 */
@Composable
fun NeuralFrequencyMeter(
    reading: String,
    ceiling: String?,
    fraction: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    steps: Int = ClockMeter.STEPS,
) {
    val p = neuralPalette()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fill by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "neural-frequency-fill",
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralValue(
                reading,
                style = MonoValueStyleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = if (fraction == null) p.muted else accent,
            )
            Spacer(Modifier.weight(1f))
            if (ceiling != null) {
                NeuralValue(
                    ceiling,
                    style = MonoValueStyleSmall.copy(fontSize = 9.sp),
                    color = p.muted,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = p.grid, size = size, cornerRadius = corner)
            val width = size.width * fill
            if (width > 0f) {
                // قراءة موجبة تبقى مرئية ولو كانت نسبتها جزءًا من مقطع واحد.
                val lit = width.coerceAtLeast(size.height)
                val left = if (rtl) size.width - lit else 0f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = .85f), accent),
                        startX = left,
                        endX = left + lit,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(lit, size.height),
                    cornerRadius = corner,
                )
            }
            if (steps > 1) {
                for (step in 1 until steps) {
                    val x = size.width * step / steps
                    drawLine(p.tile, Offset(x, 0f), Offset(x, size.height), 1f)
                }
            }
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
    // حوض غائر لا شريط لاصق: حدّ رفيع حول المسار يجعل التعبئة تبدو **داخله** — وهي نفس
    // مفردات العمق التي تحملها الألواح، بلغة ٦dp.
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(p.grid)
            .border(BorderStroke(1.dp, p.border.copy(alpha = .45f)), CircleShape)
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
                fontSize = 12.sp,
                lineHeight = 15.sp,
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

/**
 * Caption plus value, the smallest readout unit.
 *
 * The optional [onClick] exists because these tiles are the app's metric
 * shortcuts (battery → charging center and friends): making the *readout* the
 * tap target beats adding a chevron that would only steal width from the value.
 */
@Composable
fun NeuralFactTile(
    caption: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    NeuralTile(
        modifier,
        onClick = onClick,
        verticalSpacing = 4.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        NeuralCaption(caption, color = accent)
        // الرقم أكبر من اسمه بدرجتين: هو المعلومة، والاسم تسمية لها. 14sp هو ما يسع
        // «1080×2400» في ثلث العرض بلا قصّ (قاسها `NeuralValue` بـLTR مثبّت).
        NeuralValue(value, style = MonoValueStyleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = p.text)
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


