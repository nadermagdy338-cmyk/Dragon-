/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import nd.max.core.hardware.PerAppHardwareStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد `runtime/per_app_hw_status` — قناة الحقيقة بين المحرّك والواجهة.
 *
 * وهذا هو العقد الذي كان غيابه يشكو منه المستخدم حرفيًّا: مقبض يخرج من خمس بوابات بـ
 * `return@runCatching` بلا حالة وبلا سطر، فيقرأ «لم يحدث شيء» ولا يعرف أيّ بوابة أُغلقت.
 * فالمدافَع عنه هنا شيئان: **الصيغة** (تُثبَّت على الملف المشترك) و**عدم اختراع النجاح**
 * (رمز خارج الـenum المُعلَن يبقى مجهولًا، لا يُقرأ `applied`).
 */
class PerAppHardwareStatusContractTest {

    private fun encodeFixtureValues(): String = PerAppHardwareStatus.encode(
        pkg = "com.example.game",
        atMs = 1_758_000_000_000L,
        records = listOf(
            PerAppHardwareStatus.Record("gpu_profile", "applied", "verified", "gaming", "gaming"),
            PerAppHardwareStatus.Record(
                "cpu_limits:policy0", "blocked", "manual-lock", "300000:2000000", "300000:2000000",
            ),
            PerAppHardwareStatus.Record("thermal", "unsupported", "", "", ""),
        ),
    )

    @Test
    fun `the encoder reproduces the shared fixture byte for byte`() {
        assertEquals(
            "the writer and fixtures/contracts/per_app_hw_status.valid.txt disagree",
            ContractFixtures.text("per_app_hw_status.valid.txt"),
            encodeFixtureValues(),
        )
    }

    @Test
    fun `the version handshake leads the file and does not become a record`() {
        val text = encodeFixtureValues()
        assertEquals("v=${PerAppHardwareStatus.VERSION}", text.lineSequence().first())

        val snapshot = PerAppHardwareStatus.parse(text)
        assertEquals("the handshake must not be read as a knob", 3, snapshot?.records?.size)
        assertTrue(snapshot?.records?.none { it.knob.startsWith("v") } == true)
    }

    @Test
    fun `every declared outcome token survives a round trip as itself`() {
        // والرموز تُقرأ من الـenum نفسه لا من قائمة مكتوبة بيد هنا: فزيادة رمز تُضيف حالة
        // تُقاس تلقائيًّا، ولا تبقى قائمة ثانية تتقادم في صمت.
        for (outcome in PerAppHardwareStatus.Outcome.entries) {
            val text = PerAppHardwareStatus.encode(
                "com.example.game",
                0L,
                listOf(PerAppHardwareStatus.Record("knob", outcome.token, "because", "-", "-")),
            )
            assertEquals(
                "outcome ${outcome.token} did not survive encode→parse",
                outcome.token,
                PerAppHardwareStatus.parse(text)?.records?.single()?.outcome,
            )
        }
    }

    @Test
    fun `an unknown outcome token stays unknown rather than reading as applied`() {
        val text = ContractFixtures.text("per_app_hw_status.torn.txt")
        val snapshot = PerAppHardwareStatus.parse(text)

        assertEquals("com.example.game", snapshot?.pkg)
        assertEquals("a malformed at= must read as 0, not as a stale or invented time", 0L, snapshot?.atMs)
        assertEquals("unknown keys must not turn into records", 2, snapshot?.records?.size)

        val first = snapshot!!.records.first()
        assertEquals("future-outcome", first.outcome)
        assertNull(
            "an outcome token outside the declared enum must not resolve to a known one",
            PerAppHardwareStatus.Outcome.fromToken(first.outcome),
        )
        assertTrue("unknown must count as a failure, never as success", first.isFailure)
        assertEquals("", first.live)
    }
}
