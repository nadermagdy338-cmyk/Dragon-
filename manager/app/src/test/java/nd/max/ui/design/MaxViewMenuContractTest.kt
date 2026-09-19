/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * حرّاس على **شكل** قائمة الاختيار الواحد، لا اختبار سلوكي.
 *
 * والعطب المحروس وقع فعلًا: `MaxViewMenu` كانت تختار أيقونتها بـ`MaxViewIcons[selected]`
 * وقائمةُ أيقوناتها **بندان**، فأول شاشة مرّرت أربعة أسماء (ترتيب مدير الملفات) صارت
 * تخرج عن حدود القائمة لحظة فتح القائمة. ولم يُظهره المصرّف، لأنه لا يعرف طول قائمة
 * وقت الترجمة؛ ولا اختبار على جهاز، لأن السقوط يقع عند لمس واحدة.
 *
 * فالحرّاس هنا تقيس ما يمنع الصنف كله لا العطب الواحد: لا فهرسة مباشرة لأي قائمة أيقونات
 * داخل المكوّن، وكل موضع يناديه بأسماء أكثر من أيقوناته الافتراضية **يسمّي أيقوناته**.
 * ولا تُغني عن قياس على جهاز: لا شيء هنا يُثبت أن القائمة تُرسم، بل يُثبت أن الكود لا
 * يستطيع الفهرسة خارج الحدود.
 */
class MaxViewMenuContractTest {

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

    /**
     * الكود دون تعليقاته: تُقاس البنية، ولا تُقاس صياغة التعليق. ولهذا تفشل هذه
     * الحرّاس إن كان الحارس موجودًا في تعليق فقط.
     */
    private fun strip(text: String): String = text
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    private fun source(relative: String): String = strip(File(sourceRoot, relative).readText())

    private fun allSources(): List<Pair<String, String>> = sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.path to strip(it.readText()) }
        .toList()

    /** الأيقونات الافتراضية في المكوّن — عدد يُقاس لا يُفترض. */
    private fun defaultIconCount(): Int {
        val menu = source("ui/design/MaxViewMenu.kt")
        val block = menu.substringAfter("private val MaxViewIcons: List<ImageVector> = listOf(")
            .substringBefore(")")
        assertTrue("قائمة الأيقونات الافتراضية لم تُقرأ — الحارس بلا مرجع", block.contains("Icons."))
        return Regex("Icons\\.").findAll(block).count()
    }

    /**
     * لا فهرسة مباشرة لأي قائمة أيقونات داخل المكوّن: `getOrNull` هي الطريقة الوحيدة
     * المسموحة، لأن طول القائمة يعتمد على المستدعي لا على المكوّن.
     */
    @Test
    fun theMenuNeverIndexesAnIconListDirectly() {
        val menu = source("ui/design/MaxViewMenu.kt")
        val direct = Regex("""\b(MaxViewIcons|icons)\s*\[""").findAll(menu).map { it.value }.toList()
        assertTrue(
            "فهرسة مباشرة لقائمة الأيقونات — هذا هو العطب الذي أسقط الشاشة: $direct",
            direct.isEmpty(),
        )
        assertTrue(
            "المكوّن لا يستعمل مرتبةً آمنة على الإطلاق، فالحارس بلا معنى",
            menu.contains("getOrNull"),
        )
    }

    /**
     * كل موضع ينادي المكوّن بأسماء **أكثر من الأيقونات الافتراضية** يجب أن يسمّي أيقوناته
     * (أو يمرّر `emptyList()` صراحةً) — فلا يعتمد على طول قائمة لم يكتبها.
     */
    @Test
    fun everyCallerWithMoreLabelsThanDefaultIconsNamesItsIcons() {
        val defaults = defaultIconCount()
        val offenders = mutableListOf<String>()

        allSources().forEach { (path, text) ->
            Regex("""MaxViewMenu\(""").findAll(text).forEach { match ->
                // تعريف الدالة نفسه يُطابق النمط: يُستثنى بالتوقيع السابق له لا بالملف،
                // فحرسٌ يعتمد على اسم ملف يُسقط أي مستدعٍ جديد في ملف جديد.
                val before = text.substring(maxOf(0, match.range.first - 24), match.range.first)
                if (before.trimEnd().endsWith("fun")) return@forEach
                val call = text.substring(match.range.last, minOf(text.length, match.range.last + 1200))
                val labels = call.substringAfter("labels = ", "")
                // نهاية الشريحة بمعامل **مسمّى** لا بفراغ/فاصلة معيّنة: `substringBefore`
                // الفاشلة تُعيد النص كله، فيُقاس على شريحة خطأ ويُبلَّغ برسالة مضلِّلة.
                val parts = labels.split(Regex("""[,\s]*selectedIndex\s*="""), limit = 2)
                assertTrue("شريحة الأسماء بلا نهاية موثوقة: $path", parts.size == 2)
                val labelExpr = parts[0]
                val named = Regex("stringResource\\(").findAll(labelExpr).count()
                val dynamic = labelExpr.contains(".map {") || labelExpr.contains(".map{")
                if ((named > defaults || dynamic) && !call.contains("icons =")) {
                    offenders += "$path ($named اسمًا، ديناميكي=$dynamic)"
                }
            }
        }

        assertTrue(
            "مواضع تمرّر أسماء أكثر من الأيقونات الافتراضية بلا `icons`: $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * ترتيب اللوحين قرار نموذج لا شرط في التركيب، والدليل: الشاشة تستدعي القاعدة.
     * والداعي: شرط داخل التركيب لا يمكن قياسه بلا جهاز، وخطؤه يُنتج لوحين متراكبين في
     * صندوق لا يتّسعهما (قياس لا نهائي ← انهيار في `LazyColumn`).
     */
    @Test
    fun thePaneArrangementComesFromTheModelRule() {
        val screen = source("ui/subscreens/FileManagerScreen.kt")
        assertTrue(
            "الشاشة لم تستدعِ قاعدة الترتيب المشتركة",
            screen.contains("PaneLayoutRule.sideBySide("),
        )
        assertFalse(
            "قرار الترتيب مكتوب مرة ثانية داخل التركيب",
            Regex("""maxWidth\s*>=?\s*SplitThreshold""").containsMatchIn(screen),
        )
    }

    /**
     * التنقّل المرتبط يُبنى في النموذج (بحثٌ في **قراءة** اللوح الآخر)، لا بدمج نصّي لمسار.
     * والداعي: مسار مُخترع بالدمج يُنزل لوحًا على مجلد لم نقسه — و`/sdcard/Android/data`
     * محجوب على كثير من الإصدارات، فيصير اللوح «تعذّرت القراءة» بلا سبب من المستخدم.
     */
    @Test
    fun linkedNavigationAsksTheModelInsteadOfJoiningPaths() {
        val screen = source("ui/subscreens/FileManagerScreen.kt")
        assertTrue(
            "الشاشة لا تستدعي قاعدة الربط في النموذج",
            screen.contains("DualPane.mirrorFolder(") && screen.contains("DualPane.mirrorParent("),
        )
        assertFalse(
            "مسار اللوح المقابل يُبنى بدمج نصّي",
            Regex("""path\s*\+\s*"/""").containsMatchIn(screen),
        )
    }
}
