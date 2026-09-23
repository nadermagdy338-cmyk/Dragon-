package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * متوسط النافذة في بطاقة السجل — قاعدتان مقيستان هنا:
 *
 *  1. **لا متوسط دون ثلاث عيّنات مقيسة**: رقمان لا يصنعان متوسط فترة، والواجهة تعرض `—` حين
 *     تُعيد هذه الدالة `null`؛
 *  2. **الفجوة ليست صفرًا**: العيّنة المجهولة (`gpu` على جهاز بلا عقدة رسوم) لا تنزل المتوسط
 *     نحو القاع كأن الرسوم كانت هادئة.
 */
class HistoryWindowTest {

    @Test
    fun `an empty window has no average`() {
        assertNull(windowedAverage(emptyList()))
    }

    @Test
    fun `two samples are not yet a window`() {
        assertNull(windowedAverage(listOf(10f, 20f)))
    }

    @Test
    fun `three samples form the first honest average`() {
        assertEquals(20f, windowedAverage(listOf(10f, 20f, 30f))!!, 0.0001f)
    }

    @Test
    fun `unknown gaps never count as zeros`() {
        val window = listOf(null, 80f, null, 80f, 80f)
        assertEquals(80f, windowedAverage(window)!!, 0.0001f)
    }

    @Test
    fun `two measured plus gaps are still not a window`() {
        assertNull(windowedAverage(listOf(null, 50f, null, 50f)))
    }

    @Test
    fun `all-unknown windows return null not zero`() {
        assertNull(windowedAverage(listOf(null, null, null)))
    }

    @Test
    fun `the average spans every measured sample`() {
        val window = listOf(0f, 50f, 100f, null, 50f)
        assertEquals(50f, windowedAverage(window)!!, 0.0001f)
    }
}
