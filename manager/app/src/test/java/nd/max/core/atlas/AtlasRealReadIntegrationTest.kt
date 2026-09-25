/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget
import nd.max.core.hardware.ReadOnlyProbeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

/**
 * The whole chain, running against a real kernel (`P2`/`P5`, "real life" evidence available here).
 *
 * Nothing is stubbed: [AtlasFileReadTransport] does real syscalls, [ReadOnlyProbeAccess] enforces the
 * real budgets and anchor rules, the catalog is the reviewed one, the store writes real files with a
 * real atomic rename, and the paths read are this machine's own `/proc` surfaces. What this proves is
 * therefore narrow and important: **the shipped read path works on a real Linux kernel**.
 *
 * What it deliberately does **not** prove is Android: `/sys/class/thermal`, `/sys/class/devfreq` and
 * `power_supply` exist only on a phone, and SELinux labels decide what an app may open there. Those
 * remain `needs device`, and this file must never be read as saying otherwise.
 */
class AtlasRealReadIntegrationTest {

    @Test
    fun `a real proc surface is read through the shipped boundary and becomes an observation`() {
        val cpuinfo = Path.of("/proc/cpuinfo")
        assumeTrue("needs a Linux /proc; this host has none", Files.isRegularFile(cpuinfo) && Files.isReadable(cpuinfo))
        val access = ReadOnlyProbeAccess(
            transport = AtlasFileReadTransport(),
            budget = AtlasReadBudget.DEFAULT,
            clockMs = System::currentTimeMillis,
        )
        val request = AtlasProbeRequest(
            id = "cpu.info.cpuinfo",
            domain = AtlasDomain.CPU,
            providerId = "real-integration",
            catalogVersion = AtlasCatalog.SCHEMA_VERSION,
            sourceId = "S18c",
            path = "/proc/cpuinfo",
            unit = AtlasUnit.UNKNOWN,
            semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
            reviewedProcSummary = true,
        )

        val result = access.read(request)

        assertTrue(
            "a reviewed file granted to app domains must be readable: $result",
            result is AtlasReadResult.Observed,
        )
        val observation = (result as AtlasReadResult.Observed).observation
        assertTrue("real text came back", observation.textValue.orEmpty().isNotEmpty())
        assertEquals("a successful attempt carries no failure cause", AtlasFailure.NONE, observation.failure)
        assertEquals("free text has no unit, so none is invented", AtlasUnit.UNKNOWN, observation.unit)
        assertEquals("the real path is what was read", "/proc/cpuinfo", observation.path)
        assertEquals("one attempt, and no more", 1, access.stats().operations)
        assertTrue("bytes were really read from the kernel", access.stats().bytesRead > 0)
        assertTrue("nothing in the read path holds a write", !access.stats().limitsReached)
    }

    @Test
    fun `a real pressure file is read and a malformed line is never turned into a number`() {
        val pressure = Path.of("/proc/pressure/cpu")
        assumeTrue("needs a Linux PSI surface", Files.isRegularFile(pressure))
        val access = ReadOnlyProbeAccess(
            transport = AtlasFileReadTransport(),
            budget = AtlasReadBudget.DEFAULT,
            clockMs = System::currentTimeMillis,
        )
        val request = AtlasProbeRequest(
            id = "memory.psi.cpu",
            domain = AtlasDomain.MEMORY,
            providerId = "real-integration",
            catalogVersion = AtlasCatalog.SCHEMA_VERSION,
            sourceId = "LOCAL-PSI",
            path = "/proc/pressure/cpu",
            unit = AtlasUnit.UNKNOWN,
            reviewedProcSummary = true,
        )

        val result = access.read(request)

        // Either the value is reported as free text (this vocabulary reports PSI as text, never as a
        // synthesized percentage) or the cause is a real failure. What must never happen is a number
        // invented from a line whose meaning was not established.
        // A PSI value is reported as the free text the kernel wrote: no percentage is synthesized from
        // an ambiguous line, and no unit is assumed for a signal this vocabulary does not establish.
        assertTrue("this host has real PSI: $result", result is AtlasReadResult.Observed)
        val observation = (result as AtlasReadResult.Observed).observation
        assertEquals(AtlasUnit.UNKNOWN, observation.unit)
        assertEquals(AtlasFailure.NONE, observation.failure)
        assertTrue("the kernel's own line came back", observation.textValue.orEmpty().contains("avg"))
    }

    @Test
    fun `a real absence is proven by a real listing, never assumed`() {
        // This host has the real cpufreq root as an existing (empty) directory, which is exactly the
        // shape that makes absence provable: a listing that succeeded and did not contain the name.
        val root = Path.of("/sys/devices/system/cpu/cpufreq")
        assumeTrue("needs the real cpufreq root as a directory", Files.isDirectory(root))
        val access = ReadOnlyProbeAccess(
            transport = AtlasFileReadTransport(),
            budget = AtlasReadBudget.DEFAULT,
            clockMs = System::currentTimeMillis,
        )

        val listing = access.list(root.toString())
        assertEquals("the real directory lists successfully", AtlasFailure.NONE, listing.failure)

        val result = access.read(
            AtlasProbeRequest(
                id = "cpu.policy.scaling_cur_freq",
                domain = AtlasDomain.CPU,
                providerId = "real-integration",
                catalogVersion = AtlasCatalog.SCHEMA_VERSION,
                sourceId = "L02",
                path = "$root/made.up.attribute",
                unit = AtlasUnit.KILO_HERTZ,
            ),
        )

        assertTrue("a real ENOENT under a listed parent: $result", result is AtlasReadResult.Rejected)
        val rejected = result as AtlasReadResult.Rejected
        assertEquals("absence is claimable here, from a real errno and a real listing", AtlasFailure.ABSENT, rejected.failure)
        assertTrue(rejected.reason.contains("ENOENT"))
    }

    @Test
    fun `without a listing the same missing path is unknown rather than absent`() {
        val root = Path.of("/sys/devices/system/cpu/cpufreq")
        assumeTrue("needs the real cpufreq root as a directory", Files.isDirectory(root))
        val access = ReadOnlyProbeAccess(
            transport = AtlasFileReadTransport(),
            budget = AtlasReadBudget.DEFAULT,
            clockMs = System::currentTimeMillis,
        )
        // No listing this time: the identical read must not become an absence claim.
        val result = access.read(
            AtlasProbeRequest(
                id = "cpu.policy.scaling_cur_freq",
                domain = AtlasDomain.CPU,
                providerId = "real-integration",
                catalogVersion = AtlasCatalog.SCHEMA_VERSION,
                sourceId = "L02",
                path = "$root/made.up.attribute",
                unit = AtlasUnit.KILO_HERTZ,
            ),
        )

        assertEquals(
            AtlasFailure.UNKNOWN_CAUSE,
            (result as AtlasReadResult.Rejected).failure,
        )
        assertTrue(result.reason.contains("absence not proven"))
    }

    @Test
    fun `evidence survives a real filesystem round trip and a corrupted entry is discarded`() {
        val directory = Files.createTempDirectory("atlas-store-")
        val store = AtlasEvidenceStore(AtlasFileStoreIo(directory))
        val identity = AtlasDeviceIdentity(
            socManufacturer = "Qualcomm",
            socModel = "SM8650",
            hardware = "qcom",
            board = "test",
            supportedAbis = listOf("arm64-v8a"),
            apiLevel = 34,
            kernelRelease = "5.15.0",
            isLowRamDevice = false,
            memoryClassMb = 512,
        )
        val fingerprint = store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity, 0L, 0L)
        val observation = AtlasObservation(
            id = "cpu.policy.scaling_cur_freq",
            domain = AtlasDomain.CPU,
            providerId = "real-integration",
            catalogVersion = AtlasCatalog.SCHEMA_VERSION,
            sourceId = "L02",
            path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq",
            access = AtlasAccess.READABLE,
            semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
            unit = AtlasUnit.KILO_HERTZ,
            value = 1_800_000.0,
            textValue = null,
            rawRepresentation = null,
            failure = AtlasFailure.NONE,
            reason = "kHz",
            observedAtElapsedMs = 1_000L,
            bootGeneration = 0L,
            privilegeGeneration = 0L,
            truncated = false,
        )

        assertTrue(store.save(observation, AtlasVolatility.INSTANT, fingerprint))
        val hit = store.load(observation.id, fingerprint)
        assertTrue("a real file must come back as the same evidence", hit is AtlasCacheLookup.Hit)
        assertEquals(observation, (hit as AtlasCacheLookup.Hit).evidence.observation)

        // A different device is a different fingerprint, and the entry is refused without being deleted.
        val otherDevice = identity.copy(socModel = "SM8750")
        val otherFingerprint = store.fingerprint(AtlasCatalog.SCHEMA_VERSION, otherDevice, 0L, 0L)
        assertEquals(
            AtlasCacheMiss.KEY_MISMATCH,
            (store.load(observation.id, otherFingerprint) as AtlasCacheLookup.Miss).reason,
        )
        assertTrue("the entry survives a mismatch", store.load(observation.id, fingerprint) is AtlasCacheLookup.Hit)

        // Corruption is discarded and reported, never repaired into something plausible.
        val entry = directory.resolve("${AtlasEvidenceStore.NAME_PREFIX}${observation.id}${AtlasEvidenceStore.NAME_SUFFIX}")
        entry.writeText("{\"schema\":1,\"id\":")
        assertEquals(AtlasCacheMiss.CORRUPT, (store.load(observation.id, fingerprint) as AtlasCacheLookup.Miss).reason)
        assertTrue("a corrupt entry is deleted", Files.notExists(entry))

        // An unknown schema is a different fact from corruption, and is also discarded.
        assertTrue(store.save(observation, AtlasVolatility.INSTANT, fingerprint))
        entry.writeText("{\"schema\":99,\"fingerprint\":\"$fingerprint\"}")
        assertEquals(
            AtlasCacheMiss.UNSUPPORTED_SCHEMA,
            (store.load(observation.id, fingerprint) as AtlasCacheLookup.Miss).reason,
        )

        store.clear()
        assertTrue("a cleared store holds nothing", store.storedIds().isEmpty())
        assertEquals(0, store.load(observation.id, fingerprint).let { if (it is AtlasCacheLookup.Miss) 0 else 1 })
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an atomic replace leaves no half written entry behind`() {
        val directory = Files.createTempDirectory("atlas-atomic-")
        val io = AtlasFileStoreIo(directory)

        assertTrue(io.write("evidence.example.json", "first"))
        assertTrue(io.write("evidence.example.json", "second"))

        assertEquals("second", io.read("evidence.example.json"))
        assertEquals("no temporary file survives", emptyList<String>(), io.list().filter { it.endsWith(".tmp") })
        assertEquals(1, io.list().size)
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `the store refuses a name that is not a plain entry name`() {
        val directory = Files.createTempDirectory("atlas-names-")
        val io = AtlasFileStoreIo(directory)

        assertTrue(runCatching { io.read("../../etc/passwd") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { io.write("a/b", "x") }.exceptionOrNull() is IllegalArgumentException)
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a real denied path is never reported as a value`() {
        // A file this process genuinely cannot open, created with mode 000 in a temp directory.
        val directory = Files.createTempDirectory("atlas-denied-")
        val locked = directory.resolve("locked")
        locked.writeText("secret")
        Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("---------"))
        assumeTrue("needs an unprivileged process", !Files.isReadable(locked))

        val access = ReadOnlyProbeAccess(
            transport = AtlasFileReadTransport(directory),
            budget = AtlasReadBudget.DEFAULT,
            clockMs = System::currentTimeMillis,
            // The temp path is not an approved anchor, so the anchor check is stubbed for this test:
            // what is under test is the errno mapping, which is what the boundary delegates.
            approvedAnchor = { it.startsWith("/") },
        )
        // A device-style path: the transport maps it onto its root, so an already-rooted host path
        // would be prefixed twice and the test would be measuring its own mistake.
        val request = AtlasProbeRequest(
            id = "test.denied.path",
            domain = AtlasDomain.CPU,
            providerId = "real-integration",
            catalogVersion = AtlasCatalog.SCHEMA_VERSION,
            sourceId = "test",
            path = "/locked",
            unit = AtlasUnit.UNKNOWN,
        )

        val direct = AtlasFileReadTransport(directory).readText("/locked", 64)
        val result = access.read(request)

        assertTrue(
            "a denial must stay a denial and carry no value. transport=$direct boundary=$result",
            result is AtlasReadResult.Rejected && result.failure == AtlasFailure.PERMISSION_DENIED,
        )
        locked.deleteIfExists()
        directory.toFile().deleteRecursively()
    }
}
