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
/*
 * «Boost» — إيقاف التطبيقات المخبأة، **وذاكرة حُرّرت مقيسة** لا مُقدَّرة.
 *
 * عقد `MAX-MANAGER-LEVEL-UP.md` §6.4: «لا يُسمّى Clean»، «يحرّر ذاكرة مقاسة (`MemAvailable`
 * قبل/بعد) عبر الآليات الموجودة (قتل خلفية آمن، لا قتل النظام)»، و§11 البند 5: «لا تقدير
 * كأنه قياس».
 *
 * وثلاثة قرارات تقيَّدت بالمقيس لا بالاختيار:
 *
 * 1. **المقياس من `ActivityManager`، لا من `/proc/meminfo`.** `MemoryInfo.availMem` هو
 *    `MemAvailable` نفسه بعد أن يحسبه إطار العمل (يطرح ما لا يُسترجح)، وهو **مقروء للتطبيق
 *    العادي** بلا جذر — بينما `/proc/meminfo` على أندرويد حديث قد يُحجب عن uid التطبيق.
 *    والمصدر نفسه هو الذي يغذّي بطاقة الذاكرة في الرئيسية، فلا رقمان لفكرة واحدة.
 *
 * 2. **الأمر `am kill-all` وحده.** معناه المعلن: إيقاف **عمليات الخلفية** — لا يقتل عملية
 *    في المقدّمة ولا خدمة نظام. وهو الأقرب إلى نصّ العقد («قتل خلفية آمن، لا قتل النظام»).
 *    و`drop_caches` **لا يُستخدم هنا**: هو إسقاط لمخابئ النواة لا قتل تطبيقات، ويُبطئ القراءة
 *    بعدها، والخطة تنصّ أن مكانه أدوات الجذر بتنبيه — لا زرٌّ يوميّ في الرئيسية.
 *
 * 3. **ولا يأخذ مسارًا ثانيًا:** التنفيذ عبر `PrivilegedShell`، وهو الغلاف الوحيد على `Shell`
 *    في المستودع كله (نفس ما يستخدمه مدير الملفات وAppOps). ولمّا كان `am` يحتاج uid الصدفة،
 *    فالتعزيز **يحتاج جذرًا** فعلًا — و`ShizukuGateway` يعلن في تعليقه أن تشغيل أوامر عبر
 *    شيزوكو (UserService) **غير منفَّذ بعد**، فلا نَدّعي قدرة لا نملكها. من لا جذر له يقرأ
 *    في البطاقة **ما ينقصه**؛ ولا يُعرض له زرّ يفشل صامتًا.
 *
 * **وحدّه المعلَن:** القياس فرق ذاكرة متاحة بين لحظتين، وليس نسبةً إلى «محرَّر» مُعلن من
 * النظام. فإن كان الفرق سالبًا (تطبيق عاد فحجز ذاكرة بين اللحظتين) فالناتج **لا رقم** —
 * ولا يُكتب «حرّرنا صفرًا».
 *
 * **ولذلك يُستقبل القارئ لا الـ`Context`:** القرار كله (قياس، تنفيذ، قياس، طرح) يعمل على
 * دالّة تُرجع `Int?`، فيُقاس في JVM بقراءات مُصنَّعة — والـ`Context` يبقى في طرف المتصل.
 */
package nd.max.ui.util

import android.app.ActivityManager
import android.content.Context

/** نتيجة محاولة تعزيز واحدة. */
data class BoostOutcome(
    /** نُفِّذ الأمر فعلًا؟ `false` يعني: لم نصل إلى الصدفة. */
    val executed: Boolean,
    /**
     * الذاكرة المحرَّرة فعليًّا بالميغابايت، أو `null` حين **[أ] لم يُنفَّذ** أو **[ب] كان الفرق
     * صفرًا أو سالبًا** — فالحالتان تقولان «لا رقم مقيس»، والفرق بينهما يحمله [executed].
     */
    val freedMb: Int?,
)

object MemoryBoostEngine {

    /**
     * `am kill-all` — إيقاف عمليات الخلفية. **ولا يُوسَّع إلى `am force-stop` أو `kill -9`:**
     * الأول يستهدف حزمة بعينها ويقطع عنها خدماتها، والثاني قد يصيب عملية نظام.
     */
    private const val KILL_ALL = "am kill-all"

    /** الذاكرة المتاحة الآن بالميغابايت، أو `null` إن لم تُقرأ. */
    fun availableMb(context: Context): Int? = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        (info.availMem / 1_048_576L).toInt()
    }.getOrNull()


    /**
     * يقيس، يوقف، يقيس — ويُنادى على مسار IO، فالأمر يحجب حتى ينتهي.
     *
     * @param readAvailableMb قراءة الذاكرة المتاحة. تُستدعى **مرّتين**: قبل الأمر وبعده.
     *        ويُمرَّر الدالّة لا قيمتين لأن المتصل قد لا يعرف لحظة النهاية أصلًا.
     */
    fun boost(readAvailableMb: () -> Int?): BoostOutcome {
        val before = readAvailableMb()
        val executed = PrivilegedShell.run(KILL_ALL) != null
        if (!executed) return BoostOutcome(executed = false, freedMb = null)
        val after = readAvailableMb()
        val freed = if (before != null && after != null) (after - before).takeIf { it > 0 } else null
        return BoostOutcome(executed = true, freedMb = freed)
    }
}
