package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حكم كتابة الشحن **ثلاثيّ** لا ثنائيّ، والثالث هو سبب وجود الملف.
 *
 * كان الفحص القديم يسأل «نجح أمر الكتابة؟» فقط، فتصل قيمة كُتبت ولم يعرضها السائق
 * (عقدة للكتابة وحسب) فيُعاد المحاولة عليها ثلاثًا بلا فائدة ثم تُوصف النتيجة بالفشل.
 * والفشل ادّعاء معرفة. فالحالات الآن: **مُتحقَّق منه** (قراءة حيّة تطابق المطلوب) ·
 * **كُتب ولم يُتحقّق منه** (لا تُقرأ للخلف: لا ادّعاء ولا اتهام) · **مرفوض** (لم يُكتب).
 */
class ChargingHardwareBackendTest {

    // --- تكافؤ المطلوب والمقروء: يقرّر هل الكتابة نجحت، فخطؤه يقلب الحكم كلّه ---

    @Test fun theSameTextIsEquivalent() {
        assertTrue(ChargingHardwareBackend.equivalent("8", "8"))
        assertTrue(ChargingHardwareBackend.equivalent("100", "100"))
    }

    @Test fun aDriverThatReformatsTheNumberIsNotARefusal() {
        // `charge_control_limit` يعرض عند بعض السائقين `080` أو `80 ` لنفس القيمة —
        // ورفض ذلك كان سيُصنّف كتابةً ناجحة فاشلةً.
        assertTrue(ChargingHardwareBackend.equivalent("80", "80 "))
        assertTrue(ChargingHardwareBackend.equivalent("080", "80"))
    }

    @Test fun aDifferentValueIsNeverEquivalentInAnyFormat() {
        assertFalse(ChargingHardwareBackend.equivalent("80", "100"))
        assertFalse(ChargingHardwareBackend.equivalent("80", "8"))
        assertFalse(ChargingHardwareBackend.equivalent("2500000", "2200000"))
    }

    @Test fun anUnreadableNodeIsNeverEquivalent() {
        // `null` = «لم أستطع أن أقرأ» لا «القيمة مختلفة». وهذا هو الفرق الذي يفصل
        // `APPLIED_UNVERIFIED` عن `REFUSED`.
        assertFalse(ChargingHardwareBackend.equivalent("8", null))
        assertFalse(ChargingHardwareBackend.equivalent("8", ""))
    }

    @Test fun textThatIsNotNumericIsComparedAsText() {
        assertTrue(ChargingHardwareBackend.equivalent("Charging", "Charging"))
        assertFalse(ChargingHardwareBackend.equivalent("Charging", "Discharging"))
        assertFalse(ChargingHardwareBackend.equivalent("Charging", "6"))
    }

    // --- الحالات الثلاث وحدودها ---

    @Test fun aMissingNodeIsRefusedWithoutAnyWrite() {
        val outcome = ChargingHardwareBackend.write(null, "8")
        assertEquals(ChargingHardwareBackend.Verdict.REFUSED, outcome.verdict)
        assertEquals("no-node", outcome.error)
        assertNull(outcome.actual)
        assertNull(outcome.path)
        assertTrue(!outcome.written && !outcome.verified)
    }

    @Test fun appliedButUnverifiableIsNotAFailureAndNotAProof() {
        // تُبنى المحصّلة يدويًّا لأن هذه الحالة لا تُنتَج إلا من جهاز لا يُقرأ للخلف،
        // وهي بالضبط ما يجب أن يبقى متميّزًا: كُتب (written) بلا ادّعاء تحقّق (verified).
        val outcome = ChargingHardwareBackend.Outcome(
            verdict = ChargingHardwareBackend.Verdict.APPLIED_UNVERIFIED,
            requested = "8",
            actual = null,
            error = "node-not-readable-back",
            path = "/sys/class/power_supply/battery/batt_smart_charging",
        )
        assertTrue(outcome.written)
        assertFalse(outcome.verified)
        assertNull(outcome.actual)
    }

    @Test fun verifiedIsTheOnlyVerdictThatCarriesProof() {
        val verified = ChargingHardwareBackend.Outcome(
            verdict = ChargingHardwareBackend.Verdict.VERIFIED,
            requested = "80",
            actual = "80",
            path = "/sys/class/power_supply/battery/charge_control_limit",
        )
        assertTrue(verified.written && verified.verified)
        assertEquals("80", verified.actual)
    }

    @Test fun aRefusalKeepsTheLiveValueItReadSoTheCallerCanShowIt() {
        // الكبح المعلن: السائق يثبّت أقرب قيمة معلنة، فيجب أن يحمل الحكم ما قرؤه
        // ليعرضه المتصل — وإلا عرض المستخدم طلبه وهو ليس ما على العتاد.
        val clamped = ChargingHardwareBackend.Outcome(
            verdict = ChargingHardwareBackend.Verdict.REFUSED,
            requested = "2500000",
            actual = "2200000",
            error = "live value differs from requested value",
            path = "/sys/class/power_supply/battery/fast_charge_current",
        )
        assertFalse(clamped.verified)
        assertEquals("2200000", clamped.actual)
    }
}
