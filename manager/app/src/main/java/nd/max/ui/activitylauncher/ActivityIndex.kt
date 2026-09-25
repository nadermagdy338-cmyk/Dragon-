/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.activitylauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.topjohnwu.superuser.Shell

/**
 * An installed package that declares at least one activity.
 *
 * [activityCount] counts *declared* activities, not exported ones. The screen
 * that shows it answers "how much surface does this package expose", and a
 * package whose activities are all `android:exported="false"` still declares
 * them — filtering them out would under-report exactly the vendor apps this
 * tool exists to reach.
 */
data class IndexedApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val activityCount: Int,
    val versionName: String?,
)

/**
 * One activity declared by an [IndexedApp].
 *
 * @param exported whether the manifest exposes it to other packages. A
 *        non-exported activity can only be started by its own UID or by root,
 *        which is why the screen offers the root path for those rows only.
 * @param label the activity's own `android:label` when it sets one; callers fall
 *        back to the class name, which is what power users are looking for here.
 */
data class IndexedActivity(
    val name: String,
    val label: String?,
    val exported: Boolean,
    val enabled: Boolean,
)

/** Which slice of the installed packages the index screen is showing. */
enum class AppScope { ALL, USER, SYSTEM }

/** Result of a launch attempt, so the screen explains a refusal instead of dropping it. */
enum class LaunchOutcome { STARTED, NEEDS_ROOT, REFUSED, NOT_FOUND }

/**
 * Reads installed packages and their declared activities.
 *
 * Everything here touches `PackageManager`, so every entry point is blocking and
 * belongs on a worker thread — enumerating several hundred packages and
 * resolving a label for each is far past a frame budget.
 */
object ActivityIndex {

    private const val ACTIVITY_FLAGS = PackageManager.GET_ACTIVITIES

    private fun installedPackages(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(ACTIVITY_FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(ACTIVITY_FLAGS)
        }

    private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(ACTIVITY_FLAGS.toLong()))
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.getPackageInfo(packageName, ACTIVITY_FLAGS) }.getOrNull()
        }

    /**
     * Every installed package that declares at least one activity, sorted by
     * label. A package whose label cannot be resolved falls back to its package
     * name rather than being dropped: an unreadable label is a reporting
     * problem, not a reason to hide the package.
     */
    fun apps(context: Context): List<IndexedApp> {
        val pm = context.packageManager
        val packages = installedPackages(pm)
        val index = ArrayList<IndexedApp>(packages.size)

        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            val activities = info.activities
            if (activities.isNullOrEmpty()) continue

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(info.packageName)

            index += IndexedApp(
                label = label,
                packageName = info.packageName,
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                activityCount = activities.size,
                versionName = info.versionName,
            )
        }

        return index.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * The activities [packageName] declares. Sorted by class name — stable and
     * locale-independent, so the same device always presents the same order.
     */
    fun activitiesFor(context: Context, packageName: String): List<IndexedActivity> {
        val pm = context.packageManager
        val info = packageInfo(pm, packageName) ?: return emptyList()

        return info.activities.orEmpty()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { activity ->
                IndexedActivity(
                    name = activity.name,
                    label = activity.nonLocalizedLabel?.toString(),
                    exported = activity.exported,
                    enabled = activity.enabled,
                )
            }
    }

    /**
     * Substring match on label or package name, narrowed by [scope].
     *
     * Deliberately free of Android types so the screen's filtering rule can be
     * pinned by a plain unit test instead of a device test.
     */
    fun filter(apps: List<IndexedApp>, query: String, scope: AppScope): List<IndexedApp> {
        val needle = query.trim().lowercase()
        return apps.filter { app ->
            val inScope = when (scope) {
                AppScope.ALL -> true
                AppScope.USER -> !app.isSystem
                AppScope.SYSTEM -> app.isSystem
            }
            inScope && (
                needle.isEmpty() ||
                    app.label.lowercase().contains(needle) ||
                    app.packageName.lowercase().contains(needle)
                )
        }
    }
}

/** Starts activities, directly when the manifest allows it and through root when it does not. */
object ActivityLauncher {

    /**
     * Direct start. Returns [LaunchOutcome.NEEDS_ROOT] on [SecurityException]
     * (the manifest does not export the activity) so the caller can offer the
     * root path instead of reporting a dead end.
     */
    fun launch(context: Context, packageName: String, activityName: String): LaunchOutcome {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(packageName, activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            LaunchOutcome.STARTED
        } catch (_: SecurityException) {
            LaunchOutcome.NEEDS_ROOT
        } catch (_: ActivityNotFoundException) {
            LaunchOutcome.NOT_FOUND
        } catch (_: Exception) {
            LaunchOutcome.REFUSED
        }
    }

    /**
     * Root start for non-exported activities: `am start -n pkg/class` runs as
     * uid 0, which bypasses the export check the same way the platform's own
     * `adb shell am` does.
     *
     * `$` is not escaped here on purpose: the command is passed to `sh -c` as
     * one argument through libsu, and `am` takes the component verbatim.
     */
    fun launchAsRoot(packageName: String, activityName: String): Boolean {
        val component = "$packageName/$activityName"
        return runCatching {
            Shell.cmd("am start -n $component").exec().isSuccess
        }.getOrDefault(false)
    }
}
