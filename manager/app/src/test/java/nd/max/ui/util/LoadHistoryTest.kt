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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * تاريخ الحمل — الوعد الذي يحرسه هذا الاختبار: **الطيف لا يبدأ من الصفر بعد إغلاق
 * التطبيق، ولا يعرض عيّنة قديمة على أنها اللحظة**.
 */
class LoadHistoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_700_000_000_000L

    private fun samples(vararg values: Float, gpu: Boolean = true): List<LoadSample> =
        values.mapIndexed { index, value ->
            LoadSample(
                atMs = now - (values.size - 1 - index) * 2_000L,
                cpu = value,
                gpu = if (gpu) value / 2f else null,
            )
        }

    @Test
    fun aSessionSurvivesTheAppBeingClosed() {
        val file = File(folder.root, "history.txt")
        val written = samples(12f, 40f, 77f)
        LoadHistoryStore(file).save(written)

        val restored = LoadHistoryStore(file).load(now)

        assertEquals(listOf(12f, 40f, 77f), restored.map { it.cpu })
        assertEquals(listOf(6f, 20f, 38.5f), restored.map { it.gpu })
        assertEquals(written.map { it.atMs }, restored.map { it.atMs })
    }

    @Test
    fun aMissingGpuReadingIsRestoredAsAbsentNotZero() {
        val file = File(folder.root, "history.txt")
        LoadHistoryStore(file).save(
            listOf(LoadSample(now - 2_000L, 30f, 55f), LoadSample(now, 44f, null))
        )

        val restored = LoadHistoryStore(file).load(now)

        assertEquals(55f, restored[0].gpu!!, 0.001f)
        assertNull(restored[1].gpu)
    }

    /** ما مضى عليه أكثر من نافذة الصلاحية لا يُعرض كأنه «آخر فترة» اليوم. */
    @Test
    fun samplesOlderThanTheWindowAreDropped() {
        val file = File(folder.root, "history.txt")
        file.writeText(
            listOf(
                "${now - LoadHistory.STALE_AFTER_MS - 1}\u001F90.0\u001F-",
                "${now - 4_000L}\u001F21.0\u001F11.0",
            ).joinToString("\n"),
            StandardCharsets.UTF_8,
        )

        assertEquals(listOf(21f), LoadHistoryStore(file).load(now).map { it.cpu })
    }

    /** ساعة الجهاز تحرّكت للخلف: عيّنة «من المستقبل» لا تُصدَّق. */
    @Test
    fun aSampleStampedInTheFutureIsDropped() {
        val file = File(folder.root, "history.txt")
        file.writeText("${now + 60_000L}\u001F80.0\u001F-\n${now}\u001F35.0\u001F-", StandardCharsets.UTF_8)

        assertEquals(listOf(35f), LoadHistoryStore(file).load(now).map { it.cpu })
    }

    @Test
    fun oneBrokenLineDropsItselfAndNothingElse() {
        val file = File(folder.root, "history.txt")
        file.writeText(
            listOf(
                "ليس رقمًا\u001F50\u001F-",
                "${now - 2000L}\u001Fabc\u001F-",
                "${now - 2000L}\u001F50.0\u001F-",
                "",
                "${now}\u001F70.0\u001FNaN",
            ).joinToString("\n"),
            StandardCharsets.UTF_8,
        )

        val restored = LoadHistoryStore(file).load(now)

        assertEquals(listOf(50f, 70f), restored.map { it.cpu })
        assertNull("قيمة NaN ليست قراءة GPU", restored[1].gpu)
    }

    @Test
    fun theHistoryIsCappedAndKeptOldestFirst() {
        val many = (1..LoadHistory.LIMIT + 10).map { index ->
            LoadSample(atMs = now - (LoadHistory.LIMIT + 10 - index) * 2_000L, cpu = index.toFloat(), gpu = null)
        }
        val encoded = LoadHistoryCodec.encode(many)

        val restored = LoadHistoryCodec.decode(encoded, now)

        assertEquals(LoadHistory.LIMIT, restored.size)
        assertTrue("الأقدم أولًا", restored.first().atMs < restored.last().atMs)
        assertEquals(many.takeLast(LoadHistory.LIMIT).map { it.cpu }, restored.map { it.cpu })
    }

    @Test
    fun outOfOrderLinesAreSortedByTime() {
        val file = File(folder.root, "history.txt")
        file.writeText(
            "${now}\u001F70.0\u001F-\n${now - 2_000L}\u001F20.0\u001F-\n${now - 4_000L}\u001F10.0\u001F-",
            StandardCharsets.UTF_8,
        )

        assertEquals(listOf(10f, 20f, 70f), LoadHistoryStore(file).load(now).map { it.cpu })
    }

    @Test
    fun valuesCannotEscapePercent() {
        val file = File(folder.root, "history.txt")
        file.writeText("${now}\u001F140.0\u001F-30.0", StandardCharsets.UTF_8)

        val restored = LoadHistoryStore(file).load(now)

        assertEquals(100f, restored[0].cpu, 0.001f)
        assertEquals(0f, restored[0].gpu!!, 0.001f)
    }

    @Test
    fun anEmptyOrBinaryFileYieldsNoHistory() {
        val empty = File(folder.root, "empty.txt")
        empty.writeText("", StandardCharsets.UTF_8)
        assertEquals(0, LoadHistoryStore(empty).load(now).size)

        val binary = File(folder.root, "binary.txt")
        binary.writeBytes(byteArrayOf(0, 1, 2, 0x7F))
        assertEquals(0, LoadHistoryStore(binary).load(now).size)

        assertEquals(0, LoadHistoryCodec.decode(null, now).size)
    }
}
