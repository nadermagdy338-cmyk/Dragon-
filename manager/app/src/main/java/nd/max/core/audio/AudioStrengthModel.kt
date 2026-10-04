/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **نموذج المؤثّرات البسيطة** (`AQ-05`): تعزيز الجهير · المحيط · تعزيز الجهارة · الالتفاف
 * المُعدّ مسبقًا. صافٍ يُقاس على JVM.
 *
 * **والصدق في تسمية الوحدة:** `LoudnessEnhancer` **ليس تسوية جهارة (LUFS)** — إنه **تعزيز** كسبٍ
 * بالديسيبل على المخرج، فلا يُسمّى في الشاشة «تسوية» لأنّ التسوية الحقيقية تحتاج **قياس الدفق**،
 * وقياسه = التقاط، والالتقاط مرفوض في الخطّة §4. وهذا الفرق يُكتب في الشاشة لا هنا فقط.
 *
 * **ومدى القوّة (`0..1000`) من المنصّة بالاسم لا من ذاكرتنا:** `BassBoost`/`Virtualizer` تُعرّفان
 * سلّمًا مجرّدًا مقيّدًا بـ`0..1000` في واجهتهما، و`getRoundedStrength` تُعيد المقيَّد فعلًا — فما
 * يُعرض بعد الكتابة هو ما ردّته المنصّة، ولو قيّدت.
 *
 * **والحد الأعلى لتعزيز الجهارة حدُّنا المُعلَن لا حدُّ العتاد:** المنصّة لا تُعلن مدًى
 * (`setTargetGain(int mB)` بلا `getRange`)، فالرقم هنا **سقف عرضٍ** نُعلنه صراحةً — ولهذا سُمّي `UI_`.
 */
package nd.max.core.audio

import kotlin.math.roundToInt

/** حدود المؤثّرات البسيطة — **حدُّنا المُعلَن** حين لا تُعلن المنصّة مدًى. */
object AudioStrengthBounds {
    /** سلّم المنصّة المجرّد لـ`BassBoost` و`Virtualizer` — الثابت في واجهتهما، لا اختراعًا. */
    const val PLATFORM_STRENGTH_MIN = 0
    const val PLATFORM_STRENGTH_MAX = 1000

    /** سقف عرض تعزيز الجهارة (بالملّي‑ديسيبل) — **حدُّ واجهتنا لا حدُّ العتاد**. */
    const val UI_LOUDNESS_MIN_MB = 0
    const val UI_LOUDNESS_MAX_MB = 1000
}

/**
 * قراءة مؤثّر قوّةٍ بسيط.
 *
 * @param supported `getStrengthSupported()` — و`null` حين لا تُقرأ (فلا يُعرض شريط على ادّعاء).
 * @param value القيمة المقروءة بعد الكتابة — وهي الحكم، لا ما طلبناه.
 */
data class AudioStrengthSnapshot(
    val kind: AudioEffectKind,
    val supported: Boolean?,
    val value: Int?,
    val presets: List<String> = emptyList(),
    val currentPreset: Int? = null,
) {
    /** هل يُسمح بمقبض؟ — ولا يُعرض مقبض لمؤثّر أعلن أنّ القوّة غير مدعومة عنده. */
    val isWritable: Boolean get() = supported == true && value != null
}

/**
 * القيمة ← نسبة على شريط الوحدة.
 *
 * و`null` — لا صفر — حين لا قراءة أو مدًى صفريّ: نسبةٌ مصنوعة من غياب قراءة تكذب على المستخدم.
 * ويُقرَّب الناتج من الجهتين (`min`···`max`) فيبقى داخل ما أُعلن.
 */
fun strengthFractionOf(value: Int?, min: Int, max: Int): Float? {
    if (value == null) return null
    if (max <= min) return null
    return ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
}

/** النسبة ← قيمة صحيحة مقيَّدة بالمُعلَن — فلا تخرج كتابةٌ عن السقف ولو تحرّك الشريط أقصى. */
fun strengthValueFrom(fraction: Float, min: Int, max: Int): Int {
    if (max <= min) return min
    val span = max - min
    return (min + fraction.coerceIn(0f, 1f) * span).roundToInt().coerceIn(min, max)
}

/** القيمة مقروءةً للمستخدم: `٪٤٠` للسلّم المجرّد، و`+٣ dB` للجهارة — وكلٌّ بوحدته الصحيحة. */
fun strengthFormat(value: Int?): String? = value?.let { "$it / 1000" }

/** تعزيز الجهارة بالملّي‑ديسيبل ← `+3 dB`؛ ولا يُعرض `mB` خامًّا لأنّ المستخدم يقرأ الديسيبل. */
fun loudnessFormat(valueMb: Int?): String? {
    if (valueMb == null) return null
    val db = valueMb / 100.0
    val text = if (db == kotlin.math.floor(db)) db.toInt().toString() else "$db"
    return if (valueMb > 0) "+$text dB" else "$text dB"
}

/**
 * أنماط الالتفاف المُعدّة مسبقًا، بأسماء مواردها.
 *
 * **وهي ثوابت المنصّة لا اختراع:** `PresetReverb.PRESET_NONE/SMALLROOM/…/PLATE` — تُخزَّن **رموزها
 * النصّيّة** هنا لتُقارَن بلا `android.jar`، وتُترجم إلى ثوابتها في المحرّك وحده. والقائمة الكاملة
 * تُعرض فقط حين يُعلن الجهاز `preset_reverb` (القدرات تحكم، لا هذه القائمة).
 */
object AudioReverbPreset {
    const val NONE = "none"
    const val SMALL_ROOM = "small_room"
    const val MEDIUM_ROOM = "medium_room"
    const val LARGE_ROOM = "large_room"
    const val MEDIUM_HALL = "medium_hall"
    const val LARGE_HALL = "large_hall"
    const val PLATE = "plate"

    val tokens: List<String> = listOf(NONE, SMALL_ROOM, MEDIUM_ROOM, LARGE_ROOM, MEDIUM_HALL, LARGE_HALL, PLATE)
}
