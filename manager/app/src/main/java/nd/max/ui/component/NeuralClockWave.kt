package nd.max.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **موجة الساعة** — تاريخ تردّد حقيقي كرسم بياني حيّ، لا شريط تعبئة.
 *
 * ولماذا هذا الشكل بدل الشريط المستقيم: الشريط يقول «أين الساعة الآن»، وهو سؤال تجيبه
 * بطاقة أخرى برقم. أما السؤال الذي لا يجيبه رقم واحد فهو **كيف وصلت إلى هناك**: ساعةٌ
 * مستقرة عند ٧٠٪ من مداها شيء، وساعةٌ تتقافز بين ٧٠٪ و١٠٠٪ كل ثانيتين شيء آخر تمامًا —
 * والتقافز يُترجم حرارةً وتباطؤًا في اللعب. الموجة تُظهر الفرق بلا كلمة.
 *
 * **وثلاث قواعد تحدّ ما يُرسم:**
 *
 * 1. **لا موجة بلا سقف معلَن**: النقاط تأتي من [nd.max.ui.util.ClockWave.series] التي
 *    تُعيد `null` كاملةً حين لا تُعلن النواة سقفًا — فلا يُخترع مدى من أعلى قيمة رآها التطبيق.
 * 2. **الفراغ فراغ**: عيّنة لم تُقرأ تُقطع عندها الموجة ولا تُوصل، ولا تنزل إلى القاع؛
 *    فالخط المتّصل عبر فراغ كان سيرسم قياسًا لم يقع.
 * 3. **خطّ السقف مُعلَن**: خطّ منقّط عند الحدّ الأعلى المعلَن، ونصّه بجانب القراءة، فيعرف
 *    المستخدم ما تُقاس إليه النسب بدل أن يخمّن مقياس المحور.
 *
 * والحركة مُوجَّهة بالبيانات وحدها: كل عيّنة تنزلق نحو قيمتها في نصف ثانية، ولا شيء يتحرّك
 * بلا عيّنة جديدة (لا وميض تجميلي يوهم بنشاط).
 */
@Composable
fun NeuralClockWave(
    reading: String,
    ceiling: String?,
    series: List<Float?>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val p = neuralPalette()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // الحركة لكل عيّنة على حدة: إعادة التركيب لا تُعيد كشف الرسم من الصفر.
    val values = series.mapIndexed { index, value ->
        animateFloatAsState(
            targetValue = value ?: 0f,
            animationSpec = tween(620, easing = FastOutSlowInEasing),
            label = "clock-wave-$index",
        ).value
    }
    val presence = series.mapIndexed { index, value ->
        animateFloatAsState(
            targetValue = if (value == null) 0f else 1f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            label = "clock-wave-presence-$index",
        ).value
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                reading,
                color = if (series.any { it != null }) accent else p.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (ceiling != null) {
                Text(
                    ceiling,
                    color = p.muted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            if (values.isEmpty()) return@Canvas
            val step = if (values.size > 1) size.width / (values.size - 1) else size.width
            val ceilingY = 1.5f
            val floorY = size.height
            val span = floorY - ceilingY

            fun x(index: Int): Float {
                val along = step * index
                return if (rtl) size.width - along else along
            }

            fun y(index: Int): Float = floorY - values[index].coerceIn(0f, 1f) * span

            // خطّ السقف: مرجع المحور، منقّط فلا يُقرأ كعيّنة.
            drawLine(
                color = p.border.copy(alpha = .55f),
                start = Offset(0f, ceilingY),
                end = Offset(size.width, ceilingY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
            )
            // خطّ الأساس: يعطي العمق ويعرف منه المستخدم أن الموجة تُقاس على مدى كامل.
            drawLine(
                color = p.grid,
                start = Offset(0f, floorY - .5f),
                end = Offset(size.width, floorY - .5f),
                strokeWidth = 1f,
            )

            // كل مقطع يُرسم فقط بين عيّنتين مقروءتين فعليًّا.
            var runStart = -1
            for (index in values.indices) {
                val present = presence[index] > .05f
                if (!present) {
                    runStart = -1
                    continue
                }
                if (runStart < 0) runStart = index
                val next = index + 1
                if (next < values.size && presence[next] > .05f) {
                    drawLine(
                        color = accent.copy(alpha = .16f * presence[index]),
                        start = Offset(x(index), y(index)),
                        end = Offset(x(next), y(next)),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accent.copy(alpha = presence[index]),
                        start = Offset(x(index), y(index)),
                        end = Offset(x(next), y(next)),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // التعبئة تحت المقطع الذي انتهى: مساحة ناعمة تُقرأ كـ«كم من المدى كان مشغولًا».
                if (next >= values.size || presence[next] <= .05f) {
                    if (index > runStart) {
                        val area = Path()
                        area.moveTo(x(runStart), floorY)
                        for (i in runStart..index) area.lineTo(x(i), y(i))
                        area.lineTo(x(index), floorY)
                        area.close()
                        drawPath(
                            area,
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = .26f), Color.Transparent),
                                startY = ceilingY,
                                endY = floorY,
                            ),
                        )
                    }
                    runStart = -1
                }
            }

            // رأس الموجة: آخر عيّنة مقروءة — «الآن» في الرسم بلا سهم.
            val headIndex = values.indices.lastOrNull { presence[it] > .05f }
            if (headIndex != null) {
                val center = Offset(x(headIndex), y(headIndex))
                drawCircle(accent.copy(alpha = .22f), radius = 5.dp.toPx(), center = center)
                drawCircle(accent, radius = 2.2.dp.toPx(), center = center)
            }
        }
    }
}
