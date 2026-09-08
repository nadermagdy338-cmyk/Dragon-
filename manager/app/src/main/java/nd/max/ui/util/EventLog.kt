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

import com.topjohnwu.superuser.Shell
import nd.max.MaxManagerPaths
import nd.max.MaxManagerProps

/**
 * Records "what the user chose, on which screen, and when" -- as opposed to
 * everything else in the logging system (AppMonitorLogger.kt on the Kotlin
 * companion side, log_zenith()/log_preload() on the native daemon side),
 * which only records the *engine's* reaction (apply/revert/profile-switch).
 * Before this file existed there was no record of the decision that caused
 * an engine action, only the action itself -- so a bad outcome could be
 * traced to "the daemon tried to apply X" but never to "the user turned on
 * X for this app, in this screen, at this time."
 *
 * This runs in the main app (UI) process, not the rooted AppMonitor
 * companion daemon, so unlike AppMonitorLogger.kt it can't just
 * Runtime.exec() directly -- it goes through libsu's Shell (the same
 * mechanism every other root operation in this app already uses, e.g.
 * LogUtil.kt's dumpDiagnosticLogs()) to reach the same
 * `sys.maxmanager-service --log <TAG> <LEVEL> <MSG>` CLI hook that
 * AppMonitorLogger forwards through, so every source -- UI, companion
 * daemon, native daemon -- ends up in the same timestamped MaxManager.log.
 *
 * Calls are fire-and-forget (Shell.cmd(...).submit()): logging a settings
 * change must never add latency or a failure path to the UI interaction
 * that triggered it.
 *
 * Gated behind [MaxManagerProps.Conf.DETAILED_LOG], off by default (see the
 * Settings screen's "Detailed activity log" toggle / SettingsViewModel).
 * Checked via [PropertyUtils] -- a plain reflective getprop read, not a
 * shell spawn -- so the disabled (default) state costs nothing extra per
 * interaction beyond that one cheap check.
 */
object EventLog {
    private const val TAG = "ui"
    private const val LEVEL_INFO = 1

    private fun isEnabled(): Boolean = PropertyUtils.get(MaxManagerProps.Conf.DETAILED_LOG) == "1"

    /**
     * Records a single setting change.
     *
     * @param screen Short screen/section identifier, e.g. "AppSettings",
     *   "GlobalTweaks", "DebloatFreeze". Keep these stable -- they're what
     *   LogsViewerScreen's future source filter will group by.
     * @param field The setting's key, e.g. "gpu_profile", "cpu_governor".
     *   Use the same key the config JSON / prop uses where one exists, so
     *   this line and the resulting APPLY_FAILED/PROFILE_APPLY lines are
     *   trivially greppable together.
     * @param old Value before the change. Use "default"/"off"/etc. rather
     *   than an empty string so the line stays self-explanatory.
     * @param new Value after the change.
     * @param pkg Optional package name, when the action is scoped to one
     *   app (per-app config screens). Omitted for global settings.
     */
    fun userAction(screen: String, field: String, old: String, new: String, pkg: String? = null) {
        val pkgPart = if (pkg != null) " pkg=${shellSafe(pkg)}" else ""
        val message = "EVENT=USER_ACTION screen=${shellSafe(screen)} field=${shellSafe(field)} " +
            "old=${shellSafe(old)} new=${shellSafe(new)}$pkgPart"
        forward(message)
    }

    /**
     * Records a discrete user-triggered action that isn't a value change --
     * a button tap like "kill process", "freeze app", "flash kernel module".
     *
     * @param screen Short screen identifier, same convention as userAction().
     * @param action What was triggered, e.g. "kill_process", "freeze",
     *   "flash_module".
     * @param target Optional subject of the action (package name, process
     *   name, module id, etc.).
     */
    fun userTriggered(screen: String, action: String, target: String? = null) {
        val targetPart = if (target != null) " target=${shellSafe(target)}" else ""
        val message = "EVENT=USER_TRIGGERED screen=${shellSafe(screen)} action=${shellSafe(action)}$targetPart"
        forward(message)
    }

    /**
     * Records a handled application error. This is intentionally separate from
     * [userTriggered] so diagnostic logs can distinguish a failed operation
     * from a normal user action. The throwable message is sanitized and capped
     * because this log is persistent on the device.
     */
    fun error(screen: String, operation: String, throwable: Throwable? = null) {
        val detail = throwable?.message
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.take(240)
            ?.let { " detail=${shellSafe(it)}" }
            ?: ""
        forward(
            "EVENT=UI_ERROR screen=${shellSafe(screen)} " +
                "operation=${shellSafe(operation)}$detail"
        )
    }

    private fun forward(message: String) {
        if (!isEnabled()) return
        try {
            Shell.cmd("'${MaxManagerPaths.SERVICE_BIN}' --log '$TAG' '$LEVEL_INFO' '$message'").submit()
        } catch (_: Exception) {
            // Never let logging failure surface to the UI interaction that
            // triggered it -- worst case this one line is missing from
            // MaxManager.log, which is still strictly better than crashing
            // or blocking a settings toggle over a log write.
        }
    }

    /**
     * Values here are generally safe (enum-like settings values, package
     * names), but this is defense in depth against the shell command built
     * above ever seeing a stray single quote.
     */
    private fun shellSafe(value: String): String =
        value.replace("\n", " ").replace("\r", "").replace("'", "'\\''")
}
