/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalModelTest {

    @Test
    fun `the two thresholds are the ones the screen already used`() {
        assertEquals(ThermalLevel.Nominal, ThermalModel.levelOf(0))
        assertEquals(ThermalLevel.Nominal, ThermalModel.levelOf(49))
        assertEquals(ThermalLevel.Warm, ThermalModel.levelOf(50))
        assertEquals(ThermalLevel.Warm, ThermalModel.levelOf(69))
        assertEquals(ThermalLevel.Hot, ThermalModel.levelOf(70))
        assertEquals(ThermalLevel.Hot, ThermalModel.levelOf(120))
    }

    @Test
    fun `throttling starts at the lowest passive trip, not at the shutdown trip`() {
        val trips = listOf(
            TripReading(85, "passive"),
            TripReading(65, "passive"),
            // نقطة إغلاق **أدنى** من نقطة التخفيف: بلا استثنائها لصارت «بداية التخفيف»
            // ٦٠ وهي إغلاق لا تخفيف.
            TripReading(60, "critical"),
            TripReading(95, "critical"),
            TripReading(75, "hot")
        )
        assertEquals(65, ThermalModel.throttleStart(trips))
        // الإغلاق ليس تخفيفًا: من يعرضه كبداية تخفيف يقول إن المعالج يُخفَّض عند ٩٥°
        // بينما النظام يغلقه هناك. والإغلاق يُعرض بأدنى نقطة إغلاق معلنة (٦٠)، لا
        // بأعلى نقطة في القائمة.
        assertEquals(60, ThermalModel.shutdownPoint(trips))
    }

    @Test
    fun `a device that declares nothing is unknown, and unknown is not safe`() {
        assertNull(ThermalModel.throttleStart(emptyList()))
        assertNull(ThermalModel.shutdownPoint(emptyList()))
        assertEquals(ThrottleState.Unknown, ThermalModel.throttleState(99, emptyList()))
        assertNull(ThermalModel.headroomC(99, emptyList()))
    }

    @Test
    fun `state and headroom follow the declared trip point`() {
        val trips = listOf(TripReading(70, "passive"))
        assertEquals(ThrottleState.Headroom, ThermalModel.throttleState(60, trips))
        assertEquals(10, ThermalModel.headroomC(60, trips))
        // التجاوز يُعاد كرقم سالب: الحقيقة أننا تجاوزناها، لا أن المتّسع صفر.
        assertEquals(ThrottleState.Reached, ThermalModel.throttleState(78, trips))
        assertEquals(-8, ThermalModel.headroomC(78, trips))
    }

    @Test
    fun `zero and negative trip readings are treated as not declared`() {
        val trips = listOf(TripReading(0, "passive"), TripReading(-5, "passive"))
        assertNull(ThermalModel.throttleStart(trips))
        assertEquals(ThrottleState.Unknown, ThermalModel.throttleState(60, trips))
    }
}
