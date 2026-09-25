/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class HardwareRepairExecutorTest {
    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("repair-executor-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    @Test
    fun `stable readback produces verified stable result`() {
        var live = "default"
        val result = HardwareRepairExecutor(HardwareControlArbiter(), sleep = {}).execute(
            request(
                read = { live },
                write = { live = it; true },
                restore = { live = it; true },
            ),
        )

        assertTrue(result.successful)
        assertEquals(HardwareRepairState.CONFIRMED_WINDOW, result.state)
        assertEquals(3, result.stabilitySamples)
        assertEquals("performance", result.actual)
    }

    @Test
    fun `the confirmation window is the reported bound, not a promise of sustained stability`() {
        val base = request(read = { "performance" }, write = { true }, restore = { true })
        val threeSamples = base.copy(stabilitySamples = 3, stabilityIntervalMs = 40L)
        val oneSample = base.copy(stabilitySamples = 1, stabilityIntervalMs = 40L)
        val fiveSamples = base.copy(stabilitySamples = 5, stabilityIntervalMs = 100L)

        // Three samples forty milliseconds apart observe the value for eighty milliseconds. Anything
        // read as "stable" must be read against exactly this number, which is why it is carried on the
        // request rather than implied by the state name.
        assertEquals(80L, threeSamples.confirmationWindowMs)
        assertEquals(0L, oneSample.confirmationWindowMs)
        assertEquals(400L, fiveSamples.confirmationWindowMs)
    }

    @Test
    fun `a transaction label is canonical for every real control key shape`() {
        val labels = listOf(
            "cpu_limits:policy0",
            "cpu_limits:/sys/devices/system/cpu/cpufreq/policy7",
            "gpu_frequency:kgsl-3d0",
            "cpu_boost",
            "1",
            "",
        ).map(HardwareRepairExecutor::labelFor)

        labels.forEach { label ->
            assertTrue("$label must be a canonical route id", label.matches(HardwareRepairRequest.ROUTE_ID))
            assertTrue("$label must stay bounded", label.length <= 64)
        }
        assertEquals("cpu-limits-policy0", labels[0])
        assertEquals("cpu-boost", labels[3])
        // Distinct keys may collide after canonicalisation, which is why the label is never used for
        // ownership: the arbiter keys on the untouched `Request.key`.
        assertTrue(labels.all { it.isNotBlank() })
    }

    @Test
    fun `external writer causes rollback instead of false success`() {
        var live = "default"
        var reads = 0
        val result = HardwareRepairExecutor(HardwareControlArbiter(), sleep = {}).execute(
            request(
                read = {
                    reads += 1
                    if (reads == 4) "vendor" else live
                },
                write = { live = it; true },
                restore = { live = it; true },
            ),
        )

        assertFalse(result.successful)
        assertEquals(HardwareRepairState.DRIFT_ROLLED_BACK, result.state)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackVerified == true)
        assertEquals("default", live)
    }

    @Test
    fun `failed apply is reported as rolled back`() {
        var live = "default"
        val result = HardwareRepairExecutor(HardwareControlArbiter(), sleep = {}).execute(
            request(
                read = { live },
                write = { live = "corrupt"; false },
                restore = { live = it; true },
            ),
        )

        assertFalse(result.successful)
        assertEquals(HardwareRepairState.APPLY_FAILED_ROLLED_BACK, result.state)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackVerified == true)
        assertEquals("default", live)
    }

    private fun request(
        read: () -> String,
        write: (String) -> Boolean,
        restore: (String) -> Boolean,
    ): HardwareRepairRequest = HardwareRepairRequest(
        routeId = "test.route",
        key = "test.knob",
        owner = ControlOwnership.Owner.PER_APP,
        token = "test-token",
        desired = "performance",
        apply = write,
        read = read,
        restore = restore,
        stabilitySamples = 3,
        stabilityIntervalMs = 0,
    )
}
