/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpoofCapabilityMatrixTest {
    private fun resolve(field: SpoofField = SpoofField.MODEL, scope: SpoofScope = SpoofScope.PER_APP,
        readable: Boolean? = true, module: Boolean? = true, conflict: Boolean = false) =
        SpoofCapabilityMatrix.resolve(field, scope, SpoofCapabilityEvidence(readable, module, conflict))
    @Test fun parseableConfigWithoutModuleIsNotWritable() {
        assertEquals(SpoofCapabilityState.UNAVAILABLE, resolve(module = false).state)
        assertEquals(SpoofWriteMethod.NONE, resolve(module = false).writeMethod)
    }
    @Test fun unknownEligibilityIsNotPromotedToSupported() {
        assertEquals(SpoofCapabilityState.UNKNOWN, resolve(module = null).state)
        assertEquals(SpoofCapabilityState.UNKNOWN, resolve(readable = null).state)
    }
    @Test fun perAppConflictNeedsIsolationAdapter() {
        assertEquals(SpoofCapabilityState.NEEDS_ADAPTER, resolve(conflict = true).state)
        assertEquals(SpoofWriteMethod.NONE, resolve(conflict = true).writeMethod)
    }
    @Test fun supportedConfigCarriesPreciseScopeAndRecoveryButNotProcessEvidence() {
        val perApp = resolve()
        val global = resolve(scope = SpoofScope.GLOBAL)
        assertEquals(SpoofWriteMethod.COPG_CONFIG, perApp.writeMethod)
        assertEquals(SpoofWriteMethod.COPG_VD_CONFIG, global.writeMethod)
        assertEquals(SpoofRisk.RISKY, global.risk)
        assertEquals(SpoofRollbackMethod.DURABLE_CONFIG_BASELINE, perApp.rollbackMethod)
        assertEquals(SpoofVerificationMethod.ENGINE_FILE_READBACK, perApp.verificationMethod)
        assertFalse(perApp.processCompatibilityVerified)
    }
    @Test fun identifiersAndFrameworkVersionNeverBecomeWritable() {
        for (field in listOf(SpoofField.ANDROID_ID, SpoofField.SERIAL, SpoofField.CARRIER)) {
            assertEquals(SpoofCapabilityState.NEVER_TOUCH, resolve(field).state)
            assertEquals(SpoofWriteMethod.NONE, resolve(field).writeMethod)
        }
        assertEquals(SpoofCapabilityState.READ_ONLY, resolve(SpoofField.SDK_INT).state)
    }
    @Test fun everyFieldHasAnExplicitCapabilityAndUnsupportedMethodsAreAbsent() {
        SpoofField.entries.forEach { field ->
            val result = resolve(field)
            if (result.state != SpoofCapabilityState.WRITABLE) {
                assertEquals(SpoofWriteMethod.NONE, result.writeMethod)
                assertEquals(SpoofRollbackMethod.NONE, result.rollbackMethod)
            }
            assertFalse(result.processCompatibilityVerified)
        }
    }
}
