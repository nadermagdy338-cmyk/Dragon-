/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

/**
 * ضغط الذاكرة المقيس — **التوقف** لا الامتلاء.
 *
 * لماذا مقياس آخر للذاكرة ونحن نقرأ نسبة الامتلاء أصلًا: لأن الامتلاء لا يقول
 * شيئًا عن الأثر. جهاز بـ٩٠٪ ذاكرة مستخدمة قد يكون سريعًا تمامًا، وآخر بـ٦٠٪ قد
 * يكون في خنق شديد. والمقياس الذي يقيس الأثر فعلًا هو ما بنى عليه نظام أندرويد
 * نفسه قراره: ملفات PSI في `/proc/pressure/{cpu,memory,io}` تصدّر لكل مورد
 * سطرين — `some` (نسبة الوقت الذي توقف فيه **بعض** العمل) و`full` (توقف فيه
 * **كل** العمل غير الخامل، أي thrashing حقيقي) — و`lmkd` مبني على هذه الملفات.
 * المصدر: `docs/ai/EXTERNAL-RESEARCH.md` §٢ (XR-06) — فكرة مُعاد تصميمها على
 * قيودنا، لا كود منقول.
 *
 * وثلاث قواعد قبول صريحة ومُختبَرة:
 *
 *  ① **لا رقم جزئي يُعرض كقياس كامل:** إن غاب سطر `full` فالنواة لا تصدّره
 *     (PSI غير مدعوم أو مطفأ)، والنتيجة `UNSUPPORTED` — لا صفرًا ولا `some`
 *     مكانه. عرض `some` في خانة `full` كان سيقلب الحقيقة: `some` ترتفع عند أي
 *     انتظار عادي، و`full` لا ترتفع إلا عند خنق حقيقي.
 *  ② **الجهل ليس خنقًا:** غير المدعوم لا يوقف أي قرار (انظر [isThrashing])؛
 *     منع النشاط بسبب إشارة غائبة كان سيوقف المحرك على أنظمة قديمة بلا سبب.
 *  ③ **النص الغريب يُرفض:** أي سطر لا يُقرأ رقمًا صحيحًا في النافذة المطلوبة
 *     يُسقط الجواب إلى `UNSUPPORTED` بدل أن يُمرَّر رقم مشوّه إلى قرار.
 *
 * والوحدة نقية تمامًا: لا Android ولا قراءة ملفات — النص يُمرَّر إليها، فتُختبر
 * على نصوص PSI حقيقية بقيم صريحة. القارئ الفعلي في `core/hardware`.
 */
object MemoryStall {

    /**
     * هل توفّرت إشارة ضغط الذاكرة؟ `UNSUPPORTED` حكم على **النواة/الإعداد** لا
     * على الجهاز: نواة بلا PSI أو بلا صلاحية قراءة تُعطي هذه القيمة إلى الأبد.
     */
    enum class Availability { MEASURED, UNSUPPORTED }

    /**
     * عيّنة PSI واحدة. النسبان مخزَّنان **بالمئة (0..100)** كما في الملف، لأن
     * هذا ما يُعرض للمستخدم؛ والكسر (0..1) يُشتق منهما حيث يلزم للقرار.
     *
     * @param somePercent نسبة الوقت الذي توقف فيه بعض العمل (إشارة سياق).
     * @param fullPercent نسبة الوقت الذي توقف فيه كل العمل غير الخامل — **هذا
     *        هو الخنق الحقيقي**، وعليه وحدَها تُبنى الأحكام.
     * @param windowSeconds النافذة التي قُرئ منها الرقمان (ثوانٍ).
     */
    data class Sample(
        val availability: Availability = Availability.UNSUPPORTED,
        val somePercent: Float? = null,
        val fullPercent: Float? = null,
        val windowSeconds: Int = 0,
    ) {
        val measured: Boolean get() = availability == Availability.MEASURED

        /** كسر الخنق الكامل (0..1) أو null إن لم يُقَس. */
        val fullFraction: Float? get() = fullPercent?.div(100f)

        /** كسر التوقف الجزئي (0..1) أو null إن لم يُقَس. */
        val someFraction: Float? get() = somePercent?.div(100f)
    }

    /** القيمة الوحيدة التي تعني «لم يُقَس» — لا صفر ولا تقدير. */
    val UNSUPPORTED = Sample()

    /**
     * يحلّل نص `/proc/pressure/memory` لنافذة واحدة.
     *
     * الشكل المتوقّع (نواة لينكس):
     * ```
     * some avg10=0.00 avg60=0.00 avg300=0.00 total=0
     * full avg10=0.00 avg60=0.00 avg300=0.00 total=1234
     * ```
     * والمحلّل متسامح مع الترتيب والمسافات والحقول الإضافية (`total`) وحقول
     * مجهولة، ومتشدد في ثلاثة أشياء فقط: النوع (`some`/`full`)، وجود النافذة
     * المطلوبة، وصحة الرقم.
     */
    fun parse(text: String?, windowSeconds: Int = DEFAULT_WINDOW_SECONDS): Sample {
        if (text.isNullOrBlank()) return UNSUPPORTED

        var some: Float? = null
        var full: Float? = null

        text.lineSequence().forEach { rawLine ->
            val tokens = rawLine.trim().split(WHITESPACE)
            if (tokens.size < 2) return@forEach
            val kind = tokens.first()
            if (kind != "some" && kind != "full") return@forEach

            val selected = tokens.drop(1)
                .firstOrNull { it.startsWith(AVG_PREFIX + windowSeconds + "=") }
                ?.substringAfter('=')
                ?.toFloatOrNull()
                // نان أو ما لا نهائي من نواة شاذة ليسا قياسًا.
                ?.takeIf { it.isFinite() }
                ?: return@forEach

            // الملف يصدّر بالمئة؛ ما خرج عن المدى مشوّه يُقصّ إلى الحدّ المعقول
            // بدل أن يصبح رقمًا مستحيلًا في قرار.
            val clamped = selected.coerceIn(0f, 100f)
            if (kind == "some") some = clamped else full = clamped
        }

        if (some == null || full == null) return UNSUPPORTED
        return Sample(
            availability = Availability.MEASURED,
            somePercent = some,
            fullPercent = full,
            windowSeconds = windowSeconds,
        )
    }

    /**
     * هل هناك خنق ذاكرة حقيقي الآن؟ يُقاس على `full` وحدها.
     *
     * `null` (غير مدعوم أو لم يُقَس) يعني **لا**، بقاعدة صريحة: الجهل لا يُنتج
     * منعًا. وهذا هو الفرق بين «لا أعرف» و«لا يوجد».
     */
    fun isThrashing(fullFraction: Float?): Boolean =
        fullFraction != null && fullFraction >= STALL_FRACTION

    /** النافذة الافتراضية — ١٠ ثوانٍ: أقصر ما يصدّره PSI، فالتغيّر يُرى بسرعة. */
    const val DEFAULT_WINDOW_SECONDS = 10

    /**
     * عتبة «خنق حقيقي»: `full avg10 ≥ 10٪`.
     *
     * المعنى حرفيًا: في عُشر زمن آخر عشر ثوانٍ توقّف **كل** العمل غير الخامل
     * منتظرًا الذاكرة. ما دونها استرجاع عادي لا يستحق تغيير قرار؛ وما فوقها
     * يعني أن الجهاز يُنفق طاقة في الانتظار لا في العمل.
     *
     * **غير معايَرة على جهاز** (مسجَّلة في `KNOWN_ISSUES.md`).
     */
    const val STALL_FRACTION = 0.10f

    private const val AVG_PREFIX = "avg"
    private val WHITESPACE = Regex("\\s+")
}
