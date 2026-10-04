/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * **السجلّ الواحد للفتحات** — الجولة ٢٠٣.
 *
 * ولماذا اختبارٌ له وحده: العطب المُقاس هنا **لا يُرى في جلسة اختبار** — عدّادان يقيسان الفتح
 * نفسه يعملان تمامًا، ويُظهران ترتيبين مختلفين بعد أسابيع من الاستعمال، فيقول المُوجِّد «تفاصيل
 * التخزين هي الأكثر» وتقول منصة التحكم «إدارة ZRAM هي الأكثر»، ولا أحد منهما مخطئ في برمجته.
 * فالاختبار يحرس **القرار** لا الحساب: سجلٌّ واحد، يعيش على الجهاز وحده، والقارئان كلاهما منه.
 *
 * وحدّه المُعلَن: هذا فحص نصّيّ على المصدر (كتبته أداة أخرى؟ لا يعرف). ما يقيس السلوك وقت
 * التشغيل (`SharedPreferences` على جهاز) يبقى **يحتاج جهازًا** ولا يُدَّعى هنا.
 */
class ScreenUsageLedgerTest {

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

    private fun kotlinSources(): List<File> = sourceRoot
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    @Test
    fun `exactly one file in the app spells the usage key prefix`() {
        val owners = kotlinSources()
            .filter { it.readText().contains("\"screen_uses_\"") }
            .map { it.name }

        assertEquals(
            "مفتاح العدّاد يُكتب في مكان واحد؛ وثانيه يعني عدّادًا ثانيًا يفترق عنه يومًا",
            listOf("ScreenUsageStore.kt"),
            owners,
        )
        assertFalse(
            "وعدّاد المنصة القديم لا يعود",
            kotlinSources().any { it.readText().contains("\"home_deck_uses_\"") },
        )
    }

    @Test
    fun `the ledger is keyed by the route, which is a destination's identity`() {
        val text = read("ui/util/ScreenUsageStore.kt")
        assertTrue(
            "المفتاح مسارٌ لا اسم عرض يبتكره كل قارئ",
            text.contains("USAGE_PREFIX + route"),
        )
        assertTrue("والقراءة بادئتها نفسها", text.contains("startsWith(USAGE_PREFIX)"))
    }

    @Test
    fun `the ledger stays on the device and never leaves it`() {
        val text = read("ui/util/ScreenUsageStore.kt")
        assertTrue("التخزين تفضيلات محلّية", text.contains("SharedPreferences"))
        listOf("HttpURLConnection", "URL(", "Socket", "okhttp", "WorkManager").forEach { api ->
            assertFalse("سجلّ فتحات الشاشات لا يخرج من الجهاز: $api", text.contains(api))
        }
    }

    @Test
    fun `the route listener writes it, and the two readers read it`() {
        assertTrue(
            "الكاتب الواحد: مستمع الوجهة في MainActivity، فيُحتسب الفتح من أي مكان",
            read("MainActivity.kt").contains("screenUsageStore.record(destination.route)"),
        )
        assertTrue(
            "ومنصة التحكم تقرأ السجلّ نفسه بالمسار",
            read("ui/mainscreens/HomeScreen.kt").contains("homeDeckUsage(usageStore.counts())"),
        )
        assertTrue(
            "والمُوجِّد يقرأه عند كل فتحة لا مرّة واحدة",
            read("ui/mainscreens/ScreenFinderSheet.kt")
                .contains("ScreenUsageStore.of(context).counts()"),
        )
        assertTrue(
            "والتحويل من المسار إلى مفتاح البطاقة في النموذج الصافي لا في الشاشة",
            read("ui/mainscreens/HomeDeckModel.kt").contains("fun homeDeckUsage("),
        )
    }
}
