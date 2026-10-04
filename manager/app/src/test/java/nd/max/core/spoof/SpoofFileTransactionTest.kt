/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests real transaction policy; the callback driver does NOT verify OS locking or Android AtomicFile. */
class SpoofFileTransactionTest {
    private val engine = SpoofCopgContract.MODULE_ID
    private fun record(original: String = "A", target: String = "B", phase: SpoofRecoveryPhase = SpoofRecoveryPhase.CONFIG_VERIFIED) =
        SpoofRecoveryRecord(engine, "previous", 1, original, target, phase, "test")

    private class Fixture : SpoofFileTransaction.Io, SpoofFileTransaction.Journal, SpoofFileTransaction.Control {
        var live: String? = "A"
        var stored: SpoofRecoveryRecord? = null
        val writes = mutableListOf<String>()
        val events = mutableListOf<String>()
        var puts = 0
        var failPut = 0
        var throwPut = 0
        var corrupt = false
        var writeResult: (Int) -> Boolean = { true }
        var afterWrite: (Int) -> Unit = {}
        var beforeApply: () -> Unit = {}
        var afterControl: () -> Unit = {}
        var eligibilityFailure: SpoofEngineReason? = null
        var cachedFailure: SpoofEngineReason? = null
        var blocked = false
        var interrupted = false
        var releases = 0
        fun transaction() = SpoofFileTransaction(this, this, this)
        override fun eligibility(engine: String, requestRoot: Boolean) =
            eligibilityFailure ?: if (!requestRoot) cachedFailure else null
        override fun read(engine: String) = live
        override fun write(engine: String, text: String): Boolean {
            check(stored?.phase == SpoofRecoveryPhase.PREPARED) { "mutation-before-durable-prepare" }
            events += "write"
            writes += text
            live = text
            afterWrite(writes.size)
            return writeResult(writes.size)
        }
        override fun record(engine: String): SpoofRecoveryRecord? {
            if (corrupt) error("corrupt-private-journal")
            return stored
        }
        override fun put(record: SpoofRecoveryRecord): Boolean {
            puts++
            events += "journal-${record.phase}"
            if (puts == throwPut) error("journal-io-failure")
            if (puts == failPut) return false
            stored = record
            return true
        }
        override fun release(engine: String) { releases++ }
        override fun submit(engine: String, desired: String, apply: () -> Boolean, read: () -> String?,
            restore: () -> Boolean, verify: (String, String?) -> Boolean): SpoofFileTransaction.Result {
            val baseline = read()
            if (blocked) return SpoofFileTransaction.Result(false, false, true, baseline)
            if (baseline == null) return SpoofFileTransaction.Result(false, false, false, null)
            read() // arbiter reconcile read
            beforeApply()
            val applied = runCatching { apply() }.getOrDefault(false)
            if (interrupted) error("process-interrupted-after-mutation")
            val actual = read()
            val verified = applied && verify(desired, actual)
            val result = if (verified) SpoofFileTransaction.Result(true, true, false, actual)
                else {
                    val restored = runCatching { restore() }.getOrDefault(false)
                    SpoofFileTransaction.Result(applied, false, false, actual, true, restored && read() == baseline)
                }
            afterControl()
            return result
        }
    }

    @Test fun preparePrecedesMutationAndFinalJournalFollowsReadback() {
        val f = Fixture()
        val result = f.transaction().apply(engine, "A", "B")
        assertTrue(result.applied)
        assertEquals(listOf("journal-PREPARED", "write", "journal-CONFIG_VERIFIED"), f.events)
        assertEquals(SpoofRecoveryPhase.CONFIG_VERIFIED, f.stored?.phase)
        assertEquals(2, f.releases)
    }
    @Test fun unknownEngineRejectedBeforeIo() {
        val f = Fixture()
        assertThrows(IllegalArgumentException::class.java) { f.transaction().apply("../other", "A", "B") }
        assertEquals(0, f.puts)
        assertEquals(0, f.releases)
    }
    @Test fun unavailableEngineNeverWritesOrCreatesJournal() {
        val f = Fixture().apply { eligibilityFailure = SpoofEngineReason.ROOT_REQUIRED }
        assertEquals(SpoofEngineReason.ROOT_REQUIRED, f.transaction().apply(engine, "A", "B").reason)
        assertTrue(f.events.isEmpty())
    }
    @Test fun changedEligibilityInsideLockNeverWrites() {
        val f = Fixture().apply { cachedFailure = SpoofEngineReason.ENGINE_UNAVAILABLE }
        assertEquals(SpoofEngineReason.ENGINE_UNAVAILABLE, f.transaction().apply(engine, "A", "B").reason)
        assertTrue(f.events.isEmpty())
    }
    @Test fun corruptJournalIsNotReplaced() {
        val f = Fixture().apply { corrupt = true }
        assertEquals(SpoofEngineReason.RECOVERY_STORE_FAILED, f.transaction().apply(engine, "A", "B").reason)
        assertTrue(f.events.isEmpty())
    }
    @Test fun failedPreparePreventsAnyRootWrite() {
        val f = Fixture().apply { failPut = 1 }
        val result = f.transaction().apply(engine, "A", "B")
        assertFalse(result.attempted)
        assertEquals(SpoofEngineReason.RECOVERY_STORE_FAILED, result.reason)
        assertTrue(f.writes.isEmpty())
    }
    @Test fun blockedControlDoesNotTouchJournal() {
        val f = Fixture().apply { blocked = true }
        assertEquals(SpoofEngineReason.ARBITER_BLOCKED, f.transaction().apply(engine, "A", "B").reason)
        assertTrue(f.events.isEmpty())
        assertEquals(2, f.releases)
    }
    @Test fun stalePlanAndChangedCapturedBaselineAreRejected() {
        val stale = Fixture().apply { live = "foreign" }
        assertEquals(SpoofEngineReason.CONFIG_CHANGED, stale.transaction().apply(engine, "A", "B").reason)
        assertTrue(stale.writes.isEmpty())
        val moved = Fixture().apply { live = "old"; beforeApply = { live = "A" } }
        assertEquals(SpoofEngineReason.CONFIG_CHANGED, moved.transaction().apply(engine, "A", "B").reason)
        assertTrue(moved.writes.isEmpty())
    }
    @Test fun unreadableFileDoesNotWrite() {
        val f = Fixture().apply { live = null }
        assertEquals(SpoofEngineReason.ENGINE_CONFIG_UNREADABLE, f.transaction().apply(engine, "A", "B").reason)
        assertTrue(f.events.isEmpty())
    }
    @Test fun metadataFailureRollsBackOriginal() {
        val f = Fixture().apply { writeResult = { it != 1 } }
        val result = f.transaction().apply(engine, "A", "B")
        assertFalse(result.applied)
        assertEquals(listOf("B", "A"), f.writes)
        assertEquals(true, result.rollbackVerified)
        assertEquals(SpoofRecoveryPhase.RESTORED, f.stored?.phase)
    }
    @Test fun failedRestoreMetadataRollsBackToTargetNotHistoricalOriginal() {
        val f = Fixture().apply { live = "B"; stored = record(); writeResult = { it != 1 } }
        val result = f.transaction().restore(engine)
        assertFalse(result.applied)
        assertEquals(listOf("A", "B"), f.writes)
        assertEquals(true, result.rollbackVerified)
        assertEquals("B", f.live)
        assertEquals(SpoofRecoveryPhase.FAILED, f.stored?.phase)
    }
    @Test fun externalWriteDuringMutationIsNotClobberedByRollback() {
        val f = Fixture().apply { afterWrite = { live = "foreign" } }
        val result = f.transaction().apply(engine, "A", "B")
        assertFalse(result.applied)
        assertEquals(SpoofEngineReason.FOREIGN_PACKAGE_CONFLICT, result.reason)
        assertEquals(false, result.rollbackVerified)
        assertEquals(listOf("B"), f.writes)
        assertEquals("foreign", f.live)
        assertEquals(SpoofRecoveryPhase.CONFLICT, f.stored?.phase)
    }
    @Test fun failedRollbackLeavesRecoverableFailure() {
        val f = Fixture().apply { writeResult = { false } }
        val result = f.transaction().apply(engine, "A", "B")
        assertEquals(false, result.rollbackVerified)
        assertEquals(SpoofRecoveryPhase.FAILED, f.stored?.phase)
        assertEquals(SpoofEngineReason.RECOVERY_REQUIRED, f.transaction().apply(engine, "A", "C").reason)
    }
    @Test fun sameTargetAndLaterTargetPreserveOriginalBaseline() {
        val f = Fixture()
        assertTrue(f.transaction().apply(engine, "A", "B").applied)
        assertTrue(f.transaction().apply(engine, "B", "B").applied)
        assertEquals("A", f.stored?.original)
        assertTrue(f.transaction().apply(engine, "B", "C").applied)
        assertEquals("A", f.stored?.original)
        assertTrue(f.transaction().restore(engine).applied)
        assertEquals("A", f.live)
    }
    @Test fun failedSecondUpdateKeepsPreviousRecoveryPoint() {
        val f = Fixture().apply { live = "B"; stored = record(); writeResult = { it != 1 } }
        val previous = f.stored
        val result = f.transaction().apply(engine, "B", "C")
        assertEquals(true, result.rollbackVerified)
        assertEquals(previous, f.stored)
        assertEquals("B", f.live)
        assertTrue(f.transaction().restore(engine).applied)
        assertEquals("A", f.live)
    }
    @Test fun interruptionLeavesPreparedAndNewInstanceRequiresRecovery() {
        val f = Fixture().apply { interrupted = true }
        val result = f.transaction().apply(engine, "A", "B")
        assertTrue(result.attempted)
        assertFalse(result.applied)
        assertEquals(SpoofRecoveryPhase.PREPARED, f.stored?.phase)
        assertEquals(SpoofEngineReason.RECOVERY_REQUIRED, f.transaction().apply(engine, "B", "C").reason)
        f.interrupted = false
        assertTrue(f.transaction().restore(engine).applied)
        assertEquals("A", f.live)
        assertEquals(SpoofRecoveryPhase.RESTORED, f.stored?.phase)
    }
    @Test fun failedFinalJournalNeverReportsSuccessAndAllowsManualRecovery() {
        val f = Fixture().apply { failPut = 2 }
        val result = f.transaction().apply(engine, "A", "B")
        assertEquals(SpoofEngineReason.RECOVERY_STORE_FAILED, result.reason)
        assertFalse(result.applied)
        assertEquals("B", f.live)
        assertEquals(SpoofRecoveryPhase.PREPARED, f.stored?.phase)
        assertTrue(f.transaction().restore(engine).applied)
    }
    @Test fun externalChangeAfterVerifiedControlIsNotReportedSuccessful() {
        val f = Fixture().apply { afterControl = { live = "foreign" } }
        val result = f.transaction().apply(engine, "A", "B")
        assertFalse(result.applied)
        assertEquals(SpoofEngineReason.FOREIGN_PACKAGE_CONFLICT, result.reason)
        assertEquals(SpoofRecoveryPhase.CONFLICT, f.stored?.phase)
    }
    @Test fun unreadableFinalReadNeverCertifiesRecoveryOrRestoration() {
        val f = Fixture().apply { live = "B"; stored = record(); afterControl = { live = null } }
        assertFalse(f.transaction().restore(engine).applied)
        assertEquals(SpoofRecoveryPhase.FAILED, f.stored?.phase)
    }
    @Test fun foreignRestoreIsRejectedWithoutMutation() {
        val f = Fixture().apply { live = "foreign"; stored = record() }
        val previous = f.stored
        assertEquals(SpoofEngineReason.CONFIG_CHANGED, f.transaction().restore(engine).reason)
        assertTrue(f.writes.isEmpty())
        assertEquals(previous, f.stored)
    }
    @Test fun restoredOrMissingJournalCannotBeRestoredAgain() {
        val f = Fixture()
        assertEquals(SpoofEngineReason.RECOVERY_MISSING, f.transaction().restore(engine).reason)
        f.stored = record(phase = SpoofRecoveryPhase.RESTORED)
        assertEquals(SpoofEngineReason.RECOVERY_MISSING, f.transaction().restore(engine).reason)
        assertTrue(f.events.isEmpty())
    }
    @Test fun allUnresolvedPhasesBlockApply() {
        for (phase in listOf(SpoofRecoveryPhase.PREPARED, SpoofRecoveryPhase.FAILED, SpoofRecoveryPhase.CONFLICT)) {
            val f = Fixture().apply { stored = record(phase = phase) }
            assertEquals(SpoofEngineReason.RECOVERY_REQUIRED, f.transaction().apply(engine, "A", "C").reason)
            assertTrue(f.events.isEmpty())
        }
    }
    @Test fun unknownExternalBaselineStartsNewRecoveryPoint() {
        val f = Fixture().apply { stored = record(); live = "external" }
        assertTrue(f.transaction().apply(engine, "external", "C").applied)
        assertEquals("external", f.stored?.original)
    }
    @Test fun thrownJournalFailureIsReportedWithoutWriting() {
        val f = Fixture().apply { throwPut = 1 }
        assertEquals(SpoofEngineReason.RECOVERY_STORE_FAILED, f.transaction().apply(engine, "A", "B").reason)
        assertTrue(f.writes.isEmpty())
    }
    @Test fun finalReadOfOriginalAfterApplyDoesNotBecomeVerifiedOrRestored() {
        val f = Fixture().apply { afterControl = { live = "A" } }
        val result = f.transaction().apply(engine, "A", "B")
        assertFalse(result.applied)
        assertEquals(SpoofEngineReason.NOT_VERIFIED, result.reason)
        assertEquals(SpoofRecoveryPhase.FAILED, f.stored?.phase)
    }
    @Test fun restoreRefusesBaselineChangedBetweenArbiterCaptureAndApply() {
        val f = Fixture().apply { stored = record(); live = "B"; beforeApply = { live = "A" } }
        assertEquals(SpoofEngineReason.CONFIG_CHANGED, f.transaction().restore(engine).reason)
        assertTrue(f.writes.isEmpty())
    }
    @Test fun constructionDoesNotReadJournalRequestRootOrRepair() {
        val f = Fixture().apply { corrupt = true; stored = record(phase = SpoofRecoveryPhase.PREPARED) }
        f.transaction()
        assertTrue(f.events.isEmpty())
        assertEquals(0, f.releases)
        assertEquals("A", f.live)
    }
    @Test fun globalEngineUsesSamePolicyWithoutExecutingBootOrProperties() {
        val f = Fixture()
        assertTrue(f.transaction().apply(SpoofGlobalContract.MODULE_ID, "A", "B").applied)
        assertEquals(SpoofGlobalContract.MODULE_ID, f.stored?.engineId)
        assertTrue(f.transaction().restore(SpoofGlobalContract.MODULE_ID).applied)
        assertEquals("A", f.live)
    }
}
