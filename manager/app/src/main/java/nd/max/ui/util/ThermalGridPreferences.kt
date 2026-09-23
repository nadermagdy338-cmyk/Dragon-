package nd.max.ui.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * مخزن اختيار المجسّات المثبَّتة — يحفظ ويعيد فقط، **بلا قرار واحد**: التطبيع في
 * [ThermalGridModel] حتى لا تُختبر السياسة مرّتين ولا تفترق نسختان منها (نفس سبب فصل
 * `ActivityCardPreferences` عن `UnifiedActivityModel`).
 *
 * وقراءة فاشلة أو قيمة محفوظة تالفة **تُرجع الافتراضي**، فلا تُكسر الشاشة بسبب تفضيل
 * تالف: أسوأ نتيجة ممكنة هي الشبكة الأربعية المألوفة.
 */
object ThermalGridPreferences {

    private const val PREFS = "thermal_grid_prefs"
    private const val KEY_PINNED = "pinned"
    private const val SEPARATOR = ","

    fun read(context: Context?): List<String> {
        if (context == null) return ThermalGridModel.DEFAULT
        val raw = runCatching { prefs(context).getString(KEY_PINNED, null) }.getOrNull()
            ?: return ThermalGridModel.DEFAULT
        return ThermalGridModel.normalize(raw.split(SEPARATOR))
    }

    fun write(context: Context, categories: List<String>) {
        runCatching {
            prefs(context).edit {
                putString(KEY_PINNED, ThermalGridModel.normalize(categories).joinToString(SEPARATOR))
            }
        }
    }

    /** العودة إلى الشبكة الافتراضية — الطريق الوحيد للخروج من اختيار أفسد العرض. */
    fun reset(context: Context) = write(context, ThermalGridModel.DEFAULT)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
