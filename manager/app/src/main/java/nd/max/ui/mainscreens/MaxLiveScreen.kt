package nd.max.ui.mainscreens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nd.max.R
import nd.max.core.maxai.MaxAiCycleStatus
import nd.max.core.maxai.MaxAiEpisode
import nd.max.core.maxai.MaxAiInsights
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.SafetyLevel
import nd.max.core.maxai.TrustModel
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDuration
import nd.max.ui.design.MaxForecastChart
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSparkline
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxWeightBar
import nd.max.ui.design.content
import nd.max.ui.viewmodel.MaxAiViewModel

/**
 * Measured observations, model estimates and recorded control outcomes stay distinct.
 * Freshness expires without a new engine emission; opening this page requests no cycle.
 * Only measured bars have bounded transitions, and charts retain spoken descriptions.
 */
@Composable
fun MaxLiveScreen(
    navController: NavController,
    viewModel: MaxAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val safety by viewModel.safety.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val cycleStatus by viewModel.cycleStatus.collectAsStateWithLifecycle()
    val nowMs by viewModel.nowMs.collectAsStateWithLifecycle()
    val freshness = sampleFreshness(state.lastSampleAtMs, nowMs)

    val banner = when {
        safety.level == SafetyLevel.CRITICAL -> MaxCondition(
            kind = MaxConditionKind.Error,
            title = stringResource(R.string.max_ai_safety_critical_title),
            detail = stringResource(
                R.string.max_ai_safety_detail,
                liveThermal(safety.thermalC),
                safety.interventions,
            ),
            technicalDetail = safety.lastReason.takeIf { it.isNotBlank() },
        )
        safety.engaged -> MaxCondition(
            kind = MaxConditionKind.Applied,
            title = stringResource(R.string.max_ai_safety_engaged_title),
            detail = stringResource(
                R.string.max_ai_safety_detail,
                liveThermal(safety.thermalC),
                safety.interventions,
            ),
            technicalDetail = safety.lastReason.takeIf { it.isNotBlank() },
        )
        else -> null
    }

    MaxListScreen(
        title = stringResource(R.string.max_live_title),
        onBack = { navController.popBackStack() },
        subtitle = if (freshness == SampleFreshness.Missing) {
            stringResource(R.string.max_live_empty)
        } else {
            state.strategyLabel
        },
        accentIcon = Icons.Rounded.Psychology,
        accent = MaterialTheme.colorScheme.tertiary,
        banner = banner,
    ) {
        item(key = "vitals") { VitalsSection(state, freshness, nowMs) }
        item(key = "forecast") { LiveForecastSection(state, freshness) }
        item(key = "prediction_error") { PredictionErrorSection(episodes) }
        item(key = "knowledge") { KnowledgeSection(state) }
        item(key = "exploration") { ExplorationSection(state, freshness, nowMs) }
        item(key = "automation_plan") { AutomationPlanSection(state, freshness) }
        item(key = "loop_counters") { LoopCountersSection(insights) }
        item(key = "ownership") { OwnershipSection(state) }
        item(key = "actions") { ActionsSection(cycleStatus, viewModel::refresh) }
    }
}

// ── النبض والقياسات الحية ────────────────────────────

/** Observation freshness is independent of the automatic-decision preference. */
@Composable
private fun VitalsSection(state: MaxAiState, freshness: SampleFreshness, nowMs: Long) {
    val hasSample = freshness != SampleFreshness.Missing
    val observationTone = when (freshness) {
        SampleFreshness.Missing -> MaxTone.Inactive
        SampleFreshness.Stale -> MaxTone.Caution
        SampleFreshness.Live -> MaxTone.Positive
    }
    val observationText = when (freshness) {
        SampleFreshness.Missing -> stringResource(R.string.max_live_empty)
        SampleFreshness.Stale -> stringResource(R.string.max_live_pulse_stale)
        SampleFreshness.Live -> stringResource(R.string.max_trust_live)
    }

    MaxSection(
        title = stringResource(R.string.max_live_section_vitals),
        description = stringResource(R.string.max_live_section_vitals_desc),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        ) {
            LiveIndicator(tone = observationTone)
            Text(
                text = observationText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasSample) {
                MaxCapsule(
                    text = liveRelativeTime(nowMs - state.lastSampleAtMs),
                    tone = observationTone,
                )
            }
        }
        Text(
            text = stringResource(
                if (state.aiEnabled) R.string.max_ai_decisions_enabled else R.string.max_ai_decisions_disabled,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasSample) return@MaxSection

        LiveBar(
            label = stringResource(R.string.max_ai_metric_cpu),
            fraction = state.cpuLoadPercent / 100f,
            valueText = livePercent(state.cpuLoadPercent),
            tone = MaxTone.Accent,
        )
        LiveBar(
            label = stringResource(R.string.max_ai_metric_battery),
            fraction = state.batteryPercent / 100f,
            valueText = livePercent(state.batteryPercent),
            tone = MaxTone.Positive,
        )
        LiveBar(
            label = stringResource(R.string.max_ai_metric_memory),
            fraction = state.memoryPercent / 100f,
            valueText = livePercent(state.memoryPercent),
            tone = MaxTone.Neutral,
        )

        // الحرارة لا تُرسم كنسبة: لا سقف عالمي صادق لكل جهاز، فتُعرض قيمتها
        // المقيسة ومسارها الفعلي من عيّنات الجلسة بدل شريط مقياسه مُختلق.
        val thermalTrend = state.trend.map { it.thermalC }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.max_ai_metric_thermal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = liveThermal(state.thermalC),
                style = MaterialTheme.typography.bodyMedium,
                color = MaxTone.Caution.content(),
            )
        }
        if (thermalTrend.size >= 2) {
            MaxSparkline(
                values = thermalTrend,
                tone = MaxTone.Caution,
                label = curveDescription(
                    title = stringResource(R.string.max_ai_metric_thermal),
                    fromText = liveThermal(thermalTrend.first()),
                    toText = liveThermal(thermalTrend.last()),
                    spanMs = state.trend.last().timestampMs - state.trend.first().timestampMs,
                    samples = thermalTrend.size,
                ),
            )
        }
    }
}

/** A static status marker never implies unobserved engine activity. */
@Composable
private fun LiveIndicator(tone: MaxTone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MaxSize.iconGlyph)
            .background(tone.content(), CircleShape),
    )
}

/** شريط نسبة يتحرك من قيمته السابقة إلى المقيسة الجديدة. */
@Composable
private fun LiveBar(label: String, fraction: Float, valueText: String, tone: MaxTone) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(MaxDuration.standard),
        label = "maxLiveBar",
    )
    MaxWeightBar(label = label, fraction = animated, valueText = valueText, tone = tone)
}

// ── التوقع مقابل الواقع ──────────────────────────────

/**
 * أقوى دليل متاح على أن النظام يفهم الجهاز: التنبؤ الحراري الذي حسبه
 * المحرك أصلًا لحسابات السلامة، مرسومًا بجوار ما حدث فعلًا.
 */
@Composable
private fun LiveForecastSection(state: MaxAiState, freshness: SampleFreshness) {
    val points = state.thermalForecast
    MaxSection(
        title = stringResource(R.string.max_live_section_forecast),
        description = stringResource(R.string.max_live_section_forecast_desc),
    ) {
        if (points.size < 2) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_live_forecast_empty),
                    icon = Icons.Rounded.Timeline,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }

        if (freshness == SampleFreshness.Stale) {
            MaxCapsule(text = stringResource(R.string.max_trust_snapshot), tone = MaxTone.Caution)
        }

        val firstMeasured = points.firstOrNull { it.actualC != null }
        val lastMeasured = points.lastOrNull { it.actualC != null }
        val firstValue = firstMeasured?.actualC
        val lastValue = lastMeasured?.actualC
        val spoken = if (firstValue != null && lastValue != null && firstMeasured !== lastMeasured) {
            curveDescription(
                title = stringResource(R.string.max_live_section_forecast),
                fromText = liveThermal(firstValue),
                toText = liveThermal(lastValue),
                spanMs = lastMeasured.timestampMs - firstMeasured.timestampMs,
                samples = points.count { it.actualC != null },
            )
        } else {
            null
        }

        MaxForecastChart(
            actual = points.map { it.actualC },
            forecast = points.map { it.forecastC },
            futureFrom = points.indexOfFirst { it.future }
                .let { if (it < 0) points.size else it },
            tone = MaxTone.Accent,
            forecastTone = MaxTone.Caution,
            label = spoken,
        )

        // خطأ التوقع يُعرض فقط بعد أن تُطابَق نقطة واحدة على الأقل.
        state.forecastErrorC?.let { error ->
            Text(
                text = stringResource(R.string.max_live_forecast_error, liveThermal(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── انحدار خطأ التنبؤ ────────────────────────────────

/**
 * الإثبات الصادق الوحيد على تحسّن الذكاء: |تنبؤ − مقيس| لكل حلقة عبر
 * الزمن. منحنى هابط = النموذج يتعلّم؛ منحنى مستوٍ أو صاعد = لا يتعلّم،
 * ويُقال ذلك بصراحة بدل تزيينه.
 */
@Composable
private fun PredictionErrorSection(episodes: List<MaxAiEpisode>) {
    // الدفتر يعطي الأحدث أولًا؛ المنحنى الزمني يحتاج العكس.
    val measured = episodes.reversed().filter { it.predictionErrorGain != null }
    MaxSection(
        title = stringResource(R.string.max_live_section_error),
        description = stringResource(R.string.max_live_section_error_desc),
    ) {
        if (measured.size < 2) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_live_error_empty),
                    icon = Icons.Rounded.Insights,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }

        val values = measured.mapNotNull { it.predictionErrorGain }
        val first = values.first()
        val last = values.last()
        val improving = last < first
        val tone = if (improving) MaxTone.Positive else MaxTone.Caution

        MaxSparkline(
            values = values,
            tone = tone,
            label = curveDescription(
                title = stringResource(R.string.max_live_section_error),
                fromText = liveScore(first),
                toText = liveScore(last),
                spanMs = measured.last().id - measured.first().id,
                samples = values.size,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        ) {
            MaxCapsule(
                text = stringResource(
                    if (improving) R.string.max_live_error_down else R.string.max_live_error_up,
                ),
                tone = tone,
            )
            Text(
                text = stringResource(
                    R.string.max_live_error_detail,
                    liveScore(first),
                    liveScore(last),
                    values.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── ما يعرفه وما يجهله ───────────────────────────────

/**
 * توزيع المعرفة على المقابض المتاحة: المجهول أولًا لأنه سبب وجود
 * الاستكشاف، لا قائمة إنجازات.
 */
@Composable
private fun KnowledgeSection(state: MaxAiState) {
    val knobs = state.trust
    MaxSection(
        title = stringResource(R.string.max_live_section_knowledge),
        description = stringResource(R.string.max_live_section_knowledge_desc),
    ) {
        if (knobs.isEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_live_knowledge_empty),
                    icon = Icons.Rounded.Science,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }

        val total = knobs.size.toFloat()
        knowledgeOrder().forEach { (knowledge, labelRes) ->
            val count = knobs.count { it.knowledge == knowledge }
            if (count == 0) return@forEach
            LiveBar(
                label = stringResource(labelRes),
                fraction = count / total,
                valueText = stringResource(R.string.max_live_knowledge_count, count, knobs.size),
                tone = knowledgeTone(knowledge),
            )
        }

        MaxGroup {
            knobs.sortedBy { it.knowledge.ordinal }.forEachIndexed { index, knob ->
                if (index > 0) MaxGroupDivider()
                MaxRow(
                    title = knob.label,
                    subtitle = stringResource(
                        R.string.max_live_knob_row,
                        knob.samples,
                        liveScore(knob.epistemic),
                        liveScore(knob.informationGain),
                    ),
                    trailing = {
                        MaxCapsule(
                            text = stringResource(knowledgeLabel(knob.knowledge)),
                            tone = knowledgeTone(knob.knowledge),
                        )
                    },
                )
            }
        }
    }
}

// ── بوابة الاستكشاف ──────────────────────────────────

/**
 * لماذا لم يجرّب النظام شيئًا الآن — بالسبب المسمى لا بالصمت.
 *
 * هذه هي الترجمة المرئية لقاعدة "أعرف ما الذي لا أعرفه، وأجرّبه فقط حين
 * تكون تكلفة الخطأ منخفضة": لا تجربة عشوائية، وكل امتناع له سبب مقروء.
 */
@Composable
private fun ExplorationSection(state: MaxAiState, freshness: SampleFreshness, nowMs: Long) {
    val exploration = state.exploration
    MaxSection(
        title = stringResource(R.string.max_live_section_exploration),
        description = stringResource(R.string.max_live_section_exploration_desc),
    ) {
        if (freshness == SampleFreshness.Missing) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_live_empty),
                    icon = Icons.Rounded.Science,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }
        MaxGroup {
            val blocked = exploration.blockReason
            val canExplore = state.aiEnabled && freshness == SampleFreshness.Live && blocked == null
            MaxRow(
                title = when {
                    !state.aiEnabled -> stringResource(R.string.max_ai_decisions_disabled)
                    freshness == SampleFreshness.Stale -> stringResource(R.string.max_live_pulse_stale)
                    blocked != null -> stringResource(R.string.max_live_explore_blocked, blockText(blocked))
                    else -> stringResource(R.string.max_live_explore_allowed)
                },
                subtitle = stringResource(
                    R.string.max_live_explore_budget,
                    exploration.probesThisSession,
                    exploration.budget,
                ),
                icon = Icons.Rounded.Science,
                iconTone = if (canExplore) MaxTone.Accent else MaxTone.Inactive,
            )
            // المرشح يُعرض فقط حين وُجد فعلًا مقبض مجهول الأثر.
            if (exploration.targetLabel != null) {
                MaxGroupDivider()
                MaxRow(
                    title = exploration.targetLabel,
                    subtitle = stringResource(
                        R.string.max_live_explore_target,
                        exploration.worstCaseCost?.let { liveScore(it) }
                            ?: stringResource(R.string.max_ai_value_unknown),
                        exploration.informationGain?.let { liveScore(it) }
                            ?: stringResource(R.string.max_ai_value_unknown),
                    ),
                )
            }
            if (exploration.lastProbeAtMs > 0L) {
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_live_explore_last),
                    subtitle = liveRelativeTime(
                        nowMs - exploration.lastProbeAtMs,
                    ),
                )
            }
        }
    }
}


// ── خطة التحكم الذكي ─────────────────────────────────

/**
 * تحول الملكية/السلامة/التعلم إلى خطة مفهومة: ما الوضع، لماذا، وما الإجراء
 * التالي. لا تنفذ مسارًا موازيًا؛ تعرض فقط قرار المحرك القابل للتراجع.
 */
@Composable
private fun AutomationPlanSection(state: MaxAiState, freshness: SampleFreshness) {
    val plan = state.automationPlan
    val tone = when (plan.mode) {
        "Safety guard" -> MaxTone.Critical
        "User-locked" -> MaxTone.Caution
        "Adaptive", "Hold" -> MaxTone.Positive
        "Learning" -> MaxTone.Accent
        else -> MaxTone.Neutral
    }
    MaxSection(
        title = stringResource(R.string.max_live_automation_title),
        description = stringResource(R.string.max_live_automation_desc),
    ) {
        if (freshness == SampleFreshness.Missing) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_live_empty),
                    icon = Icons.Rounded.Tune,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }
        if (!state.aiEnabled || freshness == SampleFreshness.Stale) {
            Text(
                text = stringResource(R.string.max_live_plan_snapshot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MaxGroup {
            MaxRow(
                title = plan.mode,
                subtitle = plan.reason,
                icon = Icons.Rounded.Tune,
                iconTone = tone,
                trailing = {
                    if (plan.mode != "Safety guard") {
                        MaxCapsule(
                            text = stringResource(R.string.max_ai_learning_coverage, plan.confidencePercent),
                            tone = tone,
                        )
                    }
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_live_profile_hint),
                subtitle = plan.profileHint,
                icon = Icons.Rounded.Psychology,
                iconTone = MaxTone.Accent,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_live_next_action),
                subtitle = plan.nextAction,
                icon = Icons.Rounded.Refresh,
                iconTone = MaxTone.Positive,
                trailing = {
                    MaxCapsule(
                        text = if (plan.reversible) stringResource(R.string.max_live_reversible) else stringResource(R.string.max_live_not_reversible),
                        tone = if (plan.reversible) MaxTone.Positive else MaxTone.Critical,
                    )
                },
            )
        }
    }
}

// ── عدادات الحلقات ───────────────────────────────────

/**
 * كم حلقة من كل نوع جرت فعلًا: قرار مفيد، تراجع مسترجع، تدخل سلامة،
 * انحراف مقبض، تجاوز من المستخدم، وحكم مؤجل قيس بالفعل.
 *
 * التجاوزات ليست عيبًا يُخفى: هي إشارة الرضا الوحيدة الصادقة، وعرضها
 * بجوار النجاحات هو ما يجعل بقية الأرقام قابلة للتصديق.
 */
@Composable
private fun LoopCountersSection(snapshot: MaxAiInsights.Snapshot) {
    MaxSection(
        title = stringResource(R.string.max_live_section_loops),
        description = stringResource(R.string.max_live_section_loops_desc),
    ) {
        MaxGroup {
            LiveCountRow(
                title = stringResource(R.string.max_live_count_improved),
                count = snapshot.improved,
                tone = MaxTone.Positive,
            )
            MaxGroupDivider()
            LiveCountRow(
                title = stringResource(R.string.max_live_count_rolled_back),
                count = snapshot.rolledBack,
                tone = MaxTone.Caution,
            )
            MaxGroupDivider()
            LiveCountRow(
                title = stringResource(R.string.max_live_count_safety),
                count = snapshot.safetyEvents,
                tone = MaxTone.Critical,
            )
            MaxGroupDivider()
            LiveCountRow(
                title = stringResource(R.string.max_live_count_drift),
                count = snapshot.driftEvents,
                tone = MaxTone.Caution,
            )
            MaxGroupDivider()
            LiveCountRow(
                title = stringResource(R.string.max_live_count_override),
                count = snapshot.userOverrides,
                tone = MaxTone.Accent,
            )
            MaxGroupDivider()
            LiveCountRow(
                title = stringResource(R.string.max_live_count_deferred),
                count = snapshot.deferredMeasured,
                tone = MaxTone.Neutral,
            )
            MaxGroupDivider()
            LiveCountRow(
                title = stringResource(R.string.max_live_count_explorations),
                count = snapshot.explorations,
                tone = MaxTone.Accent,
            )
        }
    }
}

/** Render recorded counts directly, never intermediate, unmeasured integers. */
@Composable
private fun LiveCountRow(title: String, count: Int, tone: MaxTone) {
    MaxRow(
        title = title,
        trailing = { MaxCapsule(text = count.toString(), tone = tone) },
    )
}

// ── الملكية والأقفال ─────────────────────────────────

/** من يملك كل مقبض فعلًا، وما أغلقه المستخدم أمام كل مالك آلي. */
@Composable
private fun OwnershipSection(state: MaxAiState) {
    MaxSection(
        title = stringResource(R.string.max_ai_section_system),
        description = stringResource(R.string.max_ai_section_system_desc),
    ) {
        MaxGroup {
            if (state.ownership.isEmpty()) {
                MaxRow(
                    title = stringResource(R.string.max_ai_owner_none),
                    iconTone = MaxTone.Inactive,
                )
            } else {
                state.ownership.forEachIndexed { index, knob ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = knob.key,
                        subtitle = stringResource(
                            R.string.max_ai_owner_row,
                            knob.owner.name,
                            knob.desired,
                        ),
                        icon = if (knob.locked) Icons.Rounded.Lock else null,
                        iconTone = if (knob.locked) MaxTone.Caution else MaxTone.Neutral,
                        trailing = {
                            MaxCapsule(
                                text = if (knob.state.name == "VERIFIED") {
                                    stringResource(R.string.max_ai_owner_verified)
                                } else {
                                    stringResource(R.string.max_ai_owner_pending)
                                },
                                tone = if (knob.state.name == "VERIFIED") {
                                    MaxTone.Positive
                                } else {
                                    MaxTone.Caution
                                },
                            )
                        },
                    )
                }
            }
        }

        if (state.lockedKnobs.isNotEmpty()) {
            MaxGroup {
                state.lockedKnobs.forEachIndexed { index, lock ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = lock.key,
                        subtitle = stringResource(R.string.max_ai_locked_row, lock.desired),
                        icon = Icons.Rounded.Lock,
                        iconTone = MaxTone.Caution,
                    )
                }
            }
        }
    }
}

// ── إجراء واحد ───────────────────────────────────────

@Composable
private fun ActionsSection(status: MaxAiCycleStatus, onRefresh: () -> Unit) {
    MaxSection(title = stringResource(R.string.max_ai_section_controls)) {
        MaxAiRuntimeStatus(status, onRefresh)
    }
}

// ── وصف المنحنيات لقارئ الشاشة ───────────────────────

/**
 * وصف نصي لمنحنى: اتجاه، قيمة البداية والنهاية، المدة، وعدد العيّنات.
 *
 * بلا هذا الوصف يكون المنحنى لمستخدم TalkBack غيابًا كاملًا للمعلومة، لا
 * مجرد ضعف في العرض.
 */
@Composable
private fun curveDescription(
    title: String,
    fromText: String,
    toText: String,
    spanMs: Long,
    samples: Int,
): String {
    val minutes = (spanMs / 60_000L).coerceAtLeast(1L).toInt()
    return stringResource(
        R.string.max_live_curve_desc,
        title,
        fromText,
        toText,
        minutes,
        samples,
    )
}

// ── ترجمة القيم الثابتة ──────────────────────────────

@Composable
private fun blockText(reason: String): String = when (reason) {
    TrustModel.Block.SAFETY -> stringResource(R.string.max_live_block_safety)
    TrustModel.Block.THERMAL_HEADROOM -> stringResource(R.string.max_live_block_thermal)
    TrustModel.Block.BATTERY -> stringResource(R.string.max_live_block_battery)
    TrustModel.Block.CONTEXT_BUSY -> stringResource(R.string.max_live_block_busy)
    TrustModel.Block.COOLDOWN -> stringResource(R.string.max_live_block_cooldown)
    TrustModel.Block.SESSION_BUDGET -> stringResource(R.string.max_live_block_budget)
    TrustModel.Block.NOTHING_UNKNOWN -> stringResource(R.string.max_live_block_nothing)
    TrustModel.Block.IRREVERSIBLE -> stringResource(R.string.max_live_block_irreversible)
    TrustModel.Block.COST_TOO_HIGH -> stringResource(R.string.max_live_block_cost)
    else -> stringResource(R.string.max_ai_value_unknown)
}

private fun knowledgeLabel(knowledge: TrustModel.Knowledge): Int = when (knowledge) {
    TrustModel.Knowledge.UNKNOWN -> R.string.max_live_knowledge_unknown
    TrustModel.Knowledge.UNCERTAIN -> R.string.max_live_knowledge_uncertain
    TrustModel.Knowledge.KNOWN_USEFUL -> R.string.max_live_knowledge_useful
    TrustModel.Knowledge.KNOWN_NEUTRAL -> R.string.max_live_knowledge_neutral
    TrustModel.Knowledge.KNOWN_COSTLY -> R.string.max_live_knowledge_costly
}

private fun knowledgeTone(knowledge: TrustModel.Knowledge): MaxTone = when (knowledge) {
    TrustModel.Knowledge.UNKNOWN -> MaxTone.Inactive
    TrustModel.Knowledge.UNCERTAIN -> MaxTone.Caution
    TrustModel.Knowledge.KNOWN_USEFUL -> MaxTone.Positive
    TrustModel.Knowledge.KNOWN_NEUTRAL -> MaxTone.Neutral
    TrustModel.Knowledge.KNOWN_COSTLY -> MaxTone.Critical
}

/** المجهول أولًا: الترتيب يخبر المستخدم بما يحتاجه النظام لا بما أنجزه. */
private fun knowledgeOrder(): List<Pair<TrustModel.Knowledge, Int>> = listOf(
    TrustModel.Knowledge.UNKNOWN to R.string.max_live_knowledge_unknown,
    TrustModel.Knowledge.UNCERTAIN to R.string.max_live_knowledge_uncertain,
    TrustModel.Knowledge.KNOWN_USEFUL to R.string.max_live_knowledge_useful,
    TrustModel.Knowledge.KNOWN_NEUTRAL to R.string.max_live_knowledge_neutral,
    TrustModel.Knowledge.KNOWN_COSTLY to R.string.max_live_knowledge_costly,
)

// ── تنسيق ────────────────────────────────────────────

private fun livePercent(value: Int): String = "$value%"

private fun liveThermal(value: Float): String = "%.1f°".format(value)

private fun liveScore(value: Float): String = "%.3f".format(value)

@Composable
private fun liveRelativeTime(elapsedMs: Long): String {
    if (elapsedMs < 0L) return stringResource(R.string.max_ai_value_unknown)
    val minutes = elapsedMs / 60_000L
    return when {
        minutes < 1L -> stringResource(R.string.max_ai_time_now)
        minutes < 60L -> stringResource(R.string.max_ai_time_minutes, minutes.toInt())
        else -> stringResource(R.string.max_ai_time_hours, (minutes / 60L).toInt())
    }
}
