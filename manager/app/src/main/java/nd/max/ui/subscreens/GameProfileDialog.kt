/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import nd.max.R
import nd.max.core.gamespace.GameApp
import nd.max.ui.component.AppIconImage
import nd.max.ui.component.LobbyPalette
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.AppConfig
import nd.max.ui.util.getSupportedDownscaleFactors
import nd.max.ui.util.getSupportedRefreshRates

/** أقسام الشريط الجانبي — ستة، بترتيب قائمة الملف الجانبية التي طلبها المالك. */
internal enum class GameProfileSection(val titleRes: Int, val headRes: Int, val icon: ImageVector) {
    Touch(R.string.lobby_section_touch, R.string.lobby_head_touch, Icons.Rounded.Tune),
    Performance(R.string.lobby_section_perf, R.string.lobby_head_perf, Icons.Rounded.Memory),
    Gpu(R.string.lobby_section_gpu, R.string.lobby_head_gpu, Icons.Rounded.DeveloperBoard),
    Show(R.string.lobby_section_show, R.string.lobby_head_show, Icons.Rounded.Visibility),
    Net(R.string.lobby_section_net, R.string.lobby_head_net, Icons.Rounded.Wifi),
    Function(R.string.lobby_section_function, R.string.lobby_head_function, Icons.Rounded.Apps)
}

private val SheetCorner = 28.dp
private val SidebarWidth = 236.dp
private val SidebarItemHeight = 62.dp
private val ChipRadius = 12.dp

/**
 * ملف اللعبة — **ورقة سفلية عرضية بشريط جانبي**، ومحتواها مرتبط بمفاتيح `AppConfig` الحقيقية فقط.
 *
 * ### ما يُعرض وما لا يُعرض
 *
 * لا مفتاح بلا مالك: كل صفّ هنا يكتب عبر [onUpdate] إلى `AppSettingsViewModel.updateSetting`
 * (المسار القائم الذي ينتهي عند `AppMonitor`) — **لا كتابة عتاد من هذه الشاشة** (ADR-11).
 * وما ليس له مفتاح في المخزن لا يُرسم: لا مؤشّرات حساسية/نعومة/ثبات وهمية، ولا تسجيل «حسب الطلب»،
 * لأنّ شريطًا يتحرّك ولا يُغيّر شيئًا كذبٌ بصريّ (ADR-08).
 *
 * ### والحدّ المعلن
 *
 * القيمة المحفوظة **نيّة**، ويطبّقها المراقب حين تصبح اللعبة أمامية؛ والنصّ في أسفل كل قسم يقول
 * ذلك (`gaming_runtime_boundary`).
 */
@Composable
internal fun GameProfileDialog(
    app: GameApp,
    config: AppConfig,
    panelEnabled: Boolean,
    onPanelEnabled: (Boolean) -> Unit,
    onUpdate: (key: String, value: String) -> Unit,
    onOpenFull: () -> Unit,
    onDismiss: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(GameProfileSection.Touch) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
                    .background(LobbyPalette.Surface)
                    // يبتلع اللمس حتى لا يُغلق الورقة من داخلها.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            ) {
                DialogHeader(app = app, onDismiss = onDismiss)
                Row(Modifier.fillMaxSize()) {
                    Sidebar(selected = section, onSelect = { section = it })
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = MaxSpace.lg, vertical = MaxSpace.md),
                        verticalArrangement = Arrangement.spacedBy(MaxSpace.md)
                    ) {
                        SectionCard(title = stringResource(section.headRes)) {
                            SectionBody(
                                section = section,
                                config = config,
                                panelEnabled = panelEnabled,
                                onPanelEnabled = onPanelEnabled,
                                onUpdate = onUpdate,
                                onOpenFull = onOpenFull
                            )
                        }
                        Text(
                            text = stringResource(R.string.gaming_runtime_boundary),
                            color = LobbyPalette.Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = MaxSpace.xs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(app: GameApp, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.xl, vertical = MaxSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        AppIconImage(packageName = app.packageName, size = 36.dp, contentDescription = null)
        Text(
            text = stringResource(R.string.lobby_settings_title, app.label),
            color = LobbyPalette.Ink,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val closeLabel = stringResource(R.string.lobby_close_settings)
        Box(
            modifier = Modifier
                .size(MaxSize.minTouchTarget)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Rounded.Close, contentDescription = closeLabel, tint = LobbyPalette.Ink)
        }
    }
}

@Composable
private fun Sidebar(selected: GameProfileSection, onSelect: (GameProfileSection) -> Unit) {
    Column(
        modifier = Modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .background(LobbyPalette.Panel)
            .verticalScroll(rememberScrollState())
    ) {
        GameProfileSection.entries.forEach { item ->
            val isSelected = item == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SidebarItemHeight)
                    .background(
                        if (isSelected) {
                            Brush.horizontalGradient(
                                listOf(LobbyPalette.PanelSelected, LobbyPalette.PanelSelected, LobbyPalette.Red.copy(alpha = 0.85f))
                            )
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item) })
                    .padding(horizontal = MaxSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (isSelected) LobbyPalette.Ink else LobbyPalette.Muted
                )
                Text(
                    text = stringResource(item.titleRes),
                    color = if (isSelected) LobbyPalette.Ink else LobbyPalette.Muted,
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaxRadius.group))
            .background(LobbyPalette.Panel)
            .padding(MaxSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)
    ) {
        Text(text = title, color = LobbyPalette.Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun SectionBody(
    section: GameProfileSection,
    config: AppConfig,
    panelEnabled: Boolean,
    onPanelEnabled: (Boolean) -> Unit,
    onUpdate: (String, String) -> Unit,
    onOpenFull: () -> Unit
) {
    val context = LocalContext.current
    val defaultLabel = stringResource(R.string.default_label)
    when (section) {
        GameProfileSection.Touch -> {
            OptionRow(
                title = stringResource(R.string.lobby_row_touch),
                options = listOf(
                    "default" to stringResource(R.string.lobby_touch_follow),
                    "true" to stringResource(R.string.lobby_touch_responsive),
                    "false" to stringResource(R.string.lobby_touch_battery)
                ),
                selected = config.touch_boost,
                onSelect = { onUpdate("touch_boost", it) }
            )
            TriStateRow(stringResource(R.string.lobby_row_haptics), config.haptic_feedback) { onUpdate("haptic_feedback", it) }
        }
        GameProfileSection.Performance -> {
            TriStateRow(stringResource(R.string.lobby_row_lite), config.perf_lite_mode) { onUpdate("perf_lite_mode", it) }
            TriStateRow(stringResource(R.string.lobby_row_cpuboost), config.cpu_boost) { onUpdate("cpu_boost", it) }
            TriStateRow(stringResource(R.string.lobby_row_killbg), config.kill_bg_apps) { onUpdate("kill_bg_apps", it) }
            TriStateRow(stringResource(R.string.lobby_row_preload), config.game_preload) { onUpdate("game_preload", it) }
        }
        GameProfileSection.Gpu -> {
            OptionRow(
                title = stringResource(R.string.lobby_row_gpu),
                options = listOf(
                    "default" to defaultLabel,
                    "power" to stringResource(R.string.lobby_gpu_power),
                    "balanced" to stringResource(R.string.lobby_gpu_balanced),
                    "gaming" to stringResource(R.string.lobby_gpu_gaming),
                    "performance" to stringResource(R.string.lobby_gpu_performance)
                ),
                selected = config.gpu_profile,
                onSelect = { onUpdate("gpu_profile", it) }
            )
            TriStateRow(stringResource(R.string.lobby_row_hwui), config.force_hw_ui) { onUpdate("force_hw_ui", it) }
        }
        GameProfileSection.Show -> {
            val refreshModes = remember { getSupportedRefreshRates(context) }
            OptionRow(
                title = stringResource(R.string.lobby_row_refresh),
                options = refreshModes.map { it to if (it.equals("default", ignoreCase = true)) defaultLabel else "${it}Hz" },
                selected = config.refresh_rate,
                onSelect = { onUpdate("refresh_rate", it) }
            )
            val downscale = remember { getSupportedDownscaleFactors() }
            OptionRow(
                title = stringResource(R.string.lobby_row_downscale),
                options = downscale.map { step ->
                    step to (step.toDoubleOrNull()?.let { "${(it * 100).roundToInt()}%" } ?: defaultLabel)
                },
                selected = config.resolution_downscale,
                onSelect = { onUpdate("resolution_downscale", it) }
            )
        }
        GameProfileSection.Net -> {
            TriStateRow(stringResource(R.string.lobby_row_wifi), config.wifi_no_sleep) { onUpdate("wifi_no_sleep", it) }
        }
        GameProfileSection.Function -> {
            SwitchRow(stringResource(R.string.lobby_row_panel), panelEnabled, onPanelEnabled)
            TriStateRow(stringResource(R.string.lobby_row_dnd), config.dnd_on_gaming) { onUpdate("dnd_on_gaming", it) }
            TriStateRow(stringResource(R.string.lobby_row_notifs), config.disable_notifs) { onUpdate("disable_notifs", it) }
            TriStateRow(stringResource(R.string.lobby_row_bypass), config.bypass_charging) { onUpdate("bypass_charging", it) }
            OutlinedButton(
                onClick = onOpenFull,
                border = BorderStroke(MaxSize.hairlineBorder, LobbyPalette.Hairline),
                modifier = Modifier.heightIn(min = MaxSize.minTouchTarget)
            ) {
                Text(text = stringResource(R.string.lobby_open_full), color = LobbyPalette.Ink)
            }
        }
    }
}

/** قيمة ثلاثية (الافتراضي/تشغيل/إيقاف) — نفس القيم النصّية `default|true|false` التي يقرؤها المراقب. */
@Composable
private fun TriStateRow(title: String, current: String, onSelect: (String) -> Unit) {
    OptionRow(
        title = title,
        options = listOf(
            "default" to stringResource(R.string.default_label),
            "true" to stringResource(R.string.lobby_state_on),
            "false" to stringResource(R.string.lobby_state_off)
        ),
        selected = current,
        onSelect = onSelect
    )
}

@Composable
private fun OptionRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {
        Text(
            text = title,
            color = LobbyPalette.Ink,
            fontSize = 16.sp,
            modifier = Modifier.weight(0.38f)
        )
        Row(
            modifier = Modifier
                .weight(0.62f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .heightIn(min = MaxSize.minTouchTarget)
                        .clip(RoundedCornerShape(ChipRadius))
                        .background(if (isSelected) LobbyPalette.PanelSelected else LobbyPalette.PanelRaised.copy(alpha = 0.55f))
                        .border(
                            MaxSize.hairlineBorder,
                            if (isSelected) LobbyPalette.Hairline else Color.Transparent,
                            RoundedCornerShape(ChipRadius)
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = MaxSpace.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) LobbyPalette.Ink else LobbyPalette.Muted,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, color = LobbyPalette.Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(MaxSpace.md))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LobbyPalette.Red,
                checkedThumbColor = LobbyPalette.Ink,
                uncheckedTrackColor = LobbyPalette.PanelRaised,
                uncheckedThumbColor = LobbyPalette.Muted
            )
        )
    }
}
