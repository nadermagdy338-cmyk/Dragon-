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

import nd.max.ui.util.PermissionPolicy.OpMode
import nd.max.ui.util.PermissionPolicy.OpState
import nd.max.ui.util.PermissionPolicy.Reference
import nd.max.ui.util.PermissionPolicy.WriteVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات `GAP-07`. والقاعدة التي تحكمها كلها: **ما لا نفهمه لا يُخمَّن** — يُسقَط أو يُرفض.
 */
class PermissionPolicyTest {

    // ────────────────────────────────────────────────────────────────────────
    // تحليل المخرج
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `parses the real appops get shape and skips header lines`() {
        val parsed = PermissionPolicy.parseAppOps(
            listOf(
                "Uid: 10123",
                "Package: com.example",
                "  CAMERA: allow",
                "  READ_CONTACTS: deny; rejectTime=+1d ago",
            )
        )
        assertEquals(
            listOf(
                OpState("CAMERA", OpMode.ALLOW),
                OpState("READ_CONTACTS", OpMode.DENY),
            ),
            parsed,
        )
    }

    @Test
    fun `accepts a single-op read with no indentation`() {
        assertEquals(
            listOf(OpState("CAMERA", OpMode.ALLOW)),
            PermissionPolicy.parseAppOps(listOf("CAMERA: allow")),
        )
    }

    @Test
    fun `drops an unknown mode instead of guessing one`() {
        // «silent» وضع لا نعرفه: إسقاطه أصحّ من تحويله إلى allow أو deny.
        assertTrue(PermissionPolicy.parseAppOps(listOf("  CAMERA: silent")).isEmpty())
    }

    @Test
    fun `drops lines that do not look like op names`() {
        assertTrue(
            PermissionPolicy.parseAppOps(
                listOf("some free text: allow", "lowercase_op: allow", "  : allow")
            ).isEmpty()
        )
    }

    @Test
    fun `keeps the first declaration when an op repeats`() {
        val parsed = PermissionPolicy.parseAppOps(
            listOf("  CAMERA: allow", "  CAMERA: deny")
        )
        assertEquals(listOf(OpState("CAMERA", OpMode.ALLOW)), parsed)
    }

    @Test
    fun `parseSingleOp returns null when nothing was declared`() {
        assertNull(PermissionPolicy.parseSingleOp(listOf("Uid: 1")))
        assertNull(PermissionPolicy.parseSingleOp(null))
        assertEquals(OpMode.FOREGROUND, PermissionPolicy.parseSingleOp(listOf("CAMERA: foreground")))
    }

    // ────────────────────────────────────────────────────────────────────────
    // حكم الكتابة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `applied when the value read back matches what we wrote`() {
        assertEquals(
            WriteVerdict.APPLIED,
            PermissionPolicy.verdict(OpMode.DENY, OpMode.DENY, commandSucceeded = true),
        )
    }

    @Test
    fun `device ignored a value it kept overriding`() {
        assertEquals(
            WriteVerdict.IGNORED_BY_DEVICE,
            PermissionPolicy.verdict(OpMode.DENY, OpMode.ALLOW, commandSucceeded = true),
        )
    }

    @Test
    fun `absent row is success for default but not for anything else`() {
        // المنصّة ترفع التجاوز فتُزيل الصف: الغياب بعد default دليل نجاح...
        assertEquals(
            WriteVerdict.APPLIED_AS_DEFAULT,
            PermissionPolicy.verdict(OpMode.DEFAULT, readBack = null, commandSucceeded = true),
        )
        // ...وغيابه بعد أي وضع آخر ليس نجاحًا ولا فشلًا: «لم أستطع التحقّق».
        assertEquals(
            WriteVerdict.UNVERIFIABLE,
            PermissionPolicy.verdict(OpMode.DENY, readBack = null, commandSucceeded = true),
        )
    }

    @Test
    fun `a failed command is never reported as applied`() {
        assertEquals(
            WriteVerdict.FAILED,
            PermissionPolicy.verdict(OpMode.DENY, OpMode.DENY, commandSucceeded = false),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // المرجع والانحراف
    // ────────────────────────────────────────────────────────────────────────

    private fun reference(vararg ops: Pair<String, OpMode>) = Reference(
        pkg = "com.example",
        savedAtMs = 1_700_000_000_000L,
        ops = linkedMapOf(*ops),
    )

    @Test
    fun `drift is sorted by op and reports both sides`() {
        val ref = reference(
            "CAMERA" to OpMode.ALLOW,
            "READ_CONTACTS" to OpMode.ALLOW,
            "SMS" to OpMode.ALLOW,
        )
        val drift = PermissionPolicy.drift(
            ref,
            listOf(
                OpState("SMS", OpMode.DENY),
                OpState("CAMERA", OpMode.ALLOW),
                // READ_CONTACTS اختفت من الجدول ⇒ صف غير مُعلَن، وهو انحراف أيضًا.
            ),
        )
        assertEquals(listOf("READ_CONTACTS", "SMS"), drift.map { it.op })
        assertNull(drift.first { it.op == "READ_CONTACTS" }.current)
        assertEquals(OpMode.DENY, drift.first { it.op == "SMS" }.current)
    }

    @Test
    fun `no drift means no writes at all`() {
        val ref = reference("CAMERA" to OpMode.ALLOW)
        val drift = PermissionPolicy.drift(ref, listOf(OpState("CAMERA", OpMode.ALLOW)))
        assertTrue(drift.isEmpty())
        // خطة فارغة: لا نُرسل أمرًا إلى النظام بلا سبب.
        assertTrue(PermissionPolicy.restorePlan(ref, drift).isEmpty())
    }

    @Test
    fun `restore plan writes back the reference value, not the current one`() {
        val ref = reference("CAMERA" to OpMode.ALLOW, "SMS" to OpMode.FOREGROUND)
        val drift = PermissionPolicy.drift(
            ref,
            listOf(OpState("CAMERA", OpMode.DENY), OpState("SMS", OpMode.DEFAULT)),
        )
        assertEquals(
            mapOf("CAMERA" to OpMode.ALLOW, "SMS" to OpMode.FOREGROUND),
            PermissionPolicy.restorePlan(ref, drift),
        )
    }

    @Test
    fun `an op outside the reference is not treated as drift`() {
        // الجدول قد يُعلن عمليات لم تكن في المرجع؛ ليست انحرافًا عن شيء أوعزنا به.
        // و`CAMERA` تبقى انحرافًا لأنها سقطت من الجدول (رجعت إلى الافتراضي)، و`VIBRATE` لا.
        val ref = reference("CAMERA" to OpMode.ALLOW)
        val drift = PermissionPolicy.drift(ref, listOf(OpState("VIBRATE", OpMode.DENY)))
        assertEquals(listOf("CAMERA"), drift.map { it.op })
        assertNull(drift.single().current)
    }

    // ────────────────────────────────────────────────────────────────────────
    // الترميز
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `codec round-trips a reference`() {
        val ref = reference("CAMERA" to OpMode.ALLOW, "SMS" to OpMode.FOREGROUND)
        assertEquals(ref, PermissionPolicy.Codec.decode(PermissionPolicy.Codec.encode(ref)))
    }

    @Test
    fun `codec rejects a schema we do not understand`() {
        val json = PermissionPolicy.Codec.encode(reference("CAMERA" to OpMode.ALLOW))
            .replace("\"schema\":1", "\"schema\":99")
        assertNull(PermissionPolicy.Codec.decode(json))
    }

    @Test
    fun `codec rejects a document with an unknown mode`() {
        val json = PermissionPolicy.Codec.encode(reference("CAMERA" to OpMode.ALLOW))
            .replace("\"allow\"", "\"silent\"")
        assertNull(PermissionPolicy.Codec.decode(json))
    }

    @Test
    fun `codec rejects malformed or incomplete documents`() {
        assertNull(PermissionPolicy.Codec.decode("not json"))
        assertNull(PermissionPolicy.Codec.decode("{}"))
        assertNull(
            PermissionPolicy.Codec.decode(
                """{"schema":1,"pkg":"","savedAtMs":1,"ops":{}}"""
            )
        )
        // savedAtMs مفقود: مستند بلا وقت لا يُقارَن بشيء.
        assertNull(
            PermissionPolicy.Codec.decode("""{"schema":1,"pkg":"a.b","ops":{"CAMERA":"allow"}}""")
        )
    }

    @Test
    fun `encodeOps is readable and rejects nothing it was given`() {
        val array = PermissionPolicy.Codec.encodeOps(
            listOf(OpState("CAMERA", OpMode.ALLOW), OpState("SMS", OpMode.DENY))
        )
        assertEquals(2, array.length())
        assertEquals("CAMERA", array.getJSONObject(0).getString("op"))
        assertEquals("deny", array.getJSONObject(1).getString("mode"))
    }

    @Test
    fun `every writable mode is a mode the platform can report back`() {
        // ضمانة ضدّ إضافة وضع للكتابة لا يستطيع التحليل قراءته (فينتهي دائمًا «غير قابل للتحقّق»).
        PermissionPolicy.WRITABLE_MODES.forEach { mode ->
            assertEquals(mode, OpMode.fromId(mode.id))
        }
    }
}
