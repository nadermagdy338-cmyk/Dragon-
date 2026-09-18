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

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * `AR-04` — **مصدر واحد لهوية الإصدار**.
 *
 * ما كان قبل هذا الملف (مُتحقَّق من الكود، لا من الذاكرة):
 *
 * 1. `AppVersionUtil.getAppVersion()` — يقرأ إصدار التطبيق لشاشة «حول» والشاشة الرئيسية.
 * 2. `MainActivity` — **يعيد حساب إصدار التطبيق بنفسه** (`getPackageInfo` مرّتين مع تفريع
 *    حسب الإصدار) لمقارنته بإصدار الوحدة.
 * 3. `RootUtil.getModuleVersionCode()` — يقرأ إصدار الوحدة بـ**أمر shell** (`grep`) على مسار
 *    مكتوب يدويًّا، بينما `ModuleHealthUtil` يقرأ **الملف نفسه** بمنفذ ملفات الجذر.
 *
 * أي أن رقمَي «إصدار التطبيق» و«إصدار الوحدة» كان **لكلٍّ منهما أكثر من قارئ**، وحكم المقارنة
 * كان مبثوثًا في الواجهة. هذا الملف يجعل القراءة واحدة والحكم واحدًا.
 *
 * **حدّ مُعلَن:** لا نعرف أيّ نسخة من الوحدة يجب أن تكون مركَّبة (المستودع يحمل `module.prop`
 * بمعرّفين مختلفين) — لذلك نعرض **ما هو مركَّب فعلًا** ونقارنه، ولا نصلح شيئًا ولا نفترض
 * «الصحيح». وكل حالة غير مقروءة تبقى `UNKNOWN` لا «مطابق».
 */
object VersionIdentity {

    /** إصدار التطبيق كما يعلنه نظام الحزم — أو `UNKNOWN` إن تعذّرت القراءة. */
    data class AppIdentity(
        val versionName: String,
        val versionCode: Long,
        val readable: Boolean,
    ) {
        /** العرض الموحَّد: `1.0 (1)` أو `UNKNOWN`. */
        val display: String get() = if (readable) "$versionName ($versionCode)" else UNKNOWN
    }

    /** ما تعلنه الوحدة المركَّبة على الجهاز، لا ما نتمنّاه. */
    data class ModuleIdentity(
        val installed: Boolean,
        val id: String?,
        val version: String?,
        val versionCode: Int,
    ) {
        val codeKnown: Boolean get() = installed && versionCode >= 0
        val display: String get() = if (codeKnown) versionCode.toString() else UNKNOWN
    }

    /** حكم المقارنة — ثلاث حالات، بلا حالة رابعة ضمنية. */
    enum class Agreement {
        /** الرقمان مقروءان ومتساويان. */
        MATCH,

        /** الرقمان مقروءان ومختلفان: الوحدة والتطبيق ليسا من الإصدار نفسه. */
        MISMATCH,

        /** لا حكم: الوحدة غير مركَّبة، أو رقم غير مقروء. */
        UNKNOWN,
    }

    data class Report(
        val app: AppIdentity,
        val module: ModuleIdentity,
        val agreement: Agreement,
    ) {
        /** فرق الإصدار إن عُرف: موجب ⇒ الوحدة أحدث، سالب ⇒ التطبيق أحدث. `null` = لا حكم. */
        val versionGap: Long?
            get() = if (agreement == Agreement.UNKNOWN) null
            else module.versionCode.toLong() - app.versionCode
    }

    const val UNKNOWN = "UNKNOWN"

    /**
     * الحكم على تطابق الإصدارين — **خالصة**، لذلك مُختبرة بلا جهاز.
     * لا تُصدر [Agreement.MATCH] إلا بدليل: رقم وحدة مقروء فعلًا.
     */
    fun compare(appVersionCode: Long, moduleInstalled: Boolean, moduleVersionCode: Int): Agreement {
        if (!moduleInstalled || moduleVersionCode < 0) return Agreement.UNKNOWN
        return if (appVersionCode == moduleVersionCode.toLong()) Agreement.MATCH else Agreement.MISMATCH
    }

    /** قراءة إصدار التطبيق من مصدر واحد (نظام الحزم) — بدل التكرار في كل شاشة. */
    fun readApp(context: Context): AppIdentity {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            AppIdentity(
                versionName = info.versionName ?: UNKNOWN,
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                readable = true,
            )
        } catch (_: Exception) {
            // نظام الحزم نفسه تعذّر ⇒ لا نخترع رقمًا. «غير معروف» ليست فشلًا في القراءة فقط،
            // بل **منع** لأي استنتاج لاحق مبني على صفر.
            AppIdentity(versionName = UNKNOWN, versionCode = -1L, readable = false)
        }
    }

    /** قراءة إصدار الوحدة من قارئ واحد (`ModuleHealthUtil`) — لا من أمر shell. */
    fun readModule(health: ModuleHealth): ModuleIdentity = ModuleIdentity(
        installed = health.installed,
        id = health.id,
        version = health.version,
        versionCode = health.versionCode,
    )

    /** التقرير الكامل: قارئ واحد لكل طرف، وحكم واحد. */
    fun read(context: Context, health: ModuleHealth): Report {
        val app = readApp(context)
        val module = readModule(health)
        val agreement = if (!app.readable) {
            Agreement.UNKNOWN
        } else {
            compare(app.versionCode, module.installed, module.versionCode)
        }
        return Report(app = app, module = module, agreement = agreement)
    }
}
