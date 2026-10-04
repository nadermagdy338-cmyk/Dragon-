/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **وثيقة `audio_effects.xml` والدمج الآمن** (`AQ-09`).
 *
 * **والخطر الذي يحكم هذا التصميم كله:** وحدة الطبقة النظاميّة تحلّ محلّ ملفّ النظام **كاملًا** (Magic
 * Mount تُستبدل الملفّ لا تُضاف إليه). فخطأٌ هنا يعني ألّا يقرأ `audioserver` تهيئة مؤثّراته أصلًا.
 * ولذلك القاعدة الملزمة: **لا يُسقَط شيء قُرئ** — كل مكتبة وكل مؤثّر وكل ربطٍ أعلنه الجهاز يبقى
 * حرفيًّا، ويُضاف فوقه وحده.
 *
 * **والرفض صار حكمًا لا استثناءً:** إضافةٌ بمكتبةٍ تُناقض مكتبةً قائمة، أو بمؤثّرٍ يُناقض إعلانًا
 * قائمًا، تُرفض **بسببٍ مكتوب** (`AudioOverlayReason`) — فلا يُكتب ملفٌّ يخالف ما على الجهاز، ولا
 * تُستبدل قيمةٌ كُتبت بيد غيرنا (ADR-18: يُعلَّق ولا يُعاد كتابة).
 *
 * **وصافٍ تمامًا:** لا `android.*` — يُقاس على JVM.
 */
package nd.max.core.audio

/** مكتبة مؤثّرات كما أعلنها الملفّ: اسمها مفتاحها، ومسارها ما تُحمَّل منه. */
data class AudioEffectLibrary(val name: String, val path: String?)

/**
 * إعلان مؤثّر: اسمه، والمكتبة التي تنفّذه، والـ`uuid` التي تُعرّفه للمنصّة، و**نوعه** (`type`).
 *
 * **و`type` ليس زينةً — غيابه يُسقِط المؤثّر كلّه:** قُرئ في `EffectConfig::parseLibrary`
 * (AOSP، `hardware/interfaces/audio/aidl/default/EffectConfig.cpp` سطرا ١٩٨ و٣٤١) أنّ السمة تُقرأ
 * **من عنصر `<effect>`** وتُخزَّن في `library.type`، ثمّ `findUuid` **يُعيد `false` إن غابت** —
 * و`Factory::loadEffectLibs` يُترجم `false` إلى «`skipping`» فلا يُنشئ هويّةً ولا يفتح المكتبة أصلًا.
 *
 * **والدليل من جهاز المالك لا من الورق:** في `logcat` ظهرت المكتبات القديمة
 * (`libbassboostsw.so` · `libequalizersw.so` · `libvolumesw.so` · …) في `parseLibrary` **ولم تُفتح
 * بـ`openEffectLibrary dlopen lib` إطلاقًا** — لأنّ مفعولاتها في التهيئة بلا `type`. أي أنّ مؤثّرًا
 * بمكتبةٍ صحيحة وعقدٍ صحيح يبقى **غير محمَّل بصمت** إن غابت السمة.
 */
data class AudioEffectDeclaration(
    val name: String,
    val library: String?,
    val uuid: String?,
    val type: String? = null,
)

/** ربطٌ بجهاز: نوع الجهاز (`AUDIO_DEVICE_OUT_SPEAKER`) وأسماء المؤثّرات المطبَّقة عليه. */
data class AudioEffectDeviceAttachment(val deviceType: String, val effects: List<String>)

/**
 * وثيقة `audio_effects.xml` — **وشجرة XML الخام محفوظة داخلها**، فلا يُسقط التحليل قسمًا لا نعرفه.
 *
 * والقراءات المسمّاة (`libraries` · `effects` · `deviceAttachments`) **مشتقّة من الشجرة** ولا
 * تُخزَّن منفصلة عنها: مصدرٌ واحد للحقيقة، فالكتابة تُنتج الشجرة التي قُرئت + الإضافة، لا نموذجًا
 * موازيًا قد ينحرف.
 */
class AudioEffectsDocument internal constructor(val root: AudioXmlNode) {

    /** نسخة الصيغة (`version="2.0"`) — و`null` حين لا تُعلن، فلا يُخترع رقم. */
    val version: String? get() = root.attribute(ATTR_VERSION)

    val libraries: List<AudioEffectLibrary>
        get() = root.childrenNamed(SECTION_LIBRARIES).flatMap { section ->
            section.childrenNamed(NODE_LIBRARY).map { node ->
                AudioEffectLibrary(
                    name = node.attribute(ATTR_NAME).orEmpty(),
                    path = node.attribute(ATTR_PATH),
                )
            }
        }

    val effects: List<AudioEffectDeclaration>
        get() = root.childrenNamed(SECTION_EFFECTS).flatMap { section ->
            section.childrenNamed(NODE_EFFECT).map { node ->
                AudioEffectDeclaration(
                    name = node.attribute(ATTR_NAME).orEmpty(),
                    library = node.attribute(ATTR_LIBRARY),
                    uuid = node.attribute(ATTR_UUID),
                    type = node.attribute(ATTR_TYPE),
                )
            }
        }

    /**
     * الربط بالأجهزة — **قسم `deviceEffects` (نسخة ٢٫٥ وما بعدها)**، وهو ما يسمح بأن يُطبَّق مؤثّر على
     * مخرجٍ بعينه لا على المنصّة كلها. ورابطٌ بلا نوعِ جهازٍ يُسقَط لأنه لا يعني شيئًا، ويُعلن العدد
     * في الشاشة من الشجرة لا من هذا العدد.
     */
    val deviceAttachments: List<AudioEffectDeviceAttachment>
        get() = root.childrenNamed(SECTION_DEVICE_EFFECTS).flatMap { section ->
            section.childrenNamed(NODE_DEVICE).map { node ->
                AudioEffectDeviceAttachment(
                    deviceType = node.attribute(ATTR_TYPE).orEmpty(),
                    effects = node.childrenNamed(NODE_APPLY).mapNotNull { it.attribute(ATTR_EFFECT) },
                )
            }
        }

    /** الوثيقة ← نصّ — بنفس التسلسل الثابت الذي يقارنه المحكِّم. */
    fun toXml(): String = AudioEffectsXml.serialize(root)

    companion object {
        const val ATTR_VERSION = "version"
        const val ATTR_NAME = "name"
        const val ATTR_PATH = "path"
        const val ATTR_LIBRARY = "library"
        const val ATTR_UUID = "uuid"
        const val ATTR_TYPE = "type"
        const val ATTR_EFFECT = "effect"
        const val SECTION_LIBRARIES = "libraries"
        const val SECTION_EFFECTS = "effects"
        const val SECTION_DEVICE_EFFECTS = "deviceEffects"
        const val NODE_LIBRARY = "library"
        const val NODE_EFFECT = "effect"
        const val NODE_DEVICE = "device"
        const val NODE_APPLY = "apply"

        /** يحلّل نصًّا إلى وثيقة، أو `null` إن لم يكن XML صالحًا أو لم يكن جذره `audio_effects`. */
        fun parse(text: String): AudioEffectsDocument? {
            val root = AudioEffectsXml.parse(text) ?: return null
            if (root.name != ROOT_NAME) return null
            return AudioEffectsDocument(root)
        }

        /**
         * **لماذا تعذّر التحليل** — تحويلُ طريقٍ مسدودٍ إلى قياس.
         *
         * كانت الشاشة تقول «الملف موجود ولا يُحلَّل» بلا تفصيل، فلا يُعرف هل الملف فاسدٌ أم هو
         * بصيغةٍ لا نعرفها. والجواب يُقرأ هنا: **اسمُ الجذر الفعليّ** حين يُحلَّل XML ويكون جذره
         * غير `audio_effects`، وإلّا `null` أي «ليس XML صالحًا أصلًا».
         */
        fun parseDiagnosis(text: String): String? {
            val name = AudioEffectsXml.parse(text)?.name ?: return null
            // واسمُ الجذر الصحيح لا يُبلَّغ عنه عطبًا: الجواب يُقال حين يخالف المُتوقَّع وحده،
            // وإلّا عاد `null` أي «لا تشخيص — المشكلة ليست في الجذر».
            return name.takeIf { it != ROOT_NAME }
        }


        const val ROOT_NAME = "audio_effects"
    }
}

/**
 * مسارات `audio_effects.xml` — **بترتيب بحث المنصّة**، والأوّل الموجود هو المستعمل.
 *
 * **ولماذا الترتيب مكتوب لا مُستنتَج:** ملفّ `vendor/etc` لا يُستبدل بوحدةٍ تكتب `system/etc` —
 * فالمسار المستعمل **يحدّد مسار الطبقة** داخل الوحدة، واختيار المسار الخطأ يعني طبقةً لا تُركَّب
 * أصلًا (وتقرأ الشاشة «نجح» على ملفٍّ لا يراه أحد).
 *
 * **وأين تقع الطبقة داخل الوحدة — هذا مقيسٌ من دليل Magisk لا مُخمَّن:** دليل المطوّرين ينصّ:
 * «إن أردتَ استبدال ملفّات في `/vendor` أو `/product` أو `/system_ext` فضعها تحت `system/vendor`
 * و`system/product` و`system/system_ext` على الترتيب، وMagisk يتولّى الشفافيّة بين كونها قسمًا
 * منفصلًا أو لا». ومجلّد `system` وحده هو الذي **يُدمج** في النظام الحقيقيّ؛ وما في جذر الوحدة من
 * `vendor`/`product`/`system_ext` فهي **روابط رمزيّة يولّدها Magisk نفسه** إلى `system/...` ولا
 * تُركَّب بذاتها. وقد شوهد ذلك في وحداتٍ حقيقيّة على الأجهزة: ملفّ `VIPER4AndroidFX` يسكن
 * `/data/adb/modules/VIPER4AndroidFX/system/vendor/etc/audio_effects.xml`. فكتابة `vendor/etc/...`
 * في جذر الوحدة كانت **طبقةً لا تُركَّب أبدًا** — وهي العلّة التي كشفها هذا الفحص قبل التسليم.
 *
 * **و`/odm` استثناءٌ مُعلَن:** دليل Magisk لم يذكر `/odm` — فتُطبَّق عليه القاعدة نفسها
 * (`system/odm/...`) **تقديرًا** مُعلَنًا لا يقينًا، ويبقى الحكم عليه على الجهاز (§0.1). والبديل عن
 * التقدير هو رفض جهازٍ ملفّه في `/odm` أصلًا — وهو أسوأ: يقول «لا شيء» لملفٍّ موجود.
 */
object AudioEffectsPaths {

    const val FILE_NAME = "audio_effects.xml"

    /**
     * الاسم الثاني — **وهو ليس تخمينًا**: على `Xiaomi 24129RT7CC` (MediaTek) يقرأ مصنع المؤثّرات
     * ملفَّه من هذا الاسم حرفيًّا (‏`logcat`: `createIEffectMTK: … configFile:/vendor/etc/
     * audio_effects_config.xml` ثمّ `EffectConfig successfully parsed`)، و`EffectConfig` في AOSP
     * يبحث عن الاسمين معًا. فتجاهلُ الاسم الثاني يعني «لا ملفّ» لجهازٍ يملكه.
     */
    const val ALT_FILE_NAME = "audio_effects_config.xml"

    /**
     * مرشّحو المسار على الجهاز، بترتيب المنصّة: **قسمًا قسمًا، وداخل كلّ قسم الاسمان**.
     *
     * والترتيب داخل القسم مكتوبٌ لا مُستنتَج: `audio_effects.xml` هو اسم AOSP الأوّل، ثمّ الاسم
     * الثاني. و`/odm` أولًا كما هو معيار المنصّة.
     */
    val DEVICE_CANDIDATES: List<String> =
        listOf("odm", "vendor", "system", "product").flatMap { partition ->
            listOf("/$partition/etc/$FILE_NAME", "/$partition/etc/$ALT_FILE_NAME")
        }

    /** الأقسام التي يمكن أن تحمل الملفّ — وما عداها لا يُركَّب، فيُرفض ولا يُوعد. */
    private val PARTITION_ROOTS = setOf("odm", "vendor", "system", "product", "system_ext")

    /**
     * مسار الجهاز ← مسار الطبقة داخل الوحدة (نسبيًّا لجذرها): `/vendor/etc/audio_effects.xml` ⇒
     * `system/vendor/etc/audio_effects.xml`.
     *
     * و`null` حين لا يكون المسار داخل جزءٍ قابلٍ للطبقة — **فلا يُكتب ملفٌّ لا يُركَّب**.
     */
    fun moduleRelativePath(devicePath: String): String? {
        val clean = devicePath.trim()
        if (!clean.startsWith('/')) return null
        // **واسمُ الملفّ يُقبل بهما:** الطبقة تُكتب على الاسم الذي قرأه الجهاز، فلا تُنتج اسمًا
        // جديدًا يقرأه `EffectConfig` على أنه ملفٌّ آخر (وهو عطبٌ كان سيقع مع `audio_effects_config.xml`).
        if (!clean.endsWith("/$FILE_NAME") && !clean.endsWith("/$ALT_FILE_NAME")) return null
        val body = clean.removePrefix("/")
        // ولا جزء فارغ ولا `..` — الطبقة تُكتب تحت جذر الوحدة، ولا تخرج عنه.
        val parts = body.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        val partition = parts.first()
        if (partition !in PARTITION_ROOTS) return null
        // وقسم النظام **لا يُكرَّر**: `/system/etc/…` ⇒ `system/etc/…` لا `system/system/…`.
        // (وهذا يشمل `/system/vendor/etc/…` — المسار الحقيقيّ للأجهزة التي قسمُ vendor فيها داخل
        // system — فيخرج `system/vendor/etc/…` كما ينبغي بلا قاعدةٍ ثانية.)
        return if (partition == "system") body else "system/$body"
    }

    /**
     * سياق SELinux **بأفضل تقدير من المسار** — ولا يُدَّعى: الحكم عليه يحتاج جهازًا (§0.1).
     *
     * والقاعدة من AOSP: `/vendor/etc` و`/odm/etc` ملفّاتُ تهيئةٍ لمصنّع (`vendor_configs_file`)،
     * وما عداه من `/system` و`/product` و`/system_ext` يُوسم `system_file`. والوسم الخاطئ لا "ينجح
     * بصمت": يمنع `audioserver` من قراءة الطبقة، وهو معلَنٌ هنا كي يُقاس على أوّل جهاز يُشغَّل عليه.
     */
    fun overlayLabel(relativePath: String): String =
        if (relativePath.startsWith("system/vendor/") || relativePath.startsWith("system/odm/")) {
            VENDOR_CONFIGS_LABEL
        } else {
            SYSTEM_FILE_LABEL
        }

    const val VENDOR_CONFIGS_LABEL = "u:object_r:vendor_configs_file:s0"
    const val SYSTEM_FILE_LABEL = "u:object_r:system_file:s0"

    /**
     * **وسم المكتبة — وهو يخالف وسم ملفّ التهيئة:** مكتبةٌ في `/vendor/lib64` تُوسم `vendor_file`
     * (وليست `vendor_configs_file` التي تُوسم لها ملفّات `/vendor/etc`)، وقاعدة AOSP في `file_contexts`
     * تُوسم `(vendor|system/vendor)/lib(64)?(/.*)?` بـ`vendor_file`. **وتقديرٌ مثل أخيه** (§0.1).
     */
    fun libraryLabel(relativePath: String): String =
        if (relativePath.startsWith("system/vendor/") || relativePath.startsWith("system/odm/")) {
            VENDOR_FILE_LABEL
        } else {
            SYSTEM_FILE_LABEL
        }

    const val VENDOR_FILE_LABEL = "u:object_r:vendor_file:s0"

    /**
     * مجلّد مكتبات المؤثّرات الذي **يبحث فيه المصنع فعلًا** — مقيسًا من مصدرين:
     *
     * ١) `EffectConfig::resolveLibrary` (AOSP): يبني المسار `directory + '/' + path` ويختبر `access()`
     *    على كل مجلّد في `kEffectLibPath` (ثمّ `apex/<vendor>/…` قبلها). أي أنّ **موضع المكتبة ليس
     *    موضع ملفّ التهيئة** — قد يقرأ الأخير من `/odm/etc` والمكتبة من `/vendor/lib64`.
     * ٢) ولوق جهاز المالك يحمل المسار المحلول حرفيًّا: `parseLibrary <name> :
     *    /vendor/lib64/soundfx//lib<name>.so` — **فالمجلّد الذي يسكنه على عتاده هو `/vendor/lib64/soundfx/`.**
     *
     * **وحدُّه المُعلَن:** قائمة `kEffectLibPath` بحرفها لم تُقرأ في هذه البيئة (ملفّ التعريف ليس في
     * المسارات المتاحة، و`EC.h` عاد فارغًا) — فالمُثبَّت **مجلّد جهاز المالك** وما يقتضيه منطقُ
     * `resolveLibrary`، لا تخمينٌ لمجموعةٍ كاملة.
     */
    const val SOUNDFX_DIR = "soundfx"

    /** القسم الذي نسكن فيه المكتبة على هذا العتاد — مقيس من اللوق أعلاه. */
    const val LIBRARY_PARTITION = "vendor"

    const val LIBRARY_64_DIR = "lib64"
    const val LIBRARY_32_DIR = "lib"

    /** أعمدة ٦٤-بت المعروفة — وما عداها يُحسب ٣٢-بت، وكذلك العكس. */
    private val ABIS_64 = setOf("arm64-v8a", "x86_64", "riscv64")

    /**
     * مسار المكتبة **على الجهاز** بحسب عمود المعالج — أو `null` لعمود لا نعرفه (فلا يُبنى مسارٌ مظنون).
     *
     * والقسم ثابتٌ في [LIBRARY_PARTITION] لأنّه **القسم الذي قِيس فيه البحث** لا لأنّ التهيئة فيه.
     */
    fun libraryDevicePath(abi: String, fileName: String): String? {
        if (fileName.isBlank() || fileName.contains('/')) return null
        val dir = when (abi.trim()) {
            in ABIS_64 -> LIBRARY_64_DIR
            "armeabi-v7a", "armeabi", "x86" -> LIBRARY_32_DIR
            else -> return null
        }
        return "/$LIBRARY_PARTITION/$dir/$SOUNDFX_DIR/$fileName"
    }

    /**
     * مسار المكتبة ← مسارها داخل الوحدة (نسبيًّا لجذرها).
     *
     * **ولماذا دالّةٌ ثانية ولا تُوسَّع [`moduleRelativePath`]:** تلك تقبل **اسمَي ملفّ التهيئة
     * وحدهما**، وتوسيعها لكل مسار كان سيُدخل ملفّاتٍ لا علاقة لها بالتهيئة إلى مسار الطبقة. والفصل
     * هنا يمنع أن يُكتب يومًا `/vendor/etc/libmaxfx.so` أو العكس.
     */
    fun libraryModuleRelativePath(deviceLibraryPath: String): String? {
        val clean = deviceLibraryPath.trim()
        if (!clean.startsWith('/')) return null
        if (!clean.endsWith(".so")) return null
        val parts = clean.removePrefix("/").split('/')
        // ثلاثة أجزاء على الأقلّ، وكلّها غير فارغة (فلا `//` ولا `..` تُخرج المكتبة عن جذر الوحدة)،
        // وقسمها مذكور — فمسارٌ لا يُركَّب **يُرفض ولا يُوعد**.
        if (parts.size < 3 || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (parts.first() !in PARTITION_ROOTS) return null
        return if (parts.first() == "system") parts.joinToString("/") else "system/${parts.joinToString("/")}"
    }
}

/**
 * مكتبةُ المؤثّر: من أين تُنسخ، وإلى أين تُوضع، وبأيّ صلاحيةٍ ووسم.
 *
 * **وهي الخطوة التي كانت غائبة بالكامل** (تكملة ٢٤٠): المولّد كان يُخرج أربعة ملفّات **نصّيّة**
 * (`module.prop` · التهيئة · سكربتان) و**لا مسار نسخٍ للمكتبة إطلاقًا** — فمهما صحّ العقد (‏`AELI` أو
 * `createEffect` أو أيّ غيره) و**مهما كُتبت سمة `type`**، فالملفّ غير موجود على الجهاز و`resolveLibrary`
 * تفشل، ويُطبع في اللوق `can't find libmaxfx.so` ولا يُسمع فرق. وهذا هو الفرق المقيس بيننا وبين وحدة
 * V4A: عندها `cp … $LIBDIR/lib64/soundfx/` وعندنا **صفرُ سطرٍ ينسخ مكتبة**.
 */
data class AudioEffectLibraryPlan(
    /** اسم الملفّ المجرَّد — **وهو نفسه سمة `path` في `<library>`** (مقيس: AOSP يبحث عن اسمٍ مجرَّد). */
    val fileName: String,
    /** ملفّ المكتبة كما يُشحَن داخل التطبيق (من `nativeLibraryDir`). */
    val sourcePath: String,
    val devicePath: String,
    val moduleRelativePath: String,
    val label: String,
    /** صلاحية القراءة لـ`audioserver` — وهي التي يفرضها التثبيت ثمّ يُقيسها. */
    val mode: String = LIBRARY_MODE,
)

/** صلاحية المكتبة: ٠٦٤٤ — **ومثلُ ملفّ التهيئة: ٠٦٠٠ تُركَّب ولا تُقرأ.** */
const val LIBRARY_MODE = "644"

/**
 * بناء خطّة المكتبة — **نقيّة**، وتُرجع `null` بدل مسارٍ مظنون:
 * مجلّد مكتبات التطبيق غائبٌ أو العمود مجهولٌ أو المسار غير قابلٍ للطبقة ⇒ لا خطّة (وتُقال الحاجة
 * إلى جهازٍ أو مكتبةٍ مشحونة، ولا يُكتب ملفّ لن يُقرأ).
 */
fun audioEffectLibraryPlan(
    /**
     * **مسار ملفّ المكتبة الحقيقيّ** — لا مجلّدًا يُبنى منه:
     *
     * كان يأخذ مجلّد مكتبات التطبيق ويبني منه المسار، **وهو مسارٌ لا وجود له على الأجهزة الحديثة**
     * (`extractNativeLibs=false` يترك المكتبات داخل الحزمة — قِيس في تكملة ٢٤١). فصار يأخذ **الملفّ**
     * الذي قِيس وجوده فعلًا ([`AudioEffectLibraryStaging.sourceFor`])، والبناء يبقى في مكانٍ واحد.
     */
    sourcePath: String?,
    abi: String,
    fileName: String,
): AudioEffectLibraryPlan? {
    val source = sourcePath?.trim()
    // ولا يُقبل مسارٌ لا ينتهي باسم المكتبة نفسه: مصدرٌ لا يطابق الاسم يعني أنّ ما سيُنسخ ليس المكتبة.
    if (source.isNullOrEmpty() || !source.endsWith("/$fileName")) return null
    val devicePath = AudioEffectsPaths.libraryDevicePath(abi, fileName) ?: return null
    val relative = AudioEffectsPaths.libraryModuleRelativePath(devicePath) ?: return null
    return AudioEffectLibraryPlan(
        fileName = fileName,
        sourcePath = source,
        devicePath = devicePath,
        moduleRelativePath = relative,
        label = AudioEffectsPaths.libraryLabel(relative),
    )
}

/**
 * إضافةٌ نطلبها: مكتبةٌ تُحمَّل، ومؤثّرٌ يُعلَن، وأجهزةٌ يُربَط بها (اختياريّة).
 *
 * **وهي مُدخَل لا رأي:** الطبقة الصافية لا تخترع مكتبةً ولا `uuid` — تُمرَّر إليها، وتُرفَض إن كانت
 * غير صالحة. فأيّ «دعم التفاف» يبقى معلَّقًا على مكتبةٍ حقيقيّة على الجهاز يُصرّح بها المستخدم.
 */
data class AudioEffectAddition(
    val libraryName: String,
    val libraryPath: String,
    val effectName: String,
    val effectUuid: String,
    val deviceTypes: List<String> = emptyList(),
    /**
     * نوع المؤثّر كما يُكتب في سمة `type` على عنصر `<effect>` — **وابقاؤه اختياريًّا لا يعني أنّه
     * تحسين:** هو شرطُ تحميلٍ في مصنع AIDL (انظر [AudioEffectDeclaration.type])، فإضافةٌ بلا نوع
     * تُقبل كتابةً ولا يعمل لها شيء. ولا يُخترع هنا: يُمرَّر من العقد
     * ([MaxFxModel.TYPE_UUID]) لأنّه هويّة لا رأي.
     */
    val effectType: String? = null,
)

/** رموز رفض الإضافة — **رمزٌ يُكتب** في السجل، ونصّه في الموارد. */
object AudioOverlayReason {
    const val INVALID_ADDITION = "addition-invalid"
    const val LIBRARY_CONFLICT = "addition-library-conflict"
    const val EFFECT_CONFLICT = "addition-effect-conflict"
    const val NOTHING_TO_ADD = "addition-already-declared"
}

/** حصيلة الدمج: وثيقةٌ ستُكتب، أو رفضٌ بسببه. */
sealed interface AudioOverlayResult {
    /**
     * ما سيُكتب — **وما أُضيف فعلًا مذكورٌ بالاسم**، فلا يُعلن تغييرٌ لم يقع.
     */
    data class Overlay(
        val document: AudioEffectsDocument,
        val addedLibrary: Boolean,
        val addedEffect: Boolean,
        val addedDeviceTypes: List<String>,
    ) : AudioOverlayResult {
        val changed: Boolean get() = addedLibrary || addedEffect || addedDeviceTypes.isNotEmpty()
    }

    data class Refused(val reason: String) : AudioOverlayResult
}

/**
 * يدمج إضافةً في وثيقة الجهاز — **بلا إسقاطِ شيء، وبلا إعادة كتابةِ قائم**.
 *
 * والقواعد الأربع، كلٌّ منها عطبٌ حقيقي مُتخيَّل لا احتمال:
 *
 * 1. إضافةٌ غير صالحة (اسمٌ فيه محارف لا تصلح لـXML · `uuid` ليست `uuid`) ⇒ **رفض**: ملفٌّ مشوّه
 *    يوقف تحميل التهيئة كلها، فيدفع ثمن خطأ إدخالٍ **صوت الجهاز** كلّه.
 * 2. اسمُ مكتبةٍ موجود بمسارٍ مختلف ⇒ **رفض**: لو أضفناه لكان في الملفّ مكتبتان بالاسم نفسه
 *    وبمسارين — والمنصّة تحمل واحدة، فلا نعرف أيّهما. والأمان: لا نُغيّر إعلانًا قائمًا.
 * 3. اسمُ مؤثّرٍ موجود بمكتبةٍ أو `uuid` مختلفة ⇒ **رفض** بالسبب نفسه (ADR-18).
 * 4. وإذا كان كل ما نطلبه معلَنًا أصلًا ⇒ **رفض** بـ`addition-already-declared`: لا تغيير، ولا
 *    كتابة ملفٍّ بلا معنى.
 */
fun audioEffectsOverlay(
    device: AudioEffectsDocument,
    addition: AudioEffectAddition,
): AudioOverlayResult {
    if (!isValidAddition(addition)) return AudioOverlayResult.Refused(AudioOverlayReason.INVALID_ADDITION)

    val existingLibrary = device.libraries.firstOrNull { it.name == addition.libraryName }
    if (existingLibrary != null && (existingLibrary.path ?: "") != addition.libraryPath) {
        return AudioOverlayResult.Refused(AudioOverlayReason.LIBRARY_CONFLICT)
    }
    val existingEffect = device.effects.firstOrNull { it.name == addition.effectName }
    if (existingEffect != null &&
        ((existingEffect.library ?: "") != addition.libraryName ||
            (existingEffect.uuid ?: "") != addition.effectUuid ||
            // والنوع يُقارَن كذلك، **ومؤثّرٌ قائم بلا `type` يُرفض لا يُصمت عنه:** الطبقة تُضيف
            // فقط ولا تُعدّل عقدةً قائمة (ADR-18)، فلا سبيل إلى إسناد سمةٍ لعنصرٍ كُتب بيد غيرنا.
            // فيُقال الرفض صراحةً بدل أن يُكتب ملفٌّ يُظنّ أنّه أصلح ما لم يُصلَح.
            (addition.effectType != null && (existingEffect.type ?: "") != addition.effectType))
    ) {
        return AudioOverlayResult.Refused(AudioOverlayReason.EFFECT_CONFLICT)
    }

    val attachLibrary = existingLibrary == null
    val attachEffect = existingEffect == null
    val declaredDevices = device.deviceAttachments.map { it.deviceType }.toSet()
    val attachDevices = addition.deviceTypes.filterNot { it in declaredDevices }

    if (!attachLibrary && !attachEffect && attachDevices.isEmpty()) {
        return AudioOverlayResult.Refused(AudioOverlayReason.NOTHING_TO_ADD)
    }

    var root = device.root
    if (attachLibrary) {
        root = appendUnder(
            root,
            AudioEffectsDocument.SECTION_LIBRARIES,
            AudioXmlNode(
                name = AudioEffectsDocument.NODE_LIBRARY,
                attributes = listOf(
                    AudioEffectsDocument.ATTR_NAME to addition.libraryName,
                    AudioEffectsDocument.ATTR_PATH to addition.libraryPath,
                ),
            ),
        )
    }
    if (attachEffect) {
        root = appendUnder(
            root,
            AudioEffectsDocument.SECTION_EFFECTS,
            AudioXmlNode(
                name = AudioEffectsDocument.NODE_EFFECT,
                attributes = listOfNotNull(
                    AudioEffectsDocument.ATTR_NAME to addition.effectName,
                    AudioEffectsDocument.ATTR_LIBRARY to addition.libraryName,
                    AudioEffectsDocument.ATTR_UUID to addition.effectUuid,
                    // **وتُكتب بعد `uuid` لا قبلها:** ترتيب السمات لا دلالة له في XML (مقيس في
                    // `AudioEffectsXml`)، والترتيب هنا ترتيبُ القراءة في عين القارئ لا شرطًا في الملفّ.
                    addition.effectType?.let { AudioEffectsDocument.ATTR_TYPE to it },
                ),
            ),
        )
    }
    attachDevices.forEach { type ->
        root = appendUnder(
            root,
            AudioEffectsDocument.SECTION_DEVICE_EFFECTS,
            AudioXmlNode(
                name = AudioEffectsDocument.NODE_DEVICE,
                attributes = listOf(AudioEffectsDocument.ATTR_TYPE to type),
                children = listOf(
                    AudioXmlNode(
                        name = AudioEffectsDocument.NODE_APPLY,
                        attributes = listOf(AudioEffectsDocument.ATTR_EFFECT to addition.effectName),
                    ),
                ),
            ),
        )
    }

    return AudioOverlayResult.Overlay(
        document = AudioEffectsDocument(root),
        addedLibrary = attachLibrary,
        addedEffect = attachEffect,
        addedDeviceTypes = attachDevices,
    )
}

/**
 * يُضيف ابنًا تحت قسمٍ باسم معلوم: **إن وُجد القسم أُضيف في آخره، وإلا أُنشئ قسمٌ جديد في آخر الجذر**.
 * ولا يُمسّ قسمٌ آخر ولا يُعاد ترتيبه — فما قُرئ يبقى في موضعه.
 */
private fun appendUnder(root: AudioXmlNode, sectionName: String, child: AudioXmlNode): AudioXmlNode {
    val index = root.children.indexOfFirst { it.name == sectionName }
    if (index < 0) {
        return root.copy(children = root.children + AudioXmlNode(name = sectionName, children = listOf(child)))
    }
    val section = root.children[index]
    val updated = section.copy(children = section.children + child)
    return root.copy(children = root.children.toMutableList().also { it[index] = updated })
}

/**
 * صحة الإضافة — **والقاعدة واحدة: كل ما سيُكتب في الملفّ يجب أن يكون نصًّا لا يكسر XML أصلًا**.
 *
 * ولماذا هذا الفحص في الطبقة الصافية لا في الشاشة: نصوص الإدخال تُكتب في ملفٍّ يقرأه `audioserver`؛
 * فمحرفٌ واحد خارج القاعدة يعني تهيئةً لا تُحلَّل. والأسماء محدودة بمحارف معروفة، و`uuid` بصيغتها
 * القياسية، وأنواع الأجهزة بمحارف معروفة كذلك.
 */
fun isValidAddition(addition: AudioEffectAddition): Boolean {
    if (!TOKEN_PATTERN.matches(addition.libraryName)) return false
    if (!PATH_PATTERN.matches(addition.libraryPath)) return false
    if (!TOKEN_PATTERN.matches(addition.effectName)) return false
    if (!UUID_PATTERN.matches(addition.effectUuid)) return false
    // والنوع إن أُعلن فهو `uuid` بصيغتها القياسية — ونوعٌ مشوّه يُسقِط قراءة التهيئة كما يُسقطها uuid مشوّهة.
    if (addition.effectType?.let { !UUID_PATTERN.matches(it) } == true) return false
    if (addition.deviceTypes.size > MAX_DEVICE_TYPES) return false
    if (addition.deviceTypes.any { !TOKEN_PATTERN.matches(it) }) return false
    if (addition.deviceTypes.distinct().size != addition.deviceTypes.size) return false
    return true
}

/**
 * سطر الإضافة كما يُدخل في الشاشة: `المكتبة|المسار|المؤثّر|uuid[|جهاز1,جهاز2][|النوع]`.
 *
 * **والحدّ الأدنى أربعة، والزائد يُقرأ لا يُهمَل:** الخامس أجهزةٌ يفصلها `,`، والسادس نوع المؤثّر.
 * وأُضيف النوع **في الذيل لا في الوسط** كي لا يتغيّر معنى حقلٍ يكتبه المستخدم اليوم (سطرٌ قديم بخمسة
 * حقول يبقى مفهومًا كما كان).
 */
const val AUDIO_ADDITION_FIELDS = 4

/** رقم الحقل الذي يحمل النوع — بعد حقول الأجهزة الاختياريّة. */
const val AUDIO_ADDITION_TYPE_FIELD = 5

/**
 * يُحلّل نصّ الإدخال إلى إضافة — أو `null` إن كان السطر ناقصًا أو غير صالح.
 *
 * **وقاعدة الفصل صريحة:** `|` تفصل الحقول، و`,` تفصل الأجهزة. وما زاد على الحقل الخامس يُهمَل
 * لا يُخمَّن، والناقص يعود `null` — فيقول الحكم «إضافة غير صالحة» بدل أن يُبنى ملفٌّ من صدفة.
 */
fun audioEffectAdditionOf(text: String): AudioEffectAddition? {
    val fields = text.split('|').map { it.trim() }
    if (fields.size < AUDIO_ADDITION_FIELDS) return null
    val devices = fields.getOrNull(AUDIO_ADDITION_FIELDS)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    val addition = AudioEffectAddition(
        libraryName = fields[0],
        libraryPath = fields[1],
        effectName = fields[2],
        effectUuid = fields[3],
        deviceTypes = devices,
        // وحقلٌ فارغ نوعُه = «لم يُعلن»، لا «نوعٌ فارغ» (ADR-07) — فما لم يُكتب لا يُخترع.
        effectType = fields.getOrNull(AUDIO_ADDITION_TYPE_FIELD)?.takeIf { it.isNotEmpty() },
    )
    return addition.takeIf(::isValidAddition)
}

/** اسمٌ أو نوع: محارف معروفة فقط، وبطولٍ معقول — فلا يُكسَر الملفّ ولا يخرج عن الجذر. */
private val TOKEN_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")

/** مسار مكتبة: بلا محارف XML وبلا مسافات (المسارات في هذه الملفّات أسماء مكتبات لا مسارات مطلقة). */
private val PATH_PATTERN = Regex("^[A-Za-z0-9._/+:-]{1,160}$")

private val UUID_PATTERN = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

/** سقف أنواع الأجهزة في إضافةٍ واحدة — حدُّنا نحن، معلَنٌ كي لا يُبنى ملفٌّ ضخم من إدخالٍ عابر. */
private const val MAX_DEVICE_TYPES = 16
