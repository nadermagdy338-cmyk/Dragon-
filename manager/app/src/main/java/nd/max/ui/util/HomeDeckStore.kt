/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * تخزين «منصة التحكم»: **الوضع والاختيار اليدوي فقط** — ولا عدّاد هنا.
 *
 * **والعدّاد كان هنا، وانتقل في الجولة ٢٠٣:** كان للمنصة سجلُّها الخاص (`home_deck_uses_*`)
 * وللمُوجِّد لا سجلّ، وسجلّان يقيسان الفتح نفسه **يفترقان يومًا** فيقول أحدهما «الأكثر
 * استعمالًا» ويقول الآخر غيره — وهو عطبٌ لا يُرى إلا بعد أسابيع من الاستعمال. صار العدّ الواحد
 * **بالمسار** في `ScreenUsageStore` (هوية الوجهة في التطبيق كلّه، ADR-02)، ومنه تقرأ المنصة عبر
 * [nd.max.ui.mainscreens.homeDeckUsage] كما يقرأ المُوجِّد. فما بقي في هذا الملفّ شيءٌ لا يقرؤه
 * إلا المنصة: الوضع والاختيار.
 *
 * والملفّ لا يقرّر شيئًا: **القاعدة في `HomeDeckModel`** (صافية ومقيسة على JVM)، وهو يقرأ
 * ويكتب فقط. فحفظٌ قديم أو ناقص من نسخة سابقة لا يُعرض ناقصًا: النموذج يُكمله بالافتراضيّ.
 */
package nd.max.ui.util

import android.content.Context
import android.content.SharedPreferences
import nd.max.ui.mainscreens.HomeDeckMode

class HomeDeckStore(private val prefs: SharedPreferences) {

    /** الوضع — والتلقائيّ هو الافتراضيّ، وأي قيمة غير معروفة تُقرأ تلقائيًّا لا فراغًا. */
    var mode: HomeDeckMode
        get() = if (prefs.getString(KEY_MODE, null) == MODE_MANUAL) {
            HomeDeckMode.Manual
        } else {
            HomeDeckMode.Auto
        }
        set(value) {
            prefs.edit()
                .putString(KEY_MODE, if (value == HomeDeckMode.Manual) MODE_MANUAL else MODE_AUTO)
                .apply()
        }

    /** الاختيار اليدوي (مفاتيح البطاقات) — فارغ يعني: لم يختر المستخدم بعد. */
    var manualKeys: List<String>
        get() = prefs.getString(KEY_MANUAL, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) {
            prefs.edit().putString(KEY_MANUAL, value.joinToString(",")).apply()
        }

    /** يعيد المنصة إلى الافتراضيّ: تلقائيًّا وبالأربعة القائمة. */
    fun resetDefault() {
        prefs.edit().remove(KEY_MODE).remove(KEY_MANUAL).apply()
    }

    companion object {
        /** ملفّ التفضيلات المستعمل في كل التطبيق — لا ملفّ ثانٍ لمنصة واحدة. */
        private const val PREFS = "settings"
        private const val KEY_MODE = "home_deck_mode"
        private const val KEY_MANUAL = "home_deck_manual"
        private const val MODE_AUTO = "auto"
        private const val MODE_MANUAL = "manual"

        fun of(context: Context): HomeDeckStore =
            HomeDeckStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
