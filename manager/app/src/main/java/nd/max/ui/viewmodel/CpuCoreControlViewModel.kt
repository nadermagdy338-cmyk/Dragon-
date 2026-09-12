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
import nd.max.MaxManagerProps
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.PropertyUtils
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
    val verified: Boolean,
    /** False when the node refused the write itself; true when the write went
     *  through but an external manager (module profile / thermal daemon)
     *  overwrote the limits before or after the read-back. */
    val writeAccepted: Boolean = true,
    /** How many automatic re-assertions were spent defending this request. */
    val reassertions: Int = 0,
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
    val verification: CpuFrequencyVerification? = null,
    /** True when the user has hand-applied limits for this policy and an
     *  external manager keeps overwriting them faster than we can re-assert. */
    val externalConflict: Boolean = false,
    /** True while a manual session owns this policy's limits. */
    val sessionOwned: Boolean = false,
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

        /** Bounded defense: stop re-asserting after this many attempts and tell
         *  the user an external manager is winning instead of fighting forever. */
        const val MAX_REASSERTIONS_PER_POLICY = 3
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

    /** The limits the user hand-applied during this session, per policy path. */
    private var sessionApplied: Map<String, Pair<Long, Long>> = emptyMap()
    /** Re-assertions already spent defending each policy against external overwrites. */
    private var reassertionsSpent: Map<String, Int> = emptyMap()

    private fun setManualSessionProp(enabled: Boolean) {
        runCatching {
            PropertyUtils.set(MaxManagerProps.CoreControl.MANUAL_FREQ_SESSION, if (enabled) "1" else "0")
        }
    }

    /** The module's profile binary resets CPU limits on every AI decision and
     *  app switch; while a manual session owns them it must stand down. */
    private fun refreshManualSessionProp() {
        setManualSessionProp(sessionApplied.isNotEmpty())
    }

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
            val path = cluster.policyPath
            val policy = policies[path]
            val desired = sessionApplied[path]
            val liveMin = policy?.minKHz
            val liveMax = policy?.maxKHz
            val drifting = desired != null && (liveMin != desired.first || liveMax != desired.second)
            val exhausted = (reassertionsSpent[path] ?: 0) >= MAX_REASSERTIONS_PER_POLICY
            path to CpuFrequencyControlState(
                policyPath = path,
                currentKHz = CpuHardwareBackend.readCurrentFrequencyKHz(path),
                minKHz = liveMin,
                maxKHz = liveMax,
                hardwareMinKHz = policy?.provenMinKHz,
                hardwareMaxKHz = policy?.provenMaxKHz,
                governor = policy?.governor,
                availableFrequenciesKHz = policy?.availableFrequenciesKHz.orEmpty(),
                verification = lastFrequencyVerification[path],
                externalConflict = drifting && exhausted,
                sessionOwned = desired != null,
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

    /**
     * Defends the user's hand-applied limits against external rewrites. The
     * module's own profile binary stands down via the manual-session property,
     * but the vendor thermal daemon (HyperOS mi_thermald and friends) keeps
     * publishing its own ceilings on a timer. We re-assert a bounded number of
     * times, then stop and surface an honest conflict instead of fighting a
     * system daemon in a tight loop.
     */
    private fun reassertDriftedLimits() {
        if (sessionApplied.isEmpty()) return
        var changed = false
        sessionApplied.forEach { (path, desired) ->
            val live = CpuHardwareBackend.policies().firstOrNull { it.path == path } ?: return@forEach
            if (live.minKHz == desired.first && live.maxKHz == desired.second) {
                if (reassertionsSpent[path] != null) {
                    reassertionsSpent -= path
                    changed = true
                }
                return@forEach
            }
            val spent = reassertionsSpent[path] ?: 0
            if (spent >= MAX_REASSERTIONS_PER_POLICY) return@forEach
            reassertionsSpent = reassertionsSpent + (path to spent + 1)
            changed = true
            val result = CpuHardwareBackend.setPolicyLimits(path, desired.first, desired.second)
            val after = CpuHardwareBackend.policies().firstOrNull { it.path == path }
            lastFrequencyVerification = lastFrequencyVerification + (path to CpuFrequencyVerification(
                requestedMinKHz = desired.first,
                requestedMaxKHz = desired.second,
                actualMinKHz = after?.minKHz,
                actualMaxKHz = after?.maxKHz,
                verified = result.verified,
                writeAccepted = result.writeSucceeded,
                reassertions = spent + 1,
            ))
        }
        if (changed) refreshFrequencyControls()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                refreshRows()
                reassertDriftedLimits()
                refreshFrequencyControls()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        // The manual-session property deliberately survives leaving this
        // screen: the user's limits must keep standing against the module's
        // periodic profile resets. It is non-persistent and the companion
        // daemon clears any stale copy at startup, so a crash cannot wedge
        // the module forever.
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
                verified = result.verified,
                writeAccepted = result.writeSucceeded,
            ))
            // The user's intent is now the defended session state for this policy.
            sessionApplied = sessionApplied + (policyPath to (minKHz to maxKHz))
            reassertionsSpent -= policyPath
            refreshManualSessionProp()
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                frequencyActionMessage = when {
                    result.verified -> "تم التطبيق والتحقق من العتاد ✓"
                    !result.writeSucceeded -> "رفضت عقدة النظام الكتابة — القيم لم تتغير"
                    else -> "كِيان خارجي أعاد ضبط الحدود بعد الكتابة؛ سنعيد تثبيتها تلقائياً"
                }
            }
        }
    }

    /** Pins one policy to a single frequency (min = max). */
    fun applyPinnedFrequency(policyPath: String, freqKHz: Long) {
        applyFrequencyLimits(policyPath, freqKHz, freqKHz)
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
                    verified = result.verified,
                    writeAccepted = result.writeSucceeded,
                ))
                restored++
                allVerified = allVerified && result.verified
            }
            sessionApplied = emptyMap()
            reassertionsSpent = emptyMap()
            refreshManualSessionProp()
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                frequencyActionMessage = if (allVerified) {
                    "تمت استعادة حدود بداية الجلسة وتوثيقها"
                } else {
                    "تعذّرت استعادة بعض الحدود — راجع القيم الحية"
                }
            }
        }
    }

    fun resetFrequencyLimits(policyPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val policy = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            val min = policy?.provenMinKHz
            val max = policy?.provenMaxKHz
            if (min == null || max == null) {
                // Unknown hardware bounds: nothing can be written, and leaving
                // any old session entry defended here would be misleading.
                sessionApplied -= policyPath
                reassertionsSpent -= policyPath
                refreshManualSessionProp()
                refreshFrequencyControls()
                withContext(Dispatchers.Main) {
                    frequencyActionMessage = "مدى العتاد غير معلن من الدرايفر — لا يمكن الاستعادة"
                }
                return@launch
            }
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
                    verified = result?.verified == true,
                    writeAccepted = result?.writeSucceeded == true,
                ))
                // Choosing the full hardware range is still a manual decision:
                // keep defending it against the periodic profile reset.
                sessionApplied = sessionApplied + (policyPath to (min to max))
                reassertionsSpent -= policyPath
                refreshManualSessionProp()
            }
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                frequencyActionMessage = if (result?.verified == true) {
                    "تم استعادة مدى العتاد الكامل والتحقق منه"
                } else {
                    "تعذّرت استعادة مدى العتاد"
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
