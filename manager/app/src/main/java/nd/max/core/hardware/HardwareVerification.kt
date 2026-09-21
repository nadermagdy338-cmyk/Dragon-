package nd.max.core.hardware

/**
 * حكم «هل تحقّق الطلب؟» — منفصل عن التطبيق نفسه وعن المُحكِّم، لأنه **دلالة** لا ميكانيكا.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * كان المُحكِّم يحكم على كل معاملة بتساوٍ حرفيّ: المطلوب = المقروء، وإلا فهو «فشل» ثم
 * استرجاع لخط الأساس. وهذا صحيح لِما يُقصد به التثبيت الحرفيّ (حاكم، وضع قفل)، وخاطئ
 * لِما يُقصد به **الحدّ الأعلى**: طلبُ سقفٍ 1.3GHz على جهاز قيّدته سياسة الـvendor عند
 * 754MHz لم يكن يُقرأ «مُلبّى» بل «فاشل» — والقياس الحقيقي في `HANDOFF.md` حرفيّ:
 * `APPLY_VERIFY_FAILED knob=gpu_profile expected=1300000000 live=754000000`.
 * فالطلب كان يُكتب، ثم يُقرأ أقلّ، ثم **يُرجَع خط الأساس**، فيرى المستخدم مقبضًا يرتدّ
 * بلا سبب. والحقيقة أن الجهاز **لبّى** ما طُلب منه (لا يتجاوز السقف) فلم يكن هناك ما يُصلح،
 * وكانت إعادة الكتابة كل دورة انحراف خفقانًا في وجه مُلطِّف لا يقبل أن يُقنَع.
 *
 * والقاعدة التي تُشتقّ من ذلك: **الحكم يُبنى على دلالة الطلب**، والسقف لا يعني «ساوِ»
 * بل «لا تتجاوز»، والمدى لا يعني «ساوِ» بل «ابقَ داخله». أمّا تثبيت قيمة واحدة فيبقى
 * تساويًا حرفيًّا، لأن معناه الوحيد هو التساوي.
 *
 * و[exact] هو الافتراض في كل مسار لم يُذكر فيه غيره، فلم يتغيّر سلوك أي كاتب قائم.
 */
object HardwareVerification {

    /** التساوي الحرفي — الافتراض: المقروء يجب أن يكون المطلوب نفسه. */
    val exact: (String, String?) -> Boolean = { desired, actual -> actual != null && actual == desired }

    /**
     * طلب **سقف**: القيمة المقروءة صحيحة إن كانت معلومة، موجبة، ولا تتجاوز المطلوب.
     *
     * ولا يُقبل صفر أو سالب: صفرٌ في عقدة تردد ليس «داخل السقف» بل دليل قراءة فاشلة،
     * وقبوله يجعل انعدام القياس يمرّ نجاحًا — وهو أسوأ من الفشل لأنه لا يُرى.
     * والقيمة المجهولة/غير الرقمية تُحال إلى [exact] كي لا يُبتلع طلبٌ ذو صيغة غير رقمية.
     */
    fun ceilingAtMost(desired: String, actual: String?): Boolean {
        val wanted = desired.toLongOrNull() ?: return exact(desired, actual)
        val live = actual?.toLongOrNull() ?: return false
        return live > 0L && live <= wanted
    }

    /**
     * طلب **مدى** بصيغة `min:max` (أي حقل يجوز أن يكون فارغًا).
     *
     * والمنطق:
     *  - `min:max` متساويان (وضع القفل) ⇒ يُطلب التساوي في الحدّ الأعلى؛ لأن معنى القفل
     *    «ثبّت هذه القيمة»، ومدى أضيق منها ليس تلبيةً له بل قيمة أخرى.
     *  - مدى حقيقي ⇒ المطلوب أن يقع المقروء **داخله**: أرضية المقروء لا تنزل تحت المطلوب
     *    (الحدّ الأدنى مطلوب «على الأقل»)، وسقف المقروء لا يعلوه. وارتفاع أرضية العتاد عن
     *    الطلب ليس فشلًا: سياسة الـvendor ترفع الأرضية ولا يعنينا منعها.
     *  - وصيغة غير رقمية ⇒ [exact]، فلا يُخمَّن معنى نصّ لا نفهمه.
     */
    fun rangeContained(desired: String, actual: String?): Boolean {
        val want = parsePair(desired) ?: return exact(desired, actual)
        val live = parsePair(actual ?: return false) ?: return false
        val wantMin = want.first
        val wantMax = want.second
        if (wantMin != null && wantMax != null && wantMin == wantMax) {
            return live.second == wantMax
        }
        if (wantMin != null && live.first != null && live.first!! < wantMin) return false
        if (wantMax != null && live.second != null && live.second!! > wantMax) return false
        // مدى بلا أي حدّ مقروء لا يُثبت شيئًا — لا يُدّعى تلبية بلا قياس.
        return live.first != null || live.second != null
    }

    private fun parsePair(value: String): Pair<Long?, Long?>? {
        val parts = value.split(':', limit = 2)
        if (parts.size != 2) return null
        val min = parts[0].trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        val max = parts[1].trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        // ولا يُقبل نصّ غير رقمي في أي من الحقلين: صيغةٌ لا نعرف معناها تُحال إلى التساوي.
        if (parts[0].trim().isNotEmpty() && min == null) return null
        if (parts[1].trim().isNotEmpty() && max == null) return null
        if (min == null && max == null) return null
        return min to max
    }
}
