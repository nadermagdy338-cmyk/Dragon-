package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * إعادة الاستهداف هي الآلية التي يتحرّك بها الحارس الحراري: يخفض السقف داخل ما طلبه المستخدم،
 * ويعيده عند البرودة — **بنفس خط الأساس** فلا يتغيّر ما سيُسترجع في نهاية الجلسة.
 *
 * والحدّ الذي تحميه هذه الاختبارات: قرار آليّ لا يجوز أن يمحو إعدادًا اختاره المستخدم. فإن
 * فشلت إعادة الاستهداف، تبقى النيّة السابقة مُسجَّلة، لا أن تُطرح ويصير المقبض بلا مالك.
 */
class PerAppControlRegistryRetargetTest {

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("per-app-retarget-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    @Test
    fun `a retarget moves the owned value and keeps the entry owned`() {
        var live = "idle"
        val registry = registry()
        assertTrue(registry.ownValue("cpu_limits:policy0", "300000:2000000", { live = it; true }, { live }))

        assertTrue(registry.retarget("cpu_limits:policy0", "300000:1500000"))

        assertEquals("القيمة الجديدة على العتاد", "300000:1500000", live)
        assertEquals("والنيّة المنشورة هي الجديدة", "300000:1500000", registry.ownedDesired()["cpu_limits:policy0"])
        assertTrue("والمقبض باقٍ مملوكًا لدورة الانحراف التالية", registry.verifyAndRepair().single().successful)
    }

    @Test
    fun `a failed retarget keeps the previous intent instead of dropping the knob`() {
        var live = "idle"
        var refuse = false
        val registry = registry()
        assertTrue(
            registry.ownValue(
                "cpu_limits:policy0",
                "300000:2000000",
                { value -> if (refuse) false else { live = value; true } },
                { live },
            )
        )

        refuse = true
        assertFalse(registry.retarget("cpu_limits:policy0", "300000:1000000"))

        assertEquals(
            "إعداد المستخدم محفوظ: الحارس الآلي لا يُسقط اختيارًا بشريًّا",
            "300000:2000000",
            registry.ownedDesired()["cpu_limits:policy0"],
        )
    }

    @Test
    fun `retargeting an unknown knob reports false rather than inventing an owner`() {
        val registry = registry()
        assertFalse(registry.retarget("cpu_limits:policy9", "1:2"))
    }

    private fun registry() = PerAppControlRegistry(
        mutationGate = HardwareControlArbiter(),
        confirmationSamples = 1,
        confirmationIntervalMs = 0L,
        sleep = {},
    )
}
