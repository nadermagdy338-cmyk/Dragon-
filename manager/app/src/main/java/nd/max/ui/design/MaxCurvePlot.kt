/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — **لوح المنحنى القابل للسحب**.
 *
 * ───────────────────────── Attribution (Apache-2.0) ─────────────────────────
 * Adapted from **DolbyUI** — branch `rodin` of `Digimend-X-Rodin/packages_apps_DolbyUI`
 * (a fork of `swiitch-OFF-Lab/packages_apps_DolbyUI`), licensed **Apache-2.0** as stated in its
 * own file headers, and used with the copyright holder's permission. This is a rework, not a
 * copy, and the Apache notice is retained; the credit is recorded in `docs/PROVENANCE.md`.
 *
 * ───────────────────────────── نسبة الفضل (بالعربيّة) ─────────────────────────────
 * أصل الفكرة واجهةُ **DolbyUI** — فرع `rodin` من `Digimend-X-Rodin/packages_apps_DolbyUI`، برخصة
 * **Apache-2.0** وبرضًا من صاحبه (وتفصيلها في `docs/PROVENANCE.md`). وقد أُعيدت الصياغة لا النقل:
 * الرسم هنا **بلا رقم واحد من الأصل** — لا `±15` مصنوعة ولا `150f` ثابتة ولا `120f` مسافة إمساك،
 * ولا مقياسٌ إلّا ما يُمرَّر في [points] و[axis]. والمكوّن **لا يعرف الصوت**: يأخذ نقاطًا مُعايَرة
 * ويعيد ارتفاعًا — فالحساب يبقى في الطبقة النقيّة ويُقاس على JVM.
 *
 * **ومبدأ التصميم الوحيد الذي يحكمه:** المستخدم يرى النقطة نفسها التي يسحبها. ولذلك لا يُخزَّن
 * مقياسٌ داخله ولا تُطبَّع القيم هنا: `y` تأتي مُعايَرةً من الخارج (`-1f..1f`)، ويكتب المُنادي ما
 * طلبه على العتاد ثمّ **يُعيد قراءته** فيُعاد رسم ما ردّته المنصّة لا ما سُحب.
 */
package nd.max.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * نقطةٌ على مقياس الرسم.
 *
 * @param key هويّة النقطة — **وهي التي تُكتب**: يأخذها المُنادي ويعيدها مع الارتفاع عند الإفلات، فلا
 *   تختلط نقطةُ الرسم بنقطةِ البيانات حين يختلف ترتيبهما.
 * @param x الموضع الأفقيّ `0f..1f` (٠ يسارًا).
 * @param y الموضع الرأسيّ `-1f..1f` (**+ أعلى** الخطّ).
 */
@Immutable
data class MaxPlotPoint(val key: Int, val x: Float, val y: Float)

/** وسمٌ أسفل الرسم: `x` موضعه **من البيانات** لا من توزيعٍ منتظم عليه. */
@Immutable
data class MaxPlotTick(val x: Float, val label: String)

/** وسوم محور الكسب: أعلى الخطّ، وخطّه، وأسفله — الثلاثة **مُصاغة خارجًا** (لا وحدة تُفترض هنا). */
@Immutable
data class MaxPlotAxis(val top: String, val middle: String, val bottom: String)

/** عرض عمود وسوم المحور — ثابتٌ فيُصفّ الرسم نفسه في كل حالاته ولا يقفز عند تغيّر الصياغة. */
// **العرض كان ٣٢dp فقط**، فجملةٌ مثل `+15.0 dB` كانت **تُلَفّ إلى سطرين**، وثلاثة أسطر ملتفّة
// في عمودٍ مُقيَّدٍ بارتفاع الرسم تتراكب فوق بعضها — وهو بالضبط «تداخل الأرقام» الذي شُكِي منه.
// فصار العرض ما يسع الجملة **في سطرٍ واحد**، والنصّ **لا يُلَفّ أصلًا** (`maxLines = 1`).
private val axisLabelWidth: Dp = MaxSpace.xxl + MaxSpace.xl

/**
 * لوح المنحنى — يرسم نقاطًا مُعايَرة، ويسحبها إن كان [editable].
 *
 * @param editable هل يُسحب؟ — ومعه [onPick] و[onDrop]. ولو كان `false` يبقى الرسم **قارئًا**: يُرى
 *   ولا يُلمس، ولا يُعطَّل بصمت بل يقول المُنادي سببه تحته.
 * @param active هل هذا المحرّر هو المُشتغَل عليه؟ — يتبدّل معه اللون والخلفية، فتُعرف اللوحة الحيّة
 *   في شاشةٍ فيها أكثر من رسم.
 * @param axis وسوم الكسب؛ تُترك `null` حين لا صياغة (فوحدات الكسب ليست من عمل هذه الطبقة).
 * @param ticks وسوم أسفل الرسم (ترددات النطاقات) — تُصفّ **على مواضعها الحقيقيّة** لا على مسافات
 *   متساوية، وهو عطبٌ كان في الأصل: `Row(SpaceBetween)` على ترددات **لوغاريتميّة** يُسمّي كلّ وسم
 *   في غير موضعه.
 * @param badgeLabel صياغة الوسم العائم أثناء السحب — **وهي تأخذ هويّة النقطة**: فالوسم الذي لا
 *   يعرف نقطته لا يستطيع أن يقول إلّا مقدارًا عامًّا (`±15`)، وهو ما يُقرأ قيمةً لنطاقٍ بعينه.
 * @param onPick أيّ نقطة تُسحب من موضعٍ أفقيّ؟ — `null` يعني «لا نقطة قريبة»، فيُهمَل اللمس.
 * @param onDragSnap **تقييدٌ يرجع من المنادي أثناء السحب**: يأخذ الارتفاع الخامّ ويعيد **الارتفاع الذي
 *   سيُكتب فعلًا** (بعد تقييده بمدى النقطة). وبه تبقى الدعوى الوحيدة صحيحة: النقطة المرسومة تحت الإصبع
 *   **هي** القيمة التي ستُكتب — لا قيمةً أوسع يريدها الإصبع ويقصر عنها النطاق المُعلَن.
 * @param onDrop عند الإفلات: هويّة النقطة والارتفاع النهائيّ `-1f..1f`. **والكتابة هنا، ثمّ تُقرأ.**
 */
@Composable
fun MaxCurvePlot(
    points: List<MaxPlotPoint>,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    active: Boolean = false,
    axis: MaxPlotAxis? = null,
    ticks: List<MaxPlotTick> = emptyList(),
    height: Dp = 148.dp,
    badgeLabel: ((key: Int, y: Float) -> String)? = null,
    onPick: ((Float) -> Int?)? = null,
    onDragSnap: ((key: Int, y: Float) -> Float)? = null,
    onDrop: ((key: Int, y: Float) -> Unit)? = null,
) {
    if (points.isEmpty()) return

    val shape = RoundedCornerShape(MaxRadius.tile)
    val surface = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val ink = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val curveInk = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.borderStrong)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
    }

    var draggedKey by remember { mutableStateOf<Int?>(null) }
    var draggedY by remember { mutableFloatStateOf(0f) }
    val canDrag = editable && onPick != null && onDrop != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (axis != null) {
                Column(
                    modifier = Modifier
                        .width(axisLabelWidth)
                        .height(height),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = axis.top,
                        style = MaterialTheme.typography.labelSmall,
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        text = axis.middle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        text = axis.bottom,
                        style = MaterialTheme.typography.labelSmall,
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                Spacer(Modifier.width(MaxSpace.sm))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(surface)
                        .border(BorderStroke(MaxSize.hairlineBorder, borderColor), shape)
                        .pointerInput(canDrag, points) {
                            if (!canDrag) return@pointerInput
                            // **والتمرير ليس من عمل هذا الرسم:** كان `detectDragGestures` يبتلع كلّ حركة
                            // رأسيّة، فمن بدأ إصبعَه على المنحنى **لم يستطع تمرير الصفحة أصلًا** —
                            // وهو نصّ الشكوى («اسمح بالتمرير في الشاشة»). فصار الحبس يبدأ **بعد حكمٍ
                            // على الاتّجاه**: رأسيٌّ غالبًا ⇒ يُترك للأب المتمرّر، وأفقيٌّ أولًا ⇒ نمسكه
                            // ونكتب به. فلا جهازٌ يفقد التمرير، ولا رسمٌ يفقد السحب.
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val width = size.width.takeIf { it > 0 } ?: return@awaitEachGesture
                                var dragging = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUpIgnoreConsumed()) break
                                    if (change.isConsumed) break
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (!dragging) {
                                        if (kotlin.math.abs(dx) < slop && kotlin.math.abs(dy) < slop) continue
                                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) break
                                        dragging = true
                                        val key = onPick?.invoke((down.position.x / width).coerceIn(0f, 1f))
                                        if (key != null) {
                                            draggedKey = key
                                            // والارتفاع الابتدائيّ هو ارتفاع النقطة المُلتقَطة: الإصبع
                                            // يمسك النقطة كما تُرى، فلا يبدأ المنحنى بقفزة.
                                            draggedY = points.firstOrNull { it.key == key }?.y ?: 0f
                                        }
                                    }
                                    val key = draggedKey
                                    val plotHeight = size.height.toFloat()
                                    if (key == null || plotHeight <= 0f) break
                                    val center = plotHeight / 2f
                                    val raw = ((center - change.position.y) / center).coerceIn(-1f, 1f)
                                    // ويُرسم **ما سيُكتب** لا ما يريده الإصبع: التقييد يرجع من المنادي.
                                    draggedY = onDragSnap?.invoke(key, raw) ?: raw
                                    change.consume()
                                }
                                // والتسليم **بإسقاطٍ واحد** — وإن كان السحب أفقيًّا لم يبدأ أصلًا فلا
                                // إسقاط (ولو كتبنا لغادرَ المنحنى بقيمةٍ لم يُطلب منها شيء).
                                if (dragging) {
                                    draggedKey?.let { key -> onDrop?.invoke(key, draggedY) }
                                    draggedKey = null
                                }
                            }
                        },
                ) {
                    val width = size.width
                    val plotHeight = size.height
                    val centerY = plotHeight / 2f
                    val gridInk = ink.copy(alpha = MaxAlpha.border)
                    val baselineInk = ink.copy(alpha = MaxAlpha.borderStrong)

                    // **الارتفاع يُقاس من المنتصف لا من الأعلى:** فالمدى `-y..+y` يتماثل حول الخطّ،
                    // وهو ما يجعل «٠» في الوسط لا في الثالث.
                    fun pointY(level: Float): Float = centerY - level.coerceIn(-1f, 1f) * centerY

                    // ① الشبكة والأعمدة: كلٌّ عند موضع نقطته الحقيقيّ.
                    for (step in 1..3) {
                        val y = plotHeight * step / 4f
                        drawLine(gridInk, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                    }
                    points.forEach { point ->
                        val x = point.x.coerceIn(0f, 1f) * width
                        drawLine(gridInk, Offset(x, 0f), Offset(x, plotHeight), strokeWidth = 1f)
                    }

                    // ② خطّ الاستواء: يُرسم دائمًا فيعرف المستخدم من أين يُقاس الكسب.
                    drawLine(baselineInk, Offset(0f, centerY), Offset(width, centerY), strokeWidth = 3f)

                    // ③ المنحنى: وصلٌ منحنٍ بين النقاط (ضلعا التحكّم عند ٤٠٪ من الخطوة).
                    val drawn = points.map { point ->
                        Offset(
                            x = point.x.coerceIn(0f, 1f) * width,
                            y = pointY(if (point.key == draggedKey) draggedY else point.y),
                        )
                    }
                    val path = Path()
                    drawn.forEachIndexed { index, current ->
                        if (index == 0) {
                            path.moveTo(current.x, current.y)
                        } else {
                            val previous = drawn[index - 1]
                            val step = (current.x - previous.x) * 0.4f
                            path.cubicTo(previous.x + step, previous.y, current.x - step, current.y, current.x, current.y)
                        }
                    }
                    drawPath(path, curveInk, style = Stroke(width = if (active) 5f else 4f))

                    val fill = Path().apply {
                        addPath(path)
                        lineTo(width, plotHeight)
                        lineTo(0f, plotHeight)
                        close()
                    }
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                curveInk.copy(alpha = if (active) 0.40f else 0.28f),
                                curveInk.copy(alpha = MaxAlpha.toneWash),
                            ),
                        ),
                    )

                    // ④ النقاط: حلقةٌ بلون الخلفية ثمّ القرص — فتُقرأ النقطة على المنحنى لا داخله.
                    drawn.forEachIndexed { index, center ->
                        val isDragged = points[index].key == draggedKey
                        val radius = if (isDragged) 14f else 10f
                        if (isDragged) {
                            drawCircle(curveInk.copy(alpha = 0.30f), radius = 24f, center = center)
                        }
                        drawCircle(surface, radius = radius, center = center)
                        drawCircle(curveInk, radius = radius - 2f, center = center)
                    }
                }

                val dragging = draggedKey
                if (dragging != null && badgeLabel != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = MaxSpace.sm),
                        shape = RoundedCornerShape(MaxRadius.control),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = MaxSpace.xs,
                    ) {
                        Text(
                            text = badgeLabel(dragging, draggedY),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(
                                horizontal = MaxSpace.md,
                                vertical = MaxSpace.xs,
                            ),
                        )
                    }
                }
            }
        }

        if (ticks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaxSpace.xs),
            ) {
                if (axis != null) Spacer(Modifier.width(axisLabelWidth + MaxSpace.sm))
                MaxTickRow(
                    ticks = ticks,
                    modifier = Modifier.weight(1f),
                    ink = ink,
                )
            }
        }
    }
}

/**
 * صفّ الوسوم أسفل الرسم — **يُوضع بالقياس لا بالتوزيع**.
 *
 * و`Arrangement.SpaceBetween` على ترددات لوغاريتميّة يُسمّي كلّ وسم في غير موضعه (الأصل فعل ذلك)،
 * فالمسافات بين `60Hz` و`120Hz` ليست كالمسافات بين `8kHz` و`16kHz`. وهنا يُرسم كلّ وسم **على موضعه**،
 * ويُقيَّد الطرفان داخل العرض فلا يخرج وسمُ آخر نطاق عن الشاشة.
 */
@Composable
private fun MaxTickRow(
    ticks: List<MaxPlotTick>,
    modifier: Modifier = Modifier,
    ink: Color,
) {
    Layout(
        modifier = modifier,
        content = {
            ticks.forEach { tick ->
                Text(
                    text = tick.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints()) }
        val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
        // فاصلٌ بين وسمٍ وآخر **يُقاس لا يُفترض**: بلاه يلتصق `16kHz` بـ`8kHz` فيصير الرقمان
        // رقمًا واحدًا لا يُقرأ (وهو عطبٌ شوهد على جهازٍ بشاشةٍ ضيّقة).
        val gap = MaxSpace.xs.toPx().toInt().coerceAtLeast(1)
        layout(width, rowHeight) {
            // **ومن يزاحم يُحذف لا يُزاحم:** وسمٌ لا يجد مكانًا لا يُرسم، فيبقى الرسم متناسقًا بدل
            // أن تتداخل الأرقام. والترتيب بالـ`x` أوّلًا، فلا يتخلّل وسمٌ بين جارين ثمّ يُحذف هو.
            var lastRight = Int.MIN_VALUE
            ticks.indices.sortedBy { ticks[it].x }.forEach { index ->
                val placeable = placeables[index]
                val center = (width * ticks[index].x.coerceIn(0f, 1f)).toInt()
                val x = (center - placeable.width / 2)
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                if (x < lastRight + gap) return@forEach
                placeable.placeRelative(x, 0)
                lastRight = x + placeable.width
            }
        }
    }
}
