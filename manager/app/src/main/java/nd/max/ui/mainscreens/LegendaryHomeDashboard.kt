package nd.max.ui.mainscreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import nd.max.R
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.NeuralActionTile
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralFeedRow
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTile
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeUiState
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
 * Reading order, each block earning its place exactly once:
 *  0. storyboard— what your choices are actually doing right now: the last per-app
 *               session's verified hardware results, MAX AI's owned knobs, and your
 *               own manual locks. It states outcomes, never instruments: heat, load and
 *               cores keep living in the screens that own them.
 *  1. pulse   — device identity, heat, uptime, battery and power draw at a glance
 *  2. focus   — appears only when something is actually wrong
 *  3. matrix  — memory and storage capacity: RAM, ZRAM and internal storage side by side
 *  4. verdict — the limiter, in one sentence, with recent events
 *  5. deck    — four destinations people actually reach for
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
    profileRequest: ProfileRequestState,
    deviceName: String,
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
        PulsePanel(
            deviceName = deviceName,
            dashboard = dashboard,
            onOverview = { onNavigate(MaxDestination.Diagnostics.route) },
            // مدخل Max AI من أول بطاقة (طلب المالك): كان مقعدًا في الشريط السفلي، وصار
            // بوّابة في البطاقة التي تُقرأ أولًا — والمقعد الذي أخلاه صار للإعدادات.
            onMaxAi = { onNavigate(MaxDestination.MaxAi.route) }
        )
        HardwarePulseCards(
            dashboard = dashboard,
            onCpu = { onNavigate(MaxDestination.CpuCoreControl.route) },
            onGpu = { onNavigate(MaxDestination.GpuStudio.route) }
        )
        // بطاقة نشاط واحدة: تحكي الأثر المؤكد فقط، وتترك القياسات لشاشاتها المالكة.
        UnifiedActivityCard(maxAi = maxAi)
        FocusCard(dashboard, onNavigate)
        // مصفوفة الذاكرة تحت بطاقة التحذير مباشرة: من رأى «التخزين يكاد يمتلئ» يجد
        // تحته الأرقام التي تشرح العبارة، بلا أن يعيد هذا السطر إطلاق الحُكم نفسه.
        MemoryMatrixCard(dashboard = dashboard, onNavigate = onNavigate)
        VerdictPanel(
            dashboard = dashboard,
            maxAi = maxAi,
            request = profileRequest,
            onLive = { onNavigate(MaxDestination.MaxLive.route) },
            onThermal = { onNavigate(MaxDestination.ThermalDetail.route) },
            onRetry = onAiRetry
        )
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
    modifier: Modifier = Modifier
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
            .clip(RoundedCornerShape(22.dp))
            .background(p.tile.copy(alpha = .92f))
            .border(BorderStroke(1.dp, accent.copy(alpha = .26f)), RoundedCornerShape(22.dp))
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
            formatHardwareFrequency(current),
            color = p.muted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        FrequencySparkline(
            samples = graph,
            accent = accent,
            floorFraction = floorFraction,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        // حدّا المدى تحت الرسم، كلُّ حدٍّ تحت المستوى الذي يمثّله فعلًا: الأرضية خطُّ إسناد
        // مرسوم داخل الرسم، والسقف أعلاه. ولمّا يُعلن أيّهما لا يُكتب شيء — فسطر «— —»
        // ليس مدى، وقد يُقرأ كصفر.
        if (floor != null || ceilingMhz?.takeIf { it > 0 } != null) {
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
                    formatHardwareFrequency(ceilingMhz?.takeIf { it > 0 }),
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

@Composable
private fun FrequencySparkline(
    samples: List<Float>,
    accent: Color,
    floorFraction: Float? = null,
    modifier: Modifier = Modifier
) {
    val p = neuralPalette()
    val grid = p.muted.copy(alpha = .07f)
    val floorLine = p.muted.copy(alpha = .22f)
    Canvas(modifier) {
        if (samples.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val top = 5f
        val bottom = h - 5f
        val usable = (bottom - top).coerceAtLeast(1f)
        val step = w / (samples.size - 1).toFloat()
        val points = samples.mapIndexed { index, value ->
            Offset(step * index, top + usable * (1f - value.coerceIn(0f, 1f)))
        }

        drawLine(grid, Offset(0f, top), Offset(w, top), 1f)
        drawLine(grid, Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
        drawLine(grid, Offset(0f, bottom), Offset(w, bottom), 1f)

        // خطّ الأرضية المعلنة: يُرسم على مستواه الحقيقي داخل المدى، فيصبح المدى مقروءًا من
        // الرسم نفسه لا مِن نصّ مجاور. ويُشتقّ من الرقم الذي أعلنته النواة، لا من أدنى عيّنة.
        floorFraction?.let { fraction ->
            val y = top + usable * (1f - fraction)
            drawLine(floorLine, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                quadraticTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
            }
            lineTo(points.last().x, points.last().y)
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(path)
            lineTo(points.last().x, bottom)
            lineTo(points.first().x, bottom)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = .20f), accent.copy(alpha = .015f)),
                startY = top,
                endY = bottom
            )
        )
        drawPath(
            path,
            color = accent.copy(alpha = .14f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx())
        )
        drawPath(
            path,
            color = accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        drawCircle(accent, radius = 3.dp.toPx(), center = points.last())
    }
}

@Composable
private fun PulsePanel(
    deviceName: String,
    dashboard: DashboardState,
    onOverview: () -> Unit,
    onMaxAi: () -> Unit
) {
    val p = neuralPalette()
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
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
            // مفتاح Max AI **مكان** كلمة الحالة (طلب المالك: «بدل النظام مستقر»)،
            // لا بجانبها. والوسم هنا **زرّ**: `filled` + النجمة + `onClick` تقول
            // «اضغطني» بلا سطر يشرح ذلك.
            //
            // ولماذا حُذفت كلمة الحالة في الحالة السليمة ولم تُحذف معها في غيرها:
            // العبارة مشتقّة من الحرارة نفسها (`calm` = أقل من ٤٣°)، والحرارة تُطبع
            // رقمًا كبيرًا في هذا الصفّ بعينه — فـ«النظام مستقر» في كل فتحة تطبيق
            // تكرارٌ لمعلومةٍ معروضة، وهو الذي طُلب إزالته. أمّا «يحتاج انتباه» فتفسيرٌ
            // يُضاف إلى الرقم، وإخفاؤه إخفاءٌ لإنذار حقيقي ⇒ مخالف لـADR-07.
            // (و`home_system_stable` يبقى في الموارد بلا مستهلك — لا يُحذف: ADR-18،
            // وهو مفيد لأي سطح يعرض الحالة وحدها بلا رقم بجانبها.)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!calm) {
                    NeuralCaption(
                        stringResource(R.string.home_system_attention),
                        color = heatAccent
                    )
                }
                // و`navigates = true` مُضافة هنا **بطلب المالك** («لا يدل على أنه يدخلك إلى شاشة
                // أخرى»)؛ ويومها كانت موضعًا واحدًا، وقيست بعدها في السطور المجاورة: كل وسم
                // يقود إلى شاشة أخرى في هذه الرئيسية كان بلا سهم — فالمستدعى يُصلح الصنف.
                NeuralPill(
                    text = stringResource(R.string.max_nav_max_ai),
                    accent = p.accent,
                    icon = Icons.Rounded.AutoAwesome,
                    filled = true,
                    navigates = true,
                    onClick = onMaxAi
                )
            }
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
            navigates = true,
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

/**
 * مصفوفة الذاكرة — سعة RAM وZRAM والتخزين الداخلي في بطاقة واحدة.
 *
 * **ما هي وما ليست:** تعرض **حقائق سعة** (المستخدَم من الإجمالي، والمتاح)، ولا تُصدر حُكم ضغط.
 * وهذا ليس تحفّظًا شكليًّا: `ADR-34` يقرّر أن ضغط الذاكرة يُقاس بPSI لا بنسبة الامتلاء،
 * وأجهزة بنسبة امتلاء متقاربة تختلف في أثرها على الأداء اختلافًا كبيرًا. فالحُكم في هذه الشاشة
 * يبقى في `FocusCard` وحدها، وهذه البطاقة تجيب السؤال الآخر: «كم بقي؟».
 *
 * **ولذلك لا عتبات ولا ألوان إنذار هنا:** لون كلّ صفّ هوية (الأزرق/التركوا/الثانوي) لا حكم،
 * والمقارنة تكفلها الأشرطة والرقم المكتوب. ولون تحذير مستحدث هنا يعني عتبة امتلاء هي بالضبط
 * ما نهى عنه ADR-34 — والقارئ ينسى أن العتبة أُضيفت في الواجهة.
 *
 * **والمصادر أوعية موجودة، لا أوعية جديدة:** الأرقام من نفس لقطة اللوحة، والإجراءات إلى
 * الشاشتين المالكين للرقم (`ZramManager` · `StorageDetail`) — ولهذا صار كل صفّ قابلًا للنقر
 * بذاته: نقر بطاقة كاملة كان سيوصل صفّ التخزين إلى شاشة الذاكرة، وهي كذبة صغيرة.
 *
 * **وما لم يُقرأ لا يُصاغ:** غياب التبديل أو إجمالي الذاكرة يُكتب نصًّا («غير متاح»)
 * وبلا شريط، لا أحد عشرًا صفرًا ولا شريطًا فارغًا يُقرأ كـ«فارغ».
 */
@Composable
private fun MemoryMatrixCard(
    dashboard: DashboardState,
    onNavigate: (String) -> Unit
) {
    val p = neuralPalette()
    val ramTotal = dashboard.ramTotalMb
    val ramUsed = dashboard.ramUsedMb
    val swapTotal = dashboard.swapTotalMb
    val swapUsed = dashboard.swapUsedMb
    val storageTotal = dashboard.storageTotalGb
    val storageFree = (storageTotal - dashboard.storageUsedGb).coerceAtLeast(0f)

    NeuralPanel(accent = p.accent) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_memory_storage),
            caption = stringResource(R.string.home_memory_storage_desc),
            accent = p.accent,
        )

        MemoryFactRow(
            label = stringResource(R.string.ram_label),
            detail = if (ramTotal > 0) "${gigabytes(ramUsed)} / ${gigabytes(ramTotal)}" else null,
            status = if (ramTotal > 0) {
                stringResource(R.string.home_available_memory, gigabytes(ramTotal - ramUsed))
            } else {
                stringResource(R.string.max_home_unavailable)
            },
            fraction = if (ramTotal > 0) fractionOf(ramUsed, ramTotal) else null,
            accent = p.accent,
            // صفّ RAM كان يفتح **مدير ZRAM** — عطب مقصود (نسخ الصفّ المجاور) لا خيار: من
            // يضغط «RAM» يسأل عن الذاكرة العشوائية، ومدير ZRAM شاشةٌ أخرى. الصحيح مركز
            // الذاكرة (`MemoryHub`) الذي يضمّ RAM وZRAM معًا؛ وصفّ ZRAM تحت يبقى على مديره.
            onClick = { onNavigate(MaxDestination.MemoryHub.route) },
        )

        MemoryFactRow(
            label = stringResource(R.string.home_memory_swap_label),
            detail = if (swapTotal != null && swapTotal > 0 && swapUsed != null) {
                "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}"
            } else {
                null
            },
            status = if (swapTotal != null && swapTotal > 0 && swapUsed != null) {
                stringResource(R.string.home_available_swap, gigabytes(swapTotal - swapUsed))
            } else {
                stringResource(R.string.home_zram_unavailable)
            },
            fraction = if (swapTotal != null && swapTotal > 0 && swapUsed != null) {
                fractionOf(swapUsed, swapTotal)
            } else {
                null
            },
            accent = p.accentAlt,
            onClick = { onNavigate(MaxDestination.ZramManager.route) },
        )

        MemoryFactRow(
            label = stringResource(R.string.home_internal_storage),
            detail = if (storageTotal > 0f) {
                "${dashboard.storageUsedGb.oneDecimal()} / ${storageTotal.oneDecimal()} GB"
            } else {
                null
            },
            status = if (storageTotal > 0f) {
                stringResource(R.string.home_available_storage, storageFree.oneDecimal())
            } else {
                stringResource(R.string.max_home_unavailable)
            },
            fraction = if (storageTotal > 0f) {
                (dashboard.storageUsedGb / storageTotal).coerceIn(0f, 1f)
            } else {
                null
            },
            accent = p.ok,
            onClick = { onNavigate(MaxDestination.StorageDetail.route) },
        )
    }
}

/**
 * صفّ سعة واحد: اسم الوعاء · المستخدَم/الإجمالي · المتاح · شريط.
 *
 * والمتاح هو السطر الأبرز لأنه سؤال المستخدم فعلًا («كم بقي؟»)، والمستخدَم/الإجمالي يبقى
 * بجانب الاسم لأن بدون إجمالي لا يُقرأ المتاح على أنه كثير أو قليل.
 */
@Composable
private fun MemoryFactRow(
    label: String,
    detail: String?,
    status: String,
    fraction: Float?,
    accent: Color,
    onClick: () -> Unit
) {
    val p = neuralPalette()
    NeuralTile(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        verticalSpacing = 6.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent))
                Spacer(Modifier.width(7.dp))
                NeuralCaption(label)
            }
            if (detail != null) {
                NeuralValue(
                    detail,
                    style = MonoValueStyleSmall.copy(fontSize = 12.sp),
                    color = p.text
                )
            }
            // السهم في نهاية السطر: هذا الصفّ **بابٌ** لا بيان (`NeuralTile(onClick)` يقود
            // إلى وجهة مختلفة لكل صفّ: مركز الذاكرة · مدير ZRAM · تفصيل التخزين) — وكان
            // يُقرأ رقمًا وبطاقة فحسب، وهو نفس العطب الذي أبلغ عنه المالك في وسم Max AI
            // («لا يدل على أنه سيدخلك إلى شاشة أخرى»)، مُقاسًا هنا في ثلاثة صفوف معًا.
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(15.dp),
                tint = accent,
            )
        }
        Text(status, color = accent, fontSize = 11.sp, lineHeight = 15.sp)
        fraction?.let { NeuralTrack(it, accent.copy(alpha = .85f), height = 5.dp) }
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
                navigates = true,
                onClick = onLive
            )
            Spacer(Modifier.width(8.dp))
            NeuralPill(
                text = stringResource(R.string.home_session_open_heat),
                accent = p.warn,
                icon = Icons.Rounded.Thermostat,
                navigates = true,
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
