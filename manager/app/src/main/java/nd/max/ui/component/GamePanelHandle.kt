/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import nd.max.R
import nd.max.core.gamespace.PanelSide
import nd.max.ui.overlay.HandleFx

private val BodyW = 16.dp
private val BodyH = 108.dp
private val Room = 12.dp
private val HandleInk = Color(0xFFF4F5F7)
private val ShellInner = Color(0xF2141820)
private val ShellEdge = Color(0xE60B0D11)

/**
 * مقبض اللوحة على حافة الشاشة بنمط «شريط السحب»: نصف كبسولة داكنة بشريط أبيض رأسي.
 * حيّ دائمًا: هالة تتنفّس، وضوء يجري حول الحافة وعلى الشريط، وانزلاق دخول بزنبرك، ويخفت بعد خمول.
 * وعند اللمس/السحب: ضغط، تمدّد بحسب السرعة مع أثر، وسهم + هالة تشتدّ حتى «التسليح» عند عتبة الفتح.
 * كل الرسم يقرأ الحالة في مرحلة الرسم فقط؛ اللمس نفسه تعالجه [nd.max.ui.overlay.SilkDragController].
 */
@Composable
internal fun GamePanelHandle(
    side: PanelSide,
    gameLabel: String,
    accent: Color,
    hot: Boolean,
    fx: HandleFx?,
    onOpen: () -> Unit
) {
    val openLabel = stringResource(R.string.game_panel_open_cd)
    val end = side == PanelSide.End
    val appear = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val touches = fx?.touches ?: 0
    val settles = fx?.settles ?: 0
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 150f)) }
    LaunchedEffect(touches) {
        dim.animateTo(0f, tween(160))
        delay(3400)
        dim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(settles) {
        if (settles > 0) {
            pop.snapTo(1f)
            pop.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 260f))
        }
    }
    val press = animateFloatAsState(
        targetValue = if (fx?.pressed == true) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "handlePress"
    )
    val clock = rememberInfiniteTransition(label = "handle")
    val breathe = clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (hot) 850 else 2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "handleBreathe"
    )
    val flow = clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "handleFlow"
    )
    Box(
        Modifier
            .size(BodyW + Room, BodyH + Room * 2)
            .graphicsLayer { alpha = (1f - 0.42f * dim.value) * appear.value.coerceIn(0f, 1f) }
            .semantics {
                contentDescription = "$openLabel · $gameLabel"
                onClick(label = openLabel) {
                    onOpen()
                    true
                }
            }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val bw = BodyW.toPx()
                val bh = BodyH.toPx()
                val dir = if (end) -1f else 1f
                val edge = if (end) w else 0f
                val left = if (end) w - bw else 0f
                val top = (h - bh) / 2f
                val round = CornerRadius(bw * 0.95f, bw * 0.95f)
                val flat = CornerRadius.Zero
                val body = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left, top, left + bw, top + bh,
                            topLeftCornerRadius = if (end) round else flat,
                            topRightCornerRadius = if (end) flat else round,
                            bottomRightCornerRadius = if (end) flat else round,
                            bottomLeftCornerRadius = if (end) round else flat
                        )
                    )
                }
                val measure = PathMeasure().also { it.setPath(body, false) }
                val length = measure.length
                val seg = Path()
                val shell = Brush.horizontalGradient(
                    colors = if (end) listOf(ShellInner, ShellEdge) else listOf(ShellEdge, ShellInner),
                    startX = left,
                    endX = left + bw
                )
                val hairline = 1.dp.toPx()
                val runnerWidth = 1.6.dp.toPx()
                val barW0 = 3.6.dp.toPx()
                val barH0 = 38.dp.toPx()
                val ghostStep = 9.dp.toPx()
                val streakLen = 18.dp.toPx()
                val hide = bw + 4.dp.toPx()
                onDrawBehind {
                    val speed = fx?.speed ?: 0f
                    val fast = abs(speed)
                    val pull = fx?.pull ?: 0f
                    val armed = fx?.armed == true
                    val energy = (fast * 0.6f + pull * 0.9f + press.value * 0.5f).coerceIn(0f, 1f)
                    val b = breathe.value
                    val slide = -dir * hide * (1f - appear.value)
                    val sy = 1f + 0.10f * fast + 0.04f * press.value + 0.06f * pop.value
                    val sx = 1f + 0.22f * press.value + 0.30f * pop.value - 0.08f * fast + if (armed) 0.18f else 0f
                    withTransform({
                        translate(left = slide, top = 0f)
                        scale(scaleX = sx, scaleY = sy, pivot = Offset(edge, cy))
                    }) {
                        val haloAlpha = (0.10f + 0.16f * b + 0.45f * energy + if (armed) 0.22f else 0f).coerceIn(0f, 0.85f)
                        val haloCenter = Offset(edge + dir * bw * 0.35f, cy)
                        val reachX = (bw * 0.65f + Room.toPx()) * (0.86f + 0.14f * energy)
                        withTransform({ scale(1f, 2.3f, haloCenter) }) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(accent.copy(alpha = haloAlpha), Color.Transparent),
                                    center = haloCenter,
                                    radius = reachX
                                ),
                                radius = reachX,
                                center = haloCenter
                            )
                        }
                        drawPath(body, shell)
                        drawPath(
                            body,
                            accent.copy(alpha = (0.32f + 0.12f * b + 0.5f * energy).coerceAtMost(1f)),
                            style = Stroke(hairline)
                        )
                        seg.rewind()
                        val s0 = flow.value * length
                        val s1 = s0 + length * 0.16f
                        measure.getSegment(s0, minOf(s1, length), seg, true)
                        if (s1 > length) measure.getSegment(0f, s1 - length, seg, true)
                        drawPath(
                            seg,
                            HandleInk.copy(alpha = 0.5f + 0.4f * energy),
                            style = Stroke(runnerWidth, cap = StrokeCap.Round)
                        )
                        val barX = edge + dir * bw * 0.52f
                        val barW = barW0 * (1f + 0.5f * press.value + 0.4f * energy)
                        val barH = barH0 * (1f + 0.35f * press.value + 0.5f * fast + 0.2f * pull)
                        val barTop = cy - barH / 2f
                        val barColor = lerp(HandleInk, accent, pull.coerceIn(0f, 1f))
                        if (fast > 0.06f) {
                            for (i in 1..4) {
                                drawRoundRect(
                                    color = accent.copy(alpha = 0.30f * fast / i),
                                    topLeft = Offset(barX - barW / 2f, barTop - speed * i * ghostStep),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(barW / 2f)
                                )
                            }
                        }
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.16f + 0.30f * energy),
                            topLeft = Offset(barX - barW * 1.6f, barTop - barW),
                            size = Size(barW * 3.2f, barH + barW * 2f),
                            cornerRadius = CornerRadius(barW * 1.6f)
                        )
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.92f),
                            topLeft = Offset(barX - barW / 2f, barTop),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f)
                        )
                        val t = flow.value * 1.45f
                        if (t < 1f) {
                            val sTop = barTop - streakLen + (barH + streakLen) * t
                            clipRect(barX - barW / 2f, barTop, barX + barW / 2f, barTop + barH) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.95f), Color.Transparent),
                                        startY = sTop,
                                        endY = sTop + streakLen
                                    ),
                                    topLeft = Offset(barX - barW / 2f, sTop),
                                    size = Size(barW, streakLen)
                                )
                            }
                        }
                        if (pull > 0.12f) {
                            val k = if (armed) 1.25f else 1f
                            val cx = edge + dir * (bw + 5.dp.toPx())
                            val ch = 7.dp.toPx() * k
                            val cw = 3.5.dp.toPx() * k
                            val chevron = Path().apply {
                                moveTo(cx - dir * cw, cy - ch)
                                lineTo(cx + dir * cw, cy)
                                lineTo(cx - dir * cw, cy + ch)
                            }
                            drawPath(
                                chevron,
                                lerp(accent, Color.White, if (armed) 0.7f else 0f).copy(alpha = pull.coerceIn(0f, 1f)),
                                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
    )
}
