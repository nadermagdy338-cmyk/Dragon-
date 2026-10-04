/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

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

@Singleton
class GameSpaceRepository @Inject constructor(@ApplicationContext private val context: Context) {
    data class State(val apps: List<GameApp> = emptyList(), val manual: Set<String> = emptySet(),
        val favorites: Set<String> = emptySet(), val excluded: Set<String> = emptySet(),
        val loading: Boolean = true, val failed: Boolean = false)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                mutable.value = State(apps = GameLibraryAccess.apps(context), manual = GameLibraryAccess.manual(context),
                    favorites = packages("game_library_favorites"), excluded = packages("game_library_excluded"), loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, failed = true) }
        }
    }

    suspend fun membership(pkg: String, include: Boolean): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val next = GameLibraryMembership(GameLibraryAccess.manual(context), packages("game_library_excluded"))
                    .change(pkg, include)
                validGamePackages(next.manual)
                validGamePackages(next.excluded)
                // One preferences transaction: never persist only half of membership intent.
                val saved = prefs.edit().putStringSet("game_library_manual", next.manual)
                    .putStringSet("game_library_excluded", next.excluded).commit()
                mutable.value = mutable.value.copy(manual = if (saved) next.manual else mutable.value.manual,
                    excluded = if (saved) next.excluded else mutable.value.excluded, failed = !saved)
                saved
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(failed = true); false }
        }
    }
    private fun packages(key: String): Set<String> =
        validGamePackages(prefs.getStringSet(key, emptySet())?.toSet().orEmpty())

    suspend fun favorite(pkg: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                validGamePackages(setOf(pkg))
                val old = packages("game_library_favorites")
                val next = validGamePackages(if (enabled) old + pkg else old - pkg)
                val saved = prefs.edit().putStringSet("game_library_favorites", next).commit()
                mutable.value = mutable.value.copy(favorites = if (saved) next else mutable.value.favorites, failed = !saved)
                saved
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(failed = true); false }
        }
    }
}
