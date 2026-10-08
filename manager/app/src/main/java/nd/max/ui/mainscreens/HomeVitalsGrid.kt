/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قراءات حيّة في الرئيسية — **ما لا يُعرض في موضع آخر من الشاشة**، وكل قراءة تقول اتجاهها (طلب المالك).
 *
 * | خلية          | القراءة                                         | الباب           |
 * | ------------- | ----------------------------------------------- | --------------- |
 * | المعالج       | حرارة المعالج الداخلية، واتجاهها                 | `ThermalDetail` |
 * | الرسوم        | حرارة وحدة الرسوم، واتجاهها                      | `ThermalDetail` |
 * | هامش الخنق    | المسافة إلى أقرب نقطة تخفيف حراري                | `ThermalDetail` |
 * | ضغط الذاكرة   | ضغط PSI: هادئ أو يتعثّر، أو غير مدعوم            | `MemoryHub`     |
 * | التنزيل       | سرعة التنزيل الحيّة، والرفع تحتها                | `NetworkHub`    |
 * | معدل الشاشة   | المعدل الذي تعمل به الشاشة الآن                  | `DisplayHub`    |
 *
 * **الاتجاه** يأتي من نافذة الدقيقة الأخيرة (`HomeTrendModel`)، ويُكتب بجانب كلمة الحكم فلا يحمله اللون وحده.
 * **والمجهول شرطة** (ADR-07). والشبكة تُقاس بين عيّنتين، فقبل أول عيّنة لا قراءة.
 * **والخلية كلها جملة واحدة لقارئ الشاشة**، والسهم يدلّ على أنها باب إلى شاشتها.
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import nd.max.R
import nd.max.core.maxai.MemoryStall
import nd.max.ui.component.NeuralPalette
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

/** مقياس شريط الهامش (°م): عند ٣٠° فأكثر فوق نقطة التخفيف يكون الشريط فارغًا. مقياس رسم فقط. */
private const val HEADROOM_SCALE_C = 30f

/** حدّ «قريب من نقطة الخنق» (°م): ما دونه تحذير، ومن صفر فأقل «مخنوق الآن». */
private const val HEADROOM_NEAR_C = 10

/** مقياس شريط الضغط (%): يمتلئ عند عتبة التعثّر نفسها، فالشريط يتّفق مع الكلمة واللون. */
private const val PRESSURE_SCALE_PCT = 10f

@Composable
internal fun HomeVitalsGrid(
    dashboard: DashboardState,
    onNavigate: (String) -> Unit,
) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(MaxRadius.group)
    val unknown = stringResource(R.string.max_home_unavailable)
    val celsius = stringResource(R.string.home_unit_celsius)
    val percent = stringResource(R.string.home_unit_percent)
    val hertz = stringResource(R.string.home_unit_hz)

    // ---- حرارتا المعالج والرسوم: كلٌّ بقراءته، وصفرها غير مقروء.
    val cpuC = dashboard.cpuTempC.takeIf { it > 0 }
    val gpuC = dashboard.gpuTempC.takeIf { it > 0 }
    val cpuAccent = temperatureAccent(cpuC)
    val gpuAccent = temperatureAccent(gpuC)

    // ---- اتجاه كل حرارة: نافذة الدقيقة الأخيرة تُغذّى مرة مع كل قراءة مكتملة، لا مع كل تركيب.
    val cpuTrail = remember { mutableStateListOf<Int>() }
    val gpuTrail = remember { mutableStateListOf<Int>() }
    LaunchedEffect(dashboard.readingsAtMs) {
        HomeTrendModel.append(cpuTrail, cpuC)
        HomeTrendModel.append(gpuTrail, gpuC)
    }
    val cpuTrend = HomeTrendModel.direction(cpuTrail)
    val gpuTrend = HomeTrendModel.direction(gpuTrail)

    // ---- هامش الخنق: المسافة إلى أقرب نقطة تخفيف. لا نقطة قابلة للقراءة ⇒ شرطة، لا صفر.
    val headroom = dashboard.throttleHeadroomC
    val headroomTone = headroomToneOf(headroom, p)
    val headroomShown = headroom?.coerceAtLeast(0)
    val headroomFraction = headroom?.let { (1f - it / HEADROOM_SCALE_C).coerceIn(0f, 1f) }

    // ---- ضغط الذاكرة (PSI): غير مدعوم من النواة ⇒ شرطة وكلمة تقول السبب، لا صفر مزيّف.
    val stall = dashboard.memoryStall
    val pressure = stall.fullPercent?.takeIf { stall.measured }
    val pressureTone = pressureToneOf(stall, p)

    // ---- معدل الشاشة الجاري: من حالة الشاشة المقروءة أصلًا، بلا استعلام إضافي.
    val refreshHz = dashboard.displayRefreshHz.takeIf { it > 0 }

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
                detail = heatDetail(cpuC, cpuTrend),
                detailColor = cpuAccent,
                trend = cpuTrend,
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
                detail = heatDetail(gpuC, gpuTrend),
                detailColor = gpuAccent,
                trend = gpuTrend,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
        }
        VitalDividerHorizontal()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.home_vital_headroom),
                value = headroomShown?.toString() ?: unknown,
                unit = headroomShown?.let { celsius },
                accent = headroomTone,
                fraction = headroomFraction,
                detail = headroomDetail(headroom),
                detailColor = headroomTone,
                trend = null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.ThermalDetail.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.home_vital_memory_pressure),
                value = pressure?.let { String.format(Locale.US, "%.1f", it) } ?: unknown,
                unit = pressure?.let { percent },
                accent = pressureTone,
                fraction = pressure?.let { (it / PRESSURE_SCALE_PCT).coerceIn(0f, 1f) },
                detail = if (dashboard.readingsAtMs > 0L) pressureDetail(stall) else unknown,
                detailColor = pressureTone,
                trend = null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.MemoryHub.route) },
            )
        }
        VitalDividerHorizontal()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            VitalCell(
                label = stringResource(R.string.detail_download),
                value = down?.first ?: unknown,
                unit = down?.second,
                accent = p.accent,
                fraction = null,
                detail = upDetail,
                detailColor = p.muted,
                trend = null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.NetworkHub.route) },
            )
            VitalDividerVertical()
            VitalCell(
                label = stringResource(R.string.display_refresh_control_title),
                value = refreshHz?.toString() ?: unknown,
                unit = refreshHz?.let { hertz },
                accent = p.accentAlt,
                fraction = null,
                detail = if (refreshHz == null) unknown else stringResource(R.string.home_refresh_detail),
                detailColor = p.muted,
                trend = null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onNavigate(MaxDestination.DisplayHub.route) },
            )
        }
    }
}

/** لون الهامش: مخنوق ⇒ خطر، وقريب ⇒ تحذير، وبعيد ⇒ إيجابي، وبلا نقطة ⇒ محايد. */
private fun headroomToneOf(headroom: Int?, p: NeuralPalette): Color = when {
    headroom == null -> p.muted
    headroom <= 0 -> p.danger
    headroom <= HEADROOM_NEAR_C -> p.warn
    else -> p.ok
}

/** كلمة الهامش تُكتب بجانب الرقم، فلا يحمل اللون المعنى وحده. */
@Composable
private fun headroomDetail(headroom: Int?): String = when {
    headroom == null -> stringResource(R.string.max_home_unavailable)
    headroom <= 0 -> stringResource(R.string.home_headroom_now)
    headroom <= HEADROOM_NEAR_C -> stringResource(R.string.home_headroom_near)
    else -> stringResource(R.string.home_headroom_far)
}

/** لون الضغط: المتعثّر تحذير أحمر، والهادئ إيجابي، وغير المدعوم محايد. */
private fun pressureToneOf(stall: MemoryStall.Sample, p: NeuralPalette): Color = when {
    !stall.measured -> p.muted
    MemoryStall.isThrashing(stall.fullFraction) -> p.danger
    else -> p.ok
}

/** كلمة الضغط: هادئ · يتعثر · غير مدعوم، فلا صفر مزيّف. */
@Composable
private fun pressureDetail(stall: MemoryStall.Sample): String = when {
    !stall.measured -> stringResource(R.string.home_pressure_unsupported)
    MemoryStall.isThrashing(stall.fullFraction) -> stringResource(R.string.home_pressure_stalling)
    else -> stringResource(R.string.home_pressure_calm)
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
 * سهم الاتجاه: صعودًا للحرارة المتزايدة، وهبوطًا للمتناقصة، وخطٌّ أفقي للثابتة، ولا رمز دون قياسين.
 * أسهم رأسية لا أفقية: الأسهم الأفقية تنعكس في العربية، والاتجاه هنا صعود وهبوط لا يساراً ويميناً.
 */
private fun trendIcon(trend: HomeTrend): ImageVector? = when (trend) {
    HomeTrend.UP -> Icons.Rounded.ArrowUpward
    HomeTrend.DOWN -> Icons.Rounded.ArrowDownward
    HomeTrend.STEADY -> Icons.Rounded.Remove
    HomeTrend.UNKNOWN -> null
}

/** لون السهم: الارتفاع تحذير، والانخفاض إيجابي، والثبات محايد. */
private fun trendTone(trend: HomeTrend, p: NeuralPalette): Color = when (trend) {
    HomeTrend.UP -> p.warn
    HomeTrend.DOWN -> p.ok
    else -> p.muted
}

/** سطر الحالة: كلمة الحكم ثم الاتجاه بالكلمة، فلا يحمل اللون المعنى وحده. */
@Composable
private fun heatDetail(heat: Int?, trend: HomeTrend): String {
    val word = stringResource(heatWordRes(heat))
    val trendWord = when (trend) {
        HomeTrend.UP -> stringResource(R.string.home_trend_up)
        HomeTrend.DOWN -> stringResource(R.string.home_trend_down)
        HomeTrend.STEADY -> stringResource(R.string.home_trend_steady)
        HomeTrend.UNKNOWN -> null
    }
    return if (heat == null || trendWord == null) {
        word
    } else {
        stringResource(R.string.home_detail_with_trend, word, trendWord)
    }
}

/**
 * خلية واحدة: تسمية بحروف كبيرة بنقطة لونها وسهم الباب، والرقم الكبير ووحدته وسهم الاتجاه على خط واحد،
 * وشريط كسر، وسطر حالة. والخلية كلها جملة واحدة لقارئ الشاشة. والشريط غائب عند غياب القياس
 * ويُحجز مكانه كي لا تنزاح الخلية المجاورة.
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
    trend: HomeTrend?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    val sentence = stringResource(R.string.home_vital_a11y, label, value, unit ?: "", detail)
    val arrow = trend?.let { trendIcon(it) }
    Column(
        modifier
            .neuralClickable(onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = sentence }
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
                modifier = Modifier.weight(1f),
            )
            // سهم الباب بعد التسمية، كما في بطاقات CPU/GPU ومصفوفة الذاكرة: الخلية تقود إلى شاشتها.
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(MaxSize.iconGlyphSmall),
                tint = accent,
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
            if (arrow != null && trend != null) {
                Spacer(Modifier.width(MaxSpace.sm))
                Icon(arrow, null, Modifier.size(MaxSize.iconGlyphSmall), tint = trendTone(trend, p))
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
