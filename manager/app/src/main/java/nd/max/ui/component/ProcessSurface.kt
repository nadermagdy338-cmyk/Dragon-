/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.platform.ProcessReading
import nd.max.core.platform.ProcessSample
import nd.max.core.platform.ProcessSort
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxUsageBar
import nd.max.ui.util.ProcessOverlayPrefs

/**
 * لوحة قائمة العمليات — **مُصيِّر واحد** للنافذة العائمة وللمعاينة داخل الشاشة.
 *
 * ### ولماذا موضع واحد
 *
 * كان التراكب يرسم نسخته والواجهة ترسم نسخة أخرى بأرقام أخرى، فيظهر في الشاشة ما لا يظهر فوق
 * اللعبة: الفرق لا يُكتشف إلا بالعين على جهاز، وهذا ما لا يوجد هنا. فصار **هذا الملفّ** هو
 * الرسم، والمعاينة تناديه بالحالة نفسها التي تناديها الخدمة.
 *
 * ### ومعنى الشريط مُعلَن
 *
 * الشريط نسبة إلى **أثقل صفّ معروض** لا إلى قدرة الجهاز: سؤال اللوحة «مَن الأثقل هنا؟»، والرقم
 * المكتوب بجانبه هو الحقيقة المطلقة (نسبة أو حجمًا). ومزج المعنيين في شريط واحد هو ما يجعل
 * مقارنة لوحتين على جهازين مختلفة بلا سبب.
 *
 * ### والحدود
 *
 * [textSizeSp] و[backgroundAlpha] يأتيان من إعداد المستخدم لا من رموز ثابتة، و[markHeavy] يلوّن
 * ما بلغ [ProcessOverlayPrefs.HEAVY_PERCENT] — والقيمة القارئ يراها، فالنسبة إلى يسارها.
 *
 * @param baseline أكبر قيمة في [sample]؛ يُمرَّر من العرض لا يُحسب هنا، لأن **نفس الرقم** يُحسب
 *   في الشاشة أيضًا فلا يختلف الرسمان.
 */
@Composable
fun ProcessSurface(
    sample: ProcessSample,
    sort: ProcessSort,
    baseline: Float,
    textSizeSp: Float,
    backgroundAlpha: Float,
    markHeavy: Boolean,
    showTally: Boolean,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.row),
        color = colors.surfaceContainer.copy(alpha = backgroundAlpha),
        // ارتفاع نغميّ من رمز قائم لا رقم جديد — الرمز الواحد يُستخدم حيث يُحتاج شعرة فصل.
        tonalElevation = MaxSize.hairlineBorder
    ) {
        Column(
            modifier = Modifier.padding(start = MaxSpace.sm, end = MaxSpace.sm, top = MaxSpace.xs, bottom = MaxSpace.sm),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.processmgr_title),
                    fontSize = textSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(Modifier.width(MaxSpace.sm))
                if (showTally && !sample.failed) {
                    // التعداد على الشجرة كلها لا على الصفوف المعروضة: رقم يصف الجهاز لا قصّ اللوحة.
                    Text(
                        text = stringResource(R.string.processmgr_stat_total, sample.tally.running),
                        fontSize = textSizeSp.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.size(MaxSize.iconGlyph)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.processmgr_overlay_close_cd),
                            tint = colors.error
                        )
                    }
                }
            }

            when {
                // «لا جواب» ليست «لا شيء»: العمليات موجودة والقراءة هي التي لم تصل.
                sample.failed -> Text(
                    text = stringResource(R.string.processmgr_no_answer),
                    fontSize = textSizeSp.sp,
                    color = colors.onSurfaceVariant
                )

                sample.readings.isEmpty() -> Text(
                    text = stringResource(R.string.processmgr_no_processes),
                    fontSize = textSizeSp.sp,
                    color = colors.onSurfaceVariant
                )

                else -> sample.readings.forEach { reading ->
                    ProcessSurfaceRow(
                        reading = reading,
                        sort = sort,
                        baseline = baseline,
                        textSizeSp = textSizeSp,
                        markHeavy = markHeavy
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessSurfaceRow(
    reading: ProcessReading,
    sort: ProcessSort,
    baseline: Float,
    textSizeSp: Float,
    markHeavy: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val value = metricOf(reading, sort)
    val heavy = markHeavy && value >= ProcessOverlayPrefs.HEAVY_PERCENT
    val fraction = if (baseline > 0f) (value / baseline).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reading.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = textSizeSp.sp,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                text = valueTextOf(reading, sort),
                fontSize = textSizeSp.sp,
                fontWeight = FontWeight.Bold,
                color = if (heavy) colors.error else colors.primary
            )
        }
        MaxUsageBar(
            fraction = fraction,
            tone = if (heavy) MaxTone.Caution else MaxTone.Neutral,
            height = MaxSize.barHeight / 2
        )
    }
}

/** الرقم الذي يُرتَّب به ويُقارَن: نسبة المعالج أو الكيلوبايت. */
fun metricOf(reading: ProcessReading, sort: ProcessSort): Float = when (sort) {
    ProcessSort.Memory -> reading.residentKb.toFloat()
    else -> reading.cpuPercent
}

/** النصّ المعروض للرقم نفسه — فما يُكتب وما يُقاس من مصدر واحد. */
fun valueTextOf(reading: ProcessReading, sort: ProcessSort): String = when (sort) {
    ProcessSort.Memory -> reading.residentText
    else -> reading.cpuText
}

/** أعلى قيمة معروضة — يُمرَّر إلى [ProcessSurface] ليُبنى الشريط على مرجع واحد. */
fun baselineOf(readings: List<ProcessReading>, sort: ProcessSort): Float =
    readings.maxOfOrNull { metricOf(it, sort) } ?: 0f
