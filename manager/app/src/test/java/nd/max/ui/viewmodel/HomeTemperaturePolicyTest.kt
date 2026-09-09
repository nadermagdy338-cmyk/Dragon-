package nd.max.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
