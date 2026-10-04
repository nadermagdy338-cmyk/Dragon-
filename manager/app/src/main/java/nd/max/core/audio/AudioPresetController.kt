/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

/** Used on IO under the ViewModel's preset lock. Does not own or release effect sessions. */
class AudioPresetController(private val backend: AudioEffectBackend) {
    private var original: Map<String, Int>? = null
    private var enhanced: Map<String, Int>? = null
    private var skipped: List<String> = emptyList()
    private val kinds = listOf(AudioEffectKind.EQUALIZER, AudioEffectKind.BASS_BOOST,
        AudioEffectKind.VIRTUALIZER, AudioEffectKind.LOUDNESS)

    fun apply(
        preset: AudioSoundPreset,
        intensity: Int,
        sessions: Map<AudioEffectKind, AudioEffectSession>,
        token: () -> String,
        record: (String, AudioKnobVerdict, String) -> Unit,
    ): AudioPresetApplyResult {
        if (preset == AudioSoundPreset.OFF) return restore(sessions, token, record, clear = true)
        val live = read(sessions)
        val values = audioPresetValues(preset).scaledBy(intensity)
        val eq = sessions[AudioEffectKind.EQUALIZER]?.let { backend.readEq(it) }
        val eqTargets = eq?.let { audioPresetEqTargets(values, it.bands) }
        val wanted = mapOf(
            AudioEffectKind.EQUALIZER to values.eqGainsDb.any { it != 0 },
            AudioEffectKind.BASS_BOOST to (values.bassPercent > 0),
            AudioEffectKind.VIRTUALIZER to (values.virtualizerPercent > 0),
            AudioEffectKind.LOUDNESS to (values.loudnessMb > 0),
        )
        val usable = kinds.filter { kind ->
            sessions[kind]?.hasControl == true && live.containsKey("${kind.token}/enabled") &&
                if (kind == AudioEffectKind.EQUALIZER) eqTargets != null else live.containsKey("${kind.token}/value")
        }.toSet()
        val required = audioPresetRequiredFeatures(preset)
        if (required.any { feature -> usable.none { it.feature == feature } }) {
            return AudioPresetApplyResult(false, AudioEffectReason.NOT_ATTACHED)
        }
        val targets = linkedMapOf<String, Int>()
        // Disable unused effects from the managed set, avoiding residue when switching recipes.
        kinds.filter { sessions[it]?.hasControl == true &&
            live.containsKey("${it.token}/enabled") && wanted[it] != true }.forEach {
            targets["${it.token}/enabled"] = 0
        }
        usable.filter { wanted[it] == true }.forEach { kind ->
            targets["${kind.token}/enabled"] = 1
            if (kind == AudioEffectKind.EQUALIZER) {
                eqTargets!!.forEach { (index, value) -> targets["${kind.token}/band/$index"] = value }
            } else {
                targets["${kind.token}/value"] = when (kind) {
                    AudioEffectKind.BASS_BOOST -> strengthFromPercent(values.bassPercent)
                    AudioEffectKind.VIRTUALIZER -> strengthFromPercent(values.virtualizerPercent)
                    else -> values.loudnessMb
                }
            }
        }
        // Save baseline before any write, including a failed write/rollback.
        original = live.filterKeys { it in targets && it !in original.orEmpty() } + original.orEmpty()
        val complete = audioPresetCompletePlan(original.orEmpty(), targets)
        val transaction = audioPresetTransaction(audioPresetOrderedTargets(complete), live) { key, value ->
            write(key, value, sessions, token, record)
        }
        val unavailable = wanted.filter { it.value && it.key !in usable }.keys.map { it.token }
        if (transaction.applied) { enhanced = complete; skipped = unavailable }
        return AudioPresetApplyResult(transaction.applied, transaction.reason,
            unavailable, transaction.failedRestore)
    }

    /** Read-only check against the currently selected side of A/B. Does not enable or attach effects. */
    fun diagnose(sessions: Map<AudioEffectKind, AudioEffectSession>, originalSound: Boolean): List<AudioPresetDiagnostic> {
        val live = read(sessions)
        val plan = if (originalSound) original else enhanced
        return kinds.map { kind ->
            val session = sessions[kind]
            val prefix = "${kind.token}/"
            val expected = plan.orEmpty().filterKeys { it.startsWith(prefix) }
            val readable = if (kind == AudioEffectKind.EQUALIZER) {
                backendReadEq(session)?.bands?.let { bands -> bands.isNotEmpty() && bands.all { it.levelMb != null } } == true
            } else live.containsKey("${kind.token}/value")
            AudioPresetDiagnostic(kind, session != null, session?.enabled, session?.hasControl,
                readable, audioPresetPlanMatches(expected, live))
        }
    }

    private fun backendReadEq(session: AudioEffectSession?): AudioEqSnapshot? =
        session?.let { backend.readEq(it) }

    fun compare(
        originalSound: Boolean,
        sessions: Map<AudioEffectKind, AudioEffectSession>,
        token: () -> String,
        record: (String, AudioKnobVerdict, String) -> Unit,
    ): AudioPresetApplyResult {
        val target = if (originalSound) original else enhanced
        if (target == null) return AudioPresetApplyResult(false, AudioEffectReason.PARAM_UNREADABLE)
        return run(target, sessions, token, record).copy(skipped = if (originalSound) emptyList() else skipped)
    }

    private fun restore(
        sessions: Map<AudioEffectKind, AudioEffectSession>, token: () -> String,
        record: (String, AudioKnobVerdict, String) -> Unit, clear: Boolean,
    ): AudioPresetApplyResult {
        val target = original ?: return AudioPresetApplyResult(true)
        val result = run(target, sessions, token, record)
        if (result.applied && clear) { original = null; enhanced = null; skipped = emptyList() }
        return result
    }

    private fun run(
        targets: Map<String, Int>, sessions: Map<AudioEffectKind, AudioEffectSession>,
        token: () -> String, record: (String, AudioKnobVerdict, String) -> Unit,
    ): AudioPresetApplyResult {
        val ordered = audioPresetOrderedTargets(targets)
        val live = read(sessions)
        val result = audioPresetTransaction(ordered, live) { key, value -> write(key, value, sessions, token, record) }
        return AudioPresetApplyResult(result.applied, result.reason, failedRestore = result.failedRestore)
    }

    private fun read(sessions: Map<AudioEffectKind, AudioEffectSession>): Map<String, Int> = buildMap {
        kinds.forEach { kind ->
            val session = sessions[kind] ?: return@forEach
            session.enabled?.let {
                put("${kind.token}/enabled", if (it) 1 else 0)
                put("${kind.token}/enable_first", if (it) 1 else 0)
            }
            if (kind == AudioEffectKind.EQUALIZER) {
                backend.readEq(session)?.bands?.forEach { band ->
                    band.levelMb?.let { put("${kind.token}/band/${band.index}", it) }
                }
            } else backend.readStrength(session)?.takeIf { it.isWritable }?.value?.let {
                put("${kind.token}/value", it)
            }
        }
    }

    private fun write(
        key: String, value: Int, sessions: Map<AudioEffectKind, AudioEffectSession>,
        token: () -> String, record: (String, AudioKnobVerdict, String) -> Unit,
    ): AudioKnobVerdict {
        val parts = key.split('/')
        val kind = kinds.first { it.token == parts[0] }
        val session = sessions[kind] ?: return audioKnobNotAttempted(AudioEffectReason.NOT_ATTACHED)
        val audit = token()
        val verdict = try {
            when (parts[1]) {
                "enabled", "enable_first" -> backend.setEnabled(session, value == 1, audit)
                "band" -> backend.writeEqBand(session, parts[2].toInt(), value, audit)
                else -> backend.writeStrength(session, value, audit)
            }
        } catch (_: Exception) { audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE) }
        record("preset_$key", verdict, audit)
        return verdict
    }
}

data class AudioPresetApplyResult(
    val applied: Boolean,
    val reason: String? = null,
    val skipped: List<String> = emptyList(),
    val failedRestore: List<String> = emptyList(),
)
