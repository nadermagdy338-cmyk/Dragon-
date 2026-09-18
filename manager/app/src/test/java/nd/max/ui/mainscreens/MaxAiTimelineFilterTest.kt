package nd.max.ui.mainscreens

import nd.max.core.maxai.MaxAiEpisodeKind
import nd.max.core.maxai.MaxAiVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class MaxAiTimelineFilterTest {
    private val kindsByFilter = mapOf(
        TimelineFilter.All to MaxAiEpisodeKind.values().toSet(),
        TimelineFilter.Decisions to setOf(MaxAiEpisodeKind.DECISION),
        TimelineFilter.Probes to setOf(MaxAiEpisodeKind.PROBE),
        TimelineFilter.Alerts to setOf(MaxAiEpisodeKind.SAFETY, MaxAiEpisodeKind.DRIFT),
    )

    @Test
    fun `no verdict selection preserves every kind filter`() {
        kindsByFilter.forEach { (filter, kinds) ->
            MaxAiEpisodeKind.values().forEach { kind ->
                MaxAiVerdict.values().forEach { verdict ->
                    assertEquals(
                        "$filter / $kind / $verdict",
                        kind in kinds,
                        filter.matches(kind, verdict),
                    )
                }
            }
        }
    }

    @Test
    fun `kind and verdict filters intersect for every recorded verdict`() {
        kindsByFilter.forEach { (filter, kinds) ->
            MaxAiEpisodeKind.values().forEach { kind ->
                MaxAiVerdict.values().forEach { verdict ->
                    MaxAiVerdict.values().forEach { selected ->
                        assertEquals(
                            "$filter / $kind / $verdict selected $selected",
                            kind in kinds && verdict == selected,
                            filter.matches(kind, verdict, selected),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `filtering preserves newest first order and does not change source`() {
        val recorded = listOf(
            4L to MaxAiVerdict.WRITE_FAILED,
            3L to MaxAiVerdict.IMPROVED,
            2L to MaxAiVerdict.NO_ACTION,
            1L to MaxAiVerdict.IMPROVED,
        )
        val original = recorded.toList()
        val matched = recorded.filter { (_, verdict) ->
            TimelineFilter.Decisions.matches(
                MaxAiEpisodeKind.DECISION, verdict, MaxAiVerdict.IMPROVED,
            )
        }

        assertEquals(listOf(3L, 1L), matched.map { it.first })
        assertEquals(original, recorded)
    }
}
