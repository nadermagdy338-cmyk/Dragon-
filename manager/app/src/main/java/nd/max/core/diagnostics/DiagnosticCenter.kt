/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */

package nd.max.core.diagnostics

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nd.max.MaxManagerPaths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مركز التشخيص: الذاكرة الحية للمشاكل — حجر أساس حلقة التشخيص المغلقة.
 *
 * المشكلة التي كانت قائمة: سجلات EventLog/AppMonitorLogger تتطلب من
 * المستخدم تفعيل "سجل النشاط المفصل" **قبل** حدوث المشكلة، فإذا أبلغ
 * مستخدم عن عطل ولم يكن قد فعّل شيئًا، كان التصدير يخرج بلا أي أثر
 * للمشكلة — والسؤال الأول للمطور ("هل المكتبة الأصلية تحمّلت أصلًا؟")
 * بلا إجابة في الحزمة.
 *
 * هذا المكوّن يعمل **دائمًا** بلا أي بوابة:
 *  - كل عطل يُسجَّل فور حدوثه في مخزن حلقي في الذاكرة (بلا كلفة إن لم
 *    يحدث شيء)، مع إزالة تكرار: العطل المتكرر يزيد عدّاد المدخل نفسه
 *    بدل إغراق المخزن — "(x500)" معلومة بذاتها: فشل مزمن لا عابر.
 *  - أول ظهور لكل عطل يُحوَّل إلى MaxManager.log عبر نفس خط CLI الخاص
 *    بالـdaemon الذي يستخدمه EventLog (`--log <TAG> <LEVEL> <MSG>`)
 *    حتى يصمد الأثر بعد انهيار التطبيق (المخزن الذهني يموت مع العملية؛
 *    الملف يبقى).
 *  - dumpDiagnosticLogs يدمج [formatBlock] في كل تصدير — فيرى المطور
 *    مشاكل الجلسة حتى لو لم يفعّل المستخدم أي سجل تفصيلي.
 *
 * الكلفة في الحالة السليمة: صفر (لا حلقات، لا shell، لا كتابة).
 */
object DiagnosticCenter {

    /**
     * مستوى العطل. daemonLevel يطابق ترقيم CLIUtility.c:
     * 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR, 4=FATAL.
     */
    enum class Level(val daemonLevel: Int) { INFO(1), WARN(2), ERROR(3) }

    private class DiagEntry(
        val component: String,
        val level: Level,
        val message: String,
        val firstTimeMs: Long,
        var lastTimeMs: Long,
        var count: Int = 1
    )

    private const val MAX_ENTRIES = 64
    private const val MAX_MESSAGE_CHARS = 240

    private val lock = Any()
    private val entries = ArrayDeque<DiagEntry>()

    /** عدد المشاكل المميزة (WARN/ERROR) — تعرضه شاشة الإعدادات كمؤشر. */
    private val _issueCount = MutableStateFlow(0)
    val issueCount: StateFlow<Int> = _issueCount.asStateFlow()

    /**
     * يسجّل مشكلة. آمن من أي خيط ومن أي تكرار.
     *
     * @param component المصدر: "jni"، "engine"، "learning"، "profile"...
     * @param message وصف قصير ثابت الصياغة (ثبات الصياغة هو ما يجعل
     *   إزالة التكرار فعالة — نفس العطل = نفس النص = نفس المدخل).
     * @param throwable استثناء اختياري؛ رسالته تُلحق مقتطعة.
     */
    fun record(
        component: String,
        message: String,
        throwable: Throwable? = null,
        level: Level = Level.ERROR
    ) {
        val detail = throwable?.message?.take(120)?.let { " :: $it" } ?: ""
        val msg = (message + detail).take(MAX_MESSAGE_CHARS)
        val now = System.currentTimeMillis()

        val forwardNow: Boolean
        synchronized(lock) {
            val existing = entries.lastOrNull { it.component == component && it.message == msg }
            if (existing != null) {
                existing.count++
                existing.lastTimeMs = now
                forwardNow = false
            } else {
                if (entries.size >= MAX_ENTRIES) entries.removeFirst()
                entries.addLast(DiagEntry(component, level, msg, now, now))
                // إعادة العدّ فقط عند تغيّر بنية المخزن (مدخل جديد) —
                // وليس مع كل تسجيل متكرر: التكرار اللانهائي يكلف
                // مسحًا واحدًا وعدّادًا فقط، بلا أي عمل إضافي.
                _issueCount.value = entries.count { it.level != Level.INFO }
                forwardNow = level != Level.INFO
            }
        }

        // التحويل لأول ظهور فقط: العطل المزمن يكتب سطرًا واحدًا في ملف
        // الوحدة بدل مئات الأصفار — والعدّاد في الذاكرة يحمل التكرار.
        if (forwardNow) {
            forwardToDaemonLog(component, level, msg)
        }
    }

    fun hasIssues(): Boolean = synchronized(lock) {
        entries.any { it.level != Level.INFO }
    }

    /**
     * A structured entry as a **report** may carry it (`P6`/`T6.7`).
     *
     * There is deliberately no message field. A message is free text a caller interpolated, so it can
     * contain a path, a package name, an account id or anything else that happened to be in scope at
     * the failure site; a diagnostic report is the last place that should become a channel for those.
     * Component, level and count answer "what failed, how badly, how often" without carrying content.
     */
    data class StructuredEntry(
        val component: String,
        val level: Level,
        val count: Int,
        val firstSeenMs: Long,
        val lastSeenMs: Long,
    )

    /** The whole allowlisted projection, ordered from the most recent failure backwards. */
    data class StructuredSummary(
        val entries: List<StructuredEntry>,
        val warnCount: Int,
        val errorCount: Int,
        val distinctComponents: Int,
    )

    /**
     * The allowlisted projection of this session's ring buffer.
     *
     * Distinct from [formatBlock], which exists for a human reading a log and includes messages. This
     * one exists for a report that may leave the device, so it exposes only component names (which the
     * codebase's own call sites define) and counts.
     */
    fun structured(): StructuredSummary = synchronized(lock) {
        val projected = entries.map { entry ->
            StructuredEntry(
                component = entry.component,
                level = entry.level,
                count = entry.count,
                firstSeenMs = entry.firstTimeMs,
                lastSeenMs = entry.lastTimeMs,
            )
        }.sortedByDescending { it.lastSeenMs }
        StructuredSummary(
            entries = projected,
            warnCount = projected.count { it.level == Level.WARN },
            errorCount = projected.count { it.level == Level.ERROR },
            distinctComponents = projected.map { it.component }.distinct().size,
        )
    }

    /** الكتلة التي تُدمج في كل تصدير تشخيصي. */
    fun formatBlock(): String = synchronized(lock) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        buildString {
            appendLine("--- DIAGNOSTIC CENTER (always-on, in-memory ring) ---")
            if (entries.isEmpty()) {
                appendLine("(no issues recorded this session)")
            } else {
                entries.forEach { e ->
                    appendLine(
                        "[${e.level}] first=${fmt.format(Date(e.firstTimeMs))} " +
                            "last=${fmt.format(Date(e.lastTimeMs))} (x${e.count}) " +
                            "${e.component}: ${e.message}"
                    )
                }
            }
        }
    }

    private fun forwardToDaemonLog(component: String, level: Level, message: String) {
        // نفس آلية EventLog.forward: fire-and-forget عبر libsu — فشل
        // التسجيل لا يجوز أن يطفو على العملية التي أطلقته.
        runCatching { forwarder(component, level, message) }
    }

    /**
     * حاقن التحويل — قابل للاستبدال في اختبارات الوحدة كي لا تحاول
     * بيئة JVM إطلاق shell حقيقي. الإنتاج يستخدم الافتراضي أدناه.
     */
    internal var forwarder: (String, Level, String) -> Unit = { component, level, message ->
        Shell.cmd(
            "'${MaxManagerPaths.SERVICE_BIN}' --log 'diag' '${level.daemonLevel}' '${shellSafe(message)}'"
        ).submit()
    }

    fun resetForTesting() {
        synchronized(lock) {
            entries.clear()
        }
        _issueCount.value = 0
    }

    private fun shellSafe(value: String): String =
        value.replace("\n", " ").replace("\r", "").replace("'", "'\\''")
}
