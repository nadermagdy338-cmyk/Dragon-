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

package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `PEER-8` / `AR-31` — حكم الكتابة.
 *
 * تحرس هذه الاختبارات الفروق التي يخلطها عادةً «نجح/فشل» الثنائي:
 * **لم تُكتب** ≠ **كُتبت لكن الجهاز زحزحها** ≠ **تعذّرت القراءة**.
 */
class WriteVerificationTest {

    private fun outcome(wrote: String, readBack: String?) =
        WriteVerification.compare(wrote, readBack)

    @Test
    fun `an identical read back matches`() {
        assertEquals(
            WriteVerification.Outcome.MATCHED,
            outcome("500000", "500000"),
        )
    }

    @Test
    fun `trailing newline and padding are not a difference`() {
        // ‏sysfs تعيد القيمة بسطر جديد — وهذا ليس فشل كتابة.
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("1", "1\n"))
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("1", "  1  "))
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("interactive", "interactive\n"))
    }

    @Test
    fun `the same number written differently matches`() {
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("500000", "0500000"))
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("500000", "+500000"))
    }

    @Test
    fun `a clamped value differs and is reported as such`() {
        // الجهاز قد يقود قيمة إلى حدّه — هذه حقيقة عتاد لا فشل أمر.
        assertEquals(WriteVerification.Outcome.DIFFERS, outcome("500000", "300000"))
        assertEquals(WriteVerification.Outcome.DIFFERS, outcome("schedutil", "interactive"))
    }

    @Test
    fun `unreadable is never reported as a match or a mismatch`() {
        assertEquals(WriteVerification.Outcome.READBACK_UNAVAILABLE, outcome("1", null))
    }

    @Test
    fun `an empty read back is a value difference not an absence of evidence`() {
        // قرأنا فوجدنا فراغًا: هذه **دلالة** على أن شيئًا ما لم يُكتَب كما أردنا.
        assertEquals(WriteVerification.Outcome.DIFFERS, outcome("1", ""))
    }

    @Test
    fun `non numeric text is compared literally`() {
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("performance", "performance"))
        assertEquals(WriteVerification.Outcome.DIFFERS, outcome("performance", "powersave"))
        // ‏"1" مقابل نصّ ليس رقمًا ⇒ مقارنة نصّية لا انهيار.
        assertEquals(WriteVerification.Outcome.DIFFERS, outcome("1", "one"))
    }

    @Test
    fun `internal whitespace is collapsed before comparing`() {
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("a  b", "a b"))
        assertEquals(WriteVerification.Outcome.MATCHED, outcome("a\tb", "a b"))
    }

    @Test
    fun `verdict words are stable log identifiers`() {
        assertEquals("matched", WriteVerification.verdictWord(WriteVerification.Outcome.MATCHED))
        assertEquals("differs", WriteVerification.verdictWord(WriteVerification.Outcome.DIFFERS))
        assertEquals("write_failed", WriteVerification.verdictWord(WriteVerification.Outcome.WRITE_FAILED))
        assertEquals("unreadable", WriteVerification.verdictWord(WriteVerification.Outcome.READBACK_UNAVAILABLE))
    }

    @Test
    fun `describe shows both sides so a mismatch is auditable`() {
        assertEquals(
            "500000 -> 300000 (differs)",
            WriteVerification.describe("500000", "300000", WriteVerification.Outcome.DIFFERS),
        )
        assertEquals(
            "1 -> ? (unreadable)",
            WriteVerification.describe("1", null, WriteVerification.Outcome.READBACK_UNAVAILABLE),
        )
    }
}
