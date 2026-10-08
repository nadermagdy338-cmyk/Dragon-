package nd.max.ui.mainscreens

import nd.max.ui.navigation.MaxDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityFormatTest {
    @Test
    fun `the raw min-max pair in kHz is read as a range and never shown raw`() {
        assertEquals(1_000_000L to 1_900_000L, ActivityFormat.khzPair("1000000:1900000"))
        assertNull(ActivityFormat.khzPair("balanced"))
        assertNull(ActivityFormat.khzPair("300000"))
    }

    @Test
    fun `profiles are named by key and an unknown key is not invented`() {
        assertEquals(ActivityProfile.BALANCED, ActivityFormat.profileOf("balanced"))
        assertEquals(ActivityProfile.POWERSAVE, ActivityFormat.profileOf("eco"))
        assertNull(ActivityFormat.profileOf("custom_x"))
    }

    @Test
    fun `the cpu policies are named by their role so two limit lines never look alike`() {
        assertEquals(ActivityCluster.EFFICIENCY, ActivityFormat.clusterOf("policy0"))
        assertEquals(ActivityCluster.PERFORMANCE, ActivityFormat.clusterOf("policy4"))
        assertEquals(ActivityCluster.PRIME, ActivityFormat.clusterOf("policy7"))
        assertNull(ActivityFormat.clusterOf("policy9"))
    }

    @Test
    fun `machine reasons become a few causes a person understands`() {
        assertEquals(ActivityReason.UNVERIFIED, ActivityFormat.reasonOf("not-verified"))
        assertEquals(ActivityReason.UNSUPPORTED, ActivityFormat.reasonOf("sconfig_missing"))
        assertEquals(ActivityReason.NOT_WRITABLE, ActivityFormat.reasonOf("not-writable"))
        assertEquals(ActivityReason.OTHER, ActivityFormat.reasonOf("something-else"))
    }

    @Test
    fun `a row opens the screen that owns its knob, and a row without one opens nothing`() {
        assertEquals(MaxDestination.CpuCoreControl.route, ActivityFormat.routeFor("cpu_limits:policy4"))
        assertEquals(MaxDestination.GpuStudio.route, ActivityFormat.routeFor("gpu_profile"))
        assertNull(ActivityFormat.routeFor("renderer"))
    }
}
