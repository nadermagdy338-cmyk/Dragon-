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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حالة القراءة/الكتابة — تُقاس على **أسطر جدول حقيقية الشكل** لأن قرارًا خطِرًا يُبنى
 * عليها: ما إذا كان تعديل `/system` سيُقبل أو يُرفض. وأخطر ما يُقاس هنا **اختيار أطول
 * مطابقة**: `/` معلَّق `rw` و`/system` معلَّق `ro`، والاختيار الخاطئ يقول للمستخدم
 * «يمكن الكتابة» ثم يفشل أمره.
 */
class RootMountTest {

    private val sample = listOf(
        "/dev/block/dm-0 / ext4 rw,seclabel,relatime 0 0",
        "/dev/block/dm-1 /system ext4 ro,seclabel,relatime 0 0",
        "/dev/block/dm-2 /data ext4 rw,seclabel,nosuid,nodev 0 0",
        "tmpfs /storage/emulated/0/My\\040Files tmpfs rw,nosuid,nodev 0 0",
        "proc /proc proc ro,relatime 0 0",
        "this line has no fields",
    )

    private val entries = RootMount.parse(sample)

    @Test
    fun everyWellFormedLineBecomesAnEntryAndBrokenLinesAreIgnored() {
        assertEquals(5, entries.size)
        assertTrue(entries.none { it.point.contains("no fields") })
    }

    /** المسافة المكتوبة `\\040` في الجدول تُفكّ: وإلا انقسم السطر إلى حقول أكثر. */
    @Test
    fun kernelEscapesAreDecoded() {
        assertEquals("My Files", RootMount.decode("My\\040Files"))
        val point = entries.map { it.point }
        assertTrue(point.contains("/storage/emulated/0/My Files"))
    }

    @Test
    fun theLongestMatchingMountPointWins() {
        assertEquals("/system", RootMount.entryFor(entries, "/system/bin/app_process")?.point)
        assertEquals("/data", RootMount.entryFor(entries, "/data/adb/modules")?.point)
        assertEquals("/", RootMount.entryFor(entries, "/sdcard")?.point)
    }

    @Test
    fun accessFollowsTheOptionsOfTheWinningEntry() {
        assertEquals(MountAccess.ReadOnly, RootMount.accessFor(entries, "/system/bin"))
        assertEquals(MountAccess.ReadWrite, RootMount.accessFor(entries, "/data/local/tmp"))
        assertEquals(MountAccess.ReadWrite, RootMount.accessFor(entries, "/sdcard"))
        assertEquals(MountAccess.ReadOnly, RootMount.accessFor(entries, "/proc/1"))
    }

    /** مسار لا تعليق له: «غير معروفة» لا «قراءة فقط» ولا «كتابة» — ولا يُخترع حكم. */
    @Test
    fun aPathWithoutAMountIsUnknown() {
        val only = listOf(MountEntry("/system", listOf("ro")))
        assertEquals(MountAccess.Unknown, RootMount.accessFor(only, "/data/x"))
        assertNull(RootMount.entryFor(emptyList(), "/data/x"))
    }

    /** خيارات بلا `ro` ولا `rw` (تعليق غريب): تُعلن غير معروفة بدل أن تُخمَّن قراءةً. */
    @Test
    fun optionsWithoutRoOrRwAreDeclaredUnknown() {
        assertEquals(MountAccess.Unknown, MountEntry("/x", listOf("nosuid", "nodev")).access)
        assertEquals(MountAccess.Unknown, MountEntry("/x", emptyList()).access)
    }

    @Test
    fun theRemountCommandNamesTheTargetState() {
        assertEquals("mount -o remount,rw '/system'", RootMount.remountCommand("/system", readWrite = true))
        // المسار يُطبَّع قبل الاقتباس: `/system/` و`/system` نقطة تعليق واحدة.
        assertEquals("mount -o remount,ro '/system'", RootMount.remountCommand("/system/", readWrite = false))
        assertEquals(RootMount.PROC_MOUNTS, "/proc/mounts")
    }
}
