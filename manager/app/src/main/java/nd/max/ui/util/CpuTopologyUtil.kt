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

package nd.max.ui.util

import com.topjohnwu.superuser.Shell

/**
 * Single source of truth for this device's CPU cluster topology.
 *
 * Both the CPU Core Control screen (per-core hotplug) and the Advanced
 * Configuration screen (per-cluster governor pinning) need to know how cores
 * are grouped into clusters. Instead of each screen guessing its own core
 * ranges, both read from this one detector so the two stay consistent with
 * each other and with whatever the kernel itself reports.
 *
 * Clusters are derived from cpufreq policies (`/sys/devices/system/cpu/cpufreq/policyN`),
 * the same grouping the kernel's own frequency scaling uses — never hardcoded
 * core counts, since those vary per chipset.
 */
object CpuTopologyUtil {

    data class CpuCluster(
        val label: String,
        val shortTag: String,
        val cores: List<Int>,
        val policyPath: String
    )

    data class CoreState(
        val cpu: Int,
        val online: Boolean,
        val isMaster: Boolean,
        val coreName: String?
    )

    /** Total logical CPU count as reported by the kernel, independent of current online/offline state. */
    fun totalCpuCount(): Int {
        val fromList = Shell.cmd(
            "cat /sys/devices/system/cpu/possible 2>/dev/null"
        ).exec().out.joinToString("").trim()
        val fromRange = parseCpuRange(fromList)
        if (fromRange.isNotEmpty()) return fromRange.size

        return Shell.cmd("ls -d /sys/devices/system/cpu/cpu[0-9]* 2>/dev/null | wc -l")
            .exec().out.joinToString("").trim().toIntOrNull() ?: 0
    }

    /** Parses kernel range lists like "0-3,4-6,7" into an explicit list of ints. */
    private fun parseCpuRange(raw: String): List<Int> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").flatMap { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (start, end) = trimmed.split("-").let { it[0].toIntOrNull() to it.getOrNull(1)?.toIntOrNull() }
                if (start != null && end != null) (start..end).toList() else emptyList()
            } else {
                trimmed.toIntOrNull()?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    /**
     * Groups CPUs into clusters by reading each cpufreq policy's related_cpus
     * (falls back to affected_cpus on older kernels). Clusters are labeled by
     * ordinal position (lowest core index = "Efficiency", highest = "Prime")
     * rather than a fixed core count, so this works across 2/3/4-cluster designs.
     */
    fun detectClusters(): List<CpuCluster> {
        val policies = nd.max.core.hardware.CpuHardwareBackend.policies()
        if (policies.isEmpty()) return emptyList()

        val rawClusters = mutableListOf<Pair<String, List<Int>>>()
        for (policy in policies) {
            val relatedRaw = nd.max.core.hardware.RootFileAccess.read("${policy.path}/related_cpus")
                ?: nd.max.core.hardware.RootFileAccess.read("${policy.path}/affected_cpus")
                ?: policy.name.removePrefix("cpu").takeIf { policy.name.startsWith("cpu") }
                ?: ""
            val cores = relatedRaw.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }.sorted()
            if (cores.isEmpty()) continue
            if (rawClusters.none { it.second.firstOrNull() == cores.first() }) {
                rawClusters.add(policy.path to cores)
            }
        }

        val sorted = rawClusters.sortedBy { it.second.first() }

        return sorted.mapIndexed { index, (policyPath, cores) ->
            val (label, tag) = when (sorted.size) {
                1 -> "All Cores" to "CPU"
                2 -> if (index == 0) "Efficiency Cluster" to "SILVER" else "Performance Cluster" to "GOLD"
                else -> when (index) {
                    0 -> "Efficiency Cluster" to "SILVER"
                    sorted.lastIndex -> "Prime Supercore" to "PRIME"
                    else -> "Performance Cluster" to "GOLD"
                }
            }
            CpuCluster(label = label, shortTag = tag, cores = cores, policyPath = policyPath)
        }
    }

    /**
     * A cluster's hardware frequency ceiling in MHz (cpuinfo_max_freq, kHz on
     * disk) -- the fixed spec-sheet number for that cluster, not the
     * constantly-shifting live scaling_cur_freq. Deliberately the former:
     * this is meant for a one-line "what is this cluster capable of" label
     * next to its online count, not a live gauge that would need its own
     * polling loop. Returns 0 if the node is missing or unreadable, same
     * "caller decides how to render missing data" convention as
     * [decodeCoreName] returning null.
     */
    fun clusterMaxFreqMhz(policyPath: String): Int {
        val khz = Shell.cmd("cat $policyPath/cpuinfo_max_freq 2>/dev/null").exec()
            .out.joinToString("").trim().toIntOrNull() ?: return 0
        return khz / 1000
    }

    /** cpu0 has no writable 'online' node on most kernels — it's always considered online. */
    fun isCoreOnline(cpu: Int): Boolean {
        if (cpu == 0) {
            val hasNode = Shell.cmd("test -e /sys/devices/system/cpu/cpu0/online && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"
            if (!hasNode) return true
        }
        val v = Shell.cmd("cat /sys/devices/system/cpu/cpu$cpu/online 2>/dev/null").exec()
            .out.joinToString("").trim()
        return v != "0"
    }

    /**
     * Toggles a single core's hotplug state. CPU0 is never offlined here — most
     * kernels refuse it anyway (it's the boot/master CPU interrupts are pinned
     * to), and forcing it off from userspace risks a hang.
     */
    fun setCoreOnline(cpu: Int, online: Boolean): Boolean {
        if (cpu == 0) return false
        Shell.cmd("echo ${if (online) 1 else 0} > /sys/devices/system/cpu/cpu$cpu/online 2>/dev/null").exec()
        return true
    }

    /**
     * Best-effort ARM core codename decoded from the MIDR_EL1 register some
     * kernels expose per-core under regs/identification. Table sourced from the
     * real ARM Ltd. (implementer 0x41) ARM_CPU_PART_* values used by the Linux
     * kernel and util-linux's lscpu — not guessed. Returns null (caller just
     * shows the core count) rather than ever inventing a model name.
     */
    fun decodeCoreName(cpu: Int): String? {
        val midrHex = Shell.cmd(
            "cat /sys/devices/system/cpu/cpu$cpu/regs/identification/midr_el1 2>/dev/null"
        ).exec().out.joinToString("").trim().removePrefix("0x").removePrefix("0X")
        val midr = midrHex.toLongOrNull(16) ?: return null
        val implementer = (midr shr 24) and 0xFF
        val partNum = ((midr shr 4) and 0xFFF).toInt()
        if (implementer != 0x41L) return null // Only decode ARM Ltd. designs; don't guess for others.
        return ARM_LTD_PARTS[partNum]
    }

    // Verified against the Linux kernel's arch/arm64/include/asm/cputype.h and
    // util-linux's lscpu-arm.c ARM implementer table (id 0x41).
    private val ARM_LTD_PARTS = mapOf(
        0xd03 to "Cortex-A53",
        0xd04 to "Cortex-A35",
        0xd05 to "Cortex-A55",
        0xd06 to "Cortex-A65",
        0xd07 to "Cortex-A57",
        0xd08 to "Cortex-A72",
        0xd09 to "Cortex-A73",
        0xd0a to "Cortex-A75",
        0xd0b to "Cortex-A76",
        0xd0c to "Neoverse-N1",
        0xd0d to "Cortex-A77",
        0xd0e to "Cortex-A76AE",
        0xd40 to "Neoverse-V1",
        0xd41 to "Cortex-A78",
        0xd42 to "Cortex-A78AE",
        0xd43 to "Cortex-A65AE",
        0xd44 to "Cortex-X1",
        0xd46 to "Cortex-A510",
        0xd47 to "Cortex-A710",
        0xd48 to "Cortex-X2",
        0xd49 to "Neoverse-N2",
        0xd4b to "Cortex-A78C",
        0xd4c to "Cortex-X1C",
        0xd4d to "Cortex-A715",
        0xd4e to "Cortex-X3",
        0xd4f to "Neoverse-V2",
        0xd80 to "Cortex-A520",
        0xd81 to "Cortex-A720",
        0xd82 to "Cortex-X4",
        0xd84 to "Neoverse-V3",
        0xd85 to "Cortex-X925",
        0xd87 to "Cortex-A725",
        0xd88 to "Cortex-A520AE",
        0xd89 to "Cortex-A720AE",
        0xd8e to "Neoverse-N3",
        0xd8f to "Cortex-A320"
    )

    // ─────────────────────────────────────────────────────────────────────
    // Cpuset group affinity (adapted from ZKM's CpuGpuUtils cpuset logic).
    // Complements per-core hotplug above: hotplug decides which cores exist
    // at all, cpuset decides which of the *online* cores each scheduling
    // group (foreground app, background app, system, ...) is allowed to run
    // threads on. Both live on this screen because they're the same mental
    // model to the user ("which cores does X get").
    // ─────────────────────────────────────────────────────────────────────

    private const val CPUSET_BASE = "/dev/cpuset"

    data class CpusetGroup(
        val key: String,
        val label: String,
        val path: String,
        val cores: List<Int>
    )

    /** Known scheduler cpuset groups, in the order they're shown in the UI. */
    private val CPUSET_GROUPS = listOf(
        "top-app" to "Top App",
        "foreground" to "Foreground",
        "background" to "Background",
        "system-background" to "System Background"
    )

    /** Reads current core assignment for every cpuset group that exists on this device. */
    fun readCpusetGroups(): List<CpusetGroup> {
        return CPUSET_GROUPS.mapNotNull { (key, label) ->
            val path = "$CPUSET_BASE/$key/cpus"
            val exists = Shell.cmd("test -f $path && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"
            if (!exists) return@mapNotNull null
            val raw = Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString("").trim()
            CpusetGroup(key = key, label = label, path = path, cores = parseCpuRange(raw))
        }
    }

    /** Writes an explicit core list back to a cpuset group's `cpus` node. */
    fun writeCpusetGroup(path: String, cores: List<Int>) {
        if (cores.isEmpty()) return
        val value = cores.sorted().joinToString(",")
        Shell.cmd("echo $value > $path 2>/dev/null").exec()
    }
}
