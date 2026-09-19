package nd.max.ui.subscreens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حارس لغوي لشاشتي التخزين والحرارة.
 *
 * شكوى المستخدم كانت: «غير متناسقة مع الشكل العام للتطبيق» — وهما كانتا فعلًا تُبنيان بلغة
 * لوحة البداية (`DashCardWrapper` + `LiveHeader` + `GlowLinearBar`) لا بلغة التطبيق، وتحملان
 * عناوين إنجليزية داخل شاشة عربية (`Internal Data` · `GB total` · `used · free`). وهذا كله
 * عطب لا يراه مُصرّف ولا اختبار وحدة، فالحرس هنا نصّي عن قصد — كما في حارس شاشة التطبيقات.
 */
class DetailScreensLanguageContractTest {

    private lateinit var storage: String
    private lateinit var thermal: String

    private fun source(name: String): String {
        val file = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).map { File(it, "ui/subscreens/$name") }.firstOrNull { it.isFile }
        checkNotNull(file) { "Cannot locate $name; source guard was not evaluated" }
        // بلا تعليقات: تعليق يشرح الحالة التي يمنعها الحارس ليس حالة.
        return file.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\\n]*"), "")
    }

    @Before
    fun readScreens() {
        storage = source("StorageDetailScreen.kt")
        thermal = source("ThermalDetailScreen.kt")
    }

    @Test
    fun `both screens are built from the shared page language`() {
        // `MaxGroup` بلا قوس: النداء بلامدا متأخّرة (`MaxGroup {`) لا يكتب قوسًا،
        // وحرس يبحث عن `MaxGroup(` يمرّ على الكود السليم فاشلًا.
        mapOf("storage" to storage, "thermal" to thermal).forEach { (name, text) ->
            listOf("MaxListScreen(", "MaxSection(", "MaxMetric", "MaxGroup").forEach { api ->
                assertTrue("$name must be built from $api", text.contains(api))
            }
        }
    }

    @Test
    fun `neither screen draws the dashboard language`() {
        val forbidden = listOf("DashCardWrapper", "GlowLinearBar", "LiveHeader", "DetailStatCard", "IconBadge")
        mapOf("storage" to storage, "thermal" to thermal).forEach { (name, text) ->
            forbidden.forEach { api ->
                assertFalse("$name must not use the home dashboard's $api", text.contains(api))
            }
        }
    }

    @Test
    fun `no user-visible English text is hardcoded inside a translated screen`() {
        // القاعدة: كل نصّ يراه المستخدم يأتي من `stringResource`. والفحص **داخل النصوص
        // الحرفية فقط**، ولذلك سببان قيستًا كلاهما بإسقاط هذا الاختبار على كود سليم:
        //
        // ١. `capacityTitle` و`MAX_VALUE_UNAVAILABLE` **معرّفان** لا نصّان (وقيمة الثاني
        //    `"—"`)، ففحص يمنع كلمة `capacity` أو `UNAVAILABLE` في أي مكان من الملف
        //    يتّهم كودًا سليمًا. وحرس يكذب مرّة يُعطَّل مرّة، وهذا أسوأ من غيابه.
        // ٢. و`\n` داخل الصنف الممنوع ليست زخرفة: بلاها يبدأ المحرّك من **قوس إغلاق**
        //    (`"…$LTR",`) فيمتدّ إلى أوّل قوس في سطر لاحق، فيلتقط `(total - free)`
        //    وهما معرّفان لا نصّ — لا نصّ إنجليزي يراه مستخدم.
        val hardcodedEnglish = Regex(
            "\"[^\"\n]*(?:\\b(?:used|free|total|capacity|Internal Data|Internal Storage|UNAVAILABLE)\\b)[^\"\n]*\""
        )
        listOf("storage" to storage, "thermal" to thermal).forEach { (name, text) ->
            hardcodedEnglish.find(text)?.let { match ->
                // `match.value` لا `$match`: `MatchResult.toString()` يطبع عنوان كائن
                // (`kotlin.text.MatcherMatchResult@32ee6fee`) فلا يقول الحرس ماذا رأى.
                assertFalse(
                    "$name hardcodes English text (${match.value}) in a translated screen; it belongs in strings.xml",
                    true,
                )
            }
        }
    }

    @Test
    fun `the thermal screen reads battery heat from the home screen source`() {
        // العطب الذي أُصلح: التصنيف كان يُقارن بنصّ **مُعرَّب** («البطارية») بينما النواة
        // تعلن `battery` — فكانت القراءة صفرًا دائمًا. والمقارنة بنصّ معرَّب ممنوعة هنا،
        // والمصدر يجب أن يكون نفس مصدر الشاشة الرئيسية.
        assertFalse(
            "Zone categories must never be compared against a translated string",
            Regex("category\\s*\\.equals\\s*\\(\\s*stringResource").containsMatchIn(thermal),
        )
        assertTrue(
            "Battery heat comes from the same reader the home screen uses",
            thermal.contains("ThermalUtil.readBatteryTemperatureC"),
        )
    }
}
