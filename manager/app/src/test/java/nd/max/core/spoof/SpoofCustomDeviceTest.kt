/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofCustomDeviceTest {
    private val valid = SpoofCustomDevice(
        name = "Pixel lab",
        brand = "google",
        manufacturer = "Google",
        model = "Pixel 9",
        device = "tokay",
        product = "tokay",
        fingerprint = "google/tokay/tokay:15/AP4A.250105.002/12345:user/release-keys",
        sdk = "35",
    )

    @Test fun validDraftBecomesAProfileThatTheSavedFileAccepts() {
        val profile = valid.toProfile("custom_abc")
        assertNotNull(profile)
        assertTrue(SpoofProfileValidation.valid(profile!!))
        assertEquals("Google", profile.manufacturer)
        assertEquals(35, profile.sdkInt)
    }

    @Test fun blankRequiredFieldIsRejected() {
        assertNull(valid.copy(name = "   ").toProfile("custom_abc"))
        assertNull(valid.copy(model = "").toProfile("custom_abc"))
    }

    @Test fun fingerprintMustMatchBrandProductAndDevice() {
        assertNull(valid.copy(device = "other").toProfile("custom_abc"))
        assertNull(valid.copy(brand = "samsung").toProfile("custom_abc"))
    }

    @Test fun optionalFieldsStayUnsetWhenBlank() {
        val profile = valid.copy(manufacturer = "", fingerprint = "", sdk = "").toProfile("custom_abc")
        assertNotNull(profile)
        assertNull(profile!!.manufacturer)
        assertNull(profile.fingerprint)
        assertNull(profile.sdkInt)
    }

    @Test fun sdkMustBeBetweenOneAndOneHundredWhenGiven() {
        assertNull(valid.copy(sdk = "0").toProfile("custom_abc"))
        assertNull(valid.copy(sdk = "abc").toProfile("custom_abc"))
        assertNull(valid.copy(sdk = "101").toProfile("custom_abc"))
    }

    @Test fun badIdIsRejectedNotThrown() {
        assertNull(valid.toProfile("bad id!"))
    }
}
