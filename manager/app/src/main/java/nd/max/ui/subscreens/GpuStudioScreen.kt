@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.ui.viewmodel.GpuStudioUiState
import nd.max.ui.viewmodel.GpuStudioViewModel
import kotlin.math.roundToInt

@Composable
fun GpuStudioScreen(
    navController: NavController,
    // `hiltViewModel()` لا `viewModel()`: هذا الـViewModel له مُنشئ بوسائط (arbiter)، و`viewModel()`
    // بلا مصنع ينادي مُنشئًا بلا وسائط — فلا يجد، ويخرج التطبيق لحظة فتح الشاشة.
    viewModel: GpuStudioViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GPU Reality Studio", fontWeight = FontWeight.Bold)
                        Text("Live • Verified • Device-aware", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navController::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.device == null -> GpuUnavailableState(state, Modifier.padding(padding))
            else -> GpuStudioContent(state, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun GpuUnavailableState(state: GpuStudioUiState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Info, null, modifier = Modifier.size(36.dp))
                Text("لم يتم إثبات مزوّد GPU آمن", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when (state.selection?.state) {
                        GpuHardwareBackend.SelectionState.AMBIGUOUS -> "وجدنا أكثر من واجهة GPU متساوية الثقة. أوقفنا التحكم لمنع الكتابة إلى الجهاز الخطأ."
                        else -> "لا يعلن kernel الحالي واجهة devfreq موثوقة يمكن لـ MaxManager قراءتها والتحكم بها."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Reason: ${state.selection?.reason ?: "unknown"}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GpuStudioContent(state: GpuStudioUiState, vm: GpuStudioViewModel, modifier: Modifier = Modifier) {
    val device = state.device ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { RealityHeader(state) }
        item {
            StudioSection("SMART INTENTS", "نوايا تُترجم إلى OPP الفعلية في هذا الجهاز") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val intentsAvailable = device.rangeWritable || device.exactLockWritable
                    IntentCard("كفاءة", "خفض السقف مع تحجيم ديناميكي", Icons.Filled.BatterySaver, Color(0xFF2E7D5A), enabled = intentsAvailable) {
                        vm.stageMode(GpuHardwareBackend.IntentMode.EFFICIENCY)
                    }
                    IntentCard("متوازن تكيفي", "النطاق الآمن الكامل", Icons.Filled.Tune, Color(0xFF3559A6), enabled = intentsAvailable) {
                        vm.stageMode(GpuHardwareBackend.IntentMode.ADAPTIVE)
                    }
                    IntentCard("أداء مستدام", "أرضية أعلى والحماية الحرارية فعالة", Icons.Filled.Bolt, Color(0xFFB75A32), enabled = intentsAvailable) {
                        vm.stageMode(GpuHardwareBackend.IntentMode.SUSTAINED)
                    }
                }
            }
        }
        item { AdvancedLab(state, vm) }
        item { DiagnosticsCard(state) }
        item {
            AnimatedVisibility(state.pending != null || state.lastResult != null || state.message != null) {
                ApplyDock(state, vm)
            }
        }
    }
}

@Composable
private fun RealityHeader(state: GpuStudioUiState) {
    val device = state.device ?: return
    val accent = when (device.family) {
        GpuHardwareBackend.Family.QUALCOMM -> Color(0xFF4D65C3)
        GpuHardwareBackend.Family.MALI -> Color(0xFF008F73)
        GpuHardwareBackend.Family.UNKNOWN -> MaterialTheme.colorScheme.primary
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = .18f), Color.Transparent))).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = accent.copy(alpha = .16f)) {
                    Icon(Icons.Filled.Memory, null, tint = accent, modifier = Modifier.padding(12.dp).size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${familyLabel(device.family)} • ${if (device.rangeWritable || device.exactLockWritable || device.governorWritable) "تحكم موثّق" else "قراءة فقط"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text(if (state.selection?.state == GpuHardwareBackend.SelectionState.READY) "READY" else "READ ONLY") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("LIVE FREQUENCY", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(formatFrequency(device, device.currentFreq), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LOAD", style = MaterialTheme.typography.labelSmall)
                    Text(device.loadPercent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
            GpuSparkline(state.historyMHz, accent)
            HorizontalDivider(color = accent.copy(alpha = .18f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("النطاق الفعلي", "${formatFrequency(device, device.minFreq)} – ${formatFrequency(device, device.maxFreq)}")
                Metric("الحاكم", device.governor ?: "غير متاح", Alignment.End)
            }
            val ageSeconds = ((System.currentTimeMillis() - (state.selection?.observedAtMs ?: 0L)) / 1000L).coerceAtLeast(0L)
            Text(
                "عمر القراءة: ${ageSeconds}s • حرارة GPU: ${device.thermalC?.let { "$it°C" } ?: "غير متاحة"} • حالة الحماية الحرارية: غير متاحة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GpuSparkline(values: List<Float>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(54.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val step = size.width / (values.size - 1)
        values.zipWithNext().forEachIndexed { index, (a, b) ->
            drawLine(
                color = color,
                start = Offset(index * step, size.height - ((a - min) / span) * size.height),
                end = Offset((index + 1) * step, size.height - ((b - min) / span) * size.height),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun StudioSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun IntentCard(title: String, description: String, icon: ImageVector, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.size(190.dp, 128.dp), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent)
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdvancedLab(state: GpuStudioUiState, vm: GpuStudioViewModel) {
    val device = state.device ?: return
    var expanded by remember { mutableStateOf(false) }
    StudioSection("ADVANCED LAB", "لا يظهر إلا ما يعلنه الدرافر ويمكن التحقق منه") {
        ElevatedCard(shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null)
                        Column {
                            Text("تحكم يدوي موثّق", fontWeight = FontWeight.Bold)
                            Text("Dynamic Range • Exact Lock • Governor", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "إغلاق" else "فتح") }
                }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (device.exactLockWritable) RangeControls(state, vm)
                        else Text("لا توجد قائمة OPP قابلة للكتابة؛ تم إخفاء النطاق والقفل.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (device.governorWritable && device.governors.isNotEmpty()) {
                            Text("الحاكم", fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(device.governors) { governor ->
                                    FilterChip(
                                        selected = (state.pending?.governor ?: device.governor) == governor,
                                        onClick = { vm.stageGovernor(governor) },
                                        label = { Text(governor) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeControls(state: GpuStudioUiState, vm: GpuStudioViewModel) {
    val device = state.device ?: return
    val frequencies = device.frequencies
    if (frequencies.isEmpty()) return
    val liveMinIndex = frequencies.indexOf(state.pending?.minFreq ?: device.minFreq).takeIf { it >= 0 } ?: 0
    val liveMaxIndex = frequencies.indexOf(state.pending?.maxFreq ?: device.maxFreq).takeIf { it >= 0 } ?: frequencies.lastIndex
    var minIndex by remember(device.path, liveMinIndex) { mutableIntStateOf(liveMinIndex) }
    var maxIndex by remember(device.path, liveMaxIndex) { mutableIntStateOf(liveMaxIndex) }
    var exactLock by remember(device.path) { mutableStateOf(!device.rangeWritable) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${if (exactLock) "Exact Lock" else "Dynamic Range"}", fontWeight = FontWeight.Bold)
            FilterChip(
                selected = exactLock,
                enabled = device.rangeWritable,
                onClick = { exactLock = !exactLock },
                label = { Text("قفل دقيق") },
            )
        }
        Text("الحد الأدنى  ${formatFrequency(device, frequencies[minIndex])}")
        Slider(
            value = minIndex.toFloat(),
            enabled = device.rangeWritable || exactLock,
            onValueChange = { minIndex = it.roundToInt().coerceIn(0, maxIndex) },
            onValueChangeFinished = {
                if (exactLock) { maxIndex = minIndex; vm.stageLock(frequencies[minIndex]) }
                else vm.stageRange(frequencies[minIndex], frequencies[maxIndex])
            },
            valueRange = 0f..frequencies.lastIndex.toFloat(),
            steps = (frequencies.size - 2).coerceAtLeast(0),
        )
        Text("الحد الأقصى  ${formatFrequency(device, frequencies[maxIndex])}")
        Slider(
            value = maxIndex.toFloat(),
            enabled = device.rangeWritable || exactLock,
            onValueChange = { maxIndex = it.roundToInt().coerceIn(minIndex, frequencies.lastIndex) },
            onValueChangeFinished = {
                if (exactLock) { minIndex = maxIndex; vm.stageLock(frequencies[maxIndex]) }
                else vm.stageRange(frequencies[minIndex], frequencies[maxIndex])
            },
            valueRange = 0f..frequencies.lastIndex.toFloat(),
            steps = (frequencies.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ApplyDock(state: GpuStudioUiState, vm: GpuStudioViewModel) {
    val device = state.device ?: return
    val pending = state.pending
    val result = state.lastResult
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                result?.verified == true -> MaterialTheme.colorScheme.primaryContainer
                result?.rollbackAttempted == true && result.rollbackVerified != true -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (result?.verified == true) Icons.Filled.CheckCircle else Icons.Filled.Tune, null)
                Text(state.message ?: "معاينة التغيير", fontWeight = FontWeight.Bold)
            }
            if (pending != null) {
                Text("الحالي  ${formatFrequency(device, device.minFreq)} – ${formatFrequency(device, device.maxFreq)}  •  ${device.governor ?: "—"}")
                val requestedLine = if (pending.releaseLock) "تحرير القفل: تحجيم ديناميكي بالكامل"
                else "المطلوب  ${formatFrequency(device, pending.minFreq)} – ${formatFrequency(device, pending.maxFreq)}  •  ${pending.governor ?: device.governor ?: "—"}"
                Text(requestedLine, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::applyPreview,
                        enabled = !state.applying && GpuHardwareBackend.validate(device, pending) == null,
                    ) { Text(if (state.applying) "جارٍ التحقق" else "تطبيق للجلسة") }
                    OutlinedButton(onClick = vm::cancelPreview, enabled = !state.applying) { Text("إلغاء") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::restoreSession, enabled = !state.applying) {
                    Icon(Icons.Filled.Restore, null); Spacer(Modifier.size(6.dp)); Text("استعادة بداية الجلسة")
                }
                if (state.verifiedSnapshot != null) TextButton(onClick = vm::saveVerifiedToTweaks) { Text("حفظ في Tweaks") }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(state: GpuStudioUiState) {
    val device = state.device ?: return
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hardware truth", fontWeight = FontWeight.Bold)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "إخفاء" else "التفاصيل") }
            }
            Text("${device.frequencies.size} OPP • ${device.governors.size} governors • ${state.selection?.reason}", style = MaterialTheme.typography.bodySmall)
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Provider: ${device.path}", style = MaterialTheme.typography.labelSmall)
                    Text("Evidence: ${device.evidence.joinToString()}", style = MaterialTheme.typography.labelSmall)
                    Text("Vendor options: ${familyLabel(device.family)} only", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun familyLabel(family: GpuHardwareBackend.Family): String = when (family) {
    GpuHardwareBackend.Family.QUALCOMM -> "Qualcomm / Adreno"
    GpuHardwareBackend.Family.MALI -> "Mali"
    GpuHardwareBackend.Family.UNKNOWN -> "Generic devfreq"
}

private fun formatFrequency(device: GpuHardwareBackend.Device, raw: Long?): String =
    GpuHardwareBackend.frequencyMHz(device, raw)?.let { "$it MHz" } ?: "—"
