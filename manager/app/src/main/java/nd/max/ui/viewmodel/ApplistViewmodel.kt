/*
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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Parcelable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nd.max.R
import nd.max.core.hardware.RootFileAccess
import nd.max.ui.util.AppConfig
import nd.max.ui.util.customizedFieldCount
import nd.max.ui.util.EventLog


class ApplistViewmodel : ViewModel() {

    enum class AppFilter { ALL, CUSTOMIZED, RECOMMENDED, SYSTEM }
    enum class AppSort { SMART, NAME, CUSTOMIZATION }

    companion object {
        private const val TAG = "ApplistViewmodel"
        private val appsLock = Any()
        var apps by mutableStateOf<List<AppInfo>>(emptyList())

        @JvmStatic
        fun getAppIconDrawable(context: Context, packageName: String): Drawable? {
            val appList = synchronized(appsLock) { apps }
            val appDetail = appList.find { it.packageName == packageName }
            return appDetail?.packageInfo?.applicationInfo?.loadIcon(context.packageManager)
        }
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo,
        val isRecommended: Boolean = false,
        var isEnabledInConfig: Boolean = false,
        var customizedCount: Int = 0
    ) : Parcelable {
        val packageName: String get() = packageInfo.packageName
        val isSystem: Boolean get() = (packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0)
        val uid: Int get() = packageInfo.applicationInfo?.uid ?: 0
    }

    var isRefreshing by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)
    var appFilter by mutableStateOf(AppFilter.ALL)
    var appSort by mutableStateOf(AppSort.SMART)
    
    var searchTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set
    
    private val searchQueryString: String get() = searchTextFieldValue.text
    
    val searchQuery: String get() = searchTextFieldValue.text
    
    fun updateSearch(newValue: TextFieldValue) {
        searchTextFieldValue = newValue
    }
    
    fun clearSearch() {
        searchTextFieldValue = TextFieldValue("")
    }

    private val configPath = nd.max.MaxManagerPaths.APPLIST_JSON
    private val jsonHandler = Json { ignoreUnknownKeys = true }

    val filteredApps by derivedStateOf {
        val query = searchQueryString.trim().lowercase(Locale.getDefault())
        val labelCollator = Collator.getInstance(Locale.getDefault())
        synchronized(appsLock) {
            val matching = apps.filter { app ->
                val matchesSearch = query.isEmpty() ||
                    app.label.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.ROOT).contains(query)
                val matchesFilter = when (appFilter) {
                    AppFilter.ALL -> true
                    AppFilter.CUSTOMIZED -> app.isEnabledInConfig
                    AppFilter.RECOMMENDED -> app.isRecommended
                    AppFilter.SYSTEM -> app.isSystem
                }
                matchesSearch && matchesFilter
            }
            when (appSort) {
                AppSort.SMART -> matching.sortedWith(
                    compareByDescending<AppInfo> { it.isEnabledInConfig }
                        .thenByDescending { it.isRecommended }
                        .thenBy(labelCollator) { it.label }
                )
                AppSort.NAME -> matching.sortedWith(compareBy(labelCollator) { it.label })
                AppSort.CUSTOMIZATION -> matching.sortedWith(
                    compareByDescending<AppInfo> { it.customizedCount }
                        .thenBy(labelCollator) { it.label }
                )
            }
        }
    }

    fun loadApps(context: Context, forceRefresh: Boolean = false) {
        if (!forceRefresh && apps.isNotEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRefreshing = true
                loadError = null
            }
            try {
                val pm = context.packageManager
                val configs = getAppConfigs()
                val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val loadedApps = installed.map { pkg ->
                    val appInfo = pkg.applicationInfo
                    @Suppress("DEPRECATION")
                    val isGame = appInfo != null && (
                        appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                            (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                        )

                    AppInfo(
                        label = appInfo?.loadLabel(pm)?.toString() ?: context.getString(R.string.status_unknown),
                        packageInfo = pkg,
                        isRecommended = isGame,
                        isEnabledInConfig = configs.containsKey(pkg.packageName),
                        customizedCount = configs[pkg.packageName]?.customizedFieldCount() ?: 0
                    )
                }

                withContext(Dispatchers.Main) {
                    synchronized(appsLock) {
                        apps = loadedApps
                    }
                }
            } catch (e: Exception) {
                EventLog.error("Applist", "load_apps", e)
                withContext(Dispatchers.Main) {
                    loadError = e.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.applist_error_desc)
                }
            } finally {
                withContext(Dispatchers.Main) { isRefreshing = false }
            }
        }
    }

    fun refreshAppConfigStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val configs = getAppConfigs()
            val refreshed = synchronized(appsLock) {
                apps.map {
                    it.copy(
                        isEnabledInConfig = configs.containsKey(it.packageName),
                        customizedCount = configs[it.packageName]?.customizedFieldCount() ?: 0
                    )
                }
            }
            withContext(Dispatchers.Main) {
                synchronized(appsLock) {
                    apps = refreshed
                }
            }
        }
    }

    private fun getAppConfigs(): Map<String, AppConfig> {
        return try {
            val content = RootFileAccess.read(configPath)
            if (!content.isNullOrBlank()) jsonHandler.decodeFromString<Map<String, AppConfig>>(content) else emptyMap()
        } catch (e: Exception) {
            EventLog.error("Applist", "read_config", e)
            emptyMap()
        }
    }
}
