/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

/**
 * القناة الوحيدة إلى [RootNodeService].
 *
 * **عقد الاستعمال:** [service] قد يكون `null` في أي لحظة، وهذا **ليس عطبًا**: الربط غير
 * متزامن، ويبدأ من `MainActivity` وينتهي بعدها. فكل مستدعٍ يعتبر `null` = «لا قناة الآن»
 * ويسلك بالطريق الاحتياطي الذي يعرفه (`RootFileAccess` يمرّ إلى الملف ثم الصدفة) — وهذا
 * الترتيب هو ما كان يعمل قبل وجود الخدمة، فيبقى شبكة أمان لا مسارًا ميتًا.
 *
 * ولا تنتظار هنا: لا `CountDownLatch` ولا مهلة. الانتظار في مسار واجهة يعني تجميد إطار
 * لعملية قد لا تُربط أبدًا (جهاز بلا جذر، أو صلاحية مرفوضة) — والبديل الاحتياطي أسرع.
 */
object RootNodeChannel {

    private const val TAG = "MaxRootNode"

    /**
     * الخدمة المربوطة أو `null`، تُقرأ من خيوط متعددة (مسح الحرارة يعمل خارج الخيط الرئيسي).
     * `@Volatile` لأن الكتابة تحدث في خيط الربط والقراءة في خيوط العمل.
     */
    @Volatile
    var service: IRootNodeService? = null
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IRootNodeService.Stub.asInterface(binder)
            log("connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            log("disconnected")
        }
    }

    /** يبدأ الربط مرة واحدة؛ النداء المتكرّر أثناء الربط لا يفتح طلبًا ثانيًا. */
    fun connect(context: Context) {
        if (service != null) return
        val intent = Intent(context.applicationContext, RootNodeService::class.java)
        runCatching { RootService.bind(intent, connection) }.onFailure { log("bind failed: ${it.javaClass.simpleName}") }
    }

    /** ينهي الربط ويُسقط اليد. النداء بلا ربط قائم لا يفعل شيئًا. */
    fun disconnect() {
        if (service == null) return
        runCatching { RootService.unbind(connection) }
        service = null
    }

    private fun log(message: String) {
        android.util.Log.d(TAG, message)
    }
}
