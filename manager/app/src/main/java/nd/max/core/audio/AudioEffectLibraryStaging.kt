/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary or confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **إخراج مكتبة المؤثّر من حزمة التطبيق** إلى ملفٍّ حقيقيّ يقرؤه الجذر.
 *
 * **ولماذا هذه الخطوة لازمةٌ لا زينة (مقيس، تكملة ٢٤١):** مسار `applicationInfo.nativeLibraryDir`
 * **ليس مجلّدًا حقيقيًّا** على الأجهزة الحديثة: مدخلٌ في `AndroidManifest` اسمه `extractNativeLibs`
 * يساوي `false` افتراضيًّا في التطبيقات الحديثة، فتُترك المكتبات **داخل الحزمة** (`base.apk!/lib/…`)
 * ولا تُفكّ إلى قرص. فـ`File(nativeLibraryDir + "/libmaxfx.so").exists()` = `false`، و`cp` يفشل،
 * **فتُثبَّت الطبقة ولا مكتبة** — وهو بعينه «صفر تغيير» في لوق المالك لكن بسببٍ رابع لم يكن معروفًا.
 *
 * **والحلّ المقيس:** التطبيق **يقرأ حزمته بنفسه** (`ZipFile`) ويستخرج المدخل `lib/<abi>/<اسم>.so`
 * إلى ملفٍّ في مجلّده الخاصّ، ثمّ ينسخه الجذر من هناك إلى مجلّد مكتبات المنصّة. وهذا يعمل في الحالتين
 * (`extractNativeLibs` صحيحًا أو خاطئًا) **وبلا تغيير في بيان التطبيق** — فلا نطلب إعدادًا يُنقص
 * الأداء (فكّ المكتبات عند التثبيت) من أجل ملفّ واحد.
 *
 * **وصافٍ على JVM:** لا `android.*` — `java.util.zip` و`java.io` فقط، فيُقاس بحزمةٍ حقيقيّة في الاختبار.
 */
package nd.max.core.audio

import java.io.File
import java.util.zip.ZipFile

/**
 * **حالة المكتبة كما قِيست على الجهاز** — خمس حالات لا سادسة، وكلّها نتيجةُ قياسٍ لا توقّع.
 *
 * **ولماذا نواةٌ نقيّة لا شروطٌ في الواجهة:** الطبقة نفسها هي التي تحكم على الطبقة النظاميّة (عُرف
 * المستودع: الحكم يُقاس في نموذجٍ نقيّ والواجهة ترسمه) — وهذا هو **الموضع الذي وقع فيه العطب أكثر
 * من مرّة:** الفرق المقيس بين «نجحتْ» و«لا يُسمع فرق» هو بالضبط هذه الحالات الخمس، وشرطٌ مكتوب في
 * Compose لا يُختبر أبدًا.
 */
enum class AudioEffectLibraryState(val token: String) {
    /** لا مصدرَ معلَن أصلًا ⇒ **لا شيء يُنسخ** (تُقال لا تُخفى). */
    NOT_SHIPPED("library-not-shipped-in-app"),

    /** المصدر معلَن والملفّ **غائب** عن مسار البحث ⇒ المصنع لا يجده. */
    NOT_INSTALLED("library-not-installed"),

    /** موجودةٌ مقروءة (٠٦٤٤) ⇒ الشرط الذي يقرأه المصنع مكتمل. */
    READABLE("library-installed-readable"),

    /** موجودةٌ وصلاحيّتها ليست قراءةً للعالم ⇒ **يُطبع «can't find» والعلّة صلاحية**. */
    NOT_READABLE("library-installed-not-readable"),

    /** موجودةٌ ولم تُقرأ صلاحيّتها (بلا جذر مثلًا) ⇒ **«لم تُقَس» لا «ممنوعة»** (الجهل ليس نفيًا). */
    UNMEASURED("library-state-unmeasured"),
}

/**
 * يحكم من ثلاثة مُدخلات مقيسة — **والترتيب محسوب:** غيابُ الخطّة أسبق (لا شيء يُنسخ)، ثمّ
 * **جهلُ الوجود** (‏`null` ⇒ لا نفي ولا إثبات)، ثمّ الغياب المقيس، ثمّ الصلاحية.
 * و`mode == null` يعني **لم تُقرأ** فلا يُدّعى منعٌ ولا قراءة.
 */
fun audioEffectLibraryState(
    hasPlan: Boolean,
    installed: Boolean?,
    mode: String?,
): AudioEffectLibraryState = when {
    !hasPlan -> AudioEffectLibraryState.NOT_SHIPPED
    // **والجهل ليس نفيًا (ADR-07):** ما لم يُقس وجوده لا يُقال «غير منسوخة».
    installed == null -> AudioEffectLibraryState.UNMEASURED
    !installed -> AudioEffectLibraryState.NOT_INSTALLED
    mode == LIBRARY_MODE -> AudioEffectLibraryState.READABLE
    mode != null -> AudioEffectLibraryState.NOT_READABLE
    else -> AudioEffectLibraryState.UNMEASURED
}

object AudioEffectLibraryStaging {

    /** أعمدة ٦٤-بت المعروفة — وما عداها ٣٢-بت، وكذلك مدخل الحزمة. */
    private val ABIS_64 = setOf("arm64-v8a", "x86_64", "riscv64")
    private val ABIS_32 = setOf("armeabi-v7a", "armeabi", "x86")

    /** هل نعرف هذا العمود أصلًا؟ — عمودٌ مجهول لا يُبنى له مدخلٌ مظنون. */
    fun knowsAbi(abi: String): Boolean = abi.trim() in ABIS_64 || abi.trim() in ABIS_32

    /**
     * اسم المدخل **داخل الحزمة** — `lib/arm64-v8a/libmaxfx.so`. وصيغته من عُرف AGP لا من تخمين:
     * المكتبات تُغلَّف تحت `lib/<abi>/<اسم الملفّ>`، وهو ما يُقرأ بـ`unzip -l base.apk` على أيّ تطبيق
     * فيه كود أصليّ. وعمودٌ مجهول أو اسمٌ فيه `/` ⇒ `null`، فلا يُبحث عن مدخلٍ لا وجود له.
     */
    fun entryName(abi: String, fileName: String): String? {
        val cleanAbi = abi.trim()
        if (!knowsAbi(cleanAbi)) return null
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        return "lib/$cleanAbi/$fileName"
    }

    /**
     * يستخرج المكتبة من الحزمة إلى [targetDir] ويُعيد **مسار الملفّ المُخرج**، أو `null` بلا استخراج.
     *
     * **والخطوات، وكلٌّ منها سببُ فشلٍ يُقال:**
     * ١) الحزمة تُفتح للقراءة (`ZipFile`) — `null` إن غاب الملفّ أو لم تكن حزمةً صالحة (فتُقال
     *    «المكتبة غير مشحونة» لا «نُسخت»).
     * ٢) المدخل يُوجد بالاسم المحسوب حصرًا — **لا بحثٌ جزئيّ ولا أوّل مدخل شبيه**: مكتبةٌ أخرى
     *    موجودة في الحزمة ليست مكتبتنا.
     * ٣) الاستخراج إلى ملفّ مؤقّت (`<name>.tmp`) ثمّ `renameTo` — فالملفّ لا يظهر نصف مكتوب قطّ،
     *    ودورةٌ قُوطعت لا تترك «مكتبة» مبتورة تُنسخ إلى النظام.
     *
     * **والصلاحية 0600 مقصودة:** الملفّ يُكتب في مجلّد التطبيق الخاصّ، **والجذر يقرأ ما لا يقرؤه غيره**
     * (‏`su` يعمل بـ`uid 0` فيتجاوز صلاحيات المالك). ولا حاجة لفتح الملفّ للعالم.
     */
    fun stage(
        apkPath: String?,
        abi: String,
        fileName: String,
        targetDir: String,
    ): String? {
        val entry = entryName(abi, fileName) ?: return null
        val path = apkPath?.trim()
        if (path.isNullOrEmpty()) return null
        val directory = File(targetDir)
        if (!directory.isDirectory) return null
        val destination = File(directory, fileName)
        val staged = File(directory, "$fileName.tmp")

        return runCatching {
            ZipFile(path).use { zip ->
                val zipEntry = zip.getEntry(entry) ?: return@use null
                zip.getInputStream(zipEntry).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                zipEntry.size
            }
        }.getOrNull()?.let { size ->
            // **وحزمةٌ بمكتبةٍ فارغة ليست مكتبة:** لا نُخرج ملفًّا بلا محتوى وندّعي أنّه شُحن.
            if (size <= 0L || staged.length() <= 0L) {
                staged.delete()
                return null
            }
            if (destination.exists()) destination.delete()
            if (!staged.renameTo(destination)) {
                staged.delete()
                return null
            }
            destination.absolutePath
        }
    }

    /**
     * **الإخراج ومسار المكتبة معًا** — يجرّب الاستخراج من الحزمة **ثمّ** يقع على مجلّد المكتبات
     * المفكوك إن كان موجودًا (أجهزةٌ `extractNativeLibs=true`)، ويُعيد `null` إن لم يوجد أيّ منهما.
     *
     * **والترتيب مقصود:** الحزمة أوّلًا لأنّها تعمل في **الحالتين**؛ والمجلّد المفكوك احتياطًا.
     * ومن يُرجع `null` هنا يعني **«المكتبة غير مشحونة»** — وهي حالةٌ تُقال لا تُتجاهل.
     */
    fun sourceFor(
        apkPath: String?,
        nativeLibraryDir: String?,
        abi: String,
        fileName: String,
        targetDir: String,
    ): String? {
        stage(apkPath, abi, fileName, targetDir)?.let { return it }
        val dir = nativeLibraryDir?.trim()?.trimEnd('/')
        if (dir.isNullOrEmpty()) return null
        val extracted = File("$dir/$fileName")
        return if (extracted.isFile && extracted.length() > 0L) extracted.absolutePath else null
    }
}

/**
 * **ما يُعلنه السطح مرّةً واحدة** ليُبنى منه مصدر المكتبة في كلّ تثبيت — أربعةُ مدخلات لا أكثر.
 *
 * **ولماذا كائنٌ ولا أربعة حقولٍ في الـViewModel:** كان مسار الحزمة والمجلّد المفكوك ومجلّد الإخراج
 * والعمود أربعةَ حقولٍ تُنسَخ في الـ`ViewModel` ثمّ يُبنى منها المصدر — **فبلغ الملفّ السقف المجمَّد**
 * (`code_health`: ١٠١٢ سطرًا > ١٠٠٠). فالمسؤوليّة انتقلت إلى طبقة الأوديو، وبقي في الـ`ViewModel` سطرٌ
 * واحدٌ يُعلنها. والقيمة الافتراضيّة **لا شيء معلَن** — فتُقرأ الحاجة بسببها الصريح لا بـ`null` صامت.
 */
data class AudioEffectLibrarySource(
    /** مسار حزمة التطبيق التي **فيها** مدخل `lib/<abi>/<اسم>.so`. */
    val apkPath: String? = null,
    /** مجلّد المكتبات المفكوك — احتياطٌ لأجهزة `extractNativeLibs=true`. */
    val nativeLibraryDir: String? = null,
    /** مجلّد الإخراج في التطبيق — **والجذر يقرأ ما لا يقرؤه غيره**. */
    val stagingDir: String? = null,
    val abi: String = "",
) {

    /** مصدر ملفّ المكتبة **المقيس** — من الحزمة أوّلًا ثمّ من المجلّد، أو `null` (غير مشحونة). */
    fun sourceFor(fileName: String): String? = AudioEffectLibraryStaging.sourceFor(
        apkPath = apkPath,
        nativeLibraryDir = nativeLibraryDir,
        abi = abi,
        fileName = fileName,
        targetDir = stagingDir.orEmpty(),
    )

    /**
     * وخطّة النسخ لهذا الملفّ — و`null` تعني **«المكتبة غير مشحونة»**، وهي حالةٌ تُقال
     * (`effect-library-not-shipped-in-app`) لا تُتجاهل فتُثبَّت طبقةٌ بلا مكتبة.
     */
    fun planFor(fileName: String): AudioEffectLibraryPlan? =
        audioEffectLibraryPlan(sourceFor(fileName), abi, fileName)
}
