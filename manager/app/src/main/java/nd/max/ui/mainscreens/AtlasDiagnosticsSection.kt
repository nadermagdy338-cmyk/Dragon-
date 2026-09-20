/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.mainscreens

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import nd.max.R
import nd.max.core.atlas.AtlasDomain
import nd.max.core.atlas.AtlasFeatureOutcome
import nd.max.core.atlas.AtlasObservation
import nd.max.core.atlas.AtlasScanState
import nd.max.core.atlas.AtlasScanStatus
import nd.max.core.atlas.AtlasSemanticStatus
import nd.max.core.atlas.AtlasStage
import nd.max.core.atlas.AtlasUnit
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxDialogTab
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxProgressStrip
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTabbedDialog
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.viewmodel.AtlasConditionCode
import nd.max.ui.viewmodel.AtlasPresentation
import nd.max.ui.viewmodel.AtlasReportRefusal
import nd.max.ui.viewmodel.AtlasUiPhase
import nd.max.ui.viewmodel.AtlasUiState
import nd.max.ui.viewmodel.AtlasViewModel

/**
 * Max Atlas on the diagnostics screen: the device's own answers, in two tiers, with the one artifact a
 * user may choose to send (`P7`, slice `G`).
 *
 * Four rules shape this surface, and each one exists because its opposite is a lie a screen can tell:
 *
 * 1. **A reading is never shown as live.** Every Atlas read is one bounded attempt, so its row carries a
 *    source, an age and a trust state, and `MaxMetricLine` prints provenance precisely because the value
 *    is not a stream. A number with no source is a number nobody can check.
 * 2. **The two tiers are shown as two numbers.** Reviewed knowledge and the accumulated vocabulary are
 *    different strengths of evidence; adding them into one "supported" count would upgrade a candidate
 *    name into a reviewed fact in the only place the user ever reads it.
 * 3. **Nothing offers a report while the pass is unfinished, cancelled or stopped by a bound.** The
 *    preview is the exact artifact that will be shared, built once and frozen, and the confirmation is a
 *    separate step: preview → confirm → system chooser. There is no copy-to-clipboard shortcut here,
 *    because a support artifact that leaves by clipboard leaves without a preview.
 * 4. **A cancelled pass is never rendered as an absent device.** It has its own condition and its own
 *    words, and it offers no report at all.
 *
 * The screen does no reading of its own: it collects the view model's state and sends events.
 */
@Composable
fun AtlasDiagnosticsSection(
    modifier: Modifier = Modifier,
    viewModel: AtlasViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    AtlasDiagnosticsSection(
        state = state,
        onStart = viewModel::start,
        onCancel = viewModel::cancel,
        onPreview = viewModel::previewReport,
        onDismissPreview = viewModel::dismissPreview,
        onShare = viewModel::share,
        onShareConsumed = viewModel::consumeShareRequest,
        modifier = modifier,
    )
}

/**
 * The stateless form, so the whole surface can be rendered from a hand-built state in a test or a
 * preview without a repository, a device or a Hilt graph.
 */
@Composable
internal fun AtlasDiagnosticsSection(
    state: AtlasUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onPreview: () -> Unit,
    onDismissPreview: () -> Unit,
    onShare: () -> Unit,
    onShareConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scan = state.scan
    val phase = AtlasPresentation.phase(scan)
    val limitsReached = state.limitsReached
    var previewTab by remember { mutableIntStateOf(0) }
    var showPreview by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    /*
     * The one-shot share handoff. The file is the app's own cache artifact, handed to a receiving app
     * with a read grant only: no write grant, no upload path, no recipient address, and the request is
     * cleared as soon as it is consumed so a rotation cannot start a second chooser.
     */
    LaunchedEffect(state.shareFile) {
        val file = state.shareFile ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, file.name))
        }
        onShareConsumed()
    }

    MaxSection(
        title = stringResource(R.string.max_atlas_section_title),
        description = stringResource(R.string.max_atlas_section_desc),
        modifier = modifier,
    ) {
        MaxGroup {
            MaxRow(
                title = stringResource(phaseTitleRes(phase)),
                subtitle = statusDetail(scan, limitsReached),
                icon = Icons.Rounded.Memory,
                iconTone = when (phase) {
                    AtlasUiPhase.Complete -> MaxTone.Positive
                    AtlasUiPhase.Cancelled -> MaxTone.Caution
                    AtlasUiPhase.Denied, AtlasUiPhase.Unavailable -> MaxTone.Inactive
                    else -> MaxTone.Accent
                },
                trailing = {
                    if (scan.isRunning) {
                        nd.max.ui.component.StudioTextButton(onClick = onCancel) {
                            Text(stringResource(R.string.max_atlas_cancel))
                        }
                    } else {
                        nd.max.ui.component.StudioTextButton(onClick = onStart) {
                            Text(
                                stringResource(
                                    if (state.everStarted) R.string.max_atlas_retry else R.string.max_atlas_start
                                )
                            )
                        }
                    }
                }
            )
        }

        if (scan.isRunning) {
            MaxProgressStrip(
                title = stringResource(R.string.max_atlas_reading),
                percent = AtlasPresentation.percent(scan),
                detail = stringResource(
                    R.string.max_atlas_progress_detail,
                    scan.outcomes.size.toString(),
                    if (scan.totalFeatures > 0) scan.totalFeatures.toString() else unknownCount,
                ),
                cancelLabel = stringResource(R.string.max_atlas_cancel),
                onCancel = onCancel,
            )
        }

        AtlasPresentation.condition(scan, limitsReached)?.let { code ->
            MaxConditionNotice(condition = conditionOf(code))
        }

        AtlasTiers(scan)

        AtlasDomainReadings(scan)

        if (AtlasPresentation.offersReport(scan, limitsReached)) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_atlas_report_title),
                    subtitle = stringResource(R.string.max_atlas_report_desc),
                    icon = Icons.Rounded.Refresh,
                    iconTone = MaxTone.Caution,
                    onClick = {
                        onPreview()
                        previewTab = 0
                        showPreview = true
                    }
                )
            }
        }

        state.refusal?.let { refusal ->
            MaxConditionNotice(
                condition = MaxCondition(
                    kind = when (refusal) {
                        AtlasReportRefusal.NotFinished, AtlasReportRefusal.WasCancelled ->
                            MaxConditionKind.Unavailable
                        AtlasReportRefusal.BoundReached -> MaxConditionKind.Disconnected
                        else -> MaxConditionKind.Error
                    },
                    title = stringResource(R.string.max_atlas_report_blocked_title),
                    detail = stringResource(refusalRes(refusal)),
                )
            )
        }

        state.previewText?.takeIf { showPreview }?.let { frozen ->
            AtlasPreviewDialog(
                frozen = frozen,
                scan = scan,
                selected = previewTab,
                onSelect = { previewTab = it },
                onDismiss = {
                    showPreview = false
                    onDismissPreview()
                },
                onConfirm = {
                    // The preview closes and the confirmation opens. It must NOT discard the frozen
                    // artifact: the artifact is what the user is about to approve.
                    showPreview = false
                    showConfirm = true
                }
            )
        }
    }

    /*
     * The confirmation step exists because of a real ordering trap: `MaxConfirmDialog` calls `onDismiss`
     * *before* `onConfirm`. Anything that cleared the frozen bytes on dismiss would make the share write
     * a regenerated artifact — or nothing at all. So dismiss here only closes this dialog, and the
     * cleanup happens when the preview itself is dismissed.
     */
    MaxConfirmDialog(
        visible = showConfirm,
        title = stringResource(R.string.max_atlas_share_confirm_title),
        message = stringResource(R.string.max_atlas_share_confirm_message),
        confirmLabel = stringResource(R.string.max_atlas_share),
        dismissLabel = stringResource(R.string.max_atlas_share_cancel),
        onDismiss = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            onShare()
            onDismissPreview()
        }
    )
}

/** The two evidence tiers, side by side and never summed. */
@Composable
private fun AtlasTiers(scan: AtlasScanState) {
    if (!scan.everStartedForDisplay()) return
    MaxGroup {
        MaxMetricLine(
            metric = MaxMetric(
                label = stringResource(R.string.max_atlas_tier_reviewed),
                value = AtlasPresentation.reviewedReadings(scan).toString(),
                trust = MaxDataTrust.Snapshot,
            )
        )
        MaxGroupDivider()
        MaxMetricLine(
            metric = MaxMetric(
                label = stringResource(R.string.max_atlas_tier_candidates),
                value = AtlasPresentation.candidateReadings(scan).toString(),
                trust = MaxDataTrust.Snapshot,
                note = stringResource(R.string.max_atlas_tier_candidates_note),
            )
        )
        MaxGroupDivider()
        MaxMetricLine(
            metric = MaxMetric(
                label = stringResource(R.string.max_atlas_tier_gaps),
                value = AtlasPresentation.reviewedGaps(scan).toString(),
                trust = if (AtlasPresentation.reviewedGaps(scan) > 0) MaxDataTrust.Unreadable else MaxDataTrust.Snapshot,
                note = stringResource(R.string.max_atlas_tier_gaps_note),
            )
        )
    }
}

/**
 * One group per domain, readings only.
 *
 * A failed attempt is not a row here: sixty "did not answer" rows would bury the readings that *did*
 * arrive, and the count plus the reasons already live in the block above and in the report. What is
 * never done is hiding a failure — it is counted, it is reported, and its reason is exportable.
 */
@Composable
private fun AtlasDomainReadings(scan: AtlasScanState) {
    val observed = scan.outcomes.filterIsInstance<AtlasFeatureOutcome.Observed>()
    if (observed.isEmpty()) return

    observed.groupBy { it.observation.domain }.forEach { (domain, readings) ->
        MaxGroup(modifier = Modifier.fillMaxWidth()) {
            MaxRow(
                title = stringResource(domainLabelRes(domain)),
                subtitle = stringResource(R.string.max_atlas_domain_count, readings.size.toString()),
                icon = null
            )
            readings.take(MAX_ROWS_PER_DOMAIN).forEach { reading ->
                MaxGroupDivider()
                MaxMetricLine(metric = readingMetric(reading))
            }
            if (readings.size > MAX_ROWS_PER_DOMAIN) {
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(
                        R.string.max_atlas_domain_more,
                        (readings.size - MAX_ROWS_PER_DOMAIN).toString()
                    )
                )
            }
        }
    }
}

/** One reading as a metric: full device path, value, unit, source and age. Nothing inferred. */
@Composable
private fun readingMetric(reading: AtlasFeatureOutcome.Observed): MaxMetric {
    val observation: AtlasObservation = reading.observation
    val ageMs = observation.observedAtElapsedMs?.let { observedAt ->
        (android.os.SystemClock.elapsedRealtime() - observedAt).coerceAtLeast(0L)
    }
    return MaxMetric(
        label = observation.path,
        value = valueOf(observation),
        unit = unitSymbol(observation.unit),
        trust = AtlasPresentation.trust(reading),
        source = if (reading.stage == AtlasStage.CANDIDATE_INTERFACE) {
            stringResource(R.string.max_atlas_source_candidate)
        } else {
            stringResource(R.string.max_atlas_source_reviewed)
        },
        age = ageMs?.let { stringResource(R.string.max_atlas_age_seconds, (it / 1000L).toString()) },
        note = if (observation.semanticStatus == AtlasSemanticStatus.REVIEWED_MATCH) {
            null
        } else {
            stringResource(R.string.max_atlas_meaning_not_established)
        },
    )
}

/**
 * The preview: the frozen artifact's own summary, the interface list with full paths, and the device
 * block. It reads nothing — the text was serialized once, and what is shown is what will be shared.
 */
@Composable
private fun AtlasPreviewDialog(
    frozen: String,
    scan: AtlasScanState,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tabs = listOf(
        MaxDialogTab(stringResource(R.string.max_atlas_preview_tab_summary)),
        MaxDialogTab(stringResource(R.string.max_atlas_preview_tab_interfaces)),
        MaxDialogTab(stringResource(R.string.max_atlas_preview_tab_artifact)),
    )
    MaxTabbedDialog(
        visible = true,
        title = stringResource(R.string.max_atlas_preview_title),
        tabs = tabs,
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.max_atlas_preview_dismiss),
        confirmLabel = stringResource(R.string.max_atlas_preview_confirm),
        onConfirm = onConfirm,
    ) { index ->
        when (index) {
            0 -> AtlasPreviewSummary(scan)
            1 -> AtlasPreviewInterfaces(scan)
            else -> Text(
                text = frozen.take(PREVIEW_ARTIFACT_CHARS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AtlasPreviewSummary(scan: AtlasScanState) {        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PreviewLine(stringResource(R.string.max_atlas_preview_status), scan.status.name)
        PreviewLine(stringResource(R.string.max_atlas_preview_readings), scan.observed.size.toString())
        PreviewLine(stringResource(R.string.max_atlas_preview_gaps), scan.reviewedUnresolved.size.toString())
        PreviewLine(stringResource(R.string.max_atlas_preview_suppressed), scan.suppressed.toString())
        PreviewLine(stringResource(R.string.max_atlas_preview_cache_hits), scan.cacheHits.toString())
    }
}

/** Full paths, because a path that was ellipsized on the row is still the only identity a maintainer has. */
@Composable
private fun AtlasPreviewInterfaces(scan: AtlasScanState) {        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        scan.outcomes.forEach { outcome ->
            val line = when (outcome) {
                is AtlasFeatureOutcome.Observed -> "${outcome.observation.path} = ${valueOf(outcome.observation)}"
                is AtlasFeatureOutcome.Unresolved -> "${outcome.reason} (${outcome.failure.name})"
                is AtlasFeatureOutcome.Suppressed -> "${outcome.id}: ${outcome.cause.name}"
                is AtlasFeatureOutcome.Cancelled -> outcome.id
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Text(
        text = stringResource(R.string.max_atlas_preview_line, label, value),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The value as text, in the unit the interface declares and with **no scaling**.
 *
 * A conversion is a claim about an interface's semantics; a divisor copied from another project is
 * exactly how a wrong reading looks right. Free text is reported raw and truncated to one bounded line,
 * because a multi-line table is not a reading.
 */
internal fun valueOf(observation: AtlasObservation): String = when {
    observation.textValue != null -> observation.textValue
        .lineSequence()
        .firstOrNull()
        .orEmpty()
        .take(MAX_TEXT_CHARS)
        .ifEmpty { "-" }

    observation.value != null -> {
        val value = observation.value
        if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    else -> "-"
}

/**
 * Unit symbols, not sentences: a unit is a token a reader compares, not prose to translate. The value
 * is what a translation would change the meaning of, so the tokens stay as the kernel names them.
 */
internal fun unitSymbol(unit: AtlasUnit): String? = when (unit) {
    AtlasUnit.HERTZ -> "Hz"
    AtlasUnit.KILO_HERTZ -> "kHz"
    AtlasUnit.MEGA_HERTZ -> "MHz"
    AtlasUnit.CELSIUS -> "°C"
    AtlasUnit.MILLI_CELSIUS -> "m°C"
    AtlasUnit.DECI_CELSIUS -> "d°C"
    AtlasUnit.MICRO_AMP -> "µA"
    AtlasUnit.MICRO_VOLT -> "µV"
    AtlasUnit.MICRO_AMP_HOUR -> "µAh"
    AtlasUnit.MICRO_WATT_HOUR -> "µWh"
    AtlasUnit.BYTES -> "B"
    AtlasUnit.COUNT -> null
    AtlasUnit.PERCENT -> "%"
    AtlasUnit.UNKNOWN -> null
}

/**
 * A scan is "started" once it has any outcome or any status beyond `IDLE`.
 *
 * The state's own flag is not enough: this composable renders the repository snapshot, and a snapshot
 * that is still `IDLE` with no outcomes is what "never asked" looks like from here.
 */
private fun AtlasScanState.everStartedForDisplay(): Boolean =
    status != AtlasScanStatus.IDLE || outcomes.isNotEmpty()

@Composable
private fun statusDetail(scan: AtlasScanState, limitsReached: Boolean): String = when {
    scan.isRunning -> stringResource(R.string.max_atlas_status_reading)
    limitsReached -> stringResource(R.string.max_atlas_status_limited)
    scan.status == AtlasScanStatus.IDLE -> stringResource(R.string.max_atlas_status_idle)
    else -> stringResource(R.string.max_atlas_status_counts, scan.observed.size.toString(), scan.totalFeatures.toString())
}

@Composable
private fun conditionOf(code: AtlasConditionCode): MaxCondition = MaxCondition(
    kind = when (code) {
        AtlasConditionCode.Reading -> MaxConditionKind.Loading
        AtlasConditionCode.Partial -> MaxConditionKind.Unavailable
        AtlasConditionCode.LimitReached -> MaxConditionKind.Disconnected
        AtlasConditionCode.Cancelled -> MaxConditionKind.Unavailable
        AtlasConditionCode.Denied -> MaxConditionKind.RootRequired
        AtlasConditionCode.NoBackend -> MaxConditionKind.Unsupported
    },
    title = stringResource(
        when (code) {
            AtlasConditionCode.Reading -> R.string.max_atlas_condition_reading
            AtlasConditionCode.Partial -> R.string.max_atlas_condition_partial
            AtlasConditionCode.LimitReached -> R.string.max_atlas_condition_limit
            AtlasConditionCode.Cancelled -> R.string.max_atlas_condition_cancelled
            AtlasConditionCode.Denied -> R.string.max_atlas_condition_denied
            AtlasConditionCode.NoBackend -> R.string.max_atlas_condition_no_backend
        }
    ),
    detail = stringResource(
        when (code) {
            AtlasConditionCode.Reading -> R.string.max_atlas_condition_reading_desc
            AtlasConditionCode.Partial -> R.string.max_atlas_condition_partial_desc
            AtlasConditionCode.LimitReached -> R.string.max_atlas_condition_limit_desc
            AtlasConditionCode.Cancelled -> R.string.max_atlas_condition_cancelled_desc
            AtlasConditionCode.Denied -> R.string.max_atlas_condition_denied_desc
            AtlasConditionCode.NoBackend -> R.string.max_atlas_condition_no_backend_desc
        }
    ),
)

private fun phaseTitleRes(phase: AtlasUiPhase): Int = when (phase) {
    AtlasUiPhase.Idle -> R.string.max_atlas_phase_idle
    AtlasUiPhase.Running -> R.string.max_atlas_phase_running
    AtlasUiPhase.Complete -> R.string.max_atlas_phase_complete
    AtlasUiPhase.Incomplete -> R.string.max_atlas_phase_incomplete
    AtlasUiPhase.Cancelled -> R.string.max_atlas_phase_cancelled
    AtlasUiPhase.Denied -> R.string.max_atlas_phase_denied
    AtlasUiPhase.Unavailable -> R.string.max_atlas_phase_unavailable
}

private fun refusalRes(refusal: AtlasReportRefusal): Int = when (refusal) {
    AtlasReportRefusal.NotFinished -> R.string.max_atlas_refusal_not_finished
    AtlasReportRefusal.WasCancelled -> R.string.max_atlas_refusal_cancelled
    AtlasReportRefusal.BoundReached -> R.string.max_atlas_refusal_bound
    AtlasReportRefusal.TooManyInterfaces -> R.string.max_atlas_refusal_too_many
    AtlasReportRefusal.Refused -> R.string.max_atlas_refusal_answered
    AtlasReportRefusal.WriteFailed -> R.string.max_atlas_refusal_write_failed
}

private fun domainLabelRes(domain: AtlasDomain): Int = when (domain) {
    AtlasDomain.CPU -> R.string.max_atlas_domain_cpu
    AtlasDomain.GPU -> R.string.max_atlas_domain_gpu
    AtlasDomain.THERMAL -> R.string.max_atlas_domain_thermal
    AtlasDomain.MEMORY -> R.string.max_atlas_domain_memory
    AtlasDomain.POWER -> R.string.max_atlas_domain_power
    AtlasDomain.DISPLAY -> R.string.max_atlas_domain_display
    AtlasDomain.SENSOR -> R.string.max_atlas_domain_sensor
    AtlasDomain.STORAGE -> R.string.max_atlas_domain_storage
    AtlasDomain.NETWORK -> R.string.max_atlas_domain_network
    AtlasDomain.PRIVILEGE -> R.string.max_atlas_domain_privilege
}

/** How many readings of one domain a row list shows before it says how many are left. */
private const val MAX_ROWS_PER_DOMAIN = 6

/** One bounded line of free text: a node's whole table is not a reading. */
private const val MAX_TEXT_CHARS = 80

/** The frozen artifact is shown truncated on screen; the file is shared whole. */
private const val PREVIEW_ARTIFACT_CHARS = 4_000

/** Shown where a real total is unknown. A count that does not exist is not a zero. */
private val unknownCount: String = "?"
