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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نموذج النوافذ — يُقاس في JVM عادي.
 *
 * والأهمّ فيه ثلاثة: أن الرجوع يعود إلى **المجلد الذي أتى منه المستخدم** لا إلى الأب،
 * وأن التنقّل إلى المجلد نفسه لا يُكدّس سجلًا (زرّ رجوع بلا أثر عطب يُرى)، وأن التبديل
 * يتبادل **كل** حالة النافذة لا مسارها وحده.
 */
class FileWindowModelTest {

    // ────────────────────────────────────────────────────────────────────────
    // التنقّل والسجل
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun navigatingPushesThePreviousFolderOntoTheHistory() {
        val window = FileWindowState(path = "/a").at("/a/b").at("/a/b/c")
        assertEquals("/a/b/c", window.path)
        assertEquals(listOf("/a", "/a/b"), window.history)
        assertTrue(window.canGoBack)
    }

    /** المجلد نفسه ليس انتقالًا: لا سجل ولا تغيير — وإلا بدا زرّ الرجوع معطّلًا بلا سبب. */
    @Test
    fun navigatingIntoTheSameFolderIsNotAnEntry() {
        val window = FileWindowState(path = "/a/b").at("/a/b/").at("/a//b")
        assertEquals("/a/b", window.path)
        assertTrue(window.history.isEmpty())
        assertFalse(window.canGoBack)
    }

    /** الرجوع خطوة في التاريخ لا صعودًا للأب: يقفز من `/x/y` إلى `/a`. */
    @Test
    fun backReturnsToTheFolderTheUserCameFrom() {
        val window = FileWindowState(path = "/a").at("/x/y").back()
        assertEquals("/a", window.path)
        assertTrue(window.history.isEmpty())
    }

    @Test
    fun backOnAnEmptyHistoryChangesNothing() {
        val window = FileWindowState(path = "/a")
        assertEquals(window, window.back())
    }

    // ────────────────────────────────────────────────────────────────────────
    // التسمية والرئيسي والمفاتيح
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun theLabelIsTheUserNameOrTheFolderName() {
        assertEquals("Download", FileWindowState(path = "/sdcard/Download").label)
        assertEquals("النظام", FileWindowState(path = "/system", title = "النظام").label)
        // تسمية فارغة ليست تسمية: تعود إلى اسم المجلد بدل تبويب بلا عنوان.
        assertEquals("system", FileWindowState(path = "/system", title = "   ").label)
    }

    @Test
    fun theHomeFolderIsExplicitOrTheDeclaredStart() {
        assertEquals(FileWindows.START_PATH, FileWindowState(path = "/system").homePath)
        assertEquals("/system", FileWindowState(path = "/system").setHome().homePath)
    }

    @Test
    fun theHiddenToggleFlipsAndSortIsKeptPerWindow() {
        val window = FileWindowState(path = "/a")
            .toggleHidden()
            .withSort(FileSort(key = FileSortKey.Size, ascending = false))
        assertTrue(window.showHidden)
        assertEquals(FileSortKey.Size, window.sort.key)
        assertFalse(window.sort.ascending)
    }

    // ────────────────────────────────────────────────────────────────────────
    // النافذتان معًا
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun bothWindowsStartAtTheDeclaredFolder() {
        val windows = FileWindowsState()
        assertEquals(FileWindows.START_PATH, windows.first.path)
        assertEquals(FileWindows.START_PATH, windows.second.path)
        assertEquals(WindowSide.First, windows.active)
    }

    /** التنقّل يجعل النافذة المنقولة نشطة: العمل يقع حيث ينظر المستخدم. */
    @Test
    fun navigatingInsideAWindowActivatesIt() {
        val windows = FileWindowsState().navigate(WindowSide.Second, "/data")
        assertEquals(WindowSide.Second, windows.active)
        assertEquals("/data", windows.second.path)
        assertEquals(FileWindows.START_PATH, windows.first.path)
    }

    /** «افتح في النافذة الأخرى» يُنقلها ويُعلنها نشطة، والنافذة الأولى تبقى مكانها. */
    @Test
    fun openingInTheOtherWindowMovesAndActivatesIt() {
        val windows = FileWindowsState().openInOther(WindowSide.First, "/system/lib64")
        assertEquals(WindowSide.Second, windows.active)
        assertEquals("/system/lib64", windows.second.path)
        assertEquals(FileWindows.START_PATH, windows.first.path)
    }

    /**
     * التبديل يتبادل **كل** الحالة: المسار والسجل والتسمية والإخفاء.
     * وتبديل المسارين وحدهما كان سيترك سجلًا في نافذة لا يخصّها.
     */
    @Test
    fun swappingExchangesEverythingNotJustThePaths() {
        val windows = FileWindowsState()
            .with(WindowSide.First, FileWindowState(path = "/a", title = "أ").toggleHidden())
            .with(WindowSide.Second, FileWindowState(path = "/b").at("/c"))
            .swap()

        assertEquals("/c", windows.first.path)
        assertEquals("/a", windows.second.path)
        // السجل ينتقل **مع** النافذة: وإلا عاد زرّ الرجوع إلى مجلد لم يزره المستخدم فيها.
        assertEquals("/b", windows.first.history.last())
        assertEquals("أ", windows.second.title)
        assertTrue(windows.second.showHidden)
    }

    @Test
    fun syncingOpensTheOtherWindowOnTheActiveFolder() {
        val windows = FileWindowsState()
            .navigate(WindowSide.First, "/data/adb/modules")
            .syncOther()
        assertEquals("/data/adb/modules", windows.second.path)
    }

    @Test
    fun goingHomeUsesTheWindowOwnHome() {
        val windows = FileWindowsState()
            .with(WindowSide.Second, FileWindowState(path = "/x").setHome("/sdcard"))
            .navigate(WindowSide.Second, "/y")
            .goHome(WindowSide.Second)
        assertEquals("/sdcard", windows.second.path)
    }

    // ────────────────────────────────────────────────────────────────────────
    // قاعدة العرض
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun aNarrowScreenKeepsOneListAndAWideOneShowsBoth() {
        assertFalse(FileWindowsRule.sideBySide(360f))
        assertTrue(FileWindowsRule.sideBySide(600f))
        assertTrue(FileWindowsRule.sideBySide(1280f))
    }

    // ────────────────────────────────────────────────────────────────────────
    // الحفظ والاستعادة
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun theSavedStateSurvivesARoundTrip() {
        val state = FileWindowsState()
            .with(WindowSide.First, FileWindowState(path = "/system", title = "النظام").toggleHidden())
            .with(
                WindowSide.Second,
                FileWindowState(path = "/sdcard/Download").withSort(FileSort(FileSortKey.Size, false, false)),
            )
            .navigate(WindowSide.Second, "/sdcard/Download/apk")
        val restored = FileWindowsCodec.decode(FileWindowsCodec.encode(state))

        assertEquals(state.first.path, restored.first.path)
        assertEquals(state.first.title, restored.first.title)
        assertTrue(restored.first.showHidden)
        assertEquals(FileSortKey.Size, restored.second.sort.key)
        assertFalse(restored.second.sort.ascending)
        assertEquals(listOf("/sdcard/Download"), restored.second.history)
        assertEquals(WindowSide.Second, restored.active)
    }

    /** حالة محفوظة من نسخة أقدم أو تالفة تعود إلى البداية المعلنة — ولا تُسقط الشاشة. */
    @Test
    fun aCorruptedSavedStateFallsBackToTheDeclaredStart() {
        val restored = FileWindowsCodec.decode(listOf("nonsense"))
        assertEquals(FileWindows.START_PATH, restored.first.path)
        assertEquals(FileWindows.START_PATH, restored.second.path)
        assertEquals(WindowSide.First, restored.active)
    }

    /** الفواصل لا تتسرّب: مسار غريب لا يُقسَّم إلى نافذتين. */
    @Test
    fun separatorsInsideAValueDoNotSplitTheState() {
        val state = FileWindowsState().with(
            WindowSide.First,
            FileWindowState(path = "/a\u001Fb"),
        )
        val restored = FileWindowsCodec.decode(FileWindowsCodec.encode(state))
        assertNotEquals(FileWindows.START_PATH, restored.first.path)
        assertEquals(state.second.path, restored.second.path)
    }
}
