/*
 * Copyright (C) 2026 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max

import nd.max.ui.util.PropertyUtils

/**
 * Structured logger for the AppMonitor companion daemon (nd.max.AppMonitor).
 *
 * Before this file existed, AppMonitor.kt logged failures with bare
 * System.err.println()/printStackTrace() calls, all of which landed in
 * sysmon.log via service.sh's blind `>sysmon.log 2>&1` redirect -- a
 * separate, unstructured, un-timestamped file that never lined up with the
 * native daemon's own MaxManager.log (written by log_zenith() in
 * SystemLogger.c). That split is exactly what made the 2026-08-27
 * zen_mode/peak_refresh_rate SecurityException hard to see: the daemon's
 * log looked healthy while the real failure was silently aborting
 * applyPerAppConfig() on every app switch, visible only as raw stderr noise
 * in a different file.
 *
 * This object closes that gap by routing every AppMonitor log line through
 * the daemon's existing `sys.maxmanager-service --log <TAG> <LEVEL> <MSG>`
 * CLI hook (see handle_log() in archdaemon/jni/src/BinaryCLI/CLIUtility.c),
 * so Kotlin-side and native-side events end up interleaved in the same
 * timestamped MaxManager.log, in the order they actually happened.
 *
 * Numeric levels below intentionally mirror the native LogLevel enum
 * (AZenith.h): 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR, 4=FATAL.
 *
 * stderr output is kept alongside the CLI forward (not replaced by it) as a
 * fallback: sysmon.log still captures everything even if the daemon binary
 * isn't installed yet, is mid-restart, or the shell call itself fails for
 * any reason. Forwarding is therefore additive, never a single point of
 * failure for visibility.
 *
 * The CLI forward itself is gated behind [MaxManagerProps.Conf.DETAILED_LOG]
 * (off by default -- see the Settings screen's "Detailed activity log"
 * toggle). stderr/sysmon.log logging above is NOT gated and always happens,
 * so basic debuggability never regresses; only the extra per-process-spawn
 * forward into the shared MaxManager.log is opt-in.
 */
object AppMonitorLogger {
    private const val TAG = "appmonitor"

    private const val LEVEL_DEBUG = 0
    private const val LEVEL_INFO = 1
    private const val LEVEL_WARN = 2
    private const val LEVEL_ERROR = 3
    private const val LEVEL_FATAL = 4

    fun d(message: String) = log(LEVEL_DEBUG, message, null)

    fun i(message: String) = log(LEVEL_INFO, message, null)

    fun w(message: String, throwable: Throwable? = null) = log(LEVEL_WARN, message, throwable)

    fun e(message: String, throwable: Throwable? = null) = log(LEVEL_ERROR, message, throwable)

    fun fatal(message: String, throwable: Throwable? = null) = log(LEVEL_FATAL, message, throwable)

    private fun log(level: Int, message: String, throwable: Throwable?) {
        // Local stderr copy first and unconditionally -- this must never be
        // skipped just because the CLI forward below fails, is slow, or is
        // disabled by the detailed-log toggle.
        System.err.println("[$TAG] ${levelTag(level)}: $message")
        throwable?.printStackTrace()

        if (PropertyUtils.get(MaxManagerProps.Conf.DETAILED_LOG) != "1") return

        runCatching {
            val summary = buildSummary(message, throwable)
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "sh", "-c",
                    "'${MaxManagerPaths.SERVICE_BIN}' --log '$TAG' '$level' '$summary' >/dev/null 2>&1"
                )
            )
            process.waitFor()
            process.destroy()
        }
    }

    /**
     * Collapses the message (and, if present, the throwable's type/message)
     * into a single shell-safe log line. Kept to one line on purpose: the
     * native side (write2file in SystemLogger.c) writes one log entry per
     * line, so an embedded newline from a raw stack trace would corrupt the
     * unified log's line-based format. The full stack trace still goes to
     * stderr above for local debugging -- this is a one-line summary only.
     */
    private fun buildSummary(message: String, throwable: Throwable?): String {
        val withCause = if (throwable != null) {
            "$message :: ${throwable.javaClass.simpleName}: ${throwable.message}"
        } else {
            message
        }
        return withCause
            .replace("\n", " | ")
            .replace("\r", "")
            .replace("'", "'\\''")
    }

    private fun levelTag(level: Int) = when (level) {
        LEVEL_DEBUG -> "D"
        LEVEL_INFO -> "I"
        LEVEL_WARN -> "W"
        LEVEL_ERROR -> "E"
        LEVEL_FATAL -> "F"
        else -> "?"
    }
}
