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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.core.gamespace.GameProfileRepository
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.ui.util.AppConfig
import nd.max.ui.util.applyGpuCeilingChoice
import nd.max.ui.util.PerAppCpuRuntimeStatus
import nd.max.ui.util.PerAppHardwareRuntimeStatus
import nd.max.ui.util.readPerAppCpuRuntimeStatus
import nd.max.ui.util.readPerAppHardwareRuntimeStatus
import nd.max.core.platform.EventLog
import nd.max.ui.util.PerAppKernelUtil


class AppSettingsViewModel : ViewModel() {
    var fullConfig by mutableStateOf<Map<String, AppConfig>>(emptyMap())
        private set
    var configFailed by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            GameProfileRepository.state.collect {
                fullConfig = it.profiles
                configFailed = it.failed
            }
        }
    }

    var availableCpuGovernors by mutableStateOf<List<String>>(emptyList())
        private set
    var availableGpuGovernors by mutableStateOf<List<String>>(emptyList())
        private set
    var availableGpuFrequencies by mutableStateOf<List<Long>>(emptyList())
        private set

    /** أعلى درجة يسمح بها الجهاز **الآن** — تُعرض للتفسير ولا تُقصر عليها قائمة الخيارات. */
    var gpuLiveCeilingHz by mutableStateOf<Long?>(null)
        private set
    var gpuNode by mutableStateOf<String?>(null)
        private set
    var cpuPolicies by mutableStateOf<List<CpuHardwareBackend.Policy>>(emptyList())
        private set
    var cpuRuntimeStatus by mutableStateOf(PerAppCpuRuntimeStatus())
        private set

    /**
     * نتيجة **كل** مقبض عتاد لهذا التطبيق (CPU · GPU · الحكام · الحرارة) مع رمز سببه.
     *
     * ولماذا لم يكفِ [cpuRuntimeStatus]: ذاك كان يغطّي سياسات CPU وحدها، ففشل GPU أو الحرارة
     * لا يصل إلى الشاشة أبدًا — وهذا بالضبط سبب أن «التحكّم لا يعمل» بلا سبب مكتوب.
     */
    var hardwareRuntimeStatus by mutableStateOf(PerAppHardwareRuntimeStatus())
        private set

    fun loadConfig() { viewModelScope.launch { GameProfileRepository.load() } }

    private fun change(packageName: String, transform: (AppConfig?) -> AppConfig?) {
        viewModelScope.launch { GameProfileRepository.update(packageName, transform) }
    }

    fun resetAppSettings(packageName: String) {
        EventLog.userAction(screen = "AppSettings", field = "reset_all", old = "custom", new = "default", pkg = packageName)
        change(packageName) { AppConfig() }
    }

    fun toggleMasterSwitch(packageName: String, isEnabled: Boolean) {
        EventLog.userAction(
            screen = "AppSettings",
            field = "master_switch",
            old = (!isEnabled).toString(),
            new = isEnabled.toString(),
            pkg = packageName,
        )
        change(packageName) { if (isEnabled) it ?: AppConfig() else null }
    }

    fun loadKernelCapabilities(packageName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val cpu = PerAppKernelUtil.readCpuGovernors()
            val cpuPolicyData = CpuHardwareBackend.policies()
            val gpu = PerAppKernelUtil.readGpuCapabilities()
            val status = readPerAppCpuRuntimeStatus(packageName)
            val hardwareStatus = readPerAppHardwareRuntimeStatus(packageName)
            withContext(Dispatchers.Main) {
                availableCpuGovernors = cpu
                cpuPolicies = cpuPolicyData
                availableGpuGovernors = gpu.governors
                availableGpuFrequencies = gpu.frequencies
                gpuLiveCeilingHz = gpu.liveCeilingHz
                gpuNode = gpu.node
                cpuRuntimeStatus = status
                hardwareRuntimeStatus = hardwareStatus
            }
        }
    }

    fun refreshCpuRuntimeStatus(packageName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = readPerAppCpuRuntimeStatus(packageName)
            val hardwareStatus = readPerAppHardwareRuntimeStatus(packageName)
            withContext(Dispatchers.Main) {
                cpuRuntimeStatus = status
                hardwareRuntimeStatus = hardwareStatus
            }
        }
    }

    fun updateSetting(packageName: String, key: String, value: String) {
        change(packageName) { current ->
            updateConfig(current ?: AppConfig(), packageName, key, value)
        }
    }

    private fun updateConfig(currentAppConfig: AppConfig, packageName: String, key: String, value: String): AppConfig {
        val oldValue = fieldValue(currentAppConfig, key)
        val updated = when (key) {
            "perf_lite_mode" -> currentAppConfig.copy(perf_lite_mode = value)
            "dnd_on_gaming" -> currentAppConfig.copy(dnd_on_gaming = value)
            "app_priority" -> currentAppConfig.copy(app_priority = value)
            "game_preload" -> currentAppConfig.copy(game_preload = value)
            "cpu_boost" -> currentAppConfig.copy(cpu_boost = value)
            "cpu_policy_controls" -> currentAppConfig.copy(cpu_policy_controls = value)
            "gpu_profile", "gpu_max_freq", "thermal_profile" -> applyGpuCeilingChoice(currentAppConfig, key, value)
            "cpu_governor" -> currentAppConfig.copy(cpu_governor = value)
            "gpu_governor" -> currentAppConfig.copy(gpu_governor = value)
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
        return updated
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
