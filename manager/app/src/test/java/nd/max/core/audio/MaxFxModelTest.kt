/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * اختبار عقود `maxfx` من الطرف الكوتلنّيّ — **يُقرأ الـTSV ويُقارن، ولا يُعاد هنا رقمٌ واحد**:
 * ما في `fixtures/contracts/maxfx_{params,identity}.tsv` هو الحقيقة الوحيدة،
 * وأيّ تباعدٍ بينه وبين [MaxFxModel] عطبٌ يُسقط الاختبار — والطرف C (`maxfx/tests/dsp_test.c`)
 * يقيس العقد نفسه من جهته، فالطفرة على أيّ طرفٍ تُسقط قياسًا.
 */
@RunWith(JUnit4::class)
class MaxFxModelTest {

    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }
        .take(8)
        .map { File(it, "fixtures/contracts") }
        .firstOrNull { it.isDirectory }
        ?: error("fixtures/contracts غير موجودة — شغّل الاختبار من داخل المستودع")

    private fun rowsOf(name: String, header: String): List<List<String>> =
        File(fixtures, name).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it != header }
            .map { line ->
                val parts = line.split("\t")
                require(parts.size >= 2) { "سطر عقد مشوّه: $line" }
                parts
            }

    private fun paramRows(): List<List<String>> =
        rowsOf("maxfx_params.tsv", "key\tid\tkind\tdefault\tmin\tmax")

    private fun identityRows(): Map<String, String> =
        rowsOf("maxfx_identity.tsv", "field\tvalue").associate { it[0] to it[1] }

    @Test
    fun params_match_tsv_contract_exactly() {
        val rows = paramRows()
        assertEquals("عدد المعاملات", rows.size, MaxFxModel.params.size)
        rows.forEachIndexed { i, r ->
            val p = MaxFxModel.params[i]
            assertEquals("key #$i", r[0], p.key)
            assertEquals("id #$i (${r[0]})", r[1].toInt(), p.id)
            assertTrue("kind صحيحٌ أو عشريٌّ فقط (${r[0]})", r[2] == "int" || r[2] == "float")
            assertEquals("kind #$i (${r[0]})", r[2] == "int", p.isInt)
            assertEquals("default #$i (${r[0]})", r[3].toDouble(), p.def, 0.0)
            assertEquals("min #$i (${r[0]})", r[4].toDouble(), p.min, 0.0)
            assertEquals("max #$i (${r[0]})", r[5].toDouble(), p.max, 0.0)
            assertTrue("النطاق مرتّب والافتراض داخله (${r[0]})", p.min <= p.def && p.def <= p.max)
        }
        // id هو مفتاح `effect_param_t`: تكراره يعني قراءة معاملٍ بآخر
        assertEquals("id فريدة", rows.size, MaxFxModel.params.map { it.id }.toSet().size)
    }

    @Test
    fun identity_matches_tsv_contract_exactly() {
        val rows = identityRows()
        // الـTSV هو المتوقَّع والنموذج هو المُقاس — فرسالة الفشل تشير إلى النموذج
        assertEquals(rows["type_uuid"], MaxFxModel.TYPE_UUID)
        assertEquals(rows["impl_uuid"], MaxFxModel.IMPL_UUID)
        assertEquals(rows["library_file"], MaxFxModel.LIBRARY_FILE)
        assertEquals(rows["library_name"], MaxFxModel.LIBRARY_NAME)
        assertEquals(rows["effect_name"], MaxFxModel.EFFECT_NAME)
        assertEquals(rows["effect_config_name"], MaxFxModel.EFFECT_CONFIG_NAME)
        assertEquals(rows["prop_prefix"], MaxFxModel.PROP_PREFIX)
        // والعقد سبعة حقول لا أقلّ — قارئٌ يقرأ ستّة يمرّ بصمت
        assertEquals("حقول الهويّة", 7, rows.size)
    }

    @Test
    fun prop_channel_matches_the_native_reader() {
        // القارئ في `maxfx_effect.c`: `%s%s` من البادئة واللاّحقة، و`strtol`/`strtof` للقيمة
        assertEquals("${MaxFxModel.PROP_PREFIX}enable", MaxFxModel.propKey("enable"))
        assertNull("مفتاحٌ خارج العقد لا يُمرَّر", MaxFxModel.propKey("nope"))

        val rows = paramRows().associateBy { it[0] }
        // الاقتصاص عند حدود العقد — مأخوذٌ من الجدول لا من ذاكرة
        val width = rows.getValue("width")
        assertEquals(width[5].toDouble(), MaxFxModel.propValue("width", width[5].toDouble() + 1.0)!!.toDouble(), 0.0)
        assertEquals(width[4].toDouble(), MaxFxModel.propValue("width", width[4].toDouble() - 1.0)!!.toDouble(), 0.0)
        // الصحيح يُقرَّب ويبقى في حدوده
        val enable = rows.getValue("enable")
        assertEquals(enable[5].toDouble(), MaxFxModel.propValue("enable", 0.7)!!.toDouble(), 0.0)
        assertEquals(enable[4].toDouble(), MaxFxModel.propValue("enable", -3.0)!!.toDouble(), 0.0)
        // NaN/∞ لا تُكتب — رقمٌ فاسدٌ يُقرأ قيمةً (والغياب ليس صفرًا: ADR-07)
        assertNull(MaxFxModel.propValue("width", Double.NaN))
        assertNull(MaxFxModel.propValue("width", Double.POSITIVE_INFINITY))
        assertNull("قيمةٌ بمفتاحٍ مجهول لا تُمرَّر", MaxFxModel.propValue("nope", 1.0))
    }

    @Test
    fun prop_value_is_decimal_text_within_table_bounds() {
        // كل قيمة مُخرجة يقرأها `strtof`/`strtol` حرفّيًّا ثمّ تبقى في نطاق الجدول — بلا أسّ عشريّ
        paramRows().forEach { r ->
            val key = r[0]
            val min = r[4].toDouble()
            val max = r[5].toDouble()
            listOf(min, r[3].toDouble(), max, (min + max) / 2).forEach { v ->
                val text = MaxFxModel.propValue(key, v)
                assertTrue("قيمةٌ غير فارغة ($key=$v)", text != null)
                assertTrue("بلا أسّ عشريّ ($key=$text)", 'e' !in text!! && 'E' !in text)
                val parsed = text.toDouble()
                assertTrue("ضمن النطاق ($key=$parsed)", parsed >= min - 1e-9 && parsed <= max + 1e-9)
            }
        }
    }

    @Test
    fun installAddition_roundtrips_through_the_ui_parser() {
        val line = MaxFxModel.installAddition()
        val parsed = audioEffectAdditionOf(line)
        assertTrue("سطر الإضافة يمرّ بمحسّنة الشاشة ($line)", parsed != null)
        val identity = identityRows()
        assertEquals(MaxFxModel.CONFIG_LIBRARY_ID, parsed!!.libraryName)
        // مسار التهيئة = اسم ملفّ العقد — فلا يُنتج اسمٌ لا يوجد على الجهاز
        assertEquals(identity["library_file"], parsed.libraryPath)
        assertEquals(MaxFxModel.EFFECT_CONFIG_NAME, parsed.effectName)
        // uuid المؤثّر في التهيئة هو uuid التنفيذ لا uuid النوع
        assertEquals(identity["impl_uuid"], parsed.effectUuid)
        // **ونوع المؤثّر (`type`) هو uuid النوع لا uuid التنفيذ** — وهما حقلان لا يُخلطان:
        // `uuid` يُعرّف التنفيذ و`type` يُعرّف النوع، ومصنع AIDL يُسقط المؤثّر كله إن غاب الثاني.
        assertEquals(identity["type_uuid"], parsed.effectType)
        assertEquals(MaxFxModel.TYPE_UUID, parsed.effectType)
        // ولا ربط بمخرجٍ بعينه في السطر: يُعلَن المؤثّر ولا يُفرض على جهاز (من أراد ربطه كتبه).
        assertTrue("بلا أجهزة مربوطة", parsed.deviceTypes.isEmpty())
    }

    /**
     * **وكل معرّف يظهر في السطر هو معرّفٌ في العقد لا نصٌّ ثانٍ يُكتب بيد:** العقد (TSV) هو الحقيقة
     * الوحيدة، وسطرُ الإضافة يُشتقّ منه — فلا ينحرف المعرّفان عن الهويّة المعلنة في TSV.
     */
    @Test
    fun install_addition_takes_both_uuids_from_the_identity_contract() {
        val identity = identityRows()
        val fields = MaxFxModel.installAddition().split('|')
        assertTrue("سطر الإضافة يحمل الحقل السادس", fields.size > AUDIO_ADDITION_TYPE_FIELD)
        assertEquals(identity["impl_uuid"], fields[3])
        assertEquals(identity["type_uuid"], fields[AUDIO_ADDITION_TYPE_FIELD])
    }
}
