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
 * المهام الخلفية — يُقاس فيها أهمّ ما فيها: أن النسبة **لا تُخترع**.
 * وحين لا تُقاس الكمّية تُعاد `null` وتُعلَن «غير معروفة»، لا `0%` ولا `100%`.
 */
class FileTaskModelTest {

    private fun task(
        id: Long = 1L,
        kind: FileTaskKind = FileTaskKind.Copy,
        total: Long? = 1_000L,
        done: Long? = 0L,
        state: FileTaskState = FileTaskState.Running,
        startedAt: Long = 100L,
        endedAt: Long? = null,
    ) = FileTask(
        id = id,
        kind = kind,
        sources = listOf("/a/big.bin"),
        destination = "/b",
        totalBytes = total,
        doneBytes = done,
        state = state,
        startedAtMs = startedAt,
        endedAtMs = endedAt,
    )

    @Test
    fun aMeasuredProgressBecomesAPercentage() {
        assertEquals(25, task(total = 1_000, done = 250).percent)
        assertEquals(100, task(total = 1_000, done = 1_000).percent)
        assertEquals(0, task(total = 1_000, done = 0).percent)
    }

    /** لا كمّية ⇒ لا نسبة: العلم الصادق بدل شريط يكذب (ADR-07). */
    @Test
    fun anUnmeasuredProgressHasNoPercentage() {
        assertNull(task(total = null, done = 100).percent)
        assertNull(task(total = 1_000, done = null).percent)
        // مجلد فارغ أو قياس صفري: القسمة على صفر ليست 100%.
        assertNull(task(total = 0, done = 0).percent)
    }

    /** الكتابة قد تتجاوز المصدر (احتياطي النظام): النسبة تُسقف ولا تعرض 130%. */
    @Test
    fun anOvershootingProgressIsCapped() {
        assertEquals(100, task(total = 100, done = 130).percent)
    }

    @Test
    fun theQueueNumbersItsTasksWithoutReusingIds() {
        assertEquals(1L, FileTaskQueue.nextId(emptyList()))
        assertEquals(3L, FileTaskQueue.nextId(listOf(task(id = 1), task(id = 2))))
    }

    @Test
    fun progressAndFinishUpdateOnlyTheTargetedTask() {
        val tasks = listOf(task(id = 1), task(id = 2))
        val progressed = FileTaskQueue.progress(tasks, id = 2, doneBytes = 500)
        assertEquals(0L, progressed.first { it.id == 1L }.doneBytes)
        assertEquals(500L, progressed.first { it.id == 2L }.doneBytes)

        val finished = FileTaskQueue.finish(progressed, id = 2, state = FileTaskState.Done, atMs = 250)
        assertFalse(finished.first { it.id == 2L }.isRunning)
        assertEquals(150L, finished.first { it.id == 2L }.elapsedMs)
        assertTrue(finished.first { it.id == 1L }.isRunning)
    }

    @Test
    fun cancellingMarksTheTaskAndKeepsAReason() {
        val cancelled = FileTaskQueue.cancel(listOf(task(id = 7)), id = 7, atMs = 300).single()
        assertEquals(FileTaskState.Cancelled, cancelled.state)
        assertFalse(cancelled.isRunning)
        assertEquals("cancelled", cancelled.reasonKey)
    }

    /** الشريط يعرض الجارية أولًا ثم آخر ما انتهى — وبسقف معلن. */
    @Test
    fun theStripShowsRunningTasksFirstAndStopsAtTheCap() {
        val finished = (1L..4L).map {
            task(id = it, state = FileTaskState.Done, endedAt = 1_000L + it)
        }
        val running = task(id = 9, state = FileTaskState.Running)
        val visible = FileTaskQueue.visible(finished + running)

        assertEquals(FileTaskQueue.MAX_VISIBLE, visible.size)
        assertEquals(9L, visible.first().id)
    }

    @Test
    fun clearingFinishedTasksKeepsTheRunningOnes() {
        val tasks = listOf(
            task(id = 1, state = FileTaskState.Done, endedAt = 10),
            task(id = 2, state = FileTaskState.Failed, endedAt = 20),
            task(id = 3),
        )
        assertEquals(listOf(3L), FileTaskQueue.clearFinished(tasks).map { it.id })
        assertEquals(listOf(3L), FileTaskQueue.running(tasks).map { it.id })
    }

    /** مهمة لم يُقرأ لها كمّية تبقى معروضة بحالة «جارية» وبلا نسبة — لا تُخفى. */
    @Test
    fun aTaskWithoutMeasuredTotalsStaysVisibleAndSaysNothingItDoesNotKnow() {
        val unknown = task(total = null, done = null)
        assertTrue(unknown.isRunning)
        assertNull(unknown.percent)
        assertEquals(1, unknown.count)
    }
}
