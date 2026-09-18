package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.core.hardware.ProfileApplier
import nd.max.core.maxai.ControlRegistry
import nd.max.core.maxai.MaxAiCandidate
import nd.max.core.maxai.MaxAiEpisode
import nd.max.core.maxai.MaxAiEpisodeKind
import nd.max.core.maxai.MaxAiInsights
import nd.max.core.maxai.MaxAiOverride
import nd.max.core.maxai.MaxAiRejection
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.MaxAiVerdict
import nd.max.core.maxai.OwnershipCommitState
import nd.max.core.maxai.SafetyEnforcement
import nd.max.core.maxai.SafetyLevel
import nd.max.core.maxai.SafetyStatus
import nd.max.core.maxai.TrustModel
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxCausalStage
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxDeltaRow
import nd.max.ui.design.MaxEpisodeCard
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricReadout
import nd.max.ui.design.MaxMetricSize
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSparkline
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxWeightBar
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.viewmodel.MaxAiViewModel
import kotlin.math.abs

/*
 * MAX AI — التجربة السببية.
 *
 * الشاشة السابقة كانت تعرض أربعة عدادات وسطر "آخر إجراء"، فكان
 * المستخدم يرى أن النطام يعمل بلا أن يرى ماذا يفعل ولماذا. الآن
 * تُسرد كل دورة قرار كحلقة متكاملة:
 *
 *   الحالة المقيسة → ما لوحِط → لماذا كان مهمًا → القرار → ما تغير →
 *   ما حدر بعده → الحكم → الأثر المقيس → ما تعلّمه النطام
 *
 * قاعدة ملزمة: كل عنصر بصري هنا يمثل حقلًا واحدًا من دفتر المحرك
 * (MaxAiJournal) أو من حالته المنشورة. لا رسم زخرفي، ولا قيمة توليدية:
 * ما لم يُقس (مثلًا تعذر قياس "بعد") يُعرض كغير متوفر وليس بصفر.
 * الخط الزمني فارغ تمامًا قبل أول دورة حقيقية — وهذا مقصود.
 */
@Composable
fun MaxAiScreen(
    navController: NavHostController,
    viewModel: MaxAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val safety by viewModel.safety.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()

    // دورة فورية عند دخول الشاشة: القياسات المعروضة حالية لا قديمة.
    LaunchedEffect(Unit) { viewModel.refresh() }

    var expandedEpisode by rememberSaveable { mutableStateOf<Long?>(null) }
    var timelineFilter by rememberSaveable { mutableStateOf(TimelineFilter.All) }
    var verdictFilter by rememberSaveable { mutableStateOf<MaxAiVerdict?>(null) }
    var timelineExpanded by rememberSaveable { mutableStateOf(false) }

    // Filter the retained journal before taking the preview, without changing its order.
    val filteredEpisodes = episodes.filter {
        timelineFilter.matches(it.kind, it.verdict, verdictFilter)
    }
    val visibleEpisodes = if (timelineExpanded) {
        filteredEpisodes
    } else {
        filteredEpisodes.take(MaxAiTimelinePreview)
    }
    val timelineEmptyTitle = stringResource(
        if (episodes.isEmpty()) {
            R.string.max_ai_timeline_empty_title
        } else {
            R.string.max_ai_timeline_no_matches
        },
    )
    val timelineEmptySubtitle = if (episodes.isEmpty()) {
        if (state.aiEnabled) {
            stringResource(R.string.max_ai_timeline_empty_on)
        } else {
            stringResource(R.string.max_ai_timeline_empty_off)
        }
    } else {
        null
    }

    val banner = when {
        safety.level == SafetyLevel.CRITICAL -> MaxCondition(
            kind = MaxConditionKind.Error,
            title = stringResource(R.string.max_ai_safety_critical_title),
            detail = stringResource(
                R.string.max_ai_safety_detail,
                formatThermal(safety.thermalC),
                safety.interventions,
            ),
            technicalDetail = safety.lastReason.takeIf { it.isNotBlank() },
        )
        safety.engaged -> MaxCondition(
            kind = MaxConditionKind.Applied,
            title = stringResource(R.string.max_ai_safety_engaged_title),
            detail = stringResource(
                R.string.max_ai_safety_detail,
                formatThermal(safety.thermalC),
                safety.interventions,
            ),
            technicalDetail = safety.lastReason.takeIf { it.isNotBlank() },
        )
        else -> null
    }

    MaxListScreen(
        title = stringResource(R.string.max_ai_title),
        onBack = { navController.popBackStack() },
        subtitle = state.strategyLabel,
        accentIcon = Icons.Rounded.Psychology,
        accent = MaterialTheme.colorScheme.tertiary,
        banner = banner,
        // سطح التحكم أولًا: المفتاح الرئيسي وأهم الإجراءات قبل أي شرح تفصيلي.
        header = { ControlDeck(state, viewModel, navController) },
    ) {
        item { NowHeader(state) }

        item { ObjectiveSection(state, viewModel) }

        item { SafetySection(safety) }

        item(key = "max_ai_timeline_header") {
            TimelineHeader(
                filter = timelineFilter,
                verdict = verdictFilter,
                shown = visibleEpisodes.size,
                matched = filteredEpisodes.size,
                onFilterChange = {
                    timelineFilter = it
                    timelineExpanded = false
                    expandedEpisode = null
                },
                onVerdictChange = {
                    verdictFilter = it
                    timelineExpanded = false
                    expandedEpisode = null
                },
            )
        }

        if (filteredEpisodes.isEmpty()) {
            item(key = "max_ai_timeline_empty") {
                MaxGroup {
                    MaxRow(
                        title = timelineEmptyTitle,
                        subtitle = timelineEmptySubtitle,
                        icon = Icons.Rounded.Insights,
                        iconTone = MaxTone.Inactive,
                    )
                }
            }
        } else {
            items(visibleEpisodes, key = { it.id }) { episode ->
                EpisodeTimelineCard(
                    episode = episode,
                    expanded = expandedEpisode == episode.id,
                    onToggle = {
                        expandedEpisode = if (expandedEpisode == episode.id) null else episode.id
                    },
                )
            }
        }

        if (filteredEpisodes.size > MaxAiTimelinePreview) {
            item(key = "max_ai_timeline_toggle") {
                TimelineToggleRow(
                    expanded = timelineExpanded,
                    matched = filteredEpisodes.size,
                    onToggle = { timelineExpanded = !timelineExpanded },
                )
            }
        }

        item { InsightsSection(insights) }

        item { OwnershipSection(state) }

        item { ProfilesSection(state, viewModel) }
    }
}

/** حدود المعاينة القصيرة للخط الزمني قبل أن يطلب المستخدم البقية. */
private const val MaxAiTimelinePreview = 5

/** Filters use recorded facts, never localized labels or inferred success. */
internal enum class TimelineFilter {
    All, Decisions, Probes, Alerts;

    fun matches(
        kind: MaxAiEpisodeKind,
        verdict: MaxAiVerdict,
        selectedVerdict: MaxAiVerdict? = null,
    ): Boolean {
        val matchesKind = when (this) {
            All -> true
            Decisions -> kind == MaxAiEpisodeKind.DECISION
            Probes -> kind == MaxAiEpisodeKind.PROBE
            Alerts -> kind == MaxAiEpisodeKind.SAFETY || kind == MaxAiEpisodeKind.DRIFT
        }
        return matchesKind && (selectedVerdict == null || verdict == selectedVerdict)
    }
}

// ── الحالة الآن ───────────────────────────────────────

/**
 * سطح التحكم — أول ما يصل إليه المستخدم.
 *
 * كان المفتاح الرئيسي آخر عنصر في الشاشة (قسم "التحكم" في نهايتها)، فكان
 * أهم إجراء في التطبيق أقلّ شيء مرئي. الآن يُفتح الخط الزمني بمفتاح واحد ثم
 * بحالة المحرك الحقيقية: خطة التحكم، بوابة الاستكشاف، تقدّم التعلّم، وعدّادات
 * القرارات، ثم إجراءان فوريان. لا حقل جديد هنا: كل سطر يقابل حقلًا منشورًا
 * واحدًا في [MaxAiState]، وما لم يُقس لا يُعرض كصفر.
 */
@Composable
private fun ControlDeck(
    state: MaxAiState,
    viewModel: MaxAiViewModel,
    navController: NavHostController,
) {
    val plan = state.automationPlan
    val planTone = when (plan.mode) {
        "Safety guard" -> MaxTone.Critical
        "User-locked" -> MaxTone.Caution
        "Adaptive", "Hold" -> MaxTone.Positive
        "Learning" -> MaxTone.Accent
        else -> MaxTone.Neutral
    }
    val exploration = state.exploration
    val blocked = exploration.blockReason

    MaxSection(
        title = stringResource(R.string.max_ai_deck_title),
        description = stringResource(R.string.max_ai_deck_desc),
    ) {
        MaxGroup {
            MaxSwitchRow(
                title = stringResource(R.string.max_ai_master_title),
                checked = state.aiEnabled,
                onCheckedChange = { viewModel.setAiEnabled(it) },
                subtitle = if (state.aiEnabled) {
                    stringResource(R.string.max_ai_master_on)
                } else {
                    stringResource(R.string.max_ai_master_off)
                },
                icon = Icons.Rounded.Psychology,
                iconTone = MaxTone.Accent,
            )
        }

        MaxGroup {
            MaxRow(
                title = plan.mode,
                subtitle = plan.reason,
                icon = Icons.Rounded.Tune,
                iconTone = planTone,
                trailing = {
                    MaxCapsule(
                        text = stringResource(
                            R.string.max_live_confidence,
                            plan.confidencePercent,
                        ),
                        tone = planTone,
                    )
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = if (blocked == null) {
                    stringResource(R.string.max_live_explore_allowed)
                } else {
                    stringResource(R.string.max_live_explore_blocked, blockText(blocked))
                },
                subtitle = stringResource(
                    R.string.max_live_explore_budget,
                    exploration.probesThisSession,
                    exploration.budget,
                ),
                icon = Icons.Rounded.Science,
                iconTone = if (blocked == null) MaxTone.Accent else MaxTone.Inactive,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_deck_learning),
                subtitle = stringResource(
                    R.string.max_ai_deck_learning_row,
                    state.learnedKnobs,
                    state.learningSamples,
                ),
                icon = Icons.Rounded.Insights,
                iconTone = if (state.learningSamples > 0L) MaxTone.Positive else MaxTone.Inactive,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_deck_decisions),
                subtitle = stringResource(
                    R.string.max_ai_counters,
                    state.totalDecisions,
                    state.successfulDecisions,
                    state.adjustedDecisions,
                    state.blockedForSafety,
                ),
                icon = Icons.Rounded.Timeline,
                iconTone = MaxTone.Neutral,
            )
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_ai_refresh),
                subtitle = stringResource(R.string.max_ai_refresh_desc),
                icon = Icons.Rounded.Refresh,
                iconTone = MaxTone.Accent,
                onClick = { viewModel.refresh() },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_live_open),
                subtitle = stringResource(
                    R.string.max_ai_deck_live_summary,
                    state.trust.size,
                    state.ownership.size,
                ),
                icon = Icons.Rounded.Insights,
                iconTone = MaxTone.Accent,
                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.MaxLive) },
            )
        }
    }
}

/**
 * الحالة المقيسة الآن + شريط تطورها.
 *
 * الثقة تُشتق من عمر اللقطة نفسها (دورة المحرك 30ث)، فلا يُعرض رقم
 * قديم بمطهر الحي. وقبل أول لقطة لا توجد قيمة إطلاقًا.
 */
@Composable
private fun NowHeader(state: MaxAiState) {
    val hasSample = state.lastSampleAtMs > 0L
    val ageMs = if (hasSample) System.currentTimeMillis() - state.lastSampleAtMs else 0L
    val trust = when {
        !hasSample -> MaxDataTrust.Unreadable
        ageMs <= 45_000L -> MaxDataTrust.Live
        else -> MaxDataTrust.Stale
    }
    val age = if (hasSample) relativeTime(ageMs) else null

    MaxSection(
        title = stringResource(R.string.max_ai_section_now),
        description = stringResource(R.string.max_ai_section_now_desc),
    ) {
        MaxMetricReadout(
            metric = MaxMetric(
                label = stringResource(R.string.max_ai_score_label),
                value = state.objectiveScore?.let { formatScore(it) },
                trust = trust,
                age = age,
                note = stringResource(
                    R.string.max_ai_score_desc,
                    formatScore(state.satisfactionTarget),
                ),
            ),
            size = MaxMetricSize.Large,
        )

        val trendValues = state.trend.map { it.objectiveScore }
        if (trendValues.size >= 2) {
            MaxSparkline(
                values = trendValues,
                baseline = state.satisfactionTarget,
                label = stringResource(R.string.max_ai_trend_label),
                tone = MaxTone.Accent,
            )
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_ai_metric_cpu),
                subtitle = formatPercent(state.cpuLoadPercent),
                icon = Icons.Rounded.Bolt,
                iconTone = MaxTone.Accent,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_metric_thermal),
                subtitle = formatThermal(state.thermalC),
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_metric_battery),
                subtitle = formatPercent(state.batteryPercent),
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_metric_memory),
                subtitle = formatPercent(state.memoryPercent),
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_context_title),
                subtitle = contextLine(state),
            )
        }
    }
}

@Composable
private fun contextLine(state: MaxAiState): String {
    val app = if (state.appContext == "system") {
        stringResource(R.string.max_ai_context_system)
    } else {
        state.appContext
    }
    val screen = if (state.screenOn) {
        stringResource(R.string.max_ai_screen_on)
    } else {
        stringResource(R.string.max_ai_screen_off)
    }
    val profile = when (state.currentProfile) {
        ProfileApplier.PROFILE_PERFORMANCE -> stringResource(R.string.max_ai_profile_performance)
        ProfileApplier.PROFILE_BALANCED -> stringResource(R.string.max_ai_profile_balanced)
        ProfileApplier.PROFILE_ECO -> stringResource(R.string.max_ai_profile_eco)
        else -> stringResource(R.string.max_ai_profile_unknown)
    }
    return stringResource(R.string.max_ai_context_line, app, screen, profile)
}

// ── الهدف ───────────────────────────────────────────

/**
 * أوزان الهدف النشطة ومن أين جاءت، وفوقها اختيار الأولوية مباشرة.
 *
 * كان اختيار الأولوية مدفونًا في قسم "التحكم" في آخر الشاشة، وكان المستخدم
 * يمرّ على الأوزان بلا أن يجد ما يغيّرها. الآن الرسم والزر في نطاق واحد: ما
 * يُقرأ هو ما يُعدّل. والقيم الثلاث هي مفاتيح يفهمها المحرك ("performance"،
 * "balanced"، "battery")، ولا يطبّق الزر ملفًا — يغيّر اتجاه القرار فقط.
 */
@Composable
private fun ObjectiveSection(state: MaxAiState, viewModel: MaxAiViewModel) {
    val preference = remember(state.aiEnabled, state.objectiveSource) {
        viewModel.objectivePreference()
    }
    val source = when (state.objectiveSource) {
        "user" -> stringResource(R.string.max_ai_objective_source_user)
        "screen_off" -> stringResource(R.string.max_ai_objective_source_screen_off)
        else -> stringResource(R.string.max_ai_objective_source_learned)
    }

    MaxSection(
        title = stringResource(R.string.max_ai_section_objective),
        description = source,
    ) {
        MaxSegmented(
            options = listOf(
                stringResource(R.string.max_ai_objective_pick_performance),
                stringResource(R.string.max_ai_objective_pick_balanced),
                stringResource(R.string.max_ai_objective_pick_battery),
            ),
            selectedIndex = when (preference) {
                "performance" -> 0
                "battery" -> 2
                else -> 1
            },
            onSelect = { index ->
                viewModel.setObjectivePreference(ObjectivePreferenceKeys[index])
            },
        )

        val weights = state.objectiveWeights
        if (weights == null) {
            // الوزن يُحسب في أول دورة مقيسة؛ قبلها لا أرقام ولا أشرطة صفرية.
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_ai_objective_pending),
                    icon = Icons.Rounded.Tune,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }

        MaxWeightBar(
            label = stringResource(R.string.max_ai_weight_performance),
            fraction = weights.performance,
            valueText = formatWeight(weights.performance),
            tone = MaxTone.Accent,
        )
        MaxWeightBar(
            label = stringResource(R.string.max_ai_weight_battery),
            fraction = weights.battery,
            valueText = formatWeight(weights.battery),
            tone = MaxTone.Positive,
        )
        MaxWeightBar(
            label = stringResource(R.string.max_ai_weight_thermal),
            fraction = weights.thermalHeadroom,
            valueText = formatWeight(weights.thermalHeadroom),
            tone = MaxTone.Caution,
        )
    }
}

// ── الخط الزمني ─────────────────────────────────────

@Composable
private fun EpisodeTimelineCard(
    episode: MaxAiEpisode,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val baseHeadline = if (episode.acted) {
        episode.knobLabel ?: episode.knobKey.orEmpty()
    } else {
        stringResource(R.string.max_ai_episode_no_action)
    }
    // التجربة المعرفية ليست قرارًا مُطبّقًا: تُسمّى باسمها ويُقال صراحةً أن
    // القيمة استُرجعت، وإلا قرأها المستخدم كتحسين باقٍ على جهازه.
    // الخط الزمني واحد، والفرز بالنوع: تدخل سلامة وانحراف مقبض ليسا
    // قراري تحسين، وتسميتهما كقرار كانت ستنسب للمحرك ما لم يختره.
    val headline = when (episode.kind) {
        MaxAiEpisodeKind.PROBE -> stringResource(R.string.max_ai_episode_probe, baseHeadline)
        MaxAiEpisodeKind.SAFETY -> stringResource(R.string.max_ai_episode_safety)
        MaxAiEpisodeKind.DRIFT -> stringResource(
            R.string.max_ai_episode_drift,
            episode.knobLabel ?: episode.knobKey.orEmpty(),
        )
        MaxAiEpisodeKind.DECISION -> baseHeadline
    }
    val verdictTone = when {
        episode.exploration -> MaxTone.Accent
        episode.kind == MaxAiEpisodeKind.SAFETY -> MaxTone.Critical
        episode.kind == MaxAiEpisodeKind.DRIFT -> MaxTone.Caution
        else -> verdictTone(episode.verdict)
    }
    val verdictText = when {
        episode.exploration -> stringResource(
            if (episode.reverted) {
                R.string.max_ai_verdict_probe_reverted
            } else {
                R.string.max_ai_verdict_probe_stuck
            }
        )
        episode.kind == MaxAiEpisodeKind.SAFETY -> stringResource(R.string.max_ai_verdict_safety)
        episode.kind == MaxAiEpisodeKind.DRIFT -> stringResource(R.string.max_ai_verdict_drift)
        else -> verdictLabel(episode.verdict)
    }

    MaxEpisodeCard(
        headline = headline,
        timeLabel = relativeTime(System.currentTimeMillis() - episode.id),
        verdictLabel = verdictText,
        verdictTone = verdictTone,
        summary = if (episode.exploration) probeSummary(episode) else episodeSummary(episode),
        expanded = expanded,
        onToggle = onToggle,
    ) {
        // 1) ما لوحِط: درجة الرضا المقيسة مقابل العتبة.
        MaxCausalStage(
            order = 1,
            title = stringResource(R.string.max_ai_stage_observed),
            body = stringResource(
                R.string.max_ai_observed_body,
                formatScore(episode.before.objectiveScore),
                formatScore(episode.satisfactionTarget),
                formatScore(episode.gap),
            ),
            tone = MaxTone.Accent,
            technical = stringResource(
                R.string.max_ai_observed_technical,
                formatPercent(episode.before.cpuLoadPercent),
                formatThermal(episode.before.thermalC),
                formatPercent(episode.before.batteryPercent),
                formatPercent(episode.before.memoryPercent),
            ),
        )

        // 2) لماذا كان مهمًا: الهدف النشط والسياق الذي قيس فيه.
        MaxCausalStage(
            order = 2,
            title = stringResource(R.string.max_ai_stage_why),
            body = stringResource(
                R.string.max_ai_why_body,
                objectiveText(episode.objectiveLabel),
                if (episode.appContext == "system") {
                    stringResource(R.string.max_ai_context_system)
                } else {
                    episode.appContext
                },
            ),
            tone = MaxTone.Neutral,
            technical = stringResource(
                R.string.max_ai_why_technical,
                formatWeight(episode.weightPerformance),
                formatWeight(episode.weightBattery),
                formatWeight(episode.weightThermal),
            ),
        )

        // 3) القرار: أصغر خطوة ممكنة على مقبض واحد.
        MaxCausalStage(
            order = 3,
            title = stringResource(R.string.max_ai_stage_decided),
            body = if (episode.acted) {
                stringResource(
                    R.string.max_ai_decided_body,
                    episode.knobLabel ?: episode.knobKey.orEmpty(),
                    directionText(episode.direction),
                    episode.fromValue ?: stringResource(R.string.max_ai_value_unknown),
                    episode.toValue ?: stringResource(R.string.max_ai_value_unknown),
                )
            } else {
                stringResource(R.string.max_ai_decided_none)
            },
            tone = MaxTone.Accent,
            technical = predictionText(episode),
        )

        // 4) ما تغير فعلًا على العتاد (القيمة المقروءة بعد الكتابة).
        MaxCausalStage(
            order = 4,
            title = stringResource(R.string.max_ai_stage_changed),
            body = episode.appliedValue?.let {
                stringResource(R.string.max_ai_changed_body, it)
            } ?: stringResource(R.string.max_ai_changed_none),
            tone = if (episode.appliedValue != null) MaxTone.Positive else MaxTone.Inactive,
            technical = episode.detail.takeIf { it.isNotBlank() },
        )

        // 5) ما حدر بعد التغيير: قياس ثانٍ بعد نافدة استجابة النطام.
        MaxCausalStage(
            order = 5,
            title = stringResource(R.string.max_ai_stage_after),
            body = if (episode.after == null) {
                stringResource(R.string.max_ai_after_unmeasured)
            } else {
                stringResource(
                    R.string.max_ai_after_body,
                    formatScore(episode.after.objectiveScore),
                )
            },
            tone = if (episode.after == null) MaxTone.Inactive else MaxTone.Accent,
        )

        if (episode.after != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = MaxSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
            ) {
                MaxDeltaRow(
                    label = stringResource(R.string.max_ai_score_label),
                    beforeText = formatScore(episode.before.objectiveScore),
                    afterText = formatScore(episode.after.objectiveScore),
                    deltaText = episode.objectiveDelta?.let { formatSignedScore(it) },
                    deltaTone = deltaTone(episode.objectiveDelta),
                )
                MaxDeltaRow(
                    label = stringResource(R.string.max_ai_metric_thermal),
                    beforeText = formatThermal(episode.before.thermalC),
                    afterText = formatThermal(episode.after.thermalC),
                    deltaText = episode.thermalDeltaC?.let { formatSignedThermal(it) },
                    deltaTone = thermalDeltaTone(episode.thermalDeltaC),
                )
                MaxDeltaRow(
                    label = stringResource(R.string.max_ai_metric_cpu),
                    beforeText = formatPercent(episode.before.cpuLoadPercent),
                    afterText = formatPercent(episode.after.cpuLoadPercent),
                    deltaText = episode.cpuDeltaPercent?.let { formatSignedPercent(it) },
                )
                MaxDeltaRow(
                    label = stringResource(R.string.max_ai_metric_battery),
                    beforeText = formatPercent(episode.before.batteryPercent),
                    afterText = formatPercent(episode.after.batteryPercent),
                    deltaText = episode.batteryDeltaPercent?.let { formatSignedPercent(it) },
                )
            }
        }

        // 6) الحكم: نجاح، تراجع مع استرجاع، حجب أمان، أو فشل كتابة.
        MaxCausalStage(
            order = 6,
            title = stringResource(R.string.max_ai_stage_verdict),
            body = verdictDetail(episode),
            tone = verdictTone,
            // الحكم المؤجل يُعرض فقط حين قيس فعلًا بعد ربع ساعة — لا وعد
            // بقياس قادم ولا خانة فارغة توحي برقم مفقود.
            technical = listOfNotNull(
                stringResource(R.string.max_ai_safety_level, episode.safetyLevel),
                deferredVerdictText(episode),
            ).joinToString(" · "),
        )

        // 7) الأثر المقيس مقابل المتوقع — صدق التنبؤ لا ادعاءه.
        MaxCausalStage(
            order = 7,
            title = stringResource(R.string.max_ai_stage_impact),
            body = episode.objectiveDelta?.let {
                stringResource(R.string.max_ai_impact_body, formatSignedScore(it))
            } ?: stringResource(R.string.max_ai_impact_none),
            tone = deltaTone(episode.objectiveDelta),
            technical = episode.predictionErrorGain?.let {
                stringResource(R.string.max_ai_prediction_error, formatScore(it))
            },
        )

        // 8) ما تعلّمه: فرق عدد العينات والثقة قبل/بعد الحلقة.
        MaxCausalStage(
            order = 8,
            title = stringResource(R.string.max_ai_stage_learned),
            body = if (episode.samplesAfter > episode.samplesBefore) {
                stringResource(
                    R.string.max_ai_learned_body,
                    episode.samplesBefore,
                    episode.samplesAfter,
                    formatWeight(episode.confidenceAfter),
                )
            } else {
                stringResource(R.string.max_ai_learned_none)
            },
            tone = MaxTone.Positive,
        )

        // 9) ماذا فعل المستخدم بعده — أصدق إشارة رضا متاحة: سلوك فعلي
        // لا استبيان. الغياب يُقال صراحةً كي لا يُقرأ الفراغ رضًا مؤكّدًا.
        MaxCausalStage(
            order = 9,
            title = stringResource(R.string.max_ai_stage_user),
            body = episode.userOverrideAtMs?.let { at ->
                stringResource(
                    R.string.max_ai_user_override,
                    relativeTime(at - episode.id),
                    overrideKindText(episode.userOverrideKind),
                )
            } ?: stringResource(R.string.max_ai_user_override_none),
            tone = if (episode.userRejected) MaxTone.Critical else MaxTone.Inactive,
            isLast = episode.candidates.isEmpty(),
        )

        if (episode.candidates.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.max_ai_candidates_title,
                    episode.candidates.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MaxGroup {
                episode.candidates.forEachIndexed { index, candidate ->
                    if (index > 0) MaxGroupDivider()
                    CandidateRow(candidate)
                }
            }
        }
    }
}

@Composable
private fun deferredVerdictText(episode: MaxAiEpisode): String? {
    if (!episode.hasDeferredVerdict) return null
    val unknown = stringResource(R.string.max_ai_value_unknown)
    return stringResource(
        R.string.max_ai_deferred_verdict,
        episode.deferredBatteryDeltaPercent?.let { formatSignedPercent(it) } ?: unknown,
        episode.deferredThermalDeltaC?.let { formatSignedThermal(it) } ?: unknown,
        episode.deferredObjectiveDelta?.let { formatSignedScore(it) } ?: unknown,
    )
}

/** ترجمة نوع التجاوز — قيم ثابتة من المحرك، لا نص حر يُعرض كما هو. */
@Composable
private fun overrideKindText(kind: String?): String = when (kind) {
    MaxAiOverride.LOCK -> stringResource(R.string.max_ai_override_lock)
    MaxAiOverride.PROFILE -> stringResource(R.string.max_ai_override_profile)
    else -> stringResource(R.string.max_ai_value_unknown)
}

@Composable
private fun CandidateRow(candidate: MaxAiCandidate) {
    val statusLabel = if (candidate.chosen) {
        stringResource(R.string.max_ai_candidate_chosen)
    } else {
        rejectionLabel(candidate.rejection)
    }
    MaxRow(
        title = candidate.label,
        subtitle = stringResource(
            R.string.max_ai_candidate_metrics,
            formatScore(candidate.utility),
            formatWeight(candidate.credibility),
            candidate.samples,
        ),
        trailing = {
            MaxCapsule(
                text = statusLabel,
                tone = if (candidate.chosen) MaxTone.Positive else MaxTone.Inactive,
            )
        },
    )
}

// ── المعرفة ─────────────────────────────────────────

/** حكم تراكمي لكل مقبض من عيناته المقيسة وحدها. */
@Composable
private fun InsightsSection(snapshot: MaxAiInsights.Snapshot) {
    MaxSection(
        title = stringResource(R.string.max_ai_section_insights),
        description = stringResource(R.string.max_ai_section_insights_desc),
    ) {
        if (snapshot.knobs.isEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_ai_insight_empty),
                    icon = Icons.Rounded.Insights,
                    iconTone = MaxTone.Inactive,
                )
            }
            return@MaxSection
        }

        MaxGroup {
            snapshot.knobs.forEachIndexed { index, knob ->
                if (index > 0) MaxGroupDivider()
                MaxRow(
                    title = knob.label,
                    subtitle = stringResource(
                        R.string.max_ai_insight_row,
                        formatSignedScore(knob.meanGain),
                        formatSignedThermal(knob.meanThermalC),
                        knob.samples,
                        directionText(knob.direction.name),
                    ),
                    trailing = {
                        MaxCapsule(
                            text = insightVerdictLabel(knob.verdict),
                            tone = insightVerdictTone(knob.verdict),
                        )
                    },
                )
            }
        }

        Text(
            text = stringResource(
                R.string.max_ai_insights_summary,
                snapshot.improved,
                snapshot.rolledBack,
                snapshot.blocked,
                snapshot.unmeasured,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.max_ai_insights_events_summary,
                snapshot.safetyEvents,
                snapshot.driftEvents,
                snapshot.userOverrides,
                snapshot.deferredMeasured,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        snapshot.meanAbsPredictionError?.let { error ->
            Text(
                text = stringResource(
                    R.string.max_ai_insights_prediction,
                    formatScore(error),
                    snapshot.predictionSamples,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── الملكية والأقفال ─────────────────────────────────

/** مفاتيح الأولوية التي يفهمها المحرك (Objective.fromPreference). */
private val ObjectivePreferenceKeys = listOf("performance", "balanced", "battery")

// ── الأمان ────────────────────────────────────

/**
 * حالة حاكم الأمان الحراري الحية: المستوى، ما قيس، عدد التدخلات، وهل نجح
 * فرض السقف فعلًا.
 *
 * كان الأمان يظهر كنطاق تحذير عند اشتباكه فقط، فيبدو غائبًا ما دام كل شيء
 * بخير. عرضه دائًما هو ما يجعل "لا شيء يحدث" حالة مرئية لا صمتًا، ويجعل آخر
 * سبب تدخل قابلًا للقراءة بعد أن تهدأ الحرارة.
 */
@Composable
private fun SafetySection(safety: SafetyStatus) {
    val levelTone = when (safety.level) {
        SafetyLevel.NORMAL -> MaxTone.Positive
        SafetyLevel.ENGAGED -> MaxTone.Caution
        SafetyLevel.CRITICAL -> MaxTone.Critical
    }
    val levelLabel = stringResource(
        when (safety.level) {
            SafetyLevel.NORMAL -> R.string.max_ai_safety_state_normal
            SafetyLevel.ENGAGED -> R.string.max_ai_safety_state_engaged
            SafetyLevel.CRITICAL -> R.string.max_ai_safety_state_critical
        },
    )

    MaxSection(
        title = stringResource(R.string.max_ai_safety_section),
        description = stringResource(R.string.max_ai_safety_section_desc),
    ) {
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_ai_safety_state),
                subtitle = stringResource(
                    R.string.max_ai_safety_detail,
                    formatThermal(safety.thermalC),
                    safety.interventions,
                ),
                icon = Icons.Rounded.Bolt,
                iconTone = levelTone,
                trailing = { MaxCapsule(text = levelLabel, tone = levelTone) },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_ai_safety_enforcement),
                subtitle = listOfNotNull(
                    enforcementLabel(safety.enforcement),
                    safety.enforcementDetail.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                icon = Icons.Rounded.Lock,
                iconTone = if (safety.enforcement == SafetyEnforcement.FAILED) {
                    MaxTone.Critical
                } else {
                    MaxTone.Neutral
                },
            )
        }
        if (safety.lastReason.isNotBlank()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_ai_safety_reason),
                    subtitle = safety.lastReason,
                    icon = Icons.Rounded.Timeline,
                    iconTone = MaxTone.Caution,
                )
            }
        }
    }
}

@Composable
private fun enforcementLabel(enforcement: SafetyEnforcement): String = stringResource(
    when (enforcement) {
        SafetyEnforcement.NOT_REQUIRED -> R.string.max_ai_enforce_not_required
        SafetyEnforcement.APPLIED -> R.string.max_ai_enforce_applied
        SafetyEnforcement.PARTIAL -> R.string.max_ai_enforce_partial
        SafetyEnforcement.FAILED -> R.string.max_ai_enforce_failed
        SafetyEnforcement.UNAVAILABLE -> R.string.max_ai_enforce_unavailable
    },
)

// ── الخط الزمني ──────────────────────────────────

/**
 * رأس الخط الزمني: نوع الحلقة والحكم المسجّل، مع عدّ صادق للمعروض.
 * التصفية تغيّر العرض فقط؛ لا تغيّر دفتر المحرك أو ملخص المعرفة.
 */
@Composable
private fun TimelineHeader(
    filter: TimelineFilter,
    verdict: MaxAiVerdict?,
    shown: Int,
    matched: Int,
    onFilterChange: (TimelineFilter) -> Unit,
    onVerdictChange: (MaxAiVerdict?) -> Unit,
) {
    var verdictMenuExpanded by remember { mutableStateOf(false) }
    MaxSection(
        title = stringResource(R.string.max_ai_section_timeline),
        description = stringResource(R.string.max_ai_section_timeline_desc),
    ) {
        MaxSegmented(
            options = listOf(
                stringResource(R.string.max_ai_timeline_filter_all),
                stringResource(R.string.max_ai_timeline_filter_decisions),
                stringResource(R.string.max_ai_timeline_filter_probes),
                stringResource(R.string.max_ai_timeline_filter_alerts),
            ),
            selectedIndex = filter.ordinal,
            onSelect = { index -> onFilterChange(TimelineFilter.values()[index]) },
        )
        Box {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_ai_timeline_verdict_filter),
                    subtitle = verdict?.let { verdictLabel(it) }
                        ?: stringResource(R.string.max_ai_timeline_filter_all),
                    icon = Icons.Rounded.FilterList,
                    onClick = { verdictMenuExpanded = true },
                )
            }
            DropdownMenu(
                expanded = verdictMenuExpanded,
                onDismissRequest = { verdictMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.max_ai_timeline_filter_all)) },
                    onClick = {
                        onVerdictChange(null)
                        verdictMenuExpanded = false
                    },
                )
                MaxAiVerdict.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(verdictLabel(option)) },
                        onClick = {
                            onVerdictChange(option)
                            verdictMenuExpanded = false
                        },
                    )
                }
            }
        }
        if (matched > 0) {
            Text(
                text = stringResource(R.string.max_ai_timeline_shown, shown, matched),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** تمديد المعاينة أو تقليصها — إجراء صريح بدل خط زمني لا ينتهي. */
@Composable
private fun TimelineToggleRow(expanded: Boolean, matched: Int, onToggle: () -> Unit) {
    MaxGroup {
        MaxRow(
            title = if (expanded) {
                stringResource(R.string.max_ai_timeline_show_less, MaxAiTimelinePreview)
            } else {
                stringResource(R.string.max_ai_timeline_show_all, matched)
            },
            icon = Icons.Rounded.Timeline,
            iconTone = MaxTone.Accent,
            onClick = onToggle,
        )
    }
}

// ── التحكم ─────────────────────────────────────────

/**
 * ملخص الملكية وأقفال المستخدم.
 *
 * التفاصيل الكاملة لكل مقبض (الفائز، النية، حالة التحقق) موجودة في مركز
 * القيادة الحي. وهنا ما يحتاجه المستخدم ليقرر: كم مقبضًا يملكه المحرك، وكم
 * منها تحقق على العتاد، وكم مقبضًا أغلقه هو بنفسه أمام كل مالك آلي. الأقفال
 * تُسرد بالاسم لأنها القرار اليدوي الوحيد الذي لا يجوز أن يختفي في شاشة أخرى.
 */
@Composable
private fun OwnershipSection(state: MaxAiState) {
    val owned = state.ownership
    val verified = owned.count { it.state == OwnershipCommitState.VERIFIED }

    MaxSection(
        title = stringResource(R.string.max_ai_section_system),
        description = stringResource(R.string.max_ai_section_system_desc),
    ) {
        MaxGroup {
            if (owned.isEmpty()) {
                MaxRow(
                    title = stringResource(R.string.max_ai_owner_none),
                    icon = Icons.Rounded.Tune,
                    iconTone = MaxTone.Inactive,
                )
            } else {
                MaxRow(
                    title = stringResource(R.string.max_ai_owner_knobs),
                    subtitle = stringResource(
                        R.string.max_ai_owner_summary,
                        owned.size,
                        verified,
                    ),
                    icon = Icons.Rounded.Tune,
                    iconTone = MaxTone.Accent,
                    trailing = {
                        MaxCapsule(
                            text = stringResource(R.string.max_ai_owner_verified),
                            tone = if (verified == owned.size) {
                                MaxTone.Positive
                            } else {
                                MaxTone.Caution
                            },
                        )
                    },
                )
            }
        }

        if (state.lockedKnobs.isNotEmpty()) {
            Text(
                text = stringResource(R.string.max_ai_locks_summary, state.lockedKnobs.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

// ── ملفات الأساس ─────────────────────────────────

/**
 * ملفات الأساس اليدوية: تُطبَّق فورًا عبر خدمة التوافق الخارجية، وليست مقابض
 * للذكاء. وسم "مُطبَّق" يأتي من [MaxAiState.currentProfile] المقروء من النظام،
 * فلا ادعاء تطبيق بلا قراءة.
 */
@Composable
private fun ProfilesSection(state: MaxAiState, viewModel: MaxAiViewModel) {
    val context = LocalContext.current
    val active = state.currentProfile

    MaxSection(
        title = stringResource(R.string.max_ai_section_profiles),
        description = stringResource(R.string.max_ai_profile_row_desc),
    ) {
        MaxGroup {
            BaseProfileRow(
                label = stringResource(R.string.max_ai_profile_performance),
                active = active == ProfileApplier.PROFILE_PERFORMANCE,
                onClick = {
                    viewModel.requestProfile(
                        ProfileApplier.PROFILE_PERFORMANCE,
                        context.getString(R.string.max_ai_profile_performance),
                    )
                },
            )
            MaxGroupDivider()
            BaseProfileRow(
                label = stringResource(R.string.max_ai_profile_balanced),
                active = active == ProfileApplier.PROFILE_BALANCED,
                onClick = {
                    viewModel.requestProfile(
                        ProfileApplier.PROFILE_BALANCED,
                        context.getString(R.string.max_ai_profile_balanced),
                    )
                },
            )
            MaxGroupDivider()
            BaseProfileRow(
                label = stringResource(R.string.max_ai_profile_eco),
                active = active == ProfileApplier.PROFILE_ECO,
                onClick = {
                    viewModel.requestProfile(
                        ProfileApplier.PROFILE_ECO,
                        context.getString(R.string.max_ai_profile_eco),
                    )
                },
            )
        }
    }
}

/** سطر ملف أساس واحد؛ الوسم حالة مقروءة من النظام لا أثر نقرة. */
@Composable
private fun BaseProfileRow(label: String, active: Boolean, onClick: () -> Unit) {
    MaxRow(
        title = label,
        subtitle = if (active) stringResource(R.string.max_ai_profile_active) else null,
        icon = Icons.Rounded.Tune,
        iconTone = if (active) MaxTone.Positive else MaxTone.Neutral,
        onClick = onClick,
    )
}

// ── تنسيق وترجمة الحالات ──────────────────────────────

@Composable
private fun episodeSummary(episode: MaxAiEpisode): String = when (episode.verdict) {
    MaxAiVerdict.IMPROVED -> stringResource(
        R.string.max_ai_summary_improved,
        episode.objectiveDelta?.let { formatSignedScore(it) }
            ?: stringResource(R.string.max_ai_value_unknown),
    )
    MaxAiVerdict.REGRESSED_ROLLED_BACK -> stringResource(
        R.string.max_ai_summary_rolled_back,
        episode.objectiveDelta?.let { formatSignedScore(it) }
            ?: stringResource(R.string.max_ai_value_unknown),
    )
    MaxAiVerdict.REGRESSED_STUCK -> stringResource(R.string.max_ai_summary_stuck)
    MaxAiVerdict.BLOCKED_SAFETY -> stringResource(R.string.max_ai_summary_blocked)
    MaxAiVerdict.WRITE_FAILED -> stringResource(R.string.max_ai_summary_write_failed)
    MaxAiVerdict.UNMEASURED -> stringResource(R.string.max_ai_summary_unmeasured)
    MaxAiVerdict.NO_ACTION -> stringResource(R.string.max_ai_summary_no_action)
}

@Composable
private fun probeSummary(episode: MaxAiEpisode): String = stringResource(
    if (episode.reverted) {
        R.string.max_ai_summary_probe_reverted
    } else {
        R.string.max_ai_summary_probe_stuck
    },
)

@Composable
private fun verdictDetail(episode: MaxAiEpisode): String =
    episode.detail.takeIf { it.isNotBlank() } ?: verdictLabel(episode.verdict)

@Composable
private fun verdictLabel(verdict: MaxAiVerdict): String = stringResource(
    when (verdict) {
        MaxAiVerdict.IMPROVED -> R.string.max_ai_verdict_improved
        MaxAiVerdict.REGRESSED_ROLLED_BACK -> R.string.max_ai_verdict_rolled_back
        MaxAiVerdict.REGRESSED_STUCK -> R.string.max_ai_verdict_stuck
        MaxAiVerdict.BLOCKED_SAFETY -> R.string.max_ai_verdict_blocked
        MaxAiVerdict.WRITE_FAILED -> R.string.max_ai_verdict_write_failed
        MaxAiVerdict.UNMEASURED -> R.string.max_ai_verdict_unmeasured
        MaxAiVerdict.NO_ACTION -> R.string.max_ai_verdict_no_action
    }
)

private fun verdictTone(verdict: MaxAiVerdict): MaxTone = when (verdict) {
    MaxAiVerdict.IMPROVED -> MaxTone.Positive
    MaxAiVerdict.REGRESSED_ROLLED_BACK -> MaxTone.Caution
    MaxAiVerdict.REGRESSED_STUCK -> MaxTone.Critical
    MaxAiVerdict.BLOCKED_SAFETY -> MaxTone.Caution
    MaxAiVerdict.WRITE_FAILED -> MaxTone.Critical
    MaxAiVerdict.UNMEASURED -> MaxTone.Neutral
    MaxAiVerdict.NO_ACTION -> MaxTone.Inactive
}

@Composable
private fun insightVerdictLabel(verdict: MaxAiInsights.Verdict): String = stringResource(
    when (verdict) {
        MaxAiInsights.Verdict.PROVEN_HELPFUL -> R.string.max_ai_insight_helpful
        MaxAiInsights.Verdict.PROVEN_COSTLY -> R.string.max_ai_insight_costly
        MaxAiInsights.Verdict.INCONCLUSIVE -> R.string.max_ai_insight_inconclusive
        MaxAiInsights.Verdict.LEARNING -> R.string.max_ai_insight_learning
    }
)

private fun insightVerdictTone(verdict: MaxAiInsights.Verdict): MaxTone = when (verdict) {
    MaxAiInsights.Verdict.PROVEN_HELPFUL -> MaxTone.Positive
    MaxAiInsights.Verdict.PROVEN_COSTLY -> MaxTone.Critical
    MaxAiInsights.Verdict.INCONCLUSIVE -> MaxTone.Caution
    MaxAiInsights.Verdict.LEARNING -> MaxTone.Neutral
}

@Composable
private fun rejectionLabel(rejection: String?): String = when (rejection) {
    MaxAiRejection.MEASURED_HARM -> stringResource(R.string.max_ai_rejected_measured_harm)
    MaxAiRejection.PREDICTED_HARM -> stringResource(R.string.max_ai_rejected_predicted_harm)
    MaxAiRejection.NO_STEP -> stringResource(R.string.max_ai_rejected_no_step)
    MaxAiRejection.UNREADABLE -> stringResource(R.string.max_ai_rejected_unreadable)
    else -> stringResource(R.string.max_ai_rejected_other)
}

@Composable
private fun directionText(direction: String?): String = when (direction) {
    ControlRegistry.Direction.RAISE_PERFORMANCE.name ->
        stringResource(R.string.max_ai_direction_raise)
    ControlRegistry.Direction.SAVE_ENERGY.name ->
        stringResource(R.string.max_ai_direction_save)
    else -> stringResource(R.string.max_ai_value_unknown)
}

/**
 * سبب امتناع بوابة الاستكشاف — ثوابت [TrustModel.Block] لا نص حر، كي لا
 * تُعرض رسالة إنجليزية من المحرك داخل واجهة عربية.
 */
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

@Composable
private fun objectiveText(label: String): String = when (label) {
    "screen_off" -> stringResource(R.string.max_ai_objective_source_screen_off)
    "performance" -> stringResource(R.string.max_ai_objective_pick_performance)
    "battery" -> stringResource(R.string.max_ai_objective_pick_battery)
    "balanced" -> stringResource(R.string.max_ai_objective_pick_balanced)
    else -> label
}

@Composable
private fun predictionText(episode: MaxAiEpisode): String? {
    val gain = episode.predictedGain ?: return null
    val unknown = stringResource(R.string.max_ai_value_unknown)
    return stringResource(
        R.string.max_ai_prediction,
        formatSignedScore(gain),
        episode.predictedThermalC?.let { formatSignedThermal(it) } ?: unknown,
        episode.predictionConfidence?.let { formatWeight(it) } ?: unknown,
    )
}

private fun deltaTone(delta: Float?): MaxTone = when {
    delta == null -> MaxTone.Neutral
    delta > 0f -> MaxTone.Positive
    delta < 0f -> MaxTone.Critical
    else -> MaxTone.Neutral
}

private fun thermalDeltaTone(delta: Float?): MaxTone = when {
    delta == null -> MaxTone.Neutral
    delta >= 1.0f -> MaxTone.Critical
    delta >= 0.3f -> MaxTone.Caution
    else -> MaxTone.Positive
}

private fun formatPercent(value: Int): String = "$value%"

private fun formatSignedPercent(value: Int): String =
    (if (value > 0) "+" else "") + "$value%"

private fun formatThermal(value: Float): String = "%.1f°".format(value)

private fun formatSignedThermal(value: Float): String =
    (if (value > 0f) "+" else "") + "%.2f°".format(value)

private fun formatScore(value: Float): String = "%.3f".format(value)

private fun formatSignedScore(value: Float): String =
    (if (value > 0f) "+" else "") + "%.3f".format(value)

private fun formatWeight(value: Float): String = "%.0f%%".format(value * 100f)

@Composable
private fun relativeTime(elapsedMs: Long): String {
    val safe = abs(elapsedMs)
    val minutes = safe / 60_000L
    return when {
        minutes < 1L -> stringResource(R.string.max_ai_time_now)
        minutes < 60L -> stringResource(R.string.max_ai_time_minutes, minutes)
        else -> stringResource(R.string.max_ai_time_hours, minutes / 60L)
    }
}
