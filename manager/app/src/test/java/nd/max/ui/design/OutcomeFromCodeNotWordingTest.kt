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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **نتيجة فعل تُقرأ من رمزها، لا من صياغة جملتها.**
 *
 * وهذا عطب وقع فعلًا في شاشة CPU: كان الشريط العلوي يقرّر النجاح بالبحث في نصّ الرسالة عن
 * كلمات فشل (`رفض`، `تعذّر`، …). فحالة **مؤجَّلة** — «مالك أعلى أولوية يملك هذا المقبض، طلبك
 * محفوظ وسيُطبَّق عند الإفلات» — لا تحتوي أيًّا من تلك الكلمات، فعُرضت **نجاحًا أخضر** بينما
 * لم يتغيّر على الجهاز شيء. أي أن الواجهة قالت للمستخدم إن الطلب طُبِّق وهو لم يُطبَّق.
 *
 * والحلّ كان برمز: `CpuActionReason` و`GpuNoticeKind` في طبقة العرض المنطقي، والشاشة تُترجمه.
 * وهذا الاختبار يمنع الرجوع إلى الطريقة الأولى: يمنع أن تقرّر أي شاشة نتيجةً بمسح نصّ.
 *
 * وهو **بين-ملفّي** لأنه لا يكفي أن يكون `when` مكتملًا: `when` على `String` لا يُعالج
 * الحالة الجديدة أصلًا، فلا شيء في المصرّف يكشفها.
 */
class OutcomeFromCodeNotWordingTest {

    /**
     * الأنماط التي تعني «نتيجة قُرئت من النصّ»:
     * - `x.message.contains(...)` أو `it.contains(word)` على رسالة
     * - قائمة كلمات فشل تُستخدم للحكم
     */
    // بلا `\b` قبل `FAILURE_WORDS`: الشرطة السفلية حرف كلمة في التعبير النمطي، فلا حدَّ
    // كلمةٍ بين `_` و`F` — و`\bFAILURE_WORDS\b` كان يمرّ على العطب بلا أن يراه.
    // (اكتُشف عند تجربة تكذيب: أُعيد النمط مؤقتًا فنجح الاختبار، وهو أسوأ من ألّا يوجد.)
    private val wordingClassifiers = listOf(
        Regex("""\w*[Mm]essage\s*\.\s*contains\s*\("""),
        Regex("""\w*[Mm]essage\s*\.\s*containsAny"""),
        Regex("""FAILURE_WORDS"""),
        Regex("""\w*[Ff]ailureWords"""),
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

    @Test
    fun `الأصول موجودة فلا يمرّ اختبار فارغ`() {
        val root = sourceRoot()
        assertTrue("تعذّر العثور على جذر المصادر — اختبار لا يفحص شيئًا لا يثبت شيئًا", root != null)
        assertTrue(
            "لم يُعثر على أي شاشة — الفحص نفسه معطوب",
            root!!.walkTopDown().any { it.isFile && it.extension == "kt" },
        )
    }

    @Test
    fun `لا شاشة تقرّر نتيجةً بالبحث في نصّ الرسالة`() {
        val offenders = mutableListOf<String>()
        sourceRoot()!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readText().lineSequence().forEachIndexed { index, line ->
                    wordingClassifiers.forEach { pattern ->
                        if (pattern.containsMatchIn(line)) {
                            offenders += "${file.name}:${index + 1}"
                        }
                    }
                }
            }

        assertTrue(
            "شاشات تقرّر النتيجة من صياغة النصّ لا من رمزها — راجع CpuActionReason/GpuNoticeKind: $offenders",
            offenders.isEmpty(),
        )
    }
}
