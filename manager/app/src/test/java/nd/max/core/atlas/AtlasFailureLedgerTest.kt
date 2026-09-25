/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P12` tests for negative evidence.
 *
 * Every one of these is about *not* turning a failure into a belief about the device: the ledger may
 * refuse to ask again, and it may never conclude anything from having refused.
 */
class AtlasFailureLedgerTest {

    private var now: Long = 0L
    private var boot: Long = 0L
    private var privilege: Long = 0L

    private val path = "/sys/class/power_supply/battery/charge_full"

    // ---- causes that carry no information -------------------------------------------------------------

    @Test
    fun `a cancelled job is never remembered`() {
        val ledger = ledger()

        ledger.record(path, AtlasFailure.CANCELLED)

        assertEquals("a cancellation is evidence about the job, not the device", 0, ledger.trackedPaths())
        assertNull(ledger.suppressionFor(path))
    }

    @Test
    fun `a stale flag is not a failed read and is not remembered`() {
        val ledger = ledger()

        ledger.record(path, AtlasFailure.STALE)

        assertEquals(0, ledger.trackedPaths())
        assertNull(ledger.suppressionFor(path))
    }

    @Test
    fun `a later cancellation erases an earlier real failure`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.TIMED_OUT)

        ledger.record(path, AtlasFailure.CANCELLED)

        assertEquals(0, ledger.trackedPaths())
        assertEquals(0, ledger.avoidedAttempts())
    }

    // ---- lifetime per cause --------------------------------------------------------------------------

    @Test
    fun `an absent path is suppressed for a minute and then allowed again`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.ABSENT)

        now = AtlasFailureRetryPolicy.NEGATIVE_TTL_MS - 1
        assertTrue(ledger.suppressionFor(path) != null)
        now = AtlasFailureRetryPolicy.NEGATIVE_TTL_MS
        assertNull("absence is retried rather than believed forever", ledger.suppressionFor(path))
    }

    @Test
    fun `an unrecognized failure is remembered briefly`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.UNKNOWN_CAUSE)

        now = AtlasFailureRetryPolicy.UNKNOWN_RETRY_MS - 1
        assertEquals(AtlasFailure.UNKNOWN_CAUSE, ledger.suppressionFor(path)?.cause)
        now = AtlasFailureRetryPolicy.UNKNOWN_RETRY_MS
        assertNull(ledger.suppressionFor(path))
    }

    @Test
    fun `a malformed value is remembered for five minutes`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.MALFORMED)

        now = AtlasFailureRetryPolicy.MALFORMED_RETRY_MS - 1
        assertEquals(AtlasFailure.MALFORMED, ledger.suppressionFor(path)?.cause)
        now = AtlasFailureRetryPolicy.MALFORMED_RETRY_MS
        assertNull(ledger.suppressionFor(path))
    }

    // ---- privilege-shaped failures -------------------------------------------------------------------

    @Test
    fun `a denied path is suppressed and a privilege change lifts it immediately`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.PERMISSION_DENIED)

        assertTrue(ledger.suppressionFor(path) != null)
        privilege = 1L
        assertNull("a new privilege generation can make the same read succeed", ledger.suppressionFor(path))
        assertEquals("the dropped record is gone, not merely ignored", 0, ledger.trackedPaths())
    }

    @Test
    fun `a denied path lapses after the ceiling even without a privilege change`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.PERMISSION_DENIED)

        now = AtlasFailureRetryPolicy.DENIAL_RETRY_MS - 1
        assertTrue(ledger.suppressionFor(path) != null)
        now = AtlasFailureRetryPolicy.DENIAL_RETRY_MS
        assertNull("sleeping cannot change a policy, so the ceiling is not unbounded", ledger.suppressionFor(path))
    }

    @Test
    fun `a backend that is not wired is treated like a denial rather than retried every second`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.BACKEND_UNAVAILABLE)

        now = AtlasFailureRetryPolicy.DENIAL_RETRY_MS / 2
        assertTrue(ledger.suppressionFor(path) != null)
    }

    // ---- transient failures back off ------------------------------------------------------------------

    @Test
    fun `a timeout backs off exponentially and is capped`() {
        val ledger = ledger()

        ledger.record(path, AtlasFailure.TIMED_OUT)
        now = 999L
        assertTrue(ledger.suppressionFor(path) != null)
        now = 1_000L
        assertNull(ledger.suppressionFor(path))

        ledger.record(path, AtlasFailure.TIMED_OUT)
        now = 2_999L
        assertTrue(ledger.suppressionFor(path) != null)
        now = 3_000L
        assertNull(ledger.suppressionFor(path))

        repeat(9) { ledger.record(path, AtlasFailure.TIMED_OUT) }
        now = 3_000L + AtlasFailureRetryPolicy.TRANSIENT_CAP_MS
        assertNull("the doubling stops at the cap instead of growing forever", ledger.suppressionFor(path))
        assertEquals(
            AtlasFailureRetryPolicy.TRANSIENT_CAP_MS,
            AtlasFailureRetryPolicy.retryDelayMs(AtlasFailure.TIMED_OUT, 32),
        )
    }

    @Test
    fun `a different cause for the same path restarts the count`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.TIMED_OUT)
        ledger.record(path, AtlasFailure.ABSENT)

        assertEquals(AtlasFailure.ABSENT, ledger.suppressionFor(path)?.cause)
        assertEquals(
            "the absent schedule is used, not the timeout one",
            AtlasFailureRetryPolicy.NEGATIVE_TTL_MS,
            AtlasFailureRetryPolicy.retryDelayMs(AtlasFailure.ABSENT, 1),
        )
    }

    @Test
    fun `a success erases the record so no conclusion outlives the evidence`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.PERMISSION_DENIED)

        ledger.noteSuccess(path)

        assertNull(ledger.suppressionFor(path))
        assertEquals(0, ledger.trackedPaths())
    }

    // ---- invalidation by generation and by clock ------------------------------------------------------

    @Test
    fun `another boot drops every record`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.ABSENT)

        boot = 1L

        assertNull("an elapsed time from another boot cannot age this record", ledger.suppressionFor(path))
        assertEquals(0, ledger.trackedPaths())
    }

    @Test
    fun `a clock that moved backwards stops trusting a suppression`() {
        val ledger = ledger()
        now = 10_000L
        ledger.record(path, AtlasFailure.TIMED_OUT)

        now = 9_000L

        assertNull("an unmeasurable age is not a young age", ledger.suppressionFor(path))
    }

    // ---- accounting and shape ------------------------------------------------------------------------

    @Test
    fun `the ledger counts the attempts it saved`() {
        val ledger = ledger()
        ledger.record(path, AtlasFailure.PERMISSION_DENIED)

        ledger.suppressionFor(path)
        ledger.suppressionFor(path)

        assertEquals(2, ledger.avoidedAttempts())
        now = AtlasFailureRetryPolicy.DENIAL_RETRY_MS
        assertNull(ledger.suppressionFor(path))
        assertEquals("a refusal that did not happen is not counted", 2, ledger.avoidedAttempts())
    }

    @Test
    fun `every suppressing cause explains itself and bounds its own lapse`() {
        AtlasFailure.entries.filter(AtlasFailureRetryPolicy::suppresses).forEach { cause ->
            val ledger = ledger()
            ledger.record(path, cause)
            val suppression = ledger.suppressionFor(path)
                ?: error("$cause must produce a suppression")

            assertTrue("$cause must explain itself", suppression.reason.isNotBlank())
            assertEquals(cause, suppression.cause)
            assertEquals(
                "the lapse is the policy's own delay, so a report can state it",
                AtlasFailureRetryPolicy.retryDelayMs(cause, 1),
                suppression.untilElapsedMs,
            )
        }
    }

    @Test
    fun `a cause that does not suppress has no schedule`() {
        listOf(AtlasFailure.NONE, AtlasFailure.CANCELLED, AtlasFailure.STALE).forEach { cause ->
            assertFalse(AtlasFailureRetryPolicy.suppresses(cause))
            assertTrue(
                runCatching { AtlasFailureRetryPolicy.retryDelayMs(cause, 1) }.exceptionOrNull() is IllegalArgumentException,
            )
        }
        assertTrue(
            runCatching { AtlasFailureRetryPolicy.retryDelayMs(AtlasFailure.ABSENT, 0) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `a key that is not a canonical absolute path is rejected`() {
        val ledger = ledger()

        listOf("relative/path", "/sys/../etc/passwd", "", "/sys/class/thermal/").forEach { bad ->
            assertTrue(
                "$bad must be rejected",
                runCatching { ledger.record(bad, AtlasFailure.ABSENT) }.exceptionOrNull() is IllegalArgumentException,
            )
            assertTrue(
                "$bad must be rejected as a success key too",
                runCatching { ledger.noteSuccess(bad) }.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }

    private fun ledger(): AtlasFailureLedger = AtlasFailureLedger(
        bootGeneration = { boot },
        privilegeGeneration = { privilege },
        clockMs = { now },
    )
}
