/*
 * Generic privileged filesystem access used by hardware discovery and control.
 * It deliberately knows nothing about a specific vendor or SoC.
 */
package nd.max.core.hardware

/**
 * `PEER-8` + `AR-31` — **لا نفترض أن الكتابة نجحت، نقرأ ونتحقّق**.
 *
 * ما كان قبل هذا الملف (مُتحقَّق من الكود، لا من الذاكرة): `RootFileAccess.write` كانت تعيد
 * «نجاح أمر الكتابة» فقط، وهذا **ليس** «القيمة وصلت». والنظير `uperf` يذكر صراحةً سطر سبب
 * التجاهل عند مسار غير قابل للكتابة، أي أن الفرق بين «أمر نجح» و«قيمة استقرّت» فرق معروف
 * في هذا المجال. وكان في المستودع موضع **واحد** يفعل التحقّق يدويًّا
 * (`TouchBoostViewModel`: `write(...) && read(...) == value`) — منتشر في موضع واحد وغائب في
 * العشرين الباقية.
 *
 * **ما تفعله هذه الدوال — ولا شيء غيره:** تقارن ما كتبناه بما قرأناه. **لا** تُصلح قيمة، **ولا**
 * تُعيد المحاولة، **ولا** تبدّل مسارًا. الحكم معلَن، والقرار يبقى للمتصل.
 */
object WriteVerification {

    /**
     * نتيجة كتابة **متحقَّقة**: أربع حالات، لا ثلاث ولا اثنتان.
     * الخلط بين «لم تُكتب» و«كُتبت لكن الجهاز زحزحها» هو بالضبط ما يُخفي حقائق العتاد.
     */
    enum class Outcome {
        /** كُتبت، والقيمة المقروءة تطابق المكتوبة. */
        MATCHED,

        /** كُتبت، لكن القيمة المقروءة مختلفة — **الجهاز قد يقيّد القيمة** (تدوير، حدّ، حاكم). */
        DIFFERS,

        /** أمر الكتابة نفسه فشل (مسار غير قابل للكتابة/صلاحية/خدمة غير متاحة). */
        WRITE_FAILED,

        /** كُتبت، لكن تعذّرت القراءة ⇒ **لا حكم**: لا نقول «نجحت» ولا «فشلت». */
        READBACK_UNAVAILABLE,
    }

    /** توحيد شكل القيمة قبل المقارنة: بلا فراغات طرفية ولا أسطر متعدّدة. */
    fun normalise(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    /** هل القيمتان العدد نفسه بصيغة نصّية مختلفة؟ (`0500000` و`500000` و`+500000`) */
    private fun sameNumber(wrote: String, readBack: String): Boolean {
        val a = wrote.trim().toLongOrNull() ?: return false
        val b = readBack.trim().toLongOrNull() ?: return false
        return a == b
    }

    /**
     * الحكم على قيمة مقروءة — **خالصة**، مُختبرة بلا جهاز.
     *
     * @param readBack `null` تعني **تعذّرت القراءة** (لا «صفر» وليست «قيمة مختلفة»).
     */
    fun compare(wrote: String, readBack: String?): Outcome {
        if (readBack == null) return Outcome.READBACK_UNAVAILABLE
        val a = normalise(wrote)
        val b = normalise(readBack)
        if (a == b || sameNumber(a, b)) return Outcome.MATCHED
        return Outcome.DIFFERS
    }

    /** سطر مختصر للعرض/السجل: `1 -> 3 (differs)`. */
    fun describe(wrote: String, readBack: String?, outcome: Outcome): String =
        "${normalise(wrote)} -> ${readBack?.let { normalise(it) } ?: "?"} (${verdictWord(outcome)})"

    /** كلمة الحكم بالإنجليزية الثابتة التي تظهر في السجل (بلا ترجمة: مُعرّف لا نصّ واجهة). */
    fun verdictWord(outcome: Outcome): String = when (outcome) {
        Outcome.MATCHED -> "matched"
        Outcome.DIFFERS -> "differs"
        Outcome.WRITE_FAILED -> "write_failed"
        Outcome.READBACK_UNAVAILABLE -> "unreadable"
    }
}
