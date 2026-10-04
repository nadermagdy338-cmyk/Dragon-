/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * سجلّ استعمال الشاشات — **عدّاد واحد لكل وجهة في التطبيق**، محليًّا على الجهاز وحده.
 *
 * **ولماذا سجلّ واحد لا عدّادان:** مُوجِّد الشاشات يريد «أكثر ما تفتحه» ليعرضه قبل أن تكتب
 * حرفًا، ومنصة التحكم في الرئيسية تريد العدد نفسه لترتّب بطاقاتها. وعدّادان يقيسان الفعل نفسه
 * **يفترقان يومًا** (أحدهما يُسجّل الفتح من مكان دون آخر) فيعرض أحدهما ترتيبًا غير الذي يعرفه
 * المستخدم من الآخر. فواحد هنا، ومنه يقرأ الاثنان.
 *
 * **والوحدة هي المسار** ([nd.max.ui.navigation.MaxDestination.route]) لأنه هويّة الوجهة في
 * التطبيق كلّه (ADR-02) — لا مفتاح عرض يبتكره كل مستهلك. وما ليس في السجلّ لا يُعرض أصلًا.
 *
 * **وهو ليس قياسًا مُصنَّعًا:** ADR-07 يمنع **ادّعاء** حالة عتاد لم تُقرأ؛ وهذا عددٌ نحن من
 * سجّله عند فتح الشاشة (`MainActivity` ← مستمع الوجهة)، ويُقرأ من ملفّ التفضيلات نفسه
 * (`settings`)، ولا يخرج من الجهاز ولا يُقرأ من الشبكة.
 */
package nd.max.ui.util

import android.content.Context
import android.content.SharedPreferences

class ScreenUsageStore(private val prefs: SharedPreferences) {

    /**
     * يسجّل فتح شاشة. والوجهة مسارها ثابت (`device_info?section={section}` نمطًا لا قيمةً)،
     * فلا يتفرّع العدّاد إلى مفاتيح لا تُقرأ.
     */
    fun record(route: String?) {
        val key = route?.takeIf { it.isNotBlank() } ?: return
        val next = (prefs.getInt(usageKey(key), 0) + 1).coerceAtMost(CEILING)
        prefs.edit().putInt(usageKey(key), next).apply()
    }

    /** كل ما سُجّل: المسار ⇒ عدد الفتحات. والفراغ يعني: لا استعمال بعد، لا صفرًا مُصنَّعًا. */
    fun counts(): Map<String, Int> = prefs.all.entries
        .mapNotNull { (key, value) ->
            val route = key.takeIf { it.startsWith(USAGE_PREFIX) }?.removePrefix(USAGE_PREFIX)
            if (route.isNullOrEmpty() || value !is Int) null else route to value
        }
        .toMap()

    /** يعيد كل الأعداد إلى الصفر (ويُترك الوضع والاختيار اليدوي لمنصة التحكم كما هما). */
    fun clear() {
        val editor = prefs.edit()
        counts().keys.forEach { editor.remove(usageKey(it)) }
        editor.apply()
    }

    companion object {
        private const val PREFS = "settings"
        private const val USAGE_PREFIX = "screen_uses_"

        /** السقف يمنع فيضًا في العدّاد لا يغيّر الترتيب: مشبعًا يبقى «الأكثر استعمالًا». */
        private const val CEILING = 10_000

        private fun usageKey(route: String) = USAGE_PREFIX + route

        fun of(context: Context): ScreenUsageStore =
            ScreenUsageStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
