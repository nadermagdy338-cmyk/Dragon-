package nd.max.ui.mainscreens

/** One item that is eligible for the single home activity card. */
data class UnifiedActivityItem(
    val key: String,
    val priority: Int,
    val atMs: Long?,
    val verified: Boolean,
    val title: String,
    val detail: String,
)

/**
 * Presentation-only policy for the home card.
 * It deliberately excludes measurements and unverified outcomes: those belong to
 * their owner screens and diagnostics, not to the activity story.
 */
object UnifiedActivityModel {
    fun select(items: List<UnifiedActivityItem>, nowMs: Long): List<UnifiedActivityItem> = items
        .asSequence()
        .filter { it.verified }
        .filter { it.title.isNotBlank() || it.detail.isNotBlank() }
        .distinctBy { it.key }
        .sortedWith(
            compareByDescending<UnifiedActivityItem> { it.priority }
                .thenByDescending { it.atMs ?: Long.MIN_VALUE },
        )
        .take(4)
        .toList()

    fun shouldShow(items: List<UnifiedActivityItem>, nowMs: Long): Boolean =
        select(items, nowMs).isNotEmpty()
}
