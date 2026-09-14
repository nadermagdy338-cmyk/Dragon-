package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد نموذج الاستجابة — أثمن ما في هذه المعمارية وأخطره.
 *
 * محاكاة سبقت الكتابة أثبتت أن حدًّا ثابتًا في السمات يرفع خطأ التنبؤ
 * نحو 2.5× ويكسر التمييز الشرطي (1.06× مقابل 4.0× بعد الإزالة). هذه
 * الاختبارات تحرس ذلك القرار الهندسي:
 *
 *   **أثر(خطوة صفر) = صفر** — قانون فيزيائي لا خيار تصميمي.
 */
class ResponseModelTest {

    private fun features(step: Float, load: Float, thermal: Float, intent: Float) =
        ResponseModel.buildFeatures(step, load, thermal, intent)

    @Test
    fun `zero step produces all-zero features`() {
        // لو بقي حدٌّ ثابت لظهرت سمة غير صفرية هنا، فيتنبأ النموذج بأثر
        // بلا تدخل — وهو مستحيل فيزيائيًا وهو أكبر مصادر الخطأ.
        features(0f, 0.9f, 0.2f, 1f).forEachIndexed { i, v ->
            assertEquals("سمة $i عند خطوة صفر", 0f, v, 0.0f)
        }
    }

    @Test
    fun `feature vector excludes a constant term`() {
        // الطول 4 = (خطوة + ثلاث تفاعلات). أي زيادة تعني عودة ثابت مكسور.
        assertEquals(4, features(0.2f, 0.5f, 0.3f, 1f).size)
    }

    @Test
    fun `interactions capture device state conditionality`() {
        val f = features(0.2f, load =  0.8f, thermal = 0.4f, intent = 0.5f)
        assertEquals(0.2f, f[0], 0.0001f)          // الخطوة
        assertEquals(0.16f, f[1], 0.0001f)         // خطوة × حمل
        assertEquals(0.08f, f[2], 0.0001f)         // خطوة × حرارة
        assertEquals(0.10f, f[3], 0.0001f)         // خطوة × نية
    }

    @Test
    fun `step magnitude scales linearly`() {
        val half = features(0.1f, 0.8f, 0.3f, 1f)
        val full = features(0.2f, 0.8f, 0.3f, 1f)
        for (i in half.indices) {
            assertEquals("سمة $i", half[i] * 2f, full[i], 0.0001f)
        }
    }

    @Test
    fun `step fraction is clamped to the unit range`() {
        val over = features(5f, 0.8f, 0.3f, 1f)
        val one = features(1f, 0.8f, 0.3f, 1f)
        assertTrue(over.contentEquals(one))

        val under = features(-5f, 0.8f, 0.3f, 1f)
        val minusOne = features(-1f, 0.8f, 0.3f, 1f)
        assertTrue(under.contentEquals(minusOne))
    }

    @Test
    fun `negative step inverts sign for save-energy direction`() {
        val f = features(-0.25f, 0.6f, 0.5f, 1f)
        assertTrue("الخفض يجب أن يعطي خطوة سالبة", f[0] < 0f)
        assertTrue("تفاعل الحمل يجب أن ينعكس", f[1] < 0f)
        assertTrue("تفاعل الحرارة يجب أن ينعكس", f[2] < 0f)
    }

    @Test
    fun `low load cannot produce a large load interaction`() {
        // جوهر الذكاء الشرطي: نفس الخطوة تحت حمل منخفض تعطي تفاعلًا
        // أدنى بكثير — وهذا ما يجعل النموذج يميّز "الرفع لا ينفع الآن".
        val idle = features(0.2f, load =  0.05f, thermal = 0.3f, intent = 0.5f)
        val busy = features(0.2f, load =  0.95f, thermal = 0.3f, intent = 0.5f)
        assertTrue("تفاعل الحمل يجب أن يفصل الحالتين", busy[1] > idle[1] * 5f)
    }
}
