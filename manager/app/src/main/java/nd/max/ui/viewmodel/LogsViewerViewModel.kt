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
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import nd.max.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import nd.max.core.hardware.RootFileAccess
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
import nd.max.core.diagnostics.DeviceFacts
import nd.max.core.diagnostics.LogArea
import nd.max.core.diagnostics.LogDiagnosticReport
import nd.max.core.diagnostics.LogEventLine
import nd.max.core.diagnostics.LogEventParser
import nd.max.core.diagnostics.LogField
import nd.max.core.diagnostics.LogHeader
import nd.max.core.diagnostics.LogObservation
import nd.max.core.diagnostics.LogSettingsDigest
import nd.max.core.diagnostics.LogTargetHistory
import nd.max.core.diagnostics.LogTargetSummary
import nd.max.core.diagnostics.LogVerdict
import nd.max.core.diagnostics.ReportLine
import nd.max.ui.util.EventLog
import nd.max.ui.util.PropertyUtils
import nd.max.MaxManagerPaths
import nd.max.MaxManagerProps

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

        /**
         * حدود ضبط ملف السجل — مطابقة لما تفرضه `max_log_file_bytes()` في الأصل (64KB..16MB).
         *
         * والتكرار مقصود عند حدود نظام آخر: القيمة هي الواجهة نفسها، والأصل يُعيد أيّ شيء خارج
         * المدى إلى الحدّ المُصرَّف. فلو عرضنا قيمة خارج المدى لَعرضنا رقمًا لا ينفّذه أحد.
         */
        private const val LOG_MAX_KB_FLOOR = 64
        private const val LOG_MAX_KB_CEIL = 16384
        private const val DEFAULT_LOG_MAX_KB = 3072
        private const val LOG_LEVEL_DEBUG = 0

        // الأنماط المرجعية انتقلت إلى `LogsLineParser` (ملف مستقل، يُختبَر في JVM مباشرة) —
        // وهذا الملف كان يحملها حتى صار فوق حدّ الطول المسموح.
    }

    /**
     * مستويات `logcat` كما تُعرض: الحرف كما يكتبه النظام، ولونٌ للتمييز البصري.
     *
     * والألوان **من لوحة MaxManager** لا من لوحة أحد: المستوى حقيقة عن النظام، واختيار
     * اللون تفضيل تصميمي يملكه هذا المشروع. والأسماء موارد نصّية (`max_log_level_*`) لأنها
     * كلام يقرأه المستخدم لا معرفًا يقرأه الكود.
     */
    enum class LogLevel(val letter: String, val color: Color, @StringRes val labelRes: Int) {
        VERBOSE("V", Color(0xFF94A3B8), R.string.max_log_level_verbose),
        DEBUG("D", Color(0xFF22D3EE), R.string.max_log_level_debug),
        INFO("I", Color(0xFF34D399), R.string.max_log_level_info),
        WARN("W", Color(0xFFFBBF24), R.string.max_log_level_warn),
        ERROR("E", Color(0xFFF87171), R.string.max_log_level_error),
        ASSERT("A", Color(0xFFC084FC), R.string.max_log_level_assert);

        companion object {
            fun fromLetter(letter: String): LogLevel =
                entries.firstOrNull { it.letter.equals(letter, ignoreCase = true) } ?: VERBOSE
        }
    }

    /** مخازن `logcat -b`: قيمة `arg` حقيقة عن النظام، والاسم مورد نصّي. */
    enum class LogBuffer(val arg: String, @StringRes val labelRes: Int) {
        MAIN("main", R.string.max_log_buffer_main),
        SYSTEM("system", R.string.max_log_buffer_system),
        CRASH("crash", R.string.max_log_buffer_crash),
        KERNEL("kernel", R.string.max_log_buffer_kernel),
        EVENTS("events", R.string.max_log_buffer_events),
        RADIO("radio", R.string.max_log_buffer_radio)
    }

    /** Which log this screen is currently showing. */
    enum class ViewerMode { LOGCAT, UNIFIED }

    /**
     * كيف يقرأ محلّل السجل التبويب الموحّد.
     *
     * و`TARGETS` ليس عرضًا آخر للسطور بل **سؤال آخر**: «ما آخر ما عُرف عن هذا المقبض؟» بدل «ما
     * الذي حدث الآن؟». وهذا السؤال هو الذي يجيب عن «لماذا لا تعمل هذه الميزة» بلا تصفّح مئات
     * السطور بحثًا عن آخر سطر يخصّ المقبض نفسه.
     */
    enum class UnifiedView { TIMELINE, TARGETS }

    /**
     * Levels as written by log_zenith()/external_log() (SystemLogger.c's
     * level_str[] array: D/I/W/E/F). Deliberately separate from [LogLevel]
     * above -- that enum's V/A letters don't exist in MaxManager.log, and
     * conflating the two domains risks silently mislabeling a FATAL line.
     */
    /**
     * مستويات السجل الموحّد: نفس لوحة [LogLevel] مضافًا إليها `FATAL` (لا يقابله مستوى في
     * `logcat`، ويأتي من محلّل السجل الموحّد).
     */
    enum class UnifiedLogLevel(val letter: String, val color: Color, @StringRes val labelRes: Int) {
        DEBUG("D", Color(0xFF22D3EE), R.string.max_log_level_debug),
        INFO("I", Color(0xFF34D399), R.string.max_log_level_info),
        WARN("W", Color(0xFFFBBF24), R.string.max_log_level_warn),
        ERROR("E", Color(0xFFF87171), R.string.max_log_level_error),
        FATAL("F", Color(0xFFC084FC), R.string.max_log_level_fatal);

        companion object {
            fun fromLetter(letter: String): UnifiedLogLevel =
                entries.firstOrNull { it.letter.equals(letter, ignoreCase = true) } ?: INFO
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
    enum class LogSource(val tag: String, @StringRes val labelRes: Int) {
        DAEMON("MaxManager", R.string.max_log_source_daemon),
        APPMONITOR("appmonitor", R.string.max_log_source_appmonitor),
        UI("ui", R.string.max_log_source_ui),
        OTHER("", R.string.max_log_source_other);

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
        val raw: String,
        /** الحدث مفكوكًا إلى حقول، أو `null` حين لا يكون السطر حدثًا. */
        val event: LogEventLine? = null,
        val area: LogArea = LogArea.OTHER,
        val verdict: LogVerdict = LogVerdict.UNKNOWN,
    ) {
        /** المقبض/العقدة التي يخصّها السطر، إن خصّ واحدًا. */
        val target: String? get() = event?.target

        /** الحقول كما هي، لإظهارها مفصّلة عند الطلب. */
        val fields: List<LogField> get() = event?.fields.orEmpty()

        /** الوقت وحده، وقت كتابة السطر (بلا تاريخ) — كما يُعرض وكما يُكتب في التقرير. */
        val time: String get() = timestamp.substringAfter(' ')
    }

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
    var unifiedView by mutableStateOf(UnifiedView.TIMELINE)
        @JvmName("setUnifiedViewState") private set
    var selectedAreas by mutableStateOf(LogArea.entries.toSet())
        private set

    /**
     * «الفشل فقط» — **حكم مشتقّ من الحقول** لا من مستوى السطر.
     *
     * وهذا هو الفرق المقصود: سطر `PERAPP_KNOB outcome=not-verified` يُكتب `W` اليوم، ولكنه كان
     * يُكتب `I` قبل جولة `PERAPP-CONTROL-03` — والحكم من المستوى وحده كان سيتغيّر بتغيّر عادة
     * كتابة السطر لا بتغيّر الحقيقة. و`verdict` يحمل الحقيقة.
     */
    var failuresOnly by mutableStateOf(false)
        private set

    /** السطر الذي طلب المستخدم رؤية حقوله كاملة، أو `null`. */
    var expandedId by mutableStateOf<Long?>(null)
        private set

    /** المقبض المختار من عرض «المقابض»، يُرشّح الخط الزمني وحده. */
    var focusedTarget by mutableStateOf<String?>(null)
        private set

    /** ملخّص كل مقبض ظهر في النافذة المحمّلة — يُحسب مع كل تصفية. */
    var targetSummaries by mutableStateOf<List<LogTargetSummary>>(emptyList())
        private set

    /**
     * حدّ حجم ملف السجل بالكيلوبايت كما هو مضبوط الآن (يُقرأ من الخاصية عند البناء).
     *
     * ولماذا `@JvmName` على المُسنِد: اسمه المولَّد `setLogMaxKb(I)V` مطابق تمامًا لدالة
     * الأمر [setLogMaxKb] — وهو تصادم على توقيع JVM لا يمنعه اختلاف النطاق (خاصّ مقابل عام)،
     * فيوقف الترجمة إلى dex. وإعادة التسمية هنا هي نفس قاعدة [viewerMode] و[unifiedView]
     * أعلاه: الخاصية تُقرأ باسمها، وكل كتابة تمرّ بدالة الأمر لا بالمُسنِد.
     */
    var logMaxKb by mutableStateOf(0)
        @JvmName("setLogMaxKbState") private set

    /** أدنى مستوى يُكتب من التطبيق (0=DEBUG .. 4=FATAL) — ونفس سبب `@JvmName` أعلاه. */
    var logMinLevel by mutableStateOf(0)
        @JvmName("setLogMinLevelState") private set

    private val allLogs = Collections.synchronizedList(ArrayList<LogEntry>())
    /** سطور خام قبل التحليل الدفعي — نفس نافذة الإفراغ (٣٠٠ مللي) تحدّ حجمها. */
    private val pendingRawLines = Collections.synchronizedList(ArrayList<String>())
    private val pendingRawUnifiedLines = Collections.synchronizedList(ArrayList<String>())

    // عدّادات المعرّفات صارت ملك `LogsLineParser` مع التحليل الذي يُسندها.
    private var logShell: Shell? = null
    private var flushJob: Job? = null
    private var started = false

    private val allUnifiedLogs = Collections.synchronizedList(ArrayList<UnifiedLogEntry>())

    private var unifiedShell: Shell? = null
    private var unifiedFlushJob: Job? = null

    /** Idempotent - safe to call from LaunchedEffect(Unit) on every recomposition-safe entry. */
    fun start() {
        if (started) return
        started = true
        readLogSettings()
        restartStream()
    }

    /**
     * إعدادات الملف تُقرأ من الخصائص وقت البدء — لا تُخزَّن نسخة ثانية في التطبيق.
     *
     * ولماذا القراءة لا التخزين: الخاصية يقرأها الأصل (FileHandler.c / SystemLogger.c) وهي
     * مصدر الحقيقة، ونسخة في الـViewModel قد تنحرف عنها (خصائص تُعاد ضبطها بعد تحديث الوحدة،
     * أو تُغيّر من ملف مثلًا) فيُعرض رقم لا يطابق ما يجري.
     */
    private fun readLogSettings() {
        logMaxKb = PropertyUtils.get(MaxManagerProps.Conf.LOG_MAX_KB)
            .toIntOrNull()?.takeIf { it in LOG_MAX_KB_FLOOR..LOG_MAX_KB_CEIL } ?: DEFAULT_LOG_MAX_KB
        logMinLevel = PropertyUtils.get(MaxManagerProps.Conf.LOG_MIN_LEVEL)
            .toIntOrNull()?.takeIf { it in 0..4 } ?: LOG_LEVEL_DEBUG
    }

    fun setLogMaxKb(kb: Int) {
        if (kb !in LOG_MAX_KB_FLOOR..LOG_MAX_KB_CEIL) return
        val previous = logMaxKb
        logMaxKb = kb
        PropertyUtils.set(MaxManagerProps.Conf.LOG_MAX_KB, kb.toString())
        EventLog.userAction("LogsViewer", "log_max_kb", previous.toString(), kb.toString())
    }

    fun setLogMinLevel(level: Int) {
        if (level !in 0..4) return
        val previous = logMinLevel
        logMinLevel = level
        PropertyUtils.set(MaxManagerProps.Conf.LOG_MIN_LEVEL, level.toString())
        EventLog.userAction("LogsViewer", "log_min_level", previous.toString(), level.toString())
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
        synchronized(pendingRawLines) { pendingRawLines.clear() }
        displayedLogs = emptyList()
        totalLineCount = 0
        LogsLineParser.resetLogcatIds()

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

    /**
     * استقبال سطر logcat — **يُخزَّن خامًا** ويُحلَّل في نافذة الإفراغ.
     *
     * وكان التحليل يقع هنا سطرًا بسطر (`LOG_PATTERN` على كل سطر)، والسطور تصل بعشرات في الثانية
     * ⇒ آلاف المطابقات وعشرات آلاف الكائنات الوسيطة في الدقيقة على خيط لا يتوقف. وبعد النقل
     * الدفعي إلى Rust تصير المطابقات كلها في **نداء واحد لكل نافذة (٣٠٠ مللي)** — أي مرّتان إلى
     * ثلاث في الثانية. والوقف مؤقتًا يبقى عند لحظة الوصول كما كان (لا تُجمَّع سطور مُوقَفة).
     */
    private fun handleLine(line: String) {
        if (isPaused) return
        pendingRawLines.add(line)
    }

    private fun flushPending() {
        val raw = synchronized(pendingRawLines) {
            if (pendingRawLines.isEmpty()) return
            val copy = ArrayList(pendingRawLines)
            pendingRawLines.clear()
            copy
        }
        val batch = LogsLineParser.parseLogcatBatch(raw)
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
            synchronized(pendingRawLines) { pendingRawLines.clear() }
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
        synchronized(pendingRawUnifiedLines) { pendingRawUnifiedLines.clear() }
        unifiedDisplayedLogs = emptyList()
        unifiedTotalLineCount = 0
        LogsLineParser.resetUnifiedIds()
        // ملخّص المقابض وتركيزه يخصّان ما كان محمّلًا؛ إبقاؤهما بعد تفريغ النافذة يجعل الشاشة
        // تُظهر «آخر ما عُرف» عن سطور لم تبقَ.
        targetSummaries = emptyList()
        focusedTarget = null
        expandedId = null

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

    /** سطر السجلّ الموقَّع — يُخزَّن خامًا ويُحلَّل في نافذة الإفراغ (كما في logcat أعلاه). */
    private fun handleUnifiedLine(line: String) {
        if (isPaused) return
        pendingRawUnifiedLines.add(line)
    }

    private fun flushPendingUnified() {
        val raw = synchronized(pendingRawUnifiedLines) {
            if (pendingRawUnifiedLines.isEmpty()) return
            val copy = ArrayList(pendingRawUnifiedLines)
            pendingRawUnifiedLines.clear()
            copy
        }
        val batch = LogsLineParser.parseUnifiedBatch(raw)
        synchronized(allUnifiedLogs) {
            allUnifiedLogs.addAll(batch)
            if (allUnifiedLogs.size > MAX_IN_MEMORY) {
                allUnifiedLogs.subList(0, allUnifiedLogs.size - MAX_IN_MEMORY).clear()
            }
        }
        unifiedTotalLineCount = allUnifiedLogs.size
        refreshTargetSummaries()
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
        val areas = selectedAreas
        val onlyFailures = failuresOnly
        val focused = focusedTarget
        val snapshot = synchronized(allUnifiedLogs) { ArrayList(allUnifiedLogs) }
        unifiedDisplayedLogs = snapshot.asReversed().asSequence()
            .filter { it.level in levels }
            .filter { it.source in sources }
            .filter { it.area in areas }
            .filter { !onlyFailures || it.verdict == LogVerdict.FAIL }
            .filter { focused == null || it.target == focused }
            .filter { query.isEmpty() || it.raw.lowercase().contains(query) }
            .take(MAX_DISPLAYED)
            .toList()
    }

    /**
     * يُعيد بناء ملخّص المقابض من **كل** ما حُمِّل — لا من المعروض ولا بمناسبة تغيّر تصفية.
     *
     * ولماذا هنا لا داخل `applyUnifiedFilter`: الملخّص لا يعتمد على أيّ مرشِّح، وحسابه لكل ضغطة
     * مفتاح في البحث كان يمرّ على عشرين ألف سطر من أجل نتيجة لا تتغيّر. ويُنادى من مسار الطيّ
     * (`Dispatchers.IO`) مرّة كل [FLUSH_INTERVAL_MS]، وهو نفس إيقاع وصول سطور جديدة.
     *
     * و`null` target مستبعَد: التجميع يصنع مدخلًا، ولا مدخل لعقدة غير مسمّاة.
     */
    private fun refreshTargetSummaries() {
        val snapshot = synchronized(allUnifiedLogs) { ArrayList(allUnifiedLogs) }
        targetSummaries = LogTargetHistory.summarise(
            snapshot.asSequence()
                .filter { it.target != null }
                .map { entry ->
                    LogObservation(
                        id = entry.id,
                        time = entry.time,
                        target = entry.target.orEmpty(),
                        verdict = entry.verdict,
                        reason = entry.event?.field("reason").orEmpty(),
                        expected = entry.event?.field("expected").orEmpty(),
                        live = entry.event?.field("live").orEmpty(),
                        source = entry.source.displayName,
                    )
                }
                .toList(),
        )
    }

    fun onUnifiedSearchQueryChange(query: String) {
        unifiedSearchQuery = query
        applyUnifiedFilter()
    }

    /**
     * «الفشل فقط» — يُبدّل تصفية الحكم المفهوم. الفشل يُشتقّ من الحقول والاسم والمستوى معًا
     * (انظر `LogVerdict`)، فلا يعتمد على أن يكون الكاتب قد اختار المستوى الصحيح.
     */
    fun toggleFailuresOnly() {
        failuresOnly = !failuresOnly
        applyUnifiedFilter()
    }

    /** تصفية بالميزة — والاختيار الفارغ ممنوع، لأن «لا ميزة معروضة» ليست حالة يفهمها المستخدم. */
    fun toggleArea(area: LogArea) {
        val updated = if (area in selectedAreas) selectedAreas - area else selectedAreas + area
        if (updated.isEmpty()) return
        selectedAreas = updated
        applyUnifiedFilter()
    }

    fun setUnifiedView(view: UnifiedView) {
        if (view == unifiedView) return
        unifiedView = view
        // تنقّل بين العرضين لا يُبقي سطرًا مفتوحًا في عرض آخر: الحقول المفتوحة تخصّ سطرًا في
        // الخط الزمني وحده.
        if (view == UnifiedView.TARGETS) expandedId = null
    }

    /** فتح/ضمّ حقول سطر. سطر واحد مفتوح في المرة — التفصيل يُقرأ، ولا يُقارن. */
    fun toggleExpanded(id: Long) {
        expandedId = if (expandedId == id) null else id
    }

    /**
     * التركيز على مقبض واحد من عرض «المقابض». الضغط على المقبض نفسه يُلغي التركيز، فيعود
     * الخط الزمني كما كان — بدل إجبار المستخدم على البحث عن زرّ "إلغاء" في مكان آخر.
     */
    fun focusTarget(target: String) {
        focusedTarget = if (focusedTarget == target) null else target
        applyUnifiedFilter()
    }

    fun clearFocus() {
        if (focusedTarget == null) return
        focusedTarget = null
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
     *
     * ويُستقبل `context` من الشاشة كما في [saveLogs] و[exportUnifiedLogs] و[shareDiagnosticBundle]:
     * إعادة كتابة الترويسة تقرأ اسم الحزمة من `packageManager`، وهذا الـViewModel ليس
     * `AndroidViewModel` — فلا سياق في متناوله، وأي اسم سياق غير معلَن هنا لا وجود له في هذا النطاق.
     */
    fun clearUnifiedLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("'${MaxManagerPaths.SERVICE_BIN}' --clearlogs").exec()
            synchronized(allUnifiedLogs) { allUnifiedLogs.clear() }
            synchronized(pendingRawUnifiedLines) { pendingRawUnifiedLines.clear() }
            unifiedTotalLineCount = 0
            // الترويسة تُعاد بعد المسح: الملف الذي يُرسَل لاحقًا يجب أن يحمل جهازه وإعداده،
            // وأمر المسح نفسه يُسجَّل فتُفهم الفجوة الزمنية في الملف.
            rewriteLogHeader(context.applicationContext)
            withContext(Dispatchers.Main) {
                unifiedDisplayedLogs = emptyList()
                // لا ملخّص ولا تركيز على ما مُحي: قناة الحالة تشير إلى سطور لم تعد موجودة.
                targetSummaries = emptyList()
                focusedTarget = null
                expandedId = null
            }
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
                // السجلّ كاملًا بقراءة واحدة عبر الطبقة الموحّدة بدل صدفة `cat`.
                val rawContent = RootFileAccess.read(MaxManagerPaths.MAXMANAGER_LOG).orEmpty()
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

    // --- التقرير التشخيصي -------------------------------------------------------

    /**
     * يبني **الحزمة**: هوية الجهاز والإعداد، ودليل القراءة والوحدات، وقاموس الرموز، وملخّص الفشل،
     * ثم السجل الخام كاملًا — ملفًا واحدًا يكفي وحده.
     *
     * وهذا هو معنى الطلب: بلا هذه الكتل يبقى الشارِك مضطرًّا لإرسال جهازه وإعداده وشرح رموزه،
     * فيصير التشخيص أسئلة. ومصدر السطور هو **النافذة المحمّلة**، ولذلك يُقال `truncated` حين
     * تمتلئ: تقرير مبنيّ على نافذة يجب أن يعلنها، وإلا قُرئ كأنه السجل كامل.
     *
     * والترتيب هو ترتيب [allUnifiedLogs] (**الأقدم أولًا**) لا ترتيب العرض المقلوب.
     */
    private fun bundleText(context: Context): String {
        val lines = synchronized(allUnifiedLogs) { ArrayList(allUnifiedLogs) }
        return LogDiagnosticReport.build(
            facts = deviceFacts(context),
            lines = lines.map { entry ->
                ReportLine(
                    time = entry.time,
                    level = entry.level.letter,
                    source = entry.source.displayName,
                    event = entry.eventType,
                    area = entry.area,
                    verdict = entry.verdict,
                    reason = entry.event?.field("reason").orEmpty(),
                    raw = entry.raw,
                )
            },
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            truncated = lines.size >= MAX_IN_MEMORY,
            settings = logSettingsDigest(),
            // الحزمة تشمل السجل كامًلا لا ذيله: تُرسَل وحدها فيجب أن تحمل ما يُحلّل.
            rawTailLimit = Int.MAX_VALUE,
            extraSections = listOfNotNull(perAppStatusSection()),
        )
    }

    /** إعداد التشغيل كما يُقرأ من التطبيق — نفس قائمة المراقب، ومن موضعها الواحد. */
    private fun logSettingsDigest(): List<Pair<String, String>> =
        LogSettingsDigest.of { key -> PropertyUtils.get(key) }

    /**
     * قناة حالة المقابض لكل تطبيق، مقطوعة إلى أسطر — إن وُجدت وقرئت.
     *
     * وتُدرَج في الحزمة لأنها الجواب المباشر عن «لماذا لم يعمل؟» لكل مقبض، وحجمها صفوف قليلة؛
     * وغيابها يُعبَّر عنه بغياب المقطع، فالحزمة بلا هذا المقطع تعني «لم تُقرأ» لا «لا مشاكل».
     */
    private fun perAppStatusSection(): Pair<String, List<String>>? = runCatching {
        val text = MaxManagerPaths.PER_APP_HW_STATUS
        val out = RootFileAccess.read(text)?.lines().orEmpty()
        val section = "per-app knob status" to out.filter { it.isNotBlank() }
        section.takeIf { it.second.isNotEmpty() }
    }.getOrNull()

    /**
     * يعيد كتابة ترويسة السجل بعد تفريغه.
     *
     * ولماذا يلزم: `--clearlogs` يمحو الملف بما فيه ترويسة التشغيل، فيصير الملف الذي يُرسَل بعد
     * التفريغ بلا جهاز ولا إعداد ولا معنى للرموز — أي أن أمرًا يبدو منزّهًا هو الذي يجعل التشخيص
     * من الملف وحده مستحيلًا.
     */
    private fun rewriteLogHeader(context: Context) {
        val facts = deviceFacts(context)
        EventLog.header(LogHeader.startupLines(facts, logSettingsDigest()))
        EventLog.userTriggered("LogsViewer", "log_cleared", null)
    }

    /**
     * حقائق الجهاز للتقرير — من مصادر **معلَنة** لا مُخمَّنة.
     *
     * `SOC_MODEL`/`SOC_MANUFACTURER` أُضيفا في API 31 و`minSdk` هنا 29، وقراءتهما على 29/30 ترمي
     * `NoSuchFieldError` — فالحماية صريحة كما في `DataModule.provideAtlasDeviceIdentity`.
     */
    @Suppress("DEPRECATION")
    private fun deviceFacts(context: Context): DeviceFacts {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val moduleVersion = runCatching {
            Shell.cmd("grep '^version=' '${MaxManagerPaths.MODULE_DIR}/module.prop' 2>/dev/null | head -n1").exec()
                .out.firstOrNull()
        }.getOrNull()?.substringAfter('=', "")?.trim()?.takeIf(String::isNotEmpty)
        return DeviceFacts(
            // `null` لا "-": غياب الرقم يبقى غيابًا، والباني هو من يرسمه شرطة.
            appVersion = versionName,
            moduleVersion = moduleVersion,
            socManufacturer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.os.Build.SOC_MANUFACTURER.ifBlank { null }
            } else {
                null
            },
            socModel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.os.Build.SOC_MODEL.ifBlank { null }
            } else {
                null
            },
            hardware = android.os.Build.HARDWARE.ifBlank { null },
            apiLevel = android.os.Build.VERSION.SDK_INT,
            kernel = System.getProperty("os.version"),
            rooted = runCatching { Shell.isAppGrantedRoot() == true }.getOrDefault(false),
        )
    }

    /**
     * يكتب حزمة التشخيص في ملف نصّي واحد ويعيده للمشاركة — نفس مسار المشاركة الذي يسلكه
     * السجل الخام، فالمشاركة واحدة واختيار ما يُشارَك هو ما يختلف.
     *
     * ولماذا الحزمة لا «التقرير» وحده: مستقبل هذه الدالة أن يُرسَل الملف **وحده** بلا جهاز ولا
     * سؤال، فالحزمة تحمل السجل الخام وترويسة الجلسة وشرح الرمز والوحدات وإعداداتنا الفعّالة.
     */
    fun shareDiagnosticBundle(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val target = File(context.cacheDir, "MaxManager_Bundle_$timestamp.txt")
                target.writeText(bundleText(context))
                target
            } catch (e: Exception) {
                EventLog.error("LogsViewer", "export_diagnostic_report", e)
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
