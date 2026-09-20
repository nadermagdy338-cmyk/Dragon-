package nd.max.core.hardware

import nd.max.core.atlas.AtlasCatalog
import nd.max.core.atlas.AtlasDomain
import nd.max.core.atlas.AtlasFailure
import nd.max.core.atlas.AtlasProbeRequest
import nd.max.core.atlas.AtlasReadResult
import nd.max.core.atlas.AtlasUnit
import nd.max.core.atlas.support.AtlasClock
import nd.max.core.atlas.support.AtlasFakeAudit
import nd.max.core.atlas.support.AtlasFakeFile
import nd.max.core.atlas.support.AtlasFakeTransport
import nd.max.core.atlas.support.AtlasProbeKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * `P2` acceptance: the read boundary itself.
 *
 * These tests drive the shipped [ReadOnlyProbeAccess] against a fake transport, so what is asserted
 * is real behavior — budgets checked before work, causes never invented, absence only on evidence,
 * partial values never parsed, and no mutating operation anywhere in the path.
 */
class ReadOnlyProbeAccessTest {

    // ---- path discipline ---------------------------------------------------------------------------

    @Test
    fun `an unsafe path is refused before any transport call`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
        val access = access(transport, clock)

        val unsafe = listOf(
            "/sys/class/thermal/../../etc/passwd",
            "/sys/class/thermal/*",
            "/sys/class/thermal/thermal_zone0/temp; id",
            "/sys/class/thermal/thermal_zone0/temp\n/sys/class/thermal/thermal_zone0/type",
            "etc/passwd",
            "/sys/class/thermal/",
        )
        unsafe.forEach { path ->
            val result = access.read(request(path))
            assertEquals("$path must be refused", AtlasFailure.MALFORMED, (result as AtlasReadResult.Rejected).failure)
        }
        assertTrue("a refused path must not reach the transport", transport.operations.isEmpty())
        assertEquals(0, access.stats().operations)
    }

    @Test
    fun `a path outside the approved anchors is never attempted`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putFile("/etc/passwd", AtlasFakeFile("root:x:0:0"))
        val access = access(transport, clock)

        val result = access.read(request("/etc/passwd"))
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (result as AtlasReadResult.Rejected).failure)
        assertTrue(result.reason.contains("approved anchor"))
        assertTrue(transport.operations.isEmpty())

        val listing = access.list("/etc")
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, listing.failure)
        assertTrue(listing.names.isEmpty())
        assertTrue(transport.operations.isEmpty())
    }

    @Test
    fun `the anchor check is injectable so the refusal path itself is provable`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("42000"))
        val denied = ReadOnlyProbeAccess(
            transport = transport,
            clockMs = clock::nowMs,
            approvedAnchor = { false },
        )

        val result = denied.read(request("/sys/class/thermal/thermal_zone0/temp"))
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (result as AtlasReadResult.Rejected).failure)
        assertTrue(transport.operations.isEmpty())
    }

    // ---- absence is evidence, not a guess ------------------------------------------------------------

    @Test
    fun `absence requires the parent to have been enumerated by this job`() {
        val clock = AtlasClock()
        val other = access(AtlasFakeTransport(clock).putDirectory("/sys/class/thermal", listOf("thermal_zone0")), clock)
        other.list("/sys/class/thermal")
        val path = "/sys/class/thermal/thermal_zone7/temp"

        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/class/thermal/thermal_zone7", listOf("type", "temp_policy"))
            .putFile(path, AtlasFakeFile(raw = null, exists = false))
        val access = access(transport, clock)

        val unproven = access.read(request(path))
        assertEquals(
            "one job's listing must not license another job's absence claim",
            AtlasFailure.UNKNOWN_CAUSE,
            (unproven as AtlasReadResult.Rejected).failure,
        )

        val enumeration = access.list("/sys/class/thermal/thermal_zone7")
        assertEquals(AtlasFailure.NONE, enumeration.failure)
        assertEquals(listOf("temp_policy", "type"), enumeration.names)
        val proven = access.read(request(path))
        assertEquals(AtlasFailure.ABSENT, (proven as AtlasReadResult.Rejected).failure)
    }

    @Test
    fun `a path the transport cannot see at all is an unknown cause, not absence`() {
        val clock = AtlasClock()
        val access = access(
            AtlasFakeTransport(clock).putDirectory("/sys/class/thermal", listOf("thermal_zone0")),
            clock,
        )
        access.list("/sys/class/thermal")

        val result = access.read(request("/sys/class/thermal/thermal_zone3/temp"))
        assertEquals("an unregistered path is unknown, never absent", AtlasFailure.UNKNOWN_CAUSE, (result as AtlasReadResult.Rejected).failure)
    }

    // ---- symlinks ------------------------------------------------------------------------------------

    @Test
    fun `a symlink that escapes the anchors is refused with the read never attempted`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putAlias("/sys/class/thermal/thermal_zone0/temp", "/etc/passwd")
            .putFile("/etc/passwd", AtlasFakeFile("root:x:0:0"))
        val access = access(transport, clock)

        val result = access.read(request("/sys/class/thermal/thermal_zone0/temp"))
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (result as AtlasReadResult.Rejected).failure)
        assertTrue(result.reason.contains("escapes"))
        assertTrue("nothing may be read through an escaping link", transport.recorded(AtlasProbeKinds.READ).isEmpty())
    }

    @Test
    fun `a link chain longer than the hop bound is backend unavailable`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("42000"))
        for (hop in 0 until AtlasReadBudget.DEFAULT.maxSymlinkHops + 1) {
            transport.putAlias("/sys/class/devfreq/l$hop", "/sys/class/devfreq/l${hop + 1}")
        }
        transport.putFile("/sys/class/devfreq/l${AtlasReadBudget.DEFAULT.maxSymlinkHops + 1}", AtlasFakeFile("1"))

        val result = access(transport, clock).read(request("/sys/class/devfreq/l0"))
        assertEquals(AtlasFailure.BACKEND_UNAVAILABLE, (result as AtlasReadResult.Rejected).failure)

        val looped = AtlasFakeTransport(clock)
            .putAlias("/sys/class/devfreq/loop", "/sys/class/devfreq/loop")
        val loopResult = access(looped, clock).read(request("/sys/class/devfreq/loop"))
        assertEquals(AtlasFailure.BACKEND_UNAVAILABLE, (loopResult as AtlasReadResult.Rejected).failure)
    }

    @Test
    fun `a resolvable link inside the anchors is read at its canonical path`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putAlias("/sys/class/devfreq/gpu0/cur_freq", "/sys/devices/system/cpu/policy0/cur_freq")
            .putFile("/sys/devices/system/cpu/policy0/cur_freq", AtlasFakeFile("1800000"))
        val access = access(transport, clock)

        val result = access.read(request("/sys/class/devfreq/gpu0/cur_freq", AtlasUnit.KILO_HERTZ))
        val observation = (result as AtlasReadResult.Observed).observation
        assertEquals(1_800_000.0, observation.value!!, 0.0)
        assertEquals(
            listOf("/sys/devices/system/cpu/policy0/cur_freq"),
            transport.recorded(AtlasProbeKinds.READ).map { it.path },
        )
    }

    // ---- budgets, enforced before work ---------------------------------------------------------------

    @Test
    fun `the operation budget stops a runaway job`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putDirectory("/sys/class/thermal", listOf("thermal_zone0"))
        val access = access(transport, clock, budget = budget(maxOperations = 3))

        val outcomes = (1..5).map { access.list("/sys/class/thermal").failure }
        assertEquals(
            listOf(
                AtlasFailure.NONE,
                AtlasFailure.NONE,
                AtlasFailure.NONE,
                AtlasFailure.BUDGET_EXCEEDED,
                AtlasFailure.BUDGET_EXCEEDED,
            ),
            outcomes,
        )
        assertEquals("the transport must not be called once the budget is spent", 3, transport.operations.size)
        assertTrue(access.stats().limitsReached)
    }

    @Test
    fun `the job deadline stops work before the attempt instead of after it`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putDirectory("/sys/class/thermal", listOf("thermal_zone0"))
        val access = access(transport, clock, budget = budget(jobDeadlineMs = 500L))

        assertEquals(AtlasFailure.NONE, access.list("/sys/class/thermal").failure)
        clock.advance(501L)

        val late = access.list("/sys/class/thermal")
        assertEquals(AtlasFailure.BUDGET_EXCEEDED, late.failure)
        assertTrue(late.reason.contains("job deadline"))
        assertEquals("a job past its deadline must not work at all", 1, transport.operations.size)
    }

    @Test
    fun `an attempt slower than its own deadline returns nothing and is never partial`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putDirectory("/sys/class/thermal", listOf("thermal_zone0"), slowMs = 1_001L)
        val access = access(transport, clock)

        val listing = access.list("/sys/class/thermal")
        assertEquals(AtlasFailure.TIMED_OUT, listing.failure)
        assertTrue(listing.names.isEmpty())
        assertFalse(listing.truncated)
    }

    @Test
    fun `a value larger than its byte allowance is refused instead of parsed`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("7".repeat(64), maxBytes = 8))
        val result = access(transport, clock).read(request("/sys/class/thermal/thermal_zone0/temp", AtlasUnit.MILLI_CELSIUS))

        assertEquals(AtlasFailure.BUDGET_EXCEEDED, (result as AtlasReadResult.Rejected).failure)
    }

    @Test
    fun `the aggregate byte budget refuses the read that would not fit and no other`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("1".repeat(60)))
            .putFile("/sys/class/thermal/thermal_zone1/temp", AtlasFakeFile("2".repeat(40)))
        val access = access(transport, clock, budget = budget(maxScalarBytes = 64, maxAggregateBytes = 100))

        assertEquals(AtlasFailure.NONE, failureOf(access, "/sys/class/thermal/thermal_zone0/temp"))
        assertEquals(AtlasFailure.NONE, failureOf(access, "/sys/class/thermal/thermal_zone1/temp"))
        assertEquals(100, access.stats().bytesRead)

        val over = access.read(request("/sys/class/thermal/thermal_zone0/temp", AtlasUnit.MILLI_CELSIUS))
        assertEquals(AtlasFailure.BUDGET_EXCEEDED, (over as AtlasReadResult.Rejected).failure)
        assertTrue(over.reason.contains("aggregate byte budget"))
    }

    @Test
    fun `a reviewed proc summary may use the larger allowance and nothing else may`() {
        val clock = AtlasClock()
        val big = "9".repeat(8 * 1024)
        val transport = AtlasFakeTransport(clock).putFile("/proc/pressure/cpu", AtlasFakeFile(big))
        val access = access(transport, clock)

        val scalar = access.read(request("/proc/pressure/cpu", AtlasUnit.UNKNOWN))
        assertEquals(AtlasFailure.BUDGET_EXCEEDED, (scalar as AtlasReadResult.Rejected).failure)

        val reviewed = access.read(request("/proc/pressure/cpu", AtlasUnit.UNKNOWN, reviewedProcSummary = true))
        val observation = (reviewed as AtlasReadResult.Observed).observation
        assertEquals(big, observation.textValue)
        assertEquals(null, observation.value)
    }

    // ---- causes are preserved, never invented --------------------------------------------------------

    @Test
    fun `every transport cause reaches the caller unchanged`() {
        val causes = listOf(
            AtlasFailure.PERMISSION_DENIED,
            AtlasFailure.READ_ONLY,
            AtlasFailure.MALFORMED,
            AtlasFailure.AMBIGUOUS,
            AtlasFailure.BACKEND_UNAVAILABLE,
            AtlasFailure.STALE,
            AtlasFailure.TIMED_OUT,
            AtlasFailure.BUDGET_EXCEEDED,
            AtlasFailure.CANCELLED,
            AtlasFailure.UNKNOWN_CAUSE,
            AtlasFailure.ABSENT,
        )
        val clock = AtlasClock()

        causes.forEach { cause ->
            val attempt = ReadOnlyProbeAccess(transport = fixedTransport(cause), clockMs = clock::nowMs)
            val result = attempt.read(request("/sys/class/thermal/thermal_zone0/temp"))
            val observed = (result as AtlasReadResult.Rejected).failure
            val expected = if (cause == AtlasFailure.ABSENT) AtlasFailure.UNKNOWN_CAUSE else cause
            assertEquals("cause $cause must not be rewritten", expected, observed)
        }
    }

    @Test
    fun `a privileged request without an authorized transport is unavailable and never asks`() {
        val clock = AtlasClock()
        var asked = 0
        val transport = AtlasFakeTransport(clock).putFile("/sys/class/kgsl/kgsl-3d0/gpuclk", AtlasFakeFile("585000000"))

        val withoutPrivilege = ReadOnlyProbeAccess(
            transport = transport,
            clockMs = clock::nowMs,
            privilegeAvailable = { asked += 1; false },
        )
        val refused = withoutPrivilege.read(request("/sys/class/kgsl/kgsl-3d0/gpuclk", requiresPrivilege = true))
        assertEquals(AtlasFailure.BACKEND_UNAVAILABLE, (refused as AtlasReadResult.Rejected).failure)
        assertEquals(1, asked)
        assertTrue(transport.operations.isEmpty())

        val withPrivilege = ReadOnlyProbeAccess(
            transport = transport,
            clockMs = clock::nowMs,
            privilegeAvailable = { asked += 1; true },
        )
        val allowed = withPrivilege.read(request("/sys/class/kgsl/kgsl-3d0/gpuclk", requiresPrivilege = true))
        assertTrue(allowed is AtlasReadResult.Observed)
        assertEquals(2, asked)
        assertFalse("an unprivileged read must not consult the privilege gate", transport.operations.isEmpty())
    }

    @Test
    fun `a non privileged read never consults the privilege gate`() {
        val clock = AtlasClock()
        var asked = 0
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("42000"))
        val access = ReadOnlyProbeAccess(
            transport = transport,
            clockMs = clock::nowMs,
            privilegeAvailable = { asked += 1; false },
        )

        assertTrue(access.read(request("/sys/class/thermal/thermal_zone0/temp", AtlasUnit.MILLI_CELSIUS)) is AtlasReadResult.Observed)
        assertEquals(0, asked)
    }

    // ---- units are never guessed ---------------------------------------------------------------------

    @Test
    fun `an unknown unit keeps the raw text and invents no dimension`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/devfreq/gpu0/governor", AtlasFakeFile("performance"))
        val result = access(transport, clock).read(request("/sys/class/devfreq/gpu0/governor", AtlasUnit.UNKNOWN))

        val observation = (result as AtlasReadResult.Observed).observation
        assertEquals(AtlasUnit.UNKNOWN, observation.unit)
        assertEquals(null, observation.value)
        assertEquals("performance", observation.textValue)
    }

    @Test
    fun `a non numeric value in a dimensioned unit is malformed`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/devfreq/gpu0/cur_freq", AtlasFakeFile("high"))
        val result = access(transport, clock).read(request("/sys/class/devfreq/gpu0/cur_freq", AtlasUnit.KILO_HERTZ))

        assertEquals(AtlasFailure.MALFORMED, (result as AtlasReadResult.Rejected).failure)
    }

    @Test
    fun `a non finite value is malformed and an empty value is malformed`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putFile("/sys/class/devfreq/nan", AtlasFakeFile("NaN"))
            .putFile("/sys/class/devfreq/inf", AtlasFakeFile("Infinity"))
            .putFile("/sys/class/devfreq/empty", AtlasFakeFile(null))
        val access = access(transport, clock)

        listOf("nan", "inf", "empty").forEach { name ->
            val result = access.read(request("/sys/class/devfreq/$name", AtlasUnit.KILO_HERTZ))
            assertEquals(name, AtlasFailure.MALFORMED, (result as AtlasReadResult.Rejected).failure)
        }
    }

    // ---- zero mutation, stats, fence ------------------------------------------------------------------

    @Test
    fun `the whole path performs read only operations and records every one of them`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock)
            .putDirectory("/sys/class/thermal", listOf("thermal_zone0", "thermal_zone1"))
            .putFile("/sys/class/thermal/thermal_zone0/temp", AtlasFakeFile("42000"))
            .putFile("/sys/class/thermal/thermal_zone1/temp", AtlasFakeFile(raw = null, exists = false))
            .putFile("/sys/class/thermal/bad*", AtlasFakeFile("1"))
        val access = access(transport, clock)

        access.list("/sys/class/thermal")
        access.read(request("/sys/class/thermal/thermal_zone0/temp", AtlasUnit.MILLI_CELSIUS))
        access.read(request("/sys/class/thermal/thermal_zone1/temp", AtlasUnit.MILLI_CELSIUS))
        access.read(request("/sys/class/thermal/bad*", AtlasUnit.UNKNOWN))

        val problems = AtlasFakeAudit.problems(transport, expectedOperations = transport.operations.size)
        assertTrue(problems.toString(), problems.isEmpty())
        assertTrue(AtlasFakeAudit.mutations(transport).isEmpty())
        assertTrue(transport.operations.all { it.kind in AtlasProbeKinds.SURFACE })
        assertEquals("a refused path is not an operation", 3, access.stats().operations)
    }

    @Test
    fun `stats report a reached limit and stay quiet on a clean run`() {
        val clock = AtlasClock()
        val names = (0 until 200).map { index -> "f%03d".format(index) }
        val transport = AtlasFakeTransport(clock).putDirectory("/sys/class/devfreq", names)
        val access = access(transport, clock)

        assertFalse(access.stats().limitsReached)
        access.list("/sys/class/devfreq")
        assertTrue(access.stats().limitsReached)
        assertEquals(AtlasReadBudget.DEFAULT.maxEntriesPerRoot, access.stats().entriesVisited)
    }

    // ---- concurrency cap (T2.4) ----------------------------------------------------------------------

    @Test
    fun `a nested attempt beyond the cap is refused instead of queueing`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putFile("/sys/class/devfreq/cur_freq", AtlasFakeFile("1800000"))
        val nested = mutableListOf<AtlasFailure>()
        val holder = AtomicReference<ReadOnlyProbeAccess>()
        val reentrant = ReentrantTransport(transport) { depth ->
            if (depth < 3) nested.add(failureOf(holder.get(), "/sys/class/devfreq/cur_freq"))
        }
        val reader = access(transport = reentrant, clock = clock, budget = budget(maxConcurrentReads = 2))
        holder.set(reader)

        val outer = reader.read(request("/sys/class/devfreq/cur_freq", AtlasUnit.KILO_HERTZ))

        assertTrue("the outer attempt still succeeds", outer is AtlasReadResult.Observed)
        assertEquals("one nested attempt fits, the one after it does not", 1, nested.count { it == AtlasFailure.NONE })
        assertEquals(1, nested.count { it == AtlasFailure.BUDGET_EXCEEDED })
        assertEquals("a refused attempt must not reach the transport", 2, reentrant.reads)
        assertTrue(reader.stats().limitsReached)
        assertEquals("no gate may leak after the job", 0, reader.stats().inFlight)
    }

    @Test
    fun `the privileged cap is one and a refused privileged read blocks nothing else`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putFile("/sys/class/kgsl/kgsl-3d0/gpuclk", AtlasFakeFile("585000000"))
        val nested = mutableListOf<AtlasFailure>()
        val holder = AtomicReference<ReadOnlyProbeAccess>()
        val reentrant = ReentrantTransport(transport) { depth ->
            if (depth < 2) {
                val reader = holder.get()
                nested.add((reader.read(request("/sys/class/kgsl/kgsl-3d0/gpuclk", requiresPrivilege = true)) as AtlasReadResult.Rejected).failure)
                nested.add(failureOf(reader, "/sys/class/kgsl/kgsl-3d0/gpuclk"))
            }
        }
        val reader = access(transport = reentrant, clock = clock)
        holder.set(reader)

        val outer = reader.read(request("/sys/class/kgsl/kgsl-3d0/gpuclk", requiresPrivilege = true))

        assertTrue(outer is AtlasReadResult.Observed)
        assertEquals("a second privileged read is refused", 1, nested.count { it == AtlasFailure.BUDGET_EXCEEDED })
        assertEquals("the unprivileged attempt still fits under the total cap", 1, nested.count { it == AtlasFailure.NONE })
        assertEquals(0, reader.stats().inFlight)
    }

    @Test
    fun `a threaded flood never exceeds the cap and leaks no gate`() {
        val clock = AtlasClock()
        val cap = AtlasReadBudget.DEFAULT.maxConcurrentReads
        val threads = 8
        val transport = ParkedTransport(cap)
        val reader = access(transport, clock)
        val entered = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val outcomes = Collections.synchronizedList(mutableListOf<AtlasReadResult>())
        val workers = (0 until threads).map {
            Thread {
                start.await()
                entered.countDown()
                outcomes.add(reader.read(request("/sys/class/devfreq/cur_freq", AtlasUnit.KILO_HERTZ)))
            }
        }
        workers.forEach(Thread::start)
        start.countDown()

        // Every worker has entered the attempt. The admitted ones stay parked inside the transport,
        // so the two slots are held while the rest arrive: a refusal here can only be the cap.
        assertTrue("all workers must enter", entered.await(10, TimeUnit.SECONDS))
        assertTrue("the admitted reads must be parked together", transport.awaitParked(10, TimeUnit.SECONDS))
        awaitOutcomes(outcomes, threads - cap, 10_000L)
        transport.release()
        workers.forEach { it.join(10_000L) }

        assertTrue("no worker may still be running", workers.none { it.isAlive })
        assertEquals("exactly the cap may be in flight", cap, transport.maxObserved)
        assertEquals("only the cap may reach the transport", cap, transport.admitted)
        assertEquals("every gate must be released", 0, reader.stats().inFlight)
        assertEquals("every worker must have an outcome", threads, outcomes.size)
        val refused = outcomes.filterIsInstance<AtlasReadResult.Rejected>()
        assertEquals(threads - cap, refused.size)
        assertTrue(
            "the refusal must name the concurrency cap",
            refused.all { it.failure == AtlasFailure.BUDGET_EXCEEDED && it.reason.contains("concurrency cap") },
        )
    }

    @Test
    fun `a refused attempt releases nothing it did not take`() {
        val clock = AtlasClock()
        val transport = AtlasFakeTransport(clock).putFile("/sys/class/devfreq/cur_freq", AtlasFakeFile("1800000"))
        val access = access(transport, clock, budget = budget(maxConcurrentReads = 1))

        assertTrue(access.read(request("/sys/class/devfreq/cur_freq", AtlasUnit.KILO_HERTZ)) is AtlasReadResult.Observed)
        assertEquals(0, access.stats().inFlight)
        assertTrue("the gate must be reusable after an attempt", access.read(request("/sys/class/devfreq/cur_freq", AtlasUnit.KILO_HERTZ)) is AtlasReadResult.Observed)
    }

    @Test
    fun `a result from a cancelled generation cannot be published`() {
        val clock = AtlasClock()
        val access = access(AtlasFakeTransport(clock), clock)

        val generation = clock.generation()
        assertTrue(access.isCurrent(generation))
        clock.bumpGeneration()
        assertFalse(access.isCurrent(generation))
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private fun access(
        transport: AtlasReadTransport,
        clock: AtlasClock,
        budget: AtlasReadBudget = AtlasReadBudget.DEFAULT,
    ): ReadOnlyProbeAccess = ReadOnlyProbeAccess(
        transport = transport,
        budget = budget,
        clockMs = clock::nowMs,
        currentGeneration = clock::generation,
    )

    private fun budget(
        jobDeadlineMs: Long = AtlasReadBudget.DEFAULT.jobDeadlineMs,
        maxScalarBytes: Int = AtlasReadBudget.DEFAULT.maxScalarBytes,
        maxAggregateBytes: Int = AtlasReadBudget.DEFAULT.maxAggregateBytes,
        maxOperations: Int = AtlasReadBudget.DEFAULT.maxOperations,
        maxConcurrentReads: Int = AtlasReadBudget.DEFAULT.maxConcurrentReads,
        maxConcurrentPrivileged: Int = AtlasReadBudget.DEFAULT.maxConcurrentPrivileged,
    ): AtlasReadBudget = AtlasReadBudget(
        jobDeadlineMs = jobDeadlineMs,
        operationDeadlineMs = minOf(AtlasReadBudget.DEFAULT.operationDeadlineMs, jobDeadlineMs),
        maxEntriesPerRoot = AtlasReadBudget.DEFAULT.maxEntriesPerRoot,
        maxEntriesTotal = AtlasReadBudget.DEFAULT.maxEntriesTotal,
        maxScalarBytes = maxScalarBytes,
        maxReviewedProcBytes = maxOf(maxScalarBytes, AtlasReadBudget.DEFAULT.maxReviewedProcBytes),
        maxAggregateBytes = maxAggregateBytes,
        maxSymlinkHops = AtlasReadBudget.DEFAULT.maxSymlinkHops,
        maxOperations = maxOperations,
        maxConcurrentReads = maxConcurrentReads,
        maxConcurrentPrivileged = minOf(maxConcurrentPrivileged, maxConcurrentReads),
    )

    private fun request(
        path: String,
        unit: AtlasUnit = AtlasUnit.MILLI_CELSIUS,
        reviewedProcSummary: Boolean = false,
        requiresPrivilege: Boolean = false,
    ): AtlasProbeRequest = AtlasProbeRequest(
        id = "test.observation",
        domain = AtlasDomain.THERMAL,
        providerId = "fake",
        catalogVersion = AtlasCatalog.SCHEMA_VERSION,
        sourceId = "L05",
        path = path,
        unit = unit,
        requiresPrivilege = requiresPrivilege,
        reviewedProcSummary = reviewedProcSummary,
    )

    /** Waits for [expected] attempts to have returned, so assertions cannot race the scheduler. */
    private fun awaitOutcomes(outcomes: List<AtlasReadResult>, expected: Int, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (outcomes.size < expected && System.currentTimeMillis() < deadline) Thread.sleep(5L)
        assertEquals("attempts did not return in time", expected, outcomes.size)
    }

    private fun failureOf(access: ReadOnlyProbeAccess, path: String): AtlasFailure {
        val result = access.read(request(path))
        if (result is AtlasReadResult.Observed) return AtlasFailure.NONE
        return (result as AtlasReadResult.Rejected).failure
    }

    /** Wraps a transport and lets the test attempt reads while one is already in flight. */
    private class ReentrantTransport(
        private val delegate: AtlasReadTransport,
        private val onRead: (Int) -> Unit,
    ) : AtlasReadTransport {

        var reads: Int = 0
            private set

        override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
            reads += 1
            onRead(reads)
            return delegate.readText(path, maxBytes)
        }

        override fun listNames(path: String, limit: Int): AtlasTransportList = delegate.listNames(path, limit)

        override fun canonicalPath(path: String, maxHops: Int): String? = delegate.canonicalPath(path, maxHops)
    }

    /**
     * Parks every admitted read until the test releases it, so "how many were in flight" is an
     * assertion about the gate and not a race against the scheduler. Refusals are counted here
     * too, so a refusal that came from anywhere else than the cap would be visible.
     */
    private class ParkedTransport(private val cap: Int) : AtlasReadTransport {

        private val active = AtomicInteger(0)
        private val admittedCount = AtomicInteger(0)
        private val parked = CountDownLatch(cap)
        private val release = CountDownLatch(1)

        @Volatile
        var maxObserved: Int = 0
            private set

        val admitted: Int get() = admittedCount.get()

        fun awaitParked(timeout: Long, unit: TimeUnit): Boolean = parked.await(timeout, unit)

        fun release() {
            release.countDown()
        }

        override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
            admittedCount.incrementAndGet()
            val now = active.incrementAndGet()
            synchronized(this) { if (now > maxObserved) maxObserved = now }
            try {
                parked.countDown()
                release.await(10, TimeUnit.SECONDS)
                return AtlasTransportRead.Text("1800000", truncated = false)
            } finally {
                active.decrementAndGet()
            }
        }

        override fun listNames(path: String, limit: Int): AtlasTransportList =
            AtlasTransportList(emptyList(), AtlasFailure.BACKEND_UNAVAILABLE, "not used")

        override fun canonicalPath(path: String, maxHops: Int): String = path
    }

    /** A transport whose only behavior is reporting one chosen cause. */
    private fun fixedTransport(cause: AtlasFailure): AtlasReadTransport = object : AtlasReadTransport {
        override fun readText(path: String, maxBytes: Int): AtlasTransportRead =
            AtlasTransportRead.Failed(cause, "fixed cause $cause")

        override fun listNames(path: String, limit: Int): AtlasTransportList =
            AtlasTransportList(emptyList(), cause, "fixed cause $cause")

        override fun canonicalPath(path: String, maxHops: Int): String = path
    }
}
