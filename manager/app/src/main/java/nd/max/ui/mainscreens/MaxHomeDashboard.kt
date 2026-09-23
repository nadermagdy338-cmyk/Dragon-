package nd.max.ui.mainscreens

import android.content.Intent
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.MaxReveal
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralCoreGrid
import nd.max.ui.component.NeuralCoreReading
import nd.max.ui.component.NeuralDivider
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.NeuralFrequencyMeter
import nd.max.ui.component.NeuralGaugeCard
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralLiveDot
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPanelShape
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralSegmented
import nd.max.ui.component.NeuralSkeleton
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState

/**
 * The MAX home — **a device instrument panel, not a stack of cards**.
 *
 * The screen is read top to bottom as five answers, and nothing is drawn twice:
 *
 *  1. **Who am I attached to, and what is wrong?** — header (engine state, one word) and
 *     `FocusCard`, which appears **only** when a measured threshold is crossed, so its
 *     presence itself carries meaning.
 *  2. **What device is this?** — `IdentityCard`: name, chipset, system state, and the
 *     current limiter as a one-word chip. The paragraph that used to explain the limiter
 *     is gone on purpose: it restated the readings below it in prose
 *     (and, at baseline, said "no single limiter dominates yet" — true, but a sentence the
 *     panel already said better by showing calm numbers). The engine's limiter *name* is
 *     kept because it is one fact, and it stays as the engine wrote it (see the
 *     engine-authored-text rule in the activity card).
 *  3. **Who is in control?** — `ControlBand`: the base-profile rail (your hand) and the
 *     Max AI strip (the engine's hand) on one surface, because they are the two faces of
 *     the same question. This is also where every hardware-operation outcome surfaces:
 *     applying, applied, or failed with a retry.
 *  4. **What is the hardware doing?** — `ComputeDeck` (**CPU and GPU side by side**, each a
 *     ring plus its own clock meter), `CoreMatrixPanel` (per-core clocks), `MemoryDeck` *    (RAM | ZRAM), `CapacityDeck` (storage | battery), `ThermalPanel` (four sensors),
 *    `PerformanceHistoryCard` (the window's measured history as a chart plus windowed averages)
 *    and `DeviceDetailsPanel` (display, network, power as dense readouts).
 *  5. **What did control actually change?** — `UnifiedActivityCard` (measured outcomes) and
 *     `CommandDeck` (the destinations this screen does not duplicate).
 *
 * What changed from the previous home, and why:
 *
 *  - **The spectrum ("load ribbon") is gone.** It was the third drawing of the same load
 *    series and it read as a chart in a screen that had none. Load is now a ring on the CPU
 *    and GPU cards — the reading, the clock it is pinned to, and its ceiling in one glance.
 *  - **CPU and GPU are two cards, left and right.** Stacking them made the page taller to
 *    compare two numbers that mean the same thing; side by side they compare themselves.
 *  - **Device data that was hidden in other screens is back as widgets.** RAM, ZRAM,
 *    storage, battery, per-core clocks, display, network and power are the numbers people
 *    open a device-info app for, and they belong on the first screen of a performance app.
 *  - **Plots were replaced by ratios.** Every ring is «used of total» with a real
 *    denominator: RAM, ZRAM, storage, battery percent, load percent. A ring cannot be drawn
 *    without a denominator, which is exactly the honesty rule this screen keeps — an
 *    unknown total renders `—` with an empty ring, never a full one.
 *
 * Honesty rules kept from the old screen and tightened:
 *
 *  - every number comes from `DashboardState` / `MaxAiState` measurement; nothing is
 *    estimated. Power draw shows `—` at 0 (0 means `current_now` unreadable, not 0 W), the
 *    GPU card disappears entirely when the kernel exposes no node, a battery at 0% is
 *    treated as unreadable rather than empty, and a core with no readable clock shows `—`;
 *  - the same datum is never drawn twice on this screen: heat lives in the thermal panel
 *    (the identity card carries state, not a second temperature), knob changes live only in
 *    the activity card, and the engine state is said once in the header;
 *  - engine-authored text (limiter names, automation reason/next action, safety reasons) is
 *    shown as the engine wrote it — a translated paraphrase would break the line to log text.
 *
 * Motion is choreography, not decoration: blocks enter staggered once via [MaxReveal], every
 * ring and every core column animates to its value instead of re-mounting, and the AI dot
 * pulses **while the engine runs** and rests when it stops. The activity card's own
 * `CardMotion` setting is the user-facing kill switch.
 */

/** مقطع واحد في سلّم الملفات: المفتاح الذي يمرّ للمحرّك، واسمه المعروض. */
private data class ProfileChoice(val reason: String, val labelRes: Int)

/** إجراء صفحة «حول الهاتف» في تطبيق الإعدادات — معلن على كل أندرويد. */
private const val DEVICE_INFO_ACTION = "android.settings.DEVICE_INFO_SETTINGS"

private val PROFILE_CHOICES = listOf(
    ProfileChoice("1", R.string.Profile_Performance),
    ProfileChoice("2", R.string.Profile_Balanced),
    ProfileChoice("3", R.string.Profile_ECO_mode),
)

/**
 * الملف كما يُقرأ من حِلقة الملف الحالي — أو `null` حين لا نعرفه، فلا يُضاء أي مقطع
 * على شكل «مُختار» لقيمة لا نملكها. (`profile_perflite` ملف أداء خفيف: هو أداء.)
 */
internal fun profileReasonFor(profileRes: Int): String? = when (profileRes) {
    R.string.Profile_Performance, R.string.profile_perflite -> "1"
    R.string.Profile_Balanced -> "2"
    R.string.Profile_ECO_mode -> "3"
    else -> null
}

@Composable
internal fun MaxHomeDashboard(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onProfile: (String) -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit,
) {
    val online = ui.rootStatus && ui.moduleInstalled
    val context = LocalContext.current
    if (!dashboard.ready) {
        // قبل أول دورة قياس: هياكل لا أصفار — صفرٌ في الثانية الأولى ليس برودةً مقيسة،
        // و`—` هنا ليس «لا قراءة» بل «لم يُسأل العتاد بعد».
        HomeSkeleton(modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MaxReveal(visible = true, delayMillis = 0, modifier = Modifier.fillMaxWidth()) {
            HomeHeader(online, onSettings, onReboot)
        }
        MaxReveal(visible = true, delayMillis = 45, modifier = Modifier.fillMaxWidth()) {
            FocusCard(dashboard, maxAi, onNavigate)
        }
        MaxReveal(visible = true, delayMillis = 90, modifier = Modifier.fillMaxWidth()) {
            IdentityCard(
                dashboard = dashboard,
                deviceName = deviceName,
                onOverview = { onNavigate(MaxDestination.Diagnostics.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 135, modifier = Modifier.fillMaxWidth()) {
            ControlBand(
                profileRes = ui.currentProfileRes,
                manualProfileAllowed = ui.autoMode == "0",
                maxAi = maxAi,
                request = profileRequest,
                onProfile = onProfile,
                onOpenAi = { onNavigate(MaxDestination.MaxAi.route) },
                onLive = { onNavigate(MaxDestination.MaxLive.route) },
                onRetry = onAiRetry,
            )
        }
        MaxReveal(visible = true, delayMillis = 180, modifier = Modifier.fillMaxWidth()) {
            ComputeDeck(
                dashboard = dashboard,
                onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
                onGpu = { onNavigate(MaxDestination.GpuStudio.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 225, modifier = Modifier.fillMaxWidth()) {
            CoreMatrixPanel(
                dashboard = dashboard,
                onCores = { onNavigate(MaxDestination.CpuCoreControl.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 270, modifier = Modifier.fillMaxWidth()) {
            MemoryDeck(
                dashboard = dashboard,
                onRam = { onNavigate(MaxDestination.MemoryHub.route) },
                onZram = { onNavigate(MaxDestination.ZramManager.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 315, modifier = Modifier.fillMaxWidth()) {
            CapacityDeck(
                dashboard = dashboard,
                onStorage = { onNavigate(MaxDestination.StorageDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 360, modifier = Modifier.fillMaxWidth()) {
            ThermalPanel(
                dashboard = dashboard,
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 405, modifier = Modifier.fillMaxWidth()) {
            PerformanceHistoryCard(dashboard)
        }
        MaxReveal(visible = true, delayMillis = 450, modifier = Modifier.fillMaxWidth()) {
            DeviceDetailsPanel(
                dashboard = dashboard,
                onDisplay = { onNavigate(MaxDestination.DisplayStudio.route) },
                onNetwork = { onNavigate(MaxDestination.NetworkDetail.route) },
                onPower = { onNavigate(MaxDestination.Charging.route) },
                onDeviceInfo = {
                    // لمس صفّ النظام يفتح «حول الهاتف» مباشرة (مرجع DevCheck). وإن رفضته
                    // هذه النسخة من الإعدادات، الاتجاه العام لا صمت.
                    runCatching { context.startActivity(Intent(DEVICE_INFO_ACTION)) }.onFailure {
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        }
                    }
                },
            )
        }
        MaxReveal(visible = true, delayMillis = 495, modifier = Modifier.fillMaxWidth()) {
            UnifiedActivityCard(maxAi = maxAi)
        }
        MaxReveal(visible = true, delayMillis = 540, modifier = Modifier.fillMaxWidth()) {
            CommandDeck(
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
                onApps = { onNavigate(MaxDestination.Apps.route) },
                onAdvanced = { onNavigate(MaxDestination.Control.route) },
                onSystemSettings = {
                    runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                },
            )
        }
    }
}

/**
 * Brand, one state, two actions. The engine state is said once here — the AI strip
 * below answers a different question (what the *engine* is doing), never this one.
 */
@Composable
private fun HomeHeader(online: Boolean, onSettings: () -> Unit, onReboot: () -> Unit) {
    val p = neuralPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.max_brand_mark),
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
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.max_home_power), onReboot)
        Spacer(Modifier.width(6.dp))
        HeaderButton(Icons.Rounded.Settings, stringResource(R.string.max_home_settings), onSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .size(38.dp)
            .clip(shape)
            .background(p.tile)
            .border(BorderStroke(1.dp, p.border), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(18.dp), tint = p.muted)
    }
}

/**
 * هويّة الجهاز: اسمه، وشريحته، وحالة النظام، والمحدِّد الحالي بكلمة واحدة.
 *
 * ولا رقم حرارة هنا، ولا شارة حالة: الحرارة وحكمها في لوحة الحرارة أسفل الصفحة (حيث
 * المجسّات نفسها)، والشارة هناك تعني ما تراه العين بجانب الرقم. وما تحمله هذه البطاقة هو
 * **ما لا تملكه لوحة**: اسم الجهاز، وشريحته، والمحدِّد الحالي — أي العنوان الذي يُقرأ منه
 * ما تحته. ورقم يظهر في مكانين يعني رقمين مختلفين بعد أول تحديث لأحدهما.
 *
 * وهي **السطح الوحيد المُدرَّج في الشاشة** (تدرّج رأسي + هالة لونية من الركن): الألواح الباقية
 * مسطّحة متساوية عن قصد، فتقرأ العينُ الأولى فورًا على أنها الترويسة، وفي الوقت نفسه لا يسحب
 * تدرّجُها الانتباه عن القراءات. ومعها الاسم يكبُر إلى 20sp — أول تمييز هرمي في الصفحة.
 */
@Composable
private fun IdentityCard(
    dashboard: DashboardState,
    deviceName: String,
    onOverview: () -> Unit,
) {
    val p = neuralPalette()
    val intel = dashboard.intelligence
    // لون الحكم يحتاج حرارة اللحظة وإن لم يُعرض رقمها: محدد «Thermal» يجب أن يُقرأ بلون
    // الحرارة لا بأخضر الحالة — وإلا صار اللون جزءًا من معلومة غير معروضة.
    val heatAccent = temperatureAccent(
        dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt() ?: dashboard.cpuTempC
    )

    val shape = NeuralPanelShape
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.panelTop.copy(alpha = .96f), p.panel)))
            .background(
                Brush.radialGradient(
                    colors = listOf(p.accent.copy(alpha = .18f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 820f,
                )
            )
            .border(BorderStroke(1.dp, p.accent.copy(alpha = .30f)), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    deviceName,
                    color = p.text,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dashboard.chipsetName,
                    color = p.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        NeuralDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // المحدِّد كما كتبه المحرّك («Baseline» · «Thermal» · «Memory») — كلمة واحدة،
            // لا شرح: الشرح كان يُعيد صياغة الأرقام المعروضة أسفلها في نصّ.
            NeuralCaption(stringResource(R.string.home_story_bottleneck))
            Text(
                intel.primaryLimiter,
                color = limiterAccent(intel.primaryLimiter, heatAccent),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            NeuralPill(
                text = stringResource(R.string.home_device_overview),
                accent = p.muted,
                onClick = onOverview
            )
        }
    }
}

/**
 * الشاشة **لا تفترض أن المشكلة موجودة**. هذه البطاقة تُرسم فقط عند عتبة مقيسة (حرارة، أو
 * سعة، أو ذاكرة، أو تدخّل أمان فعلي)، فوجودها نفسه معلومة. وغيابها هو الحالة الطبيعية.
 */
@Composable
private fun FocusCard(dashboard: DashboardState, maxAi: MaxAiState, onNavigate: (String) -> Unit) {
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
        maxAi.safety.engaged -> {
            title = stringResource(R.string.home_system_attention)
            // سبب الأمان كما كتبه المحرّك — لا ترجمة إنشائية تبتعد عن نصّ السجل.
            body = maxAi.safety.lastReason.ifBlank { stringResource(R.string.max_home_state_thermal_desc) }
            accent = p.danger
            icon = Icons.Rounded.Thermostat
            route = MaxDestination.ThermalDetail.route
        }
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
        // صفّ التنبيه هو المكوّن المشترك نفسه الذي يحمل سطور الأحداث — سبب واحد مكتوب مرّة
        // في المكتبة، لا نسخة محلية تبتعد عنه عند أول تعديل.
        NeuralFeedRow(icon = icon, title = title, meta = body, accent = accent)
    }
}

/**
 * The control band — «who is in control» on one surface.
 *
 * Top: the base-profile rail. One tap applies through the same
 * `UI → ViewModel → MaxAiEngine → SafetyEngine → HardwareControlArbiter` chain the
 * old dialog used — the dialog was the only thing deleted. When the module's AI
 * switch owns the profiles (`autoMode != "0"`), the rail explains itself instead of
 * silently doing nothing, which is what the old home did.
 *
 * Bottom: the Max AI strip. It answers what the *engine* is doing — objective,
 * plan reason, next action, confidence, safety interventions — and links to its
 * screens. What it *changed* is not repeated here: that is the activity card's
 * MAX AI scene, at knob level, with measured outcomes.
 *
 * And this is the screen's single feedback surface for hardware operations:
 * `ProfileRequestState` renders applying → applied/failed with a retry, so a
 * profile tap is never fire-and-forget.
 */
@Composable
private fun ControlBand(
    profileRes: Int,
    manualProfileAllowed: Boolean,
    maxAi: MaxAiState,
    request: ProfileRequestState,
    onProfile: (String) -> Unit,
    onOpenAi: () -> Unit,
    onLive: () -> Unit,
    onRetry: () -> Unit,
) {
    val p = neuralPalette()
    val aiAccent = if (maxAi.aiEnabled) p.accentAlt else p.muted
    val selectedReason = profileReasonFor(profileRes)

    NeuralPanel(accent = p.accent, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.max_home_active_profile),
            accent = p.accent,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (request.inFlight) .55f else 1f }
        ) {
            NeuralSegmented(
                labels = PROFILE_CHOICES.map { stringResource(it.labelRes) },
                selectedIndex = PROFILE_CHOICES.indexOfFirst { it.reason == selectedReason },
                onSelect = { index ->
                    // السلوك المحفوظ من البيت القديم: لا تحويل يدوي والمحرّك يملك المفاتيح؛
                    // والسياق لا يُقال بصمت — السطر أدناه يقوله.
                    if (manualProfileAllowed && !request.inFlight) {
                        onProfile(PROFILE_CHOICES[index].reason)
                    }
                },
                accent = p.accent,
            )
        }
        val failed = !request.inFlight && request.result == DecisionResult.FAILED
        val statusText: String? = when {
            request.inFlight -> stringResource(R.string.max_home_ai_working)
            failed -> stringResource(R.string.max_home_ai_failed)
            !manualProfileAllowed && maxAi.aiEnabled -> stringResource(R.string.maxai_banner_active_title)
            !manualProfileAllowed -> stringResource(R.string.home_service_unready)
            else -> null
        }
        if (statusText != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusText,
                    color = if (failed) p.danger else p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (failed) {
                    Spacer(Modifier.width(8.dp))
                    NeuralPill(
                        text = stringResource(R.string.max_home_retry),
                        accent = p.muted,
                        onClick = onRetry
                    )
                }
            }
        }

        NeuralDivider()

        NeuralSectionHeader(
            title = stringResource(R.string.max_nav_max_ai),
            caption = stringResource(R.string.max_home_smart_section_desc),
            accent = aiAccent,
            trailing = {
                // حالة المحرّك تُقال **مرّة واحدة**: شارة مركّبة (نقطة تنبض وهو يعمل، وكلمة
                // للقراءة) — لا شارة ثابتة ونقطة بجانبها تقولان الشيء نفسه بصيغتين.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuralLiveDot(active = maxAi.aiEnabled, color = if (maxAi.aiEnabled) p.ok else p.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (maxAi.aiEnabled) R.string.max_home_ai_on else R.string.max_home_ai_off),
                        color = if (maxAi.aiEnabled) p.ok else p.muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralCaption(stringResource(R.string.storyboard_knob_max_ai_objective))
            Spacer(Modifier.width(8.dp))
            Text(
                // الهدف كما اختاره صاحبه؛ ومعه `strategyLabel` احتياطًا **وهو يعمل** فقط —
                // «هدف: يدوي» لذكاء متوقّف جملة صحيحة لغويًّا ومضلِّلة فنيًّا.
                maxAi.objectivePreference
                    ?: maxAi.strategyLabel.takeIf { it.isNotBlank() && maxAi.aiEnabled }
                    ?: "\u2014",
                color = p.text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            if (maxAi.aiEnabled) {
                NeuralCaption(
                    stringResource(R.string.home_ai_confidence, maxAi.automationPlan.confidencePercent)
                )
            }
        }
        if (maxAi.aiEnabled) {
            // لماذا يفعل ما يفعله، وما خطوته التالية — من `AutomationPlan` كما هي، بلا حشو.
            Text(
                maxAi.automationPlan.reason,
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NeuralCaption(stringResource(R.string.home_ai_next_step))
                Spacer(Modifier.width(8.dp))
                NeuralValue(
                    maxAi.automationPlan.nextAction,
                    style = MonoValueStyleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = p.accentAlt
                )
            }
        } else {
            Text(
                stringResource(R.string.max_home_ai_off_hint),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = stringResource(R.string.maxai_banner_open),
                accent = aiAccent,
                icon = Icons.Rounded.Bolt,
                onClick = onOpenAi
            )
            if (maxAi.aiEnabled) {
                Spacer(Modifier.width(8.dp))
                NeuralPill(
                    text = stringResource(R.string.home_session_open_loop),
                    accent = p.accent,
                    icon = Icons.Rounded.Timeline,
                    onClick = onLive
                )
            }
        }
    }
}

/**
 * المعالج والرسوم **جنبًا إلى جنب** — لأن المقارنة هي المعنى.
 *
 * عمودان بنفس الأبعاد يجعلان الرقمين يُقارنان بلا تحريك العين رأسًا، والبطاقة الواحدة
 * تحمل: نسبة الحمل (القوس) وتردّد الوحدة من سقفه (المقياس السفلي). وحرارة الوحدة ليست
 * هنا بل في لوحة الحرارة مع المجسّات الأربعة: قراءة واحدة، في المكان الذي تُجمع فيه.
 * والحدّ معلن في الصورة نفسها: قوس فارغ يعني «لا نقرأ»، لا «الحمل صفر».
 *
 * ومسار GPU يغيب كاملًا حين لا تُعلنه النواة (`gpuLoadPercent` و`gpuFreqMhz` كلاهما
 * مجهول): بطاقة GPU فارغة تقول «الرسوم هادئة» وهي لا تقول الحقيقة. وفي هذه الحالة يأخذ
 * المعالج العرض كلّه بدل نصف فارغ.
 */
@Composable
private fun ComputeDeck(dashboard: DashboardState, onCpu: () -> Unit, onGpu: () -> Unit) {
    val p = neuralPalette()
    val cpuLoad = dashboard.cpuLoadPercent.coerceIn(0, 100)
    val gpuLoad = dashboard.gpuLoadPercent?.coerceIn(0, 100)
    val gpuAvailable = dashboard.gpuLoadPercent != null || dashboard.gpuFreqMhz != null
    val cpuCeiling = dashboard.cpuCeilingMhz.takeIf { it > 0 }
    val cpuTop = dashboard.cpuTopCoreMhz.takeIf { it > 0 }
    val gpuCeiling = dashboard.gpuCeilingMhz?.takeIf { it > 0 }
    val gpuFreq = dashboard.gpuFreqMhz?.takeIf { it > 0 }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralGaugeCard(
            caption = stringResource(R.string.home_cpu_tag),
            value = "$cpuLoad%",
            accent = p.accent,
            modifier = Modifier.weight(1f),
            ringFraction = cpuLoad / 100f,
            icon = Icons.Rounded.Memory,
            ringSize = 106.dp,
            onClick = onCpu,
            footer = {
                NeuralFrequencyMeter(
                    reading = compactFrequency(cpuTop),
                    ceiling = cpuCeiling?.let { compactFrequency(it) },
                    // السقف غير المعلَن ⇒ لا تعبئة: نسبة من مقام مُخترع ليست قياسًا.
                    fraction = if (cpuTop != null && cpuCeiling != null) {
                        cpuTop.toFloat() / cpuCeiling
                    } else {
                        null
                    },
                    accent = p.accent,
                )
            },
        )
        if (gpuAvailable) {
            NeuralGaugeCard(
                caption = stringResource(R.string.home_gpu_tag),
                value = if (gpuLoad == null) "\u2014" else "$gpuLoad%",
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
                ringFraction = gpuLoad?.let { it / 100f },
                icon = Icons.Rounded.Speed,
                ringSize = 106.dp,
                onClick = onGpu,
                footer = {
                    NeuralFrequencyMeter(
                        reading = compactFrequency(gpuFreq),
                        ceiling = gpuCeiling?.let { compactFrequency(it) },
                        fraction = if (gpuFreq != null && gpuCeiling != null) {
                            gpuFreq.toFloat() / gpuCeiling
                        } else {
                            null
                        },
                        accent = p.accentAlt,
                    )
                },
            )
        }
    }
}

/**
 * مصفوفة الأنوية — قراءة الجهاز لكل نواة على حدة.
 *
 * والقراءة **لكل نواة** لا لكل عنقود: هذا هو الفرق الذي يفسّر «لماذا لا يرفع تردّده وقد
 * طلبت الأداء؟» — نواة واحدة من العنقود الصغير تخنق والباقي هادئ. والقيمة تُقصّ على سقف
 * نواتها هي (`cpuinfo_max_freq` الخاص بعنقودها)، فلا تُقارن نواة صغيرة قوية بنواة كبيرة.
 *
 * وحالتا الانتظار صريحتان: نواة متصلة بلا قراءة تعرض `—` وتبقى فارغة، ونواة مطفأة تُرسم
 * رماديةً بحالتها (hotplug حقيقي) — لا «صفر ميجاهرتز» لعدّة لم تُقرأ.
 */
@Composable
private fun CoreMatrixPanel(dashboard: DashboardState, onCores: () -> Unit) {
    val p = neuralPalette()
    val unavailable = stringResource(R.string.max_home_unavailable)
    val readings = dashboard.cores.map { core ->
        NeuralCoreReading(
            id = "C${core.cpu}",
            frequency = if (core.online && core.freqMhz > 0) {
                compactFrequency(core.freqMhz)
            } else {
                unavailable
            },
            fraction = core.loadFraction,
            online = core.online,
        )
    }
    val online = dashboard.cores.count { it.online }

    NeuralPanel(accent = p.accentAlt, onClick = onCores, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_cpu_cores),
            caption = if (readings.isEmpty()) {
                null
            } else {
                stringResource(R.string.home_cpu_cores_online, online, readings.size)
            },
            accent = p.accentAlt,
        )
        if (readings.isEmpty()) {
            Text(
                stringResource(R.string.home_waiting_core_data),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        } else {
            NeuralCoreGrid(cores = readings, accent = p.accentAlt)
        }
    }
}

/**
 * الذاكرة: RAM بجانب ZRAM — وهما نفس السؤال بمقامين مختلفين.
 *
 * وZRAM تُخفى كاملةً حين لا يكون في النظام swap مُهيّأ (`swapTotalMb` = null): صفٌّ يقول
 * «ZRAM: ٠ / ٠» لجهاز لا يملكها يوهم بعطب. وفي غيابها يأخذ RAM العرض كلّه.
 */
@Composable
private fun MemoryDeck(dashboard: DashboardState, onRam: () -> Unit, onZram: () -> Unit) {
    val p = neuralPalette()
    val ramKnown = dashboard.ramTotalMb > 0
    val ramFraction = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb)
    val ramAccent = when {
        !ramKnown -> p.muted
        ramFraction >= .90f -> p.danger
        ramFraction >= .75f -> p.warn
        else -> p.accent
    }
    val swapUsed = dashboard.swapUsedMb
    val swapTotal = dashboard.swapTotalMb
    val swapKnown = swapTotal != null && swapTotal > 0
    val swapFraction = if (swapKnown && swapUsed != null) {
        (swapUsed.toFloat() / swapTotal).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralGaugeCard(
            caption = stringResource(R.string.home_ram_tag),
            value = if (ramKnown) "${(ramFraction * 100).roundToInt()}%" else "\u2014",
            accent = ramAccent,
            modifier = Modifier.weight(1f),
            ringFraction = if (ramKnown) ramFraction else null,
            icon = Icons.Rounded.Storage,
            ringSize = 106.dp,
            onClick = onRam,
            support = if (ramKnown) {
                "${gigabytes(dashboard.ramUsedMb)} / ${gigabytes(dashboard.ramTotalMb)}"
            } else {
                stringResource(R.string.max_home_unavailable)
            },
        )
        if (swapKnown) {
            NeuralGaugeCard(
                caption = stringResource(R.string.home_zram_tag),
                value = "${(swapFraction * 100).roundToInt()}%",
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
                ringFraction = swapFraction,
                icon = Icons.Rounded.Memory,
                ringSize = 106.dp,
                onClick = onZram,
                support = if (swapUsed != null) {
                    "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}"
                } else {
                    stringResource(R.string.max_home_unavailable)
                },
            )
        }
    }
}

/**
 * السعة: التخزين بجانب البطارية — أكثر رقمين يفتح الناس تطبيق معلومات جهاز من أجلهما.
 *
 * وقاعدة الصدق هنا صارمة لأن الرقم مُغري بالتدوير: تخزين بلا مقام (`storageTotalGb` = ٠)
 * لا يُرسم قوسه، والبطارية عند ٠٪ تُعامَل «لا قراءة» لا «فارغة» — والجهاز الذي يعرض
 * الصفحة لا يكون بطاريته صفرًا.
 */
@Composable
private fun CapacityDeck(dashboard: DashboardState, onStorage: () -> Unit, onBattery: () -> Unit) {
    val p = neuralPalette()
    val storageKnown = dashboard.storageTotalGb > 0f
    val storageFreeGb = (dashboard.storageTotalGb - dashboard.storageUsedGb).coerceAtLeast(0f)
    val storageFraction = if (storageKnown) {
        (dashboard.storageUsedGb / dashboard.storageTotalGb).coerceIn(0f, 1f)
    } else {
        0f
    }
    val storageAccent = when {
        !storageKnown -> p.muted
        storageFreeGb / dashboard.storageTotalGb < .10f -> p.danger
        storageFreeGb / dashboard.storageTotalGb < .20f -> p.warn
        else -> p.accent
    }
    val batteryKnown = dashboard.batteryPercent > 0
    val batteryAccent = when {
        !batteryKnown -> p.muted
        dashboard.isCharging -> p.ok
        dashboard.batteryPercent <= 20 -> p.danger
        dashboard.batteryPercent <= 40 -> p.warn
        else -> p.ok
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralGaugeCard(
            caption = stringResource(R.string.max_home_storage),
            value = if (storageKnown) "${(storageFraction * 100).roundToInt()}%" else "\u2014",
            accent = storageAccent,
            modifier = Modifier.weight(1f),
            ringFraction = if (storageKnown) storageFraction else null,
            icon = Icons.Rounded.Storage,
            ringSize = 106.dp,
            onClick = onStorage,
            support = if (storageKnown) {
                stringResource(R.string.home_available_memory, "${storageFreeGb.oneDecimal()} GB")
            } else {
                stringResource(R.string.max_home_unavailable)
            },
        )
        NeuralGaugeCard(
            caption = stringResource(R.string.max_home_battery),
            value = if (batteryKnown) "${dashboard.batteryPercent}%" else "\u2014",
            accent = batteryAccent,
            modifier = Modifier.weight(1f),
            ringFraction = if (batteryKnown) dashboard.batteryPercent / 100f else null,
            icon = Icons.Rounded.BatteryChargingFull,
            ringSize = 106.dp,
            badge = if (dashboard.isCharging) stringResource(R.string.max_home_charging) else null,
            onClick = onBattery,
            // الاستهلاك هو السطر الطبيعي تحت نسبة الشحن (يُشحن أم يُسحب؟ وبقوّة كم؟)،
            // ولذلك لا يتكرّر في شبكة التفاصيل: قراءة واحدة في السياق الذي تُسأل فيه.
            // والصفر «لا قراءة» لا «صفر واط» (`current_now` صامت).
            support = if (dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W" else "\u2014",
        )
    }
}

/**
 * الهيكل قبل أول قياس — صور ظلّية بنفس مقاطع الصفحة (بطل · تحكّم · زوجان · سجل · تفاصيل):
 * لا صفر يقول «صفر درجة» ولا `—` يقول «لا قراءة» في الثانية التي سبقت أول دورة قياس.
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeuralSkeleton(Modifier.fillMaxWidth().height(108.dp), NeuralPanelShape)
        NeuralSkeleton(Modifier.fillMaxWidth().height(132.dp), NeuralPanelShape)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeuralSkeleton(Modifier.weight(1f).height(156.dp))
            NeuralSkeleton(Modifier.weight(1f).height(156.dp))
        }
        NeuralSkeleton(Modifier.fillMaxWidth().height(124.dp), NeuralPanelShape)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeuralSkeleton(Modifier.weight(1f).height(112.dp))
            NeuralSkeleton(Modifier.weight(1f).height(112.dp))
        }
        NeuralSkeleton(Modifier.fillMaxWidth().height(168.dp), NeuralPanelShape)
    }
}

