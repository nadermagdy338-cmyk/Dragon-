package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Manual locks are the only mechanism that keeps an automated owner off a knob
 * the user set by hand (INV-3), and they must never outrank safety (INV-2).
 *
 * These tests drive the real arbiter so the guarantee is proven at the gate —
 * a planner that politely avoids locked knobs would pass a model-only test and
 * still lose the knob to any future caller.
 */
class ManualControlLocksTest {
    private lateinit var rootDir: File

    @Before
    fun configureStores() {
        rootDir = Files.createTempDirectory("manual-locks-test").toFile()
        SharedHardwareOwnershipStore.configure(
            rootDir,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(rootDir)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    private fun submit(
        arbiter: HardwareControlArbiter,
        key: String,
        owner: ControlOwnership.Owner,
        token: String,
        desired: String,
        live: () -> String,
        setLive: (String) -> Unit,
        baseline: String? = null,
    ) = arbiter.submit(
        key = key,
        owner = owner,
        token = token,
        desired = desired,
        apply = { setLive(it); true },
        read = live,
        baseline = baseline,
        restore = { setLive(it); true },
    )

    @Test
    fun lockedKnobRejectsTheMindButNotSafety() {
        val arbiter = HardwareControlArbiter()
        var live = "balanced"
        ManualControlLocks.lock(
            key = "cpu_limits:policy0",
            token = "manual:cpu_limits:policy0",
            desired = "1800000:2400000",
            baseline = "balanced",
        )

        val aiResult = submit(
            arbiter, "cpu_limits:policy0", ControlOwnership.Owner.MAX_AI, "maxai",
            "1800000:2800000", { live }, { live = it },
        )
        assertTrue(aiResult.blocked)
        assertEquals("manual-lock", aiResult.error)
        assertEquals("balanced", live)
        // A refused write must not leave an intent behind: the ledger would
        // otherwise advertise a claim that was never allowed to apply.
        assertFalse(SharedHardwareOwnershipStore.snapshot().any { it.key == "cpu_limits:policy0" })

        val safetyResult = submit(
            arbiter, "cpu_limits:policy0", ControlOwnership.Owner.SAFETY, "safety",
            "1800000:1200000", { live }, { live = it },
        )
        assertTrue(safetyResult.verified)
        assertEquals("1800000:1200000", live)
    }

    @Test
    fun lockHolderTokenStillWritesThroughTheGate() {
        val arbiter = HardwareControlArbiter()
        var live = "balanced"
        val token = "manual:cpu_limits:policy4"
        ManualControlLocks.lock("cpu_limits:policy4", token, "1800000:2400000", "balanced")

        val result = submit(
            arbiter, "cpu_limits:policy4", ControlOwnership.Owner.GLOBAL_PROFILE, token,
            "1800000:2400000", { live }, { live = it }, baseline = "balanced",
        )
        assertTrue(result.verified)
        assertEquals("1800000:2400000", live)
    }

    @Test
    fun unlockGivesTheKnobBackToTheMind() {
        val arbiter = HardwareControlArbiter()
        var live = "balanced"
        val key = "gpu_frequency:kgsl"
        ManualControlLocks.lock(key, "manual:$key", "350", "balanced")

        assertTrue(
            submit(
                arbiter, key, ControlOwnership.Owner.MAX_AI, "maxai",
                "500", { live }, { live = it },
            ).blocked
        )
        assertEquals("balanced", live)

        assertEquals("350", ManualControlLocks.unlock(key)?.desired)
        val after = submit(
            arbiter, key, ControlOwnership.Owner.MAX_AI, "maxai",
            "500", { live }, { live = it },
        )
        assertTrue(after.verified)
        assertEquals("500", live)
    }

    @Test
    fun lockSurvivesProcessRestart() {
        ManualControlLocks.lock("cpu_boost", "manual:cpu_boost", "1", "0")

        // A restart re-reads the control directory from disk. Durability is the
        // whole point: a journaled intent expires with its owning process, which
        // would silently hand the user's knob back to the mind.
        ManualControlLocks.configure(rootDir)

        assertEquals(setOf("cpu_boost"), ManualControlLocks.lockedKeys())
        assertTrue(
            ManualControlLocks.blocks(ControlOwnership.Owner.MAX_AI, "cpu_boost", "maxai")
        )
        assertTrue(
            ManualControlLocks.blocks(ControlOwnership.Owner.PER_APP, "cpu_boost", "per-app")
        )
        assertFalse(
            ManualControlLocks.blocks(ControlOwnership.Owner.SAFETY, "cpu_boost", "safety")
        )
        assertFalse(
            ManualControlLocks.blocks(ControlOwnership.Owner.RECOVERY, "cpu_boost", "recovery")
        )
    }

    @Test
    fun firstBaselineSurvivesLaterManualAdjustments() {
        val token = "manual:cpu_limits:policy0"
        ManualControlLocks.lock("cpu_limits:policy0", token, "a:b", "stock")
        ManualControlLocks.lock("cpu_limits:policy0", token, "c:d", "second-session-state")

        val lock = ManualControlLocks.find("cpu_limits:policy0")
        assertEquals("stock", lock?.baseline)
        assertEquals("c:d", lock?.desired)
    }

    @Test
    fun unconfiguredStoreNeverBlocksAnything() {
        // Fail open when the directory is unknown: a lock store that cannot be
        // read must not become a reason the safety engine cannot act.
        ManualControlLocks.configure(File(rootDir, "not-created-yet").also { it.mkdirs() })
        ManualControlLocks.clearAll()
        assertFalse(
            ManualControlLocks.blocks(ControlOwnership.Owner.MAX_AI, "cpu_limits:policy0", "maxai")
        )
        assertEquals(emptySet<String>(), ManualControlLocks.lockedKeys())
    }
}
