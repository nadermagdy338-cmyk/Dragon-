/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import nd.max.ui.navigation.MaxDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * «منصة التحكم» — **قاعدة الاختيار وحدها**، مقيسة على JVM.
 *
 * وأرقام المالك محفورة هنا حرفيًّا («٤ خيارات ثابتين لو لسه مستخدم جديد» · «إلى ٦ خيارات أقصى
 * شيء» · «الافتراضي ٤ وتلقائي»)، فلا يغيّرها أحد بضغطة عابرة: تغييرها يكسر اختبارًا يُقرأ في
 * ثانية، لا يُكتشف بعد تثبيت.
 *
 * **والجولة ٢٠٧ حرّكت رقمًا واحدًا وفصلت آخر:** «والحد الأدنى ٢ بدل ٤» — فالاختبار يقول الآن
 * **أدنى ٢ · افتراضيّ ٤ · أقصى ٦**، ويُثبت معه أنّ العددين **منفصلان**: كان الرقم الواحد
 * (`HOME_DECK_MIN`) يخدم الافتراضيّ والأدنى معًا، فنزولهما معًا كان عطبًا نائمًا لم يُقَس بعد.
 */
class HomeDeckModelTest {

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

    private fun keys(usage: Map<String, Int> = emptyMap(), manual: List<String> = emptyList(), mode: HomeDeckMode = HomeDeckMode.Auto) =
        homeDeckSelection(mode, manual, usage).map { it.key }

    // ── الأرقام: ٢ أدنى · ٤ افتراضيّ · ٦ أقصى ────────────────────────────────

    @Test
    fun `the owner's numbers are four by default, two at least, six at most`() {
        assertEquals(4, HOME_DECK_DEFAULT)
        assertEquals(2, HOME_DECK_MIN)
        assertEquals(6, HOME_DECK_MAX)
        assertEquals(
            "الافتراضيّ يبني قائمة المستخدم الجديد",
            HOME_DECK_DEFAULT,
            HomeDeckDefaultKeys.size,
        )
        assertTrue(
            "والافتراضيّ فوق الأدنى: لو تعادلا لنزل الاثنان معًا في أول تحريك",
            HOME_DECK_DEFAULT > HOME_DECK_MIN,
        )
    }

    @Test
    fun `the default is not derived from the minimum, so lowering one cannot move the other`() {
        val model = read("ui/mainscreens/HomeDeckModel.kt")
        assertTrue(
            "قائمة المستخدم الجديد تُبنى من الافتراضيّ لا من الأدنى",
            model.contains("HomeDeckPool.take(HOME_DECK_DEFAULT)"),
        )
        assertFalse(
            "ولا يُشتقّ الأدنى من الافتراضيّ ولا العكس",
            model.contains("HOME_DECK_MIN = HOME_DECK_DEFAULT") ||
                model.contains("HOME_DECK_DEFAULT = HOME_DECK_MIN"),
        )
    }

    @Test
    fun `a new user sees the four cards of today, in the same order`() {
        assertEquals(HomeDeckDefaultKeys, keys())
        assertEquals(
            listOf("display", "thermal", "battery", "control"),
            HomeDeckDefaultKeys,
        )
        assertEquals(
            listOf(
                MaxDestination.DisplayStudio,
                MaxDestination.ThermalDetail,
                MaxDestination.Charging,
                MaxDestination.Control,
            ),
            homeDeckSelection(HomeDeckMode.Auto, emptyList(), emptyMap()).map { it.destination },
        )
    }

    // ── التلقائيّ: الأكثر استعمالًا ───────────────────────────────────────────

    @Test
    fun `automatic promotes what is opened most, and no longer pads a short list with two extra`() {
        // **‏٢ مستعملتان ⇒ بطاقتان.** كانت المنصة تحشو النقص حتى ٤ فتُظهر `display` و`thermal`
        // لمن لم يفتحهما — وقد نزل الأدنى إلى ٢ بأمر المالك، فانتهت الحشوة التي لم يُطلبها أحد.
        val selection = keys(usage = mapOf("storage" to 5, "zram" to 3))
        assertEquals(listOf("storage", "zram"), selection)

        // وثلاث مستعملات ⇒ ثلاث بطاقات، بحدّها الأعلى ٦.
        assertEquals(3, keys(usage = mapOf("storage" to 5, "zram" to 3, "cpu" to 1)).size)
    }

    @Test
    fun `automatic keeps the default of four when nothing is used, and stops at six when much is used`() {
        assertEquals(
            "الافتراضيّ يبقى ٤: من لم يفتح شيئًا لا يُصغَّر إلى الأدنى الجديد",
            HOME_DECK_DEFAULT,
            keys(usage = emptyMap()).size,
        )
        assertEquals(
            "وشاشة واحدة مستعملة تكفي لبطاقتين — الأدنى الجديد",
            HOME_DECK_MIN,
            keys(usage = mapOf("zram" to 1)).size,
        )
        assertEquals(
            "والمستعملة تتقدّم، وتُكمَّل الثانية بترتيب البركة",
            listOf("zram", "display"),
            keys(usage = mapOf("zram" to 1)),
        )

        val much = mapOf(
            "zram" to 9, "storage" to 8, "network" to 7, "cpu" to 6,
            "gpu" to 5, "device" to 4, "thermal" to 3, "display" to 2,
        )
        val selection = keys(usage = much)
        assertEquals(HOME_DECK_MAX, selection.size)
        assertEquals(listOf("zram", "storage", "network", "cpu", "gpu", "device"), selection)
        assertFalse("ولا بطاقة سابعة مهما استُعمل", selection.size > HOME_DECK_MAX)
    }

    @Test
    fun `a tie is broken by the pool order, not by the usage map's`() {
        val first = keys(usage = linkedMapOf("gpu" to 2, "cpu" to 2, "network" to 2, "storage" to 2))
        val second = keys(usage = linkedMapOf("storage" to 2, "network" to 2, "cpu" to 2, "gpu" to 2))
        assertEquals(first, second)
        // وترتيب البركة هو الفاصل: storage(5) ثم network(6) ثم cpu(7) ثم gpu(8).
        assertEquals(listOf("storage", "network", "cpu", "gpu"), first)
    }

    // ── اليدويّ: اختيار المستخدم، والحدّان ────────────────────────────────────

    @Test
    fun `manual shows exactly what was chosen, in the pool's order`() {
        val selection = keys(
            manual = listOf("gpu", "network", "cpu", "zram"),
            mode = HomeDeckMode.Manual,
        )
        assertEquals(listOf("zram", "network", "cpu", "gpu"), selection)
    }

    @Test
    fun `manual never drops below the minimum, and never passes the maximum`() {
        // وباثنتين ينتهي الحدّ: هذا اختيارٌ كامل لا نقصًا، فلا يُكمَّل.
        val two = keys(manual = listOf("gpu", "zram"), mode = HomeDeckMode.Manual)
        assertEquals(
            "اختيار الاثنتين هو الأدنى بالضبط: يُعرض كما هو",
            listOf("zram", "gpu"),
            two,
        )

        // وبواحدة يُكمَّل بالافتراضيّ (‏٢ أدنى عدد يُعرض).
        val tooFew = keys(manual = listOf("gpu"), mode = HomeDeckMode.Manual)
        assertTrue("اختيار ناقص يُكمَّل بالافتراضيّ لا يُعرض ناقصًا", tooFew.size >= HOME_DECK_MIN)
        assertTrue(tooFew.contains("gpu"))

        val tooMany = keys(
            manual = HomeDeckPool.map { it.key },
            mode = HomeDeckMode.Manual,
        )
        assertEquals(HOME_DECK_MAX, tooMany.size)
    }

    @Test
    fun `an unknown or repeated key is ignored instead of crashing`() {
        val selection = keys(
            manual = listOf("bogus", "gpu", "gpu", "zram", "cpu", "network"),
            mode = HomeDeckMode.Manual,
        )
        assertEquals(selection.distinct(), selection)
        assertFalse(selection.contains("bogus"))
        assertTrue(selection.containsAll(listOf("gpu", "zram", "cpu", "network")))
    }

    // ── والبركة ملتصقة بالسجلّ ────────────────────────────────────────────────

    @Test
    fun `every card is a registered destination, and its key round-trips through its route`() {
        HomeDeckPool.forEach { entry ->
            assertTrue(
                "بطاقة تفتح وجهة غير مسجّلة: ${entry.key}",
                entry.destination in MaxDestination.All,
            )
            assertEquals(
                "المفتاح لا يعود من مسار الوجهة — فالعدّاد يقيس شيئًا آخر",
                entry.key,
                homeDeckKeyOfRoute(entry.destination.route),
            )
            assertFalse(
                "بطاقة تفتح شاشة تحتاج معرّفًا: ${entry.key}",
                entry.destination.needsLaunchArgument,
            )
        }
    }

    @Test
    fun `no key and no route is listed twice in the pool`() {
        assertEquals(HomeDeckPool.map { it.key }.distinct(), HomeDeckPool.map { it.key })
        assertEquals(
            HomeDeckPool.map { it.destination.route }.distinct(),
            HomeDeckPool.map { it.destination.route },
        )
    }

    @Test
    fun `a screen outside the pool is not counted`() {
        assertNull(homeDeckKeyOfRoute(MaxDestination.Settings.route))
        assertNull(homeDeckKeyOfRoute(null))
    }

    // ── والرسم لا يكتب البطاقات بيد ───────────────────────────────────────────

    @Test
    fun `the deck draws its cards from the model instead of listing them`() {
        val deck = read("ui/mainscreens/HomeCommandDeck.kt")
        assertTrue("البطاقات تُمرَّر لا تُكتب في الرسم", deck.contains("entries: List<HomeDeckEntry>"))
        assertTrue("ونصّها من البطاقة نفسها", deck.contains("stringResource(entry.titleRes)"))
        assertFalse(
            "ولا بطاقة مكتوبة بيد (نصّ ثابت في الرسم)",
            deck.contains("R.string.home_action_thermal"),
        )
        assertTrue(
            "وزرّ الإعداد داخل رأس القسم نفسه لا في شريط الشاشة",
            deck.contains("R.string.home_deck_settings_cd"),
        )
    }

    @Test
    fun `the deck asks for the medium card size instead of carrying numbers of its own`() {
        val deck = read("ui/mainscreens/HomeCommandDeck.kt")
        assertTrue(
            "المقاس يُمرَّر من طبقة الرموز لا يُنسخ في الشاشة",
            deck.contains("size = MaxCardSize.Medium"),
        )
        assertFalse("ولا حشو بطاقة مكتوب بيد في الرسم", deck.contains("padding = MaxSpace.md"))
        assertFalse("ولا حاوية أيقونة مكتوبة بيد", deck.contains("iconContainer ="))
    }

    // ── (٤) والعدّاد من السجلّ الواحد بالمسار (الجولة ٢٠٣) ─────────────────

    @Test
    fun `the deck's counts come from the one route ledger, keyed by the card key`() {
        val usage = homeDeckUsage(
            mapOf(
                MaxDestination.ZramManager.route to 4,
                MaxDestination.Control.route to 2,
                MaxDestination.DeviceInfo.route to 9,
                "a_route_the_pool_does_not_know" to 30,
            )
        )

        assertEquals(4, usage["zram"])
        assertEquals(2, usage["control"])
        assertEquals(9, usage["device"])
        assertEquals(
            "كل مفتاح في البركة له قيمة، فلا يفرّق القارئ بين «لم يُفتح» و«غير مسجّل»",
            HomeDeckPool.map { it.key }.toSet(),
            usage.keys,
        )
        assertEquals(0, usage["gpu"])
        assertEquals(
            "وما ليس في البركة لا يُعرض له عدّاد",
            HomeDeckPool.size,
            usage.size,
        )
    }

    @Test
    fun `the deck says why a card was promoted, and only counts what was counted`() {
        val sheet = read("ui/mainscreens/HomeDeckSettingsSheet.kt")
        assertTrue(
            "العدد المقيس في السطر نفسه من المورد الواحد",
            sheet.contains("R.string.screen_opens_count"),
        )
        assertTrue("وفي المعاينة والاختيار اليدويّ معًا", sheet.contains("deckSubtitle("))
        assertTrue(
            "وما لم يُفتح لا يُقال له «٠ مرة»",
            sheet.contains("if (usage <= 0) return description"),
        )
        assertTrue(
            "وعدد البطاقات يُقال في الوضعين لا في اليدويّ وحده",
            sheet.contains("R.string.home_deck_count, current.size"),
        )
    }

    @Test
    fun `the deck keeps no counter of its own any more`() {
        val store = read("ui/util/HomeDeckStore.kt")
        assertFalse(
            "عدّاد ثانٍ يفترق عن المُوجِّد يومًا",
            store.contains("USAGE_PREFIX"),
        )
        assertFalse("ولا ملفّ تخزين لعدّ خاصة (الوصف قد يذكره، والمفتاح لا يُكتب)", store.contains("USAGE_CEILING"))
        assertFalse("ولا حفظ عدّ في ملفّ المنصة", store.contains("fun record("))
        assertFalse("ولا قراءة عدّ منه", store.contains("fun usage("))
        assertTrue(
            "والكاتب الواحد هو مستمع الوجهة في `MainActivity`",
            read("MainActivity.kt").contains("ScreenUsageStore.of(context)"),
        )
    }
}
