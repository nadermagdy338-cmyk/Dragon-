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

package nd.max.ui.subscreens

import androidx.compose.ui.geometry.Offset
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileKind
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileWindowState
import nd.max.ui.util.WindowSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * انتقالات التحديد — تُقاس على [WindowView] حقيقي لا على مثال ملفّق.
 *
 * وأهمّ ما يُقاس: **النمط لا يبقى بلا تحديد**، وأن «حدّد الكل» و«عكس» يقعان على **الظاهر**
 * لا على ما قُرئ — وهو ما وصفه المالك بالغباء حين بقي خيار التحديد معروضًا بلا شيء محدَّد.
 */
class FileManagerSelectionTest {

    private val now = 1_000L

    private fun file(name: String) = FileEntry(
        name = name,
        path = "/root/$name",
        kind = FileKind.RegularFile,
        sizeBytes = 10L,
        modifiedEpochSec = 1L,
    )

    private fun view(vararg names: String) = WindowView(
        listing = DirectoryListing.Entries("/root", names.map { file(it) }),
    )

    private fun window() = FileWindowState(path = "/root")

    /**
     * سحب كما يقع في الشاشة: **الترتيب المعروض يُمرَّر مع المدخل** — والنمط هنا هو ترتيب
     * القائمة كما قُرئت، لأن هذه النوافذ في الاختبار بلا مرشّح ولا بحث.
     */
    private fun WindowView.swiped(name: String): WindowView =
        swipedSelection(file(name), entries.map { it.path })

    // ────────────────────────────────────────────────────────────────────────
    // العكس إلى الصفر = خروج من النمط
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggling on selects and enters selection mode`() {
        val next = view("a", "b").toggledSelection(file("a"))
        assertTrue(next.selecting)
        assertEquals(setOf("/root/a"), next.selection.paths)
    }

    @Test
    fun `toggling the last one off leaves selection mode`() {
        val next = view("a").toggledSelection(file("a")).toggledSelection(file("a"))
        assertFalse("نمط التحديد يُرفع مع آخر عنصر", next.selecting)
        assertTrue(next.selection.isEmpty)
    }

    @Test
    fun `toggling one of two off keeps selection mode`() {
        val next = view("a", "b")
            .toggledSelection(file("a"))
            .toggledSelection(file("b"))
            .toggledSelection(file("a"))
        assertTrue(next.selecting)
        assertEquals(setOf("/root/b"), next.selection.paths)
    }

    // ────────────────────────────────────────────────────────────────────────
    // السحب الجانبي يحدّد ولا يعكس
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a swipe adds to the selection and never removes`() {
        val once = view("a", "b").swiped("a")
        assertEquals(setOf("/root/a"), once.selection.paths)

        val twice = once.swiped("a")
        assertEquals("السحب على صفّ محدَّد لا ينزعه", setOf("/root/a"), twice.selection.paths)
        assertTrue(twice.selecting)

        val both = twice.swiped("b")
        assertEquals(setOf("/root/a", "/root/b"), both.selection.paths)
    }

    @Test
    fun `clearing removes the selection and the mode together`() {
        val next = view("a").swiped("a").clearedSelection()
        assertFalse(next.selecting)
        assertTrue(next.selection.isEmpty)
        assertNull("وخروجٌ بلا مرساة متروكة", next.swipeAnchor)
    }

    // ────────────────────────────────────────────────────────────────────────
    // النطاق (من–إلى) — UX-06 ②: كان غائبًا، وصار السحب الثاني وراءه مدًى
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the first swipe takes one entry and becomes the range anchor`() {
        val next = view("a", "b", "c").swiped("b")
        assertEquals(setOf("/root/b"), next.selection.paths)
        assertEquals("/root/b", next.swipeAnchor)
    }

    @Test
    fun `a second swipe covers everything between the anchor and itself`() {
        val next = view("a", "b", "c", "d").swiped("a").swiped("d")
        assertEquals(setOf("/root/a", "/root/b", "/root/c", "/root/d"), next.selection.paths)
        assertEquals("المرساة لا تتحرّك بالمدّ", "/root/a", next.swipeAnchor)
    }

    @Test
    fun `the range works upwards as it works downwards`() {
        val next = view("a", "b", "c").swiped("c").swiped("a")
        assertEquals(setOf("/root/a", "/root/b", "/root/c"), next.selection.paths)
    }

    /** **يُوسَّع ولا يُستبدل**: ما اختاره المستخدم بيده لا يُهدَم بسحب واحد. */
    @Test
    fun `the range adds to what the user already picked`() {
        val next = view("a", "b", "c", "d").toggledSelection(file("d")).swiped("b")
        assertEquals(setOf("/root/b", "/root/c", "/root/d"), next.selection.paths)
    }

    @Test
    fun `a tap moves the anchor to the tapped entry`() {
        val next = view("a", "b", "c")
            .swiped("a")
            .toggledSelection(file("c"))
            .swiped("a")
        assertEquals(setOf("/root/a", "/root/b", "/root/c"), next.selection.paths)
        assertEquals("/root/c", next.swipeAnchor)
    }

    /**
     * مرساة غابت عن العرض (مُرشَّح أخفاها): يُبدأ تحديد جديد — ولا يُمتدّ نطاق على صفوف لا
     * يراها المستخدم.
     */
    @Test
    fun `an anchor that is no longer shown starts a new selection instead of faking a range`() {
        val start = view("a", "b", "c").swiped("a")
        val next = start.copy(selecting = true)
            .swipedSelection(file("c"), listOf("/root/b", "/root/c"))
        assertEquals(setOf("/root/a", "/root/c"), next.selection.paths)
        assertEquals("/root/c", next.swipeAnchor)
    }

    @Test
    fun `leaving selection mode drops the anchor so the next swipe starts fresh`() {
        val fresh = view("a", "b", "c").swiped("a").clearedSelection()
        assertEquals(setOf("/root/c"), fresh.swiped("c").selection.paths)
    }

    @Test
    fun `a long press that creates the selection makes itself the anchor`() {
        val next = view("a", "b", "c").longPressedSelection(file("c"), onSelection = false)
        assertEquals(setOf("/root/c"), next.selection.paths)
        assertEquals("/root/c", next.swipeAnchor)
        assertTrue(next.selecting)
    }

    /**
     * ضغط طويل على صفّ **محدَّد أصلًا** لا يُزحزح ما بناه المستخدم: لا تحديده ولا مرساته
     * (وسببُ التمييز مكتوب في [selectionToClearAfterMenu] نفسه).
     */
    @Test
    fun `a long press on an already selected row keeps the selection and the anchor`() {
        val built = view("a", "b", "c").swiped("a").swiped("b")
        val next = built.longPressedSelection(file("a"), onSelection = true)
        assertEquals(setOf("/root/a", "/root/b"), next.selection.paths)
        assertEquals("/root/a", next.swipeAnchor)
    }

    /** القفزة إلى مدخل (من نتائج البحث) تحديدٌ واحد صريح — والمرساة عليه لا على مرساة قديمة. */
    @Test
    fun `a jump selects that entry alone and anchors on it`() {
        val next = view("a", "b", "c").swiped("a").singleSelection("/root/c")
        assertEquals(setOf("/root/c"), next.selection.paths)
        assertEquals("/root/c", next.swipeAnchor)
    }

    /** وسحبٌ بعد القفزة يمتدّ من **المدخل المقصود** لا من مرساة تركها تحديدٌ سابق. */
    @Test
    fun `the swipe after a jump ranges from the jumped entry`() {
        val next = view("a", "b", "c", "d").swiped("a").singleSelection("/root/d").swiped("c")
        assertEquals(setOf("/root/c", "/root/d"), next.selection.paths)
    }

    @Test
    fun `a bulk act leaves no anchor behind`() {
        val inverted = view("a", "b", "c")
            .copy(selection = FileSelection(setOf("/root/a", "/root/b", "/root/c")))
            .invertedSelection(window(), now)
        assertNull("الفعل الجماعيّ لا مدخلَ واحدًا يُنسب إليه", inverted.swipeAnchor)
        assertEquals(setOf("/root/a"), inverted.swiped("a").selection.paths)
    }

    // ────────────────────────────────────────────────────────────────────────
    // حدّد الكل · اعكس · إظهار المخفي
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `select all takes only what is visible`() {
        val filtered = view("a.txt", "b.bin", "c.txt").copy(query = ".txt")
        val next = filtered.allSelected(window(), now)
        assertEquals("المخفي بالبحث لا يُحدَّد", setOf("/root/a.txt", "/root/c.txt"), next.selection.paths)
        assertTrue(next.selecting)
    }

    /**
     * عطب مُبلَّغ عنه في نظيرنا (‏`AmazeFileManager` issue #953): «تحديد الكل» مع كل شيء محدَّد كان
     * **يقلع** التحديد. وعندنا لا يقلع لأن الدالّة تُبني من الظاهر ولا تُبدّل — وهذا حرسٌ على ألّا
     * يصير تبديلًا لاحقًا.
     */
    @Test
    fun `select all twice keeps everything selected`() {
        val once = view("a", "b").allSelected(window(), now)
        val twice = once.allSelected(window(), now)
        assertEquals(setOf("/root/a", "/root/b"), twice.selection.paths)
        assertTrue(twice.selecting)
    }

    @Test
    fun `invert flips the selection over what is visible`() {
        val start = view("a", "b", "c")
            .copy(selection = FileSelection(setOf("/root/a")))
            .invertedSelection(window(), now)
        assertEquals(setOf("/root/b", "/root/c"), start.selection.paths)
        assertTrue(start.selecting)
    }

    @Test
    fun `toggling hidden entries drops the selection that is no longer shown`() {
        val withHidden = view(".secret", "shown")
            .copy(selecting = true, selection = FileSelection(setOf("/root/.secret", "/root/shown")))

        // الإخفاء صار مطفأً (showHidden = false)، فالمخفي يخرج من الظاهر ويُقصّ من التحديد.
        val next = withHidden.afterHiddenToggle(window(), now)
        assertEquals(setOf("/root/shown"), next.selection.paths)
        assertFalse("النمط يُرفع عند تبديل الإخفاء", next.selecting)
    }

    // ────────────────────────────────────────────────────────────────────────
    // إغلاق القائمة بلا أمر
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a closed menu clears the selection its long press created`() {
        assertEquals(
            WindowSide.Second,
            selectionToClearAfterMenu(null, WindowSide.Second),
        )
    }

    @Test
    fun `an open menu clears nothing`() {
        assertNull(selectionToClearAfterMenu(Offset(1f, 2f), WindowSide.First))
    }

    @Test
    fun `a selection the user already had is never cleared`() {
        assertNull("الضغط الطويل على محدَّد أصلًا لا يُسجَّل", selectionToClearAfterMenu(null, null))
    }
}
