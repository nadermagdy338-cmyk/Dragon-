/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofIdentityModelTest {
    private val global = SpoofProfile("global", "Global", "google", "Pixel", "pixel", "pixel")
    private val custom = SpoofProfile("custom", "Custom", "samsung", "Galaxy", "galaxy", "galaxy")
    private val observed = mapOf(SpoofField.MODEL to "Host", SpoofField.SDK_INT to "35")
    private fun workspace() = SpoofWorkspace(listOf(global, custom), globalProfileId = global.id)
    private fun field(data: SpoofWorkspace, field: SpoofField = SpoofField.MODEL) =
        EffectiveSpoofProfileResolver.resolve(data, "com.example.app", observed).fields.first { it.field == field }
    private fun refusal(data: SpoofWorkspace, json: String = "{}") =
        (SpoofCopgContract.plan(json, data) as SpoofCopgPlanResult.Refused).reason

    @Test fun absentAppInheritsGlobalWithOriginButNoVerifiedEffective() {
        val value = field(workspace())
        assertEquals("Pixel", value.target)
        assertEquals(SpoofSource.GLOBAL_PROFILE, value.source)
        assertEquals("Host", value.observed)
        assertNull(value.verifiedEffective)
    }
    @Test fun selectedCustomWinsAndAbsentFieldsFallBack() {
        val value = workspace().bind("com.example.app", custom.id)
        assertEquals("Galaxy", field(value).target)
        assertEquals(SpoofSource.APP_PROFILE, field(value).source)
        assertEquals("35", field(value, SpoofField.SDK_INT).target)
        assertEquals(SpoofSource.HOST_OBSERVATION, field(value, SpoofField.SDK_INT).source)
    }
    @Test fun fieldOverrideWinsWithoutChangingOtherFields() {
        val data = workspace().bind("com.example.app", custom.id).setAppPolicy("com.example.app",
            AppSpoofProfile(SpoofInheritanceMode.CUSTOM, overrides = mapOf(SpoofField.MODEL to "Manual")))
        assertEquals("Manual", field(data).target)
        assertEquals(SpoofSource.APP_OVERRIDE, field(data).source)
        assertEquals("samsung", field(data, SpoofField.BRAND).target)
    }
    @Test fun cpuAndGpuOverridesAreResolvedButNotWriteableByIdentityAdapter() {
        for (surface in listOf(SpoofField.CPU_MODEL, SpoofField.GPU_MODEL)) {
            val data = workspace().bind("com.example.app", custom.id).setAppPolicy("com.example.app",
                AppSpoofProfile(SpoofInheritanceMode.CUSTOM, overrides = mapOf(surface to "custom")))
            assertEquals("custom", field(data, surface).target)
            assertEquals(SpoofCopgRefusal.UNSUPPORTED_POLICY, refusal(data))
            assertEquals(SpoofCapabilityState.NEEDS_ADAPTER, SpoofCapabilityResolver.state(surface, true))
        }
    }
    @Test fun categoryGlobalWinsOverAppFieldOverrides() {
        val data = workspace().bind("com.example.app", custom.id).setAppPolicy("com.example.app",
            AppSpoofProfile(SpoofInheritanceMode.CUSTOM, mapOf(SpoofCategory.IDENTITY to SpoofCategoryMode.GLOBAL),
                mapOf(SpoofField.MODEL to "ignored")))
        assertEquals("Pixel", field(data).target)
        assertEquals(SpoofSource.GLOBAL_PROFILE, field(data).source)
    }
    @Test fun categoryRealAndDisabledUseObservationAndRefuseGlobalIsolationPromise() {
        val real = workspace().bind("com.example.app", custom.id).setAppPolicy("com.example.app",
            AppSpoofProfile(SpoofInheritanceMode.CUSTOM, mapOf(SpoofCategory.IDENTITY to SpoofCategoryMode.REAL)))
        assertEquals("Host", field(real).target)
        assertEquals(SpoofCopgRefusal.UNSUPPORTED_POLICY, refusal(real))
        val disabled = workspace().setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.DISABLED))
        assertEquals("Host", field(disabled).target)
        assertEquals(SpoofCopgRefusal.UNSUPPORTED_POLICY, refusal(disabled))
    }
    @Test fun deletedProfileDoesNotSilentlyActivateGlobalInheritance() {
        val data = workspace().bind("com.example.app", custom.id).remove(custom.id)
        assertEquals(SpoofInheritanceMode.DISABLED, data.appPolicy("com.example.app").mode)
        assertNull(data.bindings["com.example.app"])
        assertEquals("Host", field(data).target)
        assertNull(workspace().remove(global.id).globalProfileId)
    }
    @Test fun changingGlobalUpdatesInheritedAppWithoutTouchingCustomAssignment() {
        val data = workspace().bind("com.example.app", custom.id).copy(globalProfileId = custom.id)
        assertEquals("Galaxy", field(data).target)
        val inherited = workspace().copy(globalProfileId = custom.id)
        assertEquals("Galaxy", field(inherited).target)
        assertEquals(SpoofSource.GLOBAL_PROFILE, field(inherited).source)
    }
    @Test fun versionThreeRoundTripsAllPolicyFields() {
        val data = workspace().bind("com.example.app", custom.id).setAppPolicy("com.example.app",
            AppSpoofProfile(SpoofInheritanceMode.CUSTOM, mapOf(SpoofCategory.GPU to SpoofCategoryMode.GLOBAL),
                mapOf(SpoofField.MODEL to "Manual")))
        assertEquals(data, SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(data)))
    }
    @Test fun versionTwoMigratesWithoutInventingGlobalOrOverrides() {
        val data = SpoofWorkspace(listOf(global), mapOf("com.example.app" to global.id))
        // ملفّ schema 2 **حقيقيّ**: الترويسة، وسجلّ `P` بحقوله الثمانية (‏schema 4 زاد `manufacturer` تاسعًا).
        // تبديل الترويسة وحدها يسلّم المرمِّز سجلًّا بتسعة حقول يزعم أنه schema 2 — فيرفضه بحقّ.
        val encoded = SpoofWorkspaceCodec.encode(data).lines().filter { it.isNotEmpty() }
        val old = buildString {
            append(encoded.first().substringBeforeLast('\t')).append("\t2\n")
            encoded.drop(1).forEach { line ->
                val parts = line.split('\t')
                append(if (parts.first() == "P") parts.dropLast(1).joinToString("\t") else line).append('\n')
            }
        }
        val migrated = SpoofWorkspaceCodec.decode(old)
        assertEquals(data, migrated)
        assertNull(migrated.globalProfileId)
        assertEquals(SpoofInheritanceMode.CUSTOM, migrated.appPolicy("com.example.app").mode)
        assertTrue(migrated.appPolicies.isEmpty())
    }
    @Test fun missingCustomProfileAndDuplicatePolicyRecordsAreRejected() {
        assertTrue(runCatching { workspace().setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.CUSTOM)) }.isFailure)
        val data = workspace().setAppPolicy("com.example.app", AppSpoofProfile())
        val raw = SpoofWorkspaceCodec.encode(data)
        assertTrue(runCatching { SpoofWorkspaceCodec.decode(raw + raw.lines().first { it.startsWith("A\t") } + "\n") }.isFailure)
    }
    @Test fun malformedHeaderAndUnsupportedSchemaAreRejected() {
        assertTrue(runCatching { SpoofWorkspaceCodec.decode("3\n") }.isFailure)
        assertTrue(runCatching { SpoofWorkspaceCodec.decode("MAXMANAGER_SPOOF\t5\n") }.isFailure)
    }
    @Test fun importNeverActivatesGlobalOrOverridesExistingDisabledPolicy() {
        val local = workspace().setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.DISABLED))
        val incoming = SpoofWorkspace(listOf(global, custom), mapOf("com.example.app" to custom.id), custom.id)
        val result = planSpoofImport(local, incoming).workspace
        assertEquals(global.id, result.globalProfileId)
        assertEquals(SpoofInheritanceMode.DISABLED, result.appPolicy("com.example.app").mode)
        assertNull(planSpoofImport(SpoofWorkspace(), incoming).workspace.globalProfileId)
    }
    @Test fun importDoesNotAttachAHiddenBindingToLocalGlobalOrDisabledPolicy() {
        for (mode in listOf(SpoofInheritanceMode.GLOBAL, SpoofInheritanceMode.DISABLED)) {
            val local = workspace().setAppPolicy("com.example.app", AppSpoofProfile(mode))
            val incoming = SpoofWorkspace(listOf(custom), mapOf("com.example.app" to custom.id))
            val plan = planSpoofImport(local, incoming)
            assertNull(plan.workspace.bindings["com.example.app"])
            assertEquals(local.appPolicies, plan.workspace.appPolicies)
            assertEquals(0, plan.addedBindings)
            assertEquals(1, plan.conflicts)
        }
    }
    @Test fun dormantCustomBindingDoesNotCollideWithAnInheritedGlobalDevice() {
        val data = workspace().bind("com.a.app", custom.id).bind("com.b.app", custom.id)
            .setAppPolicy("com.a.app", AppSpoofProfile(SpoofInheritanceMode.GLOBAL))
        val ready = SpoofCopgContract.plan("{}", data) as SpoofCopgPlanResult.Ready
        val json = kotlinx.serialization.json.Json.parseToJsonElement(ready.plan.json)
            as kotlinx.serialization.json.JsonObject
        assertEquals("[\"com.a.app\"]", json[SpoofCopgContract.packageKey(global.id)].toString())
        assertEquals("[\"com.b.app\"]", json[SpoofCopgContract.packageKey(custom.id)].toString())
    }
    @Test fun conflictingProfileCannotSmuggleAnInheritingPolicyThroughImport() {
        val local = workspace()
        val incoming = SpoofWorkspace(listOf(custom.copy(model = "Foreign")),
            mapOf("com.example.app" to custom.id),
            appPolicies = mapOf("com.example.app" to AppSpoofProfile(SpoofInheritanceMode.GLOBAL)))
        val plan = planSpoofImport(local, incoming)
        assertNull(plan.workspace.bindings["com.example.app"])
        assertFalse("com.example.app" in plan.workspace.appPolicies)
        assertEquals(local, plan.workspace)
    }
    @Test fun importPreservesPolicyAndBindingOfAnExistingCustomApp() {
        val local = workspace().bind("com.example.app", custom.id).setAppPolicy("com.example.app",
            AppSpoofProfile(SpoofInheritanceMode.CUSTOM, overrides = mapOf(SpoofField.MODEL to "Local")))
        val incoming = workspace().bind("com.example.app", global.id)
        assertEquals(local, planSpoofImport(local, incoming).workspace)
    }
    @Test fun fingerprintsMustMatchIdentityAndStandardStructure() {
        assertTrue(SpoofProfileValidation.valid(global))
        assertFalse(SpoofProfileValidation.valid(global.copy(fingerprint = "anything")))
        assertFalse(SpoofProfileValidation.valid(global.copy(fingerprint = "wrong/pixel/pixel:15/ID/1:user/release-keys")))
        assertTrue(SpoofProfileValidation.valid(global.copy(fingerprint = "google/pixel/pixel:15/ID/1:user/release-keys")))
    }
    @Test fun foreignDeviceAndCpuPackageTagsBlockOurAssignment() {
        val data = SpoofWorkspace(listOf(custom), mapOf("com.example.app" to custom.id))
        assertEquals(SpoofCopgRefusal.FOREIGN_PACKAGE_CONFLICT, refusal(data,
            """{"PACKAGES_OTHER":["com.example.app:cow"]}"""))
        assertEquals(SpoofCopgRefusal.FOREIGN_PACKAGE_CONFLICT, refusal(data,
            """{"cpu_spoof":{"blacklist":["com.example.app"]}}"""))
    }
    @Test fun disabledWithoutGlobalRemovesOnlyOwnedAssignments() {
        val data = SpoofWorkspace(listOf(custom), mapOf("com.example.app" to custom.id))
            .setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.DISABLED))
        val result = (SpoofCopgContract.plan("""{"foreign":true,"PACKAGES_MAXMANAGER_CUSTOM":["com.example.app"]}""", data)
            as SpoofCopgPlanResult.Ready).plan
        assertTrue(result.ownedKeys.isEmpty())
        assertTrue(result.json.contains("foreign"))
    }
    @Test fun globalPlanRefusesSdkIsolationAndForeignAdvancedFields() {
        assertNull(SpoofGlobalContract.plan("{}", workspace()))
        assertNull(SpoofGlobalContract.plan("""{"COPG-VD":{"HARDWARE":"foreign"}}""", workspace()))
        assertNull(SpoofGlobalContract.plan("""{"COPG-VD":{}}""", workspace().upsert(global.copy(sdkInt = 36))))
        assertNull(SpoofGlobalContract.plan("""{"COPG-VD":{}}""",
            workspace().setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.DISABLED))))
    }
    @Test fun globalPlanPreservesOutsideKeysAndNeverWritesSdk() {
        val text = SpoofGlobalContract.plan("""{"keep":true,"COPG-VD":{"MODEL":"old"}}""", workspace())!!
        assertTrue(text.contains("keep"))
        assertTrue(text.contains("Pixel"))
        assertFalse(text.contains("SDK_INT"))
    }
    @Test fun legacySdkIsStoredButNeverWrittenByIdentityAdapter() {
        val data = SpoofWorkspace(listOf(custom.copy(sdkInt = 36)), mapOf("com.example.app" to custom.id))
        assertEquals(SpoofCopgRefusal.UNSUPPORTED_POLICY, refusal(data))
        assertEquals(data, SpoofWorkspaceCodec.decode(SpoofWorkspaceCodec.encode(data)))
    }
    @Test fun resolvedIdsCannotCollideWithUserProfileIds() {
        val a = custom.copy(id = "resolved_0")
        val data = SpoofWorkspace(listOf(global, a), mapOf("com.a.app" to global.id, "com.b.app" to a.id),
            appPolicies = mapOf("com.a.app" to AppSpoofProfile(SpoofInheritanceMode.CUSTOM,
                overrides = mapOf(SpoofField.MODEL to "Different"))))
        assertEquals(SpoofCopgRefusal.KEY_COLLISION, refusal(data))
    }
    @Test fun independentCopyDetachesOneAppWithoutTouchingTheSharedTemplate() {
        val data = workspace().bind("com.other.app", global.id)
            .copyProfileForApp("com.example.app", "copy1", "Global (copy)")
        // التطبيق صار «مخصّصًا» على نسخة مستقلّة، وقيم النسخة هي قيم القالب لحظةَ النسخ.
        assertEquals("copy1", data.bindings["com.example.app"])
        assertEquals(SpoofInheritanceMode.CUSTOM, data.appPolicy("com.example.app").mode)
        assertEquals("Pixel", field(data).target)
        assertEquals(SpoofSource.APP_PROFILE, field(data).source)
        // تطبيق آخر يبقى على القالب المشترك، والقالب نفسه لم يتغيّر: نسخة لا مرجع.
        assertEquals(global.id, data.bindings["com.other.app"])
        assertEquals(listOf(global, custom), data.profiles.filterNot { it.id == "copy1" })
    }
    @Test fun editingTheCopyNeverReachesTheSharedTemplateOrAnotherApp() {
        val data = workspace().bind("com.other.app", global.id)
            .copyProfileForApp("com.example.app", "copy1", "Global (copy)")
        val edited = data.copy(profiles = data.profiles.map { if (it.id == "copy1") it.copy(model = "Edited") else it })
        assertEquals("Edited", field(edited).target)
        assertEquals("Pixel", EffectiveSpoofProfileResolver.resolve(edited, "com.other.app", observed)
            .fields.first { it.field == SpoofField.MODEL }.target)
        assertEquals(global, edited.profiles.first { it.id == global.id })
    }
    @Test fun copyCarriesThisAppOwnOptionsAndFailsClosedWithoutASource() {
        val data = workspace().bind("com.example.app", custom.id)
            .setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.CUSTOM, tags = setOf("dnd")))
            .copyProfileForApp("com.example.app", "copy2", "Custom (copy)")
        assertEquals("copy2", data.bindings["com.example.app"])
        assertEquals(setOf("dnd"), data.appPolicy("com.example.app").tags)
        // بلا مصدر (لا ربط ولا قالب عام) وبلا حزمة صحيحة: فشل صريح لا كتابة صامتة.
        assertTrue(runCatching {
            SpoofWorkspace(listOf(global)).copyProfileForApp("com.example.app", "copy3", "Copy")
        }.isFailure)
        assertTrue(runCatching {
            workspace().copyProfileForApp("com..bad", "copy4", "Copy")
        }.isFailure)
    }
    @Test fun copyRefusesDuplicateIdBlankNameAndTheHundredProfileCap() {
        assertTrue(runCatching {
            workspace().copyProfileForApp("com.example.app", global.id, "Copy")
        }.isFailure)
        assertTrue(runCatching {
            workspace().copyProfileForApp("com.example.app", "copy5", "  ")
        }.isFailure)
        val full = SpoofWorkspace((1..100).map { global.copy(id = "p$it") }, emptyMap(), "p1")
        assertTrue(runCatching { full.copyProfileForApp("com.example.app", "copy6", "Copy") }.isFailure)
    }
    @Test fun capabilitiesNeverInventRootOrProcessEvidence() {
        assertEquals(SpoofCapabilityState.UNKNOWN, SpoofCapabilityResolver.state(SpoofField.MODEL, null))
        assertEquals(SpoofCapabilityState.UNAVAILABLE, SpoofCapabilityResolver.state(SpoofField.MODEL, false))
        assertEquals(SpoofCapabilityState.NEEDS_ADAPTER, SpoofCapabilityResolver.state(SpoofField.MODEL, true, global = true))
        assertEquals(SpoofCapabilityState.NEVER_TOUCH, SpoofCapabilityResolver.state(SpoofField.SERIAL, true))
        assertEquals(SpoofCapabilityState.READ_ONLY, SpoofCapabilityResolver.state(SpoofField.SDK_INT, true))
    }
}
