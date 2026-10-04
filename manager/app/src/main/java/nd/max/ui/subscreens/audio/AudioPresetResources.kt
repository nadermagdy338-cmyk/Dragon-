/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الأنماط الجاهزة — **الربط بين رموز النواة ومعرّفات مواردها**.
 *
 * **ولماذا الربط هنا لا في `core/audio`:** نواة الأنماط ([AudioSoundPreset]) تُقاس على JVM بلا
 * `android.jar` (حاضنة `kverify-audio`)، و`R` غير مرئيّ فيها — فالرمزُ فيها نصٌّ، والترجمة إلى
 * موردٍ في طبقة الواجهة. وهو نفس الفصل المُتَّبع في `AudioTab`: النواة تقرّر، والواجهة تُسمّي.
 *
 * Missing mappings fail explicitly instead of returning an invalid resource ID.
 * Resource references and translation placeholders are checked by repository gates.
 */
package nd.max.ui.subscreens.audio

import androidx.annotation.StringRes
import nd.max.R
import nd.max.core.audio.AudioSoundPreset

/** خريطة اسم الزرّ — مفاتيحها رموز النواة حرفيًّا. */
private val PRESET_LABEL_RES: Map<String, Int> = mapOf(
    "audio_preset_off" to R.string.audio_preset_off,
    "audio_preset_bass" to R.string.audio_preset_bass,
    "audio_preset_loud" to R.string.audio_preset_loud,
    "audio_preset_wide" to R.string.audio_preset_wide,
    "audio_preset_speech" to R.string.audio_preset_speech,
    "audio_preset_movie" to R.string.audio_preset_movie,
    "audio_preset_game" to R.string.audio_preset_game,
)

/** خريطة سطر الشرح. */
private val PRESET_NOTE_RES: Map<String, Int> = mapOf(
    "audio_preset_off_note" to R.string.audio_preset_off_note,
    "audio_preset_bass_note" to R.string.audio_preset_bass_note,
    "audio_preset_loud_note" to R.string.audio_preset_loud_note,
    "audio_preset_wide_note" to R.string.audio_preset_wide_note,
    "audio_preset_speech_note" to R.string.audio_preset_speech_note,
    "audio_preset_movie_note" to R.string.audio_preset_movie_note,
    "audio_preset_game_note" to R.string.audio_preset_game_note,
)

/**
 * معرّف مورد اسم الزرّ — **ويرمي إن غاب**.
 *
 * **ولماذا يرمي ولا يُعيد `0`:** رمزٌ بلا مورد يعني زرًّا بنصٍّ فارغ، وهو عطبٌ **صامت** يُقرأ
 * «الشاشة معطوبة». والرمي يُسقط الاختبار في لحظته بدل أن يُسقط التجربة على جهاز المالك. وهو نفس
 * مبدأ `require` في `AudioPresetValues`.
 */
@StringRes
fun audioPresetLabelRes(preset: AudioSoundPreset): Int =
    PRESET_LABEL_RES[preset.labelToken]
        ?: error("رمز اسم بلا مورد: ${preset.labelToken}")

/** معرّف مورد سطر الشرح — ويرمي إن غاب، كما أعلاه. */
@StringRes
fun audioPresetNoteRes(preset: AudioSoundPreset): Int =
    PRESET_NOTE_RES[preset.noteToken]
        ?: error("رمز شرح بلا مورد: ${preset.noteToken}")
