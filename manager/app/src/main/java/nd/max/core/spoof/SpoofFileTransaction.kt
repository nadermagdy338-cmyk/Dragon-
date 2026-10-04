/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import java.util.UUID

enum class SpoofEngineReason {
    OK, STORE_UNCONFIGURED, ROOT_REQUIRED, ENGINE_CONFIG_MISSING, ENGINE_CONFIG_UNREADABLE,
    CONFIG_UNPARSEABLE, KEY_COLLISION, NOTHING_BOUND, ENGINE_UNAVAILABLE, FOREIGN_PACKAGE_CONFLICT,
    UNSUPPORTED_POLICY, ARBITER_BLOCKED, ARBITER_UNAVAILABLE, WRITE_FAILED, NOT_VERIFIED,
    RECOVERY_STORE_FAILED, RECOVERY_REQUIRED, RECOVERY_MISSING, CONFIG_CHANGED, ACKNOWLEDGMENT_REQUIRED,
    GLOBAL_LAYER_CONFLICT, UNSUPPORTED_TAG,
}

/** Configuration evidence only; a successful result never certifies target-process identity. */
data class SpoofEngineWrite(
    val attempted: Boolean,
    val reason: SpoofEngineReason,
    val expected: String? = null,
    val actual: String? = null,
    val rollbackAttempted: Boolean = false,
    val rollbackVerified: Boolean? = null,
) {
    val applied: Boolean get() = attempted && reason == SpoofEngineReason.OK
}

/** IO boundaries, not another ownership system: the production Control delegates to the existing arbiter. */
internal class SpoofFileTransaction(
    private val io: Io,
    private val journal: Journal,
    private val control: Control,
) {
    interface Io {
        fun eligibility(engine: String, requestRoot: Boolean): SpoofEngineReason?
        fun read(engine: String): String?
        /** True only after atomic content write, chmod and chcon all succeed. */
        fun write(engine: String, text: String): Boolean
    }
    interface Journal {
        fun record(engine: String): SpoofRecoveryRecord?
        fun put(record: SpoofRecoveryRecord): Boolean
    }
    interface Control {
        fun release(engine: String)
        fun submit(engine: String, desired: String, apply: () -> Boolean, read: () -> String?,
            restore: () -> Boolean, verify: (String, String?) -> Boolean): Result
    }
    data class Result(val applied: Boolean, val verified: Boolean, val blocked: Boolean,
        val actual: String?, val rollbackAttempted: Boolean = false, val rollbackVerified: Boolean? = null)

    @Synchronized fun apply(engine: String, original: String, target: String): SpoofEngineWrite {
        requireEngine(engine)
        io.eligibility(engine, true)?.let { return SpoofEngineWrite(false, it) }
        val previous = try { journal.record(engine) } catch (_: Exception) {
            return SpoofEngineWrite(false, SpoofEngineReason.RECOVERY_STORE_FAILED)
        }
        if (previous?.phase in setOf(SpoofRecoveryPhase.PREPARED, SpoofRecoveryPhase.FAILED, SpoofRecoveryPhase.CONFLICT)) {
            return SpoofEngineWrite(false, SpoofEngineReason.RECOVERY_REQUIRED)
        }
        val baseline = spoofRecoveryBaseline(previous, original)
        val record = runCatching { SpoofRecoveryRecord(engine, UUID.randomUUID().toString(), System.currentTimeMillis(),
            baseline, target, SpoofRecoveryPhase.PREPARED, "prepared") }.getOrNull()
            ?: return SpoofEngineWrite(false, SpoofEngineReason.UNSUPPORTED_POLICY)
        return submit(record, undo = false, expectedLive = original, previous = previous)
    }

    /** No startup mutation: called only after explicit user confirmation. */
    @Synchronized fun restore(engine: String): SpoofEngineWrite {
        requireEngine(engine)
        io.eligibility(engine, true)?.let { return SpoofEngineWrite(false, it) }
        val record = try { journal.record(engine) } catch (_: Exception) {
            return SpoofEngineWrite(false, SpoofEngineReason.RECOVERY_STORE_FAILED)
        } ?: return SpoofEngineWrite(false, SpoofEngineReason.RECOVERY_MISSING)
        if (record.phase == SpoofRecoveryPhase.RESTORED) return SpoofEngineWrite(false, SpoofEngineReason.RECOVERY_MISSING)
        return submit(record, undo = true)
    }

    private fun submit(record: SpoofRecoveryRecord, undo: Boolean, expectedLive: String? = null,
        previous: SpoofRecoveryRecord? = null): SpoofEngineWrite {
        val engine = record.engineId
        val desiredText = if (undo) record.original else record.target
        val desired = SpoofCopgContract.signature(desiredText)
        var attempted = false
        var journalSaved = false
        var journalAttempted = false
        var metadataReady = false
        var refusal: SpoofEngineReason? = null
        var baselineText: String? = null
        var baselineCaptured = false
        val result = try {
            control.release(engine)
            control.submit(engine, desired,
                apply = {
                    val live = io.read(engine)
                    val decision = record.decision(live)
                    val allowed = if (undo) decision in setOf(SpoofRecoveryDecision.TARGET_PRESENT, SpoofRecoveryDecision.ORIGINAL_PRESENT)
                        else live != null && live.trim() == expectedLive?.trim()
                    // The arbiter baseline and the comparison must describe the same file version.
                    // External writers do not take our lock: this narrows, but cannot remove, their TOCTOU window.
                    refusal = when {
                        live == null -> SpoofEngineReason.ENGINE_CONFIG_UNREADABLE
                        !allowed || live.trim() != baselineText?.trim() -> SpoofEngineReason.CONFIG_CHANGED
                        else -> io.eligibility(engine, false)
                    }
                    if (refusal != null) false else {
                        journalAttempted = true
                        journalSaved = runCatching { journal.put(record.copy(phase = SpoofRecoveryPhase.PREPARED,
                            reason = if (undo) "restore-prepared" else "prepared")) }.getOrDefault(false)
                        if (!journalSaved) false else {
                            attempted = true
                            metadataReady = io.write(engine, desiredText)
                            metadataReady
                        }
                    }
                },
                read = {
                    val live = io.read(engine)
                    if (!baselineCaptured) { baselineText = live; baselineCaptured = true }
                    live?.let(SpoofCopgContract::signature)
                },
                restore = {
                    val live = io.read(engine)
                    if (!attempted) true
                    // Undo writes ORIGINAL, not TARGET. Either our attempted content or the
                    // captured baseline is safe; a third value must never be overwritten.
                    else if (live == null || (SpoofCopgContract.signature(live) != desired &&
                        SpoofCopgContract.signature(live) != baselineText?.let(SpoofCopgContract::signature))) false
                    else baselineText?.let { io.write(engine, it) } == true
                },
                verify = { expected, actual -> metadataReady && expected == actual },
            )
        } catch (_: Exception) { null }
        finally { runCatching { control.release(engine) } }
        // An exception may follow a real mutation. Leave PREPARED durable, never report success.
        if (result == null) return SpoofEngineWrite(attempted, SpoofEngineReason.ARBITER_UNAVAILABLE, desired)
        val live = runCatching { io.read(engine) }.getOrNull()
        val actual = live?.let(SpoofCopgContract::signature)
        val reason = when {
            result.blocked -> SpoofEngineReason.ARBITER_BLOCKED
            journalAttempted && !journalSaved -> SpoofEngineReason.RECOVERY_STORE_FAILED
            refusal != null -> refusal!!
            record.decision(live) == SpoofRecoveryDecision.EXTERNAL_CHANGE -> SpoofEngineReason.FOREIGN_PACKAGE_CONFLICT
            live == null -> SpoofEngineReason.ENGINE_CONFIG_UNREADABLE
            !result.applied -> SpoofEngineReason.WRITE_FAILED
            !result.verified || actual != desired -> SpoofEngineReason.NOT_VERIFIED
            else -> SpoofEngineReason.OK
        }
        if (journalSaved) {
            val phase = when {
                reason == SpoofEngineReason.OK && undo -> SpoofRecoveryPhase.RESTORED
                reason == SpoofEngineReason.OK -> SpoofRecoveryPhase.CONFIG_VERIFIED
                record.decision(live) == SpoofRecoveryDecision.EXTERNAL_CHANGE -> SpoofRecoveryPhase.CONFLICT
                result.rollbackVerified == true && !undo && record.decision(live) == SpoofRecoveryDecision.ORIGINAL_PRESENT -> SpoofRecoveryPhase.RESTORED
                else -> SpoofRecoveryPhase.FAILED
            }
            // Failed A→B→C rolled back to B: retain A→B only when B is actually still live.
            val finalRecord = if (!undo && result.rollbackVerified == true &&
                previous?.phase == SpoofRecoveryPhase.CONFIG_VERIFIED && actual == previous.targetSignature) previous
                else record.copy(phase = phase, reason = reason.name)
            if (!runCatching { journal.put(finalRecord) }.getOrDefault(false)) {
                return SpoofEngineWrite(attempted, SpoofEngineReason.RECOVERY_STORE_FAILED, desired, actual,
                    result.rollbackAttempted, result.rollbackVerified)
            }
        }
        return SpoofEngineWrite(attempted, reason, desired, actual, result.rollbackAttempted, result.rollbackVerified)
    }

    private fun requireEngine(engine: String) {
        require(engine in setOf(SpoofCopgContract.MODULE_ID, SpoofGlobalContract.MODULE_ID))
    }
}
