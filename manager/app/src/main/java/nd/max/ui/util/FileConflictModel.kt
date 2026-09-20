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

/**
 * `MT-FM` — قرار **تعارض الأسماء** عند اللصق: استبدال · تخطّي · إعادة تسمية · إلغاء.
 *
 * ولماذا نموذج خالص لهذا: اللصق في مجلد فيه اسم مطابق هو أخطر لحظة في مدير ملفات —
 * الاستبدال الخاطئ يمحو ملفًا لم يقصد المستخدم مسحه، والتخطّي الخاطئ يوهمه بأن كل شيء
 * نُسخ. فالقرار يُحسب هنا على **الدفعة كاملة** قبل التنفيذ، لا سطرًا سطرًا داخل حلقة.
 *
 * و«لكل» في MT تعني أن السؤال **لا يتكرّر**: من قال «استبدال الكل» لا يُسأل عشرين مرة.
 */
package nd.max.ui.util

/** قرار المستخدم: مفرد أو معمَّم، أو انتظار سؤال. */
enum class ConflictChoice { Ask, Overwrite, OverwriteAll, Skip, SkipAll, Rename, Cancel }

/** نتيجة الفحص: الأسماء التي تُصطدم بالوجهة. */
data class ConflictScan(val collisions: List<String>) {
    val hasCollisions: Boolean get() = collisions.isNotEmpty()
    val count: Int get() = collisions.size
}

/** ما سيُنفَّذ فعلًا بعد القرار: أسماء كما هي، وأسماء تُتخطّى، وأسماء تُعاد تسميتها. */
data class PastePlan(
    val proceed: List<String> = emptyList(),
    val skip: List<String> = emptyList(),
    val rename: Map<String, String> = emptyMap(),
    val cancel: Boolean = false,
) {
    val isEmpty: Boolean get() = !cancel && proceed.isEmpty() && skip.isEmpty() && rename.isEmpty()
}

object FileConflictRules {

    /** فحص الدفعة ضدّ ما هو موجود في الوجهة **بالاسم** (الاسم هو ما يُصطدم، لا المسار). */
    fun scan(sourceNames: List<String>, destinationNames: Set<String>): ConflictScan =
        ConflictScan(sourceNames.filter { it in destinationNames })

    /**
     * خطة الدفعة كاملة.
     *
     * و`Ask` تُعيد `null` **عن قصد**: هي ليست قرارًا بل انتظار قرار، وإرجاع خطة منها
     * كان يعني أن الشاشة تُنفّذ شيئًا لم يُختَر بعد.
     */
    fun plan(
        sourceNames: List<String>,
        destinationNames: Set<String>,
        choice: ConflictChoice,
    ): PastePlan? = when (choice) {
        ConflictChoice.Ask -> null
        ConflictChoice.Cancel -> PastePlan(cancel = true)
        ConflictChoice.Overwrite, ConflictChoice.OverwriteAll -> PastePlan(proceed = sourceNames)
        ConflictChoice.Skip, ConflictChoice.SkipAll -> {
            val collisions = scan(sourceNames, destinationNames).collisions.toSet()
            PastePlan(
                proceed = sourceNames.filterNot { it in collisions },
                skip = sourceNames.filter { it in collisions },
            )
        }
        ConflictChoice.Rename -> {
            val collisions = scan(sourceNames, destinationNames).collisions
            // الأسماء الجديدة تُحسب تباعًا مع تضمين ما وُلد منها، وإلا صار اسمان
            // جديدان متماثلين لأن كليهما قيس على القائمة الأصلية وحدها.
            val taken = destinationNames.toMutableSet()
            val renames = LinkedHashMap<String, String>()
            for (name in collisions) {
                val fresh = FileOpGuard.uniqueName(name, taken)
                renames[name] = fresh
                taken += fresh
            }
            PastePlan(
                proceed = sourceNames.filterNot { it in renames.keys },
                rename = renames,
            )
        }
    }

    /**
     * هل يعود السؤال في العنصر التالي؟
     *
     * `Overwrite`/`Skip` المفردتان تُجيبان عن **عنصر واحد**، فالسؤال يعود؛ والنسختان
     * المعمَّمتان تُطفئان السؤال لأن المستخدم اختار ذلك صراحةً.
     */
    fun repeatsQuestion(choice: ConflictChoice): Boolean = when (choice) {
        ConflictChoice.Ask, ConflictChoice.Overwrite, ConflictChoice.Skip -> true
        else -> false
    }

    /** ترقية القرار المفرد إلى معمَّم حين يطلب المستخدم «طالما» في الشاشة. */
    fun forAll(choice: ConflictChoice): ConflictChoice = when (choice) {
        ConflictChoice.Overwrite -> ConflictChoice.OverwriteAll
        ConflictChoice.Skip -> ConflictChoice.SkipAll
        else -> choice
    }
}
