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
 * `AR-09` — الجلسة والحكم الطولي.
 *
 * ما تحرسه هذه الاختبارات: **لا جلسة بلا عدّاد، ولا حكم بلا جلسات كافية، ولا «انحراف» بفارق
 * لا يستحقّ**. كلها خالصة، فتُختبر بلا جهاز.
 */
class ChargeLedgerTest {

    private fun obs(level: Int, counter: Long?, at: Long = 0L, charging: Boolean = true) =
        ChargeLedger.Observation(levelPct = level, counterUah = counter, charging = charging, atMs = at)

    // ---- استخراج الجلسة -------------------------------------------------------------

    @Test
    fun `a rising charge with a readable counter is a session`() {
        val session = ChargeLedger.sessionBetween(
            previous = obs(level = 20, counter = 1_000_000L),
            current = obs(level = 30, counter = 1_500_000L, at = 60_000L),
        )
        assertEquals(20, session?.levelFrom)
        assertEquals(30, session?.levelTo)
        assertEquals(500_000L, session?.chargeCounterDeltaUah)
        assertEquals(10, session?.levelDelta)
        assertEquals(50_000L, session?.uahPerPoint)
    }

    @Test
    fun `a small rise is noise not a session`() {
        val session = ChargeLedger.sessionBetween(
            previous = obs(level = 20, counter = 1_000_000L),
            current = obs(level = 22, counter = 1_100_000L),
        )
        assertNull("صعود أقل من الحدّ لا يُنتج جلسة", session)
    }

    @Test
    fun `no session without a counter on either side`() {
        assertNull(
            ChargeLedger.sessionBetween(
                previous = obs(level = 20, counter = null),
                current = obs(level = 40, counter = 2_000_000L),
            )
        )
        assertNull(
            ChargeLedger.sessionBetween(
                previous = obs(level = 20, counter = 1_000_000L),
                current = obs(level = 40, counter = null),
            )
        )
    }

    @Test
    fun `a counter that did not advance is not a session`() {
        // النسبة ارتفعت لكن العدّاد لم يتحرّك ⇒ قراءة غير متّسقة، ولا نصنع منها جلسة.
        assertNull(
            ChargeLedger.sessionBetween(
                previous = obs(level = 20, counter = 1_000_000L),
                current = obs(level = 40, counter = 1_000_000L),
            )
        )
        assertNull(
            ChargeLedger.sessionBetween(
                previous = obs(level = 20, counter = 2_000_000L),
                current = obs(level = 40, counter = 1_500_000L),
            )
        )
    }

    // ---- الحكم ---------------------------------------------------------------------

    private fun session(perPoint: Long) = ChargeSession(
        atMs = 0L,
        levelFrom = 20,
        levelTo = 30,
        chargeCounterDeltaUah = perPoint * 10,
    )

    @Test
    fun `no counter anywhere means no basis`() {
        assertEquals(ChargeVerdict.CounterUnavailable, ChargeLedger.verdict(emptyList()))
    }

    @Test
    fun `too few sessions means no verdict`() {
        val verdict = ChargeLedger.verdict(listOf(session(50_000L)))
        assertTrue(verdict is ChargeVerdict.InsufficientData)
        assertEquals(1, (verdict as ChargeVerdict.InsufficientData).have)
        assertEquals(ChargeLedger.MIN_SESSIONS_FOR_VERDICT, verdict.need)
    }

    @Test
    fun `a steady cost is steady`() {
        val verdict = ChargeLedger.verdict(
            listOf(session(50_000L), session(52_000L), session(51_000L))
        )
        assertEquals(ChargeVerdict.Stable(51_000L), verdict)
    }

    @Test
    fun `a clearly higher recent cost drifts`() {
        val verdict = ChargeLedger.verdict(
            listOf(session(50_000L), session(50_000L), session(80_000L))
        )
        assertEquals(ChargeVerdict.Drifting(50_000L, 80_000L), verdict)
    }

    @Test
    fun `a small rise is not called drift`() {
        // ‏+١٠٪ أقلّ من عتبة الانحراف (١٥٪) ⇒ لا نُطلق تحذيرًا لا يستحقّه.
        val verdict = ChargeLedger.verdict(
            listOf(session(50_000L), session(50_000L), session(55_000L))
        )
        assertEquals(ChargeVerdict.Stable(50_000L), verdict)
    }

    @Test
    fun `one outlier session does not move the verdict`() {
        // الوسيط لا المتوسط: جلسة شاذّة واحدة بين عدّة جلسات سليمة لا تُغيّر خطّ الأساس.
        val verdict = ChargeLedger.verdict(
            listOf(session(50_000L), session(500_000L), session(50_000L), session(51_000L))
        )
        assertTrue("الشاذّة لا تجعل الحكم انحرافًا", verdict is ChargeVerdict.Stable)
    }

    // ---- التخزين -------------------------------------------------------------------

    @Test
    fun `sessions survive a round trip`() {
        val sessions = listOf(session(50_000L), session(52_000L))
        assertEquals(sessions, ChargeLedger.decode(ChargeLedger.encode(sessions)))
    }

    @Test
    fun `the ledger keeps a rolling window`() {
        val many = (1..ChargeLedger.MAX_SESSIONS + 10).map { session(50_000L + it) }
        val encoded = ChargeLedger.decode(ChargeLedger.encode(many))
        assertEquals(ChargeLedger.MAX_SESSIONS, encoded.size)
        // النافذة تحتفظ بالأحدث، لا بالأقدم.
        assertEquals(many.last().chargeCounterDeltaUah, encoded.last().chargeCounterDeltaUah)
    }

    @Test
    fun `a corrupt entry is skipped without losing the rest`() {
        val raw = """
            [{"at":1,"from":20,"to":30,"uah":500000},
             {"at":2,"from":-1,"to":30,"uah":500000},
             {"at":3,"from":20,"to":30,"uah":0},
             {"at":4,"from":20,"to":40,"uah":900000}]
        """.trimIndent()
        val sessions = ChargeLedger.decode(raw)
        assertEquals("المدخلان المشوّهان يُتجاهلان", 2, sessions.size)
        assertEquals(900_000L, sessions.last().chargeCounterDeltaUah)
    }

    @Test
    fun `missing or broken storage is empty not a crash`() {
        assertTrue(ChargeLedger.decode(null).isEmpty())
        assertTrue(ChargeLedger.decode("").isEmpty())
        assertTrue(ChargeLedger.decode("not json at all").isEmpty())
    }
}
