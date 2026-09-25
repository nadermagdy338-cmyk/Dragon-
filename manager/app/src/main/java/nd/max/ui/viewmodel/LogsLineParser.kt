/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.viewmodel

import nd.max.core.diagnostics.LogArea
import nd.max.core.diagnostics.LogEventParser
import nd.max.core.diagnostics.LogVerdict
import java.util.concurrent.atomic.AtomicLong
import nd.max.core.jni.ProbeBridge
import nd.max.core.jni.ProbePacket

/**
 * مُحلِّل سطور السجلّات — **النمط المرجعي** كاملًا، ومسار الدفعة الأصلية فوقه.
 *
 * # لماذا ملف مستقل
 *
 * لأنه يُقاس في JVM مباشرة: النمط المرجعي هو ما يعمل على أجهزة حقيقية منذ البداية، فحِفظُ دلالته
 * في ملف يُختبَر **شرط** لأي نقل إلى Rust — لا زينة تنظيم. و[LogsViewerViewModel] يستهلكه، وهو
 * نفسه الذي كان يحمل هذه الدوال وأصبح فوق الحدّ المسموح لطول الملف (`oversized_files`).
 *
 * # مسارا التحليل — ودلالة واحدة
 *
 * 1. **الدفعة الأصلية** (`ProbeBridge.parseLogs`): كل سطور نافذة الإفراغ في نداء واحد (٣٠٠ مللي).
 * 2. **النمط المرجعي** (`parseLine`/`parseUnifiedLine`): سطرًا بسطر — وهو مسار السقوط عند غياب
 *    المكتبة الأصلية، وهو نفسه المرجع الدلالي الذي تُحاكيه وحدة Rust.
 *
 * وكلا المسارين يبني المدخلة من **دالة بناء واحدة** ([logcatEntry]/[unifiedEntry])، فلا تفترق
 * دلالة (تحويل المستوى، قصّ الوسم، حفظ النصّ الخام) بين طريقين لنفس السطر.
 *
 * # الخيط
 *
 * الدفعة تُحلَّل على خيط الإفراغ، والتصفير يأتي من مسارات «إعادة تشغيل البثّ/المسح» التي تعمل
 * على `Dispatchers.IO` أيضًا — أي **أكثر من خيط** نظريًّا. وعدّادات المعرّفات ذرّية لذلك: كلفتها
 * لا تُذكر أمام تحليل سطر، وخطأ عدّاد (معرّفان مكرّران ⇒ رسم مفتاحي متضارب) أغلى من ذلك بكثير.
 */
internal object LogsLineParser {

    /**
     * `MM-DD HH:MM:SS.mmm PID TID L TAG: message` — صيغة `logcat -v threadtime`.
     * **هذا النصّ هو المرجع**: وحدة `logparse` في Rust تحاكيه حرفيًّا، ويسقط اختبار إن انحرف أحدهما.
     */
    private val LOG_PATTERN = Regex(
        """^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([A-Z])\s+(.*?):\s?(.*)$"""
    )

    /**
     * `YYYY-MM-DD HH:MM:SS L TAG: message` — ما يكتبه `log_zenith()`/`external_log()` في
     * `SystemLogger.c` (ومن Kotlin: `AppMonitorLogger`/`EventLog`).
     */
    private val UNIFIED_LOG_PATTERN = Regex(
        """^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+([DIWEF])\s+(\S+?):\s?(.*)$"""
    )
    private val EVENT_TYPE_PATTERN = Regex("""EVENT=(\S+)""")
    private val SWITCH_ID_PATTERN = Regex("""sw=(\S+)""")

    private val idCounter = AtomicLong(0L)
    private val unifiedIdCounter = AtomicLong(0L)

    /** تصفير العدّادات عند إعادة تشغيل البثّ/المسح — كما كان يفعله الـViewModel نفسه. */
    fun resetLogcatIds() {
        idCounter.set(0L)
    }

    fun resetUnifiedIds() {
        unifiedIdCounter.set(0L)
    }

    // ── logcat ───────────────────────────────────────────────────────

    /**
     * تحليل دفعة سطور logcat — الأصلية أولًا، وإن غابت فالنمط المرجعي سطرًا بسطر.
     * والعائد بنفس ترتيب المُدخل وبعدد مدخلات مساوٍ لعدد السطور المُطابقة (S تُسقَط).
     */
    fun parseLogcatBatch(lines: List<String>): List<LogsViewerViewModel.LogEntry> =
        ProbeBridge.parseLogs(lines, unified = false)
            ?.let { rows -> rows.indices.mapNotNull { i -> entryFromRow(rows[i], lines[i]) } }
            ?: lines.mapNotNull(::parseLine)

    /** سطر من حزمة Rust → مدخلة. النصّ الخام يأتي من الجانبين، فلا يُعاد بناؤه. */
    private fun entryFromRow(row: ProbePacket.LogRow, raw: String): LogsViewerViewModel.LogEntry? =
        when (row.kind) {
            'S' -> null
            'F' -> LogsViewerViewModel.LogEntry(
                id = idCounter.getAndIncrement(), date = "", time = "", pid = "?", tid = "?",
                level = LogsViewerViewModel.LogLevel.VERBOSE, tag = "System", message = raw, raw = raw
            )
            else -> {
                if (row.fields.size < 7) return null
                logcatEntry(
                    date = row.fields[0],
                    time = row.fields[1],
                    pid = row.fields[2],
                    tid = row.fields[3],
                    levelStr = row.fields[4],
                    tag = row.fields[5],
                    msg = row.fields[6],
                    line = raw,
                )
            }
        }

    /** النمط المرجعي لسطر logcat — ومنه فرع الاحتياط: سطر غير مطابق يصير مدخلةً بنصّه الخام. */
    private fun parseLine(line: String): LogsViewerViewModel.LogEntry? {
        val match = LOG_PATTERN.find(line)
        if (match == null) {
            // السطر الفارغ وسطر الفاصل يُسقطان، وما عداهما يُحفظ بنصّه (لا يُفقد سطر أبدًا).
            if (line.isBlank() || line.startsWith("---------")) return null
            return LogsViewerViewModel.LogEntry(
                id = idCounter.getAndIncrement(), date = "", time = "", pid = "?", tid = "?",
                level = LogsViewerViewModel.LogLevel.VERBOSE, tag = "System", message = line, raw = line
            )
        }
        val (date, time, pid, tid, levelStr, tag, msg) = match.destructured
        return logcatEntry(date, time, pid, tid, levelStr, tag, msg, line)
    }

    /**
     * بناء مدخلة logcat من حقولها — مسار واحد للمحلّل المرجعي ولحزمة Rust معًا.
     */
    private fun logcatEntry(
        date: String,
        time: String,
        pid: String,
        tid: String,
        levelStr: String,
        tag: String,
        msg: String,
        line: String,
    ): LogsViewerViewModel.LogEntry = LogsViewerViewModel.LogEntry(
        id = idCounter.getAndIncrement(),
        date = date,
        time = time,
        pid = pid,
        tid = tid,
        level = LogsViewerViewModel.LogLevel.fromLetter(levelStr),
        tag = tag.trim(),
        message = msg,
        raw = line,
    )

    // ── السجلّ الموقَّع ───────────────────────────────────────────────

    fun parseUnifiedBatch(lines: List<String>): List<LogsViewerViewModel.UnifiedLogEntry> =
        ProbeBridge.parseLogs(lines, unified = true)
            ?.let { rows -> rows.indices.mapNotNull { i -> unifiedEntryFromRow(rows[i], lines[i]) } }
            ?: lines.mapNotNull(::parseUnifiedLine)

    private fun unifiedEntryFromRow(
        row: ProbePacket.LogRow,
        raw: String,
    ): LogsViewerViewModel.UnifiedLogEntry? {
        if (row.kind != 'P' || row.fields.size != 4) return null
        return unifiedEntry(row.fields[0], row.fields[1], row.fields[2], row.fields[3], raw)
    }

    private fun parseUnifiedLine(line: String): LogsViewerViewModel.UnifiedLogEntry? {
        if (line.isBlank()) return null
        val match = UNIFIED_LOG_PATTERN.find(line) ?: return null
        val (timestamp, levelStr, tag, message) = match.destructured
        return unifiedEntry(timestamp, levelStr, tag, message, line)
    }

    /** بناء مدخلة السجلّ الموقَّع من حقولها — مسار واحد للمحلّل المرجعي وللحزمة الأصلية. */
    private fun unifiedEntry(
        timestamp: String,
        levelStr: String,
        tag: String,
        message: String,
        line: String,
    ): LogsViewerViewModel.UnifiedLogEntry {
        val level = LogsViewerViewModel.UnifiedLogLevel.fromLetter(levelStr)
        // الفكّ **مرّة واحدة عند القراءة** لا عند كل إعادة ترتيب أو تصفية: النافذة تحمل 2000 سطر
        // وتُصفّى مع كل ضغطة، وتفكيكها في العرض كان سيُكرّر العمل بلا سبب.
        val event = LogEventParser.parse(message)
        val eventName = event?.event
        return LogsViewerViewModel.UnifiedLogEntry(
            id = unifiedIdCounter.getAndIncrement(),
            timestamp = timestamp,
            level = level,
            source = LogsViewerViewModel.LogSource.fromTag(tag),
            rawTag = tag,
            eventType = eventName ?: EVENT_TYPE_PATTERN.find(message)?.groupValues?.get(1),
            switchId = SWITCH_ID_PATTERN.find(message)?.groupValues?.get(1),
            message = message,
            raw = line,
            event = event,
            area = LogArea.of(eventName.orEmpty(), event?.target),
            verdict = LogVerdict.of(eventName.orEmpty(), event, level.letter),
        )
    }
}
