package nd.max.ui.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import nd.max.ui.mainscreens.UnifiedActivityModel.CardMotion
import nd.max.ui.mainscreens.UnifiedActivityModel.CardOptions
import nd.max.ui.mainscreens.UnifiedActivityModel.CardStyle
import nd.max.ui.mainscreens.UnifiedActivityModel.CardVerbosity
import nd.max.ui.mainscreens.UnifiedActivityModel.SessionPolicy

/**
 * تفضيلات بطاقة النشاط — التخصيص الوحيد الذي يملكه المستخدم على ما تعرضه الشاشة الرئيسية.
 *
 * لماذا ملف مستقل
 * ---------------
 * القرار (أيّ سطر يُعرض، وبأيّ نمط) في `UnifiedActivityModel` وهو **خالص ومُقاس في JVM**. وهذا
 * الملف لا يقرّر شيئًا: يُخزّن الخيارات ويعيدها كما هي، فلا تُختبر السياسة مرّتين ولا تفترق
 * نسختان منها.
 *
 * ولماذا `SharedPreferences` لا ملف خاص: هذه تفضيلات عرض صغيرة لا حالة عتاد، ولا يجوز أن تلمس
 * ملفات المحرّك أو تظهر في تقارير الجهاز — والعتاد يُقرأ من `AppConfig` لا من هنا.
 *
 * وقراءة فاشلة أو قيمة محفوظة غير معروفة **تُرجع الافتراضي** (التلقائي)، فلا تُكسر الشاشة بسبب
 * تفضيل تالف: أسوأ نتيجة ممكنة هي بطاقة بأسلوبها الافتراضي.
 */
object ActivityCardPreferences {

    private const val PREFS = "activity_card_prefs"
    private const val KEY_AUTO = "auto"
    private const val KEY_STYLE = "style"
    private const val KEY_VERBOSITY = "verbosity"
    private const val KEY_MOTION = "motion"
    private const val KEY_SHOW_PER_APP = "show_per_app"
    private const val KEY_SHOW_MAX_AI = "show_max_ai"
    private const val KEY_SHOW_MANUAL = "show_manual"
    private const val KEY_SHOW_MONITORING = "show_monitoring"
    private const val KEY_SESSION = "session"

    private fun prefs(context: Context): SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context?): CardOptions {
        if (context == null) return CardOptions.DEFAULT
        return runCatching {
            val store = prefs(context)
            CardOptions(
                auto = store.getBoolean(KEY_AUTO, true),
                style = store.readEnum(KEY_STYLE, CardStyle.entries, CardStyle.AUTO) { it.token },
                verbosity = store.readEnum(
                    KEY_VERBOSITY,
                    CardVerbosity.entries,
                    CardVerbosity.SIMPLE,
                ) { it.token },
                motion = store.readEnum(KEY_MOTION, CardMotion.entries, CardMotion.FULL) { it.token },
                showPerApp = store.getBoolean(KEY_SHOW_PER_APP, true),
                showMaxAi = store.getBoolean(KEY_SHOW_MAX_AI, true),
                showManual = store.getBoolean(KEY_SHOW_MANUAL, true),
                showMonitoring = store.getBoolean(KEY_SHOW_MONITORING, true),
                session = store.readEnum(KEY_SESSION, SessionPolicy.entries, SessionPolicy.ALWAYS) { it.token },
            )
        }.getOrDefault(CardOptions.DEFAULT)
    }

    fun write(context: Context, options: CardOptions) {
        runCatching {
            prefs(context).edit {
                putBoolean(KEY_AUTO, options.auto)
                putString(KEY_STYLE, options.style.token)
                putString(KEY_VERBOSITY, options.verbosity.token)
                putString(KEY_MOTION, options.motion.token)
                putBoolean(KEY_SHOW_PER_APP, options.showPerApp)
                putBoolean(KEY_SHOW_MAX_AI, options.showMaxAi)
                putBoolean(KEY_SHOW_MANUAL, options.showManual)
                putBoolean(KEY_SHOW_MONITORING, options.showMonitoring)
                putString(KEY_SESSION, options.session.token)
            }
        }
    }

    /** العودة إلى الافتراضي — الطريق الوحيد للخروج من تخصيص أفسد الشكل. */
    fun reset(context: Context) = write(context, CardOptions.DEFAULT)

    /**
     * قيمة مخزّنة **بالرمز** لا بالاسم: الرمز هو العقد بين إصدارين (`token`)، والاسم الداخلي
     * للصنف ليس عقدًا. ونمط مجهول (إصدار أقدم/أحدث أو ملف مُعدَّل) يُرجع الافتراضي بدل أن يرمي.
     */
    private fun <T> SharedPreferences.readEnum(
        key: String,
        values: List<T>,
        fallback: T,
        token: (T) -> String,
    ): T {
        val stored = getString(key, null) ?: return fallback
        return values.firstOrNull { token(it).equals(stored, ignoreCase = true) } ?: fallback
    }
}
