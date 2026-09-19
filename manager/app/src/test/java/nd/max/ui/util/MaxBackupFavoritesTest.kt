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
 * `OCR-04` بلا Android. والقاعدتان المقاسَتان هنا ليستا التبديل بل ما حوله:
 *
 *  - **الترتيب**: المفضّلة تُقدَّم، والبقية تبقى بترتيبها — فرز كامل كان سيمحو الترتيب الأبجدي.
 *  - **الملف**: سطر مشوّه لا يُطابق تطبيقًا، فلا يصير صفًّا لا معنى له في أطول قائمة في التطبيق.
 */
class MaxBackupFavoritesTest {

    @Test
    fun `only a real package name is kept`() {
        assertTrue(MaxBackupFavorites.isPackageName("com.example.app"))
        assertTrue(MaxBackupFavorites.isPackageName("nd.max"))
        assertTrue(MaxBackupFavorites.isPackageName("com.example.app_2"))
        assertFalse(MaxBackupFavorites.isPackageName(""))
        assertFalse(MaxBackupFavorites.isPackageName("nodots"))
        assertFalse(MaxBackupFavorites.isPackageName(".leading"))
        assertFalse(MaxBackupFavorites.isPackageName("com..double"))
        assertFalse(MaxBackupFavorites.isPackageName("com.example.app/../evil"))
        assertFalse(MaxBackupFavorites.isPackageName("2com.example"))
    }

    @Test
    fun `sanitize drops what cannot be an app and keeps the rest sorted`() {
        val cleaned = MaxBackupFavorites.sanitize(
            listOf("nd.max", "  com.example.app  ", "nd.max", "", "not a package", "/etc/passwd")
        )
        assertEquals(listOf("com.example.app", "nd.max"), cleaned)
    }

    @Test
    fun `toggling twice returns to the same list, and the file stays sorted`() {
        val once = MaxBackupFavorites.toggle(emptyList(), "nd.max")
        assertTrue(MaxBackupFavorites.isFavorite(once, "nd.max"))
        assertEquals(emptyList<String>(), MaxBackupFavorites.toggle(once, "nd.max"))

        // الترتيب في الملف لا يتبع ترتيب الوسم: وسم `z` ثم `a` يُنتج `a` ثم `z`.
        val both = MaxBackupFavorites.toggle(MaxBackupFavorites.toggle(emptyList(), "z.app"), "a.app")
        assertEquals(listOf("a.app", "z.app"), both)
    }

    @Test
    fun `a favourite is moved to the front and everyone else keeps their order`() {
        val apps = listOf("a.app", "b.app", "c.app", "d.app")
        val ordered = MaxBackupFavorites.ordered(apps, key = { it }, favorites = listOf("c.app"))
        assertEquals(listOf("c.app", "a.app", "b.app", "d.app"), ordered)

        // وبلا مفضّلة: القائمة كما جاءت، بلا فرز يعيد ترتيبها من تلقاء نفسه.
        assertEquals(apps, MaxBackupFavorites.ordered(apps, key = { it }, favorites = emptyList()))
    }

    @Test
    fun `marking one app never hides another`() {
        val apps = listOf("a.app", "b.app", "c.app")
        val ordered = MaxBackupFavorites.ordered(apps, key = { it }, favorites = listOf("b.app"))
        assertEquals(apps.size, ordered.size)
        assertTrue(ordered.containsAll(apps))
    }

    @Test
    fun `the list survives a round trip, and a damaged line costs only that line`() {
        val favorites = listOf("com.example.app", "nd.max")
        assertEquals(favorites, MaxBackupFavorites.decode(MaxBackupFavorites.encode(favorites)))

        val damaged = "com.example.app\nسطر ليس اسم حزمة\nnd.max\n"
        assertEquals(favorites, MaxBackupFavorites.decode(damaged))
        assertEquals(emptyList<String>(), MaxBackupFavorites.decode(""))
    }

    @Test
    fun `an absurdly long file is capped instead of slowing every read`() {
        val many = (1..(MaxBackupFavorites.MAX + 50)).map { "com.example.app$it" }
        assertEquals(MaxBackupFavorites.MAX, MaxBackupFavorites.sanitize(many).size)
    }
}
