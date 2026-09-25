package nd.max.core.jni

/**
 * جسر JNI لقارئ خصائص النظام الأصلي (Rust/bionic) — **قراءة فقط**.
 *
 * كل `Shell.cmd("getprop X")` كانت رحلة: ولادة عملية ثم تفسير صدفة **لكل سؤال**.
 * والقياس على المضيف (نفس مسطرة الجولة): **٢٣٦٦ ميكرو** عبر الصدفة مقابل **١٢٫٦ ميكرو**
 * داخليًّا (**×١٨٧**) — والخصائص تُسأل في مسارات ساخنة (مفتاح Max AI، محفظة الواجهة،
 * المراقب) فتضيع ملليّات في سؤال جوابه في ذاكرة العملية.
 *
 * **والمسار الأصلي أوثق لا أسرع فقط:** انعكاس `android.os.SystemProperties` سطحٌ مخفي
 * غير مضمون عبر الإصدارات، و`PropertyUtils.get` يرجع `def` **صامتًا** حين يُحجب؛
 * و`__system_property_get` في bionic سطحٌ ثابت في كل الإصدارات.
 *
 * **وحدّها المعلن:** * `null` = «اسأل غيري» (المكتبة غائبة، أو الاسم غير قابل للشحن)،
 * فيعود المتصل إلى الانعكاس/الصدفة سلوكَ اليوم.
 * * `""` = «الخصيصة غير مضبوطة» — **حكمٌ لا فشل** (وهو نصّ `getprop` نفسه لخصيصة غائبة)،
 * فلا يُخلط بالحالة الأولى: الخلط بينهما هو الفشل الصامت الذي جاءت هذه الوحدة تمنعه.
 *
 * ولا كتابة هنا: `setprop` يحتاج جذرًا وسياسة SELinux ويبقى على `PropertyUtils.set`
 * بالطريق المصرَّح (ADR-11).
 *
 * **ولا تسجيل عند غياب المكتبة:** `PredictorBridge`/`ProbeBridge` يسجّلان الحالة في
 * التشخيص بالفعل، وتسجيلها ثلاث مرّات يصنع ضجيجًا يوهم بعطب واحد متكرّر.
 */
object PropBridge {

    /** هل حُمِّلت المكتبة الأصلية؟ غيابها **ليس عطبًا** — المتصل يواصل بالاحتياطي المصرَّح. */
    val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("maxmanager_native")
    }.isSuccess

    private external fun nativeGetProp(name: String): String?

    private external fun nativeGetPropsPacked(packedNames: String): String?

    /**
     * هل الاسم قابل للشحن إلى الأصلي؟
     *
     * دالّة **نقيّة** (تُختبر بلا مكتبة أصلية على JVM): الاسم الفارغ أو الحاوي سطرًا جديدًا
     * يُرفض، فيعود المتصل إلى الاحتياطي بدل أن يقرأ قيمة باسم مُشوَّه — والحزمة لا تحمل
     * سطرًا جديدًا داخل اسم.
     */
    internal fun shippableName(name: String): Boolean =
        name.isNotBlank() && !name.contains('\n')

    /**
     * قراءة خصيصة واحدة.
     *
     * @return `null` يعني «اسأل غيري»؛ و`""` تعني «غير مضبوطة» (لا فشل).
     */
    fun get(name: String): String? {
        if (!shippableName(name)) return null
        if (!nativeAvailable) return null
        return runCatching { nativeGetProp(name) }.getOrNull()
    }

    /**
     * قراءة عدة خصائص في نداء أصلي واحد (نفس الترتيب).
     *
     * العائد `null` يعني «اسأل غيري»: المكتبة غائبة، أو الأسماء لا تُشحن بأمان، أو الحزمة
     * عادت بعدد أسطر مخالف — وهو ما يستحيل تفسيره بمحاذاة مخمَّنة.
     * وكل سطر فارغ في العائد يعني «هذه الخصيصة غير مضبوطة».
     *
     * والعائد `List<String>` لا `List<String?>?` عناصر: «غير مضبوطة» تُترجم إلى `""` **هنا**
     * لأنها حكم، ولا يُترك `null` يمشي في المتصلين فيُجبرهم على `?: ""` في كل موضع — وهو
     * الصنف الذي أمسكه المُصرّف فعلًا في `PerAppRefreshRateController` (سطر `raw.uppercase`).
     */
    fun getAll(names: List<String>): List<String>? {
        if (names.isEmpty()) return emptyList()
        if (!nativeAvailable || !ProbePacket.packable(names)) return null
        val packed = runCatching {
            nativeGetPropsPacked(ProbePacket.packPaths(names))
        }.getOrNull() ?: return null
        if (packed.isEmpty()) return null
        val values = ProbePacket.unpackValues(packed, names.size) ?: return null
        if (values.size != names.size) return null
        // سطر فارغ هنا = «غير مضبوطة» لا «محجوبة»: الخصائص لا تُحجب عن uid التطبيق.
        return values.map { it ?: "" }
    }
}
