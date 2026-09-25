/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.atlas.support.AtlasClock
import nd.max.core.atlas.support.AtlasFakeTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The enumerated-child grammar, and the regression guard for the defect that made it necessary.
 *
 * **What was wrong.** Twelve of the fifteen reviewed entries named a *class directory* as if an
 * attribute lived directly inside it — `/sys/class/devfreq/cur_freq`, `/sys/class/thermal/temp`,
 * `/sys/class/power_supply/charge_full`, `/sys/devices/system/cpu/cpufreq/scaling_cur_freq` and so on.
 * On any real device those directories contain *devices*, so every one of those paths names a file that
 * cannot exist while the interface it describes is present and readable one level down. Nothing in the
 * old grammar refused the shape, and no test could see it, because the paths were strings that parsed
 * perfectly and resolved never.
 *
 * So the tests here are of two kinds: the shape rule (which would have caught the defect the day it was
 * written), and the enumeration behavior (which is what makes a device nobody wrote down readable).
 */
class AtlasChildScopeTest {

    // ---- the shape rule ------------------------------------------------------------------------------

    @Test
    fun `a class directory is never addressed as if it held the file itself`() {
        val classDirectories = setOf(
            "/sys/class/devfreq",
            "/sys/class/thermal",
            "/sys/class/power_supply",
            "/sys/class/kgsl",
            "/sys/devices/system/cpu/cpufreq",
            "/sys/block",
        )
        val wrong = AtlasReviewedSeeds.entries().filter { entry ->
            entry.parentRoot in classDirectories && entry.scope != AtlasCatalogScope.CHILD_FILE
        }

        assertTrue(
            "these entries address a device class as one file, a path that cannot exist: " +
                wrong.map { "${it.id} -> ${it.parentRoot}/${it.attribute}" },
            wrong.isEmpty(),
        )
    }

    @Test
    fun `a class directory entry carries the child prefix that selects its own kind of device`() {
        val thermal = AtlasReviewedSeeds.entries().single { it.id == "thermal.zone.temp" }
        assertEquals("thermal_zone", thermal.childPrefix)

        // Without the prefix, `temp` would also be attempted inside every cooling device — a read that
        // cannot exist, reported as a proven absence. A prefix is what keeps "not this kind of child"
        // from looking like "not on this device".
        val cooling = AtlasReviewedSeeds.entries().filter { it.parentRoot == "/sys/class/thermal" }
        assertTrue(cooling.all { it.childPrefix == "thermal_zone" })
    }

    @Test
    fun `an enumerated entry is refused when its root may not be walked`() {
        val entry = AtlasReviewedSeeds.entries().single { it.id == "thermal.zone.temp" }

        // `/proc` is not an approved root at all. `/proc/pressure` is approved for the few files the
        // platform grants, and walking it is a different grant — enumerating it would be an
        // authorization nobody gave, so the shape is refused where it is written rather than at read
        // time, when the only thing left to do is fail silently.
        val unwalkable = runCatching {
            entry.copy(parentRoot = "/proc", scope = AtlasCatalogScope.CHILD_FILE)
        }.exceptionOrNull()
        assertNotNull("an enumeration outside the approved roots must be refused", unwalkable)

        // And the same rule holds for every enumerated entry the bank actually ships.
        assertTrue(
            AtlasReviewedSeeds.entries()
                .filter { it.scope == AtlasCatalogScope.CHILD_FILE }
                .all { AtlasAnchors.isEnumerable(it.parentRoot) },
        )
    }

    @Test
    fun `a child prefix is a name fragment and not a pattern`() {
        val entry = AtlasReviewedSeeds.entries().first { it.scope == AtlasCatalogScope.CHILD_FILE }
        listOf("policy*", "[0-9]", "p?licy", "policy;rm", "..").forEach { candidate ->
            val thrown = runCatching { entry.copy(childPrefix = candidate) }.exceptionOrNull()
            assertNotNull("a prefix of `$candidate` must be refused", thrown)
        }
    }

    // ---- enumeration ---------------------------------------------------------------------------------

    @Test
    fun `enumerated features name a child the device listed, one per interface`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/devices/system/cpu/cpufreq", listOf("policy0", "policy4"))
            .putDirectory("/sys/devices/system/cpu", listOf("cpu0", "cpu4", "cpufreq"))
            .putDirectory("/sys/class/thermal", listOf("cooling_device0", "thermal_zone0", "thermal_zone1"))
        val discovery = discovery(transport, clock)

        val features = discovery.enumeratedFeatures(
            job = discovery.newJob(emptyList()),
            catalogs = listOf(AtlasDiscovery.REVIEWED),
        )

        val paths = features.map { it.request.path }
        // Four cpufreq entries on two policies, two thermal entries on two zones.
        assertEquals(8 + 4, features.size)
        assertTrue(paths.contains("/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq"))
        assertTrue(paths.contains("/sys/devices/system/cpu/cpufreq/policy4/related_cpus"))
        assertTrue(paths.contains("/sys/class/thermal/thermal_zone1/temp"))
        assertFalse("a cooling device is not a thermal zone", paths.contains("/sys/class/thermal/cooling_device0/temp"))
        assertFalse("no child was invented", paths.any { it.contains("policy1/") })
    }

    @Test
    fun `an enumerated reading answers through the same bounded boundary`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/devices/system/cpu/cpufreq", listOf("policy0"))
            .putFile(
                "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq",
                nd.max.core.atlas.support.AtlasFakeFile("1804800\n"),
            )
        val discovery = discovery(transport, clock)
        val job = discovery.newJob(emptyList())
        val feature = discovery.enumeratedFeatures(job, listOf(AtlasDiscovery.REVIEWED))
            .first { it.request.id.startsWith("cpu.policy.scaling_cur_freq") }

        val result = job.discover(feature.request)

        val observed = result as AtlasReadResult.Observed
        assertEquals("/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq", observed.observation.path)
        assertEquals(1_804_800.0, observed.observation.value!!, 0.001)
        assertEquals(AtlasUnit.KILO_HERTZ, observed.observation.unit)
        assertEquals(AtlasSemanticStatus.REVIEWED_MATCH, observed.observation.semanticStatus)
    }

    @Test
    fun `an already attempted path is not enumerated twice`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/devices/system/cpu/cpufreq", listOf("policy0"))
        val discovery = discovery(transport, clock)
        val attempted = setOf("/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq")

        val features = discovery.enumeratedFeatures(
            job = discovery.newJob(emptyList()),
            catalogs = listOf(AtlasDiscovery.REVIEWED),
            attemptedPaths = attempted,
        )

        assertFalse(features.map { it.request.path }.any { it in attempted })
    }

    @Test
    fun `a root that could not be listed yields no features and no claim`() {
        val clock = AtlasClock()
        // No directory registered for the root at all: the transport reports a backend failure.
        val discovery = discovery(AtlasFakeTransport(clock), clock)

        val features = discovery.enumeratedFeatures(
            job = discovery.newJob(emptyList()),
            catalogs = listOf(AtlasDiscovery.REVIEWED),
        )

        assertEquals("a listing that failed is not an empty device", emptyList<AtlasFeatureRequest>(), features)
    }

    @Test
    fun `the enumerated limit bounds one scan`() {
        val clock = AtlasClock()
        val policies = (0..20).map { "policy$it" }
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/devices/system/cpu/cpufreq", policies)
        val discovery = discovery(transport, clock)

        val features = discovery.enumeratedFeatures(
            job = discovery.newJob(emptyList()),
            catalogs = listOf(AtlasDiscovery.REVIEWED),
            limit = 5,
        )

        assertEquals(5, features.size)
    }

    // ---- identity ------------------------------------------------------------------------------------

    @Test
    fun `a per-child id is canonical and derived from the name the device returned`() {
        assertEquals(
            "cpu.policy.scaling_cur_freq.policy0",
            AtlasDiscovery.perChildId("cpu.policy.scaling_cur_freq", "policy0"),
        )
        // A vendor directory name is not a canonical id, and the id is sanitized rather than invented.
        assertEquals(
            "gpu.devfreq.cur_freq.soc-qcom-kgsl-busmon",
            AtlasDiscovery.perChildId("gpu.devfreq.cur_freq", "soc:qcom,kgsl-busmon"),
        )
        assertTrue(AtlasIds.isValidObservationId(AtlasDiscovery.perChildId("gpu.devfreq.cur_freq", "13000000.mali")!!))
    }

    @Test
    fun `an id that cannot be canonical or would exceed the bound is skipped rather than invented`() {
        assertNull(AtlasDiscovery.perChildId("cpu.policy.scaling_cur_freq", "___"))
        assertNull(AtlasDiscovery.perChildId("cpu.policy.scaling_available_governors", "averyveryverylongchildnameindeed"))
    }

    @Test
    fun `a vendor interface is only addressed on a device that claims that vendor`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/class/kgsl", listOf("kgsl-3d0"))
        val qualcomm = discovery(transport, clock)
        val mediatek = discovery(
            transport = transport,
            clock = clock,
            socManufacturer = "MediaTek",
            socModel = "MT6989",
            hardware = "mt6989",
            board = "mt6989",
        )

        // Both shapes are collected: the Adreno nodes are enumerated child files, the MediaTek node is
        // one file directly inside its proc directory, and the vendor rule has to hold for both.
        val onQualcomm = addresses(qualcomm)
        val onMediatek = addresses(mediatek)

        assertTrue(onQualcomm.any { it.startsWith("/sys/class/kgsl/") })
        assertFalse(
            "a MediaTek node is not attempted on a Qualcomm device: $onQualcomm",
            onQualcomm.any { it.startsWith("/proc/gpufreq/") },
        )
        assertTrue(onMediatek.any { it.startsWith("/proc/gpufreq/") })
        assertFalse(
            "and an Adreno node is not attempted on a MediaTek one: $onMediatek",
            onMediatek.any { it.startsWith("/sys/class/kgsl/") },
        )
    }

    private fun addresses(discovery: AtlasDiscovery): List<String> {
        val job = discovery.newJob(emptyList())
        return discovery.features(AtlasDiscovery.COMMUNITY).map { it.request.path } +
            discovery.enumeratedFeatures(job, listOf(AtlasDiscovery.COMMUNITY)).map { it.request.path }
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private fun discovery(
        transport: AtlasFakeTransport,
        clock: AtlasClock,
        socManufacturer: String = "Qualcomm",
        socModel: String = "SM8650",
        hardware: String = "qcom",
        board: String = "test",
    ) = AtlasDiscovery(
        sources = AtlasDiscovery.defaultSources(),
        identity = AtlasDeviceIdentity(
            socManufacturer = socManufacturer,
            socModel = socModel,
            hardware = hardware,
            board = board,
            supportedAbis = listOf("arm64-v8a"),
            apiLevel = 34,
            kernelRelease = "5.15.0",
            isLowRamDevice = false,
            memoryClassMb = 512,
        ),
        transport = transport,
        clockMs = clock::nowMs,
    )
}
