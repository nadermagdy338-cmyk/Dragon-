/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس شكل الرئيسية بعد إعادة بنائها (`MAX-MANAGER-LEVEL-UP.md` §5) — **يقرؤون الكود لا اللقطة**:
 * ما يضمنونه أن القرارات التي عُكست عن الجولة السابقة لا تُعاد بضغطة عابرة.
 *
 * **حدّها المُعلَن:** هل يُقرأ الشكل جميلًا ومتّزنًا على شاشة حقيقية — **يحتاج جهازًا**.
 */
class HomeShapeContractTest {

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

    private fun read(name: String): String = File(sourceRoot, "ui/mainscreens/$name").readText()

    @Test
    fun `the dashboard order puts identity banners vitals and actions first`() {
        val body = read("LegendaryHomeDashboard.kt").substringAfter("internal fun LegendaryHomeDashboard(")
        val order = listOf("HomeHeader(", "HomeHeroCard(", "HardwarePulseCards(", "HomeActionRow(", "HomeVitalsGrid(", "MemoryMatrixCard(", "UltraCleanerHomeCard(", "CommandDeck(", "UnifiedActivityCard(")
            .map { body.indexOf(it) }
        assertTrue("كتلة مفقودة من الرئيسية", order.all { it >= 0 })
        assertTrue("ترتيب الكتل الأولى تغيّر", order == order.sorted())
        assertFalse("البطل الضخم القديم عاد", body.contains("PulsePanel("))
    }

    @Test
    fun `the live readings open their owner screens and never repeat what the screen already shows`() {
        val grid = read("HomeVitalsGrid.kt")
        listOf("ThermalDetail", "NetworkHub").forEach { door ->
            assertTrue("خلية بلا باب: $door", grid.contains("MaxDestination.$door.route"))
        }
        // المجهول يُكتب شرطة لا صفرًا (ADR-07).
        assertTrue(grid.contains("takeIf { it > 0 }"))
        // لا تكرار: ما تعرضه البطاقة الأولى والنبض والذاكرة والتخزين لا يُعاد هنا (طلب المالك).
        listOf(
            "batteryPercent", "batteryTempC", "powerWatt", "cpuTopCoreMhz", "ramUsedMb",
            "cpuLoadPercent", "gpuLoadPercent", "gpuFreqMhz", "storageUsedGb", "swapUsedMb",
        ).forEach { shown ->
            assertFalse("قراءة مكرّرة في الحيّ: $shown", grid.contains(shown))
        }
    }

    @Test
    fun `the first card keeps the approved shape and every number on it is a token`() {
        val hero = read("HomeHeroCard.kt")
        assertTrue("شارة الوصول غائبة", hero.contains("accessLabelRes(accessLevel)"))
        assertTrue("الحرارة بالرقم الرئيسي غائبة", hero.contains("displayMedium"))
        assertTrue("الخط الأحادي للحرارة غائب", hero.contains("MonoFontFamily"))
        assertTrue("ثلاث بلاطات قراءة لم تُبنَ", hero.contains("NeuralTile(") && hero.contains("MonoValueStyleMedium"))
        assertTrue("زر Max AI غائب", hero.contains("MaxAiEntryButton("))
        assertTrue("باب نظرة الجهاز غائب", hero.contains("home_hero_open_overview") && hero.contains("MaxDestination.DeviceInfo.icon"))
        assertFalse("حجم خط مكتوب بيد في البطاقة", hero.contains("fontSize = "))
    }

    @Test
    fun `boost has one owner and the home screen carries no moving card`() {
        assertFalse("زرّ Boost عاد إلى مصفوفة الذاكرة", read("HomeCapacityCards.kt").contains("onBoost"))
        assertTrue(read("HomeActionRow.kt").contains("onBoost"))
        assertFalse(
            "البانرات المتحركة عادت (طلب المالك: إزالتها كليًّا)",
            File(sourceRoot, "ui/mainscreens/HomeGuideBanners.kt").exists(),
        )
        val dashboard = read("LegendaryHomeDashboard.kt")
        assertFalse("الدخول المتتابع عاد إلى الرئيسية", dashboard.contains("MaxReveal("))
        assertFalse("شريط متحرك عاد إلى الرئيسية", dashboard.contains("HomeGuideStrip("))
        val activity = read("StoryboardHome.kt").substringAfter("fun UnifiedActivityCard(").substringBefore("\n}\n")
        assertFalse("تبديل مشهد بطاقة النشاط عاد بانزلاق", activity.contains("AnimatedContent("))
    }

    @Test
    fun `every tour step lights something that exists and the question mark restarts it`() {
        val model = read("HomeTourModel.kt")
        val dashboard = read("LegendaryHomeDashboard.kt")
        val targets = model.substringAfter("enum class HomeTourTarget {").substringBefore("}")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue("لا أهداف في الجولة", targets.isNotEmpty())
        targets.forEach { target ->
            assertTrue("هدف بلا مرساة في الرئيسية: $target", dashboard.contains("HomeTourTarget.$target,"))
            assertTrue("هدف بلا خطوة: $target", model.contains("(HomeTourTarget.$target,"))
        }
        assertTrue("`?` يجب أن يعيد الجولة مع البانرات", read("HomeGuideModel.kt").contains("tourFinished = false"))
        assertTrue("الغطاء يجب أن يبتلع اللمس", read("HomeTourOverlay.kt").contains("detectTapGestures"))
        assertFalse(
            "boundsInWindow تقصّ العنصر خارج الشاشة فلا يُعرف كم نمرّر",
            read("HomeTourOverlay.kt").lines().any { it.contains("boundsInWindow()") && !it.trimStart().startsWith("*") },
        )
    }

    @Test
    fun `the live readings use the design headline token and never a literal font size`() {
        val grid = read("HomeVitalsGrid.kt")
        assertTrue("القراءة الرئيسية تأخذ رمز الخط، لا حجمًا مكتوبًا", grid.contains("MonoValueStyleLarge"))
        assertFalse("حجم خط مكتوب بيد داخل الشبكة", grid.contains("fontSize = "))
    }

    @Test
    fun `the two live blocks carry the shared section title and the rest of the page keeps its order`() {
        val dashboard = read("LegendaryHomeDashboard.kt")
        assertTrue("الترويسة المشتركة للقسم غائبة", dashboard.contains("NeuralSectionHeader("))
        assertTrue("القراءات بلا ترويسة", dashboard.indexOf("home_section_live") in 0 until dashboard.indexOf("HomeVitalsGrid("))
        assertTrue("الإجراءات بلا ترويسة", dashboard.indexOf("home_section_actions") in 0 until dashboard.indexOf("HomeActionRow("))
    }

    @Test
    fun `the focus card rises under the hero only for a real problem, and sits in its place otherwise`() {
        val dashboard = read("LegendaryHomeDashboard.kt")
        assertTrue("الرفع مرتبط بقاعدة واحدة", dashboard.contains("HomeFocusModel.focusKind("))
        assertTrue("بطاقة الخلل تُعرض مرة واحدة في كل حالة", dashboard.contains("if (focusFirst)") && dashboard.contains("if (!focusFirst)"))
    }

    @Test
    fun `the live readings state their trend and read as one sentence`() {
        val grid = read("HomeVitalsGrid.kt")
        assertTrue("الخلية جملة واحدة لقارئ الشاشة", grid.contains("mergeDescendants = true"))
        assertTrue("الاتجاه من نافذة الدقيقة", grid.contains("HomeTrendModel.direction("))
        assertTrue("الاتجاه يُكتب بكلمة لا بالسهم وحده", grid.contains("home_detail_with_trend"))
    }

    @Test
    fun `storage has one owner: the memory matrix carries no storage number of its own`() {
        val capacity = read("HomeCapacityCards.kt")
        val matrix = capacity.substringAfter("internal fun MemoryMatrixCard(").substringBefore("private fun MemoryFactRow(")
        assertFalse("لا رقم للتخزين داخل المصفوفة", matrix.contains("storageUsedGb"))
        assertTrue("الصف باب إلى بطاقة التنظيف", matrix.contains("home_storage_see_cleaner"))
    }

    @Test
    fun `the poll loop measures its cost and reads storage on a slower beat`() {
        val vm = read("HomeDashboardViewModel.kt")
        assertTrue("كلفة النبضة تُكتب في السجلّ", vm.contains("recordPollCost(fastMs, slowMs)"))
        assertTrue("التخزين بإيقاع بطيء", vm.contains("storageForCycle(SystemClock.elapsedRealtime())"))
    }

}
