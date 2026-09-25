/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadBudget
import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.ReadOnlyProbeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Recording tests (`P9.1`), run against a real filesystem.
 *
 * The whole promise of a fixture is *equivalence*: what a recorded device replays must be what the
 * device answered. That promise is not asserted by describing it — it is asserted here by running the
 * same requests twice, once over the real transport and once over the fixture captured from it, and
 * comparing the shipped boundary's results object by object. If a field is dropped, a cause is
 * rewritten or a listing is reordered, the two lists stop being equal and this fails.
 *
 * The origin is [AtlasFixtureOrigin.HOST] on purpose. These are this host's answers, and the label is
 * what stops a captured run from being quoted later as evidence about a phone.
 */
class AtlasFixtureRecorderTest {

    private val version = "atlas-test-1"
    private val thermalTemp = "/sys/class/thermal/thermal_zone0/temp"
    private val devfreqRoot = "/sys/class/devfreq"
    private val devfreqValue = "/sys/class/devfreq/gpu/cur_freq"
    private val missingChild = "/sys/class/devfreq/absent_freq"

    @Test
    fun `a captured run and its replay produce identical boundary results`() {
        val root = deviceTree()
        val live = AtlasFileReadTransport(root)
        val recorder = AtlasFixtureRecorder(live)
        val state = recorder.recordDirectory(recorder.start(AtlasFixtureOrigin.HOST, version), devfreqRoot)
        val captured = recorder.record(listOf(thermalTemp, devfreqValue, missingChild), AtlasFixtureOrigin.HOST, version)
        val fixture = captured.fixture.record(devfreqRoot, state.fixture.entries.getValue(devfreqRoot))

        val liveResults = run(live)
        val replayedResults = run(AtlasFixtureTransport(fixture))

        assertEquals("a replay must reproduce the run it was captured from", liveResults, replayedResults)
        assertEquals("and it must be named for where it came from", AtlasFixtureOrigin.HOST, fixture.origin)
        assertTrue("a real capture is readable back", AtlasFixtureCodec.decode(AtlasFixtureCodec.encode(fixture)).isReadable)
    }

    @Test
    fun `a captured fixture replays the absence the run proved`() {
        val root = deviceTree()
        val recorder = AtlasFixtureRecorder(AtlasFileReadTransport(root))
        val state = recorder.recordDirectory(recorder.start(AtlasFixtureOrigin.HOST, version), devfreqRoot)
        val fixture = recorder.record(listOf(missingChild), AtlasFixtureOrigin.HOST, version).fixture
            .record(devfreqRoot, state.fixture.entries.getValue(devfreqRoot))

        val access = ReadOnlyProbeAccess(AtlasFixtureTransport(fixture), AtlasReadBudget.DEFAULT, clockMs = { 0L })
        access.list(devfreqRoot)
        val rejected = access.read(request(missingChild)) as AtlasReadResult.Rejected

        assertEquals(AtlasFailure.ABSENT, rejected.failure)
        assertTrue("the recorded reason is the transport's own: ${rejected.reason}", rejected.reason.contains("ENOENT"))
    }

    @Test
    fun `a path that may not be addressed is skipped by name instead of quietly dropped`() {
        val recorder = AtlasFixtureRecorder(AtlasFileReadTransport(deviceTree()))
        val recording = recorder.record(
            paths = listOf("/data/local/tmp/dump", "/sys/class/devfreq/../thermal/temp", devfreqValue),
            origin = AtlasFixtureOrigin.HOST,
            catalogVersion = version,
        )

        assertEquals(listOf(devfreqValue), recording.fixture.paths())
        assertEquals(
            listOf("unapproved-anchor", "unsafe-path"),
            recording.skipped.map { it.reason },
        )
        assertEquals(
            "a skipped path is named, so a fixture cannot read later as \"the device did not answer\"",
            listOf("/data/local/tmp/dump", "/sys/class/devfreq/../thermal/temp"),
            recording.skipped.map { it.path },
        )
    }

    @Test
    fun `a directory is recorded only when a caller asks for a listing`() {
        val recorder = AtlasFixtureRecorder(AtlasFileReadTransport(deviceTree()))
        val state = recorder.recordDirectory(recorder.start(AtlasFixtureOrigin.HOST, version), devfreqRoot)

        val listing = state.fixture.answerFor(devfreqRoot) as AtlasFixtureAnswer.Listing

        assertEquals(listOf("gpu"), listing.names)
        assertTrue("recording a root does not enumerate anything by itself", state.skipped.isEmpty())
    }

    /**
     * The same requests in the same order, over whichever transport is handed in.
     *
     * The parent root is listed first because absence is only provable from an enumeration this run
     * performed — both runs must therefore be given the same chance to prove it.
     */
    private fun run(transport: AtlasReadTransport): List<AtlasReadResult> {
        val access = ReadOnlyProbeAccess(transport, AtlasReadBudget.DEFAULT, clockMs = { 0L })
        access.list(devfreqRoot)
        return listOf(
            access.read(request(thermalTemp)),
            access.read(request(devfreqValue)),
            access.read(request(missingChild)),
        )
    }

    private fun request(path: String): AtlasProbeRequest = AtlasProbeRequest(
        id = "cpu.cpufreq.cur",
        domain = AtlasDomain.CPU,
        providerId = "local",
        catalogVersion = version,
        sourceId = "reviewed-1",
        path = path,
        unit = AtlasUnit.KILO_HERTZ,
    )

    /** A temporary tree shaped like the device paths the catalog addresses. */
    private fun deviceTree(): Path {
        val root = Files.createTempDirectory("atlas-fixture-").also { it.toFile().deleteOnExit() }
        root.resolve("sys/class/thermal/thermal_zone0").createDirectories().resolve("temp").writeText("42000\n")
        val devfreq = root.resolve("sys/class/devfreq").createDirectories()
        devfreq.resolve("gpu").createDirectories().resolve("cur_freq").writeText("500000000\n")
        root.resolve("proc").createDirectories().resolve("cpuinfo").writeText("processor\t: 0\n")
        return root
    }
}
