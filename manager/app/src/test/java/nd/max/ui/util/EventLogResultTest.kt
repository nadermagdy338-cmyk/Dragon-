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
import nd.max.core.platform.EventLog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AR-13` — نتيجة العملية المُسجَّلة.
 *
 * قبل هذا البند كان `cmd package compile` يُنادى ويُعاد منه `Boolean` فقط، فلا يبقى
 * سطر واحد يقول: هل نجح؟ وكم استغرق؟ هذه الاختبارات تثبّت أن السطر يحمل **النتيجة
 * الحقيقية والزمن المقيس**، وأن حقل الهدف لا يستطيع كسر السطر أو حقن أمر.
 */
class EventLogResultTest {

    @Test
    fun `result message carries every field`() {
        val message = EventLog.resultMessage(
            screen = "Dex2oat",
            action = "compile:speed-profile",
            target = "com.example.app",
            success = true,
            durationMs = 12_345L,
        )

        assertEquals(
            "EVENT=OP_RESULT screen=Dex2oat action=compile:speed-profile" +
                " target=com.example.app ok=true duration_ms=12345",
            message,
        )
    }

    @Test
    fun `omits target when there is none`() {
        val message = EventLog.resultMessage(
            screen = "Dex2oat",
            action = "reset_all",
            target = null,
            success = false,
            durationMs = 0L,
        )

        assertEquals("EVENT=OP_RESULT screen=Dex2oat action=reset_all ok=false duration_ms=0", message)
        assertFalse("لا يجب أن يظهر حقل هدف فارغ", message.contains("target="))
    }

    @Test
    fun `failure and success are never conflated`() {
        val failed = EventLog.resultMessage("S", "a", "t", success = false, durationMs = 1L)
        val passed = EventLog.resultMessage("S", "a", "t", success = true, durationMs = 1L)

        assertTrue(failed.contains("ok=false"))
        assertTrue(passed.contains("ok=true"))
    }

    @Test
    fun `symptom fields cannot break out either`() {
        val message = EventLog.symptomMessage(
            screen = "Ui'Health\nEVENT=SYMPTOM forged",
            symptom = "stall",
            valueMs = 1L,
        )
        assertEquals(1, message.lines().size)
        assertTrue(message.contains("'\\''"))
    }

    @Test
    fun `target cannot break out of its token`() {
        // هدف شرير: علامة اقتباس تحاول إغلاق الحقل، وسطر جديد يحاول تزوير سطر ثانٍ.
        val message = EventLog.resultMessage(
            screen = "Dex2oat",
            action = "compile:speed",
            target = "pkg'; rm -rf / #\nEVENT=OP_RESULT fake",
            success = true,
            durationMs = 5L,
        )

        assertFalse("يجب ألا يُسمح بسطر جديد في الحقل", message.contains("\n"))
        assertFalse(message.contains("\r"))
        // الاقتباس المفرد يُهرَّب بصيغة shell (‎'\''‎) فلا ينهي النصّ.
        assertTrue(message.contains("'\\''"))
        // ولا يستطيع الهدف تزوير سطر سجل ثانٍ: النصّ المحقون يبقى **داخل حقل الهدف**
        // بعد `target=`، والسطر يبقى واحدًا ببادئة واحدة حقيقية.
        assertTrue(message.startsWith("EVENT=OP_RESULT screen=Dex2oat action=compile:speed "))
        val targetField = message.substringAfter("target=").substringBefore(" ok=")
        assertTrue(
            "النصّ المحقون يجب أن يبقى محصورًا في حقل الهدف",
            targetField.contains("EVENT=OP_RESULT fake")
        )
        assertEquals(1, message.lines().size)
    }

    @Test
    fun `symptom message is distinct from a user action and an error`() {
        val message = EventLog.symptomMessage(
            screen = "UiHealth",
            symptom = "main_thread_stall",
            valueMs = 1_200L,
            worstMs = 2_400L,
            count = 3L,
        )

        assertEquals(
            "EVENT=SYMPTOM screen=UiHealth symptom=main_thread_stall" +
                " value_ms=1200 worst_ms=2400 count=3",
            message,
        )
        // العرض ليس فعل مستخدم ولا خطأً معالَجًا — ولو تشابهت البادئات لاختلطت أسباب السجل.
        assertFalse(message.contains("USER_ACTION"))
        assertFalse(message.contains("USER_TRIGGERED"))
        assertFalse(message.contains("UI_ERROR"))
    }

    @Test
    fun `symptom omits optional fields when unknown`() {
        val message = EventLog.symptomMessage("UiHealth", "stall", valueMs = 900L)
        assertEquals("EVENT=SYMPTOM screen=UiHealth symptom=stall value_ms=900", message)
        assertFalse(message.contains("worst_ms="))
        assertFalse(message.contains("count="))
    }

    @Test
    fun `write check records both sides and the verdict`() {
        val message = EventLog.writeCheckMessage(
            path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
            wrote = "schedutil",
            readBack = "interactive\n",
            verdict = "differs",
        )
        assertEquals(
            "EVENT=WRITE_CHECK path=/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor" +
                " wrote=schedutil read=interactive verdict=differs",
            message,
        )
    }

    @Test
    fun `write check marks an unreadable read back instead of inventing a value`() {
        val message = EventLog.writeCheckMessage(
            path = "/sys/kernel/debug/foo",
            wrote = "1",
            readBack = null,
            verdict = "unreadable",
        )
        assertTrue(message.contains("read=?"))
        assertTrue(message.endsWith("verdict=unreadable"))
        assertEquals(1, message.lines().size)
    }

    @Test
    fun `duration is reported verbatim not rounded`() {
        val message = EventLog.resultMessage("Dex2oat", "compile:speed", "app", true, 987_654_321L)
        assertTrue("الزمن المقيس يجب أن يمرّ كما هو", message.endsWith("duration_ms=987654321"))
    }

    @Test
    fun `audio op carries the verdict and both sides`() {
        val message = EventLog.audioOpMessage(
            target = "eq_band_3",
            outcome = "blocked",
            token = "audio-studio:17",
            reason = "manual-lock",
            expected = "-200",
            actual = "0",
        )
        assertEquals(
            "EVENT=AUDIO_OP token=audio-studio:17 target=eq_band_3 outcome=blocked" +
                " reason=manual-lock expected=-200 actual=0",
            message,
        )
    }

    @Test
    fun `audio op marks absent fields instead of inventing values`() {
        // لم تُجرَّب الكتابة (token) ولا سبب مكتوب (reason) ولا قراءة جانبين — والغياب يُكتب علامةً
        // لا قيمةً (ADR-07)؛ أما `none` فتعني «لا سبب مكتوب» كما تعرضه الشاشة، لا ادّعاء نجاح.
        val message = EventLog.audioOpMessage(
            target = "vendor_open",
            outcome = "not_attempted",
            token = null,
            reason = null,
            expected = null,
            actual = null,
        )
        assertEquals(
            "EVENT=AUDIO_OP token=? target=vendor_open outcome=not_attempted reason=none",
            message,
        )
        assertFalse("لا قيمة جانبيّة تُختلَق", message.contains("expected="))
        assertFalse(message.contains("actual="))
    }

    @Test
    fun `audio op fields cannot forge a second line`() {
        val message = EventLog.audioOpMessage(
            target = "stream_media",
            outcome = "applied\nEVENT=AUDIO_OP forged",
            token = "audio-studio:1'; rm -rf / #",
            reason = "none",
            expected = null,
            actual = null,
        )
        assertEquals(1, message.lines().size)
        assertFalse(message.contains("\r"))
        assertTrue(message.contains("'\\''"))
        assertTrue(message.startsWith("EVENT=AUDIO_OP token="))
    }

    @Test
    fun `audio op is its own event not a write check`() {
        val message = EventLog.audioOpMessage("stream_media", "applied", null, "none", null, null)
        // ولو تشابهت البادئات لاختلط سطرُ مقبضٍ صوتيّ بسطر عقدة sysfs عند التحليل.
        assertFalse(message.contains("WRITE_CHECK"))
        assertFalse(message.contains("USER_ACTION"))
        assertTrue(message.startsWith("EVENT=AUDIO_OP "))
    }
}
