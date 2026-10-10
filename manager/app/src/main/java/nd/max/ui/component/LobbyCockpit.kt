/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/*
 * مسرح اللوبي «ذو الأجنحة»: خلفية كحليّة بأجنحة مائلة وخلايا سداسية في الأركان، أيقونتا الزاويتين
 * العلويّتين (شبكة · ثلاث نقاط)، وأزرار الصفّ السفليّ السداسية. كل الرسم بالكود — لا صورة ولا
 * شعار ولا خط منقول — والأجنحة والخلايا زخرفة صِرفة لا تحمل معنى ولا بيانات.
 *
 * مواضع الأجنحة والخلايا **نِسَب من عرض الشاشة وارتفاعها** لا إحداثيات مطلقة، فتتبع الشاشة مهما
 * اختلف المقاس. وهي مأخوذة بالعين من اللقطة المرجعية، فمقارنتها بصريًّا على الجهاز لازمة.
 */

// ───────────────────────────── مسار الشكل ─────────────────────────────

/** مسار [shape] بقياس الرسم الحاليّ (للتوهّج الذي يُرسم خارج القصّ). */
internal fun DrawScope.outlineOf(shape: Shape): Path =
    (shape.createOutline(size, layoutDirection, this) as? Outline.Generic)?.path
        ?: Path().apply { addRect(Rect(Offset.Zero, size)) }

// ───────────────────────────── الخلفية والأجنحة ─────────────────────────────

/** جناح مائل: من طرفه الخارجيّ إلى الداخليّ (نحو مركز الشاشة)، كلها نسب من العرض/الارتفاع. */
private class Wing(val outerX: Float, val outerY: Float, val innerX: Float, val innerY: Float, val thickness: Float)

private val CockpitWings = listOf(
    Wing(0.135f, 0.205f, 0.268f, 0.160f, 0.020f),
    Wing(0.200f, 0.150f, 0.268f, 0.126f, 0.014f),
    Wing(0.070f, 0.745f, 0.185f, 0.783f, 0.020f),
    Wing(0.100f, 0.805f, 0.185f, 0.833f, 0.014f)
)

/** خلايا قرص العسل: (عمود، صفّ) بوحدة نصف القطر، تبدأ من زاوية الشاشة السفلى. */
private val HexCells = listOf(
    Offset(0f, 0f), Offset(3f, 0f),
    Offset(1.5f, 0.866f), Offset(4.5f, 0.866f),
    Offset(0f, 1.732f)
)

private fun hexPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(60.0 * i).toFloat()
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawWing(wing: Wing, mirror: Boolean) {
    val w = size.width
    val h = size.height
    fun x(fraction: Float): Float = if (mirror) w - fraction * w else fraction * w
    val ox = x(wing.outerX)
    val oy = wing.outerY * h
    val ix = x(wing.innerX)
    val iy = wing.innerY * h
    val t = wing.thickness * h
    val path = Path().apply {
        moveTo(ox, oy)
        lineTo(ix, iy)
        lineTo(ix, iy + t)
        lineTo(ox, oy + t)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(LobbyPalette.Steel.copy(alpha = 0.08f), LobbyPalette.Steel.copy(alpha = 0.62f)),
            start = Offset(ox, oy),
            end = Offset(ix, iy)
        )
    )
    // حافّة علوية لامعة تُظهر سُمك الجناح كأنه معدن مشطوف.
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, LobbyPalette.Steel.copy(alpha = 0.90f)),
            start = Offset(ox, oy),
            end = Offset(ix, iy)
        ),
        start = Offset(ox, oy),
        end = Offset(ix, iy),
        strokeWidth = MaxSize.hairlineBorder.toPx()
    )
}

private fun DrawScope.drawHexCluster(mirror: Boolean) {
    val w = size.width
    val h = size.height
    val r = w * 0.019f
    val pitch = 1.06f
    val anchorX = w * 0.045f
    val anchorY = h * 0.885f
    HexCells.forEachIndexed { index, cell ->
        val baseX = anchorX + cell.x * r * pitch
        val cx = if (mirror) w - baseX else baseX
        val cy = anchorY + cell.y * r * pitch
        val fade = 1f - index * 0.14f
        val path = hexPath(cx, cy, r)
        drawPath(path, LobbyPalette.Steel.copy(alpha = 0.10f * fade))
        drawPath(path, LobbyPalette.Steel.copy(alpha = 0.30f * fade), style = Stroke(width = MaxSize.hairlineBorder.toPx()))
    }
}

private fun DrawScope.drawCockpitFrame() {
    CockpitWings.forEach { wing ->
        drawWing(wing, mirror = false)
        drawWing(wing, mirror = true)
    }
    drawHexCluster(mirror = false)
    drawHexCluster(mirror = true)
}

/**
 * خلفية المسرح: كحليّ داكن بتوهّج أزرق خلف البطاقة المركزية وتعتيم علويّ/سفليّ، تعلوها الأجنحة
 * المائلة والخلايا السداسية. ثابتة لا تتبدّل بتبدّل اللعبة (اللون يأتي من البطاقة نفسها).
 */
@Composable
fun LobbyStageBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(LobbyPalette.Navy)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            LobbyPalette.NavyGlow.copy(alpha = 0.50f),
                            LobbyPalette.NavyGlow.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.52f),
                        radius = size.width * 0.6f
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.50f))
                    )
                )
                drawCockpitFrame()
            }
    )
}

// ───────────────────────────── أيقونتا الشريط العلويّ ─────────────────────────────

/** شبكة ٢×٢ من مربّعات مجوَّفة — فتح إدارة الألعاب. */
@Composable
fun LobbyGridButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MaxSize.minTouchTarget)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(MaxSize.iconGlyph)) {
            val stroke = Stroke(width = MaxSize.emphasisBorder.toPx())
            val inset = stroke.width / 2f
            val cell = size.width * 0.42f
            val gap = size.width - cell * 2f
            for (row in 0..1) {
                for (col in 0..1) {
                    drawRoundRect(
                        color = LobbyPalette.Ink,
                        topLeft = Offset(col * (cell + gap) + inset, row * (cell + gap) + inset),
                        size = Size(cell - stroke.width, cell - stroke.width),
                        cornerRadius = CornerRadius(cell * 0.18f),
                        style = stroke
                    )
                }
            }
        }
    }
}

/** عنصر في قائمة النقاط الثلاث. */
class LobbyMenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

private val MenuMinWidth = 200.dp

/** ثلاث نقاط رأسية — النقر يفتح [LobbyMenuOverlay] التي تملك الشاشة حالتها. */
@Composable
fun LobbyMoreButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MaxSize.minTouchTarget)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(MaxSize.iconGlyph)) {
            val radius = size.width * 0.095f
            listOf(0.18f, 0.5f, 0.82f).forEach { fraction ->
                drawCircle(color = LobbyPalette.Ink, radius = radius, center = Offset(size.width / 2f, size.height * fraction))
            }
        }
    }
}

/**
 * قائمة النقاط الثلاث: **طبقة داخل التركيب نفسه** لا نافذة `Popup`/`Dialog` — لأن اللوبي يُخفي أشرطة
 * النظام، والنوافذ المنفصلة قد تُظهرها لحظةً على بعض الأجهزة. شاشة شفّافة تغلق القائمة بالنقر خارجها،
 * ورجوع النظام يغلقها أولًا. [top] مسافة اللوحة من أعلى المنطقة الآمنة (أسفل زرّ النقاط).
 */
@Composable
fun LobbyMenuOverlay(items: List<LobbyMenuItem>, top: Dp, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDismiss)
    val shape = remember { LobbyChamferShape(MaxSpace.sm) }
    Box(
        modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = top, end = MaxSpace.xxl)
                .shadow(MaxSpace.sm, shape)
                .widthIn(min = MenuMinWidth)
                .width(IntrinsicSize.Max)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(LobbyPalette.PanelRaised, LobbyPalette.Surface)))
                .border(MaxSize.hairlineBorder, LobbyPalette.Hairline, shape)
                .padding(vertical = MaxSpace.xs)
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MaxSize.minTouchTarget)
                        .clickable(role = Role.Button, onClick = {
                            onDismiss()
                            item.onClick()
                        })
                        .padding(horizontal = MaxSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = LobbyPalette.Muted,
                        modifier = Modifier.size(MaxSize.iconGlyphSmall)
                    )
                    Text(text = item.label, color = LobbyPalette.Ink, fontSize = 14.sp)
                }
            }
        }
    }
}

// ───────────────────────────── الأزرار السداسية ─────────────────────────────

/** لون الزرّ السداسي: أزرق للثانويّ، وأحمر للإجراء الأوحد المتوهّج («ابدأ»). */
enum class LobbyHexTone { Blue, Red }

private val HexGlowWide = 14.dp
private val HexGlowMid = 8.dp
private val HexGlowNear = 4.dp

/** نسبة عرض الشريط المضيء أسفل الزرّ من عرضه. */
private const val ACCENT_BAR_SHARE = 0.30f

/**
 * زرّ سداسيّ مفلطح: أيقونة فوق الاسم (للأزرق)، حدّ ملوَّن وتوهّج خارجيّ، وشريط مضيء قصير عند
 * الحافة السفلى. الحالة تُكتب **نصًّا** في [value] لا لونًا وحده (إتاحة)؛ و[active] يُضيء الحدّ
 * والتوهّج والشريط. [breathe] اختياريّ: يُنفّس توهّج زرّ «ابدأ» مع حافة البطاقة (الحلقة الوحيدة).
 */
@Composable
fun LobbyHexButton(
    title: String,
    tone: LobbyHexTone,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    active: Boolean = true,
    breathe: State<Float>? = null,
    onClickLabel: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val red = tone == LobbyHexTone.Red
    val edge = if (red) LobbyPalette.RedBright else LobbyPalette.Blue
    val accent = if (red) LobbyPalette.RedBright else LobbyPalette.BlueBright
    val fill = if (red) {
        Brush.verticalGradient(listOf(LobbyPalette.RedDeep, LobbyPalette.Red))
    } else {
        Brush.verticalGradient(listOf(LobbyPalette.BlueDeep, LobbyPalette.Navy))
    }
    Box(
        modifier = modifier
            .lobbyPress(interaction, 0.95f)
            .size(width = width, height = height)
            .drawBehind {
                val pulse = breathe?.value ?: 0.5f
                val glow = (if (active) 1f else 0.45f) * (0.6f + 0.4f * pulse)
                val path = outlineOf(LobbyHexButtonShape)
                drawPath(path, edge.copy(alpha = 0.10f * glow), style = Stroke(width = HexGlowWide.toPx(), join = StrokeJoin.Round))
                drawPath(path, edge.copy(alpha = 0.22f * glow), style = Stroke(width = HexGlowMid.toPx(), join = StrokeJoin.Round))
                drawPath(path, edge.copy(alpha = 0.40f * glow), style = Stroke(width = HexGlowNear.toPx(), join = StrokeJoin.Round))
            }
            .clip(LobbyHexButtonShape)
            .background(fill)
            .border(MaxSize.emphasisBorder, edge.copy(alpha = if (active) 0.95f else 0.60f), LobbyHexButtonShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick
            )
            .drawWithContent {
                drawContent()
                val a = if (active) 1f else 0.5f
                val barW = size.width * ACCENT_BAR_SHARE
                val barH = MaxSpace.hairline.toPx()
                val x = (size.width - barW) / 2f
                val y = size.height - barH - MaxSpace.xs.toPx()
                drawRect(accent.copy(alpha = 0.35f * a), Offset(x - barH * 2f, y - barH * 2f), Size(barW + barH * 4f, barH * 5f))
                drawRect(accent.copy(alpha = a), Offset(x, y), Size(barW, barH))
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = height * 0.4f).padding(top = MaxSpace.xs, bottom = MaxSpace.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
            }
            Text(
                text = title,
                color = LobbyPalette.Ink,
                fontSize = if (red) 17.sp else 12.sp,
                lineHeight = if (red) 21.sp else 14.sp,
                fontWeight = if (red) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = if (red) 1 else 2
            )
            if (value != null) {
                Text(
                    text = value,
                    color = if (active) accent else LobbyPalette.Muted,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
