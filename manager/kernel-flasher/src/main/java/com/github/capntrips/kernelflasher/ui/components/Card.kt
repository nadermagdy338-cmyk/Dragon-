package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Card as Material3Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeBlurStyle

/**
 * This module's default card. When [glassTheme] is left at its default
 * (`isGlassActive = false`), behaviour is unchanged from before - a flat
 * elevated Material3 card. When the host app has glass/blur enabled with a
 * custom background, this renders the same frosted look as the rest of the
 * app (see [FlasherGlassCard]) instead of a stock opaque surface.
 */
@Composable
fun Card(
    shape: Shape = RoundedCornerShape(24.dp), // Lebih bulat (Expressive)
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer, // Warna M3 modern
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp, // Flat style dengan kontras warna
    shadowElevation: Dp = 2.dp,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme(),
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = glassTheme.isGlassActive && glassTheme.hazeState != null
    val resolvedContentColor = glassTheme.contentColor ?: contentColor

    if (isGlass) {
        val resolvedCardColor = glassTheme.cardColor ?: backgroundColor
        Material3Card(
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = resolvedContentColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(shape)
                .hazeEffect(
                    state = glassTheme.hazeState,
                    style = HazeBlurStyle(
                        backgroundColor = resolvedCardColor.copy(alpha = 0.5f),
                        blurRadius = 24.dp,
                        noiseFactor = 0.1f,
                        colorEffects = emptyList(),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                content = content
            )
        }
    } else {
        ElevatedCard(
            shape = shape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = backgroundColor,
                contentColor = resolvedContentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = shadowElevation
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp) // Sedikit spasi antar kartu
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp), // Padding internal lebih lega
                content = content
            )
        }
    }
}
