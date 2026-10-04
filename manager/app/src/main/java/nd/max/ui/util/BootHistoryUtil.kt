/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util
import nd.max.core.platform.PropertyUtils

import java.io.File

/**
 * لقطة تاريخ الإقلاع (`AR-02`) — **قراءة فقط**، بلا كتابة وبلا جذر.
 *
 * المصادر (موثَّقة في AOSP و`docs/ai/EXTERNAL-RESEARCH-APP.md`):
 * - `ro.boot.bootreason` — يكتبه المحمّل.
 * - `sys.boot.reason` — يكتبه النظام (يُقرأ موثوقًا بعد تركيب `userdata`).
 * - `/sys/fs/pstore` — `console-ramoops`/`dmesg-ramoops-*`: وجودها يعني انهيارًا سابقًا.
 *
 * **قاعدة الصدق (ADR-07 / XR-19):** لا نخمّن. وإذا تعذّرت قراءة `pstore`
 * نُعلن [pstoreReadable] = false ولا نقول «لا وجود لانهيار».
 */
data class BootHistory(
    /** سبب الإقلاع كما كتبه المحمّل، أو `null` إن لم يُعلنه النظام. */
    val loaderReason: String?,
    /** سبب الإقلاع كما كتبه النظام، أو `null`. */
    val systemReason: String?,
    /** أسماء ملفات في `/sys/fs/pstore` (فارغة إن لم يوجد أو لم تُقرأ). */
    val pstoreEntries: List<String>,
    /** هل استطعنا فحص `pstore` فعلًا؟ (`false` = لا نستطيع الجزم). */
    val pstoreReadable: Boolean,
) {
    val hasCrashArtifact: Boolean get() = pstoreEntries.isNotEmpty()

    /** هل يُعلن النظام أي سبب إقلاع أصلًا؟ */
    val isSupported: Boolean get() = loaderReason != null || systemReason != null

    val isThermalShutdown: Boolean
        get() = listOfNotNull(loaderReason, systemReason)
            .any { it.contains("thermal", ignoreCase = true) }
}

object BootHistoryUtil {

    private const val PSTORE_DIR = "/sys/fs/pstore"

    /**
     * يقرأ تاريخ الإقلاع. لا يكتب شيئًا ولا يطلب امتيازًا.
     *
     * @param pstoreListing قارئ مخصَّص لـ`pstore` (يُمرَّر للاختبار)، ويعود بـ`null`
     *        إن تعذّرت القراءة.
     */
    fun read(
        pstoreListing: (() -> List<String>?)? = null,
    ): BootHistory {
        val loader = PropertyUtils.get("ro.boot.bootreason").ifBlank { null }
        val system = PropertyUtils.get("sys.boot.reason").ifBlank { null }
            ?: PropertyUtils.get("ro.boot.boot_reason").ifBlank { null }

        val listing = (pstoreListing ?: ::listPstore).invoke()
        return BootHistory(
            loaderReason = loader,
            systemReason = system,
            pstoreEntries = listing.orEmpty(),
            pstoreReadable = listing != null,
        )
    }

    private fun listPstore(): List<String>? = runCatching {
        val dir = File(PSTORE_DIR)
        if (!dir.isDirectory || !dir.canRead()) return@runCatching null
        dir.listFiles()?.map { it.name }?.sorted()
    }.getOrNull()

    /**
     * يفكّك صيغة AOSP `reason,subreason,detail` إلى أجزاء غير فارغة.
     * لا يُعيد صياغة المعنى — يُظهر ما كُتب حرفيًّا.
     */
    fun parts(raw: String?): List<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
