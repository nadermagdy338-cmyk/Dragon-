package nd.max.core.hardware

import nd.max.core.atlas.AtlasStoreIo
import org.json.JSONObject

/** What the last attempt of one route did. Three facts, not two: see the class documentation. */
enum class AtlasRouteOutcome {
    /** The write was read back unchanged for the bounded confirmation window. */
    VERIFIED,

    /** The attempt did not take, but the baseline was restored. Retrying another route is safe. */
    FAILED,

    /**
     * The attempt did not take **and** the baseline could not be confirmed.
     *
     * This is deliberately not folded into [FAILED]: a route that failed cleanly teaches us nothing
     * about the device's safety, while a route that left an unknown state must not be tried again in
     * the same boot generation.
     */
    ROLLBACK_FAILED,
}

/** One route's remembered state, for one target, in one boot/privilege generation. */
data class AtlasRouteMemoryEntry(
    val target: String,
    val routeId: String,
    val lastOutcome: AtlasRouteOutcome,
    val lastVerifiedElapsedMs: Long?,
    val bootGeneration: Long,
    val privilegeGeneration: Long,
    val successes: Int,
    val failures: Int,
    val rollbackFailures: Int,
) {
    /** Whether this route may still be attempted in the generation the entry was recorded in. */
    fun isQuarantinedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
        rollbackFailures > 0 &&
            this.bootGeneration == bootGeneration &&
            this.privilegeGeneration == privilegeGeneration

    fun isVerifiedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
        lastOutcome == AtlasRouteOutcome.VERIFIED &&
            successes > 0 &&
            this.bootGeneration == bootGeneration &&
            this.privilegeGeneration == privilegeGeneration
}

/**
 * Learns which route actually worked on **this** device, and remembers which routes must not be
 * retried.
 *
 * The point is that Atlas does not rediscover the same device every scan: once a route has been
 * verified on this phone, the next attempt can prefer it. Three rules keep that from becoming a
 * liability:
 *
 * 1. **Learning never overrides safety ordering.** The stored preference only breaks ties *inside* one
 *    transport tier (`PLATFORM_HINT` before `VENDOR_BRIDGE` before `ROOT_DAEMON` before
 *    `ARBITER_SYSFS`). A remembered sysfs route can never outrank a platform route.
 * 2. **A rollback failure is remembered as such, and expires with the boot.** Within one boot the
 *    route is quarantined, because the device was left in an unknown state. After a reboot the
 *    evidence is void — the vendor's own state was rebuilt — so it is retried rather than banned
 *    forever.
 * 3. **Nothing here is a claim about a device.** An entry records what *we* observed; it carries no
 *    value, no unit and no capability, so it cannot be promoted into a measurement.
 *
 * The store is bounded and self-validating: an entry this build cannot read is deleted and treated as
 * absent, never half-trusted.
 */
class AtlasRouteMemory(
    private val io: AtlasStoreIo,
    private val clockMs: () -> Long,
    private val bootGeneration: () -> Long = { 0L },
    private val privilegeGeneration: () -> Long = { 0L },
    private val maxEntries: Int = MAX_ENTRIES,
) {

    init {
        require(maxEntries > 0) { "the route memory bound must be positive" }
    }

    /** The last route verified on this device for [target], or `null` when there is none. */
    fun preferredRoute(target: String): String? = entries()
        .filter { it.target == target && it.isVerifiedIn(bootGeneration(), privilegeGeneration()) }
        .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }
        ?.routeId

    /** Routes that must not be attempted again in this boot generation. */
    fun quarantinedRoutes(target: String): Set<String> = entries()
        .filter { it.target == target && it.isQuarantinedIn(bootGeneration(), privilegeGeneration()) }
        .map { it.routeId }
        .toSet()

    fun noteVerified(target: String, routeId: String) {
        val entry = load(target, routeId) ?: newEntry(target, routeId)
        store(copy(entry, AtlasRouteOutcome.VERIFIED, successes = entry.successes + 1))
    }

    /**
     * Records a failed attempt. [rollbackVerified] is required rather than optional because "we could
     * not restore the baseline" is the one failure that changes what may be attempted next.
     */
    fun noteFailed(target: String, routeId: String, rollbackVerified: Boolean) {
        val entry = load(target, routeId) ?: newEntry(target, routeId)
        store(
            copy(
                entry,
                if (rollbackVerified) AtlasRouteOutcome.FAILED else AtlasRouteOutcome.ROLLBACK_FAILED,
                failures = entry.failures + 1,
                rollbackFailures = entry.rollbackFailures + if (rollbackVerified) 0 else 1,
            ),
        )
    }

    /** Drops one target's memory. Used when a device config change invalidates what was learned. */
    fun forget(target: String): Int = io.list().count { name ->
        name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX) && name.contains(slug(target)) && io.delete(name)
    }

    fun clear(): Int = io.list().count { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) && io.delete(it) }

    fun entries(): List<AtlasRouteMemoryEntry> = io.list()
        .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
        .mapNotNull { name -> io.read(name)?.let(::decode) }
        .sortedWith(compareBy({ it.target }, { it.routeId }))

    // ---- helpers -----------------------------------------------------------------------------------

    private fun newEntry(target: String, routeId: String) = AtlasRouteMemoryEntry(
        target = target,
        routeId = routeId,
        lastOutcome = AtlasRouteOutcome.FAILED,
        lastVerifiedElapsedMs = null,
        bootGeneration = bootGeneration(),
        privilegeGeneration = privilegeGeneration(),
        successes = 0,
        failures = 0,
        rollbackFailures = 0,
    )

    private fun copy(
        entry: AtlasRouteMemoryEntry,
        outcome: AtlasRouteOutcome,
        successes: Int = entry.successes,
        failures: Int = entry.failures,
        rollbackFailures: Int = entry.rollbackFailures,
    ) = entry.copy(
        lastOutcome = outcome,
        lastVerifiedElapsedMs =
            if (outcome == AtlasRouteOutcome.VERIFIED) clockMs() else entry.lastVerifiedElapsedMs,
        // The generations are refreshed on every write: the record always describes the context it was
        // last observed in, which is what makes the boot/privilege checks meaningful.
        bootGeneration = bootGeneration(),
        privilegeGeneration = privilegeGeneration(),
        successes = successes,
        failures = failures,
        rollbackFailures = rollbackFailures,
    )

    private fun load(target: String, routeId: String): AtlasRouteMemoryEntry? {
        val text = io.read(nameOf(target, routeId)) ?: return null
        val decoded = decode(text) ?: run {
            io.delete(nameOf(target, routeId))
            return null
        }
        return decoded.takeIf { it.target == target && it.routeId == routeId }
            ?: run {
                io.delete(nameOf(target, routeId))
                null
            }
    }

    private fun store(entry: AtlasRouteMemoryEntry) {
        pruneIfNeeded(entry.target, entry.routeId)
        io.write(nameOf(entry.target, entry.routeId), encode(entry))
    }

    /** Keeps the store bounded; the entry being written is always kept. */
    private fun pruneIfNeeded(target: String, routeId: String) {
        val names = io.list().filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
        if (names.size < maxEntries) return
        val keep = nameOf(target, routeId)
        names.filter { it != keep }.forEach { io.delete(it) }
    }

    // ---- explicit-shape serialization ---------------------------------------------------------------

    private fun encode(entry: AtlasRouteMemoryEntry): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, SCHEMA)
        root.put(KEY_TARGET, entry.target)
        root.put(KEY_ROUTE, entry.routeId)
        root.put(KEY_OUTCOME, entry.lastOutcome.name)
        root.put(KEY_VERIFIED_AT, entry.lastVerifiedElapsedMs ?: JSONObject.NULL)
        root.put(KEY_BOOT, entry.bootGeneration)
        root.put(KEY_PRIVILEGE, entry.privilegeGeneration)
        root.put(KEY_SUCCESSES, entry.successes)
        root.put(KEY_FAILURES, entry.failures)
        root.put(KEY_ROLLBACK_FAILURES, entry.rollbackFailures)
        return root.toString()
    }

    private fun decode(text: String): AtlasRouteMemoryEntry? {
        if (text.length > MAX_ENTRY_CHARS) return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optInt(KEY_SCHEMA, -1) != SCHEMA) return null
        if (!REQUIRED_KEYS.all { root.has(it) }) return null
        return runCatching {
            AtlasRouteMemoryEntry(
                target = root.getString(KEY_TARGET),
                routeId = root.getString(KEY_ROUTE),
                lastOutcome = AtlasRouteOutcome.valueOf(root.getString(KEY_OUTCOME)),
                lastVerifiedElapsedMs = if (root.isNull(KEY_VERIFIED_AT)) null else root.getLong(KEY_VERIFIED_AT),
                bootGeneration = root.getLong(KEY_BOOT),
                privilegeGeneration = root.getLong(KEY_PRIVILEGE),
                successes = root.getInt(KEY_SUCCESSES).coerceAtLeast(0),
                failures = root.getInt(KEY_FAILURES).coerceAtLeast(0),
                rollbackFailures = root.getInt(KEY_ROLLBACK_FAILURES).coerceAtLeast(0),
            )
        }.getOrNull()
    }

    private fun nameOf(target: String, routeId: String): String =
        "$NAME_PREFIX${slug(target)}$NAME_SEPARATOR${slug(routeId)}$NAME_SUFFIX"

    /** Entry names are generated here, so an unusual target cannot name a file outside this store. */
    private fun slug(value: String): String {
        val cleaned = value.lowercase().map { character ->
            if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
                character
            } else {
                '-'
            }
        }.joinToString("").trim('-', '.').replace(Regex("-{2,}"), "-")
        return cleaned.take(MAX_SLUG_CHARS).ifEmpty { "unnamed" }
    }

    companion object {
        const val SCHEMA: Int = 1

        const val NAME_PREFIX: String = "route."
        const val NAME_SUFFIX: String = ".json"
        const val NAME_SEPARATOR: String = "."

        private const val MAX_ENTRIES = 64
        private const val MAX_ENTRY_CHARS = 2 * 1024
        private const val MAX_SLUG_CHARS = 48

        private const val KEY_SCHEMA = "schema"
        private const val KEY_TARGET = "target"
        private const val KEY_ROUTE = "route"
        private const val KEY_OUTCOME = "outcome"
        private const val KEY_VERIFIED_AT = "verifiedAt"
        private const val KEY_BOOT = "boot"
        private const val KEY_PRIVILEGE = "privilege"
        private const val KEY_SUCCESSES = "successes"
        private const val KEY_FAILURES = "failures"
        private const val KEY_ROLLBACK_FAILURES = "rollbackFailures"

        private val REQUIRED_KEYS: List<String> = listOf(
            KEY_SCHEMA,
            KEY_TARGET,
            KEY_ROUTE,
            KEY_OUTCOME,
            KEY_VERIFIED_AT,
            KEY_BOOT,
            KEY_PRIVILEGE,
            KEY_SUCCESSES,
            KEY_FAILURES,
            KEY_ROLLBACK_FAILURES,
        )
    }
}
