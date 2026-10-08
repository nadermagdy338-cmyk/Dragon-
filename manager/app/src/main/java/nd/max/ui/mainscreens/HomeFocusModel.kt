/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بطاقة «التخزين يكاد يمتلئ» في الرئيسية — **متى تُعرض، ومتى يُمسح إخفاؤها** (طلب المالك).
 *
 * الحدّ نفسه الذي كانت البطاقة تطبّقه قبل الإخفاء (أقلّ من ١٠٪ حرّة)، فلا عتبتان تفترقان. والإخفاء زرّ في
 * البطاقة نفسها يحفظه الجهاز. ويُمسح حين يُقاس القرص سليمًا، فإن امتلأ من جديد عادت البطاقة. والمجهول
 * (لا قياس بعد) لا يُعرض ولا يُمسح، لأن المجهول ليس سلامة (ADR-07).
 */
package nd.max.ui.mainscreens

import android.content.Context
import android.content.SharedPreferences

/** حالة القرص لهذه البطاقة: مجهولة (لا قياس بعد) · سليمة · يكاد يمتلئ. */
enum class HomeStorageState { UNKNOWN, HEALTHY, FULL }

/** الخلل الذي تُظهره بطاقة الرئيسية الواحدة: حرارة، أو تخزين، أو ذاكرة. */
enum class HomeFocusKind { HEAT, STORAGE, MEMORY }

object HomeFocusModel {

    /** أقلّ نسبة حرّة تبقى سليمة؛ دونها «يكاد يمتلئ». */
    const val LOW_FREE_FRACTION = 0.10f

    /** الحالة من الحجم المستعمل والإجمالي بالجيجابايت. الإجمالي غير المقروء صفر فيبقى مجهولًا. */
    fun storageState(totalGb: Float, usedGb: Float): HomeStorageState = when {
        totalGb <= 0f -> HomeStorageState.UNKNOWN
        (totalGb - usedGb) / totalGb < LOW_FREE_FRACTION -> HomeStorageState.FULL
        else -> HomeStorageState.HEALTHY
    }

    /** البطاقة تظهر حين يكاد القرص يمتلئ ولم يُخفِها المستخدم. */
    fun storageCardVisible(state: HomeStorageState, hidden: Boolean): Boolean =
        state == HomeStorageState.FULL && !hidden

    /** حرارة تبلغ هذا الحدّ فأعلى تُعدّ خللًا (مطابقة لحدّ الساخنة في `heatWordRes`). */
    const val HEAT_ALERT_C = 45

    /** امتلاء الذاكرة من هذه النسبة فأعلى يُعدّ خللًا. */
    const val RAM_ALERT_FRACTION = 0.90f

    /**
     * الخلل الذي تُظهره البطاقة، بالأولوية: حرارة، ثم تخزين (إن كان مُعرَّضًا ولم يُخفَ)، ثم ذاكرة.
     * هذه القاعدة وحدها تقرّر الاختيار **والترتيب** معًا، فلا تقول البطاقة شيئًا والترتيب شيئًا آخر.
     */
    fun focusKind(heatC: Int, ramFraction: Float, storageVisible: Boolean): HomeFocusKind? = when {
        heatC >= HEAT_ALERT_C -> HomeFocusKind.HEAT
        storageVisible -> HomeFocusKind.STORAGE
        ramFraction >= RAM_ALERT_FRACTION -> HomeFocusKind.MEMORY
        else -> null
    }

    /** الإخفاء يُمسح حين يُقاس القرص سليمًا فقط. */
    fun clearsHide(state: HomeStorageState): Boolean = state == HomeStorageState.HEALTHY
}

/** سجلّ إخفاء بطاقة التخزين على الجهاز وحده. */
class HomeFocusStore(private val prefs: SharedPreferences) {

    /** هل أخفى المستخدم بطاقة التخزين؟ يصمد حتى يُقاس القرص سليمًا (`HomeFocusModel.clearsHide`). */
    var storageHidden: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_HIDDEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_STORAGE_HIDDEN, value).apply()
        }

    companion object {
        private const val PREFS = "max_home_focus"
        private const val KEY_STORAGE_HIDDEN = "storage_hidden"

        fun of(context: Context): HomeFocusStore =
            HomeFocusStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
