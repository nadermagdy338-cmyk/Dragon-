package nd.max.core.atlas

import nd.max.core.atlas.support.AtlasSourceGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P1` tests. They are pure JVM: the catalog is data plus pure rules, so nothing here touches a
 * device, a privilege transport or the control plane.
 */
class AtlasCatalogTest {

    // ---- the reviewed seed set --------------------------------------------------------------------

    @Test
    fun `the reviewed seed catalog is valid versioned and non empty`() {
        val validation = AtlasReviewedSeeds.validation()
        assertTrue("seed catalog must be valid: $validation", validation is AtlasCatalogValidation.Valid)
        val catalog = AtlasReviewedSeeds.catalog()
        assertEquals(AtlasCatalog.SCHEMA_VERSION, catalog.version)
        assertTrue(catalog.entries.size >= 10)
        assertTrue(catalog.entries.all { it.safety == AtlasSafetyClass.READ_ONLY })
        assertTrue(catalog.entries.all { it.provenance.sourceId.isNotBlank() })
        assertTrue(catalog.entries.all { AtlasAnchors.isApproved(it.parentRoot) })
    }

    // ---- validation negatives ---------------------------------------------------------------------

    @Test
    fun `an unsupported schema version is rejected instead of read as this one`() {
        val result = AtlasCatalog.validate("atlas-catalog-99", AtlasReviewedSeeds.entries())
        assertTrue(result is AtlasCatalogValidation.Invalid)
        assertTrue((result as AtlasCatalogValidation.Invalid).problems.contains("unsupported-schema:atlas-catalog-99"))
    }

    @Test
    fun `an empty catalog is rejected`() {
        val result = AtlasCatalog.validate(AtlasCatalog.SCHEMA_VERSION, emptyList())
        assertEquals(listOf("empty-catalog"), (result as AtlasCatalogValidation.Invalid).problems)
    }

    @Test
    fun `duplicate ids are rejected`() {
        val one = entry(id = "cpu.dup", root = "/sys/devices/system/cpu/cpufreq", attribute = "scaling_cur_freq")
        val two = entry(id = "cpu.dup", root = "/sys/devices/system/cpu/cpufreq", attribute = "scaling_max_freq")
        val result = AtlasCatalog.validate(AtlasCatalog.SCHEMA_VERSION, listOf(one, two))
        assertTrue((result as AtlasCatalogValidation.Invalid).problems.contains("duplicate-id:cpu.dup"))
    }

    @Test
    fun `one interface described with two units is rejected`() {
        val kilo = entry(id = "cpu.unit.one", root = "/sys/devices/system/cpu/cpufreq", attribute = "scaling_cur_freq", unit = AtlasUnit.KILO_HERTZ)
        val hertz = entry(id = "cpu.unit.two", root = "/sys/devices/system/cpu/cpufreq", attribute = "scaling_cur_freq", unit = AtlasUnit.HERTZ)
        val result = AtlasCatalog.validate(AtlasCatalog.SCHEMA_VERSION, listOf(kilo, hertz))
        val problems = (result as AtlasCatalogValidation.Invalid).problems
        assertTrue(problems.any { it.startsWith("unit-conflict:") })
    }

    @Test
    fun `a root outside the approved anchors is rejected`() {
        val outside = entry(id = "cpu.outside", root = "/sys/class/nonsense", attribute = "something")
        val result = AtlasCatalog.validate(AtlasCatalog.SCHEMA_VERSION, listOf(outside))
        assertTrue((result as AtlasCatalogValidation.Invalid).problems.contains("unapproved-root:/sys/class/nonsense"))
    }

    @Test
    fun `an unsafe basename cannot even be constructed`() {
        expectIllegalArgument { entry(id = "cpu.unsafe", root = "/sys/devices/system/cpu/cpufreq", attribute = "bad/name") }
        expectIllegalArgument { entry(id = "cpu.unsafe2", root = "/sys/devices/system/cpu/cpufreq", attribute = "*") }
        expectIllegalArgument { entry(id = "cpu.unsafe3", root = "/sys/devices/system/cpu/cpufreq", attribute = "..") }
    }

    @Test
    fun `a claim without an observed revision is refused at provenance construction`() {
        expectIllegalArgument {
            AtlasProvenance(
                sourceId = "S01",
                reference = "https://example.invalid/x",
                revision = "",
                licenseNote = "cited",
                confidence = AtlasSourceConfidence.SOURCE_VERIFIED,
            )
        }
    }

    // ---- selection --------------------------------------------------------------------------------

    @Test
    fun `an unknown vendor keeps generic candidates and vendor entries stay last`() {
        val seed = AtlasReviewedSeeds.catalog()

        val unknown = seed.candidates(AtlasDomain.CPU, setOf("some-unknown-vendor"))
        assertFalse("generic knowledge must survive an unknown vendor", unknown.isEmpty())
        assertTrue(unknown.all { it.provider != AtlasProviderKind.VENDOR })

        val genericOnly = seed.candidates(AtlasDomain.GPU)
        assertTrue(genericOnly.none { it.provider == AtlasProviderKind.VENDOR })

        val mediatek = seed.candidates(AtlasDomain.GPU, setOf("mediatek"))
        val vendor = mediatek.filter { it.provider == AtlasProviderKind.VENDOR }
        assertTrue(vendor.isNotEmpty())
        assertEquals(mediatek.last(), vendor.last())
    }

    @Test
    fun `candidate selection is deterministic`() {
        val seed = AtlasReviewedSeeds.catalog()
        val first = seed.candidates(AtlasDomain.GPU, setOf("mediatek")).map(AtlasCatalogEntry::id)
        val second = seed.candidates(AtlasDomain.GPU, setOf("mediatek")).map(AtlasCatalogEntry::id)
        assertEquals(first, second)
    }

    // ---- pure rules --------------------------------------------------------------------------------

    @Test
    fun `unit rules never guess a unit from a magnitude`() {
        assertEquals(null, AtlasUnitRules.frequencyHertz(2400.0, AtlasUnit.MICRO_AMP))
        assertEquals(null, AtlasUnitRules.celsius(42.0, AtlasUnit.HERTZ))
        assertEquals(null, AtlasUnitRules.chargeMicroAmpHours(3000.0, AtlasUnit.MICRO_WATT_HOUR))
        assertEquals(AtlasUnit.UNKNOWN, AtlasUnitRules.fromToken("2400"))
        assertEquals(AtlasUnit.UNKNOWN, AtlasUnitRules.fromToken(""))
        assertEquals(AtlasUnit.UNKNOWN, AtlasUnitRules.fromToken(null))
    }

    @Test
    fun `frequency and temperature conversions are explicit`() {
        assertEquals(2_400_000.0, AtlasUnitRules.frequencyHertz(2400.0, AtlasUnit.KILO_HERTZ)!!, 0.0001)
        assertEquals(1_800_000_000.0, AtlasUnitRules.frequencyHertz(1800.0, AtlasUnit.MEGA_HERTZ)!!, 0.0001)
        assertEquals(42.0, AtlasUnitRules.celsius(42_000.0, AtlasUnit.MILLI_CELSIUS)!!, 0.0001)
        assertEquals(42.0, AtlasUnitRules.celsius(420.0, AtlasUnit.DECI_CELSIUS)!!, 0.0001)
        assertEquals(AtlasUnit.KILO_HERTZ, AtlasUnitRules.fromToken(" kHz "))
    }

    @Test
    fun `cpu lists are normalized and malformed lists fail instead of shrinking`() {
        assertEquals(listOf(0, 1, 2, 3, 5), AtlasCpuList.parse("0-3,5"))
        assertEquals(listOf(0, 1, 2), AtlasCpuList.parse("0 1 2"))
        assertEquals(listOf(2), AtlasCpuList.parse("2 2 2"))
        assertEquals(null, AtlasCpuList.parse("3-1"))
        assertEquals(null, AtlasCpuList.parse("0-"))
        assertEquals(null, AtlasCpuList.parse("cpu0"))
        assertEquals(null, AtlasCpuList.parse(""))
        assertEquals(null, AtlasCpuList.parse(null))
        assertEquals(listOf(0, 1, 2, 3), AtlasCpuList.policyCoreSet("0-3", "1,2"))
        assertEquals(listOf(4, 5), AtlasCpuList.policyCoreSet(null, "4-5"))
        assertEquals(null, AtlasCpuList.policyCoreSet(null, null))
    }

    // ---- model invariants --------------------------------------------------------------------------

    @Test
    fun `impossible evidence is refused by construction`() {
        expectIllegalArgument { observation().copy(failure = AtlasFailure.PERMISSION_DENIED) }
        expectIllegalArgument { observation().copy(value = null) }
        expectIllegalArgument { observation().copy(textValue = "text") }
        expectIllegalArgument { observation().copy(value = Double.NaN) }
        expectIllegalArgument { observation().copy(unit = AtlasUnit.UNKNOWN) }
        expectIllegalArgument { observation().copy(truncated = true) }
        expectIllegalArgument { AtlasReadResult.Rejected("/sys/x", AtlasFailure.NONE, "no cause") }
        expectIllegalArgument { AtlasReadResult.Rejected("/sys/x", AtlasFailure.ABSENT, "  ") }

        val cancelled = observation().copy(access = AtlasAccess.UNKNOWN, failure = AtlasFailure.CANCELLED, value = null)
        assertEquals(AtlasFailure.CANCELLED, cancelled.failure)
        val truncated = observation().copy(failure = AtlasFailure.BUDGET_EXCEEDED, value = null, truncated = true)
        assertTrue(truncated.truncated)
    }

    // ---- structural guard ---------------------------------------------------------------------------

    @Test
    fun `the evidence vocabulary names no authority or transport token`() {
        val sources = listOf(
            "src/main/java/nd/max/core/atlas/AtlasModels.kt",
            "src/main/java/nd/max/core/atlas/AtlasCatalog.kt",
            "src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt",
        )
        sources.forEach { relative ->
            val hits = AtlasSourceGuard.forbiddenHits(AtlasSourceGuard.read(relative))
            assertEquals("$relative must not name: $hits", emptyList<String>(), hits)
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun observation(): AtlasObservation = AtlasObservation(
        id = "test.observation",
        domain = AtlasDomain.THERMAL,
        providerId = "fake",
        catalogVersion = AtlasCatalog.SCHEMA_VERSION,
        sourceId = "L05",
        path = "/sys/class/thermal/thermal_zone0/temp",
        access = AtlasAccess.READABLE,
        semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
        unit = AtlasUnit.MILLI_CELSIUS,
        value = 42_000.0,
        textValue = null,
        rawRepresentation = "42000",
        failure = AtlasFailure.NONE,
        reason = "read",
        observedAtElapsedMs = 0L,
        bootGeneration = 0L,
        privilegeGeneration = 0L,
        truncated = false,
    )

    private fun entry(
        id: String,
        root: String,
        attribute: String,
        unit: AtlasUnit = AtlasUnit.UNKNOWN,
        provider: AtlasProviderKind = AtlasProviderKind.GENERIC,
        vendorTags: Set<String> = emptySet(),
    ): AtlasCatalogEntry = AtlasCatalogEntry(
        id = id,
        domain = AtlasDomain.CPU,
        provider = provider,
        vendorTags = vendorTags,
        parentRoot = root,
        attribute = attribute,
        unit = unit,
        safety = AtlasSafetyClass.READ_ONLY,
        provenance = AtlasProvenance(
            sourceId = "L02",
            reference = "https://docs.kernel.org/admin-guide/pm/cpufreq.html",
            revision = "live docs snapshot, fetched 2026-09-20",
            licenseNote = "cited documentation; no code copied",
            confidence = AtlasSourceConfidence.SOURCE_VERIFIED,
        ),
        featureTag = null,
        note = "test entry",
    )

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
