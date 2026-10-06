/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import nd.max.core.maxai.MaxAiEpisode
import nd.max.core.maxai.MaxAiEpisodeKind
import nd.max.core.maxai.MaxAiVerdict

internal enum class MaxAiWorkspace { Overview, Journal, Learning, Controls }

internal enum class SampleFreshness { Missing, Live, Stale }

/** A backwards wall-clock jump cannot turn an old sample into a live one. */
internal fun sampleFreshness(sampleAtMs: Long, nowMs: Long): SampleFreshness = when {
    sampleAtMs <= 0L -> SampleFreshness.Missing
    nowMs < sampleAtMs -> SampleFreshness.Stale
    nowMs - sampleAtMs <= 45_000L -> SampleFreshness.Live
    else -> SampleFreshness.Stale
}

/**
 * الهدف **الفاعل** كما يقاس من الأوزان المنشورة، لا كما اختاره المستخدم.
 *
 * ولماذا هو مطلوب: قسم «ما يوازنه الآن» كان يعرض تفضيل المستخدم وحده، بينما المحرك يختار
 * هدفًا آخر حين تكون الشاشة مطفأة أو حين يتقدّم التعلّم على التفضيل (`MaxAiEngine.cycle`).
 * فالقارئ كان يرى اختياره ويظنّه ما يجري. والاشتقاق من `Objective.labelFor` **نفس** الدالة
 * التي يستعملها المحرك لحفظ التفضيل — فلا تعريف ثانٍ ينحرف عن الأول.
 */
internal enum class ObjectiveTone { Performance, Balanced, Battery }

/**
 * رموز الهدف كما يُخرجها المحرك ([nd.max.core.maxai.Objective.labelFor]) — عقد في مكان واحد.
 *
 * ووُجدت كثوابت لأن الواجهة تقرأ الرمز ولا تُنشئ هدفًا: تمريرها عبر `String` يجعل المُسند
 * **خالصًا وقابلًا للقياس بلا أندرويد**، وعقد التقاطع مع المصدر يُفرض في اختبار الوحدة.
 */
internal object ObjectiveLabels {
    const val PERFORMANCE = "performance"
    const val BALANCED = "balanced"
    const val BATTERY = "battery"
}

/**
 * الوسم المعروض للهدف الذي **يقيسه المحرك الآن** (من `Objective.labelFor(weights)`).
 *
 * ورمز لا نعرفه يُعرض «متوازن» لا صفرًا ولا فراغًا: أوزان لا تُطابق الثلاثة المعروفة لا
 * تعني هدفًا رابعًا بل عقدًا تغيّر، والوسط أصدق تسمية من لا شيء.
 */
internal fun activeObjective(label: String?): ObjectiveTone? = when (label) {
    null -> null
    ObjectiveLabels.PERFORMANCE -> ObjectiveTone.Performance
    ObjectiveLabels.BATTERY -> ObjectiveTone.Battery
    else -> ObjectiveTone.Balanced
}

/** Preserve the selected app when contexts are re-ranked by new evidence. */
internal fun selectedLearningContext(selected: String?, contexts: List<String>): String? =
    selected?.takeIf { it in contexts } ?: contexts.firstOrNull()

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

internal object TimelineSearch {
    fun matches(episode: MaxAiEpisode, query: String): Boolean = matches(episode, terms(query))

    fun terms(query: String): List<String> = query.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }

    fun matches(episode: MaxAiEpisode, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val fields = listOfNotNull(
            episode.knobKey, episode.knobLabel, episode.appContext, episode.detail,
            episode.objectiveLabel, episode.objectiveSource, episode.fromValue,
            episode.toValue, episode.appliedValue, episode.kind.name, episode.verdict.name,
        )
        return terms.all { term -> fields.any { it.contains(term, ignoreCase = true) } }
    }
}

internal fun filterTimeline(
    episodes: List<MaxAiEpisode>,
    filter: TimelineFilter,
    verdict: MaxAiVerdict?,
    query: String,
    appContext: String? = null,
): List<MaxAiEpisode> {
    val terms = TimelineSearch.terms(query)
    return episodes.filter { episode ->
        (appContext == null || episode.appContext == appContext) &&
            filter.matches(episode.kind, episode.verdict, verdict) &&
            TimelineSearch.matches(episode, terms)
    }
}
