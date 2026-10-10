/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager motion system.
 * Motion is purposeful: short entrance choreography, responsive state changes,
 * and restrained springs. Keep motion subtle enough that hardware data remains
 * the visual priority.
 */
package nd.max.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings

object MaxMotion {
    const val fast = 160
    const val standard = 260
    const val emphasis = 420

    /** Snappier spring for small controls (switches, pills, chevrons). */
    val controlSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val contentSpring = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Spring with a touch of overshoot for needle-like value changes. */
    val needleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun enter(delayMillis: Int = 0): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = standard,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = standard,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            ),
            initialOffsetY = { it / 14 }
        )

    val exit: ExitTransition =
        fadeOut(animationSpec = tween(fast, easing = FastOutSlowInEasing)) +
            slideOutVertically(
                animationSpec = tween(fast, easing = FastOutSlowInEasing),
                targetOffsetY = { -it / 18 }
            )
}

/**
 * هل أوقف المستخدم حركات النظام؟ (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`).
 *
 * يُقرأ مرّة عند التركيب لا في كل إطار: هو تفضيل نظام يُقلَّب في الإعدادات، ومن غيّره يعيد
 * فتح الشاشة فيُعاد التركيب. والقراءة مغلّفة بـ`runCatching` لأن مزوّد الإعدادات قد يحجب
 * المفتاح على بعض البنيات — وحجبُه ليس «الحركة مطلوبة»، بل «لا نعرف»، والمجهول هنا يُحلّ إلى
 * السلوك المعتاد لا إلى إسقاط حركة لم يطلبها أحد.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) != 0f
        }.getOrDefault(true)
    }
}

/**
 * A restrained reveal used for dashboard blocks and settings groups.
 *
 * **ويحترم إيقاف الحركة في النظام:** من أوقف الحركات (`ANIMATOR_DURATION_SCALE = 0`) يُعرض له
 * المحتوى كما هو بلا fade ولا إزاحة — لا «حركة أسرع». وهذا شرط خطة المستوى (`DESIGN.md`:
 * "احترم Reduce Motion")، وكان غير مطبَّق: الـAPI كان يُنادي `animateTo` بلا سؤال.
 */
@Composable
fun MaxReveal(
    visible: Boolean = true,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!rememberAnimationsEnabled()) {
        if (visible) content()
        return
    }
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            entrance.animateTo(
                1f,
                tween(MaxMotion.standard, delayMillis.coerceIn(0, 160), FastOutSlowInEasing)
            )
        } else {
            entrance.snapTo(0f)
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * 12.dp.toPx()
        },
        enter = EnterTransition.None,
        exit = MaxMotion.exit
    ) {
        content()
    }
}


/** Responsive press feedback shared by interactive MaxManager surfaces. */
@Composable
fun Modifier.maxPressMotion(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaxMotion.controlSpring,
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Tiny settle motion for values that change without changing layout. */
fun Modifier.maxValueMotion(scale: Float = 1f, alpha: Float = 1f): Modifier =
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }

/**
 * شعاع مسح ليزري راداري (LiDAR / Telemetry Laser Beam) للأسطح التي تجري فيها عمليات فحص أو تحديث نشطة.
 *
 * يرسم مسحاً أفقياً ناعماً بتوهج نيون يتلاشى للأعلى مع خط ليزري رئيسي، متوافقاً مع [rememberAnimationsEnabled].
 */
@Composable
fun Modifier.maxLiDARScan(
    active: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary,
    durationMillis: Int = 1800
): Modifier {
    if (!active || !rememberAnimationsEnabled()) return this

    val transition = rememberInfiniteTransition(label = "lidarScan")
    val progress: Float = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lidarProgress"
    ).value

    return this.drawWithContent {
        drawContent()
        val y = size.height * progress
        val beamHeight = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.06f),
                    accent.copy(alpha = 0.25f),
                    accent.copy(alpha = 0.65f)
                ),
                startY = (y - beamHeight).coerceAtLeast(0f),
                endY = y
            ),
            topLeft = Offset(0f, (y - beamHeight).coerceAtLeast(0f)),
            size = Size(size.width, beamHeight.coerceAtMost(y))
        )
        drawLine(
            color = accent,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

