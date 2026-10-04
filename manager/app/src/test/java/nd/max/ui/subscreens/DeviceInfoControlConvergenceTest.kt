/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens

import nd.max.ui.mainscreens.controlLayoutModel
import nd.max.ui.navigation.MaxDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **قسمٌ في `Device Info` يفتح الشاشة نفسها التي تعرضها شاشة التحكّم لموضوعه — لا شاشة ثانية.**
 *
 * هذا هو السؤال الذي وُلد منه الملفّ، بنصّ المالك: «هل أضفت اختصارًا للأقسام في Device Info لنفس
 * الشاشات التي تجدها في شاشة التحكّم التي تتقارب معاها؟». والجواب لا يُقال بالعين: شاشة التحكّم
 * تبني صفوفها من **مصدرين حقيقيّين** — `parent` في السجلّ (`maxHubRows`) وسجلّ الأدوار
 * (`maxDestinationRole`) — وشاشة الجهاز تبني أبوابها من خريطة ثانية (`maxDeviceInfoSource`).
 * فمصدران لسؤال واحد **يفترقان يومًا**: يُنقل صفٌّ في التحكّم أو يُضاف مجال، فيبقى القسم في شاشة
 * الجهاز يشير إلى شاشة **لم تعد** هي التي تُعرض هناك — والبناء أخضر والضغط يفتح شيئًا آخر.
 *
 * وهذا الملفّ يقفل ذلك الافتراق في ثلاث جهات:
 *
 * 1. **كل قسم يفتح شاشة تعرضها شاشة التحكّم** — تُقرأ من `controlLayoutModel()` نفسها (النموذج
 *    الواحد الذي يقرؤه التخطيطان المضغوط والموسّع)، فلا نسخة ثانية من القائمة هنا.
 * 2. **والاستثناء أربعة أقسام لا خامس** — النظرة العامة والنظام والمستشعرات **والكاميرا**
 *    بيتها `Diagnostics`، وهي شاشة **الإعدادات** لا التحكّم (لا مجال لأيٍّ منها هناك). فالاختبار
 *    يفرض أن يكون الاستثناء **مُعلَنًا هنا** لا مصادفةً في الخريطة. والرابعة (الكاميرا) أُضيفت
 *    في جولة «معلومات أكثر من الصور» بلا شاشة تحكّم مالكة — وهو نصّ الإعلان لا صدقة.
 * 3. **وكل مجال تحكّم يفتحه قسم** — إلا **الاستجابة**، وهي مُعلَنة بالاسم في الاختبار الأخير لا
 *    مُغفَلة: لا قسم «استجابة» في النموذج (الأقسام الاثنا عشر لا تحمله)، وأي قسم لها يستلزم بنية
 *    بيانات جديدة (لمسة/إطارات) لا نقل صفّ.
 *
 *    **والصوت خرج من هذه الفجوة في `AS-01` لا بإسكاتها:** كان في جولة `AU-01` مجالًا بلا صفوف
 *    ولا قسم، فأُعلنت فجوته هنا صراحةً. ثمّ صار له صفّ (سطح التحكّم `AudioStudio`) وقسمٌ في
 *    «معلومات الجهاز» (`DeviceInfoSection.Audio`) — فعاد الاختبار يقول «كل مجال له قسم إلا
 *    الاستجابة» **لأنّها صارت كذلك فعلًا**، لا لأنّ السطر نُزع.
 *
 * **وحدّه المعلن:** يقيس **الهويّة** لا **الترتيب**: أن الشاشة التي يفتحها القسم هي التي تعرضها
 * شاشة التحكّم، لا أنّها الصفّ الأول في مجالها. والترتيب داخل المجال اختيار تحريريّ (‏`Dex2oat`
 * قبل `StorageDetail` لأن الأوّل أداة والثاني شاشة بيانات)، ومقصودٌ أن يكون الباب إلى **شاشة
 * البيانات** لا إلى الأداة.
 */
class DeviceInfoControlConvergenceTest {

    /**
     * الأقسام التي لا مجال لها في شاشة التحكّم — مُعلَنة بأسمائها.
     *
     * وبيناتها ليست غائبة: تقرير الجهاز وجرد المستشعرات والأطلس كلّها في `Diagnostics`، وهي شاشة
     * إعدادات (‏`parent = Settings`) — فليست في شاشة التحكّم ولا يُطلب لها أن تكون.
     */
    private val sectionsWithoutControlDomain = setOf(
        DeviceInfoSection.Overview,
        DeviceInfoSection.System,
        DeviceInfoSection.Sensors,
        // والكاميرا كذلك: لا مجال كاميرا في شاشة التحكّم، وبُيتها `Diagnostics` كبقية الجرد.
        DeviceInfoSection.Camera,
    )

    /** كل شاشة تعرضها شاشة التحكّم: صفوف محاورها في التخطيطين — من النموذج الواحد لا من نسخة هنا. */
    private fun screensShownOnControl(): Set<String> =
        controlLayoutModel()
            .flatMap { band -> band.hubs }
            .flatMap { hub -> hub.features }
            .map { it.destination.route }
            .toSet()

    private fun doorOf(section: DeviceInfoSection): MaxDestination = maxDeviceInfoSource(section)

    /** (١) لا قسم يشير إلى شاشة لا تعرضها شاشة التحكّم — إلا الثلاثة المُعلنة. */
    @Test
    fun `كل قسم يفتح شاشة تعرضها شاشة التحكّم إلا الثلاثة المُعلنة`() {
        val shown = screensShownOnControl()
        assertTrue("قائمة شاشة التحكّم فارغة — الفحص نفسه معطوب", shown.size >= 8)

        val outside = DeviceInfoSection.entries
            .filterNot { it in sectionsWithoutControlDomain }
            .filterNot { doorOf(it).route in shown }
            .map { "${it.name} ⇒ ${doorOf(it).route}" }

        assertTrue(
            "أقسام تفتح شاشة لا تعرضها شاشة التحكّم لموضوعها: $outside",
            outside.isEmpty(),
        )
    }

    /** (٢) والاستثناء **هو** الأربعة بالضبط — لا قسم خامس سقط من التساوي بصمت. */
    @Test
    fun `والاستثناء أربعة أقسام لا خامس`() {
        val shown = screensShownOnControl()
        val withoutDomain = DeviceInfoSection.entries
            .filterNot { doorOf(it).route in shown }
            .toSet()

        assertEquals(
            "الاستثناء تغيّر: أخفى قسمًا صار بلا مجال في التحكّم، أو أبقى قسمًا له مجال",
            sectionsWithoutControlDomain,
            withoutDomain,
        )
    }

    /**
     * (٣) والأقسام التسعة (بعد `AS-01`) تفتح تسع **شاشات مختلفة** — لا شاشتان لقسمين في موضوعين.
     *
     * والعدد ارتفع من ٨ إلى ٩ لأن قسم الصوت دخل الفحص **بشاشة تحكّم مالكة** (`AudioStudio`) لا
     * ببابٍ إلى `Diagnostics`؛ ولو كان بابُه `Diagnostics` لظهر الاختلاف في اختبار (٢) فورًا، لأن
     * الاستثناء سيزيد قسمًا واحدًا.
     */
    @Test
    fun `تسعة أقسام تفتح تسع شاشات مختلفة`() {
        val targets = DeviceInfoSection.entries
            .filterNot { it in sectionsWithoutControlDomain }
            .map { doorOf(it) }

        assertEquals("تسعة أقسام لها مجال في التحكّم", 9, targets.size)
        assertEquals("قسمان يفتحان الشاشة نفسها", targets.size, targets.distinct().size)
    }

    /**
     * (٤) وكل مجال تحكّم يفتحه قسم — **إلا الاستجابة**، وهي هنا بالاسم لا بالسكوت.
     *
     * ولا يُطلب لها قسم في هذه الجولة: الأقسام الاثنا عشر (‏`DeviceInfoSection`) لا تحمل
     * «استجابة»، وأي قسم لها يستلزم بنية بيانات جديدة (لمسة/إطارات) لا نقل صفّ. فالثابت هو
     * **إعلان الفجوة**، حتى لا تُقرأ «كل مجال له قسم» وهي ليست كذلك.
     *
     * **والصوت كان مُعلَنًا هنا جولةً واحدة (`AU-01` ← تكملة ٢٢٦) ثمّ خرج بالبناء لا بالنزع:**
     * صار له صفّ (`AudioStudio`) في `maxHubRows`، وقسمٌ في «معلومات الجهاز» — فارتفع المستثنى من
     * العدد ولم يُسكَت عنه السطر. وهذا هو الفرق بين إعلانٍ مؤقّت يُقاس وبين سكوتٍ يتقادم.
     */
    @Test
    fun `وكل مجال تحكّم له قسم إلا الاستجابة وهي مُعلَنة`() {
        val doorRoutes = DeviceInfoSection.entries.map { doorOf(it).route }.toSet()
        val hubs = controlLayoutModel().flatMap { it.hubs }
        assertTrue("لا محاور في شاشة التحكّم — الفحص نفسه معطوب", hubs.size >= 8)

        val hubsWithoutSection = hubs
            .filterNot { hub -> hub.features.any { it.destination.route in doorRoutes } }
            .map { it.hub.route }

        assertEquals(
            "مجال تحكّم بلا قسم في Device Info — إن كان مقصودًا فأعلنه في هذا الاختبار",
            listOf(MaxDestination.ResponsivenessHub.route),
            hubsWithoutSection,
        )
    }
}
