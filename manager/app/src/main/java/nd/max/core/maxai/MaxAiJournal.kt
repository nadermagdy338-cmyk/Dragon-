/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Bounded durable evidence. Clearing the journal does not clear learned effects or locks. */
@Singleton
class MaxAiJournal @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _episodes = MutableStateFlow<List<MaxAiEpisode>>(emptyList())
    val episodes: StateFlow<List<MaxAiEpisode>> = _episodes.asStateFlow()
    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    init {
        synchronized(lock) {
            runCatching {
                if (file.exists()) {
                    _episodes.value = MaxAiJournalCodec.decodeAll(file.readText())
                }
            }
        }
    }

    fun record(episode: MaxAiEpisode) {
        val capped = episode.withCappedCandidates()
        synchronized(lock) {
            _episodes.value = (listOf(capped) + _episodes.value).take(MAX_EPISODES)
        }
        schedulePersist()
    }

    /** Amend the original episode for deferred measurements and actual user overrides. */
    fun amend(id: Long, transform: (MaxAiEpisode) -> MaxAiEpisode): Boolean {
        val changed = synchronized(lock) {
            val current = _episodes.value
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) {
                false
            } else {
                val updated = current.toMutableList()
                updated[index] = transform(current[index]).withCappedCandidates()
                _episodes.value = updated
                true
            }
        }
        if (changed) schedulePersist()
        return changed
    }

    fun clear() {
        synchronized(lock) {
            persistJob?.cancel()
            persistJob = null
            _episodes.value = emptyList()
            runCatching { file.delete() }
        }
    }

    /** Keep the chosen candidate first, then the highest-utility alternatives. */
    private fun MaxAiEpisode.withCappedCandidates(): MaxAiEpisode =
        if (candidates.size <= MAX_CANDIDATES_PER_EPISODE) {
            this
        } else {
            copy(
                candidates = candidates
                    .sortedWith(
                        compareByDescending<MaxAiCandidate> { it.chosen }
                            .thenByDescending { it.utility }
                    )
                    .take(MAX_CANDIDATES_PER_EPISODE)
            )
        }

    private fun schedulePersist() {
        synchronized(lock) {
            persistJob?.cancel()
            persistJob = ioScope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                persist()
            }
        }
    }

    private fun persist() {
        val snapshot = synchronized(lock) { _episodes.value }
        val payload = runCatching { MaxAiJournalCodec.encodeAll(snapshot) }.getOrNull()
            ?: return
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(file)) {
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "maxai_journal.json"
        const val MAX_EPISODES = 80
        const val MAX_CANDIDATES_PER_EPISODE = 12
        const val PERSIST_DEBOUNCE_MS = 1_500L
    }
}
