/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * سجلّ **جولة أول فتح** (`HomeTourModel` و`HomeTourOverlay`) على الجهاز وحده.
 *
 * البانرات المتحركة أُزيلت بأمر المالك، فلم يبقَ في هذا الملف إلا السجلّ: هل أُتمّت الجولة؟ و`?` في
 * الرأس يعيدها بطلب صريح.
 */
package nd.max.ui.mainscreens

import android.content.Context
import android.content.SharedPreferences

class HomeGuideStore(private val prefs: SharedPreferences) {

    /** هل أُتمّت الجولة (بـ`Skip` أو بالوصول إلى آخر بطاقة وضغط `Done`)؟ سجلّ مستقلّ على الجهاز. */
    var tourFinished: Boolean
        get() = prefs.getBoolean(KEY_TOUR_FINISHED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TOUR_FINISHED, value).apply()
        }

    /** `?` في الرأس: يُعاد العرض بطلب صريح. */
    fun restart() {
        tourFinished = false
    }

    companion object {
        private const val PREFS = "max_home_guide"
        private const val KEY_TOUR_FINISHED = "tour_finished"

        fun of(context: Context): HomeGuideStore =
            HomeGuideStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
