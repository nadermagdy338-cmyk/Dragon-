/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import android.net.TrafficStats
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import nd.max.R
import nd.max.ui.component.LivePulseDot
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.theme.MonoValueStyleSmall

// =========================================================================
// SHARED DETAIL LANGUAGE
// =========================================================================

@Composable
private fun DetailTopBar(
    title: String,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    label: String,
    icon: ImageVector
) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = title,
        subtitle = label,
        onBack = { navController.popBackStack() },
        accentIcon = icon,
        accent = MaterialTheme.colorScheme.primary,
        actions = {
            IconBadge(icon, MaterialTheme.colorScheme.primary, size = 34)
            Spacer(Modifier.width(12.dp))
        }
    )
}

@Composable
private fun LiveHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: Color,
    value: String,
    valueLabel: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    nd.max.ui.component.MaxSurface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.18f), shape),
        accent = accent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            nd.max.ui.component.ScreenAccentGlyph(icon, accent, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .72f)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
private fun DetailStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    DashCardWrapper(modifier = modifier.heightIn(min = 76.dp), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = accent, size = 32)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MonoValueStyleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun DetailPill(text: String, accent: Color, icon: ImageVector? = null) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(50),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

private fun DetailListPadding(top: PaddingValues): PaddingValues = PaddingValues(
    top = top.calculateTopPadding() + 8.dp,
    start = 16.dp,
    end = 16.dp,
    bottom = 104.dp
)

// الشاشتان هنا (`ThermalDetailScreen` و`StorageDetailScreen`) نُقلتا إلى `ui/subscreens`
// وأُعيد بناؤهما بلغة التصميم المشتركة: كانتا بلغة لوحة البداية بشبكة مختلفة وعناوين
// إنجليزية صلبة داخل شاشة عربية، وقراءة بطارية تبحث عن تصنيف مُعرَّب لا تعلنه النواة.
// وتبقى شاشة الشبكة هنا حتى تُعاد بناؤها بنفس الطريقة.


// =========================================================================
// 3. NETWORK DETAIL SCREEN
// =========================================================================

@Composable
private fun NetworkSparkline(history: List<Long>, accent: Color, secondary: Color) {
    val maxVal = history.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceContainerHighest.copy(alpha = 0.55f))
    ) {
        val w = size.width
        val h = size.height
        val baseline = h - 18.dp.toPx()
        if (history.size >= 2) {
            val step = w / (history.size - 1).toFloat()
            val fillPath = Path()
            val linePath = Path()
            history.forEachIndexed { index, value ->
                val x = index * step
                val y = baseline - (value.toFloat() / maxVal * (baseline - 16.dp.toPx()))
                if (index == 0) {
                    fillPath.moveTo(x, baseline)
                    fillPath.lineTo(x, y)
                    linePath.moveTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                    linePath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, baseline)
            fillPath.close()
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), Color.Transparent)),
                style = Fill
            )
            drawPath(linePath, color = accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(secondary, radius = 4.dp.toPx(), center = Offset(w - 1.dp.toPx(), baseline - (history.last().toFloat() / maxVal * (baseline - 16.dp.toPx()))))
        }
        drawLine(
            color = outlineVariant,
            start = Offset(0f, baseline),
            end = Offset(w, baseline),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun NetworkDetailScreen(navController: NavController) {
    val speedHistory = remember { mutableStateListOf<Long>() }
    var dlKbps by remember { mutableLongStateOf(0L) }
    var ulKbps by remember { mutableLongStateOf(0L) }
    var peakDl by remember { mutableLongStateOf(0L) }
    var peakUl by remember { mutableLongStateOf(0L) }
    var lastRx by remember { mutableLongStateOf(TrafficStats.getTotalRxBytes()) }
    var lastTx by remember { mutableLongStateOf(TrafficStats.getTotalTxBytes()) }
    var sampled by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            dlKbps = if (rx > lastRx) (rx - lastRx) / 1024L else 0L
            ulKbps = if (tx > lastTx) (tx - lastTx) / 1024L else 0L
            lastRx = rx
            lastTx = tx
            peakDl = maxOf(peakDl, dlKbps)
            peakUl = maxOf(peakUl, ulKbps)
            speedHistory.add(dlKbps)
            if (speedHistory.size > 36) speedHistory.removeAt(0)
            sampled++
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val totalRx = TrafficStats.getTotalRxBytes()
    val totalTx = TrafficStats.getTotalTxBytes()

    fun fmtBytes(b: Long): String = when {
        b >= 1_073_741_824L -> String.format("%.2f GB", b / 1_073_741_824.0)
        b >= 1_048_576L -> String.format("%.1f MB", b / 1_048_576.0)
        else -> "${b.coerceAtLeast(0L) / 1024L} KB"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.detail_network),
                label = stringResource(R.string.detail_live_traffic),
                icon = Icons.Rounded.NetworkCheck,
                navController = navController,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = DetailListPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LiveHeader(
                    eyebrow = stringResource(R.string.detail_live_traffic_label),
                    title = if (dlKbps == 0L && ulKbps == 0L) stringResource(R.string.detail_network_quiet) else stringResource(R.string.detail_network_active),
                    subtitle = stringResource(R.string.detail_trafficstats_subtitle),
                    accent = accent,
                    value = netSpeed(dlKbps),
                    valueLabel = stringResource(R.string.detail_download_value),
                    icon = Icons.Rounded.NetworkCheck
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DetailStatCard(Icons.Rounded.ArrowDownward, stringResource(R.string.detail_download), netSpeed(dlKbps), accent, Modifier.weight(1f))
                    DetailStatCard(Icons.Rounded.ArrowUpward, stringResource(R.string.detail_upload), netSpeed(ulKbps), secondary, Modifier.weight(1f))
                }
            }

            item {
                DashCardWrapper(Modifier.fillMaxWidth(), accent = accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.detail_download_timeline), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (sampled < 2) stringResource(R.string.detail_collecting_samples) else stringResource(R.string.detail_last_samples, speedHistory.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LivePulseDot(accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedVisibility(
                        visible = speedHistory.size >= 2,
                        enter = fadeIn(tween(280)),
                        exit = fadeOut(tween(160))
                    ) {
                        NetworkSparkline(speedHistory, accent, secondary)
                    }
                    if (speedHistory.size < 2) {
                        Box(Modifier.fillMaxWidth().height(168.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.detail_collecting_live_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.detail_traffic_totals), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.detail_android_counters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DetailPill(stringResource(R.string.detail_peaks), secondary)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DetailStatCard(Icons.Rounded.Download, stringResource(R.string.detail_total_download), fmtBytes(totalRx), accent, Modifier.weight(1f))
                    DetailStatCard(Icons.Rounded.Upload, stringResource(R.string.detail_total_upload), fmtBytes(totalTx), secondary, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DetailStatCard(Icons.AutoMirrored.Rounded.TrendingDown, stringResource(R.string.detail_peak_download), netSpeed(peakDl), accent, Modifier.weight(1f))
                    DetailStatCard(Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.detail_peak_upload), netSpeed(peakUl), secondary, Modifier.weight(1f))
                }
            }
        }
    }
}

