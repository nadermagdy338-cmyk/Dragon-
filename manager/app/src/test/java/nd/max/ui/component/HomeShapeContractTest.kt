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
        val order = listOf("HomeHeader(", "HomeHeroCard(", "HomeGuideStrip(", "HomeVitalsGrid(", "HomeActionRow(")
            .map { body.indexOf(it) }
        assertTrue("كتلة مفقودة من الرئيسية", order.all { it >= 0 })
        assertTrue("ترتيب الكتل الأولى تغيّر", order == order.sorted())
        assertFalse("البطل الضخم القديم عاد", body.contains("PulsePanel("))
    }

    @Test
    fun `the vitals grid has four doors to four owner screens`() {
        val grid = read("HomeVitalsGrid.kt")
        listOf("CpuCoreControl", "MemoryHub", "ThermalDetail", "Charging").forEach { door ->
            assertTrue("خلية بلا باب: $door", grid.contains("MaxDestination.$door.route"))
        }
        // المجهول يُكتب شرطة لا صفرًا (ADR-07).
        assertTrue(grid.contains("takeIf { it > 0 }"))
    }

    @Test
    fun `the hero stays identity only and does not redraw the vitals`() {
        val hero = read("HomeHeroCard.kt")
        assertFalse("الحرارة عادت إلى البطل", hero.contains("batteryTempC"))
        assertFalse("البطارية عادت إلى البطل", hero.contains("batteryPercent"))
        assertTrue(hero.contains("MaxDestination.DeviceInfo.titleRes"))
        assertTrue(hero.contains("accessLabelRes(accessLevel)"))
    }

    @Test
    fun `boost has one owner and the banners pause on touch not on motion`() {
        assertFalse("زرّ Boost عاد إلى مصفوفة الذاكرة", read("HomeCapacityCards.kt").contains("onBoost"))
        assertTrue(read("HomeActionRow.kt").contains("onBoost"))
        val banners = read("HomeGuideBanners.kt")
        assertTrue("الإيقاف يجب أن يلتقط السحب", banners.contains("collectIsDraggedAsState"))
        assertFalse("isScrollInProgress يرتفع في الانتقال التلقائي نفسه", banners.contains("isScrollInProgress"))
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
}
