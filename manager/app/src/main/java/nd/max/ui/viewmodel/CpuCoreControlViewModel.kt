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

    val totalCores: Int get() = coreRows.size
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

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                refreshRows()
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
