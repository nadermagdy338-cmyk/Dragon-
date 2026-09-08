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

import nd.max.MaxManagerProps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nd.max.ui.util.EventLog
import nd.max.ui.util.PropertyUtils

data class SettingsUiState(
    val disableTweak: Boolean = false,
    val stateToast: Boolean = false,
    val debugMode: Boolean = false,
    val detailedLog: Boolean = false,
    val profileTimeout: Boolean = false,
    val profileNotifications: Boolean = false,
    val isLoaded: Boolean = false
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProps()
    }

    private fun loadProps() {
        viewModelScope.launch(Dispatchers.IO) {
            val disableTweak = PropertyUtils.get(MaxManagerProps.General.DISABLE_TWEAK) == "1"
            val stateToast = PropertyUtils.get(MaxManagerProps.Conf.SHOW_TOAST) == "1"
            val debugMode = PropertyUtils.get(MaxManagerProps.General.DEBUG_MODE) == "true"
            val detailedLog = PropertyUtils.get(MaxManagerProps.Conf.DETAILED_LOG) == "1"
            val profileTimeout = PropertyUtils.get(MaxManagerProps.General.DROP_FOREGROUND) == "1"
            val profileNotifications = PropertyUtils.get(MaxManagerProps.General.PROFILE_NOTIFICATIONS) == "1"
    
            _uiState.value = SettingsUiState(
                disableTweak = disableTweak,
                stateToast = stateToast,
                debugMode = debugMode,
                detailedLog = detailedLog,
                profileTimeout = profileTimeout,
                profileNotifications = profileNotifications,
                isLoaded = true
            )
        }
    }
    
    fun setProfileNotifications(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(profileNotifications = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            val flag = if (enabled) "-sn" else "-hn"
            PropertyUtils.set(MaxManagerProps.General.PROFILE_NOTIFICATIONS, if (enabled) "1" else "0")
            Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service $flag").submit()            
        }
    }
    
    fun setShowToast(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(stateToast = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.SHOW_TOAST, if (enabled) "1" else "0")
        }
    }

    fun setDebugMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(debugMode = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.General.DEBUG_MODE, if (enabled) "true" else "false")
        }
    }

    /**
     * Master switch for the detailed EVENT= log trail (EventLog.kt user
     * actions + AppMonitorLogger.kt's CLI forward) -- off by default, see
     * MaxManagerProps.Conf.DETAILED_LOG's doc comment for exactly what this
     * does and doesn't cover.
     */
    fun setDetailedLog(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(detailedLog = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.DETAILED_LOG, if (enabled) "1" else "0")
            // Best-effort: PropertyUtils.set()'s root fallback is a fire-and-
            // forget Shell.cmd().submit(), so there's a small window where
            // this read still sees the old value right after enabling. Not
            // worth blocking the toggle over -- every action after this one
            // is logged consistently either way.
            EventLog.userAction(screen = "Settings", field = "detailed_log", old = (!enabled).toString(), new = enabled.toString())
        }
    }

    fun setDisableTweak(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableTweak = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.General.DISABLE_TWEAK, if (enabled) "1" else "0")
        }
    }

    fun setProfileTimeout(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(profileTimeout = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.General.DROP_FOREGROUND, if (enabled) "1" else "0")
        }
    }
}
