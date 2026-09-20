package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الطلب يُصاغ من جدول OPP **المُعلَن**، لا من استنباط حسابي بين حدّين.
 *
 * **المقيس** (حزمة سجلّات جهاز حقيقي، MT6899، 2026-09-20): كتابة `2200000` إلى
 * `policy4/scaling_max_freq` لم تُقرأ 2200000 بعد الكتابة (`WRITE_CHECK …
 * wrote=2200000 read=2000000 verdict=differs`)، فحكم المُحكِّم على تغييرٍ ناجح
 * بالفشل، وسجّل `regression rollback cpu_limits:policy4 → 400000:2200000 ::
 * FAILED` ثم فشل الاسترجاع نفسه إلى 2200000 أيضًا. والقيمة 2100000 — وهي من
 * الجدول — ثبتت في عشرات القرارات الأخرى على الجهاز نفسه.
 *
 * **ما لا يقوله هذا الملف:** أيّ طبقة بدّلت القيمة (نواة cpufreq أم طبقة
 * `ppm` في MediaTek) وشريحة العتاد الدقيقة لجداوله — كلاهما يحتاج قياسًا على
 * الجهاز. ما يقوله هو ما تستطيع الوحدة إثباته: أن ما نطلبه لا يخرج عن الجدول
 * الذي يعلنه الجهاز نفسه، وأن غياب الجدول لا يُنتج ترددًا مخترعًا.
 */
class CpuAvailableFrequencySnapTest {

    private fun policy(advertised: List<Long>) = CpuHardwareBackend.Policy(
        path = "/sys/devices/system/cpu/cpufreq/policy4",
        name = "policy4",
        governor = "sugov_ext",
        governors = listOf("sugov_ext"),
        minKHz = 400_000L,
        maxKHz = 1_800_000L,
        hwMinKHz = 400_000L,
        hwMaxKHz = 3_000_000L,
        availableFrequenciesKHz = advertised,
    )

    /**
     * جدول متقطّع يحمل 2100000 ولا يحمل 2200000 — أي أنه يعيد إنتاج **الفرق**
     * الذي قاسته السجلّات دون أن يدّعي أنه جدول الجهاز الحقيقي بخطواته كلها.
     */
    private val sparse = listOf(
        400_000L, 800_000L, 1_200_000L, 1_800_000L, 2_000_000L, 2_100_000L, 2_400_000L, 3_000_000L,
    )

    @Test fun aRequestBetweenTwoAdvertisedStepsLandsOnTheRealOne() {
        assertEquals(2_100_000L, CpuHardwareBackend.snapToAvailableAtOrBelow(policy(sparse), 2_200_000L))
    }

    @Test fun anAdvertisedRequestIsReturnedUnchanged() {
        assertEquals(2_000_000L, CpuHardwareBackend.snapToAvailableAtOrBelow(policy(sparse), 2_000_000L))
        assertEquals(400_000L, CpuHardwareBackend.snapToAvailableAtOrBelow(policy(sparse), 400_000L))
    }

    @Test fun aRequestBelowTheWholeTableLandsOnTheLowestAdvertisedStep() {
        assertEquals(400_000L, CpuHardwareBackend.snapToAvailableAtOrBelow(policy(sparse), 100_000L))
    }

    @Test fun aRequestAboveTheWholeTableLandsOnTheHighestAdvertisedStep() {
        assertEquals(3_000_000L, CpuHardwareBackend.snapToAvailableAtOrBelow(policy(sparse), 9_000_000L))
    }

    @Test fun anUnadvertisedTableNeverInventsAFrequency() {
        // لا جدول ⇒ لا معرفة. تُعاد القيمة كما هي ولا يُدّعى اختيار تردد حقيقي:
        // المجهول ليس صفرًا ولا خطوة مُخترعة.
        assertEquals(2_200_000L, CpuHardwareBackend.snapToAvailableAtOrBelow(policy(emptyList()), 2_200_000L))
    }

    @Test fun theResultIsAlwaysOneOfTheDevicesOwnSteps() {
        // هذه هي الخاصية التي تُصلح العطب: لا يبقى مطلبنا قابلًا للتبديل الصامت
        // في السائق، فلا يُقرأ مختلفًا فيُحكم على نجاحٍ بالفشل ثم يُسترجع.
        val device = policy(sparse)
        listOf(0L, 399_999L, 400_000L, 950_000L, 2_199_999L, 2_200_000L, 3_000_001L, 9_000_000L).forEach { requested ->
            val snapped = CpuHardwareBackend.snapToAvailableAtOrBelow(device, requested)
            assertTrue(
                "الطلب $requested انتهى إلى $snapped وليس خطوة مُعلنة عن الجهاز",
                snapped in device.availableFrequenciesKHz,
            )
        }
    }
}
