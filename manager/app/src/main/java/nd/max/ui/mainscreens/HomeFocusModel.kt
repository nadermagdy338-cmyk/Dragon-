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
