package nd.max.core.hardware

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single mutation gate for controls that can be requested by several policy
 * layers. Requests are retained even when blocked by a higher-priority owner;
 * releasing that owner automatically exposes the next intent.
 *
 * ControlOwnership is the single source of truth for the active winner. This
 * class owns the competing intents and the verified mutation step, so policy
 * code does not need a second, divergent ownership model.
 */
@Singleton
class HardwareControlArbiter @Inject constructor() {
    data class Request(
        val key: String,
        val owner: ControlOwnership.Owner,
        val token: String,
        val desired: String,
        val apply: (String) -> Boolean,
        val read: () -> String?,
        val restore: ((String) -> Boolean)?,
        val baseline: String?,
        val sequence: Long,
    )

    data class Result(
        val key: String,
        val winner: ControlOwnership.Owner?,
        val desired: String?,
        val actual: String?,
        val applied: Boolean,
        val verified: Boolean,
        val blocked: Boolean,
    )

    private val requests = linkedMapOf<String, MutableList<Request>>()
    private var sequence = 0L

    @Synchronized
    fun submit(
        key: String,
        owner: ControlOwnership.Owner,
        token: String,
        desired: String,
        apply: (String) -> Boolean,
        read: () -> String?,
        baseline: String? = null,
        restore: ((String) -> Boolean)? = null,
    ): Result {
        val list = requests.getOrPut(key) { mutableListOf() }
        list.removeAll { it.token == token }
        val request = Request(key, owner, token, desired, apply, read, restore, baseline, ++sequence)
        list += request

        val current = ControlOwnership.winner(key)
        if (current != null && current.ownerToken != token && current.owner.priority > owner.priority) {
            return Result(key, current.owner, current.desired, runCatching { read() }.getOrNull(), false, false, true)
        }
        return reconcile(key)
    }

    @Synchronized
    fun release(key: String, token: String, restore: Boolean = true): Result? {
        val list = requests[key] ?: return null
        val removed = list.firstOrNull { it.token == token }
        if (removed == null) return null

        val wasWinner = ControlOwnership.isOwner(key, token)
        list.removeAll { it.token == token }

        if (wasWinner) ControlOwnership.release(key, token)

        if (list.isEmpty()) {
            requests.remove(key)
            if (restore && wasWinner && removed.baseline != null && removed.restore != null) {
                runCatching { removed.restore.invoke(removed.baseline) }
            }
            return null
        }

        // A lower-priority request may now become active. If the caller asked
        // for restoration, the new winner is authoritative; do not briefly
        // restore an old baseline over it.
        return reconcile(key)
    }

    @Synchronized
    fun releaseToken(token: String, restore: Boolean = true) {
        requests.keys.toList().forEach { key -> release(key, token, restore) }
    }

    @Synchronized
    fun reconcileAll(): List<Result> = requests.keys.toList().map { reconcile(it) }

    @Synchronized
    fun snapshot(): List<Request> = requests.values.flatten()

    @Synchronized
    private fun reconcile(key: String): Result {
        val contenders = requests[key].orEmpty()
        val winner = contenders.maxWithOrNull(
            compareBy<Request> { it.owner.priority }.thenBy { it.sequence }
        ) ?: return Result(key, null, null, null, false, false, false)

        val currentLease = ControlOwnership.winner(key)
        if (currentLease != null && currentLease.ownerToken != winner.token && currentLease.owner.priority > winner.owner.priority) {
            val actual = runCatching { winner.read() }.getOrNull()
            return Result(key, currentLease.owner, currentLease.desired, actual, false, false, true)
        }

        if (!ControlOwnership.acquire(key, winner.owner, winner.token, winner.desired)) {
            val lease = ControlOwnership.winner(key)
            return Result(
                key = key,
                winner = lease?.owner,
                desired = lease?.desired ?: winner.desired,
                actual = runCatching { winner.read() }.getOrNull(),
                applied = false,
                verified = false,
                blocked = lease != null && lease.ownerToken != winner.token,
            )
        }

        val current = runCatching { winner.read() }.getOrNull()
        if (current == winner.desired) {
            return Result(key, winner.owner, winner.desired, current, true, true, false)
        }

        val applied = runCatching { winner.apply(winner.desired) }.getOrDefault(false)
        val actual = runCatching { winner.read() }.getOrNull()
        return Result(
            key = key,
            winner = winner.owner,
            desired = winner.desired,
            actual = actual,
            applied = applied,
            verified = applied && actual == winner.desired,
            blocked = false,
        )
    }
}
