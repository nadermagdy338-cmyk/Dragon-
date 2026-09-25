package nd.max.core.jni

/**
 * جسر JNI لقارئ الدفعات الأصلي (Rust) — **قراءة فقط**.
 *
 * كل عقدة قبل هذا كانت رحلةً: إما معاملة binder كاملة عبر `MtkRootService.readNode`، وإما
 * صدفة `cat`. والمسح الحراري يقرأ عقدتين لكل منطقة من عشرات المناطق، ودورة الرئيسية كل
 * ثانيتين ⇒ مئات الرحلات لكل دقيقة. هذه الوحدة تقرأ الملفات داخل العملية في **نداء واحد**.
 *
 * **وحدّها المعلن:** ما يستطيع uid التطبيق قراءته. فالعقدة التي تحجبها النواة أو SELinux
 * تعود `null` — وليس "غير متاحة" ولا "غير مدعومة" — و`RootFileAccess` يسألها بالطريق
 * المصرَّح (IPC الجذر ثم الصدفة). فالتسريع لا يشتري قدرةً ولا يُخفي عقدة.
 *
 * ولا كتابة ولا `chmod` هنا إطلاقًا (ADR-11: كل الكتابات عبر المُحكِّم).
 *
 * **ولا تسجيل هنا عند غياب المكتبة:** `PredictorBridge` يُسجّل غياب `libmaxmanager_native`
 * في التشخيص بالفعل، وتسجيله مرتين يصنع ضجيجًا يوهم بعطبين.
 */
object ProbeBridge {

    /**
     * هل حُمِّلت المكتبة الأصلية؟ حساب مرة واحدة. غيابها **ليس عطبًا**: المستهلك يقرأ `null`
     * ويواصل بالطريق الاحتياطي المصرَّح — نفس السلوك على أجهزة بلا ثنائيات مبنية.
     */
    val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("maxmanager_native")
    }.isSuccess

    private external fun nativeReadManyPacked(packedPaths: String): String

    private external fun nativeListNamesPacked(path: String, dirsOnly: Boolean): String

    private external fun nativeExistingPacked(packedPaths: String): String

    private external fun nativeParseLogsPacked(packedLines: String, unified: Boolean): String

    /**
     * قراءة عدة عقد في نداء أصلي واحد.
     *
     * العائد `null` يعني «اسأل غيري»: إمّا المكتبة غائبة، وإمّا المسارات لا تُشحن بأمان
     * (سطر جديد أو فاصل داخلها)، وإمّا الحزمة عادت بعدد أسطر مخالف — وهو ما يستحيل تفسيره
     * تفسيرًا صحيحًا بمحاذاة واحدة. والعائد قائمة بنفس طول المدخل يعني «هذه ما قرأته»؛
     * والفارغ فيها (`null`) عقدة لم تُقرأ، ويكمّلها المتصل.
     */
    fun readMany(paths: List<String>): List<String?>? {
        if (paths.isEmpty()) return emptyList()
        if (!nativeAvailable || !ProbePacket.packable(paths)) return null
        val packed = runCatching {
            nativeReadManyPacked(ProbePacket.packPaths(paths))
        }.getOrNull() ?: return null
        if (packed.isEmpty()) return null
        val values = ProbePacket.unpackValues(packed, paths.size)
        // انحراف العدد يُرجع null (لا محاذاة مخمَّنة) — واستوى أن تكون القيم كلها null.
        return if (values.size == paths.size) values else null
    }

    /**
     * وجود عدة مداخل في نداء واحد (نفس ترتيب المدخل).
     *
     * وهذا سؤال مستقل عن القيمة: عقدة موجودة وكتبها السائق لا تُقرأ (أو تُقرأ فارغة)،
     * وشاشات الاكتشاف تسأل «هل هذه العقدة على هذا الجهاز؟» لا «ما قيمتها؟». فالحكم هنا
     * `stat` مثل `test -e` و`File.exists()` — **ولا يعني الوجود أن القيمة ستُقرأ**.
     * و`null` تعني «اسأل غيري» كما في [readMany].
     */
    fun existing(paths: List<String>): List<Boolean>? {
        if (paths.isEmpty()) return emptyList()
        if (!nativeAvailable || !ProbePacket.packable(paths)) return null
        val packed = runCatching {
            nativeExistingPacked(ProbePacket.packPaths(paths))
        }.getOrNull() ?: return null
        return ProbePacket.unpackFlags(packed, paths.size)
    }

    /**
     * تحليل **دفعة سطور** سجلّ في نداء واحد — أو `null` ليعود المتصل إلى محلّله المرجعي.
     *
     * **ولماذا لا تُنتقل الأسطر واحدًا واحدًا:** سطور logcat تصل بعشرات في الثانية، ونداء JNI
     * لكل سطر كان سيغلب ما يوفّره التحليل نفسه. فالدعوة هنا **دفعية** كما في الحزمة: نافذة
     * الواجهة (٣٠٠ مللي) تُجمع فيها السطور ثم تُحلَّل مرة واحدة.
     *
     * **ولا تُقصَّ الأسطر:** محلّل Kotlin يقرأ السطر **خامًا** (نمطه مقيّد بـ`^`، و`isBlank()`
     * و`startsWith` على النصّ نفسه)، فقصّ الأطراف كان سيحوّل سطرًا يُسقَط إلى سطر يُطابق. والمرفوض
     * هنا هو ما يكسر المحاذاة وحده: سطر يحمل سطرًا جديدًا داخله.
     */
    internal fun parseLogs(lines: List<String>, unified: Boolean): List<ProbePacket.LogRow>? {
        if (lines.isEmpty()) return emptyList()
        if (!nativeAvailable) return null
        if (lines.any { it.contains('\n') }) return null
        val packed = runCatching {
            nativeParseLogsPacked(ProbePacket.packRawLines(lines), unified)
        }.getOrNull() ?: return null
        if (packed.isEmpty()) return null
        return ProbePacket.unpackLogRows(packed, lines.size)
    }

    /**
     * أسماء مدخلات مجلد (مرتّبة، بلا تكرار) — أو `null` إن لم يكن المسار الأصلي متاحًا.
     * والقائمة الفارغة عائدةٌ يُقرأها المتصل كـ«لم أستطع» فيسأل غيره، لأن التمييز بين
     * «مجلد فارغ» و«مجلد لا أراه» لا يُصنع هنا.
     */
    fun listNames(path: String, dirsOnly: Boolean = false): List<String>? {
        if (!nativeAvailable || path.isBlank() || path.contains('\n')) return null
        val packed = runCatching { nativeListNamesPacked(path, dirsOnly) }.getOrNull() ?: return null
        if (packed.isEmpty()) return null
        return ProbePacket.unpackNames(packed)
    }
}
