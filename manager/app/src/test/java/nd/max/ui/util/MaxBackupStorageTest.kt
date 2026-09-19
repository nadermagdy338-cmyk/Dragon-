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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مكان الأرشيف — القرار الذي إن أخطأ ضاعت نسخ المستخدم في مجلد لا يُقرأ منه.
 *
 * وهذا هو الاختبار الذي يمكن تشغيله بلا جهاز وبلا Android: `MaxBackupStorage` خالص
 * عن قصد، ولهذا يمكن قياس قراره هنا مباشرة لا ترجيحه من قراءة الشيفرة.
 */
class MaxBackupStorageTest {

    /**
     * المسار المطلوب مكتوبًا بحروفه.
     *
     * يُثبَّت نصًّا لأن هذا **طلب صريح من المستخدم** لا تفصيل تنفيذي: مجلد باسم التطبيق
     * في جذر التخزين الداخلي، وداخله مجلد الأرشيف. وأي تغيير هنا يجب أن يكون قرارًا
     * مكتوبًا لا أثرًا جانبيًا لتمرير معامل.
     */
    @Test
    fun theArchiveLivesWhereTheUserAskedFor() {
        assertEquals("/storage/emulated/0/MaxManger/MaxBackup", MaxBackupStorage.requestedRoot().path)
    }

    /** الاسم في المسارين واحد، فلا يجد المستخدم مجلدين باسمين مختلفين. */
    @Test
    fun bothLocationsUseTheSameFolderName() {
        val fallback = MaxBackupStorage.fallbackRoot(File("/storage/emulated/0/Android/data/nd.max/files"))
        assertEquals(MaxBackupStorage.FOLDER, fallback.name)
        assertEquals(MaxBackupStorage.FOLDER, MaxBackupStorage.requestedRoot().name)
    }

    /** المسار العام إن كان قابلًا للكتابة فعلًا. */
    @Test
    fun aWritableRequestedRootIsTheOneUsed() {
        val requested = File("/storage/emulated/0/MaxManger/MaxBackup")
        val fallback = File("/storage/emulated/0/Android/data/nd.max/files/MaxBackup")
        assertEquals(requested, MaxBackupStorage.choose(requested, fallback, requestedUsable = true))
    }

    /**
     * وإن لم يكن: الاحتياطي لا الفشل.
     *
     * وهذا هو الفرق بين «صلاحية ناقصة» و«وظيفة معطّلة»: أندرويد ١١+ يمنع الكتابة في
     * جذر التخزين العام بلا «الوصول لكل الملفات»، والنسخ يجب أن تنجح مع ذلك.
     */
    @Test
    fun anUnwritableRequestedRootFallsBackInsteadOfFailing() {
        val requested = File("/storage/emulated/0/MaxManger/MaxBackup")
        val fallback = File("/storage/emulated/0/Android/data/nd.max/files/MaxBackup")
        assertEquals(fallback, MaxBackupStorage.choose(requested, fallback, requestedUsable = false))
    }

    /**
     * والجذران معًا عند السرد، **لا الاحتياطي عند غياب العام فقط**.
     *
     * والسبب عملي: مستخدم أخذ نسخًا في مجلد التطبيق ثم منح الصلاحية اليوم، فلو سُرِد
     * العام وحده لظهرت نسخه كأنها فُقدت — وهي باقية على القرص. ونسخٌ موجودة تُعرض
     * كغير موجودة أسوأ من ألّا تُقرأ.
     */
    @Test
    fun bothRootsAreSearchedSoEarlierCopiesNeverDisappear() {
        val requested = File("/storage/emulated/0/MaxManger/MaxBackup")
        val fallback = File("/storage/emulated/0/Android/data/nd.max/files/MaxBackup")
        val roots = MaxBackupStorage.searchRoots(requested, fallback)
        assertEquals(listOf(requested, fallback), roots)
    }

    /** ولا يُقرأ الجذر نفسه مرتين حين يكون الجذران واحدًا (المسار العام نفسه اختير احتياطيًّا). */
    @Test
    fun aRootIsNeverSearchedTwice() {
        val same = File("/storage/emulated/0/MaxManger/MaxBackup")
        val roots = MaxBackupStorage.searchRoots(same, File(same.absolutePath))
        assertEquals(1, roots.size)
    }

    /** مجلد التطبيق داخل الجذر، بلا بادئات ولا لاحقات مخترعة. */
    @Test
    fun anAppFolderIsTheRootPlusThePackageName() {
        val root = MaxBackupStorage.requestedRoot()
        assertEquals(
            "${root.path}/com.example.app",
            MaxBackupStorage.folderOf(root, "com.example.app").path,
        )
    }

    /** تمييز المسار العام قرارٌ بالمسار وحده: لا حالة قرص ولا صلاحية تُسأل هنا. */
    @Test
    fun onlyTheExactRequestedPathCountsAsTheSharedFolder() {
        assertTrue(MaxBackupStorage.isRequested(MaxBackupStorage.requestedRoot()))
        assertFalse(MaxBackupStorage.isRequested(File("/storage/emulated/0/Android/data/nd.max/files/MaxBackup")))
        assertFalse(MaxBackupStorage.isRequested(File(MaxBackupStorage.PUBLIC_PARENT)))
    }

    /**
     * ملف الفحص لا يُخلط بأرشيف.
     *
     * اسمه مخفي ومبدوء بنقطة كي لا يُرى في مدير ملفات المستخدم، ولا يُحصى نسخةً.
     */
    @Test
    fun theWriteProbeIsHiddenAndNotAPackageName() {
        assertTrue(MaxBackupStorage.PROBE_FILE.startsWith("."))
        assertFalse(MaxBackupStorage.PROBE_FILE.contains("/"))
    }
}
