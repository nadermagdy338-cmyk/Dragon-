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

import nd.max.ui.util.SensorInventory.Kind
import nd.max.ui.util.SensorInventory.ReadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات `GAP-11`.
 *
 * وأهمّها اختبار **يعيد إنتاج العطب الذي كان في المستودع**: قراءة ضوء لم يصل فيها حدث كانت
 * تُعاد `0f`. وهذه الاختبارات تثبّت أنه لا يُعاد صفر أبدًا عند العجز.
 */
class SensorInventoryTest {

    // ────────────────────────────────────────────────────────────────────────
    // قاعدة قراءة الضوء
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a timeout never becomes zero lux`() {
        // هذا هو العطب الأصلي: `registerListener` ثم `unregisterListener` فورًا ⇒ لا حدث،
        // والقيمة تبقى 0f فتُقرأ «مظلمًا».
        val reading = SensorInventory.lightReading(sensorAbsent = false, timedOut = true, lux = null)
        assertEquals(ReadingState.UNREADABLE, reading.state)
        assertNull(reading.lux)
        assertFalse(reading.usable)
    }

    @Test
    fun `a real dark reading is reported as zero, because zero is a fact`() {
        val reading = SensorInventory.lightReading(sensorAbsent = false, timedOut = false, lux = 0f)
        assertEquals(ReadingState.REPORTED, reading.state)
        assertEquals(0f, reading.lux)
        assertTrue(reading.usable)
    }

    @Test
    fun `an absent sensor is absent, not unreadable`() {
        val reading = SensorInventory.lightReading(sensorAbsent = true, timedOut = true, lux = null)
        assertEquals(ReadingState.ABSENT, reading.state)
        assertNull(reading.lux)
    }

    @Test
    fun `a value without a flag is still a reading`() {
        val reading = SensorInventory.lightReading(sensorAbsent = false, timedOut = false, lux = 42f)
        assertEquals(ReadingState.REPORTED, reading.state)
        assertEquals(42f, reading.lux)
    }

    // ────────────────────────────────────────────────────────────────────────
    // التصنيف
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `classifies common platform types`() {
        assertEquals(Kind.MOTION, SensorInventory.kindOf(1))          // ACCELEROMETER
        assertEquals(Kind.ENVIRONMENT, SensorInventory.kindOf(5))      // LIGHT
        assertEquals(Kind.POSITION, SensorInventory.kindOf(3))         // ORIENTATION
        assertEquals(Kind.BODY, SensorInventory.kindOf(21))            // HEART_RATE
        assertEquals(Kind.UNPOSITIONED, SensorInventory.kindOf(28))    // SIGNIFICANT_MOTION
    }

    @Test
    fun `a vendor type is left unclassified rather than guessed`() {
        // أنواع المُصنّعين أرقامها عالية؛ تخمين تصنيفها أسوأ من قول «غير مصنَّف».
        assertEquals(Kind.OTHER, SensorInventory.kindOf(65536))
        assertEquals(Kind.OTHER, SensorInventory.kindOf(-1))
    }

    // ────────────────────────────────────────────────────────────────────────
    // التقرير
    // ────────────────────────────────────────────────────────────────────────

    private fun item(
        name: String,
        typeId: Int,
        kind: Kind = SensorInventory.kindOf(typeId),
        wakeUp: Boolean = false,
        power: Float = 0.15f,
    ) = SensorInventory.Item(
        name = name,
        vendor = "vendor",
        typeId = typeId,
        kind = kind,
        powerMilliAmp = power,
        maxRange = 10f,
        resolution = 0.1f,
        minDelayUs = 5000,
        isWakeUp = wakeUp,
    )

    @Test
    fun `report counts only what the platform declared`() {
        val report = SensorInventory.report(
            listOf(item("accel", 1), item("light", 5)),
            SensorInventory.lightReading(false, false, 120f),
        )
        assertEquals(2, report.count)
        assertEquals(listOf(Kind.MOTION, Kind.ENVIRONMENT), report.kindsReported)
    }

    @Test
    fun `report is ordered by kind then name`() {
        val report = SensorInventory.report(
            listOf(item("z-light", 5), item("a-accel", 1), item("b-accel", 1)),
            SensorInventory.lightReading(false, false, 1f),
        )
        assertEquals(listOf("a-accel", "b-accel", "z-light"), report.items.map { it.name })
    }

    @Test
    fun `wake-up sensors are counted, not hidden`() {
        val report = SensorInventory.report(
            listOf(item("a", 1, wakeUp = true), item("b", 5)),
            SensorInventory.lightReading(true, false, null),
        )
        assertEquals(1, report.wakeUpCount)
    }

    @Test
    fun `an empty inventory is an empty report, not a failure`() {
        val report = SensorInventory.report(emptyList(), SensorInventory.lightReading(true, false, null))
        assertEquals(0, report.count)
        assertTrue(report.kindsReported.isEmpty())
        assertEquals(ReadingState.ABSENT, report.light.state)
    }

    // ────────────────────────────────────────────────────────────────────────
    // التنسيق: «غير معلَن» ليست «صفر»
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an undeclared power or range stays unknown`() {
        assertNull(SensorInventory.powerLabel(0f))
        assertNull(SensorInventory.rangeLabel(0f))
        assertNull(SensorInventory.delayLabel(0))
    }

    @Test
    fun `declared numbers are formatted`() {
        assertEquals("0.15", SensorInventory.powerLabel(0.15f))
        assertEquals("10.0", SensorInventory.rangeLabel(10f))
        assertEquals("5000", SensorInventory.delayLabel(5000))
    }
}
