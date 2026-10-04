/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofEngineAdapterTest {
    private val profile = SpoofProfile("p1", "test", "Xiaomi", "24129RT7CC", "rodin", "rodin_global")

    @Test fun anUnknownOrMissingEngineProducesNoRecordsAtAll() {
        for (engine in listOf(null, "", "something-else", "COPG-copy", "copg")) {
            val plan = SpoofEngineAdapter.plan(engine, profile)
            assertEquals(SpoofAdapterStatus.UNSUPPORTED_ENGINE, plan.status)
            assertTrue(plan.empty)
        }
    }
    @Test fun aKnownEngineTranslatesOnlyTheFieldsTheProfileSets() {
        val plan = SpoofEngineAdapter.plan("COPG", profile)
        assertEquals(SpoofAdapterStatus.DOCUMENTED, plan.status)
        // Order follows the engine's documented object (BRAND, DEVICE, MODEL, PRODUCT, …), not ours.
        assertEquals(listOf("BRAND=Xiaomi", "DEVICE=rodin", "MODEL=24129RT7CC", "PRODUCT=rodin_global"), plan.records)
        val full = SpoofEngineAdapter.plan("COPG", profile.copy(fingerprint = "fp-value", sdkInt = 36))
        assertEquals(6, full.records.size)
        assertTrue(full.records.contains("FINGERPRINT=fp-value"))
        assertTrue(full.records.contains("SDK_INT=36"))
    }
    @Test fun anUnsetOptionalFieldIsSkippedNeverEmptied() {
        for (record in SpoofEngineAdapter.plan("COPG", profile).records) {
            assertFalse(record.endsWith("="))
            assertFalse(record.contains("null"))
        }
    }
    @Test fun noAdapterStatusEverClaimsReadiness() {
        // The enum is the contract: adding a success/ready value must break this test on purpose.
        assertEquals(listOf("UNSUPPORTED_ENGINE", "DOCUMENTED"), SpoofAdapterStatus.values().map { it.name })
    }
    @Test fun aPlanCarriesNoPathAndNoCommand() {
        val plan = SpoofEngineAdapter.plan("COPG", profile)
        for (record in plan.records) {
            assertFalse(record.contains("/"))
            assertFalse(record.contains("\\"))
            assertFalse(record.contains(" "))
            assertFalse(record.contains(";"))
            assertFalse(record.contains("\$"))
        }
    }
    @Test fun theTranslationNeverInventsIdentityFields() {
        val plan = SpoofEngineAdapter.plan("COPG", profile.copy(fingerprint = "fp", sdkInt = 36))
        val keys = plan.records.map { it.substringBefore('=') }
        assertEquals(listOf("BRAND", "DEVICE", "MODEL", "PRODUCT", "FINGERPRINT", "SDK_INT"), keys)
        for (denied in listOf("IMEI", "IMSI", "ICCID", "SERIAL", "MAC", "ANDROID_ID")) {
            assertFalse(denied in keys)
        }
    }
}
