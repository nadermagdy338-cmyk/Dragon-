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
import nd.max.core.hardware.RootFileAccess
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
        // **قراءة داخل العملية أوّلًا (رحلة صدفة أُزيلت من مسار فتح الشاشات):**
        // `/sys/devices/system/cpu/possible` عقدة نواة يقرأها uid التطبيق بلا جذر، فكانت
        // `Shell.cmd("cat …")` **رحمة كاملة لسؤال لا يحتاج جذرًا** — وتُسأل عند فتح شاشة
        // الأنوية/الترددات/التحكم. والصقلة تبقى آخر طبقات الحقيقة لا تُحذف: ما لم يُقرأ من
        // العملية يُسأل عنه بالجذر كما كان. والعدوان يعطيان الرقم نفسه على كل جهاز مقيس.
        val fromRange = parseCpuRange(RootFileAccess.read(CPU_POSSIBLE_PATH).orEmpty())
        if (fromRange.isNotEmpty()) return fromRange.size

        // والاحتياطي داخل العملية أيضًا: عدد المجلّدات `cpu0…cpuN` — نفس ما يعدّه `ls | wc -l`.
        val counted = runCatching {
            File(CPU_ROOT).listFiles { file -> CPU_DIRECTORY.matches(file.name) }?.size
        }.getOrNull()
        if (counted != null && counted > 0) return counted

        return Shell.cmd("ls -d $CPU_ROOT/cpu[0-9]* 2>/dev/null | wc -l")
            .exec().out.joinToString("").trim().toIntOrNull() ?: 0
    }

    private const val CPU_ROOT = "/sys/devices/system/cpu"
    private const val CPU_POSSIBLE_PATH = "$CPU_ROOT/possible"
    private val CPU_DIRECTORY = Regex("cpu\\d+")

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
        val khz = RootFileAccess.read("$policyPath/cpuinfo_max_freq")?.toIntOrNull() ?: return 0
        return khz / 1000
    }

    /**
     * A cluster's hardware frequency floor in MHz (cpuinfo_min_freq, kHz on
     * disk) — the counterpart of [clusterMaxFreqMhz], read from the same
     * policy node and for the same reason: it is the fixed spec-sheet number
     * for that cluster, not the live scaling_min_freq that any thermal or
     * power policy may raise. Reads once alongside the ceiling.
     *
     * Returns 0 when the node is missing or unreadable, same "caller decides
     * how to render missing data" convention as the ceiling — a floor invented
     * from an observed sample would make every reading look pinned to the
     * bottom of a range the device never announced.
     */
    fun clusterMinFreqMhz(policyPath: String): Int {
        val khz = RootFileAccess.read("$policyPath/cpuinfo_min_freq")?.toIntOrNull() ?: return 0
        return khz / 1000
    }

    private fun onlinePath(cpu: Int) = "/sys/devices/system/cpu/cpu$cpu/online"

    /**
     * حالات الاتصال لكل الأنوية في **نداء واحد**.
     *
     * **ولماذا لم تبقَ نواةً نواة:** هذه هي القائمة التي تُقرأ **في كل دورة تحديث** (‏`refreshRows`
     * كل ٣ ثوانٍ في `CpuCoreControlViewModel`)، وكان كل سؤال فيها رحلةً مستقلة إلى القارئ الأصلي أو
     * إلى `RootNodeService`. والدفعة الواحدة تقرأ الملفات داخل العملية في نداء JNI واحد
     * (`RootFileAccess.readMany`، تكملة ١١٢) — فثمانية أنوية صارت رحلةً واحدة لا ثمانيًا. والقيمة
     * نفسها لكل نواة بلا تغيير: `readMany` موثَّقة بأن دلالتها لكل عقدة هي دلالة [RootFileAccess.read]
     * حرفيًّا (المقصوص غير الفارغ، أو `null`)، والترتيب بمحاذاة الفهرس.
     *
     * **وسقطت معها مساءلة `test -e` لـ`cpu0`** التي كانت رحلة صدفة كاملة في كل دورة: النتيجة
     * واحدة، لأن ما لا يُقرأ يُعدّ متصلًا — وهو نصّ القاعدة القديمة نفسها.
     */
    fun onlineStates(cpus: List<Int>): Map<Int, Boolean> {
        if (cpus.isEmpty()) return emptyMap()
        val values = RootFileAccess.readMany(cpus.map(::onlinePath))
        // عقد غائبة/غير مقروءة = "" كما كانت مع `exec().out` الفارغ — والقاعدة نفسها:
        // ما ليس "0" صريحًا يُعدّ متصلًا. (وقبله كان cpu0 يُعالَج بحالة خاصة لأن معظم النوى
        // لا تُنشئ له عقدة `online` أصلًا؛ والقاعدة أعلاه تُنتج النتيجة نفسها بنصّها.)
        return cpus.mapIndexed { index, cpu -> cpu to (values.getOrNull(index).orEmpty() != "0") }.toMap()
    }

    /** حالة نواة واحدة — تُقرأ دفعةً من عنصر واحد، فالمسار واحد لا مساران. */
    fun isCoreOnline(cpu: Int): Boolean = onlineStates(listOf(cpu))[cpu] ?: true

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

    private fun midrPath(cpu: Int) = "/sys/devices/system/cpu/cpu$cpu/regs/identification/midr_el1"

    /**
     * MIDR لكل نواة، **مُخزَّن** لأن قيمته عتاد لا حالة: رقم الجزء ومعرّف المُنفّذ لا يتغيّران
     * ما دامت العملية حيّة. وكان كل سؤال يفتح صدفة جذر كاملة (`cat ... | exec`) — والآن يُقرأ
     * مرة واحدة بالدفعة، والباقي بحثٌ في خريطة.
     */
    private val midrByCpu = ConcurrentHashMap<Int, String>()

    /**
     * هل استُنفدت محاولة قراءة MIDR؟ يُرفع بعد دفعةٍ لم تُقرأ فيها أيّ نواة، فلا تُعاد
     * **الدفعة الفاشلة** في كل دورة تحديث — وهي التي كانت تُفشل المسار كله بأطول طريق للقراءة.
     * وحدّه مُعلَن: عقدة تُصبح مقروءة بعد ذلك لا يُعاد سؤالها هذه الجلسة، وهو ثمن موثَّق لتفادي
     * تكرار رحلة في كل تحديث؛ والقيمة الناقصة تُعرض مجهولة كما كانت (لا تُخترع).
     */
    @Volatile
    private var midrUnreadable = false

    /**
     * أسماء الشرائح لعدّة أنوية في **نداء قراءة واحد** — الجدول نفسه، والتصنيع نفسه.
     *
     * Best-effort ARM core codename decoded from the MIDR_EL1 register some
     * kernels expose per-core under regs/identification. Table sourced from the
     * real ARM Ltd. (implementer 0x41) ARM_CPU_PART_* values used by the Linux
     * kernel and util-linux's lscpu — not guessed. Returns null (caller just
     * shows the core count) rather than ever inventing a model name.
     */
    fun coreNames(cpus: List<Int>): Map<Int, String?> {
        if (cpus.isEmpty()) return emptyMap()
        val distinct = cpus.distinct()
        val missing = distinct.filter { !midrByCpu.containsKey(it) }
        if (missing.isNotEmpty() && !midrUnreadable) {
            val codes = RootFileAccess.readMany(missing.map(::midrPath))
            var read = 0
            missing.forEachIndexed { index, cpu ->
                val code = codes.getOrNull(index)?.trim()?.removePrefix("0x")?.removePrefix("0X").orEmpty()
                // الفشل لا يُخزَّن: يُعاد سؤاله في الدورة القادمة (نداء واحد لها كلها)، أمّا قراءة
                // ناجحة فثابتة. فلا يُثبَّت عطب عابر كأنه حقيقة، ولا يُكرَّر الطلب في كل نواة.
                if (code.isNotEmpty()) {
                    midrByCpu[cpu] = code
                    read++
                }
            }
            if (read == 0) midrUnreadable = true
        }
        return distinct.associateWith { decodeMidr(midrByCpu[it].orEmpty()) }
    }

    /** اسم نواة واحدة — دفعةٌ من عنصر واحد، فلا يوجد مسار ثانٍ يقرأ الطريقة الأخرى. */
    fun decodeCoreName(cpu: Int): String? = coreNames(listOf(cpu))[cpu]

    private fun decodeMidr(hex: String): String? {
        val midr = hex.toLongOrNull(16) ?: return null
        val implementer = (midr shr 24) and 0xFF
        if (implementer != 0x41L) return null // Only decode ARM Ltd. designs; don't guess for others.
        val partNum = ((midr shr 4) and 0xFFF).toInt()
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
    // تخصيص مجموعات cpuset للأنوية: أيّ مجموعة (تطبيق أمامي · خلفي · نظام …) يُسمح
    // لها بالجدولة على أيّ الأنوية.
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

    /**
     * Reads current core assignment for every cpuset group that exists on this device.
     *
     * **والوجود والقراءة دفعةً واحدة.** كان السؤال ثماني رحلات (‏`exists` ثم `read` لكل مجموعة)
     * وهذا المسار يعمل في كل تحميل للشاشة. والقياس نفسه لكل مجموعة بلا تغيير: مسار غير موجود
     * يُسقط مجموعته، والقراءة الفاشلة تعني `""` كما كانت (`parseCpuRange("")` = قائمة فارغة).
     */
    fun readCpusetGroups(): List<CpusetGroup> {
        val paths = CPUSET_GROUPS.map { (key, _) -> "$CPUSET_BASE/$key/cpus" }
        val flags = RootFileAccess.existing(paths)
        val present = CPUSET_GROUPS.indices.filter { flags.getOrNull(it) == true }
        if (present.isEmpty()) return emptyList()
        val values = RootFileAccess.readMany(present.map { paths[it] })
        return present.mapIndexed { index, groupIndex ->
            val (key, label) = CPUSET_GROUPS[groupIndex]
            CpusetGroup(
                key = key,
                label = label,
                path = paths[groupIndex],
                cores = parseCpuRange(values.getOrNull(index).orEmpty())
            )
        }
    }

    /** Writes an explicit core list back to a cpuset group's `cpus` node. */
    fun writeCpusetGroup(path: String, cores: List<Int>) {
        if (cores.isEmpty()) return
        val value = cores.sorted().joinToString(",")
        Shell.cmd("echo $value > $path 2>/dev/null").exec()
    }
}
