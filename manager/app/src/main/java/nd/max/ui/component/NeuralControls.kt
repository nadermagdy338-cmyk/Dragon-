/**
 * Neural controls — the kit's interactive surfaces.
 *
 * Split from `NeuralDashboardKit.kt` on purpose: that file owns *readouts and
 * plots*, this one owns *controls*, and the kit crossed the repository's
 * oversized-file ceiling when the segmented picker moved in. The split is by
 * responsibility, not by size excuse — every symbol here speaks the same
 * palette, shapes and press feedback as the kit (via `neuralClickable`).
 */
package nd.max.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Segmented picker — one surface, a few states of a single choice (base
 * profile, activity-card scenes, …).
 *
 * Why a segmented picker and not a row of buttons: closely-related options
 * (three base profiles) are one decision with several states, and drawing them
 * as one control says exactly that. It also removes the confirm tap: the touch
 * *is* the switch. Selection moves by background color only, so the row never
 * reflows mid-switch, and the applied state is always the one with the lit
 * segment — the same rule the home profile rail relies on.
 *
 * [selectedIndex] of `-1` renders no lit segment: «no known state yet» must not
 * borrow one of the options as its face.
 */
@Composable
fun NeuralSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val p = neuralPalette()
    val tone = accent ?: p.accent
    Row(
        modifier
            .fillMaxWidth()
            .clip(NeuralTileShape)
            .background(p.tile)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(
                targetValue = if (selected) tone.copy(alpha = .16f) else Color.Transparent,
                animationSpec = tween(MaxMotion.fast, easing = FastOutSlowInEasing),
                label = "neural-segmented-fill",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(fill)
                    .border(
                        BorderStroke(1.dp, if (selected) tone.copy(alpha = .35f) else Color.Transparent),
                        RoundedCornerShape(11.dp),
                    )
                    .neuralClickable { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) tone else p.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Status dot. Pulses **only** while the state it represents is genuinely live
 * (the AI engine running); a resting state is a static dot — a pulsing dot for a
 * stopped engine would be decoration lying about the device.
 */
@Composable
fun NeuralLiveDot(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    val alpha = if (active) {
        val transition = rememberInfiniteTransition(label = "neural-live-dot")
        transition
            .animateFloat(
                initialValue = .45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
                label = "neural-live-dot-alpha",
            )
            .value
    } else {
        1f
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
}

/** Hairline rule between related sections of one panel. */
@Composable
fun NeuralDivider(modifier: Modifier = Modifier) {
    val p = neuralPalette()
    Box(modifier.fillMaxWidth().height(1.dp).background(p.border))
}
