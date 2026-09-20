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
 * الحافظة — أخطر ما فيها تحويلها إلى أمر: «قصّ» يجب أن ينتهي بطلب **نقل** لا نسخ،
 * وإلا بقي الملف في مكانين. والباقي قياسات صغيرة تُمنع بها أوهام الواجهة
 * (شريط لحافظة فارغة، أو زرّ لصق في المكان نفسه).
 */
class FileClipboardModelTest {

    @Test
    fun copyKeepsItsContentAfterPasting() {
        val clipboard = FileClipboardRules.of(ClipboardMode.Copy, listOf("/a/file.txt"), "/a")
        val pasted = clipboard?.afterPaste()
        assertEquals(1, pasted?.count)
        assertEquals(ClipboardMode.Copy, pasted?.mode)
    }

    @Test
    fun cuttingClearsTheClipboardAfterPasting() {
        val clipboard = FileClipboardRules.of(ClipboardMode.Cut, listOf("/a/file.txt"), "/a")
        assertNull(clipboard?.afterPaste())
    }

    /** حافظة بلا عناصر ليست حافظة: `null` تُخفي الشريط كله بدل «٠ عنصر». */
    @Test
    fun anEmptySelectionProducesNoClipboard() {
        assertNull(FileClipboardRules.of(ClipboardMode.Copy, emptyList(), "/a"))
    }

    /** التنقية: تكرار ومسارات غير مطبَّعة لا تصل إلى الأمر. */
    @Test
    fun theClipboardIsNormalizedAndDeduplicated() {
        val clipboard = FileClipboardRules.of(
            ClipboardMode.Copy,
            listOf("/a//b/", "/a/b", "/a/c"),
            "/a/",
        )
        assertEquals(listOf("/a/b", "/a/c"), clipboard?.sources)
        assertEquals("/a", clipboard?.origin)
    }

    /** «قصّ» يصير نقلًا و«نسخ» يبقى نسخًا — هذه هي الحافظة كلها. */
    @Test
    fun pasteBecomesAMoveForCutAndACopyForCopy() {
        val cut = FileClipboardRules.of(ClipboardMode.Cut, listOf("/a/x"), "/a")!!
        val copy = FileClipboardRules.of(ClipboardMode.Copy, listOf("/a/x"), "/a")!!

        assertEquals(FileOperation.Move, FileClipboardRules.pasteRequest(cut, "/b").operation)
        assertEquals(FileOperation.Copy, FileClipboardRules.pasteRequest(copy, "/b").operation)
        assertEquals("/b", FileClipboardRules.pasteRequest(cut, "/b/").destination)
    }

    @Test
    fun cuttingIntoTheSameFolderIsPointlessAndCopyingIsNot() {
        val cut = FileClipboardRules.of(ClipboardMode.Cut, listOf("/a/x", "/a/y"), "/a")!!
        val copy = FileClipboardRules.of(ClipboardMode.Copy, listOf("/a/x"), "/a")!!

        assertTrue(FileClipboardRules.pointlessHere(cut, "/a"))
        assertFalse(FileClipboardRules.pointlessHere(cut, "/b"))
        // نسخ ملف داخل مجلده ليس عبثًا: نسخة ثانية بالاسم نفسه بعد إعادة تسمية.
        assertFalse(FileClipboardRules.pointlessHere(copy, "/a"))
    }

    /** قصّ عناصر من مجلدات مختلفة: اللصق في أحدهما ليس عبثًا — بعضها سيُنقل فعلًا. */
    @Test
    fun cuttingMixedOriginsIsNeverPointless() {
        val cut = FileClipboardRules.of(ClipboardMode.Cut, listOf("/a/x", "/b/y"), "/a")!!
        assertFalse(FileClipboardRules.pointlessHere(cut, "/a"))
        assertFalse(FileClipboardRules.pointlessHere(cut, "/b"))
    }

    @Test
    fun theSummaryReportsTheCountAndTheOrigin() {
        val clipboard = FileClipboardRules.of(ClipboardMode.Copy, listOf("/a/x", "/a/y"), "/a")!!
        assertEquals(2 to "/a", FileClipboardRules.summary(clipboard))
    }
}
