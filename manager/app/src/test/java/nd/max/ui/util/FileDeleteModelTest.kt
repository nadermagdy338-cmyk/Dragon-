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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `UX-06 ①` — حوار الحذف يجمع الأحجام (فجوة `mt-file-manager-spec` §10.5).
 *
 * والذي يُحرَس: **الحجم المجهول يُعلن ولا يُصفَّر**. مجموع ناقص يُعرض كأنه الحجم كلّه هو
 * أسوأ من غيابه، لأن المستخدم يقرؤه ثمنًا للحذف ثم يجد غيره.
 */
class FileDeleteModelTest {

    private fun file(name: String, size: Long?) = FileEntry(
        name = name,
        path = "/root/$name",
        kind = FileKind.RegularFile,
        sizeBytes = size,
        modifiedEpochSec = 1L,
    )

    private fun dir(name: String) = FileEntry(
        name = name,
        path = "/root/$name",
        kind = FileKind.Directory,
        sizeBytes = null,
        modifiedEpochSec = 1L,
    )

    @Test
    fun `known sizes are summed and the total is offered`() {
        val target = FileDeleteTargets.of(listOf(file("a", 1_000), file("b", 2_500)))
        assertEquals(2, target.count)
        assertEquals(3_500L, target.knownBytes)
        assertEquals(0, target.unknownCount)
        assertTrue(target.sizeKnown)
        assertEquals(3_500L, target.sizeBytes)
    }

    /** المجلد لا يُعدّ محتواه في حوار تأكيد — فحجمه مجهول لا صفر. */
    @Test
    fun `a folder makes the size unknown instead of adding a zero`() {
        val target = FileDeleteTargets.of(listOf(file("a", 1_000), dir("stuff")))
        assertEquals(2, target.count)
        assertEquals(1_000L, target.knownBytes)
        assertEquals(1, target.unknownCount)
        assertFalse(target.sizeKnown)
        assertNull("لا يُعرض مجموع ناقص كأنه الحجم كلّه", target.sizeBytes)
    }

    @Test
    fun `a file whose size we never read counts as unknown too`() {
        val target = FileDeleteTargets.of(listOf(file("a", null), file("b", 10)))
        assertEquals(1, target.unknownCount)
        assertFalse(target.sizeKnown)
    }

    @Test
    fun `the paths are kept in the order they were listed`() {
        val target = FileDeleteTargets.of(listOf(file("z", 1), file("a", 1), file("m", 1)))
        assertEquals(listOf("/root/z", "/root/a", "/root/m"), target.paths)
    }

    /** هدف فارغ لا يُقال عنه «0 B»: الصفر هنا بلا معنى. */
    @Test
    fun `an empty target never claims a zero size`() {
        val target = FileDeleteTargets.of(emptyList())
        assertEquals(0, target.count)
        assertTrue(target.isEmpty)
        assertFalse(target.sizeKnown)
        assertNull(target.sizeBytes)
    }

    /** الحجم السالب أثر عطب قراءة: يُردّ إلى المجهول ولا يُجمع في المجموع. */
    @Test
    fun `a negative size is unknown and is not summed`() {
        val target = FileDeleteTargets.of(listOf(file("a", -4), file("b", 500)))
        assertEquals(500L, target.knownBytes)
        assertEquals(1, target.unknownCount)
        assertFalse(target.sizeKnown)
    }
}
