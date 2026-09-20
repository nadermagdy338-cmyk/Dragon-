/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * البحث العميق — يُقاس فيه ما يُخشى منه: أن تُعرض نتيجة **مقطوعة** كأنها كاملة.
 * فكل حدٍّ يترك علمًا في [DeepSearchOutcome]، و[DeepSearchOutcome.complete] لا تكون
 * `true` إلا حين لم ينتهِ حدٌّ ولم يسقط مجلد.
 */
class FileSearchPlanTest {

    @Test
    fun aBlankQueryIsNotASearch() {
        assertNull(FileSearchPlan.of("/sdcard", "   "))
        assertNull(FileSearchPlan.of("/sdcard", ""))
    }

    @Test
    fun thePlanNormalizesRootAndQuery() {
        val plan = FileSearchPlan.of("/sdcard//Download/", " report ")!!
        assertEquals("/sdcard/Download", plan.root)
        assertEquals("report", plan.query)
    }

    @Test
    fun theLimitsAreClampedToSomethingUsable() {
        val tiny = FileSearchPlan.clamp(SearchLimits(maxDepth = 0, maxResults = 1, timeoutMs = 5L))
        assertEquals(SearchLimits.MIN_DEPTH, tiny.maxDepth)
        assertEquals(SearchLimits.MIN_RESULTS, tiny.maxResults)
        assertEquals(SearchLimits.MIN_TIMEOUT_MS, tiny.timeoutMs)

        val huge = FileSearchPlan.clamp(SearchLimits(maxDepth = 99, maxResults = 999_999, timeoutMs = 9_999_999L))
        assertEquals(SearchLimits.MAX_DEPTH, huge.maxDepth)
        assertEquals(SearchLimits.MAX_RESULTS, huge.maxResults)
        assertEquals(SearchLimits.MAX_TIMEOUT_MS, huge.timeoutMs)
    }

    // ────────────────────────────────────────────────────────────────────────
    // العمق
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun depthCountsStepsFromTheRootNotFromSlash() {
        assertEquals(0, FileSearchPlan.depthOf("/sdcard", "/sdcard"))
        assertEquals(1, FileSearchPlan.depthOf("/sdcard", "/sdcard/Download"))
        assertEquals(3, FileSearchPlan.depthOf("/sdcard", "/sdcard/a/b/c"))
        // الجذر نفسه استثناء المسار: `/system` عمقه ١ تحت `/`.
        assertEquals(1, FileSearchPlan.depthOf("/", "/system"))
    }

    @Test
    fun aPathOutsideTheRootIsNeverWithinDepth() {
        assertEquals(Int.MAX_VALUE, FileSearchPlan.depthOf("/sdcard", "/data"))
        assertFalse(FileSearchPlan.withinDepth("/sdcard", "/data", SearchLimits()))
    }

    @Test
    fun withinDepthRespectsTheDeclaredLimit() {
        val limits = SearchLimits(maxDepth = 2)
        assertTrue(FileSearchPlan.withinDepth("/sdcard", "/sdcard/a/b", limits))
        assertFalse(FileSearchPlan.withinDepth("/sdcard", "/sdcard/a/b/c", limits))
    }

    // ────────────────────────────────────────────────────────────────────────
    // المطابقة والتوقّف
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun matchingIsByNameAndIgnoresCase() {
        val entry = FileEntry(name = "Report.TXT", path = "/a/Report.TXT", kind = FileKind.RegularFile)
        assertTrue(FileSearchPlan.matches(entry, "report"))
        assertFalse(FileSearchPlan.matches(entry, "notes"))
    }

    @Test
    fun theSearchStopsOnEitherLimit() {
        val limits = SearchLimits(maxResults = 10, timeoutMs = 1_000L)
        assertFalse(FileSearchPlan.shouldStop(hits = 9, elapsedMs = 999L, limits = limits))
        assertTrue(FileSearchPlan.shouldStop(hits = 10, elapsedMs = 0L, limits = limits))
        assertTrue(FileSearchPlan.shouldStop(hits = 0, elapsedMs = 1_000L, limits = limits))
    }

    // ────────────────────────────────────────────────────────────────────────
    // إعلان النقص
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun anEmptyOutcomeIsCompleteBecauseNothingWasCutShort() {
        assertTrue(DeepSearchOutcome().complete)
    }

    /** كل سبب نقص **يكسر** الكمال — وهذا هو الشرط الذي يجعل الجملة صادقة. */
    @Test
    fun everyTruncationReasonBreaksCompleteness() {
        assertFalse(DeepSearchOutcome(hitLimitReached = true).complete)
        assertFalse(DeepSearchOutcome(depthLimitReached = true).complete)
        assertFalse(DeepSearchOutcome(timedOut = true).complete)
        assertFalse(DeepSearchOutcome(unreadableFolders = 1).complete)
    }

    @Test
    fun aFullOutcomeCarriesItsCounts() {
        val outcome = DeepSearchOutcome(
            hits = listOf(FileEntry("a", "/a", FileKind.RegularFile)),
            scannedFolders = 12,
            unreadableFolders = 0,
        )
        assertEquals(1, outcome.hits.size)
        assertEquals(12, outcome.scannedFolders)
        assertTrue(outcome.complete)
    }
}
