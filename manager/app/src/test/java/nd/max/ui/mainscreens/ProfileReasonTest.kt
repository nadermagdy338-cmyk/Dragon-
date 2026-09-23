package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import nd.max.R

/**
 * المقطع المُضاء في سلّم الملفات — من نتيجة حِلقة الملف الحقيقية لا من تخمين الواجهة.
 *
 * والقاعدة المقيسة هنا: المجهول يُعيد `null` فلا يُضاء أي مقطع على شكل «مُختار» لقيمة
 * لا نملكها — وهي الحالة التي تحمي من شريط يقول «متوازن» والملف الحقيقية شيء آخر.
 */
class ProfileReasonTest {

    @Test
    fun `each known profile maps to the reason the engine accepts`() {
        assertEquals("1", profileReasonFor(R.string.Profile_Performance))
        assertEquals("1", profileReasonFor(R.string.profile_perflite))
        assertEquals("2", profileReasonFor(R.string.Profile_Balanced))
        assertEquals("3", profileReasonFor(R.string.Profile_ECO_mode))
    }

    @Test
    fun `an unknown profile lights no segment instead of borrowing one`() {
        assertNull(profileReasonFor(R.string.status_initializing))
    }
}
