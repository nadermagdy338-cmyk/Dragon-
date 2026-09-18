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

import com.topjohnwu.superuser.Shell

/**
 * غلاف رقيق على `Shell` — **أصل مشترك لا ملك ميزة**.
 *
 * وُجد لأن أكثر من ميزة تحتاج تنفيذ أمر وقراءة مخرجه بصدق، ولا يصحّ أن تعتمد ميزة
 * (`AppOps` والصلاحيات) على ميزة أخرى (`Max Backup`) لأجل دالّتين لا علاقة لهما بالنسخ.
 * فالاعتماد يبقى على طبقة واحدة معروفة، والفشل فيه **معلَن** لا مُخترَع.
 *
 * والقاعدتان ثابتتان في المستودع كله:
 *
 * 1. **الاقتباس إلزامي** لكل قيمة تأتي من خارجنا (اسم حزمة، اسم عملية، مسار). بلا اقتباس
 *    يصبح اسم حزمة فيه مسافة — أو أسوأ — أمرًا آخر يُنفَّذ بصلاحية جذر.
 * 2. **الفشل `null` لا قائمة فارغة.** «لم أستطع التنفيذ» و«نُفِّذ بلا مخرج» نتيجتان مختلفتان،
 *    وخلطهما هو أصل أكثر أعطاب الأدوات التي تكذب على مستخدمها.
 */
object PrivilegedShell {

    /** اقتباس مفرد آمن لـPOSIX shell: `'` تصبح `'\''`. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** مخرج الأمر مُشذَّبًا سطرًا سطرًا، أو `null` إن فشل التنفيذ. */
    fun run(vararg commands: String): List<String>? = runCatching {
        val result = Shell.cmd(*commands).exec()
        if (result.isSuccess) result.out.map(String::trim) else null
    }.getOrNull()
}
