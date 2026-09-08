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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.PropertyUtils

data class GovernorPickerState(
    val cluster: CpuTopologyUtil.CpuCluster,
    val available: List<String>,
    val current: String?,
    val pinned: String?
)

/**
 * Advanced Configuration is a *manual, always-on pin* for CPU-cluster / GPU /
 * storage governors — distinct from the Governor Settings screen, which
 * defines what each of the default/performance/powersave *profiles* switch
 * to when a profile activates. Governor Settings answers "what should
 * 'Performance mode' set?"; this screen answers "what should this piece of
 * hardware always run at, regardless of which profile is active?". Same
 * underlying sysfs knobs, different job — so instead of a second parallel
 * profile system, this intentionally sits a layer below it and uses its own
 * distinct persist.sys.maxmanager.custom_* keys.
 *
 * CPU cluster grouping comes from CpuTopologyUtil, the same detector CPU Core
 * Control uses, so the two screens can never disagree about how many
 * clusters this device has or which cores are in which.
 */
class AdvancedConfigViewModel : ViewModel() {

    companion object {
        private const val PROP_CLUSTER_PREFIX = MaxManagerProps.Governor.CUSTOM_CLUSTER_PREFIX
        private const val PROP_GPU_GOVERNOR = MaxManagerProps.Governor.CUSTOM_GPU_GOVERNOR
        private const val PROP_UFS_SCHEDULER = MaxManagerProps.Storage.UFS_SCHEDULER

        // Same devfreq glob pattern MaliFreqViewModel probes for GPU frequency
        // control; this only reads/writes the *governor* attribute on it, a
        // capability Mali screen doesn't expose, so there's no functional overlap.
        private val GPU_DEVFREQ_CANDIDATES = listOf("/sys/class/devfreq/13000000.mali", "/sys/class/devfreq/*.mali")

        // Same storage candidate family TweakViewmodel's IO-scheduler profile
        // picker probes (mmcblk0/sda/nvme) — kept local here rather than reaching
        // into that ViewModel's private state, since this is a one-shot glob.
        private val STORAGE_DEVICE_CANDIDATES = listOf("sda", "mmcblk0", "nvme0n1", "sdd")
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set

    var clusterGovernors by mutableStateOf<List<GovernorPickerState>>(emptyList())
        private set

    var gpuGovernorDir by mutableStateOf<String?>(null)
        private set
    var availableGpuGovernors by mutableStateOf<List<String>>(emptyList())
        private set
    var currentGpuGovernor by mutableStateOf<String?>(null)
        private set
    var pinnedGpuGovernor by mutableStateOf<String?>(null)
        private set

    var storageDevice by mutableStateOf<String?>(null)
        private set
    var availableSchedulers by mutableStateOf<List<String>>(emptyList())
        private set
    var currentScheduler by mutableStateOf<String?>(null)
        private set
    var pinnedScheduler by mutableStateOf<String?>(null)
        private set

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            loadClusterGovernors()
            loadGpuGovernor()
            loadStorageScheduler()
            isAvailable = clusterGovernors.isNotEmpty() || gpuGovernorDir != null || storageDevice != null
        }
    }

    private fun loadClusterGovernors() {
        val clusters = CpuTopologyUtil.detectClusters()
        clusterGovernors = clusters.mapIndexed { index, cluster ->
            val available = readList("${cluster.policyPath}/scaling_available_governors")
            val current = readSingle("${cluster.policyPath}/scaling_governor")
            val propKey = PROP_CLUSTER_PREFIX + index
            val pinned = PropertyUtils.get(propKey).ifEmpty { null }
            if (pinned != null && pinned in available && pinned != current) {
                Shell.cmd("echo $pinned > ${cluster.policyPath}/scaling_governor 2>/dev/null").exec()
            }
            GovernorPickerState(
                cluster = cluster,
                available = available,
                current = pinned ?: current,
                pinned = pinned
            )
        }
    }

    fun setClusterGovernor(index: Int, governor: String) {
        val state = clusterGovernors.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("echo $governor > ${state.cluster.policyPath}/scaling_governor 2>/dev/null").exec()
            PropertyUtils.set(PROP_CLUSTER_PREFIX + index, governor)
            clusterGovernors = clusterGovernors.toMutableList().also {
                it[index] = state.copy(current = governor, pinned = governor)
            }
        }
    }

    private fun loadGpuGovernor() {
        val dir = GPU_DEVFREQ_CANDIDATES.firstOrNull { candidate ->
            if (candidate.contains("*")) {
                Shell.cmd("ls -d $candidate 2>/dev/null").exec().out.any { it.contains("mali") }
            } else {
                Shell.cmd("test -f $candidate/governor && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"
            }
        }?.let { candidate ->
            if (candidate.contains("*")) {
                Shell.cmd("ls -d $candidate 2>/dev/null").exec().out.firstOrNull { it.contains("mali") }?.trim()
            } else candidate
        }

        gpuGovernorDir = dir
        if (dir != null) {
            availableGpuGovernors = readList("$dir/available_governors")
            val current = readSingle("$dir/governor")
            val pinned = PropertyUtils.get(PROP_GPU_GOVERNOR).ifEmpty { null }
            if (pinned != null && pinned in availableGpuGovernors && pinned != current) {
                Shell.cmd("echo $pinned > $dir/governor 2>/dev/null").exec()
            }
            currentGpuGovernor = pinned ?: current
            pinnedGpuGovernor = pinned
        }
    }

    fun setGpuGovernor(governor: String) {
        val dir = gpuGovernorDir ?: return
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("echo $governor > $dir/governor 2>/dev/null").exec()
            PropertyUtils.set(PROP_GPU_GOVERNOR, governor)
            currentGpuGovernor = governor
            pinnedGpuGovernor = governor
        }
    }

    private fun loadStorageScheduler() {
        val device = STORAGE_DEVICE_CANDIDATES.firstOrNull { dev ->
            Shell.cmd("test -f /sys/block/$dev/queue/scheduler && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"
        }
        storageDevice = device
        if (device != null) {
            val path = "/sys/block/$device/queue/scheduler"
            availableSchedulers = readList(path)
            val current = readSingle(path)
            val pinned = PropertyUtils.get(PROP_UFS_SCHEDULER).ifEmpty { null }
            if (pinned != null && pinned in availableSchedulers && pinned != current) {
                Shell.cmd("echo $pinned > $path 2>/dev/null").exec()
            }
            currentScheduler = pinned ?: current
            pinnedScheduler = pinned
        }
    }

    fun setStorageScheduler(scheduler: String) {
        val device = storageDevice ?: return
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("echo $scheduler > /sys/block/$device/queue/scheduler 2>/dev/null").exec()
            PropertyUtils.set(PROP_UFS_SCHEDULER, scheduler)
            currentScheduler = scheduler
            pinnedScheduler = scheduler
        }
    }

    /** Parses a sysfs list line that may bracket the active entry, e.g. "none [mq-deadline] kyber bfq". */
    private fun readList(path: String): List<String> {
        val raw = Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString(" ")
        return raw.replace("[", "").replace("]", "").trim()
            .split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun readSingle(path: String): String? {
        val raw = Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString(" ")
        return Regex("\\[(.*?)]").find(raw)?.groupValues?.get(1)?.ifEmpty { null }
            ?: raw.trim().ifEmpty { null }
    }
}
