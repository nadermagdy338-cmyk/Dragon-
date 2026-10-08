/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * شبكة الحيوية ٢×٢ — **أربع قراءات حيّة في لمحة، كلٌّ منها باب إلى شاشتها** (عقد §5.3).
 *
 * | خلية      | الرقم الكبير (خط `MonoValueStyleLarge`) | وحدته (تسمية)   | تحته               | الباب            |
 * | --------- | --------------------------------------- | --------------- | ------------------ | ---------------- |
 * | تردد CPU  | أعلى نواة حيّة                          | GHz / MHz       | نسبتها من سقفها    | `CpuCoreControl` |
 * | الذاكرة   | **المتاح** (سؤال المستخدم)              | GB              | المستخدَم/الإجمالي | `MemoryHub`      |
 * | الحرارة   | درجة الحرارة                            | °C              | هادئة · دافئة · ساخنة | `ThermalDetail` |
 * | البطارية  | النسبة                                  | %               | الشحن + واط إن قُرئ | `Charging`      |
 *
 * **تصميم بمقياس `DESIGN.md`:** الرقم هو القراءة الرئيسية في البطاقة (`MonoValueStyleLarge`) بلا
 * تضخيم بحجم يدويّ، والوحدة تسمية بجانبه بالخط اللاتيني/العربي العادي (الخط الأحادي للأرقام وحدها)،
 * والشريط كسرٌ بارتفاع `MaxSize.barHeight`. والتسمية بحروف كبيرة في اللاتينية، فتُقرأ كعنوان عدادٍ
 * لا كجملة.
 *
 * **ولماذا لا تكرّر ما حولها:** بطاقة CPU/GPU تحتها تعرض **تاريخًا**، وهذه الشبكة تعرض **اللحظة**.
 * **والمجهول لا يُصفَّر** (ADR-07): كل قيمة غائبة تُكتب `—` بلا وحدة وبلا شريط.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralClickable
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleLarge
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
    val (cpuValue, cpuUnit) = frequencyParts(topMhz)

    // ---- RAM: المتاح هو الرقم الأبرز، والمستخدَم/الإجمالي تحته.
    val ramTotal = dashboard.ramTotalMb
    val ramUsed = dashboard.ramUsedMb
    val ramKnown = ramTotal > 0
    val ramFreeGb = (ramTotal - ramUsed).coerceAtLeast(0) / 1024f
    val ramValue = if (ramKnown) ramFreeGb.oneDecimal() else "\u2014"

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
                value = cpuValue,
                unit = cpuUnit,
                accent = p.accent,
                fraction = cpuFraction,
                detail = cpuDetail,
                detailColor = p.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.CpuCoreControl.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.home_vital_ram_free),
                value = ramValue,
                unit = if (ramKnown) "GB" else null,
                accent = p.accentAlt,
                fraction = if (ramKnown) fractionOf(ramUsed, ramTotal) else null,
                detail = if (ramKnown) {
                    stringResource(R.string.home_vital_ram_used_of, gigabytes(ramUsed), gigabytes(ramTotal))
                } else {
                    stringResource(R.string.max_home_unavailable)
                },
                detailColor = p.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.MemoryHub.route) },
            )
        }
        VitalDividerHorizontal()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.home_vital_temperature),
                value = heat?.toString() ?: "\u2014",
                unit = heat?.let { "\u00b0C" },
                accent = heatAccent,
                fraction = heat?.let { (it / HEAT_SCALE_MAX_C).coerceIn(0f, 1f) },
                detail = heatWord,
                detailColor = heatAccent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.max_home_battery),
                value = battery?.toString() ?: "\u2014",
                unit = battery?.let { "%" },
                accent = batteryAccent,
                fraction = battery?.let { it / 100f },
                detail = batteryDetail,
                detailColor = if (dashboard.isCharging) p.ok else p.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.Charging.route) },
            )
        }
    }
}

/**
 * المعالج بوحدته: GHz من ألف MHz فصاعدًا، وإلا MHz. والمجهول شرطة بلا وحدة (ADR-07).
 * الزوج يفصل الرقم عن وحدته، فتُكتب الوحدة تسمية بجانب الرقم الكبير لا جزءًا منه.
 */
private fun frequencyParts(mhz: Int?): Pair<String, String?> = when {
    mhz == null -> "\u2014" to null
    mhz >= 1000 -> String.format(Locale.US, "%.2f", mhz / 1000f) to "GHz"
    else -> mhz.toString() to "MHz"
}

/**
 * خلية واحدة: تسمية بحروف كبيرة بنقطة لونها، والرقم الكبير ووحدته بجانبه على خط واحد، وشريط كسر،
 * وسطر حالة. والشريط غائب عند غياب القياس ويُحجز مكانه كي لا تنزاح الخلية المجاورة.
 */
@Composable
private fun VitalCell(
    label: String,
    value: String,
    unit: String?,
    accent: Color,
    fraction: Float?,
    detail: String,
    detailColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    Column(
        modifier
            .neuralClickable(onClick, role = Role.Button)
            .padding(MaxSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(MaxSpace.sm).clip(CircleShape).background(accent))
            Spacer(Modifier.width(MaxSpace.xs))
            Text(
                label.uppercase(),
                color = p.muted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            NeuralValue(value, style = MonoValueStyleLarge, color = p.text)
            if (unit != null) {
                Spacer(Modifier.width(MaxSpace.xs))
                Text(
                    unit,
                    color = p.muted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        if (fraction != null) {
            NeuralTrack(fraction, accent, height = MaxSize.barHeight)
        } else {
            Spacer(Modifier.height(MaxSize.barHeight))
        }
        Text(
            detail,
            color = detailColor,
            style = MaterialTheme.typography.bodySmall,
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
