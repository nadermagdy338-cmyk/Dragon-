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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الصلاحيات — تُقاس لأن رقمًا ثمانيًّا خاطئًا واحدًا يجعل ملفًا لا يُقرأ أو مجلدًا
 * قابلًا للتنفيذ من أي مستخدم. والترجمة بين المفاتيح المرئية والرقم المكتوب هي
 * الموضع الذي يمكن أن يقع فيه الخطأ بصمت.
 */
class FilePermissionModelTest {

    @Test
    fun aThreeDigitOctalBecomesTheReadableSymbolicForm() {
        val set = FilePermissionRules.parseOctal("644")!!
        assertEquals(setOf(AccessBit.Read, AccessBit.Write), set.owner)
        assertEquals(setOf(AccessBit.Read), set.group)
        assertEquals("rw-r--r--", set.symbolic)
        assertEquals("644", set.octal)
        assertFalse(set.hasSpecial)
    }

    @Test
    fun aFourDigitOctalCarriesTheSpecialBits() {
        val set = FilePermissionRules.parseOctal("4755")!!
        assertTrue(set.setUid)
        assertEquals("rwsr-xr-x", set.symbolic)
        assertEquals("4755", set.octal)
        assertEquals("755", set.octal3)
    }

    /** `sticky` على `/tmp`: آخر بتّة تكتب `t` لا `x`. */
    @Test
    fun theStickyBitIsWrittenAsAT() {
        val set = FilePermissionRules.parseOctal("1777")!!
        assertTrue(set.sticky)
        assertEquals("rwxrwxrwt", set.symbolic)
    }

    /** بتّة خاصة بلا تنفيذ: الحرف كبير (`S`) — وهذا الفرق الذي يمنع قراءة خاطئة. */
    @Test
    fun aSpecialBitWithoutExecuteIsCapital() {
        assertEquals("rw-r-Sr--", FilePermissionRules.parseOctal("2644")!!.symbolic)
    }

    @Test
    fun malformedOctalIsRefused() {
        assertNull(FilePermissionRules.parseOctal("888"))
        assertNull(FilePermissionRules.parseOctal("9"))
        assertNull(FilePermissionRules.parseOctal("rw-"))
        assertNull(FilePermissionRules.parseOctal("12345"))
    }

    @Test
    fun theZeroPrefixIsAcceptedButNotStored() {
        assertEquals("644", FilePermissionRules.parseOctal("0644")!!.octal)
        assertEquals("644", FilePermissionRules.parseOctal("0o644")!!.octal)
    }

    @Test
    fun symbolicParsingAcceptsNinePlacesOnly() {
        val set = FilePermissionRules.parseSymbolic("rwxrwxrwx")!!
        assertEquals("777", set.octal)
        // عشرة مواضع تعني أن حرف النوع تسرّب: التخمين هنا ممنوع.
        assertNull(FilePermissionRules.parseSymbolic("-rw-r--r--"))
        assertNull(FilePermissionRules.parseSymbolic("rw-r--r"))
        assertNull(FilePermissionRules.parseSymbolic("Rw-r--r--"))
    }

    @Test
    fun symbolicParsingReadsTheSpecialLetters() {
        val set = FilePermissionRules.parseSymbolic("rwsr-xr-x")!!
        assertTrue(set.setUid)
        assertEquals("4755", set.octal)
        assertEquals("rwxrwxrwt", FilePermissionRules.parseSymbolic("rwxrwxrwt")!!.symbolic)
    }

    /** ما أعلنه الجهاز عن ملف يُترجم إلى مجموعة قابلة للتعديل — والرمز أولًا. */
    @Test
    fun theDeclaredPermissionsBecomeEditable() {
        val set = FilePermissionRules.parse(FilePermissions(octal = "644", symbolic = "rw-r--r--"))!!
        assertEquals("rw-r--r--", set.symbolic)
        // وما لم يُقرأ يبقى غير مقروء.
        assertNull(FilePermissionRules.parse(null))
    }

    @Test
    fun togglingABitFlipsOnlyThatBit() {
        val set = PermissionSet()
            .toggle(AccessScope.Owner, AccessBit.Read)
            .toggle(AccessScope.Owner, AccessBit.Write)
        assertEquals("600", set.octal)
        assertEquals("rw-------", set.symbolic)
        assertEquals("400", set.toggle(AccessScope.Owner, AccessBit.Write).octal)
    }

    @Test
    fun theOctalValidatorAcceptsOnlyWhatChmodAccepts() {
        assertTrue(FilePermissionRules.isValidOctal("777"))
        assertTrue(FilePermissionRules.isValidOctal("0755"))
        assertFalse(FilePermissionRules.isValidOctal("77"))
        assertFalse(FilePermissionRules.isValidOctal("8"))
        assertFalse(FilePermissionRules.isValidOctal("abc"))
    }

    // ────────────────────────────────────────────────────────────────────────
    // المالك والمجموعة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun theOwnerSpecIsBuiltOnlyFromValidNames() {
        assertEquals("root:root", FilePermissionRules.ownerSpec("root", "root"))
        assertEquals("system", FilePermissionRules.ownerSpec(" system ", ""))
        // اسم فارغ أو فيه مسافة أو نقطتان: الأمر يُفسد أو يتغيّر معناه ⇒ رفض لا تصليح.
        assertNull(FilePermissionRules.ownerSpec("", "root"))
        assertNull(FilePermissionRules.ownerSpec("root", "system group"))
        assertNull(FilePermissionRules.ownerSpec("root:root", "x"))
        assertNull(FilePermissionRules.ownerSpec("root", "a:b"))
        assertTrue(FilePermissionRules.isValidOwnerName("system_ext"))
        assertFalse(FilePermissionRules.isValidOwnerName("system ext"))
    }
}
