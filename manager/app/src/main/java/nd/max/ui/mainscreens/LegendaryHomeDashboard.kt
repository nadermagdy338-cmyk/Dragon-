/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

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
import nd.max.ui.component.MaxReveal
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
 * Question the screen answers: how is the device right now, and what can I do about it in
 * one tap? Reading order, each block earning its place exactly once
 * (`MAX-MANAGER-LEVEL-UP.md` §5.1):
 *
 *  0. header   — brand, engine state, guide, power and settings.
 *  1. hero     — device identity, access mode with its honest sentence, MAX AI and Device Info
 *                doors (`HomeHeroCard`).
 *  2. banners  — wide cards that move on their own and stop when touched (`HomeGuideStrip`).
 *  3. vitals   — CPU clock · RAM free · heat · battery as a 2×2 grid, each a door
 *                (`HomeVitalsGrid`).
 *  4. actions  — Boost (measured before/after) and Control (`HomeActionRow`).
 *  5. pulse    — GPU | CPU as frequency *history*; the grid above is the *moment*.
 *  6. activity — what your choices are doing right now (verified outcomes only).
 *  7. focus    — appears only when something is actually wrong.
 *  8. memory   — RAM, ZRAM and storage capacity, each row a door.
 *  9. cleaner  — storage fullness and the Ultra Cleaner action.
 * 10. deck     — destinations people actually reach for.
 *
 * **الجولة الثانية (شكل الرئيسية):** الأولى أسقطت شبكة الحيوية ٢×٢ بحجّة أن ما فيها معروض،
 * فبقيت الشاشة كتلًا مكدّسة وبطلًا ضخمًا يحمل رقمًا واحدًا هو `—`. والعلاج ليس الحذف بل **تقسيم
 * الأدوار**: الشبكة = اللحظة، وبطاقة CPU/GPU = التاريخ، والبطل = الهوية فقط. فلا رقم يُرسم مرّتين
 * بالحجم نفسه: الحرارة والبطارية ومدّة التشغيل خرجت من البطل. و`VerdictPanel` تبقى محذوفة
 * (`HOME-STORY-TRIM-01`)، والحُكم الباقي `FocusCard` عند عطل حقيقي فقط.
 *
 * **وADR-34 قائم:** مصفوفة الذاكرة تعرض سعة لا حُكم ضغط، وخلية RAM تعرض المتاح بلا لون إنذار.
 *
 * Every color comes from MaterialTheme through neuralPalette(), so the Settings theme
 * drives the entire screen.
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
    /** هل أُتمّت جولة البانرات؟ (سجلّ الجهاز — `HomeGuideStore`). */
    guideFinished: Boolean,
    onGuideFinish: () -> Unit,
    /** حالة «تعزيز الذاكرة» الأخيرة، تُعرض تحت زرّ Boost. */
    boost: MemoryBoostState,
    onBoost: () -> Unit,
    /** `?` في الرأس: يعيد جولة البانرات بطلب صريح (`HomeGuideModel`). */
    onShowGuide: () -> Unit,
    /** مراسي جولة أول فتح (`HomeTourOverlay`)؛ `null` في المعاينات التي لا جولة فيها. */
    tourTargets: HomeTourTargets? = null,
) {
    val online = ui.rootStatus && ui.moduleInstalled
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        /*
         * الدخول المتتابع (عقد §5.4-أ): الرقم **مشتقّ من الموضع** (`slot`) لا مكتوب لكل كتلة،
         * و`MaxReveal` يقصّ التأخير عند حدّه ويحترم إيقاف الحركة في النظام.
         */
        var slot = 0
        MaxReveal(delayMillis = RevealStep * slot++) {
            HomeHeader(online, onSettings, onReboot, onShowGuide)
        }
        MaxReveal(delayMillis = RevealStep * slot++) {
            Box(Modifier.homeTourTarget(HomeTourTarget.Hero, tourTargets)) {
                HomeHeroCard(
                    deviceName = deviceName,
                    chipsetName = dashboard.chipsetName,
                    uptimeMinutes = dashboard.uptimeMinutes,
                    accessLevel = accessLevel,
                    // من مسارها الحقيقي (`MaxAiState.aiEnabled`) لا مخمَّنة من وجود الشاشة.
                    aiEnabled = maxAi.aiEnabled,
                    // معلومات الجهاز: المالك الوحيد لفكرة «نظرة على الجهاز» (طلب المالك).
                    onOverview = { onNavigate(MaxDestination.DeviceInfo.route) },
                    // مدخل Max AI من أول بطاقة (طلب المالك): كان مقعدًا في الشريط السفلي.
                    onMaxAi = { onNavigate(MaxDestination.MaxAi.route) },
                )
            }
        }
        // من أتمّ البانرات لا تُحجز لها خانة، وإلا بقي فراغ بين كتلتين بلا محتوى.
        if (HomeGuideModel.visible(guideFinished)) {
            MaxReveal(delayMillis = RevealStep * slot++) {
                HomeGuideStrip(finished = guideFinished, onFinish = onGuideFinish)
            }
        }
        MaxReveal(delayMillis = RevealStep * slot++) {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
                NeuralSectionHeader(
                    title = stringResource(R.string.home_section_live),
                    caption = stringResource(R.string.home_section_live_caption),
                )
                Box(Modifier.homeTourTarget(HomeTourTarget.Vitals, tourTargets)) {
                    HomeVitalsGrid(dashboard = dashboard, onNavigate = onNavigate)
                }
            }
        }
        MaxReveal(delayMillis = RevealStep * slot++) {
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
        }
        MaxReveal(delayMillis = RevealStep * slot++) {
            Box(Modifier.homeTourTarget(HomeTourTarget.Pulse, tourTargets)) {
                HardwarePulseCards(
                    dashboard = dashboard,
                    onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
                    onGpu = { onNavigate(MaxDestination.GpuStudio.route) },
                )
            }
        }
        MaxReveal(delayMillis = RevealStep * slot++) { UnifiedActivityCard(maxAi = maxAi) }
        MaxReveal(delayMillis = RevealStep * slot++) { FocusCard(dashboard, onNavigate) }
        // السعة ثم الفعل: من قرأ «كم بقي» يجد تحته «ما أستطيع تحريره».
        MaxReveal(delayMillis = RevealStep * slot++) {
            Box(Modifier.homeTourTarget(HomeTourTarget.Memory, tourTargets)) {
                MemoryMatrixCard(dashboard = dashboard, onNavigate = onNavigate)
            }
        }
        MaxReveal(delayMillis = RevealStep * slot++) {
            Box(Modifier.homeTourTarget(HomeTourTarget.Cleaner, tourTargets)) {
                UltraCleanerHomeCard(dashboard = dashboard, onNavigate = onNavigate)
            }
        }
        MaxReveal(delayMillis = RevealStep * slot++) {
            Box(Modifier.homeTourTarget(HomeTourTarget.Deck, tourTargets)) {
                CommandDeck(entries = deckEntries, onOpen = onOpenDeck, onConfigure = onConfigureDeck)
            }
        }
    }
}

/**
 * إيقاع الدخول بين كتلتين متجاورتين.
 *
 * و`MaxReveal` يقصّ التأخير عنده حده (160ms) فلا يتجاوز آخر بلوك سقف الحركة، والرقم هنا
 * **ثابت واحد** يُضرب في الموضع — لا جدول تأخيرات مكتوب بيد يتخلّف عن التخطيط عند أول تعديل.
 */
private const val RevealStep = 40

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
    /*
     * فتحتا العرض اللتان تختلف البطاقتان في ملئهما لا في قراءتهما — والفارق رقمٌ حقيقيّ
     * لا تسمية: صدر بطاقة GPU يعرض **السقف الحيّ** (`max_freq` المسموح به الآن، مثل 754)
     * بتسميته «Max freq»، وأقصى اليمين **أقصى ما تُعلنه الدرجات** (مثل 1300) — وهما
     * اثنان لا يُخلط بينهما بعد اليوم (كانت البطاقة تعرض الجاري في صدرها). وبلا تعبئة
     * يبقى سلوك CPU كما هو: الصدر = التردد الجاري بلا تسمية، واليمين = السقف.
     */
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
    /*
     * مقياس الرسم = المدى الحقيقي المتحلّى، وما رصدناه كاحتياط.
     *
     * ولا يجوز أن يكون **الصفر** سقفًا: الرسوم كانت تُبنى من المدى المعلن وحده، فجهاز لا تُعلن
     * نواته سقفًا (أو تُعلنه بوحدة غير موثوقة) كان يحصل على `graphCeiling = 0`، أي رسم فارغ
     * — بينما تردّداته المقيسة موجودة في العيّنات. والاحتياط هنا `max(ceiling, current,
     * observed)` لا سقفًا مصنوعًا: كل حدّ فيه رقم مقيس أو معلن، فلا يُخترع مدى.
     */
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

    Column(
        modifier
            .clip(RoundedCornerShape(MaxRadius.group))
            .background(p.tile.copy(alpha = .92f))
            .border(BorderStroke(1.dp, accent.copy(alpha = .26f)), RoundedCornerShape(MaxRadius.group))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    color = p.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                // سهمُ الباب بعد الاسم لا قبله (نفس عُرف `NeuralPill(navigates)` وصفوف
                // مصفوفة الذاكرة): البطاقتان تقودان فعلًا إلى شاشتيهما (`GpuStudio` من
                // `onGpu` · `CpuCoreControl` من `onCpu`) — وكانتا تُقرآن بيانًا لا بابًا.
                // وهذا آخر موضعٍ من صنف عطب المالك («لا يدل على أنه سيدخلك إلى شاشة أخرى»)
                // في هذه الشاشة. وحجم السهم 13.dp لا 14.dp كسهم الوسوم: عنوان هذه البطاقة
                // `11.sp` بوزن `Medium` (اسمٌ رماديّ صغير)، فسهمٌ أكبر منه كان سيصير أبرزَ من الاسم.
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    Modifier.size(13.dp),
                    tint = accent,
                )
            }
            Text(
                "${percent?.coerceIn(0, 100) ?: 0}%",
                color = p.text,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            // صدر البطاقة: للـGPU السقف الحيّ بتسميته («Max freq 754 MHz»)، ولـCPU التردد
            // الجاري كما كان — فتحةٌ واحدة لا تتغيّر إلا في ملئها.
            (topLabel?.let { "$it " } ?: "") +
                formatHardwareFrequency(topMhz?.takeIf { it > 0 } ?: current),
            color = p.muted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        // والموجة من الكومبوننت المشترك (`NeuralSparkline`) لا من نسخة محليّة: شاشة CPU
        // ترسم الموجة نفسها لكل نواة، ونسختان تفترقان عند أوّل تعديل.
        NeuralSparkline(
            samples = graph,
            accent = accent,
            floorFraction = floorFraction,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        // حدّا المدى تحت الرسم، كلُّ حدٍّ تحت المستوى الذي يمثّله فعلًا: الأرضية خطُّ إسناد
        // مرسوم داخل الرسم، والسقف أعلاه. ولمّا يُعلن أيّهما لا يُكتب شيء — فسطر «— —»
        // ليس مدى، وقد يُقرأ كصفر.
        val rangeMax = rangeMaxMhz?.takeIf { it > 0 } ?: ceilingMhz?.takeIf { it > 0 }
        if (floor != null || rangeMax != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatHardwareFrequency(floor),
                    color = p.muted.copy(alpha = .75f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
                Text(
                    formatHardwareFrequency(rangeMax),
                    color = p.muted.copy(alpha = .75f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
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


/** Shown only when a real problem exists, so its presence itself means something. */
@Composable
private fun FocusCard(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt() ?: dashboard.cpuTempC
    val ram = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)
    val storageFree = if (dashboard.storageTotalGb <= 0f) 1f else
        ((dashboard.storageTotalGb - dashboard.storageUsedGb) / dashboard.storageTotalGb).coerceIn(0f, 1f)

    val title: String
    val body: String
    val accent: Color
    val icon: ImageVector
    val route: String
    when {
        heat >= 45 -> {
            title = stringResource(R.string.home_focus_heat)
            body = stringResource(R.string.home_focus_heat_desc)
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            route = MaxDestination.ThermalDetail.route
        }
        storageFree < 0.10f -> {
            title = stringResource(R.string.home_focus_storage)
            body = stringResource(R.string.home_focus_storage_desc)
            accent = p.warn
            icon = Icons.Rounded.Storage
            route = MaxDestination.StorageDetail.route
        }
        ram >= 0.90f -> {
            title = stringResource(R.string.home_focus_memory)
            body = stringResource(R.string.home_focus_memory_desc)
            accent = p.accentAlt
            icon = Icons.Rounded.Speed
            route = MaxDestination.ZramManager.route
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
        }
    }
}
