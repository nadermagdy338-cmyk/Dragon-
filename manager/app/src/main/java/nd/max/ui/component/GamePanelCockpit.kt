/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.gamespace.BypassState
import nd.max.core.gamespace.CockpitModel
import nd.max.core.gamespace.PanelClocks
import nd.max.core.gamespace.PanelControlState
import nd.max.core.gamespace.PanelDevice
import nd.max.core.gamespace.PanelQuickApps
import nd.max.core.gamespace.PanelReminder
import nd.max.core.gamespace.PanelScreenRecorder
import nd.max.core.gamespace.PanelSessionClock
import nd.max.core.gamespace.PanelSide
import nd.max.core.gamespace.PanelToggles
import nd.max.core.gamespace.QuickApp
import nd.max.core.platform.HudField
import nd.max.core.platform.HudReading
import nd.max.core.platform.HudTally
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import kotlin.math.hypot
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState

/*
 * لوحة اللعبة المفتوحة بتصميم «Energy Cube»: عمودان مائلان من مقاطع (CPU يسارًا، GPU يمينًا)
 * يضيء منهما بقدر التردد الحيّ، وأرقام كبيرة بأقراص متوهجة وحبوب سداسية، ومقياسا السطوع والصوت،
 * وقائمة بطاقات يسارًا وشبكة بلاطات يمينًا. اللون يتبع البروفايل: أحمر=أداء، كهرماني=متوازن،
 * أخضر=توفير، أزرق=تلقائي. وكل بلاطة تكتب بالروت فعلًا ثم تُعيد اللوحة قراءة الحالة الحقيقية.
 */
private const val BAR_BLOCKS = 16
private const val BAR_BLOCKS_UP = 5
private const val METER_BARS = 14
private const val ENTER_MS = 560
private const val LEAVE_MS = 240
private const val HOT_CPU = 90
private const val HOT_HEAT_C = 42f

private val CubeInk = Color(0xFFF4F5F7)
private val CubeDim = Color(0xFFB4B8BF)
private val CubeTile = Color(0xE63A3B3F)
private val CubeBlockOff = Color(0xCC24252A)
private val CubeGlass = Color(0x66000000)
private val ModeRed = Color(0xFFFF4D5E)
private val ModeAmber = Color(0xFFFFC53D)
private val ModeGreen = Color(0xFF3DDC97)
private val ModeAuto = Color(0xFF4DD0FF)

private typealias Enter = Animatable<Float, AnimationVector1D>

/** حبّة سداسية الطرفين (سهمية) كما في زرّ الوضع في المكعّب. */
private val PillShape = GenericShape { size, _ ->
    val t = size.height * 0.42f
    moveTo(0f, size.height / 2f)
    lineTo(t, 0f)
    lineTo(size.width - t, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - t, size.height)
    lineTo(t, size.height)
    close()
}

private fun modeAccent(controls: PanelControlState, fallback: Color): Color = when {
    controls.auto -> ModeAuto
    controls.profile == "1" -> ModeRed
    controls.profile == "2" -> ModeAmber
    controls.profile == "3" -> ModeGreen
    else -> fallback
}

private class CubeProbe(
    val wifi: Boolean, val mobile: Boolean, val dnd: Boolean,
    val touch: Boolean, val rec: Boolean, val minutes: Int
)

private class CubeTileSpec(
    val icon: ImageVector?, val text: String?, val label: String,
    val active: Boolean, val onClick: () -> Unit
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun GamePanelCockpit(
    gameLabel: String,
    reading: HudReading?,
    tally: HudTally?,
    fields: List<HudField>,
    accent: Color,
    side: PanelSide,
    recording: Boolean,
    refreshRateHz: Int?,
    clocks: PanelClocks,
    controls: PanelControlState,
    frames: List<Float>,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onCycleRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenControls: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onToggleBypass: () -> Unit,
    origin: Offset = Offset(1f, 0.5f)
) {
    val context = LocalContext.current
    val device = remember { PanelDevice(context) }
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    var touchedAt by remember { mutableStateOf(0L) }
    var holdUntil by remember { mutableStateOf(0L) }
    var activeAt by remember { mutableStateOf(android.os.SystemClock.uptimeMillis()) }
    var brightness by remember { mutableStateOf(device.brightness()) }
    var volume by remember { mutableStateOf(device.volume()) }
    val brightnessAllowed = remember { device.canSetBrightness() }
    var rotationLocked by remember { mutableStateOf(device.rotationLocked()) }
    var status by remember { mutableStateOf(device.status()) }
    var wifi by remember { mutableStateOf(false) }
    var mobile by remember { mutableStateOf(true) }
    var dnd by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(false) }
    var screenRec by remember { mutableStateOf(false) }
    var reminderMin by remember { mutableStateOf(0) }
    var optimizing by remember { mutableStateOf(false) }
    var listView by remember { mutableStateOf(true) }
    var details by remember { mutableStateOf(false) }
    var appsOpen by remember { mutableStateOf(false) }
    val notes = remember { mutableStateListOf<String>() }
    val hasCellular = remember { PanelToggles.hasCellular(context) }
    val touchAvailable by produceState(false) { value = withContext(Dispatchers.IO) { PanelToggles.touchAvailable() } }
    val quickApps by produceState(emptyList<QuickApp>()) { value = withContext(Dispatchers.IO) { PanelQuickApps.installed(context) } }

    val lWifi = stringResource(R.string.game_panel_tool_wifi)
    val lData = stringResource(R.string.game_panel_tool_data)
    val lDefault = stringResource(R.string.game_panel_hz_default)
    val lInfo = stringResource(R.string.game_panel_tool_info)
    val lRecord = stringResource(R.string.game_panel_tool_record)
    val lRecording = stringResource(R.string.game_panel_tool_recording)
    val lScreen = stringResource(R.string.game_panel_tool_screenrec)
    val lScreenOn = stringResource(R.string.game_panel_tool_screenrec_on)
    val lRotation = stringResource(R.string.game_panel_tool_rotation)
    val lOptimize = stringResource(R.string.game_panel_tool_optimize)
    val lTouch = stringResource(R.string.game_panel_tool_touch)
    val lBypass = stringResource(R.string.game_panel_tool_bypass)
    val lReminder = stringResource(R.string.game_panel_tool_reminder)
    val lApp = stringResource(R.string.game_panel_tool_app)
    val lClose = stringResource(R.string.game_panel_tool_close)
    val lNormal = stringResource(R.string.game_panel_notify_normal)
    val lDnd = stringResource(R.string.game_panel_notify_dnd)
    val fmtOn = stringResource(R.string.game_panel_msg_on)
    val fmtOff = stringResource(R.string.game_panel_msg_off)
    val fmtFreed = stringResource(R.string.game_panel_msg_freed)
    val fmtReminder = stringResource(R.string.game_panel_msg_reminder)
    val msgDone = stringResource(R.string.game_panel_reminder_done)
    val msgSaved = stringResource(R.string.game_panel_msg_saved)
    val msgAuto = stringResource(R.string.game_panel_auto_locked)
    val lAuto = stringResource(R.string.str_auto_mode)
    val lPerf = stringResource(R.string.profile_performance)
    val lBal = stringResource(R.string.profile_balanced)
    val lSave = stringResource(R.string.profile_powersave)
    val cdList = stringResource(R.string.game_panel_view_list)
    val cdGrid = stringResource(R.string.game_panel_view_grid)
    val cdCollapse = stringResource(R.string.game_panel_collapse_cd)
    val cdSide = stringResource(R.string.game_panel_drawer_cd)

    val wave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { wave.animateTo(1f, tween(1150, easing = LinearOutSlowInEasing)) }
        enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 170f))
    }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - touchedAt > 1200) {
                brightness = device.brightness()
                volume = device.volume()
            }
            rotationLocked = device.rotationLocked()
            status = device.status()
            if (tick++ % 3 == 0 && now > holdUntil) {
                val probe = withContext(Dispatchers.IO) {
                    CubeProbe(
                        wifi = PanelToggles.wifiOn(context), mobile = PanelToggles.dataOn(context),
                        dnd = PanelToggles.dndOn(context), touch = PanelToggles.touchOn(),
                        rec = PanelScreenRecorder.isRecording(), minutes = PanelReminder.remainingMin()
                    )
                }
                wifi = probe.wifi; mobile = probe.mobile; dnd = probe.dnd
                touch = probe.touch; screenRec = probe.rec; reminderMin = probe.minutes
            }
            delay(800)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (CockpitModel.shouldCollapseIdle(android.os.SystemClock.uptimeMillis(), activeAt)) {
                if (!leaving) {
                    leaving = true
                    enter.animateTo(0f, tween(LEAVE_MS))
                    onCollapse()
                }
                break
            }
        }
    }

    fun leave(after: () -> Unit) {
        if (leaving) return
        leaving = true
        scope.launch {
            enter.animateTo(0f, tween(LEAVE_MS))
            after()
        }
    }

    fun say(text: String) {
        notes.add(text)
        while (notes.size > 3) notes.removeAt(0)
        scope.launch { delay(4200); notes.remove(text) }
    }

    fun hold() { holdUntil = android.os.SystemClock.uptimeMillis() + 1600 }

    /** قلب مقبض: يعرض الحالة المطلوبة فورًا، ويكتب بالروت، ثم تصحّحه القراءة الحقيقية. */
    fun flip(label: String, now: Boolean, set: (Boolean) -> Unit, write: (Boolean) -> Unit) {
        val want = !now
        hold()
        set(want)
        say(String.format(if (want) fmtOn else fmtOff, label))
        PanelToggles.async { write(want) }
    }

    fun slide(old: Float, new: Float, write: (Float) -> Unit) {
        touchedAt = android.os.SystemClock.uptimeMillis()
        if (CockpitModel.segmentOf(new, METER_BARS) != CockpitModel.segmentOf(old, METER_BARS)) write(new)
    }

    fun cycleProfile() {
        if (controls.auto) { say(msgAuto); return }
        val order = listOf("1", "2", "3")
        onSelectProfile(order[(order.indexOf(controls.profile) + 1).mod(order.size)])
    }

    val heat = reading?.heat ?: 0f
    val hot = (reading?.cpu ?: 0) >= HOT_CPU || heat >= HOT_HEAT_C
    val tint = if (hot) PanelDanger else modeAccent(controls, accent)
    val cpuFraction by animateFloatAsState(fraction(clocks.cpuMhz, clocks.cpuCeilingMhz) ?: 0f, tween(450), label = "cpuFraction")
    val gpuFraction by animateFloatAsState(fraction(clocks.gpuMhz, clocks.gpuCeilingMhz) ?: 0f, tween(450), label = "gpuFraction")
    val title = rememberGameTitle(gameLabel)
    val modeName = when {
        controls.auto -> lAuto
        controls.profile == "1" -> lPerf
        controls.profile == "2" -> lBal
        controls.profile == "3" -> lSave
        else -> MAX_VALUE_UNAVAILABLE
    }
    var dragged = 0f

    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Brush.horizontalGradient(listOf(Color(0xD9000000), Color(0x8C000000), Color(0x8C000000), Color(0xD9000000))))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    activeAt = android.os.SystemClock.uptimeMillis()
                }
            }
            .pointerInput(Unit) { detectTapGestures { leave(onCollapse) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, delta -> dragged += delta },
                    onDragEnd = { if (kotlin.math.abs(dragged) > 160f) leave(onCollapse) }
                )
            }
    ) {
        val w = maxWidth
        val h = maxHeight
        val v = h.value / 100f
        val u = minOf(v, w.value / 180f)
        val compact = CockpitModel.isCompact(w.value)
        var leftOpen by remember(compact) { mutableStateOf(!compact) }
        val btn = maxOf(40f, u * 7.6f).dp
        val tile = maxOf(44f, u * 8.2f).dp
        val colW = tile + 22.dp
        val columns = if (compact) 1 else 2
        val gridW = colW * columns + 6.dp * (columns - 1)
        val gaugeW = if (compact) w * 0.30f else (w.value * 0.17f).coerceIn(120f, 200f).dp
        val cpuX = if (compact) 0.30f else 0.25f
        val gpuX = if (compact) 0.62f else 0.75f
        val listW = if (details) 176.dp else (w.value * 0.15f).coerceIn(124f, 170f).dp
        val topPad = (v * 2.2f).dp
        val bodyTop = (v * 12.5f).dp

        Canvas(Modifier.fillMaxSize()) { cubeWave(origin, wave.value, tint) }
        Canvas(Modifier.fillMaxSize()) {
            val sweep = enter.value
            cubeBar(left = true, lit = cpuFraction, tint = tint, sweep = sweep)
            cubeBar(left = false, lit = gpuFraction, tint = tint, sweep = sweep)
        }

        // ── أعلى اليسار: الرئيسية / تحرير القائمة / شعار المكعّب (طيّ)
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = topPad).enterFrom(enter, -24f, 0f, 0f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CubeCircle(btn, lApp, { onOpenControls(); leave(onCollapse) }) {
                Icon(Icons.Rounded.Home, null, tint = CubeInk, modifier = Modifier.size(btn * 0.5f))
            }
            CubeCircle(btn, cdSide, { leftOpen = !leftOpen }, active = leftOpen, tint = tint) {
                Icon(Icons.Rounded.Edit, null, tint = CubeInk, modifier = Modifier.size(btn * 0.5f))
            }
            CubeCircle(btn, cdCollapse, { leave(onCollapse) }) { CubeLogo(Modifier.size(btn * 0.54f)) }
        }

        // ── أعلى الوسط: إطارات/شبكة/بطارية/مدّة اللعب
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = (v * 6.8f).dp).clip(RoundedCornerShape(MaxRadius.row))
                .background(CubeGlass).padding(horizontal = MaxSpace.rowPaddingHorizontal, vertical = 6.dp).swallowTaps()
                .enterFrom(enter, 0f, -24f, 0f),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            val fps = reading?.frames?.let { "%.0f".format(Locale.US, it) } ?: MAX_VALUE_UNAVAILABLE
            StatusBit(null, "$fps FPS")
            StatusBit(Icons.Rounded.Language, CockpitModel.speedText(status.netBytes))
            StatusBit(Icons.Rounded.BatteryChargingFull, batteryText(status.batteryPct, status.charging))
            StatusBit(Icons.Rounded.Timer, PanelSessionClock.hoursText())
        }

        // ── أعلى اليمين: تطبيقات سريعة (مثبّتة فعلًا) + سهم التوسيع
        if (quickApps.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = topPad).enterFrom(enter, 24f, 0f, 0f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickApps.take(if (appsOpen) 6 else 3).forEach { app ->
                    Image(
                        bitmap = app.icon.asImageBitmap(), contentDescription = app.label,
                        modifier = Modifier.size(btn).clip(CircleShape)
                            .clickable(role = Role.Button) { PanelQuickApps.launch(context, app.pkg); leave(onCollapse) }
                    )
                }
                if (quickApps.size > 3) {
                    CubeCircle(btn, cdSide, { appsOpen = !appsOpen }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = CubeInk, modifier = Modifier.size(btn * 0.55f).graphicsLayer { rotationZ = if (appsOpen) 180f else 0f })
                    }
                }
            }
        }

        // ── اليسار: بطاقات (وضع القائمة/الشبكة)
        if (leftOpen) {
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = bodyTop, bottom = 12.dp).width(listW)
                    .swallowTaps().enterFrom(enter, -80f, 0f, 0.05f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewToggle(listView, tint, cdList, cdGrid, { listView = true }, { listView = false })
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        Triple("1", lPerf, Icons.Rounded.RocketLaunch),
                        Triple("2", lBal, Icons.Rounded.Speed),
                        Triple("3", lSave, Icons.Rounded.BatteryChargingFull)
                    )
                    modes.forEach { (id, name, icon) ->
                        AssistCard(icon, name, !controls.auto && controls.profile == id, tint, !listView) {
                            if (controls.auto) say(msgAuto) else if (controls.profile != id) onSelectProfile(id)
                        }
                    }
                    InfoCard { FrameGraph(history = frames, accent = tint) }
                    if (details) {
                        InfoCard { PanelReadings(reading = reading, fields = fields) }
                        InfoCard { GamePanelSession(tally = tally) }
                    }
                }
            }
        }

        // ── عمودا CPU / GPU
        CubeGauge(
            value = clocks.cpuMhz?.let { "%.2f".format(Locale.US, it / 1000f) } ?: MAX_VALUE_UNAVAILABLE,
            unit = "GHz", label = "CPU", tint = tint, pill = modeName, onPill = { cycleProfile() },
            meter = brightness, meterIcon = Icons.Rounded.WbSunny, meterAlpha = if (brightnessAllowed) 1f else 0.45f,
            onMeter = { value ->
                if (brightnessAllowed) {
                    slide(brightness, value) { device.setBrightness(it) }
                    brightness = value
                } else if (android.os.SystemClock.uptimeMillis() - touchedAt > 1500) {
                    device.openWriteSettings()
                    touchedAt = android.os.SystemClock.uptimeMillis()
                }
            },
            outerLeft = true, boxW = gaugeW, u = u,
            modifier = Modifier.align(Alignment.TopStart).offset(x = w * cpuX - gaugeW / 2, y = (v * 19f).dp)
                .enterFrom(enter, -60f, 0f, 0.1f)
        )
        CubeGauge(
            value = clocks.gpuMhz?.toString() ?: MAX_VALUE_UNAVAILABLE,
            unit = "MHz", label = "GPU", tint = tint, pill = if (dnd) lDnd else lNormal,
            onPill = { flip(lDnd, dnd, { dnd = it }) { PanelToggles.setDnd(it) } },
            meter = volume, meterIcon = Icons.AutoMirrored.Rounded.VolumeUp, meterAlpha = 1f,
            onMeter = { value -> slide(volume, value) { device.setVolume(it) }; volume = value },
            outerLeft = false, boxW = gaugeW, u = u,
            modifier = Modifier.align(Alignment.TopStart).offset(x = w * gpuX - gaugeW / 2, y = (v * 19f).dp)
                .enterFrom(enter, 60f, 0f, 0.1f)
        )

        // ── وسط: رسائل نتيجة البلاطات
        Column(
            Modifier.align(Alignment.Center).offset(y = (v * 8f).dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            notes.forEach { note ->
                Text(
                    note, color = CubeInk, fontSize = (u * 3.2f).coerceAtLeast(12f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(MaxRadius.chip)).background(CubeGlass).padding(horizontal = MaxSpace.md, vertical = 4.dp)
                )
            }
        }

        // ── أسفل الوسط: اللعبة · الوقت · البطارية · الشبكة
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = (v * 2.6f).dp).clip(RoundedCornerShape(MaxRadius.row))
                .background(CubeGlass).padding(horizontal = MaxSpace.rowPaddingHorizontal, vertical = 5.dp).swallowTaps()
                .enterFrom(enter, 0f, 20f, 0.3f),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = CubeInk, fontSize = (u * 2.6f).coerceAtLeast(11f).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(minOf((u * 30f).dp, 150.dp)))
            StatusBit(null, status.time)
            StatusBit(Icons.Rounded.BatteryChargingFull, batteryText(status.batteryPct, status.charging))
            StatusBit(Icons.Rounded.Language, CockpitModel.speedText(status.netBytes))
        }

        // ── اليمين: شبكة البلاطات (كلها تكتب فعلًا)
        val bypassOn = controls.bypass == BypassState.On
        val tiles = buildList {
            add(CubeTileSpec(Icons.Rounded.Wifi, null, lWifi, wifi) { flip(lWifi, wifi, { wifi = it }) { PanelToggles.setWifi(it) } })
            if (hasCellular) add(CubeTileSpec(Icons.Rounded.Language, null, lData, mobile) { flip(lData, mobile, { mobile = it }) { PanelToggles.setData(it) } })
            add(CubeTileSpec(null, refreshRateHz?.toString() ?: "Hz", refreshRateHz?.let { "$it Hz" } ?: lDefault, refreshRateHz != null, onCycleRefresh))
            add(CubeTileSpec(Icons.Rounded.Info, null, lInfo, details) { details = !details; if (details) leftOpen = true })
            add(CubeTileSpec(if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, null, if (recording) lRecording else lRecord, recording, onToggleRecording))
            add(CubeTileSpec(Icons.Rounded.Videocam, null, if (screenRec) lScreenOn else lScreen, screenRec) {
                hold()
                if (screenRec) {
                    screenRec = false
                    say(msgSaved)
                    PanelToggles.async { PanelScreenRecorder.stop() }
                } else {
                    screenRec = true
                    say(String.format(fmtOn, lScreen))
                    PanelToggles.async { PanelScreenRecorder.start() }
                    scope.launch { delay(900); leave(onCollapse) }
                }
            })
            add(CubeTileSpec(if (rotationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation, null, lRotation, rotationLocked) {
                rotationLocked = !rotationLocked
                device.setRotationLock(rotationLocked)
            })
            add(CubeTileSpec(Icons.Rounded.RocketLaunch, null, if (optimizing) "…" else lOptimize, optimizing) {
                if (!optimizing) {
                    optimizing = true
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { runCatching { device.cleanMemory() }.getOrDefault(0) }
                        optimizing = false
                        say(String.format(fmtFreed, freed))
                    }
                }
            })
            if (touchAvailable) add(CubeTileSpec(Icons.Rounded.TouchApp, null, lTouch, touch) { flip(lTouch, touch, { touch = it }) { PanelToggles.setTouch(it) } })
            if (controls.bypass != BypassState.Unsupported) add(CubeTileSpec(Icons.Rounded.BatteryChargingFull, null, lBypass, bypassOn, onToggleBypass))
            add(CubeTileSpec(Icons.Rounded.Timer, null, if (reminderMin > 0) "${reminderMin}m" else lReminder, reminderMin > 0) {
                hold()
                val minutes = PanelReminder.cycle(context, msgDone)
                reminderMin = minutes
                say(if (minutes > 0) String.format(fmtReminder, minutes) else String.format(fmtOff, lReminder))
            })
            add(CubeTileSpec(Icons.Rounded.Tune, null, lApp, false) { onOpenControls(); leave(onCollapse) })
            add(CubeTileSpec(Icons.Rounded.PowerSettingsNew, null, lClose, false) { leave(onClose) })
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = bodyTop, end = 10.dp, bottom = 8.dp).width(gridW)
                .swallowTaps().enterFrom(enter, 60f, 0f, 0.1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy((v * 1.4f).dp)
        ) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { CubeTileView(it, tile, colW, tint, u) }
                }
            }
        }
    }
}

private fun batteryText(pct: Int?, charging: Boolean): String =
    (pct?.let { "$it%" } ?: "--") + if (charging) " ⚡" else ""

private fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

private fun Modifier.enterFrom(enter: Enter, dx: Float, dy: Float, delay: Float): Modifier = graphicsLayer {
    val p = ((enter.value - delay) / (1f - delay)).coerceAtLeast(0f)
    alpha = p.coerceIn(0f, 1f)
    translationX = (1f - p) * dx * density
    translationY = (1f - p) * dy * density
}

/** موجة صدمة من موضع المقبض: حلقات متتابعة تتّسع وتخبو، ووميض شعاعي عند نقطة الانطلاق. */
private fun DrawScope.cubeWave(origin: Offset, t: Float, tint: Color) {
    if (t <= 0f || t >= 1f) return
    val c = Offset(origin.x * size.width, origin.y * size.height)
    val reach = hypot(size.width, size.height)
    for (k in 0..2) {
        val p = ((t - k * 0.11f) / (1f - k * 0.11f)).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) continue
        val r = reach * (1f - (1f - p) * (1f - p))
        val fade = (1f - p) * (1f - p)
        drawCircle(tint.copy(alpha = fade * (0.10f - k * 0.02f)), r, c, style = Stroke((26f - k * 6f).dp.toPx() * (1f - p) + 2.dp.toPx()))
        drawCircle(tint.copy(alpha = fade * (0.55f - k * 0.14f)), r, c, style = Stroke((2.4f - k * 0.4f).dp.toPx()))
    }
    val flash = (1f - t / 0.4f).coerceIn(0f, 1f)
    if (flash > 0f) {
        val fr = size.height * 0.7f
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.5f * flash), Color.Transparent), center = c, radius = fr), fr, c)
    }
}

/** عمود مائل من 16 مقطعًا: يضيء من القاع بقدر `lit`، أفتح قرب القمة وأعمق عند القاع. */
private fun DrawScope.cubeBar(left: Boolean, lit: Float, tint: Color, sweep: Float) {
    val w = size.width
    val h = size.height
    fun m(x: Float) = (if (left) x else 1f - x) * w
    val x0 = m(0.285f)
    val x1 = m(0.395f)
    val x2 = m(0.185f)
    val yTip = 0.31f * h
    val half = 0.03f * w
    val gap = 0.008f * h
    fun xAt(y: Float) = if (y <= yTip) x0 + (x1 - x0) * (y / yTip) else x1 + (x2 - x1) * ((y - yTip) / (h - yTip))
    val s = sweep.coerceIn(0f, 1f)
    val litBlocks = lit.coerceIn(0f, 1f) * BAR_BLOCKS * s
    val down = BAR_BLOCKS - BAR_BLOCKS_UP
    for (i in 0 until BAR_BLOCKS) {
        val top: Float
        val bottom: Float
        if (i < BAR_BLOCKS_UP) {
            val s = yTip / BAR_BLOCKS_UP
            top = i * s; bottom = (i + 1) * s
        } else {
            val s = (h - yTip) / down
            top = yTip + (i - BAR_BLOCKS_UP) * s; bottom = yTip + (i - BAR_BLOCKS_UP + 1) * s
        }
        val a = top + gap / 2f
        val b = bottom - gap / 2f
        val path = Path().apply {
            moveTo(xAt(a) - half, a); lineTo(xAt(a) + half, a)
            lineTo(xAt(b) + half, b); lineTo(xAt(b) - half, b); close()
        }
        val j = BAR_BLOCKS - 1 - i
        val on = (litBlocks - j).coerceIn(0f, 1f)
        if (on > 0f) {
            val base = lerp(lerp(tint, Color.Black, 0.22f), lerp(tint, Color.White, 0.38f), j / (BAR_BLOCKS - 1f))
            val front = s < 0.995f && j == (litBlocks - 0.001f).toInt()
            val c = if (front) lerp(base, Color.White, 0.7f) else base
            drawPath(path, c.copy(alpha = (if (front) 0.5f else 0.22f) * on), style = Stroke(width = (if (front) 12f else 7f).dp.toPx()))
            drawPath(path, c.copy(alpha = on))
        } else {
            drawPath(path, CubeBlockOff)
            drawPath(path, Color.White.copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun CubeGauge(
    value: String, unit: String, label: String, tint: Color,
    pill: String, onPill: () -> Unit,
    meter: Float, meterIcon: ImageVector, meterAlpha: Float, onMeter: (Float) -> Unit,
    outerLeft: Boolean, boxW: Dp, u: Float, modifier: Modifier
) {
    Column(modifier.width(boxW), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, color = CubeInk, fontSize = (u * 5.6f).coerceAtLeast(22f).sp, lineHeight = (u * 6.4f).coerceAtLeast(26f).sp,
            maxLines = 1, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic
        )
        Box(Modifier.fillMaxWidth().height((u * 11f).dp), contentAlignment = Alignment.BottomCenter) {
            CubeOrb(tint, Modifier.size(boxW * 0.86f, (u * 9.2f).dp))
            Text(unit, color = CubeDim, fontSize = (u * 2.5f).coerceAtLeast(10f).sp, modifier = Modifier.align(Alignment.TopCenter))
        }
        Text(
            label, color = tint, fontSize = (u * 2.8f).coerceAtLeast(11f).sp,
            fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic
        )
        Spacer(Modifier.height((u * 2.2f).dp))
        CubePill(pill, tint, u, onPill)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = CubeDim, modifier = Modifier.size((u * 3.4f).coerceAtLeast(14f).dp))
        Spacer(Modifier.height((u * 2.2f).dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = boxW * 0.2f).graphicsLayer { alpha = meterAlpha },
            contentAlignment = if (outerLeft) Alignment.CenterStart else Alignment.CenterEnd
        ) { CubeMeter(meter, meterIcon, outerLeft, onMeter, u, Modifier) }
    }
}

/** قرص متوهّج: إهليلجات متداخلة بتوهّج شعاعي وأقواس تدور. */
@Composable
private fun CubeOrb(tint: Color, modifier: Modifier) {
    val spin by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "orbSpin"
    )
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawOval(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.40f), Color.Transparent), center = Offset(cx, cy), radius = size.width / 2f),
            topLeft = Offset.Zero, size = size
        )
        for (ring in 0..2) {
            val f = 1f - ring * 0.2f
            val rw = size.width * f
            val rh = size.height * f
            val topLeft = Offset(cx - rw / 2f, cy - rh / 2f)
            drawOval(tint.copy(alpha = 0.30f - ring * 0.07f), topLeft, Size(rw, rh), style = Stroke(1.dp.toPx()))
            drawArc(
                tint.copy(alpha = 0.95f - ring * 0.2f), spin * (if (ring % 2 == 0) 1f else -1f) + ring * 70f, 90f, false,
                topLeft, Size(rw, rh), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun CubePill(text: String, tint: Color, u: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.width((u * 24f).coerceIn(92f, 150f).dp).height(maxOf(30f, u * 5.4f).dp)
            .clip(PillShape)
            .background(Brush.verticalGradient(listOf(lerp(tint, Color.Black, 0.55f), lerp(tint, Color.Black, 0.82f))))
            .border(1.5.dp, tint, PillShape)
            .clickable(role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = CubeInk, fontSize = (u * 3.0f).coerceAtLeast(11f).sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = MaxSpace.rowPaddingHorizontal)
        )
    }
}

/** مقياس مقاطع يتّسع نحو الأعلى؛ الحافة الخارجية مستقيمة. السحب محلي (لا إعادة تركيب للّوحة) والكتابة عند تبدّل المقطع فقط. */
@Composable
private fun CubeMeter(
    value: Float, icon: ImageVector, startAnchored: Boolean, onChange: (Float) -> Unit, u: Float, modifier: Modifier
) {
    val change by rememberUpdatedState(onChange)
    var live by remember { mutableFloatStateOf(value) }
    var grabbed by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!grabbed) live = value }
    val shown = animateFloatAsState(live, spring(dampingRatio = 1f, stiffness = 900f), label = "meter")
    Column(
        modifier, horizontalAlignment = if (startAnchored) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            Modifier.width(maxOf(48f, u * 11f).dp).height(maxOf(84f, u * 16.7f).dp).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    grabbed = true
                    var segment = -1
                    fun push(y: Float) {
                        val v = (1f - y / size.height).coerceIn(0f, 1f)
                        live = v
                        val seg = CockpitModel.segmentOf(v, METER_BARS)
                        if (seg != segment) {
                            segment = seg
                            change(v)
                        }
                    }
                    push(down.position.y)
                    down.consume()
                    while (true) {
                        val move = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!move.pressed) break
                        push(move.position.y)
                        move.consume()
                    }
                    grabbed = false
                }
            }
        ) {
            val bar = size.height / (2f * METER_BARS - 1f)
            val level = shown.value.coerceIn(0f, 1f) * METER_BARS
            val full = level.toInt()
            val frac = level - full
            for (k in 0 until METER_BARS) {
                val bw = size.width * (1f - 0.65f * k / (METER_BARS - 1f))
                val x = if (startAnchored) 0f else size.width - bw
                val m2 = METER_BARS - 1 - k
                val a2 = if (m2 < full) 0.95f else if (m2 == full) 0.22f + 0.73f * frac else 0.22f
                drawRect(Color.White.copy(alpha = a2), Offset(x, k * 2f * bar), Size(bw, bar))
            }
        }
        Icon(icon, contentDescription = null, tint = CubeDim, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CubeCircle(
    size: Dp, description: String, onClick: () -> Unit,
    active: Boolean = false, tint: Color = Color.Transparent, content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (active) lerp(tint, Color.Black, 0.5f) else CubeTile)
            .clickable(role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** شعار المكعّب: سداسيّ بثلاثة أضلاع داخلية (منظور متساوي القياس). */
@Composable
private fun CubeLogo(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        fun p(deg: Float) = Offset(
            c.x + r * kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat(),
            c.y + r * kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat()
        )
        val hex = Path().apply {
            moveTo(p(-90f).x, p(-90f).y)
            listOf(-30f, 30f, 90f, 150f, 210f).forEach { lineTo(p(it).x, p(it).y) }
            close()
        }
        val stroke = Stroke(2.dp.toPx())
        drawPath(hex, CubeInk, style = stroke)
        listOf(-30f, 90f, 210f).forEach { drawLine(CubeInk, c, p(it), 2.dp.toPx()) }
    }
}

@Composable
private fun StatusBit(icon: ImageVector?, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = CubeDim, modifier = Modifier.size(13.dp))
        Text(text, color = CubeInk, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ViewToggle(
    listView: Boolean, tint: Color, cdList: String, cdGrid: String, onList: () -> Unit, onGrid: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(MaxRadius.chip)).background(CubeTile)) {
        listOf(true to Icons.Rounded.Menu, false to Icons.Rounded.Apps).forEach { (isList, icon) ->
            Box(
                Modifier.weight(1f).height(34.dp)
                    .background(if (listView == isList) lerp(tint, Color.Black, 0.5f) else Color.Transparent)
                    .clickable(role = Role.RadioButton) { if (isList) onList() else onGrid() }
                    .semantics { contentDescription = if (isList) cdList else cdGrid },
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = CubeInk, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun AssistCard(icon: ImageVector, title: String, active: Boolean, tint: Color, grid: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(MaxRadius.control)
    val fill = if (active) Brush.verticalGradient(listOf(lerp(tint, Color.Black, 0.35f), lerp(tint, Color.Black, 0.6f))) else SolidColor(CubeTile)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(fill, shape)
            .clickable(role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(MaxSpace.sm)
    ) {
        val disc: @Composable () -> Unit = {
            Box(Modifier.size(30.dp).clip(CircleShape).background(CubeGlass), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = CubeInk, modifier = Modifier.size(18.dp))
            }
        }
        if (grid) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                disc()
                Text(title, color = CubeInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                disc()
                Text(title, color = CubeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(MaxRadius.control)).background(CubeTile).padding(MaxSpace.sm)) { content() }
}

@Composable
private fun CubeTileView(spec: CubeTileSpec, tile: Dp, colW: Dp, tint: Color, u: Float) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(MaxRadius.control)
    val fill = if (spec.active) Brush.verticalGradient(listOf(lerp(tint, Color.Black, 0.35f), lerp(tint, Color.Black, 0.6f))) else SolidColor(CubeTile)
    Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.size(tile).clip(shape).background(fill, shape)
                .clickable(role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); spec.onClick() }
                .semantics { contentDescription = spec.label },
            contentAlignment = Alignment.Center
        ) {
            if (spec.icon != null) {
                Icon(spec.icon, null, tint = if (spec.active) Color.White else Color(0xFFD0D3D8), modifier = Modifier.size(tile * 0.5f))
            } else {
                Text(
                    spec.text ?: "", color = if (spec.active) Color.White else Color(0xFFD0D3D8),
                    fontSize = (u * 3.6f).coerceIn(13f, 20f).sp, fontWeight = FontWeight.Black, maxLines = 1
                )
            }
        }
        Text(spec.label, color = CubeInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
