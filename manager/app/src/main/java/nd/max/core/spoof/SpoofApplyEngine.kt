/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Process-wide configuration evidence. No field in this state claims verified target-process identity. */
@Singleton
class SpoofApplyEngine @Inject constructor(
    private val repository: SpoofConfigurationRepository,
    private val perApp: SpoofCopgBackend,
    private val global: SpoofGlobalBackend,
    private val recoveryStore: SpoofRecoveryStore,
    private val transaction: SpoofConfigTransaction,
) {
    private val mutex = Mutex()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableConfig = MutableStateFlow<SpoofEngineConfig?>(null)
    val engineConfig = mutableConfig.asStateFlow()
    private val mutableGlobal = MutableStateFlow<SpoofEngineConfig?>(null)
    val globalConfig = mutableGlobal.asStateFlow()
    private val mutableWrite = MutableStateFlow<SpoofEngineWrite?>(null)
    val lastWrite = mutableWrite.asStateFlow()
    private val mutableGlobalWrite = MutableStateFlow<SpoofEngineWrite?>(null)
    val lastGlobalWrite = mutableGlobalWrite.asStateFlow()
    private val mutableRecovery = MutableStateFlow<List<SpoofRecoverySummary>>(emptyList())
    val recovery = mutableRecovery.asStateFlow()
    private val mutableRecoveryFailed = MutableStateFlow(false)
    val recoveryFailed = mutableRecoveryFailed.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { refreshLocked() } }

    suspend fun restore(engineId: String) = mutex.withLock {
        mutableBusy.value = true
        try {
            val result = withContext(Dispatchers.IO) { transaction.restore(engineId) }
            if (engineId == SpoofGlobalContract.MODULE_ID) mutableGlobalWrite.value = result else mutableWrite.value = result
            withContext(Dispatchers.IO) { refreshLocked() }
        } finally { mutableBusy.value = false }
    }

    private fun refreshLocked() {
        mutableConfig.value = perApp.snapshot()
        mutableGlobal.value = global.snapshot()
        try {
            mutableRecovery.value = recoveryStore.summaries()
            mutableRecoveryFailed.value = false
        } catch (_: Exception) {
            mutableRecovery.value = emptyList()
            mutableRecoveryFailed.value = true
        }
    }

    /** Revision is checked under the repository lock, so confirmation cannot apply an unseen revision. */
    suspend fun apply(isGlobal: Boolean, clear: Boolean, expectedRevision: Long) = mutex.withLock {
        mutableBusy.value = true
        try {
            val result = repository.withConfiguration(expectedRevision) { workspace ->
                withContext(Dispatchers.IO) {
                    val targets = (workspace.bindings.keys + workspace.appPolicies.keys)
                        .filter { workspace.appPolicy(it).mode != SpoofInheritanceMode.DISABLED }
                    when {
                        !clear && !isGlobal && targets.any { it !in repository.acknowledgments.value } ->
                            SpoofEngineWrite(false, SpoofEngineReason.ACKNOWLEDGMENT_REQUIRED)
                        isGlobal && clear -> global.undo()
                        isGlobal -> global.prepare(workspace)
                        clear -> perApp.clear()
                        else -> perApp.apply(workspace)
                    }
                }
            }
            val outcome = result ?: SpoofEngineWrite(false, SpoofEngineReason.CONFIG_CHANGED)
            if (isGlobal) mutableGlobalWrite.value = outcome else mutableWrite.value = outcome
            withContext(Dispatchers.IO) { refreshLocked() }
        } finally { mutableBusy.value = false }
    }
}
