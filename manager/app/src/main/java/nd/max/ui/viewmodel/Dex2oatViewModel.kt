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
        viewModelScope.launch(Dispatchers.IO) {
            progress = Dex2oatProgress.Single(app.label, resetting = false)
            Dex2oatUtil.compileApp(app.packageName, selectedMode)
            progress = null
        }
    }

    fun resetApp(app: DebloatAppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            progress = Dex2oatProgress.Single(app.label, resetting = true)
            Dex2oatUtil.resetApp(app.packageName)
            progress = null
        }
    }

    fun compileAll() {
        viewModelScope.launch(Dispatchers.IO) {
            progress = Dex2oatProgress.CompilingAll
            Dex2oatUtil.compileAll(selectedMode)
            progress = null
        }
    }

    fun compileSystemApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = allApps.filter { it.isSystem }
            targets.forEachIndexed { index, app ->
                progress = Dex2oatProgress.Batch(app.label, index + 1, targets.size)
                Dex2oatUtil.compileApp(app.packageName, selectedMode)
            }
            progress = null
        }
    }

    fun compileUserApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = allApps.filter { !it.isSystem }
            targets.forEachIndexed { index, app ->
                progress = Dex2oatProgress.Batch(app.label, index + 1, targets.size)
                Dex2oatUtil.compileApp(app.packageName, selectedMode)
            }
            progress = null
        }
    }

    fun resetAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            progress = Dex2oatProgress.ResettingAll
            Dex2oatUtil.resetAll()
            progress = null
        }
    }
}
