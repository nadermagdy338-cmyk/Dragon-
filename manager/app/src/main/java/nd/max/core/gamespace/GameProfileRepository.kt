/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import nd.max.MaxManagerPaths
import nd.max.core.hardware.RootFileAccess
import nd.max.core.platform.ForegroundAppResolver
import nd.max.ui.util.AppConfig

/** Process singleton shared even by non-Hilt legacy ViewModels. AppMonitor reads this same file. */
object GameProfileRepository {
    data class State(val profiles: Map<String, AppConfig> = emptyMap(), val loading: Boolean = true,
        val failed: Boolean = false, val revision: Long = 0)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val path get() = MaxManagerPaths.APPLIST_JSON
    private val persistence = GameProfilePersistence(object : GameProfilePersistence.Io {
        override fun read() = RootFileAccess.read(path)
        override fun write(text: String) = RootFileAccess.atomicWriteText(path, text)
    }, validate = { document -> document.values.forEach { json.decodeFromString<AppConfig>(it.toString()) } })

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { publish(readDocument()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, failed = true) }
        }
    }

    /** Resolve against disk under the common lock, not a caller's stale Map snapshot. */
    suspend fun update(pkg: String, transform: (AppConfig?) -> AppConfig?): Boolean = withContext(Dispatchers.IO) {
        if (!ForegroundAppResolver.isPackageName(pkg)) return@withContext false
        mutex.withLock {
            try {
                val result = persistence.update(pkg) { fields ->
                    val current = fields?.let { migrate(json.decodeFromString<AppConfig>(it.toString())) }
                    transform(current)?.let { json.parseToJsonElement(json.encodeToString(it)) as JsonObject }
                }
                if (!result.saved) { loadFailure(result.document); return@withLock false }
                publish(requireNotNull(result.document))
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, failed = true); false }
        }
    }

    private fun readDocument() = GameProfileDocument.parse(RootFileAccess.read(path) ?: error("profile-document-unreadable"))
    private fun publish(document: JsonObject) {
        val profiles = document.mapValues { (_, value) -> migrate(json.decodeFromString<AppConfig>(value.toString())) }
        mutable.value = State(profiles, loading = false, revision = mutable.value.revision + 1)
    }
    private fun loadFailure(document: JsonObject?) {
        if (document != null) publish(document)
        mutable.value = mutable.value.copy(loading = false, failed = true)
    }
    private fun migrate(config: AppConfig): AppConfig =
        if (config.gpu_profile == "default" && config.thermal_profile != "default") config.copy(
            gpu_profile = if (config.thermal_profile.lowercase() == "powersave") "power" else config.thermal_profile.lowercase(),
            thermal_profile = "default") else config
}
