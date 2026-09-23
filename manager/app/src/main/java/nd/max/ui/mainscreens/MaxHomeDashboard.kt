package nd.max.ui.mainscreens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import nd.max.ui.component.NeuralActionTile
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralDivider
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.NeuralFrequencyMeter
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralLiveDot
import nd.max.ui.component.NeuralLoadRibbon
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralSegmented
import nd.max.ui.component.NeuralTile
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState

/**
 * The MAX home — **one instrument, one control band, one log**.
 *
 * The screen answers three questions in reading order and never repeats itself:
 *
 *  1. **How is the device right now?** — `DevicePulseHero`: identity, one headline
 *     reading (heat), one verdict sentence (the limiter), the CPU/GPU load spectrum
 *     with the clock meters that used to be two separate cards, and the three facts
 *     that have no owner screen (uptime, battery shortcut, power draw).
 *  2. **Who is in control?** — `ControlBand`: the base-profile rail (your hand) and
 *     the Max AI strip (the engine's hand) on one surface, because they are the two
 *     faces of the same question. This is also where every hardware-operation
 *     outcome surfaces: applying, applied, or failed with a retry.
 *  3. **What did that actually do?** — `UnifiedActivityCard`: measured outcomes only.
 *     The Max AI scene lives there (knob-level changes), so this band deliberately
 *     shows intent and confidence — never the same knob lines again.
 *
 * What was deleted from the old home and why (each was drawn twice somewhere):
 *
 *  - **Two big CPU/GPU cards** → merged into the hero's spectrum + clock meters.
 *    The sparklines they carried were the *third* drawing of the same series.
 *  - **`VerdictPanel` ("performance story")** → its one useful sentence (the limiter)
 *    became the hero's verdict line; its confidence track duplicated the AI strip's
 *    confidence, and its event feed duplicated the live loop's journal.
 *  - **`CommandDeck`'s profile tile** → the profile rail applies in one tap now;
 *    a dialog on top of a rail is a step nobody asked for.
 *  - **The profile dialog itself** — the rail *is* the picker.
 *
 * Honesty rules kept from the old screen and tightened:
 *
 *  - every number comes from `DashboardState` / `MaxAiState` measurement; nothing is
 *    estimated. Power draw shows `—` at 0 (0 means `current_now` unreadable, not 0 W),
 *    GPU hides entirely when the kernel exposes no node, and a missing frequency
 *    ceiling renders a meter with **no fill** instead of an invented range;
 *  - no metric is drawn twice on this screen — heat is one number in the hero, the
 *    engine state is said once (the header pill), and knob changes live only in the
 *    activity card;
 *  - engine-authored text (limiter names, automation reason/next action, safety
 *    reasons) is shown as the engine wrote it — the same rule the activity card uses
 *    for hardware reasons: a translated paraphrase would break the line to log text.
 *
 * Motion is choreography, not decoration: blocks enter staggered once via
 * [MaxReveal], the heat headline crossfades on real change, the profile rail's lit
 * segment slides by color only, and the AI dot pulses **while the engine runs** and
 * rests when it stops. Motion is never on the reading path — the activity card's
 * own `CardMotion` setting is the user-facing kill switch (`off` skips the
 * transition composable entirely, it is not a zero-duration animation).
 */

/** مقطع واحد في سلّم الملفات: المفتاح الذي يمرّ للمحرّك، واسمه المعروض. */
private data class ProfileChoice(val reason: String, val labelRes: Int)

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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MaxReveal(visible = true, delayMillis = 0, modifier = Modifier.fillMaxWidth()) {
            HomeHeader(online, onSettings, onReboot)
        }
        MaxReveal(visible = true, delayMillis = 55, modifier = Modifier.fillMaxWidth()) {
            DevicePulseHero(
                dashboard = dashboard,
                deviceName = deviceName,
                onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
                onGpu = { onNavigate(MaxDestination.GpuStudio.route) },
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
                onOverview = { onNavigate(MaxDestination.Diagnostics.route) },
            )
        }
        MaxReveal(visible = true, delayMillis = 110, modifier = Modifier.fillMaxWidth()) {
            FocusCard(dashboard, maxAi, onNavigate)
        }
        MaxReveal(visible = true, delayMillis = 165, modifier = Modifier.fillMaxWidth()) {
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
        MaxReveal(visible = true, delayMillis = 220, modifier = Modifier.fillMaxWidth()) {
            UnifiedActivityCard(maxAi = maxAi)
        }
        MaxReveal(visible = true, delayMillis = 275, modifier = Modifier.fillMaxWidth()) {
            CommandDeck(
                onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
                onBattery = { onNavigate(MaxDestination.Charging.route) },
                onApps = { onNavigate(MaxDestination.Apps.route) },
                onAdvanced = { onNavigate(MaxDestination.Control.route) },
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
 * The signature block: heat headline → verdict → load spectrum → clock meters →
 * facts. One panel instead of the old three, because all of it answers one
 * question and every datum in it is drawn exactly once on this screen.
 *
 * The spectrum is the identity element: two lanes (GPU above CPU, same 0–100%
 * scale), empty slots rendered as seats so the frame is whole from the first
 * second, and the newest column capped in the heat color — "now" is visible
 * without an arrow or a label.
 */
@Composable
private fun DevicePulseHero(
    dashboard: DashboardState,
    deviceName: String,
    onCpu: () -> Unit,
    onGpu: () -> Unit,
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onOverview: () -> Unit,
) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val heatAccent = temperatureAccent(heat)
    val calm = heat == null || heat < 43
    val intel = dashboard.intelligence

    NeuralPanel(accent = heatAccent, contentPadding = PaddingValues(18.dp), verticalSpacing = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    deviceName,
                    color = p.text,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dashboard.chipsetName,
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            NeuralPill(
                text = stringResource(if (calm) R.string.home_system_stable else R.string.home_system_attention),
                accent = if (calm) p.ok else heatAccent,
                dot = true,
                onClick = onThermal
            )
        }

        // العنوان الوحيد الكبير على الشاشة: الحرارة. الرقم يتقاطع هادئًا عند تغيّره فقط،
        // فلا حركة على قراءة ثابتة — وهي أهم قاعدة في حركة هذه الشاشة.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NeuralCaption(stringResource(R.string.home_temperature_short), color = heatAccent)
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = heat,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                        },
                        label = "hero-heat",
                    ) { value ->
                        NeuralValue(
                            value?.toString() ?: "\u2014",
                            style = MonoValueStyleSmall.copy(
                                fontSize = 44.sp,
                                lineHeight = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = p.text
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    NeuralCaption("\u00b0C", color = p.muted)
                }
            }
        }

        // الحكم في جملة: ما المحدِّد الآن ولماذا — السطر الذي كان بطاقة كاملة قديمًا.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralCaption(stringResource(R.string.home_story_bottleneck), color = p.muted)
            Spacer(Modifier.width(8.dp))
            Text(
                intel.primaryLimiter,
                color = limiterAccent(intel.primaryLimiter, heatAccent),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            intel.explanation,
            color = p.muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        NeuralLoadRibbon(
            samples = dashboard.loadSamples,
            accent = p.accent,
            secondaryAccent = p.accentAlt,
            hot = heatAccent,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        )

        // مسار GPU يظهر فقط حين توجد قراءة حقيقية: لا نصف فارغ يوهم بأن الرسوم هادئة،
        // ولا صف يقول «صفر» لعدة لا تُقرأ أصلًا.
        if (dashboard.gpuLoadPercent != null || dashboard.gpuFreqMhz != null) {
            HardwareLane(
                tag = "GPU",
                accent = p.accentAlt,
                loadPercent = dashboard.gpuLoadPercent,
                freqMhz = dashboard.gpuFreqMhz,
                ceilingMhz = dashboard.gpuCeilingMhz,
                onClick = onGpu,
            )
        }
        HardwareLane(
            tag = "CPU",
            accent = p.accent,
            loadPercent = dashboard.cpuLoadPercent,
            freqMhz = dashboard.cpuTopCoreMhz.takeIf { it > 0 },
            ceilingMhz = dashboard.cpuCeilingMhz.takeIf { it > 0 },
            onClick = onCpu,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.weight(1f),
                onClick = onBattery
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_power_draw),
                // الصفر هنا «لا قراءة» لا «صفر واط»: `current_now` غير مقروء يُعرض هكذا،
                // ورقم مُخترع من عدَّاد صامت أسوأ من علامة نقص صادقة.
                value = if (dashboard.powerWatt > 0f) "${dashboard.powerWatt.oneDecimal()} W" else "\u2014",
                accent = p.warn,
                modifier = Modifier.weight(1f)
            )
        }
        NeuralPill(
            text = stringResource(R.string.home_device_overview),
            accent = p.muted,
            onClick = onOverview
        )
    }
}

/**
 * صفّ مسار واحد: الاسم ونسبة الحمل، وتحته مقياس التردد (القراءة · السقف · المدرّج).
 *
 * والمقياس يجيب سؤالًا لا يجيبه الرقم وحده: هل التردد **قريب من سقفه** (يعمل بقوّته)
 * أم مضغوط تحته (يخنق نفسه)؟ والسقف غير المعلَن ⇒ لا تعبئة، لا مدى مُخترع.
 */
@Composable
private fun HardwareLane(
    tag: String,
    accent: Color,
    loadPercent: Int?,
    freqMhz: Int?,
    ceilingMhz: Int?,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    val current = freqMhz?.takeIf { it > 0 }
    val ceiling = ceilingMhz?.takeIf { it > 0 }
    NeuralTile(accent = accent, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                tag,
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            NeuralValue(
                if (loadPercent == null) "\u2014" else "${loadPercent.coerceIn(0, 100)}%",
                style = MonoValueStyleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = if (loadPercent == null) p.muted else p.text
            )
        }
        NeuralFrequencyMeter(
            reading = compactFrequency(current),
            ceiling = ceiling?.let { compactFrequency(it) },
            fraction = if (current != null && ceiling != null) current.toFloat() / ceiling else null,
            accent = accent,
        )
    }
}

/** لون الحكم: خط الأساس هادئ، والحرارة بلون الحرارة، وما عدا ذلك بلون المسار. */
@Composable
private fun limiterAccent(limiter: String, heatAccent: Color): Color {
    val p = neuralPalette()
    return when (limiter) {
        "Baseline" -> p.ok
        "Thermal", "Power/Thermal" -> heatAccent
        "CPU", "GPU" -> p.accent
        "Memory" -> p.accentAlt
        else -> p.muted
    }
}

/**
 * Shown only when a real problem exists (measured thresholds, or the safety
 * engine actively capping), so its presence itself means something.
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
 * Where to go next. Four destinations that people actually reach for; the profile
 * action is gone (the rail replaced it) and every tile here owns a screen this
 * home does not duplicate.
 */
@Composable
private fun CommandDeck(
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onApps: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val p = neuralPalette()
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_quick_actions),
            caption = stringResource(R.string.home_quick_actions_desc),
            accent = p.accentAlt
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.Thermostat,
                title = stringResource(R.string.home_action_thermal),
                support = stringResource(R.string.home_action_thermal_desc),
                accent = p.warn,
                onClick = onThermal,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.home_action_battery),
                support = stringResource(R.string.home_action_battery_desc),
                accent = p.ok,
                onClick = onBattery,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.Apps,
                title = stringResource(R.string.max_home_app_profiles),
                support = stringResource(R.string.home_action_apps_desc),
                accent = p.accent,
                onClick = onApps,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_action_advanced),
                support = stringResource(R.string.home_action_advanced_desc),
                accent = p.accentAlt,
                onClick = onAdvanced,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
