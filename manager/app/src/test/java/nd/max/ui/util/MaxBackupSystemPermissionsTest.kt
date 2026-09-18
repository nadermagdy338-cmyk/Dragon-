package nd.max.ui.util

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **كل إذن يطلبه جدول بيانات النظام يجب أن يكون مُعلَنًا في `AndroidManifest.xml`.**
 *
 * ولماذا هذا اختبار لا تعليق: الإذن غير المُعلَن **لا يُمنح أبدًا**، و`checkSelfPermission`
 * يُعيد `DENIED` دائمًا. أي أن فئة كاملة تصبح «تحتاج إذنًا» للأبد، ويبدو العطب كأن المستخدم
 * رفض — لا كأن الاسم مكتوب خطأً. وهذا بالضبط نوع الخطأ الذي يمرّ بصمت بلا اختبار.
 *
 * ويأتي أهميته من أن `UserDictionary` أذوناتها **معروفة للمنصّة ولا تُعلنها
 * `Manifest.permission`**، فمكتوبة عندنا نصًّا — ولا شيء غير هذا الاختبار يكشف خطأً فيها.
 */
class MaxBackupSystemPermissionsTest {

    /** يبحث عن بيان الوحدة صاعدًا من مجلد تشغيل الاختبار، فلا يعتمد على مجلد عمل ثابت. */
    private fun manifestFile(): File? {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("manager/app/src/main/AndroidManifest.xml"),
        )
        candidates.firstOrNull { it.isFile }?.let { return it }

        var dir: File? = File("").absoluteFile
        repeat(6) {
            val current = dir ?: return null
            listOf("app/src/main/AndroidManifest.xml", "manager/app/src/main/AndroidManifest.xml")
                .map { File(current, it) }
                .firstOrNull { it.isFile }
                ?.let { return it }
            dir = current.parentFile
        }
        return null
    }

    @Test
    fun `كل إذن في الجدول مُعلَن في البيان`() {
        val manifest = manifestFile()
        assertTrue(
            "تعذّر العثور على AndroidManifest.xml — الاختبار لا يستطيع أن يثبت شيئًا بلا البيان",
            manifest != null,
        )
        val text = manifest!!.readText()

        val missing = MaxBackupSystem.ALL_PERMISSIONS.filter { permission ->
            !text.contains("android:name=\"$permission\"")
        }
        assertTrue("أذونات مطلوبة وغير مُعلَنة في البيان: $missing", missing.isEmpty())
    }

    @Test
    fun `أذونات القاموس مكتوبة كما تُعلنها المنصّة`() {
        val dictionary = MaxBackupSystem.source(MaxBackupSystem.Kind.USER_DICTIONARY)?.permissions.orEmpty()
        assertTrue(MaxBackupSystem.READ_USER_DICTIONARY in dictionary)
        assertTrue(MaxBackupSystem.WRITE_USER_DICTIONARY in dictionary)
        // البادئة الكاملة جزء من القيمة لا زخرفة عليها: إذن مكتوب بلا `android.permission.`
        // لا يُمنح، ورفضه يبدو كأن المستخدم رفض.
        dictionary.forEach {
            assertTrue("إذن بلا بادئة المنصّة: $it", it.startsWith("android.permission."))
        }
    }

    @Test
    fun `كل فئة مزوّد لها إذن واحد على الأقل`() {
        MaxBackupSystem.SOURCES
            .filter { it.method == MaxBackupSystem.Method.PROVIDER }
            .forEach { source ->
                assertTrue("فئة مزوّد بلا إذن: ${source.kind.id}", source.permissions.isNotEmpty())
            }
    }
}
