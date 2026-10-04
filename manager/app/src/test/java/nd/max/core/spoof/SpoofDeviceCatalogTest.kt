/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofDeviceCatalogTest {
    private val asset = File("src/main/assets/spoof/device_catalog.json")

    private fun catalog() = SpoofDeviceCatalog.parse(asset.readText())!!

    @Test fun bundledCatalogParsesAndCarriesItsSource() {
        val c = catalog()
        assertEquals(15, c.devices.size)
        assertEquals("Apache-2.0", c.license)
        assertTrue(c.sourceRef.startsWith("JSON@e1d7da6"))
    }

    @Test fun everyBundledDeviceBecomesAValidSavedProfile() {
        catalog().devices.forEach { d ->
            val p = d.toProfile()
            assertTrue(d.key, SpoofProfileValidation.valid(p))
            assertEquals(d.manufacturer, p.manufacturer)
            assertNotNull(d.key, d.androidRelease)
        }
    }

    @Test fun listIsAlphabeticalIgnoringCase() {
        val names = catalog().devices.map { it.name }
        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
    }

    @Test fun sharedFingerprintsAreFlaggedNotHidden() {
        val byName = catalog().devices.associateBy { it.name }
        assertTrue(byName.getValue("Xiaomi 13").fingerprintShared)
        assertTrue(byName.getValue("Xiaomi 13 Pro").fingerprintShared)
        assertFalse(byName.getValue("REDMAGIC 9 Pro").fingerprintShared)
    }

    @Test fun searchIgnoresCaseSpacesAndSymbols() {
        val devices = catalog().devices
        assertEquals("Galaxy Z Fold 5", SampleDeviceSearch.search(devices, "galaxy z fold5").single().name)
        assertEquals("REDMAGIC 9 Pro", SampleDeviceSearch.search(devices, "NX769J").single().name)
        assertEquals(devices.size, SampleDeviceSearch.search(devices, "  ").size)
        assertTrue(SampleDeviceSearch.search(devices, "no such phone").isEmpty())
    }

    @Test fun malformedCatalogsAreRejectedOrPartiallyKept() {
        assertNull(SpoofDeviceCatalog.parse("not json"))
        assertNull(SpoofDeviceCatalog.parse("""{"schema":"2","devices":[]}"""))
        val one = """{"schema":"1","sourceRef":"x","license":"MIT","devices":[
            {"key":"A","name":"A","brand":"b","manufacturer":"m","model":"m","device":"d","product":"p","fingerprint":"b/p/d:14/id/x:user/release-keys"},
            {"key":"BAD KEY","name":"B","brand":"b","manufacturer":"m","model":"m","device":"d","product":"p","fingerprint":"f"},
            {"key":"C","name":" padded","brand":"b","manufacturer":"m","model":"m","device":"d","product":"p","fingerprint":"f"}]}"""
        assertEquals(listOf("A"), SpoofDeviceCatalog.parse(one)!!.devices.map { it.key })
    }

    @Test fun manufacturerReachesTheCopgDeviceBlockAndSurvivesThePlan() {
        val d = catalog().devices.first { it.key == "REDMAGIC_9_PRO" }
        assertEquals("ZTE", SpoofCopgContract.deviceObject(d.toProfile())["MANUFACTURER"].toString().trim('"'))
        val noManufacturer = d.toProfile().copy(manufacturer = null)
        assertFalse(SpoofCopgContract.deviceObject(noManufacturer).containsKey("MANUFACTURER"))
        val ws = SpoofWorkspace(listOf(d.toProfile()), mapOf("com.game" to d.profileId))
        val plan = (SpoofCopgContract.plan(null, ws) as SpoofCopgPlanResult.Ready).plan
        assertTrue(plan.json.contains("\"MANUFACTURER\": \"ZTE\"") || plan.json.contains("\"MANUFACTURER\":\"ZTE\""))
    }

    @Test fun manufacturerFollowsTheBrandOnlyWhileTheBrandIsUnchanged() {
        val d = catalog().devices.first { it.key == "REDMAGIC_9_PRO" }
        val overridden = SpoofWorkspace(listOf(d.toProfile()), mapOf("com.game" to d.profileId), null,
            mapOf("com.game" to AppSpoofProfile(SpoofInheritanceMode.CUSTOM, overrides = mapOf(SpoofField.BRAND to "other"))))
        val plan = (SpoofCopgContract.plan(null, overridden) as SpoofCopgPlanResult.Ready).plan
        assertFalse(plan.json.contains("MANUFACTURER"))
    }

    @Test fun bundledLicenseCopyShipsNextToTheData() {
        val license = File("src/main/assets/spoof/device_catalog.LICENSE.txt").readText()
        assertTrue(license.contains("Apache License"))
        assertTrue(license.contains("Version 2.0"))
    }
}
