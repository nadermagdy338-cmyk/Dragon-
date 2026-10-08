/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الوقت المتبقي للبطارية — **تقدير يُكتب فقط حين يكفيه القياس** (طلب المالك: لا أرقام بلا مصدر).
 *
 * المعادلة: السعة المتبقية (سعة التصميم × النسبة) مقسومة على تيار التفريغ المقاس.
 * التيار القريب من الصفر ضجيج قراءة فلا يُحسب منه وقت، وما يتجاوز ٩٩ ساعة ليس تقديرًا مفيدًا.
 */
package nd.max.ui.mainscreens

internal object HomeTimeLeftModel {
    /** أقلّ تيار تفريغ يُعدّ قياسًا لا ضجيجًا (بالمللي أمبير). */
    const val MIN_DISCHARGE_MA = 100

    /** أعلى وقت يُعرض: ما فوقه ليس تقديرًا مفيدًا. */
    const val MAX_MINUTES = 99 * 60

    /**
     * دقائق التفريغ المتبقية تقديرًا، أو `null`.
     * التيار الموجب شحن والسالب تفريغ (انظر `DashboardState.batteryCurrentMa`).
     * سعة التصميم بوحدة µAh كما يكتبها النظام، والتيار بالمللي أمبير فيُحوَّل إلى µA.
     */
    fun minutesLeft(percent: Int?, designUah: Long?, currentMa: Int?, charging: Boolean): Int? {
        if (charging || percent == null || percent <= 0) return null
        if (designUah == null || designUah <= 0L) return null
        if (currentMa == null || currentMa >= 0) return null
        val drawMa = -currentMa
        if (drawMa < MIN_DISCHARGE_MA) return null
        val remainingUah = designUah * percent / 100L
        val drawUa = drawMa.toLong() * 1000L
        val minutes = remainingUah * 60L / drawUa
        return minutes.toInt().takeIf { it in 1..MAX_MINUTES }
    }
}
