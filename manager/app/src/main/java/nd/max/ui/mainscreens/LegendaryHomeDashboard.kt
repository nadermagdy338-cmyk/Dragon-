/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import nd.max.ui.design.MaxSize

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.shape.CircleShape

import nd.max.ui.theme.MonoValueStyleSmall

import nd.max.ui.theme.MonoValueStyleMedium

import nd.max.ui.theme.MonoValueStyleLarge

import nd.max.ui.design.MaxAlpha

import nd.max.ui.component.neuralClickable

import nd.max.ui.component.NeuralValue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.R
import nd.max.core.maxai.MaxAiState
import nd.max.core.privilege.PrivilegeLevel
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralSparkline
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.MemoryBoostState
import nd.max.ui.util.LoadSample
import kotlin.math.roundToInt

/**
 * The MAX "Now" dashboard.
 *
 * Question the screen answers: how is the device right now, and what can I do about it in one tap?
 * Reading order (طلب المالك): every block earns its place once, and nothing repeats another block.
 *
 *  0. header   — brand, engine state, guide, power and settings.
 *  1. hero     — the first card, unchanged: access chip, device, temperature, three tiles, device overview.
 *  2. pulse    — CPU and GPU: load, clock and the frequency history beneath them.
 *  3. actions  — Boost (measured before/after) and Control, side by side.
 *  4. vitals   — the readings no other block shows: CPU, GPU and surface temperature, and network speed.
 *  5. focus    — storage almost full (hideable), or heat / memory when those are the real problem.
 *  6. memory   — RAM, ZRAM and storage capacity, each row a door.
 *  7. cleaner  — storage fullness and the Ultra Cleaner action.
 *  8. deck     — the control platform: the destinations people reach for, in compact rows.
 *  9. activity — what your choices are doing right now; last, and only when it has verified outcomes.
 *
 * **بلا حركة على البطاقات:** لا بانرات متحركة ولا دخول متتابع ولا تبديل مشهد بانزلاق.
 *
 * Every color comes from MaterialTheme through neuralPalette(), so the Settings theme drives the entire screen.
 */
@Composable
internal fun LegendaryHomeDashboard(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    deviceName: String,
    // بطاقات المنصة تُمرَّر ولا تُكتب هنا (أمر المالك): القاعدة في `homeDeckSelection` وحدها.
    deckEntries: List<HomeDeckEntry>,
    onOpenDeck: (HomeDeckEntry) -> Unit,
    onConfigureDeck: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    /** طبقة الامتياز المكتشفة — من `PrivilegeManager` الموجود لا من قراءة ثانية. */
    accessLevel: PrivilegeLevel,
    /** حالة «تعزيز الذاكرة» الأخيرة، تُعرض على بلاطته. */
    boost: MemoryBoostState,
    onBoost: () -> Unit,
    /** `?` في الرأس: يعيد جولة أول فتح بطلب صريح. */
    onShowGuide: () -> Unit,
    /** مراسي جولة أول فتح (`HomeTourOverlay`)؛ `null` في المعاينات التي لا جولة فيها. */
    tourTargets: HomeTourTargets? = null,
    /** بطاقة «التخزين يكاد يمتلئ»: تظهر حين تُستحقّ ولم يُخفِها المستخدم (`HomeFocusModel`). */
    storageCardVisible: Boolean = false,
    onHideStorage: () -> Unit = {},
) {
    val online = ui.rootStatus && ui.moduleInstalled
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        HomeHeader(online, onSettings, onReboot, onShowGuide)
        Box(Modifier.homeTourTarget(HomeTourTarget.Hero, tourTargets)) {
            HomeHeroCard(
                deviceName = deviceName,
                dashboard = dashboard,
                accessLevel = accessLevel,
                // من مسارها الحقيقي (`MaxAiState.aiEnabled`) لا مخمَّنة من وجود الشاشة.
                aiEnabled = maxAi.aiEnabled,
                // نظرة الجهاز: المالك الوحيد لفكرة «نظرة على الجهاز» (طلب المالك).
                onOverview = { onNavigate(MaxDestination.DeviceInfo.route) },
                onMaxAi = { onNavigate(MaxDestination.MaxAi.route) },
            )
        }
        Box(Modifier.homeTourTarget(HomeTourTarget.Pulse, tourTargets)) {
            HardwarePulseCards(
                dashboard = dashboard,
                onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
                onGpu = { onNavigate(MaxDestination.GpuStudio.route) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            NeuralSectionHeader(title = stringResource(R.string.home_section_actions))
            Box(Modifier.homeTourTarget(HomeTourTarget.Actions, tourTargets)) {
                HomeActionRow(
                    boost = boost,
                    onBoost = onBoost,
                    onControl = { onNavigate(MaxDestination.Control.route) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            NeuralSectionHeader(
                title = stringResource(R.string.home_section_live),
                caption = stringResource(R.string.home_section_live_caption),
            )
            Box(Modifier.homeTourTarget(HomeTourTarget.Vitals, tourTargets)) {
                HomeVitalsGrid(dashboard = dashboard, onNavigate = onNavigate)
            }
        }
        FocusCard(dashboard, onNavigate, storageCardVisible, onHideStorage)
        Box(Modifier.homeTourTarget(HomeTourTarget.Memory, tourTargets)) {
            MemoryMatrixCard(dashboard = dashboard, onNavigate = onNavigate)
        }
        Box(Modifier.homeTourTarget(HomeTourTarget.Cleaner, tourTargets)) {
            UltraCleanerHomeCard(dashboard = dashboard, onNavigate = onNavigate)
        }
        Box(Modifier.homeTourTarget(HomeTourTarget.Deck, tourTargets)) {
            CommandDeck(entries = deckEntries, onOpen = onOpenDeck, onConfigure = onConfigureDeck)
        }
        UnifiedActivityCard(maxAi = maxAi)
    }
}



/**
 * Brand, one state, two actions. The engine state is said once: it used to be a pill
 * ("active/idle", dot and color) *and* a subtitle line saying the same thing in words,
 * which is the definition of a screen that repeats itself. The pill keeps more
 * information (color, dot, localized word), so the duplicate line went.
 */
@Composable
private fun HomeHeader(
    online: Boolean,
    onSettings: () -> Unit,
    onReboot: () -> Unit,
    onShowGuide: () -> Unit,
) {
    val p = neuralPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "MAX",
                color = p.text,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
            )
        }
        NeuralPill(
            text = stringResource(if (online) R.string.home_active else R.string.home_idle),
            accent = if (online) p.ok else p.danger,
            filled = true,
            dot = true
        )
        // **`?` قبل الزرّين بأمر §5.1:** الجولة تُطلب من الرأس لا من داخل البطاقة، ومن
        // أتمّها ثم أرادها يعود إلى هنا — فلا بحث في الشاشة عن مفتاح إعادة العرض.
        HeaderButton(
            Icons.Rounded.HelpOutline,
            stringResource(R.string.home_banner_restore),
            onShowGuide
        )
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), onReboot)
        Spacer(Modifier.width(6.dp))
        HeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), onSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val p = neuralPalette()
    // 13dp لم تكن على سلّم نصف القطر؛ مرامي التحكّم هو نفسه نصف قطر التحكّم في العقد.
    val shape = RoundedCornerShape(MaxRadius.control)
    Box(
        Modifier
            .size(38.dp)
            .clip(shape)
            .background(p.tile)
            .border(BorderStroke(1.dp, p.border), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(18.dp), tint = p.muted)
    }
}

/**
 * The signature block. One headline reading (heat plus a plain-language verdict)
 * over three pressure meters, because the first question is never "what is CPU
 * load" but "is anything under pressure, and is the device hot". Straight bars
 * beat a dial here: they share one baseline, so three values are comparable at a
 * glance and every label has room to breathe in either writing direction.
 */
/**
 * CPU/GPU live hardware pair for the home screen.
 *
 * The card intentionally shows load as the headline and the *actual current clock*
 * underneath it. The sparkline is frequency history, not load history, so a user can
 * immediately see clock stepping/boost behavior instead of getting a second copy of
 * the same percentage graph.
 */
@Composable
private fun HardwarePulseCards(
    dashboard: DashboardState,
    onCpu: () -> Unit,
    onGpu: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FrequencyMetricCard(
            title = "GPU",
            percent = dashboard.gpuLoadPercent,
            frequencyMhz = dashboard.gpuFreqMhz,
            ceilingMhz = dashboard.gpuCeilingMhz,
            floorMhz = dashboard.gpuMinMhz,
            samples = dashboard.loadSamples,
            isGpu = true,
            // الثلاثة بعد تحسم الفرق الذي طلبه المالك: صدر «Max freq» الحيّ (مثل 754)،
            // يمين الأسفل «أقصى مدعوم» من كتالوج الدرجات (مثل 1300)، وأرضية اليسار كما
            // كانت. والتردد الجاري (مثل 260) صار في الموجة وحدها — لا يُخلط بسقفٍ ولا مدعوم.
            topMhz = dashboard.gpuCeilingMhz,
            topLabel = stringResource(R.string.home_gpu_max_freq),
            rangeMaxMhz = dashboard.gpuMaxSupportedMhz,
            accent = neuralPalette().accentAlt,
            onClick = onGpu,
            modifier = Modifier.weight(1f)
        )
        FrequencyMetricCard(
            title = "CPU",
            percent = dashboard.cpuLoadPercent,
            frequencyMhz = dashboard.cpuTopCoreMhz.takeIf { it > 0 },
            ceilingMhz = dashboard.cpuCeilingMhz.takeIf { it > 0 },
            floorMhz = dashboard.cpuMinMhz?.takeIf { it > 0 },
            samples = dashboard.loadSamples,
            isGpu = false,
            accent = neuralPalette().accent,
            onClick = onCpu,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FrequencyMetricCard(
    title: String,
    percent: Int?,
    frequencyMhz: Int?,
    ceilingMhz: Int?,
    floorMhz: Int?,
    samples: List<LoadSample>,
    isGpu: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topMhz: Int? = null,
    topLabel: String? = null,
    rangeMaxMhz: Int? = null,
) {
    val p = neuralPalette()
    val current = frequencyMhz?.takeIf { it > 0 }
    val history = samples.mapNotNull { sample ->
        val value = if (isGpu) sample.gpuMhz else sample.cpuMhz
        value?.takeIf { it > 0 }?.toFloat()
    }
    // مقياس الرسم = المدى المقيس أو المعلن، لا صفر مصنوع (انظر التعليق في `HardwarePulseCards`).
    val rangeCeiling = max(
        ceilingMhz?.takeIf { it > 0 }?.toFloat() ?: 0f,
        max(current?.toFloat() ?: 0f, history.maxOrNull() ?: 0f)
    )
    val graph = if (rangeCeiling > 0f) {
        samples.mapNotNull { sample ->
            val value = if (isGpu) sample.gpuMhz else sample.cpuMhz
            value?.takeIf { it > 0 }?.toFloat()?.div(rangeCeiling)
        }.takeLast(36)
    } else emptyList()
    val floor = floorMhz?.takeIf { it > 0 }
    val floorFraction = floor?.toFloat()?.div(rangeCeiling)?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val rangeMax = rangeMaxMhz?.takeIf { it > 0 } ?: ceilingMhz?.takeIf { it > 0 }
    val shape = RoundedCornerShape(MaxRadius.group)
    val unknown = "\u2014"

    Column(
        modifier
            .clip(shape)
            .background(p.tile.copy(alpha = .92f))
            .border(MaxSize.hairlineBorder, accent.copy(alpha = MaxAlpha.borderStrong), shape)
            .neuralClickable(onClick, role = Role.Button)
            .padding(MaxSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        // الرأس: النقطة والاسم وسهم الباب، والنسبة الكبيرة (القراءة الرئيسية للبطاقة) على اليمين.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(MaxSpace.sm).clip(CircleShape).background(accent))
                Spacer(Modifier.width(MaxSpace.xs))
                Text(title, color = p.muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(MaxSpace.xs))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    Modifier.size(MaxSize.iconGlyphSmall),
                    tint = accent,
                )
            }
            NeuralValue(
                percent?.coerceIn(0, 100)?.let { "$it%" } ?: unknown,
                style = MonoValueStyleLarge,
                color = p.text,
            )
        }
        // سطر التردد: الصدر (السقف الحيّ للـGPU بتسميته، أو الجاري للـCPU) — تسمية صغيرة ثم رقم أحادي.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (topLabel != null) {
                Text(
                    topLabel.uppercase(),
                    color = p.muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Spacer(Modifier.width(MaxSpace.xs))
            }
            NeuralValue(
                formatHardwareFrequency(topMhz?.takeIf { it > 0 } ?: current),
                style = MonoValueStyleMedium,
                color = p.text,
                maxLines = 1,
            )
        }
        // فاصل شعريّ يفصل القراءة عن الرسم.
        Box(Modifier.fillMaxWidth().height(MaxSize.hairlineBorder).background(p.border))
        NeuralSparkline(
            samples = graph,
            accent = accent,
            floorFraction = floorFraction,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        // المدى: الأرضية يسارًا والسقف يمينًا بالخط الأحادي، وكلٌّ يُكتب إن أُعلن فقط.
        if (floor != null || rangeMax != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NeuralValue(
                    formatHardwareFrequency(floor),
                    style = MonoValueStyleSmall,
                    color = p.muted,
                    maxLines = 1,
                )
                NeuralValue(
                    formatHardwareFrequency(rangeMax),
                    style = MonoValueStyleSmall,
                    color = p.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatHardwareFrequency(mhz: Int?): String {
    val value = mhz?.takeIf { it > 0 } ?: return "—"
    return if (value >= 1000) {
        val ghz = value / 1000f
        if (ghz >= 10f) "${ghz.toInt()} GHz"
        else "${"%.1f".format(java.util.Locale.US, ghz)} GHz"
    } else "$value MHz"
}


/**
 * لا تظهر إلا حين توجد مشكلة حقيقية، فحضورها نفسه يعني شيئًا.
 *
 * **الإخفاء لبطاقة التخزين وحدها** (طلب المالك): الحرارة والذاكرة تبقيان على حالهما. وتظهر بطاقة التخزين
 * حين تستحقّها `HomeFocusModel` ولم يُخفِها المستخدم، ويأتي زرّ «إخفاء» في نهاية سطرها.
 */
@Composable
private fun FocusCard(
    dashboard: DashboardState,
    onNavigate: (String) -> Unit,
    storageVisible: Boolean,
    onHideStorage: () -> Unit,
) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt() ?: dashboard.cpuTempC
    val ram = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)

    val title: String
    val body: String
    val accent: Color
    val icon: ImageVector
    val route: String
    val hideAction: (() -> Unit)?
    when {
        heat >= 45 -> {
            title = stringResource(R.string.home_focus_heat)
            body = stringResource(R.string.home_focus_heat_desc)
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            route = MaxDestination.ThermalDetail.route
            hideAction = null
        }
        storageVisible -> {
            title = stringResource(R.string.home_focus_storage)
            body = stringResource(R.string.home_focus_storage_desc)
            accent = p.warn
            icon = Icons.Rounded.Storage
            route = MaxDestination.StorageDetail.route
            hideAction = onHideStorage
        }
        ram >= 0.90f -> {
            title = stringResource(R.string.home_focus_memory)
            body = stringResource(R.string.home_focus_memory_desc)
            accent = p.accentAlt
            icon = Icons.Rounded.Speed
            route = MaxDestination.ZramManager.route
            hideAction = null
        }
        else -> return
    }
    NeuralPanel(accent = accent, onClick = { onNavigate(route) }, verticalSpacing = 8.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(icon, accent, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    color = p.text,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hideAction != null) {
                Spacer(Modifier.width(MaxSpace.sm))
                NeuralPill(
                    text = stringResource(R.string.home_focus_hide),
                    accent = p.muted,
                    compact = true,
                    onClick = hideAction,
                )
            }
        }
    }
}
