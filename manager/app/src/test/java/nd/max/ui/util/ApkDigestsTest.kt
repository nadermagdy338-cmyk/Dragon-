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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بصمات الإمضاء — تُقاس على **قيم مرجعية معروفة** لا على «يبدو صحيحًا».
 *
 * وبصمة خاطئة (حالة أحرف، أو بايت ناقص في التنسيق السداسي) تجعل مقارنة توقيعين —
 * وهي الغرض من الميزة كلها — تقول «مختلفان» وهما متطابقان. فالقيم المرجعية هنا هي
 * الحكم: MD5 للفراغ، وSHA-1 وSHA-256 للنصّ `abc`.
 */
class ApkDigestsTest {

    @Test
    fun hexFormattingIsUppercaseAndTwoDigitsPerByte() {
        assertEquals("000FFF", ApkDigests.hex(byteArrayOf(0x00, 0x0F, 0xFF.toByte())))
        assertEquals("00", ApkDigests.hex(byteArrayOf(0x00)))
        assertEquals("", ApkDigests.hex(ByteArray(0)))
    }

    @Test
    fun theKnownVectorsMatchTheirPublishedDigests() {
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", ApkDigests.md5(ByteArray(0)))
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", ApkDigests.sha1("abc".toByteArray()))
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            ApkDigests.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun theThreeFingerprintsAreProducedTogetherWithTheirDeclaredNames() {
        val fingerprints = ApkDigests.fingerprints("certificate".toByteArray())
        assertEquals(listOf("MD5", "SHA-1", "SHA-256"), fingerprints.keys.toList())
        assertEquals(32, fingerprints.getValue("MD5").length)
        assertEquals(40, fingerprints.getValue("SHA-1").length)
        assertEquals(64, fingerprints.getValue("SHA-256").length)
        assertTrue(fingerprints.values.all { it == it.uppercase() })
    }

    /** ملف لم يُقرأ لا يُنسب إليه إصدار ولا حزمة — الغياب معلن لا مُصلَّح. */
    @Test
    fun anUnreadApkReportsNothingRatherThanInventingIt() {
        val facts = ApkFacts(path = "/sdcard/broken.apk")
        assertEquals(false, facts.readable)
        assertEquals(null, facts.packageName)
        assertEquals(null, facts.versionName)
        assertEquals(emptyList<String>(), facts.permissions)
    }
}
