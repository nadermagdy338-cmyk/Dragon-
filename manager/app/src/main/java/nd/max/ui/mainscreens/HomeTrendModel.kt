/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * اتجاه الحرارة في القراءات الحيّة — **سهمٌ يقول أين تتجه القراءة، لا أين هي فقط** (طلب المالك).
 *
 * النافذة عيّنات الدقيقة الأخيرة (٣٠ عيّنة بإيقاع القراءة). والفرق الأقل من درجة واحدة ثباتٌ،
 * لأن القراءة بالدرجة الكاملة، فكل تذبذب دونها ضجيج لا اتجاه.
 */
package nd.max.ui.mainscreens

/** اتجاه القراءة خلال النافذة. `UNKNOWN` حين لا تكفي عيّنتان: لا اتجاه قبل قياسين. */
internal enum class HomeTrend { UP, DOWN, STEADY, UNKNOWN }

internal object HomeTrendModel {
    /** عدد العيّنات في النافذة: ٣٠ عيّنة بإيقاع القراءة ≈ دقيقة. */
    const val WINDOW_SAMPLES = 30

    /** أقلّ فرق بالدرجات يُعدّ تغيّرًا، وما دونه ثباتًا. */
    const val STEADY_BAND_C = 1

    /** يضيف قيمة مقيسة ويُسقط الأقدم عند امتلاء النافذة. القيمة الغائبة لا تُضاف ولا تصير صفرًا. */
    fun append(trail: MutableList<Int>, value: Int?) {
        if (value == null) return
        trail.add(value)
        while (trail.size > WINDOW_SAMPLES) trail.removeAt(0)
    }

    /** الاتجاه بمقارنة أحدث عيّنة بأقدمها في النافذة. */
    fun direction(trail: List<Int>): HomeTrend {
        if (trail.size < 2) return HomeTrend.UNKNOWN
        val delta = trail.last() - trail.first()
        return when {
            delta >= STEADY_BAND_C -> HomeTrend.UP
            delta <= -STEADY_BAND_C -> HomeTrend.DOWN
            else -> HomeTrend.STEADY
        }
    }
}
