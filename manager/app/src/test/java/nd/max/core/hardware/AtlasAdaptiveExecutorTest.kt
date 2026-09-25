/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteEvidence
import nd.max.core.atlas.AtlasStoreIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasAdaptiveExecutorTest {
    @Test
    fun `verified rollback lets Atlas try the next route`() {
        val calls = mutableListOf<String>()
        val executor = AtlasAdaptiveExecutor(port { request ->
            calls += request.routeId
            if (request.routeId == "route-first") {
                failed(request, rollbackVerified = true)
            } else {
                success(request)
            }
        })

        val result = executor.execute(
            intent = intent(),
            bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
        )

        assertEquals(listOf("route-first", "route-second"), calls)
        assertTrue(result.successful)
        assertEquals("route-second", result.selectedRouteId)
        assertFalse(result.fallbackStopped)
    }

    @Test
    fun `unverified rollback stops fallback to avoid compounding unknown state`() {
        val calls = mutableListOf<String>()
        val executor = AtlasAdaptiveExecutor(port { request ->
            calls += request.routeId
            failed(request, rollbackVerified = false)
        })

        val result = executor.execute(
            intent = intent(),
            bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
        )

        assertEquals(listOf("route-first"), calls)
        assertFalse(result.successful)
        assertTrue(result.fallbackStopped)
    }

    @Test
    fun `a route that previously stranded the baseline is not retried in this boot`() {
        val calls = mutableListOf<String>()
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })
        memory.noteFailed("cpu_frequency", "route-first", rollbackVerified = false)
        val executor = AtlasAdaptiveExecutor(port { request ->
            calls += request.routeId
            success(request)
        }, memory = memory)

        val result = executor.execute(
            intent = intent(),
            bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
        )

        assertEquals(listOf("route-second"), calls)
        assertTrue(result.successful)
        assertEquals(listOf("route-first" to "route-quarantined-after-unverified-rollback"), result.skipped)
    }

    @Test
    fun `a verified route is attempted before its equally safe alternatives`() {
        val calls = mutableListOf<String>()
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })
        memory.noteVerified("cpu_frequency", "route-second")
        val executor = AtlasAdaptiveExecutor(port { request ->
            calls += request.routeId
            success(request)
        }, memory = memory)

        executor.execute(
            intent = intent(),
            bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
        )

        assertEquals(listOf("route-second"), calls)
    }

    @Test
    fun `learning never promotes a route past a safer transport tier`() {
        val calls = mutableListOf<String>()
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })
        // The remembered route is a sysfs writer; the alternative is a platform-owned hint.
        memory.noteVerified("cpu_frequency", "route-sysfs")
        val executor = AtlasAdaptiveExecutor(port { request ->
            calls += request.routeId
            success(request)
        }, memory = memory)

        executor.execute(
            intent = intent(),
            bindings = listOf(
                binding("route-sysfs", transport = AtlasControlTransport.ARBITER_SYSFS),
                binding("route-platform", transport = AtlasControlTransport.PLATFORM_HINT),
            ),
        )

        assertEquals(listOf("route-platform"), calls)
    }

    @Test
    fun `a successful attempt is remembered for the next time`() {
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })
        val executor = AtlasAdaptiveExecutor(port { request -> success(request) }, memory = memory)

        executor.execute(intent = intent(), bindings = listOf(binding("route-first")))

        assertEquals("route-first", memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `a reboot recovers a quarantined route and subsequent calls keep using it`() {
        val io = InMemoryIo()
        val firstBoot = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })
        val firstExecutor = AtlasAdaptiveExecutor(port { failed(it, rollbackVerified = false) }, firstBoot)
        assertTrue(firstExecutor.execute(intent(), listOf(binding("route-first"))).fallbackStopped)

        val calls = mutableListOf<String>()
        val afterReboot = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 8L })
        val recovered = AtlasAdaptiveExecutor(port { request ->
            calls += request.routeId
            success(request)
        }, afterReboot)

        repeat(2) {
            assertTrue(recovered.execute(intent(), listOf(binding("route-first"))).successful)
        }
        assertEquals(listOf("route-first", "route-first"), calls)
        assertEquals(0, afterReboot.entries().single().rollbackFailures)
    }

    @Test
    fun `process restart with unknown boot does not retry a quarantined writer`() {
        val io = InMemoryIo()
        AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })
            .noteFailed("cpu_frequency", "route-first", rollbackVerified = false)
        var called = false
        val executor = AtlasAdaptiveExecutor(port {
            called = true
            success(it)
        }, AtlasRouteMemory(io, clockMs = { 2_000L }))

        val result = executor.execute(intent(), listOf(binding("route-first")))

        assertFalse(called)
        assertFalse(result.successful)
        assertEquals(listOf("route-first" to "route-quarantined-after-unverified-rollback"), result.skipped)
    }

    private fun port(block: (HardwareRepairRequest) -> HardwareRepairResult) = object : AtlasRepairPort {
        override fun execute(request: HardwareRepairRequest): HardwareRepairResult = block(request)
    }

    private fun intent() = AtlasControlIntent(
        target = AtlasControlTarget.CPU_FREQUENCY,
        goal = AtlasControlGoal.PERFORMANCE,
        desired = "next",
    )

    private fun binding(
        id: String,
        priority: Int = 0,
        transport: AtlasControlTransport = AtlasControlTransport.ROOT_DAEMON,
    ) = AtlasRouteBinding(
        candidate = AtlasRouteCandidate(
            id = id,
            priority = priority,
            evidence = AtlasRouteEvidence(
                providerId = "test",
                transport = transport,
                target = AtlasControlTarget.CPU_FREQUENCY,
                readable = true,
                privilegeAvailable = true,
                unitProven = true,
                baselineReadable = true,
                rollbackProven = true,
                reviewed = true,
            ),
        ),
        request = HardwareRepairRequest(
            routeId = id,
            key = "cpu.test",
            owner = ControlOwnership.Owner.MAX_AI,
            token = "atlas-test",
            desired = "next",
            apply = { true },
            read = { "base" },
            restore = { true },
            baseline = "base",
        ),
    )

    private fun success(request: HardwareRepairRequest) = HardwareRepairResult(
        routeId = request.routeId,
        key = request.key,
        state = HardwareRepairState.CONFIRMED_WINDOW,
        requested = request.desired,
        actual = request.desired,
        applied = true,
        verified = true,
        stabilitySamples = 3,
        rollbackAttempted = false,
        rollbackVerified = null,
    )

    private fun failed(request: HardwareRepairRequest, rollbackVerified: Boolean) = HardwareRepairResult(
        routeId = request.routeId,
        key = request.key,
        state = if (rollbackVerified) HardwareRepairState.APPLY_FAILED_ROLLED_BACK else HardwareRepairState.ROLLBACK_UNVERIFIED,
        requested = request.desired,
        actual = null,
        applied = true,
        verified = false,
        stabilitySamples = 0,
        rollbackAttempted = true,
        rollbackVerified = rollbackVerified,
        error = "test-failure",
    )

    private class InMemoryIo : AtlasStoreIo {
        private val entries = LinkedHashMap<String, String>()
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.sorted()
    }
}
