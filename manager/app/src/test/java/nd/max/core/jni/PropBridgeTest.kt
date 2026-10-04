/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حارس جسر الخصائص — طبقتان، ولكلٍّ حدّها المعلن:
 *
 * ١) **دلالة البيئة على JVM:** مكتبة أندرويد الأصلية غائبة هنا، فيجب أن يقول الجسر
 *    «اسأل غيري» (`null`) — لا `""` ولا قيمة مخترعة. والدالّة النقيّة
 *    [PropBridge.shippableName] هي ما يمكن قياسه فعلًا بلا مكتبة.
 * ٢) **حارس مصدر على مواضع التحويل:** لا يعود `getprop` عبر صدفة إلى المسارات الساخنة،
 *    والترتيب (أصلي ← احتياطي) محفوظ — فحارس الترتيب هو ما يمنع «تحويلًا» يعكس الاتجاه.
 *
 * و**ما لا تقيسه هذه الطبقة:** هل يُصدَّر `Java_nd_max_core_jni_PropBridge_*` فعلًا — يقيسه
 * `tools/jni_symbols.py` (بوابة كاملة على المصدر والثنائيات معًا)، ويُشغَّل في CI.
 */
class PropBridgeTest {

    private val repoRoot: File by lazy {
        listOf(File("."), File(".."), File("../.."))
            .firstOrNull { File(it, "manager/src/main/rust/src/lib.rs").isFile }
            ?: File(".")
    }

    /**
     * طبقة المصدر تحتاج الشجرة أمامها. وإن لم تُوجد **يُعلن التخطّي** بدل فشل وهمي —
     * وبنفس صنف `assumeTrue` الذي يستعمله حارس `RustBridgeSymbolTest`.
     */
    @Before
    fun locateRepoRoot() {
        assumeTrue(
            "Cannot locate the repository root; the source guard is not evaluated",
            File(repoRoot, "manager/src/main/rust/src/lib.rs").isFile,
        )
    }

    @Test
    fun `without the native library every read asks someone else`() {
        // على جهاز أندرويد تُحمَّل المكتبة فيصير الفرع الآخر هو المقيس — وهذا مُعلَن لا مَسكوت.
        if (PropBridge.nativeAvailable) return
        assertNull(PropBridge.get("ro.build.version.sdk"))
        assertNull(PropBridge.getAll(listOf("ro.build.version.sdk", "ro.product.model")))
        assertNull(PropBridge.get("persist.sys.maxmanager.custom_touch_boost"))
    }

    @Test
    fun `empty request is an empty answer not a refusal`() {
        // قائمة فارغة = لا سؤال، فالجواب قائمة فارغة — وليس «اسأل غيري» التي تدفع الاحتياطي.
        assertEquals(emptyList<String>(), PropBridge.getAll(emptyList()))
    }

    @Test
    fun `names that cannot be shipped are refused before any call`() {
        assertFalse(PropBridge.shippableName(""))
        assertFalse(PropBridge.shippableName("   "))
        assertFalse(PropBridge.shippableName("ro.a\nb"))
        assertTrue(PropBridge.shippableName("ro.build.version.sdk"))
        assertTrue(PropBridge.shippableName("persist.sys.maxmanager.custom_touch_boost"))
    }

    @Test
    fun `the native reader is tried before the reflection fallback`() {
        val text = File(repoRoot, "manager/app/src/main/java/nd/max/core/platform/PropertyUtil.kt").readText()
        val native = text.indexOf("PropBridge.get(key)")
        val reflection = text.indexOf("getMethod.invoke(null, key, def)")
        assertTrue("PropBridge.get must be present", native >= 0)
        assertTrue("reflection fallback must be preserved", reflection >= 0)
        assertTrue("the native read must come first", native < reflection)
    }

    @Test
    fun `no shell getprop is left on the converted hot paths`() {
        val converted = listOf(
            "manager/app/src/main/java/nd/max/AppMonitor.kt",
            "manager/app/src/main/java/nd/max/ui/viewmodel/HomeViewmodel.kt",
            "manager/app/src/main/java/nd/max/ui/mainscreens/GetStartedScreen.kt",
            "manager/app/src/main/java/nd/max/core/platform/PropertyUtil.kt",
        )
        for (rel in converted) {
            val text = File(repoRoot, rel).readText()
            assertFalse("$rel must not shell out for a property read", text.contains("getprop "))
        }
    }

    @Test
    fun `the refresh-rate probe keeps the native batch before the shell fallback`() {
        val rel = "manager/app/src/main/java/nd/max/PerAppRefreshRateController.kt"
        val text = File(repoRoot, rel).readText()
        val batch = text.indexOf("PropBridge.getAll(props)")
        // النصّ المطلوب حرفيًّا: shellRead("getprop '<key>'") — و`$` يجب هربه وإلا صار استيفاءً في Kotlin.
        val fallback = text.indexOf("shellRead(\"getprop '\$it'\")")
        assertTrue("the batch must be tried first", batch >= 0)
        assertTrue("the shell fallback must be declared", fallback > batch)
    }

    @Test
    fun `the dashboard reads cores natively before falling back to the shell script`() {
        val rel = "manager/app/src/main/java/nd/max/ui/viewmodel/HomeDashboardViewModel.kt"
        val text = File(repoRoot, rel).readText()
        val native = text.indexOf("readCoreNodes(topology) ?: shellCoreNodes(topology)")
        assertTrue("the native batch must be the first path", native >= 0)
        assertTrue(
            "the native reader must use the probe batch",
            text.contains("ProbeBridge.readMany(coreNodePaths(topology))"),
        )
        assertTrue(
            "the shell fallback must stay declared",
            text.contains("private fun shellCoreNodes(topology: List<CpuCoreState>)"),
        )
    }
}
