/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.viewmodel


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.RootFileAccess
import nd.max.ui.util.AppConfig
import nd.max.ui.util.PerAppCpuRuntimeStatus
import nd.max.ui.util.readPerAppCpuRuntimeStatus
import nd.max.ui.util.EventLog
import nd.max.ui.util.PerAppKernelUtil


class AppSettingsViewModel : ViewModel() {
    private val configPath = nd.max.MaxManagerPaths.APPLIST_JSON
    private val jsonHandler = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val saveMutex = Mutex()

    var fullConfig by mutableStateOf<Map<String, AppConfig>>(emptyMap())
        private set

    var availableCpuGovernors by mutableStateOf<List<String>>(emptyList())
        private set
    var availableGpuGovernors by mutableStateOf<List<String>>(emptyList())
        private set
    var availableGpuFrequencies by mutableStateOf<List<Long>>(emptyList())
        private set
    var gpuNode by mutableStateOf<String?>(null)
        private set
    var cpuPolicies by mutableStateOf<List<CpuHardwareBackend.Policy>>(emptyList())
        private set
    var cpuRuntimeStatus by mutableStateOf(PerAppCpuRuntimeStatus())
        private set

    fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = RootFileAccess.read(configPath)
                if (!content.isNullOrEmpty()) {
                    val decoded = jsonHandler.decodeFromString<Map<String, AppConfig>>(content)
                    val migrated = decoded.mapValues { (_, cfg) ->
                        if (cfg.gpu_profile == "default" && cfg.thermal_profile != "default") {
                            cfg.copy(gpu_profile = when (cfg.thermal_profile.lowercase()) {
                                "powersave" -> "power"
                                else -> cfg.thermal_profile.lowercase()
                            }, thermal_profile = "default")
                        } else cfg
                    }
                    withContext(Dispatchers.Main) { fullConfig = migrated }
                }
            } catch (e: Exception) {
                EventLog.error("AppSettings", "load_config", e)
            }
        }
    }

    private fun saveAndRefresh(newMap: Map<String, AppConfig>) {
        // Update the in-memory model immediately so rapid consecutive toggles
        // compose from the latest state instead of racing against the previous
        // IO write. Disk writes are serialized below so the last user action
        // cannot be overwritten by an older coroutine finishing later.
        fullConfig = newMap
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try {
                    val jsonString = jsonHandler.encodeToString(newMap)
                    if (!RootFileAccess.atomicWriteText(configPath, jsonString)) {
                        error("atomicWriteText failed for $configPath")
                    }
                } catch (e: Exception) {
                    EventLog.error("AppSettings", "save_config", e)
                }
            }
        }
    }

    fun resetAppSettings(packageName: String) {
        val newMap = fullConfig.toMutableMap()
        newMap[packageName] = AppConfig()
        EventLog.userAction(screen = "AppSettings", field = "reset_all", old = "custom", new = "default", pkg = packageName)
        saveAndRefresh(newMap)
    }

    fun toggleMasterSwitch(packageName: String, isEnabled: Boolean) {
        val newMap = fullConfig.toMutableMap()
        if (isEnabled) {
            if (!newMap.containsKey(packageName)) {
                newMap[packageName] = AppConfig()
            }
        } else {
            newMap.remove(packageName)
        }
        EventLog.userAction(
            screen = "AppSettings",
            field = "master_switch",
            old = (!isEnabled).toString(),
            new = isEnabled.toString(),
            pkg = packageName,
        )
        saveAndRefresh(newMap)
    }

    fun loadKernelCapabilities(packageName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val cpu = PerAppKernelUtil.readCpuGovernors()
            val cpuPolicyData = CpuHardwareBackend.policies()
            val gpu = PerAppKernelUtil.readGpuCapabilities()
            val status = readPerAppCpuRuntimeStatus(packageName)
            withContext(Dispatchers.Main) {
                availableCpuGovernors = cpu
                cpuPolicies = cpuPolicyData
                availableGpuGovernors = gpu.governors
                availableGpuFrequencies = gpu.frequencies
                gpuNode = gpu.node
                cpuRuntimeStatus = status
            }
        }
    }

    fun refreshCpuRuntimeStatus(packageName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = readPerAppCpuRuntimeStatus(packageName)
            withContext(Dispatchers.Main) { cpuRuntimeStatus = status }
        }
    }

    fun updateSetting(packageName: String, key: String, value: String) {
        val currentAppConfig = fullConfig[packageName] ?: AppConfig()
        val oldValue = fieldValue(currentAppConfig, key)
        val updated = when (key) {
            "perf_lite_mode" -> currentAppConfig.copy(perf_lite_mode = value)
            "dnd_on_gaming" -> currentAppConfig.copy(dnd_on_gaming = value)
            "app_priority" -> currentAppConfig.copy(app_priority = value)
            "game_preload" -> currentAppConfig.copy(game_preload = value)
            "cpu_boost" -> currentAppConfig.copy(cpu_boost = value)
            "cpu_policy_controls" -> currentAppConfig.copy(cpu_policy_controls = value)
            "gpu_profile" -> currentAppConfig.copy(gpu_profile = value, thermal_profile = "default")
            "cpu_governor" -> currentAppConfig.copy(cpu_governor = value)
            "gpu_governor" -> currentAppConfig.copy(gpu_governor = value)
            "gpu_max_freq" -> currentAppConfig.copy(gpu_max_freq = value)
            "thermal_profile" -> currentAppConfig.copy(gpu_profile = if (value == "powersave") "power" else value, thermal_profile = "default")
            "refresh_rate" -> currentAppConfig.copy(refresh_rate = value)
            "renderer" -> currentAppConfig.copy(renderer = value)
            "resolution_downscale" -> currentAppConfig.copy(resolution_downscale = value)
            "bypass_charging" -> currentAppConfig.copy(bypass_charging = value)
            "touch_boost" -> currentAppConfig.copy(touch_boost = value)
            "haptic_feedback" -> currentAppConfig.copy(haptic_feedback = value)
            "kill_bg_apps" -> currentAppConfig.copy(kill_bg_apps = value)
            "force_hw_ui" -> currentAppConfig.copy(force_hw_ui = value)
            "disable_notifs" -> currentAppConfig.copy(disable_notifs = value)
            "wifi_no_sleep" -> currentAppConfig.copy(wifi_no_sleep = value)
            else -> currentAppConfig
        }
        if (updated != currentAppConfig && oldValue != value) {
            EventLog.userAction(screen = "AppSettings", field = key, old = oldValue, new = value, pkg = packageName)
        }
        val newMap = fullConfig.toMutableMap()
        newMap[packageName] = updated
        saveAndRefresh(newMap)
    }

    /**
     * Mirrors updateSetting()'s `when (key)` so EventLog can report the
     * pre-change value for the same key being written. Kept as a single
     * source of truth next to updateSetting() -- if a field is added there,
     * it should be added here too.
     */
    private fun fieldValue(config: AppConfig, key: String): String = when (key) {
        "perf_lite_mode" -> config.perf_lite_mode
        "dnd_on_gaming" -> config.dnd_on_gaming
        "app_priority" -> config.app_priority
        "game_preload" -> config.game_preload
        "cpu_boost" -> config.cpu_boost
        "cpu_policy_controls" -> config.cpu_policy_controls
        "gpu_profile" -> config.gpu_profile
        "cpu_governor" -> config.cpu_governor
        "gpu_governor" -> config.gpu_governor
        "gpu_max_freq" -> config.gpu_max_freq
        "thermal_profile" -> config.thermal_profile
        "refresh_rate" -> config.refresh_rate
        "renderer" -> config.renderer
        "resolution_downscale" -> config.resolution_downscale
        "bypass_charging" -> config.bypass_charging
        "touch_boost" -> config.touch_boost
        "haptic_feedback" -> config.haptic_feedback
        "kill_bg_apps" -> config.kill_bg_apps
        "force_hw_ui" -> config.force_hw_ui
        "disable_notifs" -> config.disable_notifs
        "wifi_no_sleep" -> config.wifi_no_sleep
        else -> "unknown"
    }
}
