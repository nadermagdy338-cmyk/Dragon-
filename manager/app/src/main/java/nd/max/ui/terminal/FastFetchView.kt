/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package nd.max.ui.terminal

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import java.util.concurrent.TimeUnit

/**
 * `fastfetch` header for the terminal screen.
 *
 * This used to run on a hardcoded Catppuccin palette that had nothing to do
 * with the app's own dynamic theme, so it looked like a different app pasted
 * on top of the terminal. It now reads its accent set from [MaterialTheme],
 * meaning it inherits whatever key color / Material You seed the person
 * picked in Settings — while keeping the compact `key : value` fetch layout
 * people expect. On top of that it gets the "magical" touches requested:
 * a soft breathing glow behind the avatar ring, a staggered fade/slide-in
 * for each info line, and an animated caret on the `> fastfetch` prompt.
 */
@Composable
fun FastFetchHeader(scale: Float) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colorScheme = MaterialTheme.colorScheme

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // --- REALTIME DATA ---
    val memInfo = remember {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(info)
        info
    }

    val totalRam = Formatter.formatShortFileSize(context, memInfo.totalMem)
    val availRam = memInfo.availMem
    val usedRamCalc = memInfo.totalMem - availRam
    val usedRam = Formatter.formatShortFileSize(context, usedRamCalc)
    val ramPercentage = ((usedRamCalc.toDouble() / memInfo.totalMem.toDouble()) * 100).toInt()

    val uptimeMillis = SystemClock.elapsedRealtime()
    val uptimeHours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
    val uptimeMinutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
    val uptimeStr = "${uptimeHours}h ${uptimeMinutes}m"

    // Theme-driven fetch palette instead of a hardcoded Catppuccin set — every
    // line now visually belongs to whatever accent the person chose.
    val accentSoftware = colorScheme.tertiary
    val accentPkgs = colorScheme.primary
    val accentDisplay = colorScheme.secondary
    val accentShell = colorScheme.tertiary
    val accentHardware = colorScheme.primary
    val accentRam = colorScheme.secondary
    val accentUptime = colorScheme.tertiary
    val textMain = colorScheme.onSurface
    val textDim = colorScheme.onSurfaceVariant
    val divider = colorScheme.outlineVariant

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val caretTransition = rememberInfiniteTransition(label = "caret_blink")
    val caretAlpha by caretTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caret_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 16.dp * scale,
                bottom = 16.dp * scale,
                start = 16.dp,
                end = 16.dp
            )
    ) {
        // 1. COMMAND PROMPT
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp * scale)) {
            Text(
                text = "> fastfetch",
                color = accentPkgs,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp * scale
            )
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(7.dp * scale)
                    .height(13.dp * scale)
                    .background(accentPkgs.copy(alpha = caretAlpha))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // --- 2. GLOWING AVATAR ---
            val imageWeight = if (isLandscape) 0.2f else 0.35f
            val textWeight = 1f - imageWeight

            Box(
                modifier = Modifier
                    .weight(imageWeight)
                    .padding(end = 12.dp * scale),
                contentAlignment = Alignment.TopCenter
            ) {
                GlowingAvatarFrame(
                    accent = accentPkgs,
                    secondary = accentDisplay
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.avatar_transparent),
                        contentDescription = "MaxManager Art",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp * scale))
                    )
                }
            }

            // --- 3. INFO BLOCKS ---
            Column(
                modifier = Modifier.weight(textWeight)
            ) {
                BracketLine(true, scale, divider)

                // === SOFTWARE ===
                StaggeredFetchItem(0, revealed, Icons.Default.Settings, "OS", "Android ${Build.VERSION.RELEASE}", accentSoftware, textMain, textDim, scale)
                StaggeredFetchItem(1, revealed, Icons.Rounded.Memory, "Kernel", System.getProperty("os.version")?.take(15) ?: "Linux", accentSoftware, textMain, textDim, scale)
                StaggeredFetchItem(2, revealed, Icons.Outlined.Widgets, "Pkgs", "1337 (dpkg)", accentPkgs, textMain, textDim, scale)
                StaggeredFetchItem(3, revealed, Icons.Default.Smartphone, "Display", "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}", accentDisplay, textMain, textDim, scale)
                StaggeredFetchItem(4, revealed, Icons.Rounded.Terminal, "Shell", "MaxManagerShell", accentShell, textMain, textDim, scale)

                Spacer(modifier = Modifier.height(6.dp * scale))
                BracketLine(false, scale, divider)
                Spacer(modifier = Modifier.height(6.dp * scale))

                // Header User
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, null, tint = textMain, modifier = Modifier.size(12.dp * scale))
                    Spacer(modifier = Modifier.width(4.dp * scale))
                    Text("maxmanager @ ${Build.MODEL.take(10).trim()}", color = textMain, fontSize = 11.sp * scale, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp * scale))
                BracketLine(true, scale, divider)

                // === HARDWARE ===
                StaggeredFetchItem(5, revealed, Icons.Default.DeveloperBoard, "SoC", Build.HARDWARE.uppercase(), accentHardware, textMain, textDim, scale)
                StaggeredFetchItem(6, revealed, Icons.Outlined.Memory, "GPU", "Adreno/Mali", accentHardware, textMain, textDim, scale)
                StaggeredFetchItem(7, revealed, Icons.Outlined.SdStorage, "RAM", "$usedRam / $totalRam ($ramPercentage%)", accentRam, textMain, textDim, scale)
                StaggeredFetchItem(8, revealed, Icons.Default.Timer, "Uptime", uptimeStr, accentUptime, textMain, textDim, scale)

                BracketLine(false, scale, divider)

                // === PALETTE ===
                Spacer(modifier = Modifier.height(8.dp * scale))
                ColorDots(scale)
            }
        }
    }
}

// --- VISUAL ELEMENTS ---

/**
 * A soft, slowly breathing gradient ring behind the avatar, plus a thin
 * rotating highlight arc — the "magical" touch requested, kept subtle enough
 * not to fight with the fetch text next to it.
 */
@Composable
private fun GlowingAvatarFrame(
    accent: Color,
    secondary: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    Box(contentAlignment = Alignment.TopCenter) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(2.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = glowAlpha * 0.35f), Color.Transparent)
                ),
                radius = size.minDimension * 0.75f,
                center = Offset(size.width / 2f, size.height * 0.4f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(ringRotation)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent, secondary, Color.Transparent, Color.Transparent, accent)
                    ),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                )
            }
        }
        Box(modifier = Modifier.padding(6.dp), content = content)
    }
}

@Composable
fun BracketLine(isTop: Boolean, scale: Float, dividerColor: Color = MaterialTheme.colorScheme.outlineVariant) {
    Row(verticalAlignment = if (isTop) Alignment.Top else Alignment.Bottom) {
        Box(modifier = Modifier.width(1.dp).height(4.dp * scale).background(dividerColor))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(dividerColor))
        Box(modifier = Modifier.width(1.dp).height(4.dp * scale).background(dividerColor))
    }
}

/**
 * A single `key : value` fetch line that fades and slides in with a small
 * per-index delay, so the block reads top-to-bottom like it's being printed
 * rather than popping in all at once.
 */
@Composable
private fun StaggeredFetchItem(
    index: Int,
    revealed: Boolean,
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    textMain: Color,
    textDim: Color,
    scale: Float
) {
    AnimatedVisibility(
        visible = revealed,
        enter = fadeIn(animationSpec = tween(220, delayMillis = index * 45, easing = EaseOutCubic)) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(220, delayMillis = index * 45, easing = EaseOutCubic)
            )
    ) {
        FetchItem(icon, label, value, accentColor, textMain, textDim, scale)
    }
}

@Composable
fun FetchItem(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    textMain: Color = MaterialTheme.colorScheme.onSurface,
    textDim: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    scale: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (0.5).dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(10.dp * scale)
        )
        Spacer(modifier = Modifier.width(6.dp * scale))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                withStyle(SpanStyle(color = textDim)) {
                    append(" : ")
                }
                withStyle(SpanStyle(color = textMain)) {
                    append(value)
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp * scale,
            lineHeight = 12.sp * scale
        )
    }
}

/**
 * The little palette-swatch row at the bottom of the fetch block. Now pulled
 * from the active [MaterialTheme.colorScheme] with two neutral end caps,
 * instead of a fixed Catppuccin swatch that never matched the chosen theme —
 * and pops in one dot at a time.
 */
@Composable
fun ColorDots(scale: Float) {
    val colorScheme = MaterialTheme.colorScheme
    val dots = listOf(
        colorScheme.outlineVariant,
        colorScheme.error,
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary,
        colorScheme.primaryContainer
    )
    var revealedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in dots.indices) {
            revealedCount = i + 1
            kotlinx.coroutines.delay(45)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp * scale)) {
        dots.forEachIndexed { i, color ->
            val visible = i < revealedCount
            val animatedScale by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(180, easing = EaseOutCubic),
                label = "dot_scale_$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp * scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = animatedScale))
            )
        }
    }
}
