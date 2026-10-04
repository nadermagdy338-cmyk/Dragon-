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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import nd.max.core.gamespace.PanelSide
import nd.max.core.platform.HudField
import nd.max.core.platform.HudReading
import nd.max.core.platform.HudTally
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxRadius

private const val GAUGE_SEGMENTS = 16 // زوجيّ: رأس الشيفرون يقع على حدّ مقطعين فيبقى كلّ مقطع مستقيمًا
private const val SLIDER_SEGMENTS = 16
private const val HOT_CPU = 90
private const val HOT_HEAT_C = 42f
private const val ENTER_MS = 560
private const val LEAVE_MS = 240
private val DrawerWidth = 176.dp
private val TileSize = 72.dp
private val GaugeUnlit = Color(0x1FFFFFFF)
private val CockpitScrim = Color(0xE60A0A0C)
private val CockpitInk = Color(0xFFF1F3F5)

private typealias Enter = Animatable<Float, AnimationVector1D>

/**
 * لوحة اللعبة المفتوحة بملء الشاشة بنمط «مكعّب الطاقة»: شيفرونان مقطّعان (CPU يسارًا · GPU يمينًا)
 * وبينهما FPS، ودرج أيسر (ملفّ الأداء · منحنى الإطارات · إحصاءات الجلسة)، وبلاطات أدوات يمينًا،
 * ومنزلقان مقطّعان للسطوع والصوت **يكتبان في النظام فعلًا**. الدخول والخروج متحرّكان، والخطر
 * (CPU ≥ 90% أو حرارة ≥ 42°م) يصبغ الشيفرون والبلاطات. المحتوى LTR دائمًا (الموضع فيزيائي).
 */
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
    onToggleBypass: () -> Unit
) {
    val context = LocalContext.current
    val device = remember { PanelDevice(context) }
    val haptic = LocalHapticFeedback.current
    var brightness by remember { mutableStateOf(device.brightness()) }
    var volume by remember { mutableStateOf(device.volume()) }
    var touchedAt by remember { mutableStateOf(0L) }
    var activeAt by remember { mutableStateOf(android.os.SystemClock.uptimeMillis()) }
    val brightnessAllowed = remember { device.canSetBrightness() }
    var rotationLocked by remember { mutableStateOf(device.rotationLocked()) }
    var status by remember { mutableStateOf(device.statusLine()) }
    var cleanNote by remember { mutableStateOf<String?>(null) }
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(ENTER_MS, easing = FastOutSlowInEasing)) }
    // مزامنة حيّة: مفاتيح الصوت الفيزيائية والإعدادات السريعة تنعكس هنا ما لم يكن المستخدم يسحب.
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            if (android.os.SystemClock.uptimeMillis() - touchedAt > 1200) {
                brightness = device.brightness()
                volume = device.volume()
            }
            rotationLocked = device.rotationLocked()
            status = device.statusLine()
        }
    }
    // الخروج متحرّك ثم يُبلَّغ المالك (فتُصغَّر النافذة بعد انتهاء الحركة لا قبلها).
    fun leave(after: () -> Unit) {
        if (leaving) return
        leaving = true
        scope.launch {
            enter.animateTo(0f, tween(LEAVE_MS))
            after()
        }
    }
    // كتابة النظام عند تغيّر المقطع فقط: لا عاصفة أوامر جذر أثناء السحب، ونبضة لمس لكل مقطع.
    fun slide(old: Float, new: Float, write: (Float) -> Unit) {
        touchedAt = android.os.SystemClock.uptimeMillis()
        if (CockpitModel.segmentOf(new, SLIDER_SEGMENTS) != CockpitModel.segmentOf(old, SLIDER_SEGMENTS)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            write(new)
        }
    }

    // خمول: لوحة ملء الشاشة منسيّة تحجب اللعبة، فتُطوى وحدها بعد مدّة بلا لمس.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (CockpitModel.shouldCollapseIdle(android.os.SystemClock.uptimeMillis(), activeAt)) {
                leave(onCollapse)
                break
            }
        }
    }

    val heat = reading?.heat ?: 0f
    val cpuHot = (reading?.cpu ?: 0) >= HOT_CPU || heat >= HOT_HEAT_C
    val cpuTint = if (cpuHot) PanelDanger else accent
    val gpuTint = if (heat >= HOT_HEAT_C) PanelDanger else accent
    val cpuFraction by animateFloatAsState(fraction(clocks.cpuMhz, clocks.cpuCeilingMhz) ?: 0f, tween(450), label = "cpuFraction")
    val gpuFraction by animateFloatAsState(fraction(clocks.gpuMhz, clocks.gpuCeilingMhz) ?: 0f, tween(450), label = "gpuFraction")
    val fps = reading?.frames
    val title = rememberGameTitle(gameLabel)
    var dragged = 0f

    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(CockpitScrim)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    activeAt = android.os.SystemClock.uptimeMillis()
                }
            }
            // لمسة على مساحة فارغة أو سحب رأسيّ يطوي اللوحة (النافذة ملء الشاشة فلا بدّ من مخرج سريع).
            .pointerInput(Unit) { detectTapGestures { leave(onCollapse) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, delta -> dragged += delta },
                    onDragEnd = { if (kotlin.math.abs(dragged) > 160f) leave(onCollapse) }
                )
            }
    ) {
        val compact = CockpitModel.isCompact(maxWidth.value)
        var drawerOpen by remember(compact) { mutableStateOf(!compact) }
        val tileSize = if (compact) 56.dp else TileSize
        val columns = if (compact) 1 else 2
        val toolsWidth = tileSize * columns + 8.dp * (columns - 1) + 24.dp
        val leftEdge = if (drawerOpen) DrawerWidth + 12.dp else 12.dp

        Canvas(Modifier.fillMaxSize()) {
            val sweep = enter.value
            val shift = (1f - sweep) * size.width * 0.12f
            translate(left = -shift) { chevron(fromRight = false, lit = cpuFraction, tint = cpuTint, sweep = sweep) }
            translate(left = shift) { chevron(fromRight = true, lit = gpuFraction, tint = gpuTint, sweep = sweep) }
        }

        Box(Modifier.align(Alignment.TopStart).padding(12.dp).enterFrom(enter, -24f, 0f, 0f)) {
            PanelButton(Icons.Rounded.Menu, stringResource(R.string.game_panel_drawer_cd), { drawerOpen = !drawerOpen })
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp).widthIn(max = 460.dp).padding(horizontal = 56.dp).enterFrom(enter, 0f, -24f, 0f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PanelHeader(title = title, accent = accent, side = side, onCollapse = { leave(onCollapse) }, onClose = { leave(onClose) })
            Text(status, color = PanelMuted, fontSize = 12.sp)
        }

        Column(Modifier.align(Alignment.Center).enterFrom(enter, 0f, 24f, 0.15f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fps?.let { "%.0f".format(it) } ?: MAX_VALUE_UNAVAILABLE,
                color = CockpitInk, fontSize = 64.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic
            )
            Text(stringResource(R.string.game_panel_gauge_fps), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        GaugeReadout(
            value = clocks.cpuMhz?.let { "%.2f".format(it / 1000f) } ?: MAX_VALUE_UNAVAILABLE, unit = "GHz",
            label = stringResource(R.string.game_panel_gauge_cpu), tint = cpuTint,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = leftEdge).enterFrom(enter, -60f, 0f, 0.1f)
        )
        GaugeReadout(
            value = clocks.gpuMhz?.toString() ?: MAX_VALUE_UNAVAILABLE, unit = "MHz",
            label = stringResource(R.string.game_panel_gauge_gpu), tint = gpuTint,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = toolsWidth).enterFrom(enter, 60f, 0f, 0.1f)
        )

        SegmentedSlider(
            value = brightness, icon = Icons.Rounded.WbSunny, tint = accent,
            onChange = { v ->
                if (brightnessAllowed) {
                    slide(brightness, v) { device.setBrightness(it) }
                    brightness = v
                } else if (android.os.SystemClock.uptimeMillis() - touchedAt > 1500) {
                    device.openWriteSettings()
                    touchedAt = android.os.SystemClock.uptimeMillis()
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = leftEdge + 16.dp, bottom = 16.dp)
                .graphicsLayer { if (!brightnessAllowed) alpha = 0.45f }.enterFrom(enter, 0f, 60f, 0.25f)
        )
        SegmentedSlider(
            value = volume, icon = Icons.Rounded.GraphicEq, tint = accent,
            onChange = { v -> slide(volume, v) { device.setVolume(it) }; volume = v },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = toolsWidth + 16.dp, bottom = 16.dp).enterFrom(enter, 0f, 60f, 0.25f)
        )
        Text(
            stringResource(R.string.game_panel_dismiss_hint), color = PanelMuted.copy(alpha = 0.7f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).enterFrom(enter, 0f, 20f, 0.4f)
        )

        if (drawerOpen) {
            Column(
                Modifier.align(Alignment.CenterStart).width(DrawerWidth).padding(start = 12.dp, top = 64.dp, bottom = 12.dp)
                    .pointerInput(Unit) { detectTapGestures { } } // اللمس داخل الدرج لا يطوي اللوحة
                    .verticalScroll(rememberScrollState()).enterFrom(enter, -80f, 0f, 0.05f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStrip(selected = controls.profile, auto = controls.auto, accent = accent, onSelect = onSelectProfile)
                FrameGraph(history = frames, accent = accent)
                PanelReadings(reading = reading, fields = fields)
                GamePanelSession(tally = tally)
            }
        }

        val bypassOn = controls.bypass == BypassState.On
        val tiles = buildList {
            add(TileSpec(Icons.Rounded.Refresh, refreshRateHz?.let { "$it Hz" } ?: MAX_VALUE_UNAVAILABLE, refreshRateHz != null, accent, onCycleRefresh))
            add(TileSpec(
                if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                stringResource(if (recording) R.string.game_panel_tool_recording else R.string.game_panel_tool_record),
                recording, PanelDanger, onToggleRecording
            ))
            if (controls.bypass != BypassState.Unsupported) {
                add(TileSpec(Icons.Rounded.BatteryChargingFull, stringResource(R.string.game_panel_tool_bypass), bypassOn, accent, onToggleBypass))
            }
            add(TileSpec(
                if (rotationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                stringResource(R.string.game_panel_tool_rotation), rotationLocked, accent,
                { rotationLocked = !rotationLocked; device.setRotationLock(rotationLocked) }
            ))
            add(TileSpec(Icons.Rounded.CleaningServices, cleanNote ?: stringResource(R.string.game_panel_tool_clean), cleanNote != null, accent) {
                if (cleanNote == null) scope.launch {
                    cleanNote = "…"
                    val freed = withContext(Dispatchers.IO) { runCatching { device.cleanMemory() }.getOrDefault(0) }
                    cleanNote = "+$freed MB"
                    delay(3000)
                    cleanNote = null
                }
            })
            add(TileSpec(Icons.Rounded.Tune, stringResource(R.string.game_panel_tool_app), false, accent, onOpenControls))
        }
        TileGrid(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).heightIn(max = maxHeight - 72.dp),
            enter = enter, tiles = tiles, columns = columns, size = tileSize
        )
    }
}

@Composable
private fun TileGrid(modifier: Modifier, enter: Enter, tiles: List<TileSpec>, columns: Int, size: androidx.compose.ui.unit.Dp) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(columns).forEachIndexed { row, group ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEachIndexed { col, spec ->
                    // تتتابع البلاطات: كل واحدة تتأخّر عن التي قبلها قليلًا.
                    CockpitTile(spec, size, Modifier.enterFrom(enter, 60f, 0f, 0.1f + 0.08f * (row * columns + col)))
                }
            }
        }
    }
}

private class TileSpec(val icon: ImageVector, val label: String, val active: Boolean, val tint: Color, val onClick: () -> Unit)

@Composable
private fun CockpitTile(spec: TileSpec, tileSize: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(MaxRadius.row)
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.size(tileSize).clip(shape)
            .background(if (spec.active) spec.tint.copy(alpha = 0.55f) else PanelTile, shape)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                spec.onClick()
            }
            .semantics { contentDescription = spec.label }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(spec.icon, contentDescription = null, tint = CockpitInk, modifier = Modifier.size(if (tileSize < 64.dp) 22.dp else 28.dp))
        Text(spec.label, color = CockpitInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** رقم كبير مائل + وحدة + حلقات إهليلجية دوّارة + اسم المقياس. */
@Composable
private fun GaugeReadout(value: String, unit: String, label: String, tint: Color, modifier: Modifier) {
    val spinState = rememberInfiniteTransition(label = "gaugeSpin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "spin"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = CockpitInk, fontSize = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Text(unit, color = PanelMuted, fontSize = 12.sp)
        Canvas(Modifier.width(120.dp).height(36.dp)) {
            val spin = spinState.value
            for (ring in 0..2) {
                val inset = ring * 12.dp.toPx()
                val topLeft = Offset(inset, inset * 0.45f)
                val ringSize = Size(size.width - inset * 2, size.height - inset * 0.9f)
                drawArc(tint.copy(alpha = 0.85f - ring * 0.2f), spin * (if (ring % 2 == 0) 1 else -1) + ring * 40f, 110f, false, topLeft, ringSize, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawArc(tint.copy(alpha = 0.25f), 0f, 360f, false, topLeft, ringSize, style = Stroke(1.dp.toPx()))
            }
        }
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** منزلق عموديّ مقطّع: لمسة أو سحب يُصدران نسبة 0..1 (الأعلى = 1). */
@Composable
private fun SegmentedSlider(value: Float, icon: ImageVector, tint: Color, onChange: (Float) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier.width(64.dp).height(140.dp).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange((1f - down.position.y / size.height).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                        change.consume()
                    }
                }
            }
        ) {
            val gap = 3.dp.toPx()
            val bar = (size.height - gap * (SLIDER_SEGMENTS - 1)) / SLIDER_SEGMENTS
            val lit = (value * SLIDER_SEGMENTS).toInt()
            for (j in 0 until SLIDER_SEGMENTS) {
                val width = size.width * (0.45f + 0.55f * (j + 1f) / SLIDER_SEGMENTS) // أضيق عند القاع وأعرض نحو الأعلى
                drawRect(
                    if (j < lit) tint.copy(alpha = 0.5f + 0.5f * j / SLIDER_SEGMENTS) else GaugeUnlit,
                    Offset(size.width - width, size.height - (j + 1) * bar - j * gap), Size(width, bar)
                )
            }
        }
        Icon(icon, contentDescription = null, tint = PanelMuted, modifier = Modifier.size(16.dp))
    }
}

/** شيفرون «>» من [GAUGE_SEGMENTS] مقطعًا يُضاء من القاع بنسبة [lit]، وتُملأ المقاطع بالتتابع مع [sweep]. */
private fun DrawScope.chevron(fromRight: Boolean, lit: Float, tint: Color, sweep: Float) {
    val dir = if (fromRight) -1f else 1f
    val baseX = if (fromRight) size.width * 0.70f else size.width * 0.30f
    val bend = size.width * 0.13f * dir
    val stroke = size.width * 0.04f
    val litCount = lit.coerceIn(0f, 1f) * GAUGE_SEGMENTS * sweep
    fun point(t: Float) = Offset(baseX + bend * (1f - kotlin.math.abs(2f * t - 1f)), t * size.height)
    for (i in 0 until GAUGE_SEGMENTS) {
        val j = GAUGE_SEGMENTS - 1 - i
        val a = point(i.toFloat() / GAUGE_SEGMENTS + 0.012f)
        val b = point((i + 1f) / GAUGE_SEGMENTS - 0.012f)
        val on = (litCount - j).coerceIn(0f, 1f)
        if (on > 0f) {
            drawLine(tint.copy(alpha = 0.18f * on), a, b, stroke * 1.7f, StrokeCap.Butt)
            drawLine(tint.copy(alpha = (0.45f + 0.55f * j / GAUGE_SEGMENTS) * on), a, b, stroke, StrokeCap.Butt)
        } else {
            drawLine(GaugeUnlit, a, b, stroke, StrokeCap.Butt)
        }
    }
}

/** دخول متتابع: انزلاق من (dx,dy) dp مع ظهور تدريجيّ يبدأ بعد [delay] من زمن الدخول (0..1). */
private fun Modifier.enterFrom(enter: Enter, dx: Float, dy: Float, delay: Float): Modifier = graphicsLayer {
    val p = ((enter.value - delay) / (1f - delay)).coerceIn(0f, 1f)
    alpha = p
    translationX = (1f - p) * dx * density
    translationY = (1f - p) * dy * density
}

