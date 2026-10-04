/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.text.Collator
import java.util.Locale

/**
 * مصدر الحقيقة الواحد للغات التطبيق: القائمة، والتعيين إلى وسوم BCP-47، والأسماء، والتطبيق عند الإقلاع.
 *
 * لماذا هذا الملف: منطق تبديل اللغة كان موجودًا في `SettingsViewModel` كسلسلة `when` طويلة، لكن
 * الواجهة لم تكن تستدعيه أبدًا ولم يكن الاختيار المحفوظ يُطبَّق عند إقلاع العملية — فكانت اللغة
 * تعود للافتراضي بعد كل قتل للتطبيق. هنا يُجمع التعيين في مكان واحد يستخدمه الـViewModel والإقلاع معًا.
 *
 * **عدد اللغات مشتق من مجلدات `res/values-*` الفعلية (٨٤ مجلدًا + الإنجليزية الافتراضية = ٨٥)**،
 * فلا تُضاف لغة هنا قبل أن تكون موجودة هناك. أداة التحقق: `tools/i18n_coverage.py`.
 */
object AppLanguage {

    /** الاختيار الافتراضي: يتبع لغة النظام. يُعرض أول الخيارات دائمًا. */
    const val AUTO = "system"

    private const val PREFS_FILE = "settings_prefs"
    private const val PREFS_KEY = "app_language_code"

    /**
     * أكواد اللغات المدعومة، مرتّبة أبجديًا، ومشتقة من `manager/app/src/main/res/values-*`.
     *
     * ملاحظات التعيين: `b+sr+Latn` ← `sr-Latn` (أبجدية لاتينية)، و`en-rAU` ← `en-AU` (المجلدات
     * تستخدم صيغة الأندرويد `-r`). و`en` مُضافة يدويًا لأن الإنجليزية هي `values/` الافتراضية.
     *
     * أكواد المجلدات القديمة (`in` الإندونيسية، `iw` العبرية، `tl` التاغالوغية) مُسجّلة هنا
     * بصيغتها الحديثة (`id`, `he`, `fil`) لأن `setApplicationLocales` يُمرّر الوسم إلى نظام
     * أندرويد، والنظام يعرض الصيغة الحديثة في «اللغات لكل تطبيق»، بينما `aapt2` يوفّق بين
     * الصيغتين عند قراءة الموارد من `values-in` / `values-iw` / `values-tl`.
     */
    private val CODES: List<String> = listOf(
        "af", "am", "ar", "as", "az", "be", "bg", "bn", "bs", "ca", "cs", "da", "de", "el", "en", "en-AU",
        "en-CA", "en-GB", "en-IN", "es", "es-US", "et", "eu", "fa", "fi", "fil", "fr", "fr-CA", "gl", "gu",
        "he", "hi", "hr", "hu", "hy", "id", "is", "it", "ja", "ka", "kk", "km", "kn", "ko", "ky", "lo",
        "lt", "lv", "mk", "ml", "mn", "mr", "ms", "my", "nb", "ne", "nl", "or", "pa", "pl", "pt", "pt-BR",
        "pt-PT", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sr-Latn", "sv", "sw", "ta", "te", "th", "tr",
        "uk", "ur", "uz", "vi", "zh-CN", "zh-HK", "zh-TW", "zu",
    )

    /** خيار واحد في القائمة: الوسم، واسمه بلغته، واسمه بلغة الواجهة الحالية. */
    /**
     * [englishName] حقلٌ ثالث لا زينة: أمر المالك صار أن يُقرأ اسم اللغة **بالإنجليزية عنوانًا**
     * واسمها الأصلي سطرًا ثانويًّا (`Arabic / العربية`)، فيحتاج المنتقي اسمًا بالإنجليزية لكل
     * لغة. و[localizedName] بقي لأن البحث يجب أن يطابق ما يقرأه المستخدم بلغته أيضًا.
     */
    data class Entry(
        val tag: String,
        val nativeName: String,
        val localizedName: String,
        val englishName: String,
    )

    /**
     * يُطبّع الوسم إلى صيغة BCP-47 كاملة.
     * يحافظ على السلوك السابق حرفيًا (نفس الحالات الخاصة في `SettingsViewModel`) ويضيف `he-IL`،
     * لأن الكود القديم `iw` كان يمرّ كما هو بلا تطبيع.
     */
    fun normalize(tag: String): String = when (tag) {
        "ru" -> "ru-RU"
        "uk" -> "uk-UA"
        "be" -> "be-BY"
        "kk" -> "kk-KZ"
        "id" -> "id-ID"
        "he" -> "he-IL"
        "fil" -> "fil-PH"
        "en" -> "en-US"
        else -> tag
    }

    /** قائمة اللغات لأجل AppCompat. [AUTO] تعني قائمة فارغة = اتبع النظام. */
    fun toLocaleList(tag: String): LocaleListCompat =
        if (tag == AUTO) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(normalize(tag))

    /** يُطبّق اللغة فورًا على كل نشاطات التطبيق (AppCompat يعيد إنشاء النشاط عند الحاجة). */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(toLocaleList(tag))
    }

    /** الاختيار المحفوظ. يقرأ الملف نفسه الذي يكتبه [SettingsPreference] (`settings_prefs`). */
    fun savedTag(context: Context): String =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).getString(PREFS_KEY, AUTO) ?: AUTO

    /**
     * يُطبَّق عند إقلاع العملية قبل رسم أي شاشة، وإلا نسي التطبيق اختيار المستخدم بعد كل قتل للعملية.
     *
     * هذا المسار وحده لا يكفي على أندرويد ١٠–١٢: `AppCompatDelegate` يُطبّق اللغة عبر نشاط
     * `AppCompatActivity`، ونشاط هذا التطبيق `ComponentActivity` بسمة منصّية — فالتطبيق الفعلي هناك
     * يأتي من [wrap]. لا يُحذف أحدهما: `apply` هو ما يعرف به النظام لغة التطبيق ويُظهرها في الإعدادات
     * (API 33+)، و`wrap` هو ما يجعلها مرئية على الإصدارات الأقدم.
     */
    fun applySaved(context: Context) {
        apply(savedTag(context))
    }

    /**
     * يُعيد إنشاء النشاط بعد تبديل اللغة — فيُرى الاختيار **فورًا** لا بعد إقلاع جديد.
     *
     * **ولماذا لزم هذا أصلًا:** كل مسار اللغة في هذا الملفّ كان سليمًا إلا أنه غير مرئي
     * في اللحظة. `[apply]` تُبلّغ النظام بالاختيار، و`[wrap]` تقرأه في `attachBaseContext`
     * — وكلاهما يعمل **عند إنشاء النشاط**. فمن يختار لغة كان يراها بعد إغلاق التطبيق
     * وفتحه، وهو ما أبلغ عنه المالك.
     *
     * **والشرط على `SDK_INT` ليس تحوّطًا:** من `TIRAMISU` (‏API 33) صار تبديل اللغة
     * إطارًا في النظام (`LocaleManager.setApplicationLocales`)، والنظام يُعيد إنشاء
     * النشاطات بنفسه. وقبلها لا شيء يُعيد الإنشاء: `MainActivity` هو `ComponentActivity`
     * بلا `AppCompatDelegate` — وهي فقط من يملك منطق الإنشاء التلقائي في AppCompat.
     * وإعادة الإنشاء هنا لا تغيّر النتيجة على 33+ إلا بإنشاء ثانٍ بلا فائدة، فتُترك للإطار.
     *
     * **وحدّها المُعلَن:** هذا لا يعني «اختُبر على جهاز» — مسار API 33+ (إعادة الإنشاء
     * من الإطار) لم يُقَس على جهاز في هذه البيئة، والذي قِيس هو أن مسار ما قبل 33 كان
     * **بلا إعادة إنشاء إطلاقًا**، وهذا ما يغلقه السطر هنا.
     */
    fun reload(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity.recreate()
    }

    /**
     * النشاط الحاوي للسياق — يُستدعى من الواجهة لأنّ `Activity` غير متاحة في `LocalContext`
     * مباشرةً حين يُغلّفها سياق آخر. والتنقيب آمن الاكتمال: `while` ينتهي عند `baseContext` فارغ.
     */
    fun activityOf(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * اسم **لغة الجهاز** بلغة الواجهة — سطر ثانويّ لخيار «لغة النظام» في المنتقي، فيرى
     * المستخدم ما سيُطبَّق فعلًا بدل أن يخمّنه.
     *
     * **وهو ليس `Locale.getDefault()`:** ذاك يُصبح لغة **التطبيق** بعد `[wrap]`
     * (`Locale.setDefault(locale)`)، فسؤالُه بعد تبديل لغة يعطي الجواب عن الاختيار لا عن
     * الجهاز. والمصدر الصحيح هو قائمة النظام `LocaleListCompat.getDefault()`.
     */
    fun systemLanguageName(uiLocale: Locale = Locale.getDefault()): String {
        val device = LocaleListCompat.getDefault()[0] ?: return ""
        return device.getDisplayLanguage(uiLocale).replaceFirstChar { it.uppercase(uiLocale) }
    }

    /**
     * يلفّ سياق النشاط باللغة المختارة — يمنح كل استدعاء `resources` داخل هذا النشاط اللغة الصحيحة.
     * يُستدعى من `attachBaseContext`، أي قبل إنشاء الواجهة وقبل تحمّل أي مورد.
     * اتجاه التخطيط يُضبط أيضًا (`setLayoutDirection`) فاللغات RTL تنقلب بلا عمل إضافي في الواجهة.
     */
    fun wrap(base: Context): Context {
        val tag = savedTag(base)
        if (tag == AUTO) return base
        val locale = localeOf(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    /** اسم الوسم بلغة الواجهة الحالية. [AUTO] يُترجم إلى اسم لغة النظام الفعلية. */
    fun displayName(tag: String, uiLocale: Locale = Locale.getDefault()): String =
        if (tag == AUTO) uiLocale.getDisplayName(uiLocale) else localeOf(tag).getDisplayName(uiLocale)

    /** اسم اللغة بلغتها هي («العربية» لا «Arabic») — هكذا يجد المستخدم لغته بلا وسيط. */
    fun nativeName(tag: String): String {
        val locale = localeOf(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

    /**
     * كل الخيارات جاهزة للعرض: الاسم بلغة الواجهة أولًا، والاسم الأصلي كسطر ثانوي عندما يختلفان.
     * الترتيب بترتيب أبجدي صحيح للغة الواجهة (Collator) لا بترتيب ASCII.
     *
     * **وتغيير الترتيب والاسم الأول مقصود (طلب المالك: «حسّن طريقة عرض اللغات»).** كان
     * الترتيب بالاسم الأصلي، وهو «أصحّ» نظريًّا لمن لا يقرأ لغة الواجهة — لكنه يُنتج قائمة
     * **متعدّدة الأبجديات** فيُرى في اللقطة: أردو، العربية، فارسية (لأن مُرتّب العربية يضع
     * المحارف العربية أولًا) ثم Afrikaans, Azerbaycan, Bosanski. فالقارئ لا يجد أبجدية
     * واحدة يمسحها بعينه، ولو كان يبحث عن لغته بلغته لوجدها بالبحث (وهو المطابقة التي
     * تشمل الاسم الأصلي والاسم المعرّب والوسم معًا).
     *
     * والنتيجة: المسح بعين واحدة في لغة القارئ، والاسم الأصلي **باقٍ في الصفّ** كما كان
     * (سطرًا ثانيًا لا عنوانًا) فلم يُفقد شيء — تغيّر **موضع** الاسم لا وجوده.
     */
    fun entries(uiLocale: Locale = Locale.getDefault()): List<Entry> {
        val collator = Collator.getInstance(uiLocale)
        return CODES
            .map { tag ->
                Entry(
                    tag = tag,
                    nativeName = nativeName(tag),
                    localizedName = displayName(tag, uiLocale),
                    englishName = displayName(tag, Locale.ENGLISH),
                )
            }
            // والترتيب على الاسم **الإنجليزي** لأنه هو المعروض عنوانًا: قائمة تُرتَّب بمفتاح غير
            // المفتاح الذي يُقرأ تبدو عشوائية في كل قراءة عربية.
            .sortedWith(compareBy(collator) { it.englishName })
    }

    /**
     * لغات الجهاز المضبوطة فعليًّا (`LocaleList.getDefault()`)، مرَّت على قائمة المدعوم
     * بترتيب النظام ونزع التكرار.
     *
     * وهذا **قياس لحالة الجهاز لا تخمين**: ما ضبطه المستخدم في «لغة النظام» هو بالتأكيد
     * مرشّحه الأول داخل التطبيق، فرفعه إلى أعلى الورقة يعني أنه لا يمرّ على ٨٥ صفًّا ليصل إليه.
     *
     * والمطابقة على **اللغة** أولًا ثم البلد: جهاز ضبط `ar-EG` يقابل `ar` (لأن `ar-EG` غير
     * مدعوم بالاسم) وجهاز ضبط `pt-BR` يقابل `pt-BR` لا `pt` — ولو بدأنا بالبلد لكان كل
     * اختيار جهاز إقليميّ يسقط إلى لغته العامة بلا سبب.
     */
    fun deviceTags(): List<String> {
        val supported = CODES.map { tag -> tag to localeOf(tag) }
        val configured = LocaleListCompat.getDefault()
        val out = LinkedHashSet<String>()
        for (index in 0 until configured.size()) {
            val device = configured[index] ?: continue
            val exact = supported.firstOrNull { (_, locale) ->
                locale.language.equals(device.language, ignoreCase = true) &&
                    locale.country.equals(device.country, ignoreCase = true)
            }
            val byLanguage = supported.firstOrNull { (_, locale) ->
                locale.language.equals(device.language, ignoreCase = true)
            }
            (exact ?: byLanguage)?.let { out += it.first }
        }
        return out.toList()
    }

    private fun localeOf(tag: String): Locale = Locale.forLanguageTag(normalize(tag))
}
