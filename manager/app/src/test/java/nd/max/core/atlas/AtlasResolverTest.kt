package nd.max.core.atlas

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * `P5` acceptance tests: the ordered resolver, the bounded cache, and the scan lifecycle.
 *
 * The clock, the store's I/O and the discovery stage are injected, so every boundary here is exact
 * rather than timed. The store's file backing is the **real** one ([AtlasFileStoreIo]) on a temporary
 * directory: corruption, atomic replace and key mismatch are then facts about real bytes.
 */
class AtlasResolverTest {

    // ---- ordered resolution ------------------------------------------------------------------------

    @Test
    fun `held evidence answers first and the device is not asked again`(): Unit = runBlocking {
        val store = store()
        val fingerprint = fingerprint(store)
        val resolver = resolver(store, now = 1_000L)
        var reads = 0
        val request = request()

        val first = resolver.resolve(request, AtlasVolatility.INSTANT, fingerprint) {
            reads += 1
            observed(request, at = 1_000L)
        }
        val second = resolver.resolve(request, AtlasVolatility.INSTANT, fingerprint) {
            reads += 1
            observed(request, at = 1_000L)
        }

        assertEquals("the first resolution is a real read", AtlasStage.BOUNDED_DISCOVERY, (first as AtlasFeatureOutcome.Observed).stage)
        assertFalse(first.fromCache)
        assertEquals("the second comes from held evidence", AtlasStage.REVIEWED_KNOWLEDGE, (second as AtlasFeatureOutcome.Observed).stage)
        assertTrue(second.fromCache)
        assertEquals("a fresh answer must not be re-read", 1, reads)
    }

    @Test
    fun `stale evidence is re-read rather than served`(): Unit = runBlocking {
        val store = store()
        val fingerprint = fingerprint(store)
        val request = request()

        resolver(store, now = 0L).resolve(request, AtlasVolatility.INSTANT, fingerprint) {
            observed(request, at = 0L)
        }
        var reads = 0
        // One second past the INSTANT lifetime, plus the clock moving on.
        val outcome = resolver(store, now = 1_001L).resolve(request, AtlasVolatility.INSTANT, fingerprint) {
            reads += 1
            observed(request, at = 1_001L)
        }

        assertEquals(1, reads)
        assertEquals(AtlasStage.BOUNDED_DISCOVERY, (outcome as AtlasFeatureOutcome.Observed).stage)
    }

    @Test
    fun `a clock that moved backwards makes evidence stale instead of fresh`(): Unit = runBlocking {
        val store = store()
        val fingerprint = fingerprint(store)
        val request = request()
        resolver(store, now = 10_000L).resolve(request, AtlasVolatility.FAST, fingerprint) {
            observed(request, at = 10_000L)
        }

        // The same evidence, judged by a clock that is behind the observation.
        assertEquals(
            AtlasStaleness.UNMEASURABLE_CLOCK,
            resolver(store, now = 9_000L).heldStaleness(request.id, fingerprint),
        )
    }

    @Test
    fun `a future observation is not treated as fresh forever`(): Unit = runBlocking {
        val store = store()
        val fingerprint = fingerprint(store)
        val request = request()
        // A value stamped in the future cannot be aged; the safe direction is to re-read.
        resolver(store, now = 0L).resolve(request, AtlasVolatility.INSTANT, fingerprint) {
            observed(request, at = 5_000L)
        }

        assertEquals(
            AtlasStaleness.UNMEASURABLE_CLOCK,
            resolver(store, now = 1_000L).heldStaleness(request.id, fingerprint),
        )
    }

    // ---- causes and cancellation -------------------------------------------------------------------

    @Test
    fun `a failure keeps the cause the boundary reported`(): Unit = runBlocking {
        val outcome = resolver(store(), now = 0L).resolve(request(), AtlasVolatility.SLOW, fingerprint(store())) {
            AtlasReadResult.Rejected(request().path, AtlasFailure.PERMISSION_DENIED, "denied by policy")
        }

        val unresolved = outcome as AtlasFeatureOutcome.Unresolved
        assertEquals(AtlasFailure.PERMISSION_DENIED, unresolved.failure)
        assertEquals(AtlasStage.BOUNDED_DISCOVERY, unresolved.stage)
        assertTrue(unresolved.reason.contains("denied"))
    }

    @Test
    fun `an unexpected error becomes unknown rather than absent`(): Unit = runBlocking {
        val outcome = resolver(store(), now = 0L).resolve(request(), AtlasVolatility.SLOW, fingerprint(store())) {
            throw IllegalStateException("the transport exploded")
        }

        val unresolved = outcome as AtlasFeatureOutcome.Unresolved
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, unresolved.failure)
        assertTrue(unresolved.reason.contains("IllegalStateException"))
    }

    @Test
    fun `cancellation propagates and is never converted into a failed device`(): Unit = runBlocking {
        val store = store()
        val job = Job()
        // The job is the scope's context element: adding it replaces the supervisor it is combined with,
        // which is exactly what a controlled cancellation needs here.
        val scope = CoroutineScope(job + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        var threw = false

        val worker = scope.launch {
            try {
                resolver(store, now = 0L).resolve(request(), AtlasVolatility.SLOW, fingerprint(store)) {
                    entered.complete(Unit)
                    // Suspends until this coroutine is cancelled, and a cancellation is the **only** way
                    // out. The earlier version completed a deferred from the test thread and let the
                    // race decide whether the read or the cancellation arrived first — a test that
                    // passed or failed on thread timing, which is a test that proves nothing.
                    kotlinx.coroutines.awaitCancellation()
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                threw = true
            }
        }
        // Wait until the read is genuinely in flight: cancelling a coroutine that never started would
        // prove nothing about how cancellation is handled.
        entered.await()
        job.cancel()
        worker.join()

        assertTrue("a cancelled job must be visible as a cancellation", threw)
        assertEquals("and nothing was cached from a cancelled attempt", emptyList<String>(), store.storedIds())
    }

    @Test
    fun `a suppressed feature never reaches the device`(): Unit = runBlocking {
        var reads = 0
        val outcome = resolver(store(), now = 0L).resolve(
            request = request(),
            volatility = AtlasVolatility.SLOW,
            fingerprint = fingerprint(store()),
            suppressedBecause = AtlasSuppression(AtlasFailure.PERMISSION_DENIED, "denied by policy", 5_000L),
        ) {
            reads += 1
            observed(request(), at = 0L)
        }

        assertTrue(outcome is AtlasFeatureOutcome.Suppressed)
        assertEquals(0, reads)
    }

    // ---- cache identity and corruption --------------------------------------------------------------

    @Test
    fun `each context change invalidates the cache`() {
        val store = store()
        val identity = identity()
        val base = store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity, 0L, 0L)

        val changes = mapOf(
            "another catalog" to { store.fingerprint("atlas-catalog-2", identity, 0L, 0L) },
            "another device" to { store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity.copy(socModel = "SM8750"), 0L, 0L) },
            "another boot" to { store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity, 1L, 0L) },
            "another privilege generation" to { store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity, 0L, 1L) },
            "another abi set" to {
                store.fingerprint(
                    AtlasCatalog.SCHEMA_VERSION,
                    identity.copy(supportedAbis = listOf("armeabi-v7a")),
                    0L,
                    0L,
                )
            },
        )

        changes.forEach { (label, changed) ->
            assertTrue("$label must change the fingerprint", changed() != base)
        }
        assertEquals("and the same context is stable", base, store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity, 0L, 0L))
    }

    @Test
    fun `a corrupt entry is discarded and the feature is re-read`(): Unit = runBlocking {
        val directory = Files.createTempDirectory("atlas-resolver-")
        val io = AtlasFileStoreIo(directory)
        val store = AtlasEvidenceStore(io)
        val fingerprint = fingerprint(store)
        val request = request()
        resolver(store, now = 0L).resolve(request, AtlasVolatility.SLOW, fingerprint) { observed(request, at = 0L) }
        val entry = directory.resolve("${AtlasEvidenceStore.NAME_PREFIX}${request.id}${AtlasEvidenceStore.NAME_SUFFIX}")
        assertTrue(entry.toFile().exists())
        entry.toFile().writeText("this is not the schema we write")

        var reads = 0
        val outcome = resolver(store, now = 0L).resolve(request, AtlasVolatility.SLOW, fingerprint) {
            reads += 1
            observed(request, at = 0L)
        }

        assertEquals("corruption is a re-read, not a lie", 1, reads)
        assertEquals(AtlasCacheMiss.CORRUPT, (outcome as AtlasFeatureOutcome.Observed).cacheMiss)
        // The corrupt content is gone; the entry itself is legitimately rewritten by the successful
        // re-read, so the assertion is about the bytes rather than about the file's existence.
        assertFalse("the corrupt text is discarded", entry.toFile().readText().contains("not the schema"))
        assertTrue("and a valid entry replaced it", store.load(request.id, fingerprint) is AtlasCacheLookup.Hit)
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an oversize entry is refused rather than truncated into plausibility`() {
        val directory = Files.createTempDirectory("atlas-oversize-")
        val io = AtlasFileStoreIo(directory)
        val store = AtlasEvidenceStore(io)
        val fingerprint = fingerprint(store)
        io.write("${AtlasEvidenceStore.NAME_PREFIX}${request().id}${AtlasEvidenceStore.NAME_SUFFIX}", "x".repeat(AtlasEvidenceStore.MAX_ENTRY_BYTES + 1))

        val lookup = store.load(request().id, fingerprint)

        assertEquals(AtlasCacheMiss.OVERSIZE, (lookup as AtlasCacheLookup.Miss).reason)
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `nothing with authority can be stored, in the source or in the stored bytes`() {
        assertEquals(
            emptyList<String>(),
            nd.max.core.atlas.support.AtlasSourceGuard.forbiddenHits(
                nd.max.core.atlas.support.AtlasSourceGuard.code("src/main/java/nd/max/core/atlas/AtlasEvidenceStore.kt"),
            ),
        )
        val io = InMemoryStoreIo()
        val store = AtlasEvidenceStore(io)
        val fingerprint = fingerprint(store)
        val request = request()
        assertTrue(store.save(observed(request, at = 0L).observation, AtlasVolatility.SLOW, fingerprint))
        val text = io.read("${AtlasEvidenceStore.NAME_PREFIX}${request.id}${AtlasEvidenceStore.NAME_SUFFIX}").orEmpty()

        listOf("writable", "write", "control", "authority", "chmod").forEach { token ->
            assertFalse("the stored bytes must not carry '$token'", text.contains(token, ignoreCase = true))
        }
        assertTrue("what is stored is the evidence itself", text.contains("1800000"))
    }

    // ---- repository: coalescing, completion, cancellation --------------------------------------------

    @Test
    fun `a second start coalesces into the running scan`(): Unit = runBlocking {
        val store = store()
        val repository = repository(store)
        val gate = CompletableDeferred<Unit>()
        var reads = 0

        assertTrue(repository.start(listOf(feature()), discover = {
            reads += 1
            gate.await()
            observed(it, at = 0L)
        }))
        val second = repository.start(listOf(feature()), discover = {
            reads += 1
            gate.await()
            observed(it, at = 0L)
        })
        assertFalse("the second caller joins the running scan", second)
        gate.complete(Unit)
        waitForIdle(repository)

        assertEquals("one scan, one read", 1, reads)
        assertEquals(AtlasScanStatus.COMPLETED, repository.state.value.status)
    }

    @Test
    fun `a cancelled scan is not an exhausted device, even if the read finishes late`(): Unit = runBlocking {
        val store = store()
        val repository = repository(store)
        val arrived = CompletableDeferred<AtlasReadResult>()

        repository.start(listOf(feature())) { arrived.await() }
        waitUntil { repository.state.value.isRunning }
        repository.cancel("the user left the screen")
        assertEquals(AtlasScanStatus.CANCELLED, repository.state.value.status)

        arrived.complete(observed(request(), at = 0L))
        waitForIdle(repository)

        assertEquals("a late answer must not overwrite a cancellation", AtlasScanStatus.CANCELLED, repository.state.value.status)
        assertEquals("a cancelled run is not eligible for a report", false, repository.state.value.reportEligible)
        assertNull(repository.state.value.percent?.takeIf { repository.state.value.totalFeatures == 0 })
    }

    @Test
    fun `progress is derived from features and is unknown when the total is unknown`(): Unit = runBlocking {
        val store = store()
        val repository = repository(store)
        val gate = CompletableDeferred<Unit>()
        val features = listOf(feature(), feature(id = "cpu.policy.related_cpus"))

        repository.start(features) {
            gate.await()
            observed(it, at = 0L)
        }
        waitUntil { repository.state.value.attempts + repository.state.value.cacheHits == 1 || !repository.state.value.isRunning }
        // While running, the bar reflects resolved features out of the known total.
        if (repository.state.value.isRunning) {
            assertEquals(2, repository.state.value.totalFeatures)
            assertTrue(repository.state.value.percent in 0..100)
        }
        gate.complete(Unit)
        waitForIdle(repository)

        assertEquals(100, repository.state.value.percent)
        assertEquals(0, repository.state.value.remaining)
        assertEquals("nothing unresolved, so a report is not suggested", false, repository.state.value.reportEligible)

        // An idle repository knows no total, so it must not claim 0% or 100%.
        assertNull(AtlasScanState().percent)
    }

    @Test
    fun `one unresolved feature never suppresses another`(): Unit = runBlocking {
        val store = store()
        val repository = repository(store)
        val good = feature(id = "cpu.policy.scaling_cur_freq")
        val bad = feature(id = "cpu.policy.related_cpus")

        repository.start(listOf(good, bad)) {
            if (it.id == "cpu.policy.related_cpus") {
                AtlasReadResult.Rejected(it.path, AtlasFailure.PERMISSION_DENIED, "denied")
            } else {
                observed(good.request, at = 0L)
            }
        }
        waitForIdle(repository)

        val state = repository.state.value
        assertEquals(AtlasScanStatus.PARTIAL, state.status)
        assertEquals(1, state.observed.size)
        assertEquals(1, state.unresolved.size)
        assertTrue("a partial run with an unresolved feature may suggest a report", state.reportEligible)
    }

    @Test
    fun `a suppressed path is not attempted and is counted as suppressed`(): Unit = runBlocking {
        val store = store()
        val ledger = AtlasFailureLedger(clockMs = { 0L })
        ledger.record(request().path, AtlasFailure.PERMISSION_DENIED)
        val repository = repository(store, ledger = ledger)
        var reads = 0

        repository.start(listOf(feature())) {
            reads += 1
            observed(it, at = 0L)
        }
        waitForIdle(repository)

        assertEquals("a denial a moment ago is not re-attempted", 0, reads)
        assertEquals(1, repository.state.value.suppressed)
        assertEquals(AtlasScanStatus.DENIED, repository.state.value.status)
    }

    @Test
    fun `a successful answer erases the failure memory`(): Unit = runBlocking {
        val store = store()
        // The clock advances past the denial lifetime, so the retry is allowed and the interface is
        // asked again. If a denial suppressed the attempt, this test would prove the opposite of what
        // it claims.
        val now = longArrayOf(0L)
        val ledger = AtlasFailureLedger(clockMs = { now[0] })
        ledger.record(request().path, AtlasFailure.PERMISSION_DENIED)
        now[0] = AtlasFailureRetryPolicy.DENIAL_RETRY_MS
        val repository = repository(store, ledger = ledger)

        repository.start(listOf(feature())) { observed(it, at = now[0]) }
        waitForIdle(repository)

        assertEquals("the interface answered, so nothing about it is remembered", 0, ledger.trackedPaths())
        assertEquals(AtlasScanStatus.COMPLETED, repository.state.value.status)
    }

    @Test
    fun `an unavailable backend is reported as unavailable rather than denied`(): Unit = runBlocking {
        val repository = repository(store())
        repository.start(listOf(feature())) {
            AtlasReadResult.Rejected(it.path, AtlasFailure.BACKEND_UNAVAILABLE, "no transport")
        }
        waitForIdle(repository)

        assertEquals(AtlasScanStatus.UNAVAILABLE, repository.state.value.status)
    }

    @Test
    fun `invalidation clears held evidence and resets the state`(): Unit = runBlocking {
        val store = store()
        val repository = repository(store)
        repository.start(listOf(feature())) { observed(it, at = 0L) }
        waitForIdle(repository)
        assertTrue(store.storedIds().isNotEmpty())

        val removed = repository.invalidate()

        assertEquals(1, removed)
        assertTrue(store.storedIds().isEmpty())
        assertEquals(AtlasScanStatus.IDLE, repository.state.value.status)
        assertNull(repository.state.value.percent)
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun store(): AtlasEvidenceStore = AtlasEvidenceStore(InMemoryStoreIo())

    private fun repository(
        store: AtlasEvidenceStore,
        ledger: AtlasFailureLedger = AtlasFailureLedger(clockMs = { 0L }),
    ) = AtlasRepository(
        store = store,
        resolver = AtlasResolver(store, clockMs = { 0L }),
        ledger = ledger,
        identity = identity(),
        catalog = AtlasReviewedSeeds.catalog(),
        // Unconfined keeps the scan deterministic: it runs to its first real suspension point inline.
        dispatcher = Dispatchers.Unconfined,
    )

    private fun resolver(store: AtlasEvidenceStore, now: Long) = AtlasResolver(store, clockMs = { now })

    private fun fingerprint(store: AtlasEvidenceStore): String =
        store.fingerprint(AtlasCatalog.SCHEMA_VERSION, identity(), 0L, 0L)

    private fun feature(id: String = "cpu.policy.scaling_cur_freq") = AtlasFeatureRequest(
        request = request(id),
        volatility = AtlasVolatility.SLOW,
    )

    private fun request(id: String = "cpu.policy.scaling_cur_freq") = AtlasProbeRequest(
        id = id,
        domain = AtlasDomain.CPU,
        providerId = "test",
        catalogVersion = AtlasCatalog.SCHEMA_VERSION,
        sourceId = "L02",
        path = "/sys/devices/system/cpu/cpufreq/policy0/${id.substringAfterLast('.')}",
        unit = AtlasUnit.KILO_HERTZ,
    )

    private fun observed(request: AtlasProbeRequest, at: Long) = AtlasReadResult.Observed(
        AtlasObservation(
            id = request.id,
            domain = request.domain,
            providerId = request.providerId,
            catalogVersion = request.catalogVersion,
            sourceId = request.sourceId,
            path = request.path,
            access = AtlasAccess.READABLE,
            semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
            unit = request.unit,
            value = 1_800_000.0,
            textValue = null,
            rawRepresentation = null,
            failure = AtlasFailure.NONE,
            reason = "read",
            observedAtElapsedMs = at,
            bootGeneration = 0L,
            privilegeGeneration = 0L,
            truncated = false,
        ),
    )

    private fun identity() = AtlasDeviceIdentity(
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

    private suspend fun waitForIdle(repository: AtlasRepository) {
        repeat(WAIT_TICKS) {
            if (!repository.state.value.isRunning) return
            withContext(Dispatchers.Default) { kotlinx.coroutines.yield() }
            Thread.sleep(1L)
        }
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(WAIT_TICKS) {
            if (predicate()) return
            withContext(Dispatchers.Default) { kotlinx.coroutines.yield() }
            Thread.sleep(1L)
        }
    }

    /** An in-memory store I/O for the cases that do not need real bytes on disk. */
    private class InMemoryStoreIo : AtlasStoreIo {
        private val entries = LinkedHashMap<String, String>()
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.sorted()
    }

    private companion object {
        const val WAIT_TICKS = 500
    }
}
