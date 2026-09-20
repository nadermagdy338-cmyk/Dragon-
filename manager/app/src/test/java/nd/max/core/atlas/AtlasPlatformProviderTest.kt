package nd.max.core.atlas

import nd.max.core.atlas.support.AtlasCanaries
import nd.max.core.atlas.support.AtlasSourceGuard
import nd.max.core.hardware.ReadOnlyProbeAccess
import nd.max.core.hardware.UnavailableAtlasReadTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P4` acceptance: non-CPU/GPU domains as observations with reasons, and a matrix that cannot lie by
 * omission.
 *
 * Only the readings are faked; every rule under test (scales, dimensions, absence, identity, matrix
 * states) is the shipped provider's own logic.
 */
class AtlasPlatformProviderTest {

    // ---- the matrix ---------------------------------------------------------------------------------

    @Test
    fun `an unwired source yields a matrix that claims nothing`() {
        val matrix = provider().matrix()

        assertEquals(AtlasDomain.entries.size, matrix.supports.size)
        assertEquals(AtlasDomain.entries.size, matrix.gaps().size)
        assertTrue(matrix.observations().isEmpty())
        assertEquals(AtlasDomain.entries.map { AtlasSupportState.DEFERRED }, matrix.supports.map { it.state })
        assertEquals(AtlasReasonCodes.OTHER_PROVIDER, matrix.byDomain(AtlasDomain.CPU).reason)
        assertEquals(AtlasReasonCodes.OTHER_PROVIDER, matrix.byDomain(AtlasDomain.GPU).reason)
        assertEquals(AtlasReasonCodes.NO_SOURCE_WIRED, matrix.byDomain(AtlasDomain.THERMAL).reason)
    }

    @Test
    fun `the matrix cannot be built with a domain missing`() {
        val rows = provider().matrix().supports
        val missing = rows.filterNot { it.domain == AtlasDomain.NETWORK }

        val thrown = runCatching { AtlasSupportMatrix(catalogVersion = AtlasCatalog.SCHEMA_VERSION, supports = missing) }
        assertTrue(thrown.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `a deferred row may not carry observations and a supported row may not be empty`() {
        assertTrue(
            "a deferred row with evidence is a contradiction",
            runCatching {
                AtlasDomainSupport(AtlasDomain.THERMAL, AtlasSupportState.DEFERRED, "x", listOf(observation()), 0, 0)
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "support claimed without one successful observation",
            runCatching {
                AtlasDomainSupport(AtlasDomain.THERMAL, AtlasSupportState.OBSERVED, AtlasReasonCodes.NONE, emptyList(), 0, 0)
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "partial support must name a real gap",
            runCatching {
                AtlasDomainSupport(AtlasDomain.THERMAL, AtlasSupportState.PARTIAL, AtlasReasonCodes.PARTIAL, listOf(observation()), 0, 0)
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "a row cannot match more entries than the catalog holds",
            runCatching {
                AtlasDomainSupport(AtlasDomain.THERMAL, AtlasSupportState.DEFERRED, "x", emptyList(), 2, 3)
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `vendor hints only ever add reviewed entries and never fabricate support`() {
        val generic = provider(vendorHints = setOf("mediatek"), thermal = listOf(AtlasThermalZone(0, "cpu", "42000", AtlasThermalScale.MILLI_CELSIUS)))
            .matrix().byDomain(AtlasDomain.GPU)
        val unknown = provider(vendorHints = setOf("acme"), thermal = listOf(AtlasThermalZone(0, "cpu", "42000", AtlasThermalScale.MILLI_CELSIUS)))
            .matrix().byDomain(AtlasDomain.GPU)

        assertEquals(AtlasSupportState.DEFERRED, generic.state)
        assertEquals("a device hint does not create an observation", AtlasSupportState.DEFERRED, unknown.state)
        assertTrue("a matched vendor reads more knowledge, not more truth", generic.matchedEntries >= unknown.matchedEntries)
        assertTrue(generic.matchedEntries <= generic.catalogEntries)
    }

    @Test
    fun `a declared identity widens catalog selection to its vendor entries and never narrows it`() {
        val unknown = provider().matrix()
        val declared = provider(
            identity = AtlasDeviceIdentity(
                socManufacturer = "MediaTek",
                socModel = "MT6983",
                supportedAbis = listOf("arm64-v8a"),
                apiLevel = 35,
            ),
        ).matrix()

        AtlasDomain.entries.forEach { domain ->
            assertTrue(
                "$domain must not lose candidates to a declaration",
                declared.byDomain(domain).matchedEntries >= unknown.byDomain(domain).matchedEntries,
            )
            assertEquals(
                "a declaration cannot invent catalog entries",
                unknown.byDomain(domain).catalogEntries,
                declared.byDomain(domain).catalogEntries,
            )
        }
        assertTrue(
            "the mediatek GPU entry participates for a mediatek declaration",
            declared.byDomain(AtlasDomain.GPU).matchedEntries > unknown.byDomain(AtlasDomain.GPU).matchedEntries,
        )
        assertEquals(
            "a declaration is not a reading",
            AtlasSupportState.DEFERRED,
            declared.byDomain(AtlasDomain.GPU).state,
        )
    }

    @Test
    fun `catalog coverage is reported per domain so a gap is visible next to it`() {
        val matrix = provider().matrix()
        val power = matrix.byDomain(AtlasDomain.POWER)
        val catalog = AtlasReviewedSeeds.catalog()
        assertEquals(catalog.entries.count { it.domain == AtlasDomain.POWER }, power.catalogEntries)
        assertTrue("the seed catalog covers power", power.catalogEntries > 0)
        assertEquals("an unknown device still matches the generic entries", catalog.byDomain(AtlasDomain.POWER).size, power.matchedEntries)
    }

    // ---- thermal (T4.1) -------------------------------------------------------------------------------

    @Test
    fun `the thermal scale comes from the source and is never inferred from magnitude`() {
        val milli = provider(thermal = listOf(AtlasThermalZone(0, "cpu", "42000", AtlasThermalScale.MILLI_CELSIUS)))
            .matrix().byDomain(AtlasDomain.THERMAL)
        val degrees = provider(thermal = listOf(AtlasThermalZone(0, "cpu", "42000", AtlasThermalScale.CELSIUS)))
            .matrix().byDomain(AtlasDomain.THERMAL)

        val milliTemp = milli.observations.single { it.id == "thermal.zone0.temp" }
        val degreesTemp = degrees.observations.single { it.id == "thermal.zone0.temp" }
        assertEquals(AtlasUnit.MILLI_CELSIUS, milliTemp.unit)
        assertEquals(AtlasUnit.CELSIUS, degreesTemp.unit)
        assertEquals("the same magnitude is not the same reading", 42_000.0, milliTemp.value!!, 0.0)
        assertEquals(42_000.0, degreesTemp.value!!, 0.0)
    }

    @Test
    fun `a cpu zone is a reviewed junction and a broad vendor token is not`() {
        assertEquals(AtlasSemanticStatus.REVIEWED_MATCH, AtlasThermalTypes.classify("cpu").semantic)
        assertEquals(AtlasSemanticStatus.REVIEWED_MATCH, AtlasThermalTypes.classify(" CPU ").semantic)
        assertEquals(AtlasSemanticStatus.INFERRED, AtlasThermalTypes.classify("ap").semantic)
        assertEquals(AtlasSemanticStatus.INFERRED, AtlasThermalTypes.classify("tsens").semantic)
        assertEquals(AtlasSemanticStatus.UNKNOWN, AtlasThermalTypes.classify(null).semantic)
        assertFalse("a vendor hint never claims a cpu junction", AtlasThermalTypes.classify("ap").label.contains("cpu junction"))
        assertFalse("a vendor hint never claims a cpu junction", AtlasThermalTypes.classify("tsens").label.contains("cpu junction"))
        assertTrue(AtlasThermalTypes.classify("gpu").label.contains("gpu junction"))
    }

    @Test
    fun `a missing or unparsable temperature keeps the raw text and is never zero`() {
        val row = provider(
            thermal = listOf(
                AtlasThermalZone(0, null, "not-a-number", AtlasThermalScale.MILLI_CELSIUS),
                AtlasThermalZone(1, null, "", AtlasThermalScale.MILLI_CELSIUS),
                AtlasThermalZone(2, null, null, AtlasThermalScale.MILLI_CELSIUS),
            ),
        ).matrix().byDomain(AtlasDomain.THERMAL)

        assertEquals(AtlasSupportState.UNAVAILABLE, row.state)
        assertEquals(AtlasReasonCodes.NO_OBSERVATION, row.reason)
        val temps = row.observations.filter { it.id.endsWith(".temp") }
        assertEquals(3, temps.size)
        assertTrue(temps.all { it.failure == AtlasFailure.MALFORMED })
        assertTrue(temps.all { it.value == null })
        assertTrue("no temperature is ever recorded as zero", temps.none { it.value == 0.0 })
        assertEquals("not-a-number", temps.first { it.id == "thermal.zone0.temp" }.rawRepresentation)
    }

    // ---- power (T4.2) ---------------------------------------------------------------------------------

    @Test
    fun `battery dimensions stay separate and current keeps its polarity`() {
        val row = provider(
            battery = AtlasBatteryReading(
                levelPercent = 71,
                statusRaw = "Discharging",
                currentMicroAmps = -450_000L,
                voltageMicroVolts = 4_120_000L,
                chargeCounterMicroAmpHours = 3_100_000L,
                energyMicroWattHours = 12_800_000L,
                present = true,
            ),
        ).matrix().byDomain(AtlasDomain.POWER)

        assertEquals(AtlasSupportState.OBSERVED, row.state)
        fun unitOf(id: String) = row.observations.single { it.id == id }.unit
        assertEquals(AtlasUnit.PERCENT, unitOf("power.level"))
        assertEquals(AtlasUnit.MICRO_AMP, unitOf("power.current_now"))
        assertEquals(AtlasUnit.MICRO_VOLT, unitOf("power.voltage_now"))
        assertEquals(AtlasUnit.MICRO_AMP_HOUR, unitOf("power.charge_counter"))
        assertEquals(AtlasUnit.MICRO_WATT_HOUR, unitOf("power.energy_now"))
        assertEquals("a discharging current stays negative", -450_000.0, row.observations.single { it.id == "power.current_now" }.value!!, 0.0)

        val chargeUnit = unitOf("power.charge_counter")
        val energyUnit = unitOf("power.energy_now")
        assertTrue(chargeUnit != energyUnit)
        assertEquals("energy never becomes a charge", null, AtlasUnitRules.chargeMicroAmpHours(12_800_000.0, energyUnit))
        assertEquals(12_800_000.0, AtlasUnitRules.energyMicroWattHours(12_800_000.0, energyUnit)!!, 0.0)
    }

    @Test
    fun `a battery that reported nothing is unavailability instead of zeros`() {
        val row = provider(
            battery = AtlasBatteryReading(null, null, null, null, null, null, null),
        ).matrix().byDomain(AtlasDomain.POWER)

        assertEquals(AtlasSupportState.UNAVAILABLE, row.state)
        assertEquals(AtlasReasonCodes.NO_OBSERVATION, row.reason)
        assertTrue(row.observations.all { it.failure != AtlasFailure.NONE && it.value == null })
    }

    // ---- memory, pressure and ZRAM (T4.3) ---------------------------------------------------------------

    @Test
    fun `memory kilobytes become bytes explicitly and pressure stays a separate signal`() {
        val row = provider(
            memory = AtlasMemoryReading(
                memTotalKb = 8_000_000L,
                memAvailableKb = 2_000_000L,
                swapTotalKb = 4_000_000L,
                swapFreeKb = 3_000_000L,
                psiSomeRaw = "some avg10=1.25 avg60=0.50 avg300=0.10 total=12345",
                psiFullRaw = "full avg10=0.75 avg60=0.25 avg300=0.05 total=9875",
            ),
        ).matrix().byDomain(AtlasDomain.MEMORY)

        assertEquals(AtlasSupportState.OBSERVED, row.state)
        val total = row.observations.single { it.id == "memory.meminfo.total" }
        assertEquals(AtlasUnit.BYTES, total.unit)
        assertEquals(8_192_000_000.0, total.value!!, 0.0)

        val psiAverage = row.observations.single { it.id == "memory.psi.cpu.avg10" }
        assertEquals(AtlasUnit.PERCENT, psiAverage.unit)
        assertEquals(1.25, psiAverage.value!!, 0.0)
        assertEquals(12_345.0, row.observations.single { it.id == "memory.psi.cpu.total" }.value!!, 0.0)
        assertTrue("PSI is not a meminfo value", psiAverage.id != total.id)
    }

    @Test
    fun `malformed pressure keeps its raw text and yields no value`() {
        val truncated = "some avg10=1.25 avg60=0.50 avg300=0.10"
        assertFalse(AtlasPlatformParsers.psiIsWellFormed(truncated))
        assertFalse(AtlasPlatformParsers.psiIsWellFormed("some avg10=oops total=1"))
        assertTrue(AtlasPlatformParsers.psiIsWellFormed("some avg10=1.0 avg60=0.0 avg300=0.0 total=7"))

        val row = provider(memory = AtlasMemoryReading(null, null, null, null, truncated, null))
            .matrix().byDomain(AtlasDomain.MEMORY)

        val cpu = row.observations.single { it.id == "memory.psi.cpu" }
        assertEquals(AtlasFailure.MALFORMED, row.observations.single { it.failure == AtlasFailure.MALFORMED }.failure)
        assertEquals(null, cpu.value)
        assertEquals(truncated, cpu.rawRepresentation)
        assertFalse("no zero is invented for a malformed pressure line", row.observations.any { it.value == 0.0 })
    }

    @Test
    fun `every zram device is observed rather than a hardcoded zram0`() {
        val row = provider(
            zram = listOf(
                AtlasZramReading("zram0", 4_294_967_296L, 1_048_576L, "lz4"),
                AtlasZramReading("zram1", 2_147_483_648L, 524_288L, "zstd"),
                AtlasZramReading("zram2", null, 0L, null),
            ),
        ).matrix().byDomain(AtlasDomain.STORAGE)

        val disksizes = row.observations.filter { it.id.endsWith(".disksize") }
        assertEquals(3, disksizes.size)
        assertEquals(
            listOf("storage.zram0.disksize", "storage.zram1.disksize", "storage.zram2.disksize"),
            disksizes.map { it.id },
        )
        assertEquals(AtlasUnit.BYTES, disksizes.first().unit)
        assertEquals(AtlasFailure.BACKEND_UNAVAILABLE, disksizes.last().failure)
        assertEquals("zstd", row.observations.single { it.id == "storage.zram1.algorithm" }.textValue)
        assertTrue(row.observations.none { it.id.contains("zram3") })
    }

    // ---- display, sensors, storage, network, privilege (T4.4) ------------------------------------------

    @Test
    fun `a partially observed domain is partial and names both sides`() {
        val row = provider(display = AtlasDisplayReading(1080, 2400, "not-a-rate", listOf("HDR10", "HLG")))
            .matrix().byDomain(AtlasDomain.DISPLAY)

        assertEquals(AtlasSupportState.PARTIAL, row.state)
        assertEquals(AtlasReasonCodes.PARTIAL, row.reason)
        assertEquals(AtlasFailure.MALFORMED, row.observations.single { it.id == "display.refresh" }.failure)
        assertEquals(1080.0, row.observations.single { it.id == "display.width" }.value!!, 0.0)
        assertEquals("HDR10+HLG", row.observations.single { it.id == "display.hdr" }.textValue)
    }

    @Test
    fun `network rows carry state only and no identity canary`() {
        val row = provider(network = AtlasNetworkReading(connected = true, metered = false, transport = "wifi"))
            .matrix().byDomain(AtlasDomain.NETWORK)

        assertEquals(AtlasSupportState.OBSERVED, row.state)
        assertEquals(3, row.observations.size)
        assertTrue(row.observations.all { it.textValue != null && it.unit == AtlasUnit.UNKNOWN })
        row.observations.forEach { observation ->
            assertEquals(emptyList<String>(), AtlasCanaries.leaks(observation.textValue))
            assertEquals(emptyList<String>(), AtlasCanaries.leaks(observation.rawRepresentation))
            assertEquals(emptyList<String>(), AtlasCanaries.leaks(observation.reason))
            assertEquals(emptyList<String>(), AtlasCanaries.leaks(observation.path))
        }
        assertTrue(row.observations.none { it.path.contains("ssid") || it.path.contains("mac") || it.path.contains("ip") })
    }

    @Test
    fun `privilege is unavailable unless the control plane verified the identity`() {
        val unverified = provider(
            privilege = AtlasPrivilegeReading("KernelSU", "v1.2.3", AtlasIdentityVerification.UNVERIFIED),
        ).matrix().byDomain(AtlasDomain.PRIVILEGE)
        assertEquals(AtlasSupportState.UNAVAILABLE, unverified.state)
        assertEquals(AtlasFailure.BACKEND_UNAVAILABLE, unverified.observations.single().failure)
        assertTrue(unverified.observations.single().reason.contains(AtlasReasonCodes.UNVERIFIED_IDENTITY))
        assertEquals("an unverified identity is not reported as a fact", null, unverified.observations.single().textValue)

        val verified = provider(
            privilege = AtlasPrivilegeReading("KernelSU", "v1.2.3", AtlasIdentityVerification.VERIFIED_BY_CONTROL_PLANE),
        ).matrix().byDomain(AtlasDomain.PRIVILEGE)
        assertEquals(AtlasSupportState.OBSERVED, verified.state)
        assertEquals("KernelSU", verified.observations.single { it.id == "privilege.root_manager" }.textValue)
        assertEquals("v1.2.3", verified.observations.single { it.id == "privilege.module_version" }.textValue)
    }

    @Test
    fun `cpu and gpu stay visible as another provider's domain`() {
        val matrix = provider(thermal = listOf(AtlasThermalZone(0, "cpu", "42000", AtlasThermalScale.MILLI_CELSIUS))).matrix()

        assertEquals(AtlasSupportState.DEFERRED, matrix.stateOf(AtlasDomain.CPU))
        assertEquals(AtlasReasonCodes.OTHER_PROVIDER, matrix.gaps().getValue(AtlasDomain.CPU))
        assertEquals(AtlasSupportState.OBSERVED, matrix.stateOf(AtlasDomain.THERMAL))
        assertFalse(matrix.gaps().containsKey(AtlasDomain.THERMAL))
    }

    // ---- structural guarantees --------------------------------------------------------------------------

    @Test
    fun `the provider source names no writer no view model and no identity field`() {
        val code = AtlasSourceGuard.code(PROVIDER_SOURCE)
        // Tokens are the names a *call or field* would use (camelCase getters, `ssid`, `File`), not the
        // uppercase words an honest explanatory note may contain: the check is about not reading or
        // storing identity, and the produced observations are walked separately below.
        val forbidden = listOf(
            "import android",
            "ViewModel",
            "Shell",
            "Shizuku",
            "RootFileAccess",
            "chmod",
            "writable",
            "canWrite",
            "-cbc",
            "File",
            "data/adb",
            "ssid",
            "bssid",
            "macAddress",
            "ipAddress",
            "wifiInfo",
            "packageName",
            "networkInterface",
        )

        val hits = AtlasSourceGuard.forbiddenHits(code, forbidden)
        assertEquals("the provider must not name these: $hits", emptyList<String>(), hits)
        assertTrue("the guard must actually have read the file", code.length > 5_000)
    }

    @Test
    fun `the comment stripper keeps code and drops prose so the guard is not vacuous`() {
        val sample = """
            // no SSID is stored
            /* sshecret */
            fun `a provider's name`() {
                val path = "/android/api/network/state"
                val token = "cbc"
            }
        """.trimIndent()

        val stripped = AtlasSourceGuard.stripComments(sample)
        assertFalse("prose is stripped", stripped.contains("SSID"))
        assertTrue("string literals survive", stripped.contains("/android/api/network/state"))
        assertTrue("a backticked name with an apostrophe must not swallow the code after it", stripped.contains("network/state"))
        assertTrue(stripped.contains("provider's"))
        assertTrue(stripped.contains("cbc"))
        assertFalse(AtlasSourceGuard.forbiddenHits(stripped, listOf("cbc")).isEmpty())
    }

    @Test
    fun `the file boundary refuses a public API surface`() {
        val access = ReadOnlyProbeAccess(transport = UnavailableAtlasReadTransport, clockMs = { 0L })

        val result = access.read(
            AtlasProbeRequest(
                id = "power.level",
                domain = AtlasDomain.POWER,
                providerId = "atlas-platform-1",
                catalogVersion = AtlasCatalog.SCHEMA_VERSION,
                sourceId = "provider:platform",
                path = "/android/api/power/battery/level",
                unit = AtlasUnit.PERCENT,
            ),
        )
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (result as AtlasReadResult.Rejected).failure)
        assertTrue(result.reason.contains("not a file path"))
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, access.list("/android/api").failure)
        assertTrue(AtlasAnchors.isApproved("/android/api/power/battery/level"))
        assertTrue(AtlasAnchors.isPublicApiSurface("/android/api/power/battery/level"))
        assertFalse(AtlasAnchors.isPublicApiSurface("/sys/class/thermal/thermal_zone0/temp"))
        assertTrue(
            "the virtual root is for observations only: no catalog entry may anchor there",
            AtlasReviewedSeeds.catalog().entries.none { AtlasAnchors.isPublicApiSurface(it.parentRoot) },
        )
    }

    @Test
    fun `an odd device name still produces evidence about itself and cannot break an id`() {
        val row = provider(
            zram = listOf(AtlasZramReading("zram 0!", 1_024L, 512L, "lzo")),
            sensors = listOf(AtlasSensorReading("Proximity Sensor", 2), AtlasSensorReading("", 1)),
        ).matrix()

        val storage = row.byDomain(AtlasDomain.STORAGE)
        assertTrue(storage.observations.all { AtlasIds.isValidObservationId(it.id) })
        assertTrue(storage.observations.any { it.id.contains("zram0") })
        assertEquals(AtlasSupportState.OBSERVED, storage.state)

        val sensors = row.byDomain(AtlasDomain.SENSOR)
        assertEquals(1, sensors.observations.size)
        assertEquals("sensor.proximitysensor.count", sensors.observations.single().id)
        assertEquals(2.0, sensors.observations.single().value!!, 0.0)
    }

    @Test
    fun `two runs over the same readings produce the same matrix`() {
        val readings = { provider(thermal = listOf(AtlasThermalZone(0, "cpu", "42000", AtlasThermalScale.MILLI_CELSIUS))) }

        assertEquals(readings().matrix(), readings().matrix())
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private fun provider(
        vendorHints: Set<String> = emptySet(),
        thermal: List<AtlasThermalZone> = emptyList(),
        battery: AtlasBatteryReading? = null,
        memory: AtlasMemoryReading? = null,
        zram: List<AtlasZramReading> = emptyList(),
        display: AtlasDisplayReading? = null,
        sensors: List<AtlasSensorReading> = emptyList(),
        storage: AtlasStorageReading? = null,
        network: AtlasNetworkReading? = null,
        privilege: AtlasPrivilegeReading? = null,
        identity: AtlasDeviceIdentity? = null,
    ): AtlasPlatformProvider = AtlasPlatformProvider(
        source = FakePlatformSource(vendorHints, thermal, battery, memory, zram, display, sensors, storage, network, privilege),
        clockMs = { 1_000L },
        identity = identity,
    )

    private class FakePlatformSource(
        private val vendorHints: Set<String>,
        private val thermal: List<AtlasThermalZone>,
        private val battery: AtlasBatteryReading?,
        private val memory: AtlasMemoryReading?,
        private val zram: List<AtlasZramReading>,
        private val display: AtlasDisplayReading?,
        private val sensors: List<AtlasSensorReading>,
        private val storage: AtlasStorageReading?,
        private val network: AtlasNetworkReading?,
        private val privilege: AtlasPrivilegeReading?,
    ) : AtlasPlatformSource {
        override fun vendorHints(): Set<String> = vendorHints
        override fun thermalZones(): List<AtlasThermalZone> = thermal
        override fun battery(): AtlasBatteryReading? = battery
        override fun memory(): AtlasMemoryReading? = memory
        override fun zramDevices(): List<AtlasZramReading> = zram
        override fun display(): AtlasDisplayReading? = display
        override fun sensors(): List<AtlasSensorReading> = sensors
        override fun storage(): AtlasStorageReading? = storage
        override fun network(): AtlasNetworkReading? = network
        override fun privilege(): AtlasPrivilegeReading? = privilege
    }

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

    private companion object {
        const val PROVIDER_SOURCE = "src/main/java/nd/max/core/atlas/AtlasPlatformProvider.kt"
    }
}
