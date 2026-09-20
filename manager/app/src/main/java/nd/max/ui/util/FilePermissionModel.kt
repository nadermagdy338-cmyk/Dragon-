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
 * `MT-FM` — **تغيير الصلاحيات والمالك**: الترجمة بين ما يراه المستخدم وما يُكتب للأمر.
 *
 * ولماذا نموذج: `chmod`/`chown` أمران مدمِّران — رقم ثماني خاطئ واحد يجعل ملفًا لا
 * يُقرأ، أو يجعل مجلدًا قابلًا للتنفيذ من أي مستخدم. والترجمة بين المفاتيح المرئية
 * والرقم المكتوب يجب أن تكون **مقيسة** لا ملتصقة بحقل نصّ في الواجهة.
 *
 * والبتّات الخاصة (`setuid`/`setgid`/`sticky`) تُشتقّ من الرقم لا من الحروف، لأن
 * `s`/`S` و`t`/`T` يتغيّران حسب بت التنفيذ — وهي قاعدة قائمة في هذا المستودع.
 */
package nd.max.ui.util

import java.util.Locale

enum class AccessBit { Read, Write, Execute }

enum class AccessScope { Owner, Group, Other }

/**
 * مجموعة صلاحيات **مقروءة آليًّا**: ثلاث فئات، وثلاث بتّات خاصة.
 *
 * وهي مستقلة عن [FilePermissions] (التي تحفظ ما أعلنه الجهاز نصًّا) عن قصد: تلك
 * **قراءة**، وهذه **نيّة تعديل** — ولا يختلطان.
 */
data class PermissionSet(
    val owner: Set<AccessBit> = emptySet(),
    val group: Set<AccessBit> = emptySet(),
    val other: Set<AccessBit> = emptySet(),
    val setUid: Boolean = false,
    val setGid: Boolean = false,
    val sticky: Boolean = false,
) {
    val hasSpecial: Boolean get() = setUid || setGid || sticky

    fun bits(scope: AccessScope): Set<AccessBit> = when (scope) {
        AccessScope.Owner -> owner
        AccessScope.Group -> group
        AccessScope.Other -> other
    }

    fun withBits(scope: AccessScope, bits: Set<AccessBit>): PermissionSet = when (scope) {
        AccessScope.Owner -> copy(owner = bits)
        AccessScope.Group -> copy(group = bits)
        AccessScope.Other -> copy(other = bits)
    }

    fun toggle(scope: AccessScope, bit: AccessBit): PermissionSet {
        val current = bits(scope)
        val next = if (bit in current) current - bit else current + bit
        return withBits(scope, next)
    }

    private fun digit(bits: Set<AccessBit>): Int =
        (if (AccessBit.Read in bits) 4 else 0) +
            (if (AccessBit.Write in bits) 2 else 0) +
            (if (AccessBit.Execute in bits) 1 else 0)

    /** الرقم الثماني بثلاث خانات — الشكل الشائع في `chmod`. */
    val octal3: String get() = "%d%d%d".format(digit(owner), digit(group), digit(other))

    /** الرقم الثماني بأربع خانات، والخانة الأولى للبتّات الخاصة. */
    val octal4: String
        get() {
            val special = (if (setUid) 4 else 0) + (if (setGid) 2 else 0) + (if (sticky) 1 else 0)
            return "$special$octal3"
        }

    /** الرقم المكتوب للأمر: أربع خانات إن وُجدت بتّة خاصة، وإلا ثلاث. */
    val octal: String get() = if (hasSpecial) octal4 else octal3

    /**
     * الصيغة الرمزية كما في `ls`: `rw-r--r--` أو `rwsr-xr-x`.
     *
     * والبتّة الخاصة تُكتب `s`/`S` (و`t`/`T` للـsticky): الحرف الصغير يعني أن بتّ
     * التنفيذ معه، والكبير يعني أن البتّة الخاصة وحدها — وهذا ما يجعل الترجمة مقروءة.
     */
    val symbolic: String
        get() = buildString {
            append(scopeSymbolic(owner, AccessScope.Owner))
            append(scopeSymbolic(group, AccessScope.Group))
            append(scopeSymbolic(other, AccessScope.Other))
        }

    private fun scopeSymbolic(bits: Set<AccessBit>, scope: AccessScope): String {
        val read = if (AccessBit.Read in bits) 'r' else '-'
        val write = if (AccessBit.Write in bits) 'w' else '-'
        val executes = AccessBit.Execute in bits
        val special = when (scope) {
            AccessScope.Owner -> setUid
            AccessScope.Group -> setGid
            AccessScope.Other -> sticky
        }
        val execute = when {
            !special -> if (executes) 'x' else '-'
            scope == AccessScope.Other -> if (executes) 't' else 'T'
            else -> if (executes) 's' else 'S'
        }
        return "$read$write$execute"
    }
}

object FilePermissionRules {

    private val OCTAL = Regex("^[0-7]{3,4}$")
    private val SYMBOLIC = Regex("^[-rwxsStT]{9}$")
    private val OWNER_NAME = Regex("^[A-Za-z0-9._-]+$")

    /** تحليل ما أعلنه الجهاز عن ملف إلى مجموعة قابلة للتعديل، أو `null` إن لم يُقرأ. */
    fun parse(permissions: FilePermissions?): PermissionSet? {
        if (permissions == null) return null
        return parseSymbolic(permissions.symbolic) ?: parseOctal(permissions.octal)
    }

    fun parseOctal(text: String): PermissionSet? {
        val trimmed = text.trim().lowercase(Locale.ROOT).removePrefix("0o")
        if (!OCTAL.matches(trimmed)) return null
        val digits = trimmed.padStart(4, '0')
        val special = digits[0].digitToInt()
        val scopes = digits.drop(1).map { it.digitToInt() }
        fun bits(value: Int): Set<AccessBit> = buildSet {
            if (value and 4 != 0) add(AccessBit.Read)
            if (value and 2 != 0) add(AccessBit.Write)
            if (value and 1 != 0) add(AccessBit.Execute)
        }
        return PermissionSet(
            owner = bits(scopes[0]),
            group = bits(scopes[1]),
            other = bits(scopes[2]),
            setUid = special and 4 != 0,
            setGid = special and 2 != 0,
            sticky = special and 1 != 0,
        )
    }

    /**
     * تحليل الصيغة الرمزية. ويُقبل الطول ٩ (كما في `ls -l` بلا حرف النوع) وحده:
     * قبول أطوال أخرى يعني تخمينًا، والتخمين في صلاحيات ملف تخمين مدمِّر.
     */
    fun parseSymbolic(text: String): PermissionSet? {
        val trimmed = text.trim()
        if (!SYMBOLIC.matches(trimmed)) return null
        fun bits(chunk: String): Set<AccessBit> = buildSet {
            if (chunk[0] == 'r') add(AccessBit.Read)
            if (chunk[1] == 'w') add(AccessBit.Write)
            if (chunk[2] == 'x' || chunk[2] == 's' || chunk[2] == 't') add(AccessBit.Execute)
        }
        val owner = trimmed.substring(0, 3)
        val group = trimmed.substring(3, 6)
        val other = trimmed.substring(6, 9)
        return PermissionSet(
            owner = bits(owner),
            group = bits(group),
            other = bits(other),
            setUid = owner[2] == 's' || owner[2] == 'S',
            setGid = group[2] == 's' || group[2] == 'S',
            sticky = other[2] == 't' || other[2] == 'T',
        )
    }

    /** هل النصّ رقم ثماني صالح يمكن إرساله إلى `chmod`؟ */
    fun isValidOctal(text: String): Boolean = OCTAL.matches(text.trim().removePrefix("0o").lowercase(Locale.ROOT))

    fun isValidOwnerName(name: String): Boolean = OWNER_NAME.matches(name.trim())

    /**
     * `مالك:مجموعة` كما يتوقّعه `chown`.
     *
     * و`null` تعني «لا تُرسل الأمر»: المالك الفارغ أو اسم فيه محرف غريب (مسافة أو
     * `:` ثانٍ) يفسد الأمر أو يغيّر معناه، والرفض أصدق من إرسال نصّ مُصلَّح بالتخمين.
     */
    fun ownerSpec(owner: String, group: String): String? {
        val user = owner.trim()
        val target = group.trim()
        if (!isValidOwnerName(user)) return null
        if (target.isEmpty()) return user
        if (!isValidOwnerName(target)) return null
        return "$user:$target"
    }
}
