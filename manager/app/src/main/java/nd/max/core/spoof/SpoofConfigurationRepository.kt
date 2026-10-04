/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SpoofConfigurationState(
    val workspace: SpoofWorkspace? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val revision: Long = 0,
)

/** Only writer of local configuration. Both screens observe this singleton, never a second database. */
@Singleton
class SpoofConfigurationRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = SpoofProfileStore(context)
    private val barrier = SpoofBarrierStore(context)
    private val mutableAcknowledgments = MutableStateFlow<Set<String>>(emptySet())
    val acknowledgments = mutableAcknowledgments.asStateFlow()
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(SpoofConfigurationState())
    val state = mutable.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutable.value.workspace != null) return@withLock
            try {
                mutableAcknowledgments.value = barrier.acknowledged()
                mutable.value = SpoofConfigurationState(store.load(), loading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutable.value = SpoofConfigurationState(loading = false, failed = true)
            }
        }
    }

    suspend fun acknowledge(pkg: String, accepted: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (accepted) barrier.acknowledge(pkg) else barrier.revoke(pkg)
            mutableAcknowledgments.value = barrier.acknowledged()
        }
    }

    /** Serialize engine reads/writes against configuration changes across all ViewModel clients. */
    suspend fun <T> withConfiguration(expectedRevision: Long, action: suspend (SpoofWorkspace) -> T): T? = mutex.withLock {
        if (mutable.value.revision != expectedRevision) return@withLock null
        mutable.value.workspace?.let { action(it) }
    }

    /** Transform the freshest state under the same lock as disk compare/save; no stale UI snapshot writes. */
    suspend fun update(transform: (SpoofWorkspace) -> SpoofWorkspace): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val before = mutable.value
            val current = before.workspace ?: return@withLock false
            try {
                val next = transform(current)
                if (!store.save(next, current)) {
                    // Reload a concurrent external change, never overwrite it with stale UI state.
                    mutable.value = SpoofConfigurationState(store.load(), loading = false, failed = true, revision = before.revision + 1)
                    return@withLock false
                }
                mutable.value = SpoofConfigurationState(next, loading = false, revision = before.revision + 1)
                true
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutable.value = before.copy(loading = false, failed = true)
                false
            }
        }
    }
}
