/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import nd.max.ui.util.InstalledModule
import nd.max.ui.util.ModuleInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpoofApplyBackendTest {
    private val profile = SpoofProfile("p1", "test", "brand", "model", "device", "product")
    private fun engine(version: String? = "6.8.0", disabled: Boolean? = false) =
        InstalledModule("COPG", "COPG", "COPG", version, disabled, false, false)
    private fun evaluate(
        inventory: ModuleInventory,
        consent: Boolean = true,
        barrier: Boolean = true,
        pkg: String = "com.example.game",
    ) = SpoofApplyBackend.evaluate(pkg, profile, consent, inventory, barrier)

    @Test fun aClearedPreflightIsReadyYetWritesNothing() {
        // `READY` is the pass, not a write: the pre-flight never touches the engine file.
        val result = evaluate(ModuleInventory(true, listOf(engine())))
        assertEquals(SpoofApplyReason.READY, result.reason)
        assertEquals("blocked", result.outcome)
        assertFalse(result.applied)
        assertFalse(result.verified)
        assertFalse(result.writeAttempted)
    }
    @Test fun aVersionNumberNeverDecidesAnything() {
        // Metadata (including a version string) must not be what separates ready from refused.
        for (version in listOf(null, "5.4.0", "6.8.0", "999.0", "reviewed")) {
            assertEquals(SpoofApplyReason.READY,
                evaluate(ModuleInventory(true, listOf(engine(version)))).reason)
        }
    }
    @Test fun theBarrierIsCheckedBeforeConsentAndBeforeDiscovery() {
        val inventory = ModuleInventory(true, listOf(engine()))
        assertEquals(SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED, evaluate(inventory, barrier = false).reason)
        assertEquals(SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED,
            evaluate(inventory, consent = false, barrier = false).reason)
        assertEquals(SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED,
            evaluate(ModuleInventory(false, emptyList()), barrier = false).reason)
        assertEquals(SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED,
            evaluate(ModuleInventory(true, listOf(engine(), engine().copy(directory = "other"))),
                barrier = false).reason)
    }
    @Test fun anUnknownTargetIsRefusedBeforeTheBarrier() {
        assertEquals(SpoofApplyReason.INVALID_TARGET,
            evaluate(ModuleInventory(false, emptyList()), barrier = false, pkg = "com.game:aid").reason)
    }
    @Test fun barrierAndConsentOutcomesAreStillBlockedWithNoWrite() {
        for (reason in listOf(SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED, SpoofApplyReason.CONSENT_REQUIRED)) {
            val result = evaluate(ModuleInventory(true, listOf(engine())),
                consent = reason != SpoofApplyReason.CONSENT_REQUIRED, barrier = reason != SpoofApplyReason.BARRIER_NOT_ACKNOWLEDGED)
            assertEquals(reason, result.reason)
            assertFalse(result.writeAttempted)
            assertFalse(result.applied)
            assertFalse(result.verified)
        }
    }
    @Test fun consentIsRequiredEvenWhenEngineMetadataExists() {
        assertEquals(SpoofApplyReason.CONSENT_REQUIRED,
            evaluate(ModuleInventory(true, listOf(engine())), consent = false).reason)
    }
    @Test fun failedInventoryIsNotAbsence() {
        assertEquals(SpoofApplyReason.INVENTORY_UNKNOWN, evaluate(ModuleInventory(false, emptyList())).reason)
        assertEquals(SpoofApplyReason.ENGINE_ABSENT, evaluate(ModuleInventory(true, emptyList())).reason)
    }
    @Test fun disabledOrUnknownEngineCannotBeUsed() {
        assertEquals(SpoofApplyReason.ENGINE_UNAVAILABLE, evaluate(ModuleInventory(true, listOf(engine(disabled = true)))).reason)
        assertEquals(SpoofApplyReason.INVENTORY_UNKNOWN, evaluate(ModuleInventory(true, listOf(engine(disabled = null)))).reason)
    }
    @Test fun duplicateEngineIdentitiesFailClosed() {
        assertEquals(SpoofApplyReason.ENGINE_AMBIGUOUS,
            evaluate(ModuleInventory(true, listOf(engine(), engine().copy(directory = "other")))).reason)
    }
    @Test fun missingMetadataAndFrameworkOnlyAreNotReady() {
        assertEquals(SpoofApplyReason.INVENTORY_UNKNOWN,
            evaluate(ModuleInventory(true, listOf(engine(), engine().copy(id = null, directory = "unknown")))).reason)
        assertEquals(SpoofApplyReason.ENGINE_ABSENT,
            evaluate(ModuleInventory(true, listOf(engine().copy(id = "zygisk_lsposed")))).reason)
    }
}
