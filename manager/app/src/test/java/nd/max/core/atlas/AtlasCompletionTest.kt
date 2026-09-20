package nd.max.core.atlas

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic completion stage (`P7`): what happens when the reviewed bank does not answer.
 *
 * The whole value of a second bank is that it fills the gaps, and the whole risk is that it becomes a
 * second opinion — a bank that re-reads what was already read, or that lets a candidate name count as a
 * reviewed gap, is worse than no second bank at all, because it looks like coverage. These tests pin the
 * three rules that prevent both: completion runs **only** for unresolved domains, it never revises a
 * reviewed answer, and it never moves the report gate.
 */
class AtlasCompletionTest {

    @Test
    fun `completion is asked only about the domains the reviewed bank could not answer`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)
        var asked: Set<AtlasDomain>? = null

        repository.start(
            features = listOf(
                answers.feature("cpu.policy.scaling_cur_freq", AtlasDomain.CPU) {
                    AtlasReadResult.Rejected(it.path, AtlasFailure.PERMISSION_DENIED, "denied")
                },
                answers.feature("gpu.devfreq.cur_freq", AtlasDomain.GPU) { observed(it) },
            ),
            discover = answers.discover,
            complete = { domains ->
                asked = domains
                emptyList()
            },
        )
        waitForIdle(repository)

        assertEquals("only the CPU domain was left unanswered", setOf(AtlasDomain.CPU), asked)
    }

    @Test
    fun `a device that answered everything reviewed is never asked twice`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)
        var asked: Set<AtlasDomain>? = null

        repository.start(
            features = listOf(answers.feature("cpu.policy.scaling_cur_freq", AtlasDomain.CPU) { observed(it) }),
            discover = answers.discover,
            complete = { domains ->
                asked = domains
                emptyList()
            },
        )
        waitForIdle(repository)

        // Stronger than "no candidates were attempted": the second bank is not even consulted. A
        // completion pass that runs for every device regardless would spend the budget and the battery
        // of exactly the users whose devices already answered.
        assertNull("completion was never even asked", asked)
        assertEquals("and nothing was read twice", 1, answers.reads)
    }

    @Test
    fun `a candidate reading is marked as a candidate and answers its own interface`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)

        repository.start(
            features = listOf(
                answers.feature("gpu.devfreq.cur_freq", AtlasDomain.GPU) {
                    AtlasReadResult.Rejected(it.path, AtlasFailure.ABSENT, "no such file")
                },
            ),
            discover = answers.discover,
            complete = {
                listOf(answers.feature("gpu.kgsl.gpuclk", AtlasDomain.GPU) { observed(it) })
            },
        )
        waitForIdle(repository)

        val state = repository.state.value
        val fromCandidate = state.observed.single { it.id == "gpu.kgsl.gpuclk" }
        assertEquals(
            "a candidate reading says which pass produced it",
            AtlasStage.CANDIDATE_INTERFACE,
            fromCandidate.stage,
        )
        // Completion adds evidence; it does not erase the gap it was called to explain.
        assertTrue(state.unresolved.any { it.id == "gpu.devfreq.cur_freq" })
        assertEquals(
            "and the reviewed gap keeps its own stage",
            AtlasStage.BOUNDED_DISCOVERY,
            state.unresolved.single { it.id == "gpu.devfreq.cur_freq" }.stage,
        )
    }

    @Test
    fun `a candidate miss is recorded as a candidate and not as a reviewed gap`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)

        repository.start(
            features = listOf(
                answers.feature("cpu.policy.scaling_cur_freq", AtlasDomain.CPU) {
                    AtlasReadResult.Rejected(it.path, AtlasFailure.ABSENT, "no such file")
                },
            ),
            discover = answers.discover,
            complete = {
                listOf(
                    answers.feature("power.capacity", AtlasDomain.CPU) {
                        AtlasReadResult.Rejected(it.path, AtlasFailure.ABSENT, "no such file")
                    },
                )
            },
        )
        waitForIdle(repository)

        val state = repository.state.value
        assertEquals("both the reviewed gap and the candidate miss are kept", 2, state.unresolved.size)
        assertEquals(
            "only the reviewed one is a reviewed gap",
            listOf("cpu.policy.scaling_cur_freq"),
            state.reviewedUnresolved.map { it.id },
        )
        assertEquals(
            "and the candidate miss names its own pass",
            AtlasStage.CANDIDATE_INTERFACE,
            state.unresolved.single { it.id == "power.capacity" }.stage,
        )
    }

    @Test
    fun `a reviewed gap keeps the report gate open even when candidates answer`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)

        repository.start(
            features = listOf(
                answers.feature("cpu.policy.scaling_cur_freq", AtlasDomain.CPU) {
                    AtlasReadResult.Rejected(it.path, AtlasFailure.ABSENT, "no such file")
                },
            ),
            discover = answers.discover,
            complete = { listOf(answers.feature("power.capacity", AtlasDomain.CPU) { observed(it) }) },
        )
        waitForIdle(repository)

        val state = repository.state.value
        assertEquals(1, state.reviewedUnresolved.size)
        assertTrue("a reviewed interface that did not answer is worth reporting", state.reportEligible)
        assertEquals("and the candidate reading is counted apart", 1, state.candidateOutcomes.size)
    }

    @Test
    fun `cancelling during completion is published as a cancellation`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<List<AtlasFeatureRequest>>()

        repository.start(
            features = listOf(
                answers.feature("cpu.policy.scaling_cur_freq", AtlasDomain.CPU) {
                    AtlasReadResult.Rejected(it.path, AtlasFailure.ABSENT, "no such file")
                },
            ),
            discover = answers.discover,
            complete = {
                entered.complete(Unit)
                release.await()
            },
        )
        entered.await()
        repository.cancel("the user left during completion")
        release.complete(listOf(answers.feature("power.capacity", AtlasDomain.CPU) { observed(it) }))
        waitUntil { !repository.state.value.isRunning }

        assertEquals(AtlasScanStatus.CANCELLED, repository.state.value.status)
        assertFalse("a cancelled pass offers no report", repository.state.value.reportEligible)
    }

    @Test
    fun `a scan without a completion pass behaves exactly as before`(): Unit = runBlocking {
        val answers = Answers()
        val repository = repository(answers)

        repository.start(
            features = listOf(answers.feature("cpu.policy.scaling_cur_freq", AtlasDomain.CPU) { observed(it) }),
            discover = answers.discover,
        )
        waitForIdle(repository)

        val state = repository.state.value
        assertEquals(AtlasScanStatus.COMPLETED, state.status)
        assertEquals(1, state.totalFeatures)
        assertTrue(state.candidateOutcomes.isEmpty())
    }

    // ---- helpers --------------------------------------------------------------------------------------

    /** One answer per path, so a single discovery function serves the reviewed and candidate stages. */
    private class Answers {
        private val byPath = mutableMapOf<String, (AtlasProbeRequest) -> AtlasReadResult>()

        /** How many discovery attempts were made, so "not asked twice" is a measurement. */
        var reads: Int = 0
            private set

        fun feature(
            id: String,
            domain: AtlasDomain,
            answer: (AtlasProbeRequest) -> AtlasReadResult,
        ): AtlasFeatureRequest {
            val request = AtlasProbeRequest(
                id = id,
                domain = domain,
                providerId = "test",
                catalogVersion = AtlasCatalog.SCHEMA_VERSION,
                sourceId = "L02",
                path = "/sys/class/test/${id.substringAfterLast('.')}",
                unit = AtlasUnit.COUNT,
            )
            byPath[request.path] = answer
            return AtlasFeatureRequest(request = request, volatility = AtlasVolatility.INSTANT)
        }

        val discover: suspend (AtlasProbeRequest) -> AtlasReadResult = { request ->
            reads += 1
            byPath[request.path]?.invoke(request)
                ?: AtlasReadResult.Rejected(request.path, AtlasFailure.UNKNOWN_CAUSE, "no answer was registered")
        }
    }

    private fun repository(answers: Answers): AtlasRepository {
        val store = AtlasEvidenceStore(InMemoryStoreIo())
        return AtlasRepository(
            store = store,
            resolver = AtlasResolver(store, clockMs = { 0L }),
            ledger = AtlasFailureLedger(clockMs = { 0L }),
            identity = identity(),
            catalog = AtlasReviewedSeeds.catalog(),
            // Unconfined keeps the scan deterministic: it runs to its first real suspension inline.
            dispatcher = Dispatchers.Unconfined,
        )
    }

    private fun observed(request: AtlasProbeRequest) = AtlasReadResult.Observed(
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
            value = 1.0,
            textValue = null,
            rawRepresentation = null,
            failure = AtlasFailure.NONE,
            reason = "read",
            observedAtElapsedMs = 0L,
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

    private suspend fun waitForIdle(repository: AtlasRepository) = waitUntil { !repository.state.value.isRunning }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(WAIT_TICKS) {
            if (predicate()) return
            withContext(Dispatchers.Default) { kotlinx.coroutines.yield() }
            Thread.sleep(1L)
        }
    }

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
