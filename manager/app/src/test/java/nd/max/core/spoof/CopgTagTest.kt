/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopgTagTest {
    private fun profile() = SpoofProfile("p1", "name", "nubia", "NX769J", "REDMAGIC 9 Pro", "NX769J")

    @Test fun readmeExamplesRoundTripExactly() {
        val samples = listOf("dnd", "dab", "kso", "nolog", "serial", "blocked", "vpn", "vpns", "hidedev", "mock",
            "cow", "aid", "gaid", "appset", "drm", "imei", "cpu=snapdragon_8_elite", "gpu=Adreno 830", "tz=Asia/Tokyo",
            "lang=en-US", "sim=Vodafone", "simx=Vodafone", "ua=Chrome Desktop", "dpi=420")
        samples.forEach { token ->
            val tag = CopgTag.parse(token)
            assertFalse(token, tag is CopgTag.Unknown)
            assertEquals(token, tag.render())
        }
    }

    @Test fun unknownAndMalformedTagsAreKeptVerbatim() {
        listOf("with_cpu", "got", "dnd=1", "cpu=", "cpu=a:b", "dpi=abc", "dpi=5", "futuretag=zz", "foo=").forEach { token ->
            val parsed = CopgPackageEntry.parse("com.game:$token")
            assertEquals("com.game:$token".substringBefore(':', ""), "com.game")
            // A token with ':' splits into two; the rest must still render without dropping anything.
            assertEquals("com.game:$token", parsed.render())
        }
        assertTrue(CopgTag.parse("with_cpu") is CopgTag.Unknown)
        assertTrue(CopgTag.parse("dpi=5") is CopgTag.Unknown)
        assertFalse(CopgTagRules.valid("with_cpu"))
        assertFalse(CopgTagRules.valid("cpu= x"))
    }

    @Test fun packageEntryParsesMultipleTags() {
        val entry = CopgPackageEntry.parse("com.supercell.brawlstars:cpu=x:dnd")
        assertEquals("com.supercell.brawlstars", entry.packageName)
        assertEquals(listOf(CopgTag.Cpu("x"), CopgTag.DoNotDisturb), entry.tags)
        assertEquals(CopgPackageEntry("com.a", emptyList()).render(), "com.a")
    }

    @Test fun conflictsAreDetected() {
        assertTrue(CopgTagRules.conflicts(listOf("cpu=a", "cpu=b")).isNotEmpty())
        assertTrue(CopgTagRules.conflicts(listOf("sim=a", "simx=a")).isNotEmpty())
        assertTrue(CopgTagRules.conflicts(listOf("vpn", "vpns")).isNotEmpty())
        assertTrue(CopgTagRules.conflicts(listOf("cpu=a", "blocked")).isNotEmpty())
        assertTrue(CopgTagRules.conflicts(listOf("dnd", "kso", "cpu=a", "vpn")).isEmpty())
    }

    @Test fun conflictingOrUnknownTagsCannotEnterAPolicy() {
        assertTrue(runCatching { AppSpoofProfile(tags = setOf("cpu=a", "blocked")) }.isFailure)
        assertTrue(runCatching { AppSpoofProfile(tags = setOf("with_cpu")) }.isFailure)
        assertTrue(runCatching { AppSpoofProfile(tags = (1..30).map { "cpu=$it" }.toSet()) }.isFailure)
        assertEquals(setOf("dnd"), AppSpoofProfile(tags = setOf("dnd")).tags)
    }

    @Test fun unknownVersionAllowsOnlyTheStableComfortTags() {
        assertEquals(setOf(CopgGrammar.COMFORT_STABLE), CopgGrammarGate.allowed(null))
        assertEquals(setOf(CopgGrammar.COMFORT_STABLE), CopgGrammarGate.allowed("6.7.9"))
        assertEquals(setOf(CopgGrammar.COMFORT_STABLE), CopgGrammarGate.allowed("garbage"))
        assertEquals(CopgGrammar.entries.toSet(), CopgGrammarGate.allowed("6.8.0"))
        assertEquals(CopgGrammar.entries.toSet(), CopgGrammarGate.allowed("v6.8.0"))
        assertEquals(CopgGrammar.entries.toSet(), CopgGrammarGate.allowed("7.0.0"))
        assertEquals(CopgGrammar.entries.toSet(), CopgGrammarGate.allowed("6.10.1"))
    }

    @Test fun moduleVersionComesFromModulePropOrNothing() {
        assertEquals("6.8.0", SpoofCopgContract.moduleVersion("id=COPG\nversion=6.8.0\nversionCode=680"))
        assertNull(SpoofCopgContract.moduleVersion("id=COPG\nversionCode=680"))
        assertNull(SpoofCopgContract.moduleVersion(null))
    }

    private fun workspace(tags: Set<String>) = SpoofWorkspace(
        listOf(profile()), mapOf("com.game" to "p1"), null,
        mapOf("com.game" to AppSpoofProfile(SpoofInheritanceMode.CUSTOM, tags = tags)),
    )

    @Test fun noTagsKeepsTheOldOutputByteForByte() {
        val plain = SpoofWorkspace(listOf(profile()), mapOf("com.game" to "p1"))
        val withEmptyPolicy = workspace(emptySet())
        val a = (SpoofCopgContract.plan(null, plain) as SpoofCopgPlanResult.Ready).plan.json
        val b = (SpoofCopgContract.plan(null, withEmptyPolicy) as SpoofCopgPlanResult.Ready).plan.json
        assertEquals(a, b)
    }

    @Test fun comfortTagsAreWrittenOnAnyVersionAndSortedDeterministically() {
        val plan = (SpoofCopgContract.plan(null, workspace(setOf("kso", "dnd"))) as SpoofCopgPlanResult.Ready).plan
        val array = Json.parseToJsonElement(plan.json).jsonObject.getValue("PACKAGES_MAXMANAGER_P1").jsonArray
        assertEquals("com.game:dnd:kso", array.single().jsonPrimitive.content)
    }

    @Test fun newerTagsAreRefusedWithoutAVerifiedVersion() {
        val refused = SpoofCopgContract.plan(null, workspace(setOf("cpu=x")), moduleVersion = null)
        assertEquals(SpoofCopgRefusal.UNSUPPORTED_TAG, (refused as SpoofCopgPlanResult.Refused).reason)
        val old = SpoofCopgContract.plan(null, workspace(setOf("gpu=Adreno 830")), moduleVersion = "6.2.0")
        assertEquals(SpoofCopgRefusal.UNSUPPORTED_TAG, (old as SpoofCopgPlanResult.Refused).reason)
    }

    @Test fun newerTagsAreWrittenForAVerifiedVersion() {
        val plan = (SpoofCopgContract.plan(null, workspace(setOf("cpu=x", "dnd")), moduleVersion = "6.8.0") as SpoofCopgPlanResult.Ready).plan
        val array = Json.parseToJsonElement(plan.json).jsonObject.getValue("PACKAGES_MAXMANAGER_P1").jsonArray
        assertEquals("com.game:cpu=x:dnd", array.single().jsonPrimitive.content)
    }

    @Test fun tagsSurviveTheWorkspaceCodec() {
        val data = workspace(setOf("dnd", "cpu=x"))
        val decoded = SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(data))
        assertEquals(data, decoded)
        assertEquals(setOf("dnd", "cpu=x"), decoded.appPolicy("com.game").tags)
    }

    @Test fun olderSchemasCannotCarryTags() {
        val encoded = SpoofWorkspaceCodec.encode(workspace(setOf("dnd")))
        val downgraded = encoded.replace("MAXMANAGER_SPOOF\t4", "MAXMANAGER_SPOOF\t3")
        assertTrue(runCatching { SpoofWorkspaceCodec.decode(downgraded) }.isFailure)
    }

    @Test fun disabledAppsGetNoTags() {
        val data = SpoofWorkspace(listOf(profile()), mapOf("com.game" to "p1"), null,
            mapOf("com.game" to AppSpoofProfile(SpoofInheritanceMode.DISABLED, tags = setOf("dnd"))))
        // CUSTOM requires a binding; DISABLED does not write the app at all.
        val plan = (SpoofCopgContract.plan(null, data) as SpoofCopgPlanResult.Ready).plan
        assertFalse(plan.json.contains("com.game"))
    }
}
