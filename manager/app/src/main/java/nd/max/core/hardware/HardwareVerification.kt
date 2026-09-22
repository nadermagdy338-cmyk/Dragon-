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
 *
 * وحكمان لا حكم واحد، لأن سؤالين لا سؤال واحد:
 *
 * | الحكم | سؤاله | يُستعمل في |
 * | --- | --- | --- |
 * | [exact] · [ceilingAtMost] · [rangeContained] | «هل الحالة المقروءة **مقبولة** فلا يُسترجع خط الأساس؟» | تحقق المعاملة ونافذة التأكيد |
 * | [ceilingReached] | «هل القراءة الحيّة **دليل** على أن طلبنا نُفِّذ؟» | قرار **الكتابة** في [HardwareControlArbiter] |
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

    /**
     * هل **بلغت** القيمة المقروءة ما طلبناه؟ — سؤال *الكتابة*، لا سؤال *التلبية*.
     *
     * لماذا حكم ثالث غير [ceilingAtMost] و[rangeContained]
     * --------------------------------------------------
     * الحكمان السابقان **متسامحان بطبيعتهما**: سقفٌ حيٌّ أدنى من الطلب يُقرأ مُلبًّى (`live ≤ wanted`).
     * وهذا صحيح لِما لا نملكه، وخاطئ لِما **نملكه نحن**: عقدة `max_freq` نحن من كتبها في الخطوة
     * السابقة، فقيمةٌ ٥٢٠ كتبناها بأنفسنا لبروفايل «power» تُقرأ لطلبٍ ٧٠٢ «مُلبّاة» — فلا تُكتب
     * أبدًا، ويبقى الجهاز على ٥٢٠ كلّما حاول المستخدم الرفع.
     *
     * والقياس (rodin · MT6899 · 2026-09-22) حرفيّ: بروفايلات `gaming`/`balanced`/`power` كتبت
     * `max_freq` فعلًا (`WRITE_CHECK … mali/max_freq wrote=1092000000/780000000/520000000 matched`)،
     * ثم طلبٌ عند ٧٠٢ **بلا أي كتابة** على العقدة، ومع ذلك `PERAPP_GPU_REALIZED … clock_now`
     * و`PERAPP_COMMIT … applied=true verified=true live=520000000`. أي أن المقبض كان يُعلن نجاحًا
     * وقيمته الحيّة هي **خفضُنا السابق** لا قمعٌ من المنصّة. وهذا هو «الترددات تنقص ولا تزيد عمّا نقص»
     * في per-app و«max ai» و«gpu» معًا: العطب واحد، وأثره في كل مقبض سقف.
     *
     * فالسؤال هنا: هل القراءة الحيّة **دليل** على أن طلبنا نُفِّذ؟ الجواب: سقف القراءة ≥ المطلوب.
     * و`false` ليست فشلًا ولا استرجاعًا — تعني «اكتب أولًا»، والحكم بعد الكتابة يبقى بيد
     * [ceilingAtMost]/[rangeContained] كما كان (فتسامحُهما هو ما يمنع الاسترجاع المدمِّر حين
     * تحتفظ المنصّة بسقف أدنى حقًّا).
     *
     * وما لا يُقاس لا يُجبر كتابة: صيغة غير مفهومة أو حقل غير رقمي ⇒ `true`، فيبقى سلوك أي مقبض
     * لا نعرف صيغته على ما كان بلا ضجيج كتابة جديد.
     */
    fun ceilingReached(desired: String, actual: String?): Boolean {
        val want = ceilingOf(desired) ?: return true
        val live = ceilingOf(actual ?: return true) ?: return true
        return live >= want
    }

    /**
     * سقف القيمة في أي من صيغ هذا المشروع الثلاث: `min:max` (مدى)، أو `node|…` (قراءة سقف
     * مُرمَّزة من `GpuCeilingPolicy.CeilingReading`)، أو رقم مجرّد (تردد فعلي).
     *
     * و«سقف» هو **آخر** رقم في مدى (الحقل الثاني)، وأول حقل في القراءة المرمَّزة. وحقل غير رقمي
     * يعني «لا قياس» (`null`) لا صفرًا — فصفرٌ في عقدة تردد دليل قراءة فاشلة كما في [ceilingAtMost].
     */
    private fun ceilingOf(value: String): Long? {
        val head = value.substringBefore('|').trim()
        val field = if (head.contains(':')) head.substringAfterLast(':').trim() else head
        return field.takeIf(String::isNotEmpty)?.toLongOrNull()
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
