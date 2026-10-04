/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * **الورقة المنبثقة تعلو الكيبورد** — طلب المالك: «يجب أن يظهر النافذة المنبثقة في زر البحث فوق
 * الكيبورد».
 *
 * وكان العطب في سطرين لا في الشكل:
 *
 * 1. **التطبيق `edge-to-edge` بلا `windowSoftInputMode`** في البيان: فلا تُبلَّغ النافذة بحجم
 *    لوحة المفاتيح على كل الأجهزة، فتبقى الورقة تحتها.
 * 2. **والحاشية السفلى في القشرة كانت `navigationBars + 20dp` وحدها**، وتُطبَّق كـ`Spacer` في
 *    آخر العمود — أي أنّ كِبَر الحاشية **يكبر به العمود** ثم يُقتطع من أعلاه (الورقة ملتصقة
 *    بالقاع)، فيُدفع العنوان وحقل البحث خارج الشاشة بدل أن تُقلَّص القائمة.
 *
 * **وحدّ هذه البوّابة:** فحص نصّي على المصدر والبيان. وما تراه العين (هل الورقة فعلًا فوق اللوحة
 * على جهازك، وبارتفاع أيّ لوحة) **يحتاج جهازًا** ولا يُدّعى هنا.
 */
class BottomSheetKeyboardTest {

    private lateinit var sourceRoot: File

    @Before
    fun locateSourceRoot() {
        val found = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).firstOrNull { it.isDirectory }
        assumeTrue("Cannot locate nd.max sources; guard not evaluated", found != null)
        sourceRoot = found!!
    }

    private fun read(path: String): String = File(sourceRoot, path).readText()

    /** الكود دون تعليقاته: تُقاس البنية لا صياغة الشرح. */
    private fun readCode(path: String): String = read(path)
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    private val sheetSource = "ui/component/BottomSheetComponent.kt"

    @Test
    fun `the sheet's floor is the larger of the keyboard and the navigation bar`() {
        val sheet = readCode(sheetSource)
        assertTrue(
            "ارتفاع لوحة المفاتيح يُقرأ من النافذة — وهذا نصّ الطلب",
            sheet.contains("WindowInsets.ime.asPaddingValues()"),
        )
        assertTrue(
            "والأرضية أكبر الاثنين لا مجموعهما (والجمع يترك فراغًا ميّتًا فوق اللوحة)",
            sheet.contains("maxOf(navBarBottom, imeBottom)"),
        )
        assertFalse(
            "ولا تبقى الحاشية القديمة وحدها بلا قراءة اللوحة",
            sheet.contains(
                "val extraBottomPadding = WindowInsets.navigationBars.asPaddingValues()" +
                    ".calculateBottomPadding() + 20.dp"
            ),
        )
    }

    @Test
    fun `the clearance shrinks the content instead of pushing it off the top`() {
        val sheet = readCode(sheetSource)
        assertTrue(
            "الحاشية تُنقص ارتفاع المحتوى",
            sheet.contains("Modifier.padding(bottom = extraBottomPadding)"),
        )
        assertFalse(
            "لا `Spacer` في آخر العمود: كان يكبر بالحاشية فيُقتطع أعلى الورقة",
            sheet.contains("Spacer(modifier = Modifier.height(extraBottomPadding))"),
        )
    }

    @Test
    fun `the window reports keyboard size to the app`() {
        // ومسار البيان من جذر المصادر: `nd/max` ⇐ `nd` ⇐ `java` ⇐ **`main`** ⇒ البيان بجانبها.
        // (وأول صياغة كتبت `../../` فلم يُعثر عليه، فمرّ الاختبار **متجاوزًا** — أي أداة تمرّ
        // على كل شيء لا تُثبت شيئًا؛ وهذا ما أوقفه العدّ.)
        val manifest = File(sourceRoot, "../../../AndroidManifest.xml").canonicalFile
        assertTrue(
            "بيان التطبيق في مكانه المتوقّع — وإلا فهذا الفحص لا يقيس شيئًا: $manifest",
            manifest.isFile,
        )
        assumeTrue("Cannot locate AndroidManifest.xml; guard not evaluated", manifest.isFile)
        val text = manifest.readText()

        val activity = Regex("""<activity[^>]*android:name="\.MainActivity"[\s\S]*?>""")
            .find(text)?.value
        assertTrue("تعذّر العثور على نشاط التطبيق في البيان", activity != null)
        assertEquals(
            "بلا هذا الإعلان لا تصل `WindowInsets.ime` على كثير من الأجهزة",
            1,
            Regex("""android:windowSoftInputMode="adjustResize"""").findAll(activity!!).count(),
        )
    }
}
