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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import nd.max.ui.design.MaxDuration

/*
 * حركة اللوبي. ميزانيتها من `MaxDuration` (أطول انتقال ٣٦٠ms)، وحلقة مستمرّة **واحدة** فقط في
 * الشاشة كلّها (تنفّس حافة البطاقة وزرّ «ابدأ» معًا). كل حالة تُقرأ في مرحلة الرسم/الطبقة لا
 * التركيب، وكلها تتوقف حين يكون مقياس مدّة الأنيميشن في النظام صفرًا (`animate = false`).
 */

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

/** مقدّمة الشاشة 0→1 في [MaxDuration.deliberate]؛ تُقرأ داخل [lobbyIntro] فقط. لحظة واحدة منسَّقة لا حركة في كل عنصر. */
@Composable
fun rememberLobbyIntro(animate: Boolean): State<Float> {
    val intro = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) intro.animateTo(1f, tween(MaxDuration.deliberate, easing = FastOutSlowInEasing))
    }
    return intro.asState()
}

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

/** ضغط زنبركي: ينكمش عند اللمس ويرتدّ عند الرفع — استجابة لفعل المستخدم لا حركة تلقائية. */
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
