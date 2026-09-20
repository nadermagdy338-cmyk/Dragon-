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
 * هندسة الطيف — الوعد الذي يحرسه هذا الاختبار: **الإطار كامل العدد دائمًا**، والعيّنة
 * الأحدث في الطرف لا في البداية، وGPU زوج لا يملأ نصف الطيف حين لا قراءة له أصلًا.
 */
class SpectrumModelTest {

    private fun samples(vararg cpu: Float, gpu: List<Float?>? = null): List<LoadSample> =
        cpu.mapIndexed { index, value ->
            LoadSample(atMs = index * 2_000L, cpu = value, gpu = gpu?.get(index))
        }

    @Test
    fun anEmptyWindowIsStillAFullFrameOfEmptySlots() {
        val frame = Spectrum.frame(emptyList())

        assertEquals(Spectrum.SLOTS, frame.bars.size)
        assertTrue("لا شريط واحد بعد", frame.bars.all { it == null })
        assertFalse(frame.paired)
    }

    /** العطب الذي أُصلح: ثلاث عيّنات كانت تُرسم ثلاثة أشرطة عريضة تشغل العرض كله. */
    @Test
    fun theFirstSamplesFillFromTheRightAndKeepTheFrameWidth() {
        val frame = Spectrum.frame(samples(20f, 40f, 60f))

        assertEquals(Spectrum.SLOTS, frame.bars.size)
        assertEquals(listOf(20f, 40f, 60f), frame.bars.takeLast(3).map { it!!.cpu })
        assertTrue(frame.bars.dropLast(3).all { it == null })
    }

    @Test
    fun theWindowSlidesOnceItIsFull() {
        val long = (1..40).map { LoadSample(it * 2_000L, it.toFloat(), null) }

        val frame = Spectrum.frame(long)

        assertEquals(Spectrum.SLOTS, frame.bars.size)
        assertEquals(40f, frame.bars.last()!!.cpu, 0.001f)
        assertEquals(15f, frame.bars.first()!!.cpu, 0.001f)
    }

    @Test
    fun bothLoadsArePairedWhenTheGpuIsReadable() {
        val frame = Spectrum.frame(samples(30f, 50f, 60f, gpu = listOf(10f, null, 45f)))

        assertTrue(frame.paired)
        assertEquals(10f, frame.bars[frame.bars.size - 3]!!.gpu!!, 0.001f)
        assertNull("عيّنة بلا قراءة GPU تبقى فارغة، لا صفرًا", frame.bars[frame.bars.size - 2]!!.gpu)
        assertEquals(45f, frame.bars.last()!!.gpu!!, 0.001f)
    }

    /** هاتف بلا عقد GPU: أعمدة CPU بعرض الفتحة كامل، لا نصف طيف فارغ أبدًا. */
    @Test
    fun withoutAnyGpuReadingTheChartIsNotPaired() {
        val frame = Spectrum.frame(samples(30f, 50f))

        assertFalse(frame.paired)
        assertTrue(frame.bars.filterNotNull().all { it.gpu == null })
    }

    @Test
    fun valuesOutsideTheScaleAreClampedAndNonFiniteIsDropped() {
        val frame = Spectrum.frame(samples(140f, Float.NaN, -3f, gpu = listOf(300f, 20f, Float.NaN)))

        assertEquals(100f, frame.bars[frame.bars.size - 3]!!.cpu, 0.001f)
        assertEquals(100f, frame.bars[frame.bars.size - 3]!!.gpu!!, 0.001f)
        assertEquals(0f, frame.bars.last()!!.cpu, 0.001f)
        assertNull("قيمة غير رقمية ليست قراءة GPU", frame.bars.last()!!.gpu)
        // عيّنة لا نسبة CPU فيها تُترك فتحتها فارغة — لا شريط صفر يوحي بأن الجهاز كان هادئًا.
        assertNull(frame.bars[frame.bars.size - 2])
    }

    @Test
    fun theSlotCountIsConfigurableAndNeverZero() {
        assertEquals(4, Spectrum.frame(samples(1f, 2f), slots = 4).bars.size)
        assertEquals(1, Spectrum.frame(samples(1f), slots = 0).bars.size)
    }

    @Test
    fun theSummaryDescribesTheVisibleWindow() {
        val summary = Spectrum.summary(samples(20f, 41f, 99f, 40f))

        assertEquals(50, summary.average)
        assertEquals(99, summary.peak)
        assertEquals(LoadSummary(average = 0, peak = 0), Spectrum.summary(emptyList()))
    }
}
