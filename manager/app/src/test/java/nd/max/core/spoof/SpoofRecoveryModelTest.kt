/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofRecoveryModelTest {
    private fun record(engine: String = "COPG") = SpoofRecoveryRecord(engine, "tx-1", 123,
        "{\"MODEL\":\"original\"}", "{\"MODEL\":\"target\"}", SpoofRecoveryPhase.PREPARED, "prepared")
    private fun rejects(action: () -> Unit) = assertTrue(runCatching(action).isFailure)

    @Test fun recoveryRoundTripsBothEnginesAndUnicode() {
        val records = listOf(record("COPG-VD").copy(original = "{\"name\":\"جهاز\"}"), record())
        assertEquals(records.sortedBy { it.engineId }, SpoofRecoveryCodec.decode(SpoofRecoveryCodec.encode(records)))
    }
    @Test fun emptyJournalRoundTrips() { assertEquals(emptyList<SpoofRecoveryRecord>(), SpoofRecoveryCodec.decode(SpoofRecoveryCodec.encode(emptyList()))) }
    @Test fun originalMeansWriteDidNotLandOrRollbackCompleted() {
        val row = record()
        assertEquals(SpoofRecoveryDecision.ORIGINAL_PRESENT, row.decision(row.original + "\n"))
    }
    @Test fun targetMeansAWriteMayHaveLandedEvenIfCompletionWasNotSaved() {
        val row = record()
        assertEquals(SpoofRecoveryDecision.TARGET_PRESENT, row.decision(row.target))
        assertEquals(SpoofRecoveryPhase.PREPARED, row.phase)
    }
    @Test fun unreadableIsNotAbsenceAndNeverAuthorizesRestore() {
        assertEquals(SpoofRecoveryDecision.UNREADABLE, record().decision(null))
    }
    @Test fun externalEditNeverBecomesOurTarget() {
        assertEquals(SpoofRecoveryDecision.EXTERNAL_CHANGE, record().decision("{\"MODEL\":\"external\"}"))
    }
    @Test fun malformedAndUnsupportedEnvelopeIsRejected() {
        rejects { SpoofRecoveryCodec.decode("MAX_IDENTITY_RECOVERY\t2\n") }
        rejects { SpoofRecoveryCodec.decode("1\n") }
        rejects { SpoofRecoveryCodec.decode(SpoofRecoveryCodec.encode(listOf(record())) + "\n") }
    }
    @Test fun duplicateEngineAndTooManyRecordsAreRejected() {
        rejects { SpoofRecoveryCodec.encode(listOf(record(), record())) }
        val raw = SpoofRecoveryCodec.encode(listOf(record()))
        rejects { SpoofRecoveryCodec.decode(raw + raw.lines()[1] + "\n") }
    }
    @Test fun arbitraryPathsAndUnknownEnginesAreRejected() {
        rejects { record("../../system") }
        rejects { record().copy(engineId = "Other") }
        rejects { record().copy(transactionId = "../file") }
    }
    @Test fun oversizedSensitiveContentIsRejectedBeforePersistence() {
        rejects { record().copy(target = "x".repeat(SpoofRecoveryRecord.MAX_CONFIG_BYTES + 1)) }
        rejects { SpoofRecoveryCodec.decode("x".repeat(SpoofRecoveryCodec.MAX_BYTES + 1)) }
    }
    @Test fun invalidMetadataIsRejected() {
        rejects { record().copy(createdAtMs = -1) }
        rejects { record().copy(reason = "raw secret value") }
        rejects { record().copy(original = "") }
    }
    @Test fun invalidBase64AndNoncanonicalUtf8AreRejected() {
        val raw = SpoofRecoveryCodec.encode(listOf(record()))
        rejects { SpoofRecoveryCodec.decode(raw.replace(raw.lines()[1].split('\t')[5], "@@@")) }
        rejects { SpoofRecoveryCodec.decode(raw.replace(raw.lines()[1].split('\t')[5], "/w==")) }
    }
    @Test fun stateTransitionsPreserveRecoveryContentAndNeverAddProcessVerifiedState() {
        val row = record()
        SpoofRecoveryPhase.entries.forEach { phase ->
            val changed = row.copy(phase = phase)
            assertEquals(row.originalSignature, changed.originalSignature)
            assertEquals(row.targetSignature, changed.targetSignature)
            assertEquals(changed, SpoofRecoveryCodec.decode(SpoofRecoveryCodec.encode(listOf(changed))).single())
        }
        assertFalse(SpoofRecoveryPhase.entries.any { it.name == "PROCESS_VERIFIED" })
    }
    @Test fun repeatedApplyPreservesOriginalUndoPoint() {
        val previous = record().copy(phase = SpoofRecoveryPhase.CONFIG_VERIFIED)
        assertEquals(previous.original, spoofRecoveryBaseline(previous, previous.target))
        assertEquals(previous.original, spoofRecoveryBaseline(previous, previous.target + "\n"))
    }
    @Test fun foreignEditIsTheNewBaselineAndIsNeverReplacedWithHistoricalContent() {
        val previous = record().copy(phase = SpoofRecoveryPhase.CONFIG_VERIFIED)
        assertEquals("external", spoofRecoveryBaseline(previous, "external"))
        assertEquals(previous.target, spoofRecoveryBaseline(previous.copy(phase = SpoofRecoveryPhase.RESTORED), previous.target))
    }
    @Test fun configWhitespaceAndContentAreDistinguished() {
        assertEquals(record().originalSignature, record().copy(original = record().original + "\n").originalSignature)
        assertTrue(record().originalSignature != record().targetSignature)
    }
}
