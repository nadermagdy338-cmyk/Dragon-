/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget
import nd.max.core.hardware.AtlasTransportRead
import nd.max.core.hardware.ReadOnlyProbeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture codec and replay tests (`P9.1`).
 *
 * Two classes of test live here, and the second is the reason the file exists:
 *
 * 1. **The format refuses what it cannot vouch for.** An unknown schema, an unapproved path, an unsafe
 *    name, a duplicate key and an oversize artifact each produce a refusal rather than a partially
 *    understood device.
 * 2. **A replay cannot invent a device fact.** An unrecorded path is `UNKNOWN_CAUSE` and never
 *    `ABSENT`, and absence is still proven the only way the boundary accepts — from an enumeration
 *    this same run performed. Both are asserted *through* [ReadOnlyProbeAccess], because that is where
 *    the shipped rules live.
 */
class AtlasFixtureTest {

    private val version = "atlas-test-1"
    private val valuePath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
    private val cpufreqRoot = "/sys/devices/system/cpu/cpufreq"
    private val thermalPath = "/sys/class/thermal/thermal_zone0/temp"

    private fun fixture(vararg entries: Pair<String, AtlasFixtureAnswer>): AtlasFixture =
        AtlasFixture(AtlasFixtureOrigin.SYNTHETIC, version, entries.toMap())

    private fun request(path: String, id: String = "cpu.cpufreq.cur"): AtlasProbeRequest = AtlasProbeRequest(
        id = id,
        domain = AtlasDomain.CPU,
        providerId = "local",
        catalogVersion = version,
        sourceId = "reviewed-1",
        path = path,
        unit = AtlasUnit.KILO_HERTZ,
    )

    // ---- the format ------------------------------------------------------------------------------

    @Test
    fun `a round trip keeps the origin, the revision and every kind of answer`() {
        val original = fixture(
            valuePath to AtlasFixtureAnswer.Text("2400000\n", truncated = false),
            cpufreqRoot to AtlasFixtureAnswer.Listing(listOf("policy0", "policy4"), truncated = false),
            thermalPath to AtlasFixtureAnswer.Missing(AtlasFailure.ABSENT, "ENOENT: the interface does not exist"),
            "/sys/class/thermal" to AtlasFixtureAnswer.Canonical("/sys/class/thermal"),
            "/sys/class/kgsl/kgsl-3d0/gpuclk" to AtlasFixtureAnswer.Unresolvable,
        )

        val decoded = AtlasFixtureCodec.decode(AtlasFixtureCodec.encode(original))

        assertTrue("a fixture this build wrote must be readable by this build", decoded.isReadable)
        assertEquals(AtlasFixtureOrigin.SYNTHETIC, decoded.fixture?.origin)
        assertEquals(version, decoded.fixture?.catalogVersion)
        assertEquals(original.entries, decoded.fixture?.entries)
        assertEquals(
            "an encode of a decode is byte-identical, so a fixture in a diff shows real change only",
            AtlasFixtureCodec.encode(original),
            AtlasFixtureCodec.encode(decoded.fixture!!),
        )
    }

    @Test
    fun `an unknown schema is refused rather than read optimistically`() {
        val text = AtlasFixtureCodec.encode(fixture()).replace("\"schema\":1", "\"schema\":99")

        val decoded = AtlasFixtureCodec.decode(text)

        assertNull(decoded.fixture)
        assertEquals(AtlasFixtureDecodeFailure.UNSUPPORTED_SCHEMA, decoded.failure)
    }

    @Test
    fun `an artifact larger than the bound is refused before it is parsed`() {
        val huge = "x".repeat(AtlasFixture.MAX_BYTES + 1)

        val decoded = AtlasFixtureCodec.decode(huge)

        assertNull(decoded.fixture)
        assertEquals(AtlasFixtureDecodeFailure.OVERSIZE, decoded.failure)
    }

    @Test
    fun `text that is not a fixture is refused`() {
        assertEquals(AtlasFixtureDecodeFailure.MALFORMED, AtlasFixtureCodec.decode("not json").failure)
        assertEquals(AtlasFixtureDecodeFailure.MALFORMED, AtlasFixtureCodec.decode("{}").failure)
    }

    @Test
    fun `a well formed fixture naming an unapproved path is still refused`() {
        // The JSON parses perfectly. That is exactly why the anchor check cannot live in the parser's
        // syntax: a fixture is outside data, and it must not be the door that widens what we address.
        val text = """{"schema":1,"origin":"DEVICE","catalog":"$version","entries":[
            {"path":"/data/local/tmp/dump","kind":"text","text":"secret","truncated":false}]}"""

        val decoded = AtlasFixtureCodec.decode(text)

        assertNull(decoded.fixture)
        assertEquals(AtlasFixtureDecodeFailure.UNAPPROVED_PATH, decoded.failure)
    }

    @Test
    fun `a listing that carries an unsafe name is refused`() {
        val text = """{"schema":1,"origin":"DEVICE","catalog":"$version","entries":[
            {"path":"$cpufreqRoot","kind":"listing","names":["policy0","../etc"],"truncated":false}]}"""

        val decoded = AtlasFixtureCodec.decode(text)

        assertNull(decoded.fixture)
        assertEquals(AtlasFixtureDecodeFailure.UNSAFE_NAME, decoded.failure)
    }

    @Test
    fun `a repeated path is refused, because one path has one answer`() {
        val text = """{"schema":1,"origin":"DEVICE","catalog":"$version","entries":[
            {"path":"$thermalPath","kind":"text","text":"42000","truncated":false},
            {"path":"$thermalPath","kind":"text","text":"39000","truncated":false}]}"""

        assertEquals(AtlasFixtureDecodeFailure.MALFORMED, AtlasFixtureCodec.decode(text).failure)
    }

    @Test
    fun `a fixture cannot be constructed around an unapproved key`() {
        val thrown = runCatching {
            AtlasFixture(AtlasFixtureOrigin.DEVICE, version, mapOf("/etc/passwd" to AtlasFixtureAnswer.Unresolvable))
        }.exceptionOrNull()

        assertTrue("the constructor must fail closed: ${thrown?.message}", thrown is IllegalArgumentException)
    }

    @Test
    fun `a recorded failure carries a real cause and a single line`() {
        assertTrue(
            runCatching { AtlasFixtureAnswer.Missing(AtlasFailure.NONE, "why") }.exceptionOrNull()
                is IllegalArgumentException,
        )
        assertTrue(
            runCatching { AtlasFixtureAnswer.Missing(AtlasFailure.ABSENT, "two\nlines") }.exceptionOrNull()
                is IllegalArgumentException,
        )
    }

    // ---- the replay ------------------------------------------------------------------------------

    @Test
    fun `a replay of an unrecorded path is an unknown cause, never an absence`() {
        val transport = AtlasFixtureTransport(fixture())

        val read = transport.readText(thermalPath, 64)
        val listed = transport.listNames("/sys/class/thermal", 8)

        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (read as AtlasTransportRead.Failed).cause)
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, listed.cause)
        assertTrue("a fixture that stayed silent proves nothing about the device", listed.names.isEmpty())
    }

    @Test
    fun `a recorded value larger than the allowance replays as truncated, not as a smaller value`() {
        val transport = AtlasFixtureTransport(fixture(valuePath to AtlasFixtureAnswer.Text("1234567890", false)))

        val read = transport.readText(valuePath, 4) as AtlasTransportRead.Text

        assertTrue("a partial value must be flagged, exactly as the live transport flags it", read.truncated)
        assertEquals("1234", read.text)
    }

    @Test
    fun `a recorded absence keeps its cause and its reason verbatim`() {
        val reason = "ENOENT: the interface does not exist"
        val transport = AtlasFixtureTransport(fixture(thermalPath to AtlasFixtureAnswer.Missing(AtlasFailure.ABSENT, reason)))

        val read = transport.readText(thermalPath, 64) as AtlasTransportRead.Failed

        assertEquals(AtlasFailure.ABSENT, read.cause)
        assertEquals(reason, read.reason)
    }

    @Test
    fun `an enumeration replays its names and its cut`() {
        val transport = AtlasFixtureTransport(
            fixture(cpufreqRoot to AtlasFixtureAnswer.Listing(listOf("policy0", "policy1", "policy2"), truncated = false)),
        )

        val cut = transport.listNames(cpufreqRoot, 2)
        val whole = transport.listNames(cpufreqRoot, 8)

        assertEquals(listOf("policy0", "policy1"), cut.names)
        assertTrue("a cut listing must say it was cut", cut.truncated)
        assertEquals(listOf("policy0", "policy1", "policy2"), whole.names)
        assertFalse(whole.truncated)
    }

    @Test
    fun `a recorded resolution replays, and an unrecorded one stays the path itself`() {
        val transport = AtlasFixtureTransport(
            fixture(
                "/sys/class/thermal" to AtlasFixtureAnswer.Canonical("/sys/class/thermal"),
                "/sys/class/kgsl/kgsl-3d0/gpuclk" to AtlasFixtureAnswer.Unresolvable,
            ),
        )

        assertEquals("/sys/class/thermal", transport.canonicalPath("/sys/class/thermal", 8))
        assertNull("a recorded failure to resolve is a fact, not a gap", transport.canonicalPath("/sys/class/kgsl/kgsl-3d0/gpuclk", 8))
        assertEquals(thermalPath, transport.canonicalPath(thermalPath, 8))
    }

    @Test
    fun `the boundary accepts an absence a replayed enumeration proved, and refuses one it did not`() {
        val recorded = fixture(
            cpufreqRoot to AtlasFixtureAnswer.Listing(listOf("policy0"), truncated = false),
            "$cpufreqRoot/scaling_cur_freq" to AtlasFixtureAnswer.Missing(
                AtlasFailure.ABSENT,
                "ENOENT: the interface does not exist",
            ),
            thermalPath to AtlasFixtureAnswer.Missing(
                AtlasFailure.ABSENT,
                "ENOENT: the interface does not exist",
            ),
        )
        val access = ReadOnlyProbeAccess(
            transport = AtlasFixtureTransport(recorded),
            budget = AtlasReadBudget.DEFAULT,
            clockMs = { 0L },
        )

        // Proven absence: the parent was enumerated by this very run, and the child was not in it.
        access.list(cpufreqRoot)
        val proven = access.read(request("$cpufreqRoot/scaling_cur_freq")) as AtlasReadResult.Rejected
        assertEquals(AtlasFailure.ABSENT, proven.failure)

        // Unproven absence: nothing enumerated this parent, so the same recorded cause is not enough —
        // the fixture asserts it, the boundary still refuses to repeat it.
        val unproven = access.read(request(thermalPath, id = "thermal.zone0.temp")) as AtlasReadResult.Rejected
        assertEquals(
            "an absence must still be proven, even when the fixture asserts it",
            AtlasFailure.UNKNOWN_CAUSE,
            unproven.failure,
        )
    }

    @Test
    fun `the artifact bound is a real bound and the entry ceiling matches one job`() {
        assertEquals(512, AtlasFixture.MAX_ENTRIES)
        assertEquals(AtlasReadBudget.DEFAULT.maxEntriesTotal, AtlasFixture.MAX_ENTRIES_PER_JOB)
        assertTrue(AtlasFixtureAnswer.MAX_TEXT_CHARS <= AtlasReadBudget.DEFAULT.maxReviewedProcBytes)
    }
}
