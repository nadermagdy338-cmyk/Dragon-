/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes evaluation and shares the consumed generation across every waiter.
 * A burst during a cycle produces one follow-up, not one replay per caller.
 */
internal class CoalescingCycleRunner {
    private val mutex = Mutex()
    private val requested = AtomicLong(0L)
    private var handled = 0L

    suspend fun run(cycle: suspend () -> Unit) {
        val ticket = requested.incrementAndGet()
        mutex.withLock {
            if (ticket <= handled) return
            val batch = requested.get()
            try {
                cycle()
            } finally {
                // A failed batch must not become a retry storm among its waiters.
                handled = batch
            }
        }
    }
}
