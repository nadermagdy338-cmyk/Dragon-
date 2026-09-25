/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class CoalescingCycleRunnerTest {
    @Test
    fun `sequential requests each evaluate once`() = runBlocking {
        val runner = CoalescingCycleRunner()
        var calls = 0
        repeat(3) { runner.run { calls++ } }
        assertEquals(3, calls)
    }

    @Test
    fun `a burst during evaluation is consumed in one follow-up`() = runBlocking {
        withTimeout(5_000L) {
            val runner = CoalescingCycleRunner()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                runner.run { calls++; release.await() }
            }
            val burst = List(100) {
                launch(start = CoroutineStart.UNDISPATCHED) { runner.run { calls++ } }
            }
            assertEquals(1, calls)
            release.complete(Unit)
            (burst + first).joinAll()
            assertEquals(2, calls)
        }
    }

    @Test
    fun `a failing batch is not retried by every waiter`() = runBlocking {
        withTimeout(5_000L) {
            val runner = CoalescingCycleRunner()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                runner.run { calls++; release.await() }
            }
            val burst = List(50) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        runner.run { calls++; error("expected failure") }
                    } catch (_: IllegalStateException) {
                        // One attempted batch failed, not fifty independent cycles.
                    }
                }
            }
            release.complete(Unit)
            (burst + first).joinAll()
            assertEquals(2, calls)
            runner.run { calls++ }
            assertEquals(3, calls)
        }
    }

    @Test
    fun `cancelled owner releases gate and pending requests can proceed`() = runBlocking {
        withTimeout(5_000L) {
            val runner = CoalescingCycleRunner()
            var calls = 0
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                runner.run { calls++; CompletableDeferred<Unit>().await() }
            }
            val next = launch(start = CoroutineStart.UNDISPATCHED) { runner.run { calls++ } }
            first.cancelAndJoin()
            next.join()
            assertEquals(2, calls)
        }
    }

    @Test
    fun `new request during follow-up is not lost`() = runBlocking {
        withTimeout(5_000L) {
            val runner = CoalescingCycleRunner()
            val releaseFirst = CompletableDeferred<Unit>()
            val followUpStarted = CompletableDeferred<Unit>()
            val releaseFollowUp = CompletableDeferred<Unit>()
            var calls = 0
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                runner.run { calls++; releaseFirst.await() }
            }
            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                runner.run { calls++; followUpStarted.complete(Unit); releaseFollowUp.await() }
            }
            releaseFirst.complete(Unit)
            followUpStarted.await()
            val third = launch(start = CoroutineStart.UNDISPATCHED) { runner.run { calls++ } }
            releaseFollowUp.complete(Unit)
            listOf(first, second, third).joinAll()
            assertEquals(3, calls)
        }
    }
}
