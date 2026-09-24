package nd.max.core.atlas

import nd.max.core.hardware.AtlasRouteMemoryEntry
import nd.max.core.hardware.AtlasRouteOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ملف الجهاز — المعرفة المُكتسبة **تُبنى وتنتهي صلاحيتها**، ولا تُخزَّن افتراضًا ثابتًا.
 *
 * ما يُقاس: الصلاحية بالأجيال (جيل إقلاع آخر ⇒ ما تعلّمناه عن الجهاز القديم باطل)، وأن ما يُعدّ
 * «مُثبَتًا» هو وحده ما نجح فعلًا في الجيل نفسه، وأن التجميع لا يمسّ الجهاز ولا يُصدِّر هويته.
 */
class AtlasDeviceProfileTest {

    private fun entry(
        target: String = "cpu_frequency",
        routeId: String = "cpu-limits-policy0",
        outcome: AtlasRouteOutcome = AtlasRouteOutcome.VERIFIED,
        boot: Long = 1L,
        privilege: Long = 0L,
        successes: Int = 1,
        rollbackFailures: Int = 0,
    ) = AtlasRouteMemoryEntry(
        target = target,
        routeId = routeId,
        lastOutcome = outcome,
        lastVerifiedElapsedMs = 50L,
        bootGeneration = boot,
        privilegeGeneration = privilege,
        successes = successes,
        failures = 0,
        rollbackFailures = rollbackFailures,
    )

    private fun profile(
        learned: List<AtlasRouteMemoryEntry> = listOf(entry()),
        boot: Long = 1L,
        privilege: Long = 0L,
    ) = AtlasDeviceProfile.build(
        identity = AtlasDeviceIdentity(supportedAbis = listOf("arm64-v8a"), apiLevel = 34),
        catalogVersion = "test-catalog",
        capabilities = AtlasCapabilityMap(
            listOf(
                AtlasCapabilityRules.derive(
                    AtlasCapabilityInputs(
                        target = AtlasControlTarget.CPU_FREQUENCY,
                        measured = true,
                        readable = true,
                        absenceProved = false,
                        routeKnown = true,
                        routeStatus = AtlasRouteStatus.ELIGIBLE,
                        verifiedThisGeneration = learned.any { it.target == "cpu_frequency" && it.isVerifiedIn(boot, privilege) },
                    ),
                ),
            ),
        ),
        learned = learned,
        clockMs = { 100L },
        bootGeneration = boot,
        privilegeGeneration = privilege,
    )

    @Test
    fun `a verified route is remembered as verified knowledge of this generation`() {
        val device = profile()
        assertEquals(1, device.verifiedRoutes().size)
        assertEquals("cpu-limits-policy0", device.verifiedRoutes().single().routeId)
        assertTrue(device.verifiedRoutes().single().verified)
    }

    @Test
    fun `knowledge from another boot is void, not a fixed assumption`() {
        val device = profile(learned = listOf(entry(boot = 0L)))
        assertEquals(
            "ما تعلّمه جيل آخر لا يُثبت شيئًا في هذا الجيل",
            0,
            device.verifiedRoutes().size,
        )
        assertEquals(
            AtlasStaleness.SUPERSEDED_BY_BOOT,
            device.stalenessAt(nowMs = 200L, bootGeneration = 2L, privilegeGeneration = 0L),
        )
    }

    @Test
    fun `a privilege change voids what was readable`() {
        val device = profile()
        assertEquals(
            AtlasStaleness.SUPERSEDED_BY_PRIVILEGE,
            device.stalenessAt(nowMs = 200L, bootGeneration = 1L, privilegeGeneration = 1L),
        )
    }

    @Test
    fun `the same generations keep the profile usable and time alone never expires it`() {
        val device = profile()
        assertTrue(device.isUsableAt(nowMs = 150L, bootGeneration = 1L, privilegeGeneration = 0L))
        // معرفة الأجيال (`AtlasVolatility.STATIC`) لا تنتهي بالساعة أبدًا — انتهاؤها بالتغيير فقط:
        // ساعةٌ طويلة لا تجعل معرفةً عن الجهاز «قديمة»، وجيلُ إقلاعٍ واحد يجعلها باطلة (اختباران فوق).
        assertTrue(device.isUsableAt(nowMs = Long.MAX_VALUE, bootGeneration = 1L, privilegeGeneration = 0L))
    }

    @Test
    fun `a rollback failure is carried as such and never counted as verified`() {
        val device = profile(learned = listOf(entry(outcome = AtlasRouteOutcome.ROLLBACK_FAILED, successes = 0, rollbackFailures = 1)))
        assertEquals(0, device.verifiedRoutes().size)
        assertEquals(1, device.learnedRoutes.single().rollbackFailures)
    }

    @Test
    fun `building performs no io and keeps the capability map it was given`() {
        val device = profile()
        assertEquals(AtlasCapabilityState.SUPPORTED, device.capabilities.forTarget(AtlasControlTarget.CPU_FREQUENCY)?.state)
        assertEquals("test-catalog", device.catalogVersion)
        assertEquals(100L, device.builtAtElapsedMs)
    }
}
