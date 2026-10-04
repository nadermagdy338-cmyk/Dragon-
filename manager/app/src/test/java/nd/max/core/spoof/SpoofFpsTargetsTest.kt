/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofFpsTargetsTest {
    private val game = "com.mobile.legends"

    @Test fun anUnknownGameGetsNoRowsInsteadOfAGuessedSet() {
        assertTrue(spoofFpsTargets("com.example.other", SpoofReadiness.NEEDS_ADAPTER, 144).isEmpty())
        assertTrue(spoofFpsTargets("com.game:aid", SpoofReadiness.NEEDS_ADAPTER, 144).isEmpty())
    }
    @Test fun noRowEverReadsAsOpenedOrAvailable() {
        for (readiness in SpoofReadiness.values()) {
            for (hz in listOf(null, 60, 144)) {
                val rows = spoofFpsTargets(game, readiness, hz)
                assertEquals(SPOOF_FPS_FRAMES, rows.map { it.frames })
                assertFalse("a success state exists", rows.any { it.state.name.contains("ACTIVE") || it.state.name.contains("READY") })
            }
        }
    }
    @Test fun aDetectedEngineStillCannotClaimARate() {
        val rows = spoofFpsTargets(game, SpoofReadiness.NEEDS_ADAPTER, 144)
        assertTrue(rows.all { it.state == SpoofTargetState.CONTRACT_UNVERIFIED })
        assertTrue(rows.none { it.state == SpoofTargetState.NO_ENGINE })
    }
    @Test fun withoutAnEngineEveryRowSaysSoRegardlessOfTheDisplay() {
        for (readiness in listOf(SpoofReadiness.NO_ENGINE, SpoofReadiness.ENGINE_UNAVAILABLE, SpoofReadiness.SCAN_UNKNOWN)) {
            assertTrue(spoofFpsTargets(game, readiness, 144).all { it.state == SpoofTargetState.NO_ENGINE })
        }
    }
    @Test fun anUnmeasuredDisplayIsNotTreatedAsReachableOrUnreachable() {
        assertTrue(spoofFpsTargets(game, SpoofReadiness.NEEDS_ADAPTER, null)
            .all { it.state == SpoofTargetState.DEVICE_REFRESH_UNKNOWN })
    }
    @Test fun aDeviceLimitIsVisibleInsteadOfBeingHiddenByTheUnverifiedContract() {
        val rows = spoofFpsTargets(game, SpoofReadiness.NEEDS_ADAPTER, 120)
        assertEquals(SpoofTargetState.CONTRACT_UNVERIFIED, rows.single { it.frames == 60 }.state)
        assertEquals(SpoofTargetState.ABOVE_DEVICE_REFRESH, rows.single { it.frames == 144 }.state)
    }
    @Test fun everyRowCarriesItsSourceAndNothingHereIsMeasuredOnADevice() {
        for (row in spoofFpsTargets(game, SpoofReadiness.NEEDS_ADAPTER, 144)) {
            assertTrue(row.source.isNotBlank())
            assertTrue(row.source.contains("not measured here"))
            assertEquals(SpoofTargetEvidence.DOCUMENTED_REFERENCE, row.evidence)
        }
    }
    @Test fun theTwoDocumentedGamesShareOneMeasuredRateList() {
        assertEquals(listOf(60, 90, 120, 144), SPOOF_FPS_FRAMES)
        assertEquals(SPOOF_FPS_FRAMES, spoofFpsTargets("com.netease.yysls", SpoofReadiness.NO_ENGINE, 144).map { it.frames })
    }
}
