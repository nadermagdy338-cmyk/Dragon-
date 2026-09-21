package nd.max.ui.mainscreens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nd.max.R
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.NeuralActionTile
import nd.max.ui.component.NeuralBudgetBar
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.MaxMotion
import nd.max.ui.component.NeuralClockWave
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralKpiTile
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.util.ActivityReading
import nd.max.ui.util.ActivityState
import nd.max.ui.util.ActivityTone
import nd.max.ui.util.ClockWave
import nd.max.ui.util.HomeActivity
import nd.max.ui.util.HomeActivityModel
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeActivityViewModel
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.primaryBatteryTemperatureC
import kotlin.math.roundToInt

/**
 * The MAX "Now" dashboard.
 *
 * Question the screen answers: how is the device right now, and what is the one
 * thing worth touching? Everything that only explained MAX to itself (system
 * passport, AI console, the old "live performance" gauge stack, the base
 * profile row) is gone; those live in Max AI, Control and Diagnostics.
 *
 * Reading order, each block earning its place exactly once:
 *  0. storyboard— what your choices are actually doing right now: the last per-app
 *               session's verified hardware results, MAX AI's owned knobs, and your
 *               own manual locks. It states outcomes, never instruments: heat, load and
 *               cores keep living in the screens that own them.
 *  1. pulse   — device identity, heat with a stable/attention read, and
 *               uptime, battery and power draw at a glance
 *  2. focus   — appears only when something is actually wrong
 *  3. load    — CPU and GPU now, side by side
 *  4. activity— what MaxManager is doing right now, in one sentence, and what the
 *               engine itself reported: monitoring, applying, verified, refused
 *  5. memory  — RAM, compressed swap and storage budgets
 *  6. verdict — the limiter, in one sentence, with recent events
 *  7. details — always open: the core matrix by cluster, display, network, voltage
 *  8. deck    — four destinations people actually reach for
 *
 * No metric is drawn twice. The rule is enforced by subtraction, not by hope: the
 * spectrum owns the history, so the load tiles carry no second sparkline of the same
 * series, the engine state is stated once (the pill, not a pill plus a subtitle), the
 * spectrum repeats neither the current CPU number nor the verdict's link, and the
 * battery is a percentage up top and a voltage down here — never the same label twice.
 * Every color comes from MaterialTheme through neuralPalette(), so the Settings theme
 * drives the entire screen.
 */

@Composable
internal fun LegendaryHomeDashboard(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    gpuRoute: String?,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit
) {
    val online = ui.rootStatus && ui.moduleInstalled
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeHeader(online, onSettings, onReboot)
        // "ما فعلته اختياراتك" قبل أي قياس: البيان الأول في الشاشة، لأنه جواب السؤال الذي
        // تُفتح الرئيسية من أجله (هل فعّلت؟ وهل عمل؟) — والأرقام اللحظية بعده.
        StoryboardBand(maxAi = maxAi)
        PulsePanel(
            deviceName = deviceName,
            dashboard = dashboard,
            onOverview = { onNavigate(MaxDestination.Diagnostics.route) }
        )
        FocusCard(dashboard, onNavigate)
        TrendDuo(
            dashboard = dashboard,
            onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
            onGpu = { onNavigate(gpuRoute ?: MaxDestination.GpuStudio.route) }
        )
        ActivityPanel(dashboard) { onNavigate(MaxDestination.MaxLive.route) }
        MemoryBudgetPanel(
            dashboard = dashboard,
            onMemory = { onNavigate(MaxDestination.ZramManager.route) },
            onStorage = { onNavigate(MaxDestination.StorageDetail.route) }
        )
        VerdictPanel(
            dashboard = dashboard,
            maxAi = maxAi,
            request = profileRequest,
            onLive = { onNavigate(MaxDestination.MaxLive.route) },
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onRetry = onAiRetry
        )
        HomeDetailsPanel(dashboard = dashboard, onNavigate = onNavigate)
        CommandDeck(
            onBoost = onProfile,
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onBattery = { onNavigate(MaxDestination.Charging.route) },
            onAdvanced = { onNavigate(MaxDestination.Control.route) }
        )
    }
}

/**
 * Brand, one state, two actions. The engine state is said once: it used to be a pill
 * ("active/idle", dot and color) *and* a subtitle line saying the same thing in words,
 * which is the definition of a screen that repeats itself. The pill keeps more
 * information (color, dot, localized word), so the duplicate line went.
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
 * The signature block. One headline reading (heat plus a plain-language verdict)
 * over three pressure meters, because the first question is never "what is CPU
 * load" but "is anything under pressure, and is the device hot". Straight bars
 * beat a dial here: they share one baseline, so three values are comparable at a
 * glance and every label has room to breathe in either writing direction.
 */
@Composable
private fun PulsePanel(
    deviceName: String,
    dashboard: DashboardState,
    onOverview: () -> Unit
) {
    val p = neuralPalette()
    val heat = primaryBatteryTemperatureC(dashboard)?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val heatAccent = temperatureAccent(heat)
    val calm = heat == null || heat < 43
    NeuralPanel(accent = p.accent, contentPadding = PaddingValues(18.dp), verticalSpacing = 16.dp) {
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
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NeuralCaption(stringResource(R.string.home_temperature_short), color = heatAccent)
                Row(verticalAlignment = Alignment.Bottom) {
                    NeuralValue(
                        heat?.toString() ?: "\u2014",
                        style = MonoValueStyleSmall.copy(
                            fontSize = 44.sp,
                            lineHeight = 48.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = p.text
                    )
                    Spacer(Modifier.width(4.dp))
                    NeuralCaption("\u00b0C", color = p.muted)
                }
            }
            NeuralPill(
                text = stringResource(if (calm) R.string.home_system_stable else R.string.home_system_attention),
                accent = if (calm) p.ok else heatAccent,
                dot = true
            )
        }
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
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_power_draw),
                value = "${dashboard.powerWatt.oneDecimal()} W",
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

/** Shown only when a real problem exists, so its presence itself means something. */
@Composable
private fun FocusCard(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    val heat = primaryBatteryTemperatureC(dashboard)?.roundToInt() ?: dashboard.cpuTempC
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

/**
 * Current CPU and GPU load — each with the frequency meter it belongs to.
 *
 * The load number is the tile's own reading; the meter answers the second question a bare
 * number cannot: **how far into its range is this clock**? A phone at 1.8 GHz is loafing on
 * a 3.2 GHz chip and pinned on a 2.0 GHz one, and only the meter says which.
 *
 * The frequency is stated **once**, by the meter. It used to be a support line *and* would
 * have been a bar; a number that appears twice under two names is how a screen teaches
 * people not to trust it. The trend of these same series is still drawn once, below, in the
 * spectrum — the tiles own "now", the chart owns "recently".
 *
 * CPU reads the **highest live core clock**, not `cpu0`: on big.LITTLE the first core idles
 * while the prime cluster does the work, so `cpu0` would draw a calm meter on a busy device.
 */
@Composable
private fun TrendDuo(dashboard: DashboardState, onCpu: () -> Unit, onGpu: () -> Unit) {
    val p = neuralPalette()
    val cpuCeiling = dashboard.cpuCeilingMhz.takeIf { it > 0 }
    val gpuCeiling = dashboard.gpuCeilingMhz?.takeIf { it > 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuralKpiTile(
            caption = stringResource(R.string.max_home_cpu_label),
            value = "${dashboard.cpuLoadPercent}%",
            accent = p.accent,
            onClick = onCpu,
            modifier = Modifier.weight(1f),
            meter = {
                NeuralClockWave(
                    reading = compactFrequency(dashboard.cpuTopCoreMhz),
                    ceiling = frequencyCeilingLabel(dashboard.cpuTopCoreMhz, cpuCeiling),
                    series = ClockWave.series(dashboard.loadSamples.map { it.cpuMhz }, cpuCeiling),
                    accent = p.accent,
                )
            }
        )
        NeuralKpiTile(
            caption = stringResource(R.string.max_home_gpu_label),
            value = dashboard.gpuLoadPercent?.let { "$it%" } ?: "\u2014",
            accent = p.accentAlt,
            onClick = onGpu,
            modifier = Modifier.weight(1f),
            meter = {
                NeuralClockWave(
                    reading = compactFrequency(dashboard.gpuFreqMhz),
                    ceiling = frequencyCeilingLabel(dashboard.gpuFreqMhz, gpuCeiling),
                    series = ClockWave.series(dashboard.loadSamples.map { it.gpuMhz }, gpuCeiling),
                    accent = p.accentAlt,
                )
            }
        )
    }
}

/**
 * «ما يحدث الآن؟» — حالة حيّة واحدة للمحرك والجهاز معًا.
 *
 * **ولماذا حلّت محلّ طيف الحمل:** الطيف كان يرسم **الحمل نفسه** الذي تُعلنه بطاقتان فوقه
 * وقدمه، ثم يعيده رقمًا ثالثًا في صفّ (الآن/المتوسط/الذروة) — أي أنه كرّر معلومة قائمة ثلاث
 * مرات ولم يجب سؤالًا واحدًا لا يجيبه ما فوقه. وأسوأ من التكرار أنه لم يكن يقول **مَن** يفعل
 * شيئًا: شاشة تدّعي أنها لوحة قيادة وفيها سطر يقيس نفسه أبدًا، والجهاز قد يكون تحت حماية
 * حرارية أو محجوبًا أو غير مُدار أصلًا.
 *
 * فهذه البطاقة تجيب السؤال الذي يُفتح التطبيق من أجله: **ما الذي يجري الآن، ومن قاله؟**
 * وثلاثة قرارات تحمي صدقها:
 *
 * 1. **لا جملة بلا مصدر**: كل ما يُعرض مشتقّ من حالة المحرك نفسها — لا هناك جدول أحداث
 *    تُغذّيه الواجهة، ولا مؤقّت يختلق نشاطًا. و`HomeActivityModelTest` يثبت القواعد في JVM.
 * 2. **الحالة الأساسية ليست فراغًا**: بلا حدث تُعرض حالة هادئة مفيدة (مراقبة/مُتوقف)،
 *    ومعها ثلاث قياسات حيّة في القدم — فهي بطاقة تُقرأ حتى والمحرك مغلق.
 * 3. **الحدث عابر ثم يعود**: الحدث يظهر بتغيير متحرّك قصير ثم تعود البطاقة إلى حالتها
 *    الأساسية تلقائيًّا — فما يبقى على الشاشة هو ما لا يزال صحيحًا.
 *
 * وقدم البطاقة (CPU · GPU · الحرارة) لا تكرّر التكرار القديم: تلك الأرقام **ليست** تحت
 * البطاقة، بل تحت البطاقات السفلى؛ وهذه ثلاث قراءات لحظية مختصرة تخدم من ينظر إلى «حالة
 * النظام» في سطر واحد.
 */
@Composable
private fun ActivityPanel(dashboard: DashboardState, onLive: () -> Unit) {
    val p = neuralPalette()
    val viewModel: HomeActivityViewModel = hiltViewModel()
    val signals by viewModel.signals.collectAsStateWithLifecycle()
    val labels by viewModel.appLabels.collectAsStateWithLifecycle()

    // الدقّة ثانية واحدة، ووظيفتها واحدة: أن تعرف البطاقة أن نافذة الحدث انتهت فتعود
    // لحالتها الأساسية. وهي لا تقرأ عتادًا ولا تُنشئ عيّنة — إعادة إسقاط لحالة قائمة.
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000L)
            value = System.currentTimeMillis()
        }
    }

    // نقص القراءة يُقاس بخصائص الإقلاع (سقف غير معلَن، لا عناقيد) لا بقراءة فاشلة عارضة:
    // «لا تدعم هذه النواة عقدة الرسوم» حقيقة، و«فشلت قراءة واحدة» ليست عطب قدرة.
    val missing = remember(dashboard.gpuCeilingMhz, dashboard.cores.size) {
        buildSet {
            if (dashboard.gpuCeilingMhz == null) add(ActivityReading.GPU)
            if (dashboard.cores.isEmpty()) add(ActivityReading.CORES)
        }
    }
    val activity = remember(signals, nowMs, missing) {
        HomeActivityModel.project(signals.copy(missingReadings = missing), nowMs)
    }

    val tone = when (activity.tone) {
        ActivityTone.CALM -> p.accent
        ActivityTone.WORKING -> p.ok
        ActivityTone.ATTENTION -> p.warn
        ActivityTone.DANGER -> p.danger
    }

    NeuralPanel(onClick = onLive, accent = tone) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_activity_title),
            caption = stringResource(R.string.home_activity_caption),
            accent = tone,
            trailing = { ActivityDot(tone, active = activity.state == ActivityState.APPLYING) }
        )
        AnimatedContent(
            targetState = activity,
            transitionSpec = { MaxMotion.enter() togetherWith MaxMotion.exit },
            label = "home-activity-body",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    activityTitle(current, labels),
                    color = p.text,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    activityDetail(current, labels),
                    color = p.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                stringResource(R.string.max_home_cpu_label),
                "${dashboard.cpuLoadPercent}%",
                p.accent,
                Modifier.weight(1f)
            )
            NeuralFactTile(
                stringResource(R.string.max_home_gpu_label),
                dashboard.gpuLoadPercent?.let { "$it%" } ?: DASH,
                p.accentAlt,
                Modifier.weight(1f)
            )
            NeuralFactTile(
                stringResource(R.string.home_activity_temp),
                primaryBatteryTemperatureC(dashboard)?.let { "${it.roundToInt()}°C" } ?: DASH,
                p.warn,
                Modifier.weight(1f)
            )
        }
    }
}

/**
 * سطر الحالة بعنوانه — الترجمة تُقرأ من المورد ولا تُركّب في المنطق.
 *
 * والأسماء تستعمل [ActivityState] و[ActivityTone] فقط: لا سلسلة نصية تُقارن، ولا "name"
 * يُعرض للمستخدم كما يفعل أي عرض آلي للحالة.
 */
@Composable
private fun activityTitle(activity: HomeActivity, labels: Map<String, String>): String = when (activity.state) {
    ActivityState.SAFETY -> stringResource(R.string.home_activity_safety_title)
    ActivityState.APPLYING -> stringResource(R.string.home_activity_applying_title)
    ActivityState.VERIFIED -> when {
        activity.exploration -> stringResource(R.string.home_activity_probe_title)
        activity.measured -> stringResource(R.string.home_activity_verified_title)
        else -> stringResource(R.string.home_activity_applied_title)
    }
    ActivityState.ROLLED_BACK -> if (activity.tone == ActivityTone.ATTENTION) {
        stringResource(R.string.home_activity_rollback_failed_title)
    } else {
        stringResource(R.string.home_activity_rollback_title)
    }
    ActivityState.REFUSED -> if (activity.tone == ActivityTone.CALM) {
        stringResource(R.string.home_activity_no_action_title)
    } else {
        stringResource(R.string.home_activity_refused_title)
    }
    ActivityState.UNSUPPORTED -> stringResource(R.string.home_activity_unsupported_title)
    ActivityState.APP_SWITCH -> stringResource(
        R.string.home_activity_app_switch_title,
        activity.appPackage?.let { labels[it] } ?: activity.appPackage.orEmpty()
    )
    ActivityState.MONITORING -> stringResource(R.string.home_activity_monitoring_title)
    ActivityState.IDLE -> stringResource(R.string.home_activity_idle_title)
}

/**
 * السطر الثاني: نصّ **المحرك** إن وُجد، وإلا جملة الحالة.
 *
 * والسبب المقيس أولى دائمًا: «wrote 1800000 read 1400000» يقولة المستخدم لغيره، وجملة
 * عامة مثلا «فشل التطبيق» لا.
 */
@Composable
private fun activityDetail(activity: HomeActivity, labels: Map<String, String>): String {
    val measured = activity.detail?.takeIf { it.isNotBlank() }
    return when (activity.state) {
        ActivityState.SAFETY -> measured ?: stringResource(R.string.home_activity_safety_detail)
        ActivityState.APPLYING -> stringResource(R.string.home_activity_applying_detail)
        ActivityState.MONITORING -> stringResource(R.string.home_activity_monitoring_detail)
        ActivityState.IDLE -> stringResource(R.string.home_activity_idle_detail)
        ActivityState.APP_SWITCH -> stringResource(R.string.home_activity_app_switch_detail)
        ActivityState.UNSUPPORTED -> stringResource(
            R.string.home_activity_unsupported_detail,
            missingLabels(activity.missingReadings)
        )
        // الأحكام: القيمة المقيسة إن وُجدت، وإلا نصّ المحرك، وإلا ما استقرّ عليه المقبض.
        else -> measured
            ?: activity.value?.let { "${activity.knobLabel.orEmpty()} · $it" }?.takeIf { activity.knobLabel != null }
            ?: activity.knobLabel.orEmpty()
    }
}

/** أسماء القراءات الناقصة كما يقرؤها المستخدم — من الموراد لا من أسماء الأصناف. */
@Composable
private fun missingLabels(missing: Set<ActivityReading>): String = listOfNotNull(
    if (ActivityReading.GPU in missing) stringResource(R.string.max_home_gpu_label) else null,
    if (ActivityReading.CORES in missing) stringResource(R.string.home_activity_missing_cores) else null,
    if (ActivityReading.THERMAL in missing) stringResource(R.string.home_activity_temp) else null,
).joinToString(" · ")

/**
 * نقطة الحالة: تنبض **فقط** حين يكون شيء قيد التنفيذ.
 *
 * التنفّس المستمر في كل الحالات كان سيصير خلفية متحرّكة لا معلومة؛ فالثابت هنا يقول
 * «لا شيء يتغيّر الآن» وذلك النبض يقولة «اكتب الآن».
 */
@Composable
private fun ActivityDot(accent: Color, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "home-activity-dot")
    val breathing by transition.animateFloat(
        initialValue = if (active) .4f else .9f,
        targetValue = if (active) 1f else .9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home-activity-dot-alpha",
    )
    Box(Modifier.size(9.dp).clip(CircleShape).background(accent.copy(alpha = breathing)))
}

/** علامة غياب قراءة — شرطة، لا صفر: الصفر ادّعاء عن العتاد. */
private const val DASH = "\u2014"

/**
 * Whether a frequency ceiling can be stated at all.
 *
 * Three answers, not two: a ceiling, an explicit "this kernel declares none", and `null`
 * when the reading itself is missing — there is no point labelling the range of a clock we
 * never read. A ceiling is never inferred from readings we happened to see: the highest
 * value this app observed is not the chip's range, and a meter drawn against it would
 * measure our own sample history.
 */
@Composable
private fun frequencyCeilingLabel(currentMhz: Int?, ceilingMhz: Int?): String? {
    if (currentMhz == null || currentMhz <= 0) return null
    if (ceilingMhz == null || ceilingMhz <= 0) return stringResource(R.string.home_freq_ceiling_unknown)
    return stringResource(R.string.home_freq_ceiling, compactFrequency(ceilingMhz))
}

@Composable
private fun MemoryBudgetPanel(
    dashboard: DashboardState,
    onMemory: () -> Unit,
    onStorage: () -> Unit
) {
    val p = neuralPalette()
    val swapUsed = dashboard.swapUsedMb
    val swapTotal = dashboard.swapTotalMb
    val storageUsed = dashboard.storageUsedGb
    val storageTotal = dashboard.storageTotalGb
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_memory_storage),
            caption = stringResource(R.string.home_memory_storage_desc),
            accent = p.ok
        )
        NeuralBudgetBar(
            label = "RAM",
            value = "${gigabytes(dashboard.ramUsedMb)} / ${gigabytes(dashboard.ramTotalMb)}",
            fraction = fractionOf(dashboard.ramUsedMb, dashboard.ramTotalMb),
            accent = p.accent,
            support = stringResource(
                R.string.home_available_memory,
                gigabytes(dashboard.ramTotalMb - dashboard.ramUsedMb)
            ),
            onClick = onMemory
        )
        if (swapUsed != null && swapTotal != null && swapTotal > 0) {
            NeuralBudgetBar(
                label = "ZRAM",
                value = "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}",
                fraction = fractionOf(swapUsed, swapTotal),
                accent = p.accentAlt,
                support = stringResource(R.string.home_available_swap, gigabytes(swapTotal - swapUsed)),
                onClick = onMemory
            )
        }
        NeuralBudgetBar(
            label = stringResource(R.string.max_home_storage),
            value = "${storageUsed.oneDecimal()} / ${storageTotal.oneDecimal()} GB",
            fraction = if (storageTotal <= 0f) 0f else (storageUsed / storageTotal).coerceIn(0f, 1f),
            accent = p.ok,
            support = stringResource(R.string.home_available_storage, (storageTotal - storageUsed).oneDecimal()),
            onClick = onStorage
        )
    }
}

/** One verdict: what is limiting the device, how sure we are, what changed. */
@Composable
private fun VerdictPanel(
    dashboard: DashboardState,
    maxAi: MaxAiState,
    request: ProfileRequestState,
    onLive: () -> Unit,
    onThermal: () -> Unit,
    onRetry: () -> Unit
) {
    val p = neuralPalette()
    val intel = dashboard.intelligence
    val accent = when (intel.realImprovement) {
        true -> p.ok
        false -> p.danger
        null -> p.accent
    }
    NeuralPanel(accent = accent) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_story_title),
            caption = stringResource(R.string.home_session_confidence, intel.confidencePercent),
            accent = accent,
            trailing = {
                NeuralPill(
                    text = when (intel.realImprovement) {
                        true -> stringResource(R.string.home_session_real_yes)
                        false -> stringResource(R.string.home_session_real_no)
                        null -> stringResource(R.string.home_session_collecting)
                    },
                    accent = accent,
                    filled = true
                )
            }
        )
        Text(
            intel.explanation,
            color = p.text,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        NeuralTrack(intel.confidencePercent / 100f, accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.home_story_bottleneck),
                value = intel.primaryLimiter,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_session_workload),
                value = intel.samples.lastOrNull()?.workload ?: "\u2014",
                accent = p.accentAlt,
                modifier = Modifier.weight(1f)
            )
        }
        intel.events.lastOrNull()?.let { event ->
            NeuralFeedRow(
                icon = Icons.Rounded.Bolt,
                title = event.title,
                meta = event.impact.takeIf { it.isNotBlank() },
                accent = p.accentAlt
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = stringResource(R.string.home_session_open_loop),
                accent = p.accent,
                icon = Icons.Rounded.Timeline,
                onClick = onLive
            )
            Spacer(Modifier.width(8.dp))
            NeuralPill(
                text = stringResource(R.string.home_session_open_heat),
                accent = p.warn,
                icon = Icons.Rounded.Thermostat,
                onClick = onThermal
            )
            Spacer(Modifier.weight(1f))
            if (request.inFlight) {
                NeuralValue(
                    stringResource(R.string.max_home_ai_working),
                    style = MonoValueStyleSmall.copy(fontSize = 10.sp),
                    color = p.muted
                )
            } else if (request.result != null) {
                NeuralPill(
                    text = stringResource(R.string.max_home_retry),
                    accent = p.muted,
                    onClick = onRetry
                )
            } else if (maxAi.strategyLabel.isNotBlank()) {
                NeuralCaption(maxAi.strategyLabel)
            }
        }
    }
}

@Composable
private fun CommandDeck(
    onBoost: () -> Unit,
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onAdvanced: () -> Unit
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
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.home_action_boost),
                support = stringResource(R.string.home_action_boost_desc),
                accent = p.accent,
                onClick = onBoost,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Thermostat,
                title = stringResource(R.string.home_action_thermal),
                support = stringResource(R.string.home_action_thermal_desc),
                accent = p.warn,
                onClick = onThermal,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.home_action_battery),
                support = stringResource(R.string.home_action_battery_desc),
                accent = p.ok,
                onClick = onBattery,
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
