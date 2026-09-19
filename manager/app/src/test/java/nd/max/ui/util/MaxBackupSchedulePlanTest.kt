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
 * `OCR-01` + `OCR-02` بلا Android: كل ما يُقاس هنا منطق خالص — قراءة الوقت، وحساب الموعد
 * القادم عبر منتصف الليل، وقواعد المجموعات، والتخزين النصّي.
 *
 * **ولماذا هذا الملف هو الأهم في الميزتين:** الأشياء الثلاثة التي تُحسب ولا تُرى هي التي
 * تُخطئ بصمت: تحويل «٣:٠٠» إلى دقيقة، والمسافة إلى موعد الأسبوع القادم، ومجموعة تُحفظ ثم
 * تُقرأ فارغة. والثلاثة كلها هنا، لا في شاشة تحتاج جهازًا.
 */
class MaxBackupSchedulePlanTest {

    // ────────────────────────────────────────────────────────────────────────
    // قراءة الوقت
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `clock and parseClock are inverse for every minute of the day`() {
        for (minute in 0 until MaxBackupSchedule.MINUTES_PER_DAY) {
            assertEquals(
                "minute $minute",
                minute,
                MaxBackupSchedule.parseClock(MaxBackupSchedule.clock(minute)),
            )
        }
    }

    @Test
    fun `parseClock accepts the shapes people actually type`() {
        assertEquals(180, MaxBackupSchedule.parseClock("03:00"))
        assertEquals(180, MaxBackupSchedule.parseClock("3:00"))
        assertEquals(180, MaxBackupSchedule.parseClock("0300"))
        assertEquals(420, MaxBackupSchedule.parseClock("700"))
        assertEquals(0, MaxBackupSchedule.parseClock("00:00"))
        assertEquals(1439, MaxBackupSchedule.parseClock("23:59"))
        assertEquals(180, MaxBackupSchedule.parseClock("  03:00  "))
    }

    @Test
    fun `parseClock reads Arabic-Indic digits because keyboards write them`() {
        // `٠٣:٣٠` بأرقام عربية-هندية: وقت صحيح كتبه مستخدم عربي، ورفضه يعني «ليس وقتًا» وهو وقت.
        assertEquals(210, MaxBackupSchedule.parseClock("\u0660\u0663:\u0663\u0660"))
        assertEquals(180, MaxBackupSchedule.parseClock("\u06F0\u06F3:\u06F0\u06F0"))
    }

    @Test
    fun `parseClock refuses what it cannot know, and never throws`() {
        assertNull(MaxBackupSchedule.parseClock(""))
        assertNull(MaxBackupSchedule.parseClock("   "))
        assertNull(MaxBackupSchedule.parseClock("abc"))
        assertNull(MaxBackupSchedule.parseClock("24:00"))
        assertNull(MaxBackupSchedule.parseClock("23:60"))
        assertNull(MaxBackupSchedule.parseClock("03-00"))
        assertNull(MaxBackupSchedule.parseClock("3:5"))
        assertNull(MaxBackupSchedule.parseClock("7"))
        assertNull(MaxBackupSchedule.parseClock("03:00:00"))
        // أطول مما يستطيع `toInt` قراءته: الجواب `null` لا استثناء يُسقط الشاشة.
        assertNull(MaxBackupSchedule.parseClock("99999999999:00"))
        assertNull(MaxBackupSchedule.parseClock("12345"))
    }

    // ────────────────────────────────────────────────────────────────────────
    // حساب الموعد القادم
    // ────────────────────────────────────────────────────────────────────────

    private fun plan(
        minuteOfDay: Int = 180,
        days: Set<Int> = emptySet(),
        sets: List<String> = listOf("photos"),
    ) = MaxBackupSchedule.Plan(enabled = true, minuteOfDay = minuteOfDay, days = days, sets = sets)

    @Test
    fun `the next run is today when the time has not come yet`() {
        // الاثنين=١، الآن ٠٢:٠٠، الموعد ٠٣:٠٠ ⇒ ٦٠ دقيقة.
        assertEquals(60L, MaxBackupSchedule.minutesUntilNextRun(plan(), nowMinuteOfDay = 120, isoDay = 1))
    }

    @Test
    fun `the next run crosses midnight instead of reporting a negative wait`() {
        // الموعد ٢٣:٠٠ والآن ٠١:٠٠ ⇒ ٢٢ ساعة، لا سالب.
        val delay = MaxBackupSchedule.minutesUntilNextRun(
            plan(minuteOfDay = 23 * 60),
            nowMinuteOfDay = 60,
            isoDay = 3,
        )
        assertEquals(22 * 60L, delay)
    }

    @Test
    fun `a single weekday waits a week, not a day`() {
        // مجموعة يوم واحد: الاثنين (١) الموعد ٠٣:٠٠، والآن الاثنين ٠٤:٠٠ ⇒ ٦ أيام و٢٣ ساعة.
        // وهذه هي الحالة التي يخطئ فيها «اليوم ثم غدًا» بخطأ أسبوع.
        val delay = MaxBackupSchedule.minutesUntilNextRun(
            plan(days = setOf(1)),
            nowMinuteOfDay = 4 * 60,
            isoDay = 1,
        )
        assertEquals(6L * 1440 + 23 * 60, delay)
    }

    @Test
    fun `a stopped or empty schedule has no next run`() {
        assertNull(MaxBackupSchedule.minutesUntilNextRun(plan().copy(enabled = false), 120, 1))
        assertNull(MaxBackupSchedule.minutesUntilNextRun(plan().copy(sets = emptyList()), 120, 1))
        assertFalse(MaxBackupSchedule.isDue(plan().copy(enabled = false), 120, 1))
    }

    @Test
    fun `blockers name the reason, so the screen never shows a promise with no run`() {
        assertEquals(
            MaxBackupSchedule.Blocker.DISABLED,
            MaxBackupSchedule.blocker(MaxBackupSchedule.Plan(enabled = false)),
        )
        assertEquals(
            MaxBackupSchedule.Blocker.NO_TARGET,
            MaxBackupSchedule.blocker(MaxBackupSchedule.Plan(enabled = true)),
        )
        assertNull(MaxBackupSchedule.blocker(plan()))
    }

    @Test
    fun `conditions are only a filter on what the system already enforces`() {
        val charging = MaxBackupSchedule.Plan(enabled = true, sets = listOf("photos"), chargingOnly = true)
        assertFalse(MaxBackupSchedule.allowsRun(charging, charging = false, unmetered = true))
        assertTrue(MaxBackupSchedule.allowsRun(charging, charging = true, unmetered = false))

        val wifi = MaxBackupSchedule.Plan(enabled = true, sets = listOf("photos"), chargingOnly = false, wifiOnly = true)
        assertFalse(MaxBackupSchedule.allowsRun(wifi, charging = true, unmetered = false))
        assertTrue(MaxBackupSchedule.allowsRun(wifi, charging = false, unmetered = true))
    }

    @Test
    fun `a target that no longer exists is named, not silently replaced`() {
        val missing = MaxBackupSchedule.missing(
            plan(sets = listOf("photos", "gone")).copy(kinds = listOf("WIFI", "SMS")),
            existingSets = listOf("photos"),
            existingKinds = listOf("WIFI"),
        )
        assertEquals(listOf("gone", "SMS"), missing)
    }

    @Test
    fun `the plan survives a round trip through the text file`() {
        val original = MaxBackupSchedule.Plan(
            enabled = true,
            minuteOfDay = 210,
            chargingOnly = false,
            wifiOnly = true,
            days = setOf(2, 4, 6),
            sets = listOf("photos", "work"),
            kinds = listOf("WIFI"),
            lastRunAtMs = 1_760_000_000_000L,
            lastResult = MaxBackupSchedule.Result.FAILED,
        )
        assertEquals(original, MaxBackupSchedule.decode(MaxBackupSchedule.encode(original)))

        // قيمة تحمل سطرًا جديدًا لا تستطيع تزوير سطر ثانٍ في الملف: تُسقَط هي، ويبقى الصالح.
        val injected = original.copy(sets = listOf("photos", "bad\nsecond"))
        assertEquals(listOf("photos"), MaxBackupSchedule.decode(MaxBackupSchedule.encode(injected)).sets)
    }

    @Test
    fun `a hand-edited or damaged plan file still yields a usable plan`() {
        val decoded = MaxBackupSchedule.decode("enabled=1\nminute=9999\nnonsense\ndays=1,9,x\nsets=photos")
        assertEquals(MaxBackupSchedule.MINUTES_PER_DAY - 1, decoded.minuteOfDay)
        assertEquals(setOf(1), decoded.days)
        assertEquals(listOf("photos"), decoded.sets)
        assertTrue(MaxBackupSchedule.decode("").enabled.not())
    }

    // ────────────────────────────────────────────────────────────────────────
    // مجموعات المجلدات
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the root and the kernel filesystems can never be archived`() {
        assertFalse(MaxBackupFolders.isBackupable("/"))
        assertFalse(MaxBackupFolders.isBackupable("/proc"))
        assertFalse(MaxBackupFolders.isBackupable("/sys/class"))
        assertFalse(MaxBackupFolders.isBackupable("/dev"))
        assertFalse(MaxBackupFolders.isBackupable(""))
        // الرفض على أول قطعة من المسار: شجرة تحت `/sys` تُقرأ إلى ما لا نهاية، وضغطها في
        // مهمة مجدولة عطب لا يظهر إلا على الجهاز.
        assertFalse(MaxBackupFolders.isBackupable("/proc/self"))
        assertFalse(MaxBackupFolders.isBackupable("/dev/block"))
        assertFalse(MaxBackupFolders.isBackupable("/sys/kernel/debug"))
        assertTrue(MaxBackupFolders.isBackupable("/storage/emulated/0/DCIM"))
        // وقرين الرفض: أسماء تشبه الجذر ولا هي فيه لا تُرفض.
        assertTrue(MaxBackupFolders.isBackupable("/storage/emulated/0/Documents/proc"))
    }

    @Test
    fun `a name that would break the storage line is refused`() {
        assertNull(MaxBackupFolders.sanitizeName(""))
        assertNull(MaxBackupFolders.sanitizeName("  "))
        assertNull(MaxBackupFolders.sanitizeName("a/b"))
        assertNull(MaxBackupFolders.sanitizeName("a\nb"))
        assertNull(MaxBackupFolders.sanitizeName("a\tb"))
        assertEquals("صوري", MaxBackupFolders.sanitizeName("  صوري  "))
        assertNull(MaxBackupFolders.sanitizeName("x".repeat(MaxBackupFolders.MAX_NAME + 1)))
    }

    @Test
    fun `a set is never created empty, and duplicate paths collapse`() {
        assertNull(MaxBackupFolders.create("صور", listOf("/"), addedAtMs = 1L))
        val created = MaxBackupFolders.create(
            name = "صور",
            paths = listOf("/storage/emulated/0/DCIM", "/storage/emulated/0/DCIM/", "/proc"),
            addedAtMs = 7L,
        )
        assertEquals(listOf("/storage/emulated/0/DCIM"), created?.paths)
        assertEquals(7L, created?.addedAtMs)
    }

    @Test
    fun `two sets cannot share a name`() {
        val first = MaxBackupFolders.create("صور", listOf("/storage/emulated/0/DCIM"), 1L)!!
        val second = MaxBackupFolders.create("صور", listOf("/storage/emulated/0/Music"), 2L, taken = listOf(first.name))!!
        assertEquals("صور (1)", second.name)
    }

    @Test
    fun `adding the same folder twice is refused`() {
        val set = MaxBackupFolders.FolderSet("صور", listOf("/storage/emulated/0/DCIM"))
        assertNull(MaxBackupFolders.addPath(set, "/storage/emulated/0/DCIM"))
        assertNull(MaxBackupFolders.addPath(set, "/proc"))
        assertEquals(
            listOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Music"),
            MaxBackupFolders.addPath(set, "/storage/emulated/0/Music")?.paths,
        )
    }

    @Test
    fun `an overlap is reported, because it doubles the size of a scheduled run`() {
        val photos = MaxBackupFolders.FolderSet("صور", listOf("/storage/emulated/0/DCIM"))
        val everything = MaxBackupFolders.FolderSet("كل شيء", listOf("/storage/emulated/0"))
        assertTrue(MaxBackupFolders.overlaps(photos, everything))
        assertEquals(listOf("/storage/emulated/0/DCIM"), MaxBackupFolders.sharedPaths(photos, everything))
        assertFalse(
            MaxBackupFolders.overlaps(
                photos,
                MaxBackupFolders.FolderSet("موسيقى", listOf("/storage/emulated/0/Music")),
            )
        )
    }

    @Test
    fun `sets survive a round trip, and a damaged line costs only that line`() {
        val sets = listOf(
            MaxBackupFolders.FolderSet("صور", listOf("/storage/emulated/0/DCIM"), 11L),
            MaxBackupFolders.FolderSet("عمل", listOf("/storage/emulated/0/Documents"), 12L),
        )
        assertEquals(sets, MaxBackupFolders.decode(MaxBackupFolders.encode(sets)))

        // سطر مشوّه في الوسط: الأول والثالث يُقرآن، والعدد الصحيح يعود.
        // و`lines()` لا `lineSequence()`: الثانية تُبقي سطرًا فارغًا في الآخر، فأخذ «الأخير»
        // منها يأخذ الفراغ لا المجموعة.
        val damaged = MaxBackupFolders.encode(sets).lines().filter { it.isNotBlank() }
        val text = listOf(damaged.first(), "سطر بلا فاصل", damaged.last()).joinToString("\n")
        assertEquals(sets, MaxBackupFolders.decode(text))
    }

    @Test
    fun `a label for a path is its last segment, which is what the screen shows`() {
        assertEquals("DCIM", MaxBackupFolders.labelOf("/storage/emulated/0/DCIM/"))
        assertEquals("DCIM", MaxBackupFolders.labelOf("/storage/emulated/0/DCIM//"))
    }
}
