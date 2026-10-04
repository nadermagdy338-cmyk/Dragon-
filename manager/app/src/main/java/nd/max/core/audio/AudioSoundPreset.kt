/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** Original starting recipes, not calibrated acoustic responses or hearing protection. */
enum class AudioSoundPreset(val token: String, val labelToken: String, val noteToken: String) {
    OFF("off", "audio_preset_off", "audio_preset_off_note"),
    BASS("bass", "audio_preset_bass", "audio_preset_bass_note"),
    LOUD("loud", "audio_preset_loud", "audio_preset_loud_note"),
    WIDE("wide", "audio_preset_wide", "audio_preset_wide_note"),
    SPEECH("speech", "audio_preset_speech", "audio_preset_speech_note"),
    MOVIE("movie", "audio_preset_movie", "audio_preset_movie_note"),
    GAME("game", "audio_preset_game", "audio_preset_game_note");

    companion object {
        fun ofToken(token: String): AudioSoundPreset? = entries.firstOrNull { it.token == token }
        val buttons: List<AudioSoundPreset> get() = entries.filter { it != OFF }
    }
}

/** preAmpDb is recipe metadata only: this route does not implement a global preamp/limiter. */
data class AudioPresetValues(
    val bassPercent: Int,
    val virtualizerPercent: Int,
    val loudnessMb: Int,
    val eqGainsDb: List<Int>,
    val preAmpDb: Int,
) {
    init {
        require(bassPercent in 0..100)
        require(virtualizerPercent in 0..100)
        require(loudnessMb in 0..AudioStrengthBounds.UI_LOUDNESS_MAX_MB)
        require(preAmpDb in -24..12)
    }
    val isSilent: Boolean get() = bassPercent == 0 && virtualizerPercent == 0 &&
        loudnessMb == 0 && preAmpDb == 0 && eqGainsDb.all { it == 0 }
}

fun audioPresetValues(preset: AudioSoundPreset): AudioPresetValues = when (preset) {
    AudioSoundPreset.OFF -> AudioPresetValues(0, 0, 0, emptyList(), 0)
    AudioSoundPreset.BASS -> AudioPresetValues(70, 0, 0, listOf(4, 3, 1, 0, 2, 0, 0), -3)
    AudioSoundPreset.LOUD -> AudioPresetValues(0, 0, 600, listOf(-1, 0, 1, 2, 3, 2, 1), -4)
    AudioSoundPreset.WIDE -> AudioPresetValues(0, 60, 0, listOf(-2, -1, 0, 0, 1, 2, 2), -2)
    AudioSoundPreset.SPEECH -> AudioPresetValues(0, 0, 200, listOf(-3, -2, -1, 2, 4, 3, 1), -2)
    AudioSoundPreset.MOVIE -> AudioPresetValues(45, 30, 300, listOf(2, 2, 1, 2, 3, 1, 0), -3)
    AudioSoundPreset.GAME -> AudioPresetValues(25, 75, 200, listOf(1, 0, 0, 1, 2, 3, 3), -2)
}

fun audioPresetRequiredFeatures(preset: AudioSoundPreset): Set<AudioFeature> = when (preset) {
    AudioSoundPreset.OFF -> emptySet()
    AudioSoundPreset.BASS -> setOf(AudioFeature.BASS_BOOST)
    AudioSoundPreset.LOUD -> setOf(AudioFeature.LOUDNESS_ENHANCER)
    AudioSoundPreset.WIDE, AudioSoundPreset.GAME -> setOf(AudioFeature.VIRTUALIZER)
    AudioSoundPreset.SPEECH, AudioSoundPreset.MOVIE -> setOf(AudioFeature.EQUALIZER)
}

fun audioPresetOptionalFeatures(preset: AudioSoundPreset): Set<AudioFeature> {
    val values = audioPresetValues(preset)
    return buildSet {
        if (values.eqGainsDb.any { it != 0 }) add(AudioFeature.EQUALIZER)
        if (values.bassPercent > 0) add(AudioFeature.BASS_BOOST)
        if (values.virtualizerPercent > 0) add(AudioFeature.VIRTUALIZER)
        if (values.loudnessMb > 0) add(AudioFeature.LOUDNESS_ENHANCER)
    } - audioPresetRequiredFeatures(preset)
}

object AudioPresetRefusal {
    const val OUT_OF_RANGE = "preset-value-out-of-range"
    const val EQ_TOO_NARROW = "preset-eq-longer-than-device-bands"
    const val FEATURE_UNAVAILABLE = "preset-feature-unavailable"
}

fun strengthFromPercent(percent: Int): Int =
    percent.coerceIn(0, 100) * AudioStrengthBounds.PLATFORM_STRENGTH_MAX / 100

/** Index interpolation for preview only; actual writes use the measured center frequencies below. */
fun eqGainsForBands(table: List<Int>, bandCount: Int): List<Int> {
    if (bandCount <= 0 || table.isEmpty()) return emptyList()
    if (table.size == 1) return List(bandCount) { table[0] }
    return (0 until bandCount).map { band ->
        val pos = band.toDouble() * table.lastIndex / maxOf(bandCount - 1, 1)
        val low = pos.toInt().coerceIn(0, table.lastIndex)
        val high = (low + 1).coerceAtMost(table.lastIndex)
        (table[low] + (table[high] - table[low]) * (pos - low)).roundToInt()
    }
}

/** Table consistency heuristic, not a measured speech intelligibility guarantee. */
fun eqLowersSpeechRange(gainsDb: List<Int>): Boolean {
    if (gainsDb.isEmpty()) return false
    val from = gainsDb.size * 3 / 7
    val to = maxOf(gainsDb.size * 5 / 7, from + 1)
    return (from until to).any { gainsDb.getOrNull(it)?.let { gain -> gain < 0 } == true }
}

/** Metadata consistency check only; passing is not proof of implemented headroom or no clipping. */
fun presetCompensationRefusal(values: AudioPresetValues): String? {
    val rise = maxOf((values.eqGainsDb.maxOrNull() ?: 0).toDouble(), values.loudnessMb / 100.0)
    return if (rise > 0 && -values.preAmpDb < rise / 2) AudioPresetRefusal.OUT_OF_RANGE else null
}

fun presetVerdict(preset: AudioSoundPreset, verdicts: List<AudioFeatureVerdict>?, bandCount: Int?): PresetVerdict {
    if (preset == AudioSoundPreset.OFF) return PresetVerdict(true, null)
    if (verdicts == null) return PresetVerdict(false, AudioPresetRefusal.FEATURE_UNAVAILABLE)
    fun usable(feature: AudioFeature): Boolean {
        if (feature == AudioFeature.EQUALIZER && (bandCount == null || bandCount <= 0)) return false
        val kind = AudioEffectKind.entries.firstOrNull { it.feature == feature } ?: return false
        return audioEffectAttachable(verdicts, kind)
    }
    if (audioPresetRequiredFeatures(preset).any { !usable(it) }) {
        return PresetVerdict(false, AudioPresetRefusal.FEATURE_UNAVAILABLE)
    }
    return PresetVerdict(true, null, audioPresetOptionalFeatures(preset).filterNot { usable(it) })
}

data class PresetVerdict(val enabled: Boolean, val reason: String?, val skipped: List<AudioFeature> = emptyList()) {
    init { require(enabled || reason != null) }
    val isPartial: Boolean get() = enabled && skipped.isNotEmpty()
}

/** Light = 35% of the recipe, balanced = original recipe, strong = 140%. */
fun presetIntensityScale(intensity: Int): Double {
    val level = intensity.coerceIn(0, 100)
    return if (level <= 50) 0.35 + level / 50.0 * 0.65 else 1.0 + (level - 50) / 50.0 * 0.4
}

fun AudioPresetValues.scaledBy(intensity: Int): AudioPresetValues {
    val scale = presetIntensityScale(intensity)
    return AudioPresetValues(
        (bassPercent * scale).roundToInt().coerceIn(0, 100),
        (virtualizerPercent * scale).roundToInt().coerceIn(0, 100),
        (loudnessMb * scale).roundToInt().coerceIn(0, AudioStrengthBounds.UI_LOUDNESS_MAX_MB),
        eqGainsDb.map { (it * scale).roundToInt() },
        if (preAmpDb == 0) 0 else -abs((preAmpDb * scale).roundToInt()).coerceAtMost(24),
    )
}

/** Android Equalizer reports center frequencies in milli-Hz, despite the legacy field name. */
fun audioPresetEqTargets(values: AudioPresetValues, bands: List<AudioEqBand>): Map<Int, Int>? {
    val anchors = listOf(60.0, 150.0, 400.0, 1000.0, 2500.0, 6000.0, 15000.0)
    if (values.eqGainsDb.size != anchors.size || bands.isEmpty()) return null
    if (bands.any { !it.isWritable || (it.centerHz ?: 0) <= 0 || it.levelMinMb!! > 0 }) return null
    if (bands.map { it.index }.toSet().size != bands.size) return null
    // Cut-only curve: subtract the highest EQ boost, rather than pretending to apply a global preamp.
    val offset = maxOf(0, values.eqGainsDb.maxOrNull() ?: 0)
    return bands.associate { band ->
        val hz = band.centerHz!!.toDouble() / 1000.0
        val high = anchors.indexOfFirst { it >= hz }.let { if (it < 0) anchors.lastIndex else it }
        val low = (high - 1).coerceAtLeast(0)
        val fraction = if (low == high) 0.0 else
            ((ln(hz) - ln(anchors[low])) / (ln(anchors[high]) - ln(anchors[low]))).coerceIn(0.0, 1.0)
        val gain = values.eqGainsDb[low] + (values.eqGainsDb[high] - values.eqGainsDb[low]) * fraction
        band.index to ((gain - offset) * 100).roundToInt().coerceIn(band.levelMinMb!!, band.levelMaxMb!!)
    }
}

/** Stop on the first rejected write and attempt every touched baseline in reverse order. */
fun audioPresetTransaction(
    targets: Map<String, Int>,
    baseline: Map<String, Int>,
    write: (String, Int) -> AudioKnobVerdict,
): AudioPresetTransactionResult {
    if (targets.isEmpty() || targets.keys.any { it !in baseline }) {
        return AudioPresetTransactionResult(false, AudioEffectReason.PARAM_UNREADABLE, emptyList())
    }
    val touched = mutableListOf<String>()
    targets.forEach { (key, value) ->
        touched += key // A failing writer may already have changed the value.
        val verdict = write(key, value)
        if (!verdict.isApplied) {
            val restoreTargets = touched.asReversed().associateWith { baseline.getValue(it) }.toMutableMap()
            touched.filter { it.endsWith("/value") || "/band/" in it }.forEach { key ->
                val enabledKey = "${key.substringBefore('/')}/enabled"
                baseline[enabledKey]?.let { restoreTargets[enabledKey] = it }
            }
            val failedRestore = audioPresetOrderedTargets(restoreTargets).entries
                .filter { !write(it.key, it.value).isApplied }.map { it.key }
            return AudioPresetTransactionResult(false, verdict.reason ?: "preset-write-failed", failedRestore)
        }
    }
    return AudioPresetTransactionResult(true, null, emptyList())
}

/** Parameters may be guarded while disabled: enable temporarily, restore values, then original state. */
fun audioPresetOrderedTargets(targets: Map<String, Int>): Map<String, Int> {
    val prepared = linkedMapOf<String, Int>()
    targets.keys.filter { it.endsWith("/value") || "/band/" in it }.forEach { key ->
        prepared["${key.substringBefore('/')}/enable_first"] = 1
    }
    prepared.putAll(targets.filterKeys { !it.endsWith("/enable_first") })
    return prepared.entries.sortedBy {
        if (it.key.endsWith("/enable_first")) 0
        else if (it.key.endsWith("/enabled")) 2 else 1
    }.associate { it.toPair() }
}

/** Include previous recipe's saved values, so B→A→B cannot leave stale parameters behind. */
fun audioPresetCompletePlan(original: Map<String, Int>, targets: Map<String, Int>): Map<String, Int> = original + targets

data class AudioPresetTransactionResult(val applied: Boolean, val reason: String?, val failedRestore: List<String>)

/** A sampled observation, not continuous monitoring or evidence that PCM passes through the effect. */
data class AudioPresetDiagnostic(
    val kind: AudioEffectKind,
    val attached: Boolean,
    val enabled: Boolean?,
    val controlled: Boolean?,
    val readable: Boolean,
    val matchesPlan: Boolean?,
)

/** Missing expected values are unknown, not a mismatch or a successful readback. */
fun audioPresetPlanMatches(expected: Map<String, Int>, live: Map<String, Int>): Boolean? {
    if (expected.isEmpty() || expected.keys.any { it !in live }) return null
    return expected.all { (key, value) -> live[key] == value }
}
