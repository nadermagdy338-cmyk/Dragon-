/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مقياس `ForegroundAppResolver` — **بلا جهاز ولا أندرويد**.
 *
 * وكل حالة هنا ليست تخييليّة: أوّلها هو العطب المقيس في حزمة سجلّات جهاز TECNO LH8n
 * (Android 14 · 2026-10-01) حيث صارت قيمة عشريّة **اسمًا لحزمة في المقدّمة**، فانقضت إدارة
 * per-app كلها بلا رسالة خطأ واحدة — لأن الخادم يقارن الاسم حرفيًّا بمفاتيح قائمة التطبيقات.
 */
class ForegroundAppResolverTest {

    // ── الصيغة: اسم الحزمة لا يُقبل بالشبه ──────────────────────────────

    @Test
    fun `a dotted number is not a package name`() {
        // **هذا هو العطب نفسه:** «0.85» فيها نقطة، فكانت تُقبل قبل اليوم.
        assertFalse(ForegroundAppResolver.isPackageName("0.85"))
        assertFalse(ForegroundAppResolver.isPackageName("1.2.3"))
        assertFalse(ForegroundAppResolver.isPackageName("0.5"))
        assertFalse(ForegroundAppResolver.isPackageName("9.9.9.9"))
    }

    @Test
    fun `every label starts with a letter, as android requires`() {
        assertTrue(ForegroundAppResolver.isPackageName("com.franco.kernel"))
        assertTrue(ForegroundAppResolver.isPackageName("com.mobile.legends"))
        assertTrue(ForegroundAppResolver.isPackageName("nd.max"))
        assertTrue(ForegroundAppResolver.isPackageName("com.example.app_2"))
        // ومقطع يبدأ برقم ليس اسمًا صالحًا في أندرويد، وإن كان فيه حرف قبله.
        assertFalse(ForegroundAppResolver.isPackageName("com.2app"))
        assertFalse(ForegroundAppResolver.isPackageName("com..example"))
        assertFalse(ForegroundAppResolver.isPackageName(".com.example"))
        assertFalse(ForegroundAppResolver.isPackageName("com.example."))
    }

    @Test
    fun `a single label, an empty string and a long string are all rejected`() {
        assertFalse(ForegroundAppResolver.isPackageName("android"))
        assertFalse(ForegroundAppResolver.isPackageName(""))
        assertFalse(ForegroundAppResolver.isPackageName("   "))
        assertFalse(ForegroundAppResolver.isPackageName(null))
        assertFalse(ForegroundAppResolver.isPackageName("com." + "a".repeat(300) + ".b"))
        // والشرطة ممنوعة في اسم الحزمة نفسها، وإن كانت مقبولة في اسم الوحدة.
        assertFalse(ForegroundAppResolver.isPackageName("com.example-app"))
    }

    @Test
    fun `uppercase is judged, and reported back as it was written`() {
        assertTrue(ForegroundAppResolver.isPackageName("Com.Example"))
        // ولا تُصغَّر القيمة المُعادة: مفتاح قائمة التطبيقات يُقارن حرفيًّا.
        assertEquals("Com.Example.App", ForegroundAppResolver.extractPackageName("pkg=Com.Example.App"))
    }

    // ── الاستخراج من نصّ عتاد ───────────────────────────────────────────

    @Test
    fun `the junk from the device bundle extracts to nothing`() {
        // النصّ الذي أُنتج «0.85»: كائن/قيمة عشريّة، لا حزمة.
        assertNull(ForegroundAppResolver.extractPackageName("0.85"))
        assertNull(ForegroundAppResolver.extractPackageName("RunningTaskInfo{0.85}"))
        assertNull(ForegroundAppResolver.extractPackageName("value=0.85 scale=1.0"))
    }

    @Test
    fun `a real package is found inside a noisy string`() {
        assertEquals(
            "com.franco.kernel",
            ForegroundAppResolver.extractPackageName("u0 com.franco.kernel/.MainActivity t123"),
        )
        assertEquals(
            "com.mobile.legends",
            ForegroundAppResolver.extractPackageName("pkg=com.mobile.legends uid=10307"),
        )
        // ونظافة النصّ تُبقي المقاطع متّصلة: النقطة والشرطة تحتهما فراغ، فيُقرأ الاسم كاملًا.
        assertEquals(
            "com.example.app",
            ForegroundAppResolver.extractPackageName("com.example.app:remote/Activity"),
        )
    }

    @Test
    fun `a component shape wins over an earlier dotted action name`() {
        // شكل `Intent.toString()` الحقيقي: الفعل قبل المكوّن — و`android.intent.action.MAIN`
        // صالح الصيغة، فبلا أسبقيّة شكل المكوّن يُرسَل **الفعل** إلى الخادم فلا يطابق مفتاحًا.
        val intent = "Intent { act=android.intent.action.MAIN " +
            "cat=[android.intent.category.LAUNCHER] flg=0x10200000 " +
            "cmp=com.franco.kernel/.MainActivity }"
        assertEquals("com.franco.kernel", ForegroundAppResolver.extractPackageName(intent))
        assertEquals(
            "com.mobile.legends",
            ForegroundAppResolver.extractPackageName(
                "ActivityRecord{c91 u0 com.mobile.legends/com.epicgames.ue4.GameActivity t42}",
            ),
        )
    }

    @Test
    fun `an android intent or permission constant is never a package`() {
        // ومجال الثوابت يُتخطّى حين لا مكوّن في النصّ أصلًا.
        assertEquals(
            "com.mobile.legends",
            ForegroundAppResolver.extractPackageName(
                "action=android.intent.action.MAIN package=com.mobile.legends",
            ),
        )
        assertEquals(
            "com.example.app",
            ForegroundAppResolver.extractPackageName(
                "perm=android.permission.INTERNET target=com.example.app",
            ),
        )
        assertNull(
            ForegroundAppResolver.extractPackageName("action=android.intent.action.MAIN"),
        )
    }

    @Test
    fun `a slash that closes a single label is skipped, not returned`() {
        // `remote/` يسبق الشكل الصحيح ولا يصلح حزمةً، فيُتخطّى إلى المسار الأخير لا يُعاد.
        assertEquals(
            "com.example.app",
            ForegroundAppResolver.extractPackageName("com.example.app:remote/Activity"),
        )
        assertEquals(
            "com.x.y",
            ForegroundAppResolver.extractPackageName("/data/data/com.x.y/files"),
        )
    }

    @Test
    fun `an empty or textless input yields nothing`() {
        assertNull(ForegroundAppResolver.extractPackageName(null))
        assertNull(ForegroundAppResolver.extractPackageName(""))
        assertNull(ForegroundAppResolver.extractPackageName("no package here at all"))
    }

    // ── نصّ dumpsys: المصدر الذي يقوله النظام عن نفسه ────────────────────

    @Test
    fun `the resumed activity line names the package`() {
        val dumpsys = """
            ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
            Display #0 (activities from top to bottom):
              * Task{1a2b3c4 #123 type=standard A=10234:com.franco.kernel U=0 visible=true}
                mResumedActivity: ActivityRecord{8f3c2a1 u0 com.franco.kernel/.MainActivity t123}
                topResumedActivity=ActivityRecord{8f3c2a1 u0 com.franco.kernel/.MainActivity t123}
        """.trimIndent()
        assertEquals("com.franco.kernel", ForegroundAppResolver.parseResumedPackage(dumpsys))
    }

    @Test
    fun `a game sample parses from the task line even when the resumed line is absent`() {
        val dumpsys = """
            topResumedActivity=ActivityRecord{c91 u0 com.mobile.legends/com.epicgames.ue4.GameActivity t42}
        """.trimIndent()
        assertEquals("com.mobile.legends", ForegroundAppResolver.parseResumedPackage(dumpsys))
    }

    @Test
    fun `the window focus line is the last resort, and it names the package too`() {
        val dumpsys = """
            WINDOW MANAGER WINDOWS (dumpsys window windows)
              mCurrentFocus=Window{ab12 u0 com.tencent.ig/com.epicgames.ue4.GameActivity}
        """.trimIndent()
        assertEquals("com.tencent.ig", ForegroundAppResolver.parseResumedPackage(dumpsys))
    }

    @Test
    fun `a marker line with nothing usable does not stop the search`() {
        // أوّل سطر وسم يحمل قيمًا لا اسم فيها (وهو ما يقع في السجلّات الحقيقيّة عند إقلاع
        // الخدمة)، فيُتابع إلى السطر التالي بدل إعلان «لا تطبيق».
        val dumpsys = """
            mFocusedApp=null
            mResumedActivity: ActivityRecord{1 u0 nd.max/.MainActivity t9}
        """.trimIndent()
        assertEquals("nd.max", ForegroundAppResolver.parseResumedPackage(dumpsys))
    }

    @Test
    fun `a dumpsys with no marker, or with junk only, yields nothing`() {
        assertNull(ForegroundAppResolver.parseResumedPackage(null))
        assertNull(ForegroundAppResolver.parseResumedPackage(""))
        assertNull(ForegroundAppResolver.parseResumedPackage("Display #0\n  * Task{abc}\n"))
        assertNull(
            ForegroundAppResolver.parseResumedPackage("mResumedActivity: ActivityRecord{1 0.85 t1}"),
        )
    }

    @Test
    fun `the resolver never returns a value its own validator rejects`() {
        // حارس الذات: ما يخرج من أي دالّة هنا يقبله `isPackageName` — فلا يُعاد اسم «شبيه».
        val samples = listOf(
            "u0 com.x.y/.A t1",
            "pkg=0.85",
            "com.a.b/c",
            "0.5",
            "no dots",
            "com.example.app:remote/A",
        )
        samples.forEach { sample ->
            val extracted = ForegroundAppResolver.extractPackageName(sample)
            if (extracted != null) {
                assertTrue("استُخرج ما يرفضه المُتحقّق: $extracted من $sample", ForegroundAppResolver.isPackageName(extracted))
            }
        }
    }
}
