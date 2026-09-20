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
 * `MT-FM/ب` — تنفيذ تغيير الصلاحيات والمالك، **بتحقّق بعد الكتابة**.
 *
 * ولماذا التحقّق: `chmod` على نظام ملفات مقروء فقط يخرج بلا خطأ عند بعض الأصداف، وعلى
 * نظام بلا دعم `chown` (FUSE مثلًا) يخرج بخطأ يبتلعه من لا يقرأه. فالقاعدة المتبعة في
 * الـarbiter تُطبَّق هنا: **لا «نجح» بلا قياس** — تُقرأ الصلاحيات بعد الأمر وتُقارن.
 *
 * وبناء الأوامر مفصول عن تنفيذها: النصّ يُختبر في JVM (اقتباس صحيح، ورفض رقم غير صالح
 * **قبل** أن يصل إلى shell)، والتنفيذ رقيق بقدر ما يمرّ بـ[PrivilegedShell].
 */
package nd.max.ui.util

/** حكم تغيير الصلاحيات: ثلاثة أحوال لا اثنان. */
enum class PermissionApply { Applied, NotApplied, Unverified }

/** ما أعاده `stat` عن المدخل: الرقم الثماني والمالك والمجموعة. */
data class PermissionStat(val octal: String, val owner: String, val group: String)

object FilePermissionOps {

    /** التنسيق المُعلن: الرقم ثم المالك ثم المجموعة — تُقرأ في نداء واحد بعد التعديل. */
    const val STAT_FORMAT: String = "%a\\t%U\\t%G"

    /**
     * أمر `chmod` أو `null` إن كان الرقم غير صالح.
     *
     * و`null` **ليست فشلًا هنا بل رفضًا قبل التنفيذ**: رقم فيه `8` أو حروف كان سيصل إلى
     * shell فيفسّره شيء آخر، والرفض أصدق من «تصليح» رقم كتبه المستخدم.
     */
    fun chmodCommand(rawPath: String, octal: String): String? {
        // الرقم يُمرَّر بـ[FilePermissionRules.parseOctal] أولًا: بها يُطبَّع الشكل
        // (`0o644` و`0644` يصيران `644`) وبها يُرفض غير الصالح — تطبيع واحد لا تطبيعان.
        val requested = FilePermissionRules.parseOctal(octal) ?: return null
        val path = PrivilegedShell.quote(FileBrowser.normalize(rawPath))
        return "chmod ${requested.octal} $path"
    }

    /** أمر `chown`، أو `null` إن كان المالك/المجموعة غير صالحين. */
    fun chownCommand(rawPath: String, owner: String, group: String): String? {
        val spec = FilePermissionRules.ownerSpec(owner, group) ?: return null
        val path = PrivilegedShell.quote(FileBrowser.normalize(rawPath))
        return "chown ${PrivilegedShell.quote(spec)} $path"
    }

    fun statCommand(rawPath: String): String =
        "stat -c ${PrivilegedShell.quote(STAT_FORMAT)} ${PrivilegedShell.quote(FileBrowser.normalize(rawPath))}"

    fun parseStat(line: String?): PermissionStat? {
        val parts = line?.trim()?.split('\t') ?: return null
        if (parts.size < 3) return null
        val octal = parts[0].trim()
        if (octal.isEmpty()) return null
        return PermissionStat(octal = octal, owner = parts[1].trim(), group = parts[2].trim())
    }

    /**
     * الحكم: هل وقع الأثر فعلًا؟
     *
     * والقارئ `null` تعني «لم نستطع القراءة» ⇒ `Unverified` لا `NotApplied`: الفرق بين
     * «لم يقع» و«لم نتحقّق» هو الفرق بين تقرير صادق وتقرير مُخترع.
     */
    fun verdict(requested: PermissionSet, reread: PermissionStat?): PermissionApply {
        if (reread == null) return PermissionApply.Unverified
        val observed = FilePermissionRules.parseOctal(reread.octal) ?: return PermissionApply.Unverified
        return if (observed == requested) PermissionApply.Applied else PermissionApply.NotApplied
    }

    /** ترجمة الحكم إلى نتيجة المشروع الثلاثية. */
    fun outcome(executed: Boolean, apply: PermissionApply): FileOpOutcome = when {
        !executed -> FileOpOutcome(false, false)
        apply == PermissionApply.Applied -> FileOpOutcome(true, true)
        else -> FileOpOutcome(true, false)
    }

    /**
     * تطبيق صلاحيات على مسارات، والتحقّق **لكل مسار على حدة**: نجاح الأول لا يعني أن
     * الثالث وقع، ولذلك يُقرأ كل مسار بعد تعديله.
     */
    fun applyChmod(paths: List<String>, octal: String): FileOpOutcome {
        if (paths.isEmpty()) return FileOpOutcome(false, false)
        val requested = FilePermissionRules.parseOctal(octal) ?: return FileOpOutcome(false, false)
        var executedAny = false
        var verifiedAll = true
        for (path in paths) {
            val command = chmodCommand(path, octal) ?: return FileOpOutcome(executedAny, false)
            val executed = PrivilegedShell.run(command) != null
            executedAny = executedAny || executed
            val observed = parseStat(PrivilegedShell.run(statCommand(path))?.firstOrNull())
            val apply = verdict(requested, observed)
            if (apply != PermissionApply.Applied) verifiedAll = false
        }
        return outcome(executedAny, if (verifiedAll) PermissionApply.Applied else PermissionApply.NotApplied)
    }

    /** تطبيق المالك/المجموعة، والتحقّق بمقارنة ما أعاده `stat` بعد الأمر. */
    fun applyChown(paths: List<String>, owner: String, group: String): FileOpOutcome {
        if (paths.isEmpty()) return FileOpOutcome(false, false)
        val spec = FilePermissionRules.ownerSpec(owner, group) ?: return FileOpOutcome(false, false)
        val expectedOwner = spec.substringBefore(':')
        val expectedGroup = spec.substringAfter(':', expectedOwner)
        var executedAny = false
        var verifiedAll = true
        for (path in paths) {
            val command = chownCommand(path, owner, group) ?: return FileOpOutcome(executedAny, false)
            val executed = PrivilegedShell.run(command) != null
            executedAny = executedAny || executed
            val observed = parseStat(PrivilegedShell.run(statCommand(path))?.firstOrNull())
            val matches = observed != null && observed.owner == expectedOwner && observed.group == expectedGroup
            if (!matches) verifiedAll = false
        }
        return outcome(executedAny, if (verifiedAll) PermissionApply.Applied else PermissionApply.NotApplied)
    }
}
