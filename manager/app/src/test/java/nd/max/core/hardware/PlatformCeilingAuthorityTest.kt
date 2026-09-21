package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * طبقة سلطة المنصّة تُختبر من جزئها **الخالص**: صيغة الطلب، وتعرّف جهاز تبريد GPU.
 *
 * ولماذا هذه الأجزاء بالذات: الخطأ فيها لا يظهر عطلًا في الواجهة بل **قمعًا صامتًا** — قيمة تُكتب
 * بترتيب وسائط مقلوب أو بوحدة خاطئة فيردّها السائق، ويُقرأ ذلك «الجهاز لا يقبل القيمة» بينما
 * الطلب نفسه كان مشوَّهًا. فهي أرخص موضع يُثبت فيه الخطأ وأغلاه في الإنتاج.
 */
class PlatformCeilingAuthorityTest {

    @Test
    fun `MI thermal limit is written as cpu policy plus a kHz value`() {
        assertEquals("cpu4 2200000", PlatformCeilingAuthority.cpuLimitRequest(4, 2_200_000L))
        assertEquals("cpu0 300000", PlatformCeilingAuthority.cpuLimitRequest(0, 300_000L))
    }

    @Test
    fun `MTK powerhal range is written as policy leader then min then max in kHz`() {
        // الترتيب هو العقد: الرقم الأول سياسة، ثم الأدنى، ثم الأعلى — وقلبهما يُنتج حدًّا معكوسًا.
        assertEquals("7 1000000 3250000", PlatformCeilingAuthority.powerhalRangeRequest(7, 1_000_000L, 3_250_000L))
    }

    @Test
    fun `no-limits mode keeps the value the vendor interface documents`() {
        // `6` ليست اختيارًا من عندنا: هي القيمة التي تتوقّعها واجهة حرارة MI. وتغييرها يجعل
        // «التحرير» يضبط حدًّا بدل أن يرفعه.
        assertEquals(6, PlatformCeilingAuthority.MI_THERMAL_NO_LIMITS_MODE)
    }

    @Test
    fun `a GPU cooling device is recognised by name not by index`() {
        // الفهرسة تختلف بين الأجهزة، فالاسم هو العقد الوحيد الصالح: على جهاز مقيس كان
        // GPU في `cooling_device3`، وعلى غيره سيكون في غيره.
        assertTrue(PlatformCeilingAuthority.isGpuCoolingType("mali"))
        assertTrue(PlatformCeilingAuthority.isGpuCoolingType("GPU"))
        assertTrue(PlatformCeilingAuthority.isGpuCoolingType("gpufreq"))
        assertTrue(PlatformCeilingAuthority.isGpuCoolingType("graphics"))
        assertTrue(PlatformCeilingAuthority.isGpuCoolingType("mali_thermal"))
    }

    @Test
    fun `a non-GPU cooling device is never mistaken for the GPU cap`() {
        // ولو أخذناها بالخطأ لكتبنا `0` على تبريد آخر (شحن أو بطارية) فأضعفنا حماية لا تخصّنا.
        listOf("cpu", "battery", "charger", "quiet_therm", "pa", "tp", "").forEach {
            assertFalse("\"$it\" ليست جهاز تبريد GPU", PlatformCeilingAuthority.isGpuCoolingType(it))
        }
    }

    @Test
    fun `a report with no accepted channel claims no release`() {
        // الشرط السلوكي الوحيد: لا يُدّعى تحرير لم يقع. وتقرير فارغ يجب أن يقول ذلك صراحةً.
        val none = PlatformCeilingAuthority.Report(
            releasedMiThermalMode = false,
            cpuLimitAccepted = false,
            powerhalRangeAccepted = false,
            gpuCoolingReleased = false,
            gedCeilingReleased = false,
        )
        assertFalse(none.anyChannel)

        val one = none.copy(gpuCoolingReleased = true)
        assertTrue(one.anyChannel)
    }

    @Test
    fun `a policy path yields its index and a non-policy path yields nothing`() {
        assertEquals(0, PlatformCeilingAuthority.policyIndex("/sys/devices/system/cpu/cpufreq/policy0"))
        assertEquals(7, PlatformCeilingAuthority.policyIndex("/sys/devices/system/cpu/cpufreq/policy7"))
        assertEquals(12, PlatformCeilingAuthority.policyIndex("/x/policy12"))
        // والسقوط إلى `0` هنا خطر لا تبسيط: يكتب سلطة على policy0 بدل أن يكتب لا شيء.
        assertNull(PlatformCeilingAuthority.policyIndex("/sys/devices/system/cpu/cpufreq"))
        assertNull(PlatformCeilingAuthority.policyIndex("/x/policyN"))
    }
}
