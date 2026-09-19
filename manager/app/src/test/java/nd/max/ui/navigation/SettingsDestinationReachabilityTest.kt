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

package nd.max.ui.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **وجهة تحت الإعدادات بلا مدخل = شاشة لا يصل إليها أحد.**
 *
 * وهذا عطب وقع فعلًا: `PluginsScreen` بُنيت ومسارها سُجّل في `MaxNavGraph` ودورها كُتب في
 * `MaxDestinationCatalog` — ولم يوضع لها **مدخل واحد** في `SettingsScreen`. فبقيت شاشة كاملة
 * موصولة بالرسم وبلا طريق. ولا يكشف هذا لا التصريف ولا `code_health` (المسار مُعلن والصنف
 * موجود والبناء أخضر)، ولا يكشفه فتح التطبيق — لأن ما لا مدخل له لا يُضغط.
 *
 * **ولا يكفي أن تُذكر الوجهة في أي ملف.** فالشاشة تذكر نفسها (`MaxDestination.Plugins.icon`
 * في `PluginsScreen`)، ولو حسبنا ذلك مدخلًا لنجح الفحص على شاشة لا يصل إليها أحد — أي لكان
 * اختبارًا لا يستطيع الفشل. ⇒ يُشترط أن يكون المُذكِّر **ملفًّا آخر**: ملفّ واجهة اسمه لا يبدأ
 * باسم الوجهة. والعُرف في هذا المستودع أن شاشة الوجهة تُسمّى باسمها (`Plugins` ⇒ `PluginsScreen`)،
 * فسقف الثقة في هذا الاستدلال معلن لا مخفيّ.
 *
 * ونطاقه `Settings` وحده عن قصد: وجهات `Control` يغطّيها `ControlLayoutModelTest` (لكل صف
 * مصدر في النموذج)، ووجهات `Apps` تُفتح من قائمة التطبيقات بمعرّف حزمة، ووجهات كل hub
 * يغطّيها اختبار «كل hub يسرد كل شاشاته».
 */
class SettingsDestinationReachabilityTest {

    /**
     * الملفات التي **تعريف** الوجهة فيها لا **استعمالها**: السجل نفسه، والرسم، وجدول الأدوار.
     * ذِكرها في أحدها ليس مدخلًا.
     */
    private val registryFiles = setOf(
        "MaxDestinations.kt",
        "MaxNavGraph.kt",
        "MaxDestinationCatalog.kt",
    )

    private fun sourceRoot(): File? {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("manager/app/src/main/java"),
        )
        candidates.firstOrNull { it.isDirectory }?.let { return it }

        var dir: File? = File("").absoluteFile
        repeat(6) {
            val current = dir ?: return null
            listOf("app/src/main/java", "manager/app/src/main/java")
                .map { File(current, it) }
                .firstOrNull { it.isDirectory }
                ?.let { return it }
            dir = current.parentFile
        }
        return null
    }

    /** ملفات الواجهة **إلا** ملفات التعريف، كما هي على القرص. */
    private fun entryPointFiles(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in registryFiles }
            .toList()

    /**
     * الملفات التي تذكر هذه الوجهة بـ**اسمها** في السجل، **من غير شاشتها نفسها**.
     *
     * `MaxDestination.<Name>` هو الشكل الذي يُكتب به الانتقال فعلًا (`navigateTo`)، فالبحث
     * عن إشارة قابلة للتنفيذ لا عن نصّ حرّ.
     */
    private fun entryPointsOf(destination: MaxDestination, files: List<File>): List<String> {
        val name = destination::class.simpleName.orEmpty()
        val needle = "MaxDestination.$name"
        return files
            .filter { it.readText().contains(needle) }
            .map { it.name }
            .filterNot { it.startsWith(name) } // شاشة الوجهة تذكر نفسها — وليست مدخلًا
    }

    @Test
    fun `الأصول موجودة فلا يمرّ اختبار فارغ`() {
        val root = sourceRoot()
        assertTrue("تعذّر العثور على جذر المصادر — اختبار لا يفحص شيئًا لا يثبت شيئًا", root != null)
        assertTrue(
            "لم يُعثر على أي وجهة تحت Settings — الفحص نفسه معطوب",
            settingsDestinations().isNotEmpty(),
        )
    }

    @Test
    fun `كل وجهة تحت Settings لها مدخل في ملفّ آخر غير شاشتها`() {
        val files = entryPointFiles(sourceRoot()!!)
        val orphans = settingsDestinations()
            .filter { entryPointsOf(it, files).isEmpty() }
            .map { "${it::class.simpleName} (${it.route})" }

        assertTrue(
            "وجهات تحت الإعدادات بلا مدخل في أي شاشة غيرها (شاشة لا يصل إليها أحد): $orphans",
            orphans.isEmpty(),
        )
    }

    private fun settingsDestinations(): List<MaxDestination> =
        MaxDestination.All.filter { it.parent == MaxDestination.Settings }
}
