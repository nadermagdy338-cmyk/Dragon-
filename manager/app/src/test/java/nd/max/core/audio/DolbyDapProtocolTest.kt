/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بروتوكول معاملات DAP — **مقيسٌ على JVM ببايتاته، بلا جهاز**.
 *
 * **ولماذا يُقاس البايت لا النداء:** النداء (`setParameter`) **ليس في سطح SDK العامّ** (مقيس بـ`javap`:
 * `@hide`)، فالوصول إليه لا يكون إلا بانعكاس أو بطبقة نظاميّة — أي **على جهاز**. أمّا ما يُرسل فيُقاس
 * هنا حرفًا بحرف، فإن فُتح المسار يومًا على جهاز كان الطلب صحيحًا بالبناء لا بالتجربة.
 *
 * **والمرجع المقياسي:** الترويسات والأرقام مطابقةٌ لما قُرئ في مصدر التطبيق المنقول منه البروتوكول
 * (راجع ترويسة `DolbyDapProtocol.kt` والترخيص في `docs/PROVENANCE.md`): صندوق الأعداد ١٢ بايت
 * `[param, 1, value]` · صندوق معامل الملفّ `(len+4)*4` · معرّف القراءة `(id shl 16)+(profile shl 8)+…`.
 */
class DolbyDapProtocolTest {

    /** إنديّة صغيرة: البايت الأدنى أوّلًا — والعكس هو عطبٌ صامت يُقرأ قيمةً أخرى. */
    @Test
    fun `integers are written and read little endian`() {
        val buffer = ByteArray(4)
        DolbyDapProtocol.writeInt32Le(buffer, 0, 0x01020304)
        assertArrayEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), buffer)
        DolbyDapProtocol.writeInt32Le(buffer, 0, -1)
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), buffer)
        assertEquals(-1, DolbyDapProtocol.readInt32Le(buffer, 0))
        assertEquals(0x01020304, DolbyDapProtocol.readInt32Le(byteArrayOf(0x04, 0x03, 0x02, 0x01), 0))
    }

    /** وقراءةٌ من موضعٍ لا تكفيه أربعة بايتات **غباءٌ يُرفض**، لا صفرٌ يُخترع. */
    @Test
    fun `reading outside the buffer yields null instead of a fabricated zero`() {
        assertNull(DolbyDapProtocol.readInt32Le(ByteArray(3), 0))
        assertNull(DolbyDapProtocol.readInt32Le(ByteArray(8), 5))
        assertNull(DolbyDapProtocol.readInt32Le(ByteArray(8), -1))
    }

    /** صندوق المعامل المفرد: **١٢ بايت دائمًا** وبالترتيب `[param, 1, value]`. */
    @Test
    fun `the single integer parameter block is twelve bytes in the measured order`() {
        val enable = DolbyDapProtocol.intParamBlock(DolbyDapProtocol.ENABLE_PARAM, 1)
        assertEquals(12, enable.size)
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 0, // المعامل ٠ (التمكين)
                1, 0, 0, 0, // الثابت ١
                1, 0, 0, 0, // القيمة
            ),
            enable,
        )
        assertEquals(0, DolbyDapProtocol.intParamValue(enable))

        val profile = DolbyDapProtocol.intParamBlock(DolbyDapProtocol.PROFILE_PARAM, 3)
        assertEquals(DolbyDapProtocol.PROFILE_PARAM, DolbyDapProtocol.readInt32Le(profile, 0))
        assertEquals(1, DolbyDapProtocol.readInt32Le(profile, 4))
        assertEquals(3, DolbyDapProtocol.readInt32Le(profile, 8))
    }

    /** وإعادة الضبط هي النداء نفسه بمعاملها المقيس — فلا مسار ثانٍ لمعنى واحد. */
    @Test
    fun `the reset block reuses the single parameter block with its own id`() {
        val reset = DolbyDapProtocol.resetProfileBlock(2)
        assertEquals(12, reset.size)
        assertEquals(DolbyDapProtocol.RESET_PROFILE_SETTINGS, DolbyDapProtocol.readInt32Le(reset, 0))
        assertEquals(1, DolbyDapProtocol.readInt32Le(reset, 4))
        assertEquals(2, DolbyDapProtocol.readInt32Le(reset, 8))
    }

    /** صندوق معامل الملفّ: `(len+4)*4`، وترويسته المقيسة، ثمّ القيم. */
    @Test
    fun `the profile parameter block carries the measured header then the values`() {
        // ومعاملٌ واحد القيمة: الطول يُطابق عدد القيم — وإلا رُفض البناء (وهو ما يُقاس في اختبارٍ آخر).
        val values = intArrayOf(1)
        val block = DolbyDapProtocol.profileParameterBlock(
            DolbyDapParam.HEADPHONE_VIRTUALIZER, values, profile = 0,
        )!!
        assertEquals((values.size + 4) * 4, block.size)
        assertEquals(DolbyDapProtocol.SET_PROFILE_PARAMETER, DolbyDapProtocol.readInt32Le(block, 0))
        assertEquals(values.size + 1, DolbyDapProtocol.readInt32Le(block, 4))
        assertEquals(0, DolbyDapProtocol.readInt32Le(block, 8))
        assertEquals(DolbyDapParam.HEADPHONE_VIRTUALIZER.id, DolbyDapProtocol.readInt32Le(block, 12))
        values.forEachIndexed { index, value ->
            assertEquals(value, DolbyDapProtocol.readInt32Le(block, 16 + index * 4))
        }
    }

    /** ومعامل العشرين قيمة يُنتج صندوقه بالحجم المتوقّع — وهو الوحيد ذو الطول. */
    @Test
    fun `the twenty value gain parameter produces its fifty two byte times four block`() {
        val gains = IntArray(DolbyDapParam.GEQ_BAND_GAINS.length) { it * 10 }
        val block = DolbyDapProtocol.profileParameterBlock(DolbyDapParam.GEQ_BAND_GAINS, gains, profile = 1)!!
        assertEquals(
            (DolbyDapParam.GEQ_BAND_GAINS.length + 4) * 4,
            block.size,
        )
        assertEquals(DolbyDapParam.GEQ_BAND_GAINS.length + 1, DolbyDapProtocol.readInt32Le(block, 4))
        assertEquals(1, DolbyDapProtocol.readInt32Le(block, 8))
        assertEquals(DolbyDapParam.GEQ_BAND_GAINS.id, DolbyDapProtocol.readInt32Le(block, 12))
        assertEquals(190, DolbyDapProtocol.readInt32Le(block, 16 + 19 * 4))
    }

    /**
     * **والحراسة التي زِدناها على المنقول:** قيمٌ لا تساوي طول معاملها تُرفض، لأنها تُفسد المعامل
     * التالي في المادّة — وهي عطبٌ لا يُنتجه مُصرّف.
     */
    @Test
    fun `a value count that disagrees with the declared length is refused`() {
        assertNull(
            DolbyDapProtocol.profileParameterBlock(DolbyDapParam.GEQ_BAND_GAINS, IntArray(19), profile = 0),
        )
        assertNull(
            DolbyDapProtocol.profileParameterBlock(DolbyDapParam.BASS_ENHANCER_ENABLE, intArrayOf(1, 0), 0),
        )
        // والسالب ملفّ شخصيّ غير صالح — يُرفض بدل أن يُرسل مشوَّهًا.
        assertNull(
            DolbyDapProtocol.profileParameterBlock(DolbyDapParam.BASS_ENHANCER_ENABLE, intArrayOf(1), -1),
        )
    }

    /** معرّف القراءة: `(id shl 16) + (profile shl 8) + 0x1000005` — والرقم يُشتقّ ولا يُكتب بيد. */
    @Test
    fun `the read request id is the measured composition of parameter and profile`() {
        val expected = (110 shl 16) + (2 shl 8) + 0x1000005
        assertEquals(
            expected,
            DolbyDapProtocol.profileParameterRequestId(DolbyDapParam.GEQ_BAND_GAINS, 2),
        )
        assertEquals(
            (101 shl 16) + (0 shl 8) + 0x1000005,
            DolbyDapProtocol.profileParameterRequestId(DolbyDapParam.HEADPHONE_VIRTUALIZER, 0),
        )
    }

    /**
     * **وحدّ الإزاحة مضافٌ من عندنا:** معرّفٌ يتجاوز ١٦ بتًا أو ملفّ شخصيّ يتجاوز بايتًا يجعل
     * المعرّفين يتداخلان فيسأل الطلب عن معامل آخر — فيُرفض الطلب بدل أن يُرسل خاطئًا.
     */
    @Test
    fun `a request id would collide outside the shift limits so it is refused`() {
        assertNull(DolbyDapProtocol.profileParameterRequestId(DolbyDapParam.BASS_ENHANCER_ENABLE, 0x100))
        assertNull(DolbyDapProtocol.profileParameterRequestId(DolbyDapParam.BASS_ENHANCER_ENABLE, -1))
        assertTrue(DolbyDapProtocol.profileParameterRequestId(DolbyDapParam.BASS_ENHANCER_ENABLE, 0xFF)!! > 0)
    }

    /** صندوق القراءة بحجم `(length + 2) * 4`، والقيم تُقرأ منه بعدد طول المعامل. */
    @Test
    fun `the read buffer holds the declared length and yields the values back`() {
        val buffer = DolbyDapProtocol.profileParameterBuffer(DolbyDapParam.GEQ_BAND_GAINS)
        assertEquals((DolbyDapParam.GEQ_BAND_GAINS.length + 2) * 4, buffer.size)

        val values = intArrayOf(7, -3, 100)
        // ثلاثة أعدادٍ فقط كُتبت: فالقراءة تعود بثلاثة — لا بعشرين صفرًا يُخترع لبقيّة الصندوق.
        val written = ByteArray(values.size * 4)
        values.forEachIndexed { index, value -> DolbyDapProtocol.writeInt32Le(written, index * 4, value) }
        assertArrayEquals(values, DolbyDapProtocol.profileParameterValues(written, DolbyDapParam.GEQ_BAND_GAINS))
    }

    /** والقيم المطلوبة لا تتجاوز ما كُتب فعلًا ولا طول المعامل — فلا تُخترع أصفارٌ لبقيّة الصندوق. */
    @Test
    fun `the values read never exceed what the buffer actually holds`() {
        val short = ByteArray(4)
        DolbyDapProtocol.writeInt32Le(short, 0, 42)
        val read = DolbyDapProtocol.profileParameterValues(short, DolbyDapParam.GEQ_BAND_GAINS)
        assertEquals(1, read.size)
        assertEquals(42, read[0])

        assertArrayEquals(IntArray(0), DolbyDapProtocol.profileParameterValues(ByteArray(0), DolbyDapParam.IEQ_PRESET))
    }

    /** والشكل العامّ: صندوقٌ مكتوب يُقرأ بنفس القيم — دورةٌ كاملة لا تفقد بايتًا. */
    @Test
    fun `a written profile block round trips through the read helpers`() {
        val gains = IntArray(DolbyDapParam.GEQ_BAND_GAINS.length) { (it - 10) * 3 }
        val block = DolbyDapProtocol.profileParameterBlock(DolbyDapParam.GEQ_BAND_GAINS, gains, profile = 4)!!
        val payload = block.copyOfRange(16, block.size)
        assertArrayEquals(gains, DolbyDapProtocol.profileParameterValues(payload, DolbyDapParam.GEQ_BAND_GAINS))
    }
}
