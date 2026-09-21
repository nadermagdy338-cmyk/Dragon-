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

package nd.max.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.util.EventLog
import nd.max.MaxManagerPaths

/**
 * Live logcat viewer, adapted from ZKM's LogsView (LogsViewUtils.kt +
 * LogsViewViewModel.kt), but not a straight port: ZKM tails the log with a
 * plain `ProcessBuilder("logcat", ...)`, which only sees what the app's own
 * (non-root) process is allowed to read. This drives the same stream through
 * a *dedicated* libsu [Shell] instance instead - giving it the kernel/crash/
 * radio buffers a rooted read can see - while deliberately NOT reusing
 * MaxManager's shared root shell: `logcat -v threadtime` never terminates on
 * its own, and running it on the shared session would wedge every other
 * screen's Shell.cmd() calls behind it for as long as this screen is open.
 * A private Shell is opened on [start] and closed on [onCleared]/buffer
 * change, which also tears down the underlying logcat process with it.
 */
class LogsViewerViewModel : ViewModel() {

    companion object {
        private const val MAX_DISPLAYED = 2000
        private const val MAX_IN_MEMORY = 20_000
        private const val FLUSH_INTERVAL_MS = 300L

        private val LOG_PATTERN = Regex(
            """^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([A-Z])\s+(.*?):\s?(.*)$"""
        )

        // Matches "2026-08-27 10:15:32 I MaxManager: EVENT=... key=value" --
        // the exact "%s %s %s: %s\n" format log_zenith()/external_log() write
        // in SystemLogger.c (see AppMonitorLogger.kt / EventLog.kt for the
        // Kotlin-side callers that forward into the same file).
        private val UNIFIED_LOG_PATTERN = Regex(
            """^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+([DIWEF])\s+(\S+?):\s?(.*)$"""
        )
        private val EVENT_TYPE_PATTERN = Regex("""EVENT=(\S+)""")
        private val SWITCH_ID_PATTERN = Regex("""sw=(\S+)""")
    }

    enum class LogLevel(val letter: String, val color: Color, val displayName: String) {
        VERBOSE("V", Color(0xFF9AA0A6), "Verbose"),
        DEBUG("D", Color(0xFF4CC9F0), "Debug"),
        INFO("I", Color(0xFF00C853), "Info"),
        WARN("W", Color(0xFFFFB300), "Warning"),
        ERROR("E", Color(0xFFFF1744), "Error"),
        ASSERT("A", Color(0xFFD500F9), "Assert");

        companion object {
            fun fromLetter(letter: String): LogLevel = entries.find { it.letter == letter.uppercase() } ?: VERBOSE
        }
    }

    enum class LogBuffer(val arg: String, val displayName: String) {
        MAIN("main", "Main"),
        SYSTEM("system", "System"),
        CRASH("crash", "Crash"),
        KERNEL("kernel", "Kernel"),
        EVENTS("events", "Events"),
        RADIO("radio", "Radio")
    }

    /** Which log this screen is currently showing. */
    enum class ViewerMode { LOGCAT, UNIFIED }

    /**
     * Levels as written by log_zenith()/external_log() (SystemLogger.c's
     * level_str[] array: D/I/W/E/F). Deliberately separate from [LogLevel]
     * above -- that enum's V/A letters don't exist in MaxManager.log, and
     * conflating the two domains risks silently mislabeling a FATAL line.
     */
    enum class UnifiedLogLevel(val letter: String, val color: Color, val displayName: String) {
        DEBUG("D", Color(0xFF4CC9F0), "Debug"),
        INFO("I", Color(0xFF00C853), "Info"),
        WARN("W", Color(0xFFFFB300), "Warning"),
        ERROR("E", Color(0xFFFF1744), "Error"),
        FATAL("F", Color(0xFFD500F9), "Fatal");

        companion object {
            fun fromLetter(letter: String): UnifiedLogLevel = entries.find { it.letter == letter.uppercase() } ?: INFO
        }
    }

    /**
     * The "tag" field of a MaxManager.log line identifies which of the three
     * processes wrote it: the native daemon always writes the fixed LOG_TAG
     * ("MaxManager"), while AppMonitorLogger.kt and EventLog.kt forward
     * through the same `--log` CLI hook with their own tags ("appmonitor",
     * "ui") -- see external_log() in SystemLogger.c and CLIUtility.c's
     * handle_log(). Any other tag value falls into OTHER rather than being
     * dropped, so a future/unexpected source is still visible, just
     * unlabeled.
     */
    enum class LogSource(val tag: String, val displayName: String) {
        DAEMON("MaxManager", "Daemon"),
        APPMONITOR("appmonitor", "AppMonitor"),
        UI("ui", "UI"),
        OTHER("", "Other");

        companion object {
            fun fromTag(tag: String): LogSource = entries.find { it.tag == tag } ?: OTHER
        }
    }

    data class UnifiedLogEntry(
        val id: Long,
        val timestamp: String,
        val level: UnifiedLogLevel,
        val source: LogSource,
        val rawTag: String,
        val eventType: String?,
        val switchId: String?,
        val message: String,
        val raw: String
    )

    data class LogEntry(
        val id: Long,
        val date: String,
        val time: String,
        val pid: String,
        val tid: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val raw: String
    )

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var selectedLevels by mutableStateOf(LogLevel.entries.toSet())
        private set
    var selectedBuffers by mutableStateOf(setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH))
        private set
    var showPid by mutableStateOf(false)
        @JvmName("setShowPidState") private set
    var showTid by mutableStateOf(false)
        @JvmName("setShowTidState") private set
    var displayedLogs by mutableStateOf<List<LogEntry>>(emptyList())
        private set
    var totalLineCount by mutableStateOf(0)
        private set

    // --- Unified (MaxManager.log) tab state ---
    var viewerMode by mutableStateOf(ViewerMode.LOGCAT)
        @JvmName("setViewerModeState") private set
    var unifiedAvailable by mutableStateOf<Boolean?>(null)
        private set
    var unifiedSearchQuery by mutableStateOf("")
        private set
    var selectedUnifiedLevels by mutableStateOf(UnifiedLogLevel.entries.toSet())
        private set
    var selectedSources by mutableStateOf(LogSource.entries.toSet())
        private set
    var unifiedDisplayedLogs by mutableStateOf<List<UnifiedLogEntry>>(emptyList())
        private set
    var unifiedTotalLineCount by mutableStateOf(0)
        private set

    private val allLogs = Collections.synchronizedList(ArrayList<LogEntry>())
    private val pendingBatch = Collections.synchronizedList(ArrayList<LogEntry>())
    private var idCounter = 0L
    private var logShell: Shell? = null
    private var flushJob: Job? = null
    private var started = false

    private val allUnifiedLogs = Collections.synchronizedList(ArrayList<UnifiedLogEntry>())
    private val pendingUnifiedBatch = Collections.synchronizedList(ArrayList<UnifiedLogEntry>())
    private var unifiedIdCounter = 0L
    private var unifiedShell: Shell? = null
    private var unifiedFlushJob: Job? = null

    /** Idempotent - safe to call from LaunchedEffect(Unit) on every recomposition-safe entry. */
    fun start() {
        if (started) return
        started = true
        restartStream()
    }

    /**
     * Switches between the raw Android logcat tail and the MaxManager.log
     * unified-log tail. Only one stream runs at a time -- switching away
     * tears the other one's dedicated Shell down rather than leaving it
     * running in the background. restartStream()/restartUnifiedStream() are
     * both safe to call repeatedly (each tears itself down before
     * reinitializing), so no extra "already started" bookkeeping is needed
     * here beyond the one-time started flag in start().
     */
    fun setViewerMode(mode: ViewerMode) {
        if (mode == viewerMode) return
        viewerMode = mode
        when (mode) {
            ViewerMode.LOGCAT -> {
                stopUnifiedStream()
                restartStream()
            }
            ViewerMode.UNIFIED -> {
                stopStream()
                flushJob?.cancel()
                restartUnifiedStream()
            }
        }
    }

    private fun restartStream() {
        stopStream()
        synchronized(allLogs) { allLogs.clear() }
        synchronized(pendingBatch) { pendingBatch.clear() }
        displayedLogs = emptyList()
        totalLineCount = 0
        idCounter = 0

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shell = Shell.Builder.create().build()
                logShell = shell

                val bufferArgs = selectedBuffers.joinToString(" ") { "-b ${it.arg}" }
                val command = "logcat -v threadtime $bufferArgs"

                val sink = object : ArrayList<String>() {
                    override fun add(element: String): Boolean {
                        handleLine(element)
                        return true
                    }
                }

                isAvailable = true
                shell.newJob().add(command).to(sink).submit { }
            } catch (e: Exception) {
                isAvailable = false
                EventLog.error("LogsViewer", "start_logcat_stream", e)
            }
        }

        flushJob?.cancel()
        flushJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushPending()
            }
        }
    }

    private fun handleLine(line: String) {
        if (isPaused) return
        val entry = parseLine(line) ?: return
        pendingBatch.add(entry)
    }

    private fun parseLine(line: String): LogEntry? {
        val match = LOG_PATTERN.find(line)
        if (match == null) {
            if (line.isBlank() || line.startsWith("---------")) return null
            return LogEntry(
                id = idCounter++, date = "", time = "", pid = "?", tid = "?",
                level = LogLevel.VERBOSE, tag = "System", message = line, raw = line
            )
        }
        val (date, time, pid, tid, levelStr, tag, msg) = match.destructured
        return LogEntry(
            id = idCounter++,
            date = date,
            time = time,
            pid = pid,
            tid = tid,
            level = LogLevel.fromLetter(levelStr),
            tag = tag.trim(),
            message = msg,
            raw = line
        )
    }

    private fun flushPending() {
        val batch = synchronized(pendingBatch) {
            if (pendingBatch.isEmpty()) return
            val copy = ArrayList(pendingBatch)
            pendingBatch.clear()
            copy
        }
        synchronized(allLogs) {
            allLogs.addAll(batch)
            if (allLogs.size > MAX_IN_MEMORY) {
                allLogs.subList(0, allLogs.size - MAX_IN_MEMORY).clear()
            }
        }
        totalLineCount = allLogs.size
        applyFilter()
    }

    private fun applyFilter() {
        val query = searchQuery.trim().lowercase()
        val levels = selectedLevels
        val snapshot = synchronized(allLogs) { ArrayList(allLogs) }
        displayedLogs = snapshot.asReversed().asSequence()
            .filter { it.level in levels }
            .filter { query.isEmpty() || it.tag.lowercase().contains(query) || it.message.lowercase().contains(query) }
            .take(MAX_DISPLAYED)
            .toList()
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        applyFilter()
    }

    fun toggleLevel(level: LogLevel) {
        val updated = if (level in selectedLevels) selectedLevels - level else selectedLevels + level
        if (updated.isEmpty()) return
        selectedLevels = updated
        applyFilter()
    }

    fun setBuffers(buffers: Set<LogBuffer>) {
        if (buffers.isEmpty() || buffers == selectedBuffers) return
        selectedBuffers = buffers
        restartStream()
    }

    fun setShowPid(value: Boolean) {
        showPid = value
    }

    fun setShowTid(value: Boolean) {
        showTid = value
    }

    fun togglePause() {
        isPaused = !isPaused
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("logcat -c").exec()
            synchronized(allLogs) { allLogs.clear() }
            synchronized(pendingBatch) { pendingBatch.clear() }
            totalLineCount = 0
            withContext(Dispatchers.Main) { displayedLogs = emptyList() }
        }
    }

    fun saveLogs(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val target = File(context.cacheDir, "MaxManager_Logcat_$timestamp.txt")
                val snapshot = synchronized(allLogs) { ArrayList(allLogs) }
                FileWriter(target).use { writer ->
                    snapshot.forEach { writer.append(it.raw).append('\n') }
                }
                target
            } catch (e: Exception) {
                EventLog.error("LogsViewer", "save_logcat_export", e)
                null
            }
            withContext(Dispatchers.Main) { onResult(file) }
        }
    }

    /**
     * Closes the dedicated shell opened in [restartStream], which tears down
     * the su process (and the `logcat` child riding on it) with it. Never
     * touches MaxManager's shared root shell.
     */
    private fun stopStream() {
        try {
            logShell?.close()
        } catch (e: Exception) {
            // no-op - already dead
        }
        logShell = null
    }

    // --- Unified (MaxManager.log) tab ---

    /**
     * Tails MaxManager.log with `tail -F` (follow-with-retry) on a
     * dedicated, private root Shell -- same rationale as [restartStream]:
     * a long-lived `tail -F` must never share MaxManager's common root
     * session, or every other screen's Shell.cmd() would queue behind it.
     * `-n 1000` seeds the view with recent history instead of starting
     * empty, and `-F` (rather than plain `-f`) keeps following correctly
     * across the rotate_log_if_needed() rename in FileHandler.c.
     */
    private fun restartUnifiedStream() {
        stopUnifiedStream()
        synchronized(allUnifiedLogs) { allUnifiedLogs.clear() }
        synchronized(pendingUnifiedBatch) { pendingUnifiedBatch.clear() }
        unifiedDisplayedLogs = emptyList()
        unifiedTotalLineCount = 0
        unifiedIdCounter = 0

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shell = Shell.Builder.create().build()
                unifiedShell = shell

                val sink = object : ArrayList<String>() {
                    override fun add(element: String): Boolean {
                        handleUnifiedLine(element)
                        return true
                    }
                }

                unifiedAvailable = true
                shell.newJob().add("tail -F -n 1000 '${MaxManagerPaths.MAXMANAGER_LOG}'").to(sink).submit { }
            } catch (e: Exception) {
                unifiedAvailable = false
                EventLog.error("LogsViewer", "start_unified_stream", e)
            }
        }

        unifiedFlushJob?.cancel()
        unifiedFlushJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushPendingUnified()
            }
        }
    }

    private fun handleUnifiedLine(line: String) {
        if (isPaused) return
        val entry = parseUnifiedLine(line) ?: return
        pendingUnifiedBatch.add(entry)
    }

    private fun parseUnifiedLine(line: String): UnifiedLogEntry? {
        if (line.isBlank()) return null
        val match = UNIFIED_LOG_PATTERN.find(line) ?: return null
        val (timestamp, levelStr, tag, message) = match.destructured
        return UnifiedLogEntry(
            id = unifiedIdCounter++,
            timestamp = timestamp,
            level = UnifiedLogLevel.fromLetter(levelStr),
            source = LogSource.fromTag(tag),
            rawTag = tag,
            eventType = EVENT_TYPE_PATTERN.find(message)?.groupValues?.get(1),
            switchId = SWITCH_ID_PATTERN.find(message)?.groupValues?.get(1),
            message = message,
            raw = line
        )
    }

    private fun flushPendingUnified() {
        val batch = synchronized(pendingUnifiedBatch) {
            if (pendingUnifiedBatch.isEmpty()) return
            val copy = ArrayList(pendingUnifiedBatch)
            pendingUnifiedBatch.clear()
            copy
        }
        synchronized(allUnifiedLogs) {
            allUnifiedLogs.addAll(batch)
            if (allUnifiedLogs.size > MAX_IN_MEMORY) {
                allUnifiedLogs.subList(0, allUnifiedLogs.size - MAX_IN_MEMORY).clear()
            }
        }
        unifiedTotalLineCount = allUnifiedLogs.size
        applyUnifiedFilter()
    }

    /**
     * Free-text search matches the whole raw line, not just the message --
     * this is deliberate so searching a switch id ("sw-1798...") or an
     * EVENT type ("APPLY_FAILED") works the same way whether the person
     * types the whole token or just pastes it in from another log line,
     * without needing separate dedicated search fields for each.
     */
    private fun applyUnifiedFilter() {
        val query = unifiedSearchQuery.trim().lowercase()
        val levels = selectedUnifiedLevels
        val sources = selectedSources
        val snapshot = synchronized(allUnifiedLogs) { ArrayList(allUnifiedLogs) }
        unifiedDisplayedLogs = snapshot.asReversed().asSequence()
            .filter { it.level in levels }
            .filter { it.source in sources }
            .filter { query.isEmpty() || it.raw.lowercase().contains(query) }
            .take(MAX_DISPLAYED)
            .toList()
    }

    fun onUnifiedSearchQueryChange(query: String) {
        unifiedSearchQuery = query
        applyUnifiedFilter()
    }

    /**
     * «المشاكل فقط» **مشتقّة** لا مخزَّنة: هي بالضبط حالة أن تكون مستويات الطبع كلها غير مختارة.
     *
     * ولماذا لا عَلَم مستقل: عَلَمٌ منفصل يمكن أن يخالف المستويات المعروضة، فيقول الزرّ «معمَّم»
     * والقائمة لا تُظهر إلا التحذيرات. والمشتق لا يخالف ما يُعرض لأنه منه.
     */
    val problemsOnly: Boolean
        get() = selectedUnifiedLevels.none { it == UnifiedLogLevel.DEBUG || it == UnifiedLogLevel.INFO }

    /**
     * يُبدّل بين «كل المستويات» و«W/E/F وحدها»: فشلٌ يُكتب I(معلوماتي) لا يظهر هنا، ولذلك
     * صارت أسطر `PERAPP_KNOB` الفاشلة تُكتب W في المحرّك — المستوى من النتيجة لا من العادة.
     */
    fun toggleProblemsOnly() {
        selectedUnifiedLevels = if (problemsOnly) {
            UnifiedLogLevel.entries.toSet()
        } else {
            setOf(UnifiedLogLevel.WARN, UnifiedLogLevel.ERROR, UnifiedLogLevel.FATAL)
        }
        applyUnifiedFilter()
    }

    fun toggleUnifiedLevel(level: UnifiedLogLevel) {
        val updated = if (level in selectedUnifiedLevels) selectedUnifiedLevels - level else selectedUnifiedLevels + level
        if (updated.isEmpty()) return
        selectedUnifiedLevels = updated
        applyUnifiedFilter()
    }

    fun toggleSource(source: LogSource) {
        val updated = if (source in selectedSources) selectedSources - source else selectedSources + source
        if (updated.isEmpty()) return
        selectedSources = updated
        applyUnifiedFilter()
    }

    /**
     * Calls the daemon's own `--clearlogs` CLI (see clearlogs() in
     * CLIUtility.c) rather than rm'ing the file directly from here, so the
     * rotated .1 backups get cleaned up too and the native side's broadcast
     * hook still fires. tail -F transparently reattaches once log_zenith()
     * recreates the file on its next write.
     */
    fun clearUnifiedLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("'${MaxManagerPaths.SERVICE_BIN}' --clearlogs").exec()
            synchronized(allUnifiedLogs) { allUnifiedLogs.clear() }
            synchronized(pendingUnifiedBatch) { pendingUnifiedBatch.clear() }
            unifiedTotalLineCount = 0
            withContext(Dispatchers.Main) { unifiedDisplayedLogs = emptyList() }
        }
    }

    /**
     * Exports the actual on-disk MaxManager.log (read fresh via root, not
     * just the in-memory/MAX_IN_MEMORY-capped buffer, so nothing gets
     * silently truncated) as a zip, ready to share.
     */
    fun exportUnifiedLogs(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val rawContent = Shell.cmd("cat '${MaxManagerPaths.MAXMANAGER_LOG}'").exec().out.joinToString("\n")
                val target = File(context.cacheDir, "MaxManager_Log_$timestamp.zip")
                ZipOutputStream(target.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("MaxManager.log"))
                    zip.write(rawContent.toByteArray())
                    zip.closeEntry()
                }
                target
            } catch (e: Exception) {
                EventLog.error("LogsViewer", "export_unified_log", e)
                null
            }
            withContext(Dispatchers.Main) { onResult(file) }
        }
    }

    /**
     * Closes the dedicated shell opened in [restartUnifiedStream]; mirrors
     * [stopStream] for the unified tab.
     */
    private fun stopUnifiedStream() {
        try {
            unifiedShell?.close()
        } catch (e: Exception) {
            // no-op - already dead
        }
        unifiedShell = null
    }

    override fun onCleared() {
        super.onCleared()
        flushJob?.cancel()
        unifiedFlushJob?.cancel()
        stopStream()
        stopUnifiedStream()
    }
}
