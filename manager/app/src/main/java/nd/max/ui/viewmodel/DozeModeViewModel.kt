/*
 * Backing ViewModel for the Doze Mode screen. DozeModeScreen.kt was
 * committed importing this from nd.max.ui.viewmodel but the file
 * itself never existed in the repo — this fills that gap. See
 * DozeModeUtil.kt for the shell/root logic and the assumptions it makes
 * (idle-cycle stats, GMS standby-bucket mapping).
 *
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.MaxManagerProps
import nd.max.ui.util.DozeAppInfo
import nd.max.ui.util.DozeModeUtil
import nd.max.ui.util.DozeState
import nd.max.ui.util.GmsDozeMode
import nd.max.ui.util.PropertyUtils

/** Tab filter for the Doze whitelist app list. */
enum class DozeAppTab { ALL, WHITELISTED }

class DozeModeViewModel : ViewModel() {

    data class Settings(val isEnabled: Boolean = false, val isAggressive: Boolean = false)
    data class Stats(val idleCount: Int = 0, val lightIdleCount: Int = 0)

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    var settings by mutableStateOf(Settings())
        private set
    var dozeState by mutableStateOf(DozeState.UNKNOWN)
        private set
    var stats by mutableStateOf(Stats())
        private set
    var gmsMode by mutableStateOf(GmsDozeMode.DEFAULT)
        private set

    var searchQuery by mutableStateOf("")
        private set
    var selectedTab by mutableStateOf(DozeAppTab.ALL)
    var isAppsLoading by mutableStateOf(true)
        private set
    private var allApps by mutableStateOf<List<DozeAppInfo>>(emptyList())

    // Stats/state refresh needs a Context, but the state-machine actions below
    // (forceIdle/stepIdleState/unforceIdle/resetStats) are called by the screen
    // with no arguments. Cache the application context from loadState() for
    // those — applicationContext specifically, so this can't leak an Activity.
    private var appContext: Context? = null

    val filteredApps by derivedStateOf {
        val tabFiltered = when (selectedTab) {
            DozeAppTab.ALL -> allApps
            DozeAppTab.WHITELISTED -> allApps.filter { it.isWhitelisted }
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    fun loadState(context: Context) {
        appContext = context.applicationContext

        viewModelScope.launch(Dispatchers.IO) {
            val supported = DozeModeUtil.isSupported()
            isAvailable = supported
            if (!supported) return@launch

            settings = Settings(
                isEnabled = PropertyUtils.get(MaxManagerProps.Doze.ENABLED) == "1",
                isAggressive = PropertyUtils.get(MaxManagerProps.Doze.AGGRESSIVE) == "1"
            )
            gmsMode = DozeModeUtil.getGmsDozeMode()
            refreshDozeState()

            isAppsLoading = true
            val apps = DozeModeUtil.getInstalledApps(context)
            withContext(Dispatchers.Main) {
                allApps = apps
                isAppsLoading = false
            }
        }
    }

    private fun refreshDozeState() {
        val ctx = appContext ?: return
        val current = DozeModeUtil.getDozeState()
        dozeState = current
        val newStats = DozeModeUtil.refreshStats(ctx, current)
        stats = Stats(newStats.idleCount, newStats.lightIdleCount)
    }

    fun setDozeEnabled(enabled: Boolean) {
        settings = settings.copy(isEnabled = enabled)
        PropertyUtils.set(MaxManagerProps.Doze.ENABLED, if (enabled) "1" else "0")
    }

    fun setAggressiveDoze(enabled: Boolean) {
        settings = settings.copy(isAggressive = enabled)
        PropertyUtils.set(MaxManagerProps.Doze.AGGRESSIVE, if (enabled) "1" else "0")
    }

    fun setGmsDozeMode(mode: GmsDozeMode) {
        gmsMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            DozeModeUtil.setGmsDozeMode(mode)
            PropertyUtils.set(MaxManagerProps.Doze.GMS_MODE, mode.name)
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
    }

    fun forceIdle() = runActionThenRefresh { DozeModeUtil.forceIdle() }

    fun stepIdleState() = runActionThenRefresh { DozeModeUtil.stepIdleState() }

    fun unforceIdle() = runActionThenRefresh { DozeModeUtil.unforceIdle() }

    private fun runActionThenRefresh(action: () -> Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            action()
            // Give the state machine a moment to settle before re-reading it.
            delay(300)
            refreshDozeState()
        }
    }

    fun resetStats() {
        stats = Stats()
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DozeModeUtil.resetStats(ctx)
        }
    }

    fun setWhitelisted(context: Context, app: DozeAppInfo, whitelisted: Boolean) {
        allApps = allApps.map { if (it.packageName == app.packageName) it.copy(isWhitelisted = whitelisted) else it }
        viewModelScope.launch(Dispatchers.IO) {
            DozeModeUtil.setWhitelisted(app.packageName, whitelisted)
        }
    }
}
