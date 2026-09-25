/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class HardwareControlArbiterTest {
    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("arbiter-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        // The lock store is process-global too: point it at a fresh empty
        // directory so no other test class can make a submit fail closed here.
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    @Test
    fun lowerPriorityRequestIsBlockedAndRestoredOnRelease() {
        val arbiter = HardwareControlArbiter()
        var live = "global"
        assertTrue(arbiter.submit(
            key = "cpu.gov",
            owner = ControlOwnership.Owner.GLOBAL_PROFILE,
            token = "global",
            desired = "schedutil",
            apply = { live = it; true },
            read = { live },
            restore = { live = it; true },
        ).verified)

        assertTrue(arbiter.submit(
            key = "cpu.gov",
            owner = ControlOwnership.Owner.PER_APP,
            token = "app",
            desired = "performance",
            apply = { live = it; true },
            read = { live },
        ).verified)

        val blocked = arbiter.submit(
            key = "cpu.gov",
            owner = ControlOwnership.Owner.GLOBAL_PROFILE,
            token = "global",
            desired = "powersave",
            apply = { live = it; true },
            read = { live },
        )
        assertTrue(blocked.blocked)
        assertEquals("performance", live)

        arbiter.release("cpu.gov", "app")
        assertEquals("powersave", live)
        assertEquals("powersave", arbiter.reconcile("cpu.gov")?.actual)
    }

    @Test
    fun failedApplyRollsBackAndDoesNotCommitLease() {
        val arbiter = HardwareControlArbiter()
        var live = "default"
        val result = arbiter.submit(
            key = "gpu.gov",
            owner = ControlOwnership.Owner.MAX_AI,
            token = "ai",
            desired = "performance",
            apply = { live = "corrupt"; false },
            read = { live },
            restore = { live = it; true },
        )

        assertFalse(result.verified)
        assertTrue(result.rollbackAttempted)
        assertEquals(true, result.rollbackVerified)
        assertEquals("default", live)
        assertNull(SharedHardwareOwnershipStore.snapshot().firstOrNull { it.key == "gpu.gov" })
        assertFalse(ControlOwnership.snapshot().any { it.key == "gpu.gov" })
    }

    @Test
    fun releaseWithoutContenderRestoresCapturedLiveBaseline() {
        val arbiter = HardwareControlArbiter()
        var live = "default"
        arbiter.submit(
            key = "gpu.gov",
            owner = ControlOwnership.Owner.PER_APP,
            token = "app",
            desired = "performance",
            apply = { live = it; true },
            read = { live },
            baseline = "stale-caller-value",
            restore = { live = it; true },
        )
        assertEquals("performance", live)
        val released = arbiter.release("gpu.gov", "app", restore = true)
        assertEquals("default", live)
        assertTrue(released?.verified == true)
        assertFalse(ControlOwnership.snapshot().any { it.key == "gpu.gov" })
    }

    @Test
    fun lowerIntentRemainsJournaledDuringPreemption() {
        val arbiter = HardwareControlArbiter()
        var live = "default"
        arbiter.submit(
            key = "cpu.limit",
            owner = ControlOwnership.Owner.MAX_AI,
            token = "ai",
            desired = "mid",
            apply = { live = it; true },
            read = { live },
        )
        arbiter.submit(
            key = "cpu.limit",
            owner = ControlOwnership.Owner.SAFETY,
            token = "safety",
            desired = "low",
            apply = { live = it; true },
            read = { live },
        )

        val journal = SharedHardwareOwnershipStore.snapshot().filter { it.key == "cpu.limit" }
        assertEquals(2, journal.size)
        assertEquals(ControlOwnership.Owner.SAFETY, journal.maxBy { it.owner.priority }.owner)
    }

    @Test
    fun equalPriorityWinnerIsDeterministicAcrossSubmissionOrder() {
        fun winner(tokens: List<String>): String {
            SharedHardwareOwnershipStore.configure(
                Files.createTempDirectory("arbiter-order-test").toFile(),
                appUid = 0,
                processId = ProcessHandle.current().pid().toInt(),
            )
            val arbiter = HardwareControlArbiter()
            var live = "default"
            tokens.forEach { token ->
                arbiter.submit(
                    key = "cpu.equal",
                    owner = ControlOwnership.Owner.MAX_AI,
                    token = token,
                    desired = token,
                    apply = { live = it; true },
                    read = { live },
                )
            }
            return SharedHardwareOwnershipStore.winnerSnapshot()
                .single { it.key == "cpu.equal" }
                .token
        }

        assertEquals(winner(listOf("alpha", "omega")), winner(listOf("omega", "alpha")))
    }

    @Test
    fun winnerSnapshotExposesOnlyTheHighestPriorityIntentPerKey() {
        val arbiter = HardwareControlArbiter()
        var live = "default"
        listOf(
            ControlOwnership.Owner.MAX_AI to "ai",
            ControlOwnership.Owner.PER_APP to "app",
        ).forEach { (owner, token) ->
            arbiter.submit(
                key = "gpu.owner",
                owner = owner,
                token = token,
                desired = token,
                apply = { live = it; true },
                read = { live },
            )
        }

        val winners = SharedHardwareOwnershipStore.winnerSnapshot()
            .filter { it.key == "gpu.owner" }
        assertEquals(1, winners.size)
        assertEquals(ControlOwnership.Owner.PER_APP, winners.single().owner)
    }
}
