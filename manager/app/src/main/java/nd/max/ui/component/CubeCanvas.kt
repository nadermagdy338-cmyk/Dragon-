/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import nd.max.core.gamespace.CubeGeometry

/*
 * رسم لوحة «المكعّب»: لوحان جانبيان معتمان تحدّهما حافة الشريطين المائلين، فتنفتح بينهما نافذة
 * على اللعبة (كما في المرجع)، ثم الشريطان من ١٦ مقطعًا يضيئان من القاع بقدر التردد الحيّ.
 * الهندسة كلها من [CubeGeometry] — موضع الشريط واحد للرسم وللاختبار.
 */
internal const val BAR_BLOCKS = 16
internal const val BAR_BLOCKS_UP = 5

internal val CubeBlockOff = Color(0xD9232429)
private val ScrimDeep = Color(0xF5040406)
private val ScrimSoft = Color(0xD1060608)

/** مركز الشريط (الأيسر أو مرآته الأيمن) عند الارتفاع [y]. */
private fun DrawScope.barX(left: Boolean, y: Float): Float {
    val x = CubeGeometry.centerX(size.width, size.height, y)
    return if (left) x else size.width - x
}

/**
 * اللوحان المعتمان: يمين الشريط الأيمن ويسار الشريط الأيسر. والوسط يُترك شفّافًا إلا من عتمة
 * خفيفة عامة تُبقي النصوص المجاورة مقروءة فوق مشاهد فاتحة (خريطة صفراء مثلًا).
 */
internal fun DrawScope.cubeScrims(sweep: Float) {
    val s = sweep.coerceIn(0f, 1f)
    if (s <= 0f) return
    val w = size.width
    val h = size.height
    val tipY = CubeGeometry.TIP_Y * h
    drawRect(Color.Black.copy(alpha = 0.18f * s))
    for (left in listOf(true, false)) {
        fun edge(y: Float): Float {
            val e = CubeGeometry.leftEdge(w, h, y)
            return if (left) e else w - e
        }
        val outer = if (left) 0f else w
        val path = Path().apply {
            moveTo(outer, 0f)
            lineTo(edge(0f), 0f)
            lineTo(edge(tipY), tipY)
            lineTo(edge(h), h)
            lineTo(outer, h)
            close()
        }
        val tip = edge(tipY)
        val brush = if (left) {
            Brush.horizontalGradient(listOf(ScrimDeep, ScrimSoft), startX = 0f, endX = tip)
        } else {
            Brush.horizontalGradient(listOf(ScrimSoft, ScrimDeep), startX = tip, endX = w)
        }
        drawPath(path, brush, alpha = s)
    }
}

/** خطّا الفصل تحت صفّ الدوائر: يفصلان الرأس عن الجسم فيقرأ المستخدم بنية الصفحة لا كتلة واحدة. */
internal fun DrawScope.cubeRules(edgePx: Float, y: Float, sweep: Float) {
    val a = 0.14f * sweep.coerceIn(0f, 1f)
    if (a <= 0f) return
    val gap = 8.dp.toPx()
    val stroke = 1.dp.toPx()
    val reach = CubeGeometry.leftEdge(size.width, size.height, y) - gap
    drawLine(Color.White.copy(alpha = a), Offset(edgePx, y), Offset(reach, y), stroke)
    drawLine(Color.White.copy(alpha = a), Offset(size.width - reach, y), Offset(size.width - edgePx, y), stroke)
}

/** موجة صدمة من موضع المقبض: حلقات متتابعة تتّسع وتخبو، ووميض شعاعي عند نقطة الانطلاق. */
internal fun DrawScope.cubeWave(origin: Offset, t: Float, tint: Color) {
    if (t <= 0f || t >= 1f) return
    val c = Offset(origin.x * size.width, origin.y * size.height)
    val reach = hypot(size.width, size.height)
    for (k in 0..2) {
        val p = ((t - k * 0.11f) / (1f - k * 0.11f)).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) continue
        val r = reach * (1f - (1f - p) * (1f - p))
        val fade = (1f - p) * (1f - p)
        drawCircle(tint.copy(alpha = fade * (0.10f - k * 0.02f)), r, c, style = Stroke((26f - k * 6f).dp.toPx() * (1f - p) + 2.dp.toPx()))
        drawCircle(tint.copy(alpha = fade * (0.55f - k * 0.14f)), r, c, style = Stroke((2.4f - k * 0.4f).dp.toPx()))
    }
    val flash = (1f - t / 0.4f).coerceIn(0f, 1f)
    if (flash > 0f) {
        val fr = size.height * 0.7f
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.5f * flash), Color.Transparent), center = c, radius = fr), fr, c)
    }
}

/**
 * شريط مائل من ١٦ مقطعًا: يضيء من القاع بقدر [lit]، أعمق عند القاع وأفتح قرب القمّة. المقاطع
 * المطفأة رمادية داكنة بحدّ علوي خافت، فيُقرأ الشريط كاملًا حتى حين لا يضيء منه شيء.
 */
internal fun DrawScope.cubeBar(left: Boolean, lit: Float, tint: Color, sweep: Float) {
    val h = size.height
    val tipY = CubeGeometry.TIP_Y * h
    val half = CubeGeometry.HALF * h
    val gap = 0.008f * h
    val progress = sweep.coerceIn(0f, 1f)
    val litBlocks = lit.coerceIn(0f, 1f) * BAR_BLOCKS * progress
    val down = BAR_BLOCKS - BAR_BLOCKS_UP
    for (i in 0 until BAR_BLOCKS) {
        val top: Float
        val bottom: Float
        if (i < BAR_BLOCKS_UP) {
            val step = tipY / BAR_BLOCKS_UP
            top = i * step
            bottom = (i + 1) * step
        } else {
            val step = (h - tipY) / down
            top = tipY + (i - BAR_BLOCKS_UP) * step
            bottom = tipY + (i - BAR_BLOCKS_UP + 1) * step
        }
        val a = top + gap / 2f
        val b = bottom - gap / 2f
        val path = Path().apply {
            moveTo(barX(left, a) - half, a)
            lineTo(barX(left, a) + half, a)
            lineTo(barX(left, b) + half, b)
            lineTo(barX(left, b) - half, b)
            close()
        }
        val j = BAR_BLOCKS - 1 - i
        val on = (litBlocks - j).coerceIn(0f, 1f)
        if (on > 0f) {
            val base = lerp(lerp(tint, Color.Black, 0.18f), lerp(tint, Color.White, 0.34f), j / (BAR_BLOCKS - 1f))
            val front = progress < 0.995f && j == (litBlocks - 0.001f).toInt()
            val color = if (front) lerp(base, Color.White, 0.7f) else base
            drawPath(path, color.copy(alpha = (if (front) 0.5f else 0.22f) * on), style = Stroke(width = (if (front) 12f else 7f).dp.toPx()))
            drawPath(path, color.copy(alpha = on))
        } else {
            drawPath(path, CubeBlockOff)
            drawPath(path, Color.White.copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
        }
    }
}
