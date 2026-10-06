/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import java.util.Locale

/**
 * منطق نقيّ للوبي الجديد (بطاقات Carousel) — يُقاس بلا جهاز. الرسم نفسه في `LobbyCarousel`
 * و`GameLobbyScreen` ولا يعرف هذه الأرقام.
 *
 * ما هنا يخدم قاعدة واحدة من `DESIGN.md`: **الرقم الذي لا يملكه التطبيق يُعرض شرطةً لا صفرًا**
 * (ADR-07) — لذلك كل دالة قراءة تُرجع `null` للمجهول، والواجهة وحدها تحوّله إلى «—».
 */
object LobbyModel {
    /** حرارة البطارية المقروءة من النظام بعشر الدرجة؛ خارج هذا المدى قراءةٌ معطوبة لا درجة حرارة. */
    private const val TEMP_MIN_TENTHS = -300
    private const val TEMP_MAX_TENTHS = 1500

    /** أقلّ وزن نسبيّ للّون المميَّز لنقبله؛ دونه تكون الأيقونة رماديّة/سوداء فنعود لأحمر العلامة. */
    private const val MIN_RELATIVE_WEIGHT = 0.03f

    /**
     * يُعيد مؤشّر الصفحة التي يجب أن يقف عليها الـCarousel: موضع الحزمة المحدَّدة، وإلا الأولى.
     * القائمة الفارغة تُرجع صفرًا (لا استثناء)، فالمتصل يفحص الفراغ بنفسه.
     */
    fun targetPage(packages: List<String>, selected: String?): Int {
        if (selected == null) return 0
        val index = packages.indexOf(selected)
        return if (index >= 0) index else 0
    }

    /** «34.5°C»، و`null` إن كانت القراءة غائبة أو خارج المدى المعقول. */
    fun temperatureText(tenthsOfCelsius: Int?): String? {
        if (tenthsOfCelsius == null) return null
        if (tenthsOfCelsius < TEMP_MIN_TENTHS || tenthsOfCelsius > TEMP_MAX_TENTHS) return null
        return "%.1f°C".format(Locale.US, tenthsOfCelsius / 10f)
    }

    /**
     * ذاكرة حرّة بالجيجابايت بأرقام لاتينية في كل اللغات (قراءات العتاد لا تتبدّل أرقامها)،
     * و`null` للقراءة غير المعقولة (سالبة أو صفر).
     */
    fun freeMemoryText(availBytes: Long?): String? {
        if (availBytes == null || availBytes <= 0L) return null
        return "%.1f GB".format(Locale.US, availBytes / 1_000_000_000f)
    }

    /**
     * اللون المميِّز لأيقونة اللعبة كـ `0xRRGGBB`، أو `null` إن لم يكن فيها لون ظاهر.
     *
     * متوسّط موزون: الوزن = تشبّع² × سطوع، فالبكسلات الرماديّة والسوداء والشفّافة لا تُعدّ
     * (وإلا خرجت كل أيقونة بيضاء الخلفية «رماديّة»). `step` يأخذ كل بكسل رقم `step` فقط — كافٍ
     * للون ويُبقي الكلفة ثابتة على أي حجم أيقونة.
     */
    fun dominantRgb(argb: IntArray, step: Int = 3): Int? {
        val stride = step.coerceAtLeast(1)
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var weightSum = 0.0
        var sampled = 0
        var i = 0
        while (i < argb.size) {
            val p = argb[i]
            i += stride
            sampled += 1
            val alpha = (p ushr 24) and 0xFF
            if (alpha < OPAQUE_ENOUGH) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max < MIN_BRIGHTNESS) continue
            val saturation = (max - min) / max.toFloat()
            if (saturation < MIN_SATURATION) continue
            val weight = (saturation * saturation * (max / 255f)).toDouble()
            rSum += r * weight
            gSum += g * weight
            bSum += b * weight
            weightSum += weight
        }
        if (sampled == 0 || weightSum / sampled < MIN_RELATIVE_WEIGHT) return null
        val r = (rSum / weightSum).toInt().coerceIn(0, 255)
        val g = (gSum / weightSum).toInt().coerceIn(0, 255)
        val b = (bSum / weightSum).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    /**
     * يرفع سطوع اللون حتى تبلغ أكبر قناة فيه [floor] (دون تغيير الصبغة) — لون داكن لا يُرى كتوهّج
     * على أرضية سوداء. الألوان الأفتح أصلًا تبقى كما هي.
     */
    fun lift(rgb: Int, floor: Int = LIFT_FLOOR): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val max = maxOf(r, g, b)
        if (max <= 0 || max >= floor) return rgb
        val k = floor / max.toFloat()
        val nr = (r * k).toInt().coerceIn(0, 255)
        val ng = (g * k).toInt().coerceIn(0, 255)
        val nb = (b * k).toInt().coerceIn(0, 255)
        return (nr shl 16) or (ng shl 8) or nb
    }

    private const val OPAQUE_ENOUGH = 200
    private const val MIN_BRIGHTNESS = 40
    private const val MIN_SATURATION = 0.25f
    private const val LIFT_FLOOR = 190
}
