package nd.max.ui.settings

import android.content.Context
import android.content.res.Configuration
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
    data class Entry(val tag: String, val nativeName: String, val localizedName: String)

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
     * كل الخيارات جاهزة للعرض: الاسم الأصلي أولًا، والاسم بلغة الواجهة كسطر ثانوي عندما يختلفان.
     * الترتيب بترتيب أبجدي صحيح للغة الواجهة (Collator) لا بترتيب ASCII.
     */
    fun entries(uiLocale: Locale = Locale.getDefault()): List<Entry> {
        val collator = Collator.getInstance(uiLocale)
        return CODES
            .map { tag ->
                Entry(
                    tag = tag,
                    nativeName = nativeName(tag),
                    localizedName = displayName(tag, uiLocale),
                )
            }
            .sortedWith(compareBy(collator) { it.nativeName })
    }

    private fun localeOf(tag: String): Locale = Locale.forLanguageTag(normalize(tag))
}
