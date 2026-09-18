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
 * `AR-24` — تحليل PSS ومقارنة اللقطات.
 *
 * ما تحرسه: **لا رقم بلا مطابقة صارمة**، و**لا مقارنة بين طريقتي قياس مختلفتين**، و**لا «تغيّر»
 * بفرق لا يستحقّ**.
 */
class MemoryLedgerTest {

    private fun snap(kb: Long, method: MemoryLedger.Method, at: Long = 0L, key: String = "nd.max") =
        MemoryLedger.MemorySnapshot(key = key, totalPssKb = kb, atMs = at, method = method)

    // ---- التحليل -------------------------------------------------------------------

    @Test
    fun `reads the TOTAL PSS line`() {
        val raw = """
            Applications Memory Usage (in Kilobytes):
            Uptime: 1234 Realtime: 5678

            ** MEMINFO in pid 4242 [nd.max] **
                               Pss  Private  Private  SwapPss     Heap     Heap     Heap
                             Total    Dirty    Clean    Dirty     Size    Alloc     Free
                            ------   ------   ------   ------   ------   ------   ------
              Native Heap    12345    12000        0        0    20000    15000    10000
              Dalvik Heap     5678     5000      100        0    10000     8000     2000
                    TOTAL    98765    90000      500        0    40000    30000    20000
                 TOTAL PSS: 98765            TOTAL RSS: 120000       TOTAL SWAP PSS: 0
        """.trimIndent()

        assertEquals(98_765L, MemoryLedger.parseTotalPssKb(raw))
    }

    @Test
    fun `does not grab an unrelated number`() {
        val raw = """
            ** MEMINFO in pid 4242 [nd.max] **
                    TOTAL    98765    90000
                 TOTAL RSS: 120000
        """.trimIndent()
        assertNull("لا سطر TOTAL PSS ⇒ لا رقم", MemoryLedger.parseTotalPssKb(raw))
    }

    @Test
    fun `missing or malformed dumpsys output is null not zero`() {
        assertNull(MemoryLedger.parseTotalPssKb(null))
        assertNull(MemoryLedger.parseTotalPssKb(""))
        assertNull(MemoryLedger.parseTotalPssKb("   "))
        assertNull(MemoryLedger.parseTotalPssKb("Permission denial: can't access"))
        assertNull(MemoryLedger.parseTotalPssKb("TOTAL PSS: notanumber"))
        assertNull("صفر ليس قياسًا", MemoryLedger.parseTotalPssKb("TOTAL PSS: 0"))
    }

    @Test
    fun `picks the first TOTAL PSS when several appear`() {
        val raw = "TOTAL PSS: 111\nstuff\nTOTAL PSS: 222"
        assertEquals(111L, MemoryLedger.parseTotalPssKb(raw))
    }

    // ---- المقارنة ------------------------------------------------------------------

    @Test
    fun `no previous snapshot means no comparison`() {
        assertEquals(
            MemoryLedger.MemoryDelta.Insufficient,
            MemoryLedger.delta(null, snap(50_000L, MemoryLedger.Method.OWN_PROCESS)),
        )
    }

    @Test
    fun `a different measuring method is never compared`() {
        val previous = snap(50_000L, MemoryLedger.Method.DUMPSYS)
        val current = snap(90_000L, MemoryLedger.Method.OWN_PROCESS)
        assertEquals(
            "لا تُقارَن قراءة dumpsys بقراءة واجهة النظام",
            MemoryLedger.MemoryDelta.Insufficient,
            MemoryLedger.delta(previous, current),
        )
    }

    @Test
    fun `a small change is stable`() {
        val verdict = MemoryLedger.delta(
            snap(100_000L, MemoryLedger.Method.OWN_PROCESS),
            snap(105_000L, MemoryLedger.Method.OWN_PROCESS),
        )
        assertEquals(MemoryLedger.MemoryDelta.Stable(100_000L, 105_000L), verdict)
    }

    @Test
    fun `growth beyond the threshold is reported with its size and percent`() {
        val verdict = MemoryLedger.delta(
            snap(100_000L, MemoryLedger.Method.OWN_PROCESS),
            snap(130_000L, MemoryLedger.Method.OWN_PROCESS),
        )
        assertEquals(
            MemoryLedger.MemoryDelta.Changed(100_000L, 130_000L, 30_000L, 30),
            verdict,
        )
    }

    @Test
    fun `shrinkage is signed negatively`() {
        val verdict = MemoryLedger.delta(
            snap(100_000L, MemoryLedger.Method.OWN_PROCESS),
            snap(60_000L, MemoryLedger.Method.OWN_PROCESS),
        )
        assertEquals(
            MemoryLedger.MemoryDelta.Changed(100_000L, 60_000L, -40_000L, 40),
            verdict,
        )
    }

    @Test
    fun `an unusable previous value means no comparison`() {
        assertEquals(
            MemoryLedger.MemoryDelta.Insufficient,
            MemoryLedger.delta(
                snap(0L, MemoryLedger.Method.OWN_PROCESS),
                snap(50_000L, MemoryLedger.Method.OWN_PROCESS),
            ),
        )
    }

    // ---- التخزين -------------------------------------------------------------------

    @Test
    fun `snapshots survive a round trip`() {
        val snapshots = listOf(
            snap(10L, MemoryLedger.Method.OWN_PROCESS, at = 1L),
            snap(20L, MemoryLedger.Method.DUMPSYS, at = 2L, key = "com.other"),
        )
        assertEquals(snapshots, MemoryLedger.decode(MemoryLedger.encode(snapshots)))
    }

    @Test
    fun `the window is per key not per ledger`() {
        // مفتاح مزدحم لا يجب أن يُخرج مفتاحًا آخر من الدفتر.
        val crowded = (1..MemoryLedger.MAX_SNAPSHOTS_PER_KEY + 5).map {
            snap(1_000L + it, MemoryLedger.Method.OWN_PROCESS, at = it.toLong(), key = "busy")
        }
        val other = snap(500L, MemoryLedger.Method.OWN_PROCESS, at = 1L, key = "quiet")

        val trimmed = MemoryLedger.trim(crowded + other)

        assertEquals(MemoryLedger.MAX_SNAPSHOTS_PER_KEY, trimmed.count { it.key == "busy" })
        assertEquals(1, trimmed.count { it.key == "quiet" })
    }

    @Test
    fun `a corrupt entry is skipped without losing the ledger`() {
        val raw = """
            [{"key":"nd.max","kb":1000,"at":1,"method":"own"},
             {"key":"","kb":2000,"at":2,"method":"own"},
             {"key":"nd.max","kb":0,"at":3,"method":"own"},
             {"key":"nd.max","kb":3000,"at":4,"method":"own"}]
        """.trimIndent()
        val decoded = MemoryLedger.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(3_000L, decoded.last().totalPssKb)
    }

    @Test
    fun `broken storage is empty not a crash`() {
        assertTrue(MemoryLedger.decode(null).isEmpty())
        assertTrue(MemoryLedger.decode("").isEmpty())
        assertTrue(MemoryLedger.decode("nonsense").isEmpty())
    }

    @Test
    fun `an unknown method word falls back to the privileged path`() {
        // الأمان هنا: المجهول يُوسَم بالمصدر الأقل وثوقًا، لا بالأكثر.
        val decoded = MemoryLedger.decode("""[{"key":"p","kb":10,"at":1,"method":"whatever"}]""")
        assertEquals(MemoryLedger.Method.DUMPSYS, decoded.single().method)
    }
}
