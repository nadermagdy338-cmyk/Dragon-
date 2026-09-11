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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.getChipsetName

data class CpuCoreRow(
    val cpu: Int,
    val cluster: CpuTopologyUtil.CpuCluster,
    val online: Boolean,
    val isMaster: Boolean,
    val coreName: String?
)

data class CpuQuickConfig(
    val id: String,
    val labelRes: String,
    val descRes: String
)

/** The last request and read-back result for one cpufreq policy. */
data class CpuFrequencyVerification(
    val requestedMinKHz: Long,
    val requestedMaxKHz: Long,
    val actualMinKHz: Long?,
    val actualMaxKHz: Long?,
    val verified: Boolean
)

/** Live, per-cluster cpufreq snapshot used by the Core screen's controls. */
data class CpuFrequencyControlState(
    val policyPath: String,
    val currentKHz: Long?,
    val minKHz: Long?,
    val maxKHz: Long?,
    val hardwareMinKHz: Long?,
    val hardwareMaxKHz: Long?,
    val governor: String?,
    val availableFrequenciesKHz: List<Long>,
    val verification: CpuFrequencyVerification? = null
) {
    val canControl: Boolean get() = minKHz != null || maxKHz != null
}

/**
 * Live per-core hotplug control. Reads cluster grouping from CpuTopologyUtil
 * (shared with Advanced Configuration) instead of re-detecting topology here.
 *
 * Core online/offline state is intentionally session-scoped (never persisted
 * to a property): hotplugging is inherently transient hardware state, and
 * replaying a stale "core N was off" flag at next boot before the system has
 * even finished starting up could hang the device. Manual control always
 * starts fresh, matching real hotplug drivers, which is why the UI itself
 * flags this as "SESSION" rather than a saved setting.
 */
class CpuCoreControlViewModel : ViewModel() {

    companion object {
        val QUICK_CONFIGS = listOf(
            CpuQuickConfig("all_on", "cpu_core_quick_all_on", "cpu_core_quick_all_on_desc"),
            CpuQuickConfig("balanced", "cpu_core_quick_balanced", "cpu_core_quick_balanced_desc"),
            CpuQuickConfig("power_saver", "cpu_core_quick_power_saver", "cpu_core_quick_power_saver_desc")
        )
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    var chipsetName by mutableStateOf("")
        private set
    var clusters by mutableStateOf<List<CpuTopologyUtil.CpuCluster>>(emptyList())
        private set
    /** policyPath -> hardware max frequency (MHz). Read once in loadState() since it's a fixed ceiling, not live state. */
    var clusterMaxFreqMhz by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var coreRows by mutableStateOf<List<CpuCoreRow>>(emptyList())
        private set
    var manualControlEnabled by mutableStateOf(false)
        @JvmName("setManualControlEnabledState") private set

    var cpusetGroups by mutableStateOf<List<CpuTopologyUtil.CpusetGroup>>(emptyList())
        private set
    var frequencyControls by mutableStateOf<Map<String, CpuFrequencyControlState>>(emptyMap())
        private set
    var frequencyActionMessage by mutableStateOf<String?>(null)
        private set
    private var sessionFrequencyBaseline: Map<String, Pair<Long, Long>> = emptyMap()
    private var lastFrequencyVerification: Map<String, CpuFrequencyVerification> = emptyMap()

    val totalCores: Int get() = coreRows.size
    val hasSessionFrequencyChanges: Boolean
        get() = frequencyControls.any { (path, state) ->
            val baseline = sessionFrequencyBaseline[path]
            baseline != null && (state.minKHz != baseline.first || state.maxKHz != baseline.second)
        }
    val onlineCores: Int get() = coreRows.count { it.online }

    private var pollJob: kotlinx.coroutines.Job? = null

    fun loadState(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val detected = CpuTopologyUtil.detectClusters()
            if (detected.isEmpty()) {
                isAvailable = false
                return@launch
            }
            clusters = detected
            clusterMaxFreqMhz = detected.associate { it.policyPath to CpuTopologyUtil.clusterMaxFreqMhz(it.policyPath) }
            chipsetName = getChipsetName(context)
            isAvailable = true
            refreshRows()
            refreshFrequencyControls(captureBaseline = true)
            cpusetGroups = CpuTopologyUtil.readCpusetGroups()
            startPolling()
        }
    }

    /**
     * Applies a new core list to one cpuset group (e.g. pin "top-app" to the
     * performance cluster only). Session-scoped like core hotplug above —
     * cpuset assignments reset naturally on reboot along with the rest of the
     * scheduler's cgroup hierarchy, so nothing here is persisted.
     */
    fun setCpusetGroupCores(group: CpuTopologyUtil.CpusetGroup, cores: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            CpuTopologyUtil.writeCpusetGroup(group.path, cores)
            cpusetGroups = CpuTopologyUtil.readCpusetGroups()
        }
    }

    private fun refreshRows() {
        val rows = clusters.flatMap { cluster ->
            cluster.cores.map { cpu ->
                CpuCoreRow(
                    cpu = cpu,
                    cluster = cluster,
                    online = CpuTopologyUtil.isCoreOnline(cpu),
                    isMaster = cpu == 0,
                    coreName = CpuTopologyUtil.decodeCoreName(cpu)
                )
            }
        }
        coreRows = rows
    }

    private fun refreshFrequencyControls(captureBaseline: Boolean = false) {
        val policies = CpuHardwareBackend.policies().associateBy { it.path }
        val next = clusters.associate { cluster ->
            val policy = policies[cluster.policyPath]
            cluster.policyPath to CpuFrequencyControlState(
                policyPath = cluster.policyPath,
                currentKHz = CpuHardwareBackend.readCurrentFrequencyKHz(cluster.policyPath),
                minKHz = policy?.minKHz,
                maxKHz = policy?.maxKHz,
                hardwareMinKHz = policy?.provenMinKHz,
                hardwareMaxKHz = policy?.provenMaxKHz,
                governor = policy?.governor,
                availableFrequenciesKHz = policy?.availableFrequenciesKHz.orEmpty(),
                verification = lastFrequencyVerification[cluster.policyPath]
            )
        }
        frequencyControls = next
        if (captureBaseline && sessionFrequencyBaseline.isEmpty()) {
            sessionFrequencyBaseline = next.mapNotNull { (path, state) ->
                val min = state.minKHz
                val max = state.maxKHz
                if (min != null && max != null) path to (min to max) else null
            }.toMap()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                refreshRows()
                refreshFrequencyControls()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    fun setManualControlEnabled(enabled: Boolean) {
        manualControlEnabled = enabled
    }

    fun applyFrequencyLimits(policyPath: String, minKHz: Long, maxKHz: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CpuHardwareBackend.setPolicyLimits(policyPath, minKHz, maxKHz)
            val actual = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            lastFrequencyVerification = lastFrequencyVerification + (policyPath to CpuFrequencyVerification(
                requestedMinKHz = minKHz,
                requestedMaxKHz = maxKHz,
                actualMinKHz = actual?.minKHz,
                actualMaxKHz = actual?.maxKHz,
                verified = result.verified
            ))
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                frequencyActionMessage = if (result.verified) {
                    "Frequency limits applied and verified"
                } else {
                    "The kernel kept different frequency limits"
                }
            }
        }
    }

    fun restoreSessionFrequencyLimits() {
        if (sessionFrequencyBaseline.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var restored = 0
            var allVerified = true
            sessionFrequencyBaseline.forEach { (path, baseline) ->
                val result = CpuHardwareBackend.setPolicyLimits(path, baseline.first, baseline.second)
                val actual = CpuHardwareBackend.policies().firstOrNull { it.path == path }
                lastFrequencyVerification = lastFrequencyVerification + (path to CpuFrequencyVerification(
                    requestedMinKHz = baseline.first,
                    requestedMaxKHz = baseline.second,
                    actualMinKHz = actual?.minKHz,
                    actualMaxKHz = actual?.maxKHz,
                    verified = result.verified
                ))
                restored++
                allVerified = allVerified && result.verified
            }
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                frequencyActionMessage = if (allVerified) {
                    "Session baseline restored for $restored policies"
                } else {
                    "Some policies could not be restored"
                }
            }
        }
    }

    fun resetFrequencyLimits(policyPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val policy = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            val min = policy?.provenMinKHz
            val max = policy?.provenMaxKHz
            val result = if (min != null || max != null) {
                CpuHardwareBackend.setPolicyLimits(policyPath, min, max)
            } else null
            val actual = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            if (min != null && max != null) {
                lastFrequencyVerification = lastFrequencyVerification + (policyPath to CpuFrequencyVerification(
                    requestedMinKHz = min,
                    requestedMaxKHz = max,
                    actualMinKHz = actual?.minKHz,
                    actualMaxKHz = actual?.maxKHz,
                    verified = result?.verified == true
                ))
            }
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                frequencyActionMessage = if (result?.verified == true) {
                    "Hardware frequency range restored"
                } else {
                    "Could not restore the hardware range"
                }
            }
        }
    }

    fun consumeFrequencyActionMessage() {
        frequencyActionMessage = null
    }

    fun setCoreOnline(cpu: Int, online: Boolean) {
        manualControlEnabled = true
        viewModelScope.launch(Dispatchers.IO) {
            CpuTopologyUtil.setCoreOnline(cpu, online)
            withContext(Dispatchers.Main) { refreshRows() }
        }
    }

    fun applyQuickConfig(id: String) {
        manualControlEnabled = true
        val clustersSnapshot = clusters
        if (clustersSnapshot.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            when (id) {
                "all_on" -> {
                    clustersSnapshot.forEach { cluster ->
                        cluster.cores.forEach { CpuTopologyUtil.setCoreOnline(it, true) }
                    }
                }
                "balanced" -> {
                    // Efficiency + mid clusters on, top ("Prime") cluster off.
                    clustersSnapshot.forEachIndexed { index, cluster ->
                        val on = index != clustersSnapshot.lastIndex || clustersSnapshot.size == 1
                        cluster.cores.forEach { CpuTopologyUtil.setCoreOnline(it, on) }
                    }
                }
                "power_saver" -> {
                    // Only the efficiency (lowest) cluster stays online.
                    clustersSnapshot.forEachIndexed { index, cluster ->
                        val on = index == 0
                        cluster.cores.forEach { CpuTopologyUtil.setCoreOnline(it, on) }
                    }
                }
            }
            withContext(Dispatchers.Main) { refreshRows() }
        }
    }
}
