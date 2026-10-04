/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **نموذج سمات المازج** (`AQ-06`): معدّل العيّنة والقناة والترميز والسلوك (bit-perfect).
 * صافٍ يُقاس على JVM.
 *
 * **ولماذا هو «دقّة المخرج» عندنا:** المنصّة تُتيح طلب ما يريده المازج من معدّل وقناة وترميز لكل جهاز
 * (`AudioMixerAttributes`)، وما يُطلب يُقرأ بعده (`getPreferredMixerAttributes`) — فالسطر في الشاشة
 * **قياسٌ بعد كتابة** لا وعدًا. و«bit-perfect» هنا **سلوكٌ تعلنه المنصّة** (`MIXER_BEHAVIOR_BIT_PERFECT`)
 * لا ادّعاءً منّا أنّ المسار مثاليّ: نحن نقول ما قبلته المنصّة، والمنصّة تقول الباقي.
 *
 * **والترميز رمزٌ نصّيّ لا رقم:** الخريطة من ثوابت `AudioFormat` تُبنى في المحرّك (فهي وحدها تعرف
 * القيم)، وهذا الملفّ يقارن الرموز ويعرضها — فيُقاس على JVM بلا `android.jar`.
 */
package nd.max.core.audio

/** سمة مازج واحدة — وكل حقل اختياريّ، فالغياب `null` لا صفر (ADR-07). */
data class AudioMixerAttribute(
    val sampleRateHz: Int?,
    val channelCount: Int?,
    val encodingToken: String?,
    val bitPerfect: Boolean,
)

/**
 * الوصف المقروء: `48 kHz · 2 ch · PCM 24-bit · bit-perfect`.
 *
 * **والترتيب ثابت أبدًا** (معدّل ← قناة ← ترميز ← سلوك) فلا يقفز السطر بين صفَّين متساويين؛ وما لم
 * يُقرأ **يُسقط من الوصف** ولا يُكتب مكانه `0` ولا `?`.
 */
fun audioMixerLabel(attribute: AudioMixerAttribute): String {
    val parts = mutableListOf<String>()
    attribute.sampleRateHz?.takeIf { it > 0 }?.let { hz ->
        parts += if (hz % 1000 == 0) "${hz / 1000} kHz" else "$hz Hz"
    }
    attribute.channelCount?.takeIf { it > 0 }?.let { parts += "$it ch" }
    attribute.encodingToken?.let { parts += encodingLabel(it) }
    if (attribute.bitPerfect) parts += "bit-perfect"
    return if (parts.isEmpty()) "" else parts.joinToString(" · ")
}

/** اسم الترميز كما يُعرض — والرمز المجهول يُعرض كما هو، فلا يُلحق بأقرب شبيه. */
fun encodingLabel(token: String): String = when (token) {
    AUDIO_ENCODING_PCM_16 -> "PCM 16-bit"
    AUDIO_ENCODING_PCM_24 -> "PCM 24-bit"
    AUDIO_ENCODING_PCM_32 -> "PCM 32-bit"
    AUDIO_ENCODING_PCM_FLOAT -> "PCM float"
    AUDIO_ENCODING_PCM_8 -> "PCM 8-bit"
    else -> token
}

const val AUDIO_ENCODING_PCM_8 = "pcm_8bit"
const val AUDIO_ENCODING_PCM_16 = "pcm_16bit"
const val AUDIO_ENCODING_PCM_24 = "pcm_24bit"
const val AUDIO_ENCODING_PCM_32 = "pcm_32bit"
const val AUDIO_ENCODING_PCM_FLOAT = "pcm_float"

/**
 * هل طابق ما صار ما طلبناه؟
 *
 * **والمقارنة على الحقول المُعلَنة فقط:** حقلٌ لم نطلبه (`null` في الطلب) **لا يُفشل التطابق** — لأنّ
 * عدم طلبه يعني «لا رأي لنا فيه»، وأنّ المنصّة اختارت عنه شيئًا ليس رفضًا لطلبنا.
 */
fun audioMixerMatches(requested: AudioMixerAttribute, actual: AudioMixerAttribute): Boolean {
    if (requested.sampleRateHz != null && requested.sampleRateHz != actual.sampleRateHz) return false
    if (requested.channelCount != null && requested.channelCount != actual.channelCount) return false
    if (requested.encodingToken != null && requested.encodingToken != actual.encodingToken) return false
    if (requested.bitPerfect && !actual.bitPerfect) return false
    return true
}

/**
 * بصمة السمة — **نصّ واحد يُكتب ويُقرأ منه**، فيقارنها المحكِّم.
 *
 * **ولماذا بصمة لا حقول:** المحكِّم يقارن `String`، ولو مرّرنا الوصف المقروء لاختلف بتغيّر الترجمة أو
 * الترتيب. فالبصمة **ثابتة وصريحة** (`ك=قيمة`، ومفصولة بفاصلة منقوطة)، تُبنى منها المقارنة المتسامحة في
 * [`audioMixerMatches`] بعد إعادة التحليل.
 */
fun audioMixerSignature(attribute: AudioMixerAttribute): String = listOf(
    "rate=${attribute.sampleRateHz ?: ""}",
    "ch=${attribute.channelCount ?: ""}",
    "enc=${attribute.encodingToken ?: ""}",
    "bp=${if (attribute.bitPerfect) 1 else 0}",
).joinToString(";")

/** عكس [`audioMixerSignature`] — وقيمةٌ مفقودة تعود `null` لا صفرًا (فالغياب يُحفظ غيابًا). */
fun audioMixerOfSignature(text: String?): AudioMixerAttribute? {
    if (text.isNullOrBlank()) return null
    val parts = text.split(';').associate { part ->
        val index = part.indexOf('=')
        if (index < 0) "" to "" else part.substring(0, index) to part.substring(index + 1)
    }
    if (parts.isEmpty() || parts.keys.none { it.isNotBlank() }) return null
    return AudioMixerAttribute(
        sampleRateHz = parts["rate"]?.toIntOrNull(),
        channelCount = parts["ch"]?.toIntOrNull(),
        encodingToken = parts["enc"]?.takeIf { it.isNotBlank() },
        bitPerfect = parts["bp"] == "1",
    )
}

/**
 * صياغة الجهارة بالديسيبل — **وهي المقياس الصادق** لأنّ الدرجة (`index`) ليست وحدة صوت.
 *
 * والمنصّة تُعيد `-Infinity` حين يكون الدفق صامتًا أو مكتومًا (`getStreamVolumeDb` تعود
 * `Float.NEGATIVE_INFINITY`)، و`-Infinity` **ليست قيمة تُعرض** فتتحوّل `null` وتُقال «—» لا `-∞ dB`.
 */
fun audioDbFormat(db: Float?): String? {
    if (db == null) return null
    if (!db.isFinite()) return null
    // والديسيبل الصحيح يُعرض بلا `.0` (وهو تجميل لا يمس الحكم، والصياغة تُقاس باختبار).
    val rounded = (db * 10f).toInt() / 10f
    val text = if (rounded == kotlin.math.floor(rounded.toDouble()).toFloat()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
    return "$text dB"
}
