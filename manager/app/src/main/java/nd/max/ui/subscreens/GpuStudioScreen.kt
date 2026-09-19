/*
 * GPU Studio — GPU frequency control for the current device.
 *
 * This screen was rebuilt for two reasons that share one root cause.
 *
 * 1. It looked foreign. It was the only surface in the app built from raw
 *    Material3 `Card`/`TopAppBar`/`Scaffold` with its own 30dp radii, a fixed
 *    190x128 preset tile, a `displaySmall` headline and a gradient wash — while
 *    every other screen is built from the MaxManager language (MaxGroup/MaxRow/
 *    MaxMetricLine and the shared tokens). It also printed every label as a
 *    literal, half of them Arabic inside an English shell, so nothing could be
 *    translated. Now it uses the house components and reads all its text from
 *    `max_gpu_strings.xml`.
 *
 * 2. Selecting an intent appeared to do nothing. It was not a hardware bug: the
 *    intents only *staged* a request, the apply action lived in a panel that was
 *    (a) conditional and (b) the last item of a long scrolling list, and the
 *    intent tiles carried no selected state at all. Tapping therefore produced
 *    zero visible change anywhere on screen. Three fixes, and they are the point
 *    of this file:
 *      - `MaxChoiceRow` shows real selection (radio + selectable semantics),
 *      - the review panel sits directly under the controls currently in use,
 *      - the outcome is reported through the scaffold banner, which is at the
 *        top and always visible, instead of a card at the bottom.
 *
 * Nothing here decides what may be written: `GpuHardwareBackend.validate` is the
 * only authority, and a write that does not read back as requested is reported
 * as a refusal with the previous value restored.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxListScreen
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.GpuNotice
import nd.max.ui.viewmodel.GpuNoticeKind
import nd.max.ui.viewmodel.GpuStudioUiState
import nd.max.ui.viewmodel.GpuStudioViewModel
import kotlin.math.roundToInt

/** The three smart intents, in the order they are offered. Labels live in strings. */
private enum class GpuIntent(
    val mode: GpuHardwareBackend.IntentMode,
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
) {
    EFFICIENCY(GpuHardwareBackend.IntentMode.EFFICIENCY, R.string.max_gpu_intent_efficiency_title, R.string.max_gpu_intent_efficiency_desc),
    ADAPTIVE(GpuHardwareBackend.IntentMode.ADAPTIVE, R.string.max_gpu_intent_adaptive_title, R.string.max_gpu_intent_adaptive_desc),
    SUSTAINED(GpuHardwareBackend.IntentMode.SUSTAINED, R.string.max_gpu_intent_sustained_title, R.string.max_gpu_intent_sustained_desc),
}

@Composable
fun GpuStudioScreen(
    navController: NavController,
    // `hiltViewModel()` لا `viewModel()`: هذا الـViewModel له مُنشئ بوسائط (arbiter)،
    // و`viewModel()` بلا مصنع ينادي مُنشئًا بلا وسائط — فيخرج التطبيق لحظة فتح الشاشة.
    viewModel: GpuStudioViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    LaunchedEffect(Unit) { viewModel.load() }

    // يُحمل إلى الشاشة لأن موضع لوحة المراجعة يتبعه: اللوحة تجلس تحت الضوابط
    // التي يستعملها المستخدم الآن، فيراها بلا تمرير.
    var labExpanded by rememberSaveable { mutableStateOf(false) }

    val title = stringResource(R.string.max_gpu_title)
    val condition = when {
        state.loading -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = title,
            detail = stringResource(R.string.max_gpu_probe_detail),
        )

        state.device == null -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = stringResource(R.string.max_gpu_unavailable_title),
            detail = stringResource(
                if (state.selection?.state == GpuHardwareBackend.SelectionState.AMBIGUOUS) {
                    R.string.max_gpu_ambiguous_detail
                } else {
                    R.string.max_gpu_unavailable_detail
                }
            ),
            technicalDetail = state.selection?.reason,
        )

        else -> null
    }

    // ناتج آخر تغيير في الشريط العلوي لا في بطاقة أسفل الصفحة: ما تقرأه العين بعد
    // الضغط يجب أن يكون في المكان الذي كانت تنظر إليه.
    val banner = state.notice?.let { notice -> noticeCondition(notice) }

    MaxListScreen(
        title = title,
        subtitle = stringResource(R.string.max_gpu_subtitle),
        onBack = navController::popBackStack,
        accentIcon = Icons.Outlined.Memory,
        condition = condition,
        banner = banner,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_gpu_help_title),
                body = stringResource(R.string.max_gpu_help_body),
            )
        },
    ) {
        val device = state.device
        if (device == null) return@MaxListScreen

        item(key = "gpu_hero") { GpuHero(state, device) }

        item(key = "gpu_intents") { GpuIntents(state, viewModel) }
        // اللوحة تحت النوايا ما لم يكن المختبر مفتوحًا — وعندها تنتقل تحته،
        // فلا تظهر مرّتين ولا تخرج عن نطاق النظر.
        if (!labExpanded) item(key = "gpu_review") { GpuReview(state, viewModel) }

        item(key = "gpu_lab") {
            GpuAdvancedLab(
                state = state,
                vm = viewModel,
                expanded = labExpanded,
                onExpandedChange = { labExpanded = it },
            )
        }
        if (labExpanded) item(key = "gpu_review_lab") { GpuReview(state, viewModel) }

        item(key = "gpu_diagnostics") { GpuDiagnostics(state, device) }
    }
}

// ── القراءة الحيّة ────────────────────────────────────────────────────────────

@Composable
private fun GpuHero(state: GpuStudioUiState, device: GpuHardwareBackend.Device) {
    val writable = device.rangeWritable || device.exactLockWritable || device.governorWritable
    val ageSeconds = ((System.currentTimeMillis() - (state.selection?.observedAtMs ?: 0L)) / 1000L)
        .coerceAtLeast(0L)

    MaxGroup {
        MaxRow(
            title = device.name,
            subtitle = stringResource(
                R.string.max_gpu_family_and_state,
                familyLabel(device.family),
                stringResource(
                    if (writable) R.string.max_gpu_state_ready else R.string.max_gpu_state_read_only
                ),
            ),
            icon = Icons.Outlined.Memory,
            iconTone = MaxTone.Accent,
        )

        MaxGroupDivider()

        // بلا حشو أو مسافات هنا: كل قراءة تحمل حشوها الخاصّ (انظر `MaxMetricLine`)،
        // فالحشو المكرّر هنا كان يُضاعفه.
        Column {
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.max_gpu_live_frequency),
                    // `null` تعني «لم نقرأ» لا صفرًا؛ والعارض يرفض طباعة قيمة بلا قراءة.
                    value = GpuHardwareBackend.frequencyMHz(device, device.currentFreq)?.toString(),
                    unit = "MHz",
                    trust = if (device.currentFreq != null) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                    source = device.path,
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.max_gpu_load),
                    value = device.loadPercent?.toString(),
                    unit = "%",
                    trust = if (device.loadPercent != null) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                    source = device.path,
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.max_gpu_range_label),
                    value = effectiveRange(device),
                    unit = "MHz",
                    // نطاق معلَن لا قياس لحظي: يُوسم كما هو بدل أن يُوهم بأنه حيّ.
                    trust = if (device.minFreq != null && device.maxFreq != null) {
                        MaxDataTrust.Snapshot
                    } else {
                        MaxDataTrust.Unreadable
                    },
                    source = device.path,
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.max_gpu_governor_label),
                    value = device.governor?.takeIf { it.isNotBlank() },
                    trust = if (device.governor.isNullOrBlank()) MaxDataTrust.Unreadable else MaxDataTrust.Live,
                    source = device.path,
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.max_gpu_temp_label),
                    value = device.thermalC?.toString(),
                    unit = "°C",
                    trust = if (device.thermalC != null) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                    source = device.evidence.firstOrNull(),
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.max_gpu_read_age_label),
                    value = stringResource(R.string.max_gpu_age_seconds, ageSeconds),
                    trust = MaxDataTrust.Snapshot,
                )
            )
        }

        MaxGroupDivider()

        Column(
            modifier = Modifier.padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            Text(
                text = stringResource(R.string.max_gpu_history_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GpuSparkline(state.historyMHz, MaterialTheme.colorScheme.primary)
        }
    }
}

private fun effectiveRange(device: GpuHardwareBackend.Device): String? {
    val min = GpuHardwareBackend.frequencyMHz(device, device.minFreq) ?: return null
    val max = GpuHardwareBackend.frequencyMHz(device, device.maxFreq) ?: return null
    return "$min – $max"
}

@Composable
private fun GpuSparkline(values: List<Float>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val step = size.width / (values.size - 1)
        values.zipWithNext().forEachIndexed { index, (a, b) ->
            drawLine(
                color = color,
                start = Offset(index * step, size.height - ((a - min) / span) * size.height),
                end = Offset((index + 1) * step, size.height - ((b - min) / span) * size.height),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ── النوايا الذكية ────────────────────────────────────────────────────────────

@Composable
private fun GpuIntents(state: GpuStudioUiState, vm: GpuStudioViewModel) {
    val device = state.device ?: return
    val available = device.rangeWritable || device.exactLockWritable
    val lockedReason = stringResource(R.string.max_gpu_intents_locked)

    MaxSection(
        title = stringResource(R.string.max_gpu_intents_title),
        description = stringResource(R.string.max_gpu_intents_desc),
    ) {
        MaxGroup {
            GpuIntent.entries.forEachIndexed { index, intent ->
                if (index > 0) MaxGroupDivider()
                MaxChoiceRow(
                    title = stringResource(intent.labelRes),
                    subtitle = stringResource(intent.descRes),
                    // الاختيار يُقرأ من الحالة لا من نيّة محليّة: الصفّ يعرض ما ستُطبَّق
                    // عليه فعلًا، لا ما تمنّاه المستخدم آخر مرّة.
                    selected = state.stagedIntent == intent.mode,
                    enabled = available,
                    lockedReason = lockedReason.takeIf { !available },
                    onSelect = { vm.stageMode(intent.mode) },
                )
            }
        }
    }
}

// ── لوحة المراجعة والتطبيق ────────────────────────────────────────────────────

@Composable
private fun GpuReview(state: GpuStudioUiState, vm: GpuStudioViewModel) {
    val device = state.device ?: return
    val pending = state.pending ?: return

    MaxSection(title = stringResource(R.string.max_gpu_review_title)) {
        MaxGroup {
            Column(
                modifier = Modifier.padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.rowPaddingVertical
                ),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
            ) {
                state.stagedIntent?.let { staged ->
                    Text(
                        text = stringResource(
                            R.string.max_gpu_review_intent,
                            stringResource(GpuIntent.entries.first { it.mode == staged }.labelRes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.max_gpu_review_current, liveRange(device)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (pending.releaseLock) {
                        stringResource(R.string.max_gpu_review_release)
                    } else {
                        stringResource(R.string.max_gpu_review_requested, requestedRange(device, pending))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            MaxGroupDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaxSpace.rowPaddingHorizontal,
                        vertical = MaxSpace.rowPaddingVertical
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = vm::applyPreview,
                    modifier = Modifier.weight(1f),
                    // الزرّ معطّل لنفس السبب الذي يمنع الكتابة في الطبقة الخلفية —
                    // لا لتحفّظ بصري.
                    enabled = !state.applying && GpuHardwareBackend.validate(device, pending) == null,
                ) {
                    Text(
                        stringResource(
                            if (state.applying) R.string.max_gpu_applying else R.string.max_gpu_apply
                        )
                    )
                }
                OutlinedButton(
                    onClick = vm::cancelPreview,
                    modifier = Modifier.weight(1f),
                    enabled = !state.applying,
                ) {
                    Text(stringResource(R.string.max_gpu_cancel))
                }
            }

            MaxGroupDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaxSpace.rowPaddingHorizontal,
                        vertical = MaxSpace.xs
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = vm::restoreSession, enabled = !state.applying) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = null,
                        modifier = Modifier.width(MaxSize.iconGlyphSmall),
                    )
                    Spacer(Modifier.width(MaxSpace.xs))
                    Text(stringResource(R.string.max_gpu_restore_session))
                }
                if (state.verifiedSnapshot != null) {
                    TextButton(onClick = vm::saveVerifiedToTweaks) {
                        Text(stringResource(R.string.max_gpu_save_tweaks))
                    }
                }
            }
        }
    }
}

private fun liveRange(device: GpuHardwareBackend.Device): String =
    "${GpuHardwareBackend.frequencyMHz(device, device.minFreq) ?: "—"} – " +
        "${GpuHardwareBackend.frequencyMHz(device, device.maxFreq) ?: "—"} MHz"

private fun requestedRange(device: GpuHardwareBackend.Device, pending: GpuHardwareBackend.Request): String {
    val min = GpuHardwareBackend.frequencyMHz(device, pending.minFreq) ?: "—"
    val max = GpuHardwareBackend.frequencyMHz(device, pending.maxFreq) ?: "—"
    val governor = pending.governor ?: device.governor ?: "—"
    return "$min – $max MHz • $governor"
}

// ── المختبر المتقدّم ─────────────────────────────────────────────────────────

@Composable
private fun GpuAdvancedLab(
    state: GpuStudioUiState,
    vm: GpuStudioViewModel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val device = state.device ?: return

    MaxSection(
        title = stringResource(R.string.max_gpu_advanced_title),
        description = stringResource(R.string.max_gpu_advanced_desc),
        trailing = {
            TextButton(onClick = { onExpandedChange(!expanded) }) {
                Text(
                    stringResource(
                        if (expanded) R.string.max_gpu_diagnostics_collapse
                        else R.string.max_gpu_diagnostics_expand
                    )
                )
            }
        },
    ) {
        if (!expanded) return@MaxSection

        if (!device.exactLockWritable) {
            MaxRow(
                title = stringResource(R.string.max_gpu_no_writable_range),
                icon = Icons.Outlined.Memory,
                iconTone = MaxTone.Inactive,
            )
        } else {
            GpuRangeControls(state, vm, device)
        }

        if (device.governorWritable && device.governors.isNotEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_gpu_governor_label),
                    subtitle = device.governor ?: stringResource(R.string.max_gpu_value_absent),
                    icon = Icons.Outlined.Balance,
                    iconTone = MaxTone.Neutral,
                )
                MaxGroupDivider()
                val selected = state.pending?.governor ?: device.governor
                device.governors.forEachIndexed { index, governor ->
                    if (index > 0) MaxGroupDivider()
                    MaxChoiceRow(
                        title = governor,
                        selected = selected == governor,
                        onSelect = { vm.stageGovernor(governor) },
                    )
                }
            }
        } else {
            MaxRow(
                title = stringResource(R.string.max_gpu_no_governors),
                icon = Icons.Outlined.Balance,
                iconTone = MaxTone.Inactive,
            )
        }
    }
}

/**
 * Range or exact-lock, as one switch plus the sliders that follow from it.
 *
 * The old screen hid this behind a `FilterChip` labelled "قفل دقيق" inside a
 * collapsed card, so an exact lock read as a filter rather than as the mode that
 * changes what the sliders below mean. It is now the row that names the choice.
 */
@Composable
private fun GpuRangeControls(
    state: GpuStudioUiState,
    vm: GpuStudioViewModel,
    device: GpuHardwareBackend.Device,
) {
    val frequencies = device.frequencies
    if (frequencies.isEmpty()) return

    val pendingMin = state.pending?.minFreq
    val pendingMax = state.pending?.maxFreq
    val liveMinIndex = frequencies.indexOf(pendingMin ?: device.minFreq).takeIf { it >= 0 } ?: 0
    val liveMaxIndex = frequencies.indexOf(pendingMax ?: device.maxFreq).takeIf { it >= 0 } ?: frequencies.lastIndex

    // الجهاز الذي لا يقبل نطاقًا مكتوبًا يبقى على القفل الدقيق؛ ونفس البدء القديم محفوظ.
    var fixedFrequency by remember(device.path) { mutableStateOf(!device.rangeWritable) }
    var minIndex by remember(device.path, liveMinIndex) { mutableIntStateOf(liveMinIndex) }
    var maxIndex by remember(device.path, liveMaxIndex) { mutableIntStateOf(liveMaxIndex) }
    var lockIndex by remember(device.path, liveMaxIndex) { mutableIntStateOf(liveMaxIndex) }

    MaxGroup {
        MaxSwitchRow(
            title = stringResource(R.string.max_gpu_fixed_title),
            checked = fixedFrequency,
            onCheckedChange = { fixedFrequency = it },
            subtitle = stringResource(
                if (fixedFrequency) R.string.max_gpu_fixed_on_desc else R.string.max_gpu_fixed_off_desc
            ),
            icon = Icons.Rounded.Bolt,
            iconTone = MaxTone.Accent,
        )

        MaxGroupDivider()

        if (fixedFrequency) {
            MaxSliderRow(
                title = stringResource(R.string.max_gpu_fixed_value),
                value = lockIndex.toFloat(),
                onValueChange = { lockIndex = it.roundToInt().coerceIn(0, frequencies.lastIndex) },
                valueText = format(device, frequencies[lockIndex]),
                valueRange = 0f..frequencies.lastIndex.toFloat(),
                steps = (frequencies.size - 2).coerceAtLeast(0),
                onValueChangeFinished = { vm.stageLock(frequencies[lockIndex]) },
            )
        } else {
            MaxSliderRow(
                title = stringResource(R.string.max_gpu_range_min),
                value = minIndex.toFloat(),
                onValueChange = { minIndex = it.roundToInt().coerceIn(0, maxIndex) },
                valueText = format(device, frequencies[minIndex]),
                valueRange = 0f..frequencies.lastIndex.toFloat(),
                steps = (frequencies.size - 2).coerceAtLeast(0),
                onValueChangeFinished = { vm.stageRange(frequencies[minIndex], frequencies[maxIndex]) },
            )
            MaxSliderRow(
                title = stringResource(R.string.max_gpu_range_max),
                value = maxIndex.toFloat(),
                onValueChange = { maxIndex = it.roundToInt().coerceIn(minIndex, frequencies.lastIndex) },
                valueText = format(device, frequencies[maxIndex]),
                valueRange = 0f..frequencies.lastIndex.toFloat(),
                steps = (frequencies.size - 2).coerceAtLeast(0),
                onValueChangeFinished = { vm.stageRange(frequencies[minIndex], frequencies[maxIndex]) },
            )
        }
    }
}

// ── حقيقة العتاد ─────────────────────────────────────────────────────────────

@Composable
private fun GpuDiagnostics(state: GpuStudioUiState, device: GpuHardwareBackend.Device) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    MaxGroup {
        MaxRow(
            title = stringResource(R.string.max_gpu_diagnostics_title),
            subtitle = stringResource(
                R.string.max_gpu_diagnostics_count,
                device.frequencies.size,
                device.governors.size,
            ),
            icon = Icons.Outlined.Memory,
            iconTone = MaxTone.Neutral,
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier.padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.rowPaddingVertical
                ),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
            ) {
                TechnicalLine(stringResource(R.string.max_gpu_diagnostics_provider), device.path)
                TechnicalLine(
                    stringResource(R.string.max_gpu_diagnostics_evidence),
                    device.evidence.joinToString().ifBlank { "—" },
                )
                TechnicalLine(
                    stringResource(R.string.max_gpu_diagnostics_selection),
                    state.selection?.reason ?: "—",
                )
            }
        }
    }
}

/** Machine truth in mono, the same treatment the rest of the app gives raw causes. */
@Composable
private fun TechnicalLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MonoValueStyleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── الترجمة ──────────────────────────────────────────────────────────────────

/**
 * A notice is a code from the ViewModel, rendered here.
 *
 * It used to be an Arabic sentence produced in the background layer, which then
 * appeared untranslated in the English UI. The code is mapped to text at the same
 * place every other string is.
 */
@Composable
private fun noticeCondition(notice: GpuNotice): MaxCondition {
    val kind = when (notice.kind) {
        GpuNoticeKind.STAGED_INTENT,
        GpuNoticeKind.STAGED_RANGE,
        GpuNoticeKind.STAGED_LOCK,
        GpuNoticeKind.STAGED_GOVERNOR,
        GpuNoticeKind.CANCELLED,
        GpuNoticeKind.PROVIDER_CHANGED,
        GpuNoticeKind.DRIFTED,
        -> MaxConditionKind.Applying

        GpuNoticeKind.VERIFIED -> MaxConditionKind.Applied

        GpuNoticeKind.REFUSED,
        GpuNoticeKind.ROLLBACK_FAILED,
        -> MaxConditionKind.Failed

        GpuNoticeKind.ROLLED_BACK -> MaxConditionKind.Failed
        GpuNoticeKind.NO_TABLE -> MaxConditionKind.Unsupported
    }

    val titleRes = when (notice.kind) {
        GpuNoticeKind.STAGED_INTENT, GpuNoticeKind.STAGED_RANGE,
        GpuNoticeKind.STAGED_LOCK, GpuNoticeKind.STAGED_GOVERNOR,
        -> R.string.max_gpu_notice_staged_title

        GpuNoticeKind.CANCELLED -> R.string.max_gpu_notice_cancelled_title
        GpuNoticeKind.NO_TABLE -> R.string.max_gpu_notice_no_table_title
        GpuNoticeKind.REFUSED -> R.string.max_gpu_notice_refused_title
        GpuNoticeKind.VERIFIED -> R.string.max_gpu_notice_verified_title
        GpuNoticeKind.ROLLED_BACK -> R.string.max_gpu_notice_rolled_back_title
        GpuNoticeKind.ROLLBACK_FAILED -> R.string.max_gpu_notice_rollback_failed_title
        GpuNoticeKind.PROVIDER_CHANGED -> R.string.max_gpu_notice_provider_changed_title
        GpuNoticeKind.DRIFTED -> R.string.max_gpu_notice_drifted_title
    }

    val detail = when (notice.kind) {
        GpuNoticeKind.VERIFIED -> stringResource(R.string.max_gpu_notice_verified_detail)
        GpuNoticeKind.ROLLED_BACK -> stringResource(R.string.max_gpu_notice_rolled_back_detail)
        GpuNoticeKind.ROLLBACK_FAILED -> stringResource(R.string.max_gpu_notice_rollback_failed_detail)
        GpuNoticeKind.PROVIDER_CHANGED -> stringResource(R.string.max_gpu_notice_provider_changed_detail)
        GpuNoticeKind.DRIFTED -> stringResource(R.string.max_gpu_notice_drifted_detail)

        GpuNoticeKind.STAGED_INTENT -> stringResource(
            notice.intent?.let { mode ->
                GpuIntent.entries.first { it.mode == mode }.descRes
            } ?: R.string.max_gpu_intent_adaptive_desc
        )

        GpuNoticeKind.STAGED_RANGE -> stringResource(R.string.max_gpu_range_desc)
        GpuNoticeKind.STAGED_LOCK -> stringResource(R.string.max_gpu_fixed_on_desc)
        GpuNoticeKind.STAGED_GOVERNOR -> stringResource(R.string.max_gpu_governor_staged_desc)
        GpuNoticeKind.CANCELLED -> stringResource(R.string.max_gpu_notice_cancelled_detail)
        GpuNoticeKind.NO_TABLE -> stringResource(R.string.max_gpu_intents_locked)
        GpuNoticeKind.REFUSED -> stringResource(reasonRes(notice.reason))
    }

    return MaxCondition(
        kind = kind,
        title = stringResource(titleRes),
        detail = detail,
        // كود السبب كما هو من الطبقة الخلفية: حقيقة تقنية لا جملة، ولا تُترجم.
        technicalDetail = notice.reason?.let { "${stringResource(R.string.max_gpu_reason_technical)}: $it" },
    )
}

@StringRes
private fun reasonRes(reason: String?): Int = when (reason) {
    "unsupported-frequency" -> R.string.max_gpu_reason_unsupported_frequency
    "unsupported-governor" -> R.string.max_gpu_reason_unsupported_governor
    "invalid-range" -> R.string.max_gpu_reason_invalid_range
    "range-read-only-or-unproven" -> R.string.max_gpu_reason_range_read_only
    "governor-read-only" -> R.string.max_gpu_reason_governor_read_only
    "exact-lock-read-only-or-unproven" -> R.string.max_gpu_reason_exact_lock_read_only
    "release-lock-unavailable" -> R.string.max_gpu_reason_release_lock_unavailable
    "incomplete-range" -> R.string.max_gpu_reason_incomplete_range
    "baseline-unreadable" -> R.string.max_gpu_reason_baseline_unreadable
    "ambiguous-frequency-unit" -> R.string.max_gpu_reason_ambiguous_unit
    "provider-ambiguous" -> R.string.max_gpu_reason_provider_ambiguous
    "provider-disappeared" -> R.string.max_gpu_reason_provider_disappeared
    "empty-request" -> R.string.max_gpu_reason_empty_request
    else -> R.string.max_gpu_reason_unknown
}

@Composable
private fun familyLabel(family: GpuHardwareBackend.Family): String = stringResource(
    when (family) {
        GpuHardwareBackend.Family.QUALCOMM -> R.string.max_gpu_family_qualcomm
        GpuHardwareBackend.Family.MALI -> R.string.max_gpu_family_mali
        GpuHardwareBackend.Family.UNKNOWN -> R.string.max_gpu_family_unknown
    }
)

private fun format(device: GpuHardwareBackend.Device, raw: Long?): String =
    GpuHardwareBackend.frequencyMHz(device, raw)?.let { "$it MHz" } ?: "—"
