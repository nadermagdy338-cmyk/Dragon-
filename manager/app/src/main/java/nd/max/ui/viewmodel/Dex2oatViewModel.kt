/*
 * Backing state for the App Compiler (dex2oat) screen. Deliberately reuses
 * DebloatFreezeUtil.getInstalledApps() / DebloatAppInfo for app enumeration
 * instead of loading the installed-app list a second, separate way - this
 * screen only adds compile-mode selection and compile/reset actions on top
 * of the same app list Debloat & Freeze already knows how to build.
 *
 * Adapted from ZKM's Dex2oatViewModel.
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.util.DebloatAppInfo
import nd.max.ui.util.DebloatFreezeUtil
import nd.max.ui.util.Dex2oatUtil

/** Tab filters for the App Compiler app list. */
enum class Dex2oatTab { ALL, USER, SYSTEM }

/** Describes what compile/reset operation is in flight, for the screen to format and localize. */
sealed class Dex2oatProgress {
    data class Single(val label: String, val resetting: Boolean) : Dex2oatProgress()
    data class Batch(val label: String, val current: Int, val total: Int) : Dex2oatProgress()
    object CompilingAll : Dex2oatProgress()
    object ResettingAll : Dex2oatProgress()
}

/**
 * Outcome of the most recent compile/reset operation. The screen renders this as a real
 * Applied/Failed state instead of assuming success once the progress banner disappears.
 */
sealed class Dex2oatResult {
    /** One app compiled or reset. */
    data class Single(
        val label: String,
        val resetting: Boolean,
        val filter: String,
        val success: Boolean,
    ) : Dex2oatResult()

    /** A per-app loop over one scope, so partial failures stay visible. */
    data class Batch(
        val total: Int,
        val failed: Int,
        val filter: String,
    ) : Dex2oatResult()

    /** A single all-packages command; ART reports one aggregate exit status. */
    data class Bulk(
        val success: Boolean,
        val resetting: Boolean,
        val filter: String,
    ) : Dex2oatResult()
}

class Dex2oatViewModel : ViewModel() {

    var isLoading by mutableStateOf(true)
        private set
    var allApps by mutableStateOf<List<DebloatAppInfo>>(emptyList())
        private set
    var selectedTab by mutableStateOf(Dex2oatTab.ALL)
    var searchQuery by mutableStateOf(TextFieldValue(""))
        private set
    var selectedMode by mutableStateOf(Dex2oatUtil.COMPILE_MODES.first())
        private set

    /** Non-null while a compile/reset operation is running; shown as a progress banner. */
    var progress by mutableStateOf<Dex2oatProgress?>(null)
        private set

    val totalCount: Int get() = allApps.size
    val systemCount: Int get() = allApps.count { it.isSystem }
    val userCount: Int get() = allApps.size - systemCount

    /** Non-null once an operation finishes; the screen clears it after acknowledging. */
    var lastResult by mutableStateOf<Dex2oatResult?>(null)
        private set

    /** Dismisses the result notice so a stale outcome never sticks to a new action. */
    fun clearResult() {
        lastResult = null
    }

    val filteredApps by derivedStateOf {
        val tabFiltered = when (selectedTab) {
            Dex2oatTab.USER -> allApps.filter { !it.isSystem }
            Dex2oatTab.SYSTEM -> allApps.filter { it.isSystem }
            Dex2oatTab.ALL -> allApps
        }
        val query = searchQuery.text.trim()
        if (query.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    fun updateSearch(value: TextFieldValue) {
        searchQuery = value
    }

    fun onModeSelected(filter: String) {
        selectedMode = filter
    }

    fun loadApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val apps = DebloatFreezeUtil.getInstalledApps(context)
            withContext(Dispatchers.Main) {
                allApps = apps
                isLoading = false
            }
        }
    }

    fun compileApp(app: DebloatAppInfo) {
        val filter = selectedMode
        viewModelScope.launch(Dispatchers.IO) {
            lastResult = null
            progress = Dex2oatProgress.Single(app.label, resetting = false)
            val ok = Dex2oatUtil.compileApp(app.packageName, filter)
            progress = null
            lastResult = Dex2oatResult.Single(app.label, resetting = false, filter = filter, success = ok)
        }
    }

    fun resetApp(app: DebloatAppInfo) {
        val filter = selectedMode
        viewModelScope.launch(Dispatchers.IO) {
            lastResult = null
            progress = Dex2oatProgress.Single(app.label, resetting = true)
            val ok = Dex2oatUtil.resetApp(app.packageName)
            progress = null
            lastResult = Dex2oatResult.Single(app.label, resetting = true, filter = filter, success = ok)
        }
    }

    fun compileAll() {
        val filter = selectedMode
        viewModelScope.launch(Dispatchers.IO) {
            lastResult = null
            progress = Dex2oatProgress.CompilingAll
            val ok = Dex2oatUtil.compileAll(filter)
            progress = null
            lastResult = Dex2oatResult.Bulk(success = ok, resetting = false, filter = filter)
        }
    }

    fun resetAllApps() {
        val filter = selectedMode
        viewModelScope.launch(Dispatchers.IO) {
            lastResult = null
            progress = Dex2oatProgress.ResettingAll
            val ok = Dex2oatUtil.resetAll()
            progress = null
            lastResult = Dex2oatResult.Bulk(success = ok, resetting = true, filter = filter)
        }
    }

    fun compileSystemApps() = compileScope(allApps.filter { it.isSystem })

    fun compileUserApps() = compileScope(allApps.filter { !it.isSystem })

    /**
     * Compiles a scope app-by-app so the UI can show determinate progress and report how many
     * packages ART actually accepted, instead of a spinner that ends without an outcome.
     */
    private fun compileScope(targets: List<DebloatAppInfo>) {
        val filter = selectedMode
        viewModelScope.launch(Dispatchers.IO) {
            lastResult = null
            var failed = 0
            targets.forEachIndexed { index, app ->
                progress = Dex2oatProgress.Batch(app.label, index + 1, targets.size)
                if (!Dex2oatUtil.compileApp(app.packageName, filter)) failed++
            }
            progress = null
            lastResult = Dex2oatResult.Batch(total = targets.size, failed = failed, filter = filter)
        }
    }
}
