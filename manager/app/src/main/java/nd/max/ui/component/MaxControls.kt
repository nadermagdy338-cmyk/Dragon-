@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package nd.max.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/** Native gestures and semantics are retained; only the thumb responds to drag. */
@Composable
fun MaxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: SliderColors = SliderDefaults.colors()
) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalScreenAccent.current ?: scheme.primary
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (enabled && (dragged || pressed)) 1.12f else 1f, MaxMotion.controlSpring, label = "sliderThumb")
    val active by animateColorAsState(if (enabled) accent else scheme.onSurface.copy(alpha = .38f), label = "sliderAccent")
    val studioColors = SliderDefaults.colors(
        thumbColor = active,
        activeTrackColor = active,
        inactiveTrackColor = scheme.surfaceContainerHighest,
        activeTickColor = scheme.surface,
        inactiveTickColor = scheme.outline,
        disabledThumbColor = scheme.onSurface.copy(alpha = .38f),
        disabledActiveTrackColor = scheme.onSurface.copy(alpha = .20f),
        disabledInactiveTrackColor = scheme.onSurface.copy(alpha = .10f)
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = studioColors,
        thumb = {
            Surface(
                modifier = Modifier.size(width = 28.dp, height = 36.dp).scale(scale),
                shape = RoundedCornerShape(10.dp),
                color = scheme.surface,
                border = BorderStroke(2.dp, active),
                shadowElevation = if (dragged && enabled) 4.dp else 1.dp
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) {
                        Box(Modifier.width(2.dp).height(12.dp).clip(RoundedCornerShape(1.dp)).background(active))
                    }
                }
            }
        },
        track = { state ->
            SliderDefaults.Track(sliderState = state, enabled = enabled, colors = studioColors, modifier = Modifier.height(10.dp))
        }
    )
}

@Composable
fun MaxSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: SwitchColors = SwitchDefaults.colors()
) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalScreenAccent.current ?: scheme.primary
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        thumbContent = {
            if (thumbContent != null) {
                thumbContent()
            } else if (checked) {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
            } else {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(if (enabled) scheme.surface else scheme.onSurface.copy(alpha = .38f)))
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.surface,
            checkedTrackColor = accent,
            checkedBorderColor = accent,
            checkedIconColor = accent,
            uncheckedThumbColor = scheme.outline,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedBorderColor = scheme.outlineVariant,
            uncheckedIconColor = scheme.surface,
            disabledCheckedThumbColor = scheme.surface,
            disabledCheckedTrackColor = scheme.onSurface.copy(alpha = .12f),
            disabledUncheckedThumbColor = scheme.onSurface.copy(alpha = .38f),
            disabledUncheckedTrackColor = scheme.onSurface.copy(alpha = .08f)
        )
    )
}
