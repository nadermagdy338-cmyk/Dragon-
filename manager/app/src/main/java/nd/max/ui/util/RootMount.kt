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

/**
 * `MT-FM/ب` — حالة **القراءة/الكتابة** لنظام الملفات الذي يقف عليه مسار، وتعليقه.
 *
 * ولماذا لا يُسأل الأمر عن نجاحه: `mount -o remount,rw /system` يخرج بنجاح على أجهزة
 * كثيرة **ولا يغيّر شيئًا** (تقسيمات A/B، أو `overlayfs`، أو نظام قراءة-فقط أصلًا).
 * فالقاعدة نفسها التي يحكم بها الـarbiter: الحالة **تُقاس من جدول التعليقات** بعد الأمر،
 * ويُعلَن الفرق بين «قيل إنه تغيّر» و«تغيّر فعلًا».
 *
 * والتحليل خالص هنا (نصّ `/proc/mounts` ← حالة)، فيُقاس في JVM على أسطر مصنوعة.
 */
package nd.max.ui.util

/** حالة الوصول لكتابة نظام ملفات. `Unknown` حين لا نجد تعليقًا للمسار أو لا خيارات. */
enum class MountAccess { ReadOnly, ReadWrite, Unknown }

/** تعليق واحد: نقطة التعليق وخياراتها. */
data class MountEntry(val point: String, val options: List<String>) {
    val access: MountAccess
        get() = when {
            "rw" in options -> MountAccess.ReadWrite
            "ro" in options -> MountAccess.ReadOnly
            else -> MountAccess.Unknown
        }

    val isReadWrite: Boolean get() = access == MountAccess.ReadWrite
}

object RootMount {

    const val PROC_MOUNTS: String = "/proc/mounts"

    /**
     * فكّ الترميز الثماني الذي يكتبه النواة في جدول التعليقات: المسافة تصبح `\040`،
     * فلا يُقسَّم السطر إلى حقول أكثر مما يجب. (ومجلد باسم فيه مسافة شائع على أندرويد:
     * `/storage/emulated/0/My Files`.)
     */
    fun decode(value: String): String = value
        .replace("\\040", " ")
        .replace("\\011", "\t")
        .replace("\\012", "\n")
        .replace("\\134", "\\")

    /** أسطر `/proc/mounts` ← تعليقات. والسطر الذي لا يُفهم يُهمَل بلا إنكار. */
    fun parse(lines: List<String>): List<MountEntry> {
        val out = ArrayList<MountEntry>(lines.size)
        for (line in lines) {
            val parts = line.trim().split(' ').filter { it.isNotBlank() }
            if (parts.size < 4) continue
            val point = decode(parts[1])
            if (!point.startsWith("/")) continue
            out += MountEntry(point = point, options = parts[3].split(',').filter { it.isNotBlank() })
        }
        return out
    }

    /**
     * التعليق الذي يحكم هذا المسار: **الأطول مطابقةً**.
     *
     * ولماذا الأطول: `/system` معلَّق تحت `/` و`/system` قد يكون له تعليق خاص (rw على
     * أجهزة المعدّلين). اختيار الأول يعني الإجابة عن `/system/bin` بحالة الجذر، وهو
     * عكس الحقيقة تمامًا.
     */
    fun entryFor(entries: List<MountEntry>, rawPath: String): MountEntry? {
        val path = FileBrowser.normalize(rawPath)
        return entries
            .filter { FileBrowser.isInside(path, it.point) }
            .maxByOrNull { it.point.length }
    }

    fun accessFor(entries: List<MountEntry>, rawPath: String): MountAccess =
        entryFor(entries, rawPath)?.access ?: MountAccess.Unknown

    /** أمر إعادة التعليق: الاحتياطي الأول حين لا توجد وحدة جذر تفعلها. */
    fun remountCommand(point: String, readWrite: Boolean): String =
        "mount -o remount,${if (readWrite) "rw" else "ro"} ${PrivilegedShell.quote(FileBrowser.normalize(point))}"

    // ────────────────────────────────────────────────────────────────────────
    // التنفيذ (رقيق: قراءة ونداء)
    // ────────────────────────────────────────────────────────────────────────

    /** قراءة الجدول من الجهاز. `null` تعني «لم تُقرأ» لا «لا تعليقات». */
    fun read(): List<MountEntry>? =
        PrivilegedShell.run("cat $PROC_MOUNTS")?.let(::parse)

    fun currentAccess(rawPath: String): MountAccess = accessFor(read().orEmpty(), rawPath)

    /**
     * هل يُنفَّذ أمر بصلاحية الجذر الآن؟ **يُقاس** بتنفيذ أمر واحد، ولا يُفترض من إعداد
     * سابق ولا من منحٍ قديم.
     *
     * ووُجدت هنا لأن الشاشة كانت تنادي `PrivilegedShell` مباشرة لسؤال واحد — والوصول إلى
     * shell من طبقة العرض ممنوع بقاعدة المستودع (ADR-11)، ولو كان السؤال بريئًا. فصار
     * السؤال دالّة مُسمّاة في طبقة الأدوات، والشاشة تسأل ولا تُنفّذ.
     */
    fun granted(): Boolean = PrivilegedShell.run("id") != null

    /**
     * تغيير الحالة إلى rw/ro ثم **قياس النتيجة**.
     *
     * والنتيجة الثلاثية تُقال كما هي: `executed && verified` فقط حين تغيّرت الحالة فعلًا
     * في الجدول بعد الأمر. وإن نُفِّذ الأمر ولم تتغيّر الحالة ⇒ «نُفِّذ ولم يُتحقّق»، وهي
     * عبارة الشاشة لمن يطلب r/w على نظام لا يقبله.
     */
    fun remount(rawPath: String, readWrite: Boolean): FileOpOutcome {
        val point = entryFor(read().orEmpty(), rawPath)?.point ?: return FileOpOutcome(false, false)
        val executed = PrivilegedShell.run(remountCommand(point, readWrite)) != null
        if (!executed) return FileOpOutcome(false, false)
        val after = accessFor(read().orEmpty(), rawPath)
        val wanted = if (readWrite) MountAccess.ReadWrite else MountAccess.ReadOnly
        return FileOpOutcome(executed = true, verified = after == wanted)
    }
}
