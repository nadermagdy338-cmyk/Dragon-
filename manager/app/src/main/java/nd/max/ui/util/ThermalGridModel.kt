package nd.max.ui.util

/**
 * اختيار مجسّات الشبكة الحرارية على الرئيسية — نموذج **خالص** يُقاس في JVM بلا Compose
 * (نفس نمط `UnifiedActivityModel`: القرار هنا، والواجهة تعرضه، والمخزن يحفظه).
 *
 * وهذا هو «اختيار المجسّات» الذي يفعله DevCheck عند لمس بطاقة الحرارة: الجهاز قد يعلن
 * عشرات المناطق (`CPU` · `GPU` · `Skin` · `Battery` · `Charger` · `Modem` …)، وأربعة منها
 * فقط تسعها شبكة الشاشة — فالاختيار ملك المستخدم لا ملك الكود.
 *
 * وثلاث قواعد كلّية، كلّها مقيسة هنا:
 *
 *  1. **الحدّ أربع فئات** ([LIMIT]): الشبكة صفّ واحد بلاطة كل واحدة، ولا صفّ نصفه فارغ.
 *  2. **لا تنفرغ الشبكة**: آخر مثبَّت لا يُلغى — شبكة بلا مجسّ تعبئةً لفراغ لا رسالة.
 *  3. **المحفوظ التالف يُطهَّر لا يُكسر**: رمز مجهول يسقط، والتكرار يُزاح، وتجاوز الحدّ يُقطع،
 *     والفراغ يُرجع [DEFAULT].
 */
object ThermalGridModel {

    /** أقصى عدد مجسّات مثبَّتة — شبكة الشاشة صفّ واحد بلاطاته أربع. */
    const val LIMIT: Int = 4

    /** التثبيت الافتراضي: ما كانت الشاشة تعرضه قبل ظهور الاختيار. */
    val DEFAULT: List<String> = listOf("CPU", "GPU", "Skin", "Battery")

    /**
     * رموز فئات `ThermalUtil.classifyZone` — حصرٌ مطابق للمصدر. وما ليس هنا ليس فئة،
     * ولو جاء في ملف محفوظ من إصدار مختلف.
     */
    val KNOWN: List<String> = listOf(
        "CPU", "GPU", "Skin", "Battery", "Charger", "Modem", "WiFi", "Camera", "Flash", "PA", "System",
    )

    /** تطبيع المحفوظ: مجهول يسقط، وتكرار يُزاح، وتجاوز الحدّ يُقطع، وفراغ يُرجع [DEFAULT]. */
    fun normalize(stored: List<String>): List<String> {
        val cleaned = stored
            .map { it.trim() }
            .filter { it in KNOWN }
            .distinct()
            .take(LIMIT)
        return if (cleaned.isEmpty()) DEFAULT else cleaned
    }

    /**
     * تثبيت/فكّ تثبيت فئة واحدة: لا يزيد على [LIMIT] (فالمثبَّت الأقدم يبقى والجديد يُرفض)،
     * ولا ينفرغ التحديد (آخر مثبَّت يبقى). ورمز مجهول لا يغيّر شيئًا.
     */
    fun toggle(current: List<String>, key: String): List<String> {
        if (key !in KNOWN) return current
        return if (key in current) {
            if (current.size <= 1) current else current - key
        } else {
            (current + key).take(LIMIT)
        }
    }

    /**
     * خيارات الالتقاط مرتبة بترتيب [KNOWN]: الافتراضي دائمًا حاضر (له قراءات بتراجعات)،
     * وما عداه يظهر **فقط إن كانت هذه النواة تقيسه فعلًا** — لا صفوف تقول `—` بلا معنى.
     */
    fun options(present: Collection<String>): List<String> = KNOWN.filter { it in DEFAULT || it in present }
}
