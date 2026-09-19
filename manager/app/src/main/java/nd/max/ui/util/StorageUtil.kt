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
 * قارئ التخزين: نقاط التحميل، والعُقد، وحجم ملفات الحِزم، ومسح المجلدات.
 *
 * المصادر كلها قراءة فقط: `/proc/mounts` و`StatFs` و`File.length()` و`PackageManager`.
 * ولا مسار كتابة واحد هنا — شاشة التخزين تقيس ولا تغيّر، وهذا ما يمنعه ADR-11 من أن
 * ينقلب بخطأ صغير.
 *
 * والقرارات (تصنيف، ترتيب، نسب) ليست هنا بل في [StorageScanModel] الخالص، لأن هذا
 * الملف يحتاج جهازًا ليُقاس بينما ذاك يحتاج JVM عاديًا فقط.
 */
package nd.max.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.StatFs
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/** نقطة تحميل واحدة كما يُعلنها `/proc/mounts`، مع قياس `StatFs` إن توفّر. */
data class MountInfo(
    val path: String,
    val fileSystem: String,
    val readOnly: Boolean,
    val totalBytes: Long?,
    val freeBytes: Long?,
    val inodesTotal: Long?,
    val inodesFree: Long?
) {
    val usedBytes: Long? get() = totalBytes?.let { total -> freeBytes?.let { free -> (total - free).coerceAtLeast(0L) } }

    val usedFraction: Float
        get() {
            val total = totalBytes ?: return 0f
            if (total <= 0L) return 0f
            val used = usedBytes ?: return 0f
            return (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val isMeasured: Boolean get() = totalBytes != null && freeBytes != null
}

object StorageUtil {

    /**
     * نقاط التحميل التي تهمّ مستخدم الهاتف.
     *
     * قائمة مُسمّاة لا «كل ما في `/proc/mounts`»: الجهاز يعلن عشرات النقاط من نوع
     * `tmpfs`/`cgroup`/`binder` لا تخصّ مساحة المستخدم، وعرضها يدفن ما يهمّ تحت ما لا
     * يهمّ. ومن احتاج النقطة الغائبة يجدها في مدير الملفات.
     */
    private val INTERESTING_PATHS = listOf(
        "/data",
        "/sdcard",
        "/storage/emulated/0",
        "/system",
        "/system_ext",
        "/vendor",
        "/product",
        "/cache",
        "/metadata",
        "/persist"
    )

    /** أول مسار يطابق لكل نقطة: `/sdcard` و`/storage/emulated/0` نقطة واحدة لا اثنتان. */
    fun interestingMounts(parsed: List<MountInfo>): List<MountInfo> {
        val chosen = linkedMapOf<String, MountInfo>()
        for (mount in parsed) {
            if (mount.path !in INTERESTING_PATHS) continue
            val key = canonicalMountKey(mount.path)
            if (key !in chosen) chosen[key] = mount
        }
        return chosen.values.toList()
    }

    /**
     * `/sdcard` و`/storage/emulated/0` نفس التخزين المُركَّب مرتين؛ مفتاح واحد يمنع
     * عدّ المساحة مرتين في المجموع المعروض.
     */
    private fun canonicalMountKey(path: String): String =
        if (path == "/sdcard" || path == "/storage/emulated/0") "/storage/emulated/0" else path

    fun readMounts(): List<MountInfo> {
        val table = runCatching { File("/proc/mounts").readText() }.getOrNull().orEmpty()
        if (table.isBlank()) return emptyList()

        val parsed = table.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size < 4) return@mapNotNull null
            val path = parts[1].replace("\\040", " ")
            if (path !in INTERESTING_PATHS) return@mapNotNull null
            MountInfo(
                path = path,
                fileSystem = parts[2],
                readOnly = parts[3].split(',').any { it.equals("ro", true) },
                totalBytes = null,
                freeBytes = null,
                inodesTotal = null,
                inodesFree = null
            )
        }.toList()

        return interestingMounts(parsed).map { it.withNumbers() }
    }

    /** يضيف قياس `StatFs` إلى نقطة تحميل. `null` يعني «لم يُقس»، لا «صفر». */
    private fun MountInfo.withNumbers(): MountInfo {
        val stats = runCatching { StatFs(path) }.getOrNull()
        val total = stats?.let { runCatching { it.blockSizeLong * it.blockCountLong }.getOrNull() }
        val free = stats?.let { runCatching { it.blockSizeLong * it.availableBlocksLong }.getOrNull() }
        val inodes = readInodeCounts(path)
        return copy(
            totalBytes = total?.takeIf { it > 0L },
            freeBytes = free,
            inodesTotal = inodes?.first,
            inodesFree = inodes?.second
        )
    }

    /**
     * العُقد من أدوات النظام.
     *
     * `StatFs` لا يُعلن عدد العُقد في واجهته العلنية، فلا سبيل غير `stat -f` أو `df -i`.
     * ويُجرّبان بالترتيب لأن صيغتهما تختلف بين coreutils وtoybox، والتحليل نفسه في
     * [StorageScanModel.parseInodeCounts] ليكون مقيسًا في JVM بمخرجات حقيقية لا مُتخيَّلة.
     */
    private fun readInodeCounts(path: String): Pair<Long, Long>? {
        val stat = runCatching {
            Shell.cmd("stat -f -c '%c %d' '$path' 2>/dev/null").exec()
        }.getOrNull()?.out?.firstOrNull()
        val df = runCatching {
            Shell.cmd("df -i '$path' 2>/dev/null").exec()
        }.getOrNull()?.out?.joinToString("\n")
        return StorageScanModel.parseInodeCounts(stat, df)
    }

    /**
     * مجموع أحجام ملفات APK المثبَّتة.
     *
     * يُقرأ لأن مساحة التطبيقات أكبر مصرف لا يظهر في `StatFs` منفصلًا، و`File.length()`
     * على `sourceDir` مُتاح بلا صلاحية إضافية. البيانات الخاصة بكل تطبيق لا تُقرأ بلا
     * جذر، فما يُعرض هو ما قيس فعلًا: حجم الحزمة.
     *
     * ويعيد `null` إن تعذّر السؤال أصلًا، حتى لا يُعرض صفر مكان «مجهول».
     */
    fun installedApkBytes(context: Context): Long? = runCatching {
        val packages = context.packageManager
            .getInstalledPackages(PackageManager.GET_META_DATA)
        var total = 0L
        var measured = 0
        packages.forEach { info ->
            val source = info.applicationInfo?.sourceDir ?: return@forEach
            val length = runCatching { File(source).length() }.getOrDefault(0L)
            if (length > 0L) {
                total += length
                measured++
            }
        }
        if (measured == 0) null else total
    }.getOrNull()

    /**
     * مسح مجلدات وتراكم المصارف.
     *
     * سقف [maxEntries] مقصود: مسح شجرة كاملة على جهاز ممتلئ يستغرق دقائق، وشاشة تُجمّد
     * لتُنتج رقمًا أدقّ ليست صفقة رابحة. وعند بلوغ السقف يُعلَن [StorageScanResult.truncated]
     * بدل أن يُقدَّم الناتج كأنه الشجرة كلها.
     *
     * والمجلدات غير المقروءة تُعدّ ولا تُسقط بصمت — وهذا الفرق بين «المسح نظيف» و«المسح
     * لم يرَ كل شيء».
     */
    suspend fun scan(
        roots: List<File>,
        maxEntries: Int = 120_000,
        onProgress: (scanned: Int) -> Unit = {}
    ): StorageScanResult {
        var buckets: List<StorageBucket> = emptyList()
        var largest: List<StorageLargestItem> = emptyList()
        var scanned = 0
        var skipped = 0
        var truncated = false

        val queue = ArrayDeque<File>()
        roots.forEach { queue.addLast(it) }

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val directory = queue.removeFirst()
            val children = runCatching { directory.listFiles() }.getOrNull()
            if (children == null) {
                // غير مقروء أو ليس مجلدًا: يُعدّ ويُتخطّى، ولا يوقف المسح.
                if (directory.isDirectory) skipped++ else continue
                continue
            }
            for (child in children) {
                if (scanned >= maxEntries) {
                    truncated = true
                    break
                }
                if (child.isDirectory) {
                    queue.addLast(child)
                    continue
                }
                val length = runCatching { child.length() }.getOrDefault(0L)
                scanned++
                if (length <= 0L) continue
                val kind = StorageScanModel.kindOf(child.name)
                buckets = StorageScanModel.accumulate(buckets, kind, length)
                largest = StorageScanModel.keepLargest(
                    largest,
                    StorageLargestItem(path = child.absolutePath, name = child.name, bytes = length)
                )
            }
            if (truncated) break
            if (scanned % 512 == 0) onProgress(scanned)
        }

        onProgress(scanned)
        return StorageScanResult(
            buckets = StorageScanModel.rank(buckets),
            largest = largest,
            scannedEntries = scanned,
            skippedDirectories = skipped,
            truncated = truncated
        )
    }

    fun formatBytes(bytes: Long?): String? = bytes?.let(StorageScanModel::formatBytes)
}
