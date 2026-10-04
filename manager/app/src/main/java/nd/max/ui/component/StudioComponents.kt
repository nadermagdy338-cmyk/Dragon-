/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package nd.max.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.design.MaxRadius
import nd.max.R

@Composable
fun StudioPerformanceHero(
    deviceName: String,
    online: Boolean,
    cpuLoad: Int,
    cpuFreq: Int,
    temperature: String,
    profile: String,
    history: List<Float>,
    activeApp: String?,
    profileEnabled: Boolean,
    onProfile: () -> Unit,
    onApps: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val heroShape = RoundedCornerShape(MaxRadius.sheet)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .border(1.dp, colors.primary.copy(alpha = 0.22f), heroShape),
        shape = heroShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.primaryContainer,
                        colors.primaryContainer.copy(alpha = 0.92f),
                        colors.tertiaryContainer.copy(alpha = 0.82f)
                    )
                )
            )
        ) {
            Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
                val center = Offset(size.width * 1.03f, size.height * .30f)
                repeat(4) { ring ->
                    drawCircle(
                        colors.onPrimaryContainer.copy(alpha = .035f + ring * .008f),
                        76.dp.toPx() + ring * 34.dp.toPx(),
                        center,
                        style = Stroke(1.dp.toPx())
                    )
                }
                drawCircle(
                    colors.primary.copy(alpha = .08f),
                    120.dp.toPx(),
                    Offset(size.width * .96f, size.height * .15f)
                )
            }
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.studio_performance), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                    MaxStatusPill(if (online) stringResource(R.string.max_home_live) else stringResource(R.string.max_home_offline), online, colors.primary)
                }
                Text(deviceName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 290.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                    val readout: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.studio_cpu_frequency), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
                            Text(if (cpuFreq > 0) cpuFreq.toString() else "—", style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                            if (cpuFreq > 0) {
                                Text("MHz", style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer.copy(alpha = .75f))
                            }
                        }
                    }
                    if (stacked) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StudioLoadOrbit(cpuLoad, colors.onPrimaryContainer)
                            readout()
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) { readout() }
                            StudioLoadOrbit(cpuLoad, colors.onPrimaryContainer)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(MaxRadius.tile), color = colors.surface.copy(alpha = .7f)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.studio_load_history), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        if (history.size >= 2) {
                            MaxSparkline(history, colors.primary)
                        } else {
                            Text(stringResource(R.string.studio_waiting_samples), Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioReadout(stringResource(R.string.max_home_temp), temperature)
                    StudioReadout(stringResource(R.string.max_home_profile), profile)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    nd.max.ui.component.StudioButton(onClick = onProfile, enabled = profileEnabled, shape = RoundedCornerShape(MaxRadius.row)) {
                        Text(stringResource(R.string.max_home_change_profile))
                    }
                    nd.max.ui.component.StudioOutlinedButton(onClick = onApps, shape = RoundedCornerShape(MaxRadius.row)) {
                        Text(stringResource(R.string.max_home_app_profiles))
                    }
                }
                if (!profileEnabled) {
                    Text(stringResource(R.string.studio_automatic_profile), style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                }
                if (activeApp != null) {
                    Text("${stringResource(R.string.max_home_active_app)} · $activeApp", style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StudioReadout(label: String, value: String) {
    Surface(shape = RoundedCornerShape(MaxRadius.chip), color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StudioLoadOrbit(load: Int, accent: Color) {
    val fraction by animateFloatAsState(load.coerceIn(0, 100) / 100f, MaxMotion.needleSpring, label = "studioCpuLoad")
    Box(Modifier.size(140.dp).semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(load.coerceIn(0, 100).toFloat(), 0f..100f) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            repeat(32) { index ->
                drawArc(accent.copy(alpha = if (index / 32f < fraction) 1f else .13f), 135f + index * 8.5f, 4.5f, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(accent.copy(alpha = .14f), size.minDimension / 2 - 26.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp), tint = accent)
            Text("${load.coerceIn(0, 100)}%", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = accent)
            Text("CPU", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = accent)
        }
    }
}

@Composable
fun StudioShortcut(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaxSurface(modifier = modifier.fillMaxWidth(), accent = accent, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StudioAdaptivePair(content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        if (stacked) {
            // FlowRow retains weight semantics while placing one tile per row.
            FlowRow(maxItemsInEachRow = 1, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
