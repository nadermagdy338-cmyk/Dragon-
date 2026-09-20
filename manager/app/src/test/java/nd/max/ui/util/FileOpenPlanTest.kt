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
 * قرار الفتح — يُقاس لأنه يحدّد ما يراه المستخدم بعد النقرة: دخول مجلد، أو محرّر نصّ،
 * أو تسليم لتطبيق آخر. والتخمين هنا («كل ما ليس صورةً نصٌّ») يفتح محرّرًا على ملف
 * ثنائي فيعرض رمزًا لا معنى له.
 */
class FileOpenPlanTest {

    private fun file(name: String, size: Long? = null) = FileEntry(
        name = name,
        path = "/sdcard/$name",
        kind = FileKind.RegularFile,
        sizeBytes = size,
    )

    private fun folder(name: String) = FileEntry(
        name = name,
        path = "/sdcard/$name",
        kind = FileKind.Directory,
    )

    @Test
    fun aFolderIsEntered() {
        assertEquals(FileOpenRoute.EnterFolder, FileOpenPlan.routeOf(folder("Download")))
    }

    @Test
    fun aTextFileGoesToTheEditorAndAnythingElseGoesOut() {
        assertEquals(FileOpenRoute.TextEditor, FileOpenPlan.routeOf(file("build.prop")))
        assertEquals(FileOpenRoute.TextEditor, FileOpenPlan.routeOf(file("notes.md")))
        assertEquals(FileOpenRoute.TextEditor, FileOpenPlan.routeOf(file("init.rc")))
        assertEquals(FileOpenRoute.External, FileOpenPlan.routeOf(file("photo.jpg")))
        assertEquals(FileOpenRoute.External, FileOpenRoute.External.takeIf { FileOpenPlan.routeOf(file("app.apk")) == it }!!)
        assertEquals(FileOpenRoute.External, FileOpenPlan.routeOf(file("build.gradle.kts")))
        assertEquals(FileOpenRoute.External, FileOpenPlan.routeOf(file("noext")))
    }

    /** ملف نصّي ضخم يُسلَّم لتطبيق خارجي: محرّر يبتلع ميغابايتات ليس خدمة. */
    @Test
    fun aHugeTextFileIsHandedToAnotherApp() {
        assertFalse(FileOpenPlan.isTextLike(file("dump.txt", size = FileOpenPlan.MAX_EDITOR_BYTES + 1)))
        assertEquals(FileOpenRoute.External, FileOpenPlan.routeOf(file("dump.txt", size = 9_000_000)))
        assertTrue(FileOpenPlan.isTextLike(file("dump.txt", size = FileOpenPlan.MAX_EDITOR_BYTES)))
    }

    /** حجم **لم يُقرأ** لا يمنع: المنع بناءً على `null` يحجب ملفًا سليمًا لصفّة سقطت. */
    @Test
    fun anUnreadSizeDoesNotBlockTheEditor() {
        assertEquals(FileOpenRoute.TextEditor, FileOpenPlan.routeOf(file("settings.conf", size = null)))
    }

    @Test
    fun theExtensionIsReadFromTheLastDotOnly() {
        assertEquals("txt", FileOpenPlan.extensionOf("archive.tar.txt"))
        assertEquals("gz", FileOpenPlan.extensionOf("archive.tar.gz"))
        assertEquals("", FileOpenPlan.extensionOf("README"))
        // نقطة في الآخر ليست امتدادًا، ونقطة في الأول تعني ملفًّا مخفيًّا بلا امتداد.
        assertEquals("", FileOpenPlan.extensionOf("notes."))
        assertEquals("", FileOpenPlan.extensionOf(".bashrc"))
    }

    /** مجلد اسمه ينتهي بامتداد نصّي يبقى مجلدًا — النوع من الجهاز لا من الاسم. */
    @Test
    fun aFolderNamedLikeATextFileIsStillAFolder() {
        assertEquals(FileOpenRoute.EnterFolder, FileOpenPlan.routeOf(folder("backup.txt")))
    }
}
