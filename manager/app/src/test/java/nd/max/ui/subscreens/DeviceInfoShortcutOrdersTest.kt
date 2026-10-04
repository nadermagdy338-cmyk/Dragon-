/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens

import nd.max.ui.navigation.MaxDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * أوامر المالك في جولة التصميم (٢٠١) — **بوّابةً لكلٍّ منها**، فلا يعود أحدها بصمت.
 *
 * 1. «التصميم سيئ والزرّ كبير أوي»: فذهب الشكل رأسًا — كبسولة ٤٠dp بشارة ٢٨dp وإطار وخلفية
 *    (نسخة كبسولة `Max AI`) صارت **سطرَ بابٍ**: لا خلفية ولا إطار ولا شارة ولا `fillMaxWidth`.
 * 2. «ولماذا تضيفها في بطاقات الشاشات من الخارج»: فذهب `fillMaxWidth` — وهو سببُه المقيس:
 *    شريطٌ بعرض الصفحة في خانة جانبية يلتهم عرض العنوان المجاور (‏`weight(1f)`)، وقد قاست لقطةُ
 *    المالك أن صفّ هويّة بطاقة الرسوم لم يرسم إلا الكبسولة. وصار الباب يُحاذى على بداية القراءة.
 * 3. «أماكن الوضع تحتاج تحسين في بعض الشاشات» و«يجب أن يكون الزرّ جزءًا من فلسفة الشاشة»:
 *    فالباب **آخر البطاقة التي تشرح موضوع الشاشة** — لا في صفّ عنوانها ولا في خانة `trailing`
 *    لصفّ بيانات.
 * 5. **وجولة ٢٠٤ («زر يوصل إلى القسم المناسب في التسع بطريقة احترافية ومتناسقة ولا يكون واضحًا
 *    اوي او مجهولًا اوي»):** العلامة الأولى صارت **علامة الوجهة** من السجلّ
 *    (`MaxDestination.DeviceInfo.icon`) بدل أيقونة الشاشة — فالتسعة تشترك في المعلومة الثابتة
 *    (علامة واحدة + كلمة واحدة) ويتغيّر اسم القسم وحده، والأيقونة الشاشة مرسومة في رأسها
 *    أصلًا؛ وذهب `SemiBold` (كان يجعل الباب أبرز من الأرقام = «واضح أوي») وبقي **لون التمييز**
 *    للفعل والسهم؛ واسم قسم طويل يُقصّ `Ellipsis` بـ`weight(1f, fill = false)` فلا يدفع السهم
 *    خارج السطر (بابٌ بلا سهم = «مجهول»).
 * 4. صفحات المحور التسع **لا** ترسم بابًا (اختيار المالك): الصفحة نفسها فهرس أبواب.
 *
 * **ولماذا بوّابة على نصوص الملفّات لا على الرسم:** الشكل المرئيّ يحتاج جهازًا، وأوامر المالك
 * قرارٌ في **البنية** (أيّ مكوّن، وأيّ خانة، وأيّ حاشية) — وهي مقيسة نصًّا في ثوانٍ بلا مُصرّف.
 * وما تحتاجه العين (وضوح السطر · RTL · الوضع الداكن · مقاس اللمس · المحاذاة بالبكسل) يبقى
 * **يحتاج جهازًا** ويُقال كذلك ولا يُدّعى هنا.
 *
 * وكل نصّ يُقرأ **بلا تعليقاته**: تُقاس البنية لا صياغة الشرح.
 */
class DeviceInfoShortcutOrdersTest {
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

    private val componentSource = "ui/component/MaxDeviceInfoShortcut.kt"
    private val screenSource = "ui/subscreens/DeviceInfoScreen.kt"
    private val hubSource = "ui/subscreens/hubs/MaxDomainHubScreen.kt"
    private val gpuSource = "ui/subscreens/GpuStudioScreen.kt"

    /** الشاشات التسع التي تحمل الباب. */
    private val shortcutOwners = listOf(
        "ui/subscreens/CpuCoreControlScreen.kt" to MaxDestination.CpuCoreControl,
        "ui/subscreens/GpuStudioScreen.kt" to MaxDestination.GpuStudio,
        "ui/subscreens/ZramManagerScreen.kt" to MaxDestination.ZramManager,
        "ui/subscreens/StorageDetailScreen.kt" to MaxDestination.StorageDetail,
        "ui/subscreens/ChargingScreen.kt" to MaxDestination.Charging,
        "ui/subscreens/DisplayStudioScreen.kt" to MaxDestination.DisplayStudio,
        "ui/subscreens/ThermalDetailScreen.kt" to MaxDestination.ThermalDetail,
        "ui/subscreens/NetworkDetailScreen.kt" to MaxDestination.NetworkDetail,
        "ui/mainscreens/DiagnosticsScreen.kt" to MaxDestination.Diagnostics,
        // وسطح التحكّم الصوتيّ (`AS-02`): بابُه يعود إلى موضوعه، ولمّا كان يحمله فحقه أن يُعدّ هنا.
        "ui/subscreens/audio/AudioStudioScreen.kt" to MaxDestination.AudioStudio,
    )

    /** الشاشات التي بطاقتها **مجموعة صفوف** (`MaxGroup`)، فيُفصل الباب فيها بخطّ داخلي. */
    private val sectionCardSources = listOf(
        "ui/subscreens/ZramManagerScreen.kt",
        "ui/subscreens/StorageDetailScreen.kt",
        "ui/subscreens/ChargingScreen.kt",
        "ui/subscreens/ThermalDetailScreen.kt",
        "ui/subscreens/NetworkDetailScreen.kt",
    )

    // ── الأمر ٤: لا باب في صفحات المحور ────────────────────────────────────────

    @Test
    fun `no hub page carries the Device Info door`() {
        val hub = read(hubSource)
        assertFalse(
            "صفحة محور ترسم بابًا لمعلومات الجهاز — والصفحة نفسها فهرس أبواب (اختيار المالك)",
            hub.contains("MaxDeviceInfoShortcut"),
        )
        assertTrue(
            "وصفوف المجال نفسها باقية (إزالتها تُفرغ أربع صفحات تمامًا)",
            hub.contains("rows.forEachIndexed"),
        )
        assertTrue(
            "ومفاتيح المجال المباشرة باقية كما كانت",
            hub.contains("hubToggles("),
        )
    }

    @Test
    fun `no hub page keeps an available-tools heading`() {
        assertFalse(
            "«الأدوات المتاحة» أُزيل بأمر المالك — لا رأس ولا مفتاح في هذا الملف",
            read(hubSource).contains("max_hub_tools_title"),
        )
    }

    // ── الأمران ١ و٢: سطر رابط، لا كبسولة ولا شريط ─────────────────────────────

    @Test
    fun `the door is a worded line, not a capsule or a bare icon`() {
        val component = read(componentSource)
        assertTrue(
            "النصّ المرئيّ «More info» من الموارد لا مكتوبًا بيد (أمر المالك)",
            component.contains("R.string.more_info"),
        )
        assertFalse(
            "أمر المالك: زرّ وليس أيقونة — لا أيقونة مجرّدة بلا كلمة",
            component.contains("IconButton("),
        )
        assertTrue(
            "وما يفعله الباب لقارئة الشاشة يسمّي القسم لا «زرّ» فقط",
            component.contains("R.string.devinfo_shortcut_cd"),
        )
        assertTrue("ودوره `Button` في اللمس", component.contains("Role.Button"))
        assertTrue(
            "واسم القسم مرئيًّا بعده — وهو ما يجعله جزءًا من الشاشة لا عنصرًا عامًّا",
            component.contains("R.string.devinfo_shortcut_role"),
        )
    }

    @Test
    fun `the door never asks for the full width and carries no capsule chrome`() {
        val component = read(componentSource)
        assertFalse(
            "شريطٌ بعرض الصفحة كان يلتهم عرض العنوان المجاور (‏`weight(1f)`) — قياس لقطة المالك",
            component.contains("fillMaxWidth"),
        )
        assertFalse("ولا خلفية كبسولة", component.contains("background("))
        assertFalse("ولا إطار كبسولة", component.contains("border("))
        assertFalse("ولا شارة دائرية (نسخة زرّ آخر: كبسولة Max AI)", component.contains("CircleShape"))
        assertFalse("ولا ارتفاع زرّ كبير ٤٠dp", component.contains("BUTTON_HEIGHT"))
        assertTrue(
            "ومنطقة اللمس تبقى ٤٨dp من المنصّة وإن صغر الحبر",
            component.contains("minimumInteractiveComponentSize()"),
        )
    }

    // ── الأمر ٥ (جولة ٢٠٤): العلامة واحدة، والنبرة موزونة، والسهم لا يختفي ─────────

    @Test
    fun `the first mark is the destination's, so the nine lines share one shape`() {
        val component = read(componentSource)
        assertTrue(
            "علامة الباب من وجهته في السجلّ (‏ADR-02) لا من استيراد أيقونة بيد",
            component.contains("MaxDestination.DeviceInfo.icon"),
        )
        assertFalse(
            "وعلامة الشاشة ذهبت: كانت تُجعل كل سطر مختلفًا في أولِ ما تراه العين، " +
                "وقد أُعلن نقض قرار ٢٠١ في كتلة الملفّ نفسها",
            component.contains("from.icon"),
        )
    }

    @Test
    fun `the door is worded and coloured, and never louder than the data it explains`() {
        val component = read(componentSource)
        assertTrue(
            "الفعل باقٍ ملؤونًا — بلا لون يصير مجهولًا",
            component.contains("text = stringResource(R.string.more_info)"),
        )
        assertFalse(
            "و`SemiBold` ذهب: كان يجعل الباب أبرز من الأرقام التي يشرحها («واضح أوي»)",
            component.contains("SemiBold"),
        )
        assertTrue(
            "والوزن الوسط هو الموازنة",
            component.contains("FontWeight.Medium"),
        )
        assertTrue(
            "وعلامة الوجهة ونصّ القسم بلون هادئ لا بلون التمييز",
            component.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant"),
        )
        // **و"لون تمييز واحد" مقيس:** لون التمييز في الباب يحمله الفعل والسهم لا غير — فلا يصبح
        // عنصرًا ثالثًا ينافس الأرقام («واضح أوي»)، ولا يفقد اثنيهما فيصير «مجهولًا».
        assertEquals(
            "لون التمييز في الباب يحمله عنصران لا أكثر: كلمة «معلومات أكثر» والسهم",
            2,
            Regex("colorScheme\\.primary").findAll(component).count(),
        )
    }

    @Test
    fun `a long section name is cut instead of pushing the arrow out`() {
        val component = read(componentSource)
        assertTrue(
            "التقصير مع إعلانه (لا قطع حرفٍ صامت)",
            component.contains("TextOverflow.Ellipsis"),
        )
        assertTrue(
            "واسم القسم لا يأخذ عرض السطر كاملًا ولا يتوسّع إن قصر",
            component.contains("Modifier.weight(1f, fill = false)"),
        )
        assertTrue(
            "والسهم باقٍ AutoMirrored كما كان (ينعكس مع اتجاه اللغة)",
            component.contains("Icons.AutoMirrored.Filled.KeyboardArrowRight"),
        )
    }

    // ── الأمر ٣: الموضع بحسب الشاشة، وآخر البطاقة دائمًا ───────────────────────

    @Test
    fun `the door is separated from data and never sits on a title row`() {
        val onTitleRow = sectionCardSources.filter { read(it).contains("trailing = { MaxDeviceInfoShortcut") }
        assertTrue(
            "أبواب في صفّ العنوان بدل آخر البطاقة: $onTitleRow",
            onTitleRow.isEmpty(),
        )

        val notSeparated = sectionCardSources.filterNot { file ->
            Regex("MaxGroupDivider\\(\\)\\s*\\n\\s*MaxDeviceInfoShortcut\\(").containsMatchIn(read(file))
        }
        assertTrue(
            "بطاقة صفوف لا تفصل بابها بخطّ داخلي (إجراء على البطاقة لا صفّ بيانات): $notSeparated",
            notSeparated.isEmpty(),
        )

        // وخانة `trailing` لصفّ بيانات صارت ممنوعة على الباب: هي التي أفرغت صفّ الهوية.
        val inRowSlot = (sectionCardSources + gpuSource).filter { read(it).contains("trailing = trailing") }
        assertTrue(
            "بابٌ في خانة `trailing` لصفّ بيانات (يلتهم عرض العنوان): $inRowSlot",
            inRowSlot.isEmpty(),
        )
    }

    @Test
    fun `the hero cards render the door after their own content`() {
        // بطاقة المعالج: الباب بعد شبكة الأنوية — لا في صفّ الشريحة والعنوان.
        val cpu = read("ui/subscreens/CpuCoreControlScreen.kt")
        assertTrue(
            "بطاقة المعالج ترسم بابها قبل محتواها",
            cpu.indexOf("trailing?.invoke()") > cpu.indexOf("CoreGridMap("),
        )
        // وبطاقة العرض: الباب بعد بيانات العرض.
        val display = read("ui/subscreens/DisplayStudioScreen.kt")
        assertTrue(
            "بطاقة العرض ترسم بابها قبل بياناتها",
            display.indexOf("trailing?.invoke()") > display.indexOf("display_studio_native"),
        )
        // وبطاقة الرسوم: الباب آخِر ما في المجموعة بعد القراءة والسجل — لا على صفّ الهوية.
        val gpu = read(gpuSource)
        assertTrue(
            "بطاقة الرسوم ترسم بابها قبل محتواها",
            gpu.indexOf("trailing?.invoke()") > gpu.indexOf("max_gpu_history_title"),
        )
        assertTrue(
            "والخطّ الفاصل قبله: ما فوقه محتوى وما تحته إجراء على البطاقة",
            gpu.contains("MaxGroupDivider()\n        trailing?.invoke()"),
        )
    }

    // ── الأساس الباقي من الجولة السابقة: لا باب في آخر أقسام شاشة الجهاز ───────

    @Test
    fun `the Device Info screen no longer closes every section with a door`() {
        val screen = read(screenSource)
        assertFalse(
            "صفّ الباب أُزيل من آخر الأقسام بأمر المالك",
            screen.contains("MaxNavigationRow("),
        )
        assertFalse(
            "ولا يُقرأ مُلك القسم للعرض بعد الآن (لا `fullScreen` في مسار الرسم)",
            screen.contains("fullScreen"),
        )
        assertTrue(
            "والخريطة باقية **مكتوبة** ليقيس عليها الاختبار الاتجاه العكسي (ذهابًا وعودةً)",
            screen.contains("fun maxDeviceInfoSource("),
        )
        // **وصُحّح المُرسى في تكملة ٢٢٤:** كان التكرار `section.facts.forEachIndexed` في الشاشة،
        // وصار داخل `DeviceInfoCardBlock` — وهي مكوّن **محلّي في هذا الملفّ نفسه**. **والحكم لم
        // يُخفَّف:** الشاشة ما زالت تمرّر حقل النموذج وصفوفه كما هما (`facts = section.facts` ·
        // `rows = section.rows`)، فلا صفّ يُبنى بيد، والتفاصيل التي لا تُعرض تبقى في النموذج.
        assertTrue(
            "والأقسام تُبنى كما كانت من النموذج بلا صفّ بعدها",
            screen.contains("facts = section.facts") && screen.contains("rows = section.rows"),
        )
    }

    // ── والأساس: كل باب يفتح وجهة مسجّلة ───────────────────────────────────────

    @Test
    fun `every screen that carries the door is a registered destination`() {
        val graph = sourceRoot.walkTopDown()
            .firstOrNull { it.isFile && it.name == "MaxNavGraph.kt" }
        assertNotNull("تعذّر العثور على MaxNavGraph.kt — اختبار لا يقرأ شيئًا لا يُثبت شيئًا", graph)
        val graphText = graph!!.readText()

        // والتسجيل هنا بصيغة `route = …` لا صيغة الموقع: وجهة معلومات الجهاز تحمل معاملًا
        // مُعلَنًا، فالمطابقة تقبل الصيغتين ولا تفترض صيغة واحدة (وهي التي أسقطت أول تشغيل
        // لهذا الاختبار — أي أنه قاس الشكل لا الحقيقة).
        val registered = Regex("composable\\(\\s*(route\\s*=\\s*)?MaxDestination\\.DeviceInfo\\.route")
        assertTrue(
            "وجهة معلومات الجهاز مُسجّلة — وهي التي يفتحها كل باب بمعامل قسمه",
            registered.containsMatchIn(graphText),
        )
        assertTrue(
            "والمعامل مُعلَن: معامل غير مُعلَن يُهمله الـNavigator صامتًا",
            graphText.contains("navArgument(\"section\")"),
        )

        val missing = shortcutOwners.map { it.second }
            .distinct()
            .filterNot { it in MaxDestination.All }
        assertTrue("شاشات تحمل الباب وخارج سجلّ الوجهات: $missing", missing.isEmpty())
    }
}
