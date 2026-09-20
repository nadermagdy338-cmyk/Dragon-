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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * أوامر الصلاحيات والحكم عليها — يُقاس **بناء الأمر** في JVM قبل أن يُنفَّذ أي شيء.
 *
 * وسببان يجعلان هذا الموضع حساسًا: (١) مسار فيه علامة تنصيص واحدة يكسر الاقتباس فيتحوّل
 * الأمر إلى أوامر أخرى بصلاحية جذر؛ (٢) رقم ثماني غير صالح يُفسَّر خيارًا آخر. وكلاهما
 * يقع **قبل** أي تراجع، فلا يُترك للاختبار على جهاز.
 */
class FilePermissionOpsTest {

    @Test
    fun theChmodCommandIsBuiltWithTheCanonicalOctalAndAQuotedPath() {
        assertEquals("chmod 755 '/system/bin/toybox'", FilePermissionOps.chmodCommand("/system/bin/toybox", "755"))
        // `0o` و`0644` كلاهما يُقبل ويُكتب بالصيغة التي يفهمها الأمر.
        assertEquals("chmod 644 '/data/x'", FilePermissionOps.chmodCommand("/data/x", "0o644"))
        assertEquals("chmod 644 '/data/x'", FilePermissionOps.chmodCommand("/data/x/", "0644"))
    }

    @Test
    fun anInvalidOctalProducesNoCommandAtAll() {
        assertNull(FilePermissionOps.chmodCommand("/data/x", "999"))
        assertNull(FilePermissionOps.chmodCommand("/data/x", "rw-"))
        assertNull(FilePermissionOps.chmodCommand("/data/x", ""))
        assertNull(FilePermissionOps.chmodCommand("/data/x", "12345"))
    }

    /** علامة تنصيص داخل الاسم لا تكسر الاقتباس — وهذا هو الاختبار الذي يحمي الجذر. */
    @Test
    fun aQuoteInsideThePathCannotBreakOutOfTheQuoting() {
        val command = FilePermissionOps.chmodCommand("/data/od'd", "644")!!
        assertEquals("chmod 644 '/data/od'\\''d'", command)
        assertTrue(command.endsWith("'"))
    }

    @Test
    fun theChownCommandUsesTheOwnerSpecOrRefuses() {
        assertEquals("chown 'root:root' '/data/x'", FilePermissionOps.chownCommand("/data/x", "root", "root"))
        assertEquals("chown 'system' '/data/x'", FilePermissionOps.chownCommand("/data/x", "system", ""))
        assertNull(FilePermissionOps.chownCommand("/data/x", "", "root"))
        assertNull(FilePermissionOps.chownCommand("/data/x", "root", "sys tem"))
        assertNull(FilePermissionOps.chownCommand("/data/x", "root:root", "x"))
    }

    @Test
    fun theStatCommandReadsTheThreeDeclaredFields() {
        assertEquals("stat -c '%a\\t%U\\t%G' '/system'", FilePermissionOps.statCommand("/system"))
    }

    @Test
    fun theStatLineIsParsedOrDeclaredUnreadable() {
        assertEquals(
            PermissionStat(octal = "644", owner = "root", group = "system"),
            FilePermissionOps.parseStat("644\troot\tsystem"),
        )
        assertNull(FilePermissionOps.parseStat("755 root system"))
        assertNull(FilePermissionOps.parseStat(""))
        assertNull(FilePermissionOps.parseStat(null))
    }

    // ────────────────────────────────────────────────────────────────────────
    // الحكم: أثبت أم لا، وهذا هو بيت القصيد
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun aMatchingRereadIsApplied() {
        val requested = FilePermissionRules.parseOctal("644")!!
        assertEquals(
            PermissionApply.Applied,
            FilePermissionOps.verdict(requested, PermissionStat("644", "root", "root")),
        )
    }

    /** الأمر نُفِّذ لكن القرص رفض التغيير (نظام قراءة فقط): يُقال ذلك ولا يُدّعى النجاح. */
    @Test
    fun aDifferentRereadIsNotApplied() {
        val requested = FilePermissionRules.parseOctal("644")!!
        assertEquals(
            PermissionApply.NotApplied,
            FilePermissionOps.verdict(requested, PermissionStat("600", "root", "root")),
        )
    }

    /** ما لم نستطع قراءته ليس «لم يقع»: الفرق بين الحالتين هو كل الصدق في التقرير. */
    @Test
    fun anUnreadableRereadIsUnverifiedNotFailed() {
        val requested = FilePermissionRules.parseOctal("644")!!
        assertEquals(PermissionApply.Unverified, FilePermissionOps.verdict(requested, null))
        assertEquals(
            PermissionApply.Unverified,
            FilePermissionOps.verdict(requested, PermissionStat("----", "?", "?")),
        )
    }

    @Test
    fun theVerdictMapsOntoTheThreeProjectOutcomes() {
        assertEquals(FileOpOutcome(false, false), FilePermissionOps.outcome(false, PermissionApply.Applied))
        assertEquals(FileOpOutcome(true, true), FilePermissionOps.outcome(true, PermissionApply.Applied))
        assertEquals(FileOpOutcome(true, false), FilePermissionOps.outcome(true, PermissionApply.NotApplied))
        assertEquals(FileOpOutcome(true, false), FilePermissionOps.outcome(true, PermissionApply.Unverified))
    }
}
