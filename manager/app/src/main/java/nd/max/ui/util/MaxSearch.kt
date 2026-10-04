/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * طيّ نصّ البحث — **دالّة واحدة لكل لغات التطبيق** (٨٥ لغة، لا العربية وحدها).
 *
 * ولماذا دالّة مشتركة لا `contains(ignoreCase = true)` في كل شاشة:
 *
 * 1. **المستخدم لا يكتب ما كتبناه.** نصّ الواجهة «الإعدادات» و«الحرارة» و«السّجِل»، والمستخدم
 *    يكتب «الاعدادات» و«الحراره» و«السجل» بلا همزة ولا تاء مربوطة ولا تشكيل — لأن لوحة
 *    مفاتيحه لا تُخرِجها أو لأنه لا يعرف موضعها. ومقارنةٌ خامّة تُرجع «لا نتيجة» لمن كتب نصًّا
 *    صحيحًا في لغته، وهو أسوأ شكل من «لا نتيجة»: يبدو كأنّ الشاشة **غير موجودة**.
 * 2. **والحروف اللاتينية كذلك:** `café` لا تُطابق `cafe`، و`Konfiguration` لا تُطابق
 *    `konfiguration` بلا خفض حالة. والـ٨٤ لغة الأخرى تُقرأ بفكّ NFKD نفسه، لأن كل لغة هنا نصٌّ
 *    في `values-*` مكتوب بحروفها هي لا بحروف العربية مرموزة.
 * 3. **والمطابقة موقعٌ لا وجود فقط:** ترتيب النتائج يحتاج أن يعرف **أين** وقعت الكلمة — في أوّل
 *    العنوان أم عند بداية كلمة داخله أم في وسطه — وإلا جاءت نتيجةٌ هامشية (تطابق في سطر
 *    الوصف) فوق الشاشة التي اسمها هو نفسه ما كتبه المستخدم.
 *
 * والصافي هنا لا يعتمد على Compose ولا على `Context`: يُقاس على JVM في `ScreenFinderTest`
 * بأمثلة حقيقية (الهمزات · التاء المربوطة · التشكيل · التطويل · الحالة · العلامات اللاتينية).
 */
package nd.max.ui.util

import java.text.Normalizer

/**
 * يطوي [text] إلى صورة واحدة قابلة للمقارنة: بلا تشكيل، وبلا تطويل، وبأحرف أساسيّة موحَّدة،
 * وبحالة واحدة، وبفراغات مطويّة.
 *
 * @return نصًّا مطويًّا؛ وفراغٌ لفراغ، فلا يحتاج المنادي حارسًا.
 */
fun maxSearchFold(text: String): String {
    if (text.isEmpty()) return ""

    // NFKD أولًا: يفكّ المركّب إلى أساسيّ + علامة، فتسقط علامات اللاتينية (é ⇒ e) والتشكيل
    // العربي (الحرف نفسه + حركة) بالمُسطَّر نفسه: كلّها علامات (`Mn`).
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
    val out = StringBuilder(decomposed.length)
    var pendingSpace = false

    for (raw in decomposed) {
        // التطويل (ـ) حرفُ مدٍّ لا يُقرأ؛ كتابته في «السـجل» لا تغيّر الكلمة.
        if (raw == '\u0640') continue
        val ch = when (raw) {
            // توحيد الحروف التي تختلف باختلاف لوحة المفاتيح لا باختلاف الكلمة.
            'ة' -> 'ه'
            'ى' -> 'ي'
            'ؤ' -> 'و'
            'ئ' -> 'ي'
            'ک' -> 'ك'
            'ی' -> 'ي'
            'ے' -> 'ي'
            'ہ', 'ھ' -> 'ه'
            else -> raw
        }
        if (ch.isCombiningMark()) continue
        if (ch.isWhitespace()) {
            // الفراغ يُؤجَّل ولا يُكتب الآن: الكلمتان تتفصلان بفراغ واحد مهما تكرّر في الأصل.
            if (out.isNotEmpty()) pendingSpace = true
            continue
        }
        if (pendingSpace) {
            out.append(' ')
            pendingSpace = false
        }
        // `lowercaseChar` بسيط ولا يعتمد على لغة الجهاز: التركية تُبدّل `i` في `Locale` الخاصّ
        // بها، فكان بحث المستخدم يتغيّر مع لغة الهاتف بدل أن يتغيّر مع النصّ.
        out.append(ch.lowercaseChar())
    }
    return out.toString()
}

/**
 * رتبة مطابقة [query] في [haystack] — وأصغرُ رقمٍ أفضل، و`-1` تعني: لا مطابقة.
 *
 * | الرتبة | المعنى |
 * | --- | --- |
 * | `0` | النصّ هو الكلمة نفسها |
 * | `1` | يبدأ بها النصّ (`حرار` ⇒ «الحرارة») |
 * | `2` | تبدأ بها **كلمة** داخله (`نواة` ⇒ «تحكّم النواة») |
 * | `3` | وقعت في وسطه |
 *
 * والدالّة تطوي الطرفين بنفسها، فلا يمكن أن تُقارَن صورة مطويّة بصورة خام — وهو الخطأ الذي
 * يجعل البحث يعمل في اختبار ويفشل في شاشة.
 */
fun maxSearchRank(haystack: String, query: String): Int {
    val needle = maxSearchFold(query).trim()
    if (needle.isEmpty()) return -1
    return maxSearchRankFolded(haystack, needle)
}

/**
 * نفس [maxSearchRank] لكن بإبرة **مطويّة سلفًا** — لمن يقارن نصًّا واحدًا بمئات النصوص
 * (فهرس الشاشات)، فلا تُطوى كلمة البحث مئة مرّة في كل ضغطة مفتاح.
 */
fun maxSearchRankFolded(haystack: String, foldedNeedle: String): Int {
    if (foldedNeedle.isEmpty()) return -1
    val folded = maxSearchFold(haystack)
    if (folded.isEmpty()) return -1

    val at = folded.indexOf(foldedNeedle)
    if (at < 0) return -1
    return when {
        folded.length == foldedNeedle.length -> 0
        at == 0 -> 1
        folded.isWordStart(at) -> 2
        else -> 3
    }
}

/**
 * هل تقع [at] على بداية كلمة في [text] المطويّ؟
 *
 * وفراغٌ أو رقم قبله بدايةٌ بالبديهة، **و"ال" التعريف أيضًا**: العربية تلصق أدواتها بالكلمة
 * («النواة» · «وبالحرارة»)، فمطابقةٌ بعدها بدايةُ كلمة لا وسطها. وبلا هذا السطر يُرتَّب كل بحث
 * عربي في المرتبة الأخيرة (تطابقٌ في الوسط) فيفقد الترتيب معناه.
 */
private fun String.isWordStart(at: Int): Boolean {
    if (at <= 0) return true
    val before = this[at - 1]
    if (!before.isLetterOrDigit()) return true
    if (before != 'ل') return false
    var index = at - 2
    while (index >= 0 && this[index] in ARABIC_PREFIX_LETTERS) index--
    return index < 0 || !this[index].isLetterOrDigit()
}

/** «و ف ب ك» تلتصق بـ«ال» التعريف: «وبالحرارة» مطابقةٌ فيها بدايةُ كلمة. */
private const val ARABIC_PREFIX_LETTERS = "اوفبك"

/** علامة مركّبة تُرسم على الحرف السابق ولا تُنطق وحدها (تشكيل عربي أو علامة لاتينية). */
private fun Char.isCombiningMark(): Boolean = when (Character.getType(this).toByte()) {
    Character.NON_SPACING_MARK,
    Character.COMBINING_SPACING_MARK,
    Character.ENCLOSING_MARK,
    -> true
    else -> false
}
