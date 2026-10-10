/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.animation.AnimatedContent
import nd.max.ui.design.MaxRadius
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * أيقونة الساعة الرملية الحركية المتحركة (Kinetic Flipping Hourglass) لحالات التحميل النشطة.
 *
 * تحترم تفضيل النظام (Reduce Motion) بدقة عبر [rememberAnimationsEnabled].
 * تنفذ دورة فيزيائية ناعمة (وقوف ← دوران ١٨٠° بانعطاف خفيف ورفع Scale ← وقوف ← دوران إلى ٣٦٠°)
 * مع نبض ضوئي هادئ لوعاء الأيقونة (Glow Halo)، مما يعطي إحساساً دقيقاً بجريان العمليات دون استهلاك موارد زائد.
 */
@Composable
fun MaxAnimatedHourglass(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 28.dp
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val rotation: Float
    val scale: Float
    val bgAlpha: Float

    if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "hourglassAnimation")
        rotation = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2200
                    0f at 0
                    0f at 500
                    180f at 1100 using FastOutSlowInEasing
                    180f at 1600
                    360f at 2200 using FastOutSlowInEasing
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "hourglassRotation"
        ).value

        scale = transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2200
                    1f at 0
                    1f at 500
                    1.16f at 800 using FastOutSlowInEasing
                    1f at 1100 using FastOutSlowInEasing
                    1f at 1600
                    1.16f at 1900 using FastOutSlowInEasing
                    1f at 2200 using FastOutSlowInEasing
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "hourglassScale"
        ).value

        bgAlpha = transition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hourglassGlow"
        ).value
    } else {
        rotation = 0f
        scale = 1f
        bgAlpha = 0.12f
    }

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(MaxRadius.tile),
        color = accent.copy(alpha = bgAlpha),
        border = BorderStroke(1.dp, accent.copy(alpha = (bgAlpha * 1.5f).coerceIn(0.14f, 0.35f))),
        modifier = modifier
    ) {
        Icon(
            Icons.Rounded.HourglassTop,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(14.dp)
                .size(size)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

/** Shared visual language for transient, empty and recoverable screen states. */
@Composable
fun MaxContentState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    MaxContentState(
        title = title,
        message = message,
        modifier = modifier,
        iconContent = {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(MaxRadius.tile),
                color = accent.copy(alpha = 0.12f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(14.dp).size(28.dp)
                )
            }
        },
        actionLabel = actionLabel,
        onAction = onAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction
    )
}

@Composable
fun MaxContentState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    MaxSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .maxStateContentWidth(),
        accent = accent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            iconContent()
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.maxHeadingSemantics(),
                textAlign = TextAlign.Center
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                nd.max.ui.component.StudioButton(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                nd.max.ui.component.StudioOutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
            }
        }
    }
}

@Composable
fun MaxLoadingState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        MaxContentState(
            title = title,
            message = message,
            modifier = modifier,
            iconContent = { MaxAnimatedHourglass() }
        )
    }
}

@Composable
fun MaxEmptyState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    MaxContentState(title, message, Icons.Rounded.SearchOff, modifier, actionLabel, onAction)
}

@Composable
fun MaxErrorState(title: String, message: String, retryLabel: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MaxContentState(title, message, Icons.Rounded.ErrorOutline, modifier, retryLabel, onRetry)
}

/**
 * انتقال بين حالتين بمحتوى **مُفتاح بالحالة**: اللامبدا تستقبل `targetState` وتُعيد محتواها.
 *
 * كان التوقيع `content: @Composable () -> Unit` يُهمل الوسيط، وهو ما يمنع `AnimatedContent` من رسم
 * انتقال صحيح: محتواها لا يتغيّر بتغيّر الحالة فيصير الانتقال بين شيئين متطابقين (وهذا نصّ بلاغ
 * `UnusedContentLambdaTargetStateParameter`). ولذلك يُمرَّر الوسيط صريحًا — وهو تغيير في عقد الدالة،
 * و**لا مستهلك لها اليوم** (بحث في المستودع: لا نداء خارج تعريفها)، فلا نداء يُكسَر.
 */
@Composable
fun MaxStateTransition(targetState: Any, content: @Composable (Any) -> Unit) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "maxContentState"
    ) { state -> content(state) }
}


private fun Modifier.maxStateContentWidth(): Modifier = widthIn(max = 520.dp)
