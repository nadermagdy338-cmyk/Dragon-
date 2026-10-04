/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofCopgContractTest {
    private fun profile(id: String = "p1", fingerprint: String? = null, sdk: Int? = null) =
        SpoofProfile(id, "name", "Xiaomi", "24129RT7CC", "rodin", "rodin_global", fingerprint, sdk)

    private fun ready(existing: String?, workspace: SpoofWorkspace): SpoofCopgPlan =
        (SpoofCopgContract.plan(existing, workspace) as SpoofCopgPlanResult.Ready).plan

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test fun aFileWeCannotParseIsNeverOverwritten() {
        val refusal = SpoofCopgContract.plan("{ this is not json", SpoofWorkspace(listOf(profile())))
        assertEquals(SpoofCopgRefusal.CONFIG_UNPARSEABLE, (refusal as SpoofCopgPlanResult.Refused).reason)
    }

    @Test fun foreignKeysKeepTheirValuesAndTheirOrder() {
        val existing = """
            {
              "cpu_spoof": { "blacklist": [ "com.bbl.mobilebanking" ] },
              "PACKAGES_REDMAGIC_9_PRO": [ "com.supercell.brawlstars:blocked" ],
              "PACKAGES_REDMAGIC_9_PRO_DEVICE": { "BRAND": "nubia", "DEVICE": "REDMAGIC 9 Pro" }
            }
        """.trimIndent()
        val merged = parse(ready(existing, SpoofWorkspace(listOf(profile()), mapOf("com.game" to "p1"))).json)
        assertEquals(listOf("cpu_spoof", "PACKAGES_REDMAGIC_9_PRO", "PACKAGES_REDMAGIC_9_PRO_DEVICE",
            "PACKAGES_MAXMANAGER_P1", "PACKAGES_MAXMANAGER_P1_DEVICE"), merged.keys.toList())
        assertEquals("nubia",
            merged["PACKAGES_REDMAGIC_9_PRO_DEVICE"]!!.jsonObject["BRAND"]!!.jsonPrimitive.content)
        assertEquals("com.bbl.mobilebanking",
            merged["cpu_spoof"]!!.jsonObject["blacklist"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test fun ourOwnStaleKeysAreDropped() {
        val existing = """{ "PACKAGES_MAXMANAGER_OLD": [ "com.old" ], "keep": 1 }"""
        val merged = ready(existing, SpoofWorkspace(listOf(profile()), mapOf("com.game" to "p1")))
        assertTrue(merged.ownedKeys.isNotEmpty())
        assertFalse("PACKAGES_MAXMANAGER_OLD" in parse(merged.json).keys)
        assertTrue("keep" in parse(merged.json).keys)
    }

    @Test fun aProfileWithNoBoundPackagesWritesNothing() {
        val plan = ready("{}", SpoofWorkspace(listOf(profile())))
        assertTrue(plan.ownedKeys.isEmpty())
        assertEquals(emptyList<String>(), parse(plan.json).keys.toList())
    }

    @Test fun unsetOptionalFieldsAreSkippedNotEmptied() {
        val device = parse(ready(null, SpoofWorkspace(listOf(profile()), mapOf("com.game" to "p1"))).json)
            .getValue("PACKAGES_MAXMANAGER_P1_DEVICE").jsonObject
        assertEquals(listOf("BRAND", "DEVICE", "MODEL", "PRODUCT"), device.keys.toList())
        val full = parse(ready(null, SpoofWorkspace(listOf(profile(fingerprint = "Xiaomi/rodin_global/rodin:16/BP2A/123:user/release-keys")),
            mapOf("com.game" to "p1"))).json).getValue("PACKAGES_MAXMANAGER_P1_DEVICE").jsonObject
        assertEquals("Xiaomi/rodin_global/rodin:16/BP2A/123:user/release-keys", full["FINGERPRINT"]!!.jsonPrimitive.content)
        assertFalse("SDK_INT" in full)
    }

    @Test fun packagesAreBareAndSorted() {
        val workspace = SpoofWorkspace(listOf(profile()),
            linkedMapOf("com.z" to "p1", "com.a" to "p1"))
        val list = parse(ready(null, workspace).json).getValue("PACKAGES_MAXMANAGER_P1").jsonArray
        assertEquals(listOf("com.a", "com.z"), list.map { it.jsonPrimitive.content })
    }

    @Test fun twoProfilesThatNormaliseToOneKeyAreRefused() {
        val workspace = SpoofWorkspace(listOf(profile("a-b"), profile("a_b")),
            mapOf("com.one" to "a-b", "com.two" to "a_b"))
        assertEquals(SpoofCopgRefusal.KEY_COLLISION,
            (SpoofCopgContract.plan(null, workspace) as SpoofCopgPlanResult.Refused).reason)
    }

    @Test fun ownedKeysReadsOnlyOurNamespaceAndNullsOnGarbage() {
        val text = """{ "cpu_spoof": {}, "PACKAGES_MAXMANAGER_B": [], "PACKAGES_MAXMANAGER_A_DEVICE": {} }"""
        assertEquals(listOf("PACKAGES_MAXMANAGER_A_DEVICE", "PACKAGES_MAXMANAGER_B"), SpoofCopgContract.ownedKeys(text))
        // مفتاح غريب لا يُعاد أبدًا — نحن نقيس ما نملكه لا ما في الملفّ.
        assertEquals(emptyList<String>(), SpoofCopgContract.ownedKeys("{ \"keep\": 1, \"PACKAGES_OTHER\": [] }"))
        // نصٌّ لا يُحلَّل ⇒ `null` (غير مقيس) لا قائمةٌ فارغة تُقرأ «لا شيء» — فرقٌ يُخفي عطبًا.
        assertNull(SpoofCopgContract.ownedKeys("not json"))
    }

    @Test fun theSignatureIgnoresTrailingWhitespaceButNotContent() {
        assertEquals(SpoofCopgContract.signature("{\"a\":1}\n"), SpoofCopgContract.signature("{\"a\":1}"))
        assertNotEquals(SpoofCopgContract.signature("{\"a\":1}"), SpoofCopgContract.signature("{\"a\":2}"))
    }

    @Test fun anAbsentFileIsBuiltFromScratchAndStaysValidJson() {
        val plan = ready(null, SpoofWorkspace(listOf(profile()), mapOf("com.game" to "p1")))
        assertEquals(SpoofCopgContract.CONFIG_PATH, plan.configPath)
        assertEquals(plan.signature, SpoofCopgContract.signature(plan.json))
        assertTrue(parse(plan.json) is JsonObject)
        assertNull(plan.json.takeIf { it.contains("\n\n") })
        assertEquals(setOf("PACKAGES_MAXMANAGER_P1", "PACKAGES_MAXMANAGER_P1_DEVICE"), plan.ownedKeys.toSet())
    }

    @Test fun thePackageKeyIsNamespacedAndStable() {
        assertEquals("PACKAGES_MAXMANAGER_P1", SpoofCopgContract.packageKey("p1"))
        assertEquals("PACKAGES_MAXMANAGER_A_B", SpoofCopgContract.packageKey("a-b"))
        assertEquals("PACKAGES_MAXMANAGER_A_B_DEVICE", SpoofCopgContract.deviceKey("PACKAGES_MAXMANAGER_A_B"))
    }

    @Test fun aValueIsJuxtaposedAsALiteralNotACommand() {
        // The engine value is carried as JSON, so shell metacharacters stay data — never a command.
        val weird = profile().copy(brand = "a;rm -rf /")
        val json = ready(null, SpoofWorkspace(listOf(weird), mapOf("com.game" to "p1"))).json
        assertEquals("a;rm -rf /",
            parse(json).getValue("PACKAGES_MAXMANAGER_P1_DEVICE").jsonObject["BRAND"]!!.jsonPrimitive.content)
    }
}
