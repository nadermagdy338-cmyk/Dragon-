/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس مصدريّة لحالات شاشة التطبيقات — لا بديل عن اختبار عرض على جهاز.
 *
 * السبب الذي جعل هذا الملف موجودًا: شاشة التطبيقات كانت تعرض خطأها وفراغها من عائلة
 * الحالات المشتركة، بينما تعرض تحميلها بحلقة `76.dp` وخطّ `headlineMedium` مرسومين يدويًّا
 * في الشاشة. فكان أوّل ما يراه المستخدم أكبر عنصر في التطبيق، ثم يصغر كل شيء بعده فجأة.
 * هذا تضارب لغوي لا يمسكه مُصرّف ولا اختبار وحدة، فالحرس هنا نصّي عن قصد.
 */
class ApplistPresentationArchitectureTest {
    private lateinit var source: String

    /** الشاشة بلا تعليقات: حرس يقرأ تعليقًا يشرح الحالة التي يمنعها ليس حرسًا. */
    @Before
    fun readScreen() {
        val file = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).map { File(it, "ui/mainscreens/ApplistScreen.kt") }.firstOrNull { it.isFile }
        checkNotNull(file) { "Cannot locate ApplistScreen; source guard was not evaluated" }
        source = file.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\\n]*"), "")
    }

    @Test
    fun `loading error and empty all come from the shared state family`() {
        listOf("MaxLoadingState(", "MaxErrorState(", "MaxEmptyState(").forEach { state ->
            assertTrue(
                "Apps must render $state so its three transient states read as one language",
                source.contains(state),
            )
        }
    }

    @Test
    fun `no transient state draws its own indicator or display typography`() {
        assertFalse(
            "A bespoke spinner in this screen is how the 76dp ring came back before",
            source.contains("CircularProgressIndicator"),
        )
        listOf("headlineLarge", "headlineMedium", "displayLarge", "displayMedium", "displaySmall")
            .forEach { style ->
                assertFalse(
                    "State typography stays in the title/body families; $style belongs to content, not to a state",
                    source.contains(style),
                )
            }
    }

    @Test
    fun `the shared loading state stays wired to the first load predicate`() {
        val branch = Regex(
            "isRefreshing\\s*&&\\s*allApps\\.isEmpty\\(\\)\\s*->\\s*\\{\\s*MaxLoadingState\\(",
        )
        assertTrue(
            "The loading state must stay the answer to the empty first load, not to every refresh",
            branch.containsMatchIn(source),
        )
        assertTrue(
            "Its title and message stay the two strings the screen already announced",
            Regex("R\\.string\\.applist_loading_title").containsMatchIn(source) &&
                Regex("R\\.string\\.applist_loading_desc").containsMatchIn(source),
        )
    }
}
