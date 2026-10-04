/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.viewmodel

import nd.max.ui.design.MaxDataTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * **سرعة نبضة القياس** — العطب الذي أبلغ عنه المالك: «الرئيسية تستغرق دقيقة لإظهار القراءات»
 * و«فتح معلومات الجهاز يكتب لا قراءة وبعد دقيقة تعمل».
 *
 * وسببُه **مقيس بالأسطر لا مُتخيَّل**، وهذا ما تحرسه البوّابة:
 *
 * 1. **`dumpsys thermalservice` كان يُنفَّذ في كل نبضة (كل ثانيتين)** — وهو من أبطأ أوامر
 *    المنصّة — ثم يُطرح ناتجه في الحالة الشائعة (المناطق الحرارية مقروءة من `sysfs`).
 *    ⇒ لا يُسأل إلا حين **تنقص فئة فعلًا**، ومع كاش بنافذة صلاحية ونداء واحد متزامن.
 * 2. **والنبضة كانت سلسلةً متتابعة**: كل قارئ ينتظر الذي قبله، فالمجموع لا الأقصى؛ ثم لا
 *    يُنشر شيء حتى يكتمل آخرها. ⇒ صارت **مجموعتين متوازيتين**، والسريعة (إطار العمل وحده)
 *    **تُنشر أوّلًا** فتظهر الأرقام بدل انتظار الصدفة.
 * 3. **و«لا قراءة» كانت تُكتب قبل أول قراءة** — حكمٌ على مصدر لم يُسأل بعد ⇒ صارت حالة
 *    [`MaxDataTrust.Loading`] مفصولة، والنموذج يكتب طابع أول دورة (`readingsAtMs`).
 *
 * **وحدّ هذه البوّابة:** فحص نصّيّ على المصدر. وما يُقاس على جهاز (زمن النبضة فعلًا · عدد
 * رحلات الجذر في الثانية) **يحتاج جهازًا** ولا يُدّعى هنا — وهذه البوّابة تمنع **رجوع**
 * الأسباب المقيسة، لا تُثبت الزمن النهائي.
 */
class DashboardPulseLatencyTest {

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

    /** الكود دون تعليقاته: تُقاس البنية، لا صياغة الشرح (وفيها يرد اسم العطب بالكلام). */
    private fun read(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    private val viewModelSource = "ui/viewmodel/HomeDashboardViewModel.kt"
    private val thermalSource = "core/platform/ThermalUtil.kt"
    private val deviceInfoSource = "ui/subscreens/DeviceInfoScreen.kt"

    // ── (١) السؤال الثقيل لا يُطرح في الحالة الشائعة ─────────────────────────

    @Test
    fun `the dash is not called on every pulse, only when a category is missing`() {
        val viewModel = read(viewModelSource)
        assertTrue(
            "`dumpsys thermalservice` في كل نبضة قياس هو العطب المقيس نفسه",
            viewModel.contains(
                "if (cpu == 0 || gpu == 0 || skin == 0) {"
            ),
        )
        assertTrue(
            "والنداء داخل الحارس لا خارجه",
            Regex(
                "if \\(cpu == 0 \\|\\| gpu == 0 \\|\\| skin == 0\\) \\{\\s*" +
                    "val service = ThermalUtil\\.readThermalServiceTemperatures\\(\\)"
            ).containsMatchIn(viewModel),
        )
        assertEquals(
            "ولا يُسأل من مكان آخر في هذا الملف",
            1,
            Regex("ThermalUtil\\.readThermalServiceTemperatures\\(\\)").findAll(viewModel).count(),
        )
        assertTrue(
            "والمصدر غير الشاشة يأخذ الحارس نفسه",
            Regex(
                "if \\(cpu == 0 \\|\\| gpu == 0 \\|\\| skin == 0\\) \\{\\s*" +
                    "val service = ThermalUtil\\.readThermalServiceTemperatures\\(\\)"
            ).containsMatchIn(read("core/hardware/HardwareDataSource.kt")),
        )
    }

    @Test
    fun `the fallback itself is cached and single-flight`() {
        val thermal = read(thermalSource)
        assertTrue(
            "نافذة صلاحية للاحتياط الثقيل: ما لا يتغيّر كل ثانيتين لا يُقاس كل ثانيتين",
            thermal.contains("SERVICE_TTL_MS"),
        )
        assertTrue(
            "ونداء واحد متزامن، فلا تنطلق رحلتان متوازيتان لقيمة واحدة",
            thermal.contains("readThermalServiceTemperatures(): IntArray = synchronized(serviceLock)"),
        )
        assertFalse(
            "والقفل ليس قفل الكائن كلّه (كان سيوقف قراءة المناطق خلف الداش)",
            thermal.contains("@Synchronized"),
        )
    }

    // ── (٢) النبضة متوازية وتنشر السريع أوّلًا ────────────────────────────────

    @Test
    fun `a pulse publishes what the framework answers before the root reads`() {
        val viewModel = read(viewModelSource)
        assertTrue("توازٍ صريح لا خيط خلفيّ آخر", viewModel.contains("coroutineScope {"))
        assertTrue("ومهامّه منفّذة معًا", viewModel.contains("async {"))

        val publishFast = viewModel.indexOf("publishFastReadings(fast)")
        val slowReads = viewModel.indexOf("async { readThermal() }")
        val fullPublish = viewModel.lastIndexOf("readingsAtMs = System.currentTimeMillis()")
        assertTrue("النشر السريع أولًا في الملف", publishFast in 1 until slowReads)
        assertTrue("والنشر الكامل بعده", fullPublish > slowReads)
        assertTrue(
            "والمجموعة السريعة تُبنى في نطاق واحد لا تُوزّع على نطاقات",
            viewModel.contains("val fast = coroutineScope {"),
        )
    }

    @Test
    fun `the pulse interval is one named number, not a literal in the loop`() {
        val viewModel = read(viewModelSource)
        assertTrue(
            "إيقاع واحد معلَن بدل رقم مدفون",
            viewModel.contains("private const val POLL_INTERVAL_MS"),
        )
        assertTrue(viewModel.contains("delay(POLL_INTERVAL_MS)"))
        assertFalse(
            "ولا رقم مكتوب بيد في حلقة القياس",
            Regex("delay\\(\\s*\\d").containsMatchIn(viewModel),
        )
    }

    // ── (٣) الانتظار ليس «لا قراءة» ──────────────────────────────────────────

    @Test
    fun `waiting on the first cycle is its own state, never a verdict about the device`() {
        // الحالة نفسها في نظام التصميم: اسم وصفي، ووسم يُميَّز به عن العطب.
        assertTrue(
            "حالة الانتظار جزء من مفردات الثقة لا استثناء في شاشة",
            MaxDataTrust.values().any { it.name == "Loading" },
        )
        val metric = read("ui/design/MaxMetric.kt")
        assertTrue("ولها وسمها", metric.contains("MaxDataTrust.Loading -> R.string.max_trust_loading"))
        assertTrue(
            "ولونها هادئ لا إنذار",
            read("ui/design/MaxTokens.kt")
                .contains("MaxDataTrust.Loading -> MaxTrustVisual(MaxTone.Neutral"),
        )
    }

    @Test
    fun `the Device Info screen says reading, not unreadable, until the first cycle lands`() {
        val screen = read(deviceInfoSource)
        assertTrue(
            "الحالة تُمرَّر إلى المقياس",
            screen.contains("val pendingReadings = dashboard.readingsAtMs == 0L"),
        )
        assertTrue(
            "و«غير مقروء» تُقلب إلى «يُقرأ…» في الانتظار وحده",
            screen.contains("if (pending) MaxDataTrust.Loading else MaxDataTrust.Unreadable"),
        )
        assertTrue(
            "والمعلومة من النموذج لا من عدّاد في الشاشة",
            read(viewModelSource).contains("val readingsAtMs: Long = 0L"),
        )
    }
}
