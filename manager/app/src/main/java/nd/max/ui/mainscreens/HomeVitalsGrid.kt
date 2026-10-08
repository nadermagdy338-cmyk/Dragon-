/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قراءات حيّة في الرئيسية — **ما لا يُعرض في موضع آخر من الشاشة** (طلب المالك: غير مكرّرة).
 *
 * استُبعد من هنا كل ما تعرضه البطاقة الأولى (الحرارة الأساسية · البطارية · الطاقة · مدة التشغيل)، وكل ما
 * تعرضه بطاقتا CPU/GPU (النسبة والتردد والمدى)، وكل ما تعرضه مصفوفة الذاكرة والتخزين. وما بقي أربع قراءات
 * لا تظهر إلا هنا:
 *
 * | خلية      | القراءة                                          | الباب           |
 * | --------- | ------------------------------------------------ | --------------- |
 * | المعالج   | حرارة المعالج الداخلية                            | `ThermalDetail` |
 * | الرسوم    | حرارة وحدة الرسوم                                 | `ThermalDetail` |
 * | السطح     | حرارة الهاتف الذي تلمسه                           | `ThermalDetail` |
 * | التنزيل   | سرعة التنزيل الحيّة، والرفع تحتها                  | `NetworkHub`    |
 *
 * **والمجهول شرطة** (ADR-07): صفر الحرارة غير مقروء. والشبكة تُقاس بين عيّنتين، فقبل أول عيّنة
 * (`readingsAtMs = 0`) لا قراءة وتُكتب شرطة، لا صفرًا يُقرأ كأن الشبكة خاملة.
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

@Composable
internal fun HomeVitalsGrid(
    dashboard: DashboardState,
    onNavigate: (String) -> Unit,
) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(MaxRadius.group)
    val unknown = stringResource(R.string.max_home_unavailable)
    val celsius = stringResource(R.string.home_unit_celsius)

    // ---- حرارات السطح والمعالج والرسوم: كلٌّ بقراءته، وصفرها غير مقروء.
    val cpuC = dashboard.cpuTempC.takeIf { it > 0 }
    val gpuC = dashboard.gpuTempC.takeIf { it > 0 }
    val skinC = dashboard.skinTempC.takeIf { it > 0 }
    val cpuAccent = temperatureAccent(cpuC)
    val gpuAccent = temperatureAccent(gpuC)
    val skinAccent = temperatureAccent(skinC)

    // ---- الشبكة: عدّاد يُقاس بين عيّنتين، فقبل أول عيّنة لا قراءة.
    val measured = dashboard.readingsAtMs > 0L
    val down = if (measured) speedParts(dashboard.downloadSpeedKbps) else null
    val up = if (measured) speedParts(dashboard.uploadSpeedKbps) else null
    val upDetail = up?.let { stringResource(R.string.home_vital_net_up, "${it.first} ${it.second}") } ?: unknown

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.panelTop.copy(alpha = .92f), p.panel)))
            .border(MaxSize.hairlineBorder, p.border, shape),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.home_vital_cpu_temp),
                value = cpuC?.toString() ?: unknown,
                unit = cpuC?.let { celsius },
                accent = cpuAccent,
                fraction = cpuC?.let { (it / HEAT_SCALE_MAX_C).coerceIn(0f, 1f) },
                detail = stringResource(heatWordRes(cpuC)),
                detailColor = cpuAccent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.max_gpu_temp_label),
                value = gpuC?.toString() ?: unknown,
                unit = gpuC?.let { celsius },
                accent = gpuAccent,
                fraction = gpuC?.let { (it / HEAT_SCALE_MAX_C).coerceIn(0f, 1f) },
                detail = stringResource(heatWordRes(gpuC)),
                detailColor = gpuAccent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
        }
        VitalDividerHorizontal()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.home_vital_skin_temp),
                value = skinC?.toString() ?: unknown,
                unit = skinC?.let { celsius },
                accent = skinAccent,
                fraction = skinC?.let { (it / HEAT_SCALE_MAX_C).coerceIn(0f, 1f) },
                detail = stringResource(heatWordRes(skinC)),
                detailColor = skinAccent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.detail_download),
                value = down?.first ?: unknown,
                unit = down?.second,
                accent = p.accent,
                fraction = null,
                detail = upDetail,
                detailColor = p.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.NetworkHub.route) },
            )
        }
    }
}

/**
 * سرعة بوحدتها: الحقل بالكيلوبايت لكل ثانية (`readNetwork` يقسم فرق البايتات على 2048 لعيّنة كل
 * ثانيتين)، ومن ١٠٢٤ فصاعدًا تُعرض ميغابايت. والرقم والوحدة زوج منفصل، فالوحدة تسمية لا جزء من الرقم.
 */
private fun speedParts(kbPerSec: Long): Pair<String, String> =
    if (kbPerSec >= 1024L) {
        String.format(Locale.US, "%.1f", kbPerSec / 1024f) to "MB/s"
    } else {
        kbPerSec.toString() to "KB/s"
    }

/**
 * خلية واحدة: تسمية بحروف كبيرة بنقطة لونها، والرقم الكبير ووحدته على خط واحد، وشريط كسر،
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
