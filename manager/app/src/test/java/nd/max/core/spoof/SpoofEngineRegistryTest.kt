/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import nd.max.core.atlas.AtlasCapabilityState
import nd.max.ui.util.InstalledModule
import nd.max.ui.util.ModuleInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofEngineRegistryTest {
    private fun module(id: String? = "COPG", disabled: Boolean? = false,
        removal: Boolean? = false, update: Boolean? = false) =
        InstalledModule("sample", id, "Display name", "v1.2 beta", disabled, removal, update)
    private fun derive(vararg modules: InstalledModule, complete: Boolean = true) =
        SpoofEngineRegistry.derive(ModuleInventory(complete, modules.toList()))
    private fun frame(vararg content: String) =
        listOf("MAX_MODULES_BEGIN") + content + "MAX_MODULES_END"

    @Test fun successfulEmptyInventoryProvesOnlyScopedAbsence() {
        val inventory = ModuleInventory.parse(frame(), true)
        assertTrue(inventory.complete)
        assertEquals(SpoofReadiness.NO_ENGINE, SpoofEngineRegistry.derive(inventory).readiness)
    }
    @Test fun failedListingNeverMeansNoEngine() {
        assertEquals(SpoofReadiness.SCAN_UNKNOWN, derive(complete = false).readiness)
        assertFalse(ModuleInventory.parse(frame(), false).complete)
        assertFalse(ModuleInventory.parse(emptyList(), true).complete)
    }
    @Test fun frameworkIsNotAnEngine() {
        val result = derive(module("zygisk_lsposed"))
        assertEquals(SpoofReadiness.NO_ENGINE, result.readiness)
        assertEquals(SpoofModuleRole.FRAMEWORK, result.modules.single().role)
    }
    @Test fun engineMetadataNeverClaimsReadyOrApplied() {
        val result = derive(module())
        assertEquals(SpoofReadiness.NEEDS_ADAPTER, result.readiness)
        assertEquals(AtlasCapabilityState.NEEDS_ADAPTER, result.capability)
    }
    @Test fun markerPrecedenceIsConservative() {
        assertEquals(SpoofModuleStatus.REMOVAL_PENDING, derive(module(disabled = true, removal = true)).modules.single().status)
        assertEquals(SpoofModuleStatus.DISABLED, derive(module(disabled = true, update = true)).modules.single().status)
        assertEquals(SpoofModuleStatus.UPDATE_PENDING, derive(module(update = true)).modules.single().status)
        assertEquals(SpoofReadiness.ENGINE_UNAVAILABLE, derive(module(disabled = true)).readiness)
    }
    @Test fun unknownMarkerStateDoesNotMeanEnabled() {
        assertEquals(SpoofReadiness.SCAN_UNKNOWN, derive(module(disabled = null)).readiness)
    }
    @Test fun displayNameAndSimilarIdCannotMatchEngine() {
        assertTrue(derive(module("copg-fake").copy(name = "COPG LSPosed")).modules.isEmpty())
        assertEquals(SpoofReadiness.NO_ENGINE, derive(module("copg-fake")).readiness)
    }
    @Test fun unreadableIdentityPreventsAbsenceClaim() {
        assertEquals(SpoofReadiness.SCAN_UNKNOWN, derive(module(null)).readiness)
    }
    @Test fun partialInventoryCannotClaimReadiness() {
        assertEquals(SpoofReadiness.SCAN_UNKNOWN, derive(module(), complete = false).readiness)
    }
    @Test fun parserPreservesNameAndVersionWithSpaces() {
        val result = ModuleInventory.parse(frame("MAX_MODULE_BEGIN:COPG", "id=COPG",
            "name=COPG Spoof", "version=v1.2 beta", "", "MAX_MODULE_FLAGS:0:0:1", "MAX_MODULE_END"), true)
        assertTrue(result.complete)
        assertEquals("COPG Spoof", result.modules.single().name)
        assertEquals("v1.2 beta", result.modules.single().version)
        assertEquals(true, result.modules.single().updatePending)
    }
    @Test fun truncatedAndMalformedRecordsAreUnknown() {
        for (body in listOf(
            listOf("MAX_MODULE_BEGIN:COPG"),
            listOf("MAX_MODULE_BEGIN:../COPG", "MAX_MODULE_FLAGS:0:0:0", "MAX_MODULE_END"),
            listOf("MAX_MODULE_BEGIN:COPG", "MAX_MODULE_FLAGS:0:x:0", "MAX_MODULE_END"),
            listOf("MAX_MODULE_BEGIN:COPG", "id=COPG", "id=other", "MAX_MODULE_FLAGS:0:0:0", "MAX_MODULE_END"),
            listOf("unexpected output"),
        )) assertFalse(ModuleInventory.parse(frame(*body.toTypedArray()), true).complete)
    }
    @Test fun duplicateDirectoryIsRejected() {
        val record = listOf("MAX_MODULE_BEGIN:COPG", "id=COPG", "MAX_MODULE_FLAGS:0:0:0", "MAX_MODULE_END")
        assertFalse(ModuleInventory.parse(frame(*(record + record).toTypedArray()), true).complete)
    }
}
