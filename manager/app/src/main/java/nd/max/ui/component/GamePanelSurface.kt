/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.gamespace.GamePanelMode
import nd.max.core.gamespace.GamePanelTile
import nd.max.core.gamespace.GamePanelTileState
import nd.max.core.gamespace.PanelSide
import nd.max.core.gamespace.gamePanelTileState
import nd.max.core.platform.HudArrangement
import nd.max.core.platform.HudField
import nd.max.core.platform.HudForm
import nd.max.core.platform.HudReading
import nd.max.core.platform.HudTally
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE

/**
 * لوحة الألعاب الجانبية — **مقبضٌ حين تُطوى، ولوحةٌ حين تُفتح، ولا شيء غير ذلك**.
 *
 * ### لماذا ليست [HudSurface]
 *
 * `HudSurface` لوحة أرقام **مصقولة** بحسب الشكل المختار (شريط · رقاقة · حلقة · لوح)، ومنطق
 * اللوحة الجانبية ليس شكلًا من أشكالها: لها **مقبض** على الحافة يبقى حين لا شيء يُعرض، وطيّ
 * وفتح، ورأس يعرّف اللعبة. فحشو هذه الحالة في `HudSurface` كان سيُنتج شكلًا خامسًا لا يختاره
 * المستخدم، وهو نفس ما تمنعه طبقة التصميم. وهذه اللوحة **تستعمل [HudSurface] داخلها** حين
 * تُفتح، فمصيِّر الأرقام واحد في الموضعين.
 *
 * ### والوعد الذي تقوله ولا تخفيه
 *
 * المطويّة **مقبضٌ بعرضِ [HANDLE_WIDTH]** على الحافة: لا تغطّي اللعبة. والمفتوحة تأخذ
 * [PANEL_WIDTH] من العرض. والفرق مكتوب في `GameLobbyScreen` صراحةً، فلا يُوعد بوعدٍ يكذّبه
 * أوّل فتح.
 *
 * ### وحدود معلنة
 *
 * المقبض أقلّ من أرضية اللمس (٤٨dp) **على هذا السطح وحده** — نفس سابقة `HudActionSize` في
 * `HudSurface`: اللوحة تسكن فوق لعبة ومساحتها من مساحتها، والمقبض يُفتح بلمسة، والطي متاح من
 * اللوحة نفسها ومن إشعار الخدمة. ولا يُنقل الرقم إلى شاشة داخل التطبيق.
 */
private val HANDLE_WIDTH = 18.dp
private val PANEL_WIDTH = 292.dp

/** ألوان السطح: داكنة دائمًا — اللوحة معاينة لنافذة فوق لعبة، فتبييضها في ثيم فاتح كذب. */
private val PanelInk = Color(0xFFE9EEF2)
private val PanelMuted = Color(0xFF9AA6AE)
private val PanelBackdrop = Color(0xE6101317)
private val PanelDanger = Color(0xFFFF5A5A)

/**
 * @param reading آخر قراءة من القاسم المشترك، و`null` قبل أوّل قراءة (شرطة لا صفر).
 * @param tally نتيجة الجلسة المسجَّلة — `null` يعني «لا جلسة»، لا «جلسة بصفر».
 * @param side الحافة التي تلتصق بها — تقلب المقبض وتقلب اتجاه السهم.
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
    onOpen: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onCycleRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenControls: () -> Unit
) {
    when (mode) {
        GamePanelMode.Hidden -> Unit
        GamePanelMode.Handle -> GamePanelHandle(side = side, gameLabel = gameLabel, onOpen = onOpen)
        GamePanelMode.Open -> GamePanelBody(
            gameLabel = gameLabel,
            reading = reading,
            tally = tally,
            fields = fields,
            accent = accent,
            side = side,
            recording = recording,
            refreshRateHz = refreshRateHz,
            onCollapse = onCollapse,
            onClose = onClose,
            onCycleRefresh = onCycleRefresh,
            onToggleRecording = onToggleRecording,
            onOpenControls = onOpenControls
        )
    }
}

/**
 * المقبض: شريط رقيق ملتصق بالحافة، عليه سهم يفتح وأيقونة لعبة.
 *
 * **وهو قابل للفتح بلمسة واحدة** لأنّه `clickable` بدور `Button`، ونصّه الصوتيّ يقول ما سيحدث
 * لا ما هو مرسوم («فتح لوحة الألعاب») ومعه اسم اللعبة — وهو الفرق بين رمز يُقرأ عشوائيًّا
 * وإجراء يُفهم. **ولا نصّ مرئيّ فيه:** عمود بعرض ١٨dp لا يحمل كلمة إلّا مقصوصة.
 */
@Composable
private fun GamePanelHandle(side: PanelSide, gameLabel: String, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.game_panel_open_cd)
    Column(
        modifier = Modifier
            .width(HANDLE_WIDTH)
            .height(120.dp)
            .background(PanelBackdrop, RoundedCornerShape(MaxRadius.chip))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "$openLabel · $gameLabel" }
            .padding(vertical = MaxSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = if (side == PanelSide.End) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = PanelInk,
            modifier = Modifier.size(MaxSpace.lg)
        )
        Icon(
            imageVector = Icons.Rounded.Gamepad,
            contentDescription = null,
            tint = PanelMuted,
            modifier = Modifier.size(MaxSpace.md)
        )
    }
}

/**
 * اللوحة المفتوحة: رأس فيه اسم اللعبة وزرّا الطيّ والإغلاق، ثم سطح الأرقام القائم، ثم الجلسة.
 *
 * **والطيّ والإغلاق مفصولان** كما في `HudActions` بالحرف: الأوّل يعيد إلى المقبض، والثاني
 * يُنزل الإشعار ويُنهي الخدمة. وزرّ واحد اسمه «إغلاق» ويُخفي هو ما يجعل المستخدم يظنّ التراكب
 * انتهى وهو يعمل.
 */
@Composable
private fun GamePanelBody(
    gameLabel: String,
    reading: HudReading?,
    tally: HudTally?,
    fields: List<HudField>,
    accent: Color,
    side: PanelSide,
    recording: Boolean,
    refreshRateHz: Int?,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onCycleRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenControls: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = PANEL_WIDTH)
            .background(PanelBackdrop, RoundedCornerShape(MaxRadius.group))
            .padding(MaxSpace.md),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
            Icon(
                imageVector = Icons.Rounded.Gamepad,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(MaxSpace.lg)
            )
            Text(
                text = gameLabel,
                color = PanelInk,
                fontWeight = FontWeight.SemiBold,
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
        HorizontalDivider(color = PanelMuted.copy(alpha = 0.25f))
        // مصيِّر الأرقام واحد: نفس ما يُعرض في التراكب العائم وفي المعاينة داخل الشاشة.
        HudSurface(
            form = HudForm.Badge,
            arrangement = HudArrangement.Stack,
            reading = reading,
            fields = fields,
            accent = accent,
            textSizeSp = 14f,
            backgroundAlpha = 0.85f,
            framesHistory = emptyList(),
            showGraph = false,
            recording = recording
        )
        GamePanelTools(
            refreshRateHz = refreshRateHz,
            recording = recording,
            onCycleRefresh = onCycleRefresh,
            onToggleRecording = onToggleRecording,
            onOpenControls = onOpenControls
        )
        GamePanelSession(tally = tally)
        Text(
            text = stringResource(R.string.game_panel_covers_note),
            color = PanelMuted,
            fontSize = 9.sp
        )
    }
}

/**
 * سطر الجلسة: ما قِيس فعلًا، وما سقط بالسقف.
 *
 * **والأرقام الغائبة شرطة لا صفرًا** — `HudTally` يحمل `null` لما لم يُقَس (متوسّط بلا عيّنات
 * ليس صفرًا)، وتحويلها إلى `0` كان سيقول «قِسناه فكان صفرًا».
 */
@Composable
private fun GamePanelSession(tally: HudTally?) {
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
    // ما سقط بالسقف **يُقال** ولا يُسكَت عنه، وإلّا قُرئ المدى بدايةَ الجلسة.
    if (tally.droppedSamples > 0) {
        Text(
            text = stringResource(R.string.game_panel_dropped, tally.droppedSamples),
            color = PanelMuted,
            fontSize = 9.sp
        )
    }
}

/**
 * أدوات اللوحة: معدّل التحديث · تسجيل · عدم الإزعاج · لقطة.
 *
 * **وكل زرّ يذهب إلى مالكه:** معدّل التحديث يُرسَل إلى `RefreshRateReceiver` (مالكه القائم، وهو
 * الذي يكتب ذرّيًّا بقراءة نهائية)، والتسجيل إلى `HudRecorder`، وعدم الإزعاج **يُعلن أن مالكه
 * في `App Settings`** ويقود إليها بدل أن يكتب `zen_mode` عالميًّا من هنا، واللقطة **تُعلن أنها
 * غير متاحة بعد**. ولا سطر كتابة عتاد في هذه الواجهة (ADR-11).
 */
@Composable
private fun GamePanelTools(
    refreshRateHz: Int?,
    recording: Boolean,
    onCycleRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenControls: () -> Unit
) {
    val refreshState = gamePanelTileState(GamePanelTile.RefreshRate)
    val dndState = gamePanelTileState(GamePanelTile.DoNotDisturb)
    val captureState = gamePanelTileState(GamePanelTile.Capture)
    Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
        PanelTile(
            icon = Icons.Rounded.Refresh,
            // الرقم هو ما **فرضناه**، والشرطة حين لا فرض — لا «٦٠» كذبًا.
            value = refreshRateHz?.toString() ?: MAX_VALUE_UNAVAILABLE,
            label = stringResource(R.string.game_panel_tool_refresh),
            state = refreshState,
            enabled = true,
            onClick = onCycleRefresh
        )
        PanelTile(
            icon = if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
            value = stringResource(if (recording) R.string.game_panel_tool_recording else R.string.game_panel_tool_record),
            label = stringResource(R.string.game_panel_tool_session),
            state = gamePanelTileState(GamePanelTile.RecordSession),
            enabled = true,
            tint = if (recording) PanelDanger else PanelInk,
            onClick = onToggleRecording
        )
        PanelTile(
            icon = Icons.Rounded.DoNotDisturbOn,
            value = stringResource(R.string.game_panel_tool_elsewhere),
            label = stringResource(R.string.game_panel_tool_dnd),
            state = dndState,
            enabled = true,
            onClick = onOpenControls
        )
        PanelTile(
            icon = Icons.Rounded.ScreenshotMonitor,
            value = stringResource(R.string.game_panel_tool_soon),
            label = stringResource(R.string.game_panel_tool_capture),
            state = captureState,
            enabled = false,
            onClick = {}
        )
    }
}

/**
 * زرّ أداة واحد: أيقونة وسطر حالة.
 *
 * **والحالة تقول سببها:** `ControlledElsewhere` تعني «يعمل من `App Settings`»، و`NotAvailableYet`
 * تعني «لم يُنفَّذ بعد» — فلا زرّ صامت يُقرأ معطوبًا، ولا زرّ يدّعي عملًا لم يقع (ADR-08).
 */
@Composable
private fun PanelTile(
    icon: ImageVector,
    value: String,
    label: String,
    state: GamePanelTileState,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = PanelInk
) {
    val spoken = when (state) {
        GamePanelTileState.Ready -> label
        GamePanelTileState.ControlledElsewhere -> "$label · ${stringResource(R.string.game_panel_tool_elsewhere)}"
        GamePanelTileState.NotAvailableYet -> "$label · ${stringResource(R.string.game_panel_tool_soon)}"
    }
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(vertical = MaxSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else PanelMuted,
            modifier = Modifier.size(MaxSpace.lg)
        )
        Text(
            text = value,
            color = if (enabled) PanelInk else PanelMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = label, color = PanelMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun PanelButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = PanelInk
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(MaxSpace.lg))
    }
}
