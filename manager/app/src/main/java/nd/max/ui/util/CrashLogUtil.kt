/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import com.topjohnwu.superuser.Shell

/**
 * `AR-06` — ملخّص الانهيارات وANR، **قراءة فقط**.
 *
 * النطاق المُعلَن صراحةً (ADR-07): هذا **ملخّص** لا محلّل traces. نُبلّغ بوجود ملفات
 * `/data/anr` و`/data/tombstones` وعددها وأحدثها — ولا ندّعي قراءة محتواها ولا تفسيره.
 * وقراءة كاملة لـANR/tombstone تحتاج صيغة تختلف بين الإصدارات، فتُركت كخطوة لاحقة معلَنة.
 *
 * كل حقل ثلاثيّ: قيمة · `0` · `null` (تعذّرت القراءة). و`null` ليست «صفر الحوادث».
 */
data class CrashLogSummary(
    /** عدد ملفات `/data/anr`، أو `null` إن تعذّرت القراءة. */
    val anrCount: Int?,
    /** عدد ملفات `/data/tombstones`، أو `null` إن تعذّرت القراءة. */
    val tombstoneCount: Int?,
    /** أحدث ملف ANR (اسمًا)، أو `null`. */
    val latestAnrName: String?,
    /** أحدث ملف tombstone (اسمًا)، أو `null`. */
    val latestTombstoneName: String?,
    /** زمن آخر ملف (ms)، أو `null` (غير معروف لا «الآن»). */
    val latestAtMs: Long?,
) {
    val readable: Boolean get() = anrCount != null || tombstoneCount != null

    /** المجموع، أو `null` إن لم نقرأ أيًّا من المجلدين. */
    val total: Int?
        get() = if (!readable) null else (anrCount ?: 0) + (tombstoneCount ?: 0)
}

object CrashLogUtil {

    private const val ANR_DIR = "/data/anr"
    private const val TOMBSTONE_DIR = "/data/tombstones"

    /** أول سطر غير فارغ = أحدث ملف (لأننا نطلب من `ls -t` الترتيب). */
    fun parseNewestFile(raw: String?): String? =
        raw?.lines()?.firstOrNull { it.isNotBlank() }?.trim()

    /** زمن UNIX بالثواني من `stat -c %Y`. */
    fun parseEpochSeconds(raw: String?): Long? {
        val seconds = parseNewestFile(raw)?.toLongOrNull() ?: return null
        return seconds.takeIf { it > 0 }?.let { it * 1000 }
    }

    fun read(): CrashLogSummary {
        val anr = listFiles(ANR_DIR)
        val tombstones = listFiles(TOMBSTONE_DIR)
        val latestAnr = newestName(ANR_DIR)
        val latestTombstone = newestName(TOMBSTONE_DIR)
        return CrashLogSummary(
            anrCount = anr?.size,
            tombstoneCount = tombstones?.size,
            latestAnrName = latestAnr,
            latestTombstoneName = latestTombstone,
            latestAtMs = newestMtimeMs(listOfNotNull(latestAnr?.let { "$ANR_DIR/$it" }, latestTombstone?.let { "$TOMBSTONE_DIR/$it" })),
        )
    }

    private fun listFiles(path: String): List<String>? = runCatching {
        val result = Shell.cmd("ls -1 ${quote(path)} 2>/dev/null").exec()
        if (!result.isSuccess) null
        else result.out.map(String::trim).filter(String::isNotEmpty)
    }.getOrNull()

    private fun newestName(path: String): String? = runCatching {
        val result = Shell.cmd("ls -t ${quote(path)} 2>/dev/null | head -n 1").exec()
        if (!result.isSuccess) null else parseNewestFile(result.out.joinToString("\n"))
    }.getOrNull()

    private fun newestMtimeMs(paths: List<String>): Long? {
        if (paths.isEmpty()) return null
        val quoted = paths.joinToString(" ") { quote(it) }
        return runCatching {
            val result = Shell.cmd("stat -c %Y $quoted 2>/dev/null").exec()
            if (!result.isSuccess) return@runCatching null
            val seconds = result.out.mapNotNull { parseNewestFile(it)?.toLongOrNull() }.maxOrNull()
            seconds?.takeIf { it > 0 }?.let { it * 1000 }
        }.getOrNull()
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
