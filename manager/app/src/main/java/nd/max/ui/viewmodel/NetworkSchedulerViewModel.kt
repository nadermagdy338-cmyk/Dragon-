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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nd.max.MaxManagerProps
import nd.max.ui.util.PropertyUtils

/**
 * Combined Network (TCP/IP stack, `/proc/sys/net/ipv4`) and Scheduler
 * (`/proc/sys/kernel/sched_*`) tuning surface.
 *
 * Adapted from ZKM's NetworkUtils/NetworkViewModel + SchdulerUtils/
 * SchedulerViewModel, but not a straight port: ZKM reads/writes through its
 * own `Utils.readFile`/`Utils.testFile`/`Utils.writeFile` helpers, which
 * don't exist in MaxManager. This targets the same nodes through direct
 * `Shell.cmd` calls (`test -e` / `cat` / `echo >`), the same convention
 * [AdrenoGpuViewModel] and [MaliFreqViewModel] already use, and persists
 * user overrides through [MaxManagerProps] instead of ZKM's SharedPreferences.
 *
 * Every toggle/value here is independently optional: a node not existing on
 * a given kernel just hides that row rather than failing the whole screen,
 * mirroring ZKM's per-node `hasXxx` flags.
 */
class NetworkSchedulerViewModel : ViewModel() {

    data class TunableItem(val label: String, val path: String, val value: String)

    companion object {
        // ---- Network (/proc/sys/net/ipv4) ----
        private const val TCP_CONG = "/proc/sys/net/ipv4/tcp_congestion_control"
        private const val TCP_AVAIL_CONG = "/proc/sys/net/ipv4/tcp_available_congestion_control"
        private const val TCP_SYNCOOKIES = "/proc/sys/net/ipv4/tcp_syncookies"
        private const val TCP_REUSE = "/proc/sys/net/ipv4/tcp_tw_reuse"
        private const val TCP_FASTOPEN = "/proc/sys/net/ipv4/tcp_fastopen"
        private const val TCP_SACK = "/proc/sys/net/ipv4/tcp_sack"
        private const val TCP_ECN = "/proc/sys/net/ipv4/tcp_ecn"

        // ---- Scheduler (/proc/sys/kernel) ----
        private const val PROC_KERNEL = "/proc/sys/kernel"
        private const val SCHED_BORE = "$PROC_KERNEL/sched_bore"
        private const val SCHED_AUTOGROUP = "$PROC_KERNEL/sched_autogroup_enabled"
        private const val SCHED_CHILD_RUNS_FIRST = "$PROC_KERNEL/sched_child_runs_first"
        private const val SCHED_CSTATE_AWARE = "$PROC_KERNEL/sched_cstate_aware"
        private const val SCHED_SCHEDSTATS = "$PROC_KERNEL/sched_schedstats"
        private const val SCHED_TUNABLE_SCALING = "$PROC_KERNEL/sched_tunable_scaling"
        private const val SCHED_UCLAMP_MAX = "$PROC_KERNEL/sched_util_clamp_max"
        private const val SCHED_UCLAMP_MIN = "$PROC_KERNEL/sched_util_clamp_min"
        private const val PRINTK = "$PROC_KERNEL/printk"

        /** Same raw tunable set ZKM exposes under "Advanced Parameters", label -> node path. */
        private val GENERIC_SCHED_TUNABLES = linkedMapOf(
            "Deadline Period Max (us)" to "$PROC_KERNEL/sched_deadline_period_max_us",
            "Deadline Period Min (us)" to "$PROC_KERNEL/sched_deadline_period_min_us",
            "Energy Aware" to "$PROC_KERNEL/sched_energy_aware",
            "Latency (ns)" to "$PROC_KERNEL/sched_latency_ns",
            "Migration Cost (ns)" to "$PROC_KERNEL/sched_migration_cost_ns",
            "Min Granularity (ns)" to "$PROC_KERNEL/sched_min_granularity_ns",
            "Nr Migrate" to "$PROC_KERNEL/sched_nr_migrate",
            "PELT Multiplier" to "$PROC_KERNEL/sched_pelt_multiplier",
            "RR Timeslice (ms)" to "$PROC_KERNEL/sched_rr_timeslice_ms",
            "RT Period (us)" to "$PROC_KERNEL/sched_rt_period_us",
            "RT Runtime (us)" to "$PROC_KERNEL/sched_rt_runtime_us",
            "UClamp Min RT Default" to "$PROC_KERNEL/sched_util_clamp_min_rt_default",
            "Wakeup Granularity (ns)" to "$PROC_KERNEL/sched_wakeup_granularity_ns"
        )

        private const val PROP_TCP_CONG = MaxManagerProps.Network.TCP_CONGESTION
        private const val PROP_SYNCOOKIES = MaxManagerProps.Network.TCP_SYNCOOKIES
        private const val PROP_TCP_REUSE = MaxManagerProps.Network.TCP_REUSE
        private const val PROP_TCP_FASTOPEN = MaxManagerProps.Network.TCP_FASTOPEN
        private const val PROP_TCP_SACK = MaxManagerProps.Network.TCP_SACK
        private const val PROP_TCP_ECN = MaxManagerProps.Network.TCP_ECN

        private const val PROP_BORE = MaxManagerProps.Scheduler.BORE
        private const val PROP_AUTOGROUP = MaxManagerProps.Scheduler.AUTOGROUP
        private const val PROP_CHILD_RUNS_FIRST = MaxManagerProps.Scheduler.CHILD_RUNS_FIRST
        private const val PROP_SCHEDSTATS = MaxManagerProps.Scheduler.SCHEDSTATS
        private const val PROP_TUNABLE_SCALING = MaxManagerProps.Scheduler.TUNABLE_SCALING
        private const val PROP_CSTATE_AWARE = MaxManagerProps.Scheduler.CSTATE_AWARE
        private const val PROP_UCLAMP_MAX = MaxManagerProps.Scheduler.UCLAMP_MAX
        private const val PROP_UCLAMP_MIN = MaxManagerProps.Scheduler.UCLAMP_MIN
        private const val PROP_PRINTK = MaxManagerProps.Scheduler.PRINTK
        private const val PROP_GENERIC_OVERRIDES = MaxManagerProps.Scheduler.GENERIC_OVERRIDES

        val TUNABLE_SCALING_MODES = listOf("0", "1", "2") // none / log / linear
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set

    // ---- Network state ----
    var hasTcpCongestion by mutableStateOf(false); private set
    var tcpCongestion by mutableStateOf(""); @JvmName("setTcpCongestionState") private set
    var availableCongestion by mutableStateOf<List<String>>(emptyList()); private set

    var hasSyncookies by mutableStateOf(false); private set
    var syncookiesEnabled by mutableStateOf(false); private set

    var hasTcpReuse by mutableStateOf(false); private set
    var tcpReuseEnabled by mutableStateOf(false); private set

    var hasTcpFastopen by mutableStateOf(false); private set
    var tcpFastopenEnabled by mutableStateOf(false); private set

    var hasTcpSack by mutableStateOf(false); private set
    var tcpSackEnabled by mutableStateOf(false); private set

    var hasTcpEcn by mutableStateOf(false); private set
    var tcpEcnEnabled by mutableStateOf(false); private set

    // ---- Scheduler state ----
    var hasBore by mutableStateOf(false); private set
    var boreEnabled by mutableStateOf(false); private set

    var hasAutogroup by mutableStateOf(false); private set
    var autogroupEnabled by mutableStateOf(false); private set

    var hasChildRunsFirst by mutableStateOf(false); private set
    var childRunsFirstEnabled by mutableStateOf(false); private set

    var hasSchedstats by mutableStateOf(false); private set
    var schedstatsEnabled by mutableStateOf(false); private set

    var hasTunableScaling by mutableStateOf(false); private set
    var tunableScalingIndex by mutableStateOf(0); private set

    var hasCstateAware by mutableStateOf(false); private set
    var cstateAwareEnabled by mutableStateOf(false); private set

    var hasUclampMax by mutableStateOf(false); private set
    var uclampMaxValue by mutableStateOf(""); private set

    var hasUclampMin by mutableStateOf(false); private set
    var uclampMinValue by mutableStateOf(""); private set

    var hasPrintk by mutableStateOf(false); private set
    var printkValue by mutableStateOf(""); private set

    var genericTunables by mutableStateOf<List<TunableItem>>(emptyList())
        private set

    // ---- Shell helpers ----

    private fun nodeExists(path: String): Boolean =
        Shell.cmd("test -e $path && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"

    private fun readNode(path: String): String =
        Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString("").trim()

    private fun writeNode(path: String, value: String) {
        val safeValue = value.replace("'", "'\\''")
        Shell.cmd("echo '$safeValue' > $path 2>/dev/null").exec()
    }

    private fun isOn(value: String): Boolean = value.trim() == "1"

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            hasTcpCongestion = nodeExists(TCP_CONG)
            if (hasTcpCongestion) {
                availableCongestion = readNode(TCP_AVAIL_CONG).split(Regex("\\s+")).filter { it.isNotBlank() }
                val saved = PropertyUtils.get(PROP_TCP_CONG)
                if (saved.isNotEmpty() && saved in availableCongestion) writeNode(TCP_CONG, saved)
                tcpCongestion = readNode(TCP_CONG)
            }

            hasSyncookies = nodeExists(TCP_SYNCOOKIES)
            if (hasSyncookies) {
                applySavedBool(PROP_SYNCOOKIES, TCP_SYNCOOKIES)
                syncookiesEnabled = isOn(readNode(TCP_SYNCOOKIES))
            }

            hasTcpReuse = nodeExists(TCP_REUSE)
            if (hasTcpReuse) {
                applySavedBool(PROP_TCP_REUSE, TCP_REUSE)
                tcpReuseEnabled = isOn(readNode(TCP_REUSE))
            }

            hasTcpFastopen = nodeExists(TCP_FASTOPEN)
            if (hasTcpFastopen) {
                applySavedBool(PROP_TCP_FASTOPEN, TCP_FASTOPEN)
                tcpFastopenEnabled = isOn(readNode(TCP_FASTOPEN))
            }

            hasTcpSack = nodeExists(TCP_SACK)
            if (hasTcpSack) {
                applySavedBool(PROP_TCP_SACK, TCP_SACK)
                tcpSackEnabled = isOn(readNode(TCP_SACK))
            }

            hasTcpEcn = nodeExists(TCP_ECN)
            if (hasTcpEcn) {
                applySavedBool(PROP_TCP_ECN, TCP_ECN)
                tcpEcnEnabled = isOn(readNode(TCP_ECN))
            }

            hasBore = nodeExists(SCHED_BORE)
            if (hasBore) {
                applySavedBool(PROP_BORE, SCHED_BORE)
                boreEnabled = isOn(readNode(SCHED_BORE))
            }

            hasAutogroup = nodeExists(SCHED_AUTOGROUP)
            if (hasAutogroup) {
                applySavedBool(PROP_AUTOGROUP, SCHED_AUTOGROUP)
                autogroupEnabled = isOn(readNode(SCHED_AUTOGROUP))
            }

            hasChildRunsFirst = nodeExists(SCHED_CHILD_RUNS_FIRST)
            if (hasChildRunsFirst) {
                applySavedBool(PROP_CHILD_RUNS_FIRST, SCHED_CHILD_RUNS_FIRST)
                childRunsFirstEnabled = isOn(readNode(SCHED_CHILD_RUNS_FIRST))
            }

            hasSchedstats = nodeExists(SCHED_SCHEDSTATS)
            if (hasSchedstats) {
                applySavedBool(PROP_SCHEDSTATS, SCHED_SCHEDSTATS)
                schedstatsEnabled = isOn(readNode(SCHED_SCHEDSTATS))
            }

            hasTunableScaling = nodeExists(SCHED_TUNABLE_SCALING)
            if (hasTunableScaling) {
                val saved = PropertyUtils.get(PROP_TUNABLE_SCALING)
                if (saved.isNotEmpty() && saved in TUNABLE_SCALING_MODES) writeNode(SCHED_TUNABLE_SCALING, saved)
                tunableScalingIndex = readNode(SCHED_TUNABLE_SCALING).toIntOrNull()?.coerceIn(0, 2) ?: 0
            }

            hasCstateAware = nodeExists(SCHED_CSTATE_AWARE)
            if (hasCstateAware) {
                applySavedBool(PROP_CSTATE_AWARE, SCHED_CSTATE_AWARE)
                cstateAwareEnabled = isOn(readNode(SCHED_CSTATE_AWARE))
            }

            hasUclampMax = nodeExists(SCHED_UCLAMP_MAX)
            if (hasUclampMax) {
                val saved = PropertyUtils.get(PROP_UCLAMP_MAX)
                if (saved.isNotEmpty()) writeNode(SCHED_UCLAMP_MAX, saved)
                uclampMaxValue = readNode(SCHED_UCLAMP_MAX)
            }

            hasUclampMin = nodeExists(SCHED_UCLAMP_MIN)
            if (hasUclampMin) {
                val saved = PropertyUtils.get(PROP_UCLAMP_MIN)
                if (saved.isNotEmpty()) writeNode(SCHED_UCLAMP_MIN, saved)
                uclampMinValue = readNode(SCHED_UCLAMP_MIN)
            }

            hasPrintk = nodeExists(PRINTK)
            if (hasPrintk) {
                val saved = PropertyUtils.get(PROP_PRINTK)
                if (saved.isNotEmpty()) writeNode(PRINTK, saved)
                printkValue = readNode(PRINTK)
            }

            loadGenericTunables()

            isAvailable = hasTcpCongestion || hasSyncookies || hasTcpReuse || hasTcpFastopen ||
                hasTcpSack || hasTcpEcn || hasBore || hasAutogroup || hasChildRunsFirst ||
                hasSchedstats || hasTunableScaling || hasCstateAware || hasUclampMax ||
                hasUclampMin || hasPrintk || genericTunables.isNotEmpty()
        }
    }

    /** Re-applies a persisted "0"/"1" override onto [path] before it's read back, if one exists. */
    private fun applySavedBool(prop: String, path: String) {
        val saved = PropertyUtils.get(prop)
        if (saved == "0" || saved == "1") writeNode(path, saved)
    }

    private fun loadGenericTunables() {
        val overrides = parseGenericOverrides(PropertyUtils.get(PROP_GENERIC_OVERRIDES))
        val found = mutableListOf<TunableItem>()
        GENERIC_SCHED_TUNABLES.forEach { (label, path) ->
            if (!nodeExists(path)) return@forEach
            overrides[path]?.let { writeNode(path, it) }
            val value = readNode(path)
            if (value.isNotEmpty()) found.add(TunableItem(label, path, value))
        }
        genericTunables = found
    }

    private fun parseGenericOverrides(serialized: String): Map<String, String> {
        if (serialized.isEmpty()) return emptyMap()
        return serialized.split(";")
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                entry.substring(0, idx) to entry.substring(idx + 1)
            }
            .toMap()
    }

    private fun serializeGenericOverrides(overrides: Map<String, String>): String =
        overrides.entries.joinToString(";") { "${it.key}=${it.value}" }

    // ---- Network setters ----

    fun setTcpCongestion(algorithm: String) {
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(TCP_CONG, algorithm)
            tcpCongestion = readNode(TCP_CONG)
            PropertyUtils.set(PROP_TCP_CONG, algorithm)
        }
    }

    fun setSyncookies(enabled: Boolean) = setBoolNode(
        TCP_SYNCOOKIES, PROP_SYNCOOKIES, enabled
    ) { syncookiesEnabled = it }

    fun setTcpReuse(enabled: Boolean) = setBoolNode(
        TCP_REUSE, PROP_TCP_REUSE, enabled
    ) { tcpReuseEnabled = it }

    fun setTcpFastopen(enabled: Boolean) = setBoolNode(
        TCP_FASTOPEN, PROP_TCP_FASTOPEN, enabled
    ) { tcpFastopenEnabled = it }

    fun setTcpSack(enabled: Boolean) = setBoolNode(
        TCP_SACK, PROP_TCP_SACK, enabled
    ) { tcpSackEnabled = it }

    fun setTcpEcn(enabled: Boolean) = setBoolNode(
        TCP_ECN, PROP_TCP_ECN, enabled
    ) { tcpEcnEnabled = it }

    // ---- Scheduler setters ----

    fun setBore(enabled: Boolean) = setBoolNode(
        SCHED_BORE, PROP_BORE, enabled
    ) { boreEnabled = it }

    fun setAutogroup(enabled: Boolean) = setBoolNode(
        SCHED_AUTOGROUP, PROP_AUTOGROUP, enabled
    ) { autogroupEnabled = it }

    fun setChildRunsFirst(enabled: Boolean) = setBoolNode(
        SCHED_CHILD_RUNS_FIRST, PROP_CHILD_RUNS_FIRST, enabled
    ) { childRunsFirstEnabled = it }

    fun setSchedstats(enabled: Boolean) = setBoolNode(
        SCHED_SCHEDSTATS, PROP_SCHEDSTATS, enabled
    ) { schedstatsEnabled = it }

    fun setCstateAware(enabled: Boolean) = setBoolNode(
        SCHED_CSTATE_AWARE, PROP_CSTATE_AWARE, enabled
    ) { cstateAwareEnabled = it }

    fun setTunableScaling(index: Int) {
        val safeIndex = index.coerceIn(0, TUNABLE_SCALING_MODES.lastIndex)
        val mode = TUNABLE_SCALING_MODES[safeIndex]
        tunableScalingIndex = safeIndex
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(SCHED_TUNABLE_SCALING, mode)
            PropertyUtils.set(PROP_TUNABLE_SCALING, mode)
        }
    }

    fun setUclampMax(value: String) {
        uclampMaxValue = value
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(SCHED_UCLAMP_MAX, value)
            PropertyUtils.set(PROP_UCLAMP_MAX, value)
        }
    }

    fun setUclampMin(value: String) {
        uclampMinValue = value
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(SCHED_UCLAMP_MIN, value)
            PropertyUtils.set(PROP_UCLAMP_MIN, value)
        }
    }

    fun setPrintk(value: String) {
        printkValue = value
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(PRINTK, value)
            PropertyUtils.set(PROP_PRINTK, value)
        }
    }

    fun setGenericTunable(path: String, value: String) {
        genericTunables = genericTunables.map { if (it.path == path) it.copy(value = value) else it }
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(path, value)
            val overrides = parseGenericOverrides(PropertyUtils.get(PROP_GENERIC_OVERRIDES)).toMutableMap()
            overrides[path] = value
            PropertyUtils.set(PROP_GENERIC_OVERRIDES, serializeGenericOverrides(overrides))
        }
    }

    private fun setBoolNode(path: String, prop: String, enabled: Boolean, updateState: (Boolean) -> Unit) {
        updateState(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            writeNode(path, if (enabled) "1" else "0")
            PropertyUtils.set(prop, if (enabled) "1" else "0")
        }
    }
}
