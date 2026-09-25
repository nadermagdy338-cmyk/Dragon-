package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد قراءة الحرارة بالدفعات — الأجزاء **النقيّة** منه (بناء المسارات + التجميع).
 *
 * ولماذا هذا الاختبار وجوديّ لا شكليّ: الدفعة الواحدة تُقرأ بترتيب ثابت، فخطأ ترتيب واحد
 * («الحرارة من `mode`») يمرّ صامتًا إلى الشاشة كأرقام غريبة. والاختبار يثبّت **الاقتران**
 * بين [ThermalUtil.zonePaths] و[ThermalUtil.assembleZones] لا كليهما على حِدة.
 *
 * وما لا يقيسه (يُعلن): كلفة معاملة الـIPC الحقيقية، وسلوك الجذر على عتاد حقيقي — يحتاج جهازًا.
 */
class ThermalZoneBatchTest {

    private fun meta(id: Int, label: String = "zone$id", category: String = "CPU", dir: String = "/sys/class/thermal/thermal_zone$id") =
        ThermalUtil.ZoneMeta(id = id, label = label, category = category, dirPath = dir)

    @Test
    fun `every zone contributes exactly temp then mode, in that order`() {
        val paths = ThermalUtil.zonePaths(listOf(meta(0), meta(3)))
        assertEquals(
            listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone0/mode",
                "/sys/class/thermal/thermal_zone3/temp",
                "/sys/class/thermal/thermal_zone3/mode",
            ),
            paths,
        )
    }

    @Test
    fun `values pair with zones by index — a swap would show up here`() {
        val metas = listOf(meta(0), meta(1))
        val zones = ThermalUtil.assembleZones(
            metas,
            listOf("45000", "enabled", "61000", "disabled"),
        )
        assertEquals(45, zones[0].temperatureC)
        assertTrue(zones[0].isEnabled)
        assertEquals(61, zones[1].temperatureC)
        assertFalse(zones[1].isEnabled)
    }

    @Test
    fun `a missing reading keeps the previous semantics instead of inventing numbers`() {
        val zones = ThermalUtil.assembleZones(
            listOf(meta(0), meta(1)),
            listOf(null, null, "", " "),
        )
        // حرارة غائبة ⇒ صفر (كما كان)، و`mode` غائب ⇒ مُتمكَّن، و"disabled" وحده ⇒ معطَّل.
        assertEquals(0, zones[0].temperatureC)
        assertTrue(zones[0].isEnabled)
        assertEquals(0, zones[1].temperatureC)
        assertTrue(zones[1].isEnabled)
    }

    @Test
    fun `a short batch cannot shift zones onto the wrong readings`() {
        val zones = ThermalUtil.assembleZones(listOf(meta(0), meta(1)), listOf("42000"))
        assertEquals(42, zones[0].temperatureC)
        assertEquals(0, zones[1].temperatureC)
        assertTrue(zones[1].isEnabled)
    }

    @Test
    fun `temperatures are sanitized with the established units`() {
        val zones = ThermalUtil.assembleZones(
            listOf(meta(0), meta(1), meta(2), meta(3)),
            listOf("45000", null, "455", null, "-9999", null, "777777", null),
        )
        assertEquals(45, zones[0].temperatureC)      // milli-degrees
        assertEquals(45, zones[1].temperatureC)      // deci-degrees
        assertEquals(0, zones[2].temperatureC)       // below the plausible floor
        assertEquals(0, zones[3].temperatureC)       // outside the plausible ceiling
    }

    @Test
    fun `identity and given order survive assembly`() {
        val metas = listOf(
            meta(7, label = "gpu-0", category = "GPU"),
            meta(2, label = "cpu-1", category = "CPU"),
        )
        val zones = ThermalUtil.assembleZones(metas, listOf("1", null, "2", null))
        assertEquals(listOf(7, 2), zones.map { it.id })
        assertEquals(listOf("gpu-0", "cpu-1"), zones.map { it.label })
        assertEquals(listOf("GPU", "CPU"), zones.map { it.category })
        assertEquals(
            listOf(
                "/sys/class/thermal/thermal_zone7",
                "/sys/class/thermal/thermal_zone2",
            ),
            zones.map { it.sysfsPath },
        )
    }

    @Test
    fun `no zones means no readings and no phantom work`() {
        assertTrue(ThermalUtil.assembleZones(emptyList(), emptyList()).isEmpty())
        assertTrue(ThermalUtil.zonePaths(emptyList()).isEmpty())
    }
}
