@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.component.*
import java.util.Locale

fun formatNetSpeed(kbps: Long): String = when {
    kbps >= 1024 -> String.format(Locale.US, "%.1f MB/s", kbps / 1024f)
    else -> "$kbps KB/s"
}

@Composable
fun DashSectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 40) {
    Surface(modifier = Modifier.size(size.dp), shape = RoundedCornerShape((size * .34f).dp), color = tint.copy(alpha = .12f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * .52f).dp)) }
    }
}

@Composable
fun LabelText(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = .14f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun DashCardWrapper(modifier: Modifier = Modifier, accent: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    MaxSurface(modifier = modifier, accent = accent, onClick = onClick, content = content)
}

@Composable
fun GlowLinearBar(fraction: Float, accent: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 7.dp) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "dashboardProgress")
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
        color = accent,
        trackColor = accent.copy(alpha = .1f)
    )
}


