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
 * تعارض الأسماء — يُقاس لأن الخطأ فيه **يفقد بيانات**: استبدال لم يُطلب، أو تخطٍّ
 * صامت يُظهر أن النسخ تمّ ولم يتمّ.
 */
class FileConflictModelTest {

    private val sources = listOf("a.txt", "b.txt", "c.txt")

    @Test
    fun theScanFindsOnlyTheNamesThatAlreadyExist() {
        val scan = FileConflictRules.scan(sources, setOf("b.txt", "z.txt"))
        assertEquals(listOf("b.txt"), scan.collisions)
        assertTrue(scan.hasCollisions)
        assertFalse(FileConflictRules.scan(sources, emptySet()).hasCollisions)
    }

    /** `Ask` ليست قرارًا: لا خطة تُنفَّذ قبل أن يجيب المستخدم. */
    @Test
    fun askingProducesNoPlan() {
        assertNull(FileConflictRules.plan(sources, setOf("a.txt"), ConflictChoice.Ask))
    }

    @Test
    fun cancellingProducesACancelPlanOnly() {
        val plan = FileConflictRules.plan(sources, setOf("a.txt"), ConflictChoice.Cancel)!!
        assertTrue(plan.cancel)
        assertTrue(plan.proceed.isEmpty())
        assertTrue(plan.rename.isEmpty())
    }

    @Test
    fun overwritingProceedsWithEverything() {
        val plan = FileConflictRules.plan(sources, setOf("a.txt", "c.txt"), ConflictChoice.OverwriteAll)!!
        assertEquals(sources, plan.proceed)
        assertTrue(plan.skip.isEmpty())
    }

    @Test
    fun skippingProceedsWithoutTheCollisions() {
        val plan = FileConflictRules.plan(sources, setOf("a.txt", "c.txt"), ConflictChoice.SkipAll)!!
        assertEquals(listOf("b.txt"), plan.proceed)
        assertEquals(listOf("a.txt", "c.txt"), plan.skip)
    }

    /**
     * إعادة التسمية تتجاوز الأسماء الموجودة فعلًا، وبأسلوب [FileOpGuard.uniqueName]
     * نفسه: اللاحقةتُضاف إلى الاسم كاملًا (`a.txt (1)`) — فلا يختلف اسم واعد عن اسم مكتوب.
     */
    @Test
    fun renamingSkipsPastTheNamesAlreadyTaken() {
        val plan = FileConflictRules.plan(
            listOf("a.txt"),
            setOf("a.txt", "a.txt (1)"),
            ConflictChoice.Rename,
        )!!
        assertEquals("a.txt (2)", plan.rename["a.txt"])
        assertTrue(plan.proceed.isEmpty())
    }

    /**
     * الاسم المولَّد يدخل في حساب التالي: وإلا صدر اسمان جديدان **متماثلان** لأن
     * كلًّا منهما قيس على قائمة الوجهة الأصلية وحدها.
     */
    @Test
    fun renamingNeverProducesTwoIdenticalFreshNames() {
        val plan = FileConflictRules.plan(
            listOf("x", "x (1)"),
            setOf("x", "x (1)"),
            ConflictChoice.Rename,
        )!!
        assertEquals("x (2)", plan.rename["x"])
        assertEquals(plan.rename.size, plan.rename.values.toSet().size)
    }

    /** اسم غير متعارض لا يُعاد تسميته: الخطة تخصّ ما اصطدم وحده. */
    @Test
    fun renamingLeavesSafeNamesAlone() {
        val plan = FileConflictRules.plan(sources, setOf("b.txt"), ConflictChoice.Rename)!!
        assertEquals(listOf("a.txt", "c.txt"), plan.proceed)
        assertEquals(setOf("b.txt"), plan.rename.keys)
    }

    @Test
    fun singleChoicesKeepAskingAndAllChoicesDoNot() {
        assertTrue(FileConflictRules.repeatsQuestion(ConflictChoice.Ask))
        assertTrue(FileConflictRules.repeatsQuestion(ConflictChoice.Overwrite))
        assertTrue(FileConflictRules.repeatsQuestion(ConflictChoice.Skip))
        assertFalse(FileConflictRules.repeatsQuestion(ConflictChoice.OverwriteAll))
        assertFalse(FileConflictRules.repeatsQuestion(ConflictChoice.SkipAll))
        assertFalse(FileConflictRules.repeatsQuestion(ConflictChoice.Rename))
    }

    @Test
    fun aSingleChoiceCanBePromotedToApplyToAll() {
        assertEquals(ConflictChoice.OverwriteAll, FileConflictRules.forAll(ConflictChoice.Overwrite))
        assertEquals(ConflictChoice.SkipAll, FileConflictRules.forAll(ConflictChoice.Skip))
        assertEquals(ConflictChoice.Rename, FileConflictRules.forAll(ConflictChoice.Rename))
    }
}
