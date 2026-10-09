/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.gamespace

import kotlin.math.max
import kotlin.math.min

/**
 * هندسة لوحة «المكعّب» كلها في **مكان واحد** (كل القيم dp).
 *
 * لماذا وُجدت
 * -----------
 * كانت المقاسات تُحسب متفرّقة داخل الدالة المرسومة بـ`maxOf(40f, u * 7.6f)` وأشباهها، فحدٌّ أدنى
 * ثابت لعنصر يغلب مقياس الشاشة في ارتفاع ٤٠٠dp، وتتراكب الأعمدة (زرّ العرض فوق دوائر الأعلى،
 * وشبكة البلاطات تقصّها حافة الشاشة، والحبوب تقطع نصوصها). وحين تتفرّق القياسات لا يستطيع أحد أن
 * يقول «هل يتراكب عنصران؟» إلا بتشغيل الجهاز.
 *
 * فالقاعدة هنا: كل بُعد دالة في (عرض، ارتفاع) وحدهما، والأعمدة تُوزَّع بعرض مضمون لا يتقاطع
 * (يُختبر في `CubeLayoutTest`)، والرأسي يتقاسم الارتفاع المتاح فيُحسب طول المقياس مما يتبقّى
 * بدل أن يُفترض فيتجاوز الحافة السفلى.
 */
data class CubeLayout(
    /** وحدة القياس الأساس: أصغر نسبتي الارتفاع والعرض، فتتّسع الواجهة بتناسب لا بقفزات. */
    val u: Float,
    val compact: Boolean,
    val edge: Float,
    val topPad: Float,
    val btn: Float,
    /** خط الفصل تحت صفّ الدوائر (كما في المرجع)، وبعده يبدأ الجسم. */
    val dividerY: Float,
    val bodyTop: Float,
    val bodyBottom: Float,
    // ── العمود الأيسر (بروفايلات الحرارة)
    val leftW: Float,
    val toggleH: Float,
    val cardH: Float,
    val gridCardH: Float,
    // ── الشبكة اليمنى
    val tile: Float,
    val colW: Float,
    val columns: Int,
    val colGap: Float,
    val rowGap: Float,
    val gridW: Float,
    val gridX: Float,
    // ── عمودا CPU / GPU
    val gaugeW: Float,
    val cpuX: Float,
    val gpuX: Float,
    val gaugeTop: Float,
    val numberSp: Float,
    val unitSp: Float,
    val labelSp: Float,
    val orbH: Float,
    val pillW: Float,
    val pillH: Float,
    val meterW: Float,
    val meterH: Float,
    // ── الأسفل
    val statusW: Float,
) {
    /** حافة العمود الأيسر اليمنى (0 إن كان مغلقًا). */
    val leftRight: Float get() = if (leftW > 0f) edge + leftW else 0f

    /** مدى عمود CPU أفقيًّا. */
    val cpuRange: ClosedFloatingPointRange<Float> get() = (cpuX - gaugeW / 2f)..(cpuX + gaugeW / 2f)

    /** مدى عمود GPU أفقيًّا. */
    val gpuRange: ClosedFloatingPointRange<Float> get() = (gpuX - gaugeW / 2f)..(gpuX + gaugeW / 2f)

    /** الارتفاع المتاح لشبكة اليمين ولعمود اليسار. */
    val bodyHeight: Float get() = (bodyBottom - bodyTop).coerceAtLeast(0f)
}

/**
 * هندسة العمودين المائلين (مقيسة من لقطة المرجع الحيّة لا مخمَّنة): القمّة عند ٠٫٢٨٥ من العرض،
 * ثم ميل للخارج بنسبة من **الارتفاع** إلى رأس السهم، ثم ميل أطول عائد إلى القاع.
 *
 * وكونها نسبًا من الارتفاع لا العرض هو المقصود: ميل الشريط في المرجع واحد على الشاشات العريضة
 * والضيّقة، وما يتغيّر هو موضع القمّة أفقيًّا فقط. وتُشارك الرسمَ والاختبارَ نفسَ الدالة فلا
 * يتفرّق «أين الشريط؟» بين ما يُرسم وما يُفحص.
 */
object CubeGeometry {
    const val TOP_X = 0.285f
    const val LEAN_OUT = 0.15f
    const val LEAN_BACK = 0.34f
    const val TIP_Y = 0.315f
    const val HALF = 0.045f

    /** مركز الشريط الأيسر أفقيًّا عند الارتفاع [y] (الأيمن مرآته: `w - x`). */
    fun centerX(w: Float, h: Float, y: Float): Float {
        val tipY = TIP_Y * h
        val x0 = TOP_X * w
        val x1 = x0 + LEAN_OUT * h
        val x2 = x1 - LEAN_BACK * h
        val clamped = y.coerceIn(0f, h)
        return if (clamped <= tipY) x0 + (x1 - x0) * (clamped / tipY)
        else x1 + (x2 - x1) * ((clamped - tipY) / (h - tipY))
    }

    /** حافة الشريط الأيسر **الخارجية** (جهة العمود) عند [y]. */
    fun leftEdge(w: Float, h: Float, y: Float): Float = centerX(w, h, y) - HALF * h
}

object CubeLayoutMath {

    /** ثوابت رأسية لعمود المقياس: مجموع ما فوقه من عناصر ثابتة الطول (تقدير مقصود، محافِظ). */
    private const val ICON_BELOW_METER = 22f
    private const val CHEVRON = 14f

    /**
     * @param leftOpen هل العمود الأيسر مفتوح؟ (يؤثّر في موضع عمود CPU كي لا يتقاطعا)
     * @param wideLeft هل العمود الأيسر في وضع التفاصيل (أعرض)؟
     */
    fun of(widthDp: Float, heightDp: Float, leftOpen: Boolean = true, wideLeft: Boolean = false): CubeLayout {
        val w = max(widthDp, 320f)
        val h = max(heightDp, 240f)
        val u = min(h / 100f, w / 190f).coerceIn(2.4f, 8f)
        val compact = CockpitModel.isCompact(w)

        val edge = (u * 4.6f).coerceIn(12f, 28f)
        val btn = (u * 10f).coerceIn(38f, 56f)
        val topPad = (u * 2.6f).coerceIn(6f, 16f)
        val dividerY = topPad + btn + (u * 1.8f).coerceIn(6f, 12f)
        val bodyTop = dividerY + (u * 1.6f).coerceIn(6f, 12f)
        val bodyBottom = h - (u * 2.6f).coerceIn(8f, 14f)

        val baseLeft = (w * 0.125f).coerceIn(96f, 156f)
        val leftW = when {
            !leftOpen -> 0f
            wideLeft -> max(baseLeft, 176f)
            else -> baseLeft
        }
        val toggleH = (u * 8f).coerceIn(30f, 40f)
        val cardH = (u * 10f).coerceIn(38f, 50f)
        val gridCardH = (u * 11.5f).coerceIn(48f, 62f)

        val tile = (u * 10.4f).coerceIn(40f, 60f)
        val colW = tile + 14f
        val columns = if (compact) 1 else 2
        val colGap = 6f
        val rowGap = (u * 1.2f).coerceIn(4f, 10f)
        val gridW = colW * columns + colGap * (columns - 1)
        val gridX = w - edge - gridW

        val gaugeW = if (compact) w * 0.30f else (w * 0.15f).coerceIn(104f, 176f)
        val leftRight = if (leftW > 0f) edge + leftW else 0f
        val cpuX = if (compact) {
            w * 0.30f
        } else {
            max(w * 0.23f, if (leftRight > 0f) leftRight + 6f + gaugeW / 2f else 0f)
        }
        val gpuX = if (compact) w * 0.62f else w - cpuX
        val gaugeTop = bodyTop

        val numberSp = (u * 6.4f).coerceIn(24f, 44f)
        val unitSp = (u * 2.5f).coerceIn(10f, 15f)
        val labelSp = (u * 2.8f).coerceIn(11f, 16f)
        val orbH = (u * 8.6f).coerceIn(30f, 64f)
        val pillW = if (compact) gaugeW * 0.92f else (w * 0.105f).coerceIn(84f, 132f)
        val pillH = (u * 7f).coerceIn(28f, 40f)
        val meterW = (u * 11f).coerceIn(44f, 64f)

        // ما فوق المقياس: رقم + قرص + وسم + فجوة + حبّة + سهم + فجوة، وتحته أيقونته.
        val above = numberSp * 1.15f + orbH + labelSp * 1.3f + u * 1.6f + pillH + CHEVRON + u * 1.4f
        val room = bodyBottom - gaugeTop - above - ICON_BELOW_METER
        // والسقف لا ينزل تحت الأرضية: على شاشة ضيّقة (عرض < ٥٠٧dp ⇒ `u = 2.4` ⇒ `u * 24 = 57.6`)
        // كان `coerceIn(64f, 57.6f)` يرمي «empty range» **عند كل رسم** — عطب ينهار به الرسم لا
        // يبدو خطأً بصريًّا. (قِيس: `CubeLayoutMath.of(360f, 800f)` رمى `IllegalArgumentException`.)
        val meterH = room.coerceIn(64f, (u * 24f).coerceAtLeast(64f))

        val statusW = (w * 0.34f).coerceIn(180f, 360f)

        return CubeLayout(
            u = u, compact = compact, edge = edge, topPad = topPad, btn = btn,
            dividerY = dividerY, bodyTop = bodyTop, bodyBottom = bodyBottom,
            leftW = leftW, toggleH = toggleH, cardH = cardH, gridCardH = gridCardH,
            tile = tile, colW = colW, columns = columns, colGap = colGap, rowGap = rowGap,
            gridW = gridW, gridX = gridX,
            gaugeW = gaugeW, cpuX = cpuX, gpuX = gpuX, gaugeTop = gaugeTop,
            numberSp = numberSp, unitSp = unitSp, labelSp = labelSp, orbH = orbH,
            pillW = pillW, pillH = pillH, meterW = meterW, meterH = meterH,
            statusW = statusW,
        )
    }
}
