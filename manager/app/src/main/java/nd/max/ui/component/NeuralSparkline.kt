/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * موجة قياس مشتركة — **نُقلت لا نُسخت**.
 *
 * كانت `FrequencySparkline` خاصةً بـ`LegendaryHomeDashboard`، وشاشة CPU تحتاج الموجة نفسها
 * لكل نواة. ونسخةٌ ثانية من دالة رسم تعني **موجتين تفترقان عند أول تعديل**: من يغيّر التعبئة
 * أو سمك الخط في إحداهما يظنّ أنه غيّر «شكل الموجة» في التطبيق. فنُقلت إلى كومبوننت، والملفّان
 * يرسمان من هذا الملفّ وحده — والقيمة البصرية لم تتغيّر حرفيًّا (نقل لا إعادة تصميم).
 *
 * **والوحدات مقصودة:** `samples` **نسب** من ٠ إلى ١ لا ميغاهرتز. القرار «ما مدى هذه النواة»
 * يبقى في الشاشة التي تعرف سقفها المعلن، والرسم يعرف الشكل فقط — فلا تصنع الموجة مقياسًا.
 */
package nd.max.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun NeuralSparkline(
    samples: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    /**
     * خطّ الأرضية المعلنة داخل المدى، أو `null` إن لم تُعلن أرضية.
     *
     * ويُرسم على مستواه الحقيقي فيصير المدى مقروءًا من الرسم نفسه لا مِن نصّ مجاور — ويُشتقّ
     * من الرقم الذي أعلنته النواة، لا من أدنى عيّنة.
     */
    floorFraction: Float? = null,
) {
    val p = neuralPalette()
    val grid = p.muted.copy(alpha = .07f)
    val floorLine = p.muted.copy(alpha = .22f)
    val animationsEnabled = rememberAnimationsEnabled()
    val beaconPulse: Float = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "sparklineBeacon")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "beaconPulse"
        ).value
    } else 0f

    Canvas(modifier) {
        if (samples.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val top = 5f
        val bottom = h - 5f
        val usable = (bottom - top).coerceAtLeast(1f)
        val step = w / (samples.size - 1).toFloat()
        val points = samples.mapIndexed { index, value ->
            Offset(step * index, top + usable * (1f - value.coerceIn(0f, 1f)))
        }

        drawLine(grid, Offset(0f, top), Offset(w, top), 1f)
        drawLine(grid, Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
        drawLine(grid, Offset(0f, bottom), Offset(w, bottom), 1f)

        floorFraction?.let { fraction ->
            val y = top + usable * (1f - fraction)
            drawLine(floorLine, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                quadraticTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
            }
            lineTo(points.last().x, points.last().y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(points.last().x, bottom)
            lineTo(points.first().x, bottom)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = .20f), accent.copy(alpha = .015f)),
                startY = top,
                endY = bottom
            )
        )
        drawPath(path, color = accent.copy(alpha = .14f), style = Stroke(width = 7.dp.toPx()))
        drawPath(
            path,
            color = accent,
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        val lastPoint = points.last()
        if (animationsEnabled && beaconPulse > 0f) {
            val haloRadius = 3.dp.toPx() + beaconPulse * 5.dp.toPx()
            val haloAlpha = (1f - beaconPulse) * 0.45f
            drawCircle(accent.copy(alpha = haloAlpha), radius = haloRadius, center = lastPoint)
        }
        drawCircle(accent, radius = 3.dp.toPx(), center = lastPoint)
    }
}
