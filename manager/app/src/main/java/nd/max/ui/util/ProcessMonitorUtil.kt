/*
 * Adapted from ZKM (Zuan Kernel Manager) utils/ProcessUtils.kt.
 * Original: Copyright (c) 2025 ZKM, licensed GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
 *
 * Ported to use libsu's Shell (as the rest of MaxManager's root commands do)
 * instead of a raw `Runtime.exec("su -c ...")` call, and extended with
 * killProcess()/forceStopApp() for the manage-actions on this screen.
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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.topjohnwu.superuser.Shell

/** A single running process, as reported by `top`. */
data class ProcessInfo(
    val pid: String,
    val res: String,
    val cpu: String,
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val icon: Drawable? = null
)

enum class ProcessSortType { CPU, RAM }

object ProcessMonitorUtil {

    /**
     * Lists the top [limit] processes by [sortType], sorted descending.
     * `top -b -n 2 -d 1` takes two one-second-apart samples so the CPU%
     * column reflects an actual delta rather than the all-time average
     * `top` reports on a single sample.
     */
    fun getTopProcesses(context: Context, limit: Int, sortType: ProcessSortType): List<ProcessInfo> {
        val pm = context.packageManager
        val processMap = LinkedHashMap<String, ProcessInfo>()

        val output = Shell.cmd("top -b -n 2 -d 1 -o PID,RES,%CPU,NAME").exec().out

        for (rawLine in output) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("PID") || line.startsWith("Tasks") || line.startsWith("Mem")) continue

            val cols = line.split(Regex("\\s+"))
            if (cols.size < 4) continue
            if (cols[0].toIntOrNull() == null) continue

            val pid = cols[0]
            val resRaw = cols[1]
            val cpuRaw = cols[2]
            val rawName = cols[3]

            var appName = rawName
            var isSystem = true
            var icon: Drawable? = null

            if (rawName.contains(".")) {
                try {
                    val appInfo = pm.getApplicationInfo(rawName, 0)
                    appName = pm.getApplicationLabel(appInfo).toString()
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                } catch (e: PackageManager.NameNotFoundException) {
                    // Not an installed package (a native/system process) - keep raw name.
                }
            } else if (rawName.contains("/")) {
                appName = rawName.substringAfterLast("/")
            }

            // `top -n 2` prints every PID twice; the map keeps the second
            // (real-time) sample, overwriting the first (boot-average) one.
            processMap[pid] = ProcessInfo(pid, formatRes(resRaw), "$cpuRaw%", rawName, appName, isSystem, icon)
        }

        val sorted = processMap.values.sortedWith(
            compareByDescending {
                if (sortType == ProcessSortType.CPU) {
                    it.cpu.removeSuffix("%").toFloatOrNull() ?: 0f
                } else {
                    parseResToKb(it.res)
                }
            }
        )

        return sorted.take(limit)
    }

    /** Sends SIGKILL directly to a PID. The process may respawn if part of a live app. */
    fun killProcess(pid: String): Boolean = Shell.cmd("kill -9 $pid").exec().isSuccess

    /** Force-stops every process belonging to a package, the same as system Settings does. */
    fun forceStopApp(packageName: String): Boolean = Shell.cmd("am force-stop $packageName").exec().isSuccess

    private fun formatRes(rssVal: String): String {
        return try {
            if (rssVal.all { it.isDigit() }) {
                val kb = rssVal.toLong()
                if (kb > 1024) String.format("%.1f MB", kb / 1024f) else "$kb KB"
            } else {
                rssVal
            }
        } catch (e: Exception) {
            rssVal
        }
    }

    private fun parseResToKb(resString: String): Long {
        return try {
            val upper = resString.uppercase()
            val num = upper.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
            when {
                upper.contains("G") -> (num * 1024 * 1024).toLong()
                upper.contains("M") -> (num * 1024).toLong()
                else -> num.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
}
