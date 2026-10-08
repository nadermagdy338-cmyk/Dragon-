package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTimeLeftModelTest {
    @Test
    fun `half of a five thousand milliamp hour battery drawing five hundred milliamps lasts five hours`() {
        assertEquals(300, HomeTimeLeftModel.minutesLeft(50, 5_000_000L, -500, charging = false))
    }

    @Test
    fun `charging, unknown capacity or a weak current gives no estimate`() {
        assertNull(HomeTimeLeftModel.minutesLeft(50, 5_000_000L, -500, charging = true))
        assertNull(HomeTimeLeftModel.minutesLeft(50, null, -500, charging = false))
        assertNull(HomeTimeLeftModel.minutesLeft(50, 5_000_000L, -50, charging = false))
    }

    @Test
    fun `a positive current means charging and gives no discharge estimate`() {
        assertNull(HomeTimeLeftModel.minutesLeft(50, 5_000_000L, 500, charging = false))
    }
}
