/*
 * Ported from: nd.max.ui.flasher.StyledCard (app module)
 * Purpose: let the capntrips flasher screens (this module) render with the
 * same frosted-glass card look as the rest of the app instead of a stock
 * Material3 surface, when the user has a custom background + glass enabled.
 */
package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.HazeBlurStyle

/**
 * Theme values needed to render this module's screens in the app's glass
 * style. Every screen/composable in this module should accept this instead
 * of threading five separate parameters around.
 *
 * [isGlassActive] false (the default) renders a plain, opaque
 * [MaterialTheme]-colored card - i.e. today's stock look, so existing call
 * sites keep working unchanged.
 */
data class FlasherGlassTheme(
    val isGlassActive: Boolean = false,
    val hazeState: HazeState? = null,
    val cardColor: Color? = null,
    val contentColor: Color? = null,
    val primaryColor: Color? = null
)

@Composable
fun FlasherGlassCard(
    theme: FlasherGlassTheme,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    fallbackColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = if (cornerRadius != 24.dp) RoundedCornerShape(cornerRadius) else shape
    val active = theme.isGlassActive && theme.hazeState != null
    val resolvedCardColor = theme.cardColor ?: fallbackColor ?: Color.Transparent

    val glassModifier = if (active) {
        Modifier
            .clip(cardShape)
            .hazeEffect(
                state = theme.hazeState!!,
                style = HazeBlurStyle(
                    backgroundColor = resolvedCardColor.copy(alpha = 0.5f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    colorEffects = emptyList()
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), cardShape)
    } else {
        Modifier.clip(cardShape)
    }

    Card(
        modifier = modifier.then(glassModifier),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color.Transparent else (fallbackColor ?: Color.Transparent)
        )
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
