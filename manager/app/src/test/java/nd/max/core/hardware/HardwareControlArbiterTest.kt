package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareControlArbiterTest {
    @Test
    fun lowerPriorityRequestIsBlockedAndRestoredOnRelease() {
        val arbiter = HardwareControlArbiter()
        var live = "global"
        val global = arbiter.submit(
            key = "cpu.gov",
            owner = ControlOwnership.Owner.GLOBAL_PROFILE,
            token = "global",
            desired = "schedutil",
            apply = { live = it; true },
            read = { live },
            baseline = "userspace",
            restore = { live = it; true },
        )
        assertTrue(global.verified)
        assertEquals("schedutil", live)

        val perApp = arbiter.submit(
            key = "cpu.gov",
            owner = ControlOwnership.Owner.PER_APP,
            token = "app",
            desired = "performance",
            apply = { live = it; true },
            read = { live },
        )
        assertTrue(perApp.verified)
        assertEquals("performance", live)

        val blockedGlobal = arbiter.submit(
            key = "cpu.gov",
            owner = ControlOwnership.Owner.GLOBAL_PROFILE,
            token = "global",
            desired = "powersave",
            apply = { live = it; true },
            read = { live },
        )
        assertTrue(blockedGlobal.blocked)
        assertEquals("performance", live)

        arbiter.release("cpu.gov", "app")
        assertEquals("powersave", live)
    }

    @Test
    fun releaseWithoutContenderRestoresBaseline() {
        val arbiter = HardwareControlArbiter()
        var live = "default"
        arbiter.submit(
            key = "gpu.gov",
            owner = ControlOwnership.Owner.PER_APP,
            token = "app",
            desired = "performance",
            apply = { live = it; true },
            read = { live },
            baseline = "default",
            restore = { live = it; true },
        )
        assertEquals("performance", live)
        arbiter.release("gpu.gov", "app", restore = true)
        assertEquals("default", live)
        assertFalse(ControlOwnership.snapshot().any { it.key == "gpu.gov" })
    }
}
