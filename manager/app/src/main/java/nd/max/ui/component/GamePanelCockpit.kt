/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Waves
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.gamespace.BypassState
import nd.max.core.gamespace.CockpitModel
import nd.max.core.gamespace.CubeLayoutMath
import nd.max.core.gamespace.GameThermalProfiles
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
import nd.max.core.gamespace.ThermalPanelState
import nd.max.core.gamespace.ThermalPhase
import nd.max.core.platform.HudField
import nd.max.core.platform.HudReading
import nd.max.core.platform.HudTally
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace

/*
 * لوحة اللعبة المفتوحة بتصميم «Energy Cube»، على هيئة المرجع:
 *
 *   • صفّ دوائر في الأعلى (رئيسية · تحرير · طيّ) وتطبيقات سريعة، يفصلهما عن الجسم خطّ.
 *   • يسارًا: بروفايلات الحرارة الستة (افتراضي · موفّر · متوازن · ألعاب · أداء · مخصّص) كبطاقات
 *     قائمة/شبكة — لمسة واحدة تبدّل فورًا، ويُرى التنفيذ الفعلي (جارٍ ← طُبِّق ‏٦٥٠ MHz).
 *   • عمودا CPU / GPU بأرقام كبيرة وأقراص متوهجة وحبّتين: وضع النظام (CPU) والإشعارات (GPU)،
 *     وكلتاهما تفتح قائمة منسدلة بدل دورة عمياء.
 *   • شريطان مائلان من ١٦ مقطعًا يضيئان بقدر التردد الحيّ، بينهما نافذة على اللعبة.
 *   • يمينًا: شبكة بلاطات (كلها تكتب فعلًا) بتمرير متلاشٍ، وأسفل الوسط: الحالة وسطر الرسائل.
 *
 * اللون يتبع وضع النظام: أحمر=أداء، كهرماني=متوازن، أخضر=توفير، أزرق=تلقائي.
 * وكل مقاس من [CubeLayoutMath] فلا تتراكب الأعمدة ولا تُقطع النصوص (انظر `CubeLayoutTest`).
 */
private const val LEAVE_MS = 240
private const val HOT_CPU = 90
private const val HOT_HEAT_C = 42f

private typealias Enter = Animatable<Float, AnimationVector1D>

private enum class CubeMenu { None, System, Notify, Thermal }

private fun modeAccent(controls: PanelControlState, fallback: Color): Color = when {
    controls.auto -> ModeAuto
    controls.profile == "1" -> ModeRed
    controls.profile == "2" -> ModeAmber
    controls.profile == "3" -> ModeGreen
    else -> fallback
}

private fun thermalIcon(id: String): ImageVector = when (id) {
    "power" -> Icons.Rounded.BatteryFull
    "balanced" -> Icons.Rounded.Waves
    "gaming" -> Icons.Rounded.SportsEsports
    "performance" -> Icons.Rounded.Bolt
    "custom" -> Icons.Rounded.Tune
    else -> Icons.Rounded.Restore
}

/** ألوان البروفايلات كما في شاشة الإعدادات (أخضر · أزرق · برتقالي · أحمر) ليتّفق ما يراه المستخدم. */
private fun thermalColor(id: String): Color = when (id) {
    "power" -> Color(0xFF36C77B)
    "balanced" -> Color(0xFF3D8EFF)
    "gaming" -> Color(0xFFFF9800)
    "performance" -> Color(0xFFFF4444)
    "custom" -> Color(0xFFB07CFF)
    else -> Color(0xFFB4B8BF)
}

private class CubeProbe(
    val wifi: Boolean, val mobile: Boolean, val dnd: Boolean,
    val touch: Boolean, val rec: Boolean, val minutes: Int
)

private fun batteryText(pct: Int?, charging: Boolean): String =
    (pct?.let { "$it%" } ?: "--") + if (charging) " ⚡" else ""

private fun Modifier.enterFrom(enter: Enter, dx: Float, dy: Float, delay: Float): Modifier = graphicsLayer {
    val p = ((enter.value - delay) / (1f - delay)).coerceAtLeast(0f)
    alpha = p.coerceIn(0f, 1f)
    translationX = (1f - p) * dx * density
    translationY = (1f - p) * dy * density
}

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
    thermal: ThermalPanelState = ThermalPanelState(),
    onSelectThermal: (String) -> Unit = {},
    origin: Offset = Offset(1f, 0.5f)
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
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
    var menu by remember { mutableStateOf(CubeMenu.None) }
    var cpuPill by remember { mutableStateOf(Rect.Zero) }
    var gpuPill by remember { mutableStateOf(Rect.Zero) }
    var thermalTile by remember { mutableStateOf(Rect.Zero) }
    // الحلقات الطويلة تقرأ آخر حالة لا لقطة لحظة إطلاقها.
    val thermalNow by rememberUpdatedState(thermal)
    val leftScroll = rememberScrollState()
    val gridScroll = rememberScrollState()
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
    val lHint = stringResource(R.string.game_panel_dismiss_hint)
    val lThermalTitle = stringResource(R.string.game_panel_thermal_title)
    val lThermalTile = stringResource(R.string.game_panel_thermal_tile)
    val lModeTitle = stringResource(R.string.game_panel_mode_title)
    val lNotifyTitle = stringResource(R.string.game_panel_notify_title)
    val sNoOverride = stringResource(R.string.game_panel_thermal_default_sub)
    val sApplying = stringResource(R.string.game_panel_thermal_applying)
    val sPending = stringResource(R.string.game_panel_thermal_pending)
    val sNotApplied = stringResource(R.string.game_panel_thermal_failed_sub)
    val fmtThermalApplied = stringResource(R.string.game_panel_thermal_msg_applied)
    val msgThermalPending = stringResource(R.string.game_panel_thermal_msg_pending)
    val fmtThermalFailed = stringResource(R.string.game_panel_thermal_msg_failed)
    val rUnsupported = stringResource(R.string.game_panel_thermal_reason_unsupported)
    val rHeld = stringResource(R.string.game_panel_thermal_reason_held)
    val rSave = stringResource(R.string.game_panel_thermal_reason_save)
    val rGeneric = stringResource(R.string.game_panel_thermal_reason_generic)
    val thermalLabels = mapOf(
        "default" to stringResource(R.string.default_label),
        "power" to stringResource(R.string.profile_label_power),
        "balanced" to stringResource(R.string.Profile_Balanced),
        "gaming" to stringResource(R.string.profile_label_gaming),
        "performance" to stringResource(R.string.Profile_Performance),
        "custom" to stringResource(R.string.profile_label_custom)
    )

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
            // قائمة مفتوحة أو تنفيذ جارٍ = المستخدم يعمل؛ لا طيّ تحت يده.
            if (menu == CubeMenu.None && thermalNow.phase != ThermalPhase.Applying &&
                CockpitModel.shouldCollapseIdle(android.os.SystemClock.uptimeMillis(), activeAt)
            ) {
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

    fun reasonText(reason: String): String = when {
        reason == "save-failed" -> rSave
        reason.contains("no-gpu-provider") || reason.contains("not-writable") || reason.contains("unsupported") -> rUnsupported
        reason.contains("held") || reason.contains("suppress") || reason.contains("blocked") || reason.contains("lock") -> rHeld
        else -> rGeneric
    }

    // نتيجة تبديل البروفايل تُقال مرّة عند وصولها (لا كل إعادة تركيب).
    LaunchedEffect(thermal.phase, thermal.requestedAt) {
        val name = thermalLabels[thermal.selected] ?: thermal.selected
        when (thermal.phase) {
            ThermalPhase.Applied -> say(
                String.format(fmtThermalApplied, name) + (thermal.liveMhz?.let { " · $it MHz" } ?: "")
            )
            ThermalPhase.Pending -> say(msgThermalPending)
            ThermalPhase.Failed -> say(String.format(fmtThermalFailed, reasonText(thermal.reason)))
            else -> Unit
        }
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

    // بطاقات/خيارات بروفايل الحرارة: الاسم، وتحته التردد المتوقّع أو حالة التنفيذ للمختار.
    val thermalOptions = GameThermalProfiles.ids.map { id ->
        val chosen = id == thermal.selected
        val sub = when {
            chosen && thermal.phase == ThermalPhase.Applying -> sApplying
            chosen && thermal.phase == ThermalPhase.Pending -> sPending
            chosen && thermal.phase == ThermalPhase.Failed -> sNotApplied
            chosen && thermal.phase == ThermalPhase.Applied && thermal.liveMhz != null -> "${thermal.liveMhz} MHz"
            id == GameThermalProfiles.DEFAULT -> sNoOverride
            else -> thermal.targetsMhz[id]?.let { "$it MHz" } ?: ""
        }
        CubeOption(
            id = id, label = thermalLabels[id] ?: id, sub = sub,
            icon = thermalIcon(id), color = thermalColor(id),
            subColor = if (chosen && thermal.phase == ThermalPhase.Failed) ModeRed else CubeDim
        )
    }
    val thermalBusy = if (thermal.phase == ThermalPhase.Applying) thermal.selected else null
    fun pickThermal(id: String) {
        if (id == thermal.selected && thermal.phase != ThermalPhase.Failed && thermal.phase != ThermalPhase.Pending) return
        onSelectThermal(id)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
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
        val compact = CockpitModel.isCompact(w.value)
        var leftOpen by remember(compact) { mutableStateOf(!compact) }
        val layout = remember(w.value, h.value, leftOpen, details) {
            CubeLayoutMath.of(w.value, h.value, leftOpen, details)
        }
        val bottomPad = (h.value - layout.bodyBottom).coerceAtLeast(0f).dp

        Canvas(Modifier.fillMaxSize()) {
            cubeScrims(enter.value)
            cubeRules(layout.edge.dp.toPx(), layout.dividerY.dp.toPx(), enter.value)
        }
        Canvas(Modifier.fillMaxSize()) { cubeWave(origin, wave.value, tint) }
        Canvas(Modifier.fillMaxSize()) {
            val sweep = enter.value
            cubeBar(left = true, lit = cpuFraction, tint = tint, sweep = sweep)
            cubeBar(left = false, lit = gpuFraction, tint = tint, sweep = sweep)
        }

        // ── أعلى اليسار: الرئيسية / تحرير القائمة / شعار المكعّب (طيّ)
        Row(
            Modifier.align(Alignment.TopStart).padding(start = layout.edge.dp, top = layout.topPad.dp).enterFrom(enter, -24f, 0f, 0f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CubeCircle(layout.btn.dp, lApp, { onOpenControls(); leave(onCollapse) }) {
                Icon(Icons.Rounded.Home, null, tint = CubeInk, modifier = Modifier.size((layout.btn * 0.5f).dp))
            }
            CubeCircle(layout.btn.dp, cdSide, { leftOpen = !leftOpen }, active = leftOpen, tint = tint) {
                Icon(Icons.Rounded.Edit, null, tint = CubeInk, modifier = Modifier.size((layout.btn * 0.5f).dp))
            }
            CubeCircle(layout.btn.dp, cdCollapse, { leave(onCollapse) }) { CubeLogo(Modifier.size((layout.btn * 0.54f).dp)) }
        }

        // ── أعلى الوسط: إطارات/شبكة/بطارية/مدّة اللعب
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = (layout.topPad + (layout.btn - 28f) / 2f).coerceAtLeast(layout.topPad).dp)
                .clip(RoundedCornerShape(MaxRadius.row))
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
                Modifier.align(Alignment.TopEnd).padding(end = layout.edge.dp, top = layout.topPad.dp).enterFrom(enter, 24f, 0f, 0f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickApps.take(if (appsOpen) 6 else 3).forEach { app ->
                    Image(
                        bitmap = app.icon.asImageBitmap(), contentDescription = app.label,
                        modifier = Modifier.size(layout.btn.dp).clip(CircleShape)
                            .clickable(role = Role.Button) { PanelQuickApps.launch(context, app.pkg); leave(onCollapse) }
                    )
                }
                if (quickApps.size > 3) {
                    CubeCircle(layout.btn.dp, cdSide, { appsOpen = !appsOpen }) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown, null, tint = CubeInk,
                            modifier = Modifier.size((layout.btn * 0.55f).dp).graphicsLayer { rotationZ = if (appsOpen) 180f else 0f }
                        )
                    }
                }
            }
        }

        // ── اليسار: بروفايلات الحرارة (قائمة/شبكة) ثم بطاقات المعلومات
        if (leftOpen) {
            Column(
                Modifier.align(Alignment.TopStart)
                    .padding(start = layout.edge.dp, top = layout.bodyTop.dp, bottom = bottomPad)
                    .width(layout.leftW.dp).fillMaxHeight()
                    .swallowTaps().enterFrom(enter, -80f, 0f, 0.05f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ViewToggle(listView, tint, layout.toggleH.dp, cdList, cdGrid, { listView = true }, { listView = false })
                SectionCaption(Icons.Rounded.Thermostat, lThermalTitle)
                FadeScroll(Modifier.weight(1f), leftScroll, 6.dp) {
                    if (listView) {
                        thermalOptions.forEach { option ->
                            OptionRow(
                                option = option, selected = option.id == thermal.selected, busy = option.id == thermalBusy,
                                tint = tint, height = layout.cardH.dp, card = true, onClick = { pickThermal(option.id) }
                            )
                        }
                    } else {
                        val cell = ((layout.leftW - 6f) / 2f)
                        thermalOptions.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { option ->
                                    CubeTileView(
                                        spec = CubeTileSpec(
                                            icon = option.icon, text = null, label = option.label,
                                            active = option.id == thermal.selected, iconTint = option.color,
                                            onClick = { pickThermal(option.id) }
                                        ),
                                        tile = (cell - 10f).dp, colW = cell.dp, tint = tint, labelSp = 10f
                                    )
                                }
                            }
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
            unit = "GHz", label = "CPU", tint = tint, pill = modeName, pillBusy = false, pillOpen = menu == CubeMenu.System,
            onPill = { menu = if (menu == CubeMenu.System) CubeMenu.None else CubeMenu.System },
            onPillBounds = { cpuPill = it },
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
            outerLeft = true, layout = layout,
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = (layout.cpuX - layout.gaugeW / 2f).dp, y = layout.gaugeTop.dp)
                .enterFrom(enter, -60f, 0f, 0.1f)
        )
        CubeGauge(
            value = clocks.gpuMhz?.toString() ?: MAX_VALUE_UNAVAILABLE,
            unit = "MHz", label = "GPU", tint = tint, pill = if (dnd) lDnd else lNormal, pillBusy = false,
            pillOpen = menu == CubeMenu.Notify,
            onPill = { menu = if (menu == CubeMenu.Notify) CubeMenu.None else CubeMenu.Notify },
            onPillBounds = { gpuPill = it },
            meter = volume, meterIcon = Icons.AutoMirrored.Rounded.VolumeUp, meterAlpha = 1f,
            onMeter = { value -> slide(volume, value) { device.setVolume(it) }; volume = value },
            outerLeft = false, layout = layout,
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = (layout.gpuX - layout.gaugeW / 2f).dp, y = layout.gaugeTop.dp)
                .enterFrom(enter, 60f, 0f, 0.1f)
        )

        // ── أسفل الوسط: اللعبة · الوقت · البطارية · الشبكة، وتحتها سطر الحالة/الرسائل
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad).enterFrom(enter, 0f, 20f, 0.3f),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(MaxRadius.row))
                    .background(CubeGlass).padding(horizontal = MaxSpace.rowPaddingHorizontal, vertical = 5.dp).swallowTaps(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                FitText(
                    title, CubeInk, maxSp = 12f, minSp = 9f, weight = FontWeight.Bold, align = TextAlign.Left,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                StatusBit(null, status.time)
                StatusBit(Icons.Rounded.BatteryChargingFull, batteryText(status.batteryPct, status.charging))
                StatusBit(Icons.Rounded.Language, CockpitModel.speedText(status.netBytes))
            }
            Box(
                Modifier.width(layout.statusW.dp).height(22.dp).clip(CircleShape).background(CubeGlass).swallowTaps(),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = notes.lastOrNull() ?: lHint, label = "status") { text ->
                    FitText(text, CubeDim, maxSp = 11f, minSp = 8f, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }

        // ── اليمين: شبكة البلاطات (كلها تكتب فعلًا)
        val bypassOn = controls.bypass == BypassState.On
        val tiles = buildList {
            add(CubeTileSpec(Icons.Rounded.Wifi, null, lWifi, wifi, onClick = { flip(lWifi, wifi, { wifi = it }) { PanelToggles.setWifi(it) } }))
            if (hasCellular) add(CubeTileSpec(Icons.Rounded.Language, null, lData, mobile, onClick = { flip(lData, mobile, { mobile = it }) { PanelToggles.setData(it) } }))
            add(CubeTileSpec(null, refreshRateHz?.toString() ?: "Hz", refreshRateHz?.let { "$it Hz" } ?: lDefault, refreshRateHz != null, onClick = onCycleRefresh))
            add(CubeTileSpec(Icons.Rounded.DoNotDisturbOn, null, lDnd, dnd, onClick = { flip(lDnd, dnd, { dnd = it }) { PanelToggles.setDnd(it) } }))
            if (!leftOpen) {
                add(CubeTileSpec(
                    thermalIcon(thermal.selected), null, thermalLabels[thermal.selected] ?: lThermalTile,
                    thermal.selected != GameThermalProfiles.DEFAULT, onBounds = { thermalTile = it },
                    onClick = { menu = if (menu == CubeMenu.Thermal) CubeMenu.None else CubeMenu.Thermal }
                ))
            }
            add(CubeTileSpec(Icons.Rounded.Info, null, lInfo, details, onClick = { details = !details; if (details) leftOpen = true }))
            add(CubeTileSpec(if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, null, if (recording) lRecording else lRecord, recording, onClick = onToggleRecording))
            add(CubeTileSpec(Icons.Rounded.Videocam, null, if (screenRec) lScreenOn else lScreen, screenRec, onClick = {
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
            }))
            add(CubeTileSpec(if (rotationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation, null, lRotation, rotationLocked, onClick = {
                rotationLocked = !rotationLocked
                device.setRotationLock(rotationLocked)
            }))
            add(CubeTileSpec(Icons.Rounded.RocketLaunch, null, if (optimizing) "…" else lOptimize, optimizing, onClick = {
                if (!optimizing) {
                    optimizing = true
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { runCatching { device.cleanMemory() }.getOrDefault(0) }
                        optimizing = false
                        say(String.format(fmtFreed, freed))
                    }
                }
            }))
            if (touchAvailable) add(CubeTileSpec(Icons.Rounded.TouchApp, null, lTouch, touch, onClick = { flip(lTouch, touch, { touch = it }) { PanelToggles.setTouch(it) } }))
            if (controls.bypass != BypassState.Unsupported) add(CubeTileSpec(Icons.Rounded.BatteryChargingFull, null, lBypass, bypassOn, onClick = onToggleBypass))
            add(CubeTileSpec(Icons.Rounded.Timer, null, if (reminderMin > 0) "${reminderMin}m" else lReminder, reminderMin > 0, onClick = {
                hold()
                val minutes = PanelReminder.cycle(context, msgDone)
                reminderMin = minutes
                say(if (minutes > 0) String.format(fmtReminder, minutes) else String.format(fmtOff, lReminder))
            }))
            add(CubeTileSpec(Icons.Rounded.PowerSettingsNew, null, lClose, false, onClick = { leave(onClose) }))
        }
        FadeScroll(
            Modifier.align(Alignment.TopEnd)
                .padding(top = layout.bodyTop.dp, end = layout.edge.dp, bottom = bottomPad)
                .width(layout.gridW.dp).fillMaxHeight()
                .swallowTaps().enterFrom(enter, 60f, 0f, 0.1f),
            gridScroll, layout.rowGap.dp
        ) {
            tiles.chunked(layout.columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(layout.colGap.dp)) {
                    row.forEach { CubeTileView(it, layout.tile.dp, layout.colW.dp, tint, 11f) }
                }
            }
        }

        // ── القوائم المنسدلة: آخر ما يُرسم، فتعلو كل شيء ولمسة خارجها تغلقها وحدها
        when (menu) {
            CubeMenu.System -> CubeDropdown(
                title = lModeTitle,
                options = buildList {
                    if (controls.auto) add(CubeOption("auto", lAuto, "", Icons.Rounded.AutoAwesome, ModeAuto, enabled = false))
                    add(CubeOption("1", lPerf, "", Icons.Rounded.RocketLaunch, ModeRed))
                    add(CubeOption("2", lBal, "", Icons.Rounded.Speed, ModeAmber))
                    add(CubeOption("3", lSave, "", Icons.Rounded.BatteryChargingFull, ModeGreen))
                },
                selectedId = if (controls.auto) "auto" else controls.profile ?: "",
                busyId = null, tint = tint, anchor = cpuPill, screenW = w, screenH = h,
                topLimit = layout.bodyTop.dp, density = density,
                onSelect = { id ->
                    menu = CubeMenu.None
                    if (controls.auto) say(msgAuto) else if (controls.profile != id) onSelectProfile(id)
                },
                onDismiss = { menu = CubeMenu.None }
            )
            CubeMenu.Notify -> CubeDropdown(
                title = lNotifyTitle,
                options = listOf(
                    CubeOption("normal", lNormal, "", Icons.Rounded.Visibility, ModeGreen),
                    CubeOption("dnd", lDnd, "", Icons.Rounded.VisibilityOff, ModeRed)
                ),
                selectedId = if (dnd) "dnd" else "normal", busyId = null, tint = tint,
                anchor = gpuPill, screenW = w, screenH = h, topLimit = layout.bodyTop.dp, density = density,
                onSelect = { id ->
                    menu = CubeMenu.None
                    val want = id == "dnd"
                    if (want != dnd) flip(lDnd, dnd, { dnd = it }) { PanelToggles.setDnd(it) }
                },
                onDismiss = { menu = CubeMenu.None }
            )
            CubeMenu.Thermal -> CubeDropdown(
                title = lThermalTitle, options = thermalOptions, selectedId = thermal.selected, busyId = thermalBusy,
                tint = tint, anchor = thermalTile, screenW = w, screenH = h, topLimit = layout.bodyTop.dp, density = density,
                onSelect = { id -> menu = CubeMenu.None; pickThermal(id) },
                onDismiss = { menu = CubeMenu.None }
            )
            CubeMenu.None -> Unit
        }
    }
}
