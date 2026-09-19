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

/**
 * قارئ مجلد الإضافات — الطبقة الوحيدة التي تلمس القرص، والقرار كله في [PluginContract].
 *
 * والفرق الذي لا يُدمج: **مجلد غائب** ليس **مجلدًا لا يُقرأ**. الأول حالة طبيعية تُعرض
 * «لا إضافات مركّبة»، والثاني عطب (لا جذر، أو مجلد بصلاحيات مانعة) ويجب أن يُقال.
 * وخلطهما يجعل مستخدمًا بلا جذر يظنّ أنه لا توجد إضافات، ثم يبحث في المكان الخطأ.
 */
package nd.max.ui.util

/** نتيجة الجرد: الحكم + حقيقة الوصول إلى المجلد. */
data class PluginScan(
    val state: PluginRegistryState = PluginRegistryState(),
    val directoryPresent: Boolean = false,
    val directoryReadable: Boolean = false,
    val unreadableManifests: List<String> = emptyList(),
)

object PluginDirectory {

    fun load(): PluginScan {
        val ids = PrivilegedShell.run("ls -1 ${PrivilegedShell.quote(PluginContract.DIRECTORY)}")
        if (ids == null) {
            // لا نعرف: موجود ولم نُقرأ، أم غير موجود أصلًا؟ نسأل سؤالًا ثانيًا بدل التخمين.
            val present = PrivilegedShell.run("test -d ${PrivilegedShell.quote(PluginContract.DIRECTORY)}") != null
            return PluginScan(directoryPresent = present, directoryReadable = false)
        }

        val evaluations = mutableListOf<PluginEvaluation>()
        val unreadable = mutableListOf<String>()
        for (raw in ids) {
            val id = raw.trim()
            if (id.isEmpty() || id == "." || id == "..") continue
            val lines = PrivilegedShell.run("cat ${PrivilegedShell.quote(PluginContract.manifestPath(id))}")
            if (lines == null) {
                unreadable += id
                continue
            }
            evaluations += PluginContract.parse(lines, id).first
        }

        return PluginScan(
            state = PluginContract.evaluate(evaluations),
            directoryPresent = true,
            directoryReadable = true,
            unreadableManifests = unreadable,
        )
    }
}
