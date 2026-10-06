/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import nd.max.ui.component.MaxAiEntryButton
import nd.max.ui.component.MaxReveal
import nd.max.ui.component.NeuralPalette
import nd.max.ui.component.NeuralSparkline
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.MemoryBoostState
import nd.max.ui.util.LoadSample
import kotlin.math.roundToInt

/**
 * The MAX "Now" dashboard.
 *
 * Question the screen answers: how is the device right now, and what is the one
 * thing worth touching? Everything that only explained MAX to itself (system
 * passport, AI console, the old "live performance" gauge stack, the base
 * profile row) is gone; those live in Max AI, Control and Diagnostics.
 *
 * Reading order, each block earning its place exactly once (`MAX-MANAGER-LEVEL-UP.md` §5.1):
 *  0. header  — brand, engine state, power and settings
 *  1. hero    — device identity, **access mode**, heat, MAX AI, uptime/battery/power draw,
 *               and the door to Device Info. One honest number is allowed to be loud here.
 *  2. guide   — the moving banners, until the tour is finished (§5.4).
 *  3. pulse   — CPU | GPU as live clocks with frequency history, not a second load gauge.
 *  4. activity— what your choices are actually doing right now: the last per-app session's
 *               verified hardware results, MAX AI's owned knobs, and your own manual locks.
 *               It states outcomes, never instruments.
 *  5. focus   — appears only when something is actually wrong.
 *  6. matrix  — memory and storage capacity: RAM, ZRAM and internal storage side by side,
 *               now with the two actions that make it usable (Boost, and the cleaner door).
 *  7. cleaner — storage pressure and the door to Ultra Cleaner (§6).
 *  8. deck    — four destinations people actually reach for.
 *
 * **ورحلت `VerdictPanel` («قصة الأداء») بأمر المالك (`HOME-STORY-TRIM-01`):** كانت بطاقةً
 * بعنوان وشريط ثقة وحُكم محدِّد وجدول أحداث، تجيب سؤالًا لم يسأله أحد عند فتح التطبيق، وتُكرّر
 * ما تقوله ثلاث بطاقات فوقها. ومنطقها (`PerformanceIntelligence`) حُذف من المشروع لا من
 * هذه الشاشة وحدها: بلا مستهلك لا معنى لعمل دوري في مسار القياس. ولا تُستبدل ببطاقة «ذكاء»
 * أخرى فارغة — الحُكم الباقي هو `FocusCard`، وهي تظهر عند عطل حقيقي فقط.
 *
 * **وما لم يُبنَ من الخطة عن قصد (°5.3):** «شبكة الحيوية 2×2» لا تُضاف، لأن ما فيها موجود
 * أقوى منها: بطاقة CPU/GPU تعرض **تاريخًا** من 36 عيّنة لا رقمًا، والحرارة والبطارية والوقت
 * وواط السحب في البطل، والذاكرة في مصفوفتها. وخليّة `CPU` بجانب بطاقة CPU هي الرسم الثاني
 * لنفس الرقم — وهو ما يمنعه عقد هذه الشاشة نفسه («لا يُرسم قياس مرّتين»).
 *
 * The measurement panels that duplicated owner screens (CPU/GPU load tiles, the
 * core matrix, RAM/swap/storage budgets) are gone from here per the storyboard plan's
 * phase 4: load lives in CPU and GPU, memory in ZRAM and Storage, cores and display in
 * their own screens. Nothing was lost — the same numbers are one tap away — and nothing
 * is drawn twice on this screen: heat, uptime, battery and power appear exactly once,
 * in the pulse block.
 *
 * والأستثناء الوحيد على المرحلة ٤ هو بطاقة المصفوفة (`MemoryMatrixCard`) بناءً على طلب
 * المالك الصريح: تعود **كقدرة** (المستخدَم من الإجمالي والمتاح) لا **كحُكم ضغط** — فالحُكم
 * يبقى حيث كان: `FocusCard` هي وحدها ما يقول «يكاد يمتلئ»، والمحرك يقيس الضغط بـPSI
 * (`ADR-34`). والحدّ بينهما مقصود: الامتلاء حقيقة سعة، وأثره على الأداء قياس آخر.
 *
 * No metric is drawn twice. The rule is enforced by subtraction, not by hope: heat is a
 * number in the pulse block and nowhere else, uptime/battery/power appear once, the engine
 * state is stated once (the pill, not a pill plus a subtitle), and the load sparklines that
 * used to sit here were the third drawing of the same series — their screens own them now.
 * And "nothing is lost" was verified, not assumed: uptime and power draw have no owner
 * screen yet, which is exactly why the pulse block kept them instead of dropping them.
 * Every color comes from MaterialTheme through neuralPalette(), so the Settings theme
 * drives the entire screen.
 */

@Composable
internal fun LegendaryHomeDashboard(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    deviceName: String,
    // **وبطاقات المنصة تُمرَّر ولا تُكتب هنا (أمر المالك):** عددها ٤ إلى ٦ وترتيبها يتبع
    // الاستعمال أو اختيار المستخدم — والقاعدة في `homeDeckSelection` وحدها، فلا نسخة ثانية
    // منها في الرسم. التفصيل في `CommandDeck` و`HomeDeckModel`.
    deckEntries: List<HomeDeckEntry>,
    onOpenDeck: (HomeDeckEntry) -> Unit,
    onConfigureDeck: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    /**
     * طبقة الامتياز المكتشفة — تُقرأ من `PrivilegeManager` الموجود لا من قراءة ثانية.
     *
     * وهي **حالة لا شرح** (طلب §5.2-2): شارة تحمل الاسم، وسطر واحد يقول ما يُفتح وما يُقفل.
     */
    accessLevel: PrivilegeLevel,
    /** هل أُتمّت جولة البانرات؟ (سجلّ الجهاز — `HomeGuideStore`). */
    guideFinished: Boolean,
    onGuideFinish: () -> Unit,
    /** حالة «تعزيز الذاكرة» الأخيرة، تُعرض في بطاقة الذاكرة. */
    boost: MemoryBoostState,
    onBoost: () -> Unit,
    /** `?` في الرأس: يعيد جولة البانرات بطلب صريح (`HomeGuideModel`). */
    onShowGuide: () -> Unit,
) {
    val online = ui.rootStatus && ui.moduleInstalled
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        /*
         * الدخول المتتابع (عقد §5.4-أ): كل كتلة تفيد ثم تُسلّم العين إلى التي تحتها.
         * والتأخير يزيد بمقدار ثابت عبر `index`، و`MaxReveal` نفسه يقصّه عنده حدّه (160ms)
         * ويحترم إيقاف الحركة في النظام — فلا يُضبط رقم الدخول هنا.
         *
         * والرقم **مشتقّ من الموضع** لا مكتوب لكل كتلة: إدخال كتلة في الوسط لا يترك فجوة في
         * الإيقاع، وهو نفس مبدأ «لا رقمين لفكرة واحدة» في بقية الشاشة.
         */
        var slot = 0
        MaxReveal(delayMillis = RevealStep * slot++) {
            HomeHeader(online, onSettings, onReboot, onShowGuide)
        }
        MaxReveal(delayMillis = RevealStep * slot++) { PulsePanel(
            deviceName = deviceName,
            dashboard = dashboard,
            accessLevel = accessLevel,
            // حالة الوعي في Max AI تُقرأ من مسارها الحقيقي (`MaxAiState.aiEnabled`) ولا تُخمَّن من
            // وجود الشاشة: باقي الواجهة تكذب ADR-07 ("المجهول يُعلَن مجهولًا")، ووجود الشاشة
            // لا يقول مُشغّل من متوقّف.
            aiEnabled = maxAi.aiEnabled,
            // الزرّ يفتح **شاشة معلومات الجهاز** (`Device Info`): هي التي تملك هذه الفكرة
            // بأقسامها الأحد عشر، وفي كل قسم **بابٌ إلى الشاشة المشابهة له** (طلب المالك:
            // «كل قسم مرتبط بالشاشة المشابه له») — فيرى المستخدم المتقدّم ما يطلبه من موضع واحد،
            // ولا تُزحم بقية الشاشات بزرّ في كل بطاقة. وكانت الحبّة تفتح `Diagnostics` —
            // تشخيصًا لا نظرة جهاز — فصار المدخل إلى مالك الموضوع، و`Diagnostics` في الإعدادات.
            onOverview = { onNavigate(MaxDestination.DeviceInfo.route) },
            // مدخل Max AI من أول بطاقة (طلب المالك): كان مقعدًا في الشريط السفلي، وصار
            // بوّابة في البطاقة التي تُقرأ أولًا — والمقعد الذي أخلاه صار للإعدادات.
            onMaxAi = { onNavigate(MaxDestination.MaxAi.route) }
        ) }
        MaxReveal(delayMillis = RevealStep * slot++) {
            HomeGuideStrip(finished = guideFinished, onFinish = onGuideFinish)
        }
        MaxReveal(delayMillis = RevealStep * slot++) { HardwarePulseCards(
            dashboard = dashboard,
            onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
            onGpu = { onNavigate(MaxDestination.GpuStudio.route) }
        ) }
        // بطاقة نشاط واحدة: تحكي الأثر المؤكد فقط، وتترك القياسات لشاشاتها المالكة.
        MaxReveal(delayMillis = RevealStep * slot++) { UnifiedActivityCard(maxAi = maxAi) }
        MaxReveal(delayMillis = RevealStep * slot++) { FocusCard(dashboard, onNavigate) }
        // مصفوفة الذاكرة تحت بطاقة التحذير مباشرة: من رأى «التخزين يكاد يمتلئ» يجد
        // تحته الأرقام التي تشرح العبارة، بلا أن يعيد هذا السطر إطلاق الحُكم نفسه.
        MaxReveal(delayMillis = RevealStep * slot++) {
            MemoryMatrixCard(
                dashboard = dashboard,
                onNavigate = onNavigate,
                boost = boost,
                onBoost = onBoost,
            )
        }
        // وبطاقة التنظيف تحت المصفوفة: كلتاهما عن السعة، والقارئ ينتقل من «كم بقي» إلى
        // «ما أستطيع تحريره» بلا بطاقة ثالثة بينهما تفصل السؤال عن جوابه.
        MaxReveal(delayMillis = RevealStep * slot++) {
            UltraCleanerHomeCard(dashboard = dashboard, onNavigate = onNavigate)
        }
        MaxReveal(delayMillis = RevealStep * slot++) { CommandDeck(
            entries = deckEntries,
            onOpen = onOpenDeck,
            onConfigure = onConfigureDeck,
        ) }
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


/**
 * مقاس البطاقة الأولى — **٩٥٪ من مقاسها السابق** بأمر المالك («اجعل أوّل بطاقة في الشاشة
 * الرئيسية أصغر بنسبة ٥٪»).
 *
 * ومعامل جامع لا تخفيض في رقم أو رقمين: البطاقة تُقرأ **بلوكًا واحدًا**، فتخفيض الرقم الكبير
 * وحده يُقرأ «حرارةً أصغر في بطاقةٍ كما هي»، وتخفيض الحشو وحده يُقرأ «بطاقةً أوسع». فالضرب
 * يجري على ما تملكه هذه البطاقة من أرقام — الحشو ١٨ ← ١٧٫١ · الفراغ الرأسي ١٦ ← ١٥٫٢ ·
 * مربّع الأيقونة ٤٠ ← ٣٨ · اسم الجهاز ١٦sp ← ١٥٫٢sp · سطر الشريحة ١١sp ← ١٠٫٤٥sp ·
 * الرقم الكبير ٤٤sp ← ٤١٫٨sp (وسطره ٤٨ ← ٤٥٫٦) · فُرَج الصفّ ٨ ← ٧٫٦.
 *
 * **وما لم يُشمل بسببه:** المكوّنات المشتركة (`MaxAiEntryButton` · `NeuralPill` · `NeuralFactTile`)
 * — لأوّلها وثانيها **صندوق لمس ٤٨dp** وهو حدّ سياسة لا ذوق (تصغيره إلى ٤٥٫٦dp يخالف §١٣)،
 * وللثلاثة مستعملون خارج هذه الشاشة فمقاسها ليس ملكها. **وحدّ القياس:** النسبة مقيسة، وأثرها
 * على العين **يحتاج جهازًا** (التفصيل في `HANDOFF` ← تكملة ٢٠٧).
 */
private const val PULSE_SCALE = 0.95f

@Composable
private fun PulsePanel(
    deviceName: String,
    dashboard: DashboardState,
    /** طبقة الامتياز المكتشفة — تُقرأ في الشارة والسطر تحتها، لا في شرح منفصل. */
    accessLevel: PrivilegeLevel,
    /** حالة Max AI الحقيقية — تُعلّم المؤشّر في زرّه بلا سطر يشرح. */
    aiEnabled: Boolean,
    onOverview: () -> Unit,
    onMaxAi: () -> Unit
) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val heatAccent = temperatureAccent(heat)
    val calm = heat == null || heat < 43
    // وكل ما في هذه البطاقة من أرقام يمرّ من [PULSE_SCALE] — القرار في مكان واحد، والتفصيل في كتْبته.
    NeuralPanel(
        accent = p.accent,
        contentPadding = PaddingValues(18.dp * PULSE_SCALE),
        verticalSpacing = 16.dp * PULSE_SCALE
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 40.dp * PULSE_SCALE)
            Spacer(Modifier.width(12.dp * PULSE_SCALE))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    deviceName,
                    color = p.text,
                    fontSize = 16.sp * PULSE_SCALE,
                    lineHeight = 20.sp * PULSE_SCALE,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dashboard.chipsetName,
                    color = p.muted,
                    fontSize = 11.sp * PULSE_SCALE,
                    lineHeight = 15.sp * PULSE_SCALE,
                    // سطران لا سطر: السطر يحمل الآن رمز القطعة بين قوسين (طلب المالك)، والاسم قد
                    // يكون مجموع مرشّحين — بسطر واحد كان الاقتطاع سيأكل **الرمز**، وهو ما جاء
                    // المستخدم لأجله. فالزيادة هنا شرط وصول المعلومة لا تجميل.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        /*
         * **وضع الوصول — حالة لا شرح (عقد §5.2-2).**
         *
         * الشارة تحمل الاسم من **سجلّ الصلاحيات** (`PrivilegeLevel.labelRes`) لا من نصّ ثانٍ
         * مكتوب هنا؛ وسطر واحد تحتها يقول ما تُفتحه هذه الطبقة وما تبقي عليه مقفلاً. وبلا
         * السطر تُقرأ «أساسي» كعطبٍ في التطبيق لا كحدٍّ على الجهاز — وهذا الفرق نفسه هو ما
         * بنت عليه الخطة §10.3 («كل زر مُقفل يقول ماذا ينقصه لا يختفي»).
         *
         * **والوسم غير قابل للضغط، عن قصد:** هو يقرأ حالة، والذاهب لتغييرها يجد الصلاحيات في
         * الإعدادات — مدخل واحد للفعل خير من وسم يُنفّذ شيئًا لا يُرى. (وسمٌ يقود إلى شاشة
         * ثانية كان سيحمل `navigates = true`؛ وهذا لا يقود، فلا سهم.)
         */
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = stringResource(accessLabelRes(accessLevel)),
                accent = accessAccent(accessLevel, p),
                filled = true,
                dot = true,
            )
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                stringResource(accessNoteRes(accessLevel)),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NeuralCaption(stringResource(R.string.home_temperature_short), color = heatAccent)
                Row(verticalAlignment = Alignment.Bottom) {
                    NeuralValue(
                        heat?.toString() ?: "\u2014",
                        style = MonoValueStyleSmall.copy(
                            fontSize = 44.sp * PULSE_SCALE,
                            lineHeight = 48.sp * PULSE_SCALE,
                            fontWeight = FontWeight.Bold
                        ),
                        color = p.text
                    )
                    Spacer(Modifier.width(4.dp * PULSE_SCALE))
                    NeuralCaption("\u00b0C", color = p.muted)
                }
                /*
                 * «النظام يحتاج انتباه» **انتقلت إلى هنا من صفّ الزرّ** — والسبب مقيس لا مذوق.
                 *
                 * النصّ الإنجليزي `SYSTEM NEEDS ATTENTION` عند `10sp` بتباعد `0.9sp` يبلغ
                 * **≈١٧٢dp** (قيس بخطّ النظام لا بالتخمين)، ووسم Max AI بعد العلامة الجديدة
                 * **≈١٤٧dp**. فصفٌّ واحد يحمل الاثنين يطلب **≈٣٢٧dp**، والمتاح على شاشة ٣٦٠dp هو
                 * **≈٢٨٤dp** (بعد هامش الصفحة ٢٠dp×٢ وحشوة اللوحة ١٨dp×٢ — وهي اليوم ١٧٫١dp
                 * بـ`PULSE_SCALE`، فالمتاح يزيد ١٫٨dp ولا ينقص) ⇒ صفٌّ **مفرط القيود**،
                 * وأوّل ما يُدفع ثمنه هو الرقم الكبير (٤٤sp يحتاج ≈٦١dp).
                 *
                 * **وهذا عطب سابق للعلامة لا ناتج عنها:** الوسم قبلها كان ≈١٢١dp والمجموع
                 * ≈٣٠١dp — أي أنه تجاوز ٢٨٤dp حتى بلا العلامة. فالتغيير هنا لم يُورث العطب بل
                 * أظهره: الرقم الذي يشرح *لماذا* «يحتاج انتباه» كان أوّل ما يُقصّ في الحالة التي
                 * يظهر فيها التحذير، على ٣٦٠dp و٣٩٣dp و٤١٢dp جميعًا.
                 *
                 * ونقل العبارة تحت الرقم الذي تصفه يحلّ التنافس ويصحّح المعنى معه: العبارة عن
                 * الحرارة لا عن الزرّ (٨dp تفصلها عنه فتُقرأ تبعيّة له وهي ليست له). وقِيس الأثر:
                 * بعد النقل يحصل عمود الحرارة على **١٣٧dp** على ٣٦٠dp و**٩٧dp** على ٣٢٠dp — أكبر
                 * من ٦١dp التي يحتاجها الرقم.
                 */
                if (!calm) {
                    NeuralCaption(stringResource(R.string.home_system_attention), color = heatAccent)
                }
            }
            // مدخل Max AI **مكان** كلمة الحالة (طلب المالك: «بدل النظام مستقر»). و«يحتاج انتباه»
            // لم تُخفَ بل انتقلت إلى عمود الحرارة أعلاه (ADR-07). والزرّ مكوّن مستقلّ بأيقونة
            // عاديّة؛ والحالة (مُشغَّل/متوقّف) يحملها لونه بدل النقطة والتوهّج المحذوفين.
            MaxAiEntryButton(
                text = stringResource(R.string.max_nav_max_ai),
                active = aiEnabled,
                onClick = onMaxAi
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp * PULSE_SCALE)
        ) {
            NeuralFactTile(
                caption = stringResource(R.string.max_home_uptime),
                value = compactUptime(dashboard.uptimeMinutes),
                accent = p.accent,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.max_home_battery),
                value = "${dashboard.batteryPercent}%",
                accent = if (dashboard.isCharging) p.ok else p.accentAlt,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_power_draw),
                value = "${dashboard.powerWatt.oneDecimal()} W",
                accent = p.warn,
                modifier = Modifier.weight(1f)
            )
        }
        // `compact` بأمر المالك («قم بتصغير … زر في الشاشة الرئيسية»): حبّة بمقاسها المرئي،
        // وصندوق لمس ٤٨dp يبقى تحتها. وكان ظاهرها ٤٨dp لأن حدّ الإتاحة كان يُطبّق على شكلها
        // نفسه، لا على حاوية حوله.
        //
        // **والاسم من السجلّ لا نصًّا مكتوبًا بيد** (أمر المالك: «زر في الشاشة الرئيسية باسم
        // device info به كل الأقسام»): `MaxDestination.DeviceInfo.titleRes` هو **عنوان الشاشة
        // نفسه**، فلا اسمان لوجهة واحدة — وهو العطب الذي وُلد منه السجلّ (ADR-02). والعبارة
        // السابقة (`home_device_overview` = «افتح نظرة عامة للجهاز») كانت تصف **الحركة** لا
        // الوجهة؛ وحُذف المفتاح من `values/` و`values-ar/` ومن الـ٨٣ لغة (`--prune all`) فلا
        // تبقى ترجمة قديمة تقول شيئًا آخر عن المدخل نفسه.
        NeuralPill(
            text = stringResource(MaxDestination.DeviceInfo.titleRes),
            accent = p.muted,
            navigates = true,
            compact = true,
            onClick = onOverview
        )
    }
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
