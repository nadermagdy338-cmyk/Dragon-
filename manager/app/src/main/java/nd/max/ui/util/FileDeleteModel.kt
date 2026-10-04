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
 * `UX-06 ①` — **هدف الحذف وحسابه**: العدّ، وجمع الأحجام المعروفة، وإعلان المجهول.
 *
 * **والفجوة التي يُغلقها موصوفة في مواصفة الشاشة نفسها** (`mt-file-manager-spec` §10.5):
 * «Amaze يعدّ **ويجمع**، ونحن لا نفعله: حوارنا يقول (حذف N عنصرًا) عددًا فقط ⇒ فجوة حقيقية
 * مسجَّلة». وحوار حذف نهائيّ بصلاحية الجذر **يجب** أن يقول الثمن قبل التأكيد لا بعده.
 *
 * **وما لا يُجمَع يُعلن ولا يُصفَّر.** الحجم مجهول في حالتين، وكلتاهما ليست فشلًا:
 *
 * 1. **المجلد**: قارئنا يعرف أنّه مجلد ولا يعدّ محتواه (وعدّ شجرة كاملة في حوار تأكيد
 *    كلفة تُدفع في كل فتح). فمجلد داخل التحديد = حجم مجهول — لا صفر.
 * 2. **ملفّ لم يُقرأ حجمه**: قد يعطي القارئ حجمًا للاسم وحده. وهذا كذلك مجهول.
 *
 * ولذلك يعرض الحوار «الحجم الإجماليّ» **فقط** إذا كان كل عنصر حجمه معروف؛ وإلا قال
 * «الحجم غير معروف لـN من العناصر» — وهو أصدق من رقم يظنّه المستخدم كاملًا.
 *
 * والملفّ النموذجيّ خالص: لا shell ولا Compose ولا `R`، فيُقاس على JVM.
 */
package nd.max.ui.util

/**
 * ما سيُحذف: مساراته، ومجموع أحجامه **المعروفة**، وعدد ما جهل حجمه.
 *
 * و`paths` تُحفظ كما رُتّبت في القائمة، لأن الطلب يُبنى منها مباشرةً (وتمرّ بحرس العمليات
 * كما هي — لا مسار ثانٍ للحذف).
 */
data class FileDeleteTarget(
    val paths: List<String>,
    val knownBytes: Long,
    val unknownCount: Int,
) {
    val count: Int get() = paths.size

    /**
     * هل الحجم الإجماليّ معلوم **كاملًا**؟ وهذا شرط عرضه لا شرط حسابه: مجموع ناقص يُقرأ
     * كأنه كامل، فلا يُعرض.
     *
     * وشرط `count > 0` مقصود: مجموعة فارغة مجموعها صفر رياضيًّا، وعرض «0 B» لهدف حذف
     * فارغ رقمٌ بلا معنى — ولا يقع أصلًا لأن أمر الحذف لا يُتاح على تحديد فارغ.
     */
    val sizeKnown: Boolean get() = count > 0 && unknownCount == 0

    /** الحجم الذي يُعرض، أو `null` («غير معروف») — ولا يُكمَّل بصفر. */
    val sizeBytes: Long? get() = if (sizeKnown) knownBytes else null

    val isEmpty: Boolean get() = paths.isEmpty()
}

object FileDeleteTargets {

    /**
     * بناء الهدف من المدخلات المحدَّدة.
     *
     * والحجم السالب يُعامل كمجهول لا كصفر: القارئ لا يُنتج حجمًا سالبًا، فإن وصل فهو أثر
     * قراءة خاطئة — وردّه إلى المجهول أصدق من جمعه في المجموع.
     */
    fun of(entries: List<FileEntry>): FileDeleteTarget {
        var known = 0L
        var unknown = 0
        val paths = ArrayList<String>(entries.size)
        for (entry in entries) {
            paths += entry.path
            val size = entry.sizeBytes
            if (size != null && size >= 0L) known += size else unknown++
        }
        return FileDeleteTarget(paths = paths, knownBytes = known, unknownCount = unknown)
    }
}
