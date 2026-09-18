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

package nd.max.ui.viewmodel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`@Inject` ViewModel لا يجوز أن يُطلَب بـ`viewModel()`، بل بـ`hiltViewModel()`.**
 *
 * ولماذا هذا اختبار لا تعليق — لأن العطب **كراش فوري عند فتح الشاشة لا خطأ تصريف**:
 * `viewModel()` بلا مصنع يستعمل `ViewModelProvider.NewInstanceFactory`، وهو ينادي المُنشئ
 * **بلا وسائط**. وViewModel مُعلَّم بـ`@Inject constructor(arbiter)` **لا يملك** مُنشئًا بلا
 * وسائط ⇒ `RuntimeException: Cannot create an instance of class …` ويخرج التطبيق.
 *
 * وهذا ما حدث فعلًا في `GpuStudioScreen`: `GpuStudioViewModel` هو ViewModel الوحيد المُحقون
 * بـHilt الذي طُلب بـ`viewModel()`، والثلاثة الآخرون على `hiltViewModel()`. فلم يكن هناك ما
 * يكشف الخطأ لا في التصريف ولا في المراجعة — إلا فتح الشاشة على جهاز.
 *
 * والاختبار يقابل **الصنف بمكان طلبه**، لا الصنف وحده: الخطأ ليس في الـViewModel بل في السطر
 * الذي يستدعيه.
 */
class ViewModelInstantiationTest {

    /** يبحث عن جذر مصادر التطبيق صاعدًا من مجلد تشغيل الاختبار، فلا يعتمد على مسار ثابت. */
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

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** أسماء ViewModels التي **تُنشئ بنفسها** وسائط، فلا مُنشئ لها بلا وسائط. */
    private fun injectedViewModels(files: List<File>): Set<String> {
        val found = mutableSetOf<String>()
        files.forEach { file ->
            val text = file.readText()
            INJECTED_VM.findAll(text).forEach { found += it.groupValues[1] }
        }
        return found
    }

    /** `اسمViewModel = viewModel()` — أي طلب بالمصنع الافتراضي. */
    private fun requestedWithDefaultFactory(files: List<File>): List<Pair<File, String>> {
        val hits = mutableListOf<Pair<File, String>>()
        files.forEach { file ->
            file.readText().lineSequence().forEach { line ->
                DEFAULT_FACTORY.find(line)?.let { match ->
                    hits += file to match.groupValues[1].substringAfterLast('.')
                }
            }
        }
        return hits
    }

    @Test
    fun `الأصول موجودة فلا يمرّ اختبار فارغ`() {
        val root = sourceRoot()
        assertTrue(
            "تعذّر العثور على جذر مصادر التطبيق — اختبار لا يجد ما يفحصه لا يثبت شيئًا",
            root != null,
        )
        assertTrue("لم يُعثر على أي ViewModel مُحقون — الفحص نفسه معطوب", injectedViewModels(kotlinFiles(root!!)).isNotEmpty())
    }

    @Test
    fun `كل ViewModel مُحقون يُطلَب بـ hiltViewModel لا بـ viewModel`() {
        val files = kotlinFiles(sourceRoot()!!)
        val injected = injectedViewModels(files)
        val offenders = requestedWithDefaultFactory(files)
            .filter { (_, name) -> name in injected }
            .map { (file, name) -> "${file.name}: $name = viewModel()" }

        assertTrue(
            "ViewModels مُحقونة تُطلَب بالمصنع الافتراضي (كراش عند فتح الشاشة): $offenders",
            offenders.isEmpty(),
        )
    }

    private companion object {
        /** `class FooViewModel @Inject constructor(` — بلا مُنشئ بلا وسائط. */
        val INJECTED_VM = Regex("""class\s+(\w+ViewModel)\s+@Inject\s+constructor""")

        /** `viewModel: FooViewModel = viewModel(),` — أي طلب بلا مصنع، بالاسم البسيط أو الكامل. */
        val DEFAULT_FACTORY = Regex("""\b(\w+ViewModel)\s*=\s*viewModel\(\)""")
    }
}
