/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **مولّد وحدة الطبقة النظاميّة** (`AQ-09`): `module.prop` · ملفّ الطبقة · سكربت التركيب.
 *
 * **ولماذا وحدةٌ مستقلّة لا ملفٌّ داخل وحدة `MaxManager` القائمة:** العطب هنا لا يُصلَح بإعداد، بل
 * بحذف ملفّ — فالوحدة المستقلّة **قابلة للرجوع بحذف مجلّدها** بلا لمس وحدة المشروع الأمّ. ووحدةٌ
 * تُكتب داخل الأمّ تخلط مصيرين: عطبُ تجربةٍ صوتيّة يُفقد الثقة في الوحدة كلها.
 *
 * **وكل شيء هنا نصٌّ يُحسب ولا يُنفَّذ:** لا أمر يُشغَّل ولا ملفّ يُكتب من هذه الطبقة — التوليد فقط.
 * والكتابة الفعليّة في `AudioSystemEffectBackend` عبر المحكِّم، **ويُقرأ بعدها** كما يُقرأ كل مقبض.
 *
 * **وصافٍ تمامًا:** لا `android.*` — يُقاس على JVM.
 */
package nd.max.core.audio

import java.security.MessageDigest

/** ملفٌّ مولَّد: مساره **نسبيًّا لجذر الوحدة**، ومحتواه النهائيّ. */
data class AudioSystemModuleFile(val relativePath: String, val content: String)

/** مولّد وحدة المؤثّرات النظاميّة — نصوص فقط. */
object AudioSystemModule {

    const val MODULE_ID = "MaxManagerAudioEffects"

    /** مجلّد وحدات Magisk — **وجودُه القابل للكتابة هو شرط التثبيت**، لا وجود مجلدنا نحن. */
    const val MODULES_DIR = "/data/adb/modules"

    const val MODULE_ROOT = "$MODULES_DIR/$MODULE_ID"
    const val MODULE_NAME = "MaxManager Audio Effects Layer"
    const val MODULE_VERSION = "v1.0"
    const val MODULE_CODE = 1
    const val MODULE_AUTHOR = "MaxManager Project"

    /**
     * مسار الطبقة **داخل الوحدة** — `system/` ثمّ مسار الجهاز (وهو ما يُدمجه Magisk فعلًا)، فتقع
     * الطبقة على الملفّ الصحيح. وتفصيل القاعدة وقياسها في [AudioEffectsPaths.moduleRelativePath].
     */
    fun overlayRelativePath(devicePath: String): String? = AudioEffectsPaths.moduleRelativePath(devicePath)

    /** مسار الطبقة المطلق على الجهاز — أو `null` إن كان المسار غير قابلٍ للطبقة أصلًا. */
    fun overlayAbsolutePath(devicePath: String, moduleRoot: String = MODULE_ROOT): String? =
        overlayRelativePath(devicePath)?.let { "$moduleRoot/$it" }

    /**
     * `module.prop` — وكل قيمة **تُطوى إلى سطرٍ واحد**: حقلٌ فيه سطر جديد يُفسد الملفّ كلّه (Magisk
     * يقرأه سطرًا سطرًا)، فيفقد المستخدم الوحدة بصمت. ويُعاد `null` إن كان المعرّف خارج الصيغة التي
     * يقبلها Magisk — فلا تُكتب وحدةٌ لا تُقرأ.
     */
    fun moduleProp(
        id: String = MODULE_ID,
        name: String = MODULE_NAME,
        version: String = MODULE_VERSION,
        versionCode: Int = MODULE_CODE,
        author: String = MODULE_AUTHOR,
        description: String,
    ): String? {
        if (!ID_PATTERN.matches(id)) return null
        return buildString {
            append("id=").append(id).append('\n')
            append("name=").append(singleLine(name)).append('\n')
            append("version=").append(singleLine(version)).append('\n')
            append("versionCode=").append(versionCode).append('\n')
            append("author=").append(singleLine(author)).append('\n')
            append("description=").append(singleLine(description)).append('\n')
        }
    }

    /**
     * `customize.sh` — يُشغّله Magisk بعد فكّ الحزمة **إن رُكّبت الوحدة من ملفّ `zip`**.
     *
     * **وما يفعله أقلّ ما يمكن:** صلاحيات ووسوم فقط. لا نسخ ولا نقل — لأن التدفّق الافتراضيّ في
     * Magisk يفكّ الشجرة بنفسه، وسكربتٌ يفعل أكثر **يفعل أقلّ** حين يخطئ.
     *
     * **والمسار يأتي وسيطًا لأن الوسم يتبعه:** ملفّ `/vendor` يُوسم `vendor_configs_file` لا
     * `system_file`، و`set_perm_recursive` الافتراضيّ كان يوسم الشجرة كلّها `system_file` — فيُصلَح
     * هنا صراحةً. (وفي التركيب المباشر — وهو مسارنا — لا يُشغَّل هذا الملفّ أصلًا، والوسم يُثبَّت في
     * `post-fs-data.sh` عند كل إقلاع.)
     */
    fun customizeScript(relativePath: String, libraryRelativePath: String? = null): String {
        val lines = mutableListOf(
            "#!/system/bin/sh",
            "# MaxManager — طبقة مؤثّرات نظاميّة. الضبط هنا صلاحيات ووسم فقط؛ لا نسخ ولا نقل.",
            "set_perm_recursive \"${'$'}MODPATH/system\" 0 0 0755 0755",
            "set_perm \"${'$'}MODPATH/$relativePath\" 0 0 0644 " +
                AudioEffectsPaths.overlayLabel(relativePath),
        )
        // والمكتبة بصلاحية وسم **مغايرين**: `vendor_file` لـ`/vendor/lib64` لا `vendor_configs_file`.
        if (libraryRelativePath != null) {
            // و«0$LIBRARY_MODE» تقرأ «0644» — **والرقم مصدره واحد** (`LIBRARY_MODE` هي نفسها التي
            // يقيسها التثبيت)، والصفر البادئ يُطابق صورة السطر الشقيق أعلاه (`set_perm … 0 0 0644`).
            lines += "set_perm \"${'$'}MODPATH/$libraryRelativePath\" 0 0 0$LIBRARY_MODE " +
                AudioEffectsPaths.libraryLabel(libraryRelativePath)
        }
        lines += "set_perm \"${'$'}MODPATH/post-fs-data.sh\" 0 0 0755"
        return lines.joinToString("\n") + "\n"
    }

    /**
     * `post-fs-data.sh` — يُشغَّل باكرًا (قبل تركيب أيّ وحدة)، **ووظيفته أن يُثبّت ملكيّة ملفّ
     * الطبقة وصلاحيّته ووسمه**.
     *
     * **و`chmod 0644` هنا ليس تحسينًا تجميليًّا، بل إصلاحٌ لعطبٍ مُتخيَّل واقعي:** ملفّ يُنشئه
     * التطبيق يخرج من `atomicWriteText` بصلاحية **0600** (وهي الصحيحة لملفّ يُكتب بالجذر)، و`audioserver`
     * لا يقرأ ملفًّا 0600 مملوكًا للجذر — **فتُركَّب الطبقة ولا يقرأها أحد**، وتقرأ الشاشة «نجح». فالطبقة
     * تُركَّب ثم تُمنَع قراءتُها، وهذا أسوأ من فشلٍ ظاهر.
     *
     * **ووسم SELinux أضعف موضع في الموجة، ويُقال صراحةً:** الوسم مُستنتَجٌ من المسار بأفضل تقدير
     * ([AudioEffectsPaths.overlayLabel])، و`2>/dev/null || true` تُبقي الحالة غير المُثبتة غير قاتلة
     * (فشل الوسم يعني مؤثّراتٍ لا تُحمَّل، لا إقلاعًا لا يعمل). **والحكم عليه يحتاج جهازًا** (§0.1).
     */
    fun postFsDataScript(relativePath: String, libraryRelativePath: String? = null): String {
        val head = """
            #!/system/bin/sh
            # MaxManager — ملكيّة ملفّ الطبقة وصلاحيّته ووسمه (تقديرٌ من المسار؛ يحتاج قياسًا على جهاز).
            MODDIR=${'$'}{0%/*}
            TARGET="${'$'}MODDIR/$relativePath"
            [ -f "${'$'}TARGET" ] || exit 0
            chown 0:0 "${'$'}TARGET" 2>/dev/null || true
            chmod 0644 "${'$'}TARGET" 2>/dev/null || true
            chcon "${AudioEffectsPaths.overlayLabel(relativePath)}" "${'$'}TARGET" 2>/dev/null || true
        """.trimIndent()
        // والمكتبة **نفس العطب بنفس الأثر**: مكتبة ٠٦٠٠ تُركّب ولا يقرؤها المصنع، فيُطبع
        // «can't find libmaxfx.so» فتُقرأ العلّة عقدًا وهي صلاحية. والكتلة **لا تُكتب أصلًا** بلا مكتبة،
        // فلا يبقى في السكربت سطرٌ ميّت يوهم بقارئ.
        if (libraryRelativePath == null) return head + "\n"
        val library = LIBRARY_PERM_BLOCK
            .replace(LIBRARY_PLACEHOLDER, libraryRelativePath)
            .replace(LIBRARY_LABEL_PLACEHOLDER, AudioEffectsPaths.libraryLabel(libraryRelativePath))
        return head + "\n" + library + "\n"
    }

    /**
     * كتلة المكتبة في `post-fs-data.sh` — موضعٌ واحد للصلاحية والوسم، ويُستبدل فيه المسار والوسم.
     * و`[ -f ]` تحرس الغياب: مكتبةٌ لم تُنسخ بعد ليست عطبًا يُسقط الإقلاع.
     */
    private val LIBRARY_PERM_BLOCK = listOf(
        "LIB=\"${'$'}MODDIR/$LIBRARY_PLACEHOLDER\"",
        "if [ -f \"${'$'}LIB\" ]; then",
        "  chown 0:0 \"${'$'}LIB\" 2>/dev/null || true",
        "  chmod 0$LIBRARY_MODE \"${'$'}LIB\" 2>/dev/null || true",
        "  chcon \"$LIBRARY_LABEL_PLACEHOLDER\" \"${'$'}LIB\" 2>/dev/null || true",
        "fi",
    ).joinToString("\n")

    /**
     * ملفّات الوحدة كاملة — **والوسيط مسار الجهاز لا مسار الوحدة**، فيُشتقّ مسار الطبقة هنا مرّة
     * واحدة (ولا يوجد مدخلان يفترقان فيصير أحدهما خطأً غير مكتَشف).
     *
     * و`null` حين لا يقبل المسار طبقةً، أو حين يتعذّر توليد `module.prop` — فلا تُكتب وحدةٌ ناقصة
     * يُظنّ أنّها رُكّبت.
     */
    fun files(
        document: AudioEffectsDocument,
        devicePath: String,
        description: String,
        /**
         * مسار المكتبة داخل الوحدة (من [`audioEffectLibraryPlan`]) — **اختياريّ لا مُتجاوَز:**
         * نصوصُ الوحدة تُولَّد وتُقاس بلا مكتبة (وكلّ اختبارات الموجة كذلك)، والمكتبة **خطوةٌ ثانية**
         * تُضاف حين تكون مشحونةً ومعروفة العمود. ولا يُمرَّر مسارٌ مظنون: الخطّة تُرجع `null` فيُبنى بلاها.
         */
        libraryRelativePath: String? = null,
    ): List<AudioSystemModuleFile>? {
        val relativePath = AudioEffectsPaths.moduleRelativePath(devicePath) ?: return null
        val prop = moduleProp(description = description) ?: return null
        return listOf(
            AudioSystemModuleFile("module.prop", prop),
            AudioSystemModuleFile(relativePath, document.toXml()),
            AudioSystemModuleFile("customize.sh", customizeScript(relativePath, libraryRelativePath)),
            AudioSystemModuleFile(
                "post-fs-data.sh",
                postFsDataScript(relativePath, libraryRelativePath),
            ),
        )
    }

    /**
     * بصمة الوحدة — **النصّ الوحيد الذي يُمرَّر إلى المحكِّم ويُقرأ منه**.
     *
     * **ولماذا بصمة لا محتوى:** المحكِّم يقارن `String`، وتمرير ملفّ XML كامل (وقد يبلغ عشرات
     * الكيلوبايتات) عبر جدول الطلبات يثقل كل دورة انحراف. والبصمة تُحسم إلى ٦٤ محرفًا، والمقارنة
     * تبقى **حرفيّة على المضمون** لا على ترتيب كتابة.
     *
     * **و`ABSENT` نتيجةٌ معلَنة لا فراغ:** ملفٌّ غائب (أو بعضه غائب) يعود `ABSENT` — والغياب يختلف
     * عن «ملفٌّ مطابق»، وهو الفرق الذي يمنع أن يُقرأ ملفّ لم يُكتب قطّ «مطابقًا».
     */
    fun signature(files: List<Pair<String, String?>>): String {
        if (files.isEmpty()) return ABSENT
        val encoded = StringBuilder()
        files.sortedBy { it.first }.forEach { (path, content) ->
            if (content == null) return ABSENT
            encoded.append(path).append('\u001F').append(sha256(content)).append('\u001E')
        }
        return sha256(encoded.toString())
    }

    /** بصمة المطلوب — ما يُكتب. */
    fun signatureOf(files: List<AudioSystemModuleFile>): String =
        signature(files.map { it.relativePath to it.content })

    /**
     * بصمة **المقارنة** — تُحسب على المحتوى بعد قصّ الأبيض الطرفيّ، وهذا ما يجب أن يمرّر إلى المحكِّم.
     *
     * **وذلك عطبٌ قِيس قبل أن يُكتَب، لا تنسيق:** قارئ الجذر (`RootFileAccess.read`) يعيد المحتوى
     * **مقصوصًا** (وهو الصواب لعقد `sysfs` التي تُقرأ سطرًا واحدًا). وملفّاتنا تنتهي بسطر جديد — فبصمة
     * المحتوى الخام لا تساوي بصمة ما يُقرأ أبدًا: كل تثبيتٍ يُقرأ «فاشلًا» ثم يُسترجَع، فلا تثبت الطبقة
     * مع أنّ الكتابة صحيحة. فالتطبيع واحد على الطرفين: **قصّ طرفيّ قبل التجزئة**.
     */
    fun comparisonSignature(files: List<AudioSystemModuleFile>): String =
        signatureOf(files.map { file -> file.copy(content = file.content.trim()) })

    const val ABSENT = "absent"

    /** علامتان تُستبدلان في سكربت `post-fs-data.sh` — تُريان في الاختبار ولا تُكتبان على القرص. */
    private const val LIBRARY_PLACEHOLDER = "__library__"
    private const val LIBRARY_LABEL_PLACEHOLDER = "__label__"


    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun singleLine(value: String): String =
        value.replace('\r', ' ').replace('\n', ' ').trim()

    /** صيغة معرّف Magisk — وحدةٌ بمعرّفٍ خارجها لا تُقرأ. */
    private val ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
}
