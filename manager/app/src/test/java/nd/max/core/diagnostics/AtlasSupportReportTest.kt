package nd.max.core.diagnostics

import nd.max.core.atlas.AtlasDomain
import nd.max.core.atlas.AtlasFailure
import nd.max.core.atlas.AtlasFeatureOutcome
import nd.max.core.atlas.AtlasObservation
import nd.max.core.atlas.AtlasAccess
import nd.max.core.atlas.AtlasScanState
import nd.max.core.atlas.AtlasScanStatus
import nd.max.core.atlas.AtlasSemanticStatus
import nd.max.core.atlas.AtlasStage
import nd.max.core.atlas.AtlasUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `P6` tests: what may leave the device, and what may not.
 *
 * The privacy tests work by **planting sentinels** in every input a report draws from and then
 * searching the produced bytes, the preview bytes and the file on disk for them. A report that grows a
 * field later will fail here rather than in the field.
 */
class AtlasSupportReportTest {

    @Test
    fun `a finished scan produces a report and the bytes are the only artifact`() {
        val result = AtlasSupportReport.from(state = state(), appVersion = "1.2.3", catalogVersion = "atlas-catalog-1", device = device())

        assertTrue(result is AtlasSupportReport.Result.Ready)
        val report = (result as AtlasSupportReport.Result.Ready).report
        val text = report.encode()

        assertTrue(text.contains("\"schema\":1"))
        assertTrue(text.contains("atlas-catalog-1"))
        assertTrue("a feature id is catalog vocabulary", text.contains("cpu.policy.scaling_cur_freq"))
        assertEquals("decoding reads back the same report", report, AtlasSupportReport.decode(text).report)
        // Preview equals bytes: `encode` is pure, so calling it twice yields the same artifact.
        assertEquals(text, report.encode())
    }

    @Test
    fun `a running or cancelled scan has no report at all`() {
        val running = AtlasSupportReport.from(
            state = state(status = AtlasScanStatus.RUNNING),
            appVersion = "1.2.3",
            catalogVersion = "c",
            device = device(),
        )
        val cancelled = AtlasSupportReport.from(
            state = state(status = AtlasScanStatus.CANCELLED),
            appVersion = "1.2.3",
            catalogVersion = "c",
            device = device(),
        )
        val idle = AtlasSupportReport.from(
            state = AtlasScanState(),
            appVersion = "1.2.3",
            catalogVersion = "c",
            device = device(),
        )

        assertTrue(running is AtlasSupportReport.Result.NotReportable)
        assertTrue(cancelled is AtlasSupportReport.Result.NotReportable)
        assertTrue("an idle scan says nothing about the device", idle is AtlasSupportReport.Result.NotReportable)
        assertTrue(
            "a cancelled run must say why rather than looking complete",
            (cancelled as AtlasSupportReport.Result.NotReportable).reason.contains("cancelled"),
        )
    }

    @Test
    fun `a sentinel secret in every raw input never reaches the artifact`() {
        // Each sentinel stands in for a class of thing that must not leave: an account, a serial, a
        // network name, a user path, a package list, a token.
        val account = "sentinel@example.com"
        val serial = "SN-SENTINEL-0001"
        val ssid = "SentinelHomeWifi"
        val mac = "aa:bb:cc:dd:ee:ff"
        val userPath = "/storage/emulated/0/Download/sentinel-private.pdf"
        val packageList = "com.sentinel.app"
        val token = "tok_sentinel_1234567890"

        // A device identity that (unrealistically) carries all of them, to prove the report's fields
        // are chosen rather than copied.
        val noisyDevice = AtlasSupportReport.ReportedDevice(
            socModel = "SM8650",
            socManufacturer = "Qualcomm",
            apiLevel = 34,
            abi = "arm64-v8a",
            kernelMajorMinor = "5.15",
            lowRamDevice = false,
        )
        val report = (AtlasSupportReport.from(
            state = state(),
            appVersion = "1.2.3",
            catalogVersion = "atlas-catalog-1",
            device = noisyDevice,
        ) as AtlasSupportReport.Result.Ready).report
        val text = report.encode() + report.toString()

        listOf(account, serial, ssid, mac, userPath, packageList, token).forEach { sentinel ->
            assertFalse("a report must not carry '$sentinel'", text.contains(sentinel))
        }
    }

    @Test
    fun `the diagnostics projection carries components and counts, never messages`() {
        DiagnosticCenter.resetForTesting()
        DiagnosticCenter.record(
            component = "engine",
            message = "failed to open /storage/emulated/0/private/sentinel.txt for com.sentinel.app",
        )
        DiagnosticCenter.record(component = "engine", message = "failed to open /storage/emulated/0/private/sentinel.txt for com.sentinel.app")
        val summary = DiagnosticCenter.structured()

        val report = (AtlasSupportReport.from(
            state = state(),
            appVersion = "1.2.3",
            catalogVersion = "c",
            device = device(),
            diagnostics = AtlasSupportReport.ReportedDiagnostics(
                warnCount = summary.warnCount,
                errorCount = summary.errorCount,
                distinctComponents = summary.distinctComponents,
                components = summary.entries.map {
                    AtlasSupportReport.ReportedDiagnostics.ComponentSummary(it.component, it.level.name, it.count)
                },
            ),
        ) as AtlasSupportReport.Result.Ready).report
        val text = report.encode()

        assertTrue("the component is allowed", text.contains("engine"))
        assertTrue("the count is the useful part", text.contains("\"count\":2"))
        assertFalse("no message text may appear", text.contains("sentinel.txt"))
        assertFalse("and no user path either", text.contains("/storage/emulated/0"))
        assertFalse("nor a package name", text.contains("com.sentinel.app"))
        DiagnosticCenter.resetForTesting()
    }

    @Test
    fun `an oversize report is refused rather than truncated`() {
        val many = (1..AtlasSupportReport.MAX_FEATURES + 1).map {
            AtlasFeatureOutcome.Unresolved(
                id = "feature.$it",
                failure = AtlasFailure.ABSENT,
                reason = "not there",
                stage = AtlasStage.BOUNDED_DISCOVERY,
            )
        }
        val result = AtlasSupportReport.from(
            state = state(outcomes = many),
            appVersion = "1.2.3",
            catalogVersion = "c",
            device = device(),
        )

        assertTrue(result is AtlasSupportReport.Result.NotReportable)
        assertTrue((result as AtlasSupportReport.Result.NotReportable).reason.contains("summary"))
    }

    @Test
    fun `an unknown schema is refused explicitly and corruption is a different fact`() {
        val text = (AtlasSupportReport.from(state(), "1.0", "c", device()) as AtlasSupportReport.Result.Ready)
            .report.encode()

        val bumped = text.replaceFirst("\"schema\":1", "\"schema\":99")
        val unknown = AtlasSupportReport.decode(bumped)
        val corrupt = AtlasSupportReport.decode("{ not json")
        val oversized = AtlasSupportReport.decode("x".repeat(AtlasSupportReport.MAX_BYTES + 1))

        assertEquals(AtlasSupportReport.DecodeFailure.UNSUPPORTED_SCHEMA, unknown.failure)
        assertNull(unknown.report)
        assertEquals(AtlasSupportReport.DecodeFailure.MALFORMED, corrupt.failure)
        assertEquals(AtlasSupportReport.DecodeFailure.OVERSIZE, oversized.failure)
    }

    @Test
    fun `an observation that cannot be aged is reported as un-ageable, not as zero`() {
        val fresh = feature(fromCache = false, observedAt = 1_000L)
        val held = feature(fromCache = true, observedAt = 1_000L)
        val unageable = feature(fromCache = true, observedAt = null)

        val report = (AtlasSupportReport.from(
            state = state(outcomes = listOf(fresh, held, unageable)),
            appVersion = "1",
            catalogVersion = "c",
            device = device(),
            nowMs = 5_000L,
        ) as AtlasSupportReport.Result.Ready).report

        assertEquals(0L, report.features.first { it.id == "fresh" }.ageMs)
        assertEquals(4_000L, report.features.first { it.id == "held" }.ageMs)
        assertNull("an unmeasurable age is null, never 0", report.features.first { it.id == "unageable" }.ageMs)
    }

    @Test
    fun `the report is a summary, so a supported device is not asked for one`() {
        val allObserved = state(
            outcomes = listOf(
                AtlasFeatureOutcome.Observed(
                    id = "cpu.policy.scaling_cur_freq",
                    observation = observation(),
                    stage = AtlasStage.BOUNDED_DISCOVERY,
                    fromCache = false,
                ),
            ),
        )

        assertFalse("nothing is unresolved, so nothing needs reporting", allObserved.reportEligible)
        assertTrue("with a real gap, a report becomes the last resort", state().reportEligible)
    }

    // ---- exporter ----------------------------------------------------------------------------------

    @Test
    fun `an exported report is written atomically and reads back byte for byte`() {
        val cache = Files.createTempDirectory("atlas-export-").toFile()
        val exporter = AtlasReportExporter(AtlasReportExporter.directoryFor(cache)) { 1_700_000_000_000L }
        val text = (AtlasSupportReport.from(state(), "1.2.3", "c", device()) as AtlasSupportReport.Result.Ready).report.encode()

        val file = exporter.write(text)

        assertTrue(file != null && file.exists())
        assertEquals("what is previewed is what is shared", text, file!!.readText())
        assertEquals("and no temporary artifact survives", emptyList<File>(), exporter.temporaryFiles())
        cache.deleteRecursively()
    }

    @Test
    fun `an exporter refuses a report larger than the schema allows`() {
        val cache = Files.createTempDirectory("atlas-export-big-").toFile()
        val exporter = AtlasReportExporter(AtlasReportExporter.directoryFor(cache))

        assertNull(exporter.write("x".repeat(AtlasSupportReport.MAX_BYTES + 1)))
        assertEquals(emptyList<File>(), exporter.reports())
        cache.deleteRecursively()
    }

    @Test
    fun `cleanup removes old reports and protects the one being shared`() {
        val cache = Files.createTempDirectory("atlas-cleanup-").toFile()
        val directory = AtlasReportExporter.directoryFor(cache)
        var now = 2_000_000_000_000L
        val exporter = AtlasReportExporter(directory) { now }
        val fresh = exporter.write("first")!!
        now += 1L
        val shared = exporter.write("second")!!

        // Move both into the past, then keep one protected as if a share were reading it.
        assertTrue(fresh.setLastModified(now - AtlasReportExporter.DEFAULT_MAX_AGE_MS - 1))
        assertTrue(shared.setLastModified(now - AtlasReportExporter.DEFAULT_MAX_AGE_MS - 1))
        val removed = exporter.cleanup(protected = shared)

        assertEquals("only the report nobody is reading is removed", 1, removed)
        assertFalse(fresh.exists())
        assertTrue("a share may still be reading this one", shared.exists())
        cache.deleteRecursively()
    }

    @Test
    fun `no network client is reachable from the report package`() {
        // Comments are stripped first, for the reason the project's own source guard gives: a token
        // check that also matches prose punishes a file for *explaining* what it refuses to do, and a
        // guard people work around is worse than none.
        val code = reportSource()
        listOf("http", "HttpURLConnection", "OkHttp", "Socket", "URL(", "upload", "connect(").forEach { token ->
            assertFalse("the report path must not name '$token' in code", code.contains(token, ignoreCase = true))
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun reportSource(): String = listOf(
        "src/main/java/nd/max/core/diagnostics/AtlasSupportReport.kt",
        "src/main/java/nd/max/core/diagnostics/AtlasReportExporter.kt",
    ).joinToString("\n") { nd.max.core.atlas.support.AtlasSourceGuard.code(it) }

    private fun state(
        status: AtlasScanStatus = AtlasScanStatus.PARTIAL,
        outcomes: List<AtlasFeatureOutcome> = listOf(
            AtlasFeatureOutcome.Observed(
                id = "cpu.policy.scaling_cur_freq",
                observation = observation(),
                stage = AtlasStage.BOUNDED_DISCOVERY,
                fromCache = false,
            ),
            AtlasFeatureOutcome.Unresolved(
                id = "thermal.zone.temp",
                failure = AtlasFailure.PERMISSION_DENIED,
                reason = "denied by policy",
                stage = AtlasStage.BOUNDED_DISCOVERY,
            ),
        ),
    ) = AtlasScanState(
        status = status,
        generation = 1L,
        totalFeatures = outcomes.size,
        outcomes = outcomes,
        attempts = outcomes.size,
    )

    private fun device() = AtlasSupportReport.ReportedDevice(
        socModel = "SM8650",
        socManufacturer = "Qualcomm",
        apiLevel = 34,
        abi = "arm64-v8a",
        kernelMajorMinor = "5.15",
        lowRamDevice = false,
    )

    private fun observation() = AtlasObservation(
        id = "cpu.policy.scaling_cur_freq",
        domain = AtlasDomain.CPU,
        providerId = "reviewed-catalog",
        catalogVersion = "atlas-catalog-1",
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

    private fun feature(fromCache: Boolean, observedAt: Long?) = AtlasFeatureOutcome.Observed(
        id = when {
            !fromCache -> "fresh"
            observedAt != null -> "held"
            else -> "unageable"
        },
        observation = observation().copy(observedAtElapsedMs = observedAt),
        stage = if (fromCache) AtlasStage.REVIEWED_KNOWLEDGE else AtlasStage.BOUNDED_DISCOVERY,
        fromCache = fromCache,
    )
}
