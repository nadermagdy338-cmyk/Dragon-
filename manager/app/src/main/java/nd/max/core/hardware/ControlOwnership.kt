package nd.max.core.hardware

/**
 * A tiny arbitration layer for knobs that may be touched by more than one
 * MaxManager subsystem. The arbiter does not know vendors; it only knows
 * owner priority and verified mutation callbacks.
 *
 * The ladder encodes the MAX AI priority contract:
 *   RECOVERY > SAFETY > App Profile (PER_APP) > MAX AI > Manual/Global Profile > System
 * Safety always wins over every other actor, including app profiles and the
 * AI engine itself — a thermal emergency must never be outvoted.
 *
 * Priority alone cannot express "the user set this by hand": a manual choice is
 * a baseline (GLOBAL_PROFILE), so MAX_AI would legitimately outrank it. That is
 * why a hand-applied knob is additionally recorded in [ManualControlLocks] — a
 * durable exclusion the arbiter enforces for every automated owner, while
 * SAFETY and RECOVERY stay above it.
 */
object ControlOwnership {
    enum class Owner(val priority: Int) {
        SYSTEM(0),
        GLOBAL_PROFILE(20),
        MAX_AI(40),
        PER_APP(60),
        SAFETY(80),
        RECOVERY(100)
    }

    data class Lease(
        val key: String,
        val owner: Owner,
        val ownerToken: String,
        val desired: String,
        val acquiredAt: Long,
    )

    private val leases = linkedMapOf<String, Lease>()

    @Synchronized
    fun acquire(key: String, owner: Owner, token: String, desired: String): Boolean {
        val current = leases[key]
        if (current != null && current.ownerToken != token && current.owner.priority > owner.priority) return false
        leases[key] = Lease(key, owner, token, desired, System.currentTimeMillis())
        return true
    }

    @Synchronized
    fun updateDesired(key: String, token: String, desired: String): Boolean {
        val current = leases[key] ?: return false
        if (current.ownerToken != token) return false
        leases[key] = current.copy(desired = desired)
        return true
    }

    @Synchronized
    fun release(key: String, token: String? = null): Boolean {
        val current = leases[key] ?: return false
        if (token != null && current.ownerToken != token) return false
        leases.remove(key)
        return true
    }

    @Synchronized
    fun releaseOwner(token: String) {
        leases.entries.removeIf { it.value.ownerToken == token }
    }

    @Synchronized
    fun winner(key: String): Lease? = leases[key]

    @Synchronized
    fun isOwner(key: String, token: String): Boolean = leases[key]?.ownerToken == token

    @Synchronized
    fun snapshot(): List<Lease> = leases.values.toList()
}
