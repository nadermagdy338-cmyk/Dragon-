package nd.max.core.hardware

/** A mutation seam for Atlas; production uses [HardwareRepairExecutor], tests can replay route outcomes. */
interface AtlasRepairPort {
    fun execute(request: HardwareRepairRequest): HardwareRepairResult
}

/** The externally meaningful state of one route transaction. */
enum class HardwareRepairState {
    /**
     * The value was written and read back unchanged for the bounded confirmation window.
     *
     * **This is a fast confirmation, not a claim of sustained stability.** The default window is three
     * samples forty milliseconds apart — roughly eighty milliseconds — and a vendor writer that
     * republishes on a seconds-long timer cannot be observed inside it. The state was called
     * `VERIFIED_STABLE` before, which promised more than the measurement supports: a caller reading
     * "stable" would stop looking exactly when the interesting failure happens later.
     *
     * Sustained verification is owned by the caller's own drift loop, which re-reads on a much coarser
     * cadence (`AppMonitor.DRIFT_CHECK_INTERVAL_MS`, ten seconds) and is the only thing that can see a
     * periodic rewrite. `DRIFT_ROLLED_BACK` below is what that class of failure looks like when it
     * happens *inside* the window.
     */
    CONFIRMED_WINDOW,
    BLOCKED,
    APPLY_FAILED_ROLLED_BACK,

    /** An external writer took the value back inside the window and the baseline was restored. */
    DRIFT_ROLLED_BACK,
    ROLLBACK_UNVERIFIED,
}

data class HardwareRepairRequest(
    val routeId: String,
    val key: String,
    val owner: ControlOwnership.Owner,
    val token: String,
    val desired: String,
    val apply: (String) -> Boolean,
    val read: () -> String?,
    val restore: (String) -> Boolean,
    val baseline: String? = null,
    /**
     * حكم تلبية الطلب — `null` يعني التساوي الحرفي.
     *
     * يُمرَّر إلى المُحكِّم **وإلى نافذة التأكيد هنا** (`request.read()` مقارنًا بالمطلوب
     * عيّنةً بعد عيّنة): لو حكم المُحكِّم بالسقف وحكمت النافذة بالتساوي، لسقط طلبٌ صحيح
     * عند أول عيّنة. فحكم واحد يُستعمل في الموضعين وإلا تناقض المساران.
     */
    val verify: ((String, String?) -> Boolean)? = null,
    val stabilitySamples: Int = 3,
    val stabilityIntervalMs: Long = 40L,
) {
    init {
        require(routeId.matches(ROUTE_ID)) { "routeId is not canonical" }
        require(key.isNotBlank() && token.isNotBlank()) { "hardware transaction identity is required" }
        require(stabilitySamples in 1..8) { "stability sample count is outside the bounded range" }
        require(stabilityIntervalMs in 0L..500L) { "stability interval is outside the bounded range" }
    }

    /** How long the read-back value must hold still before the transaction is called confirmed. */
    val confirmationWindowMs: Long get() = stabilityIntervalMs * (stabilitySamples - 1)

    companion object {
        val ROUTE_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")
    }
}

data class HardwareRepairResult(
    val routeId: String,
    val key: String,
    val state: HardwareRepairState,
    val requested: String,
    val actual: String?,
    val applied: Boolean,
    val verified: Boolean,
    val stabilitySamples: Int,
    val rollbackAttempted: Boolean,
    val rollbackVerified: Boolean?,
    val error: String? = null,
) {
    val successful: Boolean get() = state == HardwareRepairState.CONFIRMED_WINDOW
}
