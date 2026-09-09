/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.component.LivePulseDot
import nd.max.ui.component.maxSemanticColors
import nd.max.ui.component.maxButtonSemantics
import nd.max.ui.component.MiniSparkline
import nd.max.ui.theme.MonoValueStyleLarge
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.theme.MonoValueStyleSmall

// =========================================================================
// HELPERS
// =========================================================================

fun formatNetSpeed(kbps: Long): String = when {
    kbps >= 1024 -> String.format("%.1f MB/s", kbps / 1024f)
    else -> "$kbps KB/s"
}

@Composable
fun DashSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 20.dp)
    )
}

/**
 * Icon badge with a soft outer glow instead of a flat tinted circle -- two
 * wider, fainter rings stacked under the crisp inner circle, the same
 * cheap-blur-substitute technique RadialGaugeCard uses for its value arc.
 * What actually reads as "glowing" on a phone screen is layered alpha, not a
 * real blur, and this keeps the whole dashboard visually related to the
 * gauge-based tuning screens elsewhere in the app instead of a flatter,
 * unrelated style living only here.
 */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 40) {
    // A single instrument-like surface. The previous three concentric circles
    // made every control look like a floating target and became especially
    // noisy on dense settings screens. One rounded chassis gives the icon a
    // clear silhouette while keeping the accent visible.
    val shape = RoundedCornerShape((size * 0.34f).dp)
    Surface(
        modifier = Modifier.size(size.dp),
        shape = shape,
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((size * 0.52f).dp)
            )
        }
    }
}

/**
 * A small tinted pill for a short status word or count -- e.g. a badge count
 * on a tab, or a "System" / "Frozen" tag on an app row. Same tint-on-tint
 * treatment as [IconBadge] so the two read as part of the same family when
 * they appear together.
 */
@Composable
fun LabelText(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * A metric card's shell: a faint diagonal wash of [accent] into
 * surfaceContainerLow (same gradient technique CpuHeaderCard already
 * established for the Core Grid screen) plus a hairline accent-tinted
 * border for edge definition, rather than one flat surfaceContainer fill
 * shared by every card on the screen regardless of what it represents.
 * [accent] stays optional (null = the old flat neutral fill) so anything
 * reusing this shell for a non-metric purpose isn't forced to pick a color.
 */
@Composable
fun DashCardWrapper(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    nd.max.ui.component.MaxSurface(
        modifier = modifier,
        accent = accent,
        onClick = onClick,
        content = content
    )
}

/**
 * A linear meter with the same soft-glow treatment as [IconBadge] -- a
 * wider, faint bar glowing behind a crisp gradient-filled bar on top --
 * instead of a bare stock LinearProgressIndicator. [animated] drives the
 * fraction through a spring so it settles with a touch of real-instrument
 * overshoot rather than a flat linear tween, matching the app's expressive
 * motion scheme.
 */
@Composable
fun GlowLinearBar(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 7.dp
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "glowBarFraction"
    )
    Box(modifier = modifier.height(height + 6.dp), contentAlignment = Alignment.CenterStart) {
        // Glow layer: wider + fainter, only under the filled portion.
        if (animated > 0.02f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(height + 6.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
        )
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.75f), accent)))
        )
    }
}

// =========================================================================
// CPU CARD — Read-only
// =========================================================================

@Composable
fun CpuDashCard(
    loadPercent: Int,
    freqMhz: Int,
    chipsetName: String,
    modifier: Modifier = Modifier
) {
    val cpuColor = maxSemanticColors().info
    DashCardWrapper(modifier = modifier, accent = cpuColor, onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Memory, cpuColor)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("CPU", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = chipsetName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "$loadPercent", style = MonoValueStyleLarge, color = cpuColor)
            Text(
                text = "%",
                style = MonoValueStyleMedium,
                color = cpuColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 3.dp, start = 1.dp)
            )
        }
        Text(
            text = if (freqMhz > 0) "$freqMhz MHz" else "\u2014",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        GlowLinearBar(fraction = loadPercent / 100f, accent = cpuColor)
    }
}

// =========================================================================
// RAM CARD — Read-only
// =========================================================================

@Composable
fun RamDashCard(
    usedMb: Int,
    totalMb: Int,
    modifier: Modifier = Modifier
) {
    val ramColor = maxSemanticColors().positive
    val pct = if (totalMb > 0) usedMb * 100 / totalMb else 0
    val usedGb = usedMb / 1024f
    val totalGb = totalMb / 1024f
    DashCardWrapper(modifier = modifier, accent = ramColor, onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Memory, ramColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text("RAM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "$pct", style = MonoValueStyleLarge, color = ramColor)
            Text(
                text = "%",
                style = MonoValueStyleMedium,
                color = ramColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 3.dp, start = 1.dp)
            )
        }
        Text(
            text = "${String.format("%.1f", usedGb)} / ${String.format("%.1f", totalGb)} GB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        GlowLinearBar(fraction = pct / 100f, accent = ramColor)
    }
}

// =========================================================================
// BATTERY CARD — Clickable
// =========================================================================

@Composable
fun BatteryDashCard(
    percent: Int,
    voltageV: Float,
    tempC: Float,
    isCharging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = maxSemanticColors()
    val chargingGreen = semantic.positive
    val battColor by animateColorAsState(
        targetValue = when {
            isCharging -> chargingGreen
            percent <= 15 -> semantic.critical
            percent <= 35 -> semantic.warning
            else -> semantic.warning
        },
        label = "battColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "bat")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    DashCardWrapper(modifier = modifier, accent = battColor, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.alpha(if (isCharging) pulseAlpha else 1f)) {
                IconBadge(
                    icon = if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                    tint = battColor
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("Battery", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = battColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "$percent", style = MonoValueStyleLarge, color = battColor)
            Text(
                text = "%",
                style = MonoValueStyleMedium,
                color = battColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 3.dp, start = 1.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCharging) {
                Icon(Icons.Rounded.Bolt, null, tint = chargingGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = if (isCharging) "Charging" else "Discharging",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCharging) chargingGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${String.format("%.2f", voltageV)} V  \u2022  ${String.format("%.1f", tempC)}\u00b0C",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        GlowLinearBar(fraction = percent / 100f, accent = battColor)
    }
}

// =========================================================================
// TEMPERATURE CARD — Clickable
// =========================================================================

@Composable
fun TempDashCard(
    cpuTempC: Int,
    gpuTempC: Int,
    skinTempC: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tempHistory: List<Float> = emptyList()
) {
    val semantic = maxSemanticColors()
    val tempColor by animateColorAsState(
        targetValue = when {
            cpuTempC >= 70 -> semantic.critical
            cpuTempC >= 50 -> semantic.warning
            else -> semantic.positive
        },
        label = "tempColor"
    )
    DashCardWrapper(modifier = modifier, accent = tempColor, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Thermostat, tempColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Temperature", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = tempColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (cpuTempC > 0) "$cpuTempC" else "\u2014",
                style = MonoValueStyleLarge,
                color = tempColor
            )
            Text(
                text = "\u00b0C",
                style = MonoValueStyleMedium,
                color = tempColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 3.dp, start = 1.dp)
            )
        }
        Text("CPU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
        if (tempHistory.size >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                MiniSparkline(
                    samples = tempHistory,
                    lineColor = tempColor,
                    width = maxWidth,
                    height = 28.dp
                )
            }
        }
        if (gpuTempC > 0 || skinTempC > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (gpuTempC > 0) {
                    Column {
                        Text("$gpuTempC\u00b0", style = MonoValueStyleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("GPU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (skinTempC > 0) {
                    Column {
                        Text("$skinTempC\u00b0", style = MonoValueStyleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("Skin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// =========================================================================
// STORAGE CARD — Clickable
// =========================================================================

@Composable
fun StorageDashCard(
    usedGb: Float,
    totalGb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storColor = Color(0xFF9B59B6)
    val pct = if (totalGb > 0f) usedGb / totalGb else 0f
    DashCardWrapper(modifier = modifier, accent = storColor, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Storage, storColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Storage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = storColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "${String.format("%.0f", pct * 100)}", style = MonoValueStyleLarge, color = storColor)
            Text(
                text = "%",
                style = MonoValueStyleMedium,
                color = storColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 3.dp, start = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "used",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            text = "${String.format("%.1f", usedGb)} / ${String.format("%.1f", totalGb)} GB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        GlowLinearBar(fraction = pct, accent = storColor)
    }
}

// =========================================================================
// NETWORK CARD — Clickable with sparkline
// =========================================================================

@Composable
fun NetworkDashCard(
    downloadKbps: Long,
    uploadKbps: Long,
    speedHistory: List<Long>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val netColor = Color(0xFF00BCD4)
    DashCardWrapper(modifier = modifier, accent = netColor, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.NetworkCheck, netColor)
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Network Speed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                LivePulseDot(color = netColor)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = netColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = netColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = formatNetSpeed(downloadKbps), style = MonoValueStyleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = formatNetSpeed(uploadKbps), style = MonoValueStyleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (speedHistory.size >= 2) {
            val maxVal = speedHistory.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(netColor.copy(alpha = 0.07f))
            ) {
                val w = size.width
                val h = size.height
                val step = w / (speedHistory.size - 1).toFloat()
                val fillPath = Path()
                val linePath = Path()
                speedHistory.forEachIndexed { index, value ->
                    val x = index * step
                    val y = h - (value.toFloat() / maxVal * h * 0.9f)
                    if (index == 0) { fillPath.moveTo(x, h); fillPath.lineTo(x, y); linePath.moveTo(x, y) }
                    else { fillPath.lineTo(x, y); linePath.lineTo(x, y) }
                }
                fillPath.lineTo((speedHistory.size - 1) * step, h)
                fillPath.close()
                drawPath(fillPath, brush = Brush.verticalGradient(
                    listOf(netColor.copy(alpha = 0.5f), netColor.copy(alpha = 0.0f))
                ), style = Fill)
                drawPath(linePath, color = netColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

// =========================================================================
// DISPLAY CARD — Clickable
// =========================================================================

@Composable
fun DisplayDashCard(
    width: Int,
    height: Int,
    refreshHz: Int,
    densityDpi: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dispColor = Color(0xFF009688)
    DashCardWrapper(modifier = modifier, accent = dispColor, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Smartphone, dispColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Display", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = dispColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (width > 0 && height > 0) "${width}\u00d7${height}" else "\u2014",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = dispColor
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("$refreshHz Hz", style = MonoValueStyleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("Refresh", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text("$densityDpi", style = MonoValueStyleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("DPI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
