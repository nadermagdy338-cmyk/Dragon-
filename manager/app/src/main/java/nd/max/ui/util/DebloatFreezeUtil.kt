/*
 * Adapted from ZKM (Zuan Kernel Manager) DebloatFreezeUtils.
 * Original: Copyright (c) 2025 ZKM, licensed GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
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
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import com.topjohnwu.superuser.Shell

/**
 * A single installed app as shown on the Debloat & Freeze screen. Unlike
 * [nd.max.ui.viewmodel.ApplistViewmodel.AppInfo] (which tracks whether an
 * app is opted into MaxManager's per-app performance profile), this tracks the
 * app's actual enabled/disabled state at the PackageManager level.
 */
data class DebloatAppInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val icon: Drawable?
)

/**
 * Freeze (disable) or uninstall-for-user any installed app via root, without
 * needing Device Owner / Device Admin privileges. Freezing is reversible
 * (`pm enable`); the uninstall path only removes the app for the current
 * user (`pm uninstall --user 0`) so a system app can still be restored with
 * a factory reset or `pm install-existing` rather than being deleted outright.
 */
object DebloatFreezeUtil {

    fun getInstalledApps(context: Context): List<DebloatAppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

        return packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                DebloatAppInfo(
                    label = pm.getApplicationLabel(appInfo).toString(),
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "?",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong(),
                    isSystem = isSystem,
                    isEnabled = appInfo.enabled,
                    icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                )
            } catch (e: Exception) {
                EventLog.error("Debloat", "read_package_info:${pkg.packageName}", e)
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    /** Freezes (disables) or unfreezes (enables) an app for the current user. */
    fun toggleAppState(packageName: String, enable: Boolean): Boolean {
        val command = if (enable) {
            "pm enable $packageName"
        } else {
            "pm disable-user --user 0 $packageName"
        }
        return Shell.cmd(command).exec().isSuccess
    }

    /** Removes an app for the current user only. Recoverable via `pm install-existing` for system apps. */
    fun debloatApp(packageName: String): Boolean {
        return Shell.cmd("pm uninstall --user 0 $packageName").exec().isSuccess
    }

    fun openAppSystemSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            EventLog.error("Debloat", "open_app_settings:$packageName", e)
        }
    }
}
