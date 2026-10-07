/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * شبكة الحيوية ٢×٢ — **أربعة أرقام حيّة في لمحة، كلٌّ منها باب إلى شاشته** (عقد §5.3).
 *
 * | خلية      | الرقم الكبير          | تحته                    | الباب            |
 * | --------- | --------------------- | ----------------------- | ---------------- |
 * | تردد CPU  | أعلى نواة حيّة        | نسبتها من سقفها المعلن  | `CpuCoreControl` |
 * | الذاكرة   | **المتاح** (سؤال المستخدم) | المستخدَم/الإجمالي  | `MemoryHub`      |
 * | الحرارة   | °C                    | هادئة · دافئة · ساخنة   | `ThermalDetail`  |
 * | البطارية  | %                     | الشحن + واط إن قُرئ     | `Charging`       |
 *
 * **ولماذا لا تكرّر ما حولها:** بطاقة CPU/GPU تحتها تعرض **تاريخًا** (٣٦ عيّنة وأرضية وسقفًا)، وهذه
 * الشبكة تعرض **اللحظة**. فأعلى نواة تُرسم هنا رقمًا وشريط سقف بلا موجة، والموجة لها بطاقتها.
 * والحرارة والبطارية ومدّة التشغيل لا تُرسم في البطل ثانيةً — البطل صار هويةً فقط.
 *
 * **والمجهول لا يُصفَّر** (ADR-07): كل قيمة غائبة تُكتب `—` وبلا شريط، لا `0%` ولا `0 °C`.
 * وشريط الحرارة مقياس عرض (٠–٦٠°م) لا ادّعاءَ خنقٍ: لا نعرف عتبة خنق هذا الجهاز.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState

/** سقف مقياس عرض الحرارة (°م). مقياس رسم فقط — لا يقول إن الجهاز يُخنق عنده. */
private const val HEAT_SCALE_MAX_C = 60f

/** عتبتا حكم الحرارة: تطابقان `temperatureAccent` حرفيًّا، فلا لونان يخالف أحدهما كلمة الآخر. */
private const val HEAT_HOT_C = 45
private const val HEAT_WARM_C = 40

@Composable
internal fun HomeVitalsGrid(
    dashboard: DashboardState,
    onNavigate: (String) -> Unit,
) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(MaxRadius.group)

    // ---- CPU: أعلى نواة حيّة، ونسبتها من سقفها المعلن إن أُعلن.
    val topMhz = dashboard.cpuTopCoreMhz.takeIf { it > 0 }
    val ceilingMhz = dashboard.cpuCeilingMhz.takeIf { it > 0 }
    val cpuFraction = if (topMhz != null && ceilingMhz != null) {
        (topMhz.toFloat() / ceilingMhz).coerceIn(0f, 1f)
    } else {
        null
    }
    val cpuDetail = when {
        topMhz == null -> stringResource(R.string.max_home_unavailable)
        cpuFraction != null -> stringResource(
            R.string.home_vital_cpu_ceiling,
            (cpuFraction * 100f).roundToInt(),
        )
        else -> stringResource(R.string.home_vital_cpu_live)
    }

    // ---- RAM: المتاح هو الرقم الأبرز، والمستخدَم/الإجمالي تحته.
    val ramTotal = dashboard.ramTotalMb
    val ramUsed = dashboard.ramUsedMb
    val ramKnown = ramTotal > 0

    // ---- الحرارة: حسّاس البطارية أولًا ثم المعالج، كما كان البطل يفعل.
    val heat = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val heatAccent = temperatureAccent(heat)
    val heatWord = when {
        heat == null -> stringResource(R.string.max_home_unavailable)
        heat >= HEAT_HOT_C -> stringResource(R.string.home_vital_temp_hot)
        heat >= HEAT_WARM_C -> stringResource(R.string.home_vital_temp_warm)
        else -> stringResource(R.string.home_vital_temp_cool)
    }

    // ---- البطارية: النسبة، وحالة الشحن، والواط إن قُرئ (الصفر = غير مقروء لا «لا استهلاك»).
    val battery = dashboard.batteryPercent.takeIf { it > 0 }
    val batteryState = stringResource(
        if (dashboard.isCharging) R.string.home_vital_charging else R.string.home_vital_on_battery,
    )
    val batteryDetail = if (dashboard.powerWatt > 0.05f) {
        "$batteryState · ${dashboard.powerWatt.oneDecimal()} W"
    } else {
        batteryState
    }
    val batteryAccent = when {
        dashboard.isCharging -> p.ok
        battery != null && battery <= 20 -> p.warn
        else -> p.accent
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.panelTop.copy(alpha = .92f), p.panel)))
            .border(MaxSize.hairlineBorder, p.border, shape),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.home_vital_cpu),
                value = topMhz?.let { compactFrequency(it) } ?: "\u2014",
                accent = p.accent,
                detail = cpuDetail,
                detailColor = p.muted,
                fraction = cpuFraction,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.CpuCoreControl.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.home_vital_ram_free),
                value = if (ramKnown) gigabytes((ramTotal - ramUsed).coerceAtLeast(0)) else "\u2014",
                accent = p.accentAlt,
                detail = if (ramKnown) {
                    stringResource(R.string.home_vital_ram_used_of, gigabytes(ramUsed), gigabytes(ramTotal))
                } else {
                    stringResource(R.string.max_home_unavailable)
                },
                detailColor = p.muted,
                fraction = if (ramKnown) fractionOf(ramUsed, ramTotal) else null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.MemoryHub.route) },
            )
        }
        VitalDividerHorizontal()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.home_vital_temperature),
                value = heat?.let { "$it\u00b0C" } ?: "\u2014",
                accent = heatAccent,
                detail = heatWord,
                detailColor = heatAccent,
                fraction = heat?.let { (it / HEAT_SCALE_MAX_C).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.max_home_battery),
                value = battery?.let { "$it%" } ?: "\u2014",
                accent = batteryAccent,
                detail = batteryDetail,
                detailColor = if (dashboard.isCharging) p.ok else p.muted,
                fraction = battery?.let { it / 100f },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.Charging.route) },
            )
        }
    }
}

/**
 * خلية واحدة: تسمية صغيرة بنقطة لونها، رقم كبير بخطّ أحادي، شريط، وسطر حالة.
 *
 * والشريط غائب عند غياب القياس ويُحجز مكانه (`Spacer`) كي يبدأ سطر الحالة من الارتفاع نفسه في
 * الخليتين المتجاورتين — وإلا انزاح نصّ خلية عن جارتها بمقدار شريط حين يُقرأ أحدهما ولا يُقرأ الآخر.
 */
@Composable
private fun VitalCell(
    label: String,
    value: String,
    accent: Color,
    detail: String,
    detailColor: Color,
    fraction: Float?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    Column(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(MaxSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                label,
                color = p.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = p.text,
        )
        if (fraction != null) {
            NeuralTrack(fraction, accent)
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Text(
            detail,
            color = detailColor,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VitalDividerVertical() {
    Box(
        Modifier
            .width(MaxSize.hairlineBorder)
            .fillMaxHeight()
            .background(neuralPalette().border),
    )
}

@Composable
private fun VitalDividerHorizontal() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MaxSize.hairlineBorder)
            .background(neuralPalette().border),
    )
}
