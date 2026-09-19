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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.MaxManagerProps
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.ManualControlLocks
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.getChipsetName
import javax.inject.Inject

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
    /**
     * True when the request fell outside the range the driver proves for this
     * policy. The write is clamped before it reaches the node, so a failure here
     * is **not** the node refusing a value — the node never saw the requested
     * one. Reported separately so the screen stops asking the user to guess
     * between "the node is protected" and "the node rejects this value".
     */
    val outsideProvenRange: Boolean = false,
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
/**
 * How the last CPU action ended, **as a code**.
 *
 * The banner used to be driven by a message — an Arabic sentence written here —
 * and the screen decided success by searching that sentence for failure words.
 * Two consequences, both real:
 *  - a **deferred** write ("a higher-priority owner holds this knob; your request
 *    is saved") matched none of the failure words and was reported as a green
 *    success, although nothing had been applied;
 *  - the wording was Arabic in every locale.
 *
 * The code carries the outcome; the screen owns the wording.
 */
enum class CpuActionReason {
    /** Written, re-read, and matching. */
    AppliedVerified,

    /** The full proven hardware range was restored and verified. */
    HardwareRangeRestored,

    /** Session limits were restored and verified. */
    Restored,

    /** Thermal safety owns the knob; the request is stored and applied on release. */
    DeferredSafety,

    /** A higher-priority owner holds the knob; the request is stored. `detail` names it. */
    DeferredOwner,

    /** Written but not confirmed by a read-back. */
    NotVerified,

    /** The cpufreq node behind this policy is not one we can name, so nothing was written. */
    UnknownNode,

    /** The driver does not declare this policy's hardware bounds. */
    HardwareRangeUnknown,

    /** No hand-applied intents were recorded this session. */
    NoManualIntents,

    /** Some limits could not be restored. */
    PartialRestore,

    /** The topology could not be read, so no preset could be applied. */
    UnknownClusterTopology,

    /** Core rows could not be read back after the preset. */
    CoresUnreadable,

    /** The preset changed nothing that a read-back can see. */
    PresetRefused,

    /** The preset changed the online count; `onlineCores`/`totalCores` say to what. */
    PresetApplied,
}

@androidx.compose.runtime.Immutable
data class CpuActionNotice(
    val reason: CpuActionReason,
    /** Machine detail only (an owner name), never a sentence. */
    val detail: String? = null,
    val onlineCores: Int = 0,
    val totalCores: Int = 0,
    val presetId: String? = null,
)

@HiltViewModel
class CpuCoreControlViewModel @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) : ViewModel() {

    companion object {
        val QUICK_CONFIGS = listOf(
            CpuQuickConfig("all_on", "cpu_core_quick_all_on", "cpu_core_quick_all_on_desc"),
            CpuQuickConfig("balanced", "cpu_core_quick_balanced", "cpu_core_quick_balanced_desc"),
            CpuQuickConfig("power_saver", "cpu_core_quick_power_saver", "cpu_core_quick_power_saver_desc")
        )

        /** Bounded defense: stop re-asserting after this many attempts and tell
         *  the user an external manager is winning instead of fighting forever. */
        const val MAX_REASSERTIONS_PER_POLICY = 3

        /** A hand-applied limit is journaled as a baseline-profile intent under
         *  this token and durably locked, so neither Max AI nor a profile preset
         *  can silently move the knob the user just set (INV-3). */
        private const val MANUAL_TOKEN_PREFIX = "manual:"
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
    var actionNotice by mutableStateOf<CpuActionNotice?>(null)
        private set

    /** The core preset last chosen, so the tiles can show a real selection. */
    var appliedQuickConfig by mutableStateOf<String?>(null)
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
     *  app switch; while a manual session owns them it must stand down.
     *
     *  The durable lock store is the source of truth, not this screen's session
     *  map: a lock outlives the screen, the process and the companion restart,
     *  and the flag must agree with it in all three cases. */
    private fun refreshManualSessionProp() {
        val lockedCpuKnobs = ManualControlLocks.lockedKeys().filter(HardwareControlKey::isCpuLimits)
        setManualSessionProp(lockedCpuKnobs.isNotEmpty())
    }

    /** Canonical arbiter identity for one policy path — the very same key Max AI,
     *  per-app policy and the safety engine contend on, so a hand-applied limit
     *  is a real owner in one ledger instead of a private write. */
    private fun controlKeyFor(policyPath: String): String? =
        CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            ?.let { HardwareControlKey.cpuLimits(it.name) }

    private fun manualToken(key: String): String = MANUAL_TOKEN_PREFIX + key

    /** Live limits of one policy in the arbiter's "min:max" value schema. */
    private fun liveLimits(policyPath: String): String? {
        val policy = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath } ?: return null
        val min = policy.minKHz ?: return null
        val max = policy.maxKHz ?: return null
        return "$min:$max"
    }

    private fun writeLimits(policyPath: String, value: String): Boolean {
        val parts = value.split(":", limit = 2)
        val min = parts.getOrNull(0)?.takeIf(String::isNotBlank)?.toLongOrNull()
        val max = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.toLongOrNull()
        return CpuHardwareBackend.setPolicyLimits(policyPath, min, max).successful
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
     * publishing its own ceilings on a timer.
     *
     * The repair itself is the arbiter's job, not a private write: reconcile()
     * re-applies only while our manual intent still wins the key and yields
     * silently when safety or a per-app policy legitimately outranks it. We
     * spend a bounded number of attempts, then stop and surface an honest
     * conflict instead of fighting a system daemon in a tight loop.
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
            val key = controlKeyFor(path) ?: return@forEach
            // A null result means the winning intent lives in another process, so
            // there is nothing for us to re-apply: not a failed repair, and it
            // must not consume one of the bounded attempts.
            val result = arbiter.reconcile(key) ?: return@forEach
            changed = true
            if (result.verified) {
                if (spent > 0) reassertionsSpent -= path
            } else {
                reassertionsSpent = reassertionsSpent + (path to spent + 1)
            }
            val after = CpuHardwareBackend.policies().firstOrNull { it.path == path }
            lastFrequencyVerification = lastFrequencyVerification + (path to CpuFrequencyVerification(
                requestedMinKHz = desired.first,
                requestedMaxKHz = desired.second,
                actualMinKHz = after?.minKHz,
                actualMaxKHz = after?.maxKHz,
                verified = result.verified,
                writeAccepted = result.applied,
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
        submitManualLimits(
            policyPath = policyPath,
            minKHz = minKHz,
            maxKHz = maxKHz,
            successReason = CpuActionReason.AppliedVerified,
        )
    }

    /** Pins one policy to a single frequency (min = max). */
    fun applyPinnedFrequency(policyPath: String, freqKHz: Long) {
        applyFrequencyLimits(policyPath, freqKHz, freqKHz)
    }

    /**
     * One hand-applied limit: written through the single ownership gate, then
     * durably locked.
     *
     * The owner is GLOBAL_PROFILE because a manual choice is a baseline, not an
     * AI command (decisions #9/#23). The durable lock is what makes it the
     * user's: it keeps MAX_AI and preset writes off this knob from now on, while
     * SAFETY/RECOVERY still override it — safety supremacy (INV-2) outranks
     * every user preference (INV-3) by design.
     */
    private fun submitManualLimits(
        policyPath: String,
        minKHz: Long,
        maxKHz: Long,
        successReason: CpuActionReason,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = controlKeyFor(policyPath)
            if (key == null) {
                withContext(Dispatchers.Main) {
                    actionNotice = CpuActionNotice(CpuActionReason.UnknownNode)
                }
                return@launch
            }
            val token = manualToken(key)
            val desired = "$minKHz:$maxKHz"
            val baseline = liveLimits(policyPath)
            val result = arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = token,
                desired = desired,
                apply = { writeLimits(policyPath, it) },
                read = { liveLimits(policyPath) },
                baseline = baseline,
                restore = { writeLimits(policyPath, it) },
            )
            // Lock only an intent the ledger actually accepted: a verified write,
            // or one journaled behind a higher-priority owner that will apply on
            // release. A failed write is forgotten by the arbiter, so locking it
            // would advertise a preference that was never set.
            val accepted = result.verified || result.blocked
            if (accepted) ManualControlLocks.lock(key, token, desired, baseline)

            val actual = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            lastFrequencyVerification = lastFrequencyVerification + (policyPath to CpuFrequencyVerification(
                requestedMinKHz = minKHz,
                requestedMaxKHz = maxKHz,
                actualMinKHz = actual?.minKHz,
                actualMaxKHz = actual?.maxKHz,
                verified = result.verified,
                writeAccepted = result.applied,
                outsideProvenRange = actual != null &&
                    CpuHardwareBackend.isOutsideProvenRange(actual, minKHz, maxKHz),
            ))
            if (accepted) {
                // The user's intent is now the defended session state for this policy.
                sessionApplied = sessionApplied + (policyPath to (minKHz to maxKHz))
                reassertionsSpent -= policyPath
                refreshManualSessionProp()
            }
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                // Outcome first, wording second. The old banner decided success by
                // searching the message for failure words, so a **deferred** write
                // (“a higher-priority owner holds this knob; your request is saved”)
                // contained none of them and was shown as a green success — while
                // nothing had in fact been applied. Now the code carries the truth
                // and the text only explains it.
                actionNotice = when {
                    result.verified -> CpuActionNotice(successReason)
                    result.blocked && result.winner == ControlOwnership.Owner.SAFETY ->
                        CpuActionNotice(CpuActionReason.DeferredSafety)
                    result.blocked ->
                        CpuActionNotice(CpuActionReason.DeferredOwner, detail = result.winner?.name)
                    else -> CpuActionNotice(CpuActionReason.NotVerified)
                }
            }
        }
    }

    /**
     * Gives every hand-applied knob back: the durable lock is dropped first,
     * then the arbiter restores the baseline it captured before the manual write
     * and verifies the readback (decision #6 — transactional restore of the
     * recorded baseline, not a vague "last stable state").
     */
    fun restoreSessionFrequencyLimits() {
        if (sessionFrequencyBaseline.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var attempted = 0
            var allVerified = true
            sessionFrequencyBaseline.forEach { (path, baseline) ->
                val key = controlKeyFor(path) ?: return@forEach
                // Drop our own lock before releasing, otherwise the restore write
                // would be refused by the very preference we are retiring.
                ManualControlLocks.unlock(key)
                // A null result means we no longer hold a local intent for this
                // key (another process won it), so there is honestly nothing we
                // restored here — it must not be reported as a failed restore.
                val result = arbiter.release(key, manualToken(key), restore = true) ?: return@forEach
                attempted++
                allVerified = allVerified && result.verified
                val actual = CpuHardwareBackend.policies().firstOrNull { it.path == path }
                lastFrequencyVerification = lastFrequencyVerification + (path to CpuFrequencyVerification(
                    requestedMinKHz = baseline.first,
                    requestedMaxKHz = baseline.second,
                    actualMinKHz = actual?.minKHz,
                    actualMaxKHz = actual?.maxKHz,
                    verified = result.verified,
                    writeAccepted = result.applied,
                ))
            }
            sessionApplied = emptyMap()
            reassertionsSpent = emptyMap()
            refreshManualSessionProp()
            refreshFrequencyControls()
            withContext(Dispatchers.Main) {
                actionNotice = CpuActionNotice(
                    when {
                        attempted == 0 -> CpuActionReason.NoManualIntents
                        allVerified -> CpuActionReason.Restored
                        else -> CpuActionReason.PartialRestore
                    }
                )
            }
        }
    }

    /**
     * Restores one policy to its full proven hardware range. Choosing the full
     * range is still a manual decision, so it goes through the same ownership
     * gate and durable lock as any other hand-applied limit.
     */
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
                    actionNotice = CpuActionNotice(CpuActionReason.HardwareRangeUnknown)
                }
                return@launch
            }
            submitManualLimits(
                policyPath = policyPath,
                minKHz = min,
                maxKHz = max,
                successReason = CpuActionReason.HardwareRangeRestored,
            )
        }
    }

    fun consumeActionNotice() {
        actionNotice = null
    }

    fun setCoreOnline(cpu: Int, online: Boolean) {
        manualControlEnabled = true
        viewModelScope.launch(Dispatchers.IO) {
            CpuTopologyUtil.setCoreOnline(cpu, online)
            withContext(Dispatchers.Main) { refreshRows() }
        }
    }

    /**
     * A core-preset tile: turn clusters on and off.
     *
     * Two things were wrong with this, and neither was the toggling itself.
     *
     * 1. It reported **nothing**. No notice, no message, no highlight — so the only
     *    evidence a tap had done something was a core row changing far down the
     *    page. It read exactly like a dead control.
     * 2. Nothing showed which preset was last chosen, so the row of tiles had no
     *    selected state at all.
     *
     * The result is now measured after the refresh rather than assumed from the
     * intent: what is reported is the number of cores that actually read back as
     * online. A preset that could not put a single core online is reported as
     * refused, not as applied.
     */
    fun applyQuickConfig(id: String) {
        manualControlEnabled = true
        val clustersSnapshot = clusters
        if (clustersSnapshot.isEmpty()) {
            actionNotice = CpuActionNotice(CpuActionReason.UnknownClusterTopology)
            return
        }
        val before = coreRows.count { it.online }

        viewModelScope.launch(Dispatchers.IO) {
            when (id) {
                "all_on" -> clustersSnapshot.forEach { cluster ->
                    cluster.cores.forEach { CpuTopologyUtil.setCoreOnline(it, true) }
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
            withContext(Dispatchers.Main) {
                refreshRows()
                val online = coreRows.count { it.online }
                val total = coreRows.size
                val reason = when {
                    // صفر صفوف = لم نقرأ، وهذه ليست «نجاحًا» ولا «رفضًا».
                    total == 0 -> CpuActionReason.CoresUnreadable
                    // ولا صف تغيّر = رفض هذا النمط، لا نجاحه.
                    online == before -> CpuActionReason.PresetRefused
                    else -> CpuActionReason.PresetApplied
                }
                actionNotice = CpuActionNotice(
                    reason = reason,
                    onlineCores = online,
                    totalCores = total,
                    presetId = id,
                )
                appliedQuickConfig = id
            }
        }
    }
}
