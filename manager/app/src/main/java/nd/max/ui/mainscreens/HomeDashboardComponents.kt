@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralTrack

/** Shared dashboard primitives now route through the same Neural visual language as Home. */
@Composable
fun DashSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.3.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 40) {
    NeuralIconChip(icon = icon, accent = tint, size = size.dp)
}

@Composable
fun LabelText(text: String, color: Color) {
    NeuralPill(text = text, accent = color, filled = true)
}

@Composable
fun DashCardWrapper(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NeuralPanel(
        modifier = modifier,
        accent = accent,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun GlowLinearBar(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 7.dp,
) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "dashboardProgress")
    NeuralTrack(
        fraction = progress,
        accent = accent,
        modifier = modifier,
        height = height,
    )
}
