/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس التظليل المشترك: يقرؤون الكود لا اللقطة. ما يضمنونه أن الصفوف القابلة للتمرير تمتدّ إلى حافة
 * الشاشة وتُظلّل طرفيها بالمكوّن المشترك، فلا يعود قصّ عند هامش الصفحة بخطّ مستقيم، ولا يعود تظليل
 * محلّيّ قديم يُخفي أوّل عنصر وهو في مكانه.
 *
 * **حدّها المُعلَن:** الشكل الذي يُرى على الشاشة يحتاج جهازًا؛ هذا يحرس الربط والبنية فقط.
 */
class MaxEdgeFadeContractTest {

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

    private fun read(relative: String): String = File(sourceRoot, relative).readText()

    @Test
    fun `the shared primitives exist and fade with the destination blend mode`() {
        val shared = read("ui/design/MaxEdgeFade.kt")
        assertTrue("امتداد الحافة غائب", shared.contains("fun Modifier.maxBleed("))
        assertTrue("التظليل المشترك غائب", shared.contains("fun Modifier.maxEdgeFade("))
        assertTrue("قناع الشفافية غائب", shared.contains("BlendMode.DstIn"))
        assertTrue("طبقة الإخراج غائبة", shared.contains("CompositingStrategy.Offscreen"))
        assertTrue("الصفّ المشترك غائب", shared.contains("fun MaxScrollRow("))
    }

    @Test
    fun `the tab strip bleeds to the screen edge and rests its first tab on the page gutter`() {
        val strip = read("ui/design/MaxTabStrip.kt")
        assertTrue("الشريط لا يمتدّ إلى الحافة", strip.contains("maxBleed()"))
        assertTrue("الشريط لا يُظلّل طرفيه", strip.contains("maxEdgeFade()"))
        assertTrue("المقطع الأول لا يرتاح عند الهامش", strip.contains("PaddingValues(horizontal = MaxSpace.gutter"))
    }

    @Test
    fun `the theme swatches use the shared fade and no longer carry a private one`() {
        val theme = read("ui/subscreens/CustomThemeScreen.kt")
        assertTrue("السوابح لا تمتدّ إلى الحافة", theme.contains(".maxBleed()"))
        assertTrue("السوابح لا تستعمل التظليل المشترك", theme.contains(".maxEdgeFade()"))
        assertTrue("الصفّ الملوّن لا يأخذ بداية الامتداد من موضعه", theme.contains("maxBleed(start = swatchStartBleed"))
        assertFalse("الشفافية المؤقتة القديمة عادت", theme.contains("alpha = 0.99f"))
        assertFalse("القناع المحلّي القديم عاد", theme.contains("0.06f to Color.Black"))
    }

    @Test
    fun `the page-level chip and filter rows use the shared scroll row`() {
        listOf(
            "ui/mainscreens/ApplistScreen.kt",
            "ui/subscreens/LogsViewerScreen.kt",
            "ui/subscreens/LogsViewerSections.kt",
            "ui/component/FileDrawerContent.kt",
        ).forEach { file ->
            assertTrue("$file لا يستعمل الصفّ المشترك", read(file).contains("MaxScrollRow("))
        }
    }

    @Test
    fun `the debloat tabs bleed and fade like every other tab strip`() {
        val debloat = read("ui/subscreens/DebloatFreezeScreen.kt")
        assertTrue("تبويبات الإزالة لا تمتدّ إلى الحافة", debloat.contains("maxBleed()"))
        assertTrue("تبويبات الإزالة لا تُظلّل طرفيها", debloat.contains("maxEdgeFade()"))
    }
}
