/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **نموذج المعادل**: النطاقات كما أعلنتها المنصّة، ومنحناها، وتحويلاتها — كلّه صافٍ يُقاس على
 * JVM بلا `android.jar` (`AQ-03`؛ ومنحنًى يُقاس على JVM لا يُشاهد على جهاز فقط: الخطّ يُرسم من أرقام،
 * والحكم على الأرقام لا على الرسم).
 *
 * **ولا رقم من عندنا:** عدد النطاقات وتردداتها ومدى كسبها **كلّها من `Equalizer` نفسه**؛ و`null` تعني
 * «لم تُقرأ» — والفرق بينها وبين الصفر هو الفرق بين «لم أعرف» و«الوسط». فجهازٌ لا يُعلن مدى كسبٍ لا
 * يُعرض له شريط، بل يُقال إنّ المدى غير مقروء (ADR-07).
 *
 * **والمنحنى «مستهدف» لا «مقيس» — وهذا يُكتب في الشاشة صراحةً:** ما نرسمه هو ما **طلبنا** كتابته
 * (الكسب لكل نطاق)، لا استجابةً مقيَّلة بالتقاط الصوت (والتقاط مرفوض في الخطّة §4). فتسميته «استجابة
 * مقيسة» كذبٌ، وتسميته «المنحنى المستهدف» صدقٌ يفهمه المستخدم.
 *
 * **ومبدأ الجودة المأخوذ من مرجعنا المفهوميّ:** المعادل يجب أن يُعطي **استجابة مستوية حين يتساوى الكسب** —
 * ولهذا يُقاس `eqIsFlat` هنا بدل أن يُدَّعى.
 */
package nd.max.core.audio

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10

/**
 * نطاق واحد كما أعلنته المنصّة.
 *
 * @param centerHz مركز النطاق (`getCenterFreq`) — و`null` لا الصفر حين لا يُقرأ.
 * @param levelMb الكسب الحاليّ بالميلي‑ديسيبل، و`null` غياب قراءة.
 */
data class AudioEqBand(
    val index: Int,
    val centerHz: Int?,
    val rangeLowHz: Int?,
    val rangeHighHz: Int?,
    val levelMb: Int?,
    val levelMinMb: Int?,
    val levelMaxMb: Int?,
) {
    /** هل يمكن تحريك هذا النطاق فعلًا؟ — السؤال الذي يُعطَّل به الشريط بسببٍ لا بصمت. */
    val isWritable: Boolean
        get() = levelMb != null && levelMinMb != null && levelMaxMb != null && levelMaxMb > levelMinMb
}

/** لقطة المعادل كما تُقرأ مرّة واحدة. */
data class AudioEqSnapshot(
    val bands: List<AudioEqBand>,
    /** أسماء الأنماط التي أعلنتها المنصّة (`getPresetName`) — والقائمة الفارغة قراءة: «لا أنماط». */
    val presets: List<String>,
    /** موضع النمط الحاليّ في [presets]، و`null` حين لا يُقرأ. */
    val currentPreset: Int?,
)

/**
 * مدى الشريط: من أعلنته المنصّة، **موسَّعًا بخطوة واحدة** على الطرفين إن كان صفريّ العرض.
 *
 * ولماذا لا نُصلح عرضًا صفريًّا بـ`0..1` مصنوع: نطاقٌ صفريّ يعني «هذا النطاق لا يقبل تحريكًا» — فالشريط
 * يُعطَّل (`isWritable = false`) ولا يُعرض، ولا يُخترع له مدى.
 */
fun eqSliderRange(band: AudioEqBand): ClosedFloatingPointRange<Float>? {
    val min = band.levelMinMb ?: return null
    val max = band.levelMaxMb ?: return null
    if (max <= min) return null
    return min.toFloat()..max.toFloat()
}

/** الكسب الحاليّ ← نسبة على الشريط — و`null` حين لا قراءة أو مدى غير صالح (ولا تُعاد ٠). */
fun eqLevelFraction(band: AudioEqBand): Float? {
    val range = eqSliderRange(band) ?: return null
    val level = band.levelMb ?: return null
    return ((level - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
}

/** نسبة الشريط ← كسب بالميلي‑ديسيبل، **مُقرَّبة إلى صحيح ومقيَّدة بالمدى** فلا تخرج كتابةٌ عن المُعلَن. */
fun eqLevelFromFraction(band: AudioEqBand, fraction: Float): Int? {
    val range = eqSliderRange(band) ?: return null
    val span = range.endInclusive - range.start
    return (range.start + (fraction.coerceIn(0f, 1f) * span)).toInt()
        .coerceIn(range.start.toInt(), range.endInclusive.toInt())
}

/**
 * نقطة على المنحنى: `x` أفقيًّا (٠ يسارًا، لوغاريتم التردد)، و`y` رأسيًّا (**+ أعلى**، ٠ = استواء)،
 * و[levelMb] الكسب المُعلَن.
 *
 * **و[index] معها عمدًا:** فبها يُسحب النطاق الذي كُتب فعلًا، لا الذي وقع في ترتيب الرسم — وقد كانت
 * نسخةٌ سابقة من هذا المنحنى تُعيد النقاط مرتَّبةً بالتردد بلا فهرس، فتصير الكتابة على النطاق الخطأ
 * حين لا يوافق ترتيبُ المنصّة ترتيبَ الرسم.
 */
data class EqCurveNode(val index: Int, val x: Float, val y: Float, val levelMb: Int)

/**
 * **مقياس المنحنى** — موضعٌ وكسبٌ في رمزٍ واحد، يقرأه **الرسم والسحب معًا**.
 *
 * **ولماذا وُجد:** كان المنحنى يُرسم بمقياسٍ ويُحرَّر بمقياسٍ آخر (أو لا يُحرَّر أصلًا)، فالنقطة التي
 * تُسحب ليست النقطة التي تُرى. وفي مرجعنا المفهوميّ كان السحب `gain / 150f` و`norm * 150` — أي مدًى
 * **مفترضًا** (±١٥dB) لا يُقرأ من الجهاز. والجهاز الذي يُعلن `-1200..1200` كان يُحرَّر فيه المنحنى على
 * مقياسٍ ليس مقياسه، فينزلق النطاق تحت الإصبع.
 *
 * **والمقياس من المُعلَن لا من رقم:** [yScaleMb] أقصى مقدار كسب أعلنته المنصّة، و[scaleDeclared] تقول
 * هل أُعلن أصلًا. وبلا إعلانٍ يُرسم الشكل **نسبيًّا** (يُعاير على أكبر كسبٍ مقروء) ويُعرض للقراءة فقط —
 * لأن السحب بلا مدًى مُعلَن يعني اختراع المدى. وهذا حدُّنا المُعلَن لا نقصٌ صامت.
 *
 * @param xs موضع كل نطاق أفقيًّا `0f..1f` **بنفس ترتيب** النطاقات المُدخَلة (لا بترتيب الرسم)، فلا
 *   يفترق فهرسُ السحب عن فهرس الكتابة. و`null` لنطاقٍ لا يُعلن تردد مركز — لا نقطة مُخترعة.
 */
data class EqCurveMetrics(
    val xs: List<Float?>,
    val yScaleMb: Int,
    val scaleDeclared: Boolean,
) {
    /** هل يُرسم منحنى؟ — نقطتان على الأقلّ لهما موضع، ومقياسٌ غير صفريّ. */
    val isDrawable: Boolean get() = yScaleMb > 0 && xs.count { it != null } >= 2

    /** وهل يُسحب؟ — لا سحب بلا مدًى **مُعلَن**، ولو رُسم. */
    val isDraggable: Boolean get() = scaleDeclared && isDrawable
}

/**
 * مواضع النطاقات أفقيًّا في **فضاء لوغاريتميّ على التردد** — كما تسمعه الأذن لا كما يُساوى خطيًّا.
 *
 * والوصف يُبنى من **ترددات المركز**، ومن لا مركز له لا موضع له (لا نقطة مُخترعة) — **ولا يُسقط من
 * المدى**: نطاقٌ أعلنته المنصّة ولم تُقرأ له قيمة يبقى موضعًا على المحور، فلا تتزاحم النقاط حوله.
 */
private fun eqLogPositions(bands: List<AudioEqBand>): List<Float?> {
    val logs = bands.map { band -> band.centerHz?.takeIf { it > 0 }?.let { log10(it.toFloat()) } }
    val known = logs.filterNotNull()
    if (known.isEmpty()) return List(bands.size) { null }

    // **والمدى يُقاس في الفضاء نفسه الذي يُقاس فيه الموضع** — لوغاريتميًّا. وطرحُ هرتزين خطيًّا
    // (`1000 - 100`) ثمّ القسمة على فرقٍ لوغاريتميّ كان يُرجع `0.0011` بدل `1` — أي أنّ المنحنى
    // ينضغط عند يساره، وهو عطبٌ صنعه القياس لا الكود، وقد أمسكه اختبار JVM في جولةٍ سابقة.
    val low = known.min()
    val span = known.max() - low
    return logs.map { log ->
        when {
            log == null -> null
            span <= 0f -> 0.5f
            else -> ((log - low) / span).coerceIn(0f, 1f)
        }
    }
}

/** يبني المقياس من نطاقات المنصّة: المواضع، وأقصى كسب مُعلَن، وهل أُعلن مدًى. */
fun eqCurveMetricsOf(bands: List<AudioEqBand>): EqCurveMetrics {
    val xs = eqLogPositions(bands)
    val declared = bands.mapNotNull { band ->
        val min = band.levelMinMb
        val max = band.levelMaxMb
        if (min == null || max == null || max <= min) null else maxOf(abs(min), abs(max))
    }
    if (declared.isNotEmpty()) {
        return EqCurveMetrics(xs = xs, yScaleMb = declared.max(), scaleDeclared = true)
    }
    // وبلا إعلانٍ: يُعاير على أكبر كسبٍ **مقروء** — فيُرى الشكل ولا يُسحب، ولا يُدَّعى مدًى لم يُعلَن.
    val readable = bands.mapNotNull { it.levelMb }.map { abs(it) }
    return EqCurveMetrics(xs = xs, yScaleMb = readable.maxOrNull() ?: 0, scaleDeclared = false)
}

/**
 * **نقاط المنحنى المستهدف** — من نفس المقياس الذي يُسحب عليه، فلا يفترق المرسوم عن المسحوب.
 *
 * والخطّ **مستهدفٌ لا مقيس** وهذا يُكتب في الشاشة: ما نرسمه هو ما **طلبنا** كتابته، لا استجابةً
 * مقيَّلة بالتقاط الصوت. ومن لا مقام له لا يُرسم (فلا يُرسم صفرٌ مكان قيمة لم تُقرأ — ADR-07).
 */
fun eqCurveNodesOf(bands: List<AudioEqBand>, metrics: EqCurveMetrics = eqCurveMetricsOf(bands)): List<EqCurveNode> {
    if (metrics.yScaleMb <= 0) return emptyList()
    return bands.mapIndexedNotNull { position, band ->
        val x = metrics.xs.getOrNull(position) ?: return@mapIndexedNotNull null
        val level = band.levelMb ?: return@mapIndexedNotNull null
        EqCurveNode(
            index = band.index,
            x = x,
            y = (level.toFloat() / metrics.yScaleMb).coerceIn(-1f, 1f),
            levelMb = level,
        )
    }
}

/** الكسب ↦ ارتفاع `-1f..1f` على المقياس (`+` أعلى الخطّ). و`null` حين لا قراءة ولا مقياس. */
fun eqCurveHeightOf(band: AudioEqBand, metrics: EqCurveMetrics): Float? {
    if (metrics.yScaleMb <= 0) return null
    val level = band.levelMb ?: return null
    return (level.toFloat() / metrics.yScaleMb).coerceIn(-1f, 1f)
}

/**
 * ارتفاع `-1f..1f` ↦ كسب بالميلي‑ديسيبل — **مقيَّدًا بمدى النطاق المُعلَن**.
 *
 * و`null` هي الجواب على نطاقٍ لا يُعلن مدًى — ولا يُخترع `±1500`، لأن ما لا يُقرأ لا يُكتب (ADR-07).
 * والسحب خارج الإطار لا يُنتج قيمةً خارج المُعلَن: يُقيَّد بالطرفين لا يُرفض.
 */
fun eqLevelFromCurveHeight(band: AudioEqBand, metrics: EqCurveMetrics, height: Float): Int? {
    if (!metrics.scaleDeclared || metrics.yScaleMb <= 0) return null
    val min = band.levelMinMb ?: return null
    val max = band.levelMaxMb ?: return null
    if (max <= min) return null
    return (height.coerceIn(-1f, 1f) * metrics.yScaleMb).toInt().coerceIn(min, max)
}

/**
 * أقرب نطاق **قابل للتحريك** لموضعٍ أفقيّ `0f..1f` — و`null` حين لا نطاق (فلا سحب على فراغ).
 *
 * والنطاق المعطَّل لا يُختار ولو كان الأقرب: يُتجاوز إلى التالي، فلا يُسنَد السحب إلى نطاقٍ لا يقبل كتابة.
 */
fun eqNearestDraggableBandIndex(bands: List<AudioEqBand>, metrics: EqCurveMetrics, x: Float): Int? {
    if (!metrics.isDraggable) return null
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    bands.forEachIndexed { position, band ->
        if (!band.isWritable) return@forEachIndexed
        val bandX = metrics.xs.getOrNull(position) ?: return@forEachIndexed
        val distance = abs(bandX - x)
        if (distance < bestDistance) {
            bestDistance = distance
            best = band.index
        }
    }
    return best
}

/**
 * **استواء الاستجابة:** كل نطاق يُعلن الكسب نفسه ⇒ المنحنى مستوٍ.
 *
 * وهذا **مبدأ الجودة المأخوذ من مرجعنا**: معادلٌ لا يعطي استجابة مستوية عند تساوي الكسب معادلٌ يشوّه
 * بلا سبب. ويُقاس على **القيم المعلَنة** لا على الشكل المرسوم، فالحكم على البيانات لا على الصورة.
 */
fun eqIsFlat(bands: List<AudioEqBand>): Boolean {
    val levels = bands.mapNotNull { it.levelMb }
    if (levels.isEmpty()) return false
    return levels.distinct().size == 1
}

/**
 * تردد النطاق مقروءًا للمستخدم: `60 Hz` · `1.2 kHz`.
 *
 * والصياغة **هنا لا في الشاشة** — لأنها تُقاس: تقريبٌ خطأ في عرض الترددات يجعل رأس النطاق يقيس رقمًا
 * غير الذي كُتب، وهو عطبٌ لا يمسكه مُصرّف. و`Locale.US` صريح فالفاصلة العشرية لا تتبدّل بلغة الجهاز.
 */
fun eqFormatFrequency(hz: Int?): String? {
    if (hz == null || hz <= 0) return null
    if (hz < 1000) return "$hz Hz"
    val khz = hz / 1000.0
    val text = if (khz == kotlin.math.floor(khz)) khz.toInt().toString() else String.format(Locale.US, "%.1f", khz)
    return "$text kHz"
}

/**
 * الكسب مقروءًا: `+3.5 dB` · `-2 dB` · `0 dB`.
 *
 * **والرمز يُعلن إشارته صراحةً** (`+`) — لأنّ `3 dB` و`-3 dB` يفترقان في المعنى لا في القراءة، وترْك
 * الموجب بلا رمز يقرؤه المستخدم ناقصًا على شاشة صغيرة.
 */
fun eqFormatLevel(levelMb: Int?): String? {
    if (levelMb == null) return null
    val db = levelMb / 100.0
    val text = if (db == kotlin.math.floor(db)) db.toInt().toString() else String.format(Locale.US, "%.1f", db)
    return if (levelMb > 0) "+$text dB" else "$text dB"
}
