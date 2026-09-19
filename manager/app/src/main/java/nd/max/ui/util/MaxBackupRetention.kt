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

package nd.max.ui.util

/**
 * سياسة الاحتفاظ: أي النسخ تُقلَّم — أي **تُحذف بلا سؤال**.
 *
 * ولماذا في ملف خالص بلا Android ولا `Context`: هذه هي القاعدة الوحيدة في Max Backup التي
 * تمحو بيانات المستخدم من تلقاء نفسها، وكانت قبل اليوم `fun` على نموذجٍ يستورد `org.json`
 * فلا تُترجم إلا على جهاز — أي **قاعدة تمسح ملفات لا يقيسها شيء**. صارت هنا حتى تُقاس في
 * اختبار JVM عادي، والقاعدة كما هي:

 *  1. النسخة المعلَّمة «تُحفَظ» **لا تُحذف أبدًا**، ولا تُحسب من رصيد الاحتفاظ: علامتها
 *     ليست ترتيبًا زمنيًّا بل قرارًا للمستخدم — أن يبقى هذا السطر بعد عشرة تقليمات.
 *  2. ومن غير المعلَّم يبقى أحدث `keep`.
 *  3. و**حدّ أدنى إلزامي**: `keep` أصغر من ١ تُرفع إلى ١. أداةُ نسخ احتياطي تحذف آخر نسخة
 *     عندها ليست أداة مضبوطة الإعداد بل فقدان بيانات مؤجَّل.
 *  4. وترتيب متعادل في الطابع الزمني ترتيب مستقرّ: المقارنة بالزمن وحده، فلا تُحذف نسخة
 *     «بالحظّ» لأن الفرز غير مستقرّ.
 */
object MaxBackupRetention {

    /** ما يلزم القرار عن النسخة — لا مجلدها ولا مستندها. */
    data class Copy(
        val folder: String,
        val createdAtMs: Long,
        /** «تُحفَظ للأبد»: لا تُقلَّم. */
        val kept: Boolean,
    )

    /** النسخ التي يجب أن تُحذف عند الاحتفاظ بأحدث `keep` من غير المعلَّم. */
    fun toRemove(copies: List<Copy>, keep: Int): List<Copy> {
        val effective = keep.coerceAtLeast(1)
        return copies
            .sortedByDescending { it.createdAtMs }
            .filterNot { it.kept }
            .drop(effective)
    }
}
