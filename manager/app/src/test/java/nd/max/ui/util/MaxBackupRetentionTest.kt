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

import nd.max.ui.util.MaxBackupRetention.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سياسة الاحتفاظ — القاعدة الوحيدة في Max Backup التي تحذف بيانات المستخدم بلا سؤال.
 *
 * وهي لهذا في ملف خالص: تُقاس هنا في JVM عادي، بلا جهاز وبلا Android. اختبارُ قاعدةِ حذف
 * بأن تقرأ شيفرتها ليس اختبارًا، بل إعادة قولها.
 */
class MaxBackupRetentionTest {

    private fun copy(at: Long, kept: Boolean = false) = Copy("folder-$at", at, kept)

    /** الأحدث يُبقى والباقي يُقلَّم، والحساب بالعدد لا بالنطاق الزمني. */
    @Test
    fun theOldestCopiesPastTheQuotaAreTheOnesRemoved() {
        val copies = listOf(copy(1_000), copy(5_000), copy(3_000), copy(2_000), copy(4_000))
        val removed = MaxBackupRetention.toRemove(copies, keep = 3).map { it.createdAtMs }
        assertEquals(listOf(2_000L, 1_000L), removed)
    }

    /**
     * الحدّ الأدنى الإلزامي: `keep` أقل من ١ تُرفع إلى ١.
     * ولو لم تُرفع، لحذف التقليمُ **آخر نسخة** — أي فقدان بيانات مؤجَّل داخل أداة نسخ احتياطي.
     */
    @Test
    fun theQuotaCanNeverReachZero() {
        val copies = listOf(copy(3_000), copy(2_000), copy(1_000))
        assertEquals(listOf(2_000L, 1_000L), MaxBackupRetention.toRemove(copies, 0).map { it.createdAtMs })
        assertEquals(listOf(2_000L, 1_000L), MaxBackupRetention.toRemove(copies, -5).map { it.createdAtMs })
    }

    /**
     * النسخة المحفوظة لا تُقلَّم ولو كانت الأقدم — وهذه وظيفتها الوحيدة، فاختبارها هنا
     * اختبار للحاجة التي وُجدت من أجلها لا لسلوك جانبي.
     */
    @Test
    fun aKeptCopySurvivesEvenWhenItIsTheOldest() {
        val copies = listOf(copy(1_000, kept = true), copy(5_000), copy(4_000), copy(3_000))
        val removed = MaxBackupRetention.toRemove(copies, keep = 1).map { it.createdAtMs }
        assertEquals(listOf(4_000L, 3_000L), removed)
    }

    /**
     * والنسخة المحفوظة **لا تستهلك رصيد الاحتفاظ**: من حفظ نسخةً لا يفقد نسخته اليومية
     * بسببها. لو حُسبت ضمن الرصيد لصار الحفظ سببًا في حذف غيره — وهو عكس المقصود.
     */
    @Test
    fun aKeptCopyDoesNotConsumeTheQuota() {
        val copies = listOf(copy(1_000, kept = true), copy(4_000), copy(3_000), copy(2_000))
        val removed = MaxBackupRetention.toRemove(copies, keep = 2).map { it.createdAtMs }
        assertEquals(listOf(2_000L), removed)
    }

    /** وكلّها محفوظة ⇒ لا شيء يُحذف. */
    @Test
    fun nothingIsRemovedWhenEveryCopyIsKept() {
        val copies = listOf(copy(3_000, kept = true), copy(2_000, kept = true), copy(1_000, kept = true))
        assertTrue(MaxBackupRetention.toRemove(copies, keep = 1).isEmpty())
    }

    /** ولا تطبيق له نسخ ⇒ لا شيء. (المسار يُنادى بعد كل نسخة، فيجب أن يتحمّل الفراغ.) */
    @Test
    fun anEmptyListRemovesNothing() {
        assertTrue(MaxBackupRetention.toRemove(emptyList(), keep = 3).isEmpty())
    }

    /**
     * تعادل الطابع الزمني محسوم بترتيب **مستقرّ** لا بالحظّ.
     *
     * والفرز في Kotlin مستقرّ، فالمتساويان يحفظان ترتيب الإدخال، والقصّ من الآخر يحذف
     * التابع لا المتقدّم. تُثبَّت هنا لأن «مستقرّ» خاصية تُورَث بالخطأ لو غُيّر الفرز
     * يومًا إلى `sortedWith(compareByDescending {})` بمنطق مختلف.
     */
    @Test
    fun tiesAreBrokenByInputOrderNotByChance() {
        val first = Copy("first-into-the-list", 2_000, kept = false)
        val second = Copy("second-into-the-list", 2_000, kept = false)
        val removed = MaxBackupRetention.toRemove(listOf(first, second), keep = 1)
        assertEquals(listOf("second-into-the-list"), removed.map { it.folder })
    }
}
