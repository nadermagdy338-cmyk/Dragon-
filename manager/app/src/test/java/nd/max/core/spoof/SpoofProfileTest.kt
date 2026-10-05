/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SpoofProfileTest {
    private fun profile(id: String = "p1") = SpoofProfile(id, "نمط الجهاز", "brand", "Model 1", "device", "product")
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    private fun v1(profile: SpoofProfile): String = "MAXMANAGER_SPOOF\t1\n" +
        "P\t" + listOf(profile.id, profile.name, profile.brand, profile.model, profile.device, profile.product)
            .joinToString("\t") { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } + "\n"

    @Test fun versionedWorkspaceRoundTripsNamesAndBindings() {
        val workspace = SpoofWorkspace(listOf(profile()), mapOf("com.example.game" to "p1"))
        assertEquals(workspace, SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(workspace)))
    }
    @Test fun optionalIdentityFieldsRoundTripAndStayUnsetWhenAbsent() {
        val bare = SpoofWorkspace(listOf(profile()))
        assertEquals(bare, SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(bare)))
        val full = SpoofWorkspace(listOf(profile().copy(
            fingerprint = "Xiaomi/rodin/rodin:16/BP2A.250605.031/OS3.0:user/release-keys", sdkInt = 36)))
        assertEquals(full, SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(full)))
        assertNull(SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(bare)).profiles.single().sdkInt)
    }
    @Test fun schemaOneStillDecodesAndItsTwoNewerFieldsStayUnset() {
        val migrated = SpoofWorkspaceCodec.decode(v1(profile())).profiles.single()
        assertEquals(profile(), migrated)
        assertNull(migrated.fingerprint)
        assertNull(migrated.sdkInt)
        // A schema-1 record must carry six fields, never the schema-2 eight.
        rejects { SpoofWorkspaceCodec.decode(v1(profile()).trimEnd('\n') + "\t\n") }
        rejects { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t1\nP\t" + "QQ==\t".repeat(8) + "\n") }
    }
    @Test fun emptyWorkspaceRoundTrips() {
        assertEquals(SpoofWorkspace(), SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(SpoofWorkspace())))
    }
    @Test fun unsupportedSchemaAndForeignFormatsAreRejected() {
        rejects { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t5\n") }
        rejects { SpoofWorkspaceCodec.decode("{\"PACKAGES_THISDEVICE\":[]}") }
        rejects { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t0\n") }
    }
    @Test fun malformedAndTruncatedRecordsAreRejected() {
        val valid = SpoofWorkspaceCodec.encode(SpoofWorkspace(listOf(profile())))
        rejects { SpoofWorkspaceCodec.decode(valid.substringBeforeLast('\t')) }
        rejects { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t1\nP\t@@@@\n") }
        rejects { SpoofWorkspaceCodec.decode(valid + "unexpected\n") }
        rejects { SpoofWorkspaceCodec.decode(valid + "\n") }
    }
    @Test fun fieldLimitsAndControlsAreEnforced() {
        rejects { profile().copy(name = " ") }
        rejects { profile().copy(model = "a".repeat(121)) }
        rejects { profile().copy(brand = "a\nb") }
        rejects { profile().copy(device = " x") }
        rejects { profile().copy(id = "../../x") }
        rejects { profile().copy(fingerprint = "") }
        rejects { profile().copy(fingerprint = "line\nbreak") }
        rejects { profile().copy(sdkInt = 0) }
        rejects { profile().copy(sdkInt = 101) }
        assertEquals(120, profile().copy(model = "a".repeat(120)).model.length)
        assertEquals(36, profile().copy(sdkInt = 36).sdkInt)
    }
    @Test fun anUnparsableApiLevelInAFileIsRejected() {
        fun encoded(vararg values: String) = values.joinToString("\t") {
            Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
        val spellItOut = encoded("p1", "n", "b", "m", "d", "pr", "", "thirty-six")
        rejects { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t2\nP\t$spellItOut\n") }
        val negative = encoded("p1", "n", "b", "m", "d", "pr", "", "-1")
        rejects { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t2\nP\t$negative\n") }
    }
    @Test fun duplicateProfileIdsAreRejected() {
        rejects { SpoofWorkspace(listOf(profile(), profile())) }
    }
    @Test fun duplicateAndDanglingBindingsAreRejected() {
        rejects { SpoofWorkspace(listOf(profile()), mapOf("com.example.game" to "absent")) }
        val valid = SpoofWorkspaceCodec.encode(SpoofWorkspace(listOf(profile()), mapOf("com.example.game" to "p1")))
        val binding = valid.lines().first { it.startsWith("B\t") }
        rejects { SpoofWorkspaceCodec.decode(valid + binding + "\n") }
    }
    @Test fun packageNamesCannotCarryTagsOrCommands() {
        for (pkg in listOf("com.game:blocked", "com.game;id", "../file", "", "com..game")) {
            rejects { SpoofWorkspace(listOf(profile())).bind(pkg, "p1") }
        }
        assertTrue(SpoofWorkspace.validPackage("com.CarXTech.highWay"))
    }
    @Test fun oneBindingChangeLeavesOthersUntouched() {
        val original = SpoofWorkspace(listOf(profile(), profile("p2")), mapOf("com.a.game" to "p1", "com.b.game" to "p1"))
        val changed = original.bind("com.a.game", "p2")
        assertEquals("p1", changed.bindings["com.b.game"])
        assertEquals("p2", changed.bindings["com.a.game"])
        assertEquals(mapOf("com.b.game" to "p1"), changed.bind("com.a.game", null).bindings)
        assertEquals("p1", original.bindings["com.a.game"])
    }
    @Test fun clearingOneAppLeavesEveryOtherAppAndProfileAlone() {
        val original = SpoofWorkspace(listOf(profile(), profile("p2")),
            mapOf("com.a.game" to "p1", "com.b.game" to "p2"))
        val cleared = original.clearApp("com.a.game")
        assertEquals(mapOf("com.b.game" to "p2"), cleared.bindings)
        assertEquals(original.profiles, cleared.profiles)
        assertEquals(original.bindings, original.clearApp("com.c.game").bindings)
        rejects { original.clearApp("com.game:aid") }
    }
    @Test fun profileRemovalCascadesOnlyItsLocalBindings() {
        val original = SpoofWorkspace(listOf(profile(), profile("p2")), mapOf("com.a.game" to "p1", "com.b.game" to "p2"))
        assertEquals(mapOf("com.b.game" to "p2"), original.remove("p1").bindings)
        assertEquals(listOf(profile("p2")), original.remove("p1").profiles)
    }
    @Test fun editingAProfileKeepsItsBindingsAndId() {
        val workspace = SpoofWorkspace(listOf(profile()), mapOf("com.a.game" to "p1"))
        val changed = workspace.upsert(profile().copy(model = "new"))
        assertEquals(1, changed.profiles.size)
        assertEquals(workspace.bindings, changed.bindings)
    }
    @Test fun previewIsDeviceAgainstProposalAndDoesNotModifyEither() {
        val original = profile()
        val proposed = original.copy(model = "new")
        val preview = spoofPreview(original, proposed)
        assertEquals(listOf("BRAND", "MODEL", "DEVICE", "PRODUCT", "FINGERPRINT", "SDK_INT"), preview.map { it.field })
        assertEquals(1, preview.count { it.changed })
        assertEquals("Model 1", preview[1].current)
        assertEquals("new", preview[1].proposed)
    }
    @Test fun unsetFieldsPreviewAsNullAndAreNotCountedAsProposedValues() {
        val preview = spoofPreview(profile(), profile().copy(sdkInt = 36))
        assertNull(preview.first { it.field == "FINGERPRINT" }.proposed)
        assertNull(preview.first { it.field == "SDK_INT" }.current)
        assertEquals(1, preview.count { it.changed })
        assertEquals(1, spoofPreview(profile().copy(fingerprint = "a"), profile()).count { it.changed })
    }
    @Test fun countsAndPayloadAreBounded() {
        rejects { SpoofWorkspace((0..100).map { profile("p$it") }) }
        rejects { SpoofWorkspaceCodec.decode("x".repeat(SpoofWorkspaceCodec.MAX_BYTES + 1)) }
    }
    @Test fun addOnlyImportPreservesConflictingLocalProfilesAndLinks() {
        val local = SpoofWorkspace(listOf(profile()), mapOf("com.a.game" to "p1"))
        val incoming = SpoofWorkspace(listOf(profile().copy(model = "different"), profile("p2")),
            mapOf("com.a.game" to "p2", "com.b.game" to "p1", "com.c.game" to "p2"))
        val plan = planSpoofImport(local, incoming)
        assertEquals(profile(), plan.workspace.profiles.first { it.id == "p1" })
        assertEquals("p1", plan.workspace.bindings["com.a.game"])
        assertFalse("com.b.game" in plan.workspace.bindings)
        assertEquals("p2", plan.workspace.bindings["com.c.game"])
        assertEquals(1, plan.addedProfiles)
        assertEquals(1, plan.addedBindings)
        assertEquals(3, plan.conflicts)
    }
    @Test fun identicalImportIsIdempotent() {
        val local = SpoofWorkspace(listOf(profile()), mapOf("com.a.game" to "p1"))
        val plan = planSpoofImport(local, local)
        assertEquals(local, plan.workspace)
        assertEquals(0, plan.addedProfiles + plan.addedBindings + plan.conflicts)
    }
    @Test fun matchingProfilesAllowNewBindings() {
        val plan = planSpoofImport(SpoofWorkspace(listOf(profile())),
            SpoofWorkspace(listOf(profile()), mapOf("com.a.game" to "p1")))
        assertEquals(1, plan.addedBindings)
        assertEquals(0, plan.conflicts)
    }
    @Test fun overLimitImportCannotProducePlan() {
        val local = SpoofWorkspace((0..99).map { profile("p$it") })
        rejects { planSpoofImport(local, SpoofWorkspace(listOf(profile("other")))) }
        assertEquals(100, local.profiles.size)
    }
    @Test fun emptyImportNeverDeletesAnything() {
        val local = SpoofWorkspace(listOf(profile()), mapOf("com.a.game" to "p1"))
        assertEquals(local, planSpoofImport(local, SpoofWorkspace()).workspace)
    }

    @Test fun theFormatIsPositionalAndCarriesNoIdentifierSlot() {
        val raw = SpoofWorkspaceCodec.encode(SpoofWorkspace(listOf(profile().copy(fingerprint = "fp", sdkInt = 36)),
            mapOf("com.a.game" to "p1")))
        val records = raw.lines().drop(1).filter { it.isNotEmpty() }
        assertTrue(records.all { it.startsWith("P\t") || it.startsWith("B\t") || it.startsWith("A\t") })
        // سبعة حقول أساسية + بصمة + SDK_INT + manufacturer (‏schema 4) = تسعة، ومعها وسم السجلّ "P" عشرة.
        assertEquals(10, records.first { it.startsWith("P\t") }.split('\t').size)
        assertEquals(3, records.first { it.startsWith("B\t") }.split('\t').size)
        assertEquals("4", raw.lines().first().substringAfterLast('\t'))
        // Base64 has no underscore, and a denied identifier would need a name and a slot: there are neither.
        for (forbidden in listOf("IMEI", "IMSI", "ICCID", "ANDROID_ID", "SERIAL", "MAC", "SDK_INT", "FINGERPRINT")) {
            assertFalse("$forbidden appeared in an exported file", raw.contains(forbidden))
        }
    }
    @Test fun aConsentRecordCannotTravelInsideAnExportedWorkspace() {
        val raw = SpoofWorkspaceCodec.encode(SpoofWorkspace(listOf(profile()), mapOf("com.a.game" to "p1")))
        assertFalse(raw.contains("barrier", ignoreCase = true))
        assertFalse(raw.contains("consent", ignoreCase = true))
        assertFalse(raw.contains("ack", ignoreCase = true))
    }
}
