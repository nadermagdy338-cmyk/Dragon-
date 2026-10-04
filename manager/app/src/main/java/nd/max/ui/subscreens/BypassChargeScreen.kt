/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0
 */

package nd.max.ui.subscreens
import nd.max.ui.design.MaxCardSpec
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSectionSpec
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import nd.max.ui.component.MaxSwitch
import nd.max.ui.component.MaxSlider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.topjohnwu.superuser.Shell
import nd.max.MaxManagerProps
import nd.max.R
import nd.max.ui.component.MaxUiMetrics
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.design.MaxListScreen
import nd.max.core.platform.PropertyUtils
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BypassChargeScreen(navController: NavHostController) {
    val colors = MaterialTheme.colorScheme

    var bypassPath by remember { mutableStateOf("") }
    var bypassEnabled by remember { mutableStateOf<Boolean?>(null) }
    var threshold by remember { mutableStateOf<Float?>(null) }

    val unsupported = bypassPath == "UNSUPPORTED"
    val needsSetup = bypassPath == "NEED_SETUP" || bypassPath.isBlank()
    val configured = !needsSetup && !unsupported

    fun writeBypassState(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        PropertyUtils.set(MaxManagerProps.Conf.BYPASS_CHARGE, value)
        Shell.cmd("echo $value > /data/adb/.config/MaxManager/bypasschgconfig/bypasschg").exec()
    }

    fun writeThreshold(value: Int) {
        PropertyUtils.set(MaxManagerProps.Conf.BYPASS_CHARGE_THRESHOLD, value.toString())
        Shell.cmd("echo $value > /data/adb/.config/MaxManager/bypasschgconfig/bypasschgthreshold").exec()
    }

    LaunchedEffect(Unit) {
        bypassPath = PropertyUtils.get(MaxManagerProps.Conf.BYPASS_PATH, "")
        bypassEnabled = PropertyUtils.get(MaxManagerProps.Conf.BYPASS_CHARGE, "0") == "1"
        threshold = PropertyUtils.get(MaxManagerProps.Conf.BYPASS_CHARGE_THRESHOLD, "20")
            .toFloatOrNull()?.coerceIn(20f, 50f) ?: 20f
    }

    ScreenAccentProvider(colors.tertiary) {
        MaxListScreen(
            title = stringResource(R.string.bcharging),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Filled.BatteryChargingFull,
            accent = colors.tertiary
        ) {
                item {
                    BypassHero(
                        enabled = bypassEnabled == true,
                        available = configured,
                        needsSetup = needsSetup,
                        unsupported = unsupported
                    )
                }

                item {
                    BypassIllustration(
                        modifier = Modifier.fillMaxWidth(),
                        active = bypassEnabled == true,
                        muted = unsupported
                    )
                }

                item {
                    SectionLabel(stringResource(R.string.bypass_charge_section_control))
                    // 7dp لم تكن على سلّم 4dp، والمكان هنا **بعده عنوان القسم** بعينه
                    // (`MaxSectionSpec.spaceAfter`) — وهو نفسه بين عنواني القسمين الآخرين.
                    Spacer(Modifier.height(MaxSectionSpec.spaceAfter))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                        shape = RoundedCornerShape(MaxUiMetrics.cardRadius)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MaxUiMetrics.cardPadding, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = if (bypassEnabled == true) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(25.dp)
                            )
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.enable_bypass_charge),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (unsupported) stringResource(R.string.bypass_not_supported)
                                    else stringResource(R.string.enable_bypass_charge_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            MaxSwitch(
                                checked = bypassEnabled == true,
                                enabled = configured && bypassEnabled != null,
                                onCheckedChange = { checked ->
                                    bypassEnabled = checked
                                    writeBypassState(checked)
                                }
                            )
                        }
                    }
                }

                item {
                    SectionLabel(stringResource(R.string.bypass_charge_section_threshold))
                    Spacer(Modifier.height(MaxSectionSpec.spaceAfter))
                    threshold?.let { value ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                            shape = RoundedCornerShape(MaxUiMetrics.cardRadius)
                        ) {
                            Column(Modifier.padding(MaxUiMetrics.cardPadding)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.BatteryChargingFull, null, tint = colors.primary)
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.charging_threshold),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            stringResource(R.string.charging_threshold_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "${value.toInt()}%",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (configured) colors.primary else colors.outline
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { ((value - 20f) / 30f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),
                                    color = if (configured) colors.primary else colors.outline,
                                    trackColor = colors.surfaceContainerHighest
                                )
                                MaxSlider(
                                    value = value,
                                    enabled = configured,
                                    onValueChange = { raw ->
                                        val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(20, 50)
                                        threshold = snapped.toFloat()
                                        writeThreshold(snapped)
                                    },
                                    valueRange = 20f..50f,
                                    steps = 5,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = colors.primary,
                                        inactiveTrackColor = colors.surfaceContainerHighest,
                                        thumbColor = colors.primary
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("20%", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                    Text("50%", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item {
                    SectionLabel(stringResource(R.string.bypass_charge_section_configuration))
                    Spacer(Modifier.height(MaxSectionSpec.spaceAfter))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                        shape = RoundedCornerShape(MaxUiMetrics.cardRadius)
                    ) {
                        Column {
                            ConfigRow(
                                icon = Icons.Filled.SettingsInputComponent,
                                title = stringResource(R.string.bypass_charge_row_path),
                                value = when {
                                    unsupported -> stringResource(R.string.bypass_charge_state_unsupported)
                                    needsSetup -> stringResource(R.string.bypass_charge_state_setup_required)
                                    else -> bypassPath
                                }
                            )
                            ConfigRow(
                                icon = Icons.Filled.Security,
                                title = stringResource(R.string.bypass_charge_row_policy),
                                value = stringResource(R.string.bypass_charge_row_profile)
                            )
                            ConfigRow(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.bypass_charge_row_behavior),
                                value = stringResource(R.string.bypass_charge_row_stop_at)
                            )
                        }
                    }
                }

                item {
                    Card(
                        onClick = { MaxNavActions(navController).navigateTo(MaxDestination.BypassChargingCheck) },
                        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
                        shape = RoundedCornerShape(MaxUiMetrics.cardRadius)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MaxUiMetrics.cardPadding, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = colors.onPrimaryContainer)
                            Spacer(Modifier.size(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.CompatibilityCheck),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onPrimaryContainer
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    stringResource(R.string.CompatibilityCheck_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onPrimaryContainer.copy(alpha = .78f)
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onPrimaryContainer)
                        }
                    }
                }

                item {
                    Surface(
                        color = colors.surfaceContainer,
                        shape = RoundedCornerShape(MaxRadius.tile)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(10.dp))
                            Text(
                                stringResource(R.string.bypass_charge_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun BypassHero(enabled: Boolean, available: Boolean, needsSetup: Boolean, unsupported: Boolean) {
    val colors = MaterialTheme.colorScheme
    val status = when {
        unsupported -> stringResource(R.string.bypass_charge_state_unsupported)
        needsSetup -> stringResource(R.string.bypass_charge_state_needs_setup)
        enabled -> stringResource(R.string.bypass_charge_state_enabled)
        else -> stringResource(R.string.bypass_charge_state_ready)
    }
    val statusColor = when {
        unsupported -> colors.error
        enabled -> colors.primary
        else -> colors.onSurfaceVariant
    }

    // الحشو الأفقي كان `2.dp`: إزاحة أفقية لا تُقرأ محاذاة وتُخرج العنوان عن حدّ البطاقات تحته.
    Column(Modifier.fillMaxWidth().padding(vertical = MaxSpace.xs)) {
        Text(
            stringResource(R.string.charging_bypass_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(MaxSpace.xs))
        Text(
            stringResource(R.string.charging_bypass_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(MaxSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(statusColor))
            Spacer(Modifier.size(8.dp))
            Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor, fontWeight = FontWeight.SemiBold)
            if (available) {
                Spacer(Modifier.size(12.dp))
                Text("•", color = colors.outline)
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.bypass_charge_daemon_controlled), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BypassIllustration(modifier: Modifier, active: Boolean, muted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val accent = if (muted) colors.outline else colors.primary
    val secondary = if (muted) colors.outlineVariant else colors.tertiary

    Surface(modifier = modifier, color = colors.surfaceContainerLow, shape = RoundedCornerShape(MaxCardSpec.radius)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp).padding(18.dp)) {
            val cy = size.height / 2f
            val batteryLeft = size.width * .36f
            val batteryRight = size.width * .66f
            val batteryTop = cy - 45f
            val batteryBottom = cy + 45f
            val corner = 18f

            drawRoundRect(
                color = colors.surfaceContainerHighest,
                topLeft = Offset(batteryLeft, batteryTop),
                size = androidx.compose.ui.geometry.Size(batteryRight - batteryLeft, batteryBottom - batteryTop),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                style = Stroke(width = 5f)
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(batteryRight, cy - 12f),
                size = androidx.compose.ui.geometry.Size(12f, 24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
            )
            drawRoundRect(
                color = if (active) accent else colors.outlineVariant,
                topLeft = Offset(batteryLeft + 12f, batteryTop + 12f),
                size = androidx.compose.ui.geometry.Size((batteryRight - batteryLeft - 24f) * if (active) .82f else .48f, batteryBottom - batteryTop - 24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(11f, 11f)
            )

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .10f, cy)
                lineTo(size.width * .27f, cy)
                lineTo(size.width * .31f, cy - 25f)
            }
            drawPath(path, color = secondary, style = Stroke(width = 5f, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(10f)))
            drawLine(secondary, Offset(size.width * .10f, cy - 12f), Offset(size.width * .10f, cy + 12f), 5f, StrokeCap.Round)

            val bypass = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .69f, cy)
                cubicTo(size.width * .76f, cy - 55f, size.width * .84f, cy - 55f, size.width * .90f, cy)
                lineTo(size.width * .84f, cy - 10f)
            }
            drawPath(bypass, color = accent, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawLine(accent, Offset(size.width * .90f, cy), Offset(size.width * .87f, cy - 13f), 5f, StrokeCap.Round)
            drawLine(accent, Offset(size.width * .90f, cy), Offset(size.width * .77f, cy), 5f, StrokeCap.Round)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        // والمحاذاة الأفقية من الهيكل: كان `start = 3.dp` يُزاح العنوان عن بطاقة قسمه
        // بثلاثة بكسلات — لا تُرى كخطأ ولا تُقرأ محاذاة.
        modifier = Modifier
    )
}

@Composable
private fun ConfigRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    val colors = MaterialTheme.colorScheme
    // حشو **داخل البطاقة**: 17dp و14dp كانتا خارج سلّم الرموز، وعقد البطاقة يعطي 16/12.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaxCardSpec.padding, vertical = MaxSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(13.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}
