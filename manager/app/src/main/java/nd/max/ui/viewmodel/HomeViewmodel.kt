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

import nd.max.MaxManagerProps


import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.core.maxai.MaxAiEngine
import nd.max.ui.util.EventLog
import nd.max.ui.util.RootUtils
import nd.max.ui.util.isBannerImageEnabled
import javax.inject.Inject


data class HomeUiState(
    val isBannerEnabled: Boolean = false,
    val moduleInstalled: Boolean = false,
    val autoMode: String? = null,
    val rootStatus: Boolean = false,
    val serviceStatusRes: Int = R.string.status_suspended,
    val servicePid: String = "",
    val currentProfileRes: Int = R.string.status_initializing,
    val runningGamePkg: String? = null,
    val runningGamePid: Int? = null,
    val runningGameStartTime: String? = null
)


@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val maxAiEngine: MaxAiEngine,
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enable_banner_image") {
            _uiState.value = _uiState.value.copy(isBannerEnabled = context.isBannerImageEnabled())
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        _uiState.value = _uiState.value.copy(isBannerEnabled = context.isBannerImageEnabled())
        
        observeRootUtils()
        fetchInitialSystemData()
    }

    private fun observeRootUtils() {
        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeServiceStatusRes().collect { (statusRes, pid) ->
                _uiState.value = _uiState.value.copy(serviceStatusRes = statusRes, servicePid = pid)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeProfileRes().collect { profileRes ->
                _uiState.value = _uiState.value.copy(currentProfileRes = profileRes)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeGameInfo().collect { info ->
                _uiState.value = _uiState.value.copy(
                    runningGamePkg = info.pkg,
                    runningGamePid = info.pid,
                    runningGameStartTime = info.startTime
                )
            }
        }
    }


    private fun fetchInitialSystemData() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = RootUtils.requestRootAccess()
            val isModuleInstalled = RootUtils.isModuleInstalled()
            val mode = Shell.cmd("getprop ${MaxManagerProps.Conf.AI_ENABLED}").exec().out.firstOrNull()?.trim()

            _uiState.value = _uiState.value.copy(
                rootStatus = isRooted,
                moduleInstalled = isModuleInstalled,
                autoMode = mode
            )
        }
    }

    /**
     * Locale-independent label for EventLog -- the R.string values behind
     * currentProfileRes are localized display text, not stable for logging.
     */
    private fun profileLabel(res: Int): String = when (res) {
        R.string.Profile_Performance, R.string.profile_perflite -> "performance"
        R.string.Profile_Balanced -> "balanced"
        R.string.Profile_ECO_mode -> "eco"
        else -> "unknown"
    }

    private fun profileReasonLabel(reason: String): String = when (reason) {
        "1" -> "performance"
        "2" -> "balanced"
        "3" -> "eco"
        else -> reason
    }

    /**
     * طلب ملف يدوي من الشاشة الرئيسية — عبر المحرك الموحد:
     * AI مطفأ → تطبيق فوري، AI مفعل → يُحفظ معلقًا (لا يضيع).
     * يعيد false عندما حُفظ معلقًا (الواجهة تعرض رسالة مختلفة).
     */
    fun applyProfile(profileReason: String, onSuccess: (appliedNow: Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            EventLog.userAction(
                screen = "GlobalTweaks",
                field = "profile",
                old = profileLabel(_uiState.value.currentProfileRes),
                new = profileReasonLabel(profileReason),
            )
            val appliedNow = maxAiEngine.requestManualProfile(
                profileReason,
                profileReasonLabel(profileReason)
            )
            viewModelScope.launch(Dispatchers.Main) { onSuccess(appliedNow) }
        }
    }

    fun rebootDevice(reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = when (reason) {
                "" -> "svc power reboot"
                "soft_reboot" -> "killall system_server"
                "recovery" -> "/system/bin/input keyevent 26 && svc power reboot $reason || reboot $reason"
                else -> "svc power reboot $reason || reboot $reason"
            }
            Shell.cmd(cmd).submit()
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
    


    fun refreshAiMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val mode = Shell.cmd("getprop ${MaxManagerProps.Conf.AI_ENABLED}").exec().out.firstOrNull()?.trim()
            _uiState.value = _uiState.value.copy(autoMode = mode)
        }
    }

}
