package nd.max.core.diagnostics

import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasRouteReason
import nd.max.core.atlas.AtlasRouteStatus
import nd.max.core.hardware.AccessLevel
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.FeatureCapability
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.core.hardware.HardwareFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AtlasRoutePlanner` had no production caller before this, so nothing checked that its input could
 * actually be built from a device. These tests build that input from fake readers and pin the three
 * answers that matter to a user staring at a control that does nothing: eligible, blocked-with-a-reason,
 * and not-reviewed.
 */
class HardwareRouteHealthTest {

    @Test
    fun `a writable device with readable baselines yields an eligible arbiter route`() {
        val verdicts = HardwareRouteHealth.verdicts(writableSnapshot(), cpuDevice(), gpuDevice())

        listOf(
            HardwareFeature.CPU_FREQUENCY,
            HardwareFeature.CPU_GOVERNOR,
            HardwareFeature.GPU_FREQUENCY,
            HardwareFeature.GPU_GOVERNOR,
        ).forEach { feature ->
            val verdict = verdicts.single { it.feature == feature }
            assertEquals("$feature must be eligible", AtlasRouteStatus.ELIGIBLE, verdict.status)
            assertEquals(AtlasControlTransport.ARBITER_SYSFS, verdict.transport)
            assertNull(verdict.reason)
            assertTrue("$feature must name its provider", !verdict.providerId.isNullOrBlank())
            assertEquals("eligible:arbiter_sysfs", verdict.code)
        }
    }

    @Test
    fun `a device that cannot be written blocks the route with a privilege reason`() {
        val verdicts = HardwareRouteHealth.verdicts(readOnlySnapshot(), cpuDevice(), gpuDevice())

        verdicts.filter { it.feature in writableFeatures }.forEach { verdict ->
            assertEquals("${verdict.feature} must be blocked", AtlasRouteStatus.BLOCKED, verdict.status)
            assertEquals(AtlasRouteReason.PRIVILEGE_UNAVAILABLE, verdict.reason)
            assertNull("a blocked control selects no route", verdict.transport)
            assertEquals("blocked:privilege_unavailable", verdict.code)
        }
    }

    @Test
    fun `a policy with no proven bounds blocks the frequency route instead of guessing a unit`() {
        val verdicts = HardwareRouteHealth.verdicts(
            writableSnapshot(),
            cpuDevice(withProvenBounds = false),
            gpuDevice(),
        )

        val verdict = verdicts.single { it.feature == HardwareFeature.CPU_FREQUENCY }
        // A governor is still controllable on this device — the failure is local to the frequency
        // evidence, which is exactly why the verdict is per control and not per device.
        assertEquals(AtlasRouteStatus.BLOCKED, verdict.status)
        assertEquals(AtlasRouteReason.UNIT_AMBIGUOUS, verdict.reason)
        assertEquals(
            AtlasRouteStatus.ELIGIBLE,
            verdicts.single { it.feature == HardwareFeature.CPU_GOVERNOR }.status,
        )
    }

    @Test
    fun `two equally proven gpus are reported as ambiguous rather than as no gpu`() {
        val verdicts = HardwareRouteHealth.verdicts(writableSnapshot(), cpuDevice(), gpuDevice(count = 2))

        val verdict = verdicts.single { it.feature == HardwareFeature.GPU_FREQUENCY }
        assertEquals(AtlasRouteStatus.BLOCKED, verdict.status)
        assertEquals(AtlasRouteReason.PROVIDER_AMBIGUOUS, verdict.reason)
        assertNull(verdict.transport)
    }

    @Test
    fun `a control with no reviewed route is reported as not reviewed`() {
        val verdicts = HardwareRouteHealth.verdicts(writableSnapshot(), emptyCpu(), emptyGpu())

        listOf(HardwareFeature.CPU_FREQUENCY, HardwareFeature.GPU_FREQUENCY).forEach { feature ->
            val verdict = verdicts.single { it.feature == feature }
            assertEquals(
                "a device with nothing readable has no reviewed route, which is not the same as unsupported",
                AtlasRouteStatus.REVIEW_REQUIRED,
                verdict.status,
            )
            assertEquals("review_required:route_not_reviewed", verdict.code)
        }

        // No verified route exists for these in this build, and the answer says so rather than claiming
        // the device is incapable.
        listOf(HardwareFeature.THERMAL_COOLING, HardwareFeature.DISPLAY_REFRESH, HardwareFeature.ZRAM).forEach { feature ->
            assertEquals(AtlasRouteStatus.REVIEW_REQUIRED, verdicts.single { it.feature == feature }.status)
        }
        assertEquals(AtlasControlTarget.MEMORY, verdicts.single { it.feature == HardwareFeature.ZRAM }.target)
    }

    @Test
    fun `every monitored control produces exactly one verdict`() {
        val verdicts = HardwareRouteHealth.verdicts(writableSnapshot(), cpuDevice(), gpuDevice())

        assertEquals(HardwareRouteHealth.monitoredFeatures.size, verdicts.size)
        assertEquals(
            HardwareRouteHealth.monitoredFeatures.toSet(),
            verdicts.map { it.feature }.toSet(),
        )
    }

    // ---- fixtures ----------------------------------------------------------------------------------

    private val writableFeatures = listOf(
        HardwareFeature.CPU_FREQUENCY,
        HardwareFeature.CPU_GOVERNOR,
        HardwareFeature.GPU_FREQUENCY,
        HardwareFeature.GPU_GOVERNOR,
    )

    private fun writableSnapshot() = snapshot(AccessLevel.READ_WRITE)

    private fun readOnlySnapshot() = snapshot(AccessLevel.READ_ONLY)

    private fun snapshot(access: AccessLevel) = HardwareCapabilitySnapshot(
        vendor = "fixture",
        platform = "fixture",
        features = writableFeatures.associateWith { feature ->
            FeatureCapability(feature, access, "fixture-backend")
        },
    )

    /** One cpufreq policy, as a legacy-oriented kernel would expose it. */
    private fun cpuDevice(withProvenBounds: Boolean = true): CpuHardwareBackend.DiscoveryIo = fakeCpu(
        policy0 = buildMap {
            put("scaling_governor", "schedutil")
            put("scaling_available_governors", "schedutil performance powersave")
            put("scaling_min_freq", "300000")
            put("scaling_max_freq", "2400000")
            if (withProvenBounds) {
                put("cpuinfo_min_freq", "300000")
                put("cpuinfo_max_freq", "2400000")
                put("scaling_available_frequencies", "300000 1000000 1800000 2400000")
            }
        },
    )

    private fun emptyCpu() = fakeCpu(policy0 = emptyMap(), present = false)

    private fun gpuDevice(count: Int = 1): GpuHardwareBackend.ReadIo = fakeGpu(
        names = (0 until count).map { "kgsl-3d$it" },
    )

    private fun emptyGpu() = fakeGpu(names = emptyList())

    private fun fakeCpu(
        policy0: Map<String, String>,
        present: Boolean = true,
    ): CpuHardwareBackend.DiscoveryIo = object : CpuHardwareBackend.DiscoveryIo {
        override fun read(path: String): String? = when {
            path == "/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state" -> null
            path.startsWith("/sys/devices/system/cpu/cpufreq/policy0/") ->
                policy0[path.substringAfterLast('/')]

            else -> null
        }

        override fun listDirectories(path: String): List<String> =
            if (present && path == "/sys/devices/system/cpu/cpufreq") listOf("policy0") else emptyList()
    }

    private fun fakeGpu(names: List<String>): GpuHardwareBackend.ReadIo = object : GpuHardwareBackend.ReadIo {
        override fun exists(path: String): Boolean = path in names.map { "/sys/class/devfreq/$it" }

        override fun read(path: String): String? {
            val name = names.firstOrNull { path.startsWith("/sys/class/devfreq/$it/") } ?: return null
            return when (path.removePrefix("/sys/class/devfreq/$name/")) {
                "device_name" -> name
                "governor" -> "msm-adreno-tz"
                "available_governors" -> "msm-adreno-tz performance powersave"
                "available_frequencies" -> "180000000 305000000 450000000"
                "min_freq" -> "180000000"
                "max_freq" -> "450000000"
                "cur_freq" -> "305000000"
                else -> null
            }
        }

        override fun listDirectories(path: String): List<String> =
            if (path == "/sys/class/devfreq") names else emptyList()
    }
}
