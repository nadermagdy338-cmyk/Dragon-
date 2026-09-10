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
import androidx.compose.ui.graphics.graphicsLayer

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

/** A restrained reveal used for dashboard blocks and settings groups. */
@Composable
fun MaxReveal(
    visible: Boolean = true,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
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
