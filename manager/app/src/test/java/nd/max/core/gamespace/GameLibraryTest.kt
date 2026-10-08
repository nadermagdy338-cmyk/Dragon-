/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLibraryTest {
    @Test fun includesDetectedAndManualGamesOnly() {
        val apps = listOf(GameApp("com.a", "Z", true), GameApp("com.b", "A", false), GameApp("com.c", "C", false))
        assertEquals(listOf("com.b", "com.a"), gameLibrary(apps, setOf("com.b", "missing")).map { it.packageName })
    }
    @Test fun manualRemovalDoesNotEraseAutoDetection() {
        assertEquals(1, gameLibrary(listOf(GameApp("com.a", "A", true)), emptySet()).size)
    }
    @Test fun explicitRemovalHidesAnAutomaticallyDetectedGame() {
        val app = GameApp("com.a", "A", true)
        val removed = GameLibraryMembership(setOf("com.a"), emptySet()).change("com.a", false)
        assertTrue(gameLibrary(listOf(app), removed.manual, removed.excluded).isEmpty())
        assertTrue(removed.manual.isEmpty())
        assertEquals(setOf("com.a"), removed.excluded)
    }
    @Test fun readdingExcludedGameClearsExclusionAndPreservesOtherPackages() {
        val before = GameLibraryMembership(setOf("com.b"), setOf("com.a", "com.c"))
        val added = before.change("com.a", true)
        assertEquals(setOf("com.a", "com.b"), added.manual)
        assertEquals(setOf("com.c"), added.excluded)
        assertEquals(added, added.change("com.a", true))
    }
    @Test fun exclusionWinsEvenOverAnOldManualEntry() {
        assertTrue(gameLibrary(listOf(GameApp("com.a", "A", false)), setOf("com.a"), setOf("com.a")).isEmpty())
    }
    @Test fun invisibleMembershipSurvivesWhileInvalidPackagesAreRejected() {
        assertEquals(setOf("com.missing"), validGamePackages(setOf("com.missing")))
        for (pkg in listOf("", "com.game;id", "com.game:clone", "com..game")) {
            assertTrue(runCatching { GameLibraryMembership(emptySet(), emptySet()).change(pkg, true) }.isFailure)
            assertTrue(runCatching { validGamePackages(setOf(pkg)) }.isFailure)
        }
    }
    @Test fun metadataCountsAreBoundedAndInvalidLauncherRowsAreNotGames() {
        assertTrue(runCatching { validGamePackages((0..GameProfileDocument.MAX_APPS).map { "com.p$it" }.toSet()) }.isFailure)
        assertTrue(gameLibrary(listOf(GameApp("bad;pkg", "A", true)), emptySet()).isEmpty())
    }
    @Test fun duplicateActivitiesAreOneGame() {
        val app = GameApp("com.a", "A", true)
        assertEquals(listOf(app), gameLibrary(listOf(app, app), emptySet()))
    }
    // جدول الامتدادات انتقل إلى `nd.max.core.emuhub.RomSystems` (مصدر حقيقة واحد)،
    // وتغطيته هناك في `RomSystemsTest` — أوسع: ١٧ نظامًا بدل ٦، وبنفس قاعدتَي الالتباس والمجهول.
    @Test fun failedReadIsNotZero() {
        assertEquals(VisibilityChange.INCOMPARABLE, compareVisibility(10, null))
        assertEquals(VisibilityChange.NO_BASELINE, compareVisibility(null, 10))
        assertEquals(VisibilityChange.INCOMPARABLE, compareVisibility(-1, 10))
    }
    @Test fun comparisonReportsDirectionNotCausality() {
        assertEquals(VisibilityChange.LOWER, compareVisibility(10, 8))
        assertEquals(VisibilityChange.HIGHER, compareVisibility(10, 12))
        assertEquals(VisibilityChange.SAME, compareVisibility(10, 10))
    }
}
