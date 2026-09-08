/*
 * Backing state for the Thermal Devices screen: active/disabled zones,
 * cooling devices, a running max/avg temperature summary, and a 2s
 * auto-refresh loop while the screen is visible. Adapted from ZKM's
 * ThermalDevicesViewModel, reading through MaxManager's ThermalUtil instead of
 * ZKM's ThermalUtils.
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nd.max.core.hardware.DriftGuard
import kotlinx.coroutines.withContext
import nd.max.ui.util.CoolingDeviceInfo
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.ui.util.ThermalUtil
import nd.max.ui.util.ThermalTripPoint
import nd.max.ui.util.ThermalZoneInfo

data class ThermalSummary(
    val maxTempC: Int = 0,
    val avgTempC: Double = 0.0,
    val enabledZoneCount: Int = 0,
    val totalZoneCount: Int = 0
)

class ThermalDevicesViewModel : ViewModel() {

    companion object {
        const val FILTER_ALL = "All"
        val CATEGORY_FILTERS = listOf("All", "CPU", "GPU", "Battery", "Skin", "Charger", "System")
    }

    private val _allZones = MutableStateFlow<List<ThermalZoneInfo>>(emptyList())
    private val _coolingDevices = MutableStateFlow<List<CoolingDeviceInfo>>(emptyList())
    private val _selectedCategory = MutableStateFlow(FILTER_ALL)
    private val _isLoading = MutableStateFlow(true)
    private val _isLiveUpdating = MutableStateFlow(true)
    private val _thermalPolicy = MutableStateFlow("default")
    private val _thermalPolicySupported = MutableStateFlow(false)
    private val _expandedTripPoints = MutableStateFlow<Map<Int, List<ThermalTripPoint>>>(emptyMap())
    private val _capabilities = MutableStateFlow<HardwareCapabilitySnapshot?>(null)
    private val policyGuard = DriftGuard<String>()

    val isLoading: StateFlow<Boolean> = _isLoading
    val isLiveUpdating: StateFlow<Boolean> = _isLiveUpdating
    val thermalPolicy: StateFlow<String> = _thermalPolicy
    val thermalPolicySupported: StateFlow<Boolean> = _thermalPolicySupported
    val selectedCategory: StateFlow<String> = _selectedCategory
    val coolingDevices: StateFlow<List<CoolingDeviceInfo>> = _coolingDevices
    val expandedTripPoints: StateFlow<Map<Int, List<ThermalTripPoint>>> = _expandedTripPoints
    val capabilities: StateFlow<HardwareCapabilitySnapshot?> = _capabilities

    val enabledZones: StateFlow<List<ThermalZoneInfo>> = combine(_allZones, _selectedCategory) { zones, category ->
        val enabled = zones.filter { it.isEnabled }
        if (category == FILTER_ALL) enabled else enabled.filter { it.category == category }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val disabledZones: StateFlow<List<ThermalZoneInfo>> = _allZones.map { zones ->
        zones.filter { !it.isEnabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<ThermalSummary> = _allZones.map { zones ->
        if (zones.isEmpty()) {
            ThermalSummary()
        } else {
            val plausibleTemps = zones.filter { it.isEnabled && it.temperatureC > 0 }.map { it.temperatureC }
            ThermalSummary(
                maxTempC = plausibleTemps.maxOrNull() ?: 0,
                avgTempC = if (plausibleTemps.isNotEmpty()) plausibleTemps.average() else 0.0,
                enabledZoneCount = zones.count { it.isEnabled },
                totalZoneCount = zones.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThermalSummary())

    init {
        loadThermalData()
        runAutoRefreshLoop()
    }

    private fun runAutoRefreshLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (_isLiveUpdating.value) refreshReadings()
            }
        }
    }

    fun loadThermalData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val zones = ThermalUtil.readThermalZones()
            val cooling = ThermalUtil.readCoolingDevices()
            val capabilities = ThermalUtil.capabilitySnapshot()
            val policySupported = ThermalUtil.isThermalPolicySupported()
            val policy = if (policySupported) ThermalUtil.readThermalPolicy() else "default"
            withContext(Dispatchers.Main) {
                _allZones.value = zones
                _coolingDevices.value = cooling
                _capabilities.value = capabilities
                _thermalPolicySupported.value = policySupported
                _thermalPolicy.value = policy
                _isLoading.value = false
            }
        }
    }

    fun refreshReadings() {
        viewModelScope.launch(Dispatchers.IO) {
            val zones = ThermalUtil.readThermalZones()
            val cooling = ThermalUtil.readCoolingDevices()
            val capabilities = ThermalUtil.capabilitySnapshot()
            policyGuard.checkAndRepair()
            withContext(Dispatchers.Main) {
                _allZones.value = zones
                _coolingDevices.value = cooling
                _capabilities.value = capabilities
            }
        }
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun setLiveUpdating(enabled: Boolean) {
        _isLiveUpdating.value = enabled
    }

    fun toggleZoneEnabled(zone: ThermalZoneInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ThermalUtil.setZoneEnabled(zone.id, !zone.isEnabled)) refreshReadings()
        }
    }

    fun setCoolingState(device: CoolingDeviceInfo, state: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ThermalUtil.setCoolingState(device.id, state)) refreshReadings()
        }
    }

    fun setThermalPolicy(policy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val applied = ThermalUtil.writeThermalPolicy(policy)
            if (applied) {
                policyGuard.release("thermal-policy")
                if (policy != "default") {
                    policyGuard.own(
                        key = "thermal-policy",
                        desired = policy,
                        writer = ThermalUtil::writeThermalPolicy,
                        reader = ThermalUtil::readThermalPolicy,
                    )
                }
                withContext(Dispatchers.Main) { _thermalPolicy.value = ThermalUtil.readThermalPolicy() }
            }
        }
    }

    fun showTripPoints(zone: ThermalZoneInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val trips = ThermalUtil.readTripPoints(zone.sysfsPath)
            withContext(Dispatchers.Main) {
                _expandedTripPoints.value = _expandedTripPoints.value + (zone.id to trips)
            }
        }
    }

    fun hideTripPoints(zoneId: Int) {
        _expandedTripPoints.value = _expandedTripPoints.value - zoneId
    }
}
