package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * قياس منطق «خيار واحد» في شاشة الحكام — بلا أندرويد وبلا جذر.
 *
 * والقيم في العيّنات **منقولة من عقد حقيقية** (`/sys/block/mmcblk0/queue/scheduler`) لأن
 * العطب الذي يحرسه هذا الاختبار ليس حسابيًّا بل صيغي: قوسان يُقرآن خطأً يُظهران للمستخدم
 * جدولةً ليست السارية، وهي نفس صنف «حالة تُنسب لغير صاحبها» في هذا المستودع.
 */
class HardwareChoiceTest {

    /** القيمة السارية هي ما بين القوسين لا أول عنصر في القائمة. */
    @Test
    fun bracketedReadsTheValueInBrackets() {
        assertEquals("cfq", HardwareChoice.bracketed("none [cfq] mq-deadline"))
        // القوسان على آخر القائمة يعني أن الساري هو الأخير — لا أول عنصر.
        assertEquals("none", HardwareChoice.bracketed("kyber mq-deadline [none]"))
        assertEquals("deadline", HardwareChoice.bracketed("[deadline]"))
    }

    /** بلا قوسين = «لا جواب» لا «القيمة الأولى»؛ والمتصل يستعمل بديله المصرَّح. */
    @Test
    fun bracketedWithoutBracketsIsNoAnswer() {
        assertNull(HardwareChoice.bracketed("none cfq mq-deadline"))
        assertNull(HardwareChoice.bracketed(""))
        assertNull(HardwareChoice.bracketed(null))
        assertNull(HardwareChoice.bracketed("[]"))
        assertNull(HardwareChoice.bracketed("[   ]"))
        assertNull(HardwareChoice.bracketed("vfio [incomplete"))
    }

    /** الحيّ يتقدّم على الخصيصة، والمطابقة لا تتأثر بحالة الأحرف. */
    @Test
    fun liveValueWinsOverSavedProperty() {
        val available = listOf("performance", "powersave", "schedutil")
        assertEquals(2, HardwareChoice.indexOf(available, "schedutil", "powersave"))
        assertEquals(1, HardwareChoice.indexOf(available, "POWERSAVE", "performance"))
    }

    /** حيّ غير معلوم في القائمة ⇒ أول بديل معروف ⇒ الصفر. ولا مؤشر على لا شيء أبدًا. */
    @Test
    fun unknownLiveFallsBackToSavedThenToFirst() {
        val available = listOf("interactive", "schedutil")
        assertEquals(1, HardwareChoice.indexOf(available, "gone", "schedutil"))
        assertEquals(0, HardwareChoice.indexOf(available, "", null, "also-gone"))
        assertEquals(0, HardwareChoice.indexOf(available, null))
    }

    /** قائمة فارغة لا تُنتج مؤشرًا خارج الحدود (القارئ يعرف من `available` أن لا جدولة). */
    @Test
    fun emptyAvailabilityDoesNotIndexOutOfBounds() {
        assertEquals(0, HardwareChoice.indexOf(emptyList(), "cfq", "none"))
    }

    /** الفراغات الطرفية في عقدة النواة لا تُفقد المطابقة. */
    @Test
    fun whitespaceAroundValuesIsIgnored() {
        assertEquals(1, HardwareChoice.indexOf(listOf("none", "cfq"), " cfq "))
        assertEquals("cfq", HardwareChoice.bracketed("none [ cfq ] none"))
    }
}
