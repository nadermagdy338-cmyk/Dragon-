/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import nd.max.ui.mainscreens.gpuRouteForChipset

class HomeTemperaturePolicyTest {

    @Test
    fun `primary temperature uses battery instead of hotter component sensors`() {
        val state = DashboardState(
            batteryTempC = 38.5f,
            cpuTempC = 60,
            gpuTempC = 55,
            skinTempC = 42
        )

        assertEquals(38.5f, primaryBatteryTemperatureC(state)!!, 0f)
    }

    @Test
    fun `primary temperature is unavailable without a valid battery reading`() {
        assertNull(primaryBatteryTemperatureC(DashboardState(batteryTempC = 0f, cpuTempC = 60)))
        assertNull(primaryBatteryTemperatureC(DashboardState(batteryTempC = -1f, gpuTempC = 55)))
        assertNull(primaryBatteryTemperatureC(DashboardState(batteryTempC = Float.NaN)))
    }

    @Test
    fun `gpu route is canonical and capability neutral`() {
        assertEquals("gpustudio", gpuRouteForChipset("Qualcomm Snapdragon 8 Gen 3 Adreno"))
        assertEquals("gpustudio", gpuRouteForChipset("MediaTek Dimensity 9300 Mali"))
        assertEquals("gpustudio", gpuRouteForChipset("Samsung Exynos Xclipse"))
        assertNull(gpuRouteForChipset(""))
    }
}
