/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import android.annotation.SuppressLint
import com.topjohnwu.superuser.Shell
import nd.max.core.jni.PropBridge

@SuppressLint("PrivateApi")
object PropertyUtils {

    private val systemPropertiesClass by lazy {
        Class.forName("android.os.SystemProperties")
    }

    private val getMethod by lazy {
        systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
    }

    private val setMethod by lazy {
        systemPropertiesClass.getMethod("set", String::class.java, String::class.java)
    }

    /**
     * قراءة خصيصة — **الأصلي أولًا** ثم الانعكاس.
     *
     * ولماذا الأصلي: انعكاس `android.os.SystemProperties` سطح مخفي غير مضمون عبر
     * الإصدارات، وحين يُحجب يرجع `def` **صامتًا** — أي قيمة تبدو مقروءة وهي ليست كذلك
     * (ولذلك كانت مسارات أخرى تهرب إلى `getprop` عبر صدفة: ٢٣٦٦ ميكرو لكل سؤال).
     * و`__system_property_get` في bionic سطح ثابت، ويُقرأ داخل العملية (**١٢٫٦ ميكرو**).
     *
     * والدلالة محفوظة كما كانت: خصيصة غير مضبوطة تعني `def`، لا فرقَ بين المسارين.
     * ويستفيد منها [setAndConfirm] أيضًا: تحقّقها بعد الكتابة صار يقرأ من bionic مباشرة.
     */
    fun get(key: String, def: String = ""): String {
        PropBridge.get(key)?.let { return it.ifEmpty { def } }
        return try {
            getMethod.invoke(null, key, def) as String
        } catch (e: Exception) {
            def
        }
    }

    fun set(key: String, value: String) {
        try {
            setMethod.invoke(null, key, value)
        } catch (e: Exception) {
            val safeValue = value.replace("'", "'\\''")
            Shell.cmd("setprop $key '$safeValue'").submit()
        }
    }

    /**
     * كتابة **متزامنة مُتحقَّقة**: تُعيد `true` فقط إذا صارت الخاصية بالقيمة المطلوبة فعلًا.
     *
     * ولماذا لم يكفِ [set]: مساره الاحتياطي `Shell.cmd("setprop …").submit()` **لا ينتظر**،
     * والانعكاس على `SystemProperties.set` يفشل كتطبيق عادي. فمن كتب ثم قرأ فورًا — وهو ما
     * يفعله محرك Max AI بعد تبديل المفتاح الرئيسي — يقرأ القيمة القديمة، فتُرجع الدورة التالية
     * الزر إلى وضعه ويظهر التأخير للمستخدم (عطب «الدقيقة» المُبلَّغ عنه). وهذه الدالة تنتظر
     * وتُعيد الحقيقة.
     *
     * **حدّها:** حاجبة (تحتوي تنفيذ صدفة وقراءة)، فلا تُستدعى من خيط الواجهة؛ مستدعوها على
     * `Dispatchers.IO` (كما يفعل المحرك)، ومن كان على الواجهة فليستعمل [set] غير الحاجبة.
     */
    fun setAndConfirm(key: String, value: String, attempts: Int = 2): Boolean {
        repeat(attempts.coerceAtLeast(1)) {
            set(key, value)
            if (get(key, "") == value) return true
            // والعودة الصريحة إلى `exec()` لا `submit()`: هذه هي نقطة العطب نفسها.
            val safeValue = value.replace("'", "'\\''")
            runCatching { Shell.cmd("setprop $key '$safeValue'").exec() }
            if (get(key, "") == value) return true
        }
        return false
    }
}

