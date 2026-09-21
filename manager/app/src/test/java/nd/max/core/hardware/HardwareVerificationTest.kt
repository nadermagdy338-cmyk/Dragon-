package nd.max.core.hardware

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الحكم على «هل تحقّق الطلب؟» — القياس الذي وُجدت هذه الدوال من أجله مسجَّل في `HANDOFF.md`:
 * `APPLY_VERIFY_FAILED knob=gpu_profile expected=1300000000 live=754000000` ثم إعادة المحاولة
 * والاسترجاع في كل دورة انحراف. والطلب كان **مُلبّى** (لا يتجاوز السقف) والحكم كان خاطئًا.
 *
 * فهذه الاختبارات تحمي معنيين لا نصّين: السقف لا يتجاوز، والمدى يبقى داخله، والباقي تساوٍ.
 */
class HardwareVerificationTest {

    @Test
    fun `a live ceiling below the request satisfies the request`() {
        assertTrue(
            "قيمة الـvendor الأضيق تلبّي طلب السقف: الكتابة عليها مقاومة لمُلطِّف لا إصلاح",
            HardwareVerification.ceilingAtMost("1300000000", "754000000"),
        )
    }

    @Test
    fun `a live ceiling above the request does not satisfy it`() {
        assertFalse(HardwareVerification.ceilingAtMost("754000000", "1300000000"))
    }

    @Test
    fun `an unreadable or zero live value is never a satisfied ceiling`() {
        assertFalse("غياب القياس ليس نجاحًا", HardwareVerification.ceilingAtMost("1300000000", null))
        assertFalse(HardwareVerification.ceilingAtMost("1300000000", "0"))
        assertFalse(HardwareVerification.ceilingAtMost("1300000000", "unreadable"))
    }

    @Test
    fun `a non numeric request falls back to literal equality`() {
        assertTrue(HardwareVerification.ceilingAtMost("boost", "boost"))
        assertFalse(HardwareVerification.ceilingAtMost("boost", "powersave"))
    }

    @Test
    fun `a live range inside the requested range is satisfied`() {
        assertTrue(HardwareVerification.rangeContained("300000:2000000", "500000:1800000"))
        assertTrue("أرضية أعلى من المطلوب ليست فشلًا: معنى الأرضية «على الأقل»", HardwareVerification.rangeContained("300000:2000000", "800000:2000000"))
    }

    @Test
    fun `a live range that leaves the request is not satisfied`() {
        assertFalse("سقف أعلى من المطلوب", HardwareVerification.rangeContained("300000:2000000", "300000:2200000"))
        assertFalse("أرضية أسفل المطلوب", HardwareVerification.rangeContained("800000:2000000", "300000:2000000"))
    }

    @Test
    fun `an equal pair is a lock and requires the same live ceiling`() {
        assertTrue(HardwareVerification.rangeContained("800000:800000", "800000:800000"))
        assertFalse(
            "تثبيت 800MHz لا يتحقّق بـ500MHz: المعنى الوحيد للقفل هو التساوي",
            HardwareVerification.rangeContained("800000:800000", "500000:500000"),
        )
    }

    @Test
    fun `a range with no readable side is not a satisfied range`() {
        assertFalse("مدى بلا حدّ مقروء لا يُثبت تلبية", HardwareVerification.rangeContained("300000:2000000", ":"))
    }

    @Test
    fun `exact remains the default meaning of a single value`() {
        assertTrue(HardwareVerification.exact("performance", "performance"))
        assertFalse(HardwareVerification.exact("performance", "performance "))
    }
}
