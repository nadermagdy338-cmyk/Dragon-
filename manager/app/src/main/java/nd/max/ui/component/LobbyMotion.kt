/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * حركة اللوبي: كل الحلقات المستمرة تقرأ حالتها في مرحلة الرسم/الطبقة فقط (لا إعادة تركيب)،
 * وكلها تتوقف حين يكون مقياس مدّة الأنيميشن في النظام صفرًا (`animate = false`).
 */
private const val EMBERS = 26
private val EmberSeeds = FloatArray(EMBERS * 3).also { seeds ->
    val random = Random(11)
    for (i in seeds.indices) seeds[i] = random.nextFloat()
}
private val EmberWarm = Color(0xFFFFA24A)

/** حلقة زمنية 0..1؛ دون أنيميشن تثبت على `rest`. */
@Composable
internal fun lobbyLoop(
    animate: Boolean,
    ms: Int,
    mode: RepeatMode = RepeatMode.Restart,
    easing: Easing = LinearEasing,
    rest: Float = 0f
): State<Float> {
    if (!animate) return remember { mutableStateOf(rest) }
    return rememberInfiniteTransition(label = "lobbyLoop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ms, easing = easing), mode),
        label = "lobbyLoopValue"
    )
}

/** مقدّمة الشاشة 0→1 بزنبرك ناعم؛ تُقرأ داخل [lobbyIntro] فقط. */
@Composable
fun rememberLobbyIntro(animate: Boolean): State<Float> {
    val intro = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) intro.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 85f))
    }
    return intro.asState()
}

@Composable
fun rememberLobbySheenPhase(ms: Int = 3600): State<Float> =
    lobbyLoop(rememberLobbyAnimationsEnabled(), ms, easing = FastOutSlowInEasing)

/**
 * دخول متعاقب: ينزلق العنصر من جهة البداية (`fromStart` سالب = من النهاية) أو من الأسفل (`fromBelow`)،
 * ويظهر ويكبر بحسب تقدّم المقدّمة بعد التأخّر النسبي `start`. يراعي RTL.
 */
@Composable
fun Modifier.lobbyIntro(
    progress: State<Float>,
    fromStart: Dp = 0.dp,
    fromBelow: Dp = 0.dp,
    start: Float = 0f,
    scaleFrom: Float = 1f
): Modifier {
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    return graphicsLayer {
        val p = ((progress.value - start) / (1f - start)).coerceAtLeast(0f)
        val c = p.coerceIn(0f, 1f)
        alpha = c
        translationX = sign * (1f - p) * fromStart.toPx()
        translationY = (1f - p) * fromBelow.toPx()
        val k = scaleFrom + (1f - scaleFrom) * c
        scaleX = k
        scaleY = k
    }
}

/** ضغط زنبركي: ينكمش عند اللمس ويرتدّ عند الرفع. */
@Composable
fun Modifier.lobbyPress(interaction: MutableInteractionSource, pressedScale: Float = 0.92f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "lobbyPress"
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** لمعة قطرية تعبر العنصر؛ تبقى خارج الإطار (غير مرئية) ما دام `phase` عند الصفر. */
fun Modifier.lobbySheen(phase: State<Float>, strength: Float = 0.26f): Modifier = drawWithContent {
    drawContent()
    val band = size.height * 1.6f
    val x = (phase.value * 1.5f - 0.25f) * (size.width + band) - band * 0.5f
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
            start = Offset(x - band * 0.5f, 0f),
            end = Offset(x + band * 0.5f, size.height)
        )
    )
}

/** لوح التحديد: يتمدّد من جهة البداية خلف الصف المحدّد بحسب `sel` (0..1). */
fun Modifier.lobbySelectionPlate(sel: State<Float>): Modifier = drawBehind {
    val a = sel.value.coerceIn(0f, 1f)
    if (a <= 0.01f) return@drawBehind
    val rtl = layoutDirection == LayoutDirection.Rtl
    val reach = size.width * 0.9f * a
    drawRect(
        Brush.horizontalGradient(
            colors = listOf(LobbyPalette.Red.copy(alpha = 0.28f * a), Color.Transparent),
            startX = if (rtl) size.width else 0f,
            endX = if (rtl) size.width - reach else reach
        )
    )
}

/**
 * مفاعل اللوبي: الهالة تتنفّس، ومسح راداري يدور داخل القرص، ونبضة حلقية دورية، وجمرات تصعد،
 * وعند تبدّل اللعبة (`pulseKey`) موجة صدمة مع ارتداد خفيف للّوحة كلّها.
 */
@Composable
fun LobbyReactor(modifier: Modifier = Modifier, animate: Boolean = true, pulseKey: Any? = null) {
    val breathe = lobbyLoop(animate, 2800, RepeatMode.Reverse, FastOutSlowInEasing, rest = 0.5f)
    val radar = lobbyLoop(animate, 6400)
    val beat = lobbyLoop(animate, 3400, easing = LinearOutSlowInEasing)
    val drift = lobbyLoop(animate, 18_000)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (animate) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(1250, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier.graphicsLayer {
            val k = 1f + 0.03f * (1f - pulse.value) * (1f - pulse.value)
            scaleX = k
            scaleY = k
        }
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.55f + 0.45f * breathe.value
                val k = 0.95f + 0.07f * breathe.value
                scaleX = k
                scaleY = k
            }
        ) {
            val r = size.minDimension * 0.66f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(LobbyPalette.RedBright.copy(alpha = 0.30f), LobbyPalette.Red.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r
            )
        }
        LobbyEmblem(Modifier.fillMaxSize(), animate)
        if (animate) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = radar.value * 360f }) {
                val r = size.minDimension * 0.275f
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.74f to Color.Transparent,
                        1f to LobbyPalette.Red.copy(alpha = 0.36f),
                        center = center
                    ),
                    radius = r
                )
                drawLine(LobbyPalette.RedBright.copy(alpha = 0.55f), center, Offset(center.x + r, center.y), 1.5.dp.toPx())
            }
            Canvas(Modifier.fillMaxSize()) {
                val m = size.minDimension
                val b = beat.value
                drawCircle(
                    LobbyPalette.Red.copy(alpha = (1f - b) * (1f - b) * 0.45f),
                    m * (0.285f + 0.19f * b),
                    center,
                    style = Stroke(1.6.dp.toPx())
                )
                val p = pulse.value
                if (p < 1f) {
                    val e = 1f - (1f - p) * (1f - p)
                    drawCircle(
                        LobbyPalette.RedBright.copy(alpha = (1f - p) * 0.6f),
                        m * (0.28f + 0.30f * e),
                        center,
                        style = Stroke(3.dp.toPx() * (1f - p) + 1.dp.toPx())
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(LobbyPalette.Red.copy(alpha = 0.35f * (1f - p)), Color.Transparent),
                            center = center,
                            radius = m * 0.3f
                        ),
                        radius = m * 0.3f,
                        center = center
                    )
                }
                val t = drift.value
                for (i in 0 until EMBERS) {
                    val s = i * 3
                    val phase = (t * (1 + i % 3) + EmberSeeds[s]) % 1f
                    val x = center.x + ((EmberSeeds[s + 1] - 0.5f) * 1.05f + 0.03f * sin(phase * 8.1f + i)) * m
                    val y = center.y + (0.46f - phase * 0.95f) * m
                    val fade = sin(PI.toFloat() * phase)
                    drawCircle(
                        lerp(LobbyPalette.RedBright, EmberWarm, EmberSeeds[s + 2]).copy(alpha = 0.75f * fade),
                        (0.003f + EmberSeeds[s + 2] * 0.006f) * m,
                        Offset(x, y)
                    )
                }
            }
        }
    }
}
