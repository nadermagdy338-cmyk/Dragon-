package nd.max.core.atlas

/**
 * The `Map` stage's safety class: the reviewed **never-touch** list.
 *
 * The capability map answers six questions about a device — supported, writable, read-only, needs an
 * adapter, unavailable — and one more that outranks all of them: **must not be touched for safety**.
 * This file owns that answer. An interface listed here is refused before planning, before routing and
 * before any transaction is built, whatever route asks for it: a faster phone is not a reason to write
 * a thermal trip point, and no adapter, profile or remembered preference may override a rule below.
 *
 * Three rules shape the matching, and each one is deliberate:
 *
 * 1. **Matching is broad on purpose (fail closed).** Dangerous families are matched by *fragment*,
 *    not by an exact name, because vendors add members (`trip_point_14_hyst`, `watchdog_thresh`) that
 *    a narrow list would let through. A false refusal costs one skipped knob and names its rule; a
 *    false allowance costs the hardware protection it was meant to keep.
 * 2. **Every rule carries its reason.** "Never write this" without "because …" is a superstition that
 *    the next maintainer will delete. The reason is part of the type.
 * 3. **This is one line of defense, not the only one.** Everything still passes the route planner's
 *    evidence gate and the single mutation boundary (`HardwareRepairExecutor` → arbiter). The list
 *    exists so "obviously dangerous" never depends on a route being under-reviewed.
 *
 * Nothing here reads or writes a device. It is vocabulary plus matching, like the rest of the Map
 * stage: the verdict is about *our* decision, never a claim about the machine.
 */
sealed interface AtlasSafetyVerdict {

    /** No reviewed rule names this interface. Permission to write still comes from the route gate. */
    object Allowed : AtlasSafetyVerdict

    /** A reviewed rule names this interface. The rule id is stable; the reason is for humans. */
    data class Denied(val ruleId: String, val reason: String) : AtlasSafetyVerdict

    val denied: Boolean get() = this is Denied
}

/** One reviewed never-touch rule. A rule that matches nothing is rejected rather than kept as noise. */
data class AtlasSafetyRule(
    val id: String,
    val reason: String,
    /** Exact basenames (after the last `/` and `:`), e.g. `uevent`. */
    val deniedBasenames: Set<String> = emptySet(),
    /** Substrings refused wherever they appear in a name or key, e.g. `trip_point`. */
    val deniedNameFragments: Set<String> = emptySet(),
    /** Substrings refused wherever they appear in a path, e.g. `/sys/power`. */
    val deniedPathFragments: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "a safety rule needs an id" }
        require(reason.isNotBlank()) { "a safety rule needs a written reason" }
        require(deniedBasenames.isNotEmpty() || deniedNameFragments.isNotEmpty() || deniedPathFragments.isNotEmpty()) {
            "a safety rule that matches nothing is not a rule"
        }
    }

    fun matches(name: String, path: String): Boolean {
        // الأسماء المرشَّحة: ما سمّاه المستدعي، وآخر جزء من كلٍّ من الاسم والمسار (اسم العقدة)،
        // وبادئتها بعد فاصل المفتاح — فتُرى العقدة الممنوعة مهما جاءت من مفتاح أو مسار.
        val names = listOf(name, path).flatMap { listOf(it, it.substringAfterLast('/').substringAfterLast(':')) }
        if (names.any { it in deniedBasenames }) return true
        if (deniedNameFragments.any { fragment -> names.any { fragment in it } }) return true
        return deniedPathFragments.any { it in path || it in name }
    }
}

object AtlasSafetyPolicy {

    /**
     * The reviewed rules. Kept short and named: each entry is a decision somebody made and can
     * revisit, not a regex wall nobody can argue with.
     */
    val RULES: List<AtlasSafetyRule> = listOf(
        AtlasSafetyRule(
            id = "thermal-trips",
            reason = "thermal trip points and thermal emulation are the device's own hardware " +
                "protection; writing them can disable cooling decisions the vendor designed",
            deniedNameFragments = setOf("trip_point", "emul_temp"),
        ),
        AtlasSafetyRule(
            id = "kernel-stability",
            reason = "panic and sysrq controls can kill or hang the kernel, and there is no rollback " +
                "for a device that stopped answering",
            deniedBasenames = setOf("sysrq", "sysrq-trigger"),
            deniedNameFragments = setOf("panic"),
        ),
        AtlasSafetyRule(
            id = "watchdog",
            reason = "a mishandled watchdog is a reboot; kicking or arming it is not a performance knob",
            deniedNameFragments = setOf("watchdog"),
        ),
        AtlasSafetyRule(
            id = "power-state",
            reason = "power state nodes are suspend and hibernation entry points, not controls",
            deniedPathFragments = setOf("/sys/power"),
        ),
        AtlasSafetyRule(
            id = "uevent",
            reason = "synthetic uevents corrupt device-manager state for every component listening",
            deniedNameFragments = setOf("uevent"),
        ),
    )

    /** The verdict for one interface name (and, when known, its path). */
    fun verdictFor(name: String, path: String = name): AtlasSafetyVerdict =
        RULES.firstOrNull { it.matches(name, path) }
            ?.let { AtlasSafetyVerdict.Denied(it.id, it.reason) }
            ?: AtlasSafetyVerdict.Allowed

    /**
     * The verdict for a hardware control key (e.g. `cpu_limits:policy0`).
     *
     * Keys carry their own separators; [AtlasSafetyRule.matches] already looks past them, so the key
     * is checked as both name and path — a fragment anywhere in it is enough to refuse.
     */
    fun verdictForKey(key: String): AtlasSafetyVerdict = verdictFor(name = key, path = key)
}
