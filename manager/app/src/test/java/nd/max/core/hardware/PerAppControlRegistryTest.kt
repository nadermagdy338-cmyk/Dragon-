package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * The two failures this suite exists for, both reported from a real device:
 *
 * 1. A knob an external writer took back *inside* the confirmation window used to be dropped from the
 *    registry, so the bounded drift pass never looked at it again and the user's per-app rule was
 *    silently lost for the rest of the session. The intent has to survive so the pass can repair it.
 * 2. The drift pass iterated the live `entries` view while removing from it, so the first knob that
 *    failed aborted the whole pass with `ConcurrentModificationException` and every knob after it went
 *    unverified. The pass must report every knob.
 */
class PerAppControlRegistryTest {

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("per-app-registry-test").toFile()
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
    fun `a knob taken back inside the window stays registered so the drift pass can repair it`() {
        var live = "idle"
        var reads = 0
        val registry = registry()

        val owned = registry.ownValue(
            key = "cpu_limits:policy0",
            desired = "boost",
            apply = { value -> live = value; true },
            read = {
                reads += 1
                // The fifth read is the second confirmation sample: the arbiter has already verified
                // its own read-back and the value is taken back right after.
                if (reads == 5) "vendor" else live
            },
            baseline = "idle",
            restore = { value -> live = value; true },
        )

        assertFalse("the drift must not be reported as success", owned)
        assertEquals("idle", live)
        assertEquals(
            "the reason must name the external writer",
            "external-writer-drift-baseline-restored",
            registry.refusalReasons()["cpu_limits:policy0"],
        )

        // Before the fix this list was empty: the entry had been removed on the way in, and no later
        // pass had any idea the knob had ever been asked for.
        val repaired = registry.verifyAndRepair()
        assertEquals("the intent must survive the drift", 1, repaired.size)
        assertTrue("and the pass must be able to repair it", repaired.single().successful)
        assertNull(registry.refusalReasons()["cpu_limits:policy0"])
    }

    @Test
    fun `the drift pass reports every knob instead of aborting on the first failure`() {
        var liveA = "idle"
        var liveB = "idle"
        var failA = false
        val registry = registry()
        val applyA: (String) -> Boolean = { value -> if (failA) false else { liveA = value; true } }

        assertTrue(registry.ownValue("cpu_limits:policy0", "a", applyA, { liveA }))
        assertTrue(registry.ownValue("cpu_limits:policy4", "b", { liveB = it; true }, { liveB }))

        // A vendor daemon rewrites the first knob, and the knob's own apply then refuses to take it back.
        liveA = "vendor"
        failA = true

        val results = registry.verifyAndRepair()

        assertEquals("both knobs must be reported, not just the ones before the failure", 2, results.size)
        val first = results.first { it.key == "cpu_limits:policy0" }
        val second = results.first { it.key == "cpu_limits:policy4" }
        assertFalse("the reclaimed knob must not be reported as verified", first.verified)
        assertTrue("and the knob after it must still be verified", second.verified)
        assertEquals("vendor", first.actual)
    }

    @Test
    fun `a knob the user locked is refused and never retried`() {
        var live = "idle"
        ManualControlLocks.lock("cpu_limits:policy0", "user", "manual", "idle")
        val registry = registry()

        val owned = registry.ownValue(
            key = "cpu_limits:policy0",
            desired = "boost",
            apply = { value -> live = value; true },
            read = { live },
            baseline = "idle",
            restore = { value -> live = value; true },
        )

        assertFalse(owned)
        assertEquals("idle", live)
        assertEquals("the gate's reason must be reported, not silence", "manual-lock", registry.refusalReasons()["cpu_limits:policy0"])
        assertTrue("a refusal is quarantined, not retried forever", registry.verifyAndRepair().isEmpty())
    }

    @Test
    fun `the drift pass says whether the value had actually diverged`() {
        var live = "idle"
        val registry = registry()
        assertTrue(registry.ownValue("cpu_limits:policy0", "boost", { live = it; true }, { live }))

        // Nothing external touched the knob: the pass re-confirms it and must not claim a repair, which
        // it would if "repaired" were inferred from `applied` (the arbiter reports `applied` for a value
        // it found already correct, so that inference would fire every ten seconds on a healthy knob).
        val untouched = registry.verifyAndRepair().single()
        assertTrue(untouched.successful)
        assertEquals(false, untouched.driftedBefore)

        // A vendor daemon takes the value back: the next pass must both repair it and say so.
        live = "vendor"
        val reclaimed = registry.verifyAndRepair().single()
        assertTrue(reclaimed.successful)
        assertEquals(true, reclaimed.driftedBefore)
        assertEquals("boost", reclaimed.actual)
    }

    /** Zero-interval confirmation: the window is behaviour the executor tests cover, not this suite. */
    private fun registry() = PerAppControlRegistry(
        mutationGate = HardwareControlArbiter(),
        confirmationSamples = 3,
        confirmationIntervalMs = 0L,
        sleep = {},
    )
}
