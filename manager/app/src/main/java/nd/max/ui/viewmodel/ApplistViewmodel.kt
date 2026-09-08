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
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nd.max.R
import nd.max.ui.util.AppConfig
import nd.max.ui.util.customizedFieldCount
import nd.max.ui.util.EventLog


class ApplistViewmodel : ViewModel() {

    enum class AppFilter { ALL, CUSTOMIZED, RECOMMENDED, SYSTEM }

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
    var showSystemApps by mutableStateOf(false)

    var appFilter by mutableStateOf(AppFilter.ALL)
    
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
        val query = searchQueryString.lowercase()
        synchronized(appsLock) {
            apps.filter { app ->
                val matchesSearch = app.label.lowercase().contains(query) || 
                                  app.packageName.lowercase().contains(query)
                val matchesSystem = showSystemApps || !app.isSystem || appFilter == AppFilter.SYSTEM
                val matchesFilter = when (appFilter) {
                    AppFilter.ALL -> true
                    AppFilter.CUSTOMIZED -> app.isEnabledInConfig
                    AppFilter.RECOMMENDED -> app.isRecommended
                    AppFilter.SYSTEM -> app.isSystem
                }
                matchesSearch && matchesSystem && matchesFilter
            }.sortedWith(
                compareByDescending<AppInfo> { it.isEnabledInConfig }
                    .thenByDescending { it.isRecommended }
                    .thenBy(Collator.getInstance(Locale.getDefault())) { it.label }
            )
        }
    }

    fun loadApps(context: Context, forceRefresh: Boolean = false) {
        if (!forceRefresh && apps.isNotEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            loadError = null
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
                    loadError = e.message?.takeIf { it.isNotBlank() } ?: "Unable to read installed applications."
                }
            } finally {
                withContext(Dispatchers.Main) { isRefreshing = false }
            }
        }
    }

    fun refreshAppConfigStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val configs = getAppConfigs()
            synchronized(appsLock) {
                apps = apps.map {
                    it.copy(
                        isEnabledInConfig = configs.containsKey(it.packageName),
                        customizedCount = configs[it.packageName]?.customizedFieldCount() ?: 0
                    )
                }
            }
        }
    }

    private fun getAppConfigs(): Map<String, AppConfig> {
        return try {
            val file = SuFile(configPath)
            if (file.exists()) {
                val content = SuFileInputStream.open(file).bufferedReader().use { it.readText() }
                if (content.isNotBlank()) jsonHandler.decodeFromString<Map<String, AppConfig>>(content) else emptyMap()
            } else emptyMap()
        } catch (e: Exception) {
            EventLog.error("Applist", "read_config", e)
            emptyMap()
        }
    }
}
