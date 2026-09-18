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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AR-33` — المصدر المعلَن والتحقّق عند الاستيراد.
 *
 * ما تحرسه هذه الاختبارات هو جوهر البند: **لا ثقة مصنوعة** — لا `MEASURED` مُشتقّة من عندنا،
 * ولا مصدر مجهول يُرقّى إلى «افتراضي»، ولا جهاز مجهول يُقرأ كـ«نفس الجهاز».
 */
class ProfileSharingTest {

    private fun doc(entries: List<ProfileSharing.Entry>, soc: String? = "SM8650") =
        ProfileSharing.Document(
            exportedAtMs = 1_000L,
            device = ProfileSharing.DeviceContext(soc = soc, androidSdk = 34),
            entries = entries,
        )

    // ---- الوسم المشتق -----------------------------------------------------------------

    @Test
    fun `an untouched value is a shipped seed`() {
        assertEquals(
            ProfileSharing.DeclaredSource.SEED,
            ProfileSharing.deriveSource(value = 85, shippedDefault = 85),
        )
    }

    @Test
    fun `a changed value is a user setting`() {
        assertEquals(
            ProfileSharing.DeclaredSource.USER_SET,
            ProfileSharing.deriveSource(value = 70, shippedDefault = 85),
        )
    }

    @Test
    fun `we never derive a measurement`() {
        // لا قيمة ولا تركيبة افتراضية تُنتج MEASURED من عندنا — القياس لا يُشتق.
        val defaults = listOf(65, 70, 85, 100, 55)
        defaults.forEach { shipped ->
            listOf(shipped, shipped - 1, shipped + 1, 20, 100).forEach { value ->
                assertFalse(
                    "الاشتقاق أنتج MEASURED لقيمة $value",
                    ProfileSharing.deriveSource(value, shipped) == ProfileSharing.DeclaredSource.MEASURED,
                )
            }
        }
    }

    @Test
    fun `an unknown source word stays unknown`() {
        assertEquals(ProfileSharing.DeclaredSource.UNKNOWN, ProfileSharing.parseSource(null))
        assertEquals(ProfileSharing.DeclaredSource.UNKNOWN, ProfileSharing.parseSource(""))
        assertEquals(ProfileSharing.DeclaredSource.UNKNOWN, ProfileSharing.parseSource("benchmark"))
        assertEquals(ProfileSharing.DeclaredSource.UNKNOWN, ProfileSharing.parseSource("SEEDISH"))
    }

    @Test
    fun `known source words round trip`() {
        ProfileSharing.DeclaredSource.entries.forEach { source ->
            assertEquals(source, ProfileSharing.parseSource(ProfileSharing.sourceWord(source)))
        }
    }

    // ---- حكم الجهاز -------------------------------------------------------------------

    @Test
    fun `identifier casing and punctuation do not fake a match`() {
        assertEquals(ProfileSharing.DeviceMatch.SAME, ProfileSharing.matchDevice("SM8650", "sm8650"))
        assertEquals(ProfileSharing.DeviceMatch.SAME, ProfileSharing.matchDevice("SM-8650", "sm8650"))
    }

    @Test
    fun `a different chipset is a different device`() {
        assertEquals(
            ProfileSharing.DeviceMatch.DIFFERENT,
            ProfileSharing.matchDevice("SM8650", "MT6989"),
        )
    }

    @Test
    fun `an unknown side yields no verdict rather than a match`() {
        assertEquals(ProfileSharing.DeviceMatch.UNKNOWN, ProfileSharing.matchDevice(null, "SM8650"))
        assertEquals(ProfileSharing.DeviceMatch.UNKNOWN, ProfileSharing.matchDevice("SM8650", null))
        assertEquals(ProfileSharing.DeviceMatch.UNKNOWN, ProfileSharing.matchDevice("", ""))
    }

    // ---- الترميز والفكّ ---------------------------------------------------------------

    @Test
    fun `a document survives a round trip`() {
        val original = doc(
            listOf(
                ProfileSharing.Entry("power", 70, ProfileSharing.DeclaredSource.USER_SET),
                ProfileSharing.Entry("gaming", 90, ProfileSharing.DeclaredSource.MEASURED),
            )
        )
        val report = ProfileSharing.decode(ProfileSharing.encode(original), "SM8650")

        assertTrue(report.schemaSupported)
        assertEquals(2, report.accepted.size)
        assertEquals(ProfileSharing.DeclaredSource.USER_SET, report.accepted[0].source)
        assertEquals(ProfileSharing.DeclaredSource.MEASURED, report.accepted[1].source)
        assertEquals(ProfileSharing.DeviceMatch.SAME, report.deviceMatch)
    }

    @Test
    fun `an unsupported schema is refused not guessed`() {
        val raw = ProfileSharing.encode(doc(emptyList())).replace("\"schema\":1", "\"schema\":99")
        val report = ProfileSharing.decode(raw, "SM8650")

        assertFalse("صيغة لا نفهمها لا تُفسَّر على أنها نفهمها", report.schemaSupported)
        assertFalse(report.hasAnything)
    }

    @Test
    fun `junk is refused without throwing`() {
        listOf(null, "", "   ", "hello", "{", "[", "{}").forEach { raw ->
            val report = ProfileSharing.decode(raw, "SM8650")
            assertFalse("يجب رفض: '$raw'", report.hasAnything)
        }
    }

    @Test
    fun `an out of range value is rejected with its reason not clamped`() {
        val raw = """
            {"schema":1,"exported_at":1,"device":{"soc":"SM8650"},
             "entries":[{"profile":"power","percent":999,"source":"seed"},
                        {"profile":"gaming","percent":85,"source":"seed"}]}
        """.trimIndent()
        val report = ProfileSharing.decode(raw, "SM8650")

        assertEquals(1, report.accepted.size)
        assertEquals("gaming", report.accepted.first().profile)
        assertEquals(
            ProfileSharing.RejectReason.PERCENT_OUT_OF_RANGE,
            report.rejected.single().reason,
        )
        assertEquals("power", report.rejected.single().profile)
    }

    @Test
    fun `a non numeric value is rejected distinctly from an out of range one`() {
        val raw = """
            {"schema":1,"device":{"soc":"SM8650"},
             "entries":[{"profile":"power","percent":"high","source":"seed"}]}
        """.trimIndent()
        val report = ProfileSharing.decode(raw, "SM8650")
        assertEquals(ProfileSharing.RejectReason.PERCENT_NOT_A_NUMBER, report.rejected.single().reason)
    }

    @Test
    fun `an entry without a profile name is rejected`() {
        val raw = """
            {"schema":1,"device":{"soc":"SM8650"},
             "entries":[{"percent":80,"source":"seed"}]}
        """.trimIndent()
        val report = ProfileSharing.decode(raw, "SM8650")
        assertEquals(ProfileSharing.RejectReason.EMPTY_PROFILE, report.rejected.single().reason)
    }

    @Test
    fun `a value without a source is flagged and not promoted`() {
        val raw = """
            {"schema":1,"device":{"soc":"SM8650"},
             "entries":[{"profile":"power","percent":70}]}
        """.trimIndent()
        val report = ProfileSharing.decode(raw, "SM8650")

        assertEquals(1, report.accepted.size)
        assertEquals(ProfileSharing.DeclaredSource.UNKNOWN, report.accepted.single().source)
        assertEquals(listOf("power"), report.missingSource)
    }

    @Test
    fun `an import from another chipset is declared as such`() {
        val report = ProfileSharing.decode(
            ProfileSharing.encode(doc(listOf(ProfileSharing.Entry("power", 70, ProfileSharing.DeclaredSource.MEASURED)), soc = "MT6989")),
            currentSoc = "SM8650",
        )
        assertEquals(ProfileSharing.DeviceMatch.DIFFERENT, report.deviceMatch)
        // والقيمة تبقى موسومة كما صرّح مُصدِّرها — لا نُغيّر وسمها ولا نتبنّاه.
        assertEquals(ProfileSharing.DeclaredSource.MEASURED, report.accepted.single().source)
    }

    @Test
    fun `an export with no chipset cannot claim a match`() {
        val report = ProfileSharing.decode(
            ProfileSharing.encode(doc(listOf(ProfileSharing.Entry("power", 70, ProfileSharing.DeclaredSource.SEED)), soc = null)),
            currentSoc = "SM8650",
        )
        assertEquals(ProfileSharing.DeviceMatch.UNKNOWN, report.deviceMatch)
    }

    @Test
    fun `a wrapped array document is still read`() {
        val inner = ProfileSharing.encode(
            doc(listOf(ProfileSharing.Entry("gaming", 88, ProfileSharing.DeclaredSource.USER_SET)))
        )
        val report = ProfileSharing.decode("[$inner]", "SM8650")
        assertTrue(report.hasAnything)
        assertEquals(88, report.accepted.single().percent)
    }
}
