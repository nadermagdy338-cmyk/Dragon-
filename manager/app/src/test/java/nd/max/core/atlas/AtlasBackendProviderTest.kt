/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.atlas.support.AtlasSourceGuard
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.GpuHardwareBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P3` acceptance tests for backend reuse.
 *
 * The seams under test are the product's own (`CpuHardwareBackend.DiscoveryIo`,
 * `GpuHardwareBackend.ReadIo`); what is fake here is only the filesystem, so the parser, the alias
 * grouping, the unit inference and the ambiguity rules being tested are the shipped ones.
 */
class AtlasBackendProviderTest {

    // ---- construction ------------------------------------------------------------------------------

    @Test
    fun `the provider refuses to exist when a mapped entry is missing from the catalog`() {
        val stripped = AtlasCatalog(AtlasCatalog.SCHEMA_VERSION, emptyList())

        val failure = runCatching { provider(catalog = stripped) }.exceptionOrNull()

        assertTrue("a catalog mismatch must fail loudly", failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("cpu.policy.scaling_cur_freq"))
    }

    @Test
    fun `every mapped entry is one the reviewed catalog actually holds`() {
        val catalog = AtlasReviewedSeeds.catalog()

        AtlasBackendProvider.MAPPED_ENTRY_IDS.forEach { id ->
            assertTrue("$id must be reviewed", catalog.byId(id) != null)
        }
    }

    @Test
    fun `the default cpu reader is the system one and an injected reader is really used`() {
        // The default is what every existing writer already calls; if these ever diverge, a writer
        // would silently start using a different reader than the one Atlas is handed. The injected
        // call proves the seam is wired rather than ignored.
        assertEquals(
            CpuHardwareBackend.commonGovernors(CpuHardwareBackend.SystemDiscoveryIo),
            CpuHardwareBackend.commonGovernors(),
        )
        assertEquals(
            "the discovered governors come from the injected reader",
            listOf("performance", "powersave"),
            CpuHardwareBackend.commonGovernors(policyIo()),
        )
    }

    // ---- CPU discovery -----------------------------------------------------------------------------

    @Test
    fun `a legacy cpuN layout is discovered and one alias group is reported once`() {
        val io = FakeDiscoveryIo(
            directories = mapOf(
                LEGACY_ROOT to listOf("cpu0", "cpu1", "cpu2", "cpu3"),
                "/sys/devices/system/cpu/cpu0/cpufreq" to listOf("scaling_governor", "related_cpus"),
                "/sys/devices/system/cpu/cpu1/cpufreq" to listOf("scaling_governor", "related_cpus"),
                "/sys/devices/system/cpu/cpu2/cpufreq" to listOf("scaling_governor", "related_cpus"),
                "/sys/devices/system/cpu/cpu3/cpufreq" to listOf("scaling_governor", "related_cpus"),
            ),
            files = mapOf(
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor" to "schedutil",
                "/sys/devices/system/cpu/cpu1/cpufreq/scaling_governor" to "schedutil",
                "/sys/devices/system/cpu/cpu2/cpufreq/scaling_governor" to "schedutil",
                "/sys/devices/system/cpu/cpu3/cpufreq/scaling_governor" to "schedutil",
                "/sys/devices/system/cpu/cpu0/cpufreq/related_cpus" to "0-3",
                "/sys/devices/system/cpu/cpu1/cpufreq/related_cpus" to "0-3",
                "/sys/devices/system/cpu/cpu2/cpufreq/related_cpus" to "0-3",
                "/sys/devices/system/cpu/cpu3/cpufreq/related_cpus" to "0-3",
            ),
        )

        val evidence = provider().cpu(io)

        assertEquals("one hardware policy, four aliases", 1, evidence.cpuPolicies.size)
        assertEquals("cpu0", evidence.cpuPolicies.single().name)
    }

    @Test
    fun `a policy directory that is listed but silent is unknown, not absent`() {
        val io = policyIo(extraDirectories = mapOf(CPU_ROOT to listOf("policy0", "policy1")))

        val evidence = provider().cpu(io)

        assertEquals("only policy0 answered", 1, evidence.cpuPolicies.size)
        val silent = evidence.rejected.filter { it.path.contains("policy1") }
        assertTrue("policy1 must appear as unknown", silent.isNotEmpty())
        assertTrue(silent.all { it.failure == AtlasFailure.UNKNOWN_CAUSE })
        assertFalse(
            "an unreadable policy is never reported as absent",
            evidence.rejected.any { it.path.contains("policy1") && it.failure == AtlasFailure.ABSENT },
        )
    }

    @Test
    fun `a missing ladder permits observation and invents no OPP`() {
        val io = FakeDiscoveryIo(
            directories = mapOf(
                CPU_ROOT to listOf("policy0"),
                "$CPU_ROOT/policy0" to listOf("scaling_governor", "scaling_cur_freq"),
            ),
            files = mapOf(
                "$CPU_ROOT/policy0/scaling_governor" to "performance",
                "$CPU_ROOT/policy0/scaling_cur_freq" to "1800000",
            ),
        )

        val evidence = provider().cpu(io)
        val fact = evidence.cpuPolicies.single()

        assertFalse(fact.hasLadder)
        assertNull("no ladder means no inferred bound", fact.provenMinKHz)
        assertNull(fact.provenMaxKHz)
        assertFalse(fact.boundsDeclaredByKernel)
        assertEquals(
            "the reading is still observed",
            1_800_000.0,
            observedValue(evidence, "cpu.policy.scaling_cur_freq"),
            0.0,
        )
        assertEquals(AtlasUnit.KILO_HERTZ, observedUnit(evidence, "cpu.policy.scaling_cur_freq"))
    }

    @Test
    fun `kernel declared bounds are distinguished from ladder derived ones`() {
        val declared = provider().cpu(
            policyIo(
                directoryFiles = mapOf(
                    "$CPU_ROOT/policy0/cpuinfo_min_freq" to "300000",
                    "$CPU_ROOT/policy0/cpuinfo_max_freq" to "2000000",
                ),
            ),
        ).cpuPolicies.single()
        val derived = provider().cpu(policyIo()).cpuPolicies.single()

        assertTrue(declared.boundsDeclaredByKernel)
        assertEquals(300_000L, declared.provenMinKHz)
        assertEquals(2_000_000L, declared.provenMaxKHz)
        assertFalse("a ladder end point is not a kernel declaration", derived.boundsDeclaredByKernel)
        assertEquals(500_000L, derived.provenMinKHz)
    }

    @Test
    fun `a value is never attributed to an interface that did not answer`() {
        val io = FakeDiscoveryIo(
            directories = mapOf(
                CPU_ROOT to listOf("policy0"),
                "$CPU_ROOT/policy0" to listOf("scaling_governor", "cpuinfo_cur_freq"),
            ),
            files = mapOf(
                "$CPU_ROOT/policy0/scaling_governor" to "schedutil",
                "$CPU_ROOT/policy0/cpuinfo_cur_freq" to "1500000",
            ),
        )

        val evidence = provider().cpu(io)

        assertEquals(1_500_000L, evidence.cpuPolicies.single().currentKHz)
        assertEquals(AtlasCpuFrequencySource.CPUINFO_CUR_FREQ, evidence.cpuPolicies.single().currentSource)
        val scaling = evidence.results.filterIsInstance<AtlasReadResult.Rejected>()
            .single { it.path.endsWith("scaling_cur_freq") }
        assertEquals("the fallback interface stays absent", AtlasFailure.ABSENT, scaling.failure)
        assertTrue(
            "no observation may claim the scaling interface answered",
            evidence.observed.none { it.path.endsWith("scaling_cur_freq") },
        )
    }

    // ---- honesty of failure causes -----------------------------------------------------------------

    @Test
    fun `absence needs a listing that was actually read`() {
        // The policy directory answers for `scaling_governor` while its own listing is empty — the
        // shape of a directory that can be read but cannot be enumerated.
        val unlistable = FakeDiscoveryIo(
            directories = mapOf(CPU_ROOT to listOf("policy0")),
            files = mapOf("$CPU_ROOT/policy0/scaling_governor" to "schedutil"),
        )
        val fromListing = provider().cpu(unlistable)
            .rejected.single { it.path.endsWith("scaling_cur_freq") }

        assertEquals(
            "an empty parent listing cannot prove absence",
            AtlasFailure.UNKNOWN_CAUSE,
            fromListing.failure,
        )
        assertTrue(fromListing.reason.contains("not claimed"))

        val listed = provider().cpu(policyIo())
            .rejected.filter { it.path.endsWith("scaling_available_frequencies") }
        assertTrue("a ladder that is not listed is not read here at all", listed.isEmpty())
    }

    @Test
    fun `an attribute that is listed but silent is unknown rather than absent`() {
        val io = FakeDiscoveryIo(
            directories = mapOf(
                CPU_ROOT to listOf("policy0"),
                "$CPU_ROOT/policy0" to listOf("scaling_governor", "related_cpus"),
            ),
            files = mapOf("$CPU_ROOT/policy0/scaling_governor" to "schedutil"),
        )

        val rejected = provider().cpu(io).rejected.single { it.path.endsWith("related_cpus") }

        assertEquals(AtlasFailure.UNKNOWN_CAUSE, rejected.failure)
        assertTrue(rejected.reason.contains("present in the listing"))
    }

    @Test
    fun `malformed text keeps the raw reading and yields no value`() {
        val io = policyIo(
            directoryFiles = mapOf("$CPU_ROOT/policy0/scaling_cur_freq" to "1.8GHz\n"),
        )

        val rejected = provider().cpu(io).rejected.single { it.failure == AtlasFailure.MALFORMED }

        assertTrue(rejected.path.endsWith("scaling_cur_freq"))
        assertTrue("the raw reading is preserved", rejected.reason.contains("1.8GHz"))
    }

    @Test
    fun `a reader that breaks its contract with blank text still yields no value`() {
        // The real reader turns blank into null, so this branch only exists for a non-conforming
        // reader. It is tested with one, rather than being left as an untested defensive line.
        val conforming = policyIo()
        val blanking = object : CpuHardwareBackend.DiscoveryIo {
            override fun read(path: String): String? =
                if (path.endsWith("scaling_available_governors")) "   " else conforming.read(path)

            override fun listDirectories(path: String): List<String> = conforming.listDirectories(path)
        }

        val rejected = provider().cpu(blanking).rejected.single { it.path.endsWith("scaling_available_governors") }

        assertEquals(AtlasFailure.MALFORMED, rejected.failure)
        assertTrue(rejected.reason.contains("blank"))
    }

    @Test
    fun `an unreadable value with no visible parent is not absence`() {
        val io = FakeDiscoveryIo(directories = emptyMap(), files = emptyMap())

        val evidence = provider().cpu(io)

        assertEquals(
            "no policy at all is a missing backend, not a missing file",
            AtlasFailure.BACKEND_UNAVAILABLE,
            evidence.rejected.single().failure,
        )
    }

    // ---- GPU ---------------------------------------------------------------------------------------

    @Test
    fun `the gpu path never asks about writability and never writes`() {
        val counting = CountingIo(HZ_NODE)

        val evidence = provider().gpu(counting)

        assertEquals("the reader's writer half was never consulted", 0, counting.writableCalls)
        assertEquals(0, counting.writeCalls)
        assertTrue(evidence.observed.isNotEmpty())
        assertTrue(
            "a read-only caller has no way to express a write",
            AtlasSourceGuard.forbiddenHits(AtlasSourceGuard.code(PROVIDER_SOURCE)).isEmpty(),
        )
    }

    @Test
    fun `a mainline hertz driver is a reviewed match`() {
        // The node values carry the trailing newline a kernel writes, and this reader hands them back
        // unchanged: if the scanner ever assumes a trimmed reader again, this test loses the clock.
        val evidence = provider().gpu(CountingIo(HZ_NODE))

        assertEquals(AtlasUnit.HERTZ, observedUnit(evidence, "gpu.devfreq.cur_freq"))
        assertEquals(600_000_000.0, observedValue(evidence, "gpu.devfreq.cur_freq"), 0.0)
        assertEquals(
            AtlasSemanticStatus.REVIEWED_MATCH,
            evidence.observed.single { it.id == "gpu.devfreq.cur_freq" }.semanticStatus,
        )
    }

    @Test
    fun `a vendor deviation from mainline hz stays inferred and says so`() {
        val evidence = provider().gpu(CountingIo(KHZ_NODE))
        val observation = evidence.observed.single { it.id == "gpu.devfreq.cur_freq" }

        assertEquals("the value is reported in the reviewed unit", 600_000_000.0, requireNotNull(observation.value), 0.0)
        assertEquals(AtlasSemanticStatus.INFERRED, observation.semanticStatus)
        assertTrue(observation.reason.contains("khz"))
        assertTrue(observation.reason.contains("inferred"))
    }

    @Test
    fun `an ambiguous frequency unit produces no value at all`() {
        val evidence = provider().gpu(CountingIo(AMBIGUOUS_NODE))

        assertTrue("no observation may carry an unestablished unit", evidence.observed.isEmpty())
        assertEquals(AtlasFailure.AMBIGUOUS, evidence.rejected.single().failure)
        assertTrue(evidence.rejected.single().reason.contains("not assumed"))
        assertFalse("nothing is claimed", evidence.rejected.single().reason.isBlank())
    }

    @Test
    fun `two equally proven providers stay ambiguous and none is chosen`() {
        val io = CountingIo(
            HZ_NODE + mapOf(
                // The root must list both, otherwise this test is about a single-provider device.
                "/sys/class/devfreq" to listOf("gpu0", "gpu1"),
                "/sys/class/devfreq/gpu1" to listOf("cur_freq", "available_frequencies", "governor"),
                "/sys/class/devfreq/gpu1/cur_freq" to "600000000\n",
                "/sys/class/devfreq/gpu1/available_frequencies" to "300000000 600000000\n",
                "/sys/class/devfreq/gpu1/governor" to "performance\n",
            ),
        )

        val evidence = provider().gpu(io)

        assertTrue(evidence.observed.isEmpty())
        assertEquals(2, evidence.rejected.size)
        assertTrue(evidence.rejected.all { it.failure == AtlasFailure.AMBIGUOUS })
        assertNull("no provider is selected", evidence.gpu?.bestPath)
        assertEquals(GpuHardwareBackend.GpuObservationState.AMBIGUOUS, evidence.gpu?.state)
        assertEquals("both candidates stay visible", 2, evidence.gpu?.devices?.size)
    }

    @Test
    fun `an unavailable gpu backend is a missing backend rather than absence`() {
        val evidence = provider().gpu(CountingIo(emptyMap()))

        assertEquals(AtlasFailure.BACKEND_UNAVAILABLE, evidence.rejected.single().failure)
        assertTrue(evidence.observed.isEmpty())
    }

    @Test
    fun `a gpu provider that reports no clock is never given one`() {
        val io = CountingIo(
            mapOf(
                "/sys/class/devfreq" to listOf("gpu0"),
                "/sys/class/devfreq/gpu0" to listOf("governor", "available_frequencies"),
                "/sys/class/devfreq/gpu0/governor" to "simple_ondemand\n",
                "/sys/class/devfreq/gpu0/available_frequencies" to "300000000 600000000\n",
            ),
        )

        val evidence = provider().gpu(io)

        assertTrue(evidence.observed.isEmpty())
        assertEquals(AtlasFailure.ABSENT, evidence.rejected.single().failure)
        assertEquals("the ladder is still projected", 2, evidence.gpu?.devices?.single()?.frequencies?.size)
    }

    // ---- shared shape ------------------------------------------------------------------------------

    @Test
    fun `no observation claims a control class or a non-read-only safety`() {
        val cpu = provider().cpu(policyIo())
        val gpu = provider().gpu(CountingIo(HZ_NODE))

        val catalog = AtlasReviewedSeeds.catalog()
        (cpu.observed + gpu.observed).forEach { observation ->
            assertEquals(
                "a mapped observation is a reviewed read-only entry",
                AtlasSafetyClass.READ_ONLY,
                catalog.byId(observation.id)?.safety,
            )
            assertTrue("the path is an interface, not an action", observation.path.startsWith("/"))
            assertTrue("every observation carries a cause of success", observation.failure == AtlasFailure.NONE)
        }
        assertTrue("a rejection always explains itself", (cpu.rejected + gpu.rejected).all { it.reason.isNotBlank() })
    }

    @Test
    fun `free text is never promoted to a typed value`() {
        val evidence = provider().cpu(policyIo())
        val governors = evidence.observed.single { it.id == "cpu.policy.scaling_available_governors" }

        assertEquals(AtlasUnit.UNKNOWN, governors.unit)
        assertNull(governors.value)
        assertEquals("performance powersave", governors.textValue)
        assertEquals(AtlasUnit.UNKNOWN, evidence.observed.single { it.id == "cpu.policy.related_cpus" }.unit)
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun provider(catalog: AtlasCatalog = AtlasReviewedSeeds.catalog()) = AtlasBackendProvider(
        catalog = catalog,
        elapsedMs = { 1_000L },
        bootGeneration = 0L,
        privilegeGeneration = 0L,
    )

    private fun observedValue(evidence: AtlasBackendEvidence, id: String): Double =
        requireNotNull(evidence.observed.single { it.id == id }.value)

    private fun observedUnit(evidence: AtlasBackendEvidence, id: String): AtlasUnit =
        evidence.observed.single { it.id == id }.unit

    private fun policyIo(
        extraDirectories: Map<String, List<String>> = emptyMap(),
        directoryFiles: Map<String, String> = emptyMap(),
    ): CpuHardwareBackend.DiscoveryIo {
        val directories = mutableMapOf(
            CPU_ROOT to listOf("policy0"),
            "$CPU_ROOT/policy0" to listOf(
                "scaling_governor",
                "scaling_available_governors",
                "scaling_min_freq",
                "scaling_max_freq",
                "scaling_cur_freq",
                "related_cpus",
                "scaling_available_frequencies",
            ),
        )
        extraDirectories.forEach { (path, names) -> directories[path] = names }
        val files = mutableMapOf(
            "$CPU_ROOT/policy0/scaling_governor" to "schedutil\n",
            "$CPU_ROOT/policy0/scaling_available_governors" to "performance powersave\n",
            "$CPU_ROOT/policy0/scaling_min_freq" to "300000\n",
            "$CPU_ROOT/policy0/scaling_max_freq" to "2000000\n",
            "$CPU_ROOT/policy0/scaling_cur_freq" to "1800000\n",
            "$CPU_ROOT/policy0/related_cpus" to "0-3\n",
            "$CPU_ROOT/policy0/scaling_available_frequencies" to "500000 1000000 2000000\n",
        )
        directoryFiles.forEach { (path, text) -> files[path] = text }
        return FakeDiscoveryIo(directories = directories, files = files)
    }

    /** A mainline devfreq node: values large enough to be Hz, which `inferFrequencyUnit` accepts. */
    private val HZ_NODE: Map<String, Any> = mapOf(
        "/sys/class/devfreq" to listOf("gpu0"),
        "/sys/class/devfreq/gpu0" to listOf("cur_freq", "available_frequencies", "governor"),
        "/sys/class/devfreq/gpu0/cur_freq" to "600000000\n",
        "/sys/class/devfreq/gpu0/available_frequencies" to "300000000 600000000\n",
        "/sys/class/devfreq/gpu0/governor" to "performance\n",
    )

    /** A vendor node reporting kHz: readable, but not the reviewed ABI unit. */
    private val KHZ_NODE: Map<String, Any> = mapOf(
        "/sys/class/devfreq" to listOf("gpu0"),
        "/sys/class/devfreq/gpu0" to listOf("cur_freq", "available_frequencies", "governor"),
        "/sys/class/devfreq/gpu0/cur_freq" to "600000\n",
        "/sys/class/devfreq/gpu0/available_frequencies" to "300000 600000\n",
        "/sys/class/devfreq/gpu0/governor" to "performance\n",
    )

    /** A node whose readings disagree about their own unit. */
    private val AMBIGUOUS_NODE: Map<String, Any> = mapOf(
        "/sys/class/devfreq" to listOf("gpu0"),
        "/sys/class/devfreq/gpu0" to listOf("cur_freq", "available_frequencies", "governor"),
        "/sys/class/devfreq/gpu0/cur_freq" to "600000000\n",
        "/sys/class/devfreq/gpu0/available_frequencies" to "600000000 1800000\n",
        "/sys/class/devfreq/gpu0/governor" to "performance\n",
    )

    /**
     * Fake filesystem for the CPU discovery seam: only the two calls the parser may make.
     *
     * It honours the seam's contract — trimmed text or `null` — because a fake that is friendlier than
     * the real reader would let a parser bug pass the tests and fail on a device.
     */
    private class FakeDiscoveryIo(
        private val directories: Map<String, List<String>>,
        private val files: Map<String, String>,
    ) : CpuHardwareBackend.DiscoveryIo {
        override fun read(path: String): String? = files[path]?.trim()?.takeIf { it.isNotEmpty() }
        override fun listDirectories(path: String): List<String> = directories[path].orEmpty()
    }

    /**
     * A GPU reader that also *could* write, so the test can prove the read path never asks.
     *
     * The provider only ever receives it as a `ReadIo`; the writer half exists here purely to be
     * counted, and any call to it would fail the test rather than silently pass. It returns the map
     * values exactly as written — newline included — which is what a sysfs read looks like.
     */
    private class CountingIo(private val map: Map<String, Any>) : GpuHardwareBackend.Io {
        var writableCalls: Int = 0
        var writeCalls: Int = 0

        override fun exists(path: String): Boolean = map.containsKey(path)
        override fun read(path: String): String? = map[path] as? String
        override fun listDirectories(path: String): List<String> =
            @Suppress("UNCHECKED_CAST")
            (map[path] as? List<String>).orEmpty()

        override fun writable(path: String): Boolean {
            writableCalls += 1
            return false
        }

        override fun write(path: String, value: String): Boolean {
            writeCalls += 1
            return false
        }
    }

    private companion object {
        const val CPU_ROOT = "/sys/devices/system/cpu/cpufreq"
        const val LEGACY_ROOT = "/sys/devices/system/cpu"
        const val PROVIDER_SOURCE = "src/main/java/nd/max/core/atlas/AtlasBackendProvider.kt"
    }
}
