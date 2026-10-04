/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.audio.AudioEffectKind
import nd.max.core.audio.AudioSoundPreset
import nd.max.core.audio.audioPresetEqTargets
import nd.max.core.audio.audioPresetValues
import nd.max.core.audio.audioPresetRequiredFeatures
import nd.max.core.audio.audioPresetOptionalFeatures
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSelectableTile
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.viewmodel.AudioStudioUiState

@Composable
fun AudioSoundPresetsSection(
    state: AudioStudioUiState,
    onPreset: (AudioSoundPreset, Int) -> Unit,
    onCompare: (Boolean) -> Unit,
    onDiagnose: () -> Unit,
) {
    var intensity by remember(state.presetIntensity) { mutableFloatStateOf(state.presetIntensity.toFloat()) }
    var preview by remember(state.soundPreset) {
        mutableStateOf(state.soundPreset.takeIf { it != AudioSoundPreset.OFF } ?: AudioSoundPreset.BASS)
    }
    val active = state.soundPreset != AudioSoundPreset.OFF
    val busy = state.presetBusy || state.presetDiagnosticBusy
    SoundPlaybackCard(state, busy, onDiagnose) {
        onPreset(AudioSoundPreset.OFF, intensity.toInt())
    }

    MaxSection(
        title = stringResource(R.string.audio_presets_title),
        description = stringResource(R.string.audio_presets_lead),
    ) {
        Text(stringResource(R.string.audio_presets_scope), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // One column on narrow/large-text surfaces; no fixed-height cards that clip Arabic.
            val columns = if (maxWidth < 400.dp || LocalDensity.current.fontScale > 1.2f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                AudioSoundPreset.buttons.chunked(columns).forEach { presets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                        presets.forEach { preset ->
                            val available = presetAvailable(state, preset)
                            MaxSelectableTile(
                                title = stringResource(audioPresetLabelRes(preset)),
                                subtitle = stringResource(if (available) audioPresetNoteRes(preset)
                                    else R.string.audio_presets_unavailable),
                                icon = when (preset) {
                                    AudioSoundPreset.BASS -> Icons.Rounded.GraphicEq
                                    AudioSoundPreset.LOUD -> Icons.AutoMirrored.Rounded.VolumeUp
                                    AudioSoundPreset.WIDE -> Icons.Rounded.Headphones
                                    AudioSoundPreset.SPEECH -> Icons.Rounded.RecordVoiceOver
                                    AudioSoundPreset.MOVIE -> Icons.Rounded.Movie
                                    AudioSoundPreset.GAME -> Icons.Rounded.SportsEsports
                                    else -> Icons.Rounded.GraphicEq
                                },
                                selected = preview == preset,
                                // Even unavailable recipes can be inspected; only Apply performs writes.
                                enabled = !busy && !state.loading,
                                onClick = { preview = preset },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        SoundRecipePreview(state, preview)
        Button(
            onClick = { onPreset(preview, intensity.toInt()) },
            enabled = presetAvailable(state, preview) && !busy && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.audio_presets_apply_preview, stringResource(audioPresetLabelRes(preview)))) }
        MaxGroup {
            MaxSliderRow(
                title = stringResource(R.string.audio_presets_intensity),
                value = intensity,
                valueText = stringResource(when {
                    intensity < 34 -> R.string.audio_presets_intensity_light
                    intensity < 67 -> R.string.audio_presets_intensity_medium
                    else -> R.string.audio_presets_intensity_strong
                }),
                valueRange = 0f..100f,
                onValueChange = { intensity = it },
                subtitle = stringResource(R.string.audio_presets_strength_note),
                onValueChangeFinished = {
                    if (active && preview == state.soundPreset && !state.comparingOriginal) {
                        onPreset(state.soundPreset, intensity.toInt())
                    }
                },
                enabled = !busy && !state.loading && !state.comparingOriginal,
                lockedReason = if (state.comparingOriginal) stringResource(R.string.audio_presets_original_playing) else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
            Button(
                onClick = { onCompare(!state.comparingOriginal) },
                enabled = active && !busy && state.presetResult?.failedRestore.isNullOrEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(if (state.comparingOriginal) R.string.audio_presets_after
                    else R.string.audio_presets_before))
            }
            OutlinedButton(
                onClick = { onPreset(AudioSoundPreset.OFF, intensity.toInt()) },
                enabled = !busy && (active || state.presetResult?.applied == false),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.audio_presets_off)) }
        }
        if (state.comparingOriginal) Text(stringResource(R.string.audio_presets_original_playing),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        state.presetResult?.let { result ->
            val skippedNames = result.skipped.map { effectName(it) }.joinToString(", ")
            val text = when {
                result.failedRestore.isNotEmpty() -> stringResource(R.string.audio_presets_restore_failed,
                    result.failedRestore.joinToString(", "))
                !result.applied -> stringResource(R.string.audio_presets_apply_failed,
                    result.reason?.let { engineReasonText(it) } ?: stringResource(R.string.audio_presets_unavailable))
                state.soundPreset == AudioSoundPreset.OFF -> stringResource(R.string.audio_presets_restored)
                state.comparingOriginal -> stringResource(R.string.audio_presets_original_playing)
                result.skipped.isNotEmpty() -> stringResource(R.string.audio_presets_partial,
                    skippedNames)
                else -> stringResource(R.string.audio_presets_verified)
            }
            Text(text, style = MaterialTheme.typography.bodySmall,
                color = if (result.applied && result.failedRestore.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error)
        }
    }
}

private fun presetAvailable(state: AudioStudioUiState, preset: AudioSoundPreset): Boolean =
    audioPresetRequiredFeatures(preset).all { feature ->
        val kind = AudioEffectKind.entries.firstOrNull { it.feature == feature } ?: return@all false
        if (!state.enabled.containsKey(kind)) return@all false
        if (state.presetDiagnostics?.firstOrNull { it.kind == kind }?.controlled == false) return@all false
        if (kind == AudioEffectKind.EQUALIZER) state.eq?.let {
            audioPresetEqTargets(audioPresetValues(preset), it.bands) != null
        } == true else state.strengths[kind]?.isWritable == true
    }

@Composable
private fun SoundPlaybackCard(state: AudioStudioUiState, busy: Boolean, onDiagnose: () -> Unit, onOff: () -> Unit) {
    MaxCardShell(contentPadding = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(MaxSpace.lg), verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
            Text(stringResource(R.string.audio_presets_playback_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.audio_presets_current,
                stringResource(audioPresetLabelRes(state.soundPreset))), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            // Inventory's first sink is NOT a verified active playback route.
            Text(stringResource(R.string.audio_presets_detected_output,
                state.activeDevice?.productName ?: stringResource(R.string.status_unknown)),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.audio_presets_audible_unverified), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(when {
                state.comparingOriginal -> R.string.audio_presets_original_playing
                state.presetResult?.applied == true && state.soundPreset != AudioSoundPreset.OFF -> R.string.audio_presets_last_verified
                else -> R.string.audio_presets_not_verified
            }), style = MaterialTheme.typography.bodySmall)
            if (state.soundPreset != AudioSoundPreset.OFF || state.presetResult?.applied == false) {
                OutlinedButton(onClick = onOff, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.audio_presets_off))
                }
            }
            OutlinedButton(onClick = onDiagnose, enabled = !busy && !state.loading,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.audio_presets_diagnose)) }
            if (state.presetDiagnosticBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.presetDiagnostics?.let { rows ->
                Text(stringResource(R.string.audio_presets_diagnostic_snapshot), style = MaterialTheme.typography.labelMedium)
                rows.forEach { row ->
                    Text(effectName(row.kind.token), style = MaterialTheme.typography.labelLarge)
                    val detail = if (!row.attached) stringResource(R.string.audio_presets_not_attached)
                    else stringResource(R.string.audio_presets_diagnostic_row,
                        diagnosticBoolean(row.enabled), diagnosticBoolean(row.controlled),
                        diagnosticBoolean(row.readable), diagnosticBoolean(row.matchesPlan))
                    Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = if (!row.attached || row.controlled == false || row.matchesPlan == false)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.audio_presets_diagnostic_help), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun diagnosticBoolean(value: Boolean?): String = stringResource(when (value) {
    true -> R.string.audio_presets_check_yes
    false -> R.string.audio_presets_check_no
    null -> R.string.status_unknown
})

@Composable
private fun SoundRecipePreview(state: AudioStudioUiState, preset: AudioSoundPreset) {
    val required = audioPresetRequiredFeatures(preset)
    val optional = audioPresetOptionalFeatures(preset)
    MaxCardShell(contentPadding = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(MaxSpace.md), verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
            Text(stringResource(R.string.audio_presets_preview_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(audioPresetNoteRes(preset)), style = MaterialTheme.typography.bodySmall)
            AudioEffectKind.entries.filter { it.feature in required || it.feature in optional }.forEach { kind ->
                val available = if (kind == AudioEffectKind.EQUALIZER) state.eq?.let {
                    audioPresetEqTargets(audioPresetValues(preset), it.bands) != null
                } == true && state.enabled.containsKey(kind)
                else state.strengths[kind]?.isWritable == true && state.enabled.containsKey(kind)
                val owned = state.presetDiagnostics?.firstOrNull { it.kind == kind }?.controlled != false
                Text(stringResource(R.string.audio_presets_preview_row, effectName(kind.token),
                    stringResource(when {
                        available && owned -> R.string.audio_presets_preview_try
                        kind.feature in required -> R.string.audio_presets_preview_required_missing
                        else -> R.string.audio_presets_preview_skipped
                    })), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.audio_presets_preview_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun effectName(token: String): String = stringResource(when (token) {
    AudioEffectKind.EQUALIZER.token -> R.string.max_audio_eq_title
    AudioEffectKind.BASS_BOOST.token -> R.string.max_audio_effect_bass
    AudioEffectKind.VIRTUALIZER.token -> R.string.max_audio_effect_spatial
    AudioEffectKind.LOUDNESS.token -> R.string.max_audio_effect_loudness
    else -> R.string.max_audio_knob_generic
})
