/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.LivePulseDot
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.RadialGaugeCard
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.ThermalZoneInfo
import kotlin.math.abs
import kotlin.math.roundToInt

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

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.detail_live_device_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DetailListPadding(top: PaddingValues): PaddingValues = PaddingValues(
    top = top.calculateTopPadding() + 8.dp,
    start = 16.dp,
    end = 16.dp,
    bottom = 104.dp
)

// =========================================================================
// 1. THERMAL DETAIL SCREEN
// =========================================================================

@Composable
fun ThermalDetailScreen(navController: NavController) {
    var zones by remember { mutableStateOf<List<ThermalZoneInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        zones = withContext(Dispatchers.IO) { ThermalUtil.readThermalZones() }
        isLoading = false
        while (true) {
            delay(3000)
            zones = withContext(Dispatchers.IO) { ThermalUtil.readThermalZones() }
        }
    }

    val cpuTemps = zones.filter { it.category.equals("CPU", true) && it.temperatureC > 0 }.map { it.temperatureC }
    val gpuTemps = zones.filter { it.category.equals("GPU", true) && it.temperatureC > 0 }.map { it.temperatureC }
    val batteryTemps = zones.filter { it.category.equals(stringResource(R.string.detail_battery), true) && it.temperatureC > 0 }.map { it.temperatureC }
    val cpuAvg = cpuTemps.average().takeUnless { it.isNaN() } ?: 0.0
    val gpuMax = gpuTemps.maxOrNull() ?: 0
    val batteryMax = batteryTemps.maxOrNull() ?: 0
    val hottest = maxOf(cpuAvg.roundToInt(), gpuMax, batteryMax)
    val accent by animateColorAsState(
        targetValue = when {
            hottest >= 70 -> MaterialTheme.colorScheme.error
            hottest >= 50 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "thermalAccent"
    )
    val enabledZones = zones.filter { it.isEnabled && it.temperatureC > 0 }
    val grouped = enabledZones.groupBy { it.category.ifBlank { "Other" } }.toList()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.detail_thermal),
                label = stringResource(R.string.detail_thermal_label),
                icon = Icons.Rounded.Thermostat,
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
            if (isLoading) {
                item { LoadingPanel() }
            } else {
                item {
                    LiveHeader(
                        eyebrow = stringResource(R.string.detail_current_peak),
                        title = if (hottest >= 70) stringResource(R.string.detail_thermal_high) else if (hottest >= 50) stringResource(R.string.detail_thermal_elevated) else stringResource(R.string.detail_thermal_stable),
                        subtitle = stringResource(R.string.detail_thermal_subtitle),
                        accent = accent,
                        value = "${hottest}°C",
                        valueLabel = stringResource(R.string.detail_hottest),
                        icon = Icons.Rounded.LocalFireDepartment
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DetailStatCard(Icons.Rounded.Memory, stringResource(R.string.detail_cpu_average), "${cpuAvg.roundToInt()}°C", accent, Modifier.weight(1f))
                        DetailStatCard(Icons.Rounded.Videocam, stringResource(R.string.detail_gpu_peak), "${gpuMax}°C", accent, Modifier.weight(1f))
                    }
                }
                item {
                    DetailStatCard(
                        Icons.Rounded.BatteryFull,
                        stringResource(R.string.detail_battery_peak),
                        "${batteryMax}°C",
                        accent,
                        Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.detail_thermal_map), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.detail_active_zones_grouped, enabledZones.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DetailPill(stringResource(R.string.detail_live), accent, Icons.Rounded.Sensors)
                    }
                }

                if (grouped.isEmpty()) {
                    item {
                        DashCardWrapper(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, size = 34)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.detail_no_thermal_zones), fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(R.string.detail_no_thermal_zones_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    grouped.forEach { (category, zoneList) ->
                        item(key = "thermal_header_$category") {
                            DashSectionLabel(category)
                        }
                        items(zoneList, key = { "thermal_${it.sysfsPath}_${it.label}" }) { zone ->
                            val zoneAccent = when {
                                zone.temperatureC >= 70 -> MaterialTheme.colorScheme.error
                                zone.temperatureC >= 50 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            DashCardWrapper(Modifier.fillMaxWidth(), accent = zoneAccent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(zone.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            zone.sysfsPath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text("${zone.temperatureC}°C", style = MonoValueStyleSmall, color = zoneAccent)
                                }
                                Spacer(Modifier.height(10.dp))
                                GlowLinearBar(
                                    fraction = (zone.temperatureC / 100f).coerceIn(0f, 1f),
                                    accent = zoneAccent,
                                    height = 6.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. STORAGE DETAIL SCREEN
// =========================================================================

private data class PartitionInfo(
    val name: String,
    val path: String,
    val usedGb: Float,
    val totalGb: Float,
    val freeGb: Float,
    val filesystem: String
)

private fun loadPartitions(): List<PartitionInfo> {
    val result = mutableListOf<PartitionInfo>()
    listOf(
        "/data" to "Internal Data",
        "/sdcard" to "Internal Storage",
        "/system" to "System"
    ).forEach { (path, name) ->
        try {
            val sf = StatFs(path)
            val total = sf.blockSizeLong * sf.blockCountLong
            val free = sf.blockSizeLong * sf.availableBlocksLong
            if (total > 0) {
                result.add(
                    PartitionInfo(
                        name,
                        path,
                        (total - free) / 1_073_741_824f,
                        total / 1_073_741_824f,
                        free / 1_073_741_824f,
                        try {
                            Shell.cmd("stat -f -c %T $path 2>/dev/null").exec().out.firstOrNull()?.trim().orEmpty()
                        } catch (_: Exception) { "" }
                    )
                )
            }
        } catch (_: Exception) {
            try {
                val out = Shell.cmd("df -k $path 2>/dev/null | tail -1").exec().out.firstOrNull()?.trim() ?: return@forEach
                val parts = out.split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val total = parts[1].toLongOrNull()?.times(1024L) ?: return@forEach
                    val used = parts[2].toLongOrNull()?.times(1024L) ?: return@forEach
                    val free = parts[3].toLongOrNull()?.times(1024L) ?: (total - used)
                    if (total > 0) {
                        result.add(
                            PartitionInfo(
                                name,
                                path,
                                used / 1_073_741_824f,
                                total / 1_073_741_824f,
                                free / 1_073_741_824f,
                                parts.getOrNull(0).orEmpty()
                            )
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }
    return result
}

private fun formatStorage(gb: Float): String = when {
    gb >= 1024f -> String.format("%.2f TB", gb / 1024f)
    gb >= 10f -> String.format("%.1f GB", gb)
    else -> String.format("%.2f GB", gb)
}

@Composable
fun StorageDetailScreen(navController: NavController) {
    var partitions by remember { mutableStateOf<List<PartitionInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            partitions = withContext(Dispatchers.IO) { loadPartitions() }
            isLoading = false
            revision++
            delay(5000)
        }
    }

    val main = partitions.firstOrNull { it.path == "/data" } ?: partitions.firstOrNull()
    val usedFraction = main?.let { if (it.totalGb > 0f) (it.usedGb / it.totalGb).coerceIn(0f, 1f) else 0f } ?: 0f
    val storageAccent = MaterialTheme.colorScheme.tertiary
    val storageTitle = when {
        main == null -> stringResource(R.string.detail_storage_unavailable)
        usedFraction >= 0.90f -> stringResource(R.string.detail_storage_nearly_full)
        usedFraction >= 0.75f -> stringResource(R.string.detail_storage_busy)
        else -> stringResource(R.string.detail_storage_headroom)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.detail_storage),
                label = stringResource(R.string.detail_filesystem_view),
                icon = Icons.Rounded.Storage,
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
            if (isLoading) {
                item { LoadingPanel() }
            } else if (main == null) {
                item {
                    LiveHeader(
                        eyebrow = "UNAVAILABLE",
                        title = storageTitle,
                        subtitle = stringResource(R.string.detail_no_filesystem_stats),
                        accent = MaterialTheme.colorScheme.error,
                        value = "—",
                        valueLabel = "capacity",
                        icon = Icons.Rounded.Storage
                    )
                }
            } else {
                item {
                    LiveHeader(
                        eyebrow = stringResource(R.string.detail_internal_capacity),
                        title = storageTitle,
                        subtitle = stringResource(R.string.detail_storage_refresh_subtitle),
                        accent = storageAccent,
                        value = "${(usedFraction * 100).roundToInt()}%",
                        valueLabel = "used",
                        icon = Icons.Rounded.Storage
                    )
                }
                item {
                    DashCardWrapper(Modifier.fillMaxWidth(), accent = storageAccent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.detail_primary_view), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(main.path, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    buildString {
                                        append(formatStorage(main.usedGb))
                                        append(" used · ")
                                        append(formatStorage(main.freeGb))
                                        append(" free")
                                        if (main.filesystem.isNotBlank()) append(" · ${main.filesystem}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DetailPill("${formatStorage(main.totalGb)} total", storageAccent)
                        }
                        Spacer(Modifier.height(14.dp))
                        GlowLinearBar(usedFraction, storageAccent, height = 9.dp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailStatCard(Icons.Rounded.DataUsage, stringResource(R.string.detail_used), formatStorage(main.usedGb), storageAccent, Modifier.weight(1f))
                            DetailStatCard(Icons.Rounded.Inventory2, stringResource(R.string.detail_free), formatStorage(main.freeGb), storageAccent, Modifier.weight(1f))
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.detail_mount_views), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.detail_readable_paths, partitions.size, if (partitions.size == 1) "" else "s"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(stringResource(R.string.detail_refresh_number, revision), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(partitions, key = { it.path }) { part ->
                    val fraction = if (part.totalGb > 0f) (part.usedGb / part.totalGb).coerceIn(0f, 1f) else 0f
                    val isPrimary = part.path == "/data" || part.path == "/sdcard"
                    val accent = if (isPrimary) storageAccent else MaterialTheme.colorScheme.secondary
                    DashCardWrapper(Modifier.fillMaxWidth(), accent = accent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                if (part.path == "/system") Icons.Rounded.Layers else Icons.Rounded.Storage,
                                accent,
                                size = 34
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(part.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(part.path)
                                        if (part.filesystem.isNotBlank()) append(" · ${part.filesystem}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("${(fraction * 100).roundToInt()}%", style = MonoValueStyleSmall, color = accent)
                        }
                        Spacer(Modifier.height(10.dp))
                        GlowLinearBar(fraction, accent, height = 6.dp)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "${formatStorage(part.usedGb)} used · ${formatStorage(part.freeGb)} free · ${formatStorage(part.totalGb)} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    DashCardWrapper(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            IconBadge(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, size = 34)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.detail_how_to_read), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    stringResource(R.string.detail_storage_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                    value = formatNetSpeed(dlKbps),
                    valueLabel = stringResource(R.string.detail_download_value),
                    icon = Icons.Rounded.NetworkCheck
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DetailStatCard(Icons.Rounded.ArrowDownward, stringResource(R.string.detail_download), formatNetSpeed(dlKbps), accent, Modifier.weight(1f))
                    DetailStatCard(Icons.Rounded.ArrowUpward, stringResource(R.string.detail_upload), formatNetSpeed(ulKbps), secondary, Modifier.weight(1f))
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
                    DetailStatCard(Icons.Rounded.TrendingDown, stringResource(R.string.detail_peak_download), formatNetSpeed(peakDl), accent, Modifier.weight(1f))
                    DetailStatCard(Icons.Rounded.TrendingUp, stringResource(R.string.detail_peak_upload), formatNetSpeed(peakUl), secondary, Modifier.weight(1f))
                }
            }
        }
    }
}

// =========================================================================
// 4. BATTERY DETAIL SCREEN
// =========================================================================

private data class BatteryDetail(
    val percent: Int,
    val voltageV: Float,
    val tempC: Float,
    val isCharging: Boolean,
    val technology: String,
    val health: String,
    val status: String,
    val capacityMah: Int,
    val currentMa: Int,
    val powerW: Float
)

private fun loadBatteryDetail(context: Context): BatteryDetail = try {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct = if (scale > 0) level * 100 / scale else 0
    val volt = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
    val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val statusInt = i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
    val isChg = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL
    val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-Ion"
    val hInt = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
    val health = when (hInt) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
        else -> "Unknown"
    }
    val status = when (statusInt) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
        else -> "Unknown"
    }
    val cap = Shell.cmd("cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull()?.div(1000) ?: 0
    val vn = Shell.cmd("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
    val cn = Shell.cmd("cat /sys/class/power_supply/battery/current_now 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
    val pw = if (vn > 0 && cn != 0L) (abs(vn.toDouble() * cn.toDouble()) / 1_000_000_000_000.0).toFloat() else 0f
    BatteryDetail(pct, volt, temp, isChg, tech, health, status, cap, (abs(cn) / 1000).toInt(), pw)
} catch (_: Exception) {
    BatteryDetail(0, 0f, 0f, false, "Li-Ion", "Unknown", "Unknown", 0, 0, 0f)
}

@Composable
fun BatteryDetailScreen(navController: NavController) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<BatteryDetail?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            info = withContext(Dispatchers.IO) { loadBatteryDetail(context) }
            delay(3000)
        }
    }

    val data = info
    val accent = when {
        data?.health == stringResource(R.string.detail_overheat) -> MaterialTheme.colorScheme.error
        data?.isCharging == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.detail_battery),
                label = stringResource(R.string.detail_live_power_state),
                icon = Icons.Rounded.BatteryChargingFull,
                navController = navController,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                LoadingPanel()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = DetailListPadding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DashCardWrapper(Modifier.fillMaxWidth(), accent = accent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadialGaugeCard(
                                title = stringResource(R.string.detail_charge_level),
                                valueText = "${data.percent}",
                                unitText = "%",
                                fraction = data.percent / 100f,
                                subtitle = data.status,
                                isLive = true,
                                accentColor = accent,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(0.92f)) {
                                Text(
                                    if (data.isCharging) stringResource(R.string.detail_power_flowing) else stringResource(R.string.detail_on_battery),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (data.powerW > 0f) stringResource(R.string.detail_live_power_value, data.powerW) else stringResource(R.string.detail_no_power_reading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                DetailPill(
                                    data.health,
                                    when (data.health) {
                                        stringResource(R.string.detail_good) -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    },
                                    Icons.Rounded.FavoriteBorder
                                )
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DetailStatCard(Icons.Rounded.ElectricBolt, stringResource(R.string.detail_voltage), String.format("%.2f V", data.voltageV), accent, Modifier.weight(1f))
                        DetailStatCard(Icons.Rounded.Thermostat, stringResource(R.string.detail_temperature), "${data.tempC}°C", accent, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DetailStatCard(Icons.Rounded.Bolt, stringResource(R.string.detail_current), if (data.currentMa > 0) "${data.currentMa} mA" else stringResource(R.string.detail_system_unavailable), accent, Modifier.weight(1f))
                        DetailStatCard(Icons.Rounded.Power, stringResource(R.string.detail_power), if (data.powerW > 0f) String.format("%.2f W", data.powerW) else stringResource(R.string.detail_system_unavailable), accent, Modifier.weight(1f))
                    }
                }

                item {
                    Text(stringResource(R.string.detail_battery_identity), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.detail_battery_identity_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DetailStatCard(Icons.Rounded.Science, stringResource(R.string.detail_technology), data.technology, accent, Modifier.weight(1f))
                        DetailStatCard(Icons.Rounded.FavoriteBorder, stringResource(R.string.detail_health), data.health, accent, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DetailStatCard(Icons.Rounded.BatteryFull, stringResource(R.string.detail_design_capacity), if (data.capacityMah > 0) "${data.capacityMah} mAh" else stringResource(R.string.detail_system_unavailable), accent, Modifier.weight(1f))
                        DetailStatCard(Icons.Rounded.Info, stringResource(R.string.detail_status), data.status, accent, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
