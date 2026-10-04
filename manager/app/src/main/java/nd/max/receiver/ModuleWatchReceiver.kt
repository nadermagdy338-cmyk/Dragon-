/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

package nd.max.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nd.max.core.daemon.ModuleWatchman

/**
 * **باب الشفاء بعد الإقلاع.**
 *
 * **والعطب الذي وُلد له (مقيس من جهاز حقيقي، ٢٠٢٦-١٠-٠١):** مات الرفيق في الإقلاع قبل فتح تخزين
 * المستخدم (`17:36:33.595`)، وظلّ الخادم ينتظر قفله ١٢٠ ثانية ثم **أغلق الوحدة كلها**
 * (`17:38:35.291`)، **ولم يُعِد تشغيلها شيء حتى الإقلاع التالي** — لأنّ [`ModuleWatchman`] الوحيد
 * الذي يعرف كيف يُصلحها ([`nd.max.core.daemon.DaemonSupervisor`]) كان **داخل العملية التي ماتت**.
 * فالعطب الذي مدّته ثانية واحدة أخذ معه إقلاعًا كاملًا من المراقبة.
 *
 * **وولاية الاستقبال مقيَّدة بقصد:** `USER_UNLOCKED` و`BOOT_COMPLETED` و`MY_PACKAGE_REPLACED` —
 * **ولا `LOCKED_BOOT_COMPLETED`**: هذا الأخير يُسلَّم قبل فتح تخزين المستخدم، فقيام عملية التطبيق
 * عنده (وهي تقرأ إعداداتها من تخزينها المحميّ باعتماد المستخدم) بابُ عطبٍ جديد لا باب شفاء —
 * والحاجة ليست إليه: الشفاء المطلوب **بعد** الفتح، وهو بالضبط وقت `USER_UNLOCKED`.
 *
 * **والحدود الثلاثة للتنفيذ:**
 *
 * 1. **لا عمل على الخيط الرئيسيّ:** النداء يستدعي صدفة ويقيس ويُشغّل — وهي عمل ثقيل، وحظر الخيط
 *    الرئيسيّ في بثّ نظام يُنتج «التطبيق لا يستجيب».
 * 2. **`goAsync()` مع إغلاق مضمون:** بدونه يعود النظام من البثّ ويقتل العملية قبل أن يُنفَّذ الفعل.
 * 3. **بلا إعلان نجاح:** كل نتيجة تُكتب في مصدرها (`ModuleWatchman` يكتب سطور `EVENT=WATCHMAN_*`)،
 *    والاستقبال لا يدّعي شيئًا.
 */
class ModuleWatchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in WATCHED_ACTIONS) return
        // الوحدة تُدار للمستخدم الأوّل في هذه الشجرة (راجع `service.sh`: `--user 0`)، فلا يُشغَّل
        // مسار ثانٍ لمستخدم آخر فيكتب في الملفّ نفسه من موقعين.
        if (!isPrimaryUser()) return

        val pending = goAsync()
        Thread {
            try {
                ModuleWatchman().watch()
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * والمستخدم الأوّل يُقاس بمقارنة **مُقدَّي العمليّة نفسهما** — لا بثابت ولا بدالّة داخليّة:
     * سقط ثلاثة قبله، وكلها من `android.os.UserHandle` **غير المُعلَنة في الـSDK**:
     * `Unresolved reference 'SYSTEM'`، ثمّ `'getUserId'`، ثمّ `Process.getUserHandleForUid`
     * (وهو في `UserHandle` لا في `Process` — قِيس بـ`javap` على `android.jar` لا بالتخمين).
     * والمُعلَن هنا اثنان: `Process.myUserHandle()` و`UserHandle.getUserHandleForUid(int)` (API ٢٤).
     */
    private fun isPrimaryUser(): Boolean = runCatching {
        android.os.Process.myUserHandle() == android.os.UserHandle.getUserHandleForUid(0)
    }.getOrDefault(true)

    private companion object {
        /**
         * والأحداث ثلاثة لا أكثر: قيام النظام، وفتح المستخدم لتخزينه، وتحديث الحزمة.
         * و«فتح التطبيق» ليس منها — لأنّه يُغطّى في [`nd.max.MaxManagerApplication`] عند كل قيام
         * عملية، ولا معنى لبثّ لا يملكه النظام.
         */
        val WATCHED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
