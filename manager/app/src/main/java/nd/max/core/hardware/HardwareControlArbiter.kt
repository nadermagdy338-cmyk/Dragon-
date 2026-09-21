package nd.max.core.hardware

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-shared arbiter. Every pending intent is journaled; the winner is
 * derived under the same OS lock used for mutation and verified publication.
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
        val restore: (String) -> Boolean,
        val baseline: String,
        val sequence: Long,
        val requestId: String,
        /**
         * حكم تلبية الطلب. `null` = تساوٍ حرفيّ (الافتراض القديم بلا تغيير).
         *
         * وُجد لأن بعض المقابض **سقف** أو **مدى** لا قيمة واحدة: مقارنتها بالتساوي
         * تُصنّف تلبيةً حقيقية فشلًا ثم تسترجع خط الأساس. انظر
         * [HardwareVerification] للتفصيل والقياس الذي أوجب هذا الحقل.
         */
        val verify: ((String, String?) -> Boolean)? = null,
    )

    data class Result(
        val key: String,
        val winner: ControlOwnership.Owner?,
        val desired: String?,
        val actual: String?,
        val applied: Boolean,
        val verified: Boolean,
        val blocked: Boolean,
        val rollbackAttempted: Boolean = false,
        val rollbackVerified: Boolean? = null,
        val error: String? = null,
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
        verify: ((String, String?) -> Boolean)? = null,
    ): Result = sharedTransaction(key, owner, token) { journal ->
        // INV-3: a knob the user locked manually is never written by an
        // automated owner. Safety/recovery stay above every user preference.
        if (ManualControlLocks.blocks(owner, key, token)) {
            return@sharedTransaction Result(
                key, journal.winner(key)?.owner, desired,
                runCatching { read() }.getOrNull(),
                applied = false, verified = false, blocked = true,
                error = "manual-lock",
            )
        }
        val liveBaseline = runCatching { read() }.getOrNull()
            ?: return@sharedTransaction Result(
                key, journal.winner(key)?.owner, desired, null, false, false, false,
                error = "baseline-unreadable",
            )
        val list = requests.getOrPut(key) { mutableListOf() }
        val existing = list.firstOrNull { it.token == token }
        val effectiveBaseline = existing?.baseline
            ?: baseline?.takeIf { it == liveBaseline }
            ?: liveBaseline
        val request = Request(
            key, owner, token, desired, apply, read, restore ?: existing?.restore ?: apply,
            effectiveBaseline, ++sequence, requestId(key, owner, token), verify,
        )
        list.removeAll { it.token == token }
        list += request
        journal.replaceToken(SharedHardwareOwnershipStore.newIntent(key, owner, token, desired, request.requestId))

        // The request is still journaled even when a higher-priority contender
        // already owns the physical knob. The caller must be told that its
        // intent is blocked rather than receiving a misleading "verified"
        // result for another owner's state. This also preserves the lower
        // intent for deterministic handoff when the winner releases.
        val winner = journal.winner(key)
        if (winner == null || winner.token != token || winner.requestId != request.requestId) {
            val actual = runCatching { read() }.getOrNull()
            return@sharedTransaction Result(
                key, winner?.owner, desired, actual,
                applied = false, verified = false, blocked = true,
                error = winner?.let { "preempted-by-${it.owner.name}" } ?: "no-winner",
            )
        }

        reconcileLocked(key, journal)
    }

    @Synchronized
    fun release(key: String, token: String, restore: Boolean = true): Result? =
        sharedTransaction(key, ControlOwnership.Owner.SYSTEM, token) { journal ->
            val list = requests[key]
            val removed = list?.firstOrNull { it.token == token }
            list?.removeAll { it.token == token }
            val wasWinner = journal.winner(key)?.token == token
            journal.removeToken(key, token)
            // Release unconditionally: a stale lease for this token (written
            // before a failed reconcile, or by another process path) would
            // otherwise leak indefinitely and keep blocking future owners.
            // ControlOwnership.release is token-guarded and returns false
            // safely when the current lease belongs to a different token.
            ControlOwnership.release(key, token)

            val localWinner = localRequestFor(journal.winner(key))
            if (localWinner != null) return@sharedTransaction reconcileLocked(key, journal)
            if (list.isNullOrEmpty()) requests.remove(key)
            if (!restore || !wasWinner || removed == null) return@sharedTransaction null
            if (journal.winner(key) != null) {
                return@sharedTransaction Result(
                    key, journal.winner(key)?.owner, journal.winner(key)?.desired,
                    runCatching { removed.read() }.getOrNull(),
                    applied = false, verified = false, blocked = true,
                    error = "handoff-awaiting-owner-process",
                )
            }

            val rollbackApplied = runCatching { removed.restore(removed.baseline) }.getOrDefault(false)
            val actual = runCatching { removed.read() }.getOrNull()
            val verified = rollbackApplied && actual == removed.baseline
            Result(
                key, journal.winner(key)?.owner, removed.baseline, actual,
                rollbackApplied, verified, false, true, verified,
                if (verified) null else "restore-not-verified",
            )
        }

    @Synchronized
    fun releaseToken(token: String, restore: Boolean = true) {
        val keys = requests.filterValues { list -> list.any { it.token == token } }.keys.toList()
        keys.forEach { release(it, token, restore) }
    }

    @Synchronized
    fun reconcileAll(): List<Result> = requests.keys.toList().mapNotNull { key ->
        sharedTransaction(key, ControlOwnership.Owner.SYSTEM, "reconcile") { journal ->
            localRequestFor(journal.winner(key))?.let { reconcileLocked(key, journal) }
        }
    }

    /** Reconciles one key after another process changed the shared winner. */
    @Synchronized
    fun reconcile(key: String): Result? =
        sharedTransaction(key, ControlOwnership.Owner.SYSTEM, "reconcile") { journal ->
            localRequestFor(journal.winner(key))?.let { reconcileLocked(key, journal) }
        }

    @Synchronized fun snapshot(): List<Request> = requests.values.flatten()

    private fun reconcileLocked(key: String, journal: SharedHardwareOwnershipStore.Journal): Result {
        val sharedWinner = journal.winner(key)
            ?: return Result(key, null, null, null, false, false, false)
        val winner = localRequestFor(sharedWinner)
        if (winner == null) {
            val local = requests[key].orEmpty().maxWithOrNull(
                compareBy<Request> { it.owner.priority }.thenBy { it.sequence }
            )
            return blockedResult(key, sharedWinner.owner, sharedWinner.desired, local?.read)
        }

        val current = runCatching { winner.read() }.getOrNull()
            ?: return failAndForget(key, winner, journal, false, null, "live-read-unavailable")
        // الطلب مُلبّى أصلًا بالمعنى لا بالحرف: لا يُكتب شيء ولا يُخفق في وجه مُلطِّف
        // حراري/طاقي فعّل سقفًا أضيق من طلبنا. هذا هو المسار الذي كان يقرأ «فشل»
        // فيُعيد الكتابة والاسترجاع كل دورة انحراف بلا أثر.
        if (satisfied(winner, current)) {
            commitWinner(journal, winner)
            return Result(key, winner.owner, winner.desired, current, true, true, false)
        }

        val applied = runCatching { winner.apply(winner.desired) }.getOrDefault(false)
        val actual = runCatching { winner.read() }.getOrNull()
        if (applied && satisfied(winner, actual)) {
            commitWinner(journal, winner)
            return Result(key, winner.owner, winner.desired, actual, true, true, false)
        }
        return failAndForget(key, winner, journal, applied, actual, "apply-not-verified")
    }

    /**
     * هل تلبّى الطلب؟ الحكم يأتي من الطلب نفسه حين يحدّده ([Request.verify])، وإلا فالتساوي
     * الحرفي — وهو سلوك كل كاتب قائم لم يُمرّر حكمًا، فلا يتغيّر شيء من غير إعلان.
     */
    private fun satisfied(request: Request, actual: String?): Boolean =
        request.verify?.invoke(request.desired, actual) ?: (actual != null && actual == request.desired)

    private fun localRequestFor(intent: SharedHardwareOwnershipStore.Intent?): Request? = intent?.let { target ->
        requests[target.key].orEmpty().firstOrNull {
            it.token == target.token && it.requestId == target.requestId
        }
    }

    private fun commitWinner(journal: SharedHardwareOwnershipStore.Journal, winner: Request) {
        journal.markCommitted(winner.key, winner.token, winner.requestId)
        ControlOwnership.acquire(winner.key, winner.owner, winner.token, winner.desired)
    }

    private fun failAndForget(
        key: String,
        winner: Request,
        journal: SharedHardwareOwnershipStore.Journal,
        applied: Boolean,
        actual: String?,
        cause: String,
    ): Result {
        val rollbackApplied = runCatching { winner.restore(winner.baseline) }.getOrDefault(false)
        val rollbackActual = runCatching { winner.read() }.getOrNull()
        val rollbackVerified = rollbackApplied && rollbackActual == winner.baseline
        requests[key]?.removeAll { it.requestId == winner.requestId }
        if (requests[key].isNullOrEmpty()) requests.remove(key)
        journal.removeRequest(winner.key, winner.requestId)
        if (ControlOwnership.isOwner(key, winner.token)) ControlOwnership.release(key, winner.token)
        return Result(
            key, journal.winner(key)?.owner, winner.desired, actual, applied, false, false,
            true, rollbackVerified,
            if (rollbackVerified) "$cause-baseline-restored" else "$cause-and-rollback-failed",
        )
    }

    private fun blockedResult(
        key: String,
        owner: ControlOwnership.Owner,
        desired: String,
        read: (() -> String?)?,
    ) = Result(
        key, owner, desired, read?.let { runCatching(it).getOrNull() },
        applied = false, verified = false, blocked = true,
    )

    private fun requestId(
        key: String,
        owner: ControlOwnership.Owner,
        token: String,
    ): String = "$key|${owner.name}|$token"

    private fun <T> sharedTransaction(
        key: String,
        owner: ControlOwnership.Owner,
        token: String,
        block: (SharedHardwareOwnershipStore.Journal) -> T,
    ): T {
        check(SharedHardwareOwnershipStore.isConfigured()) {
            "shared-control-store-not-configured:$key:${owner.name}:$token"
        }
        return SharedHardwareOwnershipStore.withExclusive(block)
    }
}
