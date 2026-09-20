package nd.max.core.atlas

/**
 * The second line of defense: a **candidate** catalog built from the interface vocabulary that
 * open-source kernel-manager projects accumulated over years.
 *
 * ## Why it exists
 *
 * The reviewed catalog is deliberately small and every entry in it is one interface somebody has to
 * justify. That is the right shape for knowledge that may be published, and the wrong shape for
 * coverage: a device whose interfaces were never reviewed ends up with almost nothing to read, and the
 * user is told "unknown" about facts the community has known for a decade.
 *
 * So there are two banks, consulted in order:
 *
 * 1. [AtlasReviewedSeeds] — our own reviewed knowledge, read first.
 * 2. this bank — the accumulated vocabulary, consulted **only** for the domains the first one left
 *    unresolved, and only for interfaces no reviewed entry already addressed.
 *
 * ## What it may never do
 *
 * - **It may never claim meaning.** Every observation that comes from here is `INFERRED` at best: an
 *   interface name is evidence that a file exists, not evidence that its number means what its name
 *   suggests. A reading from this bank can never be promoted to `REVIEWED_MATCH`.
 * - **It may never write, enumerate outside an approved root, or use a pattern.** It is an
 *   [AtlasCatalog], so it passes the same validator as the reviewed bank: provenance is mandatory,
 *   roots must be approved and enumerable, attributes must be single safe basenames, and a child
 *   selector is a plain prefix compared against names the kernel returned — never a glob.
 * - **It carries no path database.** Nothing here is a phone-by-phone map of absolute paths copied
 *   from another project. What is recorded is *interface vocabulary* — which attribute means which
 *   unit under which enumerable class directory — and the enumeration is what makes it work on a
 *   device nobody has written down.
 * - **It is not a licence laundering channel.** Two of the projects whose inventories are credited
 *   here are GPL-3.0. No code, no table, no conversion factor and no file was copied from them: the
 *   fact that a kernel interface is named `gpuclk` under `/sys/class/kgsl` is a public fact about the
 *   device, and this file states where the inventory came from so a reviewer can check the claim
 *   rather than trust it.
 *
 * ## What is deliberately absent
 *
 * - **Nested attribute paths** (`topology/core_id`, `stats/time_in_state`, `queue/scheduler`): the
 *   grammar allows one safe basename inside one child, so a two-level attribute cannot be expressed
 *   yet. They are not smuggled in as path strings; extending the grammar is its own reviewed decision.
 * - **Sensors and display**: no approved root exists for them, and widening the approved anchors is a
 *   safety decision that does not belong in a knowledge bank.
 * - **Command nodes.** `/proc/gpufreq` and friends contain files that *accept* writes. Atlas is
 *   read-only, but naming a control node in a knowledge bank is how it ends up being read when a
 *   driver treats a read as a command, so those names stay out and the reason is recorded here.
 */
object AtlasCommunityBank {

    /** The bank's schema+content version. Separate from the reviewed bank so they version apart. */
    const val VERSION: String = "atlas-community-1"

    fun validation(): AtlasCatalogValidation = AtlasCatalog.validate(VERSION, entries())

    /**
     * The bank as a catalog. A bank that does not validate is a programming error, not a runtime
     * condition: shipping knowledge that fails its own rules is worse than shipping none.
     */
    fun catalog(): AtlasCatalog = when (val result = validation()) {
        is AtlasCatalogValidation.Valid -> result.catalog
        is AtlasCatalogValidation.Invalid ->
            error("community bank is invalid: ${result.problems.joinToString()}")
    }

    /** How a group of interfaces was established. One provenance per group, never per entry. */
    private data class Group(
        val sourceId: String,
        val reference: String,
        val revision: String,
        val licenseNote: String,
        val confidence: AtlasSourceConfidence,
    )

    private val CPUFREQ = Group(
        sourceId = "L02",
        reference = "https://docs.kernel.org/admin-guide/pm/cpufreq.html",
        revision = "live docs snapshot, fetched 2026-09-20",
        licenseNote = "Kernel documentation; semantics documented, not measured here",
        confidence = AtlasSourceConfidence.CLAIMED,
    )

    private val CPU_TOPOLOGY = Group(
        sourceId = "L03",
        reference = "https://docs.kernel.org/admin-guide/abi-testing.html",
        revision = "live docs snapshot, fetched 2026-09-20",
        licenseNote = "Kernel ABI documentation; semantics documented, not measured here",
        confidence = AtlasSourceConfidence.CLAIMED,
    )

    private val DEVFREQ = Group(
        sourceId = "L04",
        reference = "https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/v6.12/drivers/devfreq/devfreq.c",
        revision = "v6.12",
        licenseNote = "Kernel source; semantics documented, not measured here",
        confidence = AtlasSourceConfidence.CLAIMED,
    )

    private val THERMAL = Group(
        sourceId = "L05",
        reference = "https://docs.kernel.org/driver-api/thermal/sysfs-api.html",
        revision = "v6.12 ABI",
        licenseNote = "Kernel documentation; semantics documented, not measured here",
        confidence = AtlasSourceConfidence.CLAIMED,
    )

    private val POWER_SUPPLY = Group(
        sourceId = "L06",
        reference = "https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/v6.12/Documentation/power/power_supply_class.rst",
        revision = "v6.12",
        licenseNote = "Kernel documentation; semantics documented, not measured here",
        confidence = AtlasSourceConfidence.CLAIMED,
    )

    private val ZRAM = Group(
        sourceId = "LOCAL-ZRAM",
        reference = "local:manager/app/src/main/java/nd/max/core/hardware/ZramHardwareBackend.kt",
        revision = "working tree, observed 2026-09-20",
        licenseNote = "Project source, independently authored; no upstream code copied",
        confidence = AtlasSourceConfidence.DESIGN,
    )

    private val PSI = Group(
        sourceId = "LOCAL-PSI",
        reference = "local:manager/app/src/main/java/nd/max/core/maxai/MemoryStall.kt",
        revision = "working tree, observed 2026-09-20",
        licenseNote = "Project source, independently authored; no upstream code copied",
        confidence = AtlasSourceConfidence.DESIGN,
    )

    /**
     * Vendor interfaces whose *name* is community knowledge and whose unit is **not** established by
     * any documentation fetched in this run. The unit is therefore `UNKNOWN` and the reading is
     * reported raw: a conversion factor copied from a blog or a project would be a claim dressed as a
     * measurement, and a wrong divisor is indistinguishable from a wrong interface.
     */
    private val VENDOR_INVENTORY = Group(
        sourceId = "S01",
        reference = "https://github.com/SmartPack/SmartPack-Kernel-Manager/blob/c886fc8eae90e74fcc6784813be28feca5b54080/app/src/main/java/com/smartpack/kernelmanager/utils/kernel/gpu/GPUFreq.java",
        revision = "c886fc8eae90e74fcc6784813be28feca5b54080",
        licenseNote = "Interface names only, invented independently from public kernel facts; " +
            "the cited project is GPL-3.0 and no code, table or conversion factor was copied from it",
        confidence = AtlasSourceConfidence.FETCHED,
    )

    private val VENDOR_INVENTORY_MTK = Group(
        sourceId = "S09",
        reference = "https://github.com/JUANIMAN/PerfMTK",
        revision = "e7e4c268be82ee51d4a5df39fe99c65f67dbaa3d",
        licenseNote = "Interface names only, invented independently from public kernel facts; " +
            "the cited project is GPL-3.0 and no code, table or profile was copied from it",
        confidence = AtlasSourceConfidence.FETCHED,
    )

    fun entries(): List<AtlasCatalogEntry> = listOf(
        // ---- CPU: one interface per policy --------------------------------------------------------
        hint(
            id = "cpu.policy.cpuinfo_cur_freq", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "cpuinfo_cur_freq", unit = AtlasUnit.KILO_HERTZ, childPrefix = "policy", group = CPUFREQ,
            note = "kHz as the driver reports it right now. Distinct from scaling_cur_freq, which is the request.",
        ),
        hint(
            id = "cpu.policy.scaling_min_freq", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_min_freq", unit = AtlasUnit.KILO_HERTZ, childPrefix = "policy", group = CPUFREQ,
            note = "kHz. A policy limit, not a measurement of what the hardware did.",
        ),
        hint(
            id = "cpu.policy.scaling_max_freq", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_max_freq", unit = AtlasUnit.KILO_HERTZ, childPrefix = "policy", group = CPUFREQ,
            note = "kHz. Reported as configured; it proves no thermal ceiling is currently applied.",
        ),
        hint(
            id = "cpu.policy.cpuinfo_max_freq", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "cpuinfo_max_freq", unit = AtlasUnit.KILO_HERTZ, childPrefix = "policy", group = CPUFREQ,
            note = "kHz. Heaviest frequency the hardware and its tables allow.",
        ),
        hint(
            id = "cpu.policy.cpuinfo_min_freq", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "cpuinfo_min_freq", unit = AtlasUnit.KILO_HERTZ, childPrefix = "policy", group = CPUFREQ,
            note = "kHz. Lightest frequency the hardware and its tables allow.",
        ),
        hint(
            id = "cpu.policy.scaling_governor", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_governor", unit = AtlasUnit.UNKNOWN, childPrefix = "policy", group = CPUFREQ,
            note = "Free text. A governor name is a policy identifier, not an effect and not authority.",
        ),
        hint(
            id = "cpu.policy.scaling_driver", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "scaling_driver", unit = AtlasUnit.UNKNOWN, childPrefix = "policy", group = CPUFREQ,
            note = "Free text naming the driver behind the policy; it identifies the interface, not the silicon.",
        ),
        hint(
            id = "cpu.policy.affected_cpus", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "affected_cpus", unit = AtlasUnit.UNKNOWN, childPrefix = "policy", group = CPUFREQ,
            note = "Online CPUs in this policy. Normalize as a CPU list; it is not a count of the policy's cores.",
        ),
        hint(
            id = "cpu.policy.transition_latency", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu/cpufreq",
            attribute = "cpuinfo_transition_latency", unit = AtlasUnit.UNKNOWN, childPrefix = "policy", group = CPUFREQ,
            note = "Nanoseconds by documentation; the unit token is not read from the device, so it stays raw.",
        ),

        // ---- CPU topology: whole-device masks ------------------------------------------------------
        hint(
            id = "cpu.topology.possible", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu",
            attribute = "possible", unit = AtlasUnit.UNKNOWN, group = CPU_TOPOLOGY,
            note = "A CPU list, not a number: kernels express it as ranges. Never parsed by magnitude.",
        ),
        hint(
            id = "cpu.topology.present", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu",
            attribute = "present", unit = AtlasUnit.UNKNOWN, group = CPU_TOPOLOGY,
            note = "A CPU list. Present on a device with offline cores, which is the normal case.",
        ),
        hint(
            id = "cpu.topology.kernel_max", domain = AtlasDomain.CPU, root = "/sys/devices/system/cpu",
            attribute = "kernel_max", unit = AtlasUnit.COUNT, group = CPU_TOPOLOGY,
            note = "The highest index the kernel can address, not the number of cores on this device.",
        ),

        // ---- GPU through devfreq -------------------------------------------------------------------
        hint(
            id = "gpu.devfreq.max_freq", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "max_freq", unit = AtlasUnit.HERTZ, group = DEVFREQ,
            note = "Hz. The bound this domain reports; it is not a promise that the device can hold it.",
        ),
        hint(
            id = "gpu.devfreq.min_freq", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "min_freq", unit = AtlasUnit.HERTZ, group = DEVFREQ,
            note = "Hz. A floor as configured by the driver or a user-space service.",
        ),
        hint(
            id = "gpu.devfreq.governor", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "governor", unit = AtlasUnit.UNKNOWN, group = DEVFREQ,
            note = "Free text. The vendor may ship a governor mainline does not have.",
        ),
        hint(
            id = "gpu.devfreq.available_governors", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "available_governors", unit = AtlasUnit.UNKNOWN, group = DEVFREQ,
            note = "Free text list of what the driver offers right now.",
        ),
        hint(
            id = "gpu.devfreq.load", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "load", unit = AtlasUnit.UNKNOWN, group = DEVFREQ,
            note = "Dimensionless by documentation and vendor-redefined in practice: reported raw, never as a percentage.",
        ),
        hint(
            id = "gpu.devfreq.utilization", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "utilization", unit = AtlasUnit.UNKNOWN, group = DEVFREQ,
            note = "Only some drivers export it. Reported raw; a name is not a unit.",
        ),
        hint(
            id = "gpu.devfreq.polling_interval", domain = AtlasDomain.GPU, root = "/sys/class/devfreq",
            attribute = "polling_interval", unit = AtlasUnit.UNKNOWN, group = DEVFREQ,
            note = "Milliseconds by documentation, unknown unit here: the value is reported raw.",
        ),

        // ---- GPU: vendor class directories ---------------------------------------------------------
        hint(
            id = "gpu.kgsl.gpuclk", domain = AtlasDomain.GPU, root = "/sys/class/kgsl",
            attribute = "gpuclk", unit = AtlasUnit.HERTZ, group = VENDOR_INVENTORY,
            vendorTags = setOf("qualcomm"),
            note = "Adreno's class node. The unit is asserted by community inventory only, so this reading " +
                "stays INFERRED until a device confirms it.",
        ),
        hint(
            id = "gpu.kgsl.max_gpuclk", domain = AtlasDomain.GPU, root = "/sys/class/kgsl",
            attribute = "max_gpuclk", unit = AtlasUnit.HERTZ, group = VENDOR_INVENTORY,
            vendorTags = setOf("qualcomm"),
            note = "Adreno maximum clock as the driver reports it. Same unit caveat as gpuclk.",
        ),
        hint(
            id = "gpu.kgsl.min_gpuclk", domain = AtlasDomain.GPU, root = "/sys/class/kgsl",
            attribute = "min_gpuclk", unit = AtlasUnit.HERTZ, group = VENDOR_INVENTORY,
            vendorTags = setOf("qualcomm"),
            note = "Adreno floor as the driver reports it. Same unit caveat as gpuclk.",
        ),
        hint(
            id = "gpu.kgsl.gpubusy", domain = AtlasDomain.GPU, root = "/sys/class/kgsl",
            attribute = "gpubusy", unit = AtlasUnit.UNKNOWN, group = VENDOR_INVENTORY,
            vendorTags = setOf("qualcomm"),
            note = "Busy time counters, driver-defined window. Raw text; never converted into a load percentage.",
        ),
        hint(
            id = "gpu.kgsl.gpu_load", domain = AtlasDomain.GPU, root = "/sys/class/kgsl",
            attribute = "gpu_load", unit = AtlasUnit.UNKNOWN, group = VENDOR_INVENTORY,
            vendorTags = setOf("qualcomm"),
            note = "Present on some Adreno kernels only. A name that says load is not a measured percentage.",
        ),

        // ---- GPU: MediaTek -------------------------------------------------------------------------
        hint(
            id = "gpu.mtk.gpufreq_cur_freq", domain = AtlasDomain.GPU, root = "/proc/gpufreq",
            attribute = "gpufreq_cur_freq", unit = AtlasUnit.UNKNOWN, group = VENDOR_INVENTORY_MTK,
            vendorTags = setOf("mediatek"),
            note = "The read-only display node. The sibling command nodes that accept writes are excluded " +
                "on purpose: naming a control surface in a knowledge bank is how it ends up read as a command.",
        ),

        // ---- Thermal: zones and cooling devices ----------------------------------------------------
        hint(
            id = "thermal.zone.policy", domain = AtlasDomain.THERMAL, root = "/sys/class/thermal",
            attribute = "policy", unit = AtlasUnit.UNKNOWN, childPrefix = "thermal_zone", group = THERMAL,
            note = "Free text naming the mitigation policy. On many kernels it is write-only and the read " +
                "fails; that failure is reported as itself.",
        ),
        hint(
            id = "thermal.zone.passive", domain = AtlasDomain.THERMAL, root = "/sys/class/thermal",
            attribute = "passive", unit = AtlasUnit.UNKNOWN, childPrefix = "thermal_zone", group = THERMAL,
            note = "Present on mainline thermal zones. Reported raw: a control flag is not a temperature.",
        ),
        hint(
            id = "thermal.zone.available_policies", domain = AtlasDomain.THERMAL, root = "/sys/class/thermal",
            attribute = "available_policies", unit = AtlasUnit.UNKNOWN, childPrefix = "thermal_zone", group = THERMAL,
            note = "Free text list; absence does not mean a zone has no mitigation.",
        ),
        hint(
            id = "thermal.cooling.cur_state", domain = AtlasDomain.THERMAL, root = "/sys/class/thermal",
            attribute = "cur_state", unit = AtlasUnit.COUNT, childPrefix = "cooling_device", group = THERMAL,
            note = "The one honest throttling indicator on a locked-down device: a cooling device above " +
                "state 0 is active mitigation, and no temperature permission is needed to see it.",
        ),
        hint(
            id = "thermal.cooling.max_state", domain = AtlasDomain.THERMAL, root = "/sys/class/thermal",
            attribute = "max_state", unit = AtlasUnit.COUNT, childPrefix = "cooling_device", group = THERMAL,
            note = "The state space of this cooling device, so cur_state can be read against something.",
        ),
        hint(
            id = "thermal.cooling.type", domain = AtlasDomain.THERMAL, root = "/sys/class/thermal",
            attribute = "type", unit = AtlasUnit.UNKNOWN, childPrefix = "cooling_device", group = THERMAL,
            note = "Free text naming the actor (a CPU limit, a fan, a charger). A name is not a proof of which actor.",
        ),

        // ---- Power: the discharge-side devices -----------------------------------------------------
        hint(
            id = "power.capacity", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "capacity", unit = AtlasUnit.PERCENT, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Percent by documentation. This is the driver's own remaining-capacity figure.",
        ),
        hint(
            id = "power.temp", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "temp", unit = AtlasUnit.DECI_CELSIUS, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Tenths of a degree by the power-supply ABI, unlike a thermal zone's thousandths. " +
                "Recording the unit is what keeps the two from being compared as the same number.",
        ),
        hint(
            id = "power.status", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "status", unit = AtlasUnit.UNKNOWN, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Free text (Charging, Discharging, Full, Not charging). Never mapped by substring guess.",
        ),
        hint(
            id = "power.health", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "health", unit = AtlasUnit.UNKNOWN, childPrefix = "bat", group = POWER_SUPPLY,
            note = "The driver's opinion of the pack, distinct from any capacity ratio we compute.",
        ),
        hint(
            id = "power.technology", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "technology", unit = AtlasUnit.UNKNOWN, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Free text (Li-ion, Li-poly). Reported as the driver wrote it.",
        ),
        hint(
            id = "power.current_now", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "current_now", unit = AtlasUnit.MICRO_AMP, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Microamps. Polarity is vendor-defined, so the sign is never normalized into a direction here.",
        ),
        hint(
            id = "power.cycle_count", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "cycle_count", unit = AtlasUnit.COUNT, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Exported by some kernels only. Its absence is not a zero.",
        ),
        hint(
            id = "power.charge_counter", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "charge_counter", unit = AtlasUnit.MICRO_AMP_HOUR, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Microamp-hours. A charge, not an energy: never converted without explicit voltage semantics.",
        ),
        hint(
            id = "power.charge_full_design", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "charge_full_design", unit = AtlasUnit.MICRO_AMP_HOUR, childPrefix = "bat", group = POWER_SUPPLY,
            note = "The nameplate charge the pack was built as: the reference a wear ratio is read against.",
        ),
        hint(
            id = "power.real_capacity", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "real_capacity", unit = AtlasUnit.PERCENT, childPrefix = "bat", group = POWER_SUPPLY,
            note = "A vendor node, not a mainline one. Percent by name only, so it is reported raw.",
        ),
        hint(
            id = "power.voltage_ocv", domain = AtlasDomain.POWER, root = "/sys/class/power_supply",
            attribute = "voltage_ocv", unit = AtlasUnit.MICRO_VOLT, childPrefix = "bat", group = POWER_SUPPLY,
            note = "Open-circuit voltage, exported by some kernels. Vendor semantics: reported, not interpreted.",
        ),

        // ---- Storage: every zram device, not zram0 -------------------------------------------------
        hint(
            id = "storage.zram.mem_used_total", domain = AtlasDomain.STORAGE, root = "/sys/block",
            attribute = "mem_used_total", unit = AtlasUnit.BYTES, childPrefix = "zram", group = ZRAM,
            note = "Bytes actually held by the compressed pool. Enumerated, so a second zram device is read too.",
        ),
        hint(
            id = "storage.zram.comp_algorithm", domain = AtlasDomain.STORAGE, root = "/sys/block",
            attribute = "comp_algorithm", unit = AtlasUnit.UNKNOWN, childPrefix = "zram", group = ZRAM,
            note = "Free text with the active algorithm in brackets. Reported as written, not parsed by guess.",
        ),
        hint(
            id = "storage.zram.mm_stat", domain = AtlasDomain.STORAGE, root = "/sys/block",
            attribute = "mm_stat", unit = AtlasUnit.UNKNOWN, childPrefix = "zram", group = ZRAM,
            note = "A whitespace-separated tuple whose field order is kernel-version dependent: kept as raw text.",
        ),

        // ---- Memory pressure: the other two stall signals ------------------------------------------
        hint(
            id = "memory.psi.memory", domain = AtlasDomain.MEMORY, root = "/proc/pressure",
            attribute = "memory", unit = AtlasUnit.UNKNOWN, group = PSI,
            note = "A reviewed /proc summary, so it may use the larger read allowance. A distinct signal " +
                "from any meminfo-derived heuristic.",
        ),
        hint(
            id = "memory.psi.io", domain = AtlasDomain.MEMORY, root = "/proc/pressure",
            attribute = "io", unit = AtlasUnit.UNKNOWN, group = PSI,
            note = "Stall time waiting on I/O. Reported raw; PSI averages are not milliseconds of CPU time.",
        ),
    )

    private fun hint(
        id: String,
        domain: AtlasDomain,
        root: String,
        attribute: String,
        unit: AtlasUnit,
        group: Group,
        note: String,
        childPrefix: String? = null,
        vendorTags: Set<String> = emptySet(),
    ): AtlasCatalogEntry = AtlasCatalogEntry(
        id = id,
        domain = domain,
        provider = if (vendorTags.isEmpty()) AtlasProviderKind.GENERIC else AtlasProviderKind.VENDOR,
        vendorTags = vendorTags,
        parentRoot = root,
        attribute = attribute,
        unit = unit,
        safety = AtlasSafetyClass.READ_ONLY,
        provenance = AtlasProvenance(
            sourceId = group.sourceId,
            reference = group.reference,
            revision = group.revision,
            licenseNote = group.licenseNote,
            confidence = group.confidence,
        ),
        featureTag = null,
        note = note,
        scope = if (childPrefix != null || CHILD_SCOPED_ROOTS.contains(root)) {
            AtlasCatalogScope.CHILD_FILE
        } else {
            AtlasCatalogScope.ROOT_FILE
        },
        childPrefix = childPrefix,
    )

    /**
     * Roots whose children are *devices*, so an attribute inside them is never a file at the root.
     *
     * This list exists so a bank entry cannot be written in the wrong shape by accident: the same
     * mistake in the reviewed bank addressed twelve interfaces that cannot exist on any device, and the
     * cheapest way to stop it happening again is to make the shape a property of the root rather than a
     * field somebody has to remember.
     */
    private val CHILD_SCOPED_ROOTS: Set<String> = setOf(
        "/sys/class/devfreq",
        "/sys/class/kgsl",
    )
}
