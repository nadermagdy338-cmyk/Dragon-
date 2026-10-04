/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس **مسارات فتح الشاشات**: القراءة الثقيلة لا تقع في التركيب، ورحلات الصدفة على مسار
 * الأوّل **معدودة لا موصوفة**.
 *
 * **ولماذا اختبار لا مراجعة بالعين:** هذه العيوب كلّها **كمّية** — عدد رحلات الصدفة، وهل
 * القراءة داخل `remember` أم في `LaunchedEffect`. ومراجعة سطر لا تكشف عودةً صامتة إلى
 * العادة القديمة (`Shell.cmd("test -e …")`، أو نسخ ملفّ أصول داخل التركيب)، ولا يراها
 * المُصرّف لأنها **تُصرَّف وهي صحيحة** — تُصرَّف بطيئة.
 *
 * والنصوص تُقرأ **بلا تعليقاتها** (`read`): تُقاس البنية لا صياغة الشرح — وهو شرط دقيق هنا،
 * لأن التعليقات الجديدة **تذكر الأوامر القديمة بأسمائها** لتشرح ما أُزيل.
 */
class ScreenOpenLatencyTest {
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

    /** الكود دون تعليقاته: تُقاس البنية، ولا تُقاس صياغة التعليق. */
    private fun read(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    /**
     * جسم دالّة بالعدّ المتوازن للأقواس.
     *
     * **ولماذا لا الحكم على الملفّ كلّه:** الملفّ يحمل **مسارين** — فتحًا وإجراءً — والثاني
     * شرعته الدنيا مختلفة (تغيير المستخدم نفسه لا يقع كل ثانية). فحكمٌ على الملفّ كلّه يقيس
     * المزيج ويضطرّنا إلى التنازل عن الحدّ ليمرّ؛ وفصلُ الجسم يجعل الحدّ **قاطعًا**: صفر صدفة
     * في مسار الفتح، بلا مساس بمسار الإجراء.
     */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val from = source.indexOf('{', start)
        if (from < 0) return ""
        var depth = 0
        for (i in from until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(from, i + 1)
                }
            }
        }
        return source.substring(from)
    }

    // ── (١) الرئيسية: لا قراءة داخل التركيب ──────────────────────────────────

    @Test
    fun `the home screen never reads the device name during composition`() {
        val home = read("ui/mainscreens/HomeScreen.kt")
        assertFalse(
            "`remember { getRealDeviceName(context) }` يُنفَّذ خلال التركيب على الخيط الرئيسي، " +
                "والدالّة تنسخ `devices.db` (٤٫٢ ميغابايت) وتفتح SQLite وتستعلم بـ`LIKE` — " +
                "فأوّل إطار في الرئيسية ينتظر قرصًا واستعلامًا.",
            Regex("remember\\([^)]*\\)\\s*\\{\\s*getRealDeviceName\\(").containsMatchIn(home),
        )
        assertTrue(
            "الاسم الذي لا يكلّف قراءة (`Build`) يُعرض أوّلًا.",
            home.contains("fallbackDeviceName()"),
        )
        assertTrue(
            "والاسم التسويقي يصل بعدها على خيط خلفيّ.",
            home.contains("withContext(Dispatchers.IO)") && home.contains("getRealDeviceName(context)"),
        )
    }

    @Test
    fun `the device name read is cached, copied atomically, and self-healing`() {
        val util = read("ui/util/DeviceNameUtil.kt")
        assertTrue(
            "اسم الجهاز يُقرأ مرّة في عمر العملية: الرئيسية و«معلومات الجهاز» كانا يقرآن الجدول نفسه.",
            util.contains("AtomicReference"),
        )
        assertTrue(
            "والنسخة تُكتب على مسار مؤقّت ثم تُنقل: مسار نهائيًا يوجد دائمًا بملفّ تامّ.",
            util.contains(".tmp") && util.contains("renameTo"),
        )
        assertTrue(
            "وقاعدة لا تُفتح تُحذف ليُعاد نسخ الأصل — بدل اسم ناقص ما دام التطبيق مثبّتًا.",
            util.contains("dbFile.delete()"),
        )
    }

    // ── (٢) شاشات أخرى: رحلات الصدفة على مسار الأوّل ────────────────────────

    @Test
    fun `charging opens without a single shell round trip`() {
        val loadState = bodyOf(read("ui/viewmodel/ChargingViewModel.kt"), "fun loadState(")
        assertTrue("لم يُقرأ جسم `loadState` — الاختبار لا يقيس شيئًا", loadState.isNotEmpty())
        assertFalse(
            "مسارات الفتح لا تشغّل صدفة إطلاقًا: كانت ثلاثًا (`test -e` ×٢ و`settings get`).",
            loadState.contains("Shell.cmd("),
        )
        assertFalse(
            "سؤال وجود عقدة (`test -e …`) تجيبه الطبقة الموحّدة، لا الصدفة.",
            loadState.contains("test -e "),
        )
        assertTrue(loadState.contains("RootFileAccess.exists(limitPath)"))
        assertTrue(loadState.contains("RootFileAccess.firstExisting(SIC_MODE_CANDIDATES)"))
        assertTrue(
            "وموفّر البطارية يُقرأ من المفتاح نفسه عبر الـContentResolver، لا بصدفة `settings get`.",
            loadState.contains("LOW_POWER_KEY"),
        )
        assertTrue(
            "والشاشة تمرّر السياق المطلوب للقراءة بلا جذر.",
            read("ui/subscreens/ChargingScreen.kt").contains("viewModel.loadState(context)"),
        )
    }

    @Test
    fun `resolution batches its window-manager reads into one round trip`() {
        val vm = read("ui/viewmodel/ResolutionViewModel.kt")
        assertFalse(
            "`wm size` و`wm density` و`dumpsys display` كانت ثلاث رحلات متتالية على الصدفة الواحدة.",
            vm.contains("Shell.cmd(\"wm size\")") || vm.contains("Shell.cmd(\"wm density\")"),
        )
        assertTrue(
            "والأوامر الثلاثة في تنفيذ واحد بعلامتين تفصلان مخرجاتها.",
            vm.contains("MARK_FPS") && vm.contains("echo \$MARK_DENSITY"),
        )
    }

    @Test
    fun `the doze screen reads one snapshot instead of four round trips`() {
        val vm = read("ui/viewmodel/DozeModeViewModel.kt")
        assertTrue(vm.contains("DozeModeUtil.readSnapshot()"))
        assertFalse(
            "`isSupported` كانت رحلة `get deep`، ثم `get deep` مرّة ثانية للحالة — واللقطة تجمعهما.",
            vm.contains("DozeModeUtil.isSupported()"),
        )
        val util = read("ui/util/DozeModeUtil.kt")
        assertTrue(
            "والتفريغ الكامل للاحتياطي لا يُسأل إلا حين يعود `get deep` فارغًا.",
            util.contains("MARK_LIGHT") && util.contains("MARK_BUCKET") && util.contains("fullDumpRaw()"),
        )
        assertTrue(
            "والحالة الخفيفة تُمرَّر مع اللقطة فلا تُقرأ ثانيةً.",
            util.contains("currentLight: DozeState = getLightDozeState()"),
        )
    }

    @Test
    fun `the cpu count is read in-process before any shell fallback`() {
        val topology = read("ui/util/CpuTopologyUtil.kt")
        assertFalse(
            "`cat /sys/devices/system/cpu/possible` عبر الجذر رحلة كاملة لسؤال لا يحتاج جذرًا.",
            topology.contains("cat /sys/devices/system/cpu/possible"),
        )
        assertTrue(topology.contains("RootFileAccess.read(CPU_POSSIBLE_PATH)"))
        assertTrue(
            "واحتياطي العدّ مجلّدات داخل العملية، والصدفة تبقى آخر طبقات الحقيقة.",
            topology.contains("CPU_DIRECTORY") && topology.contains("ls -d \$CPU_ROOT/cpu[0-9]*"),
        )
    }
}
