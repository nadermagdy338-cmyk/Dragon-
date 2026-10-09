package nd.max.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.ui.overlay.HandleFx
import nd.max.core.gamespace.GamePanelMode
import nd.max.core.gamespace.BypassState
import nd.max.core.gamespace.PanelClocks
import nd.max.core.gamespace.PanelControlState
import nd.max.core.gamespace.PanelSide
import nd.max.core.gamespace.ThermalPanelState
import nd.max.core.platform.HudField
import nd.max.core.platform.HudReading
import nd.max.core.platform.HudTally
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace

private val PANEL_MAX_WIDTH = 640.dp
private val PANEL_MAX_HEIGHT = 420.dp
private val TOOLS_WIDTH = 108.dp
private val TILE_HEIGHT = 64.dp
internal val PanelEdge = 1.dp
private val PanelTouch = 48.dp
private val RING_MIN = 64.dp
private val PanelSegment = 40.dp
private val PanelGraph = 40.dp
private val FIXED_CHROME = 250.dp
private const val HEAT_WARN_C = 42f
private val RING_MAX = 136.dp
private const val RING_TICKS = 28
private const val RING_SPAN = 270f
private const val FPS_REFERENCE_HZ = 120

internal val PanelInk = Color(0xFFF1F3F5)
internal val PanelMuted = Color(0xFF98A2AA)
private val PanelTop = Color(0xF2171A1F)
private val PanelBottom = Color(0xF20B0D10)
private val PanelTrack = Color(0x33FFFFFF)
internal val PanelTile = Color(0x14FFFFFF)
internal val PanelDanger = Color(0xFFFF4D5E)

/**
 * لوحة اللعبة: مقبض صغير مطويّ، ولوحة قيادة مفتوحة (حلقتا إطارات/معالج + قياسات + ثلاث أدوات
 * تعمل فعلًا). كل رقم من `HudSampler` الحقيقي، ولا أداة لا تعمل (لا لقطة، لا DND وهميّ).
 * المحتوى LTR دائمًا لأن الموضع فيزيائي (`PanelSide`) لا يتبع اتجاه اللغة.
 */
@Composable
fun GamePanelSurface(
    mode: GamePanelMode,
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
    onOpen: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onCycleRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenControls: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onToggleBypass: () -> Unit,
    thermal: ThermalPanelState = ThermalPanelState(),
    onSelectThermal: (String) -> Unit = {},
    handleFx: HandleFx? = null,
    origin: Offset = Offset(1f, 0.5f)
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        when (mode) {
            GamePanelMode.Hidden -> Unit
            GamePanelMode.Handle -> {
                val hot = (reading?.heat ?: 0f) >= HEAT_WARN_C
                GamePanelHandle(
                    side = side,
                    gameLabel = gameLabel,
                    accent = if (hot) PanelDanger else accent,
                    hot = hot,
                    fx = handleFx,
                    onOpen = onOpen
                )
            }
            GamePanelMode.Open -> GamePanelCockpit(
                gameLabel = gameLabel,
                reading = reading,
                tally = tally,
                fields = fields,
                accent = accent,
                side = side,
                recording = recording,
                refreshRateHz = refreshRateHz,
                clocks = clocks,
                controls = controls,
                frames = frames,
                onCollapse = onCollapse,
                onClose = onClose,
                onCycleRefresh = onCycleRefresh,
                onToggleRecording = onToggleRecording,
                onOpenControls = onOpenControls,
                onSelectProfile = onSelectProfile,
                onToggleBypass = onToggleBypass,
                thermal = thermal,
                onSelectThermal = onSelectThermal,
                origin = origin
            )
        }
    }
}

/** زوايا مستديرة من جهة الداخل فقط؛ الحافّة الملاصقة للشاشة مستقيمة. */
private fun panelShape(side: PanelSide, radius: Dp) = if (side == PanelSide.End) {
    AbsoluteRoundedCornerShape(topLeft = radius, bottomLeft = radius, topRight = 0.dp, bottomRight = 0.dp)
} else {
    AbsoluteRoundedCornerShape(topLeft = 0.dp, bottomLeft = 0.dp, topRight = radius, bottomRight = radius)
}

/** اسم اللعبة المقروء بدل اسم الحزمة؛ إن لم يُقرأ يبقى اسم الحزمة. */
@Composable
internal fun rememberGameTitle(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        @Suppress("DEPRECATION")
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
    }
}

@Composable
internal fun PanelHeader(title: String, accent: Color, side: PanelSide, onCollapse: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
        Icon(
            imageVector = Icons.Rounded.Gamepad,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(MaxSpace.xl)
        )
        Text(
            text = title,
            color = PanelInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        PanelButton(
            icon = if (side == PanelSide.End) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
            description = stringResource(R.string.game_panel_collapse_cd),
            onClick = onCollapse
        )
        PanelButton(
            icon = Icons.Rounded.Close,
            description = stringResource(R.string.game_panel_close_cd),
            tint = PanelDanger,
            onClick = onClose
        )
    }
}

/** قياسات ثانوية بحسب الحقول التي اختارها المستخدم في طبقة FPS؛ كل واحدة من قراءة فعلية. */
@Composable
internal fun PanelReadings(reading: HudReading?, fields: List<HudField>) {
    val items = buildList {
        if (HudField.Cpu in fields) {
            add(stringResource(R.string.game_panel_chip_load) to (reading?.cpu?.let { "$it%" } ?: MAX_VALUE_UNAVAILABLE))
        }
        if (HudField.Ram in fields) {
            add(stringResource(R.string.game_panel_chip_ram) to (reading?.ramMb?.let(::ramText) ?: MAX_VALUE_UNAVAILABLE))
        }
        if (HudField.Power in fields) {
            add(stringResource(R.string.game_panel_chip_power) to (reading?.watt?.let { "%.1f W".format(it) } ?: MAX_VALUE_UNAVAILABLE))
        }
        if (HudField.Heat in fields) {
            add(stringResource(R.string.game_panel_chip_heat) to (reading?.heat?.let { "%.0f°C".format(it) } ?: MAX_VALUE_UNAVAILABLE))
        }
        if (HudField.Renderer in fields) {
            add(stringResource(R.string.game_panel_chip_renderer) to (reading?.renderer ?: MAX_VALUE_UNAVAILABLE))
        }
    }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
        items.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(PanelTile, RoundedCornerShape(MaxRadius.control))
                    .padding(vertical = MaxSpace.xs, horizontal = MaxSpace.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = label, color = PanelMuted, fontSize = 9.sp, maxLines = 1)
                Text(
                    text = value,
                    color = PanelInk,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun fraction(now: Int?, ceiling: Int?): Float? =
    if (now == null || ceiling == null || ceiling <= 0) null else (now.toFloat() / ceiling).coerceIn(0f, 1f)

private fun ramText(mb: Int): String = if (mb >= 1024) "%.1f GB".format(mb / 1024f) else "$mb MB"

@Composable
internal fun GamePanelSession(tally: HudTally?) {
    if (tally == null || tally.isEmpty) {
        Text(text = stringResource(R.string.game_panel_no_session), color = PanelMuted, fontSize = 10.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
        PanelStat(stringResource(R.string.game_panel_stat_samples), tally.samples.toString())
        PanelStat(stringResource(R.string.game_panel_stat_average), tally.framesAverage?.let { "%.0f".format(it) } ?: MAX_VALUE_UNAVAILABLE)
        PanelStat(stringResource(R.string.game_panel_stat_low), tally.framesLow?.let { "%.0f".format(it) } ?: MAX_VALUE_UNAVAILABLE)
        PanelStat(stringResource(R.string.game_panel_stat_heat), tally.heatPeak?.let { "%.0f°C".format(it) } ?: MAX_VALUE_UNAVAILABLE)
    }
    if (tally.droppedSamples > 0) {
        Text(text = stringResource(R.string.game_panel_dropped, tally.droppedSamples), color = PanelMuted, fontSize = 9.sp)
    }
}

@Composable
private fun PanelStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
        Text(text = label, color = PanelMuted, fontSize = 9.sp, maxLines = 1)
        Text(
            text = value,
            color = PanelInk,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun PanelButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = PanelInk) {
    Box(
        modifier = Modifier
            .size(PanelTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(MaxSpace.xl))
    }
}

/** ثلاثة أوضاع أداء بنقرة؛ في وضع الذكاء تُعرض الشارة فقط لأن الذكاء هو المالك. */
@Composable
internal fun ProfileStrip(selected: String?, auto: Boolean, accent: Color, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(MaxRadius.control)
    val modes = listOf(
        "1" to R.string.profile_performance,
        "2" to R.string.profile_balanced,
        "3" to R.string.profile_powersave
    )
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelTile, shape).padding(MaxSpace.hairline),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
    ) {
        if (auto) {
            Box(Modifier.weight(1f).height(PanelSegment), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.str_auto_mode), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            return@Row
        }
        modes.forEach { (id, label) ->
            val on = id == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PanelSegment)
                    .clip(shape)
                    .background(if (on) accent.copy(alpha = 0.28f) else Color.Transparent, shape)
                    .clickable(role = Role.RadioButton, enabled = !on) { onSelect(id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(label),
                    color = if (on) PanelInk else PanelMuted,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** منحنى الإطارات الحيّ من تاريخ `HudSampler`؛ أقل من نقطتين ⇒ انتظار، لا خط مرسوم من العدم. */
@Composable
internal fun FrameGraph(history: List<Float>, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelGraph)
            .background(PanelTile, RoundedCornerShape(MaxRadius.control))
            .padding(MaxSpace.xs),
        contentAlignment = Alignment.Center
    ) {
        if (history.size < 2) {
            Text(text = stringResource(R.string.game_panel_graph_empty), color = PanelMuted, fontSize = 10.sp)
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) {
            val top = maxOf(history.max(), 30f) * 1.1f
            val dx = this.size.width / (history.size - 1)
            val line = Path()
            val fill = Path()
            history.forEachIndexed { i, v ->
                val x = i * dx
                val y = this.size.height * (1f - (v / top).coerceIn(0f, 1f))
                if (i == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, this.size.height)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(this.size.width, this.size.height)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
