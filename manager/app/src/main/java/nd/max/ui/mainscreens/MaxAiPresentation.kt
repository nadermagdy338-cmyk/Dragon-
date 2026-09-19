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
