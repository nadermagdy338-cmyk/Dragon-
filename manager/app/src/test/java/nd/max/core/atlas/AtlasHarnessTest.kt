/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.atlas.support.AtlasBudgets
import nd.max.core.atlas.support.AtlasCanaries
import nd.max.core.atlas.support.AtlasClock
import nd.max.core.atlas.support.AtlasFakeAudit
import nd.max.core.atlas.support.AtlasFakeFile
import nd.max.core.atlas.support.AtlasFakeTransport
import nd.max.core.atlas.support.AtlasGenerationFence
import nd.max.core.atlas.support.AtlasProbeKinds
import nd.max.core.atlas.support.AtlasUnrecordedFakeTransport
import nd.max.core.hardware.ReadOnlyProbeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P0` self-tests. The harness is only worth having if it can fail, so the first tests prove that the
 * audit detects a probe that does not record, that a slow read exceeds its deadline, and that the
 * privacy checker fires on a poisoned blob.
 *
 * Since `P2` these tests drive the **real** bounded access over a fake transport: only I/O is faked,
 * so the budgets and the absence rule being asserted here are the ones that ship.
 */
class AtlasHarnessTest {

    // ---- recording, zero mutation, negative control -------------------------------------------------

    @Test
    fun `fake records every attempt and only exposes read operations`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/class/thermal", listOf("thermal_zone0"))
            .putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("42000"))
        val access = access(transport, clock)

        access.read(request("/sys/class/thermal/thermal_zone0/temp", AtlasUnit.MILLI_CELSIUS))
        access.list("/sys/class/thermal")

        assertEquals(
            listOf(AtlasProbeKinds.CANONICAL, AtlasProbeKinds.READ, AtlasProbeKinds.LIST),
            transport.operations.map { it.kind },
        )
        assertTrue(transport.operations.all { it.kind in AtlasProbeKinds.SURFACE })
        assertTrue(AtlasFakeAudit.mutations(transport).isEmpty())
        assertTrue(AtlasFakeAudit.problems(transport, expectedOperations = 3).isEmpty())
    }

    @Test
    fun `audit rejects a probe that answers without recording the negative control`() {
        val quiet = AtlasUnrecordedFakeTransport(AtlasClock())
        quiet.answer("/sys/class/thermal/thermal_zone0/temp")

        val problems = AtlasFakeAudit.problems(quiet, expectedOperations = 1)
        assertFalse("an unrecorded probe must be rejected", problems.isEmpty())
    }

    // ---- failure causes stay distinct ---------------------------------------------------------------

    @Test
    fun `absence is reported only from a readable listing`() {
        val clock = AtlasClock()
        val path = "/sys/class/thermal/thermal_zone9/temp"

        val listed = AtlasFakeTransport(clock)
            .putDirectory("/sys/class/thermal/thermal_zone9", listOf("type", "temp_policy"))
            .putFile(path, AtlasFakeFile(raw = null, exists = false))
        val listedAccess = access(listed, clock)
        listedAccess.list("/sys/class/thermal/thermal_zone9")
        val absent = listedAccess.read(request(path, AtlasUnit.MILLI_CELSIUS))
        assertEquals(AtlasFailure.ABSENT, (absent as AtlasReadResult.Rejected).failure)

        val unlistedTransport = AtlasFakeTransport(clock).putFile(path, AtlasFakeFile(raw = null, exists = false))
        val unproven = access(unlistedTransport, clock).read(request(path, AtlasUnit.MILLI_CELSIUS))
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (unproven as AtlasReadResult.Rejected).failure)
    }

    @Test
    fun `denied malformed oversized and non finite values are different outcomes`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/devfreq/denied", AtlasFakeFile("1", readable = false))
            .putFile("/sys/class/devfreq/empty", AtlasFakeFile(null))
            .putFile("/sys/class/devfreq/huge", AtlasFakeFile("1".repeat(64), maxBytes = 16))
            .putFile("/sys/class/devfreq/nan", AtlasFakeFile("NaN"))
        val access = access(transport, clock)

        assertEquals(AtlasFailure.PERMISSION_DENIED, failureOf(access, "/sys/class/devfreq/denied"))
        assertEquals(AtlasFailure.MALFORMED, failureOf(access, "/sys/class/devfreq/empty"))
        assertEquals(AtlasFailure.BUDGET_EXCEEDED, failureOf(access, "/sys/class/devfreq/huge"))
        assertEquals(AtlasFailure.MALFORMED, failureOf(access, "/sys/class/devfreq/nan"))
    }

    @Test
    fun `a slow interface exceeds its own deadline and never returns a partial value`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/devfreq/slow", AtlasFakeFile("42000", slowMs = AtlasBudgets.OPERATION_DEADLINE_MS + 1L))

        val result = access(transport, clock).read(request("/sys/class/devfreq/slow", AtlasUnit.MILLI_CELSIUS))
        assertEquals(AtlasFailure.TIMED_OUT, (result as AtlasReadResult.Rejected).failure)
    }

    // ---- budgets -----------------------------------------------------------------------------------

    @Test
    fun `listing is capped and marked truncated instead of reporting absence`() {
        val clock = AtlasClock()
        val names = (0 until 200).map { index -> "f%03d".format(index) }
        val transport = AtlasFakeTransport(clock).putDirectory("/sys/class/devfreq", names)

        val listing = access(transport, clock).list("/sys/class/devfreq")
        assertEquals(AtlasBudgets.MAX_ENTRIES_PER_ROOT, listing.names.size)
        assertTrue(listing.truncated)
        assertEquals(AtlasFailure.NONE, listing.failure)
    }

    @Test
    fun `unsafe basenames are dropped and reported as truncated coverage`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/class/devfreq", listOf("ok0", "bad/name", "..", "ok1"))

        val listing = access(transport, clock).list("/sys/class/devfreq")
        assertEquals(listOf("ok0", "ok1"), listing.names)
        assertTrue(listing.truncated)
    }

    @Test
    fun `aggregate entry budget stops further enumeration`() {
        val clock = AtlasClock()
        val names = (0 until AtlasBudgets.MAX_ENTRIES_PER_ROOT).map { index -> "f%03d".format(index) }
        val roots = listOf(
            "/sys/class/thermal",
            "/sys/class/power_supply",
            "/sys/class/kgsl",
            "/sys/block",
            "/sys/class/devfreq",
        )
        val transport = AtlasFakeTransport(clock)
        roots.forEach { root -> transport.putDirectory(root, names) }

        val access = access(transport, clock)
        val outcomes = roots.map { root -> access.list(root).failure }
        assertEquals(AtlasFailure.NONE, outcomes[3])
        assertEquals(AtlasFailure.BUDGET_EXCEEDED, outcomes[4])
        assertTrue(access.list(roots[4]).names.isEmpty())
    }

    // ---- clock and generation fence ------------------------------------------------------------------

    @Test
    fun `clock boundaries backward time and the publication fence are deterministic`() {
        val clock = AtlasClock()

        assertFalse("first probe at zero is fresh", clock.isStale(0L, AtlasBudgets.NEGATIVE_TTL_MS))
        clock.advance(AtlasBudgets.NEGATIVE_TTL_MS)
        assertFalse("the exact retry boundary is still inside the window", clock.isStale(0L, AtlasBudgets.NEGATIVE_TTL_MS))
        clock.advance(1L)
        assertTrue("one millisecond past the boundary is stale", clock.isStale(0L, AtlasBudgets.NEGATIVE_TTL_MS))

        clock.rewind(AtlasBudgets.NEGATIVE_TTL_MS + 10L)
        assertTrue("a timestamp from the future is not fresh", clock.isStale(clock.nowMs() + 10L, AtlasBudgets.POSITIVE_TTL_MS))

        val generation = clock.bumpGeneration()
        assertTrue(AtlasGenerationFence.mayPublish(generation, generation))
        assertFalse(AtlasGenerationFence.mayPublish(generation - 1L, generation))
    }

    // ---- privacy canaries -----------------------------------------------------------------------------

    @Test
    fun `the canary checker fires on a poisoned blob and stays quiet on clean text`() {
        assertEquals(AtlasCanaries.ALL.size, AtlasCanaries.leaks(AtlasCanaries.poisonedBlob()).size)
        assertEquals(listOf(AtlasCanaries.FAKE_IMEI), AtlasCanaries.leaks("imei=${AtlasCanaries.FAKE_IMEI}"))
        assertTrue(AtlasCanaries.leakFree("catalogVersion=atlas-catalog-1 reason=permission-denied"))
        assertTrue(AtlasCanaries.leakFree(null))
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private fun access(transport: AtlasFakeTransport, clock: AtlasClock): ReadOnlyProbeAccess =
        ReadOnlyProbeAccess(
            transport = transport,
            clockMs = clock::nowMs,
            currentGeneration = clock::generation,
        )

    private fun request(path: String, unit: AtlasUnit): AtlasProbeRequest = AtlasProbeRequest(
        id = "test.observation",
        domain = AtlasDomain.THERMAL,
        providerId = "fake",
        catalogVersion = AtlasCatalog.SCHEMA_VERSION,
        sourceId = "L05",
        path = path,
        unit = unit,
    )

    private fun failureOf(access: ReadOnlyProbeAccess, path: String): AtlasFailure {
        val result = access.read(request(path, AtlasUnit.MILLI_CELSIUS))
        if (result is AtlasReadResult.Observed) {
            return AtlasFailure.NONE
        }
        return (result as AtlasReadResult.Rejected).failure
    }
}
